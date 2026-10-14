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
// Generic mechanics added for System Update 2021 / Midnight Sun (see STATUS-su21-ms.md)
// ---------------------------------------------------------------------------------------------------------------------

// purposes resolved by SU21Rules.resolveSU21Purpose
trait SU21Purpose extends Purpose

case class SabotagePurpose(n : Int) extends SU21Purpose                     // Corp chose the HQ cards to trash, rest from the top of R&D
case object ChargePurpose extends SU21Purpose                              // place 1 power counter on the chosen card
case object ForfeitPurpose extends SU21Purpose                             // forfeit the chosen agenda
case object InstallRezPurpose extends SU21Purpose                          // corp: install the chosen card (choosing the server) and rez it ignoring all costs
// DerezPurpose, BottomOfDeckPurpose(f), TopOfDeckPurpose(f): rules-ext-rwr-df-ur.scala
case class MoveHostedPurpose(host : Card) extends SU21Purpose              // move the chosen installed card so that it is hosted on host (Magnet)
case object TrashAndTagPurpose extends SU21Purpose                         // trash the chosen corp cards and give the Runner 1 tag per card (Mutually Assured Destruction)

// effects
case class SabotageEffect(n : Int) extends ForcedAction                    // sabotage n: the Corp trashes n cards from HQ and/or the top of R&D
case class ChargeEffect(f : Faction) extends ForcedAction                  // charge 1 installed card of f (only cards that already have a power counter)
// IdentifyMarkEffect / MarkShuffledAction: rules-ext-ph-tai.scala
case object ReplaceBreachEffect extends ForcedAction                       // "instead of breaching": the current run will not breach its server
case class AccessCardsEffect(server : Server, cards : $[Card], noStealOrTrash : Boolean) extends ForcedAction // access cards outside of a normal breach (pseudo-run)
case class BreachServerEffect(server : Server) extends ForcedAction        // breach a server outside of a run / after a run ended (Virtuoso)
case object SyncPseudoRunAction extends ForcedAction
case class InstallDiscountEffect(card : Card, discount : Int) extends ForcedAction // runner installs the card paying n less
case class RezDiscountEffect(card : Card, discount : Int) extends ForcedAction     // corp rezzes the card paying n less
case class RefillCreditsEffect(card : Card, n : Int) extends ForcedAction  // recurring credits: refill hosted credits to n
case class RandomTrashFromHandEffect(f : Faction, n : Int) extends ForcedAction
case class RandomTrashedAction(f : Faction, shuffled : $[Card]) extends ShuffledAction[Card]
case class LoseAllClicksEffect(f : Faction) extends ForcedAction
case class AddSubtypeEffect(card : Card, subtype : String) extends ForcedAction
case class RemoveSubtypeEffect(card : Card, subtype : String) extends ForcedAction
case class ScoreAgendaEffect(card : Card) extends ForcedAction             // score the card if it can be scored (Big Deal)
// AddToScoreAreaEffect(card, f): rules-ext-rwr-df-ur.scala
case class MoveAdvancementEffect(from : Card, to : Card, n : Int) extends ForcedAction
case class SetChoiceEffect(card : Card, choice : String) extends ForcedAction
// SetTargetEffect(card, |[target]): rules-ext-rwr-df-ur.scala
case class SetRunFlagEffect(key : String) extends ForcedAction             // "disable-root"
case object ChooseForfeitEffect extends ForcedAction                      // the Corp forfeits an agenda of its choice (ForfeitEffect(card) in rules-ext-startup.scala forfeits a given one)
case class PumpBreakerAllEffect(card : Card, n : Int) extends ForcedAction
case class MarkRunUsedEffect(card : Card, key : String) extends ForcedAction   // once per run marker


trait SU21Rules { game : Game =>

    // the Runner's mark (PHTAIRules.mark, identified with IdentifyMarkEffect)
    def isMark(s : Server) = mark.has(s)

    def chargeable(f : Faction) : $[Card] = installedCards(f).%(c => counter(c, "power") > 0)

    def otherInstalled(f : Faction, self : Card) : $[Card] = installedCards(f).but(self)

    // card titles for action labels must be ASCII
    def asciiTitle(c : Card) : String = c.title.toList.map(ch => if (ch < 128) ch else '?').mkString

    var lastPseudoRun : |[Run] = None

