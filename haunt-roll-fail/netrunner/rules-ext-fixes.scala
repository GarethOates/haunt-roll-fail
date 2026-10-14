package netrunner
//
//
//
//
import hrf.colmat._
import hrf.logger._
//
//
//
//

import hrf.elem._
import netrunner.elem._


// ---------------------------------------------------------------------------------------------------------------------
// Rules accuracy fixes (card-specific approximations replaced by the exact jinteki behaviour).
// Generic additions live here; the cards keep their own logic in their files. See STATUS-fix-misc.md.
//
// Mixin position (game.scala): `... with CreditRules with RestrictionRules with FixRules with PreventionRules`. PreventionRules is
// outside FixRules: its windows / interrupts (Patchwork, Tyr's Hand's break window, the jack-out window) suspend an action and
// perform it again through performInternal afterwards, so the bookkeeping of FixRules (ClicksSpent, programs used, Mind Game's
// jack-out cost) runs once, when the action really happens. Every override delegates what it does not handle to `super`.
// ---------------------------------------------------------------------------------------------------------------------

// events: an agenda counter spent by the Corp (Mark Yale); clicks spent on an action / ability (Jeeves Model Bioroids, Sundew)
case class AgendaCounterSpent(card : Card, n : Int) extends Event
case class ClicksSpent(f : Faction, n : Int, cause : String) extends Event
// a breach (normal or outside a run) ended; stoleOrTrashed: an accessed card was stolen or trashed during it (Aumakua)
case class BreachEnded(server : Server, stoleOrTrashed : Boolean) extends Event

// marks the card whose ability is resolving (credits gained "through an ability on X": The Zwicky Group)
case class FxSourceEffect(card : Card, on : Boolean) extends ForcedAction
// f loses n clicks when their next turn begins (MCA Austerity Policy)
case class FxLoseClicksAtTurnBeginsEffect(f : Faction, n : Int) extends ForcedAction
// jack out after paying Mind Game's additional cost
case class FxJackOutPaidAction(f : Faction) extends ForcedAction
// the Corp is considered to have n more bad publicity (Corporate Scandal): not "taken", cannot be removed
case class FxAdditionalBadPubEffect(n : Int) extends ForcedAction


trait FixRules extends TurnRules with RunRules with AccessRules with EffectRules with AbilityRules with SU21Rules with PHTAIRules with RWRRules { game : Game =>

    // the Runner's link strength in the last trace (link, or 0 when treated as 0, plus the boost): Data Hound, Waiver, margins
    var fxTraceLinkStrength = 0

    // lingering hand size modifiers that outlast the card that created them (Gyri Labyrinth -2, Valley Grid -1: until the
    // Corp's next turn begins)
    def fxHandSizeMod(f : Faction) : Int = f.isRunner.??(-2 * flagsOf("gyri").num - flagsOf("rot-valley").num)

    // RSVP: the Runner cannot spend credits for the remainder of the run (run flag "no-spend-credits", enforced on every payment by
    // CreditRules.noSpending; the options below are greyed out so that the prompts show it)
    def fxNoCredits : Boolean = noSpending(Runner) && run.exists(r => !r.ended)

    def fxCostsCredits(l : $[Cost]) : Boolean = l.exists {
        case CreditCost(n) => n > 0
        case _ => false
    }

    def fxEffectsSpend(l : $[ForcedAction]) : Boolean = l.exists {
        case LoseCreditsEffect(Runner, n) => n > 0
        case PayCreditsEffect(Runner, n) => n > 0
        case _ => false
    }

    val fxRsvpReason = "cannot spend credits (RSVP)"

    // the Runner's options that spend credits are unavailable while RSVP's restriction lasts
    def fxRsvpOption(a : UserAction) : UserAction = a match {
        case UnavailableReasonAction(x : UserAction, _) if fxRsvpBlocks(x) => UnavailableReasonAction(x.asInstanceOf[Choice], fxRsvpReason)
        case x if fxRsvpBlocks(x) => x.!(true, fxRsvpReason)
        case x => x
    }

