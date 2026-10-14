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
// Generic mechanics added for the rotated (FFG era) agendas and upgrades (see STATUS-rot-agendas-upgrades.md)
//
// Besides the usual effects / events, RotAgendaRules refines a few engine steps by overriding the rule traits it extends
// (stackable-trait style: every override handles its own cases and delegates the rest to super), so that no shared rule
// file had to be edited:
//   performTurn      install restrictions ("install only in the root of HQ"), "you cannot advance cards this turn"
//   performCards     install restrictions in server choices, "cards cannot be installed until the end of the run"
//   performAccess    extra steal costs that are not Cost records (net damage, shuffle installed cards), "must trash",
//                    the Corp choosing the cards accessed from HQ
//   performEffects   trace base strength bonuses, link treated as 0, the TraceResolved event, meat damage bonus,
//                    the AdvancementPlaced event
//   performAbilities additional costs to jack out
//   windowOptions / abilityOptions   unpayable jack out greyed out, Runner paid abilities blocked (Navi Mumbai City Grid)
//   stealCostOf / accessProtected    persistent steal costs / restrictions of trashed upgrades, "cannot steal this card"
//   resolvePurpose   the generic purposes defined here
// ---------------------------------------------------------------------------------------------------------------------

// ---- new events -----------------------------------------------------------------------------------------------------
case class PassedAllIce(server : Server) extends Event                   // the Runner passed all ice protecting the attacked server (or it has none)
case class TraceResolved(successful : Boolean, spent : Int) extends Event // a trace resolved; spent = credits the Runner spent on link
case class AdvancementPlaced(card : Card, n : Int) extends Event         // advancement counters were placed on a card (not by advancing it)

// ---- new effects ----------------------------------------------------------------------------------------------------
case class FaceupInstallEffect(card : Card) extends ForcedAction                                   // "install this agenda faceup" (it becomes active like a rezzed card)
case object CancelBypassEffect extends ForcedAction                                                // the current ice cannot be bypassed (Underway Grid)
case class RotStealPaidAction(card : Card) extends ForcedAction                                    // steal after the non-Cost additional costs were paid
case class RotJackOutPaidAction(f : Faction) extends ForcedAction                                  // jack out after the additional costs were paid
case class CorpInstallIceAtEffect(card : Card, server : Server, index : Int) extends ForcedAction   // install ice at a position (0 = innermost), ignoring all costs
case class SwapRootEffect(a : Card, b : Card) extends ForcedAction                                 // swap two cards installed in the roots of servers
case class CorpHostInstallEffect(card : Card, host : Card) extends ForcedAction                    // install a Corp card hosted on another installed Corp card (unrezzed, ignoring costs)
case class LoseAllCreditsEffect(f : Faction) extends ForcedAction
case class RezIgnoringAllCostsEffect(card : Card) extends ForcedAction                             // rez a non-agenda installed card ignoring all costs (no-op otherwise)
case class SetPositionAfterInstallEffect(card : Card, server : Server, index : Int) extends ForcedAction
case class RotSwapIceEffect(a : Card, b : Card) extends ForcedAction                                // swap two installed pieces of ice (logged without naming unrezzed ice)

// ---- generic purposes -----------------------------------------------------------------------------------------------
trait RotPurpose extends Purpose
case object CorpChosenHQAccessPurpose extends RotPurpose                     // the Corp chose which cards the Runner accesses from HQ (Dedicated Neural Net)
case class ShuffleInstalledIntoStackPurpose(f : Faction) extends RotPurpose  // the chosen installed cards are shuffled into their owner's deck


trait RotAgendaRules extends TurnRules with CardRules with AccessRules with EffectRules with AbilityRules with StartupRules { game : Game =>

    // snapshots for "installs a card from the heap" / "searches the stack" (taken at the previous event)
    var rotHeapPrev : $[Card] = $
    var rotStackPrev : $[Card] = $

    // ---- helpers ----------------------------------------------------------------------------------------------------------
    def rotImpl(c : Card) : |[RA] = CardImpls(c.code) match {
        case r : RA => |(r)
        case _ => None
    }

    // active cards implemented in the rotated agenda / upgrade files
    def rotActive : $[(Card, RA)] = allActive.%!(isDisabled)./~(c => rotImpl(c)./(r => (c, r)))

