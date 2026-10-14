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
// Rotated programs, part 3: Shaper
// ---------------------------------------------------------------------------------------------------------------------

object RotProgramCards3 {
    object NetShield extends Rot("01045") {
        override def preventions(self : Card, kind : String, n : Int)(implicit g : Game) =
            (kind == "net" && Prevent.damageCtx.exists(_.firstThisTurn) && g.preventUses(self) == 0).$(Ability("prevent 1 net damage (first net damage each turn)", c(1), $(Prevent.damage(1))))
    }

    object ZU13KeyMaster extends Rot("02007") {
        override def memory(self : Card)(implicit g : Game) = cloudMemory(self)
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(1), 1, $("Code Gate")))
        override def pumps(self : Card)(implicit g : Game) = $(Pump(c(1), 1))
    }

    object Snowball extends Rot("02027") {
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(1), 1, $("Barrier"), |("break 1 barrier subroutine (+1 strength for the run)"), $(PumpBreakerEffect(self, 1, RunDuration))))
        override def pumps(self : Card)(implicit g : Game) = $(Pump(c(1), 1))
    }

    object Creeper extends Rot("02089") {
        override def memory(self : Card)(implicit g : Game) = cloudMemory(self)
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(2), 1, $("Sentry")))
        override def pumps(self : Card)(implicit g : Game) = $(Pump(c(1), 1))
    }

    object DeepThought extends Rot("02108") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(RnD) => $(AddCounterEffect(self, "virus", 1))
            case TurnBegins(Runner) if virus(self) >= 3 && g.deck(Corp).any => $(LookAtEffect(Runner, g.deck(Corp).take(1), "Deep Thought: the top card of R&D"))
            case _ => $
        }
    }

    object Cloak extends Rot("03041") {
        override def initialCounters(self : Card)(implicit g : Game) = Map("credit" -> 1)
        // "Use this credit to pay for using icebreakers." (Cloak is a Stealth card: these are stealth credits)
        override def paysFor(self : Card, spend : Spend)(implicit g : Game) = spend.using.exists(g.isIcebreaker)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) => $(RefillCreditsEffect(self, 1))
            case _ => $
        }
    }

    object Dagger extends Rot("03042") {
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(1), 1, $("Sentry")))
        override def pumps(self : Card)(implicit g : Game) = $(Pump($(StealthCreditCost(1, 1)), 5, EncounterDuration, |("+5 strength (stealth credits only)")))
    }

    object Chakana extends Rot("03043") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(RnD) => $(AddCounterEffect(self, "virus", 1))
            case _ => $
        }
        override def advancementRequirementMod(self : Card, agenda : Card)(implicit g : Game) = (virus(self) >= 3).?(1).|(0)
    }

    object CyberCypher extends Rot("03044") {
        def serverOk(self : Card)(implicit g : Game) = g.run.exists(r => !r.ended && (g.state(self).memo == "" || r.server.name == g.state(self).memo))
        override def onInstall(self : Card)(implicit g : Game) = $(ChooseServerForAction(Runner, self, g.servers, false, "cypher", "Cyber-Cypher: choose a server"))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case ServerPickedAction(self, s, _) =>
                s.foreach { x => g.state(self).memo = x.name ; g.log(self, "targets", x) }
                NextAction
        }
        override def breaks(self : Card)(implicit g : Game) = serverOk(self).$(BreakAbility(c(1), 1, $("Code Gate")))
        override def pumps(self : Card)(implicit g : Game) = serverOk(self).$(Pump(c(1), 1))
    }

    object Sahasrara extends Rot("03047") {
        override def initialCounters(self : Card)(implicit g : Game) = Map("credit" -> 2)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) => $(RefillCreditsEffect(self, 2))
            case _ => $
        }
        // "Use these credits to install programs (you cannot use Sahasrara to install a program that trashes Sahasrara)."
        override def paysFor(self : Card, spend : Spend)(implicit g : Game) = spend.installing.exists(_.data.isProgram)
    }

    object Inti extends Rot("03048") {
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(1), 1, $("Barrier")))
        override def pumps(self : Card)(implicit g : Game) = $(Pump(c(2), 1, RunDuration, |("+1 strength for the remainder of the run")))
    }

    object FalseEcho extends Rot("04007") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case PassIce(ice) if !g.isRezzed(ice) && g.isInstalled(self) => $(OptionalEffect(Runner, "False Echo: trash it to make the Corp rez the passed ice or add it to HQ?", $(TrashEffect(self, Runner, false), FalseEchoAction(self, ice)), $))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case FalseEchoAction(self, ice) =>
                if (g.location.contains(ice) && !g.isRezzed(ice))
                    g.push(ChooseOneEffect(Corp, "False Echo: rez the passed ice or add it to HQ", $(
                        Alt("Rez the ice", $(RezEffect(ice, false)), g.canSpend(Corp, g.rezCost(ice), Spend.rez(ice)) && g.impl(ice).canRez(ice)),
                        Alt("Add the ice to HQ", $(ReturnToHandEffect(ice))))))
                NextAction
        }
    }

    object Torch extends Rot("04047") {
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(1), 1, $("Code Gate")))
        override def pumps(self : Card)(implicit g : Game) = $(Pump(c(1), 1))
    }

    object Sharpshooter extends Rot("04067") {
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility($(TrashSelfCost), 0, $("Destroyer")))
        override def pumps(self : Card)(implicit g : Game) = $(Pump(c(1), 2))
    }

    object Alpha extends Rot("04087") {
        override def breaks(self : Card)(implicit g : Game) = encounteredIce.exists(i => serverIce(i).lastOption.has(i)).$(BreakAbility(c(1), 1, $(), |("break 1 subroutine (outermost ice)")))
        override def pumps(self : Card)(implicit g : Game) = $(Pump(c(1), 1))
    }

    object Omega extends Rot("04088") {
        override def breaks(self : Card)(implicit g : Game) = encounteredIce.exists(i => serverIce(i).headOption.has(i)).$(BreakAbility(c(1), 1, $(), |("break 1 subroutine (innermost ice)")))
        override def pumps(self : Card)(implicit g : Game) = $(Pump(c(1), 1))
    }

    object Paintbrush extends Rot("04108") {
        override def abilities(self : Card)(implicit g : Game) = {
            val l = g.rezzedIce
            $(Ability("make a rezzed piece of ice gain a subtype until the end of the next run this turn", click(), $(ChooseCardEffect(Runner, l, PaintbrushPurpose(self), "Paintbrush: choose a rezzed piece of ice", false)), click = true, ok = l.any && g.state(self).target.none, reason = "no rezzed ice or already used"))
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(i => g.push(ChooseOneEffect(Runner, "Paintbrush: choose a subtype", $("Barrier", "Code Gate", "Sentry")./(st => Alt(st, $(GiveSubtypeAction(p.card, i, st)))))))
            NextAction
        }
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case RunEnds(_, _, _) if g.state(self).target.any => $(ClearSubtypeAction(self))
            case TurnEnds(Runner) if g.state(self).target.any => $(ClearSubtypeAction(self))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = RotProgramCards.subtypePerform(a)
    }

    object Leprechaun extends Rot("06019") {
        override def canHost(self : Card, card : Card)(implicit g : Game) = card.data.isProgram && g.hosted(self).num < 2
        override def memory(self : Card)(implicit g : Game) = hostedMemory(self)
    }

    object LLDSEnergyRegulator extends Rot("06039") {
        override def preventions(self : Card, kind : String, n : Int)(implicit g : Game) =
            (kind == "trash" && Prevent.ctx.exists(_.cards.exists(_.data.isHardware))).??($(
                Ability("prevent an installed piece of hardware from being trashed", c(3), $(PreventTrashChooseEffect(self, $("Hardware")))),
                Ability("prevent an installed piece of hardware from being trashed", $(TrashSelfCost), $(PreventTrashChooseEffect(self, $("Hardware"))))))
    }

    object Refractor extends Rot("06057") {
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(1), 1, $("Code Gate")))
        override def pumps(self : Card)(implicit g : Game) = $(Pump($(StealthCreditCost(1, 1)), 3, EncounterDuration, |("+3 strength (stealth credits only)")))
    }

    object CollectiveConsciousness extends Rot("06116") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Rezzed(x) if x.data.isIce && g.deck(Runner).any => $(MessageEffect(Runner, "uses Collective Consciousness to draw 1 card"), draw(Runner, 1))
            case _ => $
        }
    }

    object Sage extends Greek("06117", 1, "Barrier", 1, "Code Gate")

    object StudyGuide extends Rot("08028") {
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(1), 1, $("Code Gate")))
        override def strength(self : Card)(implicit g : Game) = g.counter(self, "power")
        override def abilities(self : Card)(implicit g : Game) = $(Ability("place 1 power counter (+1 strength)", c(2), $(AddCounterEffect(self, "power", 1))))
    }

    object AnalogDreamers extends Rot("08048") {
        override def abilities(self : Card)(implicit g : Game) = $(Ability("run R&D (shuffle an unrezzed, unadvanced card into R&D instead of breaching)", click(), $(RunEffect(RnD, |(self))), click = true))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(RnD) if thisCardRun(self) =>
                val l = g.installedCards(Corp).%(x => !x.data.isIce && !g.isRezzed(x) && g.advancement(x) == 0)
                $(ReplaceBreachEffect) ++ l.any.$(ChooseCardEffect(Runner, l, AnalogPurpose(self), "Analog Dreamers: choose a card to shuffle into R&D", false))
            case _ => $
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach { x =>
                g.moveTo(x, DeckZone(Corp))
                g.cleanupRemotes()
                g.log(Runner, "shuffles a card into R&D (Analog Dreamers)")
                g.push(ShuffleDeckEffect(Corp))
            }
            NextAction
        }
    }

    object Hyperdriver extends Rot("08070") {
        override def abilities(self : Card)(implicit g : Game) = $(Ability("remove Hyperdriver from the game to gain 3 clicks (start of turn)", $(RemoveSelfCost), $(GainClicksEffect(Runner, 3)), ok = startOfTurn, reason = "only at the start of your turn"))
    }

    object DaVinci extends Rot("08107") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(_) => $(AddCounterEffect(self, "power", 1))
            case _ => $
        }
        override def abilities(self : Card)(implicit g : Game) = {
            val n = g.counter(self, "power")
            val l = g.hand(Runner).%(x => (x.data.isProgram || x.data.isHardware || x.data.isResource) && g.installCost(x, None) <= n && (!g.impl(x).mustBeHosted(x) || g.allIce.exists(h => g.impl(x).hostRequirement(x, h))))
            $(Ability("install a card from the grip with install cost at most " + n + ", ignoring the install cost", $(TrashSelfCost), $(ChooseCardEffect(Runner, l, InstallPayingPurpose(99, None, false), "DaVinci: choose a card to install", false)), ok = l.any, reason = "no card in the grip with a low enough install cost"))
        }
    }

    object Panchatantra extends Rot("10008") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case EncounterIce(ice) if !g.usedThisTurn(self, "pancha") =>
                val l = g.iceSubtypes.%!(st => st == "Barrier" || st == "Code Gate" || st == "Sentry")
                $(OptionalEffect(Runner, "Panchatantra: give the encountered ice a subtype until the end of the run?", $(MarkUsedEffect(self, "pancha"), ChooseOneEffect(Runner, "Panchatantra: choose a subtype", l./(st => Alt(st, $(GiveSubtypeAction(self, ice, st)))))), $))
            case RunEnds(_, _, _) if g.state(self).target.any => $(ClearSubtypeAction(self))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = RotProgramCards.subtypePerform(a)
    }

    object Sadyojata extends Deva("10044") {
        def condition(ice : Card)(implicit g : Game) = g.subtypes(ice).num >= 3
        def label = "ice with 3 or more subtypes"
    }

    object Brahman extends Rot("10062") {
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(1), 2, $()))
        override def pumps(self : Card)(implicit g : Game) = $(Pump(c(2), 1))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case EncounterEnds(_) if broke(self) =>
                val l = runnerPrograms.%(x => !g.has(x, "Virus") && !g.state(x).facedown)
                l.any.$(ChooseCardEffect(Runner, l, TopOfDeckPurpose(Runner), "Brahman: choose a non-virus program to add to the top of the stack", false))
            case _ => $
        }
    }

    object Ankusa extends Rot("10101") {
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(2), 1, $("Barrier")))
        override def pumps(self : Card)(implicit g : Game) = $(Pump(c(1), 1))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SubsBroken(ice, _, b) if b == self && g.has(ice, "Barrier") && g.unbrokenSubs(ice).none => $(MessageEffect(Runner, "uses Ankusa to add the barrier to HQ"), ReturnToHandEffect(ice))
            case _ => $
        }
    }

    object DaiV extends Rot("11006") {
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility($(StealthCreditCost(2, 2)), 0, $(), |("break all subroutines (stealth credits only)")))
        override def pumps(self : Card)(implicit g : Game) = $(Pump(c(1), 1))
    }

    object Houdini extends Rot("11045") {
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(1), 1, $("Code Gate")))
        override def pumps(self : Card)(implicit g : Game) = $(Pump($(StealthCreditCost(2, 1)), 4, RunDuration, |("+4 strength for the remainder of the run (at least 1 stealth credit)")))
    }

    object Blackstone extends Rot("11068") {
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(1), 1, $("Barrier")))
        override def pumps(self : Card)(implicit g : Game) = $(Pump($(StealthCreditCost(3, 1)), 4, RunDuration, |("+4 strength for the remainder of the run (at least 1 stealth credit)")))
    }

    object Equivocation extends Rot("11084") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(RnD) if g.deck(Corp).any => $(OptionalEffect(Runner, "Equivocation: reveal the top card of R&D?", $(EquivocationAction(self)), $))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case EquivocationAction(self) =>
                g.deck(Corp).headOption.foreach { x =>
                    g.push(RevealCardsEffect(Corp, $(x)), OptionalEffect(Runner, "Equivocation: force the Corp to draw the revealed card?", $(MessageEffect(Corp, "is forced to draw (Equivocation)"), DrawEffect(Corp, 1)), $))
                }
                NextAction
        }
    }

    object Misdirection extends Rot("11085") {
        override def abilities(self : Card)(implicit g : Game) = {
            val max = g.tags.atMost(g.credits(Runner))
            $(Ability("remove X tags (X credits)", $(ClickCost(2)), $(ChooseNumberEffect(Runner, self, 1, max, "misdirection", "Misdirection: how many tags to remove?")), click = true, ok = max > 0, reason = "not tagged or no credits"))
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case NumberPickedAction(self, n, _) =>
                g.push(PayCreditsEffect(Runner, n), RemoveTagsEffect(n))
                NextAction
        }
    }

    object Fawkes extends Rot("11108") {
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(1), 1, $("Sentry")))
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("+X strength for the remainder of the run (X credits, at least 1 stealth credit)", NoCost, $(ChooseNumberEffect(Runner, self, 1, g.creditsFor(Runner, Spend.pump(self, encounteredIce)), "fawkes", "Fawkes: how many credits to spend?")), ok = g.canSpend(Runner, 1, Spend.pump(self, encounteredIce), 1) && g.run.exists(r => !r.ended), reason = "needs a run and a stealth credit"))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case NumberPickedAction(self, n, _) =>
                g.push(SpendEffect(Runner, n, Spend.pump(self, encounteredIce), 1, $(PumpBreakerEffect(self, n, RunDuration))))
                NextAction
        }
    }

    object CustomizedSecretary extends Rot("12027") {
        override def onInstall(self : Card)(implicit g : Game) = $(SecretaryAction(self))
        override def abilities(self : Card)(implicit g : Game) = {
            val l = g.hosted(self).%(x => g.state(x).uninstalled && x.data.isProgram && g.canAffordInstall(x, 0))
            $(Ability("install a hosted program", click(), $(ChooseCardEffect(Runner, l, SecretaryInstallPurpose(self), "Customized Secretary: choose a hosted program to install", false)), click = true, ok = l.any, reason = "no affordable hosted program"))
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case SecretaryAction(self) =>
                val l = g.deck(Runner).take(5)
                if (l.any)
                    g.push(RevealCardsEffect(Runner, l), SecretaryHostAction(self, l), ShuffleDeckEffect(Runner))
                NextAction
            case SecretaryHostAction(self, remaining) =>
                val l = remaining.%(x => x.data.isProgram && g.zone(x) == DeckZone(Runner))
                if (l.any)
                    g.push(ChooseCardEffect(Runner, l, SecretaryPurpose(self, remaining), "Customized Secretary: choose a program to host", true))
                NextAction
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case SecretaryPurpose(self, remaining) =>
                cards.single.foreach(x => g.push(HostOnEffect(x, self, true), SecretaryHostAction(self, remaining.but(x))))
                NextAction
            case SecretaryInstallPurpose(self) =>
                cards.single.foreach { x =>
                    g.moveTo(x, HandZone(Runner))
                    g.pushAll(g.installRunner(x, 0))
                }
                NextAction
        }
    }

    object Inversificator extends Rot("12048") {
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(1), 1, $("Code Gate")))
        override def pumps(self : Card)(implicit g : Game) = $(Pump(c(1), 1))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SubsBroken(ice, _, b) if b == self && g.unbrokenSubs(ice).none && !g.usedThisTurn(self, "inv") => $(MarkUsedEffect(self, "inv"), SetTargetEffect(self, |(ice)))
            case PassIce(ice) if g.state(self).target.has(ice) =>
                val l = g.allIce.but(ice)
                $(SetTargetEffect(self, None)) ++ l.any.$(OptionalEffect(Runner, "Inversificator: swap the passed ice with another piece of ice?", $(ChooseCardEffect(Runner, l, InversificatorPurpose(self, ice), "Inversificator: choose a piece of ice to swap with", false)), $))
            case _ => $
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case InversificatorPurpose(self, ice) =>
                cards.single.foreach(x => g.push(SwapIceEffect(ice, x)))
                NextAction
        }
    }

    object MassDriver extends Rot("12067") {
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(2), 1, $("Code Gate")))
        override def pumps(self : Card)(implicit g : Game) = $(Pump(c(1), 1))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SubsBroken(ice, _, b) if b == self && g.unbrokenSubs(ice).none => $(SetTargetEffect(self, |(ice)))
            case EncounterIce(ice) if g.state(self).target.exists(_ != ice) => $(MassDriverAction(self, ice))
            case RunEnds(_, _, _) if g.state(self).target.any => $(SetTargetEffect(self, None))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case MassDriverAction(self, ice) =>
                g.state(self).target = None
                g.log(Runner, "uses Mass-Driver")
                g.push(PreventSubsEffect(ice, 3))
                NextAction
        }
    }

    object Maven extends Rot("12087") {
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(2), 1, $()))
        override def strength(self : Card)(implicit g : Game) = runnerPrograms.%(g.isActive).num
    }

    object NaNotK extends Rot("12088") {
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(1), 1, $("Sentry")))
        override def pumps(self : Card)(implicit g : Game) = $(Pump(c(3), 2))
        override def strength(self : Card)(implicit g : Game) = g.run.%(r => !r.ended)./(r => g.ice(r.server).num).|(0)
    }

    object Adept extends Greek("13017", 1, "Barrier", 1, "Sentry")
    object Savant extends Greek("13018", 2, "Code Gate", 1, "Sentry")

    object Dhegdheer extends Rot("13020") {
        override def memory(self : Card)(implicit g : Game) = hostedMemory(self)
        override def abilities(self : Card)(implicit g : Game) = {
            val l = g.hand(Runner).%(x => x.data.isProgram && !g.impl(x).mustBeHosted(x) && g.canAffordInstall(x, 1))
            $(Ability("install a program from the grip hosted on Dhegdheer, paying 1 credit less (the program uses no memory)", click(), $(ChooseCardEffect(Runner, l, DhegdheerPurpose(self), "Dhegdheer: choose a program to install", true)), click = true, ok = g.hosted(self).none && l.any, reason = "already hosting a program or nothing to install"))
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(x => g.push(RunnerInstallPayingEffect(x, |(p.card), 1)))
            NextAction
        }
    }

    object MagnumOpus extends Rot("20050") {
        override def abilities(self : Card)(implicit g : Game) = $(Ability("gain 2 credits", click(), $(gain(Runner, 2)), click = true))
    }

    object Upya extends Rot("21007") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(RnD) => $(OptionalEffect(Runner, "Upya: place 1 power counter on it?", $(AddCounterEffect(self, "power", 1)), $))
            case _ => $
        }
        override def abilities(self : Card)(implicit g : Game) = $(Ability("gain 2 clicks", $(ClickCost(1), CounterCost("power", 3)), $(GainClicksEffect(Runner, 2)), click = true, once = once("upya")))
    }

    object Takobi extends Rot("21026") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SubsBroken(ice, _, _) if g.unbrokenSubs(ice).none => $(OptionalEffect(Runner, "Takobi: place 1 power counter on it?", $(AddCounterEffect(self, "power", 1)), $))
            case _ => $
        }
        override def abilities(self : Card)(implicit g : Game) = {
            val l = g.installedCards(Runner).%(x => x.data.isIcebreaker && !g.has(x, "AI") && g.isActive(x))
            $(Ability("give +3 strength to a non-AI icebreaker for the encounter", $(CounterCost("power", 2)), $(ChooseCardEffect(Runner, l, TakobiPurpose(self), "Takobi: choose an icebreaker", false)), ok = encounteredIce.any && l.any, reason = "needs an encounter and a non-AI icebreaker"))
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(x => g.push(PumpBreakerEffect(x, 3, EncounterDuration)))
            NextAction
        }
    }

    object Nyashia extends Rot("21067") {
        override def initialCounters(self : Card)(implicit g : Game) = Map("power" -> 3)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case BreachServer(RnD) if g.counter(self, "power") > 0 => $(OptionalEffect(Runner, "Nyashia: spend 1 power counter to access 1 additional card from R&D?", $(AddCounterEffect(self, "power", -1), AccessBonusEffect(RnD, 1)), $))
            case _ => $
        }
    }

    object Laamb extends Wrestler("21086", "Barrier") {
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(2), 0, $("Barrier")))
        override def pumps(self : Card)(implicit g : Game) = $(Pump(c(3), 6))
    }

    object Engolo extends Wrestler("21108", "Code Gate") {
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(1), 1, $("Code Gate")))
        override def pumps(self : Card)(implicit g : Game) = $(Pump(c(2), 4))
    }

    object Ika extends Rot("22019") {
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(1), 2, $("Sentry")))
        override def pumps(self : Card)(implicit g : Game) = $(Pump(c(2), 3))
        override def abilities(self : Card)(implicit g : Game) = {
            val l = g.allIce.%(h => !host(self).has(h))
            $(hostIceAbility(self, "host Ika on a piece of ice", l, c(2), false))
        }
    }

    object Kyuban extends Rot("22020") {
        override def mustBeHosted(self : Card)(implicit g : Game) = true
        override def hostRequirement(self : Card, host : Card)(implicit g : Game) = host.data.isIce
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case PassIce(ice) if host(self).has(ice) => $(MessageEffect(Runner, "gains 2 credits from Kyuban"), gain(Runner, 2))
            case _ => $
        }
    }

    object BatteringRam extends Rot("25052") {
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(2), 2, $("Barrier")))
        override def pumps(self : Card)(implicit g : Game) = $(Pump(c(1), 1, RunDuration, |("+1 strength for the remainder of the run")))
    }

    object DeusX extends Rot("25053") {
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility($(TrashSelfCost), 0, $("AP")))
        override def preventions(self : Card, kind : String, n : Int)(implicit g : Game) =
            (kind == "net").$(Ability("trash Deus X to prevent all net damage", $(TrashSelfCost), $(Prevent.allDamage)))
    }

    object Pipeline extends Rot("25055") {
        override def breaks(self : Card)(implicit g : Game) = $(BreakAbility(c(1), 1, $("Sentry")))
        override def pumps(self : Card)(implicit g : Game) = $(Pump(c(2), 1, RunDuration, |("+1 strength for the remainder of the run")))
    }

    object CerberusLady extends Cerberus("29006", "Barrier")

    val all : $[CardImpl] = $(
        NetShield, ZU13KeyMaster, Snowball, Creeper, DeepThought, Cloak, Dagger, Chakana, CyberCypher, Sahasrara, Inti, FalseEcho,
        Torch, Sharpshooter, Alpha, Omega, Paintbrush, Leprechaun, LLDSEnergyRegulator, Refractor, CollectiveConsciousness, Sage,
        StudyGuide, AnalogDreamers, Hyperdriver, DaVinci, Panchatantra, Sadyojata, Brahman, Ankusa, DaiV, Houdini, Blackstone,
        Equivocation, Misdirection, Fawkes, CustomizedSecretary, Inversificator, MassDriver, Maven, NaNotK, Adept, Savant, Dhegdheer,
        MagnumOpus, Upya, Takobi, Nyashia, Laamb, Engolo, Ika, Kyuban, BatteringRam, DeusX, Pipeline, CerberusLady,
    )
}
