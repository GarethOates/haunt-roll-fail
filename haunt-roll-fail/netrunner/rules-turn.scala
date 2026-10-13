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


case class StartTurnAction(f : Faction) extends ForcedAction
case class TurnBeginsAction(f : Faction) extends ForcedAction
case class MandatoryDrawAction(f : Faction) extends ForcedAction
case class ActionPhaseAction(f : Faction) extends ForcedAction with Reaskable
case class DiscardPhaseAction(f : Faction) extends ForcedAction with Reaskable
case class DiscardCardsAction(self : Faction, cards : $[Card]) extends ForcedAction
case class EndTurnAction(f : Faction) extends ForcedAction

case class ClickCreditAction(self : Faction) extends BaseAction("Basic actions")("Gain 1 credit")
case class ClickDrawAction(self : Faction) extends BaseAction("Basic actions")("Draw a card")
case class PlayMenuAction(self : Faction) extends BaseAction("Basic actions")(self.isCorp.?("Play an operation").|("Play an event")) with Soft
case class PlayCardAction(self : Faction, card : Card) extends BaseAction("Play")(g => g.cardElem(card) ~ " (" ~ g.playCost(card).hl ~ "[c])")
case class InstallMenuAction(self : Faction) extends BaseAction("Basic actions")("Install a card") with Soft
case class InstallWhereAction(self : Faction, card : Card) extends BaseAction("Install")(g => g.cardElem(card)) with Soft
case class InstallTrashingAction(self : Faction, card : Card, trash : Card) extends BaseAction("Install")(g => g.cardElem(card) ~ ", trashing " ~ g.cardElem(trash)) with Soft
case class CorpInstallAction(self : Faction, card : Card, server : |[Server]) extends BaseAction("Install")(g => g.cardElem(card) ~ " in " ~ server./(_.elem).|("a new remote server".hl) ~ " (" ~ g.installCost(card, server).hl ~ "[c])")
case class RunnerInstallAction(self : Faction, card : Card, host : |[Card], trash : |[Card]) extends BaseAction("Install")(g => g.cardElem(card) ~ host./(h => " hosted on " ~ g.cardElem(h)).|(Empty) ~ trash./(t => ", trashing " ~ g.cardElem(t)).|(Empty) ~ " (" ~ g.installCost(card, None).hl ~ "[c])")
case class AdvanceMenuAction(self : Faction) extends BaseAction("Basic actions")("Advance a card") with Soft
case class AdvanceAction(self : Faction, card : Card) extends BaseAction("Advance")(g => g.cardElem(card) ~ " (" ~ g.advancement(card).hl ~ ")")
case class ScoreMenuAction(self : Faction) extends BaseAction("Basic actions")("Score an agenda") with Soft
case class ScoreAction(self : Faction, card : Card) extends BaseAction("Score")(g => g.cardElem(card))
case class TrashResourceMenuAction(self : Faction) extends BaseAction("Basic actions")("Trash a resource (2 credits)") with Soft
case class TrashResourceAction(self : Faction, card : Card) extends BaseAction("Trash a resource")(g => g.cardElem(card))
case class PurgeAction(self : Faction) extends BaseAction("Basic actions")("Purge virus counters (3 clicks)")
case class RunMenuAction(self : Faction) extends BaseAction("Basic actions")("Run a server") with Soft
case class RunAction(self : Faction, server : Server) extends BaseAction("Run")(server)
case class RemoveTagAction(self : Faction) extends BaseAction("Basic actions")("Remove a tag (2 credits)")
case class RezMenuAction(self : Faction) extends BaseAction("Basic actions")("Rez a card") with Soft
case class RezAction(self : Faction, card : Card, ctx : String) extends BaseAction("Rez")(g => g.cardElem(card) ~ " (" ~ g.rezCost(card).hl ~ "[c])")
case class EndTurnClickAction(self : Faction) extends BaseAction()("End turn")
case class ConcedeAction(self : Faction) extends BaseAction()("Concede") with Soft with NotForBots
case class ConcedeConfirmAction(self : Faction) extends BaseAction("Really concede?")("Concede".styled(xstyles.error)) with NotForBots