    def fxRsvpBlocks(a : UserAction) : Boolean = a match {
        case BreakSubAction(Runner, b, bi, _, _) => impl(b).breaks(b).lift(bi).exists(br => fxCostsCredits(br.costs))
        case BreakSubsAction(Runner, b, bi, _, l) => impl(b).breaks(b).lift(bi).exists(br => fxCostsCredits(breakCosts(br, l.num)))
        case PumpAction(Runner, b, pi) => impl(b).pumps(b).lift(pi).exists(p => fxCostsCredits(p.costs))
        case UseAbilityAction(Runner, c, i, _) => impl(c).abilities(c).lift(i).exists(x => fxCostsCredits(x.costs) || fxEffectsSpend(x.effects))
        case UseRunnerAbilityAction(Runner, c, i, _) => impl(c).runnerAbilities(c).lift(i).exists(x => fxCostsCredits(x.costs) || fxEffectsSpend(x.effects))
        case PayTrashAction(Runner, c) => trashCost(c).exists(_ > 0)
        case StealAction(Runner, c) => fxCostsCredits(stealCostOf(c))
        case PreventUseAction(Runner, c, i, kind, n) => impl(c).preventions(c, kind, n).lift(i).exists(x => fxCostsCredits(x.costs))
        case _ => false
    }

    def fxRsvpAsk(c : Continue) : Continue = if (!fxNoCredits) c else c match {
        case Ask(Runner, l) => Ask(Runner, l./(fxRsvpOption))
        case MultiAsk(asks, p) => MultiAsk(asks./(a => (a.faction == Runner).?(Ask(Runner, a.actions./(fxRsvpOption))).|(a)), p)
        case c => c
    }

    override def abilityOptions(f : Faction, ctx : String, allowClick : Boolean) : $[UserAction] = {
        val l = super.abilityOptions(f, ctx, allowClick)
        (f.isRunner && fxNoCredits).?(l./(fxRsvpOption)).|(l)
    }

    // Excalibur / Uroboros: no other run this turn, while the turn lasts (even if the ice is no longer rezzed)
    override def canRunServer(s : Server) : Boolean = super.canRunServer(s) && !hasFlag("no-run")

    // Mind Game: an installed card to the bottom of the stack as an additional cost to jack out (remainder of the run)
    def fxMindGameJackOut : Boolean = hasFlag("mind-game-jack-out") && run.exists(r => !r.ended)

    override def windowOptions(f : Faction, kind : String) : $[UserAction] = {
        val l = super.windowOptions(f, kind)
        if (f.isRunner && fxMindGameJackOut && installedCards(Runner).none)
            l./{
                case a @ JackOutAction(_) => a.!(true, "cannot pay the additional cost")
                case a => a
            }
        else
            l
    }

    private def fxBaseAbilities(a : Action) : Continue = super.performAbilities(a)

    override def performAbilities : PartialFunction[Action, Continue] = ({
        case a @ UseAbilityAction(Runner, c, _, _) if run.any && c.data.isProgram =>
            fxUsedThisRun += c
            fxBaseAbilities(a)

        case JackOutAction(f) if fxMindGameJackOut =>
            if (installedCards(Runner).none) {
                log(Runner, "cannot pay the additional cost to jack out")
                NextAction
            }
            else {
                push(ChooseCardEffect(Runner, installedCards(Runner), BottomOfDeckPurpose(Runner), "Jack out (Mind Game): choose an installed card to add to the bottom of the stack", false), FxJackOutPaidAction(f))
                NextAction
            }
    } : PartialFunction[Action, Continue]) orElse super.performAbilities

    override def performAccess : PartialFunction[Action, Continue] = {
        val base = super.performAccess
        ({
            // GameNET: credits paid for an additional steal cost imposed by a Corp card are credits spent because of it
            case a @ StealAction(f, c) if run.exists(r => !r.ended) =>
                val n = stealCostOf(c)./{ case CreditCost(k) => k ; case _ => 0 }.sum
                val res = base(a)
                if (n > 0)
                    trigger(CreditsPaid(Runner, n, Spend.corpAbility))
                res

            // an installed card accessed (and not trashed or stolen) stays known to the Runner
            case a @ AccessCardAction(c) if isInstalled(c) && owner(c).isCorp =>
                fxKnown += c -> (zone(c), state(c).installedTurn)
                base(a)

            // "instead of breaching": the replacement effect was already logged
            case BreachAction(s) if run.exists(r => !r.ended && r.preventAccess) =>
                push(RunEndsAction)
                NextAction

            // Archives breached: the cards turned faceup are put in a neutral order (by title) so that their position does not
            // tell when each was trashed
            case a @ AccessServerAction(Archives) if run.exists(r => !r.ended) && discard(Corp).exists(c => !state(c).seen) =>
                val faceup = discard(Corp).%(c => state(c).seen)
                val flipped = discard(Corp).%(c => !state(c).seen)
                zones += DiscardZone(Corp) -> (faceup ++ flipped.sortBy(_.title))
                base(a)

            // the end of a breach (Aumakua); a run whose breach was replaced never breached
            case a @ BreachEndAction if run.exists(r => fxBreachRuns.exists(_ eq r)) =>
                val ev = run./(r => BreachEnded(r.breachServer.|(r.server), r.didSteal || r.didTrash))
                fxBreachRuns = fxBreachRuns.%!(x => run.exists(_ eq x))
                val res = base(a)
                ev.foreach(trigger)
                res

            case a @ BreachAction(_) if run.exists(r => !r.ended && !r.preventAccess) =>
                val res = base(a)
                run.foreach(r => fxBreachRuns :+= r)
                res

            case a @ AccessDecisionAction(_) if fxNoCredits => fxRsvpAsk(base(a))
        } : PartialFunction[Action, Continue]) orElse base
    }

