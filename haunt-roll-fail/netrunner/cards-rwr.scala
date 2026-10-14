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
// Rebellion Without Rehearsal (rwr) card implementations, ported from jinteki.net src/clj/game/cards/*.clj
// ---------------------------------------------------------------------------------------------------------------------

// card specific actions and purposes
case class SebastiaoPurpose(card : Card) extends CardPurpose
case class WizardChestAction(card : Card, kind : String) extends CardAction
case class WizardChestPurpose(card : Card) extends CardPurpose
case class PrivilegedAccessAction(card : Card) extends CardAction
case class MeetingOfMindsAction(card : Card, kind : String) extends CardAction
case class MeetingOfMindsPurpose(card : Card, kind : String) extends CardPurpose
case class MeetingOfMindsGainAction(card : Card, kind : String) extends CardAction
case class WindowPurpose(card : Card) extends CardPurpose
case class WindowRezAction(card : Card, ice : Card) extends CardAction
case class JeitinhoWinAction(card : Card) extends CardAction
case class CupellationPurpose(card : Card) extends CardPurpose
case class BurnerPurpose(card : Card) extends CardPurpose
case class BurnerPickPurpose(card : Card, remaining : $[Card], n : Int) extends CardPurpose
case class BurnerPlaceAction(card : Card, chosen : Card, remaining : $[Card], n : Int, top : Boolean) extends CardAction
case class SpreePurpose(card : Card) extends CardPurpose
case class SpreeHostPurpose(card : Card, trojan : Card) extends CardPurpose
case class CataloguerBreachAction(card : Card) extends CardAction
case class MuseAction(card : Card, zone : String) extends CardAction
case class MusePurpose(card : Card, zone : String) extends CardPurpose
case class MaryAction(card : Card) extends CardAction
case class MaryCheckAction(card : Card) extends CardAction
case class ActivePolicingOfferAction(card : Card) extends CardAction
case class BringThemHomeShuffleAction(card : Card) extends CardAction
case class AshenAction(card : Card) extends CardAction
case class AshenRfgAction(card : Card) extends CardAction
case class WarmReceptionAction(card : Card) extends CardAction
case class WarmReceptionPurpose(card : Card) extends CardPurpose
case class WarmReceptionDerezAction(card : Card) extends CardAction
case class WarmReceptionDerezPurpose(card : Card) extends CardPurpose
case class LycianAction(card : Card, chosen : $[String]) extends CardAction
case class LycianAddAction(card : Card, subtype : String, chosen : $[String]) extends CardAction
case class LycianClearAction(card : Card) extends CardAction
case class LycianSubAction(card : Card, idx : Int) extends CardAction
case class SorocabanSubAction(card : Card, kind : String) extends CardAction
case class BrasiliaPurpose(card : Card, ice : Card) extends CardPurpose
case class TributaryPurpose(card : Card) extends CardPurpose
case class BringThemHomePurpose(card : Card) extends CardPurpose
case class BringThemHomeShufflePurpose(card : Card) extends CardPurpose
case class KingmakingAction(card : Card) extends CardAction
case class JKInstallAction(card : Card) extends CardAction
case class PiranhasSubAction(card : Card) extends CardAction
case class SuddenCommandmentPurpose(card : Card) extends CardPurpose
case class SuddenCommandmentThreatAction(card : Card, first : Boolean) extends CardAction
case class HoloManMoveAction(card : Card) extends CardAction
case class NuvemLookAction(card : Card) extends CardAction
case class NuvemTrashAction(card : Card) extends CardAction
case class HeartsAndMindsFromPurpose(card : Card) extends CardPurpose
case class HeartsAndMindsToPurpose(card : Card, from : Card) extends CardPurpose
case class HeartsAndMindsPoliticalAction(card : Card) extends CardAction
case class BusinessAsUsualAction(card : Card, done : $[String]) extends CardAction
case class IsaacMovedAction(card : Card) extends CardAction
case class BotoSubAction(card : Card) extends CardAction
case class CloudEaterAction(card : Card) extends CardAction
case class ThunderboltSubAction(card : Card) extends CardAction
case class BoiTataRegisterAction(card : Card) extends CardAction
case class CupellationHostAction(card : Card, target : Card) extends CardAction
case class TrickShotRemoteAction(card : Card) extends CardAction


object RWRCards {
    // ------------------------------------------------------------------------------------------------------------
    // Runner
    // ------------------------------------------------------------------------------------------------------------

