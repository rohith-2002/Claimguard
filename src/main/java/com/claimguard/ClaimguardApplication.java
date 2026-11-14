package com.claimguard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ClaimguardApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClaimguardApplication.class, args);
    }
}
