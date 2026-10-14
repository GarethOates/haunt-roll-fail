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
// Rotated ice, titles M - Z (and Bullfrog) (see cards-rot-ice.scala)
// ---------------------------------------------------------------------------------------------------------------------

object RotIceCards2 {
    import RotIceCards1.{Morph, Grail, Constellation}

    object Bullfrog extends RI("02073") {
        override def subroutines(self : Card)(implicit g : Game) = $(psi("move this ice to the outermost position of another server; the run continues there", $(RotSubAction(self, 1))))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case RotSubAction(self, 1) =>
                val l = g.servers.%(s => !thisServer(self, s))
                if (g.isInstalled(self) && l.any)
                    g.push(ChooseServerForAction(Corp, self, l, false, "bullfrog", "Bullfrog: move it to the outermost position of"))
                NextAction
            case ServerPickedAction(self, Some(s), "bullfrog") =>
                g.push(MoveIceOutermostEffect(self, s), RotServerAction(self, s, 1))
                NextAction
            case ServerPickedAction(_, _, _) => NextAction
            case RotServerAction(self, s, 1) =>
                if (g.run.exists(r => !r.ended && r.ice.has(self)))
                    g.push(RedirectRunEffect(s, g.ice(s).indexOf(self) + 1, false))
                NextAction
        }
    }

    object MachicolationA extends RI("14010") {
        override def subroutines(self : Card)(implicit g : Game) = $(trashProgramSub, trashProgramSub, trashHardwareSub,
            Sub("The Runner loses 3[c], if able. End the run", RotSubAction(self, 1)))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case RotSubAction(self, 1) =>
                if (g.credits(Runner) >= 3) g.push(lose(Runner, 3), EndRunEffect) else g.push(EndRunEffect)
                NextAction
        }
    }

    object MachicolationB extends RI("14011") {
        override def subroutines(self : Card)(implicit g : Game) = $(trashResourceSub, trashResourceSub, netSub(1),
            Sub("The Runner loses [click], if able. End the run", CopyAbilityEffect($(LoseClicksEffect(Runner, 1), EndRunEffect))))
    }

    object Macrophage extends RI("11119") {
        override def subroutines(self : Card)(implicit g : Game) = $(
            trace(4, "Purge virus counters", $(PurgeEffect)),
            trace(3, "Trash a virus", $(ChooseCardEffect(Corp, g.installedCards(Runner).%(c => g.has(c, "Virus")), TrashPurpose(Corp), "Macrophage: choose a virus to trash", false))),
            trace(2, "Remove a virus in the heap from the game", $(ChooseCardEffect(Corp, g.discard(Runner).%(c => g.has(c, "Virus")).sortBy(_.title), RemoveFromGamePurpose, "Macrophage: choose a virus in the heap", true))),
            trace(1, "End the run", $(EndRunEffect)))
    }

    object Mamba extends RI("06065") {
        override def abilities(self : Card)(implicit g : Game) = $(powerAbility(self, "do 1 net damage", $(net(1)), g.run.exists(!_.ended), "only during a run"))
        override def subroutines(self : Card)(implicit g : Game) = $(netSub(1), psi("place 1 power counter on this ice", $(AddCounterEffect(self, "power", 1))))
    }

    // Marker: the next ice encountered this run gains "End the run" (target remembered until the run ends)
    object Marker extends RI("04116") {
        override def subroutines(self : Card)(implicit g : Game) = $(Sub("Give the next piece of ice the Runner encounters \"End the run\" for the remainder of the run", CopyAbilityEffect($(SetTargetEffect(self, None), SetMemoEffect(self, "armed:" + g.runKeyString)))))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case EncounterIce(c) if memoIs(self, "armed:" + g.runKeyString) && g.isRezzed(c) => $(SetMemoEffect(self, "run:" + g.runKeyString), SetTargetEffect(self, |(c)), MessageEffect(Corp, "uses Marker: the encountered ice gains End the run"))
            case RunEnds(_, _, _) if g.state(self).memo != "" || g.state(self).target.any => $(clearMemo(self), SetTargetEffect(self, None))
            case _ => $
        }
        override def extraSubroutines(self : Card, ice : Card)(implicit g : Game) = (memoIs(self, "run:" + g.runKeyString) && g.state(self).target.has(ice)).$(etrSub)
    }

    object Markus1 extends RI("06104") {
        override def subroutines(self : Card)(implicit g : Game) = $(runnerTrashInstalledSub, etrSub)
        override def runnerAbilities(self : Card)(implicit g : Game) = bioroid(self, 1)
    }

    object Masvingo extends RI("21099") {
        override def advanceable(self : Card)(implicit g : Game) = true
        override def onRez(self : Card)(implicit g : Game) = $(PlaceAdvancementEffect(self, 1))
        override def subroutines(self : Card)(implicit g : Game) = perAdvancement(self, etrSub)
    }

    object MatrixAnalyzer extends RI("01089") {
        override def onEncounter(self : Card)(implicit g : Game) =
            (g.credits(Corp) >= 1 && g.advanceableCards.any).$(OptionalEffect(Corp, "Matrix Analyzer: pay 1 credit to place 1 advancement counter on a card that can be advanced?", $(lose(Corp, 1), ChooseCardEffect(Corp, g.advanceableCards, AdvancePurpose(1), "Matrix Analyzer: choose a card", false)), $))
        override def subroutines(self : Card)(implicit g : Game) = $(tagTrace(2))
    }

    object Mausolus extends RI("11097") {
        def wonder(self : Card)(implicit g : Game) = g.advancement(self) >= 3
        override def advanceable(self : Card)(implicit g : Game) = true
        override def subroutines(self : Card)(implicit g : Game) = wonder(self).?($(
            gainSub(3), netSub(3), Sub("Give the Runner 1 tag and end the run", CopyAbilityEffect($(tag(1), EndRunEffect))))).|($(
            gainSub(1), netSub(1), tagSub(1)))
    }

    object Meridian extends RI("22028") {
        override def subroutines(self : Card)(implicit g : Game) = $(Sub("Gain 4[c] and end the run unless the Runner adds this ice to their score area as an agenda worth -1 point", choose("choose one", $(
            Alt("The Corp gains 4 credits and ends the run", $(gain(Corp, 4), EndRunEffect)),
            Alt("Add Meridian to your score area (-1 agenda point)", $(AddToScoreAreaEffect(self, Runner)), g.isInstalled(self))))))
        override def agendaPointsBonus(self : Card)(implicit g : Game) = (g.zone(self) == ScoredZone(Runner)).?(-1).|(0)
    }

    object Merlin extends Grail("06091") {
        def ownSub(self : Card)(implicit g : Game) = netSub(2)
    }

    object MeruMati extends RI("08039") {
        override def strength(self : Card)(implicit g : Game) = protecting(self, HQ).?(3).|(0)
        override def subroutines(self : Card)(implicit g : Game) = $(etrSub)
    }

    object Metamorph extends RI("12094") {
        def nonIce(implicit g : Game) = g.installedCards(Corp).%(c => !c.data.isIce && g.zone(c).is[RootZone])
        override def subroutines(self : Card)(implicit g : Game) = $(Sub("Swap 2 pieces of ice or swap 2 installed non-ice cards", ChooseOneEffect(Corp, "Metamorph: choose one", $(
            Alt("Swap 2 pieces of ice", $(ChooseCardsEffect(Corp, g.allIce, 2, 2, RotIcePurpose(self, 1), "Metamorph: choose 2 pieces of ice to swap")), g.allIce.num >= 2),
            Alt("Swap 2 installed non-ice cards", $(ChooseCardsEffect(Corp, nonIce, 2, 2, RotIcePurpose(self, 2), "Metamorph: choose 2 installed non-ice cards to swap")), nonIce./~(g.serverOf).distinct.num >= 2)))))
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards match {
                case List(a, b) => p match {
                    case RotIcePurpose(_, 1) => g.push(SwapIceEffect(a, b))
                    case _ => g.push(SwapRootEffect(a, b))
                }
                case _ =>
            }
            NextAction
        }
    }

    object Mganga extends RI("21013") {
        override def subroutines(self : Card)(implicit g : Game) = $(Sub("Psi game: do 2 net damage (1 net damage if the bids are equal); trash this ice",
            PsiEffect($(net(1), TrashEffect(self, Corp, false)), $(net(2), TrashEffect(self, Corp, false)))))
    }

    // Mind Game: the additional jack out cost applies to every jack out for the remainder of the run (run flag, FixRules)
    object MindGame extends RI("11092") {
        override def subroutines(self : Card)(implicit g : Game) = $(psi("redirect the run to another server", $(RotSubAction(self, 1))))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case RotSubAction(self, 1) =>
                g.runServer.foreach { cur =>
                    g.push(ChooseServerForAction(Corp, self, g.servers.but(cur), false, "mind-game", "Mind Game: redirect the run to"))
                }
                NextAction
            case ServerPickedAction(self, Some(s), "mind-game") =>
                g.push(AddFlagEffect(g.runFlag("mind-game-jack-out", |(self))), JackOutOfferEffect, RotServerAction(self, s, 1))
                NextAction
            case ServerPickedAction(_, _, _) => NextAction
            case RotServerAction(self, s, 1) =>
                g.push(RedirectRunEffect(s, g.ice(s).num, true))
                NextAction
        }
    }

    object Minelayer extends RI("03018") {
        override def subroutines(self : Card)(implicit g : Game) = $(Sub("Install 1 piece of ice from HQ protecting this server, ignoring all costs",
            ChooseCardEffect(Corp, g.hand(Corp).%(_.data.isIce), InstallPurpose(true, g.runServer.||(g.serverOf(self))), "Minelayer: choose a piece of ice to install", true)))
    }

    object Miraju extends RI("12071") {
        override def subroutines(self : Card)(implicit g : Game) = $(Sub("You may draw 1 card, then shuffle 1 card from HQ into R&D", CopyAbilityEffect($(OptionalEffect(Corp, "Miraju: draw 1 card?", $(draw(Corp, 1)), $), RotSubAction(self, 1)))))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SubsBroken(c, l, _) if c == self && l.has(0) => $(SetMemoEffect(self, "broken"))
            case EncounterEnds(c) if c == self && memoIs(self, "broken") =>
                $(clearMemo(self), MessageEffect(Corp, "uses Miraju: the Runner continues the run on Archives"), JackOutOfferEffect, DerezEffect(self), RotSubAction(self, 2))
            case _ => $
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case RotSubAction(self, 1) =>
                if (g.hand(Corp).any)
                    g.push(ChooseCardEffect(Corp, g.hand(Corp), ShuffleIntoDeckPurpose(Corp), "Miraju: choose a card in HQ to shuffle into R&D", false))
                NextAction
            case RotSubAction(self, 2) =>
                if (g.run.exists(r => !r.ended && r.server != Archives && r.forced.none))
                    g.push(RedirectRunEffect(Archives, g.ice(Archives).num, true))
                NextAction
        }
    }

    object Mlinzi extends RI("21115") {
        def s(n : Int, m : Int)(implicit g : Game) = Sub("Do " + n + " net damage unless the Runner trashes the top " + m + " cards of the stack", choose("choose one", $(
            Alt("Suffer " + n + " net damage", $(net(n))),
            Alt("Trash the top " + m + " cards of the stack", $(TrashTopEffect(Runner, m, false)), g.deck(Runner).num >= m))))
        override def subroutines(self : Card)(implicit g : Game) = $(s(1, 2), s(2, 3), s(3, 4))
    }

    // Mother Goddess: the subtypes of the other rezzed ice are kept in sync on every event while it is rezzed
    object MotherGoddess extends RI("06010") {
        def wanted(self : Card)(implicit g : Game) = g.rezzedIce.but(self)./~(c => c.data.subtypes).distinct.%!(Cards(code).subtypes.has)
        override def triggers(self : Card, e : Event)(implicit g : Game) = {
            val cur = g.state(self).extraSubtypes
            val w = wanted(self)
            (cur.toSet != w.toSet).$(ExtraSubtypesEffect(self, cur, false), ExtraSubtypesEffect(self, w, true))
        }
        override def subroutines(self : Card)(implicit g : Game) = $(etrSub)
    }

    object Muckraker extends RI("04035") {
        override def onRez(self : Card)(implicit g : Game) = takeBadPub
        override def subroutines(self : Card)(implicit g : Game) = $(tagTrace(1), tagTrace(2), tagTrace(3), etrIfTagged)
    }

    object NEXTBronze extends RI("29009") {
        override def strength(self : Card)(implicit g : Game) = g.nextIceCount
        override def subroutines(self : Card)(implicit g : Game) = $(etrSub)
    }

    object NEXTDiamond extends RI("21112") {
        override def rezCostMod(self : Card, card : Card)(implicit g : Game) = (card == self).?(-g.nextIceCount).|(0)
        override def subroutines(self : Card)(implicit g : Game) = $(coreSub(1), coreSub(1), trashInstalledSub)
    }

    object NEXTGold extends RI("08011") {
        override def subroutines(self : Card)(implicit g : Game) = {
            val x = g.nextIceCount
            $(Sub("Do X net damage (X = " + x + ")", net(x)), Sub("Trash X programs (X = " + x + ")", CopyAbilityEffect(x.times(RunnerTrashInstalledEffect("Program", Corp, "NEXT Gold: choose a program to trash") : ForcedAction))))
        }
    }

    object NEXTOpal extends RI("12050") {
        override def subroutines(self : Card)(implicit g : Game) = g.nextIceCount.times(installSub("Install a card from HQ", g.hand(Corp), true)(self))
    }

    object NEXTSapphire extends RI("21050") {
        override def subroutines(self : Card)(implicit g : Game) = {
            val x = g.nextIceCount
            $(
                Sub("Draw up to X cards (X = " + x + ")", ChooseNumberEffect(Corp, self, 0, x.atMost(g.deck(Corp).num), "sapphire-draw", "NEXT Sapphire: how many cards to draw?")),
                Sub("Add up to X cards from Archives to HQ", ChooseCardsEffect(Corp, g.discard(Corp), 0, x, AddToHandPurpose(Corp, false), "NEXT Sapphire: choose up to " + x + " cards in Archives to add to HQ")),
                Sub("Shuffle up to X cards from HQ into R&D", CopyAbilityEffect($(ChooseCardsEffect(Corp, g.hand(Corp), 0, x, ShuffleIntoDeckPurpose(Corp), "NEXT Sapphire: choose up to " + x + " cards in HQ to shuffle into R&D"), ShuffleDeckEffect(Corp)))))
        }
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case NumberPickedAction(self, n, "sapphire-draw") =>
                if (n > 0) g.push(draw(Corp, n))
                NextAction
        }
    }

    object NEXTSilver extends RI("29010") {
        override def subroutines(self : Card)(implicit g : Game) = g.nextIceCount.times(etrSub)
    }

    object Najja1 extends RI("21011") {
        override def subroutines(self : Card)(implicit g : Game) = $(etrSub, etrSub)
        override def runnerAbilities(self : Card)(implicit g : Game) = bioroid(self, 1)
    }

    // space ice: can be advanced, rez cost lowered by 3 per advancement counter
    abstract class Space(code : String) extends RI(code) {
        override def advanceable(self : Card)(implicit g : Game) = true
        override def rezCostMod(self : Card, card : Card)(implicit g : Game) = (card == self).?(-3 * g.advancement(self)).|(0)
    }

    object Nebula extends Space("07014") {
        override def subroutines(self : Card)(implicit g : Game) = $(trashProgramSub)
    }

    object Negotiator extends RI("08019") {
        override def subroutines(self : Card)(implicit g : Game) = $(gainSub(2), trashProgramSub)
        override def runnerAbilities(self : Card)(implicit g : Game) = payBreak(self, 2)
    }

    object Nerine2 extends RI("12030") {
        def s(self : Card)(implicit g : Game) = Sub("Do 1 core damage. You may draw 1 card", CopyAbilityEffect($(core(1), OptionalEffect(Corp, "Nerine 2.0: draw 1 card?", $(draw(Corp, 1)), $))))
        override def subroutines(self : Card)(implicit g : Game) = $(s(self), s(self))
        override def runnerAbilities(self : Card)(implicit g : Game) = bioroid(self, 2)
    }

    object NeuralKatana extends RI("25095") {
        override def subroutines(self : Card)(implicit g : Game) = $(netSub(3))
    }

    object NewsHound extends RI("09014") {
        def currentActive(implicit g : Game) = (g.at(PlayZone(Corp)) ++ g.at(PlayZone(Runner))).exists(c => g.has(c, "Current"))
        override def subroutines(self : Card)(implicit g : Game) = $(tagTrace(3)) ++ currentActive.$(etrSub)
    }

    object Nightdancer extends RI("21030") {
        def s = Sub("The Runner loses [click], if able. You have an additional [click] next turn", CopyAbilityEffect($(LoseClicksEffect(Runner, 1), ExtraClicksEffect(Corp, 1))))
        override def subroutines(self : Card)(implicit g : Game) = $(s, s)
    }

    object Oduduwa extends RI("21079") {
        override def onEncounter(self : Card)(implicit g : Game) = $(PlaceAdvancementEffect(self, 1), RotSubAction(self, 1))
        override def subroutines(self : Card)(implicit g : Game) = $(etrSub, etrSub)
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case RotSubAction(self, 1) =>
                val x = g.advancement(self)
                val l = g.allIce.but(self)
                if (x > 0 && l.any)
                    g.push(ChooseCardEffect(Corp, l, AdvancePurpose(x), "Oduduwa: place " + x + " advancement counter" + (x != 1).??("s") + " on another piece of ice?", true))
                NextAction
        }
    }

    object Orion extends Space("07015") {
        override def subroutines(self : Card)(implicit g : Game) = $(trashProgramSub, Sub("Resolve a subroutine on another piece of ice", resolveSubOn(self, "", false)), etrSub)
    }

    object Otoroshi extends RI("22038") {
        override def subroutines(self : Card)(implicit g : Game) = $(Sub("Place 3 advancement counters on an installed card; the Runner accesses it unless they pay 3[c]",
            ChooseCardEffect(Corp, g.installedCards(Corp).%(c => !c.data.isIce && g.zone(c).is[RootZone]), RotIcePurpose(self, 1), "Otoroshi: choose an installed card", false)))
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach { c =>
                g.push(PlaceAdvancementEffect(c, 3), choose("choose one", $(
                    Alt("Access the card", $(AccessCardsEffect(g.serverOf(c).|(HQ), $(c), false))),
                    Alt("Pay 3 credits", $(lose(Runner, 3)), g.credits(Runner) >= 3))))
            }
            NextAction
        }
    }

    object Owl extends RI("12060") {
        override def subroutines(self : Card)(implicit g : Game) = $(programToStackSub(true))
    }

    object Pachinko extends RI("08076") {
        override def subroutines(self : Card)(implicit g : Game) = $(etrIfTagged, etrIfTagged)
    }

    object PaperWall extends RI("04059") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SubsBroken(c, _, _) if c == self && g.unbrokenSubs(self).none => $(MessageEffect(Corp, "trashes Paper Wall (all subroutines broken)"), TrashEffect(self, Corp, false))
            case _ => $
        }
        override def subroutines(self : Card)(implicit g : Game) = $(etrSub)
    }

    object PeepingTom extends RI("22045") {
        val kinds = $("Event", "Hardware", "Program", "Resource")
        override def onEncounter(self : Card)(implicit g : Game) = $(ChooseOneEffect(Corp, "Peeping Tom: name a card type", kinds./(k => Alt(k, $(RevealCardsEffect(Runner, g.hand(Runner)), SetMemoEffect(self, g.hand(Runner).%(_.data.kind == k).num.toString))))))
        override def subroutines(self : Card)(implicit g : Game) = memoInt(self).times(etrUnless("takes 1 tag", "Take 1 tag", $(tag(1))))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case RunEnds(_, _, _) if g.state(self).memo != "" => $(clearMemo(self))
            case _ => $
        }
    }

    object Pup extends RI("05018") {
        def s(implicit g : Game) = Sub("Do 1 net damage unless the Runner pays 1[c]", choose("choose one", $(Alt("Suffer 1 net damage", $(net(1))), Alt("Pay 1 credit", $(lose(Runner, 1)), g.credits(Runner) >= 1))))
        override def subroutines(self : Card)(implicit g : Game) = $(s, s)
    }

    object Quandary extends RI("04120") {
        override def subroutines(self : Card)(implicit g : Game) = $(etrSub)
    }

    object Quicksand extends RI("08060") {
        override def onEncounter(self : Card)(implicit g : Game) = $(AddCounterEffect(self, "power", 1))
        override def strength(self : Card)(implicit g : Game) = g.counter(self, "power")
        override def subroutines(self : Card)(implicit g : Game) = $(etrSub)
    }

    // RSVP: the run flag "no-spend-credits" blocks every credit payment of the Runner for the run (rules-ext-credits.scala)
    object RSVP extends RI("04077") {
        override def subroutines(self : Card)(implicit g : Game) = $(Sub("The Runner cannot spend any credits for the remainder of this run", CopyAbilityEffect($(MessageEffect(Corp, "uses RSVP: the Runner cannot spend credits for the remainder of this run"), AddFlagEffect(Flag("no-spend-credits", |(self), None, "run", 0, $))))))
    }

    object Rainbow extends RI("05025") {
        override def subroutines(self : Card)(implicit g : Game) = $(etrSub)
    }

    object RedTape extends RI("10093") {
        override def subroutines(self : Card)(implicit g : Game) = $(Sub("Give +3 strength to all ice for the remainder of the run", CopyAbilityEffect(g.allIce./(c => PumpIceEffect(c, 3, RunDuration) : ForcedAction))))
    }

    object Resistor extends RI("09015") {
        override def strength(self : Card)(implicit g : Game) = g.tags
        override def subroutines(self : Card)(implicit g : Game) = $(trace(4, "End the run", $(EndRunEffect)))
    }

    object SYNCBRE extends RI("12015") {
        override def subroutines(self : Card)(implicit g : Game) = $(tagTrace(4), trace(2, "The Runner accesses 1 fewer card this run", $(AccessBonusTotalEffect(-1))))
    }

    object Sadaka extends RI("21073") {
        override def subroutines(self : Card)(implicit g : Game) = $(
            Sub("Look at the top 3 cards of R&D; arrange them or shuffle R&D; you may draw 1 card", RotSubAction(self, 1)),
            Sub("Trash 1 card in HQ; if you do, trash 1 resource. Trash this ice", RotSubAction(self, 2)))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case RotSubAction(self, 1) =>
                val l = g.deck(Corp).take(3)
                if (l.any)
                    g.push(LookAtEffect(Corp, l, "Sadaka: the top cards of R&D"), ChooseOneEffect(Corp, "Sadaka: choose one", $(
                        Alt("Arrange the cards", $(ArrangeTopEffect(Corp, Corp, 3))),
                        Alt("Shuffle R&D", $(ShuffleDeckEffect(Corp))))),
                        OptionalEffect(Corp, "Sadaka: draw 1 card?", $(draw(Corp, 1)), $))
                NextAction
            case RotSubAction(self, 2) =>
                g.push(ChooseCardEffect(Corp, g.hand(Corp), RotIcePurpose(self, 1), "Sadaka: choose a card in HQ to trash", true), TrashEffect(self, Corp, false))
                NextAction
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach { c =>
                g.push(TrashEffect(c, Corp, false), RunnerTrashInstalledEffect("Resource", Corp, "Sadaka: choose a resource to trash"))
            }
            NextAction
        }
    }

    object Sagittarius extends Constellation("06082") {
        val label = "Trash 1 installed program"
        def effect(self : Card)(implicit g : Game) = RunnerTrashInstalledEffect("Program", Corp, "Sagittarius: choose a program to trash")
    }

    // zero-to-hero: can be advanced while rezzed, one subroutine per advancement counter
    abstract class ZeroToHero(code : String) extends RI(code) {
        override def advanceable(self : Card)(implicit g : Game) = g.isRezzed(self)
    }

    object Salvage extends ZeroToHero("02098") {
        override def subroutines(self : Card)(implicit g : Game) = perAdvancement(self, tagTrace(2))
    }

    object SandStorm extends RI("12114") {
        override def subroutines(self : Card)(implicit g : Game) = $(Sub("Move this ice and the run to another server; trash this ice", RotSubAction(self, 1)))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case RotSubAction(self, 1) =>
                g.runServer.foreach { cur =>
                    if (g.isInstalled(self))
                        g.push(ChooseServerForAction(Corp, self, g.servers.but(cur), false, "sand-storm", "Sand Storm: move it and the run to"))
                }
                NextAction
            case ServerPickedAction(self, Some(s), "sand-storm") =>
                g.push(MoveIceOutermostEffect(self, s), RotServerAction(self, s, 1))
                NextAction
            case ServerPickedAction(_, _, _) => NextAction
            case RotServerAction(self, s, 1) =>
                g.pushAll(g.run.exists(r => !r.ended && r.ice.has(self)).$(RedirectRunEffect(s, g.ice(s).indexOf(self) + 1, false)) :+ TrashEffect(self, Corp, false))
                NextAction
        }
    }

    object Sandman extends RI("21075") {
        override def subroutines(self : Card)(implicit g : Game) = $(addToGripSub, addToGripSub)
    }

    object Sapper extends RI("11098") {
        override def onAccess(self : Card)(implicit g : Game) = (!g.isInstalled(self) && !g.zone(self).is[DiscardZone]).$(PhantomEncounterEffect(self))
        override def subroutines(self : Card)(implicit g : Game) = $(trashProgramSub)
    }

    object Searchlight extends RI("07019") {
        override def advanceable(self : Card)(implicit g : Game) = true
        override def subroutines(self : Card)(implicit g : Game) = {
            val x = g.advancement(self)
            $(trace(x, "Give the Runner 1 tag", $(tag(1))), trace(x, "Give the Runner 1 tag", $(tag(1))))
        }
    }

    object SeidrAdaptiveBarrier extends RI("12029") {
        override def strength(self : Card)(implicit g : Game) = iceHere(self).num
        override def subroutines(self : Card)(implicit g : Game) = $(etrSub)
    }

    object SelfAdaptingCodeWall extends RI("12040") {
        override def cannotLowerStrength(self : Card)(implicit g : Game) = true
        override def subroutines(self : Card)(implicit g : Game) = $(etrSub)
    }

    // Sensei: every other ice encountered for the remainder of the run gains "End the run"
    object Sensei extends RI("02034") {
        override def subroutines(self : Card)(implicit g : Game) = $(Sub("Each piece of ice the Runner encounters for the remainder of this run gains \"End the run\"", SetMemoEffect(self, "run:" + g.runKeyString)))
        override def extraSubroutines(self : Card, ice : Card)(implicit g : Game) = (ice != self && memoIs(self, "run:" + g.runKeyString) && encountering(self).has(ice) && g.isRezzed(ice)).$(etrSub)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case RunEnds(_, _, _) if g.state(self).memo != "" => $(clearMemo(self))
            case _ => $
        }
    }

    object Shadow extends RI("20089") {
        override def advanceable(self : Card)(implicit g : Game) = true
        override def strength(self : Card)(implicit g : Game) = g.advancement(self)
        override def subroutines(self : Card)(implicit g : Game) = $(gainSub(2), tagTrace(3))
    }

    object Sherlock1 extends RI("02030") {
        override def subroutines(self : Card)(implicit g : Game) = 2.times(trace(4, "Add 1 installed program to the top of the stack", $(programToStackSub(true).effect)))
        override def runnerAbilities(self : Card)(implicit g : Game) = bioroid(self, 1)
    }

    object Sherlock2 extends RI("11011") {
        override def subroutines(self : Card)(implicit g : Game) = 2.times(trace(4, "Add 1 installed program to the bottom of the stack", $(programToStackSub(false).effect))) :+ tagSub(1)
        override def runnerAbilities(self : Card)(implicit g : Game) = bioroid(self, 2)
    }

    object Shinobi extends RI("04115") {
        override def onRez(self : Card)(implicit g : Game) = takeBadPub
        override def subroutines(self : Card)(implicit g : Game) = $(trace(1, "Do 1 net damage", $(net(1))), trace(2, "Do 2 net damage", $(net(2))), trace(3, "Do 3 net damage and end the run", $(net(3), EndRunEffect)))
    }

    object Shiro extends RI("05019") {
        override def subroutines(self : Card)(implicit g : Game) = $(
            Sub("Rearrange the top 3 cards of R&D", ArrangeTopEffect(Corp, Corp, 3)),
            Sub("The Runner breaches R&D unless you pay 1[c]", RotSubAction(self, 1)))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case RotSubAction(self, 1) =>
                if (g.credits(Corp) >= 1)
                    g.push(OptionalEffect(Corp, "Shiro: pay 1 credit to keep the Runner from breaching R&D?", $(lose(Corp, 1)), $(BreachServerEffect(RnD))))
                else
                    g.push(BreachServerEffect(RnD))
                NextAction
        }
    }

    // Slot Machine: the top card of the stack goes to the bottom, the top 3 are revealed and remembered for the subroutines
    object SlotMachine extends RI("28004") {
        override def onEncounter(self : Card)(implicit g : Game) = $(RotSubAction(self, 1))
        def kinds(self : Card)(implicit g : Game) = g.state(self).memo.split(',').toList.%(_ != "")./(c => Cards(c).kind)
        override def subroutines(self : Card)(implicit g : Game) = $(
            loseSub(3),
            Sub("Gain 3[c] if there are 2 or fewer card types among the revealed cards", RotSubAction(self, 2)),
            Sub("Place 3 advancement counters on an installed card if the revealed cards share 1 card type", RotSubAction(self, 3)))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case RotSubAction(self, 1) =>
                g.deck(Runner).starting.foreach(c => g.moveTo(c, DeckZone(Runner)))
                val l = g.deck(Runner).take(3)
                g.state(self).memo = l./(_.code).join(",")
                if (l.any)
                    g.log(Corp, "uses Slot Machine to move the top card of the stack to the bottom")
                g.push(RevealCardsEffect(Runner, l))
                NextAction
            case RotSubAction(self, 2) =>
                val k = kinds(self)
                if ((k.num == 3 && k.distinct.num <= 2) || (k.num == 2 && k.distinct.num == 1))
                    g.push(gain(Corp, 3))
                NextAction
            case RotSubAction(self, 3) =>
                val k = kinds(self)
                if (k.num == 3 && k.distinct.num == 1)
                    g.push(ChooseCardEffect(Corp, g.installedCards(Corp), AdvancePurpose(3), "Slot Machine: choose an installed card", false))
                NextAction
        }
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case EncounterEnds(c) if c == self && g.state(self).memo != "" => $(clearMemo(self))
            case RunEnds(_, _, _) if g.state(self).memo != "" => $(clearMemo(self))
            case _ => $
        }
    }

    object Snoop extends RI("04056") {
        override def onEncounter(self : Card)(implicit g : Game) = $(RevealCardsEffect(Runner, g.hand(Runner)))
        override def abilities(self : Card)(implicit g : Game) = $(powerAbility(self, "reveal the grip and trash 1 card from it", $(RevealCardsEffect(Runner, g.hand(Runner)), ChooseCardEffect(Corp, g.hand(Runner), TrashPurpose(Corp), "Snoop: choose a card in the grip to trash", false)), g.hand(Runner).any, "the grip is empty"))
        override def subroutines(self : Card)(implicit g : Game) = $(trace(3, "Place 1 power counter on this ice", $(AddCounterEffect(self, "power", 1))))
    }

    object Snowflake extends RI("02015") {
        override def subroutines(self : Card)(implicit g : Game) = $(psi("end the run", $(EndRunEffect)))
    }

    object SpecialOffer extends RI("09016") {
        override def subroutines(self : Card)(implicit g : Game) = $(Sub("Gain 5[c] and trash this ice", CopyAbilityEffect($(gain(Corp, 5), TrashEffect(self, Corp, false)))))
    }

    object Spiderweb extends RI("25135") {
        override def subroutines(self : Card)(implicit g : Game) = $(etrSub, etrSub, etrSub)
    }

    object Surveyor extends RI("21118") {
        def x(self : Card)(implicit g : Game) = 2 * iceHere(self).num
        override def strength(self : Card)(implicit g : Game) = x(self)
        override def subroutines(self : Card)(implicit g : Game) = $(trace(x(self), "Give the Runner 2 tags", $(tag(2))), trace(x(self), "End the run", $(EndRunEffect)))
    }

    object Susanoo extends RI("05020") {
        override def subroutines(self : Card)(implicit g : Game) = $(Sub("The Runner continues the run on Archives and cannot jack out until they encounter another piece of ice", RotSubAction(self, 1)))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case RotSubAction(self, 1) =>
                if (g.run.exists(r => !r.ended && r.server != Archives))
                    g.pushAll($(RunFlagEffect("cannot-jack-out", true), AddFlagEffect(g.runFlag("jack-until-encounter", None))) ++
                        g.run.exists(r => r.forced.none && r.phase != SuccessPhase).$(RedirectRunEffect(Archives, g.ice(Archives).num, true)))
                NextAction
        }
    }

    object Swarm extends RI("04018") {
        override def advanceable(self : Card)(implicit g : Game) = true
        override def onRez(self : Card)(implicit g : Game) = takeBadPub
        def s(implicit g : Game) = Sub("Trash 1 program unless the Runner pays 3[c]", choose("choose one", $(
            Alt("The Corp trashes a program", $(RunnerTrashInstalledEffect("Program", Corp, "Swarm: choose a program to trash"))),
            Alt("Pay 3 credits", $(lose(Runner, 3)), g.credits(Runner) >= 3))))
        override def subroutines(self : Card)(implicit g : Game) = perAdvancement(self, s)
    }

    // TL;DR: the next ice encountered this run has each of its (printed) subroutines twice during that encounter
    object TLDR extends RI("09017") {
        override def subroutines(self : Card)(implicit g : Game) = $(Sub("Duplicate each subroutine on the next piece of ice the Runner encounters this run", CopyAbilityEffect($(SetTargetEffect(self, None), SetMemoEffect(self, "armed:" + g.runKeyString)))))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case EncounterIce(c) if memoIs(self, "armed:" + g.runKeyString) => $(SetMemoEffect(self, "on"), SetTargetEffect(self, |(c)), MessageEffect(Corp, "uses TL;DR to duplicate each subroutine of the encountered ice"))
            case EncounterEnds(c) if g.state(self).target.has(c) => $(clearMemo(self), SetTargetEffect(self, None))
            case RunEnds(_, _, _) if g.state(self).memo != "" || g.state(self).target.any => $(clearMemo(self), SetTargetEffect(self, None))
            case _ => $
        }
        override def extraSubroutines(self : Card, ice : Card)(implicit g : Game) = (memoIs(self, "on") && g.state(self).target.has(ice)).??(g.impl(ice).subroutines(ice))
    }

    object TMI extends RI("02017") {
        override def onRez(self : Card)(implicit g : Game) = $(TraceEffect(2, $(MessageEffect(Corp, "keeps TMI rezzed")), $(DerezEffect(self))))
        override def subroutines(self : Card)(implicit g : Game) = $(etrSub)
    }

    object Tapestry extends RI("13037") {
        override def subroutines(self : Card)(implicit g : Game) = $(loseClickSub, maybeDrawSub,
            Sub("Add 1 card from HQ to the top of R&D", ChooseCardEffect(Corp, g.hand(Corp), TopOfDeckPurpose(Corp), "Tapestry: choose a card in HQ to add to the top of R&D", false)))
    }

    object Taurus extends Constellation("06009") {
        val label = "Trash 1 piece of hardware"
        def effect(self : Card)(implicit g : Game) = RunnerTrashInstalledEffect("Hardware", Corp, "Taurus: choose a piece of hardware to trash")
    }

    object Thimblerig extends RI("22039") {
        def offer(self : Card)(implicit g : Game) = (g.allIce.num >= 2).$(OptionalEffect(Corp, "Thimblerig: swap it with another piece of ice?", $(ChooseCardEffect(Corp, g.allIce.but(self), RotIcePurpose(self, 1), "Thimblerig: choose a piece of ice to swap with", false)), $))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case PassIce(c) if c == self => offer(self)
            case TurnBegins(Corp) => offer(self)
            case _ => $
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(c => g.push(SwapIceEffect(p.card, c)))
            NextAction
        }
        override def subroutines(self : Card)(implicit g : Game) = $(etrSub)
    }

    object Thoth extends RI("11095") {
        override def onEncounter(self : Card)(implicit g : Game) = $(tag(1))
        override def subroutines(self : Card)(implicit g : Game) = $(
            trace(4, "Do 1 net damage for each tag", $(RotSubAction(self, 1))),
            trace(4, "The Runner loses 1[c] for each tag", $(RotSubAction(self, 2))))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case RotSubAction(self, 1) => g.push(net(g.tags)) ; NextAction
            case RotSubAction(self, 2) => g.push(lose(Runner, g.tags)) ; NextAction
        }
    }

    // Tithonium: the alternative rez cost (forfeit an agenda) is not offered
    object Tithonium extends RI("12098") {
        override def subroutines(self : Card)(implicit g : Game) = $(trashProgramSub, trashProgramSub,
            Sub("Trash 1 resource and end the run", CopyAbilityEffect($(RunnerTrashInstalledEffect("Resource", Corp, "Tithonium: choose a resource to trash"), EndRunEffect))))
    }

    object TourGuide extends RI("08118") {
        override def subroutines(self : Card)(implicit g : Game) = g.rezzedAssets.num.times(etrSub)
    }

    object Tribunal extends RI("11120") {
        override def subroutines(self : Card)(implicit g : Game) = $(runnerTrashInstalledSub, runnerTrashInstalledSub, runnerTrashInstalledSub)
    }

    object Troll extends RI("06108") {
        override def onEncounter(self : Card)(implicit g : Game) = $(TraceEffect(2, $(RotSubAction(self, 1)), $))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case RotSubAction(self, 1) =>
                g.push(choose("choose one", $(Alt("Spend [click]", $(LoseClicksEffect(Runner, 1)), g.clicks(Runner) >= 1), Alt("End the run", $(EndRunEffect)))))
                NextAction
        }
    }

    object Tsurugi extends RI("25097") {
        override def subroutines(self : Card)(implicit g : Game) = $(
            Sub("End the run unless you pay 1[c]", ChooseOneEffect(Corp, "Tsurugi: choose one", $(Alt("Pay 1 credit", $(lose(Corp, 1)), g.credits(Corp) >= 1), Alt("End the run", $(EndRunEffect))))),
            netSub(1), netSub(1), netSub(1))
    }

    object Turing extends RI("25077") {
        override def canBreakWith(self : Card, breaker : Card)(implicit g : Game) = !g.has(breaker, "AI")
        override def strength(self : Card)(implicit g : Game) = g.serverOf(self).exists(_.isCentral).?(0).|(3)
        override def subroutines(self : Card)(implicit g : Game) = $(etrUnlessClicks(3))
    }

    object Turnpike extends RI("09018") {
        override def onEncounter(self : Card)(implicit g : Game) = $(lose(Runner, 1))
        override def subroutines(self : Card)(implicit g : Game) = $(tagTrace(5))
    }

    object Tyrant extends ZeroToHero("02078") {
        override def subroutines(self : Card)(implicit g : Game) = perAdvancement(self, etrSub)
    }

    object UniversalConnectivityFee extends RI("06067") {
        override def subroutines(self : Card)(implicit g : Game) = $(Sub("The Runner loses 1[c]; if tagged, all credits instead and trash this ice", RotSubAction(self, 1)))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case RotSubAction(self, 1) =>
                if (g.tagged) g.push(lose(Runner, g.credits(Runner)), TrashEffect(self, Corp, false)) else g.push(lose(Runner, 1))
                NextAction
        }
    }

    object Upayoga extends RI("10069") {
        override def subroutines(self : Card)(implicit g : Game) = $(psi("the Runner loses 2 credits", $(lose(Runner, 2))),
            Sub("Resolve a subroutine on a rezzed psi ice", resolveSubOn(self, "Psi", true)))
    }

    object Uroboros extends RI("02074") {
        override def subroutines(self : Card)(implicit g : Game) = $(
            trace(4, "The Runner cannot make another run this turn", $(MessageEffect(Corp, "uses Uroboros: the Runner cannot make another run this turn"), AddFlagEffect(g.turnFlag("no-run", None)))),
            trace(4, "End the run", $(EndRunEffect)))
    }

    object Vanilla extends RI("10095") {
        override def subroutines(self : Card)(implicit g : Game) = $(etrSub)
    }

    object Veritas extends RI("11116") {
        override def subroutines(self : Card)(implicit g : Game) = $(gainSub(2), runnerLosesSub(2), tagTrace(2))
    }

    object Vikram1 extends RI("10012") {
        override def subroutines(self : Card)(implicit g : Game) = $(
            Sub("The Runner cannot use programs for the remainder of this run", CopyAbilityEffect($(MessageEffect(Corp, "uses Vikram 1.0: the Runner cannot use programs for the remainder of this run"), AddFlagEffect(Flag("no-program-use", None, None, "run", 0, $))))),
            trace(4, "Do 1 core damage", $(core(1))), trace(4, "Do 1 core damage", $(core(1))))
        override def runnerAbilities(self : Card)(implicit g : Game) = bioroid(self, 1)
    }

    object Viktor1 extends RI("25078") {
        override def subroutines(self : Card)(implicit g : Game) = $(coreSub(1), etrSub)
        override def runnerAbilities(self : Card)(implicit g : Game) = bioroid(self, 1)
    }

    object Viktor2 extends RI("03019") {
        override def abilities(self : Card)(implicit g : Game) = $(powerAbility(self, "do 1 core damage", $(core(1))))
        override def subroutines(self : Card)(implicit g : Game) = $(trace(2, "Place 1 power counter on this ice", $(AddCounterEffect(self, "power", 1))), etrSub)
        override def runnerAbilities(self : Card)(implicit g : Game) = bioroid(self, 2)
    }

    object Viper extends RI("02052") {
        override def subroutines(self : Card)(implicit g : Game) = $(trace(3, "The Runner loses [click]", $(LoseClicksEffect(Runner, 1))), trace(3, "End the run", $(EndRunEffect)))
    }

    object Virgo extends Constellation("06109") {
        val label = "Give the Runner 1 tag"
        def effect(self : Card)(implicit g : Game) = tag(1)
    }

    // Waiver: the difference = trace strength - the Runner's link strength (link + boost, recorded by FixRules)
    object Waiver extends RI("10091") {
        override def subroutines(self : Card)(implicit g : Game) = $(Sub("Trace[5]: reveal the grip and trash each card with a cost lower than or equal to the difference", CopyAbilityEffect($(RememberCreditsEffect(self, Runner), TraceEffect(5, $(RotSubAction(self, 1)), $(clearMemo(self)))))))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case RotSubAction(self, 1) =>
                val delta = g.traceStrength - g.fxTraceLinkStrength
                val l = g.hand(Runner).%(c => c.data.cost.|(0) <= delta)
                g.push(RevealCardsEffect(Runner, g.hand(Runner)), TrashCardsEffect(l, Corp), clearMemo(self))
                NextAction
        }
    }

    object WallOfStatic extends RI("25145") {
        override def subroutines(self : Card)(implicit g : Game) = $(etrSub)
    }

    object WallOfThorns extends RI("25098") {
        override def subroutines(self : Card)(implicit g : Game) = $(netSub(2), etrSub)
    }

    object Watchtower extends RI("12038") {
        override def subroutines(self : Card)(implicit g : Game) = $(Sub("Search R&D for 1 card and add it to HQ", CopyAbilityEffect($(ChooseCardEffect(Corp, g.deck(Corp).sortBy(_.title), AddToHandPurpose(Corp, false), "Watchtower: choose a card in R&D to add to HQ", false), ShuffleDeckEffect(Corp)))))
    }

    object Weir extends RI("13056") {
        override def subroutines(self : Card)(implicit g : Game) = $(loseClickSub, Sub("The Runner trashes 1 card from the grip", ChooseCardEffect(Runner, g.hand(Runner), TrashFromHandPurpose(Runner), "Weir: choose a card in the grip to trash", false)))
    }

    object Wendigo extends Morph("06047", "Code Gate", "Barrier") {
        override def subroutines(self : Card)(implicit g : Game) = $(Sub("Choose a program. The Runner cannot use the chosen program for the remainder of this run", RotSubAction(self, 1)))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case RotSubAction(self, 1) =>
                val l = g.runnerInstalledOf("Program")
                if (l.any)
                    g.push(ChooseCardEffect(Corp, l, RotIcePurpose(self, 1), "Wendigo: choose a program the Runner cannot use for the remainder of this run", false))
                NextAction
        }
        override def chosen(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.foreach { c =>
                g.log(Corp, "uses Wendigo: the Runner cannot use", c, "for the remainder of this run")
                g.addFlag(Flag("cannot-use", |(c), None, "run", 0, $))
            }
            NextAction
        }
    }

    object Whirlpool extends RI("20103") {
        override def subroutines(self : Card)(implicit g : Game) = $(Sub("The Runner cannot jack out for the remainder of this run. Trash this ice", CopyAbilityEffect($(RunFlagEffect("cannot-jack-out", true), TrashEffect(self, Corp, false)))))
    }

    object Woodcutter extends ZeroToHero("02057") {
        override def subroutines(self : Card)(implicit g : Game) = perAdvancement(self, netSub(1))
    }

    object Wormhole extends Space("07013") {
        override def subroutines(self : Card)(implicit g : Game) = $(Sub("Resolve a subroutine on another piece of ice", resolveSubOn(self, "", false)))
    }

    object Wotan extends RI("04030") {
        override def subroutines(self : Card)(implicit g : Game) = $(
            etrUnlessClicks(2),
            etrUnlessPays(3),
            etrUnless("trashes 1 installed program", "Trash an installed program", $(RunnerTrashInstalledEffect("Program", Runner, "Wotan: choose a program to trash")), g.runnerInstalledOf("Program").any),
            etrUnless("suffers 1 core damage", "Suffer 1 core damage", $(core(1))))
    }

    object Yagura extends RI("25099") {
        override def subroutines(self : Card)(implicit g : Game) = $(Sub("Look at the top card of R&D; you may move it to the bottom of R&D", RotSubAction(self, 1)), netSub(1))
        override def perform(a : CardAction)(implicit g : Game) = a match {
            case RotSubAction(self, 1) =>
                g.deck(Corp).starting.foreach { c =>
                    g.push(LookAtEffect(Corp, $(c), "Yagura: the top card of R&D"), OptionalEffect(Corp, "Yagura: move the top card of R&D to the bottom?", $(RotSubAction(self, 2)), $))
                }
                NextAction
            case RotSubAction(self, 2) =>
                g.deck(Corp).starting.foreach { c =>
                    g.moveTo(c, DeckZone(Corp))
                    g.log(Corp, "moves the top card of R&D to the bottom")
                }
                NextAction
        }
    }

    object Zed1 extends RI("03020") {
        def s(implicit g : Game) = Sub("Do 1 core damage if the Runner spent [click] to break a subroutine this run", clickBrokeThisRun.?(core(1) : ForcedAction).|(MessageEffect(Corp, "does not do core damage with Zed 1.0")))
        override def subroutines(self : Card)(implicit g : Game) = $(s, s)
        override def runnerAbilities(self : Card)(implicit g : Game) = bioroid(self, 1)
    }

    object Zed2 extends RI("12010") {
        override def subroutines(self : Card)(implicit g : Game) = $(trashHardwareSub, trashHardwareSub,
            Sub("Do 2 core damage if the Runner spent [click] to break a subroutine this run", clickBrokeThisRun.?(core(2) : ForcedAction).|(MessageEffect(Corp, "does not do core damage with Zed 2.0"))))
        override def runnerAbilities(self : Card)(implicit g : Game) = bioroid(self, 2)
    }

    val all : $[CardImpl] = $(
        Bullfrog,
        MachicolationA, MachicolationB, Macrophage, Mamba, Marker, Markus1, Masvingo, MatrixAnalyzer, Mausolus, Meridian, Merlin, MeruMati, Metamorph,
        Mganga, MindGame, Minelayer, Miraju, Mlinzi, MotherGoddess, Muckraker,
        NEXTBronze, NEXTDiamond, NEXTGold, NEXTOpal, NEXTSapphire, NEXTSilver, Najja1, Nebula, Negotiator, Nerine2, NeuralKatana, NewsHound, Nightdancer,
        Oduduwa, Orion, Otoroshi, Owl,
        Pachinko, PaperWall, PeepingTom, Pup, Quandary, Quicksand,
        RSVP, Rainbow, RedTape, Resistor,
        SYNCBRE, Sadaka, Sagittarius, Salvage, SandStorm, Sandman, Sapper, Searchlight, SeidrAdaptiveBarrier, SelfAdaptingCodeWall, Sensei, Shadow,
        Sherlock1, Sherlock2, Shinobi, Shiro, SlotMachine, Snoop, Snowflake, SpecialOffer, Spiderweb, Surveyor, Susanoo, Swarm,
        TLDR, TMI, Tapestry, Taurus, Thimblerig, Thoth, Tithonium, TourGuide, Tribunal, Troll, Tsurugi, Turing, Turnpike, Tyrant,
        UniversalConnectivityFee, Upayoga, Uroboros,
        Vanilla, Veritas, Vikram1, Viktor1, Viktor2, Viper, Virgo,
        Waiver, WallOfStatic, WallOfThorns, Watchtower, Weir, Wendigo, Whirlpool, Woodcutter, Wormhole, Wotan,
        Yagura, Zed1, Zed2,
    )
}
