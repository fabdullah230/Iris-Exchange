package com.iris.iris_dbwriter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {
        "com.iris.common",
        "com.iris.iris_dbwriter"
})
public class IrisDbWriterApplication {
    public static void main(String[] args) {
        // Set the active profile programmatically as backup
        System.setProperty("spring.profiles.active", "dbwriter");
        SpringApplication.run(IrisDbWriterApplication.class, args);
    }
}