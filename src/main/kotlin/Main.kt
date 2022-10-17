import Quickstart.convertCsvToShp
import Quickstart.loadShapeFileAndDisplay
import Quickstart.test
import io.github.cdimascio.dotenv.Dotenv

fun main(args: Array<String>) {
    Neo4jUtil().use { app ->
        app.runBetweennessCentrality()
    }

//    convertCsvToShp()
//    loadShapeFileAndDisplay()
//    test()
}

