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
// Vantage Point (vp) card implementations, ported from jinteki.net src/clj/game/cards/*.clj
// ---------------------------------------------------------------------------------------------------------------------

// card specific actions and purposes
case class ChainReactionCorpAction(card : Card) extends CardAction
case class KompromatAction(card : Card) extends CardAction
case class BakerPayAction(card : Card, server : Server) extends CardAction
case class HiramAction(card : Card) extends CardAction
case class BetaBuildPurpose(card : Card) extends CardPurpose
case class BetaBuildReturnAction(card : Card) extends CardAction
case class MethuselahPurpose(card : Card) extends CardPurpose
case class RwsPurpose(card : Card) extends CardPurpose
case class RwsShuffleAction(card : Card) extends CardAction
case class RwsAskAction(card : Card) extends CardAction        // asks with the grip as it is when the trigger resolves
case class SipaPurpose(card : Card, ice : Card) extends CardPurpose
case class WotsAction(card : Card, installedThisTurn : Boolean) extends CardAction
case class ReallocPurpose(card : Card) extends CardPurpose
case class PerfectRecallPurpose(card : Card) extends CardPurpose
case class MeliesChoiceAction(self : Faction, card : Card, n : Int) extends BaseAction("Melies U: secretly choose a server")(n match { case 1 => "HQ" ; case 2 => "R&D" ; case _ => "Archives" }) with SelfPerform {
    def perform(soft : Void)(implicit g : Game) = {
        g.state(card).counters += "melies" -> n
        g.log(Corp, "secretly chooses a server (Melies U)")
        NextAction
    }
}
case class MeliesChooseAction(card : Card) extends CardAction
case class MeliesTrashAction(card : Card) extends CardAction
case class LotusPurpose(card : Card) extends CardPurpose
case class EzamPurpose(card : Card) extends CardPurpose
case class EzamTopAction(card : Card) extends CardAction
case class EzamPumpAction(card : Card) extends CardAction
case class VicsekAction(card : Card, step : Int) extends CardAction
case class CultivateTrashPurpose(card : Card, k : Int) extends CardPurpose
case class CultivateHandPurpose(card : Card, k : Int) extends CardPurpose
case class UnleashPurpose(card : Card) extends CardPurpose
case class LuanaAction(card : Card) extends CardAction
case class FlywheelAction(card : Card) extends CardAction
case class TocsinSearchAction(card : Card, subtype : String) extends CardAction
case class TocsinPurpose(card : Card) extends CardPurpose
case class MyoshuAction(card : Card) extends CardAction
case class ReanimationPurpose(card : Card) extends CardPurpose
case class LtdSearchAction(card : Card, zone : String) extends CardAction
case class LtdPurpose(card : Card, fromRd : Boolean) extends CardPurpose
case class SzeFaceupAction(card : Card) extends CardAction
case class StickAndPokeAction(card : Card, ice : Card) extends CardAction


object VPCards {
    // ------------------------------------------------------------------------------------------------------------
    // Runner
    // ------------------------------------------------------------------------------------------------------------

