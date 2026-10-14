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
// Generic mechanics added for the rotated (FFG era) assets (see STATUS-rot-assets.md):
// cards added to a score area "as an agenda", executives, ambushes accessed in Archives, exposing cards, recurring
// credits of Corp cards (rez / install / advance / traces: RA.paysFor, paid by rules-ext-credits.scala), psi game and trace outcome events, Corp cards hosted on
// assets, and a few global restrictions (Blacklist, Genetics Pavilion, The News Now Hour, The Board, Warden Fatuma).
//
// RotAssetRules is mixed into Game like the other extension traits. Besides performRotAssets and rotAssetTriggers it
// uses stackable overrides (each one calls super and is guarded by the card being in play), so that no shared rules
// file had to be edited:
//   performEffects  psi reveal event (RotPsiRevealed), Broadcast Square, Genetics Pavilion draw limit, Blacklist (AddToHandEffect / MoveEffect / RemoveFromGameEffect out of the heap)
//   performCards    Blacklist (installing from the heap)
//   performTurn     Genetics Pavilion / The News Now Hour greying of Runner actions
//   performRun      remembers the unbroken subroutines of an encounter that ends (Chief Slee)
//   resolvePurpose  Blacklist (generic purposes cannot take cards out of the heap)
//   extImpl         cards scored "as an agenda" keep only their agenda value; The Board (-1 point per Runner agenda)
//   subs            Warden Fatuma (bioroid ice gain a first subroutine)
// ---------------------------------------------------------------------------------------------------------------------

// ---- events ---------------------------------------------------------------------------------------------------------
case class RotPsiRevealed(corp : Int, runner : Int) extends Event      // the secretly spent credits of a psi game were revealed (the one psi event of all rotated sets)

// ---- effects --------------------------------------------------------------------------------------------------------
case class RotAsAgendaEffect(card : Card, f : Faction) extends ForcedAction            // add a card to f's score area as an agenda (RA.asAgendaPoints)
case class RotCorpHostInstallEffect(card : Card, host : Card) extends ForcedAction     // install a Corp card hosted on an installed Corp card (Worlds Plaza)
case class RotChooseInstallServerEffect(card : Card, ignoreCost : Boolean) extends ForcedAction with Reaskable  // choose a server and install (paying the cost unless ignored)
case class RotBadPubNowEffect(n : Int) extends ForcedAction                            // take bad publicity without the Broadcast Square trace
case class RotTrashIfEmptyEffect(card : Card, kind : String) extends ForcedAction       // trash the card if it has no counters of kind left


trait RotAssetRules extends EffectRules with TurnRules with CardRules with RunRules with PHTAIRules with RWRRules { game : Game =>

    var rotEncounterIce : |[Card] = None       // the ice whose encounter just ended
    var rotEncounterUnbroken = 0              // and its unbroken subroutines at that moment

    def raOf(c : Card) : |[RA] = CardImpls(c.code) match {
        case r : RA => |(r)
        case _ => None
    }

    // installed, rezzed, not disabled Corp cards with the given code
    def rotLive(code : String) : $[Card] = installedCards(Corp).%(c => c.code == code && isRezzed(c) && !isDisabled(c))

    def rotBlacklisted(c : Card) : Boolean = zone(c) == DiscardZone(Runner) && rotLive("08036").any

    def rotPavilionLimit : Boolean = active.isRunner && reg("drawn-Runner") >= 2 && rotLive("08113").any

    // ---- greyed out Runner actions ----------------------------------------------------------------------------------
    def rotRestrict(a : UserAction) : UserAction = a match {
        case ClickDrawAction(Runner) if rotPavilionLimit => a.!(true, "Genetics Pavilion")
        case PlayCardAction(Runner, c) if has(c, "Current") && rotLive("06045").any => a.!(true, "The News Now Hour")
        case _ => a
    }

    def rotFixAsk(c : Continue) : Continue = c match {
        case Ask(f, l) => Ask(f, l./(rotRestrict))
        case MultiAsk(asks, p) => MultiAsk(asks./(a => Ask(a.faction, a.actions./(rotRestrict))), p)
        case c => c
    }

