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
// Rotated hardware (Kitara cycle .. System Core 2019 / Salvaged Memories), ported from jinteki.net hardware.clj
// ---------------------------------------------------------------------------------------------------------------------

case class RHRamujanAction(card : Card, kind : String, n : Int, x : Int) extends CardAction
case class RHSifrAction(card : Card, ice : Card) extends CardAction
case class RHSeverniusAction(card : Card, server : Server) extends CardAction
case class RHSeverniusPurpose(card : Card, server : Server) extends CardPurpose
case class RHRubiconPurpose(card : Card) extends CardPurpose
case class RHRespirocytesAction(card : Card) extends CardAction
case class RHDoppelgangerAction(card : Card) extends CardAction
case class RHRabbitHoleAction(card : Card) extends CardAction
case class RHFridayPurpose(card : Card) extends CardPurpose
case class RHGebrselassiePurpose(card : Card) extends CardPurpose
case class RHParagonAction(card : Card) extends CardAction


object RotHardwareCards2 {
    object Ramujan extends RH("10002") {
        def x(self : Card)(implicit g : Game) = 1 + g.installedCards(Runner).%(c => c.code == self.code && c != self).num
        override def prevent(self : Card, kind : String, n : Int)(implicit g : Game) : $[Ability] = {
            val k = x(self).atMost(n)
            (kind == "net" || kind == "core").$(Ability("prevent " + k + " damage, trashing the top " + k + " cards of the stack", $(TrashSelfCost), $(TrashTopEffect(Runner, k, true), PreventWindowAction(kind, n - k))))
        }
    }

    object Maya extends RH("10007") {
        override def memory(self : Card)(implicit g : Game) = 2
        override def afterAccess(self : Card, card : Card)(implicit g : Game) =
            (g.zone(card) == DeckZone(Corp) && firstTime(self, "maya")).$(OptionalEffect(Runner, "Maya: add the accessed card to the bottom of R&D and take 1 tag?", $(MarkUsedEffect(self, "maya"), says(self, "add the card to the bottom of R&D"), RHMayaAction(self, card), tag(1)), $))
        override def performRH(a : CardAction)(implicit g : Game) = a match {
            case RHMayaAction(self, c) =>
                if (g.zone(c) == DeckZone(Corp)) {
                    g.moveTo(c, DeckZone(Corp))
                    g.log(Runner, "adds the accessed card to the bottom of R&D")
                }
                NextAction
        }
    }

