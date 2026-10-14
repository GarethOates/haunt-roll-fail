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
// Rotated (FFG-era) ice: base class, card actions and the registry (the cards are in cards-rot-ice-1/2.scala),
// ported from jinteki.net src/clj/game/cards/ice.clj. Generic mechanics: rules-ext-rot-ice.scala.
// ---------------------------------------------------------------------------------------------------------------------

abstract class RI(code : String) extends NC(code) {
    // ASCII title for prompts (strings inside actions must be ASCII)
    val name : String = Cards(code).title.toList.map {
        case 'ō' => 'o' ; case 'ā' => 'a' ; case 'ū' => 'u' ; case 'é' => 'e' ; case 'è' => 'e' ; case 'ã' => 'a' ; case 'â' => 'a' ; case 'á' => 'a'
        case 'í' => 'i' ; case 'ó' => 'o' ; case 'ú' => 'u' ; case 'ç' => 'c' ; case 'ñ' => 'n' ; case 'ý' => 'y'
        case ch if ch < 128 => ch
        case _ => '?'
    }.mkString

    def runOnThisServer(self : Card)(implicit g : Game) = g.run.exists(r => !r.ended && thisServer(self, r.server))

    def protecting(self : Card, s : Server)(implicit g : Game) = g.serverOf(self).has(s)

    def iceHere(self : Card)(implicit g : Game) : $[Card] = g.serverOf(self)./(g.ice).|($)

    def otherIce(self : Card)(implicit g : Game) : $[Card] = g.allIce.but(self)

    // "lose [click]: break n subroutines" (the register counts click breaks this turn for Zed)
    def bioroid(self : Card, n : Int)(implicit g : Game) : $[Ability] =
        $(Ability("break " + n + " subroutine" + (n != 1).??("s"), $(LoseClickCost(n)), n.times(BioroidBreakEffect(self) : ForcedAction) :+ RegisterEffect("click-break", 1) :+ AddFlagEffect(g.runFlag("click-break", None)), ok = encounteringThis(self) && g.unbrokenSubs(self).any, reason = "not encountered"))

    // "n[c]: break 1 subroutine"
    def payBreak(self : Card, n : Int)(implicit g : Game) : $[Ability] =
        $(Ability("break 1 subroutine", c(n), $(BioroidBreakEffect(self)), ok = encounteringThis(self) && g.unbrokenSubs(self).any, reason = "not encountered"))

    def trace(base : Int, label : String, success : $[ForcedAction], fail : $[ForcedAction] = $) = Sub("Trace[" + base + "]: " + label, TraceEffect(base, success, fail))

    def psi(label : String, differ : $[ForcedAction], equal : $[ForcedAction] = $) = Sub("Psi game: " + label, PsiEffect(equal, differ))

    def choose(prompt : String, alts : $[Alt]) = ChooseOneEffect(Runner, name + ": " + prompt, alts)

    def etrUnlessPays(n : Int)(implicit g : Game) = Sub("End the run unless the Runner pays " + n + "[c]", choose("choose one", $(Alt("Pay " + n + " credits", $(LoseCreditsEffect(Runner, n)), g.credits(Runner) >= n), Alt("End the run", $(EndRunEffect)))))

    def etrUnlessClicks(n : Int)(implicit g : Game) = Sub("End the run unless the Runner spends " + "[click]" * n, choose("choose one", $(Alt("Spend " + "[click]" * n, $(LoseClicksEffect(Runner, n)), g.clicks(Runner) >= n), Alt("End the run", $(EndRunEffect)))))

    def etrIfTagged = Sub("End the run if the Runner is tagged", EndRunIfTaggedEffect)

    def loseClickSub = Sub("The Runner loses [click]", LoseClicksEffect(Runner, 1))

    def coreSub(n : Int) = Sub("Do " + n + " core damage", core(n))

    def trashProgramSub(implicit g : Game) = Sub("Trash 1 installed program", RunnerTrashInstalledEffect("Program", Corp, name + ": choose a program to trash"))
    def trashHardwareSub(implicit g : Game) = Sub("Trash 1 installed piece of hardware", RunnerTrashInstalledEffect("Hardware", Corp, name + ": choose a piece of hardware to trash"))
    def trashResourceSub(implicit g : Game) = Sub("Trash 1 installed resource", RunnerTrashInstalledEffect("Resource", Corp, name + ": choose a resource to trash"))
    def trashInstalledSub(implicit g : Game) = Sub("Trash 1 installed Runner card", RunnerTrashInstalledEffect("", Corp, name + ": choose an installed Runner card to trash"))
    def runnerTrashProgramSub(implicit g : Game) = Sub("The Runner trashes 1 installed program", RunnerTrashInstalledEffect("Program", Runner, name + ": choose a program to trash"))
    def runnerTrashInstalledSub(implicit g : Game) = Sub("The Runner trashes 1 installed card", RunnerTrashInstalledEffect("", Runner, name + ": choose an installed card to trash"))

