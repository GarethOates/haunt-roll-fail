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

// Preset Runner decks packed with the rotated / FFG-era programs (cards-rot-programs*.scala): every program legal in
// eternal appears in at least one deck (the banned Deva programs and the draft-only Surveillance Network Keys / Sneakdoor
// Primes are exercised by the JVM harness with an imported deck). System Gateway identities, legal in eternal.
object RotProgramDecks {
    val support : $[(String, Int)] = $("30030" -> 3, "30031" -> 2, "26094" -> 2, "31037" -> 2, "30028" -> 1)   // Sure Gamble, T400, Daily Casts, Dirty Laundry, Jailbreak

    val anarch1 : $[(String, Int)] = $(
        "01009" -> 2, "01013" -> 2, "01014" -> 2, "02041" -> 2, "02061" -> 2, "04002" -> 2,   // Djinn, Wyrm, Yog.0, Nerve Agent, Disrupter, Pawn
        "04003" -> 2, "04021" -> 2, "04022" -> 2, "04043" -> 2, "04061" -> 2, "06033" -> 2,   // Rook, Bishop, Scheherazade, Knight, Keyhole, D4v1d
        "06053" -> 2, "06074" -> 2, "06094" -> 2, "06113" -> 2, "09033" -> 2, "09034" -> 2,   // BlacKat, Origami, Cerberus "Cuj.0" H3, Incubator, Endless Hunger, Harbinger
    ) ++ support

    val anarch2 : $[(String, Int)] = $(
        "06114" -> 2, "07040" -> 2, "07041" -> 2, "07042" -> 2, "07043" -> 2, "08061" -> 2,   // Ixodidae, Eater, Gravedigger, Hivemind, Progenitor, Faust
        "08081" -> 2, "08102" -> 2, "10021" -> 2, "11023" -> 2, "11024" -> 2, "11042" -> 2,   // Trope, Surfer, Diwan, Nfr, Paperclip, Black Orchestra
        "11081" -> 2, "11102" -> 2, "12041" -> 2, "12042" -> 2, "11086" -> 1, "21068" -> 1,   // MKUltra, Sunya, Berserker, Persephone, Reaver, Consume
    ) ++ support

    val anarch3 : $[(String, Int)] = $(
        "12082" -> 2, "20008" -> 2, "20012" -> 2, "20014" -> 2, "21002" -> 2, "21022" -> 2,   // God of War, Darwin, Hemorrhage, Morning Star, Yusuf, Plague
        "21041" -> 2, "21082" -> 2, "21102" -> 2, "22006" -> 2, "25011" -> 2, "25012" -> 2,   // eXer, Trypano, Musaazi, Cradle, Datasucker, Force of Nature
        "25014" -> 2, "29001" -> 2, "29002" -> 2, "25061" -> 2, "05053" -> 2,   // Lamprey, Medium, Parasite, Crypsis, Overmind
    ) ++ support

    val criminal1 : $[(String, Int)] = $(
        "01027" -> 2, "02045" -> 2, "02065" -> 2, "04005" -> 2, "04025" -> 2, "04026" -> 2,   // Ninja, Snitch, Crescentus, Gorman Drip v1, Copycat, Leviathan
        "04045" -> 2, "04065" -> 2, "04105" -> 2, "05041" -> 2, "05042" -> 2, "05043" -> 2,   // Expert Schedule Analyzer, Garrote, Savoir-faire, Alias, Breach, Bug
        "05044" -> 2, "05045" -> 2, "09048" -> 2, "09049" -> 2, "09050" -> 1,   // Gingerbread, Grappling Hook, GS Striker M1, GS Shrike M2, GS Sherman M3
    ) ++ support

    val criminal2 : $[(String, Int)] = $(
        "05046" -> 2, "06077" -> 2, "06096" -> 2, "06119" -> 2, "08004" -> 2, "08046" -> 2,   // Passport, Switchblade, Cerberus "Rex" H2, Au Revoir, Spike, Crowbar
        "08066" -> 2, "10005" -> 2, "11025" -> 2, "11044" -> 2, "11064" -> 2, "11104" -> 2,   // Shiv, Mongoose, Golden, Peregrine, Saker, Tapwrm
        "11105" -> 2, "12085" -> 2, "09040" -> 2, "22022" -> 1, "21029" -> 1,   // Tracker, Flashbang, Multithreader, Algernon, RNG Key
    ) ++ support

