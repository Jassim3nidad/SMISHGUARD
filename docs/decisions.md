# Scope and implementation decisions

## Sources read

Read the complete implementation-relevant text of `SmishGuardPH_Chapter1_Introduction_1.docx`, `SmishGuardPH_Chapter2_RRL_1.docx`, and `SmishGuard_ProjectBrief_Client.docx` from the user's Downloads folder on 17 September 2026. The documents were initially absent from the attachments and were subsequently supplied by path. They provide research/product context; the user's explicit milestone and privacy instructions govern implementation. The initial workspace contained only `.idea`; no working source, dataset, or model was found.

## Resolved conflicts and assumptions

| Source issue | Decision for this milestone |
|---|---|
| Brief calls a domain the part before the first slash | Use URI parsing and a conservative suffix policy, never slash splitting. Credentials, subdomains, and URL remainders never enter reports. |
| Brief promises a dashboard and frequently describes "scam" as fact | Dashboard remains a future deliverable. UI says potential impersonation and treats labels as judgments, not proof. |
| Chapter 1 mentions network blocking as a possible future policy use | The app never blocks, deletes, hides, writes, or sends SMS. No SMS provider access. |
| Documents describe direct installation as addressing app-store restrictions | Android's installer allowlist and runtime permission still apply. No safeguard bypass, installer spoofing, default-SMS role, or automatic permission grants. |
| Three languages versus objective shorthand “Filipino and Taglish” | Filipino, English and Taglish all remain in scope. No measured language coverage is claimed before a real model exists. |
| Chapter 1 mentions calamanCy tokenization and character features | Portable deployment begins with an explicitly versioned simple unigram tokenizer. Character feature comparisons are supported in Python. calamanCy is a later experimental preprocessing branch with its own parity/export work, not silently substituted as an equivalent implementation. |
| Five research model families vs first phone runtime | LR, NB and linear SVM comparison/export foundation implemented. Transformer comparison adapter/protocol retained separately; transformer phone export is deferred. No winner chosen. |
| Brief wants linkless report counts but the allowed contract permits only nullable domain | Null can mean linkless **or unreportable URL**. Backend documentation forbids equating null-domain counts with proven linkless counts. Changing the metadata contract requires a later research decision. |
| Raw messages must not be stored, while a research dataset must exist | App scanning and dataset collection remain separate. Python reads only independently consented, reviewed, redacted data. No app donation/collection flow exists. |
| Target of 2,500 and minimum 1,200 samples | Training command rejects a primary held-out run below 1,200. The documented repeated cross-validation/small-sample fallback needs a separately finalized protocol; it is not fabricated here. |

## Architecture

Single Activity with Compose/Material 3, one ViewModel and StateFlow for transient session state, DataStore repository for three preferences, replaceable detector and reporting interfaces. No Room is needed because there is no persisted report/scan history. No analytics, crash reporter, WebView, cloud model API, network client, or INTERNET permission.

Android source lives under `android/`; Gradle root is Android Studio's project entry. Research tooling is under `research/`; documentation under `docs/`; future service scope under `backend/`.

The required 12ui design workflow was attempted. CLI installer returned Windows `spawn EINVAL`; direct CLI execution worked, but hosted draft required a separate account sign-in. No design was generated or claimed as approved. The implementation uses native Material 3 components, scrolling screens, system font scaling and light/dark color schemes. This external-service limitation does not block native development.