    // ---- game-level triggered effects that are not tied to an active card ----------------------------------------------
    def su21Triggers(e : Event) : $[ForcedAction] = e match {
        // bad publicity: the Runner gets credits for the run when it begins (spent before the pool, lost when the run ends)
        case RunInitiated(_) if badPublicity > 0 =>
            $(BadPublicityCreditsEffect)
        // Subliminal Messaging in Archives
        case TurnBegins(Corp) if regLast("runs") == 0 =>
            discard(Corp).%(_.code == "31082")./(c => OptionalEffect(Corp, "Subliminal Messaging: reveal it and add it to HQ?", $(RevealEffect(Corp, c), AddToHandEffect(c)), $))
        // Test Run: the installed program returns to the top of the stack at the end of the turn
        case TurnEnds(Runner) =>
            installedCards(Runner).%(c => state(c).choice == "test-run")./(c => MoveEffect(c, DeckZone(Runner), true))
        // Steelskin Scarring trashed from the grip by damage
        case DamageDone(_, _) =>
            lastDamaged.%(_.code == "33004")./(c => OptionalEffect(Runner, "Steelskin Scarring: draw 2 cards?", $(DrawEffect(Runner, 2)), $))
        // Light the Fire!: cards in the root of the attacked remote lose all abilities during the run, trash them when the run is successful
        case RunInitiated(s) if s.isRemote && run.exists(r => r.source.exists(_.code == "33009")) =>
            $(SetRunFlagEffect("disable-root"))
        case SuccessfulRun(s) if s.isRemote && run.exists(r => r.disableRoot && r.server == s) =>
            $(MessageEffect(Runner, "uses Light the Fire! to trash all cards in the root of " + s.name), TrashCardsEffect(root(s), Runner))
        case _ => $
    }

    // ---- purposes -----------------------------------------------------------------------------------------------------
    def resolveSU21Purpose(f : Faction, l : $[Card], p : SU21Purpose) : Continue = p match {
        case SabotagePurpose(n) =>
            l.foreach(c => toArchives(c, false))
            val rest = (n - l.num).atLeast(0).atMost(deck(Corp).num)
            deck(Corp).take(rest).foreach(c => toArchives(c, false))
            log(Corp, "trashes", l.num.cards, "from HQ and", rest.cards, "from the top of R&D")
            NextAction

        case ChargePurpose =>
            l.foreach { c =>
                log(Runner, "charges", c)
                push(AddCounterEffect(c, "power", 1))
            }
            NextAction

        case ForfeitPurpose =>
            l.foreach(c => push(ForfeitEffect(c)))
            NextAction

        case InstallRezPurpose =>
            l.single.foreach { c =>
                val ss = (c.data.isIce || c.data.isUpgrade).?(servers).|(remotes)
                push(ChooseServerEffect(Corp, c, true, ss, true), RezEffect(c, true))
            }
            NextAction

        case MoveHostedPurpose(h) =>
            l.foreach { c =>
                moveTo(c, HostedZone(h))
                log(c, "is now hosted on", h)
            }
            NextAction

        case TrashAndTagPurpose =>
            l.foreach(c => trashCard(c, Corp, false))
            if (l.any)
                push(TagsEffect(l.num))
            NextAction
    }