    // ---- stackable overrides ----------------------------------------------------------------------------------------
    override def performEffects : PartialFunction[Action, Continue] = {
        val base = super.performEffects
        ({
            case a @ PsiResolveAction(c, r, _, _) =>
                val x = base(a)
                trigger(RotPsiRevealed(c, r))
                x

            case BadPubEffect(n) if n > 0 && rotLive("04112").any =>
                log(Corp, "would take", n.hl, "bad publicity: Broadcast Square initiates a trace")
                push(TraceEffect(3, $(MessageEffect(Corp, "prevents the bad publicity (Broadcast Square)")), $(RotBadPubNowEffect(n))))
                NextAction

            case RotBadPubNowEffect(n) =>
                base(BadPubEffect(n))

            case DrawNowEffect(Runner, n) if n > 0 && active.isRunner && rotLive("08113").any && reg("drawn-Runner") + n > 2 =>
                val m = (2 - reg("drawn-Runner")).atLeast(0)
                log("Genetics Pavilion: the Runner cannot draw more than 2 cards this turn")
                if (m > 0)
                    base(DrawNowEffect(Runner, m))
                else
                    NextAction

            case AddToHandEffect(c) if rotBlacklisted(c) =>
                log(c, "cannot leave the heap (Blacklist)")
                NextAction

            case MoveEffect(c, z, _) if z != DiscardZone(Runner) && rotBlacklisted(c) =>
                log(c, "cannot leave the heap (Blacklist)")
                NextAction

            case RemoveFromGameEffect(c) if rotBlacklisted(c) =>
                log(c, "cannot leave the heap (Blacklist)")
                NextAction
        } : PartialFunction[Action, Continue]) orElse base
    }

    override def performCards : PartialFunction[Action, Continue] = {
        val base = super.performCards
        ({
            case RunnerInstallEffect(c, _, _) if rotBlacklisted(c) =>
                log(c, "cannot leave the heap (Blacklist)")
                NextAction
        } : PartialFunction[Action, Continue]) orElse base
    }

    override def performTurn : PartialFunction[Action, Continue] = {
        val base = super.performTurn
        ({
            case a @ ActionPhaseAction(Runner) if rotPavilionLimit =>
                rotFixAsk(base(a))

            case a @ PlayMenuAction(Runner) if rotLive("06045").any =>
                rotFixAsk(base(a))
        } : PartialFunction[Action, Continue]) orElse base
    }

    override def performRun : PartialFunction[Action, Continue] = {
        val base = super.performRun
        ({
            case a @ EncounterEndsAction(c) =>
                rotEncounterIce = |(c)
                rotEncounterUnbroken = unbrokenSubs(c).num
                base(a)
        } : PartialFunction[Action, Continue]) orElse base
    }

    override def resolvePurpose(f : Faction, l : $[Card], p : Purpose) : Continue = p match {
        case p : CardPurpose => super.resolvePurpose(f, l, p)
        case _ =>
            val ll = l.%!(rotBlacklisted)
            if (ll.num < l.num)
                log("Blacklist: cards cannot leave the heap")
            super.resolvePurpose(f, ll, p)
    }

    override def extImpl(c : Card) : CardImpl = {
        val i = super.extImpl(c)
        location.get(c) match {
            case Some(ScoredZone(f)) =>
                val j = CardImpls(c.code) match {
                    case r : RA => new AsAgendaImpl(r)
                    case _ => i
                }
                if (f.isRunner && installedCards(Corp).exists(x => x.code == "07011" && isRezzed(x) && !isDisabled(x)))
                    new MinusOnePointImpl(j)
                else
                    j
            case _ => i
        }
    }

    override def subs(ice : Card) : $[Sub] = {
        val l = super.subs(ice)
        if (isRezzed(ice) && has(ice, "Bioroid") && ice.data.isIce && rotLive("21093").any)
            Sub("The Runner loses [click], if able (Warden Fatuma)", LoseClicksEffect(Runner, 1)) +: l
        else
            l
    }

