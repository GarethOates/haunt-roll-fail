package netrunner
//
//
//
//
import hrf.colmat._
import hrf.compute._
import hrf.logger._
//
//
//
//

// ---------------------------------------------------------------------------------------------------------------------
// Smart bot: a generic heuristic player for any deck.
//
// Every offered action gets one score on a common scale (10 = one credit, 100 = one agenda point) computed from the
// card data (type, cost, strength, subtypes, agenda points, trash cost, printed text keywords), from the engine's
// own card implementations (icebreaker break / pump abilities, ice subroutines, ability costs and effects) and from
// the public game state. The Runner bot does not look at hidden Corp cards (unrezzed cards it has not accessed, HQ,
// R&D); the Corp bot does not look at the grip or the stack.
//
// Main ideas:
//  * economy: credit value of economy cards parsed from their text ("Gain 9[credit]", "load 12[credit]", "When your
//    turn begins, gain 1[credit]", recurring credits) and of click abilities (their GainCredits / TakeCredits effects);
//    extra credits are worth less the richer a player is (no endless clicking for money);
//  * Corp: ice where it is needed (HQ / R&D early, a scoring remote before an agenda), keep credits to rez, install an
//    agenda only where the Runner cannot easily get in (the Runner's breakers and credits against the ice), advance
//    there, fast-advance when the agenda can be scored in the same turn, score at once;
//  * Runner: run where the expected access value (agenda odds, advanced remote cards, trashable assets) beats the
//    expected cost of the ice (cheapest break + pump plan with the installed breakers, an estimate for unrezzed ice),
//    break with the cheapest plan, jack out when the rest of the run is hopeless, install breakers for the ice types it
//    meets, clear tags when tags are dangerous, draw when the grip is low; damage adds up and an empty grip makes
//    unknown ice and unknown advanced cards look like a flatline; accessed Corp cards are remembered (SmartMemory);
//  * prompts: optional effects / choices / card choices evaluated from their effect lists, traces, psi games and
//    prevention windows handled with simple bidding rules, paid ability windows passed unless an ability is useful.
// ---------------------------------------------------------------------------------------------------------------------


// What the text of a card says about its value (cached per code)
case class SmartProfile(
    gain : Int,            // operation / event: "Gain N[credit]"
    loaded : Int,          // resource / asset: "load / place / put N[credit]" on it
    perTurn : Int,         // "When your turn begins, gain / take N[credit]"
    recurring : Int,       // N[recurring-credit]
    clickGain : Int,       // "[click]...: Gain N[credit]"
    draw : Int,            // "Draw N cards"
    runEvent : Boolean,    // run event
    mu : Int,              // +N[mu]
    link : Int,            // +N[link]
    tagPunish : Boolean,   // "if the Runner is tagged"
    damage : Int,          // "Do N ... damage"
    removeTags : Int,      // "Remove N tags"
    extraClick : Boolean,  // costs an extra click / loses a click
    trap : Boolean,        // ambush / trap that fires on access
    advanceable : Boolean  // "You can advance this"
) {
    def isEcon = gain > 0 || loaded > 0 || perTurn > 0 || recurring > 0 || clickGain > 0
    // rough total credit value of an economy card over the game (before paying its cost)
    def econCredits = gain + loaded + (loaded == 0).??(perTurn * 5) + recurring * 4 + clickGain * 2
}

object SmartProfile {
    private var cache : Map[String, SmartProfile] = Map()

    private val GainR = "gain (\\d+)\\[credit\\]".r
    private val LoadR = "(?:load|place|put|places|loads) (\\d+)\\[credit\\]".r
    private val TurnR = "when your turn begins, (?:gain|take) (\\d+)\\[credit\\]".r
    private val RecR = "(\\d+)\\[recurring-credit\\]".r
    private val ClickGainR = "\\[click\\][^.\\n]{0,40}: gain (\\d+)\\[credit\\]".r
    private val DrawR = "draw (\\d+) cards?".r
    private val MuR = "\\+(\\d+)\\[mu\\]".r
    private val LinkR = "\\+(\\d+)\\[link\\]".r
    private val DmgR = "do (\\d+) (?:net|meat|core|brain) damage".r
    private val TagR = "remove (\\d+|all|1) tags?".r

    private def num(r : scala.util.matching.Regex, t : String) : Int = r.findFirstMatchIn(t).map(m => scala.util.Try(m.group(1).toInt).getOrElse(1)).getOrElse(0)

    def apply(c : CardData) : SmartProfile = cache.get(c.code) match {
        case Some(p) => p
        case None =>
            val p = analyze(c)
            cache += c.code -> p
            p
    }

    def analyze(c : CardData) : SmartProfile = {
        val t = c.text.toLowerCase.replace("<strong>", "").replace("</strong>", "")
        val oneShot = c.isOperation || c.isEvent
        val gain = oneShot.??(num(GainR, t))
        val loaded = (!oneShot).??(num(LoadR, t))
        val perTurn = (!oneShot).??(num(TurnR, t))
        val clickGain = (!oneShot).??(num(ClickGainR, t))
        val trap = c.has("Ambush") || c.has("Trap") || (c.isAsset && t.contains("when the runner accesses")) || (c.isUpgrade && t.contains("accesses"))
        SmartProfile(
            gain = gain,
            loaded = loaded,
            perTurn = perTurn,
            recurring = num(RecR, t),
            clickGain = clickGain,
            draw = num(DrawR, t),
            runEvent = c.isEvent && (c.has("Run") || t.contains("run any server") || t.contains("make a run") || t.startsWith("run ")),
            mu = num(MuR, t),
            link = num(LinkR, t),
            tagPunish = t.contains("runner is tagged") || t.contains("if the runner is tagged"),
            damage = num(DmgR, t),
            removeTags = t.contains("remove all tags").?(9).|(num(TagR, t)),
            extraClick = t.contains("spend [click]") || t.contains("lose [click]") || t.contains("additional cost to play this event, spend"),
            trap = trap,
            advanceable = t.contains("you can advance this")
        )
    }
}


// What the Runner bot remembers within a game (the bot objects are recreated for every decision)
object SmartMemory {
    private var game : Game = null
    private var known : Map[Card, Int] = Map()      // installed Corp card -> install turn, accessed by the Runner

    def sync(g : Game) {
        if (game ne g) {
            game = g
            known = Map()
        }
    }

    def see(c : Card)(implicit g : Game) {
        sync(g)
        if (g.isInstalled(c))
            known += c -> g.state(c).installedTurn
    }

    def knows(c : Card)(implicit g : Game) : Boolean = {
        sync(g)
        known.get(c).exists(_ == g.state(c).installedTurn)
    }
}


object SmartBot {
    var errors = 0
    var lastError = ""
    var greedyAbove = 12                              // selections from more cards than this are made greedily
    var unhandled : Map[String, Int] = Map()          // prompts scored by the generic fallback (statistics for tests)
}

class SmartBot(f : Faction) extends EvalBot {
    override def ask(actions : $[UserAction], deviation : Double)(implicit game : Game) : Compute[UserAction] = {
        if (f.isRunner)
            actions.foreach {
                case PayTrashAction(_, c) => SmartMemory.see(c)
                case NoAccessAction(_, c) => SmartMemory.see(c)
                case UnavailableReasonAction(PayTrashAction(_, c), _) => SmartMemory.see(c)
                case _ =>
            }

        // large card selections: hill-climb instead of exploding every combination
        actions.collect { case a : XXSelectObjectsExplodeAction[_] => a }.single match {
            case Some(x) if x.values.num > SmartBot.greedyAbove =>
                greedySelect(actions) match {
                    case Some(a) => return Just(a)
                    case None =>
                }
            case _ =>
        }

        super.ask(actions, deviation)
    }

