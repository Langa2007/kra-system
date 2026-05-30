# Revenue Intelligence Analytics

FastAPI service skeleton for analytics, model scoring, and data science APIs.

## Start Locally

```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r apps/analytics/requirements.txt
uvicorn app.main:app --app-dir apps/analytics --reload --port 8090
```

Health endpoint:

```text
http://localhost:8090/health
```

## Test

```powershell
python -m pytest apps/analytics
```
