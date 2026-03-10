"""
Run this ONCE to clear misclassified emails from cache.
Place this file next to email_analysis_cache.json and run:
    python fix_cache.py
"""
import json, os

CACHE_FILE = "email_analysis_cache.json"

if not os.path.exists(CACHE_FILE):
    print("❌ Cache file not found. Nothing to fix.")
    exit()

with open(CACHE_FILE, "r") as f:
    cache = json.load(f)

print(f"📦 Cache has {len(cache)} entries before cleanup")

# ── Rules: which cached results are WRONG and need re-analysis ────────────────

def is_wrong(entry: dict) -> bool:
    category = entry.get("category", "")
    intent   = entry.get("intent", "")
    summary  = entry.get("summary", "").lower()

    # 1. Promotional/platform emails wrongly in Personal
    if category == "Personal" and intent in ["Promotion", "Notification", "Update"]:
        return True

    # 2. Promotional/platform emails wrongly in Meeting
    if category == "Meeting" and intent in ["Promotion", "Notification", "Update"]:
        return True

    # 3. Promotional digests wrongly in Job
    if category == "Job" and intent == "Promotion":
        return True

    # 4. Internshala/Naukri/LeetCode/HackerRank/ChatGPT in wrong categories
    promo_keywords = [
        "internshala", "naukri", "leetcode", "hackerrank",
        "chatgpt", "geeksforgeeks", "tcs ion", "tcsion",
        "student partner", "weekly digest", "coding challenge",
        "postman team", "hackerrank team"
    ]
    if category in ["Personal", "Meeting", "Job", "Spam"]:
        if any(kw in summary for kw in promo_keywords):
            return True

    # 5. Google verification/recovery wrongly NOT in OTP
    if category not in ["OTP"] and any(kw in summary for kw in [
        "verification code", "google accounts team", "account was recovered",
        "one time password", "otp"
    ]):
        return True

    # 6. Security alerts (sign-in, password change) wrongly NOT in OTP
    if category not in ["OTP"] and intent == "Security":
        return True

    # 7. Real promotional emails stuck in Spam (non-scam)
    if category == "Spam" and intent == "Promotion":
        if not any(scam_kw in summary for scam_kw in [
            "lottery", "won", "prize", "phishing", "nigerian", "bitcoin"
        ]):
            return True

    # 8. Naukri hiring alerts with HIGH priority (should be LOW/Other)
    if "naukri" in summary and entry.get("priority") == "HIGH":
        return True

    # 9. HackerRank challenge with HIGH priority
    if "hackerrank" in summary and entry.get("priority") == "HIGH":
        return True

    return False

# Find bad entries
bad_ids = [eid for eid, entry in cache.items() if is_wrong(entry)]

print(f"🗑️  Found {len(bad_ids)} misclassified entries to remove:")
for eid in bad_ids:
    entry = cache[eid]
    print(f"   ❌ [{entry.get('category','?')} / {entry.get('intent','?')}] "
          f"{entry.get('summary','')[:80].strip()}")

# Remove them
for eid in bad_ids:
    del cache[eid]

# Save cleaned cache
with open(CACHE_FILE, "w") as f:
    json.dump(cache, f, indent=2)

print(f"\n✅ Cache cleaned. {len(cache)} entries remain.")
print("🔄 Now restart app.py and click 'Sync Now' to re-analyze removed emails.")