    def greedySelect(actions : $[UserAction])(implicit game : Game) : |[UserAction] = {
        val ev = new SmartEval(f)
        def thens(l : $[UserAction]) : $[UserAction] = l.collect { case t : XXObjectsSelectedAction[_] if t.then != null => t : UserAction }
        def selects(l : $[UserAction]) : $[XXSelectObjectAction[_]] = l.collect { case s : XXSelectObjectAction[_] => s }
        def best(l : $[UserAction]) : |[(UserAction, Double)] = thens(l).map(t => (t, ev.score(t))).sortBy(x => -x._2).headOption

        var current : $[UserAction] = actions
        var result : |[(UserAction, Double)] = best(current)
        var steps = 0
        var go = true
        while (go && steps < 60) {
            steps += 1
            val options : $[($[UserAction], Double, |[(UserAction, Double)])] = selects(current).map { s =>
                val next : $[UserAction] = s.perform(NoVoid)(game) match {
                    case x : XXSelectObjectsAction[_] => x.ask(game).actions
                    case _ => Nil
                }
                val b = best(next)
                (next, b.map(_._2).getOrElse(ev.itemScore(s.obj) - 1000.0), b)
            }
            options.sortBy(x => -x._2).headOption match {
                case Some((next, v, b)) if next.nonEmpty && (result.isEmpty || result.exists(_._2 < v)) =>
                    current = next
                    b.foreach(x => result = Some(x))
                case _ =>
                    go = false
            }
        }
        result.map(_._1)
    }

    def eval(actions : $[UserAction])(implicit game : Game) : Compute[$[ActionEval]] = {
        val l = actions.%!(_.is[NotForBots])
        // only options not meant for bots: never the manual adjustments or a concession when anything else is offered
        val safe = actions.%!(x => x.is[ManualAction] || x.is[ConcedeConfirmAction])
        val ev = new SmartEval(f)
        (l.any.?(l).|(safe.any.?(safe).|(actions)))./(a => ActionEval(a, $(Evaluation((ev.score(a) * 10).round.toInt + random(5), "smart"))))
    }
}


class SmartEval(val me : Faction)(implicit val g : Game) {
    val C = 10.0                                   // value of one credit

    implicit class SmartDouble(d : Double) {
        def atLeastD(x : Double) : Double = math.max(d, x)
        def atMostD(x : Double) : Double = math.min(d, x)
    }

    implicit class SmartBoolean(b : Boolean) {
        def ?#(v : => Double) : Double = if (b) v else 0.0
    }

    def sgn(f : Faction) : Double = (f == me).?(1.0).|(-1.0)

    // more credits matter less and less: a rich player should draw, install and score rather than click for money
    def gainWorth(f : Faction) : Double = {
        val c = g.credits(f)
        if (c < 10) 1.0 else if (c < 20) 0.6 else if (c < 40) 0.3 else 0.1
    }

    lazy val CG = C * gainWorth(me)
    def runnerSign : Double = sgn(Runner)
    def corpSign : Double = sgn(Corp)

    lazy val credits = g.credits(me)
    lazy val clicks = g.clicks(me)

    def prof(c : Card) = SmartProfile(c.data)

    // -----------------------------------------------------------------------------------------------------------------
    // Runner rig
    // -----------------------------------------------------------------------------------------------------------------

    // installed cards that can break subroutines (icebreakers whose break abilities depend on the run count too)
    lazy val breakers : $[Card] = (g.installedCards(Runner) ++ g.identity.get(Runner).$).%(b => g.isActive(b) && (g.isIcebreaker(b) || g.impl(b).breaks(b).any)).distinct

    def breakerTypes(b : Card) : $[String] = (g.impl(b).breaks(b)./~(br => br.subtypes.none.?($("*")).|(br.subtypes)) ++ g.isIcebreaker(b).??(cardBreakerTypes(b))).distinct

    lazy val coveredTypes : $[String] = breakers./~(breakerTypes).distinct

    def covers(t : String) = coveredTypes.has("*") || coveredTypes.has(t)

    val mainTypes = $("Barrier", "Code Gate", "Sentry")

    lazy val coverage = mainTypes.count(covers)

    // types a breaker card (in hand) would cover, from its subtypes
    def cardBreakerTypes(c : Card) : $[String] =
        if (c.data.has("AI")) $("*")
        else $(c.data.has("Fracter").?("Barrier"), c.data.has("Decoder").?("Code Gate"), c.data.has("Killer").?("Sentry")).flatten

    lazy val runCredits : Int = g.credits(Runner).atLeast(0) + g.activeCards(Runner).%(c => c.data.text.contains("recurring-credit") || g.impl(c).creditsUsableForRun(c))./(c => g.counter(c, "credit")).sum

    lazy val runnerVirus : Int = g.installedCards(Runner)./(c => g.counter(c, "virus")).sum

    def creditsIn(costs : $[Cost]) : Int = costs./ {
        case CreditCost(n) => n
        case StealthCreditCost(n, _) => n
        case _ => 0
    }.sum

    def nonCreditOk(costs : $[Cost], b : Card) : Boolean = costs.forall {
        case CreditCost(_) => true
        case StealthCreditCost(_, _) => true
        case ClickCost(_) => false
        case c => g.canPay(Runner, $(c), |(b))
    }

    // credits needed to break nsubs subroutines of ice (strength str) with breaker b, None if impossible
    def breakCost(b : Card, ice : Card, str : Int, nsubs : Int) : |[Int] = {
        if (nsubs <= 0)
            return |(0)
        val impl = g.impl(b)
        val brs = impl.breaks(b).%(br => (br.subtypes.none || br.subtypes.exists(t => g.has(ice, t))) && nonCreditOk(br.costs, b))
        if (brs.none || !g.impl(ice).canBreakWith(ice, b) || g.allActive.exists(x => g.impl(x).preventsBreak(x, ice, b)) || !g.allActive.forall(x => g.impl(x).breakAllowed(x, b, ice)))
            return None
        val pump : |[Int] =
            if (!g.isIcebreaker(b) || g.breakerStrength(b) >= str)
                |(0)
            else {
                val diff = str - g.breakerStrength(b)
                impl.pumps(b).%(p => p.strength > 0 && nonCreditOk(p.costs, b))./(p => ((diff + p.strength - 1) / p.strength) * creditsIn(g.pumpCostsWith(b, p.costs))).minOption
            }
        pump.flatMap { pc =>
            brs./(br =>
                if (br.n == 0) creditsIn(g.breakCostsWith(b, g.breakCosts(br, nsubs)))
                else ((nsubs + br.n - 1) / br.n) * creditsIn(g.breakCostsWith(b, br.costs))
            ).minOption./(_ + pc)
        }
    }

