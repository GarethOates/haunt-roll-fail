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
// Rotated / FFG-era Resource cards (part 2: M - Z), ported from jinteki.net src/clj/game/cards/resources.clj
// ---------------------------------------------------------------------------------------------------------------------

// card specific actions and purposes
case class MrLiAction(card : Card) extends CardAction
case class MrLiChooseAction(card : Card, before : $[Card]) extends CardAction
case class MuertosPurpose(card : Card) extends CardPurpose
case class OracleMayAction(card : Card, kind : String) extends CardAction
case class PaigePiperAction(card : Card, code : String, n : Int) extends CardAction
case class WorkshopHostPurpose(card : Card) extends CardPurpose
case class WorkshopRemovePurpose(card : Card) extends CardPurpose
case class WorkshopCheckAction(card : Card, target : Card) extends CardAction
case class PsychMikeAction(card : Card) extends CardAction
case class ReclaimAction(card : Card) extends CardAction
case class RosettaRfgPurpose(card : Card) extends CardPurpose
case class RosettaSearchAction(card : Card, discount : Int) extends CardAction
case class SacrificialCloneAction(card : Card) extends CardAction
case class VanadisAction(card : Card) extends CardAction
case class SlipstreamAction(card : Card) extends CardAction
case class SlipstreamPurpose(card : Card) extends CardPurpose
case class StreetMagicAction(card : Card, ice : Card, remaining : $[Int]) extends CardAction
case class StreetMagicFireAction(card : Card, ice : Card, idx : Int, remaining : $[Int]) extends CardAction
case class StreetPeddlerHostAction(card : Card) extends CardAction
case class StreetPeddlerAction(card : Card) extends CardAction
case class StreetPeddlerPurpose(card : Card) extends CardPurpose
case class StreetPeddlerCleanupAction(card : Card, cards : $[Card]) extends CardAction
case class MasqueBAction(card : Card) extends CardAction
case class BlackFileAction(card : Card) extends CardAction
case class StimDealerAction(card : Card) extends CardAction
case class NoOneHomeAction(card : Card, kind : String, n : Int) extends CardAction
case class NeutralizeAction(card : Card, target : Card) extends CardAction
case class NeutralizeTrashAction(card : Card, target : Card) extends CardAction


object RotResourceCards2 {

    object MaxwellJames extends RR("13011") {
        override def link(self : Card)(implicit g : Game) = 1
        override def abilities(self : Card)(implicit g : Game) = {
            val l = g.remotes./~(s => g.ice(s)).%(g.isRezzed)
            $(Ability("derez a piece of ice protecting a remote server", $(TrashSelfCost), $(ChooseCardEffect(Runner, l, DerezPurpose, "Maxwell James: choose a piece of ice to derez", false)), ok = l.any && g.successfulRunsThisTurn.has(HQ), reason = "needs a successful run on HQ this turn and rezzed remote ice"))
        }
    }

    object MissBones extends RR("22014") {
        override def initialCounters(self : Card)(implicit g : Game) = Map("credit" -> 12)
        // "Use these credits to trash installed cards." (trash costs of installed Corp cards)
        override def paysFor(self : Card, spend : Spend)(implicit g : Game) = spend.trashing.exists(c => g.owner(c).isCorp && g.isInstalled(c))
        override def triggers(self : Card, e : Event)(implicit g : Game) = trashOnEmpty(self, e, "credit")
    }

