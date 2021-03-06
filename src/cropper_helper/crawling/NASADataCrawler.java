package cropper_helper.crawling;


import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.vividsolutions.jts.geom.Coordinate;
import cropper_helper.data.ThermalSingleValue;
import cropper_helper.database.DatabaseHelper;
import org.codehaus.jackson.map.ObjectMapper;
import org.lightcouch.NoDocumentException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by diego on 14/4/15.
 */
public class NASADataCrawler {
    private static final Logger logger = Logger.getLogger(NASADataCrawler.class.getName());
    private static final String DATA_URL = "https://api.data.gov/nasa/planetary/earth/temperature/coords?lon=%f&lat=%f&begin=1990&end=%d&api_key=DEMO_KEY";

    public static Map<String,Object> updateThermalAnomaly(Coordinate mid_point, String _id) {
        try {
            JsonObject old_data = DatabaseHelper.getThermalDocument(_id);
            int currentYear = Calendar.getInstance().get(Calendar.YEAR);
            if (old_data == null || (currentYear > old_data.get("year").getAsInt())) {
                // Update cache version
                URL url = new URL(String.format(Locale.US, DATA_URL, mid_point.x % 180, mid_point.y % 180, currentYear));
                try (InputStream is = url.openConnection().getInputStream()) {
                    if (old_data != null) {
                        DatabaseHelper.removeDoc(old_data);
                    }
                    Map<String, Object> jsonMap = new ObjectMapper().readValue(is, Map.class);
                    jsonMap.put("_id", _id);
                    jsonMap.put("year", currentYear);

                    DatabaseHelper.storeDoc(jsonMap);
                    logger.log(Level.FINE, "Thermal anomaly information stored in the database");
                    return jsonMap;
                }
            } else if (old_data != null) {
                // Obtain data from cached version
                return new ObjectMapper().readValue(old_data.toString(), HashMap.class);
            } else {
                return null;
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, e.toString(), e);
            return null;
        }
    }

    private static ArrayList<ThermalSingleValue> readArray(JsonReader reader) throws IOException {
        ArrayList<ThermalSingleValue> result = new ArrayList<>();
        reader.beginArray();
        while (reader.hasNext()) {
            result.add(readValue(reader));
        }
        reader.endArray();
        return result;
    }

    private static ThermalSingleValue readValue(JsonReader reader) throws IOException {
        Double anomaly = 0.0;
        int year = 0;
        reader.beginObject();
        while (reader.hasNext()) {
            if (reader.nextName().equals("anomaly")) {
                anomaly = reader.nextDouble();
            } else {
                year = reader.nextInt();
            }
        }
        reader.endObject();
        return new ThermalSingleValue(anomaly, year);
    }
}