package com.sublite;

import com.sublite.billing.application.BillingRetryProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(BillingRetryProperties.class)
public class SubliteCoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(SubliteCoreApplication.class, args);
    }
}
