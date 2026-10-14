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
// Credits and costs (see STATUS-fix-credits.md and ENGINE.md section 8.2).
//
// Every credit payment knows what it pays for: a `Spend` (play an event / operation, install a card, rez, advance,
// pay a trash cost, the Corp's basic "trash a resource", the Runner's basic "remove a tag", a trace, a psi bid, an
// ability of a card, an icebreaker break / pump, an additional run cost, a "pay X" inside an effect).
//
// Every credit source declares what it may pay for:
//   * the credit pool pays for everything (unless "you cannot spend credits from your credit pool": Aircheck);
//   * hosted credits (recurring or not) on an active card pay for a Spend when the card's `paysFor(self, spend)` says so
//     (the older hooks still work: `creditsUsableForRun` = anything during a run, `creditsUsableFor(self, purpose)` with
//     the purpose strings "play-event", "trash-asset", "advance-ice", "trace", ... derived from the Spend);
//   * bad publicity credits are run-scoped: `Run.credits` is set when a run begins and they are gone when it ends.
//
// A cost may require stealth credits (`StealthCreditCost(n, stealth)`: at least `stealth` of the n credits must come
// from Stealth cards; `stealth == n` is "spend credits only from stealth cards").
//
// Payment order (deterministic, no prompt). The restricted, short-lived sources are used up first so that the pool
// stays available for everything else:
//   1. the stealth part of the cost, from Stealth sources (same order as below);
//   2. bad publicity credits of the run (they expire when the run ends);
//   3. credits hosted on cards in the play area (run events: Concerto, Overclock, Aircheck, Bahia Bands...: they
//      leave with the event);
//   4. recurring credits (they refill anyway at the start of their owner's turn);
//   5. other hosted credits (Ghost Runner, Open Market, Cybersand Harvester...);
//   within each group non-stealth cards are spent before Stealth cards, then in board order (identity, score area,
//   installed cards, play area);
//   6. the credit pool.
// While the Runner has an active card that needs stealth credits (Dagger, Houdini, Afterimage, Mu Safecracker...: its
// text says "spend credits only from stealth cards" / "at least 1 credit from a stealth card"), the stealth credits that
// a payment does not require are kept: they are spent only after the pool.
// There is no prompt to choose the sources: this order is the best choice in nearly every situation (it never spends
// the pool while a source that expires sooner or that is restricted could pay) and it keeps the game deterministic.
// ---------------------------------------------------------------------------------------------------------------------


// what a credit payment is for
case class Spend(kind : String, card : |[Card] = None, target : |[Card] = None, server : |[Server] = None) extends Record {
    def is(k : String*) : Boolean = k.contains(kind)
    // the card whose ability is used (paid abilities, icebreaker break / pump abilities)
    def using : |[Card] = if (is("ability", "break", "pump")) card else None
    // the card being installed / rezzed / played / advanced / trashed
    def installing : |[Card] = if (is("install")) card else None
    def rezzing : |[Card] = if (is("rez")) card else None
    def playing : |[Card] = if (is("play")) card else None
    def advancing : |[Card] = if (is("advance")) card else None
    def trashing : |[Card] = if (is("trash")) card else None

    // the purpose string of the older `CardImpl.creditsUsableFor` hook
    def purpose : String = kind match {
        case "play" => card./(c => "play-" + c.data.kind.toLowerCase).|("")
        case "trash" => card./(c => "trash-" + c.data.kind.toLowerCase).|("trash-")
        case "advance" => card./(c => c.data.isIce.?("advance-ice").|("advance")).|("advance")
        case "install" => card./(c => "install-" + c.data.kind.toLowerCase).|("install")
        case "ability" | "break" | "pump" | "effect" | "other" => ""
        case k => k
    }
}

