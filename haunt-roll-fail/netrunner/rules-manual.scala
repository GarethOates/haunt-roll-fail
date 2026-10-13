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


case class ManualAction(self : Faction) extends BaseAction()("Manual adjustments".hh) with Soft with NotForBots
case class ManualNumberAction(self : Faction, target : Faction, what : String, delta : Int) extends BaseAction("Manual")(g => target.name.styled(target) ~ " " ~ what ~ " " ~ ((delta > 0).?("+").|("") + delta)) with NotForBots
case class ManualDrawAction(self : Faction, target : Faction) extends BaseAction("Manual")(g => target.name.styled(target) ~ " draws a card") with NotForBots
case class ManualShuffleAction(self : Faction, target : Faction) extends BaseAction("Manual")(g => target.name.styled(target) ~ " shuffles the deck") with NotForBots
case class ManualCardMenuAction(self : Faction) extends BaseAction("Manual")("Adjust a card...") with Soft with NotForBots
case class ManualCardAction(self : Faction, card : Card) extends BaseAction("Manual")(g => g.cardElem(card) ~ " (" ~ g.zoneName(g.zone(card)) ~ ")") with Soft with NotForBots
case class ManualCounterAction(self : Faction, card : Card, kind : String, delta : Int) extends BaseAction(g => g.cardElem(card))(g => kind ~ " counter " ~ ((delta > 0).?("+").|("") + delta)) with NotForBots
case class ManualMoveAction(self : Faction, card : Card, zone : Zone, top : Boolean) extends BaseAction(g => g.cardElem(card))(g => "Move to " ~ g.zoneName(zone) ~ (top.?(" (top)").|(""))) with NotForBots
case class ManualTrashAction(self : Faction, card : Card) extends BaseAction(g => g.cardElem(card))("Trash") with NotForBots
case class ManualRezAction(self : Faction, card : Card, rezzed : Boolean) extends BaseAction(g => g.cardElem(card))(rezzed.?("Rez (free)").|("Derez")) with NotForBots
case class ManualFlipAction(self : Faction, card : Card) extends BaseAction(g => g.cardElem(card))("Flip faceup/facedown") with NotForBots
case class ManualEndRunAction(self : Faction) extends BaseAction("Manual")("End the run") with NotForBots


trait ManualRules { game : Game =>

    def zoneName(z : Zone) : String = z match {
        case HandZone(f) => f.isCorp.?("HQ").|("Grip")
        case DeckZone(f) => f.isCorp.?("R&D").|("Stack")
        case DiscardZone(f) => f.isCorp.?("Archives").|("Heap")
        case ScoredZone(f) => f.name + " score area"
        case RemovedZone(f) => "Removed from game"
        case IdentityZone(f) => "Identity"
        case PlayZone(f) => "Play area"
        case RootZone(s) => s.name
        case IceZone(s) => "Ice protecting " + s.name
        case RigZone => "Rig"
        case HostedZone(h) => "Hosted on " + h.title
        case LimboZone => "Limbo"
    }

