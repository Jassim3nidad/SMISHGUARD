# SmishGuard — first Android milestone

Native Kotlin/Compose research prototype for Philippine financial-institution impersonation in SMS. **No trained model or real research dataset is included.** The installed app explicitly says **Model not installed** and does not produce fabricated predictions or scam warnings. Reporting is unavailable because no endpoint exists.

## Open and build on Windows

Open this repository root (the folder containing `settings.gradle.kts`) in Android Studio. Let Gradle sync. Use **JDK 17**, Android **SDK Platform 36**, SDK Build Tools **35.0.0**, and current platform-tools. The app supports Android 8.0/API 26 and later; compile/target SDK is 36.

Pinned build stack: Gradle **8.13** with checksum-verified wrapper; Android Gradle Plugin **8.13.2**; Kotlin/Compose compiler plugin **2.3.21**; Compose BOM **2025.12.01**; Activity **1.12.2**; Lifecycle **2.10.0**; DataStore **1.2.0**; coroutines **1.10.2**. These are stable versions selected for compatibility, not a claim that every dependency is the newest. The newer Compose 2026.08 bundle required SDK 37/AGP 9.1+ during verification and was not used.

Android Studio creates `local.properties` for your SDK. If creating it yourself, escape the drive colon, e.g. `sdk.dir=C\:/Users/YourName/AppData/Local/Android/Sdk`.

### Android Studio import troubleshooting

If Studio reports that Gradle 8.13 is incompatible with JVM 25, open **Settings → Build, Execution, Deployment → Build Tools → Gradle**. Set **Gradle JDK** to the installed **Eclipse Temurin 17** (or use **Add JDK from disk…** to select the JDK 17 installation directory). Check the actual Java version: a misleading SDK name such as `jbr-21` can still point to a Java 25 installation.

Keep **Distribution** set to **Wrapper**. **Gradle user home** is a separate cache directory, normally `C:\Users\YourName\.gradle`; do not put the JDK path in that field. Click **OK**, then sync the project. This matches the JDK 17 used for the verified command-line build.

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug :app:testDebugUnitTest
# On this host, use the helper for a Java/Windows loopback socket workaround:
.\scripts\build.ps1
```

The helper changes `JAVA_TOOL_OPTIONS` only within its process and restores it afterward. It does not alter Windows or Android security settings. Ensure `C:/sg-nonexistent-socket-dir` does not exist; the deliberately invalid Unix-domain socket directory makes Java use its TCP pipe fallback.

APK output: `android\app\build\outputs\apk\debug\app-debug.apk`. This is a development-signed APK, not a production release. Release signing and distribution governance remain future work. First-time dependency downloads require internet on the build computer; app detection has no internet dependency.

Prepared deliverables are in `deliverables/`: `SmishGuard-0.1.0-debug.apk`, a portable `SmishGuard-source.zip`, and `SHA256SUMS.txt`. Regenerate after a successful build with `python scripts\package.py`. The source archive excludes SDK paths, build caches, virtual environments, research data and model runs.

## Run on an emulator

Create an Android API 36 virtual device in Android Studio Device Manager, start it, then:

```powershell
adb devices
adb install -r android\app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n ph.smishguard/.MainActivity
.\scripts\build.ps1 -ConnectedTests
```

An API 36 `SmishGuard_Test_API36` emulator was created for this milestone. The tests cover manual entry, empty/long Taglish input, share/clear, denied SMS state, unavailable reporting, lifecycle clearing and static-screen capture. Synthetic inputs remain within test code. Do not use real private SMS for screenshots or test logs. `VisualReviewTest` disables screenshot protection **only within instrumentation** while capturing static, empty screens; production always enables protection.

## Physical device

Enable developer options/USB debugging if using adb, connect a trusted computer and approve its debugging prompt, then use the same install/start commands. Alternatively transfer the APK and follow the phone's normal per-installer installation prompts. **Sideloading does not guarantee SMS permission**: RECEIVE_SMS is hard restricted and depends on installer allowlisting, runtime consent, telephony and device policy. Never bypass these restrictions. Manual/share checking works without SMS permission.

Onboarding explains processing and limitations. Home shows the real model and scanning states. Settings lets the user consent to new-SMS checks, request notification permission, review Android permission settings, choose a city manually, or clear preferences. A model is still required for actual warnings. Permission denial, force-stop and battery/vendor policies can prevent automatic checks. The app does not monitor RCS, Messenger, WhatsApp or other services and never replaces the default SMS app.

Share plain text from the existing messaging app using Android **Share → SmishGuard**, or type/paste into **Check**. Checking clears text; leaving the app or changing configuration also clears the session. Verify requests independently through official channels. No score could guarantee that a message is legitimate.

## Project map

- `android/app/`: Compose UI, transient ViewModel, preferences, receiver, notifications, offline detector, report contract and Android tests.
- `research/`: dataset schema, preprocessing, group/random/time splits, classical training/export, calibration/evaluation, shortcut comparisons, optional transformer adapter and software tests.
- `docs/decisions.md`: source-document reconciliation and assumptions.
- `docs/privacy.md`: data lifecycle, conservative domain policy and permission limitations.
- `docs/model-contract.md`: portable inference schema and deployment procedure.
- `docs/verification.md`: actual checks, remaining checks and requirement mapping.
- `backend/`: future voluntary-reporting/dashboard scope only.

For research setup and commands, see [research/README.md](research/README.md). No app scans feed the research dataset. Collection requires the separate consent, redaction and ethics process described in the supplied thesis documents.

Build references checked during implementation: [AGP 8.13 compatibility](https://developer.android.com/build/releases/agp-8-13-0-release-notes), [Kotlin compatibility](https://developer.android.com/build/kotlin-support), [Compose BOM](https://developer.android.com/develop/ui/compose/bom), [BOM mapping](https://developer.android.com/develop/ui/compose/bom/bom-mapping). SMS policy references are linked in `docs/privacy.md`.
