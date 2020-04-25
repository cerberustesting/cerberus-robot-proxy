FROM openjdk:11

EXPOSE 8092

# Install Chrome and Chrome Driver
RUN apt-get update && \
    apt-get install -y gnupg wget curl unzip --no-install-recommends && \
    wget -q -O - https://dl-ssl.google.com/linux/linux_signing_key.pub | apt-key add - && \
    echo "deb http://dl.google.com/linux/chrome/deb/ stable main" >> /etc/apt/sources.list.d/google.list && \
    apt-get update -y && \
    apt-get install -y google-chrome-stable && \
    CHROMEVERSION=$(google-chrome --product-version | grep -o "[^\.]*\.[^\.]*\.[^\.]*") && \
    DRIVERVERSION=$(curl -s "https://chromedriver.storage.googleapis.com/LATEST_RELEASE_$CHROMEVERSION") && \
    wget -q --continue -P /chromedriver "http://chromedriver.storage.googleapis.com/$DRIVERVERSION/chromedriver_linux64.zip" && \
    unzip /chromedriver/chromedriver* -d /chromedriver

# Install XVFB
RUN apt-get install -yq xvfb

# Download Cerberus Executor Application
#ADD cerberus-executor-1.1.0.jar cerberus-executor-1.1.0.jar
RUN echo "Download Cerberus Executor Application" && \
    wget https://github.com/cerberustesting/cerberus-executor/releases/download/cerberus-executor-1.1.0/cerberus-executor-1.1.0.jar

# Launch Cerberus Executor with xvfb
ADD launch.sh /var/launch.sh
RUN chmod 755 /var/launch.sh
CMD ["/bin/bash", "/var/launch.sh"]