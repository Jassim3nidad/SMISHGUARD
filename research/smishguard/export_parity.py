"""Create Android parity probes for a real frozen export using synthetic text only."""
import argparse
import json
import pickle
from pathlib import Path
import shutil
from .preprocessing import normalize
from .train import margin, transform, write_json
from .parity import predict

PROBES = ["Hello po", "Bayad ang account 123", "Visit https://example.com/synthetic?test=1",
          "Walang link dito. Pakisuri ang account.", "HELLO hello account", "🌟 ñ Á İ hello", "unknownterm", "bayad "*1000]

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("run",type=Path)
    args=parser.parse_args()
    model=json.loads((args.run/"model.json").read_text(encoding="utf-8"))
    with (args.run/"research-model.pkl").open("rb") as f: artifact=pickle.load(f)
    if artifact["condition"] != "deployment": raise ValueError("Only portable deployment exports supported")
    x=transform(artifact["vectorizer"],PROBES,"deployment")
    expected=artifact["calibrator"].predict_proba(margin(artifact["model"],x).reshape(-1,1))[:,1]
    cases=[]
    for text,score in zip(PROBES,expected):
        portable,classified=predict(model,text)
        if abs(portable-score)>1e-10: raise ValueError("Python export parity failed")
        cases.append(dict(text=text,normalized=normalize(text),score=float(score),suspicious=classified))
    directory=args.run/"parity"
    directory.mkdir(exist_ok=True)
    shutil.copyfile(args.run/"model.json",directory/"model.json")
    write_json(directory/"cases.json",cases)
    print("Synthetic probes exported; run Android ExportParityTest with -PmodelParityDir pointing to this parity directory.")

if __name__ == "__main__": main()
