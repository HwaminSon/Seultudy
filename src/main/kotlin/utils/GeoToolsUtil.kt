package utils

import model.MyNode
import model.MyRelationship
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import org.geotools.data.*
import org.geotools.data.collection.ListFeatureCollection
import org.geotools.data.shapefile.ShapefileDataStore
import org.geotools.data.shapefile.ShapefileDataStoreFactory
import org.geotools.data.simple.SimpleFeatureCollection
import org.geotools.data.simple.SimpleFeatureSource
import org.geotools.data.simple.SimpleFeatureStore
import org.geotools.feature.DefaultFeatureCollection
import org.geotools.feature.simple.SimpleFeatureBuilder
import org.geotools.feature.simple.SimpleFeatureTypeBuilder
import org.geotools.geometry.jts.JTSFactoryFinder
import org.geotools.map.FeatureLayer
import org.geotools.map.Layer
import org.geotools.map.MapContent
import org.geotools.referencing.CRS
import org.geotools.styling.SLD
import org.geotools.styling.StyleBuilder
import org.geotools.swing.JMapFrame
import org.geotools.swing.data.JFileDataStoreChooser
import org.geotools.util.logging.Logging
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.LineString
import org.locationtech.jts.geom.MultiLineString
import org.locationtech.jts.geom.Point
import org.opengis.feature.Feature
import org.opengis.feature.simple.SimpleFeature
import org.opengis.feature.simple.SimpleFeatureType
import java.awt.Color
import java.io.File
import java.io.Serializable
import java.nio.charset.Charset
import java.nio.file.Paths
import javax.swing.UIManager
import kotlin.String
import kotlin.system.exitProcess


object GeoToolsUtil {

    private val LOGGER = Logging.getLogger(GeoToolsUtil::class.java)

    fun readLineShapeFile(filePath: String): List<Pair<MultiLineString, Double>> {
        val file = File(filePath)

        try {
            val dataStore = DataStoreFinder.getDataStore(
                mapOf(
                    "url" to file.toURI().toString()
                )
            )

            val typeName = dataStore.typeNames[0]

            val lineList = dataStore.getFeatureSource(typeName).features.toArray().map {
                val feature = it as Feature

                Pair(
                    feature.defaultGeometryProperty.value as MultiLineString,
                    feature.properties.firstOrNull { p -> p.name.toString() == "length" }?.value as Double
                )
            }

            return lineList
        } catch (e: Throwable) {
            throw e
        }
    }

    fun convertCsvToShp(csvFile: File? = null) {

        // Set cross-platform look & feel for compatability
        UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName())

        val file = csvFile ?: JFileDataStoreChooser.showOpenFile("csv", null) ?: return

        /*
         * We use the DataUtilities class to create a FeatureType that will describe the data in our
         * shapefile.
         *
         * See also the createFeatureType method below for another, more flexible approach.
         */
        val TYPE = DataUtilities.createType(
            "Location",
            "the_geom:Point:srid=4326,"
                    + // <- the geometry attribute: Point type
                    "name:String,"
                    + // <- a String attribute
                    "number:Integer" // a number attribute
        );
        println("TYPE:$TYPE");

        /*
         * A list to collect features as we create them.
         */
        val features: MutableList<SimpleFeature> = ArrayList()

        /*
         * GeometryFactory will be used to create the geometry attribute of each feature,
         * using a Point object for the location.
         */
        val geometryFactory = JTSFactoryFinder.getGeometryFactory()
        val featureBuilder = SimpleFeatureBuilder(TYPE)


        val parseCsv = CSVParser.parse(
            file,
            Charset.forName("UTF-8"),
            CSVFormat.RFC4180.withHeader().withDelimiter(',')
        )

        val indexOfId = parseCsv.headerMap["apt_id"] ?: throw Exception("There is no 'apt_id'")
        val indexOfName = parseCsv.headerMap["n_apt_title"] ?: throw Exception("There is no 'n_apt_title'")
        val indexOfLatitude = parseCsv.headerMap["lat"] ?: throw Exception("There is no 'lat'")
        val indexOfLongitude = parseCsv.headerMap["lon"] ?: throw Exception("There is no 'lon'")

