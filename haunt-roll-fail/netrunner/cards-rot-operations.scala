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
// Rotated (FFG era) Corp operations, ported from jinteki.net src/clj/game/cards/operations.clj
// (generic mechanics: rules-ext-rot-operations.scala; status: STATUS-rot-operations.md)
// ---------------------------------------------------------------------------------------------------------------------

object RotOperationCards {
    val all : $[CardImpl] = RotOperationCardsA.all ++ RotOperationCardsB.all

    // every Runner card title (latest printing), for "name a card"
    lazy val runnerNames : $[String] = Cards.all.%(c => c.isRunner && !c.isIdentity)./(_.title).distinct./(t => Cards.byTitle(t).code)
}

// card specific actions and purposes
case class PGOAction(card : Card) extends CardAction
case class SunsetArrangeAction(card : Card, server : Server, chosen : $[Card]) extends CardAction
case class SunsetPickAction(self : Faction, card : Card, server : Server, ice : Card, chosen : $[Card]) extends BaseAction("Sunset: choose the next piece of ice, from the innermost position outwards")(g => g.cardElem(ice)) with SelfPerform {
    def perform(soft : Void)(implicit g : Game) = {
        g.push(SunsetArrangeAction(card, server, chosen :+ ice))
        NextAction
    }
}
case class CommercializationPurpose(card : Card) extends CardPurpose
case class FoxfireAction(card : Card) extends CardAction
case class MidseasonAction(card : Card) extends CardAction
case class IoPAction(card : Card) extends CardAction
case class HellionAlphaAction(card : Card) extends CardAction
case class SanSanPurpose(card : Card) extends CardPurpose
case class ADStartAction(card : Card) extends CardAction
case class ADStepAction(card : Card, remaining : $[Card]) extends CardAction
case class ADTrashAction(card : Card, remaining : $[Card]) extends CardAction
case class ADPurpose(card : Card, remaining : $[Card]) extends CardPurpose
case class PowerShutdownAction(card : Card, n : Int) extends CardAction
case class RestoringFacePurpose(card : Card) extends CardPurpose
case class ReclamationPurpose(card : Card) extends CardPurpose
case class CorporateShuffleAction(card : Card) extends CardAction
case class WitnessTamperingAction(card : Card) extends CardAction
case class MushinPurpose(card : Card) extends CardPurpose
case class MushinFlagAction(card : Card, target : Card) extends CardAction
case class MutatePurpose(card : Card) extends CardPurpose
case class MutateInstallAction(card : Card, ice : Card, server : Server, index : Int, old : Card) extends CardAction
case class TargetedMarketingPurpose(card : Card) extends CardPurpose
case class ReusePurpose(card : Card) extends CardPurpose
case class SnatchAction(card : Card) extends CardAction
case class SnatchPurpose(card : Card) extends CardPurpose
case class HousekeepingAction(card : Card) extends CardAction
case class RecruitingStepAction(card : Card, left : Int, codes : $[String]) extends CardAction
case class RecruitingPurpose(card : Card, left : Int, codes : $[String]) extends CardPurpose
case class OfferAction(card : Card, server : Server) extends CardAction
case class CastingCallPurpose(card : Card) extends CardPurpose
case class CastingCallHostAction(card : Card, agenda : Card) extends CardAction
case class BackChannelsPurpose(card : Card) extends CardPurpose
case class NewsCycleAction(card : Card) extends CardAction
case class NewsCyclePurpose(card : Card) extends CardPurpose
case class MediaBlitzPurpose(card : Card) extends CardPurpose
case class AllSeeingAction(card : Card) extends CardAction
case class HeritageAction(card : Card) extends CardAction
case class DedicationPurpose(card : Card) extends CardPurpose
case class ProductRecallPurpose(card : Card) extends CardPurpose
case class SalemPurpose(card : Card) extends CardPurpose
case class LocalizedPurpose(card : Card) extends CardPurpose
case class ExchangePurpose(card : Card) extends CardPurpose
case class ExchangeSwapPurpose(card : Card, stolen : Card) extends CardPurpose
case class ConsultingAction(card : Card) extends CardAction
case class ConsultingPurpose(card : Card) extends CardPurpose
case class ConsultingShuffleAction(card : Card) extends CardAction
case class VoterAction(card : Card) extends CardAction
case class ElectionDayAction(card : Card) extends CardAction
case class SubcontractStepAction(card : Card, i : Int) extends CardAction
case class SubcontractPurpose(card : Card, i : Int) extends CardPurpose
case class EnforcingLoyaltyAction(card : Card) extends CardAction
case class HatchetJobAction(card : Card) extends CardAction
case class SpecialReportPurpose(card : Card) extends CardPurpose
case class LiquidationPurpose(card : Card) extends CardPurpose
case class FinancialCollapseAction(card : Card) extends CardAction
case class ArkLockdownPurpose(card : Card) extends CardPurpose
case class HellionBetaAction(card : Card) extends CardAction
case class DoorToDoorAction(card : Card) extends CardAction


object RotOperationCardsA {

    object Precognition extends RotOp("01073") {
        override def effects(self : Card)(implicit g : Game) = $(ArrangeTopEffect(Corp, Corp, 5))
    }

    object AggressiveNegotiation extends RotOp("01097") {
        override def playReq(self : Card)(implicit g : Game) = g.reg("scored") > 0
        override def effects(self : Card)(implicit g : Game) = {
            val l = g.deck(Corp).sortBy(_.title)
            l.any.$(ChooseCardEffect(Corp, l, AddToHandPurpose(Corp, false), "Aggressive Negotiation: choose a card in R&D to add to HQ", false)) :+ ShuffleDeckEffect(Corp)
        }
    }

    object BigBrother extends RotOp("02035") {
        override def playReq(self : Card)(implicit g : Game) = g.tagged
        override def effects(self : Card)(implicit g : Game) = $(tag(2))
    }

    object PowerGridOverload extends RotOp("02037") {
        override def playReq(self : Card)(implicit g : Game) = g.successfulRunsLastTurn.any
        override def effects(self : Card)(implicit g : Game) = $(MarginTraceEffect(2, $(PGOAction(self)), $))
        override def performOp(a : CardAction)(implicit g : Game) = a match {
            case PGOAction(self) =>
                val l = runnerOf("Hardware").%(c => installedCost(c) <= g.rotTraceMargin)
                g.pushAll(trashOne(l, "Power Grid Overload: choose a piece of hardware to trash"))
                NextAction
        }
    }

    object Freelancer extends RotOp("02040") {
        override def playReq(self : Card)(implicit g : Game) = g.tagged
        override def effects(self : Card)(implicit g : Game) = {
            val l = runnerOf("Resource")
            l.any.$(ChooseCardsEffect(Corp, l, 0, 2, TrashPurpose(Corp), "Freelancer: choose up to 2 resources to trash"))
        }
    }

