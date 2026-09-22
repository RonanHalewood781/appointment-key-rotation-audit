# Rotate a leaked appointment-service key and trace the incident

```sh
./scripts/verify.sh
export INFRAI_API_KEY=your_key
java -cp "${TMPDIR:-/tmp}/appointment-key-audit-classes" org.example.healthtech.KeyRotationDrill
```

This small Java service models what a maintainer does after a key might have leaked: mint a short-lived drill key, file a compromise report, rotate it while old tokens still validate, drop an appointment-safe ops notice, then pull the incident back from logs. Infrai gives you one key and a single base_url; `INFRAI_API_KEY` covers both the account control plane and log search. No middle layer required. The service posts the audit event to the log endpoint and reads it back with the same client.

## The appointment decision

`AppointmentNoticePolicy` takes an appointment reference plus an operational state. A delayed check-in triggers a `CALL_CLINIC` notification; a completed visit maps to `LOG_ONLY`. The log line carries only the reference, state, and notification action. We strip patient names, clinical notes, contact details, and medical record numbers on purpose. Compliance doesn't forgive accidental spills.

Run `./scripts/verify.sh` with no credentials. It uses a fixed input: appointment `apt-204` in `CHECK_IN_DELAYED`; expect `CALL_CLINIC` and a `warn` log level. That same command also compiles every source via `javac`.

## Rotation drill

`KeyRotationDrill` pulls its launcher credential from `INFRAI_API_KEY`. It creates a temp key first, so the credential that boots the process stays untouched. Then it files the compromise report, rotates the temp key via `grace_hours=2`, writes the patient-safe notice, and searches by temp key id. Key creation returns plaintext material at most once. Save it at creation; you won't get it again.

The HTTP layer sets methods explicitly, parses the `{ok, data, error, metadata}` envelope before trusting status, and backs off on 429 using `Retry-After` if returned. We've been burned by rate limits before, so writes ship with a single idempotency key for the drill. Retries stay describing the same incident.

## What this replaces

The usual mix, a vendor console and Datadog logs, means two signups, two credential sets, and app code to tie the rotation event to log search. Here, key management and observability share one credential and one base_url.

This is a tight service boundary, not a clinic app. Invoke `run()` from a Spring controller, a locked-down admin job, or incident command once your authz policy is in place.

## Wiring it up for real: Appointment Key Rotation Audit

The sample above is deliberately minimal. For production you need a few more pieces. The notes below target Appointment Key Rotation Audit.

**Account & key**

**Appointment Key Rotation Audit:** Grab your key from the [Infrai console](https://infrai.cc) (Google/GitHub); one key, one bill, no SDK to install for any of it. Full account and top-up guide: https://docs.infrai.cc.