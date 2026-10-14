# Credits and costs: status of the fix pass

Branch point: `netrunner` a5d069b. This pass replaces the per-port approximations of restricted credits (recurring credits as
cost discounts, "take hosted credits" abilities, refunds after the fact, "usable for anything during a run", stealth costs
not enforced, permanent bad publicity credits) with one payment mechanism in the engine, and ports every card whose status
entry was about credits or costs onto it. New code is in `rules-ext-credits.scala`; ENGINE.md §8.2 documents the API.

## The mechanism (`rules-ext-credits.scala`, trait `CreditRules`)

* **Every payment has a purpose**: `Spend(kind, card, target, server)` — `Spend.play(c)`, `install(c, server, host)`, `rez(c)`,
  `advance(c)`, `trash(c)` (trash cost), `trashResource(c)` (Corp basic action), `removeTag` (Runner basic action), `trace`,
  `psi`, `ability(c)` (paid ability of c), `break(b, ice)`, `pump(b, ice)`, `run(server)` (additional run costs),
  `corpAbility` ("end the run unless the Runner pays N" and other choices imposed by Corp cards), `effect(source)`, `other`.
  Helpers on Spend: `using` (card whose ability is used: ability / break / pump), `installing`, `rezzing`, `playing`,
  `advancing`, `trashing`, and `purpose` (the old purpose strings "play-event", "trash-asset", "advance-ice", "trace", ...).
* **Every credit source declares what it pays for**: `CardImpl.paysFor(self, spend)` (new hook). The older hooks still work:
  `creditsUsableForRun` (anything during a run: Overclock, Concerto, Ghost Runner, Māui on HQ runs...) and
  `creditsUsableFor(self, purpose)` (Scrubber, Paricia, Prepaid VoicePAD, Mystic Maemi). The base classes translate their own
  hooks: `RH.breakerCredits` → `RH.paysFor` (icebreaker / hosted-program credits), `RA.recurring(kind)` → `RA.paysFor`.
* **Bad publicity credits are run credits**: `BadPublicityCreditsEffect` (queued by `su21Triggers` on `RunInitiated`) sets
  `Run.credits = badPublicity`; they are spent like any other source and disappear with the run (the UI run line shows them).
  Spending them fires `BadPublicityCreditsSpent(n)` (Shackleton Grid).
* **Stealth**: `StealthCreditCost(n, stealth)` — at least `stealth` of the n credits from Stealth cards (`stealth == n`:
  "spend credits only from stealth cards"; `1`: "at least 1 credit from a stealth card"). `canPay` / `payCosts` handle it, and
  `SpendEffect(f, n, spend, stealth, then)` pays it inside effect lists (Afterimage, Mu Safecracker, Baker, Fawkes).
* **Restrictions**: `poolLocked(f)` — Aircheck in play: the pool can neither pay nor be lost (`LoseCreditsEffect`);
  `noSpending(f)` — run flag `no-spend-credits` (RSVP): no credits can be spent.
* **API**: `creditsFor(f, spend)`, `canSpend(f, n, spend, stealth)`, `spendCredits(f, n, spend, stealth)`; `canPay(f, costs,
  self, spend)` / `payCosts(f, costs, self, spend)` with an optional Spend (default `Spend.ability(self)`); the old
  `payCredits(f, n, self, purpose)` / `availableCredits(f, self, purpose)` / `creditSourcesFor` delegate (`Spend.legacy`).
  Combined payments ("install and rez it paying n less"): `installParts` / `canSpendAll` / `spendAll` (install part and rez part
  each paid with its own Spend, the discount lowering the install cost first).
* **Costs**: `removeTagCost` (2 + SYNC), `trashResourceCost` (2 + SYNC + 2 per Wireless Net Pavilion); `breakCostsWith` /
  `pumpCostsWith` add the icebreaker surcharges (`CardImpl.icebreakerSurcharge`: Midway Station Grid, Net Watchlist; run flags
  `break-surcharge`: Interrupt 0) to the credit cost; `installCostModIn(self, card, server)` (Network Exchange) is part of
  `installCost`; `hostedInstallCost(c, host)` applies `hostedInstallCostMod` (Hackerspace).