object Spend {
    val other = Spend("other")
    def play(c : Card) = Spend("play", |(c))
    def install(c : Card, server : |[Server] = None, host : |[Card] = None) = Spend("install", |(c), host, server)
    def rez(c : Card) = Spend("rez", |(c))
    def advance(c : Card) = Spend("advance", |(c))
    def trash(c : Card) = Spend("trash", |(c))                       // a trash cost (accessed card, "pay the trash cost")
    def trashResource(c : Card) = Spend("trash-resource", |(c))      // the Corp's basic action
    val removeTag = Spend("remove-tag")                              // the Runner's basic action
    val trace = Spend("trace")
    val psi = Spend("psi")
    def ability(c : Card) = Spend("ability", |(c))
    def break(b : Card, ice : Card) = Spend("break", |(b), |(ice))
    def pump(b : Card, ice : |[Card]) = Spend("pump", |(b), ice)
    def run(s : Server) = Spend("run", None, None, |(s))
    def effect(source : |[Card]) = Spend("effect", source)
    val corpAbility = Spend("corp-ability")                          // the Runner pays because of a Corp card ability ("end the run unless the Runner pays")

    // the Spend of the older call sites (`payCredits(f, n, self, purpose)` / `availableCredits(f, self, purpose)`)
    def legacy(self : |[Card], purpose : String) : Spend = purpose match {
        case "" => self./(ability).|(other)
        case p => Spend(p, self)
    }
}

// a credit cost of which at least `stealth` credits must come from Stealth cards (stealth == n: only stealth credits)
case class StealthCreditCost(n : Int, stealth : Int) extends Cost {
    def elem = n.hl ~ " credits" ~ ((stealth >= n).?(" (stealth only)").|(" (" + stealth + " stealth)"))
}

// pay n credits for spend (stealth of them from Stealth cards), then resolve the effects; nothing happens if the
// credits cannot be paid any more
case class SpendEffect(f : Faction, n : Int, spend : Spend, stealth : Int, then : $[ForcedAction]) extends ForcedAction with SelfPerform {
    def perform(soft : Void)(implicit g : Game) = {
        if (g.canSpend(f, n, spend, stealth)) {
            g.spendCredits(f, n, spend, stealth)
            g.pushAll(then)
            if (n > 0) {
                g.log(f, "pays", n.credits)
                g.trigger(CreditsPaid(f, n, spend))
            }
        }
        else
            g.log(f, "cannot pay", n.credits)
        NextAction
    }
}

// bad publicity: when a run begins, the Runner gets one credit per bad publicity for that run
case object BadPublicityCreditsEffect extends ForcedAction with SelfPerform {
    def perform(soft : Void)(implicit g : Game) = {
        g.run.foreach { r =>
            if (!r.ended && g.badPublicity > 0) {
                r.credits = g.badPublicity
                g.log(Runner, "gets", g.badPublicity.credits, "from bad publicity for this run")
            }
        }
        NextAction
    }
}

// the Runner spent bad publicity credits (Shackleton Grid: credits from outside the credit pool)
case class BadPublicityCreditsSpent(n : Int) extends Event

// f paid n credits through a SpendEffect (GameNET: a Corp card ability made the Runner spend credits)
case class CreditsPaid(f : Faction, n : Int, spend : Spend) extends Event


object CreditRules {
    val stealthText = """only from <strong>stealth|stealth</strong> card to use|at least 1(\[credit\]| credit) from a <strong>stealth""".r
}

trait CreditRules { game : Game =>

    // ---- restrictions ------------------------------------------------------------------------------------------------

    // "you cannot lose or spend credits from your credit pool" (Aircheck while its run lasts)
    def poolLocked(f : Faction) : Boolean = f.isRunner && at(PlayZone(Runner)).exists(c => c.code == "36018" && !isDisabled(c))

    // "the Runner cannot spend credits" (RSVP for the remainder of the run)
    def noSpending(f : Faction) : Boolean = f.isRunner && hasFlag("no-spend-credits")

    // ---- sources -----------------------------------------------------------------------------------------------------

    // the run whose bad publicity credits can be spent (an access-only pseudo-run uses the run it interrupts)
    def bpRun : |[Run] = run./(r => r.accessOnly.?(r.outer.|(r)).|(r)).%(r => !r.ended)

    def bpCredits(f : Faction) : Int = f.isRunner.??(bpRun./(_.credits).|(0))

