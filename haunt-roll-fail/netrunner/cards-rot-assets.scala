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
// Rotated (FFG era) assets, ported from jinteki.net src/clj/game/cards/assets.clj (see STATUS-rot-assets.md).
// Generic mechanics: rules-ext-rot-assets.scala (RotAssetRules, base class RA). Second half: cards-rot-assets-2.scala.
// ---------------------------------------------------------------------------------------------------------------------

object RotAssetCards {
    val all : $[CardImpl] = RotAssetCardsA.all ++ RotAssetCardsB.all
}

// card specific actions and purposes
case class RASecuritySubcontractPurpose(card : Card) extends CardPurpose
case class RARexAction(card : Card) extends CardAction
case class RAToshiAction(card : Card) extends CardAction
case class RAToshiPurpose(card : Card) extends CardPurpose
case class RAToshiAccessAction(card : Card, target : Card) extends CardAction
case class RAPsychicFieldAction(card : Card) extends CardAction
case class RATenmaPurpose(card : Card) extends CardPurpose
case class RAPlanBAction(card : Card) extends CardAction
case class RAPlanBPurpose(card : Card) extends CardPurpose
case class RAPlanBScoreAction(card : Card, agenda : Card) extends CardAction
case class RAITDeptPurpose(card : Card, n : Int) extends CardPurpose
case class RAConstellationAction(card : Card) extends CardAction
case class RAConstellationFromPurpose(card : Card) extends CardPurpose
case class RAConstellationToPurpose(card : Card, from : Card) extends CardPurpose
case class RAMarkYalePurpose(card : Card) extends CardPurpose
case class RATechStartupAction(card : Card) extends CardAction
case class RATechStartupPurpose(card : Card) extends CardPurpose
case class RAAlleleAction(card : Card, total : Int) extends CardAction
case class RAAllelePurpose(card : Card, total : Int) extends CardPurpose
case class RAAlleleArchivesPurpose(card : Card, hq : $[Card]) extends CardPurpose
case class RATeamSponsorshipAction(card : Card) extends CardAction
case class RATeamSponsorshipPurpose(card : Card) extends CardPurpose
case class RAWorldsPlazaPurpose(card : Card) extends CardPurpose
case class RALilyPurpose(card : Card) extends CardPurpose
case class RALilyTopAction(card : Card) extends CardAction
case class RAShannonDrawAction(card : Card) extends CardAction
case class RAShannonPurpose(card : Card) extends CardPurpose
case class RAShannonBottomAction(card : Card) extends CardAction
case class RAShannonArchivesPurpose(card : Card) extends CardPurpose
case class RAKalaLookAction(card : Card) extends CardAction
case class RAMuseumAction(card : Card) extends CardAction
case class RAAALPurpose(card : Card) extends CardPurpose
case class RALakshmiPurpose(card : Card) extends CardPurpose
case class RAMumbadConstructionPurpose(card : Card) extends CardPurpose
case class RAPADFactoryPurpose(card : Card) extends CardPurpose


object RotAssetCardsA {

    object ZaibatsuLoyalty extends RA("01071") {
        override def exposeOptions(self : Card, target : Card)(implicit g : Game) =
            if (!g.isRezzed(self))
                $(Alt("Rez Zaibatsu Loyalty (" + g.rezCost(self) + " credits)", $(RezEffect(self, false), RotExposeEffect(target)), g.credits(Corp) >= g.rezCost(self) && g.impl(self).canRez(self)))
            else
                $(Alt("Zaibatsu Loyalty: pay 1 credit to prevent the expose", $(LoseCreditsEffect(Corp, 1), RotExposePreventedEffect(target)), g.credits(Corp) >= 1),
                  Alt("Zaibatsu Loyalty: trash it to prevent the expose", $(TrashEffect(self, Corp, false), RotExposePreventedEffect(target))))
    }

