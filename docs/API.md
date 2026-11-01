# API reference

Base URL: `http://<server>:5000`

## `GET /health`
Liveness check.

```json
{ "status": "ok" }
```

## `POST /`
Analyse a call recording.

**Request** — `multipart/form-data`

| Field | Required | Description |
|---|---|---|
| `test` | yes | Audio file (wav, mp3, mpeg, m4a… anything ffmpeg can read). Max `MAX_UPLOAD_MB` (default 25) |
| `number` | no | Caller's phone number, e.g. `+923001234567`. `caller_number` is accepted as an alias |
| `call_id` | no | Any string that is the same for every chunk of one call. When present, the server scores everything heard so far in that call, not only this chunk |

```bash
curl -X POST http://localhost:5000/ -F "test=@call.wav" -F "number=+923001234567"
```

**Response `200`**

```json
{
  "classification": "SCAM",
  "confidence": 0.95,
  "score": 100,
  "red_flags_found": ["account band", "otp", "otp dijiye"],
  "pattern_matches": 4,
  "yellow_flag_count": 1,
  "suspicious_caller": true,
  "reason": "4 critical scam pattern(s) found | 3 red flag phrase(s): account band, otp, otp dijiye | 1 suspicious word(s) | suspicious caller number detected",
  "word_count": 11
}
```

| Field | Type | Meaning |
|---|---|---|
| `classification` | `SCAM` \| `ALERT` \| `SAFE` | `score ≥ 70` / `≥ 40` / otherwise |
| `confidence` | float 0–1 | Heuristic confidence in the classification |
| `score` | int 0–100 | Risk score |
| `red_flags_found` | string[] | Red-flag phrases that matched (sorted) |
| `pattern_matches` | int | Number of critical regex patterns that matched |
| `yellow_flag_count` | int | Number of soft keywords that matched |
| `suspicious_caller` | bool | Caller number matched a suspicious prefix |
| `reason` | string | Human-readable summary |
| `word_count` | int | Words in the transcript |

**Errors** — body is `{"error": "<message>"}`

| Status | When |
|---|---|
| `400` | No file in field `test`, or empty filename |
| `413` | Upload larger than `MAX_UPLOAD_MB` |
| `500` | Transcription failed (details only in server logs) |

## Notes
- There is no authentication. Do not expose the server to the internet as is.
- Uploads are written to a unique temp file and deleted right after transcription.
  Transcripts are never logged.
