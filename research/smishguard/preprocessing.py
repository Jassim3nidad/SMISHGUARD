"""Keep sg-ascii-v1 synchronized with Android Preprocessing. Label independent."""
import re

VERSION = "sg-ascii-v1"
URL = re.compile(r"(?:https?://|www\.)[^\s<>]+", re.I | re.ASCII)
EMAIL = re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}")
ASCII_LOWER = str.maketrans("ABCDEFGHIJKLMNOPQRSTUVWXYZ", "abcdefghijklmnopqrstuvwxyz")

def normalize(text):
    text = text.translate(ASCII_LOWER)
    text = URL.sub(" zzurlzz ", text)
    text = EMAIL.sub(" zzemailzz ", text)
    text = re.sub(r"[0-9]+", " zznumberzz ", text)
    return re.sub("[^a-z]+", " ", text).strip()

def tokens(text):
    return normalize(text).split()

def feature_text(text, condition):
    """Text-only removes URL placeholders too; shortcuts receive only their named view."""
    if condition == "text-only":
        return normalize(URL.sub(" ", text))
    if condition == "placeholders":
        return " ".join(t for t in tokens(text) if t in {"zzurlzz", "zzemailzz", "zznumberzz", "zznamezz", "zzaccountzz"}) or "empty"
    if condition == "shape":
        # Binned length, token count, lines, digit count; no lexical content.
        return " ".join([f"length{min(len(text)//20, 50)}", f"words{min(len(text.split())//3, 50)}",
                         f"lines{min(text.count(chr(10)), 10)}", f"digits{min(sum(c.isdigit() for c in text), 20)}"])
    if condition == "punctuation":
        return " ".join(f"u{ord(c):x}" for c in text if not c.isalnum() and not c.isspace()) or "empty"
    if condition in {"text-url", "deployment"}:
        return normalize(text)
    raise ValueError("Unknown feature condition")