trait TurnRules { game : Game =>

    def performTurn : PartialFunction[Action, Continue] = {
        case StartTurnAction(f) =>
            if (f.isCorp)
                turn += 1
            active = f
            turnStarted = true
            phase = "turn-begins"
            register = Map()
            cannotScoreThisTurn = false
            scoredPointsThisTurn = 0
            runsThisTurn = $
            successfulRunsThisTurn = $
            clickDrawBonus = 0
            runnerDamageThisTurn = 0
            (cs.values).foreach { s => s.usedTurn = $ ; s.strengthTurn = 0 }
            clicks += f -> (f.isCorp.?(3).|(4))
            clicks += f -> (clicks(f) + bonusClicks.get(f).|(0)).atLeast(0) ; bonusClicks -= f

            log(DoubleLine)
            log("Turn", turn.hl, f, "(" ~ credits(f).credits ~ ", " ~ hand(f).num.cards ~ ")")

            pending = $(PaidWindowAction("turn-start"), TurnBeginsAction(f)) ++ f.isCorp.$(MandatoryDrawAction(f)) ++ $(ActionPhaseAction(f)) ++ pending

            Milestone(NextAction)

        case TurnBeginsAction(f) =>
            // "when your turn begins" abilities, followed by a second paid ability window if anything happened
            val l = triggerEffects(TurnBegins(f))
            if (l.any) {
                push(PaidWindowAction("turn-begins"))
                pushAll(l)
            }
            NextAction

        case MandatoryDrawAction(f) =>
            phase = "mandatory-draw"
            push(DrawEffect(f, 1))
            enqueueAfterDraw(f)
            NextAction

        case ActionPhaseAction(f) =>
            if (isOver)
                IdleAction
            else {
                phase = "action"
                val n = clicks(f)

                val own = if (n <= 0)
                    Ask(f)
                        .group("No clicks remaining")
                        .add(f.isCorp.??(scoreOptions(f)))
                        .add(f.isCorp.??(rezOptions(f, "action", None)))
                        .add(abilityOptions(f, "action", false))
                        .add(EndTurnClickAction(f))
                        .add(ConcedeAction(f))
                else
                    Ask(f)
                        .group(n.clicks ~ " remaining")
                        .add(ClickCreditAction(f))
                        .add(ClickDrawAction(f).!(deck(f).none, "empty"))
                        .add(PlayMenuAction(f).!(hand(f).%(c => c.data.isOperation || c.data.isEvent).none, "nothing to play"))
                        .add(InstallMenuAction(f).!(hand(f).%(c => installable(c)).none, "nothing to install"))
                        .add(f.isCorp.$(AdvanceMenuAction(f).!(credits(f) < 1 || advanceableCards.none, "nothing to advance")))
                        .add(f.isCorp.??(scoreOptions(f)))
                        .add(f.isCorp.??(rezOptions(f, "action", None)))
                        .add(f.isCorp.$(TrashResourceMenuAction(f).!(!tagged || credits(f) < 2 || installedCards(Runner).%(_.data.isResource).none, "Runner is not tagged")))
                        .add(f.isCorp.$(PurgeAction(f).!(n < 3, "needs 3 clicks")))
                        .add(f.isRunner.$(RunMenuAction(f)))
                        .add(f.isRunner.$(RemoveTagAction(f).!(tags == 0 || credits(f) < 2, "not tagged")))
                        .add(abilityOptions(f, "action", true))
                        .add(ConcedeAction(f))

                // paid ability window for the opponent between actions (non-blocking: the active player may act meanwhile)
                windowAsk(f.opponent, "action", takeActed()) match {
                    case Some(ask) => MultiAsk($(own, ask))
                    case None => own
                }
            }

        case ClickCreditAction(f) =>
            spendClick(f)
            credits += f -> (credits(f) + 1)
            log(f, "clicks for", 1.credits)
            ActionPhaseAction(f)

        case ClickDrawAction(f) =>
            spendClick(f)
            regInc("click-draw")
            log(f, "clicks to draw")
            val n = 1 + clickDrawBonus
            clickDrawBonus = 0
            push(DrawEffect(f, n), ActionPhaseAction(f))
            trigger(ClickDraw(f))
            NextAction

        case PlayMenuAction(f) =>
            Ask(f).add(hand(f).%(c => c.data.isOperation || c.data.isEvent)./(c => PlayCardAction(f, c).!(credits(f) < playCost(c), "not enough credits").!(!impl(c).canPlay(c), "cannot play"))).cancel

        case PlayCardAction(f, c) =>
            spendClick(f)
            payCredits(f, playCost(c), None)
            moveTo(c, PlayZone(f))
            state(c).seen = true
            impl(c).initialCounters(c).foreach { case (k, n) => addCounter(c, k, n) }
            log(f, "plays", c, "(" ~ playCost(c).credits ~ ")")
            regInc("played")
            push(PlayCleanupAction(c), ActionPhaseAction(f))
            pushAll(impl(c).onPlay(c))
            trigger(Played(f, c))
            NextAction

        case InstallMenuAction(f) =>
            Ask(f).add(hand(f).%(installable)./(c => InstallWhereAction(f, c))).cancel

        case InstallWhereAction(f, c) =>
            if (f.isCorp) {
                val l = (c.data.isIce || c.data.isUpgrade).?(servers).|(remotes)
                Ask(f)
                    .add(l./(s => CorpInstallAction(f, c, |(s)).!(credits(f) < installCost(c, |(s)), "not enough credits")))
                    .add(CorpInstallAction(f, c, None).!(credits(f) < installCost(c, None), "not enough credits"))
                    .cancel
            }
            else {
                val cost = installCost(c, None)
                val mu = c.data.isProgram.??(c.data.memory.|(0))
                val hosts = impl(c).mustBeHosted(c).?(allIce.%(h => impl(c).hostRequirement(c, h))).|($)
                val trashOptions = (c.data.isProgram && memoryFree < mu).??(installedCards(Runner).%(isProgram).%(p => memoryFree + p.data.memory.|(0) >= mu))
                if (impl(c).mustBeHosted(c))
                    Ask(f).add(hosts./(h => RunnerInstallAction(f, c, |(h), None).!(credits(f) < cost, "not enough credits"))).cancel
                else if (c.data.isProgram && memoryFree < mu)
                    Ask(f).add(trashOptions./(p => RunnerInstallAction(f, c, None, |(p)).!(credits(f) < cost, "not enough credits"))).cancel
                else
                    Ask(f).add(RunnerInstallAction(f, c, None, None).!(credits(f) < cost, "not enough credits")).add(installedCards(Runner).%(h => isActive(h) && impl(h).canHost(h, c))./(h => RunnerInstallAction(f, c, |(h), None).!(credits(f) < cost, "not enough credits"))).cancel
            }

        case CorpInstallAction(f, c, s) =>
            spendClick(f)
            push(CorpInstallEffect(c, s, false), ActionPhaseAction(f))
            NextAction

        case RunnerInstallAction(f, c, h, t) =>
            spendClick(f)
            push(RunnerInstallEffect(c, h, false), ActionPhaseAction(f))
            t.foreach(p => trashCard(p, f, false))
            NextAction

        case AdvanceMenuAction(f) =>
            Ask(f).add(advanceableCards./(c => AdvanceAction(f, c))).cancel

        case AdvanceAction(f, c) =>
            spendClick(f)
            credits += f -> (credits(f) - 1)
            addCounter(c, "advancement", 1)
            log(f, "advances", hiddenCorpElem(c))
            regInc("advanced")
            push(ActionPhaseAction(f))
            trigger(Advanced(c, 1))
            NextAction

        case ScoreMenuAction(f) =>
            Ask(f).add(scorableAgendas./(c => ScoreAction(f, c))).cancel

        case ScoreAction(f, c) =>
            windowPassed = $
            actedInWindow = |(f)
            push(reask(ctxOf(current)))
            scoreAgenda(c)
            NextAction

        case TrashResourceMenuAction(f) =>
            Ask(f).add(installedCards(Runner).%(_.data.isResource)./(c => TrashResourceAction(f, c))).cancel

        case TrashResourceAction(f, c) =>
            spendClick(f)
            credits += f -> (credits(f) - 2)
            log(f, "pays", 2.credits, "to trash a resource")
            push(ActionPhaseAction(f))
            trashCard(c, f, false)
            NextAction

        case PurgeAction(f) =>
            spendClick(f, 3)
            push(PurgeEffect, ActionPhaseAction(f))
            NextAction

        case RunMenuAction(f) =>
            Ask(f).add(servers.%(canRunOn)./(s => RunAction(f, s))).cancel

        case RunAction(f, s) =>
            spendClick(f)
            push(ActionPhaseAction(f))
            startRun(s, None, true)

        case RemoveTagAction(f) =>
            spendClick(f)
            credits += f -> (credits(f) - 2)
            tags -= 1
            log(f, "pays", 2.credits, "to remove a tag")
            push(ActionPhaseAction(f))
            trigger(TagsRemoved(1))
            NextAction

        case RezMenuAction(f) =>
            Ask(f).add(rezOptions(f, "action", None)).cancel

        case RezAction(f, c, ctx) =>
            windowPassed = $
            actedInWindow = |(f)
            push(reask(ctx))
            rezCard(c, false)
            NextAction

        case EndTurnClickAction(f) =>
            push(DiscardPhaseAction(f), PaidWindowAction("discard"), EndTurnAction(f))
            trigger(ActionPhaseEnds(f))
            NextAction

        case ConcedeAction(f) =>
            Ask(f).add(ConcedeConfirmAction(f)).cancel

        case ConcedeConfirmAction(f) =>
            log(f, "concedes")
            GameOverAction(|(f.opponent), f.name + " conceded")

        case DiscardPhaseAction(f) =>
            phase = "discard"
            val max = handSize(f)
            if (f.isRunner && max < 0)
                GameOverAction(|(Corp), "Runner is flatlined (hand size below zero)")
            else {
                val n = hand(f).num - max.atLeast(0)
                if (n <= 0)
                    NextAction
                else
                    XXSelectObjectsAction(f, hand(f))
                        .withGroup("Discard phase: " ~ hand(f).num.cards ~ " in hand, maximum " ~ max.atLeast(0).hl ~ " - choose " ~ n.cards ~ " to discard")
                        .withRule(_.num(n))
                        .withThenElem(DiscardCardsAction(f, _))("Discard".hl)
            }

        case DiscardCardsAction(f, l) =>
            l.foreach { c =>
                if (f.isCorp) toArchives(c, false) else moveTo(c, DiscardZone(f))
            }
            log(f, "discards", l.num.cards)
            trigger(DiscardedToHandSize(f, l))
            NextAction

        case EndTurnAction(f) =>
            phase = "turn-ends"
            push(EndTurnCleanupAction(f))
            trigger(TurnEnds(f))
            NextAction

        case EndTurnCleanupAction(f) =>
            registerLastTurn = register
            if (f.isRunner)
                successfulRunsLastTurn = successfulRunsThisTurn
            cs.values.foreach { s => s.strengthTurn = 0 ; s.strengthRun = 0 ; s.strengthEncounter = 0 }
            log(f, "ends the turn", "(" ~ credits(f).credits ~ ", " ~ hand(f).num.cards ~ ")")
            turnStarted = false
            StartTurnAction(f.opponent)
    }

