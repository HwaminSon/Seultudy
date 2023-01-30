package utils

import model.MyNode
import model.MyRelationship
import org.apache.commons.csv.CSVFormat
import java.io.File
import java.io.IOException
import java.nio.charset.Charset

object CsvUtil {
    fun writeNodeToCsv(myNodes: List<MyNode>, fileName: String? = null) {
        try {
            val fileName = fileName ?: "result_${System.currentTimeMillis()}"
            println("fileName = $fileName")
            val file = File("./output/$fileName.csv")

            CSVFormat.DEFAULT.print(file, Charset.defaultCharset()).apply {
                printRecord(
                    "id",
                    "type",
                    "name",
                    "x",
                    "y",
                    "score"
                )
                myNodes.forEach { result ->
                    printRecord(
                        result.id,
                        result.type,
                        result.name,
                        result.point.x(),
                        result.point.y(),
                        result.score
                    )
                }
                close()
            }
        } catch (e:IOException) {
            e.printStackTrace()
        }
    }

    fun writeRelationshipToCsv(relationships: List<MyRelationship>, fileName: String? = null) {
        try {
            val fileName = fileName ?: "relationships_${System.currentTimeMillis()}"
            println("fileName = $fileName")
            val file = File("./output/$fileName.csv")

            CSVFormat.DEFAULT.print(file, Charset.defaultCharset()).apply {
                printRecord(
                    "id",
                    "start",
                    "end",
                    "distance",
                )
                relationships.forEach { result ->
                    printRecord(
                        result.id,
                        result.start,
                        result.end,
                        result.distance,
                    )
                }
                close()
            }
        } catch (e:IOException) {
            e.printStackTrace()
        }
    }
}