    // ---- game-level triggers ----------------------------------------------------------------------------------------
    // registers are counted after the cards looked at the event: reg(k) == 0 means "the first time this turn"
    def rotAssetTriggers(e : Event) : $[ForcedAction] = {
        val l : $[ForcedAction] = e match {
            // ambushes accessed in Archives (the engine resolves onAccess outside Archives only)
            case Accessed(c) if zone(c) == DiscardZone(Corp) => raOf(c).$./~(r => r.accessInArchives.?(r.onAccess(c)).|($))
            // executives trashed while accessed
            case Trashed(c, Runner, true) if owner(c).isCorp && raOf(c).exists(_.executive) => $(RotAsAgendaEffect(c, Runner))
            case Exposed(l) => l.%(isInstalled)./~(c => raOf(c).$./~(_.onExpose(c)))
            case _ => $
        }

        e match {
            case Rezzed(c) if c.data.isIce => regInc("rot-ice-rezzed")
            case Installed(Runner, _) => regInc("rot-runner-installs")
            case ActionTaken(Runner, k) =>
                regInc("rot-runner-clicks")
                if (k == "credit")
                    regInc("rot-runner-click-credit")
            case Played(Runner, _) if active.isRunner && phase == "action" && run.none => regInc("rot-runner-clicks")
            case SubsBroken(ice, _, _) if has(ice, "AP") => regInc("rot-ap-broken")
            case RotPsiRevealed(_, _) => regInc("rot-psi")
            case _ =>
        }

        // Malia Z0L0K4: the blanked resource gets its abilities back when Malia is no longer rezzed
        if (flags.exists(_.scope == "rot-malia"))
            flags = flags.%!(f => f.scope == "rot-malia" && !installedCards(Corp).exists(m => m.code == "21069" && isRezzed(m) && state(m).target == f.card))

        l
    }

    // ---- effects ------------------------------------------------------------------------------------------------------
    def performRotAssets : PartialFunction[Action, Continue] = {
        case RotAsAgendaEffect(c, f) =>
            if (location.contains(c) && !zone(c).is[ScoredZone]) {
                if (isInstalled(c))
                    uninstall(c)
                moveTo(c, ScoredZone(f))
                state(c).seen = true
                cleanupRemotes()
                val n = raOf(c)./(_.asAgendaPoints).|(0)
                log(f, "adds", c, "to the score area as an agenda worth", n.hl, "agenda point" + (n != 1).??("s"))
                push(CheckWinAction)
            }
            NextAction

        case RotCorpHostInstallEffect(c, h) =>
            if (!location.contains(c) || location.get(c).exists(inPlayZone) || !isInstalled(h))
                NextAction
            else {
                lastInstallFrom = zone(c)
                moveTo(c, HostedZone(h))
                state(c).installedTurn = turn
                state(c).rezzed = false
                state(c).uninstalled = false
                log(Corp, "installs a card hosted on", h)
                regInc("installed")
                pushAll(impl(c).onInstall(c))
                trigger(Installed(Corp, c))
                NextAction
            }

        case RotChooseInstallServerEffect(c, ig) =>
            if (!location.contains(c) || location.get(c).exists(inPlayZone))
                NextAction
            else {
                val l = (c.data.isIce || c.data.isUpgrade).?(servers).|(remotes)
                val options = l./(s => ServerChosenAction(Corp, c, |(s), ig).!(!ig && !canSpend(Corp, installCost(c, |(s)), Spend.install(c, |(s))), "not enough credits")) :+
                    ServerChosenAction(Corp, c, None, ig).!(!canCreateRemote, "remote server limit").!(!ig && !canSpend(Corp, installCost(c, None), Spend.install(c)), "not enough credits")
                if (options.exists(a => !a.is[Unavailable]))
                    Ask(Corp).group("Install " ~ cardElem(c)).add(options)
                else {
                    log(Corp, "cannot install", hiddenCorpElem(c))
                    NextAction
                }
            }

        case RotTrashIfEmptyEffect(c, k) =>
            if (isInstalled(c) && counter(c, k) <= 0)
                trashCard(c, owner(c), false)
            NextAction
    }
}


