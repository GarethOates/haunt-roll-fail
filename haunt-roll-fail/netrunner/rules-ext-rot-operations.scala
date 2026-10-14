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
// Generic mechanics added for the rotated (FFG era) Corp operations (see STATUS-rot-operations.md):
// double / triple / terminal operations, currents, condition counters hosted on cards, trace margins,
// "install from anywhere choosing an affordable server", playing operations from any zone, remove-from-game
// instead of trashing, last-turn registers that the core engine does not keep.
// ---------------------------------------------------------------------------------------------------------------------

// ---- effects --------------------------------------------------------------------------------------------------------
case class TrashLingeringEffect(card : Card) extends ForcedAction                        // trash a current / condition / lingering operation if it is still in a play area
case class MarginTraceEffect(base : Int, success : $[ForcedAction], fail : $[ForcedAction]) extends ForcedAction  // a trace whose success effects read rotTraceMargin
case object RecordTraceMarginAction extends ForcedAction                                 // compute rotTraceMargin (trace strength - link strength) when the trace succeeds
case class RotInstallEffect(card : Card, servers : $[Server], allowNew : Boolean, payInstall : Boolean, rez : Int, adv : Int, source : |[Card]) extends ForcedAction with Reaskable  // rez : 0 no, 1 paying, 2 ignoring cost
case class RotServerChosenAction(self : Faction, card : Card, server : |[Server], payInstall : Boolean, rez : Int, adv : Int, source : |[Card]) extends BaseAction("Install")(g => g.cardElem(card) ~ " in " ~ server./(_.elem).|("a new remote server".hl) ~ " (" ~ g.rotInstallCost(card, server, payInstall, rez).hl ~ "[c])")
case class RotRecordServerAction(source : Card, card : Card) extends ForcedAction         // remember the server of an installed card in source's memo
case class RotPlayEffect(card : Card, ignoreAdditional : Boolean) extends ForcedAction    // play an operation from any zone without spending a click (paying its play cost)
case class RotPlayStartAction(card : Card) extends ForcedAction                          // reset the state of a played operation (stale counters / host from an earlier play)
case class AdvanceTimesEffect(card : Card, n : Int) extends ForcedAction                  // advance a card n times without paying
case class HostConditionEffect(card : Card, host : Card) extends ForcedAction             // host an operation as a condition counter (it stays in the play area with state.target = host)
case class SwapScoredEffect(a : Card, b : Card) extends ForcedAction                      // swap two cards between the score areas


// ---------------------------------------------------------------------------------------------------------------------
// Base class of the rotated operations
// ---------------------------------------------------------------------------------------------------------------------

// generic card actions / purposes handled by RotOp
case class RotInstallLoopAction(card : Card, from : String, left : Int, payInstall : Boolean, rez : Int, subtype : String) extends CardAction
case class RotInstallLoopPurpose(card : Card, from : String, left : Int, payInstall : Boolean, rez : Int, subtype : String) extends CardPurpose
case class RotInstallPurpose(card : Card, payInstall : Boolean, rez : Int, adv : Int, remoteOnly : Boolean) extends CardPurpose

abstract class RotOp(code : String) extends Startup(code) {
    def data = Cards(code)

    // additional clicks spent to play the operation (Double 1, Triple 2)
    def extraClicks : Int = data.has("Triple").?(2).|(data.has("Double").?(1).|(0))

    def terminal : Boolean = data.has("Terminal")
    def isCurrentCard : Boolean = data.has("Current")
    def isCondition : Boolean = false
    def rfg(self : Card)(implicit g : Game) : Boolean = false                   // remove from the game instead of trashing it

    // play restriction ("play only if ...")
    def playReq(self : Card)(implicit g : Game) : Boolean = true

    // additional costs other than clicks (forfeit an agenda, remove a tag, trash a card)
    def additionalCosts(self : Card)(implicit g : Game) : $[ForcedAction] = $

    // the effect of the operation
    def effects(self : Card)(implicit g : Game) : $[ForcedAction] = $

    // condition counters: is the host still valid
    def hostValid(self : Card, host : Card)(implicit g : Game) : Boolean = g.isInstalled(host)

    // effects resolved when the card leaves the play area (trashed)
    def onLeave(self : Card)(implicit g : Game) : $[ForcedAction] = $

    // stays in the play area for another reason (Bad Times)
    def lingers(self : Card)(implicit g : Game) : Boolean = false

    def host(self : Card)(implicit g : Game) : |[Card] = g.state(self).target

