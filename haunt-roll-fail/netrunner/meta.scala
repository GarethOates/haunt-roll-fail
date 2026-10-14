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
import hrf.options._

// the format of the game (game setup option, recorded with the game): the cards legal in it (Casual: any card). Rebirth's
// identities and the deck legality warnings follow it.
trait FormatOption extends GameOption with OneOfGroup {
    val group = "Format".hh
    val format : String
}

case object CasualFormat extends FormatOption {
    val valueOn = "Casual (any card)".hh
    val format = "casual"
}

case object StandardFormat extends FormatOption {
    val valueOn = "Standard".hh
    val format = "standard"
}

case object StartupFormat extends FormatOption {
    val valueOn = "Startup".hh
    val format = "startup"
}

case object EternalFormat extends FormatOption {
    val valueOn = "Eternal".hh
    val format = "eternal"
}

object Formats {
    val all : $[FormatOption] = $(CasualFormat, StandardFormat, StartupFormat, EternalFormat)
    def name(format : String) : String = all.%(_.format == format).starting./(_.valueOn.text).|(format)
    def of(options : $[GameOption]) : String = options.of[FormatOption].starting./(_.format).|("casual")
}

// Save Replay embeds only the card images loaded in this session (UI.saveReplay loads the cards of the game first)
object Meta extends MetaGame with hrf.quine.EmbedLoadedOnDemandAssetsOnly { mmm =>
    val gaming = netrunner.gaming

    type F = Faction

    def tagF = implicitly

    val name = "netrunner"
    val label = "Netrunner"

    val factions = $(Corp, Runner)

    val minPlayers = 2

    val options : $[O] = Formats.all

    override def defaultsFor(n : Int, l : $[Faction]) : $[O] = $(CasualFormat)

    override def quickOptions = Map[O, Double](CasualFormat -> 1.0)

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

    def validateFactionSeatingOptions(factions : $[Faction], options : $[O]) =
        if (options.of[FormatOption].num > 1) ErrorResult("Choose one format")
        else InfoResult("Format: " + Formats.name(Formats.of(options)))

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

    def writeOption(f : O) = f.toString
    def parseOption(s : String) = options.%(_.toString == s)

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
