import utils.GeoToolsUtil.convertCsvToShp
import utils.GeoToolsUtil.createShapeFileWithResults
import utils.GeoToolsUtil.showMapWithCsvFile
import utils.Neo4jUtil

fun main(args: Array<String>) {
    val resultList = Neo4jUtil().use { app ->
        app.runBetweennessCentrality()
    }
    createShapeFileWithResults(resultList)


//    convertCsvToShp()
//    loadShapeFileAndDisplay()
//    showMapWithCsvFile()
//    convertCsvToShp()
}

