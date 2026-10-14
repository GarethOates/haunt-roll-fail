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
// The Automata Initiative (tai) card implementations, ported from jinteki.net src/clj/game/cards/*.clj
// ---------------------------------------------------------------------------------------------------------------------

// card specific actions and purposes
case class ThePriceAction(card : Card) extends CardAction
case class ChrysoPurpose(card : Card) extends CardPurpose
case class HermesPurpose(card : Card) extends CardPurpose
case class ArissanaPurpose(card : Card) extends CardPurpose
case class ArissanaTrashAction(card : Card, program : Card) extends CardAction
case class PichacaoAction(card : Card) extends CardAction
case class VernissagePurpose(card : Card) extends CardPurpose
case class BahiaChoiceAction(card : Card, remaining : Int, used : $[Int]) extends CardAction
case class StegodonPurpose(card : Card) extends CardPurpose
case class AblativePurpose(card : Card) extends CardPurpose
case class JaguarundiAction(card : Card) extends CardAction
case class GreasingPurpose(card : Card) extends CardPurpose
case class GreasingTagAction(card : Card, installed : Card) extends CardAction
case class ATeiaPurpose(card : Card, server : Server) extends CardPurpose
case class AttiniAction(card : Card) extends CardAction
case class TatuPurpose(card : Card) extends CardPurpose
case class MindscapingAction(card : Card) extends CardAction
case class MindscapingPurpose(card : Card) extends CardPurpose
case class EpiphanyAction(card : Card) extends CardAction
case class OracleThinktankAction(card : Card) extends CardAction
case class BalancedAction(card : Card, kind : String) extends CardAction
case class FedDrawAction(card : Card) extends CardAction
case class ValentaoAction(card : Card) extends CardAction
case class PivotPurpose(card : Card) extends CardPurpose
case class PivotPlayAction(card : Card) extends CardAction
case class PivotPlayPurpose(card : Card) extends CardPurpose
case class TucanaPurpose(card : Card) extends CardPurpose


object TAICards {
    // ------------------------------------------------------------------------------------------------------------
    // Runner
    // ------------------------------------------------------------------------------------------------------------

    object StrikeFund extends PT("34001") {
        override def onPlay(self : Card)(implicit g : Game) = $(gain(Runner, 4))
        override def onTrash(self : Card, by : Faction)(implicit g : Game) = $(OptionalEffect(Runner, "Strike Fund was trashed: gain 2 credits?", $(gain(Runner, 2)), $))
    }

