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
// Generic mechanics added for the rotated / FFG-era programs (see STATUS-rot-programs.md): exposing cards, facedown
// installs, prevented subroutines, Hivemind virus counters, daemon hosting, run server changes.
// ---------------------------------------------------------------------------------------------------------------------

// ---- new events -----------------------------------------------------------------------------------------------------

// ---- new effects ----------------------------------------------------------------------------------------------------
case class PreventSubsEffect(ice : Card, n : Int) extends ForcedAction           // the first n unbroken subroutines of ice do not resolve this encounter (Tracker, Mass-Driver)
case class ChangeRunServerEffect(server : Server) extends ForcedAction           // the attacked server becomes server (Sneakdoor Prime)
case class SetRunPositionEffect(n : Int) extends ForcedAction                     // set the Runner's position in the attacked server (Surfer)
case class PayCreditsEffect(f : Faction, n : Int) extends ForcedAction           // pay n credits (hosted run credits first), no "loses credits" event
case class ForgetFacedownEffect(card : Card) extends ForcedAction                // card left the facedown state recorded by InstallFacedownEffect
case class ReinstallEffect(card : Card) extends ForcedAction                      // put a just trashed Runner card back into the rig (approximated trash prevention)
case class SpendVirusEffect(card : Card, n : Int, any : Boolean) extends ForcedAction  // pay n virus counters for card: hosted ones first, then Hivemind's (any = from any installed card, the Runner chooses)
case class SpendVirusPurposeAction(from : Card, card : Card, n : Int) extends ForcedAction   // remove up to n virus counters from from (for card), continue with the rest

// purposes resolved by RotProgramRules (not by a card)
trait RotProgPurpose extends Purpose
case class HostIceSelfPurpose(card : Card) extends RotProgPurpose                                   // host card on the chosen piece of ice (card keeps its state)
case class MoveVirusPurpose(from : |[Card], to : |[Card], n : Int) extends RotProgPurpose            // move n virus counters; the chosen card fills the missing side
case class SpendVirusPurpose(card : Card, n : Int) extends RotProgPurpose                            // remove virus counters from the chosen card to pay for card's ability


trait RotProgramRules { game : Game =>

    // virus counters of a virus program including those hosted on Hivemind
    def virusCounters(c : Card) : Int =
        counter(c, "virus") + (owner(c).isRunner && c.data.isProgram && has(c, "Virus") && c.code != "07042").??(installedCards(Runner).%(h => h.code == "07042" && isActive(h))./(h => counter(h, "virus")).sum)

    // virus counters on all installed Runner cards ("any virus counter" costs: Yusuf, Musaazi)
    def anyVirusCounters : Int = installedCards(Runner)./(c => counter(c, "virus")).sum

    // all ice subtypes of the card pool (Panchatantra, Pelangi style choices)
    lazy val iceSubtypes : $[String] = Cards.all.%(_.isIce)./~(_.subtypes).distinct.%(s => s.forall(_ < 128)).sorted

    def performRotPrograms : PartialFunction[Action, Continue] = {
        case ForgetFacedownEffect(c) =>
            rotFacedown = rotFacedown.but(c)
            NextAction

        case ReinstallEffect(c) =>
            if (zone(c) == DiscardZone(Runner)) {
                log(c, "is not trashed and returns to the rig")
                push(RunnerInstallEffect(c, None, true))
            }
            NextAction

        case PreventSubsEffect(ice, n) =>
            val l = subs(ice).indices.$.take(n)
            if (l.any) {
                fxNoResolve ++= l./(i => (ice, i))
                log(l.num.hl, "subroutine" + (l.num != 1).??("s"), "on", ice, "will not resolve")
            }
            NextAction

        case ChangeRunServerEffect(s) =>
            run.foreach { r =>
                if (!r.ended && servers.has(s) && r.server != s) {
                    r.server = s
                    if (!runsThisTurn.has(s))
                        runsThisTurn :+= s
                    log("The attacked server is now", s)
                }
            }
            NextAction

        case SetRunPositionEffect(n) =>
            run.foreach { r =>
                if (!r.ended)
                    r.position = n.atMost(ice(r.server).num).atLeast(0)
            }
            NextAction

        case SpendVirusEffect(c, n, any) =>
            if (any) {
                val l = installedCards(Runner).%(x => counter(x, "virus") > 0)
                if (n > 0 && l.any) {
                    if (l.num == 1)
                        push(SpendVirusPurposeAction(l.head, c, n))
                    else
                        push(ChooseCardEffect(Runner, l, SpendVirusPurpose(c, n), "Choose a card to remove a virus counter from (" + asciiTitle(c) + ")", false))
                }
            }
            else {
                var left = n
                (c +: installedCards(Runner).%(h => h.code == "07042" && h != c && isActive(h))).foreach { x =>
                    val m = left.atMost(counter(x, "virus"))
                    if (m > 0) {
                        addCounter(x, "virus", -m)
                        left -= m
                        if (x != c)
                            log(Runner, "spends", m.hl, "virus counter" + (m != 1).??("s"), "from", x)
                    }
                }
            }
            NextAction

        case SpendVirusPurposeAction(x, c, n) =>
            val m = n.atMost(counter(x, "virus"))
            if (m > 0) {
                addCounter(x, "virus", -m)
                log(Runner, "spends", m.hl, "virus counter" + (m != 1).??("s"), "from", x, "for", c)
                if (n > m)
                    push(SpendVirusEffect(c, n - m, true))
            }
            NextAction

        case PayCreditsEffect(f, n) =>
            val m = n.atMost(availableCredits(f, None))
            if (m > 0) {
                payCredits(f, m, None)
                log(f, "pays", m.credits)
            }
            NextAction
    }

