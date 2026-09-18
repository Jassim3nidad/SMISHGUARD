# SmishGuard research foundation

No real dataset, trained detector, accuracy, calibration result, or selected research winner is included. Synthetic fixtures are software tests only. Collection remains separate from app scanning.

## Data and ethics

The primary study targets 2,500 messages and requires at least 1,200 for this held-out pipeline. Collection begins only after university ethics approval, a documented consent process, and participant-side redaction. This repository does not implement or authorize collection. No donors, timestamps or labels have been invented.

`dataset-schema.csv` contains headers only. UTF-8 CSV fields:

| Field | Meaning |
|---|---|
| id | Unique anonymous research record ID, never a phone/device ID |
| text_redacted | Independently reviewed, consented and uniformly redacted message |
| label | Adjudicated 0 (not suspicious) or 1 (financial impersonation) |
| language | fil, en, or taglish |
| source_type | Consented donation, research-team donation, or documented institutional publication; provenance stays in controlled research records |
| consent_verified | Literal true after consent or approved public-source provenance review |
| redaction_verified | Literal true after human verification of name/account/number/URL redaction |
| financial_scope | Literal true for the primary study; other scam sectors stay separate |
| received_at | Optional actual ISO-8601 timestamp with timezone; empty when unknown |
| annotator_1, annotator_2 | Independent blinded labels before adjudication; optional until completed |
| adjudication_note | Reviewed nonidentifying justification; no donor details |

Use the same placeholders for both labels: `zznamezz`, `zzaccountzz`, `zznumberzz`, `zzemailzz`, `zzurlzz`. Redaction must not leave source-specific bracket styles. The automatic lexical normalizer is **not a complete PII redactor**; it cannot reliably identify names or all obfuscated identifiers. Human-verified redaction is required. Train-only fitting applies to vocabulary/IDF/classifiers; label-blind template grouping occurs before partitioning to prevent near-copy leakage.

Structured URL comparisons require approved participant-side extraction of coarse URL facts before full URL removal. Four optional schema columns become mandatory for `text-url`: `url_count` (number of links), `url_max_host_length` (maximum hostname length), `url_digit_fraction` (largest per-host fraction of digits), and `url_max_depth` (maximum hostname dot count). Record actual zeros for linkless messages; leave missing facts empty, never invent zeros. Training normalizes/caps these facts and rejects missing or invalid values. These are research collection fields, **not** additions to the app reporting contract. Never retain a private original URL just to run the comparator. Apply extraction uniformly across both labels; redaction effects are themselves part of shortcut evaluation.

SMS Spam Collection and SmishTank are explicitly rejected from the primary pipeline. Keep licensed external corpora in a separate side-test protocol; no labels or dates are guessed. Below-minimum repeated cross-validation with uncertainty intervals, external side tests, source-held-out tests, and full inter-annotator protocol require further research implementation. The current tool calculates Cohen's kappa when two independent label columns exist; it cannot replace blinded labeling or adjudication.

## Windows commands

Run from the repository root:

```powershell
python -m venv research\.venv
research\.venv\Scripts\python.exe -m pip install -r research\requirements.txt
$env:PYTHONPATH = 'research'
research\.venv\Scripts\python.exe -m unittest discover -s research\tests -v
research\.venv\Scripts\python.exe -m smishguard.parity
```

Real-data commands, only once an approved dataset exists:

```powershell
research\.venv\Scripts\python.exe -m smishguard.train research\data\approved.csv research\runs\lr-group --model lr --split group --condition deployment
research\.venv\Scripts\python.exe -m smishguard.evaluate research\data\approved.csv research\runs\lr-group
```

Output directories must be new. Training creates a frozen manifest, model, calibration and validation-derived threshold. It does not transform final-test messages. Final evaluation is separate, verifies dataset hash, refuses to overwrite an existing final report, and never automatically approves deployment or selects a winner. Pickles are trusted local outputs only; never load a downloaded model pickle.

Repeat the prespecified experiment matrix using the same dataset, seed and split strategy: `--model lr|nb|svm`; `--split random|group|time`; `--condition deployment|text-only|text-url|placeholders|shape|punctuation`; `--analyzer word|char`. Only word-unigram deployment exports are currently Android compatible. Other combinations remain research-only. Empty-vocabulary or one-class partitions fail explicitly and must be reported as invalid, not replaced by favorable seeds. Time splits reject missing timestamps and keep equal timestamps together. Group splits keep whole connected template components together. Random/time splits record remaining group overlap rather than pretending they are group-disjoint.

Train/validation/calibration/test target proportions are 55/15/15/15; group granularity changes actual counts. Validation selects threshold by maximum F1 over 0.05–0.95 (ties choose higher cutoff). Calibration fits a separate sigmoid. This is a declared baseline threshold policy, not a thesis-approved cost optimum. Final outputs include precision, recall, F1, trapezoidal PR-AUC, average precision, ROC-AUC, FPR/FNR, Brier, log loss, ten-bin ECE/reliability data, confusion counts and hypothetical 0.1/1/5% base-rate PPV. No smartphone latency or memory is fabricated.

## Transformer comparisons

`smishguard.transformer_compare` preserves XLM-RoBERTa and RoBERTa-Tagalog comparison support with the same frozen split IDs and a separate final-test action. It requires a vetted local **safetensors** checkpoint and tokenizer, `torch` and `transformers`; those heavyweight optional dependencies/checkpoints were not installed or run in this milestone. Select and freeze their compatible versions and model licenses before the real experiment. The adapter records installed versions and checkpoint hashes, rejects remote custom code, never uploads models, and defaults to deterministic CPU training. Three epochs, batch 8, 256-token truncation and AdamW 2e-5 are explicit starting settings, not selected results.

```powershell
research\.venv\Scripts\python.exe -m smishguard.transformer_compare research\data\approved.csv research\runs\lr-group research\runs\xlmr-group --family xlm-roberta --checkpoint C:\models\vetted-xlmr
# Run the same command with --final-test only after freezing research choices.
```

For RoBERTa-Tagalog, use `--family roberta-tagalog` and its independently vetted checkpoint. Preprocessing comparisons that preserve full multilingual text, calamanCy, structured-URL fusion with transformers, hyperparameter search and mobile transformer export remain explicit extensions. The initial adapter supports deployment/text-only conditions and does not claim to complete all transformer experiments.

Method references: [scikit-learn calibration](https://scikit-learn.org/stable/modules/calibration.html), [Hugging Face training](https://huggingface.co/docs/transformers/training). These are implementation guidance; the supplied thesis documents determine the study scope.