    def hosting(self : Card, c : Card)(implicit g : Game) = g.state(self).target.has(c) && g.zone(self).is[PlayZone]

    def inPlay(self : Card)(implicit g : Game) = g.zone(self).is[PlayZone]

    def clicksOk(implicit g : Game) : Boolean = extraClicks match {
        case 0 => true
        case 1 => doubleCanPlay(Corp)
        case n => g.clicks(Corp) > n
    }

    def clickCosts(implicit g : Game) : $[ForcedAction] = extraClicks match {
        case 0 => $
        case 1 => doubleCost(Corp)
        case n => $(LoseClicksEffect(Corp, n))
    }

    override def canPlay(self : Card)(implicit g : Game) = clicksOk && playReq(self)

    override def staysInPlay(self : Card)(implicit g : Game) = isCurrentCard || (isCondition && g.state(self).target.any) || lingers(self)

    def tail(self : Card)(implicit g : Game) : $[ForcedAction] = terminal.$(EndActionPhaseEffect) ++ rfg(self).$(RemoveFromGameEffect(self))

    override def onPlay(self : Card)(implicit g : Game) = $(RotPlayStartAction(self)) ++ clickCosts ++ additionalCosts(self) ++ effects(self) ++ tail(self)

    // played "ignoring any additional costs" (Accelerated Diagnostics)
    def onPlayNoAdditional(self : Card)(implicit g : Game) : $[ForcedAction] = $(RotPlayStartAction(self)) ++ effects(self) ++ tail(self)

    // ---- helpers ----------------------------------------------------------------------------------------------------
    def runnerCards(implicit g : Game) : $[Card] = g.runnerInstalled
    def runnerOf(kind : String)(implicit g : Game) : $[Card] = g.runnerInstalledOf(kind)
    def scoredAgendas(f : Faction)(implicit g : Game) : $[Card] = g.scored(f).%(_.data.isAgenda)
    def installedCost(c : Card) : Int = c.data.cost.|(0)
    def asAgendaPoints(self : Card, f : Faction, n : Int)(implicit g : Game) : Int = (g.zone(self) == ScoredZone(f)).??(n)

    def trashOne(l : $[Card], prompt : String) : $[ForcedAction] = l.any.$(ChooseCardEffect(Corp, l, TrashPurpose(Corp), prompt, false))

    def installLoop(self : Card, from : String, n : Int, payInstall : Boolean, rez : Int, subtype : String = "") : ForcedAction = RotInstallLoopAction(self, from, n, payInstall, rez, subtype)

    def corpCandidates(from : String, subtype : String)(implicit g : Game) : $[Card] = (from match {
        case "hand" => g.hand(Corp)
        case "discard" => g.discard(Corp)
        case _ => g.hand(Corp) ++ g.discard(Corp)
    }).%(c => corpInstallable(c) && (subtype == "" || c.data.has(subtype)))

    final override def performCard(a : CardAction)(implicit g : Game) : Continue = a match {
        case RotInstallLoopAction(self, from, left, pay, rez, st) =>
            if (left > 0) {
                val l = corpCandidates(from, st).%(c => g.rotInstallTargets(c, g.rotServersFor(c, false), true, pay, rez).any)
                if (l.any)
                    g.push(ChooseCardEffect(Corp, l, RotInstallLoopPurpose(self, from, left, pay, rez, st), "Install a card (" + left + " remaining)", true))
            }
            NextAction
        case _ => performOp(a)
    }

    final override def chosenCard(p : CardPurpose, cards : $[Card])(implicit g : Game) : Continue = p match {
        case RotInstallLoopPurpose(self, from, left, pay, rez, st) =>
            cards.single.foreach(c => g.push(RotInstallEffect(c, g.rotServersFor(c, false), true, pay, rez, 0, None), RotInstallLoopAction(self, from, left - 1, pay, rez, st)))
            NextAction
        case RotInstallPurpose(self, pay, rez, adv, remoteOnly) =>
            cards.single.foreach(c => g.push(RotInstallEffect(c, g.rotServersFor(c, remoteOnly), true, pay, rez, adv, |(self))))
            NextAction
        case _ => chosenOp(p, cards)
    }

    def performOp(a : CardAction)(implicit g : Game) : Continue = throw new Error("unhandled card action " + a)

    def chosenOp(p : CardPurpose, cards : $[Card])(implicit g : Game) : Continue = throw new Error("unhandled card purpose " + p)
}


