from pathlib import Path
import json
import os
import re

root = Path(__file__).resolve().parents[1]
repo = os.environ.get("GITHUB_REPOSITORY", "local/cloudstream-sporb")
if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repo):
    raise SystemExit("GITHUB_REPOSITORY kullanici/depo biçiminde olmalı.")
rows = json.loads((root / "data/channels.json").read_text(encoding="utf-8"))
assert len(rows) == len({r["url"] for r in rows}) == 225
assert {r["source"] for r in rows} == {"Tvjustin", "Trgoals", "DoğuSpor", "Vavoo Turkey"}

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
    headers = ", ".join(f"{quote(k)} to {quote(v)}" for k, v in row["headers"].items())
    values = ", ".join(quote(row[key]) for key in ("name", "group", "source", "url"))
    lines.append(f"        SportsChannel({index}, {values}, mapOf({headers})),")
lines.extend(["    )", "}", ""])
target = root / "SporBProvider/src/main/kotlin/com/ortaksistem/sporb/SporBCatalogue.kt"
target.write_text("\n".join(lines), encoding="utf-8")
