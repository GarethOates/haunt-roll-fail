# Final rules-accuracy pass of the rotated (FFG-era) cards

Branch point: `netrunner` 53f0c4e. Scope: every approximation still listed for the 1,361 rotated cards after the four fix passes
(`STATUS-rot-*.md`, `STATUS-fix-*.md`). Each card was ported again from jinteki.net (`src/clj/game/cards/*.clj`, engine in
`src/clj/game/core/`, behaviour checked against `test/clj/game/cards/*_test.clj`); where the printed text and jinteki differ, the
printed text wins. Generic code is in the new file `rules-ext-final-rot.scala` (`trait FinalRotRules`, ENGINE.md section 8.7), card
logic stays in the card files.

## Mechanism (`rules-ext-final-rot.scala`, `FinalRotRules`; since the integration mixed in after PreventionRules, inside `FinalNsgRules`)

* **Sources of abilities.** The triggered abilities of every card resolve between `FxSourceEffect` markers (game.scala
  `triggerEffects`), so the card whose triggered or paid ability is resolving is known (`fxSources`); `frSourceNow` is the innermost
  card doing something (ability, subroutine with its depth, operation / event, accessed card, scoring agenda). Every damage window
  takes it as its source.
* **Unpreventable damage / trashes**: `FrUnpreventableDamageEffect`, `FrUnpreventableTrashEffect`, `FrUnpreventableTrashPurpose`
  (no prevention ability, no forced prevention; the increases of the damage still apply).
* **"The next paid ability window after X"**: `frArm` / `frArmed` (windows = action phase, `PaidWindowAction`, encounter).
* **Trash batches**: `FrCorpTrashBatch(cards, servers)` after the Corp cards trashed by the Runner by one effect / access.
* **Pre-trash interrupt** (`finalRotPreTriggers`, before every other triggered ability) for Skorpios.
* **Check after every action** (`restrictContinue` override): `FrAddedToScoreArea`, `FrUninstalled`, The Black File leaving play.
* **Virtual tags** (`FrVirtualTagEffect`): counted in the tags, not taken, cannot be removed.
* **Access**: HQ cards drawn at random one at a time (redrawn if HQ changed since the draw), R&D accessed one at a time from the top
  (first card not accessed yet), Gagarin's access cost as a choice before the access.
* **Encounters**: uninstalled ice encountered where it is; "move to the ice and encounter it"; "when encountered" abilities
  granted by other cards go through the encounter prevention window.
