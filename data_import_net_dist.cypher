// 계산된 network distance 를 삽입

// 아파트 - 편의시설
LOAD CSV WITH HEADERS FROM 'https://docs.google.com/spreadsheets/d/e/2PACX-1vTDIO-i0G9C5eqdOjoJEjaam-h2h9ucWyIqgC8D4DYWGZZcRrFf1ml1FyCOw4RGyFhyVUMI6_ODcHOF/pub?gid=23485225&single=true&output=csv' AS nodeRecord
MATCH (apt:Apartment) WHERE ID(apt) = toInteger(nodeRecord.origin_id)
MATCH (target:ConvenienceFacility) WHERE ID(target) = toInteger(nodeRecord.destination_id)
    MERGE (apt)-[d:NETWORK_DISTANCE]->(target)
        SET
            d.entry_cost = toFloat(nodeRecord.entry_cost),
            d.network_cost = toFloat(nodeRecord.network_cost),
            d.exit_cost = toFloat(nodeRecord.exit_cost),
            d.total_cost = toFloat(nodeRecord.total_cost);

// 아파트 - 교육시설
LOAD CSV WITH HEADERS FROM 'https://docs.google.com/spreadsheets/d/e/2PACX-1vTDIO-i0G9C5eqdOjoJEjaam-h2h9ucWyIqgC8D4DYWGZZcRrFf1ml1FyCOw4RGyFhyVUMI6_ODcHOF/pub?gid=1500050291&single=true&output=csv' AS nodeRecord
MATCH (apt:Apartment) WHERE ID(apt) = toInteger(nodeRecord.origin_id)
MATCH (target:EducationalFacility) WHERE ID(target) = toInteger(nodeRecord.destination_id)
    MERGE (apt)-[d:NETWORK_DISTANCE]->(target)
        SET
            d.entry_cost = toFloat(nodeRecord.entry_cost),
            d.network_cost = toFloat(nodeRecord.network_cost),
            d.exit_cost = toFloat(nodeRecord.exit_cost),
            d.total_cost = toFloat(nodeRecord.total_cost);

// 아파트 - 대중교통
LOAD CSV WITH HEADERS FROM 'https://docs.google.com/spreadsheets/d/e/2PACX-1vTDIO-i0G9C5eqdOjoJEjaam-h2h9ucWyIqgC8D4DYWGZZcRrFf1ml1FyCOw4RGyFhyVUMI6_ODcHOF/pub?gid=569136240&single=true&output=csv' AS nodeRecord
MATCH (apt:Apartment) WHERE ID(apt) = toInteger(nodeRecord.origin_id)
MATCH (target:PublicTransport) WHERE ID(target) = toInteger(nodeRecord.destination_id)
    MERGE (apt)-[d:NETWORK_DISTANCE]->(target)
        SET
            d.entry_cost = toFloat(nodeRecord.entry_cost),
            d.network_cost = toFloat(nodeRecord.network_cost),
            d.exit_cost = toFloat(nodeRecord.exit_cost),
            d.total_cost = toFloat(nodeRecord.total_cost);

// 거리 5000 m 넘는 연결은 삭제
MATCH ()-[r:NETWORK_DISTANCE]->() where r.total_cost > 5000 delete r;