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

    enum class Centrality {
        betweenness,
        eigenvector,
    }

    private val mySession
        get() = driver.session(SessionConfig.forDatabase("neo4j"))

    private fun createSession(database: String = "neo4j"): Session {
        return driver.session(SessionConfig.forDatabase(database))
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

    fun runCentrality(centrality: Centrality): List<MyNode> {
        // check if graph exists
        val graphName = "graph_nd"
        val qRemoveGraphIfExists = "Call gds.graph.drop('$graphName', false);"
        val qCreateGraph = """
            Call gds.graph.project(
                '$graphName',
                 ['Apartment', 'PublicTransport', 'EducationalFacility', 'ConvenienceFacility'],
                 {NETWORK_DISTANCE: { orientation: 'UNDIRECTED', properties: 'weight' }}
             );
        """.trimIndent()
        val qCalculation = """
            CALL gds.${centrality.name}.stream('${graphName}', {
              maxIterations: 20,
              relationshipWeightProperty: 'weight'
            })
            YIELD nodeId, score
            RETURN 
                gds.util.asNode(nodeId) AS node,
                score
            ORDER BY score DESC
        """.trimIndent()

        return try {
            mySession.use {
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
        } catch (e: Neo4jException) {
            e.printStackTrace()
            throw e
        }
    }

    fun createRoadNetwork(lineList: List<MultiLineString>) {

        val start = System.currentTimeMillis()

        try {
            createSession("road-network").use { session ->
                session.writeTransaction { tx: Transaction ->
                    for (multiLineString in lineList) {
                        val coordinates = multiLineString.coordinates
                        for (i in 1 until coordinates.size) {
                            println("Connect ${coordinates[i-1]} - ${coordinates[i]}")
                            val point1 = coordinates[i-1]
                            val point2 = coordinates[i]
                            val createLine = """
                                MERGE (a: Point {coordinate: POINT({latitude:toFloat(${"$"}lat1), longitude:toFloat(${"$"}lng1)})})
                                MERGE (b: Point {coordinate: POINT({latitude:toFloat(${"$"}lat2), longitude:toFloat(${"$"}lng2)})})
                                MERGE (a)-[r:CONNECT]-(b)
                            """.trimIndent()

                            tx.run(
                                createLine,
                                mapOf(
                                    "lat1" to point1.y,
                                    "lng1" to point1.x,
                                    "lat2" to point2.y,
                                    "lng2" to point2.x,
                                ))
                        }
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