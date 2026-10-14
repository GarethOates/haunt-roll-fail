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
// Generic mechanics added for the rotated hardware and identities (see STATUS-rot-hardware-identities.md):
// expose, recurring credits restricted to icebreakers / traces / advancing ice, facedown installs (Apex), mods hosted
// on icebreakers, programs hosted without memory cost, damage choice (Titanium Ribs, Chronos Protocol), automatic tag
// avoidance, additional rez costs / rez restrictions (Cortez Chip, EMP Device, Saraswati Mnemonics), agenda point
// requirement modifiers (Harmony Medtech, draft identities), "after accessing a card" abilities, larger starting hands.
//
// The interception part of RotHardwareRules (performRotHardware) runs first in Game.performInternal: for the actions it
// is interested in it performs the rest of the chain (rhInner) and adjusts the result (greys out / re-enables options of
// the returned asks) or prepares the state around it (credits usable for the breaker that is paying).
// ---------------------------------------------------------------------------------------------------------------------

// ---- events -----------------------------------------------------------------------------------------------------------
// (expose / facedown installs: rules-ext-rot-shared.scala; trace / psi results: TraceResolved / RotPsiRevealed)
case class RHTrashAbilityUsed(card : Card) extends Event                 // the Runner used a [trash] ability


// ---- effects ----------------------------------------------------------------------------------------------------------

// discard the chosen cards for damage (Titanium Ribs, Chronos Protocol), then the rest at random
case class RHDamageResolveEffect(kind : String, n : Int, picked : $[Card]) extends ForcedAction with SelfPerform {
    def perform(soft : Void)(implicit g : Game) = {
        val l = picked.%(c => g.zone(c) == HandZone(Runner))
        val rest = (n - l.num).atMost(g.hand(Runner).num - l.num)
        if (rest > 0)
            ShuffleTake[Card](g.hand(Runner).%!(l.has), rest, RHDamageRandomAction(kind, l, _), "Damage")
        else {
            g.push(RHDamageDiscardEffect(kind, l))
            NextAction
        }
    }
}

case class RHDamageRandomAction(kind : String, picked : $[Card], shuffled : $[Card]) extends ShuffledAction[Card] with SelfPerform {
    def perform(soft : Void)(implicit g : Game) = {
        g.push(RHDamageDiscardEffect(kind, picked ++ shuffled))
        NextAction
    }
}

case class RHDamageDiscardEffect(kind : String, cards : $[Card]) extends ForcedAction with SelfPerform {
    def perform(soft : Void)(implicit g : Game) = {
        val l = cards.%(c => g.zone(c) == HandZone(Runner))
        l.foreach(c => g.moveTo(c, DiscardZone(Runner)))
        g.lastDamaged = l
        g.log(Runner, "trashes", l.num.cards, "from the grip", "(" ~ l./(g.cardElem).comma ~ ")")
        l.foreach(x => g.pushAll(g.impl(x).onTrash(x, Corp)))
        g.push(CheckWinAction)
        g.trigger(DamageDone(kind, l.num))
        NextAction
    }
}

// damage applied without the damage-choice interception
case class RHDamageApplyNowEffect(kind : String, n : Int) extends ForcedAction with SelfPerform {
    def perform(soft : Void)(implicit g : Game) = g.rhInnerPerform(DamageApplyEffect(kind, n))
}

// trashed cards hosted on a card (BMI Buffer): host the card without installing it
case class RHStashFromHeapEffect(card : Card, host : Card) extends ForcedAction with SelfPerform {
    def perform(soft : Void)(implicit g : Game) = {
        if (g.zone(card) == DiscardZone(Runner) && g.isInstalled(host)) {
            g.moveTo(card, StashZone(host))
            g.state(card).seen = true
            g.log(card, "is hosted on", host)
        }
        NextAction
    }
}

// remove virus counters from the Runner's cards (largest piles first)
case class RHSpendVirusEffect(n : Int) extends ForcedAction with SelfPerform {
    def perform(soft : Void)(implicit g : Game) = {
        var left = n
        g.installedCards(Runner).%(c => g.counter(c, "virus") > 0).sortBy(c => -g.counter(c, "virus")).foreach { c =>
            val m = left.atMost(g.counter(c, "virus"))
            if (m > 0) {
                g.addCounter(c, "virus", -m)
                left -= m
                g.log(m.hl, "virus counter" + (m != 1).??("s"), "removed from", c)
            }
        }
        NextAction
    }
}

