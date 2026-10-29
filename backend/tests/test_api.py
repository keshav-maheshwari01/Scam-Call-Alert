"""API tests with the Whisper model stubbed out (fast, no model download)."""
import io
import os
import sys
import types
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

# Stub the `transcriber` module BEFORE app.py imports it (it would load the real model).
fake = types.ModuleType("transcriber")
fake.transcribe_audio_local = lambda path: "Sir aapka account band ho jayega abhi OTP dijiye turant"
sys.modules["transcriber"] = fake

from app import app  # noqa: E402


class TestApi(unittest.TestCase):
    def setUp(self):
        self.client = app.test_client()

    def test_health(self):
        self.assertEqual(self.client.get("/health").status_code, 200)

    def test_missing_file_returns_400(self):
        self.assertEqual(self.client.post("/").status_code, 400)

    def test_scam_audio_is_flagged(self):
        data = {"test": (io.BytesIO(b"fake-audio"), "call.wav"), "number": "+923001234567"}
        r = self.client.post("/", data=data, content_type="multipart/form-data")
        self.assertEqual(r.status_code, 200)
        body = r.get_json()
        self.assertEqual(body["classification"], "SCAM")
        self.assertTrue(body["suspicious_caller"])

    def test_bad_audio_returns_500_not_crash(self):
        fake.transcribe_audio_local = lambda path: (_ for _ in ()).throw(RuntimeError("ffmpeg missing"))
        try:
            r = self.client.post("/", data={"test": (io.BytesIO(b"x"), "call.wav")}, content_type="multipart/form-data")
        finally:
            fake.transcribe_audio_local = lambda path: "Sir aapka account band ho jayega abhi OTP dijiye turant"
        self.assertEqual(r.status_code, 500)


if __name__ == "__main__":
    unittest.main()
