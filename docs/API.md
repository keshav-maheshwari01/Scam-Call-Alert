# API reference

Base URL: `http://<server>:5000` (the port is configurable, see [Configuration](GETTING_STARTED.md#configuration)).

There is no authentication. Do not expose the server to the internet as it is.

## `GET /health`

Liveness check.

```json
{ "status": "ok" }
```

## `POST /`

Analyse one chunk of a call recording. This is the endpoint the Android app uses.

**Request**: `multipart/form-data`

| Field | Required | Description |
|---|---|---|
| `test` | yes | The audio file (wav, mp3, mpeg, m4a… anything ffmpeg can read). Larger than `MAX_UPLOAD_MB` (default 25) is rejected |
| `number` | no | The caller's phone number, for example `+923001234567`. `caller_number` is accepted as an alias. Numbers matching a suspicious prefix add 30 points |
| `call_id` | no | Any string that stays the same for every chunk of one call (the app uses a random UUID). When present, the server scores **everything heard so far in that call**, not only this chunk. At most 64 characters are used |

```bash
curl -X POST http://localhost:5000/ \
     -F "test=@backend/samples/test_scam.mpeg" \
     -F "number=+923001234567" \
     -F "call_id=my-test-call"
```

**Response `200`** (this example is the second chunk of a call: the server scored both chunks together)

```json
{
  "classification": "ALERT",
  "confidence": 0.66,
  "pattern_matches": 0,
  "reason": "1 red flag phrase(s): unusual activity | 5 suspicious word(s)",
  "red_flags_found": [
    "unusual activity"
  ],
  "score": 55,
  "suspicious_caller": false,
  "word_count": 39,
  "yellow_flag_count": 5
}
```

The keys are returned in alphabetical order.

| Field | Type | Meaning |
|---|---|---|
| `classification` | `SCAM` \| `ALERT` \| `SAFE` | `SCAM` if `score ≥ 70`, `ALERT` if `score ≥ 40`, otherwise `SAFE` |
| `score` | integer 0–100 | The risk score, see [Scoring](SCORING.md) |
| `confidence` | number 0–1 | A heuristic confidence in the classification, not a probability |
| `red_flags_found` | string[] | The red-flag phrases that matched, in no particular order |
| `pattern_matches` | integer | How many critical patterns matched |
| `yellow_flag_count` | integer | How many soft keywords matched |
| `suspicious_caller` | boolean | Whether the caller number matched a suspicious prefix |
| `reason` | string | A human-readable summary |
| `word_count` | integer | Words in the text that was scored (the whole call so far when `call_id` is used) |

## `POST /analyze/text`

A quick way to test the scoring rules without audio. Send JSON:

```bash
curl -X POST http://localhost:5000/analyze/text \
     -H "Content-Type: application/json" \
     -d '{"text": "Sir aapka account band ho jayega abhi OTP dijiye", "number": "+923001234567"}'
```

```json
{
  "classification": "SCAM",
  "confidence": 0.95,
  "pattern_matches": 4,
  "reason": "4 critical scam pattern(s) found | 3 red flag phrase(s): otp, otp dijiye, account band | 1 suspicious word(s) | suspicious caller number detected",
  "red_flags_found": [
    "account band",
    "otp dijiye",
    "otp"
  ],
  "score": 100,
  "suspicious_caller": true,
  "word_count": 9,
  "yellow_flag_count": 1
}
```

`text` is required. `number` is optional. This endpoint does not use `call_id`.

## Errors

The body is always `{"error": "<message>"}`.

| Status | When |
|---|---|
| `400` | `POST /`: no file in the field `test`. `POST /analyze/text`: `text` is missing or empty |
| `413` | The upload is larger than `MAX_UPLOAD_MB` |
| `500` | The audio could not be processed, for example because ffmpeg is missing. The details are only in the server's console |

## Notes

- Each upload is written to a unique temporary file and deleted right after transcription, even if it fails.
- Transcripts are never written to the logs. The server logs the number of words and the verdict.
- With `call_id`, the transcript of the call is kept in memory for a while, see [Privacy & security](PRIVACY_AND_SECURITY.md).
