# Timeouts reproduced

This code attempts to reproduce an issue experienced with Apache Kafka.

This segment of code failed consistently when brokers were restarted, we do not know why but hopefully
this code can assist with reproducing it and figuring out the cause.

## Usage

Modify `config.properties` accordingly with a bootstrap server and authentication details should they be needed

Build it with:

```
docker build . -t timeouts-reproduced
```

and finally run it with:

```
docker run -p 8080:8080 -it timeouts-reproduced
```

Should you need to adjust log levels please do so from the `./src/main/java/resources/log4j.properties` file.

Should you need any of the JMX metrics from the kafka clients they are exposed at http://localhost:8080