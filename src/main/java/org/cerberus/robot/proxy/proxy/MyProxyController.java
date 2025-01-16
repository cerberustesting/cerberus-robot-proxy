package org.cerberus.robot.proxy.proxy;

import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

import java.io.*;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import javax.servlet.http.HttpServletRequest;

import net.lightbody.bmp.mitm.CertificateInfo;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import net.lightbody.bmp.core.har.Har;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cerberus.robot.proxy.version.Infos;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;

@RestController
public class MyProxyController {

    private static final Logger LOG = LogManager.getLogger(MyProxyController.class);

    @Autowired
    MySessionProxiesService mySessionProxiesService;
    @Autowired
    MyBrowserMobProxyService myBrowserMobProxyService;

    /**
     * Check server is Up
     *
     * @return
     */
    @ApiOperation(value = "Check if cerberus-robot-proxy is up")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "cerberus-robot-proxy is up")
    }
    )
    @RequestMapping(value = "/check", method = RequestMethod.GET)
    public String check() {
        return "{\"message\":\"OK\"}";
    }

    /**
     * Get Proxy information
     *
     * @return
     */
    @ApiOperation(value = "Get cerberus-robot-proxy various informations")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "cerberus-robot-proxy information provided")
    }
    )
    @RequestMapping(value = "/management", method = RequestMethod.GET)
    public String management() {

        JSONObject actionResult = new JSONObject();
        Infos infos = new Infos();

        try {
            actionResult.put("status", "OK");
            actionResult.put("version", infos.getProjectNameAndVersion());
            actionResult.put("buildid", infos.getProjectBuildId());
            actionResult.put("java.prop-log4j.logger", System.getProperty("log4j.logger"));
            actionResult.put("java.prop-java.io.tmpdir", System.getProperty("java.io.tmpdir"));
            actionResult.put("java.prop-authorisedFolderScope", System.getProperty("authorisedFolderScope"));

            actionResult.put("javaVersion", System.getProperty("java.version"));
            Runtime instance = Runtime.getRuntime();
            int mb = 1024 * 1024;
            actionResult.put("javaFreeMemory", instance.freeMemory() / mb);
            actionResult.put("javaTotalMemory", instance.totalMemory() / mb);
            actionResult.put("javaUsedMemory", (instance.totalMemory() - instance.freeMemory()) / mb);
            actionResult.put("javaMaxMemory", instance.maxMemory() / mb);

        } catch (JSONException ex) {
            java.util.logging.Logger.getLogger(MyProxyController.class.getName()).log(Level.SEVERE, null, ex);
        }

        return actionResult.toString();
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
    public String start(@RequestParam(value = "port", defaultValue = "0") int port,
            @RequestParam(value = "timeout", defaultValue = "${proxy.defaulttimeout}") int timeout,
            @RequestParam(value = "enableCapture", defaultValue = "${proxy.defaultenablecapture}") boolean enableCapture,
            @RequestParam(value = "bsLocalProxyActive", defaultValue = "${proxy.defaultlocalproxyactive}") boolean bsLocalProxyActive,
            @RequestParam(value = "bsKey", defaultValue = "") String bsKey,
            @RequestParam(value = "bsLocalIdentifier", defaultValue = "") String bsLocalIdentifier,
            @RequestParam(value = "bsLocalProxyHost", defaultValue = "") String bsLocalProxyHost) {

        String response;

        if (bsLocalProxyActive && (bsKey.equals("") || bsLocalIdentifier.equals("") || bsLocalProxyHost.equals(""))) {
            StringBuilder sb = new StringBuilder();
            sb.append("{\"status\":\"Error\",");
            sb.append("\"message\":\"bsLocalProxyActive equals to true, so parameters bsKey, bsLocalIdentifier and bsLocalProxyHost cannot be empty\",");
            sb.append("\"bsKey\":\"" + bsKey + "\",");
            sb.append("\"bsLocalIdentifier\":\"" + bsLocalIdentifier + "\",");
            sb.append("\"bsLocalProxyHost\":\"" + bsLocalProxyHost + "\"}");

            return sb.toString();
        }

        MySessionProxies msp = mySessionProxiesService.start(port, timeout, enableCapture, bsLocalProxyActive, bsKey, bsLocalIdentifier, bsLocalProxyHost);

        StringBuilder sb = new StringBuilder();
        sb.append("{\"status\":\"Success\",");
        sb.append("\"message\":\"Successfully started proxy\",");
        sb.append("\"port\":" + msp.getPort() + ",");
        sb.append("\"timeout\":" + timeout + ",");
        sb.append("\"enableCapture\":" + enableCapture + ",");
        sb.append("\"uuid\":\"" + msp.getUuid() + "\",");
        sb.append("\"maxDateUp\":\"" + msp.getEndDateMessage() + "\",");
        sb.append("\"bsLocalProxyActive\":\"" + bsLocalProxyActive + "\",");
        sb.append("\"bsKey\":\"" + bsKey + "\",");
        sb.append("\"bsLocalIdentifier\":\"" + bsLocalIdentifier + "\",");
        sb.append("\"bsLocalProxyHost\":\"" + bsLocalProxyHost + "\"}");

        response = sb.toString();

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

        Har har = myBrowserMobProxyService.getHar(uuid, requestUrl, emptyResponseContentText);

        LOG.info("Har for proxy '" + uuid + "' generated");

        try (StringWriter stringwriter = new StringWriter()) {
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

        response = myBrowserMobProxyService.getHarMD5(uuid, requestUrl);

        LOG.info("Har MD5 for proxy '" + uuid + "' : " + response);

        return response;
    }

    @RequestMapping(value = "/clearHar", method = RequestMethod.GET)
    public String clearHar(@RequestParam(value = "uuid", defaultValue = "") String uuid) throws IOException {

        String response = "";

        myBrowserMobProxyService.clearHar(uuid);

        response = "Har cleared, new Har generated";

        return response;
    }

    @RequestMapping(value = "/getProxyList", method = RequestMethod.GET)
    public String getProxyList(HttpServletRequest request) {

        String response = "";
        JSONArray ja = new JSONArray();
        List<MySessionProxies> mspList = mySessionProxiesService.mySessionProxiesList();

        try {
            for (MySessionProxies msp : mspList) {
                JSONObject jo = new JSONObject();

                jo.put("uuid", msp.getUuid().toString());
                jo.put("port", msp.getPort());
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
        mySessionProxiesService.stop(uuid);

        response = "{\"message\":\"Proxy successfully stopped\",\"uuid\" : \"" + uuid + "\"}";

        return response;
    }

    @RequestMapping(value = "/getStats", method = RequestMethod.GET)
    public String getStats(@RequestParam(value = "uuid", defaultValue = "") String uuid) throws IOException {

        String response = "";

        JSONObject hits = myBrowserMobProxyService.getStats(uuid);
        response = hits.toString();

        return response;
    }

    @PostMapping(value = "/certs/generate", consumes = MediaType.APPLICATION_JSON_VALUE, produces = "application/zip")
    public ResponseEntity<byte[]> generateCerts(
            @RequestBody Map<String, String> body
    ) {
        if ((body.get("notBeforeDate") == null || body.get("notBeforeDate").isEmpty())
                || (body.get("notAfterDate") == null || body.get("notAfterDate").isEmpty())
                || (body.get("password") == null || body.get("password").isEmpty())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        String commonName = body.get("commonName");
        String organization = body.get("organization");
        Map<String, Date> dates = myBrowserMobProxyService.formatDates(body.get("notBeforeDate"), body.get("notAfterDate"));
        String password = body.get("password");

        CertificateInfo certificateInfo = myBrowserMobProxyService.generateCertificateInfo(commonName, organization, dates.get("notBeforeDate"), dates.get("notAfterDate"));

        myBrowserMobProxyService.createCertificateFiles(certificateInfo, password);

        ByteArrayOutputStream byteArrayOutputStream = myBrowserMobProxyService.createZip();

        return ResponseEntity
                .ok()
                .header("Content-Disposition", "attachment; filename=\"certificate-files.zip\"")
                .body(byteArrayOutputStream.toByteArray());
    }
}
