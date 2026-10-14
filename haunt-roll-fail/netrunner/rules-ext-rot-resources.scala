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
// Generic mechanics added for the rotated / FFG-era Resource cards (see STATUS-rot-resources.md)
// ---------------------------------------------------------------------------------------------------------------------

// ---- new events -----------------------------------------------------------------------------------------------------
case class ClickLost(f : Faction, n : Int) extends Event                    // f lost clicks through a card ability (ClickLossEffect)

// ---- new effects ----------------------------------------------------------------------------------------------------
case class FlipFaceupEffect(card : Card) extends ForcedAction                         // turn a facedown installed runner card faceup (an event is trashed instead)
case class ClickLossEffect(f : Faction, n : Int) extends ForcedAction                 // lose clicks, triggering ClickLost (Adjusted Chronotype)
case class BreakSubIndexEffect(ice : Card, idx : Int, breaker : Card) extends ForcedAction  // break one specific subroutine of the encountered ice
case class RunnerForfeitEffect(card : Card) extends ForcedAction                      // the Runner forfeits an agenda in the Runner's score area
case class HostUninstalledEffect(card : Card, host : Card, facedown : Boolean) extends ForcedAction  // host a runner card on another card without installing it
case class AccessRandomFromHQEffect(n : Int, noStealOrTrash : Boolean) extends ForcedAction   // access n random cards from HQ outside of a breach
case class AccessRandomHQPickedAction(noStealOrTrash : Boolean, shuffled : $[Card]) extends ShuffledAction[Card]



trait RotResourceRules { game : Game =>

    // runner cards that can be installed right now paying discount less (cost, memory, host availability)
    def runnerInstallableNow(c : Card, discount : Int) : Boolean = {
        val cost = (installCost(c, None) - discount).atLeast(0)
        val mu = c.data.isProgram.??(c.data.memory.|(0))
        val hostOk = !impl(c).mustBeHosted(c) || allIce.exists(h => impl(c).hostRequirement(c, h))
        (c.data.isProgram || c.data.isHardware || c.data.isResource) && credits(Runner) >= cost && (!c.data.isProgram || memoryFree >= mu) && hostOk && impl(c).canInstall(c)
    }

    // a card title safe to embed in a prompt string of a serialized action (ASCII, no quotes or backslashes)
    def promptText(s : String) : String = s.toList.map(ch => if (ch >= 128) '?' else if (ch == '"') '\'' else if (ch == '\\' || ch == '\n') ' ' else ch).mkString
    def promptTitle(c : Card) : String = promptText(c.title)

    // runner cards owned by the runner that are installed (not hosted uninstalled)
    def runnerInstalledFaceup : $[Card] = installedCards(Runner).%(c => owner(c).isRunner && !state(c).facedown)

    // the shards: (code, server) -- installed from the grip instead of breaching that server
    val shardServers : $[(String, Server)] = $("06020" -> RnD, "06059" -> Archives, "06100" -> HQ)

    // ---- game-level triggered effects of cards that are not active (in the grip / heap) ---------------------------------
    def rotResourceTriggers(e : Event) : $[ForcedAction] = {
        // bookkeeping for Officer Frank (meat damage this turn) and Psych Mike (cards accessed from R&D this turn)
        e match {
            case DamageDone("meat", n) if n > 0 => regInc("meat-damage", n)
            case Accessed(c) if zone(c) == DeckZone(Corp) => regInc("rd-accessed")
            case _ =>
        }

        val source = run.%(r => !r.ended)./~(_.source).%!(isInstalled)

        e match {
            case SuccessfulRun(s) =>
                // Eden / Hades / Utopia Shard in the grip: install instead of breaching the matching central server
                val shards = (s.isCentral && run.exists(r => !r.ended && !r.preventAccess)).??(hand(Runner).%(c => shardServers.exists { case (code, srv) => c.code == code && srv == s }))
                shards./(c => OptionalEffect(Runner, promptTitle(c) + ": install it from the grip instead of breaching " + s.name + "?", $(ReplaceBreachEffect, RunnerInstallEffect(c, None, true)), $) : ForcedAction) ++
                // Counter Surveillance / The Masque A: the run was made by a card that trashed itself to start it
                source.%(_.code == "12023")./(c => CounterSurveillanceAction(c) : ForcedAction) ++
                source.%(_.code == "14024")./~(c => $(MessageEffect(Runner, "draws 1 card (The Masque A)"), DrawEffect(Runner, 1)))
            case RunEnds(s, true, _) =>
                run./~(_.source).$.%(c => c.code == "14025" && !isInstalled(c))./(c => MasqueBAction(c) : ForcedAction)
            // Crowdfunding in the heap: when the turn ends after 3 successful runs, may install it ignoring all costs
            case TurnEnds(Runner) if successfulRunsThisTurn.num >= 3 =>
                discard(Runner).%(_.code == "28002").take(1)./(c => OptionalEffect(Runner, "Crowdfunding: install it from the heap ignoring all costs?", $(RunnerInstallEffect(c, None, true)), $))
            // Shadow Team: installed when drawn
            case CardsDrawn(Runner, l) =>
                l.%(c => c.code == "14022" && zone(c) == HandZone(Runner) && runnerInstallableNow(c, 0))./(c => RunnerInstallEffect(c, None, false) : ForcedAction)
            case _ => $
        }
    }

