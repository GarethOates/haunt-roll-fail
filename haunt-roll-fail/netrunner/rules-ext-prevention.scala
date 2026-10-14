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


// ---------------------------------------------------------------------------------------------------------------------
// Prevention / interrupt windows (NSG timing: "when X would happen" interrupts and prevention abilities), modelled on
// jinteki's game.core.prevention. See ENGINE.md section 8.2 and STATUS-fix-prevention.md.
//
// A window is a PreventCtx on a stack. It is opened when something preventable would happen:
//   damage   DamageEffect (after the rules below have adjusted the amount) -> PreventOpenDamageAction
//   tag      TagsEffect (after the automatic avoidance of the hardware rules)
//   bad-pub  BadPubEffect with n > 0
//   end-run  EndRunEffect during a run (the source subroutine, if any, is known: preventSubs)
//   jack-out JackOutAction / JackOutEffect when a Corp card can prevent it
//   trash    trashCard of an installed Runner card (not as a cost nor by the game rules) when a card can prevent it;
//            several trashes of one effect are batched into one window
//   encounter the "when encountered" ability of a piece of ice (EncounterAbilityEffect)
// Opening the window first resolves the mandatory interrupts: the event PreventionInterrupt(kind, stage, n) is triggered
// (stage 0: increases such as The Cleaners / Defective Brainchips, stage 1: constant or forced preventions such as
// Leverage, The Noble Path, Paparazzi, Muresh Bodysuit, Shred, Broadcast Square). Then the players act with priority,
// the active player first: a player keeps using prevention abilities (CardImpl.preventions(self, kind, n), n = the
// amount left) until they pass; the window closes when both players passed in a row or nothing is left to prevent.
// Prevention abilities change the context with PreventEffect / PreventTrashOfEffect / ... and the engine returns to the
// window after their effects; an ability can be used any number of times (a card whose text limits it checks
// g.preventUses(self) or the window's firstThisTurn / firstThisRun flags; a safety cap of 20 uses per window applies). The kind passed to `preventions` is "net" / "meat" / "core"
// for damage, otherwise the window kind.
// ---------------------------------------------------------------------------------------------------------------------

class PreventCtx(val kind : String) {
    var dtype = ""                              // damage: "net" / "meat" / "core"
    var count = 0                               // initial amount
    var remaining = 0                           // amount left (numeric windows)
    var prevented = 0
    var all = false                             // everything was prevented
    var cards : $[Card] = $                     // trash: the cards that would be trashed
    var by : Faction = Corp                     // the side doing it
    var source : |[Card] = None                 // end-run: the ice whose subroutine ends the run; encounter: the ice
    var fromSub = false                         // end-run: ended by a subroutine
    var firstThisTurn = false                   // first window of this kind (damage: of this damage type) this turn
    var firstThisRun = false                    // first window of this kind (damage: of this damage type) this run
    var unpreventable = false
    var uses : Map[(Card, Int), Int] = Map()    // prevention abilities used in this window
    var passes = 0                              // consecutive passes
    var side : Faction = Corp                   // the side with priority
    var resume : $[ForcedAction] = $            // what happens after the window when it was not prevented (jack out)
    var resumePrevented : $[ForcedAction] = $   // what happens after the window when it was prevented (jack out: back to the window)
    var subs : $[Int] = $                       // break: the subroutines that would be broken
    var preventedSubs : $[Int] = $              // break: the subroutines prevented from being broken
    var action : |[Action] = None               // break: the break action to perform after the window
    def q : String = (kind == "damage").?(dtype).|(kind)
    def amount : Int = kind match {
        case "trash" => cards.num
        case "break" => subs.num
        case _ => remaining
    }
}

// ---- events -----------------------------------------------------------------------------------------------------------
case class PreventionInterrupt(kind : String, stage : Int, n : Int) extends Event   // something preventable would happen (kind: net/meat/core/tag/bad-pub/end-run/jack-out/trash/encounter); stage 0 = increases, 1 = forced preventions

case class ClicksSpentOrLost(f : Faction, n : Int) extends Event                   // f spent (costs) or lost (effects) clicks (Heinlein Grid)
case class AccessFinished(card : Card) extends Event                               // the Runner finished accessing the card (it may have been stolen / trashed): Aeneas Informant
case class BreachFinished(server : Server) extends Event                           // the breach of a server of a run finished, before the run ends (Divide and Conquer)
case class RunWouldSucceed(server : Server) extends Event                          // the run is about to be declared successful (Sneakdoor Beta / Prime)

