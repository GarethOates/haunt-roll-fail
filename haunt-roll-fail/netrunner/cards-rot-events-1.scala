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
// Rotated / FFG-era Runner events (part 1: Core Set .. The Liberated Mind), ported from jinteki.net src/clj/game/cards/events.clj
// ---------------------------------------------------------------------------------------------------------------------

// card specific actions and purposes
case class DejaVuPurpose(card : Card) extends CardPurpose
case class VampAction(card : Card, x : Int) extends CardAction
case class SurgePurpose(card : Card) extends CardPurpose
case class KrakenAction(card : Card, server : Server) extends CardAction
case class EscherAction(card : Card, n : Int) extends CardAction
case class EscherPurpose(card : Card, n : Int) extends CardPurpose
case class RompPurpose(card : Card) extends CardPurpose
case class FreelancePurpose(card : Card) extends CardPurpose
case class ScavengePurpose(card : Card) extends CardPurpose
case class ScavengeInstallAction(card : Card, discount : Int) extends CardAction
case class LevyARLabAccessAction(card : Card) extends CardAction
case class FrameJobPurpose(card : Card) extends CardPurpose
case class EurekaAction(card : Card) extends CardAction
case class LeverageAction(card : Card) extends CardAction
case class PushLuckResolveAction(card : Card, spent : Int, even : Boolean) extends CardAction
case class MassInstallAction(card : Card, n : Int) extends CardAction
case class MassInstallPurpose(card : Card, n : Int) extends CardPurpose
case class CyberThreatAction(card : Card, server : Server) extends CardAction
case class CyberThreatRunAction(card : Card, server : Server, rezzed : Int) extends CardAction
case class SocialEngineeringPurpose(card : Card) extends CardPurpose
case class NetCelebrityAction(card : Card) extends CardAction
case class InjectAction(card : Card) extends CardAction
case class TradeInPurpose(card : Card) extends CardPurpose
case class CodeSiphonPurpose(card : Card, discount : Int) extends CardPurpose
case class WantonAction(card : Card, n : Int) extends CardAction
case class ImmolationPurpose(card : Card) extends CardPurpose
case class DriveByPurpose(card : Card) extends CardPurpose
case class ApocalypseAction(card : Card) extends CardAction
case class PreyPurpose(card : Card, ice : Card) extends CardPurpose
case class IndependentThinkingPurpose(card : Card) extends CardPurpose
case class EmployeeStrikeAction(card : Card) extends CardAction
case class WindfallAction(card : Card) extends CardAction
case class GraffitiPurpose(card : Card) extends CardPurpose
case class MakingEntranceAction(card : Card) extends CardAction
case class MakingEntranceArrangeAction(card : Card, cards : $[Card]) extends CardAction
case class InfoSiftingAction(card : Card) extends CardAction
case class InfoSiftingPurpose(card : Card) extends CardPurpose
case class RebirthPurpose(card : Card) extends CardPurpose
case class FearMassesAction(card : Card) extends CardAction
case class FearMassesPurpose(card : Card) extends CardPurpose
case class RiggedResolveAction(card : Card, spent : Int, guess : Int) extends CardAction
case class RiggedIcePurpose(card : Card) extends CardPurpose


object RotEventCards1 {

