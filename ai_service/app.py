from flask import Flask, request, jsonify
from transformers import pipeline
import re
from groq import Groq
import os, json, traceback
from dotenv import load_dotenv
from bs4 import BeautifulSoup

load_dotenv()
app = Flask(__name__)

# ── EMAIL CACHE ────────────────────────────────────────────────────────────────
CACHE_FILE = "email_analysis_cache.json"

def load_cache():
    if os.path.exists(CACHE_FILE):
        with open(CACHE_FILE, "r") as f:
            return json.load(f)
    return {}

def save_cache(cache):
    with open(CACHE_FILE, "w") as f:
        json.dump(cache, f, indent=2)

email_cache = load_cache()
print(f"✅ Cache loaded ({len(email_cache)} emails already analyzed)")

# ── GROQ ───────────────────────────────────────────────────────────────────────
GROQ_MODEL = "llama-3.3-70b-versatile"

try:
    groq_client = Groq(api_key=os.getenv("GROQ_API_KEY"))
    print(f"✅ Groq ready ({GROQ_MODEL})")
except Exception as e:
    print(f"❌ Groq init failed: {e}")
    groq_client = None

# ── LOCAL CLASSIFIER ───────────────────────────────────────────────────────────
try:
    classifier = pipeline("zero-shot-classification", model="facebook/bart-large-mnli")
    print("✅ Local classifier loaded")
except Exception as e:
    print(f"⚠️  Classifier unavailable: {e}")
    classifier = None

LABELS = ["OTP", "Job", "Meeting", "Support", "Spam", "Personal", "Work", "Other"]

# ── TEXT CLEANING ──────────────────────────────────────────────────────────────
def clean_text(t: str) -> str:
    t = re.sub(r'<[^>]+>', ' ', t)
    t = re.sub(r'http\S+', '', t)
    t = re.sub(r'[\u200b\u200c\u200d\u2007\ufeff\u00ad\u034f\u00a0]', ' ', t)
    return re.sub(r'\s+', ' ', t).strip()

def extract_clean_text(html: str) -> str:
    soup = BeautifulSoup(html, "html.parser")
    for tag in soup(["style", "script", "head", "meta", "link"]):
        tag.decompose()
    text = soup.get_text(separator=" ")
    text = re.sub(r'[\u200b\u200c\u200d\u2007\ufeff\u00ad\u034f\u00a0]', ' ', text)
    return " ".join(text.split())

# ── REAL SPAM DETECTION ────────────────────────────────────────────────────────
# ONLY truly deceptive / fraudulent / phishing emails go to "Spam" category.
# Promotional/marketing emails (LeetCode, Naukri, HackerRank, etc.) go to "Other".

SPAM_SUBJECT_KEYWORDS = [
    "you won", "you have won", "winner", "lottery", "prize", "claim now",
    "click here to claim", "100% free", "make money fast",
    "wire transfer", "bank account details", "miracle cure",
    "congratulations you", "selected for prize", "free iphone",
    "earn $", "earn money online", "you are a lucky winner",
    "your account has been hacked", "send bitcoin", "crypto reward",
    "verify your bank", "update payment details", "suspended account",
    "nigerian prince", "inheritance funds",
]

SPAM_BODY_KEYWORDS = [
    "you have been selected to receive",
    "click here to claim your prize",
    "send us your bank account",
    "nigerian prince",
    "transfer funds to",
    "wire us the amount",
    "verify your credit card",
    "your paypal has been limited",
    "send bitcoin to",
    "you are entitled to",
    "million dollars",
    "inheritance of",
]

