"""Synthetic software-only fixtures and reference arithmetic for Kotlin parity tests."""
import json
import math
from collections import Counter
from pathlib import Path
from .preprocessing import VERSION, normalize, tokens

def predict(model, text):
    counts = Counter(tokens(text))
    values = [counts[w]*idf for w,idf in zip(model["vocabulary"],model["idf"])]
    norm = math.sqrt(sum(x*x for x in values))
    margin = model["bias"] + sum(w*x/norm for w,x in zip(model["weights"], values)) if norm else model["bias"]
    c = model.get("calibration")
    score = 1/(1+math.exp(-(c["a"]*margin+c["b"]))) if c else margin
    return score, score >= model["threshold"]

def generate():
    root = Path(__file__).resolve().parents[2]
    target = root/"android/app/src/test/resources"
    target.mkdir(parents=True, exist_ok=True)
    model = dict(schema_version=1, preprocessing_version=VERSION, feature_type="word-unigram-tfidf-l2",
        vocabulary=["hello","account","zzurlzz","zznumberzz","ang","bayad"],
        idf=[1.,1.5,2.,1.3,1.1,1.8], weights=[-.7,.8,1.2,.3,-.2,.5], bias=-.25,
        classes=["not_suspicious","suspicious"], threshold=.5,
        calibration=dict(method="sigmoid",a=.8,b=-.1), calibration_evaluated=False, synthetic=True)
    texts=["hello", "ACCOUNT account https://example.com/private?id=123", "Bayad ang 123!", "🌟 ñ Á İ hello\naccount",
           "Email user@example.com; www.example.org", "unknown words only", "HELLO\tANG bayad", "hello "*1000]
    cases=[dict(text=t, normalized=normalize(t), score=predict(model,t)[0], suspicious=predict(model,t)[1]) for t in texts]
    (target/"synthetic-model.json").write_text(json.dumps(model,indent=2),encoding="utf-8")
    (target/"parity-cases.json").write_text(json.dumps(cases,indent=2,ensure_ascii=False),encoding="utf-8")
    print("Synthetic parity fixtures written to test resources only; never packaged in APK.")

if __name__ == "__main__":
    generate()
