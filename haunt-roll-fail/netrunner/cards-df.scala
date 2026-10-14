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
// Downfall (df) card implementations, ported from jinteki.net src/clj/game/cards/*.clj
// ---------------------------------------------------------------------------------------------------------------------

case class ChiselAction(card : Card) extends CardAction
case class StargateAction(card : Card) extends CardAction
case class ClimacticAction(card : Card) extends CardAction
case class ClimacticCorpAction(card : Card, server : Server) extends CardAction
case class NihilistPickAction(card : Card, n : Int) extends CardAction
case class NihilistPurpose(card : Card, n : Int) extends CardPurpose
case class NihilistCorpAction(card : Card) extends CardAction
case class BackupPlanAction(card : Card, server : Server) extends CardAction
case class KhusyukAction(card : Card) extends CardAction
case class KhusyukRevealAction(card : Card, n : Int) extends CardAction
case class KhusyukPurpose(card : Card) extends CardPurpose
case class PelangiAction(card : Card, subtype : String) extends CardAction
case class PelangiClearAction(card : Card) extends CardAction
case class RejigPurpose(card : Card) extends CardPurpose
case class RejigInstallAction(card : Card, discount : Int) extends CardAction
case class WhistleblowerPurpose(card : Card) extends CardPurpose
case class WhistleblowerStealAction(card : Card, agenda : Card) extends CardAction
case class MirrorMorphAction(card : Card, kind : String) extends CardAction
case class MirrorMorphResetAction(card : Card) extends CardAction
case class FullyOperationalAction(card : Card, n : Int) extends CardAction
case class RedLevelAction(card : Card, done : $[String]) extends CardAction
case class YagiUdaPurpose(card : Card) extends CardPurpose
case class YagiUdaSwapPurpose(card : Card, installed : Card) extends CardPurpose
case class SaisentanSubAction(card : Card) extends CardAction
case class SaisentanCheckAction(card : Card) extends CardAction
case class CompleteImagePurpose(card : Card) extends CardPurpose
case class CompleteImageCheckAction(card : Card, code : String) extends CardAction
case class LootBoxSubAction(card : Card) extends CardAction
case class LootBoxPurpose(card : Card) extends CardPurpose
case class FocusGroupAction(card : Card, kind : String) extends CardAction
case class GameOverOpAction(card : Card, kind : String) extends CardAction
case class GameOverPurpose(card : Card, targets : $[Card]) extends CardPurpose
case class DivestedAction(card : Card, stolen : Card) extends CardAction
case class SecureAndProtectPurpose(card : Card) extends CardPurpose
case class ReducedServiceAction(card : Card, n : Int) extends CardAction
case class HyoubuRevealPurpose(card : Card) extends CardPurpose


object DFCards {
    // ------------------------------------------------------------------------------------------------------------
    // Runner
    // ------------------------------------------------------------------------------------------------------------

    object Isolation extends NC("26001") {
        override def canPlay(self : Card)(implicit g : Game) = g.runnerInstalledOf("Resource").any
        override def onPlay(self : Card)(implicit g : Game) = $(ChooseCardEffect(Runner, g.runnerInstalledOf("Resource"), TrashPurpose(Runner), "Isolation: choose an installed resource to trash", false), gain(Runner, 7))
    }

