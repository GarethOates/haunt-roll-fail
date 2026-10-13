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


case class RunInitiationAction() extends ForcedAction with RunStep
case class RunNextAction() extends ForcedAction with RunStep
case class ApproachIceAction() extends ForcedAction with RunStep
case class AfterApproachAction() extends ForcedAction with RunStep
case class EncounterStartAction(ice : Card) extends ForcedAction with RunStep
case class EncounterAction(ice : Card) extends ForcedAction with RunStep with Reaskable
case class ResolveSubroutinesAction(ice : Card) extends ForcedAction with RunStep
case class FireSubAction(ice : Card, idx : Int) extends ForcedAction with RunStep
case class EncounterEndsAction(ice : Card) extends ForcedAction with RunStep
case class PassIceAction(ice : Card) extends ForcedAction with RunStep
case class MovementAction() extends ForcedAction with RunStep
case class ApproachServerAction() extends ForcedAction with RunStep
case class SuccessfulRunAction() extends ForcedAction with RunStep
case class RunEndsAction() extends ForcedAction
case class RunCleanupAction() extends ForcedAction

case class BreakSubAction(self : Faction, breaker : Card, bi : Int, ice : Card, sub : Int) extends BaseAction(g => g.cardElem(breaker) ~ " (strength " ~ g.breakerStrength(breaker).hl ~ ")")(g => "Break " ~ g.impl(ice).subroutines(ice)(g).lift(sub)./(_.label).|("?") ~ " (" ~ Cost.desc(g.impl(breaker).breaks(breaker)(g)(bi).costs) ~ ")")
case class BreakSubsAction(self : Faction, breaker : Card, bi : Int, ice : Card, subs : $[Int]) extends BaseAction(g => g.cardElem(breaker) ~ " (strength " ~ g.breakerStrength(breaker).hl ~ ")")(g => "Break " ~ subs.num.hl ~ " subroutines (" ~ Cost.desc(g.impl(breaker).breaks(breaker)(g)(bi).costs) ~ ")")
case class PumpAction(self : Faction, breaker : Card, pi : Int) extends BaseAction(g => g.cardElem(breaker) ~ " (strength " ~ g.breakerStrength(breaker).hl ~ ")")(g => g.impl(breaker).pumps(breaker)(g).lift(pi)./(p => p.label./(Text(_)).|(("+" + p.strength + " strength").txt) ~ " (" ~ Cost.desc(p.costs) ~ ")").|("?"))
case class ContinueEncounterAction(self : Faction, ice : Card) extends BaseAction()(g => "Continue".hl ~ (" (" + g.unbrokenSubs(ice).num + " unbroken subroutines)"))
case class BypassEffect() extends ForcedAction
case class BioroidBreakEffect(ice : Card) extends ForcedAction with RunStep with Reaskable
case class BioroidBreakSubAction(self : Faction, ice : Card, sub : Int) extends BaseAction(g => "Break a subroutine on " ~ g.cardElem(ice))(g => g.impl(ice).subroutines(ice)(g).lift(sub)./(_.label).|("?"))


trait RunRules { game : Game =>

    def unbrokenSubs(ice : Card) : $[Int] = impl(ice).subroutines(ice).indices.$.%!(i => state(ice).brokenSubs.has(i))

    def startRun(s : Server, source : |[Card], click : Boolean) : Continue = {
        val r = new Run(s, source, click)
        r.position = ice(s).num
        run = |(r)
        runsThisTurn :+= s
        regInc("runs")
        log(Runner, "runs", s, source./(c => "with " ~ cardElem(c)).|(Empty))
        push(RunInitiationAction())
        trigger(RunInitiated(s))
        NextAction
    }

