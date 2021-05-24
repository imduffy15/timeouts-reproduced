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

## Local simulation (failures do not occur)

```
$ docker-compse up
$ kafka-topics --bootstrap-server broker1:9091 --command-config kafka.properties --create --topic replace-me --replication-factor 3 --partitions 12
$ seq 1 100000000000000 | kafka-console-producer --producer.config kafka.properties --broker-list broker1:9091,broker2:9092,broker3:9093 --topic replace-me
$ kafka-console-consumer --consumer.config kafka.properties --bootstrap-server broker1:9091,broker2:9092,broker3:9093 --topic replace-me --from-beginning 
```

Simulate restarts with:

```
$ while true; do (docker restart $(docker ps | grep cp-server | awk '{print $1}' | shuf)); sleep 5000 ;done
```