    def isCreditSource(c : Card, f : Faction, s : Spend) : Boolean = counter(c, "credit") > 0 && {
        val i = impl(c)
        i.paysFor(c, s) || (s.purpose != "" && i.creditsUsableFor(c, s.purpose)) || (f.isRunner && run.any && i.creditsUsableForRun(c))
    }

    def creditTier(c : Card) : Int = (zone(c).is[PlayZone]).?(0).|(c.data.text.contains("recurring-credit").?(1).|(2))

    // the cards whose hosted credits may pay for s, in spending order
    def creditSourcesOf(f : Faction, s : Spend) : $[Card] =
        if (noSpending(f)) $
        else activeCards(f).distinct.%(c => isCreditSource(c, f, s)).zipWithIndex.sortBy { case (c, i) => (creditTier(c), has(c, "Stealth").?(1).|(0), i) }.map(_._1)

    def poolFor(f : Faction) : Int = poolLocked(f).?(0).|(credits(f).atLeast(0))

    // all the credits f can spend for s
    def creditsFor(f : Faction, s : Spend) : Int =
        if (noSpending(f)) 0
        else poolFor(f) + bpCredits(f) + creditSourcesOf(f, s)./(c => counter(c, "credit")).sum

    def stealthCreditsFor(f : Faction, s : Spend) : Int = creditSourcesOf(f, s).%(c => has(c, "Stealth"))./(c => counter(c, "credit")).sum

    def canSpend(f : Faction, n : Int, s : Spend, stealth : Int = 0) : Boolean =
        n <= 0 || (!noSpending(f) && creditsFor(f, s) >= n && stealthCreditsFor(f, s) >= stealth.atMost(n))

    // ---- payment -----------------------------------------------------------------------------------------------------

    def spendFromCard(f : Faction, c : Card, m : Int) {
        addCounter(c, "credit", -m)
        log(f, "spends", m.credits, "from", c)
        regInc("spent-from-card")
        trigger(CounterAdded(c, "credit"))
        trigger(CreditsSpentFrom(c, m))
    }

    def spendCredits(f : Faction, n : Int, s : Spend, stealth : Int = 0) {
        if (n > 0) {
            var left = n
            val sources = creditSourcesOf(f, s)
            // 1. the stealth part
            var need = stealth.atMost(n)
            sources.%(c => has(c, "Stealth")).foreach { c =>
                val m = need.atMost(counter(c, "credit"))
                if (m > 0) {
                    spendFromCard(f, c, m)
                    need -= m
                    left -= m
                }
            }
            // 2. bad publicity credits of the run
            if (left > 0 && bpCredits(f) > 0) {
                val m = left.atMost(bpCredits(f))
                bpRun.foreach(_.credits -= m)
                left -= m
                log(f, "spends", m.credits, "of bad publicity")
                trigger(BadPublicityCreditsSpent(m))
            }
            // 3.-5. hosted credits (stealth credits after the pool while a card needs them)
            val keep = keepStealth(f)
            def fromCards(l : $[Card]) {
                l.foreach { c =>
                    val m = left.atMost(counter(c, "credit"))
                    if (m > 0) {
                        spendFromCard(f, c, m)
                        left -= m
                    }
                }
            }
            fromCards(keep.?(sources.%!(c => has(c, "Stealth"))).|(sources))
            // 6. the pool
            val p = left.atMost(poolFor(f))
            credits += f -> (credits(f) - p)
            left -= p
            if (keep)
                fromCards(sources.%(c => has(c, "Stealth")))
            // (an unchecked payment that cannot be covered takes the rest from the pool)
            credits += f -> (credits(f) - left)
        }
    }

    // cards that need stealth credits ("spend credits only from stealth cards", "at least 1 credit from a stealth card")
    def isStealthConsumer(c : Card) : Boolean = CreditRules.stealthText.findFirstIn(c.data.text).any

    // while the Runner has an active card that needs stealth credits, stealth credits that a payment does not require are
    // spent only after the pool
    def keepStealth(f : Faction) : Boolean = f.isRunner && activeCards(Runner).exists(isStealthConsumer)