        for (record in parseCsv.records) {
            val lon = record[indexOfLongitude].toDoubleOrNull() //경도 컬럼
            val lat = record[indexOfLatitude].toDoubleOrNull() //위도 컬럼
            val name = record[indexOfName] ?: "LABEL"
            val id = record[indexOfId]

            val number = 100

            if (lon == null) {
                println("longitude is null!! on $record")
            } else if (lat == null) {
                println("latitude is null!! on $record")
            } else {
                /* Longitude (= x coord) first ! */
                val point = geometryFactory.createPoint(Coordinate(lon, lat))
                featureBuilder.add(point)
                featureBuilder.add(name)
                featureBuilder.add(number)
                val feature = featureBuilder.buildFeature(null)
                features.add(feature)
            }
        }

        /*
         * Get an output file name and create the new shapefile
         */
        val newFile = getNewShapeFile(file)
        val dataStoreFactory = ShapefileDataStoreFactory()
        val params: MutableMap<String, Serializable?> = HashMap()
        params["url"] = newFile.toURI().toURL()
        params["create spatial index"] = true
        val newDataStore = dataStoreFactory.createNewDataStore(params) as ShapefileDataStore

        /*
         * TYPE is used as a template to describe the file contents
         */
        newDataStore.createSchema(TYPE)

        /*
        * Write the features to the shapefile
        */
        val transaction: Transaction = DefaultTransaction("create");
        val typeName: String = newDataStore.typeNames[0];
        val featureSource: SimpleFeatureSource = newDataStore.getFeatureSource(typeName);
        val SHAPE_TYPE: SimpleFeatureType = featureSource.schema;

        /*
         * The Shapefile format has a couple limitations:
         * - "the_geom" is always first, and used for the geometry attribute name
         * - "the_geom" must be of type Point, MultiPoint, MuiltiLineString, MultiPolygon
         * - Attribute names are limited in length
         * - Not all data types are supported (example Timestamp represented as Date)
         *
         * Each data store has different limitations so check the resulting SimpleFeatureType.
         */
        println("SHAPE:$SHAPE_TYPE");

