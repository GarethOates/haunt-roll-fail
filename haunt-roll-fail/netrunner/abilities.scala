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
// Card implementation API -- see ENGINE.md
// ---------------------------------------------------------------------------------------------------------------------

trait CardImpl {
    def code : String

    def title = Cards(code).title

    // ---- constant (static) effects -----------------------------------------------------------------------------
    def memory(self : Card)(implicit g : Game) : Int = 0
    def link(self : Card)(implicit g : Game) : Int = 0
    def handSize(self : Card, f : Faction)(implicit g : Game) : Int = 0
    def strength(self : Card)(implicit g : Game) : Int = 0
    def iceStrengthMod(self : Card, ice : Card)(implicit g : Game) : Int = 0
    def breakerStrengthMod(self : Card, breaker : Card)(implicit g : Game) : Int = 0
    def rezCostMod(self : Card, card : Card)(implicit g : Game) : Int = 0
    def installCostMod(self : Card, card : Card)(implicit g : Game) : Int = 0
    def playCostMod(self : Card, card : Card)(implicit g : Game) : Int = 0
    def trashCostMod(self : Card, card : Card)(implicit g : Game) : Int = 0
    def advancementRequirementMod(self : Card, agenda : Card)(implicit g : Game) : Int = 0
    def agendaPointsBonus(self : Card)(implicit g : Game) : Int = 0
    def creditsUsableForRun(self : Card)(implicit g : Game) : Boolean = false
    def advanceable(self : Card)(implicit g : Game) : Boolean = self.data.isAgenda
    def initialCounters(self : Card)(implicit g : Game) : Map[String, Int] = Map()
    def hostRequirement(self : Card, host : Card)(implicit g : Game) : Boolean = false
    def mustBeHosted(self : Card)(implicit g : Game) : Boolean = false
    def canBeTrashedOnAccess(self : Card)(implicit g : Game) : Boolean = true
    def accessCostPaidByRunner(self : Card)(implicit g : Game) : $[Cost] = $

    // ---- hooks added for rwr / df / ur (see rules-ext-rwr-df-ur.scala) ----------------------------------------------
    def staysInPlay(self : Card)(implicit g : Game) : Boolean = false                         // operation/event stays in the play area after resolving (lockdowns, "for the remainder of the turn")
    def activeInHand(self : Card)(implicit g : Game) : Boolean = false                        // abilities usable while in hand (expendable cards)
    def activeInHeap(self : Card)(implicit g : Game) : Boolean = false                        // triggers while in the heap (Jeitinho)
    def canScore(self : Card)(implicit g : Game) : Boolean = true                             // agenda may be scored now
    def canRezIn(self : Card, ctx : String)(implicit g : Game) : |[Boolean] = None            // override the generic rez window rule (None = default: non-ice anytime, ice when approached)
    def runCost(self : Card, server : Server)(implicit g : Game) : $[Cost] = $                // additional costs to run a server
    def stealCostMod(self : Card, agenda : Card)(implicit g : Game) : $[Cost] = $             // additional costs to steal an agenda (global)
    def extraSubroutines(self : Card, ice : Card)(implicit g : Game) : $[Sub] = $             // subroutines granted to other ice
    def breakAllowed(self : Card, breaker : Card, ice : Card)(implicit g : Game) : Boolean = true   // may breaker break (another) subroutine on ice now
    def beforeDraw(self : Card, f : Faction, n : Int)(implicit g : Game) : $[ForcedAction] = $     // effects resolved before f draws n cards

    // ---- triggered abilities: effects to queue when an event happens ------------------------------------------------
    def triggers(self : Card, e : Event)(implicit g : Game) : $[ForcedAction] = $

    // ---- paid abilities ---------------------------------------------------------------------------------------------
    def abilities(self : Card)(implicit g : Game) : $[Ability] = $
    def runnerAbilities(self : Card)(implicit g : Game) : $[Ability] = $
    def accessAbility(self : Card, target : Card)(implicit g : Game) : |[Ability] = None
    def preventions(self : Card, kind : String, n : Int)(implicit g : Game) : $[Ability] = $

    // ---- ice ----------------------------------------------------------------------------------------------------------
    def subroutines(self : Card)(implicit g : Game) : $[Sub] = $
    def onEncounter(self : Card)(implicit g : Game) : $[ForcedAction] = $

    // ---- icebreakers ------------------------------------------------------------------------------------------------
    def breaks(self : Card)(implicit g : Game) : $[BreakAbility] = $
    def pumps(self : Card)(implicit g : Game) : $[Pump] = $

    // ---- lifecycle ----------------------------------------------------------------------------------------------------
    def onPlay(self : Card)(implicit g : Game) : $[ForcedAction] = $
    def canPlay(self : Card)(implicit g : Game) : Boolean = true
    def makesRun(self : Card)(implicit g : Game) : Boolean = false
    def onScore(self : Card)(implicit g : Game) : $[ForcedAction] = $
    def onSteal(self : Card)(implicit g : Game) : $[ForcedAction] = $
    def onAccess(self : Card)(implicit g : Game) : $[ForcedAction] = $
    def onRez(self : Card)(implicit g : Game) : $[ForcedAction] = $
    def onInstall(self : Card)(implicit g : Game) : $[ForcedAction] = $
    def onTrash(self : Card, by : Faction)(implicit g : Game) : $[ForcedAction] = $
    def onEncounterEnds(self : Card)(implicit g : Game) : $[ForcedAction] = $

    // ---- card specific actions and choices --------------------------------------------------------------------------
    def perform(a : CardAction)(implicit g : Game) : Continue = throw new Error("unhandled card action " + a)
    def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) : Continue = throw new Error("unhandled card purpose " + p)
}

object GenericImpl extends CardImpl {
    val code = ""
}

object CardImpls {
    lazy val all : Map[String, CardImpl] = (SGCards.all ++ RWRCards.all ++ DFCards.all ++ URCards.all)./(i => i.code -> i).toMap

    def apply(code : String) : CardImpl = all.get(code).|(GenericImpl)

    def has(code : String) = all.contains(code)
}


// ---------------------------------------------------------------------------------------------------------------------
// Convenience helpers for writing cards
// ---------------------------------------------------------------------------------------------------------------------

object Helpers {
    def gain(f : Faction, n : Int) = GainCreditsEffect(f, n)
    def lose(f : Faction, n : Int) = LoseCreditsEffect(f, n)
    def draw(f : Faction, n : Int) = DrawEffect(f, n)
    def net(n : Int) = DamageEffect("net", n)
    def meat(n : Int) = DamageEffect("meat", n)
    def core(n : Int) = DamageEffect("core", n)
    def tag(n : Int) = TagsEffect(n)
    def etr = EndRunEffect
    def once(key : String) = |(key)

    val NoCost : $[Cost] = $
    def c(n : Int) : $[Cost] = $(CreditCost(n))
    def click(n : Int = 1) : $[Cost] = $(ClickCost(n))
    def clickc(n : Int) : $[Cost] = $(ClickCost(1), CreditCost(n))

    def sub(label : String, effect : ForcedAction) = Sub(label, effect)
    def etrSub = Sub("End the run", EndRunEffect)
    def netSub(n : Int) = Sub("Do " + n + " net damage", DamageEffect("net", n))
    def tagSub(n : Int) = Sub("Give the Runner " + n + " tag", TagsEffect(n))
    def gainSub(n : Int) = Sub("Gain " + n + "[c]", GainCreditsEffect(Corp, n))
    def loseSub(n : Int) = Sub("Runner loses " + n + "[c]", LoseCreditsEffect(Runner, n))
}
