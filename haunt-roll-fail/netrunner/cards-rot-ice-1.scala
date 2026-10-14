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
// Rotated ice, titles A - L (see cards-rot-ice.scala)
// ---------------------------------------------------------------------------------------------------------------------

object RotIceCards1 {
    object Aiki extends RI("11032") {
        override def subroutines(self : Card)(implicit g : Game) = $(psi("the Runner draws 2 cards", $(draw(Runner, 2))), netSub(1), netSub(1))
    }

    object Aimor extends RI("21032") {
        override def subroutines(self : Card)(implicit g : Game) = $(Sub("Trash the top 3 cards of the stack. Trash this ice", CopyAbilityEffect($(TrashTopEffect(Runner, 3, false), TrashEffect(self, Corp, false)))))
    }

    // Anansi: 3 net damage when the encounter ends with unbroken subroutines (the broken subroutines are counted through the SubsBroken events)
    object Anansi extends RI("21051") {
        override def subroutines(self : Card)(implicit g : Game) = $(
            Sub("Rearrange the top 5 cards of R&D", ArrangeTopEffect(Corp, Corp, 5)),
            Sub("Draw 1 card. The Runner may pay 2[c] to draw 1 card", RotSubAction(self, 1)),
            netSub(1))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case EncounterIce(c) if c == self => $(SetMemoEffect(self, "0"))
            case SubsBroken(c, l, _) if c == self => $(SetMemoEffect(self, (memoInt(self) + l.num).toString))
            case EncounterEnds(c) if c == self && g.subs(self).num > memoInt(self) => $(MessageEffect(Corp, "uses Anansi (unbroken subroutines)"), net(3), clearMemo(self))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case RotSubAction(self, 1) =>
                if (g.credits(Runner) >= 2)
                    g.push(OptionalEffect(Runner, "Anansi: pay 2 credits to draw 1 card?", $(LoseCreditsEffect(Runner, 2), draw(Runner, 1)), $))
                g.push(OptionalEffect(Corp, "Anansi: draw 1 card?", $(draw(Corp, 1)), $))
                NextAction
        }
    }

    object Archangel extends RI("09013") {
        override def onAccess(self : Card)(implicit g : Game) =
            (!g.isInstalled(self) && g.credits(Corp) >= 3).$(OptionalEffect(Corp, "Archangel: pay 3 credits to force the Runner to encounter it?", $(LoseCreditsEffect(Corp, 3), PhantomEncounterEffect(self)), $))
        override def subroutines(self : Card)(implicit g : Game) = $(trace(6, "Add 1 installed Runner card to the grip", $(ChooseCardEffect(Corp, g.runnerInstalled, ReturnToHandPurpose, "Archangel: choose an installed Runner card to add to the grip", false))))
    }

    object Architect extends RI("06061") {
        override def untrashableByRunner(self : Card)(implicit g : Game) = true
        override def subroutines(self : Card)(implicit g : Game) = $(
            Sub("Look at the top 5 cards of R&D. You may install 1 of them, ignoring all costs", RotSubAction(self, 1)),
            Sub("You may install 1 card from HQ or Archives", ChooseCardEffect(Corp, (g.hand(Corp) ++ g.discard(Corp)).%(corpInstallable), InstallChooseServerPurpose(false), "Architect: install a card from HQ or Archives?", true)))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case RotSubAction(self, 1) =>
                val l = g.deck(Corp).take(5)
                if (l.any)
                    g.push(LookAtEffect(Corp, l, "Architect: the top cards of R&D"), ChooseCardEffect(Corp, l.%(corpInstallable), InstallPayingPurpose(99, None, false), "Architect: install a card ignoring all costs?", true))
                NextAction
        }
    }

    object Ashigaru extends RI("06064") {
        override def subroutines(self : Card)(implicit g : Game) = g.hand(Corp).num.times(etrSub)
    }

    object Assassin extends RI("09028") {
        override def subroutines(self : Card)(implicit g : Game) = $(trace(5, "Do 3 net damage", $(net(3))), trace(4, "Trash 1 installed program", $(trashProgramSub.effect)))
    }

    object AsteroidBelt extends RI("07012") {
        override def advanceable(self : Card)(implicit g : Game) = true
        override def rezCostMod(self : Card, card : Card)(implicit g : Game) = (card == self).?(-3 * g.advancement(self)).|(0)
        override def subroutines(self : Card)(implicit g : Game) = $(etrSub)
    }

    object Authenticator extends RI("12055") {
        override def onEncounter(self : Card)(implicit g : Game) =
            g.run.exists(r => !r.bypass).$(OptionalEffect(Runner, "Authenticator: take 1 tag to bypass it?", $(tag(1), BypassEffect), $))
        override def subroutines(self : Card)(implicit g : Game) = $(gainSub(2), etrSub)
    }

    object Bailiff extends RI("10056") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SubsBroken(c, l, _) if c == self => $(MessageEffect(Corp, "uses Bailiff"), gain(Corp, l.num))
            case _ => $
        }
        override def subroutines(self : Card)(implicit g : Game) = $(etrSub)
    }

    object Bandwidth extends RI("08016") {
        override def subroutines(self : Card)(implicit g : Game) = $(Sub("Give the Runner 1 tag. If the run is successful, remove 1 tag", CopyAbilityEffect($(tag(1), SetMemoEffect(self, "run:" + g.runKeyString)))))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(_) if memoIs(self, "run:" + g.runKeyString) => $(MessageEffect(Corp, "uses Bandwidth"), RemoveTagsEffect(1), clearMemo(self))
            case RunEnds(_, _, _) if g.state(self).memo != "" => $(clearMemo(self))
            case _ => $
        }
    }

    object Bastion extends RI("03026") {
        override def subroutines(self : Card)(implicit g : Game) = $(etrSub)
    }

    object Battlement extends RI("12057") {
        override def subroutines(self : Card)(implicit g : Game) = $(etrSub, etrSub)
    }

    object Blockchain extends RI("22053") {
        def gainLose = Sub("Gain 1[c]. The Runner loses 1[c]", CopyAbilityEffect($(gain(Corp, 1), lose(Runner, 1))))
        override def subroutines(self : Card)(implicit g : Game) = {
            val n = g.discard(Corp).%(c => g.state(c).seen && c.data.isOperation && g.has(c, "Transaction")).num / 2
            n.times(gainLose) ++ $(gainLose, etrSub)
        }
    }

    object Bloodletter extends RI("13047") {
        override def subroutines(self : Card)(implicit g : Game) = $(Sub("The Runner trashes 1 installed program or the top 2 cards of the stack", RotSubAction(self, 1)))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case RotSubAction(self, 1) =>
                if (g.runnerInstalled.%(_.data.isProgram).none)
                    g.push(TrashTopEffect(Runner, 2, false))
                else
                    g.push(choose("choose one", $(
                        Alt("Trash 1 installed program", $(RunnerTrashInstalledEffect("Program", Runner, "Bloodletter: choose a program to trash"))),
                        Alt("Trash the top 2 cards of the stack", $(TrashTopEffect(Runner, 2, false)), g.deck(Runner).any))))
                NextAction
        }
    }

    object Bloom extends RI("12032") {
        override def subroutines(self : Card)(implicit g : Game) = $(
            Sub("Install 1 piece of ice from HQ protecting another server, ignoring all costs", ChooseCardEffect(Corp, g.hand(Corp).%(_.data.isIce), InstallPayingChooseServerPurpose(99, g.servers.%(s => !thisServer(self, s)), true, false), "Bloom: install a piece of ice from HQ protecting another server", false)),
            Sub("Install 1 piece of ice from HQ directly inside this ice, ignoring all costs", ChooseCardEffect(Corp, g.hand(Corp).%(_.data.isIce), RotPurpose(self, 1), "Bloom: install a piece of ice from HQ protecting this server", false)))
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach { c =>
                g.serverOf(p.card).foreach { s =>
                    val idx = g.ice(s).indexOf(p.card).atLeast(0)
                    g.push(CorpInstallEffect(c, |(s), true), MoveIceToIndexEffect(c, idx))
                }
            }
            NextAction
        }
    }

    object BorderControl extends RI("28005") {
        override def abilities(self : Card)(implicit g : Game) = $(Ability("end the run", $(TrashSelfCost), $(EndRunEffect), ok = g.isRezzed(self) && runOnThisServer(self), reason = "only during a run on this server"))
        override def subroutines(self : Card)(implicit g : Game) = $(Sub("Gain 1[c] for each piece of ice protecting this server (" + iceHere(self).num + ")", gain(Corp, iceHere(self).num)), etrSub)
    }

    // Brainstorm / Komainu: when encountered, gain 1 subroutine per card in the grip for the remainder of the run
    object Brainstorm extends RI("10086") {
        override def onEncounter(self : Card)(implicit g : Game) = $(SetMemoEffect(self, g.hand(Runner).num.toString))
        override def subroutines(self : Card)(implicit g : Game) = memoInt(self).times(coreSub(1))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case RunEnds(_, _, _) if g.state(self).memo != "" => $(clearMemo(self))
            case _ => $
        }
    }

    object Builder extends RI("07016") {
        def place(self : Card)(implicit g : Game) = Sub("Place 1 advancement counter on a piece of ice that can be advanced protecting this server", ChooseCardEffect(Corp, iceHere(self).%(g.isAdvanceable), AdvancePurpose(1), "Builder: choose a piece of ice", false))
        override def subroutines(self : Card)(implicit g : Game) = $(place(self), place(self))
        override def abilities(self : Card)(implicit g : Game) = $(Ability("move this ice to the outermost position of any server", click(), $(ChooseServerForAction(Corp, self, g.servers, false, "builder", "Builder: move it to the outermost position of")), click = true, ok = g.isRezzed(self), reason = "not rezzed"))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case ServerPickedAction(self, s, "builder") =>
                s.foreach(s => g.push(MoveIceOutermostEffect(self, s)))
                NextAction
        }
    }

    object Bulwark extends RI("11078") {
        def gainEtr = Sub("Gain 2[c] and end the run", CopyAbilityEffect($(gain(Corp, 2), EndRunEffect)))
        override def onRez(self : Card)(implicit g : Game) = takeBadPub
        override def onEncounter(self : Card)(implicit g : Game) = g.activeCards(Runner).exists(c => g.has(c, "AI")).$(MessageEffect(Corp, "uses Bulwark (an AI is installed)"), gain(Corp, 2))
        override def subroutines(self : Card)(implicit g : Game) = $(runnerTrashProgramSub, gainEtr, gainEtr)
    }

    object BurkeBugs extends RI("02119") {
        override def subroutines(self : Card)(implicit g : Game) = $(trace(0, "The Runner trashes 1 installed program", $(runnerTrashProgramSub.effect)))
    }

    object Caduceus extends RI("25131") {
        override def subroutines(self : Card)(implicit g : Game) = $(trace(3, "Gain 3[c]", $(gain(Corp, 3))), trace(2, "End the run", $(EndRunEffect)))
    }

    object CellPortal extends RI("01074") {
        override def subroutines(self : Card)(implicit g : Game) = $(Sub("The Runner approaches the outermost piece of ice protecting this server. Derez this ice", RotSubAction(self, 1)))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case RotSubAction(self, 1) =>
                g.runServer.foreach(s => g.push(JackOutOfferEffect, DerezEffect(self), RedirectRunEffect(s, g.ice(s).num, true)))
                NextAction
        }
    }

    // morph ice: the breakers of the printed subtype cannot break it while it has an odd number of advancement counters
    abstract class Morph(code : String, base : String, other : String) extends RI(code) {
        override def advanceable(self : Card)(implicit g : Game) = true
        override def canBreakWith(self : Card, breaker : Card)(implicit g : Game) = !morphed(self) || g.impl(breaker).breaks(breaker).exists(b => b.subtypes.none || b.subtypes.has(other))
        def sync(self : Card)(implicit g : Game) : ForcedAction = ExtraSubtypesEffect(self, $(other), morphed(self))
        override def onRez(self : Card)(implicit g : Game) = $(sync(self))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Advanced(c, _) if c == self => $(sync(self))
            case CounterAdded(c, "advancement") if c == self => $(sync(self))
            case _ => $
        }
    }

    object Changeling extends Morph("06069", "Barrier", "Sentry") {
        override def subroutines(self : Card)(implicit g : Game) = $(etrSub)
    }

    object Checkpoint extends RI("07017") {
        override def onRez(self : Card)(implicit g : Game) = takeBadPub
        override def subroutines(self : Card)(implicit g : Game) = $(trace(5, "Do 3 meat damage when this run is successful", $(SetMemoEffect(self, "run:" + g.runKeyString))))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(_) if memoIs(self, "run:" + g.runKeyString) => $(MessageEffect(Corp, "uses Checkpoint"), meat(3), clearMemo(self))
            case RunEnds(_, _, _) if g.state(self).memo != "" => $(clearMemo(self))
            case _ => $
        }
    }

    object Chetana extends RI("10089") {
        override def subroutines(self : Card)(implicit g : Game) = $(
            Sub("Each player gains 2[c]", CopyAbilityEffect($(gain(Runner, 2), gain(Corp, 2)))),
            psi("do 1 net damage for each card in the grip", $(RotSubAction(self, 1))))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case RotSubAction(self, 1) =>
                g.push(net(g.hand(Runner).num))
                NextAction
        }
    }

    object Chimera extends RI("02060") {
        val kinds = $("Barrier", "Code Gate", "Sentry")
        override def onRez(self : Card)(implicit g : Game) = $(ChooseOneEffect(Corp, "Chimera: choose a subtype", kinds./(k => Alt(k, $(ExtraSubtypesEffect(self, kinds, false), AddSubtypeEffect(self, k))))))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnEnds(_) if g.isRezzed(self) => $(DerezEffect(self), ExtraSubtypesEffect(self, kinds, false))
            case _ => $
        }
        override def subroutines(self : Card)(implicit g : Game) = $(etrSub)
    }

    object Chiyashi extends RI("11112") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SubsBroken(c, l, _) if c == self && g.activeCards(Runner).exists(x => g.has(x, "AI")) => $(MessageEffect(Corp, "uses Chiyashi (an AI is installed)"), TrashTopEffect(Runner, 2 * l.num, false))
            case _ => $
        }
        override def subroutines(self : Card)(implicit g : Game) = $(netSub(2), netSub(2), etrSub)
    }

    object Chrysalis extends RI("11013") {
        override def onAccess(self : Card)(implicit g : Game) = (!g.isInstalled(self)).$(PhantomEncounterEffect(self))
        override def subroutines(self : Card)(implicit g : Game) = $(netSub(2))
    }

    // Chum: the next ice encountered this run gets +2 strength; 3 net damage if its subroutines are not all broken when the encounter ends
    object Chum extends RI("01075") {
        override def subroutines(self : Card)(implicit g : Game) = $(Sub("The next piece of ice the Runner encounters this run gets +2 strength; if its subroutines are not broken, do 3 net damage", SetMemoEffect(self, "armed")))
        override def iceStrengthMod(self : Card, ice : Card)(implicit g : Game) = (g.isRezzed(self) && g.state(self).target.has(ice) && encountering(self).has(ice)).?(2).|(0)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case EncounterIce(c) if c != self && memoIs(self, "armed") => $(SetTargetEffect(self, |(c)), SetMemoEffect(self, "0"), MessageEffect(Corp, "uses Chum: " + g.asciiTitle(c) + " gets +2 strength"))
            case SubsBroken(c, l, _) if g.state(self).target.has(c) => $(SetMemoEffect(self, (memoInt(self) + l.num).toString))
            case EncounterEnds(c) if g.state(self).target.has(c) =>
                (g.subs(c).num > memoInt(self)).$(MessageEffect(Corp, "uses Chum (unbroken subroutines)"), net(3)) ++ $(SetTargetEffect(self, None), clearMemo(self))
            case RunEnds(_, _, _) if g.state(self).memo != "" || g.state(self).target.any => $(SetTargetEffect(self, None), clearMemo(self))
            case _ => $
        }
    }

    object ClairvoyantMonitor extends RI("08055") {
        override def subroutines(self : Card)(implicit g : Game) = $(psi("place 1 advancement counter on an installed card and end the run", $(ChooseCardEffect(Corp, g.installedCards(Corp), AdvancePurpose(1), "Clairvoyant Monitor: choose an installed card", false), EndRunEffect)))
    }

    object Cobra extends RI("10074") {
        override def subroutines(self : Card)(implicit g : Game) = $(trashProgramSub, netSub(2))
    }

    object Conundrum extends RI("12120") {
        override def strength(self : Card)(implicit g : Game) = g.activeCards(Runner).exists(c => g.has(c, "AI")).?(3).|(0)
        override def subroutines(self : Card)(implicit g : Game) = $(runnerTrashProgramSub, loseClickSub, etrSub)
    }

    object CortexLock extends RI("08014") {
        override def subroutines(self : Card)(implicit g : Game) = $(Sub("Do 1 net damage for each unused MU (" + g.memoryFree.atLeast(0) + ")", net(g.memoryFree.atLeast(0))))
    }

    object Crick extends RI("08034") {
        override def strength(self : Card)(implicit g : Game) = protecting(self, Archives).?(3).|(0)
        override def subroutines(self : Card)(implicit g : Game) = $(Sub("Install 1 card from Archives", ChooseCardEffect(Corp, g.discard(Corp).%(corpInstallable), InstallChooseServerPurpose(false), "Crick: choose a card in Archives to install", false)))
    }

    object CurtainWall extends RI("04078") {
        override def strength(self : Card)(implicit g : Game) = iceHere(self).lastOption.has(self).?(4).|(0)
        override def subroutines(self : Card)(implicit g : Game) = $(etrSub, etrSub, etrSub)
    }

    // Data Hound / Waiver: the Runner's trace boost is measured as the credits spent (RememberCreditsEffect before the trace)
    object DataHound extends RI("02096") {
        override def subroutines(self : Card)(implicit g : Game) = $(Sub("Trace[2]: look at the top X cards of the stack (X = trace strength - link), trash 1 of them, the Runner rearranges the rest", CopyAbilityEffect($(RememberCreditsEffect(self, Runner), TraceEffect(2, $(RotSubAction(self, 1)), $)))))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case RotSubAction(self, 1) =>
                val boost = (memoInt(self) - g.credits(Runner)).atLeast(0)
                val x = (g.traceStrength - g.link - boost).atLeast(0)
                val l = g.deck(Runner).take(x)
                if (l.num > 1)
                    g.push(LookAtEffect(Corp, l, "Data Hound: the top " + l.num + " cards of the stack"), ChooseCardEffect(Corp, l, TrashPurpose(Corp), "Data Hound: choose a card to trash", false), ArrangeTopEffect(Runner, Runner, l.num - 1))
                else if (l.num == 1)
                    g.push(LookAtEffect(Corp, l, "Data Hound: the top card of the stack"), TrashEffect(l.head, Corp, false))
                g.push(clearMemo(self))
                NextAction
        }
    }

    object DataLoop extends RI("12095") {
        override def onEncounter(self : Card)(implicit g : Game) = {
            val n = 2.atMost(g.hand(Runner).num)
            (n > 0).$(ChooseCardsEffect(Runner, g.hand(Runner), n, n, TopOfDeckPurpose(Runner), "Data Loop: choose " + n + " card" + (n != 1).??("s") + " in the grip to add to the top of the stack"))
        }
        override def subroutines(self : Card)(implicit g : Game) = $(etrIfTagged, etrSub)
    }

    object DataMine extends RI("01076") {
        override def subroutines(self : Card)(implicit g : Game) = $(Sub("Do 1 net damage. Trash this ice", CopyAbilityEffect($(net(1), TrashEffect(self, Corp, false)))))
    }

    object DataRaven extends RI("25112") {
        override def onEncounter(self : Card)(implicit g : Game) = $(choose("choose one", $(Alt("Take 1 tag", $(tag(1))), Alt("End the run", $(EndRunEffect)))))
        override def abilities(self : Card)(implicit g : Game) = $(powerAbility(self, "give the Runner 1 tag", $(tag(1))))
        override def subroutines(self : Card)(implicit g : Game) = $(trace(3, "Place 1 power counter on this ice", $(AddCounterEffect(self, "power", 1))))
    }

    object DataWard extends RI("11075") {
        override def onEncounter(self : Card)(implicit g : Game) = $(choose("choose one", $(Alt("Pay 3 credits", $(LoseCreditsEffect(Runner, 3)), g.credits(Runner) >= 3), Alt("Take 1 tag", $(tag(1))))))
        override def subroutines(self : Card)(implicit g : Game) = 4.times(etrIfTagged)
    }

    object Datapike extends RI("03027") {
        override def subroutines(self : Card)(implicit g : Game) = $(Sub("The Runner must pay 2[c]. If they cannot, end the run", RotSubAction(self, 1)), etrSub)
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case RotSubAction(self, 1) =>
                if (g.credits(Runner) >= 2) g.push(lose(Runner, 2)) else g.push(EndRunEffect)
                NextAction
        }
    }

    object DNATracker extends RI("11053") {
        def s = Sub("Do 1 net damage. The Runner loses 2[c]", CopyAbilityEffect($(net(1), lose(Runner, 2))))
        override def subroutines(self : Card)(implicit g : Game) = $(s, s, s)
    }

    object Draco extends RI("02020") {
        override def onRez(self : Card)(implicit g : Game) = $(ChooseNumberEffect(Corp, self, 0, g.credits(Corp), "draco", "Draco: how many credits to pay for power counters?"))
        override def strength(self : Card)(implicit g : Game) = g.counter(self, "power")
        override def subroutines(self : Card)(implicit g : Game) = $(trace(2, "Give the Runner 1 tag and end the run", $(tag(1), EndRunEffect)))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case NumberPickedAction(self, n, "draco") =>
                if (n > 0)
                    g.push(LoseCreditsEffect(Corp, n), AddCounterEffect(self, "power", n))
                NextAction
        }
    }

    object Eli2 extends RI("13034") {
        override def subroutines(self : Card)(implicit g : Game) = $(Sub("You may draw 1 card", OptionalEffect(Corp, "Eli 2.0: draw 1 card?", $(draw(Corp, 1)), $)), etrSub, etrSub)
        override def runnerAbilities(self : Card)(implicit g : Game) = bioroid(self, 2)
    }

    object EndlessEULA extends RI("21074") {
        override def subroutines(self : Card)(implicit g : Game) = 6.times(etrUnlessPays(1))
        override def runnerAbilities(self : Card)(implicit g : Game) = {
            val n = g.unbrokenSubs(self).num
            $(Ability("pay 1[c] per unbroken subroutine (" + n + ") to resolve none of them", c(n), $(BreakAllSubsEffect(self)), ok = encounteringThis(self) && n > 0, reason = "not encountered"))
        }
    }

    object Enforcer extends RI("08089") {
        override def canRez(self : Card)(implicit g : Game) = g.scored(Corp).exists(_.data.isAgenda)
        override def onRez(self : Card)(implicit g : Game) = $(ChooseForfeitEffect)
        override def subroutines(self : Card)(implicit g : Game) = $(
            trashProgramSub,
            coreSub(1),
            Sub("Trash 1 installed console", ChooseCardEffect(Corp, g.runnerInstalled.%(c => g.has(c, "Console")), TrashPurpose(Corp), "Enforcer 1.0: choose a console to trash", false)),
            Sub("Trash all virtual resources", TrashCardsEffect(g.runnerInstalled.%(c => c.data.isResource && g.has(c, "Virtual")), Corp)))
        override def runnerAbilities(self : Card)(implicit g : Game) = bioroid(self, 1)
    }

    object Envelope extends RI("21095") {
        override def subroutines(self : Card)(implicit g : Game) = $(netSub(1), etrSub)
    }

    object ErrandBoy extends RI("06102") {
        def s(implicit g : Game) = Sub("Gain 1[c] or draw 1 card", ChooseOneEffect(Corp, "Errand Boy: choose one", $(Alt("Gain 1 credit", $(gain(Corp, 1))), Alt("Draw 1 card", $(draw(Corp, 1)), g.deck(Corp).any))))
        override def subroutines(self : Card)(implicit g : Game) = $(s, s, s)
    }

    object Excalibur extends RI("29017") {
        override def subroutines(self : Card)(implicit g : Game) = $(Sub("The Runner cannot make another run this turn", CopyAbilityEffect($(MessageEffect(Corp, "uses Excalibur: the Runner cannot make another run this turn"), AddFlagEffect(g.turnFlag("no-run", None))))))
    }

    object ExecutiveFunctioning extends RI("13035") {
        override def subroutines(self : Card)(implicit g : Game) = $(trace(4, "Do 1 core damage", $(core(1))))
    }

    object Fairchild extends RI("11089") {
        override def subroutines(self : Card)(implicit g : Game) = $(
            etrUnlessPays(4), etrUnlessPays(4),
            Sub("End the run unless the Runner trashes 1 installed card", choose("choose one", $(Alt("Trash an installed card", $(RunnerTrashInstalledEffect("", Runner, "Fairchild: choose an installed card to trash")), g.runnerInstalled.any), Alt("End the run", $(EndRunEffect))))),
            Sub("End the run unless the Runner suffers 1 core damage", choose("choose one", $(Alt("Suffer 1 core damage", $(core(1))), Alt("End the run", $(EndRunEffect))))))
    }

    abstract class FairchildN(code : String, n : Int) extends RI(code) {
        def paySub(implicit g : Game) = Sub("The Runner pays " + n + "[c] or trashes 1 installed card", choose("choose one", $(
            Alt("Pay " + n + " credits", $(LoseCreditsEffect(Runner, n)), g.credits(Runner) >= n),
            Alt("Trash an installed card", $(RunnerTrashInstalledEffect("", Runner, name + ": choose an installed card to trash")), g.runnerInstalled.any))))
        override def runnerAbilities(self : Card)(implicit g : Game) = bioroid(self, n)
    }

    object Fairchild1 extends FairchildN("11010", 1) {
        override def subroutines(self : Card)(implicit g : Game) = $(paySub, paySub)
    }

    object Fairchild2 extends FairchildN("11031", 2) {
        override def subroutines(self : Card)(implicit g : Game) = $(paySub, paySub, coreSub(1))
    }

    object Fairchild3 extends FairchildN("11049", 3) {
        override def subroutines(self : Card)(implicit g : Game) = $(paySub, paySub, Sub("Do 1 core damage or end the run", ChooseOneEffect(Corp, "Fairchild 3.0: choose one", $(Alt("Do 1 core damage", $(core(1))), Alt("End the run", $(EndRunEffect))))))
    }

    object Fenris extends RI("04071") {
        override def onRez(self : Card)(implicit g : Game) = takeBadPub
        override def subroutines(self : Card)(implicit g : Game) = $(coreSub(1), etrSub)
    }

    object FireWall extends RI("07018") {
        override def advanceable(self : Card)(implicit g : Game) = true
        override def strength(self : Card)(implicit g : Game) = g.advancement(self)
        override def subroutines(self : Card)(implicit g : Game) = $(etrSub)
    }

    object Flare extends RI("25113") {
        override def subroutines(self : Card)(implicit g : Game) = $(trace(6, "Trash 1 piece of hardware, do 2 meat damage and end the run", $(RotSubAction(self, 1))))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case RotSubAction(self, 1) =>
                g.push(RunnerTrashInstalledEffect("Hardware", Corp, "Flare: choose a piece of hardware to trash"), meat(2), EndRunEffect)
                NextAction
        }
    }

    // Formicary: the game-level trigger (RotIceRules.rotIceTriggers) offers to rez it when the Runner approaches a server
    object Formicary extends RI("22054") {
        override def subroutines(self : Card)(implicit g : Game) = $(Sub("End the run unless the Runner suffers 2 net damage", choose("choose one", $(Alt("Suffer 2 net damage", $(net(2))), Alt("End the run", $(EndRunEffect))))))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case FormicaryAction(self, s) =>
                if (!g.isRezzed(self) && g.credits(Corp) >= g.rezCost(self) && g.run.exists(r => !r.ended && r.server == s)) {
                    g.rezCard(self, false)
                    g.push(MoveIceOutermostEffect(self, s), MoveIceToIndexEffect(self, 0), MoveRunnerEffect(1, false))
                }
                NextAction
        }
    }

    object FreeLunch extends RI("12035") {
        override def abilities(self : Card)(implicit g : Game) = $(powerAbility(self, "the Runner loses 1 credit", $(lose(Runner, 1))))
        override def subroutines(self : Card)(implicit g : Game) = $(powerCounterSub(self), powerCounterSub(self))
    }

    // grail ice: when encountered, reveal up to 2 grail ice from HQ; their subroutines are added for the encounter
    abstract class Grail(code : String) extends RI(code) {
        def grailSub(c : Card)(implicit g : Game) : Sub = c.code match {
            case "06011" => etrSub
            case "06051" => trashProgramSub
            case "06091" => netSub(2)
            case "29017" => Sub("The Runner cannot make another run this turn", AddFlagEffect(g.turnFlag("no-run", None)))
            case _ => etrSub
        }
        def ownSub(self : Card)(implicit g : Game) : Sub
        override def onEncounter(self : Card)(implicit g : Game) = {
            val l = g.hand(Corp).%(c => g.has(c, "Grail"))
            l.any.$(ChooseCardsEffect(Corp, l, 0, 2, RotPurpose(self, 1), name + ": reveal up to 2 grail ice from HQ to add their subroutines"))
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            if (cards.any) {
                g.push(RevealCardsEffect(Corp, cards))
                g.state(p.card).memo = cards./(_.code).join(",")
            }
            NextAction
        }
        override def subroutines(self : Card)(implicit g : Game) =
            g.state(self).memo.split(',').toList.%(_ != "")./(code => grailSub(Card(code, 0))) :+ ownSub(self)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case EncounterEnds(c) if c == self && g.state(self).memo != "" => $(clearMemo(self))
            case RunEnds(_, _, _) if g.state(self).memo != "" => $(clearMemo(self))
            case _ => $
        }
    }

    object Galahad extends Grail("06011") {
        def ownSub(self : Card)(implicit g : Game) = etrSub
    }

    object Gatekeeper extends RI("22029") {
        override def onRez(self : Card)(implicit g : Game) = $(SetMemoEffect(self, "rez:" + g.halfTurn))
        override def strength(self : Card)(implicit g : Game) = memoIs(self, "rez:" + g.halfTurn).?(6).|(0)
        override def subroutines(self : Card)(implicit g : Game) = $(Sub("Draw up to 3 cards, then reveal and shuffle up to 3 agendas from HQ and/or Archives into R&D", RotSubAction(self, 1)), etrSub)
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case RotSubAction(self, 1) =>
                g.push(ChooseOneEffect(Corp, "Gatekeeper: draw cards?", 0.to(3.atMost(g.deck(Corp).num)).$./(n => Alt("Draw " + n + " card" + (n != 1).??("s"), (n > 0).$(draw(Corp, n))))), RotSubAction(self, 2))
                NextAction
            case RotSubAction(self, 2) =>
                val l = (g.hand(Corp) ++ g.discard(Corp)).%(_.data.isAgenda)
                if (l.any)
                    g.push(ChooseCardsEffect(Corp, l, 0, 3, RotPurpose(self, 1), "Gatekeeper: choose up to 3 agendas to reveal and shuffle into R&D"))
                NextAction
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            if (cards.any)
                g.push(RevealCardsEffect(Corp, cards), CardsChosenAction(Corp, cards, ShuffleIntoDeckPurpose(Corp)))
            NextAction
        }
    }

    // constellation ice: trace 2, the ability resolves a second time if the trace strength was 5 or more
    abstract class Constellation(code : String) extends RI(code) {
        def label : String
        def effect(self : Card)(implicit g : Game) : ForcedAction
        override def subroutines(self : Card)(implicit g : Game) = $(Sub("Trace[2]: " + label + " (again if the trace strength is 5 or more)", TraceEffect(2, $(effect(self), TraceKickerEffect(5, $(effect(self)))), $)))
    }

    object Gemini extends Constellation("06084") {
        val label = "Do 1 net damage"
        def effect(self : Card)(implicit g : Game) = net(1)
    }

    object Grim extends RI("04020") {
        override def onRez(self : Card)(implicit g : Game) = takeBadPub
        override def subroutines(self : Card)(implicit g : Game) = $(trashProgramSub)
    }

    object Guard extends RI("05024") {
        override def subroutines(self : Card)(implicit g : Game) = $(etrSub)
    }

    object Gutenberg extends RI("08037") {
        override def strength(self : Card)(implicit g : Game) = protecting(self, RnD).?(3).|(0)
        override def subroutines(self : Card)(implicit g : Game) = $(trace(7, "Give the Runner 1 tag", $(tag(1))))
    }

    object GyriLabyrinth extends RI("04110") {
        override def subroutines(self : Card)(implicit g : Game) = $(Sub("The Runner's maximum hand size is reduced by 2 until the start of your next turn", CopyAbilityEffect($(MessageEffect(Corp, "uses Gyri Labyrinth: the Runner's maximum hand size is reduced by 2"), AddFlagEffect(Flag("gyri", |(self), None, "halfturn", g.halfTurn, $))))))
        override def handSize(self : Card, f : Faction)(implicit g : Game) = (f.isRunner && g.isRezzed(self)).?(-2 * g.flagsOf("gyri").%(_.card.has(self)).num).|(0)
    }

    object HadriansWall extends RI("25132") {
        override def advanceable(self : Card)(implicit g : Game) = true
        override def strength(self : Card)(implicit g : Game) = g.advancement(self)
        override def subroutines(self : Card)(implicit g : Game) = $(etrSub, etrSub)
    }

    object Hailstorm extends RI("13049") {
        override def subroutines(self : Card)(implicit g : Game) = $(Sub("Remove 1 card in the heap from the game", ChooseCardEffect(Corp, g.discard(Runner).sortBy(_.title), RemoveFromGamePurpose, "Hailstorm: choose a card in the heap", false)), etrSub)
    }

    object Harvester extends RI("10032") {
        def s(self : Card) = Sub("The Runner draws 3 cards and discards down to their maximum hand size", CopyAbilityEffect($(draw(Runner, 3), RotSubAction(self, 1))))
        override def subroutines(self : Card)(implicit g : Game) = $(s(self), s(self))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case RotSubAction(self, 1) =>
                val n = g.hand(Runner).num - g.handSize(Runner).atLeast(0)
                if (n > 0)
                    g.push(ChooseCardsEffect(Runner, g.hand(Runner), n, n, DiscardPurpose(Runner), "Harvester: choose " + n + " card" + (n != 1).??("s") + " to discard"))
                NextAction
        }
    }

    object Heimdall1 extends RI("25074") {
        override def subroutines(self : Card)(implicit g : Game) = $(coreSub(1), etrSub, etrSub)
        override def runnerAbilities(self : Card)(implicit g : Game) = bioroid(self, 1)
    }

    object Heimdall2 extends RI("03015") {
        override def subroutines(self : Card)(implicit g : Game) = $(coreSub(1), Sub("Do 1 core damage and end the run", CopyAbilityEffect($(core(1), EndRunEffect))), etrSub)
        override def runnerAbilities(self : Card)(implicit g : Game) = bioroid(self, 2)
    }

    object Herald extends RI("11115") {
        override def onAccess(self : Card)(implicit g : Game) = (!g.isInstalled(self)).$(PhantomEncounterEffect(self))
        override def subroutines(self : Card)(implicit g : Game) = $(gainSub(2), Sub("Pay up to 2[c] to place that many advancement counters on a card that can be advanced", ChooseNumberEffect(Corp, self, 0, 2.atMost(g.credits(Corp)), "herald", "Herald: how many credits to pay for advancement counters?")))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case NumberPickedAction(self, n, "herald") =>
                if (n > 0 && g.advanceableCards.any)
                    g.push(LoseCreditsEffect(Corp, n), ChooseCardEffect(Corp, g.advanceableCards, AdvancePurpose(n), "Herald: choose a card to place " + n + " advancement counter" + (n != 1).??("s") + " on", false))
                NextAction
        }
    }

    object HimitsuBako extends RI("25093") {
        override def abilities(self : Card)(implicit g : Game) = $(Ability("add this ice to HQ", c(1), $(ReturnToHandEffect(self)), ok = g.isRezzed(self), reason = "not rezzed"))
        override def subroutines(self : Card)(implicit g : Game) = $(etrSub)
    }

    object Hive extends RI("20087") {
        override def subroutines(self : Card)(implicit g : Game) = (5 - g.agendaPoints(Corp)).atLeast(0).times(etrSub)
    }

    object Holmegaard extends RI("13036") {
        override def subroutines(self : Card)(implicit g : Game) = $(
            trace(4, "The Runner cannot access any cards this run", $(MessageEffect(Corp, "uses Holmegaard: the Runner cannot access cards this run"), ReplaceBreachEffect)),
            Sub("Trash 1 installed icebreaker", ChooseCardEffect(Corp, g.runnerInstalled.%(_.data.isIcebreaker), TrashPurpose(Corp), "Holmegaard: choose an icebreaker to trash", false)))
    }

    object Hourglass extends RI("02071") {
        override def subroutines(self : Card)(implicit g : Game) = $(loseClickSub, loseClickSub, loseClickSub)
    }

    object Howler extends RI("03016") {
        override def subroutines(self : Card)(implicit g : Game) = $(Sub("Install and rez a bioroid ice from HQ or Archives directly inside this ice, ignoring all costs; derez it and trash this ice when the run ends",
            ChooseCardEffect(Corp, (g.hand(Corp) ++ g.discard(Corp)).%(c => c.data.isIce && g.has(c, "Bioroid")), RotPurpose(self, 1), "Howler: choose a bioroid ice", true)))
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach { c =>
                g.serverOf(p.card).foreach { s =>
                    val idx = g.ice(s).indexOf(p.card).atLeast(0)
                    g.push(CorpInstallEffect(c, |(s), true), MoveIceToIndexEffect(c, idx), RezEffect(c, true), AtRunEndEffect($(DerezEffect(c), TrashEffect(p.card, Corp, false))))
                }
            }
            NextAction
        }
    }

    object Hudson extends RI("20067") {
        def s(self : Card)(implicit g : Game) = Sub("The Runner cannot access more than 1 card during this run", CopyAbilityEffect($(MessageEffect(Corp, "uses Hudson 1.0: the Runner cannot access more than 1 card this run"), SetMemoEffect(self, "run:" + g.runKeyString))))
        override def subroutines(self : Card)(implicit g : Game) = $(s(self), s(self))
        override def runnerAbilities(self : Card)(implicit g : Game) = bioroid(self, 1)
        override def accessFilter(self : Card, cards : $[Card])(implicit g : Game) = (g.isRezzed(self) && memoIs(self, "run:" + g.runKeyString) && g.run.exists(_.accessCount >= 1)).?($[Card]()).|(cards)
    }

    object Hunter extends RI("25144") {
        override def subroutines(self : Card)(implicit g : Game) = $(trace(3, "Give the Runner 1 tag", $(tag(1))))
    }

    object Hydra extends RI("22046") {
        def s(self : Card, k : Int, label : String) = Sub(label + " if the Runner is tagged; otherwise give the Runner 1 tag", RotSubAction(self, k))
        override def subroutines(self : Card)(implicit g : Game) = $(s(self, 1, "Do 3 net damage"), s(self, 2, "Gain 5[c]"), s(self, 3, "End the run"))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case RotSubAction(self, k) =>
                if (!g.tagged) g.push(tag(1))
                else k match {
                    case 1 => g.push(net(3))
                    case 2 => g.push(gain(Corp, 5))
                    case _ => g.push(EndRunEffect)
                }
                NextAction
        }
    }

    object Ichi1 extends RI("25075") {
        override def subroutines(self : Card)(implicit g : Game) = $(trashProgramSub, trashProgramSub, trace(1, "Give the Runner 1 tag and do 1 core damage", $(core(1), tag(1))))
        override def runnerAbilities(self : Card)(implicit g : Game) = bioroid(self, 1)
    }

    object Ichi2 extends RI("03017") {
        override def subroutines(self : Card)(implicit g : Game) = $(trashProgramSub, trashProgramSub, trace(3, "Give the Runner 1 tag and do 1 core damage", $(core(1), tag(1))))
        override def runnerAbilities(self : Card)(implicit g : Game) = bioroid(self, 2)
    }

    // Inazuma: run flags consumed when the next ice is encountered
    object Inazuma extends RI("05016") {
        override def subroutines(self : Card)(implicit g : Game) = $(
            Sub("The Runner cannot break subroutines on the next piece of ice they encounter this run", CopyAbilityEffect($(MessageEffect(Corp, "uses Inazuma: no breaking on the next ice"), AddFlagEffect(g.runFlag("inazuma-break", None))))),
            Sub("The Runner cannot jack out until after the next piece of ice they encounter this run", CopyAbilityEffect($(RunFlagEffect("cannot-jack-out", true), AddFlagEffect(g.runFlag("inazuma-jack", None))))))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case EncounterIce(c) if c != self && (g.hasFlag("inazuma-break") || g.hasFlag("inazuma-jack")) =>
                g.hasFlag("inazuma-break").$(RemoveFlagEffect("inazuma-break", None), AddFlagEffect(g.encounterFlag("cannot-break", c)), MessageEffect(Corp, "uses Inazuma: the Runner cannot break subroutines on " + g.asciiTitle(c))) ++
                g.hasFlag("inazuma-jack").$(RemoveFlagEffect("inazuma-jack", None), RunFlagEffect("cannot-jack-out", false))
            case _ => $
        }
    }

    object InformationOverload extends RI("06027") {
        override def onEncounter(self : Card)(implicit g : Game) = $(TraceEffect(1, $(tag(1)), $))
        override def subroutines(self : Card)(implicit g : Game) = g.tags.times(runnerTrashInstalledSub)
    }

    // Interrupt 0: while the run lasts, a break with an icebreaker needs 1[c] more than its cheapest break ability, charged right after the break
    object Interrupt0 extends RI("10016") {
        def s(self : Card)(implicit g : Game) = Sub("The Runner must pay 1[c] more to use icebreakers to break subroutines this run", CopyAbilityEffect($(MessageEffect(Corp, "uses Interrupt 0: icebreakers cost 1 credit more to break subroutines this run"), SetMemoEffect(self, "run:" + g.runKeyString))))
        override def subroutines(self : Card)(implicit g : Game) = $(s(self), s(self))
        def on(self : Card)(implicit g : Game) = memoIs(self, "run:" + g.runKeyString) && g.run.exists(!_.ended)
        override def breakAllowed(self : Card, breaker : Card, ice : Card)(implicit g : Game) =
            !on(self) || !g.isIcebreaker(breaker) || g.credits(Runner) >= 1 + g.impl(breaker).breaks(breaker)./(_.costs./{ case CreditCost(n) => n ; case _ => 0 }.sum).minOption.|(0)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SubsBroken(_, _, b) if on(self) && g.isIcebreaker(b) => $(MessageEffect(Corp, "uses Interrupt 0: the Runner pays 1 credit more for the break"), lose(Runner, 1))
            case RunEnds(_, _, _) if g.state(self).memo != "" => $(clearMemo(self))
            case _ => $
        }
    }

    object IPBlock extends RI("11094") {
        override def onEncounter(self : Card)(implicit g : Game) = g.activeCards(Runner).exists(c => g.has(c, "AI")).$(MessageEffect(Corp, "uses IP Block (an AI is installed)"), tag(1))
        override def subroutines(self : Card)(implicit g : Game) = $(trace(3, "Give the Runner 1 tag", $(tag(1))), etrIfTagged)
    }

    object IQ extends RI("06041") {
        override def rezCostMod(self : Card, card : Card)(implicit g : Game) = (card == self).?(g.hand(Corp).num).|(0)
        override def strength(self : Card)(implicit g : Game) = g.hand(Corp).num
        override def subroutines(self : Card)(implicit g : Game) = $(etrSub)
    }

    object Ireress extends RI("04057") {
        override def subroutines(self : Card)(implicit g : Game) = g.badPublicity.times(loseSub(1))
    }

    object ItsATrap extends RI("08090") {
        override def subroutines(self : Card)(implicit g : Game) = $(Sub("The Runner trashes 1 installed card. Trash this ice", CopyAbilityEffect($(RunnerTrashInstalledEffect("", Runner, "It's a Trap!: choose an installed card to trash"), TrashEffect(self, Corp, false)))))
    }

    object Janus extends RI("02012") {
        override def subroutines(self : Card)(implicit g : Game) = 4.times(coreSub(1))
        override def runnerAbilities(self : Card)(implicit g : Game) = bioroid(self, 1)
    }

    object Jua extends RI("21034") {
        override def onEncounter(self : Card)(implicit g : Game) = $(MessageEffect(Corp, "uses Jua: the Runner cannot install cards this turn (not enforced)"))
        override def subroutines(self : Card)(implicit g : Game) = $(Sub("Choose 2 installed Runner cards; the Runner adds 1 of them to the top of the stack", RotSubAction(self, 1)))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case RotSubAction(self, 1) =>
                if (g.runnerInstalled.num >= 2)
                    g.push(ChooseCardsEffect(Corp, g.runnerInstalled, 2, 2, RotPurpose(self, 1), "Jua: choose 2 installed Runner cards"))
                NextAction
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            if (cards.num == 2)
                g.push(ChooseCardEffect(Runner, cards, TopOfDeckPurpose(Runner), "Jua: choose a card to add to the top of the stack", false))
            NextAction
        }
    }

    object Kakugo extends RI("12013") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case PassIce(c) if c == self => $(MessageEffect(Corp, "uses Kakugo"), net(1))
            case _ => $
        }
        override def subroutines(self : Card)(implicit g : Game) = $(etrSub)
    }

    object Kamali extends RI("21092") {
        def s(kind : String, text : String)(implicit g : Game) = Sub("Do 1 core damage unless the Runner trashes 1 installed " + text, choose("choose one", $(
            Alt("Trash an installed " + text, $(RunnerTrashInstalledEffect(kind, Runner, "Kamali 1.0: choose a " + text + " to trash")), g.runnerInstalledOf(kind).any),
            Alt("Suffer 1 core damage", $(core(1))))))
        override def subroutines(self : Card)(implicit g : Game) = $(s("Resource", "resource"), s("Hardware", "piece of hardware"), s("Program", "program"))
        override def runnerAbilities(self : Card)(implicit g : Game) = bioroid(self, 1)
    }

    object Kitsune extends RI("06043") {
        override def subroutines(self : Card)(implicit g : Game) = $(Sub("You may force the Runner to access 1 card in HQ. Trash this ice", RotSubAction(self, 1)))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case RotSubAction(self, 1) =>
                if (g.hand(Corp).any)
                    g.push(OptionalEffect(Corp, "Kitsune: force the Runner to access a card in HQ?", $(ChooseCardEffect(Corp, g.hand(Corp), RotPurpose(self, 1), "Kitsune: choose a card in HQ", false)), $))
                NextAction
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(c => g.push(AccessCardsEffect(HQ, $(c), false), TrashEffect(p.card, Corp, false)))
            NextAction
        }
    }

    object Komainu extends RI("05017") {
        override def onEncounter(self : Card)(implicit g : Game) = $(SetMemoEffect(self, g.hand(Runner).num.toString))
        override def subroutines(self : Card)(implicit g : Game) = memoInt(self).times(netSub(1))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case RunEnds(_, _, _) if g.state(self).memo != "" => $(clearMemo(self))
            case _ => $
        }
    }

    object LabDog extends RI("08052") {
        override def subroutines(self : Card)(implicit g : Game) = $(Sub("The Runner trashes 1 installed piece of hardware. Trash this ice", RotSubAction(self, 1)))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case RotSubAction(self, 1) =>
                if (g.runnerInstalledOf("Hardware").any)
                    g.push(RunnerTrashInstalledEffect("Hardware", Runner, "Lab Dog: choose a piece of hardware to trash"), TrashEffect(self, Corp, false))
                NextAction
        }
    }

    object Lancelot extends Grail("06051") {
        def ownSub(self : Card)(implicit g : Game) = trashProgramSub
    }

    object LittleEngine extends RI("08057") {
        override def subroutines(self : Card)(implicit g : Game) = $(etrSub, etrSub, Sub("The Runner gains 5[c]", gain(Runner, 5)))
    }

    object Lockdown extends RI("08056") {
        override def subroutines(self : Card)(implicit g : Game) = $(Sub("The Runner cannot draw cards for the remainder of this turn", CopyAbilityEffect($(MessageEffect(Corp, "uses Lockdown: the Runner cannot draw cards this turn"), AddFlagEffect(g.turnFlag("no-draw", None))))))
    }

    // Loki: gains the subtypes and subroutines of a chosen rezzed ice for the remainder of the run
    object Loki extends RI("12069") {
        override def onEncounter(self : Card)(implicit g : Game) = {
            val l = g.rezzedIce.but(self)
            l.any.$(ChooseCardEffect(Corp, l, RotPurpose(self, 1), "Loki: choose another rezzed piece of ice to copy", false))
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(c => g.push(SetTargetEffect(p.card, |(c)), ExtraSubtypesEffect(p.card, g.subtypes(c), true)))
            NextAction
        }
        override def subroutines(self : Card)(implicit g : Game) =
            g.state(self).target.%(g.isRezzed).$./~(t => g.impl(t).subroutines(t)) :+ Sub("End the run unless the Runner shuffles the grip into the stack", RotSubAction(self, 1))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case RunEnds(_, _, _) if g.state(self).target.any || g.state(self).extraSubtypes.any => $(SetTargetEffect(self, None), ExtraSubtypesEffect(self, g.state(self).extraSubtypes, false))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case RotSubAction(self, 1) =>
                g.push(choose("choose one", $(
                    Alt("Shuffle the grip into the stack", $(RotSubAction(self, 2)), g.hand(Runner).any || g.deck(Runner).num >= 2),
                    Alt("End the run", $(EndRunEffect)))))
                NextAction
            case RotSubAction(self, 2) =>
                g.hand(Runner).foreach(c => g.moveTo(c, DeckZone(Runner)))
                g.log(Runner, "shuffles the grip into the stack")
                g.push(ShuffleDeckEffect(Runner))
                NextAction
        }
    }

    object Lycan extends Morph("06089", "Sentry", "Code Gate") {
        override def subroutines(self : Card)(implicit g : Game) = $(trashProgramSub)
    }

    val all : $[CardImpl] = $(
        Aiki, Aimor, Anansi, Archangel, Architect, Ashigaru, Assassin, AsteroidBelt, Authenticator,
        Bailiff, Bandwidth, Bastion, Battlement, Blockchain, Bloodletter, Bloom, BorderControl, Brainstorm, Builder, Bulwark, BurkeBugs,
        Caduceus, CellPortal, Changeling, Checkpoint, Chetana, Chimera, Chiyashi, Chrysalis, Chum, ClairvoyantMonitor, Cobra, Conundrum, CortexLock, Crick, CurtainWall,
        DataHound, DataLoop, DataMine, DataRaven, DataWard, Datapike, DNATracker, Draco,
        Eli2, EndlessEULA, Enforcer, Envelope, ErrandBoy, Excalibur, ExecutiveFunctioning,
        Fairchild, Fairchild1, Fairchild2, Fairchild3, Fenris, FireWall, Flare, Formicary, FreeLunch,
        Galahad, Gatekeeper, Gemini, Grim, Guard, Gutenberg, GyriLabyrinth,
        HadriansWall, Hailstorm, Harvester, Heimdall1, Heimdall2, Herald, HimitsuBako, Hive, Holmegaard, Hourglass, Howler, Hudson, Hunter, Hydra,
        Ichi1, Ichi2, Inazuma, InformationOverload, Interrupt0, IPBlock, IQ, Ireress, ItsATrap,
        Janus, Jua, Kakugo, Kamali, Kitsune, Komainu,
        LabDog, Lancelot, LittleEngine, Lockdown, Loki, Lycan,
    )
}
