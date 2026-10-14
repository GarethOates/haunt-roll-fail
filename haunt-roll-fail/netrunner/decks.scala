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


case class Deck(name : String, side : Faction, identity : String, list : $[(String, Int)]) {
    def codes : $[String] = identity +: list./~((c, n) => n.times(c))
    def identityTitle = Cards.get(identity)./(_.title).|(identity)
}

case class DeckCheck(ok : Boolean, message : String)

object Decks {
    // System Gateway starter-style decks (codes from the sg set)
    val presets : $[Deck] = $(
        Deck("Weyland Starter", Corp, "30059", $(
            "30067" -> 3, // Offworld Office
            "30068" -> 2, // Orbital Superiority
            "30069" -> 1, // Send a Message
            "30060" -> 1, // Above the Law
            "30070" -> 3, // Superconducting Hub
            "30071" -> 3, // Regolith Mining License
            "30061" -> 2, // Clearinghouse
            "30053" -> 2, // Spin Doctor
            "30066" -> 1, // Malapert Data Vault
            "30075" -> 3, // Hedge Fund
            "30064" -> 2, // Government Subsidy
            "30065" -> 2, // Retribution
            "30056" -> 1, // Predictive Planogram
            "30072" -> 3, // Palisade
            "30063" -> 2, // Pharos
            "30062" -> 2, // Ballista
            "30074" -> 3, // Whitespace
            "30073" -> 2, // Tithe
            "30046" -> 2, // Diviner
        )),
        Deck("Jinteki Starter", Corp, "30043", $(
            "30044" -> 1, // Longevity Serum
            "30067" -> 3, // Offworld Office
            "30069" -> 1, // Send a Message
            "30070" -> 3, // Superconducting Hub
            "30068" -> 2, // Orbital Superiority
            "30045" -> 2, // Urtica Cipher
            "30071" -> 2, // Regolith Mining License
            "30037" -> 2, // Nico Campaign
            "30053" -> 1, // Spin Doctor
            "30050" -> 1, // Anoetic Void
            "30075" -> 3, // Hedge Fund
            "30048" -> 2, // Hansei Review
            "30049" -> 1, // Neurospike
            "30040" -> 2, // Seamless Launch
            "30046" -> 3, // Diviner
            "30047" -> 2, // Karuna
            "30072" -> 3, // Palisade
            "30074" -> 2, // Whitespace
            "30073" -> 2, // Tithe
            "30039" -> 2, // Bran 1.0
        )),
        Deck("Haas-Bioroid Starter", Corp, "30035", $(
            "30036" -> 1, // Luminal Transubstantiation
            "30067" -> 3, // Offworld Office
            "30069" -> 1, // Send a Message
            "30070" -> 3, // Superconducting Hub
            "30068" -> 2, // Orbital Superiority
            "30037" -> 3, // Nico Campaign
            "30071" -> 2, // Regolith Mining License
            "30053" -> 1, // Spin Doctor
            "30042" -> 2, // Manegarm Skunkworks
            "30075" -> 3, // Hedge Fund
            "30040" -> 2, // Seamless Launch
            "30041" -> 2, // Sprint
            "30056" -> 1, // Predictive Planogram
            "30038" -> 2, // Ansel 1.0
            "30039" -> 2, // Bran 1.0
            "30072" -> 3, // Palisade
            "30074" -> 2, // Whitespace
            "30073" -> 2, // Tithe
            "30054" -> 2, // Funhouse
            "30046" -> 1, // Diviner
        )),
        Deck("NBN Starter", Corp, "30051", $(
            "30052" -> 1, // Tomorrow's Headline
            "30067" -> 3, // Offworld Office
            "30069" -> 1, // Send a Message
            "30070" -> 3, // Superconducting Hub
            "30068" -> 2, // Orbital Superiority
            "30053" -> 2, // Spin Doctor
            "30071" -> 2, // Regolith Mining License
            "30061" -> 1, // Clearinghouse
            "30058" -> 2, // AMAZE Amusements
            "30075" -> 3, // Hedge Fund
            "30056" -> 2, // Predictive Planogram
            "30057" -> 2, // Public Trail
            "30065" -> 1, // Retribution
            "30048" -> 2, // Hansei Review
            "30054" -> 2, // Funhouse
            "30055" -> 3, // Ping
            "30072" -> 3, // Palisade
            "30074" -> 2, // Whitespace
            "30073" -> 2, // Tithe
            "30063" -> 1, // Pharos
        )),
        Deck("Anarch Starter", Runner, "30001", $(
            "30006" -> 2, // Cleaver
            "30005" -> 2, // Buzzsaw
            "30015" -> 2, // Carmen
            "30008" -> 2, // Leech
            "30007" -> 2, // Fermenter
            "30004" -> 2, // Botulus
            "30032" -> 1, // Mayfly
            "30003" -> 2, // Carnivore
            "30031" -> 2, // T400 Memory Diamond
            "30009" -> 2, // Cookbook
            "30033" -> 2, // Smartware Distributor
            "30034" -> 2, // Verbal Plasticity
            "30027" -> 2, // Telework Contract
            "30030" -> 3, // Sure Gamble
            "30002" -> 3, // Wildcat Strike
            "30028" -> 3, // Jailbreak
            "30029" -> 2, // Overclock
            "30020" -> 2, // Creative Commission
            "30012" -> 2, // Tread Lightly
        )),
        Deck("Shaper Starter", Runner, "30019", $(
            "30026" -> 2, // Unity
            "30025" -> 2, // Echelon
            "30006" -> 2, // Cleaver
            "30005" -> 2, // Buzzsaw
            "30024" -> 2, // Conduit
            "30032" -> 1, // Mayfly
            "30008" -> 1, // Leech
            "30023" -> 2, // Pantograph
            "30022" -> 2, // DZMZ Optimizer
            "30031" -> 2, // T400 Memory Diamond
            "30027" -> 3, // Telework Contract
            "30033" -> 2, // Smartware Distributor
            "30034" -> 2, // Verbal Plasticity
            "30030" -> 3, // Sure Gamble
            "30020" -> 3, // Creative Commission
            "30021" -> 2, // VRcation
            "30028" -> 3, // Jailbreak
            "30029" -> 2, // Overclock
            "30012" -> 2, // Tread Lightly
        )),
        Deck("Criminal Starter", Runner, "30010", $(
            "30015" -> 2, // Carmen
            "30016" -> 2, // Marjanah
            "30026" -> 2, // Unity
            "30005" -> 1, // Buzzsaw
            "30017" -> 2, // Tranquilizer
            "30007" -> 2, // Fermenter
            "30032" -> 1, // Mayfly
            "30014" -> 2, // Pennyshaver
            "30013" -> 2, // Docklands Pass
            "30031" -> 2, // T400 Memory Diamond
            "30018" -> 3, // Red Team
            "30033" -> 2, // Smartware Distributor
            "30034" -> 2, // Verbal Plasticity
            "30030" -> 3, // Sure Gamble
            "30028" -> 3, // Jailbreak
            "30012" -> 3, // Tread Lightly
            "30011" -> 2, // Mutual Favor
            "30029" -> 2, // Overclock
            "30020" -> 2, // Creative Commission
        )),
        // System Update 2021 / Midnight Sun decks (used by the bot stress test)
        Deck("HB Architects of Tomorrow", Corp, "31040", $(
            "31041" -> 3, "27004" -> 3, "33031" -> 3, "33032" -> 3, "31062" -> 1,
            "31042" -> 2, "33033" -> 2, "31043" -> 3, "31044" -> 2, "31045" -> 2, "31046" -> 2, "31047" -> 1, "31048" -> 2, "31049" -> 1,
            "33034" -> 1, "33035" -> 2, "33036" -> 2, "33037" -> 2, "33038" -> 1, "31080" -> 2, "31081" -> 2, "30075" -> 3,
        )),
        Deck("Jinteki Personal Evolution", Corp, "31050", $(
            "31051" -> 3, "31052" -> 3, "33039" -> 2, "33040" -> 3, "33031" -> 2, "31041" -> 1,
            "31053" -> 2, "31054" -> 3, "31055" -> 3, "31056" -> 2, "31057" -> 2, "31058" -> 2, "31059" -> 2, "27005" -> 2,
            "33041" -> 2, "33042" -> 1, "33043" -> 3, "33044" -> 2, "33045" -> 2, "33046" -> 1, "33047" -> 2,
        )),
        Deck("NBN Near-Earth Hub", Corp, "31060", $(
            "31061" -> 3, "31062" -> 3, "33049" -> 3, "27004" -> 3, "31071" -> 2,
            "31063" -> 3, "31064" -> 2, "31065" -> 3, "31066" -> 2, "31067" -> 3, "31068" -> 1, "31069" -> 2, "27006" -> 2,
            "33050" -> 2, "33052" -> 2, "33053" -> 2, "33054" -> 3, "33055" -> 1, "33056" -> 1, "30075" -> 2,
        )),
        Deck("NBN Pravdivost Consulting", Corp, "33048", $(
            "33049" -> 3, "31062" -> 2, "31061" -> 3, "27004" -> 2, "31072" -> 1, "31073" -> 1, "31071" -> 1,
            "33050" -> 3, "33051" -> 2, "33052" -> 3, "33053" -> 3, "33054" -> 3, "33055" -> 2, "33056" -> 2,
            "31063" -> 2, "31065" -> 2, "31067" -> 2, "31068" -> 2, "31080" -> 3, "31081" -> 2, "31082" -> 1,
        )),
        Deck("Weyland Building a Better World", Corp, "31070", $(
            "31071" -> 3, "31072" -> 3, "31073" -> 3, "33058" -> 2, "27004" -> 1,
            "31074" -> 2, "31075" -> 2, "31076" -> 3, "31077" -> 3, "31078" -> 2, "31079" -> 2, "27007" -> 2,
            "33059" -> 2, "33060" -> 2, "33061" -> 3, "33062" -> 2, "33063" -> 2, "33064" -> 1, "33065" -> 1, "30075" -> 3, "31082" -> 1,
        )),
        Deck("Weyland Ob Superheavy Logistics", Corp, "33057", $(
            "33058" -> 3, "31073" -> 3, "31072" -> 2, "31071" -> 3, "27004" -> 1,
            "33059" -> 3, "33060" -> 3, "33061" -> 3, "33062" -> 3, "33063" -> 3, "33064" -> 2, "33065" -> 2,
            "31077" -> 3, "31076" -> 2, "31075" -> 1, "31079" -> 1, "31080" -> 3, "31081" -> 2, "30075" -> 2,
        )),
        Deck("Anarch Quetzal", Runner, "31001", $(
            "31006" -> 3, "31008" -> 2, "31007" -> 2, "31005" -> 2, "31003" -> 2, "31004" -> 2, "31009" -> 2, "31010" -> 3, "31011" -> 1, "31012" -> 1,
            "27001" -> 1, "33002" -> 2, "33004" -> 3, "33006" -> 2, "33007" -> 2, "33008" -> 2, "33009" -> 2, "33010" -> 2,
            "31037" -> 3, "31038" -> 1, "31039" -> 2, "30030" -> 3,
        )),
        Deck("Anarch Esa Afontov", Runner, "33001", $(
            "33003" -> 3, "33004" -> 3, "33005" -> 2, "33006" -> 2, "33007" -> 2, "33008" -> 2, "33009" -> 2, "33010" -> 2, "33002" -> 3,
            "31006" -> 2, "31008" -> 2, "31007" -> 2, "31005" -> 2, "31010" -> 3, "31009" -> 1, "31003" -> 2, "31004" -> 2,
            "31037" -> 3, "31039" -> 2, "30030" -> 3,
        )),
        Deck("Criminal Ken Tenma", Runner, "31013", $(
            "31015" -> 2, "31016" -> 2, "31017" -> 2, "31018" -> 3, "31019" -> 2, "31020" -> 2, "31021" -> 2, "31022" -> 2, "31023" -> 2, "31024" -> 1,
            "27002" -> 2, "33012" -> 3, "33013" -> 2, "33014" -> 1, "33015" -> 2, "33016" -> 2, "33017" -> 2, "33018" -> 2, "33019" -> 2, "33020" -> 2,
            "31037" -> 3, "31006" -> 2,
        )),
        Deck("Criminal Sable", Runner, "33011", $(
            "33012" -> 3, "33013" -> 3, "33014" -> 2, "33015" -> 2, "33016" -> 2, "33017" -> 2, "33018" -> 3, "33019" -> 3, "33020" -> 2,
            "31018" -> 2, "31019" -> 2, "31021" -> 2, "31022" -> 1, "31023" -> 2, "31024" -> 2, "31020" -> 1,
            "31037" -> 3, "31038" -> 2, "31039" -> 2, "31006" -> 2, "30030" -> 2,
        )),
        Deck("Shaper Ayla", Runner, "31025", $(
            "31027" -> 3, "31028" -> 2, "31029" -> 2, "31030" -> 2, "31031" -> 2, "31033" -> 2, "31034" -> 2, "31035" -> 2, "31036" -> 2, "27003" -> 2,
            "33022" -> 1, "33023" -> 2, "33024" -> 2, "33026" -> 2, "33027" -> 2, "33028" -> 2, "33029" -> 2, "33030" -> 2,
            "31037" -> 3, "31038" -> 2, "31039" -> 2, "31008" -> 2,
        )),
        Deck("Anarch Reina Roja", Runner, "31002", $(
            "31006" -> 3, "31008" -> 2, "31007" -> 2, "31005" -> 2, "31003" -> 2, "31004" -> 2, "31009" -> 2, "31010" -> 3, "31011" -> 2, "31012" -> 2,
            "27001" -> 2, "33002" -> 2, "33004" -> 3, "33006" -> 2, "33007" -> 2, "33008" -> 2, "33009" -> 1, "33010" -> 2,
            "31037" -> 3, "31038" -> 1, "31039" -> 2, "30030" -> 1,
        )),
        Deck("Criminal Steve Cambridge", Runner, "31014", $(
            "31015" -> 2, "31016" -> 2, "31017" -> 2, "31018" -> 3, "31019" -> 2, "31020" -> 2, "31021" -> 2, "31022" -> 2, "31023" -> 2, "31024" -> 2,
            "27002" -> 2, "33012" -> 3, "33013" -> 2, "33014" -> 1, "33015" -> 2, "33016" -> 2, "33017" -> 2, "33018" -> 2, "33019" -> 2, "33020" -> 2,
            "31037" -> 3, "31006" -> 1,
        )),
        Deck("Shaper Kit", Runner, "31026", $(
            "31027" -> 3, "31028" -> 2, "31029" -> 2, "31030" -> 2, "31031" -> 2, "31032" -> 2, "31033" -> 3, "31034" -> 2, "31035" -> 2, "31036" -> 2, "27003" -> 2,
            "33022" -> 1, "33023" -> 2, "33024" -> 2, "33026" -> 3, "33027" -> 2, "33028" -> 2, "33029" -> 2, "33030" -> 2,
            "31037" -> 3, "31038" -> 2, "31039" -> 2,
        )),
        Deck("Shaper Padma", Runner, "33021", $(
            "33023" -> 3, "33024" -> 3, "33025" -> 1, "33026" -> 3, "33027" -> 3, "33028" -> 2, "33029" -> 3, "33030" -> 3,
            "31027" -> 3, "31028" -> 2, "31029" -> 2, "31033" -> 1, "31030" -> 1, "31035" -> 1, "31036" -> 2, "27003" -> 2, "33022" -> 2,
            "31037" -> 3, "30030" -> 3, "31008" -> 2,
        )),
    ) ++ StartupDecks.presets ++ PHTAIDecks.presets ++ ExtDecks.presets ++ RotIceDecks.presets ++ RotProgramDecks.presets

