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
// Generic mechanics added for Parhelion / The Automata Initiative:
// marks, sabotage, charge, threat, lingering flags (disabled cards, break restrictions, delayed effects), bypass
// events, breach replacement, access restrictions, expend abilities, discounted installs, "look at" prompts,
// action counting, cards hosted without being installed.
// ---------------------------------------------------------------------------------------------------------------------

// events (IceBypassed(ice) is defined in rules-ext-startup.scala)
case class MarkIdentified(server : Server) extends Event

// a lingering flag: key, optional card / server, scope ("encounter" | "run" | "turn" | "halfturn" with until = half-turn
// index while it stays active) and the effects resolved when it expires
case class Flag(key : String, card : |[Card], server : |[Server], scope : String, until : Int, expire : $[ForcedAction]) extends Record

// cards hosted on a card without being installed (Asmund Pudlat, Matryoshka) live in StashZone(host) (rules-ext-startup.scala)

// purposes resolved by the extension rules (not by a card)
trait PHPurpose extends Purpose
case class HostDiscountPurpose(card : Card, discount : Int) extends PHPurpose
case class InstallDiscountPurpose(discount : Int) extends PHPurpose

// effects
// SabotageEffect(n) and ChargeEffect(f) are defined in rules-ext-su21.scala
case object IdentifyMarkEffect extends ForcedAction
case class MarkShuffledAction(shuffled : $[Server]) extends ShuffledAction[Server]
case class AddFlagEffect(flag : Flag) extends ForcedAction
case class RemoveFlagEffect(key : String, card : |[Card]) extends ForcedAction
case class BypassIceEffect(ice : Card) extends ForcedAction
case class RedirectBreachEffect(from : Server, to : Server) extends ForcedAction
// LookAtEffect(f, cards, prompt): rules-ext-rwr-df-ur.scala
case class RunnerInstallDiscountEffect(card : Card, discount : Int) extends ForcedAction
case class ChooseServerDiscountEffect(card : Card, discount : Int, rez : Boolean, servers : $[Server], allowNew : Boolean) extends ForcedAction with Reaskable
case class ServerDiscountChosenAction(self : Faction, card : Card, server : |[Server], discount : Int, rez : Boolean) extends BaseAction("Install")(g => g.cardElem(card) ~ " in " ~ server./(_.elem).|("a new remote server".hl))
case class RandomToBottomEffect(f : Faction, n : Int) extends ForcedAction
case class RandomToBottomShuffledAction(f : Faction, n : Int, shuffled : $[Card]) extends ShuffledAction[Card]
case class StashEffect(card : Card, host : Card) extends ForcedAction
case class PreventEncounterAbilityEffect(ice : Card) extends ForcedAction
case class WinEffect(f : Faction, reason : String) extends ForcedAction
case class SetMarkEffect(server : Server) extends ForcedAction
case class ChooseServerFlagEffect(f : Faction, key : String, card : |[Card], scope : String, prompt : String, optional : Boolean) extends ForcedAction with Reaskable
case class ServerFlagChosenAction(self : Faction, key : String, card : |[Card], scope : String, server : |[Server]) extends BaseAction("Choose a server")(server./(_.elem).|("No server".hl))
case class SwapWithHandIceEffect(ice : Card, replacement : Card) extends ForcedAction
case class InstallFromListDiscountEffect(f : Faction, candidates : $[Card], discount : Int, prompt : String, optional : Boolean) extends ForcedAction
case class SeqEffect(effects : $[ForcedAction]) extends ForcedAction
case class ChooseServerMoveEffect(card : Card, servers : $[Server]) extends ForcedAction with Reaskable
case class MoveServerChosenAction(self : Faction, card : Card, server : Server) extends BaseAction(g => "Move " ~ g.cardElem(card))(server)
case class PlayCardFreeEffect(card : Card) extends ForcedAction


// ---------------------------------------------------------------------------------------------------------------------
// Implementation wrappers (a card whose abilities are currently altered)
// ---------------------------------------------------------------------------------------------------------------------

