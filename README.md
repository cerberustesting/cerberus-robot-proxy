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

Both routes require `Authorization: Bearer <relay.token>` (`GET /check` is the unrelated, existing health route).

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
- the relay shares the port of the robot-proxy API, whose other routes are not authenticated: expose only `/relay` and `/relay/check` through the tunnel/proxy.

The relay never logs query strings, headers or the token (only method, origin, path, status and duration).


## Swagger / OpenAPI

The API documentation (springdoc-openapi) is available on `http://localhost:8093/swagger-ui/index.html` (`/swagger-ui.html` redirects to it) and the OpenAPI 3 spec on `http://localhost:8093/v3/api-docs` (it was Springfox: UI `/swagger-ui.html`, Swagger 2 spec `/v2/api-docs`).

## Proxy engine

BrowserMob has been removed: `mitmproxy` (mitmdump) is the only proxy engine. `GET /startProxy` uses `proxyType=mitmproxy` by default; `proxyType=browsermob` returns `{"status":"Error","message":"... BrowserMob has been removed ..."}`. The `/certs/generate` endpoint and the "Certificate" tab (BrowserMob CA/keystore generation) are removed too.
