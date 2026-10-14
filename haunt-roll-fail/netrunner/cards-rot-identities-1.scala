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
import Helpers._


// ---------------------------------------------------------------------------------------------------------------------
// Rotated identities (Draft .. Order and Chaos), ported from jinteki.net src/clj/game/cards/identities.clj
// ---------------------------------------------------------------------------------------------------------------------

case class RHWyvernAction(card : Card) extends CardAction
case class RHSyntheticPurpose(card : Card) extends CardPurpose
case class RHCollectiveAction(card : Card, kind : String) extends CardAction
case class RHNextAction(card : Card, remaining : Int) extends CardAction
case class RHNextPurpose(card : Card, remaining : Int) extends CardPurpose
case class RHNextDrawAction(card : Card) extends CardAction
case class RHGrndlAction(card : Card) extends CardAction
case class RHNasirAction(card : Card, ice : Card) extends CardAction
case class RHFoundryAction(card : Card, ice : Card) extends CardAction
case class RHValenciaAction(card : Card) extends CardAction
case class RHBiotechFlipAction(card : Card) extends CardAction
case class RHHarishchandraAction(card : Card, cards : $[Card]) extends CardAction


// draft identities: win at 6 agenda points
abstract class RHDraft(code : String) extends RH(code) {
    override def winPointsMod(self : Card, f : Faction)(implicit g : Game) = -1
}


object RotIdentityCards1 {
    object TheShadow extends RHDraft("00005")

    object TheMasque extends RHDraft("00006")

