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
// Final rules-accuracy pass for the current (NSG-era) sets. See STATUS-fix-final-nsg.md and ENGINE.md section 8.7.
//
// FinalNsgRules is the outermost mixin of Game (`... with FixRules with PreventionRules with FinalNsgRules`) and its own effects
// and interceptions are the second partial function of the performInternal chain (`performRestrictions orElse performFinalNsg
// orElse ...`): illegal actions are refused first, then this trait may suspend a user action (to ask which credit sources pay,
// to pay an additional cost first) and performs it again through performInternal, so every other rule (Patchwork, Tyr's Hand,
// FixRules' bookkeeping, the card-set rules) sees the action once, when it really happens.
//
//   * credit sources: when two or more restricted / hosted credit sources (hosted credits, bad publicity credits of the run) could
//     pay for an action, the player chooses where the credits come from (`CreditAutoAction` = the deterministic default order of
//     CreditRules, preselected first and the only option bots take; `CreditTakeAction` = pay m credits from a source). The choice
//     becomes a plan applied by `spendCredits` to the matching payment.
//   * sources of effects: the effects of card triggers, "when encountered" / "when accessed" / "when rezzed" abilities, paid
//     abilities and prevention abilities are wrapped in FixRules' `FxSourceEffect` markers; `effectSource` is the card whose ability is
//     resolving. Credit losses fire `CreditsLostFrom(f, n, source)` (GameNET).
//   * additional costs paid before the action: rez (`FnsExtraRezCost`: Corporate Town, Archer, Plutus, Bloop, Biawak), score
//     (`FnsExtraScoreCost`: Azef Protocol), the Corp's basic "trash a resource" action (Sebastiao Souza Pessoa, Manuel Lattes de
//     Moura), steal and trash costs paid through the queue (`ExtCost`: Daniela Jorge Inacio's random grip cards).
//   * a "trace initiated" window: `TraceInitiated(base)` is triggered and a prevention-style window ("trace") lets both players use
//     "when a trace is initiated" abilities (Flip Switch, Disrupter) before the Corp boosts.
//   * root moves and swaps re-check the root limits (one asset / agenda per remote root, one region per server, printed server
//     restrictions, no asset / agenda in a central root).
//   * break abilities that break several subroutines offer a soft multi-select of the subroutines to break (Matryoshka: any X).
//   * "spend [click]" choices are click costs (`SpendClicksEffect`), not click losses.
// ---------------------------------------------------------------------------------------------------------------------

// ---- credit sources -------------------------------------------------------------------------------------------------
trait CreditSrc extends Record
case class CardCreditSrc(card : Card) extends CreditSrc
case object PoolCreditSrc extends CreditSrc
case object BadPubCreditSrc extends CreditSrc

case class CreditAutoAction(self : Faction) extends BaseAction(g => g.creditChoiceTitle)(g => g.creditAutoLabel)
case class CreditTakeAction(self : Faction, src : CreditSrc, n : Int) extends BaseAction(g => g.creditChoiceTitle)(g => "Pay " ~ n.credits ~ " from " ~ g.creditSrcElem(src)) with NotForBots

// ---- effects ----------------------------------------------------------------------------------------------------------
case class CreditsLostFrom(f : Faction, n : Int, source : |[Card]) extends Event              // f lost n credits because of source's ability
case class SpendClicksEffect(f : Faction, n : Int) extends ForcedAction                       // f spends n clicks (a cost, not a loss)
case class PayIfAbleEffect(f : Faction, n : Int, spend : Spend, otherwise : $[ForcedAction]) extends ForcedAction   // f must pay n credits if able, else the other effects (Tollbooth)
case object FnsResumeEffect extends ForcedAction                                              // perform the suspended action again (its additional cost is paid)
case class FnsRezAfterCostEffect(card : Card) extends ForcedAction                            // rez the card, its additional rez cost is paid
case class FnsMarkRezCostPaidEffect(card : Card) extends ForcedAction                         // a prepaid rez effect follows: its additional rez cost is paid
case class FnsScoreAfterCostEffect(card : Card) extends ForcedAction                          // score the agenda, its additional score cost is paid
case class FnsMarkScoreCostPaidEffect(card : Card) extends ForcedAction
case object FnsTraceBoostAction extends ForcedAction                                          // after the "trace initiated" window: the Corp boosts
case class TraceInitiated(base : Int) extends Event                                           // a trace was initiated (Power Tap)
case class FnsCorpTrashFromHQCostPurpose(n : Int) extends Purpose                             // the Corp trashes the chosen HQ cards as an additional cost
case class BreakChooseSubsAction(self : Faction, breaker : Card, bi : Int, ice : Card, allowed : $[Int]) extends BaseAction(g => g.cardElem(breaker) ~ " (strength " ~ g.breakerStrength(breaker).hl ~ ")")(g => "Break chosen subroutines...".hl) with Soft with NotForBots
case class SubPick(ice : Card, idx : Int) extends Record

// a cost paid through the queue before the action it belongs to (an additional steal / trash cost)
trait ExtCost extends Cost {
    def payable(f : Faction, self : |[Card])(implicit g : Game) : Boolean
    def effects(f : Faction)(implicit g : Game) : $[ForcedAction]
    def text : String
}
case class RandomToBottomCost(n : Int) extends ExtCost {
    def elem = ("add " + n + " random cards from the grip to the bottom of the stack").hl
    def text = "add " + n + " random grip cards to the bottom of the stack"
    def payable(f : Faction, self : |[Card])(implicit g : Game) = g.hand(f).num >= n
    def effects(f : Faction)(implicit g : Game) = $(MessageEffect(f, "adds " + n + " random cards from the grip to the bottom of the stack (additional cost)"), RandomToBottomEffect(f, n))
}