// ---- effects ----------------------------------------------------------------------------------------------------------
case class PreventOpenDamageAction(kind : String, n : Int) extends ForcedAction        // open the damage window (from DamageEffect)
case class PreventInterruptAction(kind : String, stage : Int) extends ForcedAction     // trigger PreventionInterrupt for the open window
case class PreventStepAction(kind : String) extends ForcedAction with Reaskable        // the window: the side with priority acts or passes
case class PreventEffect(kind : String, n : Int) extends ForcedAction                  // prevent n (n < 0: all) of the open window of that kind
case class PreventBoostEffect(kind : String, n : Int) extends ForcedAction             // increase the pending amount (The Cleaners, Defective Brainchips)
case class PreventConvertDamageEffect(dtype : String, n : Int) extends ForcedAction    // the pending damage becomes n damage of type dtype (Tori Hanzo)
case class PreventTrashOfEffect(card : Card) extends ForcedAction                      // the card is not trashed
case class PreventTrashChooseEffect(self : Card, kinds : $[String]) extends ForcedAction   // the Runner chooses one of the cards (of the card types, empty = any) to save
case class TrashWindowEffect(cards : $[Card], by : Faction, cause : String) extends ForcedAction   // installed Runner cards would be trashed
case class BadPubNowEffect(n : Int) extends ForcedAction                               // take bad publicity after the window
case class JackOutNowAction(self : Faction) extends ForcedAction                       // jack out after the window
case object JackOutNowEffect extends ForcedAction                                      // JackOutEffect after the window
case class EncounterAbilityEffect(ice : Card) extends ForcedAction                     // the encounter ability of the ice would resolve
case class SubFinishedEffect(ice : Card) extends ForcedAction                          // a subroutine of the ice finished resolving
case class LogEffect(f : |[Faction], text : String) extends ForcedAction               // a plain log line
case class PreventBreakChooseEffect(self : Card) extends ForcedAction                  // the Corp prevents one of the subroutines of the break window from being broken
case class PreventBreakOfEffect(sub : Int) extends ForcedAction                        // the subroutine is not broken
case object PatchworkResumeEffect extends ForcedAction                                 // play / install the card after the Patchwork interrupt
case class PersistentEffect(card : Card, server : Server) extends ForcedAction         // the trashed card's run-end ability lasts for the remainder of the run (AMAZE Amusements)
case class SourcedDamageEffect(kind : String, n : Int, source : Card) extends ForcedAction   // damage done by a card (the "when you install" damage of Runner cards: Chrome Parlor)
case class BreachBonusEffect(n : Int) extends ForcedAction                             // the first breach of HQ or R&D this turn accesses n additional cards (Climactic Showdown)

// ---- purposes ---------------------------------------------------------------------------------------------------------
case object PreventTrashPurpose extends Purpose                                        // the chosen card is not trashed
case object CostTrashPurpose extends Purpose                                           // the chosen card is trashed as a cost
case class PrevDummyBoxPurpose(card : Card, kind : String) extends CardPurpose         // Dummy Box: the grip card to trash
case class PrevPatchworkPurpose(patchwork : Card) extends Purpose                     // Patchwork: the grip card to trash (2 less for the play / install)


trait PreventionRules extends EffectRules with RunRules with AbilityRules with TurnRules with CardRules with ManualRules with AccessRules { game : Game =>

    var preventStack : $[PreventCtx] = $
    var preventSubs : $[Card] = $                // ice whose subroutine is resolving (innermost first)
    var trashCause = ""                         // "cost" / "game" while trashing as a cost or by the game rules
    var trashBypass = false                     // trashing after the trash window
    var jackOutBypass = false
    var preventRunRef : |[Run] = None
    var preventRunCount : Map[String, Int] = Map()
    var breakBypass = false
    var patchworkBypass = false
    var patchworkPending : |[Action] = None
    var persistent : $[(Card, Server)] = $      // trashed cards whose run-end abilities last until the run ends
    var breachFinishedFor : $[Run] = $
    var wouldSucceedFor : $[Run] = $

    // game-level triggers (game.scala triggerEffects): persistent abilities of cards trashed during the run
    def preventionTriggers(e : Event) : $[ForcedAction] = e match {
        case RunEnds(_, _, _) if persistent.any =>
            val effects = persistent./~{ case (c, _) => (!isActive(c)).??(CardImpls(c.code).triggers(c, e)) }
            persistent = $
            effects
        case BreachServer(s) if (s == HQ || s == RnD) && breachBonus > 0 && breachBonusTurn == turn =>
            val n = breachBonus
            breachBonus = 0
            $(LogEffect(|(Runner), "accesses " + n + " additional cards (Climactic Showdown)"), AccessBonusEffect(s, n))
        case _ => $
    }
    def persistentFor(c : Card, s : Server) : Boolean = persistent.has((c, s))
    var nextDamageSource : |[Card] = None       // the card doing the next damage (SourcedDamageEffect)
    var breachBonus = 0                         // access bonus for the first breach of HQ or R&D this turn (Climactic Showdown)
    var breachBonusTurn = -1