    // ---- effects ------------------------------------------------------------------------------------------------------
    def performSU21 : PartialFunction[Action, Continue] = {
        case SabotageEffect(n) =>
            val h = hand(Corp)
            val d = deck(Corp)
            if (n <= 0)
                NextAction
            else {
                log(Runner, "sabotages", n.hl)
                if (n >= h.num + d.num) {
                    h.foreach(c => toArchives(c, false))
                    d.foreach(c => toArchives(c, false))
                    log(Corp, "trashes all cards in HQ and R&D")
                    push(CheckWinAction)
                    NextAction
                }
                else {
                    push(ChooseCardsEffect(Corp, h, (n - d.num).atLeast(0), n.atMost(h.num), SabotagePurpose(n), "Sabotage " + n + ": choose cards to trash from HQ (the rest is trashed from the top of R&D)"))
                    NextAction
                }
            }

        case ChargeEffect(f) =>
            val l = chargeable(f)
            if (l.any)
                push(ChooseCardEffect(f, l, ChargePurpose, "Charge: choose an installed card with a power counter", false))
            else
                log(f, "has no card to charge")
            NextAction

        case ReplaceBreachEffect =>
            run.foreach { r =>
                if (!r.preventAccess) {
                    r.preventAccess = true
                    log(Runner, "will not breach", r.server, "(replacement effect)")
                }
            }
            NextAction

        case AccessCardsEffect(s, cards, noSteal) =>
            val l = cards.%(c => location.contains(c))
            if (l.none)
                NextAction
            else {
                val r = new Run(s, None, false)
                r.accessOnly = true
                r.outer = run
                r.phase = AccessPhase
                r.breachServer = |(s)
                r.toAccess = l
                r.cannotStealOrTrash = noSteal
                run = |(r)
                lastPseudoRun = |(r)
                push(AccessNextAction, SyncPseudoRunAction)
                NextAction
            }

        case BreachServerEffect(s) =>
            val r = new Run(s, None, false)
            r.accessOnly = true
            r.outer = run
            r.phase = AccessPhase
            r.breachServer = |(s)
            run = |(r)
            lastPseudoRun = |(r)
            log(Runner, "breaches", s)
            if (s == HQ) regInc("breach-hq")
            if (s == RnD) regInc("breach-rd")
            if (s == Archives) regInc("breach-archives")
            push(AccessServerAction(s), SyncPseudoRunAction)
            trigger(BreachServer(s))
            NextAction

        case SyncPseudoRunAction =>
            (lastPseudoRun, run) match {
                case (Some(p), Some(r)) if p != r =>
                    r.didSteal ||= p.didSteal
                    r.didTrash ||= p.didTrash
                    r.accessCount += p.accessCount
                    r.accessed ++= p.accessed
                case (Some(p), None) => lastRun = |(p)
                case _ =>
            }
            lastPseudoRun = None
            NextAction

        case InstallDiscountEffect(c, d) =>
            if (location.get(c).exists(inPlayZone))
                NextAction
            else {
                val cost = (installCost(c, None) - d).atLeast(0)
                if (!canSpend(Runner, cost, Spend.install(c))) {
                    log(Runner, "cannot afford to install", c)
                    NextAction
                }
                else {
                    if (cost > 0) {
                        spendCredits(Runner, cost, Spend.install(c))
                        log(Runner, "pays", cost.credits)
                    }
                    if (impl(c).mustBeHosted(c)) {
                        val hosts = allIce.%(h => impl(c).hostRequirement(c, h))
                        if (hosts.any)
                            push(ChooseCardEffect(Runner, hosts, HostInstallPurpose(c, true), "Choose a host for " + c.title, false))
                    }
                    else
                        push(RunnerInstallEffect(c, None, true))
                    NextAction
                }
            }

        case RezDiscountEffect(c, d) =>
            if (!isRezzed(c)) {
                val cost = (rezCost(c) - d).atLeast(0)
                if (canSpend(Corp, cost, Spend.rez(c))) {
                    spendCredits(Corp, cost, Spend.rez(c))
                    log(Corp, "pays", cost.credits, "to rez (" ~ d.hl ~ " less)")
                    rezCard(c, true)
                }
                else
                    log(Corp, "cannot afford to rez", c)
            }
            NextAction

        case RefillCreditsEffect(c, n) =>
            val cur = counter(c, "credit")
            if (cur < n && (isInstalled(c) || (zone(c).is[IdentityZone] && isActive(c)))) {     // identities too (rotated recurring-credit identities)
                addCounter(c, "credit", n - cur)
                log(c, "refills to", n.credits)
                trigger(CounterAdded(c, "credit"))
            }
            NextAction

        case RandomTrashFromHandEffect(f, n) =>
            val h = hand(f)
            if (h.none || n <= 0)
                NextAction
            else
                ShuffleTake[Card](h, n.atMost(h.num), RandomTrashedAction(f, _), "Random trash")

        case RandomTrashedAction(f, l) =>
            l.foreach(c => if (f.isCorp) toArchives(c, false) else moveTo(c, DiscardZone(f)))
            log(f, "trashes", f.isCorp.?(l.num.cards).|(l./(cardElem).comma), "at random from hand")
            NextAction

        case LoseAllClicksEffect(f) =>
            if (clicks(f) > 0) {
                log(f, "loses", clicks(f).clicks)
                clicks += f -> 0
            }
            NextAction

        case AddSubtypeEffect(c, st) =>
            if (!state(c).extraSubtypes.has(st)) {
                state(c).extraSubtypes :+= st
                log(c, "gains", st.hl)
            }
            NextAction

        case RemoveSubtypeEffect(c, st) =>
            state(c).extraSubtypes = state(c).extraSubtypes.diff($(st))
            NextAction

        case ScoreAgendaEffect(c) =>
            if (scorableAgendas.has(c))
                scoreAgenda(c)
            else
                log(Corp, "cannot score", hiddenCorpElem(c))
            NextAction

        case MoveAdvancementEffect(from, to, n) =>
            val m = n.atMost(state(from).advancement)
            if (m > 0) {
                addCounter(from, "advancement", -m)
                addCounter(to, "advancement", m)
                log(Corp, "moves", m.hl, "advancement counters from", hiddenCorpElem(from), "to", hiddenCorpElem(to))
            }
            NextAction

        case SetChoiceEffect(c, s) =>
            state(c).choice = s
            NextAction

        case SetRunFlagEffect(k) =>
            run.foreach { r =>
                k match {
                    case "disable-root" => r.disableRoot = true
                    case "cannot-jack-out" => r.cannotJackOut = true
                    case _ =>
                }
            }
            NextAction

        case ChooseForfeitEffect =>
            val l = scored(Corp).%(_.data.isAgenda)
            if (l.any)
                push(ChooseCardEffect(Corp, l, ForfeitPurpose, "Choose an agenda to forfeit", false))
            NextAction

        case PumpBreakerAllEffect(c, n) =>
            state(c).strengthTurn += n
            log(c, "gets +" + n, "strength for the remainder of the turn")
            NextAction

        case MarkRunUsedEffect(c, k) =>
            state(c).usedRun :+= k
            NextAction
    }
}
