from pathlib import Path
from shutil import copyfile
import json
import os
import re

root = Path(__file__).resolve().parents[1]
repo = os.environ["GITHUB_REPOSITORY"]
if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repo):
    raise SystemExit("Geçersiz GitHub depo adresi.")
base = f"https://raw.githubusercontent.com/{repo}/builds"
plugin = root / "SporBProvider/build/SporBProvider.cs3"
plugins_json = root / "build/plugins.json"
entries = json.loads(plugins_json.read_text(encoding="utf-8"))
if len(entries) != 1 or entries[0].get("url") != f"{base}/{plugin.name}":
    raise SystemExit("Eklenti dizini oluşturulan CS3 dosyasına işaret etmiyor.")
publish = root / "build/publish"
publish.mkdir(parents=True, exist_ok=True)
copyfile(plugin, publish / plugin.name)
entries[0]["name"] = "SPOR"
(publish / "plugins.json").write_text(json.dumps(entries, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
manifest = {
    "name": "SPOR",
    "description": "Türkçe spor canlı yayın kataloğu",
    "manifestVersion": 1,
    "pluginLists": [f"{base}/plugins.json"],
}
(publish / "repo.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
main_manifest = json.loads((root / "repo.json").read_text(encoding="utf-8"))
if main_manifest != manifest:
    raise SystemExit("Depo manifesti ve yayın adresi eşleşmiyor.")
print(f"CloudStream depo bağlantısı: https://raw.githubusercontent.com/{repo}/main/repo.json")
