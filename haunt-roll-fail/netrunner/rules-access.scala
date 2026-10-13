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


case class BreachAction(server : Server) extends ForcedAction with RunStep
case class AccessServerAction(server : Server) extends ForcedAction with RunStep
case class HQAccessShuffledAction(n : Int, shuffled : $[Card]) extends ShuffledAction[Card] with RunStep
case class AccessNextAction() extends ForcedAction with RunStep with Reaskable
case class AccessChooseAction(self : Faction, card : Card) extends BaseAction("Access next")(g => g.accessElem(card))
case class AccessCardAction(card : Card) extends ForcedAction with RunStep
case class AccessDecisionAction(card : Card) extends ForcedAction with RunStep with Reaskable
case class StealAction(self : Faction, card : Card) extends BaseAction(g => g.accessGroup(card))("Steal".hl)
case class PayTrashAction(self : Faction, card : Card) extends BaseAction(g => g.accessGroup(card))(g => "Pay " ~ g.trashCost(card).|(0).hl ~ "[c] to trash")
case class AccessAbilityAction(self : Faction, source : Card, card : Card) extends BaseAction(g => g.accessGroup(card))(g => g.cardElem(source) ~ ": " ~ g.impl(source).accessAbility(source, card)(g)./(_.label).|("?"))
case class NoAccessAction(self : Faction, card : Card) extends BaseAction(g => g.accessGroup(card))("No action")
case class AccessDoneAction(card : Card) extends ForcedAction with RunStep
case class BreachEndAction() extends ForcedAction with RunStep
case class AccessAbilityCardsAction(self : Faction, source : Card, card : Card, cards : $[Card]) extends ForcedAction


trait AccessRules { game : Game =>

    // the accessed card shown with its image in the access prompt
    def accessGroup(c : Card) : Elem = Div(Image(c.code, netrunner.elem.styles.card), netrunner.elem.styles.viewcard) ~ Div("Accessing " ~ cardElem(c) ~ trashCost(c)./(t => " (trash cost " ~ t.hl ~ ")").|(Empty))

    def accessElem(c : Card) : Elem = zone(c) match {
        case HandZone(Corp) => "a card from HQ".hl
        case DeckZone(Corp) => "a card from R&D".hl
        case RootZone(s) if !isRezzed(c) => "an unrezzed card".hl
        case _ => cardElem(c)
    }

