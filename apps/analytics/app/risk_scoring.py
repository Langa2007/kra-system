from __future__ import annotations

import json
import os
from decimal import ROUND_HALF_UP, Decimal
from hashlib import sha256
from typing import Any

import mlflow
import mlflow.sklearn
import pandas as pd
from pydantic import BaseModel, Field
from sklearn.ensemble import IsolationForest

try:
    import shap
except ImportError:  # pragma: no cover - dependency is declared, this keeps local dev graceful.
    shap = None


class TaxpayerObservation(BaseModel):
    taxpayer_id: str
    sector_name: str | None = None
    declared_sales: Decimal = Decimal("0")
    declared_income: Decimal = Decimal("0")
    invoice_sales: Decimal = Decimal("0")
    customs_landed_cost: Decimal = Decimal("0")
    withholding_income: Decimal = Decimal("0")
    risk_signal_gap: Decimal = Decimal("0")
    rule_score: Decimal = Decimal("0")
    return_count: int = 0
    invoice_count: int = 0
    customs_count: int = 0
    withholding_count: int = 0
    open_signal_count: int = 0


class TrainRiskModelRequest(BaseModel):
    model_name: str = "PHASE12_UNSUPERVISED_RISK_SCORING"
    seed: int = 42
    observations: list[TaxpayerObservation] = Field(default_factory=list)


class RiskPrediction(BaseModel):
    taxpayer_id: str
    model_score: Decimal
    combined_score: Decimal
    confidence_score: Decimal
    main_contributing_features: list[str]
    explanation: dict[str, Any]


class TrainRiskModelResponse(BaseModel):
    model_name: str
    model_version: str
    model_type: str
    algorithm: str
    training_data_summary: str
    reproducibility_seed: int
    mlflow_run_id: str | None
    metrics: dict[str, Any]
    predictions: list[RiskPrediction]


ALGORITHM = "ISOLATION_FOREST_WITH_PEER_PERCENTILE"
MODEL_TYPE = "UNSUPERVISED_ANOMALY"
FEATURE_COLUMNS = [
    "invoice_to_declared_sales",
    "customs_to_declared_income",
    "withholding_to_declared_income",
    "risk_signal_gap_scaled",
    "rule_score_scaled",
    "revenue_footprint_scaled",
    "peer_group_percentile",
    "sector_peer_percentile",
]


def train_and_score(request: TrainRiskModelRequest) -> TrainRiskModelResponse:
    observations = sorted(request.observations, key=lambda observation: observation.taxpayer_id)
    if not observations:
        version = _model_version(request.model_name, request.seed, observations)
        return TrainRiskModelResponse(
            model_name=request.model_name,
            model_version=version,
            model_type=MODEL_TYPE,
            algorithm=ALGORITHM,
            training_data_summary=(
                "IsolationForest training skipped because no observations arrived"
            ),
            reproducibility_seed=request.seed,
            mlflow_run_id=None,
            metrics={
                "algorithm": ALGORITHM,
                "featureColumns": FEATURE_COLUMNS,
                "predictionCount": 0,
                "averageModelScore": Decimal("0.00"),
                "highRiskCount": 0,
                "reproducibilitySeed": request.seed,
                "shapAvailable": False,
                "officerReviewRequired": True,
            },
            predictions=[],
        )
    dataframe = _feature_frame(observations)
    model = IsolationForest(
        contamination="auto",
        n_estimators=128,
        random_state=request.seed,
    )
    model.fit(dataframe[FEATURE_COLUMNS])

    isolation_scores = _normalize_scores(-model.decision_function(dataframe[FEATURE_COLUMNS]))
    shap_contributions = _shap_contributions(model, dataframe[FEATURE_COLUMNS])
    version = _model_version(request.model_name, request.seed, observations)
    predictions = [
        _prediction(
            observation,
            dataframe.iloc[index].to_dict(),
            isolation_scores[index],
            shap_contributions[index],
        )
        for index, observation in enumerate(observations)
    ]
    predictions.sort(key=lambda prediction: prediction.combined_score, reverse=True)

    metrics = {
        "algorithm": ALGORITHM,
        "featureColumns": FEATURE_COLUMNS,
        "predictionCount": len(predictions),
        "averageModelScore": _round(
            sum((prediction.model_score for prediction in predictions), Decimal("0"))
            / max(len(predictions), 1)
        ),
        "highRiskCount": sum(1 for prediction in predictions if prediction.combined_score >= 70),
        "reproducibilitySeed": request.seed,
        "shapAvailable": shap is not None and any(shap_contributions),
        "officerReviewRequired": True,
    }
    mlflow_run_id = _log_mlflow_run(request, version, metrics, model, dataframe)
    return TrainRiskModelResponse(
        model_name=request.model_name,
        model_version=version,
        model_type=MODEL_TYPE,
        algorithm=ALGORITHM,
        training_data_summary=(
            f"IsolationForest trained on {len(observations)} taxpayer observations "
            f"and {len(FEATURE_COLUMNS)} engineered features"
        ),
        reproducibility_seed=request.seed,
        mlflow_run_id=mlflow_run_id,
        metrics=metrics,
        predictions=predictions,
    )


