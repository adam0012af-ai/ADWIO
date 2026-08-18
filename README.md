# ADWIO Player

ADWIO Player is a native Android TV/mobile player with:

- Landscape-first UI
- Playlist-style entry
- Username + password only
- Automatic multi-server account discovery
- Right-side navigation
- Live TV
- Movies
- Series catalog
- Search
- Favorites
- Settings/logout
- Media3 / ExoPlayer playback
- Architecture ready for ADWIO Control

Package: `com.adwio.player`

## Build
Run the GitHub Action **Build ADWIO APK**.

## Multi-host
The initial build contains the two bootstrap hosts supplied for testing. No usernames, passwords, tokens, or admin credentials are stored in source.

## ADWIO Control
The host list is isolated in `ServerRepository`, so a future signed remote-config layer can replace it without changing the login and content layers.
