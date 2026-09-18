"""Optional local transformer comparison using an existing frozen split manifest.

Not executed without research data and vetted checkpoints. No upload or phone export.
"""
import argparse
import hashlib
import json
from pathlib import Path
import random
import shutil
import numpy as np
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import f1_score
from .data import read_dataset
from .preprocessing import feature_text
from .train import write_json
from .evaluate import metrics

def main():
    p = argparse.ArgumentParser()
    p.add_argument("dataset", type=Path)
    p.add_argument("split_run", type=Path, help="Existing LR/NB/SVM run; reuse exact split IDs")
    p.add_argument("output", type=Path)
    p.add_argument("--family", choices=["xlm-roberta","roberta-tagalog"], required=True)
    p.add_argument("--checkpoint", type=Path, required=True, help="Vetted local safetensors checkpoint and tokenizer")
    p.add_argument("--condition", choices=["deployment","text-only"], default="deployment")
    p.add_argument("--epochs", type=int, default=3)
    p.add_argument("--batch-size", type=int, default=8)
    p.add_argument("--final-test", action="store_true", help="Evaluate an already frozen output once")
    args = p.parse_args()
    import torch
    import transformers
    from transformers import AutoTokenizer, AutoModelForSequenceClassification
    metadata = json.loads((args.split_run/"run.json").read_text(encoding="utf-8"))
    if hashlib.sha256(args.dataset.read_bytes()).hexdigest() != metadata["dataset_sha256"]:
        raise ValueError("Dataset must match frozen manifest")
    if args.epochs < 1 or args.batch_size < 1:
        raise ValueError("Positive epochs/batch size required")
    seed = metadata["seed"]
    random.seed(seed); np.random.seed(seed); torch.manual_seed(seed)
    torch.use_deterministic_algorithms(True)
    # CPU by default for repeatability; GPU benchmarking requires a separately recorded config.
    rows = {r["id"]:r for r in read_dataset(args.dataset)}
    splits = json.loads((args.split_run/"splits.json").read_text(encoding="utf-8"))
    checkpoint = args.output/"checkpoint" if args.final_test else args.checkpoint
    tokenizer = AutoTokenizer.from_pretrained(checkpoint, local_files_only=True, trust_remote_code=False)
    model = AutoModelForSequenceClassification.from_pretrained(checkpoint, num_labels=2,
        local_files_only=True, trust_remote_code=False, use_safetensors=True)
    expected = "xlm-roberta" if args.family == "xlm-roberta" else "roberta"
    if model.config.model_type != expected:
        raise ValueError("Checkpoint architecture does not match declared family")
    def batches(partition, shuffle=False):
        ids = list(splits[partition])
        if shuffle: random.shuffle(ids)
        for start in range(0,len(ids),args.batch_size):
            selected = [rows[i] for i in ids[start:start+args.batch_size]]
            encoded = tokenizer([feature_text(r["text_redacted"], args.condition) for r in selected],
                padding=True, truncation=True, max_length=256, return_tensors="pt")
            yield encoded, torch.tensor([int(r["label"]) for r in selected])
    def scores(partition):
        model.eval(); out=[]; labels=[]
        with torch.no_grad():
            for batch,y in batches(partition):
                logits=model(**batch).logits
                out.extend((logits[:,1]-logits[:,0]).tolist()); labels.extend(y.tolist())
        return np.array(out).reshape(-1,1),np.array(labels)
    if args.final_test:
        if (args.output/"final-test.json").exists(): raise ValueError("Final test already evaluated")
        frozen=json.loads((args.output/"transformer.json").read_text(encoding="utf-8"))
        if frozen["condition"] != args.condition or frozen["family"] != args.family:
            raise ValueError("Configuration differs from frozen model")
        if frozen["dataset_sha256"] != metadata["dataset_sha256"]:
            raise ValueError("Frozen dataset mismatch")
        if (args.output/"splits.json").read_bytes() != (args.split_run/"splits.json").read_bytes():
            raise ValueError("Split manifest differs from training")
        margins, y=scores("test")
        probs=1/(1+np.exp(-np.clip(frozen["a"]*margins[:,0]+frozen["b"],-700,700)))
        write_json(args.output/"final-test.json",metrics(y,probs,frozen["threshold"]))
        return
    args.output.mkdir(parents=True,exist_ok=False)
    optimizer=torch.optim.AdamW(model.parameters(),lr=2e-5)
    for _ in range(args.epochs):
        model.train()
        for batch,y in batches("train",True):
            optimizer.zero_grad()
            loss=model(**batch,labels=y).loss
            loss.backward(); optimizer.step()
    calibration,ycal=scores("calibration")
    calibrator=LogisticRegression(random_state=seed).fit(calibration,ycal)
    validation,yval=scores("validation")
    probabilities=calibrator.predict_proba(validation)[:,1]
    threshold=float(max(np.linspace(.05,.95,91),key=lambda t:(f1_score(yval,probabilities>=t),t)))
    model.save_pretrained(args.output/"checkpoint",safe_serialization=True)
    tokenizer.save_pretrained(args.output/"checkpoint")
    shutil.copyfile(args.split_run/"splits.json",args.output/"splits.json")
    hashes={str(f.relative_to(args.checkpoint)):hashlib.sha256(f.read_bytes()).hexdigest()
            for f in args.checkpoint.rglob("*") if f.is_file()}
    write_json(args.output/"transformer.json",dict(family=args.family,condition=args.condition,
        dataset_sha256=metadata["dataset_sha256"],seed=seed,epochs=args.epochs,batch_size=args.batch_size,
        learning_rate=2e-5,max_length=256,device="cpu",checkpoint_hashes=hashes,
        torch=torch.__version__,transformers=transformers.__version__,threshold=threshold,
        a=float(calibrator.coef_[0,0]),b=float(calibrator.intercept_[0]),test_evaluated=False))
    print("Transformer frozen; final test untouched. No phone export or winner selected.")

if __name__ == "__main__": main()