def _feature_frame(observations: list[TaxpayerObservation]) -> pd.DataFrame:
    rows = []
    footprints = [_revenue_footprint(observation) for observation in observations]
    sorted_footprints = sorted(footprints)
    max_footprint = max(footprints or [Decimal("1")], default=Decimal("1"))
    sector_footprints: dict[str, list[Decimal]] = {}
    for observation, footprint in zip(observations, footprints, strict=True):
        sector = observation.sector_name or "UNSPECIFIED"
        sector_footprints.setdefault(sector, []).append(footprint)
    sector_footprints = {
        sector: sorted(values) for sector, values in sector_footprints.items()
    }
    for observation, footprint in zip(observations, footprints, strict=True):
        sector = observation.sector_name or "UNSPECIFIED"
        rows.append(
            {
                "taxpayer_id": observation.taxpayer_id,
                "invoice_to_declared_sales": float(
                    _ratio_gap(observation.invoice_sales, observation.declared_sales)
                ),
                "customs_to_declared_income": float(
                    _ratio_gap(observation.customs_landed_cost, observation.declared_income)
                ),
                "withholding_to_declared_income": float(
                    _ratio_gap(observation.withholding_income, observation.declared_income)
                ),
                "risk_signal_gap_scaled": float(
                    observation.risk_signal_gap / Decimal("100000.00")
                ),
                "rule_score_scaled": float(_cap(observation.rule_score, Decimal("100")) / 100),
                "revenue_footprint_scaled": float(footprint / max(max_footprint, Decimal("1"))),
                "peer_group_percentile": float(_percentile(footprint, sorted_footprints) / 100),
                "sector_peer_percentile": float(
                    _percentile(footprint, sector_footprints[sector]) / 100
                ),
            }
        )
    return pd.DataFrame(rows, columns=["taxpayer_id", *FEATURE_COLUMNS])


def _prediction(
    observation: TaxpayerObservation,
    feature_row: dict[str, Any],
    isolation_score: Decimal,
    shap_values: dict[str, Decimal],
) -> RiskPrediction:
    fallback_contributions = {
        "invoiceSalesExceedDeclaredSales": _round(
            Decimal(str(feature_row["invoice_to_declared_sales"])) * Decimal("30")
        ),
        "customsValueExceedsDeclaredIncome": _round(
            Decimal(str(feature_row["customs_to_declared_income"])) * Decimal("24")
        ),
        "withholdingIncomeExceedsDeclaredIncome": _round(
            Decimal(str(feature_row["withholding_to_declared_income"])) * Decimal("18")
        ),
        "riskSignalGapPressure": _round(
            Decimal(str(feature_row["risk_signal_gap_scaled"])) * Decimal("12")
        ),
        "peerGroupOutlierPercentile": _round(
            Decimal(str(feature_row["peer_group_percentile"])) * Decimal("10")
        ),
        "sectorOutlierPercentile": _round(
            Decimal(str(feature_row["sector_peer_percentile"])) * Decimal("10")
        ),
        "ruleScoreSignal": _round(Decimal(str(feature_row["rule_score_scaled"])) * Decimal("6")),
    }
    contributions = (
        shap_values
        if shap_values and sum(shap_values.values(), Decimal("0")) > Decimal("0")
        else fallback_contributions
    )
    contribution_score = _cap(sum(fallback_contributions.values(), Decimal("0")), Decimal("100"))
    model_score = max(isolation_score, contribution_score)
    rule_score = _cap(observation.rule_score, Decimal("100"))
    combined_score = _round((model_score * Decimal("0.65")) + (rule_score * Decimal("0.35")))
    main_features = [
        name
        for name, value in sorted(contributions.items(), key=lambda item: item[1], reverse=True)
        if value > 0
    ][:4]
    return RiskPrediction(
        taxpayer_id=observation.taxpayer_id,
        model_score=model_score,
        combined_score=combined_score,
        confidence_score=_confidence(observation),
        main_contributing_features=main_features,
        explanation={
            "source": "AI_RISK_SCORING",
            "algorithm": ALGORITHM,
            "officerReviewRequired": True,
            "isolationForestScore": isolation_score,
            "modelScore": model_score,
            "ruleScore": rule_score,
            "combinedScore": combined_score,
            "peerGroupPercentile": _round(Decimal(str(feature_row["peer_group_percentile"])) * 100),
            "sectorPeerPercentile": _round(
                Decimal(str(feature_row["sector_peer_percentile"])) * 100
            ),
            "mainContributingFeatures": main_features,
            "featureContributions": contributions,
            "shapAvailable": bool(shap_values),
        },
    )


