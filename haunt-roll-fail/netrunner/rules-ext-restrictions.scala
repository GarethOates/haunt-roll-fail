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
// Install / rez / play legality, global lockdowns, advancement counting and deck-building rules (STATUS-fix-restrictions.md)
//
// One legality check per kind of game action, applied to every path that performs it:
//   corpInstallBlock(card, server)   install lock (Signal Jamming), 1 ice per turn (Unscheduled Maintenance), remote server limit
//                                    (A Teia, Earth Station), agendas / assets in remotes only, printed server restrictions
//                                    ("Remote server only", "Central server only", "HQ or R&D only", "Install only in the root
//                                    of HQ", ...), the rotated upgrades' installOnly, the card's canInstall
//   runnerInstallBlock(card, host)   install lock (Jua, Signal Jamming), locked heap (Blacklist), Apex's resource ban, the card's
//                                    "install only if" requirement; facedown installs are always legal (as on jinteki)
//   rezBlock(card, ignoreCost)       the card's rez requirement, "cannot rez" markers / flags, Blackmail / Cyber Threat (no ice rez
//                                    during the run), Interdiction, Hacktivist Meeting without cards in HQ
//   playBlock(card)                  The News Now Hour (no current events), locked heap
//   corpCannotWin                    The Black File: a Corp win other than by flatline is turned into "continue"
//   abrRestrict                      Always Be Running: the first [click] of the turn runs or plays a run event
//   programUseBlocked(program)       Vikram 1.0 / Wendigo ("the Runner cannot use (the chosen) program(s) for the remainder of
//                                    this run")
// They are applied
//   * to every Ask the engine produces (restrictContinue, called by Game.performInternal): offered options that are illegal are
//     greyed out with the reason; an Ask left without any usable option gets a "Continue" (RestrictedSkipAction) so it never
//     dead-ends;
//   * at the effect level (performRestrictions, first in the performInternal chain): CorpInstallEffect / RunnerInstallEffect and
//     the effects that pay before installing or rezzing (discounted / paying installs, install-and-rez) refuse illegal targets
//     before any payment, and the rezCard override refuses illegal rezzes whatever the path.
// Position: `... with CreditRules with RestrictionRules with FixRules with PreventionRules` (ENGINE.md 8.6). The checks do not
// depend on the mixin order: performRestrictions is the first partial function of the performInternal chain and restrictContinue
// wraps the final result of every performInternal call (prevention-window asks included); the only stackable override, rezCard,
// only needs to be above RotAssetRules.
// Also here: "limit 1 region per server" (installing a region trashes the server's previous region, like an asset / agenda),
// installs into a remote whose last card was just overinstalled keep the server, Satellite Grid's extra advancement token
// (advancementBonus, read by Game.advancement), agenda values with modifiers (agendaValue, used for the score areas),
// the Blacklist heap lock for every move (heapLocked, read by Game.moveTo) and the per-identity deck-building rules (DeckRules,
// used by Decks.validate and the deck preview).
// ---------------------------------------------------------------------------------------------------------------------

case class RestrictedSkipAction(self : Faction) extends BaseAction("No legal option")("Continue")
case class NoRootInstallPurpose(card : Card, server : Server) extends CardPurpose        // install the chosen card, not in the root of server (Vaporframe Fabricator)


object InstallRules {
    // printed server restrictions ("Remote server only.", "Central server only.", "HQ or R&D only.", "Root of HQ or R&D only.",
    // "Install only in a remote server.", "Install only in the root of HQ.", "Install only in the root of R&D.")
    lazy val serverRule : Map[String, String] = Cards.all./~{ d =>
        val t = d.text.toLowerCase
        val r =
            if (t.contains("remote server only") || t.contains("install only in a remote server") || t.contains("install only in the root of a remote server")) |("remote")
            else if (t.contains("central server only")) |("central")
            else if (t.contains("hq or r&d only")) |("hq-rd")
            else if (t.contains("install only in the root of hq.")) |("hq")
            else if (t.contains("install only in the root of r&d.")) |("rd")
            else None
        r./(d.code -> _)
    }.toMap

    // s = None: a new remote server
    def serverAllowed(code : String, s : |[Server]) : Boolean = serverRule.get(code) match {
        case Some("remote") => s.forall(_.isRemote)
        case Some("central") => s.exists(_.isCentral)
        case Some("hq-rd") => s.has(HQ) || s.has(RnD)
        case Some("hq") => s.has(HQ)
        case Some("rd") => s.has(RnD)
        case _ => true
    }
}