    // which card broke each subroutine of an ice during the current encounter with it (jinteki :breaker)
    var fxBreaker : Map[(Card, Int), Card] = Map()

    // programs used during the current run (break / pump / paid ability: Cold Read)
    var fxUsedThisRun : Set[Card] = Set()

    // additional bad publicity (Corporate Scandal), included in badPublicity
    var fxAdditionalBadPub = 0

    // clicks spent this turn by cause (basic action kind or card ability)
    var fxClicksByCause : Map[(Faction, String), Int] = Map()
    var fxDoubleSpend = false

    // agenda counters paid as a cost
    override def payCosts(f : Faction, costs : $[Cost], self : |[Card], spend : |[Spend] = None) {
        super.payCosts(f, costs, self, spend)
        if (f.isCorp)
            costs.foreach {
                case CounterCost("agenda", n) if n > 0 => self.foreach(c => trigger(AgendaCounterSpent(c, n)))
                case _ =>
            }
    }

    // memory that can only be used by some programs (MemStrips / Knobkierie: virus, Deep Red: Caissa; +3 each): the part not used
    // yet is free for such a program even when the general memory is full
    def fxRestrictedFreeMu(c : Card) : Int = {
        def caissa(x : Card) = has(x, "Caïssa") || has(x, "Caissa")
        def free(code : String, ok : Card => Boolean) = activeCards(Runner).%(x => x.code == code && !isDisabled(x))./(_ => (3 - installedCards(Runner).%(x => x.data.isProgram && ok(x))./(_.data.memory.|(0)).sum.atMost(3)).atLeast(0)).sum
        (has(c, "Virus").??(free("07046", x => has(x, "Virus")) + free("21062", x => has(x, "Virus")))) + caissa(c).??(free("04042", caissa))
    }

    // Street Magic: the order in which the unbroken subroutines of an ice resolve
    var fxSubOrder : Map[Card, $[Int]] = Map()

    // the Runner is resolving an ability or event (the source of a derez: Keros Mcintyre)
    def fxRunnerResolving : Boolean = fxSources.starting.exists(c => owner(c).isRunner) || at(PlayZone(Runner)).exists(c => pending.has(PlayCleanupAction(c)))

    // Direct Access: while it resolves (its run), both identities lose all abilities
    def fxDirectAccess : Boolean = run.exists(r => !r.ended && r.source.exists(c => c.code == "26028" && zone(c) == PlayZone(Runner)))

    override def isDisabled(c : Card) : Boolean = super.isDisabled(c) || (fxDirectAccess && zone(c).is[IdentityZone])

    override def extImpl(c : Card) : CardImpl =
        if (fxDirectAccess && zone(c).is[IdentityZone]) new DisabledImpl(CardImpls(c.code), false) else super.extImpl(c)

    // runs (normal or pseudo) that are breaching a server, as opposed to accessing a few cards outside a breach
    var fxBreachRuns : $[Run] = $

    // Paparazzi: the Runner is tagged
    def fxConsideredTagged : Boolean = rig.exists(c => c.code == "08087" && isActive(c)) && !rig.exists(c => c.code == "08087" && isDisabled(c))

    // installed Corp cards the Runner accessed and that stayed where they were (shown faceup to the Runner by the UI; not
    // "seen": a card trashed later from there still goes to Archives facedown)
    var fxKnown : Map[Card, (Zone, Int)] = Map()

