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
import Helpers._


// ---------------------------------------------------------------------------------------------------------------------
// Final rules-accuracy pass of the rotated (FFG-era) cards (see STATUS-fix-final-rot.md and ENGINE.md section 8.7).
//
// Mixin position (game.scala): `... with CreditRules with RestrictionRules with FixRules with PreventionRules with FinalRotRules`.
// FinalRotRules is the outermost trait: its overrides see every action first and delegate everything they do not change to
// `super` (so PreventionRules' windows and FixRules' bookkeeping still see each action once). Several of its mechanisms need
// to act before a window is opened (unpreventable damage, the source of the damage) or around the result of the other traits
// (restrictContinue: the check after every action), which is only possible from the outside.
//
// Generic mechanisms:
//   * sources: the triggered abilities of every card resolve between FxSourceEffect markers (game.scala triggerEffects), so
//     `fxSources` knows the card whose triggered or paid ability is resolving; `frSourceNow` is the card doing something now
//     (innermost of: paid / triggered ability, resolving subroutine, operation / event, accessed card, scoring agenda). The
//     damage windows get that card as their source (Chrome Parlor, Prana Condenser, Tori Hanzo, Lucky Charm read it).
//   * unpreventable damage / trashes (FrUnpreventableDamageEffect / FrUnpreventableTrashEffect).
//   * "the next paid ability window after X" (Salvaged Vanadis Armory, Bioroid Work Crew): frArm / frArmed.
//   * batches of Corp cards trashed by the Runner (FrCorpTrashBatch: Warroid Tracker, Overseer Matrix).
//   * the pre-trash interrupt of Skorpios Defense Systems (finalRotPreTriggers, before every other triggered ability).
//   * the check after every action (restrictContinue): The Black File leaving play, "when uninstalled" (Muertos Gang Member),
//     agendas added to the Runner's score area (Jackpot!).
//
// Merged order: FinalNsgRules extends this trait and is mixed in after it (`... with PreventionRules with FinalRotRules with
// FinalNsgRules`), so FinalNsgRules' overrides run first and call these through `super`; `performFinalRot` is the last partial
// function of the performInternal chain. "Spend [click]" choices use FinalNsgRules' `SpendClicksEffect`.
// ---------------------------------------------------------------------------------------------------------------------

// ---- events -----------------------------------------------------------------------------------------------------------
case class FrCorpTrashBatch(cards : $[Card], servers : $[|[Server]]) extends Event        // Corp cards trashed by the Runner by one effect / access (server each came from)
case class FrAddedToScoreArea(card : Card, f : Faction) extends Event                     // a card entered f's score area as an agenda (stolen, swapped, "as an agenda", ...)
case class FrUninstalled(card : Card, zone : Zone) extends Event                           // an installed Runner card left play without being trashed (to the grip, stack, removed from the game)
case class FrStackSearched(source : |[Card]) extends Event                                // the Runner searched the stack (StackSearchedEffect)
case class FrBeforeFirstTurn(f : Faction) extends Event                                    // "before taking your first turn"

// ---- effects ----------------------------------------------------------------------------------------------------------
case class FrUnpreventableDamageEffect(kind : String, n : Int) extends ForcedAction       // damage that cannot be prevented
case class FrUnpreventableTrashEffect(card : Card, by : Faction) extends ForcedAction     // a trash that cannot be prevented
case object FrUnpreventableClearEffect extends ForcedAction
case object FrSkorpiosCheckEffect extends ForcedAction                                    // Skorpios: the Runner cards just trashed are set aside, the Corp may remove 1
case object FrTrashBatchEndEffect extends ForcedAction                                    // the trashes of one effect are finished
case class FrVirtualTagEffect(n : Int) extends ForcedAction                               // the Runner is considered to have n more (n < 0: fewer) tags: not taken, cannot be removed
case class FrRezAfterCostAction(card : Card, ignoreCost : Boolean) extends ForcedAction   // rez after the additional cost (forfeit) was paid
case class FrDiscountResumeAction(action : ForcedAction, card : Card) extends ForcedAction   // a discounted rez after its additional cost (forfeit) was paid
case class FrRezForfeitAction(self : Faction, card : Card, ctx : String) extends BaseAction("Rez")(g => g.cardElem(card) ~ " by forfeiting an agenda (alternative cost)")
case class FrSurveillanceBoostAction(self : Faction, n : Int) extends BaseAction("Surveillance Sweep: boost your link strength first")(n)
case class FrAccessPayAction(self : Faction, card : Card) extends BaseAction(g => "Gagarin Deep Space: pay 1 credit to access " ~ g.accessElem(card) ~ "?")("Pay 1 credit to access")
case class FrAccessRefuseAction(self : Faction, card : Card) extends BaseAction(g => "Gagarin Deep Space: pay 1 credit to access " ~ g.accessElem(card) ~ "?")("No action")
case class FrAccessUnpaidAction(self : Faction, card : Card) extends BaseAction(g => "Gagarin Deep Space: you cannot pay to access " ~ g.accessElem(card))("OK")
case class FrHQAccessShuffledAction(shuffled : $[Card]) extends ShuffledAction[Card] with RunStep
case class FrPhantomEndAction(card : Card) extends ForcedAction                            // the encounter with an ice that is not installed ended
case class FrArmoredBreakAction(action : Action) extends ForcedAction                      // Armored Servers: the grip cards were trashed, perform the break
case class FrFaustBreakAction(action : Action) extends ForcedAction                        // Faust: the grip card (the cost) was trashed, perform the break
case class FrPatchworkResumeAction(action : ForcedAction) extends ForcedAction             // the play / install after the Patchwork interrupt
case class StackSearchedEffect(source : Card) extends ForcedAction                         // the Runner searched the stack (emitted by every card whose effect searches it, after the search)
case class FrAccessEncounterEffect(ice : Card) extends ForcedAction                       // "when the Runner accesses this ice anywhere except in Archives, they encounter it" (Archangel, Chrysalis, Herald, Sapper)
case class FrAccessUnrezAction(ice : Card) extends ForcedAction                           // the encounter with an installed unrezzed ice accessed by the Runner ended: it is unrezzed again
case class FrEncounterNowEffect(ice : Card) extends ForcedAction                          // the Runner moves to the ice and encounters it (Formicary)
case class FrGrantedEncounterEffect(ice : Card, effects : $[ForcedAction]) extends ForcedAction   // a "when encountered" ability granted to the ice by another card (ZATO City Grid): through the encounter prevention window
case class FrGrantedResumeAction(effects : $[ForcedAction]) extends ForcedAction             // after the window: the granted ability resolves unless it was prevented
case class FrHiveSpendAction(self : Faction, card : Card, idx : Int, ctx : String, fromHive : Int) extends BaseAction(g => g.cardElem(card) ~ ": spend the virus counters from")(g => (fromHive == 0).?("this program".txt).|(fromHive.hl ~ " from Hivemind"))
// Hivemind: the virus counter cost of an ability / break / pump can be paid with the program's own counters or Hivemind's: a soft
// menu in place of the action (leaves: FrHiveCostAction) and, for the effects that spend counters (SpendVirusEffect), a choice
case class FrHiveMenuAction(self : Faction, action : UserAction) extends BaseAction(g => action.question(g))(g => action.option(g) ~ " (choose the virus counters)".txt) with Soft
case class FrHiveCostAction(self : Faction, action : UserAction, fromHive : Int) extends BaseAction(g => g.frHiveTitle(action))(g => g.frHiveLabel(action, fromHive))
case class FrHiveEffectAction(self : Faction, card : Card, n : Int, fromHive : Int) extends BaseAction(g => g.cardElem(card) ~ ": spend " ~ n.hl ~ " virus counter" ~ (n != 1).??("s") ~ " from")(g => g.frHiveSplit(card, n, fromHive))

// ---- purposes ---------------------------------------------------------------------------------------------------------
trait FrPurpose extends Purpose
case class FrSkorpiosPurpose(card : Card) extends FrPurpose
case class FrArmoredPurpose(action : Action) extends FrPurpose
case class FrUnpreventableTrashPurpose(by : Faction) extends FrPurpose
case class FrFaustPurpose(action : Action) extends FrPurpose
case class FrPatchworkPurpose(action : ForcedAction, patchwork : Card) extends FrPurpose
case class FrForfeitRezPurpose(card : Card, alt : Boolean, then : |[ForcedAction]) extends FrPurpose


trait FinalRotRules extends RotAgendaRules with PreventionRules with FixRules with RestrictionRules { game : Game =>

    // =================================================================================================================
    // sources
    // =================================================================================================================