    def validate(f : Faction, codes : $[String]) : DeckCheck = {
        val unknown = codes.%(c => Cards.get(c).none)
        if (unknown.any)
            return DeckCheck(false, "Unknown cards: " + unknown.join(", "))

        val cards = codes./(Cards(_))
        val ids = cards.%(_.isIdentity)

        if (ids.num != 1)
            return DeckCheck(false, "The deck needs exactly one identity")

        val id = ids.head
        val sideName = f.name

        if (id.side != sideName)
            return DeckCheck(false, "Identity " + id.title + " is a " + id.side + " identity")

        val wrongSide = cards.%(_.side != sideName)
        if (wrongSide.any)
            return DeckCheck(false, "Wrong side: " + wrongSide./(_.title).distinct.join(", "))

        val rest = cards.%!(_.isIdentity)
        val n = rest.num
        val min = id.minDeckSize.|(40)

        if (n < min)
            return DeckCheck(false, "Deck has " + n + " cards, minimum is " + min)

        val over = rest.groupBy(_.title).toList.%((t, l) => l.num > l.head.deckLimit)
        if (over.any)
            return DeckCheck(false, "Too many copies: " + over./((t, l) => t).join(", "))

        val influence = rest.%(c => c.faction != id.faction)./(_.influence.|(0)).sum
        val limit = id.influenceLimit.|(15)
        if (influence > limit)
            return DeckCheck(false, "Influence " + influence + " exceeds " + limit)

        if (f.isCorp) {
            val points = rest./(_.agendaPoints.|(0)).sum
            val minPoints = 2 * (n / 5) + 2
            val maxPoints = minPoints + 1
            if (points < minPoints || points > maxPoints)
                return DeckCheck(false, "Agenda points " + points + " (need " + minPoints + "-" + maxPoints + " for " + n + " cards)")
        }

        DeckCheck(true, n + " cards, " + influence + "/" + limit + " influence")
    }