        if (featureSource is SimpleFeatureStore) {
            val featureStore: SimpleFeatureStore = featureSource
            /*
             * SimpleFeatureStore has a method to add features from a
             * SimpleFeatureCollection object, so we use the ListFeatureCollection
             * class to wrap our list of features.
             */
            val collection: SimpleFeatureCollection = ListFeatureCollection(TYPE, features);
            featureStore.transaction = transaction;
            try {
                featureStore.addFeatures(collection);
                transaction.commit();
            } catch ( problem: Exception) {
                problem.printStackTrace();
                transaction.rollback();
            } finally {
                transaction.close();
            }
            exitProcess(0); // success!
        } else {
            println("$typeName does not support read/write access");
            exitProcess(1);
        }
    }

    fun createShapeFileWithResults(myNodeList: List<MyNode>) {

        // Set cross-platform look & feel for compatability
        UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName())

        /*
         * We use the DataUtilities class to create a FeatureType that will describe the data in our
         * shapefile.
         *
         * See also the createFeatureType method below for another, more flexible approach.
         */
        val TYPE = DataUtilities.createType(
            "Location",
            "the_geom:Point:srid=4326," // <- the geometry attribute: Point type
                    + "name:String,"// <- a String attribute
                    + "score:Double" // a number attribute
        );

        /*
         * A list to collect features as we create them.
         */
        val features: MutableList<SimpleFeature> = ArrayList()

        /*
         * GeometryFactory will be used to create the geometry attribute of each feature,
         * using a Point object for the location.
         */
        val geometryFactory = JTSFactoryFinder.getGeometryFactory()
        val featureBuilder = SimpleFeatureBuilder(TYPE)

        for (myNode in myNodeList) {
            featureBuilder.add(
                geometryFactory.createPoint(
                    Coordinate(
                        myNode.point.x(),
                        myNode.point.y()
                    )
                )
            )
            featureBuilder.add("[${myNode.type}] ${myNode.name}")
            featureBuilder.add(myNode.score)
            val feature = featureBuilder.buildFeature(myNode.id)
            features.add(feature)
        }

        /*
         * Get an output file name and create the new shapefile
         */
        val newFile = getNewShapeFile()
        val dataStoreFactory = ShapefileDataStoreFactory()
        val params: MutableMap<String, Serializable?> = HashMap()
        params["url"] = newFile.toURI().toURL()
        params["create spatial index"] = true
        val newDataStore = dataStoreFactory.createNewDataStore(params) as ShapefileDataStore

        /*
         * TYPE is used as a template to describe the file contents
         */
        newDataStore.createSchema(TYPE)

        /*
        * Write the features to the shapefile
        */
        val transaction: Transaction = DefaultTransaction("create");
        val typeName: String = newDataStore.typeNames[0];
        val featureSource: SimpleFeatureSource = newDataStore.getFeatureSource(typeName);
        val SHAPE_TYPE: SimpleFeatureType = featureSource.schema;

        /*
         * The Shapefile format has a couple limitations:
         * - "the_geom" is always first, and used for the geometry attribute name
         * - "the_geom" must be of type Point, MultiPoint, MuiltiLineString, MultiPolygon
         * - Attribute names are limited in length
         * - Not all data types are supported (example Timestamp represented as Date)
         *
         * Each data store has different limitations so check the resulting SimpleFeatureType.
         */
        println("SHAPE:$SHAPE_TYPE");

        if (featureSource is SimpleFeatureStore) {
            val featureStore: SimpleFeatureStore = featureSource
            /*
             * SimpleFeatureStore has a method to add features from a
             * SimpleFeatureCollection object, so we use the ListFeatureCollection
             * class to wrap our list of features.
             */
            val collection: SimpleFeatureCollection = ListFeatureCollection(TYPE, features);
            featureStore.transaction = transaction;
            try {
                featureStore.addFeatures(collection);
                transaction.commit();
            } catch ( problem: Exception) {
                problem.printStackTrace();
                transaction.rollback();
            } finally {
                transaction.close();
            }
            exitProcess(0); // success!
        } else {
            println("$typeName does not support read/write access");
            exitProcess(1);
        }
    }

    fun createPointShapeFile(nodes: List<MyNode>) {
        val geometryFactory = JTSFactoryFinder.getGeometryFactory()

//        val featureType = SimpleFeatureTypeBuilder().apply {
//            name = "Point"
//            setSRS("EPSG:4326")
//            add("the_geom", Point::class.java)
//            add("name", String::class.java)
//            add("type", String::class.java)
//            add("score", Double::class.java)
//
//        }.buildFeatureType()
        val featureType = DataUtilities.createType(
            "Point",
            "the_geom:Point:srid=4326," // <- the geometry attribute: Point type
                    + "name:String,"// <- a String attribute
                    + "type:String,"// <- a String attribute
                    + "score:Double" // a number attribute
        );

        val featureCollection = DefaultFeatureCollection()

        val featureBuilder = SimpleFeatureBuilder(featureType)

        featureCollection.addAll(nodes.map { node ->
            featureBuilder.add(geometryFactory.createPoint(Coordinate(node.point.x(), node.point.y())))
            featureBuilder.add(node.name)
            featureBuilder.add(node.type)
            featureBuilder.add(node.score)
            featureBuilder.buildFeature(null)
        })

        val dataStore = ShapefileDataStoreFactory()
            .createNewDataStore(mapOf(
                "url" to Paths.get("./output/centrality_result_${System.currentTimeMillis()}.shp").toUri().toURL(),
                "create spatial index" to true,
                "charset" to Charset.forName("UTF-8")
            )) as ShapefileDataStore

        dataStore.createSchema(featureType)
        dataStore.forceSchemaCRS(CRS.decode("EPSG:4326"))

        val transaction = DefaultTransaction("create")
        val typeName = dataStore.typeNames[0]
        val featureSource = dataStore.getFeatureSource(typeName)
        val featureStore = featureSource as SimpleFeatureStore
        featureStore.transaction = transaction
        try {
            featureStore.addFeatures(featureCollection)
            transaction.commit()
        } catch (e: Exception) {
            transaction.rollback()
        } finally {
            transaction.close()
        }
    }

    fun createLineShapeFile(relationships: List<MyRelationship>) {
        val geometryFactory = JTSFactoryFinder.getGeometryFactory()

        val featureType = SimpleFeatureTypeBuilder().apply {
            name = "Route"
            add("the_geom", LineString::class.java)
            add("name", String::class.java)
        }.buildFeatureType()

        val featureCollection = DefaultFeatureCollection()

        val featureBuilder = SimpleFeatureBuilder(featureType)

        featureBuilder.add(geometryFactory.createLineString(arrayOf(Coordinate(1.0, 1.0), Coordinate(3.0, 3.0))))
        featureBuilder.add("경로 A")

        featureCollection.add(featureBuilder.buildFeature(null))

        featureBuilder.add(geometryFactory.createLineString(arrayOf(Coordinate(3.0, 3.0), Coordinate(5.0, 1.0))))
        featureBuilder.add("경로 B")

        featureCollection.add(featureBuilder.buildFeature(null))


        val dataStore = ShapefileDataStoreFactory()
            .createNewDataStore(mapOf(
                "url" to Paths.get("./output/test_line.shp").toUri().toURL(),
                "create spatial index" to true,
                "charset" to Charset.forName("UTF-8")
            )) as ShapefileDataStore

        dataStore.createSchema(featureType)
//        dataStore.forceSchemaCRS(CRS.decode("EPSG:4326"))

        val transaction = DefaultTransaction("create")
        val typeName = dataStore.typeNames[0]
        val featureSource = dataStore.getFeatureSource(typeName)
        val featureStore = featureSource as SimpleFeatureStore
        featureStore.transaction = transaction
        try {
            featureStore.addFeatures(featureCollection)
            transaction.commit()
        } catch (e: Exception) {
            transaction.rollback()
        } finally {
            transaction.close()
        }
    }

    fun showMapWithShapeFile(inputFile: File? = null) {
        // display a data store file chooser dialog for shapefiles
        val file = inputFile ?: JFileDataStoreChooser.showOpenFile("shp", null) ?: return

        LOGGER.config("File selected $file")
        val store = FileDataStoreFinder.getDataStore(file)

        val featureSource = store.featureSource

        val test = featureSource.dataStore

        // Create a map content and add our shapefile to it
        val map = MapContent()
        map.title = "Quickstart"
//        val style = SLD.createSimpleStyle(featureSource.schema)
//        val style = SLD.createPointStyle("Circle", Color.BLUE, Color.RED, 0.5f, 13.0f)

        val style = SLD.createPointStyle("Circle", Color.BLACK, Color.RED, 0.9f, 8f).apply {
            //라벨 폰트 스타일 설정
//            val styleBuilder = StyleBuilder()
//            val font = styleBuilder.createFont("맑은 고딕", 10.0)
//            val textSymb = styleBuilder.createTextSymbolizer(Color.black, font, "LABEL")
//            val labelPlace = styleBuilder.createPointPlacement(0.5, 1.5, 0.0)
//            textSymb.setLabelPlacement(labelPlace)
//            val rule = styleBuilder.createRule(textSymb)
//            featureTypeStyles()[0].rules().add(rule)
        }

//        featureSource.features

        val layer: Layer = FeatureLayer(featureSource.features, style)
        map.addLayer(layer)

        // Now display the map
        JMapFrame.showMap(map)
    }

    /**
     * Prompt the user for the name and path to use for the output shapefile
     *
     * @param csvFile the input csv file used to create a default shapefile name
     * @return name and path for the shapefile as a new File object
     */
    private fun getNewShapeFile(csvFile: File? = null): File {
        val chooser = JFileDataStoreChooser("shp")
        chooser.dialogTitle = "Save shapefile"

        if (csvFile != null) {
            val path = csvFile.absolutePath
            val newPath = path.substring(0, path.length - 4) + ".shp"
            chooser.selectedFile = File(newPath)
        }

        val returnVal = chooser.showSaveDialog(null)
        if (returnVal != JFileDataStoreChooser.APPROVE_OPTION) {
            // the user cancelled the dialog
            exitProcess(0)
        }
        val newFile = chooser.selectedFile
        if (newFile == csvFile) {
            println("Error: cannot replace $csvFile")
            exitProcess(0)
        }
        return newFile
    }

    fun showMapWithCsvFile() {
        val file = File("apt_info - apt_info.csv")

        try {
            val format: CSVFormat = CSVFormat.RFC4180.withHeader().withDelimiter(',')
            val parseCsv: CSVParser = CSVParser.parse(file, Charset.forName("UTF-8"), format)

            val indexOfId = parseCsv.headerMap["apt_id"] ?: throw Exception("There is no 'apt_id'")
            val indexOfName = parseCsv.headerMap["n_apt_title"] ?: throw Exception("There is no 'n_apt_title'")
            val indexOfLatitude = parseCsv.headerMap["lat"] ?: throw Exception("There is no 'lat'")
            val indexOfLongitude = parseCsv.headerMap["lon"] ?: throw Exception("There is no 'lon'")

            val records: List<CSVRecord> = parseCsv.records
            val features: MutableList<SimpleFeature> = mutableListOf()

            //포인트를 담을 geometryfacotry 생성
            val geometryFactory = JTSFactoryFinder.getGeometryFactory()
            val pointTb = SimpleFeatureTypeBuilder()

            //포인트 라벨을 충전소이름으로 설정
            pointTb.name = "csvPoints"
            pointTb.add("point", Point::class.java)
            pointTb.add("LABEL", String::class.java)

            val mapContent = MapContent()

            //배경지도는 브이월드 타일 레이어로 추가했다.
//            val baseURL = "http://xdworld.vworld.kr:8080/2d/Base/201802/"
//            val baseURL = "https://tile.openstreetmap.org/"
//            mapContent.addLayer(TileLayer(OSMService("OSM", baseURL)))

            val pointCollection = DefaultFeatureCollection()
            val pointFeatureBuilder = SimpleFeatureBuilder(pointTb.buildFeatureType())
            val layerPoints: Layer

            for (record in records) {
                val lon = record[indexOfLongitude].toDoubleOrNull() //경도 컬럼
                val lat = record[indexOfLatitude].toDoubleOrNull() //위도 컬럼
                val label = record[indexOfName] ?: "LABEL"
                val id = record[indexOfId]

                if (lon == null) {
                    println("longitude is null!! on $record")
                } else if (lat == null) {
                    println("latitude is null!! on $record")
                } else {
                    val point = geometryFactory.createPoint(Coordinate(lon, lat))
                    pointFeatureBuilder.add(point)
                    pointFeatureBuilder["LABEL"] = label
//                    pointFeatureBuilder["ID"] = id
                    val feature = pointFeatureBuilder.buildFeature(null)
                    features.add(feature)
                    pointCollection.add(feature)
                }
            }
            //포인트 스타일 설정

            val pointStyle = SLD.createPointStyle("Circle", Color.BLACK, Color.RED, 0.9f, 8f).apply {
                //라벨 폰트 스타일 설정
                val styleBuilder = StyleBuilder()
                val font = styleBuilder.createFont("맑은 고딕", 10.0)
                val textSymb = styleBuilder.createTextSymbolizer(Color.black, font, "LABEL")
                val labelPlace = styleBuilder.createPointPlacement(0.5, 1.5, 0.0)
                textSymb.setLabelPlacement(labelPlace)
                val rule = styleBuilder.createRule(textSymb)
                featureTypeStyles()[0].rules().add(rule)
            }

            layerPoints = FeatureLayer(pointCollection, pointStyle)
            mapContent.addLayer(layerPoints)
            mapContent.viewport.isMatchingAspectRatio = true
//            mapContent.viewport.bounds = bounds

            JMapFrame.showMap(mapContent)
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }
}