    object ThePrice extends PT("34002") {
        override def onPlay(self : Card)(implicit g : Game) = $(ThePriceAction(self))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case ThePriceAction(self) =>
                val l = g.deck(Runner).take(4)
                g.push(MillEffect(Runner, 4), InstallFromListDiscountEffect(Runner, l, 3, "The Price: install 1 of the trashed cards paying 3 credits less?", true))
                NextAction
        }
    }

    object SolidarityBadge extends PT("34003") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Trashed(c, Runner, _) if g.owner(c).isCorp && g.reg("runner-trash-corp") == 1 => $(AddCounterEffect(self, "power", 1))
            case TurnBegins(Runner) if g.counter(self, "power") > 0 =>
                $(ChooseOneEffect(Runner, "Solidarity Badge", $(
                    Alt("Remove 1 power counter to draw 1 card", $(AddCounterEffect(self, "power", -1), draw(Runner, 1))),
                    Alt("Remove 1 power counter to remove 1 tag", $(AddCounterEffect(self, "power", -1), RemoveTagsEffect(1)), g.tagged),
                    Alt("Keep the counters", $))))
            case _ => $
        }
    }

    object AudreyV2 extends PT("34004") {
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility($(VirusCost(1)), 2, $(), |("break up to 2 subroutines (1 hosted virus counter)")))
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("+3 strength", $(TrashFromHandCost(1)), $(PumpBreakerEffect(self, 3, EncounterDuration)), ok = encounterIce.any, reason = "not encountering ice"))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Trashed(c, Runner, true) => $(AddCounterEffect(self, "virus", 1))
            case _ => $
        }
    }

    object Banner extends PT("34005") {
        override def abilities(self : Card)(implicit g : Game) = {
            val ice = encounterIce.%(i => g.has(i, "Barrier") && g.iceStrength(i) <= g.breakerStrength(self))
            $(Ability("subroutines on the encountered barrier cannot end the run this encounter", c(2), ice.$./(i => AddFlagEffect(g.encounterFlag("no-etr", i))), ok = ice.any, reason = "not encountering a barrier of lower or equal strength"))
        }
    }

    object Monkeywrench extends PT("34006") {
        override def mustBeHosted(self : Card)(implicit g : Game) = true
        override def hostRequirement(self : Card, host : Card)(implicit g : Game) = host.data.isIce
        override def iceStrengthMod(self : Card, ice : Card)(implicit g : Game) = g.state(self).host match {
            case Some(h) if h == ice => -2
            case Some(h) if g.serverOf(h).any && g.serverOf(h) == g.serverOf(ice) => -1
            case _ => 0
        }
    }

    object EruAyasePessoa extends PT("34007") {
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("run Archives taking 1 tag; if successful, breach R&D instead", $(ClickCost(1), TagCost(1)), $(RunEffect(Archives, |(self))), click = true, once = once("eru")))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(Archives) if thisCardRun(self) => $(RedirectBreachEffect(Archives, RnD))
            case BreachServer(RnD) if g.threat(3) && g.run.exists(_.server == Archives) => $(MessageEffect(Runner, "uses Eru Ayase-Pessoa to access 1 additional card"), AccessBonusEffect(RnD, 1))
            case _ => $
        }
    }

    object HannahWheels extends PT("34008") {
        override def abilities(self : Card)(implicit g : Game) = $(
            Ability("gain [click] and run a remote server (take 1 tag if unsuccessful)", click(), $(GainClicksEffect(Runner, 1), RunAnyServerEffect(|(self), g.remotes)), click = true, once = once("hannah"), ok = g.remotes.any, reason = "no remote server"),
            Ability("gain [click][click] and remove 1 tag", $(ClickCost(1), TrashSelfCost), $(GainClicksEffect(Runner, 2), RemoveTagsEffect(1)), click = true))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case RunEnds(_, false, _) if thisCardRun(self) => $(MessageEffect(Runner, "takes 1 tag (Hannah Wheels Pilintra)"), tag(1))
            case _ => $
        }
    }

    object LagoParanoaShelter extends PT("34009") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Installed(Corp, c) if !c.data.isIce && g.flagsOf("installed").%(f => f.card.exists(x => !x.data.isIce)).num == 1 && g.deck(Runner).any =>
                $(OptionalEffect(Runner, "Lago Paranoa Shelter: trash the top card of the stack to draw 1 card?", $(MillEffect(Runner, 1), draw(Runner, 1)), $))
            case _ => $
        }
    }

    object Mercury extends PT("34010") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case BreachServer(s) if (s == HQ || s == RnD) && g.run.any && !g.subsBrokenThisRun && !g.usedThisTurn(self, "mercury") =>
                $(OptionalEffect(Runner, "Mercury: access 1 additional card?", $(MarkUsedEffect(self, "mercury"), AccessBonusEffect(s, 1)), $))
            case _ => $
        }
    }

    object ChrysopoeianSkimming extends PT("34011") {
        override def onPlay(self : Card)(implicit g : Game) = {
            val l = g.hand(Corp).%(_.data.isAgenda)
            if (l.any)
                $(ChooseCardEffect(Corp, l, ChrysoPurpose(self), "Chrysopoeian Skimming: reveal an agenda from HQ?", true))
            else
                $(LookAtEffect(Runner, g.deck(Corp).take(3), "Chrysopoeian Skimming: the top 3 cards of R&D"))
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single match {
                case Some(c) =>
                    g.log(Corp, "reveals", c, "from HQ")
                    g.state(c).seen = true
                    g.push(GainClicksEffect(Runner, 1), draw(Runner, 1))
                case None =>
                    g.push(LookAtEffect(Runner, g.deck(Corp).take(3), "Chrysopoeian Skimming: the top 3 cards of R&D"))
            }
            NextAction
        }
    }

    object SDobrado extends PT("34012") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = $(RunAnyServerEffect(|(self), Server.centrals))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case EncounterIce(ice) if thisCardRun(self) && g.encountersThisRun == 1 => $(MessageEffect(Runner, "uses S-Dobrado"), BypassIceEffect(ice))
            case EncounterIce(ice) if thisCardRun(self) && g.encountersThisRun == 2 && g.threat(4) && g.clicks(Runner) >= 1 =>
                $(OptionalEffect(Runner, "S-Dobrado: spend [click] to bypass the ice?", $(LoseClicksEffect(Runner, 1), BypassIceEffect(ice)), $))
            case _ => $
        }
    }

    object Capybara extends PT("34013") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case IceBypassed(ice) if g.isRezzed(ice) => $(OptionalEffect(Runner, "Capybara: remove it from the game to derez the bypassed ice?", $(RemoveFromGameEffect(self), DerezEffect(ice)), $))
            case _ => $
        }
    }

    object Hermes extends PT("34014") {
        override def memory(self : Card)(implicit g : Game) = 1
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case AgendaScored(_) | AgendaStolen(_) if g.installedCards(Corp).exists(c => !g.isRezzed(c)) =>
                $(ChooseCardEffect(Runner, g.installedCards(Corp).%(c => !g.isRezzed(c)), HermesPurpose(self), "Hermes: choose an unrezzed card to add to HQ", false))
            case _ => $
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.foreach { c =>
                g.log(Runner, "adds", g.hiddenCorpElem(c), "to HQ (Hermes)")
                g.moveTo(c, HandZone(Corp))
                g.cleanupRemotes()
            }
            NextAction
        }
    }

    object Curupira extends PT("34015") {
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(1), 1, $("Barrier")))
        override def pumps(self : Card)(implicit g : Game) = $(Pump(c(1), 1))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case EncounterIce(ice) if g.has(ice, "Barrier") && g.counter(self, "power") >= 3 =>
                $(OptionalEffect(Runner, "Curupira: spend 3 hosted power counters to bypass the barrier?", $(AddCounterEffect(self, "power", -3), BypassIceEffect(ice)), $))
            case SubsBroken(_, _, _) if fullyBroke(self, e) => $(AddCounterEffect(self, "power", 1))
            case _ => $
        }
    }

    object LaserPointer extends PT("34016") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case EncounterIce(ice) if $("AP", "Observer", "Destroyer").exists(t => g.has(ice, t)) =>
                $(OptionalEffect(Runner, "Laser Pointer: trash it to bypass the ice?", $(TrashEffect(self, Runner, false), BypassIceEffect(ice)), $))
            case _ => $
        }
    }

    object Saci extends PT("34017") {
        override def mustBeHosted(self : Card)(implicit g : Game) = true
        override def hostRequirement(self : Card, host : Card)(implicit g : Game) = host.data.isIce
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Rezzed(c) if g.state(self).host.has(c) => $(MessageEffect(Runner, "uses Saci"), gain(Runner, 3))
            case Derezzed(c) if g.state(self).host.has(c) => $(MessageEffect(Runner, "uses Saci"), gain(Runner, 3))
            case _ => $
        }
    }

    object Shibboleth extends PT("34018") {
        override def strength(self : Card)(implicit g : Game) = g.threat(4).?(-2).|(0)
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(1), 1, $("Code Gate")))
        override def pumps(self : Card)(implicit g : Game) = $(Pump(c(2), 2))
    }

    object DebbieMoreira extends PT("34019") {
        override def onInstall(self : Card)(implicit g : Game) = g.threat(4).$(AddCounterEffect(self, "credit", 2))
        override def creditsUsableForRun(self : Card)(implicit g : Game) = thisCardRun(self)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Played(Runner, c) if g.has(c, "Run") => $(AddCounterEffect(self, "credit", 1))
            case _ => $
        }
        override def abilities(self : Card)(implicit g : Game) = $(
            Ability("run any server (hosted credits can be spent during the run)", click(), $(RunAnyServerEffect(|(self), g.servers)), click = true))
    }

    object Arissana extends PT("34020") {
        override def abilities(self : Card)(implicit g : Game) = {
            val l = g.hand(Runner).%(c => c.data.isProgram && runnerCanInstall(c, 0))
            $(Ability("install a program from the grip (trashed when the run ends unless it is a trojan)", $, $(ChooseCardEffect(Runner, l, ArissanaPurpose(self), "Arissana: choose a program to install", true)), once = once("arissana"), ok = g.run.exists(r => !r.ended) && l.any, reason = "only during a run"))
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.foreach { c =>
                g.push(RunnerInstallDiscountEffect(c, 0), AddFlagEffect(Flag("arissana", |(c), None, "run", 0, g.has(c, "Trojan").?($[ForcedAction]()).|($(ArissanaTrashAction(p.card, c))))))
            }
            NextAction
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case ArissanaTrashAction(self, c) =>
                if (g.isInstalled(c)) {
                    g.log(self, "trashes", c)
                    g.trashCard(c, Runner, false)
                }
                NextAction
        }
    }

    object JoyRide extends PT("34021") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = $(RunEffect(RnD, |(self)))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(RnD) if thisCardRun(self) => $(draw(Runner, 5))
            case _ => $
        }
    }

    object AirbladeX extends PT("34022") {
        override def initialCounters(self : Card)(implicit g : Game) = Map("power" -> 3)
        override def triggers(self : Card, e : Event)(implicit g : Game) = trashOnEmpty(self, e, "power") ++ (e match {
            case EncounterIce(ice) if g.counter(self, "power") > 0 && g.impl(ice).onEncounter(ice).any =>
                $(OptionalEffect(Runner, "AirbladeX: spend 1 hosted power counter to prevent the encounter ability of the ice?", $(AddCounterEffect(self, "power", -1), PreventEncounterAbilityEffect(ice)), $))
            case _ => $
        })
        override def preventions(self : Card, kind : String, n : Int)(implicit g : Game) =
            (kind == "net" && g.run.any && g.counter(self, "power") > 0).$(Ability("prevent 1 net damage (1 hosted power counter)", $, $(AddCounterEffect(self, "power", -1), DamageEffect("net", n - 1))))
    }

    object LilyPAD extends PT("34023") {
        override def memory(self : Card)(implicit g : Game) = 2
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Installed(Runner, c) if c.data.isProgram && g.reg("install-program") == 1 && g.deck(Runner).any => $(OptionalEffect(Runner, "LilyPAD: draw 1 card?", $(draw(Runner, 1)), $))
            case _ => $
        }
    }

    object LivingMural extends PT("34024") {
        override def mustBeHosted(self : Card)(implicit g : Game) = true
        override def hostRequirement(self : Card, host : Card)(implicit g : Game) = host.data.isIce
        override def onInstall(self : Card)(implicit g : Game) = g.threat(4).$(PumpBreakerEffect(self, 3, TurnDuration))
        override def breaks(self : Card)(implicit g : Game) = g.run.exists(r => g.serverOf(self).has(r.server)).$(BreakAbility(c(1), 1, $("Sentry"), |("break 1 sentry subroutine (ice protecting this server)")))
        override def pumps(self : Card)(implicit g : Game) = $(Pump(c(1), 2))
    }

    object Pichacao extends PT("34025") {
        override def mustBeHosted(self : Card)(implicit g : Game) = true
        override def hostRequirement(self : Card, host : Card)(implicit g : Game) = host.data.isIce
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case PassIce(ice) if g.state(self).host.has(ice) => $(OptionalEffect(Runner, "Pichacao: gain [click]?", $(PichacaoAction(self)), $))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case PichacaoAction(self) =>
                val first = g.reg("run-click-gain") == 0
                g.regInc("run-click-gain")
                g.clicks += Runner -> (g.clicks(Runner) + 1)
                g.log(Runner, "gains", 1.clicks, "(Pichacao)")
                if (!first) {
                    g.moveTo(self, HandZone(Runner))
                    g.log(self, "is added to the grip")
                }
                NextAction
        }
    }

    object SlapVandal extends PT("34026") {
        override def mustBeHosted(self : Card)(implicit g : Game) = true
        override def hostRequirement(self : Card, host : Card)(implicit g : Game) = host.data.isIce
        override def breaks(self : Card)(implicit g : Game) =
            encounterIce.%(i => g.state(self).host.has(i) && !g.hasFlag("slap", self)).$./(i => BreakAbility(c(1), 1, $(), |("break 1 subroutine on host ice (once per encounter)"), $(AddFlagEffect(g.encounterFlag("slap", self)))))
    }

    object Umbrella extends PT("34027") {
        override def breaks(self : Card)(implicit g : Game) =
            encounterIce.%(i => g.hosted(i).exists(h => h.data.isProgram && g.has(h, "Trojan"))).$./(i => BreakAbility(c(2), 3, $("Code Gate"), |("break up to 3 code gate subroutines, then each player may draw 1 card"), $(
                OptionalEffect(Runner, "Umbrella: draw 1 card?", $(draw(Runner, 1)), $),
                OptionalEffect(Corp, "Umbrella: draw 1 card?", $(draw(Corp, 1)), $))))
    }

    object BeatrizFriereGonzalez extends PT("34028") {
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("run HQ; if successful, breach R&D instead accessing 1 additional card", click(2), $(RunEffect(HQ, |(self))), click = true))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(HQ) if thisCardRun(self) => $(RedirectBreachEffect(HQ, RnD), AccessBonusEffect(RnD, 1))
            case _ => $
        }
    }

    object UrbanArtVernissage extends PT("34029") {
        def eligible(implicit g : Game) = g.installedCards(Runner).%(c => c.data.isProgram && g.has(c, "Trojan") && !g.has(c, "Virus"))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) if eligible.any =>
                $(OptionalEffect(Runner, "Urban Art Vernissage: add a non-virus trojan program to the grip to place 2 credits on it?", $(ChooseCardEffect(Runner, eligible, VernissagePurpose(self), "Urban Art Vernissage: choose a trojan program", false)), $))
            case _ => $
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.foreach { c =>
                g.moveTo(c, HandZone(Runner))
                g.log(Runner, "adds", c, "to the grip")
                g.push(AddCounterEffect(p.card, "credit", 2))
            }
            NextAction
        }
        // "You can spend hosted credits to install cards."
        override def paysFor(self : Card, spend : Spend)(implicit g : Game) = spend.installing.any
    }

    object BahiaBands extends PT("34030") {
        override def makesRun(self : Card)(implicit g : Game) = true
        // "You can spend hosted credits to pay trash costs for the remainder of this run."
        override def paysFor(self : Card, spend : Spend)(implicit g : Game) = thisCardRun(self) && spend.trashing.exists(c => g.owner(c).isCorp)
        override def onPlay(self : Card)(implicit g : Game) = $(RunAnyServerEffect(|(self), g.servers))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(_) if thisCardRun(self) => $(BahiaChoiceAction(self, 2, $))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case BahiaChoiceAction(self, k, used) =>
                val options = $(
                    (0, "Draw 2 cards", $[ForcedAction](draw(Runner, 2)), true),
                    (1, "Install 1 card from the grip paying 1 credit less", $[ForcedAction](InstallFromListDiscountEffect(Runner, g.hand(Runner), 1, "Bahia Bands: choose a card to install paying 1 credit less", true)), true),
                    (2, "Remove 1 tag", $[ForcedAction](RemoveTagsEffect(1)), g.tagged),
                    (3, "Place 4 credits on Bahia Bands to pay trash costs this run", $[ForcedAction](AddCounterEffect(self, "credit", 4)), true))
                val l = options.%(o => !used.has(o._1))./(o => Alt(o._2, o._3 ++ (k > 1).$(BahiaChoiceAction(self, k - 1, used :+ o._1)), o._4))
                g.push(ChooseOneEffect(Runner, "Bahia Bands: choose an effect (" + k + " remaining)", l))
                NextAction
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Corp
    // ------------------------------------------------------------------------------------------------------------

    object SalvoTesting extends PT("34031") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case AgendaScored(_) if g.zone(self) == ScoredZone(Corp) => $(OptionalEffect(Corp, "Salvo Testing: do 1 core damage?", $(core(1)), $))
            case _ => $
        }
    }

    object StegodonMKIV extends PT("34032") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case RunInitiated(s) if !g.usedThisTurn(self, "stegodon") && g.rezzedIce.exists(i => !g.serverOf(i).has(s)) =>
                $(OptionalEffect(Corp, "Stegodon MK IV: derez a piece of ice protecting another server to gain 1 credit?", $(MarkUsedEffect(self, "stegodon"), ChooseCardEffect(Corp, g.rezzedIce.%(i => !g.serverOf(i).has(s)), StegodonPurpose(self), "Stegodon MK IV: choose a piece of ice to derez", false)), $))
            case Derezzed(c) if c.data.isIce && g.run.exists(r => !r.ended) => $(AddFlagEffect(g.runFlag("ice-derezzed", |(c))))
            case _ => $
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.foreach(c => g.push(DerezEffect(c), gain(Corp, 1)))
            NextAction
        }
        override def breakerStrengthMod(self : Card, b : Card)(implicit g : Game) = (b.data.isIcebreaker && g.run.any && g.hasFlag("ice-derezzed")).?(-2).|(0)
    }

    object WageWorkers extends PT("34033") {
        override def onActionTaken(self : Card, key : String, n : Int)(implicit g : Game) = (n == 3 && g.isRezzed(self)).$(MessageEffect(Corp, "uses Wage Workers"), GainClicksEffect(Corp, 1))
    }

    object AblativeBarrier extends PT("34034") {
        override def subroutines(self : Card)(implicit g : Game) = $(etrSub)
        override def onRez(self : Card)(implicit g : Game) = {
            val l = (g.hand(Corp) ++ g.discard(Corp)).%(c => g.installable(c) && !c.data.isAgenda)
            (g.threat(3) && runOnThisServer(self) && l.any).$(ChooseCardEffect(Corp, l, AblativePurpose(self), "Ablative Barrier: install a non-agenda card from HQ or Archives in or protecting another server?", true))
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.foreach { c =>
                val own = g.serverOf(p.card)
                val l = ((c.data.isIce || c.data.isUpgrade).?(g.servers).|((g.remotes : $[Server]))).%(s => !own.has(s))
                g.push(ChooseServerDiscountEffect(c, 0, false, l, true))
            }
            NextAction
        }
    }

    object Jaguarundi extends PT("34035") {
        override def onEncounter(self : Card)(implicit g : Game) =
            g.threat(4).$(ChooseOneEffect(Runner, "Jaguarundi: choose one", $(Alt("Take 1 tag", $(tag(1))), Alt("Spend [click]", $(LoseClicksEffect(Runner, 1)), g.clicks(Runner) >= 1))))
        override def subroutines(self : Card)(implicit g : Game) = $(tagSub(1), Sub("If the Runner is tagged, do 1 core damage", JaguarundiAction(self)))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case JaguarundiAction(self) =>
                if (g.tagged)
                    g.push(core(1))
                NextAction
        }
    }

    object MIC extends PT("34036") {
        override def subroutines(self : Card)(implicit g : Game) = $(Sub("The Runner loses [click]", LoseClicksEffect(Runner, 1)), Sub("The Runner loses [click]", LoseClicksEffect(Runner, 1)), etrSub)
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("end the run unless the Runner spends [click]", $(TrashSelfCost), $(clickOrEtr("M.I.C.: choose one")), ok = g.isRezzed(self) && runOnThisServer(self), reason = "only during a run on this server"))
    }

    object GreasingThePalm extends PT("34037") {
        override def onPlay(self : Card)(implicit g : Game) = {
            val l = g.hand(Corp).%(g.installable)
            $(gain(Corp, 5)) ++ l.any.$(ChooseCardEffect(Corp, l, GreasingPurpose(self), "Greasing the Palm: install a card from HQ?", true))
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.foreach { c =>
                g.push(ChooseServerEffect(Corp, c, false, (c.data.isIce || c.data.isUpgrade).?(g.servers).|((g.remotes : $[Server])), true), GreasingTagAction(p.card, c))
            }
            NextAction
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case GreasingTagAction(self, c) =>
                if (g.tagged && g.isInstalled(c))
                    g.push(OptionalEffect(Corp, "Greasing the Palm: remove 1 tag to place 1 advancement counter on the installed card?", $(RemoveTagsEffect(1), PlaceAdvancementEffect(c, 1)), $))
                NextAction
        }
    }

    object VovoOzetti extends PT("34038") {
        override def rezCostMod(self : Card, c : Card)(implicit g : Game) = ((c.data.isIce || g.threat(4)) && c != self && g.serverOf(self).any && g.serverOf(c) == g.serverOf(self)).?(-2).|(0)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnEnds(Corp) if g.isRezzed(self) && g.servers.exists(s => !g.serverOf(self).has(s)) => $(OptionalEffect(Corp, "Vovo Ozetti: move it to the root of another server?", $(ChooseServerMoveEffect(self, g.servers)), $))
            case _ => $
        }
    }

    object ATeia extends PT("34039") {
        override def remoteLimit(self : Card)(implicit g : Game) = 2
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Installed(Corp, c) if g.serverOf(c).exists(_.isRemote) && g.flagsOf("installed").%(f => f.card.exists(x => g.serverOf(x).exists(_.isRemote))).num == 1 && g.hand(Corp).exists(g.installable) =>
                val s = g.serverOf(c).get
                $(OptionalEffect(Corp, "A Teia: install a card from HQ in or protecting another remote server, ignoring all costs?", $(ChooseCardEffect(Corp, g.hand(Corp).%(g.installable), ATeiaPurpose(self, s), "A Teia: choose a card to install", true)), $))
            case _ => $
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case ATeiaPurpose(self, s) =>
                cards.foreach { c =>
                    val l = (g.remotes.%(r => r != s) : $[Server])
                    if (l.any || g.canCreateRemote) {
                        g.push(ChooseServerDiscountEffect(c, 99, false, l, true))
                        g.addFlag(g.turnFlag("cannot-score", |(c)))
                    }
                    else
                        g.log("No other remote server available")
                }
                NextAction
        }
    }

    object FujiiAssetRetrieval extends PT("34040") {
        override def onScore(self : Card)(implicit g : Game) = $(net(2))
        override def onSteal(self : Card)(implicit g : Game) = $(net(2))
    }

    object FrontCompany extends PT("34041") {
        override def canRez(self : Card)(implicit g : Game) = g.active.isCorp
        override def allowsRun(self : Card, s : Server)(implicit g : Game) = !(s.isRemote && g.runsThisTurn.none)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case RunInitiated(Archives) if g.runsThisTurn.%(_ == Archives).num == 1 && g.serverOf(self).exists(s => g.ice(s).none) => $(MessageEffect(Corp, "uses Front Company"), net(2))
            case _ => $
        }
    }

    object Attini extends PT("34042") {
        override def subroutines(self : Card)(implicit g : Game) = 3.times(Sub("Do 1 net damage unless the Runner pays 2 credits", AttiniAction(self)))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case AttiniAction(self) =>
                if (g.threat(3))
                    g.push(net(1))
                else
                    g.push(ChooseOneEffect(Runner, "Attini: choose one", $(Alt("Pay 2 credits", $(SpendEffect(Runner, 2, Spend.corpAbility, 0, $)), g.canSpend(Runner, 2, Spend.corpAbility)), Alt("Take 1 net damage", $(net(1))))))
                NextAction
        }
    }

    object Phoneutria extends PT("34043") {
        override def subroutines(self : Card)(implicit g : Game) = $(netSub(1), netSub(1))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case PassIce(ice) if ice == self && g.hand(Runner).num >= 4 => $(MessageEffect(Corp, "uses Phoneutria"), tag(1))
            case _ => $
        }
    }

    object TatuBola extends PT("34044") {
        override def subroutines(self : Card)(implicit g : Game) = $(etrSub)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case PassIce(ice) if ice == self && g.hand(Corp).exists(_.data.isIce) =>
                $(OptionalEffect(Corp, "Tatu-Bola: swap it with a piece of ice from HQ and gain 4 credits?", $(ChooseCardEffect(Corp, g.hand(Corp).%(_.data.isIce), TatuPurpose(self), "Tatu-Bola: choose a piece of ice in HQ", false)), $))
            case _ => $
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.foreach(c => g.push(SwapWithHandIceEffect(p.card, c), gain(Corp, 4)))
            NextAction
        }
    }

    object Mindscaping extends PT("34045") {
        override def onPlay(self : Card)(implicit g : Game) = $(ChooseOneEffect(Corp, "Mindscaping", $(
            Alt("Gain 4 credits and draw 2 cards, then add 1 card from HQ to the top of R&D", $(gain(Corp, 4), draw(Corp, 2), MindscapingAction(self))),
            Alt("Do " + g.tags.atMost(3) + " net damage (1 per tag, up to 3)", $(net(g.tags.atMost(3)))))))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case MindscapingAction(self) =>
                if (g.hand(Corp).any)
                    g.push(ChooseCardEffect(Corp, g.hand(Corp), MindscapingPurpose(self), "Mindscaping: choose a card to add to the top of R&D", false))
                NextAction
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.foreach { c =>
                g.moveTo(c, DeckZone(Corp), true)
                g.log(Corp, "adds a card from HQ to the top of R&D")
            }
            NextAction
        }
    }

    object AdrianSeis extends PT("34046") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(s) if thisServer(self, s) && g.isRezzed(self) =>
                $(MessageEffect(Corp, "uses Adrian Seis"), PsiEffect(
                    $(MessageEffect(Corp, "prevents the Runner from accessing Adrian Seis"), AddFlagEffect(g.runFlag("access-exclude", |(self)))),
                    $(MessageEffect(Corp, "prevents the Runner from accessing cards other than Adrian Seis"), AddFlagEffect(g.runFlag("access-only", |(self))))))
            case TurnEnds(Corp) if g.isRezzed(self) && g.servers.exists(s => !g.serverOf(self).has(s)) => $(OptionalEffect(Corp, "Adrian Seis: move it to the root of another server?", $(ChooseServerMoveEffect(self, g.servers)), $))
            case _ => $
        }
    }

    object DanielaJorgeInacio extends PT("34047") {
        override def canBeTrashedOnAccess(self : Card)(implicit g : Game) = g.hand(Runner).num >= 2
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case AgendaStolen(_) if g.isRezzed(self) && g.run.exists(r => thisServer(self, r.server)) => $(MessageEffect(Corp, "uses Daniela Jorge Inacio"), RandomToBottomEffect(Runner, 2))
            case _ => $
        }
        override def onTrash(self : Card, by : Faction)(implicit g : Game) =
            (by.isRunner && g.state(self).seen).??(g.run.%(r => !r.ended).$./~(r => $(MessageEffect(Corp, "uses Daniela Jorge Inacio"), RandomToBottomEffect(Runner, 2), AddFlagEffect(Flag("on-steal", |(self), |(r.server), "run", 0, $(MessageEffect(Corp, "uses Daniela Jorge Inacio"), RandomToBottomEffect(Runner, 2)))))))
    }

    object EpiphanyAnalytica extends PT("34048") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case AgendaStolen(_) if !g.usedThisTurn(self, "epiphany") => $(MarkUsedEffect(self, "epiphany"), AddCounterEffect(self, "power", 1))
            case Trashed(c, Runner, _) if g.owner(c).isCorp && !g.usedThisTurn(self, "epiphany") => $(MarkUsedEffect(self, "epiphany"), AddCounterEffect(self, "power", 1))
            case _ => $
        }
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("remove 1 hosted power counter to look at the top 3 cards of R&D and install 1 of them", click(), $(AddCounterEffect(self, "power", -1), EpiphanyAction(self)), click = true, ok = g.counter(self, "power") > 0 && g.deck(Corp).any, reason = "no power counters or empty R&D"))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case EpiphanyAction(self) =>
                val top = g.deck(Corp).take(3)
                g.log(Corp, "looks at the top", top.num.cards, "of R&D")
                g.push(LookAtEffect(Corp, top, "Epiphany Analytica: the top cards of R&D"), ChooseCardEffect(Corp, top.%(g.installable), InstallChooseServerPurpose(false), "Epiphany Analytica: install 1 of the cards?", true))
                NextAction
        }
    }

    object OracleThinktank extends PT("34049") {
        override def onSteal(self : Card)(implicit g : Game) = $(tag(1))
        override def abilities(self : Card)(implicit g : Game) =
            (g.zone(self) == ScoredZone(Runner) && g.active.isCorp).$(Ability("remove 1 tag to shuffle Oracle Thinktank into R&D", click(), $(RemoveTagsEffect(1), OracleThinktankAction(self)), click = true, ok = g.tagged, reason = "the Runner is not tagged"))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case OracleThinktankAction(self) =>
                g.moveTo(self, DeckZone(Corp))
                g.log(Corp, "shuffles", self, "into R&D")
                g.push(ShuffleDeckEffect(Corp))
                NextAction
        }
    }

    object BalancedCoverage extends PT("34050") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) if g.isRezzed(self) && g.deck(Corp).any =>
                $(OptionalEffect(Corp, "Balanced Coverage: choose a card type to look at the top card of R&D?", $(ChooseOneEffect(Corp, "Balanced Coverage: choose a card type", $("Operation", "Asset", "Upgrade", "ICE", "Agenda")./(t => Alt(t, $(BalancedAction(self, t)))))), $))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case BalancedAction(self, t) =>
                g.deck(Corp).starting.foreach { top =>
                    if (top.data.kind == t)
                        g.push(LookAtEffect(Corp, $(top), "Balanced Coverage: the top card of R&D"), OptionalEffect(Corp, "Balanced Coverage: reveal it to gain 2 credits?", $(RevealEffect(Corp, top), gain(Corp, 2)), $))
                    else
                        g.push(LookAtEffect(Corp, $(top), "Balanced Coverage: the top card of R&D"))
                }
                NextAction
        }
    }

    object Behold extends PT("34051") {
        override def onAccess(self : Card)(implicit g : Game) =
            (g.zone(self) == DeckZone(Corp)).$(RevealEffect(Corp, self)) ++ (g.credits(Corp) >= 4).$(OptionalEffect(Corp, "Behold!: pay 4 credits to give the Runner 2 tags?", $(LoseCreditsEffect(Corp, 4), tag(2)), $))
    }

    object FederalFundraising extends PT("34052") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) if g.isRezzed(self) && g.deck(Corp).any =>
                $(OptionalEffect(Corp, "Federal Fundraising: look at the top 3 cards of R&D and arrange them?", $(ArrangeTopEffect(Corp, Corp, 3), FedDrawAction(self)), $(FedDrawAction(self))))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case FedDrawAction(self) =>
                if (g.serverOf(self).exists(s => g.ice(s).none) && g.deck(Corp).any)
                    g.push(OptionalEffect(Corp, "Federal Fundraising: draw 1 card?", $(draw(Corp, 1)), $))
                NextAction
        }
    }

    object StarlitKnight extends PT("34053") {
        override def onEncounter(self : Card)(implicit g : Game) = (g.threat(4) && g.tags > 0).$(MessageEffect(Corp, "uses Starlit Knight to gain " + g.tags + " end the run subroutines"), AddFlagEffect(Flag("starlit", |(self), None, "run", g.tags, $)))
        override def subroutines(self : Card)(implicit g : Game) = $(tagSub(1), tagSub(1)) ++ g.flagsOf("starlit").%(_.card.has(self)).starting./(_.until).|(0).times(etrSub)
    }

    object VirtualServiceAgent extends PT("34054") {
        override def subroutines(self : Card)(implicit g : Game) = $(loseSub(1))
        override def onEncounterEnds(self : Card)(implicit g : Game) =
            (!g.flagsOf("broken-by").exists(_.card.exists(b => g.has(b, "Decoder")))).$(MessageEffect(Corp, "uses Virtual Service Agent"), tag(1))
    }

    object OppoResearch extends PT("34055") {
        override def canPlay(self : Card)(implicit g : Game) = g.regLast("stolen") > 0 || g.regLast("runner-trash-corp") > 0
        override def onPlay(self : Card)(implicit g : Game) =
            $(tag(2)) ++ (g.threat(3) && g.credits(Corp) >= 5).$(OptionalEffect(Corp, "Oppo Research: pay 5 credits to give the Runner 2 more tags?", $(LoseCreditsEffect(Corp, 5), tag(2)), $)) ++ $(MessageEffect(Corp, "ends the action phase (Terminal)"), LoseClicksEffect(Corp, 99))
    }

    object YourDigitalLife extends PT("34056") {
        override def onPlay(self : Card)(implicit g : Game) = $(gain(Corp, g.hand(Corp).num))
    }

    object SlashAndBurnAgriculture extends PT("34057") {
        override def abilities(self : Card)(implicit g : Game) =
            (g.zone(self) == HandZone(Corp)).$(Ability("expend: place 2 advancement counters on an installed card you can advance", clickc(1), $(TrashEffect(self, Corp, false), ChooseCardEffect(Corp, g.advanceableCards, AdvancePurpose(2), "Slash and Burn Agriculture: choose a card to advance", false)), click = true, ok = g.advanceableCards.any, reason = "nothing to advance"))
    }

    object CybersandHarvester extends PT("34058") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Rezzed(c) if c.data.isIce && g.isRezzed(self) => $(AddCounterEffect(self, "credit", 2))
            case _ => $
        }
        // "You can spend hosted credits to pay install costs."
        override def paysFor(self : Card, spend : Spend)(implicit g : Game) = spend.installing.any
        override def abilities(self : Card)(implicit g : Game) = $(
            Ability("take all hosted credits", $(TrashSelfCost), $(TakeCreditsEffect(Corp, self, 99)), ok = g.counter(self, "credit") > 0, reason = "no hosted credits"))
    }

    object TreeLine extends PT("34059") {
        override def advanceable(self : Card)(implicit g : Game) = true
        override def strength(self : Card)(implicit g : Game) = g.advancement(self)
        override def subroutines(self : Card)(implicit g : Game) = $(Sub("Gain 1 credit. End the run", SeqEffect($(gain(Corp, 1), EndRunEffect))))
        override def abilities(self : Card)(implicit g : Game) =
            (g.zone(self) == HandZone(Corp)).$(Ability("expend: place 3 advancement counters on an installed piece of ice", clickc(1), $(TrashEffect(self, Corp, false), ChooseCardEffect(Corp, g.allIce, AdvancePurpose(3), "Tree Line: choose a piece of ice", false)), click = true, ok = g.allIce.any, reason = "no installed ice"))
    }

    object Valentao extends PT("34060") {
        override def onRez(self : Card)(implicit g : Game) = $(ChooseOneEffect(Corp, "Valentao: additional rez cost", $(Alt("Take 1 bad publicity", $(BadPubEffect(1))), Alt("Remove 1 tag", $(RemoveTagsEffect(1)), g.tagged))))
        override def subroutines(self : Card)(implicit g : Game) = $(gainSub(2), loseSub(2), Sub("End the run if you have more credits than the Runner", ValentaoAction(self)))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case ValentaoAction(self) =>
                if (g.credits(Corp) > g.credits(Runner))
                    g.push(EndRunEffect)
                NextAction
        }
    }

    object ArmedAssetProtection extends PT("34061") {
        override def onPlay(self : Card)(implicit g : Game) = {
            val faceup = g.discard(Corp).%(c => g.state(c).seen)
            $(gain(Corp, 3 + faceup./(_.data.kind).distinct.num)) ++ faceup.exists(_.data.isAgenda).$(gain(Corp, 2))
        }
    }

    object Pivot extends PT("34062") {
        override def canPlay(self : Card)(implicit g : Game) = g.clicks(Corp) >= 2
        override def onPlay(self : Card)(implicit g : Game) = {
            val l = g.deck(Corp).%(c => c.data.isOperation || c.data.isAgenda).sortBy(_.title)
            $(LoseClicksEffect(Corp, 1)) ++ l.any.$(ChooseCardEffect(Corp, l, PivotPurpose(self), "Pivot: choose an operation or agenda to reveal and add to HQ", true)) ++ $(ShuffleDeckEffect(Corp)) ++ g.threat(3).$(PivotPlayAction(self))
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case PivotPurpose(self) =>
                cards.foreach { c =>
                    g.log(Corp, "reveals", c, "from R&D and adds it to HQ")
                    g.state(c).seen = true
                    g.moveTo(c, HandZone(Corp))
                }
                NextAction
            case PivotPlayPurpose(self) =>
                cards.foreach { c =>
                    if (c.data.isOperation)
                        g.push(PlayCardFreeEffect(c))
                    else
                        g.push(ChooseServerEffect(Corp, c, false, (c.data.isIce || c.data.isUpgrade).?(g.servers).|((g.remotes : $[Server])), true))
                }
                NextAction
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case PivotPlayAction(self) =>
                val l = g.hand(Corp).%(c => (c.data.isOperation && g.canSpend(Corp, g.playCost(c), Spend.play(c)) && g.impl(c).canPlay(c)) || g.installable(c))
                if (l.any)
                    g.push(ChooseCardEffect(Corp, l, PivotPlayPurpose(self), "Pivot: play or install a card from HQ?", true))
                NextAction
        }
    }

    object AngeliqueGarzaCorrea extends PT("34063") {
        override def abilities(self : Card)(implicit g : Game) =
            (g.zone(self) == HandZone(Corp)).$(Ability("expend: do 1 meat damage", clickc(1), $(TrashEffect(self, Corp, false), meat(1)), click = true, ok = g.threat(3), reason = "threat 3 not reached"))
        override def onAccess(self : Card)(implicit g : Game) =
            (g.isRezzed(self) && g.credits(Corp) >= 2).$(OptionalEffect(Corp, "Angelique Garza Correa: pay 2 credits to do 2 meat damage?", $(LoseCreditsEffect(Corp, 2), meat(2)), $))
    }

    object Tucana extends PT("34064") {
        def search(self : Card)(implicit g : Game) : $[ForcedAction] = {
            val l = g.deck(Corp).%(_.data.isIce).sortBy(_.title)
            $(OptionalEffect(Corp, "Tucana: search R&D for a piece of ice to install and rez paying 3 credits less?", $(ChooseCardEffect(Corp, l, TucanaPurpose(self), "Tucana: choose a piece of ice", true), ShuffleDeckEffect(Corp)), $))
        }
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case AgendaScored(_) if g.isRezzed(self) && g.lastScoredFrom.exists(s => thisServer(self, s)) && g.deck(Corp).exists(_.data.isIce) => search(self)
            case AgendaStolen(_) if g.isRezzed(self) && g.run.exists(r => thisServer(self, r.server)) && g.deck(Corp).exists(_.data.isIce) => search(self)
            case _ => $
        }
        override def onTrash(self : Card, by : Faction)(implicit g : Game) =
            (by.isRunner && g.state(self).seen).??(g.run.%(r => !r.ended).$./(r => AddFlagEffect(Flag("on-steal", |(self), |(r.server), "run", 0, search(self)))))
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.foreach(c => g.push(ChooseServerDiscountEffect(c, 3, true, g.servers, true)))
            NextAction
        }
    }

    object B1001 extends PT("34065") {
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("remove 1 tag to end the run (run on another server)", $, $(RemoveTagsEffect(1), EndRunEffect), ok = g.isRezzed(self) && g.tagged && g.run.exists(r => !r.ended && !g.serverOf(self).has(r.server)), reason = "needs a tagged Runner running another server"))
    }

    val all : $[CardImpl] = $(
        StrikeFund, ThePrice, SolidarityBadge, AudreyV2, Banner, Monkeywrench, EruAyasePessoa, HannahWheels, LagoParanoaShelter, Mercury,
        ChrysopoeianSkimming, SDobrado, Capybara, Hermes, Curupira, LaserPointer, Saci, Shibboleth, DebbieMoreira, Arissana, JoyRide,
        AirbladeX, LilyPAD, LivingMural, Pichacao, SlapVandal, Umbrella, BeatrizFriereGonzalez, UrbanArtVernissage, BahiaBands,
        SalvoTesting, StegodonMKIV, WageWorkers, AblativeBarrier, Jaguarundi, MIC, GreasingThePalm, VovoOzetti, ATeia, FujiiAssetRetrieval,
        FrontCompany, Attini, Phoneutria, TatuBola, Mindscaping, AdrianSeis, DanielaJorgeInacio, EpiphanyAnalytica, OracleThinktank,
        BalancedCoverage, Behold, FederalFundraising, StarlitKnight, VirtualServiceAgent, OppoResearch, YourDigitalLife,
        SlashAndBurnAgriculture, CybersandHarvester, TreeLine, Valentao, ArmedAssetProtection, Pivot, AngeliqueGarzaCorrea, Tucana, B1001,
    )
}
