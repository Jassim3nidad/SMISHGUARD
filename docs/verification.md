# Verification and requirement mapping

This file records the first Android milestone. No real dataset/model was supplied, so there is no research accuracy or real-model offline inference result to report.

## Requirements to implementation

| Requirement | Implementation / scope |
|---|---|
| Read sources, preserve repository | Three supplied DOCX texts reviewed; workspace initially contained only `.idea`; conflicts documented in decisions.md |
| Native APK / Kotlin / Compose M3 | Root Gradle project, `android/app`, six screens, system light/dark theme |
| ViewModel / coroutines / StateFlow | `CheckViewModel`, background inference with cancellation and timeout |
| DataStore / minimal persistence | Three preference fields only; no scan/report history, no need for Room |
| Manual and share checking | Edit/clear/scan/error/loading, ACTION_SEND text/plain, 10,000-character bound |
| Honest result | Missing/invalid/failure/success states; no invented score; uncertainty guidance |
| Incoming SMS | Consent, RECEIVE_SMS only, protected SMS_RECEIVED receiver, multipart joining, bounded async work |
| Notification privacy | Channel and runtime/app/channel permission checks, generic private warning, no message text |
| SMS integrity | No read-inbox, send, delete, block or default-SMS APIs/permissions |
| Raw message privacy | Memory only; no saveable state/backup/logging/jobs/network; clears on scan/end/background/rotation |
| Optional reporting | Allowlisted factory, exact preview after real result/city, disabled repository; no fake success |
| URL safety | URI parser, conservative registry suffixes, removes tenants/paths/credentials; malformed inputs tested |
| Model availability | No main model asset; debug-only fixtures cannot drive receiver warnings |
| Portable inference | Versioned TF-IDF/L2/linear double runtime, JSON validation, sigmoid calibration support |
| Training foundation | LR/NB/SVM; frozen manifest and train-only fitting; separate calibration/validation/final evaluation |
| Evaluation | Random/group/time strategies, template grouping, lexical/URL/shortcut conditions, classification/calibration/base-rate metrics |
| Transformers | Optional local XLM-R/RoBERTa-Tagalog adapter; unexecuted without data/checkpoints; phone export deferred |
| Dashboard | Contract and future-work documentation only; voluntary counts not prevalence |

## Test status

Verified on Windows 11 with JDK 17.0.16, Python 3.12 and an Android API 36 emulator, 17–18 September 2026:

| Check | Actual result |
|---|---|
| `:app:assembleDebug` | Passed; installable debug APK produced |
| `:app:lintDebug` | Passed, 0 errors; 10 nonblocking warnings for deliberately pinned versions/target SDK and a KTX style suggestion |
| `:app:testDebugUnitTest` | 6 tests passed; 1 real-export parity test explicitly skipped because no real model exists |
| Python `unittest` | 9 passed, including LR/NB/SVM export arithmetic, calibration arithmetic, train-only vocabulary, split disjointness, missing timestamps, URL fact separation and shortcut isolation |
| API 36 connected UI/lifecycle tests | 5 passed via Gradle after fixing shared-intent routing identity |
| API 36 native instrumentation suite | 6 passed, adding Android PDU decoding of synthetic UCS-2 multipart SMS |
| Native clipboard paste | Additional emulator test passed with SMS permission denied; 7 distinct emulator tests passed overall |
| Dark theme static screens | Additional focused visual test passed in system night mode; captures reviewed |
| APK permissions/assets inspection | RECEIVE_SMS, POST_NOTIFICATIONS and AndroidX's internal signature receiver permission only; no INTERNET/READ_SMS/SEND_SMS; no model or parity JSON packaged |
| Static visual inspection | Onboarding, Home, Check and Settings captured in light/dark themes; navigation scroll reset and consistent surface colors corrected |
| Debug APK signature | `apksigner verify --verbose` passed (APK Signature Scheme v2) |

The initial Java AF_UNIX host error was handled by a process-local JVM property. Initial lint found an unescaped local SDK path; that was fixed. Initial share-test teardown failed because replacing the launch Intent broke ActivityScenario's routing match; sanitizing only payload fields fixed it. No test failures are being relabeled as passes without rerunning.

Python software tests and JVM parity use synthetic fixtures only; they are not research evaluations. The optional transformer adapter passed syntax compilation but was not trained or exercised with heavyweight dependencies. Real-data training/evaluation, physical-device SMS receipt, live warning delivery and live reporting were not tested.

The latest reports are generated under `android/app/build/reports/`; inspect `tests/testDebugUnitTest/index.html`, `lint-results-debug.html` and `androidTests/connected/debug/index.html`. Static screenshots in `docs/screenshots/` contain no real message data. Instrumentation output reported `OK (6 tests)` for the native suite, including `SmsDecodingTest`.

## Required later validation

- Physical-device incoming SMS and multipart delivery across carriers/vendors, including real installer restriction behavior, permission revocation, force-stop and channel disabling.
- Evaluated real model, approved calibration/threshold, actual Python/Android parity for that export, airplane-mode inference and measured latency/memory.
- Full accessibility review with TalkBack and multiple font/display sizes, minimum API 26 and newer Android versions, landscape/tablet layouts and multiple keyboards.
- Ethics-approved collection/redaction tooling; missing-data and small-sample protocol; external corpus side tests, source-held-out analyses and confidence intervals.
- Transformer training, calamanCy experiments, checkpoint/license review, full research experiment matrix and future export work.
- Reporting transport/backend security, retention policy, city-dashboard acceptance and production APK signing.

Exact remaining software commands (after starting an emulator or connecting an authorized device):

```powershell
.\scripts\build.ps1 -ConnectedTests
$env:PYTHONPATH='research'
research\.venv\Scripts\python.exe -m unittest discover -s research\tests -v
```

The six screen flows are implemented even when the model is missing. This is a runnable app foundation, not an evaluated scam detector or a completed thesis study.
