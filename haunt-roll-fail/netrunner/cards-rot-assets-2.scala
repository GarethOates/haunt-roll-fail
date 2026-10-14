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
// Rotated (FFG era) assets, second half (codes 10049 and up). See cards-rot-assets.scala.
// ---------------------------------------------------------------------------------------------------------------------

// card specific actions and purposes
case class RACloneSuffrageAction(card : Card) extends CardAction
case class RAPoliticalDealingsAction(card : Card, agenda : Card) extends CardAction
case class RASensieAction(card : Card) extends CardAction
case class RAMumbadCityHallPurpose(card : Card) extends CardPurpose
case class RARamanAction(card : Card, drawn : $[Card]) extends CardAction
case class RARamanPurpose(card : Card) extends CardPurpose
case class RARamanArchivesPurpose(card : Card, drawn : Card) extends CardPurpose
case class RARecStudioPurpose(card : Card) extends CardPurpose
case class RAIbrahimAction(card : Card, kind : String) extends CardAction
case class RACIFundAction(card : Card) extends CardAction
case class RACIFundBankAction(card : Card) extends CardAction
case class RAAlexaAction(card : Card) extends CardAction
case class RAAlexaPurpose(card : Card) extends CardPurpose
case class RAQuarantinePurpose(card : Card) extends CardPurpose
case class RAQuarantineRezAction(card : Card, discount : Int, remaining : Int) extends CardAction
case class RAQuarantineRezPurpose(card : Card, discount : Int, remaining : Int) extends CardPurpose
case class RAClydeAction(card : Card) extends CardAction
case class RABioroidWorkCrewPurpose(card : Card) extends CardPurpose
case class RAWhampoaAction(card : Card) extends CardAction
case class RAOpenForumAction(card : Card) extends CardAction
case class RAOpenForumTopAction(card : Card) extends CardAction
case class RAInezAction(card : Card, scored : Card) extends CardAction
case class RAInezPurpose(card : Card, scored : Card) extends CardPurpose
case class RAInez2Action(card : Card, stolen : Card) extends CardAction
case class RAInez2SwapAction(card : Card, stolen : Card) extends CardAction
case class RAInez2Purpose(card : Card, stolen : Card) extends CardPurpose
case class RATodachineAction(card : Card, server : Server) extends CardAction
case class RATrojanAction(card : Card) extends CardAction
case class RAUrbanRenewalAction(card : Card) extends CardAction
case class RAPortalAction(card : Card) extends CardAction
case class RAMaliaPurpose(card : Card) extends CardPurpose
case class RAAmaniAction(card : Card) extends CardAction
case class RAIsobelAction(card : Card) extends CardAction
case class RAIsobelPurpose(card : Card) extends CardPurpose
case class RADrudgeWorkPurpose(card : Card) extends CardPurpose
case class RABootCampAction(card : Card) extends CardAction
case class RABootCampPurpose(card : Card) extends CardPurpose


object RotAssetCardsB {