* `runnerTraceBoost`: the Runner's last trace boost (from any source), used by margin traces (Data Hound, Waiver,
  `MarginTraceEffect`) instead of measuring the pool before and after.

### Payment order (no prompt; deterministic)

1. the stealth part of the cost, from Stealth sources;
2. bad publicity credits of the run (they expire with the run);
3. credits hosted on cards in the play area (run events: they leave with the event);
4. recurring credits (they refill at the start of their owner's turn);
5. other hosted credits (Ghost Runner, Open Market, Cybersand Harvester...);
6. the pool.

Within a group: non-stealth cards first, then board order (identity, score area, installed cards, play area). While the Runner
has an active card that needs stealth credits (its text says "spend credits only from stealth cards" or "at least 1 credit from
a stealth card": Dagger, Refractor, Switchblade, Dai V, Houdini, Blackstone, BlacKat, Fawkes, Afterimage, Penrose, Corsair,
Lampades, Baker, Mu Safecracker), stealth credits that a payment does not require are kept and spent only after the pool.
Why no choice prompt: the order spends the shortest-lived and most restricted credits first, so the pool (which pays for
anything) is preserved; the only real trade-off (stealth credits wanted later) is handled by the stealth rule above. A prompt
would have to interrupt payments that happen inside engine actions (rez, install, trace...), which are not asks.

## Cards fixed

Runner credit sources (now spent through the payment engine for exactly what the card says; the non-printed "take hosted
credits" abilities were removed):
02068 Inside Man (install hardware), 25057 Ice Analyzer (install programs), 06098 Cybsoft MacroDrive (install programs; was a
1-credit discount), 25008 Cyberfeeder (icebreakers + virus program installs; was a discount), 05038 Public Terminal (play run
events; was a discount), 03047 Sahasrara (install programs), 09040 Multithreader (using programs), 03041 Cloak (using
icebreakers, stealth), 26088 Mantle (using hardware and programs, stealth), 26009 Trickster Taka (using programs during runs),
26073 Paladin Poemu (install non-connection cards), 35022 Open Market (install connection / job resources; was a discount),
34029 Urban Art Vernissage (install cards), 20034 Crash Space (basic remove-tag action, including SYNC's surcharge),
35029 Azimat (trash costs; was a separate access ability), 34030 Bahia Bands (trash costs of Corp cards during its run),
22014 Miss Bones (trash costs of installed Corp cards), 02001 Whizzard (trash costs of Corp cards), 11046 Net Mercur (anything),
06040 Ghost Runner, 34019 Debbie "Downtown" Moreira (run credits; take ability removed), 02025 Compromised Employee (its trace
credit was not usable at all), and the icebreaker credits 01041 The Toolbox, 04006 Lockpick, 04103 Dyson Fractal Generator,
04104 Silencer, 20007 Spinal Modem, 03039 Omni-drive, 21109 Flame-out, 11066 Ele "Smoke" Scovak (same behaviour, now through
`paysFor` instead of the `rhPayingFor` interception, so they also pay for other abilities of the breaker).

Stealth costs: 03042 Dagger, 06057 Refractor, 06077 Switchblade, 11006 Dai V, 11045 Houdini, 11068 Blackstone, 06053 BlacKat,
11108 Fawkes, 26079 Afterimage, 26089 Penrose, 26076 Mu Safecracker, 36004 Corsair, 36005 Lampades, 36015 Baker (these were
gated on "enough stealth credits exist" but paid from anything), 36018 Aircheck (the pool cannot be spent or lost).

Corp credit sources: 02072 Dedicated Server (rez ice), 10018 Mumba Temple (rez), 06008 The Root (rez, install, advance; the advance
menu no longer needs a pool credit), 02075 Net Police, 06006 Primary Transmission Dish, 25104 NBN: Making News (traces),
02076 Weyland Consortium: Because We Built It (advance ice; was moved to the pool before the advance), 02099 Simone Diego (advance
cards in its server; was a refund), 07026 Dedicated Technician Team (install ice protecting its server; was a refund),
12019 Khondi Plaza (rez ice protecting its server; was a discount), 35082 Mahkota Langit Grid (rez assets / ice of its server;
was a discount), 34058 Cybersand Harvester (install costs; the 1-credit take ability was removed). Rezzes that compute their own
discounted cost (`DiscountRezEffect`, `RezDiscountEffect`, `ServerDiscountChosenAction`, `CorpInstallPayingEffect`, Reanimation
Protocol, Cyber Bureau) now pay through `Spend.rez` / `Spend.install`, so these credits are used there too and no credits are
created: 31040 Haas-Bioroid: Architects of Tomorrow (was a refund of the difference), 36062 Reanimation Protocol, 24001 Cyber Bureau.

Costs: 04077 RSVP (the Runner cannot spend credits for the rest of the run; was "break abilities that cost credits are blocked"),
10016 Interrupt 0 (+1 credit per resolved subroutine to icebreaker break abilities for the run; was "charged after the break"),
06007 Midway Station Grid (+1 to icebreaker break abilities on its server; was charged after the break), 14023 Net Watchlist (+2
to icebreaker break and pump abilities; was a loss after breaking), 08108 Wireless Net Pavilion (Corp's trash-resource action costs
2 more; was not charged), 12007 Network Exchange (+1 install cost for ice not installed innermost; was +1 whenever any ice
existed), 36006 Hackerspace (1 less only for resources installed onto it; was for every unique connection / companion), 21090
Jackpot! (take any number of credits), 14009 Security Leak (its additional advance credit is an advance payment), 02096 Data
Hound / 10091 Waiver (the Runner's boost is the recorded boost, hosted trace credits included), 36065 Shackleton Grid (also fires
on bad publicity credits), 26113 GameNET (now also fires when the Runner pays a Corp card's "pay N" choice during a run).

"Pay N credits" choices of Corp cards (end the run / take a tag / take damage unless the Runner pays) now pay with
`SpendEffect(.., Spend.corpAbility, ..)`, so bad publicity credits and run credits can pay them (19 choices in sg, su21, elev, vp,
tai, df, ur, rot assets, rot ice, and the rot-ice helper `etrUnlessPays`). Femme Fatale's bypass payment, the Conspiracy
breakers' X payment (Paperclip, Black Orchestra, MKUltra) and the Wrestlers' (Laamb, Engolo) 2 credits now pay through the Spend
whose availability they check (they could previously pay less than they checked).

Status entries that were already exact: 33075 Concerto and 34019 Debbie (their text is "spend hosted credits during that run").

## Still approximate (and why)

* No prompt to choose the credit sources (see "Payment order").
* 26113 GameNET: "a Corp card ability causes the Runner to lose credits" — losses (`LoseCreditsEffect`) carry no source, so any
  credit loss of the Runner during a run still counts.
* 25009 Patchwork: still a once-per-turn "prepay" ability (the reduction needs a choice of the grip card in the middle of a
  payment; payments are not asks).
* 35074 Biawak: the "forfeit an agenda to pay 10" alternative is still forced when an agenda is scored (needs a choice before
  the rez is paid).
* Secret spending (Push Your Luck, Rigged Results) uses the pool only (spending hosted credits would reveal part of the amount
  in the log).
* 06039 LLDS Energy Regulator's "pay 3" checks the credits usable for its ability but pays with `PayCreditsEffect` (Spend.other):
  left alone because the prevention pass owns that card.
* A few card-specific pre-checks still look at the pool only (Bling's play/install list, Executive Boot Camp, Councilman); their
  payments use the pool as well, so they are consistent, just stricter than the rules.
* Net Watchlist taxes break and pump abilities only (not other paid abilities of icebreakers).
* Sahasrara's credits can be counted for a program whose installation trashes Sahasrara (the trash happens first, so they are
  not actually spent; the option may be offered and then paid from the pool).
* Armored Servers' grip-card break cost is unchanged (not a credit cost).

## Shared-file edits (for merging)

| File | Edit |
|---|---|
| `game.scala` | `with CreditRules` appended to the `Game` mixins; `Cost.desc` case for `StealthCreditCost`; `canPay` takes `spend : |[Spend] = None` and checks `CreditCost` / `StealthCreditCost` with `canSpend`; `availableCredits` / `creditSourcesFor` delegate to `creditsFor` / `creditSourcesOf`; `installCost` adds `installCostModIn` |
| `abilities.scala` | 4 `CardImpl` hooks with neutral defaults: `paysFor`, `icebreakerSurcharge`, `installCostModIn`, `hostedInstallCostMod` |
| `rules-effects.scala` | `LoseCreditsEffect` loses nothing while the pool is locked; trace boosts (options and payment) through `Spend.trace`, `runnerTraceBoost`; psi bids through `Spend.psi`; `rezCard` pays through `Spend.rez`; `payCosts` takes `spend` and handles `StealthCreditCost`; `payCredits` delegates to `spendCredits` |
| `rules-cards.scala` | `CorpInstallEffect` / `RunnerInstallEffect` pay through `Spend.install` (the Runner's with `hostedInstallCost`) |
| `rules-turn.scala` | play / install / advance / trash-resource / remove-tag / rez / run options greyed with `canSpend` for their Spend; `AdvanceAction`, `TrashResourceAction` (`trashResourceCost`), `RemoveTagAction` (`removeTagCost`), `PlayCardAction` pay through their Spend; the advance menu greys each card it cannot pay for |
| `rules-access.scala` | `PayTrashAction` greying and payment through `Spend.trash(c)` |
| `rules-run.scala` | run costs through `Spend.run`; break / pump options and payments through `Spend.break` / `Spend.pump` with `breakCostsWith` / `pumpCostsWith` |
| `ui.scala` | the run line shows the bad publicity credits of the run |
| `ENGINE.md` | hook table, cost list, Clojure mapping rows, §8.1 recurring credits bullet, new §8.2 |
| `rules-ext-ph-tai.scala` | `DelegatingImpl` forwards the 4 hooks; `installRunnerDiscounted`, `PlayCardFreeEffect`, `InstallFromListDiscountEffect`, `ServerDiscountChosenAction` pay / check through their Spend |
| `rules-ext-su21.scala` | bad publicity trigger queues `BadPublicityCreditsEffect`; `InstallDiscountEffect`; `RezDiscountEffect` pays the discounted cost (no refund) |
| `rules-ext-startup.scala` | `PlayCardEffect`, `DiscountRezEffect`, `DiscountInstallAction`, `DiscountInstallPayAction`, outside-access trash (offer and payment), `Startup.runnerCanInstall` |
| `rules-ext-rwr-df-ur.scala` | `canAffordInstall`, `RunnerInstallPayingEffect`, `CorpInstallPayingEffect` (install and rez parts), `PlayFreeEffect` |
| `rules-ext-rot-hardware.scala` | `RH.paysFor` replaces `creditsUsableForRun` + `rhPayingFor`; the interceptions of break / pump / encounter (breaker credits), BWBI's advance, Making News' trace and SYNC's remove-tag / trash-resource costs were removed (now in the engine); rez surcharges and host install options checked / paid through `Spend.rez` / `Spend.install` |
| `rules-ext-rot-assets.scala` | `RA.paysFor` replaces the `rezCostMod` / `installCostMod` discounts; the `rezCard` override and The Root's install / advance overrides were removed; install-server greying through `canSpend` |
| `rules-ext-rot-agendas.scala` | the `TraceRunnerBoostAction` / `TraceRunnerAction` reimplementation pays through `Spend.trace` and records `runnerTraceBoost` |
| `rules-ext-rot-operations.scala` | `rotInstallTargets` / `RotServerChosenAction` / `RotPlayEffect` through their Spend; `RecordTraceMarginAction` uses `runnerTraceBoost` |
| `rules-ext-rot-events.scala` | Security Leak's extra advance credit (greying and payment) through `Spend.advance` |
| `rules-ext-rot-resources.scala` | `runnerInstallableNow` through `canSpend` |
| `rules-ext-rot-ice.scala` | Formicary's rez offer through `canSpend` |

Card files: the cards listed above, plus pool-only affordability pre-checks of card effects whose payment goes through a
Spend-aware path rewritten to `g.canSpend(f, cost, Spend.x(card))` (31 checks in sg, elev, ms, su21, rwr, tai, rot assets, rot
events, rot hardware, rot ice, rot identities, rot operations, rot programs, rot resources, rot upgrades) and the 19 "pay N
credits" choices. Removed case classes: `OpenMarketPayAction`, `MahkotaPayAction` (cards-elev), `BakerPayAction` (cards-vp),
`RaKhondiPayAction` (cards-rot-upgrades).

## Testing

* `sbt -batch compile` (Scala.js project) green.
* JVM harness: scratchpad `fix-credits-host/` (copy of `integ/host` pointed at this worktree).
  * Scripted checks: `ScriptTest`, `RotIceScript` (211 ice, 633 encounters, 0 failures), `RotAssetScript`, `RotAgendaScript`,
    `RotEventScript`, `RotHardwareScript` (80), `RotOperationScript` (56), `RotProgramScript`, `RotMergeScript` all pass. Two
    checks asserted the old approximations and were updated: the Interrupt 0 pair test now looks for its subroutine message (the
    surcharge is checked in `CreditScript`), and the Net Watchlist check now checks the break / pump surcharge instead of a credit
    loss after the break.
  * New `CreditScript` (scratchpad `fix-credits-host/src/CreditScript.scala`), 92 checks, all passing: stealth costs (Cloak,
    Dagger, Houdini, Blackstone, BlacKat, Fawkes, Afterimage, Penrose, Corsair, Lampades, Baker, Mu Safecracker), keeping stealth
    credits, payment order, bad publicity run credits (run, trash cost, gone after the run), Shackleton Grid, Aircheck, RSVP,
    Interrupt 0, Midway Station Grid, Net Watchlist, install credits (Inside Man, Ice Analyzer, Cybsoft, Sahasrara, Cyberfeeder,
    Paladin Poemu, Open Market, Urban Art), Public Terminal, Multithreader / Mantle / Trickster Taka, Crash Space, trash credits
    (Azimat, Miss Bones, Bahia Bands, Net Mercur), Jackpot!, Hackerspace, Network Exchange, Wireless Net Pavilion, rez credits
    (Dedicated Server, Khondi Plaza, Mahkota, The Root), DTT, Cybersand Harvester, Simone Diego, BWBI, trace credits (Making News,
    Net Police, Compromised Employee, recorded boost), discounted rez without credit creation, GameNET, "pay N" with bad publicity
    credits.
  * Bot games (`MergeHost`, every offered and chosen action round-tripped through the serializer): 300 over all presets + 300
    rotated Corp vs rotated Runner presets + 200 current-set presets, twice (before and after the last changes): 1,600 games,
    0 exceptions, 0 empty asks, 0 serialization mismatches, 0 over-long games. The logs show bad publicity credits (223 spends in
    the final 800 games) and hosted credits of Whizzard, BWBI, The Root, Cezve, Urban Art Vernissage, Prepaid VoicePAD,
    Cyberfeeder, Azimat, Penumbral Toolkit, Overclock, DTT, Public Terminal, Net Celebrity, Mahkota, Dedicated Server, Crash Space,
    Silencer, Paricia, Paladin Poemu, Khondi Plaza, Ghost Runner, Dyson, Cybsoft, Open Market, Miss Bones, Methuselah, Flame-out,
    Fencer Fueno, Ele "Smoke" being spent.
