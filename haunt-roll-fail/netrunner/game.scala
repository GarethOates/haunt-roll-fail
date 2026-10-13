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
// Players
// ---------------------------------------------------------------------------------------------------------------------

trait Faction extends BasePlayer with Named with Styling with Record {
    def name = toString
    def style = name.toLowerCase
    def opponent : Faction
    def isCorp = this == Corp
    def isRunner = this == Runner
}

case object Corp extends Faction { def opponent = Runner }
case object Runner extends Faction { def opponent = Corp }


// Card factions (for styling only)
trait NRFaction extends Named with Styling with Record {
    def name = toString
}

case object Anarch extends NRFaction
case object Criminal extends NRFaction
case object Shaper extends NRFaction
case object HaasBioroid extends NRFaction { override def name = "Haas-Bioroid" }
case object Jinteki extends NRFaction
case object NBN extends NRFaction
case object Weyland extends NRFaction { override def name = "Weyland Consortium" }
case object Neutral extends NRFaction

object NRFaction {
    def of(s : String) : NRFaction = s match {
        case "Anarch" => Anarch
        case "Criminal" => Criminal
        case "Shaper" => Shaper
        case "Haas-Bioroid" => HaasBioroid
        case "Jinteki" => Jinteki
        case "NBN" => NBN
        case "Weyland Consortium" => Weyland
        case _ => Neutral
    }

    def logo(s : String) : |[String] = s match {
        case "Anarch" => |("faction-runner-anarch")
        case "Criminal" => |("faction-runner-criminal")
        case "Shaper" => |("faction-runner-shaper")
        case "Haas-Bioroid" => |("faction-corp-hb")
        case "Jinteki" => |("faction-corp-jinteki")
        case "NBN" => |("faction-corp-nbn")
        case "Weyland Consortium" => |("faction-corp-weyland")
        case "Adam" => |("faction-runner-adam")
        case "Apex" => |("faction-runner-apex")
        case "Sunny Lebeau" => |("faction-runner-sunny")
        case _ => None
    }
}


// ---------------------------------------------------------------------------------------------------------------------
// Servers and zones
// ---------------------------------------------------------------------------------------------------------------------

trait Server extends Record with Elementary {
    def name : String
    def isCentral : Boolean
    def isRemote = !isCentral
    def elem = name.hl
    override def toString = name
}

case object HQ extends Server { val name = "HQ" ; val isCentral = true }
case object RnD extends Server { val name = "R&D" ; val isCentral = true }
case object Archives extends Server { val name = "Archives" ; val isCentral = true }
case class Remote(n : Int) extends Server { def name = "Server " + n ; val isCentral = false }

object Server {
    val centrals : $[Server] = $(HQ, RnD, Archives)
}

trait Zone extends Record

case class HandZone(f : Faction) extends Zone
case class DeckZone(f : Faction) extends Zone
case class DiscardZone(f : Faction) extends Zone
case class ScoredZone(f : Faction) extends Zone
case class RemovedZone(f : Faction) extends Zone
case class IdentityZone(f : Faction) extends Zone
case class PlayZone(f : Faction) extends Zone
case class RootZone(server : Server) extends Zone
case class IceZone(server : Server) extends Zone
case object RigZone extends Zone
case class HostedZone(host : Card) extends Zone
case object LimboZone extends Zone


// ---------------------------------------------------------------------------------------------------------------------
// Per-card mutable state
// ---------------------------------------------------------------------------------------------------------------------

class CardState {
    var rezzed = false
    var facedown = false
    var seen = false
    var counters : Map[String, Int] = Map()
    var host : |[Card] = None
    var hosted : $[Card] = $
    var strengthEncounter = 0
    var strengthRun = 0
    var strengthTurn = 0
    var installedTurn = -1
    var rezzedTurn = -1
    var brokenSubs : $[Int] = $
    var usedTurn : $[String] = $
    var usedRun : $[String] = $
    var extraSubtypes : $[String] = $
    var target : |[Card] = None       // a card chosen by this card (Femme Fatale, Trieste, Kit)
    var choice : String = ""          // a choice remembered by this card (Chameleon subtype, Security Testing server, Test Run marker)