    object Sunset extends RotOp("02054") {
        override def effects(self : Card)(implicit g : Game) = {
            val l = g.servers.%(s => g.ice(s).num >= 2)
            l.any.$(ChooseOneEffect(Corp, "Sunset: choose a server", l./(s => Alt(s.name, $(SunsetArrangeAction(self, s, $))))))
        }
        override def performOp(a : CardAction)(implicit g : Game) = a match {
            case SunsetArrangeAction(self, s, chosen) =>
                val rest = g.ice(s).%!(chosen.has)
                if (!g.servers.has(s))
                    NextAction
                else if (rest.num <= 1) {
                    val order = chosen.%(g.ice(s).has) ++ rest
                    if (order.num == g.ice(s).num)
                        g.zones += IceZone(s) -> order
                    g.log(Corp, "rearranges the ice protecting", s)
                    NextAction
                }
                else
                    Ask(Corp).add(rest./(c => SunsetPickAction(Corp, self, s, c, chosen)))
        }
    }

    object Commercialization extends RotOp("02058") {
        override def effects(self : Card)(implicit g : Game) = {
            val l = g.allIce
            l.any.$(ChooseCardEffect(Corp, l, CommercializationPurpose(self), "Commercialization: choose a piece of ice", false))
        }
        override def chosenOp(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(c => g.push(gain(Corp, g.advancement(c))))
            NextAction
        }
    }

    object Rework extends RotOp("02093") {
        override def effects(self : Card)(implicit g : Game) =
            g.hand(Corp).any.$(ChooseCardEffect(Corp, g.hand(Corp), ShuffleIntoDeckPurpose(Corp), "Rework: choose a card in HQ to shuffle into R&D", false))
    }

    object Foxfire extends RotOp("02100") {
        override def effects(self : Card)(implicit g : Game) = $(TraceEffect(7, $(FoxfireAction(self)), $))
        override def performOp(a : CardAction)(implicit g : Game) = a match {
            case FoxfireAction(self) =>
                val l = runnerCards.%(c => (c.data.isResource && g.has(c, "Virtual")) || g.has(c, "Link"))
                g.pushAll(trashOne(l, "Foxfire: choose a virtual resource or link to trash"))
                NextAction
        }
    }

    object MidseasonReplacements extends RotOp("02116") {
        override def playReq(self : Card)(implicit g : Game) = g.regLast("stolen") > 0
        override def effects(self : Card)(implicit g : Game) = $(MarginTraceEffect(6, $(MidseasonAction(self)), $))
        override def performOp(a : CardAction)(implicit g : Game) = a match {
            case MidseasonAction(self) =>
                g.push(tag(g.rotTraceMargin))
                NextAction
        }
    }

