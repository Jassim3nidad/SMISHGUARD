# Privacy and SMS behavior

## Data lifecycle

Typing/pasting/shared text is held in ViewModel StateFlow only. No SavedStateHandle or Compose saveable state is used; the activity clears saved bundles. Share extras and ClipData references are removed from the Activity intent. A checking result retains only a class/score, sanitized domain, and hour source time; raw input is cleared immediately after inference. Navigation away from the session, Android back, backgrounding (`onStop`), or clear resets the session and cancels its coroutine. Rotation also clears the session intentionally. The app does not attempt to erase the user's system clipboard or the originating app's copy. JVM strings are garbage-collected; this is reference clearing, not a promise of forensic memory zeroization.

`FLAG_SECURE` protects recents/screenshots; the Activity is excluded from recents. Keyboard autocorrect is disabled and a password-class keyboard hint discourages keyboard learning; the operating system and user's chosen keyboard are outside the app's control. No backup, logs of messages/errors, analytics, network access, raw-text job inputs, or persistence of scan history exists. DataStore saves only onboarding completion, incoming-SMS consent, and selected city. Clearing local preferences removes all three and turns scanning off; Android's permissions are managed by Android.

The Android framework delivers shared/SMS text in system-owned intents. SmishGuard does not save those intents or request inbox-reading access. Notifications and their PendingIntents never contain message bodies, domains, scores, or sender data.

## Domain policy v1

Reporting extracts explicit HTTP(S) or `www.` candidates, parses using `java.net.URI`, requires a valid host, rejects credentials, malformed URI encoding, backslashes, IP literals, and unknown suffixes. It strips trailing DNS dots and normalizes ASCII case. Non-ASCII hosts that URI cannot safely parse are omitted; IDN/punycode registered labels are omitted conservatively.

Supported registry suffixes are deliberately finite: `com`, `org`, `net`, `edu`, `gov`, `io`, `ph`, `com.ph`, `org.ph`, `net.ph`, `gov.ph`, `edu.ph`, `co.uk`. The longest matching suffix is selected. Only one label immediately above the registry suffix is retained. **All subdomains are discarded**, including customer/account names and private hosting tenants. Example: `alice.github.io/token` becomes `github.io`, not `alice.github.io`; `customer.example.com.ph` becomes `example.com.ph`. Registered labels containing digits, punycode or more than 40 characters are omitted. Unknown suffixes are omitted rather than guessed. This is a conservative reporting policy, not a full public-suffix implementation or a link-legitimacy detector.

For multiple links, only the first successfully sanitized domain is offered. Unparseable links produce no domain. This loses detail deliberately; no parser can prove a registered domain itself never encodes a person's name, so the preview remains important and users can decline reporting. No domains are sent in this milestone. No link is opened, fetched, resolved, or tested against a remote reputation service.

Only controlled city and brand choices are accepted; there is no free-text field that could accidentally contain private data. A future submit action must send the exact preview from `ReportPayload.json()`, whose factory requires a nonsynthetic finite prediction and a selected city. Metadata is not retained after the session.

## Incoming SMS

`SMS_RECEIVED` receiver requires sender-side `BROADCAST_SMS`, checks RECEIVE_SMS and telephony availability, obtains `goAsync()`, and processes the platform-provided PDU segments in order on Dispatchers.Default. It reads only message bodies; it never accesses originating addresses. Malformed/empty/oversized messages are ignored. A seven-second cooperative timeout bounds work, with input/model size caps. `finish()` always runs. No WorkManager job or persistent queue is created. Consent and permissions are rechecked before a warning. Missing/invalid/synthetic models cannot issue real warnings.

Only RECEIVE_SMS and POST_NOTIFICATIONS are declared. There is no READ_SMS, SEND_SMS, contacts, location, identifier, or default-messaging-role request. Notifications use a dedicated channel, runtime permission on API 33+, app/channel enable checks, and secret lockscreen visibility. When disabled, incoming inference may still occur but cannot show a warning. Permission denial or revocation leaves manual/shared checks available. Revocation does not cause automatic rerequests; the settings screen provides system settings access.

Installer allowlisting can prevent RECEIVE_SMS from being granted even for a sideloaded APK. Force-stop, device battery policies, missing telephony and protected SMS delivery rules limit coverage. The app does not monitor RCS or any other messaging app. Its warning is a separate risk advisory, not a duplicate SMS inbox notification.

Official references checked 17 September 2026:

- [RECEIVE_SMS permission and hard restriction](https://developer.android.com/reference/android/Manifest.permission#RECEIVE_SMS)
- [SMS broadcasts and getMessagesFromIntent](https://developer.android.com/reference/android/provider/Telephony.Sms.Intents)
- [Notification runtime permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission)
- [Broadcast receiver lifecycle and goAsync](https://developer.android.com/develop/background-work/background-tasks/broadcasts)
