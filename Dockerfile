FROM gradle:7-jdk11

WORKDIR /opt/timeouts-reproduced
COPY *.gradle /opt/timeouts-reproduced
RUN gradle build || return 0
COPY src src
COPY *.xml /opt/timeouts-reproduced
COPY *.properties /opt/timeouts-reproduced
RUN gradle installDist

FROM openjdk:11-slim-buster

COPY --from=0 /opt/timeouts-reproduced/build/install/timeouts-reproduced /opt/timeouts-reproduced

WORKDIR /opt/timeouts-reproduced
EXPOSE 8080

ENTRYPOINT ["bin/timeouts-reproduced"]