    object EMPDevice extends RH("10020") {
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("the Corp cannot rez more than 1 piece of ice for the remainder of the run", $(TrashSelfCost), $(AddFlagEffect(Flag("rh-emp", None, None, "run", 0, $)), MessageEffect(Runner, "prevents the Corp from rezzing more than 1 piece of ice this run")), ok = g.run.exists(!_.ended) && !g.hasFlag("rh-emp"), reason = "only during a run"))
    }

    object NetChip extends RH("10024") {
        def copies(implicit g : Game) = g.installedCards(Runner).%(c => c.code == "10024" && g.isActive(c)).num
        override def canHost(self : Card, card : Card)(implicit g : Game) = card.data.isProgram && card.data.memory.|(0) <= copies && hostedPrograms(self).none
        override def hostsWithoutMU(self : Card)(implicit g : Game) = true
        override def memory(self : Card)(implicit g : Game) = hostedMU(self)
    }

    object Reflection extends RH("10041") {
        override def memory(self : Card)(implicit g : Game) = 1
        override def link(self : Card)(implicit g : Game) = 1
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case RunEnds(_, _, _) if g.run.exists(_.jackedOut) && g.hand(Corp).any => $(says(self, "make the Corp reveal a random card from HQ"), RandomPickEffect(Corp, g.hand(Corp), 1, RevealPurpose(self)))
            case _ => $
        }
    }

    object SpyCamera extends RH("10042") {
        override def abilities(self : Card)(implicit g : Game) = {
            val x = g.installedCards(Runner).%(c => c.code == self.code && g.isActive(c)).num
            $(
                Ability("look at the top " + x + " cards of the stack and arrange them", click(), $(ArrangeTopEffect(Runner, Runner, x)), click = true, ok = g.deck(Runner).any, reason = "the stack is empty"),
                Ability("look at the top card of R&D", $(TrashSelfCost), $(LookAtEffect(Runner, g.deck(Corp).take(1), "Spy Camera: the top card of R&D")), ok = g.deck(Corp).any, reason = "R&D is empty"))
        }
    }

    object SportsHopper extends RH("10064") {
        override def link(self : Card)(implicit g : Game) = 1
        override def abilities(self : Card)(implicit g : Game) = $(Ability("draw 3 cards", $(TrashSelfCost), $(draw(Runner, 3))))
    }

    object GPINetTap extends RH("11003") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case ApproachIce(c) if !g.isRezzed(c) => $(OptionalEffect(Runner, "GPI Net Tap: expose the approached ice?", $(RHExposeEffect(c), RHGpiAction(self)), $))
            case _ => $
        }
        override def performRH(a : CardAction)(implicit g : Game) = a match {
            case RHGpiAction(self) =>
                if (g.isInstalled(self) && g.run.exists(r => !r.ended && !r.cannotJackOut))
                    g.push(OptionalEffect(Runner, "GPI Net Tap: trash it to jack out?", $(TrashEffect(self, Runner, false), JackOutEffect), $))
                NextAction
        }
    }

    object Mirror extends RH("11005") {
        override def memory(self : Card)(implicit g : Game) = 2
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(RnD) =>
                val l = g.rhRunnerCards.%{ case (c, r) => r.recurring(c) > g.counter(c, "credit") }.map(_._1)
                l.any.$(ChooseCardEffect(Runner, l, RHAddCounterPurpose(self, "credit", 1), "Mirror: replace 1 spent recurring credit?", true))
            case _ => $
        }
    }

    object Obelus extends RH("11041") {
        override def memory(self : Card)(implicit g : Game) = 1
        override def handSize(self : Card, f : Faction)(implicit g : Game) = f.isRunner.??(g.tags)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case RunEnds(s, true, _) if (s == HQ || s == RnD) && firstTime(self, "obelus") =>
                val n = g.run./(_.accessCount).|(0)
                $(MarkUsedEffect(self, "obelus")) ++ (n > 0).$(says(self, "draw " + n + " cards")) ++ (n > 0).$(draw(Runner, n))
            case _ => $
        }
    }

    object TheGauntlet extends RH("11063") {
        override def memory(self : Card)(implicit g : Game) = 2
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SubsBroken(ice, _, _) if g.serverOf(ice).has(HQ) && g.unbrokenSubs(ice).none && !g.hasFlag("gauntlet-broken", ice) =>
                $(AddFlagEffect(Flag("gauntlet-broken", |(ice), None, "run", 0, $)))
            case BreachServer(HQ) if g.run.exists(r => !r.accessOnly && r.server == HQ) && g.flagsOf("gauntlet-broken").any =>
                $(MessageEffect(Runner, "accesses " + g.flagsOf("gauntlet-broken").num + " additional cards (The Gauntlet)"), AccessBonusEffect(HQ, g.flagsOf("gauntlet-broken").num))
            case _ => $
        }
    }

    object TopHat extends RH("11067") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(RnD) if g.deck(Corp).any =>
                $(OptionalEffect(Runner, "Top Hat: instead of breaching R&D, access 1 of the top 5 cards?", $(ReplaceBreachEffect, ChooseNumberEffect(Runner, self, 1, g.deck(Corp).num.atMost(5), "tophat", "Top Hat: choose a position in R&D (1 is the top card)")), $))
            case _ => $
        }
        override def performRH(a : CardAction)(implicit g : Game) = a match {
            case NumberPickedAction(self, n, _) =>
                g.log(Runner, "accesses the card at position", n.hl, "of R&D")
                g.deck(Corp).lift(n - 1).foreach(c => g.push(AccessCardsEffect(RnD, $(c), false)))
                NextAction
        }
    }

    object Sifr extends RH("11101") {
        override def memory(self : Card)(implicit g : Game) = 2
        override def handSize(self : Card, f : Faction)(implicit g : Game) = f.isRunner.??(-g.counter(self, "sifr"))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) if g.counter(self, "sifr") > 0 => $(SetCounterEffect(self, "sifr", 0))
            case EncounterIce(ice) if firstTime(self, "sifr") && g.iceStrength(ice) > 0 =>
                $(OptionalEffect(Runner, "Sifr: get -1 maximum hand size until your next turn to lower the strength of this ice to 0?", $(MarkUsedEffect(self, "sifr"), says(self, "lower the strength of the ice to 0"), AddCounterEffect(self, "sifr", 1), RHSifrAction(self, ice)), $))
            case _ => $
        }
        override def performRH(a : CardAction)(implicit g : Game) = a match {
            case RHSifrAction(self, ice) =>
                val s = g.iceStrength(ice)
                if (s > 0)
                    g.push(PumpIceEffect(ice, -s, EncounterDuration))
                NextAction
        }
    }

    object ReconDrone extends RH("11103") {
        override def prevent(self : Card, kind : String, n : Int)(implicit g : Game) : $[Ability] =
            g.run.exists(r => !r.ended && r.phase == AccessPhase).$(Ability("prevent " + n + " damage from the accessed card", $(TrashSelfCost, CreditCost(n)), $(PreventWindowAction(kind, 0))))
    }

    object Maw extends RH("12002") {
        override def memory(self : Card)(implicit g : Game) = 2
        override def afterAccess(self : Card, card : Card)(implicit g : Game) = {
            val untouched = g.location.contains(card) && !g.zone(card).is[DiscardZone] && !g.zone(card).is[ScoredZone] && !g.zone(card).is[RemovedZone]
            (untouched && firstTime(self, "maw") && g.hand(Corp).any).$(MarkUsedEffect(self, "maw")) ++ (untouched && firstTime(self, "maw") && g.hand(Corp).any).$(says(self, "make the Corp trash a random card from HQ")) ++
            (untouched && firstTime(self, "maw") && g.hand(Corp).any).$(TrashRandomFromHandEffect(self, Corp, 1, false))
        }
    }

    object SeverniusStimImplant extends RH("12021") {
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("trash 2 or more cards from the grip and run HQ or R&D", click(), $(ChooseOneEffect(Runner, "Severnius Stim Implant: choose a server", $(Alt("HQ", $(RHSeverniusAction(self, HQ))), Alt("R&D", $(RHSeverniusAction(self, RnD)))))), click = true, ok = g.hand(Runner).num >= 2, reason = "needs 2 cards in the grip"))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case BreachServer(s) if thisCardRun(self) && g.counter(self, "stim") > 0 => $(says(self, "access " + g.counter(self, "stim") + " additional cards"), AccessBonusEffect(s, g.counter(self, "stim")))
            case RunEnds(_, _, _) if thisCardRun(self) => $(SetCounterEffect(self, "stim", 0))
            case _ => $
        }
        override def performRH(a : CardAction)(implicit g : Game) = a match {
            case RHSeverniusAction(self, s) =>
                g.push(ChooseCardsEffect(Runner, g.hand(Runner), 2, g.hand(Runner).num, RHSeverniusPurpose(self, s), "Severnius Stim Implant: choose at least 2 cards to trash"))
                NextAction
        }
        override def chosenRH(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case RHSeverniusPurpose(self, s) =>
                cards.foreach(c => g.moveTo(c, DiscardZone(Runner)))
                g.log(Runner, "trashes", cards./(g.cardElem).comma, "from the grip")
                g.push(SetCounterEffect(self, "stim", cards.num / 2), RunEffect(s, |(self)))
                NextAction
        }
    }

    object RubiconSwitch extends RH("12043") {
        override def abilities(self : Card)(implicit g : Game) = {
            val l = g.rezzedIce.%(c => g.state(c).rezzedTurn == g.turn && c.data.cost.|(0) <= g.credits(Runner))
            $(Ability("pay X credits to derez a piece of ice with printed rez cost X rezzed this turn", click(), $(ChooseCardEffect(Runner, l, RHRubiconPurpose(self), "Rubicon Switch: choose a piece of ice to derez", false)), click = true, once = once("rubicon"), ok = l.any, reason = "no ice rezzed this turn"))
        }
        override def chosenRH(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(c => g.push(LoseCreditsEffect(Runner, c.data.cost.|(0)), DerezEffect(c)))
            NextAction
        }
    }

    object AdjustedMatrix extends RH("12046") {
        override def modHosts(self : Card)(implicit g : Game) = |(icebreakers)
        override def onInstall(self : Card)(implicit g : Game) = hostOf(self).$./(h => AddSubtypeEffect(h, "AI"))
        override def breaks(self : Card)(implicit g : Game) =
            hostOf(self).%(h => encounteredIce.exists(i => g.breakerStrength(h) >= g.iceStrength(i))).$./(h => BreakAbility($(LoseClickCost(1)), 1, $(), |("lose a click: break 1 subroutine (host " + asciiTitle(h) + ")")))
    }

    object DedicatedProcessor extends RH("12047") {
        override def modHosts(self : Card)(implicit g : Game) = |(icebreakers.%(c => !g.has(c, "AI")))
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("host icebreaker gets +4 strength", c(2), hostOf(self).$./(h => PumpBreakerEffect(h, 4, EncounterDuration)), ok = hostOf(self).any && encounteredIce.any, reason = "not encountering ice"))
    }

    object Maui extends RH("12063") {
        override def memory(self : Card)(implicit g : Game) = 2
        override def recurring(self : Card)(implicit g : Game) = g.ice(HQ).num
        override def creditsUsableForRun(self : Card)(implicit g : Game) = g.run.exists(r => !r.ended && r.server == HQ)
    }

    object Daredevil extends RH("12066") {
        override def memory(self : Card)(implicit g : Game) = 2
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case RunInitiated(s) if g.ice(s).num >= 2 && firstTime(self, "daredevil") => $(MarkUsedEffect(self, "daredevil"), says(self, "draw 2 cards"), draw(Runner, 2))
            case _ => $
        }
    }

    object Respirocytes extends RH("12102") {
        override def onInstall(self : Card)(implicit g : Game) = $(meat(1))
        override def triggers(self : Card, e : Event)(implicit g : Game) =
            (g.hand(Runner).none && firstTime(self, "respirocytes") && g.isInstalled(self) && !g.pending.has(RHRespirocytesAction(self)) && g.phase != "mulligan").$(RHRespirocytesAction(self))
        override def performRH(a : CardAction)(implicit g : Game) = a match {
            case RHRespirocytesAction(self) =>
                if (g.hand(Runner).none && firstTime(self, "respirocytes") && g.isInstalled(self)) {
                    g.markUsed(self, "respirocytes")
                    g.log(self, "draws 1 card and places a power counter")
                    g.addCounter(self, "power", 1)
                    if (g.counter(self, "power") >= 3)
                        g.push(draw(Runner, 1), TrashEffect(self, Runner, false))
                    else
                        g.push(draw(Runner, 1))
                }
                NextAction
        }
    }

    object Polyhistor extends RH("13005") {
        override def memory(self : Card)(implicit g : Game) = 1
        override def link(self : Card)(implicit g : Game) = 1
        def offer(self : Card)(implicit g : Game) : $[ForcedAction] =
            (g.deck(Runner).any && firstTime(self, "polyhistor")).$(OptionalEffect(Runner, "Polyhistor: draw 1 card to force the Corp to draw 1 card?", $(MarkUsedEffect(self, "polyhistor"), says(self, "draw 1 card and make the Corp draw 1 card"), draw(Runner, 1), draw(Corp, 1)), $))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case PassIce(_) if g.run.exists(r => !r.ended && r.server == HQ && r.position == 0) => offer(self)
            case RunInitiated(HQ) if g.ice(HQ).none => offer(self)
            case _ => $
        }
    }

    object LLDSMemoryDiamond extends RH("13015") {
        override def memory(self : Card)(implicit g : Game) = 1
        override def link(self : Card)(implicit g : Game) = 1
        override def handSize(self : Card, f : Faction)(implicit g : Game) = f.isRunner.??(1)
    }

    object Ubax extends RH("13016") {
        override def memory(self : Card)(implicit g : Game) = 1
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) => $(says(self, "draw 1 card"), draw(Runner, 1))
            case _ => $
        }
    }

    abstract class BMIBufferBase(code : String, free : Boolean) extends RH(code) {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case DamageDone(_, _) => g.lastDamaged.%(c => c.data.isProgram && g.zone(c) == DiscardZone(Runner))./(c => RHStashFromHeapEffect(c, self))
            case Trashed(c, _, _) if c.data.isProgram && g.owner(c).isRunner && g.lastTrashedFrom == HandZone(Runner) => $(RHStashFromHeapEffect(c, self))
            case _ => $
        }
        override def abilities(self : Card)(implicit g : Game) = {
            val l = g.stashed(self).%(c => c.data.isProgram && (free || g.canAffordInstall(c, 0)))
            $(Ability("install a hosted program" + free.??(" ignoring all costs"), click(2), $(ChooseCardEffect(Runner, l, InstallPayingPurpose(free.?(99).|(0), None, false), "BMI Buffer: choose a program to install", false)), click = true, ok = l.any, reason = "no hosted program"))
        }
    }

    object BMIBuffer extends BMIBufferBase("14020", false)
    object BMIBuffer2 extends BMIBufferBase("14021", true)

    object SpinalModem extends RH("20007") {
        override def memory(self : Card)(implicit g : Game) = 1
        override def recurring(self : Card)(implicit g : Game) = 2
        override def breakerCredits(self : Card, breaker : Card)(implicit g : Game) = g.isIcebreaker(breaker)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case RHTraceResolved(true) if g.run.exists(!_.ended) => $(MessageEffect(Runner, "suffers 1 core damage (Spinal Modem)"), core(1))
            case _ => $
        }
    }

    object Doppelganger extends RH("20025") {
        override def memory(self : Card)(implicit g : Game) = 1
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case RunEnds(_, true, _) if firstTime(self, "doppelganger") && g.run.exists(!_.accessOnly) =>
                $(OptionalEffect(Runner, "Doppelganger: run any server?", $(MarkUsedEffect(self, "doppelganger"), says(self, "run again"), RHDoppelgangerAction(self)), $))
            case _ => $
        }
        override def performRH(a : CardAction)(implicit g : Game) = a match {
            case RHDoppelgangerAction(self) =>
                g.insertAfterRunCleanup(RunAnyServerEffect(|(self), g.servers))
                NextAction
        }
    }

    object RabbitHole extends RH("20046") {
        override def link(self : Card)(implicit g : Game) = 1
        override def onInstall(self : Card)(implicit g : Game) =
            g.deck(Runner).exists(_.title == self.title).$(OptionalEffect(Runner, "Rabbit Hole: search the stack for another copy and install it?", $(RHRabbitHoleAction(self)), $))
        override def performRH(a : CardAction)(implicit g : Game) = a match {
            case RHRabbitHoleAction(self) =>
                g.deck(Runner).%(_.title == self.title).starting.foreach { c =>
                    if (g.credits(Runner) >= g.installCost(c, None))
                        g.push(RunnerInstallPayingEffect(c, None, 0))
                    else
                        g.log(Runner, "cannot afford to install another", self)
                }
                g.push(ShuffleDeckEffect(Runner))
                NextAction
        }
    }

    object ThePersonalTouch extends RH("20047") {
        override def modHosts(self : Card)(implicit g : Game) = |(icebreakers)
        override def breakerStrengthMod(self : Card, breaker : Card)(implicit g : Game) = hostOf(self).has(breaker).?(1).|(0)
    }

    object DysonMemChip extends RH("20057") {
        override def memory(self : Card)(implicit g : Game) = 1
        override def link(self : Card)(implicit g : Game) = 1
    }

    object Zamba extends RH("21003") {
        override def memory(self : Card)(implicit g : Game) = 2
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case RHExposed(c) if g.owner(c).isCorp => $(says(self, "gain 1 credit"), gain(Runner, 1))
            case _ => $
        }
    }

    object Cyberdelia extends RH("21006") {
        override def memory(self : Card)(implicit g : Game) = 1
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SubsBroken(ice, _, _) if g.unbrokenSubs(ice).none && firstTime(self, "cyberdelia") => $(MarkUsedEffect(self, "cyberdelia"), says(self, "gain 1 credit"), gain(Runner, 1))
            case _ => $
        }
    }

    object Acacia extends RH("21021") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Purged(n) if g.isInstalled(self) => $(OptionalEffect(Runner, "Acacia: trash it to gain " + n + " credits?", $(TrashEffect(self, Runner, false), gain(Runner, n)), $))
            case _ => $
        }
    }

    object FridayChip extends RH("21042") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Trashed(c, Runner, _) if g.owner(c).isCorp => $(says(self, "place 1 virus counter on itself"), AddCounterEffect(self, "virus", 1))
            case TurnBegins(Runner) if g.counter(self, "virus") > 0 && virusPrograms.any =>
                $(ChooseCardEffect(Runner, virusPrograms, RHFridayPurpose(self), "Friday Chip: move 1 hosted virus counter to a virus program?", true))
            case _ => $
        }
        override def chosenRH(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(c => g.push(AddCounterEffect(p.card, "virus", -1), AddCounterEffect(c, "virus", 1)))
            NextAction
        }
    }

    object Knobkierie extends RH("21062") {
        override def memory(self : Card)(implicit g : Game) = g.installedCards(Runner).%(c => c.data.isProgram && g.has(c, "Virus"))./(_.data.memory.|(0)).sum.atMost(3)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(_) if g.reg("successful-run") == 1 && virusPrograms.any =>
                $(ChooseCardEffect(Runner, virusPrograms, RHAddCounterPurpose(self, "virus", 1), "Knobkierie: place 1 virus counter on a virus program?", true))
            case _ => $
        }
    }

    object Gebrselassie extends RH("21087") {
        override def abilities(self : Card)(implicit g : Game) = {
            val l = icebreakers.%(c => !g.has(c, "AI") && !hostOf(self).has(c))
            $(Ability("host it on an installed non-AI icebreaker", click(), $(ChooseCardEffect(Runner, l, RHGebrselassiePurpose(self), "Gebrselassie: choose an icebreaker", false)), click = true, ok = l.any, reason = "no non-AI icebreaker"))
        }
        override def chosenRH(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(b => g.push(HostOnEffect(p.card, b, false)))
            NextAction
        }
        override def pumpDuration(self : Card, breaker : Card, d : Duration)(implicit g : Game) = hostOf(self).has(breaker).?(TurnDuration : Duration).|(d)
    }

    object Zer0 extends RH("21101") {
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("suffer 1 net damage: gain 1 credit and draw 2 cards", click(), $(net(1), gain(Runner, 1), draw(Runner, 2)), click = true, once = once("zer0"), ok = g.hand(Runner).any, reason = "no cards in the grip"))
    }

    object Hippo extends RH("21103") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SubsBroken(ice, _, _) if g.unbrokenSubs(ice).none && firstTime(self, "hippo") && g.run.exists(r => !r.ended && g.ice(r.server).lastOption.has(ice)) =>
                $(MarkUsedEffect(self, "hippo"), OptionalEffect(Runner, "Hippo: remove it from the game to trash the fully broken ice?", $(says(self, "trash the ice"), RemoveFromGameEffect(self), TrashEffect(ice, Runner, false)), $))
            case _ => $
        }
    }

    object FlameOut extends RH("21109") {
        override def initialCounters(self : Card)(implicit g : Game) = Map("credit" -> 9)
        override def canHost(self : Card, card : Card)(implicit g : Game) = card.data.isProgram && hostedPrograms(self).none
        override def breakerCredits(self : Card, breaker : Card)(implicit g : Game) = g.hosted(self).has(breaker)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case CreditsSpentFrom(c, _) if c == self => $(SetCounterEffect(self, "flame", 1))
            case TurnEnds(_) if g.counter(self, "flame") > 0 => $(SetCounterEffect(self, "flame", 0)) ++ hostedPrograms(self)./(c => TrashEffect(c, Runner, false))
            case _ => $
        }
    }

    object HijackedRouter extends RH("22005") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case ServerCreated(_) => $(says(self, "make the Corp lose 1 credit"), lose(Corp, 1))
            case SuccessfulRun(Archives) => $(OptionalEffect(Runner, "Hijacked Router: trash it to make the Corp lose 3 credits?", $(TrashEffect(self, Runner, false), lose(Corp, 3)), $))
            case _ => $
        }
    }

    object MindsEye extends RH("22017") {
        override def memory(self : Card)(implicit g : Game) = 1
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(RnD) => $(says(self, "place 1 power counter"), AddCounterEffect(self, "power", 1))
            case _ => $
        }
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("breach R&D", $(ClickCost(1), CounterCost("power", 3)), $(BreachServerEffect(RnD)), click = true, ok = g.counter(self, "power") >= 3, reason = "needs 3 power counters"))
    }

    object Mache extends RH("22018") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Trashed(c, Runner, true) if g.owner(c).isCorp && firstTime(self, "mache") && c.data.trashCost.|(0) > 0 =>
                $(MarkUsedEffect(self, "mache"), says(self, "place power counters"), AddCounterEffect(self, "power", g.trashCost(c).|(0)))
            case _ => $
        }
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("draw 1 card", $(CounterCost("power", 3)), $(draw(Runner, 1)), ok = g.counter(self, "power") >= 3, reason = "needs 3 power counters"))
    }

    object Cyberfeeder extends RH("25008") {
        def virus(c : Card)(implicit g : Game) = c.data.isProgram && g.has(c, "Virus") && c.data.cost.|(0) > 0
        override def recurring(self : Card)(implicit g : Game) = 1
        override def breakerCredits(self : Card, breaker : Card)(implicit g : Game) = g.isIcebreaker(breaker)
        override def installCostMod(self : Card, card : Card)(implicit g : Game) = (virus(card) && g.counter(self, "credit") > 0).?(-1).|(0)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Installed(Runner, c) if virus(c) && g.counter(self, "credit") > 0 => $(MessageEffect(Runner, "uses 1 credit from Cyberfeeder"), AddCounterEffect(self, "credit", -1))
            case _ => $
        }
    }

    object Patchwork extends RH("25009") {
        override def memory(self : Card)(implicit g : Game) = 1
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("trash 1 card from the grip: the next card you play or install this turn costs 2 less", $(TrashFromHandCost(1)), $(SetCounterEffect(self, "patchwork", 1)), once = once("patchwork"), ok = g.hand(Runner).num >= 2 && g.counter(self, "patchwork") == 0, reason = "needs 2 cards in the grip"))
        override def installCostMod(self : Card, card : Card)(implicit g : Game) = (g.counter(self, "patchwork") > 0 && card != self && g.owner(card).isRunner).?(-2).|(0)
        override def playCostMod(self : Card, card : Card)(implicit g : Game) = (g.counter(self, "patchwork") > 0 && card.data.isEvent).?(-2).|(0)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Installed(Runner, c) if c != self && g.counter(self, "patchwork") > 0 => $(SetCounterEffect(self, "patchwork", 0))
            case Played(Runner, _) if g.counter(self, "patchwork") > 0 => $(SetCounterEffect(self, "patchwork", 0))
            case TurnEnds(_) if g.counter(self, "patchwork") > 0 => $(SetCounterEffect(self, "patchwork", 0))
            case _ => $
        }
    }

    object HQInterface extends RH("25031") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case BreachServer(HQ) => $(says(self, "access 1 additional card"), AccessBonusEffect(HQ, 1))
            case _ => $
        }
    }

    object Paragon extends RH("25032") {
        override def memory(self : Card)(implicit g : Game) = 1
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(_) if g.reg("successful-run") == 1 =>
                $(OptionalEffect(Runner, "Paragon: gain 1 credit and look at the top card of the stack?", $(says(self, "gain 1 credit"), gain(Runner, 1), RHParagonAction(self)), $))
            case _ => $
        }
        override def performRH(a : CardAction)(implicit g : Game) = a match {
            case RHParagonAction(self) =>
                g.deck(Runner).starting.foreach { c =>
                    g.push(OptionalEffect(Runner, "Paragon: the top card of the stack is " + asciiTitle(c) + ". Add it to the bottom of the stack?", $(MoveEffect(c, DeckZone(Runner), false), MessageEffect(Runner, "adds the top card of the stack to the bottom")), $))
                }
                NextAction
        }
    }

    object AkamatsuMemChip extends RH("25048") {
        override def memory(self : Card)(implicit g : Game) = 1
    }

    object Dinosaurus extends RH("25049") {
        override def canHost(self : Card, card : Card)(implicit g : Game) = card.data.isProgram && card.data.isIcebreaker && !g.has(card, "AI") && hostedPrograms(self).none
        override def hostsWithoutMU(self : Card)(implicit g : Game) = true
        override def memory(self : Card)(implicit g : Game) = hostedMU(self)
        override def breakerStrengthMod(self : Card, breaker : Card)(implicit g : Game) = g.hosted(self).has(breaker).?(2).|(0)
    }

    object RnDInterface extends RH("25050") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case BreachServer(RnD) => $(says(self, "access 1 additional card"), AccessBonusEffect(RnD, 1))
            case _ => $
        }
    }

    object E3FeedbackImplants extends RH("29003") {
        override def breaks(self : Card)(implicit g : Game) =
            encounteredIce.exists(i => g.state(i).brokenSubs.any).$(BreakAbility(c(1), 1, $(), |("break 1 more subroutine (e3)")))
    }
}


