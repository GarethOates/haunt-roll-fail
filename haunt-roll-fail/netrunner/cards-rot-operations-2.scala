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
// Rotated (FFG era) Corp operations, part 2 (see cards-rot-operations.scala)
// ---------------------------------------------------------------------------------------------------------------------

// card specific actions and purposes
case class HastyRelocationAction(card : Card) extends CardAction
case class HastyRelocationPurpose(card : Card) extends CardPurpose
case class PreemptiveShuffleAction(card : Card) extends CardAction
case class PsychokinesisAction(card : Card) extends CardAction
case class PsychokinesisChooseAction(card : Card, top : $[Card]) extends CardAction
case class ReplantingPurpose(card : Card) extends CardPurpose
case class MCATagAction(card : Card) extends CardAction
case class SacrificePurpose(card : Card) extends CardPurpose
case class SacrificeAction(card : Card) extends CardAction
case class AudacityAction(card : Card) extends CardAction
case class AudacityAdvanceAction(card : Card) extends CardAction
case class RedPlanetPurpose(card : Card) extends CardPurpose
case class SuccessForfeitPurpose(card : Card) extends CardPurpose
case class SuccessAction(card : Card) extends CardAction
case class SuccessAdvancePurpose(card : Card, n : Int) extends CardPurpose
case class BiasedReportingAction(card : Card, kind : String) extends CardAction
case class BiasedReportingPurpose(card : Card, kind : String) extends CardPurpose
case class BiasedReportingGainAction(card : Card, kind : String) extends CardAction
case class RestorePurpose(card : Card) extends CardPurpose
case class RestoreRfgAction(card : Card, target : Card) extends CardAction
case class ThreatLevelAlphaAction(card : Card) extends CardAction
case class PriorityConstructionPurpose(card : Card) extends CardPurpose
case class GenotypingAction(card : Card) extends CardAction
case class WakeUpCallPurpose(card : Card) extends CardPurpose
case class ThreatAssessmentPurpose(card : Card) extends CardPurpose
case class EconomicWarfareAction(card : Card) extends CardAction
case class DistractTheMassesAction(card : Card) extends CardAction
case class DistractTheMassesShuffleAction(card : Card) extends CardAction
case class ReverseInfectionAction(card : Card) extends CardAction
case class TrojanHorseAction(card : Card) extends CardAction
case class StandardProcedureAction(card : Card, kind : String) extends CardAction
case class MarketForcesAction(card : Card) extends CardAction
case class DivertPowerPurpose(card : Card) extends CardPurpose
case class DivertPowerRezAction(card : Card, discount : Int) extends CardAction
case class DivertPowerRezPurpose(card : Card, discount : Int) extends CardPurpose
case class FastBreakStepAction(card : Card, left : Int) extends CardAction
case class FastBreakPurpose(card : Card, left : Int) extends CardPurpose
case class HangekiPurpose(card : Card) extends CardPurpose
case class AttitudeAdjustmentAction(card : Card) extends CardAction
case class AttitudeAdjustmentPurpose(card : Card) extends CardPurpose
case class AttitudeAdjustmentShuffleAction(card : Card, agendas : $[Card]) extends CardAction
case class BuildingBlocksPurpose(card : Card) extends CardPurpose
case class ClosedAccountsAction(card : Card) extends CardAction


object RotOperationCardsB {

    object WetworkRefit extends RotCondition("11071") {
        override def candidates(self : Card)(implicit g : Game) = g.rezzedIce.%(c => g.has(c, "Bioroid"))
        override def hostPrompt = "Wetwork Refit: choose a rezzed piece of bioroid ice"
        override def extraSubroutines(self : Card, ice : Card)(implicit g : Game) = hosting(self, ice).$(Sub("Do 1 core damage (Wetwork Refit)", DamageEffect("core", 1)))
    }

    object HastyRelocation extends RotOp("11074") {
        override def playReq(self : Card)(implicit g : Game) = g.deck(Corp).any
        override def additionalCosts(self : Card)(implicit g : Game) = $(TrashTopEffect(Corp, 1, false))
        override def effects(self : Card)(implicit g : Game) = $(draw(Corp, 3), HastyRelocationAction(self))
        override def performOp(a : CardAction)(implicit g : Game) = a match {
            case HastyRelocationAction(self) =>
                val n = 3.atMost(g.hand(Corp).num)
                if (n > 0)
                    g.push(ChooseCardsEffect(Corp, g.hand(Corp), n, n, HastyRelocationPurpose(self), "Hasty Relocation: choose 3 cards in HQ to add to the top of R&D"))
                NextAction
        }
        override def chosenOp(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.foreach(c => g.moveTo(c, DeckZone(Corp), true))
            if (cards.any) {
                g.log(Corp, "adds", cards.num.cards, "from HQ to the top of R&D")
                g.push(ArrangeTopEffect(Corp, Corp, cards.num))
            }
            NextAction
        }
    }

