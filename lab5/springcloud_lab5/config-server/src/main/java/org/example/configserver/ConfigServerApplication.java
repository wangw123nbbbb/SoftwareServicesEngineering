package org.example.configserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;
// import org.springframework.cloud.netflix.eureka.EnableEurekaClient; // 注释掉或删除此行

@SpringBootApplication
@EnableConfigServer
// @EnableEurekaClient // 注释掉或删除此注解
public class ConfigServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }

} 