// remove all copies of a card title owned by the Runner from the game (Chronos Protocol)
case class RHRemoveAllCopiesEffect(code : String) extends ForcedAction with SelfPerform {
    def perform(soft : Void)(implicit g : Game) = {
        val title = Cards(code).title
        val l = g.location.keys.toList.%(c => c.title == title && g.owner(c).isRunner && g.zone(c) != RemovedZone(Runner))
        l.foreach { c =>
            if (g.isInstalled(c))
                g.leavePlay(c)
            g.moveTo(c, RemovedZone(Runner))
        }
        if (l.any)
            g.log("All copies of", Cards(code).title.hl, "are removed from the game (" ~ l.num.hl ~ ")")
        NextAction
    }
}

// swap two agendas between the score areas (Turntable)
case class RHSwapAgendasEffect(a : Card, b : Card) extends ForcedAction with SelfPerform {
    def perform(soft : Void)(implicit g : Game) = {
        (g.zone(a), g.zone(b)) match {
            case (ScoredZone(fa), ScoredZone(fb)) if fa != fb =>
                g.moveTo(a, ScoredZone(fb))
                g.moveTo(b, ScoredZone(fa))
                g.state(a).seen = true
                g.state(b).seen = true
                g.log("Swapped", a, "and", b, "between the score areas")
                g.push(CheckWinAction)
            case _ =>
        }
        NextAction
    }
}

// install ice from HQ at the innermost position of a server, ignoring all costs (Mti Mwekundu)
case class RHInstallInnermostEffect(card : Card, server : Server) extends ForcedAction with SelfPerform {
    def perform(soft : Void)(implicit g : Game) = {
        if (g.zone(card) == HandZone(Corp) && g.servers.has(server)) {
            g.moveTo(card, IceZone(server))
            g.zones += IceZone(server) -> (card +: g.ice(server).but(card))
            g.state(card).installedTurn = g.turn
            g.state(card).rezzed = false
            g.regInc("installed")
            g.log(Corp, "installs ice at the innermost position protecting", server)
            g.trigger(Installed(Corp, card))
        }
        NextAction
    }
}


// ---- generic purposes (CardPurposes of an RH card, resolved by RotHardwareExt) ------------------------------------------
trait RHPurpose extends CardPurpose

case class RHDamagePickPurpose(card : Card, kind : String, n : Int) extends RHPurpose     // the chosen grip cards are trashed by damage
case class RHTrashProgramPreventPurpose(card : Card, kind : String, n : Int) extends RHPurpose  // trash a program from the grip, prevent 1 damage
case class RHAddCounterPurpose(card : Card, kind : String, n : Int) extends RHPurpose     // place n counters of kind on the chosen card
case class RHAdvancePurpose(card : Card, n : Int) extends RHPurpose                        // place n advancement counters on the chosen card
case class RHPumpRunPurpose(card : Card, n : Int) extends RHPurpose                        // the chosen icebreakers get +n strength for the run
case class RHToTopPurpose(card : Card) extends RHPurpose                                   // add the chosen card to the top of its owner's deck
case class RHShuffleIntoPurpose(card : Card) extends RHPurpose                             // shuffle the chosen cards into their owner's deck
case class RHSearchToHandPurpose(card : Card) extends RHPurpose                            // add the chosen card from the deck to hand and shuffle


// ---------------------------------------------------------------------------------------------------------------------
// Base class of the rotated hardware / identity implementations: Startup helpers + the hooks read by RotHardwareRules
// ---------------------------------------------------------------------------------------------------------------------

