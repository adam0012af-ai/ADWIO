# ADWIO Architecture

Startup:
Splash -> Playlist -> Add Playlist -> Login -> Automatic server discovery -> Home.

The login layer iterates enabled servers in priority order and stores the first valid authenticated server in the local session.

Future ADWIO Control:
- unlimited server records
- enable/disable
- internal name
- base URL
- priority
- signed HTTPS config
- local cache fallback

Never store panel admin credentials in the Android app.
