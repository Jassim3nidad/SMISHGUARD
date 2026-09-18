# Offline model contract

The APK contains no trained model. `MissingDetector` returns `Unavailable`; corrupt/unsupported assets return `InvalidModel`; inference can return `Failure` or `Success`. The app never treats a missing model as a negative prediction.

For this first deployment path, place a reviewed `model.json` in `android/app/src/main/assets/` and rebuild. Loader bounds the file at 8 MB and vocabulary at 50,000 entries, rejects incompatible schemas/feature types, duplicate tokens, inconsistent dimensions, nonfinite parameters, invalid IDF or thresholds, wrong class order, and synthetic assets. Unit resources and debug fixtures are never chosen by the production loader.

The JSON exports schema version, preprocessing version, feature type, ordered vocabulary, IDF vector, weights, bias, class mapping, threshold, optional sigmoid calibration (`a`, `b`), calibration evaluation flag, and synthetic flag. The synthetic test JSON under `src/test/resources` is an executable schema example but **must never be installed as a real model**.

`sg-ascii-v1`: fold only A–Z to a–z; replace explicit HTTP(S)/www spans with `zzurlzz`, email spans with `zzemailzz`, each digit run with `zznumberzz`; replace everything outside a–z with spaces; collapse/trim; split on spaces. No label-specific rule, stemming, learned redaction, or network request. Non-ASCII letters, punctuation and emoji are omitted by this first lexical runtime; this is a documented limitation for future tokenizer comparisons. The research pipeline separately studies punctuation/emoji and character features. Dataset personal-name redaction uses `zznamezz` and account placeholders `zzaccountzz` before research ingestion; people must review those redactions uniformly for both classes.

Features are raw unigram counts × exported IDF followed by L2 normalization. Unknown terms contribute zero. Decision margin is `bias + dot(weights, normalized_features)`. LR/SVM export their coefficient vector directly. NB exports the positive-minus-negative log feature likelihoods and log prior difference, producing a log-odds margin. Optional calibration computes `sigmoid(a * margin + b)`. No arbitrary scikit-learn-to-TFLite conversion is assumed. This small deterministic Kotlin runtime avoids an additional ML native runtime and supports explicit cross-language parity.

A raw SVM margin is never labeled probability. Android displays a percentage only if sigmoid parameters exist AND `calibration_evaluated` is true, and never for synthetic fixtures. Export defaults this flag to false even after fitting a calibrator. Researchers must review the actual final calibration/error results before approving any percentage display; running a script alone cannot establish acceptable calibration. The reporting `score` is the model's threshold score (sigmoid output if calibrated, margin otherwise); a future backend needs out-of-band deployment version documentation because the user's allowed payload does not include a model ID.

## Parity and release procedure

1. Train only on verified research data. Freeze preprocessing and all splits.
2. Evaluate once on untouched final data; inspect errors, calibration, base-rate sensitivity and shortcut comparisons. No research winner is automatically selected.
3. Use synthetic privacy-safe probes to compare scikit-learn, portable Python arithmetic and Android (tolerance 1e-10 for the included double runtime). `python -m smishguard.parity` generates committed tests, not a deployment model. For a real export, run equivalent probes against that export before installation.
4. Review model source, calibration and threshold documentation, then package the real asset. Do not reuse the synthetic test's weights.
5. Run phone tests in airplane mode, measure latency/memory, test long/unknown/linkless/Taglish cases and false positives. No real-model phone results exist in this milestone.

For a real trained export, these commands compare scikit-learn → portable Python → Android's Kotlin runtime using only synthetic probe messages:

```powershell
$env:PYTHONPATH='research'
research\.venv\Scripts\python.exe -m smishguard.export_parity research\runs\lr-group
.\gradlew.bat :app:testDebugUnitTest '-PmodelParityDir=C:/absolute/path/to/research/runs/lr-group/parity'
```

`ExportParityTest` explicitly skips when no real export path is provided. The always-run `CoreTest.pythonKotlinParity` uses the checked-in synthetic fixture. Neither test constitutes evaluation of predictive accuracy.

Transformer research comparison remains in the study. Their deployment requires a separate tokenizer, supported export/runtime, quantization evaluation and parity work; they are not accepted by this runtime.
