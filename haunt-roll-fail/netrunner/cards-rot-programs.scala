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
// Rotated / FFG-era programs (rot), ported from jinteki.net src/clj/game/cards/programs.clj: shared helpers, card
// specific actions and purposes, and the registration object. The cards are in cards-rot-programs-1/2/3.scala.
// ---------------------------------------------------------------------------------------------------------------------

// ---- card specific actions and purposes -------------------------------------------------------------------------------
case class KeyholeAction(card : Card) extends CardAction
case class PawnAction(card : Card) extends CardAction
case class PawnPurpose(card : Card) extends CardPurpose
case class DevaPurpose(card : Card) extends CardPurpose
case class HeapInstallOfferAction(card : Card, ice : Card) extends CardAction
case class HeapBreakerXAction(card : Card, n : Int) extends CardAction
case class IncubatorPurpose(card : Card, n : Int) extends CardPurpose
case class SurferPurpose(card : Card) extends CardPurpose
case class ParasiteCheckAction(card : Card) extends CardAction
case class TrypanoCheckAction(card : Card) extends CardAction
case class CopycatPurpose(card : Card) extends CardPurpose
case class BugPurpose(card : Card) extends CardPurpose
case class GrapplingAction(card : Card, ice : Card, keep : Int) extends CardAction
case class WariAction(card : Card, subtype : String) extends CardAction
case class WariPurpose(card : Card, subtype : String) extends CardPurpose
case class RngKeyAction(card : Card, target : Card) extends CardAction
case class CrypsisAction(card : Card) extends CardAction
case class EquivocationAction(card : Card) extends CardAction
case class FalseEchoAction(card : Card, ice : Card) extends CardAction
case class PaintbrushPurpose(card : Card) extends CardPurpose
case class GiveSubtypeAction(card : Card, ice : Card, subtype : String) extends CardAction
case class ClearSubtypeAction(card : Card) extends CardAction
case class AnalogPurpose(card : Card) extends CardPurpose
case class SecretaryAction(card : Card) extends CardAction
case class SecretaryHostAction(card : Card, remaining : $[Card]) extends CardAction
case class SecretaryPurpose(card : Card, remaining : $[Card]) extends CardPurpose
case class SecretaryInstallPurpose(card : Card) extends CardPurpose
case class DhegdheerPurpose(card : Card) extends CardPurpose
case class TakobiPurpose(card : Card) extends CardPurpose
case class InversificatorPurpose(card : Card, ice : Card) extends CardPurpose
case class PersephoneAction(card : Card, ice : Card) extends CardAction
case class MassDriverAction(card : Card, ice : Card) extends CardAction


// ---- shared card shapes ------------------------------------------------------------------------------------------------

// Conspiracy breakers (Black Orchestra, MKUltra, Paperclip): may be installed from the heap when ice of their type is
// encountered; their paid ability raises the strength and grants a free break of up to n subroutines ("break" counter)
abstract class Conspiracy(code : String, val iceType : String) extends Rot(code) {
    override def activeInHeap(self : Card)(implicit g : Game) = true

    def freeBreaks(self : Card)(implicit g : Game) = g.counter(self, "break")

    override def breaks(self : Card)(implicit g : Game) =
        (g.isInstalled(self) && freeBreaks(self) > 0).$(BreakAbility(NoCost, freeBreaks(self), $(iceType), |("break up to " + freeBreaks(self) + " " + iceType.toLowerCase + " subroutine" + (freeBreaks(self) != 1).??("s") + " (paid)"), $(SetCounterEffect(self, "break", 0))))

    def conspiracyTriggers(self : Card, e : Event)(implicit g : Game) : $[ForcedAction] = e match {
        case EncounterIce(ice) if g.zone(self) == DiscardZone(Runner) && g.has(ice, iceType) => $(HeapInstallOfferAction(self, ice))
        case EncounterEnds(_) if g.isInstalled(self) && freeBreaks(self) > 0 => $(SetCounterEffect(self, "break", 0))
        case _ => $
    }

    override def triggers(self : Card, e : Event)(implicit g : Game) = conspiracyTriggers(self, e)

    def conspiracyPerform(a : CardAction)(implicit g : Game) : |[Continue] = a match {
        case HeapInstallOfferAction(self, ice) =>
            if (g.zone(self) == DiscardZone(Runner) && g.run.exists(r => !r.ended && r.ice.has(ice)) && !g.state(self).usedRun.has("no-install")
                && !g.installedCards(Runner).exists(_.code == self.code) && g.canAffordInstall(self, 0))
                g.push(OptionalEffect(Runner, "Install " + g.asciiTitle(self) + " from the heap?", $(RunnerInstallPayingEffect(self, None, 0)), $(MarkUsedRunEffect(self, "no-install"))))
            |(NextAction)
        case HeapBreakerXAction(self, n) =>
            g.push(PayCreditsEffect(Runner, n), PumpBreakerEffect(self, n, EncounterDuration), SetCounterEffect(self, "break", n))
            |(NextAction)
        case _ => None
    }

