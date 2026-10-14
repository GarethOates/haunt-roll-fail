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
// Rotated (FFG era) upgrades, ported from jinteki.net src/clj/game/cards/upgrades.clj
// ---------------------------------------------------------------------------------------------------------------------

case class RaMidoriPurpose(card : Card, ice : Card) extends CardPurpose
case class RaAwakeningHostPurpose(card : Card) extends CardPurpose
case class RaAwakeningRezPurpose(card : Card) extends CardPurpose
case class RaAwakeningEncounterAction(card : Card, ice : Card) extends CardAction
case class RaTyrAction(card : Card) extends CardAction
case class RaSelfDestructAction(card : Card, server : Server) extends CardAction
case class RaTwinsPurpose(card : Card, ice : Card) extends CardPurpose
case class RaBattyAction(card : Card, server : Server) extends CardAction
case class RaMumbadPurpose(card : Card, ice : Card) extends CardPurpose
case class RaNihongaiAction(card : Card) extends CardAction
case class RaNihongaiPurpose(card : Card) extends CardPurpose
case class RaNihongaiHQPurpose(card : Card, rd : Card) extends CardPurpose
case class RaBambooAction(card : Card) extends CardAction
case class RaWarroidAction(card : Card) extends CardAction
case class RaJinjaAction(card : Card, ice : $[Card]) extends CardAction
case class RaJinjaPurpose(card : Card, ice : $[Card]) extends CardPurpose
case class RaCodeReplicatorAction(card : Card) extends CardAction
case class RaMwanzaPayAction(card : Card, base : Int) extends CardAction
case class RaIntakeAction(card : Card) extends CardAction
case class RaDarumaPurpose(card : Card) extends CardPurpose
case class RaDarumaSwapPurpose(card : Card, a : Card) extends CardPurpose
case class RaDarumaAction(card : Card, server : Server) extends CardAction
case class RaOverseerAction(card : Card, n : Int) extends CardAction
case class RaArellaPurpose(card : Card) extends CardPurpose


object RotUpgradeCards {

    // the server of a card trashed right now (the zone it was trashed from)
    def trashedFrom(implicit g : Game) : |[Server] = g.zoneServer(g.lastTrashedFrom)

    // persistent abilities of upgrades trashed by the Runner during a run: a run-scoped flag on the server
    def persist(self : Card, by : Faction, key : String)(implicit g : Game) : $[ForcedAction] =
        (by.isRunner && g.lastTrashedRezzed && g.run.exists(r => !r.ended)).??(trashedFrom.$./(s => AddFlagEffect(Flag(key, |(self), |(s), "run", 0, $)) : ForcedAction))

    // ---- Core Set / Genesis / Spin ----------------------------------------------------------------------------------------

    object ExperientialData extends RAg("01066") {
        override def iceStrengthMod(self : Card, ice : Card)(implicit g : Game) = protecting(self, ice).?(1).|(0)
    }

    object AkitaroWatanabe extends RAg("01079") {
        override def rezCostMod(self : Card, card : Card)(implicit g : Game) = (g.isActive(self) && protecting(self, card)).?(-2).|(0)
    }

    object ResearchStation extends RAg("01105") {
        override def installOnly(self : Card, server : |[Server])(implicit g : Game) = server.has(HQ)
        override def handSize(self : Card, f : Faction)(implicit g : Game) = f.isCorp.?(2).|(0)
    }

