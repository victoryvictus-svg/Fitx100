from pathlib import Path
import subprocess
import sys
import tempfile

with tempfile.TemporaryDirectory() as d:
    root = Path(d)
    target = root / "app/src/main/java/com/fit100/tracker/WorkoutDashboard.kt"
    target.parent.mkdir(parents=True)
    original = "package com.fit100.tracker\nclass WorkoutDashboard {}\nclass SportsExerciseArt(c:Context, id:String):View(c) {}\nclass SportsRouteArt(c:Context):View(c) {}\n"
    target.write_text(original)
    result = subprocess.run([sys.executable, str(Path(__file__).with_name("apply_fit100_sports_animation.py")), str(root)], capture_output=True, text=True)
    assert result.returncode == 0, result.stderr + result.stdout
    updated = target.read_text()
    assert updated.count("class SportsExerciseArt(") == 1
    assert updated.count("class SportsRouteArt(") == 1
    assert "postInvalidateDelayed(33L)" in updated
    assert target.with_suffix(".kt.before-animation").read_text() == original
    print("PASS: class replacement, original preservation, animation integration")
