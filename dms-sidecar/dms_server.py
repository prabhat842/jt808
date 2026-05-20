#!/usr/bin/env python3
"""
DMS Sidecar — MediaPipe Face Landmarker eye-closure / distraction monitor.

Exposes:
  GET  /dms/state       → JSON with current DMS flags (consumed by Java simulator every 500 ms)
  POST /dms/seatbelt    → {"worn": true/false}  toggle seatbelt status
  GET  /health          → {"ok": true}

Alarm flag bitmask (matches Java DmsState constants):
  bit 0  fatigue (eyes closed)
  bit 1  distraction (face absent)
  bit 4  no seatbelt
  bit 5  camera blocked

Primary alarm types:
  0 none | 1 fatigue | 2 distraction | 5 no_seatbelt | 6 cam_blocked

Environment variables:
  DMS_CAMERA_INDEX      integer, default 0
  DMS_MODEL_PATH        path to face_landmarker.task, default same dir as this script
  DMS_HOST              bind address, default 127.0.0.1
  DMS_PORT              listen port,  default 7500
"""

import os
import pathlib
import threading
import time

import cv2
import mediapipe as mp
import numpy as np
from mediapipe.tasks import python as mp_python
from mediapipe.tasks.python import vision as mp_vision
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
import uvicorn

# ── Eye landmark indices for EAR (MediaPipe 478-point model) ─────────────────
# Layout per eye: [outer, top_a, top_b, inner, bottom_b, bottom_a]
RIGHT_EYE = [33,  160, 158, 133, 153, 144]
LEFT_EYE  = [362, 385, 387, 263, 373, 380]

EAR_CLOSED_THRESHOLD = 0.25   # avg EAR below this → eyes closed
FATIGUE_FPS          = 25     # assumed capture fps for degree calc

_SCRIPT_DIR = pathlib.Path(__file__).parent
_DEFAULT_MODEL = _SCRIPT_DIR / "face_landmarker.task"

# Alarm flags / types (mirror Java DmsState)
FLAG_FATIGUE      = 1
FLAG_DISTRACTION  = 1 << 1
FLAG_NO_SEATBELT  = 1 << 4
FLAG_CAM_BLOCKED  = 1 << 5

ALARM_NONE        = 0
ALARM_FATIGUE     = 1
ALARM_DISTRACTION = 2
ALARM_NO_SEATBELT = 5
ALARM_CAM_BLOCKED = 6


# ── helpers ───────────────────────────────────────────────────────────────────

def _dist(a, b):
    return float(np.linalg.norm(np.asarray(a) - np.asarray(b)))


def eye_aspect_ratio(landmarks, indices, w, h):
    """Eye Aspect Ratio using 6 MediaPipe landmark indices."""
    pts = [(landmarks[i].x * w, landmarks[i].y * h) for i in indices]
    vertical = _dist(pts[1], pts[5]) + _dist(pts[2], pts[4])
    horizontal = _dist(pts[0], pts[3])
    return vertical / (2.0 * horizontal) if horizontal > 0 else 0.0


# ── detector ─────────────────────────────────────────────────────────────────

