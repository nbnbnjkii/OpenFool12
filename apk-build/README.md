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

## Lower hand and natural card colors (0.6.3)

The portrait fan now uses a shallow ±10-degree angle and sits behind an opaque
95-unit bottom panel. Approximately the upper half of each card remains visible
and draggable. The panel blocks touches on the hidden halves; a dragged card
is lifted above it and becomes fully visible. Fan geometry is checked for all
hand sizes from 1 to 52.

All hand-card gray/green tinting and hover-target highlights have been removed.
Cards retain their original deck colors. Automatic legal defense targeting,
passing rules, and invalid-drop return behavior are unchanged. This supersedes
the earlier descriptions of target highlighting and a fully visible fan.

## Local wallet, decision clock and paid tools (0.7.0)

- One-time local welcome balance: 10,000 coins and 20 crystals. Persisted in
  OpenFool preferences, not replenished by reopening a menu or restarting.
- A 30-second ring surrounds the active avatar. A generated three-beep WAV
  sounds once when 15 seconds remain. Animation time and app suspension do
  not consume a decision. At timeout, an undefended human takes; a human
  throwing finishes; the first attack can be selected automatically.
- Three icon-only buttons next to the human: undo (2 crystals each successful
  use), discard viewer (3 crystals to unlock for this game), hints (1 crystal
  to unlock for this game). The two unlocks reset on a new game, not when a
  modal is dismissed. Insufficient funds never unlock or charge an action.
- Paid undo is available for 3 seconds after the last human card. Bots cannot
  respond during this interval. Undo restores the exact hand, attack/defense
  arrays, attacker/thrower, done statuses, taking flag and rule state. It cannot
  undo a bot response, finished round, or revealed deal. Invalid undo is free.
- The discard modal shows ONLY cards in the actual discard pile, never cards
  collected by a player. Any tap or Back closes it without reaching underlying
  game controls. The decision clock continues while it is open.
- Hints tint ONLY legal cards in the human hand. No opponent/target highlight.
- Removed the lobby's card-loading indicator and disabled-button wait gate.
  Only the selected deck is queued instead of all four. Other deck styles
  load on demand in settings. Necessary assets are finalized on entering a
  game; a first-launch resource decode can still take time on slower devices.

Manual checks: restart after spending; open/close the discard modal repeatedly;
try every tool with insufficient funds; undo throws/defenses/transfers; verify
warning at 15 seconds and timeout at 30; verify the panel cannot leak a tap.
No real-money purchases or network balance are implemented.

## Reference-inspired compact HUD (0.7.1)

The game table is unchanged. The bottom panel uses the existing navy/teal palette
with the active action/status on the left, human avatar and timer in the middle,
and three larger horizontal tool icons on the right. Crystal prices sit above
the icons; the real saved coin/crystal balances sit below them. Unlocked tools
show zero further cost for the rest of the game.

The hand's masked half-card layout follows the panel's new 115-unit height.
Contextual take/done/give buttons share the left slot, never overlap one another
or the avatar, and retain their original legality checks. A human decision
shows “Ваш ход”; a bot decision never displays that message.

The top row shows the actual passing/throw-in rule selection and its icon, plus
“Ставка: 0”. Bot games do not yet implement wager selection or coin settlements,
so this display does not fabricate a stake or silently debit the wallet.