    object Wyvern extends RHDraft("00007") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Trashed(c, Runner, _) if g.owner(c).isCorp && mostFaction(Runner, "Anarch") && g.discard(Runner).any => $(RHWyvernAction(self))
            case _ => $
        }
        override def performRH(a : CardAction)(implicit g : Game) = a match {
            case RHWyvernAction(self) =>
                g.discard(Runner).lastOption.foreach { c =>
                    g.moveTo(c, DeckZone(Runner))
                    g.log(Runner, "shuffles", c, "from the heap into the stack (Wyvern)")
                    g.push(ShuffleDeckEffect(Runner))
                }
                NextAction
        }
    }

    object Boris extends RHDraft("00008") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) if g.tags > 0 && mostFaction(Runner, "Criminal") => $(says(self, "remove 1 tag"), RemoveTagsEffect(1))
            case _ => $
        }
    }

    object Jamie extends RHDraft("00009") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Installed(Runner, _) if firstTime(self, "jamie") => $(MarkUsedEffect(self, "jamie")) ++ mostFaction(Runner, "Shaper").$(says(self, "draw 1 card")) ++ mostFaction(Runner, "Shaper").$(draw(Runner, 1))
            case _ => $
        }
    }

    object StrategicInnovations extends RHDraft("00010") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnEnds(Runner) if g.discard(Corp).any && mostFaction(Corp, "Haas-Bioroid") =>
                $(says(self, "shuffle a card from Archives into R&D"), ChooseCardEffect(Corp, g.discard(Corp), RHShuffleIntoPurpose(self), "Strategic Innovations: choose a card in Archives to shuffle into R&D", false))
            case _ => $
        }
    }

    object SyntheticSystems extends RHDraft("00011") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) if g.allIce.num >= 2 && mostFaction(Corp, "Jinteki") =>
                $(OptionalEffect(Corp, "Synthetic Systems: swap 2 pieces of installed ice?", $(says(self, "swap 2 pieces of ice"), ChooseCardsEffect(Corp, g.allIce, 2, 2, RHSyntheticPurpose(self), "Synthetic Systems: choose 2 pieces of ice to swap")), $))
            case _ => $
        }
        override def chosenRH(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards match {
                case $(a, b) => g.push(SwapIceEffect(a, b))
                case _ =>
            }
            NextAction
        }
    }

    object InformationDynamics extends RHDraft("00012") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case AgendaScored(_) | AgendaStolen(_) if mostFaction(Corp, "NBN") => $(says(self, "give the Runner 1 tag"), tag(1))
            case _ => $
        }
    }

    object FringeApplications extends RHDraft("00013") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) if g.allIce.any && mostFaction(Corp, "Weyland Consortium") =>
                $(says(self, "place 1 advancement counter on a piece of ice"), ChooseCardEffect(Corp, g.allIce, RHAdvancePurpose(self, 1), "Fringe Applications: place 1 advancement counter on a piece of ice", false))
            case _ => $
        }
    }

    object Noise extends RH("01001") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Installed(Runner, c) if c.data.isProgram && g.has(c, "Virus") => $(MessageEffect(Runner, "forces the Corp to trash the top card of R&D (Noise)"), TrashTopEffect(Corp, 1, false))
            case _ => $
        }
    }

    object Kate extends RH("01033") {
        def kind(c : Card) = c.data.isProgram || c.data.isHardware
        override def installCostMod(self : Card, card : Card)(implicit g : Game) = (kind(card) && firstTime(self, "kate")).?(-1).|(0)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Installed(Runner, c) if kind(c) && firstTime(self, "kate") => $(MarkUsedEffect(self, "kate"), says(self, "lower the install cost by 1"))
            case _ => $
        }
    }

    object HBEngineeringTheFuture extends RH("01054") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Installed(Corp, _) if firstTime(self, "etf") => $(MarkUsedEffect(self, "etf"), says(self, "gain 1 credit"), gain(Corp, 1))
            case _ => $
        }
    }

    object TheCollective extends RH("014001") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case ActionTaken(Runner, kind) => $(RHCollectiveAction(self, kind))
            case TurnBegins(Runner) => $(SetMemoEffect(self, ""), SetCounterEffect(self, "streak", 0))
            case _ => $
        }
        override def performRH(a : CardAction)(implicit g : Game) = a match {
            case RHCollectiveAction(self, kind) =>
                if (g.state(self).memo == kind)
                    g.state(self).counters += "streak" -> (g.counter(self, "streak") + 1)
                else {
                    g.state(self).memo = kind
                    g.state(self).counters += "streak" -> 1
                }
                if (g.counter(self, "streak") >= 3 && firstTime(self, "collective")) {
                    g.markUsed(self, "collective")
                    g.log(Runner, "performed the same action three times in a row (The Collective)")
                    g.push(GainClicksEffect(Runner, 1))
                }
                NextAction
        }
    }

    object ChronosProtocolHB extends RH("014002") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case DamageDone("core", n) if n > 0 && g.lastDamaged.any =>
                says(self, "remove all copies of the trashed cards from the game") +: g.lastDamaged./(_.code).distinct./(c => RHRemoveAllCopiesEffect(c)) :+ ShuffleDeckEffect(Runner)
            case _ => $
        }
    }

    object Whizzard extends RH("02001") {
        override def recurring(self : Card)(implicit g : Game) = 3
        // "Use these credits to trash cards." (trash costs of Corp cards)
        override def paysFor(self : Card, spend : Spend)(implicit g : Game) = spend.trashing.exists(c => g.owner(c).isCorp)
    }

    object WeylandBecauseWeBuiltIt extends RH("02076") {
        override def recurring(self : Card)(implicit g : Game) = 1
        // "Use this credit to advance ice."
        override def paysFor(self : Card, spend : Spend)(implicit g : Game) = spend.advancing.exists(_.data.isIce)
    }

    object Andromeda extends RH("02083") {
        override def onSetup(self : Card)(implicit g : Game) = $(says(self, "draw a starting hand of 9 cards"), DrawEffect(Runner, 4))
        override def startingHandBonus(self : Card)(implicit g : Game) = 4
    }

    object NBNTheWorldIsYours extends RH("02114") {
        override def handSize(self : Card, f : Faction)(implicit g : Game) = f.isCorp.??(1)
    }

    object CerebralImaging extends RH("03001") {
        override def handSize(self : Card, f : Faction)(implicit g : Game) = f.isCorp.??(g.credits(Corp) - 5)
    }

    object CustomBiotics extends RH("03002")

    object NEXTDesign extends RH("03003") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) if g.turn == 1 && firstTime(self, "next") => $(MarkUsedEffect(self, "next"), says(self, "install up to 3 pieces of ice"), RHNextAction(self, 3), RHNextDrawAction(self))
            case _ => $
        }
        override def performRH(a : CardAction)(implicit g : Game) = a match {
            case RHNextAction(self, n) =>
                val l = g.hand(Corp).%(_.data.isIce)
                if (n > 0 && l.any && g.servers.exists(s => g.ice(s).none))
                    g.push(ChooseCardEffect(Corp, l, RHNextPurpose(self, n), "NEXT Design: install a piece of ice (" + n + " remaining)?", true))
                NextAction
            case RHNextDrawAction(self) =>
                val k = 5 - g.hand(Corp).num
                if (k > 0)
                    g.push(DrawEffect(Corp, k))
                NextAction
            case ServerPickedAction(self, s, key) =>
                val parts = key.split(':')
                val c = Card(parts(1), parts(2).toInt)
                val n = parts(3).toInt
                g.push(CorpInstallEffect(c, s, false), RHNextAction(self, n - 1))
                NextAction
        }
        override def chosenRH(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case RHNextPurpose(self, n) =>
                cards.single.foreach { c =>
                    g.push(ChooseServerForAction(Corp, self, g.servers.%(s => g.ice(s).none), true, "next:" + c.code + ":" + c.n + ":" + n, "NEXT Design: choose a server without ice"))
                }
                NextAction
        }
    }

    object TheProfessor extends RH("03029")

    object Exile extends RH("03030") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Installed(Runner, c) if c.data.isProgram && g.rhInstallFrom.get(c).has(DiscardZone(Runner)) => $(says(self, "draw 1 card"), draw(Runner, 1))
            case _ => $
        }
    }

    object GRNDL extends RH("04097") {
        override def onSetup(self : Card)(implicit g : Game) = $(says(self, "start with 10 credits and 1 bad publicity"), GainCreditsEffect(Corp, 5), RHGrndlAction(self))
        override def performRH(a : CardAction)(implicit g : Game) = a match {
            case RHGrndlAction(self) =>
                if (g.badPublicity == 0)
                    g.push(BadPubEffect(1))
                NextAction
        }
    }

    object HarmonyMedtech extends RH("05001") {
        override def winPointsMod(self : Card, f : Faction)(implicit g : Game) = -1
    }

    object NiseiDivision extends RH("05002") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case RotPsiRevealed(_, _) => $(says(self, "gain 1 credit"), gain(Corp, 1))
            case _ => $
        }
    }

    object TenninInstitute extends RH("05003") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) if g.successfulRunsLastTurn.none && g.installedCards(Corp).any =>
                $(says(self, "place 1 advancement counter"), ChooseCardEffect(Corp, g.installedCards(Corp), RHAdvancePurpose(self, 1), "Tennin Institute: place 1 advancement counter on an installed card?", true))
            case _ => $
        }
    }

    object IainStirling extends RH("05028") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) if g.agendaPoints(Corp) > g.agendaPoints(Runner) => $(says(self, "gain 2 credits"), gain(Runner, 2))
            case _ => $
        }
    }

    object Silhouette extends RH("05030") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(HQ) if g.successfulRunsThisTurn.count(_ == HQ) == 1 && unrezzedInstalled.any => says(self, "expose 1 card") +: expose(unrezzedInstalled, "Silhouette: expose 1 card?", true, self)
            case _ => $
        }
    }

    object NasirMeidan extends RH("06017") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case ApproachIce(c) if !g.isRezzed(c) => $(SetTargetEffect(self, |(c)))
            case EncounterIce(c) if g.state(self).target.has(c) => $(RHNasirAction(self, c))
            case PassIce(_) | RunEnds(_, _, _) if g.state(self).target.any => $(SetTargetEffect(self, None))
            case _ => $
        }
        override def performRH(a : CardAction)(implicit g : Game) = a match {
            case RHNasirAction(self, c) =>
                g.state(self).target = None
                val n = g.rezCost(c)
                g.log(Runner, "loses", g.credits(Runner).credits, "and gains", n.credits, "(Nasir Meidan)")
                g.credits += Runner -> n
                NextAction
        }
    }

    object TheFoundry extends RH("06021") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Rezzed(c) if c.data.isIce && firstTime(self, "foundry") =>
                $(MarkUsedEffect(self, "foundry")) ++ g.deck(Corp).exists(_.title == c.title).$(OptionalEffect(Corp, "The Foundry: search R&D for another copy of the rezzed ice?", $(says(self, "search R&D for another copy"), RHFoundryAction(self, c)), $))
            case _ => $
        }
        override def performRH(a : CardAction)(implicit g : Game) = a match {
            case RHFoundryAction(self, c) =>
                g.deck(Corp).%(_.title == c.title).starting.foreach { x =>
                    g.log(Corp, "reveals", x, "from R&D and adds it to HQ")
                    g.state(x).seen = true
                    g.moveTo(x, HandZone(Corp))
                }
                g.push(ShuffleDeckEffect(Corp))
                NextAction
        }
    }

    object IndustrialGenomics extends RH("06105") {
        override def trashCostMod(self : Card, card : Card)(implicit g : Game) = g.discard(Corp).%(c => !g.state(c).seen).num
    }

    object ArgusSecurity extends RH("07001") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case AgendaStolen(_) => $(says(self, "make the Runner take 1 tag or suffer 2 meat damage"), ChooseOneEffect(Runner, "Argus Security: choose one", $(Alt("Take 1 tag", $(tag(1))), Alt("Suffer 2 meat damage", $(meat(2))))))
            case _ => $
        }
    }

    object GagarinDeepSpace extends RH("07002") {
        def remoteRoot(c : Card)(implicit g : Game) : Boolean = g.zone(c) match {
            case RootZone(s) => s.isRemote
            case HostedZone(h) => remoteRoot(h)
            case _ => false
        }
        override def accessFilter(self : Card, cards : $[Card])(implicit g : Game) = cards.%(c => !remoteRoot(c) || g.credits(Runner) >= 1)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Accessed(c) if remoteRoot(c) => $(MessageEffect(Runner, "pays 1 credit to access the card (Gagarin Deep Space)"), lose(Runner, 1))
            case _ => $
        }
    }

    object TitanTransnational extends RH("07003") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case AgendaScored(c) => $(says(self, "place 1 agenda counter on the scored agenda"), AddCounterEffect(c, "agenda", 1))
            case _ => $
        }
    }

    object EdwardKim extends RH("07028") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Accessed(c) if c.data.isOperation && !g.zone(c).is[DiscardZone] && firstTime(self, "edward") && g.run.exists(!_.cannotStealOrTrash) =>
                $(MarkUsedEffect(self, "edward"), MessageEffect(Runner, "trashes the accessed operation at no cost (Edward Kim)"), TrashEffect(c, Runner, true))
            case _ => $
        }
    }

    object MaxX extends RH("07029") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) => $(says(self, "trash the top 2 cards of the stack and draw 1 card"), TrashTopEffect(Runner, 2, true), draw(Runner, 1))
            case _ => $
        }
    }

    object ValenciaEstevez extends RH("07030") {
        override def onSetup(self : Card)(implicit g : Game) = $(RHValenciaAction(self))
        override def performRH(a : CardAction)(implicit g : Game) = a match {
            case RHValenciaAction(self) =>
                if (g.badPublicity == 0) {
                    g.badPublicity = 1
                    g.log(Corp, "starts the game with 1 bad publicity (Valencia Estevez)")
                }
                NextAction
        }
    }

    object JintekiBiotech extends RH("08012") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) if g.turn == 1 && g.state(self).memo == "" =>
                $(ChooseOneEffect(Corp, "Jinteki Biotech: choose the copy used this game", $("The Brewery", "The Tank", "The Greenhouse")./(k => Alt(k, $(SetMemoEffect(self, k))))))
            case _ => $
        }
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("flip this identity (" + (g.state(self).memo == "").?("no copy chosen").|(g.state(self).memo) + ")", click(3), $(RHBiotechFlipAction(self)), click = true, ok = g.counter(self, "flip") == 0 && g.state(self).memo != "", reason = "already flipped"))
        override def performRH(a : CardAction)(implicit g : Game) = a match {
            case RHBiotechFlipAction(self) =>
                val k = g.state(self).memo
                g.push(FlipAction(self, k))
                k match {
                    case "The Brewery" => g.push(net(2))
                    case "The Tank" =>
                        val l = g.discard(Corp)
                        l.foreach(c => g.moveTo(c, DeckZone(Corp)))
                        g.log(Corp, "shuffles Archives (" ~ l.num.cards ~ ") into R&D")
                        g.push(ShuffleDeckEffect(Corp))
                    case _ =>
                        val l = g.installedCards(Corp).%(c => g.isAdvanceable(c))
                        if (l.any)
                            g.push(ChooseCardEffect(Corp, l, RHAdvancePurpose(self, 4), "The Greenhouse: place 4 advancement counters on a card", false))
                }
                NextAction
        }
    }

    object HayleyKaplan extends RH("08025") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Installed(Runner, c) if firstTime(self, "hayley") =>
                val l = g.hand(Runner).%(x => x.data.kind == c.data.kind && g.canAffordInstall(x, 0) && g.rhModHosts(x).none && !g.rhInstallBlocked(x))
                $(MarkUsedEffect(self, "hayley")) ++ l.any.$(says(self, "install another card of the same type")) ++ l.any.$(ChooseCardEffect(Runner, l, InstallPayingPurpose(0, None, false), "Hayley Kaplan: install another card of the same type from the grip?", true))
            case _ => $
        }
    }

    object CyberneticsDivision extends RH("08050") {
        override def handSize(self : Card, f : Faction)(implicit g : Game) = -1
    }

    object ArmandGeistWalker extends RH("08063") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case RHTrashAbilityUsed(_) => $(says(self, "draw 1 card"), draw(Runner, 1))
            case _ => $
        }
    }

    object HaarpsichordStudios extends RH("08092") {
        override def protectsFromAccess(self : Card, target : Card)(implicit g : Game) = target.data.isAgenda && g.reg("stolen") > 0
    }

    object LaramyFisk extends RH("08104") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(s) if s.isCentral && g.successfulRunsThisTurn.%(_.isCentral).num == 1 && g.deck(Corp).any =>
                $(OptionalEffect(Runner, "Laramy Fisk: force the Corp to draw 1 card?", $(says(self, "force the Corp to draw 1 card"), draw(Corp, 1)), $))
            case _ => $
        }
    }

    object ChronosProtocolJinteki extends RH("08111") {
        override def damageChoice(self : Card, kind : String, n : Int)(implicit g : Game) = (kind == "net" && firstTime(self, "chronos-net")).?(|((Corp : Faction, 1))).|(None)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case DamageDone("net", _) if firstTime(self, "chronos-net") => $(MarkUsedEffect(self, "chronos-net"))
            case _ => $
        }
    }
}
