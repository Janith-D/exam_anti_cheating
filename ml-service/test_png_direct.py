import sys, os
sys.path.insert(0, 'src')
import torch, torch.nn as nn
from torchvision import models, transforms
from PIL import Image
import numpy as np

IMAGES_ROOT = r'D:\voice-data-20260424T144123Z-3-001\voice-data-20260424T144123Z-3-001\images'
MODEL_PATH  = r'models/voice/cnn/voice.pth'

classes = sorted([d for d in os.listdir(IMAGES_ROOT) if os.path.isdir(os.path.join(IMAGES_ROOT, d))])
print('Classes:', classes)

device = torch.device('cpu')
model = models.resnet50(weights=None)
for p in model.parameters():
    p.requires_grad = False
model.fc = nn.Sequential(
    nn.Linear(2048, 512), nn.ReLU(), nn.Dropout(0.5),
    nn.Linear(512, 256), nn.ReLU(), nn.Dropout(0.3),
    nn.Linear(256, len(classes))
)
model.load_state_dict(torch.load(MODEL_PATH, map_location=device))
model.eval()
print('Model loaded OK\n')

tf = transforms.Compose([
    transforms.Resize((224, 224)),
    transforms.ToTensor(),
    transforms.Normalize([0.485, 0.456, 0.406], [0.229, 0.224, 0.225])
])
idx_to_class = {i: c for i, c in enumerate(classes)}

correct = 0
total = 0
for cls_name in classes:
    cls_dir = os.path.join(IMAGES_ROOT, cls_name)
    imgs = [f for f in os.listdir(cls_dir) if f.endswith('.png')][:5]
    for img_file in imgs:
        img = Image.open(os.path.join(cls_dir, img_file)).convert('RGB')
        t = tf(img).unsqueeze(0)
        with torch.no_grad():
            out = model(t)
            probs = torch.softmax(out, dim=1).squeeze(0).numpy()
        pred = int(np.argmax(probs))
        conf = float(probs[pred]) * 100
        match = idx_to_class[pred] == cls_name
        if match:
            correct += 1
        total += 1
        status = "OK" if match else "WRONG"
        print(f"  {cls_name}/{img_file[:22]:22s}: pred={idx_to_class[pred]} conf={conf:5.1f}%  {status}")

print(f'\nAccuracy on existing PNGs: {correct}/{total} = {100*correct/total:.1f}%')
