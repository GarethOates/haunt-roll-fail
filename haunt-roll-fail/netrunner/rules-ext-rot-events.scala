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
// Generic mechanics added for the rotated / FFG-era Runner events (see STATUS-rot-events.md):
// lingering events (currents: see rules-ext-rot-operations.scala), turning the rig facedown, extra turns, identity switch, credit drains,
// root-less breaches, run markers (access from the bottom of R&D / no HQ cards), game-level restrictions on rezzing,
// running, advancing and installing ice (Blackmail, Interdiction, Unscheduled Maintenance, Security Leak, Hacktivist
// Meeting, Marathon, Peace in Our Time, The Price of Freedom) applied by intercepting the engine asks.
// ---------------------------------------------------------------------------------------------------------------------

// ---- new effects ----------------------------------------------------------------------------------------------------
case class TurnRigFacedownEffect(cards : $[Card]) extends ForcedAction                          // turn installed Runner cards facedown (Apocalypse)
case class ExtraTurnEffect(f : Faction) extends ForcedAction                                   // f takes an additional turn after this one
case class SwitchIdentityEffect(f : Faction, code : String) extends ForcedAction                // replace f's identity with another one (Rebirth)
case class DrainCreditsEffect(max : Int, mult : Int, tags : Int) extends ForcedAction           // Corp loses up to max credits, Runner gains mult per credit and takes tags
case class SetRunMarkerEffect(key : String) extends ForcedAction                                // mark the current run ("bottom": access R&D from the bottom, "no-hand": no HQ cards)
case class TrashAccessedEffect(card : Card) extends ForcedAction                                // trash the card being accessed at no cost (the access decision is skipped)
case class RfgAccessedEffect(card : Card) extends ForcedAction                                  // remove the card being accessed from the game
case class RemoveFlagsScopeEffect(scope : String) extends ForcedAction                          // remove every lingering flag of a scope
case class RemoveAdvancementEffect(card : Card, n : Int) extends ForcedAction
case class RunnerTrashCardsEffect(cards : $[Card]) extends ForcedAction                         // the Runner trashes cards (only those still in a trashable zone)
case class CorpAddAllToTopEffect(cards : $[Card]) extends ForcedAction                          // the Corp adds cards from HQ to the top of R&D and arranges them
case class SecretSpendEffect(card : Card, max : Int) extends ForcedAction                        // the Runner secretly spends 0..max credits for card (Push Your Luck, Rigged Results)
case class SecretSpendAction(self : Faction, card : Card, n : Int) extends BaseAction("Secretly spend credits")(n.hl ~ " credits")
case class SecretSpentAction(card : Card, n : Int) extends CardAction                           // dispatched to the card's perform: n credits were set aside (not yet paid)


trait RotEventRules { game : Game =>

    var extraTurns : Map[Faction, Int] = Map()
    var rotSkipDecision : $[Card] = $                 // accessed cards whose access decision is skipped (trashed / removed by an access trigger)

    // ---- helpers -------------------------------------------------------------------------------------------------------
    def currentActive(code : String) : Boolean = at(PlayZone(Runner)).exists(_.code == code) || at(PlayZone(Corp)).exists(_.code == code)

    def runBlocked(s : Server) : Boolean = flagsOf("no-run").exists(f => f.server.none || f.server.has(s))

    def iceInstallBlocked : Boolean = currentActive("06036") && reg("ice-installed") >= 1

    def noIceRez : Boolean = hasFlag("no-ice-rez") && run.exists(r => !r.ended)

    def noAccessMarked : Boolean = installedCards(Corp).exists(c => usedThisTurn(c, "no-access"))

    def rotActive : Boolean = noIceRez || hasFlag("no-run") || hasFlag("no-advance") || at(PlayZone(Runner)).exists(c => c.data.has("Current"))