    def performRotResources : PartialFunction[Action, Continue] = {
        case FlipFaceupEffect(c) =>
            if (zone(c) == RigZone && state(c).facedown) {
                if (c.data.isEvent) {
                    log(Runner, "turns", c, "faceup: it is trashed")
                    trashCard(c, Runner, false)
                }
                else {
                    state(c).facedown = false
                    state(c).seen = true
                    impl(c).initialCounters(c).foreach { case (k, n) => addCounter(c, k, n) }
                    log(Runner, "turns", c, "faceup")
                    if (c.data.isProgram)
                        push(CheckMemoryAction)
                }
            }
            NextAction

        case ClickLossEffect(f, n) =>
            val m = n.atMost(clicks(f))
            clicks += f -> (clicks(f) - m)
            if (m > 0) {
                log(f, "loses", m.clicks)
                trigger(ClickLost(f, m))
            }
            NextAction

        case BreakSubIndexEffect(ice, i, b) =>
            if (run.exists(r => !r.ended && r.ice.has(ice)) && !state(ice).brokenSubs.has(i) && subs(ice).lift(i).any) {
                state(ice).brokenSubs :+= i
                log(Runner, "uses", b, "to break", subs(ice).apply(i).label.hl, "on", ice)
                trigger(SubsBroken(ice, $(i), b))
            }
            NextAction

        case RunnerForfeitEffect(c) =>
            if (zone(c) == ScoredZone(Runner)) {
                log(Runner, "forfeits", c)
                toArchives(c, true)
                push(CheckWinAction)
                trigger(Forfeited(c))
            }
            NextAction

        case HostUninstalledEffect(c, h, facedown) =>
            if (location.contains(h) && c != h && !allHosted(c).has(h)) {
                if (inPlayZone(zone(c)))
                    leavePlay(c)
                place(c, HostedZone(h))
                state(c).uninstalled = true
                state(c).facedown = facedown
                state(c).seen = !facedown
                log(facedown.?("A card".hl).|(cardElem(c)), "is hosted on", h)
            }
            NextAction

        case AccessRandomFromHQEffect(n, noSteal) =>
            val h = hand(Corp)
            if (h.none || n <= 0)
                NextAction
            else
                ShuffleTake[Card](h, n.atMost(h.num), AccessRandomHQPickedAction(noSteal, _), "HQ access")

        case AccessRandomHQPickedAction(noSteal, l) =>
            push(AccessCardsEffect(HQ, l, noSteal))
            NextAction
    }
}


// ---------------------------------------------------------------------------------------------------------------------
// Base class for the rotated resource cards
// ---------------------------------------------------------------------------------------------------------------------

// purposes resolved by the base class (shared by several cards)
trait RotResPurpose extends CardPurpose

case class RotInstallIgnoringPurpose(card : Card) extends RotResPurpose                     // install the chosen card ignoring all costs
case class RotInstallPayingPurpose(card : Card, discount : Int) extends RotResPurpose       // install the chosen card paying discount less
case class RotHostPurpose(card : Card, facedown : Boolean) extends RotResPurpose             // host the chosen card on card, uninstalled
case class RotPlayPurpose(card : Card, payCost : Boolean) extends RotResPurpose              // play the chosen event
case class RotRfgGainPurpose(card : Card, n : Int) extends RotResPurpose                    // remove the chosen card from the game and gain n credits
case class RotPumpPurpose(card : Card, n : Int, run : Boolean) extends RotResPurpose         // give the chosen breaker +n strength (run / turn)
case class RotTrashPayingPurpose(card : Card) extends RotResPurpose                          // pay the trash cost of the chosen rezzed card and trash it
case class RotRevealPurpose(card : Card) extends RotResPurpose                               // reveal the chosen cards (owner reveals)
case class RotMoveVirusPurpose(card : Card) extends RotResPurpose                            // move 1 virus counter from card to the chosen card
case class RotUnhostInstallPurpose(card : Card, discount : Int) extends RotResPurpose        // install the chosen hosted (uninstalled) card paying discount less
case class RotForfeitPurpose(card : Card) extends RotResPurpose                              // the Runner forfeits the chosen agenda
case class RotFlipFaceupPurpose(card : Card) extends RotResPurpose                           // turn the chosen facedown installed card faceup

abstract class RR(val code : String) extends CardImpl {
    def trashOnEmpty(self : Card, e : Event, kind : String)(implicit g : Game) : $[ForcedAction] = e match {
        case CounterAdded(c, k) if c == self && k == kind && g.counter(self, kind) == 0 && g.isInstalled(self) => $(TrashEffect(self, g.owner(self), false))
        case AbilityUsed(_, c, _) if c == self && g.counter(self, kind) == 0 && g.isInstalled(self) => $(TrashEffect(self, g.owner(self), false))   // counter costs do not trigger CounterAdded
        case _ => $
    }

