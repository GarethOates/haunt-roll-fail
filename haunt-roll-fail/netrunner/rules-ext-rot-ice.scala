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
// Generic mechanics added for the rotated (FFG-era) ice (see STATUS-rot-ice.md)
// ---------------------------------------------------------------------------------------------------------------------

// ---- effects ----------------------------------------------------------------------------------------------------------
case class MoveIceToIndexEffect(card : Card, index : Int) extends ForcedAction       // move an installed piece of ice to a position of its server (0 = innermost); the Runner keeps facing the same ice
case class PhantomEncounterEffect(card : Card) extends ForcedAction                   // the Runner encounters an uninstalled piece of ice (accessed from R&D / HQ): it protects the attacked server, rezzed, for the encounter
case class PhantomRestoreAction(card : Card, zone : Zone, index : Int) extends ForcedAction
case class RunFlagEffect(key : String, on : Boolean) extends ForcedAction              // set or clear a run flag: "cannot-jack-out"
case class AccessBonusTotalEffect(n : Int) extends ForcedAction                        // +n / -n cards accessed this run (any server)
case class BreakAllSubsEffect(ice : Card) extends ForcedAction                         // all unbroken subroutines of the ice are broken
case class RememberCreditsEffect(card : Card, f : Faction) extends ForcedAction         // remember the credits of f in the memo of card (to measure a trace boost afterwards)
case class TraceKickerEffect(min : Int, effects : $[ForcedAction]) extends ForcedAction  // after a successful trace: resolve effects again if the trace strength was at least min (constellation ice)
case object EndRunIfTaggedEffect extends ForcedAction
case class ResolveSubOnIceEffect(card : Card, subtype : String, allowSelf : Boolean) extends ForcedAction   // the Corp may resolve a subroutine of a rezzed piece of ice (of a subtype, "" = any) -- the choice is completed by RI.chosen


trait RotIceRules { game : Game =>

    def runKeyString : String = runKey._1 + "/" + runKey._2

    def rezzedAssets : $[Card] = installedCards(Corp).%(c => c.data.isAsset && isRezzed(c))

    def nextIceCount : Int = rezzedIce.count(c => has(c, "NEXT"))

    // the server the Runner is running against (if any)
    def runServer : |[Server] = run.%(r => !r.ended)./(_.server)

    // reorder a card inside the list of a zone
    def placeAt(c : Card, z : Zone, idx : Int) {
        place(c, z)
        val l = at(z).but(c)
        val i = idx.atLeast(0).atMost(l.num)
        zones += z -> (l.take(i) ++ $(c) ++ l.drop(i))
    }

    // game-level triggers of the rotated ice that are not tied to an active card (Formicary is unrezzed when it fires)
    def rotIceTriggers(e : Event) : $[ForcedAction] = e match {
        // Susanoo-no-Mikoto: the Runner cannot jack out until they encounter a piece of ice
        case EncounterIce(_) if hasFlag("jack-until-encounter") =>
            $(RemoveFlagEffect("jack-until-encounter", None), RunFlagEffect("cannot-jack-out", false))
        case ApproachServer(s) if run.exists(r => !r.ended) =>
            allIce.%(c => c.code == "22054" && !isRezzed(c) && credits(Corp) >= rezCost(c) && impl(c).canRez(c))./(c =>
                OptionalEffect(Corp, "Formicary: rez it and move it to the innermost position of " + s.name + " (the Runner encounters it)?", $(FormicaryAction(c, s)), $))
        case _ => $
    }

    def performRotIce : PartialFunction[Action, Continue] = {
        case MoveIceToIndexEffect(c, idx) =>
            zone(c) match {
                case IceZone(s) =>
                    val l = ice(s).but(c)
                    val i = idx.atLeast(0).atMost(l.num)
                    zones += IceZone(s) -> (l.take(i) ++ $(c) ++ l.drop(i))
                    run.foreach { r =>
                        if (!r.ended && r.server == s && r.position > 0 && i <= r.position - 1)
                            r.position += 1
                    }
                    log(hiddenCorpElem(c), "is now at position", (i + 1).hl, "of", s)
                case _ =>
            }
            NextAction

        case PhantomEncounterEffect(c) =>
            run match {
                case Some(r) if !r.ended && !isInstalled(c) && c.data.isIce =>
                    val z = zone(c)
                    val idx = at(z).indexOf(c)
                    place(c, IceZone(r.server))
                    state(c).rezzed = true
                    state(c).seen = true
                    log(Runner, "is forced to encounter", c)
                    push(ForceEncounterEffect(c), PhantomRestoreAction(c, z, idx))
                case _ =>
            }
            NextAction

        case PhantomRestoreAction(c, z, idx) =>
            zone(c) match {
                case IceZone(_) =>
                    leavePlay(c)
                    placeAt(c, z, idx)
                    state(c).reset()
                    cleanupRemotes()
                    run.foreach(r => if (r.ice.has(c)) r.ice = None)
                case _ =>
            }
            NextAction

        case RunFlagEffect(k, on) =>
            run.foreach { r =>
                k match {
                    case "cannot-jack-out" =>
                        r.cannotJackOut = on
                        if (on) log("The Runner cannot jack out")
                    case _ =>
                }
            }
            NextAction

        case AccessBonusTotalEffect(n) =>
            run.foreach { r =>
                r.accessBonus += "total" -> (r.accessBonusFor("total") + n)
            }
            NextAction

        case BreakAllSubsEffect(c) =>
            val l = unbrokenSubs(c)
            if (l.any) {
                state(c).brokenSubs ++= l
                log(Runner, "breaks", l.num.hl, "subroutines on", c)
                trigger(SubsBroken(c, l, c))
            }
            NextAction

        case RememberCreditsEffect(c, f) =>
            state(c).memo = credits(f).toString
            NextAction

        case TraceKickerEffect(min, l) =>
            if (traceStrength >= min) {
                log("The trace strength of", traceStrength.hl, "triggers the ability again")
                pushAll(l)
            }
            NextAction

        case ResolveSubOnIceEffect(c, t, allowSelf) =>
            val l = rezzedIce.%(x => (allowSelf || x != c) && (t == "" || has(x, t)) && impl(x).subroutines(x).any)
            if (l.any)
                push(ChooseCardEffect(Corp, l, RotIcePurpose(c, 99), asciiTitle(c) + ": choose a piece of ice to resolve a subroutine of", true))
            NextAction

        case EndRunIfTaggedEffect =>
            if (tagged)
                push(EndRunEffect)
            else
                log("The Runner is not tagged")
            NextAction
    }
}

case class FormicaryAction(card : Card, server : Server) extends CardAction