    // amounts that can be spent secretly (Government Investigations: not 2)
    def secretAmounts(max : Int) : $[Int] = 0.to(max.atMost(credits(Runner))).$.%(n => n != 2 || !currentActive("11069"))

    // installed Corp cards in the root of or protecting a server
    def cardsOfServer(s : Server) : $[Card] = ice(s) ++ root(s)

    def rotFix(a : UserAction) : UserAction = a match {
        case RezAction(_, c, _) if c.data.isIce && noIceRez => a.!(true, "cannot rez ice during this run")
        case RezAction(_, c, _) if !c.data.isIce && active.isRunner && currentActive("11087") => a.!(true, "Interdiction: no non-ice rez on the Runner's turn")
        case RezAction(_, c, _) if !c.data.isIce && currentActive("08021") && hand(Corp).none => a.!(true, "Hacktivist Meeting: no card in HQ to trash")
        case AdvanceMenuAction(_) if hasFlag("no-advance") => a.!(true, "cannot advance cards this turn")
        case AdvanceAction(_, _) if hasFlag("no-advance") => a.!(true, "cannot advance cards this turn")
        case AdvanceMenuAction(_) if currentActive("14009") && credits(Corp) < 2 => a.!(true, "Security Leak: advancing costs 1 more")
        case AdvanceAction(_, _) if currentActive("14009") && credits(Corp) < 2 => a.!(true, "Security Leak: advancing costs 1 more")
        case InstallWhereAction(_, c) if c.data.isIce && iceInstallBlocked => a.!(true, "Unscheduled Maintenance: only 1 ice per turn")
        case CorpInstallAction(_, c, _) if c.data.isIce && iceInstallBlocked => a.!(true, "Unscheduled Maintenance: only 1 ice per turn")
        case RunAction(_, s) if runBlocked(s) => a.!(true, "cannot run this server this turn")
        case _ => a
    }

    def rotAsk(c : Continue) : Continue = c match {
        case Ask(f, l) => Ask(f, l./(rotFix))
        case MultiAsk(asks, p) => MultiAsk(asks./(a => Ask(a.faction, a.actions./(rotFix))), p)
        case c => c
    }

    // lingering events in the play areas: end-of-duration cleanup and the trashing of currents
    def lingerTriggers(e : Event) : $[ForcedAction] =
        (at(PlayZone(Runner)) ++ at(PlayZone(Corp)))./~ { c =>
            CardImpls(c.code) match {
                case re : RE => re.lingerTriggers(c, e)
                case _ => $
            }
        }

    // ---- game-level triggers (not tied to an active card) ---------------------------------------------------------------
    def rotEventTriggers(e : Event) : $[ForcedAction] = lingerTriggers(e) ++ (e match {
        case Installed(Corp, c) if c.data.isIce => $(RegisterEffect("ice-installed", 1))
        case Installed(Runner, _) => $(RegisterEffect("runner-install", 1))
        case AbilityUsed(_, _, true) => $(RegisterEffect("click-ability", 1))
        case CounterAdded(c, "virus") if owner(c).isRunner && c.data.isProgram => $(MarkUsedEffect(c, "virus-placed"))
        // I've Had Worse trashed from the grip by net or meat damage
        case DamageDone(kind, _) if kind == "net" || kind == "meat" =>
            lastDamaged.%(_.code == "07032")./(_ => $(MessageEffect(Runner, "draws 3 cards (I've Had Worse)"), DrawEffect(Runner, 3))).flatten
        // Political Graffiti hosted on a scored agenda is trashed when the Corp purges
        case Purged(_) =>
            scored(Corp)./~(hosted).%(_.code == "10039")./(c => TrashEffect(c, Runner, false))
        // The Price of Freedom: the Corp cannot advance during its next turn
        case TurnBegins(Corp) if at(PlayZone(Runner)).exists(_.code == "10100") => $(AddFlagEffect(Flag("no-advance", None, None, "turn", 0, $)))
        // Employee Strike / Rumor Mill / Corporate Scandal leaving play
        case Trashed(c, _, _) if c.code == "09053" => $(RemoveFlagsScopeEffect("strike"))
        case Trashed(c, _, _) if c.code == "11022" => $(RemoveFlagsScopeEffect("rumor"))
        case Trashed(c, _, _) if c.code == "10025" => $(BadPubEffect(-1))
        case _ => $
    })

