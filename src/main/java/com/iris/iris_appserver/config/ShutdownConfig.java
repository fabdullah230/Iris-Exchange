package com.iris.iris_appserver.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.stereotype.Component;
import quickfix.Acceptor;


@Component
public class ShutdownConfig implements ApplicationListener<ContextClosedEvent> {

    private final Logger log = LoggerFactory.getLogger(ShutdownConfig.class);

    private final Acceptor serverAcceptor;

    public ShutdownConfig(Acceptor serverAcceptor) {
        this.serverAcceptor = serverAcceptor;
    }

    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        if (serverAcceptor != null) {
            log.info("Explicitly stopping FIX acceptor...");
            try {
                serverAcceptor.stop(true);
            } catch (Exception e) {
                log.error("Error stopping FIX acceptor", e);
            }
        }
    }
}