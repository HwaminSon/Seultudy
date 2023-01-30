// 이전 노드들 모두 삭제
MATCH (n) DETACH DELETE n;

// 0) 아파트, 지역 노드 생성
// 제약조건 설정
CREATE CONSTRAINT IF NOT EXISTS FOR (apt: Apartment) REQUIRE apt.apt_id IS UNIQUE;
CREATE CONSTRAINT IF NOT EXISTS FOR (region_code: Region) REQUIRE region_code.region_code IS UNIQUE;

// apt_info.csv 로드
LOAD CSV WITH HEADERS FROM 'https://docs.google.com/spreadsheets/d/e/2PACX-1vTc_mAKF78DEloooXtrpRent-OzssLxuOvzRlOaYP47Ckf7gdZleo-ovqfWM3F4AYvpxclJ11Qg_g2u/pub?gid=349222695&single=true&output=csv' AS nodeRecord
// Apartment 노드 생성
MERGE (n: Apartment { apt_id: toInteger(nodeRecord.apt_id) })
SET n.name = nodeRecord.apt_title + ' ' + nodeRecord.n_apt_title
SET n.coord = point({latitude: toFloat(nodeRecord.lat), longitude: toFloat(nodeRecord.lon)});


// 대중교통 시설
// 버스정류장 노드 생성 및 연결
// 제약조건 설정
CREATE CONSTRAINT IF NOT EXISTS FOR (bus: BusStation) REQUIRE bus.station_id IS UNIQUE;

// bus_station_all.csv 로드
:auto LOAD CSV WITH HEADERS FROM 'https://docs.google.com/spreadsheets/d/e/2PACX-1vTIklzvtThBMu876zP-d1oxMFl1h5Na5KJd7LnzWpXvIzgYFKxq8gaeub77dwnB8t5fVHzF3cHtv5Ir/pub?gid=1981090009&single=true&output=csv' AS nodeRecord
CALL {
//  BusStation 노드 생성
WITH nodeRecord
MERGE (n: BusStation { station_id: nodeRecord.station_id })
SET n: PublicTransport
SET n.type = "버스정류장"
SET n.name = nodeRecord.station_name
SET n.coord = point({latitude: toFloat(nodeRecord.lat), longitude: toFloat(nodeRecord.lon)})
} IN TRANSACTIONS OF 500 ROWS;

// bus_station_apt.csv 로드
LOAD CSV WITH HEADERS FROM 'https://docs.google.com/spreadsheets/d/e/2PACX-1vQK8j8yiohftDUlFWkmr09ZB3WToM0pkwJebY_uCFNRz9TEpqtMxYAjsqtebyNTfEIQ0CSvwOB5_7Lb/pub?gid=700110950&single=true&output=csv' AS nodeRecord
// (:Apartment)-[:NEAR]->(:BusStation) 관계 생성
MATCH (apt:Apartment {apt_id: toInteger(nodeRecord.apt_id)}), (bs:BusStation {station_id: nodeRecord.station_id})
MERGE (apt)-[r:NEAR]->(bs)
SET r.distance = toFloat(nodeRecord.dist);


// 지하철 노드 생성 및 연결
// 제약조건 설정
CREATE CONSTRAINT IF NOT EXISTS FOR (subway: Subway) REQUIRE subway.no IS UNIQUE;

// subway_station_api.csv 로드
LOAD CSV WITH HEADERS FROM 'https://docs.google.com/spreadsheets/d/e/2PACX-1vTOAIbcJC8aEnoG6nfiPKBtlSpcH_GyT1QS3hQKAG7HT-NARK6XdjHSMu0P3BY-NJWT27wz4_Kss7RV/pub?gid=1641438675&single=true&output=csv' AS nodeRecord
// Subway 노드 생성
MERGE (n: Subway { no: toInteger(nodeRecord.no) })
SET n: PublicTransport
SET n.type = "지하철역"
SET n.name = nodeRecord.line_name + " " + nodeRecord.station_nm + "역"
SET n.station_cd = toInteger(nodeRecord.station_cd)
SET n.station_nm = nodeRecord.station_nm
SET n.line_num = nodeRecord.line_num
SET n.line_name = nodeRecord.line_name
SET n.fr_code = nodeRecord.fr_code
SET n.coord = point({latitude: toFloat(nodeRecord.ypoint_wgs), longitude: toFloat(nodeRecord.xpoint_wgs)});

