import utils.CsvUtil
import utils.GeoToolsUtil
import utils.GeoToolsUtil.convertCsvToShp
import utils.GeoToolsUtil.createShapeFileWithResults
import utils.GeoToolsUtil.showMapWithCsvFile
import utils.Neo4jUtil
import utils.Neo4jUtil.Centrality

fun main(args: Array<String>) {
    val result = Neo4jUtil().use { app ->
        app.runCentrality(Centrality.eigenvector)
    }
    GeoToolsUtil.createPointShapeFile(result)


//    createShapeFileWithResults(resultList)
//    nodeList.forEach {
//        CsvUtil.writeCsv(it, )
//    }


//    convertCsvToShp()
//    loadShapeFileAndDisplay()
//    showMapWithCsvFile()
//    convertCsvToShp()
}

