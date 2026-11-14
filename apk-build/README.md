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
3. Drag a card from the fan to the table to attack. Drag onto a specific
   uncovered attack card to cover it (not necessarily the first card). A tap
   must not play a card; a canceled drag or invalid target must restore it.
   Take cards or finish throwing with БИТО. During animations,
   action buttons must not submit an extra move. A large hand stays onscreen.
   In passing mode, drag a matching-rank card to empty table space before defending; verify the next
   active defender has enough cards. In throw-in mode, passing must be rejected.
4. Finish a game, replay, then return to lobby using МЕНЮ or Android Back.
5. Open settings, choose another card deck, save and start a new game.
6. Confirm online/friends/tournaments never pretend to connect to a server.

Compilation alone does not replace these device checks.

UI fonts are rasterized at 3x virtual size and downscaled with linear mipmap
filtering. Button PNGs are rendered natively at 1320×288 with antialiased masks.
The Android unit suite checks standard deck sizes/dealing for 2–6 players and
passing-mode legality (rank, capacity, membership, full table, defense started).

## Fan and drag controls (0.6.0)

The human hand is laid out as a bounded fan for 1–52 cards. Visible card
transforms and Scene2D hit testing use the same scale and rotation. One pointer
can drag one human-owned card at a time; cards in motion and bot turns are
locked. Legal drop targets are highlighted. Dropping on a covered card, on the
wrong suit/rank, outside the play area, or canceling touch focus restores the
hand without a move. In passing mode, drop a matching rank on empty table space
to transfer rather than cover. Auto-tests cover arbitrary defense indices,
invalid drops at the rules boundary, fully covered tables, and fan geometry.

## Context-sensitive turn controls (0.6.1)

- БЕРУ is shown only when the human is defending uncovered attacks, including
  after a transfer; it is hidden on an attacking turn, during animations/dragging,
  after taking is declared, and once all attacks are covered.
- БИТО is shown only when the human can finish throwing and the bot has covered
  every attack. The human may throw another legal card or finish the round.
- After the human covers all attacks, bots automatically finish that round.
- If the defending bot takes, ОТДАТЬ lets the human finish throwing and hand
  over the pile; the incorrect БИТО label is not used for an unblocked pile.
- Explicit human confirmation remains available at the throw limit or when
  the defending bot has used its last card. Hidden controls are not touchable.

## Automatic drop targeting (0.6.2)

A defensive drop anywhere in the play area automatically covers a legal,
uncovered attack. A legal card directly under the pointer takes priority;
otherwise the first legal open attack is selected. Hover highlighting and
release use the same target policy. A card cannot cover an already covered
attack, the wrong suit, or a higher card. If no defense is legal, passing is
attempted in passing mode; otherwise the card returns to the hand. Drops
outside the play area (unless directly on an open attack) remain canceled.
This supersedes the earlier exact-target-only and empty-space-transfer controls.