    object ChiLoCityGrid extends RAg("02036") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TraceResolved(true, _) if runHere(self) => $(MessageEffect(Corp, "uses ChiLo City Grid"), tag(1))
            case _ => $
        }
    }

    object AmazonIndustrialZone extends RAg("02038") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Installed(Corp, c) if c.data.isIce && protecting(self, c) && !g.isRezzed(c) && g.canSpend(Corp, g.rezCost(c) - 3, Spend.rez(c)) =>
                $(OptionalEffect(Corp, "Amazon Industrial Zone: rez the ice, lowering its rez cost by 3?", $(DiscountRezEffect(c, 3)), $))
            case _ => $
        }
    }

    object SimoneDiego extends RAg("02099") {
        override def initialCounters(self : Card)(implicit g : Game) = Map("credit" -> 2)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) => $(RefillCreditsEffect(self, 2))
            case _ => $
        }
        // hosted credits pay for advancing cards in the root of / protecting this server
        override def paysFor(self : Card, spend : Spend)(implicit g : Game) = spend.advancing.exists(c => inThisServer(self, c))
    }

    object RuhrValley extends RAg("02111") {
        override def runCost(self : Card, server : Server)(implicit g : Game) = thisServer(self, server).$(ClickCost(1))
    }

    object Midori extends RAg("02113") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case ApproachIce(c) if runHere(self) && protecting(self, c) && !g.state(self).usedRun.has("midori") && g.hand(Corp).exists(_.data.isIce) =>
                $(OptionalEffect(Corp, "Midori: swap the approached ice with a piece of ice from HQ?", $(MarkRunUsedEffect(self, "midori"),
                    ChooseCardEffect(Corp, g.hand(Corp).%(_.data.isIce), RaMidoriPurpose(self, c), "Midori: choose a piece of ice in HQ", false)), $))
            case _ => $
        }
        override def chosenCard(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case RaMidoriPurpose(self, ice) =>
                cards.single.foreach(c => g.push(SwapIceWithEffect(ice, c), JackOutOfferEffect))
                NextAction
        }
    }

    object AwakeningCenter extends RAg("03021") {
        def hostedIce(self : Card)(implicit g : Game) = g.hosted(self).%(c => c.data.isIce && !g.isRezzed(c))
        def rezzable(self : Card)(implicit g : Game) = hostedIce(self).%(c => g.canSpend(Corp, g.rezCost(c) - 7, Spend.rez(c)))
        override def abilities(self : Card)(implicit g : Game) = {
            val l = g.hand(Corp).%(c => c.data.isIce && g.has(c, "Bioroid"))
            $(Ability("host a piece of bioroid ice from HQ", click(), $(ChooseCardEffect(Corp, l, RaAwakeningHostPurpose(self), "Awakening Center: choose a piece of bioroid ice", false)), click = true, ok = l.any, reason = "no bioroid ice in HQ"))
        }
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case PassedAllIce(s) if thisServer(self, s) && rezzable(self).any =>
                $(OptionalEffect(Corp, "Awakening Center: rez a hosted piece of ice paying 7 less and make the Runner encounter it?",
                    $(ChooseCardEffect(Corp, rezzable(self), RaAwakeningRezPurpose(self), "Awakening Center: choose a hosted piece of ice", false)), $))
            case _ => $
        }
        override def chosenCard(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case RaAwakeningHostPurpose(self) =>
                cards.single.foreach(c => g.push(CorpHostInstallEffect(c, self)))
                NextAction
            case RaAwakeningRezPurpose(self) =>
                cards.single.foreach(c => g.push(DiscountRezEffect(c, 7), RaAwakeningEncounterAction(self, c)))
                NextAction
        }
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case RaAwakeningEncounterAction(self, c) =>
                if (g.isRezzed(c) && g.run.exists(r => !r.ended)) {
                    g.log(Corp, "uses Awakening Center to make the Runner encounter", c)
                    g.push(AtRunEndEffect($(TrashEffect(c, Corp, false))), ForceEncounterEffect(c))
                }
                NextAction
        }
    }

    // [interrupt] when a subroutine would be broken on bioroid ice protecting this server: the Corp may rez it (the rules'
    // break window offers the rez) and trash it to prevent 1 subroutine from being broken
    object TyrsHand extends RAg("03022") {
        override def preventions(self : Card, kind : String, n : Int)(implicit g : Game) =
            (kind == "break" && Prevent.ctx.exists(_.source.exists(i => protecting(self, i) && g.has(i, "Bioroid")))).$(Ability("prevent a subroutine from being broken", $(TrashSelfCost), $(PreventBreakChooseEffect(self))))
    }

    // ---- Lunar / SanSan cycles ----------------------------------------------------------------------------------------------

    object OffTheGrid extends RAg("04038") {
        override def installOnly(self : Card, server : |[Server])(implicit g : Game) = remoteOnly(server)
        override def allowsRun(self : Card, server : Server)(implicit g : Game) = !(g.isRezzed(self) && thisServer(self, server))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(HQ) => $(MessageEffect(Corp, "trashes Off the Grid"), TrashEffect(self, Corp, false))
            case _ => $
        }
    }

    object PanicButton extends RAg("04072") {
        override def installOnly(self : Card, server : |[Server])(implicit g : Game) = server.has(HQ)
        override def abilities(self : Card)(implicit g : Game) = $(Ability("draw 1 card", c(1), $(draw(Corp, 1)), ok = g.run.exists(r => !r.ended && r.server == HQ) && g.deck(Corp).any, reason = "only during a run on HQ"))
    }

    object CapriceNisei extends RAg("04114") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case PassedAllIce(s) if thisServer(self, s) => $(MessageEffect(Corp, "uses Caprice Nisei"), PsiEffect($, $(MessageEffect(Corp, "ends the run with Caprice Nisei"), etr)))
            case _ => $
        }
    }

    object NeoTokyoGrid extends RAg("05021") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Advanced(c, _) if inRoot(self, c) && !g.usedThisTurn(self, "neotokyo") => $(MarkUsedEffect(self, "neotokyo"), MessageEffect(Corp, "uses NeoTokyo Grid"), gain(Corp, 1))
            case AdvancementPlaced(c, _) if inRoot(self, c) && !g.usedThisTurn(self, "neotokyo") => $(MarkUsedEffect(self, "neotokyo"), MessageEffect(Corp, "uses NeoTokyo Grid"), gain(Corp, 1))
            case _ => $
        }
    }

    object ToriHanzo extends RAg("05022") {
        override def preventions(self : Card, kind : String, n : Int)(implicit g : Game) =
            (kind == "net" && runHere(self) && Prevent.damageCtx.exists(x => x.firstThisRun && x.by.isCorp) && g.preventUses(self) == 0).$(Ability("pay 2 credits to do 1 core damage instead", c(2), $(PreventConvertDamageEffect("core", 1))))
    }

    object MidwayStationGrid extends RAg("06007") {
        // "During runs on this server, the Runner must pay 1 credit as an additional cost to use an icebreaker ability to break subroutines."
        override def icebreakerSurcharge(self : Card, breaker : Card, breaking : Boolean)(implicit g : Game) = (breaking && runHere(self)).??(1)
    }

    object HeinleinGrid extends RAg("06023") {
        // whenever the Runner spends (costs: paid abilities, bioroid breaks) or loses (effects) [click] during a run on this server
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case ClicksSpentOrLost(Runner, _) if runHere(self) && g.credits(Runner) > 0 => $(MessageEffect(Corp, "uses Heinlein Grid: the Runner loses all credits"), LoseAllCreditsEffect(Runner))
            case _ => $
        }
    }

    object WillOTheWisp extends RAg("06032") {
        def used(implicit g : Game) = g.flagsOf("rot-breaker")./~(_.card).distinct.%(c => g.isInstalled(c) && g.isIcebreaker(c))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(s) if thisServer(self, s) && used.any =>
                $(OptionalEffect(Corp, "Will-o-the-Wisp: trash it to add an icebreaker used this run to the bottom of the stack?", $(TrashEffect(self, Corp, false),
                    ChooseCardEffect(Corp, used, BottomOfDeckPurpose(Runner), "Will-o-the-Wisp: choose an icebreaker", false), CheckMemoryAction), $))
            case _ => $
        }
    }

    object PortAnsonGrid extends RAg("06044") {
        override def jackOutCosts(self : Card)(implicit g : Game) = runHere(self).$("program")
    }

    object ShellCorporation extends RAg("06092") {
        override def abilities(self : Card)(implicit g : Game) = $(
            Ability("place 3 credits on Shell Corporation", click(), $(AddCounterEffect(self, "credit", 3)), click = true, once = once("shell")),
            Ability("take all credits from Shell Corporation", click(), $(TakeCreditsEffect(Corp, self, g.counter(self, "credit"))), click = true, once = once("shell"), ok = g.counter(self, "credit") > 0, reason = "no credits"))
    }

    object SelfDestruct extends RAg("06112") {
        override def installOnly(self : Card, server : |[Server])(implicit g : Game) = remoteOnly(server)
        override def abilities(self : Card)(implicit g : Game) = $(Ability("trash all cards in this server, Trace X: do 3 net damage", $(TrashSelfCost), g.serverOf(self).$./(s => RaSelfDestructAction(self, s)),
            ok = runHere(self), reason = "only during a run on this server"))
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case RaSelfDestructAction(self, s) =>
                val l = g.ice(s) ++ g.root(s)
                g.log(Corp, "uses Self-destruct to trash", l.num.cards)
                g.push(TrashCardsEffect(l, Corp), TraceEffect(l.num, $(net(3)), $))
                NextAction
        }
    }

    object SatelliteGrid extends RAg("07023")       // +1 advancement token on the ice protecting its server: RestrictionRules.advancementBonus

    object TheTwins extends RAg("07024") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case PassIce(c) if runHere(self) && g.isRezzed(c) && protecting(self, c) && g.hand(Corp).exists(_.code == c.code) =>
                $(OptionalEffect(Corp, "The Twins: reveal and trash a copy of the passed ice from HQ to make the Runner encounter it again?",
                    $(ChooseCardEffect(Corp, g.hand(Corp).%(_.code == c.code), RaTwinsPurpose(self, c), "The Twins: choose a copy in HQ", false)), $))
            case _ => $
        }
        override def chosenCard(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case RaTwinsPurpose(self, ice) =>
                cards.single.foreach(c => g.push(RevealEffect(Corp, c), TrashEffect(c, Corp, false), ForceEncounterEffect(ice)))
                NextAction
        }
    }

    object DedicatedTechnicianTeam extends RAg("07026") {
        override def initialCounters(self : Card)(implicit g : Game) = Map("credit" -> 2)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) => $(RefillCreditsEffect(self, 2))
            case _ => $
        }
        // hosted credits pay for installing ice protecting this server
        override def paysFor(self : Card, spend : Spend)(implicit g : Game) = spend.installing.exists(_.data.isIce) && spend.server.exists(s => g.serverOf(self).has(s))
    }

    object CyberdexVirusSuite extends RAg("07027") {
        def offer(implicit g : Game) : $[ForcedAction] = $(OptionalEffect(Corp, "Cyberdex Virus Suite: purge virus counters?", $(PurgeEffect), $))
        override def onAccess(self : Card)(implicit g : Game) = offer
        override def onArchivesAccess(self : Card)(implicit g : Game) = offer
        override def abilities(self : Card)(implicit g : Game) = $(Ability("purge virus counters", $(TrashSelfCost), $(PurgeEffect)))
    }

    object ValleyGrid extends RAg("08015") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SubsBroken(ice, _, _) if protecting(self, ice) && g.unbrokenSubs(ice).none =>
                val until = g.active.isRunner.?(g.halfTurn).|(g.halfTurn + 1)
                $(MessageEffect(Corp, "uses Valley Grid: the Runner gets -1 maximum hand size until the next Corp turn"), AddFlagEffect(Flag("rot-valley", |(self), None, "halfturn", until, $)))
            case _ => $
        }
    }

    object BreakerBayGrid extends RAg("08040") {
        override def rezCostMod(self : Card, card : Card)(implicit g : Game) = (g.isActive(self) && card != self && inRoot(self, card)).?(-5).|(0)
    }

    object OaktownGrid extends RAg("08053") {
        override def trashCostMod(self : Card, card : Card)(implicit g : Game) = inRoot(self, card).?(3).|(0)
    }

    object RyonKnight extends RAg("08054") {
        override def abilities(self : Card)(implicit g : Game) = $(Ability("do 1 core damage", $(TrashSelfCost), $(core(1)), ok = runHere(self) && g.clicks(Runner) == 0, reason = "only during a run on this server when the Runner has no clicks"))
    }

    object MarcusBatty extends RAg("08074") {
        override def abilities(self : Card)(implicit g : Game) = $(Ability("psi game: resolve a subroutine on a piece of ice protecting this server", $(TrashSelfCost),
            g.serverOf(self).$./(s => PsiEffect($, $(RaBattyAction(self, s))) : ForcedAction), ok = runHere(self), reason = "only during a run on this server"))
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case RaBattyAction(self, s) =>
                val l = g.ice(s).%(g.isRezzed)
                if (l.any)
                    g.push(ChooseCardEffect(Corp, l, SubChoicePurpose(self), "Marcus Batty: choose a piece of ice", false))
                NextAction
        }
    }

    object UnderwayGrid extends RAg("08080") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case IceBypassed(ice) if protecting(self, ice) => $(MessageEffect(Corp, "uses Underway Grid: the ice cannot be bypassed"), CancelBypassEffect)
            case _ => $
        }
    }

    object OldHollywoodGrid extends RAg("08097") {
        override def protectsFromAccess(self : Card, target : Card)(implicit g : Game) = target.data.isAgenda && fromThisServer(self, target) && !g.scored(Runner).exists(_.title == target.title)
        override def onTrash(self : Card, by : Faction)(implicit g : Game) = persist(self, by, "rot-no-steal-server")
    }

    object ExpoGrid extends RAg("08119") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) if g.serverOf(self).exists(s => g.root(s).exists(c => c.data.isAsset && g.isRezzed(c))) => $(MessageEffect(Corp, "uses Expo Grid"), gain(Corp, 1))
            case _ => $
        }
    }

    // ---- Data and Destiny / Mumbad cycle ------------------------------------------------------------------------------------

    object KeeganLane extends RAg("09024") {
        override def abilities(self : Card)(implicit g : Game) = {
            val l = g.runnerInstalledOf("Program")
            $(Ability("remove 1 tag: trash 1 program", $(TrashSelfCost), $(RemoveTagsEffect(1), ChooseCardEffect(Corp, l, TrashPurpose(Corp), "Keegan Lane: choose a program to trash", false)),
                ok = runHere(self) && g.tagged && l.any, reason = "only during a run on this server while the Runner is tagged"))
        }
    }

    object RutherfordGrid extends RAg("09025") {
        override def traceBonus(self : Card)(implicit g : Game) = runHere(self).?(2).|(0)
    }

    object MumbadCityGrid extends RAg("10014") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case PassIce(c) if runHere(self) && protecting(self, c) && g.serverOf(self).exists(s => g.ice(s).num >= 2) =>
                val l = g.serverOf(self)./~(s => g.ice(s)).but(c)
                $(ChooseCardEffect(Corp, l, RaMumbadPurpose(self, c), "Mumbad City Grid: swap the passed ice with another piece of ice protecting this server?", true))
            case _ => $
        }
        override def chosenCard(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case RaMumbadPurpose(self, ice) =>
                cards.single.foreach(c => g.push(RotSwapIceEffect(ice, c)))
                NextAction
        }
    }

    object DisposableHQ extends RAg("10034") {
        def offer(implicit g : Game) : $[ForcedAction] = g.hand(Corp).any.$(OptionalEffect(Corp, "Disposable HQ: add cards from HQ to the bottom of R&D?",
            $(ChooseCardsEffect(Corp, g.hand(Corp), 0, g.hand(Corp).num, BottomOfDeckPurpose(Corp), "Disposable HQ: choose any number of cards in HQ")), $))
        override def onAccess(self : Card)(implicit g : Game) = offer
        override def onArchivesAccess(self : Card)(implicit g : Game) = offer
    }

    object SuratCityGrid extends RAg("10057") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Rezzed(c) if c != self && inThisServer(self, c) =>
                val l = g.installedCards(Corp).%(x => !g.isRezzed(x) && !x.data.isAgenda && g.canSpend(Corp, g.rezCost(x) - 2, Spend.rez(x)) && g.impl(x).canRez(x))
                l.any.$(OptionalEffect(Corp, "Surat City Grid: rez another card paying 2 credits less?", $(ChooseCardEffect(Corp, l, RezDiscountPurpose(self, 2), "Surat City Grid: choose a card to rez", false)), $))
            case _ => $
        }
    }

    object MumbadVirtualTour extends RAg("10076") {
        override def mustTrashOnAccess(self : Card)(implicit g : Game) = true
    }

    object NaviMumbaiCityGrid extends RAg("10110") {
        override def blocksRunnerAbilities(self : Card)(implicit g : Game) = runHere(self)
    }

    // ---- Flashpoint / Red Sand cycles ------------------------------------------------------------------------------------------

    object GeorgiaEmelyov extends RAg("11014") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case UnsuccessfulRun(s) if thisServer(self, s) => $(MessageEffect(Corp, "uses Georgia Emelyov"), net(1))
            case _ => $
        }
        override def abilities(self : Card)(implicit g : Game) = $(Ability("move to another server", c(2), $(ChooseServerMoveEffect(self, g.servers))))
    }

    object Prisec extends RAg("11040") {
        override def onAccess(self : Card)(implicit g : Game) = (g.isInstalled(self) && g.credits(Corp) >= 2).$(OptionalEffect(Corp, "Prisec: pay 2 credits to do 1 meat damage and give the Runner 1 tag?",
            $(LoseCreditsEffect(Corp, 2), meat(1), tag(1)), $))
    }

    object DroneScreen extends RAg("11076") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case RunInitiated(s) if thisServer(self, s) && g.tagged => $(MessageEffect(Corp, "uses Drone Screen"), TraceEffect(3, $(FrUnpreventableDamageEffect("meat", 1)), $))
            case _ => $
        }
    }

    object MantaGrid extends RAg("11091") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case RunEnds(s, true, _) if thisServer(self, s) && (g.credits(Runner) < 6 || g.clicks(Runner) == 0) => $(MessageEffect(Corp, "uses Manta Grid"), ExtraClicksEffect(Corp, 1))
            case _ => $
        }
    }

    object NihongaiGrid extends RAg("11093") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(s) if thisServer(self, s) && (g.availableCredits(Runner, None) < 6 || g.hand(Runner).num < 2) && g.hand(Corp).any && g.deck(Corp).any =>
                $(OptionalEffect(Corp, "Nihongai Grid: look at the top 5 cards of R&D and swap one with a card in HQ?", $(RaNihongaiAction(self)), $))
            case _ => $
        }
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case RaNihongaiAction(self) =>
                val top = g.deck(Corp).take(5)
                g.log(Corp, "looks at the top", top.num.cards, "of R&D")
                g.push(ChooseCardEffect(Corp, top, RaNihongaiPurpose(self), "Nihongai Grid: choose a card in R&D to swap", true))
                NextAction
        }
        override def chosenCard(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case RaNihongaiPurpose(self) =>
                cards.single.foreach(rd => g.push(ChooseCardEffect(Corp, g.hand(Corp), RaNihongaiHQPurpose(self, rd), "Nihongai Grid: choose a card in HQ to swap", false)))
                NextAction
            case RaNihongaiHQPurpose(self, rd) =>
                cards.single.foreach { h =>
                    if (g.zone(rd) == DeckZone(Corp) && g.zone(h) == HandZone(Corp)) {
                        val i = g.deck(Corp).indexOf(rd)
                        g.moveTo(rd, HandZone(Corp))
                        g.moveTo(h, DeckZone(Corp))
                        val d = g.deck(Corp).but(h)
                        g.zones += DeckZone(Corp) -> (d.take(i) ++ $(h) ++ d.drop(i))
                        g.log(Corp, "swaps a card in R&D with a card in HQ")
                    }
                }
                NextAction
        }
    }

    object BryanStinson extends RAg("11117") {
        def transactions(implicit g : Game) = g.discard(Corp).%(c => c.data.isOperation && g.has(c, "Transaction") && g.impl(c).canPlay(c))
        override def abilities(self : Card)(implicit g : Game) = $(Ability("play a transaction operation from Archives ignoring all costs and remove it from the game", click(),
            $(ChooseCardEffect(Corp, transactions, PlayPurpose(self, false, true), "Bryan Stinson: choose a transaction in Archives", false)), click = true,
            ok = g.credits(Runner) < 6 && transactions.any, reason = "the Runner has 6 or more credits or no transaction in Archives"))
    }

    // ---- Daedalus Complex / Earth's Scion / Blood and Water / Free Mars / Crimson Dust ---------------------------------------

    object DefenseConstruct extends RAg("12011") {
        override def advanceable(self : Card)(implicit g : Game) = true
        override def abilities(self : Card)(implicit g : Game) = {
            val l = g.discard(Corp).%(c => !g.state(c).seen)
            val n = g.advancement(self)
            $(Ability("add facedown cards from Archives to HQ", $(TrashSelfCost), $(ChooseCardsEffect(Corp, l, 0, n, ToHandTopPurpose(self, Corp), "Defense Construct: choose up to " + n + " facedown cards in Archives")),
                ok = g.run.exists(r => !r.ended && r.server == Archives) && n > 0 && l.any, reason = "only during a run on Archives"))
        }
    }

    object OberthProtocol extends RAg("12018") {
        override def canRez(self : Card)(implicit g : Game) = g.scored(Corp).exists(_.data.isAgenda)
        override def onRez(self : Card)(implicit g : Game) = $(MessageEffect(Corp, "forfeits an agenda to rez Oberth Protocol"), ChooseForfeitEffect)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Advanced(c, _) if inThisServer(self, c) && !g.usedThisTurn(self, "oberth") => $(MarkUsedEffect(self, "oberth"), MessageEffect(Corp, "uses Oberth Protocol"), PlaceAdvancementEffect(c, 1))
            case _ => $
        }
    }

    object KhondiPlaza extends RAg("12019") {
        override def initialCounters(self : Card)(implicit g : Game) = Map("credit" -> g.remotes.num)
        // hosted credits pay for rezzing ice protecting this server
        override def paysFor(self : Card, spend : Spend)(implicit g : Game) = spend.rezzing.exists(c => c.data.isIce && protecting(self, c))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) => $(RefillCreditsEffect(self, g.remotes.num))
            case _ => $
        }
    }

    object SignalJamming extends RAg("12020") {
        override def abilities(self : Card)(implicit g : Game) = $(Ability("cards cannot be installed until the end of the run", $(TrashSelfCost),
            $(MessageEffect(Corp, "prevents cards from being installed until the end of the run"), AddFlagEffect(Flag("rot-no-install", None, None, "run", 0, $))), ok = runHere(self), reason = "only during a run on this server"))
    }

    object BambooDome extends RAg("12053") {
        override def installOnly(self : Card, server : |[Server])(implicit g : Game) = server.has(RnD)
        override def abilities(self : Card)(implicit g : Game) = $(Ability("reveal the top 3 cards of R&D, add 1 to HQ", click(), $(RaBambooAction(self)), click = true, ok = g.deck(Corp).any, reason = "R&D is empty"))
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case RaBambooAction(self) =>
                val top = g.deck(Corp).take(3)
                g.push(RevealCardsEffect(Corp, top), ChooseCardEffect(Corp, top, ToHandTopPurpose(self, Corp), "Bamboo Dome: choose a card to add to HQ", false), ArrangeTopEffect(Corp, Corp, (top.num - 1).atLeast(0)))
                NextAction
        }
    }

    object BenMusashi extends RAg("12054") {
        override def stealDamage(self : Card, agenda : Card)(implicit g : Game) = fromThisServer(self, agenda).?(2).|(0)
        override def onTrash(self : Card, by : Faction)(implicit g : Game) = persist(self, by, "rot-steal-net")
    }

    object HenryPhillips extends RAg("12056") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SubsBroken(_, subs, _) if runHere(self) && g.tagged => $(MessageEffect(Corp, "uses Henry Phillips"), gain(Corp, 2 * subs.num))
            case _ => $
        }
    }

    object WarroidTracker extends RAg("12068") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            // "Whenever the Runner trashes at least 1 card from this server": once per batch of trashes
            case FrCorpTrashBatch(_, from) if from.exists(_.exists(s => thisServer(self, s))) => $(MessageEffect(Corp, "uses Warroid Tracker"), TraceEffect(4, $(RaWarroidAction(self)), $))
            case _ => $
        }
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case RaWarroidAction(self) =>
                val k = 2.atMost(g.runnerInstalled.num)
                if (k > 0)
                    g.push(ChooseCardsEffect(Runner, g.runnerInstalled, k, k, TrashPurpose(Runner), "Warroid Tracker: choose " + k + " installed card" + (k > 1).??("s") + " to trash"))
                NextAction
        }
    }

    object TrafficAnalyzer extends RAg("12075") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Rezzed(c) if c.data.isIce && protecting(self, c) => $(MessageEffect(Corp, "uses Traffic Analyzer"), TraceEffect(2, $(gain(Corp, 1)), $))
            case _ => $
        }
    }

    object HelheimServers extends RAg("12091") {
        override def abilities(self : Card)(implicit g : Game) = $(Ability("all ice protecting this server has +2 strength for the remainder of the run", $(TrashFromHandCost(1)),
            g.serverOf(self)./~(s => g.ice(s))./(i => PumpIceEffect(i, 2, RunDuration) : ForcedAction), ok = runHere(self) && g.hand(Corp).any, reason = "only during a run on this server"))
    }

    object FractalThreatMatrix extends RAg("12119") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SubsBroken(ice, _, _) if protecting(self, ice) && g.unbrokenSubs(ice).none => $(MessageEffect(Corp, "uses Fractal Threat Matrix"), MillEffect(Runner, 2))
            case _ => $
        }
    }

    // ---- Terminal Directive / Revised Core Set ------------------------------------------------------------------------------

    object BlackLevelClearance extends RAg("13039") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(s) if thisServer(self, s) => $(ChooseOneEffect(Runner, "Black Level Clearance: choose one", $(
                Alt("Suffer 1 core damage", $(core(1))),
                Alt("Jack out", $(JackOutEffect, MessageEffect(Corp, "uses Black Level Clearance to gain 5 credits and draw 1 card"), gain(Corp, 5), draw(Corp, 1), TrashEffect(self, Corp, false))))))
            case _ => $
        }
    }

    object KPLynn extends RAg("13052") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case PassedAllIce(s) if thisServer(self, s) => $(ChooseOneEffect(Runner, "K. P. Lynn: choose one", $(Alt("Take 1 tag", $(tag(1))), Alt("End the run", $(etr)))))
            case _ => $
        }
    }

    object Strongbox extends RAg("20076") {
        override def stealCostMod(self : Card, agenda : Card)(implicit g : Game) = fromThisServer(self, agenda).$(ClickCost(1))
        override def onTrash(self : Card, by : Faction)(implicit g : Game) = persist(self, by, "rot-steal-click")
    }

    object BerniceMai extends RAg("20123") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(s) if thisServer(self, s) => $(MessageEffect(Corp, "uses Bernice Mai"), TraceEffect(5, $(tag(1)), $(MessageEffect(Corp, "trashes Bernice Mai"), TrashEffect(self, Corp, false))))
            case _ => $
        }
    }

    // ---- Kitara cycle ----------------------------------------------------------------------------------------------------------

    object CalibrationTesting extends RAg("21017") {
        override def installOnly(self : Card, server : |[Server])(implicit g : Game) = remoteOnly(server)
        override def abilities(self : Card)(implicit g : Game) = {
            val l = g.serverOf(self)./~(s => g.root(s)).but(self)
            $(Ability("place 1 advancement counter on a card in the root of this server", $(TrashSelfCost), $(ChooseCardEffect(Corp, l, AdvancePurpose(1), "Calibration Testing: choose a card", false)),
                ok = l.any, reason = "no other card in this server"))
        }
    }

    object JinjaCityGrid extends RAg("21031") {
        def installable(self : Card, l : $[Card])(implicit g : Game) = l.%(c => g.zone(c) == HandZone(Corp) && g.canSpend(Corp, g.installCost(c, g.serverOf(self)) - 4, Spend.install(c, g.serverOf(self))))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case CardsDrawn(Corp, l) if l.exists(_.data.isIce) => $(RaJinjaAction(self, l.%(_.data.isIce)))
            case _ => $
        }
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case RaJinjaAction(self, l) =>
                val ll = installable(self, l)
                if (ll.any && g.isInstalled(self))
                    g.push(ChooseCardEffect(Corp, ll, RaJinjaPurpose(self, ll), "Jinja City Grid: reveal and install a drawn piece of ice protecting this server, paying 4 less?", true))
                NextAction
        }
        override def chosenCard(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case RaJinjaPurpose(self, l) =>
                cards.single.foreach(c => g.push(RevealEffect(Corp, c), CorpInstallPayingEffect(c, g.serverOf(self), 4, false), RaJinjaAction(self, l.but(c))))
                NextAction
        }
    }

    object ForcedConnection extends RAg("21037") {
        override def onAccess(self : Card)(implicit g : Game) = $(MessageEffect(Corp, "uses Forced Connection"), TraceEffect(3, $(tag(2)), $))
    }

    object CodeReplicator extends RAg("21052") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case PassIce(c) if runHere(self) && g.isRezzed(c) && protecting(self, c) =>
                $(OptionalEffect(Corp, "Code Replicator: trash it to make the Runner approach the passed ice again?", $(RaCodeReplicatorAction(self)), $))
            case _ => $
        }
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case RaCodeReplicatorAction(self) =>
                g.run.%(r => !r.ended).foreach { r =>
                    g.log(Corp, "trashes Code Replicator to make the Runner approach the ice again")
                    g.push(TrashEffect(self, Corp, false), MoveRunnerEffect(r.position + 1, true))
                }
                NextAction
        }
    }

    object Tempus extends RAg("21071") {
        override def onAccess(self : Card)(implicit g : Game) = $(MessageEffect(Corp, "uses Tempus"), TraceEffect(3, $(ChooseOneEffect(Runner, "Tempus: choose one", $(
            Alt("Lose 2 clicks", $(LoseClicksEffect(Runner, 2)), g.clicks(Runner) >= 2),
            Alt("Suffer 1 core damage", $(core(1)))))), $))
    }

    object BioVault extends RAg("21072") {
        override def installOnly(self : Card, server : |[Server])(implicit g : Game) = remoteOnly(server)
        override def advanceable(self : Card)(implicit g : Game) = true
        override def abilities(self : Card)(implicit g : Game) = $(Ability("end the run", $(CounterCost("advancement", 2), TrashSelfCost), $(etr), ok = g.run.exists(r => !r.ended), reason = "only during a run"))
    }

    object MwanzaCityGrid extends RAg("21096") {
        override def installOnly(self : Card, server : |[Server])(implicit g : Game) = server.has(HQ) || server.has(RnD)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case BreachServer(s) if thisServer(self, s) && g.run.exists(r => !r.ended && !r.accessOnly) =>
                val base = g.run./(_.accessCount).|(0)
                $(MessageEffect(Corp, "uses Mwanza City Grid: the Runner accesses 3 additional cards"), AccessBonusEffect(s, 3), AddFlagEffect(Flag("rot-mwanza", |(self), |(s), "run", 0, $(RaMwanzaPayAction(self, base)))))
            case _ => $
        }
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case RaMwanzaPayAction(self, base) =>
                val n = (g.run.||(g.lastRun)./(_.accessCount).|(0) - base).atLeast(0)
                if (n > 0) {
                    g.log(Corp, "gains", (2 * n).credits, "from Mwanza City Grid")
                    g.push(gain(Corp, 2 * n))
                }
                NextAction
        }
    }

    object Intake extends RAg("21098") {
        override def onAccess(self : Card)(implicit g : Game) = $(MessageEffect(Corp, "uses Intake"), TraceEffect(4, $(RaIntakeAction(self)), $))
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case RaIntakeAction(self) =>
                val l = g.runnerInstalled.%(c => c.data.isProgram || (c.data.isResource && g.has(c, "Virtual")))
                if (l.any)
                    g.push(ChooseCardEffect(Corp, l, ReturnToHandPurpose, "Intake: choose a program or virtual resource to add to the grip", false))
                NextAction
        }
    }

    object OverseerMatrix extends RAg("21100") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            // once per batch of trashes: pay 1 credit per card trashed from this server to give that many tags (jinteki)
            case FrCorpTrashBatch(_, from) if g.serverOf(self).exists(s => from.exists(_.has(s))) => $(RaOverseerAction(self, from.count(f => f == g.serverOf(self))))
            case _ => $
        }
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case RaOverseerAction(self, n) =>
                val max = n.atMost(g.creditsFor(Corp, Spend.ability(self)))
                if (max > 0)
                    g.push(ChooseNumberEffect(Corp, self, 0, max, "overseer", "Overseer Matrix: pay how many credits to give the Runner that many tags?"))
                NextAction
            case NumberPickedAction(self, n, "overseer") =>
                if (n > 0)
                    g.push(SpendEffect(Corp, n, Spend.ability(self), 0, $(MessageEffect(Corp, "uses Overseer Matrix"), tag(n))))
                NextAction
        }
        // persistent: the flag "fr-overseer" keeps the ability for the rest of the run (FinalRotRules.finalRotTriggers)
        override def onTrash(self : Card, by : Faction)(implicit g : Game) = persist(self, by, "fr-overseer")
    }

    // ---- Reign and Reverie / Magnum Opus / System Core 2019 / Midnight Sun Booster ---------------------------------------------

    object GiordanoMemorialField extends RAg("22033") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(s) if thisServer(self, s) =>
                val cost = 2 * g.scored(Runner).num
                $(ChooseOneEffect(Runner, "Giordano Memorial Field: pay " + cost + " credits or end the run", $(
                    Alt("Pay " + cost + " credits", $(LoseCreditsEffect(Runner, cost)), g.credits(Runner) >= cost),
                    Alt("End the run", $(etr)))))
            case _ => $
        }
    }

    // "you may trash this upgrade. If you do, choose 1 card in the root of another server or 1 agenda, asset, or upgrade in HQ.
    // Swap that card with 1 card in the root of this server": the upgrade is trashed first, then the two cards are chosen
    object Daruma extends RAg("22041") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case ApproachServer(s) if thisServer(self, s) && g.root(s).but(self).any =>
                $(OptionalEffect(Corp, "Daruma: trash it to swap a card in the root of this server with another card?", $(TrashEffect(self, Corp, false), RaDarumaAction(self, s)), $))
            case _ => $
        }
        override def performCard(a : CardAction)(implicit g : Game) = a match {
            case RaDarumaAction(self, s) =>
                val others = g.servers.%(_ != s)./~(x => g.root(x)) ++ g.hand(Corp).%(c => c.data.isAgenda || c.data.isAsset || c.data.isUpgrade)
                if (others.any && g.root(s).any)
                    g.push(ChooseCardEffect(Corp, others, RaDarumaPurpose(self), "Daruma: choose a card in the root of another server or in HQ", false))
                g.state(self).memo = s.name
                NextAction
        }
        override def chosenCard(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case RaDarumaPurpose(self) =>
                g.servers.%(_.name == g.state(self).memo).foreach { s =>
                    cards.single.foreach { b =>
                        val l = g.root(s).but(b)
                        if (l.any)
                            g.push(ChooseCardEffect(Corp, l, RaDarumaSwapPurpose(self, b), "Daruma: choose a card in the root of the approached server to swap", false))
                    }
                }
                NextAction
            case RaDarumaSwapPurpose(self, b) =>
                cards.single.foreach { a =>
                    val swap = (g.zone(b) == HandZone(Corp)).?(SwapInstalledWithHandEffect(a, b) : ForcedAction).|(SwapRootEffect(a, b))
                    g.push(swap, JackOutOfferEffect)
                }
                NextAction
        }
    }

    object ArellaSalvatore extends RAg("22049") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case AgendaScored(_) if g.lastScoredFrom.exists(s => thisServer(self, s)) && g.hand(Corp).exists(c => corpInstallable(c)) =>
                $(ChooseCardEffect(Corp, g.hand(Corp).%(c => corpInstallable(c)), RaArellaPurpose(self), "Arella Salvatore: install a card from HQ ignoring all costs and place 1 advancement counter on it?", true))
            case _ => $
        }
        override def chosenCard(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case RaArellaPurpose(self) =>
                cards.single.foreach { c =>
                    val l : $[Server] = (c.data.isIce || c.data.isUpgrade).?(g.servers).|(g.remotes)
                    g.push(ChooseServerEffect(Corp, c, true, l, true), PlaceAdvancementEffect(c, 1))
                }
                NextAction
        }
    }

    object HiredHelp extends RAg("23101") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case RunInitiated(s) if thisServer(self, s) && !g.successfulRunsThisTurn.has(HQ) =>
                $(ChooseOneEffect(Runner, "Hired Help: trash 1 agenda from your score area or end the run", $(
                    Alt("Trash an agenda from your score area", $(ChooseCardEffect(Runner, g.scored(Runner), TrashPurpose(Runner), "Hired Help: choose an agenda to trash", false)), g.scored(Runner).any),
                    Alt("End the run", $(etr)))))
            case _ => $
        }
    }

    object Ash2X3ZB9CY extends RAg("25082") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(s) if thisServer(self, s) => $(MessageEffect(Corp, "uses Ash 2X3ZB9CY"), TraceEffect(4, $(MessageEffect(Corp, "prevents the Runner from accessing cards other than Ash 2X3ZB9CY"),
                AddFlagEffect(Flag("access-only", |(self), None, "run", 0, $))), $))
            case _ => $
        }
    }

    object MasonBellamy extends RAg("25083") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case EncounterEnds(ice) if runHere(self) && protecting(self, ice) && g.flagsOf("broken-by").any => $(MessageEffect(Corp, "uses Mason Bellamy"), LoseClicksEffect(Runner, 1))
            case _ => $
        }
    }

    object ProductPlacement extends RAg("25120") {
        override def onAccess(self : Card)(implicit g : Game) = $(MessageEffect(Corp, "uses Product Placement"), gain(Corp, 2))
    }

    object RedHerrings extends RAg("25121") {
        override def stealCostMod(self : Card, agenda : Card)(implicit g : Game) = fromThisServer(self, agenda).$(CreditCost(5))
        override def onTrash(self : Card, by : Faction)(implicit g : Game) = persist(self, by, "rot-steal-credits")
    }

    object Embolus extends RAg("28003") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) if g.credits(Corp) >= 1 => $(OptionalEffect(Corp, "Embolus: pay 1 credit to place 1 power counter on it?", $(LoseCreditsEffect(Corp, 1), AddCounterEffect(self, "power", 1)), $))
            case SuccessfulRun(_) if g.counter(self, "power") > 0 => $(AddCounterEffect(self, "power", -1))
            case _ => $
        }
        override def abilities(self : Card)(implicit g : Game) = $(Ability("end the run", $(CounterCost("power", 1)), $(etr), ok = runHere(self), reason = "only during a run on this server"))
    }

    val all : $[CardImpl] = $(
        ExperientialData, AkitaroWatanabe, ResearchStation, ChiLoCityGrid, AmazonIndustrialZone, SimoneDiego, RuhrValley, Midori, AwakeningCenter,
        TyrsHand, OffTheGrid, PanicButton, CapriceNisei, NeoTokyoGrid, ToriHanzo, MidwayStationGrid, HeinleinGrid, WillOTheWisp, PortAnsonGrid,
        ShellCorporation, SelfDestruct, SatelliteGrid, TheTwins, DedicatedTechnicianTeam, CyberdexVirusSuite, ValleyGrid, BreakerBayGrid,
        OaktownGrid, RyonKnight, MarcusBatty, UnderwayGrid, OldHollywoodGrid, ExpoGrid, KeeganLane, RutherfordGrid, MumbadCityGrid,
        DisposableHQ, SuratCityGrid, MumbadVirtualTour, NaviMumbaiCityGrid, GeorgiaEmelyov, Prisec, DroneScreen, MantaGrid, NihongaiGrid,
        BryanStinson, DefenseConstruct, OberthProtocol, KhondiPlaza, SignalJamming, BambooDome, BenMusashi, HenryPhillips, WarroidTracker,
        TrafficAnalyzer, HelheimServers, FractalThreatMatrix, BlackLevelClearance, KPLynn, Strongbox, BerniceMai, CalibrationTesting,
        JinjaCityGrid, ForcedConnection, CodeReplicator, Tempus, BioVault, MwanzaCityGrid, Intake, OverseerMatrix, GiordanoMemorialField,
        Daruma, ArellaSalvatore, HiredHelp, Ash2X3ZB9CY, MasonBellamy, ProductPlacement, RedHerrings, Embolus,
    )
}
