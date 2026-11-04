"""Compatibility entrypoint for the complete current firebox resource/renderer contract.

Pass --gt, --modernity and optional --jar/--source/--out. The historical SHA-only
fixture remains in ForegroundGuardContract.java for earlier receipt provenance.
"""
import runpy
from pathlib import Path

if __name__ == "__main__":
    runpy.run_path(str(Path(__file__).resolve().parents[1] / "workeffects/firebox_resource_contract.py"), run_name="__main__")
