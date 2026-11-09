from flask import Flask, request, jsonify
from ultralytics import YOLO
import os
import torch

torch.set_num_threads(1)

app = Flask(__name__)

# Load your newly trained Xavery model weights
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
model_path = os.path.join(BASE_DIR, 'models', 'best.pt')
model = YOLO(model_path)

@app.route('/', methods=['GET'])
def home():
    return jsonify({
        "status": "Xavery SmartInventory AI Server is running",
        "model_loaded": True,
        "classes": model.names
    })

@app.route('/predict', methods=['POST'])
@app.route('/detect', methods=['POST'])
def predict():
    if 'image' not in request.files:
        return jsonify({'error': 'No image uploaded'}), 400

    file = request.files['image']
    img_path = 'temp.jpg'
    file.save(img_path)

    # Run YOLO prediction on the uploaded image
    import torch
    with torch.no_grad():
        results = model(img_path, device='cpu', verbose=False)

    # Extract detection results
    detections = []
    for r in results:
        for box in r.boxes:
            cls_id = int(box.cls[0])
            conf = float(box.conf[0])
            class_name = model.names[cls_id]
            detections.append({
                'class': class_name,
                'confidence': conf
            })

    # Clean up temporary image
    if os.path.exists(img_path):
        os.remove(img_path)

    return jsonify({'success': True, 'detections': detections})

if __name__ == '__main__':
    port = int(os.environ.get('PORT', 5000))
    app.run(host='0.0.0.0', port=port, debug=True)
