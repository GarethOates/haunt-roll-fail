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
// Elevation (elev) card implementations, ported from jinteki.net src/clj/game/cards/*.clj
// ---------------------------------------------------------------------------------------------------------------------

// card specific actions and purposes
case class RyoTrashPurpose(card : Card) extends CardPurpose
case class TopanPurpose(card : Card) extends CardPurpose
case class ScroungeBottomAction(card : Card) extends CardAction
case class ShredAction(card : Card) extends CardAction
case class BlingHostAction(card : Card) extends CardAction
case class BlingTrashAction(card : Card) extends CardAction
case class BlingPurpose(card : Card) extends CardPurpose
case class MuslihatAction(card : Card) extends CardAction
case class MuslihatPurpose(card : Card) extends CardPurpose
case class DetenteHostAction(card : Card) extends CardAction
case class DetenteHostedAction(card : Card, shuffled : $[Card]) extends ShuffledAction[Card] with SelfPerform {
    def perform(soft : Void)(implicit g : Game) = {
        g.push(DetenteHostedDoneAction(card, shuffled))
        NextAction
    }
}
case class DetenteHostedDoneAction(card : Card, cards : $[Card]) extends CardAction
case class DetenteReturnAction(card : Card, f : Faction) extends CardAction
case class DetenteReturnPurpose(card : Card) extends CardPurpose
case class MaglectricPurpose(card : Card) extends CardPurpose
case class OpenMarketPayAction(card : Card, installed : Card) extends CardAction
case class GamedragonPurpose(card : Card) extends CardPurpose
case class MadaniPurpose(card : Card) extends CardPurpose
case class ChromatophoresAction(card : Card, add : Boolean) extends CardAction
case class KnickknackPurpose(card : Card) extends CardPurpose
case class LeoPurpose(card : Card) extends CardPurpose
case class PoetriPurpose(card : Card) extends CardPurpose
case class HumanoidPlayAction(card : Card) extends CardAction
case class OttoAction(card : Card) extends CardAction
case class MerciaPurpose(card : Card) extends CardPurpose
case class MerciaMoveAction(card : Card, ice : Card) extends CardAction
case class AuAction(card : Card) extends CardAction
case class AuPurpose(card : Card) extends CardPurpose
case class PtPurpose(card : Card) extends CardPurpose
case class PhatAction(card : Card, n : Int) extends CardAction
case class PeerReviewPurpose(card : Card) extends CardPurpose
case class PeerInstallPurpose(card : Card) extends CardPurpose
case class MitraSwapAction(card : Card, ice : Card) extends CardAction
case class MitraPurpose(card : Card, ice : Card) extends CardPurpose
case class SynapsePurpose(card : Card) extends CardPurpose
case class EmbeddedPurpose(card : Card) extends CardPurpose
case class NbtShuffleAction(card : Card) extends CardAction
case class IdioAction(card : Card) extends CardAction
case class DoomscrollAction(card : Card) extends CardAction
case class NPotAction(card : Card, n : Int) extends CardAction
case class BiggerPictureAction(card : Card, n : Int) extends CardAction
case class IpPurpose(card : Card) extends CardPurpose
case class TouchupsPurpose(card : Card) extends CardPurpose
case class TouchupsTypeAction(card : Card, kind : String) extends CardAction
case class BangunFaceupAction(card : Card, agenda : Card) extends CardAction
case class OtbPurpose(card : Card) extends CardPurpose
case class PlutusRezAction(card : Card) extends CardAction
case class PlutusDerezAction(card : Card) extends CardAction
case class KpiAction(card : Card, done : $[String]) extends CardAction
case class KpiShuffleAction(card : Card) extends CardAction
case class LamplighterAction(card : Card) extends CardAction
case class MahkotaPayAction(card : Card, rezzed : Card) extends CardAction
case class SemakAction(card : Card) extends CardAction
case class MagdalenePurpose(card : Card) extends CardPurpose


object ElevCards {
    // ------------------------------------------------------------------------------------------------------------
    // Runner
    // ------------------------------------------------------------------------------------------------------------