    def preventTop : |[PreventCtx] = preventStack.starting
    def preventCtx(q : String) : |[PreventCtx] = preventTop.%(c => c.q == q || c.kind == q)
    def preventUses(c : Card) : Int = preventTop./(x => x.uses.filter(_._1._1 == c).values.sum).|(0)

    val damageKinds = $("net", "meat", "core")

    def withTrashCause[T](cause : String)(f : => T) : T = {
        val was = trashCause
        trashCause = cause
        try f finally trashCause = was
    }

    // the prevention abilities f can use now in the window
    def preventOptions(f : Faction, ctx : PreventCtx) : $[(Card, Int, Ability)] =
        if (ctx.amount <= 0 || ctx.unpreventable)
            Nil
        else
            activeCards(f).distinct.%!(isDisabled)./~(c => impl(c).preventions(c, ctx.q, ctx.amount).indexed./((a, i) => (c, i, a))).%{ case (c, i, a) =>
                a.ok && canPay(f, a.costs, |(c)) && a.once.forall(k => !usedThisTurn(c, k)) && ctx.uses.get((c, i)).|(0) < 20 && !(f.isRunner && rotBlocksRunnerAbility(c))
            }

    // the forced interrupts of the cards (the game-level trigger hooks of the extension files are not consulted)
    def interruptEffects(e : PreventionInterrupt) : $[ForcedAction] = $(active, active.opponent)./~(f => activeCards(f).distinct.%!(isDisabled)./~(c => impl(c).triggers(c, e)))

    // would anything be offered in a window like ctx (used to skip windows nobody can act in)
    def preventWouldOffer(ctx : PreventCtx) : Boolean = {
        preventStack +:= ctx
        try preventOptions(Corp, ctx).any || preventOptions(Runner, ctx).any || interruptEffects(PreventionInterrupt(ctx.q, 1, ctx.amount)).any
        finally preventStack = preventStack.drop(1)
    }

    def preventNoun(ctx : PreventCtx, n : Int) : String = ctx.kind match {
        case "damage" => n + " " + ctx.dtype + " damage"
        case "tag" => n + " tag" + (n != 1).??("s")
        case "bad-pub" => n + " bad publicity"
        case "trash" => n + " card" + (n != 1).??("s")
        case k => k
    }

    def preventTitle(ctx : PreventCtx, f : Faction) : Elem = ctx.kind match {
        case "damage" => (f.isRunner).?("Prevent any of the ".txt ~ preventNoun(ctx, ctx.remaining).hl ~ "?").|("The Runner would suffer ".txt ~ preventNoun(ctx, ctx.remaining).hl)
        case "tag" => (f.isRunner).?("Avoid any of the ".txt ~ preventNoun(ctx, ctx.remaining).hl ~ "?").|("The Runner would take ".txt ~ preventNoun(ctx, ctx.remaining).hl)
        case "bad-pub" => "The Corp would take ".txt ~ preventNoun(ctx, ctx.remaining).hl
        case "trash" => "Prevent ".txt ~ ctx.cards./(cardElem).comma ~ " from being trashed?"
        case "end-run" => "The run would end".txt
        case "jack-out" => "The Runner would jack out".txt
        case "encounter" => "The encounter ability of ".txt ~ ctx.source./(cardElem).|("the ice".txt) ~ " would resolve"
        case k => ("Prevent " + k + "?").txt
    }

    def preventQuestion(q : String, n : Int) : Elem = preventTop.%(_.q == q) match {
        case Some(ctx) => preventTitle(ctx, ctx.side)
        case None => ("Prevent " + q + "?").txt
    }

    def preventPassLabel(f : Faction, q : String) : Elem = preventTop.%(_.q == q)./(_.kind) match {
        case Some("damage") => f.isRunner.?("Take the damage").|("Pass").txt
        case Some("tag") => f.isRunner.?("Take the tags").|("Pass").txt
        case Some("bad-pub") => f.isCorp.?("Take the bad publicity").|("Pass").txt
        case Some("trash") => "Continue trashing".txt
        case Some("end-run") => "Let the run end".txt
        case Some("jack-out") => "Allow the Runner to jack out".txt
        case Some("encounter") => "Allow the encounter ability".txt
        case _ => "Take it".txt
    }

    def openPrevent(ctx : PreventCtx, stages : $[Int]) : Continue = {
        ctx.side = active
        preventStack +:= ctx
        pushAll(stages./(s => PreventInterruptAction(ctx.kind, s) : ForcedAction) :+ PreventStepAction(ctx.kind))
        NextAction
    }

