"""Tests for call-level scoring and short sentences (Whisper stubbed out)."""
import io
import os
import sys
import types
import unittest
from unittest import mock

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

if "transcriber" not in sys.modules:  # test_api.py may already have installed a stub
    stub = types.ModuleType("transcriber")
    stub.transcribe_audio_local = lambda path: ""
    sys.modules["transcriber"] = stub

import app as app_module  # noqa: E402
from textcheck import check_text  # noqa: E402

CHUNKS = [
    "Hello sir, I am calling from the bank security department. We noticed some unusual activity on your account.",
    "For your safety we need to verify your identity. Please keep your debit card ready and do not disconnect the call.",
    "If you do not cooperate your account will be blocked today and a case will be filed against you.",
]


class ShortSentences(unittest.TestCase):
    def test_share_your_otp_is_not_safe(self):
        self.assertNotEqual(check_text("Please share your OTP")["classification"], "SAFE")

    def test_short_text_with_only_soft_signals_is_still_scaled_down(self):
        self.assertEqual(check_text("bank account")["classification"], "SAFE")


class CallSession(unittest.TestCase):
    def post_chunk(self, text, call_id):
        client = app_module.app.test_client()
        data = {"test": (io.BytesIO(b"x"), "chunk.wav"), "call_id": call_id}
        with mock.patch.object(app_module.transcriber, "transcribe_audio_local", return_value=text):
            return client.post("/", data=data, content_type="multipart/form-data").get_json()

    def test_score_grows_as_the_call_goes_on(self):
        scores = [self.post_chunk(c, "call-A")["score"] for c in CHUNKS]
        self.assertEqual(scores, sorted(scores))          # never goes down
        self.assertEqual(self.post_chunk(CHUNKS[2], "call-A")["classification"], "SCAM")

    def test_different_calls_do_not_mix(self):
        self.post_chunk(CHUNKS[0], "call-B")
        self.post_chunk(CHUNKS[1], "call-B")
        other = self.post_chunk("Hello bhai kya chal raha hai sab theek hai", "call-C")
        self.assertEqual(other["classification"], "SAFE")

    def test_without_call_id_chunks_are_scored_alone(self):
        client = app_module.app.test_client()
        with mock.patch.object(app_module.transcriber, "transcribe_audio_local", return_value=CHUNKS[1]):
            r = client.post("/", data={"test": (io.BytesIO(b"x"), "c.wav")}, content_type="multipart/form-data")
        self.assertEqual(r.get_json()["score"], check_text(CHUNKS[1])["score"])


if __name__ == "__main__":
    unittest.main()
