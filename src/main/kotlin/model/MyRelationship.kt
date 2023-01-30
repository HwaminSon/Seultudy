package model

import org.neo4j.driver.types.Relationship

data class MyRelationship(
    val relationship: Relationship,
    val id: String,
    val start: String,
    val end: String,
    val distance: Double,
)