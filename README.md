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

To start a new proxy, you just need the `<port>` of the proxy to start (make sure it's not already in used)

`/startProxy?port=<port>`

You will get a `uuid` in the body response

## Stop a Proxy

To stop a proxy, you need its `<uuid>`

`/stopProxy?uuid=<uuid>`

## Get content (HAR)

To get the current content of network activity (`.har` file) for a proxy, you need its `<uuid>`

`/getHar?uuid=<uuid>`
