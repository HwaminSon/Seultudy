package utils

import model.MyNode
import org.apache.commons.csv.CSVFormat
import java.io.File
import java.io.IOException
import java.nio.charset.Charset

object CsvUtil {
    fun writeCsv(myNodes: List<MyNode>, fileName: String? = null) {
        try {
            val fileName = fileName ?: "result_${System.currentTimeMillis()}"
            println("fileName = $fileName")
            val file = File("/Users/hwaminson/dev/shp/boo-talk/$fileName.csv")

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
}
