import Quickstart.convertCsvToShp
import Quickstart.loadShapeFileAndDisplay
import Quickstart.test
import io.github.cdimascio.dotenv.Dotenv

fun main(args: Array<String>) {
//    val env = System.getenv("aura_ds_hwamin_ds_credentials.env")
    val env = Dotenv.load()
    println("env[NEO4J_URI] = ${env["NEO4J_URI"]}")
    println("env[NEO4J_USERNAME] = ${env["NEO4J_USERNAME"]}")
    println("env[NEO4J_PASSWORD] = ${env["NEO4J_PASSWORD"]}")
    println("env[AURA_INSTANCENAME] = ${env["AURA_INSTANCENAME"]}")



//    convertCsvToShp()
//    loadShapeFileAndDisplay()
//    test()
}