    // the server a Corp card is in, or the central server whose zone it is in (HQ / R&D / Archives)
    def rotFromServer(c : Card) : |[Server] = zoneServer(zone(c))

    def rotRunOn(s : Server) = run.exists(r => !r.ended && r.server == s)

    def rotRunKey : String = turn + ":" + reg("runs")

    // the stack was searched since the previous event: cards left it from below the top, or its order changed (shuffle)
    def rotStackSearched : Boolean = turn >= 1 && {
        val prev = rotStackPrev
        val cur = deck(Runner)
        val removed = prev.%!(cur.has)
        val keptOrder = prev.%(cur.has)
        val curOld = cur.%(prev.has)
        (removed.any && prev.take(removed.num) != removed) || keptOrder != curOld
    }

    def rotInstallAllowed(c : Card, s : |[Server]) : Boolean = rotImpl(c).forall(_.installOnly(c, s)(this))

    // net damage the Runner suffers as an additional cost to steal agenda c
    def rotStealDamage(c : Card) : Int =
        rotActive./{ case (x, r) => r.stealDamage(x, c)(this) }.sum +
        (!allActive.has(c)).??(rotImpl(c)./(_.stealDamage(c, c)(this)).|(0)) +
        flagsOf("rot-steal-net").%(f => f.server.exists(s => rotFromServer(c).has(s)))./(_ => 2).sum

    // installed Runner cards the Runner shuffles into the stack as an additional cost to steal agenda c (Degree Mill)
    def rotStealShuffle(c : Card) : Int = (!allActive.has(c)).??(rotImpl(c)./(_.stealShuffleInstalled(c, c)(this)).|(0))

    def rotStealExtra(c : Card) = rotStealDamage(c) > 0 || rotStealShuffle(c) > 0

    def rotCanPayStealExtra(c : Card) = runnerInstalled.num >= rotStealShuffle(c)

    // additional costs to jack out: "program" (trash an installed program), "grip" (trash a card from the grip)
    def rotJackOutCosts : $[String] = run.exists(r => !r.ended).??(rotActive./~{ case (x, r) => r.jackOutCosts(x)(this) })

    def rotCanPayJackOut : Boolean = {
        val l = rotJackOutCosts
        runnerInstalledOf("Program").num >= l.count("program") && hand(Runner).num >= l.count("grip")
    }

    def rotTraceBonus : Int = rotActive./{ case (x, r) => r.traceBonus(x)(this) }.sum

    def rotLinkZero : Boolean = rotActive.exists { case (x, r) => r.linkZero(x)(this) }

    def rotMeatBonus : Int = rotActive./{ case (x, r) => r.meatBonus(x)(this) }.sum

    def rotBlocksRunnerAbility(c : Card) : Boolean = owner(c).isRunner && !isIcebreaker(c) && rotActive.exists { case (x, r) => r.blocksRunnerAbilities(x)(this) }

    def rotMustTrash(c : Card) : Boolean = isInstalled(c) && rotImpl(c).exists(_.mustTrashOnAccess(c)(this))

    def rotCorpChoosesHQAccess : Boolean = run.exists(r => !r.ended) && hasFlag("rot-corp-chooses-hq")

    // ---- ask filters --------------------------------------------------------------------------------------------------
    def rotMapAsk(c : Continue, f : UserAction => UserAction) : Continue = c match {
        case Ask(ff, l) => Ask(ff, l./(f))
        case MultiAsk(asks, p) => MultiAsk(asks./(a => Ask(a.faction, a.actions./(f))), p)
        case c => c
    }

    def rotRestrictInstall(c : Continue) : Continue = rotMapAsk(c, {
        case a @ CorpInstallAction(_, card, s) if !rotInstallAllowed(card, s) => a.!(true, "cannot be installed there")
        case a @ ServerChosenAction(_, card, s, _) if !rotInstallAllowed(card, s) => a.!(true, "cannot be installed there")
        case a => a
    })

    // ---- engine refinements -------------------------------------------------------------------------------------------
    override def performTurn : PartialFunction[Action, Continue] = ({
        case a @ InstallWhereAction(f, c) if f.isCorp && rotImpl(c).any =>
            rotRestrictInstall(super.performTurn(a))

        case a @ ActionPhaseAction(f) if f.isCorp && reg("rot-no-advance") > 0 =>
            rotMapAsk(super.performTurn(a), {
                case x @ AdvanceMenuAction(_) => x.!(true, "cannot advance this turn")
                case x => x
            })
    } : PartialFunction[Action, Continue]) orElse super.performTurn

