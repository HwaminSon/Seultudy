package utils

import org.neo4j.driver.types.Node
import org.neo4j.driver.types.Point

data class MyResult(
    val node: Node,
    val id: String,
    val type: String,
    val name: String,
    val point: Point,
    val score: Double,
)