    def countPreventWindow(key : String) : (Boolean, Boolean) = {
        regInc("prevent-window-" + key)
        if (!preventRunRef.exists(r => run.exists(_ eq r))) {
            preventRunRef = run
            preventRunCount = Map()
        }
        val n = preventRunCount.get(key).|(0) + 1
        preventRunCount += key -> n
        (reg("prevent-window-" + key) == 1, run.any && n == 1)
    }

    def finishPrevent(ctx : PreventCtx) : Continue = {
        preventStack = preventStack.%(_ ne ctx)
        ctx.kind match {
            case "damage" =>
                if (ctx.remaining > 0)
                    push(DamageApplyEffect(ctx.dtype, ctx.remaining))
                else
                    log("All", ctx.dtype, "damage is prevented")
            case "tag" =>
                if (ctx.remaining > 0)
                    push(TagsApplyEffect(ctx.remaining))
                else
                    log(Runner, "avoids all tags")
            case "bad-pub" =>
                if (ctx.remaining > 0)
                    push(BadPubNowEffect(ctx.remaining))
                else
                    log("The bad publicity is prevented")
            case "end-run" =>
                if (ctx.remaining > 0)
                    push(EndRunNowEffect)
                else
                    log("The run does not end")
            case "jack-out" =>
                if (ctx.remaining > 0)
                    pushAll(ctx.resume)
                else {
                    log(Runner, "is prevented from jacking out")
                    run.foreach(_.cannotJackOut = true)
                    reopenWindows()
                    pushAll(ctx.resumePrevented)
                }
            case "encounter" =>
                if (ctx.remaining > 0)
                    ctx.source.foreach(ice => if (run.exists(r => !r.ended) && isRezzed(ice)) pushAll(impl(ice).onEncounter(ice)))
                else
                    log("The encounter ability of", ctx.source./(cardElem).|("the ice".txt), "is prevented")
            case "trash" =>
                trashBypass = true
                try ctx.cards.%(c => location.contains(c) && isInstalled(c)).reverse.foreach(c => trashCard(c, ctx.by, false))
                finally trashBypass = false
            case "break" =>
                breakBypass = true
                val r = try performInternal(ctx.action.get, NoVoid) finally breakBypass = false
                ctx.source.foreach { ice =>
                    if (ctx.preventedSubs.any) {
                        state(ice).brokenSubs = state(ice).brokenSubs.%!(ctx.preventedSubs.has)
                        log(ctx.preventedSubs.num.hl, "subroutine" + (ctx.preventedSubs.num != 1).??("s"), "on", ice, (ctx.preventedSubs.num != 1).?("are").|("is"), "not broken")
                    }
                }
                return r
            case _ =>
        }
        NextAction
    }

    // ---- break window (Tyr's Hand) ----------------------------------------------------------------------------------
    // cards that may be rezzed when a subroutine on bioroid ice protecting their server would be broken
    val breakRezInterrupts = $("03022")

    def breakInterruptible(ice : Card) : Boolean = has(ice, "Bioroid") && (zone(ice) match {
        case IceZone(s) =>
            root(s).exists(c => !isRezzed(c) && breakRezInterrupts.has(c.code) && credits(Corp) >= rezCost(c)) || {
                val ctx = new PreventCtx("break")
                ctx.source = |(ice)
                ctx.subs = $(0)
                ctx.by = Runner
                preventWouldOffer(ctx)
            }
        case _ => false
    })

    def openBreak(a : Action, ice : Card, l : $[Int]) : Continue = {
        val ctx = new PreventCtx("break")
        ctx.source = |(ice)
        ctx.subs = l.distinct
        ctx.count = ctx.subs.num
        ctx.by = Runner
        ctx.action = |(a)
        val rez : $[Card] = zone(ice) match {
            case IceZone(s) => root(s).%(c => !isRezzed(c) && breakRezInterrupts.has(c.code) && credits(Corp) >= rezCost(c))
            case _ => Nil
        }
        ctx.side = active
        preventStack +:= ctx
        pushAll(rez./(c => OptionalEffect(Corp, "A subroutine on bioroid ice protecting the server of " + c.title.filter(_ < 128) + " would be broken: rez it?", $(RezEffect(c, false)), $) : ForcedAction) :+ PreventStepAction("break"))
        NextAction
    }

    // ---- Patchwork: an interrupt of the basic play / install actions -------------------------------------------------
    def patchworkFor(c : Card, cost : Int) : |[Card] = (cost > 0 && hand(Runner).exists(_ != c)).??(activeCards(Runner).%(x => x.code == "25009" && !isDisabled(x) && !usedThisTurn(x, "patchwork") && counter(x, "patchwork") == 0).starting)

