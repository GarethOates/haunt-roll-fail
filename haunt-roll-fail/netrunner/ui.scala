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

    def viewer : |[Faction] = lastSelf

    def canSee(c : Card) : Boolean = {
        val g = game
        // cards accessed during the current run are revealed
        if (g.run.exists(r => !r.ended && r.accessed.has(c)))
            return true
        viewer match {
            case None => true
            case Some(v) =>
                g.zone(c) match {
                    case HandZone(f) => f == v
                    case DeckZone(f) => false
                    case DiscardZone(Corp) => v.isCorp || g.state(c).seen
                    case DiscardZone(Runner) => true
                    case RootZone(_) | IceZone(_) => v.isCorp || g.isRezzed(c) || g.state(c).seen
                    case HostedZone(h) => g.owner(c) == v || (g.owner(c).isCorp && (g.isRezzed(c) || g.state(c).seen)) || (g.owner(c).isRunner && !g.state(c).facedown)
                    case RigZone => v.isRunner || !g.state(c).facedown
                    case _ => true
                }
        }
    }

    def back(c : Card) = game.owner(c).isCorp.?("corp-back").|("runner-back")

    // ---------------------------------------------------------------------------------------------------------------
    // Cards on the board
    // ---------------------------------------------------------------------------------------------------------------

    def clickable(e : Elem, p : Any) : Elem = Parameter(p, OnOverOut(OnClick(e)))

    def cardImage(c : Card, style : Style, extra : $[Style] = $) : Elem = {
        val g = game
        val visible = canSee(c)
        val inPlay = g.inPlayZone(g.zone(c))
        val unrezzed = inPlay && g.owner(c).isCorp && !g.isRezzed(c)
        val facedown = inPlay && g.owner(c).isRunner && g.state(c).facedown
        val img = visible.?(Image(c.code, style)).|(Image(back(c), style))
        val styled = img.copy(styles = img.styles ++ extra ++ (visible && unrezzed).$(styles.unrezzed) ++ (visible && facedown).$(styles.facedown) :+ styles.actionable)
        clickable(styled, c)
    }

    def badges(c : Card) : Elem = {
        val g = game
        val s = g.state(c)
        val visible = canSee(c)
        val l =
            (s.advancement > 0).$((s.advancement + " adv").spn(styles.badge)(styles.badgeAdv)) ++
            (s.virus > 0).$((s.virus + " virus").spn(styles.badge)(styles.badgeVirus)) ++
            (s.credits > 0).$((s.credits + "c").spn(styles.badge)(styles.badgeCredit)) ++
            (s.power > 0).$((s.power + " power").spn(styles.badge)(styles.badgePower)) ++
            (visible && c.data.isIce && g.isRezzed(c) && g.iceStrength(c) != c.data.strength.|(0)).$(("str " + g.iceStrength(c)).spn(styles.badge)(styles.badgeStr)) ++
            (visible && c.data.isIcebreaker && g.breakerStrength(c) != c.data.strength.|(0)).$(("str " + g.breakerStrength(c)).spn(styles.badge)(styles.badgeStr))
        l.any.?(Div(l.merge, styles.badges)).|(Empty)
    }

    def tagLabel(c : Card) : Elem = {
        val g = game
        val inPlay = g.inPlayZone(g.zone(c))
        if (inPlay && g.owner(c).isCorp && !g.isRezzed(c) && canSee(c))
            "unrezzed".spn(styles.tag)
        else if (inPlay && g.owner(c).isRunner && g.state(c).facedown && canSee(c))
            "facedown".spn(styles.tag)
        else
            Empty
    }

    def highlightOf(c : Card) : $[Style] = {
        val g = game
        val encountered = g.run.exists(r => !r.ended && r.ice.has(c) && (r.phase == EncounterPhase || r.phase == ApproachIcePhase))
        encountered.$(styles.encountered) ++ (hasActions(c) && !encountered).$(styles.highlighted)
    }

    def slot(c : Card, style : Style = styles.card) : Elem = {
        val g = game
        val hostedElems = g.hosted(c)./(h => slot(h, styles.cardTiny))
        Div(cardImage(c, style, highlightOf(c)) ~ tagLabel(c) ~ badges(c) ~ hostedElems.some./(l => Div(l.merge, styles.hostedRow)).|(Empty), styles.slot)
    }

    def iceSlot(c : Card) : Elem = {
        val g = game
        val hostedElems = g.hosted(c)./(h => slot(h, styles.cardTiny))
        Div(Div(cardImage(c, styles.cardIceImg, highlightOf(c)) ~ tagLabel(c), styles.iceBox) ~ badges(c) ~ hostedElems.some./(l => Div(l.merge, styles.hostedRow)).|(Empty), styles.slot)
    }

    def pileImage(id : String, count : Int, label : String, p : Any) : Elem =
        Div(clickable(Image(id, styles.cardSmall)(styles.actionable), p) ~ (count + " " + label).spn(styles.serverCount), styles.slot)

    // ---------------------------------------------------------------------------------------------------------------
    // Servers
    // ---------------------------------------------------------------------------------------------------------------

    def serverElem(s : Server) : Elem = {
        val g = game
        val r = g.run.%(r => r.server == s && !r.ended)
        val iceList = g.ice(s)
        val position = r./(_.position).|(-1)
        val approachingServer = r.exists(x => x.position == 0 || x.phase == ApproachServerPhase || x.phase == AccessPhase || x.phase == SuccessPhase)
        val iceElems = iceList.indexed.reverse./ { (c, i) =>
            val here = r.any && position == i + 1 && !approachingServer
            here.?(Div("Runner".spn(styles.marker)) ~ iceSlot(c)).|(iceSlot(c))
        }
        val rootElems = g.root(s)./(c => slot(c, styles.cardSmall))
        val central = s match {
            case HQ => pileImage("corp-back", g.hand(Corp).num, "cards", "hq")
            case RnD => pileImage("corp-back", g.deck(Corp).num, "cards", "rnd")
            case Archives =>
                val l = g.discard(Corp)
                val faceup = l.%(c => g.state(c).seen)
                val top = l.lastOption./(c => (g.state(c).seen || viewer.forall(_.isCorp)).?(c.code).|("corp-back")).|("corp-back")
                Div(clickable(Image(top, styles.cardSmall)(styles.actionable)(l.none.?(styles.unrezzed)), "archives") ~ (l.num + " cards" + (l.num > faceup.num).??(" (" + (l.num - faceup.num) + " facedown)")).spn(styles.serverCount), styles.slot)
            case _ => Empty
        }
        val marker = approachingServer.?(Div("Runner".spn(styles.marker))).|(Empty)
        val name = clickable(s.name.spn(styles.serverName)(hasServerActions(s).$(styles.highlighted))(styles.actionable), s)
        Div(
            name ~
            Div(iceElems.merge, styles.iceColumn) ~
            marker ~
            Div(central ~ rootElems.merge, styles.rootRow),
            styles.server
        )(r.any.$(styles.serverRun))
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Boards
    // ---------------------------------------------------------------------------------------------------------------

    def stat(value : Any, label : String, style : Style) : Elem = Span(value.toString.spn(styles.statValue) ~ " " ~ label, styles.stat, style)

    def playerName(f : Faction) : Elem = f.name.styled(f)(styles.name) ~ resources.getName(f)./(n => n.spn(styles.dim)(styles.headerItem)).|(Empty)

    def activeMark(f : Faction) : Elem = {
        val g = game
        (g.active == f && g.turnStarted).?(("turn " + g.turn + (g.run.exists(!_.ended) && f.isRunner).??(" - running")).spn(styles.active)).|(Empty)
    }

    def corpBoard : Elem = {
        val g = game
        val id = g.identity.get(Corp)
        val header = Div(
            playerName(Corp) ~
            id./(c => c.title.styled(NRFaction.of(c.data.faction))(styles.headerItem)).|(Empty) ~
            activeMark(Corp) ~
            stat(g.credits(Corp), "credits", styles.statCredit) ~
            stat(g.clicks(Corp), "clicks", styles.statClick) ~
            stat(g.agendaPoints(Corp), "agenda points", styles.statPoints) ~
            (g.badPublicity > 0).?(stat(g.badPublicity, "bad publicity", styles.statBadPub)).|(Empty) ~
            stat(g.hand(Corp).num + "/" + g.handSize(Corp), "hand", styles.statHand),
            styles.header)
        if (g.phase == "setup")
            return header ~ deckSelection(Corp)
        val idImg = id./(c => Div(clickable(Image(c.code, styles.cardSmall)(styles.actionable), c), styles.slot)).|(Empty)
        val scored = g.scored(Corp)./(c => slot(c, styles.cardTiny))
        val scoredSection = scored.any.?(Div("scored".spn(styles.sectionTitle) ~ Div(scored.merge, styles.row), styles.section)).|(Empty)
        val servers = Div(g.servers./(serverElem).merge, styles.serversRow)
        val hand = handElem(Corp)

        header ~ Div(Div(idImg ~ scoredSection, styles.section) ~ servers, styles.row) ~ hand
    }

    def runnerBoard : Elem = {
        val g = game
        val id = g.identity.get(Runner)
        val header = Div(
            playerName(Runner) ~
            id./(c => c.title.styled(NRFaction.of(c.data.faction))(styles.headerItem)).|(Empty) ~
            activeMark(Runner) ~
            stat(g.credits(Runner), "credits", styles.statCredit) ~
            stat(g.clicks(Runner), "clicks", styles.statClick) ~
            stat(g.agendaPoints(Runner), "agenda points", styles.statPoints) ~
            stat(g.tags, (g.tags == 1).?("tag").|("tags"), styles.statTag) ~
            stat(g.link, "link", styles.statLink) ~
            stat(g.memoryUsed + "/" + g.memoryTotal, "MU", styles.statMU) ~
            stat(g.hand(Runner).num + "/" + g.handSize(Runner), "hand", styles.statHand) ~
            (g.coreDamage > 0).?(stat(g.coreDamage, "core damage", styles.statTag)).|(Empty),
            styles.header)
        if (g.phase == "setup")
            return header ~ deckSelection(Runner)
        val idImg = id./(c => Div(clickable(Image(c.code, styles.cardSmall)(styles.actionable), c), styles.slot)).|(Empty)
        val scored = g.scored(Runner)./(c => slot(c, styles.cardTiny))
        val scoredSection = scored.any.?(Div("stolen".spn(styles.sectionTitle) ~ Div(scored.merge, styles.row), styles.section)).|(Empty)
        val programs = g.rig.%(_.data.isProgram)./(slot(_, styles.cardSmall))
        val hardware = g.rig.%(_.data.isHardware)./(slot(_, styles.cardSmall))
        val resourcesL = g.rig.%(_.data.isResource)./(slot(_, styles.cardSmall))
        val heap = g.discard(Runner)
        val piles = Div(
            Div(Image("runner-back", styles.cardSmall)(g.deck(Runner).none.?(styles.unrezzed)) ~ (g.deck(Runner).num + " stack").spn(styles.serverCount), styles.slot) ~
            Div(heap.any.?(clickable(Image(heap.last.code, styles.cardSmall)(styles.actionable), "heap")).|(Image("runner-back", styles.cardSmall)(styles.unrezzed)) ~ (heap.num + " heap").spn(styles.serverCount), styles.slot),
            styles.row)
        def section(title : String, l : $[Elem]) = l.any.?(Div(title.spn(styles.sectionTitle) ~ Div(l.merge, styles.row), styles.section)).|(Empty)
        val runLine = g.run.%(!_.ended)./(r => runStatus(r)).|(Empty)
        val hand = handElem(Runner)

        header ~ runLine ~ Div(Div(idImg ~ scoredSection, styles.section) ~ Div(piles, styles.section) ~ section("programs", programs) ~ section("hardware", hardware) ~ section("resources", resourcesL), styles.row) ~ hand
    }

    def runStatus(r : Run) : Elem = {
        val g = game
        val ph = r.phase match {
            case InitiationPhase => "initiated".txt
            case ApproachIcePhase => "approaching " ~ r.ice./(c => (g.isRezzed(c) || viewer.forall(_.isCorp)).?(g.cardElem(c)).|("unrezzed ice".hl)).|(Empty)
            case EncounterPhase => "encountering " ~ r.ice./(g.cardElem).|(Empty)
            case MovementPhase => "passed ice, moving on".txt
            case ApproachServerPhase => "approaching the server".txt
            case SuccessPhase => "successful".txt
            case AccessPhase => "breaching".txt
            case EndedPhase => "ended".txt
        }
        val n = r.position
        val accessed = (r.phase == AccessPhase).??(r.accessed.%(c => g.location.contains(c)))
        val accessedElems = accessed./(c => Div(cardImage(c, styles.cardSmall), styles.slot))
        Div("Run on " ~ r.server.elem ~ ": " ~ ph ~ ((n > 0 && r.phase != AccessPhase).?(" (" + n + " ice " + ((n == 1).?("piece").|("pieces")) + " to pass)").|("")), styles.runLine) ~
        accessed.any.?(Div("accessed".spn(styles.sectionTitle) ~ Div(accessedElems.merge, styles.row), styles.section)).|(Empty)
    }

    def handElem(f : Faction) : Elem = {
        val g = game
        val l = g.hand(f)
        val visible = viewer.forall(_ == f)
        val title = f.isCorp.?("HQ").|("Grip")
        if (l.none)
            Div((title + ": empty").spn(styles.dim))
        else if (visible)
            Div(l./(c => Div(cardImage(c, styles.card, highlightOf(c)) ~ badges(c), styles.slotHand)).merge, styles.hand)
        else
            Div(l./(c => Div(Image(back(c), styles.cardSmall), styles.slotHand)).merge, styles.hand)
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Deck selection
    // ---------------------------------------------------------------------------------------------------------------

    def deckSelection(f : Faction) : Elem = {
        val g = game
        val mine = viewer.forall(_ == f)
        g.decks.get(f) match {
            case Some(codes) =>
                val id = codes./(Cards.get)./~(x => x).%(_.isIdentity).starting
                Div(id./(c => Div(Image(c.code, styles.card), styles.slot)).|(Empty) ~ Div(("Deck chosen: " ~ id./(_.title).|("?").hl ~ " (" ~ (codes.num - 1).toString ~ " cards)").spn), styles.row)
            case None if mine && lastActions.exists(_.is[ChoosePresetDeckAction]) =>
                val presets = lastActions.of[ChoosePresetDeckAction].%(_.self == f)
                val items = presets./ { a =>
                    val d = Decks.presets(a.index)
                    val idc = Cards.get(d.identity)
                    val check = Decks.validate(f, d.codes)
                    Parameter(("deck", a), OnClick(Div(
                        idc./(c => Image(c.code, styles.card)).|(Empty) ~
                        d.name.spn(styles.galleryName) ~
                        (d.identityTitle + " - " + check.message).spn(styles.galleryInfo),
                        styles.galleryItem)))
                }
                val importItem = Parameter("import", OnClick(Div(Image(f.isCorp.?("corp-back").|("runner-back"), styles.card) ~ "Import a decklist".spn(styles.galleryName) ~ "paste a NetrunnerDB export".spn(styles.galleryInfo), styles.galleryItem)))
                Div("Choose a deck".spn(styles.menuTitle)) ~ Div((items :+ importItem).merge, styles.gallery)
            case None =>
                Div("choosing a deck...".spn(styles.dim))
        }
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
        val img = c.%(canSee)./(c => Div(Image(c.code, styles.card), styles.slot)).|(Empty)
        showOverlay(overlayScrollX(Div(img ~ Div(title, styles.menuTitle) ~ items.merge ~ view ~ close, xlo.column)), onClick)
    }

    def updateStatus() {
        corpPane.replace(corpBoard, resources, onClick, onOver, onOut)
        runnerPane.replace(runnerBoard, resources, onClick, onOver, onOut)
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

    def onOver(a : Any) : Unit = a @@ {
        case c : Card if canSee(c) =>
            val d = c.data
            val e = Div(Div(Image(c.code, styles.zoomImg), styles.slot) ~ Div(d.title.hlb ~ Break ~ cardStats(c).join(" | ") ~ Break ~ Break ~ cardText(c) ~ stateText(c), styles.zoomText), xlo.flexhcenter)(xlo.flexnowrap)
            new ElementAttachmentPoint(zoomNode).clear()
            new ElementAttachmentPoint(zoomNode).append(e, resources)
            placeZoom()
            zoomNode.style.display = "block"
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
            (inPlay && c.data.isIce).$(g.serverOf(c)./(s => "protecting " + s.name).|("")) ++
            (CardImpls.has(c.code)).?($()).|($("no automation: use the Manual menu"))
        l.any.?("\n\n" + l.join(", ")).|("")
    }

    def cardOverlay(c : Card) : Elem = {
        val g = game
        if (canSee(c))
            Div(Image(c.code, styles.artwork), styles.slot) ~ Div(c.title.hlb ~ Break ~ cardStats(c).join(" | ") ~ Break ~ Break ~ cardText(c) ~ stateText(c), styles.cardText)
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

        case ("deck", a : UserAction) =>
            if (lastThen != null)
                lastThen(a)

        case ("zoom", c : Card) =>
            showOverlay(overlayScrollX(cardOverlay(c)), onClick)

        case "archives" =>
            val g = game
            val l = g.discard(Corp).%(canSee)
            val hidden = g.discard(Corp).num - l.num
            showOverlay(overlayScrollX(pileOverlay("Archives" + (hidden > 0).??(" (" + hidden + " facedown)"), l, true)), onClick)

        case "heap" =>
            showOverlay(overlayScrollX(pileOverlay("Heap", game.discard(Runner), false)), onClick)

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

        button("Check", "nr-import-check", () => { check() })

        button("Import", "nr-import-ok", () => {
            check().foreach { codes =>
                hideOverlay()
                if (lastThen != null)
                    lastThen(ImportDeckAction(self, codes))
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
        lastThen = then
        lastActions = actions
        super.ask(faction, actions, then)
        updateStatus()
    }

    override def wait(self : $[Faction], factions : $[Faction], message : Elem) {
        lastThen = null
        lastActions = $
        super.wait(self, factions, message)
        updateStatus()
    }

    override def preinfo(self : |[Faction], aa : $[UserAction]) = {
        val ii = currentGame.info(Nil, self, aa)
        ii.any.??(convertActions(self, ii))
    }

    override def info(self : |[Faction], aa : $[UserAction]) = {
        aa.exists(_.is[ImportDeckMarker]).$(
            ZBasic(Break, "Import NetrunnerDB decklist".hh, () => { onClick("import") }, ZBasic.infoch).copy(clear = false)
        ) ++
        (currentGame.isOver && hrf.HRF.flag("replay").not).$(
            ZBasic(Break ~ Break ~ Break, "Save Replay".hh, () => {
                callbacks.saveReplay {}
            }).copy(clear = false)
        ) ++
        (currentGame.isOver && callbacks.canPlayAgain).$(
            ZBasic(Break ~ Break ~ Break, "Play Again".hh, () => {
                callbacks.playAgain()
            }).copy(clear = false)
        ) ++
        $(ZBasic(Break ~ Break, "Interface".spn, () => { callbacks.editSettings { updateStatus() } }, ZBasic.infoch).copy(clear = false))
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