# ── GROQ ANALYSIS ──────────────────────────────────────────────────────────────
def groq_analyze(email_text: str, subject: str = "") -> dict | None:
    if groq_client is None:
        return None

    subject_hint = f'Subject: "{subject}"\n' if subject else ""

    prompt = f"""You are an expert email analyst. Analyze the email below and return a JSON object.

{subject_hint}
EMAIL:
{email_text}

Return ONLY valid JSON (no markdown, no backticks) in this exact format:
{{
  "intent": "<one of: Meeting | Request | Update | Notification | Promotion | OTP | Job | Personal | Security | Spam>",
  "summary": "• <specific point about who sent it and why>\\n• <key fact, date, or detail>\\n• <action needed or 'No action needed'>",
  "reply": "<2-3 sentence specific reply body, no greeting or sign-off>"
}}

=== INTENT RULES (pick the BEST match) ===
- OTP        : one-time passwords, verification codes, login codes, account recovery emails
- Security   : sign-in alerts, password change notifications, suspicious login warnings from Google/Microsoft/banks
- Meeting    : scheduling, calendar invites, interview time slots WITH a specific time/date
- Request    : sender asks YOU to do something specific (submit, complete, review)
- Job        : DIRECT job offers, interview call letters with specific date/time,
               job fair CONFIRMATION emails (HR writing to you personally, not bulk alerts)
- Promotion  : ALL marketing, newsletters, platform promotions — including:
               LeetCode digests, HackerRank challenges, Internshala campaigns,
               Naukri bulk job alerts, ChatGPT/OpenAI promos, GeeksforGeeks offers,
               LinkedIn notifications/recommendations, platform welcome emails,
               webinar promotions, student partner programs, course ads
- Update     : order confirmations, seat confirmations, general status updates, announcements
- Notification: system alerts, receipts, delivery confirmations
- Personal   : emails from REAL individual people (friends, family, colleagues by name) NOT platforms
- Spam       : ONLY truly deceptive/fraudulent emails:
               lottery scams, fake prize claims, phishing for bank/card details,
               miracle cure ads, Nigerian prince scams, "you won $1 million",
               crypto investment scams, fake account suspension to steal credentials.
               ⚠️ Do NOT use Spam for newsletters or marketing — those are Promotion.

=== PRIORITY RULES ===
- Promotion, OTP, Security, Notification, Spam → always LOW
- Meeting, Request → HIGH
- Job → MEDIUM (HIGH if deadline within 48 hours)
- Personal → MEDIUM

=== REPLY RULES ===
- Body only — no "Hi", no "Thanks", no signature
- For Promotion, OTP, Security, Notification, Spam → reply must be "No reply required."
- Never write "Thank you for your email. I will get back to you shortly."
"""

    try:
        response = groq_client.chat.completions.create(
            model=GROQ_MODEL,
            messages=[{"role": "user", "content": prompt}],
            max_tokens=400,
            temperature=0.1,
        )
        raw = response.choices[0].message.content.strip()
        print(f"🤖 Groq raw: {repr(raw[:300])}")

        raw = re.sub(r'^```(?:json)?\s*', '', raw, flags=re.MULTILINE)
        raw = re.sub(r'\s*```$', '', raw, flags=re.MULTILINE)
        raw = raw.strip()

        result = json.loads(raw)

        for key in ("intent", "summary", "reply"):
            if key not in result:
                print(f"⚠️  Missing key '{key}' in Groq response")
                return None

        valid_intents = ["Meeting", "Request", "Update", "Notification",
                         "Promotion", "OTP", "Job", "Personal", "Security", "Spam"]
        if result["intent"] not in valid_intents:
            for v in valid_intents:
                if v.lower() in result["intent"].lower():
                    result["intent"] = v
                    break
            else:
                result["intent"] = "Update"

        return result

    except json.JSONDecodeError as e:
        print(f"❌ JSON parse error: {e}\nRaw: {raw[:500]}")
        return None
    except Exception as e:
        print(f"❌ Groq API error: {e}")
        traceback.print_exc()
        return None

# ── PRIORITY DETECTION ─────────────────────────────────────────────────────────
def detect_priority(text: str, intent: str, subject: str = "") -> str:
    text_lower    = text.lower()
    subject_lower = subject.lower()
    combined      = text_lower + " " + subject_lower

    # Always LOW for these intents
    if intent in ["Promotion", "OTP", "Notification", "Security", "Spam"]:
        return "LOW"

    # Genuine HIGH priority keywords
    high_keywords = [
        "urgent", "asap", "immediately", "critical", "time-sensitive",
        "expires", "action required", "respond by", "due by",
        "last chance", "interview scheduled", "offer letter",
        "account compromised", "unauthorized access", "verify now",
        "deadline", "today only", "within 24 hours", "within 48 hours"
    ]
    if any(w in combined for w in high_keywords):
        return "HIGH"

    # Intent-based defaults
    if intent in ["Meeting", "Request"]:
        return "HIGH"
    if intent in ["Job", "Personal"]:
        return "MEDIUM"

    return "LOW"

