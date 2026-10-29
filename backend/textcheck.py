"""Rule-based scam scoring for call transcripts (English / Hindi / Hinglish)."""
import re
from typing import Dict

#40 points 
critical_patterns = [

    r"(?:share|give|send|bata|dena|dijiye|do|batao)\s*(?:your|mera|apna)?\s*otp",
    r"otp\s*(?:mat|share|batana|dijiye|dena|send)",
    r"one\s*time\s*password",

    # Account block threats
    r"(?:account|khata)\s*(?:will be|ho\s*jayega|band|block|suspend|freeze)",
    r"(?:block|band|suspend|freeze)\s*(?:kar|ho|hoga|denge|karenge)",

    # Arrest / legal threats
    r"(?:arrest|jail|police|court|case|FIR)\s*(?:hoga|karenge|will|ho\s*jayega|file)",
    r"(?:giraftaar|pakad|warrant)\s*(?:hoga|karenge|aa\s*jayega)",

    # Identity theft
    r"(?:aadhaar|aadhar|pan|passport)\s*(?:number|no|card)\s*(?:batao|share|send|dijiye|verify|confirm)",
    r"(?:confirm|verify|validate)\s+(?:social\s+security|ssn|aadhaar|pan)",

    # Bank/card details
    r"(?:card|credit|debit)\s*(?:number|no|details|info)\s*(?:batao|share|dijiye|give|send)",
    r"(?:cvv|cvc|expiry|pin)\s*(?:batao|share|dijiye|bolo|give|send)",
    r"(?:bank\s*account|routing)\s*(?:number|details)\s*(?:share|give|send|batao)",

    # Lottery / prize scams
    r"(?:lottery|prize|reward|jackpot)\s*(?:jeeta|jeet|won|win|claim|liya)",
    r"(?:congratulations|badhai|mubarak)\s*(?:aap|you|tumhe)?\s*(?:jeete|won|selected|chosen)",

    # KYC fraud
    r"(?:kyc|know\s*your\s*customer)\s*(?:update|verify|complete|pending|expire)",
    r"(?:kyc|verification)\s*(?:nahi|not)\s*(?:hua|done|complete)",

    # Refund scams
    r"(?:refund|cashback|paisa\s*wapas)\s*(?:milega|aayega|process|send|transfer)",
    r"(?:income\s*tax|itr|tds)\s*(?:refund|return|pending)",

    # Urgency + action
    r"(?:urgent|turant|abhi|immediately|last\s*chance|aakhri\s*mauka)\s*(?:call|karo|karein|contact|click)",
    r"(?:expire|band|block)\s*(?:ho\s*jayega|hoga|will)\s*(?:abhi|aaj|today|now|turant)",
]

#25 points 
red_flags = [

    # English
    "otp", "cvv", "pin number", "card number", "account number",
    "social security", "routing number", "credit card",
    "verify account", "confirm password", "update payment",
    "click link", "verify identity", "banking information",
    "won a prize", "claim reward", "tax refund", "irs",
    "locked account", "suspended account", "unusual activity",
    "fraud detected", "unauthorized access", "act now",
    "limited time", "call immediately", "press 1", "press 2",
    "remote access", "teamviewer", "anydesk", "gift card",
    "google play card", "itunes card", "wire transfer",

    # Hindi / Hinglish
    "otp dijiye", "otp batao", "otp share karo", "otp dena",
    "account band", "khata band", "account block",
    "giraftaar", "warrant", "court notice", "fir darj",
    "aadhaar number", "pan number", "aadhar batao",
    "kyc update", "kyc expire", "kyc pending",
    "paisa transfer", "turant karo", "abhi karo",
    "lottery jeeti", "prize mila", "inaam", "lucky draw",
    "refund aayega", "paisa wapas", "cashback milega",
    "bank officer", "rbi officer", "income tax officer",
    "police aa jayegi", "arrest ho jaoge", "jail bhejenge",
]

#10 points 
yellow_flags = [
    "verify", "confirm", "validate", "update", "urgent",
    "immediately", "account", "password", "security",
    "payment", "bank", "card", "information", "details",
    "action required", "contact", "call back", "number",
    "सत्यापित", "खाता", "तुरंत", "जरूरी", "बैंक",
    "reactivate", "renew", "expire", "suspend",
    "customer care", "helpline", "toll free",
]

#-20
safe_context = [
    "how can i help",
    "thank you for calling",
    "have a great day",
    "is there anything else",
    "your complaint has been registered",
    "we will look into it",
    "aapki madad",
    "koi samasya",
    "kaise help kar sakta",
]