    def enqueueAfterDraw(f : Faction) {
        push(TriggerEventAction(MandatoryDraw(f)))
    }

    def installable(c : Card) : Boolean = c.data.kind match {
        case "Agenda" | "Asset" | "Upgrade" | "ICE" => owner(c).isCorp
        case "Program" | "Hardware" | "Resource" => owner(c).isRunner
        case _ => false
    }

    def advanceableCards : $[Card] = installedCards(Corp).%(c => isAdvanceable(c))

    def isAdvanceable(c : Card) : Boolean = c.data.isAgenda || impl(c).advanceable(c) || (isRezzed(c) && impl(c).advanceable(c))

    def scorableAgendas : $[Card] = installedCards(Corp).%(_.data.isAgenda).%(c => advancement(c) >= advancementRequirement(c)).%(_ => !cannotScoreThisTurn)

    def scoreOptions(f : Faction) : $[UserAction] = scorableAgendas./(c => ScoreAction(f, c))

    // the Corp may score agendas only in the paid ability windows of its own turn outside runs
    def canScoreNow(kind : String) : Boolean = active.isCorp && run.none && (kind == "action" || kind == "turn-start" || kind == "turn-begins")

    def rezOptions(f : Faction, ctx : String, approached : |[Card]) : $[UserAction] =
        (installedCards(Corp).%(c => !isRezzed(c) && !c.data.isIce && !c.data.isAgenda) ++ approached.$)./(c => RezAction(f, c, ctx).!(credits(Corp) < rezCost(c), "not enough credits"))

