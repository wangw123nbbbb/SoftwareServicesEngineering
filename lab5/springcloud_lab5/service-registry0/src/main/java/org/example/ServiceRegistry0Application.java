package org.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

@SpringBootApplication
@EnableEurekaServer
public class ServiceRegistry0Application {
    public static void main(String[] args) {
        SpringApplication.run(ServiceRegistry0Application.class, args);
    }
}