    // the triggered abilities of c resolve between source markers (game.scala triggerEffects)
    def frSourced(c : Card, l : $[ForcedAction]) : $[ForcedAction] = if (l.none) l else (FxSourceEffect(c, true) +: l) :+ FxSourceEffect(c, false)

    // fxSources depth when each resolving subroutine fired (to know whether an ability resolves inside the subroutine)
    var frSubDepth : Map[Card, Int] = Map()

    // the card doing something now: a paid / triggered ability, a subroutine, an operation / event, an accessed card, a
    // scoring agenda (innermost first)
    def frSourceNow : |[Card] = {
        val sub = preventSubs.starting
        val src = fxSources.starting
        if (src.any && sub.forall(i => fxSources.num > frSubDepth.get(i).|(0)))
            src
        else
            sub.orElse((at(PlayZone(active)) ++ at(PlayZone(active.opponent))).%(c => pending.has(PlayCleanupAction(c))).starting)
               .orElse(run.%(r => !r.ended).flatMap(_ => pending.collectFirst { case AccessDecisionAction(c) => c }))
               .orElse(scoringAgenda)
    }

    // damage that cannot be prevented: the next damage window is marked
    var frUnpreventableNext = false
    var frUnpreventable : $[PreventCtx] = $

    // =================================================================================================================
    // "the next paid ability window after X"
    // =================================================================================================================

    // key -> 1: armed, no window yet; 2: a window is open
    var frArmedState : Map[String, Int] = Map()
    def frArm(k : String) { frArmedState += k -> 1 }
    def frArmed(k : String) : Boolean = frArmedState.contains(k)
    def frDisarm(k : String) { frArmedState -= k }

    // a window produced an ask: the armed keys see their window
    def frWindowOpened() { frArmedState = frArmedState.map { case (k, _) => k -> 2 } }
    // the window closed (passed, continued, or an action was taken)
    def frWindowClosed() { frArmedState = frArmedState.filter { case (_, v) => v != 2 } }

    def frIsAsk(c : Continue) = c match {
        case Ask(_, _) => true
        case MultiAsk(_, _) => true
        case _ => false
    }

    // =================================================================================================================
    // batches of Corp cards trashed by the Runner
    // =================================================================================================================

    var frTrashBatch : $[(Card, |[Server])] = $

    // the batch ends after the "when trashed" abilities of the trashed cards (they are pushed in front of its end marker)
    override def trashCard(c : Card, by : Faction, accessed : Boolean) {
        val corp = owner(c).isCorp && by.isRunner && location.contains(c)
        val from = zoneServer(zone(c))
        val before = zone(c)
        val first = corp && frTrashBatch.none && !pending.has(FrTrashBatchEndEffect)
        if (first)
            push(FrTrashBatchEndEffect)
        super.trashCard(c, by, accessed)
        if (corp && zone(c) != before)
            frTrashBatch :+= ((c, from))
        else if (first)
            pending = pending.diff($(FrTrashBatchEndEffect))
    }

    // =================================================================================================================
    // Skorpios Defense Systems
    // =================================================================================================================

    var frHeapSeen : Set[Card] = Set()

    def frSkorpios : |[Card] = identity.get(Corp).%(c => c.code == "13041" && !isDisabled(c) && !usedThisTurn(c, "skorpios"))

    // abilities resolved before every other triggered ability of the event (game.scala triggerEffects)
    def finalRotPreTriggers(e : Event) : $[ForcedAction] = e match {
        case DiscardedToHandSize(Runner, l) =>
            frHeapSeen ++= l
            $
        case _ if frSkorpios.any && discard(Runner).exists(c => !frHeapSeen.contains(c)) =>
            // one check for the whole batch: move it to the front
            pending = pending.%(_ != FrSkorpiosCheckEffect)
            $(FrSkorpiosCheckEffect)
        case _ => $
    }

    // =================================================================================================================
    // the check after every action
    // =================================================================================================================

    var frBlackFileActive = false
    var frRunnerScored : Set[Card] = Set()
    var frInstalledRunner : Map[Card, Zone] = Map()

    override def restrictContinue(c : Continue) : Continue = {
        frAfterAction()
        frHiveMenus(super.restrictContinue(c))
    }

    // Hivemind: an ability / break / pump whose virus counters can come from the program or Hivemind becomes a soft menu
    def frHiveMenus(c : Continue) : Continue = if (frHiveSpend.any || !installedCards(Runner).exists(_.code == "07042")) c else frMapAsk(c)(_./{
        case a @ (_ : UseAbilityAction | _ : BreakSubAction | _ : BreakSubsAction | _ : PumpAction) if frHiveChoices(a).num > 1 => FrHiveMenuAction(Runner, a)
        case a => a
    })

    def frAfterAction() {
        if (isOver)
            return
        // The Black File: when it leaves play the Corp may win at once
        val bf = activeCards(Runner).exists(x => x.code == "10099" && !isDisabled(x))
        if (frBlackFileActive && !bf && !pending.has(CheckWinAction))
            enqueueFront(CheckWinAction)
        frBlackFileActive = bf
        // cards added to the Runner's score area
        val sc = scored(Runner).toSet
        val added = scored(Runner).%(x => !frRunnerScored.contains(x))
        frRunnerScored = sc
        added.foreach(x => frTrigger(FrAddedToScoreArea(x, Runner)))
        // installed Runner cards that left play without being trashed ("when uninstalled")
        val inst = installedCards(Runner).%(x => !state(x).facedown)./(x => x -> zone(x)).toMap
        frInstalledRunner.foreach { case (x, z) =>
            if (!inst.contains(x) && location.contains(x) && !zone(x).is[DiscardZone] && !(isInstalled(x) && state(x).facedown))
                frTrigger(FrUninstalled(x, z))
        }
        frInstalledRunner = inst
        // Skorpios: cards that reached the heap without an event
        if (frSkorpios.none)
            frHeapSeen = discard(Runner).toSet
        else if (discard(Runner).exists(x => !frHeapSeen.contains(x)) && !pending.has(FrSkorpiosCheckEffect))
            enqueueFront(FrSkorpiosCheckEffect)
    }

    def enqueueFront(a : ForcedAction) { pending = a +: pending }

    // trigger an event from the check after an action (its effects are resolved next)
    def frTrigger(e : Event) {
        val l = triggerEffects(e)
        if (l.any) {
            pending = l ++ pending
            reopenWindows()
        }
    }

    // =================================================================================================================
    // virtual tags ("the Runner is considered to have 1 additional tag": Acme Consulting, MCA Informant)
    // =================================================================================================================

    var frVirtualTags = 0

    def frRemovableTags : Int = (tags - frVirtualTags).atLeast(0)

    // =================================================================================================================
    // Hivemind: virus counters on Hivemind are considered hosted on every other virus program
    // =================================================================================================================

    def frHiveminds(c : Card) : $[Card] = (owner(c).isRunner && c.data.isProgram && has(c, "Virus") && c.code != "07042").??(installedCards(Runner).%(h => h.code == "07042" && isActive(h) && !isDisabled(h)))
    def frHiveCounters(c : Card) : Int = frHiveminds(c)./(h => counter(h, "virus")).sum

    // virus counters spent from Hivemind by the next VirusCost payment (chosen by FrHiveSpendAction / FrHiveCostAction)
    var frHiveSpend : |[Int] = None

    // the numbers of virus counters that may come from Hivemind when c pays n (empty: no choice, the program's own first)
    def frHiveOptions(c : Card, n : Int) : $[Int] = if (n <= 0) $ else {
        val own = counter(c, "virus")
        val hive = frHiveCounters(c)
        if (hive == 0 || own == 0) $ else ((n - own).atLeast(0) to n.atMost(hive)).$.%(k => own >= n - k)
    }

    def frVirusOf(l : $[Cost]) : Int = l.of[VirusCost]./(_.n).sum

    // the card and the virus counters a user action pays (abilities, breaks, pumps)
    def frVirusCostOf(a : UserAction) : |[(Card, Int)] = frVirusCostRaw(a).%(_._2 > 0)

    def frVirusCostRaw(a : UserAction) : |[(Card, Int)] = a match {
        case UseAbilityAction(Runner, c, i, _) => impl(c).abilities(c).lift(i)./(x => (c, frVirusOf(x.costs)))
        case BreakSubAction(Runner, b, bi, _, _) => impl(b).breaks(b).lift(bi)./(br => (b, frVirusOf(breakCostsWith(b, br.costs))))
        case BreakSubsAction(Runner, b, bi, _, l) => impl(b).breaks(b).lift(bi)./(br => (b, frVirusOf(breakCostsWith(b, breakCosts(br, l.num)))))
        case PumpAction(Runner, b, pi) => impl(b).pumps(b).lift(pi)./(p => (b, frVirusOf(pumpCostsWith(b, p.costs))))
        case _ => None
    }

