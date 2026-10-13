### HRF Boardgames App

#### Building
In **scala-js-dom-reduced** dir
```
sbt publishLocal
```

In **haunt-roll-fail** dir (a `.jvmopts` file raises the JVM heap; the Scala.js linker needs ~8 GB)
```
sbt fastOptJS
```

In **good-game** dir
```
sbt "run create ../good-game-database ../haunt-roll-fail http://localhost:7070 http://localhost:7070/hrf/ 7070"
sbt "run run ../good-game-database ../haunt-roll-fail http://localhost:7070 http://localhost:7070/hrf/ 7070"
```

#### Netrunner
The `haunt-roll-fail/netrunner` plugin needs its card images generated once (they are not in git):
```
python3 -I haunt-roll-fail/netrunner/tools/gen-cards.py --check
python3 -I haunt-roll-fail/netrunner/tools/fetch-assets.py
```
See `haunt-roll-fail/netrunner/STATUS.md` for coverage and `tools/README.md` for details. Play at `/play/netrunner`.
