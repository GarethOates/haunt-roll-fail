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
// Uprising (ur) card implementations, ported from jinteki.net src/clj/game/cards/*.clj
// ---------------------------------------------------------------------------------------------------------------------

case class HoshikoFlipAction(card : Card) extends CardAction
case class GachaponAction(card : Card) extends CardAction
case class GachaponPurpose(card : Card, aside : $[Card]) extends CardPurpose
case class GachaponRestAction(card : Card, aside : $[Card]) extends CardAction
case class GachaponKeepPurpose(card : Card, remaining : $[Card]) extends CardPurpose
case class BoomerangPurpose(card : Card) extends CardPurpose
case class BoomerangReturnAction(card : Card) extends CardAction
case class QLoopAction(card : Card) extends CardAction
case class CordycepsPurpose(card : Card) extends CardPurpose
case class CordycepsSwapPurpose(card : Card, first : Card) extends CardPurpose
case class PaulePurpose(card : Card) extends CardPurpose
case class BufferDriveAction(card : Card) extends CardAction
case class VacheronAction(card : Card) extends CardAction
case class EngramSubAction(card : Card) extends CardAction
case class KonjinAction(card : Card) extends CardAction
case class KonjinPurpose(card : Card) extends CardPurpose
case class KakurenboAction(card : Card) extends CardAction
case class KakurenboPurpose(card : Card) extends CardPurpose
case class GankedAction(card : Card) extends CardAction
case class GankedPurpose(card : Card) extends CardPurpose
case class EarthStationFlipAction(card : Card, flipped : Boolean) extends CardAction
case class WallToWallAction(card : Card, n : Int, done : $[String]) extends CardAction
case class ColossusTrashAction(card : Card) extends CardAction


abstract class Lockdown(code : String) extends NC(code) {
    override def canPlay(self : Card)(implicit g : Game) = !g.lockdownActive
    override def staysInPlay(self : Card)(implicit g : Game) = true
    def lockdownTriggers(self : Card, e : Event)(implicit g : Game) : $[ForcedAction] = e match {
        case TurnBegins(Corp) if g.zone(self) == PlayZone(Corp) => $(TrashEffect(self, Corp, false))
        case _ => $
    }
}


object URCards {
    // ------------------------------------------------------------------------------------------------------------
    // Runner
    // ------------------------------------------------------------------------------------------------------------

