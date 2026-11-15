from pathlib import Path
import json
import os
import re
from urllib.parse import urlsplit

root = Path(__file__).resolve().parents[1]
repo = os.environ.get("GITHUB_REPOSITORY", "local/cloudstream-sporb")
if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repo):
    raise SystemExit("GITHUB_REPOSITORY kullanici/depo biçiminde olmalı.")
rows = json.loads((root / "data/channels.json").read_text(encoding="utf-8"))
if len(rows) != len({r["url"] for r in rows}):
    raise SystemExit("Spor kataloğunda yinelenen yayın adresi var.")

disabled = json.loads((root / "data/unavailable-sports-sources.json").read_text(encoding="utf-8"))["disabled"]
disabled_ids = set()
for entry in disabled:
    channel_id = entry["id"]
    if not isinstance(channel_id, int) or not 1 <= channel_id <= len(rows) or channel_id in disabled_ids:
        raise SystemExit(f"Geçersiz veya tekrarlanan spor kaydı: {channel_id}")
    row = rows[channel_id - 1]
    if row["name"] != entry["name"] or row["source"] != entry["source"]:
        raise SystemExit(f"Spor kataloğunda kaynak kimliği değişmiş: {channel_id}")
    disabled_ids.add(channel_id)

def quote(text):
    return json.dumps(text, ensure_ascii=False).replace("$", chr(92) + "$")

lines = [
    "package com.ortaksistem.sporb",
    "",
    "internal data class SportsChannel(",
    "    val id: Int, val name: String, val group: String, val source: String,",
    "    val url: String, val headers: Map<String, String>,",
    ")",
    "",
    "internal object SporBCatalogue {",
    "    const val repository = " + quote(repo),
    "    val channels = listOf(",
]
for index, row in enumerate(rows, 1):
    if index in disabled_ids:
        continue
    headers = ", ".join(f"{quote(k)} to {quote(v)}" for k, v in row["headers"].items())
    values = ", ".join(quote(row[key]) for key in ("name", "group", "source", "url"))
    lines.append(f"        SportsChannel({index}, {values}, mapOf({headers})),")
lines.extend(["    )", "}", ""])
target = root / "SporBProvider/src/main/kotlin/com/ortaksistem/sporb/SporBCatalogue.kt"
target.write_text("\n".join(lines), encoding="utf-8")

web_groups = [
    ("patron-spor", "spor5-patronizle-kanallar.json"),
    ("selcuk-spor", "spor6-selcuksports-kanallar.json"),
    ("taraftarium", "taraftarium-canli-kanallar.json"),
    ("taraftarium24", "taraftarium24-program.json"),
]
web_lines = [
    "package com.ortaksistem.sporb",
    "",
    "internal data class SportsWebChannel(",
    "    val id: String, val name: String, val page: String, val waitSeconds: Int,",
    ")",
    "",
    "internal data class SportsWebGroup(val id: String, val name: String, val channels: List<SportsWebChannel>)",
    "",
    "internal object SportsWebCatalogue {",
    "    val groups = listOf(",
]
for group_id, filename in web_groups:
    data = json.loads((root / "data" / filename).read_text(encoding="utf-8"))
    is_schedule = data["linkType"] == "schedule-page"
    wait = data.get("playback", {}).get("startupWaitSeconds", 5)
    if type(wait) is not int or not 0 <= wait <= 10:
        raise SystemExit(f"Geçersiz açılış süresi: {filename}")
    web_lines.append(f"        SportsWebGroup({quote(group_id)}, {quote(data['group'])}, listOf(")
    seen = set()
    for index, entry in enumerate(data["sections" if is_schedule else "channels"], 1):
        page = entry.get("page" if is_schedule else "channelPage")
        if not page or page in seen:
            continue
        uri = urlsplit(page)
        if uri.scheme != "https" or not uri.hostname or uri.username or uri.password:
            raise SystemExit(f"Geçersiz oynatıcı adresi: {filename}, kayıt {index}")
        seen.add(page)
        ident = quote(f"{group_id}/{index}")
        web_lines.append(f"            SportsWebChannel({ident}, {quote(entry['name'])}, {quote(page)}, {wait}),")
    web_lines.append("        )),")
web_lines.extend(["    )", "}", ""])
target.with_name("SportsWebCatalogue.kt").write_text("\n".join(web_lines), encoding="utf-8")
