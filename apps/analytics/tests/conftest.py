import sys
from pathlib import Path

ANALYTICS_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ANALYTICS_ROOT))
