package org.cerberus.robot.proxy.application;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.annotation.web.configurers.ExceptionHandlingConfigurer;
import org.springframework.http.MediaType;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.AndRequestMatcher;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authentication of every service of the robot-proxy, chosen once at startup with
 * {@code robotproxy.auth.mode}:
 * <ul>
 * <li>{@code none} (default): nothing is authenticated. The relay keeps its historical rule: it is
 * disabled unless {@code relay.token} is set, and then requires it (see RelayController).</li>
 * <li>{@code token}: every route requires {@code Authorization: Bearer <robotproxy.auth.token>}
 * (defaults to {@code relay.token}). The UI has a login page (/login, see TokenLoginController) where the
 * token is typed once; it then works with a session cookie.</li>
 * <li>{@code oauth}: every route requires a Bearer JWT of the Keycloak realm
 * ({@code spring.security.oauth2.resourceserver.jwt.issuer-uri}, audience from
 * {@code ...jwt.audiences}). All or nothing: any valid token gives access to every protected route.
 * If {@code robotproxy.auth.oauth2.ui.client-id} is also set, the UI gets a browser login (authorization
 * code flow, session cookie): unauthenticated browser navigations are redirected to Keycloak, and any
 * logged-in user is accepted. Bearer clients (Cerberus) keep working unchanged.</li>
 * </ul>
 * In token and oauth modes the routes listed in {@code robotproxy.auth.open-paths} stay public (health
 * check, UI pages and assets, API docs). The WebSocket (/chat) is protected: a browser cannot send the
 * Authorization header on it.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger LOG = LogManager.getLogger(SecurityConfig.class);

    static final String MODE_NONE = "none";
    static final String MODE_TOKEN = "token";
    static final String MODE_OAUTH = "oauth";
    static final String REGISTRATION_ID = "keycloak";
    private static final String DEFAULT_OPEN_PATHS = "/check,/,/index.html,/favicon.ico,/css/**,/js/**,/img/**,/webjars/**,/swagger-ui.html,/swagger-ui/**,/v3/api-docs/**";
    /** With the browser login the pages themselves (/ and /index.html) require it: only the assets stay public. */
    private static final String LOGIN_OPEN_PATHS = "/check,/favicon.ico,/css/**,/js/**,/img/**,/webjars/**,/swagger-ui.html,/swagger-ui/**,/v3/api-docs/**";
    /** Token mode: the login page itself is public. */
    private static final String TOKEN_OPEN_PATHS = LOGIN_OPEN_PATHS + ",/login";
    private static final String ISSUER = "spring.security.oauth2.resourceserver.jwt.issuer-uri";
    private static final String AUDIENCES = "spring.security.oauth2.resourceserver.jwt.audiences";

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, Environment environment,
            ObjectProvider<ClientRegistrationRepository> clientRegistrations,
            @Value("${robotproxy.auth.mode:none}") String modeValue,
            @Value("${robotproxy.auth.token:${relay.token:}}") String token) throws Exception {

        String mode = modeValue == null ? MODE_NONE : modeValue.trim().toLowerCase();
        ClientRegistrationRepository clients = clientRegistrations.getIfAvailable();
        boolean browserLogin = MODE_OAUTH.equals(mode) && clients != null;
        boolean sessionLogin = browserLogin || MODE_TOKEN.equals(mode);
        String openPaths = environment.getProperty("robotproxy.auth.open-paths",
                MODE_TOKEN.equals(mode) ? TOKEN_OPEN_PATHS : browserLogin ? LOGIN_OPEN_PATHS : DEFAULT_OPEN_PATHS);
        http.csrf(AbstractHttpConfigurer::disable)
                .headers(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(sessionLogin ? SessionCreationPolicy.IF_REQUIRED : SessionCreationPolicy.STATELESS));

        switch (mode) {
            case MODE_NONE:
                LOG.info("Authentication mode: none (no route is authenticated)");
                http.authorizeHttpRequests(a -> a.anyRequest().permitAll());
                break;
            case MODE_TOKEN:
                if (token == null || token.trim().isEmpty()) {
                    throw new IllegalStateException("robotproxy.auth.mode=token requires robotproxy.auth.token (or relay.token) to be set");
                }
                LOG.info("Authentication mode: token (Bearer token or /login required on every route except {})", openPaths);
                http.addFilterBefore(new StaticTokenFilter(token.trim()), AnonymousAuthenticationFilter.class)
                        .authorizeHttpRequests(a -> {
                            open(a, openPaths);
                            a.anyRequest().authenticated();
                        })
                        .exceptionHandling(e -> entryPoints(e, "/login", "Missing or invalid token"))
                        .logout(l -> l.logoutSuccessUrl("/login"));
                break;
            case MODE_OAUTH:
                String issuer = environment.getProperty(ISSUER);
                if (issuer == null || issuer.trim().isEmpty()) {
                    throw new IllegalStateException("robotproxy.auth.mode=oauth requires " + ISSUER + " to be set");
                }
                List<String> audiences = Binder.get(environment).bind(AUDIENCES, Bindable.listOf(String.class)).orElse(new ArrayList<String>());
                if (audiences.isEmpty()) {
                    LOG.warn("OAuth2 enabled without {}: any token of the issuer is accepted, whatever its audience", AUDIENCES);
                }
                LOG.info("Authentication mode: oauth (issuer: {}, Bearer JWT{} required on every route except {})", issuer,
                        browserLogin ? " or browser login" : "", openPaths);

                http.authorizeHttpRequests(a -> {
                    open(a, openPaths);
                    a.anyRequest().authenticated();
                })
                        .exceptionHandling(e -> entryPoints(e, browserLogin ? "/oauth2/authorization/" + REGISTRATION_ID : null,
                        "Missing or invalid credentials"))
                        .oauth2ResourceServer(o -> o
                        .authenticationEntryPoint((request, response, ex) -> writeError(response, 401, "unauthorized", "Missing or invalid credentials"))
                        .jwt(Customizer.withDefaults()));
                if (browserLogin) {
                    OidcClientInitiatedLogoutSuccessHandler logout = new OidcClientInitiatedLogoutSuccessHandler(clients);
                    logout.setPostLogoutRedirectUri("{baseUrl}/");
                    http.oauth2Login(l -> { })
                            .logout(l -> l.logoutSuccessHandler(logout));
                }
                break;
            default:
                throw new IllegalStateException("Unknown robotproxy.auth.mode '" + modeValue + "': expected none, token or oauth");
        }
        return http.build();
    }

    /**
     * Browser login of the UI: registered only in oauth mode with robotproxy.auth.oauth2.ui.client-id. Without
     * client secret the client is public and PKCE is used. The realm discovery is done on first use, so a
     * Keycloak that is down at startup is not fatal.
     */
    @Bean
    @ConditionalOnExpression("'${robotproxy.auth.mode:none}'.trim().equalsIgnoreCase('oauth') && !'${robotproxy.auth.oauth2.ui.client-id:}'.trim().isEmpty()")
    public ClientRegistrationRepository clientRegistrationRepository(
            @Value("${" + ISSUER + ":}") String issuer,
            @Value("${robotproxy.auth.oauth2.ui.client-id}") String clientId,
            @Value("${robotproxy.auth.oauth2.ui.client-secret:}") String clientSecret) {
        return new LazyClientRegistrationRepository(issuer.trim(), clientId.trim(), clientSecret.trim());
    }

    static final class LazyClientRegistrationRepository implements ClientRegistrationRepository {

        private final String issuer;
        private final String clientId;
        private final String clientSecret;
        private volatile ClientRegistration registration;

        LazyClientRegistrationRepository(String issuer, String clientId, String clientSecret) {
            this.issuer = issuer;
            this.clientId = clientId;
            this.clientSecret = clientSecret;
        }

        @Override
        public ClientRegistration findByRegistrationId(String registrationId) {
            if (!REGISTRATION_ID.equals(registrationId)) {
                return null;
            }
            ClientRegistration result = registration;
            if (result == null) {
                synchronized (this) {
                    if (registration == null) {
                        registration = ClientRegistrations.fromIssuerLocation(issuer)
                                .registrationId(REGISTRATION_ID)
                                .clientId(clientId)
                                .clientSecret(clientSecret)
                                .clientAuthenticationMethod(clientSecret.isEmpty() ? ClientAuthenticationMethod.NONE : ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                                .scope("openid")
                                .build();
                    }
                    result = registration;
                }
            }
            return result;
        }
    }

    /**
     * Without loginUrl every unauthenticated request gets the JSON 401. With it, page navigations
     * (Accept: text/html, no Authorization header) are redirected to it, while fetch/XHR and Bearer clients
     * still get the JSON 401. (An explicit authenticationEntryPoint would make Spring ignore the
     * per-matcher ones.)
     */
    private static void entryPoints(ExceptionHandlingConfigurer<HttpSecurity> e, String loginUrl, String message) {
        AuthenticationEntryPoint unauthorized = (request, response, ex) -> writeError(response, 401, "unauthorized", message);
        if (loginUrl == null) {
            e.authenticationEntryPoint(unauthorized);
            return;
        }
        MediaTypeRequestMatcher html = new MediaTypeRequestMatcher(MediaType.TEXT_HTML);
        html.setIgnoredMediaTypes(Set.of(MediaType.ALL));
        RequestMatcher navigation = new AndRequestMatcher(html,
                new NegatedRequestMatcher(request -> request.getHeader("Authorization") != null));
        e.defaultAuthenticationEntryPointFor(new LoginUrlAuthenticationEntryPoint(loginUrl), navigation);
        e.defaultAuthenticationEntryPointFor(unauthorized, AnyRequestMatcher.INSTANCE);
    }

    /** Constant-time comparison of a supplied token with the expected one. */
    static boolean sameToken(String expected, String supplied) {
        return supplied != null && MessageDigest.isEqual(sha256(supplied.trim()), sha256(expected.trim()));
    }

    static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void open(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry registry, String openPaths) {
        // Spring MVC dispatches controller failures to /error: without this the client would get a 401 instead of the real error.
        List<String> paths = new ArrayList<String>();
        paths.add("/error");
        for (String p : String.valueOf(openPaths).split(",")) {
            if (!p.trim().isEmpty()) {
                paths.add(p.trim());
            }
        }
        if (!paths.isEmpty()) {
            registry.requestMatchers(paths.toArray(new String[0])).permitAll();
        }
    }

    private static void writeError(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        if (status == 401) {
            response.setHeader("WWW-Authenticate", "Bearer");
        }
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.getOutputStream().write(("{\"error\":\"" + message + "\",\"code\":\"" + code + "\"}").getBytes(StandardCharsets.UTF_8));
    }

    /** Authenticates a request carrying "Authorization: Bearer <token>" (constant-time comparison). */
    private static final class StaticTokenFilter extends OncePerRequestFilter {

        private final byte[] tokenDigest;

        StaticTokenFilter(String token) {
            this.tokenDigest = sha256(token);
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            String header = request.getHeader("Authorization");
            if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)
                    && MessageDigest.isEqual(sha256(header.substring(7).trim()), tokenDigest)) {
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(new UsernamePasswordAuthenticationToken("token", null, AuthorityUtils.createAuthorityList("token")));
                SecurityContextHolder.setContext(context);
            }
            chain.doFilter(request, response);
        }
    }
}
