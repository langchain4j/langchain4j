"""
Checks that no workflow or local action uploads an artifact. GitHub masks secrets in job logs and job summaries, but not
in artifacts, and anyone can download the artifacts of a public repository. The job log and the job summary already
show the test output and the failures, so print what is needed there instead.

Usage, from the root of this repository:
    python3 .github/scripts/check-artifact-uploads.py
"""

import glob
import re
import sys

UPLOAD = re.compile(r"\bactions/upload-artifact@")


def main():
    paths = glob.glob(".github/workflows/*.y*ml") + glob.glob(".github/actions/**/*.y*ml", recursive=True)
    found = []
    for path in sorted(paths):
        with open(path) as f:
            for number, line in enumerate(f, start=1):
                if UPLOAD.search(line.split("#", 1)[0]):
                    found.append(f"{path}:{number}: uploads an artifact, and artifacts are not masked like job logs; "
                                 "print what is needed to the job log or the job summary instead")
    for problem in found:
        print(f"::error::{problem}")
    return 1 if found else 0


if __name__ == "__main__":
    sys.exit(main())