    object SecuritySubcontract extends RA("01096") {
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("trash a rezzed piece of ice to gain 4 credits", click(), $(ChooseCardEffect(Corp, g.rezzedIce, RASecuritySubcontractPurpose(self), "Security Subcontract: choose a rezzed piece of ice to trash", false)), click = true, ok = g.rezzedIce.any, reason = "no rezzed ice"))
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(c => g.push(TrashEffect(c, Corp, false), gain(Corp, 4)))
            NextAction
        }
    }

    object EncryptionProtocol extends RA("02029") {
        override def trashCostMod(self : Card, card : Card)(implicit g : Game) = g.isInstalled(card).?(1).|(0)
    }

    object EdgeOfWorld extends RA("02053") {
        override def onAccess(self : Card)(implicit g : Game) = {
            val n = g.serverOf(self)./(s => g.ice(s).num).|(0)
            ambush(self, 3, "Edge of World: pay 3 credits to do " + n + " core damage?", $(core(n)))
        }
    }

    object PrivateContracts extends RA("02059") {
        override def initialCounters(self : Card)(implicit g : Game) = Map("credit" -> 14)
        override def triggers(self : Card, e : Event)(implicit g : Game) = trashOnEmpty(self, e, "credit")
        override def abilities(self : Card)(implicit g : Game) = $(Ability("take 2 credits", click(), $(TakeCreditsEffect(Corp, self, 2)), click = true))
    }

    object DedicatedServer extends RA("02072") {
        override def initialCounters(self : Card)(implicit g : Game) = Map("credit" -> 2)
        override def recurring(self : Card, kind : String, card : Card)(implicit g : Game) = kind == "rez" && card.data.isIce
        override def triggers(self : Card, e : Event)(implicit g : Game) = refill(self, e, 2)
    }

    object NetPolice extends RA("02075") {
        override def initialCounters(self : Card)(implicit g : Game) = Map("credit" -> g.link)
        override def recurring(self : Card, kind : String, card : Card)(implicit g : Game) = kind == "trace"
        override def triggers(self : Card, e : Event)(implicit g : Game) = refill(self, e, g.link)
    }

    object EveCampaign extends RA("02092") {
        override def initialCounters(self : Card)(implicit g : Game) = Map("credit" -> 16)
        override def triggers(self : Card, e : Event)(implicit g : Game) = campaign(self, e, 2)
    }

    object AlixT4LB07 extends RA("03008") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Installed(Corp, _) => $(AddCounterEffect(self, "power", 1))
            case _ => $
        }
        override def abilities(self : Card)(implicit g : Game) = {
            val n = g.counter(self, "power")
            $(Ability("gain " + (2 * n) + " credits (2 per power counter)", $(ClickCost(1), TrashSelfCost), $(gain(Corp, 2 * n)), click = true))
        }
    }

    object DirectorHaas extends RA("03010") {
        override def executive = true
        override def asAgendaPoints = 2
        override def clicksPerTurn(self : Card, f : Faction)(implicit g : Game) = f.isCorp.?(1).|(0)
    }

    object HaasArcologyAI extends RA("03011") {
        override def advanceable(self : Card)(implicit g : Game) = !g.isRezzed(self)
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("gain 2 clicks", $(ClickCost(1), CounterCost("advancement", 1)), $(GainClicksEffect(Corp, 2)), click = true, once = once("arcology")))
    }

    object ThomasHaas extends RA("03012") {
        override def advanceable(self : Card)(implicit g : Game) = true
        override def abilities(self : Card)(implicit g : Game) = {
            val n = g.advancement(self)
            $(Ability("gain " + (2 * n) + " credits (2 per advancement counter)", $(TrashSelfCost), $(gain(Corp, 2 * n))))
        }
    }

    object LevyUniversity extends RA("03024") {
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("search R&D for a piece of ice and add it to HQ", $(ClickCost(1), CreditCost(1)), $(ChooseCardEffect(Corp, sortedDeck(_.data.isIce), AddToHandPurpose(Corp, true), "Levy University: choose a piece of ice", true), ShuffleDeckEffect(Corp)), click = true))
    }

    object ServerDiagnostics extends RA("03025") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) => $(gain(Corp, 2))
            case Installed(Corp, c) if c.data.isIce => $(MessageEffect(Corp, "trashes Server Diagnostics (a piece of ice was installed)"), TrashEffect(self, Corp, false))
            case _ => $
        }
    }

    object JacksonHoward extends RA("04015") {
        override def abilities(self : Card)(implicit g : Game) = $(
            Ability("draw 2 cards", click(), $(draw(Corp, 2)), click = true),
            Ability("shuffle up to 3 cards from Archives into R&D", $(RemoveSelfCost), $(ShuffleFromArchivesEffect(3))))
    }

    object IsabelMcGuire extends RA("04050") {
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("add 1 of your installed cards to HQ", click(), $(ChooseCardEffect(Corp, g.installedCards(Corp), ReturnToHandPurpose, "Isabel McGuire: choose an installed card to add to HQ", false)), click = true))
    }

    object CitySurveillance extends RA("04055") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) => $(ChooseOneEffect(Runner, "City Surveillance: pay 1 credit or take 1 tag", $(
                Alt("Pay 1 credit", $(LoseCreditsEffect(Runner, 1)), g.credits(Runner) >= 1),
                Alt("Take 1 tag", $(tag(1))))))
            case _ => $
        }
    }

    object RexCampaign extends RA("04070") {
        override def initialCounters(self : Card)(implicit g : Game) = Map("power" -> 3)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) => $(RARexAction(self))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case RARexAction(self) =>
                if (g.isInstalled(self) && g.counter(self, "power") > 0) {
                    g.addCounter(self, "power", -1)
                    g.log("1 power counter removed from", self)
                    if (g.counter(self, "power") == 0) {
                        g.trashCard(self, Corp, false)
                        g.push(ChooseOneEffect(Corp, "Rex Campaign: choose one", $(
                            Alt("Remove 1 bad publicity", $(BadPubEffect(-1)), g.badPublicity > 0),
                            Alt("Gain 5 credits", $(gain(Corp, 5))))))
                    }
                }
                NextAction
        }
    }

    object Shock extends RA("04073") {
        override def accessInArchives = true
        override def onAccess(self : Card)(implicit g : Game) = $(MessageEffect(Corp, "uses Shock! to do 1 net damage"), net(1))
    }

    object ToshiyukiSakai extends RA("04092") {
        override def advanceable(self : Card)(implicit g : Game) = true
        override def onAccess(self : Card)(implicit g : Game) = g.isInstalled(self).$(RAToshiAction(self))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case RAToshiAction(self) =>
                val l = g.hand(Corp).%(c => c.data.isAgenda || c.data.isAsset)
                if (g.isInstalled(self) && l.any)
                    g.push(OptionalEffect(Corp, "Toshiyuki Sakai: swap it with an agenda or asset from HQ?", $(ChooseCardEffect(Corp, l, RAToshiPurpose(self), "Toshiyuki Sakai: choose an agenda or asset in HQ", false)), $))
                NextAction
            case RAToshiAccessAction(self, x) =>
                g.run.foreach { r =>
                    if (!r.ended && g.isInstalled(x))
                        r.toAccess :+= x
                }
                NextAction
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            val self = p.card
            (cards.single, g.serverOf(self)) match {
                case (Some(x), Some(s)) if g.isInstalled(self) && g.zone(x) == HandZone(Corp) =>
                    val n = g.advancement(self)
                    // the access of Toshiyuki Sakai ends: he is now in HQ
                    g.pending = g.pending./{
                        case AccessDecisionAction(c) if c == self => AccessDoneAction(self)
                        case a => a
                    }
                    g.moveTo(self, HandZone(Corp))
                    g.moveTo(x, RootZone(s))
                    g.state(x).installedTurn = g.turn
                    g.state(x).rezzed = false
                    if (n > 0)
                        g.addCounter(x, "advancement", n)
                    g.log(Corp, "swaps Toshiyuki Sakai with a card from HQ, keeping", n.hl, "advancement counters")
                    g.push(OptionalEffect(Runner, "Toshiyuki Sakai: access the newly installed card?", $(RAToshiAccessAction(self, x)), $))
                case _ =>
            }
            NextAction
        }
    }

    object BroadcastSquare extends RA("04112")       // the bad publicity trace is in RotAssetRules.performEffects

    object ChairmanHiro extends RA("05008") {
        override def executive = true
        override def asAgendaPoints = 2
        override def handSize(self : Card, f : Faction)(implicit g : Game) = f.isRunner.?(-2).|(0)
    }

    object MentalHealthClinic extends RA("05009") {
        override def handSize(self : Card, f : Faction)(implicit g : Game) = f.isRunner.?(1).|(0)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) => $(gain(Corp, 1))
            case _ => $
        }
    }

    object PsychicField extends RA("05010") {
        def psi(self : Card)(implicit g : Game) : $[ForcedAction] = g.isInstalled(self).$(PsiEffect($, $(RAPsychicFieldAction(self))))
        override def onAccess(self : Card)(implicit g : Game) = psi(self)
        override def onExpose(self : Card)(implicit g : Game) = psi(self)
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case RAPsychicFieldAction(self) =>
                val n = g.hand(Runner).num
                g.log(Corp, "uses Psychic Field to do", n.hl, "net damage")
                g.push(net(n))
                NextAction
        }
    }

    object ShiKyu extends RA("05011") {
        override def accessInArchives = true
        override def asAgendaPoints = -1
        override def onAccess(self : Card)(implicit g : Game) =
            (g.zone(self) != DeckZone(Corp) && g.credits(Corp) > 0).$(ChooseNumberEffect(Corp, self, 0, g.credits(Corp), "shikyu", "Shi.Kyu: spend how many credits?"))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case NumberPickedAction(self, n, "shikyu") =>
                if (n > 0)
                    g.push(LoseCreditsEffect(Corp, n), ChooseOneEffect(Runner, "Shi.Kyu: choose one", $(
                        Alt("Suffer " + n + " net damage", $(net(n))),
                        Alt("Add Shi.Kyu to your score area as an agenda worth -1 point", $(RotAsAgendaEffect(self, Runner))))))
                NextAction
        }
    }

    object TenmaLine extends RA("05012") {
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("swap 2 installed pieces of ice", click(), $(ChooseCardsEffect(Corp, g.allIce, 2, 2, RATenmaPurpose(self), "Tenma Line: choose 2 pieces of ice to swap")), click = true, ok = g.allIce.num >= 2, reason = "needs 2 pieces of ice"))
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards match {
                case $(a, b) => g.push(SwapIceEffect(a, b))
                case _ =>
            }
            NextAction
        }
    }

    object PlanB extends RA("05023") {
        override def advanceable(self : Card)(implicit g : Game) = true
        override def onAccess(self : Card)(implicit g : Game) = (g.isInstalled(self) && g.advancement(self) > 0).$(RAPlanBAction(self))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case RAPlanBAction(self) =>
                val n = g.advancement(self)
                val l = g.hand(Corp).%(c => c.data.isAgenda && g.advancementRequirement(c) <= n)
                if (l.any)
                    g.push(OptionalEffect(Corp, "Plan B: reveal and score an agenda from HQ?", $(ChooseCardEffect(Corp, l, RAPlanBPurpose(self), "Plan B: choose an agenda to score", false)), $))
                NextAction
            case RAPlanBScoreAction(self, c) =>
                if (g.zone(c) == HandZone(Corp))
                    g.scoreAgenda(c)
                NextAction
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(c => g.push(RevealEffect(Corp, c), RAPlanBScoreAction(p.card, c)))
            NextAction
        }
    }

    object PrimaryTransmissionDish extends RA("06006") {
        override def initialCounters(self : Card)(implicit g : Game) = Map("credit" -> 3)
        override def recurring(self : Card, kind : String, card : Card)(implicit g : Game) = kind == "trace"
        override def triggers(self : Card, e : Event)(implicit g : Game) = refill(self, e, 3)
    }

    object TheRoot extends RA("06008") {
        override def initialCounters(self : Card)(implicit g : Game) = Map("credit" -> 3)
        override def recurring(self : Card, kind : String, card : Card)(implicit g : Game) = kind == "rez" || kind == "install" || kind == "advance"
        override def triggers(self : Card, e : Event)(implicit g : Game) = refill(self, e, 3)
    }

    object SealedVault extends RA("06029") {
        override def abilities(self : Card)(implicit g : Game) = {
            val n = g.counter(self, "credit")
            $(
                Ability("move credits from your credit pool to Sealed Vault", c(1), $(ChooseNumberEffect(Corp, self, 0, (g.credits(Corp) - 1).atLeast(0), "vault-store", "Sealed Vault: store how many credits?")), ok = g.credits(Corp) >= 2, reason = "not enough credits"),
                Ability("take any number of hosted credits", click(), $(ChooseNumberEffect(Corp, self, 0, n, "vault-take", "Sealed Vault: take how many credits?")), click = true, ok = n > 0, reason = "no hosted credits"),
                Ability("take any number of hosted credits", $(TrashSelfCost), $(ChooseNumberEffect(Corp, self, 0, n, "vault-trash", "Sealed Vault: take how many credits?")), ok = n > 0, reason = "no hosted credits"))
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case NumberPickedAction(self, n, "vault-store") =>
                val m = n.atMost(g.credits(Corp))
                if (m > 0) {
                    g.credits += Corp -> (g.credits(Corp) - m)
                    g.addCounter(self, "credit", m)
                    g.log(Corp, "stores", m.credits, "on", self)
                }
                NextAction
            case NumberPickedAction(self, n, "vault-take") =>
                g.push(TakeCreditsEffect(Corp, self, n))
                NextAction
            case NumberPickedAction(self, n, "vault-trash") =>
                g.push(gain(Corp, n))
                NextAction
        }
    }

    object ElizasToybox extends RA("06042") {
        override def abilities(self : Card)(implicit g : Game) = {
            val l = g.installedCards(Corp).%(c => !g.isRezzed(c) && !c.data.isAgenda)
            $(Ability("rez a card, ignoring all costs", click(3), $(ChooseCardEffect(Corp, l, RezPurpose(true), "Elizas Toybox: choose a card to rez", false)), click = true, ok = l.any, reason = "nothing to rez"))
        }
    }

    object TheNewsNowHour extends RA("06045")       // current events cannot be played: RotAssetRules.rotRestrict

    object ShatteredRemains extends RA("06050") {
        override def advanceable(self : Card)(implicit g : Game) = true
        override def onAccess(self : Card)(implicit g : Game) = {
            val n = g.advancement(self)
            val l = g.runnerInstalledOf("Hardware")
            (n > 0).??(ambush(self, 1, "Shattered Remains: pay 1 credit to trash " + n + " pieces of hardware?", $(ChooseCardsEffect(Corp, l, n.atMost(l.num), n, TrashPurpose(Corp), "Shattered Remains: choose hardware to trash"))))
        }
    }

    object DocklandsCrackdown extends RA("06072") {
        override def abilities(self : Card)(implicit g : Game) = $(Ability("place 1 power counter", click(2), $(AddCounterEffect(self, "power", 1)), click = true))
        override def installCostMod(self : Card, card : Card)(implicit g : Game) = (g.owner(card).isRunner && g.reg("rot-runner-installs") == 0).?(g.counter(self, "power")).|(0)
    }

    object ITDepartment extends RA("06103") {
        override def abilities(self : Card)(implicit g : Game) = {
            val n = g.counter(self, "power")
            $(
                Ability("place 1 power counter", click(), $(AddCounterEffect(self, "power", 1)), click = true),
                Ability("give a rezzed piece of ice +" + n + " strength for the remainder of the turn", $(CounterCost("power", 1)), $(ChooseCardEffect(Corp, g.rezzedIce, RAITDeptPurpose(self, n), "IT Department: choose a rezzed piece of ice", false)), ok = g.rezzedIce.any, reason = "no rezzed ice"))
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case RAITDeptPurpose(_, n) =>
                cards.single.foreach(c => g.push(PumpIceEffect(c, n, TurnDuration)))
                NextAction
        }
    }

    object ConstellationProtocol extends RA("07008") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) if g.allIce.exists(i => g.advancement(i) > 0) => $(RAConstellationAction(self))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case RAConstellationAction(self) =>
                val from = g.allIce.%(i => g.advancement(i) > 0)
                if (from.any && g.allIce.exists(i => g.isAdvanceable(i)))
                    g.push(OptionalEffect(Corp, "Constellation Protocol: move an advancement counter between 2 pieces of ice?", $(ChooseCardEffect(Corp, from, RAConstellationFromPurpose(self), "Constellation Protocol: choose a piece of ice to move a counter from", false)), $))
                NextAction
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case RAConstellationFromPurpose(self) =>
                cards.single.foreach { from =>
                    val to = g.allIce.%(i => i != from && g.isAdvanceable(i))
                    if (to.any)
                        g.push(ChooseCardEffect(Corp, to, RAConstellationToPurpose(self, from), "Constellation Protocol: choose a piece of ice that can be advanced", false))
                }
                NextAction
            case RAConstellationToPurpose(self, from) =>
                cards.single.foreach(to => g.push(MoveAdvancementEffect(from, to, 1)))
                NextAction
        }
    }

    object MarkYale extends RA("07009") {
        def withCounters(implicit g : Game) = g.scored(Corp).%(c => g.counter(c, "agenda") > 0)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case AgendaCounterSpent(_, n) if n > 0 => $(MessageEffect(Corp, "gains " + n + " credit" + (n != 1).??("s") + " for spending agenda counters (Mark Yale)"), gain(Corp, n))
            case _ => $
        }
        override def abilities(self : Card)(implicit g : Game) = $(
            Ability("gain 2 credits", $(TrashSelfCost), $(gain(Corp, 2))),
            Ability("spend an agenda counter to gain 2 credits", $, $(ChooseCardEffect(Corp, withCounters, RAMarkYalePurpose(self), "Mark Yale: choose an agenda to remove an agenda counter from", false)), ok = withCounters.any, reason = "no agenda counters"))
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(c => g.push(AddCounterEffect(c, "agenda", -1), gain(Corp, 2)))
            NextAction
        }
    }

    object SpaceCamp extends RA("07010") {
        override def accessInArchives = true
        override def onAccess(self : Card)(implicit g : Game) = {
            val l = g.installedCards(Corp).%(c => g.isAdvanceable(c))
            l.any.$(OptionalEffect(Corp, "Space Camp: place 1 advancement counter on a card that can be advanced?", $(ChooseCardEffect(Corp, l, AdvancePurpose(1), "Space Camp: choose a card", false)), $))
        }
    }

    object TheBoard extends RA("07011") {       // -1 point per Runner agenda: RotAssetRules.extImpl
        override def executive = true
        override def asAgendaPoints = 2
    }

    object BrainTapingWarehouse extends RA("08010") {
        override def rezCostMod(self : Card, card : Card)(implicit g : Game) = (card.data.isIce && g.has(card, "Bioroid")).?(-g.clicks(Runner)).|(0)
    }

    object CapitalInvestors extends RA("08018") {
        override def abilities(self : Card)(implicit g : Game) = $(Ability("gain 2 credits", click(), $(gain(Corp, 2)), click = true))
    }

    object TechStartup extends RA("08020") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) if g.deck(Corp).exists(_.data.isAsset) =>
                $(OptionalEffect(Corp, "Tech Startup: trash it to search R&D for an asset and install it?", $(TrashEffect(self, Corp, false), RATechStartupAction(self)), $))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case RATechStartupAction(self) =>
                val l = sortedDeck(_.data.isAsset)
                if (l.any)
                    g.push(ChooseCardEffect(Corp, l, RATechStartupPurpose(self), "Tech Startup: choose an asset to install", false), ShuffleDeckEffect(Corp))
                NextAction
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(c => g.push(RevealEffect(Corp, c), RotChooseInstallServerEffect(c, false)))
            NextAction
        }
    }

    object Blacklist extends RA("08036")       // cards cannot leave the heap: RotAssetRules

    object StudentLoans extends RA("08038") {
        override def playCostMod(self : Card, card : Card)(implicit g : Game) = (card.data.isEvent && g.discard(Runner).exists(_.title == card.title)).?(2).|(0)
    }

    object TestGround extends RA("08071") {
        override def advanceable(self : Card)(implicit g : Game) = true
        override def abilities(self : Card)(implicit g : Game) = {
            val n = g.advancement(self)
            val l = g.installedCards(Corp).%(c => c != self && g.isRezzed(c) && !c.data.isAgenda)
            $(Ability("derez " + n + " cards", $(TrashSelfCost), $(ChooseCardsEffect(Corp, l, n.atMost(l.num), n, DerezPurpose, "Test Ground: choose cards to derez")), ok = n > 0 && l.any, reason = "nothing to derez"))
        }
    }

    object AlleleRepression extends RA("08073") {
        override def advanceable(self : Card)(implicit g : Game) = true
        override def abilities(self : Card)(implicit g : Game) = {
            val n = g.advancement(self).atMost(g.hand(Corp).num).atMost(g.discard(Corp).num)
            $(Ability("swap " + n + " cards in HQ with cards in Archives", $(TrashSelfCost), $(RAAlleleAction(self, g.advancement(self))), ok = n > 0, reason = "nothing to swap"))
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case RAAlleleAction(self, total) =>
                val n = total.atMost(g.hand(Corp).num).atMost(g.discard(Corp).num)
                if (n > 0)
                    g.push(ChooseCardsEffect(Corp, g.hand(Corp), n, n, RAAllelePurpose(self, n), "Allele Repression: choose " + n + " cards in HQ"))
                NextAction
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case RAAllelePurpose(self, n) =>
                val l = g.discard(Corp)
                if (cards.any && l.any)
                    g.push(ChooseCardsEffect(Corp, l, cards.num.atMost(l.num), cards.num.atMost(l.num), RAAlleleArchivesPurpose(self, cards), "Allele Repression: choose " + cards.num + " cards in Archives"))
                NextAction
            case RAAlleleArchivesPurpose(self, hq) =>
                val pairs = hq.zip(cards)
                pairs.foreach { case (h, a) =>
                    g.toArchives(h, false)
                    g.moveTo(a, HandZone(Corp))
                }
                g.log(Corp, "swaps", pairs.num.cards, "in HQ with", pairs.num.cards, "in Archives")
                NextAction
        }
    }

    object Expose extends RA("08075") {
        override def advanceable(self : Card)(implicit g : Game) = true
        override def abilities(self : Card)(implicit g : Game) = {
            val n = g.advancement(self)
            $(Ability("remove " + n + " bad publicity", $(TrashSelfCost), $(BadPubEffect(-n.atMost(g.badPublicity))), ok = n > 0 && g.badPublicity > 0, reason = "nothing to remove"))
        }
    }

    object RonaldFive extends RA("08088") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Trashed(c, Runner, _) if g.owner(c).isCorp && g.clicks(Runner) > 0 => $(MessageEffect(Corp, "uses Ronald Five"), LoseClicksEffect(Runner, 1))
            case _ => $
        }
        override def onTrash(self : Card, by : Faction)(implicit g : Game) = (by.isRunner && g.lastTrashedRezzed && g.clicks(Runner) > 0).$(LoseClicksEffect(Runner, 1))
    }

    object EarlyPremiere extends RA("08095") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) =>
                val l = g.installedCards(Corp).%(c => g.zone(c).is[RootZone] && g.isAdvanceable(c))
                (g.credits(Corp) >= 1 && l.any).$(OptionalEffect(Corp, "Early Premiere: pay 1 credit to place 1 advancement counter on a card in the root of a server?", $(LoseCreditsEffect(Corp, 1), ChooseCardEffect(Corp, l, AdvancePurpose(1), "Early Premiere: choose a card", false)), $))
            case _ => $
        }
    }

    object CyberneticsCourt extends RA("08109") {
        override def handSize(self : Card, f : Faction)(implicit g : Game) = f.isCorp.?(4).|(0)
    }

    object TeamSponsorship extends RA("08110") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case AgendaScored(_) => $(RATeamSponsorshipAction(self))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case RATeamSponsorshipAction(self) =>
                val l = (g.hand(Corp) ++ g.discard(Corp)).%(corpInstallableNow)
                if (l.any)
                    g.push(ChooseCardEffect(Corp, l, RATeamSponsorshipPurpose(self), "Team Sponsorship: install a card from HQ or Archives ignoring the install cost?", true))
                NextAction
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(c => g.push(RotChooseInstallServerEffect(c, true)))
            NextAction
        }
    }

    object GeneticsPavilion extends RA("08113")       // draw limit: RotAssetRules

    object FranchiseCity extends RA("08114") {
        override def asAgendaPoints = 1
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Accessed(c) if c.data.isAgenda => $(RotAsAgendaEffect(self, Corp))
            case _ => $
        }
    }

    object WorldsPlaza extends RA("08116") {
        override def abilities(self : Card)(implicit g : Game) = {
            val l = g.hand(Corp).%(_.data.isAsset)
            $(Ability("install an asset from HQ on Worlds Plaza and rez it paying 2 credits less", click(), $(ChooseCardEffect(Corp, l, RAWorldsPlazaPurpose(self), "Worlds Plaza: choose an asset to install", false)), click = true, ok = l.any && g.hosted(self).num < 3, reason = "cannot host another asset"))
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(c => g.push(RotCorpHostInstallEffect(c, p.card), RezDiscountEffect(c, 2)))
            NextAction
        }
    }

    object LilyLockwell extends RA("09008") {
        override def onRez(self : Card)(implicit g : Game) = $(draw(Corp, 3))
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("remove 1 tag: search R&D for an operation and add it to the top of R&D", click(), $(RemoveTagsEffect(1), ChooseCardEffect(Corp, sortedDeck(_.data.isOperation), RALilyPurpose(self), "Lily Lockwell: choose an operation", true), ShuffleDeckEffect(Corp), RALilyTopAction(self)), click = true, ok = g.tagged, reason = "the Runner is not tagged"))
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach { c =>
                g.state(p.card).target = |(c)
                g.push(RevealEffect(Corp, c))
            }
            NextAction
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case RALilyTopAction(self) =>
                g.state(self).target.foreach { c =>
                    if (g.zone(c) == DeckZone(Corp)) {
                        g.moveTo(c, DeckZone(Corp), true)
                        g.log(Corp, "adds", c, "to the top of R&D")
                    }
                }
                g.state(self).target = None
                NextAction
        }
    }

    object NewsTeam extends RA("09009") {
        override def accessInArchives = true
        override def asAgendaPoints = -1
        override def onAccess(self : Card)(implicit g : Game) = $(ChooseOneEffect(Runner, "News Team: choose one", $(
            Alt("Take 2 tags", $(tag(2))),
            Alt("Add News Team to your score area as an agenda worth -1 point", $(RotAsAgendaEffect(self, Runner))))))
    }

    object ShannonClaire extends RA("09010") {
        override def abilities(self : Card)(implicit g : Game) = {
            val ra = g.discard(Corp).%(_.data.isAgenda)
            $(
                Ability("draw 1 card from the bottom of R&D", click(), $(RAShannonDrawAction(self)), click = true, ok = g.deck(Corp).any, reason = "R&D is empty"),
                Ability("search R&D for an agenda and add it to the bottom of R&D", $(TrashSelfCost), $(ChooseCardEffect(Corp, sortedDeck(_.data.isAgenda), RAShannonPurpose(self), "Shannon Claire: choose an agenda in R&D", true), ShuffleDeckEffect(Corp), RAShannonBottomAction(self))),
                Ability("search Archives for an agenda and add it to the bottom of R&D", $(TrashSelfCost), $(ChooseCardEffect(Corp, ra, RAShannonArchivesPurpose(self), "Shannon Claire: choose an agenda in Archives", true)), ok = ra.any, reason = "no agenda in Archives"))
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case RAShannonDrawAction(self) =>
                g.deck(Corp).lastOption.foreach { c =>
                    g.moveTo(c, HandZone(Corp))
                    g.log(Corp, "draws 1 card from the bottom of R&D")
                }
                NextAction
            case RAShannonBottomAction(self) =>
                g.state(self).target.foreach { c =>
                    if (g.zone(c) == DeckZone(Corp)) {
                        g.moveTo(c, DeckZone(Corp))
                        g.log(Corp, "adds", c, "to the bottom of R&D")
                    }
                }
                g.state(self).target = None
                NextAction
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case RAShannonPurpose(self) =>
                cards.single.foreach { c =>
                    g.state(self).target = |(c)
                    g.push(RevealEffect(Corp, c))
                }
                NextAction
            case RAShannonArchivesPurpose(self) =>
                cards.single.foreach(c => g.push(RevealEffect(Corp, c), MoveEffect(c, DeckZone(Corp), false), MessageEffect(Corp, "adds the agenda to the bottom of R&D")))
                NextAction
        }
    }

    object VictoriaJenkins extends RA("09011") {
        override def executive = true
        override def asAgendaPoints = 2
        override def clicksPerTurn(self : Card, f : Faction)(implicit g : Game) = f.isRunner.?(-1).|(0)
    }

    object RealityThreedee extends RA("09012") {
        override def onRez(self : Card)(implicit g : Game) = $(BadPubEffect(1))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) => $(gain(Corp, g.tagged.?(2).|(1)))
            case _ => $
        }
    }

    object LaunchCampaign extends RA("09027") {
        override def initialCounters(self : Card)(implicit g : Game) = Map("credit" -> 6)
        override def triggers(self : Card, e : Event)(implicit g : Game) = campaign(self, e, 2)
    }

    object KalaGhodaRealTV extends RA("10015") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) if g.deck(Runner).any => $(OptionalEffect(Corp, "Kala Ghoda Real TV: look at the top card of the stack?", $(RAKalaLookAction(self)), $))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case RAKalaLookAction(self) =>
                g.deck(Runner).starting.foreach(c => g.push(LookAtEffect(Corp, $(c), "Kala Ghoda Real TV: the top card of the stack")))
                NextAction
        }
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("the Runner trashes the top card of the stack", $(TrashSelfCost), $(MillEffect(Runner, 1)), ok = g.deck(Runner).any, reason = "the stack is empty"))
    }

    object MumbaTemple extends RA("10018") {
        override def initialCounters(self : Card)(implicit g : Game) = Map("credit" -> 2)
        override def recurring(self : Card, kind : String, card : Card)(implicit g : Game) = kind == "rez"
        override def triggers(self : Card, e : Event)(implicit g : Game) = refill(self, e, 2)
    }

    object MuseumOfHistory extends RA("10019") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) if g.discard(Corp).any => $(RAMuseumAction(self))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case RAMuseumAction(self) =>
                if (g.discard(Corp).any)
                    g.push(ChooseCardsEffect(Corp, g.discard(Corp), 0, 1, ShuffleIntoDeckPurpose(Corp), "Museum of History: choose up to 1 card in Archives to shuffle into R&D"))
                NextAction
        }
    }

    object AdvancedAssemblyLines extends RA("10027") {
        override def onRez(self : Card)(implicit g : Game) = $(gain(Corp, 3))
        override def abilities(self : Card)(implicit g : Game) = {
            val l = g.hand(Corp).%(c => corpInstallableNow(c) && !c.data.isAgenda)
            $(Ability("install a non-agenda card from HQ", $(TrashSelfCost), $(ChooseCardEffect(Corp, l, RAAALPurpose(self), "Advanced Assembly Lines: choose a card to install", false)), ok = l.any && g.run.none, reason = "not during a run"))
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(c => g.push(RotChooseInstallServerEffect(c, false)))
            NextAction
        }
    }

    object LakshmiSmartfabrics extends RA("10028") {
        def protectedCodes(self : Card)(implicit g : Game) : $[String] = g.state(self).memo.split(':').toList match {
            case t :: l if t == g.turn.toString + "/" + g.active.name => l./~(_.split(',').toList).%(_ != "")
            case _ => $
        }
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Rezzed(_) => $(AddCounterEffect(self, "power", 1))
            case _ => $
        }
        override def abilities(self : Card)(implicit g : Game) = {
            val l = g.hand(Corp).%(c => c.data.isAgenda && c.data.agendaPoints.|(0) > 0 && c.data.agendaPoints.|(0) <= g.counter(self, "power"))
            $(Ability("reveal an agenda from HQ: the Runner cannot steal its copies this turn", $, $(ChooseCardEffect(Corp, l, RALakshmiPurpose(self), "Lakshmi Smartfabrics: choose an agenda to reveal", false)), ok = l.any, reason = "no agenda worth the hosted power counters"))
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            val self = p.card
            cards.single.foreach { c =>
                val x = c.data.agendaPoints.|(0)
                g.addCounter(self, "power", -x)
                g.state(self).memo = g.turn.toString + "/" + g.active.name + ":" + (protectedCodes(self) :+ c.code).mkString(",")
                g.push(RevealEffect(Corp, c), MessageEffect(Corp, "prevents the Runner from stealing copies of the revealed agenda this turn"))
            }
            NextAction
        }
        override def protectsFromAccess(self : Card, target : Card)(implicit g : Game) = target.data.isAgenda && protectedCodes(self).exists(code => Cards(code).title == target.title)
    }

    object PalanaAgroplex extends RA("10031") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) => $(draw(Corp, 1), draw(Runner, 1))
            case _ => $
        }
    }

    object MumbadConstructionCo extends RA("10036") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) => $(PlaceAdvancementEffect(self, 1))
            case _ => $
        }
        override def abilities(self : Card)(implicit g : Game) = {
            val l = g.installedCards(Corp).%(c => c != self && g.isRezzed(c))
            $(Ability("move 1 advancement counter to a faceup card", c(2), $(ChooseCardEffect(Corp, l, RAMumbadConstructionPurpose(self), "Mumbad Construction Co.: choose a faceup card", false)), ok = g.advancement(self) > 0 && l.any, reason = "no advancement counter"))
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(c => g.push(MoveAdvancementEffect(p.card, c, 1)))
            NextAction
        }
    }

    object PADFactory extends RA("10038") {
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("place 1 advancement counter on an installed card (it cannot be scored this turn)", click(), $(ChooseCardEffect(Corp, g.installedCards(Corp), RAPADFactoryPurpose(self), "PAD Factory: choose an installed card", false)), click = true))
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(c => g.push(PlaceAdvancementEffect(c, 1), MarkUsedEffect(c, "no-score")))
            NextAction
        }
    }

    val all : $[CardImpl] = $(
        ZaibatsuLoyalty, SecuritySubcontract, EncryptionProtocol, EdgeOfWorld, PrivateContracts, DedicatedServer, NetPolice, EveCampaign,
        AlixT4LB07, DirectorHaas, HaasArcologyAI, ThomasHaas, LevyUniversity, ServerDiagnostics, JacksonHoward, IsabelMcGuire,
        CitySurveillance, RexCampaign, Shock, ToshiyukiSakai, BroadcastSquare, ChairmanHiro, MentalHealthClinic, PsychicField, ShiKyu,
        TenmaLine, PlanB, PrimaryTransmissionDish, TheRoot, SealedVault, ElizasToybox, TheNewsNowHour, ShatteredRemains,
        DocklandsCrackdown, ITDepartment, ConstellationProtocol, MarkYale, SpaceCamp, TheBoard, BrainTapingWarehouse, CapitalInvestors,
        TechStartup, Blacklist, StudentLoans, TestGround, AlleleRepression, Expose, RonaldFive, EarlyPremiere, CyberneticsCourt,
        TeamSponsorship, GeneticsPavilion, FranchiseCity, WorldsPlaza, LilyLockwell, NewsTeam, ShannonClaire, VictoriaJenkins,
        RealityThreedee, LaunchCampaign, KalaGhodaRealTV, MumbaTemple, MuseumOfHistory, AdvancedAssemblyLines, LakshmiSmartfabrics,
        PalanaAgroplex, MumbadConstructionCo, PADFactory,
    )
}
