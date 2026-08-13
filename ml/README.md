# AgriScout ML Training Pipeline

This folder holds training notebooks, dataset scripts, and exported model artifacts for the Smart Agriculture ERP AI module.

## Crop calendar (Phase 1 — live in app)

Cultivation schedules live in:

- `app/src/main/assets/crop_calendars/*.json` (runtime)
- `ml/crop_calendars/` (mirror for documentation)

The **Crop Calendar Engine** on Android reads these profiles offline. ML models (CNN, text classifier, yield regression) will extend this backbone in later phases.

## Planned models

| Model | Algorithm | Artifact | Runtime |
| --- | --- | --- | --- |
| Plant disease (image) | MobileNetV3 CNN | `models/v1/plant_disease.tflite` | Android TFLite |
| Symptom text | TF-IDF + Logistic Regression | `models/v1/symptom_classifier.joblib` | Android |
| Yield / harvest | XGBoost Regressor | `models/v1/yield_regressor.joblib` | Cloud Function |
| Sensor anomaly | Isolation Forest | `models/v1/sensor_anomaly.joblib` | Cloud Function |

## Setup (when training)

```bash
cd ml
python -m venv .venv
.venv\Scripts\activate   # Windows
pip install -r requirements.txt
jupyter lab
```

Do not commit large dataset files or API keys.
