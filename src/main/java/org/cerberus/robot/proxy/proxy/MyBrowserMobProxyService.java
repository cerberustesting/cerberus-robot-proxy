/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.cerberus.robot.proxy.proxy;

import java.io.*;
import java.math.BigInteger;
import java.net.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import net.lightbody.bmp.BrowserMobProxy;
import net.lightbody.bmp.BrowserMobProxyServer;
import net.lightbody.bmp.core.har.Har;
import net.lightbody.bmp.core.har.HarEntry;
import net.lightbody.bmp.core.har.HarLog;
import net.lightbody.bmp.mitm.*;
import net.lightbody.bmp.mitm.manager.ImpersonatingMitmManager;
import net.lightbody.bmp.proxy.CaptureType;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cerberus.robot.proxy.repository.MySessionProxiesRepository;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.system.ApplicationHome;
import org.springframework.stereotype.Service;

/**
 *
 * @author bcivel
 */
@Service
public class MyBrowserMobProxyService {

    private static final Logger LOG = LogManager.getLogger(MyBrowserMobProxyService.class);

    @Autowired
    MySessionProxiesRepository mySessionProxiesRepository;

    private static File keystore;
    private static CertificateAndKeySource keystoreSource;

    /**
     * Start Proxy on specific Port. If port = 0, a random free port will be
     * used
     *
     * @param port
     * @return BrowserMobProxy
     */
    public BrowserMobProxy startProxy(int port, boolean enableCapture) {
        BrowserMobProxy proxy = new BrowserMobProxyServer();

        if (cliArgsPresent(System.getProperty("keystorePath"), System.getProperty("keystorePassword"))) {
            keystore = getKeystore();
            if (keystore != null) {
                keystoreSource = setKeystoreSource(keystore);
                ImpersonatingMitmManager mitmManager = ImpersonatingMitmManager.builder()
                        .rootCertificateSource(keystoreSource)
                        .build();

                proxy.setMitmManager(mitmManager);
            }
        }

        proxy.setTrustAllServers(true);
        if(enableCapture) {
            proxy.enableHarCaptureTypes(EnumSet.allOf(CaptureType.class));
        } else {
            proxy.disableHarCaptureTypes(EnumSet.allOf(CaptureType.class));
        }

        proxy.start(port);
        proxy.newHar();

        return proxy;
    }

    //One global instance of keystore
    private static File getKeystore() {
        if (keystore == null) {
            keystore = new File(System.getProperty("keystorePath"));
            if (keystore.exists()) {
                LOG.info("Keystore found: {}", keystore.getAbsolutePath());
            } else {
                LOG.warn("Keystore not found: '{}'", keystore.getAbsolutePath());
                keystore = null;
            }
        }
        return keystore;
    }

    //One instance of keystoreSource
    private static CertificateAndKeySource setKeystoreSource(File keystore) {
        if (keystoreSource == null) {
            return new KeyStoreFileCertificateSource("PKCS12", keystore,"privateKeyAlias", System.getProperty("keystorePassword"));
        } else {
            return keystoreSource;
        }
    }

    private boolean cliArgsPresent(String keystorePath, String keystorePassword) {
        boolean isKeystorePathPresent = keystorePath != null && !keystorePath.isEmpty();
        boolean isKeystorePasswordPresent = keystorePassword != null && !keystorePassword.isEmpty();
        if (!isKeystorePathPresent) LOG.info("Keystore path not specified, please specify it if necessary.");
        return (isKeystorePathPresent && isKeystorePasswordPresent);
    }

    /**
     * Get Har for a specific Proxy defined with an uuid
     *
     * @param uuid uuid of the Proxy
     * @param requestUrlPattern Request URL to filter
     * @param emptyResponseContentText Boolean that activate the cleaning of the
     * Response Content Text
     * @return
     */
    public Har getHar(String uuid, String requestUrlPattern, boolean emptyResponseContentText) {

        Har response;
        BrowserMobProxy proxy = mySessionProxiesRepository.getMySessionProxies(uuid).getBrowserMobProxy();

        if (!"".equals(requestUrlPattern) || emptyResponseContentText) {
            response = getFilteredHar(proxy.getHar(), requestUrlPattern, emptyResponseContentText);
        } else {
            response = proxy.getHar();
        }

        return response;

    }

