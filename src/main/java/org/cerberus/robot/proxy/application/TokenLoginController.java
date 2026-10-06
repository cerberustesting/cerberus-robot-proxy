package org.cerberus.robot.proxy.application;

import java.nio.charset.StandardCharsets;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login page of the UI in robotproxy.auth.mode=token: the token is typed once, then a session cookie
 * carries it, so the UI (fetch calls and /chat WebSocket, both same-origin) works without sending any header.
 * Bearer clients are not concerned: they keep sending the token on every call.
 */
@RestController
@ConditionalOnExpression("'${robotproxy.auth.mode:none}'.trim().equalsIgnoreCase('token')")
public class TokenLoginController {

    private final String token;
    private final HttpSessionSecurityContextRepository repository = new HttpSessionSecurityContextRepository();

    public TokenLoginController(@Value("${robotproxy.auth.token:${relay.token:}}") String token) {
        this.token = token == null ? "" : token.trim();
    }

    @GetMapping("/login")
    public ResponseEntity<byte[]> page() {
        return html(HttpStatus.OK, false);
    }

    @PostMapping("/login")
    public ResponseEntity<byte[]> login(@RequestParam(value = "token", defaultValue = "") String supplied,
            HttpServletRequest request, HttpServletResponse response) {
        if (token.isEmpty() || !SecurityConfig.sameToken(token, supplied)) {
            return html(HttpStatus.UNAUTHORIZED, true);
        }
        if (request.getSession(false) != null) {
            request.changeSessionId(); // no session fixation
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken("token", null, AuthorityUtils.createAuthorityList("token")));
        repository.saveContext(context, request, response);
        return ResponseEntity.status(HttpStatus.FOUND).header("Location", "/").header("Cache-Control", "no-store").build();
    }

    private static ResponseEntity<byte[]> html(HttpStatus status, boolean error) {
        String page = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><title>Cerberus Robot Proxy</title>"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">"
                + "<style>body{font-family:system-ui,sans-serif;background:#f3f4f6;display:flex;justify-content:center;padding-top:15vh}"
                + "form{background:#fff;padding:2rem;border-radius:.5rem;box-shadow:0 1px 4px rgba(0,0,0,.2);width:22rem}"
                + "h1{font-size:1.1rem;margin:0 0 1rem}input{width:100%;box-sizing:border-box;padding:.5rem;margin-bottom:1rem}"
                + "button{width:100%;padding:.5rem;background:#1f2937;color:#fff;border:0;border-radius:.25rem;cursor:pointer}"
                + ".error{color:#b91c1c;margin-bottom:1rem}</style></head><body>"
                + "<form method=\"post\" action=\"/login\"><h1>Cerberus Robot Proxy</h1>"
                + (error ? "<div class=\"error\">Invalid token</div>" : "")
                + "<input type=\"password\" name=\"token\" placeholder=\"Access token\" autofocus autocomplete=\"off\">"
                + "<button type=\"submit\">Sign in</button></form></body></html>";
        return ResponseEntity.status(status)
                .contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
                .header("Cache-Control", "no-store")
                .body(page.getBytes(StandardCharsets.UTF_8));
    }
}
