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

// Preset decks packed with the rotated / FFG-era Runner events (cards-rot-events-*.scala): every eternal-legal event
// of the two files appears in at least one deck; used by the bot-vs-bot stress test and offered as presets
object RotEventDecks {
    def x3(l : String*) : $[(String, Int)] = l.$./(_ -> 3)

    val anarchSupport : $[(String, Int)] = $(
        "30006" -> 2, "30005" -> 2, "30032" -> 2, "30008" -> 2, "30007" -> 1, "30031" -> 1, "30033" -> 1,       // rig
        "30030" -> 3, "30002" -> 2, "30028" -> 2,                                                               // economy / runs
    )

    val criminalSupport : $[(String, Int)] = $(
        "30015" -> 2, "30016" -> 2, "30005" -> 2, "30032" -> 1, "30013" -> 1, "30031" -> 1, "30033" -> 2,       // rig
        "30030" -> 3, "30012" -> 1, "30028" -> 2,                                                               // economy / runs
    )

    val shaperSupport : $[(String, Int)] = $(
        "30026" -> 2, "30025" -> 2, "30016" -> 2, "30022" -> 1, "30031" -> 1, "30033" -> 2, "30027" -> 1,       // rig
        "30030" -> 3, "30028" -> 2, "30020" -> 1,                                                               // economy / runs
    )

    // mini-faction identities (25 influence): neutral rig plus two off-faction breakers
    val neutralSupport : $[(String, Int)] = $(
        "30032" -> 3, "30031" -> 2, "30033" -> 2, "30034" -> 1, "30005" -> 2, "30016" -> 2,                     // rig
        "30030" -> 3, "30028" -> 3, "30029" -> 2,                                                               // economy / runs
    )

    val anarch1 = anarchSupport ++ x3("01002", "02021", "02081", "04001", "06013", "06034", "06073", "07031", "07032", "07033", "07034", "07035", "07036", "07037") :+ ("10083" -> 1)
    val anarch2 = anarchSupport ++ x3("07038", "07039", "08021", "08041", "08101", "10039", "10058", "10077", "10096", "11001", "11021", "11022", "11062", "12001")
    val anarch3 = anarchSupport ++ x3("12081", "12101", "20002", "20004", "21001", "21061", "21083", "22002", "22003", "25003", "25004", "25006", "25007", "28001") :+ ("12023" -> 2)

    val criminal1 = criminalSupport ++ x3("01018", "02023", "04024", "04044", "04063", "04064", "04084", "05031", "05032", "05033", "05034", "05036")
    val criminal2 = criminalSupport ++ x3("05047", "06015", "06035", "06036", "06118", "08064", "08105", "10004", "10022", "10060", "10079", "11082")
    val criminal3 = criminalSupport ++ x3("12004", "12005", "12024", "12083", "12084", "13002", "13004", "21023")
    val criminal4 = criminalSupport ++ x3("21044", "21064", "21084", "21105", "22009", "25023", "25025", "25029", "25030")

    val shaper1 = shaperSupport ++ x3("02087", "03031", "03032", "03033", "03034", "03035", "04027", "04107", "06018", "06038", "06078")
    val shaper2 = shaperSupport ++ x3("06115", "08026", "10080", "10100", "11028", "11083", "11107", "12026", "12086", "12106", "12107")
    val shaper3 = shaperSupport ++ x3("13013", "13014", "21046", "21066", "21088", "22016", "25043", "25044", "25047", "29005")

    val adam = neutralSupport ++ x3("09038", "21028", "02090", "04089", "05051", "07053", "08008", "09054", "10026", "09053", "10025", "10102") :+ ("08062" -> 2)
    val apex = neutralSupport ++ x3("09030", "09031", "22023", "10045", "11009", "11069", "11087", "12008", "20055")
    val sunny = neutralSupport ++ x3("11007", "21048", "21110", "22024", "11008", "11109", "12028", "13023", "29007") ++ $("02090" -> 1, "04089" -> 1, "10045" -> 1)

    val presets : $[Deck] = $(
        Deck("Anarch Events I (Rotated)", Runner, "30001", anarch1),
        Deck("Anarch Events II (Rotated)", Runner, "30001", anarch2),
        Deck("Anarch Events III (Rotated)", Runner, "30001", anarch3),
        Deck("Criminal Events I (Rotated)", Runner, "30010", criminal1),
        Deck("Criminal Events II (Rotated)", Runner, "30010", criminal2),
        Deck("Criminal Events III (Rotated)", Runner, "30010", criminal3),
        Deck("Criminal Events IV (Rotated)", Runner, "30010", criminal4),
        Deck("Shaper Events I (Rotated)", Runner, "30019", shaper1),
        Deck("Shaper Events II (Rotated)", Runner, "30019", shaper2),
        Deck("Shaper Events III (Rotated)", Runner, "30019", shaper3),
        Deck("Adam Events (Rotated)", Runner, "09037", adam),
        Deck("Apex Events (Rotated)", Runner, "09029", apex),
        Deck("Sunny Lebeau Events (Rotated)", Runner, "09045", sunny),
    )
}
