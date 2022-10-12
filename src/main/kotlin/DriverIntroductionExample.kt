import io.github.cdimascio.dotenv.Dotenv
import org.neo4j.driver.*
import org.neo4j.driver.exceptions.Neo4jException
import java.util.*
import java.util.logging.Level
import java.util.logging.Logger

class DriverIntroductionExample(uri: String?, user: String?, password: String?, config: Config?) : AutoCloseable {
    private val driver: Driver

    init {
        // The driver is a long living object and should be opened during the start of your application
        driver = GraphDatabase.driver(uri, AuthTokens.basic(user, password), config)
    }

    @Throws(Exception::class)
    override fun close() {
        // The driver object should be closed before the application ends.
        driver.close()
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
            driver.session(SessionConfig.forDatabase("neo4j")).use { session ->
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
            driver.session(SessionConfig.forDatabase("neo4j")).use { session ->
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
        private val LOGGER = Logger.getLogger(DriverIntroductionExample::class.java.name)
        @Throws(Exception::class)
        @JvmStatic
        fun main(args: Array<String>) {

            val env = Dotenv.load()
            val NEO4J_URI = env["NEO4J_URI"]
            val NEO4J_USERNAME = env["NEO4J_USERNAME"]
            val NEO4J_PASSWORD = env["NEO4J_PASSWORD"]
            val AURA_INSTANCENAME = env["AURA_INSTANCENAME"]

            DriverIntroductionExample(NEO4J_URI, NEO4J_USERNAME, NEO4J_PASSWORD, Config.defaultConfig()).use { app ->
//                app.createFriendship("Alice", "David")
                app.findApartment("반포자이")
            }
        }
    }
}