// a condition counter: the operation is hosted on a card chosen when it is played (state.target) and stays in the play
// area while the host is valid; trashed by RotOperationRules when the host leaves play
case class ConditionHostPurpose(card : Card) extends CardPurpose

abstract class RotCondition(code : String) extends RotOp(code) {
    override def isCondition = true

    def candidates(self : Card)(implicit g : Game) : $[Card] = $

    def hostPrompt : String = "Choose a card to host the condition counter"

    // effects resolved when the host is chosen (after the condition is hosted)
    def hostEffects(self : Card, h : Card)(implicit g : Game) : $[ForcedAction] = $

    override def playReq(self : Card)(implicit g : Game) = candidates(self).any

    override def effects(self : Card)(implicit g : Game) = {
        val l = candidates(self)
        l.any.$(ChooseCardEffect(Corp, l, ConditionHostPurpose(self), hostPrompt, false))
    }

    override def chosenOp(p : CardPurpose, cards : $[Card])(implicit g : Game) : Continue = p match {
        case ConditionHostPurpose(self) =>
            cards.single.foreach(h => g.pushAll(HostConditionEffect(self, h) +: hostEffects(self, h)))
            NextAction
        case _ => chosenCondition(p, cards)
    }

    def chosenCondition(p : CardPurpose, cards : $[Card])(implicit g : Game) : Continue = throw new Error("unhandled card purpose " + p)
}


// ---------------------------------------------------------------------------------------------------------------------
// Rules
// ---------------------------------------------------------------------------------------------------------------------

trait RotOperationRules { game : Game =>

    var rotTraceCredits = 0                 // Runner credits when a margin trace started
    var rotTraceMargin = 0                  // amount by which the last margin trace exceeded the Runner's link strength
    var rotClickRunStarting = false         // a run started with the basic action is being initiated (Enhanced Login Protocol)

    def rotPlayed : $[Card] = at(PlayZone(Corp)) ++ at(PlayZone(Runner))

    def isCurrent(c : Card) = has(c, "Current")

    // servers where a corp card can be installed (agendas / assets: remotes only)
    def rotServersFor(c : Card, remoteOnly : Boolean) : $[Server] = (c.data.isIce || c.data.isUpgrade).?(remoteOnly.?(remotes).|(servers)).|(remotes)

    def rotInstallCost(c : Card, s : |[Server], payInstall : Boolean, rez : Int) : Int =
        payInstall.??(installCost(c, s)) + (rez == 1 && !c.data.isAgenda).??(rezCost(c))

    // the affordable install destinations (None = a new remote server)
    def rotInstallTargets(c : Card, l : $[Server], allowNew : Boolean, payInstall : Boolean, rez : Int) : $[|[Server]] =
        (l.%(servers.has)./(s => |(s) : |[Server]) ++ (allowNew && canCreateRemote).$(None : |[Server])).%(s => credits(Corp) >= rotInstallCost(c, s, payInstall, rez))

    // ---- game-level triggers: bookkeeping registers, currents, conditions whose host left play --------------------------
    // (called after the cards' own triggers: registers incremented here still have their old value inside card triggers)
    def rotOperationTriggers(e : Event) : $[ForcedAction] = {
        e match {
            case Installed(Runner, c) =>
                regInc("runner-installs")
                if (c.data.isResource)
                    regInc("install-resource")
            case Played(Runner, c) if c.data.isEvent => regInc("runner-played-event")
            case DamageDone("core", n) => regInc("core-damage", n)
            case ActionTaken(Runner, "run") =>
                rotClickRunStarting = true
                regInc("click-run")
            case RunInitiated(_) => rotClickRunStarting = false
            case TurnBegins(_) => flagsOf("rot-no-rez")./~(_.card).foreach(c => markUsed(c, "cannot-rez"))
            case _ =>
        }

        // currents: trashed when another current is played; Corp currents when an agenda is stolen, Runner currents when one is scored
        val currents : $[Card] = e match {
            case Played(_, c) if isCurrent(c) => rotPlayed.%(x => x != c && isCurrent(x))
            case AgendaStolen(_) => rotPlayed.%(x => isCurrent(x) && owner(x).isCorp)
            case AgendaScored(_) => rotPlayed.%(x => isCurrent(x) && owner(x).isRunner)
            case _ => $
        }

        val leaving : $[ForcedAction] = e match {
            case Trashed(c, _, _) => CardImpls(c.code) match {
                case r : RotOp => r.onLeave(c)
                case _ => $
            }
            case _ => $
        }

        // condition counters whose host is gone
        val conditions = rotPlayed.%(c => CardImpls(c.code) match {
            case r : RotOp => r.isCondition && state(c).target.exists(h => !r.hostValid(c, h))
            case _ => false
        })

        val trash = (currents ++ conditions).distinct.%(c => !pending.has(TrashLingeringEffect(c)))
        trash./(TrashLingeringEffect(_)) ++ leaving
    }