abstract class RH(code : String) extends Startup(code) {
    // recurring credits: maximum hosted credits (refilled when the owner's turn begins)
    def recurring(self : Card)(implicit g : Game) : Int = 0
    // hosted credits usable to pay for using this breaker (Toolbox, Lockpick, Silencer, Omni-drive, ...)
    def breakerCredits(self : Card, breaker : Card)(implicit g : Game) : Boolean = false
    // programs hosted on this card (canHost) do not use memory: the hosting option is offered even without free MU
    def hostsWithoutMU(self : Card)(implicit g : Game) : Boolean = false
    // a hardware mod that must be installed hosted on one of these cards (None = installed normally)
    def modHosts(self : Card)(implicit g : Game) : |[$[Card]] = None
    // Runner cards that cannot be installed (Apex)
    def blocksInstall(self : Card, card : Card)(implicit g : Game) : Boolean = false
    // modifier of the agenda points f needs to win
    def winPointsMod(self : Card, f : Faction)(implicit g : Game) : Int = 0
    // extra credits the Runner pays to remove a tag with the basic action / modifier of the Corp's resource trash cost (SYNC)
    def removeTagExtraCost(self : Card)(implicit g : Game) : Int = 0
    def trashResourceCostMod(self : Card)(implicit g : Game) : Int = 0
    // who chooses the cards trashed by damage: (chooser, number of cards chosen), optional for the Corp
    def damageChoice(self : Card, kind : String, n : Int)(implicit g : Game) : |[(Faction, Int)] = None
    // tags automatically avoided (mutates the card's bookkeeping), returns the number avoided
    def avoidTags(self : Card, n : Int)(implicit g : Game) : Int = 0
    // effects after the Runner finished accessing a card (it may have been stolen / trashed)
    def afterAccess(self : Card, card : Card)(implicit g : Game) : $[ForcedAction] = $
    // additional cards in the starting hand (also after a mulligan)
    def startingHandBonus(self : Card)(implicit g : Game) : Int = 0
    // additional rez cost / rez restriction imposed by this card
    def rezSurcharge(self : Card, card : Card)(implicit g : Game) : Int = 0

    // prevention abilities, offered only while there is something left to prevent
    def prevent(self : Card, kind : String, n : Int)(implicit g : Game) : $[Ability] = $
    final override def preventions(self : Card, kind : String, n : Int)(implicit g : Game) : $[Ability] = (n > 0).??(prevent(self, kind, n))

    override def creditsUsableForRun(self : Card)(implicit g : Game) : Boolean = g.rhPayingFor.exists(b => breakerCredits(self, b))
    override def initialCounters(self : Card)(implicit g : Game) : Map[String, Int] = (recurring(self) > 0).?(Map[String, Int]("credit" -> recurring(self))).|(Map[String, Int]())

    final override def performCard(a : CardAction)(implicit g : Game) : Continue = RotHardwareExt.perform(this, a).|(performRH(a))
    def performRH(a : CardAction)(implicit g : Game) : Continue = throw new Error("unhandled card action " + a)

    final override def chosenCard(p : CardPurpose, cards : $[Card])(implicit g : Game) : Continue = p match {
        case p : RHPurpose => RotHardwareExt.chosen(this, p, cards)
        case _ => chosenRH(p, cards)
    }
    def chosenRH(p : CardPurpose, cards : $[Card])(implicit g : Game) : Continue = throw new Error("unhandled card purpose " + p)

    // ---- helpers ---------------------------------------------------------------------------------------------------
    def shortTitle : String = title.split(':').head.toList.filter(ch => ch < 128 && ch != '"').mkString.trim
    // log line naming the card whose ability resolves ("Corp uses Blue Sun to ...")
    def says(self : Card, text : String)(implicit g : Game) : ForcedAction = MessageEffect(g.owner(self), "uses " + shortTitle + " to " + text)
    def installedThisTurnBy(self : Card)(implicit g : Game) = g.state(self).installedTurn == g.turn
    def firstTime(self : Card, key : String)(implicit g : Game) = !g.usedThisTurn(self, key)
    def unrezzedInstalled(implicit g : Game) : $[Card] = g.installedCards(Corp).%(c => !g.isRezzed(c))
    def icebreakers(implicit g : Game) : $[Card] = g.installedCards(Runner).%(c => g.isActive(c) && g.isIcebreaker(c))
    def virusPrograms(implicit g : Game) : $[Card] = g.installedCards(Runner).%(c => g.isActive(c) && c.data.isProgram && g.has(c, "Virus"))
    def hostOf(self : Card)(implicit g : Game) : |[Card] = g.state(self).host
    def hostedPrograms(self : Card)(implicit g : Game) : $[Card] = g.hosted(self).%(_.data.isProgram)
    def hostedMU(self : Card)(implicit g : Game) : Int = hostedPrograms(self)./(_.data.memory.|(0)).sum
    def asciiTitle(c : Card) : String = c.title.toList.map(ch => if (ch < 128 && ch != '"') ch else '?').mkString
    def expose(candidates : $[Card], prompt : String, optional : Boolean, self : Card) : $[ForcedAction] =
        candidates.any.$(ChooseCardEffect(Runner, candidates, ExposePurpose, prompt, optional))
    def mostFaction(f : Faction, faction : String)(implicit g : Game) = g.rhHasMostFaction(f, faction)
    def recurringRefill(self : Card, e : Event, f : Faction)(implicit g : Game) : $[ForcedAction] = e match {
        case TurnBegins(ff) if ff == f && recurring(self) > 0 => $(RefillCreditsEffect(self, recurring(self)))
        case _ => $
    }
}