// subway_station_apt.csv 로드
LOAD CSV WITH HEADERS FROM 'https://docs.google.com/spreadsheets/d/e/2PACX-1vTk-JlJWT3f4y42NMpo1yq6JmSWiZOwCaQGECQnkCiBgoOfKT29J7-rE9V7g9gEKGALvesGro9qlRii/pub?gid=1835856580&single=true&output=csv' AS nodeRecord
// (:Apartment)-[:NEAR]->(:Subway) 관계 생성
MATCH (apt:Apartment {apt_id: toInteger(nodeRecord.apt_id)}), (subway:Subway {no: toInteger(nodeRecord.subway_station_no)})
MERGE (apt)-[r:NEAR]->(subway)
SET r.distance = toFloat(nodeRecord.dist);


// 교육 시설

// 학교 노드 생성 및 연결
// 제약조건 설정
CREATE CONSTRAINT IF NOT EXISTS FOR (school: School) REQUIRE school.name IS UNIQUE;

// apt_school.csv 로드
LOAD CSV WITH HEADERS FROM 'https://docs.google.com/spreadsheets/d/e/2PACX-1vTt4sC-LTIibLdbxJwKmKCxDtzgMLRFXtC5ns7GdXjhGJvlBFFQKFUba58WoLdBOWJw37ce1FUs8SDk/pub?gid=676751154&single=true&output=csv' AS nodeRecord
// School 노드 생성
MERGE (n: School { name: nodeRecord.school_name })
SET n: EducationalFacility
SET n.type = "학교"
SET n.hakgun = nodeRecord.hakgudo_name
SET n.gubun = nodeRecord.school_gubun
SET n.establishment = nodeRecord.establishment
SET n.coord = point({latitude: toFloat(nodeRecord.lat), longitude: toFloat(nodeRecord.lon)})
SET n.homepage = nodeRecord.homepage
// (:Apartment)-[:NEAR]->(:School) 관계 생성
WITH nodeRecord, n
MATCH (apt:Apartment {apt_id:toInteger(nodeRecord.apt_id)})
MERGE (apt)-[r:NEAR]->(n)
SET r.distance = toFloat(nodeRecord.dist);

// 유치원 노드 생성 및 연결
// 제약조건 설정
CREATE CONSTRAINT IF NOT EXISTS FOR (kinder: Kinder) REQUIRE kinder.name IS UNIQUE;

// apt_kinder.csv 로드
LOAD CSV WITH HEADERS FROM 'https://docs.google.com/spreadsheets/d/e/2PACX-1vS8hmuEKzOUwJQwwzSd0XlZD-DfAQ1EtQd_-c2lmAHX6IOxerWYnn0XPAp9vDSOEOifh_lGVy6gRcwz/pub?gid=540904510&single=true&output=csv' AS nodeRecord
// Kinder 노드 생성
MERGE (n: Kinder { name: nodeRecord.name })
SET n: EducationalFacility
SET n.type = "유치원"
SET n.coord = point({latitude: toFloat(nodeRecord.lat), longitude: toFloat(nodeRecord.lon)})
// (:Apartment)-[:NEAR]->(:Kinder) 관계 생성
WITH nodeRecord, n
MATCH (apt:Apartment {apt_id:toInteger(nodeRecord.apt_id)})
MERGE (apt)-[r:NEAR]->(n)
SET r.distance = toFloat(nodeRecord.dist);

// 어린이집 노드 생성 및 연결
// 제약조건 설정
CREATE CONSTRAINT IF NOT EXISTS FOR (daycare: Daycare) REQUIRE daycare.name IS UNIQUE;

