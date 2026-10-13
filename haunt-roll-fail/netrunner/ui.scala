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

    var lastThen : UserAction => Unit = null
    var lastActions : $[UserAction] = $

    def factionElem(f : Faction) = f.name.styled(f)

    // ---------------------------------------------------------------------------------------------------------------
    // Visibility
    // ---------------------------------------------------------------------------------------------------------------

    def viewer : |[Faction] = lastSelf

    def canSee(c : Card) : Boolean = {
        val g = game
        viewer match {
            case None => true
            case Some(v) =>
                g.zone(c) match {
                    case HandZone(f) => f == v
                    case DeckZone(f) => false
                    case DiscardZone(Corp) => v.isCorp || g.state(c).seen
                    case DiscardZone(Runner) => true
                    case RootZone(_) | IceZone(_) => v.isCorp || g.isRezzed(c) || g.state(c).seen
                    case HostedZone(h) => g.owner(c) == v || (g.owner(c).isCorp && g.isRezzed(c)) || (g.owner(c).isRunner && !g.state(c).facedown)
                    case RigZone => v.isRunner || !g.state(c).facedown
                    case _ => true
                }
        }
    }

    def back(c : Card) = game.owner(c).isCorp.?("corp-back").|("runner-back")

    def cardImage(c : Card, style : Style) : Elem = {
        val img = canSee(c).?(Image(c.code, style)).|(Image(back(c), style))
        img.pointer.onClick.param(c)
    }

    def countersElem(c : Card) : Elem = {
        val g = game
        val s = g.state(c)
        val l =
            (s.advancement > 0).$(("adv " + s.advancement).spn(styles.counter)(styles.counterAdv)) ++
            (s.virus > 0).$(("virus " + s.virus).spn(styles.counter)(styles.counterVirus)) ++
            (s.credits > 0).$((s.credits + "c").spn(styles.counter)(styles.counterCredit)) ++
            (s.power > 0).$(("power " + s.power).spn(styles.counter)(styles.counterPower)) ++
            (c.data.isIce && g.isRezzed(c) && g.iceStrength(c) != c.data.strength.|(0)).$(("str " + g.iceStrength(c)).spn(styles.counter)) ++
            (c.data.isIcebreaker && g.breakerStrength(c) != c.data.strength.|(0)).$(("str " + g.breakerStrength(c)).spn(styles.counter))
        l.any.?(Div(l.merge)).|(Empty)
    }

    def slot(c : Card, style : Style = styles.card) : Elem = {
        val g = game
        val hostedElems = g.hosted(c)./(h => slot(h, styles.cardSmall))
        Div(cardImage(c, style) ~ countersElem(c) ~ hostedElems.merge, styles.slot)
    }

    def iceSlot(c : Card) : Elem = {
        val g = game
        val running = g.run.exists(r => r.ice.has(c))
        val hostedElems = g.hosted(c)./(h => slot(h, styles.cardTiny))
        Div(cardImage(c, styles.cardIce) ~ countersElem(c) ~ hostedElems.merge, styles.iceSlot)(running.?(styles.selected).|(styles.slot))
    }

    def serverElem(s : Server) : Elem = {
        val g = game
        val running = g.run.exists(r => r.server == s && !r.ended)
        val iceElems = g.ice(s).reverse./(iceSlot)
        val rootElems = g.root(s)./(c => slot(c, styles.cardSmall))
        val central = s match {
            case HQ => Div(Image("corp-back", styles.cardSmall)) ~ (g.hand(Corp).num + " cards").spn(styles.serverName)
            case RnD => Div(Image("corp-back", styles.cardSmall)) ~ (g.deck(Corp).num + " cards").spn(styles.serverName)
            case Archives =>
                val l = g.discard(Corp)
                val faceup = l.%(c => g.state(c).seen || viewer.exists(_.isCorp) || viewer.none)
                Div((l.any.?(Image(l.last.use(c => (g.state(c).seen || viewer.forall(_.isCorp)).?(c.code).|("corp-back")), styles.cardSmall).pointer.onClick.param("archives")).|(Empty))) ~ (l.num + " cards").spn(styles.serverName)
            case _ => Empty
        }
        Div(
            s.name.spn(styles.serverName).div ~
            Div(iceElems.merge, styles.row) ~
            Div(central ~ rootElems.merge, styles.row),
            styles.server
        )(running.?(styles.serverRun).|(styles.server))
    }

    def statLine(l : $[Elem]) : Elem = Div(l./(e => e.spn(styles.stat)).merge, styles.statLine)

    def corpBoard : Elem = {
        val g = game
        val id = g.identity.get(Corp)
        val header = statLine($(
            Corp.name.styled(Corp)(styles.name) ~ " " ~ resources.getName(Corp)./(n => (" " + n).spn(styles.dim)).|(Empty),
            id./(c => g.cardElem(c)).|("no identity".spn(styles.dim)),
            g.credits(Corp).hl ~ " credits",
            g.clicks(Corp).hl ~ " clicks",
            g.badPublicity.hl ~ " bad pub",
            g.agendaPoints(Corp).hl ~ " agenda points",
        ))
        val idImg = id./(c => Div(Image(c.code, styles.cardSmall).pointer.onClick.param(c), styles.slot)).|(Empty)
        val scored = g.scored(Corp)./(c => slot(c, styles.cardTiny))
        val servers = g.servers./(serverElem)
        val hand = handElem(Corp)
        val active = (g.active == Corp && g.turnStarted).?(" (active)".spn(styles.dim)).|(Empty)

        header ~ active ~ Div(idImg ~ Div(scored.merge, styles.row) ~ servers.merge, styles.row) ~ hand
    }

    def runnerBoard : Elem = {
        val g = game
        val id = g.identity.get(Runner)
        val header = statLine($(
            Runner.name.styled(Runner)(styles.name) ~ " " ~ resources.getName(Runner)./(n => (" " + n).spn(styles.dim)).|(Empty),
            id./(c => g.cardElem(c)).|("no identity".spn(styles.dim)),
            g.credits(Runner).hl ~ " credits",
            g.clicks(Runner).hl ~ " clicks",
            g.tags.hl ~ " tags",
            g.link.hl ~ " link",
            g.memoryUsed.hl ~ "/" ~ g.memoryTotal.hl ~ " MU",
            g.agendaPoints(Runner).hl ~ " agenda points",
            (g.coreDamage > 0).?(g.coreDamage.hl ~ " core damage").|(Empty),
        ))
        val idImg = id./(c => Div(Image(c.code, styles.cardSmall).pointer.onClick.param(c), styles.slot)).|(Empty)
        val scored = g.scored(Runner)./(c => slot(c, styles.cardTiny))
        val programs = g.rig.%(_.data.isProgram)./(slot(_))
        val hardware = g.rig.%(_.data.isHardware)./(slot(_))
        val resourcesL = g.rig.%(_.data.isResource)./(slot(_))
        val heap = g.discard(Runner)
        val piles = Div(
            Div(Div(Image("runner-back", styles.cardSmall)) ~ (g.deck(Runner).num + " stack").spn(styles.serverName), styles.slot) ~
            Div(heap.any.?(Image(heap.last.code, styles.cardSmall).pointer.onClick.param("heap")).|(Div(Empty, styles.cardSmall)) ~ (heap.num + " heap").spn(styles.serverName), styles.slot),
            styles.row)
        val runLine = g.run./(r => runStatus(r)).|(Empty)
        val hand = handElem(Runner)
        val active = (g.active == Runner && g.turnStarted).?(" (active)".spn(styles.dim)).|(Empty)

        header ~ active ~ runLine ~ Div(idImg ~ Div(scored.merge, styles.row) ~ piles ~ Div(programs.merge, styles.row) ~ Div(hardware.merge, styles.row) ~ Div(resourcesL.merge, styles.row), styles.row) ~ hand
    }

    def runStatus(r : Run) : Elem = {
        val g = game
        val ph = r.phase match {
            case InitiationPhase => "initiating"
            case ApproachIcePhase => "approaching ice"
            case EncounterPhase => "encountering " + r.ice./(_.title).|("")
            case MovementPhase => "moving"
            case ApproachServerPhase => "approaching server"
            case SuccessPhase => "successful"
            case AccessPhase => "accessing"
            case EndedPhase => "ended"
        }
        Div(("Run on " ~ r.server.elem ~ ": " ~ ph ~ " (" ~ r.position.toString ~ " ice to pass)").spn(styles.dim))
    }

    def handElem(f : Faction) : Elem = {
        val g = game
        val l = g.hand(f)
        val visible = viewer.forall(_ == f)
        if (l.none)
            Div((f.isCorp.?("HQ").|("Grip") + ": empty").spn(styles.dim))
        else if (visible)
            Div(l./(c => Div(cardImage(c, styles.card), styles.slot)).merge, styles.hand)
        else
            Div(l./(c => Div(Image(back(c), styles.cardSmall), styles.slot)).merge, styles.hand)
    }

    def updateStatus() {
        corpPane.replace(corpBoard, resources, onClick)
        runnerPane.replace(runnerBoard, resources, onClick)
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
            .replace("[credit]", "[c]").replace("[click]", "[click]").replace("[mu]", "MU").replace("[subroutine]", "[sub]")
            .replace("[trash]", "[trash]").replace("[recurring-credit]", "[recurring c]").replace("[link]", "[link]")
            .replace("&amp;", "&")
    }

    def cardOverlay(c : Card) : Elem = {
        val g = game
        val d = c.data
        val stats = $(d.kind) ++ d.subtypes.some./(_.join(" - ")).$ ++ d.cost./("Cost " + _).$ ++ d.strength./("Strength " + _).$ ++ d.advancementCost./("Advancement " + _).$ ++ d.agendaPoints./("Points " + _).$ ++ d.memory./("MU " + _).$ ++ d.trashCost./("Trash " + _).$ ++ d.influence./("Influence " + _).$
        if (canSee(c))
            Div(Image(c.code, styles.artwork), styles.slot) ~ Div(d.title.hlb ~ Break ~ stats.join(" | ") ~ Break ~ Break ~ cardText(c) ~ Break ~ (CardImpls.has(c.code).?("").|("(no automation: use the Manual menu)")), styles.cardText)
        else
            Div(Image(back(c), styles.artwork), styles.slot)
    }

    def overlayScrollX(e : Elem) = overlayScroll(e)(xstyles.seeThrough).onClick
    def overlayFitX(e : Elem) = overlayFit(e)(xstyles.seeThrough).onClick

    def showOverlay(e : Elem, onClick : Any => Unit) {
        overlayPane.vis()
        overlayPane.replace(e, resources, onClick, _ => {}, _ => {})
    }

    def hideOverlay() {
        overlayPane.clear()
        overlayPane.invis()
    }

    def pileOverlay(l : $[Card]) : Elem = {
        Div(l.reverse./(c => Div(cardImage(c, styles.card), styles.slot)).merge, styles.row)
    }

    override def onClick(a : Any) : Unit = a @@ {
        case c : Card =>
            showOverlay(overlayScrollX(cardOverlay(c)), onClick)

        case "archives" =>
            showOverlay(overlayScrollX(pileOverlay(game.discard(Corp))), onClick)

        case "heap" =>
            showOverlay(overlayScrollX(pileOverlay(game.discard(Runner))), onClick)

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

        val root = overlayPane.attach.appendContainer(Div(Div(Div("Paste a NetrunnerDB decklist".hlb ~ Break ~ Content, xstyles.middleScrollIn), xstyles.middleScrollOut), xstyles.unselectable, xstyles.inner, xstyles.pane.action), resources)

        val parent = root.attach.parent
        val ta = dom.document.createElement("textarea").asInstanceOf[dom.html.TextArea]
        ta.id = "nr-import"
        ta.rows = 24
        ta.cols = 60
        ta.spellcheck = false
        ta.style.display = "block"
        ta.style.margin = "1ex auto"
        ta.style.fontSize = "90%"
        ta.style.color = "#d3d3d3"
        ta.style.backgroundColor = "#111111"
        parent.appendChild(ta)

        val msg = dom.document.createElement("div").asInstanceOf[dom.html.Div]
        msg.style.color = "#ffa500"
        msg.style.minHeight = "3ex"
        parent.appendChild(msg)

        def button(label : String, f : () => Unit) = {
            val b = dom.document.createElement("div").asInstanceOf[dom.html.Div]
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

        button("Import", () => {
            val (codes, errors) = Decks.parse(ta.value)
            if (errors.any)
                msg.textContent = errors.join("; ")
            else {
                val v = Decks.validate(self, codes)
                if (!v.ok)
                    msg.textContent = v.message
                else {
                    hideOverlay()
                    if (lastThen != null)
                        lastThen(ImportDeckAction(self, codes))
                }
            }
        })

        button("Cancel", () => hideOverlay())
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Layout
    // ---------------------------------------------------------------------------------------------------------------

    val zoom = 0.72

    val layouts = $(
        Layout("base",
            $(
                BasicPane("corp", 50, 17, Priorities(top = 1, left = 1, grow = 3)),
                BasicPane("runner", 50, 17, Priorities(bottom = 1, left = 1, grow = 3)),
                BasicPane("log", 20, 10, Priorities(grow = 2, maxYscale = 4)),
                BasicPane("action", 23, 36, Priorities(bottom = 1, right = 1, grow = 2, maxXscale = 2.5, maxYscale = 2.5))
            )./(p => p.copy(kX = p.kX * zoom, kY = p.kY * zoom))
        ),
        Layout("narrow",
            $(
                BasicPane("corp", 40, 16, Priorities(top = 1, grow = 3)),
                BasicPane("runner", 40, 16, Priorities(grow = 3)),
                BasicPane("log", 40, 6, Priorities(grow = 1, maxYscale = 3)),
                BasicPane("action", 40, 20, Priorities(bottom = 1, grow = 2, maxYscale = 3))
            )./(p => p.copy(kX = p.kX * zoom, kY = p.kY * zoom)),
            0.9
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

    val layoutKey = "v" + 1

    override def ask(faction : |[Faction], actions : $[UserAction], then : UserAction => Unit) {
        lastThen = then
        lastActions = actions
        super.ask(faction, actions, then)
        updateStatus()
    }

    override def wait(self : $[Faction], factions : $[Faction], message : Elem) {
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
        a @@ {
            case _ if unavailable => $(xstyles.unavailableText)
            case _ => Nil
        } ++
        a @@ {
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
        $(xlo.fullwidth) ++
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
