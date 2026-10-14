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


case class UseAbilityAction(self : Faction, card : Card, idx : Int, ctx : String) extends BaseAction(g => g.cardElem(card))(g => g.impl(card).abilities(card)(g).lift(idx)./(a => a.label ~ (a.costs.any).?(" (" ~ Cost.desc(a.costs) ~ ")").|(Empty)).|("?"))
case class UseRunnerAbilityAction(self : Faction, card : Card, idx : Int, ctx : String) extends BaseAction(g => g.cardElem(card))(g => g.impl(card).runnerAbilities(card)(g).lift(idx)./(a => a.label ~ (a.costs.any).?(" (" ~ Cost.desc(a.costs) ~ ")").|(Empty)).|("?"))
case class AbilityCostCardsAction(self : Faction, card : Card, idx : Int, ctx : String, cards : $[Card], runner : Boolean) extends ForcedAction
case class PaidWindowAction(kind : String) extends ForcedAction with Reaskable
case class WindowPassAction(self : Faction, kind : String) extends BaseAction()(kind match { case "movement" => "Continue the run" ; case "action" => "No action" ; case _ => "Done" })
case class TrashProgramForMemoryAction(self : Faction, card : Card) extends BaseAction("Memory exceeded: trash a program")(g => g.cardElem(card) ~ " (" ~ card.data.memory.|(0).hl ~ " MU)")
case object CheckMemoryAction extends ForcedAction with Reaskable
case class JackOutAction(self : Faction) extends BaseAction()("Jack out")
case class ContinueRunAction(self : Faction) extends ForcedAction


trait AbilityRules { game : Game =>

    // paid ability window bookkeeping: a player who passed is not asked again while their options are unchanged
    var windowPassed : $[Faction] = $
    var passedSig : Map[Faction, String] = Map()
    var actedInWindow : |[Faction] = None

    def reopenWindows() {
        windowPassed = $
        passedSig = Map()
        actedInWindow = None
    }

    def optionSig(a : UserAction) : String = a match {
        case UnavailableReasonAction(x, _) => "x:" + optionSig(x)
        case RezAction(_, c, _) => "rez:" + c
        case UseAbilityAction(_, c, i, _) => "use:" + c + "/" + i
        case UseRunnerAbilityAction(_, c, i, _) => "ruse:" + c + "/" + i
        case JackOutAction(_) => "jack:" + run./(_.position).|(0)
        case ScoreAction(_, c) => "score:" + c
        case a => a.toString
    }

    def windowSig(l : $[UserAction]) : String = l./(optionSig).join(";")

    // the ask of faction f in the window of the given kind, if f has options and has not already declined the same options;
    // acted = the player who just used a window action: they are not asked again while their remaining options stay the same
    def windowAsk(f : Faction, kind : String, acted : |[Faction]) : |[Ask] = {
        val l = windowOptions(f, kind)
        if (acted.has(f))
            passedSig += f -> windowSig(l)
        // the non-blocking window between actions is always offered again after each action
        val declined = (kind != "action") && passedSig.get(f).has(windowSig(l))
        // greyed-out options alone (abilities that cannot be used now) do not open a window
        (l.exists(a => !a.is[Unavailable]) && windowPassed.has(f).not && !declined).?(Ask(f).group(windowTitle(kind)).add(l).add(WindowPassAction(f, kind)))
    }

    // consume the "just acted" marker (valid only for the window re-asked right after the action)
    def takeActed() : |[Faction] = {
        val a = actedInWindow
        actedInWindow = None
        a
    }

    def performAbilities : PartialFunction[Action, Continue] = {
        case UseAbilityAction(f, c, i, ctx) =>
            val a = impl(c).abilities(c).apply(i)
            a.costs.of[TrashFromHandCost].single match {
                case Some(TrashFromHandCost(n)) =>
                    XXSelectObjectsAction(f, hand(f))
                        .withGroup("Trash " ~ n.cards ~ " from hand for " ~ cardElem(c))
                        .withRule(_.num(n))
                        .withThenElem(AbilityCostCardsAction(f, c, i, ctx, _, false))("Trash".hl)
                case None =>
                    useAbility(f, c, a, ctx, $)
            }

        case UseRunnerAbilityAction(f, c, i, ctx) =>
            val a = impl(c).runnerAbilities(c).apply(i)
            useAbility(f, c, a, ctx, $)

        case AbilityCostCardsAction(f, c, i, ctx, l, runner) =>
            val a = runner.?(impl(c).runnerAbilities(c).apply(i)).|(impl(c).abilities(c).apply(i))
            useAbility(f, c, a, ctx, l)

        case PaidWindowAction(kind) =>
            val r = run
            if (isOver)
                IdleAction
            else if (r.exists(_.ended) && runWindows.has(kind)) {
                reopenWindows()
                NextAction
            }
            else {
                // a player who passed stays passed until something happens (an action, an ability, a rez, a triggered effect)
                val acted = takeActed()
                val asks = $(active, active.opponent)./~(f => windowAsk(f, kind, acted))
                if (asks.none)
                    NextAction
                else if (asks.num == 1)
                    asks.only
                else
                    MultiAsk(asks)
            }

        case WindowPassAction(f, kind) =>
            windowPassed :+= f
            passedSig += f -> windowSig(windowOptions(f, kind))
            if (kind == "movement" && f.isRunner)
                log(Runner, "continues the run")
            if (kind == "action")
                ActionPhaseAction(active)
            else if (kind == "encounter")
                run./~(_.ice)./(EncounterAction(_)).|(NextAction)
            else
                PaidWindowAction(kind)

        case CheckMemoryAction =>
            if (memoryFree >= 0 || isOver)
                NextAction
            else {
                val programs = installedCards(Runner).%(isProgram).%(c => c.data.memory.|(0) > 0)
                if (programs.none)
                    NextAction
                else
                    Ask(Runner).group("Memory in use " ~ memoryUsed.hl ~ " exceeds " ~ memoryTotal.hl ~ " MU").add(programs./(c => TrashProgramForMemoryAction(Runner, c)))
            }

        case TrashProgramForMemoryAction(f, c) =>
            trashCard(c, Runner, false)
            push(CheckMemoryAction)
            NextAction

        case JackOutAction(f) =>
            run.foreach(_.jackedOut = true)
            reopenWindows()
            log(Runner, "jacks out")
            push(EndRunEffect)
            NextAction

        case ContinueRunAction(f) =>
            NextAction
    }