// apt_daycare.csv 로드
LOAD CSV WITH HEADERS FROM 'https://docs.google.com/spreadsheets/d/e/2PACX-1vRcmnOv8nDJfFFyer4pc3gHIJqznWoIeR8zJtzVxOclUpIRZvsgbxk0o3bBaw7WDjLO6k-FdXvKpSRJ/pub?gid=693624626&single=true&output=csv' AS nodeRecord
// Daycare 노드 생성
MERGE (n: Daycare { name: nodeRecord.name })
SET n: EducationalFacility
SET n.type = "어린이집"
SET n.coord = point({latitude: toFloat(nodeRecord.lat), longitude: toFloat(nodeRecord.lon)})
// (:Apartment)-[:NEAR]->(:Daycare) 관계 생성
WITH nodeRecord, n
MATCH (apt:Apartment {apt_id:toInteger(nodeRecord.apt_id)})
MERGE (apt)-[r:NEAR]->(n)
SET r.distance = toFloat(nodeRecord.dist);


// 편의시설

// 병원 노드 생성 및 연결
// 제약조건 설정
CREATE CONSTRAINT IF NOT EXISTS FOR (hospital: Hospital) REQUIRE hospital.name IS UNIQUE;

// apt_hospital.csv 로드
LOAD CSV WITH HEADERS FROM 'https://docs.google.com/spreadsheets/d/e/2PACX-1vTNlxKXHqWXNA7TNWcn8kb2B41Mx5iWFHU7lCgbOKMs-wxs5yxMkae3JNconXoqGB3VnMpLewPOl4i1/pub?gid=806087151&single=true&output=csv' AS nodeRecord

// Hospital 노드 생성
MERGE (n: Hospital { name: nodeRecord.name })
SET n: ConvenienceFacility
SET n.type = "병원"
SET n.coord = point({latitude: toFloat(nodeRecord.lat), longitude: toFloat(nodeRecord.lon)})
// (:Apartment)-[:NEAR]->(:Hospital) 관계 생성
WITH nodeRecord, n
MATCH (apt:Apartment {apt_id:toInteger(nodeRecord.apt_id)})
MERGE (apt)-[r:NEAR]->(n)
SET r.distance = toFloat(nodeRecord.dist);


// 마트 노드 생성 및 연결
// 제약조건 설정
CREATE CONSTRAINT IF NOT EXISTS FOR (mart: Mart) REQUIRE mart.name IS UNIQUE;

// apt_mart.csv 로드
LOAD CSV WITH HEADERS FROM 'https://docs.google.com/spreadsheets/d/e/2PACX-1vQsynS5Ocn7DpDy50MyJCN6dOTf3vk4lfeM1oboPugKZAd_s_M_V7O9SksIu8LnT2l2T2Q34LTuAert/pub?gid=507843600&single=true&output=csv' AS nodeRecord
// Mart 노드 생성
MERGE (n: Mart { name: nodeRecord.name })
SET n: ConvenienceFacility
SET n.type = "마트"
SET n.coord = point({latitude: toFloat(nodeRecord.lat), longitude: toFloat(nodeRecord.lon)})
// (:Apartment)-[:NEAR]->(:Mart) 관계 생성
WITH nodeRecord, n
MATCH (apt:Apartment {apt_id:toInteger(nodeRecord.apt_id)})
MERGE (apt)-[r:NEAR]->(n)
SET r.distance = toFloat(nodeRecord.dist);


// 공원 노드 생성 및 연결
// 제약조건 설정
CREATE CONSTRAINT IF NOT EXISTS FOR (park: Park) REQUIRE park.name IS UNIQUE;

// apt_park.csv 로드
LOAD CSV WITH HEADERS FROM 'https://docs.google.com/spreadsheets/d/e/2PACX-1vQQaLtfE7-Z8JPyjJDtuqKbFPoNwipC7Mbsk0YNWKFS1xDDxg71xTN9fxCq2V4TSRnn9oq6Xm1iX-cC/pub?gid=171692443&single=true&output=csv' AS nodeRecord
// Park 노드 생성
MERGE (n: Park { name: nodeRecord.name })
SET n: ConvenienceFacility
SET n.type = "공원"
SET n.coord = point({latitude: toFloat(nodeRecord.lat), longitude: toFloat(nodeRecord.lon)})
// (:Apartment)-[:NEAR]->(:Park) 관계 생성
WITH nodeRecord, n
MATCH (apt:Apartment {apt_id:toInteger(nodeRecord.apt_id)})
MERGE (apt)-[r:NEAR]->(n)
SET r.distance = toFloat(nodeRecord.dist);

// 연결되지 않은 노드 제거.
MATCH (n) WHERE NOT (n)-[]-() DELETE n;