    override def performCards : PartialFunction[Action, Continue] = ({
        case a @ ChooseServerEffect(f, c, _, l, _) if rotImpl(c).any =>
            val r = rotRestrictInstall(super.performCards(a))
            // keep at least one usable option
            r match {
                case Ask(_, ll) if ll.exists(x => !x.is[Unavailable] && !x.is[Info]) => r
                case _ => super.performCards(a)
            }

        case CorpInstallEffect(c, _, _) if hasFlag("rot-no-install") && run.exists(r => !r.ended) && !location.get(c).exists(inPlayZone) =>
            log("Cards cannot be installed until the end of the run")
            NextAction

        case RunnerInstallEffect(c, _, _) if hasFlag("rot-no-install") && run.exists(r => !r.ended) && !location.get(c).exists(inPlayZone) =>
            log("Cards cannot be installed until the end of the run")
            NextAction
    } : PartialFunction[Action, Continue]) orElse super.performCards

    private def baseAccess(a : Action) : Continue = super.performAccess(a)

    override def performAccess : PartialFunction[Action, Continue] = ({
        case a @ AccessDecisionAction(c) if rotStealExtra(c) || rotMustTrash(c) =>
            val r = baseAccess(a)
            val canTrash = r match {
                case Ask(_, l) => l.exists(_.is[PayTrashAction])
                case _ => false
            }
            rotMapAsk(r, {
                case x @ StealAction(_, cc) if !rotCanPayStealExtra(cc) => x.!(true, "cannot pay the additional cost")
                case x @ NoAccessAction(_, cc) if canTrash && rotMustTrash(cc) => x.!(true, "must be trashed")
                case x => x
            })

        case StealAction(f, c) if rotStealExtra(c) =>
            val d = rotStealDamage(c)
            val k = rotStealShuffle(c)
            log(Runner, "pays the additional cost to steal", c)
            if (k > 0)
                push(ChooseCardsEffect(Runner, runnerInstalled, k, k, ShuffleInstalledIntoStackPurpose(Runner), "Choose " + k + " installed cards to shuffle into the stack"))
            if (d > 0)
                push(DamageEffect("net", d))
            push(RotStealPaidAction(c))
            NextAction

        case AccessServerAction(HQ) if rotCorpChoosesHQAccess && hand(Corp).any =>
            run match {
                case Some(r) if !r.ended =>
                    val n = (1 + r.accessBonusFor("HQ") + r.accessBonusFor("total")).atMost(hand(Corp).num)
                    r.toAccess = root(HQ)./~(c => c +: allHosted(c))
                    log(Corp, "chooses the cards the Runner accesses from HQ")
                    push(ChooseCardsEffect(Corp, hand(Corp), n, n, CorpChosenHQAccessPurpose, "Choose " + n + " cards in HQ for the Runner to access"))
                    NextAction
                case _ => NextAction
            }
    } : PartialFunction[Action, Continue]) orElse super.performAccess

    override def performEffects : PartialFunction[Action, Continue] = ({
        case TraceEffect(base, s, fl) =>
            regInc("rot-traces")
            val bonus = rotTraceBonus
            if (bonus != 0)
                log("The base trace strength is increased by", bonus.hl)
            super.performEffects(TraceEffect(base + bonus, s, fl))

        case TraceRunnerBoostAction(strength, s, fl) if rotLinkZero && reg("rot-traces") <= 1 =>
            Ask(Runner).group("Trace strength " ~ strength.hl ~ ", your link is treated as " ~ 0.hl ~ ", boost by paying credits").add(0.to(credits(Runner)).$./(n => TraceRunnerAction(Runner, n)))

        case TraceRunnerAction(f, n) =>
            credits += Runner -> (credits(Runner) - n)
            val zero = rotLinkZero && reg("rot-traces") <= 1
            if (zero)
                log("The Runner's link is treated as", 0.hl, "for the first trace this turn")
            val l = zero.?(0).|(link) + n
            log(Runner, "pays", n.credits, "for link strength", l.hl)
            val ok = traceStrength > l
            if (ok)
                log("The trace is", "successful".hl)
            else
                log("The trace is", "unsuccessful".hl)
            pushAll(ok.?(traceSuccess).|(traceFail) :+ TriggerEventAction(TraceResolved(ok, n)))
            NextAction

        case DamageEffect("meat", n) if n > 0 && rotMeatBonus > 0 =>
            val b = rotMeatBonus
            log("The meat damage is increased by", b.hl)
            super.performEffects(DamageEffect("meat", n + b))

        case a @ PlaceAdvancementEffect(c, n) =>
            val r = super.performEffects(a)
            if (n > 0)
                trigger(AdvancementPlaced(c, n))
            r
    } : PartialFunction[Action, Continue]) orElse super.performEffects

