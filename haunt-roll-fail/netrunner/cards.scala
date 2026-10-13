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

import hrf.base._
import hrf.elem._

// Static card database. Data lives in generated files cards-data-*.scala (see tools/gen-cards.py):
// one card per line, fields separated by TAB, in the order of CardData's constructor, with
//   - optional ints written as "" when absent
//   - booleans as "1"/"0"
//   - subtypes and legal formats joined with "|"
//   - newlines inside text written as the two characters "\n"
case class CardData(
    code : String,
    title : String,
    side : String,
    faction : String,
    kind : String,
    subtypes : $[String],
    cost : |[Int],
    strength : |[Int],
    advancementCost : |[Int],
    agendaPoints : |[Int],
    memory : |[Int],
    trashCost : |[Int],
    influence : |[Int],
    baseLink : |[Int],
    minDeckSize : |[Int],
    influenceLimit : |[Int],
    unique : Boolean,
    deckLimit : Int,
    text : String,
    setCode : String,
    setName : String,
    rotated : Boolean,
    legal : $[String]
) {
    def isCorp = side == "Corp"
    def isRunner = side == "Runner"
    def isIdentity = kind == "Identity"
    def isAgenda = kind == "Agenda"
    def isAsset = kind == "Asset"
    def isUpgrade = kind == "Upgrade"
    def isOperation = kind == "Operation"
    def isIce = kind == "ICE"
    def isEvent = kind == "Event"
    def isHardware = kind == "Hardware"
    def isProgram = kind == "Program"
    def isResource = kind == "Resource"
    def has(subtype : String) = subtypes.contains(subtype)
    def isIcebreaker = has("Icebreaker")
    def isVirus = has("Virus")
    def legalIn(format : String) = legal.contains(format)
}

object Cards {
    private def parseLine(l : String) : CardData = {
        val f = l.split('\t').$.padTo(23, "")
        def oi(s : String) : |[Int] = if (s.trim == "") None else Some(s.trim.toInt)
        def b(s : String) = s.trim == "1"
        def list(s : String) = s.split('|').$./(_.trim).but("")
        CardData(f(0), f(1), f(2), f(3), f(4), list(f(5)), oi(f(6)), oi(f(7)), oi(f(8)), oi(f(9)), oi(f(10)), oi(f(11)), oi(f(12)), oi(f(13)), oi(f(14)), oi(f(15)), b(f(16)), oi(f(17)).|(3), f(18).replace("\\n", "\n"), f(19), f(20), b(f(21)), list(f(22)))
    }

    lazy val all : $[CardData] = CardsData.chunks./~(_.split('\n').$./(_.trim).but(""))./(parseLine)

    lazy val byCode : Map[String, CardData] = all./(c => c.code -> c).toMap

    lazy val byTitle : Map[String, CardData] = all.groupBy(_.title)./((t, l) => t -> l.maxBy(_.code))

    def apply(code : String) : CardData = byCode.get(code).|(throw new Error("unknown card " + code))

    def get(code : String) : |[CardData] = byCode.get(code)

    // (setCode, setName, cycleCode) in release order
    lazy val sets : $[(String, String, String)] = CardsData.sets

    lazy val sides : Map[String, $[CardData]] = all.groupBy(_.side)

    def ofSet(setCode : String) : $[CardData] = all.%(_.setCode == setCode)

    def ofFormat(format : String) : $[CardData] = all.%(_.legalIn(format))
}

// A physical copy n (1-based) of a printing, as used in game state and actions.
case class Card(code : String, n : Int) extends Record with Elementary {
    def data = Cards(code)
    def title = data.title
    def elem = title.hl
    override def toString = title + "#" + n
}
