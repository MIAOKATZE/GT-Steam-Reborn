"""Copy a read-only Prism pack into a new owned offline QA client, without accounts or worlds."""
from pathlib import Path
import argparse
import hashlib
import json
import re
import shutil

ROOT = Path(__file__).resolve().parents[3]


def artifact(lib):
    group, name, version, *classifier = lib["name"].split(":")
    filename = name + "-" + version + ("-" + classifier[0] if classifier else "") + ".jar"
    return Path(group.replace(".", "/")) / name / version / filename


def main():
    import sys
    sys.path.insert(0, str(ROOT.parent / 'tools/testlab'))
    import client_prepare
    return client_prepare.main(ROOT)


if __name__ == "__main__":
    main()