    object Demolisher extends NC("26002") {
        override def memory(self : Card)(implicit g : Game) = 1
        override def trashCostMod(self : Card, card : Card)(implicit g : Game) = card.data.isCorp.?(-1).|(0)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Trashed(c, Runner, _) if c.data.isCorp && !g.usedThisTurn(self, "demolisher") => $(MarkUsedEffect(self, "demolisher"), gain(Runner, 1))
            case _ => $
        }
    }

    object Chisel extends NC("26003") {
        override def mustBeHosted(self : Card)(implicit g : Game) = true
        override def hostRequirement(self : Card, host : Card)(implicit g : Game) = host.data.isIce
        override def iceStrengthMod(self : Card, ice : Card)(implicit g : Game) = g.state(self).host.has(ice).?(-g.counter(self, "virus")).|(0)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case EncounterIce(ice) if g.state(self).host.has(ice) => $(ChiselAction(self))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case ChiselAction(self) =>
                g.state(self).host.foreach { h =>
                    if (g.iceStrength(h) <= 0)
                        g.push(TrashEffect(h, Runner, false))
                    else
                        g.push(AddCounterEffect(self, "virus", 1))
                }
                NextAction
        }
    }

    object Stargate extends NC("26004") {
        override def abilities(self : Card)(implicit g : Game) = $(Ability("run R&D", click(), $(RunEffect(RnD, |(self))), click = true, once = once("stargate")))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(RnD) if thisCardRun(self) => $(ReplaceBreachEffect, StargateAction(self))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case StargateAction(self) =>
                val l = g.deck(Corp).take(3)
                if (l.any)
                    g.push(RevealCardsEffect(Corp, l), ChooseCardEffect(Runner, l, TrashPurpose(Runner), "Stargate: choose a revealed card to trash", false))
                NextAction
        }
    }

    object Utae extends NC("26005") {
        override def breaks(self : Card)(implicit g : Game) =
            (!g.state(self).usedRun.has("utae")).$(BreakAbility(c(1), 0, $("Code Gate"), |("break X code gate subroutines (X credits, once per run)"), $(MarkUsedRunEffect(self, "utae")), true)) ++
            (virtualResources >= 3).$(BreakAbility(c(1), 1, $("Code Gate"), |("break 1 code gate subroutine (3 virtual resources)")))
        override def pumps(self : Card)(implicit g : Game) = $(Pump(c(1), 1))
    }

    object ClimacticShowdown extends NC("26006") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) if g.isInstalled(self) => $(RemoveFromGameEffect(self), ClimacticAction(self))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case ClimacticAction(self) =>
                val l = g.servers.%(s => g.ice(s).any)
                if (l.any)
                    g.push(ChooseOneEffect(Runner, "Climactic Showdown: choose a server protected by ice", l./(s => Alt(s.name, $(ClimacticCorpAction(self, s))))))
                NextAction
            case ClimacticCorpAction(self, s) =>
                g.push(OptionalEffect(Corp, "Climactic Showdown: trash a piece of ice protecting " + s.name + "? (otherwise the Runner accesses 2 additional cards from HQ or R&D this turn)",
                    $(ChooseCardEffect(Corp, g.ice(s), TrashPurpose(Corp), "Choose a piece of ice to trash", false)),
                    $(BreachBonusEffect(2), MessageEffect(Runner, "will access 2 additional cards the first time they breach HQ or R&D this turn"))))
                NextAction
        }
    }

    object FencerFueno extends NC("26007") {
        override def creditsUsableForRun(self : Card)(implicit g : Game) = g.run.exists(_.successful)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) | AgendaStolen(_) => $(AddCounterEffect(self, "credit", 1))
            case TurnEnds(Runner) if g.counter(self, "credit") >= 3 =>
                $(ChooseOneEffect(Runner, "Fencer Fueno: choose one", $(Alt("Pay 1 credit", $(LoseCreditsEffect(Runner, 1)), g.credits(Runner) >= 1), Alt("Trash Fencer Fueno", $(TrashEffect(self, Runner, false))))))
            case _ => $
        }
        override def abilities(self : Card)(implicit g : Game) = $(Ability("take 1 credit", NoCost, $(TakeCreditsEffect(Runner, self, 1)), ok = g.counter(self, "credit") > 0 && g.run.exists(_.successful), reason = "only during a successful run"))
    }

    object TheNihilist extends NC("26008") {
        def totalVirus(implicit g : Game) = g.runnerInstalled./(c => g.counter(c, "virus")).sum
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Installed(Runner, c) if g.has(c, "Virus") && c.data.isProgram && !g.usedThisTurn(self, "nihilist") => $(MarkUsedEffect(self, "nihilist"), AddCounterEffect(self, "virus", 2))
            case TurnBegins(Runner) if totalVirus >= 2 => $(OptionalEffect(Runner, "The Nihilist: remove 2 virus counters to draw 2 cards unless the Corp trashes the top card of R&D?", $(NihilistPickAction(self, 2)), $))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case NihilistPickAction(self, n) =>
                val l = g.runnerInstalled.%(c => g.counter(c, "virus") > 0)
                if (l.any)
                    g.push(ChooseCardEffect(Runner, l, NihilistPurpose(self, n), "The Nihilist: choose a card to remove 1 virus counter from (" + n + " left)", false))
                NextAction
            case NihilistCorpAction(self) =>
                g.push(ChooseOneEffect(Corp, "The Nihilist: choose one", $(Alt("Trash the top card of R&D", $(TrashTopEffect(Corp, 1, false)), g.deck(Corp).any), Alt("The Runner draws 2 cards", $(draw(Runner, 2))))))
                NextAction
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case NihilistPurpose(self, n) =>
                cards.single.foreach { c =>
                    g.push(AddCounterEffect(c, "virus", -1), (n > 1).?(NihilistPickAction(self, n - 1)).|(NihilistCorpAction(self)))
                }
                NextAction
        }
    }

    object TricksterTaka extends NC("26009") {
        override def creditsUsableForRun(self : Card)(implicit g : Game) = g.run.exists(r => !r.successful && !r.unsuccessful)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) | AgendaStolen(_) => $(AddCounterEffect(self, "credit", 1))
            case TurnEnds(Runner) if g.counter(self, "credit") >= 3 =>
                $(ChooseOneEffect(Runner, "Trickster Taka: choose one", $(Alt("Take 1 tag", $(tag(1))), Alt("Trash Trickster Taka", $(TrashEffect(self, Runner, false))))))
            case _ => $
        }
        override def abilities(self : Card)(implicit g : Game) = $(Ability("take 1 credit", NoCost, $(TakeCreditsEffect(Runner, self, 1)), ok = g.counter(self, "credit") > 0 && g.run.exists(r => !r.successful && !r.unsuccessful), reason = "only during a run"))
    }

    object Az extends NC("26010") {
        def azType(c : Card)(implicit g : Game) = c.data.isHardware || (c.data.isResource && (g.has(c, "Job") || g.has(c, "Connection")))
        override def installCostMod(self : Card, card : Card)(implicit g : Game) = (azType(card) && g.reg("az") == 0).?(-1).|(0)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Installed(Runner, c) if azType(c) => $(RegisterEffect("az", 1))
            case _ => $
        }
    }

    object AlwaysHaveABackupPlan extends NC("26011") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = $(RunAnyServerEffect(|(self), g.servers))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case EncounterIce(ice) if thisCardRun(self) && g.usedThisTurn(self, "second") && g.state(self).target.has(ice) => $(BypassEffect)
            case EncounterIce(ice) if thisCardRun(self) && !g.usedThisTurn(self, "second") => $(SetTargetEffect(self, |(ice)))
            case RunEnds(s, false, _) if thisCardRun(self) && !g.usedThisTurn(self, "second") =>
                $(OptionalEffect(Runner, "Always Have a Backup Plan: run the same server again, ignoring additional costs?", $(MarkUsedEffect(self, "second"), RunIgnoringCostsEffect(s, |(self))), $))
            case _ => $
        }
    }

    object BlueberryDiesel extends NC("26012") {
        override def onPlay(self : Card)(implicit g : Game) = {
            val l = g.deck(Runner).take(2)
            l.any.$(ChooseCardEffect(Runner, l, BottomOfDeckPurpose(Runner), "Blueberry Diesel: add a card to the bottom of the stack?", true)) :+ draw(Runner, 2)
        }
    }

    object FlipSwitch extends NC("26013") {
        override def abilities(self : Card)(implicit g : Game) = $(
            Ability("jack out", $(TrashSelfCost), $(JackOutEffect), ok = g.active.isRunner && g.run.exists(r => !r.ended), reason = "only during a run on your turn"),
            Ability("remove 1 tag", $(TrashSelfCost), $(RemoveTagsEffect(1)), ok = g.active.isRunner && g.tagged, reason = "not tagged or not your turn"))
        override def preventions(self : Card, kind : String, n : Int)(implicit g : Game) =
            (kind == "trace" && g.active.isRunner).$(Ability("reduce the base trace strength to 0", $(TrashSelfCost), $(FlipSwitchTraceAction(self))))
    }

    object LuckyCharm extends NC("26014") {
        override def preventions(self : Card, kind : String, n : Int)(implicit g : Game) =
            (kind == "end-run" && g.successfulRunsThisTurn.has(HQ) && Prevent.ctx.exists(_.by.isCorp)).$(Ability("prevent the run from ending", $(RemoveSelfCost), $(Prevent.endRun)))
    }

    object Masterwork extends NC("26015") {
        override def memory(self : Card)(implicit g : Game) = 1
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case RunInitiated(_) if g.hand(Runner).%(c => c.data.isHardware && g.canAffordInstall(c, -1)).any =>
                $(OptionalEffect(Runner, "Masterwork: install a piece of hardware paying 1 credit more?", $(ChooseCardEffect(Runner, g.hand(Runner).%(c => c.data.isHardware && g.canAffordInstall(c, -1)), InstallPayingPurpose(-1, None, false), "Choose a piece of hardware", true)), $))
            case Installed(Runner, c) if c.data.isHardware && !g.usedThisTurn(self, "masterwork") => $(MarkUsedEffect(self, "masterwork"), draw(Runner, 1))
            case _ => $
        }
    }

    object Bukhgalter extends NC("26016") {
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(1), 1, $("Sentry")))
        override def pumps(self : Card)(implicit g : Game) = $(Pump(c(1), 1))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SubsBroken(ice, _, b) if b == self && g.unbrokenSubs(ice).none && !g.usedThisTurn(self, "bukh") => $(MarkUsedEffect(self, "bukh"), gain(Runner, 2))
            case _ => $
        }
    }

    object Baklan extends NC("26017") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case EncounterIce(_) if !g.state(self).usedRun.has("baklan") => $(MarkUsedRunEffect(self, "baklan"), AddCounterEffect(self, "power", 1))
            case _ => $
        }
        override def abilities(self : Card)(implicit g : Game) = {
            val ice = encountering(self).%(i => g.isRezzed(i) && g.iceStrength(i) <= g.counter(self, "power"))
            $(Ability("derez the encountered ice (strength at most " + g.counter(self, "power") + ") and take 1 tag", $(TrashSelfCost), ice.$./~(i => $(DerezEffect(i), tag(1))), ok = ice.any, reason = "no encountered ice with strength at most the hosted power counters"))
        }
    }

    object TheClassAct extends NC("26018") {
        override def onInstall(self : Card)(implicit g : Game) = $(MarkUsedEffect(self, "installed"))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnEnds(_) if g.usedThisTurn(self, "installed") => $(draw(Runner, 4))
            case _ => $
        }
        override def beforeDraw(self : Card, f : Faction, n : Int)(implicit g : Game) =
            (f.isRunner && n > 0 && g.deck(Runner).num > 1 && !g.usedThisTurn(self, "class-act")).$(MarkUsedEffect(self, "class-act"), ChooseCardEffect(Runner, g.deck(Runner).take(n + 1), BottomOfDeckPurpose(Runner), "The Class Act: choose a card to add to the bottom of the stack", false))
    }

    object Lat extends NC("26019") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnEnds(Runner) if g.hand(Runner).num == g.hand(Corp).num && g.deck(Runner).any => $(OptionalEffect(Runner, "Lat: draw 1 card?", $(draw(Runner, 1)), $))
            case _ => $
        }
    }

    object InTheGroove extends NC("26020") {
        override def canPlay(self : Card)(implicit g : Game) = g.clicks(Runner) >= 4
        override def staysInPlay(self : Card)(implicit g : Game) = true
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Installed(Runner, c) if c.data.cost.exists(_ >= 1) => $(ChooseOneEffect(Runner, "In the Groove: choose one", $(Alt("Draw 1 card", $(draw(Runner, 1))), Alt("Gain 1 credit", $(gain(Runner, 1))))))
            case TurnEnds(Runner) => $(TrashEffect(self, Runner, false))
            case _ => $
        }
    }

    object Khusyuk extends NC("26021") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = $(RunEffect(RnD, |(self)))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(RnD) if thisCardRun(self) => $(ReplaceBreachEffect, KhusyukAction(self))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case KhusyukAction(self) =>
                val costs = g.runnerInstalled./~(_.data.cost).%(_ > 0)
                val l = (costs.distinct.sorted ++ (costs.none.$(1)))./(x => Alt(x + " credits: " + costs.count(_ == x) + " cards", $(KhusyukRevealAction(self, costs.count(_ == x).atMost(6)))))
                g.push(ChooseOneEffect(Runner, "Khusyuk: choose an install cost", l))
                NextAction
            case KhusyukRevealAction(self, n) =>
                val l = g.deck(Corp).take(n)
                if (l.any)
                    g.push(RevealCardsEffect(Corp, l), ChooseCardEffect(Runner, l, KhusyukPurpose(self), "Khusyuk: choose a card to access", false), ShuffleDeckEffect(Corp))
                NextAction
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(c => g.push(AccessCardAction(c)))
            NextAction
        }
    }

    object SpecWork extends NC("26022") {
        override def canPlay(self : Card)(implicit g : Game) = g.runnerInstalledOf("Program").any
        override def onPlay(self : Card)(implicit g : Game) = $(ChooseCardEffect(Runner, g.runnerInstalledOf("Program"), TrashPurpose(Runner), "Spec Work: choose an installed program to trash", false), gain(Runner, 4), draw(Runner, 2))
    }

    object Supercorridor extends NC("26023") {
        override def memory(self : Card)(implicit g : Game) = 2
        override def handSize(self : Card, f : Faction)(implicit g : Game) = f.isRunner.?(1).|(0)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnEnds(Runner) if g.credits(Runner) == g.credits(Corp) => $(OptionalEffect(Runner, "Supercorridor: gain 2 credits?", $(gain(Runner, 2)), $))
            case _ => $
        }
    }

    object Gauss extends NC("26024") {
        override def strength(self : Card)(implicit g : Game) = installedThisTurn(self).?(3).|(0)
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(1), 1, $("Barrier")))
        override def pumps(self : Card)(implicit g : Game) = $(Pump(c(2), 2))
    }

    object Pelangi extends NC("26025") {
        override def initialCounters(self : Card)(implicit g : Game) = Map("virus" -> 2)
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("make the encountered ice gain a subtype", $(VirusCost(1)), $(ChooseOneEffect(Runner, "Pelangi: choose a subtype", $("Barrier", "Code Gate", "Sentry")./(k => Alt(k, $(PelangiAction(self, k)))))), once = once("pelangi"), ok = encountering(self).any, reason = "not encountering ice"))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case EncounterEnds(ice) if g.state(self).target.has(ice) => $(PelangiClearAction(self))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case PelangiAction(self, k) =>
                encountering(self).foreach { ice =>
                    g.state(ice).extraSubtypes :+= k
                    g.state(self).target = |(ice)
                    g.state(self).memo = k
                    g.log(ice, "gains", k.hl, "until the end of the encounter")
                }
                NextAction
            case PelangiClearAction(self) =>
                g.state(self).target.foreach { ice =>
                    val l = g.state(ice).extraSubtypes
                    val i = l.indexOf(g.state(self).memo)
                    if (i >= 0)
                        g.state(ice).extraSubtypes = l.take(i) ++ l.drop(i + 1)
                }
                g.state(self).target = None
                NextAction
        }
    }

    object Rezeki extends NC("26026") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) => $(gain(Runner, 1))
            case _ => $
        }
    }

    object TheArtist extends NC("26027") {
        override def abilities(self : Card)(implicit g : Game) = {
            val l = g.hand(Runner).%(c => (c.data.isProgram || c.data.isHardware) && g.canAffordInstall(c, 1))
            $(
                Ability("gain 2 credits", click(), $(gain(Runner, 2)), click = true, once = once("artist-credits")),
                Ability("install a program or piece of hardware paying 1 credit less", click(), $(ChooseCardEffect(Runner, l, InstallPayingPurpose(1, None, false), "The Artist: choose a card to install", false)), click = true, once = once("artist-install"), ok = l.any, reason = "nothing to install"))
        }
    }

    object DirectAccess extends NC("26028") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = $(RunAnyServerEffect(|(self), g.servers))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case RunEnds(_, _, _) if thisCardRun(self) => $(OptionalEffect(Runner, "Direct Access: shuffle it into the stack?", $(MoveEffect(self, DeckZone(Runner), false), ShuffleDeckEffect(Runner)), $))
            case _ => $
        }
    }

    object Rejig extends NC("26029") {
        def targets(implicit g : Game) = g.runnerInstalled.%(c => c.data.isProgram || c.data.isHardware)
        override def canPlay(self : Card)(implicit g : Game) = targets.any
        override def onPlay(self : Card)(implicit g : Game) = $(ChooseCardEffect(Runner, targets, RejigPurpose(self), "Rejig: choose a program or piece of hardware to add to the grip", false))
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(c => g.push(ReturnToHandEffect(c), RejigInstallAction(p.card, c.data.cost.|(0))))
            NextAction
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case RejigInstallAction(self, d) =>
                val l = g.hand(Runner).%(c => (c.data.isProgram || c.data.isHardware) && g.canAffordInstall(c, d))
                if (l.any)
                    g.push(ChooseCardEffect(Runner, l, InstallPayingPurpose(d, None, false), "Rejig: choose a card to install paying " + d + " credits less", false))
                NextAction
        }
    }

    object Whistleblower extends NC("26030") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(_) if !g.state(self).usedRun.has("wb") =>
                val codes = g.decks.get(Corp).|($).%(c => Cards.get(c).exists(_.isAgenda)).distinct
                codes.any.$(OptionalEffect(Runner, "Whistleblower: trash it to name an agenda?", $(ChooseCodeEffect(Runner, codes, "Whistleblower: name an agenda", WhistleblowerPurpose(self))), $))
            case RunEnds(_, _, _) if g.state(self).usedRun.has("wb") => $(TrashEffect(self, Runner, false))
            case _ => $
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach { c =>
                g.state(p.card).memo = c.code
                g.state(p.card).usedRun :+= "wb"
            }
            NextAction
        }
        override def accessAbility(self : Card, target : Card)(implicit g : Game) =
            (g.state(self).usedRun.has("wb") && target.code == g.state(self).memo && target.data.isAgenda && g.run.exists(!_.cannotStealOrTrash)).?(Ability("steal it ignoring all costs (Whistleblower)", NoCost, $(WhistleblowerStealAction(self, target))))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case WhistleblowerStealAction(self, c) =>
                if (g.location.contains(c) && !g.zone(c).is[ScoredZone])
                    g.stealAgenda(c)
                NextAction
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Corp
    // ------------------------------------------------------------------------------------------------------------

    object MirrorMorph extends NC("26031") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case ActionTaken(Corp, k) => $(MirrorMorphAction(self, k))
            case PlayResolved(Corp, c) => $(MirrorMorphAction(self, "play"))
            case TurnBegins(_) => $(MirrorMorphResetAction(self))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case MirrorMorphAction(self, k) =>
                if (g.active.isCorp) {
                    g.mmActions :+= k
                    if (g.mmActions.num == 3 && g.mmActions.distinct.num == 3)
                        g.push(ChooseOneEffect(Corp, "MirrorMorph: three different actions", $(Alt("Gain 1 credit", $(gain(Corp, 1))), Alt("Gain [click]", $(GainClicksEffect(Corp, 1))))))
                }
                NextAction
            case MirrorMorphResetAction(self) =>
                g.mmActions = $
                NextAction
        }
    }

    object ArchitectDeploymentTest extends NC("26032") {
        override def onScore(self : Card)(implicit g : Game) = {
            val l = g.deck(Corp).take(5)
            l.any.$(LookAtEffect(Corp, l, "Architect Deployment Test: the top cards of R&D"), ChooseCardEffect(Corp, l.%(corpInstallable), InstallPayingPurpose(99, None, true), "Architect Deployment Test: install and rez a card ignoring all costs?", true))
        }
    }

    object Calvin extends NC("26033") {
        override def abilities(self : Card)(implicit g : Game) = $(Ability("draw 2 cards", click(), $(draw(Corp, 2)), click = true, once = once("calvin"), ok = g.isRezzed(self), reason = "not rezzed"))
        override def onTrash(self : Card, by : Faction)(implicit g : Game) = by.isRunner.$(OptionalEffect(Corp, "Calvin B4L3Y: draw 2 cards?", $(draw(Corp, 2)), $))
    }

    object NanoetchingMatrix extends NC("26034") {
        override def abilities(self : Card)(implicit g : Game) = $(Ability("gain 2 credits", click(), $(gain(Corp, 2)), click = true, once = once("nano"), ok = g.isRezzed(self), reason = "not rezzed"))
        override def onTrash(self : Card, by : Faction)(implicit g : Game) = by.isRunner.$(OptionalEffect(Corp, "Nanoetching Matrix: gain 2 credits?", $(gain(Corp, 2)), $))
    }

    object Hagen extends NC("26035") {
        override def strength(self : Card)(implicit g : Game) = -g.activeCards(Runner).%(_.data.isIcebreaker).num
        override def subroutines(self : Card)(implicit g : Game) = $(
            Sub("Trash 1 installed program that is not a decoder, fracter or killer", ChooseCardEffect(Corp, g.runnerInstalled.%(c => c.data.isProgram && !g.has(c, "Decoder") && !g.has(c, "Fracter") && !g.has(c, "Killer")), TrashPurpose(Corp), "Hagen: choose a program to trash", false)),
            etrSub)
    }

    object FullyOperational extends NC("26036") {
        override def onPlay(self : Card)(implicit g : Game) = $(FullyOperationalAction(self, 1 + g.remotes.%(r => g.root(r).any && g.ice(r).any).num))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case FullyOperationalAction(self, n) =>
                if (n > 0)
                    g.push(ChooseOneEffect(Corp, "Fully Operational: choose one (" + n + " left)", $(Alt("Gain 2 credits", $(gain(Corp, 2), FullyOperationalAction(self, n - 1))), Alt("Draw 2 cards", $(draw(Corp, 2), FullyOperationalAction(self, n - 1))))))
                NextAction
        }
    }

    object RedLevelClearance extends NC("26037") {
        override def onPlay(self : Card)(implicit g : Game) = $(RedLevelAction(self, $))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case RedLevelAction(self, done) =>
                if (done.num < 2) {
                    val next = (done.num < 1).$(RedLevelAction(self, done :+ "x"))
                    val inst = g.hand(Corp).%(c => corpInstallable(c) && !c.data.isAgenda)
                    val l = $(
                        ("draw", Alt("Draw 2 cards", draw(Corp, 2) +: next./(x => RedLevelAction(self, done :+ "draw")))),
                        ("gain", Alt("Gain 2 credits", gain(Corp, 2) +: next./(x => RedLevelAction(self, done :+ "gain")))),
                        ("install", Alt("Install 1 non-agenda card from HQ", ChooseCardEffect(Corp, inst, InstallChooseServerPurpose(false), "Red Level Clearance: choose a card to install", false) +: next./(x => RedLevelAction(self, done :+ "install")), inst.any)),
                        ("click", Alt("Gain [click]", GainClicksEffect(Corp, 1) +: next./(x => RedLevelAction(self, done :+ "click")))))
                    g.push(ChooseOneEffect(Corp, "Red Level Clearance: choose one (" + (2 - done.num) + " left)", l.%!((k, alt) => done.has(k))./((k, alt) => alt)))
                }
                NextAction
        }
    }

    object ColdSiteServer extends NC("26038") {
        override def abilities(self : Card)(implicit g : Game) = $(Ability("place 1 power counter", click(), $(AddCounterEffect(self, "power", 1)), click = true, ok = g.isRezzed(self), reason = "not rezzed"))
        override def runCost(self : Card, server : Server)(implicit g : Game) = (g.isRezzed(self) && thisServer(self, server) && g.counter(self, "power") > 0).??($[Cost](ClickCost(g.counter(self, "power")), CreditCost(g.counter(self, "power"))))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) if g.counter(self, "power") > 0 => $(RemoveCountersEffect(self, "power"))
            case _ => $
        }
    }

    object HyoubuInstitute extends NC("26039") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Revealed(Corp, l) if l.any && g.reg("revealed-Corp") == 1 => $(gain(Corp, 1))
            case _ => $
        }
        override def abilities(self : Card)(implicit g : Game) = $(
            Ability("reveal the top card of the stack", click(), g.deck(Runner).starting.$./(c => RevealCardsEffect(Corp, $(c))), click = true, ok = g.deck(Runner).any, reason = "empty stack"),
            Ability("reveal a random card from the grip", click(), $(RandomPickEffect(Corp, g.hand(Runner), 1, HyoubuRevealPurpose(self))), click = true, ok = g.hand(Runner).any, reason = "empty grip"))
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            g.push(RevealCardsEffect(Corp, cards))
            NextAction
        }
    }

    object ProjectYagiUda extends NC("26040") {
        override def onScore(self : Card)(implicit g : Game) = $(AddCounterEffect(self, "agenda", (g.advancement(self) - 3).atLeast(0)))
        override def abilities(self : Card)(implicit g : Game) = {
            val l = g.run./(r => g.ice(r.server) ++ g.root(r.server)).|($)
            $(Ability("swap a card from HQ with a card in or protecting the attacked server", $(CounterCost("agenda", 1)), $(ChooseCardEffect(Corp, l, YagiUdaPurpose(self), "Project Yagi-Uda: choose an installed card", false)), ok = g.zone(self) == ScoredZone(Corp) && g.run.exists(!_.ended) && l.any && g.hand(Corp).any, reason = "only during a run"))
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case YagiUdaPurpose(self) =>
                cards.single.foreach { c =>
                    val l = g.hand(Corp).%(h => c.data.isIce.?(h.data.isIce).|(h.data.isAgenda || h.data.isAsset || h.data.isUpgrade))
                    if (l.any)
                        g.push(ChooseCardEffect(Corp, l, YagiUdaSwapPurpose(self, c), "Project Yagi-Uda: choose a card in HQ to swap", false))
                }
                NextAction
            case YagiUdaSwapPurpose(self, c) =>
                cards.single.foreach(h => g.push(SwapInstalledWithHandEffect(c, h), JackOutOfferEffect))
                NextAction
        }
    }

    object Sting extends NC("26041") {
        override def onScore(self : Card)(implicit g : Game) = $(net(1 + g.scored(Runner).%(_.title == "Sting!").num))
        override def onSteal(self : Card)(implicit g : Game) = $(net(1 + g.scored(Corp).%(_.title == "Sting!").num))
    }

    object PublicHealthPortal extends NC("26042") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) if g.isRezzed(self) => g.deck(Corp).starting.$./(c => RevealCardsEffect(Corp, $(c))) :+ gain(Corp, 2)
            case _ => $
        }
    }

    object StorgoticResonator extends NC("26043") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Trashed(c, Corp, _) if g.isRezzed(self) && g.identity.get(Runner).exists(_.data.faction == c.data.faction) && !g.usedThisTurn(self, "storgotic") => $(MarkUsedEffect(self, "storgotic"), AddCounterEffect(self, "power", 1))
            case _ => $
        }
        override def abilities(self : Card)(implicit g : Game) = $(Ability("do 1 net damage", $(ClickCost(1), CounterCost("power", 1)), $(net(1)), click = true, ok = g.isRezzed(self), reason = "not rezzed"))
    }

    object Saisentan extends NC("26044") {
        override def onEncounter(self : Card)(implicit g : Game) = $(ChooseOneEffect(Corp, "Saisentan: choose a card type", $("Event", "Hardware", "Program", "Resource")./(k => Alt(k, $(SetMemoEffect(self, k))))))
        override def subroutines(self : Card)(implicit g : Game) = $(Sub("Do 1 net damage", SaisentanSubAction(self)), Sub("Do 1 net damage", SaisentanSubAction(self)), Sub("Do 1 net damage", SaisentanSubAction(self)))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case EncounterEnds(c) if c == self => $(SetMemoEffect(self, ""))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case SaisentanSubAction(self) =>
                g.lastDamaged = $
                g.push(net(1), SaisentanCheckAction(self))
                NextAction
            case SaisentanCheckAction(self) =>
                if (g.lastDamaged.exists(c => c.data.kind == g.state(self).memo)) {
                    g.log(self, "deals 1 additional net damage")
                    g.push(net(1))
                }
                NextAction
        }
    }

    object CompleteImage extends NC("26045") {
        def codes(implicit g : Game) = g.decks.get(Runner).|($).%(c => Cards.get(c).exists(!_.isIdentity)).distinct
        override def canPlay(self : Card)(implicit g : Game) = g.agendaPoints(Runner) >= 3 && g.successfulRunsLastTurn.any
        override def onPlay(self : Card)(implicit g : Game) = $(ChooseCodeEffect(Corp, codes, "Complete Image: name a Runner card", CompleteImagePurpose(self)))
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach { c =>
                g.lastDamaged = $
                g.push(net(1), CompleteImageCheckAction(p.card, c.code))
            }
            NextAction
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case CompleteImageCheckAction(self, code) =>
                if (!g.isOver && g.lastDamaged.exists(_.code == code))
                    g.push(ChooseCodeEffect(Corp, codes, "Complete Image: name a Runner card", CompleteImagePurpose(self)))
                NextAction
        }
    }

    object LetheiaNisei extends NC("26046") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case ApproachServer(s) if thisServer(self, s) && g.isRezzed(self) && !g.state(self).usedRun.has("letheia") =>
                $(MarkUsedRunEffect(self, "letheia"), PsiEffect($, $(OptionalEffect(Corp, "Letheia Nisei: trash it to make the Runner approach the outermost piece of ice?", $(TrashEffect(self, Corp, false), MoveRunnerEffect(g.ice(s).num, true)), $))))
            case _ => $
        }
    }

    object RemasteredEdition extends NC("26047") {
        override def onScore(self : Card)(implicit g : Game) = $(AddCounterEffect(self, "agenda", 1))
        override def abilities(self : Card)(implicit g : Game) = $(Ability("place 1 advancement counter on an installed card", $(CounterCost("agenda", 1)), $(ChooseCardEffect(Corp, g.installedCards(Corp), AdvancePurpose(1), "Remastered Edition: choose an installed card", false)), ok = g.zone(self) == ScoredZone(Corp) && g.installedCards(Corp).any, reason = "nothing installed"))
    }

    object DailyQuest extends NC("26048") {
        override def canRezIn(self : Card, ctx : String)(implicit g : Game) = |(ctx == "action")
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(s) if thisServer(self, s) && g.isRezzed(self) => $(gain(Runner, 2))
            case TurnBegins(Corp) if g.isRezzed(self) && !g.serverOf(self).exists(s => g.successfulRunsLastTurn.has(s)) => $(gain(Corp, 3))
            case _ => $
        }
    }

    object TieredSubscription extends NC("26049") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case RunInitiated(_) if g.reg("runs") == 1 && g.isRezzed(self) => $(gain(Corp, 1))
            case _ => $
        }
    }

    object Congratulations extends NC("26050") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case PassIce(c) if c == self && g.isRezzed(self) => $(gain(Corp, 1))
            case _ => $
        }
        override def subroutines(self : Card)(implicit g : Game) = $(Sub("Gain 2[c]. The Runner gains 1[c]", CopyAbilityEffect($(gain(Corp, 2), gain(Runner, 1)))))
    }

    object LootBox extends NC("26051") {
        override def subroutines(self : Card)(implicit g : Game) = $(
            Sub("End the run unless the Runner pays 2[c]", ChooseOneEffect(Runner, "Loot Box: choose one", $(Alt("Pay 2 credits", $(LoseCreditsEffect(Runner, 2)), g.credits(Runner) >= 2), Alt("End the run", $(EndRunEffect))))),
            Sub("Reveal the top 3 cards of the stack, add 1 to the grip and gain its cost, trash this ice", LootBoxSubAction(self)))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case LootBoxSubAction(self) =>
                val l = g.deck(Runner).take(3)
                if (l.any)
                    g.push(RevealCardsEffect(Runner, l), ChooseCardEffect(Corp, l, LootBoxPurpose(self), "Loot Box: choose a card to add to the grip", false))
                else
                    g.push(TrashEffect(self, Corp, false))
                NextAction
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach { c =>
                g.moveTo(c, HandZone(Runner))
                g.log(c, "is added to the grip")
                g.push(gain(Corp, c.data.cost.|(0)), ShuffleDeckEffect(Runner), TrashEffect(p.card, Corp, false))
            }
            NextAction
        }
    }

    object FocusGroup extends NC("26052") {
        override def canPlay(self : Card)(implicit g : Game) = g.successfulRunsLastTurn.any
        override def onPlay(self : Card)(implicit g : Game) = $(ChooseOneEffect(Corp, "Focus Group: choose a card type", $("Event", "Hardware", "Program", "Resource")./(k => Alt(k, $(FocusGroupAction(self, k))))))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case FocusGroupAction(self, k) =>
                val n = g.hand(Runner).%(_.data.kind == k).num
                if (n > 0 && g.installedCards(Corp).any)
                    g.push(ChooseOneEffect(Corp, "Focus Group: " + n + " " + k.toLowerCase + " cards revealed, pay X credits to place X advancement counters", 0.to(n.atMost(g.credits(Corp))).$./(x => Alt("Pay " + x, (x > 0).$(LoseCreditsEffect(Corp, x), ChooseCardEffect(Corp, g.installedCards(Corp), AdvancePurpose(x), "Focus Group: choose a card", false))))))
                g.push(RevealCardsEffect(Runner, g.hand(Runner)))
                NextAction
        }
    }

    object GameOverOp extends NC("26053") {
        override def canPlay(self : Card)(implicit g : Game) = g.regLast("stolen-points") > 0
        override def onPlay(self : Card)(implicit g : Game) = $(ChooseOneEffect(Corp, "Game Over: choose a card type", $("Hardware", "Program", "Resource")./(k => Alt(k, $(GameOverOpAction(self, k))))))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case GameOverOpAction(self, k) =>
                val targets = g.runnerInstalledOf(k).%(c => !c.data.isIcebreaker)
                g.push(BadPubEffect(1))
                if (targets.any)
                    g.push(ChooseCardsEffect(Runner, targets, 0, targets.num.atMost(g.credits(Runner) / 3), GameOverPurpose(self, targets), "Game Over: choose cards to prevent from being trashed (3 credits each)"))
                NextAction
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case GameOverPurpose(self, targets) =>
                if (cards.any)
                    g.push(LoseCreditsEffect(Runner, 3 * cards.num))
                g.push(TrashCardsEffect(targets.%!(cards.has), Corp))
                NextAction
        }
    }

    object IncreasedDropRates extends NC("26054") {
        override def onAccess(self : Card)(implicit g : Game) = $(ChooseOneEffect(Runner, "Increased Drop Rates: choose one", $(Alt("Take 1 tag", $(tag(1))), Alt("The Corp removes 1 bad publicity", $(BadPubEffect(-1))))))
    }

    object DivestedTrust extends NC("26055") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case AgendaStolen(c) if c != self && g.zone(self).is[ScoredZone] && !g.isOver => $(OptionalEffect(Corp, "Divested Trust: forfeit it to add the stolen agenda to HQ and gain 5 credits?", $(DivestedAction(self, c)), $))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case DivestedAction(self, c) =>
                g.moveTo(self, DiscardZone(Corp))
                g.state(self).seen = true
                g.log(Corp, "forfeits", self)
                if (g.zone(c) == ScoredZone(Runner)) {
                    g.moveTo(c, HandZone(Corp))
                    g.log(c, "is added to HQ")
                }
                g.push(gain(Corp, 5), CheckWinAction)
                NextAction
        }
    }

    object SDSDroneDeployment extends NC("26056") {
        override def accessCostPaidByRunner(self : Card)(implicit g : Game) = $(TrashInstalledCost("Program"))
        override def onScore(self : Card)(implicit g : Game) = g.runnerInstalledOf("Program").any.$(ChooseCardEffect(Corp, g.runnerInstalledOf("Program"), TrashPurpose(Corp), "SDS Drone Deployment: choose a program to trash", false))
    }

    object RoughneckRepairSquad extends NC("26057") {
        override def abilities(self : Card)(implicit g : Game) = $(Ability("gain 6 credits and remove 1 bad publicity", $(ClickCost(3)), $(gain(Corp, 6)) ++ (g.badPublicity > 0).$(OptionalEffect(Corp, "Roughneck Repair Squad: remove 1 bad publicity?", $(BadPubEffect(-1)), $)), click = true, ok = g.isRezzed(self), reason = "not rezzed"))
    }

    object Afshar extends NC("26058") {
        override def breakAllowed(self : Card, breaker : Card, ice : Card)(implicit g : Game) = ice != self || !g.serverOf(self).has(HQ) || g.state(self).brokenSubs.none
        override def subroutines(self : Card)(implicit g : Game) = $(loseSub(2), etrSub)
    }

    object Sandstone extends NC("26059") {
        override def onEncounter(self : Card)(implicit g : Game) = $(AddCounterEffect(self, "virus", 1))
        override def strength(self : Card)(implicit g : Game) = -g.counter(self, "virus")
        override def subroutines(self : Card)(implicit g : Game) = $(etrSub)
    }

    object Trebuchet extends NC("26060") {
        override def onRez(self : Card)(implicit g : Game) = $(BadPubEffect(1))
        override def subroutines(self : Card)(implicit g : Game) = $(trashSub("", "Trash 1 installed Runner card"), Sub("Trace[6]: the Runner cannot steal or trash Corp cards for the remainder of the run", TraceEffect(6, $(CannotStealOrTrashEffect), $)))
    }

    object SecureAndProtect extends NC("26061") {
        override def canPlay(self : Card)(implicit g : Game) = g.clicks(Corp) >= 2
        override def onPlay(self : Card)(implicit g : Game) = {
            val l = g.deck(Corp).%(_.data.isIce).sortBy(_.title)
            $(LoseClicksEffect(Corp, 1)) ++ l.any.$(ChooseCardEffect(Corp, l, SecureAndProtectPurpose(self), "Secure and Protect: choose a piece of ice", false)) :+ ShuffleDeckEffect(Corp)
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(c => g.push(RevealCardsEffect(Corp, $(c)), ChooseServerPayingEffect(c, 3, Server.centrals, false, false)))
            NextAction
        }
    }

    object ReducedService extends NC("26062") {
        override def onRez(self : Card)(implicit g : Game) = $(ChooseOneEffect(Corp, "Reduced Service: pay credits to place power counters", 0.to(4.atMost(g.credits(Corp))).$./(n => Alt("Pay " + n, $(ReducedServiceAction(self, n))))))
        override def runCost(self : Card, server : Server)(implicit g : Game) = (g.isRezzed(self) && thisServer(self, server) && g.counter(self, "power") > 0).??($[Cost](CreditCost(2 * g.counter(self, "power"))))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(s) if s.isCentral && g.counter(self, "power") > 0 && g.isRezzed(self) => $(AddCounterEffect(self, "power", -1))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case ReducedServiceAction(self, n) =>
                if (n > 0)
                    g.push(LoseCreditsEffect(Corp, n), AddCounterEffect(self, "power", n))
                NextAction
        }
    }

    object VulnerabilityAudit extends NC("26063") {
        override def canScore(self : Card)(implicit g : Game) = g.state(self).installedTurn != g.turn
    }

    object CSRCampaign extends NC("26064") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) if g.isRezzed(self) && g.deck(Corp).any => $(OptionalEffect(Corp, "CSR Campaign: draw 1 card?", $(draw(Corp, 1)), $))
            case _ => $
        }
    }

    object Rime extends NC("26065") {
        override def canRezIn(self : Card, ctx : String)(implicit g : Game) = g.run.exists(r => !r.ended && thisServer(self, r.server)).?(|(true)).|(None)
        override def iceStrengthMod(self : Card, ice : Card)(implicit g : Game) = (g.isRezzed(self) && ice != self && g.serverOf(ice).exists(s => thisServer(self, s))).?(1).|(0)
        override def subroutines(self : Card)(implicit g : Game) = $(loseSub(1))
    }

    val all : $[CardImpl] = $(
        Isolation, Demolisher, Chisel, Stargate, Utae, ClimacticShowdown, FencerFueno, TheNihilist, TricksterTaka, Az, AlwaysHaveABackupPlan,
        BlueberryDiesel, FlipSwitch, LuckyCharm, Masterwork, Bukhgalter, Baklan, TheClassAct, Lat, InTheGroove, Khusyuk, SpecWork,
        Supercorridor, Gauss, Pelangi, Rezeki, TheArtist, DirectAccess, Rejig, Whistleblower,
        MirrorMorph, ArchitectDeploymentTest, Calvin, NanoetchingMatrix, Hagen, FullyOperational, RedLevelClearance, ColdSiteServer,
        HyoubuInstitute, ProjectYagiUda, Sting, PublicHealthPortal, StorgoticResonator, Saisentan, CompleteImage, LetheiaNisei,
        RemasteredEdition, DailyQuest, TieredSubscription, Congratulations, LootBox, FocusGroup, GameOverOp, IncreasedDropRates,
        DivestedTrust, SDSDroneDeployment, RoughneckRepairSquad, Afshar, Sandstone, Trebuchet, SecureAndProtect, ReducedService,
        VulnerabilityAudit, CSRCampaign, Rime,
    )
}
