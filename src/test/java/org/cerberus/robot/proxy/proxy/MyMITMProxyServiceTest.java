package org.cerberus.robot.proxy.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.UUID;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Starting mitmdump: the start must fail at once, with the reason, when the process dies, and the calls
 * to its API must explain themselves when it is gone. A fake "mitmdump" script stands in for the real one.
 */
class MyMITMProxyServiceTest {

    @TempDir
    Path dir;

    private Process started;

    @AfterEach
    void stop() {
        if (started != null) {
            started.destroyForcibly();
        }
    }

    private MyMITMProxyService service(String script, long startTimeoutMs) throws IOException {
        assumeTrue(new File("/bin/sh").canExecute(), "needs /bin/sh");
        Path file = dir.resolve("fake-mitmdump");
        Files.writeString(file, "#!/bin/sh\n" + script + "\n");
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rwxr-xr-x"));
        MyMITMProxyService service = new MyMITMProxyService();
        ReflectionTestUtils.setField(service, "mitmCommand", file.toString());
        ReflectionTestUtils.setField(service, "startTimeoutMs", startTimeoutMs);
        ReflectionTestUtils.setField(service, "trafficLogDir", "");
        return service;
    }

    private static boolean python3Available() {
        try {
            return new ProcessBuilder("python3", "-c", "pass").start().waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    void startFailsAtOnceWhenMitmdumpDies() throws Exception {
        MyMITMProxyService service = service("echo 'Error starting proxy server: Address already in use'; exit 3", 10000);
        long begin = System.nanoTime();
        ProxyStartException e = assertThrows(ProxyStartException.class, () -> service.startProxy(8888, true, UUID.randomUUID()));
        assertTrue((System.nanoTime() - begin) / 1_000_000 < 5000, "must not wait for the whole timeout");
        assertTrue(e.getMessage().contains("exited with code 3"), e.getMessage());
        assertTrue(e.getMessage().contains("Address already in use"), "the engine's own output explains why: " + e.getMessage());
    }

    @Test
    void startFailsWithAHintWhenMitmdumpIsNotFound() throws Exception {
        MyMITMProxyService service = service("exit 0", 1000);
        ReflectionTestUtils.setField(service, "mitmCommand", dir.resolve("does-not-exist").toString());
        ProxyStartException e = assertThrows(ProxyStartException.class, () -> service.startProxy(8888, true, UUID.randomUUID()));
        assertTrue(e.getMessage().contains("mitmproxy.command"), e.getMessage());
    }

    @Test
    void startReturnsOnceTheApiAnswers() throws Exception {
        assumeTrue(python3Available(), "needs python3 to play the API of mitmdump");
        MyMITMProxyService service = service(
                "for a in \"$@\"; do case \"$a\" in api_port=*) P=\"${a#api_port=}\";; esac; done\n"
                + "exec python3 -c \"import socket,sys,time; s=socket.socket(); s.setsockopt(socket.SOL_SOCKET,socket.SO_REUSEADDR,1); "
                + "s.bind(('127.0.0.1',int(sys.argv[1]))); s.listen(5); time.sleep(30)\" \"$P\"", 10000);
        MyMITMProxyService.MitmProxyHandle handle = service.startProxy(8888, true, UUID.randomUUID());
        started = handle.process;
        assertTrue(handle.process.isAlive());
        assertTrue(handle.apiPort > 0);
    }

    @Test
    void aSlowEngineIsNotAFailure() throws Exception {
        MyMITMProxyService service = service("sleep 30", 800);
        MyMITMProxyService.MitmProxyHandle handle = service.startProxy(8888, true, UUID.randomUUID());
        started = handle.process;
        assertTrue(handle.process.isAlive(), "alive but not listening: warned about, not failed");
    }

    @Test
    void anApiFailureNamesTheDeadProcess() throws Exception {
        MyMITMProxyService service = service("echo 'addon error: boom'; exit 7", 10000);
        MySessionProxies msp = new MySessionProxies();
        msp.setUuid(UUID.randomUUID());
        Process dead = new ProcessBuilder("/bin/sh", "-c", "exit 7").start();
        dead.waitFor();
        MyMITMProxyService.RecentOutput output = new MyMITMProxyService.RecentOutput();
        output.add("addon error: boom");
        msp.setMitmProcess(dead);
        msp.setMitmApiPort(1); // nothing listens: Connection refused, as in the field
        msp.setMitmOutput(output);

        String why = service.explainApiFailure(msp, new java.net.ConnectException("Connection refused"));
        assertTrue(why.contains("no longer running") && why.contains("exit code 7"), why);
        assertTrue(why.contains("addon error: boom"), why);

        // The calls themselves keep their contract (an empty answer) and no longer throw nor flood.
        JSONObject har = service.getHar(msp, null, false);
        assertEquals(0, har.length());
        assertEquals(0, service.getStats(msp).length());
        assertFalse(why.contains("\tat "), "no stack trace in the explanation");
    }
}