// delegates everything to the wrapped implementation
class DelegatingImpl(val raw : CardImpl) extends CardImpl {
    def code = raw.code
    override def memory(self : Card)(implicit g : Game) = raw.memory(self)
    override def link(self : Card)(implicit g : Game) = raw.link(self)
    override def handSize(self : Card, f : Faction)(implicit g : Game) = raw.handSize(self, f)
    override def strength(self : Card)(implicit g : Game) = raw.strength(self)
    override def iceStrengthMod(self : Card, ice : Card)(implicit g : Game) = raw.iceStrengthMod(self, ice)
    override def breakerStrengthMod(self : Card, breaker : Card)(implicit g : Game) = raw.breakerStrengthMod(self, breaker)
    override def rezCostMod(self : Card, card : Card)(implicit g : Game) = raw.rezCostMod(self, card)
    override def installCostMod(self : Card, card : Card)(implicit g : Game) = raw.installCostMod(self, card)
    override def playCostMod(self : Card, card : Card)(implicit g : Game) = raw.playCostMod(self, card)
    override def trashCostMod(self : Card, card : Card)(implicit g : Game) = raw.trashCostMod(self, card)
    override def advancementRequirementMod(self : Card, agenda : Card)(implicit g : Game) = raw.advancementRequirementMod(self, agenda)
    override def agendaPointsBonus(self : Card)(implicit g : Game) = raw.agendaPointsBonus(self)
    override def creditsUsableForRun(self : Card)(implicit g : Game) = raw.creditsUsableForRun(self)
    override def advanceable(self : Card)(implicit g : Game) = raw.advanceable(self)
    override def initialCounters(self : Card)(implicit g : Game) = raw.initialCounters(self)
    override def hostRequirement(self : Card, host : Card)(implicit g : Game) = raw.hostRequirement(self, host)
    override def mustBeHosted(self : Card)(implicit g : Game) = raw.mustBeHosted(self)
    override def canBeTrashedOnAccess(self : Card)(implicit g : Game) = raw.canBeTrashedOnAccess(self)
    override def accessCostPaidByRunner(self : Card)(implicit g : Game) = raw.accessCostPaidByRunner(self)
    override def triggers(self : Card, e : Event)(implicit g : Game) = raw.triggers(self, e)
    override def abilities(self : Card)(implicit g : Game) = raw.abilities(self)
    override def runnerAbilities(self : Card)(implicit g : Game) = raw.runnerAbilities(self)
    override def accessAbility(self : Card, target : Card)(implicit g : Game) = raw.accessAbility(self, target)
    override def preventions(self : Card, kind : String, n : Int)(implicit g : Game) = raw.preventions(self, kind, n)
    override def subroutines(self : Card)(implicit g : Game) = raw.subroutines(self)
    override def onEncounter(self : Card)(implicit g : Game) = raw.onEncounter(self)
    override def breaks(self : Card)(implicit g : Game) = raw.breaks(self)
    override def pumps(self : Card)(implicit g : Game) = raw.pumps(self)
    override def onPlay(self : Card)(implicit g : Game) = raw.onPlay(self)
    override def canPlay(self : Card)(implicit g : Game) = raw.canPlay(self)
    override def makesRun(self : Card)(implicit g : Game) = raw.makesRun(self)
    override def onScore(self : Card)(implicit g : Game) = raw.onScore(self)
    override def onSteal(self : Card)(implicit g : Game) = raw.onSteal(self)
    override def onAccess(self : Card)(implicit g : Game) = raw.onAccess(self)
    override def onRez(self : Card)(implicit g : Game) = raw.onRez(self)
    override def onInstall(self : Card)(implicit g : Game) = raw.onInstall(self)
    override def onTrash(self : Card, by : Faction)(implicit g : Game) = raw.onTrash(self, by)
    override def onEncounterEnds(self : Card)(implicit g : Game) = raw.onEncounterEnds(self)
    override def perform(a : CardAction)(implicit g : Game) = raw.perform(a)
    override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = raw.chosen(p, cards)
    override def canRez(self : Card)(implicit g : Game) = raw.canRez(self)
    override def canInstall(self : Card)(implicit g : Game) = raw.canInstall(self)
    override def allowsRun(self : Card, server : Server)(implicit g : Game) = raw.allowsRun(self, server)
    override def remoteLimit(self : Card)(implicit g : Game) = raw.remoteLimit(self)
    override def disablesHost(self : Card)(implicit g : Game) = raw.disablesHost(self)
    override def onActionTaken(self : Card, key : String, n : Int)(implicit g : Game) = raw.onActionTaken(self, key, n)
    // hooks of the other extension sets
    override def corpAbilities(self : Card)(implicit g : Game) = raw.corpAbilities(self)
    override def inactiveAbilities(self : Card)(implicit g : Game) = raw.inactiveAbilities(self)
    override def canHost(self : Card, card : Card)(implicit g : Game) = raw.canHost(self, card)
    override def canBreakWith(self : Card, breaker : Card)(implicit g : Game) = raw.canBreakWith(self, breaker)
    override def endRunPrevention(self : Card)(implicit g : Game) = raw.endRunPrevention(self)
    override def blocksSuccessfulRun(self : Card, server : Server)(implicit g : Game) = raw.blocksSuccessfulRun(self, server)
    override def accessFilter(self : Card, cards : $[Card])(implicit g : Game) = raw.accessFilter(self, cards)
    override def extraStealCost(self : Card, agenda : Card)(implicit g : Game) = raw.extraStealCost(self, agenda)
    override def protectsFromAccess(self : Card, target : Card)(implicit g : Game) = raw.protectsFromAccess(self, target)
    override def pumpDuration(self : Card, breaker : Card, d : Duration)(implicit g : Game) = raw.pumpDuration(self, breaker, d)
    override def untrashableByRunner(self : Card)(implicit g : Game) = raw.untrashableByRunner(self)
    override def staysInPlay(self : Card)(implicit g : Game) = raw.staysInPlay(self)
    override def activeInHand(self : Card)(implicit g : Game) = raw.activeInHand(self)
    override def activeInHeap(self : Card)(implicit g : Game) = raw.activeInHeap(self)
    override def canScore(self : Card)(implicit g : Game) = raw.canScore(self)
    override def canRezIn(self : Card, ctx : String)(implicit g : Game) = raw.canRezIn(self, ctx)
    override def runCost(self : Card, server : Server)(implicit g : Game) = raw.runCost(self, server)
    override def stealCostMod(self : Card, agenda : Card)(implicit g : Game) = raw.stealCostMod(self, agenda)
    override def extraSubroutines(self : Card, ice : Card)(implicit g : Game) = raw.extraSubroutines(self, ice)
    override def breakAllowed(self : Card, breaker : Card, ice : Card)(implicit g : Game) = raw.breakAllowed(self, breaker, ice)
    override def beforeDraw(self : Card, f : Faction, n : Int)(implicit g : Game) = raw.beforeDraw(self, f, n)
    override def creditsUsableFor(self : Card, purpose : String)(implicit g : Game) = raw.creditsUsableFor(self, purpose)
    override def preventsScoring(self : Card, agenda : Card)(implicit g : Game) = raw.preventsScoring(self, agenda)
    override def preventsBreak(self : Card, ice : Card, breaker : Card)(implicit g : Game) = raw.preventsBreak(self, ice, breaker)
    override def cannotLowerStrength(self : Card)(implicit g : Game) = raw.cannotLowerStrength(self)
    override def preventsSuccessfulRun(self : Card, server : Server)(implicit g : Game) = raw.preventsSuccessfulRun(self, server)
    override def disablesHosted(self : Card)(implicit g : Game) = raw.disablesHosted(self)
    override def drawBonus(self : Card, f : Faction)(implicit g : Game) = raw.drawBonus(self, f)
    override def onSetup(self : Card)(implicit g : Game) = raw.onSetup(self)
}