    def performRotOperations : PartialFunction[Action, Continue] = {
        case TrashLingeringEffect(c) =>
            if (zone(c).is[PlayZone])
                trashCard(c, owner(c), false)
            NextAction

        case MarginTraceEffect(base, success, fail) =>
            rotTraceCredits = credits(Runner)
            push(TraceEffect(base, RecordTraceMarginAction +: success, fail))
            NextAction

        case RecordTraceMarginAction =>
            rotTraceMargin = (traceStrength - fxTraceLinkStrength).atLeast(0)
            log("The trace strength exceeds the link strength by", rotTraceMargin.hl)
            NextAction

        case RotInstallEffect(c, l, allowNew, pay, rez, adv, source) =>
            if (!location.contains(c) || location.get(c).exists(inPlayZone))
                NextAction
            else {
                val targets = rotInstallTargets(c, l, allowNew, pay, rez)
                if (targets.none) {
                    log(Corp, "cannot install", (zone(c).is[DiscardZone] && state(c).seen).?(cardElem(c)).|("a card".hl))
                    NextAction
                }
                else
                    Ask(Corp).group("Install " ~ cardElem(c)).add(targets./(s => RotServerChosenAction(Corp, c, s, pay, rez, adv, source)))
            }

        case RotServerChosenAction(f, c, s, pay, rez, adv, source) =>
            val cost = pay.??(installCost(c, s))
            if (cost > 0) {
                credits += Corp -> (credits(Corp) - cost).atLeast(0)
                log(Corp, "pays", cost.credits, "to install")
            }
            val then = (rez > 0 && !c.data.isAgenda).$(RezEffect(c, rez == 2)) ++ (adv > 0).$(PlaceAdvancementEffect(c, adv)) ++ source./(x => RotRecordServerAction(x, c))
            pushAll(CorpInstallEffect(c, s, true) +: then)
            NextAction

        case RotRecordServerAction(x, c) =>
            state(x).memo = serverOf(c)./(_.name).|("")
            NextAction

        case RotPlayEffect(c, ignore) =>
            val f = owner(c)
            val cost = playCost(c)
            if (!location.contains(c) || zone(c).is[PlayZone] || availableCredits(f, None, "play-" + c.data.kind.toLowerCase) < cost)
                NextAction
            else {
                val from = zone(c)
                payCredits(f, cost, None, "play-" + c.data.kind.toLowerCase)
                moveTo(c, PlayZone(f))
                state(c).seen = true
                log(f, "plays", c, "(" ~ cost.credits ~ ")", from match {
                    case DeckZone(_) => "from R&D"
                    case DiscardZone(_) => "from Archives"
                    case _ => ""
                })
                regInc("played")
                push(PlayCleanupAction(c))
                val l = impl(c).onPlay(c)
                pushAll(ignore.?(CardImpls(c.code) match {
                    case r : RotOp => r.onPlayNoAdditional(c)
                    case _ => l.filterNot {
                        case LoseClicksEffect(Corp, _) => true
                        case RegisterEffect("double", _) => true
                        case _ => false
                    }
                }).|(l))
                trigger(Played(f, c))
                NextAction
            }

        case RotPlayStartAction(c) =>
            val s = state(c)
            s.reset()
            s.seen = true
            NextAction

        case AdvanceTimesEffect(c, n) =>
            if (isInstalled(c) && n > 0) {
                addCounter(c, "advancement", n)
                log(Corp, "advances", hiddenCorpElem(c), n.hl, "time" + (n != 1).??("s"))
                trigger(Advanced(c, n))
            }
            NextAction

        case HostConditionEffect(c, h) =>
            if (location.contains(h)) {
                state(c).target = |(h)
                log(c, "is hosted on", owner(h).isCorp.?(hiddenCorpElem(h)).|(cardElem(h)), "as a condition counter")
            }
            NextAction

        case SwapScoredEffect(a, b) =>
            val za = zone(a)
            val zb = zone(b)
            if (za.is[ScoredZone] && zb.is[ScoredZone]) {
                moveTo(a, zb)
                moveTo(b, za)
                log("Swapped", a, "and", b, "between the score areas")
                push(CheckWinAction)
            }
            NextAction
    }
}
