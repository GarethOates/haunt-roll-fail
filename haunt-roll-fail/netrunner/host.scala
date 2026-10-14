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

// JVM-only headless harness (excluded from the Scala.js build by file name): bot vs bot games with log dumps
object Host extends hrf.host.BaseHost {
    val gaming = netrunner.gaming
    val path = sys.env.getOrElse("HRF_HOST_OUT", ".")

    type W = Faction

    var games = 0

    def botFor(f : F) : Bot = ((games + f.isCorp.??(1)) % 2 == 0).?(new BasicBot(f)).|(new RandomBot(f))

    // HRF_DECK_MIN_INDEX=n restricts the bots to the preset decks with index >= n (stress testing a card set)
    val deckMinIndex = sys.env.get("HRF_DECK_MIN_INDEX")./(_.toInt).|(0)

    def askBot(g : G, f : F, actions : $[UserAction]) = {
        val filtered = actions.%!(a => a @@ { case ChoosePresetDeckAction(_, i) => i < deckMinIndex ; case _ => false })
        botFor(f).ask(filtered.any.?(filtered).|(actions), 0)(g)
    }

    def factions = $[Faction](Corp, Runner)
    def subjects = factions

    def batch = $(() => { games += 1 ; new G($(Corp, Runner), $) })

    def factionName(f : F) = f.name
    def nameWinner(f : F) = f.name

    def winnersFromFaction(f : F)(implicit g : G) = $(f)

    def serializer = netrunner.Serialize
    def start = StartAction(version)
    def times = sys.env.get("HRF_HOST_TIMES")./(_.toInt).|(20)

    def winners(a : Action)(implicit g : G) : $[W] = a @@ {
        case GameOverAction(w, _) => w.$
        case _ => Nil
    }

    override def main(args : Array[String]) {
        val n = args.lift(0)./(_.toInt).|(times)
        val keepLogs = args.lift(1).has("logs")
        var wins = Map[String, Int]()
        var errors = 0
        var lengths : $[Int] = $

        1.to(n).foreach { i =>
            games += 1
            val game = new G($(Corp, Runner), $)
            var log : $[String] = $
            var continue : Continue = StartContinue
            var a : Action = start
            var k = 0
            var result = "unfinished"
            implicit val sink : String => Unit = (s : String) => { log :+= s }

            try {
                while (game.isOver.not && a.is[HostGameOverAction].not && k < limit) {
                    k += 1
                    val r = game.performContinue(|(continue), a, false)
                    r.effects.foreach {
                        case PrintLog(e) => log :+= e.text
                        case PrintTempLog(e) => log :+= e.text
                        case PrintError(e) => log :+= "ERROR " + e.text
                        case _ =>
                    }
                    continue = unwrapLogs(r.continue)
                    continue @@ {
                        case ErrorContinue(e, m) => throw e
                        case Ask(f, Nil) => throw new Error("empty ask for " + f + " after " + a)
                        case _ =>
                    }
                    a = askFaction(game, continue)
                }
                if (k >= limit)
                    throw new Error("game too long")
                result = game.winner./(_.name).|("nobody") + " won in " + k + " actions (turn " + game.turn + ")"
                wins += game.winner./(_.name).|("nobody") -> (wins.get(game.winner./(_.name).|("nobody")).|(0) + 1)
                lengths :+= k
                println(result)
                if (keepLogs)
                    writeFile(path + "/game-" + i + ".log", log.mkString("\n") + "\n\n" + result)
            }
            catch {
                case e : Throwable =>
                    errors += 1
                    println("ERROR in game " + i + ": " + e)
                    writeFile(path + "/game-error-" + i + ".txt", log.mkString("\n") + "\n\nLAST ACTION: " + a + "\n\nCONTINUE: " + continue + "\n\nPENDING: " + game.pending + "\n\n" + e + "\n" + e.getStackTrace.take(40).mkString("\n"))
            }
        }

        println()
        println("games: " + n + "  errors: " + errors + "  wins: " + wins + "  avg actions: " + (lengths.sum / lengths.num.atLeast(1)))
    }

    def unwrapLogs(c : Continue)(implicit sink : String => Unit) : Continue = c match {
        case Log(e, k, cc) =>
            sink(e.text)
            unwrapLogs(cc)
        case DelayedContinue(_, cc) => unwrapLogs(cc)
        case c => c
    }

    def writeFile(name : String, text : String) {
        import java.nio.file.{Paths, Files}
        import java.nio.charset.StandardCharsets
        Files.write(Paths.get(name), text.getBytes(StandardCharsets.UTF_8))
    }
}