trait RestrictionRules extends EffectRules { game : Game =>

    def restrictRunActive : Boolean = run.exists(r => !r.ended)

    def notInPlay(c : Card) : Boolean = location.contains(c).not || !location.get(c).exists(inPlayZone)

    def isCorpInstallKind(c : Card) = c.data.isAgenda || c.data.isAsset || c.data.isUpgrade || c.data.isIce
    def isRunnerInstallKind(c : Card) = c.data.isProgram || c.data.isHardware || c.data.isResource

    def isRegion(c : Card) = has(c, "Region")

    // ---- install ------------------------------------------------------------------------------------------------------
    def corpInstallLocked : Boolean = hasFlag("corp-no-install") || (hasFlag("rot-no-install") && restrictRunActive)
    def runnerInstallLocked : Boolean = hasFlag("runner-no-install") || (hasFlag("rot-no-install") && restrictRunActive)

    def remoteLimitNow : Int = allActive./(x => impl(x).remoteLimit(x)).minOption.|(99)

    def corpInstallBlock(c : Card, s : |[Server]) : |[String] =
        if (!isCorpInstallKind(c)) None
        else if (corpInstallLocked) |("installing is locked")
        else if (c.data.isIce && iceInstallBlocked) |("only 1 piece of ice per turn")
        else if (s.none && remotes.num >= remoteLimitNow) |("remote server limit")
        else if ((c.data.isAgenda || c.data.isAsset) && s.exists(_.isCentral)) |("remote servers only")
        else if (!c.data.isIce && !InstallRules.serverAllowed(c.code, s)) |("cannot be installed there")
        else if (!c.data.isIce && !rotInstallAllowed(c, s)) |("cannot be installed there")
        else if (!impl(c).canInstall(c)) |("cannot install now")
        else None

    // the restrictions that do not depend on the server (hosted installs)
    def corpHostInstallBlock(c : Card) : |[String] =
        if (corpInstallLocked) |("installing is locked")
        else if (c.data.isIce && iceInstallBlocked) |("only 1 piece of ice per turn")
        else if (!impl(c).canInstall(c)) |("cannot install now")
        else None

    def corpInstallTargets : $[|[Server]] = servers./(s => |(s)) :+ None

    def corpCanInstallSomewhere(c : Card) : |[String] = {
        val l = corpInstallTargets./(s => corpInstallBlock(c, s))
        if (l.exists(_.none)) None else l.head
    }

    def runnerInstallBlock(c : Card, host : |[Card]) : |[String] =
        if (!isRunnerInstallKind(c)) None
        else if (runnerInstallLocked) |("installing is locked")
        else if (heapLockedCard(c)) |("the heap is locked")
        else if (rhInstallBlocked(c)) |("cannot install this card")
        else if (!impl(c).canInstall(c)) |("install requirement not met")
        else None

    // cards already installed in the root of s that installing c trashes (one asset or agenda, one region)
    def overinstallVictims(c : Card, s : Server) : $[Card] =
        root(s).%(x => x != c && (((c.data.isAgenda || c.data.isAsset) && (x.data.isAgenda || x.data.isAsset)) || (c.data.isUpgrade && isRegion(c) && isRegion(x))))

    // ---- rez ----------------------------------------------------------------------------------------------------------
    // "Rez only during your turn" (Front Company) is a requirement, not a cost: it applies even when all costs are ignored
    def rezRequirement(c : Card) : Boolean = c.data.text.toLowerCase.contains("rez only")

    def rezBlock(c : Card, ignoreCost : Boolean = false) : |[String] =
        if ((!ignoreCost || rezRequirement(c)) && !impl(c).canRez(c)) |("cannot rez")
        else if (usedThisTurn(c, "cannot-rez")) |("cannot rez this turn")
        else if (rhRezBlocked(c)) |("cannot rez")
        else if (c.data.isIce && noIceRez) |("cannot rez ice during this run")
        else if (!c.data.isIce && active.isRunner && currentActive("11087")) |("Interdiction: no non-ice rez during the Runner's turn")
        else if (!ignoreCost && !c.data.isIce && currentActive("08021") && hand(Corp).none) |("Hacktivist Meeting: no card in HQ to trash")
        else None