object RotHardwareExt {
    def perform(impl : RH, a : CardAction)(implicit g : Game) : |[Continue] = None

    def chosen(impl : RH, p : RHPurpose, cards : $[Card])(implicit g : Game) : Continue = {
        p match {
            case RHDamagePickPurpose(_, kind, n) =>
                g.log(g.owner(p.card).isCorp.?(Corp).|(Runner), "chooses", cards.num.cards, "to trash")
                g.push(RHDamageResolveEffect(kind, n, cards))

            case RHTrashProgramPreventPurpose(self, kind, n) =>
                cards.foreach { c =>
                    g.moveTo(c, DiscardZone(Runner))
                    g.log(Runner, "trashes", c, "from the grip")
                }
                g.push(PreventWindowAction(kind, n - 1))

            case RHAddCounterPurpose(_, kind, n) =>
                cards.foreach(c => g.push(AddCounterEffect(c, kind, n)))

            case RHAdvancePurpose(_, n) =>
                cards.foreach(c => g.push(PlaceAdvancementEffect(c, n)))

            case RHPumpRunPurpose(_, n) =>
                cards.foreach(c => g.push(PumpBreakerEffect(c, n, RunDuration)))

            case RHToTopPurpose(_) =>
                cards.foreach { c =>
                    val o = g.owner(c)
                    g.moveTo(c, DeckZone(o), true)
                    g.log((o.isCorp && !g.state(c).seen).?("A card".hl).|(g.cardElem(c)), "is added to the top of", o.isCorp.?("R&D").|("the stack"))
                }

            case RHShuffleIntoPurpose(_) =>
                cards.foreach { c =>
                    g.moveTo(c, DeckZone(g.owner(c)))
                    g.log((g.owner(c).isCorp && !g.state(c).seen).?("A card".hl).|(g.cardElem(c)), "is shuffled into", g.owner(c).isCorp.?("R&D").|("the stack"))
                }
                cards./(g.owner).distinct.foreach(f => g.push(ShuffleDeckEffect(f)))

            case RHSearchToHandPurpose(self) =>
                cards.foreach { c =>
                    val o = g.owner(c)
                    g.log(o, "reveals", c, "and adds it to", o.isCorp.?("HQ").|("the grip"))
                    g.moveTo(c, HandZone(o))
                }
        }
        NextAction
    }
}


// ---------------------------------------------------------------------------------------------------------------------
// Rules
// ---------------------------------------------------------------------------------------------------------------------

trait RotHardwareRules { game : Game =>

    var rhInner = false                         // performing the rest of the chain from performRotHardware
    var rhPayingFor : |[Card] = None            // the icebreaker whose ability is being paid for
    var rhInstallFrom : Map[Card, Zone] = Map() // the zone a Runner card was last installed from

    // perform the action with the rest of the chain (the interceptions below are skipped)
    def rhInnerPerform(a : Action) : Continue = {
        val was = rhInner
        rhInner = true
        try performInternal(a, NoVoid) finally rhInner = was
    }

    def rhCards : $[(Card, RH)] = allActive./~(c => impl(c) match {
        case r : RH => |((c, r))
        case _ => None
    })

    def rhRunnerCards : $[(Card, RH)] = activeCards(Runner)./~(c => impl(c) match {
        case r : RH => |((c, r))
        case _ => None
    })

    // "if you have more [faction] cards installed (Runner) / rezzed (Corp) than any other faction" (draft identities)
    def rhHasMostFaction(f : Faction, faction : String) : Boolean = {
        val l = installedCards(f).%(c => owner(c) == f && isActive(c))./(_.data.faction)
        val mine = l.count(_ == faction)
        mine > 0 && l.distinct.%(_ != faction).forall(x => l.count(_ == x) < mine)
    }

