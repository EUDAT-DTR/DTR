FROM gradle
MAINTAINER Alberto Miranda <alberto.miranda@bsc.es>

ENV LANG=en_US.UTF-8

# the gradle image changes the user to 'gradle'
# but we need to be able to create some stuff
USER root

# fetch https://github.com/EUDAT-DTR/DTR.git/cordra 
# and copy it to /eudat/dtr
RUN mkdir -p /eudat/dtr
WORKDIR /eudat/dtr
COPY cordra .

WORKDIR /home/gradle

# fetch https://github.com/EUDAT-DTR/DTR.git/EUDAT-DTR-WEBAPP
# and copy it to /home/gradle
COPY EUDAT-DTR-WEBAPP EUDAT-DTR-WEBAPP

# build and install the webapp
RUN set -o errexit \
        && echo "Building EUDAT-DTR webapp" \
        && cd EUDAT-DTR-WEBAPP \
        && ./autogen-docker.sh /eudat/dtr/data/webapps-priority/ROOT.war /eudat/dtr/sw/lib \
        && gradle assemble \
        \
        && echo "Installing EUDAT-DTR webapp" \
        && cp build/libs/EUDAT-DTR.war /eudat/dtr/data/webapps-priority/ROOT.war \
        \
        && echo "Building EUDAT-DTR tools" \
        && cd tools \
        && gradle shadowJar \
        \
        && echo "Installing EUDAT-DTR tools" \
        && mkdir -p /eudat/dtr/tools \
        && cp ./json_exporter/build/libs/json_exporter-1.0-all.jar /eudat/dtr/tools \
        \
        && echo "Cleaning up" \
        && cd /home/gradle \
        && rm -rf EUDAT-DTR-WEBAPP

WORKDIR /eudat/dtr
COPY docker-entrypoint.sh .

ENV DTRDATA /eudat/dtr/data
ENTRYPOINT ["/eudat/dtr/docker-entrypoint.sh"]

EXPOSE 8080
EXPOSE 8443
EXPOSE 9900

CMD [ "./startup" ]
