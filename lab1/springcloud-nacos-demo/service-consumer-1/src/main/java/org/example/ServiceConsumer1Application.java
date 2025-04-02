package org.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients  // 如果使用 Feign 进行远程调用需要开启此注解
@EnableDiscoveryClient
public class ServiceConsumer1Application {
    public static void main(String[] args) {
        SpringApplication.run(ServiceConsumer1Application.class, args);
    }
}