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
// Rotated / FFG-era Resource cards (part 1: A - L), ported from jinteki.net src/clj/game/cards/resources.clj
// ---------------------------------------------------------------------------------------------------------------------

// card specific actions and purposes
case class AlgoTradingAction(card : Card) extends CardAction
case class AngelArenaAction(card : Card) extends CardAction
case class AngelArenaRevealAction(card : Card) extends CardAction
case class ArtistColonyPurpose(card : Card) extends CardPurpose
case class BankJobAction(card : Card) extends CardAction
case class BazaarPurpose(card : Card) extends CardPurpose
case class BethAction(card : Card) extends CardAction
case class BlockadeRunnerAction(card : Card) extends CardAction
case class BugOutBagAction(card : Card) extends CardAction
case class CharlatanPayAction(card : Card, ice : Card) extends CardAction
case class ChatterjeePurpose(card : Card) extends CardPurpose
case class CitadelAction(card : Card) extends CardAction
case class ClanVengeanceAction(card : Card) extends CardAction
case class CouncilmanAction(card : Card, target : Card) extends CardAction
case class CounterSurveillanceAction(card : Card) extends CardAction
case class CounterSurveillancePurpose(card : Card, server : Server) extends CardPurpose
case class CryptPurpose(card : Card) extends CardPurpose
case class DadianaAction(card : Card) extends CardAction
case class DDoSAction(card : Card) extends CardAction
case class DistrictPurpose(card : Card) extends CardPurpose
case class FenrisPurpose(card : Card) extends CardPurpose
case class FenrisTrashAction(card : Card) extends CardAction
case class LovegoodPurpose(card : Card) extends CardPurpose
case class EnhancedVisionAction(card : Card) extends CardAction
case class GuruAction(card : Card) extends CardAction
case class HuntingGroundsAction(card : Card) extends CardAction
case class InezAction(card : Card, n : Int) extends CardAction
case class JackpotAction(card : Card) extends CardAction
case class JarogniewAction(card : Card) extends CardAction
case class LevyAction(card : Card) extends CardAction
case class LevyPurpose(card : Card, revealed : $[Card]) extends CardPurpose
case class LevyBottomAction(card : Card, cards : $[Card]) extends CardAction
case class LewiAction(card : Card) extends CardAction
case class ChelaAction(card : Card) extends CardAction
case class ChelaForfeitPurpose(card : Card) extends CardPurpose
case class LondonLibraryPurpose(card : Card) extends CardPurpose
case class AeneasCheckAction(card : Card, target : Card) extends CardAction
case class AssimilatorPurpose(card : Card) extends CardPurpose
case class FanSiteAction(card : Card) extends CardAction


object RotResourceCards1 {

