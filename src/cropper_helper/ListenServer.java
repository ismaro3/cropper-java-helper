package cropper_helper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Polygon;
import cropper_helper.crawling.NASADataCrawler;
import cropper_helper.cropper.Feature;
import cropper_helper.cropper.Subscription;
import cropper_helper.cropper.ThermalZone;
import cropper_helper.notification.CropperNotifier;
import org.apache.commons.io.IOUtils;
import org.codehaus.jackson.map.ObjectMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.InputMismatchException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by dbarelop on 14/4/15.
 */
public class ListenServer implements Runnable {
    private static final Logger logger = Logger.getLogger(ListenServer.class.getName());
    public static final int LISTEN_PORT = 8080;

    public static class NotificationHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange httpExchange) throws IOException {

            try {
                String GET = java.net.URLDecoder.decode(httpExchange.getRequestURI().toString().substring(13), "UTF-8");
                System.out.print(GET + "\n");
                httpExchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");

                httpExchange.sendResponseHeaders(200, "ok".length());
                OutputStream os = httpExchange.getResponseBody();
                os.write("ok".getBytes());
                os.close();

                JsonObject obj = new JsonParser().parse(GET).getAsJsonObject();
                System.out.println("Starting to calculate...");
                CropperNotifier.notifyUsers(new Feature(obj));
                System.out.println("Finished...");

            }
            catch(Exception ex){
               System.out.println("Bad request");
            }
        }
    }

    public static class PlotHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange httpExchange) throws IOException {
            try {


                String GET = java.net.URLDecoder.decode(httpExchange.getRequestURI().toString().substring(14), "UTF-8");
                System.out.print(GET + "\n");


                JsonObject obj = new JsonParser().parse(GET).getAsJsonObject();

                ThermalZone subs = new ThermalZone(obj);

                GeometryFactory gf = new GeometryFactory();

                Polygon p = new Polygon(gf.createLinearRing(subs.getGeometry().getCoordinates().toArray(new Coordinate[0])), null, gf);

                Coordinate mid = new Coordinate(p.getCentroid().getX(), p.getCentroid().getY());


                Map<String, Object> newObj = NASADataCrawler.updateThermalAnomaly(mid, subs.get_id());

                Gson gson = new GsonBuilder().create();
                String json = gson.toJson(newObj);

                //Access-Control-Allow-Origin is essential.
                httpExchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                httpExchange.sendResponseHeaders(200, json.length());
                OutputStream os = httpExchange.getResponseBody();
                os.write(json.getBytes());
                os.close();



            } catch (Exception e) {
                System.out.println("Peticion a thermal incorrecta.");
                httpExchange.getResponseHeaders().set("Access-Control-Allow-Origin","*");
                httpExchange.sendResponseHeaders(315, '0');
                OutputStream os = httpExchange.getResponseBody();
                os.write("Error parsing input.".getBytes());
                os.close();
            }
        }
    }

    @Override
    public void run() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(LISTEN_PORT), 0);
            server.createContext("/notify", new NotificationHandler());
            server.createContext("/thermal", new PlotHandler());
            server.setExecutor(null);
            server.start();
        } catch (IOException e) {
            logger.log(Level.SEVERE, e.toString(), e);
        }
    }
}

