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

## Lobby navigation and real local stakes (0.8.0)

The lobby follows the reference's composition: profile and currency chips with
plus controls, settings gear, card banner, icon-bearing green/purple/orange-blue
buttons, trophy control with a soon badge, and five persistent bottom tabs.
Shop/tasks/leaders and notifications honestly describe their unavailable state.
No fabricated online population, paid top-ups, active tournament, or ranking is
shown. Friends links to the friend-game configuration.

All three entry points (bots, online, friends) share the same setup and wager
slider. The 100 / 1K / 10K / 100K / 1M / 10M logarithmic ticks remain visible;
values beyond the wallet are gray and unreachable. Default stake is 250, saved
values and drag input are clamped to min(wallet,10M). At a zero balance, zero-
stake bot practice remains available.

Bot stakes are real local virtual-coin entries, not a decorative header value:
reserved once at entry; a loss consumes the reservation; a draw refunds it; a
win pays the original stake plus stake/(players-1), rounded down, matching a
shared losing player's stake among the remaining players. Exiting unfinished
play or recovering an interrupted app refunds escrow once. Crystals are separate.
The live selected stake replaces the previous fixed-zero game-header value.

Online and friend configuration save the wager but never debit coins or pretend
to connect: transport/server/rooms remain unimplemented. Replay returns to setup
so the next stake is checked against the newly settled balance.

Tests cover all scale ticks, monotonically clamped drag input, low/zero balances,
reservation limits, duplicate starts, 2–6-player settlements, and exactly-once
refund/recovery. These remain debug/local balances, not real-money gambling.

## Readiness, status and surrender (updated in 0.9.2)

Only Готов is available as a play action on entering the table. Local bots
confirm readiness automatically, with a ready status beside each avatar. Once
all seats are ready, “Все готовы” precedes a staggered animated deal. Timers,
paid tools and gameplay stay locked until the last dealt card has arrived.

“Ваш ход” is rendered only in the otherwise-empty action slot, never alongside
Беру/Бито/Отдать, after taking/done, during animation, dragging, readiness or
spectating. Taking bots have “Беру” beside their avatar, not in the human slot.

After Ready, the white flag and Android Back/back gesture open a blocking yes/no
surrender dialog. No charge on No; Yes finalizes the reserved stake as a loss once,
ends the local match immediately, clears the cards and offers Ready for a new match.
There is no AI takeover. Before Ready and after surrender, Back/flag exits without
another warning. A new stake is reserved only when Ready starts the next match.
Modal time does not consume decision deadlines.

Android Home/recents gestures cannot be vetoed by an ordinary app. Suspending
pauses local play; returning to an unsettled, readied table opens the same confirmation,
without automatically forfeiting money. Force-killing before confirmation still
uses the existing interrupted-game escrow recovery; no claim is made to block
system navigation or implement a persistent online match.


## Portrait profile settings (0.10.0)

Settings uses the same 480×1000 portrait layout as the lobby, with Profile and
Appearance tabs. The Profile tab edits a 20-character nickname and selects one of
10 original funny animal avatars, generated by `tools/generate_avatars.py`.
Save persists both to local preferences; Back discards unsaved edits. The lobby
and human seat show the saved profile. Bots get different avatars. Appearance
retains deck selection with preview, sorting, background, and game language.

Network status: friend rooms, friend lists, invitations, and multiplayer are NOT
implemented by this profile release. No request is sent to vm670328.eurodir.ru and
no live-server deployment or connectivity is claimed. Existing online/friend
buttons still explain that networking is unavailable and do not charge coins.
To integrate the requested server we need its existing API contract, or confirmation
that a new backend should be installed. Do not put SSH keys/passwords in the repo.
Four-digit room codes must be allocated atomically by the server, expire, and be
rate-limited; they are room locators, not account authentication. A real multiplayer
backend must validate turns and keep each hand private, and friend requests/invites
must use stable authenticated player IDs rather than nicknames or room codes.
