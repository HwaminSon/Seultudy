import utils.Neo4jUtil
import utils.Neo4jUtil.Centrality

fun main(args: Array<String>) {

    Neo4jUtil().use { app ->
//        val lineList = GeoToolsUtil.readLineShapeFile("./data/seocho-gu/edges.shp")
//        println("lineList = ${lineList.size}")
//        app.createRoadNetwork(lineList)
        app.runCentrality(Neo4jUtil.Database.ROAD_NETWORK, Centrality.EIGENVECTOR)
    }
}
