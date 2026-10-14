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

import hrf.meta._
import hrf.elem._

// Save Replay embeds only the card images loaded in this session (UI.saveReplay loads the cards of the game first)
object Meta extends MetaGame with hrf.quine.EmbedLoadedOnDemandAssetsOnly { mmm =>
    val gaming = netrunner.gaming

    type F = Faction

    def tagF = implicitly

    val name = "netrunner"
    val label = "Netrunner"

    val factions = $(Corp, Runner)

    val minPlayers = 2

    val options = $

    override val insignificantSeating : Boolean = true

    val quickMin = 2
    val quickMax = 2

    def randomGameName() = {
        val n = $("Run", "Breach", "Trace", "Ice", "Rez", "Access", "Steal", "Flatline", "Purge", "Advance").shuffle
        val c = $("on", "against", "versus", "through", "and", "of", "in", "at").shuffle
        n.head + " " + c.head + " " + n.last
    }

    def validateFactionCombination(factions : $[Faction]) =
        if (factions.num != 2)
            ErrorResult("Netrunner needs exactly two players")
        else if (factions.distinct.num != 2)
            ErrorResult("Select Corp and Runner")
        else
            InfoResult("")

    def validateFactionSeatingOptions(factions : $[Faction], options : $[O]) = InfoResult("")

    def factionName(f : Faction) = f.name
    def factionElem(f : Faction) = f.name.styled(f)

    def createGame(factions : $[Faction], options : $[O]) = new Game(factions, options)

    def getBots(f : Faction) = $("Smart", "Basic", "Random")

    def getBot(f : Faction, b : String) = (f, b) match {
        case (f : Faction, "Random") => new RandomBot(f)
        case (f : Faction, "Basic") => new BasicBot(f)
        case (f : Faction, _) => new SmartBot(f)
    }

    def defaultBot(f : Faction) = "Smart"

    def writeFaction(f : Faction) = f.name
    def parseFaction(s : String) : |[Faction] = factions.%(_.name == s).single

    def writeOption(f : O) = "n/a"
    def parseOption(s : String) = $

    def parseAction(s : String) : Action = Serialize.parseAction(s)
    def writeAction(a : Action) : String = Serialize.write(a)

    val start = StartAction(gaming.version)

    override def tips = super.tips ++ $(
        "Click on a card image to read it.",
        "Use the Manual menu to adjust credits, clicks, tags and counters when a card is not implemented.",
        "Paste a NetrunnerDB decklist to import a deck.",
    )

    val assets =
    ConditionalAssetsList((factions : $[Faction], options : $[O]) => true, lzy = Laziness.OnDemand)(
        Cards.all./(c => ImageAsset(c.code)) ++
        $(
            ImageAsset("corp-back"),
            ImageAsset("runner-back"),
            ImageAsset("faction-corp-hb"),
            ImageAsset("faction-corp-jinteki"),
            ImageAsset("faction-corp-nbn"),
            ImageAsset("faction-corp-weyland"),
            ImageAsset("faction-runner-anarch"),
            ImageAsset("faction-runner-criminal"),
            ImageAsset("faction-runner-shaper"),
            ImageAsset("faction-runner-adam"),
            ImageAsset("faction-runner-apex"),
            ImageAsset("faction-runner-sunny"),
        )
    ) :: $
}
