import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import org.geotools.data.DataUtilities
import org.geotools.data.DefaultTransaction
import org.geotools.data.FileDataStoreFinder
import org.geotools.data.Transaction
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
import org.geotools.styling.SLD
import org.geotools.styling.Style
import org.geotools.styling.StyleBuilder
import org.geotools.swing.JMapFrame
import org.geotools.swing.data.JFileDataStoreChooser
import org.geotools.tile.TileService
import org.geotools.tile.impl.osm.OSMService
import org.geotools.tile.util.TileLayer
import org.geotools.util.logging.Logging
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Point
import org.opengis.feature.simple.SimpleFeature
import org.opengis.feature.simple.SimpleFeatureType
import java.awt.Color
import java.awt.Font
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.Serializable
import java.nio.charset.Charset
import javax.swing.UIManager
import kotlin.String
import kotlin.system.exitProcess


object Quickstart {

    private val LOGGER = Logging.getLogger(Quickstart::class.java)

    fun convertCsvToShp() {

        // Set cross-platform look & feel for compatability
        UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName())
        val file = JFileDataStoreChooser.showOpenFile("csv", null) ?: return


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
        BufferedReader(FileReader(file)).use { reader ->
            /* First line of the data file is the header */
            var line = reader.readLine()
            println("Header: $line")

            val indexOfLatitude = line.split(",").indexOfFirst { it == "lat" || it == "latitude" }
            val indexOfLongitude = line.split(",").indexOfFirst { it == "lng" || it == "lon" || it == "longitude" }
            val indexOfName = line.split(",").indexOf("n_apt_title")

            println("indexOfLatitude = $indexOfLatitude")
            println("indexOfLongitude = $indexOfLongitude")

            line = reader.readLine()
            while (line != null) {
                if (line.trim { it <= ' ' }.isNotEmpty()) { // skip blank lines
                    val tokens = line.split("\\,".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    val latitude = tokens[indexOfLatitude].toDoubleOrNull()
                    val longitude = tokens[indexOfLongitude].toDoubleOrNull()

                    val name = tokens[indexOfName].trim { it <= ' ' }
                    val number = 100

//                    println("number = $number")
//                    println("name = $name")
//                    println("latitude = $latitude")
//                    println("longitude = $longitude")

                    println("tokens = ${tokens.size}")

                    if (latitude != null && longitude != null) {
                        /* Longitude (= x coord) first ! */
                        val point = geometryFactory.createPoint(Coordinate(longitude, latitude))
                        featureBuilder.add(point)
                        featureBuilder.add(name)
                        featureBuilder.add(number)
                        val feature = featureBuilder.buildFeature(null)
                        features.add(feature)
                    }
                }
                line = reader.readLine()
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
    fun loadShapeFileAndDisplay(inputFile: File? = null) {
        // display a data store file chooser dialog for shapefiles
        val file = inputFile ?: JFileDataStoreChooser.showOpenFile("shp", null) ?: return

        LOGGER.config("File selected $file")
        println("File selected $file")
        val store = FileDataStoreFinder.getDataStore(file)
        println("File selected - store = $store")

        val featureSource = store.featureSource
        println("File selected - featureSource = $featureSource")

        val test = featureSource.dataStore

        println("test = ${test}")

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



        println("featureSource.schema=${featureSource.schema}")

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
    private fun getNewShapeFile(csvFile: File): File {
        val path = csvFile.absolutePath
        val newPath = path.substring(0, path.length - 4) + ".shp"
        val chooser = JFileDataStoreChooser("shp")
        chooser.dialogTitle = "Save shapefile"
        chooser.selectedFile = File(newPath)
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

    fun test() {

        val file = File("apt_info - apt_info.csv")

        try {
            val format: CSVFormat = CSVFormat.RFC4180.withHeader().withDelimiter(',')
            //보통 공공데이터포탈의 경우 인코딩은 euckr
            val parseCsv: CSVParser = CSVParser.parse(file, Charset.forName("UTF-8"), format)
//            println("header headerMap = ${parseCsv.headerMap}")
//            println("header headerNames = ${parseCsv.headerNames}")


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
//            val service: TileService = OSMService("vworld", baseURL)
//            val tile_layer = TileLayer(service)
//            mapContent.addLayer(tile_layer)

            val pointCollection = DefaultFeatureCollection()
            val pointFeatureBuilder = SimpleFeatureBuilder(pointTb.buildFeatureType())
            val layerPoints: Layer
            val coords: ArrayList<Coordinate> = ArrayList()

            for (record in records) {
                println("record = ${record[2]}")

//                println("record = ${records[i].size()}")

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
                    coords.add(Coordinate(lon, lat))
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
