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
// Rotated (FFG era) agendas, ported from jinteki.net src/clj/game/cards/agendas.clj (part 1: core set .. Data and Destiny)
// ---------------------------------------------------------------------------------------------------------------------

case class RaAbtStartAction(card : Card) extends CardAction
case class RaAbtAction(card : Card, rest : $[Card]) extends CardAction
case class RaAbtNextAction(card : Card, rest : $[Card]) extends CardAction
case class RaAbtPurpose(card : Card, rest : $[Card]) extends CardPurpose
case class RaShuffleHQAction(card : Card) extends CardAction
case class RaCorpWarAction(card : Card) extends CardAction
case class RaDhppAction(card : Card, n : Int, anchor : |[Card]) extends CardAction
case class RaDhppPurpose(card : Card, n : Int, anchor : |[Card]) extends CardPurpose
case class RaWotanAction(card : Card) extends CardAction
case class RaChronosAction(card : Card) extends CardAction
case class RaBifrostPurpose(card : Card) extends CardPurpose
case class RaHeliumAction(card : Card, n : Int) extends CardAction
case class RaHeliumPurpose(card : Card, n : Int) extends CardPurpose
case class RaHriAction(card : Card) extends CardAction
case class RaAgendaCounterPurpose(card : Card) extends CardPurpose
case class RaResearchGrantPurpose(card : Card) extends CardPurpose
case class RaAwardBaitAction(card : Card) extends CardAction
case class RaHollywoodAction(card : Card) extends CardAction


object RotAgendaCards {

    // ---- Core Set -------------------------------------------------------------------------------------------------------

