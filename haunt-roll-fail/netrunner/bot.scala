package netrunner
//
//
//
//
import hrf.colmat._
import hrf.compute._
import hrf.logger._
//
//
//
//

class RandomBot(f : Faction) extends EvalBot {
    def eval(actions : $[UserAction])(implicit game : Game) : Compute[$[ActionEval]] = {
        val l = actions.%!(_.is[NotForBots])
        (l.any.?(l).|(actions))./(a => ActionEval(a, $(Evaluation(random(1000), "random"))))
    }
}

class BasicBot(f : Faction) extends EvalBot {
    def eval(actions : $[UserAction])(implicit game : Game) : Compute[$[ActionEval]] = {
        val l = actions.%!(_.is[NotForBots])
        (l.any.?(l).|(actions))./(a => ActionEval(a, $(Evaluation(weight(a) * 10 + random(10), "basic"))))
    }

    def weight(a : UserAction)(implicit g : Game) : Int = a match {
        case ChoosePresetDeckAction(_, _) => 50
        case KeepHandAction(_) => 50
        case MulliganAction(_) => 10

        case ScoreAction(_, _) => 100
        case ClickCreditAction(_) => 20
        case ClickDrawAction(_) => (g.hand(f).num < 3).?(35).|(10)
        case PlayCardAction(_, c) => c.data.kind match {
            case "Operation" => (c.data.subtypes.has("Transaction")).?(60).|(30)
            case "Event" => (c.title == "Sure Gamble").?(70).|(25)
            case _ => 20
        }
        case CorpInstallAction(_, c, s) =>
            if (c.data.isAgenda) s./(r => g.ice(r).any.?(55).|(15)).|(40)
            else if (c.data.isIce) s./(r => (g.ice(r).none).?(50).|(30)).|(20)
            else if (c.data.isAsset) s./(_ => 25).|(35)
            else s./(_ => 25).|(5)
        case RunnerInstallAction(_, c, _, t) => t.any.?(5).|(c.data.isIcebreaker.?(55).|(c.data.isProgram.?(40).|(c.data.isHardware.?(40).|(35))))
        case AdvanceAction(_, c) => c.data.isAgenda.?(60).|(20)
        case RezAction(_, c, ctx) => c.data.isIce.?(80).|((ctx == "window:turn-begins" || ctx == "action").?(40).|(15))
        case RunAction(_, s) =>
            val ice = g.ice(s)
            val breakers = g.installedCards(Runner).%(g.isIcebreaker)
            val base = s match {
                case r : Remote => g.root(r).any.?(55).|(0)
                case HQ => 35
                case RnD => 40
                case Archives => 10
            }
            base - ice.num * (breakers.none.?(15).|(5)) + (g.credits(Runner) > 8).??(10)
        case RemoveTagAction(_) => 45
        case TrashResourceAction(_, _) => 50
        case PurgeAction(_) => 5
        case EndTurnClickAction(_) => 5
        case WindowPassAction(_, "movement") => 40
        case WindowPassAction(_, _) => 20
        case JackOutAction(_) => (g.credits(Runner) < 2).?(50).|(5)
        case StealAction(_, _) => 100
        case PayTrashAction(_, c) => (g.credits(Runner) > g.trashCost(c).|(0) + 3).?(50).|(10)
        case NoAccessAction(_, _) => 10
        case BreakSubAction(_, b, bi, ice, i) => 70
        case BreakSubsAction(_, _, _, _, _) => 75
        case PumpAction(_, b, _) => g.run./~(_.ice).exists(i => g.iceStrength(i) > g.breakerStrength(b)).?(65).|(1)
        case ContinueEncounterAction(_, ice) => (g.unbrokenSubs(ice).none).?(90).|(15)
        case UseAbilityAction(_, c, i, _) => 30
        case UseRunnerAbilityAction(_, _, _, _) => 20
        case YesAction(_, _, _) => 40
        case NoAction(_, _, _) => 20
        case ChoiceAction(_, _, _) => 30
        case CardChosenAction(_, _, _) => 30
        case DeclineChoiceAction(_, _) => 10
        case AccessChooseAction(_, _) => 30
        case TraceCorpAction(_, n) => 30 - n * 5
        case TraceRunnerAction(_, n) => 30 - n * 5
        case PsiBidAction(_, n) => 30
        case PreventPassAction(_, _, _) => 30
        case _ => 25
    }
}
