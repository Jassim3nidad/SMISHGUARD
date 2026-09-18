"""One final-test evaluation of a frozen local artifact. Never tunes using test results."""
import argparse
import hashlib
import json
import pickle
from pathlib import Path
import time
import numpy as np
from sklearn.metrics import (precision_score, recall_score, f1_score, average_precision_score,
    precision_recall_curve, auc, roc_auc_score, confusion_matrix, brier_score_loss, log_loss, cohen_kappa_score)
from .data import read_dataset
from .train import margin, transform, write_json

def metrics(y, probabilities, threshold):
    y, p = np.asarray(y), np.asarray(probabilities)
    pred = p >= threshold
    tn, fp, fn, tp = confusion_matrix(y, pred, labels=[0,1]).ravel()
    precision, recall, _ = precision_recall_curve(y, p)
    bins = []
    for i in range(10):
        mask = (p >= i/10) & ((p < (i+1)/10) if i < 9 else (p <= 1))
        bins.append(dict(count=int(mask.sum()), mean_probability=float(p[mask].mean()) if mask.any() else None,
                         observed_positive_rate=float(y[mask].mean()) if mask.any() else None))
    fpr, fnr = float(fp/max(1,fp+tn)), float(fn/max(1,fn+tp))
    return dict(precision=float(precision_score(y,pred,zero_division=0)), recall=float(recall_score(y,pred)),
        f1=float(f1_score(y,pred)), pr_auc=float(auc(recall,precision)), average_precision=float(average_precision_score(y,p)),
        roc_auc=float(roc_auc_score(y,p)), false_positive_rate=fpr, false_negative_rate=fnr,
        brier=float(brier_score_loss(y,p)), log_loss=float(log_loss(y,p)), reliability_bins=bins,
        expected_calibration_error=sum(b["count"]/len(y)*abs(b["mean_probability"]-b["observed_positive_rate"]) for b in bins if b["count"]),
        confusion=dict(tn=int(tn),fp=int(fp),fn=int(fn),tp=int(tp)),
        hypothetical_base_rates=[dict(prevalence=r, estimated_positive_predictive_value=(1-fnr)*r/max(1e-15,(1-fnr)*r+fpr*(1-r))) for r in (.001,.01,.05)])

def main():
    p = argparse.ArgumentParser()
    p.add_argument("dataset", type=Path); p.add_argument("run", type=Path)
    args = p.parse_args()
    if (args.run/"final-test.json").exists():
        raise ValueError("Final evaluation already exists. Do not retune against this test set.")
    metadata = json.loads((args.run/"run.json").read_text(encoding="utf-8"))
    if hashlib.sha256(args.dataset.read_bytes()).hexdigest() != metadata["dataset_sha256"]:
        raise ValueError("Dataset changed after split freeze")
    rows = {r["id"]:r for r in read_dataset(args.dataset)}
    manifest = json.loads((args.run/"splits.json").read_text(encoding="utf-8"))
    test = [rows[i] for i in manifest["test"]]
    with open(args.run/"research-model.pkl", "rb") as f:
        artifact = pickle.load(f)  # Only the trusted local training output.
    started = time.perf_counter()
    x = transform(artifact["vectorizer"], [r["text_redacted"] for r in test], artifact["condition"], url_rows=test)
    probabilities = artifact["calibrator"].predict_proba(margin(artifact["model"], x).reshape(-1,1))[:,1]
    result = metrics([int(r["label"]) for r in test], probabilities, artifact["threshold"])
    result["host_batch_seconds"] = time.perf_counter()-started
    result["phone_latency_and_memory"] = "not measured"
    labeled = [r for r in rows.values() if r.get("annotator_1") in {"0","1"} and r.get("annotator_2") in {"0","1"}]
    result["cohen_kappa"] = None
    if labeled:
        value = float(cohen_kappa_score([r["annotator_1"] for r in labeled],[r["annotator_2"] for r in labeled]))
        result["cohen_kappa"] = value if np.isfinite(value) else None
    result["agreement_records"] = len(labeled)
    result["deployment_approval"] = "pending researcher review of calibration, errors, parity and phone tests"
    write_json(args.run/"final-test.json", result)
    print("Final test evaluated once. No model winner or deployment approval inferred.")

if __name__ == "__main__":
    main()