    override def perform(a : CardAction)(implicit g : Game) = conspiracyPerform(a).|(throw new Error("unhandled card action " + a))
}

// Deva breakers (Aghora, Sadyojata, Vamadeva): break 1 subroutine on ice satisfying a condition, swap with a deva from the grip
abstract class Deva(code : String) extends Rot(code) {
    def condition(ice : Card)(implicit g : Game) : Boolean
    def label : String

    override def breaks(self : Card)(implicit g : Game) = encounteredIce.exists(condition).$(BreakAbility(c(1), 1, $(), |("break 1 subroutine (" + label + ")")))
    override def pumps(self : Card)(implicit g : Game) = $(Pump(c(1), 1))
    override def abilities(self : Card)(implicit g : Game) = {
        val l = g.hand(Runner).%(x => g.has(x, "Deva"))
        $(Ability("swap with a deva program from the grip", c(2), $(ChooseCardEffect(Runner, l, DevaPurpose(self), "Choose a deva program to swap in", false)), ok = l.any && g.isInstalled(self), reason = "no deva program in the grip"))
    }
    override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
        cards.single.foreach(x => g.push(ReturnToHandEffect(p.card), RunnerInstallEffect(x, None, true)))
        NextAction
    }
}

// Cerberus suite: 4 power counters, 1 power counter: break up to 2 subroutines of the type, 1 credit: +1 strength
abstract class Cerberus(code : String, iceType : String) extends Rot(code) {
    override def initialCounters(self : Card)(implicit g : Game) = Map("power" -> 4)
    override def breaks(self : Card)(implicit g : Game) = $(BreakAbility($(CounterCost("power", 1)), 2, $(iceType)))
    override def pumps(self : Card)(implicit g : Game) = $(Pump(c(1), 1))
}

// Breaking and Entering suite (Crowbar, Shiv, Spike): cloud, strength = installed icebreakers, trash: break up to 3 subroutines
abstract class BreakAndEnter(code : String, iceType : String) extends Rot(code) {
    override def memory(self : Card)(implicit g : Game) = cloudMemory(self)
    override def strength(self : Card)(implicit g : Game) = installedBreakers
    override def breaks(self : Card)(implicit g : Game) = $(BreakAbility($(TrashSelfCost), 3, $(iceType)))
}

// GlobalSec suite (GS Striker M1, GS Shrike M2, GS Sherman M3): cloud, 2 credits: break any number, 2 credits: +3 strength
abstract class GlobalSec(code : String, iceType : String) extends Rot(code) {
    override def memory(self : Card)(implicit g : Game) = cloudMemory(self)
    override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(2), 0, $(iceType)))
    override def pumps(self : Card)(implicit g : Game) = $(Pump(c(2), 3))
}

// Central-only suite (Alias, Breach, Passport): the break ability works on central servers only
abstract class CentralOnly(code : String, iceType : String, breakCost : Int, breakN : Int, pumpCost : Int, pumpN : Int) extends Rot(code) {
    override def breaks(self : Card)(implicit g : Game) = centralRun.$(BreakAbility(c(breakCost), breakN, $(iceType), |("break " + (breakN == 1).?("1").|("up to " + breakN) + " " + iceType.toLowerCase + " subroutine" + (breakN != 1).??("s") + " (central servers only)")))
    override def pumps(self : Card)(implicit g : Game) = $(Pump(c(pumpCost), pumpN))
}

// Bird suite (Golden, Peregrine, Saker): 2 credits + return to the grip: derez the encountered ice after breaking all its subroutines
abstract class Bird(code : String, iceType : String, breakCost : Int, breakN : Int, pumpCost : Int, pumpN : Int) extends Rot(code) {
    override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(breakCost), breakN, $(iceType)))
    override def pumps(self : Card)(implicit g : Game) = $(Pump(c(pumpCost), pumpN))
    override def abilities(self : Card)(implicit g : Game) = {
        val ice = encounteredIce.%(i => g.has(i, iceType) && broke(self) && g.unbrokenSubs(i).none)
        $(Ability("derez the encountered " + iceType.toLowerCase + " and return to the grip", c(2), ice.$./~(i => $(DerezEffect(i), ReturnToHandEffect(self))), ok = ice.any && g.isInstalled(self), reason = "needs an encountered " + iceType.toLowerCase + " with all subroutines broken by this program"))
    }
}

