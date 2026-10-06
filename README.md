# cerberus-robot-proxy

This project can be used from Cerberus (https://github.com/cerberustesting/cerberus-core) v 4.2 
This project allow to activate some extra features like a proxy server.

Requirements: Java 17 or later (built for Java 17, runs on Java 21), Spring Boot 3.5.x.

You can start the robot proxy with:

```
java -jar cerberus-robot-proxy.jar
```

In case you want to start robot proxy on a different port you can overwrite it by creating a new file from https://github.com/cerberustesting/cerberus-robot-proxyblob/master/src/main/resources/application.properties

and then start the robot proxy with :

```
java -jar cerberus-robot-proxy.jar --spring.config.location=classpath:application.properties,/Users/Documents/cerberusrobotproxy_local.properties
```

# API 

## Start a Proxy

Start a proxy using the API `http://localhost:8093/startProxy` 
- Parameters : 
  - `port` : The port of the proxy to start (make sure it's not already in used). If port is empty or equals to 0, a random port will be defined.
  - `timeout` : Timeout in ms. Default value is 3600000 (1 H)
  - `enableCapture` : Boolean that define if MITM proxy capture element or not. Default value is true (set into application.properties proxy.defaultenablecapture)
  - `bsLocalProxyActive` : Boolean that define if BrowserStack local proxy is active. Default value is false (set into application.properties proxy.defaultlocalproxyactive). If set to true, `bsKey`, `bsLocalIdentifier` and `bsLocalProxyHost` cannot be empty.
  - `bsKey` : BrowserStack key used by browserstack local proxy
  - `bsLocalIdentifier` : BrowserStack local identifier to link local proxy session with BrowserStack execution
  - `bsLocalProxyHost` : Proxy Host

Example : `http://localhost:8093/startProxy?port=<port>&timeout=100000&enableCapture=true.....`

You will get a `uuid` in the body response

## Stop a Proxy

To stop a proxy, you need its `<uuid>`

`http://localhost:8093//stopProxy?uuid=<uuid>`

## Get content (HAR)

To get the current content of network activity (`.har` file) for a proxy, you need its `<uuid>`

`http://localhost:8093//getHar?uuid=<uuid>`


## Relay

The relay lets Cerberus Core have an HTTP request executed **from the machine (and network) where the robot-proxy runs**, for APIs that Core cannot reach directly. Core sends a fully built request to `POST /relay`; the robot-proxy sends it and returns the raw outcome (status, headers, body).

### Activation

The relay is **disabled by default**: while `relay.token` is empty, every relay route answers `503 relay_disabled`. Enable it with a token:

```
java -jar cerberus-robot-proxy.jar --relay.token=<long-random-secret>
# or: RELAY_TOKEN=<long-random-secret>
```

| Property | Default | Description |
|---|---|---|
| `relay.token` | *(empty = relay disabled)* | Bearer token required on both routes |
| `relay.allowed-hosts` | *(empty = any host)* | Comma-separated host patterns with `*` (e.g. `*.corp.example.com,api.example.com`), checked on the initial URL and on every redirect |
| `relay.blocked-local-ports` | *(empty)* | Comma-separated extra ports that cannot be reached on loopback (`127.x`, `::1`, `0.0.0.0`, `::`). `server.port` is always blocked; other local APIs stay reachable |
| `relay.max-response-bytes` | `10485760` | Response body cap (10 MiB); beyond it the body is cut and `truncated` is `true` |
| `relay.max-request-bytes` | `20971520` | Max size of the JSON relay request (20 MiB), otherwise `413` |
| `relay.max-in-flight` | `50` | Max simultaneous relayed calls, otherwise `429` |

### Contract

Both routes require `Authorization: Bearer <relay.token>`, or the credentials of the configured [authentication mode](#authentication) (`GET /check` is the unrelated, existing health route).

`GET /relay/check` -> `200 {"ok":true,"version":1}`

`POST /relay`

```json
{
  "method": "POST",
  "url": "https://api.internal/orders",
  "headers": {"Content-Type": "application/json", "X-Multi": ["a", "b"]},
  "bodyBase64": "eyJhIjoxfQ==",
  "followRedirects": true,
  "timeoutMs": 60000,
  "acceptUnsignedSsl": false
}
```

`method` is one of GET/POST/PUT/PATCH/DELETE/HEAD/OPTIONS, `url` is http or https. Optional fields default to: `followRedirects` true (max 10 hops, followed by the relay), `timeoutMs` 60000 (bounded 1000..600000, covers connection, reading and redirects), `acceptUnsignedSsl` false (when true, certificate and hostname checks are skipped for that call only).

Answer, whatever the status returned by the **target** (4xx/5xx included):

```json
{
  "status": 200, "statusText": "OK",
  "headers": [["Content-Type", "application/json"], ["Set-Cookie", "a=1"], ["Set-Cookie", "b=2"]],
  "bodyBase64": "...", "truncated": false, "durationMs": 42, "finalUrl": "https://api.internal/orders"
}
```

Duplicate headers are preserved, gzip/deflate bodies are decompressed (`Content-Encoding`/`Content-Length` removed) and hop-by-hop headers are dropped.

Errors of the relay itself are `{"error": "...", "code": "..."}`: `400 invalid_request`, `401 unauthorized`, `403 target_blocked` (host not allowed, protected local port, redirect to a non-http(s) protocol), `413 request_too_large`, `429 too_many_requests`, `502 connect_failed` (connection, DNS or TLS failure), `503 relay_disabled`, `504 timeout`.

### Security warning

An enabled relay is, by design, an HTTP proxy into the network of this machine. If it is reachable from outside (tunnel, reverse proxy, public IP):

- use a **long random token** and **https only** (the token travels in a header);
- set `relay.allowed-hosts` to the hosts that Cerberus actually needs;
- the relay shares the port of the robot-proxy API: with `robotproxy.auth.mode=none` its other routes are not authenticated, so either use the `token` or `oauth` [authentication mode](#authentication), or expose only `/relay` and `/relay/check` through the tunnel/proxy.

The relay never logs query strings, headers or the token (only method, origin, path, status and duration).


## Authentication

One setting, read at startup, applies to **all** the services (relay, proxy management API, WebSocket): `robotproxy.auth.mode`.

| Mode | Behaviour |
|---|---|
| `none` (default) | Nothing is authenticated. The relay keeps its historical rule: disabled (`503`) unless `relay.token` is set, and then it requires it |
| `token` | Every route requires `Authorization: Bearer <robotproxy.auth.token>` (shared secret). If `robotproxy.auth.token` is empty, `relay.token` is used, so an existing setup only has to switch the mode. The UI shows a **login page** (`/login`) where the token is typed once |
| `oauth` | Every route requires a Bearer JWT issued by Keycloak (machine-to-machine, `client_credentials` grant), or, if `robotproxy.auth.oauth2.ui.client-id` is set, a browser login session for the UI. `relay.token` is ignored |

A wrong setup stops the startup: unknown mode, `token` without any token, `oauth` without issuer.

| Property | Default | Description |
|---|---|---|
| `robotproxy.auth.mode` | `none` | `none`, `token` or `oauth` |
| `robotproxy.auth.token` | `${relay.token}` | Shared secret of the `token` mode |
| `robotproxy.auth.open-paths` | `/check,/,/index.html,/favicon.ico,/css/**,/js/**,/img/**,/webjars/**,/swagger-ui.html,/swagger-ui/**,/v3/api-docs/**` (without `/` and `/index.html` when a UI login exists: `token` mode, or `oauth` with `ui.client-id`; `/login` is added in `token` mode) | Routes that stay public in `token` and `oauth` modes (health check, UI pages and assets, API docs). `/error` is always public |
| `spring.security.oauth2.resourceserver.jwt.issuer-uri` | | `oauth`: Keycloak realm URL. Signing keys are discovered lazily, so a Keycloak that is down at startup is not fatal |
| `spring.security.oauth2.resourceserver.jwt.audiences` | | `oauth`: required `aud` of the tokens. **Set it**, otherwise any token of the realm is accepted (a warning is logged) |
| `robotproxy.auth.oauth2.ui.client-id` | | `oauth`: Keycloak client used for the **browser login of the UI**. Empty = no browser login |
| `robotproxy.auth.oauth2.ui.client-secret` | | Secret of that client. Empty = public client (authorization code + PKCE) |

```
java -jar cerberus-robot-proxy.jar --robotproxy.auth.mode=token --robotproxy.auth.token=<long-random-secret>

java -jar cerberus-robot-proxy.jar --robotproxy.auth.mode=oauth \
  --spring.security.oauth2.resourceserver.jwt.issuer-uri=https://keycloak.example.com/realms/cerberus \
  --spring.security.oauth2.resourceserver.jwt.audiences=cerberus-robot-proxy
```

Authentication errors are `401 {"error":"...","code":"unauthorized"}`. It is all or nothing: there are no roles or scopes, a valid token (or a logged-in user) gives access to every protected route.

**The built-in UI**: in `token` mode, opening it redirects to `/login` (type the token, a session cookie is created; `/logout` ends it); in `oauth` mode, with `robotproxy.auth.oauth2.ui.client-id`, it redirects to the Keycloak login (see below). The `/chat` WebSocket and the API calls of the UI work through that session. Bearer clients (Cerberus) are unaffected.

### OAuth2 with Keycloak (machine to machine)

Nobody logs in: Cerberus is a service that authenticates itself with its own credentials and calls the robot-proxy with the resulting token.

1. Cerberus calls the token endpoint of the realm with the `client_credentials` grant (its `client_id` and `client_secret`) and gets a short-lived access token (JWT).
2. Cerberus sends it on every call: `Authorization: Bearer <access_token>`, and asks for a new one shortly before it expires.
3. The robot-proxy never calls Keycloak per request: it verifies the signature with the realm public keys (cached), then `iss`, `exp` and `aud`.

Keycloak setup (same realm as Cerberus), no role needed: a valid token gives access to everything.

1. A client for Cerberus (`cerberus`, *Client authentication* On, *Service accounts roles* On, *Standard flow* Off; the secret is in *Credentials*).
2. An **Audience** mapper on it (*Client scopes* -> `cerberus-dedicated` -> *Add mapper* -> *By configuration* -> Audience, *Included Client Audience* `cerberus-robot-proxy` (the client of the UI login, create it first), *Add to access token* On), and `...jwt.audiences=cerberus-robot-proxy` on the robot-proxy: only tokens meant for it are accepted. (Without `audiences`, any token of the realm is.)

Cerberus then gets a token with the `client_credentials` grant and sends it as `Authorization: Bearer <token>`.

### OAuth2 browser login of the UI

With `robotproxy.auth.oauth2.ui.client-id` set, the UI is usable in `oauth` mode: opening it in a browser redirects to the Keycloak login (authorization code flow), then back to the UI, which works with a session cookie (its `fetch` calls and the `/chat` WebSocket are same-origin, so they carry it). Cerberus keeps using its Bearer token on the same routes, nothing changes for it.

- Keycloak: create the client `cerberus-robot-proxy` (OpenID Connect, *Standard flow*; it is also the audience target of the machine tokens above), with the valid redirect URI `https://<robot-proxy>/login/oauth2/code/keycloak` and the valid post-logout redirect URI `https://<robot-proxy>/`. Confidential (with `client-secret`) or public (PKCE).
- Any user of the realm who can log in gets the UI (all or nothing, as for tokens).
- `/logout` ends the session and the Keycloak SSO session. Behind a reverse proxy, set `server.forward-headers-strategy=native` (or `framework`) so the redirect URIs use the public address.
- The realm is discovered on the first login, not at startup.
- The Helm probes on `/` get a redirect (`302`, accepted by Kubernetes) instead of `200`: point them to `/check`.
- Known limit: some UI actions are state-changing `GET`s (`startProxy`, `stopProxy`, `clearHar`), so a session cookie is exposed to cross-site requests (browsers send it on top-level navigations with `SameSite=Lax`). Use it on a trusted network, or keep Bearer-only.

The Spring Security headers (cache-control, frame options...) are disabled, to keep the responses as they were.


## Swagger / OpenAPI

The API documentation (springdoc-openapi) is available on `http://localhost:8093/swagger-ui/index.html` (`/swagger-ui.html` redirects to it) and the OpenAPI 3 spec on `http://localhost:8093/v3/api-docs` (it was Springfox: UI `/swagger-ui.html`, Swagger 2 spec `/v2/api-docs`).

## Proxy engine

BrowserMob has been removed: `mitmproxy` (mitmdump) is the only proxy engine. `GET /startProxy` uses `proxyType=mitmproxy` by default; `proxyType=browsermob` returns `{"status":"Error","message":"... BrowserMob has been removed ..."}`. The `/certs/generate` endpoint and the "Certificate" tab (BrowserMob CA/keystore generation) are removed too.
