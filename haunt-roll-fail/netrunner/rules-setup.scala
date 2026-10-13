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


case object ChooseDecksAction extends ForcedAction
case class ChoosePresetDeckAction(self : Faction, index : Int) extends BaseAction("Choose a deck")(Image(Decks.presets(index).identity, netrunner.elem.styles.cardTiny) ~ " " ~ Decks.presets(index).name.hl ~ " (" ~ Decks.presets(index).identityTitle ~ ")")
case class ImportDeckMarker(self : Faction) extends HiddenChoice with SelfValidate {
    def validate(a : Action) = a @@ {
        case ImportDeckAction(f, codes) => f == self && Decks.validate(f, codes).ok
        case _ => false
    }
}
case class ImportDeckAction(self : Faction, codes : $[String]) extends BaseAction("Choose a deck")("Imported deck")
case object SetupDecksAction extends ForcedAction
case class MulliganAskAction(f : Faction) extends ForcedAction with Reaskable
case class KeepHandAction(self : Faction) extends BaseAction("Keep this hand or mulligan?")("Keep")
case class MulliganAction(self : Faction) extends BaseAction("Keep this hand or mulligan?")("Mulligan")


trait SetupRules { game : Game =>

    def performSetup : PartialFunction[Action, Continue] = {
        case StartAction(version) =>
            log("HRF".hl, "version", gaming.version.hlb)
            log("Netrunner".hlb)

            if (version != gaming.version)
                log("Saved game version", version.hlb)

            ChooseDecksAction

        case ChooseDecksAction =>
            val waiting = setup.%(f => decks.contains(f).not)

            if (waiting.none)
                SetupDecksAction
            else {
                val asks = waiting./ { f =>
                    Ask(f)
                        .group("Choose a deck")
                        .add(Decks.presets.indexed.%(_.side == f).indices./(i => ChoosePresetDeckAction(f, i)))
                        .add(ImportDeckMarker(f))
                }

                if (asks.num == 1)
                    asks.only
                else
                    MultiAsk(asks)
            }

        case ChoosePresetDeckAction(f, i) =>
            val d = Decks.presets(i)
            decks += f -> d.codes
            log(f, "chose", d.name.hl, "(" ~ d.identityTitle.hl ~ ")")
            ChooseDecksAction

        case ImportDeckAction(f, codes) =>
            val v = Decks.validate(f, codes)
            if (v.ok) {
                decks += f -> codes
                val id = codes./(Cards.byCode.get)./~(x => x).%(_.isIdentity).starting./(_.title).|("?")
                log(f, "imported a deck", "(" ~ id.hl ~ ")")
            }
            else
                log(f, "deck rejected:", v.message)
            ChooseDecksAction

        case SetupDecksAction =>
            setup.foreach { f =>
                val codes = decks(f)
                val counts = codes.groupBy(c => c).toList./((c, l) => c -> l.num)
                val cards = counts.toList.sortBy(_._1)./~((c, n) => 1.to(n)./(i => Card(c, i)))
                val (ids, rest) = cards.partition(_.data.isIdentity)
                ids.headOption.foreach { id =>
                    place(id, IdentityZone(f))
                    identity += f -> id
                }
                rest.foreach(c => place(c, DeckZone(f)))
                log(f, "plays", identity(f))
            }

            credits = Map(Corp -> 5, Runner -> 5)
            clicks = Map(Corp -> 0, Runner -> 0)
            phase = "mulligan"

            pending = $(ShuffleDeckEffect(Corp), ShuffleDeckEffect(Runner), DrawEffect(Corp, 5), DrawEffect(Runner, 5), MulliganAskAction(Corp), MulliganAskAction(Runner), StartTurnAction(Corp))

            NextAction

        case MulliganAskAction(f) =>
            Ask(f).add(KeepHandAction(f)).add(MulliganAction(f))

        case KeepHandAction(f) =>
            kept :+= f
            log(f, "keeps the hand")
            NextAction

        case MulliganAction(f) =>
            mulliganed :+= f
            hand(f).foreach(c => moveTo(c, DeckZone(f)))
            log(f, "takes a mulligan")
            push(ShuffleDeckEffect(f), DrawEffect(f, 5))
            NextAction
    }
}