    val runWindows = $("run-initiated", "approach-ice", "movement", "approach-server", "encounter")

    def windowTitle(kind : String) : Elem = kind match {
        case "turn-start" => "Start of " ~ active.name.styled(active) ~ " turn"
        case "turn-begins" => "Start of " ~ active.name.styled(active) ~ " turn (after triggers)"
        case "action" => active.name.styled(active) ~ " is acting - paid abilities"
        case "discard" => "End of " ~ active.name.styled(active) ~ " turn"
        case "run-initiated" => "Run initiated on " ~ run./(_.server.elem).|(Empty)
        case "approach-ice" => "Approaching " ~ run./~(_.ice)./(c => isRezzed(c).?(cardElem(c)).|("unrezzed ice".hl)).|(Empty) ~ " protecting " ~ run./(_.server.elem).|(Empty)
        case "movement" => "Passed " ~ run./(r => ice(r.server).lift(r.position)./(c => isRezzed(c).?(cardElem(c)).|("unrezzed ice".hl)).|("ice".txt)).|(Empty)
        case "approach-server" => "Approaching " ~ run./(_.server.elem).|(Empty)
        case "encounter" => "Encounter - paid abilities"
        case _ => "Paid abilities"
    }

    // the options of faction f in the paid ability window of the given kind (empty = nothing to do, the window is skipped)
    def windowOptions(f : Faction, kind : String) : $[UserAction] = {
        val ctx = "window:" + kind
        val rez = f.isCorp.??(rezOptions(f, ctx, (kind == "approach-ice").??(run./~(_.ice).%(c => !isRezzed(c)))))
        val score = (f.isCorp && canScoreNow(kind)).??(scoreOptions(f))
        val jack = (f.isRunner && kind == "movement" && run.exists(r => !r.cannotJackOut)).$(JackOutAction(f))
        score ++ rez ++ abilityOptions(f, ctx, false) ++ jack
    }

    // paid abilities available to f in context ctx
    def abilityOptions(f : Faction, ctx : String, allowClick : Boolean) : $[UserAction] = {
        (activeCards(f) ++ hand(f).%(c => impl(c).activeInHand(c)))./~ { c =>
            impl(c).abilities(c).indexed./~ { (a, i) =>
                if (a.click && !allowClick)
                    None
                else {
                    val clickOk = !a.click || (active == f && run.none && clicks(f) >= a.costs.of[ClickCost]./(_.n).sum.atLeast(1))
                    val onceOk = a.once.forall(k => !usedThisTurn(c, k))
                    val payOk = canPay(f, a.costs, |(c))
                    |(UseAbilityAction(f, c, i, ctx).!(!a.ok, a.reason).!(!onceOk, "already used this turn").!(!payOk, "cannot pay").!(!clickOk, "needs a click"))
                }
            }
        } ++ f.isRunner.??(run./~(_.ice).%(isRezzed).$./~ { ice =>
            impl(ice).runnerAbilities(ice).indexed./ { (a, i) =>
                UseRunnerAbilityAction(f, ice, i, ctx).!(!a.ok, a.reason).!(!canPay(f, a.costs, |(ice)), "cannot pay").!(has(ice, "Bioroid") && reg("no-bioroid-abilities") > 0, "blocked this turn")
            }
        }) ++ extraAbilityOptions(f, ctx, allowClick)
    }

    def useAbility(f : Faction, c : Card, a : Ability, ctx : String, costCards : $[Card]) : Continue = {
        payCosts(f, a.costs, |(c))
        costCards.foreach { x =>
            if (f.isCorp) toArchives(x, false) else moveTo(x, DiscardZone(f))
        }
        if (costCards.any)
            log(f, "trashes", costCards.num.cards, "from hand")
        a.once.foreach(k => markUsed(c, k))
        log(f, "uses", c, (a.costs.any).?("paying " + Cost.desc(a.costs)).|(""), "to", a.label)
        windowPassed = $
        actedInWindow = |(f)
        if (ctx == "action" && a.click)
            push(ActionPhaseAction(f))
        else
            push(reask(ctx))
        pushAll(a.effects)
        trigger(AbilityUsed(f, c, a.click))
        if (a.click)
            trigger(ActionTaken(f, "ability-" + c.code))
        NextAction
    }
}