// card hooks of this pass (marker traits on the card objects; read from the raw implementation)
trait FnsExtraRezCost {
    // the effects that pay the additional rez cost (the card's canRez says whether it can be paid)
    def extraRezCost(self : Card)(implicit g : Game) : $[ForcedAction]
}
trait FnsExtraScoreCost {
    def extraScoreCost(self : Card)(implicit g : Game) : $[ForcedAction]
}


trait FinalNsgRules extends FinalRotRules with PreventionRules with FixRules with RestrictionRules with CreditRules with StartupRules with RotAgendaRules { game : Game =>

    // ==== credit sources ==============================================================================================

    class CreditChoice(val f : Faction, val n : Int, val spend : Spend, val stealth : Int, val action : Action) {
        var parts : $[(CreditSrc, Int)] = $
        def paid = parts.map(_._2).sum
        def left = n - paid
    }

    var creditChoice : |[CreditChoice] = None
    var creditDecided : |[Action] = None
    var creditPlan : |[(Faction, Spend, $[(CreditSrc, Int)])] = None

    def creditPartOf(l : $[Cost]) : (Int, Int) = l.foldLeft((0, 0)) {
        case ((n, s), CreditCost(k)) => (n + k, s)
        case ((n, s), StealthCreditCost(k, st)) => (n + k, s + st.atMost(k))
        case (x, _) => x
    }

    // the payment a user action will make: (payer, credits, purpose, stealth part)
    def creditPaymentOf(a : Action) : |[(Faction, Int, Spend, Int)] = a match {
        case PlayCardAction(f, c) => |((f, playCost(c), Spend.play(c), 0))
        case RunnerInstallAction(f, c, h, _) => |((f, hostedInstallCost(c, h), Spend.install(c, None, h), 0))
        case CorpInstallAction(f, c, s) => |((f, installCost(c, s), Spend.install(c, s), 0))
        case ServerChosenAction(f, c, s, false) => |((f, installCost(c, s), Spend.install(c, s), 0))
        case RezAction(f, c, _) if !isRezzed(c) => |((Corp, rezCost(c), Spend.rez(c), 0))
        case AdvanceAction(f, c) => |((f, 1, Spend.advance(c), 0))
        case TrashResourceAction(f, c) => |((f, trashResourceCost, Spend.trashResource(c), 0))
        case RemoveTagAction(f) => |((f, removeTagCost, Spend.removeTag, 0))
        case PayTrashAction(f, c) => |((f, trashCost(c).|(0), Spend.trash(c), 0))
        case BreakSubAction(f, b, bi, ice, _) => impl(b).breaks(b).lift(bi)./{ br => val (n, s) = creditPartOf(breakCostsWith(b, br.costs)) ; (f, n, Spend.break(b, ice), s) }
        case BreakSubsAction(f, b, bi, ice, l) => impl(b).breaks(b).lift(bi)./{ br => val (n, s) = creditPartOf(breakCostsWith(b, breakCosts(br, l.num))) ; (f, n, Spend.break(b, ice), s) }
        case PumpAction(f, b, pi) => impl(b).pumps(b).lift(pi)./{ p => val (n, s) = creditPartOf(pumpCostsWith(b, p.costs)) ; (f, n, Spend.pump(b, run./~(_.ice)), s) }
        case UseAbilityAction(f, c, i, _) => impl(c).abilities(c).lift(i)./{ ab => val (n, s) = creditPartOf(ab.costs) ; (f, n, Spend.ability(c), s) }
        case UseRunnerAbilityAction(f, c, i, _) => impl(c).runnerAbilities(c).lift(i)./{ ab => val (n, s) = creditPartOf(ab.costs) ; (f, n, Spend.ability(c), s) }
        case AccessAbilityAction(f, s, c) => impl(s).accessAbility(s, c)./{ ab => val (n, st) = creditPartOf(ab.costs) ; (f, n, Spend.ability(s), st) }
        case StealAction(f, c) => val (n, s) = creditPartOf(stealCostOf(c)) ; |((f, n, Spend.other, s))
        case TraceCorpAction(f, n) => |((f, n, Spend.trace, 0))
        case TraceRunnerAction(f, n) => |((f, n, Spend.trace, 0))
        case ChoiceAction(f, _, alt) => alt.effects.starting.of[SpendEffect].map(x => (x.f, x.n, x.spend, x.stealth))
        case YesAction(f, _, l) => l.starting.of[SpendEffect].map(x => (x.f, x.n, x.spend, x.stealth))
        case _ => None
    }

    def creditAvail(f : Faction, s : Spend, src : CreditSrc) : Int = src match {
        case PoolCreditSrc => poolFor(f)
        case BadPubCreditSrc => bpCredits(f)
        case CardCreditSrc(c) => counter(c, "credit")
    }

    def creditStealth(src : CreditSrc) : Boolean = src match {
        case CardCreditSrc(c) => has(c, "Stealth")
        case _ => false
    }

    // the restricted sources (hosted credits, bad publicity credits) that could pay for s, in the default spending order
    def restrictedSources(f : Faction, s : Spend) : $[CreditSrc] =
        (f.isRunner && bpCredits(f) > 0).$(BadPubCreditSrc : CreditSrc) ++ creditSourcesOf(f, s).%(c => counter(c, "credit") > 0)./(c => CardCreditSrc(c) : CreditSrc)

    def needsCreditChoice(f : Faction, n : Int, s : Spend, stealth : Int) : Boolean =
        n > 0 && !noSpending(f) && restrictedSources(f, s).num >= 2 && creditsFor(f, s) > n && canSpend(f, n, s, stealth)

    def creditPromptFor(a : Action) : |[(Faction, Int, Spend, Int)] = a match {
        case _ : CreditAutoAction | _ : CreditTakeAction => None
        // Patchwork's interrupt comes first (it changes the cost): the choice is asked when the action is performed again
        case PlayCardAction(Runner, c) if !patchworkBypass && patchworkFor(c, playCost(c)).any => None
        case RunnerInstallAction(Runner, c, _, _) if !patchworkBypass && patchworkFor(c, installCost(c, None)).any => None
        case _ => creditPaymentOf(a).%{ case (f, n, s, st) => needsCreditChoice(f, n, s, st) }
    }

