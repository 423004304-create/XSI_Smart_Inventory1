# Optional YOLOv8 API

## Setup
Use Python 3.10 or newer in a virtual environment:

```bash
python -m venv .venv
# Windows:
.venv\Scripts\activate
# macOS/Linux:
source .venv/bin/activate
pip install -r requirements.txt
```

The YOLOv8 weights from the supplied project are included as `best.pt`. From the `vision_api` folder run:

```bash
uvicorn main:app --host 127.0.0.1 --port 8000 --reload
```

Test `http://127.0.0.1:8000/health`. The response should show `model_loaded: true`.

## Model classes
The included model recognizes Adelin Brake System, BOMX Mags, CCP Suspension, Daytona Quick Throttle, SMOK Supernova Biled Projector, and Shijiro Motegi Pipe. Detection returns a `class_name`; the user can choose the matching catalog product before recording a sale or stock movement. Validate the model on representative shop images before using it for inventory decisions.
