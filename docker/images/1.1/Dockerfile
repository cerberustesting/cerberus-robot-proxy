FROM openjdk:11

EXPOSE 8093

# Download Cerberus Executor Application
ADD cerberus-executor-screenrecorder-1.1.0.jar cerberus-executor-screenrecorder-1.1.0.jar
#RUN echo "Download Cerberus Executor Application" && \
#    wget https://github.com/cerberustesting/cerberus-executor/releases/download/cerberus-executor-1.1.0/cerberus-executor-1.1.0.jar

ENTRYPOINT ["java", "-jar", "cerberus-executor-screenrecorder-1.1.0.jar"]