    // ---- hosting ------------------------------------------------------------------------------------------------------
    def rhModHosts(c : Card) : |[$[Card]] = CardImpls(c.code) match {
        case r : RH => r.modHosts(c)
        case _ => None
    }

    def rhInstallBlocked(c : Card) : Boolean = rhRunnerCards.exists { case (x, r) => r.blocksInstall(x, c) }

    // ---- recurring credits for icebreakers ----------------------------------------------------------------------------
    def rhBreakerSources : $[Card] = rhRunnerCards.%{ case (c, r) => counter(c, "credit") > 0 && icebreakerCreditCard(c, r) }.map(_._1)
    private def icebreakerCreditCard(c : Card, r : RH) = installedCards(Runner).exists(b => r.breakerCredits(c, b)) || identity.get(Runner).exists(b => r.breakerCredits(c, b))

    def rhWithPaying[T](b : Card)(f : => T) : T = {
        val was = rhPayingFor
        rhPayingFor = |(b)
        try f finally rhPayingFor = was
    }

    // re-enable break / pump options greyed out for lack of credits that become payable with credits usable for the breaker
    def rhFixBreakerAsk(l : $[UserAction]) : $[UserAction] = l./{
        case u @ UnavailableReasonAction(x : BreakSubAction, "cannot pay") =>
            rhWithPaying(x.breaker)(canPay(Runner, impl(x.breaker).breaks(x.breaker).lift(x.bi)./(_.costs).|($), |(x.breaker))).?(x : UserAction).|(u)
        case u @ UnavailableReasonAction(x : BreakSubsAction, "cannot pay") =>
            rhWithPaying(x.breaker)(impl(x.breaker).breaks(x.breaker).lift(x.bi).exists(br => canPay(Runner, breakCosts(br, x.subs.num), |(x.breaker)))).?(x : UserAction).|(u)
        case u @ UnavailableReasonAction(x : PumpAction, "cannot pay") =>
            rhWithPaying(x.breaker)(impl(x.breaker).pumps(x.breaker).lift(x.pi).exists(p => canPay(Runner, p.costs, |(x.breaker)))).?(x : UserAction).|(u)
        case u @ UnavailableReasonAction(x : UseAbilityAction, "cannot pay") if x.self.isRunner && isIcebreaker(x.card) =>
            rhWithPaying(x.card)(impl(x.card).abilities(x.card).lift(x.idx).exists(a => canPay(Runner, a.costs, |(x.card)))).?(x : UserAction).|(u)
        case a => a
    }

    // ---- rez costs / restrictions ---------------------------------------------------------------------------------------
    def rhRezSurcharge(c : Card) : Int = rhCards./{ case (x, r) => r.rezSurcharge(x, c) }.sum + flagsOf("rh-rez-surcharge").%(_.card.has(c)).num * 2
    def rhRezBlocked(c : Card) : Boolean = flagsOf("rh-no-rez").exists(_.card.has(c)) || (c.data.isIce && hasFlag("rh-emp-used"))
    def rhRezRestrictions : Boolean = hasFlag("rh-rez-surcharge") || hasFlag("rh-no-rez") || hasFlag("rh-emp-used")

    def rhFixRez(l : $[UserAction]) : $[UserAction] = l./{
        case a @ RezAction(f, c, ctx) if rhRezBlocked(c) => a.!(true, "cannot rez")
        case a @ RezAction(f, c, ctx) if rhRezSurcharge(c) > 0 => a.!(credits(Corp) < rezCost(c) + rhRezSurcharge(c), "cannot pay the additional cost")
        case a => a
    }

    // ---- asks ---------------------------------------------------------------------------------------------------------
    def rhMapAsk(c : Continue)(f : $[UserAction] => $[UserAction]) : Continue = c match {
        case Ask(x, l) => Ask(x, f(l))
        case MultiAsk(asks, p) => MultiAsk(asks./(a => Ask(a.faction, f(a.actions))), p)
        case c => c
    }

    // modifiers of the same kind do not stack (two draft identities: 6 points), others add up
    def rhWinPoints(f : Faction) : Int = {
        val l = rhCards./{ case (x, r) => r.winPointsMod(x, f) }
        pointsToWin + l.%(_ < 0).minOption.getOrElse(0) + l.%(_ > 0).sum
    }

    def rhSync(k : String) : Int = rhCards./{ case (x, r) => (k == "tag").?(r.removeTagExtraCost(x)).|(r.trashResourceCostMod(x)) }.sum

