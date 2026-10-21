FROM eclipse-temurin:17

EXPOSE 8092
EXPOSE 8080
EXPOSE 8081

ARG VERSION
ENV PACKAGE_NAME=cerberus-robot-proxy-$VERSION.jar

# Install Python + mitmproxy
RUN apt-get update && \
    apt-get install -y python3 python3-pip wget && \
    pip3 install --no-cache-dir --break-system-packages mitmproxy && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

#wget https://vm.cerberus-testing.org/delivery/cerberus-robot-proxy-$VERSION.jar
RUN echo "Download Cerberus Executor Application" && \
    wget https://github.com/cerberustesting/cerberus-robot-proxy/releases/download/v$VERSION/cerberus-robot-proxy-$VERSION.jar

# exec form, and exec: the JVM is PID 1 and gets the stop signal, instead of a shell that does not pass it on
ENTRYPOINT ["sh", "-c", "exec java -jar \"$PACKAGE_NAME\""]