object RotHardwareCards {
    import RotHardwareCards1._
    import RotHardwareCards2._

    val all : $[CardImpl] = $(
        Grimoire, LemuriaCodecracker, Desperado, TheToolbox, CortezChip, PlascreteCarapace, MureshBodysuit, Replicator, Monolith,
        FeedbackFilter, CloneChip, OmniDrive, Lockpick, RecordReconstructor, DeepRed, LLDSProcessor, Capstone, Blackguard,
        CyberSolutionsMemChip, DysonFractalGenerator, Silencer, Logos, PublicTerminal, UnregisteredSW35, Window, QCoherenceChip,
        BoxE, Autoscripter, Astrolabe, Ekomind, CybsoftMacroDrive, ArchivesInterface, ChopBot3000, MemStrips, Vigil, QianjuPT,
        DormComputer, Comet, Skulljack, Turntable, TitaniumRibs, NetReadyEyes, BrainCage, Forger, Bookmark, Heartbeat, BrainChip,
        SecurityChip, SecurityNexus,
        Ramujan, Maya, EMPDevice, NetChip, Reflection, SpyCamera, SportsHopper, GPINetTap, Mirror, Obelus, TheGauntlet, TopHat,
        Sifr, ReconDrone, Maw, SeverniusStimImplant, RubiconSwitch, AdjustedMatrix, DedicatedProcessor, Maui, Daredevil,
        Respirocytes, Polyhistor, LLDSMemoryDiamond, Ubax, BMIBuffer, BMIBuffer2, SpinalModem, Doppelganger, RabbitHole,
        ThePersonalTouch, DysonMemChip, Zamba, Cyberdelia, Acacia, FridayChip, Knobkierie, Gebrselassie, Zer0, Hippo, FlameOut,
        HijackedRouter, MindsEye, Mache, Cyberfeeder, Patchwork, HQInterface, Paragon, AkamatsuMemChip, Dinosaurus, RnDInterface,
        E3FeedbackImplants,
    )
}
