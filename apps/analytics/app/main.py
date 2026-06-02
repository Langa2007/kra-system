from datetime import UTC, datetime

from fastapi import FastAPI, Request

from app.risk_scoring import TrainRiskModelRequest, TrainRiskModelResponse, train_and_score

app = FastAPI(
    title="Revenue Intelligence Analytics API",
    version="0.1.0",
    description="Analytics service foundation for risk scoring and model APIs.",
)


@app.get("/health", tags=["health"])
def health() -> dict[str, str]:
    return {
        "status": "UP",
        "service": "revenue-intelligence-analytics",
        "timestamp": datetime.now(UTC).isoformat(),
    }


@app.post("/risk-scoring/train", response_model=TrainRiskModelResponse, tags=["risk-scoring"])
async def train_risk_model(request: Request) -> TrainRiskModelResponse:
    body = await request.body()
    payload = {} if not body else await request.json()
    return train_and_score(TrainRiskModelRequest.model_validate(payload))
