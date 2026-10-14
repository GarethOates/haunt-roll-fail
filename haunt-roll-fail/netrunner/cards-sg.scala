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
// System Gateway (sg) card implementations, ported from jinteki.net src/clj/game/cards/*.clj
// ---------------------------------------------------------------------------------------------------------------------

abstract class SG(val code : String) extends CardImpl {
    // "trash this card when it has no more <kind> counters" (Daily Casts style)
    def trashOnEmpty(self : Card, e : Event, kind : String)(implicit g : Game) : $[ForcedAction] = e match {
        case CounterAdded(c, k) if c == self && k == kind && g.counter(self, kind) == 0 && g.isInstalled(self) => $(TrashEffect(self, g.owner(self), false))
        case _ => $
    }

    def thisServer(self : Card, s : Server)(implicit g : Game) = g.serverOf(self).has(s)

    def thisCardRun(self : Card)(implicit g : Game) = g.run.exists(_.source.has(self))

    def installedBreakers(implicit g : Game) = g.installedCards(Runner).%(c => c.data.isProgram && c.data.isIcebreaker).num
}

// card specific actions
case class TranquilizerAction(card : Card) extends CardAction
case class MutualFavorPurpose(card : Card) extends CardPurpose
case class MutualFavorInstallAction(card : Card, breaker : Card) extends CardAction
case class TaoPurpose(card : Card) extends CardPurpose
case class PantographPurpose(card : Card) extends CardPurpose
case class MayflyUsedAction(card : Card) extends CardAction
case class NicoAction(card : Card) extends CardAction
case class BranPurpose(card : Card) extends CardPurpose
case class SprintAction(card : Card) extends CardAction
case class DivinerAction(card : Card) extends CardAction
case class DivinerCheckAction(card : Card) extends CardAction
case class AnoeticAction(card : Card) extends CardAction
case class AnoeticPurpose(card : Card) extends CardPurpose
case class ClearinghouseAction(card : Card) extends CardAction
case class MalapertPurpose(card : Card) extends CardPurpose
case class WhitespaceAction(card : Card) extends CardAction
case class PingAction(card : Card) extends CardAction


object SGCards {
    // ------------------------------------------------------------------------------------------------------------
    // Runner
    // ------------------------------------------------------------------------------------------------------------

