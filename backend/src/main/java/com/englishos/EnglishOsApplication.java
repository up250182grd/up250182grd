package com.englishos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class EnglishOsApplication {

    public static void main(String[] args) {
        SpringApplication.run(EnglishOsApplication.class, args);
    }
}