    def rhCreditSources(purpose : String) : $[Card] = rhCards.%{ case (x, r) => counter(x, "credit") > 0 && r.creditsUsableFor(x, purpose) }.map(_._1)

    // hosted credits usable for traces: rotated hardware / identities (Making News) and assets (Net Police, Primary Transmission Dish)
    def rhTraceSources : $[Card] = (rhCreditSources("trace") ++ rotRecurring("trace", Card("", 0))).distinct

    def rhDamageChoice(kind : String, n : Int) : |[(Card, Faction, Int)] = rhCards./~{ case (x, r) => r.damageChoice(x, kind, n)./{ case (f, k) => (x, f, k) } }.starting

    // ---- the interception -----------------------------------------------------------------------------------------------
    def rhWants(a : Action) : Boolean = a match {
        case EncounterAction(_) => rhBreakerSources.any || rhRezRestrictions
        case BreakSubAction(_, _, _, _, _) | BreakSubsAction(_, _, _, _, _) | PumpAction(_, _, _) => rhBreakerSources.any
        case UseAbilityAction(f, c, _, _) => f.isRunner
        case PreventUseAction(f, c, _, _, _) => f.isRunner
        case InstallWhereAction(Runner, c) => rhModHosts(c).any || (c.data.isProgram && rhRunnerCards.exists { case (h, r) => r.hostsWithoutMU(h) && r.canHost(h, c) })
        case InstallMenuAction(Runner) => true
        case RunnerInstallEffect(_, _, _) => true
        case RezAction(_, c, _) => rhRezBlocked(c) || rhRezSurcharge(c) > 0
        case RezEffect(c, _) => rhRezBlocked(c) || rhRezSurcharge(c) > 0
        case ActionPhaseAction(_) | PaidWindowAction(_) | RezMenuAction(_) => rhRezRestrictions || rhSync("tag") != 0
        case CheckWinAction => rhCards.exists { case (x, r) => r.winPointsMod(x, Corp) != 0 || r.winPointsMod(x, Runner) != 0 }
        case RemoveTagAction(_) | TrashResourceAction(_, _) => rhSync("tag") != 0 || rhSync("resource") != 0
        case AdvanceAction(_, c) => c.data.isIce && rhCreditSources("advance-ice").any
        case TraceCorpBoostAction(_, _, _) | TraceCorpAction(_, _) => rhTraceSources.any
        case DamageApplyEffect(kind, n) => n > 0 && n <= hand(Runner).num && rhDamageChoice(kind, n).any
        case TagsEffect(n) => n > 0 && rhCards.any
        case AccessDoneAction(c) => rhCards.any
        case MulliganAction(_) => true
        case _ => false
    }

    def performRotHardware : PartialFunction[Action, Continue] = {
        case a if !rhInner && rhWants(a) => rhIntercept(a)
    }

