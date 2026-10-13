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
// Parhelion (ph) card implementations, ported from jinteki.net src/clj/game/cards/*.clj
// ---------------------------------------------------------------------------------------------------------------------

abstract class PT(c : String) extends SG(c) {
    // the ice currently encountered
    def encounterIce(implicit g : Game) : |[Card] = g.run.%(r => !r.ended && r.phase == EncounterPhase)./~(_.ice)

    // a run against the server of this card is in progress
    def runOnThisServer(self : Card)(implicit g : Game) = g.run.exists(r => !r.ended && g.serverOf(self).has(r.server))

    // this breaker just broke the last unbroken subroutine of the ice
    def fullyBroke(self : Card, e : Event)(implicit g : Game) = e match {
        case SubsBroken(ice, _, b) => b == self && g.unbrokenSubs(ice).none
        case _ => false
    }

    def runnerCanInstall(c : Card, discount : Int)(implicit g : Game) =
        g.credits(Runner) >= (g.installCost(c, None) - discount).atLeast(0) && (!c.data.isProgram || g.memoryFree >= c.data.memory.|(0)) && (!g.impl(c).mustBeHosted(c) || g.allIce.exists(h => g.impl(c).hostRequirement(c, h)))

    def clickOrEtr(label : String)(implicit g : Game) =
        ChooseOneEffect(Runner, label, $(Alt("Spend [click]", $(LoseClicksEffect(Runner, 1)), g.clicks(Runner) >= 1), Alt("End the run", $(EndRunEffect))))
}

// card specific actions and purposes
case class TimeBombAction(card : Card) extends CardAction
case class AbaasyPurpose(card : Card) extends CardPurpose
case class HushPurpose(card : Card) extends CardPurpose
case class ConcertoAction(card : Card) extends CardAction
case class ReprisePurpose(card : Card) extends CardPurpose
case class AsmundSearchAction(card : Card, remaining : Int) extends CardAction
case class AsmundPurpose(card : Card, remaining : Int) extends CardPurpose
case class AsmundCheckAction(card : Card) extends CardAction
case class SparkAction(card : Card) extends CardAction
case class WorldTreePurpose(card : Card) extends CardPurpose
case class WorldTreeInstallPurpose(card : Card) extends CardPurpose
case class MatryoshkaHostPurpose(card : Card) extends CardPurpose
case class MatryoshkaFlipAction(card : Card) extends CardAction
case class MatryoshkaFaceupAction(card : Card) extends CardAction
case class NightmareScoreAction(card : Card) extends CardAction
case class BloopPurpose(card : Card) extends CardPurpose
case class IssuaqWinCheckAction(card : Card) extends CardAction
case class HafrunTrashPurpose(card : Card) extends CardPurpose
case class HafrunTargetPurpose(card : Card) extends CardPurpose
case class SimResetPurpose(card : Card) extends CardPurpose
case class NanisivikPurpose(card : Card) extends CardPurpose
case class GaslightAction(card : Card) extends CardAction
case class VeraAction(card : Card) extends CardAction
case class KlevetnikPurpose(card : Card) extends CardPurpose
case class KimberlitePurpose(card : Card) extends CardPurpose
case class HostileArchitectureAction(card : Card) extends CardAction
case class BoreholeAction(card : Card) extends CardAction
case class AnvilPurpose(card : Card) extends CardPurpose
case class YakovAction(card : Card) extends CardAction


object PHCards {
    // ------------------------------------------------------------------------------------------------------------
    // Runner
    // ------------------------------------------------------------------------------------------------------------