    object AcceleratedBetaTest extends RAg("01055") {
        override def onScore(self : Card)(implicit g : Game) = g.deck(Corp).any.$(OptionalEffect(Corp, "Accelerated Beta Test: look at the top 3 cards of R&D?", $(RaAbtStartAction(self)), $))
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case RaAbtStartAction(self) =>
                val top = g.deck(Corp).take(3)
                g.log(Corp, "looks at the top", top.num.cards, "of R&D")
                g.push(LookAtEffect(Corp, top, "Accelerated Beta Test: the top cards of R&D (top first)"), RaAbtAction(self, top))
                NextAction
            case RaAbtAction(self, rest) =>
                val l = rest.%(c => g.zone(c) == DeckZone(Corp))
                val ice = l.%(_.data.isIce)
                if (ice.any)
                    g.push(ChooseCardEffect(Corp, ice, RaAbtPurpose(self, l), "Accelerated Beta Test: install and rez a piece of ice, ignoring all costs", true), RaAbtNextAction(self, l))
                else if (l.any)
                    g.push(MessageEffect(Corp, "trashes the rest of the cards looked at with Accelerated Beta Test"), TrashCardsEffect(l, Corp))
                NextAction
            case RaAbtNextAction(self, rest) =>
                val l = rest.%(c => g.zone(c) == DeckZone(Corp))
                if (l.num < rest.num && l.exists(_.data.isIce))
                    g.push(RaAbtAction(self, l))
                else if (l.any)
                    g.push(MessageEffect(Corp, "trashes the rest of the cards looked at with Accelerated Beta Test"), TrashCardsEffect(l, Corp))
                NextAction
        }
        override def chosenCard(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case RaAbtPurpose(self, rest) =>
                cards.single.foreach(c => g.push(ChooseServerEffect(Corp, c, true, g.servers, true), RezEffect(c, true)))
                NextAction
        }
    }

    object AstroScriptPilotProgram extends RAg("01081") {
        override def onScore(self : Card)(implicit g : Game) = agendaCounters(self, 1)
        override def abilities(self : Card)(implicit g : Game) = corpScored(self).??($(Ability("place 1 advancement counter on an installed card you can advance", $(CounterCost("agenda", 1)),
            $(ChooseCardEffect(Corp, g.advanceableCards, AdvancePurpose(1), "AstroScript Pilot Program: choose a card to advance", false)), ok = g.advanceableCards.any, reason = "nothing to advance")))
    }

    object BreakingNews extends RAg("01082") {
        override def onScore(self : Card)(implicit g : Game) = $(tag(2), SetMemoEffect(self, "bn:" + g.halfTurn))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnEnds(_) if corpScored(self) && g.state(self).memo == "bn:" + g.halfTurn => $(MessageEffect(Corp, "uses Breaking News: the Runner removes 2 tags"), RemoveTagsEffect(2), SetMemoEffect(self, ""))
            case _ => $
        }
    }

    object PostedBounty extends RAg("01095") {
        override def onScore(self : Card)(implicit g : Game) = $(OptionalEffect(Corp, "Posted Bounty: forfeit it to give the Runner 1 tag and take 1 bad publicity?", $(ForfeitEffect(self), BadPubEffect(1), tag(1)), $))
    }

    // ---- Genesis / Creation and Control / Spin --------------------------------------------------------------------------

    object MandatoryUpgrades extends RAg("02011") {
        override def onScore(self : Card)(implicit g : Game) = g.active.isCorp.$(MessageEffect(Corp, "gains 1 additional click per turn from Mandatory Upgrades"), GainClicksEffect(Corp, 1))
        override def clicksPerTurn(self : Card, f : Faction)(implicit g : Game) = (f.isCorp && corpScored(self)).?(1).|(0)
    }

    object RestructuredDatapool extends RAg("02016") {
        override def abilities(self : Card)(implicit g : Game) = corpScored(self).??($(Ability("Trace 2: give the Runner 1 tag", click(), $(TraceEffect(2, $(tag(1)), $)), click = true)))
    }

    object ExecutiveRetreat extends RAg("02039") {
        override def onScore(self : Card)(implicit g : Game) = $(RaShuffleHQAction(self), AddCounterEffect(self, "agenda", 1))
        override def abilities(self : Card)(implicit g : Game) = corpScored(self).??($(Ability("draw 5 cards", $(ClickCost(1), CounterCost("agenda", 1)), $(draw(Corp, 5)), click = true)))
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case RaShuffleHQAction(self) =>
                val l = g.hand(Corp)
                l.foreach(c => g.moveTo(c, DeckZone(Corp)))
                g.log(Corp, "shuffles", l.num.cards, "from HQ into R&D")
                g.push(ShuffleDeckEffect(Corp))
                NextAction
        }
    }

    object GovernmentContracts extends RAg("02077") {
        override def abilities(self : Card)(implicit g : Game) = corpScored(self).??($(Ability("gain 4 credits", click(2), $(gain(Corp, 4)), click = true)))
    }

    object CorporateWar extends RAg("02120") {
        override def onScore(self : Card)(implicit g : Game) = $(RaCorpWarAction(self))
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case RaCorpWarAction(self) =>
                if (g.credits(Corp) >= 7)
                    g.push(MessageEffect(Corp, "uses Corporate War"), gain(Corp, 7))
                else
                    g.push(MessageEffect(Corp, "uses Corporate War and loses all credits"), LoseAllCreditsEffect(Corp))
                NextAction
        }
    }

    object DirectorHaasPetProject extends RAg("03004") {
        def candidates(anchor : |[Card])(implicit g : Game) = {
            val server = anchor./~(g.serverOf)
            val rootTaken = server.exists(s => g.root(s).exists(x => x.data.isAgenda || x.data.isAsset))
            (g.hand(Corp) ++ g.discard(Corp)).%(c => corpInstallable(c)).%(c => !rootTaken || c.data.isIce || c.data.isUpgrade)
        }
        override def onScore(self : Card)(implicit g : Game) = $(OptionalEffect(Corp, "Director Haas Pet Project: install cards from HQ and Archives in a new remote server?", $(RaDhppAction(self, 0, None)), $))
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case RaDhppAction(self, n, anchor) =>
                val l = candidates(anchor)
                if (n < 3 && l.any && (anchor.none || anchor.exists(x => g.serverOf(x).any)))
                    g.push(ChooseCardEffect(Corp, l, RaDhppPurpose(self, n, anchor), "Director Haas Pet Project: install a card from HQ or Archives (" + (3 - n) + " remaining)", true))
                NextAction
        }
        override def chosenCard(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case RaDhppPurpose(self, n, anchor) =>
                cards.single.foreach { c =>
                    val server = anchor./~(g.serverOf)
                    g.push(CorpInstallEffect(c, server, true), RaDhppAction(self, n + 1, anchor.||(|(c))))
                }
                NextAction
        }
    }

    object EfficiencyCommittee extends RAg("03005") {
        override def onScore(self : Card)(implicit g : Game) = agendaCounters(self, 3)
        override def abilities(self : Card)(implicit g : Game) = corpScored(self).??($(Ability("gain [click][click]; you cannot advance cards this turn", $(ClickCost(1), CounterCost("agenda", 1)),
            $(GainClicksEffect(Corp, 2), RegisterEffect("rot-no-advance", 1)), click = true)))
    }

    object ProjectWotan extends RAg("03006") {
        def approached(implicit g : Game) = g.run.%(r => !r.ended && r.phase == ApproachIcePhase)./~(_.ice).%(i => g.isRezzed(i) && g.has(i, "Bioroid"))
        override def onScore(self : Card)(implicit g : Game) = agendaCounters(self, 3)
        override def abilities(self : Card)(implicit g : Game) = corpScored(self).??($(Ability("the approached bioroid ice gains [sub] End the run for the remainder of this run", $(CounterCost("agenda", 1)),
            $(RaWotanAction(self)), ok = approached.any, reason = "only while the Runner approaches a rezzed bioroid")))
        override def extraSubroutines(self : Card, ice : Card)(implicit g : Game) =
            (corpScored(self) && g.scored(Corp).%(_.code == code).starting.has(self)).??(g.flagsOf("rot-wotan").%(_.card.has(ice))./(_ => etrSub))
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case RaWotanAction(self) =>
                approached.foreach { i =>
                    g.log(i, "gains [sub] End the run for the remainder of the run")
                    g.addFlag(Flag("rot-wotan", |(i), None, "run", 0, $))
                }
                NextAction
        }
    }

    object SentinelDefenseProgram extends RAg("03007") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case DamageDone("core", n) if n > 0 && corpScored(self) => $(MessageEffect(Corp, "uses Sentinel Defense Program"), net(1))
            case _ => $
        }
    }

    object GilaHandsArcology extends RAg("03023") {
        override def abilities(self : Card)(implicit g : Game) = corpScored(self).??($(Ability("gain 3 credits", click(2), $(gain(Corp, 3)), click = true)))
    }

    // ---- Lunar cycle ---------------------------------------------------------------------------------------------------

    object CharacterAssassination extends RAg("04014") {
        override def onScore(self : Card)(implicit g : Game) = {
            val l = g.runnerInstalledOf("Resource")
            l.any.$(ChooseCardEffect(Corp, l, TrashPurpose(Corp), "Character Assassination: choose a resource to trash", false))
        }
    }

    object GeothermalFracking extends RAg("04017") {
        override def onScore(self : Card)(implicit g : Game) = agendaCounters(self, 2)
        override def abilities(self : Card)(implicit g : Game) = corpScored(self).??($(Ability("gain 7 credits and take 1 bad publicity", $(ClickCost(1), CounterCost("agenda", 1)), $(gain(Corp, 7), BadPubEffect(1)), click = true)))
    }

    object CloneRetirement extends RAg("04032") {
        override def onScore(self : Card)(implicit g : Game) = (g.badPublicity > 0).$(OptionalEffect(Corp, "Clone Retirement: remove 1 bad publicity?", $(BadPubEffect(-1)), $))
        override def onSteal(self : Card)(implicit g : Game) = $(MessageEffect(Corp, "takes 1 bad publicity from Clone Retirement"), BadPubEffect(1))
    }

    object Profiteering extends RAg("04039") {
        override def onScore(self : Card)(implicit g : Game) = $(ChooseOneEffect(Corp, "Profiteering: take up to 3 bad publicity, gain 5 credits for each", 0.to(3).$./(k =>
            Alt("Take " + k + " bad publicity", (k > 0).$(BadPubEffect(k), gain(Corp, 5 * k))))))
    }

    object UnorthodoxPredictions extends RAg("04053") {
        override def onScore(self : Card)(implicit g : Game) = $(ChooseOneEffect(Corp, "Unorthodox Predictions: choose an ice type", $("Barrier", "Code Gate", "Sentry")./(t =>
            Alt(t, $(SetChoiceEffect(self, t), SetMemoEffect(self, "" + g.turn), MessageEffect(Corp, "prevents subroutines on " + t + " ice from being broken until the next Corp turn"))))))
        override def preventsBreak(self : Card, ice : Card, breaker : Card)(implicit g : Game) = corpScored(self) && g.state(self).memo == "" + g.turn && g.state(self).choice != "" && g.has(ice, g.state(self).choice)
    }

    object VeteransProgram extends RAg("04080") {
        override def onScore(self : Card)(implicit g : Game) = (g.badPublicity > 0).$(OptionalEffect(Corp, "Veterans Program: remove up to 2 bad publicity?", $(BadPubEffect(-(g.badPublicity.atMost(2)))), $))
    }

    object MarketResearch extends RAg("04095") {
        override def onScore(self : Card)(implicit g : Game) = g.tagged.$(AddCounterEffect(self, "agenda", 1))
        override def agendaPointsBonus(self : Card)(implicit g : Game) = (corpScored(self) && g.counter(self, "agenda") > 0).?(1).|(0)
    }

    object VulcanCoverup extends RAg("04098") {
        override def onScore(self : Card)(implicit g : Game) = $(meat(2))
        override def onSteal(self : Card)(implicit g : Game) = $(MessageEffect(Corp, "takes 1 bad publicity from Vulcan Coverup"), BadPubEffect(1))
    }

    object NAPDContract extends RAg("04119") {
        override def accessCostPaidByRunner(self : Card)(implicit g : Game) = $(CreditCost(4))
        override def advancementRequirementMod(self : Card, agenda : Card)(implicit g : Game) = (agenda == self).?(g.badPublicity).|(0)
    }

    // ---- Honor and Profit / Order and Chaos -----------------------------------------------------------------------------

    object MedicalBreakthrough extends RAg("05005") {
        override def advancementRequirementMod(self : Card, agenda : Card)(implicit g : Game) = (scoredAnywhere(self) && agenda != self && agenda.code == code).?(-1).|(0)
    }

    object TheFuturePerfect extends RAg("05007") {
        def psi(self : Card)(implicit g : Game) : $[ForcedAction] = $(PsiEffect($, $(MessageEffect(Corp, "wins the psi game: The Future Perfect cannot be stolen during this access"), AddFlagEffect(Flag("rot-no-steal", |(self), None, "run", 0, $)))))
        override def onAccess(self : Card)(implicit g : Game) = (!g.isInstalled(self)).??(psi(self))
        override def onArchivesAccess(self : Card)(implicit g : Game) = psi(self)
    }

    object DomesticSleepers extends RAg("06001") {
        override def abilities(self : Card)(implicit g : Game) = corpScored(self).??($(Ability("place 1 agenda counter on Domestic Sleepers", click(3), $(AddCounterEffect(self, "agenda", 1)), click = true)))
        override def agendaPointsBonus(self : Card)(implicit g : Game) = (corpScored(self) && g.counter(self, "agenda") > 0).?(1).|(0)
    }

    object EncryptedPortals extends RAg("06024") {
        override def iceStrengthMod(self : Card, ice : Card)(implicit g : Game) = (corpScored(self) && g.has(ice, "Code Gate")).?(1).|(0)
        override def onScore(self : Card)(implicit g : Game) = $(gain(Corp, g.rezzedIce.count(c => g.has(c, "Code Gate"))))
    }

    object EdenFragment extends RAg("06030") {
        override def installCostMod(self : Card, card : Card)(implicit g : Game) =
            (corpScored(self) && card.data.isIce && !g.flagsOf("installed").exists(_.card.exists(_.data.isIce))).?(-99).|(0)
    }

    object ChronosProject extends RAg("06049") {
        override def onScore(self : Card)(implicit g : Game) = $(RaChronosAction(self))
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case RaChronosAction(self) =>
                val l = g.discard(Runner)
                l.foreach(c => g.moveTo(c, RemovedZone(Runner)))
                g.log(Corp, "uses Chronos Project to remove", l.num.cards, "in the heap from the game")
                NextAction
        }
    }

    object LabyrinthineServers extends RAg("06063") {
        override def onScore(self : Card)(implicit g : Game) = $(AddCounterEffect(self, "power", 2))
        override def preventions(self : Card, kind : String, n : Int)(implicit g : Game) =
            (kind == "jack-out" && corpScored(self)).$(Ability("prevent the Runner from jacking out (the Runner cannot jack out for the remainder of this run)", $(CounterCost("power", 1)), $(Prevent.jackOut)))
    }

    object HadesFragment extends RAg("06071") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) if corpScored(self) && g.discard(Corp).any =>
                $(ChooseCardEffect(Corp, g.discard(Corp), BottomOfDeckPurpose(Corp), "Hades Fragment: add a card from Archives to the bottom of R&D?", true))
            case _ => $
        }
    }

    object BifrostArray extends RAg("06081") {
        override def onScore(self : Card)(implicit g : Game) = {
            val l = g.scored(Corp).%(c => c.data.isAgenda && c.code != code && g.owner(c).isCorp)
            l.any.$(ChooseCardEffect(Corp, l, RaBifrostPurpose(self), "Bifrost Array: trigger the when scored ability of another agenda?", true))
        }
        override def chosenCard(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case RaBifrostPurpose(self) =>
                cards.single.foreach { c =>
                    g.log(Corp, "uses Bifrost Array to trigger the when scored ability of", c)
                    g.pushAll(g.impl(c).onScore(c))
                }
                NextAction
        }
    }

    object SuperiorCyberwalls extends RAg("06087") {
        override def iceStrengthMod(self : Card, ice : Card)(implicit g : Game) = (corpScored(self) && g.has(ice, "Barrier")).?(1).|(0)
        override def onScore(self : Card)(implicit g : Game) = $(gain(Corp, g.rezzedIce.count(c => g.has(c, "Barrier"))))
    }

    object Helium3Deposit extends RAg("06101") {
        def targets(implicit g : Game) = (g.installedCards(Corp) ++ g.installedCards(Runner) ++ g.scored(Corp) ++ g.scored(Runner) ++ g.identity.values.$).distinct.%(c => g.counter(c, "power") > 0)
        override def onScore(self : Card)(implicit g : Game) = targets.any.$(ChooseOneEffect(Corp, "Helium-3 Deposit: place power counters on a card with a power counter", $(
            Alt("Place 2 power counters", $(RaHeliumAction(self, 2))),
            Alt("Place 1 power counter", $(RaHeliumAction(self, 1))),
            Alt("No action", $))))
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case RaHeliumAction(self, n) =>
                if (targets.any)
                    g.push(ChooseCardEffect(Corp, targets, RaHeliumPurpose(self, n), "Helium-3 Deposit: choose a card with a power counter", false))
                NextAction
        }
        override def chosenCard(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case RaHeliumPurpose(self, n) =>
                cards.single.foreach(c => g.push(AddCounterEffect(c, "power", n)))
                NextAction
        }
    }

    object UtopiaFragment extends RAg("06110") {
        override def stealCostMod(self : Card, agenda : Card)(implicit g : Game) = (corpScored(self) && g.advancement(agenda) > 0).$(CreditCost(2 * g.advancement(agenda)))
    }

    object FirmwareUpdates extends RAg("07004") {
        def targets(implicit g : Game) = g.allIce.%(c => g.isAdvanceable(c))
        override def onScore(self : Card)(implicit g : Game) = agendaCounters(self, 3)
        override def abilities(self : Card)(implicit g : Game) = corpScored(self).??($(Ability("place 1 advancement counter on a piece of ice you can advance", $(CounterCost("agenda", 1)),
            $(ChooseCardEffect(Corp, targets, AdvancePurpose(1), "Firmware Updates: choose a piece of ice", false)), once = once("firmware-updates"), ok = targets.any, reason = "no ice can be advanced")))
    }

    object GlennStation extends RAg("07005") {
        override def abilities(self : Card)(implicit g : Game) = corpScored(self).??($(
            Ability("host a card from HQ facedown", click(), $(ChooseCardEffect(Corp, g.hand(Corp), StashPurpose(self, true), "Glenn Station: choose a card in HQ to host", false)), click = true,
                ok = g.hand(Corp).any && g.stashed(self).none, reason = "already hosts a card"),
            Ability("add the hosted card to HQ", click(), $(ChooseCardEffect(Corp, g.stashed(self), ToHandTopPurpose(self, Corp), "Glenn Station: choose a hosted card", false)), click = true,
                ok = g.stashed(self).any, reason = "no hosted card")))
    }

    object GovernmentTakeover extends RAg("07006") {
        override def abilities(self : Card)(implicit g : Game) = corpScored(self).??($(Ability("gain 3 credits", click(), $(gain(Corp, 3)), click = true)))
    }

    object HighRiskInvestment extends RAg("07007") {
        override def onScore(self : Card)(implicit g : Game) = agendaCounters(self, 1)
        override def abilities(self : Card)(implicit g : Game) = corpScored(self).??($(Ability("gain 1 credit for each credit in the Runner credit pool", $(ClickCost(1), CounterCost("agenda", 1)),
            $(RaHriAction(self)), click = true, ok = g.credits(Runner) > 0, reason = "the Runner has no credits")))
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case RaHriAction(self) =>
                g.push(gain(Corp, g.credits(Runner)))
                NextAction
        }
    }

    // ---- SanSan / Mumbad / Flashpoint -----------------------------------------------------------------------------------

    object GeneticResequencing extends RAg("08013") {
        override def onScore(self : Card)(implicit g : Game) = {
            val l = g.scored(Corp).%(_.data.isAgenda)
            l.any.$(ChooseCardEffect(Corp, l, RaAgendaCounterPurpose(self), "Genetic Resequencing: place 1 agenda counter on an agenda in your score area?", true))
        }
        override def chosenCard(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case RaAgendaCounterPurpose(self) =>
                cards.foreach(c => g.push(AddCounterEffect(c, "agenda", 1)))
                NextAction
        }
    }

    object ResearchGrant extends RAg("08032") {
        override def onScore(self : Card)(implicit g : Game) = {
            val l = g.installedCards(Corp).%(c => c.code == code && c != self)
            l.any.$(ChooseCardEffect(Corp, l, RaResearchGrantPurpose(self), "Research Grant: score another installed copy of Research Grant?", true))
        }
        override def chosenCard(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case RaResearchGrantPurpose(self) =>
                cards.single.foreach { c =>
                    if (g.isInstalled(c)) {
                        g.log(Corp, "uses Research Grant to score another copy")
                        g.scoreAgenda(c)
                    }
                }
                NextAction
        }
    }

    object SelfDestructChips extends RAg("08051") {
        override def handSize(self : Card, f : Faction)(implicit g : Game) = (f.isRunner && corpScored(self)).?(-1).|(0)
    }

    object UnderwayRenovation extends RAg("08077") {
        override def onInstall(self : Card)(implicit g : Game) = $(FaceupInstallEffect(self))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Advanced(c, _) if c == self && g.isInstalled(self) => $(MessageEffect(Corp, "uses Underway Renovation"), MillEffect(Runner, (g.advancement(self) >= 4).?(2).|(1)))
            case _ => $
        }
    }

    object AwardBait extends RAg("08093") {
        override def onAccess(self : Card)(implicit g : Game) = $(RaAwardBaitAction(self))
        override def onArchivesAccess(self : Card)(implicit g : Game) = $(RaAwardBaitAction(self))
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case RaAwardBaitAction(self) =>
                val l = g.advanceableCards
                if (l.any)
                    g.push(ChooseOneEffect(Corp, "Award Bait: place advancement counters on a card you can advance", $(
                        Alt("Place 2 advancement counters", $(ChooseCardEffect(Corp, l, AdvancePurpose(2), "Award Bait: choose a card", false))),
                        Alt("Place 1 advancement counter", $(ChooseCardEffect(Corp, l, AdvancePurpose(1), "Award Bait: choose a card", false))),
                        Alt("No action", $))))
                NextAction
        }
    }

    object HollywoodRenovation extends RAg("08098") {
        override def onInstall(self : Card)(implicit g : Game) = $(FaceupInstallEffect(self))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Advanced(c, _) if c == self && g.isInstalled(self) => $(RaHollywoodAction(self))
            case _ => $
        }
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case RaHollywoodAction(self) =>
                val n = (g.advancement(self) >= 6).?(2).|(1)
                val l = g.advanceableCards.but(self)
                if (l.any)
                    g.push(ChooseCardEffect(Corp, l, AdvancePurpose(n), "Hollywood Renovation: place " + n + " advancement counter" + (n > 1).??("s") + " on another card?", true))
                NextAction
        }
    }

    object VanityProject extends RAg("08100")

    object AncestralImager extends RAg("08112") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case RunEnds(_, _, _) if corpScored(self) && g.run.exists(_.jackedOut) => $(MessageEffect(Corp, "uses Ancestral Imager"), net(1))
            case _ => $
        }
    }

    object TheFutureIsNow extends RAg("08120") {
        override def onScore(self : Card)(implicit g : Game) = g.deck(Corp).any.$(
            ChooseCardEffect(Corp, g.deck(Corp).sortBy(_.title), AddToHandPurpose(Corp, false), "The Future is Now: choose a card in R&D to add to HQ", false), ShuffleDeckEffect(Corp))
    }

    // ---- Data and Destiny -----------------------------------------------------------------------------------------------

    object FifteenMinutes extends RAg("09004") {
        override def abilities(self : Card)(implicit g : Game) = (g.active.isCorp && scoredAnywhere(self)).??($(Ability("shuffle 15 Minutes into R&D", click(),
            $(MessageEffect(Corp, "shuffles 15 Minutes into R&D"), MoveEffect(self, DeckZone(Corp), false), ShuffleDeckEffect(Corp)), click = true)))
    }

    object ImprovedTracers extends RAg("09005") {
        override def iceStrengthMod(self : Card, ice : Card)(implicit g : Game) = (corpScored(self) && g.has(ice, "Tracer")).?(1).|(0)
        override def traceBonus(self : Card)(implicit g : Game) = (corpScored(self) && g.fxResolvingSubs.any).?(1).|(0)
    }

    object RebrandingTeam extends RAg("09006") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case AgendaScored(c) if c == self => g.installedCards(Corp).%(_.data.isAsset)./(c => ExtraSubtypesEffect(c, $("Advertisement"), true))
            case Installed(Corp, c) if corpScored(self) && c.data.isAsset => $(ExtraSubtypesEffect(c, $("Advertisement"), true))
            case _ => $
        }
    }

    object QuantumPredictiveModel extends RAg("09007") {
        def add(self : Card)(implicit g : Game) : $[ForcedAction] = g.tagged.$(MessageEffect(Corp, "adds Quantum Predictive Model to the score area"), AddToScoreAreaEffect(self, Corp))
        override def onAccess(self : Card)(implicit g : Game) = add(self)
        override def onArchivesAccess(self : Card)(implicit g : Game) = add(self)
    }

    object GlobalFoodInitiative extends RAg("09026") {
        override def agendaPointsBonus(self : Card)(implicit g : Game) = (g.zone(self) == ScoredZone(Runner)).?(-1).|(0)
    }

    val all : $[CardImpl] = $(
        AcceleratedBetaTest, AstroScriptPilotProgram, BreakingNews, PostedBounty, MandatoryUpgrades, RestructuredDatapool, ExecutiveRetreat,
        GovernmentContracts, CorporateWar, DirectorHaasPetProject, EfficiencyCommittee, ProjectWotan, SentinelDefenseProgram, GilaHandsArcology,
        CharacterAssassination, GeothermalFracking, CloneRetirement, Profiteering, UnorthodoxPredictions, VeteransProgram, MarketResearch,
        VulcanCoverup, NAPDContract, MedicalBreakthrough, TheFuturePerfect, DomesticSleepers, EncryptedPortals, EdenFragment, ChronosProject,
        LabyrinthineServers, HadesFragment, BifrostArray, SuperiorCyberwalls, Helium3Deposit, UtopiaFragment, FirmwareUpdates, GlennStation,
        GovernmentTakeover, HighRiskInvestment, GeneticResequencing, ResearchGrant, SelfDestructChips, UnderwayRenovation, AwardBait,
        HollywoodRenovation, VanityProject, AncestralImager, TheFutureIsNow, FifteenMinutes, ImprovedTracers, RebrandingTeam,
        QuantumPredictiveModel, GlobalFoodInitiative,
    ) ++ RotAgendaCards2.all
}