    def reask(ctx : String) : ForcedAction = ctx match {
        case "action" => ActionPhaseAction(active)
        case "window:action" => ActionPhaseAction(active)
        case "encounter" => run./~(_.ice)./(EncounterAction(_)).|(NextAction)
        case "window:encounter" => run./~(_.ice)./(EncounterAction(_)).|(NextAction)
        case s if s.startsWith("window:") => PaidWindowAction(s.drop(7))
        case _ => current
    }

    def ctxOf(a : ForcedAction) : String = a match {
        case ActionPhaseAction(_) => "action"
        case EncounterAction(_) => "encounter"
        case PaidWindowAction(k) => "window:" + k
        case _ => "current"
    }

    // scoring: the agenda moves to the score area, the win condition is checked at once, then "when scored" abilities resolve
    def scoreAgenda(c : Card) {
        val points = c.data.agendaPoints.|(0)
        lastScoredFrom = serverOf(c)
        lastScoredAdvancement = advancement(c) ; lastScoredInstalledTurn = state(c).installedTurn ; scoringAgenda = |(c)
        val hostedTrashed = leavePlay(c)
        moveTo(c, ScoredZone(Corp))
        state(c).seen = true
        hostedTrashed.foreach(h => log("Hosted card", cardElem(h), "is trashed"))
        cleanupRemotes()
        scoredPointsThisTurn += points
        regInc("scored-points", points)
        regInc("scored")
        log(Corp, "scores", c, "and gains", points.hl, "agenda point" + (points != 1).??("s"))
        pushAll(impl(c).onScore(c))
        trigger(AgendaScored(c))
        push(CheckWinAction())
    }

    def stealAgenda(c : Card) {
        val points = c.data.agendaPoints.|(0)
        lastStolenFrom = zoneServer(zone(c))
        val hostedTrashed = isInstalled(c).??(leavePlay(c))
        moveTo(c, ScoredZone(Runner))
        state(c).seen = true
        hostedTrashed.foreach(h => log("Hosted card", cardElem(h), "is trashed"))
        cleanupRemotes()
        log(Runner, "steals", c, "and gains", points.hl, "agenda point" + (points != 1).??("s"))
        regInc("stolen-points", points)
        regInc("stolen")
        run.foreach(_.didSteal = true)
        pushAll(impl(c).onSteal(c))
        trigger(AgendaStolen(c))
        push(CheckWinAction())
    }
}

case class EndTurnCleanupAction(f : Faction) extends ForcedAction
case class TriggerEventAction(e : Event) extends ForcedAction
