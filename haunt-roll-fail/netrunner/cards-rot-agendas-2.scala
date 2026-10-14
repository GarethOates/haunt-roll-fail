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
// Rotated (FFG era) agendas, ported from jinteki.net src/clj/game/cards/agendas.clj (part 2: Mumbad .. Magnum Opus)
// ---------------------------------------------------------------------------------------------------------------------

case class RaNewConstructionPurpose(card : Card, rez : Boolean) extends CardPurpose
case class RaSensorNetPurpose(card : Card) extends CardPurpose
case class RaKusanagiPurpose(card : Card) extends CardPurpose
case class RaReeducationPurpose(card : Card) extends CardPurpose
case class RaReeducationRunnerAction(card : Card, n : Int) extends CardAction
case class RaStandoffAction(card : Card, f : Faction) extends CardAction
case class RaMsrAction(card : Card, k : Int) extends CardAction
case class RaMsrPurpose(card : Card, k : Int) extends CardPurpose
case class RaBrainRewiringAction(card : Card) extends CardAction
case class RaIllicitSalesAction(card : Card) extends CardAction
case class RaOversightAction(card : Card, remote : Boolean) extends CardAction
case class RaOversightPurpose(card : Card, remote : Boolean) extends CardPurpose
case class RaRemoteEnforcementAction(card : Card) extends CardAction
case class RaRemoteEnforcementPurpose(card : Card) extends CardPurpose
case class RaBacterialAction(card : Card) extends CardAction
case class RaBacterialTrashPurpose(card : Card, top : $[Card]) extends CardPurpose
case class RaBacterialHandPurpose(card : Card, rest : $[Card]) extends CardPurpose
case class RaViralAction(card : Card) extends CardAction
case class RaBroadDaylightAction(card : Card) extends CardAction
case class RaPaperTrailAction(card : Card) extends CardAction
case class RaTprPurpose(card : Card) extends CardPurpose


object RotAgendaCards2 {

    // ---- Mumbad cycle ----------------------------------------------------------------------------------------------------