    // ---- play ---------------------------------------------------------------------------------------------------------
    def playBlock(c : Card) : |[String] =
        if (owner(c).isRunner && has(c, "Current") && rotLive("06045").any) |("The News Now Hour: no current events")
        else if (heapLockedCard(c)) |("the heap is locked")
        else None

    // ---- program use --------------------------------------------------------------------------------------------------
    def programUseBlocked(c : Card) : Boolean = owner(c).isRunner && c.data.isProgram && restrictRunActive && (hasFlag("no-program-use") || hasFlag("cannot-use", c))

    // ---- Blacklist: cards cannot leave the Runner's heap (Harbinger never reaches the heap: its trash is replaced) -------
    def heapLockedCard(c : Card) : Boolean = zone(c) == DiscardZone(Runner) && c.code != "09034" && rotLive("08036").any

    def heapLocked(c : Card, z : Zone) : Boolean = z != DiscardZone(Runner) && location.get(c).has(DiscardZone(Runner)) && heapLockedCard(c)

    // ---- Satellite Grid: each piece of ice protecting its server has 1 additional advancement token ---------------------
    def advancementBonus(c : Card) : Int = location.get(c) match {
        case Some(IceZone(s)) => root(s).count(x => x.code == "07023" && isRezzed(x) && !isDisabled(x))
        case _ => 0
    }

    // ---- agenda value with its modifiers (Political Graffiti, Nightmare Archive, ...): printed + own + hosted bonuses -----
    def agendaValue(c : Card) : Int = c.data.agendaPoints.|(0) + impl(c).agendaPointsBonus(c) + hosted(c)./(h => impl(h).agendaPointsBonus(h)).sum

    // ---- the rez sink -------------------------------------------------------------------------------------------------
    override def rezCard(c : Card, ignoreCost : Boolean) {
        if (isRezzed(c))
            super.rezCard(c, ignoreCost)
        else
            rezBlock(c, ignoreCost) match {
                case Some(r) =>
                    log(Corp, "cannot rez", hiddenCorpElem(c), "(" + r + ")")
                case None =>
                    val hacktivist = !ignoreCost && !c.data.isIce && currentActive("08021")
                    super.rezCard(c, ignoreCost)
                    if (hacktivist && isRezzed(c)) {
                        log(Corp, "trashes a random card from HQ (Hacktivist Meeting)")
                        push(RandomTrashFromHandEffect(Corp, 1))
                    }
            }
    }

    // ---- ask filter ---------------------------------------------------------------------------------------------------
    def grey(a : UserAction, r : |[String]) : UserAction = r match {
        case Some(x) => a.!(true, x)
        case None => a
    }

    def installPurposeBlock(c : Card, p : Purpose) : |[String] = {
        def any = if (owner(c).isCorp) corpCanInstallSomewhere(c) else runnerInstallBlock(c, None)
        p match {
            case InstallPurpose(_, s) => if (owner(c).isCorp) corpInstallBlock(c, s) else runnerInstallBlock(c, None)
            case InstallChooseServerPurpose(_) => any
            case InstallDiscountPurpose(_) => any
            case InstallPayingPurpose(_, s, _) => if (owner(c).isCorp && s.any) corpInstallBlock(c, s) else any
            case InstallPayingChooseServerPurpose(_, _, _, _) => any
            case InstallRezPurpose => any
            case RotInstallIgnoringPurpose(_) => any
            case RotInstallPayingPurpose(_, _) => any
            case _ => None
        }
    }

    def restrictOption(a : UserAction) : UserAction = a match {
        case _ : Info => a
        case CorpInstallAction(_, c, s) => grey(a, corpInstallBlock(c, s))
        case ServerChosenAction(_, c, s, _) => grey(a, corpInstallBlock(c, s))
        case ServerDiscountChosenAction(_, c, s, _, _) => grey(a, corpInstallBlock(c, s))
        case ServerChosenPayingAction(_, c, s, _, _) => grey(a, corpInstallBlock(c, s))
        case RotServerChosenAction(_, c, s, _, _, _, _) => grey(a, corpInstallBlock(c, s))
        case MoveServerChosenAction(_, c, s) => grey(a, (!c.data.isIce && !InstallRules.serverAllowed(c.code, |(s))).?("cannot be moved there"))
        case InstallWhereAction(f, c) => grey(a, f.isCorp.?(corpCanInstallSomewhere(c)).|(runnerInstallBlock(c, None)))
        case RunnerInstallAction(_, c, h, _) => grey(a, runnerInstallBlock(c, h))
        case RezAction(_, c, _) => grey(a, rezBlock(c))
        case PlayCardAction(_, c) => grey(a, playBlock(c))
        case CardChosenAction(_, c, RezPurpose(ig)) => grey(a, rezBlock(c, ig))
        case CardChosenAction(_, c, p) if isCorpInstallKind(c) || isRunnerInstallKind(c) => grey(a, installPurposeBlock(c, p))
        case BreakSubAction(_, b, _, _, _) if programUseBlocked(b) => a.!(true, "cannot use programs this run")
        case BreakSubsAction(_, b, _, _, _) if programUseBlocked(b) => a.!(true, "cannot use programs this run")
        case PumpAction(_, b, _) if programUseBlocked(b) => a.!(true, "cannot use programs this run")
        case UseAbilityAction(_, c, _, _) if programUseBlocked(c) => a.!(true, "cannot use programs this run")
        case AccessAbilityAction(_, src, _) if programUseBlocked(src) => a.!(true, "cannot use programs this run")
        case PreventUseAction(_, c, _, _, _) if programUseBlocked(c) => a.!(true, "cannot use programs this run")
        case _ => a
    }

