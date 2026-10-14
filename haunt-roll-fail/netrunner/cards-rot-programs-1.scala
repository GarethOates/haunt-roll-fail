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
// Rotated programs, part 1: Anarch, Apex, Adam
// ---------------------------------------------------------------------------------------------------------------------

object RotProgramCards1 {
    // ------------------------------------------------------------------------------------------------------------
    // Anarch
    // ------------------------------------------------------------------------------------------------------------

    object Djinn extends Rot("01009") {
        override def canHost(self : Card, card : Card)(implicit g : Game) = card.data.isProgram && !card.data.isIcebreaker && hostedMemory(self) + card.data.memory.|(0) <= 3
        override def memory(self : Card)(implicit g : Game) = hostedMemory(self)
        override def abilities(self : Card)(implicit g : Game) = {
            val l = g.deck(Runner).%(x => x.data.isProgram && g.has(x, "Virus")).sortBy(_.title)
            $(Ability("search the stack for a virus program and add it to the grip", clickc(1), $(ChooseCardEffect(Runner, l, AddToHandPurpose(Runner, true), "Djinn: choose a virus program", true), ShuffleDeckEffect(Runner)), click = true, ok = g.deck(Runner).any, reason = "empty stack"))
        }
    }

    object Wyrm extends Rot("01013") {
        override def breaks(self : Card)(implicit g : Game) = encounteredIce.exists(i => g.iceStrength(i) <= 0).$(BreakAbility(c(3), 1, $(), |("break 1 subroutine (ice with 0 or less strength)")))
        override def pumps(self : Card)(implicit g : Game) = $(Pump(c(1), 1))
        override def abilities(self : Card)(implicit g : Game) = {
            val ice = encounteredIce.%(i => g.iceStrength(i) <= g.breakerStrength(self))
            $(Ability("give -1 strength to the encountered ice", c(1), ice.$./(i => PumpIceEffect(i, -1, EncounterDuration)), ok = ice.any, reason = "needs encountered ice with strength at most Wyrm's"))
        }
    }