    object BestDefense extends RotOp("11079") {
        def cands(implicit g : Game) = runnerCards.%(c => !g.state(c).facedown && installedCost(c) <= g.tags)
        override def playReq(self : Card)(implicit g : Game) = cands.any
        override def effects(self : Card)(implicit g : Game) = trashOne(cands, "Best Defense: choose an installed Runner card with an install cost of " + g.tags + " or less to trash")
    }

    object PreemptiveAction extends RotOp("11080") {
        override def rfg(self : Card)(implicit g : Game) = true
        override def effects(self : Card)(implicit g : Game) = $(PreemptiveShuffleAction(self))
        override def performOp(a : CardAction)(implicit g : Game) = a match {
            case PreemptiveShuffleAction(self) =>
                val l = g.discard(Corp)
                val n = 3.atMost(l.num)
                if (n > 0)
                    g.push(ChooseCardsEffect(Corp, l, n, 3, ShuffleIntoDeckPurpose(Corp), "Preemptive Action: choose 3 cards in Archives to shuffle into R&D"))
                NextAction
        }
    }

    object FriendsInHighPlaces extends RotOp("11090") {
        override def effects(self : Card)(implicit g : Game) = $(installLoop(self, "discard", 2, true, 0))
    }

    object EnforcedCurfew extends RotOp("11100") {
        override def handSize(self : Card, f : Faction)(implicit g : Game) = (f.isRunner && inPlay(self)).?(-1).|(0)
    }

    object VioletLevelClearance extends RotOp("11111") {
        override def effects(self : Card)(implicit g : Game) = $(gain(Corp, 8), draw(Corp, 4))
    }

    object Psychokinesis extends RotOp("11113") {
        override def effects(self : Card)(implicit g : Game) = $(PsychokinesisAction(self))
        override def performOp(a : CardAction)(implicit g : Game) = a match {
            case PsychokinesisAction(self) =>
                val top = g.deck(Corp).take(5)
                if (top.any)
                    g.push(LookAtEffect(Corp, top, "Psychokinesis: the top 5 cards of R&D (top first)"), PsychokinesisChooseAction(self, top))
                NextAction
            case PsychokinesisChooseAction(self, top) =>
                val l = top.%(c => g.zone(c) == DeckZone(Corp) && (c.data.isAgenda || c.data.isAsset || c.data.isUpgrade)).%(c => g.rotInstallTargets(c, g.remotes, true, true, 0).any)
                if (l.any)
                    g.push(ChooseCardEffect(Corp, l, RotInstallPurpose(self, true, 0, 0, true), "Psychokinesis: choose an agenda, asset or upgrade to install in a remote server", true))
                NextAction
        }
    }

    object LoadTesting extends RotOp("12031") {
        override def effects(self : Card)(implicit g : Game) = $(ExtraClicksEffect(Runner, -1))
    }

    object Replanting extends RotOp("12033") {
        override def playReq(self : Card)(implicit g : Game) = g.installedCards(Corp).any
        override def effects(self : Card)(implicit g : Game) = $(ChooseCardEffect(Corp, g.installedCards(Corp), ReplantingPurpose(self), "Replanting: choose an installed card to add to HQ", false), installLoop(self, "hand", 2, false, 0))
        override def chosenOp(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach { c =>
                g.log(Corp, "adds", g.hiddenCorpElem(c), "to HQ")
                g.uninstall(c)
                g.moveTo(c, HandZone(Corp))
                g.cleanupRemotes()
            }
            NextAction
        }
    }

    object MCAInformant extends RotCondition("12036") {
        override def candidates(self : Card)(implicit g : Game) = runnerOf("Resource").%(c => g.has(c, "Connection"))
        override def hostPrompt = "MCA Informant: choose a connection resource"
        override def hostEffects(self : Card, h : Card)(implicit g : Game) = $(MoveEffect(self, PlayZone(Runner), false), MCATagAction(self))
        override def performOp(a : CardAction)(implicit g : Game) = a match {
            case MCATagAction(self) =>
                g.tags += 1
                g.log("The Runner is considered to have", 1.tags, "more (MCA Informant)")
                NextAction
        }
        override def onLeave(self : Card)(implicit g : Game) = (g.tags > 0).$(RemoveTagsEffect(1))
        // the Runner may trash the host resource ([click], 2 credits); MCA Informant sits in the Runner's play area so that
        // the Runner can use this ability
        override def abilities(self : Card)(implicit g : Game) = host(self).%(_ => g.zone(self) == PlayZone(Runner))./(h => Ability("trash the host resource", $(ClickCost(1), CreditCost(2)), $(TrashEffect(h, Runner, false)), click = true)).$
    }