    object ChainReaction extends Startup("36001") {
        override def canPlay(self : Card)(implicit g : Game) = $(HQ, RnD, Archives).forall(s => g.successfulRunsThisTurn.has(s))
        override def onPlay(self : Card)(implicit g : Game) = {
            val l = g.installedCards(Corp).%!(cannotBeTrashedByRunner)
            val n = 2.atMost(l.num)
            (n > 0).$(ChooseCardsEffect(Runner, l, n, n, RunnerTrashPurpose(self), "Chain Reaction: choose " + n + " installed Corp cards to trash")) :+ ChainReactionCorpAction(self)
        }
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case ChainReactionCorpAction(self) =>
                val l = g.installedCards(Runner)
                if (l.any)
                    g.push(ChooseCardEffect(Corp, l, TrashPurpose(Corp), "Chain Reaction: choose an installed Runner card to trash", false))
                NextAction
        }
    }

    object TakeADive extends Startup("36002") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = $(RunAnyServerEffect(|(self), $(HQ, RnD)))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(s) if thisCardRun(self) && g.run.exists(_.subsFired > 0) => $(MessageEffect(Runner, "gives the Corp 1 bad publicity (Take a Dive)"), BadPubEffect(1))
            case RunEnds(_, _, _) if thisCardRun(self) => $(RemoveFromGameEffect(self))
            case _ => $
        }
    }

    object TungstenTailor extends Startup("36003") {
        override def iceStrengthMod(self : Card, ice : Card)(implicit g : Game) = -1
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SubsBroken(ice, _, _) if g.iceStrength(ice) <= 0 && !g.usedThisTurn(self, "tailor") => $(MarkUsedEffect(self, "tailor"), gain(Runner, 1))
            case _ => $
        }
    }

    object Corsair extends Startup("36004") {
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(1), 1, $("Barrier")))
        override def abilities(self : Card)(implicit g : Game) = {
            val ice = encounteredIce.%(i => g.has(i, "Barrier"))
            $(Ability("give -3 strength to the encountered barrier (stealth credit)", c(1), ice.$./(i => PumpIceEffect(i, -3, EncounterDuration)), ok = ice.any && g.stealthCredits >= 1, reason = "needs an encountered barrier and a stealth credit"))
        }
    }

    object Lampades extends Startup("36005") {
        override def initialCounters(self : Card)(implicit g : Game) = Map("power" -> 3)
        override def accessAbility(self : Card, target : Card)(implicit g : Game) = {
            val cost = target.data.cost.|(0)
            (!target.data.isAgenda && !g.zone(target).is[DiscardZone] && g.counter(self, "power") > 0).?(
                Ability("trash the accessed card paying its printed cost (" + cost + ") with stealth credits", $(CounterCost("power", 1), CreditCost(cost)), $(TrashEffect(target, Runner, true)), ok = g.stealthCredits >= cost && g.run.exists(!_.cannotStealOrTrash) && !g.accessProtected(target) && !cannotBeTrashedByRunner(target), reason = "needs stealth credits"))
        }
    }

    object Hackerspace extends Startup("36006") {
        def eligible(c : Card)(implicit g : Game) = c.data.isResource && c.data.unique && (g.has(c, "Connection") || g.has(c, "Companion"))
        override def canHost(self : Card, card : Card)(implicit g : Game) = eligible(card)
        override def installCostMod(self : Card, card : Card)(implicit g : Game) = (card != self && eligible(card)).?(-1).|(0)
        override def handSize(self : Card, f : Faction)(implicit g : Game) = (f.isRunner && g.hosted(self).exists(c => g.has(c, "Connection")) && g.hosted(self).exists(c => g.has(c, "Companion"))).?(2).|(0)
    }

    object NurseHanh extends Startup("36007") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case ArchivesFlipped(n) if n >= 2 => $(MessageEffect(Runner, "draws 2 cards (Nurse Hanh)"), draw(Runner, 2))
            case _ => $
        }
    }

    object StickAndPoke extends Startup("36008") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case EncounterIce(ice) if !g.usedThisTurn(self, "stick") => $(MarkUsedEffect(self, "stick"), StickAndPokeAction(self, ice))
            case _ => $
        }
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case StickAndPokeAction(self, ice) =>
                val str = g.iceStrength(ice)
                val canBreak = g.installedCards(Runner).exists(b => g.isActive(b) && g.impl(b).breaks(b).exists(br => (br.subtypes.none || br.subtypes.exists(t => g.has(ice, t))) && g.impl(ice).canBreakWith(ice, b)) && g.breakerStrength(b) >= str)
                g.push(ChooseOneEffect(Runner, "Stick and Poke: the ice gains a subroutine (do 1 net damage, the Runner draws 1 card)", $(
                    Alt("Break it with a matching icebreaker (1 credit)", $(LoseCreditsEffect(Runner, 1)), canBreak && g.credits(Runner) >= 1),
                    Alt("Let the subroutine resolve", $(net(1), draw(Runner, 1))))))
                NextAction
        }
    }

    object Vic extends Startup("36009") {
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("draw 1 card and remove 1 tag", $(ClickCost(1), CreditCost(1)), $(draw(Runner, 1)) ++ g.tagged.$(RemoveTagsEffect(1)), click = true, once = once("vic"), ok = g.deck(Runner).any || g.tagged, reason = "nothing to do"))
    }

    object Kompromat extends Startup("36010") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def canPlay(self : Card)(implicit g : Game) = g.servers.exists(s => g.ice(s).any)
        override def onPlay(self : Card)(implicit g : Game) = $(RunAnyServerEffect(|(self), g.servers.%(s => g.ice(s).any)))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case RunEnds(s, true, _) if thisCardRun(self) => $(KompromatAction(self), RemoveFromGameEffect(self))
            case RunEnds(s, false, _) if thisCardRun(self) => $(RemoveFromGameEffect(self))
            case _ => $
        }
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case KompromatAction(self) =>
                val l = g.run./(r => g.ice(r.server).%(g.isRezzed)).|($)
                if (l.any)
                    g.push(ChooseOneEffect(Corp, "Kompromat: derez a piece of ice protecting the attacked server or take 1 bad publicity", $(
                        Alt("Derez a piece of ice", $(ChooseCardEffect(Corp, l, DerezPurpose, "Kompromat: choose a piece of ice to derez", false))),
                        Alt("Take 1 bad publicity", $(BadPubEffect(1))))))
                else
                    g.push(BadPubEffect(1))
                NextAction
        }
    }

    object SellOut extends Startup("36011") {
        override def canPlay(self : Card)(implicit g : Game) = g.installedCards(Runner).exists(_.data.isResource)
        override def onPlay(self : Card)(implicit g : Game) = $(ChooseCardEffect(Runner, g.installedCards(Runner).%(_.data.isResource), RunnerTrashPurpose(self), "Sell Out: choose a resource to trash", false), gain(Runner, 4), draw(Runner, 2))
    }

    object Tailgate extends Startup("36012") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def playCostMod(self : Card, card : Card)(implicit g : Game) = (card == self).?(-g.ice(HQ).num).|(0)
        override def onPlay(self : Card)(implicit g : Game) = $(RunEffect(HQ, |(self)))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(HQ) if thisCardRun(self) => $(AccessBonusEffect(HQ, 2))
            case _ => $
        }
    }

    object BorrowedGoods extends Startup("36013") {
        override def memory(self : Card)(implicit g : Game) = 1
        override def onInstall(self : Card)(implicit g : Game) = (!g.tagged).$(tag(1))
    }

    object Rotary extends Startup("36014") {
        override def memory(self : Card)(implicit g : Game) = 1
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case BreachServer(s) if s == HQ || s == RnD => $(OptionalEffect(Runner, "Rotary: take 1 tag to access 1 additional card?", $(tag(1), AccessBonusEffect(s, 1)), $))
            case _ => $
        }
        override def corpAbilities(self : Card)(implicit g : Game) = $(Ability("trash Rotary", $(ClickCost(1), CreditCost(2)), $(TrashEffect(self, Corp, false)), click = true, ok = g.tagged, reason = "Runner is not tagged"))
    }

    object Baker extends Startup("36015") {
        override def abilities(self : Card)(implicit g : Game) = $(Ability("run Archives (may switch to HQ or R&D when approaching)", click(), $(RunEffect(Archives, |(self))), click = true, once = once("baker")))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case ApproachServer(Archives) if thisCardRun(self) && g.stealthCredits >= 1 =>
                $(ChooseOneEffect(Runner, "Baker: pay 1 stealth credit to change the attacked server?", $(
                    Alt("Change to HQ", $(BakerPayAction(self, HQ), RedirectRunEffect(HQ, 0, false))),
                    Alt("Change to R&D", $(BakerPayAction(self, RnD), RedirectRunEffect(RnD, 0, false))),
                    Alt("Continue to Archives", $))))
            case _ => $
        }
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case BakerPayAction(self, s) =>
                g.activeCards(Runner).%(c => g.has(c, "Stealth") && g.counter(c, "credit") > 0).starting.foreach { c =>
                    g.addCounter(c, "credit", -1)
                    g.log(Runner, "spends", 1.credits, "from", c)
                    g.trigger(CounterAdded(c, "credit"))
                }
                NextAction
        }
    }

    object UnderdomeIrregulars extends Startup("36016") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Rezzed(c) if c.data.isIce => $(MarkUsedEffect(self, "ice-rezzed"))
            case ActionPhaseEnds(Runner) if g.usedThisTurn(self, "ice-rezzed") =>
                $(ChooseOneEffect(Runner, "Underdome Irregulars: choose one", $(Alt("Draw 2 cards", $(draw(Runner, 2))), Alt("Remove 1 tag", $(RemoveTagsEffect(1)), g.tagged))))
            case ActionPhaseEnds(Runner) => $(MessageEffect(Runner, "trashes Underdome Irregulars (no ice was rezzed this turn)"), TrashEffect(self, Runner, false))
            case _ => $
        }
    }

    object Hiram extends Startup("36017") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Installed(Runner, c) if c.data.isHardware && g.deck(Corp).any => $(HiramAction(self))
            case Trashed(c, _, _) if c.data.isHardware && g.owner(c).isRunner && g.deck(Corp).any => $(HiramAction(self))
            case _ => $
        }
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case HiramAction(self) =>
                g.deck(Corp).starting.foreach { c =>
                    g.log(Runner, "looks at the top card of R&D (Hiram Svensson)")
                    g.push(ChooseCardEffect(Runner, $(c), LookPurpose(self), "Hiram Svensson: the top card of R&D (choose it or decline, it stays)", true))
                }
                NextAction
        }
    }

    object Aircheck extends Startup("36018") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def initialCounters(self : Card)(implicit g : Game) = Map("credit" -> 4)
        override def creditsUsableForRun(self : Card)(implicit g : Game) = thisCardRun(self)
        override def onPlay(self : Card)(implicit g : Game) = $(RunAnyServerEffect(|(self), $(HQ, RnD)))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case RunEnds(s, true, _) if thisCardRun(self) && g.remotes.any =>
                g.insertAfterRunCleanup(OptionalEffect(Runner, "Aircheck: run a remote server?", $(RunAnyServerEffect(None, g.remotes)), $))
                $
            case _ => $
        }
    }

    object BetaBuild extends Startup("36019") {
        override def makesRun(self : Card)(implicit g : Game) = true
        override def onPlay(self : Card)(implicit g : Game) = {
            val l = g.deck(Runner).%(c => c.data.isProgram && !c.data.isVirus && g.memoryFree >= c.data.memory.|(0) && !g.impl(c).mustBeHosted(c)).sortBy(_.title)
            l.any.$(ChooseCardEffect(Runner, l, BetaBuildPurpose(self), "Beta Build: choose a non-virus program to install ignoring all costs", true)) ++ $(ShuffleDeckEffect(Runner), RunAnyServerEffect(|(self), g.servers))
        }
        override def chosenCard(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach { c =>
                g.linkedCard += p.card -> c
                g.push(RunnerInstallEffect(c, None, true))
            }
            NextAction
        }
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case RunEnds(_, _, _) if thisCardRun(self) && g.linkedCard.get(self).exists(g.isInstalled) => $(BetaBuildReturnAction(self))
            case _ => $
        }
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case BetaBuildReturnAction(self) =>
                g.linkedCard.get(self).foreach { c =>
                    if (g.isInstalled(c)) {
                        g.uninstall(c)
                        g.moveTo(c, DeckZone(Runner), true)
                        g.log(c, "is added to the top of the stack (Beta Build)")
                    }
                }
                g.linkedCard -= self
                NextAction
        }
    }

    object Methuselah extends Startup("36020") {
        override def memory(self : Card)(implicit g : Game) = 1
        override def creditsUsableForRun(self : Card)(implicit g : Game) = true
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case RunInitiated(_) if g.hand(Runner).exists(_.data.isHardware) =>
                $(ChooseCardEffect(Runner, g.hand(Runner).%(_.data.isHardware), MethuselahPurpose(self), "Methuselah: trash a piece of hardware from the grip to place 2 credits?", true))
            case _ => $
        }
        override def chosenCard(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach { c =>
                g.log(Runner, "trashes", c, "from the grip")
                g.moveTo(c, DiscardZone(Runner))
                g.push(AddCounterEffect(p.card, "credit", 2))
            }
            NextAction
        }
    }

    object Touchstone extends Startup("36021") {
        override def creditsUsableForRun(self : Card)(implicit g : Game) = true
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Played(Runner, c) if c.data.isEvent && g.reg("played") == 1 => $(AddCounterEffect(self, "credit", 1))
            case _ => $
        }
    }

    object ReadWriteShare extends Startup("36022") {
        def ask(self : Card)(implicit g : Game) = (g.stashed(self).num < 4 && g.hand(Runner).any).$(ChooseCardEffect(Runner, g.hand(Runner), RwsPurpose(self), "Read-Write Share: host a card from the grip facedown to draw 1 card?", true))
        override def onInstall(self : Card)(implicit g : Game) = ask(self)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) => $(RwsAskAction(self))
            case _ => $
        }
        override def chosenCard(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach { c =>
                g.moveTo(c, StashZone(p.card))
                g.state(c).facedown = true
                g.log(Runner, "hosts a card facedown on", p.card)
                g.push(draw(Runner, 1))
            }
            NextAction
        }
        override def abilities(self : Card)(implicit g : Game) = $(Ability("trash: shuffle all hosted cards into the stack", NoCost, $(RwsShuffleAction(self)), ok = g.isInstalled(self), reason = "not installed"))
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case RwsAskAction(self) =>
                if (g.isInstalled(self))
                    g.pushAll(ask(self))
                NextAction
            case RwsShuffleAction(self) =>
                val l = g.stashed(self)
                l.foreach(c => g.moveTo(c, DeckZone(Runner)))
                g.log(Runner, "shuffles", l.num.cards, "into the stack")
                g.trashCard(self, Runner, false)
                g.push(ShuffleDeckEffect(Runner))
                NextAction
        }
        override def onTrash(self : Card, by : Faction)(implicit g : Game) = { g.uninstall(self) ; $ }
    }

    object Sipa extends Startup("36023") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SubsBroken(ice, _, _) if g.unbrokenSubs(ice).none =>
                g.state(ice).usedRun :+= "fully-broken"
                $
            case PassIce(ice) if g.state(ice).usedRun.has("fully-broken") && g.serverOf(ice).exists(s => g.ice(s).ending.has(ice)) && !g.usedThisTurn(self, "sipa") && g.allIce.num >= 2 =>
                $(MarkUsedEffect(self, "sipa"), ChooseCardEffect(Runner, g.allIce.but(ice), SipaPurpose(self, ice), "Sipa: swap the passed ice with another installed piece of ice?", true))
            case _ => $
        }
        override def chosenCard(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case SipaPurpose(self, ice) =>
                cards.single.foreach(c => g.push(SwapIceEffect(ice, c)))
                NextAction
        }
    }

    object Stowaway extends Startup("36024") {
        override def mustBeHosted(self : Card)(implicit g : Game) = true
        override def hostRequirement(self : Card, host : Card)(implicit g : Game) = host.data.isIce
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(s) if g.state(self).host.exists(h => g.serverOf(h).has(s)) => $(gain(Runner, 2))
            case _ => $
        }
    }

    object WordOnTheStreet extends Startup("36025") {
        override def agendaPointsBonus(self : Card)(implicit g : Game) = inCorpScore(self).?(-1).|(0)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case AgendaScored(_) if g.isInstalled(self) => $(WotsAction(self, g.lastScoredInstalledTurn == g.turn))
            case _ => $
        }
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case WotsAction(self, true) =>
                g.uninstall(self)
                g.moveTo(self, ScoredZone(Corp))
                g.log(self, "is added to the Corp's score area as an agenda worth -1 agenda point")
                NextAction
            case WotsAction(self, false) =>
                g.trashCard(self, Runner, false)
                g.push(gain(Runner, 4), draw(Runner, 1))
                NextAction
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Corp
    // ------------------------------------------------------------------------------------------------------------

    object MeliesCityLuxuryLine extends Startup("36026") {
        override def accessCostPaidByRunner(self : Card)(implicit g : Game) = $(ClickCost(1))
        override def onScore(self : Card)(implicit g : Game) = $(GainClicksEffect(Corp, 1))
    }

    object Synchrocyclotron extends Startup("36027")

    object Ansel2 extends Startup("36028") {
        override def runnerAbilities(self : Card)(implicit g : Game) = bioroidBreak(self, 2, 2)
        override def subroutines(self : Card)(implicit g : Game) = $(
            Sub("Trash 1 installed Runner card", ChooseCardEffect(Corp, g.installedCards(Runner), TrashPurpose(Corp), "Ansel 2.0: choose an installed Runner card to trash", false)),
            Sub("Remove 1 card in the heap from the game", ChooseCardEffect(Corp, g.discard(Runner), RfgPurpose(self), "Ansel 2.0: choose a card in the heap to remove from the game", false)),
            Sub("You may install 1 card from HQ or Archives", ChooseCardEffect(Corp, (g.hand(Corp) ++ g.discard(Corp)).%(corpInstallable), InstallChooseServerPurpose(false), "Ansel 2.0: install a card from HQ or Archives?", true)),
            etrSub)
    }

    object Reverb extends Startup("36029") {
        override def rezCostMod(self : Card, card : Card)(implicit g : Game) = (card == self).?(-g.allIce.%(c => c != self && !g.isRezzed(c)).num).|(0)
        override def subroutines(self : Card)(implicit g : Game) = $(etrSub, etrSub)
    }

    object Sleipnir extends Startup("36030") {
        override def subroutines(self : Card)(implicit g : Game) = $(
            Sub("You may draw 1 card", OptionalEffect(Corp, "Sleipnir: draw 1 card?", $(draw(Corp, 1)), $)),
            Sub("You may shuffle 1 card from HQ or Archives into R&D", ChooseCardEffect(Corp, g.hand(Corp) ++ g.discard(Corp), ShuffleIntoDeckPurpose(Corp), "Sleipnir: shuffle a card from HQ or Archives into R&D?", true)),
            etrSub)
    }

    object Vertigo extends Startup("36031") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case PassIce(ice) if ice == self && g.clicks(Runner) == 0 => $(MessageEffect(Corp, "uses Vertigo"), CannotStealOrTrashEffect)
            case _ => $
        }
        override def subroutines(self : Card)(implicit g : Game) = $(Sub("The Runner loses [click]", LoseClicksEffect(Runner, 1)))
    }

    object CaveatEmptor extends Startup("36032") {
        override def onPlay(self : Card)(implicit g : Game) = $(ChooseOneEffect(Corp, "Caveat Emptor: choose one", $(
            Alt("Gain 6 credits, the Runner gets -1 allotted click next turn", $(gain(Corp, 6), ExtraClicksEffect(Runner, -1))),
            Alt("Gain 10 credits, the Runner gets +1 allotted click next turn", $(gain(Corp, 10), ExtraClicksEffect(Runner, 1))))))
    }

    object Realloc extends Startup("36033") {
        override def canPlay(self : Card)(implicit g : Game) = doubleCanPlay(Corp) && g.rezzedIce.any
        override def onPlay(self : Card)(implicit g : Game) = {
            val n = 2.atMost(g.rezzedIce.num)
            doubleCost(Corp) ++ (n > 0).$(ChooseCardsEffect(Corp, g.rezzedIce, n, n, ReallocPurpose(self), "realloc(): choose " + n + " rezzed pieces of ice to derez (gain their printed rez cost)"))
        }
        override def chosenCard(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            val n = cards./(_.data.cost.|(0)).sum
            g.push(gain(Corp, n))
            cards.foreach(c => g.push(DerezEffect(c)))
            NextAction
        }
    }

    object RetirementPlan extends Startup("36034") {
        def candidates(implicit g : Game) = g.discard(Corp).%(c => c.data.isAgenda || c.data.isAsset || c.data.isIce)
        override def canPlay(self : Card)(implicit g : Game) = doubleCanPlay(Corp) && candidates.any
        override def onPlay(self : Card)(implicit g : Game) = doubleCost(Corp) ++ candidates.any.$(ChooseCardEffect(Corp, candidates, InstallChooseServerPurpose(false), "Retirement Plan: choose an agenda, asset or piece of ice in Archives to install", false))
    }

    object PerfectRecall extends Startup("36035") {
        override def onRez(self : Card)(implicit g : Game) = $(AddCounterEffect(self, "power", 1))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case AgendaScored(_) if g.isRezzed(self) && g.lastScoredFrom.exists(s => thisServer(self, s)) => $(AddCounterEffect(self, "power", 1))
            case AgendaStolen(_) if g.isRezzed(self) && g.lastStolenFrom.exists(s => thisServer(self, s)) => $(AddCounterEffect(self, "power", 1))
            case _ => $
        }
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("reveal 1 card in HQ: the Runner cannot steal or trash copies of it this run", $(CounterCost("power", 1)), $(ChooseCardEffect(Corp, g.hand(Corp), PerfectRecallPurpose(self), "Perfect Recall: choose a card in HQ to reveal", false)), ok = g.isRezzed(self) && g.activeRun.any && g.hand(Corp).any, reason = "only during a run"))
        override def chosenCard(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach { c =>
                g.log(Corp, "reveals", c, "from HQ: copies cannot be stolen or trashed this run")
                g.state(c).seen = true
                if (g.protectedRunKey != g.runKey)
                    g.protectedTitles = $
                g.protectedRunKey = g.runKey
                g.protectedTitles :+= c.title
            }
            NextAction
        }
        override def protectsFromAccess(self : Card, target : Card)(implicit g : Game) = g.protectedNow(target.title)
    }

    object MeliesU extends Startup("36036") {
        def face(n : Int) = n match { case 1 => "Tenure Floors: Melies U" ; case 2 => "Subsurface Labs: Melies U" ; case _ => "Disposal Grounds: Melies U" }
        def chosenServer(self : Card)(implicit g : Game) : Server = g.counter(self, "melies") match { case 2 => RnD ; case 3 => Archives ; case _ => HQ }
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnEnds(Corp) => $(MeliesChooseAction(self))
            case TurnEnds(Runner) if !flipped(self) => $(gain(Corp, 1))
            case TurnBegins(Corp) if flipped(self) => $(SetCounterEffect(self, "flip", 0), MessageEffect(Corp, "flips the identity back to Melies U: Only the Brightest"))
            case SuccessfulRun(s) if s.isCentral && !flipped(self) =>
                val n = g.counter(self, "melies").atLeast(1)
                $(SetCounterEffect(self, "flip", 1), MessageEffect(Corp, "flips the identity to " + face(n))) ++ (s == chosenServer(self) && g.deck(Corp).any).$(OptionalEffect(Corp, "Melies U: trash the top card of R&D to add 1 card from Archives to HQ?", $(MeliesTrashAction(self)), $))
            case _ => $
        }
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case MeliesChooseAction(self) =>
                Ask(Corp).group("Melies U: secretly choose a server").add($(1, 2, 3)./(n => MeliesChoiceAction(Corp, self, n)))
            case MeliesTrashAction(self) =>
                g.push(MillEffect(Corp, 1))
                if (g.discard(Corp).any || g.deck(Corp).any)
                    g.push(MeliesRecurAction(self))
                NextAction
            case MeliesRecurAction(self) =>
                if (g.discard(Corp).any)
                    g.push(ChooseCardEffect(Corp, g.discard(Corp), AddToHandPurpose(Corp, false), "Melies U: choose a card in Archives to add to HQ", true))
                NextAction
        }
    }

    object LotusHaze extends Startup("36037") {
        override def onScore(self : Card)(implicit g : Game) = $(AddCounterEffect(self, "agenda", 3))
        override def abilities(self : Card)(implicit g : Game) = {
            val l = g.installedCards(Corp).%(c => c.data.isUpgrade && g.isRezzed(c))
            $(Ability("move a rezzed upgrade to the root of another server", $(CounterCost("agenda", 1)), $(ChooseCardEffect(Corp, l, LotusPurpose(self), "Lotus Haze: choose a rezzed upgrade", false)), ok = inCorpScore(self) && l.any, reason = "no rezzed upgrade"))
        }
        override def chosenCard(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach { c =>
                val own = g.serverOf(c)
                val l = g.servers.%(s => !own.has(s)).%(s => !g.has(c, "Region") || !g.root(s).exists(x => g.has(x, "Region")))
                if (l.any)
                    g.push(ChooseServerForAction(Corp, p.card, l, false, "lotus:" + c.code + ":" + c.n, "Lotus Haze: choose a server"))
            }
            NextAction
        }
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case ServerPickedAction(self, Some(server), key) =>
                val parts = key.split(':')
                g.push(MoveToRootEffect(Card(parts(1), parts(2).toInt), server))
                NextAction
            case ServerPickedAction(self, None, key) => NextAction
        }
    }

    object Esca extends Startup("36038") {
        override def onAccess(self : Card)(implicit g : Game) =
            (!g.zone(self).is[DiscardZone]).??((g.zone(self) == DeckZone(Corp)).$(RevealEffect(Corp, self)) ++ $(MessageEffect(Corp, "uses Esca"), LoseCreditsEffect(Runner, 1)) ++ g.tagged.$(net(1)))
    }

    object Ezam extends Startup("36039") {
        override def abilities(self : Card)(implicit g : Game) = {
            val l = g.allIce.but(self)
            $(Ability("swap this ice with another installed piece of ice", click(), $(ChooseCardEffect(Corp, l, EzamPurpose(self), "ezaM: choose a piece of ice to swap with", false)), click = true, ok = g.isRezzed(self) && l.any, reason = "no other ice"))
        }
        override def chosenCard(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(c => g.push(SwapIceEffect(p.card, c)))
            NextAction
        }
        override def subroutines(self : Card)(implicit g : Game) = $(
            Sub("Look at the top card of R&D. You may add that card to the bottom of R&D", EzamTopAction(self)),
            Sub("Each piece of ice gets +1 strength for the remainder of this run", EzamPumpAction(self)))
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case EzamTopAction(self) =>
                g.deck(Corp).starting.foreach { c =>
                    g.log(Corp, "looks at the top card of R&D")
                    g.push(ChooseCardEffect(Corp, $(c), ToDeckPurpose(self, Corp, false, false), "ezaM: choose the top card to add it to the bottom of R&D, or decline", true))
                }
                NextAction
            case EzamPumpAction(self) =>
                g.pushAll(g.allIce./(i => PumpIceEffect(i, 1, RunDuration)))
                NextAction
        }
    }

    object KnowledgeSeeker extends Startup("36040") {
        override def onEncounterEnds(self : Card)(implicit g : Game) = (g.counter(self, "virus") >= 3).??($(MessageEffect(Corp, "purges virus counters and derezzes Knowledge Seeker"), DerezEffect(self), PurgeEffect))
        override def subroutines(self : Card)(implicit g : Game) = $(
            Sub("Place 1 virus counter on this ice", AddCounterEffect(self, "virus", 1)),
            Sub("Look at the top 4 cards of R&D and arrange them in any order", ArrangeTopEffect(Corp, Corp, 4)),
            etrSub)
    }

    object Lionsmane extends Startup("36041") {
        override def subroutines(self : Card)(implicit g : Game) = $(
            netSub(2),
            Sub("Do 2 net damage unless the Runner pays 3 credits", ChooseOneEffect(Runner, "Lionsmane", $(Alt("Pay 3 credits", $(LoseCreditsEffect(Runner, 3)), g.credits(Runner) >= 3), Alt("Suffer 2 net damage", $(net(2)))))),
            Sub("Do 2 net damage unless the Runner jacks out", ChooseOneEffect(Runner, "Lionsmane", $(Alt("Jack out", $(JackOutEffect)), Alt("Suffer 2 net damage", $(net(2)))))))
    }

    object Vicsek extends Startup("36042") {
        override def subroutines(self : Card)(implicit g : Game) = $(
            Sub("Do X net damage and give the Runner X tags (X = tags)", VicsekAction(self, 1)),
            Sub("Give the Runner 1 tag. Trash this ice", VicsekAction(self, 2)))
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case VicsekAction(self, 1) =>
                val x = g.tags
                if (x > 0)
                    g.push(tag(x), net(x))
                NextAction
            case VicsekAction(self, 2) =>
                g.push(tag(1), TrashEffect(self, Corp, false))
                NextAction
        }
    }

    object Cultivate extends Startup("36043") {
        override def canPlay(self : Card)(implicit g : Game) = g.deck(Corp).any
        override def onPlay(self : Card)(implicit g : Game) = {
            val k = 5.atMost(g.deck(Corp).num)
            if (k <= 1)
                $(MillEffect(Corp, 1))
            else
                $(MessageEffect(Corp, "looks at the top " + k + " cards of R&D"), ChooseCardEffect(Corp, g.deck(Corp).take(k), CultivateTrashPurpose(self, k), "Cultivate: choose a card to trash", false))
        }
        override def chosenCard(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case CultivateTrashPurpose(self, k) =>
                cards.single.foreach { c =>
                    g.log(Corp, "trashes a card from the top of R&D")
                    g.toArchives(c, false)
                }
                g.push(ChooseCardEffect(Corp, g.deck(Corp).take(k - 1), CultivateHandPurpose(self, k), "Cultivate: choose a card to add to HQ", false))
                NextAction
            case CultivateHandPurpose(self, k) =>
                cards.single.foreach { c =>
                    g.log(Corp, "adds a card from the top of R&D to HQ")
                    g.moveTo(c, HandZone(Corp))
                }
                g.push(ArrangeTopEffect(Corp, Corp, k - 2))
                NextAction
        }
    }

    object Unleash extends Startup("36044") {
        override def canPlay(self : Card)(implicit g : Game) = g.tagged && g.allIce.exists(c => !g.isRezzed(c))
        override def onPlay(self : Card)(implicit g : Game) = $(RemoveTagsEffect(1), ChooseCardEffect(Corp, g.allIce.%(c => !g.isRezzed(c)), UnleashPurpose(self), "Unleash: choose a piece of ice to rez ignoring all costs", false))
        override def chosenCard(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(c => g.push(RezEffect(c, true), ChooseSubEffect(Corp, c, false, true)))
            NextAction
        }
    }

    object TheRedRoom extends Startup("36045") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case AgendaScored(_) | AgendaStolen(_) if g.isRezzed(self) && !g.usedThisTurn(self, "redroom") => $(MarkUsedEffect(self, "redroom"), AddCounterEffect(self, "power", 1))
            case _ => $
        }
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("end the run", $(CounterCost("power", 1)), $(EndRunEffect), ok = g.isRezzed(self) && g.activeRun.exists(r => !thisServer(self, r.server)), reason = "only during a run against another server"))
    }

    object EditorialDivision extends Startup("36046") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case BadPubGained(n) if n > 0 && !g.usedThisTurn(self, "editorial") =>
                val l = g.deck(Corp).%(c => !c.data.isAgenda && $("Black Ops", "Gray Ops", "Liability", "Illicit").exists(t => g.has(c, t))).sortBy(_.title)
                $(MarkUsedEffect(self, "editorial")) ++ l.any.$(OptionalEffect(Corp, "Editorial Division: search R&D for a black ops, gray ops or liability card?", $(ChooseCardEffect(Corp, l, AddToHandPurpose(Corp, true), "Editorial Division: choose a card to reveal and add to HQ", true), ShuffleDeckEffect(Corp)), $))
            case _ => $
        }
    }

    object WitchHunt extends Startup("36047") {
        override def onScore(self : Card)(implicit g : Game) = $(SetCounterEffect(self, "scoredturn", g.turn + 1), BadPubEffect(1))
        override def onSteal(self : Card)(implicit g : Game) = $(BadPubEffect(1))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case ActionPhaseEnds(Corp) if inCorpScore(self) && g.counter(self, "scoredturn") == g.turn + 1 =>
                $(MessageEffect(Corp, "uses Witch Hunt")) ++ g.tagged.$(RemoveTagsEffect(g.tags)) :+ tag(3)
            case _ => $
        }
    }

    object MagistrateRevontulet extends Startup("36048") {
        override def extraStealCost(self : Card, agenda : Card)(implicit g : Game) = g.isRezzed(self).??($(CreditCost(3)))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case AgendaScored(_) if g.isRezzed(self) => $(MessageEffect(Corp, "uses Magistrate Revontulet"), LoseCreditsEffect(Runner, 3))
            case _ => $
        }
    }

    object NihiloAgent extends Startup("36049") {
        override def initialCounters(self : Card)(implicit g : Game) = Map("power" -> 3)
        override def triggers(self : Card, e : Event)(implicit g : Game) = trashOnEmpty(self, e, "power") ++ (e match {
            case TurnBegins(Corp) if g.isRezzed(self) => g.tagged.$(RemoveTagsEffect(1)) ++ (g.badPublicity > 0).$(BadPubEffect(-1))
            case TurnEnds(Corp) if g.isRezzed(self) => $(tag(1), BadPubEffect(1), AddCounterEffect(self, "power", -1))
            case _ => $
        })
    }

    object Grubber extends Startup("36050") {
        override def onRez(self : Card)(implicit g : Game) = g.serverOf(self).exists(_.isCentral).$(BadPubEffect(1))
        def sub(implicit g : Game) = Sub("End the run unless the Runner pays 3 credits", ChooseOneEffect(Runner, "Grubber", $(Alt("Pay 3 credits", $(LoseCreditsEffect(Runner, 3)), g.credits(Runner) >= 3), Alt("End the run", $(EndRunEffect)))))
        override def subroutines(self : Card)(implicit g : Game) = $(sub, sub)
    }

    object Lethe extends Startup("36051") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case IceBypassed(ice) if ice == self => $(MessageEffect(Corp, "uses Lethe"), tag(1))
            case SubsBroken(ice, _, _) if ice == self && g.unbrokenSubs(self).none => $(MessageEffect(Corp, "uses Lethe"), tag(1))
            case _ => $
        }
        override def subroutines(self : Card)(implicit g : Game) = $(
            Sub("You may add 1 card from Archives to the top or bottom of R&D", ChooseCardEffect(Corp, g.discard(Corp), TopOrBottomPurpose(self), "Lethe: choose a card in Archives?", true)),
            Sub("Add 1 installed Runner card to the grip", ChooseCardEffect(Corp, g.installedCards(Runner), UninstallToHandPurpose(self), "Lethe: choose an installed Runner card", false)))
    }

    object Paywall extends Startup("36052") {
        override def onEncounter(self : Card)(implicit g : Game) = $(LoseCreditsEffect(Runner, 1))
        override def subroutines(self : Card)(implicit g : Game) = $(Sub("End the run unless the Runner pays 1 credit", ChooseOneEffect(Runner, "Paywall", $(Alt("Pay 1 credit", $(LoseCreditsEffect(Runner, 1)), g.credits(Runner) >= 1), Alt("End the run", $(EndRunEffect))))))
    }

    object FloodTheMarket extends Startup("36053") {
        def n(implicit g : Game) = g.remotes.%(r => g.root(r).any && g.ice(r).any).num
        override def canPlay(self : Card)(implicit g : Game) = doubleCanPlay(Corp) && n > 0 && g.advanceableCards.any
        override def onPlay(self : Card)(implicit g : Game) = doubleCost(Corp) ++ (n > 0).$(ChooseCardEffect(Corp, g.advanceableCards, AdvancePurpose(n), "Flood the Market: choose a card to place " + n + " advancement counters on", false))
    }

    object Scapegoat extends Startup("36054") {
        override def onPlay(self : Card)(implicit g : Game) = $(ChooseOneEffect(Runner, "Scapegoat: the Runner chooses", $(
            Alt("The Corp removes 2 bad publicity", $(BadPubEffect(-2))),
            Alt("Shuffle 1 installed Runner card into the stack (Corp's choice)", $(ChooseCardEffect(Corp, g.installedCards(Runner), ToDeckPurpose(self, Runner, false, true), "Scapegoat: choose an installed Runner card to shuffle into the stack", false)), g.installedCards(Runner).any))))
    }

    object HypeMachine extends Startup("36055") {
        override def rezCostMod(self : Card, card : Card)(implicit g : Game) = (card == self && (g.reg("scored-points") > 0 || g.reg("stolen-points") > 0)).?(-6).|(0)
        override def abilities(self : Card)(implicit g : Game) = {
            val l = g.serverOf(self)./(s => g.root(s).%(c => c != self && g.isAdvanceable(c))).|($)
            $(Ability("place 1 advancement counter on a card in this server", $(TrashSelfCost), $(ChooseCardEffect(Corp, l, AdvancePurpose(1), "Hype Machine: choose a card", false)), ok = g.isRezzed(self) && l.any, reason = "no advanceable card in this server"))
        }
    }

    object SacrificeZoneExpansion extends Startup("36056") {
        override def onInstall(self : Card)(implicit g : Game) = $(SzeFaceupAction(self))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Advanced(c, _) if c == self && g.isInstalled(self) && !g.usedThisTurn(self, "sze") => $(MarkUsedEffect(self, "sze"), gain(Corp, 3))
            case SuccessfulRun(s) if g.isInstalled(self) && !thisServer(self, s) && g.advancement(self) > 0 && !g.usedThisTurn(self, "sze-dmg") =>
                $(OptionalEffect(Corp, "Sacrifice Zone Expansion: remove 1 advancement counter to do 1 meat damage?", $(MarkUsedEffect(self, "sze-dmg"), AddCounterEffect(self, "advancement", -1), meat(1)), $))
            case _ => $
        }
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case SzeFaceupAction(self) =>
                g.state(self).rezzed = true
                g.state(self).seen = true
                g.log(self, "is installed faceup")
                NextAction
        }
    }

    object LuanaCampos extends Startup("36057") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) if g.isRezzed(self) && g.badPublicity > 0 => $(OptionalEffect(Corp, "Luana Campos: host 1 bad publicity to gain 3 credits and draw 1 card?", $(LuanaAction(self)), $))
            case _ => $
        }
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case LuanaAction(self) =>
                if (g.badPublicity > 0) {
                    g.badPublicity -= 1
                    g.hostedBadPub += self -> (g.hostedBadPub.get(self).|(0) + 1)
                    g.log(Corp, "hosts 1 bad publicity on", self)
                    g.push(gain(Corp, 3), draw(Corp, 1))
                }
                NextAction
        }
        override def onTrash(self : Card, by : Faction)(implicit g : Game) = {
            val n = g.hostedBadPub.get(self).|(0)
            g.hostedBadPub -= self
            (n > 0).$(BadPubEffect(n))
        }
    }

    object EventHorizon extends Startup("36058") {
        override def abilities(self : Card)(implicit g : Game) = $(Ability("end the run", $(TrashSelfCost), $(EndRunEffect), ok = g.isRezzed(self) && runOnThisServer(self), reason = "only during a run against this server"))
        override def subroutines(self : Card)(implicit g : Game) = {
            val programs = g.installedCards(Runner).%(_.data.isProgram)
            $(
                Sub("Trash 1 installed program unless the Runner pays 3 credits", ChooseOneEffect(Runner, "Event Horizon", $(
                    Alt("Pay 3 credits", $(LoseCreditsEffect(Runner, 3)), g.credits(Runner) >= 3),
                    Alt("The Corp trashes a program", $(ChooseCardEffect(Corp, programs, TrashPurpose(Corp), "Event Horizon: choose a program to trash", false)))))),
                Sub("End the run unless the Runner pays 3 credits", ChooseOneEffect(Runner, "Event Horizon", $(
                    Alt("Pay 3 credits", $(LoseCreditsEffect(Runner, 3)), g.credits(Runner) >= 3),
                    Alt("End the run", $(EndRunEffect))))))
        }
    }

    object Flywheel extends Startup("36059") {
        override def subroutines(self : Card)(implicit g : Game) = $(Sub("Gain 1 credit. You may draw 1 card", FlywheelAction(self)), Sub("Gain 1 credit. You may draw 1 card", FlywheelAction(self)))
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case FlywheelAction(self) =>
                g.push(gain(Corp, 1), OptionalEffect(Corp, "Flywheel: draw 1 card?", $(draw(Corp, 1)), $))
                NextAction
        }
    }

    object Tocsin extends Startup("36060") {
        override def subroutines(self : Card)(implicit g : Game) = $(loseSub(2), etrSub, etrSub)
        override def inactiveAbilities(self : Card)(implicit g : Game) =
            (g.zone(self) == HandZone(Corp)).$(Ability("Expend: reveal and trash it, search R&D for up to 1 barrier and up to 1 sentry", $(ClickCost(1), CreditCost(1)), $(RevealEffect(Corp, self), TrashEffect(self, Corp, false), TocsinSearchAction(self, "Barrier")), click = true, ok = g.deck(Corp).any, reason = "R&D is empty"))
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case TocsinSearchAction(self, t) =>
                val l = g.deck(Corp).%(c => c.data.isIce && g.has(c, t)).sortBy(_.title)
                if (l.any)
                    g.push(ChooseCardEffect(Corp, l, TocsinPurpose(self), "Tocsin: choose a " + t.toLowerCase + " to add to HQ", true))
                if (t == "Barrier")
                    g.push(TocsinSearchAction(self, "Sentry"))
                else
                    g.push(ShuffleDeckEffect(Corp))
                NextAction
        }
        override def chosenCard(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.foreach { c =>
                g.log(Corp, "reveals", c, "from R&D and adds it to HQ")
                g.moveTo(c, HandZone(Corp))
            }
            NextAction
        }
    }

    object Myoshu extends Startup("36061") {
        override def canPlay(self : Card)(implicit g : Game) = g.reg("scored-points") > 0 && g.lastScoredInstalledTurn != g.turn
        override def onPlay(self : Card)(implicit g : Game) = $(MyoshuAction(self))
        override def agendaPointsBonus(self : Card)(implicit g : Game) = inCorpScore(self).?(2).|(0)
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case MyoshuAction(self) =>
                g.moveTo(self, ScoredZone(Corp))
                g.log(Corp, "adds", self, "to the score area as an agenda worth 2 agenda points")
                g.push(CheckWinAction)
                NextAction
        }
    }

    object ReanimationProtocol extends Startup("36062") {
        override def canPlay(self : Card)(implicit g : Game) = g.discard(Corp).exists(_.data.isIce)
        override def onPlay(self : Card)(implicit g : Game) = $(ChooseCardEffect(Corp, g.discard(Corp).%(_.data.isIce), ReanimationPurpose(self), "Reanimation Protocol: choose a piece of ice in Archives to install and rez", false))
        override def chosenCard(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(c => g.push(ChooseServerForAction(Corp, p.card, g.servers, true, "reanim:" + c.code + ":" + c.n, "Reanimation Protocol: choose a server")))
            NextAction
        }
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case ServerPickedAction(self, server, key) =>
                val parts = key.split(':')
                val c = Card(parts(1), parts(2).toInt)
                val total = g.installCost(c, server) + g.rezCost(c)
                val cost = (total - 10).atLeast(0)
                if (g.credits(Corp) >= cost) {
                    g.credits += Corp -> (g.credits(Corp) - cost)
                    g.log(Corp, "pays", cost.credits, "(10 less)")
                    g.push(CorpInstallEffect(c, server, true), RezEffect(c, true))
                    if (!g.has(c, "Liability") && !g.has(c, "Illicit"))
                        g.push(MessageEffect(Corp, "takes 1 bad publicity (Reanimation Protocol)"), BadPubEffect(1))
                }
                else {
                    g.log(Corp, "cannot afford to rez the ice and only installs it")
                    g.push(CorpInstallEffect(c, server, true))
                }
                NextAction
        }
    }

    object VultureFund extends Startup("36063") {
        override def onPlay(self : Card)(implicit g : Game) = $(gain(Corp, 14), BadPubEffect(1))
    }

    object Flagship extends Startup("36064") {
        override def blocksSuccessfulRun(self : Card, server : Server)(implicit g : Game) = g.isRezzed(self) && thisServer(self, server)
        override def accessFilter(self : Card, cards : $[Card])(implicit g : Game) =
            if (g.isRezzed(self) && g.run.exists(r => thisServer(self, r.server) && r.accessed.exists(_ != self))) cards.%(_ == self) else cards
    }

    object ShackletonGrid extends Startup("36065") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case CreditsSpentFrom(c, n) if g.isRezzed(self) && g.owner(c).isRunner && runOnThisServer(self) && !g.usedThisTurn(self, "shackleton") =>
                $(OptionalEffect(Corp, "Shackleton Grid: do 4 meat damage?", $(MarkUsedEffect(self, "shackleton"), meat(4)), $))
            case _ => $
        }
    }

    object LetThemDream extends Startup("36066") {
        override def agendaPointsBonus(self : Card)(implicit g : Game) = inRunnerScore(self).?(-1).|(0)
        override def onScore(self : Card)(implicit g : Game) = $(ChooseOneEffect(Corp, "Let Them Dream: search for an agenda where?", $(
            Alt("HQ", $(LtdSearchAction(self, "hand")), g.hand(Corp).exists(_.data.isAgenda)),
            Alt("R&D", $(LtdSearchAction(self, "deck")), g.deck(Corp).exists(_.data.isAgenda)),
            Alt("Archives", $(LtdSearchAction(self, "discard")), g.discard(Corp).exists(_.data.isAgenda)),
            Alt("Do not search", $))))
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case LtdSearchAction(self, z) =>
                val l = (z match { case "hand" => g.hand(Corp) ; case "deck" => g.deck(Corp).sortBy(_.title) ; case _ => g.discard(Corp) }).%(_.data.isAgenda)
                if (l.any)
                    g.push(ChooseCardEffect(Corp, l, LtdPurpose(self, z == "deck"), "Let Them Dream: choose an agenda to reveal", true))
                NextAction
        }
        override def chosenCard(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case LtdPurpose(self, fromRd) =>
                cards.single.foreach { c =>
                    g.log(Corp, "reveals", c)
                    g.push(ChooseOneEffect(Corp, "Let Them Dream: add the agenda to HQ or the bottom of R&D?", $(
                        Alt("Add it to HQ", $(AddToHandEffect(c))),
                        Alt("Add it to the bottom of R&D", $(MoveEffect(c, DeckZone(Corp), false))))))
                    if (fromRd)
                        g.push(ShuffleDeckEffect(Corp))
                }
                NextAction
        }
    }

    val all : $[CardImpl] = $(
        ChainReaction, TakeADive, TungstenTailor, Corsair, Lampades, Hackerspace, NurseHanh, StickAndPoke, Vic, Kompromat, SellOut, Tailgate,
        BorrowedGoods, Rotary, Baker, UnderdomeIrregulars, Hiram, Aircheck, BetaBuild, Methuselah, Touchstone, ReadWriteShare, Sipa, Stowaway,
        WordOnTheStreet,
        MeliesCityLuxuryLine, Synchrocyclotron, Ansel2, Reverb, Sleipnir, Vertigo, CaveatEmptor, Realloc, RetirementPlan, PerfectRecall, MeliesU,
        LotusHaze, Esca, Ezam, KnowledgeSeeker, Lionsmane, Vicsek, Cultivate, Unleash, TheRedRoom, EditorialDivision, WitchHunt,
        MagistrateRevontulet, NihiloAgent, Grubber, Lethe, Paywall, FloodTheMarket, Scapegoat, HypeMachine, SacrificeZoneExpansion, LuanaCampos,
        EventHorizon, Flywheel, Tocsin, Myoshu, ReanimationProtocol, VultureFund, Flagship, ShackletonGrid, LetThemDream,
    )
}

