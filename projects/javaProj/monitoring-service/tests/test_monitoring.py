import cv2
import numpy as np
from fastapi.testclient import TestClient

import main


class Tensor:
    def __init__(self, values):
        self.values = values

    def item(self):
        return self.values[0]

    def tolist(self):
        return self.values


class Box:
    def __init__(self, class_id, confidence, coords):
        self.cls = Tensor([class_id])
        self.conf = Tensor([confidence])
        self.xyxy = [Tensor(coords)]


class Prediction:
    def __init__(self):
        self.boxes = [Box(0, 0.91, [1, 2, 8, 9]), Box(67, 0.77, [10, 11, 15, 18])]

    def plot(self):
        return np.zeros((12, 20, 3), dtype=np.uint8)


class FakeModel:
    def predict(self, image, **kwargs):
        assert kwargs["classes"] == [0, 67]
        return [Prediction()]


def jpeg():
    ok, encoded = cv2.imencode(".jpg", np.zeros((12, 20, 3), dtype=np.uint8))
    assert ok
    return encoded.tobytes()


def client(monkeypatch, tmp_path):
    monkeypatch.setattr(main, "SNAPSHOT_DIR", tmp_path)
    monkeypatch.setattr(main, "load_model", lambda: (FakeModel(), None))
    return TestClient(main.app)


def test_analysis_auth_and_snapshot_roundtrip(monkeypatch, tmp_path):
    with client(monkeypatch, tmp_path) as test_client:
        assert test_client.get("/health").json()["status"] == "ok"
        response = test_client.post("/analyze", headers={"X-Internal-Token": main.SERVICE_TOKEN},
                                    files={"file": ("frame.jpg", jpeg(), "image/jpeg")})
        assert response.status_code == 200
        body = response.json()
        assert body["personCount"] == 1
        assert body["phoneCount"] == 1
        assert body["multiplePeople"] is False
        assert body["phoneDetected"] is True
        assert len(body["detections"]) == 2

        image = test_client.get(f"/snapshots/{body['snapshotId']}", headers={"X-Internal-Token": main.SERVICE_TOKEN})
        assert image.status_code == 200
        assert image.headers["content-type"] == "image/jpeg"
        assert len(list(tmp_path.glob("*.jpg"))) == 1


def test_analysis_rejects_missing_token_invalid_type_and_corrupt_image(monkeypatch, tmp_path):
    with client(monkeypatch, tmp_path) as test_client:
        assert test_client.post("/analyze", files={"file": ("x.jpg", jpeg(), "image/jpeg")}).status_code == 401
        headers = {"X-Internal-Token": main.SERVICE_TOKEN}
        assert test_client.post("/analyze", headers=headers,
                                files={"file": ("x.txt", b"bad", "text/plain")}).status_code == 415
        assert test_client.post("/analyze", headers=headers,
                                files={"file": ("x.jpg", b"not an image", "image/jpeg")}).status_code == 400