    // ---- payments made of several parts ("install and rez it, paying n less") -----------------------------------------

    // the install part and (optionally) the rez part of a combined payment; the discount lowers the install cost first
    def installParts(c : Card, server : |[Server], host : |[Card], discount : Int, rez : Boolean) : $[(Int, Spend)] = {
        val ic = installCost(c, server)
        val rc = rez.??(rezCost(c))
        val i = (ic - discount).atLeast(0)
        val r = (rc - (discount - ic).atLeast(0)).atLeast(0)
        $((i, Spend.install(c, server, host))) ++ rez.$((r, Spend.rez(c)))
    }

    // can f pay all the parts (simulates the payment order of spendCredits, without stealth requirements)
    def canSpendAll(f : Faction, parts : $[(Int, Spend)]) : Boolean = parts.forall(_._1 <= 0) || (!noSpending(f) && {
        var hosted : Map[Card, Int] = Map()
        var bp = bpCredits(f)
        var pool = poolFor(f)
        parts.forall { case (n, s) =>
            var left = n.atLeast(0)
            val b = left.atMost(bp)
            bp -= b
            left -= b
            creditSourcesOf(f, s).foreach { c =>
                val have = hosted.getOrElse(c, counter(c, "credit"))
                val m = left.atMost(have)
                hosted += c -> (have - m)
                left -= m
            }
            val m = left.atMost(pool)
            pool -= m
            left -= m
            left == 0
        }
    })

    def spendAll(f : Faction, parts : $[(Int, Spend)]) {
        parts.foreach { case (n, s) => spendCredits(f, n, s) }
    }

    // ---- additional costs of icebreaker abilities --------------------------------------------------------------------

    // additional credits to use an ability of icebreaker b (breaking: a break ability): CardImpl.icebreakerSurcharge
    // (Midway Station Grid, Net Watchlist) plus the run flags "break-surcharge" (Interrupt 0, one flag per resolved subroutine)
    def surchargeFor(b : Card, breaking : Boolean) : Int =
        if (!isIcebreaker(b)) 0
        else allActive./(x => impl(x).icebreakerSurcharge(x, b, breaking)).sum + breaking.??(flagsOf("break-surcharge").num)

    // the costs plus n credits (added to the credit cost; a "stealth credits only" cost stays stealth only)
    def withSurcharge(costs : $[Cost], n : Int) : $[Cost] =
        if (n <= 0) costs
        else costs.indexWhere { case CreditCost(_) | StealthCreditCost(_, _) => true ; case _ => false } match {
            case -1 => costs :+ CreditCost(n)
            case i => costs.updated(i, costs(i) match {
                case CreditCost(k) => CreditCost(k + n)
                case StealthCreditCost(k, s) => StealthCreditCost(k + n, (s >= k).?(k + n).|(s))
                case x => x
            })
        }

    def breakCostsWith(b : Card, costs : $[Cost]) : $[Cost] = withSurcharge(costs, surchargeFor(b, true))
    def pumpCostsWith(b : Card, costs : $[Cost]) : $[Cost] = withSurcharge(costs, surchargeFor(b, false))

    // ---- install costs depending on the host -------------------------------------------------------------------------

    // the install cost of a Runner card installed hosted on host (Hackerspace: 1 less)
    def hostedInstallCost(c : Card, host : |[Card]) : Int = (installCost(c, None) + host./(h => impl(h).hostedInstallCostMod(h, c)).|(0)).atLeast(0)

    // ---- basic action costs ------------------------------------------------------------------------------------------

    // the Runner's basic action to remove a tag: 2 credits (+ SYNC: Everything, Everywhere)
    def removeTagCost : Int = (2 + rhSync("tag")).atLeast(0)

    // the Corp's basic action to trash a resource: 2 credits (+ SYNC, + 2 per active Wireless Net Pavilion)
    def trashResourceCost : Int = (2 + rhSync("resource") + activeCards(Runner).%(c => c.code == "08108")./(_ => 2).sum).atLeast(0)
}
