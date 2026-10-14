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

// Preset decks packed with the rotated / FFG-era Resource cards (every card of cards-rot-resources*.scala appears in at
// least one deck; used by the bot-vs-bot stress test). All decks are legal in eternal except "Catalyst TD (rot-res)",
// which holds the Terminal Directive campaign / draft cards (Investigator Inez Delgado, Shadow Team, The Masque) and
// Salvaged Vanadis Armory.
object RotResourceDecks {
    val neutrals : $[String] = $("01052", "02050", "02068", "02109", "03054", "03055", "04049", "04069", "05054", "05055", "06020", "06040",
        "06059", "06060", "06080", "06100", "07055", "08009", "08031", "08087", "08108", "09055", "10010", "10046", "10047", "10065",
        "10084", "10085", "11048", "11070", "12049", "12089", "12109", "13024", "13025", "13026", "13027", "20060", "21090", "21111",
        "22025", "25062", "25064", "25065")

    val anarch : $[String] = $("01016", "02042", "02103", "04023", "04062", "04083", "06054", "06075", "07048", "07049", "07050", "07051",
        "07052", "08002", "08003", "08022", "08044", "08062", "08082", "08103", "10003", "10059", "10078", "10098", "12003", "12022",
        "12023", "12062", "21043", "22007")

    val criminal : $[String] = $("01032", "02025", "04046", "05049", "05050", "06016", "06056", "06097", "08005", "08067", "08068", "08083",
        "08084", "10023", "10043", "10099", "11004", "11026", "11065", "11106", "12044", "12045", "12064", "12065", "12105", "13010",
        "13011", "20034", "20035", "20036", "21005", "21045", "21065", "21085", "21106", "22013", "22014", "25038", "25039", "28002")

    val shaper : $[String] = $("02008", "02049", "03050", "04008", "04048", "06058", "08006", "08007", "08029", "08030", "08085", "08086",
        "10009", "10063", "10081", "10082", "11029", "11030", "11046", "12006", "12007", "12108", "13021", "13022", "20053", "20054",
        "21027", "21047", "21107", "22021", "25057")

    // two copies of each resource (fewer if the deck limit is lower), the shell, then fillers up to the minimum deck size
    def build(name : String, id : String, res : $[String], shell : $[(String, Int)]) : Deck = {
        val base = res./(c => c -> Cards(c).deckLimit.atMost(2)) ++ shell
        val min = Cards(id).minDeckSize.|(45)
        var fill : $[(String, Int)] = $
        $("31037", "30028", "30029", "30031", "31038").foreach { c =>
            val missing = min - (base ++ fill)./((c, n) => n).sum
            if (missing > 0)
                fill :+= c -> missing.atMost(3)
        }
        Deck(name, Runner, id, base ++ fill)
    }

    val anarchShell : $[(String, Int)] = $("30030" -> 3, "31006" -> 2, "31008" -> 1, "30032" -> 1, "30008" -> 1)       // Sure Gamble, Corroder, Mimic, Mayfly, Leech
    val criminalShell : $[(String, Int)] = $("30030" -> 3, "31021" -> 1, "30015" -> 1, "30016" -> 1, "30032" -> 1)     // Sure Gamble, Abagnale, Carmen, Marjanah, Mayfly
    val shaperShell : $[(String, Int)] = $("30030" -> 3, "30026" -> 1, "30025" -> 1, "31033" -> 1, "30032" -> 1, "31034" -> 1, "30031" -> 1)  // + Unity, Echelon, Gordian Blade, Paricia, T400
    val miniShell : $[(String, Int)] = $("30030" -> 3, "30032" -> 3, "31006" -> 2)                                     // Sure Gamble, Mayfly, Corroder

    val presets : $[Deck] = $(
        build("Anarch Reina (rot-res)", "31002", anarch.take(15) ++ neutrals.slice(34, 36), anarchShell),
        build("Anarch Quetzal (rot-res)", "31001", anarch.drop(15) ++ neutrals.slice(36, 38), anarchShell),
        build("Criminal Steve (rot-res)", "31014", criminal.take(14) ++ neutrals.slice(38, 39), criminalShell),
        build("Criminal Ken (rot-res)", "31013", criminal.slice(14, 27) ++ neutrals.slice(39, 40), criminalShell),
        build("Criminal Sable (rot-res)", "33011", criminal.drop(27) ++ neutrals.slice(40, 41), criminalShell),
        build("Shaper Kit (rot-res)", "31026", shaper.take(16) ++ neutrals.slice(41, 43), shaperShell),
        build("Shaper Padma (rot-res)", "33021", shaper.drop(16) ++ neutrals.slice(43, 44), shaperShell),
        build("Apex (rot-res)", "09029", $("09035", "09036", "21008") ++ neutrals.take(12), miniShell),
        build("Adam (rot-res)", "09037", $("09041", "09042", "09043", "09044", "11047", "21089") ++ neutrals.slice(12, 22), miniShell),
        build("Sunny Lebeau (rot-res)", "09045", $("09051", "09052") ++ neutrals.slice(22, 35), miniShell),
        Deck("Catalyst TD (rot-res)", Runner, "30076", $("14014" -> 1, "14015" -> 1, "14016" -> 1, "14017" -> 1, "14022" -> 2, "14024" -> 2, "14025" -> 2, "12103" -> 1,
            "30030" -> 3, "30032" -> 3, "31006" -> 2, "31033" -> 1, "31037" -> 3, "30028" -> 3, "30029" -> 3, "30031" -> 2)),
    )
}
