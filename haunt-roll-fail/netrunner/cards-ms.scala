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
// Midnight Sun (ms) and Uprising Booster Pack (urbp) card implementations, ported from jinteki.net src/clj/game/cards/*.clj
// ---------------------------------------------------------------------------------------------------------------------

// card specific actions and purposes
case class CarpeDiemAction(card : Card) extends CardAction
case class PinholeAction(card : Card) extends CardAction
case class PinholePurpose(card : Card) extends CardPurpose
case class DeepDiveAction(card : Card, first : Boolean) extends CardAction
case class DeepDivePurpose(card : Card, first : Boolean) extends CardPurpose
case class DeepDiveSecondAction(card : Card) extends CardAction
case class DeepDiveEndAction(card : Card) extends CardAction
case class IntoTheDepthsAction(card : Card, n : Int, done : $[String]) extends CardAction
case class IntoTheDepthsInstallAction(card : Card) extends CardAction
case class RiggingUpPurpose(card : Card) extends CardPurpose
case class RiggingUpChargeAction(card : Card, target : Card) extends CardAction
case class EnvironmentalTestingAction(card : Card) extends CardAction
case class TriestePurpose(card : Card) extends CardPurpose
case class HakarlPurpose(card : Card) extends CardPurpose
case class BigDealPurpose(card : Card) extends CardPurpose
case class BigDealScoreAction(card : Card, target : Card) extends CardAction
case class RegenesisPurpose(card : Card) extends CardPurpose
case class BladderwortAction(card : Card) extends CardAction
case class MoonPoolAction(card : Card) extends CardAction
case class MoonPoolTrashPurpose(card : Card) extends CardPurpose
case class MoonPoolRevealPurpose(card : Card) extends CardPurpose
case class MitosisPurpose(card : Card) extends CardPurpose
case class ObAction(card : Card, cost : Int) extends CardAction
case class TrashGainPurpose(card : Card, n : Int) extends CardPurpose
case class StavkaPurpose(card : Card) extends CardPurpose
case class DRMInstallAction(card : Card) extends CardAction
case class DRMPurpose(card : Card) extends CardPurpose
case class TwinningAction(card : Card, server : Server) extends CardAction


object MSCards {
    // ------------------------------------------------------------------------------------------------------------
    // Runner
    // ------------------------------------------------------------------------------------------------------------