// a card that lost all its abilities (Klevetnik) or all but its printed subroutines (Hush)
class DisabledImpl(raw : CardImpl, keepSubs : Boolean) extends CardImpl {
    def code = raw.code
    override def subroutines(self : Card)(implicit g : Game) = keepSubs.?(raw.subroutines(self)).|($)
    override def hostRequirement(self : Card, host : Card)(implicit g : Game) = raw.hostRequirement(self, host)
    override def mustBeHosted(self : Card)(implicit g : Game) = raw.mustBeHosted(self)
    override def onTrash(self : Card, by : Faction)(implicit g : Game) = raw.onTrash(self, by)
    override def perform(a : CardAction)(implicit g : Game) = raw.perform(a)
    override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = raw.chosen(p, cards)
}

// a Runner card whose abilities cannot break subroutines (Hafrun)
class NoBreakImpl(raw : CardImpl) extends DelegatingImpl(raw) {
    override def breaks(self : Card)(implicit g : Game) = $
}

// ice whose subroutines are replaced by "Do 1 net damage" (Bankhar)
class NetDamageSubsImpl(raw : CardImpl) extends DelegatingImpl(raw) {
    override def subroutines(self : Card)(implicit g : Game) = raw.subroutines(self)./(s => Sub("Do 1 net damage (Bankhar)", DamageEffect("net", 1)))
}