    def counter(k : String) = counters.get(k).|(0)
    def advancement = counter("advancement")
    def virus = counter("virus")
    def credits = counter("credit")
    def power = counter("power")

    def reset() {
        rezzed = false
        facedown = false
        counters = Map()
        host = None
        hosted = $
        strengthEncounter = 0
        strengthRun = 0
        strengthTurn = 0
        installedTurn = -1
        rezzedTurn = -1
        brokenSubs = $
        usedTurn = $
        usedRun = $
        extraSubtypes = $
        target = None
        choice = ""
    }
}


// ---------------------------------------------------------------------------------------------------------------------
// Run state
// ---------------------------------------------------------------------------------------------------------------------

trait RunPhase extends Record
case object InitiationPhase extends RunPhase
case object ApproachIcePhase extends RunPhase
case object EncounterPhase extends RunPhase
case object MovementPhase extends RunPhase
case object ApproachServerPhase extends RunPhase
case object SuccessPhase extends RunPhase
case object AccessPhase extends RunPhase
case object EndedPhase extends RunPhase

class Run(var server : Server, val source : |[Card], val clickRun : Boolean) {
    var position : Int = 0
    var phase : RunPhase = InitiationPhase
    var ice : |[Card] = None
    var ended = false
    var successful = false
    var unsuccessful = false
    var didSteal = false
    var didTrash = false
    var didAccess = false
    var accessed : $[Card] = $
    var accessBonus : Map[String, Int] = Map()
    var cannotStealOrTrash = false
    var preventAccess = false
    var bypass = false
    var subsFired = 0
    var cannotJackOut = false
    var jackedOut = false
    var credits = 0
    var toAccess : $[Card] = $
    var accessCount = 0
    var window : $[Faction] = $
    var breachServer : |[Server] = None
    var accessOnly = false            // pseudo-run used to access cards outside of a breach (Deep Dive, Virtuoso)
    var outer : |[Run] = None         // the run to restore when an accessOnly pseudo-run ends
    var disableRoot = false           // cards in the root of the attacked server lose all abilities (Light the Fire!)

    def accessBonusFor(k : String) = accessBonus.get(k).|(0)
}


// ---------------------------------------------------------------------------------------------------------------------
// Events
// ---------------------------------------------------------------------------------------------------------------------

trait Event extends Record

case class TurnBegins(f : Faction) extends Event
case class TurnEnds(f : Faction) extends Event
case class MandatoryDraw(f : Faction) extends Event
case class AgendaScored(card : Card) extends Event
case class AgendaStolen(card : Card) extends Event
case class RunInitiated(server : Server) extends Event
case class SuccessfulRun(server : Server) extends Event
case class UnsuccessfulRun(server : Server) extends Event
case class RunEnds(server : Server, successful : Boolean, didSteal : Boolean) extends Event
case class BreachServer(server : Server) extends Event
case class ApproachServer(server : Server) extends Event
case class ApproachIce(ice : Card) extends Event
case class EncounterIce(ice : Card) extends Event
case class PassIce(ice : Card) extends Event
case class Installed(f : Faction, card : Card) extends Event
case class Rezzed(card : Card) extends Event
case class Derezzed(card : Card) extends Event
case class Trashed(card : Card, by : Faction, accessed : Boolean) extends Event
case class Advanced(card : Card, n : Int) extends Event
case class TagsGained(n : Int) extends Event
case class CounterAdded(card : Card, kind : String) extends Event
case class ClickDraw(f : Faction) extends Event
case class Played(f : Faction, card : Card) extends Event
case class Accessed(card : Card) extends Event
case class DamageDone(kind : String, n : Int) extends Event
case class SubsBroken(ice : Card, subs : $[Int], breaker : Card) extends Event
case class Purged(n : Int) extends Event
case class CardsDrawn(f : Faction, cards : $[Card]) extends Event
case class ServerCreated(server : Server) extends Event