    private def baseAbilities(a : Action) : Continue = super.performAbilities(a)

    override def performAbilities : PartialFunction[Action, Continue] = ({
        case JackOutAction(f) if rotJackOutCosts.any =>
            if (!rotCanPayJackOut) {
                log(Runner, "cannot pay the additional cost to jack out")
                NextAction
            }
            else {
                val l = rotJackOutCosts
                val programs = l.count("program")
                val grip = l.count("grip")
                pushAll(
                    (programs > 0).$(ChooseCardsEffect(Runner, runnerInstalledOf("Program"), programs, programs, TrashPurpose(Runner), "Jack out: choose " + programs + " installed program to trash")) ++
                    (grip > 0).$(ChooseCardsEffect(Runner, hand(Runner), grip, grip, TrashFromHandPurpose(Runner), "Jack out: choose " + grip + " card from the grip to trash")) :+
                    RotJackOutPaidAction(f))
                NextAction
            }
    } : PartialFunction[Action, Continue]) orElse super.performAbilities

    override def windowOptions(f : Faction, kind : String) : $[UserAction] = {
        val l = super.windowOptions(f, kind)
        if (f.isRunner && rotJackOutCosts.any && !rotCanPayJackOut)
            l./{
                case a @ JackOutAction(_) => a.!(true, "cannot pay the additional cost")
                case a => a
            }
        else
            l
    }

    override def abilityOptions(f : Faction, ctx : String, allowClick : Boolean) : $[UserAction] = {
        val l = super.abilityOptions(f, ctx, allowClick)
        if (f.isRunner && run.exists(r => !r.ended))
            l./{
                case a @ UseAbilityAction(_, c, _, _) if rotBlocksRunnerAbility(c) => a.!(true, "blocked by Navi Mumbai City Grid")
                case a => a
            }
        else
            l
    }

    override def stealCostOf(c : Card) : $[Cost] =
        super.stealCostOf(c) ++
        flagsOf("rot-steal-click").%(f => f.server.exists(s => rotFromServer(c).has(s)))./(_ => ClickCost(1) : Cost) ++
        flagsOf("rot-steal-credits").%(f => f.server.exists(s => rotFromServer(c).has(s)))./(_ => CreditCost(5) : Cost) ++
        // a no-op cost so that the access prompt is shown (the steal is not automatic) when there are other additional costs
        rotStealExtra(c).$(CreditCost(0) : Cost)

    override def accessProtected(c : Card) : Boolean =
        super.accessProtected(c) || hasFlag("rot-no-steal", c) ||
        (c.data.isAgenda && flagsOf("rot-no-steal-server").exists(f => f.server.exists(s => rotFromServer(c).has(s))) && !scored(Runner).exists(_.title == c.title))

    override def resolvePurpose(f : Faction, l : $[Card], p : Purpose) : Continue = p match {
        case CorpChosenHQAccessPurpose =>
            run.foreach { r =>
                r.toAccess = r.toAccess ++ l
            }
            push(AccessNextAction)
            NextAction

        case ShuffleInstalledIntoStackPurpose(ff) =>
            l.foreach { c =>
                uninstall(c)
                moveTo(c, DeckZone(owner(c)))
            }
            if (l.any) {
                log(ff, "shuffles", l./(cardElem).comma, "into", ff.isCorp.?("R&D").|("the stack"))
                push(ShuffleDeckEffect(ff))
            }
            push(CheckMemoryAction)
            NextAction

        case _ => super.resolvePurpose(f, l, p)
    }