    def rhIntercept(a : Action) : Continue = a match {
        case EncounterAction(_) =>
            rhMapAsk(rhInnerPerform(a))(l => rhFixRez(rhFixBreakerAsk(l)))

        case BreakSubAction(_, b, _, _, _) => rhWithPaying(b)(rhInnerPerform(a))
        case BreakSubsAction(_, b, _, _, _) => rhWithPaying(b)(rhInnerPerform(a))
        case PumpAction(_, b, _) => rhWithPaying(b)(rhInnerPerform(a))

        case UseAbilityAction(f, c, i, _) =>
            val trash = impl(c).abilities(c).lift(i).exists(_.costs.has(TrashSelfCost))
            val r = if (isIcebreaker(c)) rhWithPaying(c)(rhInnerPerform(a)) else rhInnerPerform(a)
            if (trash)
                trigger(RHTrashAbilityUsed(c))
            r

        case PreventUseAction(f, c, i, kind, n) =>
            val trash = impl(c).preventions(c, kind, n).lift(i).exists(_.costs.has(TrashSelfCost))
            val r = rhInnerPerform(a)
            if (trash)
                trigger(RHTrashAbilityUsed(c))
            r

        case InstallWhereAction(f, c) =>
            rhModHosts(c) match {
                case Some(hosts) =>
                    val cost = installCost(c, None)
                    Ask(f).add(hosts./(h => RunnerInstallAction(f, c, |(h), None).!(credits(f) < cost, "not enough credits"))).cancel
                case None =>
                    val cost = installCost(c, None)
                    val extra = rhRunnerCards.%{ case (h, r) => r.hostsWithoutMU(h) && r.canHost(h, c) }.map(_._1)
                    rhMapAsk(rhInnerPerform(a)) { l =>
                        val present = l./~{
                            case RunnerInstallAction(_, _, h, _) => h
                            case UnavailableReasonAction(RunnerInstallAction(_, _, h, _), _) => h
                            case _ => None
                        }
                        val add = extra.%!(present.has)./(h => RunnerInstallAction(f, c, |(h), None).!(credits(f) < cost, "not enough credits"))
                        val (cancel, rest) = l.partition(_ == CancelAction)
                        rest ++ add ++ cancel
                    }
            }

        case InstallMenuAction(f) =>
            rhMapAsk(rhInnerPerform(a))(_./{
                case x @ InstallWhereAction(_, c) if rhInstallBlocked(c) => x.!(true, "cannot install")
                case x @ InstallWhereAction(_, c) if rhModHosts(c).exists(_.none) => x.!(true, "no host")
                case x => x
            })

        case RunnerInstallEffect(c, _, _) =>
            if (!location.get(c).exists(inPlayZone))
                rhInstallFrom += c -> zone(c)
            rhInnerPerform(a)

        case RezAction(f, c, ctx) =>
            if (rhRezBlocked(c)) {
                log(c, "cannot be rezzed")
                reask(ctx)
            }
            else {
                val s = rhRezSurcharge(c)
                if (credits(Corp) < rezCost(c) + s) {
                    log(Corp, "cannot pay the additional cost to rez", c)
                    reask(ctx)
                }
                else {
                    credits += Corp -> (credits(Corp) - s)
                    if (s > 0)
                        log(Corp, "pays", s.credits, "as an additional cost")
                    rhInnerPerform(a)
                }
            }

        case RezEffect(c, ignore) =>
            if (rhRezBlocked(c)) {
                log(c, "cannot be rezzed")
                NextAction
            }
            else {
                val s = rhRezSurcharge(c)
                if (credits(Corp) < (!ignore).??(rezCost(c)) + s) {
                    log(Corp, "cannot pay the additional cost to rez", c)
                    NextAction
                }
                else {
                    credits += Corp -> (credits(Corp) - s)
                    if (s > 0)
                        log(Corp, "pays", s.credits, "as an additional cost")
                    rhInnerPerform(a)
                }
            }

        case ActionPhaseAction(_) | PaidWindowAction(_) | RezMenuAction(_) =>
            val extra = rhSync("tag")
            rhMapAsk(rhInnerPerform(a))(l => rhFixRez(l)./{
                case x @ RemoveTagAction(f) if extra > 0 => x.!(credits(f) < 2 + extra, "not enough credits")
                case x => x
            })

        case CheckWinAction =>
            scoringAgenda = None
            if (isOver)
                IdleAction
            else if (agendaPoints(Corp) >= rhWinPoints(Corp))
                GameOverAction(|(Corp), "Corp scored " + agendaPoints(Corp) + " agenda points")
            else if (agendaPoints(Runner) >= rhWinPoints(Runner))
                GameOverAction(|(Runner), "Runner stole " + agendaPoints(Runner) + " agenda points")
            else
                NextAction

        case RemoveTagAction(f) =>
            val extra = rhSync("tag")
            if (credits(f) < 2 + extra) {
                log(f, "cannot pay to remove a tag")
                ActionPhaseAction(f)
            }
            else {
                if (extra > 0) {
                    credits += f -> (credits(f) - extra)
                    log(f, "pays", extra.credits, "more (SYNC)")
                }
                rhInnerPerform(a)
            }

        case TrashResourceAction(f, c) =>
            val mod = rhSync("resource")
            val r = rhInnerPerform(a)
            if (mod != 0) {
                val back = (-mod).atMost(2).atLeast(0)
                credits += f -> (credits(f) + back)
                if (back > 0)
                    log(f, "pays", back.credits, "less (SYNC)")
            }
            r

        case AdvanceAction(f, c) =>
            rhCreditSources("advance-ice").starting.foreach { s =>
                addCounter(s, "credit", -1)
                credits += Corp -> (credits(Corp) + 1)
                log(Corp, "uses 1 hosted credit from", s)
            }
            rhInnerPerform(a)

        case TraceCorpBoostAction(base, success, fail) =>
            val hosted = rhTraceSources./(counter(_, "credit")).sum
            Ask(Corp).group("Trace strength " ~ base.hl ~ ", boost by paying credits (" ~ hosted.hl ~ " hosted credits usable)").add(0.to(credits(Corp) + hosted).$./(n => TraceCorpAction(Corp, n)))

        case TraceCorpAction(f, n) =>
            var left = n
            rhTraceSources.foreach { s =>
                val m = left.atMost(counter(s, "credit"))
                if (m > 0) {
                    addCounter(s, "credit", -m)
                    credits += Corp -> (credits(Corp) + m)
                    left -= m
                    log(Corp, "uses", m.credits, "hosted on", s)
                }
            }
            rhInnerPerform(a)

        case DamageApplyEffect(kind, n) =>
            rhDamageChoice(kind, n) match {
                case Some((x, Runner, k)) =>
                    log(Runner, "suffers", n.hl, kind, "damage")
                    runnerDamageThisTurn += n
                    if (kind == "core")
                        coreDamage += n
                    regInc("rh-damage-" + kind)
                    ChooseCardsEffect(Runner, hand(Runner), k.atMost(n), k.atMost(n), RHDamagePickPurpose(x, kind, n), "Choose " + k.atMost(n) + " card" + (k != 1).??("s") + " to trash for the " + kind + " damage")
                case Some((x, Corp, k)) =>
                    regInc("rh-damage-" + kind)
                    OptionalEffect(Corp, "Look at the grip and choose the card trashed by the " + kind + " damage?", $(RHChronosStartEffect(x, kind, n)), $(RHDamageApplyNowEffect(kind, n)))
                case _ => rhInnerPerform(a)
            }

        case TagsEffect(n) =>
            var left = n
            rhCards.foreach { case (x, r) =>
                if (left > 0) {
                    val m = r.avoidTags(x, left).atMost(left)
                    if (m > 0) {
                        left -= m
                        log(Runner, "avoids", m.tags, "(" ~ cardElem(x) ~ ")")
                    }
                }
            }
            if (left > 0)
                rhInnerPerform(TagsEffect(left))
            else
                NextAction

        case AccessDoneAction(c) =>
            val effects = rhCards./~{ case (x, r) => r.afterAccess(x, c) }
            val r = rhInnerPerform(a)
            pushAll(effects)
            r

        case MulliganAction(f) =>
            val bonus = identity.get(f)./(id => impl(id) match { case r : RH => r.startingHandBonus(id) ; case _ => 0 }).|(0)
            val r = rhInnerPerform(a)
            if (bonus > 0) {
                val i = pending.indexWhere(_ == DrawEffect(f, 5))
                if (i >= 0)
                    pending = pending.take(i + 1) ++ $(DrawEffect(f, bonus)) ++ pending.drop(i + 1)
            }
            r

        case a => rhInnerPerform(a)
    }