    def frHiveChoices(a : UserAction) : $[Int] = frVirusCostOf(a)./~ { case (c, n) => frHiveOptions(c, n) }

    def frHiveSplit(c : Card, n : Int, k : Int) : Elem =
        ((n - k > 0).$((n - k).hl ~ " from " ~ cardElem(c)) ++ (k > 0).$(k.hl ~ " from Hivemind")).join(", ")

    def frHiveTitle(a : UserAction) : Elem = a.option(this) ~ ": spend the virus counters from"
    def frHiveLabel(a : UserAction, k : Int) : Elem = frVirusCostOf(a)./{ case (c, n) => frHiveSplit(c, n, k) }.|(k.hl)

    def frHiveAsk(a : UserAction, cancel : Boolean) : Continue = {
        val ask = Ask(Runner).add(frHiveChoices(a)./(k => FrHiveCostAction(Runner, a, k)))
        cancel.?(ask.cancel).|(ask)
    }

    // the virus counters an effect spends for c (SpendVirusEffect(c, n, false)): own first by default, Hivemind's only for virus programs
    def frSpendVirus(c : Card, n : Int, fromHive : |[Int]) {
        val hive = fromHive.|((n - counter(c, "virus")).atLeast(0)).atMost(frHiveCounters(c)).atMost(n)
        addCounter(c, "virus", -(n - hive).atMost(counter(c, "virus")))
        var left = hive
        frHiveminds(c).foreach { h =>
            val m = left.atMost(counter(h, "virus"))
            if (m > 0) {
                addCounter(h, "virus", -m)
                left -= m
                log(Runner, "spends", m.hl, "virus counter" + (m != 1).??("s"), "from", h, "for", c)
            }
        }
    }

    override def payCosts(f : Faction, costs : $[Cost], self : |[Card], spend : |[Spend] = None) {
        val (virus, rest) = costs.partition(_.is[VirusCost])
        super.payCosts(f, rest, self, spend)
        virus.foreach {
            case VirusCost(n) => self.foreach { c =>
                val hive = frHiveSpend.|((n - counter(c, "virus")).atLeast(0)).atMost(frHiveCounters(c)).atMost(n)
                frHiveSpend = None
                addCounter(c, "virus", -(n - hive))
                var left = hive
                frHiveminds(c).foreach { h =>
                    val m = left.atMost(counter(h, "virus"))
                    if (m > 0) {
                        addCounter(h, "virus", -m)
                        left -= m
                        log(Runner, "spends", m.hl, "virus counter" + (m != 1).??("s"), "from", h, "for", c)
                    }
                }
            }
            case _ =>
        }
    }

    // =================================================================================================================
    // card-specific state
    // =================================================================================================================

    var frClickDrawContext = false                          // the Corp spent [click] to draw: the basic draw action or a [click] ability (Surveillance Network Key)
    var frSurveillanceBoost : |[Int] = None                 // Surveillance Sweep: the Runner's boost chosen first
    var frRdAccessedThisRun = 0                             // Psych Mike
    var frPhantom : $[Card] = $                             // ice encountered while not installed (Chrysalis, ...)
    var frAccessRezzed : $[Card] = $                        // installed unrezzed ice encountered on access (considered rezzed for the encounter)
    var frGagarinPaid : $[Card] = $
    var frHQLeft = 0                                        // HQ cards still to access in the current breach (drawn one at a time)
    var frRDLeft = 0                                        // R&D cards still to access in the current breach (from the top, one at a time)
    var frDynRun : |[Run] = None
    var frHQPicked : |[Card] = None
    var frHQSnapshot : $[Card] = $
    var frSahasraraLocked : |[Card] = None                  // Sahasrara's credits paid for the program being installed
    var frPreFirstTurnDone : $[Faction] = $
    var frAltRez : |[Card] = None                           // Tithonium is being rezzed by forfeiting an agenda
    var frRezCostPaid : |[Card] = None                      // the additional rez cost (forfeit) of the card was paid
    var frTyrPrevented : Set[(Card, Int)] = Set()           // subroutines Tyr's Hand prevented from being broken (break window)
    var frArmoredPaid = false
    var frFaustPaid = false
    var frPatchworkBypass = false
    var frPatchworkSkip : |[Card] = None                    // the basic install action already offered Patchwork for the card
    var frFromHeap : Set[Card] = Set()                      // cards being installed from the heap (Personality Profiles)
    var frClickDrawn = 0
    var frDiscountInner = false
    var frGranted : $[PreventCtx] = $

    // ---- helpers --------------------------------------------------------------------------------------------------------

    // Harishchandra Ent.: "While the Runner is tagged, they play with the grip revealed" (the UI shows the grip to the Corp)
    def frGripRevealed : Boolean = tagged && identity.get(Corp).exists(c => c.code == "10107" && !isDisabled(c))

    def frActiveCode(f : Faction, code : String) : Boolean = activeCards(f).exists(c => c.code == code && !isDisabled(c))
    def frCurrent(code : String) : Boolean = (at(PlayZone(Corp)) ++ at(PlayZone(Runner))).exists(c => c.code == code && !isDisabled(c))

    // Starlight Crusade Funding: the additional costs of double events are ignored
    def frStarlight : Boolean = frActiveCode(Runner, "04069")

    // Hernando Cortez: an additional cost to rez ice while the Corp has 10 or more credits
    def frRezSurcharge(c : Card) : Int = (c.data.isIce && credits(Corp) >= 10).??(activeCards(Runner).%(x => x.code == "11004" && !isDisabled(x)).num * subs(c).num)

    // Armored Servers used this run
    def frArmored : Boolean = run.exists(r => !r.ended) && scored(Corp).exists(c => c.code == "13042" && !isDisabled(c) && state(c).memo == rotRunKey)

    // the scored agendas the Corp can forfeit
    def frForfeitable : $[Card] = scored(Corp).%(_.data.isAgenda)

    def frDiscountPerform(a : ForcedAction) : Continue = {
        frDiscountInner = true
        try performInternal(a, NoVoid) finally frDiscountInner = false
    }

    def frCanRezAt(c : Card) : Boolean = rezBlock(c, false).none && impl(c).canRez(c)

    // ---- rez: additional costs (Hernando Cortez, Ibrahim Salem) and the alternative cost of Tithonium -------------------

    override def rezCard(c : Card, ignoreCost : Boolean) {
        if (isRezzed(c) || ignoreCost || frRezCostPaid.has(c))
            super.rezCard(c, ignoreCost)
        else if (c.code == "10109" && rezBlock(c, false).none) {
            // Ibrahim Salem: forfeit an agenda as an additional cost (before the rez)
            if (frForfeitable.none || !canSpend(Corp, rezCost(c), Spend.rez(c)))
                log(Corp, "cannot pay the additional cost to rez", hiddenCorpElem(c))
            else
                push(ChooseCardEffect(Corp, frForfeitable, FrForfeitRezPurpose(c, false, None), "Ibrahim Salem: forfeit an agenda (additional cost to rez)", false))
        }
        else {
            val s = frRezSurcharge(c)
            if (s > 0 && rezBlock(c, false).none) {
                if (!canSpend(Corp, rezCost(c) + s, Spend.rez(c))) {
                    log(Corp, "cannot pay the additional cost to rez", hiddenCorpElem(c), "(Hernando Cortez)")
                    return
                }
                spendCredits(Corp, s, Spend.rez(c))
                log(Corp, "pays", s.credits, "as an additional cost to rez", c, "(Hernando Cortez)")
            }
            super.rezCard(c, ignoreCost)
        }
    }

    override def rezOptions(f : Faction, ctx : String, approached : |[Card]) : $[UserAction] = {
        val l = super.rezOptions(f, ctx, approached)./{
            case a @ RezAction(_, c, _) if frRezSurcharge(c) > 0 => a.!(!canSpend(Corp, rezCost(c) + frRezSurcharge(c), Spend.rez(c)), "cannot pay the additional cost (Hernando Cortez)")
            case a @ RezAction(_, c, _) if c.code == "10109" => a.!(frForfeitable.none, "no agenda to forfeit")
            case a => a
        }
        // Tithonium: forfeit an agenda instead of paying the rez cost
        val alt = l./~{
            case RezAction(_, c, _) if c.code == "12098" => |(c)
            case UnavailableReasonAction(RezAction(_, c, _), _) if c.code == "12098" => |(c)
            case _ => None
        }./(c => FrRezForfeitAction(f, c, ctx).!(frForfeitable.none, "no agenda to forfeit").!(!frCanRezAt(c), "cannot rez").!(frRezSurcharge(c) > 0 && !canSpend(Corp, frRezSurcharge(c), Spend.rez(c)), "cannot pay the additional cost"))
        l ++ alt
    }