    object Finality extends PT("33066") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = $(MessageEffect(Runner, "suffers 1 core damage (Finality)"), core(1), RunEffect(RnD, |(self)))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(RnD) if thisCardRun(self) => $(AccessBonusEffect(RnD, 3))
            case _ => $
        }
    }

    object KatorgaBreakout extends PT("33067") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = $(RunAnyServerEffect(|(self), g.servers))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(_) if thisCardRun(self) && g.discard(Runner).any =>
                $(ChooseCardEffect(Runner, g.discard(Runner).sortBy(_.title), AddToHandPurpose(Runner, false), "Katorga Breakout: choose a card in the heap to add to the grip", true))
            case _ => $
        }
    }

    object RaindropsCutStone extends PT("33068") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = $(RunAnyServerEffect(|(self), g.servers))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case RunEnds(_, _, _) if thisCardRun(self) =>
                val n = g.run./(_.subsFired).|(0)
                $(MessageEffect(Runner, "uses Raindrops Cut Stone")) ++ (n > 0).$(draw(Runner, n)) ++ $(gain(Runner, 3))
            case _ => $
        }
    }

    object TimeBomb extends PT("33069") {
        override def canInstall(self : Card)(implicit g : Game) = g.successfulRunsThisTurn.exists(_.isCentral)
        override def initialCounters(self : Card)(implicit g : Game) = Map("power" -> 1)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) => $(TimeBombAction(self))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case TimeBombAction(self) =>
                if (g.counter(self, "power") >= 3) {
                    g.log(self, "explodes")
                    g.trashCard(self, Runner, false)
                    g.push(SabotageEffect(3))
                }
                else
                    g.push(AddCounterEffect(self, "power", 1))
                NextAction
        }
    }

    object Abaasy extends PT("33070") {
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(1), 1, $("Code Gate")))
        override def pumps(self : Card)(implicit g : Game) = $(Pump(c(2), 2))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SubsBroken(_, _, _) if fullyBroke(self, e) && !g.usedThisTurn(self, "abaasy") && g.hand(Runner).any =>
                $(MarkUsedEffect(self, "abaasy"), OptionalEffect(Runner, "Abaasy: trash 1 card from the grip to draw 1 card?", $(ChooseCardEffect(Runner, g.hand(Runner), AbaasyPurpose(self), "Abaasy: choose a card in the grip to trash", false)), $))
            case _ => $
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.foreach { c =>
                g.trashCard(c, Runner, false)
                g.push(draw(Runner, 1))
            }
            NextAction
        }
    }

    object Hush extends PT("33071") {
        override def mustBeHosted(self : Card)(implicit g : Game) = true
        override def hostRequirement(self : Card, host : Card)(implicit g : Game) = host.data.isIce
        override def disablesHost(self : Card)(implicit g : Game) = true
        override def abilities(self : Card)(implicit g : Game) = {
            val l = g.allIce.%(i => !g.state(self).host.has(i))
            $(Ability("host Hush on another piece of ice", click(), $(ChooseCardEffect(Runner, l, HushPurpose(self), "Hush: choose a piece of ice to host it on", false)), click = true, ok = l.any, reason = "no other ice"))
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.foreach { h =>
                g.moveTo(p.card, HostedZone(h))
                g.log(p.card, "is now hosted on", h)
            }
            NextAction
        }
    }

    object Nga extends PT("33072") {
        override def initialCounters(self : Card)(implicit g : Game) = Map("power" -> 3)
        override def triggers(self : Card, e : Event)(implicit g : Game) = trashOnEmpty(self, e, "power") ++ (e match {
            case SuccessfulRun(_) if g.reg("successful-run") == 1 && g.counter(self, "power") > 0 =>
                $(OptionalEffect(Runner, "Nga: remove 1 hosted power counter to sabotage 1?", $(AddCounterEffect(self, "power", -1), SabotageEffect(1)), $))
            case _ => $
        })
    }

    object Num extends PT("33073") {
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(2), 1, $("Sentry")))
    }

    object Bankhar extends PT("33074") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) => $(ChooseServerFlagEffect(Runner, "bankhar-server", |(self), "turn", "Bankhar: choose a server", true))
            case EncounterIce(ice) if !g.usedThisTurn(self, "bankhar") && g.flagsOf("bankhar-server").exists(f => f.card.has(self) && f.server.any && f.server == g.serverOf(ice)) =>
                $(MarkUsedEffect(self, "bankhar"), MessageEffect(Runner, "uses Bankhar: the subroutines become Do 1 net damage"), AddFlagEffect(g.encounterFlag("net-subs", ice)))
            case _ => $
        }
    }

    object Concerto extends PT("33075") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def creditsUsableForRun(self : Card)(implicit g : Game) = thisCardRun(self)
        override def onPlay(self : Card)(implicit g : Game) = $(ConcertoAction(self), RunAnyServerEffect(|(self), g.servers))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case ConcertoAction(self) =>
                g.deck(Runner).starting.foreach { c =>
                    val n = c.data.cost.|(0)
                    g.log(Runner, "reveals", c, "from the top of the stack and adds it to the grip")
                    g.addCounter(self, "credit", n)
                    g.log(n.credits, "placed on", self)
                    g.moveTo(c, HandZone(Runner))
                }
                NextAction
        }
    }

    object Reprise extends PT("33076") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def canPlay(self : Card)(implicit g : Game) = g.reg("stolen") > 0
        override def onPlay(self : Card)(implicit g : Game) = {
            val l = g.installedCards(Corp)
            l.any.$(ChooseCardEffect(Runner, l, ReprisePurpose(self), "Reprise: choose an installed Corp card to add to HQ", true)) :+ OptionalEffect(Runner, "Reprise: run a server?", $(RunAnyServerEffect(|(self), g.servers)), $)
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.foreach { c =>
                g.log(Runner, "adds", g.hiddenCorpElem(c), "to HQ")
                g.moveTo(c, HandZone(Corp))
                g.cleanupRemotes()
            }
            NextAction
        }
    }

    object PoisonVial extends PT("33077") {
        override def initialCounters(self : Card)(implicit g : Game) = Map("power" -> 3)
        override def triggers(self : Card, e : Event)(implicit g : Game) = trashOnEmpty(self, e, "power")
        override def breaks(self : Card)(implicit g : Game) =
            encounterIce.%(i => g.state(i).brokenSubs.any).$./(i => BreakAbility($(CounterCost("power", 1)), 2, $(), |("break up to 2 subroutines (1 hosted power counter)")))
    }

    object WakeImplant extends PT("33078") {
        override def onInstall(self : Card)(implicit g : Game) = $(meat(1))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(HQ) => $(AddCounterEffect(self, "power", 1))
            case BreachServer(RnD) if g.counter(self, "power") > 0 =>
                val m = g.counter(self, "power").atMost(3)
                $(ChooseOneEffect(Runner, "WAKE Implant: remove power counters to access additional cards from R&D?", 1.to(m).$./(k => Alt("Remove " + k + " to access " + k + " additional cards", $(AddCounterEffect(self, "power", -k), AccessBonusEffect(RnD, k)))) :+ Alt("Keep the counters", $)))
            case _ => $
        }
    }

    object ZenitChip extends PT("33079") {
        override def onInstall(self : Card)(implicit g : Game) = $(core(1))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(s) if s.isCentral && g.successfulRunsThisTurn.%(_.isCentral).num == 1 => $(MessageEffect(Runner, "uses Zenit Chip JZ-2MJ"), draw(Runner, 1))
            case _ => $
        }
    }

    object Tremolo extends PT("33080") {
        override def breaks(self : Card)(implicit g : Game) = {
            val n = g.installedCards(Runner).%(c => c.data.isHardware && g.has(c, "Cybernetic")).num
            $(BreakAbility(c((3 - n).atLeast(0)), 2, $("Barrier"), |("break up to 2 barrier subroutines")))
        }
        override def pumps(self : Card)(implicit g : Game) = $(Pump(c(2), 2))
    }

    object TunnelVision extends PT("33081") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) => $(IdentifyMarkEffect)
            case _ => $
        }
        override def breaks(self : Card)(implicit g : Game) = g.run.exists(r => g.mark.has(r.server)).$(BreakAbility(c(2), 2, $(), |("break up to 2 subroutines on ice protecting the mark")))
        override def pumps(self : Card)(implicit g : Game) = $(Pump(c(2), 2))
    }

    object AsmundPudlat extends PT("33082") {
        override def onInstall(self : Card)(implicit g : Game) = $(AsmundSearchAction(self, 2))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) if g.stashed(self).any =>
                $(OptionalEffect(Runner, "Asmund Pudlat: add a hosted card to the grip?", $(ChooseCardEffect(Runner, g.stashed(self), AddToHandPurpose(Runner, false), "Asmund Pudlat: choose a hosted card", false), AsmundCheckAction(self)), $(AsmundCheckAction(self))))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case AsmundSearchAction(self, n) =>
                val hostedTitles = g.stashed(self)./(_.title)
                val l = g.deck(Runner).%(c => (c.data.isVirus || c.data.has("Weapon")) && !hostedTitles.has(c.title)).sortBy(_.title)
                if (l.any)
                    g.push(ChooseCardEffect(Runner, l, AsmundPurpose(self, n), "Asmund Pudlat: choose a virus or weapon card from the stack to host", true))
                else
                    g.push(ShuffleDeckEffect(Runner))
                NextAction
            case AsmundCheckAction(self) =>
                if (g.stashed(self).none && g.isInstalled(self))
                    g.trashCard(self, Runner, false)
                NextAction
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case AsmundPurpose(self, n) =>
                cards.single match {
                    case Some(c) =>
                        if (n > 1)
                            g.push(StashEffect(c, self), AsmundSearchAction(self, n - 1))
                        else
                            g.push(StashEffect(c, self), ShuffleDeckEffect(Runner))
                    case None =>
                        g.push(ShuffleDeckEffect(Runner))
                }
                NextAction
        }
    }

    object InfoBounty extends PT("33083") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) => $(IdentifyMarkEffect)
            case RunEnds(s, _, _) if g.mark.has(s) && !g.usedThisTurn(self, "bounty") && g.run.exists(_.didAccess) => $(MarkUsedEffect(self, "bounty"), MessageEffect(Runner, "uses Info Bounty"), gain(Runner, 2))
            case _ => $
        }
    }

    object SparkOfInspiration extends PT("33084") {
        override def onPlay(self : Card)(implicit g : Game) = $(SparkAction(self))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case SparkAction(self) =>
                val d = g.deck(Runner)
                val before = d.takeWhile(c => !c.data.isProgram)
                d.drop(before.num).starting match {
                    case Some(p) =>
                        g.log(Runner, "sets aside", (before :+ p)./(g.cardElem).comma)
                        if (runnerCanInstall(p, 10))
                            g.push(OptionalEffect(Runner, "Spark of Inspiration: install the program paying 10 credits less?", $(RunnerInstallDiscountEffect(p, 10), ShuffleDeckEffect(Runner)), $(ShuffleDeckEffect(Runner))))
                        else
                            g.push(ShuffleDeckEffect(Runner))
                    case None =>
                        g.log(Runner, "sets aside the whole stack and finds no program")
                        g.push(ShuffleDeckEffect(Runner))
                }
                NextAction
        }
    }

    object HippocampicMechanocytes extends PT("33085") {
        override def initialCounters(self : Card)(implicit g : Game) = Map("power" -> 2)
        override def onInstall(self : Card)(implicit g : Game) = $(meat(1))
        override def handSize(self : Card, f : Faction)(implicit g : Game) = f.isRunner.?(g.counter(self, "power")).|(0)
    }

    object BasilarSynthgland extends PT("33086") {
        override def onInstall(self : Card)(implicit g : Game) = $(core(2))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) => $(MessageEffect(Runner, "has 1 additional allotted click (Basilar Synthgland)"), GainClicksEffect(Runner, 1))
            case _ => $
        }
    }

    object FluxCapacitor extends PT("33087") {
        override def mustBeHosted(self : Card)(implicit g : Game) = true
        override def hostRequirement(self : Card, host : Card)(implicit g : Game) = host.data.isIce
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SubsBroken(ice, _, _) if g.state(self).host.has(ice) && !g.hasFlag("flux", self) && g.installedCards(Runner).exists(c => g.counter(c, "power") > 0) =>
                $(AddFlagEffect(g.encounterFlag("flux", self)), OptionalEffect(Runner, "Flux Capacitor: charge 1 of your installed cards?", $(ChargeEffect(Runner)), $))
            case _ => $
        }
    }

    object Nanuq extends PT("33088") {
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(2), 2, $()))
        override def pumps(self : Card)(implicit g : Game) = $(Pump(c(1), 1))
        override def onTrash(self : Card, by : Faction)(implicit g : Game) = $(RemoveFromGameEffect(self))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case AgendaScored(_) | AgendaStolen(_) if g.isInstalled(self) => $(RemoveFromGameEffect(self))
            case _ => $
        }
    }

    object Orca extends PT("33089") {
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(2), 0, $("Sentry")))
        override def pumps(self : Card)(implicit g : Game) = $(Pump(c(2), 3))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SubsBroken(_, _, _) if fullyBroke(self, e) && !g.usedThisTurn(self, "orca") && g.installedCards(Runner).exists(c => g.counter(c, "power") > 0) =>
                $(MarkUsedEffect(self, "orca"), OptionalEffect(Runner, "Orca: charge 1 of your installed cards?", $(ChargeEffect(Runner)), $))
            case _ => $
        }
    }

    object K2CPTurbine extends PT("33090") {
        override def breakerStrengthMod(self : Card, b : Card)(implicit g : Game) = (b.data.isIcebreaker && !g.has(b, "AI")).?(2).|(0)
    }

    object WorldTree extends PT("33091") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(_) if g.reg("successful-run") == 1 && g.installedCards(Runner).but(self).any =>
                $(OptionalEffect(Runner, "World Tree: trash 1 of your other installed cards to search the stack for a card of the same type?", $(ChooseCardEffect(Runner, g.installedCards(Runner).but(self), WorldTreePurpose(self), "World Tree: choose an installed card to trash", false)), $))
            case _ => $
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case WorldTreePurpose(self) =>
                cards.single.foreach { c =>
                    g.trashCard(c, Runner, false)
                    val l = g.deck(Runner).%(x => x.data.kind == c.data.kind && runnerCanInstall(x, 3)).sortBy(_.title)
                    if (l.any)
                        g.push(ChooseCardEffect(Runner, l, WorldTreeInstallPurpose(self), "World Tree: choose a card to install paying 3 credits less", true), ShuffleDeckEffect(Runner))
                    else
                        g.push(ShuffleDeckEffect(Runner))
                }
                NextAction
            case WorldTreeInstallPurpose(self) =>
                cards.single.foreach(c => g.push(RunnerInstallDiscountEffect(c, 3)))
                NextAction
        }
    }

    object DrNukaVrolyck extends PT("33092") {
        override def initialCounters(self : Card)(implicit g : Game) = Map("power" -> 2)
        override def triggers(self : Card, e : Event)(implicit g : Game) = trashOnEmpty(self, e, "power")
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("remove 1 hosted power counter to draw 3 cards", click(), $(AddCounterEffect(self, "power", -1), draw(Runner, 3)), click = true, ok = g.counter(self, "power") > 0 && g.deck(Runner).any, reason = "no power counters or empty stack"))
    }

    object NovaInitiumia extends PT("33093")

    object Matryoshka extends PT("33094") {
        override def abilities(self : Card)(implicit g : Game) = {
            val l = g.hand(Runner).%(_.title == self.title)
            $(Ability("host a copy of Matryoshka from the grip", click(), $(ChooseCardEffect(Runner, l, MatryoshkaHostPurpose(self), "Matryoshka: choose a copy to host", false)), click = true, ok = l.any, reason = "no copy in the grip"))
        }
        override def breaks(self : Card)(implicit g : Game) = {
            val faceup = g.stashed(self).%(c => !g.state(c).facedown)
            encounterIce.%(_ => faceup.any)./~ { ice =>
                val n = g.unbrokenSubs(ice).num
                (n > 0).$(BreakAbility(c(n), 0, $(), |("break all " + n + " subroutines (" + n + " credits, turn 1 hosted copy facedown)"), $(MatryoshkaFlipAction(self))))
            }
        }
        override def pumps(self : Card)(implicit g : Game) = $(Pump(c(1), 1))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) if g.stashed(self).exists(c => g.state(c).facedown) => $(MatryoshkaFaceupAction(self))
            case _ => $
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.foreach(c => g.push(StashEffect(c, p.card)))
            NextAction
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case MatryoshkaFlipAction(self) =>
                g.stashed(self).%(c => !g.state(c).facedown).starting.foreach { c =>
                    g.state(c).facedown = true
                    g.log("A copy hosted on", self, "is turned facedown")
                }
                NextAction
            case MatryoshkaFaceupAction(self) =>
                g.stashed(self).foreach(c => g.state(c).facedown = false)
                g.log("Cards hosted on", self, "are turned faceup")
                NextAction
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Corp
    // ------------------------------------------------------------------------------------------------------------

    object ThuleSubsea extends PT("33095") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case AgendaStolen(_) =>
                $(ChooseOneEffect(Runner, "Thule Subsea: choose one", $(
                    Alt("Spend [click] and 2 credits", $(LoseClicksEffect(Runner, 1), LoseCreditsEffect(Runner, 2)), g.clicks(Runner) >= 1 && g.credits(Runner) >= 2),
                    Alt("Suffer 1 core damage", $(core(1))))))
            case _ => $
        }
    }

    object OntologicalDependence extends PT("33096") {
        override def advancementRequirementMod(self : Card, agenda : Card)(implicit g : Game) = (agenda == self).?(-g.coreDamage).|(0)
    }

    object NightmareArchive extends PT("33097") {
        override def canBeTrashedOnAccess(self : Card)(implicit g : Game) = false
        override def agendaPointsBonus(self : Card)(implicit g : Game) = (g.zone(self) == ScoredZone(Runner)).?(-1).|(0)
        override def onAccess(self : Card)(implicit g : Game) = $(RevealEffect(Corp, self), ChooseOneEffect(Runner, "Nightmare Archive: choose one", $(
            Alt("Suffer 1 core damage", $(RemoveFromGameEffect(self), core(1))),
            Alt("Add it to your score area as an agenda worth -1 point", $(NightmareScoreAction(self))))))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case NightmareScoreAction(self) =>
                g.moveTo(self, ScoredZone(Runner))
                g.state(self).seen = true
                g.log(Runner, "adds", self, "to the score area as an agenda worth -1 agenda point")
                g.cleanupRemotes()
                NextAction
        }
    }

    object Bloop extends PT("33098") {
        def others(self : Card)(implicit g : Game) = g.rezzedIce.%(i => i != self && g.has(i, "Harmonic"))
        override def canRez(self : Card)(implicit g : Game) = others(self).any
        override def onRez(self : Card)(implicit g : Game) = others(self).any.$(ChooseCardEffect(Corp, others(self), BloopPurpose(self), "Bloop: derez another piece of harmonic ice", false))
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.foreach(c => g.push(DerezEffect(c)))
            NextAction
        }
        override def subroutines(self : Card)(implicit g : Game) = {
            val programs = g.installedCards(Runner).%(_.data.isProgram)
            $(Sub("Do 1 core damage", core(1)),
              Sub("Trash 1 installed program", ChooseCardEffect(Corp, programs, TrashPurpose(Corp), "Bloop: choose a program to trash", false)),
              Sub("Trash 1 installed program", ChooseCardEffect(Corp, programs, TrashPurpose(Corp), "Bloop: choose a program to trash", false)))
        }
    }

    object Pulse extends PT("33099") {
        override def onRez(self : Card)(implicit g : Game) = runOnThisServer(self).$(MessageEffect(Corp, "uses Pulse: the Runner loses [click]"), LoseClicksEffect(Runner, 1))
        override def subroutines(self : Card)(implicit g : Game) = {
            val n = g.rezzedIce.%(i => g.has(i, "Harmonic")).num
            $(Sub("The Runner loses 1 credit for each rezzed piece of harmonic ice (" + n + ")", LoseCreditsEffect(Runner, n)),
              Sub("End the run unless the Runner spends [click]", clickOrEtr("Pulse: choose one")))
        }
    }

    object DistributedTracing extends PT("33100") {
        override def canPlay(self : Card)(implicit g : Game) = g.clicks(Corp) >= 2 && g.regLast("stolen") > 0
        override def onPlay(self : Card)(implicit g : Game) = $(LoseClicksEffect(Corp, 1), tag(1))
    }

    object Hypoxia extends PT("33101") {
        override def canPlay(self : Card)(implicit g : Game) = g.tagged
        override def onPlay(self : Card)(implicit g : Game) =
            $(core(1), AddFlagEffect(Flag("hypoxia", None, None, "halfturn", g.halfTurn, $(MessageEffect(Runner, "gets -1 allotted click (Hypoxia)"), LoseClicksEffect(Runner, 1)))), RemoveFromGameEffect(self))
    }

    object DjupstadGrid extends PT("33102") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case AgendaScored(_) if g.isRezzed(self) && g.lastScoredFrom.exists(s => thisServer(self, s)) => $(MessageEffect(Corp, "uses Djupstad Grid"), core(1))
            case _ => $
        }
    }

    object MrHendrik extends PT("33103") {
        override def onAccess(self : Card)(implicit g : Game) =
            (g.isInstalled(self) && g.credits(Corp) >= 2).$(OptionalEffect(Corp, "Mr. Hendrik: pay 2 credits to do 1 core damage?", $(LoseCreditsEffect(Corp, 2), ChooseOneEffect(Runner, "Mr. Hendrik: choose one", $(
                Alt("Suffer 1 core damage", $(core(1))),
                Alt("Lose all remaining clicks", $(LoseClicksEffect(Runner, 99)), g.clicks(Runner) > 0)))), $))
    }

    object IssuaqAdaptics extends PT("33104") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case AgendaScored(c) if !g.hasFlag("installed", c) && !g.hasFlag("advanced", c) => $(AddCounterEffect(self, "power", 1), IssuaqWinCheckAction(self))
            case AgendaScored(c) => $(IssuaqWinCheckAction(self))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case IssuaqWinCheckAction(self) =>
                if (g.agendaPoints(Corp) >= g.pointsToWin - g.counter(self, "power") && !g.isOver)
                    GameOverAction(|(Corp), "Corp scored " + g.agendaPoints(Corp) + " agenda points (Issuaq Adaptics)")
                else
                    NextAction
        }
    }

    object HybridRelease extends PT("33105") {
        override def onScore(self : Card)(implicit g : Game) = {
            val l = g.discard(Corp).%(c => !g.state(c).seen && g.installable(c))
            l.any.$(OptionalEffect(Corp, "Hybrid Release: install a facedown card from Archives?", $(ChooseCardEffect(Corp, l, InstallChooseServerPurpose(false), "Hybrid Release: choose a facedown card in Archives to install", true)), $))
        }
    }

    object DrVientianeKeeling extends PT("33106") {
        override def onRez(self : Card)(implicit g : Game) = $(AddCounterEffect(self, "power", 1))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) if g.isRezzed(self) => $(AddCounterEffect(self, "power", 1))
            case _ => $
        }
        override def handSize(self : Card, f : Faction)(implicit g : Game) = f.isRunner.?(-g.counter(self, "power")).|(0)
    }

    object ReaperFunction extends PT("33107") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) if g.isRezzed(self) => $(OptionalEffect(Corp, "Reaper Function: trash it to do 2 net damage?", $(TrashEffect(self, Corp, false), net(2)), $))
            case _ => $
        }
    }

    object Hafrun extends PT("33108") {
        override def subroutines(self : Card)(implicit g : Game) = $(etrSub)
        override def onRez(self : Card)(implicit g : Game) =
            (runOnThisServer(self) && g.hand(Corp).any && g.installedCards(Runner).any).$(OptionalEffect(Corp, "Hafrun: trash 1 card from HQ so that an installed Runner card cannot break subroutines this run?", $(
                ChooseCardEffect(Corp, g.hand(Corp), HafrunTrashPurpose(self), "Hafrun: choose a card in HQ to trash", false),
                ChooseCardEffect(Corp, g.installedCards(Runner), HafrunTargetPurpose(self), "Hafrun: choose an installed Runner card", false)), $))
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case HafrunTrashPurpose(self) =>
                cards.foreach { c =>
                    g.toArchives(c, false)
                    g.log(Corp, "trashes a card from HQ")
                }
                NextAction
            case HafrunTargetPurpose(self) =>
                cards.foreach { c =>
                    g.addFlag(g.runFlag("cannot-break", |(c)))
                    g.log(c, "cannot break subroutines for the remainder of the run")
                }
                NextAction
        }
    }

    object Vampyronassa extends PT("33109") {
        override def subroutines(self : Card)(implicit g : Game) = $(loseSub(2), gainSub(2), netSub(2),
            Sub("You may draw 1 or 2 cards", ChooseOneEffect(Corp, "Vampyronassa: draw cards?", $(Alt("Draw 2 cards", $(draw(Corp, 2))), Alt("Draw 1 card", $(draw(Corp, 1))), Alt("Draw nothing", $)))))
    }

    object SimulationReset extends PT("33110") {
        override def onPlay(self : Card)(implicit g : Game) =
            $(ChooseCardsEffect(Corp, g.hand(Corp), 0, 5, SimResetPurpose(self), "Simulation Reset: choose up to 5 cards in HQ to trash"), RemoveFromGameEffect(self))
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            val n = cards.num
            cards.foreach(c => g.toArchives(c, false))
            if (n > 0) {
                g.log(Corp, "trashes", n.cards, "from HQ")
                g.push(ChooseCardsEffect(Corp, g.discard(Corp), n, n, ShuffleIntoDeckPurpose(Corp), "Simulation Reset: choose " + n + " cards in Archives to shuffle into R&D"), draw(Corp, n))
            }
            NextAction
        }
    }

    object NanisivikGrid extends PT("33111") {
        def facedownIce(implicit g : Game) = g.discard(Corp).%(c => c.data.isIce && !g.state(c).seen)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case ApproachServer(s) if thisServer(self, s) && g.isRezzed(self) && facedownIce.any =>
                $(OptionalEffect(Corp, "Nanisivik Grid: turn a facedown piece of ice in Archives faceup to resolve 1 of its subroutines?", $(ChooseCardEffect(Corp, facedownIce, NanisivikPurpose(self), "Nanisivik Grid: choose a facedown piece of ice in Archives", true)), $))
            case _ => $
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.foreach { c =>
                g.state(c).seen = true
                g.log(Corp, "turns", c, "faceup in Archives")
                val subs = g.impl(c).subroutines(c)
                if (subs.any)
                    g.push(ChooseOneEffect(Corp, "Nanisivik Grid: choose a subroutine to resolve", subs./(s => Alt(s.label, $(s.effect)))))
            }
            NextAction
        }
    }

    object FreedomOfInformation extends PT("33112") {
        override def advancementRequirementMod(self : Card, agenda : Card)(implicit g : Game) = (agenda == self).?(-g.tags).|(0)
    }

    object PostTruthDividend extends PT("33113") {
        override def onScore(self : Card)(implicit g : Game) = $(OptionalEffect(Corp, "Post-Truth Dividend: draw 1 card?", $(draw(Corp, 1)), $))
    }

    object Gaslight extends PT("33114") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) if g.isRezzed(self) => $(OptionalEffect(Corp, "Gaslight: trash it to search R&D for an operation?", $(TrashEffect(self, Corp, false), GaslightAction(self)), $))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case GaslightAction(self) =>
                val l = g.deck(Corp).%(_.data.isOperation).sortBy(_.title)
                if (l.any)
                    g.push(ChooseCardEffect(Corp, l, AddToHandPurpose(Corp, true), "Gaslight: choose an operation to reveal and add to HQ", true), ShuffleDeckEffect(Corp))
                else
                    g.push(ShuffleDeckEffect(Corp))
                NextAction
        }
    }

    object VeraIvanovnaShuyskaya extends PT("33115") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case AgendaScored(_) | AgendaStolen(_) if g.isRezzed(self) && g.hand(Runner).any =>
                $(OptionalEffect(Corp, "Vera Ivanovna Shuyskaya: reveal the grip and trash 1 card?", $(VeraAction(self)), $))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case VeraAction(self) =>
                g.log(Runner, "reveals the grip:", g.hand(Runner)./(g.cardElem).comma)
                g.push(ChooseCardEffect(Corp, g.hand(Runner), TrashPurpose(Corp), "Vera Ivanovna Shuyskaya: choose a card in the grip to trash", false))
                NextAction
        }
    }

    object Klevetnik extends PT("33116") {
        override def subroutines(self : Card)(implicit g : Game) = $(etrSub)
        override def onRez(self : Card)(implicit g : Game) = {
            val l = g.installedCards(Runner).%(_.data.isResource)
            (runOnThisServer(self) && l.any).$(OptionalEffect(Corp, "Klevetnik: let the Runner gain 2 credits to blank a resource until your next turn ends?", $(gain(Runner, 2), ChooseCardEffect(Corp, l, KlevetnikPurpose(self), "Klevetnik: choose an installed resource", false)), $))
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.foreach { c =>
                g.addFlag(Flag("disabled", |(c), None, "halfturn", g.active.isCorp.?(g.halfTurn + 2).|(g.halfTurn + 1), $))
                g.log(c, "loses all abilities until the Corp's next turn ends")
            }
            NextAction
        }
    }

    object UnsmilingTsarevna extends PT("33117") {
        override def subroutines(self : Card)(implicit g : Game) = $(tagSub(1), netSub(2), Sub("You may draw 2 cards", OptionalEffect(Corp, "Unsmiling Tsarevna: draw 2 cards?", $(draw(Corp, 2)), $)))
        override def onRez(self : Card)(implicit g : Game) =
            runOnThisServer(self).$(OptionalEffect(Corp, "Unsmiling Tsarevna: let the Runner gain 2 credits so they cannot break more than 1 subroutine per encounter this run?", $(gain(Runner, 2), AddFlagEffect(g.runFlag("max-break-1", |(self)))), $))
    }

    object NonequivalentExchange extends PT("33118") {
        override def onPlay(self : Card)(implicit g : Game) = $(ChooseOneEffect(Corp, "Nonequivalent Exchange", $(
            Alt("Gain 7 credits and the Runner gains 2 credits", $(gain(Corp, 7), gain(Runner, 2))),
            Alt("Gain 5 credits", $(gain(Corp, 5))))))
    }

    object ShipmentFromVladisibirsk extends PT("33119") {
        override def canPlay(self : Card)(implicit g : Game) = g.tags >= 2
        override def onPlay(self : Card)(implicit g : Game) = {
            val l = g.advanceableCards
            l.any.??(4.to(1, -1).$./(k => ChooseCardEffect(Corp, l, AdvancePurpose(1), "Shipment from Vladisibirsk: place 1 advancement counter (" + k + " remaining)", false)))
        }
    }

    object RegulatoryCapture extends PT("33120") {
        override def advancementRequirementMod(self : Card, agenda : Card)(implicit g : Game) = (agenda == self).?(-g.badPublicity.atMost(4)).|(0)
    }

    object KimberliteField extends PT("33121") {
        override def onScore(self : Card)(implicit g : Game) = {
            val l = g.installedCards(Corp).%(g.isRezzed)
            l.any.$(OptionalEffect(Corp, "Kimberlite Field: trash 1 of your rezzed cards to trash a Runner card?", $(ChooseCardEffect(Corp, l, KimberlitePurpose(self), "Kimberlite Field: choose a rezzed card to trash", false)), $))
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.foreach { c =>
                val cost = c.data.cost.|(0)
                g.trashCard(c, Corp, false)
                val l = g.installedCards(Runner).%(_.data.cost.|(0) <= cost)
                if (l.any)
                    g.push(ChooseCardEffect(Corp, l, TrashPurpose(Corp), "Kimberlite Field: choose a Runner card to trash", false))
            }
            NextAction
        }
    }

    object HostileArchitecture extends PT("33122") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Trashed(c, Runner, _) if c != self && g.owner(c).isCorp && g.wasInstalled(c) && g.isRezzed(self) && g.reg("runner-trash-installed") == 1 => $(MessageEffect(Corp, "uses Hostile Architecture"), meat(2))
            case _ => $
        }
        override def onTrash(self : Card, by : Faction)(implicit g : Game) = by.isRunner.$(HostileArchitectureAction(self))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case HostileArchitectureAction(self) =>
                if (g.reg("runner-trash-installed") == 1 && g.state(self).seen) {
                    g.log(Corp, "uses", self)
                    g.push(meat(2))
                }
                NextAction
        }
    }

    object SuperdeepBorehole extends PT("33123") {
        override def onRez(self : Card)(implicit g : Game) = $(AddCounterEffect(self, "badpub", 6))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) if g.isRezzed(self) => $(AddCounterEffect(self, "badpub", -1), BadPubEffect(1), BoreholeAction(self))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case BoreholeAction(self) =>
                if (g.counter(self, "badpub") == 0 && !g.isOver)
                    GameOverAction(|(Corp), "Superdeep Borehole is empty")
                else
                    NextAction
        }
    }

    object Anvil extends PT("33124") {
        override def onEncounter(self : Card)(implicit g : Game) = {
            val l = g.installedCards(Corp).but(self)
            l.any.$(OptionalEffect(Corp, "Anvil: trash 1 of your other installed cards so that its subroutines cannot be broken this encounter?", $(ChooseCardEffect(Corp, l, AnvilPurpose(self), "Anvil: choose an installed card to trash", false)), $))
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.foreach { c =>
                g.trashCard(c, Corp, false)
                g.addFlag(g.encounterFlag("cannot-break", p.card))
                g.log("The subroutines of", p.card, "cannot be broken this encounter")
            }
            NextAction
        }
        override def subroutines(self : Card)(implicit g : Game) = $(
            Sub("Gain 1 credit. The Runner loses 1 credit", SeqEffect($(gain(Corp, 1), LoseCreditsEffect(Runner, 1)))),
            Sub("The Runner trashes 1 of their installed cards", ChooseCardEffect(Runner, g.installedCards(Runner), TrashPurpose(Runner), "Anvil: choose an installed card to trash", false)))
    }

    object EndOfTheLine extends PT("33125") {
        override def canPlay(self : Card)(implicit g : Game) = g.tagged
        override def onPlay(self : Card)(implicit g : Game) = $(RemoveTagsEffect(1), meat(4))
    }

    object YakovAvdakov extends PT("33126") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Trashed(c, _, _) if c != self && g.owner(c).isCorp && g.isRezzed(self) && !g.installing && g.lastServerOf(c).exists(s => thisServer(self, s)) => $(MessageEffect(Corp, "uses Yakov Erikovich Avdakov"), gain(Corp, 2))
            case _ => $
        }
        override def onTrash(self : Card, by : Faction)(implicit g : Game) = $(YakovAction(self))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case YakovAction(self) =>
                if (g.state(self).seen && !g.installing)
                    g.push(MessageEffect(Corp, "uses Yakov Erikovich Avdakov"), gain(Corp, 2))
                NextAction
        }
    }

    object ZATOCityGrid extends PT("33127") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case EncounterIce(ice) if g.isRezzed(self) && g.serverOf(ice).exists(s => thisServer(self, s)) && !g.isDisabled(ice) && g.impl(ice).subroutines(ice).any =>
                val subs = g.impl(ice).subroutines(ice)
                $(OptionalEffect(Corp, "ZATO City Grid: trash the encountered ice to resolve 1 of its subroutines?", $(ChooseOneEffect(Corp, "ZATO City Grid: choose a subroutine", subs./(s => Alt(s.label, $(TrashEffect(ice, Corp, false), s.effect))))), $))
            case _ => $
        }
    }

    object Ampere extends PT("33128")

    val all : $[CardImpl] = $(
        Finality, KatorgaBreakout, RaindropsCutStone, TimeBomb, Abaasy, Hush, Nga, Num, Bankhar, Concerto, Reprise, PoisonVial,
        WakeImplant, ZenitChip, Tremolo, TunnelVision, AsmundPudlat, InfoBounty, SparkOfInspiration, HippocampicMechanocytes,
        BasilarSynthgland, FluxCapacitor, Nanuq, Orca, K2CPTurbine, WorldTree, DrNukaVrolyck, NovaInitiumia, Matryoshka,
        ThuleSubsea, OntologicalDependence, NightmareArchive, Bloop, Pulse, DistributedTracing, Hypoxia, DjupstadGrid, MrHendrik,
        IssuaqAdaptics, HybridRelease, DrVientianeKeeling, ReaperFunction, Hafrun, Vampyronassa, SimulationReset, NanisivikGrid,
        FreedomOfInformation, PostTruthDividend, Gaslight, VeraIvanovnaShuyskaya, Klevetnik, UnsmilingTsarevna, NonequivalentExchange,
        ShipmentFromVladisibirsk, RegulatoryCapture, KimberliteField, HostileArchitecture, SuperdeepBorehole, Anvil, EndOfTheLine,
        YakovAvdakov, ZATOCityGrid, Ampere,
    )
}