    // Always Be Running: the first [click] of the turn must be spent to run or to play a run event
    def abrActive : Boolean = active.isRunner && phase == "action" && reg("actions") == 0 && reg("click-ability") == 0 && activeCards(Runner).exists(x => x.code == "09041" && !isDisabled(x))

    def abrRestrict(l : $[UserAction]) : $[UserAction] = {
        val canRun = l.exists { case RunMenuAction(Runner) => true ; case _ => false }
        val why = "Always Be Running: run or play a run event first"
        l./{
            case a @ PlayCardAction(Runner, c) if !has(c, "Run") => a.!(true, why)
            case a @ ClickCreditAction(Runner) if canRun => a.!(true, why)
            case a @ ClickDrawAction(Runner) if canRun => a.!(true, why)
            case a @ InstallMenuAction(Runner) if canRun => a.!(true, why)
            case a @ RemoveTagAction(Runner) if canRun => a.!(true, why)
            case a @ UseAbilityAction(Runner, c, i, _) if canRun && impl(c).abilities(c).lift(i).exists(_.click) => a.!(true, why)
            case a => a
        }
    }

    // The Black File: the Corp cannot win the game except by flatlining the Runner
    def corpCannotWin : Boolean = activeCards(Runner).exists(x => x.code == "10099" && !isDisabled(x))

    def restrictContinue(c : Continue) : Continue = c match {
        case Force(GameOverAction(Some(Corp), reason)) if !reason.contains("flatlined") && corpCannotWin =>
            log("The Black File: the Corp cannot win the game except by flatlining the Runner")
            if (agendaPoints(Runner) >= rhWinPoints(Runner))
                GameOverAction(|(Runner), "Runner stole " + agendaPoints(Runner) + " agenda points")
            else
                NextAction
        case Ask(f, l) =>
            val ll = (f.isRunner && abrActive).?(abrRestrict(l)).|(l)./(restrictOption)
            if (ll.exists(x => !x.is[Info]) || l.forall(x => x.is[Info]))
                Ask(f, ll)
            else
                Ask(f, ll :+ RestrictedSkipAction(f))
        case MultiAsk(asks, p) => MultiAsk(asks./(a => Ask(a.faction, (a.faction.isRunner && abrActive).?(abrRestrict(a.actions)).|(a.actions)./(restrictOption))), p)
        case c => c
    }

    def logInstallBlocked(c : Card, r : String) {
        if (owner(c).isCorp)
            log(Corp, "cannot install", hiddenCorpElem(c), "(" + r + ")")
        else
            log(Runner, "cannot install", c, "(" + r + ")")
    }

