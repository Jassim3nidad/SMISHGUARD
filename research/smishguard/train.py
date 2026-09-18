"""Freeze a model using train/validation/calibration only. Test is a separate command."""
import argparse
import hashlib
import json
import platform
from pathlib import Path
import pickle
import numpy as np
import scipy
from scipy.sparse import hstack, csr_matrix
import sklearn
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.naive_bayes import MultinomialNB
from sklearn.svm import LinearSVC
from sklearn.metrics import f1_score
from .data import read_dataset, template_groups, split_rows
from .preprocessing import VERSION, feature_text, URL

def url_features(text):
    """Local lexical URL facts only. No resolution, HTTP, or retained addresses."""
    from urllib.parse import urlsplit
    links = URL.findall(text)
    lengths, digits, depths = [], [], []
    for link in links:
        try:
            host = urlsplit(link if "://" in link else "https://"+link).hostname or ""
            lengths.append(min(len(host), 253)/253)
            digits.append(sum(c.isdigit() for c in host)/max(1,len(host)))
            depths.append(min(host.count("."),10)/10)
        except ValueError:
            continue
    return [min(len(links),10)/10, max(lengths, default=0), max(digits, default=0), max(depths, default=0)]

def transform(vectorizer, texts, condition, fit=False, url_rows=None):
    prepared = [feature_text(t, condition) for t in texts]
    matrix = vectorizer.fit_transform(prepared) if fit else vectorizer.transform(prepared)
    if condition == "text-url":
        if url_rows is None or len(url_rows) != len(texts):
            raise ValueError("Structured URL comparison requires participant-side URL facts, not retained URLs")
        keys = ("url_count", "url_max_host_length", "url_digit_fraction", "url_max_depth")
        values = [[float(row[k]) for k in keys] for row in url_rows]
        if any(not np.isfinite(x) or x < 0 for row in values for x in row):
            raise ValueError("URL facts must be finite and nonnegative")
        values = [[min(r[0],10)/10,min(r[1],253)/253,min(r[2],1),min(r[3],10)/10] for r in values]
        matrix = hstack([matrix, csr_matrix(values)]).tocsr()
    return matrix

def margin(model, x):
    if hasattr(model, "decision_function"):
        return model.decision_function(x)
    logp = model.predict_log_proba(x)
    return logp[:, 1] - logp[:, 0]

def write_json(path, obj):
    path.write_text(json.dumps(obj, indent=2, ensure_ascii=False, allow_nan=False), encoding="utf-8")

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("dataset", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--model", choices=["lr", "nb", "svm"], default="lr")
    parser.add_argument("--split", choices=["random", "group", "time"], default="group")
    parser.add_argument("--condition", choices=["deployment", "text-only", "text-url", "placeholders", "shape", "punctuation"], default="deployment")
    parser.add_argument("--analyzer", choices=["word", "char"], default="word")
    parser.add_argument("--seed", type=int, default=20260917)
    args = parser.parse_args()
    rows = read_dataset(args.dataset)
    if len(rows) < 1200:
        raise ValueError("Below the study's 1,200-message minimum. Use a separately approved small-sample protocol; no held-out research claim produced.")
    args.output.mkdir(parents=True, exist_ok=False)
    groups = template_groups(rows)
    splits = split_rows(rows, groups, args.split, args.seed)
    manifest = {name:[rows[i]["id"] for i in ids] for name,ids in splits.items()}
    manifest["groups"] = {r["id"]: groups[i] for i,r in enumerate(rows)}
    manifest["group_overlap"] = {f"{a}:{b}":len(set(groups[i] for i in splits[a]) & set(groups[i] for i in splits[b]))
        for a,b in [("train","validation"),("train","calibration"),("train","test"),("validation","test"),("calibration","test")]}
    write_json(args.output/"splits.json", manifest)
    vectorizer = TfidfVectorizer(lowercase=False, token_pattern=r"(?u)\b\w+\b", analyzer=args.analyzer,
        ngram_range=(1,1) if args.analyzer == "word" else (3,5), max_features=50000, norm="l2", dtype=np.float64)
    # Test text never enters transform, fitting, calibration, or threshold selection here.
    matrices = {name:transform(vectorizer, [rows[i]["text_redacted"] for i in splits[name]], args.condition, name=="train", [rows[i] for i in splits[name]])
                for name in ("train", "validation", "calibration")}
    labels = {name:np.array([int(rows[i]["label"]) for i in splits[name]]) for name in matrices}
    model = {"lr":LogisticRegression(max_iter=2000, random_state=args.seed),
             "nb":MultinomialNB(), "svm":LinearSVC(random_state=args.seed, dual="auto")}[args.model]
    model.fit(matrices["train"], labels["train"])
    calibrator = LogisticRegression(random_state=args.seed)
    calibrator.fit(margin(model, matrices["calibration"]).reshape(-1,1), labels["calibration"])
    probabilities = calibrator.predict_proba(margin(model, matrices["validation"]).reshape(-1,1))[:,1]
    thresholds = np.linspace(.05, .95, 91)
    threshold = float(max(thresholds, key=lambda t: (f1_score(labels["validation"], probabilities>=t), t)))
    artifact = dict(vectorizer=vectorizer, model=model, calibrator=calibrator, threshold=threshold, condition=args.condition)
    # Local trusted training artifact only. Never load pickle from an untrusted source.
    with open(args.output/"research-model.pkl", "wb") as f:
        pickle.dump(artifact, f)
    metadata = dict(model=args.model, split=args.split, seed=args.seed, condition=args.condition, analyzer=args.analyzer,
        preprocessing_version=VERSION, dataset_sha256=hashlib.sha256(args.dataset.read_bytes()).hexdigest(),
        versions=dict(python=platform.python_version(), numpy=np.__version__, scipy=scipy.__version__, sklearn=sklearn.__version__),
        threshold_policy="maximum validation F1 over 0.05..0.95 step 0.01; ties prefer higher threshold",
        calibration="sigmoid fit on independent calibration partition", template_similarity=.85,
        test_evaluated=False, research_winner=None)
    write_json(args.output/"run.json", metadata)
    if args.analyzer == "word" and args.condition == "deployment":
        if args.model == "nb":
            weights = model.feature_log_prob_[1]-model.feature_log_prob_[0]
            bias = float(model.class_log_prior_[1]-model.class_log_prior_[0])
        else:
            weights = model.coef_[0]
            bias = float(model.intercept_[0])
        write_json(args.output/"model.json", dict(schema_version=1, preprocessing_version=VERSION,
            feature_type="word-unigram-tfidf-l2", vocabulary=vectorizer.get_feature_names_out().tolist(),
            idf=vectorizer.idf_.tolist(), weights=weights.tolist(), bias=bias,
            classes=["not_suspicious","suspicious"], threshold=threshold,
            calibration=dict(method="sigmoid", a=float(calibrator.coef_[0,0]), b=float(calibrator.intercept_[0])),
            calibration_evaluated=False, synthetic=False))
    print("Frozen research artifact created. Final test not evaluated. No winner selected.")

if __name__ == "__main__":
    main()
