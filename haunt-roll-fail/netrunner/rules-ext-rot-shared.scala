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
// Mechanics shared by several rotated / FFG-era card types. The eight rotated ports (ice, programs, resources, events,
// operations, assets, agendas + upgrades, hardware + identities) were written in parallel and several of them defined
// the same mechanic independently; the merge keeps one definition of each here (see ENGINE.md section 8):
//   expose            ExposeEffect(cards) -> Corp responses (RA.exposeOptions: Zaibatsu Loyalty) -> one Exposed(cards)
//                     event (RA.onExpose: Psychic Field); ExposePurpose for "choose cards to expose"
//   facedown installs InstallFacedownEffect(card) (Harbinger, Hunting Grounds, Apex, Reboot) + FacedownInstalled event;
//                     facedown programs use no memory (Game.memoryUsed)
//   LoseAllCreditsEffect, BreachNoRootEffect (breach without the root cards), SwapRootEffect (two root cards)
// Other unified mechanics live where they were first defined:
//   TraceResolved(successful, spent)   rules-ext-rot-agendas.scala (fired once per trace, after its success/fail effects)
//   RotPsiRevealed(corp, runner)       rules-ext-rot-assets.scala (fired once per psi game)
//   RefillCreditsEffect                rules-ext-su21.scala (recurring credits, also for identities)
//   currents                           rules-ext-rot-operations.scala (rotOperationTriggers, for Corp and Runner currents)
//   recurring trace credits            rules-ext-rot-hardware.scala (RH.creditsUsableFor "trace" and RA.recurring "trace")
// ---------------------------------------------------------------------------------------------------------------------

// ---- events -----------------------------------------------------------------------------------------------------------
case class Exposed(cards : $[Card]) extends Event                       // installed Corp cards were exposed (once per expose effect)
case class FacedownInstalled(card : Card) extends Event                 // a Runner card was installed facedown

// ---- effects ----------------------------------------------------------------------------------------------------------
case class ExposeEffect(cards : $[Card]) extends ForcedAction                  // the Runner exposes installed unrezzed Corp cards
case class RotExposeEffect(card : Card) extends ForcedAction                   // the Corp may respond to the expose of card (re-asked after a rez)
case class RotExposePreventedEffect(card : Card) extends ForcedAction          // the expose of card is prevented
case class ExposeResolveAction(cards : $[Card]) extends ForcedAction           // expose the cards whose expose was not prevented
case class InstallFacedownEffect(card : Card) extends ForcedAction             // install a Runner card facedown (no cost, no abilities, no memory)
case class LoseAllCreditsEffect(f : Faction) extends ForcedAction
case class BreachNoRootEffect(server : Server) extends ForcedAction            // breach a server (outside of a run) without accessing its root cards
case class SwapRootEffect(a : Card, b : Card) extends ForcedAction             // swap two cards installed in the roots of servers

// ---- generic purposes ---------------------------------------------------------------------------------------------------
case object ExposePurpose extends Purpose                                      // the chosen cards are exposed


trait RotSharedRules { game : Game =>

    var rotFacedown : $[Card] = $                 // cards installed by InstallFacedownEffect (a facedown Harbinger is not turned facedown again)
    var rotExposePrevented : $[Card] = $          // cards whose pending expose was prevented

    def rotExposable(c : Card) : Boolean = location.contains(c) && isInstalled(c) && owner(c).isCorp && !isRezzed(c)

    // Corp responses to an expose (Zaibatsu Loyalty)
    def rotExposeOptions(c : Card) : $[Alt] = installedCards(Corp)./~(x => CardImpls(x.code) match {
        case r : RA if !isDisabled(x) => r.exposeOptions(x, c)
        case _ => $
    })

    def resolveRotSharedPurpose(f : Faction, l : $[Card], p : Purpose) : |[Continue] = p match {
        case ExposePurpose =>
            if (l.any)
                push(ExposeEffect(l))
            |(NextAction)

        case _ => None
    }

    def performRotShared : PartialFunction[Action, Continue] = {
        case ExposeEffect(l) =>
            val ll = l.distinct.%(rotExposable)
            if (ll.any) {
                rotExposePrevented = rotExposePrevented.%!(ll.has)
                pushAll(ll.%(c => rotExposeOptions(c).exists(_.ok))./(c => RotExposeEffect(c) : ForcedAction) :+ ExposeResolveAction(ll))
            }
            NextAction

        case RotExposeEffect(c) =>
            val alts = rotExposeOptions(c)
            if (!rotExposable(c) || rotExposePrevented.has(c) || alts.%(_.ok).none)
                NextAction
            else
                ChooseOneEffect(Corp, "A card would be exposed", alts :+ Alt("Let the card be exposed", $))

        case RotExposePreventedEffect(c) =>
            rotExposePrevented :+= c
            log("The card is not exposed")
            NextAction

        case ExposeResolveAction(l) =>
            val ll = l.%(rotExposable).%!(rotExposePrevented.has)
            rotExposePrevented = rotExposePrevented.%!(l.has)
            if (ll.any) {
                ll.foreach(c => state(c).seen = true)
                log(Runner, "exposes", ll./(cardElem).comma)
                regInc("exposed", ll.num)
                trigger(Exposed(ll))
            }
            NextAction

        case InstallFacedownEffect(c) =>
            if (location.contains(c) && !location.get(c).exists(inPlayZone)) {
                moveTo(c, RigZone)
                state(c).facedown = true
                state(c).seen = false
                state(c).installedTurn = turn
                rotFacedown :+= c
                regInc("installed")
                log(Runner, "installs a card facedown")
                trigger(FacedownInstalled(c))
            }
            NextAction

        case LoseAllCreditsEffect(f) =>
            if (credits(f) > 0)
                push(LoseCreditsEffect(f, credits(f)))
            NextAction

        case BreachNoRootEffect(s) =>
            val l = root(s)./~(c => c +: allHosted(c))
            pushAll(l./(c => AddFlagEffect(Flag("access-exclude", |(c), None, "run", 0, $)) : ForcedAction) ++ $(BreachServerEffect(s)) ++ l./(c => RemoveFlagEffect("access-exclude", |(c)) : ForcedAction))
            NextAction

        case SwapRootEffect(a, b) =>
            (zone(a), zone(b)) match {
                case (za @ RootZone(sa), zb @ RootZone(sb)) if a != b =>
                    val la = root(sa)
                    val lb = root(sb)
                    if (sa == sb)
                        zones += za -> la.updated(la.indexOf(a), b).updated(la.indexOf(b), a)
                    else {
                        zones += za -> la.updated(la.indexOf(a), b)
                        zones += zb -> lb.updated(lb.indexOf(b), a)
                        location += a -> zb
                        location += b -> za
                    }
                    log(Corp, "swaps", hiddenCorpElem(a), "and", hiddenCorpElem(b))
                case _ =>
            }
            NextAction
    }
}