    // purposes of the rotated programs (composed after resolveRWRPurpose in resolvePurpose)
    def resolveRotPurpose(f : Faction, l : $[Card], p : Purpose) : |[Continue] = p match {
        case HostIceSelfPurpose(c) =>
            l.single.foreach(h => push(HostOnEffect(c, h, false)))
            |(NextAction)

        case SpendVirusPurpose(c, n) =>
            l.single.foreach(x => push(SpendVirusPurposeAction(x, c, n)))
            |(NextAction)

        case MoveVirusPurpose(from, to, n) =>
            l.single.foreach { x =>
                val a = from.|(x)
                val b = to.|(x)
                val m = n.atMost(counter(a, "virus"))
                if (m > 0 && a != b && location.contains(a) && location.contains(b)) {
                    addCounter(a, "virus", -m)
                    addCounter(b, "virus", m)
                    log(Runner, "moves", m.hl, "virus counter" + (m != 1).??("s"), "from", a, "to", b)
                    trigger(CounterAdded(a, "virus"))
                    trigger(CounterAdded(b, "virus"))
                }
            }
            |(NextAction)

        case _ => None
    }
}


// ---------------------------------------------------------------------------------------------------------------------
// Base class for the rotated programs
// ---------------------------------------------------------------------------------------------------------------------

abstract class Rot(code : String) extends NC(code) {
    def virus(self : Card)(implicit g : Game) = g.virusCounters(self)

    // the ice currently encountered and rezzed
    def encounteredIce(implicit g : Game) : |[Card] = g.run.%(r => !r.ended && r.phase == EncounterPhase)./~(_.ice).%(i => g.isRezzed(i))

    // this card broke subroutines during the current encounter
    def broke(self : Card)(implicit g : Game) = g.hasFlag("broken-by", self)

    // start of the Runner's turn (the paid ability windows before the action phase)
    def startOfTurn(implicit g : Game) = g.active.isRunner && g.phase == "turn-begins" && g.run.none

    def trashOnPurge(self : Card, e : Event)(implicit g : Game) : $[ForcedAction] = e match {
        case Purged(_) if g.isInstalled(self) => $(TrashEffect(self, Runner, false))
        case _ => $
    }

    def stealth(n : Int)(implicit g : Game) = g.stealthCredits >= n

    def caissaFree(h : Card)(implicit g : Game) = !g.hosted(h).exists(x => g.has(x, "Caïssa"))

    def serverIce(c : Card)(implicit g : Game) : $[Card] = g.serverOf(c)./(s => g.ice(s)).|($)

    def iceIndex(c : Card)(implicit g : Game) = serverIce(c).indexOf(c)

    def host(self : Card)(implicit g : Game) : |[Card] = g.state(self).host

    def hostIceAbility(self : Card, label : String, candidates : $[Card], costs : $[Cost], click : Boolean)(implicit g : Game) =
        Ability(label, costs, $(ChooseCardEffect(Runner, candidates, HostIceSelfPurpose(self), "Choose a piece of ice to host " + g.asciiTitle(self) + " on", false)), click = click, ok = candidates.any, reason = "no piece of ice available")

    // "cloud" icebreakers use no memory with 2 or more link
    def cloudMemory(self : Card)(implicit g : Game) = (g.link >= 2).?(self.data.memory.|(0)).|(0)

    def runnerPrograms(implicit g : Game) = g.installedCards(Runner).%(c => c.data.isProgram && g.owner(c).isRunner)

    def hostedMemory(self : Card)(implicit g : Game) = g.hosted(self).%(c => c.data.isProgram && g.isInstalled(c))./(_.data.memory.|(0)).sum

    def centralRun(implicit g : Game) = g.run.exists(r => !r.ended && r.server.isCentral)

    def hasEtr(ice : Card)(implicit g : Game) = g.subs(ice).exists(_.label.toLowerCase.contains("end the run"))
}
