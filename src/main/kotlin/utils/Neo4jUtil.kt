package utils

import io.github.cdimascio.dotenv.Dotenv
import org.neo4j.driver.*
import org.neo4j.driver.exceptions.Neo4jException
import java.util.*
import java.util.logging.Level
import java.util.logging.Logger

class Neo4jUtil() : AutoCloseable {
    private val driver: Driver

    private val mySession
        get() = driver.session(SessionConfig.forDatabase("neo4j"))


    init {
        // The driver is a long living object and should be opened during the start of your application
//        val env = Dotenv.load()
//        val neo4jUri = env["NEO4J_URI"]
//        val neo4jUserName = env["NEO4J_USERNAME"]
//        val neo4jUserPw = env["NEO4J_PASSWORD"]

        val neo4jUri = "neo4j://localhost:7687"
        val neo4jUserName = "neo4j"
        val neo4jUserPw = "pw"

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

    fun runBetweennessCentrality(): List<MyResult> {
        // check if graph exists
        val graphName = "myUndirectedGraph"
        val qRemoveGraphIfExists = "Call gds.graph.drop('$graphName', false);"
        val qCreateGraph = "Call gds.graph.project('$graphName', ['Apartment', 'PublicTransport', 'EducationalFacility', 'ConvenienceFacility'], {NEAR: { orientation: 'UNDIRECTED'}});"
        val qCalculation = """
            CALL gds.betweenness.stream('myUndirectedGraph')
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
                val myResultList = result.list().map { record ->
                    val node = record["node"].asNode()

                    MyResult(
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

                myResultList
                    .sortedByDescending { item -> item.score }
                    .take(4)
                    .forEach {
                        println("name=${it.name}, score=${it.score}")
                    }
                myResultList
            }
        } catch (e: Neo4jException) {
            e.printStackTrace()
            throw e
        }
    }

    fun createFriendship(person1Name: String, person2Name: String) {
        // To learn more about the Cypher syntax, see https://neo4j.com/docs/cypher-manual/current/
        // The Reference Card is also a good resource for keywords https://neo4j.com/docs/cypher-refcard/current/
        val createFriendshipQuery = """
               CREATE (p1:Person { name: ${"$"}person1_name })
               CREATE (p2:Person { name: ${"$"}person2_name })
               CREATE (p1)-[:KNOWS]->(p2)
               RETURN p1, p2
               """.trimIndent()
        val params: MutableMap<String, Any> = HashMap()
        params["person1_name"] = person1Name
        params["person2_name"] = person2Name
        try {
            mySession.use { session ->
                // Write transactions allow the driver to handle retries and transient errors
                val record = session.writeTransaction { tx: Transaction ->
                    val result = tx.run(createFriendshipQuery, params)
                    result.single()
                }
                println(
                    String.format(
                        "Created friendship between: %s, %s",
                        record["p1"]["name"].asString(),
                        record["p2"]["name"].asString()
                    )
                )
            }
        } catch (ex: Neo4jException) {
            LOGGER.log(Level.SEVERE, "$createFriendshipQuery raised an exception", ex)
            throw ex
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