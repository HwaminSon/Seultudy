package utils

import model.MyNode
import model.MyRelationship
import org.locationtech.jts.geom.MultiLineString
import org.neo4j.driver.*
import org.neo4j.driver.exceptions.Neo4jException
import java.util.*
import java.util.logging.Level
import java.util.logging.Logger

class Neo4jUtil() : AutoCloseable {
    private val driver: Driver

    enum class Centrality(val value: String) {
        BETWEENNESS("betweenness"),
        EIGENVECTOR("eigenvector"),
    }

    enum class Database(val value: String) {
        DEFAULT("neo4j"),
        ROAD_NETWORK("road-network"),
    }

    private val mySession
        get() = driver.session(SessionConfig.forDatabase("neo4j"))

    private fun createSession(database: Database = Database.DEFAULT): Session {
        return driver.session(SessionConfig.forDatabase(database.value))
    }

    init {
        // The driver is a long living object and should be opened during the start of your application
//        val env = Dotenv.load()
//        val neo4jUri = env["NEO4J_URI"]
//        val neo4jUserName = env["NEO4J_USERNAME"]
//        val neo4jUserPw = env["NEO4J_PASSWORD"]

        val neo4jUri = "neo4j://localhost:7687"
        val neo4jUserName = "neo4j"
        val neo4jUserPw = "qwer1234"

        driver = GraphDatabase.driver(
            neo4jUri,
            AuthTokens.basic(
                neo4jUserName,
                neo4jUserPw
            ),
            Config.defaultConfig()
        )
    }

    @Throws(Exception::class)
    override fun close() {
        // The driver object should be closed before the application ends.
        driver.close()
    }

    fun saveAllNodesAsCsv() {
        val qMatchAll = """
                    MATCH (n) RETURN n
               """.trimIndent()
        try {
            mySession.use { session ->
                val records = session.run(qMatchAll).list().map { record ->
                    val node = record["n"].asNode()

                    MyNode(
                        node = node,
                        id = node.elementId(),
                        type = when {
                            node.labels().first() == "Apartment" -> "아파트"
                            else -> node["type"].asString()
                        },
                        name = node["name"].asString(),
                        point = node["coord"].asPoint(),
                        score = record["score"].asDouble(0.0)
                    )
                }

                listOf(
                    "ConvenienceFacility",
                    "PublicTransport",
                    "Apartment",
                    "EducationalFacility",
                )
                    .forEach { label ->
                        val nodes = records.filter {
                            it.node.labels().contains(label)
                        }
                        CsvUtil.writeNodeToCsv(nodes, label)
                    }
            }
        } catch (ex: Neo4jException) {
            ex.printStackTrace()
            throw ex
        }
    }

    fun saveAllRelationshipsAsCsv() {
        try {
            mySession.use { session ->
                val records = session.run(
                    "MATCH ()-[r]->() RETURN r"
                )
                    .list()
                    .map { record ->
                        val relationship = record["r"].asRelationship()

                        MyRelationship(
                            relationship = relationship,
                            id = relationship.elementId(),
                            start = relationship.startNodeElementId(),
                            end = relationship.endNodeElementId(),
                            distance = relationship["total_cost"].asDouble(0.0),
                        )
                    }

                CsvUtil.writeRelationshipToCsv(records)
            }
        } catch (ex: Neo4jException) {
            ex.printStackTrace()
            throw ex
        }
    }

    private val graphName = "graph_nd"

    private fun getNodeProjection(database: Database): String {
        return when(database) {
            Database.DEFAULT -> "['Apartment', 'PublicTransport', 'EducationalFacility', 'ConvenienceFacility']"
            Database.ROAD_NETWORK -> "'Point'"
        }
    }

    private fun getRelationshipProjection(database: Database): String {
        return when(database) {
            Database.DEFAULT -> "{NETWORK_DISTANCE: { orientation: 'UNDIRECTED', properties: 'weight' }}"
            Database.ROAD_NETWORK -> "{CONNECT: { orientation: 'UNDIRECTED', properties: 'weight' }}"
        }
    }

    private fun getCreateGraphCypher(database: Database, centrality: Centrality): String {
        return """
            Call gds.graph.project(
                '$graphName',
                 ${getNodeProjection(database)},
                 ${getRelationshipProjection(database)}
             );
        """.trimIndent()
    }