    def openPatchwork(a : Action, c : Card, pw : Card) : Continue = {
        patchworkPending = |(a)
        push(ChooseCardEffect(Runner, hand(Runner).%(_ != c), PrevPatchworkPurpose(pw), "Patchwork: trash a card from the grip to " + c.data.isEvent.?("play").|("install") + " " + c.title.filter(_ < 128) + " paying 2 credits less?", true), PatchworkResumeEffect)
        NextAction
    }

    // ---- trash window -----------------------------------------------------------------------------------------------
    override def trashCard(c : Card, by : Faction, accessed : Boolean) {
        if (!trashBypass && trashCause == "" && owner(c).isRunner && isInstalled(c) && trashPreventable(c, by)) {
            pending match {
                case TrashWindowEffect(l, b, cause) :: rest if b == by && !l.has(c) => pending = TrashWindowEffect(l :+ c, b, cause) +: rest
                case _ => push(TrashWindowEffect($(c), by, ""))
            }
        }
        else
            super.trashCard(c, by, accessed)
    }

    def trashPreventable(c : Card, by : Faction) : Boolean = {
        val ctx = new PreventCtx("trash")
        ctx.cards = $(c)
        ctx.by = by
        preventWouldOffer(ctx)
    }

    override def payCosts(f : Faction, costs : $[Cost], self : |[Card]) {
        // "trash an installed card" costs: the chosen card is trashed as a cost (no trash prevention window)
        val (tic, rest) = costs.partition(_.is[TrashInstalledCost])
        withTrashCause("cost")(super.payCosts(f, rest, self))
        tic.foreach {
            case TrashInstalledCost(k) => push(ChooseCardEffect(Runner, runnerInstalledOf(k), CostTrashPurpose, "Choose an installed " + (k == "").?("card").|(k.toLowerCase) + " to trash", false))
            case _ =>
        }
        val k = costs./{ case ClickCost(n) => n ; case LoseClickCost(n) => n ; case _ => 0 }.sum
        if (k > 0 && f.isRunner && run.exists(r => !r.ended))
            trigger(ClicksSpentOrLost(f, k))
    }

    override def resolvePurpose(f : Faction, l : $[Card], p : Purpose) : Continue = p match {
        case PreventTrashPurpose =>
            l.foreach(c => push(PreventTrashOfEffect(c)))
            NextAction
        case CostTrashPurpose =>
            withTrashCause("cost")(l.foreach(c => trashCard(c, f, false)))
            NextAction
        case PrevPatchworkPurpose(pw) =>
            l.foreach { c =>
                moveTo(c, DiscardZone(Runner))
                log(Runner, "uses", pw, "to trash", c, "from the grip and pay 2 credits less")
                markUsed(pw, "patchwork")
                addCounter(pw, "patchwork", 1)
            }
            NextAction
        case _ => super.resolvePurpose(f, l, p)
    }

    // ---- trash causes of the game rules (memory limit, uniqueness, console limit, manual adjustments) ---------------
    override def performTurn : PartialFunction[Action, Continue] = {
        val base = super.performTurn
        ({
            case a @ PlayCardAction(Runner, c) if !patchworkBypass && patchworkFor(c, playCost(c)).any => openPatchwork(a, c, patchworkFor(c, playCost(c)).get)
            case a @ RunnerInstallAction(Runner, c, _, _) if !patchworkBypass && patchworkFor(c, installCost(c, None)).any => openPatchwork(a, c, patchworkFor(c, installCost(c, None)).get)
            case a : RunnerInstallAction => withTrashCause("game")(base(a))
        } : PartialFunction[Action, Continue]) orElse base
    }

    override def performCards : PartialFunction[Action, Continue] = {
        val base = super.performCards
        ({
            case a @ RunnerInstallEffect(c, _, _) if base.isDefinedAt(a) =>
                val before = pending.num
                val oi = impl(c).onInstall(c)
                val r = withTrashCause("game")(base(a))
                // the damage of "when you install" abilities is done by the installed card
                val k = pending.num - before
                if (k > 0 && oi.exists(_.is[DamageEffect]))
                    pending = pending.take(k)./{
                        case DamageEffect(kind, n) if oi.has(DamageEffect(kind, n)) => SourcedDamageEffect(kind, n, c)
                        case x => x
                    } ++ pending.drop(k)
                r
        } : PartialFunction[Action, Continue]) orElse base
    }

    override def performManual : PartialFunction[Action, Continue] = {
        val base = super.performManual
        ({
            case a : ManualTrashAction => withTrashCause("game")(base(a))
        } : PartialFunction[Action, Continue]) orElse base
    }

