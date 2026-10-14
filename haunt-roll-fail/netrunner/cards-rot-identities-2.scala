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
// Rotated identities (Data and Destiny .. System Core 2019, Reign and Reverie, Cyber Bureau), ported from identities.clj
// ---------------------------------------------------------------------------------------------------------------------

case class RHApexPurpose(card : Card) extends CardPurpose
case class RHAdamSetupAction(card : Card) extends CardAction
case class RHAdamInstallAction(card : Card, codes : $[String]) extends CardAction
case class RHSkorpiosPurpose(card : Card) extends CardPurpose
case class RHKabonesaPurpose(card : Card) extends CardPurpose
case class RH419Action(card : Card, target : Card) extends CardAction
case class RHMtiPurpose(card : Card, server : Server) extends CardPurpose
case class RHSaraswatiPurpose(card : Card) extends CardPurpose
case class RHAcmeAction(card : Card, add : Boolean) extends CardAction
case class RHBureauRezAction(card : Card, pool : Int) extends CardAction
case class RHBureauRezPurpose(card : Card, pool : Int) extends CardPurpose
case class RHBureauDrainAction(card : Card) extends CardAction
case class RHBlueSunPurpose(card : Card) extends CardPurpose


object RotIdentityCards2 {
    object SYNC extends RH("09001") {
        override def abilities(self : Card)(implicit g : Game) = $(Ability("flip this identity", click(), $(FlipAction(self, "SYNC (flipped)")), click = true))
        override def removeTagExtraCost(self : Card)(implicit g : Game) = (!flipped(self)).?(1).|(0)
        override def trashResourceCostMod(self : Card)(implicit g : Game) = flipped(self).?(-2).|(0)
    }