// ---------------------------------------------------------------------------------------------------------------------
// Costs and abilities
// ---------------------------------------------------------------------------------------------------------------------

trait Cost extends Record with Elementary

case class CreditCost(n : Int) extends Cost { def elem = n.hl ~ " credits" }
case class ClickCost(n : Int) extends Cost { def elem = ("[click]" * n).hl }
case object TrashSelfCost extends Cost { def elem = "[trash]".hl }
case class VirusCost(n : Int) extends Cost { def elem = n.hl ~ " hosted virus counters" }
case class CounterCost(kind : String, n : Int) extends Cost { def elem = n.hl ~ (" hosted " + kind + " counters") }
case class TrashFromHandCost(n : Int) extends Cost { def elem = ("trash " + n + " from hand").hl }
case class LoseClickCost(n : Int) extends Cost { def elem = ("lose " + "[click]" * n).hl }
case object RemoveSelfCost extends Cost { def elem = "remove from game".hl }
case class TagCost(n : Int) extends Cost { def elem = ("take " + n + " tag").hl }

object Cost {
    def desc(l : $[Cost]) : String = l./{
        case CreditCost(n) => n + "[c]"
        case ClickCost(n) => "[click]" * n
        case TrashSelfCost => "[trash]"
        case VirusCost(n) => n + " virus"
        case CounterCost(k, n) => n + " " + k
        case TrashFromHandCost(n) => "trash " + n + " from hand"
        case LoseClickCost(n) => "lose " + "[click]" * n
        case RemoveSelfCost => "remove from game"
        case TagCost(n) => "take " + n + " tag"
    }.join(", ")
}

trait Duration extends Record
case object EncounterDuration extends Duration
case object RunDuration extends Duration
case object TurnDuration extends Duration

// A paid ability of a card (index is its position in the list returned by the implementation)
case class Ability(label : String, costs : $[Cost], effects : $[ForcedAction], click : Boolean = false, once : |[String] = None, ok : Boolean = true, reason : String = "")

// A subroutine of a piece of ice
case class Sub(label : String, effect : ForcedAction)

// An icebreaker break ability: n = 0 means any number
case class BreakAbility(costs : $[Cost], n : Int, subtypes : $[String], label : |[String] = None, after : $[ForcedAction] = $)

// An icebreaker strength pump
case class Pump(costs : $[Cost], strength : Int, duration : Duration = EncounterDuration, label : |[String] = None)


// ---------------------------------------------------------------------------------------------------------------------
// Marker traits for actions
// ---------------------------------------------------------------------------------------------------------------------

// Actions whose Ask can be regenerated by re-performing them (used after manual adjustments)
trait Reaskable { self : Action => }

// Run steps that are discarded from the pending queue when the run ends
trait RunStep { self : Action => }

// Actions handled by a specific card implementation
trait CardAction extends ForcedAction { def card : Card }

// A purpose for a card selection: what to do with the chosen card(s)
trait Purpose extends Record

// Card-specific purposes delegate to the card implementation
trait CardPurpose extends Purpose { def card : Card }

// Actions excluded from bot choices
trait NotForBots { self : Action => }


case class StartAction(version : String) extends StartGameAction with GameVersion

case object NextAction extends ForcedAction
case object IdleAction extends ForcedAction

case class GameOverAction(winner : |[Faction], reason : String) extends ForcedAction
case class GameOverInfo(winner : |[Faction], reason : String) extends BaseInfo("Game Over")(winner./(w => w.name.styled(w)).|("Nobody".hl) ~ " wins: " ~ reason)


// ---------------------------------------------------------------------------------------------------------------------
// The game
// ---------------------------------------------------------------------------------------------------------------------