    def performRun : PartialFunction[Action, Continue] = {
        case RunInitiationAction() =>
            push(PaidWindowAction("run-initiated"), RunNextAction())
            NextAction

        case RunNextAction() =>
            run match {
                case Some(r) if !r.ended =>
                    if (r.position > 0 && r.position <= ice(r.server).num)
                        push(ApproachIceAction())
                    else
                        push(ApproachServerAction())
                    NextAction
                case _ => NextAction
            }

        case ApproachIceAction() =>
            run match {
                case Some(r) if !r.ended =>
                    val c = ice(r.server)(r.position - 1)
                    r.ice = |(c)
                    r.phase = ApproachIcePhase
                    windowPassed = $
                    log(Runner, "approaches", isRezzed(c).?(cardElem(c)).|("unrezzed ice".hl), "protecting", r.server)
                    push(PaidWindowAction("approach-ice"), AfterApproachAction())
                    trigger(ApproachIce(c))
                    NextAction
                case _ => NextAction
            }

        case AfterApproachAction() =>
            run match {
                case Some(r) if !r.ended =>
                    r.ice match {
                        case Some(c) if isRezzed(c) => push(EncounterStartAction(c))
                        case Some(c) => push(PassIceAction(c))
                        case None => push(RunNextAction())
                    }
                    NextAction
                case _ => NextAction
            }

        case EncounterStartAction(c) =>
            run match {
                case Some(r) if !r.ended =>
                    r.phase = EncounterPhase
                    r.bypass = false
                    state(c).brokenSubs = $
                    log(Runner, "encounters", c)
                    push(EncounterAction(c))
                    pushAll(impl(c).onEncounter(c))
                    trigger(EncounterIce(c))
                    NextAction
                case _ => NextAction
            }

        case EncounterAction(c) =>
            run match {
                case Some(r) if !r.ended && !r.bypass && isRezzed(c) && isInstalled(c) =>
                    val subs = impl(c).subroutines(c)
                    val unbroken = unbrokenSubs(c)
                    val str = iceStrength(c)
                    val breakers = installedCards(Runner).%(b => isActive(b) && (impl(b).breaks(b).any || impl(b).pumps(b).any))
                    val breakOptions = breakers.distinct./~ { b =>
                        impl(b).breaks(b).indexed./~ { (br, bi) =>
                            val typeOk = (br.subtypes.none || br.subtypes.exists(t => has(c, t))) && impl(c).canBreakWith(c, b)
                            val strOk = !isIcebreaker(b) || breakerStrength(b) >= str
                            val payOk = canPay(Runner, br.costs, |(b))
                            if (!typeOk || unbroken.none)
                                $
                            else {
                                val singles = unbroken./(i => BreakSubAction(Runner, b, bi, c, i).!(!strOk, "strength too low").!(!payOk, "cannot pay"))
                                val multi = (br.n != 1 && unbroken.num > 1).$(BreakSubsAction(Runner, b, bi, c, (br.n == 0).?(unbroken).|(unbroken.take(br.n))).!(!strOk, "strength too low").!(!payOk, "cannot pay"))
                                singles ++ multi
                            }
                        }
                    }
                    val pumpOptions = breakers.distinct./~ { b =>
                        impl(b).pumps(b).indexed./ { (p, pi) =>
                            PumpAction(Runner, b, pi).!(!canPay(Runner, p.costs, |(b)), "cannot pay")
                        }
                    }
                    val runnerAsk = Ask(Runner)
                        .group("Encountering " ~ cardElem(c) ~ " (strength " ~ str.hl ~ ")" ~ Break ~ subs.indexed./((s, i) => (unbroken.has(i).?(("[sub] " + s.label).hl).|(("[sub] " + s.label).styled(styles.subBroken)))).join(Break))
                        .add(breakOptions)
                        .add(pumpOptions)
                        .add(abilityOptions(Runner, "encounter", false))
                        .add(ContinueEncounterAction(Runner, c))
                    // the Corp may use paid abilities and rez non-ice cards during the encounter
                    windowAsk(Corp, "encounter", takeActed()) match {
                        case Some(ask) => MultiAsk($(runnerAsk, ask))
                        case None => runnerAsk
                    }
                case Some(r) if !r.ended =>
                    push(EncounterEndsAction(c))
                    NextAction
                case _ => NextAction
            }

        case BreakSubAction(f, b, bi, c, i) =>
            val br = impl(b).breaks(b).apply(bi)
            windowPassed = $
            payCosts(f, br.costs, |(b))
            state(c).brokenSubs :+= i
            log(Runner, "uses", b, "to break", impl(c).subroutines(c).apply(i).label.hl, "on", c)
            push(EncounterAction(c))
            pushAll(br.after)
            trigger(SubsBroken(c, $(i), b))
            NextAction

        case BreakSubsAction(f, b, bi, c, l) =>
            val br = impl(b).breaks(b).apply(bi)
            windowPassed = $
            payCosts(f, br.costs, |(b))
            state(c).brokenSubs ++= l
            log(Runner, "uses", b, "to break", l.num.hl, "subroutines on", c)
            push(EncounterAction(c))
            pushAll(br.after)
            trigger(SubsBroken(c, l, b))
            NextAction

        case PumpAction(f, b, pi) =>
            val p = impl(b).pumps(b).apply(pi)
            windowPassed = $
            payCosts(f, p.costs, |(b))
            val s = state(b)
            pumpDurationFor(b, p.duration) match {
                case EncounterDuration => s.strengthEncounter += p.strength
                case RunDuration => s.strengthRun += p.strength
                case TurnDuration => s.strengthTurn += p.strength
            }
            log(Runner, "pumps", b, "to strength", breakerStrength(b).hl)
            run./~(_.ice)./(EncounterAction(_)).|(NextAction)

        case ContinueEncounterAction(f, c) =>
            windowPassed = $
            push(ResolveSubroutinesAction(c))
            NextAction

        case ResolveSubroutinesAction(c) =>
            val l = unbrokenSubs(c)
            pushAll(l./(i => FireSubAction(c, i)) :+ EncounterEndsAction(c))
            NextAction

        case FireSubAction(c, i) =>
            run match {
                case Some(r) if !r.ended =>
                    val s = impl(c).subroutines(c).lift(i)
                    s match {
                        case Some(sub) =>
                            r.subsFired += 1
                            log("Subroutine", sub.label.hl, "on", c, "fires")
                            push(sub.effect)
                        case None =>
                    }
                    NextAction
                case _ => NextAction
            }

        case EncounterEndsAction(c) =>
            state(c).brokenSubs = $
            state(c).strengthEncounter = 0
            installedCards(Runner).foreach(b => state(b).strengthEncounter = 0)
            pushAll(impl(c).onEncounterEnds(c))
            run match {
                case Some(r) if !r.ended =>
                    r.phase = MovementPhase
                    push(PassIceAction(c))
                    NextAction
                case _ => NextAction
            }

        case PassIceAction(c) =>
            run match {
                case Some(r) if !r.ended =>
                    r.position -= 1
                    r.phase = MovementPhase
                    r.ice = None
                    windowPassed = $
                    log(Runner, "passes", isRezzed(c).?(cardElem(c)).|("unrezzed ice".hl))
                    push(MovementAction())
                    trigger(PassIce(c))
                    NextAction
                case _ => NextAction
            }

        case MovementAction() =>
            run match {
                case Some(r) if !r.ended =>
                    push(PaidWindowAction("movement"), RunNextAction())
                    NextAction
                case _ => NextAction
            }

        case ApproachServerAction() =>
            run match {
                case Some(r) if !r.ended =>
                    r.phase = ApproachServerPhase
                    r.ice = None
                    windowPassed = $
                    log(Runner, "approaches", r.server)
                    push(PaidWindowAction("approach-server"), SuccessfulRunAction())
                    trigger(ApproachServer(r.server))
                    NextAction
                case _ => NextAction
            }

        case SuccessfulRunAction() =>
            run match {
                case Some(r) if !r.ended && allActive.exists(c => impl(c).blocksSuccessfulRun(c, r.server)) =>
                    log("The run on", r.server, "cannot be declared successful")
                    push(RunEndsAction())
                    NextAction
                case Some(r) if !r.ended =>
                    r.phase = SuccessPhase
                    r.successful = true
                    successfulRunsThisTurn :+= r.server
                    regInc("successful-run")
                    log("The run on", r.server, "is", "successful".hl)
                    push(BreachAction(r.server))
                    trigger(SuccessfulRun(r.server))
                    NextAction
                case _ => NextAction
            }

        case RunEndsAction() =>
            run match {
                case Some(r) =>
                    r.phase = EndedPhase
                    r.ended = true
                    if (!r.successful) {
                        r.unsuccessful = true
                        regInc("unsuccessful-run")
                    }
                    else
                        log("The run on", r.server, "ends")
                    push(RunCleanupAction())
                    if (r.unsuccessful)
                        trigger(UnsuccessfulRun(r.server))
                    trigger(RunEnds(r.server, r.successful, r.didSteal))
                    NextAction
                case None => NextAction
            }

        case RunCleanupAction() =>
            run.foreach { r =>
                lastRun = |(r)
                cs.values.foreach { s => s.strengthEncounter = 0 ; s.strengthRun = 0 ; s.brokenSubs = $ ; s.usedRun = $ }
                r.source.foreach { c =>
                    if (zone(c) == PlayZone(Runner)) {
                        moveTo(c, DiscardZone(Runner))
                    }
                }
            }
            run = None
            windowPassed = $
            NextAction

        case BypassEffect() =>
            run.foreach(_.bypass = true)
            run./~(_.ice).foreach(i => trigger(IceBypassed(i)))
            NextAction

        case BioroidBreakEffect(c) =>
            val l = unbrokenSubs(c)
            if (l.none)
                NextAction
            else
                Ask(Runner).add(l./(i => BioroidBreakSubAction(Runner, c, i)))

        case BioroidBreakSubAction(f, c, i) =>
            state(c).brokenSubs :+= i
            log(Runner, "breaks", impl(c).subroutines(c).apply(i).label.hl, "on", c)
            NextAction
    }
}
