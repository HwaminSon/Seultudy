import utils.CsvUtil
import utils.GeoToolsUtil.convertCsvToShp
import utils.GeoToolsUtil.createShapeFileWithResults
import utils.GeoToolsUtil.showMapWithCsvFile
import utils.Neo4jUtil

fun main(args: Array<String>) {
    val nodeList = Neo4jUtil().use { app ->
        app.runBetweennessCentrality()
    }
//    createShapeFileWithResults(resultList)
//    nodeList.forEach {
//        CsvUtil.writeCsv(it, )
//    }


//    convertCsvToShp()
//    loadShapeFileAndDisplay()
//    showMapWithCsvFile()
//    convertCsvToShp()
}