    // ---- actions ----------------------------------------------------------------------------------------------------
    def performRotEvents : PartialFunction[Action, Continue] = {
        // ---- interceptions of engine asks (restrictions of the currents / flags)
        case a : ActionPhaseAction if rotActive => rotAsk(performPHTAI(a))
        case a : PaidWindowAction if rotActive => rotAsk(performPHTAI(a))
        case a : EncounterAction if rotActive => rotAsk(performPHTAI(a))
        case a : InstallWhereAction if rotActive => rotAsk(performPHTAI(a))
        case a : RunMenuAction if rotActive => rotAsk(performPHTAI(a))
        case a : InstallMenuAction if rotActive => rotAsk(performTurn(a))
        case a : RezMenuAction if rotActive => rotAsk(performTurn(a))
        case a : AdvanceMenuAction if rotActive => rotAsk(performTurn(a))
        case RunAnyServerEffect(src, l) if hasFlag("no-run") => performPHTAI(RunAnyServerEffect(src, l.%!(runBlocked)))
        case RunEffect(s, _) if runBlocked(s) =>
            log(Runner, "cannot run", s, "this turn")
            NextAction
        // Hacktivist Meeting: the random trash from HQ is charged by every rez (RestrictionRules.rezCard)
        // Security Leak: advancing costs 1 more
        case a : AdvanceAction if currentActive("14009") =>
            credits += Corp -> (credits(Corp) - 1)
            log(Corp, "pays", 1.credits, "more to advance (Security Leak)")
            performPHTAI(a)
        // Data Breach / Mobius: run R&D again after the run is cleaned up, keeping the event in play
        case RunCleanupAction if run.exists(r => r.source.exists(c => state(c).memo == "run-again")) =>
            run.foreach { r =>
                lastRun = |(r)
                cs.values.foreach { s => s.strengthEncounter = 0 ; s.strengthRun = 0 ; s.brokenSubs = $ ; s.usedRun = $ }
                r.source.foreach { c =>
                    state(c).memo = "second"
                    push(RunEffect(RnD, |(c)))
                }
            }
            run = None
            windowPassed = $
            NextAction
        // an additional turn (Encore)
        case EndTurnCleanupAction(f) if extraTurns.get(f).exists(_ > 0) =>
            extraTurns += f -> (extraTurns(f) - 1)
            registerLastTurn = register
            if (f.isRunner)
                successfulRunsLastTurn = successfulRunsThisTurn
            cs.values.foreach { s => s.strengthTurn = 0 ; s.strengthRun = 0 ; s.strengthEncounter = 0 }
            log(f, "ends the turn", "(" ~ credits(f).credits ~ ", " ~ hand(f).num.cards ~ ")")
            log(f, "takes an additional turn")
            turnStarted = false
            StartTurnAction(f)
        // Showing Off: the R&D accesses come from the bottom
        case a @ AccessServerAction(RnD) if run.exists(_.accessBonusFor("bottom") > 0) =>
            val c = performAccess(a)
            run.foreach { r =>
                val n = r.toAccess.count(x => zone(x) == DeckZone(Corp))
                r.toAccess = deck(Corp).reverse.take(n) ++ r.toAccess.%(x => zone(x) != DeckZone(Corp))
            }
            c
        // run markers / no-access markers: restrict the cards still to be accessed
        case a @ AccessNextAction if run.exists(r => r.accessBonusFor("no-hand") > 0) || noAccessMarked =>
            run.foreach { r =>
                if (r.accessBonusFor("no-hand") > 0)
                    r.toAccess = r.toAccess.%!(c => zone(c) == HandZone(Corp))
                r.toAccess = r.toAccess.%!(c => usedThisTurn(c, "no-access"))
            }
            performPHTAI(a)
        // a card trashed / removed by an access trigger needs no access decision
        case AccessDecisionAction(c) if rotSkipDecision.has(c) =>
            rotSkipDecision = rotSkipDecision.but(c)
            push(AccessDoneAction(c))
            NextAction

        // ---- new effects
        case TurnRigFacedownEffect(l) =>
            val ll = l.%(c => isInstalled(c) && owner(c).isRunner && !state(c).facedown)
            ll.foreach(c => state(c).facedown = true)
            if (ll.any)
                log(ll.num.cards, "turned facedown:", ll./(cardElem).comma)
            NextAction

        case ExtraTurnEffect(f) =>
            extraTurns += f -> (extraTurns.get(f).|(0) + 1)
            log(f, "will take an additional turn")
            NextAction

        case SwitchIdentityEffect(f, code) =>
            identity.get(f).foreach { old =>
                val used = location.keys.%(_.code == code)./(_.n).toList
                val nc = Card(code, (used.any.?(used.max).|(0)) + 1)
                hosted(old).foreach(h => place(h, HostedZone(nc)))
                moveTo(old, RemovedZone(f))
                place(nc, IdentityZone(f))
                identity += f -> nc
                state(nc).seen = true
                log(f, "switches identity to", nc)
            }
            NextAction

        case DrainCreditsEffect(max, mult, tags) =>
            val n = max.atMost(credits(Corp))
            pushAll($(LoseCreditsEffect(Corp, n), GainCreditsEffect(Runner, n * mult)) ++ (tags > 0).$(TagsEffect(tags)))
            NextAction

        case SetRunMarkerEffect(k) =>
            run.foreach(r => r.accessBonus += k -> 1)
            NextAction

        case TrashAccessedEffect(c) =>
            if (location.contains(c) && !zone(c).is[DiscardZone] && !zone(c).is[ScoredZone]) {
                rotSkipDecision :+= c
                state(c).seen = true
                trashCard(c, Runner, true)
            }
            NextAction

        case RfgAccessedEffect(c) =>
            if (location.contains(c) && !zone(c).is[ScoredZone] && zone(c) != RemovedZone(Corp)) {
                rotSkipDecision :+= c
                state(c).seen = true
                log(cardElem(c), "is removed from the game")
                moveTo(c, RemovedZone(Corp))
                cleanupRemotes()
            }
            NextAction

        case RemoveFlagsScopeEffect(scope) =>
            flags = flags.%!(_.scope == scope)
            NextAction

        case RemoveAdvancementEffect(c, n) =>
            val m = n.atMost(state(c).advancement)
            if (m > 0) {
                addCounter(c, "advancement", -m)
                log(m.hl, "advancement counter" + (m != 1).??("s"), "removed from", hiddenCorpElem(c))
            }
            NextAction

        case RunnerTrashCardsEffect(l) =>
            l.foreach { c =>
                if (location.contains(c) && !zone(c).is[DiscardZone] && !zone(c).is[ScoredZone] && zone(c) != RemovedZone(owner(c)))
                    trashCard(c, Runner, false)
            }
            NextAction

        case SecretSpendEffect(c, max) =>
            Ask(Runner).group(c.title + ": secretly spend credits").add(secretAmounts(max)./(n => SecretSpendAction(Runner, c, n)))

        case SecretSpendAction(f, c, n) =>
            log(f, "spends credits secretly")
            push(SecretSpentAction(c, n))
            NextAction

        case CorpAddAllToTopEffect(l) =>
            val ll = l.%(c => zone(c) == HandZone(Corp))
            ll.foreach(c => moveTo(c, DeckZone(Corp), true))
            if (ll.any) {
                log(Corp, "adds", ll.num.cards, "from HQ to the top of R&D")
                push(ArrangeTopEffect(Corp, Corp, ll.num))
            }
            NextAction
    }
}


