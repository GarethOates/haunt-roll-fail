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


case class CorpInstallEffect(card : Card, server : |[Server], ignoreCost : Boolean) extends ForcedAction
case class RunnerInstallEffect(card : Card, host : |[Card], ignoreCost : Boolean) extends ForcedAction
case class PlayCleanupAction(card : Card) extends ForcedAction
case class ChooseServerEffect(f : Faction, card : Card, ignoreCost : Boolean, servers : $[Server], allowNew : Boolean) extends ForcedAction with Reaskable
case class ServerChosenAction(self : Faction, card : Card, server : |[Server], ignoreCost : Boolean) extends BaseAction("Install")(g => g.cardElem(card) ~ " in " ~ server./(_.elem).|("a new remote server".hl))


trait CardRules { game : Game =>

    def performCards : PartialFunction[Action, Continue] = {
        case TriggerEventAction(e) =>
            trigger(e)
            NextAction

        case CorpInstallEffect(c, so, ignoreCost) =>
            if (location.get(c).exists(inPlayZone))
                NextAction
            else {
                lastInstallFrom = zone(c)
                val s : Server = so.|(newRemote())
                val cost = ignoreCost.?(0).|(installCost(c, |(s)))
                if (cost > 0) {
                    spendCredits(Corp, cost, Spend.install(c, |(s)))
                }
                if (c.data.isIce) {
                    moveTo(c, IceZone(s))
                    log(Corp, "installs ice protecting", s, (cost > 0).?("(" ~ cost.credits ~ ")").|(Empty))
                }
                else {
                    if (c.data.isAgenda || c.data.isAsset) {
                        root(s).%(x => x.data.isAgenda || x.data.isAsset).foreach { x =>
                            trashCard(x, Corp, false)
                        }
                    }
                    moveTo(c, RootZone(s))
                    log(Corp, "installs a card in", s)
                }
                state(c).installedTurn = turn
                state(c).rezzed = false
                regInc("installed")
                pushAll(impl(c).onInstall(c))
                trigger(Installed(Corp, c))
                NextAction
            }

        case RunnerInstallEffect(c, h, ignoreCost) =>
            if (location.get(c).exists(inPlayZone))
                NextAction
            else {
                val cost = ignoreCost.?(0).|(hostedInstallCost(c, h))
                spendCredits(Runner, cost, Spend.install(c, None, h))
                // uniqueness
                if (c.data.unique)
                    installedCards(Runner).%(x => x.title == c.title && x != c && !state(x).facedown).foreach { x =>
                        log(Runner, "trashes", x, "(unique)")
                        trashCard(x, Runner, false)
                    }
                if (has(c, "Console"))
                    installedCards(Runner).%(x => has(x, "Console") && x != c && !state(x).facedown).foreach { x =>
                        log(Runner, "trashes", x, "(console limit)")
                        trashCard(x, Runner, false)
                    }
                h match {
                    case Some(host) => moveTo(c, HostedZone(host))
                    case None => moveTo(c, RigZone)
                }
                state(c).installedTurn = turn
                impl(c).initialCounters(c).foreach { case (k, n) => addCounter(c, k, n) }
                log(Runner, "installs", c, h./(x => " hosted on " ~ cardElem(x)).|(Empty), (cost > 0).?("(" ~ cost.credits ~ ")").|(Empty))
                regInc("installed")
                if (c.data.isProgram)
                    regInc("install-program")
                pushAll(impl(c).onInstall(c))
                trigger(Installed(Runner, c))
                NextAction
            }

        case PlayCleanupAction(c) =>
            if (zone(c).is[PlayZone]) {
                if (run.exists(r => r.source.has(c) && !r.ended))
                    enqueue(PlayCleanupAction(c))
                else {
                    trigger(PlayResolved(owner(c), c))
                    if (impl(c).staysInPlay(c))
                        ()
                    else {
                        if (owner(c).isCorp)
                            toArchives(c, true)
                        else
                            moveTo(c, DiscardZone(Runner))
                        trigger(Trashed(c, owner(c), false))
                    }
                }
            }
            NextAction

        case ChooseServerEffect(f, c, ignoreCost, l, allowNew) =>
            Ask(f).group("Install " ~ cardElem(c)).add(l./(s => ServerChosenAction(f, c, |(s), ignoreCost))).when(allowNew)(ServerChosenAction(f, c, None, ignoreCost))

        case ServerChosenAction(f, c, s, ignoreCost) =>
            push(CorpInstallEffect(c, s, ignoreCost))
            NextAction
    }
}