    object CloneSuffrageMovement extends RA("10049") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) if unprotectedServer(self) && g.discard(Corp).exists(_.data.isOperation) => $(RACloneSuffrageAction(self))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case RACloneSuffrageAction(self) =>
                val l = g.discard(Corp).%(_.data.isOperation)
                if (l.any && unprotectedServer(self))
                    g.push(ChooseCardEffect(Corp, l, AddToHandPurpose(Corp, true), "Clone Suffrage Movement: add an operation from Archives to HQ?", true))
                NextAction
        }
    }

    object BioEthicsAssociation extends RA("10050") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) if unprotectedServer(self) => $(MessageEffect(Corp, "uses Bio-Ethics Association to do 1 net damage"), net(1))
            case _ => $
        }
    }

    object PoliticalDealings extends RA("10051") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case CardsDrawn(Corp, l) => l.%(_.data.isAgenda)./(c => RAPoliticalDealingsAction(self, c))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case RAPoliticalDealingsAction(self, c) =>
                if (g.zone(c) == HandZone(Corp))
                    g.push(OptionalEffect(Corp, "Political Dealings: reveal and install the agenda you drew?", $(RevealEffect(Corp, c), RotChooseInstallServerEffect(c, false)), $))
                NextAction
        }
    }

    object SensieActorsUnion extends RA("10053") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) if unprotectedServer(self) =>
                $(OptionalEffect(Corp, "Sensie Actors Union: draw 3 cards, then add 1 card from HQ to the bottom of R&D?", $(draw(Corp, 3), RASensieAction(self)), $))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case RASensieAction(self) =>
                if (g.hand(Corp).any)
                    g.push(ChooseCardEffect(Corp, g.hand(Corp), BottomOfDeckPurpose(Corp), "Sensie Actors Union: choose a card in HQ to add to the bottom of R&D", false))
                NextAction
        }
    }

    object CommercialBankersGroup extends RA("10054") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) if unprotectedServer(self) => $(gain(Corp, 3))
            case _ => $
        }
    }

    object MumbadCityHall extends RA("10055") {
        override def abilities(self : Card)(implicit g : Game) = {
            val l = sortedDeck(c => g.has(c, "Alliance") && (!c.data.isOperation || g.playCost(c) <= g.credits(Corp)))
            $(Ability("search R&D for an alliance card to play or install", click(), $(ChooseCardEffect(Corp, l, RAMumbadCityHallPurpose(self), "Mumbad City Hall: choose an alliance card", true), ShuffleDeckEffect(Corp)), click = true))
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach { c =>
                if (c.data.isOperation)
                    g.push(RevealEffect(Corp, c), PlayCardEffect(Corp, c, true, false))
                else
                    g.push(RevealEffect(Corp, c), RotChooseInstallServerEffect(c, false))
            }
            NextAction
        }
    }

    object JeevesModelBioroids extends RA("10067") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case ClicksSpent(Corp, n, cause) if g.fxClicksByCause.get((Corp, cause)).|(0) + n >= 3 && !g.usedThisTurn(self, "jeeves") =>
                $(MarkUsedEffect(self, "jeeves"), MessageEffect(Corp, "uses Jeeves Model Bioroids"), GainClicksEffect(Corp, 1))
            case _ => $
        }
    }

    object RamanRai extends RA("10068") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case CardsDrawn(Corp, l) if g.clicks(Corp) > 0 && !g.usedThisTurn(self, "raman") && l.exists(c => g.discard(Corp).exists(_.data.kind == c.data.kind)) => $(RARamanAction(self, l))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case RARamanAction(self, l) =>
                val drawn = l.%(c => g.zone(c) == HandZone(Corp) && g.discard(Corp).exists(_.data.kind == c.data.kind))
                if (drawn.any && g.clicks(Corp) > 0 && !g.usedThisTurn(self, "raman"))
                    g.push(OptionalEffect(Corp, "Raman Rai: lose 1 click to swap a card you drew with a card of the same type in Archives?", $(MarkUsedEffect(self, "raman"), LoseClicksEffect(Corp, 1), ChooseCardEffect(Corp, drawn, RARamanPurpose(self), "Raman Rai: choose a card you just drew", false)), $))
                NextAction
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case RARamanPurpose(self) =>
                cards.single.foreach { d =>
                    val l = g.discard(Corp).%(_.data.kind == d.data.kind)
                    if (l.any)
                        g.push(ChooseCardEffect(Corp, l, RARamanArchivesPurpose(self, d), "Raman Rai: choose a card of the same type in Archives", false))
                }
                NextAction
            case RARamanArchivesPurpose(self, d) =>
                cards.single.foreach { a =>
                    if (g.zone(d) == HandZone(Corp)) {
                        g.log(Corp, "reveals", d, "from HQ and swaps it with", a, "from Archives")
                        g.toArchives(d, true)
                        g.moveTo(a, HandZone(Corp))
                    }
                }
                NextAction
        }
    }

    object AryabhataTech extends RA("10070") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TraceResolved(true, _) => $(MessageEffect(Corp, "uses Aryabhata Tech"), gain(Corp, 1), LoseCreditsEffect(Runner, 1))
            case _ => $
        }
    }

    object ExecutiveSearchFirm extends RA("10072") {
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("search R&D for an executive, sysop or character and add it to HQ", click(), $(ChooseCardEffect(Corp, sortedDeck(c => g.has(c, "Executive") || g.has(c, "Sysop") || g.has(c, "Character")), AddToHandPurpose(Corp, true), "Executive Search Firm: choose a card", true), ShuffleDeckEffect(Corp)), click = true))
    }

    object IndianUnionStockExchange extends RA("10073") {
        def outOfFaction(c : Card)(implicit g : Game) = g.identity.get(Corp).exists(_.data.faction != c.data.faction)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Rezzed(c) if outOfFaction(c) => $(MessageEffect(Corp, "uses Indian Union Stock Exchange"), gain(Corp, 1))
            case Played(Corp, c) if c.data.isOperation && outOfFaction(c) => $(MessageEffect(Corp, "uses Indian Union Stock Exchange"), gain(Corp, 1))
            case _ => $
        }
    }

    object FullImmersionRecStudio extends RA("10108") {
        override def trashCostMod(self : Card, card : Card)(implicit g : Game) = (card == self).?(3 * g.hosted(self).num).|(0)
        override def abilities(self : Card)(implicit g : Game) = {
            val l = g.hand(Corp).%(c => c.data.isAsset || c.data.isAgenda)
            $(Ability("install an asset or agenda from HQ hosted on Full Immersion RecStudio", click(), $(ChooseCardEffect(Corp, l, RARecStudioPurpose(self), "Full Immersion RecStudio: choose an asset or agenda to install", false)), click = true, ok = l.any && g.hosted(self).num < 2, reason = "cannot host another card"))
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(c => g.push(RotCorpHostInstallEffect(c, p.card)))
            NextAction
        }
    }

    object IbrahimSalem extends RA("10109") {
        override def canRez(self : Card)(implicit g : Game) = g.scored(Corp).exists(_.data.isAgenda) || g.frRezCostPaid.has(self)
        // the forfeit is an additional cost paid before the rez (FinalRotRules.rezCard / discounted rezzes)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) if g.hand(Runner).any =>
                $(ChooseOneEffect(Corp, "Ibrahim Salem: name a card type", $("Event", "Hardware", "Program", "Resource")./(k => Alt(k, $(RAIbrahimAction(self, k))))))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case RAIbrahimAction(self, k) =>
                val h = g.hand(Runner)
                val l = h.%(_.data.kind == k)
                g.pushAll(h.any.$(LookAtEffect(Corp, h, "Ibrahim Salem: the grip")) ++ l.any.$(ChooseCardEffect(Corp, l, TrashPurpose(Corp), "Ibrahim Salem: choose a card to trash", false)))
                NextAction
        }
    }

    object ZealousJudge extends RA("10111") {
        override def canRez(self : Card)(implicit g : Game) = g.tagged
        override def abilities(self : Card)(implicit g : Game) = $(Ability("give the Runner 1 tag", $(ClickCost(1), CreditCost(1)), $(tag(1)), click = true))
    }

    object HyoubuResearchFacility extends RA("11012") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case RotPsiRevealed(c, _) if c > 0 && g.reg("rot-psi") == 0 => $(MessageEffect(Corp, "uses Hyoubu Research Facility"), gain(Corp, c))
            case _ => $
        }
    }

    object Watchdog extends RA("11015") {
        override def rezCostMod(self : Card, card : Card)(implicit g : Game) = (card.data.isIce && g.reg("rot-ice-rezzed") == 0).?(-g.tags).|(0)
    }

    object Sandburg extends RA("11020") {
        override def iceStrengthMod(self : Card, ice : Card)(implicit g : Game) = (g.credits(Corp) >= 10).?(g.credits(Corp) / 5).|(0)
    }

    object CIFund extends RA("11036") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) => $(RACIFundAction(self))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case RACIFundAction(self) =>
                g.push(RACIFundBankAction(self))
                val m = 3.atMost(g.credits(Corp))
                if (m > 0)
                    g.push(ChooseNumberEffect(Corp, self, 0, m, "cifund", "C.I. Fund: store how many credits?"))
                NextAction
            case NumberPickedAction(self, n, "cifund") =>
                val m = n.atMost(g.credits(Corp))
                if (m > 0) {
                    g.credits += Corp -> (g.credits(Corp) - m)
                    g.addCounter(self, "credit", m)
                    g.log(Corp, "stores", m.credits, "on", self)
                }
                NextAction
            case RACIFundBankAction(self) =>
                if (g.isInstalled(self) && g.counter(self, "credit") >= 6)
                    g.push(AddCounterEffect(self, "credit", 2))
                NextAction
        }
        override def abilities(self : Card)(implicit g : Game) = {
            val n = g.counter(self, "credit")
            $(Ability("take all hosted credits (" + n + ")", $(CreditCost(2), TrashSelfCost), $(gain(Corp, n)), ok = n > 0, reason = "no hosted credits"))
        }
    }

    object AlexaBelsky extends RA("11055") {
        override def abilities(self : Card)(implicit g : Game) = $(Ability("shuffle all cards in HQ into R&D", $(TrashSelfCost), $(RAAlexaAction(self)), ok = g.hand(Corp).any, reason = "HQ is empty"))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case RAAlexaAction(self) =>
                if (g.hand(Corp).any)
                    g.push(ChooseNumberEffect(Runner, self, 0, (g.credits(Runner) / 2).atMost(g.hand(Corp).num), "alexa", "Alexa Belsky: pay 2 credits for each random card to keep in HQ"))
                NextAction
            case NumberPickedAction(self, n, "alexa") =>
                val keep = n.atMost(g.hand(Corp).num)
                val shuffle = g.hand(Corp).num - keep
                if (keep > 0)
                    g.log(Runner, "pays", (2 * keep).credits, "to keep", keep.cards, "in HQ")
                g.pushAll($(LoseCreditsEffect(Runner, 2 * keep)) ++ (shuffle > 0).$(RandomPickEffect(Corp, g.hand(Corp), shuffle, RAAlexaPurpose(self))))
                NextAction
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.foreach(c => g.moveTo(c, DeckZone(Corp)))
            g.log(Corp, "shuffles", cards.num.cards, "from HQ into R&D")
            g.push(ShuffleDeckEffect(Corp))
            NextAction
        }
    }

    object FumikoYamamori extends RA("11073") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case RotPsiRevealed(c, r) if c != r => $(MessageEffect(Corp, "uses Fumiko Yamamori"), meat(1))
            case _ => $
        }
    }

    object ChiefSlee extends RA("11077") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case EncounterEnds(ice) if g.rotEncounterIce.has(ice) && g.rotEncounterUnbroken > 0 => $(AddCounterEffect(self, "power", g.rotEncounterUnbroken))
            case _ => $
        }
        override def abilities(self : Card)(implicit g : Game) = $(Ability("do 5 meat damage", $(ClickCost(1), CounterCost("power", 5)), $(meat(5)), click = true))
    }

    object AnsonRose extends RA("11096") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) => $(PlaceAdvancementEffect(self, 1))
            case Rezzed(ice) if ice.data.isIce && g.advancement(self) > 0 =>
                $(ChooseOneEffect(Corp, "Anson Rose: move advancement counters to the rezzed ice?", 0.to(g.advancement(self)).$./(k => Alt("Move " + k, (k > 0).$(MoveAdvancementEffect(self, ice, k))))))
            case _ => $
        }
    }

    object NASX extends RA("11118") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) => $(gain(Corp, 1))
            case _ => $
        }
        override def abilities(self : Card)(implicit g : Game) = {
            val n = g.counter(self, "power")
            $(
                Ability("place 1 power counter", c(1), $(AddCounterEffect(self, "power", 1))),
                Ability("place 2 power counters", c(2), $(AddCounterEffect(self, "power", 2))),
                Ability("gain " + (2 * n) + " credits (2 per power counter)", $(ClickCost(1), TrashSelfCost), $(gain(Corp, 2 * n)), click = true))
        }
    }

    object SynthDNAModification extends RA("12012") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SubsBroken(ice, _, _) if g.has(ice, "AP") && g.reg("rot-ap-broken") == 0 => $(MessageEffect(Corp, "uses Synth DNA Modification"), net(1))
            case _ => $
        }
    }

    object NetAnalytics extends RA("12014") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TagsRemoved(_) => $(OptionalEffect(Corp, "Net Analytics: draw 1 card?", $(draw(Corp, 1)), $))
            case _ => $
        }
    }

    object QuarantineSystem extends RA("12017") {
        override def abilities(self : Card)(implicit g : Game) = {
            val l = g.scored(Corp).%(_.data.isAgenda)
            $(Ability("forfeit an agenda: rez up to 3 pieces of ice paying 2 credits less per agenda point", $, $(ChooseCardEffect(Corp, l, RAQuarantinePurpose(self), "Quarantine System: choose an agenda to forfeit", false)), ok = l.any && g.allIce.exists(i => !g.isRezzed(i)), reason = "nothing to forfeit or rez"))
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case RAQuarantinePurpose(self) =>
                cards.single.foreach(c => g.push(ForfeitEffect(c), RAQuarantineRezAction(self, 2 * c.data.agendaPoints.|(0), 3)))
                NextAction
            case RAQuarantineRezPurpose(self, d, k) =>
                cards.single.foreach(c => g.push(RezDiscountEffect(c, d), RAQuarantineRezAction(self, d, k - 1)))
                NextAction
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case RAQuarantineRezAction(self, d, k) =>
                val l = g.allIce.%(i => !g.isRezzed(i) && g.impl(i).canRez(i) && g.canSpend(Corp, (g.rezCost(i) - d).atLeast(0), Spend.rez(i)))
                if (k > 0 && l.any)
                    g.push(ChooseCardEffect(Corp, l, RAQuarantineRezPurpose(self, d, k), "Quarantine System: rez a piece of ice paying " + d + " credits less?", true))
                NextAction
        }
    }

    object CPCGenerator extends RA("12034") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case ActionTaken(Runner, "credit") if g.reg("rot-runner-click-credit") == 0 => $(MessageEffect(Corp, "uses CPC Generator"), gain(Corp, 1))
            case _ => $
        }
    }

    object ClydeVanRite extends RA("12037") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) => $(RAClydeAction(self))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case RAClydeAction(self) =>
                g.push(ChooseOneEffect(Runner, "Clyde Van Rite: pay 1 credit or trash the top card of the stack", $(
                    Alt("Pay 1 credit", $(SpendEffect(Runner, 1, Spend.corpAbility, 0, $)), g.canSpend(Runner, 1, Spend.corpAbility)),
                    Alt("Trash the top card of the stack", $(MillEffect(Runner, 1)), g.deck(Runner).any || g.credits(Runner) < 1))))
                NextAction
        }
    }

    object BioroidWorkCrew extends RA("12051") {
        override def abilities(self : Card)(implicit g : Game) = {
            val l = g.hand(Corp).%(corpInstallableNow)
            // "Use this ability only during the next paid ability window after playing and resolving an operation" (FinalRotRules.frArm)
            $(Ability("install 1 card from HQ", $(TrashSelfCost), $(ChooseCardEffect(Corp, l, RABioroidWorkCrewPurpose(self), "Bioroid Work Crew: choose a card to install", false)), ok = l.any && g.frArmed("operation"), reason = "only in the next paid ability window after resolving an operation"))
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(c => g.push(RotChooseInstallServerEffect(c, false)))
            NextAction
        }
    }

    object WhampoaReclamation extends RA("12079") {
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("add 1 card from Archives to the bottom of R&D", $(TrashFromHandCost(1)), $(RAWhampoaAction(self)), once = once("whampoa"), ok = g.hand(Corp).any && g.discard(Corp).any, reason = "needs cards in HQ and Archives"))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case RAWhampoaAction(self) =>
                if (g.discard(Corp).any)
                    g.push(ChooseCardEffect(Corp, g.discard(Corp), BottomOfDeckPurpose(Corp), "Whampoa Reclamation: choose a card in Archives", false))
                NextAction
        }
    }

    object OpenForum extends RA("12097") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case CardsDrawn(Corp, _) if g.phase == "mandatory-draw" && !g.usedThisTurn(self, "forum") => $(MarkUsedEffect(self, "forum"), RAOpenForumAction(self))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case RAOpenForumAction(self) =>
                g.deck(Corp).starting.foreach(c => g.push(RevealEffect(Corp, c), AddToHandEffect(c), RAOpenForumTopAction(self)))
                NextAction
            case RAOpenForumTopAction(self) =>
                if (g.hand(Corp).any)
                    g.push(ChooseCardEffect(Corp, g.hand(Corp), TopOfDeckPurpose(Corp), "Open Forum: choose a card in HQ to add to the top of R&D", false))
                NextAction
        }
    }

    object MCAAusterityPolicy extends RA("12111") {
        override def abilities(self : Card)(implicit g : Game) = $(
            Ability("the Runner loses 1 click next turn; place 1 power counter", click(), $(FxLoseClicksAtTurnBeginsEffect(Runner, 1), AddCounterEffect(self, "power", 1)), click = true, once = once("mca")),
            Ability("gain 4 clicks", $(ClickCost(1), CounterCost("power", 3), TrashSelfCost), $(GainClicksEffect(Corp, 4)), click = true))
    }

    object BreachedDome extends RA("12113") {
        override def accessInArchives = true
        override def onAccess(self : Card)(implicit g : Game) = $(MessageEffect(Corp, "uses Breached Dome to trash the top card of the stack and do 1 meat damage"), MillEffect(Runner, 1), meat(1))
    }

    object EstelleMoon extends RA("13032") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Installed(Corp, c) if (c.data.isAsset || c.data.isAgenda || c.data.isUpgrade) && (g.zone(c) match { case RootZone(s) => s.isRemote ; case _ => false }) => $(AddCounterEffect(self, "power", 1))
            case _ => $
        }
        override def abilities(self : Card)(implicit g : Game) = {
            val n = g.counter(self, "power")
            $(Ability("draw " + n + " cards and gain " + (2 * n) + " credits", $(TrashSelfCost), $(draw(Corp, n), gain(Corp, 2 * n)), ok = n > 0, reason = "no power counters"))
        }
    }

    object IllegalArmsFactory extends RA("13045") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) => $(gain(Corp, 1), draw(Corp, 1))
            case _ => $
        }
        override def onTrash(self : Card, by : Faction)(implicit g : Game) = (by.isRunner && g.lastTrashedRezzed).$(BadPubEffect(1))
    }

    object MrStone extends RA("13046") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TagsGained(_) => $(MessageEffect(Corp, "uses Mr. Stone to do 1 meat damage"), meat(1))
            case _ => $
        }
    }

    object Honeyfarm extends RA("13054") {
        override def accessInArchives = true
        override def onAccess(self : Card)(implicit g : Game) = $(LoseCreditsEffect(Runner, 1))
    }

    object LongTermInvestment extends RA("13055") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) => $(AddCounterEffect(self, "credit", 2))
            case _ => $
        }
        override def abilities(self : Card)(implicit g : Game) = {
            val n = g.counter(self, "credit")
            $(Ability("take any number of hosted credits", click(), $(ChooseNumberEffect(Corp, self, 0, n, "lti", "Long-Term Investment: take how many credits?")), click = true, ok = n >= 8, reason = "needs 8 hosted credits"))
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case NumberPickedAction(self, n, "lti") =>
                g.push(TakeCreditsEffect(Corp, self, n))
                NextAction
        }
    }

    object InezDelgadoA extends RA("14004") {
        def candidates(implicit g : Game) = g.scored(Runner).%(c => c.data.isAgenda && g.agendaValue(c) >= 1)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case AgendaScored(c) if candidates.any => $(RAInezAction(self, c))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case RAInezAction(self, c) =>
                if (g.zone(c) == ScoredZone(Corp) && candidates.any)
                    g.push(OptionalEffect(Corp, "Investigator Inez Delgado: swap the scored agenda with an agenda in the Runner score area?", $(ChooseCardEffect(Corp, candidates, RAInezPurpose(self, c), "Choose an agenda in the Runner score area", false)), $))
                NextAction
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case RAInezPurpose(self, c) =>
                cards.single.foreach { x =>
                    if (g.zone(c) == ScoredZone(Corp)) {
                        g.moveTo(c, ScoredZone(Runner))
                        g.moveTo(x, ScoredZone(Corp))
                        g.log(Corp, "swaps", c, "with", x)
                        g.pushAll(g.impl(x).onScore(x))
                        g.push(CheckWinAction)
                    }
                }
                NextAction
        }
    }

    object InezDelgadoA2 extends RA("14005") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case AgendaStolen(c) => $(RAInez2Action(self, c))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case RAInez2Action(self, c) =>
                if (g.zone(c) == ScoredZone(Runner)) {
                    val l = g.impl(c).onScore(c)
                    g.push(RAInez2SwapAction(self, c))
                    if (l.any)
                        g.push(OptionalEffect(Corp, "Investigator Inez Delgado: resolve the when scored ability of the stolen agenda?", l, $))
                }
                NextAction
            case RAInez2SwapAction(self, c) =>
                val l = g.scored(Corp).%(_.data.isAgenda)
                if (g.zone(c) == ScoredZone(Runner) && l.any)
                    g.push(OptionalEffect(Corp, "Investigator Inez Delgado: swap the stolen agenda with an agenda in your score area?", $(ChooseCardEffect(Corp, l, RAInez2Purpose(self, c), "Choose an agenda in your score area", false)), $))
                NextAction
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case RAInez2Purpose(self, c) =>
                cards.single.foreach { x =>
                    if (g.zone(c) == ScoredZone(Runner)) {
                        g.moveTo(c, ScoredZone(Corp))
                        g.moveTo(x, ScoredZone(Runner))
                        g.log(Corp, "swaps", c, "with", x)
                        g.push(CheckWinAction)
                    }
                }
                NextAction
        }
    }

    object LtTodachine extends RA("14006") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Rezzed(c) if c.data.isIce => $(MessageEffect(Corp, "uses Lt. Todachine"), tag(1))
            case _ => $
        }
    }

    object LtTodachine2 extends RA("14007") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Rezzed(c) if c.data.isIce => $(MessageEffect(Corp, "uses Lt. Todachine"), tag(1))
            case BreachServer(s) if g.tagged && (s == HQ || s == RnD) => $(RATodachineAction(self, s))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case RATodachineAction(self, s) =>
                g.run.foreach { r =>
                    if (g.tagged && 1 + r.accessBonusFor(s.name) + r.accessBonusFor("total") >= 2) {
                        g.log(self, "makes the Runner access 1 fewer card")
                        g.push(AccessBonusEffect(s, -1))
                    }
                }
                NextAction
        }
    }

    object Trojan extends RA("14008") {
        override def onAccess(self : Card)(implicit g : Game) = (g.zone(self) != DiscardZone(Corp)).$(RATrojanAction(self))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case RATrojanAction(self) =>
                if (g.location.contains(self) && !g.zone(self).is[DiscardZone]) {
                    g.pending = g.pending./{
                        case AccessDecisionAction(c) if c == self => AccessDoneAction(self)
                        case a => a
                    }
                    if (g.isInstalled(self))
                        g.uninstall(self)
                    g.moveTo(self, RemovedZone(Corp))
                    g.cleanupRemotes()
                    g.log(self, "is destroyed")
                    g.push(LoseCreditsEffect(Corp, 2), RandomTrashFromHandEffect(Corp, 1))
                }
                NextAction
        }
    }

    object DedicatedResponseTeam extends RA("20081") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case RunEnds(_, true, _) if g.tagged => $(MessageEffect(Corp, "uses Dedicated Response Team"), meat(2))
            case _ => $
        }
    }

    object GRNDLRefinery extends RA("20083") {
        override def advanceable(self : Card)(implicit g : Game) = true
        override def abilities(self : Card)(implicit g : Game) = {
            val n = g.advancement(self)
            $(Ability("gain " + (4 * n) + " credits (4 per advancement counter)", $(ClickCost(1), TrashSelfCost), $(gain(Corp, 4 * n)), click = true))
        }
    }

    object MelangeMiningCorp extends RA("20127") {
        override def abilities(self : Card)(implicit g : Game) = $(Ability("gain 7 credits", click(3), $(gain(Corp, 7)), click = true))
    }

    object GeneSplicer extends RA("21012") {
        override def asAgendaPoints = 1
        override def advanceable(self : Card)(implicit g : Game) = true
        override def onAccess(self : Card)(implicit g : Game) = {
            val n = g.advancement(self)
            (n > 0).$(MessageEffect(Corp, "uses Gene Splicer to do " + n + " net damage"), net(n))
        }
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("add Gene Splicer to your score area as an agenda worth 1 point", $(ClickCost(1), CounterCost("advancement", 3)), $(RotAsAgendaEffect(self, Corp)), click = true))
    }

    object EchoChamber extends RA("21015") {
        override def asAgendaPoints = 1
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("add Echo Chamber to your score area as an agenda worth 1 point", click(3), $(RotAsAgendaEffect(self, Corp)), click = true))
    }

    object UrbanRenewal extends RA("21018") {
        override def initialCounters(self : Card)(implicit g : Game) = Map("power" -> 3)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) => $(RAUrbanRenewalAction(self))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case RAUrbanRenewalAction(self) =>
                if (g.isInstalled(self) && g.counter(self, "power") > 0) {
                    g.addCounter(self, "power", -1)
                    g.log("1 power counter removed from", self)
                    if (g.counter(self, "power") == 0) {
                        g.trashCard(self, Corp, false)
                        g.push(MessageEffect(Corp, "uses Urban Renewal to do 4 meat damage"), meat(4))
                    }
                }
                NextAction
        }
    }

    object ReconstructionContract extends RA("21020") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case DamageDone("meat", n) if n > 0 => $(PlaceAdvancementEffect(self, 1))
            case _ => $
        }
        override def abilities(self : Card)(implicit g : Game) = {
            val n = g.advancement(self)
            val l = g.installedCards(Corp).%(c => c != self && g.isAdvanceable(c))
            $(Ability("move hosted advancement counters to a card that can be advanced", $(TrashSelfCost), $(ChooseOneEffect(Corp, "Reconstruction Contract: move how many advancement counters?", 1.to(n).$./(k => Alt("Move " + k, $(ChooseCardEffect(Corp, l, AdvancePurpose(k), "Reconstruction Contract: choose a card", false)))))), ok = n > 0 && l.any, reason = "nothing to move"))
        }
    }

    object NGOFront extends RA("21039") {
        override def advanceable(self : Card)(implicit g : Game) = true
        override def abilities(self : Card)(implicit g : Game) = $(
            Ability("gain 5 credits", $(CounterCost("advancement", 1), TrashSelfCost), $(gain(Corp, 5))),
            Ability("gain 8 credits", $(CounterCost("advancement", 2), TrashSelfCost), $(gain(Corp, 8))))
    }

    object KuwindaK4H1U3 extends RA("21049") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) =>
                val x = g.counter(self, "power")
                $(OptionalEffect(Corp, "Kuwinda K4H1U3: initiate a trace " + x + "?", $(TraceEffect(x, $(core(1), TrashEffect(self, Corp, false)), $(AddCounterEffect(self, "power", 1)))), $))
            case _ => $
        }
    }

    object PersonalizedPortal extends RA("21056") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) => $(draw(Runner, 1), RAPortalAction(self))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case RAPortalAction(self) =>
                val n = g.hand(Runner).num / 2
                if (n > 0)
                    g.push(gain(Corp, n))
                NextAction
        }
    }

    object TechnoCo extends RA("21060") {
        def affected(c : Card)(implicit g : Game) = c.data.isProgram || c.data.isHardware || (c.data.isResource && g.has(c, "Virtual"))
        override def installCostMod(self : Card, card : Card)(implicit g : Game) = (g.owner(card).isRunner && affected(card)).?(1).|(0)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Installed(Runner, c) if affected(c) => $(MessageEffect(Corp, "uses TechnoCo"), gain(Corp, 1))
            case _ => $
        }
    }

    object MaliaZ0L0K4 extends RA("21069") {
        override def onRez(self : Card)(implicit g : Game) = {
            val l = g.runnerInstalledOf("Resource").%!(c => g.has(c, "Virtual"))
            l.any.$(ChooseCardEffect(Corp, l, RAMaliaPurpose(self), "Malia Z0L0K4: choose a resource to lose its printed abilities", false))
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach { c =>
                g.state(p.card).target = |(c)
                g.log(c, "loses its printed abilities (Malia Z0L0K4)")
                g.push(AddFlagEffect(Flag("disabled", |(c), None, "rot-malia", 0, $)))
            }
            NextAction
        }
    }

    object AmaniSenai extends RA("21076") {
        def trace(self : Card, agenda : Card)(implicit g : Game) : $[ForcedAction] =
            $(OptionalEffect(Corp, "Amani Senai: initiate a trace " + g.advancementRequirement(agenda) + "?", $(TraceEffect(g.advancementRequirement(agenda), $(RAAmaniAction(self)), $)), $))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case AgendaScored(c) => trace(self, c)
            case AgendaStolen(c) => trace(self, c)
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case RAAmaniAction(self) =>
                val l = g.runnerInstalled
                if (l.any)
                    g.push(ChooseCardEffect(Corp, l, ReturnToHandPurpose, "Amani Senai: choose an installed Runner card to add to the grip", false))
                NextAction
        }
    }

    object RashidaJaheem extends RA("21080") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) => $(OptionalEffect(Corp, "Rashida Jaheem: trash it to gain 3 credits and draw 3 cards?", $(TrashEffect(self, Corp, false), gain(Corp, 3), draw(Corp, 3)), $))
            case _ => $
        }
    }

    object WardenFatuma extends RA("21093")       // the extra subroutine: RotAssetRules.subs

    object FalseFlag extends RA("21120") {
        override def asAgendaPoints = 3
        override def advanceable(self : Card)(implicit g : Game) = true
        override def onAccess(self : Card)(implicit g : Game) = {
            val n = g.advancement(self) / 2
            (n > 0).$(MessageEffect(Corp, "uses False Flag"), tag(n))
        }
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("add False Flag to your score area as an agenda worth 3 points", $(ClickCost(1), CounterCost("advancement", 7)), $(RotAsAgendaEffect(self, Corp)), click = true))
    }

    object APISKeeperIsobel extends RA("22036") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) if g.installedCards(Corp).exists(c => g.advancement(c) > 0) => $(RAIsobelAction(self))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case RAIsobelAction(self) =>
                val l = g.installedCards(Corp).%(c => g.advancement(c) > 0)
                if (l.any)
                    g.push(ChooseCardEffect(Corp, l, RAIsobelPurpose(self), "API-S Keeper Isobel: remove an advancement counter to gain 3 credits?", true))
                NextAction
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(c => g.push(AddCounterEffect(c, "advancement", -1), gain(Corp, 3)))
            NextAction
        }
    }

    object Neurostasis extends RA("22037") {
        override def advanceable(self : Card)(implicit g : Game) = true
        override def onAccess(self : Card)(implicit g : Game) = {
            val n = g.advancement(self)
            val l = g.runnerInstalled
            (n > 0 && l.any).??(ambush(self, 3, "Neurostasis: pay 3 credits to shuffle " + n + " installed Runner cards into the stack?", $(ChooseCardsEffect(Corp, l, n.atMost(l.num), n, ShuffleIntoDeckPurpose(Runner), "Neurostasis: choose installed Runner cards"))))
        }
    }

    object SIU extends RA("22044") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) => $(OptionalEffect(Corp, "SIU: trash it to initiate a trace 3?", $(TrashEffect(self, Corp, false), TraceEffect(3, $(tag(1)), $)), $))
            case _ => $
        }
    }

    object DrudgeWork extends RA("22052") {
        override def initialCounters(self : Card)(implicit g : Game) = Map("power" -> 3)
        override def triggers(self : Card, e : Event)(implicit g : Game) = trashOnEmpty(self, e, "power")
        override def abilities(self : Card)(implicit g : Game) = {
            val l = (g.hand(Corp) ++ g.discard(Corp)).%(_.data.isAgenda)
            $(Ability("reveal an agenda from HQ or Archives, gain its agenda points in credits and shuffle it into R&D", $(ClickCost(1), CounterCost("power", 1)), $(ChooseCardEffect(Corp, l, RADrudgeWorkPurpose(self), "Drudge Work: choose an agenda", false), RotTrashIfEmptyEffect(self, "power")), click = true, ok = l.any, reason = "no agenda in HQ or Archives"))
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(c => g.push(RevealEffect(Corp, c), gain(Corp, c.data.agendaPoints.|(0)), MoveEffect(c, DeckZone(Corp), false), ShuffleDeckEffect(Corp)))
            NextAction
        }
    }

    object LadyLiberty extends RA("22058") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) => $(AddCounterEffect(self, "power", 1))
            case _ => $
        }
        override def abilities(self : Card)(implicit g : Game) = {
            val n = g.counter(self, "power")
            val l = g.hand(Corp).%(c => c.data.isAgenda && c.data.agendaPoints.|(0) == n)
            $(Ability("add an agenda worth " + n + " points from HQ to your score area", click(3), $(ChooseCardEffect(Corp, l, AddToScorePurpose(Corp), "Lady Liberty: choose an agenda", false)), click = true, ok = l.any, reason = "no agenda worth " + n + " points in HQ"))
        }
    }

    object AdonisCampaign extends RA("25070") {
        override def initialCounters(self : Card)(implicit g : Game) = Map("credit" -> 12)
        override def triggers(self : Card, e : Event)(implicit g : Game) = campaign(self, e, 3)
    }

    object AggressiveSecretary extends RA("25071") {
        override def advanceable(self : Card)(implicit g : Game) = true
        override def onAccess(self : Card)(implicit g : Game) = {
            val n = g.advancement(self)
            val l = g.runnerInstalledOf("Program")
            (n > 0).??(ambush(self, 2, "Aggressive Secretary: pay 2 credits to trash " + n + " programs?", $(ChooseCardsEffect(Corp, l, n.atMost(l.num), n, TrashPurpose(Corp), "Aggressive Secretary: choose programs to trash"))))
        }
    }

    object ProjectJunebug extends RA("25089") {
        override def advanceable(self : Card)(implicit g : Game) = true
        override def onAccess(self : Card)(implicit g : Game) = {
            val n = g.advancement(self)
            (n > 0).??(ambush(self, 1, "Project Junebug: pay 1 credit to do " + (2 * n) + " net damage?", $(net(2 * n))))
        }
    }

    object Sundew extends RA("25092") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            // the click spent on the basic run action: the run has already begun when the event is fired
            case ClicksSpent(Runner, _, cause) if g.reg("fx-click-spend-events-Runner") == 0 =>
                $(MessageEffect(Corp, "uses Sundew"), gain(Corp, 2)) ++
                (!cause.startsWith("lose-click")).$(SetMemoEffect(self, "action:" + g.turn)) ++
                (cause == "run" && g.run.exists(r => !r.ended && thisServer(self, r.server))).$(MarkUsedEffect(self, "sundew-paid"), MessageEffect(Corp, "pays 2 credits (Sundew: a run on its server began during that action)"), LoseCreditsEffect(Corp, 2))
            // a run on this server begins later during that action (a run event, a click ability): no other click spent since
            case RunInitiated(s) if thisServer(self, s) && g.state(self).memo == "action:" + g.turn && g.reg("fx-click-spend-events-Runner") == 1 && !g.usedThisTurn(self, "sundew-paid") =>
                $(MarkUsedEffect(self, "sundew-paid"), MessageEffect(Corp, "pays 2 credits (Sundew: a run on its server began during that action)"), LoseCreditsEffect(Corp, 2))
            case _ => $
        }
    }

    object GhostBranch extends RA("25109") {
        override def advanceable(self : Card)(implicit g : Game) = true
        override def onAccess(self : Card)(implicit g : Game) = {
            val n = g.advancement(self)
            (n > 0).??(ambush(self, 0, "Ghost Branch: give the Runner " + n + " tags?", $(tag(n))))
        }
    }

    object MarkedAccounts extends RA("25110") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) if g.counter(self, "credit") > 0 => $(TakeCreditsEffect(Corp, self, 1))
            case _ => $
        }
        override def abilities(self : Card)(implicit g : Game) = $(Ability("place 3 credits on Marked Accounts", click(), $(AddCounterEffect(self, "credit", 3)), click = true))
    }

    object ContractKiller extends RA("25127") {
        override def advanceable(self : Card)(implicit g : Game) = true
        override def abilities(self : Card)(implicit g : Game) = {
            val ok = g.advancement(self) >= 2
            val l = g.runnerInstalled.%(c => g.has(c, "Connection"))
            $(
                Ability("trash a connection", $(ClickCost(1), TrashSelfCost), $(ChooseCardEffect(Corp, l, TrashPurpose(Corp), "Contract Killer: choose a connection to trash", false)), click = true, ok = ok && l.any, reason = "needs 2 advancement counters and a connection"),
                Ability("do 2 meat damage", $(ClickCost(1), TrashSelfCost), $(meat(2)), click = true, ok = ok, reason = "needs 2 advancement counters"))
        }
    }

    object ElizabethMills extends RA("25128") {
        override def onRez(self : Card)(implicit g : Game) = (g.badPublicity > 0).$(BadPubEffect(-1))
        override def abilities(self : Card)(implicit g : Game) = {
            val l = g.runnerInstalled.%(c => c.data.isResource && g.has(c, "Location"))
            $(Ability("trash a location and take 1 bad publicity", $(ClickCost(1), TrashSelfCost), l.any.$(ChooseCardEffect(Corp, l, TrashPurpose(Corp), "Elizabeth Mills: choose a location to trash", false)) :+ BadPubEffect(1), click = true))
        }
    }

    object PublicSupport extends RA("25129") {
        override def asAgendaPoints = 1
        override def initialCounters(self : Card)(implicit g : Game) = Map("power" -> 3)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) if g.counter(self, "power") > 0 => $(AddCounterEffect(self, "power", -1))
            case CounterAdded(c, "power") if c == self && g.counter(self, "power") == 0 && g.isInstalled(self) => $(RotAsAgendaEffect(self, Corp))
            case _ => $
        }
    }

    object HostileInfrastructure extends RA("29011") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Trashed(c, Runner, _) if g.owner(c).isCorp => $(MessageEffect(Corp, "uses Hostile Infrastructure to do 1 net damage"), net(1))
            case _ => $
        }
        override def onTrash(self : Card, by : Faction)(implicit g : Game) = (by.isRunner && g.lastTrashedRezzed).$(MessageEffect(Corp, "uses Hostile Infrastructure to do 1 net damage"), net(1))
    }

    object Turtlebacks extends RA("29012") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case ServerCreated(_) => $(MessageEffect(Corp, "uses Turtlebacks"), gain(Corp, 1))
            case _ => $
        }
    }

    object ExecutiveBootCamp extends RA("29015") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) => $(RABootCampAction(self))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case RABootCampAction(self) =>
                val l = g.installedCards(Corp).%(c => !g.isRezzed(c) && !c.data.isAgenda && g.impl(c).canRez(c) && g.canSpend(Corp, (g.rezCost(c) - 1).atLeast(0), Spend.rez(c)))
                if (l.any)
                    g.push(ChooseCardEffect(Corp, l, RABootCampPurpose(self), "Executive Boot Camp: rez a card paying 1 credit less?", true))
                NextAction
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(c => g.push(RezDiscountEffect(c, 1)))
            NextAction
        }
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("search R&D for an asset and add it to HQ", $(CreditCost(1), TrashSelfCost), $(ChooseCardEffect(Corp, sortedDeck(_.data.isAsset), AddToHandPurpose(Corp, true), "Executive Boot Camp: choose an asset", true), ShuffleDeckEffect(Corp))))
    }

    val all : $[CardImpl] = $(
        CloneSuffrageMovement, BioEthicsAssociation, PoliticalDealings, SensieActorsUnion, CommercialBankersGroup, MumbadCityHall,
        JeevesModelBioroids, RamanRai, AryabhataTech, ExecutiveSearchFirm, IndianUnionStockExchange, FullImmersionRecStudio, IbrahimSalem,
        ZealousJudge, HyoubuResearchFacility, Watchdog, Sandburg, CIFund, AlexaBelsky, FumikoYamamori, ChiefSlee, AnsonRose, NASX,
        SynthDNAModification, NetAnalytics, QuarantineSystem, CPCGenerator, ClydeVanRite, BioroidWorkCrew, WhampoaReclamation, OpenForum,
        MCAAusterityPolicy, BreachedDome, EstelleMoon, IllegalArmsFactory, MrStone, Honeyfarm, LongTermInvestment, InezDelgadoA,
        InezDelgadoA2, LtTodachine, LtTodachine2, Trojan, DedicatedResponseTeam, GRNDLRefinery, MelangeMiningCorp, GeneSplicer,
        EchoChamber, UrbanRenewal, ReconstructionContract, NGOFront, KuwindaK4H1U3, PersonalizedPortal, TechnoCo, MaliaZ0L0K4,
        AmaniSenai, RashidaJaheem, WardenFatuma, FalseFlag, APISKeeperIsobel, Neurostasis, SIU, DrudgeWork, LadyLiberty, AdonisCampaign,
        AggressiveSecretary, ProjectJunebug, Sundew, GhostBranch, MarkedAccounts, ContractKiller, ElizabethMills, PublicSupport,
        HostileInfrastructure, Turtlebacks, ExecutiveBootCamp,
    )
}
