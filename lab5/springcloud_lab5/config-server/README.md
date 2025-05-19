# Spring Cloud Config配置中心实验

## 实验概述

本实验基于Spring Cloud 2024.0.1 (Moorgate) 和Spring Boot 3.4.4构建分布式配置中心，并结合Spring Cloud Bus和RabbitMQ实现配置的动态刷新机制。通过本实验，您将掌握微服务架构中集中化配置管理和动态配置更新的实现方法。

## 实验目的

1. 掌握Spring Cloud微服务架构基础知识
2. 了解分布式消息队列的基本原理
3. 熟知配置中心集群与消息总线的基本原理和作用
4. 掌握Spring Cloud Config的配置与集群搭建方法
5. 掌握RabbitMQ的安装配置和基本使用
6. 掌握基于Spring Cloud Bus的配置动态刷新机制

## 技术栈

- **Spring Cloud**: 2024.0.1 (Moorgate)
- **Spring Boot**: 3.4.4
- **Spring Cloud Config**: 配置中心
- **Spring Cloud Bus**: 消息总线
- **RabbitMQ**: 消息队列
- **Eureka**: 服务注册中心
- **GitHub**: 配置文件存储

## 项目结构

```
springcloud_lab5/
├── config-server/         # 配置中心
├── service-registry0/     # 服务注册中心实例0
├── service-registry1/     # 服务注册中心实例1
├── service-registry2/     # 服务注册中心实例2
├── service-provider-1/    # 服务提供者1
├── service-provider-2/    # 服务提供者2
├── service-consumer-1/    # 服务消费者1
├── service-consumer-2/    # 服务消费者2
└── service-gateway/       # 服务网关
```

## 实验步骤

### 1. 配置仓库准备

在GitHub上创建配置仓库，添加各服务的配置文件：
- `lab5/service-provider-1.yml`
- `lab5/service-consumer-1.yml`
- `lab5/service-consumer-2.yml`

### 2. 配置中心构建

1. 创建Spring Boot应用，引入以下依赖：
   - `spring-cloud-config-server`
   - `spring-cloud-starter-netflix-eureka-client`
   - `spring-cloud-bus`
   - `spring-cloud-stream-binder-rabbit`
   - `spring-boot-starter-actuator`

2. 主启动类添加`@EnableConfigServer`注解

3. 配置`application.yml`：
   ```yaml
   server:
     port: 8888
   spring:
     application:
       name: config-server
     profiles:
       active: git
     cloud:
       config:
         server:
           git:
             uri: https://github.com/your-username/your-repo.git
             search-paths:
               - lab5
               - lab5/{application}
   ```

### 3. 微服务配置

1. 所有微服务添加依赖：
   - `spring-cloud-starter-config`
   - `spring-cloud-starter-bootstrap`
   - `spring-cloud-bus`
   - `spring-cloud-stream-binder-rabbit`
   - `spring-boot-starter-actuator`

2. 创建`bootstrap.yml`配置：
   ```yaml
   spring:
     application:
       name: service-name
     cloud:
       config:
         discovery:
           enabled: true
           service-id: config-server
   ```

3. 控制器添加`@RefreshScope`注解，实现配置动态刷新

### 4. 配置中心集群构建

1. 配置中心注册到Eureka服务注册中心
2. 启动多个配置中心实例
3. 微服务通过Eureka发现配置中心

### 5. 动态配置刷新测试

1. 修改GitHub仓库中的配置文件
2. 发送POST请求到配置中心：`/actuator/busrefresh`
3. 观察微服务配置是否自动更新

## 动态配置刷新机制原理

Spring Cloud Config与Spring Cloud Bus结合的动态配置刷新机制工作原理：

1. **配置源管理**：配置文件集中存储在GitHub仓库
2. **配置中心分发**：Config Server从GitHub拉取配置，并向各微服务提供配置
3. **消息总线传播**：配置变更时，通过`/actuator/busrefresh`触发刷新事件
4. **消息队列中转**：RabbitMQ作为消息代理，将刷新事件广播给所有微服务
5. **配置动态更新**：微服务接收到刷新事件后，重新获取最新配置，`@RefreshScope`注解的Bean会被重新创建

## 高级特性

### Webhook自动刷新

可以配置GitHub Webhook，当配置仓库变更时自动触发配置刷新：
1. GitHub仓库 → Settings → Webhooks → Add webhook
2. Payload URL: `http://your-config-server/actuator/busrefresh`
3. Content type: `application/json`
4. 选择触发事件：`Just the push event`

### 配置加密与安全

Spring Cloud Config支持配置加密存储，保护敏感信息：
```yaml
encrypt:
  key: your-secret-key
```

## 总结

通过Spring Cloud Config配置中心和Spring Cloud Bus消息总线，我们实现了微服务架构中配置的集中管理和动态更新。这种方式具有以下优势：

1. **配置集中管理**：所有配置统一存储，便于版本控制
2. **环境隔离**：不同环境配置可以分别管理
3. **动态更新**：无需重启服务即可更新配置
4. **高可用保障**：配置中心集群部署，避免单点故障
5. **安全性提升**：敏感配置可以加密存储

这些能力为构建大规模微服务系统提供了强大的配置管理支持，是现代分布式系统的重要基础设施。 