    def fxRunnerKnows(c : Card) : Boolean = fxKnown.get(c).exists { case (z, t) => zone(c) == z && state(c).installedTurn == t }

    // clicks lost when the faction's next turn begins
    var fxTurnBeginsClickLoss : Map[Faction, Int] = Map()

    // subroutines that do not resolve during the current encounter (Mass-Driver: not broken, jinteki dont-resolve-subroutine)
    var fxNoResolve : Set[(Card, Int)] = Set()

    // breakers restricted to some subroutines (Endless Hunger: only "End the run." subroutines)
    def fxSubBreakable(b : Card, ice : Card, i : Int) : Boolean = b.code match {
        case "09033" => subs(ice).lift(i).exists(s => s.label.trim.stripSuffix(".").equalsIgnoreCase("End the run"))
        case _ => true
    }

    def fxFilterBreaks(c : Continue) : Continue = {
        def f(l : $[UserAction]) : $[UserAction] = l.%{
            case BreakSubAction(Runner, b, _, ice, i) => fxSubBreakable(b, ice, i)
            case UnavailableReasonAction(BreakSubAction(Runner, b, _, ice, i), _) => fxSubBreakable(b, ice, i)
            case _ => true
        }
        c match {
            case Ask(f0, l) => Ask(f0, f(l))
            case MultiAsk(asks, p) => MultiAsk(asks./(a => Ask(a.faction, f(a.actions))), p)
            case c => c
        }
    }

    // ice whose subroutines are resolving (innermost first): PreventionRules.preventSubs (one stack for both passes)
    def fxResolvingSubs : $[Card] = preventSubs

    def fxBreakerOf(ice : Card, i : Int) : |[Card] = fxBreaker.get((ice, i))

    // all subroutines of the ice are broken, all of them by b; l = the subroutines broken by the event being handled
    // (the cards see SubsBroken before FixRules records it)
    def fxFullyBroke(ice : Card, l : $[Int], b : Card) : Boolean = {
        val n = subs(ice).num
        n > 0 && unbrokenSubs(ice).none && 0.until(n).forall(i => l.has(i) || fxBreakerOf(ice, i).has(b))
    }

    // the same, after the event: every subroutine was broken by b
    def fxAllBrokenBy(ice : Card, b : Card) : Boolean = fxFullyBroke(ice, $, b)

    // positions of the printed subroutines in subs(ice) (other cards may add subroutines before or after them)
    def fxPrintedSubIndices(ice : Card) : $[Int] = {
        val all = subs(ice)
        val printed = impl(ice).subroutines(ice)
        val k = all.indexOfSlice(printed)
        (k >= 0).??(k.until(k + printed.num).$)
    }

    // Anvil: the printed subroutines cannot be broken this encounter
    override def canBreakSub(ice : Card, l : $[Int]) : Boolean =
        super.canBreakSub(ice, l) && !(hasFlag("cannot-break-printed", ice) && l.exists(i => fxPrintedSubIndices(ice).has(i)))

    // the Corp's agenda point threshold lowered by Issuaq Adaptics' power counters
    override def checkWin() : Continue = {
        val lower = activeCards(Corp).%(_.code == "33104")./(c => counter(c, "power")).sum
        // (The Black File is RestrictionRules.restrictContinue: it turns any Corp win other than by flatline into "continue")
        if (!isOver && lower > 0 && agendaPoints(Corp) >= pointsToWin - lower)
            GameOverAction(|(Corp), "Corp scored " + agendaPoints(Corp) + " agenda points (Issuaq Adaptics)")
        else
            super.checkWin()
    }

    // Nanuq: if it would move from installed to anywhere other than the removed-from-game zone (not being shuffled), it is
    // removed from the game instead
    def fxNanuq(c : Card) : Boolean = c.code == "33088" && isInstalled(c) && !state(c).facedown && !isDisabled(c)

    // ice passed during the current run and during the last run (one entry per PassIce event: Into the Depths, En Passant)
    var fxPassedThisRun : $[Card] = $
    var fxPassedLastRun : $[Card] = $

    // subtypes granted to a card by the cards hosted on it (Egret)
    def fxHostedSubtypes(c : Card) : $[String] =
        if (state(c).hosted.none) $ else state(c).hosted.%(h => isActive(h))./~(h => CardImpls(h.code).hostSubtypes(h)(this))

