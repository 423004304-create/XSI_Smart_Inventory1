from pathlib import Path
from fastapi import FastAPI, File, UploadFile, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from ultralytics import YOLO
import cv2
import numpy as np

BASE_DIR = Path(__file__).resolve().parent
MODEL_PATH = BASE_DIR / "best.pt"
app = FastAPI(title="Xavery SmartInventory YOLOv8 API")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:8080", "http://127.0.0.1:8080",
                   "http://localhost:5500", "http://127.0.0.1:5500"],
    allow_credentials=False, allow_methods=["POST", "GET"], allow_headers=["*"],
)
model = None

@app.on_event("startup")
def load_model():
    global model
    if MODEL_PATH.exists():
        model = YOLO(str(MODEL_PATH))

@app.get("/health")
def health():
    return {"status": "ok", "model_loaded": model is not None,
            "model_path": str(MODEL_PATH)}

@app.post("/detect")
async def detect(file: UploadFile = File(...)):
    if model is None:
        raise HTTPException(status_code=503, detail="Model not loaded. Place trained weights at vision_api/best.pt.")
    if not (file.content_type or "").startswith("image/"):
        raise HTTPException(status_code=415, detail="Upload an image file.")
    raw = await file.read()
    if not raw:
        raise HTTPException(status_code=400, detail="Empty image.")
    image = cv2.imdecode(np.frombuffer(raw, np.uint8), cv2.IMREAD_COLOR)
    if image is None:
        raise HTTPException(status_code=400, detail="Could not decode image.")
    results = model.predict(source=image, verbose=False)
    detections = []
    for result in results:
        names = result.names
        if result.boxes is None:
            continue
        for box in result.boxes:
            class_id = int(box.cls.item())
            detections.append({
                "class_name": str(names[class_id]),
                "confidence": float(box.conf.item()),
                "bbox_xyxy": [float(v) for v in box.xyxy[0].tolist()]
            })
    return {"detections": detections, "count": len(detections)}