    def performManual : PartialFunction[Action, Continue] = {
        case ManualAction(f) =>
            val o = f.opponent
            Ask(f)
                .group("Manual adjustments".hh)
                .add(ManualNumberAction(f, f, "credits", 1))
                .add(ManualNumberAction(f, f, "credits", -1))
                .add(ManualNumberAction(f, f, "credits", 5))
                .add(ManualNumberAction(f, f, "credits", -5))
                .add(ManualNumberAction(f, f, "clicks", 1))
                .add(ManualNumberAction(f, f, "clicks", -1))
                .add(ManualNumberAction(f, Runner, "tags", 1))
                .add(ManualNumberAction(f, Runner, "tags", -1))
                .add(ManualNumberAction(f, Corp, "bad publicity", 1))
                .add(ManualNumberAction(f, Corp, "bad publicity", -1))
                .add(ManualNumberAction(f, Runner, "core damage", 1))
                .add(ManualNumberAction(f, Runner, "core damage", -1))
                .add(ManualNumberAction(f, o, "credits", 1))
                .add(ManualNumberAction(f, o, "credits", -1))
                .add(ManualNumberAction(f, o, "clicks", 1))
                .add(ManualNumberAction(f, o, "clicks", -1))
                .add(ManualDrawAction(f, f))
                .add(ManualShuffleAction(f, f))
                .add(ManualCardMenuAction(f))
                .add(run.any.$(ManualEndRunAction(f)))
                .cancel

        case ManualNumberAction(f, t, what, d) =>
            what match {
                case "credits" => credits += t -> (credits(t) + d).atLeast(0)
                case "clicks" => clicks += t -> (clicks(t) + d).atLeast(0)
                case "tags" => tags = (tags + d).atLeast(0)
                case "bad publicity" => badPublicity = (badPublicity + d).atLeast(0)
                case "core damage" => coreDamage = (coreDamage + d).atLeast(0)
            }
            log(f, "manually adjusts", t.name.styled(t), what, ((d > 0).?("+").|("") + d).hl)
            current

        case ManualDrawAction(f, t) =>
            deck(t).starting.foreach(c => moveTo(c, HandZone(t)))
            log(f, "manually draws a card for", t)
            current

        case ManualShuffleAction(f, t) =>
            push(current)
            push(ShuffleDeckEffect(t))
            NextAction

        case ManualCardMenuAction(f) =>
            val l = hand(f) ++ installedCards(f) ++ scored(f) ++ discard(f).takeRight(8) ++ deck(f).take(3) ++ f.isCorp.??(installedCards(Runner)) ++ f.isRunner.??(installedCards(Corp))
            Ask(f).group("Choose a card".hh).add(l.distinct./(c => ManualCardAction(f, c))).cancel

        case ManualCardAction(f, c) =>
            val o = owner(c)
            Ask(f)
                .group(cardElem(c))
                .add($("advancement", "virus", "credit", "power")./~(k => $(ManualCounterAction(f, c, k, 1), ManualCounterAction(f, c, k, -1).!(counter(c, k) == 0))))
                .add(ManualTrashAction(f, c).!(zone(c).is[DiscardZone]))
                .add(ManualMoveAction(f, c, HandZone(o), false).!(zone(c) == HandZone(o)))
                .add(ManualMoveAction(f, c, DeckZone(o), true))
                .add(ManualMoveAction(f, c, DeckZone(o), false))
                .add(ManualMoveAction(f, c, ScoredZone(Corp), false).!(zone(c) == ScoredZone(Corp)))
                .add(ManualMoveAction(f, c, ScoredZone(Runner), false).!(zone(c) == ScoredZone(Runner)))
                .add(ManualMoveAction(f, c, RemovedZone(o), false).!(zone(c).is[RemovedZone]))
                .add(o.isRunner.$(ManualMoveAction(f, c, RigZone, false).!(zone(c) == RigZone)))
                .add(o.isCorp.??(remotes./(r => ManualMoveAction(f, c, RootZone(r), false).!(zone(c) == RootZone(r)))))
                .add(o.isCorp.$(ManualMoveAction(f, c, RootZone(HQ), false), ManualMoveAction(f, c, RootZone(RnD), false), ManualMoveAction(f, c, RootZone(Archives), false)))
                .add(o.isCorp.??(servers./(s => ManualMoveAction(f, c, IceZone(s), false).!(zone(c) == IceZone(s)))))
                .add(o.isCorp.$(ManualRezAction(f, c, !isRezzed(c))))
                .add(ManualFlipAction(f, c))
                .cancel

        case ManualCounterAction(f, c, k, d) =>
            addCounter(c, k, d)
            log(f, "manually adjusts", k, "counters on", hiddenCorpElem(c), ((d > 0).?("+").|("") + d).hl)
            current

        case ManualMoveAction(f, c, z, top) =>
            moveTo(c, z, top)
            if (z.is[DiscardZone]) state(c).seen = true
            cleanupRemotes()
            log(f, "manually moves", (owner(c).isCorp && !z.is[DiscardZone] && !z.is[ScoredZone]).?(hiddenCorpElem(c)).|(cardElem(c)), "to", zoneName(z))
            current

        case ManualTrashAction(f, c) =>
            trashCard(c, f, false)
            push(current)
            NextAction

        case ManualRezAction(f, c, r) =>
            state(c).rezzed = r
            if (r) {
                state(c).seen = true
                impl(c).initialCounters(c).foreach { case (k, n) => if (counter(c, k) == 0) addCounter(c, k, n) }
            }
            log(f, "manually", r.?("rezzes").|("derezzes"), c)
            current

        case ManualFlipAction(f, c) =>
            if (owner(c).isRunner)
                state(c).facedown = !state(c).facedown
            else
                state(c).seen = !state(c).seen
            log(f, "manually flips", c)
            current

        case ManualEndRunAction(f) =>
            log(f, "manually ends the run")
            push(current)
            push(EndRunEffect)
            NextAction
    }
}