    def creditChoiceTitle : Elem = creditChoice./(ch => "Pay " ~ ch.n.credits ~ " (" ~ ch.left.hl ~ " left): choose where the credits come from").|("Pay credits".txt)

    def creditSrcElem(src : CreditSrc) : Elem = src match {
        case PoolCreditSrc => "the credit pool".hl
        case BadPubCreditSrc => "bad publicity credits".hl
        case CardCreditSrc(c) => cardElem(c)
    }

    // the default allocation of the remaining credits (CreditRules' order), for the label of the automatic option
    def creditAutoLabel : Elem = creditChoice./{ ch =>
        val l = defaultAllocation(ch)
        ((ch.parts.any).?("Rest automatically: ").|("Automatically: ")).txt ~ l.map { case (src, m) => m.hl ~ " from " ~ creditSrcElem(src) }.join(", ")
    }.|("Automatically".txt)

    def remainingOf(ch : CreditChoice, src : CreditSrc) : Int = creditAvail(ch.f, ch.spend, src) - ch.parts.filter(_._1 == src).map(_._2).sum

    def allSources(ch : CreditChoice) : $[CreditSrc] = restrictedSources(ch.f, ch.spend) :+ PoolCreditSrc

    def stealthNeed(ch : CreditChoice) : Int = (ch.stealth.atMost(ch.n) - ch.parts.filter(p => creditStealth(p._1)).map(_._2).sum).atLeast(0)

    // CreditRules.spendCredits' order applied to what is left
    def defaultAllocation(ch : CreditChoice) : $[(CreditSrc, Int)] = {
        var left = ch.left
        var need = stealthNeed(ch).atMost(left)
        var avail : Map[CreditSrc, Int] = allSources(ch)./(s => s -> remainingOf(ch, s)).toMap
        var out : $[(CreditSrc, Int)] = $
        def take(s : CreditSrc, m : Int) { if (m > 0) { out :+= (s -> m) ; avail += s -> (avail(s) - m) ; left -= m } }
        allSources(ch).%(creditStealth).foreach { s => val m = need.atMost(avail(s)) ; take(s, m) ; need -= m }
        val keep = keepStealth(ch.f)
        allSources(ch).%(_ != PoolCreditSrc).%(s => !keep || !creditStealth(s)).foreach(s => take(s, left.atMost(avail(s))))
        take(PoolCreditSrc, left.atMost(avail(PoolCreditSrc)))
        if (keep) allSources(ch).%(creditStealth).foreach(s => take(s, left.atMost(avail(s))))
        out.groupBy(_._1).toList.map { case (s, l) => s -> l.map(_._2).sum }.sortBy { case (s, _) => allSources(ch).indexOf(s) }
    }

    // can m credits come from src (the rest of the payment, including its stealth part, stays payable)
    def creditTakeOk(ch : CreditChoice, src : CreditSrc, m : Int) : Boolean = m > 0 && m <= ch.left && m <= remainingOf(ch, src) && {
        val left2 = ch.left - m
        val need2 = (stealthNeed(ch) - creditStealth(src).??(m)).atLeast(0)
        val rest = allSources(ch)./(s => remainingOf(ch, s) - (s == src).??(m))
        val total = rest.sum
        val stealthTotal = allSources(ch).zip(rest).filter(p => creditStealth(p._1)).map(_._2).sum
        total >= left2 && stealthTotal >= need2.atMost(left2) && need2 <= left2
    }

    def creditAsk(ch : CreditChoice) : Continue =
        Ask(ch.f)
            .add(CreditAutoAction(ch.f))
            .add(allSources(ch)./~(src => 1.to(remainingOf(ch, src).atMost(ch.left)).$.%(m => creditTakeOk(ch, src, m))./(m => CreditTakeAction(ch.f, src, m))))

    def creditResume(ch : CreditChoice) : Continue = {
        creditChoice = None
        creditDecided = |(ch.action)
        creditPlan = |((ch.f, ch.spend, ch.parts))
        if (ch.parts.any)
            log(ch.f, "chooses the credits:", ch.parts.map { case (src, m) => m.hl ~ " from " ~ creditSrcElem(src) }.join(", "), "(the rest automatically)")
        performInternal(ch.action, NoVoid)
    }

    // the plan chosen for the next matching payment is applied first, the rest of the payment follows CreditRules' order
    override def spendCredits(f : Faction, n : Int, s : Spend, stealth : Int = 0) {
        if (s.is("install") && n > 0)
            s.card.foreach(c => fnsInstallSpent += c)
        creditPlan match {
            case Some((pf, ps, parts)) if pf == f && ps.kind == s.kind && ps.card == s.card && n > 0 =>
                creditPlan = None
                creditDecided = None
                var left = n
                var st = stealth.atMost(n)
                parts.foreach { case (src, m0) =>
                    val m = m0.atMost(left).atMost(creditAvail(f, s, src))
                    if (m > 0) {
                        src match {
                            case PoolCreditSrc => credits += f -> (credits(f) - m)
                            case BadPubCreditSrc =>
                                bpRun.foreach(_.credits -= m)
                                log(f, "spends", m.credits, "of bad publicity")
                                trigger(BadPublicityCreditsSpent(m))
                            case CardCreditSrc(c) => spendFromCard(f, c, m)
                        }
                        left -= m
                        if (creditStealth(src)) st -= m
                    }
                }
                super.spendCredits(f, left, s, st.atLeast(0))
            case _ => super.spendCredits(f, n, s, stealth)
        }
    }

    // ==== sources of effects ==========================================================================================