    // Runner cards trashed from the grip by card effects get their "when trashed" hooks (Steelskin Scarring); damage and mill
    // discards already do (rules-ext-ph-tai.scala); discarding to the hand size is not a trash
    def fxGripTrashed(l : $[Card]) {
        l.%(c => owner(c).isRunner && zone(c) == DiscardZone(Runner)).foreach(c => pushAll(impl(c).onTrash(c, Runner)))
    }

    // rezCard was asked to ignore all costs (read by on-rez abilities that are additional costs: Biawak)
    var fxRezIgnoredCost = false

    // cards whose abilities are resolving (innermost first)
    var fxSources : $[Card] = $

    override def rezCard(c : Card, ignoreCost : Boolean) {
        val old = fxRezIgnoredCost
        fxRezIgnoredCost = ignoreCost
        super.rezCard(c, ignoreCost)
        fxRezIgnoredCost = old
    }

    // subroutines granted for one encounter by lingering effects (Stick and Poke: at the front)
    override def subs(ice : Card) : $[Sub] = {
        val l = super.subs(ice)
        // Wetwork Refit: "[subroutine] Do 1 core damage" before all the other subroutines of its host
        val wetwork = isRezzed(ice).??(at(PlayZone(Corp)).%(w => w.code == "11071" && state(w).target.has(ice))./(_ => Sub("Do 1 core damage (Wetwork Refit)", DamageEffect("core", 1))))
        if (isRezzed(ice) && hasFlag("stick-poke", ice))
            Sub("[Stick] Do 1 net damage. The Runner draws 1 card.", SeqEffect($(DamageEffect("net", 1), DrawEffect(Runner, 1)))) +: (wetwork ++ l)
        else
            wetwork ++ l
    }

    // paid abilities: the card is the source of what its effects do
    override def useAbility(f : Faction, c : Card, a : Ability, ctx : String, costCards : $[Card]) : Continue = {
        val r = super.useAbility(f, c, a.copy(effects = (FxSourceEffect(c, true) +: a.effects) :+ FxSourceEffect(c, false)), ctx, costCards)
        val clickCost = a.costs.of[ClickCost]./(_.n).sum
        val loseCost = a.costs.of[LoseClickCost]./(_.n).sum
        // a [click] ability is an action; a bioroid break ("lose [click]") is not
        if (clickCost + loseCost > 0)
            trigger(ClicksSpent(f, clickCost + loseCost, (clickCost > 0).?("ability-").|("lose-click-") + c.code + "-" + c.n + "-" + a.label.filter(_ < 128)))
        r
    }

    // the source of a credit gain: a resolving ability, a resolving operation / event, an agenda's "when scored" ability
    def fxCreditSource(f : Faction) : |[Card] =
        fxSources.starting || at(PlayZone(f)).%(c => pending.has(PlayCleanupAction(c))).starting || scoringAgenda

    // scoring (TurnRules.scoreAgenda with the additional cost of Word on the Street paid before the agenda is scored, and the
    // agenda marked as the source of its "when scored" abilities)
    override def scoreAgenda(c : Card) {
        val installedThisTurn = state(c).installedTurn == turn
        if (installedThisTurn)
            installedCards(Runner).%(x => x.code == "36025" && isActive(x)).foreach { w =>
                uninstall(w)
                moveTo(w, ScoredZone(Corp))
                log(Corp, "adds", w, "to the score area as an agenda worth -1 agenda point (additional cost to score)")
            }
        else
            regInc("fx-scored-not-installed-this-turn")
        // the agenda's value with its modifiers (RestrictionRules.agendaValue), read once it is in the score area
        lazy val points = agendaValue(c)
        lastScoredFrom = serverOf(c)
        lastScoredAdvancement = advancement(c) ; lastScoredInstalledTurn = state(c).installedTurn ; scoringAgenda = |(c)
        val hostedTrashed = leavePlay(c)
        moveTo(c, ScoredZone(Corp))
        state(c).seen = true
        hostedTrashed.foreach(h => log("Hosted card", cardElem(h), "is trashed"))
        cleanupRemotes()
        scoredPointsThisTurn += points
        regInc("scored-points", points)
        regInc("scored")
        log(Corp, "scores", c, "and gains", points.hl, "agenda point" + (points != 1).??("s"))
        val l = impl(c).onScore(c)
        pushAll(l.any.??((FxSourceEffect(c, true) +: l) :+ FxSourceEffect(c, false)))
        trigger(AgendaScored(c))
        push(CheckWinAction)
    }