trait PHTAIRules { game : Game =>

    var flags : $[Flag] = $
    var mark : |[Server] = None
    var actionCounts : Map[String, Int] = Map()

    // index of the current half-turn (each player's turn)
    def halfTurn = turn * 2 + active.isRunner.??(1)

    def activeFlags = flags.%(f => f.scope != "halfturn" || f.until >= halfTurn)
    def hasFlag(key : String) = activeFlags.exists(_.key == key)
    def hasFlag(key : String, c : Card) = activeFlags.exists(f => f.key == key && f.card.has(c))
    def flagsOf(key : String) = activeFlags.%(_.key == key)
    def addFlag(f : Flag) { flags :+= f }
    def removeFlags(key : String, c : |[Card]) { flags = flags.%!(f => f.key == key && (c.none || f.card == c)) }

    def encounterFlag(key : String, c : Card) = Flag(key, |(c), None, "encounter", 0, $)
    def runFlag(key : String, c : |[Card]) = Flag(key, c, None, "run", 0, $)
    def turnFlag(key : String, c : |[Card]) = Flag(key, c, None, "turn", 0, $)

    def expireFlags(scope : String) : $[ForcedAction] = {
        val (gone, kept) = flags.partition(_.scope == scope)
        flags = kept
        gone./~(_.expire)
    }

    def expireHalfTurnFlags() : $[ForcedAction] = {
        val (gone, kept) = flags.partition(f => f.scope == "halfturn" && f.until < halfTurn)
        flags = kept
        gone./~(_.expire)
    }

    // a card that lost all its abilities: a "disabled" flag (Klevetnik), a host that disables it (Hush), or a card in the root of the
    // attacked server during a Light the Fire! run
    def isDisabled(c : Card) : Boolean = hasFlag("disabled", c) || state(c).hosted.exists(h => isActive(h) && CardImpls(h.code).disablesHost(h)) || run.exists(r => r.disableRoot && !r.ended && zone(c) == RootZone(r.server))

    // the implementation of a card, taking into account current alterations
    def extImpl(c : Card) : CardImpl = {
        val raw = CardImpls(c.code)
        if (flags.none && state(c).hosted.none && !run.exists(_.disableRoot))
            raw
        else if (isDisabled(c))
            new DisabledImpl(raw, c.data.isIce)
        else if (hasFlag("net-subs", c))
            new NetDamageSubsImpl(raw)
        else if (hasFlag("cannot-break", c) && owner(c).isRunner)
            new NoBreakImpl(raw)
        else
            raw
    }

    // installed Corp cards and their servers as of the previous event (to know where a trashed card was)
    var snapshot : Map[Card, Server] = Map()
    var prevSnapshot : Map[Card, Server] = Map()
    var installing = false

    def lastServerOf(c : Card) : |[Server] = prevSnapshot.get(c)
    def wasInstalled(c : Card) : Boolean = prevSnapshot.contains(c)