    // ---- run: subroutine sources, jack out ----------------------------------------------------------------------------
    override def performRun : PartialFunction[Action, Continue] = {
        val base = super.performRun
        ({
            case a @ FireSubAction(c, _) =>
                val before = pending.num
                val r = base(a)
                if (pending.num == before + 1) {
                    pending = pending.head +: SubFinishedEffect(c) +: pending.drop(1)
                    preventSubs +:= c
                }
                r

            case a @ BreakSubAction(_, _, _, ice, sub) if !breakBypass && breakInterruptible(ice) => openBreak(a, ice, $(sub))
            case a @ BreakSubsAction(_, _, _, ice, l) if !breakBypass && l.any && breakInterruptible(ice) => openBreak(a, ice, l)
            case a @ BioroidBreakSubAction(_, ice, sub) if !breakBypass && breakInterruptible(ice) => openBreak(a, ice, $(sub))

            case SuccessfulRunAction if run.exists(r => !r.ended && !wouldSucceedFor.exists(_ eq r)) =>
                val r = run.get
                wouldSucceedFor = $(r)
                push(SuccessfulRunAction)
                trigger(RunWouldSucceed(r.server))
                NextAction
        } : PartialFunction[Action, Continue]) orElse base
    }

    override def performAccess : PartialFunction[Action, Continue] = {
        val base = super.performAccess
        ({
            case a @ AccessDoneAction(c) =>
                val r = base(a)
                trigger(AccessFinished(c))
                r

            case BreachEndAction if run.exists(r => !r.ended && !r.accessOnly && r.breachServer.any && !breachFinishedFor.exists(_ eq r)) =>
                val r = run.get
                breachFinishedFor = $(r)
                push(BreachEndAction)
                trigger(BreachFinished(r.breachServer.get))
                NextAction
        } : PartialFunction[Action, Continue]) orElse base
    }

    override def performAbilities : PartialFunction[Action, Continue] = {
        val base = super.performAbilities
        ({
            case a : TrashProgramForMemoryAction => withTrashCause("game")(base(a))

            case JackOutAction(f) if !jackOutBypass && run.exists(r => !r.ended && !r.cannotJackOut) && jackOutPreventable =>
                val ctx = new PreventCtx("jack-out")
                ctx.remaining = 1
                ctx.count = 1
                ctx.by = Runner
                ctx.resume = $(JackOutNowAction(f))
                ctx.resumePrevented = current.is[PaidWindowAction].$(current)
                openPrevent(ctx, $(1))

            case JackOutNowAction(f) =>
                jackOutBypass = true
                try base(JackOutAction(f)) finally jackOutBypass = false
        } : PartialFunction[Action, Continue]) orElse base
    }

    def jackOutPreventable : Boolean = {
        val ctx = new PreventCtx("jack-out")
        ctx.remaining = 1
        ctx.by = Runner
        preventWouldOffer(ctx)
    }

    // the "when encountered" abilities of a piece of ice, through the encounter prevention window
    def encounterAbility(ice : Card) : $[ForcedAction] = impl(ice).onEncounter(ice).any.$(EncounterAbilityEffect(ice))

