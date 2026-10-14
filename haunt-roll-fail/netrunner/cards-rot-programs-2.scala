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
// Rotated programs, part 2: Criminal, Neutral, Sunny Lebeau
// ---------------------------------------------------------------------------------------------------------------------

object RotProgramCards2 {
    // ------------------------------------------------------------------------------------------------------------
    // Criminal
    // ------------------------------------------------------------------------------------------------------------

    object Ninja extends Rot("01027") {
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(1), 1, $("Sentry")))
        override def pumps(self : Card)(implicit g : Game) = $(Pump(c(3), 5))
    }

    object Snitch extends Rot("02045") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case ApproachIce(ice) if !g.isRezzed(ice) => $(OptionalEffect(Runner, "Snitch: expose the approached ice?", $(ExposeEffect($(ice)), JackOutOfferEffect), $))
            case _ => $
        }
    }

    object Crescentus extends Rot("02065") {
        override def abilities(self : Card)(implicit g : Game) = {
            val ice = encounteredIce.%(i => g.unbrokenSubs(i).none)
            $(Ability("derez the encountered ice (all subroutines broken)", $(TrashSelfCost), ice.$./(i => DerezEffect(i)), ok = ice.any, reason = "needs an encountered ice with all subroutines broken"))
        }
    }

    object GormanDrip extends Rot("04005") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case ActionTaken(Corp, "credit") => $(AddCounterEffect(self, "virus", 1))
            case ClickDraw(Corp) => $(AddCounterEffect(self, "virus", 1))
            case _ => $
        }
        override def abilities(self : Card)(implicit g : Game) = {
            val n = virus(self)
            $(Ability("gain " + n + " credits (1 per virus counter)", $(ClickCost(1), TrashSelfCost), $(gain(Runner, n)), click = true, ok = n > 0, reason = "no virus counters"))
        }
    }

    object Copycat extends Rot("04025") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case PassIce(ice) if g.isRezzed(ice) && g.run.exists(r => !r.ended) =>
                val l = g.allIce.%(x => x != ice && g.isRezzed(x) && x.title == ice.title)
                l.any.$(OptionalEffect(Runner, "Copycat: trash it to continue the run from another rezzed copy of the passed ice?", $(TrashEffect(self, Runner, false), ChooseCardEffect(Runner, l, CopycatPurpose(self), "Copycat: choose a rezzed copy of the passed ice", false)), $))
            case _ => $
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach { x =>
                g.serverOf(x).foreach(s => g.push(RedirectRunEffect(s, iceIndex(x), false)))
            }
            NextAction
        }
    }

    object Leviathan extends Rot("04026") {
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(3), 3, $("Code Gate")))
        override def pumps(self : Card)(implicit g : Game) = $(Pump(c(3), 5))
    }

    object ExpertScheduleAnalyzer extends Rot("04045") {
        override def abilities(self : Card)(implicit g : Game) = $(Ability("run HQ (reveal all cards in HQ instead of breaching)", click(), $(RunEffect(HQ, |(self))), click = true))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(HQ) if thisCardRun(self) => $(ReplaceBreachEffect, RevealCardsEffect(Corp, g.hand(Corp)))
            case _ => $
        }
    }

    object Garrote extends Rot("04065") {
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(1), 1, $("Sentry")))
        override def pumps(self : Card)(implicit g : Game) = $(Pump(c(1), 1))
    }

    object SavoirFaire extends Rot("04105") {
        override def abilities(self : Card)(implicit g : Game) = {
            val l = g.hand(Runner).%(x => x.data.isProgram && g.credits(Runner) - 2 >= g.installCost(x, None) && (!g.impl(x).mustBeHosted(x) || g.allIce.exists(h => g.impl(x).hostRequirement(x, h))))
            $(Ability("install a program from the grip", c(2), $(ChooseCardEffect(Runner, l, InstallPayingPurpose(0, None, false), "Savoir-faire: choose a program to install", true)), once = once("savoir"), ok = l.any, reason = "no affordable program in the grip"))
        }
    }

    object Alias extends CentralOnly("05041", "Sentry", 1, 1, 2, 3)
    object Breach extends CentralOnly("05042", "Barrier", 2, 3, 2, 4)

    object Bug extends Rot("05043") {
        override def canInstall(self : Card)(implicit g : Game) = g.successfulRunsThisTurn.has(HQ)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case CardsDrawn(Corp, l) if l.any && g.credits(Runner) >= 2 =>
                val max = l.num.atMost(g.credits(Runner) / 2)
                $(ChooseOneEffect(Runner, "Bug: pay 2 credits per card to reveal cards the Corp drew?", Alt("No", $) +: 1.to(max).$./(k => Alt(k + " card" + (k != 1).??("s") + " (" + (2 * k) + " credits)", $(PayCreditsEffect(Runner, 2 * k), RandomPickEffect(Runner, l, k, BugPurpose(self)))))))
            case _ => $
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            if (cards.any)
                g.push(RevealCardsEffect(Corp, cards))
            NextAction
        }
    }

    object Gingerbread extends Rot("05044") {
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(1), 1, $("Tracer")))
        override def pumps(self : Card)(implicit g : Game) = $(Pump(c(2), 3))
    }

    object GrapplingHook extends Rot("05045") {
        override def abilities(self : Card)(implicit g : Game) = {
            val ice = encounteredIce.%(i => g.unbrokenSubs(i).num > 1)
            $(Ability("break all but 1 subroutine on the encountered ice", $(TrashSelfCost), ice.$./(i => ChooseOneEffect(Runner, "Grappling Hook: choose the subroutine NOT to break", g.unbrokenSubs(i)./(k => Alt(g.subs(i).apply(k).label.filter(_ < 128), $(GrapplingAction(self, i, k)))))), ok = ice.any, reason = "needs an encountered ice with at least 2 unbroken subroutines"))
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case GrapplingAction(self, ice, keep) =>
                val l = g.unbrokenSubs(ice).but(keep)
                g.state(ice).brokenSubs ++= l
                g.log(Runner, "breaks", l.num.hl, "subroutines on", ice, "(Grappling Hook)")
                if (l.any)
                    g.trigger(SubsBroken(ice, l, self))
                NextAction
        }
    }

    object Passport extends CentralOnly("05046", "Code Gate", 1, 1, 2, 2)

    object Switchblade extends Rot("06077") {
        override def breaks(self : Card)(implicit g : Game) = stealth(1).$(BreakAbility(c(1), 0, $("Sentry"), |("break any number of sentry subroutines (stealth credit)")))
        override def pumps(self : Card)(implicit g : Game) = stealth(1).$(Pump(c(1), 7, EncounterDuration, |("+7 strength (stealth credit)")))
    }

    object CerberusRex extends Cerberus("06096", "Code Gate")

    object AuRevoir extends Rot("06119") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case RunEnds(_, _, _) if g.run.exists(_.jackedOut) => $(MessageEffect(Runner, "gains 1 credit from Au Revoir"), gain(Runner, 1))
            case _ => $
        }
    }

    object Spike extends BreakAndEnter("08004", "Barrier")
    object Crowbar extends BreakAndEnter("08046", "Code Gate")
    object Shiv extends BreakAndEnter("08066", "Sentry")

    object Mongoose extends Rot("10005") {
        override def breaks(self : Card)(implicit g : Game) =
            encounteredIce.%(i => g.state(self).target.forall(_ == i)).$./(i => BreakAbility(c(1), 2, $("Sentry"), None, $(SetTargetEffect(self, |(i)))))
        override def pumps(self : Card)(implicit g : Game) = encounteredIce.forall(i => g.state(self).target.forall(_ == i)).$(Pump(c(2), 2))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case RunEnds(_, _, _) if g.state(self).target.any => $(SetTargetEffect(self, None))
            case _ => $
        }
    }

    object Vamadeva extends Deva("10061") {
        def condition(ice : Card)(implicit g : Game) = g.subs(ice).num == 1
        def label = "ice with exactly 1 subroutine"
    }

    object Golden extends Bird("11025", "Sentry", 2, 2, 2, 4)
    object Peregrine extends Bird("11044", "Code Gate", 1, 1, 3, 3)
    object Saker extends Bird("11064", "Barrier", 1, 1, 2, 2)

    object Tapwrm extends Rot("11104") {
        override def canInstall(self : Card)(implicit g : Game) = g.successfulRunsThisTurn.exists(_.isCentral)
        override def triggers(self : Card, e : Event)(implicit g : Game) = trashOnPurge(self, e) ++ (e match {
            case TurnBegins(Runner) if g.credits(Corp) / 5 > 0 => $(MessageEffect(Runner, "uses Tapwrm"), gain(Runner, g.credits(Corp) / 5))
            case _ => $
        })
    }

    object Tracker extends Rot("11105") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) => $(ChooseServerForAction(Runner, self, g.servers, false, "tracker", "Tracker: choose a server"))
            case TurnEnds(Runner) => $(SetMemoEffect(self, ""))
            case RunInitiated(_) if thisCardRun(self) => $(AddFlagEffect(g.runFlag("fx-tracker", |(self))))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case ServerPickedAction(self, s, _) =>
                s.foreach { x => g.state(self).memo = x.name ; g.log(self, "targets", x) }
                NextAction
        }
        override def abilities(self : Card)(implicit g : Game) = {
            val s = g.serverByName(g.state(self).memo)
            $(Ability("run the targeted server (" + (g.state(self).memo == "").?("none").|(g.state(self).memo) + "): the first subroutine that would resolve is prevented", clickc(2), s.$./(x => RunEffect(x, |(self))), click = true, ok = s.exists(g.canRunServer), reason = "no targeted server"))
        }
    }

    object Flashbang extends Rot("12085") {
        override def pumps(self : Card)(implicit g : Game) = $(Pump(c(1), 1))
        override def abilities(self : Card)(implicit g : Game) = {
            val ice = encounteredIce.%(i => g.has(i, "Sentry"))
            $(Ability("derez the encountered sentry", c(6), ice.$./(i => DerezEffect(i)), ok = ice.any, reason = "not encountering a sentry"))
        }
    }

    object Aumakua extends Rot("12104") {
        override def strength(self : Card)(implicit g : Game) = virus(self)
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(1), 1, $()))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case BreachEnded(_, false) => $(AddCounterEffect(self, "virus", 1))
            case Exposed(_) => $(AddCounterEffect(self, "virus", 1))
            case _ => $
        }
    }

    object Lustig extends Fraud("13007", "Sentry", 1, 1, 3, 5)

    object Mammon extends Rot("13009") {
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility($(CounterCost("power", 1)), 1, $()))
        override def pumps(self : Card)(implicit g : Game) = $(Pump(c(2), 2))
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("place X power counters (X credits, start of turn)", NoCost, $(ChooseNumberEffect(Runner, self, 1, g.credits(Runner), "mammon", "Mammon: how many credits to spend?")), once = once("mammon"), ok = startOfTurn && g.credits(Runner) >= 1, reason = "only at the start of your turn"))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case NumberPickedAction(self, n, _) =>
                g.push(PayCreditsEffect(Runner, n), AddCounterEffect(self, "power", n))
                NextAction
        }
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnEnds(Runner) if g.counter(self, "power") > 0 => $(RemoveCountersEffect(self, "power"))
            case _ => $
        }
    }

    object Aurora extends Rot("20027") {
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(2), 1, $("Barrier")))
        override def pumps(self : Card)(implicit g : Game) = $(Pump(c(2), 3))
    }

    object Peacock extends Rot("20030") {
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(2), 1, $("Code Gate")))
        override def pumps(self : Card)(implicit g : Game) = $(Pump(c(2), 3))
    }

    object Pheromones extends Rot("20031") {
        override def creditsUsableForRun(self : Card)(implicit g : Game) = g.run.exists(_.server == HQ)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(HQ) => $(AddCounterEffect(self, "virus", 1))
            case TurnBegins(Runner) if g.counter(self, "credit") < virus(self) => $(RefillCreditsEffect(self, virus(self)))
            case _ => $
        }
    }

    object Puffer extends Rot("21004") {
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(1), 1, $("Sentry")))
        override def pumps(self : Card)(implicit g : Game) = $(Pump(c(2), 1))
        override def strength(self : Card)(implicit g : Game) = g.counter(self, "power")
        override def memory(self : Card)(implicit g : Game) = -g.counter(self, "power")
        override def abilities(self : Card)(implicit g : Game) = $(
            Ability("place 1 power counter (+1 strength, uses 1 more MU)", click(), $(AddCounterEffect(self, "power", 1)), click = true, ok = g.memoryFree >= 1, reason = "no free memory"),
            Ability("remove 1 power counter", click(), $(AddCounterEffect(self, "power", -1)), click = true, ok = g.counter(self, "power") > 0, reason = "no power counters"))
    }

    object Wari extends Rot("21024") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(HQ) if g.successfulRunsThisTurn.count(HQ) == 1 && g.allIce.exists(i => !g.isRezzed(i)) =>
                $(OptionalEffect(Runner, "Wari: trash it to name a subtype and expose a piece of ice?", $(ChooseOneEffect(Runner, "Wari: name a subtype", $("Barrier", "Code Gate", "Sentry")./(st => Alt(st, $(WariAction(self, st)))))), $))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case WariAction(self, st) =>
                val l = g.allIce.%(i => !g.isRezzed(i))
                g.push(TrashEffect(self, Runner, false), ChooseCardEffect(Runner, l, WariPurpose(self, st), "Wari: choose an unrezzed piece of ice to expose", false))
                NextAction
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case WariPurpose(self, st) =>
                cards.single.foreach { i =>
                    g.push(ExposeEffect($(i)))
                    if (g.has(i, st))
                        g.push(MessageEffect(Runner, "uses Wari to add the exposed ice to HQ"), ReturnToHandEffect(i))
                }
                NextAction
        }
    }

    object Amina extends Rot("21104") {
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(2), 3, $("Code Gate")))
        override def pumps(self : Card)(implicit g : Game) = $(Pump(c(2), 3))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SubsBroken(ice, l, b) if b == self && g.fxFullyBroke(ice, l, b) && !g.usedThisTurn(self, "amina") => $(MarkUsedEffect(self, "amina"), MessageEffect(Runner, "uses Amina"), lose(Corp, 1))
            case _ => $
        }
    }

    object Bankroll extends Rot("22011") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(_) => $(OptionalEffect(Runner, "Bankroll: place 1 credit on it?", $(AddCounterEffect(self, "credit", 1)), $))
            case _ => $
        }
        override def abilities(self : Card)(implicit g : Game) = {
            val n = g.counter(self, "credit")
            $(Ability("take all hosted credits (" + n + ")", $(TrashSelfCost), $(gain(Runner, n)), ok = n > 0, reason = "no hosted credits"))
        }
    }

    object Tycoon extends Rot("22012") {
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(1), 2, $("Barrier")))
        override def pumps(self : Card)(implicit g : Game) = $(Pump(c(2), 3))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case EncounterEnds(_) if broke(self) => $(MessageEffect(Corp, "gains 2 credits from Tycoon"), gain(Corp, 2))
            case _ => $
        }
    }

    object Demara extends Fraud("25034", "Barrier", 2, 2, 2, 3)

    object Faerie extends Rot("25035") {
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(0), 1, $("Sentry")))
        override def pumps(self : Card)(implicit g : Game) = $(Pump(c(1), 1))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case EncounterEnds(_) if broke(self) && g.isInstalled(self) => $(TrashEffect(self, Runner, false))
            case _ => $
        }
    }

    object Cache extends Rot("29004") {
        override def initialCounters(self : Card)(implicit g : Game) = Map("virus" -> 3)
        override def abilities(self : Card)(implicit g : Game) = $(Ability("gain 1 credit (1 virus counter)", NoCost, $(SpendVirusEffect(self, 1, false), gain(Runner, 1)), ok = virus(self) >= 1, reason = "no virus counters"))
    }

    // ------------------------------------------------------------------------------------------------------------
    // Neutral
    // ------------------------------------------------------------------------------------------------------------

    object Overmind extends Rot("05053") {
        override def initialCounters(self : Card)(implicit g : Game) = Map("power" -> g.memoryFree.atLeast(0))
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility($(CounterCost("power", 1)), 1, $()))
        override def pumps(self : Card)(implicit g : Game) = $(Pump(c(1), 1))
    }

    object BabaYaga extends Rot("11088") {
        override def canHost(self : Card, card : Card)(implicit g : Game) = card.data.isProgram && card.data.isIcebreaker && !g.has(card, "AI")
    }

    object SurveillanceNetworkKey extends Rot("14018") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case CardsDrawn(Corp, l) if g.phase == "action" && g.active.isCorp && l.any => $(RevealCardsEffect(Corp, l))
            case _ => $
        }
    }

    object SurveillanceNetworkKey2 extends Rot("14019") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case CardsDrawn(Corp, l) if g.phase == "action" && g.active.isCorp && l.any => $(RevealCardsEffect(Corp, l))
            case _ => $
        }
        override def abilities(self : Card)(implicit g : Game) = {
            val ok = g.run.exists(r => !r.ended && r.breachServer.none)
            $(
                Ability("access 1 additional card from R&D this run", c(2), $(AccessBonusEffect(RnD, 1)), once = once("snk-rd"), ok = ok, reason = "only during a run before the breach"),
                Ability("access 1 additional card from HQ this run", c(2), $(AccessBonusEffect(HQ, 1)), once = once("snk-hq"), ok = ok, reason = "only during a run before the breach"))
        }
    }

    object SneakdoorPrimeA extends Rot("14026") {
        override def abilities(self : Card)(implicit g : Game) = {
            val l = g.remotes.%(g.canRunServer)
            $(Ability("run a remote server (change the attacked server to a central when approaching it)", $(ClickCost(2)), $(RunAnyServerEffect(|(self), l)), click = true, ok = l.any, reason = "no remote server"))
        }
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case ApproachServer(s) if thisCardRun(self) && s.isRemote => $(ChooseOneEffect(Runner, "Sneakdoor Prime A: choose a central server to attack instead", Server.centrals./(x => Alt(x.name, $(ChangeRunServerEffect(x))))))
            case _ => $
        }
    }

    object SneakdoorPrimeB extends Rot("14027") {
        override def abilities(self : Card)(implicit g : Game) = {
            val l = Server.centrals.%(g.canRunServer)
            $(Ability("run a central server (change the attacked server to a remote when approaching it)", $(ClickCost(2)), $(RunAnyServerEffect(|(self), l)), click = true, ok = l.any, reason = "cannot run"))
        }
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case ApproachServer(s) if thisCardRun(self) && s.isCentral && g.remotes.any => $(ChooseOneEffect(Runner, "Sneakdoor Prime B: choose a remote server to attack instead", Alt("Keep attacking " + s.name, $) +: g.remotes./(x => Alt(x.name, $(ChangeRunServerEffect(x))))))
            case _ => $
        }
    }

    object RNGKey extends Rot("21029") {
        lazy val maxCost = Cards.all./~(x => x.cost.$ ++ x.advancementCost.$).max
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(s) if (s == HQ || s == RnD) && g.successfulRunsThisTurn.count(x => x == HQ || x == RnD) == 1 =>
                $(OptionalEffect(Runner, "RNG Key: guess a number?", $(ChooseNumberEffect(Runner, self, 0, maxCost, "rng", "RNG Key: guess a number")), $))
            case Accessed(x) if g.state(self).memo != "" => $(RngKeyAction(self, x))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case NumberPickedAction(self, n, _) =>
                g.state(self).memo = n.toString
                g.log(Runner, "guesses", n.hl, "(RNG Key)")
                NextAction
            case RngKeyAction(self, x) =>
                val guess = g.state(self).memo.toIntOption.|(-1)
                g.state(self).memo = ""
                if (x.data.cost.has(guess) || x.data.advancementCost.has(guess))
                    g.push(ChooseOneEffect(Runner, "RNG Key: the guess was right", $(Alt("Gain 3 credits", $(gain(Runner, 3))), Alt("Draw 2 cards", $(draw(Runner, 2))))))
                NextAction
        }
    }

    object Crypsis extends Rot("25061") {
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(1), 1, $()))
        override def pumps(self : Card)(implicit g : Game) = $(Pump(c(1), 1))
        override def abilities(self : Card)(implicit g : Game) = $(Ability("place 1 virus counter", click(), $(AddCounterEffect(self, "virus", 1)), click = true))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case EncounterEnds(_) if broke(self) && g.isInstalled(self) => $(CrypsisAction(self))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case CrypsisAction(self) =>
                if (virus(self) > 0)
                    g.push(SpendVirusEffect(self, 1, false))
                else
                    g.push(MessageEffect(Runner, "trashes Crypsis (no virus counter to remove)"), TrashEffect(self, Runner, false))
                NextAction
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Sunny Lebeau
    // ------------------------------------------------------------------------------------------------------------

    object GSStrikerM1 extends GlobalSec("09048", "Code Gate")
    object GSShrikeM2 extends GlobalSec("09049", "Sentry")
    object GSShermanM3 extends GlobalSec("09050", "Barrier")

    val all : $[CardImpl] = $(
        Ninja, Snitch, Crescentus, GormanDrip, Copycat, Leviathan, ExpertScheduleAnalyzer, Garrote, SavoirFaire, Alias, Breach, Bug,
        Gingerbread, GrapplingHook, Passport, Switchblade, CerberusRex, AuRevoir, Spike, Crowbar, Shiv, Mongoose, Vamadeva, Golden,
        Peregrine, Saker, Tapwrm, Tracker, Flashbang, Aumakua, Lustig, Mammon, Aurora, Peacock, Pheromones, Puffer, Wari, Amina,
        Bankroll, Tycoon, Demara, Faerie, Cache,
        Overmind, BabaYaga, SurveillanceNetworkKey, SurveillanceNetworkKey2, SneakdoorPrimeA, SneakdoorPrimeB, RNGKey, Crypsis,
        GSStrikerM1, GSShrikeM2, GSShermanM3,
    )
}