suspicious_number_patterns = [
    r"\+92",
    r"\+1\s*\d{3}\s*\d{3}",
    r"00\s*92",
    r"140\d+",
]
#+1 800 555
#+1800555
#+1    800   555

def _contains(phrase: str, text: str) -> bool:
    """True if `phrase` appears in `text` as whole word(s), not inside another word."""
    return re.search(r"(?<!\w)" + re.escape(phrase) + r"(?!\w)", text) is not None


def check_text(text: str, caller_number: str = "") -> Dict:

    text_lower = text.lower().strip()

    score = 0

    found_red_flags = []
    found_patterns = []
    found_yellow = 0

    for pattern in critical_patterns:

        matches = re.findall(pattern, text_lower, re.IGNORECASE)  # some patterns have 'FIR' in caps

        if matches:
            score += 40
            found_patterns.append(pattern)

    # 2. Red flag phrases
    for flag in red_flags:

        if _contains(flag, text_lower):
            score += 25
            found_red_flags.append(flag)

    # 3. Yellow flags
    for flag in yellow_flags:

        if _contains(flag, text_lower):
            found_yellow += 1

    # common words like "bank" / "account" alone shouldn't push a call to ALERT
    score += min(found_yellow * 10, 30)

    # 4. Safe context
    for ctx in safe_context:

        if _contains(ctx, text_lower):
            score -= 20

    # 5. Suspicious caller number
    suspicious_caller = False

    if caller_number:

        for pattern in suspicious_number_patterns:

            if re.search(pattern, caller_number):
                score += 30
                suspicious_caller = True
                break

    # 6. Very short text
    word_count = len(text_lower.split())

    if word_count < 5:
        score = int(score * 0.6)

    # Keep score between 0 and 100
    score = max(0, min(score, 100))

    # ══════════════════════════════════════
    # CLASSIFICATION
    # ══════════════════════════════════════
    if score >= 70:
        classification = "SCAM"
        confidence = min(1.0, 0.80 + (score - 70) * 0.005)

    elif score >= 40:
        classification = "ALERT"
        confidence = 0.55 + (score - 40) * 0.007

    else:
        classification = "SAFE"
        confidence = max(0.5, 0.95 - score * 0.01)

    # ══════════════════════════════════════
    # REASONS
    # ══════════════════════════════════════
    reasons = []

    if found_patterns:
        reasons.append(f"{len(found_patterns)} critical scam pattern(s) found")

    if found_red_flags:
        reasons.append(
            f"{len(found_red_flags)} red flag phrase(s): "
            f"{', '.join(found_red_flags[:3])}"
        )

    if found_yellow:
        reasons.append(f"{found_yellow} suspicious word(s)")

    if suspicious_caller:
        reasons.append("suspicious caller number detected")

    if not reasons:
        reasons.append("no suspicious patterns found")

    return {
        "classification": classification,
        "confidence": round(confidence, 2),
        "score": score,
        "red_flags_found": list(set(found_red_flags)),
        "pattern_matches": len(found_patterns),
        "yellow_flag_count": found_yellow,
        "suspicious_caller": suspicious_caller,
        "reason": " | ".join(reasons),
        "word_count": word_count,
    }


# ══════════════════════════════════════════
# RESULT FORMATTER
# ══════════════════════════════════════════
def format_result(result: Dict) -> str:

    label = {
        "SCAM": "🚨 SCAM DETECTED 🚨",
        "ALERT": "⚠️ SUSPICIOUS CALL ⚠️",
        "SAFE": "✅ SAFE ✅",
    }

    flags = (
        "\n  • ".join(result["red_flags_found"])
        if result["red_flags_found"]
        else "None"
    )

    return f"""
{'='*55}
{label.get(result['classification'])}
Risk Score  : {result['score']}/100
Confidence  : {result['confidence']*100:.0f}%
{'='*55}
Reason      : {result['reason']}
Red Flags   : {len(result['red_flags_found'])}
  • {flags}
Patterns    : {result['pattern_matches']}
Yellow Flags: {result['yellow_flag_count']}
Caller Suspicious: {'Yes ⚠️' if result['suspicious_caller'] else 'No'}
Word Count  : {result['word_count']}
{'='*55}
"""

if __name__ == "__main__":
    import sys
    import transcriber  # imported here so tests / the API don't pay for it twice

    audio_path = sys.argv[1] if len(sys.argv) > 1 else "samples/test_scam.mpeg"
    transcript = transcriber.transcribe_audio_local(audio_path)
    print(transcript)
    print(format_result(check_text(transcript, "")))