    object BioroidEfficiencyResearch extends RotCondition("03013") {
        override def candidates(self : Card)(implicit g : Game) = g.allIce.%(c => !g.isRezzed(c) && g.has(c, "Bioroid"))
        override def hostPrompt = "Bioroid Efficiency Research: choose a piece of bioroid ice to rez"
        override def hostEffects(self : Card, h : Card)(implicit g : Game) = $(RezEffect(h, true))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SubsBroken(ice, _, _) if hosting(self, ice) && g.unbrokenSubs(ice).none => $(DerezEffect(ice), TrashLingeringEffect(self))
            case _ => $
        }
    }

    object SuccessfulDemonstration extends RotOp("03014") {
        override def playReq(self : Card)(implicit g : Game) = g.regLast("unsuccessful-run") > 0
        override def effects(self : Card)(implicit g : Game) = $(gain(Corp, 7))
    }

    object InvasionOfPrivacy extends RotOp("04016") {
        override def effects(self : Card)(implicit g : Game) = $(MarginTraceEffect(2, $(IoPAction(self)), $(BadPubEffect(1))))
        override def performOp(a : CardAction)(implicit g : Game) = a match {
            case IoPAction(self) =>
                val grip = g.hand(Runner)
                val l = grip.%(c => c.data.isResource || c.data.isEvent)
                g.push(RevealCardsEffect(Runner, grip), ChooseCardsEffect(Corp, l, 0, g.rotTraceMargin.atMost(l.num), TrashPurpose(Corp), "Invasion of Privacy: choose up to " + g.rotTraceMargin + " resources and events to trash"))
                NextAction
        }
    }

    object CyberdexTrial extends RotOp("04019") {
        override def effects(self : Card)(implicit g : Game) = $(PurgeEffect)
    }

    object HellionAlphaTest extends RotOp("04031") {
        override def playReq(self : Card)(implicit g : Game) = g.regLast("install-resource") > 0
        override def effects(self : Card)(implicit g : Game) = $(TraceEffect(2, $(HellionAlphaAction(self)), $(BadPubEffect(1))))
        override def performOp(a : CardAction)(implicit g : Game) = a match {
            case HellionAlphaAction(self) =>
                val l = runnerOf("Resource")
                if (l.any)
                    g.push(ChooseCardEffect(Corp, l, TopOfDeckPurpose(Runner), "Hellion Alpha Test: choose a resource to add to the top of the stack", false))
                NextAction
        }
    }

    object ShipmentFromSanSan extends RotOp("04034") {
        override def effects(self : Card)(implicit g : Game) = {
            val l = g.advanceableCards
            l.any.$(ChooseCardEffect(Corp, l, SanSanPurpose(self), "Shipment from SanSan: choose a card to place advancement counters on", true))
        }
        override def chosenOp(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(c => g.push(ChooseOneEffect(Corp, "Shipment from SanSan: place how many advancement counters", $(Alt("1", $(PlaceAdvancementEffect(c, 1))), Alt("2", $(PlaceAdvancementEffect(c, 2)))))))
            NextAction
        }
    }

    object Restructure extends RotOp("04040") {
        override def effects(self : Card)(implicit g : Game) = $(gain(Corp, 15))
    }

    object AcceleratedDiagnostics extends RotOp("04052") {
        override def effects(self : Card)(implicit g : Game) = $(ADStartAction(self))
        def playable(c : Card)(implicit g : Game) = c.data.isOperation && g.zone(c) == DeckZone(Corp) && g.availableCredits(Corp, None, "play-operation") >= g.playCost(c) && (g.has(c, "Double") || g.has(c, "Triple") || g.impl(c).canPlay(c))
        override def performOp(a : CardAction)(implicit g : Game) = a match {
            case ADStartAction(self) =>
                val top = g.deck(Corp).take(3)
                if (top.any)
                    g.push(LookAtEffect(Corp, top, "Accelerated Diagnostics: the top cards of R&D (top first)"), ADStepAction(self, top))
                NextAction
            case ADStepAction(self, rem) =>
                val l = rem.%(c => g.zone(c) == DeckZone(Corp))
                val ops = l.%(playable)
                if (ops.any)
                    g.push(ChooseCardEffect(Corp, ops, ADPurpose(self, l), "Accelerated Diagnostics: play an operation (decline to trash the rest)", true), ADTrashAction(self, l))
                else
                    g.push(ADTrashAction(self, l))
                NextAction
            case ADTrashAction(self, rem) =>
                rem.%(c => g.zone(c) == DeckZone(Corp)).foreach(c => g.trashCard(c, Corp, false))
                NextAction
        }
        override def chosenOp(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case ADPurpose(self, l) =>
                cards.single.foreach(c => g.push(RotPlayEffect(c, true), ADStepAction(self, l.but(c))))
                NextAction
        }
    }

    object PowerShutdown extends RotOp("04058") {
        override def playReq(self : Card)(implicit g : Game) = g.regLast("runs") > 0
        override def effects(self : Card)(implicit g : Game) = $(ChooseNumberEffect(Corp, self, 0, g.deck(Corp).num, "ps", "Power Shutdown: trash how many cards from the top of R&D?"))
        override def performOp(a : CardAction)(implicit g : Game) = a match {
            case NumberPickedAction(self, n, "ps") =>
                g.push(TrashTopEffect(Corp, n, false), PowerShutdownAction(self, n))
                NextAction
            case PowerShutdownAction(self, n) =>
                val l = runnerCards.%(c => (c.data.isProgram || c.data.isHardware) && installedCost(c) <= n)
                if (l.any)
                    g.push(ChooseCardEffect(Runner, l, TrashPurpose(Runner), "Power Shutdown: choose a program or piece of hardware to trash", false))
                NextAction
        }
    }

    object Interns extends RotOp("04060") {
        override def effects(self : Card)(implicit g : Game) = $(installLoop(self, "both", 1, false, 0))
    }

    object RestoringFace extends RotOp("04094") {
        def cands(implicit g : Game) = g.installedCards(Corp).%(c => g.has(c, "Sysop") || g.has(c, "Executive") || g.has(c, "Clone"))
        override def playReq(self : Card)(implicit g : Game) = cands.any
        override def effects(self : Card)(implicit g : Game) = $(ChooseCardEffect(Corp, cands, RestoringFacePurpose(self), "Restoring Face: choose a sysop, executive or clone to trash", false))
        override def chosenOp(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach { c =>
                val n = 2.atMost(g.badPublicity)
                g.pushAll((!g.isRezzed(c)).$(RevealEffect(Corp, c)) ++ $(TrashEffect(c, Corp, false)) ++ (n > 0).$(BadPubEffect(-n)))
            }
            NextAction
        }
    }

    object ReclamationOrder extends RotOp("04111") {
        override def effects(self : Card)(implicit g : Game) = {
            val l = g.discard(Corp).%(_.title != title).distinctBy(_.title).sortBy(_.title)
            l.any.$(ChooseCardEffect(Corp, l, ReclamationPurpose(self), "Reclamation Order: name a card in Archives", false))
        }
        override def chosenOp(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach { c =>
                val copies = g.discard(Corp).%(_.title == c.title)
                g.pushAll($(RevealCardsEffect(Corp, copies), MessageEffect(Corp, "adds " + copies.num + " cop" + (copies.num != 1).?("ies").|("y") + " from Archives to HQ")) ++ copies./(AddToHandEffect(_)))
            }
            NextAction
        }
    }

    object CorporateShuffle extends RotOp("04113") {
        override def effects(self : Card)(implicit g : Game) = $(CorporateShuffleAction(self))
        override def performOp(a : CardAction)(implicit g : Game) = a match {
            case CorporateShuffleAction(self) =>
                val l = g.hand(Corp)
                l.foreach(c => g.moveTo(c, DeckZone(Corp)))
                g.log(Corp, "shuffles", l.num.cards, "from HQ into R&D")
                g.push(ShuffleDeckEffect(Corp), draw(Corp, 5))
                NextAction
        }
    }

    object WitnessTampering extends RotOp("04118") {
        override def effects(self : Card)(implicit g : Game) = $(WitnessTamperingAction(self))
        override def performOp(a : CardAction)(implicit g : Game) = a match {
            case WitnessTamperingAction(self) =>
                val n = 2.atMost(g.badPublicity)
                if (n > 0)
                    g.push(BadPubEffect(-n))
                NextAction
        }
    }

    object CerebralCast extends RotOp("05013") {
        override def playReq(self : Card)(implicit g : Game) = g.successfulRunsLastTurn.any
        override def effects(self : Card)(implicit g : Game) = $(PsiEffect($, $(ChooseOneEffect(Runner, "Cerebral Cast: choose one", $(Alt("Take 1 tag", $(tag(1))), Alt("Suffer 1 core damage", $(core(1))))))))
    }

    object MedicalResearchFundraiser extends RotOp("05014") {
        override def effects(self : Card)(implicit g : Game) = $(gain(Corp, 8), gain(Runner, 3))
    }

    object MushinNoShin extends RotOp("05015") {
        override def effects(self : Card)(implicit g : Game) = {
            val l = g.hand(Corp).%(c => c.data.isAsset || c.data.isAgenda || c.data.isUpgrade)
            l.any.$(ChooseCardEffect(Corp, l, MushinPurpose(self), "Mushin No Shin: choose a card to install in a new remote server", false))
        }
        override def chosenOp(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(c => g.push(RotInstallEffect(c, $, true, true, 0, 3, None), MushinFlagAction(p.card, c)))
            NextAction
        }
        override def performOp(a : CardAction)(implicit g : Game) = a match {
            case MushinFlagAction(self, c) =>
                if (g.isInstalled(c)) {
                    g.addFlag(Flag("cannot-score", |(c), None, "halfturn", g.halfTurn + 1, $))
                    g.addFlag(Flag("rot-no-rez", |(c), None, "halfturn", g.halfTurn + 1, $))
                    g.markUsed(c, "cannot-rez")
                    g.log("The card cannot be rezzed or scored until the Corp's next turn begins")
                }
                NextAction
        }
    }

    object DiversifiedPortfolio extends RotOp("05026") {
        override def effects(self : Card)(implicit g : Game) = $(gain(Corp, g.remotes.%(s => g.root(s).any).num))
    }

    object FastTrack extends RotOp("05027") {
        override def effects(self : Card)(implicit g : Game) = {
            val l = g.deck(Corp).%(_.data.isAgenda).sortBy(_.title)
            l.any.$(ChooseCardEffect(Corp, l, AddToHandPurpose(Corp, true), "Fast Track: choose an agenda to reveal and add to HQ", true)) :+ ShuffleDeckEffect(Corp)
        }
    }

    object Mutate extends RotOp("06004") {
        override def playReq(self : Card)(implicit g : Game) = g.rezzedIce.any
        override def additionalCosts(self : Card)(implicit g : Game) = $(ChooseCardEffect(Corp, g.rezzedIce, MutatePurpose(self), "Mutate: choose a rezzed piece of ice to trash", false))
        override def chosenOp(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach { old =>
                g.zone(old) match {
                    case IceZone(s) =>
                        val idx = g.ice(s).indexOf(old)
                        val (revealed, rest) = g.deck(Corp).span(x => !x.data.isIce)
                        val found = rest.starting
                        g.pushAll($(RevealCardsEffect(Corp, revealed ++ found.$)) ++ (found match {
                            case Some(ice) => $(MutateInstallAction(p.card, ice, s, idx, old))
                            case None => $(TrashEffect(old, Corp, false), ShuffleDeckEffect(Corp))
                        }))
                    case _ =>
                }
            }
            NextAction
        }
        override def performOp(a : CardAction)(implicit g : Game) = a match {
            case MutateInstallAction(self, ice, s, idx, old) =>
                if (g.servers.has(s) && g.zone(ice) == DeckZone(Corp)) {
                    g.moveTo(ice, IceZone(s))
                    val l = g.ice(s).but(ice)
                    g.zones += IceZone(s) -> (l.take(idx) ++ $(ice) ++ l.drop(idx))
                    g.state(ice).installedTurn = g.turn
                    g.state(ice).rezzed = false
                    g.log(Corp, "installs", ice, "protecting", s, "in the position of the trashed ice")
                    g.push(TrashEffect(old, Corp, false), RezEffect(ice, true), ShuffleDeckEffect(Corp))
                    g.trigger(Installed(Corp, ice))
                }
                else
                    g.push(TrashEffect(old, Corp, false), ShuffleDeckEffect(Corp))
                NextAction
        }
    }

    object BadTimes extends RotOp("06012") {
        override def playReq(self : Card)(implicit g : Game) = g.tagged
        override def lingers(self : Card)(implicit g : Game) = g.zone(self) == PlayZone(Runner)
        override def memory(self : Card)(implicit g : Game) = (g.zone(self) == PlayZone(Runner)).?(-2).|(0)
        override def effects(self : Card)(implicit g : Game) = $(MoveEffect(self, PlayZone(Runner), false), MessageEffect(Corp, "reduces the memory limit of the Runner by 2 until the end of the turn"), CheckMemoryAction)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnEnds(_) if g.zone(self) == PlayZone(Runner) => $(TrashLingeringEffect(self))
            case _ => $
        }
    }

    object EnhancedLoginProtocol extends RotOp("06022") {
        override def runCost(self : Card, server : Server)(implicit g : Game) = (inPlay(self) && g.rotClickRunStarting && g.reg("click-run") == 1).$(ClickCost(1) : Cost)
    }

    object CerebralStatic extends RotOp("06025") {
        override def effects(self : Card)(implicit g : Game) = g.identity.get(Runner)./(id => AddFlagEffect(Flag("disabled", |(id), None, "rot-cerebral-static", 0, $))).$ :+ MessageEffect(Corp, "disables the identity of the Runner")
        override def onLeave(self : Card)(implicit g : Game) = g.identity.get(Runner)./(id => RemoveFlagEffect("disabled", |(id))).$
    }

    object TargetedMarketing extends RotOp("06026") {
        override def effects(self : Card)(implicit g : Game) = $(ChooseCodeEffect(Corp, RotOperationCards.runnerNames, "Targeted Marketing: name a Runner card", TargetedMarketingPurpose(self)))
        override def chosenOp(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(c => g.state(p.card).memo = c.code)
            NextAction
        }
        def named(self : Card, c : Card)(implicit g : Game) = inPlay(self) && g.state(self).memo != "" && Cards.get(g.state(self).memo).exists(_.title == c.title)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Installed(Runner, c) if named(self, c) => $(gain(Corp, 10))
            case Played(Runner, c) if named(self, c) => $(gain(Corp, 10))
            case _ => $
        }
    }

    object PaywallImplementation extends RotOp("06028") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(_) if inPlay(self) => $(gain(Corp, 1))
            case _ => $
        }
    }

    object LagTime extends RotOp("06031") {
        override def iceStrengthMod(self : Card, ice : Card)(implicit g : Game) = inPlay(self).??(1)
    }

    object Manhunt extends RotOp("06046") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(_) if inPlay(self) && g.reg("successful-run") == 1 => $(TraceEffect(2, $(tag(1)), $))
            case _ => $
        }
    }

    object PeakEfficiency extends RotOp("06062") {
        override def effects(self : Card)(implicit g : Game) = $(gain(Corp, g.rezzedIce.num))
    }

    object Reuse extends RotOp("06070") {
        override def effects(self : Card)(implicit g : Game) = {
            val l = g.hand(Corp)
            l.any.$(ChooseCardsEffect(Corp, l, 0, l.num, ReusePurpose(self), "Reuse: choose any number of cards in HQ to trash"))
        }
        override def chosenOp(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.foreach(c => g.trashCard(c, Corp, false))
            if (cards.any)
                g.push(gain(Corp, 2 * cards.num))
            NextAction
        }
    }

    object SnatchAndGrab extends RotOp("06090") {
        override def effects(self : Card)(implicit g : Game) = $(TraceEffect(3, $(SnatchAction(self)), $))
        override def performOp(a : CardAction)(implicit g : Game) = a match {
            case SnatchAction(self) =>
                val l = runnerCards.%(c => g.has(c, "Connection"))
                if (l.any)
                    g.push(ChooseCardEffect(Corp, l, SnatchPurpose(self), "Snatch and Grab: choose a connection to trash", false))
                NextAction
        }
        override def chosenOp(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(c => g.push(OptionalEffect(Runner, "Snatch and Grab: take 1 tag to prevent the connection from being trashed?", $(TagsApplyEffect(1)), $(TrashEffect(c, Corp, false)))))
            NextAction
        }
    }

    object ShootTheMoon extends RotOp("06107") {
        override def playReq(self : Card)(implicit g : Game) = g.tagged
        override def effects(self : Card)(implicit g : Game) = {
            val l = g.allIce.%(c => !g.isRezzed(c))
            l.any.$(ChooseCardsEffect(Corp, l, 0, g.tags, RezPurpose(true), "Shoot the Moon: choose up to " + g.tags + " pieces of ice to rez"))
        }
    }

    object Housekeeping extends RotOp("07020") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Installed(Runner, _) if inPlay(self) && g.reg("runner-installs") == 0 => $(HousekeepingAction(self))
            case _ => $
        }
        override def performOp(a : CardAction)(implicit g : Game) = a match {
            case HousekeepingAction(self) =>
                if (g.hand(Runner).any)
                    g.push(ChooseCardEffect(Runner, g.hand(Runner), TrashFromHandPurpose(Runner), "Housekeeping: choose a card in the grip to trash", false))
                NextAction
        }
    }

    object Patch extends RotCondition("07021") {
        override def candidates(self : Card)(implicit g : Game) = g.rezzedIce
        override def hostPrompt = "Patch: choose a rezzed piece of ice"
        override def iceStrengthMod(self : Card, ice : Card)(implicit g : Game) = hosting(self, ice).??(2)
    }

    object TrafficAccident extends RotOp("07022") {
        override def playReq(self : Card)(implicit g : Game) = g.tags >= 2
        override def effects(self : Card)(implicit g : Game) = $(meat(2))
    }

    object SubBoost extends RotCondition("07025") {
        override def candidates(self : Card)(implicit g : Game) = g.rezzedIce
        override def hostPrompt = "Sub Boost: choose a rezzed piece of ice"
        override def hostEffects(self : Card, h : Card)(implicit g : Game) = $(AddSubtypeEffect(h, "Barrier"))
        override def extraSubroutines(self : Card, ice : Card)(implicit g : Game) = hosting(self, ice).$(Sub("End the run (Sub Boost)", EndRunEffect))
        override def onLeave(self : Card)(implicit g : Game) = host(self)./(h => RemoveSubtypeEffect(h, "Barrier")).$
    }

    object PredictiveAlgorithm extends RotOp("08017") {
        override def stealCostMod(self : Card, agenda : Card)(implicit g : Game) = inPlay(self).$(CreditCost(2) : Cost)
    }

    object RecruitingTrip extends RotOp("08035") {
        def sysops(codes : $[String])(implicit g : Game) = g.deck(Corp).%(c => g.has(c, "Sysop") && !codes.exists(x => Cards(x).title == c.title)).distinctBy(_.title).sortBy(_.title)
        override def effects(self : Card)(implicit g : Game) = $(ChooseNumberEffect(Corp, self, 0, g.credits(Corp).atMost(sysops($).num), "rt", "Recruiting Trip: pay how many credits (X)?"))
        override def performOp(a : CardAction)(implicit g : Game) = a match {
            case NumberPickedAction(self, n, "rt") =>
                if (n > 0) {
                    g.credits += Corp -> (g.credits(Corp) - n).atLeast(0)
                    g.log(Corp, "pays", n.credits, "(X)")
                }
                g.push(RecruitingStepAction(self, n, $), ShuffleDeckEffect(Corp))
                NextAction
            case RecruitingStepAction(self, left, codes) =>
                val l = sysops(codes)
                if (left > 0 && l.any)
                    g.push(ChooseCardEffect(Corp, l, RecruitingPurpose(self, left, codes), "Recruiting Trip: choose a sysop (" + left + " remaining)", true))
                NextAction
        }
        override def chosenOp(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case RecruitingPurpose(self, left, codes) =>
                cards.single.foreach(c => g.push(RevealEffect(Corp, c), AddToHandEffect(c), RecruitingStepAction(self, left - 1, codes :+ c.code)))
                NextAction
        }
    }

    object DefectiveBrainchips extends RotOp("08072") {
        override def preventions(self : Card, kind : String, n : Int)(implicit g : Game) =
            (kind == "core" && inPlay(self) && g.reg("core-damage") == 0 && !g.usedThisTurn(self, "brainchips")).$(Ability("increase the core damage by 1", $, $(MarkUsedEffect(self, "brainchips"), PreventWindowAction("core", n + 1))))
    }

    object AnOfferYouCantRefuse extends RotOp("08091") {
        override def effects(self : Card)(implicit g : Game) = $(ChooseOneEffect(Corp, "An Offer You Cant Refuse: choose a central server", Server.centrals./(s => Alt(s.name, $(OfferAction(self, s))))))
        override def performOp(a : CardAction)(implicit g : Game) = a match {
            case OfferAction(self, s) =>
                g.push(SetMemoEffect(self, "offer"), OptionalEffect(Runner, "An Offer You Cant Refuse: run " + s.name + " without being able to jack out?", $(RunEffect(s, None)), $(SetMemoEffect(self, ""), AddToScoreAreaEffect(self, Corp))))
                NextAction
        }
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case RunInitiated(_) if inPlay(self) && g.state(self).memo == "offer" => $(SetMemoEffect(self, ""), SetRunFlagEffect("cannot-jack-out"))
            case _ => $
        }
        override def agendaPointsBonus(self : Card)(implicit g : Game) = asAgendaPoints(self, Corp, 1)
    }

    object CastingCall extends RotCondition("08096") {
        override def candidates(self : Card)(implicit g : Game) = g.hand(Corp).%(_.data.isAgenda)
        override def effects(self : Card)(implicit g : Game) = {
            val l = candidates(self)
            l.any.$(ChooseCardEffect(Corp, l, CastingCallPurpose(self), "Casting Call: choose an agenda to install faceup", false))
        }
        override def chosenCondition(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(c => g.push(RotInstallEffect(c, g.remotes, true, true, 0, 0, None), CastingCallHostAction(p.card, c)))
            NextAction
        }
        override def performOp(a : CardAction)(implicit g : Game) = a match {
            case CastingCallHostAction(self, c) =>
                if (g.isInstalled(c)) {
                    g.state(c).seen = true
                    g.log(Corp, "turns", c, "faceup")
                    g.push(HostConditionEffect(self, c))
                }
                NextAction
        }
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Accessed(c) if hosting(self, c) => $(tag(2))
            case _ => $
        }
    }

    object BackChannels extends RotOp("08099") {
        def cands(implicit g : Game) = g.remotes./~(g.root)
        override def playReq(self : Card)(implicit g : Game) = cands.any
        override def effects(self : Card)(implicit g : Game) = $(ChooseCardEffect(Corp, cands, BackChannelsPurpose(self), "Back Channels: choose a card in the root of a remote server to trash", false))
        override def chosenOp(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(c => g.push(gain(Corp, 3 * g.advancement(c)), TrashEffect(c, Corp, false)))
            NextAction
        }
    }

    object NewsCycle extends RotOp("09019") {
        override def playReq(self : Card)(implicit g : Game) = scoredAgendas(Corp).any
        override def additionalCosts(self : Card)(implicit g : Game) = $(ChooseCardEffect(Corp, scoredAgendas(Corp), ForfeitPurpose, "24/7 News Cycle: choose an agenda to forfeit", false))
        override def effects(self : Card)(implicit g : Game) = $(NewsCycleAction(self))
        override def performOp(a : CardAction)(implicit g : Game) = a match {
            case NewsCycleAction(self) =>
                val l = scoredAgendas(Corp).%(c => g.impl(c).onScore(c).any)
                if (l.any)
                    g.push(ChooseCardEffect(Corp, l, NewsCyclePurpose(self), "24/7 News Cycle: choose an agenda to resolve its when scored ability", false))
                NextAction
        }
        override def chosenOp(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach { c =>
                g.log(Corp, "resolves the when scored ability of", c)
                g.pushAll(g.impl(c).onScore(c))
            }
            NextAction
        }
    }

    object AdBlitz extends RotOp("09020") {
        def ads(implicit g : Game) = corpCandidates("both", "Advertisement")
        override def effects(self : Card)(implicit g : Game) = $(ChooseNumberEffect(Corp, self, 0, g.credits(Corp).atMost(ads.num), "ab", "Ad Blitz: pay how many credits (X)?"))
        override def performOp(a : CardAction)(implicit g : Game) = a match {
            case NumberPickedAction(self, n, "ab") =>
                if (n > 0) {
                    g.credits += Corp -> (g.credits(Corp) - n).atLeast(0)
                    g.log(Corp, "pays", n.credits, "(X)")
                }
                g.push(installLoop(self, "both", n, true, 1, "Advertisement"))
                NextAction
        }
    }

    object MediaBlitz extends RotOp("09021") {
        def copied(self : Card)(implicit g : Game) : |[Card] = g.state(self).target.%(t => g.zone(t) == ScoredZone(Runner) && inPlay(self))
        def raw(t : Card) = CardImpls(t.code)
        override def playReq(self : Card)(implicit g : Game) = scoredAgendas(Runner).any
        override def effects(self : Card)(implicit g : Game) = $(ChooseCardEffect(Corp, scoredAgendas(Runner), MediaBlitzPurpose(self), "Media Blitz: choose an agenda in the score area of the Runner", false))
        override def chosenOp(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(c => g.push(SetTargetEffect(p.card, |(c))))
            NextAction
        }
        override def triggers(self : Card, e : Event)(implicit g : Game) = copied(self)./~(t => raw(t).triggers(t, e))
        override def handSize(self : Card, f : Faction)(implicit g : Game) = copied(self)./(t => raw(t).handSize(t, f)).|(0)
        override def iceStrengthMod(self : Card, ice : Card)(implicit g : Game) = copied(self)./(t => raw(t).iceStrengthMod(t, ice)).|(0)
        override def rezCostMod(self : Card, card : Card)(implicit g : Game) = copied(self)./(t => raw(t).rezCostMod(t, card)).|(0)
        override def installCostMod(self : Card, card : Card)(implicit g : Game) = copied(self)./(t => raw(t).installCostMod(t, card)).|(0)
        override def advancementRequirementMod(self : Card, agenda : Card)(implicit g : Game) = copied(self)./(t => raw(t).advancementRequirementMod(t, agenda)).|(0)
    }

    object TheAllSeeingI extends RotOp("09022") {
        override def playReq(self : Card)(implicit g : Game) = g.tagged
        override def effects(self : Card)(implicit g : Game) = $(AllSeeingAction(self))
        override def performOp(a : CardAction)(implicit g : Game) = a match {
            case AllSeeingAction(self) =>
                val res = runnerOf("Resource")
                if (res.any) {
                    if (g.badPublicity > 0)
                        g.push(OptionalEffect(Runner, "The All-Seeing I: remove 1 bad publicity to prevent all resources from being trashed?", $(BadPubEffect(-1)), $(TrashCardsEffect(res, Corp))))
                    else
                        g.push(TrashCardsEffect(res, Corp))
                }
                NextAction
        }
    }

    object SurveillanceSweep extends RotOp("09023") {
        // the trace order is not enforced (see STATUS-rot-operations.md): the card only stays in play as a current
    }

    object HeritageCommittee extends RotOp("10013") {
        override def effects(self : Card)(implicit g : Game) = $(draw(Corp, 3), HeritageAction(self))
        override def performOp(a : CardAction)(implicit g : Game) = a match {
            case HeritageAction(self) =>
                if (g.hand(Corp).any)
                    g.push(ChooseCardEffect(Corp, g.hand(Corp), TopOfDeckPurpose(Corp), "Heritage Committee: choose a card in HQ to add to the top of R&D", false))
                NextAction
        }
    }

    object DedicationCeremony extends RotOp("10017") {
        def cands(implicit g : Game) = g.installedCards(Corp).%(c => g.isRezzed(c) || (c.data.isAgenda && g.state(c).seen)) ++ runnerCards
        override def playReq(self : Card)(implicit g : Game) = cands.any
        override def effects(self : Card)(implicit g : Game) = $(ChooseCardEffect(Corp, cands, DedicationPurpose(self), "Dedication Ceremony: choose a faceup card", false))
        override def chosenOp(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(c => g.push(PlaceAdvancementEffect(c, 3), AddFlagEffect(Flag("cannot-score", |(c), None, "halfturn", g.halfTurn, $))))
            NextAction
        }
    }

    object ProductRecall extends RotOp("10029") {
        def cands(implicit g : Game) = g.installedCards(Corp).%(c => g.isRezzed(c) && (c.data.isAsset || c.data.isUpgrade))
        override def playReq(self : Card)(implicit g : Game) = cands.any
        override def effects(self : Card)(implicit g : Game) = $(ChooseCardEffect(Corp, cands, ProductRecallPurpose(self), "Product Recall: choose a rezzed asset or upgrade to trash", false))
        override def chosenOp(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(c => g.push(TrashEffect(c, Corp, false), gain(Corp, g.trashCost(c).|(0))))
            NextAction
        }
    }

    object ClonesAreNotPeople extends RotOp("10052") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case AgendaScored(_) if inPlay(self) => $(AddToScoreAreaEffect(self, Corp))
            case _ => $
        }
        override def agendaPointsBonus(self : Card)(implicit g : Game) = asAgendaPoints(self, Corp, 1)
    }

    object SalemsHospitality extends RotOp("10071") {
        override def effects(self : Card)(implicit g : Game) = $(ChooseCodeEffect(Corp, RotOperationCards.runnerNames, "Salems Hospitality: name a Runner card", SalemPurpose(self)))
        override def chosenOp(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach { c =>
                val grip = g.hand(Runner)
                val copies = grip.%(_.title == c.title)
                g.push(RevealCardsEffect(Runner, grip), TrashCardsEffect(copies, Corp))
            }
            NextAction
        }
    }

    object LocalizedProductLine extends RotOp("10075") {
        override def effects(self : Card)(implicit g : Game) = {
            val l = g.deck(Corp).distinctBy(_.title).sortBy(_.title)
            l.any.$(ChooseCardEffect(Corp, l, LocalizedPurpose(self), "Localized Product Line: choose a card to search for", true)) :+ ShuffleDeckEffect(Corp)
        }
        override def chosenOp(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach { c =>
                val copies = g.deck(Corp).%(_.title == c.title)
                g.pushAll($(RevealCardsEffect(Corp, copies), MessageEffect(Corp, "adds " + copies.num + " cop" + (copies.num != 1).?("ies").|("y") + " from R&D to HQ")) ++ copies./(AddToHandEffect(_)))
            }
            NextAction
        }
    }

    object ExchangeOfInformation extends RotOp("10092") {
        override def playReq(self : Card)(implicit g : Game) = g.tagged && scoredAgendas(Runner).any && scoredAgendas(Corp).any
        override def effects(self : Card)(implicit g : Game) = $(ChooseCardEffect(Corp, scoredAgendas(Runner), ExchangePurpose(self), "Exchange of Information: choose an agenda in the score area of the Runner", false))
        override def chosenOp(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case ExchangePurpose(self) =>
                cards.single.foreach(r => g.push(ChooseCardEffect(Corp, scoredAgendas(Corp), ExchangeSwapPurpose(self, r), "Exchange of Information: choose an agenda in your score area to swap", false)))
                NextAction
            case ExchangeSwapPurpose(self, r) =>
                cards.single.foreach(c => g.push(SwapScoredEffect(c, r)))
                NextAction
        }
    }

    object ConsultingVisit extends RotOp("10094") {
        override def effects(self : Card)(implicit g : Game) = $(ConsultingAction(self))
        override def performOp(a : CardAction)(implicit g : Game) = a match {
            case ConsultingAction(self) =>
                val l = g.deck(Corp).%(c => c.data.isOperation && g.availableCredits(Corp, None, "play-operation") >= g.playCost(c) && g.impl(c).canPlay(c)).distinctBy(_.title).sortBy(_.title)
                if (l.any)
                    g.push(ChooseCardEffect(Corp, l, ConsultingPurpose(self), "Consulting Visit: choose an operation in R&D to play", true), ConsultingShuffleAction(self))
                else
                    g.push(ShuffleDeckEffect(Corp))
                NextAction
            case ConsultingShuffleAction(self) =>
                if (g.state(self).memo != "played")
                    g.push(ShuffleDeckEffect(Corp))
                g.state(self).memo = ""
                NextAction
        }
        override def chosenOp(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach { c =>
                g.state(p.card).memo = "played"
                g.push(ShuffleDeckEffect(Corp), RotPlayEffect(c, false))
            }
            NextAction
        }
    }

    object LateralGrowth extends RotOp("10104") {
        override def effects(self : Card)(implicit g : Game) = $(gain(Corp, 4), installLoop(self, "hand", 1, true, 0))
    }

    object VoterIntimidation extends RotOp("10106") {
        override def playReq(self : Card)(implicit g : Game) = scoredAgendas(Runner).any
        override def effects(self : Card)(implicit g : Game) = $(PsiEffect($, $(VoterAction(self))))
        override def performOp(a : CardAction)(implicit g : Game) = a match {
            case VoterAction(self) =>
                g.pushAll(trashOne(runnerOf("Resource"), "Voter Intimidation: choose a resource to trash"))
                NextAction
        }
    }

    object ElectionDay extends RotOp("10112") {
        override def playReq(self : Card)(implicit g : Game) = g.hand(Corp).but(self).any
        override def effects(self : Card)(implicit g : Game) = $(ElectionDayAction(self))
        override def performOp(a : CardAction)(implicit g : Game) = a match {
            case ElectionDayAction(self) =>
                g.hand(Corp).foreach(c => g.trashCard(c, Corp, false))
                g.push(draw(Corp, 5))
                NextAction
        }
    }

    object Subcontract extends RotOp("10113") {
        def ops(self : Card)(implicit g : Game) = g.hand(Corp).%(c => c != self && c.data.isOperation && g.availableCredits(Corp, None, "play-operation") >= g.playCost(c) && g.impl(c).canPlay(c))
        override def playReq(self : Card)(implicit g : Game) = g.tagged && g.hand(Corp).exists(c => c != self && c.data.isOperation)
        override def effects(self : Card)(implicit g : Game) = $(SubcontractStepAction(self, 1))
        override def performOp(a : CardAction)(implicit g : Game) = a match {
            case SubcontractStepAction(self, i) =>
                val l = ops(self)
                if (l.any)
                    g.push(ChooseCardEffect(Corp, l, SubcontractPurpose(self, i), "Subcontract: choose an operation in HQ to play (" + i + " of 2)", true))
                NextAction
        }
        override def chosenOp(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case SubcontractPurpose(self, i) =>
                cards.single.foreach(c => g.pushAll($(RotPlayEffect(c, false)) ++ (i < 2 && !g.has(c, "Terminal")).$(SubcontractStepAction(self, i + 1))))
                NextAction
        }
    }

    object HardHittingNews extends RotOp("11016") {
        override def playReq(self : Card)(implicit g : Game) = g.regLast("runs") > 0
        override def effects(self : Card)(implicit g : Game) = $(TraceEffect(4, $(tag(4)), $))
    }

    object StockBuyBack extends RotOp("11019") {
        override def effects(self : Card)(implicit g : Game) = $(gain(Corp, 3 * scoredAgendas(Runner).num))
    }

    object EnforcingLoyalty extends RotOp("11033") {
        override def effects(self : Card)(implicit g : Game) = $(TraceEffect(3, $(EnforcingLoyaltyAction(self)), $))
        override def performOp(a : CardAction)(implicit g : Game) = a match {
            case EnforcingLoyaltyAction(self) =>
                val f = g.identity.get(Runner)./(_.data.faction).|("Neutral")
                val l = runnerCards.%(c => f == "Neutral" || c.data.faction != f)
                g.pushAll(trashOne(l, "Enforcing Loyalty: choose an installed card not matching the faction of the Runner identity"))
                NextAction
        }
    }

    object HatchetJob extends RotOp("11034") {
        override def effects(self : Card)(implicit g : Game) = $(TraceEffect(5, $(HatchetJobAction(self)), $))
        override def performOp(a : CardAction)(implicit g : Game) = a match {
            case HatchetJobAction(self) =>
                val l = runnerCards.%(c => !g.has(c, "Virtual"))
                if (l.any)
                    g.push(ChooseCardEffect(Corp, l, ReturnToHandPurpose, "Hatchet Job: choose an installed non-virtual card to add to the grip", false))
                NextAction
        }
    }

    object SpecialReport extends RotOp("11035") {
        override def effects(self : Card)(implicit g : Game) = {
            val l = g.hand(Corp)
            l.any.$(ChooseCardsEffect(Corp, l, 0, l.num, SpecialReportPurpose(self), "Special Report: choose any number of cards in HQ to shuffle into R&D"))
        }
        override def chosenOp(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.foreach(c => g.moveTo(c, DeckZone(Corp)))
            if (cards.any) {
                g.log(Corp, "shuffles", cards.num.cards, "from HQ into R&D")
                g.push(ShuffleDeckEffect(Corp), draw(Corp, cards.num))
            }
            NextAction
        }
    }

    object Liquidation extends RotOp("11037") {
        override def effects(self : Card)(implicit g : Game) = {
            val l = g.installedCards(Corp).%(c => g.isRezzed(c) && !c.data.isAgenda)
            l.any.$(ChooseCardsEffect(Corp, l, 0, l.num, LiquidationPurpose(self), "Liquidation: choose any number of rezzed cards to trash"))
        }
        override def chosenOp(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.foreach(c => g.trashCard(c, Corp, false))
            if (cards.any)
                g.push(gain(Corp, 3 * cards.num))
            NextAction
        }
    }

    object FinancialCollapse extends RotOp("11039") {
        override def playReq(self : Card)(implicit g : Game) = g.credits(Runner) >= 6
        override def effects(self : Card)(implicit g : Game) = $(FinancialCollapseAction(self))
        override def performOp(a : CardAction)(implicit g : Game) = a match {
            case FinancialCollapseAction(self) =>
                val res = runnerOf("Resource")
                val n = 2 * res.num
                if (n > 0)
                    g.push(ChooseOneEffect(Runner, "Financial Collapse: choose one", $(
                        Alt("Trash a resource", $(ChooseCardEffect(Runner, res, TrashPurpose(Runner), "Financial Collapse: choose a resource to trash", false))),
                        Alt("Lose " + n + " credits", $(LoseCreditsEffect(Runner, n))))))
                NextAction
        }
    }

    object ArkLockdown extends RotOp("11050") {
        def names(implicit g : Game) = g.discard(Runner).distinctBy(_.title).sortBy(_.title)
        override def playReq(self : Card)(implicit g : Game) = names.any
        override def effects(self : Card)(implicit g : Game) = $(ChooseCardEffect(Corp, names, ArkLockdownPurpose(self), "Ark Lockdown: name a card in the heap", false))
        override def chosenOp(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(c => g.pushAll(g.discard(Runner).%(_.title == c.title)./(RemoveFromGameEffect(_))))
            NextAction
        }
    }

    object HellionBetaTest extends RotOp("11051") {
        override def playReq(self : Card)(implicit g : Game) = g.regLast("trash-accessed") > 0
        override def effects(self : Card)(implicit g : Game) = $(TraceEffect(2, $(HellionBetaAction(self)), $(BadPubEffect(1))))
        override def performOp(a : CardAction)(implicit g : Game) = a match {
            case HellionBetaAction(self) =>
                val l = runnerCards.%(c => !c.data.isProgram)
                if (l.any)
                    g.push(ChooseCardsEffect(Corp, l, 2.atMost(l.num), 2, TrashPurpose(Corp), "Hellion Beta Test: choose 2 installed non-program cards to trash"))
                NextAction
        }
    }

    object ObserveAndDestroy extends RotOp("11056") {
        override def playReq(self : Card)(implicit g : Game) = g.tagged && g.credits(Runner) < 6
        override def additionalCosts(self : Card)(implicit g : Game) = $(RemoveTagsEffect(1))
        override def effects(self : Card)(implicit g : Game) = trashOne(runnerCards, "Observe and Destroy: choose an installed card to trash")
    }

    object ServiceOutage extends RotOp("11057") {
        override def runCost(self : Card, server : Server)(implicit g : Game) = (inPlay(self) && g.active.isRunner && g.reg("runs") == 0).$(CreditCost(1) : Cost)
    }

    object Boom extends RotOp("11058") {
        override def playReq(self : Card)(implicit g : Game) = g.tags >= 2
        override def effects(self : Card)(implicit g : Game) = $(meat(7))
    }

    object DoorToDoor extends RotOp("11059") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) if inPlay(self) => $(TraceEffect(1, $(DoorToDoorAction(self)), $))
            case _ => $
        }
        override def performOp(a : CardAction)(implicit g : Game) = a match {
            case DoorToDoorAction(self) =>
                g.push(g.tagged.?(meat(1) : ForcedAction).|(tag(1)))
                NextAction
        }
    }

    object ScarcityOfResources extends RotOp("11060") {
        override def installCostMod(self : Card, card : Card)(implicit g : Game) = (inPlay(self) && card.data.isResource).??(2)
    }

    val all : $[CardImpl] = $(
        Precognition, AggressiveNegotiation, BigBrother, PowerGridOverload, Freelancer, Sunset, Commercialization, Rework, Foxfire,
        MidseasonReplacements, BioroidEfficiencyResearch, SuccessfulDemonstration, InvasionOfPrivacy, CyberdexTrial, HellionAlphaTest,
        ShipmentFromSanSan, Restructure, AcceleratedDiagnostics, PowerShutdown, Interns, RestoringFace, ReclamationOrder, CorporateShuffle,
        WitnessTampering, CerebralCast, MedicalResearchFundraiser, MushinNoShin, DiversifiedPortfolio, FastTrack, Mutate, BadTimes,
        EnhancedLoginProtocol, CerebralStatic, TargetedMarketing, PaywallImplementation, LagTime, Manhunt, PeakEfficiency, Reuse,
        SnatchAndGrab, ShootTheMoon, Housekeeping, Patch, TrafficAccident, SubBoost, PredictiveAlgorithm, RecruitingTrip,
        DefectiveBrainchips, AnOfferYouCantRefuse, CastingCall, BackChannels, NewsCycle, AdBlitz, MediaBlitz, TheAllSeeingI,
        SurveillanceSweep, HeritageCommittee, DedicationCeremony, ProductRecall, ClonesAreNotPeople, SalemsHospitality,
        LocalizedProductLine, ExchangeOfInformation, ConsultingVisit, LateralGrowth, VoterIntimidation, ElectionDay, Subcontract,
        HardHittingNews, StockBuyBack, EnforcingLoyalty, HatchetJob, SpecialReport, Liquidation, FinancialCollapse, ArkLockdown,
        HellionBetaTest, ObserveAndDestroy, ServiceOutage, Boom, DoorToDoor, ScarcityOfResources,
    )
}
