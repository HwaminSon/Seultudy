import utils.CsvUtil
import utils.GeoToolsUtil
import utils.GeoToolsUtil.convertCsvToShp
import utils.GeoToolsUtil.createShapeFileWithResults
import utils.GeoToolsUtil.showMapWithCsvFile
import utils.Neo4jUtil
import utils.Neo4jUtil.Centrality

fun main(args: Array<String>) {
//    val result = Neo4jUtil().use { app ->
//        app.runCentrality(Centrality.eigenvector)
//    }
//    GeoToolsUtil.createPointShapeFile(result)

    val lineList = GeoToolsUtil.readLineShapeFile("./data/seocho-gu/edges.shp")

    println("lineList = ${lineList.size}")

    Neo4jUtil().use { app ->
        app.createRoadNetwork(lineList)
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