case class MeliesRecurAction(card : Card) extends CardAction


// ---------------------------------------------------------------------------------------------------------------------
// Preset decks built around the Startup sets (Elevation + Vantage Point, completed with System Gateway)
// ---------------------------------------------------------------------------------------------------------------------

object StartupDecks {
    val presets : $[Deck] = $(
        Deck("Elevation Anarch (Ryo)", Runner, "35001", $(
            "35003" -> 2, // Charm Offensive
            "35004" -> 2, // Scrounge
            "35005" -> 2, // Shred
            "35006" -> 2, // Bling
            "35007" -> 2, // Gourmand
            "35008" -> 3, // Hantu
            "35009" -> 3, // Rising Tide
            "35010" -> 2, // Cacophony
            "35011" -> 3, // Rent Rioters
            "36001" -> 1, // Chain Reaction
            "36002" -> 2, // Take a Dive
            "36003" -> 2, // The Tungsten Tailor
            "36004" -> 2, // Corsair
            "36005" -> 2, // Lampades
            "36006" -> 2, // Hackerspace
            "36007" -> 2, // Nurse Hanh
            "36008" -> 2, // Stick and Poke
            "35034" -> 3, // Side Hustle
            "36025" -> 2, // Word on the Street
            "30030" -> 3, // Sure Gamble
            "30005" -> 1, // Buzzsaw
            "30026" -> 2, // Unity
        )),
        Deck("Elevation Criminal (MuslihaT)", Runner, "35013", $(
            "35014" -> 3, // Clean Getaway
            "35015" -> 2, // Lie Low
            "35016" -> 2, // Maintenance Access
            "35017" -> 2, // Transfer of Wealth
            "35018" -> 2, // Detente
            "35019" -> 2, // Maglectric Rapid
            "35020" -> 3, // Sang Kancil
            "35021" -> 2, // Fransofia Ward
            "35022" -> 3, // Open Market
            "36010" -> 2, // Kompromat
            "36011" -> 2, // Sell Out
            "36012" -> 2, // Tailgate
            "36013" -> 2, // Borrowed Goods
            "36014" -> 2, // Rotary
            "36015" -> 2, // Baker
            "36016" -> 2, // Underdome Irregulars
            "35034" -> 2, // Side Hustle
            "30030" -> 3, // Sure Gamble
            "30015" -> 2, // Carmen
            "30016" -> 2, // Marjanah
            "36008" -> 1, // Stick and Poke
        )),
        Deck("Elevation Shaper (Dewi)", Runner, "35023", $(
            "35025" -> 2, // Illumination
            "35026" -> 2, // Ritual
            "35027" -> 2, // GAMEDRAGON Pro
            "35028" -> 2, // Madani
            "35029" -> 2, // Azimat
            "35030" -> 2, // Chromatophores
            "35031" -> 3, // Devadatta Drone
            "35032" -> 3, // Principia
            "35033" -> 2, // Knickknack O'Brian
            "36018" -> 2, // Aircheck
            "36019" -> 2, // Beta Build
            "36020" -> 2, // Methuselah
            "36021" -> 2, // Touchstone
            "36022" -> 2, // Read-Write Share
            "36023" -> 3, // Sipa
            "36024" -> 2, // Stowaway
            "35034" -> 2, // Side Hustle
            "30030" -> 3, // Sure Gamble
            "30005" -> 2, // Buzzsaw
            "30025" -> 2, // Echelon
            "36025" -> 1, // Word on the Street
        )),
        Deck("Vantage Point Criminal (Vic)", Runner, "36009", $(
            "36010" -> 3, // Kompromat
            "36011" -> 3, // Sell Out
            "36012" -> 3, // Tailgate
            "36013" -> 3, // Borrowed Goods
            "36014" -> 3, // Rotary
            "36015" -> 2, // Baker
            "36016" -> 3, // Underdome Irregulars
            "35014" -> 3, // Clean Getaway
            "35020" -> 3, // Sang Kancil
            "35022" -> 3, // Open Market
            "36025" -> 2, // Word on the Street
            "35034" -> 3, // Side Hustle
            "30030" -> 3, // Sure Gamble
            "30015" -> 3, // Carmen
            "30016" -> 2, // Marjanah
            "36021" -> 2, // Touchstone
            "36008" -> 1, // Stick and Poke
        )),
        Deck("Elevation HB (Poetri)", Corp, "35036", $(
            "35037" -> 3, // Aggressive Trendsetting
            "35038" -> 3, // Project Ingatan
            "36026" -> 2, // Melies City Luxury Line
            "36066" -> 3, // Let Them Dream
            "35039" -> 2, // Humanoid Resources
            "35040" -> 3, // Otto Campaign
            "36027" -> 1, // Synchrocyclotron
            "35041" -> 3, // Bumi 1.0
            "35042" -> 2, // Scatter Field
            "36028" -> 1, // Ansel 2.0
            "36029" -> 2, // Reverb
            "36030" -> 2, // Sleipnir
            "36031" -> 2, // Vertigo
            "35043" -> 2, // Nanomanagement
            "35044" -> 2, // Top-Down Solutions
            "36032" -> 2, // Caveat Emptor
            "36033" -> 2, // realloc()
            "36034" -> 2, // Retirement Plan
            "35045" -> 1, // Mercia B4LL4RD
            "36035" -> 1, // Perfect Recall
            "35079" -> 2, // Flyswatter
            "35081" -> 2, // Petty Cash
            "30075" -> 3, // Hedge Fund
        )),
        Deck("Elevation Jinteki (AU Co.)", Corp, "35046", $(
            "35048" -> 2, // Proprionegation
            "35049" -> 3, // Sericulture Expansion
            "36037" -> 2, // Lotus Haze
            "36066" -> 3, // Let Them Dream
            "35050" -> 3, // Byte!
            "35051" -> 1, // Phat Gioan Baotixita
            "36038" -> 3, // Esca
            "35052" -> 2, // Empiricist
            "35053" -> 1, // Mycoweb
            "35054" -> 3, // Semak-samun
            "36039" -> 2, // ezaM
            "36040" -> 2, // Knowledge Seeker
            "36041" -> 2, // Lionsmane
            "36042" -> 2, // Vicsek
            "35055" -> 3, // Peer Review
            "36043" -> 2, // Cultivate
            "36044" -> 2, // Unleash
            "35056" -> 1, // Mitra Aman
            "36045" -> 1, // The Red Room
            "35080" -> 2, // Lamplighter
            "35081" -> 2, // Petty Cash
            "30075" -> 3, // Hedge Fund
        )),
        Deck("Elevation NBN (Synapse)", Corp, "35058", $(
            "35059" -> 3, // Embedded Reporting
            "35060" -> 2, // Next Big Thing
            "36047" -> 2, // Witch Hunt
            "36066" -> 2, // Let Them Dream
            "35061" -> 2, // Idiosyncresis
            "35062" -> 3, // Public Access Plaza
            "36048" -> 1, // Magistrate Revontulet
            "36049" -> 2, // Nihilo Agent
            "35063" -> 3, // Doomscroll
            "35064" -> 2, // N-Pot
            "36050" -> 2, // Grubber
            "36051" -> 1, // Lethe
            "36052" -> 3, // Paywall
            "35065" -> 2, // Bigger Picture
            "35066" -> 1, // IP Enforcement
            "35067" -> 2, // Touch-ups
            "36053" -> 2, // Flood the Market
            "36054" -> 2, // Scapegoat
            "36055" -> 1, // Hype Machine
            "35079" -> 2, // Flyswatter
            "35082" -> 2, // Mahkota Langit Grid
            "30075" -> 3, // Hedge Fund
        )),
        Deck("Elevation Weyland (Zwicky)", Corp, "35069", $(
            "36056" -> 3, // Sacrifice Zone Expansion
            "35070" -> 3, // Greenmail
            "35071" -> 3, // Off the Books
            "36066" -> 3, // Let Them Dream
            "35072" -> 3, // Anthill Excavation Contract
            "35073" -> 1, // Plutus
            "36057" -> 2, // Luana Campos
            "35074" -> 1, // Biawak
            "35075" -> 3, // Kessleroid
            "35076" -> 2, // Syailendra
            "36058" -> 2, // Event Horizon
            "36059" -> 2, // Flywheel
            "36060" -> 2, // Tocsin
            "35077" -> 2, // Key Performance Indicators
            "35078" -> 1, // Measured Response
            "36061" -> 1, // Myoshu
            "36062" -> 2, // Reanimation Protocol
            "36063" -> 2, // Vulture Fund
            "36064" -> 1, // Flagship
            "36065" -> 1, // Shackleton Grid
            "35080" -> 2, // Lamplighter
            "35081" -> 2, // Petty Cash
            "30075" -> 3, // Hedge Fund
        )),
    )
}
