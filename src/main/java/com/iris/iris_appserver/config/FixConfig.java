package com.iris.iris_appserver.config;

import lombok.extern.slf4j.Slf4j;
import org.quickfixj.jmx.JmxExporter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import quickfix.*;

import javax.management.JMException;
import java.io.InputStream;

@Slf4j
@Configuration
public class FixConfig {

    @Bean(destroyMethod = "stop")
    public Acceptor serverAcceptor(Application fixApplication) throws ConfigError, JMException {
        // Load session settings from the configuration file
        InputStream inputStream = getClass().getResourceAsStream("/quickfixj.cfg");
        SessionSettings settings = new SessionSettings(inputStream);

        // Create directories for FileStorePath and FileLogPath if they don't exist
        createDirectories(settings);

        // Create the FIX components
        MessageStoreFactory storeFactory = new FileStoreFactory(settings);
        LogFactory logFactory = new FileLogFactory(settings);
        MessageFactory messageFactory = new DefaultMessageFactory();

        // Create the FIX acceptor
        SocketAcceptor acceptor = new SocketAcceptor(fixApplication, storeFactory, settings, logFactory, messageFactory);

        // Register the acceptor with JMX for monitoring
        JmxExporter exporter = new JmxExporter();
        exporter.register(acceptor);

        log.info("FIX Acceptor initialized");
        return acceptor;
    }

    private void createDirectories(SessionSettings settings) {
        try {
            String fileStorePath = settings.getString("FileStorePath");
            String fileLogPath = settings.getString("FileLogPath");

            java.nio.file.Files.createDirectories(java.nio.file.Paths.get(fileStorePath));
            java.nio.file.Files.createDirectories(java.nio.file.Paths.get(fileLogPath));

            log.info("Directories created for FileStorePath: {} and FileLogPath: {}", fileStorePath, fileLogPath);
        } catch (Exception e) {
            log.error("Error creating directories for FileStorePath or FileLogPath", e);
            throw new RuntimeException(e);
        }
    }
}