    def normalize(s : String) : String = {
        s.toLowerCase.toList
            .map {
                case 'ʼ' | '’' | '‘' | '`' => '\''
                case '“' | '”' => '"'
                case 'ā' | 'â' | 'á' | 'à' | 'ä' | 'ã' => 'a'
                case 'ē' | 'ê' | 'é' | 'è' | 'ë' => 'e'
                case 'ī' | 'î' | 'í' | 'ì' | 'ï' => 'i'
                case 'ō' | 'ô' | 'ó' | 'ò' | 'ö' | 'õ' => 'o'
                case 'ū' | 'û' | 'ú' | 'ù' | 'ü' => 'u'
                case 'ç' => 'c'
                case 'ñ' => 'n'
                case c => c
            }
            .mkString
            .replace("&amp;", "&")
            .split("\\s+").$.but("").join(" ")
            .trim
    }

    lazy val byNormalizedTitle : Map[String, CardData] = Cards.all.groupBy(c => normalize(c.title)).toList./((t, l) => t -> l.maxBy(_.code)).toMap

    def find(title : String) : |[CardData] = Cards.byTitle.get(title).||(byNormalizedTitle.get(normalize(title)))

    private val headerWords = $("agenda", "asset", "upgrade", "operation", "barrier", "code gate", "sentry", "ice", "event", "hardware", "program", "resource", "icebreaker", "identity", "multi", "mythic", "trap", "other", "console", "virus")