    def thisServer(self : Card, s : Server)(implicit g : Game) = g.serverOf(self).has(s)

    def thisCardRun(self : Card)(implicit g : Game) = g.run.exists(_.source.has(self))

    def encountering(implicit g : Game) : |[Card] = g.run.%(r => !r.ended && r.phase == EncounterPhase)./~(_.ice).%(g.isRezzed)

    def activeRun(implicit g : Game) = g.run.exists(r => !r.ended)

    def mark(self : Card, key : String) = MarkUsedEffect(self, key)

    def unused(self : Card, key : String)(implicit g : Game) = !g.usedThisTurn(self, key)

    // Genetics: trigger the first time each turn, and the second time with Gene Conditioning Shoppe
    def shoppe(implicit g : Game) = g.activeCards(Runner).exists(c => c.code == "08006" && !g.isDisabled(c))

    def geneticsMark(self : Card, key : String)(implicit g : Game) : |[ForcedAction] =
        if (!g.usedThisTurn(self, key + "1")) |(MarkUsedEffect(self, key + "1"))
        else if (!g.usedThisTurn(self, key + "2") && shoppe) |(MarkUsedEffect(self, key + "2"))
        else None

    def firstSuccessfulRunOn(s : Server)(implicit g : Game) = g.successfulRunsThisTurn.count(s) == 1

    def installableFromHand(discount : Int)(implicit g : Game) = g.hand(Runner).%(c => !c.data.isEvent && g.runnerInstallableNow(c, discount))

    // events after which "whenever you have 0 credits" is checked
    def creditsCheckpoint(e : Event)(implicit g : Game) : Boolean = e match {
        case CreditsLost(Runner, _) => true
        case Installed(Runner, _) => true
        case Played(Runner, _) => true
        case AbilityUsed(Runner, _, _) => true
        case ActionTaken(Runner, _) => true
        case TurnBegins(Runner) => true
        case CreditsSpentFrom(_, _) => true
        case _ => false
    }

    def runnerAgendas(implicit g : Game) = g.scored(Runner).%(_.data.isAgenda)

    def forfeitChoice(self : Card, prompt : String)(implicit g : Game) : ForcedAction = ChooseCardEffect(Runner, runnerAgendas, RotForfeitPurpose(self), prompt, false)

    final override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) : Continue = p match {
        case p : RotResPurpose => RotResourceExt.chosen(this, p, cards)
        case _ => chosenCard(p, cards)
    }

    def chosenCard(p : CardPurpose, cards : $[Card])(implicit g : Game) : Continue = throw new Error("unhandled card purpose " + p)
}

object RotResourceExt {
    def chosen(impl : RR, p : RotResPurpose, cards : $[Card])(implicit g : Game) : Continue = {
        p match {
            case RotInstallIgnoringPurpose(self) =>
                cards.single.foreach(c => g.pushAll(g.installRunner(c, 99)))

            case RotInstallPayingPurpose(self, d) =>
                cards.single.foreach(c => g.pushAll(g.installRunner(c, d)))

            case RotHostPurpose(self, facedown) =>
                cards.foreach(c => g.push(HostUninstalledEffect(c, self, facedown)))

            case RotPlayPurpose(self, payCost) =>
                cards.single.foreach(c => g.push(PlayCardEffect(Runner, c, payCost, false)))

            case RotRfgGainPurpose(self, n) =>
                if (cards.any)
                    g.log(Runner, "uses", self)
                cards.foreach(c => g.push(RemoveFromGameEffect(c), GainCreditsEffect(Runner, n)))

            case RotPumpPurpose(self, n, run) =>
                cards.foreach(c => g.push(PumpBreakerEffect(c, n, run.?(RunDuration : Duration).|(TurnDuration))))

            case RotTrashPayingPurpose(self) =>
                cards.single.foreach { c =>
                    val tc = g.trashCost(c).|(0)
                    g.push(LoseCreditsEffect(Runner, tc), TrashEffect(c, Runner, false))
                }

            case RotRevealPurpose(self) =>
                if (cards.any)
                    g.push(RevealCardsEffect(g.owner(cards.head), cards))

            case RotMoveVirusPurpose(self) =>
                cards.single.foreach(c => g.push(AddCounterEffect(self, "virus", -1), AddCounterEffect(c, "virus", 1)))

            case RotUnhostInstallPurpose(self, d) =>
                cards.single.foreach { c =>
                    if (g.zone(c).is[HostedZone]) {
                        g.log(Runner, "uses", self, "to install a hosted card paying", d.credits, "less")
                        g.moveTo(c, HandZone(Runner))
                        g.pushAll(g.installRunner(c, d))
                    }
                }

            case RotForfeitPurpose(self) =>
                cards.foreach(c => g.push(RunnerForfeitEffect(c)))

            case RotFlipFaceupPurpose(self) =>
                cards.foreach(c => g.push(FlipFaceupEffect(c)))
        }
        NextAction
    }
}