    object Motivation extends RR("04008") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) if g.deck(Runner).any => $(OptionalEffect(Runner, "Motivation: look at the top card of the stack?", $(MessageEffect(Runner, "uses Motivation to look at the top card of the stack"), LookAtEffect(Runner, g.deck(Runner).take(1), "Motivation: the top card of the stack")), $))
            case _ => $
        }
    }

    object MrLi extends RR("20036") {
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("draw 2 cards, add 1 of them to the bottom of the stack", click(), $(MrLiAction(self)), click = true, ok = g.deck(Runner).any, reason = "empty stack"))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case MrLiAction(self) =>
                g.push(draw(Runner, 2), MrLiChooseAction(self, g.hand(Runner)))
                NextAction
            case MrLiChooseAction(self, before) =>
                val drawn = g.hand(Runner).%!(before.has)
                if (drawn.any)
                    g.push(ChooseCardEffect(Runner, drawn, BottomOfDeckPurpose(Runner), "Mr. Li: choose a drawn card to add to the bottom of the stack", false))
                NextAction
        }
    }

    object MuertosGangMember extends RR("08068") {
        override def onInstall(self : Card)(implicit g : Game) = {
            val l = g.installedCards(Corp).%(c => g.isRezzed(c) && !c.data.isAgenda)
            l.any.$(ChooseCardEffect(Corp, l, DerezPurpose, "Muertos Gang Member: choose a card to derez", false))
        }
        override def onTrash(self : Card, by : Faction)(implicit g : Game) = {
            val l = g.installedCards(Corp).%(c => !g.isRezzed(c) && !c.data.isAgenda)
            l.any.$(ChooseCardEffect(Corp, l, RezPurpose(true), "Muertos Gang Member: rez a card ignoring the rez cost?", true))
        }
        override def abilities(self : Card)(implicit g : Game) = $(Ability("draw 1 card", $(TrashSelfCost), $(draw(Runner, 1))))
    }

    object NetMercur extends RR("11046") {
        // "You can spend hosted credits for anything."
        override def paysFor(self : Card, spend : Spend)(implicit g : Game) = true
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case CreditsSpentFrom(c, _) if g.has(c, "Stealth") && activeRun && !g.state(self).usedRun.has("mercur") =>
                $(MarkUsedRunEffect(self, "mercur"), ChooseOneEffect(Runner, "Net Mercur: choose one", $(
                    Alt("Place 1 credit on Net Mercur", $(AddCounterEffect(self, "credit", 1))),
                    Alt("Draw 1 card", $(draw(Runner, 1))))))
            case _ => $
        }
    }

    object NetworkExchange extends RR("12007") {
        // "The install cost of each piece of ice that is not installed in the innermost position is increased by 1."
        override def installCostModIn(self : Card, card : Card, server : |[Server])(implicit g : Game) = (card.data.isIce && server.exists(s => g.ice(s).any)).?(1).|(0)
    }

    object NeutralizeAllThreats extends RR("09043") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case BreachServer(HQ) => $(AccessBonusEffect(HQ, 1))
            case Accessed(c) if g.trashCost(c).any && !g.zone(c).is[DiscardZone] && unused(self, "nat") => $(mark(self, "nat"), NeutralizeAction(self, c))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case NeutralizeAction(self, c) =>
                // the accessed card is revealed and must be trashed by paying its trash cost, if able (before the access decision)
                g.push(RevealCardsEffect(Corp, $(c)), NeutralizeTrashAction(self, c))
                NextAction
            case NeutralizeTrashAction(self, c) =>
                val tc = g.trashCost(c).|(0)
                if (g.run.exists(r => !r.ended && !r.cannotStealOrTrash) && !g.zone(c).is[DiscardZone] && g.impl(c).canBeTrashedOnAccess(c) && !g.accessProtected(c) && g.canSpend(Runner, tc, Spend.trash(c))) {
                    g.spendCredits(Runner, tc, Spend.trash(c))
                    g.log(Runner, "must pay", tc.credits, "to trash", c, "(Neutralize All Threats)")
                    g.trashCard(c, Runner, true)
                }
                NextAction
        }
    }

    object NewAngelesCityHall extends RR("02109") {
        override def preventions(self : Card, kind : String, n : Int)(implicit g : Game) =
            (kind == "tag").$(Ability("avoid 1 tag", c(2), $(Prevent.tags(1))))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case AgendaStolen(_) if g.isInstalled(self) => $(MessageEffect(Runner, "trashes New Angeles City Hall"), TrashEffect(self, Runner, false))
            case _ => $
        }
    }

    object NoOneHome extends RR("21045") {
        override def preventions(self : Card, kind : String, n : Int)(implicit g : Game) =
            ((kind == "tag" && g.reg("prevent-window-tag") == 1 && g.reg("prevent-window-net") == 0) || (kind == "net" && g.reg("prevent-window-net") == 1 && g.reg("prevent-window-tag") == 0)).$(
                Ability("force the Corp to trace[0]; if unsuccessful, " + (kind == "tag").?("avoid all tags").|("prevent all net damage"), $(TrashSelfCost), $(NoOneHomeAction(self, kind, n))))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case NoOneHomeAction(self, kind, n) =>
                g.push(TraceEffect(0, $, $(MessageEffect(Runner, (kind == "tag").?("avoids all tags").|("prevents all net damage") + " (No One Home)"), (kind == "tag").?(Prevent.allTags).|(Prevent.allDamage))))
                NextAction
        }
    }

    object OffCampusApartment extends RR("08022") {
        override def canHost(self : Card, card : Card)(implicit g : Game) = card.data.isResource && card.data.has("Connection")
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Installed(Runner, c) if g.state(c).host.has(self) => $(MessageEffect(Runner, "draws 1 card (Off-Campus Apartment)"), draw(Runner, 1))
            case _ => $
        }
    }

    object OfficerFrank extends RR("13024") {
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("the Corp trashes 2 random cards from HQ", $(CreditCost(1), TrashSelfCost), $(RandomTrashFromHandEffect(Corp, 2)), ok = g.reg("meat-damage") > 0 && g.hand(Corp).any, reason = "no meat damage suffered this turn"))
    }

    object OracleMay extends RR("05054") {
        val kinds = $("Event", "Hardware", "Program", "Resource")
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("name a card type and reveal the top card of the stack", click(), $(ChooseOneEffect(Runner, "Oracle May: name a card type", kinds./(k => Alt(k, $(OracleMayAction(self, k)))))), click = true, once = once("oracle"), ok = g.deck(Runner).any, reason = "empty stack"))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case OracleMayAction(self, k) =>
                g.deck(Runner).starting.foreach { c =>
                    if (c.data.kind == k)
                        g.push(RevealCardsEffect(Runner, $(c)), MessageEffect(Runner, "gains 2 credits and draws the revealed card (Oracle May)"), gain(Runner, 2), draw(Runner, 1))
                    else
                        g.push(RevealCardsEffect(Runner, $(c)), MessageEffect(Runner, "trashes the revealed card (Oracle May)"), MillEffect(Runner, 1))
                }
                NextAction
        }
    }

    object OrderOfSol extends RR("06058") {
        override def triggers(self : Card, e : Event)(implicit g : Game) =
            (creditsCheckpoint(e) && g.credits(Runner) == 0 && unused(self, "sol")).$(mark(self, "sol"), MessageEffect(Runner, "gains 1 credit (Order of Sol)"), gain(Runner, 1))
    }

    object PADTap extends RR("21106") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case CreditsGained(Corp, _, _) if unused(self, "padtap") => $(mark(self, "padtap"), OptionalEffect(Runner, "PAD Tap: gain 1 credit?", $(MessageEffect(Runner, "gains 1 credit (PAD Tap)"), gain(Runner, 1)), $))
            case _ => $
        }
        override def corpAbilities(self : Card)(implicit g : Game) =
            $(Ability("trash PAD Tap", $(ClickCost(1), CreditCost(3)), $(TrashEffect(self, Corp, false)), click = true))
    }

    object PaigePiper extends RR("08002") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Installed(Runner, c) if g.reg("installed") == 1 && g.deck(Runner).exists(_.title == c.title) =>
                val n = g.deck(Runner).count(_.title == c.title)
                $(OptionalEffect(Runner, "Paige Piper: search the stack for copies of " + g.promptTitle(c) + " to add to the heap?", $(ChooseNumberEffect(Runner, self, 0, n, "piper:" + c.code, "Paige Piper: how many copies?")), $))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case NumberPickedAction(self, n, key) if key.startsWith("piper:") =>
                val code = key.drop(6)
                val l = g.deck(Runner).%(_.code == code).take(n)
                l.foreach(c => g.moveTo(c, DiscardZone(Runner)))
                if (l.any)
                    g.log(Runner, "adds", l.num.hl, "copies of", Cards(code).title.hl, "from the stack to the heap")
                g.push(ShuffleDeckEffect(Runner))
                NextAction
        }
    }

    object Paparazzi extends RR("08087") {
        // "you are tagged": Game.tagged (FixRules.fxConsideredTagged); all meat damage is prevented (forced interrupt)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case PreventionInterrupt("meat", 1, n) => $(MessageEffect(Runner, "prevents " + n + " meat damage (Paparazzi)"), Prevent.allDamage)
            case _ => $
        }
    }

    object Patron extends RR("10063") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) =>
                $(ChooseOneEffect(Runner, "Patron: choose a server", g.servers./(s => Alt(s.name, $(SetMemoEffect(self, s.name)))) :+ Alt("No server", $(SetMemoEffect(self, "")))))
            case SuccessfulRun(s) if g.state(self).memo == s.name && firstSuccessfulRunOn(s) && g.run.exists(r => !r.ended && !r.preventAccess) =>
                $(MessageEffect(Runner, "draws 2 cards instead of breaching " + s.name + " (Patron)"), ReplaceBreachEffect, draw(Runner, 2))
            case TurnEnds(Runner) => $(SetMemoEffect(self, ""))
            case _ => $
        }
    }

    object PersonalWorkshop extends RR("02049") {
        override def abilities(self : Card)(implicit g : Game) = {
            val l = g.hand(Runner).%(c => c.data.isProgram || c.data.isHardware)
            val h = g.hosted(self).%(c => g.counter(c, "power") > 0)
            $(Ability("host a program or piece of hardware from the grip", click(), $(ChooseCardEffect(Runner, l, WorkshopHostPurpose(self), "Personal Workshop: choose a card to host", false)), click = true, ok = l.any, reason = "nothing to host"),
              Ability("remove 1 power counter from a hosted card", c(1), $(ChooseCardEffect(Runner, h, WorkshopRemovePurpose(self), "Personal Workshop: choose a hosted card", false)), ok = h.any, reason = "no hosted card"))
        }
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) if g.hosted(self).exists(c => g.counter(c, "power") > 0) =>
                $(ChooseCardEffect(Runner, g.hosted(self).%(c => g.counter(c, "power") > 0), WorkshopRemovePurpose(self), "Personal Workshop: remove 1 power counter from a hosted card", false))
            case _ => $
        }
        override def chosenCard(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            p match {
                case WorkshopHostPurpose(self) =>
                    cards.single.foreach { c =>
                        val cost = c.data.cost.|(0)
                        if (cost <= 0)
                            g.pushAll(g.installRunner(c, 99))
                        else
                            g.push(HostUninstalledEffect(c, self, false), AddCounterEffect(c, "power", cost))
                    }
                case WorkshopRemovePurpose(self) =>
                    cards.single.foreach(c => g.push(AddCounterEffect(c, "power", -1), WorkshopCheckAction(self, c)))
                case _ =>
            }
            NextAction
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case WorkshopCheckAction(self, c) =>
                if (g.zone(c) == HostedZone(self) && g.counter(c, "power") == 0) {
                    g.moveTo(c, HandZone(Runner))
                    g.log(Runner, "installs", c, "from Personal Workshop ignoring all costs")
                    g.pushAll(g.installRunner(c, 99))
                }
                NextAction
        }
    }

    object PoliticalOperative extends RR("10043") {
        override def canInstall(self : Card)(implicit g : Game) = g.successfulRunsThisTurn.has(HQ)
        override def abilities(self : Card)(implicit g : Game) = {
            val l = g.installedCards(Corp).%(c => g.isRezzed(c) && g.trashCost(c).exists(_ <= g.credits(Runner)))
            $(Ability("pay the trash cost of a rezzed card to trash it", $(TrashSelfCost), $(ChooseCardEffect(Runner, l, RotTrashPayingPurpose(self), "Political Operative: choose a rezzed card to trash", false)), ok = l.any, reason = "no affordable rezzed card"))
        }
    }

    // "Gain 1 credit whenever a trace is initiated": a forced trigger of the trace-initiated timing (FinalNsgRules)
    object PowerTap extends RR("06016") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TraceInitiated(_) => $(MessageEffect(Runner, "uses Power Tap"), gain(Runner, 1))
            case _ => $
        }
    }

    object PsychMike extends RR("22021") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case RunEnds(RnD, true, _) if firstSuccessfulRunOn(RnD) => $(PsychMikeAction(self))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case PsychMikeAction(self) =>
                val n = g.reg("rd-accessed")
                if (n > 0)
                    g.push(OptionalEffect(Runner, "Psych Mike: gain " + n + " credits?", $(MessageEffect(Runner, "gains " + n + " credits (Psych Mike)"), gain(Runner, n)), $))
                NextAction
        }
    }

    object PublicSympathy extends RR("02050") {
        override def handSize(self : Card, f : Faction)(implicit g : Game) = f.isRunner.?(2).|(0)
    }

    object RachelBeckman extends RR("06060") {
        override def clicksPerTurn(self : Card, f : Faction)(implicit g : Game) = f.isRunner.?(1).|(0)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TagsGained(_) if g.isInstalled(self) => $(MessageEffect(Runner, "trashes Rachel Beckman (tagged)"), TrashEffect(self, Runner, false))
            case Installed(Runner, c) if c == self && g.tagged => $(MessageEffect(Runner, "trashes Rachel Beckman (tagged)"), TrashEffect(self, Runner, false))
            case _ => $
        }
    }

    object RaymondFlint extends RR("04049") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case BadPubGained(_) if g.isInstalled(self) => $(MessageEffect(Runner, "breaches HQ (Raymond Flint)"), BreachNoRootEffect(HQ))
            case _ => $
        }
        override def abilities(self : Card)(implicit g : Game) = {
            val l = g.installedCards(Corp).%(c => !g.isRezzed(c))
            $(Ability("expose 1 card", $(TrashSelfCost), $(ChooseCardEffect(Runner, l, ExposePurpose, "Raymond Flint: choose a card to expose", false)), ok = l.any, reason = "no unrezzed card"))
        }
    }

    object Reclaim extends RR("21107") {
        def eligible(c : Card)(implicit g : Game) = c.data.isProgram || c.data.isHardware || (c.data.isResource && g.has(c, "Virtual"))
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("install a program, piece of hardware or virtual resource from the heap", $(ClickCost(1), TrashSelfCost, TrashFromHandCost(1)), $(ReclaimAction(self)), click = true, ok = g.discard(Runner).exists(c => eligible(c) && g.runnerInstallableNow(c, 0)) && g.hand(Runner).any, reason = "nothing to install from the heap"))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case ReclaimAction(self) =>
                val l = g.discard(Runner).%(c => eligible(c) && g.runnerInstallableNow(c, 0))
                if (l.any)
                    g.push(ChooseCardEffect(Runner, l, RotInstallPayingPurpose(self, 0), "Reclaim: choose a card to install", false))
                NextAction
        }
    }

    object RogueTrading extends RR("21065") {
        override def initialCounters(self : Card)(implicit g : Game) = Map("credit" -> 18)
        override def triggers(self : Card, e : Event)(implicit g : Game) = trashOnEmpty(self, e, "credit")
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("take 6 credits and take 1 tag", $(ClickCost(2)), $(TakeCreditsEffect(Runner, self, 6), tag(1)), click = true))
    }

    object Rolodex extends RR("08084") {
        override def onInstall(self : Card)(implicit g : Game) = $(ArrangeTopEffect(Runner, Runner, 5))
        override def onTrash(self : Card, by : Faction)(implicit g : Game) = $(MessageEffect(Runner, "trashes the top 3 cards of the stack (Rolodex)"), MillEffect(Runner, 3))
    }

    object Rosetta extends RR("12045") {
        override def abilities(self : Card)(implicit g : Game) = {
            val l = g.installedCards(Runner).%(_.data.isProgram)
            $(Ability("remove an installed program from the game: install a non-virus program from the stack paying its cost less", click(), $(ChooseCardEffect(Runner, l, RosettaRfgPurpose(self), "Rosetta 2.0: choose a program to remove from the game", false)), click = true, ok = l.any && g.deck(Runner).any, reason = "no installed program"))
        }
        override def chosenCard(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(c => g.push(RemoveFromGameEffect(c), RosettaSearchAction(p.card, c.data.cost.|(0))))
            NextAction
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case RosettaSearchAction(self, d) =>
                val l = g.deck(Runner).%(c => c.data.isProgram && !g.has(c, "Virus") && g.runnerInstallableNow(c, d)).sortBy(_.title)
                g.push(ChooseCardEffect(Runner, l, RotInstallPayingPurpose(self, d), "Rosetta 2.0: choose a program to install", true), ShuffleDeckEffect(Runner))
                NextAction
        }
    }

    object SacrificialClone extends RR("07050") {
        override def preventions(self : Card, kind : String, n : Int)(implicit g : Game) =
            (kind == "net" || kind == "meat" || kind == "core").$(Ability("prevent all damage (trash hardware, non-virtual resources and the grip, lose all credits and tags)", $(TrashSelfCost), $(SacrificialCloneAction(self))))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case SacrificialCloneAction(self) =>
                val installed = g.runnerInstalledFaceup.%(c => c.data.isHardware || (c.data.isResource && !g.has(c, "Virtual")))
                val h = g.hand(Runner)
                h.foreach(c => g.moveTo(c, DiscardZone(Runner)))
                if (h.any)
                    g.log(Runner, "trashes", h.num.cards, "from the grip")
                g.push(TrashCardsEffect(installed, Runner), LoseAllCreditsEffect(Runner), RemoveTagsEffect(g.tags), Prevent.allDamage)
                NextAction
        }
    }

    object SacrificialConstruct extends RR("20054") {
        override def preventions(self : Card, kind : String, n : Int)(implicit g : Game) =
            (kind == "trash" && Prevent.ctx.exists(_.cards.exists(c => c.data.isProgram || c.data.isHardware))).$(Ability("prevent an installed program or piece of hardware from being trashed", $(TrashSelfCost), $(PreventTrashChooseEffect(self, $("Program", "Hardware")))))
    }

    object SafetyFirst extends RR("09044") {
        override def handSize(self : Card, f : Faction)(implicit g : Game) = f.isRunner.?(-2).|(0)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnEnds(Runner) if g.hand(Runner).num < g.handSize(Runner) => $(MessageEffect(Runner, "draws 1 card (Safety First)"), draw(Runner, 1))
            case _ => $
        }
    }

    object SalsetteSlums extends RR("10059") {
        override def accessAbility(self : Card, target : Card)(implicit g : Game) = {
            val tc = g.trashCost(target)
            (tc.any && !g.zone(target).is[DiscardZone] && unused(self, "slums") && g.run.exists(!_.cannotStealOrTrash)).?(
                Ability("pay the trash cost to remove the accessed card from the game", c(tc.|(0)), $(RemoveFromGameEffect(target)), once = once("slums"), ok = g.credits(Runner) >= tc.|(0), reason = "not enough credits"))
        }
    }

    object SalvagedVanadisArmory extends RR("12103") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case DamageDone(_, n) if n > 0 && g.isInstalled(self) && g.deck(Corp).any =>
                $(OptionalEffect(Runner, "Salvaged Vanadis Armory: trash it to force the Corp to trash the top cards of R&D?", $(VanadisAction(self)), $))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case VanadisAction(self) =>
                if (g.isInstalled(self)) {
                    g.log(Runner, "uses", self, "to force the Corp to trash the top", g.runnerDamageThisTurn.cards, "of R&D")
                    g.push(TrashEffect(self, Runner, false), MillEffect(Corp, g.runnerDamageThisTurn))
                }
                NextAction
        }
    }

    object SameOldThing extends RR("03054") {
        override def abilities(self : Card)(implicit g : Game) = {
            val l = g.discard(Runner).%(c => c.data.isEvent && g.canSpend(Runner, g.playCost(c), Spend.play(c)) && g.impl(c).canPlay(c))
            $(Ability("play an event from the heap paying its play cost", $(ClickCost(2), TrashSelfCost), $(ChooseCardEffect(Runner, l, RotPlayPurpose(self, true), "Same Old Thing: choose an event to play", false)), click = true, ok = l.any, reason = "no playable event in the heap"))
        }
    }

    object ShadowTeam extends RR("14022") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case RunInitiated(_) if g.hand(Runner).any => $(MessageEffect(Runner, "must trash a card from the grip (Shadow Team)"), ChooseCardEffect(Runner, g.hand(Runner), TrashFromHandPurpose(Runner), "Shadow Team: choose a card in the grip to trash", false))
            case SuccessfulRun(s) if s.isCentral && g.isInstalled(self) => $(MessageEffect(Runner, "destroys Shadow Team"), RemoveFromGameEffect(self))
            case _ => $
        }
    }

    object Slipstream extends RR("21085") {
        def candidates(implicit g : Game) : $[Card] = g.run.%(r => !r.ended).$./~(r => Server.centrals.%(_ != r.server)./~(s => g.ice(s).lift(r.position)))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case PassIce(ice) if g.isRezzed(ice) && candidates.any =>
                $(OptionalEffect(Runner, "Slipstream: trash it to approach a piece of ice protecting a central server in the same position?", $(SlipstreamAction(self)), $))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case SlipstreamAction(self) =>
                val l = candidates
                if (l.any)
                    g.push(ChooseCardEffect(Runner, l, SlipstreamPurpose(self), "Slipstream: choose a piece of ice to approach", false))
                NextAction
        }
        override def chosenCard(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach { t =>
                (g.run, g.serverOf(t)) match {
                    case (Some(r), Some(s)) if !r.ended && g.zone(t).is[IceZone] =>
                        g.trashCard(p.card, Runner, false)
                        r.server = s
                        r.position = g.ice(s).indexOf(t) + 1
                        r.ice = None
                        r.phase = ApproachIcePhase
                        if (!g.runsThisTurn.has(s))
                            g.runsThisTurn :+= s
                        g.flushRun()
                        g.windowPassed = $
                        g.log(Runner, "moves to", s, "to approach", t)
                        g.push(JackOutOfferEffect, RunNextAction)
                    case _ =>
                }
            }
            NextAction
        }
    }

    object Spoilers extends RR("08082") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case AgendaScored(_) => $(MessageEffect(Runner, "forces the Corp to trash the top card of R&D (Spoilers)"), MillEffect(Corp, 1))
            case _ => $
        }
    }

    object StarlightCrusadeFunding extends RR("04069") {
        // the additional costs of double events are not waived (not modelled)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) => $(MessageEffect(Runner, "loses [click] (Starlight Crusade Funding)"), ClickLossEffect(Runner, 1))
            case _ => $
        }
    }

    object StimDealer extends RR("07051") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) => $(StimDealerAction(self))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case StimDealerAction(self) =>
                if (g.counter(self, "power") >= 2)
                    g.push(MessageEffect(Runner, "suffers 1 core damage (Stim Dealer)"), RemoveCountersEffect(self, "power"), DamageApplyEffect("core", 1))
                else
                    g.push(AddCounterEffect(self, "power", 1), MessageEffect(Runner, "gains [click] (Stim Dealer)"), GainClicksEffect(Runner, 1))
                NextAction
        }
    }

    object StreetMagic extends RR("10003") {
        override def abilities(self : Card)(implicit g : Game) = {
            val ice = encountering.%(i => g.unbrokenSubs(i).num >= 2)
            $(Ability("choose the order in which the unbroken subroutines resolve", NoCost, ice.$./~(i => $(MarkUsedRunEffect(self, "magic"), StreetMagicAction(self, i, g.unbrokenSubs(i)))), ok = ice.any && !g.state(self).usedRun.has("magic"), reason = "not encountering ice with 2 or more unbroken subroutines"))
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case StreetMagicAction(self, ice, remaining) =>
                val l = remaining.%(i => !g.state(ice).brokenSubs.has(i))
                if (l.num >= 1 && g.run.exists(r => !r.ended && r.ice.has(ice)))
                    g.push(ChooseOneEffect(Runner, "Street Magic: choose the next subroutine to resolve", l./(i => Alt(g.promptText("Subroutine " + (i + 1) + ": " + g.subs(ice).lift(i)./(_.label).|("?")), $(StreetMagicFireAction(self, ice, i, l.but(i)))))))
                NextAction
            case StreetMagicFireAction(self, ice, i, rest) =>
                g.fxSubOrder += ice -> (g.fxSubOrder.get(ice).|($) :+ i)
                if (rest.num > 1)
                    g.push(StreetMagicAction(self, ice, rest))
                else
                    g.fxSubOrder += ice -> (g.fxSubOrder(ice) ++ rest)
                NextAction
        }
    }

    object StreetPeddler extends RR("08062") {
        override def onInstall(self : Card)(implicit g : Game) = g.deck(Runner).take(3)./(c => HostUninstalledEffect(c, self, true))
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("trash Street Peddler: install a hosted card paying 1 less", NoCost, $(StreetPeddlerAction(self)), ok = g.hosted(self).any && g.isInstalled(self), reason = "no hosted card"))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case StreetPeddlerAction(self) =>
                val l = g.hosted(self)
                l.foreach(c => g.moveTo(c, LimboZone))
                g.log(Runner, "sets aside the cards hosted on", self, ":", l./(g.cardElem).comma)
                g.trashCard(self, Runner, false)
                val ok = l.%(c => !c.data.isEvent && g.runnerInstallableNow(c, 1))
                if (ok.any)
                    g.push(ChooseCardEffect(Runner, ok, StreetPeddlerPurpose(self), "Street Peddler: choose a card to install paying 1 less", true), StreetPeddlerCleanupAction(self, l))
                else
                    g.push(StreetPeddlerCleanupAction(self, l))
                NextAction
            case StreetPeddlerCleanupAction(self, l) =>
                val rest = l.%(c => g.zone(c) == LimboZone)
                rest.foreach(c => g.moveTo(c, DiscardZone(Runner)))
                if (rest.any)
                    g.log(Runner, "trashes", rest./(g.cardElem).comma)
                NextAction
        }
        override def chosenCard(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(c => g.pushAll(g.installRunner(c, 1)))
            NextAction
        }
    }

    object SymmetricalVisage extends RR("08009") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case ClickDraw(Runner) => geneticsMark(self, "visage").$./(m => $(m, MessageEffect(Runner, "gains 1 credit (Symmetrical Visage)"), gain(Runner, 1))).flatten
            case _ => $
        }
    }

    object SyntheticBlood extends RR("08007") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case DamageDone(_, n) if n > 0 => geneticsMark(self, "blood").$./(m => $(m, MessageEffect(Runner, "draws 1 card (Synthetic Blood)"), draw(Runner, 1))).flatten
            case _ => $
        }
    }

    object TalliePerrault extends RR("04083") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case PlayResolved(Corp, c) if g.has(c, "Black Ops") || g.has(c, "Gray Ops") =>
                $(OptionalEffect(Runner, "Tallie Perrault: give the Corp 1 bad publicity and take 1 tag?", $(MessageEffect(Runner, "uses Tallie Perrault"), BadPubEffect(1), tag(1)), $))
            case _ => $
        }
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("draw 1 card for each bad publicity the Corp has (" + g.badPublicity + ")", $(TrashSelfCost), $(draw(Runner, g.badPublicity)), ok = g.badPublicity > 0, reason = "no bad publicity"))
    }

    object TechTrader extends RR("10023") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case AbilityUsed(Runner, c, _) if c != self && g.zone(c) == DiscardZone(Runner) && g.owner(c).isRunner => $(MessageEffect(Runner, "gains 1 credit (Tech Trader)"), gain(Runner, 1))
            case _ => $
        }
    }

    object TechnicalWriter extends RR("09055") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Installed(Runner, c) if (c.data.isHardware || c.data.isProgram) && !g.state(c).facedown => $(AddCounterEffect(self, "credit", 1))
            case _ => $
        }
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("take all hosted credits (" + g.counter(self, "credit") + ")", $(ClickCost(1), TrashSelfCost), $(gain(Runner, g.counter(self, "credit"))), click = true, ok = g.counter(self, "credit") > 0, reason = "no hosted credits"))
    }

    object TempleOfTheLiberatedMind extends RR("10082") {
        override def abilities(self : Card)(implicit g : Game) = $(
            Ability("place 1 power counter", click(), $(AddCounterEffect(self, "power", 1)), click = true),
            Ability("gain [click]", $(CounterCost("power", 1)), $(GainClicksEffect(Runner, 1)), once = once("temple"), ok = g.active.isRunner, reason = "only during your turn"))
    }

    object TemujinContract extends RR("11026") {
        override def initialCounters(self : Card)(implicit g : Game) = Map("credit" -> 20)
        override def onInstall(self : Card)(implicit g : Game) = $(ChooseServerForAction(Runner, self, g.servers, false, "temujin", "Temujin Contract: choose a server"))
        override def triggers(self : Card, e : Event)(implicit g : Game) = trashOnEmpty(self, e, "credit") ++ (e match {
            case SuccessfulRun(s) if g.state(self).memo == s.name => $(MessageEffect(Runner, "takes 4 credits from Temujin Contract"), TakeCreditsEffect(Runner, self, 4))
            case _ => $
        })
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case ServerPickedAction(self, s, "temujin") =>
                s.foreach { s =>
                    g.log(Runner, "chooses", s, "for Temujin Contract")
                    g.state(self).memo = s.name
                }
                NextAction
        }
    }

    object TheArchivist extends RR("12003") {
        override def link(self : Card)(implicit g : Game) = 1
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case AgendaScored(c) if g.has(c, "Initiative") || g.has(c, "Security") => $(MessageEffect(Runner, "forces the Corp to trace (The Archivist)"), TraceEffect(1, $, $(BadPubEffect(1))))
            case _ => $
        }
    }

    object TheBlackFile extends RR("10099") {
        // "the Corp cannot win except by flatline": RestrictionRules.corpCannotWin; the win is checked again when it leaves play
        // (removed from the game by its own ability, or trashed)
        override def onTrash(self : Card, by : Faction)(implicit g : Game) = $(CheckWinAction)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) => $(BlackFileAction(self))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case BlackFileAction(self) =>
                if (g.counter(self, "power") >= 2)
                    g.push(RemoveFromGameEffect(self), CheckWinAction)
                else
                    g.push(AddCounterEffect(self, "power", 1))
                NextAction
        }
    }

    object TheHelpfulAI extends RR("02008") {
        override def link(self : Card)(implicit g : Game) = 1
        override def abilities(self : Card)(implicit g : Game) = {
            val l = g.installedCards(Runner).%(g.isIcebreaker)
            $(Ability("give an icebreaker +2 strength until the end of the turn", $(TrashSelfCost), $(ChooseCardEffect(Runner, l, RotPumpPurpose(self, 2, false), "The Helpful AI: choose an icebreaker", false)), ok = l.any, reason = "no icebreaker"))
        }
    }

    object TheMasqueA extends RR("14024") {
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("make a run and gain [click]; if successful, draw 1 card", $(ClickCost(1), TrashSelfCost), $(GainClicksEffect(Runner, 1), RunAnyServerEffect(|(self), g.servers)), click = true))
    }

    object TheMasqueB extends RR("14025") {
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("make a run and gain [click]; if successful, you may run another server", $(ClickCost(1), TrashSelfCost), $(GainClicksEffect(Runner, 1), RunAnyServerEffect(|(self), g.servers)), click = true))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case MasqueBAction(self) =>
                g.run.foreach { r =>
                    g.insertAfterRunCleanup(OptionalEffect(Runner, "The Masque B: make another run on another server?", $(RunAnyServerEffect(None, g.servers.but(r.server))), $))
                }
                NextAction
        }
    }

    object TheShadowNet extends RR("13027") {
        override def abilities(self : Card)(implicit g : Game) = {
            val l = g.discard(Runner).%(c => c.data.isEvent && !g.has(c, "Priority") && g.impl(c).canPlay(c))
            $(Ability("forfeit an agenda: play an event from the heap ignoring all costs", click(), $(forfeitChoice(self, "The Shadow Net: choose an agenda to forfeit"), ChooseCardEffect(Runner, l, RotPlayPurpose(self, false), "The Shadow Net: choose an event to play", false)), click = true, ok = l.any && runnerAgendas.any, reason = "needs an agenda to forfeit and an event in the heap"))
        }
    }

    object TheSource extends RR("03055") {
        override def advancementRequirementMod(self : Card, agenda : Card)(implicit g : Game) = 1
        override def stealCostMod(self : Card, agenda : Card)(implicit g : Game) = c(3)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case AgendaScored(_) if g.isInstalled(self) => $(MessageEffect(Runner, "trashes The Source"), TrashEffect(self, Runner, false))
            case AgendaStolen(_) if g.isInstalled(self) => $(MessageEffect(Runner, "trashes The Source"), TrashEffect(self, Runner, false))
            case _ => $
        }
    }

    object TheSupplier extends RR("06056") {
        override def abilities(self : Card)(implicit g : Game) = {
            val l = g.hand(Runner).%(c => c.data.isHardware || c.data.isResource)
            $(Ability("host a resource or piece of hardware from the grip", click(), $(ChooseCardEffect(Runner, l, RotHostPurpose(self, false), "The Supplier: choose a card to host", false)), click = true, ok = l.any, reason = "nothing to host"))
        }
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) if g.hosted(self).exists(c => g.runnerInstallableNow(c, 2)) =>
                $(ChooseCardEffect(Runner, g.hosted(self).%(c => g.runnerInstallableNow(c, 2)), RotUnhostInstallPurpose(self, 2), "The Supplier: install a hosted card paying 2 less?", true))
            case _ => $
        }
    }

    object TheTurningWheel extends RR("10085") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case RunEnds(s, _, false) if (s == HQ || s == RnD) && g.isInstalled(self) => $(AddCounterEffect(self, "power", 1))
            case _ => $
        }
        override def abilities(self : Card)(implicit g : Game) = $(
            Ability("access 1 additional card from R&D for the remainder of the run", $(CounterCost("power", 2)), $(AccessBonusEffect(RnD, 1)), ok = activeRun, reason = "only during a run"),
            Ability("access 1 additional card from HQ for the remainder of the run", $(CounterCost("power", 2)), $(AccessBonusEffect(HQ, 1)), ok = activeRun, reason = "only during a run"))
    }

    object TheophiliusBagbiter extends RR("05049") {
        override def handSize(self : Card, f : Faction)(implicit g : Game) = f.isRunner.?(g.credits(Runner) - 5).|(0)
        override def onInstall(self : Card)(implicit g : Game) = $(MessageEffect(Runner, "loses all credits (Theophilius Bagbiter)"), LoseAllCreditsEffect(Runner))
    }

    object ThunderArtGallery extends RR("22013") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TagsRemoved(_) if unused(self, "gallery") && installableFromHand(1).any =>
                $(mark(self, "gallery"), InstallFromListDiscountEffect(Runner, installableFromHand(1), 1, "Thunder Art Gallery: install a card from the grip paying 1 less?", true))
            case _ => $
        }
    }

    object TriMafContact extends RR("05050") {
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("gain 2 credits", click(), $(gain(Runner, 2)), click = true, once = once("trimaf")))
        override def onTrash(self : Card, by : Faction)(implicit g : Game) = $(MessageEffect(Runner, "suffers 3 meat damage (Tri-maf Contact)"), meat(3))
    }

    object TysonObservatory extends RR("08030") {
        override def abilities(self : Card)(implicit g : Game) = {
            val l = g.deck(Runner).%(_.data.isHardware).sortBy(_.title)
            $(Ability("search the stack for a piece of hardware and add it to the grip", $(ClickCost(2)), $(ChooseCardEffect(Runner, l, AddToHandPurpose(Runner, true), "Tyson Observatory: choose a piece of hardware", true), ShuffleDeckEffect(Runner)), click = true, ok = g.deck(Runner).any, reason = "empty stack"))
        }
    }

    object UnderworldContact extends RR("20060") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) if g.link >= 2 => $(MessageEffect(Runner, "gains 1 credit (Underworld Contact)"), gain(Runner, 1))
            case _ => $
        }
    }

    object UtopiaShard extends RotResourceCards1.Shard("06100", HQ) {
        def shardLabel = "the Corp discards 2 cards from HQ at random"
        def shardEffect(self : Card)(implicit g : Game) = $(RandomTrashFromHandEffect(Corp, 2))
    }

    object VirusBreedingGround extends RR("07052") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) => $(AddCounterEffect(self, "virus", 1))
            case _ => $
        }
        override def abilities(self : Card)(implicit g : Game) = {
            val l = (g.installedCards(Runner) ++ g.installedCards(Corp)).but(self).%(c => g.counter(c, "virus") > 0)
            $(Ability("move 1 hosted virus counter to another card with virus counters", click(), $(ChooseCardEffect(Runner, l, RotMoveVirusPurpose(self), "Virus Breeding Ground: choose a card", false)), click = true, ok = l.any && g.counter(self, "virus") > 0, reason = "no virus counters to move"))
        }
    }

    object Wasteland extends RR("09036") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Trashed(c, Runner, _) if c != self && g.owner(c).isRunner && !c.data.isEvent && g.inPlayZone(g.lastTrashedFrom) && unused(self, "waste") =>
                $(mark(self, "waste"), MessageEffect(Runner, "gains 1 credit (Wasteland)"), gain(Runner, 1))
            case _ => $
        }
    }

    object WirelessNetPavilion extends RR("08108") {
        // the additional cost of the Corp's basic action to trash a resource: Game.trashResourceCost (rules-ext-credits.scala)
    }

    object WomanInTheRedDress extends RR("04048") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) if g.deck(Corp).any =>
                $(MessageEffect(Runner, "uses Woman in the Red Dress to reveal the top card of R&D"), RevealCardsEffect(Corp, g.deck(Corp).take(1)), OptionalEffect(Corp, "Woman in the Red Dress: draw the revealed card?", $(draw(Corp, 1)), $))
            case _ => $
        }
    }

    object Wyldside extends RR("01016") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) => $(MessageEffect(Runner, "draws 2 cards and loses [click] (Wyldside)"), ClickLossEffect(Runner, 1), draw(Runner, 2))
            case _ => $
        }
    }

    object ZonaSulShipping extends RR("06097") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) => $(AddCounterEffect(self, "credit", 1))
            case TagsGained(_) if g.isInstalled(self) => $(MessageEffect(Runner, "trashes Zona Sul Shipping (tagged)"), TrashEffect(self, Runner, false))
            case Installed(Runner, c) if c == self && g.tagged => $(MessageEffect(Runner, "trashes Zona Sul Shipping (tagged)"), TrashEffect(self, Runner, false))
            case _ => $
        }
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("take all hosted credits (" + g.counter(self, "credit") + ")", click(), $(TakeCreditsEffect(Runner, self, g.counter(self, "credit"))), click = true, ok = g.counter(self, "credit") > 0, reason = "no hosted credits"))
    }

    val all : $[CardImpl] = $(MaxwellJames, MissBones, Motivation, MrLi, MuertosGangMember, NetMercur, NetworkExchange, NeutralizeAllThreats, NewAngelesCityHall,
        NoOneHome, OffCampusApartment, OfficerFrank, OracleMay, OrderOfSol, PADTap, PaigePiper, Paparazzi, Patron, PersonalWorkshop, PoliticalOperative, PowerTap,
        PsychMike, PublicSympathy, RachelBeckman, RaymondFlint, Reclaim, RogueTrading, Rolodex, Rosetta, SacrificialClone, SacrificialConstruct, SafetyFirst,
        SalsetteSlums, SalvagedVanadisArmory, SameOldThing, ShadowTeam, Slipstream, Spoilers, StarlightCrusadeFunding, StimDealer, StreetMagic, StreetPeddler,
        SymmetricalVisage, SyntheticBlood, TalliePerrault, TechTrader, TechnicalWriter, TempleOfTheLiberatedMind, TemujinContract, TheArchivist, TheBlackFile,
        TheHelpfulAI, TheMasqueA, TheMasqueB, TheShadowNet, TheSource, TheSupplier, TheTurningWheel, TheophiliusBagbiter, ThunderArtGallery, TriMafContact,
        TysonObservatory, UnderworldContact, UtopiaShard, VirusBreedingGround, Wasteland, WirelessNetPavilion, WomanInTheRedDress, Wyldside, ZonaSulShipping)
}


object RotResourceCards {
    val all : $[CardImpl] = RotResourceCards1.all ++ RotResourceCards2.all
}