    private fun getCalculationCypher(centrality: Centrality): String {
        return """
            CALL gds.${centrality.value}.stream('${graphName}', {
              maxIterations: 20,
              relationshipWeightProperty: 'weight'
            })
            YIELD nodeId, score
            RETURN 
                gds.util.asNode(nodeId) AS node,
                score
            ORDER BY score DESC
        """.trimIndent()
    }

    /**
     * 아파트 - 주변 시설 간의 Centrality 를 계산한다.
     */
    fun runCentrality(database: Database, centrality: Centrality): List<MyNode> {
        // check if graph exists
        val graphName = "graph_nd"
        val qRemoveGraphIfExists = "Call gds.graph.drop('$graphName', false);"
        val qCreateGraph = getCreateGraphCypher(database, centrality)
        val qCalculation = getCalculationCypher(centrality)

        try {
            val result = createSession(database).use {
                it.run(qRemoveGraphIfExists)
                it.run(qCreateGraph)
                val result = it.run(qCalculation)
                val myNodeList = result.list().map { record ->
                    val node = record["node"].asNode()

                    MyNode(
                        node = node,
                        id = node.elementId(),
                        type = when {
                            node.labels().first() == "Apartment" -> "Apartment"
                            else -> node["type"].asString()
                        },
                        name = node["name"].asString(),
                        point = node["coord"].asPoint(),
                        score = record["score"].asDouble()
                    )
                }

                myNodeList
                    .sortedByDescending { item -> item.score }
                    .take(4)
                    .forEach {
                        println("name=${it.name}, score=${it.score}")
                    }

                myNodeList
            }

            GeoToolsUtil.createPointShapeFile(database, centrality, result)

            return result
        } catch (e: Neo4jException) {
            e.printStackTrace()
            throw e
        }
    }

    fun createRoadNetwork(lineList: List<Pair<MultiLineString, Double>>) {

        val start = System.currentTimeMillis()

        try {
            createSession(Database.ROAD_NETWORK).use { session ->
                session.executeWriteWithoutResult { tr ->
                    // 이전의 데이터는 삭제
                    tr.run("MATCH (n) DETACH DELETE n").consume()

                    for ((multiLineString, length) in lineList) {
                        val coordinates = multiLineString.coordinates

                        val point1 = coordinates.first()
                        val point2 = coordinates.last()

                        val createLine = """
                                MERGE (a: Point {coord: POINT({latitude:toFloat(${"$"}lat1), longitude:toFloat(${"$"}lng1)})})
                                MERGE (b: Point {coord: POINT({latitude:toFloat(${"$"}lat2), longitude:toFloat(${"$"}lng2)})})
                                MERGE (a)-[r:CONNECT {length: ${"$"}length, weight: ${"$"}weight}]-(b)
                            """.trimIndent()

                        val result = tr.run(
                            createLine,
                            mapOf(
                                "lat1" to point1.y,
                                "lng1" to point1.x,
                                "lat2" to point2.y,
                                "lng2" to point2.x,
                                "length" to length,
                                "weight" to 1/length,
                            ))

                        result.consume()
                    }
                }
            }

            println("Job done. take ${(System.currentTimeMillis() - start)/1000.0} 초")

        } catch (ex: Neo4jException) {
            LOGGER.log(Level.SEVERE, "Neo4j Exception. (${ex.message})", ex)
//            throw ex
        }
    }

    fun findApartment(apartmentName: String) {
        val readPersonByNameQuery = """
               MATCH (p:Apartment)
               WHERE p.apt_title = ${"$"}apart_name
               RETURN p.addr AS name
               """.trimIndent()
        println(readPersonByNameQuery)
        val params = Collections.singletonMap<String, Any>("apart_name", apartmentName)
        try {
            mySession.use { session ->
                val record = session.readTransaction { tx: Transaction ->
                    val result = tx.run(readPersonByNameQuery, params)
                    result.single()
                }
                println(String.format("Found person: %s", record["name"].asString()))
            }
        } catch (ex: Neo4jException) {
            LOGGER.log(Level.SEVERE, "$readPersonByNameQuery raised an exception", ex)
            throw ex
        }
    }

    companion object {
        private val LOGGER = Logger.getLogger(Neo4jUtil::class.java.name)
    }
}