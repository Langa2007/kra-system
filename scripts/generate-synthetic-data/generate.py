from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
PACKAGE = ROOT / "packages" / "synthetic-data"
sys.path.insert(0, str(PACKAGE))

from kra_synthetic.generator import main  # noqa: E402

if __name__ == "__main__":
    main()