    val criminal3 : $[(String, Int)] = $(
        "12104" -> 2, "13007" -> 2, "13009" -> 2, "20027" -> 2, "20030" -> 2, "20031" -> 2,   // Aumakua, Lustig, Mammon, Aurora, Peacock, Pheromones
        "21004" -> 2, "21024" -> 2, "21104" -> 2, "22011" -> 2, "22012" -> 2, "25034" -> 2,   // Puffer, Wari, Amina, Bankroll, Tycoon, Demara
        "25035" -> 2, "29004" -> 2, "05053" -> 1, "25061" -> 1,   // Faerie, Cache, Overmind, Crypsis
    ) ++ support

    val shaper1 : $[(String, Int)] = $(
        "01045" -> 2, "02007" -> 2, "02027" -> 2, "02089" -> 2, "02108" -> 2, "03041" -> 2,   // Net Shield, ZU.13 Key Master, Snowball, Creeper, Deep Thought, Cloak
        "03042" -> 2, "03043" -> 2, "03044" -> 2, "03047" -> 2, "03048" -> 2, "04007" -> 2,   // Dagger, Chakana, Cyber-Cypher, Sahasrara, Inti, False Echo
        "04047" -> 2, "04067" -> 2, "11088" -> 2,   // Torch, Sharpshooter, Baba Yaga
    ) ++ support

    val shaper2 : $[(String, Int)] = $(
        "04087" -> 2, "04088" -> 2, "04108" -> 2, "06019" -> 2, "06039" -> 2, "06057" -> 2,   // Alpha, Omega, Paintbrush, Leprechaun, LLDS Energy Regulator, Refractor
        "06116" -> 2, "06117" -> 2, "08028" -> 2, "08048" -> 2, "08070" -> 2, "08107" -> 2,   // Collective Consciousness, Sage, Study Guide, Analog Dreamers, Hyperdriver, DaVinci
        "10008" -> 2, "10062" -> 2, "21029" -> 1,   // Panchatantra, Brahman, RNG Key
    ) ++ support ++ $("30028" -> 1)

    val shaper3 : $[(String, Int)] = $(
        "10101" -> 2, "11006" -> 2, "11045" -> 2, "11068" -> 2, "11084" -> 2, "11085" -> 2,   // Ankusa, Dai V, Houdini, Blackstone, Equivocation, Misdirection
        "11108" -> 2, "12027" -> 2, "12048" -> 2, "12067" -> 2, "12087" -> 2, "12088" -> 2,   // Fawkes, Customized Secretary, Inversificator, Mass-Driver, Maven, Na'Not'K
        "13017" -> 2, "13018" -> 2, "05053" -> 1,   // Adept, Savant, Overmind
    ) ++ support ++ $("30028" -> 1)

    val shaper4 : $[(String, Int)] = $(
        "13020" -> 2, "20050" -> 2, "21007" -> 2, "21026" -> 2, "21067" -> 2, "21086" -> 2,   // Dhegdheer, Magnum Opus, Upya, Takobi, Nyashia, Laamb
        "21108" -> 2, "22019" -> 2, "22020" -> 2, "25052" -> 2, "25053" -> 2, "25055" -> 2,   // Engolo, Ika, Kyuban, Battering Ram, Deus X, Pipeline
        "29006" -> 2, "25061" -> 1,   // Cerberus "Lady" H1, Crypsis
    ) ++ support ++ $("30028" -> 2, "31037" -> 1)

    val presets : $[Deck] = $(
        Deck("Anarch programs 1 (rot)", Runner, "30001", anarch1),
        Deck("Anarch programs 2 (rot)", Runner, "30001", anarch2),
        Deck("Anarch programs 3 (rot)", Runner, "30001", anarch3),
        Deck("Criminal programs 1 (rot)", Runner, "30010", criminal1),
        Deck("Criminal programs 2 (rot)", Runner, "30010", criminal2),
        Deck("Criminal programs 3 (rot)", Runner, "30010", criminal3),
        Deck("Shaper programs 1 (rot)", Runner, "30019", shaper1),
        Deck("Shaper programs 2 (rot)", Runner, "30019", shaper2),
        Deck("Shaper programs 3 (rot)", Runner, "30019", shaper3),
        Deck("Shaper programs 4 (rot)", Runner, "30019", shaper4),
    )
}