    object Loup extends SG("30001") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Trashed(c, Runner, true) if g.reg("trash-accessed") == 1 => $(draw(Runner, 1), gain(Runner, 1))
            case _ => $
        }
    }

    object WildcatStrike extends SG("30002") {
        override def onPlay(self : Card)(implicit g : Game) = $(ChooseOneEffect(Corp, "Wildcat Strike: the Corp chooses", $(
            Alt("Runner gains 6 credits", $(gain(Runner, 6))),
            Alt("Runner draws 4 cards", $(draw(Runner, 4))))))
    }

    object Carnivore extends SG("30003") {
        override def memory(self : Card)(implicit g : Game) = 1
        override def accessAbility(self : Card, target : Card)(implicit g : Game) =
            (!g.zone(target).is[DiscardZone] && g.run.exists(!_.cannotStealOrTrash)).?(Ability("trash the accessed card at no cost", $(TrashFromHandCost(2)), $(TrashEffect(target, Runner, true)), once = once("carnivore"), ok = !g.usedThisTurn(self, "carnivore") && g.hand(Runner).num >= 2, reason = "once per turn, needs 2 cards in grip"))
    }

    object Botulus extends SG("30004") {
        override def mustBeHosted(self : Card)(implicit g : Game) = true
        override def hostRequirement(self : Card, host : Card)(implicit g : Game) = host.data.isIce
        override def initialCounters(self : Card)(implicit g : Game) = Map("virus" -> 1)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) => $(AddCounterEffect(self, "virus", 1))
            case _ => $
        }
        override def breaks(self : Card)(implicit g : Game) =
            (g.run./~(_.ice).any && g.run./~(_.ice) == g.state(self).host).$(BreakAbility($(VirusCost(1)), 1, $(), |("break 1 subroutine on host ice")))
    }

    object Buzzsaw extends SG("30005") {
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(1), 2, $("Code Gate")))
        override def pumps(self : Card)(implicit g : Game) = $(Pump(c(3), 1))
    }

    object Cleaver extends SG("30006") {
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(1), 2, $("Barrier")))
        override def pumps(self : Card)(implicit g : Game) = $(Pump(c(2), 1))
    }

    object Fermenter extends SG("30007") {
        override def initialCounters(self : Card)(implicit g : Game) = Map("virus" -> 1)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) => $(AddCounterEffect(self, "virus", 1))
            case _ => $
        }
        override def abilities(self : Card)(implicit g : Game) = {
            val n = g.counter(self, "virus")
            $(Ability("gain " + (2 * n) + " credits (2 per virus counter)", $(ClickCost(1), TrashSelfCost), $(gain(Runner, 2 * n)), click = true, ok = n > 0, reason = "no virus counters"))
        }
    }

    object Leech extends SG("30008") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(s) if s.isCentral => $(AddCounterEffect(self, "virus", 1))
            case _ => $
        }
        override def abilities(self : Card)(implicit g : Game) = {
            val ice = g.run./~(_.ice).%(i => g.run.exists(_.phase == EncounterPhase))
            $(Ability("give -1 strength to the encountered ice", $(VirusCost(1)), ice.$./(i => PumpIceEffect(i, -1, EncounterDuration)), ok = ice.any, reason = "not encountering ice"))
        }
    }

    object Cookbook extends SG("30009") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Installed(Runner, c) if g.has(c, "Virus") => $(OptionalEffect(Runner, "Cookbook: place 1 virus counter on the installed card?", $(AddCounterEffect(c, "virus", 1)), $))
            case _ => $
        }
    }

    object Zahya extends SG("30010") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case RunEnds(s, _, _) if (s == HQ || s == RnD) && g.run.exists(_.accessCount > 0) && !g.usedThisTurn(self, "zahya") =>
                val n = g.run./(_.accessCount).|(0)
                $(OptionalEffect(Runner, "Zahya: gain " + n + " credits (1 per card accessed)?", $(gain(Runner, n), MarkUsedEffect(self, "zahya")), $))
            case _ => $
        }
    }

    object MutualFavor extends SG("30011") {
        override def onPlay(self : Card)(implicit g : Game) = {
            val l = g.deck(Runner).%(_.data.isIcebreaker).sortBy(_.title)
            l.any.$(ChooseCardEffect(Runner, l, MutualFavorPurpose(self), "Mutual Favor: choose an icebreaker from the stack", true)) :+ ShuffleDeckEffect(Runner)
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single match {
                case Some(b) =>
                    g.log(Runner, "reveals", b, "from the stack")
                    if (g.successfulRunsThisTurn.any && g.canSpend(Runner, g.installCost(b, None), Spend.install(b)))
                        g.push(OptionalEffect(Runner, "Mutual Favor: install the icebreaker?", $(MutualFavorInstallAction(p.card, b)), $(AddToHandEffect(b))))
                    else
                        g.push(AddToHandEffect(b))
                case None =>
            }
            NextAction
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case MutualFavorInstallAction(_, b) =>
                g.push(RunnerInstallEffect(b, None, false))
                NextAction
        }
    }

    object TreadLightly extends SG("30012") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = $(RunAnyServerEffect(|(self), g.servers))
        override def rezCostMod(self : Card, card : Card)(implicit g : Game) = (card.data.isIce && thisCardRun(self)).?(3).|(0)
    }

    object DocklandsPass extends SG("30013") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case BreachServer(HQ) if g.reg("breach-hq") == 1 => $(AccessBonusEffect(HQ, 1))
            case _ => $
        }
    }

    object Pennyshaver extends SG("30014") {
        override def memory(self : Card)(implicit g : Game) = 1
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(_) => $(AddCounterEffect(self, "credit", 1))
            case _ => $
        }
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("gain 1 credit and take all hosted credits (" + g.counter(self, "credit") + ")", click(), $(gain(Runner, 1), TakeCreditsEffect(Runner, self, 99)), click = true))
    }

    object Carmen extends SG("30015") {
        override def installCostMod(self : Card, card : Card)(implicit g : Game) = (card == self && g.successfulRunsThisTurn.any).?(-2).|(0)
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(1), 1, $("Sentry")))
        override def pumps(self : Card)(implicit g : Game) = $(Pump(c(2), 3))
    }

    object Marjanah extends SG("30016") {
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(g.successfulRunsThisTurn.any.?(1).|(2)), 1, $("Barrier")))
        override def pumps(self : Card)(implicit g : Game) = $(Pump(c(1), 1))
    }

    object Tranquilizer extends SG("30017") {
        override def mustBeHosted(self : Card)(implicit g : Game) = true
        override def hostRequirement(self : Card, host : Card)(implicit g : Game) = host.data.isIce
        override def onInstall(self : Card)(implicit g : Game) = $(TranquilizerAction(self))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) => $(TranquilizerAction(self))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case TranquilizerAction(self) =>
                g.addCounter(self, "virus", 1)
                g.log("1 virus counter placed on", self)
                g.state(self).host match {
                    case Some(h) if g.isRezzed(h) && g.counter(self, "virus") >= 3 => g.push(DerezEffect(h))
                    case _ =>
                }
                NextAction
        }
    }

    object RedTeam extends SG("30018") {
        override def initialCounters(self : Card)(implicit g : Game) = Map("credit" -> 12)
        override def triggers(self : Card, e : Event)(implicit g : Game) = trashOnEmpty(self, e, "credit") ++ (e match {
            case SuccessfulRun(_) if thisCardRun(self) => $(TakeCreditsEffect(Runner, self, 3))
            case _ => $
        })
        override def abilities(self : Card)(implicit g : Game) = {
            val l = Server.centrals.%(s => !g.runsThisTurn.has(s))
            $(Ability("run a central server not run this turn", click(), $(RunAnyServerEffect(|(self), l)), click = true, ok = l.any, reason = "all centrals already run"))
        }
    }

    object Tao extends SG("30019") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case AgendaScored(_) | AgendaStolen(_) if g.allIce.num >= 2 =>
                $(OptionalEffect(Runner, "Tao Salonga: swap 2 pieces of ice?", $(ChooseCardsEffect(Runner, g.allIce, 2, 2, TaoPurpose(self), "Choose 2 pieces of ice to swap")), $))
            case _ => $
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards match {
                case $(a, b) => g.push(SwapIceEffect(a, b))
                case _ =>
            }
            NextAction
        }
    }

    object CreativeCommission extends SG("30020") {
        override def onPlay(self : Card)(implicit g : Game) = $(gain(Runner, 5)) ++ (g.clicks(Runner) > 0).$(LoseClicksEffect(Runner, 1))
    }

    object VRcation extends SG("30021") {
        override def onPlay(self : Card)(implicit g : Game) = $(draw(Runner, 4)) ++ (g.clicks(Runner) > 0).$(LoseClicksEffect(Runner, 1))
    }

    object DZMZOptimizer extends SG("30022") {
        override def memory(self : Card)(implicit g : Game) = 1
        override def installCostMod(self : Card, card : Card)(implicit g : Game) = (card.data.isProgram && g.reg("install-program") == 0).?(-1).|(0)
    }

    object Pantograph extends SG("30023") {
        override def memory(self : Card)(implicit g : Game) = 1
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case AgendaScored(_) | AgendaStolen(_) =>
                $(gain(Runner, 1), PantographChooseAction(self))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case PantographChooseAction(self) =>
                val l = g.hand(Runner).%(c => !c.data.isEvent && g.canSpend(Runner, g.installCost(c, None), Spend.install(c)) && (!c.data.isProgram || g.memoryFree >= c.data.memory.|(0)))
                if (l.any)
                    g.push(ChooseCardEffect(Runner, l, PantographPurpose(self), "Pantograph: install a card from the grip?", true))
                NextAction
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach { c =>
                if (g.impl(c).mustBeHosted(c)) {
                    val hosts = g.allIce.%(h => g.impl(c).hostRequirement(c, h))
                    if (hosts.any)
                        g.push(ChooseCardEffect(Runner, hosts, HostInstallPurpose(c, false), "Choose a host", false))
                }
                else
                    g.push(RunnerInstallEffect(c, None, false))
            }
            NextAction
        }
    }

    object Conduit extends SG("30024") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case RunEnds(RnD, true, _) => $(OptionalEffect(Runner, "Conduit: place 1 virus counter on Conduit?", $(AddCounterEffect(self, "virus", 1)), $))
            case SuccessfulRun(RnD) if thisCardRun(self) && g.counter(self, "virus") > 0 => $(AccessBonusEffect(RnD, g.counter(self, "virus")))
            case _ => $
        }
        override def abilities(self : Card)(implicit g : Game) = $(Ability("run R&D", click(), $(RunEffect(RnD, |(self))), click = true))
    }

    object Echelon extends SG("30025") {
        override def strength(self : Card)(implicit g : Game) = installedBreakers
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(1), 1, $("Sentry")))
        override def pumps(self : Card)(implicit g : Game) = $(Pump(c(3), 2))
    }

    object Unity extends SG("30026") {
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(1), 1, $("Code Gate")))
        override def pumps(self : Card)(implicit g : Game) = $(Pump(c(1), installedBreakers, EncounterDuration, |("+1 strength per installed icebreaker (" + installedBreakers + ")")))
    }

    object TeleworkContract extends SG("30027") {
        override def initialCounters(self : Card)(implicit g : Game) = Map("credit" -> 9)
        override def triggers(self : Card, e : Event)(implicit g : Game) = trashOnEmpty(self, e, "credit")
        override def abilities(self : Card)(implicit g : Game) = $(Ability("take 3 credits", click(), $(TakeCreditsEffect(Runner, self, 3)), click = true, once = once("telework")))
    }

    object Jailbreak extends SG("30028") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = $(RunAnyServerEffect(|(self), $(HQ, RnD)))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(s) if (s == HQ || s == RnD) && thisCardRun(self) => $(AccessBonusEffect(s, 1), draw(Runner, 1))
            case _ => $
        }
    }

    object Overclock extends SG("30029") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def initialCounters(self : Card)(implicit g : Game) = Map("credit" -> 5)
        override def creditsUsableForRun(self : Card)(implicit g : Game) = thisCardRun(self)
        override def onPlay(self : Card)(implicit g : Game) = $(RunAnyServerEffect(|(self), g.servers))
    }

    object SureGamble extends SG("30030") {
        override def onPlay(self : Card)(implicit g : Game) = $(gain(Runner, 9))
    }

    object T400 extends SG("30031") {
        override def memory(self : Card)(implicit g : Game) = 1
        override def handSize(self : Card, f : Faction)(implicit g : Game) = f.isRunner.?(1).|(0)
    }

    object Mayfly extends SG("30032") {
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(1), 1, $(), None, $(MayflyUsedAction(self))))
        override def pumps(self : Card)(implicit g : Game) = $(Pump(c(1), 1))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case RunEnds(_, _, _) if g.state(self).usedRun.has("mayfly") => $(TrashEffect(self, Runner, false))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case MayflyUsedAction(self) =>
                g.state(self).usedRun :+= "mayfly"
                g.log(self, "will be trashed when the run ends")
                NextAction
        }
    }

    object SmartwareDistributor extends SG("30033") {
        override def abilities(self : Card)(implicit g : Game) = $(Ability("place 3 credits on Smartware Distributor", click(), $(AddCounterEffect(self, "credit", 3)), click = true))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) if g.counter(self, "credit") > 0 => $(TakeCreditsEffect(Runner, self, 1))
            case _ => $
        }
    }

    object VerbalPlasticity extends SG("30034") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case ClickDraw(Runner) if g.reg("click-draw") == 1 => $(MessageEffect(Runner, "draws 1 additional card (Verbal Plasticity)"), draw(Runner, 1))
            case _ => $
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Corp
    // ------------------------------------------------------------------------------------------------------------

    object PrecisionDesign extends SG("30035") {
        override def handSize(self : Card, f : Faction)(implicit g : Game) = f.isCorp.?(1).|(0)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case AgendaScored(_) if g.discard(Corp).any =>
                $(OptionalEffect(Corp, "Precision Design: add 1 card from Archives to HQ?", $(ChooseCardEffect(Corp, g.discard(Corp), AddToHandPurpose(Corp, false), "Choose a card in Archives to add to HQ", true)), $))
            case _ => $
        }
    }

    object LuminalTransubstantiation extends SG("30036") {
        override def onScore(self : Card)(implicit g : Game) = $(GainClicksEffect(Corp, 3), SetFlagEffect("cannot-score"), MessageEffect(Corp, "cannot score agendas for the remainder of the turn"))
    }

    object NicoCampaign extends SG("30037") {
        override def initialCounters(self : Card)(implicit g : Game) = Map("credit" -> 9)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) if g.isRezzed(self) => $(NicoAction(self))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case NicoAction(self) =>
                val n = 3.atMost(g.counter(self, "credit"))
                g.addCounter(self, "credit", -n)
                g.credits += Corp -> (g.credits(Corp) + n)
                g.log(Corp, "takes", n.credits, "from", self)
                if (g.counter(self, "credit") == 0) {
                    g.trashCard(self, Corp, false)
                    g.push(draw(Corp, 1))
                }
                NextAction
        }
    }

    object Ansel extends SG("30038") {
        override def subroutines(self : Card)(implicit g : Game) = $(
            Sub("Trash an installed Runner card", ChooseCardEffect(Corp, g.installedCards(Runner), TrashPurpose(Corp), "Ansel 1.0: choose an installed Runner card to trash", false)),
            Sub("Install a card from HQ or Archives", ChooseCardEffect(Corp, (g.hand(Corp) ++ g.discard(Corp)).%(c => g.installable(c)), InstallChooseServerPurpose(false), "Ansel 1.0: choose a card to install", true)),
            Sub("The Runner cannot steal or trash Corp cards for the remainder of this run", CannotStealOrTrashEffect))
        override def runnerAbilities(self : Card)(implicit g : Game) = $(Ability("break 1 subroutine", $(LoseClickCost(1)), $(BioroidBreakEffect(self)), ok = g.run.exists(r => r.ice.has(self) && r.phase == EncounterPhase) && g.unbrokenSubs(self).any, reason = "not encountered"))
    }

    object Bran extends SG("30039") {
        override def subroutines(self : Card)(implicit g : Game) = $(
            Sub("Install a piece of ice from HQ or Archives protecting this server", ChooseCardEffect(Corp, (g.hand(Corp) ++ g.discard(Corp)).%(_.data.isIce), BranPurpose(self), "Bran 1.0: choose ice to install (ignoring install cost)", true)),
            etrSub, etrSub)
        override def runnerAbilities(self : Card)(implicit g : Game) = $(Ability("break 1 subroutine", $(LoseClickCost(1)), $(BioroidBreakEffect(self)), ok = g.run.exists(r => r.ice.has(self) && r.phase == EncounterPhase) && g.unbrokenSubs(self).any, reason = "not encountered"))
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            (cards.single, g.serverOf(p.card)) match {
                case (Some(c), Some(s)) =>
                    val idx = g.ice(s).indexOf(p.card).atLeast(0)
                    g.moveTo(c, IceZone(s))
                    val l = g.ice(s).but(c)
                    g.zones += IceZone(s) -> (l.take(idx) ++ $(c) ++ l.drop(idx))
                    g.state(c).installedTurn = g.turn
                    g.run.foreach(r => if (r.server == s && r.position > idx) r.position += 1)
                    g.log(Corp, "installs ice protecting", s, "(Bran 1.0)")
                    g.trigger(Installed(Corp, c))
                case _ =>
            }
            NextAction
        }
    }

    object SeamlessLaunch extends SG("30040") {
        override def onPlay(self : Card)(implicit g : Game) = {
            val l = g.installedCards(Corp).%(c => g.state(c).installedTurn != g.turn)
            l.any.$(ChooseCardEffect(Corp, l, AdvancePurpose(2), "Seamless Launch: place 2 advancement counters on a card installed before this turn", false))
        }
    }

    object Sprint extends SG("30041") {
        override def onPlay(self : Card)(implicit g : Game) = $(draw(Corp, 3), SprintAction(self))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case SprintAction(self) =>
                g.push(ChooseCardsEffect(Corp, g.hand(Corp), 2, 2, ShuffleIntoDeckPurpose(Corp), "Sprint: choose 2 cards in HQ to shuffle into R&D"))
                NextAction
        }
    }

    object ManegarmSkunkworks extends SG("30042") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case ApproachServer(s) if thisServer(self, s) && g.isRezzed(self) =>
                $(ChooseOneEffect(Runner, "Manegarm Skunkworks: choose one", $(
                    Alt("Spend [click][click]", $(LoseClicksEffect(Runner, 2)), g.clicks(Runner) >= 2),
                    Alt("Pay 5 credits", $(SpendEffect(Runner, 5, Spend.corpAbility, 0, $)), g.canSpend(Runner, 5, Spend.corpAbility)),
                    Alt("End the run", $(EndRunEffect)))))
            case _ => $
        }
    }

    object RestoringHumanity extends SG("30043") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnEnds(Corp) if g.discard(Corp).exists(c => !g.state(c).seen) => $(gain(Corp, 1))
            case _ => $
        }
    }

    object LongevitySerum extends SG("30044") {
        override def onScore(self : Card)(implicit g : Game) =
            $(ChooseCardsEffect(Corp, g.hand(Corp), 0, g.hand(Corp).num, TrashPurpose(Corp), "Longevity Serum: choose any number of cards in HQ to trash"), ShuffleFromArchivesEffect(3))
    }

    object UrticaCipher extends SG("30045") {
        override def advanceable(self : Card)(implicit g : Game) = true
        override def onAccess(self : Card)(implicit g : Game) =
            g.isInstalled(self).$(OptionalEffect(Corp, "Urtica Cipher: do " + (2 + g.advancement(self)) + " net damage?", $(net(2 + g.advancement(self))), $))
    }

    object Diviner extends SG("30046") {
        override def subroutines(self : Card)(implicit g : Game) = $(Sub("Do 1 net damage; if the trashed card's cost is odd, end the run", DivinerAction(self)))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case DivinerAction(self) =>
                g.lastDamaged = $
                g.push(net(1), DivinerCheckAction(self))
                NextAction
            case DivinerCheckAction(self) =>
                g.lastDamaged.single match {
                    case Some(c) if c.data.cost.exists(_ % 2 == 1) =>
                        g.log(self, "ends the run (odd cost)")
                        g.push(EndRunEffect)
                    case _ =>
                }
                NextAction
        }
    }

    object Karuna extends SG("30047") {
        override def subroutines(self : Card)(implicit g : Game) = $(Sub("Do 2 net damage. The Runner may jack out", KarunaAction(self)), netSub(2))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case KarunaAction(self) =>
                g.push(net(2), JackOutOfferEffect)
                NextAction
        }
    }

    object HanseiReview extends SG("30048") {
        override def onPlay(self : Card)(implicit g : Game) = $(gain(Corp, 10)) ++ g.hand(Corp).any.$(ChooseCardEffect(Corp, g.hand(Corp), TrashPurpose(Corp), "Hansei Review: choose a card in HQ to trash", false))
    }

    object Neurospike extends SG("30049") {
        override def onPlay(self : Card)(implicit g : Game) = $(net(g.reg("scored-points")))
    }

    object AnoeticVoid extends SG("30050") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case ApproachServer(s) if thisServer(self, s) && g.isRezzed(self) && g.credits(Corp) >= 2 && g.hand(Corp).num >= 2 =>
                $(OptionalEffect(Corp, "Anoetic Void: pay 2 credits and trash 2 cards from HQ to end the run?", $(AnoeticAction(self)), $))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case AnoeticAction(self) =>
                g.credits += Corp -> (g.credits(Corp) - 2)
                g.log(Corp, "pays", 2.credits)
                g.push(ChooseCardsEffect(Corp, g.hand(Corp), 2, 2, AnoeticPurpose(self), "Anoetic Void: choose 2 cards in HQ to trash"))
                NextAction
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.foreach(c => g.toArchives(c, false))
            g.log(Corp, "trashes", cards.num.cards, "from HQ")
            g.push(EndRunEffect)
            NextAction
        }
    }

    object RealityPlus extends SG("30051") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TagsGained(n) if g.reg("tags-gained") == n =>
                $(ChooseOneEffect(Corp, "NBN: Reality Plus", $(Alt("Gain 2 credits", $(gain(Corp, 2))), Alt("Draw 2 cards", $(draw(Corp, 2))))))
            case _ => $
        }
    }

    object TomorrowsHeadline extends SG("30052") {
        override def onScore(self : Card)(implicit g : Game) = $(tag(1))
        override def onSteal(self : Card)(implicit g : Game) = $(tag(1))
    }

    object SpinDoctor extends SG("30053") {
        override def onRez(self : Card)(implicit g : Game) = $(draw(Corp, 2))
        override def abilities(self : Card)(implicit g : Game) = $(Ability("shuffle up to 2 cards from Archives into R&D", $(RemoveSelfCost), $(ShuffleFromArchivesEffect(2)), ok = g.isRezzed(self), reason = "not rezzed"))
    }

    object Funhouse extends SG("30054") {
        override def onEncounter(self : Card)(implicit g : Game) = $(ChooseOneEffect(Runner, "Funhouse: choose one", $(Alt("Take 1 tag", $(tag(1))), Alt("End the run", $(EndRunEffect)))))
        override def subroutines(self : Card)(implicit g : Game) = $(Sub("Give the Runner 1 tag unless they pay 4 credits", ChooseOneEffect(Runner, "Funhouse subroutine", $(Alt("Take 1 tag", $(tag(1))), Alt("Pay 4 credits", $(SpendEffect(Runner, 4, Spend.corpAbility, 0, $)), g.canSpend(Runner, 4, Spend.corpAbility))))))
    }

    object Ping extends SG("30055") {
        override def onRez(self : Card)(implicit g : Game) = g.run.exists(r => !r.ended && g.serverOf(self).has(r.server)).$(tag(1))
        override def subroutines(self : Card)(implicit g : Game) = $(etrSub)
    }

    object PredictivePlanogram extends SG("30056") {
        override def onPlay(self : Card)(implicit g : Game) = $(ChooseOneEffect(Corp, "Predictive Planogram", $(
            Alt("Gain 3 credits", $(gain(Corp, 3))),
            Alt("Draw 3 cards", $(draw(Corp, 3)))) ++ g.tagged.$(Alt("Gain 3 credits and draw 3 cards", $(gain(Corp, 3), draw(Corp, 3))))))
    }

    object PublicTrail extends SG("30057") {
        override def onPlay(self : Card)(implicit g : Game) =
            g.successfulRunsLastTurn.any.$(ChooseOneEffect(Runner, "Public Trail: choose one", $(Alt("Take 1 tag", $(tag(1))), Alt("Pay 8 credits", $(SpendEffect(Runner, 8, Spend.corpAbility, 0, $)), g.canSpend(Runner, 8, Spend.corpAbility)))))
    }

    object AmazeAmusements extends SG("30058") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case RunEnds(s, _, true) if thisServer(self, s) && g.isRezzed(self) => $(MessageEffect(Corp, "uses AMAZE Amusements"), tag(2))
            case _ => $
        }
    }

    object BuiltToLast extends SG("30059") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Advanced(c, n) if g.advancement(c) - n <= 0 => $(gain(Corp, 2))
            case _ => $
        }
    }

    object AboveTheLaw extends SG("30060") {
        override def onScore(self : Card)(implicit g : Game) = {
            val l = g.installedCards(Runner).%(_.data.isResource)
            l.any.$(ChooseCardEffect(Corp, l, TrashPurpose(Corp), "Above the Law: choose a resource to trash", false))
        }
    }

    object Clearinghouse extends SG("30061") {
        override def advanceable(self : Card)(implicit g : Game) = true
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) if g.isRezzed(self) => $(OptionalEffect(Corp, "Clearinghouse: trash it to do " + g.advancement(self) + " meat damage?", $(ClearinghouseAction(self)), $))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case ClearinghouseAction(self) =>
                val n = g.advancement(self)
                g.trashCard(self, Corp, false)
                g.push(meat(n))
                NextAction
        }
    }

    object Ballista extends SG("30062") {
        override def subroutines(self : Card)(implicit g : Game) = {
            val programs = g.installedCards(Runner).%(_.data.isProgram)
            $(Sub("Trash 1 program or end the run", ChooseOneEffect(Corp, "Ballista: choose one", $(
                Alt("Trash a program", $(ChooseCardEffect(Corp, programs, TrashPurpose(Corp), "Ballista: choose a program to trash", false)), programs.any),
                Alt("End the run", $(EndRunEffect))))))
        }
    }

    object Pharos extends SG("30063") {
        override def advanceable(self : Card)(implicit g : Game) = true
        override def strength(self : Card)(implicit g : Game) = (g.advancement(self) >= 3).?(5).|(0)
        override def subroutines(self : Card)(implicit g : Game) = $(tagSub(1), etrSub, etrSub)
    }

    object GovernmentSubsidy extends SG("30064") {
        override def onPlay(self : Card)(implicit g : Game) = $(gain(Corp, 15))
    }

    object Retribution extends SG("30065") {
        override def onPlay(self : Card)(implicit g : Game) = {
            val l = g.installedCards(Runner).%(c => c.data.isProgram || c.data.isHardware)
            (g.tagged && l.any).$(ChooseCardEffect(Corp, l, TrashPurpose(Corp), "Retribution: choose a program or piece of hardware to trash", false))
        }
    }

    object MalapertDataVault extends SG("30066") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case AgendaScored(_) if g.isRezzed(self) && g.lastScoredFrom.exists(s => thisServer(self, s)) =>
                $(OptionalEffect(Corp, "Malapert Data Vault: search R&D for a non-agenda card?", $(ChooseCardEffect(Corp, g.deck(Corp).%!(_.data.isAgenda).sortBy(_.title), MalapertPurpose(self), "Choose a card to reveal and add to HQ", true), ShuffleDeckEffect(Corp)), $))
            case _ => $
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.foreach { c =>
                g.log(Corp, "reveals", c, "from R&D and adds it to HQ")
                g.moveTo(c, HandZone(Corp))
            }
            NextAction
        }
    }

    object OffworldOffice extends SG("30067") {
        override def onScore(self : Card)(implicit g : Game) = $(gain(Corp, 7))
    }

    object OrbitalSuperiority extends SG("30068") {
        override def onScore(self : Card)(implicit g : Game) = g.tagged.?($(meat(4))).|($(tag(1)))
    }

    object SendAMessage extends SG("30069") {
        def rez(implicit g : Game) = {
            val l = g.allIce.%(c => !g.isRezzed(c))
            l.any.$(ChooseCardEffect(Corp, l, RezPurpose(true), "Send a Message: choose a piece of ice to rez, ignoring all costs", false))
        }
        override def onScore(self : Card)(implicit g : Game) = rez
        override def onSteal(self : Card)(implicit g : Game) = rez
    }

    object SuperconductingHub extends SG("30070") {
        override def handSize(self : Card, f : Faction)(implicit g : Game) = (f.isCorp && g.zone(self) == ScoredZone(Corp)).?(2).|(0)
        override def onScore(self : Card)(implicit g : Game) = $(OptionalEffect(Corp, "Superconducting Hub: draw 2 cards?", $(draw(Corp, 2)), $))
    }

    object RegolithMiningLicense extends SG("30071") {
        override def initialCounters(self : Card)(implicit g : Game) = Map("credit" -> 15)
        override def triggers(self : Card, e : Event)(implicit g : Game) = trashOnEmpty(self, e, "credit")
        override def abilities(self : Card)(implicit g : Game) = $(Ability("take 3 credits", click(), $(TakeCreditsEffect(Corp, self, 3)), click = true, ok = g.isRezzed(self), reason = "not rezzed"))
    }

    object Palisade extends SG("30072") {
        override def strength(self : Card)(implicit g : Game) = g.serverOf(self).exists(_.isRemote).?(2).|(0)
        override def subroutines(self : Card)(implicit g : Game) = $(etrSub)
    }

    object Tithe extends SG("30073") {
        override def subroutines(self : Card)(implicit g : Game) = $(netSub(1), gainSub(1))
    }

    object Whitespace extends SG("30074") {
        override def subroutines(self : Card)(implicit g : Game) = $(loseSub(3), Sub("End the run if the Runner has 6 credits or less", WhitespaceAction(self)))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case WhitespaceAction(self) =>
                if (g.credits(Runner) <= 6) {
                    g.log(self, "ends the run")
                    g.push(EndRunEffect)
                }
                NextAction
        }
    }

    object HedgeFund extends SG("30075") {
        override def onPlay(self : Card)(implicit g : Game) = $(gain(Corp, 9))
    }

    object Catalyst extends SG("30076")
    object Syndicate extends SG("30077")

    val all : $[CardImpl] = $(
        Loup, WildcatStrike, Carnivore, Botulus, Buzzsaw, Cleaver, Fermenter, Leech, Cookbook, Zahya, MutualFavor, TreadLightly,
        DocklandsPass, Pennyshaver, Carmen, Marjanah, Tranquilizer, RedTeam, Tao, CreativeCommission, VRcation, DZMZOptimizer,
        Pantograph, Conduit, Echelon, Unity, TeleworkContract, Jailbreak, Overclock, SureGamble, T400, Mayfly, SmartwareDistributor,
        VerbalPlasticity,
        PrecisionDesign, LuminalTransubstantiation, NicoCampaign, Ansel, Bran, SeamlessLaunch, Sprint, ManegarmSkunkworks,
        RestoringHumanity, LongevitySerum, UrticaCipher, Diviner, Karuna, HanseiReview, Neurospike, AnoeticVoid, RealityPlus,
        TomorrowsHeadline, SpinDoctor, Funhouse, Ping, PredictivePlanogram, PublicTrail, AmazeAmusements, BuiltToLast, AboveTheLaw,
        Clearinghouse, Ballista, Pharos, GovernmentSubsidy, Retribution, MalapertDataVault, OffworldOffice, OrbitalSuperiority,
        SendAMessage, SuperconductingHub, RegolithMiningLicense, Palisade, Tithe, Whitespace, HedgeFund, Catalyst, Syndicate,
    )
}

case class PantographChooseAction(card : Card) extends CardAction
case class KarunaAction(card : Card) extends CardAction