    def performAccess : PartialFunction[Action, Continue] = {
        case BreachAction(s) =>
            run match {
                case Some(r) if !r.ended =>
                    if (r.preventAccess) {
                        log(Runner, "is prevented from breaching", s)
                        push(RunEndsAction())
                        NextAction
                    }
                    else {
                        r.phase = AccessPhase
                        r.breachServer = |(s)
                        r.didAccess = true
                        log(Runner, "breaches", s)
                        if (s == HQ) regInc("breach-hq")
                        if (s == RnD) regInc("breach-rd")
                        if (s == Archives) regInc("breach-archives")
                        push(AccessServerAction(s))
                        trigger(BreachServer(s))
                        NextAction
                    }
                case _ => NextAction
            }

        case AccessServerAction(s) =>
            run match {
                case Some(r) if !r.ended =>
                    val upgrades = root(s)./~(c => c +: allHosted(c))
                    s match {
                        case HQ =>
                            val n = 1 + r.accessBonusFor("HQ") + r.accessBonusFor("total")
                            r.toAccess = upgrades
                            if (hand(Corp).any)
                                Shuffle[Card](hand(Corp), HQAccessShuffledAction(n, _), "HQ access")
                            else {
                                push(AccessNextAction())
                                NextAction
                            }
                        case RnD =>
                            val n = 1 + r.accessBonusFor("R&D") + r.accessBonusFor("total")
                            r.toAccess = deck(Corp).take(n) ++ upgrades
                            push(AccessNextAction())
                            NextAction
                        case Archives =>
                            // all cards in Archives are turned faceup and accessed (the order is irrelevant: present them as a set)
                            val facedown = discard(Corp).%(c => !state(c).seen)
                            discard(Corp).foreach(c => state(c).seen = true)
                            if (facedown.any)
                                log(facedown.num.cards, "in Archives turned faceup:", facedown.sortBy(_.title)./(cardElem).comma)
                            r.toAccess = discard(Corp).sortBy(_.title) ++ upgrades
                            push(AccessNextAction())
                            NextAction
                        case _ =>
                            r.toAccess = upgrades
                            push(AccessNextAction())
                            NextAction
                    }
                case _ => NextAction
            }

        case HQAccessShuffledAction(n, l) =>
            run.foreach { r =>
                r.toAccess = r.toAccess ++ l.take(n)
            }
            push(AccessNextAction())
            NextAction

        case AccessNextAction() =>
            run match {
                case Some(r) if !r.ended =>
                    r.toAccess = r.toAccess.%(c => location.contains(c) && !zone(c).is[ScoredZone] && zone(c) != DiscardZone(Runner) && zone(c) != RemovedZone(Corp))
                    // cards from R&D must be accessed in order (top first); root cards can be accessed before, between or after them
                    val rd = r.toAccess.%(c => zone(c) == DeckZone(Corp))
                    val others = r.toAccess.%(c => zone(c) != DeckZone(Corp))
                    val candidates = rd.take(1) ++ others
                    // cards in Archives without access effects are accessed automatically
                    val trivial = candidates.%(c => zone(c) == DiscardZone(Corp) && !c.data.isAgenda && impl(c).onAccess(c).none && activeCards(Runner)./~(s => impl(s).accessAbility(s, c)).none)
                    if (r.toAccess.none) {
                        push(BreachEndAction())
                        NextAction
                    }
                    else if (candidates.num == 1) {
                        push(AccessCardAction(candidates.head))
                        NextAction
                    }
                    else if (trivial.any) {
                        push(AccessCardAction(trivial.head))
                        NextAction
                    }
                    else
                        Ask(Runner).group("Breaching " ~ r.server.elem ~ ": choose the next card to access").add(candidates./(c => AccessChooseAction(Runner, c)))
                case _ => NextAction
            }

        case AccessChooseAction(f, c) =>
            push(AccessCardAction(c))
            NextAction

        case AccessCardAction(c) =>
            run match {
                case Some(r) if !r.ended =>
                    r.toAccess = r.toAccess.but(c)
                    r.accessed :+= c
                    r.accessCount += 1
                    regInc("accessed")
                    log(Runner, "accesses", cardElem(c), "from", zone(c) match {
                        case HandZone(_) => HQ.elem
                        case DeckZone(_) => RnD.elem
                        case DiscardZone(_) => Archives.elem
                        case _ => r.server.elem
                    })
                    push(AccessDecisionAction(c))
                    if (!zone(c).is[DiscardZone])
                        pushAll(impl(c).onAccess(c))
                    trigger(Accessed(c))
                    NextAction
                case _ => NextAction
            }

        case AccessDecisionAction(c) =>
            run match {
                case Some(r) if !r.ended && location.contains(c) && !zone(c).is[ScoredZone] =>
                    val inArchives = zone(c).is[DiscardZone]
                    val accessAbilities = activeCards(Runner)./~(s => impl(s).accessAbility(s, c)./(a => AccessAbilityAction(Runner, s, c).!(!a.ok, a.reason).!(!canPay(Runner, a.costs, |(s)), "cannot pay")))
                    if (c.data.isAgenda) {
                        val canSteal = !r.cannotStealOrTrash
                        val extra = impl(c).accessCostPaidByRunner(c)
                        if (canSteal && extra.none && accessAbilities.none) {
                            push(AccessDoneAction(c))
                            stealAgenda(c)
                            NextAction
                        }
                        else
                            Ask(Runner)
                                .add(StealAction(Runner, c).!(!canSteal, "cannot steal").!(!canPay(Runner, extra, None), "cannot pay"))
                                .add(accessAbilities)
                                .add(NoAccessAction(Runner, c))
                    }
                    else {
                        val tc = trashCost(c)
                        val canTrash = !inArchives && tc.any && !r.cannotStealOrTrash && impl(c).canBeTrashedOnAccess(c)
                        if (inArchives && accessAbilities.none) {
                            push(AccessDoneAction(c))
                            NextAction
                        }
                        else
                            Ask(Runner)
                                .add(canTrash.$(PayTrashAction(Runner, c).!(availableCredits(Runner, None) < tc.|(0), "not enough credits")))
                                .add(accessAbilities.%(_ => !inArchives))
                                .add(NoAccessAction(Runner, c))
                    }
                case _ =>
                    push(AccessDoneAction(c))
                    NextAction
            }

        case StealAction(f, c) =>
            val extra = impl(c).accessCostPaidByRunner(c)
            payCosts(Runner, extra, None)
            push(AccessDoneAction(c))
            stealAgenda(c)
            NextAction

        case PayTrashAction(f, c) =>
            val tc = trashCost(c).|(0)
            payCredits(Runner, tc, None)
            log(Runner, "pays", tc.credits, "to trash", c)
            push(AccessDoneAction(c))
            trashCard(c, Runner, true)
            NextAction

        case AccessAbilityAction(f, s, c) =>
            val a = impl(s).accessAbility(s, c).get
            a.costs.of[TrashFromHandCost].single match {
                case Some(TrashFromHandCost(n)) =>
                    XXSelectObjectsAction(f, hand(f))
                        .withGroup("Trash " ~ n.cards ~ " from hand for " ~ cardElem(s))
                        .withRule(_.num(n))
                        .withThenElem(AccessAbilityCardsAction(f, s, c, _))("Trash".hl)
                case None =>
                    push(AccessAbilityCardsAction(f, s, c, $))
                    NextAction
            }

        case AccessAbilityCardsAction(f, s, c, l) =>
            val a = impl(s).accessAbility(s, c).get
            payCosts(f, a.costs, |(s))
            l.foreach(x => moveTo(x, DiscardZone(Runner)))
            if (l.any)
                log(Runner, "trashes", l./(cardElem).comma, "from the grip")
            a.once.foreach(k => markUsed(s, k))
            log(Runner, "uses", s, "to", a.label)
            push(AccessDoneAction(c))
            pushAll(a.effects)
            NextAction

        case NoAccessAction(f, c) =>
            log(Runner, "takes no action on", cardElem(c))
            push(AccessDoneAction(c))
            NextAction

        case AccessDoneAction(c) =>
            push(AccessNextAction())
            NextAction

        case BreachEndAction() =>
            run.foreach { r =>
                r.breachServer = None
            }
            push(RunEndsAction())
            NextAction
    }
}