    // ---- virus counters: Hivemind (which counters an ability with a virus counter cost spends) -------------------------
    override def performAbilities : PartialFunction[Action, Continue] = {
        val base = super.performAbilities
        ({
            // Hivemind: when both the program and Hivemind could pay the virus counters, the Runner chooses (the asks offer the soft
            // FrHiveMenuAction in place of the action; an action performed without it asks here, without cancelling)
            case a @ UseAbilityAction(Runner, c, i, ctx) if frHiveSpend.none && frHiveChoices(a).num > 1 => frHiveAsk(a, false)
            case FrHiveSpendAction(f, c, i, ctx, k) =>
                frHiveSpend = |(k)
                frUseAbility(UseAbilityAction(f, c, i, ctx))
            case FrHiveMenuAction(f, a) => frHiveAsk(a, true)
            case FrHiveCostAction(f, a, k) =>
                frHiveSpend = |(k)
                performInternal(a, NoVoid)

            case a @ UseAbilityAction(f, c, i, ctx) => frUseAbility(a)

            case a @ CheckMemoryAction =>
                val r = base(a)
                frSahasraraLocked match {
                    case Some(s) if memoryFree < 0 => frMapAsk(r)(_./{
                        case x @ TrashProgramForMemoryAction(_, p) if p == s => x.!(true, "its credits paid for this install")
                        case x => x
                    })
                    case _ =>
                        frSahasraraLocked = None
                        r
                }
        } : PartialFunction[Action, Continue]) orElse base
    }

    // the numbers of virus counters the Runner may spend from Hivemind for the ability
    def frHiveChoice(c : Card, i : Int) : $[Int] = impl(c).abilities(c).lift(i)./(x => frHiveOptions(c, frVirusOf(x.costs))).|($)

    // Net Watchlist: 2 credits as an additional cost to use an icebreaker (its paid abilities; breaks and pumps through
    // CardImpl.icebreakerSurcharge)
    def frWatchlist(c : Card) : Int = (owner(c).isRunner && isIcebreaker(c) && frCurrent("14023")).??(2)

    def frUseAbility(a : UseAbilityAction) : Continue = {
        val n = frWatchlist(a.card)
        if (n > 0 && a.self.isRunner) {
            if (!canSpend(Runner, n, Spend.ability(a.card))) {
                log(Runner, "cannot pay the additional cost (Net Watchlist)")
                return reask(a.ctx)
            }
            spendCredits(Runner, n, Spend.ability(a.card))
            log(Runner, "pays", n.credits, "to use", a.card, "(Net Watchlist)")
        }
        // Surveillance Network Key: the Corp spends [click] on an ability
        if (a.self.isCorp && impl(a.card).abilities(a.card).lift(a.idx).exists(_.click)) {
            frClickDrawContext = true
            frClickDrawn = 0
        }
        super.performAbilities(a)
    }

    override def abilityOptions(f : Faction, ctx : String, allowClick : Boolean) : $[UserAction] = {
        val l = super.abilityOptions(f, ctx, allowClick)
        if (f.isRunner && frCurrent("14023"))
            l./{
                case a @ UseAbilityAction(_, c, i, _) if frWatchlist(c) > 0 => a.!(impl(c).abilities(c).lift(i).exists(x => !canPay(Runner, x.costs.%!(_.is[CreditCost]) :+ CreditCost(x.costs.of[CreditCost]./(_.n).sum + 2), |(c))), "cannot pay the additional cost (Net Watchlist)")
                case a => a
            }
        else
            l
    }

    def frMapAsk(c : Continue)(f : $[UserAction] => $[UserAction]) : Continue = c match {
        case Ask(x, l) => Ask(x, f(l))
        case MultiAsk(asks, p) => MultiAsk(asks./(a => Ask(a.faction, f(a.actions))), p)
        case c => c
    }

    // ---- turn: windows, Surveillance Network Key, Enhanced Login Protocol, virtual tags, "before your first turn" ---------
    override def performTurn : PartialFunction[Action, Continue] = {
        val base = super.performTurn
        ({
            case a @ StartTurnAction(f) if !frPreFirstTurnDone.has(f) =>
                frPreFirstTurnDone :+= f
                push(a)
                trigger(FrBeforeFirstTurn(f))
                NextAction

            case a @ ActionPhaseAction(f) =>
                frClickDrawContext = false
                val r = base(a)
                if (frIsAsk(r))
                    frWindowOpened()
                frMapAsk(r)(_./{
                    case x @ RemoveTagAction(Runner) if frVirtualTags > 0 && frRemovableTags == 0 => x.!(true, "the tag cannot be removed")
                    case x => x
                })

            case a @ RunMenuAction(f) if frCurrent("06022") && reg("click-run") == 0 && clicks(Runner) < 2 =>
                frMapAsk(base(a))(_./{
                    case x @ RunAction(_, _) => x.!(true, "Enhanced Login Protocol: the first run costs [click]")
                    case x => x
                })

            case a @ ClickDrawAction(f) =>
                frWindowClosed()
                if (f.isCorp) {
                    frClickDrawContext = true
                    frClickDrawn = 0
                }
                base(a)

            // (an operation played with the basic action is not "spending [click] to draw": Surveillance Network Key counts the
            // basic draw action and [click] abilities only)
            case a @ PlayCardAction(f, c) =>
                frWindowClosed()
                frClickDrawContext = false
                base(a)

            case a @ RunnerInstallAction(f, c, _, _) =>
                frWindowClosed()
                frPatchworkSkip = |(c)
                base(a)

            case a @ (ClickCreditAction(_) | CorpInstallAction(_, _, _) | AdvanceAction(_, _) | RunAction(_, _) | RemoveTagAction(_) | PurgeAction(_) | TrashResourceAction(_, _) | EndTurnClickAction(_)) =>
                frWindowClosed()
                base(a)

            case a @ DiscardCardsAction(Runner, l) =>
                val r = base(a)
                frHeapSeen ++= l
                r
        } : PartialFunction[Action, Continue]) orElse base
    }