// Fraud suite (Demara, Lustig): trash: bypass the encountered ice of the type
abstract class Fraud(code : String, iceType : String, breakCost : Int, breakN : Int, pumpCost : Int, pumpN : Int) extends Rot(code) {
    override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(breakCost), breakN, $(iceType)))
    override def pumps(self : Card)(implicit g : Game) = $(Pump(c(pumpCost), pumpN))
    override def abilities(self : Card)(implicit g : Game) = {
        val ok = encounteredIce.exists(i => g.has(i, iceType))
        $(Ability("bypass the encountered " + iceType.toLowerCase, $(TrashSelfCost), $(BypassEffect), ok = ok, reason = "not encountering a " + iceType.toLowerCase))
    }
}

// Khumalo suite (Musaazi, Yusuf): "any virus counter" (from any installed card) as break / pump cost; the pump is a
// Pump while this program hosts a counter, otherwise an ability spending a counter of another card
abstract class VirusBreaker(code : String, iceType : String) extends Rot(code) {
    override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
        case SuccessfulRun(_) => $(AddCounterEffect(self, "virus", 1))
        case _ => $
    }
    override def breaks(self : Card)(implicit g : Game) = (g.anyVirusCounters >= 1).$(BreakAbility(NoCost, 1, $(iceType), |("break 1 " + iceType.toLowerCase + " subroutine (any virus counter)"), $(SpendVirusEffect(self, 1, true))))
    override def pumps(self : Card)(implicit g : Game) = (g.counter(self, "virus") >= 1).$(Pump($(VirusCost(1)), 1))
    override def abilities(self : Card)(implicit g : Game) = {
        val ok = encounteredIce.any && g.isInstalled(self) && g.counter(self, "virus") == 0 && g.anyVirusCounters >= 1
        $(Ability("+1 strength (a virus counter from another card)", NoCost, $(SpendVirusEffect(self, 1, true), PumpBreakerEffect(self, 1, EncounterDuration)), ok = ok, reason = "needs an encounter and a virus counter on another card"))
    }
}

// Greek suite (Adept, Sage, Savant): strength = unused memory, 2 credits: break subroutines of two types
abstract class Greek(code : String, firstN : Int, firstType : String, secondN : Int, secondType : String) extends Rot(code) {
    override def strength(self : Card)(implicit g : Game) = g.memoryFree.atLeast(0)
    override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(2), firstN, $(firstType)), BreakAbility(c(2), secondN, $(secondType)))
}

// Wrestling suite (Laamb, Engolo): once per turn, pay 2 to make the encountered ice gain the type until the end of the encounter
abstract class Wrestler(code : String, iceType : String) extends Rot(code) {
    override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
        case EncounterIce(ice) if !g.has(ice, iceType) && !g.usedThisTurn(self, "wrestle") && g.availableCredits(Runner, |(self)) >= 2 =>
            $(OptionalEffect(Runner, "Pay 2 credits to make the encountered ice gain " + iceType + " (" + g.asciiTitle(self) + ")?", $(MarkUsedEffect(self, "wrestle"), PayCreditsEffect(Runner, 2), GiveSubtypeAction(self, ice, iceType)), $))
        case EncounterEnds(ice) if g.state(self).target.has(ice) => $(ClearSubtypeAction(self))
        case _ => $
    }
    override def perform(a : CardAction)(implicit g : Game) = RotProgramCards.subtypePerform(a)
}


object RotProgramCards {
    val all : $[CardImpl] = RotProgramCards1.all ++ RotProgramCards2.all ++ RotProgramCards3.all

    // give a subtype to a piece of ice remembered in the card's target / memo, and take it back
    def subtypePerform(a : CardAction)(implicit g : Game) : Continue = a match {
        case GiveSubtypeAction(self, ice, st) =>
            if (g.location.contains(ice)) {
                g.state(ice).extraSubtypes :+= st
                g.state(self).target = |(ice)
                g.state(self).memo = st
                g.log(ice, "gains", st.hl)
            }
            NextAction
        case ClearSubtypeAction(self) =>
            g.state(self).target.foreach { ice =>
                val l = g.state(ice).extraSubtypes
                val i = l.indexOf(g.state(self).memo)
                if (i >= 0)
                    g.state(ice).extraSubtypes = l.take(i) ++ l.drop(i + 1)
            }
            g.state(self).target = None
            g.state(self).memo = ""
            NextAction
        case _ => throw new Error("unhandled card action " + a)
    }
}