    object DejaVu extends RE("01002") {
        override def onPlay(self : Card)(implicit g : Game) = g.discard(Runner).any.$(ChooseCardEffect(Runner, g.discard(Runner), DejaVuPurpose(self), "Deja Vu: choose a card in the heap to add to the grip", true))
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach { c =>
                g.log(Runner, "adds", c, "to the grip")
                g.moveTo(c, HandZone(Runner))
                val viruses = g.discard(Runner).%(x => g.has(x, "Virus"))
                if (g.has(c, "Virus") && viruses.any)
                    g.push(ChooseCardEffect(Runner, viruses, AddToHandPurpose(Runner, false), "Deja Vu: choose another virus card to add to the grip", true))
            }
            NextAction
        }
    }

    object AccountSiphon extends RE("01018") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = $(runOn(self, HQ))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(HQ) if thisCardRun(self) =>
                $(OptionalEffect(Runner, "Account Siphon: instead of breaching HQ, force the Corp to lose up to 5 credits (gain 2 per credit, take 2 tags)?", $(ReplaceBreachEffect, DrainCreditsEffect(5, 2, 2)), $))
            case _ => $
        }
    }

    object Vamp extends RE("02021") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = $(runOn(self, HQ))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(HQ) if thisCardRun(self) && g.credits(Runner) > 0 && g.credits(Corp) > 0 =>
                $(OptionalEffect(Runner, "Vamp: instead of breaching HQ, spend X credits to make the Corp lose X credits (and take 1 tag)?", $(ReplaceBreachEffect, ChooseOneEffect(Runner, "Vamp: choose X", 1.to(g.credits(Runner).atMost(g.credits(Corp))).$./(x => Alt("X = " + x, $(VampAction(self, x)))))), $))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case VampAction(self, x) =>
                g.push(LoseCreditsEffect(Runner, x), LoseCreditsEffect(Corp, x), tag(1))
                NextAction
        }
    }

    object SatelliteUplink extends RE("02023") {
        override def onPlay(self : Card)(implicit g : Game) = {
            val l = g.installedCards(Corp).%(c => !g.isRezzed(c))
            l.any.$(ChooseCardsEffect(Runner, l, 0, 2, ExposePurpose, "Satellite Uplink: choose up to 2 cards to expose"))
        }
    }

    object Surge extends RE("02081") {
        def targets(implicit g : Game) = g.installedCards(Runner).%(c => c.data.isProgram && g.counter(c, "virus") > 0 && g.usedThisTurn(c, "virus-placed"))
        override def canPlay(self : Card)(implicit g : Game) = targets.any
        override def onPlay(self : Card)(implicit g : Game) = targets.any.$(ChooseCardEffect(Runner, targets, SurgePurpose(self), "Surge: choose a program that got virus counters this turn", false))
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.foreach(c => g.push(AddCounterEffect(c, "virus", 2)))
            NextAction
        }
    }

    object QualityTime extends RE("02087") {
        override def onPlay(self : Card)(implicit g : Game) = $(draw(Runner, 5))
    }

    object Kraken extends RE("02090") {
        override def canPlay(self : Card)(implicit g : Game) = g.reg("stolen") > 0
        override def onPlay(self : Card)(implicit g : Game) = {
            val l = g.servers.%(s => g.ice(s).any)
            l.any.$(ChooseOneEffect(Runner, "Kraken: choose a server", l./(s => Alt(s.name, $(KrakenAction(self, s))))))
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case KrakenAction(self, s) =>
                if (g.ice(s).any)
                    g.push(ChooseCardEffect(Corp, g.ice(s), TrashPurpose(Corp), "Kraken: choose a piece of ice protecting " + s.name + " to trash", false))
                NextAction
        }
    }

    object Escher extends RE("03031") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = $(runOn(self, HQ))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(HQ) if thisCardRun(self) => $(ReplaceBreachEffect, MessageEffect(Runner, "rearranges the ice (Escher)"), EscherAction(self, 3 * g.allIce.num))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case EscherAction(self, n) =>
                if (n > 0 && g.allIce.num >= 2)
                    g.push(ChooseCardsEffect(Runner, g.allIce, 0, 2, EscherPurpose(self, n), "Escher: choose 2 pieces of ice to swap (fewer to finish)"))
                NextAction
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case EscherPurpose(self, n) =>
                cards match {
                    case $(a, b) => g.push(SwapIceEffect(a, b), EscherAction(self, n - 1))
                    case _ => g.log(Runner, "has finished rearranging ice")
                }
                NextAction
        }
    }

    object ExploratoryRomp extends RE("03032") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = $(runAny(self))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(s) if thisCardRun(self) =>
                val l = g.cardsOfServer(s).%(c => g.advancement(c) > 0)
                $(ReplaceBreachEffect) ++ l.any.$(ChooseCardEffect(Runner, l, RompPurpose(self), "Exploratory Romp: choose a card to remove up to 3 advancement counters from", false))
            case _ => $
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(c => g.push(ChooseOneEffect(Runner, "Exploratory Romp: how many advancement counters to remove?", 0.to(3.atMost(g.advancement(c))).$./(n => Alt(n.toString, $(RemoveAdvancementEffect(c, n)))))))
            NextAction
        }
    }

    object FreelanceCodingContract extends RE("03033") {
        override def onPlay(self : Card)(implicit g : Game) = {
            val l = g.hand(Runner).%(_.data.isProgram)
            l.any.$(ChooseCardsEffect(Runner, l, 0, 5, FreelancePurpose(self), "Freelance Coding Contract: choose up to 5 programs in the grip to trash (2 credits each)"))
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.foreach(c => g.trashCard(c, Runner, false))
            if (cards.any)
                g.push(gain(Runner, 2 * cards.num))
            NextAction
        }
    }

    object Scavenge extends RE("03034") {
        def programs(implicit g : Game) = g.runnerInstalledOf("Program")
        override def canPlay(self : Card)(implicit g : Game) = programs.any
        override def onPlay(self : Card)(implicit g : Game) = programs.any.$(ChooseCardEffect(Runner, programs, ScavengePurpose(self), "Scavenge: choose an installed program to trash", false))
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach { c =>
                val x = c.data.cost.|(0)
                g.trashCard(c, Runner, false)
                g.push(ScavengeInstallAction(p.card, x))
            }
            NextAction
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case ScavengeInstallAction(self, x) =>
                val l = installable((g.hand(Runner) ++ g.discard(Runner)).%(_.data.isProgram), x)
                if (l.any)
                    g.push(ChooseCardEffect(Runner, l, InstallPayingPurpose(x, None, false), "Scavenge: choose a program from the grip or heap to install, paying " + x + " less", true))
                NextAction
        }
    }

    object LevyARLabAccess extends RE("03035") {
        override def onPlay(self : Card)(implicit g : Game) = $(LevyARLabAccessAction(self), ShuffleDeckEffect(Runner), draw(Runner, 5), RemoveFromGameEffect(self))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case LevyARLabAccessAction(self) =>
                (g.hand(Runner) ++ g.discard(Runner)).foreach(c => g.moveTo(c, DeckZone(Runner)))
                g.log(Runner, "shuffles the grip and the heap into the stack")
                NextAction
        }
    }

    object FrameJob extends RE("04001") {
        override def canPlay(self : Card)(implicit g : Game) = doubleOk() && g.scored(Runner).exists(_.data.isAgenda)
        override def onPlay(self : Card)(implicit g : Game) = {
            val l = g.scored(Runner).%(_.data.isAgenda)
            doubleCost() ++ l.any.$(ChooseCardEffect(Runner, l, FrameJobPurpose(self), "Frame Job: choose an agenda to forfeit", false))
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(c => g.push(ForfeitEffect(c), BadPubEffect(1)))
            NextAction
        }
    }

    object Recon extends RE("04024") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = $(runAny(self))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case EncounterIce(_) if thisCardRun(self) && !g.state(self).usedRun.has("recon") => $(MarkUsedRunEffect(self, "recon"), JackOutOfferEffect)
            case _ => $
        }
    }

    object Eureka extends RE("04027") {
        override def canPlay(self : Card)(implicit g : Game) = doubleOk()
        override def onPlay(self : Card)(implicit g : Game) = doubleCost() ++ g.deck(Runner).any.$(EurekaAction(self))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case EurekaAction(self) =>
                g.deck(Runner).starting.foreach { c =>
                    if (runnerCanInstall(c, 10))
                        g.push(RevealCardsEffect(Runner, $(c)), OptionalEffect(Runner, "Eureka!: install the revealed card paying 10 less?", g.installRunner(c, 10), $(TrashEffect(c, Runner, false))))
                    else
                        g.push(RevealCardsEffect(Runner, $(c)), TrashEffect(c, Runner, false))
                }
                NextAction
        }
    }

    object RunningInterference extends RE("04044") {
        override def canPlay(self : Card)(implicit g : Game) = doubleOk()
        override def makesRun(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = doubleCost() :+ runAny(self)
        override def rezCostMod(self : Card, card : Card)(implicit g : Game) = (card.data.isIce && thisCardRun(self) && activeRun.any).?(card.data.cost.|(0)).|(0)
    }

    object LawyerUp extends RE("04063") {
        override def canPlay(self : Card)(implicit g : Game) = doubleOk()
        override def onPlay(self : Card)(implicit g : Game) = doubleCost() ++ $(RemoveTagsEffect(2), draw(Runner, 3))
    }

    object Leverage extends RE("04064") {
        override def canPlay(self : Card)(implicit g : Game) = g.successfulRunsThisTurn.has(HQ)
        override def onPlay(self : Card)(implicit g : Game) =
            $(OptionalEffect(Corp, "Leverage: take 2 bad publicity? (otherwise all damage to the Runner is prevented until their next turn begins)", $(BadPubEffect(2)), $(LeverageAction(self))))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case LeverageAction(self) =>
                g.state(self).choice = "prevent"
                g.log(Runner, "prevents all damage until the next turn begins (Leverage)")
                NextAction
        }
        override def lingering(self : Card)(implicit g : Game) = (g.state(self).choice == "prevent").?("runner-begins").|("")
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case PreventionInterrupt(k, 1, n) if g.damageKinds.has(k) && g.state(self).choice == "prevent" && inPlay(self) =>
                $(MessageEffect(Runner, "prevents " + n + " " + k + " damage (Leverage)"), Prevent.allDamage)
            case _ => $
        }
    }

    object ExecutiveWiretaps extends RE("04084") {
        override def canPlay(self : Card)(implicit g : Game) = doubleOk()
        override def onPlay(self : Card)(implicit g : Game) = doubleCost() ++ g.hand(Corp).any.$(RevealCardsEffect(Corp, g.hand(Corp)))
    }

    object Blackmail extends RE("04089") {
        override def canPlay(self : Card)(implicit g : Game) = g.badPublicity > 0
        override def makesRun(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = $(MessageEffect(Runner, "the Corp cannot rez ice during this run (Blackmail)"), AddFlagEffect(Flag("no-ice-rez", None, None, "run", 0, $)), runAny(self))
    }

    object PowerNap extends RE("04107") {
        override def canPlay(self : Card)(implicit g : Game) = doubleOk()
        override def onPlay(self : Card)(implicit g : Game) = doubleCost() :+ gain(Runner, 2 + g.discard(Runner).count(c => c.data.has("Double")))
    }

    object CallingInFavors extends RE("05031") {
        override def onPlay(self : Card)(implicit g : Game) = $(gain(Runner, g.activeCards(Runner).count(c => c.data.isResource && g.has(c, "Connection") && g.isInstalled(c))))
    }

    object EarlyBird extends RE("05032") {
        override def canPlay(self : Card)(implicit g : Game) = firstClick
        override def makesRun(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = $(GainClicksEffect(Runner, 1), runAny(self))
    }

    object ExpressDelivery extends RE("05033") {
        override def onPlay(self : Card)(implicit g : Game) = g.deck(Runner).any.$(ChooseCardEffect(Runner, g.deck(Runner).take(4), AddToHandPurpose(Runner, false), "Express Delivery: choose 1 of the top 4 cards to add to the grip", false), ShuffleDeckEffect(Runner))
    }

    object Feint extends RE("05034") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = $(runOn(self, HQ))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case EncounterIce(_) if thisCardRun(self) && g.counter(self, "bypass") < 2 => $(AddCounterEffect(self, "bypass", 1), BypassEffect)
            case SuccessfulRun(HQ) if thisCardRun(self) => $(MessageEffect(Runner, "cannot breach HQ (Feint)"), ReplaceBreachEffect)
            case _ => $
        }
    }

    object PlannedAssault extends RE("05036") {
        override def canPlay(self : Card)(implicit g : Game) = doubleOk()
        override def onPlay(self : Card)(implicit g : Game) = {
            val l = g.deck(Runner).%(c => g.has(c, "Run") && c.data.isEvent && g.canSpend(Runner, g.playCost(c), Spend.play(c))).sortBy(_.title)
            doubleCost() ++ l.any.$(ChooseCardEffect(Runner, l, PlayPurpose(self, true, false), "Planned Assault: choose a run event in the stack to play", true)) :+ ShuffleDeckEffect(Runner)
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(c => g.push(PlayCardEffect(Runner, c, true, false)))
            NextAction
        }
    }

    object PushYourLuck extends RE("05047") {
        override def onPlay(self : Card)(implicit g : Game) =
            $(SecretSpendEffect(self, 99))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case SecretSpentAction(self, n) =>
                g.push(ChooseOneEffect(Corp, "Push Your Luck: did the Runner spend an even or an odd amount?", $(Alt("Even", $(PushLuckResolveAction(self, n, true))), Alt("Odd", $(PushLuckResolveAction(self, n, false))))))
                NextAction
            case PushLuckResolveAction(self, n, even) =>
                g.credits += Runner -> (g.credits(Runner) - n)
                g.log(Runner, "spent", n.credits)
                if ((n % 2 == 0) == even)
                    g.log(Corp, "guessed correctly")
                else {
                    g.log(Corp, "guessed incorrectly")
                    g.push(gain(Runner, 2 * n))
                }
                NextAction
        }
    }

    object MassInstall extends RE("05051") {
        override def onPlay(self : Card)(implicit g : Game) = $(MassInstallAction(self, 0))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case MassInstallAction(self, n) =>
                val l = installable(g.hand(Runner).%(_.data.isProgram), 0)
                if (n < 3 && l.any)
                    g.push(ChooseCardEffect(Runner, l, MassInstallPurpose(self, n), "Mass Install: choose a program to install (" + (3 - n) + " remaining)", true))
                NextAction
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case MassInstallPurpose(self, n) =>
                cards.single.foreach(c => g.pushAll(g.installRunner(c, 0) :+ MassInstallAction(self, n + 1)))
                NextAction
        }
    }

    object CyberThreat extends RE("06013") {
        override def canPlay(self : Card)(implicit g : Game) = firstClick
        override def makesRun(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = $(ChooseOneEffect(Runner, "Cyber Threat: choose a server", g.servers./(s => Alt(s.name, $(CyberThreatAction(self, s))))))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case CyberThreatAction(self, s) =>
                val l = g.ice(s).%(c => !g.isRezzed(c) && g.canSpend(Corp, g.rezCost(c), Spend.rez(c)) && g.impl(c).canRez(c))
                val rezzed = g.ice(s).count(g.isRezzed)
                if (l.any)
                    g.push(OptionalEffect(Corp, "Cyber Threat: rez a piece of ice protecting " + s.name + "? (otherwise the Runner runs it and no ice can be rezzed)", $(ChooseCardEffect(Corp, l, RezPurpose(false), "Cyber Threat: choose a piece of ice to rez", false)), $), CyberThreatRunAction(self, s, rezzed))
                else
                    g.push(CyberThreatRunAction(self, s, rezzed))
                NextAction
            case CyberThreatRunAction(self, s, rezzed) =>
                if (g.servers.has(s) && g.ice(s).count(g.isRezzed) > rezzed)
                    g.log(Corp, "rezzed a piece of ice: no run")
                else
                    g.push(MessageEffect(Runner, "runs " + s.name + ": the Corp cannot rez ice during this run (Cyber Threat)"), AddFlagEffect(Flag("no-ice-rez", None, None, "run", 0, $)), runOn(self, s))
                NextAction
        }
    }

    object PaperTripping extends RE("06015") {
        override def canPlay(self : Card)(implicit g : Game) = firstClick
        override def onPlay(self : Card)(implicit g : Game) = $(RemoveTagsEffect(g.tags))
    }

    object SocialEngineering extends RE("06018") {
        override def canPlay(self : Card)(implicit g : Game) = firstClick
        override def lingering(self : Card)(implicit g : Game) = "turn"
        override def onPlay(self : Card)(implicit g : Game) = unrezzedIce.any.$(ChooseCardEffect(Runner, unrezzedIce, SocialEngineeringPurpose(self), "Social Engineering: choose an unrezzed piece of ice", false))
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(c => g.push(SetTargetEffect(p.card, |(c))))
            NextAction
        }
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Rezzed(c) if g.state(self).target.has(c) && inPlay(self) => $(MessageEffect(Runner, "gains the rez cost of the ice (Social Engineering)"), gain(Runner, g.rezCost(c)))
            case _ => $
        }
    }

    object Scrubbed extends RE("06034") {
        override def iceStrengthMod(self : Card, ice : Card)(implicit g : Game) = (g.state(self).target.has(ice) && activeRun.any).?(-2).|(0)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case EncounterIce(ice) if !g.usedThisTurn(self, "scrubbed") => $(MarkUsedEffect(self, "scrubbed"), SetTargetEffect(self, |(ice)), MessageEffect(Runner, "gives the ice -2 strength for the run (Scrubbed)"))
            case RunEnds(_, _, _) if g.state(self).target.any => $(SetTargetEffect(self, None))
            case _ => $
        }
    }

    object ThreeStepsAhead extends RE("06035") {
        override def canPlay(self : Card)(implicit g : Game) = firstClick
        override def lingering(self : Card)(implicit g : Game) = "turn"
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnEnds(Runner) if inPlay(self) && g.successfulRunsThisTurn.any => $(MessageEffect(Runner, "gains 2 credits per successful run (Three Steps Ahead)"), gain(Runner, 2 * g.successfulRunsThisTurn.num))
            case _ => $
        }
    }

    object UnscheduledMaintenance extends RE("06036") {
        override def onPlay(self : Card)(implicit g : Game) = $(MessageEffect(Runner, "the Corp cannot install more than 1 piece of ice each turn (Unscheduled Maintenance)"))
    }

    object NetCelebrity extends RE("06038") {
        override def initialCounters(self : Card)(implicit g : Game) = Map("credit" -> 1)
        override def creditsUsableForRun(self : Card)(implicit g : Game) = inPlay(self)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) if g.counter(self, "credit") < 1 => $(AddCounterEffect(self, "credit", 1 - g.counter(self, "credit")))
            case _ => $
        }
    }

    object Inject extends RE("06073") {
        override def onPlay(self : Card)(implicit g : Game) = g.deck(Runner).any.$(InjectAction(self))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case InjectAction(self) =>
                val l = g.deck(Runner).take(4)
                val programs = l.%(_.data.isProgram)
                val others = l.%!(_.data.isProgram)
                g.log(Runner, "reveals", l./(g.cardElem).comma, "from the top of the stack")
                programs.foreach(c => g.trashCard(c, Runner, false))
                others.foreach { c =>
                    g.log(Runner, "adds", c, "to the grip")
                    g.moveTo(c, HandZone(Runner))
                }
                if (programs.any)
                    g.push(gain(Runner, programs.num))
                NextAction
        }
    }

    object TradeIn extends RE("06078") {
        def hardware(implicit g : Game) = g.runnerInstalledOf("Hardware")
        override def canPlay(self : Card)(implicit g : Game) = hardware.any
        override def onPlay(self : Card)(implicit g : Game) = hardware.any.$(ChooseCardEffect(Runner, hardware, TradeInPurpose(self), "Trade-In: choose an installed piece of hardware to trash", false))
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach { c =>
                val v = c.data.cost.|(0) / 2
                g.trashCard(c, Runner, false)
                val l = g.deck(Runner).%(_.data.isHardware).sortBy(_.title)
                g.pushAll($(gain(Runner, v)) ++ l.any.??($(ChooseCardEffect(Runner, l, AddToHandPurpose(Runner, true), "Trade-In: choose a piece of hardware in the stack to add to the grip", true), ShuffleDeckEffect(Runner))))
            }
            NextAction
        }
    }

    object CodeSiphon extends RE("06115") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = $(runOn(self, RnD))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(RnD) if thisCardRun(self) =>
                val d = 3 * g.ice(RnD).num
                val l = installable(g.deck(Runner).%(_.data.isProgram), d).sortBy(_.title)
                l.any.$(OptionalEffect(Runner, "Code Siphon: instead of breaching R&D, install a program from the stack paying " + d + " less and take 1 tag?", $(ReplaceBreachEffect, ChooseCardEffect(Runner, l, CodeSiphonPurpose(self, d), "Code Siphon: choose a program to install", false), ShuffleDeckEffect(Runner)), $))
            case _ => $
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case CodeSiphonPurpose(self, d) =>
                cards.single.foreach(c => g.pushAll(g.installRunner(c, d) :+ tag(1)))
                NextAction
        }
    }

    object Bribery extends RE("06118") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) =
            $(ChooseOneEffect(Runner, "Bribery: choose X (additional rez cost of the first unrezzed ice approached)", 0.to(g.credits(Runner)).$./(x => Alt("X = " + x, $(LoseCreditsEffect(Runner, x), SetCounterEffect(self, "x", x))))), runAny(self))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case ApproachIce(ice) if thisCardRun(self) && !g.isRezzed(ice) && g.state(self).target.none => $(SetTargetEffect(self, |(ice)))
            case _ => $
        }
        override def rezCostMod(self : Card, card : Card)(implicit g : Game) = (g.state(self).target.has(card) && thisCardRun(self) && activeRun.any).?(g.counter(self, "x")).|(0)
    }

    object AmpedUp extends RE("07031") {
        override def onPlay(self : Card)(implicit g : Game) = $(GainClicksEffect(Runner, 3), DamageApplyEffect("core", 1))
    }

    object IveHadWorse extends RE("07032") {
        override def onPlay(self : Card)(implicit g : Game) = $(draw(Runner, 3))
    }

    object ItinerantProtesters extends RE("07033") {
        override def handSize(self : Card, f : Faction)(implicit g : Game) = f.isCorp.?(-g.badPublicity).|(0)
    }

    object ShowingOff extends RE("07034") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = $(runOn(self, RnD))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(RnD) if thisCardRun(self) => $(MessageEffect(Runner, "accesses cards from the bottom of R&D (Showing Off)"), SetRunMarkerEffect("bottom"))
            case _ => $
        }
    }

    object WantonDestruction extends RE("07035") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = $(runOn(self, HQ))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(HQ) if thisCardRun(self) && g.clicks(Runner) > 0 && g.hand(Corp).any =>
                $(OptionalEffect(Runner, "Wanton Destruction: instead of breaching HQ, spend clicks to make the Corp trash that many cards from HQ at random?", $(ReplaceBreachEffect, ChooseOneEffect(Runner, "Wanton Destruction: how many clicks?", 1.to(g.clicks(Runner)).$./(n => Alt(n + " click" + (n != 1).??("s"), $(WantonAction(self, n)))))), $))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case WantonAction(self, n) =>
                g.push(LoseClicksEffect(Runner, n), RandomTrashFromHandEffect(Corp, n))
                NextAction
        }
    }

    object DayJob extends RE("07036") {
        override def canPlay(self : Card)(implicit g : Game) = doubleOk(3)
        override def onPlay(self : Card)(implicit g : Game) = doubleCost(3) :+ gain(Runner, 10)
    }

    abstract class Cutlery(code : String, subtype : String) extends RE(code) {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = $(runAny(self))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SubsBroken(ice, _, _) if thisCardRun(self) && g.has(ice, subtype) && g.unbrokenSubs(ice).none && !g.state(self).usedRun.has("cutlery") =>
                $(MarkUsedRunEffect(self, "cutlery"), MessageEffect(Runner, "trashes the fully broken ice (" + g.asciiTitle(self) + ")"), TrashEffect(ice, Runner, false))
            case _ => $
        }
    }

    object Forked extends Cutlery("07037", "Sentry")
    object Knifed extends Cutlery("07038", "Barrier")
    object Spooned extends Cutlery("07039", "Code Gate")

    object Uninstall extends RE("07053") {
        override def onPlay(self : Card)(implicit g : Game) = {
            val l = g.runnerInstalled.%(c => (c.data.isProgram || c.data.isHardware) && !g.state(c).facedown)
            l.any.$(ChooseCardEffect(Runner, l, ReturnToHandPurpose, "Uninstall: choose a program or piece of hardware to add to the grip", false))
        }
    }

    object TrafficJam extends RE("08008") {
        override def advancementRequirementMod(self : Card, agenda : Card)(implicit g : Game) = g.scored(Corp).count(_.title == agenda.title)
    }

    object HacktivistMeeting extends RE("08021") {
        override def onPlay(self : Card)(implicit g : Game) = $(MessageEffect(Runner, "the Corp must trash a random card from HQ to rez a non-ice card (Hacktivist Meeting)"))
    }

    object GameDay extends RE("08026") {
        override def canPlay(self : Card)(implicit g : Game) = doubleOk()
        override def onPlay(self : Card)(implicit g : Game) = doubleCost() ++ (g.handSize(Runner) > g.hand(Runner).num).$(draw(Runner, g.handSize(Runner) - g.hand(Runner).num))
    }

    object ImmolationScript extends RE("08041") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = $(runOn(self, Archives))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            // remember the ice that is faceup in Archives before the breach turns everything faceup
            case BreachServer(Archives) if thisCardRun(self) && g.run.exists(r => !r.accessOnly) =>
                $(SetMemoEffect(self, g.discard(Corp).%(c => c.data.isIce && g.state(c).seen)./(c => c.code + "-" + c.n).mkString(",")))
            // "whenever you would access a faceup piece of ice in Archives, you may instead trash 1 rezzed copy" (once per run)
            case Accessed(c) if thisCardRun(self) && c.data.isIce && g.zone(c) == DiscardZone(Corp) && g.state(self).memo.split(',').contains(c.code + "-" + c.n) && !g.state(self).usedRun.has("immolation") && g.rezzedIce.exists(_.title == c.title) =>
                $(OptionalEffect(Runner, "Immolation Script: instead of accessing " + c.title.filter(_ < 128) + ", trash a rezzed copy of it?", $(MarkRunUsedEffect(self, "immolation"), ChooseCardEffect(Runner, g.rezzedIce.%(_.title == c.title), TrashPurpose(Runner), "Immolation Script: choose a rezzed copy to trash", false)), $))
            case _ => $
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach { c =>
                val copies = g.rezzedIce.%(_.title == c.title)
                if (copies.any)
                    g.push(ChooseCardEffect(Runner, copies, TrashPurpose(Runner), "Immolation Script: choose a rezzed copy to trash", false))
            }
            NextAction
        }
    }

    object DriveBy extends RE("08064") {
        override def canPlay(self : Card)(implicit g : Game) = doubleOk()
        override def onPlay(self : Card)(implicit g : Game) = doubleCost() ++ remoteRootUnrezzed.any.$(ChooseCardEffect(Runner, remoteRootUnrezzed, DriveByPurpose(self), "Drive By: choose a card in the root of a remote server to expose", false))
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach { c =>
                g.pushAll($(ExposeEffect($(c))) ++ (c.data.isAsset || c.data.isUpgrade).??($(MessageEffect(Runner, "trashes the exposed card (Drive By)"), TrashEffect(c, Runner, false))))
            }
            NextAction
        }
    }

    object PowerToThePeople extends RE("08101") {
        override def canPlay(self : Card)(implicit g : Game) = firstClick
        override def lingering(self : Card)(implicit g : Game) = "turn"
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Accessed(c) if c.data.isAgenda && inPlay(self) && !g.usedThisTurn(self, "pttp") => $(MarkUsedEffect(self, "pttp"), MessageEffect(Runner, "gains 7 credits (Power to the People)"), gain(Runner, 7))
            case _ => $
        }
    }

    object FiskInvestmentSeminar extends RE("08105") {
        override def canPlay(self : Card)(implicit g : Game) = firstClick
        override def onPlay(self : Card)(implicit g : Game) = $(draw(Runner, 3), draw(Corp, 3))
    }

    object Apocalypse extends RE("09030") {
        override def canPlay(self : Card)(implicit g : Game) = centralsRun
        override def onPlay(self : Card)(implicit g : Game) = $(ApocalypseAction(self))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case ApocalypseAction(self) =>
                g.log(Runner, "trashes all installed Corp cards (Apocalypse)")
                val corp = g.installedCards(Corp)
                corp.foreach(c => if (g.isInstalled(c)) g.trashCard(c, Runner, false))
                g.push(TurnRigFacedownEffect(g.installedCards(Runner)))
                NextAction
        }
    }

    object Prey extends RE("09031") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = $(runAny(self))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case PassIce(ice) if thisCardRun(self) && g.isRezzed(ice) && !g.state(self).usedRun.has("prey") && g.iceStrength(ice) <= g.runnerInstalled.num =>
                val n = g.iceStrength(ice)
                if (n <= 0)
                    $(OptionalEffect(Runner, "Prey: trash the passed ice?", $(MarkUsedRunEffect(self, "prey"), TrashEffect(ice, Runner, false)), $))
                else
                    $(OptionalEffect(Runner, "Prey: trash " + n + " installed cards to trash the passed ice?", $(MarkUsedRunEffect(self, "prey"), ChooseCardsEffect(Runner, g.runnerInstalled, n, n, PreyPurpose(self, ice), "Prey: choose " + n + " installed cards to trash")), $))
            case _ => $
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case PreyPurpose(self, ice) =>
                cards.foreach(c => g.trashCard(c, Runner, false))
                if (cards.any && g.isInstalled(ice))
                    g.push(TrashEffect(ice, Runner, false))
                NextAction
        }
    }

    object IndependentThinking extends RE("09038") {
        override def onPlay(self : Card)(implicit g : Game) = g.runnerInstalled.any.$(ChooseCardsEffect(Runner, g.runnerInstalled, 0, 5, IndependentThinkingPurpose(self), "Independent Thinking: choose up to 5 installed cards to trash (draw 1 per card, 2 if a directive is trashed)"))
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            val directive = cards.exists(c => !g.state(c).facedown && g.has(c, "Directive"))
            cards.foreach(c => g.trashCard(c, Runner, false))
            if (cards.any)
                g.push(draw(Runner, cards.num * directive.?(2).|(1)))
            NextAction
        }
    }

    object EmployeeStrike extends RE("09053") {
        override def onPlay(self : Card)(implicit g : Game) = $(EmployeeStrikeAction(self))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case EmployeeStrikeAction(self) =>
                g.identity.get(Corp).foreach { id =>
                    g.addFlag(Flag("disabled", |(id), None, "strike", 0, $))
                    g.log(id, "loses its printed abilities (Employee Strike)")
                }
                NextAction
        }
    }

    object Windfall extends RE("09054") {
        override def onPlay(self : Card)(implicit g : Game) = g.deck(Runner).any.$(ShuffleDeckEffect(Runner), WindfallAction(self))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case WindfallAction(self) =>
                g.deck(Runner).starting.foreach { c =>
                    val n = c.data.isEvent.?(0).|(c.data.cost.|(0))
                    g.trashCard(c, Runner, false)
                    if (n > 0)
                        g.push(gain(Runner, n))
                }
                NextAction
        }
    }

    object HighStakesJob extends RE("10004") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def canPlay(self : Card)(implicit g : Game) = g.servers.exists(s => g.ice(s).exists(c => !g.isRezzed(c)))
        override def onPlay(self : Card)(implicit g : Game) = $(RunAnyServerEffect(|(self), g.servers.%(s => g.ice(s).exists(c => !g.isRezzed(c)))))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case RunEnds(_, true, _) if thisCardRun(self) => $(MessageEffect(Runner, "gains 12 credits (High-Stakes Job)"), gain(Runner, 12))
            case _ => $
        }
    }

    object CBIRaid extends RE("10022") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = $(runOn(self, HQ))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(HQ) if thisCardRun(self) => $(ReplaceBreachEffect, MessageEffect(Runner, "forces the Corp to add all cards in HQ to the top of R&D (CBI Raid)"), CorpAddAllToTopEffect(g.hand(Corp)))
            case _ => $
        }
    }

    object CorporateScandal extends RE("10025") {
        override def onPlay(self : Card)(implicit g : Game) = $(FxAdditionalBadPubEffect(1))
    }

    object PopulistRally extends RE("10026") {
        override def canPlay(self : Card)(implicit g : Game) = g.activeCards(Runner).exists(c => g.has(c, "Seedy") && g.isInstalled(c))
        override def onPlay(self : Card)(implicit g : Game) = $(ExtraClicksEffect(Corp, -1))
    }

    object PoliticalGraffiti extends RE("10039") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = $(runOn(self, Archives))
        override def agendaPointsBonus(self : Card)(implicit g : Game) = g.state(self).host.exists(h => g.zone(h) == ScoredZone(Corp)).?(-1).|(0)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(Archives) if thisCardRun(self) =>
                val l = g.scored(Corp).%(_.data.isAgenda)
                $(ReplaceBreachEffect) ++ l.any.$(ChooseCardEffect(Runner, l, GraffitiPurpose(self), "Political Graffiti: choose an agenda in the Corp's score area to host it on (worth 1 less point)", false))
            case _ => $
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(c => g.push(HostOnEffect(p.card, c, true), CheckWinAction))
            NextAction
        }
    }

    object FreedomThroughEquality extends RE("10045") {
        override def agendaPointsBonus(self : Card)(implicit g : Game) = (g.zone(self) == ScoredZone(Runner)).?(1).|(0)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case AgendaStolen(_) if inPlay(self) => $(MessageEffect(Runner, "adds the current to the score area as an agenda worth 1 point"), AddToScoreAreaEffect(self, Runner))
            case _ => $
        }
    }

    object MakingAnEntrance extends RE("10058") {
        override def canPlay(self : Card)(implicit g : Game) = firstClick
        override def onPlay(self : Card)(implicit g : Game) = g.deck(Runner).any.$(MakingEntranceAction(self))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case MakingEntranceAction(self) =>
                val l = g.deck(Runner).take(6)
                g.push(LookAtEffect(Runner, l, "Making an Entrance: the top cards of the stack"), ChooseCardsEffect(Runner, l, 0, l.num, TrashPurpose(Runner), "Making an Entrance: choose any number of these cards to trash"), MakingEntranceArrangeAction(self, l))
                NextAction
            case MakingEntranceArrangeAction(self, l) =>
                val n = l.count(c => g.zone(c) == DeckZone(Runner))
                if (n > 1)
                    g.push(ArrangeTopEffect(Runner, Runner, n))
                NextAction
        }
    }

    object ExclusiveParty extends RE("10060") {
        override def onPlay(self : Card)(implicit g : Game) = $(draw(Runner, 1), gain(Runner, g.discard(Runner).count(_.title == self.title)))
    }

    object TheNoblePath extends RE("10077") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = $(RunnerTrashCardsEffect(g.hand(Runner)), runAny(self))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case PreventionInterrupt(k, 1, n) if g.damageKinds.has(k) && thisCardRun(self) && activeRun.any =>
                $(MessageEffect(Runner, "prevents " + n + " " + k + " damage (The Noble Path)"), Prevent.allDamage)
            case _ => $
        }
    }

    object InformationSifting extends RE("10079") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = $(runOn(self, HQ))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(HQ) if thisCardRun(self) => $(ReplaceBreachEffect, InfoSiftingAction(self))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case InfoSiftingAction(self) =>
                val h = g.hand(Corp)
                if (h.num == 1)
                    g.push(AccessCardsEffect(HQ, h, false))
                else if (h.num > 1)
                    g.push(ChooseCardsEffect(Corp, h, 0, h.num - 1, InfoSiftingPurpose(self), "Information Sifting: choose the cards of the first pile (the rest form the second pile)"))
                NextAction
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            val p1 = cards
            val p2 = g.hand(Corp).%!(cards.has)
            g.push(ChooseOneEffect(Runner, "Information Sifting: choose a pile to access", $(
                Alt("Pile 1 (" + p1.num + " cards)", $(AccessCardsEffect(HQ, p1, false)), p1.any),
                Alt("Pile 2 (" + p2.num + " cards)", $(AccessCardsEffect(HQ, p2, false)), p2.any))))
            NextAction
        }
    }

    object OutOfTheAshes extends RE("10080") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def activeInHeap(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = $(runAny(self))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) if g.zone(self) == DiscardZone(Runner) => $(OptionalEffect(Runner, "Out of the Ashes: remove it from the game to make a run?", $(RemoveFromGameEffect(self), runAny(self)), $))
            case _ => $
        }
    }

    object Rebirth extends RE("10083") {
        def candidates(implicit g : Game) : $[String] = g.identity.get(Runner).$./~ { id =>
            Cards.all.%(c => c.isIdentity && c.side == "Runner" && c.faction == id.data.faction && !c.code.startsWith("00") && c.title != id.title && c.legal.any)./(_.code)
        }
        override def canPlay(self : Card)(implicit g : Game) = candidates.any
        override def onPlay(self : Card)(implicit g : Game) = $(ChooseCodeEffect(Runner, candidates, "Rebirth: choose an identity of the same faction", RebirthPurpose(self)), RemoveFromGameEffect(self))
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(c => g.push(SwitchIdentityEffect(Runner, c.code)))
            NextAction
        }
    }

    object FearTheMasses extends RE("10096") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = $(runOn(self, HQ))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(HQ) if thisCardRun(self) => $(ReplaceBreachEffect, MessageEffect(Runner, "forces the Corp to trash the top card of R&D (Fear the Masses)"), MillEffect(Corp, 1), FearMassesAction(self))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case FearMassesAction(self) =>
                val copies = g.hand(Runner).%(_.title == self.title)
                if (copies.any)
                    g.push(ChooseCardsEffect(Runner, copies, 0, copies.num, FearMassesPurpose(self), "Fear the Masses: reveal copies from the grip (the Corp trashes 1 more card from R&D per copy)"))
                NextAction
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            if (cards.any)
                g.push(RevealCardsEffect(Runner, cards), MillEffect(Corp, cards.num))
            NextAction
        }
    }

    object ThePriceOfFreedom extends RE("10100") {
        def connections(implicit g : Game) = g.runnerInstalledOf("Resource").%(c => g.has(c, "Connection"))
        override def canPlay(self : Card)(implicit g : Game) = connections.any
        override def lingering(self : Card)(implicit g : Game) = "corp-begins"
        override def lingerEnd(self : Card)(implicit g : Game) = RemoveFromGameEffect(self)
        override def onPlay(self : Card)(implicit g : Game) = connections.any.$(ChooseCardEffect(Runner, connections, TrashPurpose(Runner), "The Price of Freedom: choose a connection to trash", false)) :+ MessageEffect(Runner, "the Corp cannot advance cards during their next turn (The Price of Freedom)")
    }

    object RiggedResults extends RE("10102") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = $(SecretSpendEffect(self, 2))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case SecretSpentAction(self, n) =>
                g.push(ChooseOneEffect(Corp, "Rigged Results: how many credits did the Runner spend?", 0.to(2).$./(k => Alt(k.toString, $(RiggedResolveAction(self, n, k))))))
                NextAction
            case RiggedResolveAction(self, n, k) =>
                g.credits += Runner -> (g.credits(Runner) - n)
                g.log(Runner, "spent", n.credits, "and", Corp, "guessed", k.hl)
                if (n != k && g.allIce.any)
                    g.push(ChooseCardEffect(Runner, g.allIce, RiggedIcePurpose(self), "Rigged Results: choose a piece of ice to bypass and run its server", false))
                NextAction
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(ice => g.serverOf(ice).foreach(s => g.push(SetTargetEffect(p.card, |(ice)), runOn(p.card, s))))
            NextAction
        }
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case EncounterIce(ice) if thisCardRun(self) && g.state(self).target.has(ice) && !g.state(self).usedRun.has("rigged") => $(MarkUsedRunEffect(self, "rigged"), BypassEffect)
            case _ => $
        }
    }

    val all : $[CardImpl] = $(
        DejaVu, AccountSiphon, Vamp, SatelliteUplink, Surge, QualityTime, Kraken, Escher, ExploratoryRomp, FreelanceCodingContract,
        Scavenge, LevyARLabAccess, FrameJob, Recon, Eureka, RunningInterference, LawyerUp, Leverage, ExecutiveWiretaps, Blackmail,
        PowerNap, CallingInFavors, EarlyBird, ExpressDelivery, Feint, PlannedAssault, PushYourLuck, MassInstall, CyberThreat,
        PaperTripping, SocialEngineering, Scrubbed, ThreeStepsAhead, UnscheduledMaintenance, NetCelebrity, Inject, TradeIn,
        CodeSiphon, Bribery, AmpedUp, IveHadWorse, ItinerantProtesters, ShowingOff, WantonDestruction, DayJob, Forked, Knifed,
        Spooned, Uninstall, TrafficJam, HacktivistMeeting, GameDay, ImmolationScript, DriveBy, PowerToThePeople,
        FiskInvestmentSeminar, Apocalypse, Prey, IndependentThinking, EmployeeStrike, Windfall, HighStakesJob, CBIRaid,
        CorporateScandal, PopulistRally, PoliticalGraffiti, FreedomThroughEquality, MakingAnEntrance, ExclusiveParty, TheNoblePath,
        InformationSifting, OutOfTheAshes, Rebirth, FearTheMasses, ThePriceOfFreedom, RiggedResults,
    )
}
