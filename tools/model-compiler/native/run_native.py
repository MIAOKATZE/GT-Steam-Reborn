"""Scoped adapter for the existing fixed-instance/private-desktop runner. Never copies a client or rebuilds QA."""
from pathlib import Path
import importlib.util
import sys
sys.dont_write_bytecode = True
REPO=Path(__file__).resolve().parents[3]
OUT=REPO.parent/'.artifacts/gtsr-dimension-trim'
RUNTIME=REPO.parent/'.artifacts/core-global-followup/runtime'
if len(sys.argv)<2 or sys.argv[1] not in ['prepare','launch','status','stop','resume','restore']:
    raise SystemExit('Use prepare/launch/status/stop/resume/restore; bootstrap is deliberately unavailable')
for key in ['--run','--world-from']:
    if key in sys.argv:
        target=Path(sys.argv[sys.argv.index(key)+1]).resolve()
        target.relative_to((OUT/'runs').resolve())
spec=importlib.util.spec_from_file_location('owned_rigmesh_fixed_instance_runner',RUNTIME/'runner.py')
runner=importlib.util.module_from_spec(spec);spec.loader.exec_module(runner)
original_own=runner.own
def own(path):
    path=Path(path).resolve()
    try:path.relative_to(OUT.resolve());return path
    except ValueError:return original_own(path)
runner.own=own
runner.RUNS=OUT/'runs'
# Preserve the original HERE for its frozen QA selectors and shared fixed-instance lock.
runner.main()
