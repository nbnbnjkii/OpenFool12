# Android: Neon edition

The standalone build uses JDK 17, Gradle 8.7 and Android SDK 35. It does not
configure the obsolete iOS/Graal plugins from the original root build.

```sh
# Set ANDROID_HOME to an SDK installation, or write apk-build/local.properties.
gradle -p apk-build testDebugUnitTest assembleDebug
```

APK: `apk-build/build/outputs/apk/debug/OpenFoolNeon-debug.apk` (the exact filename
may vary with Gradle). This is a **debug-signed** installable build, not a
production release signed with the original author's key. Android 5.0+.

Alternatively open **Actions → Android APK → Run workflow**, select the working
branch, then download the `OpenFool-neon-debug` artifact when the job succeeds.

## Design and functionality

- Original generated artwork inspired by the supplied portrait references;
  no phone frame or flattened reference screenshot is used as game UI.
- Portrait lobby, green/purple/blue actions, card banner, avatar medallions.
- Portrait neon table; 2–6 player bot selector, 24/36/52-card decks, throw-in / passing modes; live hand/deck counters.
- Separate БИТО / БЕРУ controls wired to the existing rules engine.
- Portrait results and replay/menu actions. Existing advanced card/rule settings
  remain available in their landscape layout, with the shared dark background.
- Network play and tournaments are **not implemented** in the original project.
  Their lobby buttons explain that they are unavailable. Currency/shop/social
  counters are intentionally not fabricated.
- Textures can be regenerated with Pillow: `python tools/generate_theme.py`
  (uses DejaVu Sans installed in `/usr/share/fonts/truetype/dejavu`).

## Manual smoke tests on device

1. Open lobby; wait until cards finish loading. Rotate/resize: UI remains centred.
2. Start 2–6 player bot games. A 24-card deck supports at most four players. Check avatars, card counts and trump card.
3. Attack, cover a card, take cards, finish throwing with БИТО. During animations,
   action buttons must not submit an extra move. A large hand stays onscreen.
   In passing mode, tap a matching-rank card before defending; verify the next
   active defender has enough cards. In throw-in mode, passing must be rejected.
4. Finish a game, replay, then return to lobby using МЕНЮ or Android Back.
5. Open settings, choose another card deck, save and start a new game.
6. Confirm online/friends/tournaments never pretend to connect to a server.

Compilation alone does not replace these device checks.

UI fonts are rasterized at 3x virtual size and downscaled with linear mipmap
filtering. Button PNGs are rendered natively at 1320×288 with antialiased masks.
The Android unit suite checks standard deck sizes/dealing for 2–6 players and
passing-mode legality (rank, capacity, membership, full table, defense started).