    // One marker mechanism for the whole engine: FixRules' `FxSourceEffect(card, on)` (paid abilities: FixRules.useAbility; "when
    // scored": FixRules.scoreAgenda; card triggers: FinalRotRules.frSourced in game.scala triggerEffects). This pass adds the markers
    // around "when encountered", "when accessed", "when rezzed" and prevention abilities.
    def fnsSourced(c : Card, l : $[ForcedAction]) : $[ForcedAction] = frSourced(c, l)

    // the card whose ability is resolving (innermost): FinalRotRules.frSourceNow (a marked ability, a resolving subroutine, an
    // operation / event being played, the accessed card, the scoring agenda); read by GameNET (CreditsLostFrom) and the damage windows
    def effectSource : |[Card] = frSourceNow

    override def encounterAbility(ice : Card) : $[ForcedAction] = fnsSourced(ice, super.encounterAbility(ice))

    // ==== additional costs ============================================================================================

    def rawImpl(c : Card) : CardImpl = CardImpls(c.code)

    def extraRezCostOf(c : Card) : $[ForcedAction] = rawImpl(c) match {
        case x : FnsExtraRezCost if !isDisabled(c) => x.extraRezCost(c)
        case _ => $
    }

    def extraScoreCostOf(c : Card) : $[ForcedAction] = rawImpl(c) match {
        case x : FnsExtraScoreCost if !isDisabled(c) => x.extraScoreCost(c)
        case _ => $
    }

    var fnsRezCostPaid : $[Card] = $
    var fnsScoreCostPaid : $[Card] = $
    var fnsExtraPaid : $[Action] = $
    var fnsExtPrepaid = false
    var fnsInstallSpent : Set[Card] = Set()

    // the Corp's basic action to trash a resource: number of HQ cards the Corp must trash in addition (Sebastiao, Manuel Lattes)
    def trashResourceHQCost(c : Card) : Int = activeCards(Runner).%!(isDisabled).count { x =>
        (x.code == "34066" && has(c, "Connection")) || (x.code == "34075" && x == c && threat(3))
    }

    def extCostsOf(l : $[Cost]) : $[ExtCost] = l.of[ExtCost]

    // queued additional trash cost of an accessed card (Daniela Jorge Inacio)
    def trashExtCosts(c : Card) : $[ExtCost] = (c.code == "34047" && isRezzed(c) && !isDisabled(c)).$(RandomToBottomCost(2))

    override def payCosts(f : Faction, costs : $[Cost], self : |[Card], spend : |[Spend] = None) {
        val ext = extCostsOf(costs)
        super.payCosts(f, costs.%!(_.is[ExtCost]), self, spend)
        if (!fnsExtPrepaid)
            ext.foreach(x => pushAll(x.effects(f)))
    }

    // persistent additional steal costs of trashed cards (Daniela Jorge Inacio trashed during the run)
    override def stealCostOf(c : Card) : $[Cost] =
        super.stealCostOf(c) ++ flagsOf("fns-daniela").%(fl => fl.server.any && zoneServer(zone(c)) == fl.server)./(_ => RandomToBottomCost(2))

    def resumeWith(a : Action, costs : $[ForcedAction]) : Continue = {
        fnsExtraPaid = (fnsExtraPaid.%(_ == a) :+ a).distinct
        fnsResume +:= a
        pushAll(costs :+ FnsResumeEffect)
        NextAction
    }

    var fnsResume : $[Action] = $
    var fnsTrashCostPaid : $[Card] = $             // accessed cards whose additional trash cost was paid before the trash (Daniela)

    override def rezCard(c : Card, ignoreCost : Boolean) {
        if (!isRezzed(c) && !ignoreCost && isInstalled(c) && !fnsRezCostPaid.has(c) && extraRezCostOf(c).any && rezBlock(c, false).none) {
            pushAll(extraRezCostOf(c) :+ FnsRezAfterCostEffect(c))
        }
        else {
            val wasRezzed = isRezzed(c)
            val before = pending.num
            val onRez = impl(c).onRez(c)
            super.rezCard(c, ignoreCost)
            fnsRezCostPaid = fnsRezCostPaid.but(c)
            // the "when rezzed" abilities resolve with the card as their source
            if (!wasRezzed && isRezzed(c) && onRez.any && pending.num >= before + onRez.num) {
                val added = pending.take(pending.num - before)
                if (added.takeRight(onRez.num) == onRez)
                    pending = added.dropRight(onRez.num) ++ fnsSourced(c, onRez) ++ pending.drop(pending.num - before)
            }
        }
    }

    // once its additional rez cost is paid, a card's canRez (whether the additional cost can be paid) no longer applies
    override def rezBlock(c : Card, ignoreCost : Boolean) : |[String] = super.rezBlock(c, ignoreCost || fnsRezCostPaid.has(c))

    override def scoreAgenda(c : Card) {
        fnsScoreCostPaid = fnsScoreCostPaid.but(c)
        super.scoreAgenda(c)
    }

    // ==== trashing records ============================================================================================

    // (installed, rezzed, zone) of a card when it was last trashed
    var fnsTrashInfo : Map[Card, (Boolean, Boolean, Zone)] = Map()

    def fnsWasInstalled(c : Card) : Boolean = fnsTrashInfo.get(c).exists(_._1)
    def fnsWasRezzed(c : Card) : Boolean = fnsTrashInfo.get(c).exists(_._2)
    def fnsTrashedFrom(c : Card) : |[Zone] = fnsTrashInfo.get(c)./(_._3)

    override def trashCard(c : Card, by : Faction, accessed : Boolean) {
        fnsTrashInfo += c -> ((isInstalled(c), isRezzed(c), zone(c)))
        super.trashCard(c, by, accessed)
    }

    // ==== the "trace initiated" window ================================================================================