    // bookkeeping on events; returns additional effects (expired lingering effects)
    def phTriggers(e : Event) : $[ForcedAction] = {
        prevSnapshot = snapshot
        snapshot = installedCards(Corp)./~(c => serverOf(c)./(s => c -> s)).toMap

        e match {
            case EncounterIce(ice) =>
                val l = expireFlags("encounter")
                addFlag(runFlag("encountered", |(ice)))
                l
            case PassIce(_) =>
                expireFlags("encounter")
            case RunEnds(_, _, _) =>
                expireFlags("encounter") ++ expireFlags("run")
            case SubsBroken(ice, subs, b) =>
                addFlag(runFlag("subs-broken", |(ice)))
                addFlag(encounterFlag("broken-by", b))
                $
            case TurnBegins(f) =>
                actionCounts = Map()
                expireHalfTurnFlags()
            case TurnEnds(f) =>
                if (f.isRunner)
                    mark = None
                expireFlags("turn")
            case Trashed(c, by, _) =>
                if (by.isRunner && owner(c).isCorp) {
                    regInc("runner-trash-corp")
                    if (prevSnapshot.contains(c))
                        regInc("runner-trash-installed")
                }
                stashed(c).foreach { x =>
                    moveTo(x, DiscardZone(owner(x)))
                    log(x, "is trashed")
                }
                $
            case AgendaStolen(_) =>
                // persistent "when an agenda is stolen from this server" effects of trashed cards (Daniela, Tucana)
                flagsOf("on-steal").%(f => run.exists(r => f.server.has(r.server)))./~(_.expire)
            case Installed(Corp, c) =>
                addFlag(turnFlag("installed", |(c)))
                $
            case Advanced(c, _) =>
                addFlag(turnFlag("advanced", |(c)))
                $
            case _ => $
        }
    }

    def encountersThisRun = flagsOf("encountered").num
    def subsBrokenThisRun = flagsOf("subs-broken").any

    def canRunServer(s : Server) : Boolean = servers.has(s) && allActive.forall(c => impl(c).allowsRun(c, s))

    def canCreateRemote : Boolean = allActive.forall(c => remotes.num < impl(c).remoteLimit(c))

    def canBreakSub(ice : Card, subs : $[Int]) : Boolean =
        !hasFlag("cannot-break", ice) && !(hasFlag("max-break-1", ice) && (state(ice).brokenSubs.any || subs.num > 1))

    def fixAction(a : UserAction) : UserAction = a match {
        case RezAction(f, c, ctx) => a.!(!impl(c).canRez(c), "cannot rez now")
        case CorpInstallAction(f, c, None) => a.!(!canCreateRemote, "remote server limit")
        case ServerChosenAction(f, c, None, _) => a.!(!canCreateRemote, "remote server limit")
        case ScoreAction(f, c) => a.!(hasFlag("cannot-score", c), "cannot score this turn")
        case InstallWhereAction(f, c) => a.!(!impl(c).canInstall(c), "cannot install now")
        case RunAction(f, s) => a.!(!canRunServer(s), "cannot run this server")
        case BreakSubAction(f, b, bi, ice, i) => a.!(!canBreakSub(ice, $(i)) || hasFlag("cannot-break", b), "cannot break")
        case BreakSubsAction(f, b, bi, ice, subs) => a.!(!canBreakSub(ice, subs) || hasFlag("cannot-break", b), "cannot break")
        case BioroidBreakSubAction(f, ice, i) => a.!(!canBreakSub(ice, $(i)), "cannot break")
        case _ => a
    }

