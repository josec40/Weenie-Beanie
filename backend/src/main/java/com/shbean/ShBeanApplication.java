package com.shbean;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ShBeanApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShBeanApplication.class, args);
    }
}