    // ---- effects --------------------------------------------------------------------------------------------------------
    override def performEffects : PartialFunction[Action, Continue] = {
        val base = super.performEffects
        ({
            // the source of the damage (the window reads nextDamageSource) and unpreventable damage
            case a @ PreventOpenDamageAction(k, n) =>
                if (nextDamageSource.none && n > 0)
                    nextDamageSource = frSourceNow
                val unp = frUnpreventableNext
                frUnpreventableNext = false
                val before = preventTop
                val r = base(a)
                if (unp)
                    preventTop.%(x => !before.exists(_ eq x)).foreach(x => frUnpreventable :+= x)
                r

            case a @ PreventInterruptAction(k, 1) =>
                preventTop.foreach { ctx =>
                    if (frUnpreventable.exists(_ eq ctx)) {
                        ctx.unpreventable = true
                        frUnpreventable = frUnpreventable.%!(_ eq ctx)
                    }
                }
                base(a)

            // Tyr's Hand: remember the subroutines prevented from being broken (the break action leaves them unbroken)
            case a @ PreventBreakOfEffect(i) =>
                preventCtx("break").foreach(ctx => ctx.source.foreach(ice => if (ctx.subs.has(i)) frTyrPrevented += ((ice, i))))
                base(a)

            // Tyr's Hand: breaks by card effects (Gbahali, Kongamato) are interruptible too
            case a @ BreakSubIndexEffect(ice, i, b) if !breakBypass && run.exists(r => !r.ended && r.ice.has(ice)) && !state(ice).brokenSubs.has(i) && breakInterruptible(ice) =>
                openBreak(a, ice, $(i))
            case BreakSubIndexEffect(ice, i, b) if breakBypass && frTyrPrevented.contains((ice, i)) =>
                frTyrPrevented = frTyrPrevented.filter(_._1 != ice)
                log("The subroutine", subs(ice).lift(i)./(_.label).|("?").hl, "on", ice, "is not broken")
                NextAction

            // Hivemind: virus counters spent by an effect for a virus program (SpendVirusEffect(c, n, false)): the Runner chooses
            // between the program's counters and Hivemind's when both could pay; Hivemind's counters are spent only for virus programs
            case SpendVirusEffect(c, n, false) =>
                if (frHiveSpend.none && frHiveOptions(c, n).num > 1)
                    Ask(Runner).add(frHiveOptions(c, n)./(k => FrHiveEffectAction(Runner, c, n, k)))
                else {
                    val k = frHiveSpend
                    frHiveSpend = None
                    frSpendVirus(c, n, k)
                    NextAction
                }

            case FrHiveEffectAction(f, c, n, k) =>
                frHiveSpend = None
                frSpendVirus(c, n, |(k))
                NextAction

            // virtual tags cannot be removed
            case RemoveTagsEffect(n) if frVirtualTags > 0 && n > frRemovableTags =>
                if (frRemovableTags > 0) base(RemoveTagsEffect(frRemovableTags)) else NextAction

            // Progenitor: 1 virus counter of the hosted program is kept when virus counters are purged
            case a @ PurgeEffect =>
                val keep = installedCards(Runner).%(p => p.code == "07043" && isActive(p) && !isDisabled(p))./~(p => hosted(p).%(h => counter(h, "virus") > 0))
                val r = base(a)
                keep.foreach { h =>
                    if (location.contains(h) && counter(h, "virus") == 0) {
                        addCounter(h, "virus", 1)
                        log("Progenitor prevents 1 virus counter on", h, "from being purged")
                    }
                }
                r

            // Genetics Pavilion: no more than 2 cards drawn during each of the Runner's turns (draw bonuses included)
            case DrawNowEffect(Runner, n) if n > 0 && active.isRunner && rotLive("08113").any =>
                val bonus = allActive./(x => impl(x).drawBonus(x, Runner)).sum
                val allowed = (2 - reg("drawn-Runner")).atLeast(0)
                if (n + bonus <= allowed)
                    base(DrawNowEffect(Runner, n))
                else {
                    log("Genetics Pavilion: the Runner cannot draw more than 2 cards this turn")
                    val l = deck(Runner).take(allowed)
                    l.foreach(c => moveTo(c, HandZone(Runner)))
                    if (l.any) {
                        log(Runner, "draws", l.num.cards)
                        regInc("drawn-Runner", l.num)
                        trigger(CardsDrawn(Runner, l))
                    }
                    NextAction
                }

            // Surveillance Sweep: during a run the Runner spends credits first for each trace
            case a @ TraceCorpBoostAction(b, s, fl) if frSurveillanceBoost.none && run.exists(r => !r.ended) && frCurrent("09023") =>
                val max = noSpending(Runner).?(0).|(creditsFor(Runner, Spend.trace))
                Ask(Runner).group("Trace strength " ~ b.hl ~ ", your link is " ~ (rotLinkZero && reg("rot-traces") <= 1).?(0).|(link).hl ~ " - Surveillance Sweep: you boost first").add(0.to(max).$./(n => FrSurveillanceBoostAction(Runner, n)))

            case FrSurveillanceBoostAction(f, n) =>
                frSurveillanceBoost = |(n)
                log(Runner, "commits", n.credits, "to the trace first (Surveillance Sweep)")
                performInternal(TraceCorpBoostAction(traceBase, traceSuccess, traceFail), NoVoid)

            case TraceRunnerBoostAction(st, s, fl) if frSurveillanceBoost.any =>
                val n = frSurveillanceBoost.get.atMost(creditsFor(Runner, Spend.trace))
                frSurveillanceBoost = None
                performInternal(TraceRunnerAction(Runner, n), NoVoid)

            // Security Leak / The Price of Freedom also apply to cards advanced by card effects
            case AdvanceTimesEffect(c, n) if hasFlag("no-advance") =>
                log("The Corp cannot advance cards this turn")
                NextAction
            case AdvanceTimesEffect(c, n) if frCurrent("14009") && n > 0 =>
                val m = n.atMost(creditsFor(Corp, Spend.advance(c)))
                if (m > 0) {
                    spendCredits(Corp, m, Spend.advance(c))
                    log(Corp, "pays", m.credits, "more to advance (Security Leak)")
                }
                if (m < n)
                    log(Corp, "cannot pay the additional cost of", (n - m).hl, "advance" + (n - m != 1).??("s"))
                if (m > 0) performRotOperations(AdvanceTimesEffect(c, m)) else NextAction

            // rezzes paid through a discount (the rez itself then ignores the costs): the additional costs still apply
            // (Ibrahim Salem's forfeit, Hernando Cortez's credits, Hacktivist Meeting's random trash from HQ)
            case a0 @ (DiscountRezEffect(_, _) | RezDiscountEffect(_, _)) if !frDiscountInner =>
                val a = a0.asInstanceOf[ForcedAction]
                val (c, d) = a match { case DiscountRezEffect(x, n) => (x, n) ; case RezDiscountEffect(x, n) => (x, n) ; case _ => throw new Error("?") }
                if (isRezzed(c) || rezBlock(c, false).any)
                    frDiscountPerform(a)
                else if (c.code == "10109" && !frRezCostPaid.has(c)) {
                    if (frForfeitable.none)
                        log(Corp, "cannot pay the additional cost to rez", hiddenCorpElem(c))
                    else
                        push(ChooseCardEffect(Corp, frForfeitable, FrForfeitRezPurpose(c, false, |(FrDiscountResumeAction(a, c))), "Ibrahim Salem: forfeit an agenda (additional cost to rez)", false))
                    NextAction
                }
                else {
                    val s = frRezSurcharge(c)
                    if (s > 0 && !canSpend(Corp, (rezCost(c) - d).atLeast(0) + s, Spend.rez(c))) {
                        log(Corp, "cannot pay the additional cost to rez", hiddenCorpElem(c), "(Hernando Cortez)")
                        NextAction
                    }
                    else {
                        if (s > 0) {
                            spendCredits(Corp, s, Spend.rez(c))
                            log(Corp, "pays", s.credits, "as an additional cost to rez", c, "(Hernando Cortez)")
                        }
                        val was = isRezzed(c)
                        val r = frDiscountPerform(a)
                        if (!was && isRezzed(c) && !c.data.isIce && frCurrent("08021")) {
                            log(Corp, "trashes a random card from HQ (Hacktivist Meeting)")
                            push(RandomTrashFromHandEffect(Corp, 1))
                        }
                        r
                    }
                }

            // the encounter-on-access ice: the Runner encounters the ice where it is (not installed)
            case PhantomEncounterEffect(c) =>
                run match {
                    case Some(r) if !r.ended && !isInstalled(c) && c.data.isIce =>
                        frPhantom +:= c
                        state(c).rezzed = true
                        state(c).seen = true
                        r.forced +:= c
                        r.forcedPrev +:= ((r.ice, r.phase))
                        r.ice = |(c)
                        r.phase = EncounterPhase
                        r.bypass = false
                        state(c).brokenSubs = $
                        log(Runner, "encounters", c)
                        push(EncounterAction(c), FrPhantomEndAction(c))
                        pushAll(encounterAbility(c))
                        trigger(EncounterIce(c))
                    case _ =>
                }
                NextAction
        } : PartialFunction[Action, Continue]) orElse base
    }

    // ---- cards: Patchwork for installs / plays by card effects; installs from the heap ----------------------------------
    override def performCards : PartialFunction[Action, Continue] = {
        val base = super.performCards
        ({
            case a @ RunnerInstallEffect(c, h, false) if frPatchworkSkip.has(c) =>
                frPatchworkSkip = None
                frInstallBookkeeping(c)(base(a))

            case a @ RunnerInstallEffect(c, h, false) if !frPatchworkBypass && !location.get(c).exists(inPlayZone) && patchworkFor(c, hostedInstallCost(c, h)).any =>
                frOpenPatchwork(a, c, patchworkFor(c, hostedInstallCost(c, h)).get)

            case a @ RunnerInstallEffect(c, h, _) =>
                frPatchworkSkip = None
                frInstallBookkeeping(c)(base(a))

            case a : ForcedAction if frPatchworkWanted(a).any => frPatchworkOpen(a)
        } : PartialFunction[Action, Continue]) orElse base
    }

    // the plays / installs by card effects that pay a cost: Patchwork may interrupt them
    def frPatchworkTarget(a : ForcedAction) : |[(Card, Int)] = a match {
        case PlayCardEffect(Runner, c, true, _) => |((c, playCost(c)))
        case PlayFreeEffect(c) if owner(c).isRunner => |((c, playCost(c)))
        case PlayCardFreeEffect(c) if owner(c).isRunner => |((c, playCost(c)))
        case RunnerInstallPayingEffect(c, h, d) => |((c, (installCost(c, None) - d).atLeast(0)))
        case InstallDiscountEffect(c, d) => |((c, (installCost(c, None) - d).atLeast(0)))
        case RunnerInstallDiscountEffect(c, d) if !impl(c).mustBeHosted(c) => |((c, (installCost(c, None) - d).atLeast(0)))
        case _ => None
    }

