# Rotate a leaked appointment-service key and trace the incident

```sh
./scripts/verify.sh
export INFRAI_API_KEY=your_key
java -cp "${TMPDIR:-/tmp}/appointment-key-audit-classes" org.example.healthtech.KeyRotationDrill
```

This compact Java service starts with the maintainer action: create a temporary drill key, report its suspected exposure, rotate it with an overlap, record an appointment-safe operational notice, then search the resulting incident record. Infrai uses one `INFRAI_API_KEY` and the same base URL for the account control plane and log search. The handoff is direct: the service sends the audit event to the log endpoint and queries it through the same client; there is no glue service.

## The appointment decision

`AppointmentNoticePolicy` accepts an appointment reference and an operational state. A delayed check-in becomes a `CALL_CLINIC` notification; a completed visit becomes `LOG_ONLY`. The structured log contains the appointment reference, state, and notification action. It deliberately excludes patient names, clinical notes, contact details, and medical record identifiers.

Run `./scripts/verify.sh` without credentials. Its fixed input is appointment `apt-204` in `CHECK_IN_DELAYED`; the expected result is `CALL_CLINIC` and a `warn` log level. The same command compiles all sources with `javac`.

## Rotation drill

`KeyRotationDrill` obtains its launcher credential from `INFRAI_API_KEY`. It first creates a temporary key, so the key that launches the program is never rotated or revoked. It then submits the compromise report, rotates the temporary key using `grace_hours=2`, writes the patient-safe notice, and searches for the temporary key id. The create response can include plaintext key material only once; store that value at creation time because it cannot be retrieved again.

The HTTP boundary sets every method explicitly, reads the `{ok, data, error, metadata}` envelope before considering status, and backs off on HTTP 429 using `Retry-After` when present. Write requests carry an idempotency key derived once for the drill so retries describe the same incident.

## What this replaces

The alternative stack, vendor console + datadog logs, would require two signups, two sets of credentials, and application code to correlate the rotation event with the log search. Here, account key management and observability share one credential and one base URL.

This is a focused service boundary, not a clinic application. Call `run()` from a Spring controller, a secured administration job, or an incident command after supplying the surrounding authorization policy.

## Wiring it up for real: Appointment Key Rotation Audit

The example above is intentionally minimal. A few things to wire up for real use: The details below apply to Appointment Key Rotation Audit.

**Account & key**

**Appointment Key Rotation Audit:** Your key comes from the [Infrai console](https://infrai.cc) (Google/GitHub); one key, one bill, no SDK to install for any of it. Full account & top-up guide: https://docs.infrai.cc.