    object Sebastiao extends NC("34066") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TagsGained(n) if g.tags == n =>
                val l = g.gripCards.%(c => c.data.isResource && g.has(c, "Connection") && g.canAffordInstall(c, 2))
                l.any.$(ChooseCardEffect(Runner, l, InstallPayingPurpose(2, None, false), "Sebastiao: install a connection paying 2 credits less?", true))
            case _ => $
        }
    }

    object EyeForAnEye extends NC("34067") {
        override def canPlay(self : Card)(implicit g : Game) = !g.tagged
        override def makesRun(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = $(RunEffect(HQ, |(self)))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(HQ) if thisCardRun(self) => $(tag(1), AccessBonusEffect(HQ, 1))
            case _ => $
        }
        override def accessAbility(self : Card, target : Card)(implicit g : Game) =
            (!g.zone(target).is[DiscardZone] && g.run.exists(!_.cannotStealOrTrash)).?(Ability("trash the accessed card", $(TrashFromHandCost(1)), $(TrashEffect(target, Runner, true)), ok = g.hand(Runner).any, reason = "no cards in grip"))
    }

    object PrivilegedAccess extends NC("34068") {
        override def canPlay(self : Card)(implicit g : Game) = !g.tagged
        override def makesRun(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = $(RunEffect(Archives, |(self)))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(Archives) if thisCardRun(self) => $(ReplaceBreachEffect, tag(1), PrivilegedAccessAction(self))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case PrivilegedAccessAction(self) =>
                val res = g.discard(Runner).%(c => c.data.isResource && g.canAffordInstall(c, 2))
                val prog = g.threat(3).??(g.discard(Runner).%(c => c.data.isProgram && g.canAffordInstall(c, 0)))
                if (prog.any)
                    g.push(ChooseCardEffect(Runner, prog, InstallPayingPurpose(0, None, false), "Privileged Access: install a program from the heap?", true))
                if (res.any)
                    g.push(ChooseCardEffect(Runner, res, InstallPayingPurpose(2, None, false), "Privileged Access: install a resource from the heap paying 2 credits less?", true))
                NextAction
        }
    }

    object Amanuensis extends NC("34069") {
        override def memory(self : Card)(implicit g : Game) = 1
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnEnds(Runner) if g.tagged => $(AddCounterEffect(self, "power", 1))
            case TagsRemoved(_) if g.counter(self, "power") > 0 => $(OptionalEffect(Runner, "Amanuensis: remove 1 power counter to draw 2 cards?", $(AddCounterEffect(self, "power", -1), draw(Runner, 2)), $))
            case _ => $
        }
    }

    object WizardsChest extends NC("34070") {
        override def abilities(self : Card)(implicit g : Game) = {
            val ok = Server.centrals.forall(s => g.successfulRunsThisTurn.has(s))
            $(Ability("reveal cards from the stack until 2 of a chosen type and install 1 ignoring costs", $(TrashSelfCost), $(ChooseOneEffect(Runner, "Wizard's Chest: choose a type", $("Hardware", "Program", "Resource")./(k => Alt(k, $(WizardChestAction(self, k)))))), ok = ok && g.deck(Runner).any, reason = "needs successful runs on HQ, R&D and Archives"))
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case WizardChestAction(self, kind) =>
                var found : $[Card] = $
                var seen : $[Card] = $
                g.deck(Runner).foreach { c =>
                    if (found.num < 2) {
                        seen :+= c
                        if (c.data.kind == kind)
                            found :+= c
                    }
                }
                g.log(Runner, "reveals", seen./(g.cardElem).comma, "from the top of the stack")
                if (found.any)
                    g.push(ChooseCardEffect(Runner, found, WizardChestPurpose(self), "Wizard's Chest: install a card ignoring all costs?", true), ShuffleDeckEffect(Runner))
                else
                    g.push(ShuffleDeckEffect(Runner))
                NextAction
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(c => g.pushAll(g.installRunner(c, 99)))
            NextAction
        }
    }

    // "If you trashed any of your installed cards this turn": installed Runner cards trashed by the Runner this turn (whether or not
    // Boi-tata was installed then), counted by FinalNsgRules
    object BoiTata extends NC("34071") {
        def discount(implicit g : Game) = (g.reg("fns-runner-trashed-own-installed") > 0).?(1).|(0)
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(2 - discount), 2, $("Sentry")))
        override def pumps(self : Card)(implicit g : Game) = $(Pump(c(3 - discount), 3))
    }

    object Heliamphora extends NC("34072") {
        override def accessAbility(self : Card, target : Card)(implicit g : Game) =
            (g.zone(target) == DiscardZone(Corp) && !g.state(self).usedRun.has("heliamphora")).?(Ability("host the accessed card on Heliamphora instead of accessing it", NoCost, $(HostOnEffect(target, self, true), MarkUsedRunEffect(self, "heliamphora"))))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Purged(_) => $(RandomPickEffect(Corp, g.hand(Corp), 2, TrashFromHandPurpose(Corp)), TrashEffect(self, Runner, false))
            case _ => $
        }
    }

    object ArruaceirasCrew extends NC("34073") {
        override def abilities(self : Card)(implicit g : Game) = {
            val ice = encountering(self)
            $(
                Ability("give the encountered ice -2 strength", $(TagCost(1)), ice.$./(i => PumpIceEffect(i, -2, EncounterDuration)), once = once("crew"), ok = ice.any, reason = "not encountering ice"),
                Ability("trash the encountered ice if its strength is 0 or less", $(TrashSelfCost, CreditCost(2)), ice.$./(i => TrashEffect(i, Runner, false)), ok = ice.exists(i => g.iceStrength(i) <= 0), reason = "ice strength is above 0"))
        }
    }

    object FriendOfAFriend extends NC("34074") {
        override def abilities(self : Card)(implicit g : Game) = $(
            Ability("gain 5 credits and remove 1 tag", $(ClickCost(1), TrashSelfCost), $(gain(Runner, 5), RemoveTagsEffect(1)), click = true),
            Ability("gain 9 credits and take 1 tag", $(ClickCost(1), TrashSelfCost), $(gain(Runner, 9), tag(1)), click = true, ok = !g.tagged, reason = "tagged"))
    }

    object ManuelLattesDeMoura extends NC("34075") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case BreachServer(s) if (s == HQ || s == RnD) && g.tagged => $(AccessBonusEffect(s, 1))
            case _ => $
        }
    }

    object MeetingOfMinds extends NC("34076") {
        override def onPlay(self : Card)(implicit g : Game) = $(ChooseOneEffect(Runner, "Meeting of Minds: choose a subtype", $("Connection", "Virtual")./(k => Alt(k, $(MeetingOfMindsAction(self, k))))))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case MeetingOfMindsAction(self, kind) =>
                val l = g.deck(Runner).%(c => c.data.isResource && g.has(c, kind)).sortBy(_.title)
                if (l.any)
                    g.push(OptionalEffect(Runner, "Meeting of Minds: search the stack for a resource?", $(ChooseCardEffect(Runner, l, MeetingOfMindsPurpose(self, kind), "Choose a resource to add to the grip", true), ShuffleDeckEffect(Runner), StackSearchedEffect(self), MeetingOfMindsGainAction(self, kind)), $(MeetingOfMindsGainAction(self, kind))))
                else
                    g.push(MeetingOfMindsGainAction(self, kind))
                NextAction
            case MeetingOfMindsGainAction(self, kind) =>
                val l = g.hand(Runner).%(c => g.has(c, kind))
                if (l.any)
                    g.push(ChooseCardsEffect(Runner, l, 0, l.num, MeetingOfMindsPurpose(self, "gain"), "Meeting of Minds: reveal cards to gain 1 credit each"))
                NextAction
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case MeetingOfMindsPurpose(_, "gain") =>
                if (cards.any)
                    g.push(RevealCardsEffect(Runner, cards), gain(Runner, cards.num))
                NextAction
            case _ =>
                cards.single.foreach { c =>
                    g.log(Runner, "reveals", c, "from the stack and adds it to the grip")
                    g.moveTo(c, HandZone(Runner))
                }
                NextAction
        }
    }

    object WindowOfOpportunity extends NC("34077") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = {
            val l = g.gripCards.%(c => (c.data.isProgram || c.data.isHardware) && g.canAffordInstall(c, 0))
            l.any.$(ChooseCardEffect(Runner, l, InstallPayingPurpose(0, None, false), "Window of Opportunity: install a program or piece of hardware?", true)) :+ RunAnyServerEffect(|(self), g.servers)
        }
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case RunInitiated(s) if thisCardRun(self) && g.ice(s).%(g.isRezzed).any =>
                $(ChooseCardEffect(Runner, g.ice(s).%(g.isRezzed), WindowPurpose(self), "Window of Opportunity: choose a piece of ice to derez", false))
            case _ => $
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(c => g.push(DerezEffect(c), AtRunEndEffect($(WindowRezAction(p.card, c)))))
            NextAction
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case WindowRezAction(self, ice) =>
                if (g.isInstalled(ice) && !g.isRezzed(ice))
                    g.push(OptionalEffect(Corp, "Window of Opportunity: rez the derezzed ice ignoring all costs?", $(RezEffect(ice, true)), $))
                NextAction
        }
    }

    object AlarmClock extends NC("34078") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) => $(OptionalEffect(Runner, "Alarm Clock: run HQ?", $(RunEffect(HQ, |(self))), $))
            case EncounterIce(ice) if thisCardRun(self) && !g.state(self).usedRun.has("alarm") =>
                $(MarkUsedRunEffect(self, "alarm")) ++ (g.clicks(Runner) >= 2).$(OptionalEffect(Runner, "Alarm Clock: spend 2 clicks to bypass this ice?", $(LoseClicksEffect(Runner, 2), BypassEffect), $))
            case _ => $
        }
    }

    object Jeitinho extends NC("34079") {
        override def activeInHeap(self : Card)(implicit g : Game) = true
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case IceBypassed(_) if g.threat(3) && g.zone(self) == DiscardZone(Runner) && g.clicks(Runner) >= 1 =>
                $(OptionalEffect(Runner, "Jeitinho: lose a click to install it from the heap?", $(LoseClicksEffect(Runner, 1), RunnerInstallPayingEffect(self, None, 0)), $))
            case TurnEnds(Runner) if g.isInstalled(self) && Server.centrals.forall(s => g.successfulRunsThisTurn.has(s)) =>
                $(OptionalEffect(Runner, "Jeitinho: add it to the score area as an assassination agenda?", $(AddToScoreAreaEffect(self, Runner), JeitinhoWinAction(self)), $))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case JeitinhoWinAction(self) =>
                if (g.scored(Runner).%(_.code == self.code).num >= 3)
                    GameOverAction(|(Runner), "Runner completed the assassination plot (Jeitinho)")
                else
                    NextAction
        }
    }

    object Cupellation extends NC("34080") {
        def hostedCorp(self : Card)(implicit g : Game) = g.hosted(self).%(c => g.owner(c).isCorp)
        override def accessAbility(self : Card, target : Card)(implicit g : Game) =
            // an access ability, also on cards accessed in Archives (the engine offers "host" access abilities there)
            (!target.data.isAgenda && hostedCorp(self).none).?(Ability("host the accessed card on Cupellation", c(1), $(HostOnEffect(target, self, true))))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case BreachServer(HQ) if hostedCorp(self).any && g.canSpend(Runner, 1, Spend.ability(self)) =>
                $(OptionalEffect(Runner, "Cupellation: pay 1 credit and trash it to access 2 additional cards?", $(SpendEffect(Runner, 1, Spend.ability(self), 0, $(TrashEffect(self, Runner, false), AccessBonusEffect(HQ, 2)))), $))
            case _ => $
        }
    }

    object Malandragem extends NC("34081") {
        override def initialCounters(self : Card)(implicit g : Game) = Map("power" -> 2)
        override def triggers(self : Card, e : Event)(implicit g : Game) = rfgOnEmpty(self, e, "power") ++ (e match {
            case EncounterIce(ice) =>
                g.threat(4).$(OptionalEffect(Runner, "Malandragem: remove it from the game to bypass this ice?", $(RemoveFromGameEffect(self), BypassEffect), $)) ++
                (g.counter(self, "power") >= 1 && g.iceStrength(ice) <= 3 && !g.usedThisTurn(self, "malandragem")).$(OptionalEffect(Runner, "Malandragem: remove 1 power counter to bypass this ice?", $(MarkUsedEffect(self, "malandragem"), AddCounterEffect(self, "power", -1), BypassEffect), $))
            case _ => $
        })
    }

    object PhysarumEntangler extends NC("34082") {
        override def mustBeHosted(self : Card)(implicit g : Game) = true
        override def hostRequirement(self : Card, host : Card)(implicit g : Game) = host.data.isIce
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case EncounterIce(ice) if g.state(self).host.has(ice) && !g.has(ice, "Barrier") && g.canSpend(Runner, g.subs(ice).num, Spend.effect(|(self))) =>
                val n = g.subs(ice).num
                $(OptionalEffect(Runner, "Physarum Entangler: pay " + n + " credits to bypass this ice?", $(SpendEffect(Runner, n, Spend.effect(|(self)), 0, $(BypassEffect))), $))
            case Purged(_) => $(TrashEffect(self, Runner, false))
            case _ => $
        }
    }

    object AmeliaEarhart extends NC("34083") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case RunEnds(s, _, _) if (s == HQ || s == RnD) && g.run.exists(_.accessCount >= 3) => $(AddCounterEffect(self, "power", 1))
            case TurnBegins(Runner) if g.counter(self, "power") >= 3 =>
                $(OptionalEffect(Runner, "Amelia Earhart: trash it to make the Corp lose 10 credits?", $(TrashEffect(self, Runner, false), LoseCreditsEffect(Corp, 10)), $))
            case _ => $
        }
    }

    object JuliMoreiraLee extends NC("34084") {
        override def initialCounters(self : Card)(implicit g : Game) = Map("power" -> 4)
        override def triggers(self : Card, e : Event)(implicit g : Game) = trashOnEmpty(self, e, "power") ++ (e match {
            case AbilityUsed(Runner, c, true) if c.data.isResource && g.isInstalled(c) && !g.usedThisTurn(self, "juli") =>
                $(MarkUsedEffect(self, "juli"), GainClicksEffect(Runner, 1), AddCounterEffect(self, "power", -1))
            case _ => $
        })
    }

    object Burner extends NC("34085") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = $(RunEffect(HQ, |(self)))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(HQ) if thisCardRun(self) => $(ReplaceBreachEffect) ++ g.hand(Corp).any.$(RandomPickEffect(Runner, g.hand(Corp), 3, BurnerPurpose(self)))
            case _ => $
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case BurnerPurpose(self) =>
                if (cards.any)
                    g.push(ChooseCardEffect(Runner, cards, BurnerPickPurpose(self, cards, 2.atMost(cards.num)), "Burner: choose a card to add to R&D", false))
                g.push(RevealCardsEffect(Corp, cards))
                NextAction
            case BurnerPickPurpose(self, remaining, n) =>
                cards.single.foreach { c =>
                    g.push(ChooseOneEffect(Runner, "Burner: where to put the card?", $(
                        Alt("Top of R&D", $(BurnerPlaceAction(self, c, remaining.but(c), n - 1, true))),
                        Alt("Bottom of R&D", $(BurnerPlaceAction(self, c, remaining.but(c), n - 1, false))))))
                }
                NextAction
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case BurnerPlaceAction(self, c, remaining, n, top) =>
                g.moveTo(c, DeckZone(Corp), top)
                g.log(Runner, "adds", c, "to the", top.?("top").|("bottom"), "of R&D")
                if (n > 0 && remaining.any)
                    g.push(ChooseCardEffect(Runner, remaining, BurnerPickPurpose(self, remaining, n), "Burner: choose a card to add to R&D", false))
                NextAction
        }
    }

    object Spree extends NC("34086") {
        override def initialCounters(self : Card)(implicit g : Game) = Map("power" -> 3)
        override def makesRun(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = $(RunAnyServerEffect(|(self), g.servers))
        override def abilities(self : Card)(implicit g : Game) = {
            val trojans = g.runnerInstalled.%(c => c.data.isProgram && g.has(c, "Trojan"))
            val ices = g.run./(r => g.ice(r.server)).|($)
            $(Ability("host an installed trojan on a piece of ice protecting the attacked server", $(CounterCost("power", 1)), $(ChooseCardEffect(Runner, trojans, SpreePurpose(self), "Spree: choose a trojan", false)), ok = thisCardRun(self) && trojans.any && ices.any, reason = "needs a trojan and ice on the attacked server"))
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case SpreePurpose(self) =>
                cards.single.foreach(t => g.push(ChooseCardEffect(Runner, g.run./(r => g.ice(r.server)).|($), SpreeHostPurpose(self, t), "Spree: choose a piece of ice to host the trojan on", false)))
                NextAction
            case SpreeHostPurpose(self, t) =>
                cards.single.foreach(i => g.push(HostOnEffect(t, i, false)))
                NextAction
        }
    }

    object TrickShot extends NC("34087") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def initialCounters(self : Card)(implicit g : Game) = Map("credit" -> 4)
        override def creditsUsableForRun(self : Card)(implicit g : Game) = thisCardRun(self)
        override def onPlay(self : Card)(implicit g : Game) = $(RunEffect(RnD, |(self)))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(RnD) if thisCardRun(self) && !g.usedThisTurn(self, "ts") => $(MarkUsedEffect(self, "ts"), AddCounterEffect(self, "credit", 2), AccessBonusEffect(RnD, 1))
            case RunEnds(_, _, _) if thisCardRun(self) && !g.usedThisTurn(self, "ts2") && g.remotes.any => $(MarkUsedEffect(self, "ts2"), TrickShotRemoteAction(self))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case TrickShotRemoteAction(self) =>
                g.push(OptionalEffect(Runner, "Trick Shot: run a remote server?", $(RunAnyServerEffect(|(self), g.remotes)), $))
                NextAction
        }
    }

    object Cataloguer extends NC("34088") {
        override def initialCounters(self : Card)(implicit g : Game) = Map("power" -> 2)
        override def triggers(self : Card, e : Event)(implicit g : Game) = trashOnEmpty(self, e, "power") ++ (e match {
            case SuccessfulRun(RnD) if g.counter(self, "power") > 0 && g.deck(Corp).num >= 2 && g.run.exists(r => !r.preventAccess) =>
                $(OptionalEffect(Runner, "Cataloguer: instead of breaching R&D, remove 1 power counter to arrange the top 4 cards?", $(ReplaceBreachEffect, AddCounterEffect(self, "power", -1), ArrangeTopEffect(Runner, Corp, 4)), $))
            case _ => $
        })
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("breach R&D", $(ClickCost(1), CounterCost("power", 1)), $(CataloguerBreachAction(self)), click = true, ok = g.successfulRunsThisTurn.has(RnD) && g.run.none, reason = "needs a successful run on R&D this turn"))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case CataloguerBreachAction(self) =>
                // a breach outside a run: no run begins or ends (BreachServerEffect's access-only pseudo-run)
                if (g.run.none)
                    g.push(BreachServerEffect(RnD))
                NextAction
        }
    }

    object Coalescence extends NC("34089") {
        override def initialCounters(self : Card)(implicit g : Game) = Map("power" -> 2)
        override def abilities(self : Card)(implicit g : Game) = $(Ability("gain 2 credits", $(CounterCost("power", 1)), $(gain(Runner, 2)), ok = g.active.isRunner, reason = "only during your turn"))
    }

    object Lobisomem extends NC("34090") {
        override def initialCounters(self : Card)(implicit g : Game) = Map("power" -> 1)
        override def breaks(self : Card)(implicit g : Game) = $(
            BreakAbility(c(1), 1, $("Code Gate")),
            BreakAbility($(CreditCost(1), CounterCost("power", 1)), 0, $("Barrier"), |("break X barrier subroutines (X credits, 1 power counter)"), $, true))
        override def pumps(self : Card)(implicit g : Game) = $(Pump(c(1), 2))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SubsBroken(ice, l, b) if b == self && g.has(ice, "Code Gate") && g.fxFullyBroke(ice, l, b) => $(AddCounterEffect(self, "power", 1))
            case _ => $
        }
    }

    object Muse extends NC("34091") {
        override def onInstall(self : Card)(implicit g : Game) = $(ChooseOneEffect(Runner, "Muse: search where?", $("Grip", "Stack", "Heap")./(z => Alt(z, $(MuseAction(self, z))))))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case MuseAction(self, z) =>
                val from = z match { case "Grip" => g.gripCards ; case "Stack" => g.deck(Runner).sortBy(_.title) ; case _ => g.discard(Runner) }
                val l = from.%(c => c.data.isProgram && !g.has(c, "Daemon") && g.canAffordInstall(c, 0))
                if (z == "Stack")
                    g.push(ShuffleDeckEffect(Runner), StackSearchedEffect(self))
                if (l.any)
                    g.push(ChooseCardEffect(Runner, l, MusePurpose(self, z), "Muse: choose a non-daemon program to install", true))
                NextAction
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach { c =>
                if (g.has(c, "Trojan"))
                    g.pushAll(g.installRunner(c, 0))
                else
                    g.push(RunnerInstallEffect(c, |(p.card), false))
            }
            NextAction
        }
    }

    object PressureSpike extends NC("34092") {
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(1), 1, $("Barrier")))
        override def pumps(self : Card)(implicit g : Game) = $(Pump(c(2), 3))
        override def abilities(self : Card)(implicit g : Game) =
            (g.threat(4) && encountering(self).any).$(Ability("+9 strength (once per run)", c(2), $(PumpBreakerEffect(self, 9, EncounterDuration), MarkUsedRunEffect(self, "spike")), ok = !g.state(self).usedRun.has("spike"), reason = "already used this run"))
    }

    object PrettyMary extends NC("34093") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case BreachServer(RnD) => $(MaryAction(self))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            // the Runner resolves Pretty Mary last among the breach abilities: the check waits until the cards are about to be
            // accessed (just before AccessServerAction), so every access bonus of this breach is known
            case MaryAction(self) =>
                val i = g.pending.indexWhere(_ == AccessServerAction(RnD))
                if (i > 0)
                    g.pending = g.pending.take(i) ++ $(MaryCheckAction(self)) ++ g.pending.drop(i)
                else
                    g.push(MaryCheckAction(self))
                NextAction
            case MaryCheckAction(self) =>
                if (g.run.exists(r => 1 + r.accessBonusFor("R&D") + r.accessBonusFor("total") >= 2))
                    g.push(OptionalEffect(Runner, "Pretty Mary: access 1 additional card?", $(AccessBonusEffect(RnD, 1)), $))
                NextAction
        }
    }

    object AshenEpilogue extends NC("34094") {
        override def onPlay(self : Card)(implicit g : Game) = $(AshenAction(self))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case AshenAction(self) =>
                (g.hand(Runner) ++ g.discard(Runner)).foreach(c => g.moveTo(c, DeckZone(Runner)))
                g.log(Runner, "shuffles the grip and the heap into the stack")
                g.push(ShuffleDeckEffect(Runner), AshenRfgAction(self))
                NextAction
            case AshenRfgAction(self) =>
                val l = g.deck(Runner).take(5)
                l.foreach(c => g.moveTo(c, RemovedZone(Runner)))
                g.log(l.num.cards, "removed from the game from the top of the stack")
                g.push(draw(Runner, 5), RemoveFromGameEffect(self))
                NextAction
        }
    }

    object Valentina extends NC("34095") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TagsRemoved(_) => $(gain(Runner, 1))
            case _ => $
        }
        override def onInstall(self : Card)(implicit g : Game) =
            (g.threat(3) && g.active.isRunner).$(ChooseOneEffect(Runner, "Valentina: choose one", $(Alt("Remove 1 tag", $(RemoveTagsEffect(1)), g.tagged), Alt("Gain 2 credits", $(gain(Runner, 2))), Alt("Done", $))))
    }

    // ------------------------------------------------------------------------------------------------------------
    // Corp
    // ------------------------------------------------------------------------------------------------------------

    object ThunderboltArmaments extends NC("34096") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Rezzed(c) if g.run.exists(!_.ended) && c.data.isIce && (g.has(c, "AP") || g.has(c, "Destroyer")) =>
                $(MessageEffect(Corp, "uses Thunderbolt Armaments: the ice gets +1 strength and an additional subroutine"), PumpIceEffect(c, 1, RunDuration), MarkUsedRunEffect(c, "thunderbolt"))
            case _ => $
        }
        override def extraSubroutines(self : Card, ice : Card)(implicit g : Game) =
            g.state(ice).usedRun.has("thunderbolt").$(Sub("End the run unless the Runner trashes 1 of their installed cards", ThunderboltSubAction(self)))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case ThunderboltSubAction(self) =>
                g.push(ChooseOneEffect(Runner, "Thunderbolt Armaments: choose one", $(
                    Alt("End the run", $(EndRunEffect)),
                    Alt("Trash 1 installed card", $(RunnerTrashInstalledEffect("", Runner, "Choose an installed card to trash")), g.runnerInstalled.any))))
                NextAction
        }
    }

    object LightningLaboratory extends NC("34097") {
        override def onScore(self : Card)(implicit g : Game) = $(AddCounterEffect(self, "agenda", 1))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case RunInitiated(s) if g.zone(self) == ScoredZone(Corp) && g.counter(self, "agenda") > 0 && g.ice(s).%(c => !g.isRezzed(c)).any =>
                $(OptionalEffect(Corp, "Lightning Laboratory: remove 1 agenda counter to rez up to 2 pieces of ice ignoring all costs?",
                    $(AddCounterEffect(self, "agenda", -1), MarkUsedEffect(self, "lightning"), SetMemoEffect(self, s.name), ChooseCardsEffect(Corp, g.ice(s).%(c => !g.isRezzed(c)), 0, 2, RezPurpose(true), "Lightning Laboratory: choose up to 2 pieces of ice to rez")), $))
            case TurnEnds(_) if g.usedThisTurn(self, "lightning") =>
                val l = g.serverByName(g.state(self).memo)./(s => g.ice(s).%(g.isRezzed)).|($)
                val n = 2.atMost(l.num)
                (n > 0).$(ChooseCardsEffect(Corp, l, n, n, DerezPurpose, "Lightning Laboratory: choose " + n + " pieces of ice to derez"))
            case _ => $
        }
    }

    object WarmReception extends NC("34098") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) if g.isRezzed(self) => $(WarmReceptionAction(self))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case WarmReceptionAction(self) =>
                val l = g.hand(Corp).%(corpInstallable)
                g.push(WarmReceptionDerezAction(self))
                if (l.any)
                    g.push(ChooseCardEffect(Corp, l, WarmReceptionPurpose(self), "Warm Reception: install a card from HQ? (it cannot be scored this turn)", true))
                NextAction
            case WarmReceptionDerezAction(self) =>
                val l = g.installedCards(Corp).%(c => c != self && g.isRezzed(c))
                if (g.isRezzed(self) && g.serverOf(self).exists(g.unprotected) && l.any)
                    g.push(ChooseCardEffect(Corp, l, WarmReceptionDerezPurpose(self), "Warm Reception: derez it to derez another card?", true))
                NextAction
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case WarmReceptionPurpose(self) =>
                cards.single.foreach(c => g.push(ChooseServerEffect(Corp, c, false, (c.data.isIce || c.data.isUpgrade).?(g.servers).|(g.remotes), true), MarkUsedEffect(c, "no-score")))
                NextAction
            case WarmReceptionDerezPurpose(self) =>
                cards.single.foreach(c => g.push(DerezEffect(self), DerezEffect(c)))
                NextAction
        }
    }

    object WorkingPrototype extends NC("34099") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Rezzed(_) if g.isRezzed(self) => $(AddCounterEffect(self, "power", 1))
            case _ => $
        }
        override def abilities(self : Card)(implicit g : Game) = $(
            Ability("gain 3 credits", $(ClickCost(1), CounterCost("power", 1)), $(gain(Corp, 3)), click = true, ok = g.isRezzed(self), reason = "not rezzed"),
            Ability("gain 6 credits and add an installed resource to the top of the stack", $(ClickCost(1), CounterCost("power", 5)), $(gain(Corp, 6), ChooseCardEffect(Corp, g.runnerInstalledOf("Resource"), TopOfDeckPurpose(Runner), "Working Prototype: choose a resource", false)), click = true, ok = g.isRezzed(self), reason = "not rezzed"))
    }

    object LycianMultiMunition extends NC("34100") {
        val kinds = $("Barrier", "Code Gate", "Sentry")
        override def onRez(self : Card)(implicit g : Game) = $(LycianAction(self, $))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnEnds(_) if g.isRezzed(self) => $(DerezEffect(self))
            case Derezzed(c) if c == self => $(LycianClearAction(self))
            case _ => $
        }
        override def subroutines(self : Card)(implicit g : Game) = $(
            Sub("(Code Gate) The Runner loses [click] and 1[c]", LycianSubAction(self, 0)),
            Sub("(Sentry) Trash 1 program", LycianSubAction(self, 1)),
            Sub("(Barrier) Gain 1[c] and end the run", LycianSubAction(self, 2)))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case LycianAction(self, chosen) =>
                val rest = kinds.%!(chosen.has)
                if (rest.any)
                    g.push(ChooseOneEffect(Corp, "Lycian Multi-Munition: choose a subtype to gain", rest./(k => Alt(k, $(LycianAddAction(self, k, chosen)))) ++ chosen.any.$(Alt("Done", $))))
                NextAction
            case LycianAddAction(self, k, chosen) =>
                g.state(self).extraSubtypes = (g.state(self).extraSubtypes :+ k).distinct
                g.log(self, "gains", k.hl)
                g.push(LycianAction(self, chosen :+ k))
                NextAction
            case LycianClearAction(self) =>
                g.state(self).extraSubtypes = $
                NextAction
            case LycianSubAction(self, i) =>
                i match {
                    case 0 if g.has(self, "Code Gate") => g.push(LoseCreditsEffect(Runner, 1), LoseClicksEffect(Runner, 1))
                    case 1 if g.has(self, "Sentry") => g.push(RunnerTrashInstalledEffect("Program", Corp, "Lycian Multi-Munition: choose a program to trash"))
                    case 2 if g.has(self, "Barrier") => g.push(gain(Corp, 1), EndRunEffect)
                    case _ => g.log("No effect (missing subtype)")
                }
                NextAction
        }
    }

    object SorocabanBlade extends NC("34101") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Trashed(c, Corp, _) if encounteringThis(self) && g.owner(c).isRunner => $(SetMemoEffect(self, "trashed"))
            case EncounterEnds(c) if c == self => $(SetMemoEffect(self, ""))
            case _ => $
        }
        override def subroutines(self : Card)(implicit g : Game) = $(
            Sub("Trash 1 installed resource", SorocabanSubAction(self, "Resource")),
            Sub("Trash 1 installed piece of hardware", SorocabanSubAction(self, "Hardware")),
            Sub("Trash 1 installed program", SorocabanSubAction(self, "Program")))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case SorocabanSubAction(self, kind) =>
                if (g.state(self).memo == "trashed")
                    g.log(self, "cannot trash more than 1 card this encounter")
                else
                    g.push(RunnerTrashInstalledEffect(kind, Corp, "Sorocaban Blade: choose a " + kind.toLowerCase + " to trash"))
                NextAction
        }
    }

    object ActivePolicing extends NC("34102") {
        override def canPlay(self : Card)(implicit g : Game) = g.regLast("runner-trash") > 0 || g.regLast("stolen-points") > 0
        override def onPlay(self : Card)(implicit g : Game) = {
            val l = g.hand(Corp).%(corpInstallable)
            l.any.$(ChooseCardEffect(Corp, l, InstallChooseServerPurpose(false), "Active Policing: install a card from HQ?", true)) ++
            $(ExtraClicksEffect(Runner, -1)) ++
            g.threat(3).$(ActivePolicingOfferAction(self)) :+
            EndActionPhaseEffect
        }
        // the payment is offered when it resolves (after the install), through the credit engine
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case ActivePolicingOfferAction(self) =>
                if (g.canSpend(Corp, 2, Spend.effect(|(self))))
                    g.push(OptionalEffect(Corp, "Active Policing: pay 2 credits to give the Runner another -1 allotted click?", $(SpendEffect(Corp, 2, Spend.effect(|(self)), 0, $(ExtraClicksEffect(Runner, -1)))), $))
                NextAction
        }
    }

    object CorporateHospitality extends NC("34103") {
        override def canPlay(self : Card)(implicit g : Game) = g.fnsDoubleCanPlay(Corp)
        override def onPlay(self : Card)(implicit g : Game) =
            g.fnsDoubleCost(Corp) ++ $(gain(Corp, 6), draw(Corp, 2)) ++ g.discard(Corp).any.$(ChooseCardEffect(Corp, g.discard(Corp), AddToHandPurpose(Corp, false), "Corporate Hospitality: add a card from Archives to HQ", true))
    }

    object BrasiliaGovernmentGrid extends NC("34104") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Rezzed(ice) if ice.data.isIce && g.isRezzed(self) && thisServer(self, g.serverOf(ice).|(HQ)) && g.run.exists(r => !r.ended && thisServer(self, r.server)) && !g.usedThisTurn(self, "bgg") && g.rezzedIce.but(ice).any =>
                $(OptionalEffect(Corp, "Brasilia Government Grid: derez another piece of ice to give the rezzed ice +3 strength for the run?", $(ChooseCardEffect(Corp, g.rezzedIce.but(ice), BrasiliaPurpose(self, ice), "Choose a piece of ice to derez", false)), $))
            case _ => $
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case BrasiliaPurpose(self, ice) =>
                cards.single.foreach(c => g.push(MarkUsedEffect(self, "bgg"), DerezEffect(c), PumpIceEffect(ice, 3, RunDuration)))
                NextAction
        }
    }

    object SeeHowTheyRun extends NC("34105") {
        override def onScore(self : Card)(implicit g : Game) = $(tag(1), PsiEffect($(net(1)), $(core(1))))
    }

    object SisyphusProtocol extends NC("34106") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case PassIce(ice) if g.isRezzed(ice) && (g.has(ice, "Code Gate") || g.has(ice, "Sentry")) && !g.usedThisTurn(self, "sisyphus") =>
                $(MarkUsedEffect(self, "sisyphus"), ChooseOneEffect(Corp, "Sisyphus Protocol: make the Runner encounter the ice again?", $(
                    Alt("Pay 1 credit", $(SpendEffect(Corp, 1, Spend.effect(|(self)), 0, $(ForceEncounterEffect(ice)))), g.canSpend(Corp, 1, Spend.effect(|(self)))),
                    Alt("Trash 1 card from HQ", $(CorpTrashFromHandEffect(1), ForceEncounterEffect(ice)), g.hand(Corp).any),
                    Alt("Done", $))))
            case _ => $
        }
    }

    object CharlotteCacador extends NC("34107") {
        override def advanceable(self : Card)(implicit g : Game) = true
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) if g.isRezzed(self) && g.advancement(self) > 0 =>
                $(OptionalEffect(Corp, "Charlotte Cacador: remove 1 advancement counter to gain 4 credits and draw 1 card?", $(AddCounterEffect(self, "advancement", -1), gain(Corp, 4), draw(Corp, 1)), $))
            case _ => $
        }
        override def abilities(self : Card)(implicit g : Game) = $(Ability("gain 3 credits", $(CounterCost("advancement", 1), TrashSelfCost), $(gain(Corp, 3)), ok = g.isRezzed(self), reason = "not rezzed"))
    }

    object CohortGuidanceProgram extends NC("34108") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) if g.isRezzed(self) =>
                val facedown = g.discard(Corp).%(c => !g.state(c).seen)
                $(ChooseOneEffect(Corp, "Cohort Guidance Program: choose one", $(
                    Alt("Trash 1 card from HQ to gain 2 credits and draw 1 card", $(CorpTrashFromHandEffect(1), gain(Corp, 2), draw(Corp, 1)), g.hand(Corp).any),
                    Alt("Turn 1 facedown card in Archives faceup to place 1 advancement counter", $(ChooseCardEffect(Corp, facedown, TurnFaceupPurpose, "Choose a facedown card in Archives", false), ChooseCardEffect(Corp, g.installedCards(Corp), AdvancePurpose(1), "Choose an installed card to advance", false)), facedown.any && g.installedCards(Corp).any),
                    Alt("Done", $))))
            case _ => $
        }
    }

    object Boto extends NC("34109") {
        override def strength(self : Card)(implicit g : Game) = g.threat(4).?(2).|(0)
        override def subroutines(self : Card)(implicit g : Game) = $(netSub(2), Sub("You may trash 1 card from HQ to end the run", BotoSubAction(self)), Sub("You may trash 1 card from HQ to end the run", BotoSubAction(self)))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case BotoSubAction(self) =>
                if (g.hand(Corp).any)
                    g.push(OptionalEffect(Corp, "Boto: trash 1 card from HQ to end the run?", $(CorpTrashFromHandEffect(1), EndRunEffect), $))
                NextAction
        }
    }

    object CloudEater extends NC("34110") {
        override def subroutines(self : Card)(implicit g : Game) = $(trashSub("", "Trash 1 installed Runner card"), tagSub(2), netSub(3))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case EncounterEnds(c) if c == self && g.state(self).rezzedTurn == g.turn && g.run.exists(!_.ended) => $(CloudEaterAction(self))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case CloudEaterAction(self) =>
                g.push(ChooseOneEffect(Runner, "Cloud Eater: choose one", $(
                    Alt("The Corp trashes 1 installed Runner card", $(RunnerTrashInstalledEffect("", Corp, "Cloud Eater: choose an installed Runner card to trash"))),
                    Alt("Take 2 tags", $(tag(2))),
                    Alt("Suffer 3 net damage", $(net(3)), g.hand(Runner).num >= 3))))
                NextAction
        }
    }

    object Tributary extends NC("34111") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case RunInitiated(s) if g.reg("runs") == 1 && g.isRezzed(self) && !thisServer(self, s) && !g.usedThisTurn(self, "tributary") =>
                $(OptionalEffect(Corp, "Tributary: move it to the outermost position of the attacked server?", $(MarkUsedEffect(self, "tributary"), MoveIceOutermostEffect(self, s)), $))
            case _ => $
        }
        override def subroutines(self : Card)(implicit g : Game) = $(
            Sub("You may draw 1 card. You may install a piece of ice from HQ protecting another server, ignoring all costs", CopyAbilityEffect($(OptionalEffect(Corp, "Tributary: draw 1 card?", $(draw(Corp, 1)), $), ChooseCardEffect(Corp, g.hand(Corp).%(_.data.isIce), TributaryPurpose(self), "Tributary: install a piece of ice from HQ?", true)))),
            Sub("Each piece of ice gets +2 strength for the remainder of the run", CopyAbilityEffect(g.allIce./(i => PumpIceEffect(i, 2, RunDuration)))))
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(c => g.push(ChooseServerPayingEffect(c, 99, g.servers.%(s => !thisServer(p.card, s)), true, false)))
            NextAction
        }
    }

    object BringThemHome extends NC("34112") {
        override def canPlay(self : Card)(implicit g : Game) = g.regLast("runner-trash") > 0 || g.regLast("stolen-points") > 0
        override def onPlay(self : Card)(implicit g : Game) =
            g.hand(Runner).any.$(RandomPickEffect(Corp, g.hand(Runner), 2, BringThemHomePurpose(self))) ++
            g.threat(3).$(BringThemHomeShuffleAction(self)) :+
            EndActionPhaseEffect
        // the optional payment and the random card are decided when that part resolves (after the first 2 cards moved)
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case BringThemHomeShuffleAction(self) =>
                if (g.canSpend(Corp, 2, Spend.effect(|(self))) && g.hand(Runner).any)
                    g.push(OptionalEffect(Corp, "Bring Them Home: pay 2 credits to shuffle a random card from the grip into the stack?", $(SpendEffect(Corp, 2, Spend.effect(|(self)), 0, $(RandomPickEffect(Corp, g.hand(Runner), 1, BringThemHomeShufflePurpose(self))))), $))
                NextAction
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case BringThemHomePurpose(_) =>
                g.pushAll(RevealCardsEffect(Corp, cards) +: cards./(c => MoveEffect(c, DeckZone(Runner), true)))
                g.log(cards.num.cards, "will be placed on top of the stack")
                NextAction
            case BringThemHomeShufflePurpose(_) =>
                g.pushAll(RevealCardsEffect(Corp, cards) +: cards./(c => MoveEffect(c, DeckZone(Runner), false)) :+ ShuffleDeckEffect(Runner))
                NextAction
        }
    }

    object Kingmaking extends NC("34113") {
        override def onScore(self : Card)(implicit g : Game) = $(ChooseOneEffect(Corp, "Kingmaking: draw up to 3 cards", 0.to(3.atMost(g.deck(Corp).num)).$./(n => Alt("Draw " + n, $(draw(Corp, n))))), KingmakingAction(self))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case KingmakingAction(self) =>
                val l = g.hand(Corp).%(c => c.data.isAgenda && c.data.agendaPoints.|(0) <= 1)
                if (l.any)
                    g.push(ChooseCardEffect(Corp, l, AddToScorePurpose(Corp), "Kingmaking: add an agenda worth 1 or less points to the score area?", true))
                NextAction
        }
    }

    object StokeTheEmbers extends NC("34114") {
        def place(implicit g : Game) = g.installedCards(Corp).any.$(ChooseCardEffect(Corp, g.installedCards(Corp), AdvancePurpose(1), "Stoke the Embers: place 1 advancement counter on an installed card", false))
        override def onScore(self : Card)(implicit g : Game) = gain(Corp, 3) +: place
        override def onInstall(self : Card)(implicit g : Game) =
            (g.lastInstallFrom != HandZone(Corp)).$(OptionalEffect(Corp, "Stoke the Embers: reveal it to gain 2 credits and place 1 advancement counter?", $(RevealCardsEffect(Corp, $(self)), gain(Corp, 2)) ++ place, $))
    }

    object JK extends NC("34115") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) if g.isRezzed(self) => $(AddCounterEffect(self, "credit", 3))
            case _ => $
        }
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("take all hosted credits (" + g.counter(self, "credit") + "), add it to HQ and install 1 card from HQ", click(), $(TakeCreditsEffect(Corp, self, 99), ReturnToHandEffect(self), JKInstallAction(self)), click = true, ok = g.isRezzed(self), reason = "not rezzed"))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case JKInstallAction(self) =>
                val l = g.hand(Corp).%(corpInstallable)
                if (l.any)
                    g.push(ChooseCardEffect(Corp, l, InstallChooseServerPurpose(false), "JK: install a card from HQ?", true))
                NextAction
        }
    }

    object Capacitor extends NC("34116") {
        override def strength(self : Card)(implicit g : Game) = g.tagged.?(2).|(0)
        override def subroutines(self : Card)(implicit g : Game) = $(Sub("Gain 1[c] for each tag the Runner has", gain(Corp, g.tags)), etrSub)
    }

    object Piranhas extends NC("34117") {
        override def onRez(self : Card)(implicit g : Game) = $(ChooseOneEffect(Corp, "Piranhas: additional rez cost", $(Alt("Take 1 bad publicity", $(BadPubEffect(1))), Alt("Remove 1 tag", $(RemoveTagsEffect(1)), g.tagged))))
        override def subroutines(self : Card)(implicit g : Game) = $(Sub("You may draw 1 card", OptionalEffect(Corp, "Piranhas: draw 1 card?", $(draw(Corp, 1)), $)), netSub(1), Sub("End the run if there are more cards in HQ than in the grip", PiranhasSubAction(self)))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case PiranhasSubAction(self) =>
                if (g.hand(Corp).num > g.hand(Runner).num)
                    g.push(EndRunEffect)
                NextAction
        }
    }

    object Seraph extends NC("34118") {
        override def onEncounter(self : Card)(implicit g : Game) = $(ChooseOneEffect(Runner, "Seraph: choose one", $(
            Alt("Lose 3 credits", $(LoseCreditsEffect(Runner, 3))),
            Alt("Suffer 2 net damage", $(net(2)), g.hand(Runner).num >= 2),
            Alt("Take 1 tag", $(tag(1))))))
        override def subroutines(self : Card)(implicit g : Game) = $(loseSub(3), netSub(2), tagSub(1))
    }

    object SuddenCommandment extends NC("34119") {
        override def onPlay(self : Card)(implicit g : Game) = {
            val first = g.reg("mandate") == 0
            $(RegisterEffect("mandate", 1), draw(Corp, 2), ChooseCardEffect(Corp, g.hand(Corp).%(c => c.data.isOperation && !g.has(c, "Terminal") && g.canSpend(Corp, g.playCost(c), Spend.play(c)) && g.impl(c).canPlay(c)), SuddenCommandmentPurpose(self), "Sudden Commandment: play a non-terminal operation from HQ?", true), SuddenCommandmentThreatAction(self, first))
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(c => g.push(PlayFreeEffect(c)))
            NextAction
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case SuddenCommandmentThreatAction(self, first) =>
                if (first && g.threat(3) && g.canSpend(Corp, 3, Spend.effect(|(self))))
                    g.push(OptionalEffect(Corp, "Sudden Commandment: pay 3 credits to gain a click?", $(SpendEffect(Corp, 3, Spend.effect(|(self)), 0, $(GainClicksEffect(Corp, 1)))), $))
                NextAction
        }
    }

    object TheHoloMan extends NC("34120") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) if g.isRezzed(self) => $(HoloManMoveAction(self))
            case Installed(Corp, c) if g.lastInstallFrom == HandZone(Corp) => $(RegisterEffect("install-from-hq", 1))
            case _ => $
        }
        override def abilities(self : Card)(implicit g : Game) = {
            val n = (g.reg("install-from-hq") == 0).?(3).|(2)
            val l = g.serverOf(self)./(s => g.ice(s) ++ g.root(s)).|($)
            $(Ability("place " + n + " advancement counters on a card in or protecting this server", $(ClickCost(1), CreditCost(4)), $(ChooseCardEffect(Corp, l, AdvancePurpose(n), "The Holo Man: choose a card", false)), click = true, once = once("holo"), ok = g.isRezzed(self) && l.any, reason = "not rezzed"))
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case HoloManMoveAction(self) =>
                val l = g.servers.%(s => !thisServer(self, s))
                g.push(ChooseOneEffect(Corp, "The Holo Man: move to the root of another server?", l./(s => Alt(s.name, $(MoveToRootEffect(self, s)))) :+ Alt("Stay", $)))
                NextAction
        }
    }

    object NuvemSA extends NC("34121") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case PlayResolved(Corp, _) if g.deck(Corp).any => $(NuvemLookAction(self))
            case ExpendResolved(_) if g.deck(Corp).any => $(NuvemLookAction(self))
            case DeckTrashed(Corp, _) if g.active.isCorp && g.reg("trashed-from-deck-Corp") > 0 && !g.usedThisTurn(self, "nuvem") => $(MarkUsedEffect(self, "nuvem"), gain(Corp, 2))
            // any other card the Corp trashes from R&D during its turn
            case Trashed(c, Corp, _) if g.active.isCorp && g.fnsTrashedFrom(c).has(DeckZone(Corp)) && !g.usedThisTurn(self, "nuvem") => $(MarkUsedEffect(self, "nuvem"), gain(Corp, 2))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case NuvemLookAction(self) =>
                g.deck(Corp).starting.foreach(c => g.push(LookAtEffect(Corp, $(c), "Nuvem SA: the top card of R&D"), OptionalEffect(Corp, "Nuvem SA: trash the top card of R&D?", $(TrashTopEffect(Corp, 1, false)), $)))
                NextAction
        }
    }

    object EminentDomain extends NC("34122") {
        override def activeInHand(self : Card)(implicit g : Game) = true
        override def abilities(self : Card)(implicit g : Game) = (g.zone(self) == HandZone(Corp)).$(
            Ability("reveal and trash it from HQ: install and rez 1 card from HQ paying 5 credits less", $(ClickCost(1), CreditCost(1)),
                $(RevealCardsEffect(Corp, $(self)), TrashEffect(self, Corp, false), ChooseCardEffect(Corp, g.hand(Corp).%(c => corpInstallable(c) && c != self), InstallPayingPurpose(5, None, true), "Eminent Domain: choose a card to install and rez", true), ExpendTriggerEffect(self)),
                click = true, ok = g.hand(Corp).%(c => corpInstallable(c) && c != self).any, reason = "nothing to install"))
        override def onScore(self : Card)(implicit g : Game) = {
            val l = g.deck(Corp).%(corpInstallable).sortBy(_.title)
            l.any.$(OptionalEffect(Corp, "Eminent Domain: search R&D for a card to install and rez ignoring all costs?", $(ChooseCardEffect(Corp, l, InstallPayingPurpose(99, None, true), "Choose a card to install and rez", true), ShuffleDeckEffect(Corp)), $))
        }
    }

    object TheBasaltSpire extends NC("34123") {
        def recur(implicit g : Game) = g.discard(Corp).any.$(ChooseCardEffect(Corp, g.discard(Corp), AddToHandPurpose(Corp, false), "The Basalt Spire: add a card from Archives to HQ", true))
        override def onScore(self : Card)(implicit g : Game) = $(AddCounterEffect(self, "agenda", 2))
        override def onSteal(self : Card)(implicit g : Game) = recur
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("trash the top card of R&D: add 1 card from Archives to HQ", $(CounterCost("agenda", 1)), TrashTopEffect(Corp, 1, false) +: recur, once = once("basalt"), ok = g.zone(self) == ScoredZone(Corp) && g.deck(Corp).any, reason = "needs cards in R&D"))
    }

    object HeartsAndMinds extends NC("34124") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) if g.isRezzed(self) =>
                val from = g.installedCards(Corp).%(c => g.advancement(c) > 0)
                from.any.$(ChooseCardEffect(Corp, from, HeartsAndMindsFromPurpose(self), "Hearts and Minds: move 1 advancement counter from a card?", true)) :+ HeartsAndMindsPoliticalAction(self)
            case _ => $
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case HeartsAndMindsFromPurpose(self) =>
                cards.single.foreach { from =>
                    val to = g.installedCards(Corp).%(c => c != from && g.isAdvanceable(c))
                    if (to.any)
                        g.push(ChooseCardEffect(Corp, to, HeartsAndMindsToPurpose(self, from), "Hearts and Minds: choose a card to move the counter to", true))
                }
                NextAction
            case HeartsAndMindsToPurpose(self, from) =>
                cards.single.foreach(to => g.push(AddCounterEffect(from, "advancement", -1), PlaceAdvancementEffect(to, 1)))
                NextAction
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case HeartsAndMindsPoliticalAction(self) =>
                val l = g.installedCards(Corp).%(g.isAdvanceable)
                if (g.isRezzed(self) && g.serverOf(self).exists(g.unprotected) && l.any)
                    g.push(ChooseCardEffect(Corp, l, AdvancePurpose(1), "Hearts and Minds: place 1 advancement counter on a card you can advance?", true))
                NextAction
        }
    }

    object Descent extends NC("34125") {
        override def activeInHand(self : Card)(implicit g : Game) = true
        override def abilities(self : Card)(implicit g : Game) = (g.zone(self) == HandZone(Corp)).$(
            Ability("reveal and trash it from HQ: draw 1 card and shuffle up to 2 agendas from HQ/Archives into R&D", $(ClickCost(1), CreditCost(1)),
                $(RevealCardsEffect(Corp, $(self)), TrashEffect(self, Corp, false), draw(Corp, 1), ChooseCardsEffect(Corp, (g.hand(Corp) ++ g.discard(Corp)).%(c => c.data.isAgenda && c != self), 0, 2, ShuffleIntoDeckPurpose(Corp), "Descent: choose up to 2 agendas to shuffle into R&D"), ExpendTriggerEffect(self)), click = true))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) if g.isRezzed(self) => $(OptionalEffect(Corp, "Descent: add it to HQ?", $(ReturnToHandEffect(self)), $))
            case _ => $
        }
        override def subroutines(self : Card)(implicit g : Game) = $(etrSub)
    }

    object Hammer extends NC("34126") {
        override def breakAllowed(self : Card, breaker : Card, ice : Card)(implicit g : Game) = ice != self || g.has(breaker, "Killer") || g.state(self).brokenSubs.none
        override def subroutines(self : Card)(implicit g : Game) = {
            val rh = g.runnerInstalled.%(c => c.data.isResource || c.data.isHardware)
            val progs = g.runnerInstalled.%(c => c.data.isProgram && !g.has(c, "Decoder") && !g.has(c, "Fracter") && !g.has(c, "Killer"))
            $(tagSub(1),
                Sub("Trash 1 installed resource or piece of hardware", ChooseCardEffect(Corp, rh, TrashPurpose(Corp), "Hammer: choose a resource or piece of hardware to trash", false)),
                Sub("Trash 1 installed program that is not a decoder, fracter or killer", ChooseCardEffect(Corp, progs, TrashPurpose(Corp), "Hammer: choose a program to trash", false)))
        }
    }

    object Logjam extends NC("34127") {
        override def advanceable(self : Card)(implicit g : Game) = true
        override def strength(self : Card)(implicit g : Game) = g.advancement(self)
        override def onRez(self : Card)(implicit g : Game) = $(PlaceAdvancementEffect(self, 1 + g.discard(Corp).%(c => g.state(c).seen)./(_.data.kind).distinct.num))
        override def subroutines(self : Card)(implicit g : Game) = $(Sub("Gain 2[c]. End the run", CopyAbilityEffect($(gain(Corp, 2), EndRunEffect))), etrSub, etrSub)
    }

    object BusinessAsUsual extends NC("34128") {
        override def onPlay(self : Card)(implicit g : Game) = $(BusinessAsUsualAction(self, $))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case BusinessAsUsualAction(self, done) =>
                val again = (g.threat(3) && done.none)
                val adv = g.installedCards(Corp).%(g.isAdvanceable)
                val virus = g.runnerInstalled.%(c => g.counter(c, "virus") > 0)
                val l =
                    (!done.has("adv")).$(Alt("Place 1 advancement counter on up to 2 cards you can advance", $(ChooseCardsEffect(Corp, adv, 0, 2, AdvancePurpose(1), "Business As Usual: choose up to 2 cards")) ++ again.$(BusinessAsUsualAction(self, done :+ "adv")), adv.any)) ++
                    (!done.has("virus")).$(Alt("Remove all virus counters from 1 installed card", $(ChooseCardEffect(Corp, virus, RemoveCountersPurpose("virus"), "Business As Usual: choose a card", false)) ++ again.$(BusinessAsUsualAction(self, done :+ "virus")), virus.any))
                g.push(ChooseOneEffect(Corp, "Business As Usual: choose one", l ++ done.any.$(Alt("Done", $))))
                NextAction
        }
    }

    object IsaacLiberdade extends NC("34129") {
        override def iceStrengthMod(self : Card, ice : Card)(implicit g : Game) = (g.isRezzed(self) && g.serverOf(ice).exists(s => thisServer(self, s)) && g.advancement(ice) > 0).?(2).|(0)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnEnds(Corp) if g.isRezzed(self) && g.servers.%(s => !thisServer(self, s)).any =>
                $(ChooseOneEffect(Corp, "Isaac Liberdade: move to the root of another server?", g.servers.%(s => !thisServer(self, s))./(s => Alt(s.name, $(MoveToRootEffect(self, s), IsaacMovedAction(self)))) :+ Alt("Stay", $)))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case IsaacMovedAction(self) =>
                val l = g.serverOf(self)./(g.ice).|($).%(i => g.advancement(i) == 0)
                if (l.any)
                    g.push(ChooseCardEffect(Corp, l, AdvancePurpose(1), "Isaac Liberdade: place 1 advancement counter on a piece of ice protecting this server?", true))
                NextAction
        }
    }

    object ThePowersThatBe extends NC("34130") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case AgendaScored(_) if g.isRezzed(self) =>
                val l = (g.hand(Corp) ++ g.discard(Corp)).%(corpInstallable)
                l.any.$(ChooseCardEffect(Corp, l, InstallPayingPurpose(99, None, false), "The Powers That Be: install a card from HQ or Archives ignoring all costs?", true))
            case _ => $
        }
    }

    val all : $[CardImpl] = $(
        Sebastiao, EyeForAnEye, PrivilegedAccess, Amanuensis, WizardsChest, BoiTata, Heliamphora, ArruaceirasCrew, FriendOfAFriend,
        ManuelLattesDeMoura, MeetingOfMinds, WindowOfOpportunity, AlarmClock, Jeitinho, Cupellation, Malandragem, PhysarumEntangler,
        AmeliaEarhart, JuliMoreiraLee, Burner, Spree, TrickShot, Cataloguer, Coalescence, Lobisomem, Muse, PressureSpike, PrettyMary,
        AshenEpilogue, Valentina,
        ThunderboltArmaments, LightningLaboratory, WarmReception, WorkingPrototype, LycianMultiMunition, SorocabanBlade, ActivePolicing,
        CorporateHospitality, BrasiliaGovernmentGrid, SeeHowTheyRun, SisyphusProtocol, CharlotteCacador, CohortGuidanceProgram, Boto,
        CloudEater, Tributary, BringThemHome, Kingmaking, StokeTheEmbers, JK, Capacitor, Piranhas, Seraph, SuddenCommandment, TheHoloMan,
        NuvemSA, EminentDomain, TheBasaltSpire, HeartsAndMinds, Descent, Hammer, Logjam, BusinessAsUsual, IsaacLiberdade, ThePowersThatBe,
    )
}