    // ---- the windows ------------------------------------------------------------------------------------------------
    override def performEffects : PartialFunction[Action, Continue] = {
        val base = super.performEffects
        ({
            // damage: the rules below adjust the amount and queue PreventWindowAction, which opens the window
            case a @ DamageEffect(k, n) if n > 0 =>
                val r = base(a)
                pending match {
                    case PreventWindowAction(kk, m) :: rest => pending = PreventOpenDamageAction(kk, m) +: rest
                    case _ =>
                }
                r

            case PreventOpenDamageAction(k, n) =>
                if (n <= 0)
                    NextAction
                else {
                    val ctx = new PreventCtx("damage")
                    ctx.dtype = k
                    ctx.count = n
                    ctx.remaining = n
                    ctx.source = nextDamageSource.orElse(preventSubs.starting)
                    ctx.by = ctx.source./(owner).|(Corp)
                    nextDamageSource = None
                    val (t, r) = countPreventWindow(k)
                    ctx.firstThisTurn = t
                    ctx.firstThisRun = r
                    openPrevent(ctx, $(0, 1))
                }

            // a prevention ability written as a continuation: set the amount left
            case PreventWindowAction(k, n) if damageKinds.has(k) =>
                preventStack.find(_.kind == "damage") match {
                    case Some(ctx) =>
                        ctx.remaining = n.atLeast(0)
                        NextAction
                    case None =>
                        PreventOpenDamageAction(k, n)
                }

            case TagsEffect(n) if n > 0 =>
                val ctx = new PreventCtx("tag")
                ctx.count = n
                ctx.remaining = n
                val (t, r) = countPreventWindow("tag")
                ctx.firstThisTurn = t
                ctx.firstThisRun = r
                openPrevent(ctx, $(1))

            case BadPubEffect(n) if n > 0 =>
                val ctx = new PreventCtx("bad-pub")
                ctx.count = n
                ctx.remaining = n
                ctx.by = Corp
                openPrevent(ctx, $(1))

            case BadPubNowEffect(n) =>
                base(BadPubEffect(n))

            case EndRunEffect if run.exists(r => !r.ended && !r.jackedOut) =>
                val ctx = new PreventCtx("end-run")
                ctx.count = 1
                ctx.remaining = 1
                ctx.source = preventSubs.starting
                ctx.fromSub = preventSubs.any
                val (t, r) = countPreventWindow("end-run")
                ctx.firstThisTurn = t
                ctx.firstThisRun = r
                openPrevent(ctx, $(1))

            case JackOutEffect if !jackOutBypass && run.exists(r => !r.ended && !r.cannotJackOut) && jackOutPreventable =>
                val ctx = new PreventCtx("jack-out")
                ctx.remaining = 1
                ctx.count = 1
                ctx.by = Runner
                ctx.resume = $(JackOutNowEffect)
                openPrevent(ctx, $(1))

            case JackOutNowEffect =>
                jackOutBypass = true
                try performInternal(JackOutEffect, NoVoid) finally jackOutBypass = false

            case TrashWindowEffect(l, by, cause) =>
                val ll = l.%(c => location.contains(c) && isInstalled(c))
                if (ll.none)
                    NextAction
                else {
                    val ctx = new PreventCtx("trash")
                    ctx.cards = ll
                    ctx.count = ll.num
                    ctx.by = by
                    openPrevent(ctx, $(1))
                }

            case EncounterAbilityEffect(ice) =>
                if (run.exists(r => !r.ended) && isRezzed(ice) && impl(ice).onEncounter(ice).any) {
                    val ctx = new PreventCtx("encounter")
                    ctx.remaining = 1
                    ctx.count = 1
                    ctx.source = |(ice)
                    ctx.by = Corp
                    if (preventWouldOffer(ctx))
                        openPrevent(ctx, $(1))
                    else {
                        pushAll(impl(ice).onEncounter(ice))
                        NextAction
                    }
                }
                else
                    NextAction

            case SubFinishedEffect(c) =>
                preventSubs = preventSubs.drop(1)
                NextAction

            case PreventInterruptAction(k, stage) =>
                preventTop.%(_.kind == k).foreach { ctx =>
                    if (ctx.amount > 0 && !ctx.unpreventable)
                        pushAll(interruptEffects(PreventionInterrupt(ctx.q, stage, ctx.amount)))
                }
                NextAction

            case PreventStepAction(k) =>
                preventTop.%(_.kind == k) match {
                    case Some(ctx) =>
                        var result : |[Continue] = None
                        var guard = 0
                        while (result.isEmpty) {
                            guard += 1
                            if (ctx.amount <= 0 || ctx.passes >= 2 || guard > 4)
                                result = |(finishPrevent(ctx))
                            else {
                                val l = preventOptions(ctx.side, ctx)
                                if (l.none) {
                                    ctx.passes += 1
                                    ctx.side = ctx.side.opponent
                                }
                                else
                                    result = |(Ask(ctx.side).group(preventTitle(ctx, ctx.side)).add(l./{ case (c, i, a) => PreventUseAction(ctx.side, c, i, ctx.q, ctx.amount) }).add(PreventPassAction(ctx.side, ctx.q, ctx.amount)))
                            }
                        }
                        result.get
                    case None => NextAction
                }

            case PreventUseAction(f, c, i, q, n) if preventTop.exists(_.q == q) =>
                val ctx = preventTop.get
                impl(c).preventions(c, q, ctx.amount).lift(i) match {
                    case Some(a) =>
                        ctx.uses += (c, i) -> (ctx.uses.get((c, i)).|(0) + 1)
                        ctx.passes = 0
                        ctx.side = f
                        a.once.foreach(k => markUsed(c, k))
                        log(f, "uses", c, "to", a.label)
                        push(PreventStepAction(ctx.kind))
                        pushAll(a.effects)
                        payCosts(f, a.costs, |(c))
                        NextAction
                    case None =>
                        PreventStepAction(ctx.kind)
                }

            case PreventPassAction(f, q, n) if preventTop.exists(_.q == q) =>
                val ctx = preventTop.get
                ctx.passes += 1
                ctx.side = f.opponent
                PreventStepAction(ctx.kind)

            case PreventEffect(k, n) =>
                preventCtx(k).foreach { ctx =>
                    if (ctx.kind == "trash") {
                        if (n < 0) {
                            ctx.cards.foreach(c => log(c, "is not trashed"))
                            ctx.cards = $
                            ctx.all = true
                        }
                    }
                    else if (n < 0 || n >= ctx.remaining) {
                        ctx.prevented += ctx.remaining
                        ctx.remaining = 0
                        ctx.all = n < 0
                    }
                    else if (n > 0) {
                        ctx.prevented += n
                        ctx.remaining -= n
                    }
                }
                NextAction

            case PreventBoostEffect(k, n) =>
                preventCtx(k).foreach { ctx =>
                    if (ctx.remaining > 0 && n > 0) {
                        ctx.remaining += n
                        ctx.count += n
                        log("The pending", ctx.q, "is increased by", n.hl, "to", ctx.remaining.hl)
                    }
                }
                NextAction

            case PreventConvertDamageEffect(t, n) =>
                preventCtx("damage").foreach { ctx =>
                    ctx.dtype = t
                    ctx.count = n
                    ctx.remaining = n
                    ctx.prevented = 0
                    log("The damage becomes", n.hl, t, "damage")
                }
                NextAction

            case PreventTrashOfEffect(c) =>
                preventCtx("trash").foreach { ctx =>
                    if (ctx.cards.has(c)) {
                        ctx.cards = ctx.cards.%(_ != c)
                        ctx.prevented += 1
                        log(c, "is not trashed")
                    }
                }
                NextAction

            case PreventTrashChooseEffect(self, kinds) =>
                preventCtx("trash").foreach { ctx =>
                    val l = ctx.cards.%(c => c != self && location.contains(c) && isInstalled(c) && (kinds.none || kinds.has(c.data.kind)))
                    if (l.num == 1)
                        push(PreventTrashOfEffect(l.head))
                    else if (l.any)
                        push(ChooseCardEffect(Runner, l, PreventTrashPurpose, "Choose a card to prevent from being trashed", false))
                }
                NextAction

            case a @ LoseClicksEffect(f, n) =>
                val before = clicks(f)
                val r = base(a)
                if (clicks(f) < before && f.isRunner && run.exists(r => !r.ended))
                    trigger(ClicksSpentOrLost(f, before - clicks(f)))
                r

            case PreventBreakChooseEffect(self) =>
                preventCtx("break").foreach { ctx =>
                    ctx.source.foreach { ice =>
                        if (ctx.subs.num == 1)
                            push(PreventBreakOfEffect(ctx.subs.head))
                        else if (ctx.subs.any)
                            push(ChooseOneEffect(Corp, "Choose the subroutine that is not broken", ctx.subs./(i => Alt(subs(ice).lift(i)./(_.label).|("subroutine " + (i + 1)), $(PreventBreakOfEffect(i))))))
                    }
                }
                NextAction

            case PreventBreakOfEffect(i) =>
                preventCtx("break").foreach { ctx =>
                    if (ctx.subs.has(i)) {
                        ctx.subs = ctx.subs.%(_ != i)
                        ctx.preventedSubs :+= i
                        ctx.prevented += 1
                    }
                }
                NextAction

            case PatchworkResumeEffect =>
                patchworkPending match {
                    case Some(a) =>
                        patchworkPending = None
                        patchworkBypass = true
                        try performInternal(a, NoVoid) finally patchworkBypass = false
                    case None => NextAction
                }

            case SourcedDamageEffect(k, n, c) =>
                nextDamageSource = (n > 0).?(c)
                push(DamageEffect(k, n))
                NextAction

            case BreachBonusEffect(n) =>
                breachBonus = n
                breachBonusTurn = turn
                NextAction

            case PersistentEffect(c, s) =>
                if (run.exists(r => !r.ended && r.server == s) && !persistent.has((c, s)))
                    persistent :+= ((c, s))
                NextAction

            case LogEffect(f, text) =>
                f match {
                    case Some(f) => log(f, text)
                    case None => log(text)
                }
                NextAction
        } : PartialFunction[Action, Continue]) orElse base
    }
}

// helpers for card implementations
object Prevent {
    def damage(n : Int) : ForcedAction = PreventEffect("damage", n)
    def allDamage : ForcedAction = PreventEffect("damage", -1)
    def tags(n : Int) : ForcedAction = PreventEffect("tag", n)
    def allTags : ForcedAction = PreventEffect("tag", -1)
    def endRun : ForcedAction = PreventEffect("end-run", 1)
    def jackOut : ForcedAction = PreventEffect("jack-out", 1)
    def badPub : ForcedAction = PreventEffect("bad-pub", -1)
    def encounter : ForcedAction = PreventEffect("encounter", 1)
    def ctx(implicit g : Game) : |[PreventCtx] = g.preventTop
    def damageCtx(implicit g : Game) : |[PreventCtx] = g.preventCtx("damage")
    // the amount left in the open window of that kind
    def left(kind : String)(implicit g : Game) : Int = g.preventCtx(kind)./(_.amount).|(0)
}
