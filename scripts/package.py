"""Package portable source and the already-built debug APK. Does not build or sign."""
from pathlib import Path
import hashlib
import shutil
import zipfile

root = Path(__file__).resolve().parents[1]
output = root / "deliverables"
output.mkdir(exist_ok=True)
apk = root / "android/app/build/outputs/apk/debug/app-debug.apk"
if not apk.is_file():
    raise SystemExit("Build the APK before packaging.")
target = output / "SmishGuard-0.1.0-debug.apk"
shutil.copyfile(apk, target)
top = ["README.md", ".gitignore", ".gitattributes", "settings.gradle.kts", "build.gradle.kts", "gradle.properties", "gradlew", "gradlew.bat"]
files = [root / name for name in top]
for name in ["android", "research", "docs", "backend", "scripts", "gradle"]:
    files.extend(p for p in (root/name).rglob("*") if p.is_file()
                 and not {"build", ".venv", "__pycache__", "data", "runs"}.intersection(p.relative_to(root/name).parts))
archive = output / "SmishGuard-source.zip"
with zipfile.ZipFile(archive, "w", compression=zipfile.ZIP_DEFLATED) as z:
    for path in sorted(files):
        z.write(path, path.relative_to(root).as_posix())
(output / "SHA256SUMS.txt").write_text("".join(
    hashlib.sha256(p.read_bytes()).hexdigest() + "  " + p.name + "\n" for p in (target, archive)), encoding="utf-8")
print(f"Packaged APK and {len(files)} portable source files.")