def _shap_contributions(
    model: IsolationForest,
    feature_frame: pd.DataFrame,
) -> list[dict[str, Decimal]]:
    if shap is None or feature_frame.empty:
        return [{} for _ in range(len(feature_frame))]
    try:
        explainer = shap.TreeExplainer(model)
        values = explainer.shap_values(feature_frame)
        rows = values if hasattr(values, "__iter__") else []
    except Exception:
        return [{} for _ in range(len(feature_frame))]

    contributions: list[dict[str, Decimal]] = []
    for row in rows:
        row_values = {
            _feature_label(name): _round(abs(Decimal(str(value))) * Decimal("10"))
            for name, value in zip(FEATURE_COLUMNS, row, strict=True)
        }
        contributions.append(row_values)
    return contributions


def _normalize_scores(values: Any) -> list[Decimal]:
    raw = [Decimal(str(value)) for value in values]
    if not raw:
        return []
    minimum = min(raw)
    maximum = max(raw)
    spread = max(maximum - minimum, Decimal("0.000001"))
    return [_round(((value - minimum) / spread) * Decimal("100")) for value in raw]


def _log_mlflow_run(
    request: TrainRiskModelRequest,
    version: str,
    metrics: dict[str, Any],
    model: IsolationForest,
    dataframe: pd.DataFrame,
) -> str | None:
    try:
        mlflow.set_tracking_uri(os.getenv("MLFLOW_TRACKING_URI", "sqlite:///./mlflow.db"))
        _ensure_experiment(request.model_name)
        with mlflow.start_run(run_name=version) as run:
            mlflow.set_tag("phase", "12")
            mlflow.set_tag("governance", "officer_review_required")
            mlflow.log_param("algorithm", ALGORITHM)
            mlflow.log_param("seed", request.seed)
            mlflow.log_param("feature_columns", ",".join(FEATURE_COLUMNS))
            mlflow.log_metric("training_rows", len(dataframe))
            mlflow.log_metric("high_risk_count", metrics["highRiskCount"])
            mlflow.sklearn.log_model(model, artifact_path="isolation_forest_model")
            return run.info.run_id
    except Exception:
        return None


def _ensure_experiment(model_name: str) -> None:
    client = mlflow.tracking.MlflowClient()
    experiment = client.get_experiment_by_name(model_name)
    if experiment is None:
        client.create_experiment(
            model_name,
            artifact_location=os.getenv("MLFLOW_ARTIFACT_ROOT", "file:./mlruns"),
        )
    mlflow.set_experiment(model_name)


def _ratio_gap(observed: Decimal, declared: Decimal) -> Decimal:
    positive_gap = max(observed - declared, Decimal("0"))
    return positive_gap / max(declared, Decimal("1"))


def _revenue_footprint(observation: TaxpayerObservation) -> Decimal:
    return (
        observation.invoice_sales
        + observation.customs_landed_cost
        + observation.withholding_income
        + observation.risk_signal_gap
    )


def _percentile(value: Decimal, sorted_values: list[Decimal]) -> Decimal:
    if not sorted_values:
        return Decimal("0")
    lower_or_equal = sum(1 for candidate in sorted_values if candidate <= value)
    return _round((Decimal(lower_or_equal) * Decimal("100")) / Decimal(len(sorted_values)))


def _confidence(observation: TaxpayerObservation) -> Decimal:
    evidence_sources = sum(
        [
            observation.return_count > 0,
            observation.invoice_count > 0,
            observation.customs_count > 0,
            observation.withholding_count > 0,
            observation.open_signal_count > 0,
        ]
    )
    return min(Decimal("55.00") + (Decimal(evidence_sources) * Decimal("8.00")), Decimal("95.00"))


def _model_version(model_name: str, seed: int, observations: list[TaxpayerObservation]) -> str:
    payload = "|".join(
        [
            model_name,
            str(seed),
            *[_observation_json(observation) for observation in observations],
        ]
    )
    return f"phase12-{sha256(payload.encode()).hexdigest()[:12]}"


def _observation_json(observation: TaxpayerObservation) -> str:
    if hasattr(observation, "model_dump"):
        payload = observation.model_dump(mode="json")
    else:
        payload = observation.dict()
    return json.dumps(payload, sort_keys=True, default=str)


def _feature_label(value: str) -> str:
    return {
        "invoice_to_declared_sales": "invoiceSalesExceedDeclaredSales",
        "customs_to_declared_income": "customsValueExceedsDeclaredIncome",
        "withholding_to_declared_income": "withholdingIncomeExceedsDeclaredIncome",
        "risk_signal_gap_scaled": "riskSignalGapPressure",
        "rule_score_scaled": "ruleScoreSignal",
        "revenue_footprint_scaled": "revenueFootprintOutlier",
        "peer_group_percentile": "peerGroupOutlierPercentile",
        "sector_peer_percentile": "sectorOutlierPercentile",
    }[value]


def _cap(value: Decimal, cap: Decimal) -> Decimal:
    return _round(min(max(value, Decimal("0")), cap))


def _round(value: Decimal) -> Decimal:
    return value.quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)
