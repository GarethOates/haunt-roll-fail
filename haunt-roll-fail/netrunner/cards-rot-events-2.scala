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
// Rotated / FFG-era Runner events (part 2: 23 Seconds .. System Update 2021 era reprints), ported from
// jinteki.net src/clj/game/cards/events.clj
// ---------------------------------------------------------------------------------------------------------------------

// card specific actions and purposes
case class AnotherDayAction(card : Card) extends CardAction
case class DeucesAction(card : Card, done : $[String]) extends CardAction
case class InjectionAction(card : Card, server : Server) extends CardAction
case class InjectionPurpose(card : Card, server : Server) extends CardPurpose
case class CreditCrashAction(card : Card, target : Card) extends CardAction
case class RumorMillAction(card : Card) extends CardAction
case class FranticAction(card : Card) extends CardAction
case class FranticTrashAction(card : Card, cards : $[Card]) extends CardAction
case class OnTheLamPurpose(card : Card) extends CardPurpose
case class ColdReadPurpose(card : Card) extends CardPurpose
case class ExploitPurpose(card : Card) extends CardPurpose
case class ReshapePurpose(card : Card) extends CardPurpose
case class BruteForceAction(card : Card, x : Int) extends CardAction
case class CarefulPlanningPurpose(card : Card) extends CardPurpose
case class DemolitionRunAction(card : Card) extends CardAction
case class InsightRevealAction(card : Card) extends CardAction
case class WhiteHatAction(card : Card) extends CardAction
case class EmbezzleAction(card : Card, kind : String) extends CardAction
case class EmbezzlePurpose(card : Card, kind : String) extends CardPurpose
case class GlutPurpose(card : Card) extends CardPurpose
case class FalsifiedAction(card : Card, kind : String) extends CardAction
case class FalsifiedPurpose(card : Card, kind : String) extends CardPurpose
case class BecauseICanAction(card : Card, server : Server) extends CardAction
case class CompileAction(card : Card, stack : Boolean) extends CardAction
case class CompilePurpose(card : Card, stack : Boolean) extends CardPurpose
case class DianaPurpose(card : Card) extends CardPurpose
case class EmergentPurpose(card : Card) extends CardPurpose
case class RipDealAction(card : Card) extends CardAction
case class RebootPurpose(card : Card) extends CardPurpose
case class RunAmokAction(card : Card) extends CardAction
case class LeaveNoTraceAction(card : Card) extends CardAction
case class HostagePurpose(card : Card) extends CardPurpose
case class QueensGambitAction(card : Card, n : Int) extends CardAction
case class QueensGambitPurpose(card : Card, n : Int) extends CardPurpose
case class QuestCompletedPurpose(card : Card) extends CardPurpose
case class TinkeringPurpose(card : Card) extends CardPurpose
case class LaborRightsAction(card : Card) extends CardAction
case class SpotThePreyPurpose(card : Card) extends CardPurpose
case class ContaminatePurpose(card : Card) extends CardPurpose
case class MadDashAction(card : Card) extends CardAction


object RotEventCards2 {

