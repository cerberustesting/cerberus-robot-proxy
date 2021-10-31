# cerberus-executor (Work In Progress)

This project can be used from Cerberus (https://github.com/cerberustesting/cerberus-source) v 4.2 
This project can run on top of selenium server and allow to activate some extra features like a proxy server.

You can start the executor with:

```
java -jar cerberus-executor.jar
```

In case you want to start executor on a different port you can overwrite it by creating a new file from https://github.com/cerberustesting/cerberus-executor/blob/master/src/main/resources/application.properties

and then start the executor with :

```
java -jar cerberus-executor.jar --spring.config.location=classpath:application.properties,/Users/Documents/cerberusexecutor_local.properties
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
