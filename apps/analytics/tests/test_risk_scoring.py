import uuid
from pathlib import Path

from fastapi.testclient import TestClient

from app.main import app


def test_risk_scoring_is_reproducible_and_ranks_known_anomaly(monkeypatch) -> None:
    tracking_db = Path("C:/tmp") / f"phase12-mlflow-{uuid.uuid4()}.db"
    artifact_root = Path("C:/tmp") / f"phase12-mlflow-artifacts-{uuid.uuid4()}"
    monkeypatch.setenv("MLFLOW_TRACKING_URI", f"sqlite:///{tracking_db.as_posix()}")
    monkeypatch.setenv("MLFLOW_ARTIFACT_ROOT", f"file:{artifact_root.as_posix()}")
    client = TestClient(app)
    payload = {
        "seed": 42,
        "observations": [
                {
                    "taxpayer_id": "normal",
                    "sector_name": "Retail",
                    "declared_sales": "100000",
                "declared_income": "100000",
                "invoice_sales": "110000",
                "customs_landed_cost": "10000",
                "withholding_income": "5000",
                "risk_signal_gap": "0",
                "rule_score": "10",
            },
                {
                    "taxpayer_id": "anomaly",
                    "sector_name": "Retail",
                    "declared_sales": "100000",
                "declared_income": "100000",
                "invoice_sales": "900000",
                "customs_landed_cost": "700000",
                "withholding_income": "250000",
                "risk_signal_gap": "500000",
                "rule_score": "80",
            },
        ],
    }

    first = client.post("/risk-scoring/train", json=payload)
    second = client.post("/risk-scoring/train", json=payload)

    assert first.status_code == 200
    assert second.status_code == 200
    assert first.json()["model_version"] == second.json()["model_version"]
    assert first.json()["algorithm"] == "ISOLATION_FOREST_WITH_PEER_PERCENTILE"
    assert first.json()["model_type"] == "UNSUPERVISED_ANOMALY"
    assert "featureColumns" in first.json()["metrics"]
    assert first.json()["metrics"]["officerReviewRequired"] is True
    assert "sector_peer_percentile" in first.json()["metrics"]["featureColumns"]
    predictions = first.json()["predictions"]
    assert predictions[0]["taxpayer_id"] == "anomaly"
    assert float(predictions[0]["combined_score"]) > 70
    assert predictions[0]["explanation"]["officerReviewRequired"] is True
    assert "invoiceSalesExceedDeclaredSales" in predictions[0]["main_contributing_features"]