    object Yog0 extends Rot("01014") {
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(0), 1, $("Code Gate")))
    }

    object NerveAgent extends Rot("02041") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(HQ) => $(AddCounterEffect(self, "virus", 1))
            case BreachServer(HQ) if virus(self) >= 2 => $(ChooseNumberEffect(Runner, self, 0, virus(self) - 1, "nerve", "Nerve Agent: how many additional cards from HQ to access?"))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case NumberPickedAction(self, n, _) =>
                if (n > 0) {
                    g.log(Runner, "accesses", n.hl, "additional card" + (n != 1).??("s"), "from HQ (Nerve Agent)")
                    g.push(AccessBonusEffect(HQ, n))
                }
                NextAction
        }
    }

    object Disrupter extends Rot("02061") {
        override def preventions(self : Card, kind : String, n : Int)(implicit g : Game) =
            (kind == "trace").$(Ability("trash Disrupter to reduce the base trace strength to 0", $(TrashSelfCost), $(FlipSwitchTraceAction(self))))
    }

    object Pawn extends Rot("04002") {
        def outermostCentral(h : Card)(implicit g : Game) = g.zone(h) match {
            case IceZone(s) => s.isCentral && g.ice(s).lastOption.has(h)
            case _ => false
        }
        override def mustBeHosted(self : Card)(implicit g : Game) = true
        override def hostRequirement(self : Card, host : Card)(implicit g : Game) = host.data.isIce && outermostCentral(host) && caissaFree(host)
        override def abilities(self : Card)(implicit g : Game) = {
            val l = g.allIce.%(h => outermostCentral(h) && caissaFree(h) && !host(self).has(h))
            $(hostIceAbility(self, "host Pawn on the outermost piece of ice protecting a central server", l, click(), true))
        }
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(_) if host(self).exists(_.data.isIce) => $(PawnAction(self))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case PawnAction(self) =>
                host(self) match {
                    case Some(h) if iceIndex(h) > 0 && caissaFree(serverIce(h).apply(iceIndex(h) - 1)) =>
                        g.push(HostOnEffect(self, serverIce(h).apply(iceIndex(h) - 1), false))
                    case _ =>
                        val l = (g.hand(Runner) ++ g.discard(Runner)).%(x => g.has(x, "Caïssa") && g.allIce.exists(h => g.impl(x).hostRequirement(x, h)))
                        if (l.any)
                            g.push(ChooseCardEffect(Runner, l, PawnPurpose(self), "Pawn: choose another Caissa to install, ignoring all costs (Pawn is trashed)", true), TrashEffect(self, Runner, false))
                        else
                            g.push(TrashEffect(self, Runner, false))
                }
                NextAction
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(x => g.pushAll(g.installRunner(x, 99)))
            NextAction
        }
    }

    object Rook extends Rot("04003") {
        override def mustBeHosted(self : Card)(implicit g : Game) = true
        override def hostRequirement(self : Card, host : Card)(implicit g : Game) = host.data.isIce && caissaFree(host)
        override def rezCostMod(self : Card, card : Card)(implicit g : Game) = (card.data.isIce && g.isInstalled(card) && g.serverOf(self).any && g.serverOf(card) == g.serverOf(self)).?(2).|(0)
        override def abilities(self : Card)(implicit g : Game) = {
            val l = host(self)./(h => g.allIce.%(x => x != h && caissaFree(x) && (g.serverOf(x) == g.serverOf(h) || iceIndex(x) == iceIndex(h)))).|($)
            $(hostIceAbility(self, "host Rook on a piece of ice protecting this server or at the same position of another server", l, click(), true))
        }
    }

    object Bishop extends Rot("04021") {
        override def mustBeHosted(self : Card)(implicit g : Game) = true
        override def hostRequirement(self : Card, host : Card)(implicit g : Game) = host.data.isIce && caissaFree(host)
        override def iceStrengthMod(self : Card, ice : Card)(implicit g : Game) = (host(self).has(ice) && g.isRezzed(ice)).?(-2).|(0)
        override def abilities(self : Card)(implicit g : Game) = {
            val l = host(self)./(h => g.allIce.%(x => x != h && caissaFree(x) && g.serverOf(x).exists(s => g.serverOf(h).exists(t => s.isCentral != t.isCentral)))).|($)
            $(hostIceAbility(self, "host Bishop on a piece of ice protecting a server of the other kind (central / remote)", l, click(), true))
        }
    }

    object Scheherazade extends Rot("04022") {
        override def canHost(self : Card, card : Card)(implicit g : Game) = card.data.isProgram
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Installed(Runner, x) if g.state(x).host.has(self) => $(gain(Runner, 1))
            case _ => $
        }
    }

    object Knight extends Rot("04043") {
        override def mustBeHosted(self : Card)(implicit g : Game) = true
        override def hostRequirement(self : Card, host : Card)(implicit g : Game) = host.data.isIce && caissaFree(host)
        override def breaks(self : Card)(implicit g : Game) = encounteredIce.exists(i => host(self).has(i)).$(BreakAbility(c(2), 1, $(), |("break 1 subroutine on the host ice")))
        override def abilities(self : Card)(implicit g : Game) = {
            val l = host(self)./(h => g.allIce.%(x => x != h && caissaFree(x) && (g.serverOf(x) != g.serverOf(h) || (iceIndex(x) - iceIndex(h)).abs != 1))).|($)
            $(hostIceAbility(self, "host Knight on a piece of ice (not adjacent to the current host)", l, click(), true))
        }
    }

    object Keyhole extends Rot("04061") {
        override def abilities(self : Card)(implicit g : Game) = $(Ability("run R&D (trash 1 of the top 3 cards instead of breaching)", click(), $(RunEffect(RnD, |(self))), click = true))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(RnD) if thisCardRun(self) => $(ReplaceBreachEffect, KeyholeAction(self))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case KeyholeAction(self) =>
                val l = g.deck(Corp).take(3)
                if (l.any)
                    g.push(ChooseCardEffect(Runner, l, TrashPurpose(Runner), "Keyhole: choose 1 of the top 3 cards of R&D to trash", false), ShuffleDeckEffect(Corp))
                NextAction
        }
    }

    object D4v1d extends Rot("06033") {
        override def initialCounters(self : Card)(implicit g : Game) = Map("power" -> 3)
        override def breaks(self : Card)(implicit g : Game) = encounteredIce.exists(i => g.iceStrength(i) >= 5).$(BreakAbility($(CounterCost("power", 1)), 1, $(), |("break 1 subroutine (ice with strength 5 or more)")))
    }

    object BlacKat extends Rot("06053") {
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(1), 1, $("Barrier")), BreakAbility($(StealthCreditCost(1, 1)), 3, $("Barrier"), |("break up to 3 barrier subroutines (stealth credit)")))
        override def pumps(self : Card)(implicit g : Game) = $(Pump(c(2), 1), Pump($(StealthCreditCost(2, 1)), 2, EncounterDuration, |("+2 strength (at least 1 stealth credit)")))
    }

    object Origami extends Rot("06074") {
        override def handSize(self : Card, f : Faction)(implicit g : Game) = f.isRunner.?(g.installedCards(Runner).%(x => x.code == self.code && g.isActive(x)).num).|(0)
    }

    object CerberusCuj0 extends Cerberus("06094", "Sentry")

    object Incubator extends Rot("06113") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) => $(AddCounterEffect(self, "virus", 1))
            case _ => $
        }
        override def abilities(self : Card)(implicit g : Game) = {
            val n = g.counter(self, "virus")
            val l = g.installedCards(Runner).%(x => x != self && g.has(x, "Virus"))
            $(Ability("move the hosted virus counters (" + n + ") to an installed virus card", $(ClickCost(1), TrashSelfCost), $(ChooseCardEffect(Runner, l, IncubatorPurpose(self, n), "Incubator: choose a virus card", false)), click = true, ok = n > 0 && l.any, reason = "needs virus counters and an installed virus card"))
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case IncubatorPurpose(self, n) =>
                cards.single.foreach(x => g.push(AddCounterEffect(x, "virus", n)))
                NextAction
        }
    }

    object Ixodidae extends Rot("06114") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = trashOnPurge(self, e) ++ (e match {
            case CreditsLost(Corp, _) => $(gain(Runner, 1))
            case _ => $
        })
    }

    object Eater extends Rot("07040") {
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(1), 1, $(), |("break 1 subroutine and access no cards this run"), $(ReplaceBreachEffect)))
        override def pumps(self : Card)(implicit g : Game) = $(Pump(c(1), 1))
    }

    object Gravedigger extends Rot("07041") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Trashed(x, _, _) if g.owner(x).isCorp && g.inPlayZone(g.lastTrashedFrom) => $(AddCounterEffect(self, "virus", 1))
            case _ => $
        }
        override def abilities(self : Card)(implicit g : Game) = $(Ability("force the Corp to trash the top card of R&D (1 virus counter)", click(), $(SpendVirusEffect(self, 1, false), MillEffect(Corp, 1)), click = true, ok = g.deck(Corp).any && virus(self) >= 1, reason = "needs a virus counter and cards in R&D"))
    }

    object Hivemind extends Rot("07042") {
        override def initialCounters(self : Card)(implicit g : Game) = Map("virus" -> 1)
        override def abilities(self : Card)(implicit g : Game) = {
            val l = g.installedCards(Runner).%(x => x != self && g.has(x, "Virus"))
            $(Ability("move 1 hosted virus counter to another virus card", NoCost, $(ChooseCardEffect(Runner, l, MoveVirusPurpose(|(self), None, 1), "Hivemind: choose a virus card", false)), ok = g.counter(self, "virus") > 0 && l.any, reason = "needs a virus counter and another virus card"))
        }
    }

    // the purge protection remembers (target) the hosted program while it has virus counters; spending counters through
    // a cost is not seen, so a program that spent all its counters before a purge still keeps 1
    object Progenitor extends Rot("07043") {
        override def canHost(self : Card, card : Card)(implicit g : Game) = card.data.isProgram && g.has(card, "Virus") && g.hosted(self).none
        override def memory(self : Card)(implicit g : Game) = hostedMemory(self)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case CounterAdded(x, "virus") if g.hosted(self).has(x) => $(SetTargetEffect(self, (g.counter(x, "virus") > 0).?(x)))
            case Purged(_) => g.state(self).target.%(x => g.hosted(self).has(x)).$./~(x => $(MessageEffect(Runner, "keeps 1 virus counter on the program hosted on Progenitor"), AddCounterEffect(x, "virus", 1)))
            case _ => $
        }
    }

    object Faust extends Rot("08061") {
        // the grip card is trashed as the cost, before the break (FinalRotRules.performRun)
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(NoCost, 1, $(), |("break 1 subroutine (trash 1 card from the grip)")))
        override def breakAllowed(self : Card, breaker : Card, ice : Card)(implicit g : Game) = breaker != self || g.hand(Runner).any || g.frFaustPaid
        override def abilities(self : Card)(implicit g : Game) = $(Ability("+2 strength", $(TrashFromHandCost(1)), $(PumpBreakerEffect(self, 2, EncounterDuration)), ok = encounteredIce.any, reason = "not encountering ice"))
    }

    object Trope extends Rot("08081") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) => $(AddCounterEffect(self, "power", 1))
            case _ => $
        }
        override def abilities(self : Card)(implicit g : Game) = {
            val n = g.counter(self, "power").atMost(g.discard(Runner).num)
            $(Ability("shuffle " + n + " cards from the heap into the stack (1 per power counter)", $(ClickCost(1), RemoveSelfCost), $(ChooseCardsEffect(Runner, g.discard(Runner), n, n, ShuffleIntoDeckPurpose(Runner), "Trope: choose " + n + " cards to shuffle into the stack")), click = true, ok = n > 0, reason = "needs power counters and cards in the heap"))
        }
    }

    object Surfer extends Rot("08102") {
        override def abilities(self : Card)(implicit g : Game) = {
            val ice = encounteredIce.%(i => g.has(i, "Barrier"))
            val l = ice.$./~(i => serverIce(i).%(x => (iceIndex(x) - iceIndex(i)).abs == 1))
            $(Ability("swap the encountered barrier with a piece of ice directly before or after it", c(2), $(ChooseCardEffect(Runner, l, SurferPurpose(self), "Surfer: choose a piece of ice to swap with", false)), ok = ice.any && l.any, reason = "needs an encountered barrier with an adjacent piece of ice"))
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            (encounteredIce, cards.single) match {
                case (Some(i), Some(x)) => g.push(SwapIceEffect(i, x), SetRunPositionEffect(iceIndex(x) + 1))
                case _ =>
            }
            NextAction
        }
    }

    object Diwan extends Rot("10021") {
        override def onInstall(self : Card)(implicit g : Game) = $(ChooseServerForAction(Runner, self, g.servers, false, "diwan", "Diwan: choose a server"))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case ServerPickedAction(self, s, _) =>
                s.foreach { x => g.state(self).memo = x.name ; g.log(self, "targets", x) }
                NextAction
        }
        override def triggers(self : Card, e : Event)(implicit g : Game) = trashOnPurge(self, e) ++ (e match {
            case Installed(Corp, x) if g.state(self).memo != "" && g.serverOf(x).exists(_.name == g.state(self).memo) => $(MessageEffect(Corp, "pays 1 more to install in the server targeted by Diwan"), lose(Corp, 1))
            case _ => $
        })
    }

    object Aghora extends Deva("10097") {
        def condition(ice : Card)(implicit g : Game) = g.rezCost(ice) >= 5
        def label = "ice with rez cost 5 or more"
    }

    object Nfr extends Rot("11023") {
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(1), 1, $("Barrier")))
        override def strength(self : Card)(implicit g : Game) = g.counter(self, "power")
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SubsBroken(ice, l, b) if b == self && g.fxFullyBroke(ice, l, b) => $(AddCounterEffect(self, "power", 1))
            case _ => $
        }
    }

    object Paperclip extends Conspiracy("11024", "Barrier") {
        override def abilities(self : Card)(implicit g : Game) = g.isInstalled(self).??{
            val ok = encounteredIce.exists(i => g.has(i, "Barrier")) && g.availableCredits(Runner, |(self)) >= 1
            $(Ability("pay X credits: +X strength and break up to X barrier subroutines", NoCost, $(ChooseNumberEffect(Runner, self, 1, g.availableCredits(Runner, |(self)), "paperclip", "Paperclip: how many credits to spend?")), ok = ok, reason = "not encountering a barrier"))
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case NumberPickedAction(self, n, _) => g.push(HeapBreakerXAction(self, n)) ; NextAction
            case _ => super.perform(a)
        }
    }

    object BlackOrchestra extends Conspiracy("11042", "Code Gate") {
        override def abilities(self : Card)(implicit g : Game) = g.isInstalled(self).??(
            $(Ability("+2 strength and break up to 2 code gate subroutines", c(3), $(PumpBreakerEffect(self, 2, EncounterDuration), SetCounterEffect(self, "break", 2)), ok = encounteredIce.exists(i => g.has(i, "Code Gate")), reason = "not encountering a code gate")))
    }

    object MKUltra extends Conspiracy("11081", "Sentry") {
        override def abilities(self : Card)(implicit g : Game) = g.isInstalled(self).??(
            $(Ability("+2 strength and break up to 2 sentry subroutines", c(3), $(PumpBreakerEffect(self, 2, EncounterDuration), SetCounterEffect(self, "break", 2)), ok = encounteredIce.exists(i => g.has(i, "Sentry")), reason = "not encountering a sentry")))
    }

    object Sunya extends Rot("11102") {
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(2), 1, $("Sentry")))
        override def strength(self : Card)(implicit g : Game) = g.counter(self, "power")
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SubsBroken(ice, l, b) if b == self && g.fxFullyBroke(ice, l, b) => $(AddCounterEffect(self, "power", 1))
            case _ => $
        }
    }

    object Berserker extends Rot("12041") {
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(2), 2, $("Barrier")))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case EncounterIce(ice) if g.has(ice, "Barrier") && g.subs(ice).num > 0 => $(PumpBreakerEffect(self, g.subs(ice).num, EncounterDuration))
            case _ => $
        }
    }

    object Persephone extends Rot("12042") {
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(2), 1, $("Sentry")))
        override def pumps(self : Card)(implicit g : Game) = $(Pump(c(1), 1))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case EncounterIce(ice) => $(SetMemoEffect(self, g.run./(_.subsFired).|(0).toString))
            case PassIce(ice) if g.has(ice, "Sentry") && g.isRezzed(ice) && g.deck(Runner).any => $(PersephoneAction(self, ice))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case PersephoneAction(self, ice) =>
                val before = g.state(self).memo.toIntOption.|(0)
                val fired = (g.run./(_.subsFired).|(0) - before).atLeast(0)
                if (fired > 0 && g.deck(Runner).any)
                    g.push(OptionalEffect(Runner, "Persephone: trash the top card of the stack to trash " + fired + " card" + (fired != 1).??("s") + " from the top of R&D?", $(MillEffect(Runner, 1), MillEffect(Corp, fired)), $))
                NextAction
        }
    }

    object GodOfWar extends Rot("12082") {
        override def breaks(self : Card)(implicit g : Game) = (virus(self) >= 1).$(BreakAbility(NoCost, 1, $(), |("break 1 subroutine (1 virus counter)"), $(SpendVirusEffect(self, 1, false))))
        override def pumps(self : Card)(implicit g : Game) = $(Pump(c(2), 1))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) if !g.usedThisTurn(self, "gow") => $(OptionalEffect(Runner, "God of War: take 1 tag to place 2 virus counters on it?", $(MarkUsedEffect(self, "gow"), tag(1), AddCounterEffect(self, "virus", 2)), $))
            case _ => $
        }
        override def abilities(self : Card)(implicit g : Game) = $(Ability("place 2 virus counters (start of turn)", $(TagCost(1)), $(AddCounterEffect(self, "virus", 2)), once = once("gow"), ok = startOfTurn, reason = "only at the start of your turn"))
    }

    object Darwin extends Rot("20008") {
        override def strength(self : Card)(implicit g : Game) = virus(self)
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(2), 1, $()))
        override def abilities(self : Card)(implicit g : Game) = $(Ability("place 1 virus counter (start of turn)", c(1), $(AddCounterEffect(self, "virus", 1)), once = once("darwin"), ok = startOfTurn, reason = "only at the start of your turn"))
    }

    object Hemorrhage extends Rot("20012") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(_) => $(AddCounterEffect(self, "virus", 1))
            case _ => $
        }
        override def abilities(self : Card)(implicit g : Game) = $(Ability("force the Corp to trash 1 card from HQ (2 virus counters)", click(), $(SpendVirusEffect(self, 2, false), CorpTrashFromHandEffect(1)), click = true, ok = g.hand(Corp).any && virus(self) >= 2, reason = "needs 2 virus counters and cards in HQ"))
    }

    object MorningStar extends Rot("20014") {
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(1), 0, $("Barrier")))
    }

    object Yusuf extends VirusBreaker("21002", "Barrier")

    object Plague extends Rot("21022") {
        override def onInstall(self : Card)(implicit g : Game) = $(ChooseServerForAction(Runner, self, g.servers, false, "plague", "Plague: choose a server"))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case ServerPickedAction(self, s, _) =>
                s.foreach { x => g.state(self).memo = x.name ; g.log(self, "targets", x) }
                NextAction
        }
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(s) if g.state(self).memo == s.name => $(AddCounterEffect(self, "virus", 2))
            case _ => $
        }
    }

    object EXer extends Rot("21041") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = trashOnPurge(self, e) ++ (e match {
            case BreachServer(RnD) => $(AccessBonusEffect(RnD, 1))
            case _ => $
        })
    }

    object Trypano extends Rot("21082") {
        override def mustBeHosted(self : Card)(implicit g : Game) = true
        override def hostRequirement(self : Card, host : Card)(implicit g : Game) = host.data.isIce
        override def onInstall(self : Card)(implicit g : Game) = $(TrypanoCheckAction(self))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) => $(OptionalEffect(Runner, "Trypano: place 1 virus counter on it?", $(AddCounterEffect(self, "virus", 1)), $))
            case CounterAdded(x, "virus") if x == self || x.code == "07042" => $(TrypanoCheckAction(self))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case TrypanoCheckAction(self) =>
                host(self).foreach { h =>
                    if (virus(self) >= 5 && g.isInstalled(self) && !g.impl(h).untrashableByRunner(h))
                        g.push(MessageEffect(Runner, "uses Trypano to trash the host ice"), TrashEffect(h, Runner, false))
                }
                NextAction
        }
    }

    object Musaazi extends VirusBreaker("21102", "Sentry")

    object Cradle extends Rot("22006") {
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(2), 0, $("Code Gate")))
        override def strength(self : Card)(implicit g : Game) = -g.hand(Runner).num
    }

    object Datasucker extends Rot("25011") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(s) if s.isCentral => $(AddCounterEffect(self, "virus", 1))
            case _ => $
        }
        override def abilities(self : Card)(implicit g : Game) = {
            val ice = encounteredIce
            $(Ability("give -1 strength to the encountered ice (1 virus counter)", NoCost, SpendVirusEffect(self, 1, false) +: ice.$./(i => PumpIceEffect(i, -1, EncounterDuration)), ok = ice.any && virus(self) >= 1, reason = "needs a virus counter and an encountered rezzed ice"))
        }
    }

    object ForceOfNature extends Rot("25012") {
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(2), 2, $("Code Gate")))
        override def pumps(self : Card)(implicit g : Game) = $(Pump(c(1), 1))
    }

    object Lamprey extends Rot("25014") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = trashOnPurge(self, e) ++ (e match {
            case SuccessfulRun(HQ) => $(MessageEffect(Runner, "uses Lamprey"), lose(Corp, 1))
            case _ => $
        })
    }

    object Medium extends Rot("29001") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(RnD) => $(AddCounterEffect(self, "virus", 1))
            case BreachServer(RnD) if virus(self) >= 2 => $(ChooseNumberEffect(Runner, self, 0, virus(self) - 1, "medium", "Medium: how many additional cards from R&D to access?"))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case NumberPickedAction(self, n, _) =>
                if (n > 0) {
                    g.log(Runner, "accesses", n.hl, "additional card" + (n != 1).??("s"), "from R&D (Medium)")
                    g.push(AccessBonusEffect(RnD, n))
                }
                NextAction
        }
    }

    object Parasite extends Rot("29002") {
        override def mustBeHosted(self : Card)(implicit g : Game) = true
        override def hostRequirement(self : Card, host : Card)(implicit g : Game) = host.data.isIce && g.isRezzed(host)
        override def iceStrengthMod(self : Card, ice : Card)(implicit g : Game) = host(self).has(ice).?(-virus(self)).|(0)
        override def onInstall(self : Card)(implicit g : Game) = $(ParasiteCheckAction(self))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) => $(AddCounterEffect(self, "virus", 1))
            case CounterAdded(x, "virus") if x == self || x.code == "07042" => $(ParasiteCheckAction(self))
            case EncounterIce(ice) if host(self).has(ice) => $(ParasiteCheckAction(self))
            case ApproachIce(ice) if host(self).has(ice) => $(ParasiteCheckAction(self))
            case Rezzed(ice) if host(self).has(ice) => $(ParasiteCheckAction(self))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case ParasiteCheckAction(self) =>
                host(self).foreach { h =>
                    if (g.isInstalled(self) && g.isRezzed(h) && g.iceStrength(h) <= 0 && !g.impl(h).untrashableByRunner(h))
                        g.push(MessageEffect(Runner, "uses Parasite to trash the host ice (strength 0 or less)"), TrashEffect(h, Runner, false))
                }
                NextAction
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Apex
    // ------------------------------------------------------------------------------------------------------------

    object EndlessHunger extends Rot("09033") {
        override def breaks(self : Card)(implicit g : Game) = (encounteredIce.exists(hasEtr) && g.runnerInstalled.num >= 1).$(BreakAbility($(TrashInstalledCost("")), 1, $(), |("break 1 end the run subroutine (trash an installed card)")))
    }

    object Harbinger extends Rot("09034") {
        override def onTrash(self : Card, by : Faction)(implicit g : Game) =
            if (g.rotFacedown.has(self)) $(ForgetFacedownEffect(self)) else g.inPlayZone(g.lastTrashedFrom).$(InstallFacedownEffect(self))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Installed(Runner, x) if x == self && g.rotFacedown.has(self) => $(ForgetFacedownEffect(self))
            case _ => $
        }
    }

    object Reaver extends Rot("11086") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Trashed(x, Runner, _) if g.inPlayZone(g.lastTrashedFrom) && !g.usedThisTurn(self, "reaver") => $(MarkUsedEffect(self, "reaver"), MessageEffect(Runner, "uses Reaver to draw 1 card"), draw(Runner, 1))
            case _ => $
        }
    }

    object Consume extends Rot("21068") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Trashed(x, Runner, _) if g.owner(x).isCorp => $(OptionalEffect(Runner, "Consume: place 1 virus counter on it?", $(AddCounterEffect(self, "virus", 1)), $))
            case _ => $
        }
        override def abilities(self : Card)(implicit g : Game) = {
            val n = virus(self)
            val hive = g.installedCards(Runner).%(x => x.code == "07042" && g.isActive(x))
            $(Ability("gain " + (2 * n) + " credits (2 per virus counter) and remove all hosted virus counters", click(), $(gain(Runner, 2 * n), RemoveCountersEffect(self, "virus")) ++ hive./(h => RemoveCountersEffect(h, "virus")), click = true, ok = n > 0, reason = "no virus counters"))
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Adam
    // ------------------------------------------------------------------------------------------------------------

    object Multithreader extends Rot("09040") {
        override def initialCounters(self : Card)(implicit g : Game) = Map("credit" -> 2)
        // "Use these credits to pay for using programs."
        override def paysFor(self : Card, spend : Spend)(implicit g : Game) = spend.using.exists(_.data.isProgram)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) => $(RefillCreditsEffect(self, 2))
            case _ => $
        }
    }

    object Algernon extends Rot("22022") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) if g.credits(Runner) >= 2 => $(OptionalEffect(Runner, "Algernon: pay 2 credits to gain a click?", $(MarkUsedEffect(self, "algernon"), PayCreditsEffect(Runner, 2), GainClicksEffect(Runner, 1)), $))
            case TurnEnds(Runner) if g.usedThisTurn(self, "algernon") && g.successfulRunsThisTurn.none => $(MessageEffect(Runner, "trashes Algernon (no successful run this turn)"), TrashEffect(self, Runner, false))
            case _ => $
        }
    }

    val all : $[CardImpl] = $(
        Djinn, Wyrm, Yog0, NerveAgent, Disrupter, Pawn, Rook, Bishop, Scheherazade, Knight, Keyhole, D4v1d, BlacKat, Origami,
        CerberusCuj0, Incubator, Ixodidae, Eater, Gravedigger, Hivemind, Progenitor, Faust, Trope, Surfer, Diwan, Aghora, Nfr,
        Paperclip, BlackOrchestra, MKUltra, Sunya, Berserker, Persephone, GodOfWar, Darwin, Hemorrhage, MorningStar, Yusuf,
        Plague, EXer, Trypano, Musaazi, Cradle, Datasucker, ForceOfNature, Lamprey, Medium, Parasite,
        EndlessHunger, Harbinger, Reaver, Consume,
        Multithreader, Algernon,
    )
}
