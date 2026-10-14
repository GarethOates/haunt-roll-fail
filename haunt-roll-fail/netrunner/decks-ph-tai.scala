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


// Preset decks built around Parhelion (ph) and The Automata Initiative (tai), filled up with System Gateway cards
object PHTAIDecks {
    val presets : $[Deck] = $(
        Deck("HB Thule Subsea", Corp, "33095", $(
            "33096" -> 3, // Ontological Dependence
            "34031" -> 2, // Salvo Testing
            "34032" -> 2, // Stegodon MK IV
            "30036" -> 1, // Luminal Transubstantiation
            "30067" -> 2, // Offworld Office
            "33097" -> 2, // Nightmare Archive
            "34033" -> 2, // Wage Workers
            "33102" -> 1, // Djupstad Grid
            "33103" -> 2, // Mr. Hendrik
            "34038" -> 2, // Vovo Ozetti
            "34065" -> 2, // B-1001
            "30037" -> 2, // Nico Campaign
            "30075" -> 3, // Hedge Fund
            "33100" -> 2, // Distributed Tracing
            "33101" -> 2, // Hypoxia
            "34037" -> 2, // Greasing the Palm
            "30041" -> 1, // Sprint
            "33098" -> 2, // Bloop
            "33099" -> 3, // Pulse
            "34034" -> 2, // Ablative Barrier
            "34035" -> 2, // Jaguarundi
            "34036" -> 2, // M.I.C.
            "30038" -> 1, // Ansel 1.0
        )),
        Deck("Jinteki Issuaq Adaptics", Corp, "33104", $(
            "33105" -> 3, // Hybrid Release
            "34040" -> 2, // Fujii Asset Retrieval
            "30044" -> 1, // Longevity Serum
            "30067" -> 3, // Offworld Office
            "30070" -> 3, // Superconducting Hub
            "33106" -> 2, // Dr. Vientiane Keeling
            "33107" -> 2, // Reaper Function
            "33111" -> 1, // Nanisivik Grid
            "34041" -> 2, // Front Company
            "34046" -> 2, // Adrian Seis
            "34047" -> 2, // Daniela Jorge Inacio
            "30045" -> 1, // Urtica Cipher
            "30075" -> 3, // Hedge Fund
            "33110" -> 2, // Simulation Reset
            "34045" -> 2, // Mindscaping
            "30048" -> 2, // Hansei Review
            "33108" -> 3, // Hafrun
            "33109" -> 2, // Vampyronassa
            "34042" -> 2, // Attini
            "34043" -> 2, // Phoneutria
            "34044" -> 3, // Tatu-Bola
        )),
        Deck("Jinteki A Teia", Corp, "34039", $(
            "33105" -> 3, // Hybrid Release
            "34040" -> 2, // Fujii Asset Retrieval
            "30044" -> 1, // Longevity Serum
            "30067" -> 3, // Offworld Office
            "30070" -> 3, // Superconducting Hub
            "33106" -> 2, // Dr. Vientiane Keeling
            "33107" -> 2, // Reaper Function
            "33111" -> 1, // Nanisivik Grid
            "34041" -> 2, // Front Company
            "34046" -> 2, // Adrian Seis
            "34047" -> 2, // Daniela Jorge Inacio
            "30045" -> 1, // Urtica Cipher
            "30075" -> 3, // Hedge Fund
            "33110" -> 2, // Simulation Reset
            "34045" -> 2, // Mindscaping
            "30048" -> 2, // Hansei Review
            "33108" -> 3, // Hafrun
            "33109" -> 2, // Vampyronassa
            "34042" -> 2, // Attini
            "34043" -> 2, // Phoneutria
            "34044" -> 3, // Tatu-Bola
        )),
        Deck("NBN Epiphany Analytica", Corp, "34048", $(
            "33112" -> 3, // Freedom of Information
            "33113" -> 3, // Post-Truth Dividend
            "34049" -> 3, // Oracle Thinktank
            "30052" -> 1, // Tomorrow's Headline
            "30067" -> 3, // Offworld Office
            "33114" -> 2, // Gaslight
            "33115" -> 2, // Vera Ivanovna Shuyskaya
            "34050" -> 2, // Balanced Coverage
            "34051" -> 2, // Behold!
            "34052" -> 2, // Federal Fundraising
            "30075" -> 3, // Hedge Fund
            "33118" -> 2, // Nonequivalent Exchange
            "33119" -> 1, // Shipment from Vladisibirsk
            "34055" -> 2, // Oppo Research
            "34056" -> 2, // Your Digital Life
            "33116" -> 3, // Klevetnik
            "33117" -> 2, // Unsmiling Tsarevna
            "34053" -> 2, // Starlit Knight
            "34054" -> 3, // Virtual Service Agent
            "30054" -> 2, // Funhouse
        )),
        Deck("Weyland Built to Last", Corp, "30059", $(
            "33120" -> 2, // Regulatory Capture
            "33121" -> 2, // Kimberlite Field
            "34057" -> 3, // Slash and Burn Agriculture
            "30060" -> 1, // Above the Law
            "30067" -> 2, // Offworld Office
            "33122" -> 2, // Hostile Architecture
            "33123" -> 1, // Superdeep Borehole
            "33126" -> 2, // Yakov Erikovich Avdakov
            "33127" -> 2, // ZATO City Grid
            "34058" -> 2, // Cybersand Harvester
            "34063" -> 2, // Angelique Garza Correa
            "34064" -> 2, // Tucana
            "30075" -> 3, // Hedge Fund
            "33125" -> 2, // End of the Line
            "34061" -> 3, // Armed Asset Protection
            "34062" -> 2, // Pivot
            "33124" -> 3, // Anvil
            "34059" -> 3, // Tree Line
            "34060" -> 3, // Valentao
            "30072" -> 3, // Palisade
        )),
        Deck("Criminal Mercury", Runner, "34010", $(
            "33075" -> 2, // Concerto
            "33076" -> 2, // Reprise
            "33077" -> 2, // Poison Vial
            "33078" -> 2, // WAKE Implant v2A-JRJ
            "33079" -> 2, // Zenit Chip JZ-2MJ
            "33080" -> 2, // Tremolo
            "33081" -> 2, // Tunnel Vision
            "33082" -> 2, // Asmund Pudlat
            "33083" -> 2, // Info Bounty
            "34011" -> 2, // Chrysopoeian Skimming
            "34012" -> 2, // S-Dobrado
            "34013" -> 2, // Capybara
            "34014" -> 2, // Hermes
            "34015" -> 2, // Curupira
            "34016" -> 2, // Laser Pointer
            "34017" -> 2, // Saci
            "34018" -> 2, // Shibboleth
            "34019" -> 2, // Debbie Downtown Moreira
            "34030" -> 3, // Bahia Bands
            "30030" -> 3, // Sure Gamble
            "30015" -> 1, // Carmen
            "30028" -> 2, // Jailbreak
        )),
        Deck("Shaper Arissana Rocha Nahu", Runner, "34020", $(
            "33084" -> 2, // Spark of Inspiration
            "33085" -> 2, // Hippocampic Mechanocytes
            "33086" -> 2, // Basilar Synthgland 2KVJ
            "33087" -> 2, // Flux Capacitor
            "33088" -> 2, // Nanuq
            "33089" -> 2, // Orca
            "33090" -> 2, // K2CP Turbine
            "33091" -> 2, // World Tree
            "33092" -> 2, // Dr. Nuka Vrolyck
            "34021" -> 2, // Joy Ride
            "34022" -> 2, // AirbladeX (JSRF Ed.)
            "34023" -> 2, // LilyPAD
            "34024" -> 2, // Living Mural
            "34025" -> 2, // Pichacao
            "34026" -> 2, // Slap Vandal
            "34027" -> 2, // Umbrella
            "34028" -> 2, // Beatriz Friere Gonzalez
            "34029" -> 2, // Urban Art Vernissage
            "34030" -> 2, // Bahia Bands
            "30030" -> 3, // Sure Gamble
            "33094" -> 3, // Matryoshka
            "30020" -> 1, // Creative Commission
        )),
        Deck("Anarch Loup", Runner, "30001", $(
            "33066" -> 2, // Finality
            "33067" -> 2, // Katorga Breakout
            "33068" -> 2, // Raindrops Cut Stone
            "33069" -> 2, // Time Bomb
            "33070" -> 2, // Abaasy
            "33071" -> 2, // Hush
            "33072" -> 2, // Nga
            "33073" -> 2, // Num
            "33074" -> 2, // Tsakhia Bankhar Gantulga
            "34001" -> 2, // Strike Fund
            "34002" -> 2, // The Price
            "34003" -> 2, // Solidarity Badge
            "34004" -> 2, // Audrey v2
            "34005" -> 2, // Banner
            "34006" -> 2, // Monkeywrench
            "34007" -> 2, // Eru Ayase-Pessoa
            "34008" -> 2, // Hannah Wheels Pilintra
            "34009" -> 2, // Lago Paranoa Shelter
            "30030" -> 3, // Sure Gamble
            "34030" -> 2, // Bahia Bands
            "30006" -> 2, // Cleaver
            "30008" -> 2, // Leech
        )),
    )
}
