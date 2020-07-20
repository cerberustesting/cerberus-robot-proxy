package org.cerberus.proxy;

import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import net.lightbody.bmp.BrowserMobProxy;
import net.lightbody.bmp.core.har.Har;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMethod;

@RestController
public class MyProxyController {

    private static final Logger LOG = LogManager.getLogger(MyProxyController.class);

    @Autowired
    MyProxy myProxy;
    @Autowired
    MyProxyService myProxyService;

    /**
     * Check server is Up
     *
     * @param port
     * @return
     */
    @ApiOperation(value = "Check if cerberus-executor is up")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "cerberus-executor is up")
    }
    )
    @RequestMapping(value = "/check", method = RequestMethod.GET)
    public String check() {
        return "{\"message\":\"OK\"}";
    }

    /**
     * Start Proxy
     *
     * @param port Port to use for the proxy. Default value is 0, meaning port
     * will be randomly determined
     * @return String in Json format containing port and uuid
     */
    @ApiOperation(value = "Start a proxy on specific port, or on random port if port information is not provided")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "proxy has been created, the port is provided")
    }
    )
    @RequestMapping(value = "/startProxy", method = RequestMethod.GET)
    public String startProxy(@RequestParam(value = "port", defaultValue = "0") int port,
            @RequestParam(value = "timeout", defaultValue = "${proxy.defaulttimeout}") int timeout) {

        String response;
        UUID uuid = UUID.randomUUID();

        //Start proxy on specific port (or random)
        LOG.info("Start Proxy '" + uuid + "'");
        BrowserMobProxy proxy = myProxyService.startProxy(port);

        //Calculate the max date for the proxy to be alive
        Date now = new Date();
        Calendar c = Calendar.getInstance();
        c.setTime(now);
        c.add(Calendar.MILLISECOND, timeout);
        Date maxDateUp = c.getTime();

        //Get port 
        port = proxy.getPort();
        LOG.info("Proxy '" + uuid + "' started on port :" + port + " until :" + maxDateUp);

        //Add Started proxy to the list
        myProxy.addProxy(uuid.toString(), proxy, maxDateUp);

        response = "{\"port\":" + port + ",\"uuid\" : \"" + uuid + "\",\"maxDateUp\" : \"" + maxDateUp + "\"}";

        return response;
    }

    /**
     * Get Har
     *
     * @param uuid
     * @param requestUrl
     * @return
     * @throws IOException
     */
    @ApiOperation(value = "Get HAR file knowing a specific proxy uuid")
    @RequestMapping(value = "/getHar", method = RequestMethod.GET)
    public String getHar(
            @RequestParam(value = "uuid", defaultValue = "") String uuid,
            @RequestParam(value = "requestUrl", defaultValue = "") String requestUrl,
            @RequestParam(value = "emptyResponseContentText", required = false, defaultValue = "false") boolean emptyResponseContentText) throws IOException {

        String response = "";
        LOG.info("Get Har for Proxy : '" + uuid + "'");

        Har har = myProxyService.getHar(uuid, requestUrl, emptyResponseContentText);

        LOG.info("Har for proxy '" + uuid + "' generated");

        try ( StringWriter stringwriter = new StringWriter()) {
            har.writeTo(stringwriter);
            response = stringwriter.toString();
        } catch (Exception ex) {
            LOG.warn("Error generating har for proxy '" + uuid + "' : " + ex);
        }

        return response;
    }

    @RequestMapping(value = "/getHarMD5", method = RequestMethod.GET)
    public String getHarMD5(@RequestParam(value = "uuid", defaultValue = "") String uuid,
            @RequestParam(value = "requestUrl", defaultValue = "") String requestUrl) throws IOException {

        String response;
        LOG.info("Get Har MD5 for Proxy '" + uuid + "'");

        response = myProxyService.getHarMD5(uuid, requestUrl);

        LOG.info("Har MD5 for proxy '" + uuid + "' : " + response);

        return response;
    }

    @RequestMapping(value = "/clearHar", method = RequestMethod.GET)
    public String clearHar(@RequestParam(value = "uuid", defaultValue = "") String uuid) throws IOException {

        String response = "";

        BrowserMobProxy proxy = myProxy.getProxy(uuid);

        proxy.newHar();
        response = "Har cleared, new Har generated";

        return response;
    }

    @RequestMapping(value = "/getProxyList", method = RequestMethod.GET)
    public String getProxyList(HttpServletRequest request) {

        String response = "";
        JSONArray ja = new JSONArray();
        HashMap<String, BrowserMobProxy> proxyList = myProxy.getProxyList();
        try {
            for (Map.Entry<String, BrowserMobProxy> item : proxyList.entrySet()) {
                JSONObject jo = new JSONObject();
                String key = item.getKey();
                BrowserMobProxy proxy = item.getValue();

                jo.put("uuid", key);
                jo.put("port", proxy.getPort());
                ja.put(jo);
            }
        } catch (JSONException ex) {
            java.util.logging.Logger.getLogger(MyProxyController.class.getName()).log(Level.SEVERE, null, ex);
        }

        return ja.toString();
    }

    @RequestMapping(value = "/stopProxy", method = RequestMethod.GET)
    public String stopProxy(@RequestParam(value = "uuid", defaultValue = "") String uuid) {

        String response = "";
        LOG.info("Stop Proxy : '" + uuid + "'");
        myProxyService.stopProxy(uuid);

        response = "{\"message\":\"Proxy successfully stopped\",\"uuid\" : \"" + uuid + "\"}";

        return response;
    }

    @RequestMapping(value = "/getStats", method = RequestMethod.GET)
    public String getStats(@RequestParam(value = "uuid", defaultValue = "") String uuid) throws IOException {

        String response = "";

        JSONObject hits = myProxyService.getStats(uuid);
        response = hits.toString();

        return response;
    }
}
