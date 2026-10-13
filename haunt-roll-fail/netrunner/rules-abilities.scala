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
case class WindowPassAction(self : Faction, kind : String) extends BaseAction()(kind match { case "movement" => "Continue the run" ; case _ => "Done" })
case class JackOutAction(self : Faction) extends BaseAction()("Jack out")
case class ContinueRunAction(self : Faction) extends ForcedAction


trait AbilityRules { game : Game =>

    var windowPassed : $[Faction] = $

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
            if (r.exists(_.ended) && kind != "turn-begins") {
                windowPassed = $
                NextAction
            }
            else {
                val asks = $(active, active.opponent)./~ { f =>
                    val l = windowOptions(f, kind)
                    (l.any && windowPassed.has(f).not).?(Ask(f).group(windowTitle(kind)).add(l).add(WindowPassAction(f, kind)))
                }
                if (asks.none) {
                    windowPassed = $
                    NextAction
                }
                else if (asks.num == 1)
                    asks.only
                else
                    MultiAsk(asks)
            }

        case WindowPassAction(f, kind) =>
            windowPassed :+= f
            if (kind == "movement" && f.isRunner)
                log(Runner, "continues the run")
            PaidWindowAction(kind)

        case JackOutAction(f) =>
            run.foreach(_.jackedOut = true)
            windowPassed = $
            log(Runner, "jacks out")
            push(EndRunEffect)
            NextAction

        case ContinueRunAction(f) =>
            NextAction
    }

    def windowTitle(kind : String) : Elem = kind match {
        case "turn-begins" => "Start of turn"
        case "run-initiated" => "Run initiated"
        case "approach-ice" => "Approaching " ~ run./~(_.ice)./(c => isRezzed(c).?(cardElem(c)).|("unrezzed ice".hl)).|(Empty)
        case "movement" => "Passed ice"
        case "approach-server" => "Approaching " ~ run./(_.server.elem).|(Empty)
        case _ => "Paid abilities"
    }

    def windowOptions(f : Faction, kind : String) : $[UserAction] = {
        val ctx = "window:" + kind
        val rez = f.isCorp.??(rezOptions(f, ctx, (kind == "approach-ice").??(run./~(_.ice).%(c => !isRezzed(c)))))
        val jack = (f.isRunner && kind == "movement" && run.exists(r => !r.cannotJackOut)).$(JackOutAction(f))
        rez ++ abilityOptions(f, ctx, false) ++ jack
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
                UseRunnerAbilityAction(f, ice, i, ctx).!(!a.ok, a.reason).!(!canPay(f, a.costs, |(ice)), "cannot pay")
            }
        })
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