    // click abilities of cards outside the usual active zones (expendable cards in HQ, agendas in the Runner's score area)
    def extraAbilities(f : Faction) : $[UserAction] = {
        if (clicks(f) <= 0 || run.any || active != f)
            $
        else {
            val sources = f.isCorp.??(hand(Corp).%(c => has(c, "Expendable")) ++ scored(Runner).%(c => owner(c).isCorp))
            sources./~ { c =>
                impl(c).abilities(c).indexed./~ { (a, i) =>
                    val clickOk = clicks(f) >= a.costs.of[ClickCost]./(_.n).sum.atLeast(1)
                    val onceOk = a.once.forall(k => !usedThisTurn(c, k))
                    val payOk = canPay(f, a.costs, |(c))
                    a.click.$(UseAbilityAction(f, c, i, "action").!(!a.ok, a.reason).!(!onceOk, "already used this turn").!(!payOk, "cannot pay").!(!clickOk, "needs a click"))
                }
            }
        }
    }

    def extAsk(c : Continue, actionPhase : Boolean) : Continue = c match {
        case Ask(f, l) => Ask(f, l./(fixAction) ++ actionPhase.??(extraAbilities(f)))
        case MultiAsk(asks, p) => MultiAsk(asks./(a => Ask(a.faction, a.actions./(fixAction) ++ (actionPhase && a.faction == active).??(extraAbilities(a.faction)))), p)
        case c => c
    }

    def restrictAccess() {
        run.foreach { r =>
            flagsOf("access-only")./~(_.card).foreach(c => r.toAccess = r.toAccess.%(_ == c))
            flagsOf("access-exclude")./~(_.card).foreach(c => r.toAccess = r.toAccess.but(c))
        }
    }

    // "whenever you finish taking an action" (Wage Workers)
    def countAction(key : String, c : Continue) : Continue = {
        if (!active.isCorp || phase != "action")
            c
        else {
            actionCounts += key -> (actionCounts.get(key).|(0) + 1)
            val n = actionCounts(key)
            val effects = activeCards(Corp)./~(x => impl(x).onActionTaken(x, key, n))
            if (effects.none)
                c
            else
                c match {
                    case Force(x : ForcedAction) =>
                        push(x)
                        pushAll(effects)
                        NextAction
                    case _ =>
                        val i = pending.lastIndexWhere(_.is[ActionPhaseAction])
                        if (i >= 0)
                            pending = pending.take(i) ++ effects ++ pending.drop(i)
                        else
                            pushAll(effects)
                        c
                }
        }
    }

    def installRunnerDiscounted(c : Card, host : |[Card], discount : Int) {
        val cost = (installCost(c, None) - discount).atLeast(0)
        payCredits(Runner, cost, None)
        if (cost > 0)
            log(Runner, "pays", cost.credits)
        push(RunnerInstallEffect(c, host, true))
    }