    override def preventTitle(ctx : PreventCtx, f : Faction) : Elem = ctx.kind match {
        case "trace" => "A trace is initiated (base strength ".txt ~ traceBase.hl ~ ")"
        case _ => super.preventTitle(ctx, f)
    }

    override def preventPassLabel(f : Faction, q : String) : Elem = q match {
        case "trace" if preventCtx("trace").any => "Continue the trace".txt
        case _ => super.preventPassLabel(f, q)
    }

    // ==== break: a soft multi-select of the subroutines ===============================================================

    implicit def descSubPick(g : Game, s : SubPick) : Elem = ("[sub] " + subs(s.ice).lift(s.idx)./(_.label).|("subroutine " + (s.idx + 1))).hl

    def addSubsetBreaks(c : Continue, ice : Card) : Continue = {
        def extra(l : $[UserAction]) : $[UserAction] = {
            val singles = l.collect { case BreakSubAction(Runner, b, bi, i, s) if i == ice => (b, bi, s) }
            val multi = l.collect { case BreakSubsAction(Runner, b, bi, i, _) if i == ice => (b, bi) }
            (singles.map(x => (x._1, x._2)) ++ multi).distinct./~ { case (b, bi) =>
                impl(b).breaks(b).lift(bi).%(br => br.n != 1)./~ { br =>
                    val allowed = singles.filter(x => x._1 == b && x._2 == bi).map(_._3).distinct.sorted
                    (allowed.num > 2).$(BreakChooseSubsAction(Runner, b, bi, ice, allowed) : UserAction)
                }
            }
        }
        c match {
            case Ask(Runner, l) => Ask(Runner, l ++ extra(l))
            case MultiAsk(asks, p) => MultiAsk(asks./(a => (a.faction == Runner).?(Ask(Runner, a.actions ++ extra(a.actions))).|(a)), p)
            case c => c
        }
    }

    def breakSubsetAsk(f : Faction, b : Card, bi : Int, ice : Card, allowed : $[Int]) : Continue = {
        val br = impl(b).breaks(b).lift(bi)
        val max = br./(x => (x.n == 0).?(allowed.num).|(x.n.atMost(allowed.num))).|(0)
        val payable = 1.to(max).$.%(k => br.exists(x => canPay(Runner, breakCostsWith(b, breakCosts(x, k)), |(b), |(Spend.break(b, ice)))))
        val maxPay = payable.lastOption.|(0)
        XXSelectObjectsAction(f, allowed./(i => SubPick(ice, i)))
            .withGroup("Break subroutines on " ~ cardElem(ice) ~ " with " ~ cardElem(b) ~ " (up to " ~ maxPay.hl ~ ")")
            .withRule(_.upTo(maxPay.atLeast(1)).atLeast(1))
            .withThens(l => $(BreakSubsAction(Runner, b, bi, ice, l./(_.idx)).!(l.none || l.num > maxPay, "cannot pay").!(!canBreakSub(ice, l./(_.idx)), "cannot break")))
            .withExtras(CancelAction)
    }

    // Unsmiling Tsarevna: "cannot break more than 1 of its printed subroutines" (subroutines added by other cards can be broken)
    override def canBreakSub(ice : Card, l : $[Int]) : Boolean =
        if (hasFlag("max-break-1", ice)) {
            val printed = fxPrintedSubIndices(ice)
            !hasFlag("cannot-break", ice) &&
                !(hasFlag("cannot-break-printed", ice) && l.exists(printed.has)) &&
                (state(ice).brokenSubs.%(printed.has).num + l.distinct.%(printed.has).num) <= 1
        }
        else
            super.canBreakSub(ice, l)

    // ==== root moves and swaps ========================================================================================

    var fnsRootBypass = false

    def rootMoveBlock(c : Card, s : Server) : |[String] =
        if ((c.data.isAgenda || c.data.isAsset) && s.isCentral) |("assets and agendas only in remote servers")
        else if (!c.data.isIce && !InstallRules.serverAllowed(c.code, |(s))) |("cannot be moved there")
        else None

    // after a card moved into the root of s: the other asset / agenda and the other region of that root are trashed (as an overinstall)
    def enforceRoot(s : Server, moved : $[Card]) {
        val l = root(s)
        val aa = l.%(x => x.data.isAgenda || x.data.isAsset)
        val regions = l.%(isRegion)
        val victims = ((aa.num > 1).??(aa.%!(moved.has)) ++ (regions.num > 1).??(regions.%!(moved.has))).distinct
        victims.foreach { x =>
            log(Corp, "trashes", hiddenCorpElem(x), "(" + isRegion(x).?("limit 1 region per server").|("1 asset or agenda per remote server") + ")")
            trashCard(x, Corp, false)
        }
    }

    def bypassRoot(a : Action) : Continue = {
        fnsRootBypass = true
        try performInternal(a, NoVoid) finally fnsRootBypass = false
    }

    // ==== double operations of the non-Startup sets: the additional [click] is spent on playing (FixRules' "double" register) and
    //      Synchrocyclotron's "ignore the additional cost of the first double operation each turn" applies (as Startup.doubleCost)
    def fnsDoubleFree(f : Faction) : Boolean = f.isCorp && reg("double") == 0 && activeCards(Corp).exists(c => c.code == "36027" && isRezzed(c))
    def fnsDoubleCanPlay(f : Faction) : Boolean = clicks(f) >= 2 || fnsDoubleFree(f)
    def fnsDoubleCost(f : Faction) : $[ForcedAction] = $(RegisterEffect("double", 1)) ++ (!fnsDoubleFree(f)).$(LoseClicksEffect(f, 1))

    // ==== MirrorMorph =================================================================================================

    var fnsMMExtra : $[String] = $                 // the three actions taken: the additional action must differ from them

