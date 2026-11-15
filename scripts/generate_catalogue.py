from pathlib import Path
import json
import os
import re

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