    override def performRun : PartialFunction[Action, Continue] = {
        val base = super.performRun
        ({
            // break restrictions of some breakers (Endless Hunger) and RSVP
            case a @ EncounterAction(_) => fxRsvpAsk(fxFilterBreaks(base(a)))

            // programs used during the run (Cold Read)
            case a @ BreakSubAction(_, b, _, _, _) =>
                fxUsedThisRun += b
                base(a)
            case a @ BreakSubsAction(_, b, _, _, _) =>
                fxUsedThisRun += b
                base(a)
            case a @ PumpAction(_, b, _) =>
                fxUsedThisRun += b
                base(a)

            // breaking with a bioroid's click ability is breaking subroutines: the same event as an icebreaker's break
            // (the ice itself is given as the breaker)
            case a @ BioroidBreakSubAction(f, c, i) =>
                val r = base(a)
                trigger(SubsBroken(c, $(i), c))
                r

            // Street Magic: the unbroken subroutines resolve in the chosen order
            case ResolveSubroutinesAction(c) if fxSubOrder.get(c).exists(_.any) =>
                val u = unbrokenSubs(c)
                val order = fxSubOrder(c).%(u.has) ++ u.%!(fxSubOrder(c).has)
                pushAll(order./(i => FireSubAction(c, i)) :+ EncounterEndsAction(c))
                NextAction

            // Mass-Driver: this subroutine does not resolve
            case FireSubAction(c, i) if fxNoResolve.contains((c, i)) && run.exists(r => !r.ended) =>
                log("The subroutine", subs(c).lift(i)./(_.label).|("?").hl, "of", c, "does not resolve")
                NextAction

            // Tracker: the first subroutine that would resolve during the run is prevented
            case FireSubAction(c, i) if hasFlag("fx-tracker") && run.exists(r => !r.ended) && subs(c).lift(i).any =>
                removeFlags("fx-tracker", None)
                log("Tracker prevents", subs(c).lift(i)./(_.label).|("?").hl, "on", c, "from resolving")
                NextAction

        } : PartialFunction[Action, Continue]) orElse base
    }

    override def performEffects : PartialFunction[Action, Continue] = {
        val base = super.performEffects
        ({
            // Pichacao: "the first time you gained [click] during a run this turn"
            case a @ GainClicksEffect(f, n) if run.exists(r => !r.ended && !r.accessOnly) && n > 0 =>
                regInc("fx-run-click-gain")
                base(a)

            case AddToHandEffect(c) if fxNanuq(c) => base(RemoveFromGameEffect(c))
            case MoveEffect(c, z, _) if fxNanuq(c) && !z.is[RemovedZone] => base(RemoveFromGameEffect(c))

            // agenda counters removed from Corp cards are spent (Mark Yale)
            case a @ AddCounterEffect(c, "agenda", n) if n < 0 && owner(c).isCorp && counter(c, "agenda") > 0 =>
                val m = (-n).atMost(counter(c, "agenda"))
                val r = base(a)
                trigger(AgendaCounterSpent(c, m))
                r

            // the additional click of a double operation / event is spent on playing it
            case a @ RegisterEffect("double", 1) =>
                fxDoubleSpend = true
                base(a)
            case a @ LoseClicksEffect(f, 1) if fxDoubleSpend && clicks(f) > 0 =>
                fxDoubleSpend = false
                val r = base(a)
                trigger(ClicksSpent(f, 1, "play"))
                r
            // losing clicks (Heinlein Grid, Adjusted Chronotype)
            case a @ LoseClicksEffect(f, n) if n > 0 && clicks(f) > 0 =>
                val m = n.atMost(clicks(f))
                val r = base(a)
                trigger(ClickLost(f, m))
                r

            // bad publicity can be removed only down to the additional (Corporate Scandal) bad publicity
            case BadPubEffect(n) if n < 0 && badPublicity + n < fxAdditionalBadPub =>
                val m = (badPublicity - fxAdditionalBadPub).atLeast(0)
                if (m > 0) base(BadPubEffect(-m)) else NextAction

            // RSVP: no boost, no bid, no "pay or ..." choice
            case TraceRunnerBoostAction(strength, s, fl) if fxNoCredits =>
                Ask(Runner).group("Trace strength " ~ strength.hl ~ " (the Runner cannot spend credits)").add(TraceRunnerAction(Runner, 0))
            case PsiRunnerBidAction(c, eq, df) if fxNoCredits =>
                Ask(Runner).group("Psi game (the Runner cannot spend credits)").add(PsiBidAction(Runner, 0))
            case ChooseOneEffect(Runner, prompt, l) if fxNoCredits && l.exists(x => x.ok && fxEffectsSpend(x.effects)) =>
                base(ChooseOneEffect(Runner, prompt, l./(x => fxEffectsSpend(x.effects).?(x.copy(ok = false)).|(x))))

            // record the Runner's link strength (before the agendas port resolves the trace: link treated as 0 for Net Quarantine)
            case a @ TraceRunnerAction(f, n) =>
                fxTraceLinkStrength = (rotLinkZero && reg("rot-traces") <= 1).?(0).|(link) + n
                base(a)

            // Lockdown: the Runner cannot draw cards for the remainder of the turn
            case DrawNowEffect(Runner, n) if n > 0 && hasFlag("no-draw") =>
                log("The Runner cannot draw cards this turn")
                NextAction

            case a @ DiscardFromHandEffect(Runner, c) =>
                val r = base(a)
                fxGripTrashed($(c))
                r

            // the source of the credit gain (The Zwicky Group)
            case GainCreditsEffect(f, n) =>
                if (n > 0) {
                    credits += f -> (credits(f) + n)
                    reopenWindows()
                    log(f, "gains", n.credits)
                    trigger(CreditsGained(f, n, fxCreditSource(f)))
                }
                NextAction
        } : PartialFunction[Action, Continue]) orElse base
    }