    def mmKey(a : UserAction) : |[String] = a match {
        case ClickCreditAction(_) => |("credit")
        case ClickDrawAction(_) => |("draw")
        case PlayMenuAction(_) => |("play")
        case InstallMenuAction(_) => |("install")
        case AdvanceMenuAction(_) => |("advance")
        case TrashResourceMenuAction(_) => |("trash-resource")
        case PurgeAction(_) => |("purge")
        case UseAbilityAction(_, c, i, _) if impl(c).abilities(c).lift(i).exists(_.click) => |("ability-" + c.code + "-" + c.n)
        case _ => None
    }

    // ==== Bling: hosted cards can be played / installed as if they were in the grip ===================================

    def blingCards : $[Card] = activeCards(Runner).%(c => c.code == "35006" && !isDisabled(c))./~(stashed)

    var fnsBlingBypass = false

    // the Runner's cards that can be played / installed as if they were in the grip (the grip and the cards hosted on Bling)
    def gripCards : $[Card] = hand(Runner) ++ blingCards

    // ==== Tsakhia "Bankhar" Gantulga: "whenever the Corp would resolve a subroutine, instead they resolve 'Do 1 net damage'" ======
    // the ice keeps its subroutines (they are broken / shown as printed); the replacement happens when one resolves
    override def extImpl(c : Card) : CardImpl = super.extImpl(c) match {
        case x : NetDamageSubsImpl => x.raw
        case x => x
    }

    // ==== option filter ================================================================================================

    def fnsFilter(c : Continue) : Continue = {
        def opt(a : UserAction) : UserAction = a match {
            case TrashResourceAction(f, r) if hand(Corp).num < trashResourceHQCost(r) => a.!(true, "the Corp must trash " + trashResourceHQCost(r) + " card from HQ")
            case x if fnsMMExtra.any && mmKey(x).exists(fnsMMExtra.has) => a.!(true, "MirrorMorph: a different action")
            case MoveServerChosenAction(_, mc, ms) if rootMoveBlock(mc, ms).any => a.!(true, rootMoveBlock(mc, ms).get)
            case UnavailableReasonAction(PlayMenuAction(Runner), "nothing to play") if blingCards.exists(c => c.data.isEvent) => PlayMenuAction(Runner)
            case UnavailableReasonAction(InstallMenuAction(Runner), "nothing to install") if blingCards.exists(installable) => InstallMenuAction(Runner)
            case x => x
        }
        c match {
            case Ask(f, l) => Ask(f, l./(opt))
            case MultiAsk(asks, p) => MultiAsk(asks./(a => Ask(a.faction, a.actions./(opt))), p)
            case c => c
        }
    }

    override def restrictContinue(c : Continue) : Continue = super.restrictContinue(fnsFilter(c))

    // ==== game-level triggers (end of Game.triggerEffects) ============================================================

    def finalNsgTriggers(e : Event) : $[ForcedAction] = e match {
        case Installed(Runner, c) =>
            fnsInstallSpent -= c
            $
        case Trashed(c, Runner, _) if owner(c).isRunner && fnsWasInstalled(c) =>
            regInc("fns-runner-trashed-own-installed")
            $
        case Accessed(c) if c.data.isAgenda && flagsOf("fns-whistleblower").exists(fl => fl.card.exists(x => x.title == c.title)) =>
            $(FnsWhistleblowerStealEffect(c))
        case TurnEnds(_) =>
            creditDecided = None
            creditPlan = None
            fnsExtraPaid = $
            fnsMMExtra = $
            // markers whose closing marker was dropped from the queue (an ended run, ...) are forgotten
            fxSources = fxSources.%(c => pending.has(FxSourceEffect(c, false)))
            fnsTrashCostPaid = $
            $
        case _ => $
    }

    // ==== effects =====================================================================================================

