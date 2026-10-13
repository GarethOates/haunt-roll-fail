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

import hrf.serialize._

object Serialize extends Serializer {
    val gaming = netrunner.gaming

    def writeFaction(f : F) = Meta.writeFaction(f)
    def parseFaction(s : String) = Meta.parseFaction(s)

    val prefix = "netrunner."
}
