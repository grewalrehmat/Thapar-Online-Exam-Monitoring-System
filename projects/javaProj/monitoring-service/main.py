from __future__ import annotations

import os
import asyncio
import threading
import uuid
from io import BytesIO
from contextlib import asynccontextmanager
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Annotated

import cv2
import numpy as np
from fastapi import Depends, FastAPI, File, Header, HTTPException, UploadFile
from fastapi.responses import FileResponse
from pydantic import BaseModel
from PIL import Image, UnidentifiedImageError
from starlette.concurrency import run_in_threadpool


MAX_UPLOAD_BYTES = int(os.getenv("JAVALAB_MAX_FRAME_BYTES", 2 * 1024 * 1024))
CONFIDENCE = float(os.getenv("JAVALAB_YOLO_CONFIDENCE", "0.35"))
SNAPSHOT_DIR = Path(os.getenv("JAVALAB_SNAPSHOT_DIR", "./data/snapshots")).resolve()
MODEL_SOURCE = os.getenv("JAVALAB_YOLO_MODEL", "yolov8n.pt")
RETENTION_HOURS = int(os.getenv("JAVALAB_SNAPSHOT_RETENTION_HOURS", "2160"))
MAX_SNAPSHOTS = int(os.getenv("JAVALAB_MAX_SNAPSHOTS", "5000"))
SERVICE_TOKEN = os.getenv("JAVALAB_MONITORING_TOKEN", "local-dev-monitoring-token")


class Detection(BaseModel):
    classId: int
    label: str
    confidence: float
    box: list[float]


class AnalysisResult(BaseModel):
    timestamp: datetime
    snapshotId: str
    personCount: int
    phoneCount: int
    multiplePeople: bool
    phoneDetected: bool
    detections: list[Detection]


def require_service_token(x_internal_token: Annotated[str | None, Header()] = None) -> None:
    if not x_internal_token or not _constant_time_equal(x_internal_token, SERVICE_TOKEN):
        raise HTTPException(status_code=401, detail="Invalid service token")


def _constant_time_equal(left: str, right: str) -> bool:
    import hmac

    return hmac.compare_digest(left.encode(), right.encode())


def cleanup_snapshots(now: datetime | None = None) -> None:
    SNAPSHOT_DIR.mkdir(parents=True, exist_ok=True)
    current = now or datetime.now(timezone.utc)
    cutoff = current - timedelta(hours=RETENTION_HOURS)
    files = sorted(SNAPSHOT_DIR.glob("*.jpg"), key=lambda item: item.stat().st_mtime)
    retained: list[Path] = []
    for path in files:
        modified = datetime.fromtimestamp(path.stat().st_mtime, timezone.utc)
        if modified < cutoff:
            path.unlink(missing_ok=True)
        else:
            retained.append(path)
    for path in retained[: max(0, len(retained) - MAX_SNAPSHOTS)]:
        path.unlink(missing_ok=True)


def load_model():
    try:
        from ultralytics import YOLO

        # Ultralytics downloads its official lightweight weight on first use when absent.
        # Offline campuses can set JAVALAB_YOLO_MODEL to a mounted local weight file.
        return YOLO(MODEL_SOURCE), None
    except Exception as error:  # surfaced through /health; do not claim monitoring is ready
        return None, f"YOLO model could not load: {type(error).__name__}"


@asynccontextmanager
async def lifespan(app: FastAPI):
    SNAPSHOT_DIR.mkdir(parents=True, exist_ok=True)
    cleanup_snapshots()
    model, error = await run_in_threadpool(load_model)
    app.state.model = model
    app.state.model_error = error
    app.state.inference_lock = threading.Lock()
    cleanup_task = asyncio.create_task(snapshot_cleanup_loop())
    try:
        yield
    finally:
        cleanup_task.cancel()
        try:
            await cleanup_task
        except asyncio.CancelledError:
            pass


async def snapshot_cleanup_loop() -> None:
    while True:
        await asyncio.sleep(3600)
        await run_in_threadpool(cleanup_snapshots)


app = FastAPI(title="JavaLab Monitoring Service", version="1.0.0", lifespan=lifespan)


@app.get("/health")
def health():
    if getattr(app.state, "model", None) is None:
        return {"status": "unavailable", "reason": getattr(app.state, "model_error", "Model is not initialized")}
    return {"status": "ok", "model": Path(MODEL_SOURCE).name}