    def performFinalNsg : PartialFunction[Action, Continue] = {
        // ---- credit sources
        case a : UserAction if creditDecided != |(a) && creditChoice.none && creditPromptFor(a).any =>
            val (f, n, s, st) = creditPromptFor(a).get
            creditPlan = None
            creditDecided = None
            val ch = new CreditChoice(f, n, s, st, a)
            creditChoice = |(ch)
            creditAsk(ch)

        case CreditAutoAction(f) =>
            creditChoice match {
                case Some(ch) => creditResume(ch)
                case None => NextAction
            }

        case CreditTakeAction(f, src, m) =>
            creditChoice match {
                case Some(ch) if creditTakeOk(ch, src, m) =>
                    ch.parts :+= (src -> m)
                    if (ch.left <= 0)
                        creditResume(ch)
                    else
                        creditAsk(ch)
                case Some(ch) => creditAsk(ch)
                case None => NextAction
            }

        // ---- additional costs of the Corp's basic action to trash a resource (Sebastiao, Manuel Lattes)
        case a @ TrashResourceAction(f, c) if !fnsExtraPaid.has(a) && trashResourceHQCost(c) > 0 =>
            val n = trashResourceHQCost(c)
            if (hand(Corp).num < n) {
                log(Corp, "cannot pay the additional cost to trash", c)
                ActionPhaseAction(f)
            }
            else
                resumeWith(a, $(ChooseCardsEffect(Corp, hand(Corp), n, n, FnsCorpTrashFromHQCostPurpose(n), "Trash " + n + " card from HQ (additional cost to trash " + asciiTitle(c) + ")")))

        // ---- additional steal / trash costs paid through the queue (Daniela Jorge Inacio)
        case a @ StealAction(f, c) if !fnsExtraPaid.has(a) && extCostsOf(stealCostOf(c)).any =>
            resumeWith(a, extCostsOf(stealCostOf(c))./~(_.effects(Runner)))

        case a @ PayTrashAction(f, c) if !fnsExtraPaid.has(a) && trashExtCosts(c).any =>
            fnsTrashCostPaid :+= c
            resumeWith(a, trashExtCosts(c)./~(_.effects(Runner)))

        case FnsResumeEffect =>
            fnsResume match {
                case a :: rest =>
                    fnsResume = rest
                    fnsExtPrepaid = true
                    try performInternal(a, NoVoid) finally fnsExtPrepaid = false
                case Nil => NextAction
            }

        // ---- additional score costs (Azef Protocol)
        case ScoreAction(f, c) if extraScoreCostOf(c).any && !fnsScoreCostPaid.has(c) =>
            windowPassed = $
            actedInWindow = |(f)
            push(reask(ctxOf(current)))
            pushAll(extraScoreCostOf(c) :+ FnsScoreAfterCostEffect(c))
            NextAction

        case a @ ScoreAgendaEffect(c) if extraScoreCostOf(c).any && !fnsScoreCostPaid.has(c) =>
            pushAll(extraScoreCostOf(c) :+ FnsMarkScoreCostPaidEffect(c) :+ a)
            NextAction

        case FnsMarkScoreCostPaidEffect(c) =>
            fnsScoreCostPaid :+= c
            NextAction

        case FnsScoreAfterCostEffect(c) =>
            if (isInstalled(c) && c.data.isAgenda)
                scoreAgenda(c)
            NextAction

        // ---- additional rez costs on rezzes that pay before rezzing ignoring costs
        case a @ RezDiscountEffect(c, _) if !isRezzed(c) && !fnsRezCostPaid.has(c) && extraRezCostOf(c).any && rezBlock(c).none =>
            pushAll(extraRezCostOf(c) :+ FnsMarkRezCostPaidEffect(c) :+ a)
            NextAction

        case a @ DiscountRezEffect(c, _) if !isRezzed(c) && !fnsRezCostPaid.has(c) && extraRezCostOf(c).any && rezBlock(c).none =>
            pushAll(extraRezCostOf(c) :+ FnsMarkRezCostPaidEffect(c) :+ a)
            NextAction

        case a @ CorpInstallPayingEffect(c, s, d, true) if !fnsRezCostPaid.has(c) && extraRezCostOf(c).any && notInPlay(c) =>
            if (rezBlock(c).none) {
                pushAll(extraRezCostOf(c) :+ FnsMarkRezCostPaidEffect(c) :+ a)
                NextAction
            }
            else {
                log(Corp, "cannot pay the additional cost to rez", hiddenCorpElem(c))
                push(CorpInstallPayingEffect(c, s, d, false))
                NextAction
            }

        case a @ ServerDiscountChosenAction(f, c, s, d, true) if !fnsRezCostPaid.has(c) && extraRezCostOf(c).any && notInPlay(c) =>
            if (rezBlock(c).none) {
                fnsResume +:= a ; pushAll(extraRezCostOf(c) ++ $(FnsMarkRezCostPaidEffect(c), FnsResumeEffect))
                NextAction
            }
            else {
                log(Corp, "cannot pay the additional cost to rez", hiddenCorpElem(c))
                performInternal(ServerDiscountChosenAction(f, c, s, d, false), NoVoid)
            }

        case FnsMarkRezCostPaidEffect(c) =>
            fnsRezCostPaid :+= c
            NextAction

        case FnsRezAfterCostEffect(c) =>
            if (isInstalled(c) && !isRezzed(c)) {
                fnsRezCostPaid :+= c
                rezCard(c, false)
            }
            NextAction

        // ---- the "trace initiated" window: Flip Switch / Disrupter reduce the base strength, then the Corp boosts
        case FlipSwitchTraceAction(c) if preventCtx("trace").any =>
            traceBase = 0
            preventCtx("trace").foreach(_.remaining = 0)
            log("The base trace strength is reduced to", 0.hl)
            NextAction

        case FnsTraceBoostAction =>
            TraceCorpBoostAction(traceBase, traceSuccess, traceFail)

        // ---- root moves and swaps
        case a @ MoveToRootEffect(c, s) if !fnsRootBypass && isInstalled(c) && !serverOf(c).has(s) =>
            rootMoveBlock(c, s) match {
                case Some(r) =>
                    log(hiddenCorpElem(c), "cannot be moved to", s, "(" + r + ")")
                    NextAction
                case None =>
                    val res = bypassRoot(a)
                    if (zone(c) == RootZone(s))
                        enforceRoot(s, $(c))
                    res
            }

        case a @ SwapRootEffect(x, y) if !fnsRootBypass =>
            (zone(x), zone(y)) match {
                case (RootZone(sx), RootZone(sy)) if sx != sy && (rootMoveBlock(x, sy).any || rootMoveBlock(y, sx).any) =>
                    log(Corp, "cannot swap", hiddenCorpElem(x), "and", hiddenCorpElem(y), "(" + (rootMoveBlock(x, sy) orElse rootMoveBlock(y, sx)).get + ")")
                    NextAction
                case (RootZone(sx), RootZone(sy)) if sx != sy =>
                    val res = bypassRoot(a)
                    enforceRoot(sx, $(y))
                    enforceRoot(sy, $(x))
                    res
                case _ => bypassRoot(a)
            }

        case a @ SwapInstalledWithHandEffect(x, y) if !fnsRootBypass =>
            zone(x) match {
                case RootZone(s) if rootMoveBlock(y, s).any =>
                    log(Corp, "cannot install", hiddenCorpElem(y), "in", s, "(" + rootMoveBlock(y, s).get + ")")
                    NextAction
                case RootZone(s) =>
                    val res = bypassRoot(a)
                    if (zone(y) == RootZone(s))
                        enforceRoot(s, $(y))
                    res
                case _ => bypassRoot(a)
            }

        // ---- run restrictions (Front Company's "the first run each turn cannot be made against a remote server") also apply to
        //      runs made by card effects on a given server
        case RunEffect(s, src) if run.none && servers.has(s) && !canRunServer(s) =>
            log(Runner, "cannot run", s)
            NextAction

        // ---- the subset break
        case BreakChooseSubsAction(f, b, bi, ice, allowed) =>
            breakSubsetAsk(f, b, bi, ice, allowed)

        // ---- Bling: the basic play / install actions list its hosted cards
        case a @ PlayMenuAction(Runner) if !fnsBlingBypass && blingCards.exists(_.data.isEvent) =>
            val bl = blingCards.%(_.data.isEvent)./(c => PlayCardAction(Runner, c).!(!canSpend(Runner, playCost(c), Spend.play(c)), "not enough credits").!(!impl(c).canPlay(c), "cannot play"))
            fnsBlingBypass = true
            val r = try performInternal(a, NoVoid) finally fnsBlingBypass = false
            r match {
                case Ask(f, l) => Ask(f, l.%!(_ == CancelAction) ++ bl :+ CancelAction)
                case r => r
            }

        case a @ InstallMenuAction(Runner) if !fnsBlingBypass && blingCards.exists(installable) =>
            val bl = blingCards.%(installable)./(c => InstallWhereAction(Runner, c))
            fnsBlingBypass = true
            val r = try performInternal(a, NoVoid) finally fnsBlingBypass = false
            r match {
                case Ask(f, l) => Ask(f, l.%!(_ == CancelAction) ++ bl :+ CancelAction)
                case r => r
            }

        // ---- bookkeeping effects
        case PayIfAbleEffect(f, n, s, otherwise) =>
            if (canSpend(f, n, s))
                push(SpendEffect(f, n, s, 0, $))
            else
                pushAll(otherwise)
            NextAction

        case SpendClicksEffect(f, n) =>
            if (n > 0 && clicks(f) >= n) {
                payCosts(f, $(ClickCost(n)), None)            // ClicksSpentOrLost during a run (PreventionRules.payCosts)
                log(f, "spends", ("[click]" * n).hl)
                reopenWindows()
                trigger(ClicksSpent(f, n, "effect"))
            }
            NextAction

        case FnsWhistleblowerStealEffect(c) =>
            flagsOf("fns-whistleblower").%(fl => fl.card.exists(x => x.title == c.title)).starting.foreach { fl =>
                if (location.contains(c) && !zone(c).is[ScoredZone] && run.exists(r => !r.ended && !r.cannotStealOrTrash) && !accessProtected(c)) {
                    flags = flags.%!(_ eq fl)
                    log(Runner, "steals", c, "ignoring all costs (Whistleblower)")
                    stealAgenda(c)
                }
            }
            NextAction
    }

