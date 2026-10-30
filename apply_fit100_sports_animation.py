from pathlib import Path
import sys

repo = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(".")
target = repo / "app/src/main/java/com/fit100/tracker/WorkoutDashboard.kt"
source = Path(__file__).with_name("Fit100_SportsExerciseArt.kt")
if not target.is_file():
    raise SystemExit(f"Cannot find {target}. Run from the Fitx100 repository or pass its path.")
code = target.read_text(encoding="utf-8-sig")
start = code.find("class SportsExerciseArt(")
end = code.find("class SportsRouteArt(", start)
if start < 0 or end < 0:
    raise SystemExit("Expected SportsExerciseArt / SportsRouteArt class boundaries not found. No files changed.")
replacement = source.read_text(encoding="utf-8").split("class SportsExerciseArt(", 1)[1]
replacement = "class SportsExerciseArt(" + replacement
updated = code[:start] + replacement.rstrip() + "\n\n" + code[end:]
backup = target.with_suffix(".kt.before-animation")
if not backup.exists():
    backup.write_text(code, encoding="utf-8")
target.write_text(updated, encoding="utf-8")
print(f"Updated {target}")
print(f"Original saved as {backup}")
print("Build and test the app before committing.")