    object Sacrifice extends RotOp("12039") {
        override def playReq(self : Card)(implicit g : Game) = scoredAgendas(Corp).any
        override def additionalCosts(self : Card)(implicit g : Game) = $(ChooseCardEffect(Corp, scoredAgendas(Corp), SacrificePurpose(self), "Sacrifice: choose an agenda to forfeit", false))
        override def effects(self : Card)(implicit g : Game) = $(SacrificeAction(self))
        override def chosenOp(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach { c =>
                g.state(p.card).memo = c.data.agendaPoints.|(0).toString
                g.push(ForfeitEffect(c))
            }
            NextAction
        }
        override def performOp(a : CardAction)(implicit g : Game) = a match {
            case SacrificeAction(self) =>
                val x = g.state(self).memo.toIntOption.|(0)
                val n = x.atMost(g.badPublicity).atLeast(0)
                if (n > 0)
                    g.push(BadPubEffect(-n), gain(Corp, n))
                NextAction
        }
    }

    object Audacity extends RotOp("12058") {
        override def playReq(self : Card)(implicit g : Game) = g.hand(Corp).but(self).num >= 2
        override def effects(self : Card)(implicit g : Game) = $(AudacityAction(self))
        override def performOp(a : CardAction)(implicit g : Game) = a match {
            case AudacityAction(self) =>
                g.hand(Corp).foreach(c => g.trashCard(c, Corp, false))
                g.push(AudacityAdvanceAction(self), AudacityAdvanceAction(self))
                NextAction
            case AudacityAdvanceAction(self) =>
                val l = g.advanceableCards
                if (l.any)
                    g.push(ChooseCardEffect(Corp, l, AdvancePurpose(1), "Audacity: choose a card to place 1 advancement counter on", false))
                NextAction
        }
    }

    object RedPlanetCouriers extends RotOp("12059") {
        override def effects(self : Card)(implicit g : Game) = {
            val l = g.advanceableCards
            l.any.$(ChooseCardEffect(Corp, l, RedPlanetPurpose(self), "Red Planet Couriers: choose a card to move all advancement counters to", false))
        }
        override def chosenOp(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach { c =>
                g.pushAll((g.installedCards(Corp) ++ runnerCards).%(x => x != c && g.advancement(x) > 0)./(x => MoveAdvancementEffect(x, c, g.advancement(x))))
            }
            NextAction
        }
    }

    object ShipmentFromTennin extends RotOp("12072") {
        override def playReq(self : Card)(implicit g : Game) = g.successfulRunsLastTurn.none
        override def effects(self : Card)(implicit g : Game) = {
            val l = g.installedCards(Corp)
            l.any.$(ChooseCardEffect(Corp, l, AdvancePurpose(2), "Shipment from Tennin: choose an installed card to place 2 advancement counters on", false))
        }
    }

    object Success extends RotOp("12078") {
        override def playReq(self : Card)(implicit g : Game) = scoredAgendas(Corp).any
        override def additionalCosts(self : Card)(implicit g : Game) = $(ChooseCardEffect(Corp, scoredAgendas(Corp), SuccessForfeitPurpose(self), "Success: choose an agenda to forfeit", false))
        override def effects(self : Card)(implicit g : Game) = $(SuccessAction(self))
        override def chosenOp(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case SuccessForfeitPurpose(self) =>
                cards.single.foreach { c =>
                    g.state(self).memo = g.advancementRequirement(c).toString
                    g.push(ForfeitEffect(c))
                }
                NextAction
            case SuccessAdvancePurpose(self, n) =>
                cards.single.foreach(c => g.push(AdvanceTimesEffect(c, n)))
                NextAction
        }
        override def performOp(a : CardAction)(implicit g : Game) = a match {
            case SuccessAction(self) =>
                val n = g.state(self).memo.toIntOption.|(0)
                val l = g.advanceableCards
                if (n > 0 && l.any)
                    g.push(ChooseCardEffect(Corp, l, SuccessAdvancePurpose(self, n), "Success: choose a card to advance " + n + " times", false))
                NextAction
        }
    }

    object MassCommercialization extends RotOp("12080") {
        override def effects(self : Card)(implicit g : Game) = $(gain(Corp, 2 * (g.installedCards(Corp) ++ runnerCards).%(c => g.advancement(c) > 0).num))
    }

    object O2Shortage extends RotOp("12090") {
        override def effects(self : Card)(implicit g : Game) = $(ChooseOneEffect(Runner, "O2 Shortage: choose one", $(
            Alt("Trash 1 card from the grip at random", $(RandomTrashFromHandEffect(Runner, 1)), g.hand(Runner).any),
            Alt("The Corp gains 2 clicks", $(GainClicksEffect(Corp, 2))))))
    }