def analyze_frame(model, image: np.ndarray) -> tuple[AnalysisResult, bytes]:
    results = model.predict(image, classes=[0, 67], conf=CONFIDENCE, verbose=False)
    result = results[0]
    boxes = getattr(result, "boxes", None)
    detections: list[Detection] = []
    if boxes is not None:
        for box in boxes:
            class_id = int(box.cls[0].item())
            if class_id not in (0, 67):
                continue
            confidence = float(box.conf[0].item())
            coords = [float(value) for value in box.xyxy[0].tolist()]
            detections.append(Detection(classId=class_id, label="person" if class_id == 0 else "cell phone",
                                        confidence=confidence, box=coords))
    annotated = result.plot()
    success, encoded = cv2.imencode(".jpg", annotated, [int(cv2.IMWRITE_JPEG_QUALITY), 82])
    if not success:
        raise RuntimeError("Could not encode annotated image")
    people = sum(detection.classId == 0 for detection in detections)
    phones = sum(detection.classId == 67 for detection in detections)
    response = AnalysisResult(timestamp=datetime.now(timezone.utc), snapshotId=str(uuid.uuid4()),
                              personCount=people, phoneCount=phones, multiplePeople=people > 1,
                              phoneDetected=phones > 0, detections=detections)
    return response, encoded.tobytes()


@app.post("/analyze", response_model=AnalysisResult, dependencies=[Depends(require_service_token)])
async def analyze(file: UploadFile = File(...)):
    if file.content_type not in {"image/jpeg", "image/png"}:
        raise HTTPException(status_code=415, detail="Upload a JPEG or PNG image")
    content = await file.read(MAX_UPLOAD_BYTES + 1)
    await file.close()
    if not content or len(content) > MAX_UPLOAD_BYTES:
        raise HTTPException(status_code=413 if content else 400, detail="Frame is empty or too large")
    try:
        with Image.open(BytesIO(content)) as header:
            if header.format not in {"JPEG", "PNG"}:
                raise HTTPException(status_code=415, detail="Upload a JPEG or PNG image")
            if header.width > 4000 or header.height > 4000:
                raise HTTPException(status_code=413, detail="Image dimensions exceed the limit")
            header.verify()
    except (UnidentifiedImageError, OSError) as error:
        raise HTTPException(status_code=400, detail="Image could not be decoded") from error
    image = cv2.imdecode(np.frombuffer(content, dtype=np.uint8), cv2.IMREAD_COLOR)
    if image is None or image.size == 0:
        raise HTTPException(status_code=400, detail="Image could not be decoded")
    if image.shape[0] > 4000 or image.shape[1] > 4000:
        raise HTTPException(status_code=413, detail="Image dimensions exceed the limit")
    model = getattr(app.state, "model", None)
    if model is None:
        raise HTTPException(status_code=503, detail="YOLO model is unavailable")
    try:
        lock = getattr(app.state, "inference_lock", threading.Lock())
        result, annotated = await run_in_threadpool(_locked_inference, lock, model, image)
    except Exception as error:
        raise HTTPException(status_code=502, detail=f"Inference failed: {type(error).__name__}") from error
    SNAPSHOT_DIR.mkdir(parents=True, exist_ok=True)
    snapshot_path = SNAPSHOT_DIR / f"{result.snapshotId}.jpg"
    snapshot_path.write_bytes(annotated)
    cleanup_snapshots()
    return result


def _locked_inference(lock: threading.Lock, model, image: np.ndarray):
    with lock:
        return analyze_frame(model, image)


@app.get("/snapshots/{snapshot_id}", dependencies=[Depends(require_service_token)])
def get_snapshot(snapshot_id: str):
    try:
        canonical_id = str(uuid.UUID(snapshot_id))
    except ValueError as error:
        raise HTTPException(status_code=404, detail="Snapshot not found") from error
    path = SNAPSHOT_DIR / f"{canonical_id}.jpg"
    if not path.is_file() or path.resolve().parent != SNAPSHOT_DIR:
        raise HTTPException(status_code=404, detail="Snapshot not found")
    return FileResponse(path, media_type="image/jpeg", filename="monitoring-snapshot.jpg")