# ── /analyze ───────────────────────────────────────────────────────────────────
@app.route("/analyze", methods=["POST"])
def analyze():
    global email_cache
    try:
        data = request.get_json()
        if not data:
            return jsonify({"error": "Invalid JSON"}), 400

        raw      = (data.get("text")     or "").strip()
        subject  = (data.get("subject")  or "").strip()
        email_id = (data.get("email_id") or "").strip()

        print(f"\n{'='*60}")
        print(f"📧 Subject  : {subject!r}")
        print(f"📧 Email ID : {email_id!r}")
        print(f"📧 Raw      : {raw[:200]!r}")

        # ── CACHE CHECK ────────────────────────────────────────────────────────
        if email_id and email_id in email_cache:
            print(f"✅ Cache hit — skipping Groq for {email_id}")
            return jsonify(email_cache[email_id])

        if not raw:
            return jsonify({
                "category": "Other", "priority": "LOW", "intent": "Other",
                "summary": "• Empty email received\n• No content to analyse\n• No action needed",
                "reply": "No content to respond to."
            })

        # ── CLEAN TEXT ─────────────────────────────────────────────────────────
        model_text = extract_clean_text(raw) if ("<html" in raw.lower() or "<body" in raw.lower()) else clean_text(raw)
        model_text = re.sub(r'[|_]{3,}', ' ', model_text)
        model_text = re.sub(r'\s+', ' ', model_text).strip()
        print(f"📝 Cleaned : {model_text[:200]!r}")

        if not model_text:
            return jsonify({
                "category": "Other", "priority": "LOW", "intent": "Other",
                "summary": "• Unable to extract email text\n• Body may be empty\n• No action needed",
                "reply": "Could not process email content."
            })

        full_context = (f"Subject: {subject}\n\n{model_text}" if subject else model_text)[:3000]

        # ── PRE-CHECK: Real spam/phishing keywords ─────────────────────────────
        subject_lower = subject.lower()
        body_lower    = model_text.lower()
        is_real_spam  = (
            any(kw in subject_lower for kw in SPAM_SUBJECT_KEYWORDS) or
            any(kw in body_lower    for kw in SPAM_BODY_KEYWORDS)
        )

        # ── GROQ ANALYSIS ──────────────────────────────────────────────────────
        ai = groq_analyze(full_context, subject)

        if ai:
            intent  = ai["intent"]
            summary = ai["summary"]
            reply   = ai["reply"]
            print(f"🎯 Intent  : {intent}")
            print(f"📋 Summary : {summary}")
            print(f"💬 Reply   : {reply}")
        else:
            intent  = "Update"
            summary = "• AI unavailable — check your GROQ_API_KEY in .env\n• Visit console.groq.com to get a free API key\n• No action needed"
            reply   = "I've received your message and will follow up shortly."
            print("⚠️  Using fallback (Groq unavailable)")

        # ── LOCAL CLASSIFIER (fallback base) ───────────────────────────────────
        category = "Other"
        if classifier:
            try:
                res = classifier(model_text[:512], LABELS)
                category = res["labels"][0]
                print(f"🏷️  Category (classifier): {category}")
            except Exception as e:
                print(f"⚠️ Classifier error: {e}")

        # ── INTENT → CATEGORY MAPPING ─────────────────────────────────────────
        # UI Categories: Personal | Meeting | Work | Job | OTP | Spam | Other
        #
        # ✅ Spam    = ONLY real scams, phishing, deceptive fraud
        # ✅ Other   = Promotional/marketing/newsletter/platform emails
        intent_to_category = {
            "OTP":          "OTP",
            "Security":     "OTP",      # Google sign-in alerts → OTP (informational)
            "Meeting":      "Meeting",
            "Job":          "Job",
            "Personal":     "Personal",
            "Promotion":    "Other",    # Marketing/newsletters → Other
            "Request":      "Work",
            "Update":       "Work",
            "Notification": "Other",
            "Spam":         "Spam",     # Groq confirmed real scam → Spam
        }
        if intent in intent_to_category:
            category = intent_to_category[intent]
            print(f"🏷️  Category (intent override): {category}")

        # ── REAL SPAM KEYWORD HARD OVERRIDE ───────────────────────────────────
        if is_real_spam:
            category = "Spam"
            print(f"🚫 Real spam/phishing keywords detected → Spam")

        # ── PRIORITY ──────────────────────────────────────────────────────────
        priority = detect_priority(model_text, intent, subject)
        print(f"⚡ Priority : {priority}")

        result = {
            "category": category,
            "priority": priority,
            "summary":  summary,
            "reply":    reply,
            "intent":   intent,
        }

        # ── SAVE TO CACHE ──────────────────────────────────────────────────────
        if email_id:
            email_cache[email_id] = result
            save_cache(email_cache)
            print(f"💾 Cached result for {email_id}")

        print(f"✅ Done: {json.dumps(result, indent=2)}")
        return jsonify(result)

    except Exception as e:
        print("❌ Unhandled error:")
        traceback.print_exc()
        return jsonify({
            "error": str(e), "category": "Other", "priority": "LOW", "intent": "Other",
            "summary": "• Error processing email\n• Check server logs\n• No action needed",
            "reply": "Unable to generate response."
        }), 500

# ── /health ────────────────────────────────────────────────────────────────────
@app.route("/health", methods=["GET"])
def health():
    groq_status = "unavailable"
    if groq_client:
        try:
            r = groq_client.chat.completions.create(
                model=GROQ_MODEL,
                messages=[{"role": "user", "content": 'Return {"status":"ok"}'}],
                max_tokens=20
            )
            groq_status = "ok" if r.choices[0].message.content else "no response"
        except Exception as e:
            groq_status = f"error: {str(e)[:120]}"

    return jsonify({
        "status":        "healthy",
        "service":       "AI Email Intelligence",
        "model":         GROQ_MODEL,
        "groq":          groq_status,
        "classifier":    "ok" if classifier else "unavailable",
        "cached_emails": len(email_cache),
    })

if __name__ == "__main__":
    app.run(port=5001, threaded=True, debug=True)