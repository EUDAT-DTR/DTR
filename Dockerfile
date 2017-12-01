FROM openjdk:8-jre
MAINTAINER Alberto Miranda <alberto.miranda@bsc.es>

ENV LANG=en_US.UTF-8

# fetch https://github.com/EUDAT-DTR/DTR.git/cordra 
# and copy it to /eudat/dtr
RUN mkdir -p /eudat/dtr
WORKDIR /eudat/dtr
COPY cordra .

ENV DTRDATA /eudat/dtr/data
# XXX add some ENVS for B2ACCESS configuration
# that can be passed through to docker-entrypoint.sh

COPY docker-entrypoint.sh .

ENTRYPOINT ["/eudat/dtr/docker-entrypoint.sh"]

EXPOSE 8080
EXPOSE 8443
EXPOSE 9900

CMD [ "./startup" ]