    /**
     * Get Filtered Har on specific request URL or with less detail on the
     * response
     *
     * @param har
     * @param requestUrlPattern
     * @param emptyResponseContentText
     * @return
     */
    private Har getFilteredHar(Har har, String requestUrlPattern, boolean emptyResponseContentText) {

        Har response = new Har();

        HarLog harLog = new HarLog();

        for (HarEntry entry : har.getLog().getEntries()) {
            //Filter on specific URL
            if (!"".equals(requestUrlPattern)) {
                if (entry.getRequest() != null) {
                    String requestUrl = entry.getRequest().getUrl();
                    if (requestUrl.contains(requestUrlPattern)) {
                        //Filter Response Content Text
                        if (emptyResponseContentText) {
                            entry = clearResponseContentText(entry);
                        }
                        harLog.addEntry(entry);
                    }
                }
            } else {
                //Filter Response Content Text
                if (emptyResponseContentText) {
                    entry = clearResponseContentText(entry);
                }
                harLog.addEntry(entry);
            }
        }
        response.setLog(harLog);

        return response;
    }

    /**
     * 
     * @param entry
     * @return 
     */
    private HarEntry clearResponseContentText(HarEntry entry) {
        HarEntry newEntry = new HarEntry();
        if (entry.getResponse() != null && entry.getResponse().getContent() != null) {
            newEntry = entry;
            newEntry.getResponse().getContent().setText("");
        }
        return newEntry;
    }

