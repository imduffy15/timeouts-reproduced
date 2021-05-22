package ie.ianduffy.timeoutsreproduced;

import io.prometheus.client.Collector;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.HTTPServer;
import io.prometheus.jmx.BuildInfoCollector;
import io.prometheus.jmx.JmxCollector;
import org.apache.commons.cli.*;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.FileBasedConfiguration;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.combined.CombinedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.CombinedBuilderParameters;
import org.apache.commons.configuration2.builder.fluent.FileBasedBuilderParameters;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.convert.DefaultListDelimiterHandler;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.pivovarit.function.ThrowingFunction.unchecked;

public class Main {

    private final static Logger logger = LoggerFactory.getLogger(Main.class);

    private static Optional<HTTPServer> prometheusMetrics = Optional.empty();
    private static Optional<LagTracker> lagTracker = Optional.empty();

    public static void main(String[] args) throws InterruptedException {
        CommandLine cli = parseCommandLine(args);
        Configuration configuration = readConfiguration(cli.getOptionValue("config", "config.xml"));
        prometheusMetrics = Optional.of(prometheusMetrics(configuration.getString("prometheus.host"), configuration.getInt("prometheus.port")));
        lagTracker = Optional.of(new LagTracker(configuration));

        Runtime.getRuntime().addShutdownHook(shutdown());

        while (true) {
            logger.info("lag is: {}", lagTracker.get().update());
            Thread.sleep(5000);
        }
    }

    private static Thread shutdown() {
        return new Thread(() -> {
            logger.info("Shutting down...");
            logger.info("Shutting down the lag tracker...");
            lagTracker.ifPresent(LagTracker::stop);
            logger.info("Shutting down prometheus exporter...");
            prometheusMetrics.ifPresent(HTTPServer::stop);
        });
    }

    private static Configuration readConfiguration(String path) {
        try {
            FileBasedBuilderParameters defaultParameters = new Parameters()
                    .fileBased()
                    .setPath(path)
                    .setListDelimiterHandler(new DefaultListDelimiterHandler(','));
            if (path.endsWith(".xml")) {
                CombinedBuilderParameters parameters = new Parameters()
                        .combined()
                        .setDefinitionBuilderParameters(defaultParameters)
                        .setListDelimiterHandler(new DefaultListDelimiterHandler(','));
                return new CombinedConfigurationBuilder().configure(parameters).getConfiguration();
            } else {
                return new FileBasedConfigurationBuilder<FileBasedConfiguration>(PropertiesConfiguration.class)
                        .configure(defaultParameters)
                        .getConfiguration();
            }
        } catch (ConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

    private static CommandLine parseCommandLine(String[] args) {
        try {
            CommandLineParser parser = new DefaultParser();
            Options options = new Options();
            options.addOption(
                    Option.builder("c")
                            .argName("config")
                            .hasArg()
                            .longOpt("config")
                            .desc("Path to configuration file")
                            .build()
            );
            return parser.parse(options, args);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    private static HTTPServer prometheusMetrics(String host, int port) {
        try {
            new BuildInfoCollector().register();
            getJmxCollector().ifPresent(Collector::register);
            HTTPServer httpServer = new HTTPServer(new InetSocketAddress(host, port), CollectorRegistry.defaultRegistry);
            logger.info("Prometheus metrics started at http://{}:{}", host, port);
            return httpServer;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Optional<JmxCollector> getJmxCollector() {
        return Optional.ofNullable(Main.class.getClassLoader().getResourceAsStream("jmx_exporter.yaml"))
                .map(unchecked(inputStream -> new JmxCollector(new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8)).lines().collect(Collectors.joining("\n")))));
    }
}