    // (pure: used in the guards of the partial functions)
    def frPatchworkWanted(a : ForcedAction) : |[(Card, Card)] = if (frPatchworkBypass) None else frPatchworkTarget(a) match {
        case Some((c, cost)) if location.contains(c) && !location.get(c).exists(inPlayZone) => patchworkFor(c, cost)./(pw => (c, pw))
        case _ => None
    }

    def frPatchworkOpen(a : ForcedAction) : Continue = frPatchworkWanted(a) match {
        case Some((c, pw)) => frOpenPatchwork(a, c, pw)
        case None => NextAction
    }

    override def performPHTAI : PartialFunction[Action, Continue] = {
        val base = super.performPHTAI
        ({
            case a : ForcedAction if frPatchworkWanted(a).any => frPatchworkOpen(a)
        } : PartialFunction[Action, Continue]) orElse base
    }

    def frInstallBookkeeping(c : Card)(r : => Continue) : Continue = {
        frSahasraraLocked = None
        if (zone(c) == DiscardZone(Runner))
            frFromHeap += c
        val sah = installedCards(Runner).%(x => x.code == "03047")./(x => x -> counter(x, "credit")).toMap
        val res = r
        if (c.data.isProgram)
            sah.foreach { case (x, n) => if (counter(x, "credit") < n && location.contains(x)) frSahasraraLocked = |(x) }
        res
    }

    def frOpenPatchwork(a : ForcedAction, c : Card, pw : Card) : Continue = {
        push(ChooseCardEffect(Runner, hand(Runner).%(_ != c), FrPatchworkPurpose(a, pw), "Patchwork: trash a card from the grip to " + c.data.isEvent.?("play").|("install") + " " + promptTitle(c) + " paying 2 credits less?", true), FrPatchworkResumeAction(a))
        NextAction
    }

    // ---- run: phantom encounters, Tyr's Hand, Armored Servers, Formicary --------------------------------------------------
    override def performRun : PartialFunction[Action, Continue] = {
        val base = super.performRun
        ({
            case a @ FireSubAction(c, i) =>
                frSubDepth += c -> fxSources.num
                base(a)

            // Hivemind: a break / pump paying virus counters both the program and Hivemind could pay (performed without the menu)
            case a @ BreakSubAction(Runner, _, _, _, _) if frHiveSpend.none && !breakBypass && frHiveChoices(a).num > 1 => frHiveAsk(a, false)
            case a @ BreakSubsAction(Runner, _, _, _, _) if frHiveSpend.none && !breakBypass && frHiveChoices(a).num > 1 => frHiveAsk(a, false)
            case a @ PumpAction(Runner, _, _) if frHiveSpend.none && frHiveChoices(a).num > 1 => frHiveAsk(a, false)

            case a @ EncounterAction(c) if frPhantom.has(c) && run.exists(r => !r.ended && !r.bypass) && !isInstalled(c) =>
                restrictContinue(fxRsvpAsk(fxFilterBreaks(frPhantomEncounterAsk(c))))

            case a @ ContinueEncounterAction(f, c) =>
                frWindowClosed()
                base(a)

            // Armored Servers: trash 1 card from the grip for each subroutine broken, before the break
            case a @ BreakSubAction(f, b, bi, c, i) if frArmored && !frArmoredPaid && !breakBypass => frArmoredAsk(a, 1)
            case a @ BreakSubsAction(f, b, bi, c, l) if frArmored && !frArmoredPaid && !breakBypass => frArmoredAsk(a, l.num)
            case a @ BioroidBreakSubAction(f, c, i) if frArmored && !frArmoredPaid && !breakBypass => frArmoredAsk(a, 1)

            // Faust: "Trash a card from your grip: break 1 subroutine" (the card is trashed as the cost, before the break)
            case a @ BreakSubAction(f, b, bi, c, i) if b.code == "08061" && !frFaustPaid && !breakBypass =>
                if (hand(Runner).none)
                    EncounterAction(c)
                else {
                    push(ChooseCardEffect(Runner, hand(Runner), FrFaustPurpose(a), "Faust: choose a card in the grip to trash (cost of the break)", false))
                    NextAction
                }

            // Tyr's Hand: the prevented subroutines are not broken (the break costs are paid; SubsBroken lists only the broken ones)
            case BreakSubAction(f, b, bi, c, i) if breakBypass && frTyrPrevented.contains((c, i)) =>
                val br = impl(b).breaks(b).apply(bi)
                windowPassed = $
                payCosts(f, breakCostsWith(b, br.costs), |(b), |(Spend.break(b, c)))
                fxUsedThisRun += b
                frTyrPrevented = frTyrPrevented.filter(_._1 != c)
                log(Runner, "uses", b, "but", subs(c).lift(i)./(_.label).|("the subroutine").hl, "on", c, "is not broken")
                EncounterAction(c)
            case BreakSubsAction(f, b, bi, c, l) if breakBypass && l.exists(i => frTyrPrevented.contains((c, i))) =>
                val br = impl(b).breaks(b).apply(bi)
                val ok = l.%(i => !frTyrPrevented.contains((c, i)))
                frTyrPrevented = frTyrPrevented.filter(_._1 != c)
                windowPassed = $
                payCosts(f, breakCostsWith(b, breakCosts(br, l.num)), |(b), |(Spend.break(b, c)))
                fxUsedThisRun += b
                state(c).brokenSubs ++= ok
                log(Runner, "uses", b, "to break", ok.num.hl, "subroutine" + (ok.num != 1).??("s"), "on", c)
                push(EncounterAction(c))
                if (ok.any) {
                    pushAll(br.after)
                    trigger(SubsBroken(c, ok, b))
                }
                NextAction
            case BioroidBreakSubAction(f, c, i) if breakBypass && frTyrPrevented.contains((c, i)) =>
                frTyrPrevented = frTyrPrevented.filter(_._1 != c)
                log("The subroutine", subs(c).lift(i)./(_.label).|("?").hl, "on", c, "is not broken")
                NextAction

            case a @ EncounterAction(c) =>
                val r = base(a)
                if (frIsAsk(r))
                    frWindowOpened()
                if (frArmored)
                    frMapAsk(r)(_./{
                        case x @ BreakSubsAction(Runner, _, _, _, l) if hand(Runner).num < l.num => x.!(true, "Armored Servers: not enough cards in the grip")
                        case x @ UseRunnerAbilityAction(Runner, _, _, _) if hand(Runner).none => x.!(true, "Armored Servers: no card in the grip")
                        case x => x
                    })
                else
                    r
        } : PartialFunction[Action, Continue]) orElse base
    }

    def frArmoredAsk(a : Action, k : Int) : Continue = {
        if (hand(Runner).num < k) {
            log(Runner, "cannot pay the additional cost to break (Armored Servers)")
            run./~(_.ice)./(EncounterAction(_)).|(NextAction)
        }
        else {
            push(ChooseCardsEffect(Runner, hand(Runner), k, k, FrArmoredPurpose(a), "Armored Servers: trash " + k + " card" + (k != 1).??("s") + " from the grip to break"))
            NextAction
        }
    }