    // ---- game-level triggers ----------------------------------------------------------------------------------------------
    def rotAgendaTriggers(e : Event) : $[ForcedAction] = {
        val out : $[ForcedAction] = e match {
            // "whenever the Runner passes all the ice protecting this server"
            case PassIce(c) => run.%(r => !r.ended && r.position == 0 && ice(r.server).has(c))./(r => TriggerEventAction(PassedAllIce(r.server)) : ForcedAction).$
            case RunInitiated(s) if ice(s).none => $(TriggerEventAction(PassedAllIce(s)))
            // icebreakers used to break subroutines during the run (Will-o'-the-Wisp)
            case SubsBroken(_, _, b) =>
                addFlag(runFlag("rot-breaker", |(b)))
                $
            // "when the Runner accesses this card" abilities that also apply in Archives
            case Accessed(c) if zone(c) == DiscardZone(Corp) => rotImpl(c)./~(_.onArchivesAccess(c)(this))
            // persistent "whenever the Runner trashes a card from this server" (Overseer Matrix trashed during the run)
            case Trashed(c, Runner, _) if owner(c).isCorp =>
                val from = zoneServer(lastTrashedFrom)
                flagsOf("rot-overseer").%(f => f.server.exists(s => from.has(s)) && credits(Corp) >= 1)./(f =>
                    OptionalEffect(Corp, "Overseer Matrix: pay 1 credit to give the Runner 1 tag?", $(LoseCreditsEffect(Corp, 1), MessageEffect(Corp, "uses Overseer Matrix"), TagsEffect(1)), $) : ForcedAction)
            case _ => $
        }
        rotHeapPrev = discard(Runner)
        rotStackPrev = deck(Runner)
        out
    }

    // ---- effects ------------------------------------------------------------------------------------------------------
    def performRotAgendas : PartialFunction[Action, Continue] = {
        case FaceupInstallEffect(c) =>
            if (isInstalled(c) && !isRezzed(c)) {
                state(c).rezzed = true
                state(c).seen = true
                log(c, "is installed faceup")
            }
            NextAction

        case CancelBypassEffect =>
            run.foreach { r =>
                if (r.bypass) {
                    r.bypass = false
                    log("The ice cannot be bypassed")
                }
            }
            NextAction

        case RotStealPaidAction(c) =>
            if (location.contains(c) && !zone(c).is[ScoredZone] && run.exists(r => !r.ended))
                baseAccess(StealAction(Runner, c))
            else {
                if (run.exists(r => !r.ended))
                    push(AccessDoneAction(c))
                NextAction
            }

        case RotJackOutPaidAction(f) =>
            if (run.exists(r => !r.ended))
                baseAbilities(JackOutAction(f))
            else
                NextAction

        case CorpInstallIceAtEffect(c, s, index) =>
            push(CorpInstallEffect(c, |(s), true), SetPositionAfterInstallEffect(c, s, index))
            NextAction

        case SetPositionAfterInstallEffect(c, s, index) =>
            if (zone(c) == IceZone(s)) {
                val l = ice(s).but(c)
                val k = index.atLeast(0).atMost(l.num)
                zones += IceZone(s) -> (l.take(k) ++ $(c) ++ l.drop(k))
                run.foreach { r =>
                    if (!r.ended && r.server == s && k < r.position)
                        r.position += 1
                }
                log(hiddenCorpElem(c), "is installed at position", (k + 1).hl, "from the inside of", s)
            }
            NextAction

        case RotSwapIceEffect(a, b) =>
            (zone(a), zone(b)) match {
                case (IceZone(sa), IceZone(sb)) if a != b =>
                    val la = ice(sa)
                    val lb = ice(sb)
                    if (sa == sb)
                        zones += IceZone(sa) -> la.updated(la.indexOf(a), b).updated(la.indexOf(b), a)
                    else {
                        zones += IceZone(sa) -> la.updated(la.indexOf(a), b)
                        zones += IceZone(sb) -> lb.updated(lb.indexOf(b), a)
                        location += a -> IceZone(sb)
                        location += b -> IceZone(sa)
                    }
                    log(Corp, "swaps", hiddenCorpElem(a), "and", hiddenCorpElem(b))
                case _ =>
            }
            NextAction

        case SwapRootEffect(a, b) =>
            (zone(a), zone(b)) match {
                case (za @ RootZone(sa), zb @ RootZone(sb)) if a != b =>
                    val la = root(sa)
                    val lb = root(sb)
                    zones += za -> la.updated(la.indexOf(a), b)
                    zones += zb -> lb.updated(lb.indexOf(b), a)
                    location += a -> zb
                    location += b -> za
                    log(Corp, "swaps", hiddenCorpElem(a), "and", hiddenCorpElem(b))
                case _ =>
            }
            NextAction

        case CorpHostInstallEffect(c, h) =>
            if (isInstalled(h) && !location.get(c).exists(inPlayZone)) {
                place(c, HostedZone(h))
                state(c).uninstalled = false
                state(c).rezzed = false
                state(c).installedTurn = turn
                log(Corp, "installs a card hosted on", h)
                regInc("installed")
                trigger(Installed(Corp, c))
            }
            NextAction

        case LoseAllCreditsEffect(f) =>
            if (credits(f) > 0)
                push(LoseCreditsEffect(f, credits(f)))
            NextAction

        case RezIgnoringAllCostsEffect(c) =>
            if (isInstalled(c) && !isRezzed(c) && !c.data.isAgenda)
                rezCard(c, true)
            NextAction
    }
}


