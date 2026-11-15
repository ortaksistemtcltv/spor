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
manifest_path = root / "repo.json"
manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
if manifest.get("pluginLists") != [f"{base}/plugins.json"]:
    raise SystemExit("Depo manifesti ve eklenti dizini adresi eşleşmiyor.")

entries = json.loads((root / "build/plugins.json").read_text(encoding="utf-8"))
if not isinstance(entries, list) or not entries:
    raise SystemExit("Yayımlanacak eklenti bulunamadı.")

display_names = {"SporBProvider": "SEYİRBOX", "FilmProvider": "FİLM", "DiziProvider": "DİZİ"}
artifacts = {}
for entry in entries:
    if not isinstance(entry, dict):
        raise SystemExit("Geçersiz eklenti kaydı.")
    internal_name = entry.get("internalName")
    if not isinstance(internal_name, str) or not re.fullmatch(r"[A-Za-z0-9_-]+", internal_name):
        raise SystemExit("Geçersiz eklenti kimliği.")
    filename = f"{internal_name}.cs3"
    if entry.get("url") != f"{base}/{filename}" or filename in artifacts:
        raise SystemExit(f"Geçersiz veya tekrarlanan eklenti adresi: {internal_name}")
    artifact = root / internal_name / "build" / filename
    if not artifact.is_file():
        raise SystemExit(f"Derlenen eklenti bulunamadı: {filename}")
    artifacts[filename] = artifact
    if internal_name in display_names:
        entry["name"] = display_names[internal_name]

publish = root / "build/publish"
publish.mkdir(parents=True, exist_ok=True)
for stale in publish.glob("*.cs3"):
    if stale.name not in artifacts:
        stale.unlink()
for filename, artifact in artifacts.items():
    copyfile(artifact, publish / filename)
(publish / "plugins.json").write_text(
    json.dumps(entries, ensure_ascii=False, indent=2) + "\n",
    encoding="utf-8",
)
copyfile(manifest_path, publish / "repo.json")
print(f"CloudStream depo bağlantısı: https://raw.githubusercontent.com/{repo}/main/repo.json")