    // ==== stackable overrides =========================================================================================

    override def performEffects : PartialFunction[Action, Continue] = {
        val base = super.performEffects
        ({
            // credit losses carry their source
            case a @ LoseCreditsEffect(f, n) =>
                val before = credits(f)
                val src = effectSource
                val r = base(a)
                val m = before - credits(f)
                if (m > 0)
                    trigger(CreditsLostFrom(f, m, src))
                r

            // the trace is initiated: TraceInitiated, then the window, then the Corp boosts
            case a @ TraceEffect(_, _, _) =>
                base(a)                          // sets the base strength (and the trace's effects); its legacy ask is replaced by the window
                val ctx = new PreventCtx("trace")
                ctx.count = 1
                ctx.remaining = 1
                ctx.by = Corp
                push(FnsTraceBoostAction)
                openPrevent(ctx, $)
                trigger(TraceInitiated(traceBase))
                NextAction

            // a prevention ability resolves with its card as the source
            case a @ PreventUseAction(f, c, i, q, n) if preventTop.exists(_.q == q) =>
                val effects = preventTop./~(ctx => impl(c).preventions(c, q, ctx.amount).lift(i))./~(_.effects)
                val r = base(a)
                val k = pending.indexWhere(_.is[PreventStepAction])
                if (effects.any && k >= effects.num && pending.slice(k - effects.num, k) == effects)
                    pending = pending.take(k - effects.num) ++ fnsSourced(c, effects) ++ pending.drop(k)
                r
        } : PartialFunction[Action, Continue]) orElse base
    }

    override def performAccess : PartialFunction[Action, Continue] = {
        val base = super.performAccess
        ({
            // the "when accessed" abilities resolve with the accessed card as their source
            case a @ AccessCardAction(c) if run.exists(r => !r.ended) && !zone(c).is[DiscardZone] && impl(c).onAccess(c).any =>
                val oa = impl(c).onAccess(c)
                val r = base(a)
                val k = pending.indexWhere(_ == AccessDecisionAction(c))
                if (k >= oa.num && pending.slice(k - oa.num, k) == oa)
                    pending = pending.take(k - oa.num) ++ fnsSourced(c, oa) ++ pending.drop(k)
                r
        } : PartialFunction[Action, Continue]) orElse base
    }

    override def performRun : PartialFunction[Action, Continue] = {
        val base = super.performRun
        ({
            case a @ EncounterAction(ice) => addSubsetBreaks(base(a), ice)
            case a @ FireSubAction(ice, i) if hasFlag("net-subs", ice) && run.exists(r => !r.ended) && subs(ice).lift(i).any =>
                val eff = subs(ice)(i).effect
                val r = base(a)
                pending match {
                    case x :: rest if x == eff =>
                        log("The subroutine resolves as", "Do 1 net damage".hl, "(Bankhar)")
                        pending = DamageEffect("net", 1) +: rest
                    case _ =>
                }
                r
        } : PartialFunction[Action, Continue]) orElse base
    }

    override def resolvePurpose(f : Faction, l : $[Card], p : Purpose) : Continue = p match {
        case FnsCorpTrashFromHQCostPurpose(n) =>
            withTrashCause("cost") {
                l.foreach(c => trashCard(c, Corp, false))
            }
            NextAction
        case _ => super.resolvePurpose(f, l, p)
    }
}

case class FnsWhistleblowerStealEffect(card : Card) extends ForcedAction    // the named agenda is stolen ignoring all costs