// ---------------------------------------------------------------------------------------------------------------------
// Base class of the rotated agenda / upgrade implementations: the Startup helpers plus the hooks read by RotAgendaRules
// ---------------------------------------------------------------------------------------------------------------------

abstract class RA(code : String) extends Startup(code) {
    // ---- hooks read by RotAgendaRules -------------------------------------------------------------------------------
    def installOnly(self : Card, server : |[Server])(implicit g : Game) : Boolean = true             // server restriction (None = new remote)
    def stealDamage(self : Card, agenda : Card)(implicit g : Game) : Int = 0                          // net damage as an additional cost to steal agenda
    def stealShuffleInstalled(self : Card, agenda : Card)(implicit g : Game) : Int = 0                // installed cards shuffled into the stack to steal agenda (own)
    def jackOutCosts(self : Card)(implicit g : Game) : $[String] = $                                  // "program" / "grip"
    def traceBonus(self : Card)(implicit g : Game) : Int = 0                                          // base trace strength bonus
    def linkZero(self : Card)(implicit g : Game) : Boolean = false                                     // the Runner's link is 0 for the first trace each turn
    def meatBonus(self : Card)(implicit g : Game) : Int = 0                                           // meat damage increase
    def blocksRunnerAbilities(self : Card)(implicit g : Game) : Boolean = false                       // the Runner cannot use paid abilities of non-icebreakers
    def mustTrashOnAccess(self : Card)(implicit g : Game) : Boolean = false                           // the Runner must trash it when accessed while installed, if able
    def onArchivesAccess(self : Card)(implicit g : Game) : $[ForcedAction] = $                        // access ability that also applies in Archives

    // ---- helpers ----------------------------------------------------------------------------------------------------------
    def corpScored(self : Card)(implicit g : Game) = g.zone(self) == ScoredZone(Corp)

    def scoredAnywhere(self : Card)(implicit g : Game) = g.zone(self).is[ScoredZone]

    def rezzedHere(self : Card)(implicit g : Game) = g.isRezzed(self) && g.isInstalled(self)

    def runHere(self : Card)(implicit g : Game) = g.run.exists(r => !r.ended && thisServer(self, r.server))

    def protecting(self : Card, ice : Card)(implicit g : Game) = ice.data.isIce && g.zone(ice) @@ {
        case IceZone(s) => thisServer(self, s)
        case _ => false
    }

    def inRoot(self : Card, c : Card)(implicit g : Game) = g.zone(c) @@ {
        case RootZone(s) => thisServer(self, s)
        case _ => false
    }

    // root card of this server or ice protecting it
    def inThisServer(self : Card, c : Card)(implicit g : Game) = inRoot(self, c) || protecting(self, c)

    // the agenda is in this server's root or in this central server's zone
    def fromThisServer(self : Card, c : Card)(implicit g : Game) = g.serverOf(self).exists(s => g.rotFromServer(c).has(s))

    def remoteOnly(server : |[Server]) = server.forall(_.isRemote)

    def ascii(c : Card) = c.title.filter(ch => ch < 128 && ch != '"' && ch != '\'' && ch != '\\')

    def name(implicit g : Game) = title.filter(ch => ch < 128 && ch != '"' && ch != '\'' && ch != '\\')

    def agendaCounters(self : Card, n : Int) : $[ForcedAction] = (n > 0).$(AddCounterEffect(self, "agenda", n))

    def runFlagHere(key : String, self : Card)(implicit g : Game) = Flag(key, |(self), g.serverOf(self), "run", 0, $)
}