    object SystemOutage extends RE("11001") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case CardsDrawn(Corp, l) if g.reg("drawn-Corp") > l.num => $(MessageEffect(Runner, "makes the Corp lose 1 credit (System Outage)"), LoseCreditsEffect(Corp, 1))
            case _ => $
        }
    }

    object AnotherDayAnotherPaycheck extends RE("11007") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case AgendaStolen(_) if inPlay(self) => $(AnotherDayAction(self))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case AnotherDayAction(self) =>
                val n = g.agendaPoints(Corp) + g.agendaPoints(Runner)
                g.push(TraceEffect(0, $, $(MessageEffect(Runner, "gains " + n + " credits (Another Day, Another Paycheck)"), gain(Runner, n))))
                NextAction
        }
    }

    object DeucesWild extends RE("11008") {
        override def makesRun(self : Card)(implicit g : Game) = true
        val labels = $("Gain 3 credits", "Draw 2 cards", "Remove 1 tag", "Expose 1 piece of ice and make a run")
        override def onPlay(self : Card)(implicit g : Game) = $(DeucesAction(self, $))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case DeucesAction(self, done) =>
                if (done.num < 2) {
                    def next(l : String) : $[ForcedAction] = (done.num < 1).$(DeucesAction(self, done :+ l))
                    val alts = labels.%!(done.has)./ {
                        case l @ "Gain 3 credits" => Alt(l, gain(Runner, 3) +: next(l))
                        case l @ "Draw 2 cards" => Alt(l, draw(Runner, 2) +: next(l))
                        case l @ "Remove 1 tag" => Alt(l, RemoveTagsEffect(1) +: next(l), g.tagged)
                        case l => Alt(l, g.allIce.any.$(ChooseCardEffect(Runner, g.allIce, ExposePurpose, "Deuces Wild: choose a piece of ice to expose", true)) ++ next(l) :+ runAny(self))
                    }
                    g.push(ChooseOneEffect(Runner, "Deuces Wild: choose an ability to resolve (" + (2 - done.num) + " remaining)", alts))
                }
                NextAction
        }
    }

    object InjectionAttack extends RE("11009") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def canPlay(self : Card)(implicit g : Game) = breakers.any
        override def onPlay(self : Card)(implicit g : Game) = $(ChooseOneEffect(Runner, "Injection Attack: choose a server", g.servers./(s => Alt(s.name, $(InjectionAction(self, s))))))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case InjectionAction(self, s) =>
                if (breakers.any)
                    g.push(ChooseCardEffect(Runner, breakers, InjectionPurpose(self, s), "Injection Attack: choose an icebreaker (+2 strength during the run)", false))
                else
                    g.push(runOn(self, s))
                NextAction
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case InjectionPurpose(self, s) =>
                cards.single.foreach(b => g.push(PumpBreakerEffect(b, 2, RunDuration), runOn(self, s)))
                NextAction
        }
    }

    object CreditCrash extends RE("11021") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = $(runAny(self))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Accessed(c) if thisCardRun(self) && !c.data.isAgenda && !g.zone(c).is[DiscardZone] && !g.state(self).usedRun.has("crash") => $(MarkUsedRunEffect(self, "crash"), CreditCrashAction(self, c))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case CreditCrashAction(self, c) =>
                val cost = (c.data.isAsset || c.data.isUpgrade || c.data.isIce).?(g.rezCost(c)).|(c.data.isOperation.?(g.playCost(c)).|(0))
                if (g.credits(Corp) >= cost)
                    g.push(OptionalEffect(Corp, "Credit Crash: pay " + cost + " credits to prevent the accessed card from being trashed at no cost?", $(LoseCreditsEffect(Corp, cost)), $(MessageEffect(Runner, "trashes the accessed card at no cost (Credit Crash)"), TrashAccessedEffect(c))))
                else
                    g.push(MessageEffect(Runner, "trashes the accessed card at no cost (Credit Crash)"), TrashAccessedEffect(c))
                NextAction
        }
    }

    object RumorMill extends RE("11022") {
        def eligible(c : Card)(implicit g : Game) = c.data.unique && (c.data.isAsset || c.data.isUpgrade) && !g.has(c, "Region")
        override def onPlay(self : Card)(implicit g : Game) = $(RumorMillAction(self))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case RumorMillAction(self) =>
                g.installedCards(Corp).%(eligible).foreach(c => g.addFlag(Flag("disabled", |(c), None, "rumor", 0, $)))
                g.log(Runner, "makes each unique non-region asset and upgrade lose its printed abilities (Rumor Mill)")
                NextAction
        }
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Installed(Corp, c) if eligible(c) && inPlay(self) => $(AddFlagEffect(Flag("disabled", |(c), None, "rumor", 0, $)))
            case _ => $
        }
    }

    object DataBreach extends RE("11028") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = $(runOn(self, RnD))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case RunEnds(RnD, true, _) if thisCardRun(self) && g.state(self).memo == "" => $(OptionalEffect(Runner, "Data Breach: run R&D again?", $(SetMemoEffect(self, "run-again")), $))
            case _ => $
        }
    }

    object FranticCoding extends RE("11062") {
        override def onPlay(self : Card)(implicit g : Game) = g.deck(Runner).any.$(FranticAction(self))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case FranticAction(self) =>
                val l = g.deck(Runner).take(10)
                val programs = installable(l.%(_.data.isProgram), 5)
                if (programs.any)
                    g.push(LookAtEffect(Runner, l, "Frantic Coding: the top cards of the stack"), ChooseCardEffect(Runner, programs, InstallPayingPurpose(5, None, false), "Frantic Coding: install a program paying 5 less? (the other cards are trashed)", true), FranticTrashAction(self, l))
                else
                    g.push(LookAtEffect(Runner, l, "Frantic Coding: the top cards of the stack"), FranticTrashAction(self, l))
                NextAction
            case FranticTrashAction(self, l) =>
                val rest = l.%(c => g.zone(c) == DeckZone(Runner))
                rest.foreach(c => g.trashCard(c, Runner, false))
                NextAction
        }
    }

    object GovernmentInvestigations extends RE("11069") {
        override def onPlay(self : Card)(implicit g : Game) = $(MessageEffect(Runner, "players cannot secretly spend 2 credits (Government Investigations)"))
    }

    object OnTheLam extends RE("11082") {
        def resources(implicit g : Game) = g.runnerInstalledOf("Resource")
        override def canPlay(self : Card)(implicit g : Game) = resources.any
        override def onPlay(self : Card)(implicit g : Game) = resources.any.$(ChooseCardEffect(Runner, resources, OnTheLamPurpose(self), "On the Lam: choose a resource to host it on", false))
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(h => g.push(HostOnEffect(p.card, h, false)))
            NextAction
        }
        override def preventions(self : Card, kind : String, n : Int)(implicit g : Game) =
            g.isInstalled(self).?? {
                if (kind == "tag")
                    $(Ability("avoid up to 3 tags", $(TrashSelfCost), $(Prevent.tags(3))))
                else if (kind == "net" || kind == "meat" || kind == "core")
                    $(Ability("prevent up to 3 damage", $(TrashSelfCost), $(Prevent.damage(3))))
                else
                    $
            }
    }

    object ColdRead extends RE("11083") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def initialCounters(self : Card)(implicit g : Game) = Map("credit" -> 4)
        override def creditsUsableForRun(self : Card)(implicit g : Game) = thisCardRun(self)
        override def onPlay(self : Card)(implicit g : Game) = $(runAny(self))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case RunEnds(_, _, _) if thisCardRun(self) && g.runnerInstalledOf("Program").any =>
                $(ChooseCardEffect(Runner, g.runnerInstalledOf("Program"), ColdReadPurpose(self), "Cold Read: choose an installed program used during the run to trash", false))
            case _ => $
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.foreach(c => g.trashCard(c, Runner, false))
            NextAction
        }
    }

    object Interdiction extends RE("11087") {
        override def onPlay(self : Card)(implicit g : Game) = $(MessageEffect(Runner, "the Corp cannot rez non-ice cards during the Runner's turn (Interdiction)"))
    }

    object Encore extends RE("11107") {
        override def canPlay(self : Card)(implicit g : Game) = centralsRun
        override def onPlay(self : Card)(implicit g : Game) = $(ExtraTurnEffect(Runner), RemoveFromGameEffect(self))
    }

    object PeaceInOurTime extends RE("11109") {
        override def canPlay(self : Card)(implicit g : Game) = firstClick && g.regLast("scored") == 0
        override def onPlay(self : Card)(implicit g : Game) = $(gain(Runner, 10), gain(Corp, 5), MessageEffect(Runner, "cannot make runs this turn (Peace in Our Time)"), AddFlagEffect(Flag("no-run", None, None, "turn", 0, $)))
    }

    object PushingTheEnvelope extends RE("12001") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = (g.hand(Runner).num <= 2).??(breakers./(b => PumpBreakerEffect(b, 2, RunDuration) : ForcedAction)) :+ runAny(self)
    }

    object Exploit extends RE("12004") {
        override def canPlay(self : Card)(implicit g : Game) = centralsRun
        override def onPlay(self : Card)(implicit g : Game) = g.rezzedIce.any.$(ChooseCardsEffect(Runner, g.rezzedIce, 0, 3, DerezPurpose, "Exploit: choose up to 3 pieces of ice to derez"))
    }

    object SpotThePrey extends RE("12005") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = {
            val l = g.installedCards(Corp).%(c => !c.data.isIce && !g.isRezzed(c))
            l.any.$(ChooseCardEffect(Runner, l, ExposePurpose, "Spot the Prey: choose a non-ice card to expose", false)) :+ runAny(self)
        }
    }

    object MadDash extends RE("12008") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def agendaPointsBonus(self : Card)(implicit g : Game) = (g.zone(self) == ScoredZone(Runner)).?(1).|(0)
        override def onPlay(self : Card)(implicit g : Game) = $(runAny(self))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case RunEnds(_, _, true) if thisCardRun(self) => $(MessageEffect(Runner, "adds Mad Dash to the score area as an agenda worth 1 point"), AddToScoreAreaEffect(self, Runner))
            case RunEnds(_, _, false) if thisCardRun(self) => $(MessageEffect(Runner, "suffers 1 meat damage (Mad Dash)"), meat(1))
            case _ => $
        }
    }

    object Mobius extends RE("12024") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = $(runOn(self, RnD))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case RunEnds(RnD, true, _) if thisCardRun(self) && g.state(self).memo == "" => $(OptionalEffect(Runner, "Mobius: run R&D again? (gain 4 credits if successful)", $(SetMemoEffect(self, "run-again")), $))
            case SuccessfulRun(RnD) if thisCardRun(self) && g.state(self).memo == "second" => $(MessageEffect(Runner, "gains 4 credits (Mobius)"), gain(Runner, 4))
            case _ => $
        }
    }

    object SystemSeizure extends RE("12026") {
        override def pumpDuration(self : Card, breaker : Card, d : Duration)(implicit g : Game) = {
            if (g.state(self).target.none && !g.usedThisTurn(self, "seizure") && activeRun.any) {
                g.markUsed(self, "seizure")
                g.state(self).target = |(breaker)
                g.log(breaker, "keeps its strength for the remainder of the run (System Seizure)")
            }
            g.state(self).target.has(breaker).?(RunDuration : Duration).|(d)
        }
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case RunEnds(_, _, _) if g.state(self).target.any => $(SetTargetEffect(self, None))
            case TurnEnds(_) if g.state(self).target.any => $(SetTargetEffect(self, None))
            case _ => $
        }
    }

    object BuildScript extends RE("12028") {
        override def onPlay(self : Card)(implicit g : Game) = $(gain(Runner, 1), draw(Runner, 2))
    }

    object MarsForMartians extends RE("12081") {
        override def canPlay(self : Card)(implicit g : Game) = firstClick
        override def onPlay(self : Card)(implicit g : Game) = {
            val clans = g.runnerInstalledOf("Resource").count(c => g.has(c, "Clan"))
            (clans > 0).$(draw(Runner, clans)) ++ (g.tags > 0).$(gain(Runner, g.tags))
        }
    }

    object LeaveNoTrace extends RE("12083") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = $(runAny(self))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Rezzed(c) if thisCardRun(self) && activeRun.any && c.data.isIce => $(MarkUsedRunEffect(c, "lnt"))
            case RunEnds(_, _, _) if thisCardRun(self) => $(LeaveNoTraceAction(self))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case LeaveNoTraceAction(self) =>
                val l = g.allIce.%(c => g.isRezzed(c) && g.state(c).usedRun.has("lnt"))
                if (l.any)
                    g.log(Runner, "derezzes the ice rezzed during the run (Leave No Trace)")
                l.foreach(c => g.push(DerezEffect(c)))
                NextAction
        }
    }

    object RipDeal extends RE("12084") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = $(runOn(self, HQ))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case BreachServer(HQ) if thisCardRun(self) && g.hand(Corp).any && g.discard(Runner).any =>
                $(OptionalEffect(Runner, "Rip Deal: add cards from the heap to the grip instead of accessing cards in HQ?", $(RipDealAction(self)), $))
            case RunEnds(_, _, _) if thisCardRun(self) => $(RemoveFromGameEffect(self))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case RipDealAction(self) =>
                val n = g.run./(r => 1 + r.accessBonusFor("HQ") + r.accessBonusFor("total")).|(1).atMost(g.hand(Corp).num).atMost(g.discard(Runner).num)
                if (n > 0)
                    g.push(ChooseCardsEffect(Runner, g.discard(Runner), n, n, AddToHandPurpose(Runner, false), "Rip Deal: choose " + n + " card" + (n != 1).??("s") + " in the heap to add to the grip"), SetRunMarkerEffect("no-hand"))
                NextAction
        }
    }

    object LeanAndMean extends RE("12086") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = (g.runnerInstalledOf("Program").num <= 3).??(breakers./(b => PumpBreakerEffect(b, 2, RunDuration) : ForcedAction)) :+ runAny(self)
    }

    object MiningAccident extends RE("12101") {
        override def canPlay(self : Card)(implicit g : Game) = centralRun
        override def onPlay(self : Card)(implicit g : Game) = $(ChooseOneEffect(Corp, "Mining Accident: choose one", $(
            Alt("Pay 5 credits", $(LoseCreditsEffect(Corp, 5)), g.credits(Corp) >= 5),
            Alt("Take 1 bad publicity", $(BadPubEffect(1))))), RemoveFromGameEffect(self))
    }

    object DianasHunt extends RE("12106") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = $(runAny(self))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case EncounterIce(_) if thisCardRun(self) && g.hand(Runner).exists(_.data.isProgram) =>
                $(OptionalEffect(Runner, "Diana's Hunt: install a program from the grip ignoring all costs? (trashed when the run ends)", $(ChooseCardEffect(Runner, g.hand(Runner).%(_.data.isProgram), DianaPurpose(self), "Diana's Hunt: choose a program to install", true)), $))
            case RunEnds(_, _, _) if thisCardRun(self) =>
                val l = g.runnerInstalled.%(c => g.usedThisTurn(c, "diana"))
                l.any.$(MessageEffect(Runner, "trashes the programs installed with Diana's Hunt"), TrashCardsEffect(l, Runner))
            case _ => $
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(c => g.pushAll(g.installRunner(c, 99) :+ MarkUsedEffect(c, "diana")))
            NextAction
        }
    }

    object Reshape extends RE("12107") {
        override def canPlay(self : Card)(implicit g : Game) = unrezzedIce.num >= 2
        override def onPlay(self : Card)(implicit g : Game) = (unrezzedIce.num >= 2).$(ChooseCardsEffect(Runner, unrezzedIce, 2, 2, ReshapePurpose(self), "Reshape: choose 2 unrezzed pieces of ice to swap"))
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards match {
                case $(a, b) => g.push(SwapIceEffect(a, b))
                case _ =>
            }
            NextAction
        }
    }

    object BruteForceHack extends RE("13002") {
        override def canPlay(self : Card)(implicit g : Game) = doubleOk() && g.rezzedIce.any
        override def onPlay(self : Card)(implicit g : Game) =
            doubleCost() ++ g.rezzedIce.any.$(ChooseOneEffect(Runner, "Brute-Force-Hack: choose X (derez a piece of ice with rez cost X or lower)", 0.to(g.credits(Runner)).$./(x => Alt("X = " + x, $(BruteForceAction(self, x)), g.rezzedIce.exists(c => g.rezCost(c) <= x)))))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case BruteForceAction(self, x) =>
                val l = g.rezzedIce.%(c => g.rezCost(c) <= x)
                if (l.any)
                    g.push(LoseCreditsEffect(Runner, x), ChooseCardEffect(Runner, l, DerezPurpose, "Brute-Force-Hack: choose a piece of ice to derez", false))
                else
                    g.push(LoseCreditsEffect(Runner, x))
                NextAction
        }
    }

    object SYNAttack extends RE("13004") {
        override def canPlay(self : Card)(implicit g : Game) = doubleOk()
        override def onPlay(self : Card)(implicit g : Game) = doubleCost() :+ ChooseOneEffect(Corp, "SYN Attack: choose one", $(
            Alt("Discard 2 cards from HQ", $(CorpTrashFromHandEffect(2)), g.hand(Corp).num >= 2),
            Alt("Draw 4 cards", $(draw(Corp, 4)))))
    }

    object CarefulPlanning extends RE("13013") {
        override def canPlay(self : Card)(implicit g : Game) = firstClick
        override def onPlay(self : Card)(implicit g : Game) = {
            val l = g.remotes./~(g.cardsOfServer).%(c => !g.isRezzed(c))
            l.any.$(ChooseCardEffect(Runner, l, CarefulPlanningPurpose(self), "Careful Planning: choose a card in or protecting a remote server (it cannot be rezzed this turn)", false))
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.foreach(c => g.push(MarkUsedEffect(c, "cannot-rez"), MessageEffect(Runner, "chooses a card that cannot be rezzed this turn (Careful Planning)")))
            NextAction
        }
    }

    object DeepDataMining extends RE("13014") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = $(runOn(self, RnD))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(RnD) if thisCardRun(self) => $(AccessBonusEffect(RnD, g.memoryFree.atMost(4).atLeast(0)))
            case _ => $
        }
    }

    object ProcessAutomation extends RE("13023") {
        override def onPlay(self : Card)(implicit g : Game) = $(gain(Runner, 2), draw(Runner, 1))
    }

    object SecurityLeak extends RE("14009") {
        override def onPlay(self : Card)(implicit g : Game) = $(MessageEffect(Runner, "the Corp must pay 1 credit more to advance a card (Security Leak)"))
    }

    object DemolitionRun extends RE("20002") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = $(RunAnyServerEffect(|(self), $(HQ, RnD)))
        override def accessAbility(self : Card, target : Card)(implicit g : Game) =
            (thisCardRun(self) && !g.zone(target).is[DiscardZone] && g.run.exists(!_.cannotStealOrTrash) && !g.accessProtected(target)).?(Ability("trash the accessed card at no cost", NoCost, $(TrashEffect(target, Runner, true))))
    }

    object Singularity extends RE("20004") {
        override def canPlay(self : Card)(implicit g : Game) = doubleOk() && g.remotes.any
        override def makesRun(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = doubleCost() :+ runRemote(self)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(s) if s.isRemote && thisCardRun(self) => $(ReplaceBreachEffect, MessageEffect(Runner, "trashes all cards in the root of the server (Singularity)"), RunnerTrashCardsEffect(g.root(s)))
            case _ => $
        }
    }

    object Infiltration extends RE("20055") {
        override def onPlay(self : Card)(implicit g : Game) = {
            val l = g.installedCards(Corp).%(c => !g.isRezzed(c))
            $(ChooseOneEffect(Runner, "Infiltration: choose one", $(
                Alt("Gain 2 credits", $(gain(Runner, 2))),
                Alt("Expose 1 card", $(ChooseCardEffect(Runner, l, ExposePurpose, "Infiltration: choose a card to expose", false)), l.any))))
        }
    }

    object ByAnyMeans extends RE("21001") {
        override def canPlay(self : Card)(implicit g : Game) = firstClick
        override def lingering(self : Card)(implicit g : Game) = "turn"
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Accessed(c) if inPlay(self) && !g.zone(c).is[DiscardZone] && !g.accessProtected(c) && !g.impl(c).untrashableByRunner(c) =>
                $(MessageEffect(Runner, "trashes the accessed card and suffers 1 meat damage (By Any Means)"), TrashAccessedEffect(c), meat(1))
            case _ => $
        }
    }

    object CreditKiting extends RE("21023") {
        override def canPlay(self : Card)(implicit g : Game) = centralRun
        override def onPlay(self : Card)(implicit g : Game) = {
            val l = installable(g.hand(Runner).%(c => !c.data.isEvent), 8)
            l.any.$(ChooseCardEffect(Runner, l, InstallPayingPurpose(8, None, false), "Credit Kiting: choose a card to install paying 8 less", true)) :+ tag(1)
        }
    }

    object EmergentCreativity extends RE("21028") {
        override def canPlay(self : Card)(implicit g : Game) = doubleOk()
        override def onPlay(self : Card)(implicit g : Game) = {
            val l = g.hand(Runner).%(c => c.data.isProgram || c.data.isHardware)
            doubleCost() :+ ChooseCardsEffect(Runner, l, 0, l.num, EmergentPurpose(self), "Emergent Creativity: choose programs and hardware in the grip to trash (their install costs reduce the next install)")
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            val x = cards./(_.data.cost.|(0)).sum
            cards.foreach(c => g.trashCard(c, Runner, false))
            val l = installable(g.deck(Runner).%(c => c.data.isProgram || c.data.isHardware), x).sortBy(_.title)
            if (l.any)
                g.push(ChooseCardEffect(Runner, l, InstallPayingPurpose(x, None, false), "Emergent Creativity: choose a program or piece of hardware in the stack to install paying " + x + " less", true), ShuffleDeckEffect(Runner))
            else
                g.push(ShuffleDeckEffect(Runner))
            NextAction
        }
    }

    object CorporateGrant extends RE("21044") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Installed(Runner, _) if g.reg("runner-install") == 0 => $(MessageEffect(Runner, "makes the Corp lose 1 credit (Corporate Grant)"), LoseCreditsEffect(Corp, 1))
            case _ => $
        }
    }

    object Marathon extends RE("21046") {
        override def canPlay(self : Card)(implicit g : Game) = g.remotes.any
        override def makesRun(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = $(runRemote(self))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case RunEnds(s, successful, _) if thisCardRun(self) =>
                $(AddFlagEffect(Flag("no-run", None, |(s), "turn", 0, $)), MessageEffect(Runner, "cannot run " + s.name + " again this turn (Marathon)")) ++
                successful.??($(MessageEffect(Runner, "gains a click and adds Marathon to the grip"), GainClicksEffect(Runner, 1), ReturnToHandEffect(self)))
            case _ => $
        }
    }

    object WhiteHat extends RE("21048") {
        override def canPlay(self : Card)(implicit g : Game) = centralRun
        override def onPlay(self : Card)(implicit g : Game) = $(TraceEffect(3, $, $(WhiteHatAction(self))))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case WhiteHatAction(self) =>
                if (g.hand(Corp).any)
                    g.push(RevealCardsEffect(Corp, g.hand(Corp)), ChooseCardsEffect(Runner, g.hand(Corp), 0, 2, ShuffleIntoDeckPurpose(Corp), "White Hat: choose up to 2 revealed cards to shuffle into R&D"))
                NextAction
        }
    }

    object GlutCipher extends RE("21061") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = $(runOn(self, Archives))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(Archives) if thisCardRun(self) =>
                $(ReplaceBreachEffect) ++ (g.discard(Corp).num >= 5).$(ChooseCardsEffect(Corp, g.discard(Corp), 5, 5, GlutPurpose(self), "Glut Cipher: choose 5 cards in Archives to add to HQ (5 random cards from HQ are trashed)"))
            case _ => $
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.foreach(c => g.moveTo(c, HandZone(Corp)))
            g.log(Corp, "adds", cards.num.cards, "from Archives to HQ")
            if (cards.any)
                g.push(RandomPickEffect(Corp, g.hand(Corp), 5, TrashFromHandPurpose(Corp)))
            NextAction
        }
    }

    object FalsifiedCredentials extends RE("21064") {
        override def onPlay(self : Card)(implicit g : Game) = $(ChooseOneEffect(Runner, "Falsified Credentials: name a card type", $("Agenda", "Asset", "Upgrade")./(k => Alt(k, $(FalsifiedAction(self, k))))))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case FalsifiedAction(self, k) =>
                if (remoteRootUnrezzed.any)
                    g.push(ChooseCardEffect(Runner, remoteRootUnrezzed, FalsifiedPurpose(self, k), "Falsified Credentials: choose a card in a remote server to expose", false))
                NextAction
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case FalsifiedPurpose(self, k) =>
                cards.single.foreach(c => g.pushAll($(ExposeEffect($(c))) ++ (c.data.kind == k).??($(MessageEffect(Runner, "named the right type and gains 5 credits"), gain(Runner, 5)))))
                NextAction
        }
    }

    object BecauseICan extends RE("21066") {
        override def canPlay(self : Card)(implicit g : Game) = g.remotes.any
        override def makesRun(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = $(runRemote(self))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(s) if s.isRemote && thisCardRun(self) =>
                $(OptionalEffect(Runner, "Because I Can: instead of breaching the server, shuffle all cards in its root into R&D?", $(ReplaceBreachEffect, BecauseICanAction(self, s)), $))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case BecauseICanAction(self, s) =>
                val l = g.root(s)
                l.foreach(c => g.moveTo(c, DeckZone(Corp)))
                g.cleanupRemotes()
                g.log(Runner, "shuffles", l.num.cards, "from the root of", s, "into R&D")
                if (l.any)
                    g.push(ShuffleDeckEffect(Corp))
                NextAction
        }
    }

    object Contaminate extends RE("21083") {
        def targets(implicit g : Game) = g.runnerInstalled.%(c => g.counter(c, "virus") == 0)
        override def onPlay(self : Card)(implicit g : Game) = targets.any.$(ChooseCardEffect(Runner, targets, ContaminatePurpose(self), "Contaminate: choose an installed card without virus counters", false))
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.foreach(c => g.push(AddCounterEffect(c, "virus", 3)))
            NextAction
        }
    }

    object Embezzle extends RE("21084") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = $(runOn(self, HQ))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(HQ) if thisCardRun(self) =>
                $(ReplaceBreachEffect, ChooseOneEffect(Runner, "Embezzle: name a card type", $("Asset", "ICE", "Operation", "Upgrade")./(k => Alt(k, $(EmbezzleAction(self, k))))))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case EmbezzleAction(self, k) =>
                if (g.hand(Corp).any)
                    g.push(RandomPickEffect(Runner, g.hand(Corp), 2, EmbezzlePurpose(self, k)))
                NextAction
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case EmbezzlePurpose(self, k) =>
                val hits = cards.%(_.data.kind == k)
                hits.foreach(c => g.state(c).seen = true)
                if (hits.any)
                    g.push(RevealCardsEffect(Corp, cards), TrashCardsEffect(hits, Runner), MessageEffect(Runner, "gains " + (4 * hits.num) + " credits (Embezzle)"), gain(Runner, 4 * hits.num))
                else
                    g.push(RevealCardsEffect(Corp, cards))
                NextAction
        }
    }

    object Compile extends RE("21088") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = $(runAny(self))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case EncounterIce(_) if thisCardRun(self) && !g.state(self).usedRun.has("compile") =>
                $(MarkUsedRunEffect(self, "compile"), OptionalEffect(Runner, "Compile: search the stack or heap for a program and install it ignoring all costs?", $(ChooseOneEffect(Runner, "Compile: search where?", $(
                    Alt("Stack", $(CompileAction(self, true)), g.deck(Runner).exists(_.data.isProgram)),
                    Alt("Heap", $(CompileAction(self, false)), g.discard(Runner).exists(_.data.isProgram))))), $))
            case RunEnds(_, _, _) if thisCardRun(self) =>
                g.state(self).target.%(c => g.isInstalled(c)).$./(c => CopyAbilityEffect($(MessageEffect(Runner, "adds the program installed with Compile to the bottom of the stack"), MoveEffect(c, DeckZone(Runner), false))))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case CompileAction(self, stack) =>
                val l = (stack.?(g.deck(Runner)).|(g.discard(Runner))).%(_.data.isProgram).sortBy(_.title)
                if (l.any)
                    g.push(ChooseCardEffect(Runner, l, CompilePurpose(self, stack), "Compile: choose a program to install ignoring all costs", true))
                if (stack)
                    g.push(ShuffleDeckEffect(Runner))
                NextAction
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(c => g.pushAll(g.installRunner(c, 99) :+ SetTargetEffect(p.card, |(c))))
            NextAction
        }
    }

    object DiversionOfFunds extends RE("21105") {
        override def canPlay(self : Card)(implicit g : Game) = doubleOk()
        override def makesRun(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = doubleCost() :+ runOn(self, HQ)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(HQ) if thisCardRun(self) =>
                $(OptionalEffect(Runner, "Diversion of Funds: instead of breaching HQ, force the Corp to lose up to 5 credits (gain 1 per credit)?", $(ReplaceBreachEffect, DrainCreditsEffect(5, 1, 0)), $))
            case _ => $
        }
    }

    object BlackHat extends RE("21110") {
        override def onPlay(self : Card)(implicit g : Game) = $(TraceEffect(4, $, $(SetChoiceEffect(self, "black-hat"), MessageEffect(Runner, "accesses 2 additional cards from HQ and R&D this turn (Black Hat)"))))
        override def lingering(self : Card)(implicit g : Game) = (g.state(self).choice == "black-hat").?("turn").|("")
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case BreachServer(s) if (s == HQ || s == RnD) && inPlay(self) && g.state(self).choice == "black-hat" => $(AccessBonusEffect(s, 2))
            case _ => $
        }
    }

    object DivideAndConquer extends RE("22002") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = $(runOn(self, Archives))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case BreachFinished(Archives) if thisCardRun(self) && g.run.exists(_.successful) => $(MessageEffect(Runner, "breaches HQ and then R&D (Divide and Conquer)"), BreachNoRootEffect(HQ), BreachNoRootEffect(RnD))
            case _ => $
        }
    }

    object GuineaPig extends RE("22003") {
        override def onPlay(self : Card)(implicit g : Game) = $(RunnerTrashCardsEffect(g.hand(Runner)), gain(Runner, 10))
    }

    object HotPursuit extends RE("22009") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = $(runOn(self, HQ))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(HQ) if thisCardRun(self) => $(MessageEffect(Runner, "gains 9 credits and takes 1 tag (Hot Pursuit)"), gain(Runner, 9), tag(1))
            case _ => $
        }
    }

    object Insight extends RE("22016") {
        override def canPlay(self : Card)(implicit g : Game) = doubleOk()
        override def onPlay(self : Card)(implicit g : Game) = doubleCost() ++ g.deck(Corp).any.??($(OptionalEffect(Corp, "Insight: look at the top 4 cards of R&D and arrange them?", $(ArrangeTopEffect(Corp, Corp, 4)), $), InsightRevealAction(self)))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case InsightRevealAction(self) =>
                val l = g.deck(Corp).take(4)
                if (l.any)
                    g.push(RevealCardsEffect(Corp, l))
                NextAction
        }
    }

    object Reboot extends RE("22023") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = $(runOn(self, Archives))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(Archives) if thisCardRun(self) =>
                $(ReplaceBreachEffect) ++ g.discard(Runner).any.$(ChooseCardsEffect(Runner, g.discard(Runner), 0, 5, RebootPurpose(self), "Reboot: choose up to 5 cards in the heap to install facedown"))
            case RunEnds(_, _, _) if thisCardRun(self) => $(RemoveFromGameEffect(self))
            case _ => $
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.foreach(c => g.push(InstallFacedownEffect(c)))
            NextAction
        }
    }

    object OfficeSupplies extends RE("22024") {
        override def playCostMod(self : Card, card : Card)(implicit g : Game) = (card == self).?(-g.link).|(0)
        override def onPlay(self : Card)(implicit g : Game) = $(ChooseOneEffect(Runner, "Office Supplies: choose one", $(Alt("Gain 4 credits", $(gain(Runner, 4))), Alt("Draw 4 cards", $(draw(Runner, 4))))))
    }

    object WatchTheWorldBurn extends RE("23100") {
        override def canPlay(self : Card)(implicit g : Game) = g.remotes.any
        override def makesRun(self : Card)(implicit g : Game) = true
        override def activeInHeap(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = $(runRemote(self), LoseAllClicksEffect(Runner))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Accessed(c) if thisCardRun(self) && g.run.exists(_.successful) && !c.data.isAgenda && !g.state(self).usedRun.has("burn") && g.state(self).memo == "" =>
                $(MarkUsedRunEffect(self, "burn"), SetMemoEffect(self, c.code), MessageEffect(Runner, "removes the accessed card from the game (Watch the World Burn)"), RfgAccessedEffect(c))
            case Accessed(c) if g.state(self).memo != "" && c.code == g.state(self).memo && !g.zone(c).is[DiscardZone] =>
                $(MessageEffect(Runner, "removes the accessed card from the game (Watch the World Burn)"), RfgAccessedEffect(c))
            case _ => $
        }
    }

    object QueensGambit extends RE("25003") {
        override def canPlay(self : Card)(implicit g : Game) = doubleOk()
        override def onPlay(self : Card)(implicit g : Game) = doubleCost() ++ remoteRootUnrezzed.any.$(ChooseOneEffect(Runner, "Queen's Gambit: how many advancement counters to place? (gain 2 credits each)", 0.to(3).$./(n => Alt(n.toString, $(QueensGambitAction(self, n))))))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case QueensGambitAction(self, n) =>
                if (remoteRootUnrezzed.any)
                    g.push(ChooseCardEffect(Runner, remoteRootUnrezzed, QueensGambitPurpose(self, n), "Queen's Gambit: choose an unrezzed card in a remote server (you cannot access it this turn)", false))
                NextAction
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case QueensGambitPurpose(self, n) =>
                cards.single.foreach(c => g.pushAll((n > 0).??($(PlaceAdvancementEffect(c, n), gain(Runner, 2 * n))) :+ MarkUsedEffect(c, "no-access")))
                NextAction
        }
    }

    object QuestCompleted extends RE("25004") {
        override def canPlay(self : Card)(implicit g : Game) = centralsRun
        override def onPlay(self : Card)(implicit g : Game) = {
            val l = g.installedCards(Corp).%(c => !c.data.isIce)
            l.any.$(ChooseCardEffect(Runner, l, QuestCompletedPurpose(self), "Quest Completed: choose an installed non-ice card to access", false))
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(c => g.push(AccessCardsEffect(g.serverOf(c).|(HQ), $(c), false)))
            NextAction
        }
    }

    object RunAmok extends RE("25006") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = $(runAny(self))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Rezzed(c) if thisCardRun(self) && activeRun.any && c.data.isIce => $(MarkUsedRunEffect(c, "amok"))
            case RunEnds(_, _, _) if thisCardRun(self) => $(RunAmokAction(self))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case RunAmokAction(self) =>
                val l = g.allIce.%(c => g.isRezzed(c) && g.state(c).usedRun.has("amok"))
                if (l.any)
                    g.push(ChooseCardEffect(Runner, l, TrashPurpose(Runner), "Run Amok: choose a piece of ice rezzed during the run to trash", false))
                NextAction
        }
    }

    object Stimhack extends RE("25007") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def initialCounters(self : Card)(implicit g : Game) = Map("credit" -> 9)
        override def creditsUsableForRun(self : Card)(implicit g : Game) = thisCardRun(self)
        override def onPlay(self : Card)(implicit g : Game) = $(runAny(self))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case RunEnds(_, _, _) if thisCardRun(self) => $(MessageEffect(Runner, "suffers 1 core damage (Stimhack)"), DamageApplyEffect("core", 1))
            case _ => $
        }
    }

    object EasyMark extends RE("25023") {
        override def onPlay(self : Card)(implicit g : Game) = $(gain(Runner, 3))
    }

    object Hostage extends RE("25025") {
        override def canPlay(self : Card)(implicit g : Game) = doubleOk()
        override def onPlay(self : Card)(implicit g : Game) = {
            val l = g.deck(Runner).%(c => g.has(c, "Connection")).sortBy(_.title)
            doubleCost() ++ l.any.$(ChooseCardEffect(Runner, l, HostagePurpose(self), "Hostage: choose a connection in the stack", true)) :+ ShuffleDeckEffect(Runner)
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach { c =>
                g.log(Runner, "reveals", c, "from the stack and adds it to the grip")
                g.moveTo(c, HandZone(Runner))
                if (runnerCanInstall(c, 0))
                    g.push(OptionalEffect(Runner, "Hostage: install the connection?", g.installRunner(c, 0), $))
            }
            NextAction
        }
    }

    object SpearPhishing extends RE("25029") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = $(runAny(self))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case EncounterIce(ice) if thisCardRun(self) && g.run.exists(r => r.position == 1 && r.ice.has(ice)) => $(MessageEffect(Runner, "bypasses the innermost ice (Spear Phishing)"), BypassEffect)
            case _ => $
        }
    }

    object SpecialOrder extends RE("25030") {
        override def onPlay(self : Card)(implicit g : Game) = {
            val l = g.deck(Runner).%(_.data.isIcebreaker).sortBy(_.title)
            l.any.$(ChooseCardEffect(Runner, l, AddToHandPurpose(Runner, true), "Special Order: choose an icebreaker in the stack to add to the grip", true)) :+ ShuffleDeckEffect(Runner)
        }
    }

    object Modded extends RE("25043") {
        override def onPlay(self : Card)(implicit g : Game) = {
            val l = installable(g.hand(Runner).%(c => c.data.isProgram || c.data.isHardware), 3)
            l.any.$(ChooseCardEffect(Runner, l, InstallPayingPurpose(3, None, false), "Modded: choose a program or piece of hardware to install paying 3 less", true))
        }
    }

    object Notoriety extends RE("25044") {
        override def canPlay(self : Card)(implicit g : Game) = centralsRun
        override def agendaPointsBonus(self : Card)(implicit g : Game) = (g.zone(self) == ScoredZone(Runner)).?(1).|(0)
        override def onPlay(self : Card)(implicit g : Game) = $(AddToScoreAreaEffect(self, Runner))
    }

    object Tinkering extends RE("25047") {
        override def canPlay(self : Card)(implicit g : Game) = g.allIce.any
        override def lingering(self : Card)(implicit g : Game) = g.state(self).target.any.?("turn").|("")
        override def onPlay(self : Card)(implicit g : Game) = g.allIce.any.$(ChooseCardEffect(Runner, g.allIce, TinkeringPurpose(self), "Tinkering: choose a piece of ice (it gains sentry, code gate and barrier this turn)", false))
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(c => g.push(SetTargetEffect(p.card, |(c)), AddSubtypeEffect(c, "Sentry"), AddSubtypeEffect(c, "Code Gate"), AddSubtypeEffect(c, "Barrier")))
            NextAction
        }
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnEnds(_) if inPlay(self) => g.state(self).target.$./~(c => $(RemoveSubtypeEffect(c, "Sentry"), RemoveSubtypeEffect(c, "Code Gate"), RemoveSubtypeEffect(c, "Barrier")))
            case _ => $
        }
    }

    object LaborRights extends RE("28001") {
        override def onPlay(self : Card)(implicit g : Game) = $(TrashTopEffect(Runner, 3, true), LaborRightsAction(self), draw(Runner, 1), RemoveFromGameEffect(self))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case LaborRightsAction(self) =>
                val n = 3.atMost(g.discard(Runner).num)
                if (n > 0)
                    g.push(ChooseCardsEffect(Runner, g.discard(Runner), n, n, ShuffleIntoDeckPurpose(Runner), "Labor Rights: choose " + n + " card" + (n != 1).??("s") + " in the heap to shuffle into the stack"))
                NextAction
        }
    }

    object Indexing extends RE("29005") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = $(runOn(self, RnD))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(RnD) if thisCardRun(self) && g.deck(Corp).num >= 2 =>
                $(OptionalEffect(Runner, "Indexing: instead of breaching R&D, look at the top 5 cards and arrange them?", $(ReplaceBreachEffect, ArrangeTopEffect(Runner, Corp, 5)), $))
            case _ => $
        }
    }

    object LuckyFind extends RE("29007") {
        override def canPlay(self : Card)(implicit g : Game) = doubleOk()
        override def onPlay(self : Card)(implicit g : Game) = doubleCost() :+ gain(Runner, 9)
    }

    val all : $[CardImpl] = $(
        SystemOutage, AnotherDayAnotherPaycheck, DeucesWild, InjectionAttack, CreditCrash, RumorMill, DataBreach, FranticCoding,
        GovernmentInvestigations, OnTheLam, ColdRead, Interdiction, Encore, PeaceInOurTime, PushingTheEnvelope, Exploit, SpotThePrey,
        MadDash, Mobius, SystemSeizure, BuildScript, MarsForMartians, LeaveNoTrace, RipDeal, LeanAndMean, MiningAccident, DianasHunt,
        Reshape, BruteForceHack, SYNAttack, CarefulPlanning, DeepDataMining, ProcessAutomation, SecurityLeak, DemolitionRun,
        Singularity, Infiltration, ByAnyMeans, CreditKiting, EmergentCreativity, CorporateGrant, Marathon, WhiteHat, GlutCipher,
        FalsifiedCredentials, BecauseICan, Contaminate, Embezzle, Compile, DiversionOfFunds, BlackHat, DivideAndConquer, GuineaPig,
        HotPursuit, Insight, Reboot, OfficeSupplies, WatchTheWorldBurn, QueensGambit, QuestCompleted, RunAmok, Stimhack, EasyMark,
        Hostage, SpearPhishing, SpecialOrder, Modded, Notoriety, Tinkering, LaborRights, Indexing, LuckyFind,
    )
}

object RotEventCards {
    val all : $[CardImpl] = RotEventCards1.all ++ RotEventCards2.all
}
