# ADWIO Control

Private Node.js control API/dashboard. No third-party packages are required.

Environment:
- `PORT` (default 8787)
- `ADWIO_ADMIN_TOKEN` (required for dashboard API)

Android build environment:
- `ADWIO_CONTROL_API_URL=https://your-control-host.example`

The app heartbeat sends only installation ID, sanitized host/domain, playlist type, app version, Android version and device model. It does not send playlist usernames, passwords, or full playlist URLs.
