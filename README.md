# PrideGuard

A server-side anti-cheat for Forge 1.12.2 modpacks: 23 checks across combat, world, movement and protocol, a small neural network that learns what cheating looks like on your server, and an in-game admin menu with every setting.

## About

Most anti-cheats are written for vanilla servers and trip constantly in a modpack, where jetpacks, speed boots, trains, reach items and odd block shapes are normal. PrideGuard reads the server's own numbers instead of vanilla constants: the player's real movement-speed and reach attributes, real collision boxes of modded blocks, Forge break-speed, and the player's measured ping. Modded gear that keeps showing up in movement flags can be approved with one click.

It starts in **Safe Mode**: every check watches and alerts staff, but nothing is blocked and nobody is kicked until you decide the checks are tuned for your pack.

PrideGuard was built for the Pride modpack, a 1.12.2 pack of about 750 mods, and works in other Forge or Cleanroom packs. It runs on the server; players do not need it installed. Installing it on the client as well adds the admin menu key.

## Features

### How a flag works
- Each check watches client packets (through a mixin into the server's network handler, on the server thread, before vanilla handles them) or runs once per tick per player.
- Suspicious actions fill a per-check **buffer**; once it reaches the "in a row" count, a **flag** is recorded and the player's **violation level (VL)** for that check rises by `weight × amount`. Clean actions drain the buffer, and VL decays every minute.
- A **Sensitivity** slider (1 = forgiving, 100 = strict) moves every limit inside a check between a "forgiving end" and a "strict end", both of which are settings of their own.
- **Modes** (each includes the ones before it):

| Mode | Effect |
|------|--------|
| Off | the check does not run |
| Watch | logs only |
| Alert | logs and tells staff in chat |
| Block | also stops the action (setback, cancelled hit, block put back) |
| Kick | also kicks once VL reaches the check's kick line |

- **Safe Mode** (on by default) caps every check at Alert. Rule-type checks (New-Player Lava Lock) are not softened by it.
- **Lag fairness**: every player gets a silent transaction ping each tick, so reach and break-speed allow for their real delay. A server tick slower than `lagTickMs` pauses movement timing checks for a grace period. Entity positions are kept for the last ticks so hits are judged against where the target was on the attacker's screen.
- **Teleport grace**: server teleports (commands, pearls, respawn, dimension change) reset movement state; once the client confirms the teleport only a 3-tick settle is used.
- **Trust tiers** from play time (red, orange, yellow, green). Ops can count as green. Trust is shown in alerts and can be used to skip a check.

### Checks

**Combat**

| Check (id) | Detects |
|------------|---------|
| Reach (`reach`) | Hitting further than allowed (3 survival, 6 creative, plus any extra reach attribute from items or commands). Measured from standing and sneaking eye height to every box the target had during the attacker's ping plus extra ticks. |
| Hit Through Walls (`walls`) | Hitting an entity when every line from the eye to its centre, top, closest point and four upper corners is blocked by a solid block. Point-blank hits are skipped. |
| Kill Aura (`killaura`) | Hitting without an arm swing in the same tick, or hitting two or more different targets in one tick. |
| Auto Clicker (`autoclicker`) | More attacks per second than a hand manages, or fast clicking (above `rhythmFromCps`) whose gap timing varies less than a human's. Mining swings are not counted. |
| Aim Analysis (`aim`) | Only during fights (within 2 s of a hit). A 60°+ snap onto the target in the tick of the hit, or pitch changes whose common step (GCD) is smaller than any real mouse sensitivity produces. |
| AI Cheat Brain (`brain`) | See "AI Cheat Brain" below. Adds a flag when the trained network's score passes the threshold. |

**World**

| Check (id) | Detects |
|------------|---------|
| Fast Break (`fastbreak`) | The server adds up real break progress every tick (tool, enchantments, haste, water, and every mod's Forge BreakSpeed changes). Finishing a block before progress plus a ping-based allowance reaches 100% flags; in Block mode the block is put back on the client. |
| Nuker (`nuker`) | Starting to break more than 2 blocks in one tick, or too many instant breaks per second. |
| Far Break / Place (`farinteract`) | Breaking or using a block beyond the player's reach attribute plus a margin. |
| No-Swing Breaking (`noswing`) | Digging a block for many ticks with no arm swing at all. |
| Air Place (`airplace`) | Placing against a block face that is air or liquid. |
| Scaffold / Impossible Place (`scaffold`) | A click point outside the block, clicking a face from behind it, or placing far from where the player is looking. |
| New-Player Lava Lock (`lavalock`) | A rule, not a cheat guess: players with less than `newPlayerHours` of play time cannot pour lava (including any fluid hotter than 600 K) or use flint and steel / fire charges outside land where they have builder trust. Land is read from RealmCoin if installed; without it new players simply wait the hours. Creative players and holders of `prideguard.bypass` are not affected. Default mode is Block. |

**Movement**

Movement checks skip players who are riding, flying with permission, using elytra, spectating, dead, sleeping, in teleport grace, during server lag, under Levitation, in cobwebs, or carrying an approved Movement Source. Knockback and explosions add allowance rather than exempting.

| Check (id) | Detects |
|------------|---------|
| Speed (`speed`) | Horizontal speed above a limit derived from the server's movement-speed attribute (so sprint, potions and modded gear are included), with ice, water and knockback allowances. Small overshoots accumulate and drain, so one odd tick never flags. |
| Fly / Hover (`fly`) | Too many ticks in the air without falling, with no ground, liquid, ladder or recent slime bounce. Extra allowance after knockback and with Jump Boost. |
| Step (`step`) | Moving up more than the player's own step height (modded boots raise it) while staying on the ground, with no jump. |
| Water Walk (`jesus`) | Standing or walking on top of water or lava: liquid right below, feet dry, nothing solid to stand on. |
| Timer (`timer`) | Each movement packet advances a "packet clock" by 50 ms. If it runs ahead of real time by more than the allowance, the client is ticking too fast. Banked time is capped at 1 s; resets after lag, teleports and respawns. |
| No Fall (Ground Spoof) (`groundspoof`) | Claiming to be on the ground when the server finds no collision box or liquid under the feet. Checks the ground flag against real collision, not fall damage, since many modded boots cancel fall damage legitimately. |

**Protocol**

| Check (id) | Detects |
|------------|---------|
| Bad Packets (`badpackets`) | Pitch beyond ±90°, attacking yourself, attacking while dead or asleep, switching to the slot already held, starting to sprint twice. |
| Packet Flood (`flood`) | More client packets per second than the limit. |
| Crash / Dupe Exploits (`exploits`) | Creative-inventory packets from non-creative players, items with very deep or very large NBT, clicks on a window that isn't open or on a slot outside it, over-long sign lines, book-edit spam or oversized book packets, tab-complete spam. Limits are loose because modded items legitimately carry big NBT. |
| Client Mods (`clientmods`) | On join, compares the mod list from the Forge handshake with a watch list of known cheat clients and mods (Wurst, ForgeHax, Impact, Aristois, LiquidBounce, Meteor, Wolfram, Sigma, X-ray mods, Freecam, KillAura, autoclicker mods, Nodus, Huzuni, Future, RusherHack, Inertia, KAMI). Cheat clients can hide, so this is a deterrent and a record, not proof. |

### AI Cheat Brain
- A small neural network (12 inputs, one tanh hidden layer of 16, sigmoid output), written in plain Java with no external libraries.
- Every hit is recorded as aim error to the target, last turn size, average recent turn, time since last hit, distance, whether the arm swung, and whether the target changed. Every 5 hits, the last 20 hits are turned into 12 features (mean and spread of aim error, share of precise hits, snap size, turn size, mean and spread of click interval, share of very fast hits, distance, swing rate, target-switch rate, largest turn).
- Train it on your own server: record players fighting legitimately or with a test cheat client (`/pg brain record`), then `/pg brain train`. At least 5 windows of each label are needed. Training uses 80% of the data and reports accuracy on the held-back 20%.
- Training data and the trained network are stored per world in `<world>/prideguard/brain/data.csv` and `brain.txt`, and loaded on server start.
- It never punishes on its own: by default it is a Watch check, and `/pg info` shows each player's latest score.

### Punishment ladder (repeat offenders)
- Kicks are remembered per player for `windowHours` in `config/prideguard-ladder.txt`.
- After `warnAtKicks` kicks the player gets a warning on join.
- Optional automatic temp-ban after `tempbanAtKicks` kicks in the window. **Off by default**; bans stay a human decision unless you switch it on.
- `/pg pardon <player>` clears kick history and lifts a temp-ban.

### Movement Sources
Every World and Movement flag records the non-vanilla items the player was wearing, holding or carrying in Baubles slots, and any modded entity they were riding. The Movement Sources page lists these by number of flags and number of players (3+ players is highlighted as "almost certainly the mod, not a cheat"). Click one to approve it; players carrying an approved source are skipped by movement checks. Approved sources are saved as `item:<id>` or `ride:<entity id>`.

### Alerts
- Staff with `prideguard.alerts` get a chat line per flag (with cooldown and minimum VL), optionally with ping, a hover showing the check description and held/ridden item, a click-to-teleport action, and a sound.
- Alerts can also go to the server console.
- Each staff member can turn their own alerts off (`/pg alerts`).
- Setting changes are announced to other staff and kept in an audit log.

### Logging to MySQL (optional)
If PridePrism is installed, flags are written in batches to a `pg_flags` table in PridePrism's database (time, player, check, category, VL, detail, mode, position, ping, TPS, held item, riding, trust). Without PridePrism, flags stay in memory only.

### Admin menu
Opened with `/pg gui` or an unbound key ("Open PrideGuard (admins)" under the PrideGuard category). Requires `prideguard.admin`. Pages, with breadcrumbs and Back:

| Page | Contents |
|------|----------|
| Dashboard | online count, flags since start, last hour, actions stopped, most flagged player, flags per check, flags per minute for the last 30 minutes |
| Live Feed | every flag, filterable by category; click one for full details (player, trust, check, detail, VL, ping, TPS, action, position, held, riding, time) |
| Players | online players sorted by VL with ping and trust; per player: VL and flags per check, play time, position, health, and buttons for Reset VL, Exempt 10 m / 1 h, their flags, Go to, Kick (with confirm) |
| Checks | by category; per check: mode buttons, sensitivity slider, in-a-row / decay / kick-line steppers, flags and stops, players caught, cost per run, flags by hour, and a link to all of its settings |
| Punishment Ladder | set a whole category's mode at once; link to the repeat-offender settings |
| Movement Sources | learned modded gear and vehicles, click to approve or revoke |
| Settings | every global option |
| All Options | every option of every check in one searchable list |
| Performance | time spent in each check |
| Audit Log | who changed which setting and when |
| Server | TPS, tick time, online players, memory, uptime, flags by hour |
| Help | a short guide |

### Self-test
If a file named `prideguard-selftest` exists in the server folder, the first player to join becomes the test subject 5 seconds later. PrideGuard first requires that normal play produced no flags, then feeds the checks the packets a cheat client would send (reach, bad packets, kill aura, timer, speed, fly, step, no fall) and tests the options engine and the punishment ladder. The result is logged as `PRIDEGUARD PASS` or `PRIDEGUARD FAIL`. Use a throwaway test world.

## Commands

`/prideguard`, alias `/pg`. Requires op level 2.

| Command | Effect |
|---------|--------|
| `/pg gui` | Open the admin menu. |
| `/pg alerts` | Toggle your own chat alerts. |
| `/pg safe <on\|off>` | Turn Safe Mode on or off. |
| `/pg checks` | List every check with category, mode, sensitivity and flag count, plus lag statistics. |
| `/pg info <player>` | Ping, trust, total VL, per-check VL and flags, and the AI brain score. |
| `/pg reset <player>` | Reset a player's VL and buffers. |
| `/pg exempt <player> <minutes>` | Exempt a player from all checks for a time. |
| `/pg pardon <player>` | Clear kick history and lift a temp-ban (works for offline players who have joined before). |
| `/pg mode <check> <off\|watch\|alert\|cancel\|kick>` | Set a check's mode (`cancel` = Block). |
| `/pg sens <check> <1-100>` | Set a check's sensitivity. |
| `/pg set <scope.key> <value>` | Change any option, e.g. `/pg set reach.survivalReach 3.2` or `/pg set global.kickMessage Bye {check}`. Value `*default` restores the default. |
| `/pg get <scope>` | Show every option of a check (or `global`, `ladder`) with current value and default; changed values are marked. |
| `/pg debug <check\|off>` | Log every decision of one check to the server log, for tuning. |
| `/pg brain` or `/pg brain status` | Brain status: trained or not, training data counts, players being recorded. |
| `/pg brain record <player> <cheat\|legit>` | Record a player's fights as training data with that label. |
| `/pg brain stop <player>` | Stop recording. |
| `/pg brain train [epochs]` | Train on all recorded data (default 300 epochs) and save. |
| `/pg brain score <player>` | Show the player's latest score. |

Most commands that change settings must be run by a player (they are recorded in the audit log under that name). Tab completion covers subcommands, check ids, option keys, choices and player names.

## Permissions

Registered with the Forge permission API. Without a permissions mod, all four default to op level 2.

| Node | Grants |
|------|--------|
| `prideguard.admin` | open the menu and change settings |
| `prideguard.alerts` | receive alerts, kick messages and setting-change notices |
| `prideguard.bypass` | skipped by checks with "Ignore ops" on, and by the Lava Lock |
| `prideguard.trusted` | counts as green trust when "Ops count as green" is on |

## Config

Main file: `config/prideguard.cfg` (Forge config). Every option below is also editable in the menu and with `/pg set`. Global options are in the `general` category; each check's options are in `checks.<id>`; the repeat-offender ladder is in `checks.ladder`.

### Global

| Key | Default | Effect |
|-----|---------|--------|
| `safeMode` | `true` | Checks never block or kick, whatever their mode. |
| `kickBroadcast` | `false` | Announce PrideGuard kicks in chat. |
| `kickMessage` | `You were kicked for {check}.\nIf this was a mistake, tell an admin — it's logged.` | Kick screen text. `{check}`, `{vl}`, `\n`. |
| `alertOps` | `true` | Staff get chat alerts for checks at Alert or higher. |
| `alertCooldownMs` | `2000` | At most one alert per player per check this often (0–60000). |
| `alertMinVl` | `0` | No alert until the VL for that check reaches this. |
| `alertShowPing` | `true` | Ping in the alert line. |
| `alertShowHeld` | `true` | Held/ridden item in the alert hover. |
| `alertClickTp` | `true` | Click an alert to teleport to the player. |
| `alertSound` | `false` | Sound with each alert. |
| `alertConsole` | `true` | Write alerts to the server log. |
| `auditToChat` | `true` | Tell other staff about setting changes. |
| `logToDatabase` | `true` | Write flags to PridePrism's database (`pg_flags`). |
| `feedMax` | `800` | Flags kept in memory for the Live Feed (100–5000). |
| `lagTickMs` | `100` | A tick longer than this counts as lag (50–1000). |
| `lagGraceTicks` | `40` | Movement timing stays relaxed this long after a slow tick. |
| `pingCap` | `200` | Ping probes in flight per player before sending stops. |
| `historyTicks` | `30` | Ticks of entity positions kept for reach judging. |
| `historyRadius` | `10` | Entities within this many blocks of a player have positions recorded. |
| `perfTiming` | `true` | Time every check for the Performance page. |
| `trustGreenHours` | `20` | Play time for green trust. |
| `trustYellowHours` | `3` | Play time for yellow trust. |
| `trustOrangeHours` | `0.5` | Play time for orange trust (below: red). |
| `opsAreTrusted` | `true` | Holders of `prideguard.trusted` are always green. |
| `menuRefreshSec` | `2` | How often the open menu asks for fresh data. |

### Punishment ladder (`checks.ladder`)

| Key | Default | Effect |
|-----|---------|--------|
| `enabled` | `true` | Escalate by how often a player has been kicked. |
| `windowHours` | `24` | Kicks older than this are forgiven. |
| `warnAtKicks` | `1` | Warning on join after this many kicks (0 = never). |
| `tempbanAtKicks` | `3` | Temp-ban after this many kicks in the window (0 = never). |
| `tempbanMinutes` | `30` | Temp-ban length. |
| `tempbanEnabled` | `false` | Allow automatic temp-bans. |
| `tempbanMessage` | `Temporarily banned by PrideGuard for {minutes} min ({kicks} kicks in {hours} h).` | Temp-ban screen text. |

### Settings every check has

| Key | Default | Effect |
|-----|---------|--------|
| `mode` | `WATCH` (Lava Lock: `CANCEL`) | `OFF`, `WATCH`, `ALERT`, `CANCEL` (Block), `KICK`. |
| `sensitivity` | `50` | 1 = forgiving, 100 = strict. |
| `buffer` | per check (1–3) | Suspicious actions in a row before one counts as a flag. |
| `decayPerMinute` | `2` (Aim, Brain: 1; Timer: 3) | VL lost per clean minute. |
| `kickAt` | `20` | VL that kicks in Kick mode with Safe Mode off. |
| `weight` | `1` (Bad Packets: 2; Exploits: 3) | Multiplier on VL added per flag. |
| `ignoreCreative` | `false` | Skip creative players. |
| `ignoreSpectator` | `true` | Skip spectators. |
| `ignoreOps` | `false` | Skip holders of `prideguard.bypass`. |
| `ignoreTrusted` | `false` | Skip green-trust players. |
| `joinGraceSec` | `0` | Seconds after login before the check starts. |
| `maxPingMs` | `0` | Skip players above this ping (0 = always check). |
| `minTps` | `0` | Pause while server TPS is below this (0 = always check). |
| `teleportGraceTicks` | `0` | Ticks after a teleport, respawn or dimension change to ignore. |
| `offDims` | *(empty)* | Comma-separated dimension ids where the check doesn't run. |
| `alertMinVl` | `0` | Per-check minimum VL for alerts. |
| `alertCooldownMs` | `-1` | Per-check alert cooldown (-1 = use the global one). |

### Check limits

Settings shown as *forgiving → strict* are two keys, `<key>Forgiving` and `<key>Strict`; the sensitivity slider picks a value between them.

| Check | Key | Default | Effect |
|-------|-----|---------|--------|
| `reach` | `survivalReach` | 3.0 | Survival hit reach. |
| | `creativeReach` | 6.0 | Creative hit reach. |
| | `margin` | 0.35 → 0.03 | Extra allowance in blocks. |
| | `extraTicks` | 3 | Target history beyond the attacker's ping. |
| | `sneakEye` | true | Also measure from sneaking eye height. |
| `walls` | `closeRange` | 1.2 | Ignore hits closer than this. |
| | `wallTolerance` | 0.25 | A wall this close to the eye doesn't count. |
| `autoclicker` | `window` | 20 | Hits remembered. |
| | `minClicks` | 12 | Hits in a burst before judging rhythm. |
| | `maxCps` | 25 → 14 | Fastest allowed clicks per second. |
| | `rhythmFromCps` | 8 | Rhythm test only above this rate. |
| | `rhythm` | 6 → 14 ms | Least human variation in click gaps. |
| `aim` | `fightMs` | 2000 | Only aim this soon after a hit is analysed. |
| | `window` | 60 | Aim steps kept. |
| | `minSamples` | 40 | Steps needed before a verdict. |
| | `maxTurn` | 40° | Bigger turns are ignored. |
| | `gcd` | 0.0015 → 0.004° | Smallest real mouse step. |
| `brain` | `threshold` | 0.97 → 0.85 | Score above which it flags. |
| `fastbreak` | `slack` | 0.25 → 0.02 | How much sooner than possible a block may break. |
| | `pingJitterCap` | 10 | Most ticks of lag jitter forgiven. |
| `nuker` | `perSecond` | 40 → 14 | Instant breaks per second. |
| `farinteract` | `margin` | 2.0 → 0.9 | Added to block reach. |
| `noswing` | `ticks` | 12 → 5 | Digging ticks without a swing. |
| `scaffold` | `behind` | 0.4 → 0.1 | How far behind the clicked face the eye may be. |
| | `lookAngle` | 75° → 35° | How far the clicked spot may be from the look direction. |
| `speed` | `groundSpeed` | 0.29 | Ground limit, × speed attribute. |
| | `airSpeed` | 0.63 | Air limit, × speed attribute. |
| | `iceTicks` | 40 | Ice allowance duration. |
| | `iceMultiplier` | 2.6 | Ice speed factor. |
| | `waterSpeed` | 0.4 | Limit while swimming, × speed attribute. |
| | `excessCarry` | 0.85 | How much overshoot is remembered each tick. |
| | `margin` | 0.12 → 0.02 | Added to every limit. |
| | `excess` | 1.2 → 0.35 | Total overshoot that counts as speeding. |
| `fly` | `hoverTicks` | 20 → 9 | Ticks in the air without falling. |
| | `knockBonus` | 25 | Extra ticks after knockback. |
| | `jumpBoostBonus` | 10 | Extra ticks with Jump Boost. |
| | `bounceTicks` | 40 | Grace after a slime bounce. |
| `step` | `margin` | 0.25 → 0.05 | Added to the player's step height. |
| `jesus` | `surfaceTicks` | 30 → 10 | Ticks on top of liquid. |
| `timer` | `teleportTicks` | 40 | Ignore after teleport. |
| | `aheadMs` | 400 → 90 | How far the client clock may run ahead. |
| `groundspoof` | `teleportTicks` | 60 | Ignore after teleport. |
| | `groundReach` | 0.6 → 0.12 | How far below the feet still counts as ground. |
| `flood` | `perSecond` | 1500 → 350 | Packets per second. |
| `exploits` | `signChars` | 600 → 384 | Longest sign line. |
| | `booksPerSecond` | 20 → 6 | Book edit packets per second. |
| | `bookBytes` | 32000 → 12000 | Largest book packet. |
| | `tabsPerSecond` | 40 → 12 | Tab-complete requests per second. |
| | `nbtDepth` | 96 → 40 | Deepest item NBT. |
| | `nbtTags` | 200000 → 40000 | Most NBT tags on an item. |

Other config entries:

| Category / key | Default | Effect |
|----------------|---------|--------|
| `checks.lavalock` / `newPlayerHours` | `2` | Hours of play before the Lava Lock no longer applies. |
| `movement` / `approvedSources` | *(empty)* | Approved Movement Sources (`item:<id>` or `ride:<entity id>`). |

Other files: `config/prideguard-ladder.txt` (kick history and temp-bans), `<world>/prideguard/brain/` (brain data).

## Requirements

- Minecraft 1.12.2
- Forge 14.23.5.2860 or newer, or Cleanroom
- [MixinBooter](https://github.com/CleanroomMC/MixinBooter) (bundled with Cleanroom)
- Optional: PridePrism (MySQL/MariaDB logging of flags), RealmCoin (land-aware Lava Lock), Baubles (bauble slots in Movement Sources)

## Install

1. Download `PrideGuard-1.12.2-<version>.jar`.
2. Put it in the server's `mods` folder, with MixinBooter if you are not on Cleanroom.
3. Optionally put it in your own client `mods` folder too, to get the menu key.
4. Start the server, run `/pg gui`, and leave Safe Mode on while you watch the Live Feed and approve Movement Sources. Turn it off with `/pg safe off` when you are happy with the checks.

## Building

```
./gradlew build
```

The jar is written to `build/libs/`. The project targets Java 8 (ForgeGradle 3, MCP snapshot `20171003-1.12`). It compiles against `libs/mixinbooter-api.jar`, `libs/sponge-mixin.jar` and `libs/prideprism.jar` (compile-only; PridePrism is optional at runtime), which you need to place in `libs/` before building.

`BrainNet` has a `main` method with a self-check that trains on synthetic data and verifies save/load.

## License

MIT License. © 2026 crunkazcanbe.

## Credits

Made by crunkazcanbe, with Claude.


## Compile-only jars

The build compiles against these jars in `libs/` (other authors' mods / APIs). They are not included in this repo — get them from their official pages and drop them in `libs/` before building:

- `mixinbooter-api.jar`
- `prideprism.jar`
- `sponge-mixin.jar`
