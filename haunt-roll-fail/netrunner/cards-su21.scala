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
// System Update 2021 (su21) card implementations, ported from jinteki.net src/clj/game/cards/*.clj
// ---------------------------------------------------------------------------------------------------------------------

// shared helpers for the su21 / ms sets
abstract class Ext(code : String) extends SG(code) {
    def encountering(self : Card)(implicit g : Game) : |[Card] = g.run.%(r => !r.ended && r.phase == EncounterPhase)./~(_.ice)

    def bioroidBreak(self : Card)(implicit g : Game) : $[Ability] =
        $(Ability("break 1 subroutine", $(LoseClickCost(1)), $(BioroidBreakEffect(self)), ok = g.run.exists(r => r.ice.has(self) && r.phase == EncounterPhase) && g.unbrokenSubs(self).any, reason = "not encountered"))

    def trashProgramSub(implicit g : Game) : Sub = {
        val l = g.installedCards(Runner).%(_.data.isProgram)
        Sub("Trash 1 installed program", ChooseCardEffect(Corp, l, TrashPurpose(Corp), "Choose an installed program to trash", false))
    }

    def runProgramInstallable(c : Card)(implicit g : Game) = c.data.isProgram && !g.impl(c).mustBeHosted(c) && g.memoryFree >= c.data.memory.|(0)

    def rezzedDuringRunOnThisServer(self : Card)(implicit g : Game) = g.run.exists(r => !r.ended && thisServer(self, r.server))

    def firstHQRun(implicit g : Game) = g.successfulRunsThisTurn.count(HQ) == 1
}

// card specific actions and purposes
case class StevePurpose(card : Card) extends CardPurpose
case class SteveCorpPurpose(card : Card, a : Card, b : Card) extends CardPurpose
case class CareerFairPurpose(card : Card) extends CardPurpose
case class ForgedOrdersPurpose(card : Card) extends CardPurpose
case class FemmePurpose(card : Card) extends CardPurpose
case class SneakdoorAction(card : Card) extends CardAction
case class AylaSetupAction(card : Card) extends CardAction
case class AylaPurpose(card : Card) extends CardPurpose
case class TestRunChooseAction(card : Card, stack : Boolean) extends CardAction
case class TestRunPurpose(card : Card, stack : Boolean) extends CardPurpose
case class AesopPurpose(card : Card) extends CardPurpose
case class ArchitectsPurpose(card : Card) extends CardPurpose
case class RavanaAction(card : Card) extends CardAction
case class TroubleshooterAction(card : Card, server : Server) extends CardAction
case class TroubleshooterXAction(card : Card, server : Server, x : Int) extends CardAction
case class TroubleshooterPurpose(card : Card, x : Int) extends CardPurpose
case class CelebrityGiftPurpose(card : Card) extends CardPurpose
case class TrickTargetPurpose(card : Card) extends CardPurpose
case class TrickSourcePurpose(card : Card, target : Card) extends CardPurpose
case class PsychographicsAction(card : Card, x : Int) extends CardAction
case class OaktownAction(card : Card) extends CardAction
case class HortumGainAction(card : Card) extends CardAction
case class HortumEndAction(card : Card) extends CardAction
case class RetrievalRunPurpose(card : Card) extends CardPurpose


object SU21Cards {
    // ------------------------------------------------------------------------------------------------------------
    // Runner
    // ------------------------------------------------------------------------------------------------------------

    object Quetzal extends Ext("31001") {
        override def breaks(self : Card)(implicit g : Game) =
            (!g.usedThisTurn(self, "quetzal")).$(BreakAbility($, 1, $("Barrier"), |("break 1 barrier subroutine (once per turn)"), $(MarkUsedEffect(self, "quetzal"))))
    }