    // the encounter ask of a piece of ice that is not installed (RunRules.EncounterAction requires an installed ice)
    def frPhantomEncounterAsk(c : Card) : Continue = {
        val subs = game.subs(c)
        val unbroken = unbrokenSubs(c)
        val str = iceStrength(c)
        val breakers = (installedCards(Runner) ++ identity.get(Runner).$).%(b => isActive(b) && (impl(b).breaks(b).any || impl(b).pumps(b).any))
        val breakOptions = breakers.distinct./~ { b =>
            impl(b).breaks(b).indexed./~ { (br, bi) =>
                val typeOk = (br.subtypes.none || br.subtypes.exists(t => has(c, t))) && impl(c).canBreakWith(c, b) && !allActive.exists(x => impl(x).preventsBreak(x, c, b))
                val strOk = !isIcebreaker(b) || breakerStrength(b) >= str
                val payOk = canPay(Runner, breakCostsWith(b, br.costs), |(b), |(Spend.break(b, c)))
                val allowed = allActive.forall(x => impl(x).breakAllowed(x, b, c))
                if (!typeOk || unbroken.none || !allowed)
                    $
                else {
                    val singles = unbroken./(i => BreakSubAction(Runner, b, bi, c, i).!(!strOk, "strength too low").!(!payOk, "cannot pay"))
                    val multiSubs = (br.n == 0).?(unbroken).|(unbroken.take(br.n))
                    val multi = (br.n != 1 && unbroken.num > 1).$(BreakSubsAction(Runner, b, bi, c, multiSubs).!(!strOk, "strength too low").!(!canPay(Runner, breakCostsWith(b, breakCosts(br, multiSubs.num)), |(b), |(Spend.break(b, c))), "cannot pay"))
                    singles ++ multi
                }
            }
        }
        val pumpOptions = breakers.distinct./~ { b =>
            impl(b).pumps(b).indexed./ { (p, pi) =>
                PumpAction(Runner, b, pi).!(!canPay(Runner, pumpCostsWith(b, p.costs), |(b), |(Spend.pump(b, |(c)))), "cannot pay")
            }
        }
        val runnerAsk = Ask(Runner)
            .group("Encountering " ~ cardElem(c) ~ " (strength " ~ str.hl ~ ")" ~ Break ~ subs.indexed./((s, i) => (unbroken.has(i).?(("[sub] " + s.label).hl).|(("[sub] " + s.label).styled(styles.subBroken)))).join(Break))
            .add(breakOptions)
            .add(pumpOptions)
            .add(abilityOptions(Runner, "encounter", false))
            .add(ContinueEncounterAction(Runner, c))
        frWindowOpened()
        windowAsk(Corp, "encounter", takeActed()) match {
            case Some(ask) => MultiAsk($(runnerAsk, ask))
            case None => runnerAsk
        }
    }

    // ---- access: Gagarin Deep Space -----------------------------------------------------------------------------------
    def frGagarin : Boolean = identity.get(Corp).exists(c => c.code == "07002" && !isDisabled(c))

    def frRemoteRoot(c : Card) : Boolean = zone(c) match {
        case RootZone(s) => s.isRemote
        case HostedZone(h) => frRemoteRoot(h)
        case _ => false
    }

    override def performAccess : PartialFunction[Action, Continue] = {
        val base = super.performAccess
        ({
            case a @ AccessCardAction(c) if frGagarin && frRemoteRoot(c) && !frGagarinPaid.has(c) && run.exists(r => !r.ended) =>
                if (canSpend(Runner, 1, Spend.corpAbility))
                    Ask(Runner).add(FrAccessPayAction(Runner, c)).add(FrAccessRefuseAction(Runner, c))
                else
                    Ask(Runner).add(FrAccessUnpaidAction(Runner, c))

            case FrAccessPayAction(f, c) =>
                spendCredits(Runner, 1, Spend.corpAbility)
                log(Runner, "pays", 1.credits, "to access", accessElem(c), "(Gagarin Deep Space)")
                frGagarinPaid :+= c
                push(AccessCardAction(c))
                trigger(CreditsPaid(Runner, 1, Spend.corpAbility))
                NextAction

            case FrAccessRefuseAction(f, c) => frSkipAccess(c)
            case FrAccessUnpaidAction(f, c) => frSkipAccess(c)

            // HQ and R&D: the cards are accessed one at a time; each HQ card is drawn at random among the HQ cards not
            // accessed yet when it is accessed, each R&D card is the first card of R&D not accessed yet (jinteki)
            case a @ AccessServerAction(RnD) if frDynOk(RnD) =>
                val res = base(a)
                run.foreach { r =>
                    frDynRun = |(r)
                    frHQLeft = 0
                    frHQPicked = None
                    frRDLeft = r.toAccess.count(c => zone(c) == DeckZone(Corp))
                    r.toAccess = r.toAccess.%(c => zone(c) != DeckZone(Corp))
                }
                res

            case AccessServerAction(HQ) if frDynOk(HQ) && hand(Corp).any =>
                run.foreach { r =>
                    frDynRun = |(r)
                    frRDLeft = 0
                    frHQPicked = None
                    frHQLeft = 1 + r.accessBonusFor("HQ") + r.accessBonusFor("total")
                    r.toAccess = root(HQ)./~(c => c +: allHosted(c))
                }
                push(AccessNextAction)
                NextAction

            case a @ AccessNextAction if frDynActive => frDynNext(a, base)

            case FrHQAccessShuffledAction(l) =>
                run.foreach { r =>
                    frHQPicked = l.starting
                    frHQSnapshot = frHQEligible(r)
                }
                push(AccessNextAction)
                NextAction

            case a @ AccessCardAction(c) if frDynActive && frHQPicked.has(c) =>
                val r = run.get
                val elig = frHQEligible(r)
                if (elig.toSet != frHQSnapshot.toSet) {
                    // HQ changed since the card was drawn: draw again
                    frHQPicked = None
                    r.toAccess = r.toAccess.but(c)
                    Shuffle[Card](elig, FrHQAccessShuffledAction(_), "HQ access")
                }
                else {
                    frHQLeft -= 1
                    frHQPicked = None
                    base(a)
                }

            case a @ AccessCardAction(c) =>
                frGagarinPaid = frGagarinPaid.but(c)
                if (frDynActive && zone(c) == DeckZone(Corp) && frRDLeft > 0)
                    frRDLeft -= 1
                base(a)
        } : PartialFunction[Action, Continue]) orElse base
    }

    // the dynamic HQ / R&D access applies to normal breaches (not when the Corp chooses the HQ cards, or R&D is accessed
    // from the bottom)
    def frDynOk(s : Server) : Boolean = run.exists(r => !r.ended) && !(s == HQ && rotCorpChoosesHQAccess) && !(s == RnD && run.exists(_.accessBonusFor("bottom") > 0))
    def frDynNext(a : Action, base : PartialFunction[Action, Continue]) : Continue = {
        val r = run.get
        if (frRDLeft > 0)
            r.toAccess = r.toAccess.%(c => zone(c) != DeckZone(Corp)) ++ deck(Corp).%(c => !r.accessed.has(c) && !usedThisTurn(c, "no-access")).take(1)
        if (frHQLeft > 0 && r.accessBonusFor("no-hand") > 0)
            frHQLeft = 0
        if (frHQLeft > 0) {
            val elig = frHQEligible(r)
            frHQPicked = frHQPicked.%(elig.has)
            if (elig.none)
                frHQLeft = 0
            else if (frHQPicked.none)
                return Shuffle[Card](elig, FrHQAccessShuffledAction(_), "HQ access")
            r.toAccess = r.toAccess.%(c => zone(c) != HandZone(Corp)) ++ frHQPicked.$
        }
        base(a)
    }

    def frDynActive : Boolean = frDynRun.exists(x => run.exists(r => (r eq x) && !r.ended)) && (frRDLeft > 0 || frHQLeft > 0)
    def frHQEligible(r : Run) : $[Card] = hand(Corp).%(c => !r.accessed.has(c) && !usedThisTurn(c, "no-access"))

    def frSkipAccess(c : Card) : Continue = {
        run.foreach(r => r.toAccess = r.toAccess.but(c))
        log(Runner, "does not access", accessElem(c), "(Gagarin Deep Space)")
        push(AccessNextAction)
        NextAction
    }

    // Lakshmi Smartfabrics: copies of the revealed agenda cannot be stolen for the remainder of the turn
    override def accessProtected(c : Card) : Boolean = super.accessProtected(c) || (c.data.isAgenda && flagsOf("fr-lakshmi").exists(_.card.exists(_.title == c.title)))

    // ---- purposes -------------------------------------------------------------------------------------------------------
    override def resolvePurpose(f : Faction, l : $[Card], p : Purpose) : Continue = p match {
        case FrSkorpiosPurpose(id) =>
            l.single.%(c => zone(c) == DiscardZone(Runner)).foreach { c =>
                place(c, RemovedZone(Runner))
                state(c).reset()
                markUsed(id, "skorpios")
                frHeapSeen -= c
                log(Corp, "uses", id, "to remove", c, "from the game")
            }
            NextAction

        case FrForfeitRezPurpose(c, alt, then) =>
            l.single.foreach { x =>
                log(Corp, "forfeits", x, alt.?("to rez").|("as an additional cost to rez"), hiddenCorpElem(c))
                if (alt)
                    frAltRez = |(c)
                push(ForfeitEffect(x), then.|(FrRezAfterCostAction(c, false)))
            }
            NextAction

        case FrUnpreventableTrashPurpose(by) =>
            withTrashCause("unpreventable")(l.foreach(c => trashCard(c, by, false)))
            NextAction

        case FrFaustPurpose(a) =>
            l.foreach(c => moveTo(c, DiscardZone(Runner)))
            if (l.any)
                log(Runner, "trashes", l./(cardElem).comma, "from the grip (Faust)")
            fxGripTrashed(l)
            push(FrFaustBreakAction(a))
            NextAction

        case FrArmoredPurpose(a) =>
            l.foreach(c => moveTo(c, DiscardZone(Runner)))
            if (l.any)
                log(Runner, "trashes", l./(cardElem).comma, "from the grip (Armored Servers)")
            fxGripTrashed(l)
            push(FrArmoredBreakAction(a))
            NextAction

        case FrPatchworkPurpose(a, pw) =>
            super.resolvePurpose(f, l, PrevPatchworkPurpose(pw))

        case _ => super.resolvePurpose(f, l, p)
    }