    override def performSU21 : PartialFunction[Action, Continue] = {
        val base = super.performSU21
        ({
            case a @ BreachServerEffect(_) =>
                val res = base(a)
                run.foreach(r => fxBreachRuns :+= r)
                res

            case a @ RandomTrashedAction(Runner, l) =>
                val r = base(a)
                fxGripTrashed(l)
                r
        } : PartialFunction[Action, Continue]) orElse base
    }

    // bypassing an ice that is already being bypassed does not log / trigger a second time
    override def performPHTAI : PartialFunction[Action, Continue] = {
        val base = super.performPHTAI
        ({
            case BypassIceEffect(ice) if run.exists(r => r.bypass && r.ice.has(ice)) => NextAction
        } : PartialFunction[Action, Continue]) orElse base
    }

    override def performRWR : PartialFunction[Action, Continue] = {
        val base = super.performRWR
        ({
            case ReturnToHandEffect(c) if fxNanuq(c) => performEffects(RemoveFromGameEffect(c))
            // terminal: the action phase ends (Wage Workers has no payoff after it)
            case a @ EndActionPhaseEffect =>
                if (active.isCorp)
                    regInc("fx-terminal")
                base(a)
        } : PartialFunction[Action, Continue]) orElse base
    }

    override def performTurn : PartialFunction[Action, Continue] = {
        val base = super.performTurn
        ({
            // clicks spent on basic actions
            case a @ ClickCreditAction(f) => fxSpent(f, 1, "credit", base(a))
            case a @ ClickDrawAction(f) => fxSpent(f, 1, "draw", base(a))
            case a @ PlayCardAction(f, _) => fxSpent(f, 1, "play", base(a))
            case a @ CorpInstallAction(f, _, _) => fxSpent(f, 1, "install", base(a))
            case a @ RunnerInstallAction(f, _, _, _) => fxSpent(f, 1, "install", base(a))
            case a @ AdvanceAction(f, _) => fxSpent(f, 1, "advance", base(a))
            case a @ TrashResourceAction(f, _) => fxSpent(f, 1, "trash-resource", base(a))
            case a @ PurgeAction(f) => fxSpent(f, 3, "purge", base(a))
            case a @ RunAction(f, _) => fxSpent(f, 1, "run", base(a))
            case a @ RemoveTagAction(f) => fxSpent(f, 1, "remove-tag", base(a))

            // a program that fits in restricted memory can be installed without trashing other programs
            case a @ InstallWhereAction(Runner, c) if c.data.isProgram && !impl(c).mustBeHosted(c) && memoryFree < c.data.memory.|(0) && memoryFree + fxRestrictedFreeMu(c) >= c.data.memory.|(0) =>
                base(a) match {
                    case Ask(f, l) if !l.exists { case RunnerInstallAction(_, _, None, None) => true ; case _ => false } =>
                        Ask(f, RunnerInstallAction(Runner, c, None, None).!(!canSpend(Runner, installCost(c, None), Spend.install(c)), "not enough credits") +: l)
                    case r => r
                }

            // static allotted clicks (Basilar Synthgland, Director Haas, Victoria Jenkins)
            case a @ StartTurnAction(f) =>
                val n = (identity.values.$ ++ scored(Corp) ++ scored(Runner) ++ installedCards(Corp) ++ installedCards(Runner)).%(isActive).%!(isDisabled)./(c => impl(c).clicksPerTurn(c, f)).sum
                if (n != 0)
                    extraClicks += f -> (extraClicks.get(f).|(0) + n)
                base(a)
        } : PartialFunction[Action, Continue]) orElse base
    }

