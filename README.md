# ADWIO Professional 3.0.0

Clean consolidated Android project for ADWIO Player.

## GitHub build
Push the project to the repository root, then open **Actions → Build ADWIO Professional → Run workflow**.

The workflow always produces a Debug APK artifact. It can also produce a signed Release APK when these repository secrets are configured:

- `ADWIO_KEYSTORE_BASE64`
- `ADWIO_KEYSTORE_PASSWORD`
- `ADWIO_KEY_ALIAS`
- `ADWIO_KEY_PASSWORD`

To install the new APK over an existing ADWIO installation without uninstalling it, the release APK must use the same `applicationId` and the same signing key as the currently installed APK. This project keeps `applicationId = com.adwio.player`.

See `docs/IMPLEMENTATION_STATUS.md` for implemented features and validation status.