    def addToGripSub(implicit g : Game) = Sub("Add 1 installed Runner card to the grip", ChooseCardEffect(Corp, g.runnerInstalled, ReturnToHandPurpose, name + ": choose an installed Runner card to add to the grip", false))

    def programToStackSub(top : Boolean)(implicit g : Game) =
        Sub("Add 1 installed program to the " + top.?("top").|("bottom") + " of the stack", ChooseCardEffect(Corp, g.runnerInstalled.%(_.data.isProgram), top.?(TopOfDeckPurpose(Runner) : Purpose).|(BottomOfDeckPurpose(Runner)), name + ": choose a program", false))

    def powerAbility(self : Card, label : String, effects : $[ForcedAction], ok : Boolean = true, reason : String = "")(implicit g : Game) =
        Ability(label, $(CounterCost("power", 1)), effects, ok = g.isRezzed(self) && ok, reason = g.isRezzed(self).?(reason).|("not rezzed"))

    def powerCounterSub(self : Card) = Sub("Place 1 power counter on this ice", AddCounterEffect(self, "power", 1))

    def takeBadPub : $[ForcedAction] = $(BadPubEffect(1))

    def memoIs(self : Card, s : String)(implicit g : Game) = g.state(self).memo == s

    def memoInt(self : Card)(implicit g : Game) : Int = g.state(self).memo.toIntOption.|(0)

    def clearMemo(self : Card) = SetMemoEffect(self, "")

    // "resolve a subroutine on a rezzed piece of ice" (the Corp chooses the ice, then the subroutine)
    def resolveSubOn(self : Card, subtype : String, allowSelf : Boolean) = ResolveSubOnIceEffect(self, subtype, allowSelf)

    // the rotated ice enforce "the Runner cannot make another run this turn" (Excalibur, Uroboros) and "cannot draw" (Lockdown) while rezzed
    override def allowsRun(self : Card, s : Server)(implicit g : Game) = !(g.isRezzed(self) && g.hasFlag("no-run"))
    override def drawBonus(self : Card, f : Faction)(implicit g : Game) = (f.isRunner && g.isRezzed(self) && g.hasFlag("no-draw")).?(-99).|(0)

    // ---- morph ice (Changeling, Lycan, Wendigo): odd number of advancement counters = the other subtype
    def morphed(self : Card)(implicit g : Game) = g.advancement(self) % 2 == 1

    def tagTrace(base : Int, n : Int = 1) = trace(base, "Give the Runner " + n + " tag" + (n != 1).??("s"), $(tag(n)))

    def etrUnless(label : String, alt : String, effects : $[ForcedAction], ok : Boolean = true) = Sub("End the run unless the Runner " + label, choose("choose one", $(Alt(alt, effects, ok), Alt("End the run", $(EndRunEffect)))))

    def runnerLosesSub(n : Int) = Sub("The Runner loses " + n + "[c]", lose(Runner, n))

    def maybeDrawSub = Sub("You may draw 1 card", OptionalEffect(Corp, name + ": draw 1 card?", $(draw(Corp, 1)), $))

    // "the Runner spent [click] to break a subroutine this run" (bioroid breaks of the rotated ice set the run flag)
    def clickBrokeThisRun(implicit g : Game) = g.hasFlag("click-break")

    // install a card (from HQ / Archives) choosing among the servers where it is affordable (RotPurpose k = 98)
    def installSub(label : String, l : $[Card], optional : Boolean)(self : Card)(implicit g : Game) =
        Sub(label, ChooseCardEffect(Corp, l.%(corpInstallable), RotPurpose(self, 98), name + ": choose a card to install", optional))

    // the generic purposes of the base class: 98 = install paying, 99 = resolve a subroutine of the chosen ice
    override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) : Continue = p match {
        case RotPurpose(_, 98) =>
            cards.single.foreach { c =>
                val l = (c.data.isIce || c.data.isUpgrade).?(g.servers).|(g.remotes).%(s => g.installCost(c, |(s)) <= g.credits(Corp))
                val n = g.installCost(c, None) <= g.credits(Corp)
                if (l.any || n)
                    g.push(ChooseServerPayingEffect(c, 0, l, n, false))
            }
            NextAction
        case RotPurpose(_, 99) =>
            cards.single.foreach(c => g.push(ChooseSubEffect(Corp, c, false, false)))
            NextAction
        case _ => throw new Error("unhandled card purpose " + p)
    }

    // hero-to-hero / zero-to-hero ice: one copy of the subroutine per advancement counter (in front of the printed ones)
    def perAdvancement(self : Card, sub : Sub)(implicit g : Game) : $[Sub] = g.advancement(self).times(sub)
}

// card specific actions and purposes (k discriminates the uses inside a card)
case class RotSubAction(card : Card, k : Int) extends CardAction
case class RotPurpose(card : Card, k : Int) extends CardPurpose
case class RotServerAction(card : Card, server : Server, k : Int) extends CardAction


object RotIceCards {
    val all : $[CardImpl] = RotIceCards1.all ++ RotIceCards2.all
}
