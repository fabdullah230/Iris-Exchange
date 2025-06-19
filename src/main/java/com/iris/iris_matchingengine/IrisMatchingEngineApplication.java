package com.iris.iris_matchingengine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.iris.common", "com.iris.iris_matchingengine"})
public class IrisMatchingEngineApplication {
    public static void main(String[] args) {
        SpringApplication.run(IrisMatchingEngineApplication.class, args);
    }
}