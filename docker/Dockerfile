FROM openjdk:11

EXPOSE 8092
ARG VERSION
ENV PACKAGE_NAME=cerberus-robot-proxy-$VERSION.jar

#wget https://vm.cerberus-testing.org/delivery/cerberus-robot-proxy-$VERSION.jar
RUN echo "Download Cerberus Executor Application" && \
    wget https://github.com/cerberustesting/cerberus-robot-proxy/releases/download/v$VERSION/cerberus-robot-proxy-$VERSION.jar

ENTRYPOINT sh -c 'java -jar "$PACKAGE_NAME"'