    object Hoshiko extends NC("26066") {
        def flipped(self : Card)(implicit g : Game) = g.counter(self, "flipped") > 0
        override def link(self : Card)(implicit g : Game) = flipped(self).?(1).|(0)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnEnds(Runner) if flipped(self) && g.reg("accessed") == 0 => $(HoshikoFlipAction(self))
            case TurnEnds(Runner) if !flipped(self) && g.reg("accessed") > 0 => $(gain(Runner, 2), HoshikoFlipAction(self))
            case TurnBegins(Runner) if flipped(self) => $(draw(Runner, 1), LoseCreditsEffect(Runner, 1))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case HoshikoFlipAction(self) =>
                if (flipped(self)) {
                    g.state(self).counters += "flipped" -> 0
                    g.state(self).extraSubtypes = $
                    g.log(Runner, "flips the identity to", "Hoshiko Shiro: Untold Protagonist".hl)
                }
                else {
                    g.state(self).counters += "flipped" -> 1
                    g.state(self).extraSubtypes = $("Digital")
                    g.log(Runner, "flips the identity to", "Hoshiko Shiro: Mahou Shoujo".hl)
                }
                NextAction
        }
    }

    object Moshing extends NC("26067") {
        override def canPlay(self : Card)(implicit g : Game) = g.hand(Runner).but(self).num >= 3
        override def onPlay(self : Card)(implicit g : Game) = $(ChooseCardsEffect(Runner, g.hand(Runner), 3, 3, TrashFromHandPurpose(Runner), "Moshing: choose 3 cards in the grip to trash"), gain(Runner, 3), draw(Runner, 3))
    }

    object DevilCharm extends NC("26068") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case EncounterIce(ice) => $(OptionalEffect(Runner, "Devil Charm: remove it from the game to give the ice -6 strength for the run?", $(RemoveFromGameEffect(self), PumpIceEffect(ice, -6, RunDuration)), $))
            case _ => $
        }
    }

    object Gachapon extends NC("26069") {
        override def abilities(self : Card)(implicit g : Game) = $(Ability("set aside the top 6 cards of the stack and install 1 program or virtual resource paying 2 credits less", $(TrashSelfCost), $(GachaponAction(self)), ok = g.deck(Runner).any, reason = "empty stack"))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case GachaponAction(self) =>
                val aside = g.deck(Runner).take(6)
                g.log(Runner, "sets aside", aside./(g.cardElem).comma)
                val l = aside.%(c => (c.data.isProgram || (c.data.isResource && g.has(c, "Virtual"))) && g.canAffordInstall(c, 2))
                g.push(GachaponRestAction(self, aside))
                if (l.any)
                    g.push(ChooseCardEffect(Runner, l, GachaponPurpose(self, aside), "Gachapon: install a card paying 2 credits less?", true))
                NextAction
            case GachaponRestAction(self, aside) =>
                val remaining = aside.%(c => g.zone(c) == DeckZone(Runner))
                val n = 3.atMost(remaining.num)
                if (remaining.num > n)
                    g.push(ChooseCardsEffect(Runner, remaining, n, n, GachaponKeepPurpose(self, remaining), "Gachapon: choose " + n + " cards to shuffle back (the rest are removed from the game)"))
                else
                    g.push(ShuffleDeckEffect(Runner))
                NextAction
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case GachaponPurpose(self, aside) =>
                cards.single.foreach(c => g.pushAll(g.installRunner(c, 2)))
                NextAction
            case GachaponKeepPurpose(self, remaining) =>
                remaining.%!(cards.has).foreach(c => g.moveTo(c, RemovedZone(Runner)))
                g.log(remaining.%!(cards.has).num.cards, "removed from the game")
                g.push(ShuffleDeckEffect(Runner))
                NextAction
        }
    }

    object Keiko extends NC("26070") {
        override def memory(self : Card)(implicit g : Game) = 2
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Installed(Runner, c) if g.has(c, "Companion") && !g.usedThisTurn(self, "keiko") => $(MarkUsedEffect(self, "keiko"), gain(Runner, 1))
            case CreditsSpentFrom(c, _) if g.has(c, "Companion") && g.owner(c).isRunner && !g.usedThisTurn(self, "keiko") => $(MarkUsedEffect(self, "keiko"), gain(Runner, 1))
            case _ => $
        }
    }

    object Odore extends NC("26071") {
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(2), 0, $("Sentry"))) ++ (virtualResources >= 3).$(BreakAbility(c(0), 1, $("Sentry"), |("break 1 sentry subroutine (3 virtual resources)")))
        override def pumps(self : Card)(implicit g : Game) = $(Pump(c(3), 3))
    }

    object PaladinPoemu extends NC("26073") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) | AgendaStolen(_) => $(AddCounterEffect(self, "credit", 1))
            case TurnEnds(Runner) if g.counter(self, "credit") >= 3 && g.runnerInstalled.any => $(ChooseCardEffect(Runner, g.runnerInstalled, TrashPurpose(Runner), "Paladin Poemu: choose an installed card to trash", false))
            case _ => $
        }
        override def abilities(self : Card)(implicit g : Game) = $(Ability("take 1 credit (to install a non-connection card)", NoCost, $(TakeCreditsEffect(Runner, self, 1)), ok = g.counter(self, "credit") > 0, reason = "no hosted credits"))
    }

    object Bravado extends NC("26074") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def canPlay(self : Card)(implicit g : Game) = g.servers.exists(s => g.ice(s).any)
        override def onPlay(self : Card)(implicit g : Game) = $(RunAnyServerEffect(|(self), g.servers.%(s => g.ice(s).any)))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case PassIce(_) if thisCardRun(self) => $(AddCounterEffect(self, "power", 1))
            case RunEnds(_, _, _) if thisCardRun(self) => $(gain(Runner, 6 + g.counter(self, "power")))
            case _ => $
        }
    }

    object Boomerang extends NC("26075") {
        override def onInstall(self : Card)(implicit g : Game) = g.allIce.any.$(ChooseCardEffect(Runner, g.allIce, BoomerangPurpose(self), "Boomerang: choose a piece of ice", false))
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach { c =>
                g.state(p.card).target = |(c)
                g.log(p.card, "targets", g.isRezzed(c).?(g.cardElem(c)).|("a piece of ice".hl))
            }
            NextAction
        }
        override def breaks(self : Card)(implicit g : Game) =
            encountering(self).exists(i => g.state(self).target.has(i)).$(BreakAbility($(TrashSelfCost), 2, $(), |("break up to 2 subroutines (trash Boomerang)"), $(AtRunEndEffect($(BoomerangReturnAction(self))))))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case BoomerangReturnAction(self) =>
                val l = g.discard(Runner).%(_.code == self.code)
                if (g.run.exists(_.successful) && l.any)
                    g.push(OptionalEffect(Runner, "Boomerang: shuffle a copy from the heap into the stack?", $(MoveEffect(l.head, DeckZone(Runner), false), ShuffleDeckEffect(Runner)), $))
                NextAction
        }
    }

    object MuSafecracker extends NC("26076") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(HQ) if stealthActive && g.credits(Runner) >= 1 => $(OptionalEffect(Runner, "Mu Safecracker: pay 1 credit to access 1 additional card from HQ?", $(LoseCreditsEffect(Runner, 1), AccessBonusEffect(HQ, 1)), $))
            case SuccessfulRun(RnD) if stealthActive && g.credits(Runner) >= 2 => $(OptionalEffect(Runner, "Mu Safecracker: pay 2 credits to access 1 additional card from R&D?", $(LoseCreditsEffect(Runner, 2), AccessBonusEffect(RnD, 1)), $))
            case _ => $
        }
    }

    object PrognosticQLoop extends NC("26077") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case RunInitiated(_) if g.reg("runs") == 1 && g.deck(Runner).any => $(OptionalEffect(Runner, "Prognostic Q-Loop: look at the top 2 cards of the stack?", $(LookAtEffect(Runner, g.deck(Runner).take(2), "Prognostic Q-Loop: the top cards of the stack")), $))
            case _ => $
        }
        override def abilities(self : Card)(implicit g : Game) = $(Ability("reveal the top card of the stack and install it if it is a program or hardware", c(1), $(QLoopAction(self)), once = once("qloop"), ok = g.deck(Runner).any, reason = "empty stack"))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case QLoopAction(self) =>
                g.deck(Runner).starting.foreach { c =>
                    if ((c.data.isProgram || c.data.isHardware) && g.canAffordInstall(c, 0))
                        g.push(OptionalEffect(Runner, "Prognostic Q-Loop: install the revealed card?", g.installRunner(c, 0), $))
                    g.push(RevealCardsEffect(Runner, $(c)))
                }
                NextAction
        }
    }

    object Afterimage extends NC("26079") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case EncounterIce(ice) if g.has(ice, "Sentry") && !g.usedThisTurn(self, "afterimage") && g.credits(Runner) >= 2 =>
                $(OptionalEffect(Runner, "Afterimage: pay 2 credits to bypass this sentry?", $(MarkUsedEffect(self, "afterimage"), LoseCreditsEffect(Runner, 2), BypassEffect), $))
            case _ => $
        }
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(1), 2, $("Sentry")))
        override def pumps(self : Card)(implicit g : Game) = $(Pump(c(1), 2))
    }

    object Makler extends NC("26080") {
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(2), 2, $("Barrier")))
        override def pumps(self : Card)(implicit g : Game) = $(Pump(c(2), 2))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SubsBroken(ice, _, b) if b == self && g.unbrokenSubs(ice).none && !g.usedThisTurn(self, "makler") => $(MarkUsedEffect(self, "makler"), gain(Runner, 1))
            case _ => $
        }
    }

    object PenumbralToolkit extends NC("26081") {
        override def installCostMod(self : Card, card : Card)(implicit g : Game) = (card == self && g.successfulRunsThisTurn.has(HQ)).?(-2).|(0)
        override def initialCounters(self : Card)(implicit g : Game) = Map("credit" -> 4)
        override def creditsUsableForRun(self : Card)(implicit g : Game) = true
        override def triggers(self : Card, e : Event)(implicit g : Game) = trashOnEmpty(self, e, "credit")
    }

    object TheBack extends NC("26082") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case AbilityUsed(Runner, c, _) if c.data.isHardware && g.run.exists(!_.ended) && !g.usedThisTurn(self, "back") => $(MarkUsedEffect(self, "back"), AddCounterEffect(self, "power", 1))
            case _ => $
        }
        override def abilities(self : Card)(implicit g : Game) = {
            val l = g.discard(Runner).%(c => c.data.text.contains("[trash]"))
            val n = 2 * g.counter(self, "power")
            $(Ability("shuffle up to " + n + " cards with trash abilities from the heap into the stack", $(ClickCost(1), RemoveSelfCost), $(ChooseCardsEffect(Runner, l, 0, n, ShuffleIntoDeckPurpose(Runner), "The Back: choose cards to shuffle into the stack")), click = true, ok = n > 0 && l.any, reason = "needs power counters and cards with trash abilities in the heap"))
        }
    }

    object HarmonyARTherapy extends NC("26083") {
        override def onPlay(self : Card)(implicit g : Game) = {
            val l = g.discard(Runner).groupBy(_.title).toList./((t, cs) => cs.head).sortBy(_.title)
            l.any.$(ChooseCardsEffect(Runner, l, 0, 5, ShuffleIntoDeckPurpose(Runner), "Harmony AR Therapy: choose up to 5 cards with different names to shuffle into the stack")) :+ RemoveFromGameEffect(self)
        }
    }

    object Aniccam extends NC("26084") {
        override def memory(self : Card)(implicit g : Game) = 1
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Trashed(c, _, _) if c.data.isEvent && !g.usedThisTurn(self, "aniccam") && g.deck(Runner).any => $(MarkUsedEffect(self, "aniccam"), draw(Runner, 1))
            case DamageDone(_, _) if g.lastDamaged.exists(_.data.isEvent) && !g.usedThisTurn(self, "aniccam") && g.deck(Runner).any => $(MarkUsedEffect(self, "aniccam"), draw(Runner, 1))
            case _ => $
        }
    }

    object Simulchip extends NC("26085") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Trashed(c, _, _) if c.data.isProgram && g.owner(c).isRunner => $(RegisterEffect("program-trashed", 1))
            case _ => $
        }
        override def abilities(self : Card)(implicit g : Game) = {
            val l = g.discard(Runner).%(c => c.data.isProgram && g.canAffordInstall(c, 3))
            val extra = (g.reg("program-trashed") == 0).$[Cost](TrashInstalledCost("Program"))
            $(Ability("install a program from the heap paying 3 credits less", TrashSelfCost +: extra, $(ChooseCardEffect(Runner, l, InstallPayingPurpose(3, None, false), "Simulchip: choose a program to install", false)), ok = l.any, reason = "no program in the heap"))
        }
    }

    object Cordyceps extends NC("26086") {
        override def initialCounters(self : Card)(implicit g : Game) = Map("virus" -> 2)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(s) if s.isCentral && g.counter(self, "virus") > 0 && !g.usedThisTurn(self, "cordyceps") && g.ice(s).any && g.allIce.num >= 2 =>
                $(OptionalEffect(Runner, "Cordyceps: remove 1 virus counter to swap a piece of ice protecting this server with another?", $(ChooseCardEffect(Runner, g.ice(s), CordycepsPurpose(self), "Cordyceps: choose a piece of ice protecting this server", false)), $))
            case _ => $
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case CordycepsPurpose(self) =>
                cards.single.foreach(a => g.push(ChooseCardEffect(Runner, g.allIce.but(a), CordycepsSwapPurpose(self, a), "Cordyceps: choose a piece of ice to swap with", false)))
                NextAction
            case CordycepsSwapPurpose(self, a) =>
                cards.single.foreach(b => g.push(MarkUsedEffect(self, "cordyceps"), AddCounterEffect(self, "virus", -1), SwapIceEffect(a, b)))
                NextAction
        }
    }

    object Euler extends NC("26087") {
        override def breaks(self : Card)(implicit g : Game) = installedThisTurn(self).$(BreakAbility(c(0), 1, $("Code Gate"), |("break 1 code gate subroutine (installed this turn)"))) :+ BreakAbility(c(2), 2, $("Code Gate"))
        override def pumps(self : Card)(implicit g : Game) = $(Pump(c(1), 1))
    }

    object Mantle extends NC("26088") {
        override def initialCounters(self : Card)(implicit g : Game) = Map("credit" -> 1)
        override def creditsUsableForRun(self : Card)(implicit g : Game) = true
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) if g.counter(self, "credit") < 1 => $(AddCounterEffect(self, "credit", 1 - g.counter(self, "credit")))
            case _ => $
        }
    }

    object Penrose extends NC("26089") {
        override def breaks(self : Card)(implicit g : Game) = installedThisTurn(self).$(BreakAbility(c(1), 1, $("Barrier"), |("break 1 barrier subroutine (installed this turn)"))) :+ BreakAbility(c(1), 1, $("Code Gate"))
        override def pumps(self : Card)(implicit g : Game) = $(Pump(c(1), 3))
    }

    object SelfModifyingCode extends NC("26090") {
        override def abilities(self : Card)(implicit g : Game) = {
            val l = g.deck(Runner).%(c => c.data.isProgram && g.credits(Runner) - 2 >= g.installCost(c, None)).sortBy(_.title)
            $(Ability("search the stack for a program and install it", $(CreditCost(2), TrashSelfCost), $(ChooseCardEffect(Runner, l, InstallPayingPurpose(0, None, false), "Self-modifying Code: choose a program to install", true), ShuffleDeckEffect(Runner)), ok = g.deck(Runner).any, reason = "empty stack"))
        }
    }

    object PaulesCafe extends NC("26092") {
        override def abilities(self : Card)(implicit g : Game) = {
            val hand = g.hand(Runner).%(c => c.data.isProgram || c.data.isHardware)
            val hosted = g.hosted(self).%(c => g.owner(c).isRunner)
            val discount = (g.active.isRunner && !g.usedThisTurn(self, "paule")).?(g.activeCards(Runner).%(c => c.data.isResource && g.has(c, "Connection") && c.data.unique).num).|(0)
            $(
                Ability("host a program or piece of hardware from the grip", click(), $(ChooseCardEffect(Runner, hand, HostOnPurpose(self, true), "Paule's Cafe: choose a card to host", false)), click = true, ok = hand.any, reason = "nothing to host"),
                Ability("install a hosted card" + (discount > 0).??(" paying " + discount + " credits less"), c(1), $(ChooseCardEffect(Runner, hosted, PaulePurpose(self), "Paule's Cafe: choose a hosted card to install", false)), ok = hosted.exists(c => g.canAffordInstall(c, discount - 1)), reason = "no affordable hosted card"))
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach { c =>
                val discount = (g.active.isRunner && !g.usedThisTurn(p.card, "paule")).?(g.activeCards(Runner).%(x => x.data.isResource && g.has(x, "Connection") && x.data.unique).num).|(0)
                g.moveTo(c, HandZone(Runner))
                g.markUsed(p.card, "paule")
                g.pushAll(g.installRunner(c, discount))
            }
            NextAction
        }
    }

    object BufferDrive extends NC("26093") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case DamageDone(_, _) if g.lastDamaged.any && !g.usedThisTurn(self, "buffer") => $(MarkUsedEffect(self, "buffer"), BufferDriveAction(self))
            case Trashed(c, _, _) if g.owner(c).isRunner && g.zone(c) == DiscardZone(Runner) && !g.usedThisTurn(self, "buffer") && g.reg("trashed-from-hand-Runner") > 0 => $(MarkUsedEffect(self, "buffer"), ChooseCardEffect(Runner, $(c), BottomOfDeckPurpose(Runner), "Buffer Drive: add the trashed card to the bottom of the stack?", true))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case BufferDriveAction(self) =>
                val l = g.lastDamaged.%(c => g.zone(c) == DiscardZone(Runner))
                if (l.any)
                    g.push(ChooseCardEffect(Runner, l, BottomOfDeckPurpose(Runner), "Buffer Drive: add a trashed card to the bottom of the stack?", true))
                NextAction
        }
        override def abilities(self : Card)(implicit g : Game) = $(Ability("add a card from the heap to the top of the stack", $(RemoveSelfCost), $(ChooseCardEffect(Runner, g.discard(Runner), TopOfDeckPurpose(Runner), "Buffer Drive: choose a card", false)), ok = g.discard(Runner).any, reason = "empty heap"))
    }

    object DailyCasts extends NC("26094") {
        override def initialCounters(self : Card)(implicit g : Game) = Map("credit" -> 8)
        override def triggers(self : Card, e : Event)(implicit g : Game) = trashOnEmpty(self, e, "credit") ++ (e match {
            case TurnBegins(Runner) => $(TakeCreditsEffect(Runner, self, 2))
            case _ => $
        })
    }

    object DreamNet extends NC("26095") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(_) if g.reg("successful-run") == 1 =>
                $(draw(Runner, 1)) ++ (g.link >= 2 || g.identity.get(Runner).exists(c => g.has(c, "Digital"))).$(gain(Runner, 1))
            case _ => $
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Corp
    // ------------------------------------------------------------------------------------------------------------

    object ProjectVacheron extends NC("26097") {
        override def agendaPointsBonus(self : Card)(implicit g : Game) = (g.zone(self) == ScoredZone(Runner) && g.counter(self, "agenda") > 0).?(-3).|(0)
        override def onSteal(self : Card)(implicit g : Game) = (!g.run.exists(_.breachServer.has(Archives))).$(AddCounterEffect(self, "agenda", 4))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) if g.zone(self) == ScoredZone(Runner) && g.counter(self, "agenda") > 0 => $(AddCounterEffect(self, "agenda", -1), VacheronAction(self))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case VacheronAction(self) =>
                if (g.counter(self, "agenda") == 0) {
                    g.log(self, "is now worth", 3.hl, "agenda points")
                    g.push(CheckWinAction)
                }
                NextAction
        }
    }

    object Bass extends NC("26098") {
        override def abilities(self : Card)(implicit g : Game) = $(Ability("gain 2 clicks", $(ClickCost(1), TrashSelfCost), $(GainClicksEffect(Corp, 2)), click = true, ok = g.isRezzed(self), reason = "not rezzed"))
    }

    object CerebralOverwriter extends NC("26099") {
        override def advanceable(self : Card)(implicit g : Game) = true
        override def onAccess(self : Card)(implicit g : Game) =
            (g.isInstalled(self) && g.advancement(self) > 0 && g.credits(Corp) >= 3).$(OptionalEffect(Corp, "Cerebral Overwriter: pay 3 credits to do " + g.advancement(self) + " core damage?", $(LoseCreditsEffect(Corp, 3), core(g.advancement(self))), $))
    }

    object VaporframeFabricator extends NC("26100") {
        def install(implicit g : Game) = {
            val l = g.hand(Corp).%(corpInstallable)
            l.any.$(ChooseCardEffect(Corp, l, InstallPayingPurpose(99, None, false), "Vaporframe Fabricator: install a card from HQ ignoring all costs?", true))
        }
        override def abilities(self : Card)(implicit g : Game) = $(Ability("install 1 card from HQ ignoring all costs", click(), install, click = true, once = once("vaporframe"), ok = g.isRezzed(self) && g.hand(Corp).%(corpInstallable).any, reason = "nothing to install"))
        // "you cannot install that card in the root of this server"
        override def onTrash(self : Card, by : Faction)(implicit g : Game) = by.isRunner.??(g.zoneServer(g.lastTrashedFrom) match {
            case Some(s) =>
                val l = g.hand(Corp).%(corpInstallable)
                l.any.$(ChooseCardEffect(Corp, l, NoRootInstallPurpose(self, s), "Vaporframe Fabricator: install a card from HQ ignoring all costs (not in the root of " + s.name + ")?", true))
            case None => install
        })
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case NoRootInstallPurpose(_, s) =>
                cards.single.foreach { c =>
                    val l = (c.data.isIce || c.data.isUpgrade).?(g.servers).|(g.remotes : $[Server]).%(x => c.data.isIce || x != s)
                    g.push(ChooseServerPayingEffect(c, 99, l, true, false))
                }
                NextAction
        }
    }

    object Drafter extends NC("26101") {
        override def subroutines(self : Card)(implicit g : Game) = $(
            Sub("You may add 1 card from Archives to HQ", ChooseCardEffect(Corp, g.discard(Corp), AddToHandPurpose(Corp, false), "Drafter: add a card from Archives to HQ?", true)),
            Sub("You may install 1 card from Archives or HQ, ignoring all costs", ChooseCardEffect(Corp, (g.hand(Corp) ++ g.discard(Corp)).%(corpInstallable), InstallPayingPurpose(99, None, false), "Drafter: install a card ignoring all costs?", true)))
    }

    object Tyr extends NC("26102") {
        override def subroutines(self : Card)(implicit g : Game) = $(Sub("Do 2 core damage", core(2)), Sub("Trash 1 installed Runner card. Gain 3[c]", CopyAbilityEffect($(RunnerTrashInstalledEffect("", Corp, "Tyr: choose an installed Runner card to trash"), gain(Corp, 3)))), etrSub)
        override def runnerAbilities(self : Card)(implicit g : Game) = $(Ability("break 1 subroutine (the Corp gets +1 allotted click next turn)", $(LoseClickCost(1)), $(BioroidBreakEffect(self), ExtraClicksEffect(Corp, 1)), ok = encounteringThis(self) && g.unbrokenSubs(self).any, reason = "not encountered"))
    }

    object NEXTActivationCommand extends Lockdown("26103") {
        override def iceStrengthMod(self : Card, ice : Card)(implicit g : Game) = 2
        override def breakAllowed(self : Card, breaker : Card, ice : Card)(implicit g : Game) = breaker.data.isIcebreaker
        override def triggers(self : Card, e : Event)(implicit g : Game) = lockdownTriggers(self, e)
    }

    object Scapenet extends NC("26104") {
        override def canPlay(self : Card)(implicit g : Game) = g.successfulRunsLastTurn.any
        override def onPlay(self : Card)(implicit g : Game) = {
            val l = g.runnerInstalled.%(c => g.has(c, "Chip") || g.has(c, "Virtual"))
            $(TraceEffect(7, l.any.$(ChooseCardEffect(Corp, l, RemoveFromGamePurpose, "Scapenet: choose a chip or virtual card to remove from the game", false)), $))
        }
    }

    object TranquilityHomeGrid extends NC("26105") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Installed(Corp, c) if g.isRezzed(self) && (c.data.isAsset || c.data.isAgenda || c.data.isUpgrade) && c != self && g.serverOf(c).exists(s => thisServer(self, s)) && !g.usedThisTurn(self, "thg") =>
                $(MarkUsedEffect(self, "thg"), ChooseOneEffect(Corp, "Tranquility Home Grid: choose one", $(Alt("Gain 2 credits", $(gain(Corp, 2))), Alt("Draw 1 card", $(draw(Corp, 1))))))
            case _ => $
        }
    }

    object FlowerSermon extends NC("26106") {
        override def onScore(self : Card)(implicit g : Game) = $(AddCounterEffect(self, "agenda", 5))
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("reveal the top card of R&D, draw 2 cards, add 1 card from HQ to the top of R&D", $(CounterCost("agenda", 1)), g.deck(Corp).starting.$./(c => RevealCardsEffect(Corp, $(c))) ++ $(draw(Corp, 2), ChooseCardEffect(Corp, g.hand(Corp), TopOfDeckPurpose(Corp), "Flower Sermon: choose a card in HQ to add to the top of R&D", false)), once = once("sermon"), ok = g.zone(self) == ScoredZone(Corp) && g.deck(Corp).any, reason = "empty R&D"))
    }

    object PranaCondenser extends NC("26107") {
        override def preventions(self : Card, kind : String, n : Int)(implicit g : Game) =
            (kind == "net" && g.isRezzed(self)).$(Ability("prevent 1 net damage, place 1 power counter and gain 3 credits", NoCost, $(AddCounterEffect(self, "power", 1), gain(Corp, 3), PreventWindowAction("net", n - 1))))
        override def abilities(self : Card)(implicit g : Game) = $(Ability("do " + g.counter(self, "power") + " net damage", $(ClickCost(2), TrashSelfCost), $(net(g.counter(self, "power"))), click = true, ok = g.isRezzed(self) && g.counter(self, "power") > 0, reason = "no power counters"))
    }

    object EngramFlush extends NC("26108") {
        override def onEncounter(self : Card)(implicit g : Game) = $(ChooseOneEffect(Corp, "Engram Flush: choose a card type", $("Event", "Hardware", "Program", "Resource")./(k => Alt(k, $(SetMemoEffect(self, k))))))
        override def subroutines(self : Card)(implicit g : Game) = $(Sub("Reveal the grip", EngramSubAction(self)), Sub("Reveal the grip", EngramSubAction(self)))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case EncounterEnds(c) if c == self => $(SetMemoEffect(self, ""))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case EngramSubAction(self) =>
                val l = g.hand(Runner).%(_.data.kind == g.state(self).memo)
                if (l.any)
                    g.push(ChooseCardEffect(Corp, l, TrashFromHandPurpose(Runner), "Engram Flush: trash a revealed card of the chosen type?", true))
                g.push(RevealCardsEffect(Runner, g.hand(Runner)))
                NextAction
        }
    }

    object Konjin extends NC("26109") {
        override def onEncounter(self : Card)(implicit g : Game) = $(PsiEffect($, $(KonjinAction(self))))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case KonjinAction(self) =>
                val l = g.rezzedIce.but(self)
                if (l.any)
                    g.push(ChooseCardEffect(Corp, l, KonjinPurpose(self), "Konjin: choose another rezzed piece of ice for the Runner to encounter?", true))
                NextAction
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(c => g.push(ForceEncounterEffect(c)))
            NextAction
        }
    }

    object HyoubuPrecogManifold extends Lockdown("26110") {
        override def onPlay(self : Card)(implicit g : Game) = $(ChooseOneEffect(Corp, "Hyoubu Precog Manifold: choose a server", g.servers./(s => Alt(s.name, $(SetMemoEffect(self, s.name))))))
        override def triggers(self : Card, e : Event)(implicit g : Game) = lockdownTriggers(self, e) ++ (e match {
            case SuccessfulRun(s) if s.name == g.state(self).memo => $(PsiEffect($, $(EndRunEffect)))
            case _ => $
        })
    }

    object Kakurenbo extends NC("26111") {
        override def canPlay(self : Card)(implicit g : Game) = g.clicks(Corp) >= 3
        override def onPlay(self : Card)(implicit g : Game) = $(LoseClicksEffect(Corp, 2), ChooseCardsEffect(Corp, g.hand(Corp).but(self), 0, g.hand(Corp).but(self).num, TrashFromHandPurpose(Corp), "Kakurenbo: choose any number of cards in HQ to trash"), KakurenboAction(self))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case KakurenboAction(self) =>
                g.discard(Corp).foreach(c => g.state(c).seen = false)
                g.log(Corp, "turns all cards in Archives facedown")
                val l = g.discard(Corp).%(c => c.data.isAgenda || c.data.isAsset || c.data.isUpgrade)
                g.push(RemoveFromGameEffect(self))
                if (l.any)
                    g.push(ChooseCardEffect(Corp, l, KakurenboPurpose(self), "Kakurenbo: install a card from Archives in a remote server with 2 advancement counters?", true))
                NextAction
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(c => g.push(ChooseServerEffect(Corp, c, false, g.remotes, true), PlaceAdvancementEffect(c, 2)))
            NextAction
        }
    }

    object GameNET extends NC("26113") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case CreditsLost(Runner, n) if n > 0 && g.run.exists(!_.ended) => $(gain(Corp, 1))
            case _ => $
        }
    }

    object Bellona extends NC("26114") {
        override def accessCostPaidByRunner(self : Card)(implicit g : Game) = c(5)
        override def onScore(self : Card)(implicit g : Game) = $(gain(Corp, 5))
    }

    object F2P extends NC("26115") {
        override def subroutines(self : Card)(implicit g : Game) = $(Sub("Add 1 installed Runner card to the grip", ChooseCardEffect(Corp, g.runnerInstalled, ReturnToHandPurpose, "F2P: choose an installed Runner card to add to the grip", false)), tagSub(1))
        override def runnerAbilities(self : Card)(implicit g : Game) = $(Ability("break 1 subroutine", c(2), $(BioroidBreakEffect(self)), ok = !g.tagged && encounteringThis(self) && g.unbrokenSubs(self).any, reason = "tagged or not encountered"))
    }

    object GoldFarmer extends NC("26116") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SubsBroken(ice, subs, _) if ice == self => $(LoseCreditsEffect(Runner, subs.num))
            case _ => $
        }
        def paySub(implicit g : Game) = Sub("End the run unless the Runner pays 3[c]", ChooseOneEffect(Runner, "Gold Farmer: choose one", $(Alt("Pay 3 credits", $(LoseCreditsEffect(Runner, 3)), g.credits(Runner) >= 3), Alt("End the run", $(EndRunEffect)))))
        override def subroutines(self : Card)(implicit g : Game) = $(paySub, paySub)
    }

    object SYNCRerouting extends Lockdown("26118") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = lockdownTriggers(self, e) ++ (e match {
            case RunInitiated(_) => $(ChooseOneEffect(Runner, "SYNC Rerouting: choose one", $(Alt("Take 1 tag", $(tag(1))), Alt("Pay 4 credits", $(LoseCreditsEffect(Runner, 4)), g.credits(Runner) >= 4))))
            case _ => $
        })
    }

    object Ganked extends NC("26119") {
        override def onAccess(self : Card)(implicit g : Game) = g.isInstalled(self).$(OptionalEffect(Corp, "Ganked!: trash it to force the Runner to encounter a piece of ice protecting this server?", $(GankedAction(self)), $))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case GankedAction(self) =>
                val l = g.serverOf(self)./(s => g.ice(s).%(g.isRezzed)).|($)
                if (l.any)
                    g.push(ChooseCardEffect(Corp, l, GankedPurpose(self), "Ganked!: choose a rezzed piece of ice", false))
                else
                    g.push(TrashEffect(self, Corp, false))
                NextAction
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(c => g.push(TrashEffect(p.card, Corp, false), ForceEncounterEffect(c)))
            NextAction
        }
    }

    object EarthStation extends NC("26120") {
        def flipped(self : Card)(implicit g : Game) = g.counter(self, "flipped") > 0
        override def remoteLimit(self : Card)(implicit g : Game) = 1                    // "Limit 1 remote server" (both sides)
        override def runCost(self : Card, server : Server)(implicit g : Game) =
            if (!flipped(self) && server == HQ) c(1) else if (flipped(self) && server.isRemote) c(6) else $
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(HQ) if flipped(self) => $(EarthStationFlipAction(self, false))
            case _ => $
        }
        override def abilities(self : Card)(implicit g : Game) = $(Ability("flip to Earth Station: Ascending to Orbit", click(), $(EarthStationFlipAction(self, true)), click = true, ok = !flipped(self), reason = "already flipped"))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case EarthStationFlipAction(self, f) =>
                g.state(self).counters += "flipped" -> f.?(1).|(0)
                g.log(Corp, "flips the identity to", f.?("Earth Station: Ascending to Orbit").|("Earth Station: SEA Headquarters").hl)
                NextAction
        }
    }

    object TransportMonopoly extends NC("26121") {
        override def onScore(self : Card)(implicit g : Game) = $(AddCounterEffect(self, "agenda", 2))
        override def abilities(self : Card)(implicit g : Game) = $(Ability("this run cannot be declared successful", $(CounterCost("agenda", 1)), $(BlockSuccessEffect), once = once("monopoly"), ok = g.zone(self) == ScoredZone(Corp) && g.run.exists(r => !r.ended && !r.successful), reason = "only during a run"))
    }

    object WallToWall extends NC("26122") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) if g.isRezzed(self) => $(WallToWallAction(self, g.installedCards(Corp).exists(c => c != self && c.data.isAsset && g.isRezzed(c)).?(1).|(3), $))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case WallToWallAction(self, n, done) =>
                if (n > 0 && g.isRezzed(self)) {
                    val next = (n > 1).$(WallToWallAction(self, n - 1, done))
                    val opts = $(
                        ("draw", Alt("Draw 1 card", draw(Corp, 1) +: next./(x => WallToWallAction(self, n - 1, done :+ "draw")))),
                        ("gain", Alt("Gain 1 credit", gain(Corp, 1) +: next./(x => WallToWallAction(self, n - 1, done :+ "gain")))),
                        ("adv", Alt("Place 1 advancement counter on a piece of ice", ChooseCardEffect(Corp, g.allIce, AdvancePurpose(1), "Wall to Wall: choose a piece of ice", false) +: next./(x => WallToWallAction(self, n - 1, done :+ "adv")), g.allIce.any)),
                        ("hand", Alt("Add Wall to Wall to HQ", $(ReturnToHandEffect(self)))))
                    g.push(ChooseOneEffect(Corp, "Wall to Wall: choose one", opts.%!((k, alt) => done.has(k))./((k, alt) => alt) :+ Alt("Done", $)))
                }
                NextAction
        }
    }

    object Akhet extends NC("26123") {
        override def advanceable(self : Card)(implicit g : Game) = true
        override def strength(self : Card)(implicit g : Game) = (g.advancement(self) >= 3).?(3).|(0)
        override def breakAllowed(self : Card, breaker : Card, ice : Card)(implicit g : Game) = ice != self || g.advancement(self) < 3 || g.state(self).brokenSubs.none
        override def subroutines(self : Card)(implicit g : Game) = $(Sub("Gain 1[c]. Place 1 advancement counter on an installed card", CopyAbilityEffect($(gain(Corp, 1), ChooseCardEffect(Corp, g.installedCards(Corp), AdvancePurpose(1), "Akhet: choose an installed card", false)))), etrSub)
    }

    object Colossus extends NC("26124") {
        override def advanceable(self : Card)(implicit g : Game) = true
        override def strength(self : Card)(implicit g : Game) = g.advancement(self)
        override def subroutines(self : Card)(implicit g : Game) = $(
            Sub("Give the Runner 1 tag (2 tags with 3 or more advancement counters)", tag((g.advancement(self) >= 3).?(2).|(1))),
            Sub("Trash 1 installed program (and 1 resource with 3 or more advancement counters)", ColossusTrashAction(self)))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case ColossusTrashAction(self) =>
                if (g.advancement(self) >= 3)
                    g.push(RunnerTrashInstalledEffect("Resource", Corp, "Colossus: choose a resource to trash"))
                g.push(RunnerTrashInstalledEffect("Program", Corp, "Colossus: choose a program to trash"))
                NextAction
        }
    }

    object Winchester extends NC("26125") {
        override def subroutines(self : Card)(implicit g : Game) = $(
            Sub("Trace[4]: trash 1 installed program", TraceEffect(4, $(RunnerTrashInstalledEffect("Program", Corp, "Winchester: choose a program to trash")), $)),
            Sub("Trace[3]: trash 1 installed piece of hardware", TraceEffect(3, $(RunnerTrashInstalledEffect("Hardware", Corp, "Winchester: choose a piece of hardware to trash")), $))) ++
            g.serverOf(self).has(HQ).$(Sub("Trace[3]: end the run", TraceEffect(3, $(EndRunEffect), $)))
    }

    object ArgusCrackdown extends Lockdown("26126") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = lockdownTriggers(self, e) ++ (e match {
            case SuccessfulRun(s) if g.ice(s).any => $(meat(2))
            case _ => $
        })
    }

    object CyberdexSandbox extends NC("26128") {
        override def onScore(self : Card)(implicit g : Game) = $(OptionalEffect(Corp, "Cyberdex Sandbox: purge virus counters?", $(PurgeEffect), $))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Purged(_) if g.zone(self) == ScoredZone(Corp) && !g.usedThisTurn(self, "sandbox") => $(MarkUsedEffect(self, "sandbox"), gain(Corp, 4))
            case _ => $
        }
    }

    object FalseLead extends NC("26129") {
        override def abilities(self : Card)(implicit g : Game) = $(Ability("forfeit: the Runner loses 2 clicks", NoCost, $(ForfeitEffect(self), LoseClicksEffect(Runner, 2)), ok = g.zone(self) == ScoredZone(Corp) && g.clicks(Runner) >= 2, reason = "the Runner needs 2 clicks"))
    }

    object NAPDCordon extends Lockdown("26130") {
        override def stealCostMod(self : Card, agenda : Card)(implicit g : Game) = c(4 + 2 * g.advancement(agenda))
        override def triggers(self : Card, e : Event)(implicit g : Game) = lockdownTriggers(self, e)
    }

    val all : $[CardImpl] = $(
        Hoshiko, Moshing, DevilCharm, Gachapon, Keiko, Odore, PaladinPoemu, Bravado, Boomerang, MuSafecracker, PrognosticQLoop, Afterimage,
        Makler, PenumbralToolkit, TheBack, HarmonyARTherapy, Aniccam, Simulchip, Cordyceps, Euler, Mantle, Penrose, SelfModifyingCode,
        PaulesCafe, BufferDrive, DailyCasts, DreamNet,
        ProjectVacheron, Bass, CerebralOverwriter, VaporframeFabricator, Drafter, Tyr, NEXTActivationCommand, Scapenet, TranquilityHomeGrid,
        FlowerSermon, PranaCondenser, EngramFlush, Konjin, HyoubuPrecogManifold, Kakurenbo, GameNET, Bellona, F2P, GoldFarmer, SYNCRerouting,
        Ganked, EarthStation, TransportMonopoly, WallToWall, Akhet, Colossus, Winchester, ArgusCrackdown, CyberdexSandbox, FalseLead, NAPDCordon,
    )
}
