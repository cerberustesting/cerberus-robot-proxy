package org.cerberus.robot.proxy.relay;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP relay: Cerberus core sends an already built HTTP request, it is executed from this
 * machine and the raw outcome is returned. Every route requires "Authorization: Bearer
 * relay.token"; without a configured token the relay is disabled (503 relay_disabled).
 */
@RestController
public class RelayController {

    private static final Logger LOG = LogManager.getLogger(RelayController.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RelayService relayService;

    @Autowired
    public RelayController(RelayService relayService) {
        this.relayService = relayService;
    }

    @RequestMapping(value = "/relay/check", method = RequestMethod.GET)
    public ResponseEntity<byte[]> check(@RequestHeader(value = "Authorization", required = false) String authorization) {
        try {
            authorize(authorization);
            ObjectNode out = MAPPER.createObjectNode();
            out.put("ok", true);
            out.put("version", RelayService.RELAY_VERSION);
            return json(HttpStatus.OK.value(), out);
        } catch (RelayException e) {
            return error(e);
        }
    }

    @RequestMapping(value = "/relay", method = RequestMethod.POST)
    public ResponseEntity<byte[]> relay(@RequestHeader(value = "Authorization", required = false) String authorization,
            HttpServletRequest request) {
        try {
            authorize(authorization);
            return json(HttpStatus.OK.value(), relayService.relay(request.getInputStream(), request.getContentLengthLong()));
        } catch (RelayException e) {
            return error(e);
        } catch (Exception e) {
            LOG.error("Unexpected relay failure: " + e.getClass().getName(), e);
            return error(new RelayException(500, "internal_error", "Unexpected relay failure"));
        }
    }

    private void authorize(String authorization) throws RelayException {
        if (!relayService.isEnabled()) {
            throw new RelayException(503, "relay_disabled", "The relay is disabled: relay.token is not configured");
        }
        if (!relayService.isAuthorized(authorization)) {
            throw new RelayException(401, "unauthorized", "Missing or invalid relay token");
        }
    }

    private ResponseEntity<byte[]> error(RelayException e) {
        ObjectNode out = MAPPER.createObjectNode();
        out.put("error", e.getMessage());
        out.put("code", e.getCode());
        return json(e.getStatus(), out);
    }

    private ResponseEntity<byte[]> json(int status, ObjectNode body) {
        byte[] bytes;
        try {
            bytes = MAPPER.writeValueAsString(body).getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return ResponseEntity.status(status)
                .contentType(new MediaType("application", "json", StandardCharsets.UTF_8))
                .header("Cache-Control", "no-store")
                .body(bytes);
    }
}