* **Rez costs**: additional costs (Hernando Cortez's credits, a forfeit before the rez), alternative costs (Tithonium), also on
  discounted rezzes.
* `FrSpendClicksEffect` (clicks spent by a choice; merged into the NSG pass's `SpendClicksEffect` at the integration), `FrBeforeFirstTurn`,
  `FrStackSearched`, `frStarlight`, `frGripRevealed`.

## Cards fixed

| Code | Card | Before | Now |
|---|---|---|---|
| 13041 | Skorpios Defense Systems | the Corp could remove a card already in the heap, asked per trashed card | interrupt: the Runner cards trashed by one effect (any location) are offered together before any other ability of the trash resolves; removed directly from the set-aside cards (Blacklist irrelevant); once per turn when a card is removed |
| 12103 | Salvaged Vanadis Armory | optional trigger right after the damage | [trash] paid ability usable only until the next paid ability window after the damage closes |
| 12051 | Bioroid Work Crew | usable any time during the Corp's turn | only in the next paid ability window after an operation finished resolving |
| 03022 | Tyr's Hand | the break's `SubsBroken` listed the prevented subroutine; card-effect breaks not interruptible | the break action pays its costs and breaks / reports only the other subroutines; Gbahali / Kongamato breaks (`BreakSubIndexEffect`) open the break window too |
| — | damage source | Runner install / subroutine damage only | every damage: paid / triggered abilities, subroutines, operations / events, accessed cards, scoring agendas (Chrome Parlor, Prana Condenser, Tori Hanzo, Lucky Charm read it) |
| 07031 / 07051 / 11076 / 25007 / 25113 | Amped Up, Stim Dealer, Drone Screen, Stimhack, Flare | damage applied without any window (or preventable) | unpreventable damage (window increases such as Defective Brainchips still apply) |
| 04014 / 11083 | Character Assassination, Cold Read | preventable trash | cannot be prevented |
| 33127 + 09035 | ZATO City Grid + Hunting Grounds / AirbladeX | the granted encounter ability could not be prevented | goes through the encounter prevention window |
| 08068 | Muertos Gang Member | rez offer only when trashed | also when uninstalled any other way (grip, stack, removed from the game) |
| 22042 | Acme Consulting | a real, removable tag | a virtual tag (counted, not taken, cannot be removed) |
| 12036 | MCA Informant | a real, removable tag | a virtual tag, gone when MCA Informant leaves play |
| 07002 | Gagarin Deep Space | 1 credit lost automatically after the access; cards filtered on the pool | before each access of a remote root card the Runner pays 1 credit (any usable source) or does not access it |
| 09013 / 11013 / 11115 / 11098 | Archangel, Chrysalis, Herald, Sapper | the ice was placed temporarily as the outermost ice of the attacked server | encountered where it is (not installed, rezzed for the encounter only) |
| 22054 | Formicary | moved and then approached | moved, the Runner moves to it and encounters it (no approach step) |
| 12098 | Tithonium | alternative cost not offered | "rez by forfeiting an agenda" offered next to every rez option of it |
| 07042 | Hivemind | its counters used by the rotated virus programs only | every virus program: `VirusCost` (canPay / payCosts; abilities ask how many counters come from Hivemind), counter readers (Chisel, Conduit, Fermenter, Tranquilizer, Cordyceps, Imp), Hivemind counters spent by Cordyceps |
| 21002 / 21102 | Yusuf, Musaazi | the pump spent its own counter only while it had one | "any virus counter" for the pump too (the Runner chooses the card) |
| 08061 | Faust | the grip card was chosen after the break | the grip card is trashed as the cost before the break |
| 03047 | Sahasrara | its credits could pay for a program whose memory needs Sahasrara to be trashed | they pay only when the other programs can free the memory, and Sahasrara cannot then be trashed for that install |
| 14018 / 14019 | Surveillance Network Key 1 / 2 | revealed every card drawn during the Corp's action phase | the first card drawn when the Corp spends [click] to draw (basic action, click abilities, operations played with the basic action) |
| 07043 | Progenitor | remembered the hosted program; could restore a counter that was spent | when virus counters are purged, 1 counter is kept on the hosted program if it had any |
| 04069 | Starlight Crusade Funding | double events' additional costs paid | ignored (rotated `RE` doubles and the Startup double helpers; Scrounge / Lie Low / Maintenance Access use them) |
| 11004 | Hernando Cortez | a rez cost increase (lowered by discounts, read by "rez cost" checks) | an additional cost paid on every rez that does not ignore all costs, discounted rezzes included |
| 12065 | Keros Mcintyre | derezzes by Runner triggered abilities not recognised | recognised (triggered abilities are sources) |
| 13021 | Levy Advanced Research Lab | the rest to the bottom in the current order | the Runner chooses the order |
| 22021 | Psych Mike | R&D accesses this turn | R&D accesses during that run |
| 21090 | Jackpot! | offered when an agenda is stolen | whenever an agenda is added to the Runner's score area (stolen, swapped, "as an agenda") |
| 05047 / 10102 | Push Your Luck, Rigged Results | secret spending from the pool only | from every source that can pay for the event's effect, paid when revealed |
| 14009 / 10100 | Security Leak, The Price of Freedom | basic advance action only | also card effects that advance (`AdvanceTimesEffect`, Success) |
| 08021 | Hacktivist Meeting | discounted rezzes (they rez "ignoring costs" after paying) skipped the random trash | charged |
| 10099 | The Black File | a Corp win waited for the next win check when it left play other than by its own ability | the win is checked as soon as it leaves play / loses its abilities |
| 09023 | Surveillance Sweep | no effect | during a run the Runner chooses its trace boost first, then the Corp |
| 14023 | Net Watchlist | break / pump abilities only | every paid ability of an icebreaker (+2 credits, greyed when unaffordable) |
| 06022 | Enhanced Login Protocol | the run menu offered a run with 1 click (the run then failed) | runs greyed out |
| 08113 | Genetics Pavilion | draw bonuses could exceed the cap | at most 2 cards per Runner turn, bonuses included |
| 10109 | Ibrahim Salem | forfeit as an on-rez effect | forfeit as an additional cost before the rez (discounted rezzes too) |
| 10028 | Lakshmi Smartfabrics | protection while Lakshmi stays active | a turn effect (lasts if Lakshmi leaves play) |
| 06103 | IT Department | strength fixed at use time | +1 for the spent counter plus +1 per counter on IT Department at any time this turn |
| 10036 | Mumbad Construction Co. | rezzed Corp cards only | any faceup card (rezzed Corp cards, faceup Runner cards) |
| 29015 / 10047 / 35006 | Executive Boot Camp, Councilman, Bling | pool-only affordability (Councilman paid from the pool) | credit engine (`canSpend` / `SpendEffect`) |
| 13042 | Armored Servers | grip cards collected after the break | 1 grip card per subroutine trashed before each break (bioroid click-breaks too); multi-breaks greyed when the grip is too small |
| 10066 | Personality Profiles | heap / stack snapshots between events (heuristic) | installs from the heap (tracked at install) and stack searches (`FrStackSearched`) |
| 21033 | Bacterial Programming | R&D cards moved to HQ during the access were still accessed | R&D accesses are the first R&D cards not accessed yet |
| 12068 | Warroid Tracker | one trace per trashed card | one trace per batch of trashes |
| 21100 | Overseer Matrix | 1 credit / 1 tag per trashed card, not for its own trash | once per batch: pay up to 1 credit per card trashed from its server for that many tags, its own trash included (persistent) |
| 22041 | Daruma | trashed after the choices | trashed first, then the card in another root / HQ, then the card of this server |
| 25009 | Patchwork | interrupt of the basic actions only | also plays / installs by card effects that pay a cost |
| 22017 | Mind's Eye | its breach included the root | no root accesses |
| 23100 | Watch the World Burn | copies accessed in Archives not removed | removed wherever accessed |
| 10083 | Rebirth | any legal identity of the faction | identities legal in a format of the Runner's deck |
| 25067 | Seidr Laboratories | manual once-per-turn ability | trigger: the first time each turn the Runner loses or spends [click] during a run |
| 10107 | Harishchandra Ent. | grip printed in the log | the UI shows the grip to the Corp while the Runner is tagged |
| 08025 | Hayley Kaplan | mods needing a host not offered | offered with their possible hosts |
| 24001 / 03003 / 08012 | Cyber Bureau, NEXT Design, Jinteki Biotech | "before your first turn" at the start of the first turn | before the first turn begins (`FrBeforeFirstTurn`) |
| — | HQ accesses | drawn when the breach started | drawn one at a time |
| 08003 + spend choices | Adjusted Chronotype | "spend [click]" choices fired `ClickLost` | `SpendClicksEffect` since the integration (Aggressive Trendsetting, Deep Dive, the ph "spend [click] or end the run" ice, Thule Subsea, Manegarm Skunkworks, S-Dobrado, Jaguarundi) |

Verified as exact or as jinteki (status notes corrected, not counted as approximations): Customized Secretary (the UI shows uninstalled
hosted cards since the QA pass), Wari / Paintbrush / Panchatantra (printed subtype choices), Bug (random drawn cards as jinteki),
Copycat (printed text), Street Magic, DJ Fenris and Adam (representations of hosted identities / setup cards), Apocalypse / Reboot
(nothing turns facedown cards faceup), Death and Taxes (a "may" with no downside: automatic like jinteki), Riot Suppression and Prey
(as jinteki), Accelerated Diagnostics (Double / Triple additional costs ignored as printed), Full Immersion RecStudio (constant ability
of a rezzed card), Toshiyuki Sakai, Trojan, Net Police, Lt. Todachine 2, Archives Interface, Maya, Maw, Top Hat, Q-Coherence Chip,
Respirocytes, SSO Industries, Kabonesa Wu, Unorthodox Predictions, Will-o'-the-Wisp, Harbinger (jinteki releases the heap lock),
Warden Fatuma, Evidence Collection 1-3 (campaign cards with no game effect), Accelerated Beta Test (identities of the looked-at cards are
kept through a shuffle), LLDS Energy Regulator (its 3 credits are an ability `CreditCost`).

## Still approximate (and why)

(All six rules items below were replaced by the printed behaviour in the last rules-accuracy pass: STATUS.md "Last rules-accuracy
pass", ENGINE.md 8.9. The list is the record of this pass.)

* Hivemind: a `VirusCost` of a **break or pump** ability spends the program's own counters first, then Hivemind's, without a choice
  (break / pump payments happen inside the break action, they are not asks); paid abilities ask.
* Patchwork: the basic play / install options are still greyed when only Patchwork's discount would make them affordable, so its
  non-printed "prepay" ability (trash 1 grip card: the next play / install costs 2 less) is kept for that case.
* Archangel / Chrysalis / Herald / Sapper accessed while **installed** (only possible through cards that access installed ice) are
  not encountered.
* Personality Profiles: a search is recognised as a stack shuffle while a card whose printed text says "search your stack" / "search
  the stack" resolves (every implemented search shuffles).
* Rebirth: the game has no declared format; it is inferred as the formats in which every card of the Runner's deck is legal.
* Surveillance Network Key: an operation played with the basic action that draws counts as "spends [click] to draw" (jinteki only
  implements the basic draw action).
* (Resolved by the integration with the final NSG pass: Daruma's root-to-root swaps go through `SwapRootEffect` /
  `SwapInstalledWithHandEffect`, which now re-check the one asset / agenda / region per root rule.)
* UI-only secrecy (not a rules difference): Push Your Luck / Rigged Results amounts and Harishchandra's revealed grip are in the
  journal of every client; the UI hides / shows them.

Current-set cards named in the request but owned by the current-set pass (not changed here): Whistleblower, Nuvem SA, Boi-tatá,
Cataloguer (Climactic Showdown was already exact since the prevention pass). The current-set cards edited here are interactions with
rotated cards only: ZATO City Grid (Hunting Grounds), Chisel / Conduit / Fermenter / Tranquilizer / Cordyceps / Imp (Hivemind),
Scrounge / Lie Low / Maintenance Access (Starlight Crusade Funding), the "spend [click]" choices (Adjusted Chronotype), Bling's
affordability check.

## Shared-file edits

| File | Edit | Why |
|---|---|---|
| `game.scala` | `with FinalRotRules` at the end of the `Game` mixins | the new trait (outermost) |
| `game.scala` | `triggerEffects`: `finalRotPreTriggers(e) ++` first, each card's triggers wrapped by `frSourced(c, ...)`, `++ finalRotTriggers(e)` last | Skorpios first, sources of triggered abilities, bookkeeping |
| `game.scala` | `orElse performFinalRot` at the end of the `performInternal` chain | the new effects |
| `game.scala` | `canPay`: `VirusCost` counts `frHiveCounters(c)` | Hivemind |
| `ui.scala` | `canSee`: the Runner's grip while `frGripRevealed` | Harishchandra Ent. |
| `rules-ext-rot-events.scala` | `secretAmounts(max, card)` reads `creditsFor(Runner, Spend.effect(card))`; `RE.doubleOk` / `doubleCost` honour `frStarlight` | Push Your Luck / Rigged Results; Starlight Crusade Funding |
| `rules-ext-startup.scala` | `doubleFree` true for the Runner with `frStarlight` | Starlight Crusade Funding |
| `ENGINE.md` | 8.1 mixin order / chain / triggers, 8.6 order note, access note, new 8.7 | documentation |
| card files | `cards-rot-*` (the cards above), `cards-sg`, `cards-df`, `cards-ur`, `cards-su21`, `cards-elev`, `cards-ms`, `cards-ph`, `cards-tai` (interactions listed above) | ports |

`abilities.scala` (no new hook), `rules-*.scala` core files, `bot.scala`, `meta.scala`, `cards-data*`, `decks*` are unchanged.

## Verification

* `sbt -batch compile` (Scala.js) green.
* JVM harness (scratchpad `final-rot/host`, a copy of `merge4/host` pointed at this worktree): all scripted checks pass —
  `ScriptTest`, `FixScript` 81, `PreventionScript`, `CreditScript`, `RestrictionScript`, `RotIceScript` (211 ice, 633 encounters, 0
  failures), `RotAssetScript`, `RotAgendaScript`, `RotEventScript`, `RotHardwareScript` 80 (Gagarin check updated: the Runner now
  chooses to pay), `RotOperationScript` 56, `RotProgramScript`, `RotMergeScript`, `PresetCheck` (195 presets, 0 invalid) — and the new
  `FinalRotScript` (91 checks: every fix above that bots rarely exercise, plus the serializer round trip of every new user action /
  purpose).
* Bot games (`MergeHost`, every offered and chosen action round-tripped through the serializer; exceptions, empty asks, mismatches
  and over-long games counted as errors): on the final code 250 + 250 games over all presets and 250 rotated Runner presets against
  rotated Corp presets (seeds 9401-9403): 750 games, 0 errors (+ 250 games seed 9302, 0 errors); see STATUS.md "Tested after the final
  rotated pass" (an earlier batch found Faust's break failing when the last grip card paid for it: fixed, with a scripted check).
