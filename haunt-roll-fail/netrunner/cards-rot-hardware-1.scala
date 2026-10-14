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
// Rotated hardware (Core Set .. Data and Destiny), ported from jinteki.net src/clj/game/cards/hardware.clj
// ---------------------------------------------------------------------------------------------------------------------

case class RHCortezPurpose(card : Card) extends CardPurpose
case class RHReplicatorAction(card : Card, installed : Card) extends CardAction
case class RHCapstonePurpose(card : Card) extends CardPurpose
case class RHBlackguardAction(card : Card, target : Card) extends CardAction
case class RHLogosAction(card : Card) extends CardAction
case class RHWindowDrawAction(card : Card) extends CardAction
case class RHArchivesInterfaceAction(card : Card) extends CardAction
case class RHChopBotPurpose(card : Card) extends CardPurpose
case class RHTurntablePurpose(card : Card, stolen : Card) extends CardPurpose
case class RHBookmarkTakeAction(card : Card) extends CardAction
case class RHMayaAction(card : Card, target : Card) extends CardAction
case class RHGpiAction(card : Card) extends CardAction


object RotHardwareCards1 {
    object Grimoire extends RH("01006") {
        override def memory(self : Card)(implicit g : Game) = 2
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Installed(Runner, c) if c.data.isProgram && g.has(c, "Virus") => $(says(self, "place 1 virus counter"), AddCounterEffect(c, "virus", 1))
            case _ => $
        }
    }

    object LemuriaCodecracker extends RH("01023") {
        override def abilities(self : Card)(implicit g : Game) = {
            val l = unrezzedInstalled
            $(Ability("expose 1 card", $(ClickCost(1), CreditCost(1)), expose(l, "Lemuria Codecracker: choose a card to expose", false, self), click = true, ok = g.successfulRunsThisTurn.has(HQ) && l.any, reason = "needs a successful run on HQ"))
        }
    }

    object Desperado extends RH("01024") {
        override def memory(self : Card)(implicit g : Game) = 1
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(_) => $(says(self, "gain 1 credit"), gain(Runner, 1))
            case _ => $
        }
    }

    object TheToolbox extends RH("01041") {
        override def memory(self : Card)(implicit g : Game) = 2
        override def link(self : Card)(implicit g : Game) = 2
        override def recurring(self : Card)(implicit g : Game) = 2
        override def breakerCredits(self : Card, breaker : Card)(implicit g : Game) = g.isIcebreaker(breaker)
    }

    object CortezChip extends RH("02005") {
        override def abilities(self : Card)(implicit g : Game) = {
            val l = g.allIce.%(c => !g.isRezzed(c))
            $(Ability("the Corp pays 2 credits more to rez a piece of ice this turn", $(TrashSelfCost), $(ChooseCardEffect(Runner, l, RHCortezPurpose(self), "Cortez Chip: choose a piece of ice", false)), ok = l.any, reason = "no unrezzed ice"))
        }
        override def chosenRH(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.foreach { c =>
                g.log(c, "costs 2 credits more to rez this turn")
                g.push(AddFlagEffect(Flag("rh-rez-surcharge", |(c), None, "turn", 0, $)))
            }
            NextAction
        }
    }

    object PlascreteCarapace extends RH("02009") {
        override def initialCounters(self : Card)(implicit g : Game) = Map("power" -> 4)
        override def triggers(self : Card, e : Event)(implicit g : Game) = trashOnEmpty(self, e, "power")
        override def prevent(self : Card, kind : String, n : Int)(implicit g : Game) : $[Ability] =
            (kind == "meat").$(Ability("prevent 1 meat damage", $(CounterCost("power", 1)), $(Prevent.damage(1), TriggerEventAction(CounterAdded(self, "power")))))
    }

    object MureshBodysuit extends RH("02044") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case PreventionInterrupt("meat", 1, n) if Prevent.damageCtx.exists(_.firstThisTurn) => $(says(self, "prevent 1 meat damage"), Prevent.damage(1))
            case _ => $
        }
    }

    object Replicator extends RH("02088") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Installed(Runner, c) if c.data.isHardware && g.deck(Runner).exists(_.title == c.title) =>
                $(OptionalEffect(Runner, "Replicator: search the stack for another copy of the installed hardware?", $(says(self, "search the stack"), RHReplicatorAction(self, c)), $))
            case _ => $
        }
        override def performRH(a : CardAction)(implicit g : Game) = a match {
            case RHReplicatorAction(self, c) =>
                g.deck(Runner).%(_.title == c.title).starting.foreach { x =>
                    g.log(Runner, "reveals", x, "from the stack and adds it to the grip")
                    g.moveTo(x, HandZone(Runner))
                }
                g.push(ShuffleDeckEffect(Runner))
                NextAction
        }
    }

    object Monolith extends RH("03036") {
        override def memory(self : Card)(implicit g : Game) = 3
        override def onInstall(self : Card)(implicit g : Game) = $(InstallLoopAction(self, Runner, 3, 4, false, "hand", $("Program")))
        override def prevent(self : Card, kind : String, n : Int)(implicit g : Game) : $[Ability] = {
            val l = g.hand(Runner).%(_.data.isProgram)
            (kind == "net" || kind == "core").$(Ability("trash a program from the grip to prevent 1 damage", NoCost, $(ChooseCardEffect(Runner, l, RHTrashProgramPreventPurpose(self, kind, n), "Monolith: choose a program to trash", false)), ok = l.any, reason = "no program in the grip"))
        }
    }

    object FeedbackFilter extends RH("03037") {
        override def prevent(self : Card, kind : String, n : Int)(implicit g : Game) : $[Ability] =
            (kind == "net").$(Ability("prevent 1 net damage", c(3), $(Prevent.damage(1)))) ++
            (kind == "core").$(Ability("prevent up to 2 core damage", $(TrashSelfCost), $(Prevent.damage(2))))
    }

    object CloneChip extends RH("03038") {
        override def abilities(self : Card)(implicit g : Game) = {
            val l = g.discard(Runner).%(c => c.data.isProgram && g.canAffordInstall(c, 0))
            $(Ability("install a program from the heap", $(TrashSelfCost), $(ChooseCardEffect(Runner, l, InstallPayingPurpose(0, None, false), "Clone Chip: choose a program to install", false)), ok = l.any, reason = "no installable program in the heap"))
        }
    }

    object OmniDrive extends RH("03039") {
        override def recurring(self : Card)(implicit g : Game) = 1
        override def breakerCredits(self : Card, breaker : Card)(implicit g : Game) = g.hosted(self).has(breaker)
        override def canHost(self : Card, card : Card)(implicit g : Game) = card.data.isProgram && card.data.memory.|(0) <= 1 && hostedPrograms(self).none
        override def hostsWithoutMU(self : Card)(implicit g : Game) = true
        override def memory(self : Card)(implicit g : Game) = hostedMU(self)
    }

    object Lockpick extends RH("04006") {
        override def recurring(self : Card)(implicit g : Game) = 1
        override def breakerCredits(self : Card, breaker : Card)(implicit g : Game) = g.isIcebreaker(breaker) && g.has(breaker, "Decoder")
    }

    object RecordReconstructor extends RH("04028") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(Archives) if g.discard(Corp).exists(c => g.state(c).seen) =>
                val l = g.discard(Corp).%(c => g.state(c).seen).sortBy(_.title)
                $(OptionalEffect(Runner, "Record Reconstructor: instead of breaching Archives, add a faceup card from Archives to the top of R&D?", $(says(self, "add a card from Archives to the top of R&D"), ReplaceBreachEffect, ChooseCardEffect(Runner, l, RHToTopPurpose(self), "Record Reconstructor: choose a card", false)), $))
            case _ => $
        }
    }

    object DeepRed extends RH("04042") {
        def caissa(c : Card)(implicit g : Game) = g.has(c, "Caïssa") || g.has(c, "Caissa")
        override def memory(self : Card)(implicit g : Game) = g.installedCards(Runner).%(c => c.data.isProgram && caissa(c))./(_.data.memory.|(0)).sum.atMost(3)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Installed(Runner, c) if c.data.isProgram && caissa(c) && g.impl(c).abilities(c).exists(_.click) =>
                $(OptionalEffect(Runner, "Deep Red: trigger the click ability of the installed program without spending a click?", g.impl(c).abilities(c).%(_.click).starting./(_.effects).|($), $))
            case _ => $
        }
    }

    object LLDSProcessor extends RH("04066") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Installed(Runner, c) if g.isIcebreaker(c) => $(says(self, "give +1 strength"), PumpBreakerEffect(c, 1, TurnDuration))
            case _ => $
        }
    }

    object Capstone extends RH("04068") {
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("trash cards from the grip, draw 1 card for each one with an installed copy", click(), $(ChooseCardsEffect(Runner, g.hand(Runner), 1, g.hand(Runner).num, RHCapstonePurpose(self), "Capstone: choose cards to trash from the grip")), click = true, ok = g.hand(Runner).any, reason = "no cards in the grip"))
        override def chosenRH(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            val titles = g.installedCards(Runner).%(c => g.owner(c).isRunner)./(_.title)
            val n = cards.%(c => titles.has(c.title)).num
            cards.foreach(c => g.moveTo(c, DiscardZone(Runner)))
            if (cards.any)
                g.log(Runner, "trashes", cards./(g.cardElem).comma, "from the grip")
            if (n > 0)
                g.push(draw(Runner, n))
            NextAction
        }
    }

    object Blackguard extends RH("04085") {
        override def memory(self : Card)(implicit g : Game) = 2
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Exposed(l) => l.%(c => g.owner(c).isCorp && !c.data.isAgenda)./(c => RHBlackguardAction(self, c) : ForcedAction)
            case _ => $
        }
        override def performRH(a : CardAction)(implicit g : Game) = a match {
            case RHBlackguardAction(self, c) =>
                if (g.isInstalled(c) && !g.isRezzed(c) && g.impl(c).canRez(c) && g.credits(Corp) >= g.rezCost(c) + g.rhRezSurcharge(c) && !g.rhRezBlocked(c)) {
                    g.log(Corp, "is forced to rez", c, "(Blackguard)")
                    g.push(RezEffect(c, false))
                }
                NextAction
        }
    }

    object CyberSolutionsMemChip extends RH("04086") {
        override def memory(self : Card)(implicit g : Game) = 2
    }

    object DysonFractalGenerator extends RH("04103") {
        override def recurring(self : Card)(implicit g : Game) = 1
        override def breakerCredits(self : Card, breaker : Card)(implicit g : Game) = g.isIcebreaker(breaker) && g.has(breaker, "Fracter")
    }

    object Silencer extends RH("04104") {
        override def recurring(self : Card)(implicit g : Game) = 1
        override def breakerCredits(self : Card, breaker : Card)(implicit g : Game) = g.isIcebreaker(breaker) && g.has(breaker, "Killer")
    }

    object Logos extends RH("05037") {
        override def memory(self : Card)(implicit g : Game) = 1
        override def handSize(self : Card, f : Faction)(implicit g : Game) = f.isRunner.??(1)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case AgendaScored(_) if g.deck(Runner).any => $(OptionalEffect(Runner, "Logos: search the stack for a card?", $(says(self, "search the stack"), RHLogosAction(self)), $))
            case _ => $
        }
        override def performRH(a : CardAction)(implicit g : Game) = a match {
            case RHLogosAction(self) =>
                g.push(ChooseCardEffect(Runner, g.deck(Runner).sortBy(_.title), AddToHandPurpose(Runner, false), "Logos: choose a card to add to the grip", false), ShuffleDeckEffect(Runner))
                NextAction
        }
    }

    object PublicTerminal extends RH("05038") {
        def runEvent(c : Card)(implicit g : Game) = c.data.isEvent && g.has(c, "Run") && c.data.cost.|(0) > 0
        override def recurring(self : Card)(implicit g : Game) = 1
        override def playCostMod(self : Card, card : Card)(implicit g : Game) = (runEvent(card) && g.counter(self, "credit") > 0).?(-1).|(0)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Played(Runner, c) if runEvent(c) && g.counter(self, "credit") > 0 => $(MessageEffect(Runner, "uses 1 credit from Public Terminal"), AddCounterEffect(self, "credit", -1))
            case _ => $
        }
    }

    object UnregisteredSW35 extends RH("05039") {
        override def abilities(self : Card)(implicit g : Game) = {
            val l = g.remotes./~(s => g.root(s)).%(c => g.isRezzed(c) && $("Bioroid", "Clone", "Executive", "Sysop").exists(t => g.has(c, t)))
            $(Ability("trash a rezzed bioroid, clone, executive or sysop in the root of a remote server", click(2), $(ChooseCardEffect(Runner, l, TrashPurpose(Runner), "Unregistered S and W 35: choose a card to trash", false)), click = true, ok = g.successfulRunsThisTurn.has(HQ) && l.any, reason = "needs a successful run on HQ and a target"))
        }
    }

    object Window extends RH("05040") {
        override def abilities(self : Card)(implicit g : Game) = $(Ability("draw the bottom card of the stack", click(), $(RHWindowDrawAction(self)), click = true, ok = g.deck(Runner).any, reason = "the stack is empty"))
        override def performRH(a : CardAction)(implicit g : Game) = a match {
            case RHWindowDrawAction(self) =>
                g.deck(Runner).lastOption.foreach { c =>
                    g.moveTo(c, HandZone(Runner))
                    g.log(Runner, "draws the bottom card of the stack")
                    g.trigger(CardsDrawn(Runner, $(c)))
                }
                NextAction
        }
    }

    object QCoherenceChip extends RH("05052") {
        override def memory(self : Card)(implicit g : Game) = 1
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Trashed(c, _, _) if c.data.isProgram && g.owner(c).isRunner && (g.lastTrashedFrom == RigZone || g.lastTrashedFrom.is[HostedZone]) && g.isInstalled(self) => $(TrashEffect(self, Runner, false))
            case _ => $
        }
    }

    object BoxE extends RH("06055") {
        override def memory(self : Card)(implicit g : Game) = 2
        override def handSize(self : Card, f : Faction)(implicit g : Game) = f.isRunner.??(2)
    }

    object Autoscripter extends RH("06076") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Installed(Runner, c) if c.data.isProgram && g.active.isRunner && g.rhInstallFrom.get(c).has(HandZone(Runner)) && firstTime(self, "autoscripter") =>
                $(MarkUsedEffect(self, "autoscripter"), says(self, "gain a click"), GainClicksEffect(Runner, 1))
            case UnsuccessfulRun(_) => $(TrashEffect(self, Runner, false))
            case _ => $
        }
    }

    object Astrolabe extends RH("06079") {
        override def memory(self : Card)(implicit g : Game) = 1
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case ServerCreated(_) => $(says(self, "draw 1 card"), draw(Runner, 1))
            case _ => $
        }
    }

    object Ekomind extends RH("06093") {
        override def memory(self : Card)(implicit g : Game) = g.hand(Runner).num - 4
    }

    object CybsoftMacroDrive extends RH("06098") {
        def ok(c : Card) = c.data.isProgram && c.data.cost.|(0) > 0
        override def recurring(self : Card)(implicit g : Game) = 1
        override def installCostMod(self : Card, card : Card)(implicit g : Game) = (ok(card) && g.counter(self, "credit") > 0).?(-1).|(0)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Installed(Runner, c) if ok(c) && c != self && g.counter(self, "credit") > 0 => $(MessageEffect(Runner, "uses 1 credit from Cybsoft MacroDrive"), AddCounterEffect(self, "credit", -1))
            case _ => $
        }
    }

    object ArchivesInterface extends RH("07044") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case BreachServer(Archives) if g.discard(Corp).any => $(RHArchivesInterfaceAction(self))
            case _ => $
        }
        override def performRH(a : CardAction)(implicit g : Game) = a match {
            case RHArchivesInterfaceAction(self) =>
                val facedown = g.discard(Corp).%(c => !g.state(c).seen)
                facedown.foreach(c => g.state(c).seen = true)
                if (facedown.any)
                    g.log(facedown.num.cards, "in Archives turned faceup:", facedown.sortBy(_.title)./(g.cardElem).comma)
                g.push(ChooseCardEffect(Runner, g.discard(Corp).sortBy(_.title), RemoveFromGamePurpose, "Archives Interface: remove a card in Archives from the game instead of accessing it?", true))
                NextAction
        }
    }

    object ChopBot3000 extends RH("07045") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) if g.runnerInstalled.but(self).any =>
                $(ChooseCardEffect(Runner, g.runnerInstalled.but(self), RHChopBotPurpose(self), "Chop Bot 3000: trash another installed card to draw 1 card or remove 1 tag?", true))
            case _ => $
        }
        override def chosenRH(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach { c =>
                g.push(ChooseOneEffect(Runner, "Chop Bot 3000: choose one", $(Alt("Draw 1 card", $(draw(Runner, 1))), Alt("Remove 1 tag", $(RemoveTagsEffect(1)), g.tags > 0))))
                g.trashCard(c, Runner, false)
            }
            NextAction
        }
    }

    object MemStrips extends RH("07046") {
        override def memory(self : Card)(implicit g : Game) = g.installedCards(Runner).%(c => c.data.isProgram && g.has(c, "Virus"))./(_.data.memory.|(0)).sum.atMost(3)
    }

    object Vigil extends RH("07047") {
        override def memory(self : Card)(implicit g : Game) = 1
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) if g.hand(Corp).num >= g.handSize(Corp) => $(says(self, "draw 1 card"), draw(Runner, 1))
            case _ => $
        }
    }

    object QianjuPT extends RH("07054") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) =>
                (g.counter(self, "qianju") > 0).$(SetCounterEffect(self, "qianju", 0)) ++
                $(OptionalEffect(Runner, "Qianju PT: lose a click to avoid the first tag until your next turn?", $(says(self, "avoid the next tag"), LoseClicksEffect(Runner, 1), SetCounterEffect(self, "qianju", 1)), $))
            case _ => $
        }
        override def avoidTags(self : Card, n : Int)(implicit g : Game) = {
            if (g.counter(self, "qianju") > 0) {
                g.state(self).counters += "qianju" -> 0
                1
            }
            else
                0
        }
    }

    object DormComputer extends RH("08024") {
        override def initialCounters(self : Card)(implicit g : Game) = Map("power" -> 4)
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("run any server, avoiding all tags during the run", $(ClickCost(1), CounterCost("power", 1)), $(RunAnyServerEffect(|(self), g.servers)), click = true, ok = g.counter(self, "power") > 0, reason = "no power counters"))
        override def avoidTags(self : Card, n : Int)(implicit g : Game) = g.run.exists(r => !r.ended && r.source.has(self)).?(n).|(0)
    }

    object Comet extends RH("08027") {
        override def memory(self : Card)(implicit g : Game) = 1
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Played(Runner, c) if c.data.isEvent && firstTime(self, "comet-first") => $(MarkUsedEffect(self, "comet-first"), says(self, "allow another event this turn"), SetCounterEffect(self, "comet", 1))
            case TurnEnds(_) if g.counter(self, "comet") > 0 => $(SetCounterEffect(self, "comet", 0))
            case _ => $
        }
        override def abilities(self : Card)(implicit g : Game) = {
            val l = g.hand(Runner).%(c => c.data.isEvent && g.impl(c).canPlay(c) && g.credits(Runner) >= g.playCost(c))
            $(Ability("play another event without spending a click", NoCost, $(SetCounterEffect(self, "comet", 0), ChooseCardEffect(Runner, l, PlayPurpose(self, true, false), "Comet: choose an event to play", false)), ok = g.counter(self, "comet") > 0 && l.any && g.active.isRunner && g.run.none, reason = "no event played this turn"))
        }
    }

    object Skulljack extends RH("08042") {
        override def onInstall(self : Card)(implicit g : Game) = $(core(1))
        override def trashCostMod(self : Card, card : Card)(implicit g : Game) = g.owner(card).isCorp.?(-1).|(0)
    }

    object Turntable extends RH("08043") {
        override def memory(self : Card)(implicit g : Game) = 1
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case AgendaStolen(c) if g.scored(Corp).exists(_.data.isAgenda) =>
                $(ChooseCardEffect(Runner, g.scored(Corp).%(_.data.isAgenda), RHTurntablePurpose(self, c), "Turntable: swap the stolen agenda with an agenda in the Corp score area?", true))
            case _ => $
        }
        override def chosenRH(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case RHTurntablePurpose(_, stolen) =>
                cards.single.foreach(c => g.push(RHSwapAgendasEffect(stolen, c)))
                NextAction
        }
    }

    object TitaniumRibs extends RH("08045") {
        override def onInstall(self : Card)(implicit g : Game) = $(meat(2))
        override def damageChoice(self : Card, kind : String, n : Int)(implicit g : Game) = |((Runner, n))
    }

    object NetReadyEyes extends RH("08047") {
        override def onInstall(self : Card)(implicit g : Game) = $(meat(2))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case RunInitiated(_) if icebreakers.any => $(says(self, "give an icebreaker +1 strength"), ChooseCardEffect(Runner, icebreakers, RHPumpRunPurpose(self, 1), "Net-Ready Eyes: choose an icebreaker to get +1 strength for the run", false))
            case _ => $
        }
    }

    object BrainCage extends RH("08049") {
        override def handSize(self : Card, f : Faction)(implicit g : Game) = f.isRunner.??(3)
        override def onInstall(self : Card)(implicit g : Game) = $(core(1))
    }

    object Forger extends RH("08065") {
        override def link(self : Card)(implicit g : Game) = 1
        override def prevent(self : Card, kind : String, n : Int)(implicit g : Game) : $[Ability] =
            (kind == "tag").$(Ability("avoid 1 tag", $(TrashSelfCost), $(Prevent.tags(1))))
        override def abilities(self : Card)(implicit g : Game) = $(Ability("remove 1 tag", $(TrashSelfCost), $(RemoveTagsEffect(1)), ok = g.tagged, reason = "not tagged"))
    }

    object Bookmark extends RH("08106") {
        override def abilities(self : Card)(implicit g : Game) = $(
            Ability("host up to 3 cards from the grip facedown", click(), $(ChooseCardsEffect(Runner, g.hand(Runner), 1, 3, StashPurpose(self, true), "Bookmark: choose up to 3 cards to host")), click = true, ok = g.hand(Runner).any, reason = "no cards in the grip"),
            Ability("add all hosted cards to the grip", click(), $(RHBookmarkTakeAction(self)), click = true, ok = g.stashed(self).any, reason = "no hosted cards"),
            Ability("trash it: add all hosted cards to the grip", NoCost, $(RHBookmarkTakeAction(self), TrashEffect(self, Runner, false)), ok = g.stashed(self).any, reason = "no hosted cards"))
        override def performRH(a : CardAction)(implicit g : Game) = a match {
            case RHBookmarkTakeAction(self) =>
                val l = g.stashed(self)
                l.foreach(c => g.moveTo(c, HandZone(Runner)))
                if (l.any)
                    g.log(Runner, "adds", l.num.cards, "hosted on", self, "to the grip")
                NextAction
        }
    }

    object Heartbeat extends RH("09032") {
        override def memory(self : Card)(implicit g : Game) = 1
        override def prevent(self : Card, kind : String, n : Int)(implicit g : Game) : $[Ability] =
            $("net", "meat", "core").has(kind).$(Ability("trash an installed card to prevent 1 damage", $(TrashInstalledCost("")), $(Prevent.damage(1))))
    }

    object BrainChip extends RH("09039") {
        override def memory(self : Card)(implicit g : Game) = g.agendaPoints(Runner).atLeast(0)
        override def handSize(self : Card, f : Faction)(implicit g : Game) = f.isRunner.??(g.agendaPoints(Runner).atLeast(0))
    }

    object SecurityChip extends RH("09046") {
        override def abilities(self : Card)(implicit g : Game) = {
            val l = icebreakers.%(c => !g.has(c, "Cloud"))
            val clouds = icebreakers.%(c => g.has(c, "Cloud"))
            val inRun = g.run.exists(!_.ended)
            $(
                Ability("an icebreaker gets +" + g.link + " strength for the run", $(TrashSelfCost), $(ChooseCardEffect(Runner, l, RHPumpRunPurpose(self, g.link), "Security Chip: choose an icebreaker", false)), ok = inRun && l.any && g.link > 0, reason = "only during a run"),
                Ability("cloud icebreakers get +" + g.link + " strength for the run", $(TrashSelfCost), $(ChooseCardsEffect(Runner, clouds, 1, clouds.num, RHPumpRunPurpose(self, g.link), "Security Chip: choose cloud icebreakers")), ok = inRun && clouds.any && g.link > 0, reason = "only during a run"))
        }
    }

    object SecurityNexus extends RH("09047") {
        override def memory(self : Card)(implicit g : Game) = 1
        override def link(self : Card)(implicit g : Game) = 1
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case EncounterIce(ice) if firstTime(self, "nexus") =>
                $(OptionalEffect(Runner, "Security Nexus: have the Corp trace 5 to bypass this ice?", $(MarkUsedEffect(self, "nexus"), says(self, "make the Corp trace 5"), TraceEffect(5, $(tag(1), EndRunEffect), $(BypassEffect))), $))
            case _ => $
        }
    }
}
