package com.iris.iris_appserver;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import quickfix.Acceptor;

@SpringBootApplication
@ComponentScan(basePackages = {"com.iris.iris_appserver", "com.iris.common"})
public class IrisAppserverApplication implements CommandLineRunner {

    private final Acceptor serverAcceptor;

    public IrisAppserverApplication(Acceptor serverAcceptor) {
        this.serverAcceptor = serverAcceptor;
    }

    public static void main(String[] args) {
        SpringApplication.run(IrisAppserverApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        serverAcceptor.start(); // Start the FIX server
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            serverAcceptor.stop();
        }));
    }
}