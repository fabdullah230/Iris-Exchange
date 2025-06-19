package com.iris.iris_exchangeoperations;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.iris.common", "com.iris.iris_exchangeoperations"})
public class IrisExchangeOperationsApplication {
    public static void main(String[] args) {
        SpringApplication.run(IrisExchangeOperationsApplication.class, args);
    }
}