    object EsaAfontov extends Ext("33001") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case DamageDone("core", n) if n > 0 && !g.usedThisTurn(self, "esa") =>
                $(MarkUsedEffect(self, "esa"), OptionalEffect(Runner, "Esa Afontov: draw 1 card and sabotage 2?", $(draw(Runner, 1), SabotageEffect(2)), $))
            case _ => $
        }
    }

    object Chastushka extends Ext("33002") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = $(RunEffect(HQ, |(self)))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(HQ) if thisCardRun(self) => $(ReplaceBreachEffect, SabotageEffect(4))
            case _ => $
        }
    }

    object RunningHot extends Ext("33003") {
        override def onPlay(self : Card)(implicit g : Game) = $(core(1), GainClicksEffect(Runner, 3))
    }

    object SteelskinScarring extends Ext("33004") {
        override def onPlay(self : Card)(implicit g : Game) = $(draw(Runner, 3))
        override def onTrash(self : Card, by : Faction)(implicit g : Game) = $(OptionalEffect(Runner, "Steelskin Scarring: draw 2 cards?", $(draw(Runner, 2)), $))
    }

    object Ghosttongue extends Ext("33005") {
        override def onInstall(self : Card)(implicit g : Game) = $(core(1))
        override def playCostMod(self : Card, card : Card)(implicit g : Game) = card.data.isEvent.?(-1).|(0)
    }

    object Marrow extends Ext("33006") {
        override def memory(self : Card)(implicit g : Game) = 1
        override def handSize(self : Card, f : Faction)(implicit g : Game) = f.isRunner.?(3).|(0)
        override def onInstall(self : Card)(implicit g : Game) = $(core(1))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case AgendaScored(_) => $(MessageEffect(Runner, "uses Marrow"), SabotageEffect(1))
            case _ => $
        }
    }

    object Begemot extends Ext("33007") {
        override def onInstall(self : Card)(implicit g : Game) = $(core(1))
        override def strength(self : Card)(implicit g : Game) = g.coreDamage
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(1), 0, $("Barrier"), |("break any number of barrier subroutines")))
    }

    object Avgustina extends Ext("33008") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Installed(Runner, c) if c.data.isProgram && g.has(c, "Virus") && !g.usedThisTurn(self, "avgustina") =>
                $(MarkUsedEffect(self, "avgustina"), MessageEffect(Runner, "uses Avgustina Ivanovskaya"), SabotageEffect(1))
            case _ => $
        }
    }

    object LightTheFire extends Ext("33009") {
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("suffer 1 core damage: run a remote server (cards in its root lose all abilities; trash them if successful)", $(ClickCost(1), TrashSelfCost), $(core(1), RunAnyServerEffect(|(self), g.remotes)), click = true, ok = g.remotes.any, reason = "no remote server"))
    }

    object TheTwinning extends Ext("33010") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case CounterAdded(c, "credit") if g.owner(c).isRunner && g.isInstalled(c) && g.reg("spent-from-card") >= 1 && !g.usedThisTurn(self, "twinning") =>
                $(MarkUsedEffect(self, "twinning"), AddCounterEffect(self, "power", 1))
            case BreachServer(s) if (s == HQ || s == RnD) && g.counter(self, "power") > 0 => $(TwinningAction(self, s))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case TwinningAction(self, s) =>
                val max = 2.atMost(g.counter(self, "power"))
                g.push(ChooseOneEffect(Runner, "The Twinning: remove power counters to access that many additional cards", 0.to(max).$./(n => Alt(n + " additional", (n > 0).$(AddCounterEffect(self, "power", -n), AccessBonusEffect(s, n))))))
                NextAction
        }
    }

    object Sable extends Ext("33011") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) => $(IdentifyMarkEffect)
            case SuccessfulRun(s) if g.isMark(s) && !g.usedThisTurn(self, "sable") => $(MarkUsedEffect(self, "sable"), MessageEffect(Runner, "uses Sable"), GainClicksEffect(Runner, 1))
            case _ => $
        }
    }

    object CarpeDiem extends Ext("33012") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = $(IdentifyMarkEffect, gain(Runner, 4), CarpeDiemAction(self))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case CarpeDiemAction(self) =>
                g.mark.foreach(m => g.push(OptionalEffect(Runner, "Carpe Diem: run " + m.name + " (your mark)?", $(RunEffect(m, |(self))), $)))
                NextAction
        }
    }

    object PinholeThreading extends Ext("33013") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = $(RunAnyServerEffect(|(self), g.servers))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(s) if thisCardRun(self) => $(ReplaceBreachEffect, PinholeAction(self))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case PinholeAction(self) =>
                val l = g.run.$./~(r => g.servers.but(r.server)./~(s => g.root(s)))
                if (l.any)
                    g.push(ChooseCardEffect(Runner, l, PinholePurpose(self), "Pinhole Threading: choose a card in the root of another server to access", false))
                else
                    g.log(Runner, "finds no card to access")
                NextAction
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.foreach(c => g.serverOf(c).foreach(s => g.push(AccessCardsEffect(s, $(c), c.data.isAgenda))))
            NextAction
        }
    }

    object PANWeave extends Ext("33014") {
        override def onInstall(self : Card)(implicit g : Game) = $(meat(1))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(HQ) if firstHQRun && g.credits(Corp) > 0 => $(MessageEffect(Runner, "uses PAN-Weave"), lose(Corp, 1), gain(Runner, 1))
            case _ => $
        }
    }

    object Virtuoso extends Ext("33015") {
        override def memory(self : Card)(implicit g : Game) = 1
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) => $(IdentifyMarkEffect)
            case SuccessfulRun(s) if g.isMark(s) && !g.usedThisTurn(self, "virtuoso") =>
                $(MarkUsedEffect(self, "virtuoso")) ++ ((s == HQ).?($(AccessBonusEffect(HQ, 1))).|($(MessageEffect(Runner, "will breach HQ when the run ends (Virtuoso)"), SetChoiceEffect(self, "breach-hq"))))
            case RunEnds(_, _, _) if g.state(self).choice == "breach-hq" => $(SetChoiceEffect(self, ""), BreachServerEffect(HQ))
            case _ => $
        }
    }

    object CatsCradle extends Ext("33016") {
        override def rezCostMod(self : Card, card : Card)(implicit g : Game) = (card.data.isIce && card.data.has("Code Gate")).?(1).|(0)
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(1), 1, $("Code Gate")))
        override def pumps(self : Card)(implicit g : Game) = $(Pump(c(1), 1))
    }

    object Cezve extends Ext("33017") {
        override def initialCounters(self : Card)(implicit g : Game) = Map("credit" -> 2)
        override def creditsUsableForRun(self : Card)(implicit g : Game) = g.run.exists(r => r.server.isCentral && !r.ended)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) => $(RefillCreditsEffect(self, 2))
            case _ => $
        }
    }

    object Revolver extends Ext("33018") {
        override def initialCounters(self : Card)(implicit g : Game) = Map("power" -> 6)
        override def breaks(self : Card)(implicit g : Game) = $(
            BreakAbility($(CounterCost("power", 1)), 1, $("Sentry"), |("break 1 sentry subroutine (hosted power counter)")),
            BreakAbility($(TrashSelfCost), 1, $("Sentry"), |("break 1 sentry subroutine (trash Revolver)")))
        override def pumps(self : Card)(implicit g : Game) = $(Pump(c(2), 3))
    }

    object Backstitching extends Ext("33019") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) => $(IdentifyMarkEffect)
            case EncounterIce(ice) if g.run.exists(r => g.isMark(r.server)) =>
                $(OptionalEffect(Runner, "Backstitching: trash it to bypass " + g.asciiTitle(ice) + "?", $(TrashEffect(self, Runner, false), BypassEffect), $))
            case _ => $
        }
    }

    object NoFreeLunch extends Ext("33020") {
        override def abilities(self : Card)(implicit g : Game) = $(
            Ability("gain 3 credits", $(TrashSelfCost), $(gain(Runner, 3))),
            Ability("remove 1 tag", $(TrashSelfCost), $(RemoveTagsEffect(1)), ok = g.tagged, reason = "not tagged"))
    }

    object Padma extends Ext("33021") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case RunInitiated(RnD) if g.runsThisTurn.count(RnD) == 1 && g.chargeable(Runner).any =>
                $(OptionalEffect(Runner, "Captain Padma Isbister: charge 1 of your installed cards?", $(ChargeEffect(Runner)), $))
            case _ => $
        }
    }

    object DeepDive extends Ext("33022") {
        override def canPlay(self : Card)(implicit g : Game) = $(HQ, RnD, Archives).forall(s => g.successfulRunsThisTurn.has(s)) && g.deck(Corp).any
        override def onPlay(self : Card)(implicit g : Game) = $(DeepDiveAction(self, true))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            // the top 8 cards of R&D are set aside (faceup, hosted on Deep Dive without being installed) until the end
            case DeepDiveAction(self, first) =>
                if (first) {
                    val top = g.deck(Corp).take(8)
                    top.foreach { c =>
                        g.moveTo(c, StashZone(self))
                        g.state(c).seen = true
                    }
                    if (top.any)
                        g.log(Runner, "sets aside the top", top.num.cards, "of R&D:", top.sortBy(_.title)./(g.cardElem).comma)
                }
                val l = g.stashed(self).but(g.state(self).target).sortBy(_.title)
                if (l.any)
                    g.push(ChooseCardEffect(Runner, l, DeepDivePurpose(self, first), "Deep Dive: choose a set-aside card to access", false))
                else
                    g.push(DeepDiveEndAction(self))
                NextAction
            case DeepDiveSecondAction(self) =>
                if (g.clicks(Runner) >= 1 && g.stashed(self).but(g.state(self).target).any)
                    g.push(OptionalEffect(Runner, "Deep Dive: spend [click] to access another set-aside card?", $(LoseClicksEffect(Runner, 1), DeepDiveAction(self, false)), $(DeepDiveEndAction(self))))
                else
                    g.push(DeepDiveEndAction(self))
                NextAction
            case DeepDiveEndAction(self) =>
                val l = g.stashed(self)
                l.foreach(c => g.moveTo(c, DeckZone(Corp)))
                if (l.any)
                    g.log(Corp, "shuffles the", l.num.cards, "set aside by Deep Dive back into R&D")
                g.push(ShuffleDeckEffect(Corp))
                NextAction
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case DeepDivePurpose(self, first) =>
                cards.foreach { c =>
                    g.state(self).target = |(c)
                    val next : ForcedAction = first.?(DeepDiveSecondAction(self)).|(DeepDiveEndAction(self))
                    g.push(AccessCardsEffect(RnD, $(c), false), next)
                }
                NextAction
        }
    }

    object IntoTheDepths extends Ext("33023") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = $(RunAnyServerEffect(|(self), g.servers))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            // "for each time you passed a piece of ice during this run" (passing the same ice twice counts twice), up to 3
            case SuccessfulRun(s) if thisCardRun(self) && g.fxPassedThisRun.any => $(IntoTheDepthsAction(self, g.fxPassedThisRun.num.atMost(3), $))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case IntoTheDepthsAction(self, n, done) =>
                if (n > 0) {
                    val alts = $(
                        (!done.has("gain")).$(Alt("Gain 4 credits", $(gain(Runner, 4), IntoTheDepthsAction(self, n - 1, done :+ "gain")))),
                        (!done.has("install")).$(Alt("Search the stack for a program and install it", $(IntoTheDepthsInstallAction(self), IntoTheDepthsAction(self, n - 1, done :+ "install")))),
                        (!done.has("charge")).$(Alt("Charge 1 of your installed cards", $(ChargeEffect(Runner), IntoTheDepthsAction(self, n - 1, done :+ "charge")), g.chargeable(Runner).any))
                    ).flatten
                    if (alts.any)
                        g.push(ChooseOneEffect(Runner, "Into the Depths: choose an ability to resolve (" + n + " remaining)", alts))
                }
                NextAction
            case IntoTheDepthsInstallAction(self) =>
                val l = g.deck(Runner).%(c => runProgramInstallable(c) && g.credits(Runner) >= g.installCost(c, None)).sortBy(_.title)
                if (l.any)
                    g.push(ChooseCardEffect(Runner, l, InstallPurpose(false, None), "Into the Depths: choose a program to install", true))
                g.push(ShuffleDeckEffect(Runner))
                NextAction
        }
    }

    object RiggingUp extends Ext("33024") {
        override def onPlay(self : Card)(implicit g : Game) = {
            val l = g.hand(Runner).%(c => (c.data.isHardware || (c.data.isProgram && g.memoryFree >= c.data.memory.|(0))) && g.credits(Runner) >= (g.installCost(c, None) - 3).atLeast(0))
            l.any.$(ChooseCardEffect(Runner, l, RiggingUpPurpose(self), "Rigging Up: choose a program or piece of hardware to install, paying 3 less", true))
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.foreach(c => g.push(InstallDiscountEffect(c, 3), RiggingUpChargeAction(p.card, c)))
            NextAction
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case RiggingUpChargeAction(self, c) =>
                if (g.isInstalled(c) && g.counter(c, "power") > 0)
                    g.push(OptionalEffect(Runner, "Rigging Up: charge " + g.asciiTitle(c) + "?", $(AddCounterEffect(c, "power", 1)), $))
                NextAction
        }
    }

    object Endurance extends Ext("33025") {
        override def memory(self : Card)(implicit g : Game) = 2
        override def initialCounters(self : Card)(implicit g : Game) = Map("power" -> 3)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(_) if g.successfulRunsThisTurn.num == 1 => $(AddCounterEffect(self, "power", 1))
            case _ => $
        }
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility($(CounterCost("power", 2)), 2, $(), |("break up to 2 subroutines (2 hosted power counters)")))
    }

    object Hyperbaric extends Ext("33026") {
        override def initialCounters(self : Card)(implicit g : Game) = Map("power" -> 1)
        override def strength(self : Card)(implicit g : Game) = g.counter(self, "power")
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(1), 1, $("Code Gate")))
        override def abilities(self : Card)(implicit g : Game) = $(Ability("place 1 power counter on Hyperbaric (+1 strength)", c(2), $(AddCounterEffect(self, "power", 1))))
    }

    object Propeller extends Ext("33027") {
        override def initialCounters(self : Card)(implicit g : Game) = Map("power" -> 4)
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(1), 1, $("Barrier")))
        override def pumps(self : Card)(implicit g : Game) = $(Pump($(CounterCost("power", 1)), 2))
    }

    object Daeg extends Ext("33028") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case AgendaScored(_) | AgendaStolen(_) if g.chargeable(Runner).any => $(OptionalEffect(Runner, "Daeg: charge 1 of your installed cards?", $(ChargeEffect(Runner)), $))
            case _ => $
        }
    }

    object EnvironmentalTesting extends Ext("33029") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Installed(Runner, c) if c.data.isProgram || c.data.isHardware => $(AddCounterEffect(self, "power", 1))
            case CounterAdded(c, "power") if c == self && g.counter(self, "power") >= 4 && g.isInstalled(self) => $(EnvironmentalTestingAction(self))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case EnvironmentalTestingAction(self) =>
                if (g.isInstalled(self)) {
                    g.trashCard(self, Runner, false)
                    g.push(gain(Runner, 9))
                }
                NextAction
        }
    }

    object StoneshipChartRoom extends Ext("33030") {
        override def abilities(self : Card)(implicit g : Game) = $(
            Ability("draw 2 cards", $(TrashSelfCost), $(draw(Runner, 2))),
            Ability("charge 1 of your installed cards", $(TrashSelfCost), $(ChargeEffect(Runner)), ok = g.chargeable(Runner).but(self).any, reason = "no card to charge"))
    }

    // ------------------------------------------------------------------------------------------------------------
    // Corp
    // ------------------------------------------------------------------------------------------------------------

    object ElivagarBifurcation extends Ext("33031") {
        override def onScore(self : Card)(implicit g : Game) = {
            val l = g.installedCards(Corp).%(g.isRezzed)
            l.any.$(ChooseCardEffect(Corp, l, DerezPurpose, "Elivagar Bifurcation: derez an installed card?", true))
        }
    }

    object Midnight3Arcology extends Ext("33032") {
        override def onScore(self : Card)(implicit g : Game) = $(draw(Corp, 3), SetFlagEffect("skip-discard"), MessageEffect(Corp, "skips the discard step this turn"))
    }

    object RefugeCampaign extends Ext("33033") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) if g.isRezzed(self) => $(gain(Corp, 2))
            case _ => $
        }
    }

    object TriesteModelBioroids extends Ext("33034") {
        override def onRez(self : Card)(implicit g : Game) = {
            val l = g.rezzedIce.%(i => g.has(i, "Bioroid"))
            l.any.$(ChooseCardEffect(Corp, l, TriestePurpose(self), "Trieste Model Bioroids: choose a rezzed bioroid ice", false))
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.foreach(c => g.push(SetTargetEffect(p.card, |(c))))
            NextAction
        }
        override def preventsBreak(self : Card, ice : Card, breaker : Card)(implicit g : Game) = g.isRezzed(self) && g.state(self).target.has(ice) && !breaker.data.isIdentity
    }

    object Echo extends Ext("33035") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Rezzed(c) if c.data.isIce && g.has(c, "Harmonic") => $(AddCounterEffect(self, "power", 1))
            case _ => $
        }
        override def subroutines(self : Card)(implicit g : Game) = g.counter(self, "power").times(etrSub)
    }

    object Hakarl extends Ext("33036") {
        override def subroutines(self : Card)(implicit g : Game) = $(Sub("Do 1 core damage", core(1)), etrSub)
        override def runnerAbilities(self : Card)(implicit g : Game) = bioroidBreak(self)
        override def onRez(self : Card)(implicit g : Game) = {
            val l = g.otherInstalled(Corp, self).%(g.isRezzed)
            (rezzedDuringRunOnThisServer(self) && l.any).$(OptionalEffect(Corp, "Hakarl 1.0: derez another card to block paid abilities on bioroid ice this turn?", $(ChooseCardEffect(Corp, l, HakarlPurpose(self), "Choose a card to derez", false)), $))
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.foreach(c => g.push(DerezEffect(c), RegisterEffect("no-bioroid-abilities", 1), MessageEffect(Corp, "blocks the Runner from using paid abilities on bioroid ice this turn")))
            NextAction
        }
    }

    object Wave extends Ext("33037") {
        override def onRez(self : Card)(implicit g : Game) =
            (rezzedDuringRunOnThisServer(self) && g.deck(Corp).exists(_.data.isIce)).$(OptionalEffect(Corp, "Wave: search R&D for a piece of ice?", $(ChooseCardEffect(Corp, g.deck(Corp).%(_.data.isIce).sortBy(_.title), AddToHandPurpose(Corp, true), "Wave: choose a piece of ice", true), ShuffleDeckEffect(Corp)), $))
        override def subroutines(self : Card)(implicit g : Game) = {
            val n = g.rezzedIce.count(i => g.has(i, "Harmonic"))
            $(Sub("Gain 1 credit for each rezzed harmonic ice (" + n + ")", gain(Corp, n)))
        }
    }

    object BigDeal extends Ext("33038") {
        override def canPlay(self : Card)(implicit g : Game) = g.installedCards(Corp).any
        override def onPlay(self : Card)(implicit g : Game) =
            $(ChooseCardEffect(Corp, g.installedCards(Corp), BigDealPurpose(self), "Big Deal: choose a card to place 4 advancement counters on", false), RemoveFromGameEffect(self), LoseAllClicksEffect(Corp))
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.foreach(c => g.push(PlaceAdvancementEffect(c, 4), BigDealScoreAction(p.card, c)))
            NextAction
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case BigDealScoreAction(self, c) =>
                if (g.scorableAgendas.has(c))
                    g.push(OptionalEffect(Corp, "Big Deal: score " + g.asciiTitle(c) + "?", $(ScoreAgendaEffect(c)), $))
                NextAction
        }
    }

    object BloodInTheWater extends Ext("33039") {
        override def advancementRequirementMod(self : Card, agenda : Card)(implicit g : Game) = (agenda == self).?(g.hand(Runner).num).|(0)
    }

    object Regenesis extends Ext("33040") {
        override def onScore(self : Card)(implicit g : Game) = {
            val l = g.discard(Corp).%(c => c.data.isAgenda && !g.state(c).seen)
            (g.reg("to-archives") == 0 && l.any).$(ChooseCardEffect(Corp, l, RegenesisPurpose(self), "Regenesis: reveal a facedown agenda in Archives and add it to the score area?", true))
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.foreach(c => g.push(RevealEffect(Corp, c), AddToScoreAreaEffect(c, Corp)))
            NextAction
        }
    }

    object Bladderwort extends Ext("33041") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) if g.isRezzed(self) => $(gain(Corp, 1), BladderwortAction(self))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case BladderwortAction(self) =>
                if (g.credits(Corp) <= 4) {
                    g.log(Corp, "uses", self, "to do 1 net damage")
                    g.push(net(1))
                }
                NextAction
        }
    }

    object MoonPool extends Ext("33042") {
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("trash up to 2 cards from HQ, shuffle up to 2 facedown cards from Archives into R&D", $(RemoveSelfCost), $(MoonPoolAction(self)), ok = g.isRezzed(self), reason = "not rezzed"))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case MoonPoolAction(self) =>
                if (g.hand(Corp).any)
                    g.push(ChooseCardsEffect(Corp, g.hand(Corp), 0, 2, MoonPoolTrashPurpose(self), "Moon Pool: choose up to 2 cards in HQ to trash"))
                else
                    g.push(CardsChosenAction(Corp, $, MoonPoolTrashPurpose(self)))
                NextAction
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case MoonPoolTrashPurpose(self) =>
                cards.foreach(c => g.toArchives(c, false))
                if (cards.any)
                    g.log(Corp, "trashes", cards.num.cards, "from HQ")
                val l = g.discard(Corp).%(c => !g.state(c).seen)
                if (l.any)
                    g.push(ChooseCardsEffect(Corp, l, 0, 2, MoonPoolRevealPurpose(self), "Moon Pool: choose up to 2 facedown cards in Archives to reveal and shuffle into R&D"))
                NextAction
            case MoonPoolRevealPurpose(self) =>
                cards.foreach { c =>
                    g.log(Corp, "reveals", c, "from Archives")
                    g.moveTo(c, DeckZone(Corp))
                }
                if (cards.any) {
                    g.push(ShuffleDeckEffect(Corp))
                    cards.%(_.data.isAgenda).foreach { _ =>
                        g.push(ChooseCardEffect(Corp, g.installedCards(Corp), AdvancePurpose(1), "Moon Pool: place 1 advancement counter on an installed card?", true))
                    }
                }
                NextAction
        }
    }

    object Anemone extends Ext("33043") {
        override def onRez(self : Card)(implicit g : Game) =
            (rezzedDuringRunOnThisServer(self) && g.hand(Corp).any).$(OptionalEffect(Corp, "Anemone: trash 1 card from HQ to do 2 net damage?", $(ChooseCardEffect(Corp, g.hand(Corp), DiscardPurpose(Corp), "Anemone: choose a card in HQ to trash", false), net(2)), $))
        override def subroutines(self : Card)(implicit g : Game) = $(netSub(1))
    }

    object Bathynomus extends Ext("33044") {
        override def strength(self : Card)(implicit g : Game) = g.serverOf(self).has(Archives).?(3).|(0)
        override def subroutines(self : Card)(implicit g : Game) = $(netSub(3))
    }

    object Ivik extends Ext("33045") {
        override def rezCostMod(self : Card, card : Card)(implicit g : Game) = (card == self).?(-g.rezzedIce.count(i => g.has(i, "Code Gate"))).|(0)
        override def subroutines(self : Card)(implicit g : Game) = $(netSub(2), etrSub)
    }

    object Mitosis extends Ext("33046") {
        override def canPlay(self : Card)(implicit g : Game) = g.clicks(Corp) >= 2 && g.hand(Corp).exists(c => !c.data.isOperation)
        override def onPlay(self : Card)(implicit g : Game) =
            $(LoseClicksEffect(Corp, 1), ChooseCardsEffect(Corp, g.hand(Corp).%(c => !c.data.isOperation), 0, 2, MitosisPurpose(self), "Mitosis: choose up to 2 cards to install in new remote servers"))
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.foreach(c => g.push(CorpInstallEffect(c, None, false), PlaceAdvancementEffect(c, 2), MarkUsedEffect(c, "cannot-score"), MarkUsedEffect(c, "cannot-rez")))
            NextAction
        }
    }

    object Mavirus extends Ext("33047") {
        override def onAccess(self : Card)(implicit g : Game) = {
            val dmg = g.isRezzed(self).$(net(1))
            $(RevealEffect(Corp, self), OptionalEffect(Corp, "Mavirus: purge virus counters?", $(PurgeEffect) ++ dmg, dmg))
        }
        override def abilities(self : Card)(implicit g : Game) = $(Ability("purge virus counters", $(TrashSelfCost), $(PurgeEffect), ok = g.isRezzed(self), reason = "not rezzed"))
    }

    object Pravdivost extends Ext("33048") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(_) if g.successfulRunsThisTurn.num == 1 && g.advanceableCards.any =>
                $(ChooseCardEffect(Corp, g.advanceableCards, AdvancePurpose(1), "Pravdivost Consulting: place 1 advancement counter on a card?", true))
            case _ => $
        }
    }

    object ArtificialCryptocrash extends Ext("33049") {
        override def onScore(self : Card)(implicit g : Game) = $(lose(Runner, 7))
    }

    object ChekistScion extends Ext("33050") {
        override def advanceable(self : Card)(implicit g : Game) = true
        override def onAccess(self : Card)(implicit g : Game) = g.isInstalled(self).$(MessageEffect(Corp, "uses Chekist Scion"), tag(1 + g.advancement(self)))
    }

    object DragoIvanov extends Ext("33051") {
        override def advanceable(self : Card)(implicit g : Game) = true
        override def abilities(self : Card)(implicit g : Game) = $(Ability("give the Runner 1 tag", $(CounterCost("advancement", 2)), $(tag(1)), ok = g.isRezzed(self) && g.active.isCorp, reason = "only during your turn"))
    }

    object UbiquitousVig extends Ext("33052") {
        override def advanceable(self : Card)(implicit g : Game) = true
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) if g.isRezzed(self) && g.advancement(self) > 0 => $(gain(Corp, g.advancement(self)))
            case _ => $
        }
    }

    object Mestnichestvo extends Ext("33053") {
        override def advanceable(self : Card)(implicit g : Game) = true
        override def onEncounter(self : Card)(implicit g : Game) =
            (g.advancement(self) > 0).$(OptionalEffect(Corp, "Mestnichestvo: remove 1 advancement counter to make the Runner lose 3 credits?", $(AddCounterEffect(self, "advancement", -1), lose(Runner, 3)), $))
        override def subroutines(self : Card)(implicit g : Game) = $(loseSub(3), etrSub)
    }

    object Vasilisa extends Ext("33054") {
        override def onEncounter(self : Card)(implicit g : Game) =
            (g.credits(Corp) >= 1 && g.advanceableCards.any).$(OptionalEffect(Corp, "Vasilisa: pay 1 credit to place 1 advancement counter on a card?", $(LoseCreditsEffect(Corp, 1), ChooseCardEffect(Corp, g.advanceableCards, AdvancePurpose(1), "Vasilisa: choose a card", false)), $))
        override def subroutines(self : Card)(implicit g : Game) = $(tagSub(1))
    }

    object BackroomMachinations extends Ext("33055") {
        override def canPlay(self : Card)(implicit g : Game) = g.tagged
        override def onPlay(self : Card)(implicit g : Game) = $(RemoveTagsEffect(1), AddToScoreAreaEffect(self, Corp))
        override def agendaPointsBonus(self : Card)(implicit g : Game) = (g.zone(self) == ScoredZone(Corp)).?(1).|(0)
    }

    object VladisibirskCityGrid extends Ext("33056") {
        override def advanceable(self : Card)(implicit g : Game) = true
        override def abilities(self : Card)(implicit g : Game) = {
            val l = g.serverOf(self).$./~(s => g.root(s)).but(self).%(g.isAdvanceable)
            $(Ability("place 2 advancement counters on another card in this server", $(CounterCost("advancement", 2)), $(ChooseCardEffect(Corp, l, AdvancePurpose(2), "Vladisibirsk City Grid: choose a card", false)), once = once("vlad"), ok = l.any, reason = "no other advanceable card in this server"))
        }
    }

    object ObSuperheavyLogistics extends Ext("33057") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Trashed(c, Corp, _) if g.owner(c).isCorp && g.lastTrashedRezzed && !g.installing && (c.data.isAsset || c.data.isUpgrade || c.data.isIce) && !g.usedThisTurn(self, "ob") && c.data.cost.exists(_ >= 1) =>
                val cost = c.data.cost.|(0) - 1
                $(OptionalEffect(Corp, "Ob Superheavy Logistics: search R&D for a card with rez cost " + cost + " to install and rez?", $(MarkUsedEffect(self, "ob"), ObAction(self, cost)), $))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case ObAction(self, cost) =>
                val l = g.deck(Corp).%(c => (c.data.isAsset || c.data.isUpgrade || c.data.isIce) && c.data.cost.has(cost)).sortBy(_.title)
                if (l.any)
                    g.push(ChooseCardEffect(Corp, l, InstallRezPurpose, "Ob Superheavy Logistics: choose a card to install and rez, ignoring credit costs", true))
                g.push(ShuffleDeckEffect(Corp))
                NextAction
        }
    }

    object AzefProtocol extends Ext("33058") {
        override def preventsScoring(self : Card, agenda : Card)(implicit g : Game) = agenda == self && g.otherInstalled(Corp, self).none
        override def onScore(self : Card)(implicit g : Game) = {
            val l = g.installedCards(Corp)
            l.any.$(ChooseCardEffect(Corp, l, TrashPurpose(Corp), "Azef Protocol: choose an installed card to trash", false)) :+ meat(2)
        }
    }

    object SvyatogorExcavator extends Ext("33059") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) if g.isRezzed(self) && g.otherInstalled(Corp, self).any =>
                $(OptionalEffect(Corp, "Svyatogor Excavator: trash another installed card to gain 3 credits?", $(ChooseCardEffect(Corp, g.otherInstalled(Corp, self), TrashGainPurpose(self, 3), "Choose a card to trash", false)), $))
            case _ => $
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case TrashGainPurpose(_, n) =>
                cards.foreach { c =>
                    g.trashCard(c, Corp, false)
                    g.push(gain(Corp, n))
                }
                NextAction
        }
    }

    object Envelopment extends Ext("33060") {
        override def onRez(self : Card)(implicit g : Game) = $(AddCounterEffect(self, "power", 4))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) if g.isRezzed(self) && g.counter(self, "power") > 0 => $(AddCounterEffect(self, "power", -1))
            case _ => $
        }
        override def subroutines(self : Card)(implicit g : Game) = g.counter(self, "power").times(etrSub) :+ Sub("Trash this ice", TrashEffect(self, Corp, false))
    }

    object Maskirovka extends Ext("33061") {
        override def subroutines(self : Card)(implicit g : Game) = $(gainSub(2), etrSub)
    }

    object Stavka extends Ext("33062") {
        override def onRez(self : Card)(implicit g : Game) = {
            val l = g.otherInstalled(Corp, self)
            l.any.$(OptionalEffect(Corp, "Stavka: trash another installed card to give it +5 strength for the run?", $(ChooseCardEffect(Corp, l, StavkaPurpose(self), "Choose a card to trash", false)), $))
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.foreach { c =>
                g.trashCard(c, Corp, false)
                if (g.run.exists(r => !r.ended))
                    g.push(PumpIceEffect(p.card, 5, RunDuration))
            }
            NextAction
        }
        override def subroutines(self : Card)(implicit g : Game) = $(trashProgramSub, trashProgramSub)
    }

    object Extract extends Ext("33063") {
        override def onPlay(self : Card)(implicit g : Game) =
            $(gain(Corp, 6)) ++ g.installedCards(Corp).any.$(OptionalEffect(Corp, "Extract: trash an installed card to gain 3 credits?", $(ChooseCardEffect(Corp, g.installedCards(Corp), TrashGainPurpose(self, 3), "Choose a card to trash", false)), $))
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case TrashGainPurpose(_, n) =>
                cards.foreach { c =>
                    g.trashCard(c, Corp, false)
                    g.push(gain(Corp, n))
                }
                NextAction
        }
    }

    object MutuallyAssuredDestruction extends Ext("33064") {
        override def canPlay(self : Card)(implicit g : Game) = g.clicks(Corp) >= 3
        override def onPlay(self : Card)(implicit g : Game) = {
            val l = g.installedCards(Corp).%(c => g.isRezzed(c) && !c.data.isAgenda)
            $(LoseClicksEffect(Corp, 2)) ++ l.any.$(ChooseCardsEffect(Corp, l, 0, l.num, TrashAndTagPurpose, "Mutually Assured Destruction: choose any number of rezzed cards to trash (1 tag each)"))
        }
    }

    object TrustOperation extends Ext("33065") {
        override def canPlay(self : Card)(implicit g : Game) = g.tagged
        override def onPlay(self : Card)(implicit g : Game) = {
            val res = g.installedCards(Runner).%(_.data.isResource)
            val arch = g.discard(Corp).%(c => !c.data.isOperation && !c.data.isAgenda)
            res.any.$(ChooseCardEffect(Corp, res, TrashPurpose(Corp), "Trust Operation: choose a resource to trash", false)) ++
            arch.any.$(ChooseCardEffect(Corp, arch, InstallRezPurpose, "Trust Operation: install and rez a card from Archives, ignoring all costs?", true))
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Uprising Booster Pack (urbp)
    // ------------------------------------------------------------------------------------------------------------

    object MysticMaemi extends Ext("27001") {
        override def creditsUsableFor(self : Card, purpose : String)(implicit g : Game) = purpose == "play-event"
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) | AgendaStolen(_) => $(AddCounterEffect(self, "credit", 1))
            case TurnEnds(Runner) if g.counter(self, "credit") >= 3 =>
                $(ChooseOneEffect(Runner, "Mystic Maemi has 3 or more credits: choose one", $(
                    Alt("Trash a random card from the grip", $(RandomTrashFromHandEffect(Runner, 1)), g.hand(Runner).any),
                    Alt("Trash Mystic Maemi", $(TrashEffect(self, Runner, false))))))
            case _ => $
        }
        override def abilities(self : Card)(implicit g : Game) = $(Ability("take 1 credit", $, $(TakeCreditsEffect(Runner, self, 1)), ok = g.counter(self, "credit") > 0, reason = "no hosted credits"))
    }

    object Swift extends Ext("27002") {
        override def memory(self : Card)(implicit g : Game) = 1
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Played(Runner, c) if g.has(c, "Run") && !g.usedThisTurn(self, "swift") => $(MarkUsedEffect(self, "swift"), MessageEffect(Runner, "uses Swift"), GainClicksEffect(Runner, 1))
            case _ => $
        }
    }

    object CybertrooperTalut extends Ext("27003") {
        override def link(self : Card)(implicit g : Game) = 1
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Installed(Runner, c) if c.data.isIcebreaker && !g.has(c, "AI") => $(PumpBreakerAllEffect(c, 2))
            case _ => $
        }
    }

    object MegaprixQualifier extends Ext("27004") {
        override def onScore(self : Card)(implicit g : Game) =
            ((g.scored(Corp) ++ g.scored(Runner)).count(_.title == self.title) > 1).$(AddCounterEffect(self, "agenda", 1))
        override def agendaPointsBonus(self : Card)(implicit g : Game) = (g.counter(self, "agenda") > 0).?(1).|(0)
    }

    object LaCostaGrid extends Ext("27005") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) if g.isRezzed(self) && g.serverOf(self).exists(_.isRemote) =>
                $(ChooseCardEffect(Corp, g.serverOf(self).$./~(s => g.root(s)), AdvancePurpose(1), "La Costa Grid: choose a card in this server to place 1 advancement counter on", false))
            case _ => $
        }
    }

    object DigitalRightsManagement extends Ext("27006") {
        override def canPlay(self : Card)(implicit g : Game) = g.turn > 1 && !g.successfulRunsLastTurn.has(HQ)
        override def onPlay(self : Card)(implicit g : Game) = {
            val l = g.deck(Corp).%(_.data.isAgenda).sortBy(_.title)
            l.any.$(ChooseCardEffect(Corp, l, AddToHandPurpose(Corp, true), "Digital Rights Management: choose an agenda in R&D", true)) ++ $(ShuffleDeckEffect(Corp), DRMInstallAction(self), SetFlagEffect("cannot-score"), MessageEffect(Corp, "cannot score agendas for the remainder of the turn"))
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case DRMInstallAction(self) =>
                val l = g.hand(Corp).%(c => !c.data.isOperation)
                if (l.any)
                    g.push(ChooseCardEffect(Corp, l, DRMPurpose(self), "Digital Rights Management: install a card from HQ in the root of a remote server?", true))
                NextAction
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.foreach(c => g.push(ChooseServerEffect(Corp, c, false, g.remotes.%(r => !c.data.isIce), true)))
            NextAction
        }
    }

    object CayambeGrid extends Ext("27007") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) if g.isRezzed(self) && g.serverOf(self).exists(s => g.ice(s).any) =>
                $(ChooseCardEffect(Corp, g.serverOf(self).$./~(s => g.ice(s)), AdvancePurpose(1), "Cayambe Grid: choose a piece of ice protecting this server", false))
            case ApproachServer(s) if thisServer(self, s) && g.isRezzed(self) =>
                val cost = 2 * g.ice(s).count(i => g.advancement(i) > 0)
                $(ChooseOneEffect(Runner, "Cayambe Grid: pay " + cost + " credits or end the run", $(
                    Alt("Pay " + cost + " credits", (cost > 0).$(LoseCreditsEffect(Runner, cost)), g.credits(Runner) >= cost),
                    Alt("End the run", $(EndRunEffect)))))
            case _ => $
        }
    }

    val all : $[CardImpl] = $(
        EsaAfontov, Chastushka, RunningHot, SteelskinScarring, Ghosttongue, Marrow, Begemot, Avgustina, LightTheFire, TheTwinning,
        Sable, CarpeDiem, PinholeThreading, PANWeave, Virtuoso, CatsCradle, Cezve, Revolver, Backstitching, NoFreeLunch,
        Padma, DeepDive, IntoTheDepths, RiggingUp, Endurance, Hyperbaric, Propeller, Daeg, EnvironmentalTesting, StoneshipChartRoom,
        ElivagarBifurcation, Midnight3Arcology, RefugeCampaign, TriesteModelBioroids, Echo, Hakarl, Wave, BigDeal,
        BloodInTheWater, Regenesis, Bladderwort, MoonPool, Anemone, Bathynomus, Ivik, Mitosis, Mavirus,
        Pravdivost, ArtificialCryptocrash, ChekistScion, DragoIvanov, UbiquitousVig, Mestnichestvo, Vasilisa, BackroomMachinations, VladisibirskCityGrid,
        ObSuperheavyLogistics, AzefProtocol, SvyatogorExcavator, Envelopment, Maskirovka, Stavka, Extract, MutuallyAssuredDestruction, TrustOperation,
        MysticMaemi, Swift, CybertrooperTalut, MegaprixQualifier, LaCostaGrid, DigitalRightsManagement, CayambeGrid,
    )
}