// ---------------------------------------------------------------------------------------------------------------------
// Base class for the rotated events: lingering events (currents, "for the remainder of the turn"), doubles, priority,
// run helpers
// ---------------------------------------------------------------------------------------------------------------------

abstract class RE(val code : String) extends CardImpl {
    def thisCardRun(self : Card)(implicit g : Game) = g.run.exists(_.source.has(self))

    def activeRun(implicit g : Game) : |[Run] = g.run.%(r => !r.ended)

    def encountering(implicit g : Game) : |[Card] = g.run.%(r => !r.ended && r.phase == EncounterPhase)./~(_.ice)

    def inPlay(self : Card)(implicit g : Game) = g.zone(self) == PlayZone(g.owner(self))

    // how long the card stays in the play area after resolving: "" (not at all), "turn", "corp-turn", "runner-begins",
    // "corp-begins", "current" (until another current is played or an agenda is scored by the opponent), "game"
    def lingering(self : Card)(implicit g : Game) : String = self.data.has("Current").?("current").|("")

    def lingerEnd(self : Card)(implicit g : Game) : ForcedAction = TrashEffect(self, g.owner(self), false)

    override def staysInPlay(self : Card)(implicit g : Game) = lingering(self) != ""

    def lingerTriggers(self : Card, e : Event)(implicit g : Game) : $[ForcedAction] = (lingering(self), e) match {
        case ("turn", TurnEnds(_)) => $(lingerEnd(self))
        case ("corp-turn", TurnEnds(Corp)) => $(lingerEnd(self))
        case ("runner-begins", TurnBegins(Runner)) => $(lingerEnd(self))
        case ("corp-begins", TurnBegins(Corp)) => $(lingerEnd(self))
        // "current": trashed by rotOperationTriggers (another current played, an agenda scored / stolen)
        case _ => $
    }

