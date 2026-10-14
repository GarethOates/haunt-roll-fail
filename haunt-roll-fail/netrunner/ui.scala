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

import org.scalajs.dom

import hrf.elem._
import hrf.html._
import hrf.canvas._
import hrf.web._
import hrf.ui._
import hrf.ui.again._

import netrunner.elem._

// a card of the viewer's hand, shown in the action pane (display only: never submitted or serialized)
case class HandCardInfoAction(self : Faction, card : Card)(q : Elem, face : Elem) extends ElemAction(q)(face) with Info with ViewObject[Card] with OnClickInfo {
    def obj = card
    def param = card
}

object UI extends BaseUI {
    val mmeta = Meta

    def create(uir : ElementAttachmentPoint, arity : Int, options : $[mmeta.O], resources : Resources, title : String, callbacks : hrf.Callbacks) = new UI(uir, arity, resources, callbacks)
}

class UI(val uir : ElementAttachmentPoint, arity : Int, val resources : Resources, callbacks : hrf.Callbacks) extends GUI {
    val corpPane = newPane("corp", Content, styles.status, ExternalStyle("hide-scrollbar"))
    val runnerPane = newPane("runner", Content, styles.status, ExternalStyle("hide-scrollbar"))
    val overlayPane = newOuterPane("overlay", Content)

    overlayPane.invis()

    // the latest ask of the viewer: used to tie clicks on the board to actions
    var lastThen : UserAction => Unit = null
    var lastActions : $[UserAction] = $

    def factionElem(f : Faction) = f.name.styled(f)

    // ---------------------------------------------------------------------------------------------------------------
    // Visibility
    // ---------------------------------------------------------------------------------------------------------------

    // Who sees what. Every client has the whole game state, hidden information is hidden by the UI only.
    // * A seated viewer (lastSelf: the player of this link, the human of a local game against a bot, or the player
    //   being asked in a hotseat game) sees their own hand and their own unrezzed / facedown cards, plus everything public.
    // * A viewer without a seat in a local game (bot-vs-bot, or a hotseat game between prompts: the journal is in
    //   memory, callbacks.canPlayAgain) or in a saved replay file (HRF.replay, offered once the game is over) sees
    //   everything ("omniscient").
    // * Any other viewer without a seat (the spectator link of an online game) sees public information only: both
    //   hands as counts and card backs, unrezzed ice / Corp cards and facedown cards as backs, the facedown part of
    //   Archives as a count, never the order of R&D / the stack. Secret choices (psi bids, secret credit spends) are not
    //   on the board; the log only says that a player bid / spent secretly until the reveal.
    //   The seat is lastSelf while the viewer is asked or waits; the framework asks with no faction on the game-over
    //   screen, so the seats this client played are remembered (seats) and, after reloading a finished online game (no
    //   ask or wait happens before the game-over screen), read from the page title that HRF sets to
    //   "<faction> - <game title> - Netrunner" for a player link (a spectator's title has no faction).
    var seats : $[Faction] = $

    def titleSeat : |[Faction] = hrf.HRF.lobby.any.??(dom.document.title.split(" - ").$ match {
        case f :: _ :: _ :: _ => Meta.parseFaction(f)
        case _ => None
    })

    def viewer : |[Faction] = lastSelf || seats.single || seats.none.??(titleSeat)

    def omniscient : Boolean = viewer.none && (callbacks.canPlayAgain || hrf.HRF.replay)

    // the viewer sees the hidden information of side f
    def sees(f : Faction) : Boolean = omniscient || viewer.contains(f)

    def canSee(c : Card) : Boolean = {
        val g = game
        // cards accessed during the current run are revealed
        if (g.run.exists(r => !r.ended && r.accessed.has(c)))
            return true
        if (omniscient)
            return true
        val owner = g.owner(c)
        val mine = viewer.contains(owner)
        g.zone(c) match {
            case HandZone(f) => viewer.contains(f)
            case DeckZone(f) => false
            case DiscardZone(Corp) => sees(Corp) || g.state(c).seen
            case DiscardZone(Runner) => true
            // installed cards the Runner accessed stay known to the Runner while they stay where they are (FixRules.fxRunnerKnows)
            case RootZone(_) | IceZone(_) => sees(Corp) || g.isRezzed(c) || g.state(c).seen || (viewer.contains(Runner) && g.fxRunnerKnows(c))
            // cards set aside on an identity (Ayla) are known to their owner only
            case HostedZone(h) if g.zone(h).is[IdentityZone] => mine || g.state(c).seen
            case HostedZone(h) => mine || (owner.isCorp && (g.isRezzed(c) || g.state(c).seen)) || (owner.isRunner && !g.state(c).facedown)
            // cards hosted without being installed (Bling, Detente, Read-Write Share, Asmund Pudlat, Matryoshka...)
            case StashZone(_) => mine || g.state(c).seen || (owner.isRunner && !g.state(c).facedown)
            case RigZone => sees(Runner) || !g.state(c).facedown
            case RemovedZone(_) => mine || g.state(c).seen || owner.isRunner
            case _ => true
        }
    }

    def back(c : Card) = game.owner(c).isCorp.?("corp-back").|("runner-back")

    // ---------------------------------------------------------------------------------------------------------------
    // Card images with a fallback for missing assets
    // ---------------------------------------------------------------------------------------------------------------

    // true when the image of a card is known to be unavailable: not declared, not embedded in a saved replay file
    // (only the cards of the game are), or its load failed (printings without an image on NetrunnerDB get a generated
    // one, see tools/make-placeholder-cards.py)
    def imageMissing(code : String) : Boolean = {
        val images = resources.images
        if (images.has(code))
            return false
        if (!images.hasSource(code))
            return true
        if (hrf.HRF.embedded)
            return true
        try { images.loader.error(images.getSource(code)) } catch { case e : Throwable => true }
    }