class Game(val setup : $[Faction], val options : $[Meta.O]) extends BaseGame with ContinueGame with LoggedGame
    with SetupRules with TurnRules with CardRules with RunRules with AccessRules with EffectRules with ManualRules with AbilityRules with ExtRules {

    implicit def implicitGame : Game = this

    var isOver = false
    var winner : |[Faction] = None

    // deck selection
    var decks : Map[Faction, $[String]] = Map()
    var identity : Map[Faction, Card] = Map()
    var mulliganed : $[Faction] = $
    var kept : $[Faction] = $

    // zones
    var zones : Map[Zone, $[Card]] = Map()
    var location : Map[Card, Zone] = Map()
    var cs : Map[Card, CardState] = Map()

    var remotes : $[Remote] = $
    var nextRemote = 1

    // counters
    var credits : Map[Faction, Int] = Map(Corp -> 5, Runner -> 5)
    var clicks : Map[Faction, Int] = Map(Corp -> 0, Runner -> 0)
    var tags = 0
    var badPublicity = 0
    var coreDamage = 0

    // turn
    var turn = 0
    var active : Faction = Corp
    var turnStarted = false
    var phase : String = "setup"
    var current : ForcedAction = IdleAction

    // run
    var run : |[Run] = None
    var lastRun : |[Run] = None

    // pending effects queue
    var pending : $[ForcedAction] = $

    // registers (per turn / per run / last turn)
    var register : Map[String, Int] = Map()
    var registerLastTurn : Map[String, Int] = Map()
    var runsThisTurn : $[Server] = $
    var successfulRunsThisTurn : $[Server] = $
    var successfulRunsLastTurn : $[Server] = $
    var scoredPointsThisTurn = 0
    var cannotScoreThisTurn = false
    var clickDrawBonus = 0
    var highlightFaction : $[Faction] = $
    var runnerDamageThisTurn = 0
    var lastScoredFrom : |[Server] = None

    // -----------------------------------------------------------------------------------------------------------------
    // Card data helpers
    // -----------------------------------------------------------------------------------------------------------------

    def data(c : Card) = c.data
    def impl(c : Card) : CardImpl = CardImpls(c.code)

    def state(c : Card) : CardState = cs.get(c).|{ val s = new CardState ; cs += c -> s ; s }

    def owner(c : Card) : Faction = c.data.isCorp.?(Corp).|(Runner)

    def zone(c : Card) : Zone = location.get(c).|(LimboZone)

    def at(z : Zone) : $[Card] = zones.get(z).|($)

    def hand(f : Faction) = at(HandZone(f))
    def deck(f : Faction) = at(DeckZone(f))
    def discard(f : Faction) = at(DiscardZone(f))
    def scored(f : Faction) = at(ScoredZone(f))
    def removed(f : Faction) = at(RemovedZone(f))
    def rig = at(RigZone)
    def ice(s : Server) = at(IceZone(s))
    def root(s : Server) = at(RootZone(s))
    def hosted(c : Card) = state(c).hosted

    def servers : $[Server] = Server.centrals ++ remotes

    def serverOf(c : Card) : |[Server] = zone(c) @@ {
        case RootZone(s) => |(s)
        case IceZone(s) => |(s)
        case HostedZone(h) => serverOf(h)
        case _ => None
    }

    def isInstalled(c : Card) : Boolean = zone(c) @@ {
        case RootZone(_) => true
        case IceZone(_) => true
        case RigZone => true
        case HostedZone(h) => isInstalled(h)
        case _ => false
    }

    def isRezzed(c : Card) = state(c).rezzed
    def isActive(c : Card) : Boolean = zone(c) @@ {
        case IdentityZone(_) => true
        case ScoredZone(Corp) => owner(c).isCorp
        case ScoredZone(Runner) => owner(c).isRunner
        case RigZone => !state(c).facedown
        case RootZone(_) => state(c).rezzed
        case IceZone(_) => state(c).rezzed
        case HostedZone(h) => ((owner(c).isRunner && !state(c).facedown) || (owner(c).isCorp && state(c).rezzed)) && !impl(h).disablesHosted(h)
        case PlayZone(_) => true
        case _ => false
    }

    def installedCards(f : Faction) : $[Card] =
        if (f.isRunner)
            rig ++ rig./~(allHosted) ++ servers./~(s => ice(s))./~(allHosted).%(c => owner(c).isRunner)
        else
            servers./~(s => ice(s) ++ root(s)) ++ servers./~(s => (ice(s) ++ root(s))./~(allHosted)).%(c => owner(c).isCorp)

    def allHosted(c : Card) : $[Card] = hosted(c)./~(h => h +: allHosted(h))

    def activeCards(f : Faction) : $[Card] =
        identity.get(f).$ ++ scored(f).%(c => owner(c) == f || f.isRunner) ++ installedCards(f).%(isActive) ++ at(PlayZone(f))

    def allActive : $[Card] = activeCards(active) ++ activeCards(active.opponent)

    def subtypes(c : Card) : $[String] = c.data.subtypes ++ state(c).extraSubtypes

    def has(c : Card, subtype : String) = subtypes(c).has(subtype)

    def isIce(c : Card) = c.data.isIce
    def isAgenda(c : Card) = c.data.isAgenda
    def isProgram(c : Card) = c.data.isProgram
    def isIcebreaker(c : Card) = c.data.isIcebreaker

    def rezzedIce : $[Card] = servers./~(ice).%(isRezzed)
    def allIce : $[Card] = servers./~(ice)

    def cardElem(c : Card) : Elem = c.title.styled(NRFaction.of(c.data.faction))

    def hiddenElem(c : Card, viewer : Faction) : Elem = (owner(c) == viewer).?(cardElem(c)).|("a card".hl)

    // -----------------------------------------------------------------------------------------------------------------
    // Zone manipulation
    // -----------------------------------------------------------------------------------------------------------------

    def place(c : Card, z : Zone, top : Boolean = false) {
        location.get(c).foreach { old =>
            zones += old -> at(old).but(c)
            old @@ {
                case HostedZone(h) => state(h).hosted = state(h).hosted.but(c)
                case _ =>
            }
        }

        zones += z -> (top.?(c +: at(z)).|(at(z) :+ c))
        location += c -> z

        z @@ {
            case HostedZone(h) =>
                state(h).hosted :+= c
                state(c).host = |(h)
            case _ =>
                state(c).host = None
        }
    }

    def inPlayZone(z : Zone) = z @@ {
        case RootZone(_) => true
        case IceZone(_) => true
        case RigZone => true
        case HostedZone(_) => true
        case _ => false
    }

    // move a card out of play (or between zones) resetting its state and trashing hosted cards
    def leavePlay(c : Card) : $[Card] = {
        val h = state(c).hosted
        var trashedHosted : $[Card] = $
        h.foreach { x =>
            trashedHosted ++= leavePlay(x)
            place(x, DiscardZone(owner(x)))
            state(x).seen = true
            trashedHosted :+= x
        }
        state(c).reset()
        trashedHosted
    }

    def moveTo(c : Card, z : Zone, top : Boolean = false) {
        if (inPlayZone(zone(c)) && !inPlayZone(z))
            leavePlay(c)
        place(c, z, top)
        if (!inPlayZone(z) && zone(c) != z)
            state(c).reset()
    }

    def toArchives(c : Card, faceup : Boolean) {
        moveTo(c, DiscardZone(Corp))
        state(c).seen = faceup
        regInc("to-archives")
    }

    def newRemote() : Remote = {
        val r = Remote(nextRemote)
        nextRemote += 1
        remotes :+= r
        regInc("remote-created")
        trigger(ServerCreated(r))
        r
    }

    def cleanupRemotes() {
        remotes.foreach { r =>
            if (ice(r).none && root(r).none) {
                remotes = remotes.but(r)
                zones -= IceZone(r)
                zones -= RootZone(r)
            }
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Derived values
    // -----------------------------------------------------------------------------------------------------------------

    def counter(c : Card, k : String) = state(c).counter(k)
    def advancement(c : Card) = state(c).advancement

    def addCounter(c : Card, k : String, n : Int) {
        val s = state(c)
        s.counters += k -> (s.counter(k) + n).atLeast(0)
    }

    def agendaPoints(f : Faction) : Int = scored(f)./(c => c.data.agendaPoints.|(0) + impl(c).agendaPointsBonus(c)).sum

    def pointsToWin = 7

    def baseLink : Int = identity.get(Runner)./(_.data.baseLink.|(0)).|(0)
    def link : Int = baseLink + activeCards(Runner)./(c => impl(c).link(c)).sum

    def memoryTotal : Int = 4 + activeCards(Runner)./(c => impl(c).memory(c)).sum
    def memoryUsed : Int = installedCards(Runner).%(isProgram)./(c => c.data.memory.|(0)).sum
    def memoryFree : Int = memoryTotal - memoryUsed

    def handSize(f : Faction) : Int = 5 + allActive./(c => impl(c).handSize(c, f)).sum - f.isRunner.??(coreDamage)

    def advancementRequirement(c : Card) : Int = (c.data.advancementCost.|(0) + allActive./(x => impl(x).advancementRequirementMod(x, c)).sum + (!isActive(c)).??(impl(c).advancementRequirementMod(c, c))).atLeast(0)

    def iceStrength(c : Card) : Int = {
        val s = state(c)
        val mods = impl(c).strength(c) + allActive./(x => impl(x).iceStrengthMod(x, c)).sum + s.strengthEncounter + s.strengthRun + s.strengthTurn
        c.data.strength.|(0) + impl(c).cannotLowerStrength(c).?(mods.atLeast(0)).|(mods)
    }

    def breakerStrength(c : Card) : Int = {
        val s = state(c)
        c.data.strength.|(0) + impl(c).strength(c) + allActive./(x => impl(x).breakerStrengthMod(x, c)).sum + s.strengthEncounter + s.strengthRun + s.strengthTurn
    }

    def strength(c : Card) : Int = c.data.isIce.?(iceStrength(c)).|(breakerStrength(c))

    def rezCost(c : Card) : Int = (c.data.cost.|(0) + allActive./(x => impl(x).rezCostMod(x, c)).sum + (!isActive(c)).??(impl(c).rezCostMod(c, c))).atLeast(0)

    def installCost(c : Card, server : |[Server]) : Int = {
        val base = owner(c).isCorp.?(c.data.isIce.?(server./(ice(_).num).|(0)).|(0)).|(c.data.cost.|(0))
        (base + allActive./(x => impl(x).installCostMod(x, c)).sum + (!isActive(c)).??(impl(c).installCostMod(c, c))).atLeast(0)
    }

    def playCost(c : Card) : Int = (c.data.cost.|(0) + allActive./(x => impl(x).playCostMod(x, c)).sum).atLeast(0)

    def trashCost(c : Card) : |[Int] = c.data.trashCost./(t => (t + allActive./(x => impl(x).trashCostMod(x, c)).sum).atLeast(0))

    def tagged = tags > 0

    def canPay(f : Faction, costs : $[Cost], self : |[Card] = None) : Boolean = costs.forall {
        case CreditCost(n) => availableCredits(f, self) >= n
        case ClickCost(n) => clicks(f) >= n
        case TrashSelfCost => self.exists(isInstalled)
        case VirusCost(n) => self.exists(c => counter(c, "virus") >= n)
        case CounterCost(k, n) => self.exists(c => counter(c, k) >= n)
        case TrashFromHandCost(n) => hand(f).num >= n
        case LoseClickCost(n) => clicks(f) >= n
        case RemoveSelfCost => self.any
        case TagCost(n) => f.isRunner
    }

    // credits available including hosted credits usable for the purpose (run credits)
    def availableCredits(f : Faction, self : |[Card], purpose : String = "") : Int = credits(f) + creditSourcesFor(f, purpose)./(c => counter(c, "credit")).sum

    def creditSources(f : Faction) : $[Card] = activeCards(f).%(c => impl(c).creditsUsableForRun(c))

    // hosted credits usable for a purpose: run credits during a run, plus purpose specific ones (recurring credits)
    def creditSourcesFor(f : Faction, purpose : String) : $[Card] = activeCards(f).%(c => (f.isRunner && run.any && impl(c).creditsUsableForRun(c)) || (purpose != "" && impl(c).creditsUsableFor(c, purpose)))

    def paidAbilitiesUsedThisTurn(c : Card, key : String) = state(c).usedTurn.has(key)

    def usedThisTurn(c : Card, key : String) = state(c).usedTurn.has(key)
    def markUsed(c : Card, key : String) { state(c).usedTurn :+= key }

    def reg(k : String) = register.get(k).|(0)
    def regInc(k : String, n : Int = 1) { register += k -> (reg(k) + n) }
    def regLast(k : String) = registerLastTurn.get(k).|(0)

    // -----------------------------------------------------------------------------------------------------------------
    // Pending effect queue
    // -----------------------------------------------------------------------------------------------------------------

    def push(l : ForcedAction*) {
        pending = l.$ ++ pending
    }

    def pushAll(l : $[ForcedAction]) {
        pending = l ++ pending
    }

    def enqueue(l : ForcedAction*) {
        pending = pending ++ l
    }

    def flushRun() {
        pending = pending.%!(_.is[RunStep])
    }

    def nextStep : Continue = pending match {
        case a :: rest =>
            pending = rest
            a
        case Nil =>
            if (isOver)
                IdleAction
            else if (turnStarted)
                ActionPhaseAction(active)
            else
                IdleAction
    }

    // trigger an event: collect triggered abilities and push them to the queue (active player first)
    def trigger(e : Event) {
        val order = $(active, active.opponent)
        val effects = order./~(f => activeCards(f).%!(isDisabled)./~(c => impl(c).triggers(c, e))) ++ extTriggers(e)
        pushAll(effects)
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Logging helpers
    // -----------------------------------------------------------------------------------------------------------------

    implicit def descCard(g : Game, c : Card) : Elem = cardElem(c)

    def loggedPerform(action : Action, soft : Void) : Continue = {
        val c = performInternal(action, soft)

        c match {
            case Ask(_, Nil) =>
                println("empty ask as a result of " + action)
            case _ =>
        }

        highlightFaction = c match {
            case Ask(f, _) => $(f)
            case MultiAsk(a, _) => a./(_.faction)
            case _ => Nil
        }

        val r = action @@ {
            case a : Reaskable with ForcedAction if a.isSoft.not =>
                c @@ {
                    case Ask(f, l) =>
                        current = a
                        Ask(f, l :+ ManualAction(f))
                    case MultiAsk(asks, p) =>
                        current = a
                        MultiAsk(asks./(ask => ask.add(ManualAction(ask.faction))), p)
                    case c => c
                }
            case _ => c
        }

        r
    }

    def info(waiting : $[Faction], self : |[Faction], actions : $[UserAction]) : $[Info] = $

    def performInternal(a : Action, soft : Void) : Continue = {
        val pf = performSetup orElse performTurn orElse performCards orElse performRun orElse performAccess orElse performEffects orElse performManual orElse performAbilities orElse performExt

        a match {
            case NextAction => nextStep
            case IdleAction => Ask(active).add(IdleInfo())
            case GameOverAction(w, reason) =>
                isOver = true
                winner = w
                log("Game over:", reason.hl)
                GameOver(w.$, "Game Over", $(GameOverInfo(w, reason)))
            case a : CardAction if pf.isDefinedAt(a).not => impl(a.card).perform(a)
            case a if pf.isDefinedAt(a) => pf(a)
            case a : SelfPerform => a.perform(soft)(this)
            case a => throw new Error("unknown action " + a)
        }
    }
}

case class IdleInfo() extends BaseInfo()("Nothing to do")