    def performPHTAI : PartialFunction[Action, Continue] = {
        // ---- interceptions of engine asks
        case a : ActionPhaseAction => extAsk(performTurn(a), true)
        case a : PaidWindowAction => extAsk(performAbilities(a), false)
        case a : InstallWhereAction => extAsk(performTurn(a), false)
        case a : ChooseServerEffect => extAsk(performCards(a), false)
        case a : RunMenuAction => extAsk(performTurn(a), false)
        case RunAnyServerEffect(source, l) => performEffects(RunAnyServerEffect(source, l.%(canRunServer)))
        case a : EncounterAction => extAsk(performRun(a), false)
        case a : BioroidBreakEffect => extAsk(performRun(a), false)
        case a @ AccessNextAction =>
            restrictAccess()
            performAccess(a)
        case a : CorpInstallEffect =>
            installing = true
            val c = performCards(a)
            installing = false
            c
        case SeqEffect(l) =>
            pushAll(l)
            NextAction
        // cards trashed from the stack / grip by mill or damage get their on-trash hooks (Strike Fund)
        case a @ MillEffect(f, n) =>
            val l = deck(f).take(n)
            val c = performEffects(a)
            l.foreach(x => pushAll(impl(x).onTrash(x, f)))
            c
        case a @ DamageDiscardAction(kind, l) =>
            val c = performEffects(a)
            l.foreach(x => pushAll(impl(x).onTrash(x, Corp)))
            c

        case ChooseServerMoveEffect(c, l) =>
            val ll = l.%(s => !serverOf(c).has(s))
            if (ll.none)
                NextAction
            else
                Ask(Corp).group("Move " ~ cardElem(c)).add(ll./(s => MoveServerChosenAction(Corp, c, s)))

        case MoveServerChosenAction(f, c, s) =>
            push(MoveToRootEffect(c, s))
            NextAction

        case PlayCardFreeEffect(c) =>
            val f = owner(c)
            if (zone(c) != HandZone(f) || !impl(c).canPlay(c) || credits(f) < playCost(c))
                NextAction
            else {
                payCredits(f, playCost(c), None)
                moveTo(c, PlayZone(f))
                state(c).seen = true
                impl(c).initialCounters(c).foreach { case (k, n) => addCounter(c, k, n) }
                log(f, "plays", c, "(" ~ playCost(c).credits ~ ")")
                regInc("played")
                push(PlayCleanupAction(c))
                pushAll(impl(c).onPlay(c))
                trigger(Played(f, c))
                NextAction
            }
        case EndRunEffect if hasFlag("no-etr") && preventSubs.any && run.exists(r => !r.ended) =>
            log("The run cannot end now")
            NextAction
        case a : ClickCreditAction => countAction("credit", performTurn(a))
        case a : ClickDrawAction => countAction("draw", performTurn(a))
        case a : PlayCardAction => countAction("play", performTurn(a))
        case a : CorpInstallAction => countAction("install", performTurn(a))
        case a : AdvanceAction => countAction("advance", performTurn(a))
        case a : TrashResourceAction => countAction("trash-resource", performTurn(a))
        case a : PurgeAction => countAction("purge", performTurn(a))
        case a @ UseAbilityAction(f, c, i, "action") if f.isCorp && impl(c).abilities(c).lift(i).exists(_.click) => countAction("ability-" + c.code + "-" + c.n + "-" + i, performAbilities(a))

        // ---- new effects
        case CardsChosenAction(f, l, HostDiscountPurpose(c, d)) =>
            l.single.foreach(h => installRunnerDiscounted(c, |(h), d))
            NextAction

        case CardsChosenAction(f, l, InstallDiscountPurpose(d)) =>
            l.single.foreach { c =>
                if (owner(c).isRunner)
                    push(RunnerInstallDiscountEffect(c, d))
                else
                    push(ChooseServerDiscountEffect(c, d, false, (c.data.isIce || c.data.isUpgrade).?(servers).|(remotes), true))
            }
            NextAction

        case IdentifyMarkEffect =>
            if (mark.any)
                NextAction
            else
                Shuffle[Server](Server.centrals, MarkShuffledAction(_), "Mark")

        case MarkShuffledAction(l) =>
            val s = l.head
            mark = |(s)
            log(Runner, "identifies", s, "as the mark")
            trigger(MarkIdentified(s))
            NextAction

        case SetMarkEffect(s) =>
            mark = |(s)
            log(Runner, "marks", s)
            trigger(MarkIdentified(s))
            NextAction

        case AddFlagEffect(f) =>
            addFlag(f)
            NextAction

        case RemoveFlagEffect(k, c) =>
            removeFlags(k, c)
            NextAction

        case BypassIceEffect(ice) =>
            run.foreach(_.bypass = true)
            log(Runner, "bypasses", ice)
            trigger(IceBypassed(ice))
            NextAction

        case RedirectBreachEffect(from, to) =>
            if (pending.exists(_ == BreachAction(from))) {
                pending = pending./{
                    case BreachAction(s) if s == from => BreachAction(to)
                    case a => a
                }
                log(Runner, "will breach", to, "instead of", from)
            }
            NextAction

        case RunnerInstallDiscountEffect(c, d) =>
            if (location.get(c).exists(inPlayZone))
                NextAction
            else if (impl(c).mustBeHosted(c)) {
                val hosts = allIce.%(h => impl(c).hostRequirement(c, h))
                if (hosts.any)
                    push(ChooseCardEffect(Runner, hosts, HostDiscountPurpose(c, d), "Choose a host", false))
                else
                    log("No host available")
                NextAction
            }
            else {
                installRunnerDiscounted(c, None, d)
                NextAction
            }

        case InstallFromListDiscountEffect(f, l, d, prompt, optional) =>
            val ll = l.%(c => location.contains(c) && !c.data.isEvent && !c.data.isOperation && !c.data.isIdentity).%(c => f.isCorp || (credits(f) >= (installCost(c, None) - d).atLeast(0) && (!c.data.isProgram || memoryFree >= c.data.memory.|(0)) && (!impl(c).mustBeHosted(c) || allIce.exists(h => impl(c).hostRequirement(c, h)))))
            if (ll.any)
                push(ChooseCardEffect(f, ll, InstallDiscountPurpose(d), prompt, optional))
            NextAction

        case ChooseServerDiscountEffect(c, d, rez, l, allowNew) =>
            val ll = l.%(servers.has)
            val newOk = allowNew && canCreateRemote
            if (ll.none && !newOk) {
                log("No server available for", c)
                NextAction
            }
            else
                Ask(Corp).group("Install " ~ cardElem(c)).add(ll./(s => ServerDiscountChosenAction(Corp, c, |(s), d, rez))).when(newOk)(ServerDiscountChosenAction(Corp, c, None, d, rez))

        case ServerDiscountChosenAction(f, c, s, d, rez) =>
            val cost = (installCost(c, s) + rez.??(rezCost(c)) - d).atLeast(0)
            credits += Corp -> (credits(Corp) - cost).atLeast(0)
            if (cost > 0)
                log(Corp, "pays", cost.credits)
            if (rez)
                push(CorpInstallEffect(c, s, true), RezEffect(c, true))
            else
                push(CorpInstallEffect(c, s, true))
            NextAction

        case RandomToBottomEffect(f, n) =>
            val h = hand(f)
            if (h.none)
                NextAction
            else
                ShuffleTake[Card](h, n.atMost(h.num), RandomToBottomShuffledAction(f, n, _), "Random")

        case RandomToBottomShuffledAction(f, n, l) =>
            l.foreach(c => moveTo(c, DeckZone(f)))
            log(f, "adds", l.num.cards, "at random from", f.isCorp.?("HQ").|("the grip"), "to the bottom of", f.isCorp.?("R&D").|("the stack"))
            NextAction

        case StashEffect(c, h) =>
            moveTo(c, StashZone(h))
            state(c).seen = true
            log(c, "is hosted on", h, "(not installed)")
            NextAction

        case PreventEncounterAbilityEffect(ice) =>
            val k = impl(ice).onEncounter(ice).num
            val i = pending.indexWhere(_ == EncounterAction(ice))
            if (k > 0 && i >= k) {
                pending = pending.take(i - k) ++ pending.drop(i)
                log("The encounter ability of", ice, "is prevented")
            }
            NextAction

        case WinEffect(f, reason) =>
            GameOverAction(|(f), reason)

        case ChooseServerFlagEffect(f, key, card, scope, prompt, optional) =>
            Ask(f).group(prompt).add(servers./(s => ServerFlagChosenAction(f, key, card, scope, |(s)))).when(optional)(ServerFlagChosenAction(f, key, card, scope, None))

        case ServerFlagChosenAction(f, key, card, scope, s) =>
            s match {
                case Some(s) =>
                    addFlag(Flag(key, card, |(s), scope, 0, $))
                    log(f, "chooses", s)
                case None =>
                    log(f, "chooses no server")
            }
            NextAction

        case SwapWithHandIceEffect(ice, c) =>
            zone(ice) match {
                case IceZone(s) =>
                    val l = ice_(s)
                    val idx = l.indexOf(ice)
                    moveTo(ice, HandZone(Corp))
                    moveTo(c, IceZone(s))
                    val ll = ice_(s).but(c)
                    zones += IceZone(s) -> (ll.take(idx) ++ $(c) ++ ll.drop(idx))
                    state(c).installedTurn = turn
                    state(c).rezzed = false
                    log(Corp, "swaps", ice, "with a piece of ice from HQ")
                case _ =>
            }
            NextAction

    }

    private def ice_(s : Server) = ice(s)
}