// ---------------------------------------------------------------------------------------------------------------------
// Implementation wrappers
// ---------------------------------------------------------------------------------------------------------------------

// a card in a score area as an agenda: only its agenda value (and its pending card actions) remain
class AsAgendaImpl(raw : RA) extends CardImpl {
    def code = raw.code
    override def agendaPointsBonus(self : Card)(implicit g : Game) = raw.agendaPointsBonus(self)
    override def perform(a : CardAction)(implicit g : Game) = raw.perform(a)
    override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = raw.chosen(p, cards)
}

// an agenda in the Runner's score area while The Board is rezzed
class MinusOnePointImpl(raw : CardImpl) extends DelegatingImpl(raw) {
    override def agendaPointsBonus(self : Card)(implicit g : Game) = raw.agendaPointsBonus(self) - 1
}


// ---------------------------------------------------------------------------------------------------------------------
// Base class for the rotated assets
// ---------------------------------------------------------------------------------------------------------------------

abstract class RA(c : String) extends NC(c) {
    def asAgendaPoints : Int = 0                                                                     // value when added to a score area as an agenda
    def executive : Boolean = false                                                                  // trashed while accessed: the Runner adds it as a 2-point agenda
    def accessInArchives : Boolean = false                                                           // onAccess also resolves when accessed in Archives
    def exposeOptions(self : Card, target : Card)(implicit g : Game) : $[Alt] = $                    // Corp responses when target would be exposed
    def onExpose(self : Card)(implicit g : Game) : $[ForcedAction] = $                               // when this card is exposed while installed
    def recurring(self : Card, kind : String, card : Card)(implicit g : Game) : Boolean = false      // hosted credits usable for kind: rez / install / advance / trace

    override def agendaPointsBonus(self : Card)(implicit g : Game) = g.zone(self).is[ScoredZone].?(asAgendaPoints).|(0)
    // recurring credits pay for rez / install / advance / trace (rules-ext-credits.scala)
    override def paysFor(self : Card, spend : Spend)(implicit g : Game) = g.isInstalled(self) && (spend.kind match {
        case "rez" | "install" | "advance" => spend.card.exists(c => recurring(self, spend.kind, c))
        case "trace" => recurring(self, "trace", self)
        case _ => false
    })

    def unprotectedServer(self : Card)(implicit g : Game) = g.serverOf(self).exists(s => g.ice(s).none)

    // "When your turn begins, take n credits from this card" + trash when empty
    def campaign(self : Card, e : Event, n : Int)(implicit g : Game) : $[ForcedAction] = trashOnEmpty(self, e, "credit") ++ (e match {
        case TurnBegins(Corp) if g.counter(self, "credit") > 0 => $(TakeCreditsEffect(Corp, self, n))
        case _ => $
    })

    // recurring credits refill when the Corp's turn begins
    def refill(self : Card, e : Event, n : Int)(implicit g : Game) : $[ForcedAction] = e match {
        case TurnBegins(Corp) => $(RefillCreditsEffect(self, n))
        case _ => $
    }

    // "When the Runner accesses this card while it is installed, you may pay cost to ..."
    def ambush(self : Card, cost : Int, prompt : String, effects : $[ForcedAction])(implicit g : Game) : $[ForcedAction] =
        (g.isInstalled(self) && g.credits(Corp) >= cost).$(OptionalEffect(Corp, prompt, (cost > 0).$(LoseCreditsEffect(Corp, cost)) ++ effects, $))

    def sortedDeck(p : Card => Boolean)(implicit g : Game) : $[Card] = g.deck(Corp).%(p).sortBy(_.title)

    def corpInstallableNow(c : Card)(implicit g : Game) = corpInstallable(c) && g.impl(c).canInstall(c)
}