    object AaronMarron extends RR("11106") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case AgendaScored(_) => $(AddCounterEffect(self, "power", 2))
            case AgendaStolen(_) => $(AddCounterEffect(self, "power", 2))
            case _ => $
        }
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("remove 1 tag and draw 1 card", $(CounterCost("power", 1)), $(RemoveTagsEffect(1), draw(Runner, 1))))
    }

    object AccessToGlobalsec extends RR("01052") {
        override def link(self : Card)(implicit g : Game) = 1
    }

    object ActivistSupport extends RR("04062") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) if g.tags == 0 => $(MessageEffect(Runner, "takes 1 tag (Activist Support)"), tag(1))
            case TurnBegins(Runner) if g.badPublicity == 0 => $(MessageEffect(Runner, "gives the Corp 1 bad publicity (Activist Support)"), BadPubEffect(1))
            case _ => $
        }
    }

    object AdjustedChronotype extends RR("08003") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case ClickLost(Runner, _) => geneticsMark(self, "chronotype").$./(m => $(m, MessageEffect(Runner, "gains [click] (Adjusted Chronotype)"), GainClicksEffect(Runner, 1))).flatten
            case _ => $
        }
    }

    object AeneasInformant extends RR("12044") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case AccessFinished(c) if c.data.trashCost.any && g.location.contains(c) && !g.zone(c).is[DiscardZone] => $(AeneasCheckAction(self, c))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case AeneasCheckAction(self, c) =>
                if (g.zone(c) != DiscardZone(Corp) && g.isInstalled(self))
                    g.push(OptionalEffect(Runner, "Aeneas Informant: reveal the accessed card and gain 1 credit?", $(MessageEffect(Runner, "uses Aeneas Informant to reveal the accessed card and gain 1 credit")) ++ (!g.isInstalled(c)).$(RevealCardsEffect(Corp, $(c))) :+ gain(Runner, 1), $))
                NextAction
        }
    }

    object AksharaSareen extends RR("10046") {
        override def clicksPerTurn(self : Card, f : Faction)(implicit g : Game) = 1
    }

    object AlgoTrading extends RR("11029") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) =>
                (g.credits(Runner) > 0).$(ChooseNumberEffect(Runner, self, 0, 3.atMost(g.credits(Runner)), "algo", "Algo Trading: move how many credits to Algo Trading?")) ++
                (g.counter(self, "credit") >= 6).$(MessageEffect(Runner, "places 2 credits on Algo Trading"), AddCounterEffect(self, "credit", 2))
            case _ => $
        }
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("take all hosted credits (" + g.counter(self, "credit") + ")", $(ClickCost(1), TrashSelfCost), $(gain(Runner, g.counter(self, "credit"))), click = true, ok = g.counter(self, "credit") > 0, reason = "no hosted credits"))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case NumberPickedAction(self, n, "algo") =>
                if (n > 0)
                    g.push(LoseCreditsEffect(Runner, n), AddCounterEffect(self, "credit", n))
                NextAction
        }
    }

    object AllNighter extends RR("20053") {
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("gain [click][click]", $(ClickCost(1), TrashSelfCost), $(GainClicksEffect(Runner, 2)), click = true))
    }

    object AlwaysBeRunning extends RR("09041") {
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("break 1 subroutine", $(LoseClickCost(2)), encountering.$./(BioroidBreakEffect(_)), once = once("abr"), ok = encountering.exists(i => g.unbrokenSubs(i).any), reason = "not encountering ice"))
    }

    object AngelArena extends RR("06080") {
        override def onInstall(self : Card)(implicit g : Game) = $(ChooseNumberEffect(Runner, self, 0, g.credits(Runner), "arena", "Angel Arena: spend how many credits (1 power counter each)?"))
        override def triggers(self : Card, e : Event)(implicit g : Game) = trashOnEmpty(self, e, "power")
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("reveal the top card of the stack, may add it to the bottom", $(CounterCost("power", 1)), $(AngelArenaRevealAction(self)), ok = g.deck(Runner).any, reason = "empty stack"))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case NumberPickedAction(self, n, "arena") =>
                if (n > 0)
                    g.push(LoseCreditsEffect(Runner, n), AddCounterEffect(self, "power", n))
                else
                    g.push(TrashEffect(self, Runner, false))
                NextAction
            case AngelArenaRevealAction(self) =>
                g.deck(Runner).starting.foreach { c =>
                    g.push(RevealCardsEffect(Runner, $(c)), OptionalEffect(Runner, "Angel Arena: add the revealed card to the bottom of the stack?", $(MoveEffect(c, DeckZone(Runner), false)), $))
                }
                NextAction
        }
    }

    object ArmitageCodebusting extends RR("25062") {
        override def initialCounters(self : Card)(implicit g : Game) = Map("credit" -> 12)
        override def triggers(self : Card, e : Event)(implicit g : Game) = trashOnEmpty(self, e, "credit")
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("take 2 credits", click(), $(TakeCreditsEffect(Runner, self, 2)), click = true))
    }

    object ArtistColony extends RR("10009") {
        override def abilities(self : Card)(implicit g : Game) = {
            val l = g.deck(Runner).%(c => !c.data.isEvent)
            $(Ability("forfeit an agenda: search the stack for a card and install it", NoCost, $(forfeitChoice(self, "Artist Colony: choose an agenda to forfeit"), ChooseCardEffect(Runner, l.sortBy(_.title), ArtistColonyPurpose(self), "Artist Colony: choose a card to install", true), ShuffleDeckEffect(Runner)), ok = runnerAgendas.any && l.any, reason = "no agenda to forfeit"))
        }
        override def chosenCard(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(c => g.pushAll(g.installRunner(c, 0)))
            NextAction
        }
    }

    object Assimilator extends RR("21008") {
        override def abilities(self : Card)(implicit g : Game) = {
            val l = g.rig.%(c => g.state(c).facedown)
            $(Ability("turn a facedown installed card faceup", $(ClickCost(2)), $(ChooseCardEffect(Runner, l, RotFlipFaceupPurpose(self), "Assimilator: choose a facedown card", false)), click = true, ok = l.any, reason = "no facedown cards"))
        }
    }

    object BankJob extends RR("25038") {
        override def initialCounters(self : Card)(implicit g : Game) = Map("credit" -> 8)
        override def triggers(self : Card, e : Event)(implicit g : Game) = trashOnEmpty(self, e, "credit") ++ (e match {
            case SuccessfulRun(s) if s.isRemote && g.run.exists(r => !r.ended && !r.preventAccess) =>
                $(OptionalEffect(Runner, "Bank Job: take hosted credits instead of breaching the server?", $(ReplaceBreachEffect, BankJobAction(self)), $))
            case _ => $
        })
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case BankJobAction(self) =>
                g.push(ChooseNumberEffect(Runner, self, 0, g.counter(self, "credit"), "bankjob", "Bank Job: take how many credits?"))
                NextAction
            case NumberPickedAction(self, n, "bankjob") =>
                if (n > 0)
                    g.push(TakeCreditsEffect(Runner, self, n))
                NextAction
        }
    }

    object Bazaar extends RR("10065") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Installed(Runner, c) if c.data.isHardware && g.rhInstallFrom.get(c).has(HandZone(Runner)) && g.hand(Runner).exists(x => x.title == c.title && g.runnerInstallableNow(x, 0)) =>
                $(OptionalEffect(Runner, "Bazaar: install another copy of " + g.promptTitle(c) + " from the grip?", $(MessageEffect(Runner, "uses Bazaar to install another copy"), ChooseCardEffect(Runner, g.hand(Runner).%(x => x.title == c.title), BazaarPurpose(self), "Bazaar: choose a copy to install", false)), $))
            case _ => $
        }
        override def chosenCard(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(c => g.pushAll(g.installRunner(c, 0)))
            NextAction
        }
    }

    object BeachParty extends RR("08031") {
        override def handSize(self : Card, f : Faction)(implicit g : Game) = f.isRunner.?(5).|(0)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) => $(MessageEffect(Runner, "loses [click] (Beach Party)"), ClickLossEffect(Runner, 1))
            case _ => $
        }
    }

    object BethKilrainChang extends RR("11030") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) if g.credits(Corp) >= 5 => $(BethAction(self))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case BethAction(self) =>
                val c = g.credits(Corp)
                if (c >= 15) g.push(MessageEffect(Runner, "gains [click] (Beth Kilrain-Chang)"), GainClicksEffect(Runner, 1))
                else if (c >= 10) g.push(MessageEffect(Runner, "draws 1 card (Beth Kilrain-Chang)"), draw(Runner, 1))
                else if (c >= 5) g.push(MessageEffect(Runner, "gains 1 credit (Beth Kilrain-Chang)"), gain(Runner, 1))
                NextAction
        }
    }

    object Bhagat extends RR("10098") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(HQ) if firstSuccessfulRunOn(HQ) => $(MessageEffect(Runner, "forces the Corp to trash the top card of R&D (Bhagat)"), MillEffect(Corp, 1))
            case _ => $
        }
    }

    object BioModeledNetwork extends RR("12006") {
        override def preventions(self : Card, kind : String, n : Int)(implicit g : Game) =
            (kind == "net" && n > 1).$(Ability("prevent all but 1 net damage", $(TrashSelfCost), $(Prevent.damage(n - 1))))
    }

    object BiometricSpoofing extends RR("13026") {
        override def preventions(self : Card, kind : String, n : Int)(implicit g : Game) =
            (kind == "net" || kind == "meat" || kind == "core").$(Ability("prevent 2 " + kind + " damage", $(TrashSelfCost), $(Prevent.damage(2))))
    }

    object BlockadeRunner extends RR("11065") {
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("draw 3 cards and shuffle 1 card from the grip into the stack", $(ClickCost(2)), $(draw(Runner, 3), BlockadeRunnerAction(self)), click = true))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case BlockadeRunnerAction(self) =>
                if (g.hand(Runner).any)
                    g.push(ChooseCardEffect(Runner, g.hand(Runner), ShuffleIntoDeckPurpose(Runner), "Blockade Runner: choose a card to shuffle into the stack", false))
                NextAction
        }
    }

    object BlooMoose extends RR("12089") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) if g.discard(Runner).any =>
                $(ChooseCardEffect(Runner, g.discard(Runner), RotRfgGainPurpose(self, 2), "Bloo Moose: remove a card in the heap from the game to gain 2 credits?", true))
            case _ => $
        }
    }

    object BorrowedSatellite extends RR("03050") {
        override def link(self : Card)(implicit g : Game) = 1
        override def handSize(self : Card, f : Faction)(implicit g : Game) = f.isRunner.?(1).|(0)
    }

    object BugOutBag extends RR("12064") {
        override def onInstall(self : Card)(implicit g : Game) = $(ChooseNumberEffect(Runner, self, 0, g.credits(Runner), "bob", "Bug Out Bag: spend how many credits (1 power counter each)?"))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnEnds(Runner) if g.hand(Runner).none => $(BugOutBagAction(self))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case NumberPickedAction(self, n, "bob") =>
                if (n > 0)
                    g.push(LoseCreditsEffect(Runner, n), AddCounterEffect(self, "power", n))
                NextAction
            case BugOutBagAction(self) =>
                val n = g.counter(self, "power")
                g.push(MessageEffect(Runner, "draws " + n + " cards and trashes Bug Out Bag"), draw(Runner, n), TrashEffect(self, Runner, false))
                NextAction
        }
    }

    object Caldera extends RR("12105") {
        override def preventions(self : Card, kind : String, n : Int)(implicit g : Game) =
            (kind == "net" || kind == "core").$(Ability("prevent 1 " + kind + " damage", c(3), $(Prevent.damage(1))))
    }

    object Charlatan extends RR("13010") {
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("run any server (pay to bypass the first rezzed ice approached)", $(ClickCost(2)), $(SetTargetEffect(self, None), RunAnyServerEffect(|(self), g.servers)), click = true))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case ApproachIce(ice) if thisCardRun(self) && g.isRezzed(ice) && !g.state(self).usedRun.has("charlatan") =>
                val str = g.iceStrength(ice)
                $(MarkUsedRunEffect(self, "charlatan")) ++ (g.credits(Runner) >= str).$(OptionalEffect(Runner, "Charlatan: pay " + str + " credits to bypass " + g.promptTitle(ice) + " when encountered?", $(CharlatanPayAction(self, ice)), $))
            case EncounterIce(ice) if thisCardRun(self) && g.state(self).target.has(ice) => $(SetTargetEffect(self, None), BypassEffect)
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case CharlatanPayAction(self, ice) =>
                val str = g.iceStrength(ice)
                if (g.credits(Runner) >= str) {
                    g.log(Runner, "uses", self, "to pay", str.credits, "to bypass", ice)
                    g.push(LoseCreditsEffect(Runner, str), SetTargetEffect(self, |(ice)))
                }
                else
                    g.log(Runner, "cannot afford to pay for", ice)
                NextAction
        }
    }

    object ChatterjeeUniversity extends RR("10010") {
        override def abilities(self : Card)(implicit g : Game) = {
            val d = g.counter(self, "power")
            val l = g.hand(Runner).%(c => c.data.isProgram && g.runnerInstallableNow(c, d))
            $(Ability("place 1 power counter", click(), $(AddCounterEffect(self, "power", 1)), click = true),
              Ability("install a program from the grip paying " + d + " less", click(), $(ChooseCardEffect(Runner, l, ChatterjeePurpose(self), "Chatterjee University: choose a program to install", false)), click = true, ok = l.any, reason = "nothing to install"))
        }
        override def chosenCard(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            val d = g.counter(p.card, "power")
            cards.single.foreach { c =>
                g.pushAll(g.installRunner(c, d) ++ (d > 0).$(AddCounterEffect(p.card, "power", -1)))
            }
            NextAction
        }
    }

    object ChromeParlor extends RR("08044") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case PreventionInterrupt(k, 1, n) if g.damageKinds.has(k) && Prevent.damageCtx.exists(_.source.exists(c => g.has(c, "Cybernetic"))) =>
                $(MessageEffect(Runner, "prevents " + n + " " + k + " damage from a cybernetic card (Chrome Parlor)"), Prevent.allDamage)
            case _ => $
        }
    }

    object CitadelSanctuary extends RR("11070") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnEnds(Runner) if g.tagged => $(MessageEffect(Runner, "forces the Corp to trace (Citadel Sanctuary)"), TraceEffect(1, $, $(RemoveTagsEffect(1))))
            case _ => $
        }
        override def preventions(self : Card, kind : String, n : Int)(implicit g : Game) =
            (kind == "meat").$(Ability("trash all cards in the grip: prevent all meat damage", $(TrashSelfCost), $(CitadelAction(self), Prevent.allDamage)))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case CitadelAction(self) =>
                val h = g.hand(Runner)
                h.foreach(c => g.moveTo(c, DiscardZone(Runner)))
                if (h.any)
                    g.log(Runner, "trashes", h.num.cards, "from the grip")
                NextAction
        }
    }

    object ClanVengeance extends RR("12022") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case DamageDone(_, n) if n > 0 => $(AddCounterEffect(self, "power", 1))
            case _ => $
        }
        override def abilities(self : Card)(implicit g : Game) = {
            val n = g.counter(self, "power")
            $(Ability("the Corp trashes " + n + " random cards from HQ", $(TrashSelfCost), $(RandomTrashFromHandEffect(Corp, n)), ok = n > 0 && g.hand(Corp).any, reason = "no power counters"))
        }
    }

    object CompromisedEmployee extends RR("02025") {
        override def initialCounters(self : Card)(implicit g : Game) = Map("credit" -> 1)
        // "Use this credit during traces."
        override def paysFor(self : Card, spend : Spend)(implicit g : Game) = spend.is("trace")
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) => $(RefillCreditsEffect(self, 1))
            case Rezzed(c) if c.data.isIce => $(MessageEffect(Runner, "gains 1 credit (Compromised Employee)"), gain(Runner, 1))
            case _ => $
        }
        override def preventions(self : Card, kind : String, n : Int)(implicit g : Game) =
            (kind == "trace" && g.counter(self, "credit") > 0).$(Ability("take the hosted credit for the trace", NoCost, $(AddCounterEffect(self, "credit", -1), gain(Runner, 1), TraceCorpBoostAction(g.traceBase, g.traceSuccess, g.traceFail))))
    }

    object CorporateDefector extends RR("12109") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case ClickDraw(Corp) if g.deck(Corp).any => $(MessageEffect(Runner, "forces the Corp to reveal the drawn card (Corporate Defector)"), RevealCardsEffect(Corp, g.deck(Corp).take(1)))
            case _ => $
        }
    }

    object Councilman extends RR("10047") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Rezzed(c) if (c.data.isAsset || c.data.isUpgrade) && g.credits(Runner) >= g.rezCost(c) =>
                $(OptionalEffect(Runner, "Councilman: pay " + g.rezCost(c) + " credits and trash Councilman to derez " + g.promptTitle(c) + "?", $(CouncilmanAction(self, c)), $))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case CouncilmanAction(self, c) =>
                val cost = g.rezCost(c)
                if (g.credits(Runner) >= cost && g.isInstalled(self)) {
                    g.log(Runner, "uses", self, "to derez", c)
                    g.push(LoseCreditsEffect(Runner, cost), TrashEffect(self, Runner, false), DerezEffect(c), MarkUsedEffect(c, "cannot-rez"))
                }
                NextAction
        }
    }

    object CounterSurveillance extends RR("12023") {
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("run any server; if successful access X cards instead of breaching (X = tags)", $(ClickCost(1), TrashSelfCost), $(RunAnyServerEffect(|(self), g.servers)), click = true))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case CounterSurveillanceAction(self) =>
                g.run.foreach { r =>
                    val x = g.tags
                    g.push(ReplaceBreachEffect)
                    if (g.credits(Runner) >= x) {
                        g.log(Runner, "pays", x.credits, "(Counter Surveillance)")
                        g.push(LoseCreditsEffect(Runner, x), ChooseNumberEffect(Runner, self, 0, x, "cs", "Counter Surveillance: access how many cards?"))
                    }
                    else
                        g.log(Runner, "cannot afford to use Counter Surveillance")
                }
                NextAction
            case NumberPickedAction(self, n, "cs") =>
                g.run.foreach { r =>
                    if (n > 0)
                        r.server match {
                            case HQ => g.push(AccessRandomFromHQEffect(n, false))
                            case RnD => g.push(AccessCardsEffect(RnD, g.deck(Corp).take(n), false))
                            case Archives =>
                                g.discard(Corp).foreach(c => g.state(c).seen = true)
                                g.push(AccessCardsEffect(Archives, g.discard(Corp).sortBy(_.title).take(n), false))
                            case s => g.push(AccessCardsEffect(s, g.root(s)./~(c => c +: g.allHosted(c)).take(n), false))
                        }
                }
                NextAction
        }
    }

    object CrashSpace extends RR("20034") {
        override def initialCounters(self : Card)(implicit g : Game) = Map("credit" -> 2)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) => $(RefillCreditsEffect(self, 2))
            case _ => $
        }
        // "You can spend hosted credits to take the basic action to remove 1 tag."
        override def paysFor(self : Card, spend : Spend)(implicit g : Game) = spend.is("remove-tag")
        override def preventions(self : Card, kind : String, n : Int)(implicit g : Game) =
            (kind == "meat").$(Ability("prevent up to 3 meat damage", $(TrashSelfCost), $(Prevent.damage(3))))
    }

    object Crowdfunding extends RR("28002") {
        override def initialCounters(self : Card)(implicit g : Game) = Map("credit" -> 3)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) if g.counter(self, "credit") > 0 => $(TakeCreditsEffect(Runner, self, 1))
            case CounterAdded(c, "credit") if c == self && g.counter(self, "credit") == 0 && g.isInstalled(self) => $(TrashEffect(self, Runner, false), draw(Runner, 1))
            case _ => $
        }
    }

    object Crypt extends RR("21043") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(Archives) => $(OptionalEffect(Runner, "Crypt: place 1 virus counter on it?", $(AddCounterEffect(self, "virus", 1)), $))
            case _ => $
        }
        override def abilities(self : Card)(implicit g : Game) = {
            val l = g.deck(Runner).%(c => c.data.isProgram && g.has(c, "Virus")).sortBy(_.title)
            $(Ability("install a virus program from the stack", $(ClickCost(1), VirusCost(3), TrashSelfCost), $(ChooseCardEffect(Runner, l, CryptPurpose(self), "Crypt: choose a virus program to install", true), ShuffleDeckEffect(Runner)), click = true, ok = l.any, reason = "no virus program in the stack"))
        }
        override def chosenCard(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(c => g.pushAll(g.installRunner(c, 0)))
            NextAction
        }
    }

    object DadianaChacon extends RR("12049") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = {
            val l : $[ForcedAction] = e match {
                case TurnBegins(Runner) if g.credits(Runner) < 6 => $(MessageEffect(Runner, "gains 1 credit (Dadiana Chacon)"), gain(Runner, 1))
                case _ => $
            }
            l ++ (creditsCheckpoint(e) && g.credits(Runner) == 0 && g.isInstalled(self)).$(DadianaAction(self))
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case DadianaAction(self) =>
                if (g.isInstalled(self) && g.credits(Runner) == 0) {
                    g.log(Runner, "has no credits:", self, "is trashed")
                    g.push(TrashEffect(self, Runner, false), meat(3))
                }
                NextAction
        }
    }

    object DataDealer extends RR("25039") {
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("forfeit an agenda: gain 9 credits", click(), $(forfeitChoice(self, "Data Dealer: choose an agenda to forfeit"), gain(Runner, 9)), click = true, ok = runnerAgendas.any, reason = "no agenda to forfeit"))
    }

    object DataFolding extends RR("07055") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) if g.memoryFree >= 2 => $(MessageEffect(Runner, "gains 1 credit (Data Folding)"), gain(Runner, 1))
            case _ => $
        }
    }

    object DataLeakReversal extends RR("02103") {
        override def canInstall(self : Card)(implicit g : Game) = g.successfulRunsThisTurn.exists(_.isCentral)
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("the Corp trashes the top card of R&D", click(), $(MillEffect(Corp, 1)), click = true, ok = g.tagged && g.deck(Corp).any, reason = "not tagged"))
    }

    object DDoS extends RR("08103") {
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("the Corp cannot rez the outermost ice of each server this turn", $(TrashSelfCost), $(DDoSAction(self))))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case DDoSAction(self) =>
                g.servers./~(s => g.ice(s).lastOption).%(c => !g.isRezzed(c)).foreach(c => g.markUsed(c, "cannot-rez"))
                g.log("The Corp cannot rez the outermost ice protecting any server this turn")
                NextAction
        }
    }

    object DeanLister extends RR("13025") {
        override def abilities(self : Card)(implicit g : Game) = {
            val l = g.installedCards(Runner).%(g.isIcebreaker)
            $(Ability("give an icebreaker +1 strength per card in the grip until the end of the run", $(TrashSelfCost), $(ChooseCardEffect(Runner, l, RotPumpPurpose(self, g.hand(Runner).num, true), "Dean Lister: choose an icebreaker", false)), ok = l.any && activeRun, reason = "no run or no icebreaker"))
        }
    }

    object Decoy extends RR("01032") {
        override def preventions(self : Card, kind : String, n : Int)(implicit g : Game) =
            (kind == "tag").$(Ability("avoid 1 tag", $(TrashSelfCost), $(Prevent.tags(1))))
    }

    object District99 extends RR("22007") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Trashed(c, _, _) if (c.data.isProgram || c.data.isHardware) && unused(self, "d99") => $(mark(self, "d99"), OptionalEffect(Runner, "District 99: place 1 power counter on it?", $(AddCounterEffect(self, "power", 1)), $))
            case _ => $
        }
        override def abilities(self : Card)(implicit g : Game) = {
            val faction = g.identity.get(Runner)./(_.data.faction).|("")
            val l = g.discard(Runner).%(_.data.faction == faction)
            $(Ability("add a card of your faction from the heap to the grip", $(ClickCost(1), CounterCost("power", 3)), $(ChooseCardEffect(Runner, l, AddToHandPurpose(Runner, false), "District 99: choose a card to add to the grip", false)), click = true, ok = l.any, reason = "no matching card in the heap"))
        }
    }

    object DJFenris extends RR("22025") {
        val gmods = $("30001", "31001", "31002", "31014", "33011", "35001", "34066")
        override def onInstall(self : Card)(implicit g : Game) = {
            val faction = g.identity.get(Runner)./(_.data.faction).|("")
            val l = gmods.%(c => CardImpls.has(c) && Cards.get(c).exists(d => d.faction != faction && d.has("G-mod")))
            l.any.$(ChooseCodeEffect(Runner, l, "DJ Fenris: choose a g-mod identity to host", FenrisPurpose(self)))
        }
        override def chosenCard(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach { c =>
                g.place(c, HostedZone(p.card))
                g.state(c).seen = true
                g.log(c, "is hosted on", p.card)
            }
            NextAction
        }
        override def onTrash(self : Card, by : Faction)(implicit g : Game) = $(FenrisTrashAction(self))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case FenrisTrashAction(self) =>
                g.discard(Runner).%(c => c.n == 0 && c.data.isIdentity).foreach(c => g.push(RemoveFromGameEffect(c)))
                NextAction
        }
    }

    object DonutTaganes extends RR("05055") {
        override def playCostMod(self : Card, card : Card)(implicit g : Game) = (card.data.isOperation || card.data.isEvent).?(1).|(0)
    }

    object DrLovegood extends RR("09042") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) =>
                val l = g.installedCards(Runner).but(self)
                l.any.$(ChooseCardEffect(Runner, l, LovegoodPurpose(self), "Dr. Lovegood: choose an installed card to blank for the turn?", true))
            case _ => $
        }
        override def chosenCard(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach { c =>
                g.log(c, "loses its printed abilities for the remainder of the turn (Dr. Lovegood)")
                g.push(AddFlagEffect(Flag("disabled", |(c), None, "turn", 0, $)))
            }
            NextAction
        }
    }

    object DrugDealer extends RR("08083") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) => $(MessageEffect(Runner, "loses 1 credit (Drug Dealer)"), lose(Runner, 1))
            case TurnBegins(Corp) => $(MessageEffect(Runner, "draws 1 card (Drug Dealer)"), draw(Runner, 1))
            case _ => $
        }
    }

    object Duggars extends RR("06054") {
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("draw 10 cards", $(ClickCost(4)), $(draw(Runner, 10)), click = true, ok = g.deck(Runner).any, reason = "empty stack"))
    }

    object DummyBox extends RR("12108") {
        override def preventions(self : Card, kind : String, n : Int)(implicit g : Game) =
            (kind == "trash").??(Prevent.ctx.%(_.by.isCorp).$./~(x => $("Hardware", "Program", "Resource").%(t => x.cards.exists(_.data.kind == t) && g.hand(Runner).exists(_.data.kind == t))./(t =>
                Ability("trash a " + t.toLowerCase + " from the grip to prevent the Corp from trashing an installed " + t.toLowerCase, NoCost, $(ChooseCardEffect(Runner, g.hand(Runner).%(_.data.kind == t), PrevDummyBoxPurpose(self, t), "Dummy Box: choose a " + t.toLowerCase + " in the grip to trash", false))))))
        override def chosenCard(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case PrevDummyBoxPurpose(self, t) =>
                cards.foreach { c =>
                    g.moveTo(c, DiscardZone(Runner))
                    g.log(Runner, "trashes", c, "from the grip")
                }
                if (cards.any)
                    g.push(PreventTrashChooseEffect(self, $(t)))
                NextAction
        }
    }

    abstract class Shard(code : String, val server : Server) extends RR(code) {
        def shardEffect(self : Card)(implicit g : Game) : $[ForcedAction]
        def shardLabel : String
        override def abilities(self : Card)(implicit g : Game) = $(Ability(shardLabel, $(TrashSelfCost), shardEffect(self)))
    }

    object EdenShard extends Shard("06020", RnD) {
        def shardLabel = "the Corp draws 2 cards"
        def shardEffect(self : Card)(implicit g : Game) = $(draw(Corp, 2))
    }

    object EmptiedMind extends RR("10078") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) if g.hand(Runner).none => $(MessageEffect(Runner, "gains [click] (Emptied Mind)"), GainClicksEffect(Runner, 1))
            case _ => $
        }
    }

    object EnhancedVision extends RR("08005") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(_) => geneticsMark(self, "vision").$./~(m => $(m, EnhancedVisionAction(self)))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case EnhancedVisionAction(self) =>
                if (g.hand(Corp).any) {
                    g.log(Runner, "uses", self, "to force the Corp to reveal a random card from HQ")
                    g.push(RandomPickEffect(Corp, g.hand(Corp), 1, RotRevealPurpose(self)))
                }
                NextAction
        }
    }

    object FallGuy extends RR("20035") {
        override def abilities(self : Card)(implicit g : Game) = $(Ability("gain 2 credits", $(TrashSelfCost), $(gain(Runner, 2))))
        override def preventions(self : Card, kind : String, n : Int)(implicit g : Game) =
            (kind == "trash" && Prevent.ctx.exists(_.cards.exists(c => c != self && c.data.isResource))).$(Ability("prevent another installed resource from being trashed", $(TrashSelfCost), $(PreventTrashChooseEffect(self, $("Resource")))))
    }

    object FanSite extends RR("08085") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case AgendaScored(_) if g.isInstalled(self) => $(MessageEffect(Runner, "adds Fan Site to the score area"), AddToScoreAreaEffect(self, Runner))
            case _ => $
        }
    }

    object Fester extends RR("06075") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Purged(_) if g.credits(Corp) >= 2 => $(MessageEffect(Runner, "forces the Corp to lose 2 credits (Fester)"), lose(Corp, 2))
            case _ => $
        }
    }

    object FilmCritic extends RR("08086") {
        def hostedAgenda(self : Card)(implicit g : Game) = g.hosted(self).%(_.data.isAgenda)
        override def accessAbility(self : Card, target : Card)(implicit g : Game) =
            (target.data.isAgenda && hostedAgenda(self).none && g.run.exists(!_.cannotStealOrTrash)).?(Ability("host the accessed agenda on Film Critic", NoCost, $(HostOnEffect(target, self, true))))
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("add the hosted agenda to your score area", $(ClickCost(2)), hostedAgenda(self)./(c => AddToScoreAreaEffect(c, Runner)), click = true, ok = hostedAgenda(self).any, reason = "no hosted agenda"))
    }

    object FindTheTruth extends RR("11047") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case CardsDrawn(Runner, l) => $(RevealCardsEffect(Runner, l))
            case SuccessfulRun(_) if g.reg("successful-run") == 1 && g.deck(Corp).any =>
                $(OptionalEffect(Runner, "Find the Truth: look at the top card of R&D?", $(LookAtEffect(Runner, g.deck(Corp).take(1), "Find the Truth: the top card of R&D")), $))
            case _ => $
        }
    }

    object FirstResponders extends RR("11048") {
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("draw 1 card", c(2), $(draw(Runner, 1)), ok = g.runnerDamageThisTurn > 0 && g.deck(Runner).any, reason = "no damage suffered this turn"))
    }

    object GangSign extends RR("08067") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case AgendaScored(_) if g.isInstalled(self) => $(MessageEffect(Runner, "breaches HQ (Gang Sign)"), BreachNoRootEffect(HQ))
            case _ => $
        }
    }

    abstract class BiteyBoi(code : String, val last : Boolean) extends RR(code) {
        override def abilities(self : Card)(implicit g : Game) = {
            val target = encountering./~(ice => (last.?(g.unbrokenSubs(ice).lastOption).|(g.unbrokenSubs(ice).headOption))./(i => (ice, i)))
            $(Ability("break the " + last.?("last").|("first") + " subroutine on the encountered ice", $(TrashSelfCost), target.$./((ice, i) => BreakSubIndexEffect(ice, i, self)), ok = target.any, reason = "not encountering ice with unbroken subroutines"))
        }
    }

    object Gbahali extends BiteyBoi("21047", true)

    object GeneConditioningShoppe extends RR("08006") {
        // Genetics check for this card through RR.shoppe
    }

    object GhostRunner extends RR("06040") {
        override def initialCounters(self : Card)(implicit g : Game) = Map("credit" -> 3)
        // "You can use the credits on Ghost Runner during a run."
        override def creditsUsableForRun(self : Card)(implicit g : Game) = true
        override def triggers(self : Card, e : Event)(implicit g : Game) = trashOnEmpty(self, e, "credit")
    }

    object GlobalsecSecurityClearance extends RR("09051") {
        override def canInstall(self : Card)(implicit g : Game) = g.link >= 2
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) if g.deck(Corp).any && g.clicks(Runner) > 0 =>
                $(OptionalEffect(Runner, "Globalsec Security Clearance: lose [click] to look at the top card of R&D?", $(MessageEffect(Runner, "uses Globalsec Security Clearance to look at the top card of R&D"), ClickLossEffect(Runner, 1), LookAtEffect(Runner, g.deck(Corp).take(1), "Globalsec Security Clearance: the top card of R&D")), $))
            case _ => $
        }
    }

    object Grifter extends RR("04046") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnEnds(Runner) if g.successfulRunsThisTurn.any => $(MessageEffect(Runner, "gains 1 credit (Grifter)"), gain(Runner, 1))
            case TurnEnds(Runner) => $(MessageEffect(Runner, "trashes Grifter (no successful run)"), TrashEffect(self, Runner, false))
            case _ => $
        }
    }

    object GuruDavinder extends RR("10084") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case PreventionInterrupt(k, 1, n) if k == "net" || k == "meat" => $(MessageEffect(Runner, "prevents " + n + " " + k + " damage (Guru Davinder)"), Prevent.allDamage, GuruAction(self))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case GuruAction(self) =>
                g.push(ChooseOneEffect(Runner, "Guru Davinder: choose one", $(
                    Alt("Pay 4 credits", $(SpendEffect(Runner, 4, Spend.effect(|(self)), 0, $)), g.canSpend(Runner, 4, Spend.effect(|(self)))),
                    Alt("Trash Guru Davinder", $(TrashEffect(self, Runner, false))))))
                NextAction
        }
    }

    object HadesShard extends Shard("06059", Archives) {
        def shardLabel = "breach Archives"
        def shardEffect(self : Card)(implicit g : Game) = $(BreachNoRootEffect(Archives))
    }

    object HardAtWork extends RR("04023") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) => $(MessageEffect(Runner, "gains 2 credits and loses [click] (Hard at Work)"), ClickLossEffect(Runner, 1), gain(Runner, 2))
            case _ => $
        }
    }

    object HernandoCortez extends RR("11004") {
        override def rezCostMod(self : Card, card : Card)(implicit g : Game) = (card.data.isIce && g.credits(Corp) >= 10).?(g.subs(card).num).|(0)
    }

    object HumanFirst extends RR("07048") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case AgendaScored(c) => $(MessageEffect(Runner, "gains " + c.data.agendaPoints.|(0) + " credits (Human First)"), gain(Runner, c.data.agendaPoints.|(0)))
            case AgendaStolen(c) => $(MessageEffect(Runner, "gains " + c.data.agendaPoints.|(0) + " credits (Human First)"), gain(Runner, c.data.agendaPoints.|(0)))
            case _ => $
        }
    }

    object HuntingGrounds extends RR("09035") {
        override def preventions(self : Card, kind : String, n : Int)(implicit g : Game) =
            (kind == "encounter").$(Ability("prevent the encounter ability", NoCost, $(Prevent.encounter), once = |("hg")))
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("install the top 3 cards of the stack facedown", $(TrashSelfCost), g.deck(Runner).take(3)./(c => InstallFacedownEffect(c)), ok = g.deck(Runner).any, reason = "empty stack"))
    }

    object IceAnalyzer extends RR("25057") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Rezzed(c) if c.data.isIce => $(AddCounterEffect(self, "credit", 1))
            case _ => $
        }
        // "You may use credits on Ice Analyzer to install programs."
        override def paysFor(self : Card, spend : Spend)(implicit g : Game) = spend.installing.exists(_.data.isProgram)
    }

    object InsideMan extends RR("02068") {
        override def initialCounters(self : Card)(implicit g : Game) = Map("credit" -> 2)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) => $(RefillCreditsEffect(self, 2))
            case _ => $
        }
        // "Use these credits to install hardware."
        override def paysFor(self : Card, spend : Spend)(implicit g : Game) = spend.installing.exists(_.data.isHardware)
    }

    object InvestigativeJournalism extends RR("07049") {
        override def canInstall(self : Card)(implicit g : Game) = g.badPublicity > 0
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("give the Corp 1 bad publicity", $(ClickCost(4), TrashSelfCost), $(BadPubEffect(1)), click = true))
    }

    abstract class Inez(code : String, val variant : Int) extends RR(code) {
        override def abilities(self : Card)(implicit g : Game) = {
            val label = variant match {
                case 1 => "add to your score area: expose all cards in a remote server"
                case 2 => "add to your score area: reveal the top 3 cards of R&D"
                case 3 => "add to your score area: reveal each card in HQ"
                case _ => "add to your score area: reveal each card in HQ and the top card of R&D"
            }
            $(Ability(label, NoCost, $(AddToScoreAreaEffect(self, Runner), InezAction(self, variant)), ok = g.isInstalled(self) && g.reg("stolen") > 0 && (variant != 1 || g.remotes.any), reason = "no agenda stolen this turn"))
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case InezAction(self, 1) =>
                if (g.remotes.any)
                    g.push(ChooseServerForAction(Runner, self, g.remotes, false, "inez", "Investigator Inez Delgado: choose a remote server to expose"))
                NextAction
            case InezAction(self, 2) =>
                if (g.deck(Corp).any)
                    g.push(RevealCardsEffect(Corp, g.deck(Corp).take(3)))
                NextAction
            case InezAction(self, 3) =>
                if (g.hand(Corp).any)
                    g.push(RevealCardsEffect(Corp, g.hand(Corp)))
                NextAction
            case InezAction(self, _) =>
                val l = g.hand(Corp) ++ g.deck(Corp).take(1)
                if (l.any)
                    g.push(RevealCardsEffect(Corp, l))
                NextAction
            case ServerPickedAction(self, Some(s), "inez") =>
                g.push(ExposeEffect(g.root(s)))
                NextAction
            case ServerPickedAction(self, None, "inez") =>
                NextAction
        }
    }

    object InezDelgado1 extends Inez("14014", 1)
    object InezDelgado2 extends Inez("14015", 2)
    object InezDelgado3 extends Inez("14016", 3)
    object InezDelgado4 extends Inez("14017", 4)

    object Jackpot extends RR("21090") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) => $(AddCounterEffect(self, "credit", 1))
            case AgendaStolen(_) if g.isInstalled(self) && g.counter(self, "credit") > 0 => $(OptionalEffect(Runner, "Jackpot!: trash it to take hosted credits?", $(JackpotAction(self)), $))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case JackpotAction(self) =>
                g.push(ChooseNumberEffect(Runner, self, 0, g.counter(self, "credit"), "jackpot", "Jackpot!: how many hosted credits to take?"))
                NextAction
            case NumberPickedAction(self, n, "jackpot") =>
                g.push(TakeCreditsEffect(Runner, self, n), TrashEffect(self, Runner, false))
                NextAction
        }
    }

    object JakSinclair extends RR("09052") {
        override def installCostMod(self : Card, card : Card)(implicit g : Game) = (card == self).?(-g.link).|(0)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) => $(OptionalEffect(Runner, "Jak Sinclair: make a run?", $(RunAnyServerEffect(|(self), g.servers)), $))
            case _ => $
        }
    }

    object JarogniewMercs extends RR("12062") {
        override def onInstall(self : Card)(implicit g : Game) = $(tag(1), JarogniewAction(self))
        override def triggers(self : Card, e : Event)(implicit g : Game) = trashOnEmpty(self, e, "power")
        override def preventions(self : Card, kind : String, n : Int)(implicit g : Game) =
            (kind == "meat").$(Ability("prevent 1 meat damage", $(CounterCost("power", 1)), $(Prevent.damage(1), TriggerEventAction(CounterAdded(self, "power")))))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case JarogniewAction(self) =>
                g.push(AddCounterEffect(self, "power", 3 + g.tags))
                NextAction
        }
    }

    object JohnMasanori extends RR("25064") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(_) if g.reg("successful-run") == 1 => $(MessageEffect(Runner, "draws 1 card (John Masanori)"), draw(Runner, 1))
            case UnsuccessfulRun(_) if g.reg("unsuccessful-run") == 1 => $(MessageEffect(Runner, "takes 1 tag (John Masanori)"), tag(1))
            case _ => $
        }
    }

    object JoshuaB extends RR("02042") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) => $(OptionalEffect(Runner, "Joshua B.: gain [click] (take 1 tag when the turn ends)?", $(mark(self, "joshua"), MessageEffect(Runner, "gains [click] (Joshua B.)"), GainClicksEffect(Runner, 1)), $))
            case TurnEnds(Runner) if g.usedThisTurn(self, "joshua") => $(MessageEffect(Runner, "takes 1 tag (Joshua B.)"), tag(1))
            case _ => $
        }
    }

    object KasiString extends RR("21111") {
        override def agendaPointsBonus(self : Card)(implicit g : Game) = (g.zone(self) == ScoredZone(Runner)).?(1).|(0)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case RunEnds(s, true, stole) if s.isRemote && g.isInstalled(self) && unused(self, "kasi") =>
                $(mark(self, "kasi")) ++ (!stole && g.run.exists(_.didAccess)).$(OptionalEffect(Runner, "Kasi String: place 1 power counter on it?", $(AddCounterEffect(self, "power", 1)), $))
            case CounterAdded(c, "power") if c == self && g.isInstalled(self) && g.counter(self, "power") >= 4 =>
                $(MessageEffect(Runner, "adds Kasi String to the score area as an agenda worth 1 point"), AddToScoreAreaEffect(self, Runner))
            case _ => $
        }
    }

    object KatiJones extends RR("25065") {
        override def abilities(self : Card)(implicit g : Game) = $(
            Ability("place 3 credits on Kati Jones", click(), $(AddCounterEffect(self, "credit", 3)), click = true, once = once("kati")),
            Ability("take all hosted credits (" + g.counter(self, "credit") + ")", click(), $(TakeCreditsEffect(Runner, self, g.counter(self, "credit"))), click = true, once = once("kati"), ok = g.counter(self, "credit") > 0, reason = "no hosted credits"))
    }

    object KerosMcintyre extends RR("12065") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Derezzed(c) if c.data.isIce && g.fxRunnerResolving && unused(self, "keros") => $(mark(self, "keros"), MessageEffect(Runner, "gains 2 credits (Keros Mcintyre)"), gain(Runner, 2))
            case _ => $
        }
    }

    object Kongamato extends BiteyBoi("21027", false)

    object LagunaVelascoDistrict extends RR("13022") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case ClickDraw(Runner) => $(MessageEffect(Runner, "draws 1 additional card (Laguna Velasco District)"), draw(Runner, 1))
            case _ => $
        }
    }

    object LevyAdvancedResearchLab extends RR("13021") {
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("reveal the top 4 cards of the stack, may add a program to the grip", click(), $(LevyAction(self)), click = true, ok = g.deck(Runner).any, reason = "empty stack"))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case LevyAction(self) =>
                val l = g.deck(Runner).take(4)
                val programs = l.%(_.data.isProgram)
                if (programs.any)
                    g.push(RevealCardsEffect(Runner, l), ChooseCardEffect(Runner, programs, LevyPurpose(self, l), "Levy Advanced Research Lab: add a program to the grip?", true), LevyBottomAction(self, l))
                else
                    g.push(RevealCardsEffect(Runner, l), LevyBottomAction(self, l))
                NextAction
            case LevyBottomAction(self, l) =>
                val rest = l.%(c => g.zone(c) == DeckZone(Runner))
                rest.foreach(c => g.moveTo(c, DeckZone(Runner)))
                if (rest.any)
                    g.log(Runner, "adds", rest.num.cards, "to the bottom of the stack")
                NextAction
        }
        override def chosenCard(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach { c =>
                g.log(Runner, "adds", c, "to the grip")
                g.moveTo(c, HandZone(Runner))
            }
            NextAction
        }
    }

    object LewiGuilherme extends RR("21005") {
        override def handSize(self : Card, f : Faction)(implicit g : Game) = f.isCorp.?(-1).|(0)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) => $(ChooseOneEffect(Runner, "Lewi Guilherme: choose one", $(
                Alt("Pay 1 credit for Lewi Guilherme", $(lose(Runner, 1)), g.credits(Runner) >= 1),
                Alt("Trash Lewi Guilherme", $(TrashEffect(self, Runner, false))))))
            case _ => $
        }
    }

    object LiberatedChela extends RR("10081") {
        override def agendaPointsBonus(self : Card)(implicit g : Game) = (g.zone(self) == ScoredZone(Runner)).?(2).|(0)
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("forfeit an agenda: add Liberated Chela to your score area (2 points) unless the Corp forfeits an agenda", $(ClickCost(5)), $(forfeitChoice(self, "Liberated Chela: choose an agenda to forfeit"), ChelaAction(self)), click = true, ok = g.isInstalled(self) && runnerAgendas.any, reason = "no agenda to forfeit"))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case ChelaAction(self) =>
                val l = g.scored(Corp).%(_.data.isAgenda)
                if (l.any)
                    g.push(OptionalEffect(Corp, "Liberated Chela: forfeit an agenda to remove it from the game?", $(ChooseCardEffect(Corp, l, ChelaForfeitPurpose(self), "Liberated Chela: choose an agenda to forfeit", false)), $(AddToScoreAreaEffect(self, Runner))))
                else
                    g.push(AddToScoreAreaEffect(self, Runner))
                NextAction
        }
        override def chosenCard(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(c => g.push(ForfeitEffect(c), RemoveFromGameEffect(p.card)))
            NextAction
        }
    }

    object LogicBomb extends RR("21089") {
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("bypass the encountered ice and lose all remaining clicks", $(TrashSelfCost), $(BypassEffect, LoseAllClicksEffect(Runner)), ok = encountering.any, reason = "not encountering ice"))
    }

    object LondonLibrary extends RR("08029") {
        override def abilities(self : Card)(implicit g : Game) = {
            val l = g.hand(Runner).%(c => c.data.isProgram && !g.has(c, "Virus") && !g.impl(c).mustBeHosted(c) && g.memoryFree >= c.data.memory.|(0))
            val h = g.hosted(self).%(_.data.isProgram)
            $(Ability("install a non-virus program from the grip on London Library ignoring the install cost", click(), $(ChooseCardEffect(Runner, l, LondonLibraryPurpose(self), "London Library: choose a program to install", false)), click = true, ok = l.any, reason = "nothing to install"),
              Ability("add a hosted program to the grip", click(), $(ChooseCardEffect(Runner, h, ReturnToHandPurpose, "London Library: choose a program to add to the grip", false)), click = true, ok = h.any, reason = "no hosted program"))
        }
        override def chosenCard(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(c => g.push(RunnerInstallEffect(c, |(p.card), true)))
            NextAction
        }
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnEnds(Runner) if g.hosted(self).exists(_.data.isProgram) => $(MessageEffect(Runner, "trashes the programs hosted on London Library"), TrashCardsEffect(g.hosted(self).%(_.data.isProgram), Runner))
            case _ => $
        }
    }

    val all : $[CardImpl] = $(AaronMarron, AccessToGlobalsec, ActivistSupport, AdjustedChronotype, AeneasInformant, AksharaSareen, AlgoTrading, AllNighter,
        AlwaysBeRunning, AngelArena, ArmitageCodebusting, ArtistColony, Assimilator, BankJob, Bazaar, BeachParty, BethKilrainChang, Bhagat, BioModeledNetwork,
        BiometricSpoofing, BlockadeRunner, BlooMoose, BorrowedSatellite, BugOutBag, Caldera, Charlatan, ChatterjeeUniversity, ChromeParlor, CitadelSanctuary,
        ClanVengeance, CompromisedEmployee, CorporateDefector, Councilman, CounterSurveillance, CrashSpace, Crowdfunding, Crypt, DadianaChacon, DataDealer,
        DataFolding, DataLeakReversal, DDoS, DeanLister, Decoy, District99, DJFenris, DonutTaganes, DrLovegood, DrugDealer, Duggars, DummyBox, EdenShard,
        EmptiedMind, EnhancedVision, FallGuy, FanSite, Fester, FilmCritic, FindTheTruth, FirstResponders, GangSign, Gbahali, GeneConditioningShoppe, GhostRunner,
        GlobalsecSecurityClearance, Grifter, GuruDavinder, HadesShard, HardAtWork, HernandoCortez, HumanFirst, HuntingGrounds, IceAnalyzer, InsideMan,
        InvestigativeJournalism, InezDelgado1, InezDelgado2, InezDelgado3, InezDelgado4, Jackpot, JakSinclair, JarogniewMercs, JohnMasanori, JoshuaB, KasiString,
        KatiJones, KerosMcintyre, Kongamato, LagunaVelascoDistrict, LevyAdvancedResearchLab, LewiGuilherme, LiberatedChela, LogicBomb, LondonLibrary)
}
