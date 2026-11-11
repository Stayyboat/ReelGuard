# ReelGuard

ReelGuard is an Android focus utility that aims to block the dedicated **Instagram Reels** and **YouTube Shorts** viewers while leaving normal Instagram and YouTube use available.

## Current implementation

- Kotlin Android app with a Streak-inspired dark dashboard, rounded cards, large typography and violet accent.
- Android AccessibilityService listens only to Instagram (com.instagram.android) and YouTube (com.google.android.youtube) UI events.
- The detector looks for known viewer view-ID markers inside large, visible screen containers. Small bottom-navigation labels and inline thumbnails are not enough to trigger a block.
- A touch-consuming accessibility overlay covers the short-form viewer. Its action uses Android's Back global action and remains in place until the viewer is no longer detected.
- Local daily counters for total blocks, Instagram and YouTube.
- Per-platform switches and a master protection switch.
- No INTERNET permission, analytics, screenshot capture, message access or remote data transfer.

## Install and enable

1. Install ReelGuard.apk.
2. Open ReelGuard and tap **Enable accessibility service**.
3. In Android Accessibility settings, open **ReelGuard protection** and enable it.
4. Return to ReelGuard. Keep the master switch and the two platform switches enabled.
5. Open Instagram Reels or YouTube Shorts to test. Normal feeds, messages and regular long-form videos should remain available.

## How detection works

Android does not provide a normal app API that identifies which internal tab of another app is visible. ReelGuard uses an explicitly enabled AccessibilityService to inspect UI resource identifiers, not the contents of messages or posts. The current rules recognise common Instagram Reels viewer identifiers (for example clips_viewer_view_pager, clips_video_container) and YouTube Shorts identifiers (for example shorts_container, reel_watch_player, reel_recycler_view). A match must be visible and associated with a large on-screen container.

## Important limitations

- Instagram and YouTube are third-party apps. Their private view IDs can change at any time; the detector must be updated if that happens.
- The YouTube Shorts markers are compatibility heuristics and need physical-device verification across the installed YouTube version.
- Inline short videos embedded in a regular feed may not be distinguishable from ordinary feed content and are not guaranteed to be blocked.
- Accessibility services can be stopped by Android or device battery-management software. If protection stops, check the service state in Android Accessibility settings.
- This is a self-control aid, not a tamper-proof parental-control or security boundary. A device owner can disable its AccessibilityService in system settings.

## Build

The GitHub Actions workflow builds a debug APK on every push to main and publishes the APK as a workflow artifact and a GitHub release asset. The project uses Android Gradle Plugin 8.9.1, Kotlin 2.2.10, Java 17 and Android SDK 36.

## Design reference

The dashboard takes visual inspiration from the user-provided Streak 2.8.0 project: near-black backgrounds, quiet card surfaces, rounded geometry, expressive hierarchy and violet accent. ReelGuard's application logic is an independent Kotlin implementation.