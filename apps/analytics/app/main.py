from datetime import UTC, datetime

from fastapi import FastAPI

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