    // the event is triggered once the action has done its own bookkeeping (its follow-up is already queued)
    def fxSpent(f : Faction, n : Int, cause : String, c : Continue) : Continue = {
        trigger(ClicksSpent(f, n, cause))
        c
    }

    override def resolveRWRPurpose(f : Faction, l : $[Card], p : Purpose) : |[Continue] = p match {
        case TrashFromHandPurpose(Runner) =>
            val r = super.resolveRWRPurpose(f, l, p)
            fxGripTrashed(l)
            r
        case _ => super.resolveRWRPurpose(f, l, p)
    }

    def performFixes : PartialFunction[Action, Continue] = {
        case FxLoseClicksAtTurnBeginsEffect(f, n) =>
            fxTurnBeginsClickLoss += f -> (fxTurnBeginsClickLoss.get(f).|(0) + n)
            log(f, "will lose", n.clicks, "when the next turn begins")
            NextAction

        case FxAdditionalBadPubEffect(n) =>
            val m = n.atLeast(-fxAdditionalBadPub)
            fxAdditionalBadPub += m
            badPublicity = (badPublicity + m).atLeast(0)
            if (m > 0)
                log("The Corp is considered to have", m.hl, "additional bad publicity (Corporate Scandal)")
            else if (m < 0)
                log("The Corp no longer has", (-m).hl, "additional bad publicity (Corporate Scandal)")
            NextAction

        case FxJackOutPaidAction(f) =>
            fxBaseAbilities(JackOutAction(f))

        case FxSourceEffect(c, on) =>
            if (on)
                fxSources = c +: fxSources
            else
                fxSources = fxSources.diff($(c))
            NextAction
    }

    // game-level bookkeeping, evaluated after the cards saw the event (reg(k) == 0 = the first time this turn)
    def fixTriggers(e : Event) : $[ForcedAction] = fxEkomind(e) ++ fixTriggers1(e)

    // Ekomind: the memory limit (the grip size) is checked whenever it changes, not only when a card leaves play
    def fxEkomind(e : Event) : $[ForcedAction] =
        (rig.exists(c => c.code == "06093" && isActive(c)) && memoryFree < 0 && !pending.has(CheckMemoryAction)).$(CheckMemoryAction)

    def fixTriggers1(e : Event) : $[ForcedAction] = e match {
        case EncounterIce(ice) =>
            regInc("fx-encounters")
            fxBreaker = fxBreaker.filter { case ((i, _), _) => i != ice }
            fxNoResolve = fxNoResolve.filter { case (i, _) => i != ice }
            fxSubOrder -= ice
            $
        case EncounterEnds(ice) =>
            fxNoResolve = fxNoResolve.filter { case (i, _) => i != ice }
            $
        case SubsBroken(ice, l, b) =>
            l.foreach(i => fxBreaker += (ice, i) -> b)
            $
        case RunInitiated(_) =>
            fxPassedThisRun = $
            fxUsedThisRun = Set()
            $
        case ClicksSpent(f, n, cause) =>
            fxClicksByCause += (f, cause) -> (fxClicksByCause.get((f, cause)).|(0) + n)
            regInc("fx-clicks-spent-" + f.name, n)
            regInc("fx-click-spend-events-" + f.name)
            $
        case TurnEnds(_) =>
            fxClicksByCause = Map()
            fxDoubleSpend = false
            $
        case TurnBegins(f) if fxTurnBeginsClickLoss.get(f).exists(_ > 0) =>
            val n = fxTurnBeginsClickLoss(f)
            fxTurnBeginsClickLoss -= f
            $(LoseClicksEffect(f, n))
        case PassIce(c) =>
            fxPassedThisRun :+= c
            $
        case RunEnds(_, _, _) =>
            fxBreachRuns = $
            fxNoResolve = Set()
            fxPassedLastRun = fxPassedThisRun
            fxPassedThisRun = $
            $
        case _ => $
    }
}