    object ReinaRoja extends Ext("31002") {
        override def rezCostMod(self : Card, card : Card)(implicit g : Game) = (card.data.isIce && !g.isRezzed(card) && g.reg("ice-rezzed") == 0).?(1).|(0)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Rezzed(c) if c.data.isIce => $(RegisterEffect("ice-rezzed", 1))
            case _ => $
        }
    }

    object EnPassant extends Ext("31003") {
        override def canPlay(self : Card)(implicit g : Game) = g.successfulRunsThisTurn.any
        override def onPlay(self : Card)(implicit g : Game) = {
            val l = g.lastRun.$./~(r => g.ice(r.server).drop(r.position)).%(c => !g.isRezzed(c))
            l.any.$(ChooseCardEffect(Runner, l, TrashPurpose(Runner), "En Passant: choose an unrezzed piece of ice passed during the last run", true))
        }
    }

    object RetrievalRun extends Ext("31004") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = $(RunEffect(Archives, |(self)))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(Archives) if thisCardRun(self) && g.discard(Runner).exists(runProgramInstallable) =>
                $(OptionalEffect(Runner, "Retrieval Run: install a program from the heap instead of breaching Archives?", $(ReplaceBreachEffect, ChooseCardEffect(Runner, g.discard(Runner).%(runProgramInstallable), RetrievalRunPurpose(self), "Choose a program to install, ignoring all costs", true)), $))
            case _ => $
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.foreach(c => g.push(RunnerInstallEffect(c, None, true)))
            NextAction
        }
    }

    object Clot extends Ext("31005") {
        override def preventsScoring(self : Card, agenda : Card)(implicit g : Game) = g.state(agenda).installedTurn == g.turn
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Purged(_) => $(TrashEffect(self, Runner, false))
            case _ => $
        }
    }

    object Corroder extends Ext("31006") {
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(1), 1, $("Barrier")))
        override def pumps(self : Card)(implicit g : Game) = $(Pump(c(1), 1))
    }

    object Imp extends Ext("31007") {
        override def initialCounters(self : Card)(implicit g : Game) = Map("virus" -> 2)
        override def accessAbility(self : Card, target : Card)(implicit g : Game) =
            (!g.zone(target).is[DiscardZone] && g.run.exists(!_.cannotStealOrTrash)).?(Ability("trash the accessed card", $(VirusCost(1)), $(TrashEffect(target, Runner, true)), once = once("imp"), ok = !g.usedThisTurn(self, "imp") && g.counter(self, "virus") > 0, reason = "once per turn, needs a virus counter"))
    }

    object Mimic extends Ext("31008") {
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(1), 1, $("Sentry")))
    }

    object IceCarver extends Ext("31009") {
        override def iceStrengthMod(self : Card, ice : Card)(implicit g : Game) = encountering(self).has(ice).?(-1).|(0)
    }

    object LiberatedAccount extends Ext("31010") {
        override def initialCounters(self : Card)(implicit g : Game) = Map("credit" -> 16)
        override def triggers(self : Card, e : Event)(implicit g : Game) = trashOnEmpty(self, e, "credit")
        override def abilities(self : Card)(implicit g : Game) = $(Ability("take 4 credits", click(), $(TakeCreditsEffect(Runner, self, 4)), click = true))
    }

    object Scrubber extends Ext("31011") {
        override def initialCounters(self : Card)(implicit g : Game) = Map("credit" -> 2)
        override def creditsUsableFor(self : Card, purpose : String)(implicit g : Game) = purpose.startsWith("trash-")
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) => $(RefillCreditsEffect(self, 2))
            case _ => $
        }
    }

    object Xanadu extends Ext("31012") {
        override def rezCostMod(self : Card, card : Card)(implicit g : Game) = card.data.isIce.?(1).|(0)
    }

    object KenTenma extends Ext("31013") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Played(Runner, c) if g.has(c, "Run") && !g.usedThisTurn(self, "ken") => $(MarkUsedEffect(self, "ken"), gain(Runner, 1))
            case _ => $
        }
    }

    object SteveCambridge extends Ext("31014") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(HQ) if firstHQRun && g.discard(Runner).num >= 2 =>
                $(OptionalEffect(Runner, "Steve Cambridge: choose 2 cards in the heap (the Corp removes one, you add the other to the grip)?", $(ChooseCardsEffect(Runner, g.discard(Runner), 2, 2, StevePurpose(self), "Choose 2 cards in the heap")), $))
            case _ => $
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case StevePurpose(self) =>
                cards match {
                    case $(a, b) => g.push(ChooseCardEffect(Corp, $(a, b), SteveCorpPurpose(self, a, b), "Steve Cambridge: choose a card to remove from the game", false))
                    case _ =>
                }
                NextAction
            case SteveCorpPurpose(self, a, b) =>
                cards.single.foreach { c =>
                    val other = (c == a).?(b).|(a)
                    g.push(RemoveFromGameEffect(c), AddToHandEffect(other), MessageEffect(Runner, "adds " + g.asciiTitle(other) + " to the grip"))
                }
                NextAction
        }
    }

    object CareerFair extends Ext("31015") {
        override def onPlay(self : Card)(implicit g : Game) = {
            val l = g.hand(Runner).%(c => c.data.isResource && g.canSpend(Runner, (g.installCost(c, None) - 3).atLeast(0), Spend.install(c)))
            l.any.$(ChooseCardEffect(Runner, l, CareerFairPurpose(self), "Career Fair: choose a resource to install, paying 3 less", true))
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.foreach(c => g.push(InstallDiscountEffect(c, 3)))
            NextAction
        }
    }

    object EmergencyShutdown extends Ext("31016") {
        override def canPlay(self : Card)(implicit g : Game) = g.successfulRunsThisTurn.has(HQ)
        override def onPlay(self : Card)(implicit g : Game) = {
            val l = g.rezzedIce
            l.any.$(ChooseCardEffect(Runner, l, DerezPurpose, "Emergency Shutdown: choose a piece of ice to derez", false))
        }
    }

    object ForgedActivationOrders extends Ext("31017") {
        override def onPlay(self : Card)(implicit g : Game) = {
            val l = g.allIce.%(c => !g.isRezzed(c))
            l.any.$(ChooseCardEffect(Runner, l, ForgedOrdersPurpose(self), "Forged Activation Orders: choose an unrezzed piece of ice", false))
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach { c =>
                g.push(ChooseOneEffect(Corp, "Forged Activation Orders: rez or trash the ice?", $(
                    Alt("Rez it", $(RezEffect(c, false)), g.canSpend(Corp, g.rezCost(c), Spend.rez(c)) && g.impl(c).canRez(c)),
                    Alt("Trash it", $(TrashEffect(c, Corp, false))))))
            }
            NextAction
        }
    }

    object InsideJob extends Ext("31018") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = $(RunAnyServerEffect(|(self), g.servers))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case EncounterIce(ice) if thisCardRun(self) && !g.state(self).usedRun.has("inside-job") =>
                $(MarkRunUsedEffect(self, "inside-job"), MessageEffect(Runner, "bypasses the ice (Inside Job)"), BypassEffect)
            case _ => $
        }
    }

    object Legwork extends Ext("31019") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = $(RunEffect(HQ, |(self)))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(HQ) if thisCardRun(self) => $(AccessBonusEffect(HQ, 2))
            case _ => $
        }
    }

    object Networking extends Ext("31020") {
        override def onPlay(self : Card)(implicit g : Game) =
            $(RemoveTagsEffect(1)) ++ (g.credits(Runner) >= 1).$(OptionalEffect(Runner, "Networking: pay 1 credit to add it to the grip?", $(LoseCreditsEffect(Runner, 1), AddToHandEffect(self)), $))
    }

    object Abagnale extends Ext("31021") {
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(1), 1, $("Code Gate")))
        override def pumps(self : Card)(implicit g : Game) = $(Pump(c(2), 2))
        override def abilities(self : Card)(implicit g : Game) = {
            val ok = encountering(self).exists(i => g.has(i, "Code Gate"))
            $(Ability("bypass the encountered code gate", $(TrashSelfCost), $(BypassEffect), ok = ok, reason = "not encountering a code gate"))
        }
    }

    object FemmeFatale extends Ext("31022") {
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(1), 1, $("Sentry")))
        override def pumps(self : Card)(implicit g : Game) = $(Pump(c(2), 1))
        override def onInstall(self : Card)(implicit g : Game) = g.allIce.any.$(ChooseCardEffect(Runner, g.allIce, FemmePurpose(self), "Femme Fatale: choose a piece of ice to bypass", true))
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.foreach(c => g.push(SetTargetEffect(p.card, |(c))))
            NextAction
        }
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case EncounterIce(ice) if g.state(self).target.has(ice) =>
                val n = g.impl(ice).subroutines(ice).num
                g.canSpend(Runner, n, Spend.ability(self)).$(OptionalEffect(Runner, "Femme Fatale: pay " + n + " credits to bypass the ice?", $(SpendEffect(Runner, n, Spend.ability(self), 0, $(BypassEffect))), $))
            case _ => $
        }
    }

    object SneakdoorBeta extends Ext("31023") {
        override def abilities(self : Card)(implicit g : Game) = $(Ability("run Archives (successful runs become runs on HQ)", click(), $(RunEffect(Archives, |(self))), click = true))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case RunWouldSucceed(Archives) if thisCardRun(self) => $(SneakdoorAction(self))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case SneakdoorAction(self) =>
                g.run.foreach { r =>
                    if (r.server == Archives) {
                        r.server = HQ
                        g.log(self, "changes the attacked server to", HQ)
                    }
                }
                NextAction
        }
    }

    object SecurityTesting extends Ext("31024") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) =>
                $(ChooseOneEffect(Runner, "Security Testing: choose a server", g.servers./(s => Alt(s.name, $(SetChoiceEffect(self, s.name)))) :+ Alt("No server", $(SetChoiceEffect(self, "")))))
            case SuccessfulRun(s) if g.state(self).choice == s.name && g.successfulRunsThisTurn.count(s) == 1 =>
                $(MessageEffect(Runner, "uses Security Testing"), ReplaceBreachEffect, gain(Runner, 2))
            case TurnEnds(Runner) => $(SetChoiceEffect(self, ""))
            case _ => $
        }
    }

    object Ayla extends Ext("31025") {
        override def onSetup(self : Card)(implicit g : Game) = $(AylaSetupAction(self))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case AylaSetupAction(self) =>
                val l = g.deck(Runner).take(6)
                if (l.num >= 4)
                    g.push(ChooseCardsEffect(Runner, l, 4, 4, AylaPurpose(self), "Ayla: choose 4 of the top 6 cards to set aside (the other 2 are shuffled back)"), ShuffleDeckEffect(Runner))
                NextAction
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.foreach(c => g.moveTo(c, HostedZone(p.card)))
            g.log(Runner, "sets aside", cards.num.cards, "with", p.card)
            NextAction
        }
        override def abilities(self : Card)(implicit g : Game) = {
            val l = g.hosted(self)
            $(Ability("add a set-aside card to the grip", click(), $(ChooseCardEffect(Runner, l, AddToHandPurpose(Runner, false), "Ayla: choose a set-aside card", false)), click = true, ok = l.any, reason = "no set-aside cards"))
        }
    }

    object Kit extends Ext("31026") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case EncounterIce(ice) if !g.usedThisTurn(self, "kit") =>
                $(MarkUsedEffect(self, "kit")) ++ (!g.has(ice, "Code Gate")).$(AddSubtypeEffect(ice, "Code Gate"), SetTargetEffect(self, |(ice)))
            case RunEnds(_, _, _) => g.state(self).target.$./(i => RemoveSubtypeEffect(i, "Code Gate")) :+ SetChoiceEffect(self, "")
            case _ => $
        }
    }

    object Diesel extends Ext("31027") {
        override def onPlay(self : Card)(implicit g : Game) = $(draw(Runner, 3))
    }

    object TestRun extends Ext("31028") {
        override def onPlay(self : Card)(implicit g : Game) = $(ChooseOneEffect(Runner, "Test Run: search the stack or the heap?", $(
            Alt("Stack", $(TestRunChooseAction(self, true)), g.deck(Runner).exists(runProgramInstallable)),
            Alt("Heap", $(TestRunChooseAction(self, false)), g.discard(Runner).exists(runProgramInstallable)))))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case TestRunChooseAction(self, stack) =>
                val l = (stack.?(g.deck(Runner)).|(g.discard(Runner))).%(runProgramInstallable).sortBy(_.title)
                g.push(ChooseCardEffect(Runner, l, TestRunPurpose(self, stack), "Test Run: choose a program to install, ignoring all costs", true))
                if (stack)
                    g.push(ShuffleDeckEffect(Runner))
                NextAction
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.foreach(c => g.push(RunnerInstallEffect(c, None, true), SetChoiceEffect(c, "test-run"), MessageEffect(Runner, "will add " + g.asciiTitle(c) + " to the top of the stack at the end of the turn")))
            NextAction
        }
    }

    object MakersEye extends Ext("31029") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = $(RunEffect(RnD, |(self)))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(RnD) if thisCardRun(self) => $(AccessBonusEffect(RnD, 2))
            case _ => $
        }
    }

    object Atman extends Ext("31030") {
        override def onInstall(self : Card)(implicit g : Game) =
            $(ChooseOneEffect(Runner, "Atman: spend credits to place that many power counters", 0.to(g.credits(Runner)).$./(n => Alt(n + " credits", (n > 0).$(LoseCreditsEffect(Runner, n), AddCounterEffect(self, "power", n))))))
        override def strength(self : Card)(implicit g : Game) = g.counter(self, "power")
        override def breaks(self : Card)(implicit g : Game) =
            encountering(self).exists(i => g.iceStrength(i) == g.breakerStrength(self)).$(BreakAbility(c(1), 1, $(), |("break 1 subroutine (ice of equal strength)")))
    }

    object Chameleon extends Ext("31031") {
        override def onInstall(self : Card)(implicit g : Game) =
            $(ChooseOneEffect(Runner, "Chameleon: choose a subtype", $("Barrier", "Code Gate", "Sentry")./(s => Alt(s, $(SetChoiceEffect(self, s))))))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnEnds(Runner) => $(MessageEffect(Runner, "adds Chameleon to the grip"), AddToHandEffect(self))
            case _ => $
        }
        override def breaks(self : Card)(implicit g : Game) = {
            val st = g.state(self).choice
            $(BreakAbility(c(1), 1, (st != "").?($(st)).|($), |("break 1 " + (st != "").?(st.toLowerCase).|("(no subtype chosen)") + " subroutine")))
        }
    }

    object Egret extends Ext("31032") {
        override def mustBeHosted(self : Card)(implicit g : Game) = true
        override def hostRequirement(self : Card, host : Card)(implicit g : Game) = host.data.isIce && g.isRezzed(host)
        override def onInstall(self : Card)(implicit g : Game) = g.state(self).host.$./~(h => $("Barrier", "Code Gate", "Sentry")./(s => AddSubtypeEffect(h, s)))
    }

    object GordianBlade extends Ext("31033") {
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(1), 1, $("Code Gate")))
        override def pumps(self : Card)(implicit g : Game) = $(Pump(c(1), 1, RunDuration, |("+1 strength for the remainder of the run")))
    }

    object Paricia extends Ext("31034") {
        override def initialCounters(self : Card)(implicit g : Game) = Map("credit" -> 2)
        override def creditsUsableFor(self : Card, purpose : String)(implicit g : Game) = purpose == "trash-asset"
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) => $(RefillCreditsEffect(self, 2))
            case _ => $
        }
    }

    object AesopsPawnshop extends Ext("31035") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) if g.otherInstalled(Runner, self).any =>
                $(OptionalEffect(Runner, "Aesop's Pawnshop: trash another installed card to gain 3 credits?", $(ChooseCardEffect(Runner, g.otherInstalled(Runner, self), AesopPurpose(self), "Choose a card to trash", false)), $))
            case _ => $
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.foreach { c =>
                g.trashCard(c, Runner, false)
                g.push(gain(Runner, 3))
            }
            NextAction
        }
    }

    object ProfessionalContacts extends Ext("31036") {
        override def abilities(self : Card)(implicit g : Game) = $(Ability("gain 1 credit and draw 1 card", click(), $(gain(Runner, 1), draw(Runner, 1)), click = true))
    }

    object DirtyLaundry extends Ext("31037") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = $(RunAnyServerEffect(|(self), g.servers))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case RunEnds(_, true, _) if thisCardRun(self) => $(gain(Runner, 5))
            case _ => $
        }
    }

    object PrepaidVoicePAD extends Ext("31038") {
        override def initialCounters(self : Card)(implicit g : Game) = Map("credit" -> 1)
        override def creditsUsableFor(self : Card, purpose : String)(implicit g : Game) = purpose == "play-event"
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) => $(RefillCreditsEffect(self, 1))
            case _ => $
        }
    }

    object EarthriseHotel extends Ext("31039") {
        override def initialCounters(self : Card)(implicit g : Game) = Map("power" -> 3)
        override def triggers(self : Card, e : Event)(implicit g : Game) = trashOnEmpty(self, e, "power") ++ (e match {
            case TurnBegins(Runner) if g.counter(self, "power") > 0 => $(AddCounterEffect(self, "power", -1), draw(Runner, 2))
            case _ => $
        })
    }

    // ------------------------------------------------------------------------------------------------------------
    // Corp
    // ------------------------------------------------------------------------------------------------------------

    object ArchitectsOfTomorrow extends Ext("31040") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case PassIce(ice) if g.isRezzed(ice) && g.has(ice, "Bioroid") && !g.usedThisTurn(self, "aot") =>
                val l = g.installedCards(Corp).%(c => !g.isRezzed(c) && g.has(c, "Bioroid") && g.canSpend(Corp, (g.rezCost(c) - 4).atLeast(0), Spend.rez(c)) && g.impl(c).canRez(c))
                $(MarkUsedEffect(self, "aot")) ++ l.any.$(ChooseCardEffect(Corp, l, ArchitectsPurpose(self), "Architects of Tomorrow: rez a bioroid card paying 4 less?", true))
            case _ => $
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.foreach(c => g.push(RezDiscountEffect(c, 4)))
            NextAction
        }
    }

    object ProjectVitruvius extends Ext("31041") {
        override def onScore(self : Card)(implicit g : Game) = {
            val n = (g.lastScoredAdvancement - 3).atLeast(0)
            (n > 0).$(AddCounterEffect(self, "agenda", n))
        }
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("add 1 card from Archives to HQ", $(CounterCost("agenda", 1)), $(ChooseCardEffect(Corp, g.discard(Corp), AddToHandPurpose(Corp, false), "Project Vitruvius: choose a card in Archives", false)), ok = g.discard(Corp).any && g.counter(self, "agenda") > 0, reason = "needs an agenda counter and a card in Archives"))
    }

    object MarilynCampaign extends Ext("31042") {
        override def initialCounters(self : Card)(implicit g : Game) = Map("credit" -> 8)
        override def triggers(self : Card, e : Event)(implicit g : Game) = trashOnEmpty(self, e, "credit") ++ (e match {
            case TurnBegins(Corp) if g.isRezzed(self) => $(TakeCreditsEffect(Corp, self, 2))
            case _ => $
        })
        override def onTrash(self : Card, by : Faction)(implicit g : Game) =
            $(OptionalEffect(Corp, "Marilyn Campaign: shuffle it into R&D instead of Archives?", $(MoveEffect(self, DeckZone(Corp), false), ShuffleDeckEffect(Corp)), $))
    }

    object Eli extends Ext("31043") {
        override def subroutines(self : Card)(implicit g : Game) = $(etrSub, etrSub)
        override def runnerAbilities(self : Card)(implicit g : Game) = bioroidBreak(self)
    }

    object Magnet extends Ext("31044") {
        override def onRez(self : Card)(implicit g : Game) = {
            val l = g.allIce.but(self)./~(i => g.hosted(i)).%(_.data.isProgram)
            l.any.$(ChooseCardEffect(Corp, l, MoveHostedPurpose(self), "Magnet: choose a program hosted on another piece of ice to host on Magnet", false))
        }
        override def disablesHosted(self : Card)(implicit g : Game) = g.isRezzed(self)
        override def subroutines(self : Card)(implicit g : Game) = $(etrSub)
    }

    object Ravana extends Ext("31045") {
        override def subroutines(self : Card)(implicit g : Game) = $(Sub("Resolve 1 subroutine on another rezzed bioroid ice", RavanaAction(self)), Sub("Resolve 1 subroutine on another rezzed bioroid ice", RavanaAction(self)))
        override def runnerAbilities(self : Card)(implicit g : Game) = bioroidBreak(self)
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case RavanaAction(self) =>
                val alts = g.rezzedIce.but(self).%(i => g.has(i, "Bioroid"))./~(i => g.impl(i).subroutines(i).%!(_.effect.is[RavanaAction])./(s => Alt(g.asciiTitle(i) + ": " + s.label, $(s.effect))))
                if (alts.any)
                    g.push(ChooseOneEffect(Corp, "Ravana 1.0: choose a subroutine to resolve", alts))
                NextAction
        }
    }

    object Rototurret extends Ext("31046") {
        override def subroutines(self : Card)(implicit g : Game) = $(trashProgramSub, etrSub)
    }

    object ArchivedMemories extends Ext("31047") {
        override def onPlay(self : Card)(implicit g : Game) = g.discard(Corp).any.$(ChooseCardEffect(Corp, g.discard(Corp), AddToHandPurpose(Corp, false), "Archived Memories: choose a card in Archives to add to HQ", false))
    }

    object BioticLabor extends Ext("31048") {
        override def onPlay(self : Card)(implicit g : Game) = $(GainClicksEffect(Corp, 2))
    }

    object CorporateTroubleshooter extends Ext("31049") {
        override def abilities(self : Card)(implicit g : Game) = {
            val s = g.serverOf(self)
            val ok = g.isRezzed(self) && s.exists(x => g.ice(x).exists(g.isRezzed))
            $(Ability("pay X credits: a rezzed piece of ice protecting this server gets +X strength this turn", $(TrashSelfCost), s.$./(x => TroubleshooterAction(self, x)), ok = ok, reason = "needs a rezzed piece of ice protecting this server"))
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case TroubleshooterAction(self, s) =>
                g.push(ChooseOneEffect(Corp, "Corporate Troubleshooter: choose X", 0.to(g.credits(Corp)).$./(x => Alt("X = " + x, $(TroubleshooterXAction(self, s, x))))))
                NextAction
            case TroubleshooterXAction(self, s, x) =>
                g.credits += Corp -> (g.credits(Corp) - x)
                g.log(Corp, "pays", x.credits)
                g.push(ChooseCardEffect(Corp, g.ice(s).%(g.isRezzed), TroubleshooterPurpose(self, x), "Choose a rezzed piece of ice", false))
                NextAction
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case TroubleshooterPurpose(_, x) =>
                cards.foreach(i => g.push(PumpIceEffect(i, x, TurnDuration)))
                NextAction
        }
    }

    object PersonalEvolution extends Ext("31050") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case AgendaScored(_) | AgendaStolen(_) => $(MessageEffect(Corp, "uses Jinteki: Personal Evolution"), net(1))
            case _ => $
        }
    }

    object HouseOfKnives extends Ext("31051") {
        override def onScore(self : Card)(implicit g : Game) = $(AddCounterEffect(self, "agenda", 3))
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("do 1 net damage (once per run)", $(CounterCost("agenda", 1)), $(MarkRunUsedEffect(self, "hok"), net(1)), ok = g.run.exists(r => !r.ended) && !g.state(self).usedRun.has("hok"), reason = "only during a run, once per run"))
    }

    object NiseiMkII extends Ext("31052") {
        override def onScore(self : Card)(implicit g : Game) = $(AddCounterEffect(self, "agenda", 1))
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("end the run", $(CounterCost("agenda", 1)), $(EndRunEffect), ok = g.run.exists(r => !r.ended), reason = "only during a run"))
    }

    object Ronin extends Ext("31053") {
        override def advanceable(self : Card)(implicit g : Game) = true
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("do 3 net damage", $(ClickCost(1), TrashSelfCost), $(net(3)), click = true, ok = g.isRezzed(self) && g.advancement(self) >= 4, reason = "needs 4 advancement counters and to be rezzed"))
    }

    object Snare extends Ext("31054") {
        override def onAccess(self : Card)(implicit g : Game) =
            $(RevealEffect(Corp, self)) ++ (g.credits(Corp) >= 4).$(OptionalEffect(Corp, "Snare!: pay 4 credits to give the Runner 1 tag and do 3 net damage?", $(LoseCreditsEffect(Corp, 4), tag(1), net(3)), $))
    }

    object LotusField extends Ext("31055") {
        override def cannotLowerStrength(self : Card)(implicit g : Game) = true
        override def subroutines(self : Card)(implicit g : Game) = $(etrSub)
    }

    object Swordsman extends Ext("31056") {
        override def preventsBreak(self : Card, ice : Card, breaker : Card)(implicit g : Game) = ice == self && g.has(breaker, "AI")
        override def subroutines(self : Card)(implicit g : Game) = {
            val l = g.installedCards(Runner).%(c => c.data.isProgram && g.has(c, "AI"))
            $(Sub("Trash 1 installed AI program", ChooseCardEffect(Corp, l, TrashPurpose(Corp), "Swordsman: choose an AI program to trash", false)), netSub(1))
        }
    }

    object CelebrityGift extends Ext("31057") {
        override def canPlay(self : Card)(implicit g : Game) = g.clicks(Corp) >= 2
        override def onPlay(self : Card)(implicit g : Game) = $(LoseClicksEffect(Corp, 1)) ++ g.hand(Corp).any.$(ChooseCardsEffect(Corp, g.hand(Corp), 0, 5, CelebrityGiftPurpose(self), "Celebrity Gift: choose up to 5 cards in HQ to reveal"))
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.foreach(c => g.push(RevealEffect(Corp, c)))
            if (cards.any)
                g.push(gain(Corp, 2 * cards.num))
            NextAction
        }
    }

    object TrickOfLight extends Ext("31058") {
        override def onPlay(self : Card)(implicit g : Game) = {
            val targets = g.advanceableCards
            val sources = g.installedCards(Corp).%(c => g.advancement(c) > 0)
            (targets.any && sources.exists(s => targets.num > 1 || !targets.has(s))).$(ChooseCardEffect(Corp, targets, TrickTargetPurpose(self), "Trick of Light: choose a card to move advancement counters to", true))
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case TrickTargetPurpose(self) =>
                cards.single.foreach { t =>
                    val sources = g.installedCards(Corp).%(c => g.advancement(c) > 0).but(t)
                    if (sources.any)
                        g.push(ChooseCardEffect(Corp, sources, TrickSourcePurpose(self, t), "Trick of Light: choose a card to move advancement counters from", false))
                }
                NextAction
            case TrickSourcePurpose(self, t) =>
                cards.single.foreach { s =>
                    g.push(ChooseOneEffect(Corp, "Trick of Light: how many advancement counters to move?", 0.to(2.atMost(g.advancement(s))).$./(n => Alt(n.toString, $(MoveAdvancementEffect(s, t, n))))))
                }
                NextAction
        }
    }

    object HokusaiGrid extends Ext("31059") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(s) if thisServer(self, s) && g.isRezzed(self) => $(MessageEffect(Corp, "uses Hokusai Grid"), net(1))
            case _ => $
        }
    }

    object NearEarthHub extends Ext("31060") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case ServerCreated(_) if g.reg("remote-created") == 1 => $(MessageEffect(Corp, "uses Near-Earth Hub"), draw(Corp, 1))
            case _ => $
        }
    }

    object LicenseAcquisition extends Ext("31061") {
        override def onScore(self : Card)(implicit g : Game) = {
            val l = (g.hand(Corp) ++ g.discard(Corp)).%(c => c.data.isAsset || c.data.isUpgrade)
            l.any.$(ChooseCardEffect(Corp, l, InstallRezPurpose, "License Acquisition: install and rez an asset or upgrade from HQ or Archives, ignoring all costs?", true))
        }
    }

    object ProjectBeale extends Ext("31062") {
        override def onScore(self : Card)(implicit g : Game) = {
            val n = (g.lastScoredAdvancement - 3).atLeast(0) / 2
            (n > 0).$(AddCounterEffect(self, "agenda", n))
        }
        override def agendaPointsBonus(self : Card)(implicit g : Game) = (g.zone(self) == ScoredZone(Corp)).?(g.counter(self, "agenda")).|(0)
    }

    object DailyBusinessShow extends Ext("31063") {
        override def drawBonus(self : Card, f : Faction)(implicit g : Game) = (f.isCorp && g.isRezzed(self) && g.reg("drawn-Corp") == 0).?(1).|(0)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case CardsDrawn(Corp, l) if g.isRezzed(self) && g.reg("drawn-Corp") == l.num && !g.usedThisTurn(self, "dbs") && l.num > 1 =>
                $(MarkUsedEffect(self, "dbs"), ChooseCardEffect(Corp, l, BottomOfDeckPurpose(Corp), "Daily Business Show: choose a drawn card to add to the bottom of R&D", false))
            case _ => $
        }
    }

    object ReversedAccounts extends Ext("31064") {
        override def advanceable(self : Card)(implicit g : Game) = true
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("the Runner loses " + (4 * g.advancement(self)) + " credits", $(ClickCost(1), TrashSelfCost), $(LoseCreditsEffect(Runner, 4 * g.advancement(self))), click = true, ok = g.isRezzed(self), reason = "not rezzed"))
    }

    object PopUpWindow extends Ext("31065") {
        override def onEncounter(self : Card)(implicit g : Game) = $(gain(Corp, 1))
        override def subroutines(self : Card)(implicit g : Game) = $(Sub("End the run unless the Runner pays 1 credit", ChooseOneEffect(Runner, "Pop-up Window", $(Alt("Pay 1 credit", $(SpendEffect(Runner, 1, Spend.corpAbility, 0, $)), g.canSpend(Runner, 1, Spend.corpAbility)), Alt("End the run", $(EndRunEffect))))))
    }

    object Tollbooth extends Ext("31066") {
        override def onEncounter(self : Card)(implicit g : Game) =
            (g.credits(Runner) >= 3).?($(MessageEffect(Runner, "pays 3 credits (Tollbooth)"), LoseCreditsEffect(Runner, 3))).|($(MessageEffect(Corp, "uses Tollbooth to end the run"), EndRunEffect))
        override def subroutines(self : Card)(implicit g : Game) = $(etrSub)
    }

    object Wraparound extends Ext("31067") {
        override def strength(self : Card)(implicit g : Game) = g.installedCards(Runner).exists(c => g.isActive(c) && g.has(c, "Fracter")).?(0).|(7)
        override def subroutines(self : Card)(implicit g : Game) = $(etrSub)
    }

    object Psychographics extends Ext("31068") {
        override def canPlay(self : Card)(implicit g : Game) = g.tagged && g.advanceableCards.any
        override def onPlay(self : Card)(implicit g : Game) =
            $(ChooseOneEffect(Corp, "Psychographics: choose X (up to the number of tags)", 0.to(g.tags.atMost(g.credits(Corp))).$./(x => Alt("X = " + x, $(PsychographicsAction(self, x))))))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case PsychographicsAction(self, x) =>
                g.credits += Corp -> (g.credits(Corp) - x)
                g.log(Corp, "pays", x.credits)
                if (x > 0 && g.advanceableCards.any)
                    g.push(ChooseCardEffect(Corp, g.advanceableCards, AdvancePurpose(x), "Psychographics: choose a card to place " + x + " advancement counters on", false))
                NextAction
        }
    }

    object SanSanCityGrid extends Ext("31069") {
        override def advancementRequirementMod(self : Card, agenda : Card)(implicit g : Game) = (g.isRezzed(self) && g.serverOf(agenda).any && g.serverOf(agenda) == g.serverOf(self)).?(-1).|(0)
    }

    object BuildingABetterWorld extends Ext("31070") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Played(Corp, c) if g.has(c, "Transaction") => $(gain(Corp, 1))
            case _ => $
        }
    }

    object HostileTakeover extends Ext("31071") {
        override def onScore(self : Card)(implicit g : Game) = $(gain(Corp, 7), BadPubEffect(1))
    }

    object OaktownRenovation extends Ext("31072") {
        override def onInstall(self : Card)(implicit g : Game) = $(OaktownAction(self))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case OaktownAction(self) =>
                g.state(self).rezzed = true
                g.state(self).seen = true
                g.log(self, "is installed faceup")
                NextAction
        }
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Advanced(c, _) if c == self => $(gain(Corp, (g.advancement(self) >= 5).?(3).|(2)))
            case _ => $
        }
    }

    object ProjectAtlas extends Ext("31073") {
        override def onScore(self : Card)(implicit g : Game) = {
            val n = (g.lastScoredAdvancement - 3).atLeast(0)
            (n > 0).$(AddCounterEffect(self, "agenda", n))
        }
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("search R&D for a card and add it to HQ", $(CounterCost("agenda", 1)), $(ChooseCardEffect(Corp, g.deck(Corp).sortBy(_.title), AddToHandPurpose(Corp, true), "Project Atlas: choose a card in R&D", true), ShuffleDeckEffect(Corp)), ok = g.deck(Corp).any && g.counter(self, "agenda") > 0, reason = "needs an agenda counter"))
    }

    object CorporateTown extends Ext("31074") {
        override def canRez(self : Card)(implicit g : Game) = g.scored(Corp).exists(_.data.isAgenda)
        override def onRez(self : Card)(implicit g : Game) = $(ChooseForfeitEffect)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) if g.isRezzed(self) && g.installedCards(Runner).exists(_.data.isResource) =>
                $(OptionalEffect(Corp, "Corporate Town: trash an installed resource?", $(ChooseCardEffect(Corp, g.installedCards(Runner).%(_.data.isResource), TrashPurpose(Corp), "Choose a resource to trash", false)), $))
            case _ => $
        }
    }

    object Archer extends Ext("31075") {
        override def canRez(self : Card)(implicit g : Game) = g.scored(Corp).exists(_.data.isAgenda)
        override def onRez(self : Card)(implicit g : Game) = $(ChooseForfeitEffect)
        override def subroutines(self : Card)(implicit g : Game) = $(gainSub(2), trashProgramSub, trashProgramSub, etrSub)
    }

    object Hortum extends Ext("31076") {
        override def advanceable(self : Card)(implicit g : Game) = true
        override def preventsBreak(self : Card, ice : Card, breaker : Card)(implicit g : Game) = ice == self && g.advancement(self) >= 3 && g.has(breaker, "AI")
        override def subroutines(self : Card)(implicit g : Game) = $(
            Sub("Gain 1 credit (4 if 3+ advancement counters)", HortumGainAction(self)),
            Sub("End the run (search R&D for up to 2 cards first if 3+ advancement counters)", HortumEndAction(self)))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case HortumGainAction(self) =>
                g.push(gain(Corp, (g.advancement(self) >= 3).?(4).|(1)))
                NextAction
            case HortumEndAction(self) =>
                if (g.advancement(self) >= 3 && g.deck(Corp).any)
                    g.push(ChooseCardsEffect(Corp, g.deck(Corp).sortBy(_.title), 0, 2, AddToHandPurpose(Corp, false), "Hortum: choose up to 2 cards in R&D to add to HQ"), ShuffleDeckEffect(Corp))
                g.push(EndRunEffect)
                NextAction
        }
    }

    object IceWall extends Ext("31077") {
        override def advanceable(self : Card)(implicit g : Game) = true
        override def strength(self : Card)(implicit g : Game) = g.advancement(self)
        override def subroutines(self : Card)(implicit g : Game) = $(etrSub)
    }

    object PunitiveCounterstrike extends Ext("31078") {
        override def onPlay(self : Card)(implicit g : Game) = $(TraceEffect(5, $(meat(g.regLast("stolen-points"))), $))
    }

    object CrisiumGrid extends Ext("31079") {
        override def preventsSuccessfulRun(self : Card, server : Server)(implicit g : Game) = thisServer(self, server)
    }

    object PADCampaign extends Ext("31080") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) if g.isRezzed(self) => $(gain(Corp, 1))
            case _ => $
        }
    }

    object Enigma extends Ext("31081") {
        override def subroutines(self : Card)(implicit g : Game) = $(Sub("The Runner loses [click]", LoseClicksEffect(Runner, 1)), etrSub)
    }

    object SubliminalMessaging extends Ext("31082") {
        override def onPlay(self : Card)(implicit g : Game) =
            $(gain(Corp, 1)) ++ (g.reg("subliminal") == 0).$(RegisterEffect("subliminal", 1), GainClicksEffect(Corp, 1))
    }

    val all : $[CardImpl] = $(
        Quetzal, ReinaRoja, EnPassant, RetrievalRun, Clot, Corroder, Imp, Mimic, IceCarver, LiberatedAccount, Scrubber, Xanadu,
        KenTenma, SteveCambridge, CareerFair, EmergencyShutdown, ForgedActivationOrders, InsideJob, Legwork, Networking, Abagnale,
        FemmeFatale, SneakdoorBeta, SecurityTesting, Ayla, Kit, Diesel, TestRun, MakersEye, Atman, Chameleon, Egret, GordianBlade,
        Paricia, AesopsPawnshop, ProfessionalContacts, DirtyLaundry, PrepaidVoicePAD, EarthriseHotel,
        ArchitectsOfTomorrow, ProjectVitruvius, MarilynCampaign, Eli, Magnet, Ravana, Rototurret, ArchivedMemories, BioticLabor,
        CorporateTroubleshooter, PersonalEvolution, HouseOfKnives, NiseiMkII, Ronin, Snare, LotusField, Swordsman, CelebrityGift,
        TrickOfLight, HokusaiGrid, NearEarthHub, LicenseAcquisition, ProjectBeale, DailyBusinessShow, ReversedAccounts, PopUpWindow,
        Tollbooth, Wraparound, Psychographics, SanSanCityGrid, BuildingABetterWorld, HostileTakeover, OaktownRenovation, ProjectAtlas,
        CorporateTown, Archer, Hortum, IceWall, PunitiveCounterstrike, CrisiumGrid, PADCampaign, Enigma, SubliminalMessaging,
    )
}
