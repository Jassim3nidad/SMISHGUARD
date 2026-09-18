"""Validation and label-blind template grouping. Read already consented/redacted data only."""
import csv
from datetime import datetime
from difflib import SequenceMatcher
from .preprocessing import normalize

REQUIRED = {"id", "text_redacted", "label", "language", "source_type", "consent_verified", "redaction_verified", "financial_scope"}

def read_dataset(path):
    with open(path, encoding="utf-8-sig", newline="") as f:
        reader = csv.DictReader(f)
        if not REQUIRED <= set(reader.fieldnames or []):
            raise ValueError("Missing required schema columns")
        rows = list(reader)
    if len({r["id"] for r in rows}) != len(rows) or any(not r["id"] for r in rows):
        raise ValueError("IDs must be unique and nonempty")
    for r in rows:
        if r["consent_verified"] != "true" or r["redaction_verified"] != "true":
            raise ValueError("Consent/provenance and redaction review must be verified")
        if r["source_type"] in {"synthetic", "sms_spam_collection", "smishtank"}:
            raise ValueError("Synthetic/external corpora are excluded from primary research")
        if r["financial_scope"] != "true" or r["label"] not in {"0", "1"}:
            raise ValueError("Primary study requires adjudicated binary financial-scope records")
        if r["language"] not in {"fil", "en", "taglish"} or not r["text_redacted"].strip():
            raise ValueError("Invalid language or empty text")
        if len(r["text_redacted"]) > 10000:
            raise ValueError("Message exceeds deployment input bound")
    return rows

def template_groups(rows, similarity=0.85):
    """Deterministic connected components of redacted character-template similarity.

    O(n²), appropriate for this study's 1,200–2,500 target. No labels used.
    Caller records group membership and similarity policy, then manually audits templates.
    """
    texts = [normalize(r["text_redacted"]) for r in rows]
    parent = list(range(len(rows)))
    def root(i):
        while parent[i] != i:
            parent[i] = parent[parent[i]]
            i = parent[i]
        return i
    for i in range(len(rows)):
        for j in range(i):
            # Length upper bound makes obvious nonmatches inexpensive.
            if 2 * min(len(texts[i]), len(texts[j])) / max(1, len(texts[i])+len(texts[j])) < similarity:
                continue
            if texts[i] == texts[j] or SequenceMatcher(None, texts[i], texts[j], autojunk=False).ratio() >= similarity:
                parent[root(i)] = root(j)
    return [root(i) for i in range(len(rows))]

def timestamp(value):
    parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    if parsed.tzinfo is None:
        raise ValueError("Timestamps require a recorded timezone")
    return parsed

def split_rows(rows, groups, strategy, seed):
    import numpy as np
    from sklearn.model_selection import train_test_split, GroupShuffleSplit
    ids = np.arange(len(rows))
    y = np.array([int(r["label"]) for r in rows])
    if strategy == "time":
        dates = [timestamp(r.get("received_at", "")) for r in rows]
        ids = np.array(sorted(ids, key=lambda i: (dates[i], rows[i]["id"])))
        # Keep equal timestamps on one side; invalid/too-small splits are rejected below.
        boundaries = [dates[ids[int(len(ids)*fraction)]] for fraction in (.55, .70, .85)]
        partitions = [[i for i in ids if sum(dates[i] >= b for b in boundaries) == p] for p in range(4)]
    elif strategy == "random":
        train, held = train_test_split(ids, test_size=.45, random_state=seed, stratify=y)
        validation, held = train_test_split(held, test_size=2/3, random_state=seed, stratify=y[held])
        calibration, test = train_test_split(held, test_size=.5, random_state=seed, stratify=y[held])
        partitions = [train, validation, calibration, test]
    elif strategy == "group":
        groups = np.asarray(groups)
        def split(subset, fraction):
            a,b = next(GroupShuffleSplit(n_splits=1, test_size=fraction, random_state=seed).split(subset, groups=groups[subset]))
            return subset[a], subset[b]
        train, held = split(ids, .45)
        validation, held = split(held, 2/3)
        calibration, test = split(held, .5)
        partitions = [train, validation, calibration, test]
    else:
        raise ValueError("Unknown split strategy")
    result = dict(zip(("train", "validation", "calibration", "test"), [[int(i) for i in p] for p in partitions]))
    if any(len({rows[i]["label"] for i in p}) != 2 for p in result.values()):
        raise ValueError("Each partition requires both classes; report this split as not viable, do not retry seeds until favorable")
    return result
