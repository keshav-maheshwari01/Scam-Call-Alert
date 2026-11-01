# How scoring works

`backend/textcheck.py` lower-cases the transcript, looks for signals and adds up points. The final score is
limited to the range 0–100.

| Signal | Points |
|---|---|
| **Critical pattern**: a regular expression for a classic scam sentence, such as "share your OTP", "account will be blocked" or "arrest hoga" | **+40** each |
| **Red-flag phrase**: for example `cvv`, `gift card`, `anydesk`, `otp dijiye` | **+25** each |
| **Yellow-flag word**: a soft word such as `verify`, `bank` or `urgent` | **+10** each, at most **+30** in total |
| **Safe-context phrase**: for example "thank you for calling" | **−20** each |
| **Suspicious caller number**: `+92…`, `+1 xxx xxx`, `0092…` or `140…` | **+30** |
| **Very short text** (fewer than 5 words) with no critical pattern and no red-flag phrase | score × 0.6 |

| Score | Verdict |
|---|---|
| 70 or more | `SCAM` |
| 40 to 69 | `ALERT` |
| below 40 | `SAFE` |

Each pattern or phrase counts **once**, no matter how many times it is spoken.

## Real examples

These were produced by running the current code:

| Transcript | Score | Verdict |
|---|---|---|
| Hello bhai kya chal raha hai sab theek? | 0 | `SAFE` |
| Thank you for calling customer support. How can I help you today? | 0 | `SAFE` |
| Please share your OTP | 65 | `ALERT` |
| Sir aapka SBI account band ho jayega abhi OTP dijiye turant | 100 | `SCAM` |
| Your account has been locked. Verify your credit card number and CVV immediately. | 100 | `SCAM` |
| Congratulations! You won a lottery prize. Share your Aadhaar number to claim. | 100 | `SCAM` |
| tell me the six digit code you just received | 0 | `SAFE` |
| your parcel is held at customs, you are under digital arrest, stay on the video call | 0 | `SAFE` |

## Scoring a whole call

When the app sends a `call_id`, the server joins the text of all chunks received so far and scores that. Here is
a call split into two chunks of about 8 seconds each:

| After | Verdict | Score |
|---|---|---|
| chunk 1 (~8 s) | `ALERT` | 55 |
| chunk 2 (~16 s) | `ALERT` | 55 |

Scoring the whole call means a score cannot fall back to `SAFE` just because the next sentence was harmless, and
signals that are spread across several chunks add up.

## Matching rules

- Red-flag, yellow-flag and safe-context phrases match as **whole words**. `irs` does not match "first" and `otp`
  does not match "footprint". There is no stemming, so `verify` does not match "verified".
- Critical patterns are case-insensitive regular expressions. They are not restricted to whole words.
- Spoken Hindi is translated to English by Whisper before scoring, so the rules mix English words with
  romanised Hindi (Hinglish).

## Changing the rules

Edit the lists at the top of `backend/textcheck.py`: `critical_patterns`, `red_flags`, `yellow_flags`,
`safe_context` and `suspicious_number_patterns`. For every phrase you add, add a test in
`backend/tests/test_textcheck.py`: one scam sample that must be caught **and** one innocent sample that must not
be flagged. Then run the tests, see [Getting started](GETTING_STARTED.md#run-the-tests).

## Known gaps

- **It is keyword-based, not machine learning.** A scammer who avoids the listed words is not caught, and an
  unusual honest call can trigger an alert. In the table above, *"tell me the six digit code you just received"*
  (a paraphrase of an OTP request) and a "digital arrest" script both score 0.
- **A single chunk can miss the context.** Words split across two chunks may not match a pattern that expects
  them together.
- **Translation can change the words.** If Whisper translates a sentence differently from what the rules expect,
  the pattern will not match.