    // ---- game-level triggers (recurring credit refills, EMP Device) -----------------------------------------------------
    def rotHardwareTriggers(e : Event) : $[ForcedAction] = e match {
        case TurnBegins(f) =>
            activeCards(f)./~(c => impl(c) match {
                case r : RH if r.recurring(c) > 0 && counter(c, "credit") < r.recurring(c) => $(RefillCreditsEffect(c, r.recurring(c)))
                case _ => $
            })
        case Rezzed(c) if c.data.isIce && hasFlag("rh-emp") && !hasFlag("rh-emp-used") =>
            $(AddFlagEffect(runFlag("rh-emp-used", None)))
        case _ => $
    }
}

// the Corp chooses the first card trashed by net damage (Chronos Protocol: Selective Mind-mapping)
case class RHChronosStartEffect(card : Card, kind : String, n : Int) extends ForcedAction with SelfPerform {
    def perform(soft : Void)(implicit g : Game) = {
        g.log(Runner, "suffers", n.hl, kind, "damage")
        g.runnerDamageThisTurn += n
        if (kind == "core")
            g.coreDamage += n
        g.log(Corp, "looks at the grip:", g.hand(Runner)./(g.cardElem).comma)
        g.push(ChooseCardEffect(Corp, g.hand(Runner), RHDamagePickPurpose(card, kind, n), "Choose the card trashed by the damage", false))
        NextAction
    }
}