    // "Play only as your first [click]"
    def firstClick(implicit g : Game) = g.active.isRunner && g.reg("actions") == 0 && g.reg("click-ability") == 0

    // doubles: an additional click (n) as a cost
    def doubleOk(n : Int = 1)(implicit g : Game) = g.clicks(Runner) >= n + 1
    def doubleCost(n : Int = 1)(implicit g : Game) : $[ForcedAction] = $(LoseClicksEffect(Runner, n))

    def centralsRun(implicit g : Game) = Server.centrals.forall(s => g.successfulRunsThisTurn.has(s))
    def centralRun(implicit g : Game) = g.successfulRunsThisTurn.exists(_.isCentral)

    def runAny(self : Card)(implicit g : Game) = RunAnyServerEffect(|(self), g.servers)
    def runRemote(self : Card)(implicit g : Game) = RunAnyServerEffect(|(self), g.remotes)
    def runOn(self : Card, s : Server)(implicit g : Game) = RunEffect(s, |(self))

    def runnerCanInstall(c : Card, discount : Int)(implicit g : Game) : Boolean =
        (c.data.isProgram || c.data.isHardware || c.data.isResource) && g.canAffordInstall(c, discount) && (!g.impl(c).mustBeHosted(c) || g.allIce.exists(h => g.impl(c).hostRequirement(c, h)))

    def installable(l : $[Card], discount : Int)(implicit g : Game) = l.%(c => runnerCanInstall(c, discount))

    def breakers(implicit g : Game) = g.installedCards(Runner).%(c => g.isActive(c) && g.isIcebreaker(c))

    def unrezzedIce(implicit g : Game) = g.allIce.%(c => !g.isRezzed(c))

    def remoteRootUnrezzed(implicit g : Game) = g.remotes./~(r => g.root(r)).%(c => !g.isRezzed(c))
}