    // Parse a NetrunnerDB-style decklist into card codes. Returns (codes, errors).
    def parse(text : String) : ($[String], $[String]) = {
        var codes : $[String] = $
        var errors : $[String] = $

        text.split("\n").$./(_.trim).foreach { raw =>
            var line = raw.replace("●", "").replace("•", "").replace("○", "").replace("◆", "").replace("\t", " ").trim
            line = line.split("\\s+").$.join(" ").trim

            if (line.any && !line.startsWith("#") && !line.startsWith("//")) {
                val lower = line.toLowerCase
                val isHeader = headerWords.exists(w => lower.startsWith(w) && lower.matches("^[a-z /&-]+\\(\\d+\\)$")) || lower.matches("^\\d+ cards.*") || lower.matches("^\\d+ influence.*") || lower.contains("influence spent") || lower.startsWith("influence") || lower.startsWith("agenda points") || lower.startsWith("cards up to") || lower.startsWith("deck built") || lower.startsWith("total") || lower.startsWith("format") || lower.startsWith("http") || lower.startsWith("---")

                if (!isHeader) {
                    val (n, title) = line match {
                        case s if s.matches("^\\d+x? .*") =>
                            val k = s.takeWhile(_.isDigit)
                            (k.toInt, s.drop(k.length).dropWhile(c => c == 'x' || c == 'X').trim)
                        case s if s.matches(".* x\\d+$") =>
                            val k = s.reverse.takeWhile(_.isDigit).reverse
                            (k.toInt, s.dropRight(k.length + 1).trim.reverse.dropWhile(_ == 'x').reverse.trim)
                        case s => (1, s)
                    }

                    val t = title.replaceAll("\\s*\\(.*\\)\\s*$", "").trim

                    find(t).||(find(title)) match {
                        case Some(c) => codes ++= n.times(c.code)
                        case None =>
                            if (headerWords.exists(w => lower.startsWith(w)) && lower.contains("("))
                                {}
                            else
                                errors :+= "Unknown card: " + title
                    }
                }
            }
        }

        (codes, errors)
    }
}