    object NewAngelesSol extends RH("09002") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case AgendaScored(_) | AgendaStolen(_) =>
                val l = (g.hand(Corp) ++ g.discard(Corp)).%(c => c.data.isOperation && g.has(c, "Current") && g.impl(c).canPlay(c) && g.canSpend(Corp, g.playCost(c), Spend.play(c)))
                l.any.$(ChooseCardEffect(Corp, l, PlayPurpose(self, true, false), "New Angeles Sol: play a current from HQ or Archives?", true))
            case _ => $
        }
    }

    object Apex extends RH("09029") {
        override def blocksInstall(self : Card, card : Card)(implicit g : Game) = card.data.isResource && !g.has(card, "Virtual")
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) if g.hand(Runner).any => $(ChooseCardEffect(Runner, g.hand(Runner), RHApexPurpose(self), "Apex: install 1 card from the grip facedown?", true))
            case _ => $
        }
        override def chosenRH(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(c => g.push(says(p.card, "install a card facedown"), InstallFacedownEffect(c)))
            NextAction
        }
    }

    object Adam extends RH("09037") {
        def directives = Cards.all.%(c => c.isRunner && c.has("Directive") && c.isResource)./(_.code).sortBy(c => Cards(c).title)
        override def onSetup(self : Card)(implicit g : Game) = $(RHAdamSetupAction(self))
        override def performRH(a : CardAction)(implicit g : Game) = a match {
            case RHAdamSetupAction(self) =>
                val l = directives
                if (l.num <= 3)
                    g.push(RHAdamInstallAction(self, l))
                else
                    g.push(ChooseOneEffect(Runner, "Adam: choose the directive you do not start with", l./(x => Alt(Cards(x).title.filter(_ < 128), $(RHAdamInstallAction(self, l.but(x)))))))
                NextAction
            case RHAdamInstallAction(self, l) =>
                l.indexed.reverse.foreach { case (code, i) => g.push(RunnerInstallEffect(Card(code, 100 + i), None, true)) }
                g.log(Runner, "starts the game with", l./(c => Cards(c).title.hl).comma, "installed (Adam)")
                NextAction
        }
    }

    object SunnyLebeau extends RH("09045")

    object JesminderSareen extends RH("10006") {
        override def avoidTags(self : Card, n : Int)(implicit g : Game) = {
            if (g.run.exists(!_.ended) && !g.state(self).usedRun.has("jesminder")) {
                g.state(self).usedRun :+= "jesminder"
                1
            }
            else
                0
        }
    }

    object PalanaFoods extends RH("10030") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case CardsDrawn(Runner, l) if l.any && firstTime(self, "palana") => $(MarkUsedEffect(self, "palana"), says(self, "gain 1 credit"), gain(Corp, 1))
            case _ => $
        }
    }

    object NeroSevern extends RH("10040") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case EncounterIce(ice) if g.has(ice, "Sentry") && firstTime(self, "nero") && g.run.exists(r => !r.ended && !r.cannotJackOut) =>
                $(OptionalEffect(Runner, "Nero Severn: jack out?", $(MarkUsedEffect(self, "nero"), says(self, "jack out"), JackOutEffect), $))
            case _ => $
        }
    }

    // the grip is played revealed while the Runner is tagged: the UI shows it to the Corp (FinalRotRules.frGripRevealed,
    // ui.scala canSee); the log notes when the grip becomes revealed
    object Harishchandra extends RH("10107") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TagsGained(n) if g.tags == n => $(MessageEffect(Runner, "plays with the grip revealed while tagged (Harishchandra Ent.)"))
            case _ => $
        }
    }

    object NullWhistleblower extends RH("11002") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case EncounterIce(ice) if firstTime(self, "null") && g.hand(Runner).any =>
                $(OptionalEffect(Runner, "Null: trash 1 card from the grip to give this ice -2 strength for the run?", $(MarkUsedEffect(self, "null"), says(self, "give the ice -2 strength"), ChooseCardEffect(Runner, g.hand(Runner), TrashFromHandPurpose(Runner), "Null: choose a card to trash", false), PumpIceEffect(ice, -2, RunDuration)), $))
            case _ => $
        }
    }

    object NBNControllingTheMessage extends RH("11017") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Trashed(c, Runner, _) if g.owner(c).isCorp && g.wasInstalled(c) && firstTime(self, "ctm") =>
                $(MarkUsedEffect(self, "ctm"), OptionalEffect(Corp, "NBN Controlling the Message: trace 4 to give the Runner 1 tag?", $(says(self, "trace 4"), TraceEffect(4, $(TagsApplyEffect(1)), $)), $))
            case _ => $
        }
    }

    object Khan extends RH("11027") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case PassIce(_) if firstTime(self, "khan") =>
                val l = g.hand(Runner).%(c => c.data.isProgram && c.data.isIcebreaker && g.canAffordInstall(c, 1))
                $(MarkUsedEffect(self, "khan")) ++ l.any.$(says(self, "install an icebreaker paying 1 less")) ++ l.any.$(ChooseCardEffect(Runner, l, InstallPayingPurpose(1, None, false), "Khan: install an icebreaker from the grip paying 1 credit less?", true))
            case _ => $
        }
    }

    object WeylandBuilderOfNations extends RH("11038") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case EncounterEnds(ice) if g.isRezzed(ice) && g.advancement(ice) > 0 && firstTime(self, "bon") => $(MarkUsedEffect(self, "bon"), says(self, "do 1 meat damage"), meat(1))
            case _ => $
        }
    }

    object OmarKeung extends RH("11043") {
        override def abilities(self : Card)(implicit g : Game) = $(Ability("run Archives", click(), $(RunEffect(Archives, |(self))), click = true, once = once("omar")))
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case ApproachServer(Archives) if thisCardRun(self) =>
                $(ChooseOneEffect(Runner, "Omar Keung: change the attacked server to", $(Alt("HQ", $(RedirectRunEffect(HQ, 0, false))), Alt("R&D", $(RedirectRunEffect(RnD, 0, false))))))
            case _ => $
        }
    }

    object JintekiPotentialUnleashed extends RH("11054") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case DamageDone("net", n) if n > 0 && g.deck(Runner).any => $(says(self, "trash the top card of the stack"), TrashTopEffect(Runner, 1, true))
            case _ => $
        }
    }

    object EleSmokeScovak extends RH("11066") {
        override def recurring(self : Card)(implicit g : Game) = 1
        override def breakerCredits(self : Card, breaker : Card)(implicit g : Game) = g.isIcebreaker(breaker)
    }

    object JemisonAstronautics extends RH("12016") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Forfeited(c) if g.installedCards(Corp).any =>
                val n = c.data.agendaPoints.|(0) + 1
                $(says(self, "place " + n + " advancement counters"), ChooseCardEffect(Corp, g.installedCards(Corp), RHAdvancePurpose(self, n), "Jemison Astronautics: place " + n + " advancement counters on an installed card", false))
            case _ => $
        }
    }

    object Los extends RH("12025") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Rezzed(c) if c.data.isIce && firstTime(self, "los") => $(MarkUsedEffect(self, "los"), says(self, "gain 2 credits"), gain(Runner, 2))
            case _ => $
        }
    }

    object AgInfusion extends RH("12052") {
        override def abilities(self : Card)(implicit g : Game) = {
            val r = g.run.%(r => !r.ended && r.phase == ApproachIcePhase)
            val ice = r./~(_.ice).%(c => !g.isRezzed(c))
            $(Ability("trash the approached unrezzed ice: the Runner moves to another server", NoCost, ice.$./~(c => $(TrashEffect(c, Corp, false), ChooseServerForAction(Corp, self, g.servers.but(r.get.server), false, "aginfusion", "AgInfusion: choose the server the Runner moves to"))), once = once("aginfusion"), ok = ice.any, reason = "the Runner is not approaching unrezzed ice"))
        }
        override def performRH(a : CardAction)(implicit g : Game) = a match {
            case ServerPickedAction(self, Some(s), _) =>
                if (g.run.exists(!_.ended)) {
                    g.pending = g.pending.%!(x => x.is[RunStep] || x.is[PaidWindowAction])
                    g.push(RedirectRunEffect(s, g.ice(s).num, true))
                }
                NextAction
            case ServerPickedAction(self, None, _) => NextAction
        }
    }

    object AliceMerchant extends RH("12061") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(Archives) if g.successfulRunsThisTurn.count(_ == Archives) == 1 && g.hand(Corp).any => $(MessageEffect(Runner, "forces the Corp to trash 1 card from HQ (Alice Merchant)"), CorpTrashFromHandEffect(1))
            case _ => $
        }
    }

    // the interrupt (Runner cards that would be trashed are set aside, the Corp may remove 1 from the game) is
    // FinalRotRules.finalRotPreTriggers / FrSkorpiosCheckEffect: it resolves before any other ability of the trash
    object Skorpios extends RH("13041") {
        override def chosenRH(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            if (firstTime(p.card, "skorpios"))
                cards.single.%(c => g.zone(c) == DiscardZone(Runner)).foreach { c =>
                    g.markUsed(p.card, "skorpios")
                    g.push(says(p.card, "remove a trashed card from the game"), RemoveFromGameEffect(c))
                }
            NextAction
        }
    }

    object AsaGroup extends RH("21009") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Installed(Corp, c) if firstTime(self, "asa") =>
                val l = g.serverOf(c).$./~(s => g.hand(Corp).%(x => !x.data.isAgenda && (x.data.isIce || x.data.isUpgrade || (x.data.isAsset && s.isRemote)) && g.canSpend(Corp, g.installCost(x, |(s)), Spend.install(x, |(s))))./(x => (x, s)))
                $(MarkUsedEffect(self, "asa")) ++ l.any.$(says(self, "install a card in the same server")) ++ l.any.$(ChooseCardEffect(Corp, l.map(_._1), InstallPurpose(false, g.serverOf(c)), "Asa Group: install a non-agenda card from HQ in the same server?", true))
            case _ => $
        }
    }

    object KabonesaWu extends RH("21025") {
        override def abilities(self : Card)(implicit g : Game) = {
            val l = g.deck(Runner).%(c => c.data.isProgram && !g.has(c, "Virus") && g.canAffordInstall(c, 1)).sortBy(_.title)
            $(Ability("install a non-virus program from the stack paying 1 credit less", click(), $(ChooseCardEffect(Runner, l, RHKabonesaPurpose(self), "Kabonesa Wu: choose a program to install", true), ShuffleDeckEffect(Runner)), click = true, ok = g.deck(Runner).any, reason = "the stack is empty"))
        }
        override def chosenRH(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(c => g.push(RunnerInstallPayingEffect(c, None, 1), SetChoiceEffect(c, "kabonesa")))
            NextAction
        }
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnEnds(Runner) => g.installedCards(Runner).%(c => g.state(c).choice == "kabonesa")./(c => RemoveFromGameEffect(c))
            case _ => $
        }
    }

    object AzmariEdTech extends RH("21054") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnEnds(Corp) =>
                $(ChooseOneEffect(Corp, "Azmari EdTech: name a card type", $("Event", "Resource", "Program", "Hardware", "None")./(k => Alt(k, $(SetMemoEffect(self, k))))))
            case Installed(Runner, c) if g.state(self).memo == c.data.kind && firstTime(self, "azmari") => $(MarkUsedEffect(self, "azmari"), says(self, "gain 2 credits"), gain(Corp, 2))
            case Played(Runner, c) if g.state(self).memo == c.data.kind && firstTime(self, "azmari") => $(MarkUsedEffect(self, "azmari"), says(self, "gain 2 credits"), gain(Corp, 2))
            case _ => $
        }
    }

    object FourOneNine extends RH("21063") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Installed(Corp, c) if firstTime(self, "419") =>
                $(MarkUsedEffect(self, "419")) ++ (g.turn > 0 && !g.isRezzed(c)).$(OptionalEffect(Runner, "419: expose the installed card unless the Corp pays 1 credit?", $(says(self, "expose the installed card"), RH419Action(self, c)), $))
            case _ => $
        }
        override def performRH(a : CardAction)(implicit g : Game) = a match {
            case RH419Action(self, c) =>
                if (g.credits(Corp) >= 1)
                    g.push(ChooseOneEffect(Corp, "419: pay 1 credit to prevent the card from being exposed?", $(Alt("Pay 1 credit", $(lose(Corp, 1))), Alt("Let the Runner expose it", $(ExposeEffect($(c)))))))
                else
                    g.push(ExposeEffect($(c)))
                NextAction
        }
    }

    object SSOIndustries extends RH("21077") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnEnds(Corp) =>
                val n = g.installedCards(Corp).%(c => c.data.isAgenda && g.isRezzed(c))./(_.data.agendaPoints.|(0)).sum
                val l = g.allIce.%(c => g.advancement(c) == 0)
                (n > 0 && l.any).$(says(self, "place advancement counters on ice")) ++ (n > 0 && l.any).$(ChooseCardEffect(Corp, l, RHAdvancePurpose(self, n), "SSO Industries: place " + n + " advancement counters on a piece of ice?", true))
            case _ => $
        }
    }

    object FreedomKhumalo extends RH("21081") {
        override def accessAbility(self : Card, target : Card)(implicit g : Game) = {
            val x = target.data.cost.|(0)
            val total = g.installedCards(Runner)./(c => g.counter(c, "virus")).sum
            (!target.data.isAgenda && !g.zone(target).is[DiscardZone] && g.run.exists(!_.cannotStealOrTrash)).?(Ability("trash the accessed card spending " + x + " virus counters", NoCost, $(RHSpendVirusEffect(x), TrashEffect(target, Runner, true)), once = once("freedom"), ok = total >= x && firstTime(self, "freedom"), reason = "not enough virus counters"))
        }
    }

    object MtiMwekundu extends RH("21114") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case ApproachServer(s) if firstTime(self, "mti") && g.hand(Corp).exists(_.data.isIce) && g.run.exists(r => !r.ended && !r.accessOnly) =>
                $(OptionalEffect(Corp, "Mti Mwekundu: install a piece of ice from HQ at the innermost position of the attacked server?", $(MarkUsedEffect(self, "mti"), says(self, "install a piece of ice"), ChooseCardEffect(Corp, g.hand(Corp).%(_.data.isIce), RHMtiPurpose(self, s), "Mti Mwekundu: choose a piece of ice", false)), $))
            case _ => $
        }
        override def chosenRH(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case RHMtiPurpose(self, s) =>
                cards.single.foreach { c =>
                    if (g.run.exists(r => !r.ended && r.server == s))
                        g.push(RHInstallInnermostEffect(c, s), MoveRunnerEffect(1, g.ice(s).any))
                }
                NextAction
        }
    }

    object NathanielGnatHall extends RH("22001") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Runner) if g.hand(Runner).num <= 2 => $(says(self, "gain 1 credit"), gain(Runner, 1))
            case _ => $
        }
    }

    object LizaTalkingThunder extends RH("22008") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(s) if s.isCentral && g.successfulRunsThisTurn.%(_.isCentral).num == 1 => $(says(self, "draw 2 cards and take 1 tag"), draw(Runner, 2), tag(1))
            case _ => $
        }
    }

    object AkikoNisei extends RH("22015") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case BreachServer(RnD) => $(PsiEffect($(MessageEffect(Runner, "accesses 1 additional card (Akiko Nisei)"), AccessBonusEffect(RnD, 1)), $))
            case _ => $
        }
    }

    object Sportsmetal extends RH("22026") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case AgendaScored(_) | AgendaStolen(_) => $(says(self, "gain 2 credits or draw 2 cards"), ChooseOneEffect(Corp, "Sportsmetal: choose one", $(Alt("Gain 2 credits", $(gain(Corp, 2))), Alt("Draw 2 cards", $(draw(Corp, 2)), g.deck(Corp).num >= 2))))
            case _ => $
        }
    }

    object SaraswatiMnemonics extends RH("22034") {
        override def abilities(self : Card)(implicit g : Game) = {
            val l = g.hand(Corp).%(c => c.data.isAsset || c.data.isAgenda || c.data.isUpgrade)
            $(Ability("install a card from HQ in a remote server and place 1 advancement counter on it", $(ClickCost(1), CreditCost(1)), $(ChooseCardEffect(Corp, l, RHSaraswatiPurpose(self), "Saraswati Mnemonics: choose a card to install", false)), click = true, ok = l.any, reason = "nothing to install"))
        }
        override def chosenRH(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach(c => g.push(ChooseServerForAction(Corp, p.card, g.remotes, true, "saraswati:" + c.code + ":" + c.n, "Saraswati Mnemonics: choose a remote server")))
            NextAction
        }
        override def performRH(a : CardAction)(implicit g : Game) = a match {
            case ServerPickedAction(self, s, key) =>
                val parts = key.split(':')
                val c = Card(parts(1), parts(2).toInt)
                g.push(CorpInstallEffect(c, s, false), PlaceAdvancementEffect(c, 1), AddFlagEffect(Flag("rh-no-rez", |(c), None, "halfturn", g.halfTurn + 1, $)), MarkUsedEffect(c, "cannot-score"))
                NextAction
        }
    }

    object AcmeConsulting extends RH("22042") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case EncounterIce(ice) if g.isRezzed(ice) && g.serverOf(ice).exists(s => g.ice(s).lastOption.has(ice)) && g.counter(self, "acme") == 0 => $(RHAcmeAction(self, true))
            case EncounterEnds(_) | RunEnds(_, _, _) if g.counter(self, "acme") > 0 => $(RHAcmeAction(self, false))
            case _ => $
        }
        override def performRH(a : CardAction)(implicit g : Game) = a match {
            case RHAcmeAction(self, true) =>
                g.state(self).counters += "acme" -> 1
                g.log(Runner, "is considered to have 1 additional tag during this encounter (Acme Consulting)")
                // a virtual tag: counted, not taken, cannot be removed (FinalRotRules.frVirtualTags)
                g.push(FrVirtualTagEffect(1))
                NextAction
            case RHAcmeAction(self, false) =>
                g.state(self).counters += "acme" -> 0
                g.push(FrVirtualTagEffect(-1))
                NextAction
        }
    }

    object TheOutfit extends RH("22050") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case BadPubGained(_) => $(says(self, "gain 3 credits"), gain(Corp, 3))
            case _ => $
        }
    }

    object CyberBureau extends RH("24001") {
        override def onSetup(self : Card)(implicit g : Game) = $(DrawEffect(Corp, 5))
        override def startingHandBonus(self : Card)(implicit g : Game) = 5
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case FrBeforeFirstTurn(Corp) if !flipped(self) =>
                $(says(self, "install up to 5 cards"), InstallLoopAction(self, Corp, 5, 0, true, "hand", $), RHBureauRezAction(self, 20), FlipAction(self, "Detective's Bureau: Upholding the Law"))
            case RunInitiated(_) if flipped(self) && firstTime(self, "detective") => $(MarkUsedEffect(self, "detective"), RHBureauDrainAction(self))
            case _ => $
        }
        override def abilities(self : Card)(implicit g : Game) =
            $(Ability("gain 3 credits or draw 3 cards", click(), $(ChooseOneEffect(Corp, "Detective's Bureau: choose one", $(Alt("Gain 3 credits", $(gain(Corp, 3))), Alt("Draw 3 cards", $(draw(Corp, 3)), g.deck(Corp).num >= 3)))), click = true, ok = flipped(self), reason = "not flipped"))
        override def performRH(a : CardAction)(implicit g : Game) = a match {
            case RHBureauRezAction(self, pool) =>
                val l = g.installedCards(Corp).%(c => !g.isRezzed(c) && !c.data.isAgenda && g.impl(c).canRez(c) && g.canSpend(Corp, (g.rezCost(c) - pool).atLeast(0), Spend.rez(c)))
                if (l.any)
                    g.push(ChooseCardEffect(Corp, l, RHBureauRezPurpose(self, pool), "Cyber Bureau: rez a card (" + pool + " credits of discount left)?", true))
                NextAction
            case RHBureauDrainAction(self) =>
                val n = g.agendaPoints(Runner).atMost(g.credits(Runner)).atLeast(0)
                if (n > 0)
                    g.push(says(self, "drain " + n + " credits"), lose(Runner, n), gain(Corp, n))
                NextAction
        }
        override def chosenRH(p : CardPurpose, cards : $[Card])(implicit g : Game) = p match {
            case RHBureauRezPurpose(self, pool) =>
                cards.single.foreach { c =>
                    val cost = g.rezCost(c)
                    val d = pool.atMost(cost)
                    g.spendCredits(Corp, cost - d, Spend.rez(c))
                    g.log(Corp, "pays", (cost - d).credits, "(" ~ d.hl ~ " less)")
                    g.push(RezEffect(c, true), RHBureauRezAction(self, pool - d))
                }
                NextAction
        }
    }

    object GabrielSantiago extends RH("25020") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case SuccessfulRun(HQ) if g.successfulRunsThisTurn.count(_ == HQ) == 1 => $(says(self, "gain 2 credits"), gain(Runner, 2))
            case _ => $
        }
    }

    object LeelaPatel extends RH("25021") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case AgendaScored(_) | AgendaStolen(_) if unrezzedInstalled.any =>
                $(says(self, "add an unrezzed card to HQ"), ChooseCardEffect(Runner, unrezzedInstalled, ReturnToHandPurpose, "Leela Patel: choose an unrezzed card to add to HQ", false))
            case _ => $
        }
    }

    object ChaosTheory extends RH("25040") {
        override def memory(self : Card)(implicit g : Game) = 1
    }

    object HBStrongerTogether extends RH("25066") {
        override def iceStrengthMod(self : Card, ice : Card)(implicit g : Game) = g.has(ice, "Bioroid").?(1).|(0)
    }

    object SeidrLaboratories extends RH("25067") {
        // "The first time each turn the Runner loses or spends [click] during a run" (ClicksSpentOrLost is fired during runs only)
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case ClicksSpentOrLost(Runner, n) if n > 0 && firstTime(self, "seidr") =>
                $(MarkUsedEffect(self, "seidr")) ++ g.discard(Corp).any.$(ChooseCardEffect(Corp, g.discard(Corp), RHToTopPurpose(self), "Seidr Laboratories: add 1 card from Archives to the top of R&D?", true))
            case _ => $
        }
    }

    object JintekiReplicatingPerfection extends RH("25085") {
        override def allowsRun(self : Card, server : Server)(implicit g : Game) = server.isCentral || g.runsThisTurn.exists(_.isCentral)
    }

    object NBNMakingNews extends RH("25104") {
        override def recurring(self : Card)(implicit g : Game) = 2
        override def paysFor(self : Card, spend : Spend)(implicit g : Game) = spend.is("trace")
    }

    object SparkAgency extends RH("25105") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case Rezzed(c) if g.has(c, "Advertisement") && firstTime(self, "spark") => $(MarkUsedEffect(self, "spark"), says(self, "make the Runner lose 1 credit"), lose(Runner, 1))
            case _ => $
        }
    }

    object BlueSun extends RH("25123") {
        override def triggers(self : Card, e : Event)(implicit g : Game) = e match {
            case TurnBegins(Corp) if g.installedCards(Corp).exists(g.isRezzed) =>
                $(ChooseCardEffect(Corp, g.installedCards(Corp).%(g.isRezzed), RHBlueSunPurpose(self), "Blue Sun: add a rezzed card to HQ and gain credits equal to its rez cost?", true))
            case _ => $
        }
        override def chosenRH(p : CardPurpose, cards : $[Card])(implicit g : Game) = {
            cards.single.foreach { c =>
                val n = g.rezCost(c)
                g.push(says(p.card, "add a rezzed card to HQ and gain " + n + " credits"), ReturnToHandEffect(c), gain(Corp, n))
            }
            NextAction
        }
    }
}