    // ---- effect-level enforcement (first in the performInternal chain; defined only for the cases it changes) ----------
    def performRestrictions : PartialFunction[Action, Continue] = {
        case RestrictedSkipAction(f) =>
            log(f, "has no legal option")
            NextAction

        // ---- Corp installs
        case CorpInstallEffect(c, s, _) if notInPlay(c) && corpInstallBlock(c, s).any =>
            logInstallBlocked(c, corpInstallBlock(c, s).get)
            NextAction

        // the target remote was emptied (and removed) since the install was decided: keep it
        case a @ CorpInstallEffect(c, Some(s : Remote), _) if notInPlay(c) && !remotes.has(s) =>
            remotes = (remotes :+ s).sortBy(_.n)
            push(a)
            NextAction

        // overinstalling: the previous asset / agenda and the previous region of the server are trashed, the server is kept
        case a @ CorpInstallEffect(c, Some(s), _) if notInPlay(c) && overinstallVictims(c, s).any =>
            val l = overinstallVictims(c, s)
            push(a)
            l.foreach { x =>
                if (isRegion(c) && isRegion(x))
                    log(Corp, "trashes", hiddenCorpElem(x), "(limit 1 region per server)")
                trashCard(x, Corp, false)
            }
            s match {
                case r : Remote if !remotes.has(r) => remotes = (remotes :+ r).sortBy(_.n)
                case _ =>
            }
            NextAction

        case CorpInstallPayingEffect(c, s, _, _) if notInPlay(c) && corpInstallBlock(c, s).any =>
            logInstallBlocked(c, corpInstallBlock(c, s).get)
            NextAction

        case CorpInstallPayingEffect(c, s, d, true) if notInPlay(c) && rezBlock(c, true).any =>
            log(Corp, "cannot rez", hiddenCorpElem(c), "(" + rezBlock(c, true).get + ")")
            push(CorpInstallPayingEffect(c, s, d, false))
            NextAction

        case ServerDiscountChosenAction(f, c, s, _, _) if notInPlay(c) && corpInstallBlock(c, s).any =>
            logInstallBlocked(c, corpInstallBlock(c, s).get)
            NextAction

        case ServerDiscountChosenAction(f, c, s, d, true) if notInPlay(c) && rezBlock(c, true).any =>
            log(Corp, "cannot rez", hiddenCorpElem(c), "(" + rezBlock(c, true).get + ")")
            performInternal(ServerDiscountChosenAction(f, c, s, d, false), NoVoid)

        case RotServerChosenAction(f, c, s, _, _, _, _) if notInPlay(c) && corpInstallBlock(c, s).any =>
            logInstallBlocked(c, corpInstallBlock(c, s).get)
            NextAction

        case RotServerChosenAction(f, c, s, pay, rez, adv, src) if rez > 0 && !c.data.isAgenda && notInPlay(c) && rezBlock(c, rez == 2).any =>
            log(Corp, "cannot rez", hiddenCorpElem(c), "(" + rezBlock(c, rez == 2).get + ")")
            performInternal(RotServerChosenAction(f, c, s, pay, 0, adv, src), NoVoid)

        case CorpInstallIceAtEffect(c, s, _) if notInPlay(c) && corpInstallBlock(c, |(s)).any =>
            logInstallBlocked(c, corpInstallBlock(c, |(s)).get)
            NextAction

        case RHInstallInnermostEffect(c, s) if notInPlay(c) && corpInstallBlock(c, |(s)).any =>
            logInstallBlocked(c, corpInstallBlock(c, |(s)).get)
            NextAction

        case CorpHostInstallEffect(c, _) if notInPlay(c) && corpHostInstallBlock(c).any =>
            logInstallBlocked(c, corpHostInstallBlock(c).get)
            NextAction

        case RotCorpHostInstallEffect(c, _) if notInPlay(c) && corpHostInstallBlock(c).any =>
            logInstallBlocked(c, corpHostInstallBlock(c).get)
            NextAction

        // ---- Runner installs
        case RunnerInstallEffect(c, h, _) if notInPlay(c) && runnerInstallBlock(c, h).any =>
            logInstallBlocked(c, runnerInstallBlock(c, h).get)
            NextAction

        case RunnerInstallPayingEffect(c, h, _) if notInPlay(c) && runnerInstallBlock(c, h).any =>
            logInstallBlocked(c, runnerInstallBlock(c, h).get)
            NextAction

        case RunnerInstallDiscountEffect(c, _) if notInPlay(c) && runnerInstallBlock(c, None).any =>
            logInstallBlocked(c, runnerInstallBlock(c, None).get)
            NextAction

        case InstallDiscountEffect(c, _) if notInPlay(c) && runnerInstallBlock(c, None).any =>
            logInstallBlocked(c, runnerInstallBlock(c, None).get)
            NextAction

        // ---- rezzes that pay before rezzing ignoring costs
        case DiscountRezEffect(c, _) if !isRezzed(c) && rezBlock(c).any =>
            log(Corp, "cannot rez", hiddenCorpElem(c), "(" + rezBlock(c).get + ")")
            NextAction

        case RezDiscountEffect(c, _) if !isRezzed(c) && rezBlock(c).any =>
            log(Corp, "cannot rez", hiddenCorpElem(c), "(" + rezBlock(c).get + ")")
            NextAction

        // ---- plays
        case PlayCardAction(f, c) if playBlock(c).any =>
            log(f, "cannot play", c, "(" + playBlock(c).get + ")")
            NextAction

        case PlayCardFreeEffect(c) if playBlock(c).any =>
            log(owner(c), "cannot play", c, "(" + playBlock(c).get + ")")
            NextAction

        case PlayFreeEffect(c) if playBlock(c).any =>
            log(owner(c), "cannot play", c, "(" + playBlock(c).get + ")")
            NextAction

        case RotPlayEffect(c, _) if playBlock(c).any =>
            log(owner(c), "cannot play", c, "(" + playBlock(c).get + ")")
            NextAction

        case PlayCardEffect(f, c, _, _) if playBlock(c).any =>
            log(f, "cannot play", c, "(" + playBlock(c).get + ")")
            NextAction

        // ---- scoring by card effects (Big Deal) honours "cannot score this turn"
        case ScoreAgendaEffect(c) if hasFlag("cannot-score", c) =>
            log(Corp, "cannot score", hiddenCorpElem(c), "this turn")
            NextAction
    }
}