class DmsDetector:
    def __init__(self, camera_index: int = 0, model_path: str | None = None):
        self._camera_index = camera_index
        self._seatbelt     = True
        self._closed_frames = 0
        self._lock  = threading.Lock()
        self._state = self._make_state(False, False, 0, False)

        resolved_model = model_path or str(_DEFAULT_MODEL)
        base_options = mp_python.BaseOptions(model_asset_path=resolved_model)
        options = mp_vision.FaceLandmarkerOptions(
            base_options=base_options,
            num_faces=1,
            min_face_detection_confidence=0.5,
            min_face_presence_confidence=0.5,
            min_tracking_confidence=0.5,
        )
        self._landmarker = mp_vision.FaceLandmarker.create_from_options(options)
        threading.Thread(target=self._capture_loop, daemon=True,
                         name="dms-capture").start()

    # ── public interface ──────────────────────────────────────────────────────

    @property
    def state(self) -> dict:
        with self._lock:
            return dict(self._state)

    def set_seatbelt(self, worn: bool):
        self._seatbelt = worn

    # ── internal ──────────────────────────────────────────────────────────────

    def _make_state(self, face_detected, eyes_closed, fatigue_degree, distracted):
        flags   = 0
        primary = ALARM_NONE

        if fatigue_degree > 0:
            flags  |= FLAG_FATIGUE
            primary = ALARM_FATIGUE
        if distracted:
            flags  |= FLAG_DISTRACTION
            if primary == ALARM_NONE:
                primary = ALARM_DISTRACTION
        if not self._seatbelt:
            flags  |= FLAG_NO_SEATBELT
            if primary == ALARM_NONE:
                primary = ALARM_NO_SEATBELT

        return {
            "faceDetected":  face_detected,
            "eyesClosed":    eyes_closed,
            "fatigueDegree": fatigue_degree,
            "distracted":    distracted,
            "seatbeltWorn":  self._seatbelt,
            "alarmFlags":    flags,
            "primaryAlarm":  primary,
        }

    def _capture_loop(self):
        cap = cv2.VideoCapture(self._camera_index)
        if not cap.isOpened():
            with self._lock:
                self._state = {
                    "faceDetected": False, "eyesClosed": False,
                    "fatigueDegree": 0,    "distracted": True,
                    "seatbeltWorn":  self._seatbelt,
                    "alarmFlags":    FLAG_CAM_BLOCKED,
                    "primaryAlarm":  ALARM_CAM_BLOCKED,
                }
            return

        try:
            while True:
                ret, frame = cap.read()
                if not ret:
                    time.sleep(0.04)
                    continue
                self._process(frame)
        finally:
            cap.release()

    def _process(self, frame):
        h, w = frame.shape[:2]
        rgb      = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb)
        result   = self._landmarker.detect(mp_image)

        face_detected = bool(result.face_landmarks)
        eyes_closed   = False
        distracted    = not face_detected

        if face_detected:
            lm    = result.face_landmarks[0]
            ear_r = eye_aspect_ratio(lm, RIGHT_EYE, w, h)
            ear_l = eye_aspect_ratio(lm, LEFT_EYE,  w, h)
            eyes_closed = (ear_r + ear_l) / 2.0 < EAR_CLOSED_THRESHOLD

            if eyes_closed:
                self._closed_frames += 1
            else:
                self._closed_frames = max(0, self._closed_frames - 2)
        else:
            self._closed_frames = max(0, self._closed_frames - 1)

        closed_seconds = self._closed_frames / FATIGUE_FPS
        fatigue_degree = min(10, int(closed_seconds * 2.5))

        with self._lock:
            self._state = self._make_state(face_detected, eyes_closed,
                                           fatigue_degree, distracted)


# ── FastAPI ───────────────────────────────────────────────────────────────────

app      = FastAPI(title="DMS Sidecar")
detector: DmsDetector | None = None


@app.on_event("startup")
def _startup():
    global detector
    cam_idx    = int(os.getenv("DMS_CAMERA_INDEX", "0"))
    model_path = os.getenv("DMS_MODEL_PATH") or None
    detector   = DmsDetector(camera_index=cam_idx, model_path=model_path)


class SeatbeltRequest(BaseModel):
    worn: bool


@app.get("/dms/state")
def get_state():
    if detector is None:
        raise HTTPException(status_code=503, detail="detector not initialised")
    return detector.state


@app.post("/dms/seatbelt")
def set_seatbelt(req: SeatbeltRequest):
    if detector is None:
        raise HTTPException(status_code=503, detail="detector not initialised")
    detector.set_seatbelt(req.worn)
    return {"worn": req.worn}


@app.get("/health")
def health():
    return {"ok": True}


if __name__ == "__main__":
    host = os.getenv("DMS_HOST", "127.0.0.1")
    port = int(os.getenv("DMS_PORT", "7500"))
    uvicorn.run(app, host=host, port=port)