    // ---- own effects (end of the performInternal chain) -----------------------------------------------------------------
    def performFinalRot : PartialFunction[Action, Continue] = {
        case FrUnpreventableDamageEffect(k, n) =>
            frUnpreventableNext = true
            push(DamageEffect(k, n), FrUnpreventableClearEffect)
            NextAction

        // the encounter-on-access ice: uninstalled (R&D, HQ) -> encountered where it is (PhantomEncounterEffect); installed and rezzed ->
        // encountered where it is installed (the Runner's position does not change); installed and unrezzed -> encountered as if it
        // were rezzed (as an uninstalled one), unrezzed again when the encounter ends
        case FrAccessEncounterEffect(c) =>
            if (location.contains(c) && c.data.isIce && !zone(c).is[DiscardZone] && run.exists(r => !r.ended)) {
                if (!isInstalled(c))
                    push(PhantomEncounterEffect(c))
                else if (isRezzed(c))
                    push(ForceEncounterEffect(c))
                else {
                    state(c).rezzed = true
                    state(c).seen = true
                    frAccessRezzed :+= c
                    log(c, "is encountered as if it were rezzed")
                    push(ForceEncounterEffect(c), FrAccessUnrezAction(c))
                }
            }
            NextAction

        case FrAccessUnrezAction(c) =>
            if (frAccessRezzed.has(c)) {
                frAccessRezzed = frAccessRezzed.but(c)
                if (location.contains(c) && isInstalled(c)) {
                    state(c).rezzed = false
                    state(c).brokenSubs = $
                    state(c).strengthEncounter = 0
                }
            }
            NextAction

        case StackSearchedEffect(c) =>
            regInc("stack-searched")
            trigger(FrStackSearched(|(c)))
            NextAction

        case FrUnpreventableClearEffect =>
            frUnpreventableNext = false
            NextAction

        case FrUnpreventableTrashEffect(c, by) =>
            if (location.contains(c) && isInstalled(c))
                withTrashCause("unpreventable")(trashCard(c, by, false))
            NextAction

        case FrSkorpiosCheckEffect =>
            val l = discard(Runner).%(c => !frHeapSeen.contains(c) && owner(c).isRunner)
            frHeapSeen = discard(Runner).toSet
            frSkorpios match {
                case Some(id) if l.any =>
                    push(ChooseCardEffect(Corp, l.sortBy(_.title), FrSkorpiosPurpose(id), "Skorpios Defense Systems: the trashed Runner cards are set aside - remove 1 of them from the game?", true))
                case _ =>
            }
            NextAction

        case FrTrashBatchEndEffect =>
            val l = frTrashBatch
            frTrashBatch = $
            if (l.any)
                trigger(FrCorpTrashBatch(l.map(_._1), l.map(_._2)))
            NextAction

        case FrVirtualTagEffect(n) =>
            val m = n.atLeast(-frVirtualTags)
            frVirtualTags += m
            tags = (tags + m).atLeast(0)
            if (m > 0)
                log("The Runner is considered to have", m.tags, "more")
            NextAction

        case FrRezAfterCostAction(c, ignore) =>
            frRezCostPaid = |(c)
            try rezCard(c, ignore) finally { frRezCostPaid = None ; frAltRez = None }
            NextAction

        case FrRezForfeitAction(f, c, ctx) =>
            windowPassed = $
            actedInWindow = |(f)
            push(reask(ctx))
            if (frRezSurcharge(c) > 0 && canSpend(Corp, frRezSurcharge(c), Spend.rez(c))) {
                spendCredits(Corp, frRezSurcharge(c), Spend.rez(c))
                log(Corp, "pays", frRezSurcharge(c).credits, "as an additional cost (Hernando Cortez)")
            }
            push(ChooseCardEffect(Corp, frForfeitable, FrForfeitRezPurpose(c, true, None), "Tithonium: forfeit an agenda to rez it", false))
            NextAction

        case FrPhantomEndAction(c) =>
            frPhantom = frPhantom.but(c)
            if (!isInstalled(c)) {
                state(c).rezzed = false
                state(c).brokenSubs = $
                state(c).strengthEncounter = 0
                state(c).strengthRun = 0
            }
            run.foreach(r => if (r.ice.has(c)) r.ice = None)
            NextAction

        case FrDiscountResumeAction(a, c) =>
            frRezCostPaid = |(c)
            try performInternal(a, NoVoid) finally frRezCostPaid = None

        case FrFaustBreakAction(a) =>
            frFaustPaid = true
            try performInternal(a, NoVoid) finally frFaustPaid = false

        case FrArmoredBreakAction(a) =>
            frArmoredPaid = true
            try performInternal(a, NoVoid) finally frArmoredPaid = false

        case FrPatchworkResumeAction(a) =>
            frPatchworkBypass = true
            try performInternal(a, NoVoid) finally frPatchworkBypass = false

        // the Runner moves to the installed ice and encounters it (Formicary)
        case FrEncounterNowEffect(c) =>
            (run, zone(c)) match {
                case (Some(r), IceZone(s)) if !r.ended && r.server == s && isRezzed(c) =>
                    pending = pending.%!(a => a.is[RunStep] || a.is[PaidWindowAction])
                    r.position = ice(s).indexOf(c) + 1
                    r.ice = |(c)
                    log(Runner, "moves to", c, "and encounters it")
                    push(EncounterStartAction(c))
                case _ =>
            }
            NextAction

        case FrGrantedEncounterEffect(ice, l) =>
            if (run.exists(r => !r.ended) && isRezzed(ice)) {
                val ctx = new PreventCtx("encounter")
                ctx.remaining = 1
                ctx.count = 1
                ctx.by = Corp
                ctx.source = |(ice)
                if (preventWouldOffer(ctx)) {
                    // the window resolves no effect of its own (no source): FrGrantedResumeAction resolves the granted ability
                    ctx.source = None
                    frGranted +:= ctx
                    push(FrGrantedResumeAction(l))
                    openPrevent(ctx, $(1))
                }
                else {
                    pushAll(l)
                    NextAction
                }
            }
            else
                NextAction

        case FrGrantedResumeAction(l) =>
            frGranted.starting.foreach { ctx =>
                frGranted = frGranted.drop(1)
                if (ctx.remaining > 0)
                    pushAll(l)
                else
                    log("The encounter ability is prevented")
            }
            NextAction

    }

    // ---- game-level triggers (end of triggerEffects) ----------------------------------------------------------------------
    def finalRotTriggers(e : Event) : $[ForcedAction] = e match {
        case DamageDone(_, n) if n > 0 =>
            frArm("damage")
            $
        case PlayResolved(Corp, c) if c.data.isOperation =>
            frArm("operation")
            $
        case RunInitiated(_) =>
            frRdAccessedThisRun = 0
            $
        case Accessed(c) if zone(c) == DeckZone(Corp) =>
            frRdAccessedThisRun += 1
            $
        case TurnEnds(_) =>
            frHiveSpend = None
            $
        case CardsDrawn(Corp, _) if frClickDrawContext =>
            frClickDrawn += 1
            $
        // Muertos Gang Member: "when uninstalled" (trashed: its onTrash)
        case FrUninstalled(c, _) if c.code == "08068" =>
            val l = installedCards(Corp).%(x => !isRezzed(x) && !x.data.isAgenda)
            l.any.$(ChooseCardEffect(Corp, l, RezPurpose(true), "Muertos Gang Member: rez a card ignoring the rez cost?", true))
        case Installed(Runner, c) =>
            frFromHeap -= c
            $
        // Overseer Matrix trashed by the Runner during the run: its ability lasts for the remainder of the run
        case FrCorpTrashBatch(_, from) =>
            flagsOf("fr-overseer")./~(f => f.card.%(_ => f.server.exists(s => from.exists(_.has(s))))./(o => RaOverseerAction(o, from.count(_ == f.server)) : ForcedAction))
        case _ => $
    }
}