    def iceBreakCost(ice : Card, nsubs : Int) : |[Int] = {
        val str = g.iceStrength(ice)
        breakers./~(b => breakCost(b, ice, str, nsubs)).minOption
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Subroutines
    // -----------------------------------------------------------------------------------------------------------------

    private val NumR = "(\\d+)".r

    // a subroutine that ends the run; conditional ones ("unless the Runner pays 3", "if the Runner is tagged",
    // "if the Runner has 6 credits or less") only count when the condition is likely to hold
    def subETR(s : Sub) : Boolean = s.effect == EndRunEffect || {
        val l = s.label.toLowerCase
        if (!l.contains("end the run")) false
        else if (l.contains("unless")) runCredits < NumR.findFirstIn(l).map(_.toInt).getOrElse(2) + 2
        else if (l.contains("tagged")) g.tagged
        else if (l.contains("or less") || l.contains("or fewer")) NumR.findFirstIn(l).map(n => runCredits <= n.toInt).getOrElse(true)
        else true
    }

    def subDamage(s : Sub) : Int = {
        val l = s.label.toLowerCase
        if (l.contains("damage")) NumR.findFirstIn(l).map(_.toInt).getOrElse(1) else 0
    }

    def damageValue(n : Int) : Double = {
        val h = g.hand(Runner).num
        if (n <= 0) 0
        else if (n > h) 5000
        else n * 22.0 + (n == h).?#(40.0)
    }

    lazy val tagValue : Double = 25.0 + 6 * g.installedCards(Runner).%(_.data.isResource).num + (g.credits(Corp) >= 5).?#(10.0)

    // harm (for the Runner) of a non-ETR subroutine firing
    def subHarm(s : Sub) : Double = {
        val l = s.label.toLowerCase
        if (subETR(s)) 0
        else if (l.contains("end the run") && l.contains("unless")) NumR.findFirstIn(l).map(_.toInt).getOrElse(2) * C
        else if (l.contains("end the run")) 10
        else if (l.contains("damage")) damageValue(subDamage(s))
        else if (l.contains("tag")) tagValue
        else if (l.contains("trash") && l.contains("program")) 55
        else if (l.contains("trash")) 40
        else if (l.contains("lose") && l.contains("click")) 15
        else if (l.contains("lose") && (l.contains("credit") || l.contains("[c]"))) NumR.findFirstIn(l).map(_.toInt).getOrElse(2) * C
        else if (l.contains("trace")) 25
        else if (l.contains("gain") && (l.contains("credit") || l.contains("[c]"))) NumR.findFirstIn(l).map(_.toInt).getOrElse(1) * 4.0
        else 12
    }

    // harm of letting the subroutines in `unbroken` fire, in order: an ETR ends the run (etr = value lost)
    // damage adds up (several damage subroutines can flatline together); dmg0 = damage already expected in this run
    def firingHarm(subs : $[Sub], unbroken : $[Int], etr : Double, dmg0 : Int = 0) : Double = {
        var total = 0.0
        var stop = false
        var dmg = dmg0
        subs.indices.foreach { i =>
            if (!stop && unbroken.has(i)) {
                val s = subs(i)
                val d = subDamage(s)
                if (d > 0) {
                    total += damageValue(dmg + d) - damageValue(dmg)
                    dmg += d
                }
                else if (subETR(s)) {
                    total += etr
                    stop = true
                }
                else
                    total += subHarm(s)
            }
        }
        total
    }

    def firingDamage(subs : $[Sub], unbroken : $[Int]) : Int = {
        val firstEtr = subs.indices.%(i => unbroken.has(i) && subETR(subs(i)) && subDamage(subs(i)) == 0).minOption.|(subs.num)
        unbroken.%(_ < firstEtr)./(i => subDamage(subs(i))).sum
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Runner: run evaluation (only public information + accessed cards)
    // -----------------------------------------------------------------------------------------------------------------

    def runnerKnows(c : Card) : Boolean = g.isRezzed(c) || g.state(c).seen || (me.isCorp) || SmartMemory.knows(c)

    case class RunEst(pass : Double, cost : Double, harm : Double)

    // the ice still to pass on server s: innermost first in the engine list, so the next one is the last
    def iceAhead(s : Server, position : Int) : $[Card] = g.ice(s).take(position).reverse

    def estimate(s : Server, ices : $[Card]) : RunEst = {
        var budget = runCredits.toDouble
        var corp = g.credits(Corp).toDouble
        var pass = 1.0
        var cost = 0.0
        var harm = 0.0
        var dmg = 0
        var explored = false
        ices.foreach { ice =>
            if (g.isRezzed(ice)) {
                val subs = g.subs(ice)
                val all = subs.indices.$
                val etrs = all.%(i => subETR(subs(i)) && subDamage(subs(i)) == 0)
                val full = iceBreakCost(ice, subs.num)
                val etrOnly = etrs.any.?(iceBreakCost(ice, etrs.num)).|(None)
                full match {
                    case Some(c) if c <= budget =>
                        budget -= c
                        cost += c
                    case _ =>
                        etrOnly match {
                            case Some(c) if c <= budget && firingHarm(subs, all.diff(etrs), 0, dmg) < c * C + 30 =>
                                budget -= c
                                cost += c
                                harm += firingHarm(subs, all.diff(etrs), 0, dmg)
                                dmg += firingDamage(subs, all.diff(etrs))
                            case _ =>
                                harm += firingHarm(subs, all, 0, dmg)
                                dmg += firingDamage(subs, all)
                                if (etrs.any)
                                    pass = 0
                        }
                }
            }
            else {
                // unknown ice: the Corp may rez it
                val pRez = if (corp >= 5) 0.85 else if (corp >= 3) 0.65 else if (corp >= 1) 0.3 else 0.05
                var pBreak = coverage match {
                    case 3 => 0.9
                    case 2 => 0.6
                    case 1 => 0.35
                    case _ => 0.05
                }
                if (budget < 3) pBreak *= 0.3
                else if (budget < 5) pBreak *= 0.7
                else if (budget >= 10) pBreak = (pBreak + 0.15).atMostD(0.95)
                // face-checking teaches what the ice is: worth something when the Runner can afford the risk
                if (!explored && g.hand(Runner).num - dmg >= 3 && budget >= 5) {
                    explored = true
                    harm -= 10
                }
                pass *= (1 - pRez) + pRez * pBreak
                cost += pRez * pBreak * 3.5
                budget -= pRez * pBreak * 3.5
                val h = g.hand(Runner).num - dmg
                harm += pRez * (1 - pBreak) * (12 + 60.0 / (h + 1).atLeast(1))
                // unknown ice may do damage: with an empty or nearly empty grip that is a flatline
                harm += pRez * (h <= 0).?(400.0).|((h == 1).?(120.0).|((h == 2).?(30.0).|(0.0)))
                corp -= pRez * 3
            }
        }
        RunEst(pass, cost, harm)
    }

    // expected value of a successful breach of s (Runner's point of view)
    def accessValue(s : Server) : Double = {
        val runs = g.successfulRunsThisTurn.count(s)
        val upgrades = g.root(s)
        val rootValue = upgrades./(rootCardValue).sum
        s match {
            case HQ =>
                g.hand(Corp).any.?#(45.0 * math.pow(0.6, runs)) + rootValue
            case RnD =>
                (g.deck(Corp).any).?#(45.0 * (runs > 0).?(0.15).|(1.0)) + rootValue
            case Archives =>
                if (runs > 0) rootValue
                else {
                    val known = g.discard(Corp).%(c => g.state(c).seen && c.data.isAgenda)
                    val facedown = g.discard(Corp).%(c => !g.state(c).seen)
                    known./(c => g.agendaValue(c) * 100.0 + 40).sum + (facedown.num * 10.0).atMostD(50) + rootValue
                }
            case r : Remote =>
                (runs > 0 && upgrades.forall(runnerKnows)).?(rootValue * 0.2).|(rootValue)
        }
    }

    // value of accessing an installed root card
    def rootCardValue(c : Card) : Double = {
        if (runnerKnows(c) && me.isRunner) {
            if (c.data.isAgenda) g.agendaValue(c) * 100.0 + 60
            else if (prof(c).trap && !g.isRezzed(c)) -45
            else trashValue(c)
        }
        else if (me.isCorp) {
            // the Corp's estimate of what the Runner gets
            if (c.data.isAgenda) g.agendaValue(c) * 100.0 + 60
            else if (prof(c).trap) -30
            else trashValue(c) * 0.5
        }
        else {
            // an unknown card may be an agenda, an asset or a trap (advanced traps hurt more)
            val adv = g.advancement(c)
            if (adv > 0) 0.7 * 250 - 0.25 * damageValue(adv + 2)
            else 0.4 * 200 + 0.3 * 20 - 0.1 * damageValue(3)
        }
    }

    // value for the Runner of trashing an accessed card (net of the trash cost), at least 0
    def trashValue(c : Card) : Double = g.trashCost(c) match {
        case Some(t) if t <= runCredits =>
            val p = prof(c)
            val worth = 25.0 + p.isEcon.?#(35.0) + g.isRezzed(c).?#(10.0) + c.data.isUpgrade.?#(15.0) + (c.data.cost.|(0) * 3.0)
            (worth - t * C * 0.8).atLeastD(0)
        case _ => 0
    }

    private var runMemo : Map[Server, Double] = Map()

    // expected value of running s from the start (Runner's view)
    def runValue(s : Server) : Double = runMemo.get(s) match {
        case Some(v) => v
        case None =>
            val est = estimate(s, iceAhead(s, g.ice(s).num))
            val v = est.pass * accessValue(s) - est.cost * C - est.harm - (1 - est.pass) * 8
            runMemo += s -> v
            v
    }

    lazy val bestRunValue : Double = g.servers./(runValue).maxOr(0)

    // value of the rest of the current run
    lazy val currentRunValue : Double = g.run.filter(!_.ended) match {
        case Some(r) =>
            val ahead = (r.phase == EncounterPhase || r.phase == ApproachIcePhase).?(iceAhead(r.server, r.position - 1)).|(iceAhead(r.server, r.position))
            val est = estimate(r.server, ahead)
            est.pass * accessValue(r.server) - est.cost * C - est.harm
        case None => 0
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Corp: protection of servers (the Corp knows its own ice)
    // -----------------------------------------------------------------------------------------------------------------

    private var protMemo : Map[|[Server], Double] = Map()

    // chance-like measure (0..1) that the Runner cannot get into s this turn, if the Corp rezzes what it can afford
    def protection(so : |[Server], extraIce : |[Card] = None) : Double = protMemo.get(so).filter(_ => extraIce.none) match {
        case Some(v) => v
        case None =>
            val ices = so./(s => g.ice(s).reverse).|($) ++ extraIce.$
            var corp = (g.credits(Corp) - 1).atLeast(0)
            var runner = (g.credits(Runner) + 3 + runnerRecurring).toDouble
            var result = 0.05
            var done = false
            ices.foreach { ice =>
                if (!done) {
                    val rezzed = g.isRezzed(ice)
                    val cost = g.rezCost(ice)
                    if (rezzed || cost <= corp) {
                        if (!rezzed) corp -= cost
                        val subs = g.subs(ice)
                        val etrs = subs.count(subETR)
                        if (etrs > 0 || subs.exists(s => subHarm(s) >= 40)) {
                            iceBreakCost(ice, etrs.atLeast(1)) match {
                                case None =>
                                    result = 0.9
                                    done = true
                                case Some(c) =>
                                    runner -= c
                                    if (runner < 0) {
                                        result = 0.75
                                        done = true
                                    }
                                    else
                                        result = (result + 0.15).atMostD(0.5)
                            }
                        }
                    }
                }
            }
            if (extraIce.none)
                protMemo += so -> result
            result
    }

    lazy val runnerRecurring : Int = g.activeCards(Runner)./(c => g.counter(c, "credit")).sum

    lazy val corpHandAgendas : Int = g.hand(Corp).count(_.data.isAgenda)

    lazy val installedAgendas : $[Card] = g.installedCards(Corp).%(_.data.isAgenda)

    def rootHasMain(s : Server) = g.root(s).exists(c => c.data.isAgenda || c.data.isAsset)

    // remotes with ice and nothing in the root: ready for an agenda
    lazy val scoringRemotes : $[Remote] = g.remotes.%(r => !rootHasMain(r) && g.ice(r).any)

    lazy val bestScoringProtection : Double = scoringRemotes./(r => protection(|(r))).maxOr(0)

    // how much ice is needed on a server (Corp)
    def iceNeed(so : |[Server]) : Double = so match {
        case Some(HQ) =>
            val n = g.ice(HQ).num
            $(55.0, 22.0, 10.0, 4.0).lift(n).|(2.0) + (corpHandAgendas * 8.0) / (1 + n)
        case Some(RnD) =>
            $(60.0, 24.0, 10.0, 4.0).lift(g.ice(RnD).num).|(2.0)
        case Some(Archives) =>
            val agendas = g.discard(Corp).count(_.data.isAgenda)
            (agendas > 0).?(30.0 / (1 + g.ice(Archives).num)).|((g.ice(Archives).none && g.turn > 3).?(6.0).|(1.0))
        case Some(r : Remote) =>
            val n = g.ice(r).num
            val root = g.root(r)
            if (root.exists(_.data.isAgenda)) 80.0 / (1 + n)
            else if (root.exists(c => c.data.isAsset && prof(c).trap)) 3.0
            else if (root.exists(_.data.isAsset)) 14.0 / (1 + n)
            else if (corpHandAgendas > 0) 50.0 / (1 + n)
            else 10.0 / (1 + n)
        case None =>
            if (corpHandAgendas > 0 && scoringRemotes.none) 48.0
            else if (scoringRemotes.num < 1 && g.turn > 2) 18.0
            else 3.0
    }

    // what the Corp stands to lose if the Runner gets into s
    def stake(s : Server) : Double = s match {
        case HQ => 30.0 + 25 * corpHandAgendas
        case RnD => 45.0
        case Archives => g.discard(Corp).%(_.data.isAgenda)./(c => g.agendaValue(c) * 100.0).sum + 5
        case r : Remote => g.root(r)./ { c =>
            if (c.data.isAgenda) 150.0 + 25 * g.advancement(c)
            else if (prof(c).trap) -40.0
            else if (c.data.isAsset) 20.0 + prof(c).isEcon.?#(20.0)
            else 10.0
        }.sum
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Card values
    // -----------------------------------------------------------------------------------------------------------------

    def drawValue(f : Faction) : Double = {
        val h = g.hand(f).num
        val base = h match {
            case 0 | 1 => 30.0
            case 2 => 20.0
            case 3 => 14.0
            case 4 => 9.0
            case _ => 4.0
        }
        val over = (h + 1 > g.handSize(f)).?#(18.0)
        val deck = (f.isCorp && g.deck(Corp).num <= 3).?#(40.0)
        val danger = (f.isRunner && h <= 2 && corpDamageThreat).?#(10.0) + (f.isRunner && h <= 1).?#(20.0)
        val rich = (g.credits(f) >= 8).?#(4.0) + (f.isRunner && h >= 3).?#(3.0)
        // the Corp looks for ice when it holds agendas and has no protected remote
        val richDraw = (f.isCorp && g.credits(Corp) >= 15).?#(8.0)
        val needIce = richDraw + (f.isCorp && corpHandAgendas > 0 && !g.hand(Corp).exists(_.data.isIce) && bestScoringProtection < 0.6).?#(10.0)
        base - over - deck + danger + rich + needIce
    }

    lazy val corpDamageThreat : Boolean = g.rezzedIce.exists(i => g.subs(i).exists(s => subDamage(s) > 0)) || g.scored(Corp).any && g.discard(Corp).exists(c => prof(c).damage > 0)

    // the value of keeping a card in hand
    def keepValue(c : Card) : Double = {
        val p = prof(c)
        val d = c.data
        if (d.isCorp) {
            if (d.isAgenda) 45.0
            else if (d.isIce) 32.0 + d.strength.|(0) * 2
            else if (p.isEcon) 30.0 + (p.econCredits - d.cost.|(0)) * 2
            else if (p.trap) 16.0
            else 18.0
        }
        else {
            if (d.isIcebreaker) {
                val types = cardBreakerTypes(c)
                if (types.exists(t => !covers(t)) && !g.installedCards(Runner).exists(_.title == c.title)) 60.0 else 8.0
            }
            else if (d.unique && g.installedCards(Runner).exists(_.title == c.title)) 2.0
            else if (p.isEcon) 30.0 + (p.econCredits - d.cost.|(0)) * 2
            else if (p.runEvent) 18.0
            else 15.0 + d.cost.|(0)
        }
    }

    // the value of an installed / scored card to its owner (for trash decisions)
    def cardWorth(c : Card) : Double = {
        val d = c.data
        if (d.isAgenda) g.agendaValue(c) * 100.0 + 50
        else if (d.isIce) 18.0 + g.rezCost(c) * 4 + g.isRezzed(c).?#(8.0)
        else if (d.isIcebreaker) 50.0 + d.cost.|(0) * 3
        else if (g.isInstalled(c)) 15.0 + d.cost.|(0) * 4 + prof(c).isEcon.?#(15.0)
        else 15.0
    }

    // greedy selection fallback: value of selecting a card (own cards are given up, the opponent's are hit)
    def itemScore(o : Any) : Double = o match {
        case c : Card => (g.owner(c) == me).?(-keepValue(c)).|(cardWorth(c))
        case _ => 0
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Effects (the content of optional effects, choices, abilities)
    // -----------------------------------------------------------------------------------------------------------------

    def fxs(l : $[ForcedAction]) : Double = l./(fx).sum

    def fx(e : ForcedAction) : Double = e match {
        case GainCreditsEffect(f, n) => sgn(f) * n * C * gainWorth(f)
        case LoseCreditsEffect(f, n) => -sgn(f) * n.atMost(g.credits(f).atLeast(0)) * C
        case PayCreditsEffect(f, n) => -sgn(f) * n * C
        case TakeCreditsEffect(f, c, n) => sgn(f) * n.atMost(g.counter(c, "credit")) * C * gainWorth(f)
        case DrawEffect(f, n) => sgn(f) * n * drawValue(f)
        case DrawNowEffect(f, n) => sgn(f) * n * drawValue(f)
        case GainClicksEffect(f, n) => sgn(f) * n * 12
        case LoseClicksEffect(f, n) => -sgn(f) * n.atMost(g.clicks(f)) * 12
        case DamageEffect(_, n) => -runnerSign * damageValue(n)
        case TagsEffect(n) => -runnerSign * n * tagValue
        case RemoveTagsEffect(n) => runnerSign * n.atMost(g.tags) * tagValue
        case BadPubEffect(n) => -corpSign * n * 25
        case EndRunEffect => -runnerSign * (currentRunValue.atLeastD(0) + 15)
        case EndRunNowEffect => -runnerSign * (currentRunValue.atLeastD(0) + 15)
        case TrashEffect(c, _, _) => -sgn(g.owner(c)) * cardWorth(c)
        case TrashCardsEffect(l, _) => l./(c => -sgn(g.owner(c)) * cardWorth(c)).sum
        case AddCounterEffect(c, k, n) => k match {
            case "credit" => sgn(g.owner(c)) * n * (canTakeAll(c).?(7.0).|(3.0))
            case "virus" => sgn(g.owner(c)) * n * 4
            case "advancement" => sgn(g.owner(c)) * n * 30
            case "power" | "agenda" => sgn(g.owner(c)) * n * 5
            case _ => 0
        }
        case PlaceAdvancementEffect(c, n) => sgn(g.owner(c)) * n * 30
        case RunEffect(s, _) => runnerSign * runValue(s).atLeastD(0)
        case RunAnyServerEffect(_, l) => runnerSign * l./(runValue).maxOr(0).atLeastD(0)
        case AccessBonusEffect(_, n) => runnerSign * n * 30
        case RezEffect(_, _) => corpSign * 20
        case DerezEffect(_) => -corpSign * 25
        case PumpIceEffect(_, n, _) => corpSign * n * 12
        case PumpBreakerEffect(b, n, _) => runnerSign * pumpUse(b, n)
        case BypassEffect => runnerSign * 60
        case MillEffect(f, n) => -sgn(f) * n * 5
        case PurgeEffect => corpSign * runnerVirus * 8
        case AddToHandEffect(c) => sgn(g.owner(c)) * 15
        case OptionalEffect(f, _, y, n) => (f == me).?(fxs(y) max fxs(n)).|(fxs(y) min fxs(n))
        case ChooseOneEffect(f, _, l) =>
            val v = l.%(_.ok)./(a => fxs(a.effects))
            if (v.none) 0 else (f == me).?(v.max).|(v.min)
        case ChooseCardEffect(f, _, _, _, _) => sgn(f) * 8
        case ChooseCardsEffect(f, _, _, _, _, _) => sgn(f) * 8
        case TraceEffect(_, s, fl) => 0.5 * fxs(s) + 0.5 * fxs(fl)
        case PsiEffect(eq, df) => 0.4 * fxs(eq) + 0.6 * fxs(df)
        case _ => 0
    }

    // hosted credits that can be taken all at once (Kati Jones) are worth nearly as much as credits in the pool
    def canTakeAll(c : Card) : Boolean = c.data.text.toLowerCase.contains("take all")

    // strength for a breaker is worth something only while it encounters ice it can break but is too weak for
    def pumpUse(b : Card, n : Int) : Double = encounter.ice match {
        case Some(ice) if g.run.exists(_.phase == EncounterPhase) && g.unbrokenSubs(ice).any =>
            val str = g.iceStrength(ice)
            val missing = str - g.breakerStrength(b)
            val canBreak = g.impl(b).breaks(b).exists(br => br.subtypes.none || br.subtypes.exists(t => g.has(ice, t)))
            if (canBreak && missing > 0) n.atMost(missing) * 12.0 + (n >= missing).?#(encounter.etrLoss * 0.5) else 0.0
        case _ => 0.0
    }

    def costValue(costs : $[Cost], card : Card) : Double = costs./ {
        case CreditCost(n) => n * C
        case StealthCreditCost(n, _) => n * C
        case ClickCost(n) => (n - 1).atLeast(0) * 12.0
        case TrashSelfCost => cardWorth(card) * 0.6
        case VirusCost(n) => n * 4.0
        case CounterCost(_, n) => n * 3.0
        case TrashFromHandCost(n) => n * 15.0 + (g.hand(me).num <= n + 2).?#(25.0) + (g.hand(me).num <= n + 1).?#(60.0) + (g.hand(me).num <= n).?#(300.0)
        case LoseClickCost(n) => n * 12.0
        case RemoveSelfCost => cardWorth(card) * 0.6
        case TagCost(n) => n * tagValue
        case _ => 5.0
    }.sum

    private val LabelGainR = "(?:gain|take) (\\d+)".r

    def abilityValue(a : Ability, card : Card) : Double = {
        val e = fxs(a.effects)
        val label = a.label.toLowerCase
        // card-specific effects the generic valuation does not see: read the label
        val fromLabel =
            if (e == 0 && (label.contains("credit") || label.contains("[c]")))
                LabelGainR.findFirstMatchIn(label).map(_.group(1).toInt * CG * 0.8).|(0.0)
            else if (e == 0 && label.contains("draw")) drawValue(me)
            else 0.0
        val unknown = (e == 0 && fromLabel == 0).?(a.click.?(4.0).|(-3.0)).|(0.0)
        e + fromLabel + unknown - costValue(a.costs, card)
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Scores
    // -----------------------------------------------------------------------------------------------------------------

    // a failing heuristic must never break the game: the action gets a neutral score (counted for the test harness)
    def score(a : UserAction) : Double = try { scoreRaw(a) } catch { case e : Throwable => SmartBot.errors += 1 ; SmartBot.lastError = e.toString + " on " + a ; 0.0 }

    def scoreRaw(a : UserAction) : Double = a.unwrap match {
        // ---- setup ----
        case ChoosePresetDeckAction(_, _) => random(100)
        case KeepHandAction(_) => keepHand
        case MulliganAction(_) => 0

        // ---- basic actions ----
        case ClickCreditAction(f) =>
            10.0 * gainWorth(f) + (credits < 3).?#(8.0) + (credits < 6).?#(3.0) + (f.isCorp && credits < 4).?#(4.0)
        case ClickDrawAction(f) => drawValue(f)
        case EndTurnClickAction(_) => (clicks > 0).?(-100.0).|(0.0)
        case PlayCardAction(f, c) => f.isCorp.?(playOperation(c)).|(playEvent(c))
        case CorpInstallAction(_, c, s) => corpInstall(c, s, false)
        case RunnerInstallAction(_, c, h, t) => runnerInstall(c, h, t)
        case AdvanceAction(_, c) => advance(c)
        case ScoreAction(_, _) => 1000.0
        case RezAction(_, c, ctx) => rez(c, ctx)
        case TrashResourceAction(_, c) => trashResource(c)
        case PurgeAction(_) => (runnerVirus >= 3).?(14.0 * runnerVirus).|(-40.0)
        case RunAction(_, s) => runValue(s)
        case RemoveTagAction(_) => removeTag
        case UseAbilityAction(_, c, i, ctx) => g.impl(c).abilities(c).lift(i)./(x => abilityValue(x, c) + encounterAbilityBonus(x)).|(-5.0)
        case UseRunnerAbilityAction(_, c, i, _) => g.impl(c).runnerAbilities(c).lift(i)./(x => bioroidValue(c, x)).|(-5.0)
        case UseCorpAbilityAction(_, c, i, _) => g.impl(c).corpAbilities(c).lift(i)./(x => abilityValue(x, c)).|(-5.0)
        case UseInactiveAbilityAction(_, c, i, _) => g.impl(c).inactiveAbilities(c).lift(i)./(x => abilityValue(x, c)).|(-5.0)

        // ---- windows ----
        case WindowPassAction(_, "movement") => currentRunValue
        case ContinueRunAction(_) =>
            // jack-out offer in the middle of an encounter: the rest of the subroutines still fire
            val fire = g.pending.collect { case FireSubAction(ice, i) if encounter.ice.has(ice) => i }
            -firingHarm(encounter.subs, fire, encounter.etrLoss) + encounter.after
        case WindowPassAction(_, _) => 1.0
        case JackOutAction(_) => 0.0

        // ---- encounters ----
        case ContinueEncounterAction(_, ice) => encounter.continueValue
        case BreakSubAction(_, b, bi, ice, i) => encounter.breakOne(b, bi, i)
        case BreakSubsAction(_, b, bi, ice, l) => encounter.breakMany(b, bi, l)
        case PumpAction(_, b, pi) => encounter.pump(b, pi)
        case BioroidBreakSubAction(_, ice, i) => g.subs(ice).lift(i)./(s => subETR(s).?(100.0).|(subHarm(s))).|(0.0)
        case SubChoiceAction(f, ice, i) => g.subs(ice).lift(i)./(s => subETR(s).?(60.0).|(subHarm(s)) * -runnerSign).|(0.0)

        // ---- access ----
        case StealAction(_, _) => 1000.0
        case PayTrashAction(_, c) => payTrash(c)
        case NoAccessAction(_, _) => 0.0
        case AccessChooseAction(_, c) => g.isInstalled(c).?(2.0).|(1.0)
        case AccessAbilityAction(_, s, c) => g.impl(s).accessAbility(s, c)./(x => abilityValue(x, s) + (fxs(x.effects) == 0).?#(8.0)).|(0.0)
        case TrashProgramForMemoryAction(_, c) => -keepValue(c) - cardWorth(c)

        // ---- generic prompts ----
        case YesAction(_, prompt, yes) => fxs(yes) + 3 - promptCost(prompt)
        case NoAction(_, _, no) => fxs(no)
        case ChoiceAction(_, _, alt) => fxs(alt.effects) + random(3)
        case CardChosenAction(_, c, p) => cardChoice(c, p)
        case DeclineChoiceAction(_, _) => 0.0
        case DiscardCardsAction(_, l) => -l./(keepValue).sum
        case CardsChosenAction(_, l, p) => l./(c => cardChoice(c, p)).sum
        case AbilityCostCardsAction(_, _, _, _, l, _) => -l./(keepValue).sum
        case AccessAbilityCardsAction(_, _, _, l) => -l./(keepValue).sum
        case RunChoiceAction(_, s, _) => runValue(s)
        case ServerPickAction(_, _, Some(s), _) if me.isRunner => runValue(s) * 0.2 + random(3)
        case ServerChosenAction(_, c, s, _) => me.isCorp.?(corpInstall(c, s, true)).|(0.0)
        case ServerChosenPayingAction(_, c, s, _, _) => me.isCorp.?(corpInstall(c, s, true)).|(0.0)
        case ServerDiscountChosenAction(_, c, s, _, _) => me.isCorp.?(corpInstall(c, s, true)).|(0.0)
        case RotServerChosenAction(_, c, s, _, _, _, _) => me.isCorp.?(corpInstall(c, s, true)).|(0.0)

        // ---- traces, psi, prevention ----
        case TraceCorpAction(_, n) => traceCorp(n)
        case TraceRunnerAction(_, n) => traceRunner(n)
        case PsiBidAction(_, n) => random(10) - n * 3.0
        case SecretSpendAction(_, _, n) => random(10) - n * 3.0
        case PreventUseAction(f, c, i, kind, n) => prevent(c, i, kind, n)
        case PreventPassAction(_, _, _) => 0.0

        // final rules passes: credit sources (the manual options are NotForBots: the automatic order is always taken), Gagarin
        // Deep Space's pay-or-skip access cost, Hivemind counters, Tithonium's forfeit, Surveillance Sweep's first boost
        case CreditAutoAction(_) => 0.0
        case FrAccessPayAction(_, c) => (rootCardValue(c) - C * (credits <= 1).?(3.0).|(1.0)).atLeastD(-1)
        case FrAccessRefuseAction(_, _) => 0.0
        case FrAccessUnpaidAction(_, _) => 0.0
        case FrHiveSpendAction(_, _, _, _, k) => -k.toDouble
        // rules-accuracy pass: Hivemind's counters for breaks / pumps / abilities / effects (the program's own counters preferred),
        // the plays / installs only Patchwork's discount makes affordable (a grip card is trashed)
        case FrHiveCostAction(_, x, k) => score(x) - k * 0.5
        case FrHiveEffectAction(_, _, _, k) => -k.toDouble
        case PatchworkPlayAction(f, c) => score(PlayCardAction(f, c)) - 12.0
        case PatchworkInstallAction(f, c, h, t) => score(RunnerInstallAction(f, c, h, t)) - 12.0
        case FrRezForfeitAction(_, c, ctx) => rez(c, ctx) - 250.0
        case FrSurveillanceBoostAction(_, n) => traceRunner(n)

        case x =>
            SmartBot.unhandled += x.getClass.getSimpleName -> (SmartBot.unhandled.getOrElse(x.getClass.getSimpleName, 0) + 1)
            random(3)
    }

    def promptCost(prompt : String) : Double = {
        val l = prompt.toLowerCase
        "pay (\\d+) credit".r.findFirstMatchIn(l).map(_.group(1).toInt * C).|(0.0) +
        "pay (\\d+)\\[c".r.findFirstMatchIn(l).map(_.group(1).toInt * C).|(0.0)
    }

    // ---- setup ----

    def keepHand : Double = {
        val h = g.hand(me)
        if (me.isCorp) {
            val ice = h.count(_.data.isIce)
            val econ = h.count(c => prof(c).isEcon)
            val agendas = h.count(_.data.isAgenda)
            if (ice >= 1 && (econ >= 1 || ice >= 2) && agendas <= 2) 10 else -10
        }
        else {
            val econ = h.count(c => prof(c).isEcon)
            val breaker = h.count(_.data.isIcebreaker)
            if (econ >= 1 || breaker >= 2) 10 else -10
        }
    }

    // ---- Corp ----

    def playOperation(c : Card) : Double = {
        val p = prof(c)
        val cost = g.playCost(c)
        val after = credits - cost
        val poor = credits < 5
        if (p.tagPunish) {
            if (!g.tagged) -20.0
            else if (p.damage > 0) 60.0 + damageValue(p.damage)
            else 60.0
        }
        else if (p.gain > 0)
            (p.gain - cost) * CG * poor.?(1.5).|(1.0) - p.extraClick.?#(12.0) + 2
        else if (p.damage > 0)
            damageValue(p.damage) * 0.8 - cost * 3
        else if (p.draw > 0)
            p.draw * drawValue(Corp) - cost * 5
        else
            (after >= 2).?(8.0).|(-5.0) - cost * 2
    }

    // install value of a Corp card in a server (None = new remote)
    def corpInstall(c : Card, so : |[Server], free : Boolean) : Double = {
        val cost = free.?(0).|(g.installCost(c, so))
        val after = credits - cost
        val p = prof(c)
        val occupied = so.exists(s => s.isRemote && rootHasMain(s))
        if (c.data.isIce) {
            val need = iceNeed(so)
            val rez = g.rezCost(c)
            val etr = g.subs(c).exists(subETR)
            val afford = (after >= rez).?(6.0).|((after >= 1).?(-4.0).|(-12.0))
            val wasteOnTrap = so.exists(s => s.isRemote && g.root(s).exists(x => prof(x).trap)).?#(25.0)
            val rich = (after >= 12).?#(8.0) + (after >= 20).?#(8.0)
            need + afford + etr.?#(6.0) + (rez * 1.5).atMostD(9) - cost * 6 - wasteOnTrap + rich
        }
        else if (c.data.isAgenda) {
            if (occupied) -200.0
            else {
                val req = g.advancementRequirement(c)
                val clicksAfter = clicks - free.?(0).|(1)
                if (req <= clicksAfter && after >= req)
                    800.0 + so./(s => protection(|(s)) * 50).|(0.0)
                else {
                    val prot = so./(s => protection(|(s))).|(0.0)
                    val flood = (corpHandAgendas >= 3).?#(10.0 * (corpHandAgendas - 2))
                    // a rich Corp cannot wait for perfect protection: the Runner keeps building up too
                    val rich = credits >= 15
                    val iced = so.exists(s => g.ice(s).num >= 2)
                    if (prot >= 0.6) 140.0 + prot * 100 + flood
                    else if (prot >= 0.3) rich.?(60.0).|(20.0) + prot * 40 + flood
                    else if (rich && iced) 25.0 + flood
                    else -30.0 + flood
                }
            }
        }
        else if (c.data.isAsset) {
            if (occupied) -200.0
            else if (p.trap) so.forall(s => g.ice(s).num <= 1).?(18.0).|(6.0) + (g.turn > 2).?#(4.0)
            else if (p.isEcon) {
                val net = p.econCredits - g.rezCost(c)
                // keep the iced remotes for agendas
                val scoringRemote = so.exists(s => s.isRemote && g.ice(s).any && !rootHasMain(s))
                (30.0 + net * 2.5) * gainWorth(Corp) + scoringRemote.?(-25.0).|(0.0) - (after < g.rezCost(c)).?#(10.0)
            }
            else 12.0 - (after < 2).?#(8.0)
        }
        else if (c.data.isUpgrade) {
            so match {
                case Some(r : Remote) if g.root(r).exists(_.data.isAgenda) || g.ice(r).any => 14.0
                case Some(HQ) | Some(RnD) => 9.0
                case None => 2.0
                case _ => 4.0
            }
        }
        else 0.0
    }

    def advance(c : Card) : Double = {
        if (c.data.isAgenda) {
            val rem = g.advancementRequirement(c) - g.advancement(c)
            if (rem <= clicks && credits >= rem)
                700.0 + g.advancement(c)
            else {
                val prot = g.serverOf(c)./(s => protection(|(s))).|(0.0)
                if (prot >= 0.6 && credits >= 2) 120.0 + 10 * g.advancement(c)
                else if (prot >= 0.3 && credits >= 3) 22.0
                else -10.0
            }
        }
        else {
            val p = prof(c)
            if (p.trap && g.isInstalled(c) && credits >= 3) 26.0 - g.advancement(c) * 4
            else if (c.data.isIce) 4.0
            else 2.0
        }
    }

    def rez(c : Card, ctx : String) : Double = {
        val cost = g.rezCost(c)
        val after = credits - cost
        if (c.data.isIce) {
            if (ctx.contains("approach-ice") && g.run.exists(r => r.ice.has(c))) {
                val s = g.run.get.server
                val st = stake(s)
                val subs = g.subs(c)
                val useful = subs.exists(subETR) || subs.exists(x => subHarm(x) >= 25)
                val pendingAdv = installedAgendas./(x => g.advancementRequirement(x) - g.advancement(x)).minOr(99)
                val reserve = (pendingAdv <= 3).?(pendingAdv).|(0)
                if (st <= 0) -10.0
                else if (!useful) 5.0 - cost * 2
                else 35.0 + st * 0.4 - cost * 3 - (after < reserve).?#(30.0)
            }
            else -20.0
        }
        else if (c.data.isAsset || c.data.isUpgrade) {
            val p = prof(c)
            if (p.trap) -15.0
            else if (p.isEcon) {
                // assets used with clicks are rezzed when needed; "when your turn begins" assets at the last moment
                // (end of the Runner's turn or start of the Corp's turn) so the Runner has less time to trash them
                val clickUse = g.impl(c).abilities(c).exists(_.click)
                val lastMoment = (ctx == "window:discard" && g.active.isRunner) || ctx == "window:turn-start" || ctx == "window:turn-begins"
                if (clickUse || lastMoment) (35.0 + p.econCredits * 1.5) * gainWorth(Corp) - cost * 2
                else -2.0
            }
            else if (c.data.isUpgrade) {
                val s = g.serverOf(c)
                val running = g.run.exists(r => s.has(r.server))
                (running && s./(stake).|(0.0) >= 100 && after >= 2).?(10.0 - cost * 2).|(-5.0)
            }
            else if (g.impl(c).abilities(c).any || c.data.text.toLowerCase.contains("when your turn begins")) 12.0 - cost * 2 - (after < 3).?#(10.0)
            else -5.0
        }
        else -10.0
    }

    def trashResource(c : Card) : Double =
        if (!g.tagged) -50.0
        else 30.0 + prof(c).isEcon.?#(15.0) + c.data.cost.|(0) * 3.0 - (credits < 4).?#(15.0)

    def traceCorp(n : Int) : Double = {
        val runnerMax = g.link + g.creditsFor(Runner, Spend.trace).atMost(g.credits(Runner))
        val base = g.traceBase
        val worth = fxs(g.traceSuccess) - fxs(g.traceFail)
        val needed = (runnerMax + 1 - base).atLeast(0)
        if (needed == 0) -n * 10.0
        else if (n == needed && worth > n * C * 0.7) 50.0
        else if (n == 0) 0.0
        else -n * 10.0
    }

    // ---- Runner ----

    def playEvent(c : Card) : Double = {
        val p = prof(c)
        val cost = g.playCost(c)
        val poor = credits < 5
        if (p.gain > 0 && !p.runEvent)
            (p.gain - cost) * CG * poor.?(1.4).|(1.0) - p.extraClick.?#(12.0) + 2 + p.draw * drawValue(Runner)
        else if (p.runEvent) {
            val v = bestRunValue
            (v > 15).?(v + 8 + p.gain * C * 0.5 - cost * C * 0.5).|(-10.0)
        }
        else if (p.removeTags > 0)
            g.tagged.?(p.removeTags.atMost(g.tags) * tagValue + 5 - cost * C * 0.5).|(-20.0)
        else if (p.draw > 0)
            p.draw * drawValue(Runner) - cost * 6
        else
            (credits - cost >= 3).?(6.0).|(-6.0) - cost * 2
    }

    def runnerInstall(c : Card, host : |[Card], trash : |[Card]) : Double = {
        val p = prof(c)
        val cost = g.installCost(c, None)
        val after = credits - cost
        val d = c.data
        val dup = d.unique && g.installedCards(Runner).exists(x => x.title == c.title)
        val trashPenalty = trash./(t => keepValue(t) + cardWorth(t)).|(0.0)
        val base =
            if (dup) -60.0
            else if (d.isIcebreaker) {
                val types = cardBreakerTypes(c)
                val newTypes = types.%(t => !covers(t))
                if (newTypes.any) {
                    val seen = g.rezzedIce.%(i => newTypes.has("*") || newTypes.exists(t => g.has(i, t))).num
                    val n = newTypes.has("*").?(3 - coverage).|(newTypes.num)
                    40.0 + 18 * n + 8 * seen
                }
                else if (types.none && breakers.none) 25.0
                else 3.0
            }
            else if (p.isEcon)
                (p.econCredits - cost) * CG * 0.8 + 4
            else if (p.mu > 0) {
                val needMu = g.hand(Runner).exists(x => x.data.isProgram && x.data.memory.|(0) > g.memoryFree)
                needMu.?(35.0).|(10.0)
            }
            else if (d.isProgram && d.has("Virus")) 16.0
            else if (d.has("Console")) 28.0
            else if (p.link > 0) 12.0
            else 13.0
        val poorPenalty = (after < 2 && !p.isEcon && !(d.isIcebreaker && base > 30)).?#(15.0)
        base - trashPenalty - poorPenalty - cost * 1.5
    }

    def removeTag : Double =
        if (g.tags <= 0) -50.0
        else 18.0 + 6 * g.installedCards(Runner).%(_.data.isResource).num + (g.credits(Corp) >= 4).?#(10.0) + (g.tags >= 2).?#(8.0) - (credits < 4).?#(10.0)

    def payTrash(c : Card) : Double = {
        val t = g.trashCost(c).|(0)
        val p = prof(c)
        val worth = 22.0 + p.isEcon.?#(35.0) + g.isRezzed(c).?#(10.0) + c.data.isUpgrade.?#(15.0) + c.data.cost.|(0) * 3.0 + p.trap.?#(12.0)
        val after = credits - t
        worth - t * C * 0.8 - (after < 2).?#(15.0)
    }

    def traceRunner(n : Int) : Double = {
        val strength = g.traceStrength
        val needed = (strength - g.link).atLeast(0)
        val harm = fxs(g.traceFail) - fxs(g.traceSuccess)
        if (needed == 0) -n * 10.0
        else if (n == needed && harm > n * C * 0.8) 50.0
        else if (n == 0) 0.0
        else -n * 10.0
    }

    def prevent(c : Card, i : Int, kind : String, n : Int) : Double = {
        val a = g.impl(c).preventions(c, kind, n).lift(i)
        val cost = a./(x => costValue(x.costs, c)).|(0.0)
        val worth = kind match {
            case "net" | "meat" | "core" | "brain" => damageValue(n).atMostD(200)
            case "tag" => tagValue * n
            case "trash" => 40.0
            case "end-run" => currentRunValue.atLeastD(0) + 10
            case "bad-pub" => 25.0
            // "when a trace is initiated" (Flip Switch, Disrupter): the base strength becomes 0
            case "trace" => (me.isRunner && g.traceBase > 0).?#(g.traceBase * C + 20)
            case _ => 15.0
        }
        worth * (me.isRunner || kind == "trash").?(1.0).|(0.8) - cost
    }

    def bioroidValue(ice : Card, a : Ability) : Double = {
        val subs = g.subs(ice)
        val unbroken = g.unbrokenSubs(ice)
        val etr = unbroken.%(i => subETR(subs(i)))
        val cost = costValue(a.costs, ice)
        if (etr.num == 1 && encounter.bestPlan.none) currentRunValue - cost
        else if (etr.none && unbroken.exists(i => subHarm(subs(i)) > cost)) unbroken./(i => subHarm(subs(i))).max - cost
        else -10.0
    }

    def encounterAbilityBonus(a : Ability) : Double = {
        if (g.run.exists(_.phase == EncounterPhase) && me.isRunner) {
            val lowers = a.effects.collect { case PumpIceEffect(_, n, _) if n < 0 => -n }.sum
            if (lowers > 0 && encounter.bestPlan.forall(_._3 > 0)) lowers * 12.0 else 0.0
        }
        else 0.0
    }

    // card chosen for a purpose: own cards to give up get their keep value subtracted, the opponent's are targets
    def cardChoice(c : Card, p : Purpose) : Double = {
        val mine = g.owner(c) == me
        val prompt = g.current match {
            case ChooseCardEffect(_, _, _, pr, _) => pr.toLowerCase
            case _ => ""
        }
        p match {
            case TrashPurpose(_) | DiscardPurpose(_) | DamagePickPurpose(_) => mine.?(-keepValue(c) - g.isInstalled(c).?#(cardWorth(c))).|(cardWorth(c))
            case AddToHandPurpose(_, _) | InstallPurpose(_, _) | HostInstallPurpose(_, _) => mine.?(keepValue(c) + 10).|(5.0)
            case AdvancePurpose(_) => c.data.isAgenda.?(50.0 + g.advancement(c) * 10).|(5.0)
            case RezPurpose(_) => c.data.isIce.?(20.0 + g.rezCost(c) * 3).|(10.0 + g.rezCost(c))
            case ShuffleIntoDeckPurpose(_) => mine.?(c.data.isAgenda.?(30.0).|(keepValue(c) * 0.5)).|(cardWorth(c))
            case _ =>
                val bad = prompt.contains("trash") || prompt.contains("discard") || prompt.contains("derez") || prompt.contains("remove") || prompt.contains("return") || prompt.contains("forfeit") || prompt.contains("lose")
                if (mine) bad.?(-keepValue(c) - cardWorth(c) * 0.5).|(keepValue(c) + cardWorth(c) * 0.3)
                else cardWorth(c) + random(3)
        }
    }

    // ---- encounters: plan the cheapest way through the current ice ----

    object encounter {
        lazy val ice : |[Card] = g.run.filter(!_.ended)./~(_.ice)
        lazy val subs : $[Sub] = ice./(g.subs).|($)
        lazy val unbroken : $[Int] = ice./(g.unbrokenSubs).|($)
        lazy val str : Int = ice./(g.iceStrength).|(0)
        // value of the rest of the run after this encounter
        lazy val after : Double = g.run.filter(!_.ended)./ { r =>
            val est = estimate(r.server, iceAhead(r.server, r.position - 1))
            est.pass * accessValue(r.server) - est.cost * C - est.harm
        }.|(0.0)
        lazy val etrLoss : Double = after.atLeastD(0) + 20
        lazy val continueValue : Double = -firingHarm(subs, unbroken, etrLoss)
        lazy val budget : Int = runCredits

        // (breaker, subs to break, credits, value)
        lazy val plans : $[(Card, $[Int], Int, Double)] = ice.$./~ { c =>
            val harmful = unbroken.%(i => subETR(subs(i)) || subHarm(subs(i)) > 0)
            val etrOnly = unbroken.%(i => subETR(subs(i)))
            val sets = $(harmful, etrOnly, unbroken.%(i => !subETR(subs(i)) && subHarm(subs(i)) >= 20)).%(_.any).distinct
            breakers./~ { b =>
                sets./~ { set =>
                    breakCost(b, c, str, set.num).%(_ <= budget)./(k => (b, set, k, -k * C - firingHarm(subs, unbroken.diff(set), etrLoss)))
                }
            }
        }

        lazy val bestPlan : |[(Card, $[Int], Int, Double)] = plans.sortBy(-_._4).starting.%(_._4 > continueValue + 1)

        def pump(b : Card, pi : Int) : Double = bestPlan match {
            case Some((pb, _, _, v)) if pb == b && g.breakerStrength(b) < str =>
                val p = g.impl(b).pumps(b).lift(pi)
                val eff = p./(x => creditsIn(g.pumpCostsWith(b, x.costs)).toDouble / x.strength.atLeast(1)).|(9.0)
                200.0 - eff
            case _ => continueValue - 50.0
        }

        def breakOne(b : Card, bi : Int, i : Int) : Double = bestPlan match {
            case Some((pb, set, _, _)) if pb == b && set.has(i) => 150.0 + subETR(subs(i)).?#(10.0) - creditsIn(g.impl(b).breaks(b).apply(bi).costs)
            case _ => continueValue - 50.0
        }

        def breakMany(b : Card, bi : Int, l : $[Int]) : Double = bestPlan match {
            case Some((pb, set, _, _)) if pb == b && l.forall(set.has) => 155.0 + l.num * 2 - creditsIn(g.breakCosts(g.impl(b).breaks(b).apply(bi), l.num))
            case Some((pb, set, _, _)) if pb == b && set.forall(l.has) => 150.0 + set.num - creditsIn(g.breakCosts(g.impl(b).breaks(b).apply(bi), l.num))
            case _ => continueValue - 50.0
        }
    }
}
