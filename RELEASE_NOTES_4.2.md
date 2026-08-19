# ADWIO Professional 4.2.0

Playback + M3U + user-flow reliability release.

- Live mini-player and fullscreen now use the same shared ExoPlayer instance.
- First channel click starts the mini-player with picture and sound.
- Returning from fullscreen LIVE returns the same stream to the mini-player without restarting.
- Closing the system PiP/background window with X stops and releases playback instead of leaving audio running.
- Removing the app task also stops background playback.
- Xtream category/content requests are started in parallel for faster opening.
- M3U login remains fast; full playlist caching now runs in an application-level background scope.
- M3U can scan specifically for LIVE, MOVIE, or SERIES instead of only reading the first live-heavy portion.
- M3U ALL count is shown only when the full cache is available; no incorrect partial count is displayed.
- Add User is now a clean dedicated screen with no saved-user list.
- Saved users moved to the Switch User screen.
- Switch User shows saved users; tap switches, long press provides Edit/Delete.
- User information remains read-only and does not expose the host.
- Application ID remains com.adwio.player.