// ---------------------------------------------------------------------------------------------------------------------
// Deck-building rules per identity (jinteki validator.cljc)
// ---------------------------------------------------------------------------------------------------------------------

object DeckRules {
    val professor = "03029"
    val customBiotics = "03002"
    val singletonIds = $("33093", "33128")       // Nova Initiumia, Ampere

    def isDraft(id : CardData) = id.setName == "Draft"
    def isSingleton(id : CardData) = singletonIds.has(id.code)

    // alliance cards: 0 influence if the deck fulfils the condition
    val allianceSix = $("10013", "10029", "10067", "10068", "10071", "10072", "10094", "10109")

    def allianceFree(c : CardData, rest : $[CardData]) : Boolean = c.code match {
        case x if allianceSix.has(x) => rest.count(d => d.faction == c.faction && !d.has("Alliance")) >= 6
        case "10018" => rest.count(_.isIce) <= 15                      // Mumba Temple
        case "10019" => rest.num >= 50                                  // Museum of History
        case "10038" => rest.count(_.title == "PAD Campaign") == 3      // PAD Factory
        case "10076" => rest.count(_.kind == "Asset") >= 7              // Mumbad Virtual Tour
        case _ => false
    }

    def influenceLimit(id : CardData) : Int = id.influenceLimit.|(Int.MaxValue)

    def influence(id : CardData, rest : $[CardData]) : Int =
        rest.groupBy(_.code).toList./{ case (_, l) =>
            val c = l.head
            val base = (c.faction != id.faction).??(l.num * c.influence.|(0))
            if (base == 0) 0
            else if (id.code == professor && c.isProgram) base - c.influence.|(0)
            else if (allianceFree(c, rest)) 0
            else base
        }.sum

    // too many copies of a title
    def overLimit(id : CardData, rest : $[CardData]) : $[String] =
        if (isDraft(id)) $
        else rest.groupBy(_.title).toList.%{ case (_, l) => l.num > (isSingleton(id).?(1).|(l.head.deckLimit)) }.map(_._1)

    // cards the identity does not allow (agendas of other factions, Custom Biotics' Jinteki ban, singleton agenda limit)
    def check(id : CardData, rest : $[CardData]) : |[String] = {
        val badAgendas = rest.%(c => c.isAgenda && c.faction != "Neutral" && c.faction != id.faction && !isDraft(id) && !isSingleton(id))
        val jinteki = (id.code == customBiotics).??(rest.%(_.faction == "Jinteki"))
        val singletonAgendas = isSingleton(id).??(rest.%(c => c.isAgenda && c.faction != "Neutral").groupBy(_.faction).toList.%{ case (_, l) => l./(_.title).distinct.num > 2 }.flatMap(_._2))
        if (badAgendas.any)
            |("Agendas must be of the identity's faction or neutral: " + badAgendas./(_.title).distinct.join(", "))
        else if (jinteki.any)
            |("Custom Biotics cannot include Jinteki cards: " + jinteki./(_.title).distinct.join(", "))
        else if (singletonAgendas.any)
            |("At most 2 different agendas from each faction: " + singletonAgendas./(_.title).distinct.join(", "))
        else
            None
    }
}