    /**
     * Get Har MD5 to compare files before downloading a new HAR
     * @param uuid
     * @param requestUrlPattern
     * @return 
     */
    public String getHarMD5(String uuid, String requestUrlPattern) {

        try {

            // Static getInstance method is called with hashing MD5 
            MessageDigest md = MessageDigest.getInstance("MD5");

            // digest() method is called to calculate message digest 
            //  of an input digest() return array of byte 
            byte[] messageDigest = md.digest(this.getHar(uuid, requestUrlPattern, true).toString().getBytes());

            // Convert byte array into signum representation 
            BigInteger no = new BigInteger(1, messageDigest);

            // Convert message digest into hex value 
            String hashtext = no.toString(16);
            while (hashtext.length() < 32) {
                hashtext = "0" + hashtext;
            }
            return hashtext;
        } // For specifying wrong message digest algorithms 
        catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }

    }

    /**
     * Clear HAR
     * @param uuid
     */
    public void clearHar(String uuid) {
        BrowserMobProxy proxy = mySessionProxiesRepository.getMySessionProxies(uuid).getBrowserMobProxy();
        proxy.newHar();
    }

    /**
     * Stop Specific proxy
     * @param uuid 
     */
    public void stop(String uuid) {
        BrowserMobProxy proxy = mySessionProxiesRepository.getMySessionProxies(uuid).getBrowserMobProxy();
        if(proxy!=null && proxy.isStarted()) {
            LOG.info("Stopping BrowserMobProxy : '" + uuid + "'");
            proxy.stop();
            LOG.info("BrowserMobProxy : '" + uuid + "' stopped");
        }
    }
    
    /**
     * Stop Specific proxy
     * @param uuid 
     */
    public JSONObject getStats(String uuid) {
        JSONObject response = new JSONObject();
        
        try {
            
            BrowserMobProxy proxy = mySessionProxiesRepository.getMySessionProxies(uuid).getBrowserMobProxy();
            response.put("hits", proxy.getHar().getLog().getEntries().size());
            
        } catch (JSONException ex) {
            LOG.warn(ex);
        }
        return response;
    }

    /**
     * Method extracted from browsermob proxy to generate a default common nome with the hostname and current date if user doesn't specify it
     * @return Default common name
     */
    public String generateDefaultCommonName() {
        String hostname;
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException var4) {
            hostname = "localhost";
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss zzz");
        String currentDateTime = dateFormat.format(new Date());
        String defaultCN = "Generated CA (" + hostname + ") " + currentDateTime;
        return defaultCN.length() <= 64 ? defaultCN : defaultCN.substring(0, 63);
    }

    /**
     * Method to generate a default organization if user doesn't specify it.
     * @return Default organization
     */
    public String generateDefaultOrganization() {
        return "CA dynamically generated by LittleProxy";
    }

    public Map<String, Date> formatDates(String notBeforeDate, String notAfterDate) {
        String notBeforeDecoded;
        String notAfterDecoded;
        Map<String, Date> dates = new HashMap<>();

        try {
            notBeforeDecoded = URLDecoder.decode(notBeforeDate, "UTF-8");
            notAfterDecoded = URLDecoder.decode(notAfterDate, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }

        String partsDateNotBefore = notBeforeDecoded.split("T")[0];
        LocalDate localNotBefore = LocalDate.parse(partsDateNotBefore, DateTimeFormatter.ISO_LOCAL_DATE);

        String partsDateNotAfter = notAfterDecoded.split("T")[0];
        LocalDate localNotAfter = LocalDate.parse(partsDateNotAfter, DateTimeFormatter.ISO_LOCAL_DATE);

        dates.put("notBeforeDate", Date.from(localNotBefore.atStartOfDay().atZone(ZoneId.of("UTC")).toInstant()));
        dates.put("notAfterDate", Date.from(localNotAfter.atStartOfDay().atZone(ZoneId.of("UTC")).toInstant()));

        return dates;
    }

    /**
     * Method to generate a CertificateInfo
     * @param commonName
     * @param organization
     * @param notBeforeDate
     * @param notAfterDate
     * @return CertificateInfo that contains all necessary information for the certificate generation.
     */
    public CertificateInfo generateCertificateInfo(String commonName, String organization, Date notBeforeDate, Date notAfterDate) {
        CertificateInfo certificateInfo = new CertificateInfo();

        //Generate values by default if Common name and Organization are empty.
        if (commonName == null || commonName.isEmpty()) {
            commonName = generateDefaultCommonName();
        }
        if (organization == null || organization.isEmpty()) {
            organization = generateDefaultOrganization();
        }

        certificateInfo.commonName(commonName);
        certificateInfo.organization(organization);
        certificateInfo.notBefore(notBeforeDate);
        certificateInfo.notAfter(notAfterDate);

        return certificateInfo;
    }

    /**
     * Create the different files: certificate, private key and keystore. File are saved on the host
     * @param certificateInfo
     * @param password Password for private key et keystore
     */
    public void createCertificateFiles(CertificateInfo certificateInfo, String password) {
        File jarPath = new ApplicationHome(MyBrowserMobProxyService.class).getDir();
        RootCertificateGenerator rootCertificateGenerator = RootCertificateGenerator.builder().certificateInfo(certificateInfo).build();
        // save the newly-generated Root Certificate and Private Key -- the .cer file can be imported directly into a browser
        rootCertificateGenerator.saveRootCertificateAsPemFile(new File(String.format("%s%s%s", jarPath, File.separator, "ca-certificate-rsa.cer")));
        rootCertificateGenerator.savePrivateKeyAsPemFile(new File(String.format("%s%s%s", jarPath, File.separator, "ca-key-rsa.pem")), password);
        rootCertificateGenerator.saveRootCertificateAndKey("PKCS12", new File(String.format("%s%s%s", jarPath, File.separator, "ca-keystore-rsa.p12")),
                "privateKeyAlias", password);
    }

    /**
     * Create a ZIP file which contains certificate, private key and keystore in order to send it to the client.
     * @return ByteArrayOutputStream
     */
    public ByteArrayOutputStream createZip() {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(byteArrayOutputStream);
        ZipOutputStream zipOutputStream = new ZipOutputStream(bufferedOutputStream);

        try {
            addFilesToArchive(zipOutputStream);
            IOUtils.close(bufferedOutputStream);
            IOUtils.close(byteArrayOutputStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return byteArrayOutputStream;

    }

    private void addFilesToArchive(ZipOutputStream zipOutputStream) throws IOException {
        List<String> filesNames = new ArrayList<>();
        File jarPath = new ApplicationHome(MyBrowserMobProxyService.class).getDir();
        filesNames.add(String.format("%s%s%s", jarPath, File.separator, "ca-certificate-rsa.cer"));
        filesNames.add(String.format("%s%s%s", jarPath, File.separator, "ca-key-rsa.pem"));
        filesNames.add(String.format("%s%s%s", jarPath, File.separator, "ca-keystore-rsa.p12"));

        for (String fileName : filesNames) {
            File file = new File(fileName);
            zipOutputStream.putNextEntry(new ZipEntry(file.getName()));
            FileInputStream fileInputStream = new FileInputStream(file);

            IOUtils.copy(fileInputStream, zipOutputStream);

            fileInputStream.close();
            zipOutputStream.closeEntry();
        }

        zipOutputStream.finish();
        zipOutputStream.flush();
        IOUtils.close(zipOutputStream);
    }
}