    object Ryo extends Startup("35001") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(_) if g.run.exists(_.subsFired > 0) && !g.usedThisTurn(self, "ryo") =>
                $(MarkUsedEffect(self, "ryo"), gain(Runner, 1)) ++ g.hand(Corp).any.$(ChooseCardEffect(Corp, g.hand(Corp), TrashFromHQPurpose(self, false), "Ryo Ono: choose a card in HQ to trash", false))
            case _ => $
        }
    }

    object Topan extends Startup("35002") {
        override def abilities(self : Card)(implicit g : Game) = {
            val l = runnerInstallable(g.hand(Runner), 2).%!(c => g.impl(c).mustBeHosted(c))
            $(Ability("install a card paying 2 credits less (then suffer 1 meat damage)", click(), $(ChooseCardEffect(Runner, l, TopanPurpose(self), "Topan: choose a card to install paying 2 credits less", true)), click = true, once = once("topan"), ok = l.any, reason = "nothing to install"))
        }
        override def chosenCard(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(c => g.push(DiscountInstallAction(p.card, c, 2, None, None), meat(1)))
            NextAction
        }
    }

    object CharmOffensive extends Startup("35003") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = $(RunEffect(Archives, |(self)))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case RunEnds(_, _, _) if thisCardRun(self) =>
                val titles = g.run./(_.accessed).|($).%(c => g.zone(c) == DiscardZone(Corp))./(_.title).distinct
                val l = g.installedCards(Corp).%(c => g.isRezzed(c) && titles.has(c.title) && !cannotBeTrashedByRunner(c))
                l.any.$(ChooseCardEffect(Runner, l, RunnerTrashPurpose(self), "Charm Offensive: trash a rezzed copy of a card accessed in Archives?", true))
            case _ => $
        }
    }

    object Scrounge extends Startup("35004") {
        override def canPlay(self : Card)(implicit g : Game) = g.clicks(Runner) >= 2
        override def onPlay(self : Card)(implicit g : Game) = {
            val l = runnerInstallable(g.discard(Runner).%(_.data.isProgram), 0)
            $(LoseClicksEffect(Runner, 1)) ++ l.any.$(ChooseCardEffect(Runner, l, InstallLoopPurpose(self, Runner, 1, 0, false, "discard", $("Program")), "Scrounge: install a program from the heap", true)) :+ ScroungeBottomAction(self)
        }
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case ScroungeBottomAction(self) =>
                val l = g.discard(Runner).%(_.data.isProgram)
                if (l.any)
                    g.push(ChooseCardEffect(Runner, l, ToDeckPurpose(self, Runner, false, false), "Scrounge: add a program from the heap to the bottom of the stack?", true))
                NextAction
        }
    }

    object Shred extends Startup("35005") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = $(RunAnyServerEffect(|(self), g.servers))
        override def endRunPrevention(self : Card)(implicit g : Game) =
            (thisCardRun(self) && !g.state(self).usedRun.has("shred")).?(ShredAction(self))
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case ShredAction(self) =>
                g.state(self).usedRun :+= "shred"
                val x = g.run./(r => g.root(r.server).num).|(0)
                if (x == 0)
                    g.push(EndRunEffect)
                else
                    g.push(ChooseOneEffect(Corp, "Shred: the run does not end unless you reveal and trash " + x + " random cards from HQ", $(
                        Alt("Reveal and trash " + x + " random cards from HQ", $(TrashRandomFromHandEffect(self, Corp, x, true), EndRunEffect), g.hand(Corp).num >= x),
                        Alt("The run does not end", $(MessageEffect(Runner, "prevents the run from ending (Shred)"))))))
                NextAction
        }
    }

    object Bling extends Startup("35006") {
        override def memory(self : Card)(implicit g : Game) = 1
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Installed(Runner, c) if c != self && g.installCost(c, None) == 0 && g.deck(Runner).any =>
                $(OptionalEffect(Runner, "Bling: host the top card of the stack?", $(BlingHostAction(self)), $))
            case TurnEnds(Runner) if g.stashed(self).any => $(BlingTrashAction(self))
            case _ => $
        }
        override def abilities(self : Card)(implicit g : Game) = {
            val l = g.stashed(self).%(c => (c.data.isEvent && g.credits(Runner) >= g.playCost(c) && g.impl(c).canPlay(c)) || (!c.data.isEvent && runnerCanInstall(c, 0) && !g.impl(c).mustBeHosted(c)))
            $(Ability("play or install a hosted card", click(), $(ChooseCardEffect(Runner, l, BlingPurpose(self), "Bling: choose a hosted card to play or install", true)), click = true, ok = l.any, reason = "no playable hosted card"))
        }
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case BlingHostAction(self) =>
                g.deck(Runner).starting.foreach { c =>
                    g.moveTo(c, StashZone(self))
                    g.state(c).seen = true
                    g.log(c, "is hosted on", self)
                }
                NextAction
            case BlingTrashAction(self) =>
                g.stashed(self).foreach { c =>
                    g.moveTo(c, DiscardZone(Runner))
                    g.log(c, "is trashed from", self)
                }
                NextAction
        }
        override def chosenCard(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach { c =>
                if (c.data.isEvent)
                    g.push(PlayCardEffect(Runner, c, true, false))
                else
                    g.push(RunnerInstallEffect(c, None, false))
            }
            NextAction
        }
        override def onTrash(self : Card, by : Faction)(implicit g : Game) = { g.uninstall(self) ; $ }
    }

    object Gourmand extends Startup("35007") {
        override def accessAbility(self : Card, target : Card)(implicit g : Game) =
            (!target.data.isAgenda && !g.zone(target).is[DiscardZone]).?(Ability("trash the accessed card and draw 1 card", $(TrashSelfCost), $(TrashEffect(target, Runner, true), draw(Runner, 1)), ok = g.run.exists(!_.cannotStealOrTrash) && !g.accessProtected(target) && !cannotBeTrashedByRunner(target), reason = "cannot trash"))
    }

    object Hantu extends Startup("35008") {
        override def initialCounters(self : Card)(implicit g : Game) = Map("virus" -> 2)
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(1), 1, $("Sentry")))
        override def pumps(self : Card)(implicit g : Game) = $(Pump($(VirusCost(1)), 2))
    }

    object RisingTide extends Startup("35009") {
        override def strength(self : Card)(implicit g : Game) = g.discard(Runner).%(_.data.has("Fracter")).num
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(1), 1, $("Barrier")))
        override def pumps(self : Card)(implicit g : Game) = $(Pump(c(1), 1))
    }

    object Cacophony extends Startup("35010") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Trashed(c, Runner, _) if g.owner(c).isCorp && !g.usedThisTurn(self, "cacophony") => $(MarkUsedEffect(self, "cacophony"), AddCounterEffect(self, "power", 1))
            case AgendaStolen(_) if !g.usedThisTurn(self, "cacophony") => $(MarkUsedEffect(self, "cacophony"), AddCounterEffect(self, "power", 1))
            case ActionPhaseEnds(Runner) if g.counter(self, "power") >= 2 =>
                $(OptionalEffect(Runner, "Cacophony: remove 2 power counters to sabotage 3?", $(AddCounterEffect(self, "power", -2), SabotageEffect(3)), $))
            case _ => $
        }
    }

    object RentRioters extends Startup("35011") {
        override def abilities(self : Card)(implicit g : Game) = $(Ability("gain 9 credits", $(ClickCost(3), TrashSelfCost), $(gain(Runner, 9)), click = true))
    }

    object BazWong extends Startup("35012") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Rezzed(c) if c.data.isIce =>
                val l = runnerInstallable(g.hand(Runner).%(x => x.data.isResource || x.data.isHardware), 0)
                l.any.$(ChooseCardEffect(Runner, l, InstallLoopPurpose(self, Runner, 1, 0, false, "hand", $("Resource", "Hardware")), "Baz Wong: install a resource or piece of hardware?", true))
            case _ => $
        }
    }

    object Muslihat extends Startup("35013") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) if g.deck(Runner).any => $(MuslihatAction(self))
            case _ => $
        }
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case MuslihatAction(self) =>
                g.deck(Runner).starting.foreach { c =>
                    g.log(Runner, "looks at the top card of the stack")
                    if ((c.data.isEvent && c.data.has("Run")) || (c.data.isProgram && c.data.isIcebreaker))
                        g.push(ChooseCardEffect(Runner, $(c), MuslihatPurpose(self), "MuslihaT: reveal the top card of the stack and add it to the grip?", true))
                    else
                        g.push(ChooseCardEffect(Runner, $(c), LookPurpose(self), "MuslihaT: the top card of the stack stays (choose it or decline)", true))
                }
                NextAction
        }
        override def chosenCard(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.foreach { c =>
                g.log(Runner, "reveals", c, "and adds it to the grip")
                g.moveTo(c, HandZone(Runner))
            }
            NextAction
        }
    }

    object CleanGetaway extends Startup("35014") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = $(RunAnyServerEffect(|(self), g.servers))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(_) if thisCardRun(self) => $(gain(Runner, 6))
            case _ => $
        }
    }

    object LieLow extends Startup("35015") {
        override def canPlay(self : Card)(implicit g : Game) = g.clicks(Runner) >= 2
        override def onPlay(self : Card)(implicit g : Game) = $(LoseClicksEffect(Runner, 1), ChooseOneEffect(Runner, "Lie Low: choose one", $(
            Alt("Draw 4 cards", $(draw(Runner, 4))),
            Alt("Remove up to 2 tags", $(ChooseOneEffect(Runner, "Lie Low: remove how many tags?", $(
                Alt("Remove 2 tags", $(RemoveTagsEffect(2)), g.tags >= 2),
                Alt("Remove 1 tag", $(RemoveTagsEffect(1)), g.tags >= 1),
                Alt("Remove no tags", $)))), g.tagged))))
    }

    object MaintenanceAccess extends Startup("35016") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def canPlay(self : Card)(implicit g : Game) = g.clicks(Runner) >= 2
        override def onPlay(self : Card)(implicit g : Game) = $(LoseClicksEffect(Runner, 1), RunEffect(Archives, |(self)))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case ApproachServer(Archives) if thisCardRun(self) => $(MessageEffect(Runner, "changes the attacked server to HQ (Maintenance Access)"), RedirectRunEffect(HQ, 0, false))
            case _ => $
        }
    }

    object TransferOfWealth extends Startup("35017") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = $(RunEffect(HQ, |(self)))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(HQ) if thisCardRun(self) =>
                val cc = 3.atMost(g.credits(Corp))
                $(tag(1)) ++ (cc > 0).??($(LoseCreditsEffect(Corp, cc), gain(Runner, 2 * cc)))
            case _ => $
        }
    }

    object Detente extends Startup("35018") {
        override def memory(self : Card)(implicit g : Game) = 1
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(HQ) if !g.usedThisTurn(self, "detente") && g.hand(Corp).any =>
                $(MarkUsedEffect(self, "detente"), OptionalEffect(Runner, "Detente: reveal and host a random card from HQ?", $(DetenteHostAction(self)), $))
            case _ => $
        }
        def returnAbility(self : Card, f : Faction)(implicit g : Game) =
            Ability("add 2 hosted cards to HQ: the Runner may access 1 card in HQ at random", click(), $(DetenteReturnAction(self, f)), click = true, ok = g.stashed(self).num >= 2, reason = "needs 2 hosted cards")
        override def abilities(self : Card)(implicit g : Game) = $(returnAbility(self, Runner))
        override def corpAbilities(self : Card)(implicit g : Game) = $(returnAbility(self, Corp))
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case DetenteHostAction(self) =>
                if (g.hand(Corp).any)
                    ShuffleTake[Card](g.hand(Corp), 1, DetenteHostedAction(self, _), "Detente")
                else
                    NextAction
            case DetenteHostedDoneAction(self, l) =>
                l.foreach { c =>
                    g.log(Runner, "reveals", c, "from HQ and hosts it on", self)
                    g.moveTo(c, StashZone(self))
                    g.state(c).seen = true
                }
                NextAction
            case DetenteReturnAction(self, f) =>
                g.push(ChooseCardsEffect(f, g.stashed(self), 2, 2, DetenteReturnPurpose(self), "Detente: choose 2 hosted cards to add to HQ"))
                NextAction
        }
        override def chosenCard(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.foreach { c =>
                g.moveTo(c, HandZone(Corp))
                g.log(c, "is added to HQ")
            }
            g.push(OptionalEffect(Runner, "Detente: access 1 card in HQ at random?", $(AccessRandomHQEffect(p.card)), $))
            NextAction
        }
        override def onTrash(self : Card, by : Faction)(implicit g : Game) = { g.uninstall(self) ; $ }
    }

    object Maglectric extends Startup("35019") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(HQ) =>
                val l = g.installedCards(Corp).%(c => g.isRezzed(c) && !c.data.isAgenda)
                l.any.$(OptionalEffect(Runner, "Maglectric Rapid: trash it to derez an installed Corp card?", $(ChooseCardEffect(Runner, l, MaglectricPurpose(self), "Maglectric Rapid: choose a card to derez", false)), $))
            case _ => $
        }
        override def chosenCard(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            g.trashCard(p.card, Runner, false)
            cards.foreach(c => g.push(DerezEffect(c)))
            NextAction
        }
    }

    object SangKancil extends Startup("35020") {
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(1), 1, $("Code Gate")))
        override def pumps(self : Card)(implicit g : Game) = {
            val discount = g.at(PlayZone(Runner)).exists(c => c.data.isEvent && c.data.has("Run"))
            $(Pump(c(discount.?(1).|(3)), 2))
        }
    }

    object FransofiaWard extends Startup("35021") {
        override def rezCostMod(self : Card, card : Card)(implicit g : Game) = card.data.isIce.?(1).|(0)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case EncounterIce(ice) if g.credits(Corp) >= 15 => $(OptionalEffect(Runner, "Fransofia Ward: trash it to bypass the encountered ice?", $(TrashEffect(self, Runner, false), BypassEffect), $))
            case _ => $
        }
    }

    object OpenMarket extends Startup("35022") {
        def eligible(c : Card)(implicit g : Game) = c.data.isResource && (g.has(c, "Connection") || g.has(c, "Job"))
        override def initialCounters(self : Card)(implicit g : Game) = Map("credit" -> 6)
        override def installCostMod(self : Card, card : Card)(implicit g : Game) = (card != self && eligible(card)).?(-g.counter(self, "credit")).|(0)
        override def triggers(self : Card, e : Event)(implicit g : Game) = trashOnEmpty(self, e, "credit") ++ (e match {
            case TurnBegins(Runner) if g.counter(self, "credit") > 0 => $(TakeCreditsEffect(Runner, self, 1))
            case Installed(Runner, c) if c != self && eligible(c) && c.data.cost.|(0) > 0 && g.counter(self, "credit") > 0 => $(OpenMarketPayAction(self, c))
            case _ => $
        })
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case OpenMarketPayAction(self, c) =>
                val m = g.counter(self, "credit").atMost(c.data.cost.|(0))
                if (m > 0) {
                    g.log(Runner, "spends", m.credits, "from", self)
                    g.push(AddCounterEffect(self, "credit", -m))
                }
                NextAction
        }
    }

    object Dewi extends Startup("35023") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(_) if !flipped(self) && g.memoryFree == 0 => $(OptionalEffect(Runner, "Dewi: flip the identity and gain 1 credit?", $(gain(Runner, 1), flip(self, "Dewi Subrotoputri: Shadow Guide")), $))
            case SuccessfulRun(_) if flipped(self) && g.memoryFree > 0 => $(OptionalEffect(Runner, "Dewi: flip the identity and draw 1 card?", $(draw(Runner, 1), flip(self, "Dewi Subrotoputri: Shadow Guide")), $))
            case _ => $
        }
    }

    object Magdalene extends Startup("35024") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case DiscardedToHandSize(Runner, l) =>
                val ll = l.%(c => (c.data.isProgram || c.data.isHardware) && runnerCanInstall(c, 0) && !g.impl(c).mustBeHosted(c))
                ll.any.$(ChooseCardEffect(Runner, ll, MagdalenePurpose(self), "Magdalene: install a discarded program or piece of hardware?", true))
            case _ => $
        }
        override def chosenCard(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(c => g.push(RunnerInstallEffect(c, None, false)))
            NextAction
        }
    }

    object Illumination extends Startup("35025") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = $(RunEffect(RnD, |(self)))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(RnD) if thisCardRun(self) => $(InstallLoopAction(self, Runner, 3, 1, false, "hand", $()))
            case _ => $
        }
    }

    object Ritual extends Startup("35026") {
        override def onPlay(self : Card)(implicit g : Game) = $(draw(Runner, g.clicks(Runner)))
    }

    object Gamedragon extends Startup("35027") {
        def hosts(self : Card)(implicit g : Game) = g.installedCards(Runner).%(c => c.data.isProgram && c.data.isIcebreaker && !g.has(c, "AI") && !g.state(self).host.has(c))
        def ask(self : Card)(implicit g : Game) = hosts(self).some./(l => ChooseCardEffect(Runner, l, GamedragonPurpose(self), "GAMEDRAGON Pro: host it on an icebreaker?", true)).$
        override def onInstall(self : Card)(implicit g : Game) = ask(self)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) => ask(self)
            case _ => $
        }
        override def chosenCard(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach { b =>
                g.moveTo(p.card, HostedZone(b))
                g.log(p.card, "is hosted on", b)
            }
            NextAction
        }
        override def breakerStrengthMod(self : Card, breaker : Card)(implicit g : Game) = g.state(self).host.has(breaker).?(1).|(0)
        override def pumpDuration(self : Card, breaker : Card, d : Duration)(implicit g : Game) = (g.state(self).host.has(breaker) && d == EncounterDuration).?(RunDuration : Duration).|(d)
    }

    object Madani extends Startup("35028") {
        override def abilities(self : Card)(implicit g : Game) = {
            val h = g.hand(Runner).%(_.data.isProgram)
            val l = g.stashed(self).%(c => runnerCanInstall(c, 0) && !g.impl(c).mustBeHosted(c))
            $(
                Ability("host any number of programs from the grip", click(), $(ChooseCardsEffect(Runner, h, 0, h.num, StashPurpose(self, false), "Madani: choose programs to host")), click = true, ok = h.any, reason = "no programs in grip"),
                Ability("install a hosted program", NoCost, $(ChooseCardEffect(Runner, l, MadaniPurpose(self), "Madani: choose a hosted program to install", true)), once = once("madani"), ok = l.any, reason = "no installable hosted program"))
        }
        override def chosenCard(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(c => g.push(RunnerInstallEffect(c, None, false)))
            NextAction
        }
        override def onTrash(self : Card, by : Faction)(implicit g : Game) = { g.uninstall(self) ; $ }
    }

    object Azimat extends Startup("35029") {
        override def initialCounters(self : Card)(implicit g : Game) = Map("credit" -> 2)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) => $(RefillCreditsEffect(self, 2))
            case _ => $
        }
        override def accessAbility(self : Card, target : Card)(implicit g : Game) = {
            val hosted = g.counter(self, "credit")
            g.trashCost(target).%(_ => !target.data.isAgenda && !g.zone(target).is[DiscardZone] && hosted > 0)./ { tc =>
                val m = hosted.atMost(tc)
                Ability("pay " + tc + " credits to trash (" + m + " from Azimat)", $(CounterCost("credit", m)) ++ (tc > m).$(CreditCost(tc - m)), $(TrashEffect(target, Runner, true)), ok = g.run.exists(!_.cannotStealOrTrash) && g.impl(target).canBeTrashedOnAccess(target) && !g.accessProtected(target), reason = "cannot trash")
            }
        }
    }

    object Chromatophores extends Startup("35030") {
        override def mustBeHosted(self : Card)(implicit g : Game) = true
        override def hostRequirement(self : Card, host : Card)(implicit g : Game) = host.data.isIce
        override def onInstall(self : Card)(implicit g : Game) = $(ChromatophoresAction(self, true))
        override def onTrash(self : Card, by : Faction)(implicit g : Game) = $(ChromatophoresAction(self, false))
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case ChromatophoresAction(self, true) =>
                g.state(self).host.foreach { h =>
                    g.chromatophoreHosts += self -> h
                    g.push(ExtraSubtypesEffect(h, $("Barrier", "Code Gate", "Sentry"), true))
                    g.log(h, "gains barrier, code gate and sentry")
                }
                NextAction
            case ChromatophoresAction(self, false) =>
                g.chromatophoreHosts.get(self).foreach { h =>
                    g.chromatophoreHosts -= self
                    if (!g.chromatophoreHosts.values.exists(_ == h))
                        g.push(ExtraSubtypesEffect(h, $("Barrier", "Code Gate", "Sentry"), false))
                }
                NextAction
        }
    }

    object DevadattaDrone extends Startup("35031") {
        override def initialCounters(self : Card)(implicit g : Game) = Map("power" -> 2)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case BreachServer(RnD) if g.counter(self, "power") > 0 => $(OptionalEffect(Runner, "Devadatta Drone: remove 1 power counter to access 1 additional card?", $(AddCounterEffect(self, "power", -1), AccessBonusEffect(RnD, 1)), $))
            case _ => $
        }
    }

    object Principia extends Startup("35032") {
        override def installCostMod(self : Card, card : Card)(implicit g : Game) = (card == self).?(-g.installedCards(Runner).%(c => c != self && c.data.isIcebreaker).num).|(0)
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(1), 1, $("Barrier")))
        override def pumps(self : Card)(implicit g : Game) = $(Pump(c(2), 2))
    }

    object Knickknack extends Startup("35033") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case RunInitiated(_) if g.reg("runs") == 1 =>
                val l = g.installedCards(Runner).but(self)
                l.any.$(ChooseCardEffect(Runner, l, KnickknackPurpose(self), "Knickknack O'Brian: trash an installed card to gain its install cost and draw 1 card?", true))
            case _ => $
        }
        override def chosenCard(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach { c =>
                val n = c.data.cost.|(0)
                g.trashCard(c, Runner, false)
                g.push(gain(Runner, n), draw(Runner, 1))
            }
            NextAction
        }
    }

    object SideHustle extends Startup("35034") {
        override def initialCounters(self : Card)(implicit g : Game) = Map("credit" -> 1)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case RunInitiated(_) => $(AddCounterEffect(self, "credit", 1))
            case CounterAdded(c, "credit") if c == self && g.counter(self, "credit") >= 6 && g.isInstalled(self) =>
                $(TakeCreditsEffect(Runner, self, 99), draw(Runner, 1), TrashEffect(self, Runner, false))
            case _ => $
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Corp
    // ------------------------------------------------------------------------------------------------------------

    object LeoConstruction extends Startup("35035") {
        def candidates(implicit g : Game) = g.activeRun./(r => (g.ice(r.server) ++ g.root(r.server)).%(c => g.isRezzed(c) && g.has(c, "Bioroid"))).|($)
        override def abilities(self : Card)(implicit g : Game) = {
            val l = candidates
            $(Ability("trash a rezzed bioroid in or protecting the attacked server: end the run", NoCost, $(ChooseCardEffect(Corp, l, LeoPurpose(self), "LEO Construction: choose a bioroid to trash", false)), once = once("leo"), ok = l.any, reason = "no rezzed bioroid in the attacked server"))
        }
        override def chosenCard(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.foreach(c => g.trashCard(c, Corp, false))
            g.push(EndRunEffect)
            NextAction
        }
    }

    object Poetri extends Startup("35036") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case AgendaScored(_) if g.deck(Corp).any =>
                $(OptionalEffect(Corp, "Poetri Luxury Brands: look at the top 3 cards of R&D?", $(ChooseCardEffect(Corp, g.deck(Corp).take(3).%(c => !c.data.isAgenda && !c.data.isOperation), PoetriPurpose(self), "Poetri Luxury Brands: install a non-agenda card from the top 3 cards of R&D?", true)), $))
            case AgendaStolen(_) =>
                val l = g.hand(Corp).%(c => !c.data.isAgenda && !c.data.isOperation)
                l.any.$(ChooseCardEffect(Corp, l, InstallChooseServerPurpose(false), "Poetri Luxury Brands: install a non-agenda card from HQ?", true))
            case _ => $
        }
        override def chosenCard(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach { c =>
                g.log(Corp, "installs a card from the top of R&D (Poetri Luxury Brands)")
                g.push(ChooseServerEffect(Corp, c, false, (c.data.isIce || c.data.isUpgrade).?(g.servers).|(g.remotes), true))
            }
            NextAction
        }
    }

    object AggressiveTrendsetting extends Startup("35037") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Trashed(c, Runner, _) if inCorpScore(self) && g.owner(c).isCorp && g.inPlayZone(g.lastTrashedFrom) && g.active.isRunner && !g.usedThisTurn(self, "trendsetting") =>
                $(MarkUsedEffect(self, "trendsetting"), ChooseOneEffect(Runner, "Aggressive Trendsetting: spend [click] to deny the Corp +1 allotted click next turn?", $(
                    Alt("Spend [click]", $(LoseClicksEffect(Runner, 1)), g.clicks(Runner) > 0),
                    Alt("Do not spend a click", $(ExtraClicksEffect(Corp, 1))))))
            case _ => $
        }
    }

    object ProjectIngatan extends Startup("35038") {
        override def onScore(self : Card)(implicit g : Game) = dividends(self, 1)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnEnds(Corp) if inCorpScore(self) && g.counter(self, "agenda") > 0 && g.discard(Corp).exists(c => corpInstallable(c)) =>
                $(OptionalEffect(Corp, "Project Ingatan: remove 1 agenda counter to install a card from Archives ignoring all costs?", $(AddCounterEffect(self, "agenda", -1), ChooseCardEffect(Corp, g.discard(Corp).%(corpInstallable), InstallChooseServerPurpose(true), "Project Ingatan: choose a card in Archives to install", true)), $))
            case _ => $
        }
    }

    object HumanoidResources extends Startup("35039") {
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("gain 4 credits, draw 3 cards, install up to 2 cards, play 1 operation", $(ClickCost(3), TrashSelfCost), $(gain(Corp, 4), draw(Corp, 3), InstallLoopAction(self, Corp, 2, 0, false, "hand", $()), HumanoidPlayAction(self)), click = true, ok = g.isRezzed(self), reason = "not rezzed"))
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case HumanoidPlayAction(self) =>
                val l = g.hand(Corp).%(c => c.data.isOperation && g.credits(Corp) >= g.playCost(c) && g.impl(c).canPlay(c))
                if (l.any)
                    g.push(ChooseCardEffect(Corp, l, PlayPurpose(self, true, false), "Humanoid Resources: play an operation from HQ?", true))
                NextAction
        }
    }

    object OttoCampaign extends Startup("35040") {
        override def initialCounters(self : Card)(implicit g : Game) = Map("credit" -> 6)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) if g.isRezzed(self) => $(OttoAction(self))
            case _ => $
        }
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case OttoAction(self) =>
                val n = 2.atMost(g.counter(self, "credit"))
                g.addCounter(self, "credit", -n)
                g.credits += Corp -> (g.credits(Corp) + n)
                g.log(Corp, "takes", n.credits, "from", self)
                if (g.counter(self, "credit") == 0) {
                    g.trashCard(self, Corp, false)
                    g.push(GainClicksEffect(Corp, 2))
                }
                NextAction
        }
    }

    object Bumi extends Startup("35041") {
        override def onRez(self : Card)(implicit g : Game) = {
            val l = g.installedCards(Runner).%(c => c.data.isProgram && g.has(c, "Trojan"))
            (runOnThisServer(self) && l.any).$(ChooseCardEffect(Corp, l, TrashPurpose(Corp), "Bumi 1.0: trash a trojan program?", true))
        }
        override def runnerAbilities(self : Card)(implicit g : Game) = bioroidBreak(self, 1, 1)
        override def subroutines(self : Card)(implicit g : Game) = $(
            Sub("Trash 1 installed program", ChooseCardEffect(Corp, g.installedCards(Runner).%(_.data.isProgram), TrashPurpose(Corp), "Bumi 1.0: choose a program to trash", false)),
            Sub("Do 1 core damage", core(1)))
    }

    object ScatterField extends Startup("35042") {
        override def strength(self : Card)(implicit g : Game) = g.serverOf(self).exists(s => g.ice(s).num == 1).?(4).|(0)
        override def subroutines(self : Card)(implicit g : Game) = $(
            Sub("You may install 1 card from HQ", ChooseCardEffect(Corp, g.hand(Corp).%(corpInstallable), InstallChooseServerPurpose(false), "Scatter Field: install a card from HQ?", true)),
            etrSub)
    }

    object Nanomanagement extends Startup("35043") {
        override def onPlay(self : Card)(implicit g : Game) = $(GainClicksEffect(Corp, 2))
    }

    object TopDownSolutions extends Startup("35044") {
        override def onPlay(self : Card)(implicit g : Game) = $(draw(Corp, 2), InstallLoopAction(self, Corp, 2, 0, false, "hand", $()))
    }

    object Mercia extends Startup("35045") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case ActionPhaseEnds(Corp) if g.isRezzed(self) && g.hand(Corp).exists(_.data.isIce) =>
                $(ChooseCardEffect(Corp, g.hand(Corp).%(_.data.isIce), MerciaPurpose(self), "Mercia B4LL4RD: install a piece of ice paying 1 credit less?", true))
            case _ => $
        }
        override def chosenCard(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(c => g.push(ChooseServerForAction(Corp, p.card, g.servers, true, "mercia:" + c.code + ":" + c.n, "Mercia B4LL4RD: choose a server for the ice")))
            NextAction
        }
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case ServerPickedAction(self, server, key) =>
                val parts = key.split(':')
                val c = Card(parts(1), parts(2).toInt)
                g.push(DiscountInstallAction(self, c, 1, None, server), MerciaMoveAction(self, c))
                NextAction
            case MerciaMoveAction(self, c) =>
                (g.serverOf(c), g.serverOf(self)) match {
                    case (Some(s), Some(own)) if s != own && g.isInstalled(c) => g.push(MoveToRootEffect(self, s))
                    case _ =>
                }
                NextAction
        }
    }

    object AuCo extends Startup("35046") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case DamageDone(_, n) if n > 0 => $(AddCounterEffect(self, "power", 1))
            case Trashed(c, Corp, _) if g.owner(c).isCorp && g.lastTrashedFrom == HandZone(Corp) => $(AddCounterEffect(self, "power", 1))
            case TurnBegins(Corp) if g.counter(self, "power") >= 2 && g.deck(Corp).any =>
                $(OptionalEffect(Corp, "AU Co.: remove 2 power counters to look at the top 3 cards of R&D?", $(AddCounterEffect(self, "power", -2), AuAction(self)), $))
            case _ => $
        }
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case AuAction(self) =>
                val l = g.deck(Corp).take(3)
                g.log(Corp, "looks at the top", l.num.cards, "of R&D")
                g.push(ChooseCardEffect(Corp, l, AuPurpose(self), "AU Co.: choose a card to trash (the others are added to HQ)", false))
                NextAction
        }
        override def chosenCard(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            val l = g.deck(Corp).take(3)
            cards.single.foreach { c =>
                g.log(Corp, "trashes a card from the top of R&D")
                g.toArchives(c, false)
            }
            l.%!(cards.has).foreach { c =>
                g.moveTo(c, HandZone(Corp))
            }
            g.log(Corp, "adds", l.%!(cards.has).num.cards, "to HQ")
            NextAction
        }
    }

    object PtUntaian extends Startup("35047") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnEnds(Corp) if g.hand(Corp).num <= 3 && g.credits(Corp) >= 1 =>
                val l = g.installedCards(Corp).%(c => !g.isRezzed(c) && g.isAdvanceable(c))
                l.any.$(OptionalEffect(Corp, "PT Untaian: pay 1 credit to place 1 advancement counter on an unrezzed card?", $(ChooseCardEffect(Corp, l, PtPurpose(self), "PT Untaian: choose a card to advance", false)), $))
            case _ => $
        }
        override def chosenCard(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach { c =>
                g.credits += Corp -> (g.credits(Corp) - 1)
                g.log(Corp, "pays", 1.credits)
                g.push(PlaceAdvancementEffect(c, 1))
            }
            NextAction
        }
    }

    object Proprionegation extends Startup("35048") {
        override def onScore(self : Card)(implicit g : Game) = $(AddCounterEffect(self, "agenda", 1))
        override def abilities(self : Card)(implicit g : Game) = {
            val ok = inCorpScore(self) && g.activeRun.exists(r => r.phase != SuccessPhase && r.phase != AccessPhase && r.phase != InitiationPhase)
            $(Ability("the Runner moves to the outermost position of Archives", $(CounterCost("agenda", 1)), $(RedirectRunEffect(Archives, g.ice(Archives).num, true)), ok = ok, reason = "only during a run"))
        }
    }

    object SericultureExpansion extends Startup("35049") {
        override def onScore(self : Card)(implicit g : Game) = dividends(self, 1)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnEnds(Corp) if inCorpScore(self) && g.counter(self, "agenda") > 0 && g.installedCards(Corp).any =>
                $(OptionalEffect(Corp, "Sericulture Expansion: remove 1 agenda counter to place 2 advancement counters on an installed card?", $(AddCounterEffect(self, "agenda", -1), ChooseCardEffect(Corp, g.installedCards(Corp), AdvancePurpose(2), "Sericulture Expansion: choose a card", false)), $))
            case _ => $
        }
    }

    object Byte extends Startup("35050") {
        override def onAccess(self : Card)(implicit g : Game) =
            (!g.zone(self).is[DiscardZone]).??((g.zone(self) == DeckZone(Corp)).$(RevealEffect(Corp, self)) ++ (g.credits(Corp) >= 4).$(OptionalEffect(Corp, "Byte!: pay 4 credits to give the Runner 1 tag and do 3 net damage?", $(LoseCreditsEffect(Corp, 4), tag(1), net(3)), $)))
    }

    object Phat extends Startup("35051") {
        def ask(self : Card)(implicit g : Game) = {
            val p = g.counter(self, "power")
            $(MarkUsedEffect(self, "phat"), ChooseOneEffect(Corp, "Phat Gioan Baotixita: remove up to 2 power counters to do net damage", $(
                Alt("Do 1 net damage", $(net(1))),
                Alt("Remove 1 power counter: do 2 net damage", $(PhatAction(self, 1)), p >= 1),
                Alt("Remove 2 power counters: do 3 net damage", $(PhatAction(self, 2)), p >= 2))))
        }
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnEnds(Corp) if g.isRezzed(self) => $(AddCounterEffect(self, "power", 1))
            case AgendaScored(_) | AgendaStolen(_) if g.isRezzed(self) && !g.usedThisTurn(self, "phat") => ask(self)
            case _ => $
        }
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case PhatAction(self, n) =>
                g.addCounter(self, "power", -n)
                g.push(net(1 + n))
                NextAction
        }
    }

    object Empiricist extends Startup("35052") {
        override def subroutines(self : Card)(implicit g : Game) = $(
            Sub("Draw 1 card. You may add 1 card from HQ to the top of R&D", EmpiricistAction(self, 1)),
            Sub("Do 1 net damage. Give the Runner 1 tag", EmpiricistAction(self, 2)),
            netSub(2))
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case EmpiricistAction(self, 1) =>
                g.push(draw(Corp, 1), EmpiricistAction(self, 3))
                NextAction
            case EmpiricistAction(self, 2) =>
                g.push(net(1), tag(1))
                NextAction
            case EmpiricistAction(self, 3) =>
                if (g.hand(Corp).any)
                    g.push(ChooseCardEffect(Corp, g.hand(Corp), ToDeckPurpose(self, Corp, true, false), "Empiricist: add a card from HQ to the top of R&D?", true))
                NextAction
        }
    }

    object Mycoweb extends Startup("35053") {
        override def subroutines(self : Card)(implicit g : Game) = $(
            Sub("You may install 1 piece of ice from Archives, ignoring all costs", ChooseCardEffect(Corp, g.discard(Corp).%(_.data.isIce), InstallChooseServerPurpose(true), "Mycoweb: install a piece of ice from Archives?", true)),
            Sub("You may rez 1 installed piece of ice, paying 2 credits less", ChooseCardEffect(Corp, g.allIce.%(c => !g.isRezzed(c) && g.credits(Corp) >= (g.rezCost(c) - 2).atLeast(0)), RezDiscountPurpose(self, 2), "Mycoweb: rez a piece of ice paying 2 credits less?", true)),
            Sub("Resolve 1 subroutine on a rezzed sentry", ChooseCardEffect(Corp, g.rezzedIce.%(c => g.has(c, "Sentry")), SubChoicePurpose(self), "Mycoweb: choose a rezzed sentry", true)),
            Sub("Resolve 1 subroutine on another rezzed code gate", ChooseCardEffect(Corp, g.rezzedIce.%(c => c != self && g.has(c, "Code Gate")), SubChoicePurpose(self), "Mycoweb: choose another rezzed code gate", true)))
    }

    object SemakSamun extends Startup("35054") {
        override def canBreakWith(self : Card, breaker : Card)(implicit g : Game) = g.has(breaker, "Fracter")
        override def subroutines(self : Card)(implicit g : Game) = $(Sub("End the run unless the Runner suffers 3 net damage", ChooseOneEffect(Runner, "Semak-samun", $(
            Alt("End the run", $(EndRunEffect)),
            Alt("Suffer 3 net damage", $(net(3)), g.hand(Runner).num >= 3)))))
    }

    object PeerReview extends Startup("35055") {
        override def onPlay(self : Card)(implicit g : Game) =
            if (g.hand(Corp).num >= 2)
                $(ChooseCardEffect(Corp, g.hand(Corp), PeerReviewPurpose(self), "Peer Review: choose a card in HQ to keep private (the others are revealed)", false))
            else
                $(gain(Corp, 7), PeerReviewInstallAction(self))
        override def chosenCard(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case PeerReviewPurpose(self) =>
                g.hand(Corp).%!(cards.has).foreach(c => g.push(RevealEffect(Corp, c)))
                g.push(gain(Corp, 7), PeerReviewInstallAction(self))
                NextAction
            case PeerInstallPurpose(self) =>
                cards.single.foreach(c => g.push(ChooseServerForAction(Corp, self, g.remotes, true, "peer:" + c.code + ":" + c.n, "Peer Review: choose a remote server")))
                NextAction
        }
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case PeerReviewInstallAction(self) =>
                val l = g.hand(Corp).%(c => !c.data.isIce && !c.data.isOperation)
                if (l.any)
                    g.push(ChooseCardEffect(Corp, l, PeerInstallPurpose(self), "Peer Review: install a card from HQ in the root of a remote server?", true))
                NextAction
            case ServerPickedAction(self, server, key) =>
                val parts = key.split(':')
                g.push(CorpInstallEffect(Card(parts(1), parts(2).toInt), server, false))
                NextAction
        }
    }

    object MitraAman extends Startup("35056") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case ApproachIce(ice) if thisServer(self, g.run./(_.server).|(HQ)) && g.isRezzed(self) && g.run.exists(r => !r.ended) =>
                $(OptionalEffect(Corp, "Mitra Aman: trash it to gain 3 credits and swap the approached ice?", $(TrashEffect(self, Corp, false), gain(Corp, 3), MitraSwapAction(self, ice)), $))
            case _ => $
        }
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case MitraSwapAction(self, ice) =>
                val l = (g.hand(Corp) ++ g.discard(Corp)).%(_.data.isIce)
                if (l.any && g.isInstalled(ice))
                    g.push(ChooseCardEffect(Corp, l, MitraPurpose(self, ice), "Mitra Aman: swap the approached ice with a piece of ice from HQ or Archives?", true))
                NextAction
        }
        override def chosenCard(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case MitraPurpose(self, ice) =>
                cards.single.foreach(c => g.push(SwapIceWithEffect(ice, c)))
                NextAction
        }
    }

    object Nebula extends Startup("35057") {
        val back = "Gemilang Arena: Burning Bright"
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case ActionPhaseEnds(Corp) if g.reg("played") > 0 && !flipped(self) => $(gain(Corp, 1), flip(self, back))
            case SuccessfulRun(s) if (s == HQ || s == RnD) && flipped(self) => $(flip(self, back))
            case Played(Corp, c) if flipped(self) && g.reg("played") == 1 && !g.has(c, "Terminal") => $(GainClicksEffect(Corp, 1))
            case _ => $
        }
    }

    object SynapseGlobal extends Startup("35058") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TagsRemoved(n) if n > 0 && !g.usedThisTurn(self, "synapse") =>
                val l = g.hand(Corp).%(corpInstallable)
                $(MarkUsedEffect(self, "synapse")) ++ l.any.$(ChooseCardEffect(Corp, l, SynapsePurpose(self), "Synapse Global: reveal and install a card from HQ ignoring all costs?", true))
            case _ => $
        }
        override def chosenCard(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(c => g.push(RevealEffect(Corp, c), ChooseServerEffect(Corp, c, true, (c.data.isIce || c.data.isUpgrade).?(g.servers).|(g.remotes), true)))
            NextAction
        }
        override def abilities(self : Card)(implicit g : Game) = $(Ability("remove 1 tag: gain 2 credits", click(), $(RemoveTagsEffect(1), gain(Corp, 2)), click = true, ok = g.tagged, reason = "Runner is not tagged"))
    }

    object EmbeddedReporting extends Startup("35059") {
        override def onScore(self : Card)(implicit g : Game) = dividends(self, 2)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnEnds(Corp) if inCorpScore(self) && g.counter(self, "agenda") > 0 && g.deck(Corp).exists(_.data.isOperation) =>
                $(OptionalEffect(Corp, "Embedded Reporting: remove 1 agenda counter to search R&D for an operation and put it on top?", $(AddCounterEffect(self, "agenda", -1), ChooseCardEffect(Corp, g.deck(Corp).%(_.data.isOperation).sortBy(_.title), EmbeddedPurpose(self), "Embedded Reporting: choose an operation", true)), $))
            case _ => $
        }
        override def chosenCard(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach { c =>
                g.log(Corp, "reveals", c, "from R&D")
                g.push(ShuffleDeckEffect(Corp), MoveEffect(c, DeckZone(Corp), true), MessageEffect(Corp, "adds the revealed operation to the top of R&D"))
            }
            NextAction
        }
    }

    object NextBigThing extends Startup("35060") {
        override def onScore(self : Card)(implicit g : Game) = $(AddCounterEffect(self, "agenda", 1))
        override def onSteal(self : Card)(implicit g : Game) = $(AddCounterEffect(self, "agenda", 1))
        def ability(self : Card)(implicit g : Game) = Ability("draw 4 cards and shuffle any number of cards from HQ into R&D", $(ClickCost(1), CounterCost("agenda", 1)), $(draw(Corp, 4), NbtShuffleAction(self)), click = true)
        override def abilities(self : Card)(implicit g : Game) = inCorpScore(self).$(ability(self))
        override def corpAbilities(self : Card)(implicit g : Game) = inRunnerScore(self).$(ability(self))
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case NbtShuffleAction(self) =>
                if (g.hand(Corp).any)
                    g.push(ChooseCardsEffect(Corp, g.hand(Corp), 0, g.hand(Corp).num, ShuffleIntoDeckPurpose(Corp), "Next Big Thing: choose any number of cards in HQ to shuffle into R&D"))
                NextAction
        }
    }

    object Idiosyncresis extends Startup("35061") {
        override def advanceable(self : Card)(implicit g : Game) = true
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) if g.isRezzed(self) => $(OptionalEffect(Corp, "Idiosyncresis: trash it (" + g.advancement(self) + " advancement counters: gain " + (3 * g.advancement(self)) + ", Runner loses " + (2 * g.advancement(self)) + ")?", $(IdioAction(self)), $))
            case _ => $
        }
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case IdioAction(self) =>
                val n = g.advancement(self)
                g.trashCard(self, Corp, false)
                g.push(LoseCreditsEffect(Runner, 2 * n), gain(Corp, 3 * n))
                NextAction
        }
    }

    object PublicAccessPlaza extends Startup("35062") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) if g.isRezzed(self) => $(gain(Corp, 1))
            case _ => $
        }
        override def onTrash(self : Card, by : Faction)(implicit g : Game) = (by.isRunner && g.threat(2)).$(MessageEffect(Corp, "uses Public Access Plaza (threat 2)"), tag(1))
    }

    object Doomscroll extends Startup("35063") {
        override def subroutines(self : Card)(implicit g : Game) = $(tagSub(1), netSub(1), Sub("Do 2 net damage if the Runner has at least 2 tags", DoomscrollAction(self)))
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case DoomscrollAction(self) =>
                if (g.tags >= 2)
                    g.push(net(2))
                NextAction
        }
    }

    object NPot extends Startup("35064") {
        override def subroutines(self : Card)(implicit g : Game) = $(etrSub, Sub("If the threat level is 2 or greater, end the run", NPotAction(self, 2)), Sub("If the threat level is 4 or greater, end the run", NPotAction(self, 4)))
        override def runnerAbilities(self : Card)(implicit g : Game) = $(Ability("break 1 subroutine", c(3), $(BioroidBreakEffect(self)), ok = encountering(self) && g.unbrokenSubs(self).any, reason = "not encountered"))
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case NPotAction(self, n) =>
                if (g.threat(n))
                    g.push(EndRunEffect)
                else
                    g.log("Threat level below", n.hl)
                NextAction
        }
    }

    object BiggerPicture extends Startup("35065") {
        override def canPlay(self : Card)(implicit g : Game) = g.tagged
        override def onPlay(self : Card)(implicit g : Game) = $(ChooseOneEffect(Corp, "Bigger Picture: choose one", $(
            Alt("Give the Runner 1 tag", $(tag(1))),
            Alt("Remove tags: the Runner loses 5 credits per tag removed, gain that many credits", $(ChooseNumberEffect(Corp, self, 1, g.tags, "bp", "Bigger Picture: remove how many tags?")), g.tagged))))
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case NumberPickedAction(self, n, "bp") =>
                val m = (5 * n).atMost(g.credits(Runner))
                g.push(RemoveTagsEffect(n), LoseCreditsEffect(Runner, m), gain(Corp, m))
                NextAction
        }
    }

    object IpEnforcement extends Startup("35066") {
        override def canPlay(self : Card)(implicit g : Game) = g.scored(Runner).exists(c => c.data.isAgenda && g.agendaValue(c) <= g.tags)
        override def onPlay(self : Card)(implicit g : Game) = $(ChooseNumberEffect(Corp, self, 0, g.tags, "ip", "IP Enforcement: remove how many tags (X)?"))
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case NumberPickedAction(self, x, "ip") =>
                val l = g.scored(Runner).%(c => c.data.isAgenda && g.agendaValue(c) == x)
                if (l.any)
                    g.push(RemoveTagsEffect(x), ChooseCardEffect(Corp, l, IpPurpose(self), "IP Enforcement: choose an agenda in the Runner's score area to install", false))
                else
                    g.log("No agenda worth", x.hl, "points in the Runner's score area")
                NextAction
        }
        override def chosenCard(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach { c =>
                g.log(Corp, "installs", c, "from the Runner's score area")
                g.push(CorpInstallEffect(c, None, false), CheckWinAction)
                if (g.tagged)
                    g.push(PlaceAdvancementEffect(c, 1))
            }
            NextAction
        }
    }

    object Touchups extends Startup("35067") {
        override def canPlay(self : Card)(implicit g : Game) = doubleCanPlay(Corp) && g.advanceableCards.any
        override def onPlay(self : Card)(implicit g : Game) = doubleCost(Corp) ++ $(ChooseCardEffect(Corp, g.advanceableCards, TouchupsPurpose(self), "Touch-ups: choose a card to place 2 advancement counters on", false))
        override def chosenCard(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach { c =>
                g.push(PlaceAdvancementEffect(c, 2), ChooseOneEffect(Corp, "Touch-ups: choose a card type", $("Event", "Hardware", "Program", "Resource")./(t => Alt(t, $(TouchupsTypeAction(p.card, t))))))
            }
            NextAction
        }
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case TouchupsTypeAction(self, kind) =>
                g.log(Runner, "reveals the grip:", g.hand(Runner)./(g.cardElem).comma)
                val l = g.hand(Runner).%(_.data.kind == kind)
                if (l.any)
                    g.push(ChooseCardsEffect(Corp, l, 0, 2, ToDeckPurpose(self, Runner, false, true), "Touch-ups: choose up to 2 revealed cards to shuffle into the stack"))
                NextAction
        }
    }

    object Bangun extends Startup("35068") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Installed(Corp, c) if c.data.isAgenda && g.serverOf(c).exists(_.isRemote) => $(OptionalEffect(Corp, "BANGUN: install the agenda faceup?", $(BangunFaceupAction(self, c)), $))
            case Accessed(c) if c.data.isAgenda && g.isInstalled(c) && g.counter(c, "faceup") > 0 => $(MessageEffect(Corp, "uses BANGUN"), meat(2), tag(1))
            case _ => $
        }
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case BangunFaceupAction(self, c) =>
                if (g.isInstalled(c)) {
                    g.state(c).seen = true
                    g.addCounter(c, "faceup", 1)
                    g.log(Corp, "turns", c, "faceup")
                }
                NextAction
        }
    }

    object ZwickyGroup extends Startup("35069") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case CreditsGained(Corp, n, Some(src)) if (src.data.isAgenda || src.data.isOperation) && !g.usedThisTurn(self, "zwicky") && g.deck(Corp).any =>
                $(MarkUsedEffect(self, "zwicky"), OptionalEffect(Corp, "The Zwicky Group: draw 1 card?", $(draw(Corp, 1)), $))
            case _ => $
        }
    }

    object Greenmail extends Startup("35070") {
        override def onScore(self : Card)(implicit g : Game) = $(gain(Corp, 2))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Forfeited(c) if c == self => $(gain(Corp, 4))
            case _ => $
        }
    }

    object OffTheBooks extends Startup("35071") {
        override def onScore(self : Card)(implicit g : Game) = dividends(self, 1)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnEnds(Corp) if inCorpScore(self) && g.counter(self, "agenda") > 0 && g.deck(Corp).any =>
                $(OptionalEffect(Corp, "Off the Books: remove 1 agenda counter to search R&D for a card?", $(AddCounterEffect(self, "agenda", -1), ChooseCardEffect(Corp, g.deck(Corp).sortBy(_.title), OtbPurpose(self), "Off the Books: choose a card", true), ShuffleDeckEffect(Corp)), $))
            case _ => $
        }
        override def chosenCard(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach { c =>
                g.log(Corp, "reveals", c, "from R&D")
                g.push(ChooseOneEffect(Corp, "Off the Books: install the card ignoring all costs, or add it to HQ?", $(
                    Alt("Install it, ignoring all costs", $(ChooseServerEffect(Corp, c, true, (c.data.isIce || c.data.isUpgrade).?(g.servers).|(g.remotes), true)), corpInstallable(c)),
                    Alt("Add it to HQ", $(AddToHandEffect(c))))))
            }
            NextAction
        }
    }

    object AnthillExcavationContract extends Startup("35072") {
        override def initialCounters(self : Card)(implicit g : Game) = Map("credit" -> 8)
        override def triggers(self : Card, e : Event)(implicit g : Game) = trashOnEmpty(self, e, "credit") ++ (e match {
            case TurnBegins(Corp) if g.isRezzed(self) => $(TakeCreditsEffect(Corp, self, 4), draw(Corp, 1))
            case _ => $
        })
    }

    object Plutus extends Startup("35073") {
        override def onRez(self : Card)(implicit g : Game) = $(PlutusRezAction(self))
        // the additional rez cost must be payable (otherwise the rez is offered, fails and is offered again)
        override def canRez(self : Card)(implicit g : Game) = g.scored(Corp).exists(_.data.isAgenda) || g.hand(Corp).num >= 3
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) if g.isRezzed(self) =>
                val l = g.discard(Corp).%(c => c.data.isOperation && g.has(c, "Transaction") && g.credits(Corp) >= g.playCost(c) && g.impl(c).canPlay(c))
                l.any.$(OptionalEffect(Corp, "Plutus: play a transaction from Archives (removed from the game afterwards)?", $(ChooseCardEffect(Corp, l, PlayPurpose(self, true, true), "Plutus: choose a transaction in Archives", true)), $))
            case _ => $
        }
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case PlutusRezAction(self) =>
                val agendas = g.scored(Corp).%(_.data.isAgenda)
                val h = g.hand(Corp)
                if (agendas.none && h.num < 3)
                    g.push(PlutusDerezAction(self))
                else
                    g.push(ChooseOneEffect(Corp, "Plutus: additional rez cost", $(
                        Alt("Forfeit 1 agenda", $(ChooseCardEffect(Corp, agendas, ForfeitPurpose, "Plutus: choose an agenda to forfeit", false)), agendas.any),
                        Alt("Reveal and trash 3 cards from HQ", $(ChooseCardsEffect(Corp, h, 3, 3, TrashFromHQPurpose(self, true), "Plutus: choose 3 cards in HQ to reveal and trash")), h.num >= 3))))
                NextAction
            case PlutusDerezAction(self) =>
                g.log(Corp, "cannot pay the additional cost of", self)
                g.push(DerezEffect(self))
                NextAction
        }
    }

    object Biawak extends Startup("35074") {
        override def rezCostMod(self : Card, card : Card)(implicit g : Game) = (card == self && g.scored(Corp).exists(_.data.isAgenda)).?(-10).|(0)
        override def onRez(self : Card)(implicit g : Game) = {
            val agendas = g.scored(Corp).%(_.data.isAgenda)
            agendas.any.$(ChooseCardEffect(Corp, agendas, ForfeitPurpose, "Biawak: choose an agenda to forfeit (paid 10 credits of the rez cost)", false))
        }
        override def subroutines(self : Card)(implicit g : Game) = {
            val programs = g.installedCards(Runner).%(_.data.isProgram)
            val resources = g.installedCards(Runner).%(_.data.isResource)
            $(
                Sub("Trash 1 installed program or end the run", ChooseOneEffect(Corp, "Biawak: choose one", $(
                    Alt("Trash a program", $(ChooseCardEffect(Corp, programs, TrashPurpose(Corp), "Biawak: choose a program to trash", false)), programs.any),
                    Alt("End the run", $(EndRunEffect))))),
                Sub("Trash 1 installed resource or end the run", ChooseOneEffect(Corp, "Biawak: choose one", $(
                    Alt("Trash a resource", $(ChooseCardEffect(Corp, resources, TrashPurpose(Corp), "Biawak: choose a resource to trash", false)), resources.any),
                    Alt("End the run", $(EndRunEffect))))),
                etrSub)
        }
    }

    object Kessleroid extends Startup("35075") {
        override def untrashableByRunner(self : Card)(implicit g : Game) = g.isRezzed(self)
        override def subroutines(self : Card)(implicit g : Game) = $(etrSub, etrSub)
    }

    object Syailendra extends Startup("35076") {
        override def advanceable(self : Card)(implicit g : Game) = true
        def place(implicit g : Game) = ChooseCardEffect(Corp, g.advanceableCards, AdvancePurpose(1), "Syailendra: place 1 advancement counter on a card?", true)
        override def onEncounter(self : Card)(implicit g : Game) = (g.advancement(self) >= 3 && g.advanceableCards.any).$(place)
        override def subroutines(self : Card)(implicit g : Game) = $(Sub("You may place 1 advancement counter on an installed card you can advance", place), loseSub(2), netSub(1))
    }

    object KeyPerformanceIndicators extends Startup("35077") {
        override def onPlay(self : Card)(implicit g : Game) = $(KpiAction(self, $))
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case KpiAction(self, done) =>
                if (done.num < 2) {
                    val next = (k : String) => KpiAction(self, done :+ k)
                    g.push(ChooseOneEffect(Corp, "Key Performance Indicators: choose an option (" + (2 - done.num) + " remaining)", $(
                        Alt("Draw 1 card and shuffle 1 card from HQ into R&D", $(draw(Corp, 1), KpiShuffleAction(self), next("draw")), !done.has("draw") && g.deck(Corp).any),
                        Alt("Install 1 piece of ice from HQ, ignoring all costs", $(ChooseCardEffect(Corp, g.hand(Corp).%(_.data.isIce), InstallChooseServerPurpose(true), "Key Performance Indicators: choose a piece of ice", false), next("ice")), !done.has("ice") && g.hand(Corp).exists(_.data.isIce)),
                        Alt("Place 1 advancement counter on an installed card you can advance", $(ChooseCardEffect(Corp, g.advanceableCards, AdvancePurpose(1), "Key Performance Indicators: choose a card", false), next("adv")), !done.has("adv") && g.advanceableCards.any),
                        Alt("Gain 2 credits", $(gain(Corp, 2), next("gain")), !done.has("gain")),
                        Alt("Done", $))))
                }
                NextAction
            case KpiShuffleAction(self) =>
                if (g.hand(Corp).any)
                    g.push(ChooseCardEffect(Corp, g.hand(Corp), ShuffleIntoDeckPurpose(Corp), "Key Performance Indicators: choose a card in HQ to shuffle into R&D", false))
                NextAction
        }
    }

    object MeasuredResponse extends Startup("35078") {
        override def canPlay(self : Card)(implicit g : Game) = g.threat(4) && g.successfulRunsLastTurn.any
        override def onPlay(self : Card)(implicit g : Game) = $(ChooseOneEffect(Runner, "Measured Response: pay 8 credits or suffer 4 meat damage", $(
            Alt("Pay 8 credits", $(LoseCreditsEffect(Runner, 8)), g.credits(Runner) >= 8),
            Alt("Suffer 4 meat damage", $(meat(4))))))
    }

    object Flyswatter extends Startup("35079") {
        override def onRez(self : Card)(implicit g : Game) = runOnThisServer(self).$(PurgeEffect)
        override def subroutines(self : Card)(implicit g : Game) = $(etrSub)
    }

    object Lamplighter extends Startup("35080") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case AgendaScored(_) if g.lastScoredFrom.exists(s => thisServer(self, s)) => $(MessageEffect(Corp, "trashes Lamplighter"), TrashEffect(self, Corp, false))
            case AgendaStolen(_) if g.lastStolenFrom.exists(s => thisServer(self, s)) => $(MessageEffect(Corp, "trashes Lamplighter"), TrashEffect(self, Corp, false))
            case _ => $
        }
        override def subroutines(self : Card)(implicit g : Game) = $(
            Sub("Give the Runner 1 tag unless they pay 3 credits", ChooseOneEffect(Runner, "Lamplighter", $(Alt("Take 1 tag", $(tag(1))), Alt("Pay 3 credits", $(LoseCreditsEffect(Runner, 3)), g.credits(Runner) >= 3)))),
            Sub("End the run if the Runner is tagged", LamplighterAction(self)))
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case LamplighterAction(self) =>
                if (g.tagged)
                    g.push(EndRunEffect)
                NextAction
        }
    }

    object PettyCash extends Startup("35081") {
        override def canPlay(self : Card)(implicit g : Game) = g.clicks(Corp) >= 3 && g.reg("played") == 0 && g.reg("installed") == 0 && g.reg("advanced") == 0 && g.reg("click-draw") == 0
        override def onPlay(self : Card)(implicit g : Game) = $(gain(Corp, 5)) ++ g.playedFrom.get(self).exists(z => !z.is[HandZone]).$(GainClicksEffect(Corp, 1))
        override def inactiveAbilities(self : Card)(implicit g : Game) =
            (g.zone(self) == DiscardZone(Corp)).$(Ability("play Petty Cash from Archives (then remove it from the game)", click(), $(PlayCardEffect(Corp, self, true, true)), click = true, ok = canPlay(self) && g.credits(Corp) >= g.playCost(self), reason = "an action was already taken this turn"))
    }

    object MahkotaLangitGrid extends Startup("35082") {
        def covers(self : Card, c : Card)(implicit g : Game) = c != self && g.serverOf(self).exists(s => (c.data.isAsset && g.root(s).has(c)) || (c.data.isIce && g.ice(s).has(c)))
        override def initialCounters(self : Card)(implicit g : Game) = Map("credit" -> 2)
        override def rezCostMod(self : Card, card : Card)(implicit g : Game) = covers(self, card).?(-g.counter(self, "credit")).|(0)
        override def trashCostMod(self : Card, card : Card)(implicit g : Game) = (card != self && card.data.isAsset && g.serverOf(self).exists(s => g.root(s).has(card))).?(2).|(0)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) => $(RefillCreditsEffect(self, 2))
            case Rezzed(c) if covers(self, c) && g.counter(self, "credit") > 0 && c.data.cost.|(0) > 0 => $(MahkotaPayAction(self, c))
            case _ => $
        }
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case MahkotaPayAction(self, c) =>
                val m = g.counter(self, "credit").atMost(c.data.cost.|(0))
                if (m > 0) {
                    g.log(Corp, "spends", m.credits, "from", self)
                    g.push(AddCounterEffect(self, "credit", -m))
                }
                NextAction
        }
    }

    val all : $[CardImpl] = $(
        Ryo, Topan, CharmOffensive, Scrounge, Shred, Bling, Gourmand, Hantu, RisingTide, Cacophony, RentRioters, BazWong, Muslihat,
        CleanGetaway, LieLow, MaintenanceAccess, TransferOfWealth, Detente, Maglectric, SangKancil, FransofiaWard, OpenMarket, Dewi,
        Magdalene, Illumination, Ritual, Gamedragon, Madani, Azimat, Chromatophores, DevadattaDrone, Principia, Knickknack, SideHustle,
        LeoConstruction, Poetri, AggressiveTrendsetting, ProjectIngatan, HumanoidResources, OttoCampaign, Bumi, ScatterField, Nanomanagement,
        TopDownSolutions, Mercia, AuCo, PtUntaian, Proprionegation, SericultureExpansion, Byte, Phat, Empiricist, Mycoweb, SemakSamun,
        PeerReview, MitraAman, Nebula, SynapseGlobal, EmbeddedReporting, NextBigThing, Idiosyncresis, PublicAccessPlaza, Doomscroll, NPot,
        BiggerPicture, IpEnforcement, Touchups, Bangun, ZwickyGroup, Greenmail, OffTheBooks, AnthillExcavationContract, Plutus, Biawak,
        Kessleroid, Syailendra, KeyPerformanceIndicators, MeasuredResponse, Flyswatter, Lamplighter, PettyCash, MahkotaLangitGrid,
    )
}

case class PeerReviewInstallAction(card : Card) extends CardAction
case class EmpiricistAction(card : Card, step : Int) extends CardAction
