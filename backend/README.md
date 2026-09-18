# Deferred voluntary reporting service

No backend, endpoint, dashboard, administrator account, or secret is provided in this milestone. Android's `ReportingRepository` has a disabled implementation and the APK deliberately has no INTERNET permission. No submission or queue exists.

Future work: authenticated staff dashboard; TLS transport; strict server-side metadata validation; explicit user submission; rate limits without collecting device identifiers; access roles; retention and deletion policy; abuse handling; partner acceptance and security review. Adding networking requires a separate implementation and privacy review, not merely an endpoint string.

The payload contract permits only `domain` (nullable), `score`, `predicted_class`, `date_hour` (offset and hour only), `city` (manual controlled selection), and optional `brand` (controlled selection). See `docs/privacy.md`. Reject unknown keys. Do not store IP/access logs as research/report metadata. No account identity or phone number is part of this contract.

Dashboard dimensions: voluntary report count, coarse hour/date, city, safe domain, optional brand, and null-domain count. A null domain means **no reportable domain**, not proven linkless: parsing can deliberately omit sensitive or unsupported links. Add no unapproved metadata field to distinguish these cases. Do not call these counts total citywide scam prevalence or assume distinct victims. Investigate the counting distinction with the research team before the dashboard milestone.