    // a card image, or a card back with the title when the image is not available
    def cardFace(code : String, style : Style, extra : $[Style] = $) : Elem = {
        val data = Cards.get(code)
        if (imageMissing(code)) {
            val back = data./(d => d.isCorp.?("corp-back").|("runner-back")).|("corp-back")
            val title = data./(_.title).|(code)
            val img = Image(back, style)
            Div(img.copy(styles = img.styles ++ extra) ~ title.spn(styles.placeholderTitle)((style == styles.cardTiny || style == styles.cardSmall || style == styles.cardHand).$(styles.placeholderSmall)), styles.placeholder)
        }
        else {
            val img = Image(code, style)
            img.copy(styles = img.styles ++ extra)
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Cards on the board
    // ---------------------------------------------------------------------------------------------------------------

    def clickable(e : Elem, p : Any) : Elem = Parameter(p, OnOverOut(OnClick(e)))

    def cardImage(c : Card, style : Style, extra : $[Style] = $) : Elem = {
        val g = game
        val visible = canSee(c)
        val z = g.zone(c)
        val inPlay = g.inPlayZone(z)
        val unrezzed = inPlay && g.owner(c).isCorp && !g.isRezzed(c)
        val facedown = (inPlay || z.is[StashZone]) && g.state(c).facedown
        val styled =
            if (visible)
                cardFace(c.code, style, extra ++ unrezzed.$(styles.unrezzed) ++ facedown.$(styles.facedown) :+ styles.actionable)
            else {
                val img = Image(back(c), style)
                img.copy(styles = img.styles ++ extra :+ styles.actionable)
            }
        clickable(styled, c)
    }

    val standardCounters = $("advancement", "virus", "credit", "power", "agenda")

    def badges(c : Card) : Elem = {
        val g = game
        val s = g.state(c)
        val visible = canSee(c)
        val other = s.counters.toList.filter { case (k, n) => !standardCounters.has(k) && n > 0 }.sortBy(_._1)
        val l =
            (s.advancement > 0).$((s.advancement + " adv").spn(styles.badge)(styles.badgeAdv)) ++
            (s.virus > 0).$((s.virus + " virus").spn(styles.badge)(styles.badgeVirus)) ++
            (s.credits > 0).$((s.credits + "c").spn(styles.badge)(styles.badgeCredit)) ++
            (s.power > 0).$((s.power + " power").spn(styles.badge)(styles.badgePower)) ++
            (s.counter("agenda") > 0).$((s.counter("agenda") + " agenda").spn(styles.badge)(styles.badgePower)) ++
            other.map { case (k, n) => (n + " " + counterLabel(k)).spn(styles.badge)(styles.badgePower) } ++
            g.hostedBadPub.get(c).%(_ > 0).$./(n => (n + " bad pub").spn(styles.badge)(styles.badgeMark)) ++
            (visible && c.data.isIce && g.isRezzed(c) && g.iceStrength(c) != c.data.strength.|(0)).$(("str " + g.iceStrength(c)).spn(styles.badge)(styles.badgeStr)) ++
            (visible && c.data.isIcebreaker && g.breakerStrength(c) != c.data.strength.|(0)).$(("str " + g.breakerStrength(c)).spn(styles.badge)(styles.badgeStr))
        l.any.?(Div(l.merge, styles.badges)).|(Empty)
    }

    def counterLabel(k : String) = k match {
        case "badpub" => "bad pub"
        case k => k.replace("-", " ")
    }

    // per-card lingering flags worth showing on the board
    def flagLabels(c : Card) : $[String] = {
        val g = game
        if (g.flags.none)
            return $
        $("disabled", "cannot-break", "net-subs", "max-break-1", "cannot-score").%(k => g.hasFlag(k, c))./ {
            case "net-subs" => "net damage subs"
            case "max-break-1" => "break 1 sub max"
            case k => k.replace("-", " ")
        }
    }

    def tagLabel(c : Card, tiny : Boolean = false) : Elem = {
        val g = game
        val z = g.zone(c)
        val inPlay = g.inPlayZone(z)
        val visible = canSee(c)
        val base : $[String] =
            if (z.is[HostedZone] && z.as[HostedZone].exists(hz => g.zone(hz.host).is[IdentityZone]))
                $("set aside")
            else if (z.is[StashZone] || (z.is[HostedZone] && g.state(c).uninstalled))
                $((visible && !g.state(c).facedown).?("not installed").|("facedown"))
            else if (inPlay && g.owner(c).isCorp && !g.isRezzed(c) && visible && !c.data.isAgenda)
                $("unrezzed")
            else if (inPlay && g.owner(c).isRunner && g.state(c).facedown && visible)
                $("facedown")
            else
                $
        val l : $[String] = base ++ visible.??(flagLabels(c))
        // on tiny (hosted) cards a card back or a dimmed facedown card speaks for itself and the label is smaller
        if (tiny && (!visible || l == $("facedown")))
            Empty
        else
            l.any.?(l.join(", ").spn(styles.tag)(tiny.$(styles.tagTiny))).|(Empty)
    }

    def highlightOf(c : Card) : $[Style] = {
        val g = game
        val encountered = g.run.exists(r => !r.ended && r.ice.has(c) && (r.phase == EncounterPhase || r.phase == ApproachIcePhase))
        encountered.$(styles.encountered) ++ (hasActions(c) && !encountered).$(styles.highlighted)
    }

    // cards shown under a card: installed hosted cards, then cards hosted without being installed (StashZone)
    def hostedElems(c : Card, depth : Int = 0) : Elem = {
        val g = game
        if (depth > 8)
            return Empty
        val l = (g.hosted(c) ++ g.stashed(c)).distinct./(h => slot(h, styles.cardTiny, depth + 1))
        l.some./(l => Div(l.merge, styles.hostedRow)).|(Empty)
    }

    def slot(c : Card, style : Style = styles.card, depth : Int = 0) : Elem = {
        Div(cardImage(c, style, highlightOf(c)) ~ tagLabel(c, style == styles.cardTiny) ~ badges(c) ~ hostedElems(c, depth), styles.slot)
    }

    def iceSlot(c : Card) : Elem = {
        Div(Div(cardImage(c, styles.cardIceImg, highlightOf(c)) ~ tagLabel(c), styles.iceBox) ~ badges(c) ~ hostedElems(c), styles.slot)
    }

    def identitySlot(c : Card) : Elem = {
        Div(cardImage(c, styles.cardSmall, highlightOf(c)) ~ tagLabel(c) ~ badges(c) ~ hostedElems(c), styles.slot)
    }

    def pileImage(id : String, count : Int, label : String, p : Any) : Elem =
        Div(clickable(Image(id, styles.cardSmall)(styles.actionable), p) ~ (count + " " + label).spn(styles.serverCount), styles.slot)

    // ---------------------------------------------------------------------------------------------------------------
    // Servers
    // ---------------------------------------------------------------------------------------------------------------

    // Servers are drawn from the Runner's point of view (the Corp board is above the Runner board): the root (central
    // pile / installed cards) at the top of the box, the ice below it, innermost ice just under the root and outermost
    // ice at the bottom, closest to the Runner. The "Runner" marker sits below the piece of ice being approached /
    // encountered, or between the root and the innermost ice once the Runner approaches the server.
    def serverElem(s : Server) : Elem = {
        val g = game
        val r = g.run.%(r => r.server == s && !r.ended && !g.isOver)
        val iceList = g.ice(s)
        val position = r./(_.position).|(-1)
        val approachingServer = r.exists(x => x.position == 0 || x.phase == ApproachServerPhase || x.phase == AccessPhase || x.phase == SuccessPhase)
        val iceElems = iceList.indexed./ { (c, i) =>
            val here = r.any && position == i + 1 && !approachingServer
            here.?(iceSlot(c) ~ Div("Runner".spn(styles.marker))).|(iceSlot(c))
        }
        val rootElems = g.root(s)./(c => slot(c, styles.cardSmall))
        val central = s match {
            case HQ => pileImage("corp-back", g.hand(Corp).num, "cards", "hq")
            case RnD => pileImage("corp-back", g.deck(Corp).num, "cards", "rnd")
            case Archives =>
                val l = g.discard(Corp)
                val faceup = l.%(c => g.state(c).seen)
                val top = l.lastOption./(c => (g.state(c).seen || sees(Corp)).?(c.code).|("corp-back")).|("corp-back")
                Div(clickable(cardFace(top, styles.cardSmall, $(styles.actionable) ++ l.none.$(styles.unrezzed)), "archives") ~ (l.num + " cards" + (l.num > faceup.num).??(" (" + (l.num - faceup.num) + " facedown)")).spn(styles.serverCount), styles.slot)
            case _ => Empty
        }
        val marker = approachingServer.?(Div("Runner".spn(styles.marker))).|(Empty)
        val mark = g.mark.has(s).?(" (mark)".spn(styles.markLabel)).|(Empty)
        val name = clickable(Span(s.name.txt ~ mark, styles.serverName)(hasServerActions(s).$(styles.highlighted))(styles.actionable), s)
        Div(
            name ~
            Div(central ~ rootElems.merge, styles.rootRow) ~
            marker ~
            Div(iceElems.merge, styles.iceColumn),
            styles.server
        )(r.any.$(styles.serverRun))
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Boards
    // ---------------------------------------------------------------------------------------------------------------

    def stat(value : Any, label : String, style : Style) : Elem = Span(value.toString.spn(styles.statValue) ~ " " ~ label, styles.stat, style)

    // a key number of the status banner: a large value over a small label
    def bigStat(value : Any, label : String, style : Style, emphasis : Boolean, extra : $[Style] = $) : Elem =
        Span(Span(value.toString.txt, styles.bigValue) ~ Span(label.txt, styles.bigLabel), styles.bigStat, style)(emphasis.$(styles.bigEmphasis) ++ extra)

    // The per-player status banner: name and identity, then the key numbers (credits, clicks, agenda points; tags or
    // bad publicity when there are any) as large badges, then the other stats small. The active player's banner is
    // tinted with their colour and their credits / clicks are outlined.
    def statusBanner(f : Faction, alert : Elem, small : Elem) : Elem = {
        val g = game
        val id = g.identity.get(f)
        val active = g.active == f && g.turnStarted && !g.isOver
        val credits = g.credits(f)
        val clicks = g.clicks(f)
        val points = g.agendaPoints(f)
        val who = Span(
            Span(playerName(f), styles.bannerName) ~
            id./(c => Span(Span(c.title.txt, styles.get(NRFaction.of(c.data.faction))), styles.bannerId)).|(Empty),
            styles.bannerWho)
        Div(
            who ~
            activeMark(f) ~
            bigStat(credits, (credits == 1).?("credit").|("credits"), styles.bigCredit, active) ~
            bigStat(clicks, (clicks == 1).?("click").|("clicks"), styles.bigClick, active && clicks > 0, (active && clicks == 0).$(styles.bigSpent)) ~
            bigStat(points, (points == 1).?("point").|("points"), styles.bigPoints, false) ~
            alert ~
            extraClicksStat(f) ~
            Span(small, styles.bannerSmall),
            styles.header)(active.$(f.isCorp.?(styles.headerActiveCorp).|(styles.headerActiveRunner)))
    }

    def playerName(f : Faction) : Elem = f.name.styled(f)(styles.name) ~ resources.getName(f)./(n => n.spn(styles.dim)(styles.headerItem)).|(Empty)

    def activeMark(f : Faction) : Elem = {
        val g = game
        (g.active == f && g.turnStarted && !g.isOver).?(("turn " + g.turn).spn(styles.active)).|(Empty)
    }

    // the threat level is shown only while a card in play refers to it
    def threatMatters : Boolean = {
        val g = game
        (g.installedCards(Corp) ++ g.installedCards(Runner) ++ g.identity.values.$ ++ g.scored(Corp) ++ g.scored(Runner)).exists(_.data.text.contains("Threat"))
    }

    // allotted clicks for the next turn (Wage Workers and the like)
    def extraClicksStat(f : Faction) : Elem = {
        val n = game.extraClicks.get(f).|(0)
        (n != 0).?(stat((n > 0).?("+" + n).|("" + n), "clicks next turn", styles.statClick)).|(Empty)
    }

    // game-level lingering flags (not tied to a card)
    def globalFlagsLine : Elem = {
        val g = game
        val l = g.activeFlags.%(f => f.card.none && !$("encountered", "subs-broken", "installed", "advanced").has(f.key))./(f => f.key.replace("-", " ") + f.server./(s => " (" + s.name + ")").|(""))
        val cannotScore = g.cannotScoreThisTurn.$("cannot score this turn")
        val all = (l ++ cannotScore).distinct
        all.any.?(Div(("in effect: " + all.join(", ")).spn(styles.promptLine))).|(Empty)
    }

    def section(title : String, l : $[Elem]) : Elem = l.any.?(Div(title.spn(styles.sectionTitle) ~ Div(l.merge, styles.row), styles.section)).|(Empty)

    // operations / events that stay in the play area (lockdowns, "for the remainder of the turn", the card being resolved)
    def playAreaSection(f : Faction) : Elem = section("play area", game.at(PlayZone(f))./(c => slot(c, styles.cardTiny)))

    // cards removed from the game: a pile with the count, clicking opens the list
    def removedPile(f : Faction) : Elem = {
        val g = game
        val l = g.removed(f)
        if (l.none)
            return Empty
        val top = l.last
        Div(clickable(canSee(top).?(cardFace(top.code, styles.cardTiny, $(styles.actionable, styles.unrezzed))).|(Image(back(top), styles.cardTiny)(styles.unrezzed)(styles.actionable)), ("removed", f)) ~ (l.num + " removed").spn(styles.serverCount), styles.slot)
    }

    def corpBoard : Elem = {
        val g = game
        val id = g.identity.get(Corp)
        val header = statusBanner(Corp,
            (g.badPublicity > 0).?(bigStat(g.badPublicity, "bad pub", styles.bigTag, false)).|(Empty),
            (g.threatLevel > 0 && threatMatters).?(stat(g.threatLevel, "threat", styles.statPoints)).|(Empty) ~
            stat(g.hand(Corp).num + "/" + g.handSize(Corp), "hand", styles.statHand))
        if (g.phase == "setup")
            return header ~ deckSelection(Corp)
        val idImg = id./(identitySlot).|(Empty)
        val scored = g.scored(Corp)./(c => slot(c, styles.cardTiny))
        val scoredSection = section("scored", scored)
        val servers = Div(g.servers./(serverElem).merge, styles.serversRow)

        // the hands are drawn in the action pane (see preinfo), the header shows the hand size
        header ~ globalFlagsLine ~ Div(Div(Div(idImg ~ scoredSection ~ playAreaSection(Corp) ~ removedPile(Corp), styles.section) ~ servers, styles.row), styles.boardArea)
    }

    def runnerBoard : Elem = {
        val g = game
        val id = g.identity.get(Runner)
        val header = statusBanner(Runner,
            (g.tags > 0).?(bigStat(g.tags, (g.tags == 1).?("tag").|("tags"), styles.bigTag, false)).|(Empty),
            (g.tags == 0).?(stat(0, "tags", styles.statTag)).|(Empty) ~
            stat(g.link, "link", styles.statLink) ~
            stat(g.memoryUsed + "/" + g.memoryTotal, "MU", styles.statMU) ~
            stat(g.hand(Runner).num + "/" + g.handSize(Runner), "hand", styles.statHand) ~
            (g.coreDamage > 0).?(stat(g.coreDamage, "core damage", styles.statTag)).|(Empty) ~
            g.mark./(s => stat(s.name, "mark", styles.statMark)).|(Empty))
        if (g.phase == "setup")
            return header ~ deckSelection(Runner)
        val idImg = id./(identitySlot).|(Empty)
        val scored = g.scored(Runner)./(c => slot(c, styles.cardTiny))
        val scoredSection = section("stolen", scored)
        val programs = g.rig.%(_.data.isProgram)./(slot(_, styles.cardSmall))
        val hardware = g.rig.%(_.data.isHardware)./(slot(_, styles.cardSmall))
        val resourcesL = g.rig.%(_.data.isResource)./(slot(_, styles.cardSmall))
        val otherRig = g.rig.%(c => !c.data.isProgram && !c.data.isHardware && !c.data.isResource)./(slot(_, styles.cardSmall))
        val heap = g.discard(Runner)
        val piles = Div(
            Div(Image("runner-back", styles.cardSmall)(g.deck(Runner).none.?(styles.unrezzed)) ~ (g.deck(Runner).num + " stack").spn(styles.serverCount), styles.slot) ~
            Div(heap.any.?(clickable(cardFace(heap.last.code, styles.cardSmall, $(styles.actionable)), "heap")).|(Image("runner-back", styles.cardSmall)(styles.unrezzed)) ~ (heap.num + " heap").spn(styles.serverCount), styles.slot) ~
            removedPile(Runner),
            styles.row)
        val run = g.run.%(!_.ended).%(_ => !g.isOver)
        val runLine = run./(r => runStatus(r)).|(Empty)
        val accessed = run./(r => accessedElem(r)).|(Empty)

        // the hands are drawn in the action pane (see preinfo), the header shows the hand size
        header ~ runLine ~ Div(accessed ~ Div(Div(idImg ~ scoredSection ~ playAreaSection(Runner), styles.section) ~ Div(piles, styles.section) ~ section("programs", programs) ~ section("hardware", hardware) ~ section("resources", resourcesL) ~ section("installed", otherRig), styles.row), styles.boardArea)
    }

    def runStatus(r : Run) : Elem = {
        val g = game
        val ph = r.phase match {
            case InitiationPhase => "initiated".txt
            case ApproachIcePhase => "approaching " ~ r.ice./(c => (g.isRezzed(c) || sees(Corp)).?(g.cardElem(c)).|("unrezzed ice".hl)).|(Empty)
            case EncounterPhase => "encountering " ~ r.ice./(g.cardElem).|(Empty)
            case MovementPhase => "passed ice, moving on".txt
            case ApproachServerPhase => "approaching the server".txt
            case SuccessPhase => "successful".txt
            case AccessPhase => "breaching".txt
            case EndedPhase => "ended".txt
        }
        val n = r.position
        // lingering restrictions and bonuses of this run
        val notes : $[String] =
            r.cannotStealOrTrash.$("cannot steal or trash") ++
            (r.credits > 0).$(r.credits + " bad publicity credit" + (r.credits != 1).??("s")) ++
            r.preventAccess.$("no access") ++
            r.cannotJackOut.$("cannot jack out") ++
            r.disableRoot.$("root cards blank") ++
            r.accessBonus.toList.filter(_._2 != 0).sortBy(_._1).map(kv => (kv._2 > 0).?("+").|("") + kv._2 + " access" + (kv._1 != "total").??(" (" + kv._1 + ")"))
        Div("Run on " ~ r.server.elem ~ ": " ~ ph ~ ((n > 0 && r.phase != AccessPhase).?(" (" + n + " " + (n == 1).?("piece").|("pieces") + " of ice to pass)").|("")) ~ notes.any.?((" - " + notes.join(", ")).spn(styles.dim)).|(Empty), styles.runLine)
    }

    // cards accessed during the breach, on the Runner board
    def accessedElem(r : Run) : Elem = {
        val g = game
        val accessed = (r.phase == AccessPhase).??(r.accessed.%(c => g.location.contains(c)))
        accessed.any.?(Div("accessed".spn(styles.sectionTitle) ~ Div(accessed./(c => Div(cardImage(c, styles.cardSmall), styles.slot)).merge, styles.row), styles.section)).|(Empty)
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Hands: drawn in the action pane (like the Arcs / Coup hands), not on the boards
    // ---------------------------------------------------------------------------------------------------------------

    // hand cards already offered as choices of a selection prompt (discard to hand size, choose cards from HQ...)
    def selectingHand(f : Faction, aa : $[UserAction]) : Boolean = {
        val hand = game.hand(f)
        hand.any && aa.exists {
            case a : ViewObject[_] => a.obj match { case c : Card => hand.has(c) ; case _ => false }
            case _ => false
        }
    }

    def handInfos(self : |[Faction], aa : $[UserAction]) : $[HandCardInfoAction] = {
        val g = game
        if (g.phase == "setup" && g.hand(Corp).none && g.hand(Runner).none)
            return $
        // a seated player sees their own hand; a viewer without a seat sees both hands: faceup in a local game or a
        // replay file, as card backs for the spectator of an online game (see omniscient)
        val who = self || viewer
        val sides = who./(f => $(f)).|($(Corp, Runner))
        sides./~ { f =>
            val l = g.hand(f)
            val title = Break ~ (who.none.?(f.name.styled(f) ~ " ").|(Empty) ~ f.isCorp.?("HQ").|("Grip") ~ " (" ~ l.num.hl ~ " card".s(l.num) ~ ", maximum " ~ g.handSize(f).atLeast(0).hl ~ ")").spn(styles.handTitle)
            if (selectingHand(f, aa))
                $
            else
            if (sees(f))
                l./(c => HandCardInfoAction(f, c)(title, Div(cardFace(c.code, styles.cardHand, highlightOf(c)) ~ badges(c), styles.handSlot)))
            else
                l./(c => HandCardInfoAction(f, c)(title, Div(Image(back(c), styles.cardHand), styles.handSlot)))
        }
    }

    def handOptions(self : |[Faction], aa : $[UserAction]) : $[ZOption] = {
        val g = game
        handInfos(self, aa)./ { a =>
            val q = Div(a.question(g))(xlo.fullwidth)
            val ss = styleAction(self, aa, a, false, |(a.card))
            // click: the matching action of the current prompt, or its menu, or the zoom (same as a click on the board)
            ZOption(q, OnOverOut(OnClick(Div(a.option(g), ss))), _ => onClick(a.card), false, _ => onOver(a.card), _ => onOut(a.card))
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Deck selection
    // ---------------------------------------------------------------------------------------------------------------

    // preset names without set codes or other parenthesised suffixes ("Weyland BtL (rot-ice)" -> "Weyland BtL")
    def cleanDeckName(name : String) : String = name.replaceAll("\\s*\\([^)]*\\)\\s*$", "").trim

    // A deck selected in the gallery / the action pane list / the decklist import is first previewed: its cards are
    // drawn in the viewer's board pane and the action pane offers "Confirm <deck>". Only Confirm submits the
    // ChoosePresetDeckAction / ImportDeckAction of the current prompt; the preview is UI state only (not in the journal).
    case class DeckPreview(side : Faction, name : String, codes : $[String], preset : |[Int])

    var preview : |[DeckPreview] = None

    // the last decklist pasted in the import overlay (restored when it is reopened)
    var importText = ""

    def deckPrompt(f : Faction) : Boolean = lastThen != null && lastActions.exists {
        case ChoosePresetDeckAction(x, _) => x == f
        case ImportDeckMarker(x) => x == f
        case _ => false
    }

    def previewFor(f : Faction) : |[DeckPreview] = preview.%(_.side == f).%(_ => game.decks.contains(f).not && deckPrompt(f))

    def presetPreview(a : ChoosePresetDeckAction) = {
        val d = Decks.presets(a.index)
        DeckPreview(a.self, cleanDeckName(d.name), d.codes, |(a.index))
    }

    // the action of the current prompt that the Confirm button submits
    def confirmAction(p : DeckPreview) : |[UserAction] = p.preset match {
        case Some(i) => lastActions.of[ChoosePresetDeckAction].find(a => a.self == p.side && a.index == i)
        case None => lastActions.exists { case ImportDeckMarker(x) => x == p.side ; case _ => false }.?(ImportDeckAction(p.side, p.codes))
    }

    // re-render the board and the action pane after a UI-only change (preview set / cleared)
    def redrawAsk() {
        hideOverlay()
        if (lastThen != null)
            ask(lastSelf, lastActions, lastThen)
        else
            updateStatus()
    }

    def setPreview(p : |[DeckPreview]) {
        preview = p
        redrawAsk()
    }

    def deckSelection(f : Faction) : Elem = {
        val g = game
        val mine = viewer.forall(_ == f)
        g.decks.get(f) match {
            case Some(codes) =>
                val id = codes./(Cards.get)./~(x => x).%(_.isIdentity).starting
                Div(id./(c => Div(cardFace(c.code, styles.card), styles.slot)).|(Empty) ~ Div(("Deck chosen: " ~ id./(_.title).|("?").hl ~ " - " ~ (codes.num - 1).toString ~ " cards").spn), styles.row)
            case None if mine && previewFor(f).any =>
                deckPreviewElem(previewFor(f).get)
            case None if mine && lastActions.exists(_.is[ChoosePresetDeckAction]) =>
                val all = lastActions.of[ChoosePresetDeckAction].%(_.self == f)
                val presets = all.%(presetShown)
                val items = presets./ { a =>
                    val d = Decks.presets(a.index)
                    val idc = Cards.get(d.identity)
                    val check = Decks.validate(f, d.codes)
                    // the identity is on the image (hover zooms it) and in the action pane list; a click previews the deck
                    Parameter(("deck", a), OnOverOut(OnClick(Div(
                        idc./(c => cardFace(c.code, styles.cardGallery)).|(Empty) ~
                        cleanDeckName(d.name).spn(styles.galleryName) ~
                        (eraOf(a.index) + " | " + check.message).spn(styles.galleryInfo),
                        styles.galleryItem))))
                }
                val importItem = Parameter("import", OnClick(Div(Image(f.isCorp.?("corp-back").|("runner-back"), styles.cardGallery) ~ "Import a decklist".spn(styles.galleryName) ~ "paste a NetrunnerDB export".spn(styles.galleryInfo), styles.galleryItem)))
                val none = presets.none.?(Div("No preset deck matches these filters".spn(styles.dim), styles.galleryItem)).|(Empty)
                // only the gallery is scaled to the pane, never below a readable size (see fitPane): it scrolls instead
                Div(Div("Choose a deck".spn(styles.menuTitle) ~ (presets.num + " of " + all.num + " decks shown, click a deck to see its cards").spn(styles.dim)) ~ galleryTabs(f, all) ~ Div((items ++ $(none, importItem)).merge, styles.gallery), styles.boardArea)
            case None =>
                Div("choosing a deck...".spn(styles.dim))
        }
    }

    // Gallery filters (UI state per side): the faction of the identity and the era of the deck, as two rows of small
    // tabs with counts above the gallery; the action pane list shows the same decks.
    var galleryFaction : Map[Faction, String] = Map()
    var galleryEra : Map[Faction, String] = Map()

    val eras = $("Starter", "NSG era", "FFG era")

    // sets published by Null Signal Games (Downfall onwards); every other set is from the FFG era
    val nsgSets = Set("df", "mor", "urbp", "ur", "sm", "sg", "su21", "msbp", "ms", "ph", "tai", "rwr", "elev", "vp")

    val presetEras = scala.collection.mutable.Map[Int, String]()

    // Starter: System Gateway cards only; NSG era: only cards of NSG sets; FFG era: at least one card of an FFG set
    def eraOf(i : Int) : String = presetEras.getOrElseUpdate(i, {
        val cards = Decks.presets(i).codes.distinct./~(Cards.get)
        if (cards.forall(_.setCode == "sg")) "Starter"
        else if (cards.forall(c => nsgSets.contains(c.setCode))) "NSG era"
        else "FFG era"
    })

    def presetFaction(i : Int) : String = Cards.get(Decks.presets(i).identity)./(_.faction).|("Neutral")

    def factionOrder(f : Faction) : $[String] = f.isCorp.?($("Haas-Bioroid", "Jinteki", "NBN", "Weyland Consortium", "Neutral")).|($("Anarch", "Criminal", "Shaper", "Adam", "Apex", "Sunny Lebeau", "Neutral"))

    def shortFaction(s : String) : String = s match {
        case "Haas-Bioroid" => "HB"
        case "Weyland Consortium" => "Weyland"
        case "Sunny Lebeau" => "Sunny"
        case s => s
    }

    def presetShown(a : ChoosePresetDeckAction) : Boolean = {
        val fs = galleryFaction.get(a.self).|("")
        val es = galleryEra.get(a.self).|("")
        (fs == "" || presetFaction(a.index) == fs) && (es == "" || eraOf(a.index) == es)
    }

    def galleryTabs(f : Faction, presets : $[ChoosePresetDeckAction]) : Elem = {
        val fs = galleryFaction.get(f).|("")
        val es = galleryEra.get(f).|("")
        val byEra = presets.%(a => es == "" || eraOf(a.index) == es)
        val byFaction = presets.%(a => fs == "" || presetFaction(a.index) == fs)
        def tab(label : Elem, n : Int, on : Boolean, p : Any) : Elem =
            Parameter(p, OnClick(Span(label ~ (" " + n).spn(styles.tabCount), styles.tab)(on.$(styles.tabOn))))
        val factions = factionOrder(f).%(x => presets.exists(a => presetFaction(a.index) == x)) ++ presets./(a => presetFaction(a.index)).distinct.diff(factionOrder(f))
        val ftabs = tab("All factions".txt, byEra.num, fs == "", ("gallery-faction", f, "")) +:
            factions./(x => tab(Span(shortFaction(x).txt, styles.get(NRFaction.of(x))), byEra.%(a => presetFaction(a.index) == x).num, fs == x, ("gallery-faction", f, x)))
        val etabs = tab("All eras".txt, byFaction.num, es == "", ("gallery-era", f, "")) +:
            eras.%(e => presets.exists(a => eraOf(a.index) == e))./(e => tab(e.txt, byFaction.%(a => eraOf(a.index) == e).num, es == e, ("gallery-era", f, e)))
        Div(Div(ftabs.merge, styles.tabRow) ~ Div(etabs.merge, styles.tabRow))
    }

    val corpKinds = $("Agenda", "Asset", "Upgrade", "Operation", "ICE")
    val runnerKinds = $("Event", "Hardware", "Program", "Resource")

    def kindTitle(kind : String, n : Int) : String = (kind match {
        case "ICE" => "ice"
        case "Hardware" => "hardware"
        case "Agenda" => (n == 1).?("agenda").|("agendas")
        case k => k.toLowerCase + (n != 1).??("s")
    }) + " (" + n + ")"

    // the previewed deck: identity first, then the cards grouped by type with their counts (hover zooms a card)
    def deckPreviewElem(p : DeckPreview) : Elem = {
        val cards = p.codes./~(Cards.get)
        val id = cards.%(_.isIdentity).starting
        val rest = cards.%!(_.isIdentity)
        val counts : $[(CardData, Int)] = rest.groupBy(_.code).toList./((c, l) => l.head -> l.num).sortBy(_._1.title)
        val kinds = p.side.isCorp.?(corpKinds).|(runnerKinds)
        val allKinds = kinds ++ counts.map(_._1.kind).distinct.diff(kinds)
        val influence = id./(i => DeckRules.influence(i, rest)).|(0)
        val limit = id./(DeckRules.influenceLimit).%(_ != Int.MaxValue)./(_.toString).|("unlimited")
        val min = id./~(_.minDeckSize).|(40)
        val points = rest./(_.agendaPoints.|(0)).sum
        val check = Decks.validate(p.side, p.codes)

        def cardElem(d : CardData, n : Int) : Elem = {
            val dots = id.%(_.faction != d.faction)./~(_ => d.influence).%(_ > 0)
            Div(
                Parameter(("preview", d.code), OnOverOut(OnClick(cardFace(d.code, styles.cardSmall, $(styles.actionable))))) ~
                Div((n + "x").spn(styles.countBadge) ~ dots./(k => Span(("\u25CF" * k).txt, styles.influenceBadge, styles.get(NRFaction.of(d.faction)))).|(Empty), styles.previewCount),
                styles.slot)
        }

        val idElem = id./(c => Div("identity".spn(styles.sectionTitle) ~ Div(Parameter(("preview", c.code), OnOverOut(OnClick(cardFace(c.code, styles.cardSmall, $(styles.actionable))))) ~ Div(c.faction.txt, styles.previewCount), styles.slot), styles.previewSection)).|(Empty)
        val sections = allKinds./~ { k =>
            val l = counts.filter(_._1.kind == k)
            l.any.$(Div(Div(kindTitle(k, l.map(_._2).sum).spn(styles.sectionTitle) ~ Div(l./((d, n) => cardElem(d, n)).merge, styles.row)), styles.previewSection))
        }

        val summary =
            (rest.num + " cards").hlb ~ (" (minimum " + min + ")").spn(styles.dim) ~ " | " ~
            p.side.isCorp.?((points + " agenda point".s(points)).hlb ~ " | ").|(Empty) ~
            (influence + "/" + limit + " influence").hlb ~ " | " ~
            check.ok.?("legal".spn(styles.previewOk)).|(check.message.spn(styles.previewBad))

        val head = Div(
            Span(p.name.txt, styles.previewTitle) ~ id./(c => Span(c.title.txt, styles.get(NRFaction.of(c.faction)))).|(Empty) ~ Break ~
            Span(summary, styles.previewSummary) ~ Span(" | ".txt ~ "confirm in the action pane or ".txt ~ Parameter("preview-back", OnClick("pick another deck".spn(styles.previewLink))), styles.previewHint),
            styles.previewHead)

        Div(head ~ Div((idElem +: sections).merge, styles.row), styles.boardArea)
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Board actions: clicking a card performs the matching action or opens a menu
    // ---------------------------------------------------------------------------------------------------------------

    def leafActions : $[UserAction] = {
        if (lastThen == null || lastActions.none)
            return $
        val l = try { game.explode(lastActions, true, None) } catch { case e : Throwable => lastActions }
        l.%(a => !a.is[Info] || a.is[Unavailable]).%!(_.is[Hidden]).%!(_.is[Cancel]).%!(_.is[Back]).%!(_.is[NotForBots]).%(a => !a.isSoft || a.is[NoExplode])
    }

    def refersTo(a : UserAction, o : Any) : Boolean = {
        val inner = a @@ {
            case u : Unavailable => u.action
            case a => a
        }
        inner.productIterator.exists(x => x == o || x == Some(o))
    }

    def actionsFor(o : Any) : $[UserAction] = leafActions.%(a => refersTo(a, o))

    def hasActions(c : Card) : Boolean = lastThen != null && actionsFor(c).exists(a => !a.is[Unavailable])

    def hasServerActions(s : Server) : Boolean = lastThen != null && actionsFor(s).exists(a => !a.is[Unavailable])

    def boardClick(o : Any, c : |[Card]) {
        val acts = actionsFor(o)
        val avail = acts.%!(_.is[Unavailable])
        if (acts.num == 1 && avail.num == 1) {
            val a = avail.only
            hideOverlay()
            lastThen(a)
        }
        else if (acts.any)
            showMenu(o, c, acts)
        else
            c.foreach(c => showOverlay(overlayScrollX(cardOverlay(c)), onClick))
    }

    def showMenu(o : Any, c : |[Card], acts : $[UserAction]) {
        val g = game
        val title = c./(c => canSee(c).?(g.cardElem(c)).|("a card".hl)).|(o match { case s : Server => s.elem ; case _ => Empty })
        val items = acts./ { a =>
            val q = a.question(g)
            val reason = a @@ { case UnavailableReasonAction(_, r) => r ; case _ => "" }
            val o = a.option(g) ~ reason.some./(r => (" (" + r + ")").spn(xstyles.smaller85)).|(Empty)
            if (a.is[Unavailable])
                Div((q != Empty).?(Div(q, styles.menuQ)).|(Empty) ~ o, styles.menuItemOff)
            else
                Parameter(("menu", a), OnClick(Div((q != Empty).?(Div(q, styles.menuQ)).|(Empty) ~ o, styles.menuItem)))
        }
        val view = c./(c => Parameter(("zoom", c), OnClick(Div("View card", styles.menuItem)))).|(Empty)
        val close = Parameter(Nil, OnClick(Div("Cancel", styles.menuItem)))
        val img = c.%(canSee)./(c => Div(cardFace(c.code, styles.card), styles.slot)).|(Empty)
        showOverlay(overlayScrollX(Div(img ~ Div(title, styles.menuTitle) ~ items.merge ~ view ~ close, xlo.column)), onClick)
    }

    def updateStatus() {
        // the hovered card may be re-rendered (or gone) without a mouseout event: drop the zoom
        onOut(Nil)
        corpPane.replace(corpBoard, resources, onClick, onOver, onOut)
        runnerPane.replace(runnerBoard, resources, onClick, onOver, onOut)
        fitPane(corpPane)
        fitPane(runnerPane)
    }

    // Scale the board area of a pane (all card sizes are in ex) to the largest size that fits the pane: the board uses
    // the pane's width and shrinks only when it would overflow (many remotes, long ice stacks, a big rig). The header
    // keeps the pane's font size. A cap keeps a nearly empty board from drawing cards larger than a readable size.
    def fitPane(pane : Container) {
        try {
            val node = pane.attach.parent.asInstanceOf[dom.html.Element]
            node.style.fontSize = ""
            val area = node.querySelector("." + styles.boardArea.prefix.p + styles.boardArea.name).asInstanceOf[dom.html.Element]
            if (area == null || node.clientHeight <= 0)
                return
            def overflows = node.scrollHeight > node.clientHeight + 1 || node.scrollWidth > node.clientWidth + 1
            area.style.fontSize = "100%"
            val probe = dom.document.createElement("div").asInstanceOf[dom.html.Div]
            probe.style.height = "10ex"
            probe.style.position = "absolute"
            probe.style.visibility = "hidden"
            area.appendChild(probe)
            val ex10 = probe.offsetHeight.toDouble
            area.removeChild(probe)
            // at the cap a board card (cardSmall in a slot: 10.48ex * 80%) is 40% of the pane height
            val cap = (ex10 > 0).?(node.clientHeight * 0.40 / (ex10 * 0.8384) * 100).|(200.0).max(60.0).min(500.0)
            var lo = (game.phase == "setup").?(75.0).|(25.0).min(cap)
            var hi = cap
            area.style.fontSize = hi.round + "%"
            if (overflows) {
                1.to(9).foreach { _ =>
                    val mid = (lo + hi) / 2
                    area.style.fontSize = mid.round + "%"
                    if (overflows) hi = mid else lo = mid
                }
                area.style.fontSize = lo.round + "%"
            }
        }
        catch {
            case e : Throwable => warn("fitPane " + e)
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Hover zoom
    // ---------------------------------------------------------------------------------------------------------------

    val zoomNode = dom.document.createElement("div").asInstanceOf[dom.html.Div]
    zoomNode.style.position = "fixed"
    zoomNode.style.zIndex = "2000"
    zoomNode.style.display = "none"
    zoomNode.style.pointerEvents = "none"
    zoomNode.style.top = "4vh"
    zoomNode.style.maxHeight = "92vh"
    zoomNode.style.backgroundColor = "#111111f0"
    zoomNode.style.border = "1px solid #555555"
    zoomNode.style.borderRadius = "1.2ex"
    zoomNode.style.padding = "0.6ex"
    zoomNode.style.overflow = "hidden"
    dom.document.body.appendChild(zoomNode)

    var mouseX = 0.0
    var mouseY = 0.0

    dom.document.addEventListener("mousemove", (e : dom.MouseEvent) => {
        mouseX = e.clientX
        mouseY = e.clientY
        if (zoomNode.style.display != "none")
            placeZoom()
    })

    def placeZoom() {
        val w = dom.window.innerWidth
        if (mouseX < w / 2) {
            zoomNode.style.left = (mouseX + 28) + "px"
            zoomNode.style.right = ""
        }
        else {
            zoomNode.style.right = (w - mouseX + 28) + "px"
            zoomNode.style.left = ""
        }
    }

    // the zoom shows the card image only; the printed text is shown only when the image is missing
    def showZoom(code : String, c : |[Card]) {
        val missing = imageMissing(code)
        val e =
            if (missing)
                Div(Div(cardFace(code, styles.zoomImg), styles.slot) ~ Div(Cards.get(code)./(d => d.title.hlb ~ Break ~ c./(c => cardStats(c).join(" | ") ~ Break ~ Break ~ cardText(c) ~ stateText(c)).|(Empty)).|(Empty), styles.zoomText), xlo.flexhcenter)(xlo.flexnowrap)
            else
                cardFace(code, styles.zoomImg)
        zoomNode.style.backgroundColor = missing.?("#111111f0").|("transparent")
        zoomNode.style.border = missing.?("1px solid #555555").|("none")
        zoomNode.style.padding = missing.?("0.6ex").|("0")
        new ElementAttachmentPoint(zoomNode).clear()
        new ElementAttachmentPoint(zoomNode).append(e, resources)
        placeZoom()
        zoomNode.style.display = "block"
    }

    def onOver(a : Any) : Unit = a @@ {
        case c : Card if canSee(c) => showZoom(c.code, |(c))
        case ("deck", a : ChoosePresetDeckAction) => showZoom(Decks.presets(a.index).identity, None)
        case ("preview", code : String) => showZoom(code, None)
        case _ =>
    }

    def onOut(a : Any) : Unit = {
        zoomNode.style.display = "none"
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Overlay
    // ---------------------------------------------------------------------------------------------------------------

    def cardText(c : Card) : String = {
        c.data.text
            .replace("<ul>", "").replace("</ul>", "")
            .replace("<li>", "\n* ").replace("</li>", "")
            .replace("<strong>", "").replace("</strong>", "")
            .replace("<em>", "").replace("</em>", "")
            .replace("<i>", "").replace("</i>", "")
            .replace("[credit]", "[c]").replace("[mu]", "MU").replace("[subroutine]", "[sub]")
            .replace("[recurring-credit]", "[recurring c]")
            .replace("&amp;", "&")
    }

    def cardStats(c : Card) : $[String] = {
        val d = c.data
        $(d.kind) ++ d.subtypes.some./(_.join(" - ")).$ ++ d.cost./("Cost " + _).$ ++ d.strength./("Strength " + _).$ ++ d.advancementCost./("Advancement " + _).$ ++ d.agendaPoints./("Points " + _).$ ++ d.memory./("MU " + _).$ ++ d.trashCost./("Trash " + _).$ ++ d.influence./("Influence " + _).$
    }

    def stateText(c : Card) : String = {
        val g = game
        val s = g.state(c)
        val inPlay = g.inPlayZone(g.zone(c))
        val l =
            (inPlay && g.owner(c).isCorp).$(g.isRezzed(c).?("rezzed").|("unrezzed")) ++
            (s.advancement > 0).$(s.advancement + " advancement") ++
            (s.virus > 0).$(s.virus + " virus") ++
            (s.credits > 0).$(s.credits + " credits") ++
            (s.power > 0).$(s.power + " power") ++
            (inPlay && c.data.isIce && g.isRezzed(c)).$("strength " + g.iceStrength(c)) ++
            (inPlay && c.data.isIcebreaker).$("strength " + g.breakerStrength(c)) ++
            s.host./(h => "hosted on " + h.title).$ ++
            (g.zone(c) match { case StashZone(h) => $("hosted on " + h.title + " (not installed)") ; case _ => $[String]() }) ++
            s.uninstalled.$("not installed") ++
            (inPlay && c.data.isIce).$(g.serverOf(c)./(s => "protecting " + s.name).|("")) ++
            s.extraSubtypes.some./(l => "gained subtypes: " + l.join(", ")).$ ++
            s.target.%(t => canSee(t))./(t => "targeting " + t.title).$ ++
            (s.choice != "").$("choice: " + s.choice) ++
            flagLabels(c) ++
            g.hostedBadPub.get(c).%(_ > 0)./(n => n + " hosted bad publicity").$ ++
            (g.zone(c).is[RemovedZone]).$("removed from the game") ++
            (CardImpls.has(c.code)).?($()).|($("no automation: use the Manual menu"))
        l.any.?("\n\n" + l.join(", ")).|("")
    }

    def cardOverlay(c : Card) : Elem = {
        val g = game
        if (canSee(c) && imageMissing(c.code))
            Div(cardFace(c.code, styles.artwork), styles.slot) ~ Div(c.title.hlb ~ Break ~ cardStats(c).join(" | ") ~ Break ~ Break ~ cardText(c) ~ stateText(c), styles.cardText)
        else if (canSee(c))
            Div(cardFace(c.code, styles.artwork), styles.slot)
        else
            Div(Image(back(c), styles.artwork), styles.slot)
    }

    def overlayScrollX(e : Elem) = overlayScroll(e)(xstyles.seeThrough).onClick
    def overlayFitX(e : Elem) = overlayFit(e)(xstyles.seeThrough).onClick

    def showOverlay(e : Elem, onClick : Any => Unit) {
        onOut(Nil)
        overlayPane.vis()
        overlayPane.replace(e, resources, onClick, onOver, onOut)
    }

    def hideOverlay() {
        onOut(Nil)
        overlayPane.clear()
        overlayPane.invis()
    }

    def pileOverlay(title : String, l : $[Card], sorted : Boolean) : Elem = {
        val ll = sorted.?(l.sortBy(_.title)).|(l.reverse)
        Div(title.spn(styles.pileTitle) ~ (ll.none.?("empty".spn(styles.dim)).|(Empty)) ~ Div(ll./(c => Div(cardImage(c, styles.card), styles.slot)).merge, styles.row), xlo.column)
    }

    override def onClick(a : Any) : Unit = a @@ {
        case c : Card =>
            boardClick(c, |(c))

        case s : Server =>
            boardClick(s, None)

        case ("menu", a : UserAction) =>
            hideOverlay()
            if (lastThen != null)
                lastThen(a)

        // a deck of the gallery: preview it (Confirm in the action pane chooses it)
        case ("deck", a : ChoosePresetDeckAction) =>
            setPreview(|(presetPreview(a)))

        case ("preview", code : String) =>
            showOverlay(overlayScrollX(Div(cardFace(code, styles.artwork), styles.slot)), onClick)

        case "preview-back" =>
            setPreview(None)

        case ("gallery-faction", f : Faction, x : String) =>
            galleryFaction += f -> x
            redrawAsk()

        case ("gallery-era", f : Faction, x : String) =>
            galleryEra += f -> x
            redrawAsk()

        case ("zoom", c : Card) =>
            showOverlay(overlayScrollX(cardOverlay(c)), onClick)

        case "archives" =>
            val g = game
            val l = g.discard(Corp).%(canSee)
            val hidden = g.discard(Corp).num - l.num
            showOverlay(overlayScrollX(pileOverlay("Archives" + (hidden > 0).??(" (" + hidden + " facedown)"), l, true)), onClick)

        case "heap" =>
            showOverlay(overlayScrollX(pileOverlay("Heap", game.discard(Runner), false)), onClick)

        case ("removed", f : Faction) =>
            val l = game.removed(f).%(canSee)
            val hidden = game.removed(f).num - l.num
            showOverlay(overlayScrollX(pileOverlay("Removed from the game (" + f.name + ")" + (hidden > 0).??(" (" + hidden + " facedown)"), l, true)), onClick)

        case "hq" | "rnd" =>
            hideOverlay()

        case "import" =>
            showImport()

        case Nil =>
            hideOverlay()

        case _ =>
            hideOverlay()
    }

    def showImport() {
        val self = lastSelf.|(Corp)
        overlayPane.vis()
        overlayPane.clear()

        val root = overlayPane.attach.appendContainer(Div(Div(Div("Paste a NetrunnerDB decklist".hlb ~ Break ~ ("for the " ~ self.name.styled(self) ~ " side: lines like 3x Card Name, 2 Card Name or Card Name x3; the identity on its own line").spn(xstyles.smaller85) ~ Break ~ Content, xstyles.middleScrollIn), xstyles.middleScrollOut), xstyles.unselectable, xstyles.inner, xstyles.pane.action), resources)

        val parent = root.attach.parent
        val ta = dom.document.createElement("textarea").asInstanceOf[dom.html.TextArea]
        ta.id = "nr-import"
        ta.rows = 22
        ta.cols = 60
        ta.spellcheck = false
        ta.style.display = "block"
        ta.style.margin = "1ex auto"
        ta.style.fontSize = "90%"
        ta.style.color = "#d3d3d3"
        ta.style.backgroundColor = "#111111"
        ta.value = importText
        ta.oninput = e => { importText = ta.value }
        parent.appendChild(ta)

        val msg = dom.document.createElement("div").asInstanceOf[dom.html.Div]
        msg.id = "nr-import-message"
        msg.style.color = "#ffa500"
        msg.style.minHeight = "3ex"
        msg.style.maxWidth = "70ex"
        msg.style.margin = "0 auto"
        msg.style.whiteSpace = "pre-wrap"
        parent.appendChild(msg)

        def button(label : String, id : String, f : () => Unit) = {
            val b = dom.document.createElement("div").asInstanceOf[dom.html.Div]
            b.id = id
            b.textContent = label
            b.style.cursor = "pointer"
            b.style.display = "inline-block"
            b.style.margin = "1ex"
            b.style.padding = "0.5ex 2ex"
            b.style.outline = "0.3vmin solid #999999"
            b.style.backgroundColor = "#111111"
            b.onclick = e => { e.stopPropagation() ; f() }
            parent.appendChild(b)
        }

        def check() : |[$[String]] = {
            val text = ta.value
            importText = text
            if (text.trim.isEmpty) {
                msg.textContent = "The decklist is empty"
                return None
            }
            val (codes, errors) = Decks.parse(text)
            if (errors.any) {
                msg.textContent = "Could not read " + errors.num + " line" + (errors.num != 1).??("s") + ":\n" + errors.take(12).join("\n") + (errors.num > 12).??("\n...")
                return None
            }
            val v = Decks.validate(self, codes)
            if (!v.ok) {
                msg.textContent = "Invalid deck: " + v.message
                return None
            }
            val id = codes./(Cards.get)./~(x => x).%(_.isIdentity).starting./(_.title).|("?")
            msg.textContent = "Valid: " + id + ", " + v.message
            Some(codes)
        }

        // a valid decklist is previewed on the board like a preset; "Import" in the action pane confirms it
        button("Check and preview", "nr-import-check", () => {
            check().foreach { codes =>
                setPreview(|(DeckPreview(self, "Imported deck", codes, None)))
            }
        })

        button("Cancel", "nr-import-cancel", () => hideOverlay())
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Layout
    // ---------------------------------------------------------------------------------------------------------------

    val zoom = 0.72

    val layouts = $(
        // boards stacked on the left, log and actions on the right
        Layout("base",
            $(
                BasicPane("corp", 56, 25, Priorities(top = 1, left = 1, grow = 3, maxYscale = 1.5)),
                BasicPane("runner", 56, 25, Priorities(bottom = 1, left = 1, grow = 3, maxYscale = 1.5)),
                BasicPane("log", 24, 16, Priorities(top = 1, right = 1, grow = 1, maxYscale = 2.5)),
                BasicPane("action", 24, 34, Priorities(bottom = 1, right = 1, grow = 2, maxXscale = 1.6, maxYscale = 2.0))
            )./(p => p.copy(kX = p.kX * zoom, kY = p.kY * zoom))
        ),
        // wider boards with a narrower side column
        Layout("wide",
            $(
                BasicPane("corp", 68, 24, Priorities(top = 1, left = 1, grow = 3, maxYscale = 1.5)),
                BasicPane("runner", 68, 24, Priorities(bottom = 1, left = 1, grow = 3, maxYscale = 1.5)),
                BasicPane("log", 20, 16, Priorities(top = 1, right = 1, grow = 1, maxYscale = 2.5)),
                BasicPane("action", 20, 32, Priorities(bottom = 1, right = 1, grow = 2, maxXscale = 1.6, maxYscale = 2.0))
            )./(p => p.copy(kX = p.kX * zoom, kY = p.kY * zoom)),
            0.97
        ),
        // everything in one column (portrait / narrow windows)
        Layout("narrow",
            $(
                BasicPane("corp", 42, 17, Priorities(top = 1, grow = 3)),
                BasicPane("runner", 42, 17, Priorities(grow = 3)),
                BasicPane("action", 42, 18, Priorities(grow = 2, maxYscale = 2)),
                BasicPane("log", 42, 7, Priorities(bottom = 1, grow = 1, maxYscale = 3))
            )./(p => p.copy(kX = p.kX * zoom, kY = p.kY * zoom)),
            0.85
        )
    )

    val layouter = Layouter(layouts,
        _./~{
            case f if f.name == "action" => $(f, f.copy(name = "undo"), f.copy(name = "settings"))
            case f => $(f)
        },
        ff => ff :+ Fit("overlay", ff./(_.x).min, ff./(_.y).min, ff./(_.right).max - ff./(_.x).min, ff./(_.bottom).max - ff./(_.y).min)
    )

    val settingsKey = Meta.settingsKey

    val layoutKey = "v" + 3

    override def ask(faction : |[Faction], actions : $[UserAction], then : UserAction => Unit) {
        seats = (seats ++ faction.$).distinct
        lastThen = then
        lastActions = actions
        super.ask(faction, actions, then)
        updateStatus()
    }

    override def wait(self : $[Faction], factions : $[Faction], message : Elem) {
        seats = (seats ++ self).distinct
        lastThen = null
        lastActions = $
        super.wait(self, factions, message)
        updateStatus()
    }

    // the viewer's hand (card images, click = the matching action / menu / zoom, hover = zoom) above the prompt
    override def preinfo(self : |[Faction], aa : $[UserAction]) = {
        val ii = currentGame.info(Nil, self, aa)
        ii.any.??(convertActions(self, ii)) ++ handOptions(self, aa)
    }

    override def info(self : |[Faction], aa : $[UserAction]) = {
        aa.exists(_.is[ImportDeckMarker]).$(
            ZBasic(Break, "Import NetrunnerDB decklist".hh, () => { onClick("import") }, ZBasic.infoch).copy(clear = false)
        ) ++
        // "Save Replay": a self-contained HTML file that replays the game offline (see saveReplay)
        (currentGame.isOver && hrf.HRF.flag("replay").not).$(
            ZBasic(Break ~ Break ~ Break, "Save Replay".hh, () => { saveReplay() }).copy(clear = false)
        ) ++
        (currentGame.isOver && callbacks.canPlayAgain.not && hrf.HRF.flag("replay").not).$(
            ZBasic(Break, "This game is also saved on the server: open any of its links to replay it".spn(xstyles.smaller85), null).copy(clear = false)
        ) ++
        (callbacks.canPlayAgain && hrf.HRF.offline.not && hrf.HRF.flag("replay").not).$(
            ZBasic(Break ~ Break ~ Break, "Save Game Online".hh, () => {
                showOverlay(overlayScrollX("Save Game Online".hlb(xstyles.larger125) ~
                    ("Save".hlb).div.div(xstyles.choice)(xstyles.xx)(xstyles.chm)(xstyles.chp)(xstyles.thu)(xlo.fullwidth)(xstyles.width60ex).pointer.onClick.param("***") ~
                    ("Save and replace bots with humans".hh).div.div(xstyles.choice)(xstyles.xx)(xstyles.chm)(xstyles.chp)(xstyles.thu)(xlo.fullwidth)(xstyles.width60ex).pointer.onClick.param("///") ~
                    ("Cancel".txt).div.div(xstyles.choice)(xstyles.xx)(xstyles.chm)(xstyles.chp)(xstyles.thu)(xlo.fullwidth)(xstyles.width60ex).pointer.onClick.param("???")
                ).onClick, {
                    case "***" => callbacks.saveReplayOnline(false, false) { url => onClick(Nil) }
                    case "///" => callbacks.saveReplayOnline(true , false) { url => onClick(Nil) }
                    case _ => onClick(Nil)
                })
            }, ZBasic.infoch).copy(clear = false)
        ) ++
        (currentGame.isOver && callbacks.canPlayAgain).$(
            ZBasic(Break ~ Break ~ Break, "Play Again".hh, () => {
                callbacks.playAgain()
            }).copy(clear = false)
        ) ++
        $(ZBasic(Break ~ Break, "Interface".spn, () => { callbacks.editSettings { updateStatus() } }, ZBasic.infoch).copy(clear = false))
    }

    // Save Replay. The framework (hrf.quine.Quine) embeds the image assets of a game in the HTML file; Netrunner
    // declares ~2,000 lazily loaded card images, so Meta opts in to EmbedLoadedOnDemandAssetsOnly: only the images
    // loaded in this browser are embedded. Before saving, the images of every card of the game (both decks, the
    // identities, cards created during the game), the card backs and the faction logos of the two identities are
    // loaded; images that fail or take longer than 30 seconds are left out (the replay then draws the card back with
    // the title, as for any missing image).
    var savingReplay = false

    def saveReplay() {
        if (savingReplay)
            return

        val g = game
        val images = resources.images
        val codes = (g.decks.values.$.flatten ++ g.location.keys.$./(_.code) ++ g.identity.values.$./(_.code)).distinct
        val logos = g.identity.values.$./~(c => NRFaction.logo(c.data.faction))
        val names = (codes ++ $("corp-back", "runner-back") ++ logos).distinct.%(images.hasSource)
        val urls = names.%!(images.has)./(n => images.sources(n.toLowerCase)).distinct
        val loader = images.loader

        savingReplay = true

        var left = urls.%(u => loader.has(u).not && loader.error(u).not)

        showOverlay(overlayScrollX(Div("Save Replay".hlb ~ Break ~ ("Preparing the replay file: loading the images of " + names.num + " cards...").spn(xstyles.smaller85), styles.menuTitle)), onClick)

        var done = false

        def go() {
            if (done.not) {
                done = true
                try {
                    callbacks.saveReplay {
                        savingReplay = false
                        hideOverlay()
                    }
                }
                catch {
                    case e : Throwable =>
                        savingReplay = false
                        warn("save replay " + e)
                        showOverlay(overlayScrollX(Div("Save Replay failed: ".hlb ~ e.toString, styles.menuTitle)), onClick)
                }
            }
        }

        if (hrf.HRF.embedded || left.none)
            go()
        else {
            left.foreach { u =>
                loader.wait($(u)) {
                    left = left.%(_ != u)
                    if (left.none)
                        go()
                }
            }
            scalajs.js.timers.setTimeout(30000) { go() }
        }
    }

    // Deck choice in the action pane. Without a preview: the presets (identity thumbnail, clean deck name, identity);
    // a click previews the deck (UI only, nothing is submitted). With a preview: "Confirm <deck>" (submits the matching
    // action of the current prompt), "Back to the deck list", then the presets again to preview another one.
    override def convertActions(faction : Option[Faction], actions : $[UserAction], then : UserAction => Unit) : $[ZOption] =
        if (then == null || actions.exists(_.is[ChoosePresetDeckAction]).not)
            super.convertActions(faction, actions, then)
        else {
            val f = actions.of[ChoosePresetDeckAction].head.self
            val p = previewFor(f)
            val confirm : $[ZOption] = p./~ { p =>
                val id = p.codes./~(Cards.get).%(_.isIdentity).starting
                val label = p.preset.any.?("Confirm ".txt ~ p.name.hlb).|("Import ".txt ~ id./(_.title).|("?").hlb)
                val thumb = id./(c => cardFace(c.code, styles.cardTiny, $(styles.presetThumb))).|(Empty)
                val group = Div(Break ~ "Previewing ".txt ~ p.name.hl ~ " (" ~ (p.codes.num - 1).toString ~ " cards)")(xlo.fullwidth)
                confirmAction(p)./(a => ZOption(group, OnClick(Div(thumb ~ Div(label), styles.confirmButton)(elem.borders.get(f))), _ => { preview = None ; then(a) })).$ ++
                p.preset.none.$(ZOption(group, OnClick(Div("Edit the decklist", styles.confirmBack)), _ => showImport(), false)) ++
                $(ZOption(group, OnClick(Div("Back to the deck list", styles.confirmBack)), _ => setPreview(None), false))
            }
            val presets = actions.of[ChoosePresetDeckAction]
            val hidden = presets.%(a => presetShown(a).not && p.exists(_.preset.contains(a.index)).not)
            val filtered : $[ZOption] = hidden.any.$(ZOption(Div(Break ~ "Decks")(xlo.fullwidth), Div((hidden.num + " more decks hidden by the filters above the board gallery").spn(xstyles.smaller85), xstyles.info, xstyles.xx, xlo.fullwidth), _ => {}, false))
            confirm ++ actions./~ {
                // the "Choose a deck" group header of the prompt: the presets carry their own header here
                case _ : InfoGroupAction => $
                case a : ChoosePresetDeckAction if hidden.has(a) => $
                case a : ChoosePresetDeckAction =>
                    val d = Decks.presets(a.index)
                    val o = Div(cardFace(d.identity, styles.cardTiny, $(styles.presetThumb)) ~ Div(cleanDeckName(d.name).hl ~ Break ~ d.identityTitle.spn(styles.presetIdentity)), styles.presetOption)
                    val ss = styleAction(faction, actions, a, false, None) ++ p.exists(_.preset.contains(a.index)).$(styles.selected)
                    val q = p.any.?(Div(Break ~ "Preview another deck")(xlo.fullwidth)).|(Div("Choose a deck (click to see its cards)")(xlo.fullwidth))
                    $(ZOption(q, OnOverOut(OnClick(Div(o, ss))), _ => setPreview(|(presetPreview(a))), false, _ => onOver(("deck", a)), _ => onOut(Nil)))
                case a => super.convertActions(faction, $(a), then)
            } ++ filtered
        }

    override def styleAction(faction : Option[Faction], actions : List[UserAction], a : UserAction, unavailable : Boolean, view : Option[Any]) = {
        val img = view @@ {
            case Some(_ : Card) => true
            case _ => false
        }

        a @@ {
            case _ if unavailable && img => $(xstyles.unavailableCard)
            case _ if unavailable => $(xstyles.unavailableText)
            case _ => Nil
        } ++
        a @@ {
            case _ if img => Nil
            case _ : Info => $(xstyles.info, xstyles.xx, xstyles.thu)
            case _ => $(xstyles.choice, xstyles.xx, xstyles.thu, xstyles.thumargin)
        } ++
        a @@ {
            case _ : Extra[_] => Nil
            case _ : Choice => $(xlo.pointer)
            case _ : Cancel => $(xlo.pointer)
            case _ : OnClickInfo => $(xlo.pointer)
            case _ => Nil
        } ++
        a @@ {
            case _ if img => $(styles.viewcard)
            case _ => $(xlo.fullwidth)
        } ++
        faction @@ {
            case Some(f) => $(elem.borders.get(f))
            case _ => Nil
        } ++
        a @@ {
            case a : Selectable if a.selected => $(styles.selected)
            case _ => Nil
        }
    }
}