    object AdvancedConceptHopper extends RAg("10011") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case RunInitiated(_) if corpScored(self) && g.reg("runs") == 1 => $(ChooseOneEffect(Corp, "Advanced Concept Hopper: choose one", $(
                Alt("Draw 1 card", $(draw(Corp, 1))),
                Alt("Gain 1 credit", $(gain(Corp, 1))),
                Alt("No action", $))))
            case _ => $
        }
    }

    object RemoteDataFarm extends RAg("10033") {
        override def handSize(self : Card, f : Faction)(implicit g : Game) = (f.isCorp && corpScored(self)).?(2).|(0)
    }

    object NewConstruction extends RAg("10035") {
        override def onInstall(self : Card)(implicit g : Game) = $(FaceupInstallEffect(self))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Advanced(c, _) if c == self && g.isInstalled(self) =>
                val l = g.hand(Corp).%(x => x.data.isAgenda || x.data.isAsset || x.data.isUpgrade)
                l.any.$(ChooseCardEffect(Corp, l, RaNewConstructionPurpose(self, g.advancement(self) >= 5), "New Construction: install a card from HQ in the root of a new server?", true))
            case _ => $
        }
        override def chosenCard(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case RaNewConstructionPurpose(self, rez) =>
                cards.single.foreach { c =>
                    if (rez)
                        g.push(CorpInstallEffect(c, None, false), RezIgnoringAllCostsEffect(c))
                    else
                        g.push(CorpInstallEffect(c, None, false))
                }
                NextAction
        }
    }

    object CorporateSalesTeam extends RAg("10037") {
        override def onScore(self : Card)(implicit g : Game) = $(AddCounterEffect(self, "credit", 10))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(_) if corpScored(self) && g.counter(self, "credit") > 0 => $(TakeCreditsEffect(Corp, self, 1))
            case _ => $
        }
    }

    object VotingMachineInitiative extends RAg("10048") {
        override def onScore(self : Card)(implicit g : Game) = agendaCounters(self, 3)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) if corpScored(self) && g.counter(self, "agenda") > 0 =>
                $(OptionalEffect(Corp, "Voting Machine Initiative: spend an agenda counter to make the Runner lose 1 click?", $(AddCounterEffect(self, "agenda", -1), LoseClicksEffect(Runner, 1)), $))
            case _ => $
        }
    }

    object PersonalityProfiles extends RAg("10066") {
        def punish(implicit g : Game) : $[ForcedAction] = g.hand(Runner).any.$(MessageEffect(Corp, "uses Personality Profiles"), RandomTrashFromHandEffect(Runner, 1))
        override def triggers(self : Card, e : Event)(implicit g : Game) = if (!corpScored(self)) $ else {
            // installs from the heap (FinalRotRules.frFromHeap; facedown installs: the heap before the event) and searches of the
            // stack (FrStackSearched: a stack shuffle by a card whose text searches the stack)
            e match {
                case Installed(Runner, c) if g.frFromHeap.contains(c) => punish
                case FacedownInstalled(c) if g.rotHeapPrev.has(c) => punish
                case FrStackSearched(_) => punish
                case _ => $
            }
        }
    }

    object DedicatedNeuralNet extends RAg("10088") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(HQ) if corpScored(self) && g.successfulRunsThisTurn.count(HQ) == 1 =>
                $(MessageEffect(Corp, "uses Dedicated Neural Net"), PsiEffect($, $(MessageEffect(Corp, "will choose the cards the Runner accesses from HQ"), AddFlagEffect(Flag("rot-corp-chooses-hq", |(self), None, "run", 0, $)))))
            case _ => $
        }
    }

    object PuppetMaster extends RAg("10090") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(_) if corpScored(self) && g.advanceableCards.any =>
                $(ChooseCardEffect(Corp, g.advanceableCards, AdvancePurpose(1), "Puppet Master: place 1 advancement counter on a card that can be advanced?", true))
            case _ => $
        }
    }

    object ImprovedProteinSource extends RAg("10105") {
        override def onScore(self : Card)(implicit g : Game) = $(gain(Runner, 4))
        override def onSteal(self : Card)(implicit g : Game) = $(gain(Runner, 4))
    }

    object Merger extends RAg("10114") {
        override def agendaPointsBonus(self : Card)(implicit g : Game) = (g.zone(self) == ScoredZone(Runner)).?(1).|(0)
    }

    // ---- Flashpoint / Red Sand ----------------------------------------------------------------------------------------------

    object CrisisManagement extends RAg("11018") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) if corpScored(self) && g.tagged => $(MessageEffect(Corp, "uses Crisis Management"), meat(1))
            case _ => $
        }
    }

    object ProjectKusanagi extends RAg("11052") {
        override def onScore(self : Card)(implicit g : Game) = agendaCounters(self, g.lastScoredAdvancement - 2)
        override def abilities(self : Card)(implicit g : Game) = corpScored(self).??($(Ability("a piece of ice gains [sub] Do 1 net damage for the remainder of this run", $(CounterCost("agenda", 1)),
            $(ChooseCardEffect(Corp, g.rezzedIce, RaKusanagiPurpose(self), "Project Kusanagi: choose a piece of ice", false)), ok = g.run.exists(r => !r.ended) && g.rezzedIce.any, reason = "only during a run")))
        override def extraSubroutines(self : Card, ice : Card)(implicit g : Game) =
            (corpScored(self) && g.scored(Corp).%(_.code == code).starting.has(self)).??(g.flagsOf("rot-kusanagi").%(_.card.has(ice))./(_ => netSub(1)))
        override def chosenCard(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case RaKusanagiPurpose(self) =>
                cards.single.foreach { c =>
                    g.log(c, "gains [sub] Do 1 net damage for the remainder of the run")
                    g.addFlag(Flag("rot-kusanagi", |(c), None, "run", 0, $))
                }
                NextAction
        }
    }

    object ShowOfForce extends RAg("11099") {
        override def onScore(self : Card)(implicit g : Game) = $(meat(2))
    }

    object SensorNetActivation extends RAg("11110") {
        def bioroids(implicit g : Game) = g.installedCards(Corp).%(c => !g.isRezzed(c) && g.has(c, "Bioroid") && !c.data.isAgenda)
        override def onScore(self : Card)(implicit g : Game) = agendaCounters(self, 1)
        override def abilities(self : Card)(implicit g : Game) = corpScored(self).??($(Ability("rez a bioroid ignoring all costs (derezzed when the turn ends)", $(CounterCost("agenda", 1)),
            $(ChooseCardEffect(Corp, bioroids, RaSensorNetPurpose(self), "Sensor Net Activation: choose a bioroid to rez", false)), ok = bioroids.any, reason = "no unrezzed bioroid")))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnEnds(_) if g.state(self).target.any => g.state(self).target.$./~(c => $(DerezEffect(c))) :+ SetTargetEffect(self, None)
            case _ => $
        }
        override def chosenCard(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case RaSensorNetPurpose(self) =>
                cards.single.foreach(c => g.push(RezEffect(c, true), SetTargetEffect(self, |(c))))
                NextAction
        }
    }

    object NetQuarantine extends RAg("11114") {
        override def linkZero(self : Card)(implicit g : Game) = corpScored(self)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TraceResolved(_, n) if corpScored(self) && n >= 2 => $(MessageEffect(Corp, "uses Net Quarantine"), gain(Corp, n / 2))
            case _ => $
        }
    }

    object NEXTWave2 extends RAg("12009") {
        override def onScore(self : Card)(implicit g : Game) = g.rezzedIce.exists(c => g.has(c, "NEXT")).$(OptionalEffect(Corp, "NEXT Wave 2: do 1 core damage?", $(core(1)), $))
    }

    object ObokataProtocol extends RAg("12070") {
        override def stealDamage(self : Card, agenda : Card)(implicit g : Game) = (agenda == self).?(4).|(0)
    }

    object EscalateVitriol extends RAg("12073") {
        override def abilities(self : Card)(implicit g : Game) = corpScored(self).??($(Ability("gain 1 credit for each tag", click(), $(gain(Corp, g.tags)), click = true, once = once("escalate-vitriol"),
            ok = g.tagged, reason = "the Runner is not tagged")))
    }

    object Reeducation extends RAg("12074") {
        override def onScore(self : Card)(implicit g : Game) = g.hand(Corp).any.$(ChooseCardsEffect(Corp, g.hand(Corp), 0, g.hand(Corp).num, RaReeducationPurpose(self),
            "Reeducation: choose any number of cards in HQ to add to the bottom of R&D"))
        override def chosenCard(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case RaReeducationPurpose(self) =>
                if (cards.none)
                    g.log(Corp, "adds no cards to the bottom of R&D")
                else {
                    cards.foreach(c => g.moveTo(c, DeckZone(Corp)))
                    g.log(Corp, "adds", cards.num.cards, "from HQ to the bottom of R&D")
                    g.push(draw(Corp, cards.num), RaReeducationRunnerAction(self, cards.num))
                }
                NextAction
        }
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case RaReeducationRunnerAction(self, n) =>
                if (g.hand(Runner).num >= n)
                    g.push(RandomToBottomEffect(Runner, n))
                NextAction
        }
    }

    object MeteorMining extends RAg("12076") {
        override def onScore(self : Card)(implicit g : Game) = $(ChooseOneEffect(Corp, "Meteor Mining: choose one", $(Alt("Gain 7 credits", $(gain(Corp, 7)))) ++
            (g.tags >= 2).$(Alt("Do 7 meat damage", $(meat(7)))) :+ Alt("No action", $)))
    }

    object Standoff extends RAg("12077") {
        override def onScore(self : Card)(implicit g : Game) = $(RaStandoffAction(self, Runner))
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case RaStandoffAction(self, Runner) =>
                val l = g.runnerInstalled
                val reward : $[ForcedAction] = $(MessageEffect(Corp, "uses Standoff to draw 1 card and gain 5 credits"), draw(Corp, 1), gain(Corp, 5))
                if (l.any)
                    g.push(ChooseOneEffect(Runner, "Standoff: trash 1 of your installed cards?", $(
                        Alt("Trash an installed card", $(ChooseCardEffect(Runner, l, TrashPurpose(Runner), "Standoff: choose an installed card to trash", false), RaStandoffAction(self, Corp))),
                        Alt("Do not trash", reward))))
                else
                    g.pushAll(reward)
                NextAction
            case RaStandoffAction(self, Corp) =>
                val l = g.installedCards(Corp)
                if (l.any)
                    g.push(ChooseOneEffect(Corp, "Standoff: trash 1 of your installed cards to repeat the process?", $(
                        Alt("Trash an installed card", $(ChooseCardEffect(Corp, l, TrashPurpose(Corp), "Standoff: choose an installed card to trash", false), RaStandoffAction(self, Runner))),
                        Alt("Do not trash", $))))
                NextAction
        }
    }

    object MandatorySeedReplacement extends RAg("12092") {
        override def onScore(self : Card)(implicit g : Game) = $(RaMsrAction(self, 0))
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case RaMsrAction(self, k) =>
                // "rearrange any number of ice": swaps until the Corp stops (the bound only stops endless bot loops)
                if (g.allIce.num >= 2 && k < 3 * g.allIce.num)
                    g.push(ChooseCardsEffect(Corp, g.allIce, 0, 2, RaMsrPurpose(self, k), "Mandatory Seed Replacement: choose 2 pieces of ice to swap (none to finish)"))
                NextAction
        }
        override def chosenCard(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case RaMsrPurpose(self, k) =>
                cards match {
                    case List(x, y) => g.push(RotSwapIceEffect(x, y), RaMsrAction(self, k + 1))
                    case _ => g.log(Corp, "has finished rearranging ice")
                }
                NextAction
        }
    }

    object WaterMonopoly extends RAg("12093") {
        override def installCostMod(self : Card, card : Card)(implicit g : Game) = (corpScored(self) && card.data.isResource && !g.has(card, "Virtual")).?(1).|(0)
    }

    object CFCExcavationContract extends RAg("12110") {
        override def onScore(self : Card)(implicit g : Game) = $(gain(Corp, 2 * g.installedCards(Corp).count(c => g.isRezzed(c) && g.has(c, "Bioroid"))))
    }

    object AREnhancedSecurity extends RAg("12115") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Trashed(c, Runner, _) if corpScored(self) && g.owner(c).isCorp && g.reg("runner-trash-corp") == 1 => $(MessageEffect(Corp, "uses AR-Enhanced Security"), tag(1))
            case _ => $
        }
    }

    // ---- Kitara / Terminal Directive ------------------------------------------------------------------------------------

    object BrainRewiring extends RAg("13029") {
        override def onScore(self : Card)(implicit g : Game) = (g.credits(Corp) > 0 && g.hand(Runner).any).$(OptionalEffect(Corp,
            "Brain Rewiring: pay credits to add random cards from the grip to the bottom of the stack?", $(RaBrainRewiringAction(self)), $))
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case RaBrainRewiringAction(self) =>
                g.push(ChooseNumberEffect(Corp, self, 0, g.credits(Corp).atMost(g.hand(Runner).num), "brain-rewiring", "Brain Rewiring: how many credits do you pay?"))
                NextAction
            case NumberPickedAction(self, n, _) =>
                if (n > 0) {
                    g.log(Corp, "pays", n.credits, "for Brain Rewiring")
                    g.push(LoseCreditsEffect(Corp, n), RandomToBottomEffect(Runner, n), draw(Runner, 1))
                }
                NextAction
        }
    }

    object ElectiveUpgrade extends RAg("13030") {
        override def onScore(self : Card)(implicit g : Game) = agendaCounters(self, 2)
        override def abilities(self : Card)(implicit g : Game) = corpScored(self).??($(Ability("gain [click][click]", $(ClickCost(1), CounterCost("agenda", 1)), $(GainClicksEffect(Corp, 2)), click = true, once = once("elective-upgrade"))))
    }

    object ArmoredServers extends RAg("13042") {
        def active(self : Card)(implicit g : Game) = corpScored(self) && g.run.exists(r => !r.ended) && g.state(self).memo == g.rotRunKey
        override def onScore(self : Card)(implicit g : Game) = agendaCounters(self, 1)
        override def abilities(self : Card)(implicit g : Game) = corpScored(self).??($(Ability("the Runner must trash a card from the grip to jack out or break a subroutine this run", $(CounterCost("agenda", 1)),
            $(SetMemoEffect(self, g.rotRunKey), MessageEffect(Corp, "uses Armored Servers: jacking out or breaking a subroutine costs a card from the grip for the remainder of the run")),
            ok = g.run.exists(r => !r.ended) && !active(self), reason = "only during a run")))
        override def jackOutCosts(self : Card)(implicit g : Game) = active(self).$("grip")
        // the grip cards are an additional cost paid before each break (FinalRotRules.frArmoredAsk)
        override def breakAllowed(self : Card, breaker : Card, ice : Card)(implicit g : Game) = !active(self) || g.hand(Runner).any
    }

    object IllicitSales extends RAg("13043") {
        override def onScore(self : Card)(implicit g : Game) = $(OptionalEffect(Corp, "Illicit Sales: take 1 bad publicity?", $(BadPubEffect(1)), $), RaIllicitSalesAction(self))
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case RaIllicitSalesAction(self) =>
                g.push(gain(Corp, 3 * g.badPublicity))
                NextAction
        }
    }

    object Graft extends RAg("13044") {
        override def onScore(self : Card)(implicit g : Game) = g.deck(Corp).any.$(
            ChooseCardsEffect(Corp, g.deck(Corp).sortBy(_.title), 0, 3, AddToHandPurpose(Corp, true), "Graft: choose up to 3 cards in R&D to reveal and add to HQ"), ShuffleDeckEffect(Corp))
    }

    // ---- Terminal Directive Campaign (not legal in eternal) ---------------------------------------------------------------

    object EvidenceCollection extends RAg("14000")
    object EvidenceCollection2 extends RAg("14001")
    object EvidenceCollection3 extends RAg("14002")
    object EvidenceCollection4 extends RAg("14003") {
        override def agendaPointsBonus(self : Card)(implicit g : Game) = (g.zone(self) == ScoredZone(Runner)).?(-1).|(0)
    }

    abstract class CorporateOversight(code : String, remote : Boolean) extends RAg(code) {
        override def onScore(self : Card)(implicit g : Game) = $(OptionalEffect(Corp, "Corporate Oversight: search R&D for a piece of ice to install and rez protecting a " + remote.?("remote").|("central") + " server?",
            $(RaOversightAction(self, remote)), $))
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case RaOversightAction(self, r) =>
                val l = g.deck(Corp).%(_.data.isIce).sortBy(_.title)
                if (l.any)
                    g.push(ChooseCardEffect(Corp, l, RaOversightPurpose(self, r), "Corporate Oversight: choose a piece of ice", false))
                else
                    g.push(ShuffleDeckEffect(Corp))
                NextAction
        }
        override def chosenCard(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case RaOversightPurpose(self, r) =>
                cards.single.foreach(c => g.push(ChooseServerEffect(Corp, c, true, r.?(g.remotes : $[Server]).|(Server.centrals), r), RezEffect(c, true)))
                g.push(ShuffleDeckEffect(Corp))
                NextAction
        }
    }

    object CorporateOversightA extends CorporateOversight("14012", true)
    object CorporateOversightB extends CorporateOversight("14013", false)

    // ---- Revised Core Set -------------------------------------------------------------------------------------------------

    object ProjectAres extends RAg("20062") {
        override def onScore(self : Card)(implicit g : Game) = {
            val k = (g.lastScoredAdvancement - 4).atMost(g.runnerInstalled.num)
            (k > 0).$(ChooseCardsEffect(Runner, g.runnerInstalled, k, k, TrashPurpose(Runner), "Project Ares: choose " + k + " installed card" + (k > 1).??("s") + " to trash"), BadPubEffect(1))
        }
    }

    object TheCleaners extends RAg("20080") {
        override def meatBonus(self : Card)(implicit g : Game) = corpScored(self).?(1).|(0)
    }

    object Braintrust extends RAg("20094") {
        override def onScore(self : Card)(implicit g : Game) = agendaCounters(self, (g.lastScoredAdvancement - 3).atLeast(0) / 2)
        override def rezCostMod(self : Card, card : Card)(implicit g : Game) = (corpScored(self) && card.data.isIce).?(-g.counter(self, "agenda")).|(0)
    }

    object TGTBT extends RAg("20111") {
        override def onAccess(self : Card)(implicit g : Game) = $(MessageEffect(Corp, "uses TGTBT"), tag(1))
        override def onArchivesAccess(self : Card)(implicit g : Game) = $(MessageEffect(Corp, "uses TGTBT"), tag(1))
    }

    object PrivateSecurityForce extends RAg("20126") {
        override def abilities(self : Card)(implicit g : Game) = corpScored(self).??($(Ability("do 1 meat damage", click(), $(meat(1)), click = true, ok = g.tagged, reason = "the Runner is not tagged")))
    }

    // ---- Kitara cycle -----------------------------------------------------------------------------------------------------

    object IkawahProject extends RAg("21010") {
        override def accessCostPaidByRunner(self : Card)(implicit g : Game) = $(ClickCost(1), CreditCost(2))
    }

    object BacterialProgramming extends RAg("21033") {
        def ability(self : Card)(implicit g : Game) : $[ForcedAction] = g.deck(Corp).any.$(OptionalEffect(Corp, "Bacterial Programming: look at the top 7 cards of R&D?", $(RaBacterialAction(self)), $))
        override def onScore(self : Card)(implicit g : Game) = ability(self)
        override def onSteal(self : Card)(implicit g : Game) = ability(self)
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case RaBacterialAction(self) =>
                val top = g.deck(Corp).take(7)
                g.log(Corp, "looks at the top", top.num.cards, "of R&D")
                g.push(LookAtEffect(Corp, top, "Bacterial Programming: the top cards of R&D (top first)"),
                    ChooseCardsEffect(Corp, top, 0, top.num, RaBacterialTrashPurpose(self, top), "Bacterial Programming: choose any number of cards to trash"))
                NextAction
        }
        override def chosenCard(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case RaBacterialTrashPurpose(self, top) =>
                cards.foreach(c => g.trashCard(c, Corp, false))
                val rest = top.%(c => g.zone(c) == DeckZone(Corp))
                if (rest.any)
                    g.push(ChooseCardsEffect(Corp, rest, 0, rest.num, RaBacterialHandPurpose(self, rest), "Bacterial Programming: choose any number of cards to add to HQ"))
                NextAction
            case RaBacterialHandPurpose(self, rest) =>
                cards.foreach(c => g.moveTo(c, HandZone(Corp)))
                if (cards.any)
                    g.log(Corp, "adds", cards.num.cards, "to HQ")
                val left = rest.%(c => g.zone(c) == DeckZone(Corp))
                if (left.num > 1)
                    g.push(ArrangeTopEffect(Corp, Corp, left.num))
                NextAction
        }
    }

    object SSLEndorsement extends RAg("21038") {
        override def onScore(self : Card)(implicit g : Game) = $(AddCounterEffect(self, "credit", 9))
        override def onSteal(self : Card)(implicit g : Game) = $(AddCounterEffect(self, "credit", 9))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) if scoredAnywhere(self) && g.counter(self, "credit") > 0 =>
                $(OptionalEffect(Corp, "SSL Endorsement: take 3 credits?", $(TakeCreditsEffect(Corp, self, 3)), $))
            case _ => $
        }
    }

    object DegreeMill extends RAg("21055") {
        override def stealShuffleInstalled(self : Card, agenda : Card)(implicit g : Game) = (agenda == self).?(2).|(0)
    }

    object ArmedIntimidation extends RAg("21057") {
        override def onScore(self : Card)(implicit g : Game) = $(ChooseOneEffect(Runner, "Armed Intimidation: choose one", $(Alt("Suffer 5 meat damage", $(meat(5))), Alt("Take 2 tags", $(tag(2))))))
    }

    object CityWorksProject extends RAg("21078") {
        override def onInstall(self : Card)(implicit g : Game) = $(FaceupInstallEffect(self))
        override def onAccess(self : Card)(implicit g : Game) = g.isInstalled(self).$(MessageEffect(Corp, "uses City Works Project"), meat(2 + g.advancement(self)))
    }

    object RemoteEnforcement extends RAg("21091") {
        override def onScore(self : Card)(implicit g : Game) = $(OptionalEffect(Corp, "Remote Enforcement: search R&D for a piece of ice to install protecting a remote server?", $(RaRemoteEnforcementAction(self)), $))
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case RaRemoteEnforcementAction(self) =>
                val l = g.deck(Corp).%(_.data.isIce).sortBy(_.title)
                if (l.any)
                    g.push(ChooseCardEffect(Corp, l, RaRemoteEnforcementPurpose(self), "Remote Enforcement: choose a piece of ice", false))
                else
                    g.push(ShuffleDeckEffect(Corp))
                NextAction
        }
        override def chosenCard(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case RaRemoteEnforcementPurpose(self) =>
                cards.single.foreach { c =>
                    val l = g.remotes.%(s => g.installCost(c, |(s)) <= g.credits(Corp))
                    g.push(ChooseServerEffect(Corp, c, false, l, true), RezEffect(c, true))
                }
                g.push(ShuffleDeckEffect(Corp))
                NextAction
        }
    }

    object ViralWeaponization extends RAg("21094") {
        override def onScore(self : Card)(implicit g : Game) = $(SetMemoEffect(self, "vw:" + g.halfTurn))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnEnds(_) if corpScored(self) && g.state(self).memo == "vw:" + g.halfTurn => $(SetMemoEffect(self, ""), RaViralAction(self))
            case _ => $
        }
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case RaViralAction(self) =>
                g.log(Corp, "uses Viral Weaponization")
                g.push(net(g.hand(Runner).num))
                NextAction
        }
    }

    object BetterCitizenProgram extends RAg("21116") {
        def offer(self : Card)(implicit g : Game) : $[ForcedAction] = $(MarkUsedEffect(self, "bcp"), OptionalEffect(Corp, "Better Citizen Program: give the Runner 1 tag?", $(tag(1)), $))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Played(Runner, c) if corpScored(self) && g.has(c, "Run") && !g.usedThisTurn(self, "bcp") => offer(self)
            case Installed(Runner, c) if corpScored(self) && g.isIcebreaker(c) && !g.usedThisTurn(self, "bcp") => offer(self)
            case _ => $
        }
    }

    // ---- Reign and Reverie ------------------------------------------------------------------------------------------------

    object HyperloopExtension extends RAg("22027") {
        override def onScore(self : Card)(implicit g : Game) = $(gain(Corp, 3))
        override def onSteal(self : Card)(implicit g : Game) = $(gain(Corp, 3))
    }

    object Jumon extends RAg("22035") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnEnds(Corp) if corpScored(self) && g.remotes.exists(s => g.root(s).any) =>
                $(ChooseCardEffect(Corp, g.remotes./~(s => g.root(s)), AdvancePurpose(2), "Jumon: place 2 advancement counters on a card in the root of a remote server", false))
            case _ => $
        }
    }

    object FlyOnTheWall extends RAg("22043") {
        override def onScore(self : Card)(implicit g : Game) = $(tag(1))
    }

    object BroadDaylight extends RAg("22051") {
        override def onScore(self : Card)(implicit g : Game) = $(OptionalEffect(Corp, "Broad Daylight: take 1 bad publicity?", $(BadPubEffect(1)), $), RaBroadDaylightAction(self))
        override def abilities(self : Card)(implicit g : Game) = corpScored(self).??($(Ability("do 2 meat damage", $(ClickCost(1), CounterCost("agenda", 1)), $(meat(2)), click = true, once = once("broad-daylight"))))
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case RaBroadDaylightAction(self) =>
                g.pushAll(agendaCounters(self, g.badPublicity))
                NextAction
        }
    }

    // ---- System Core 2019 / Magnum Opus -------------------------------------------------------------------------------------

    object SuccessfulFieldTest extends RAg("25069") {
        override def onScore(self : Card)(implicit g : Game) = {
            val k = g.hand(Corp).count(c => corpInstallable(c))
            (k > 0).$(InstallLoopAction(self, Corp, k, 0, true, "hand", $))
        }
    }

    object FetalAI extends RAg("25086") {
        override def accessCostPaidByRunner(self : Card)(implicit g : Game) = $(CreditCost(2))
        override def onAccess(self : Card)(implicit g : Game) = $(MessageEffect(Corp, "uses Fetal AI"), net(2))
    }

    object PhiloticEntanglement extends RAg("25088") {
        override def onScore(self : Card)(implicit g : Game) = g.scored(Runner).any.$(net(g.scored(Runner).num))
    }

    object ExplodeAPalooza extends RAg("25106") {
        def offer(implicit g : Game) : $[ForcedAction] = $(OptionalEffect(Corp, "Explode-a-palooza: gain 5 credits?", $(gain(Corp, 5)), $))
        override def onAccess(self : Card)(implicit g : Game) = offer
        override def onArchivesAccess(self : Card)(implicit g : Game) = offer
    }

    object PaperTrail extends RAg("25140") {
        override def onScore(self : Card)(implicit g : Game) = $(TraceEffect(6, $(RaPaperTrailAction(self)), $))
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case RaPaperTrailAction(self) =>
                val l = g.runnerInstalledOf("Resource").%(c => g.has(c, "Connection") || g.has(c, "Job"))
                g.log(Corp, "uses Paper Trail to trash all connection and job resources")
                g.push(TrashCardsEffect(l, Corp))
                NextAction
        }
    }

    object PriorityRequisition extends RAg("25141") {
        override def onScore(self : Card)(implicit g : Game) = {
            val l = g.allIce.%(c => !g.isRezzed(c))
            l.any.$(ChooseCardEffect(Corp, l, RezPurpose(true), "Priority Requisition: rez a piece of ice, ignoring all costs?", true))
        }
    }

    object TimelyPublicRelease extends RAg("28006") {
        def candidates(implicit g : Game) = (g.hand(Corp) ++ g.discard(Corp)).%(_.data.isIce)
        override def onScore(self : Card)(implicit g : Game) = agendaCounters(self, 1)
        override def abilities(self : Card)(implicit g : Game) = corpScored(self).??($(Ability("install a piece of ice from HQ or Archives in any position, ignoring all costs", $(CounterCost("agenda", 1)),
            $(ChooseCardEffect(Corp, candidates, RaTprPurpose(self), "Timely Public Release: choose a piece of ice", false)), ok = candidates.any, reason = "no ice in HQ or Archives")))
        override def chosenCard(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case RaTprPurpose(self) =>
                cards.single.foreach(c => g.push(ChooseServerForAction(Corp, self, g.servers, true, "tpr:" + c.code + ":" + c.n, "Timely Public Release: choose a server")))
                NextAction
        }
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case ServerPickedAction(self, server, key) if key.startsWith("tpr:") =>
                val parts = key.split(':')
                val c = Card(parts(1), parts(2).toInt)
                server match {
                    case Some(s) if g.ice(s).any =>
                        g.push(ChooseNumberEffect(Corp, self, 0, g.ice(s).num, "tprpos:" + c.code + ":" + c.n + ":" + s.name, "Timely Public Release: position (0 is innermost)"))
                    case _ =>
                        g.push(CorpInstallEffect(c, server, true))
                }
                NextAction
            case NumberPickedAction(self, k, key) if key.startsWith("tprpos:") =>
                val parts = key.split(':')
                val c = Card(parts(1), parts(2).toInt)
                g.serverByName(parts(3)).foreach(s => g.push(CorpInstallIceAtEffect(c, s, k)))
                NextAction
        }
    }

    val all : $[CardImpl] = $(
        AdvancedConceptHopper, RemoteDataFarm, NewConstruction, CorporateSalesTeam, VotingMachineInitiative, PersonalityProfiles,
        DedicatedNeuralNet, PuppetMaster, ImprovedProteinSource, Merger, CrisisManagement, ProjectKusanagi, ShowOfForce, SensorNetActivation,
        NetQuarantine, NEXTWave2, ObokataProtocol, EscalateVitriol, Reeducation, MeteorMining, Standoff, MandatorySeedReplacement,
        WaterMonopoly, CFCExcavationContract, AREnhancedSecurity, BrainRewiring, ElectiveUpgrade, ArmoredServers, IllicitSales, Graft,
        EvidenceCollection, EvidenceCollection2, EvidenceCollection3, EvidenceCollection4, CorporateOversightA, CorporateOversightB,
        ProjectAres, TheCleaners, Braintrust, TGTBT, PrivateSecurityForce, IkawahProject, BacterialProgramming, SSLEndorsement, DegreeMill,
        ArmedIntimidation, CityWorksProject, RemoteEnforcement, ViralWeaponization, BetterCitizenProgram, HyperloopExtension, Jumon,
        FlyOnTheWall, BroadDaylight, SuccessfulFieldTest, FetalAI, PhiloticEntanglement, ExplodeAPalooza, PaperTrail, PriorityRequisition,
        TimelyPublicRelease,
    )
}