object RotIdentityCards {
    import RotIdentityCards1._
    import RotIdentityCards2._

    val all : $[CardImpl] = $(
        TheShadow, TheMasque, Wyvern, Boris, Jamie, StrategicInnovations, SyntheticSystems, InformationDynamics, FringeApplications,
        Noise, Kate, HBEngineeringTheFuture, TheCollective, ChronosProtocolHB, Whizzard, WeylandBecauseWeBuiltIt, Andromeda,
        NBNTheWorldIsYours, CerebralImaging, CustomBiotics, NEXTDesign, TheProfessor, Exile, GRNDL, HarmonyMedtech, NiseiDivision,
        TenninInstitute, IainStirling, Silhouette, NasirMeidan, TheFoundry, IndustrialGenomics, ArgusSecurity, GagarinDeepSpace,
        TitanTransnational, EdwardKim, MaxX, ValenciaEstevez, JintekiBiotech, HayleyKaplan, CyberneticsDivision, ArmandGeistWalker,
        HaarpsichordStudios, LaramyFisk, ChronosProtocolJinteki,
        SYNC, NewAngelesSol, Apex, Adam, SunnyLebeau, JesminderSareen, PalanaFoods, NeroSevern, Harishchandra, NullWhistleblower,
        NBNControllingTheMessage, Khan, WeylandBuilderOfNations, OmarKeung, JintekiPotentialUnleashed, EleSmokeScovak,
        JemisonAstronautics, Los, AgInfusion, AliceMerchant, Skorpios, AsaGroup, KabonesaWu, AzmariEdTech, FourOneNine, SSOIndustries,
        FreedomKhumalo, MtiMwekundu, NathanielGnatHall, LizaTalkingThunder, AkikoNisei, Sportsmetal, SaraswatiMnemonics,
        AcmeConsulting, TheOutfit, CyberBureau, GabrielSantiago, LeelaPatel, ChaosTheory, HBStrongerTogether, SeidrLaboratories,
        JintekiReplicatingPerfection, NBNMakingNews, SparkAgency, BlueSun,
    )
}