    object BiasedReporting extends RotOp("12096") {
        override def playReq(self : Card)(implicit g : Game) = runnerCards.any
        override def effects(self : Card)(implicit g : Game) = $(ChooseOneEffect(Corp, "Biased Reporting: choose a card type", $("Hardware", "Program", "Resource")./(k => Alt(k, $(BiasedReportingAction(self, k))))))
        override def performOp(a : CardAction)(implicit g : Game) = a match {
            case BiasedReportingAction(self, k) =>
                val l = runnerOf(k)
                if (l.any)
                    g.push(ChooseCardsEffect(Runner, l, 0, l.num, BiasedReportingPurpose(self, k), "Biased Reporting: choose any number of cards of the chosen type to trash (gain 1 credit each)"), BiasedReportingGainAction(self, k))
                NextAction
            case BiasedReportingGainAction(self, k) =>
                g.push(gain(Corp, 2 * runnerOf(k).num))
                NextAction
        }
        override def chosenOp(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.foreach(c => g.trashCard(c, Runner, false))
            if (cards.any)
                g.pushAll($(gain(Runner, cards.num)))
            NextAction
        }
    }

    object TransparencyInitiative extends RotCondition("12099") {
        override def candidates(self : Card)(implicit g : Game) = g.installedCards(Corp).%(c => c.data.isAgenda && !g.state(c).seen)
        override def hostPrompt = "Transparency Initiative: choose a facedown agenda to turn faceup"
        override def hostEffects(self : Card, h : Card)(implicit g : Game) = $(RevealEffect(Corp, h), AddSubtypeEffect(h, "Public"))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Advanced(c, _) if hosting(self, c) => $(gain(Corp, 1))
            case _ => $
        }
        override def onLeave(self : Card)(implicit g : Game) = host(self)./(h => RemoveSubtypeEffect(h, "Public")).$
    }

    object RoverAlgorithm extends RotCondition("12100") {
        override def candidates(self : Card)(implicit g : Game) = g.rezzedIce
        override def hostPrompt = "Rover Algorithm: choose a rezzed piece of ice"
        override def iceStrengthMod(self : Card, ice : Card)(implicit g : Game) = hosting(self, ice).??(g.counter(self, "power"))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case PassIce(ice) if hosting(self, ice) => $(AddCounterEffect(self, "power", 1))
            case _ => $
        }
    }

    object Restore extends RotOp("12112") {
        def cands(implicit g : Game) = g.discard(Corp).%(c => corpInstallable(c) && !c.data.isAgenda && g.rotInstallTargets(c, g.rotServersFor(c, false), true, true, 1).any)
        override def playReq(self : Card)(implicit g : Game) = g.discard(Corp).exists(c => corpInstallable(c) && !c.data.isAgenda)
        override def effects(self : Card)(implicit g : Game) = {
            val l = cands
            l.any.$(ChooseCardEffect(Corp, l, RestorePurpose(self), "Restore: choose a card in Archives to install and rez", false))
        }
        override def chosenOp(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(c => g.push(RotInstallEffect(c, g.rotServersFor(c, false), true, true, 1, 0, None), RestoreRfgAction(p.card, c)))
            NextAction
        }
        override def performOp(a : CardAction)(implicit g : Game) = a match {
            case RestoreRfgAction(self, c) =>
                g.pushAll(g.discard(Corp).%(x => x.title == c.title && x != c)./(RemoveFromGameEffect(_)))
                NextAction
        }
    }

    object RollingBrownout extends RotOp("12116") {
        override def playCostMod(self : Card, card : Card)(implicit g : Game) = (inPlay(self) && (card.data.isOperation || card.data.isEvent)).??(1)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Played(Runner, c) if c.data.isEvent && inPlay(self) && g.reg("runner-played-event") == 0 => $(gain(Corp, 1))
            case _ => $
        }
    }

    object ThreatLevelAlpha extends RotOp("12117") {
        override def effects(self : Card)(implicit g : Game) = $(TraceEffect(1, $(ThreatLevelAlphaAction(self)), $))
        override def performOp(a : CardAction)(implicit g : Game) = a match {
            case ThreatLevelAlphaAction(self) =>
                g.push(tag(g.tags.atLeast(1)))
                NextAction
        }
    }

    object PriorityConstruction extends RotOp("12118") {
        override def effects(self : Card)(implicit g : Game) = {
            val l = g.hand(Corp).%(_.data.isIce)
            l.any.$(ChooseCardEffect(Corp, l, PriorityConstructionPurpose(self), "Priority Construction: choose a piece of ice in HQ to install protecting a remote server", true))
        }
        override def chosenOp(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(c => g.push(RotInstallEffect(c, g.remotes, true, false, 0, 3, None)))
            NextAction
        }
    }

    object UltravioletClearance extends RotOp("13038") {
        override def effects(self : Card)(implicit g : Game) = $(gain(Corp, 10), draw(Corp, 4), installLoop(self, "hand", 1, true, 0))
    }

    object HunterSeeker extends RotOp("13051") {
        override def playReq(self : Card)(implicit g : Game) = g.regLast("stolen") > 0
        override def effects(self : Card)(implicit g : Game) = trashOne(runnerCards, "Hunter Seeker: choose an installed card to trash")
    }

    object NetWatchlist extends RotOp("14023") {
        // "The Runner must pay 2 credits as an additional cost to use an icebreaker." (break and pump abilities)
        override def icebreakerSurcharge(self : Card, breaker : Card, breaking : Boolean)(implicit g : Game) = inPlay(self).??(2)
    }

    object GreenLevelClearance extends RotOp("20073") {
        override def effects(self : Card)(implicit g : Game) = $(gain(Corp, 3), draw(Corp, 1))
    }

    object ShipmentFromMirrorMorph extends RotOp("20074") {
        override def effects(self : Card)(implicit g : Game) = $(installLoop(self, "hand", 3, true, 0))
    }

    object ShipmentFromKaguya extends RotOp("20092") {
        override def effects(self : Card)(implicit g : Game) = {
            val l = g.advanceableCards
            l.any.$(ChooseCardsEffect(Corp, l, 0, 2, AdvancePurpose(1), "Shipment from Kaguya: choose up to 2 cards to place 1 advancement counter on"))
        }
    }

    object AnonymousTip extends RotOp("20118") {
        override def effects(self : Card)(implicit g : Game) = $(draw(Corp, 3))
    }

    object Genotyping extends RotOp("21014") {
        override def rfg(self : Card)(implicit g : Game) = true
        override def effects(self : Card)(implicit g : Game) = $(TrashTopEffect(Corp, 2, false), GenotypingAction(self))
        override def performOp(a : CardAction)(implicit g : Game) = a match {
            case GenotypingAction(self) =>
                if (g.discard(Corp).any)
                    g.push(ChooseCardsEffect(Corp, g.discard(Corp), 0, 4, ShuffleIntoDeckPurpose(Corp), "Genotyping: choose up to 4 cards in Archives to shuffle into R&D"))
                NextAction
        }
    }

    object SelfGrowthProgram extends RotOp("21016") {
        override def playReq(self : Card)(implicit g : Game) = g.tagged
        override def effects(self : Card)(implicit g : Game) = {
            val l = runnerCards
            l.any.$(ChooseCardsEffect(Corp, l, 2.atMost(l.num), 2, ReturnToHandPurpose, "Self-Growth Program: choose 2 installed Runner cards to add to the grip"))
        }
    }

    object WakeUpCall extends RotOp("21019") {
        def cands(implicit g : Game) = runnerCards.%(c => c.data.isHardware || (c.data.isResource && !g.has(c, "Virtual")))
        override def rfg(self : Card)(implicit g : Game) = true
        override def playReq(self : Card)(implicit g : Game) = g.regLast("runner-trash-corp") > 0 && cands.any
        override def effects(self : Card)(implicit g : Game) = $(ChooseCardEffect(Corp, cands, WakeUpCallPurpose(self), "Wake Up Call: choose a piece of hardware or a non-virtual resource", false))
        override def chosenOp(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(c => g.push(ChooseOneEffect(Runner, "Wake Up Call: choose one", $(Alt("Trash the chosen card", $(TrashEffect(c, Runner, false))), Alt("Suffer 4 meat damage", $(meat(4)))))))
            NextAction
        }
    }

    object ThreatAssessment extends RotOp("21035") {
        override def rfg(self : Card)(implicit g : Game) = true
        override def playReq(self : Card)(implicit g : Game) = g.regLast("runner-trash-corp") > 0 && runnerCards.any
        override def effects(self : Card)(implicit g : Game) = $(ChooseCardEffect(Corp, runnerCards, ThreatAssessmentPurpose(self), "Threat Assessment: choose an installed Runner card", false))
        override def chosenOp(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(c => g.push(ChooseOneEffect(Runner, "Threat Assessment: choose one", $(
                Alt("Add the chosen card to the top of the stack", $(ChooseCardEffect(Runner, $(c), TopOfDeckPurpose(Runner), "Threat Assessment", false), CheckMemoryAction)),
                Alt("Take 2 tags", $(tag(2)))))))
            NextAction
        }
    }

    object EconomicWarfare extends RotOp("21036") {
        override def playReq(self : Card)(implicit g : Game) = g.successfulRunsLastTurn.any
        override def effects(self : Card)(implicit g : Game) = $(EconomicWarfareAction(self))
        override def performOp(a : CardAction)(implicit g : Game) = a match {
            case EconomicWarfareAction(self) =>
                if (g.credits(Runner) >= 4)
                    g.push(lose(Runner, 4))
                NextAction
        }
    }

    object DistractTheMasses extends RotOp("21040") {
        override def rfg(self : Card)(implicit g : Game) = true
        override def effects(self : Card)(implicit g : Game) = $(gain(Runner, 2), DistractTheMassesAction(self))
        override def performOp(a : CardAction)(implicit g : Game) = a match {
            case DistractTheMassesAction(self) =>
                if (g.hand(Corp).any)
                    g.push(ChooseCardsEffect(Corp, g.hand(Corp), 0, 2, TrashFromHandPurpose(Corp), "Distract the Masses: choose up to 2 cards in HQ to trash"), DistractTheMassesShuffleAction(self))
                else
                    g.push(DistractTheMassesShuffleAction(self))
                NextAction
            case DistractTheMassesShuffleAction(self) =>
                if (g.discard(Corp).any)
                    g.push(ChooseCardsEffect(Corp, g.discard(Corp), 0, 2, ShuffleIntoDeckPurpose(Corp), "Distract the Masses: choose up to 2 cards in Archives to shuffle into R&D"))
                NextAction
        }
    }

    object ReverseInfection extends RotOp("21053") {
        override def effects(self : Card)(implicit g : Game) = $(ChooseOneEffect(Corp, "Reverse Infection: choose one", $(Alt("Purge virus counters", $(ReverseInfectionAction(self))), Alt("Gain 2 credits", $(gain(Corp, 2))))))
        override def performOp(a : CardAction)(implicit g : Game) = a match {
            case ReverseInfectionAction(self) =>
                val n = (g.installedCards(Runner) ++ g.installedCards(Corp))./(c => g.counter(c, "virus")).sum
                g.pushAll($(PurgeEffect) ++ (n / 3 > 0).$(MillEffect(Runner, n / 3)))
                NextAction
        }
    }

    object DeathAndTaxes extends RotOp("21058") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Installed(Runner, _) if inPlay(self) => $(gain(Corp, 1))
            case Trashed(c, Runner, _) if inPlay(self) && g.inPlayZone(g.lastTrashedFrom) => $(gain(Corp, 1))
            case _ => $
        }
    }

    object TrojanHorse extends RotOp("21059") {
        override def playReq(self : Card)(implicit g : Game) = g.regLast("accessed") > 0
        override def effects(self : Card)(implicit g : Game) = $(MarginTraceEffect(4, $(TrojanHorseAction(self)), $))
        override def performOp(a : CardAction)(implicit g : Game) = a match {
            case TrojanHorseAction(self) =>
                g.pushAll(trashOne(runnerOf("Program").%(c => installedCost(c) <= g.rotTraceMargin), "Trojan Horse: choose a program to trash"))
                NextAction
        }
    }

    object KillSwitch extends RotOp("21070") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Accessed(c) if inPlay(self) && c.data.isAgenda => $(TraceEffect(3, $(core(1)), $))
            case AgendaScored(_) if inPlay(self) => $(TraceEffect(3, $(core(1)), $))
            case _ => $
        }
    }

    object StandardProcedure extends RotOp("21097") {
        override def playReq(self : Card)(implicit g : Game) = g.successfulRunsLastTurn.any
        override def effects(self : Card)(implicit g : Game) = $(ChooseOneEffect(Corp, "Standard Procedure: choose a card type", $("Event", "Hardware", "Program", "Resource")./(k => Alt(k, $(StandardProcedureAction(self, k))))))
        override def performOp(a : CardAction)(implicit g : Game) = a match {
            case StandardProcedureAction(self, k) =>
                val grip = g.hand(Runner)
                g.push(RevealCardsEffect(Runner, grip), gain(Corp, 2 * grip.%(_.data.kind == k).num))
                NextAction
        }
    }

    object RiotSuppression extends RotOp("21113") {
        override def rfg(self : Card)(implicit g : Game) = true
        override def playReq(self : Card)(implicit g : Game) = g.regLast("runner-trash-corp") > 0
        override def effects(self : Card)(implicit g : Game) = $(ChooseOneEffect(Runner, "Riot Suppression: choose one", $(
            Alt("Suffer 1 core damage", $(DamageApplyEffect("core", 1))),
            Alt("Get 3 fewer allotted clicks next turn", $(ExtraClicksEffect(Runner, -3))))))
    }

    object MarketForces extends RotOp("21117") {
        override def playReq(self : Card)(implicit g : Game) = g.tagged
        override def effects(self : Card)(implicit g : Game) = $(MarketForcesAction(self))
        override def performOp(a : CardAction)(implicit g : Game) = a match {
            case MarketForcesAction(self) =>
                val n = (3 * g.tags).atMost(g.credits(Runner))
                g.push(lose(Runner, n), gain(Corp, n))
                NextAction
        }
    }

    object HighProfileTarget extends RotOp("21119") {
        override def playReq(self : Card)(implicit g : Game) = g.tagged
        override def effects(self : Card)(implicit g : Game) = $(meat(2 * g.tags))
    }

    object DivertPower extends RotOp("22030") {
        override def effects(self : Card)(implicit g : Game) = {
            val l = g.installedCards(Corp).%(g.isRezzed)
            $(ChooseCardsEffect(Corp, l, 0, l.num, DivertPowerPurpose(self), "Divert Power: choose any number of cards to derez"))
        }
        override def chosenOp(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case DivertPowerPurpose(self) =>
                g.pushAll(cards./(DerezEffect(_)) :+ DivertPowerRezAction(self, 3 * cards.num))
                NextAction
            case DivertPowerRezPurpose(self, d) =>
                cards.single.foreach(c => g.push(RezDiscountEffect(c, d)))
                NextAction
        }
        override def performOp(a : CardAction)(implicit g : Game) = a match {
            case DivertPowerRezAction(self, d) =>
                val l = g.installedCards(Corp).%(c => !g.isRezzed(c) && !c.data.isAgenda && g.impl(c).canRez(c) && g.canSpend(Corp, (g.rezCost(c) - d).atLeast(0), Spend.rez(c)))
                if (l.any)
                    g.push(ChooseCardEffect(Corp, l, DivertPowerRezPurpose(self, d), "Divert Power: choose a card to rez paying " + d + " credits less", true))
                NextAction
        }
    }

    object FastBreak extends RotOp("22031") {
        def x(implicit g : Game) = g.scored(Runner).num
        override def playReq(self : Card)(implicit g : Game) = x > 0
        override def effects(self : Card)(implicit g : Game) = $(gain(Corp, x), ChooseNumberEffect(Corp, self, 0, x, "fb", "Fast Break: draw how many cards?"))
        def target(self : Card)(implicit g : Game) : ($[Server], Boolean) = g.state(self).memo match {
            case "" => (g.remotes, true)
            case m => (g.serverByName(m).$, false)
        }
        override def performOp(a : CardAction)(implicit g : Game) = a match {
            case NumberPickedAction(self, n, "fb") =>
                g.push(draw(Corp, n), FastBreakStepAction(self, x))
                NextAction
            case FastBreakStepAction(self, left) =>
                val (ss, allowNew) = target(self)
                val l = g.hand(Corp).%(c => corpInstallable(c) && g.rotInstallTargets(c, ss, allowNew, true, 0).any)
                if (left > 0 && (ss.any || allowNew) && l.any)
                    g.push(ChooseCardEffect(Corp, l, FastBreakPurpose(self, left), "Fast Break: install a card in or protecting a single remote server (" + left + " remaining)", true))
                NextAction
        }
        override def chosenOp(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case FastBreakPurpose(self, left) =>
                val (ss, allowNew) = target(self)
                cards.single.foreach(c => g.push(RotInstallEffect(c, ss, allowNew, true, 0, 0, |(self)), FastBreakStepAction(self, left - 1)))
                NextAction
        }
    }

    object GameChanger extends RotOp("22032") {
        override def rfg(self : Card)(implicit g : Game) = true
        override def playReq(self : Card)(implicit g : Game) = g.scored(Runner).any
        override def effects(self : Card)(implicit g : Game) = $(GainClicksEffect(Corp, g.scored(Runner).num))
    }

    object Hangeki extends RotOp("22040") {
        override def playReq(self : Card)(implicit g : Game) = g.regLast("runner-trash-corp") > 0 && g.installedCards(Corp).any
        override def effects(self : Card)(implicit g : Game) = $(ChooseCardEffect(Corp, g.installedCards(Corp), HangekiPurpose(self), "Hangeki: choose an installed card", false))
        override def chosenOp(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            val self = p.card
            cards.single.foreach { c =>
                val s = g.serverOf(c).|(HQ)
                g.push(OptionalEffect(Runner, "Hangeki: access the chosen card (otherwise Hangeki becomes an agenda worth -1 point in your score area)?", $(AccessCardsEffect(s, $(c), false), RemoveFromGameEffect(self)), $(AddToScoreAreaEffect(self, Runner))))
            }
            NextAction
        }
        override def agendaPointsBonus(self : Card)(implicit g : Game) = asAgendaPoints(self, Runner, -1)
    }

    object Eavesdrop extends RotCondition("22047") {
        override def candidates(self : Card)(implicit g : Game) = g.allIce
        override def hostPrompt = "Eavesdrop: choose a piece of ice"
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case EncounterIce(ice) if hosting(self, ice) => $(TraceEffect(3, $(tag(1)), $))
            case _ => $
        }
    }

    object AttitudeAdjustment extends RotOp("22048") {
        override def effects(self : Card)(implicit g : Game) = $(draw(Corp, 2), AttitudeAdjustmentAction(self))
        override def performOp(a : CardAction)(implicit g : Game) = a match {
            case AttitudeAdjustmentAction(self) =>
                val l = (g.hand(Corp) ++ g.discard(Corp)).%(_.data.isAgenda)
                if (l.any)
                    g.push(ChooseCardsEffect(Corp, l, 0, 2, AttitudeAdjustmentPurpose(self), "Attitude Adjustment: choose up to 2 agendas in HQ or Archives to reveal"))
                NextAction
            case AttitudeAdjustmentShuffleAction(self, l) =>
                l.foreach(c => g.moveTo(c, DeckZone(Corp)))
                g.log(Corp, "shuffles", l.num.cards, "into R&D")
                g.push(ShuffleDeckEffect(Corp))
                NextAction
        }
        override def chosenOp(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            if (cards.any)
                g.push(RevealCardsEffect(Corp, cards), gain(Corp, 2 * cards.num), AttitudeAdjustmentShuffleAction(p.card, cards))
            NextAction
        }
    }

    object BuildingBlocks extends RotOp("22055") {
        def cands(implicit g : Game) = g.hand(Corp).%(c => c.data.isIce && g.has(c, "Barrier"))
        override def playReq(self : Card)(implicit g : Game) = cands.any
        override def effects(self : Card)(implicit g : Game) = $(ChooseCardEffect(Corp, cands, BuildingBlocksPurpose(self), "Building Blocks: choose a barrier in HQ to install and rez", false))
        override def chosenOp(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(c => g.push(RevealEffect(Corp, c), RotInstallEffect(c, g.servers, true, false, 2, 0, None)))
            NextAction
        }
    }

    object TooBigToFail extends RotOp("22056") {
        override def playReq(self : Card)(implicit g : Game) = g.credits(Corp) < 10
        override def effects(self : Card)(implicit g : Game) = $(gain(Corp, 7), BadPubEffect(1))
    }

    object UnderTheBus extends RotOp("22057") {
        override def playReq(self : Card)(implicit g : Game) = g.regLast("accessed") > 0
        override def effects(self : Card)(implicit g : Game) = {
            val l = runnerOf("Resource").%(c => g.has(c, "Connection"))
            l.any.$(ChooseCardEffect(Corp, l, TrashPurpose(Corp), "Under the Bus: choose a connection to trash", true)) :+ BadPubEffect(1)
        }
    }

    object BlueLevelClearance extends RotOp("25081") {
        override def effects(self : Card)(implicit g : Game) = $(gain(Corp, 5), draw(Corp, 2))
    }

    object NeuralEMP extends RotOp("25101") {
        override def playReq(self : Card)(implicit g : Game) = g.regLast("runs") > 0
        override def effects(self : Card)(implicit g : Game) = $(net(1))
    }

    object ClosedAccounts extends RotOp("25117") {
        override def playReq(self : Card)(implicit g : Game) = g.tagged
        override def effects(self : Card)(implicit g : Game) = $(ClosedAccountsAction(self))
        override def performOp(a : CardAction)(implicit g : Game) = a match {
            case ClosedAccountsAction(self) =>
                g.push(lose(Runner, g.credits(Runner)))
                NextAction
        }
    }

    object SEASource extends RotOp("25119") {
        override def playReq(self : Card)(implicit g : Game) = g.successfulRunsLastTurn.any
        override def effects(self : Card)(implicit g : Game) = $(TraceEffect(3, $(tag(1)), $))
    }

    object BeanstalkRoyalties extends RotOp("25136") {
        override def effects(self : Card)(implicit g : Game) = $(gain(Corp, 3))
    }

    object OversightAI extends RotCondition("25137") {
        override def candidates(self : Card)(implicit g : Game) = g.allIce.%(c => !g.isRezzed(c))
        override def hostPrompt = "Oversight AI: choose a piece of ice to rez"
        override def hostEffects(self : Card, h : Card)(implicit g : Game) = $(RezEffect(h, true))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SubsBroken(ice, _, _) if hosting(self, ice) && g.unbrokenSubs(ice).none => $(TrashEffect(ice, Corp, false))
            case _ => $
        }
    }

    object IPO extends RotOp("25147") {
        override def effects(self : Card)(implicit g : Game) = $(gain(Corp, 13))
    }

    object SweepsWeek extends RotOp("29013") {
        override def effects(self : Card)(implicit g : Game) = $(gain(Corp, g.hand(Runner).num))
    }

    object ScorchedEarth extends RotOp("29016") {
        override def playReq(self : Card)(implicit g : Game) = g.tagged
        override def effects(self : Card)(implicit g : Game) = $(meat(4))
    }

    val all : $[CardImpl] = $(
        WetworkRefit, HastyRelocation, BestDefense, PreemptiveAction, FriendsInHighPlaces, EnforcedCurfew, VioletLevelClearance,
        Psychokinesis, LoadTesting, Replanting, MCAInformant, Sacrifice, Audacity, RedPlanetCouriers, ShipmentFromTennin, Success,
        MassCommercialization, O2Shortage, BiasedReporting, TransparencyInitiative, RoverAlgorithm, Restore, RollingBrownout,
        ThreatLevelAlpha, PriorityConstruction, UltravioletClearance, HunterSeeker, NetWatchlist, GreenLevelClearance,
        ShipmentFromMirrorMorph, ShipmentFromKaguya, AnonymousTip, Genotyping, SelfGrowthProgram, WakeUpCall, ThreatAssessment,
        EconomicWarfare, DistractTheMasses, ReverseInfection, DeathAndTaxes, TrojanHorse, KillSwitch, StandardProcedure,
        RiotSuppression, MarketForces, HighProfileTarget, DivertPower, FastBreak, GameChanger, Hangeki, Eavesdrop,
        AttitudeAdjustment, BuildingBlocks, TooBigToFail, UnderTheBus, BlueLevelClearance, NeuralEMP, ClosedAccounts, SEASource,
        BeanstalkRoyalties, OversightAI, IPO, SweepsWeek, ScorchedEarth,
    )
}
