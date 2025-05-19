# Spring Cloud Gateway 网关服务

本项目是Spring Cloud微服务架构中的API网关服务，使用Spring Cloud Gateway实现。

## 项目结构

```
service-gateway/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── org/
│   │   │       └── example/
│   │   │           └── gateway/
│   │   │               ├── config/
│   │   │               │   ├── CorsConfig.java         # 跨域配置
│   │   │               │   └── ResilienceConfig.java   # 容错机制配置
│   │   │               ├── filter/
│   │   │               │   ├── AuthenticationFilter.java # 认证过滤器
│   │   │               │   ├── FallbackController.java  # 熔断回调处理
│   │   │               │   └── RateLimiterFilter.java   # 限流过滤器
│   │   │               └── GatewayApplication.java     # 主启动类
│   │   └── resources/
│   │       └── application.yml        # 配置文件
            |__ test.html              # 全局认证简单测试样例
├── pom.xml                            # 项目依赖
└── README.md                          # 项目说明
```

## 核心功能

1. **动态路由**：基于服务名的动态路由，实现了服务转发
2. **负载均衡**：集成Ribbon实现负载均衡，使用`lb://`前缀定义路由
3. **全局认证**：实现了全局权限认证过滤器，对请求进行认证
4. **跨域配置**：支持全局跨域请求配置
5. **容错机制**：
   - 断路器(Circuit Breaker)：使用Resilience4j实现断路器，防止雪崩效应
   - 限流(Rate Limiter)：自定义限流过滤器，控制API访问频率
   - 熔断降级：提供友好的服务降级响应

## 配置说明

### 路由配置

路由配置在`application.yml`中，每条路由包含：
- id：路由唯一标识
- uri：目标服务地址，使用`lb://`前缀实现负载均衡
- predicates：路由断言条件，基于Path匹配请求路径
- filters：路由过滤器，进行路径处理、熔断处理等

### 断路器配置

使用Resilience4j实现断路器：
- slidingWindowSize：滑动窗口大小
- minimumNumberOfCalls：最小调用次数
- failureRateThreshold：失败率阈值
- waitDurationInOpenState：断路器打开状态持续时间
- permittedNumberOfCallsInHalfOpenState：半开状态允许的调用次数

### 限流配置

自定义实现了限流过滤器(RateLimiterFilter)，基于计数器实现对不同路径的访问频率控制。

### 跨域配置

支持全局跨域配置，允许所有来源、方法和头部，实现了前后端分离架构支持。

## 使用方法

### 服务启动

1. 确保服务注册中心已启动
2. 启动Gateway服务：`mvn spring-boot:run`
3. 访问API网关：`http://localhost:9000`

### 请求示例

- 访问服务提供者1: `http://localhost:9000/provider1/api/hello`
- 访问服务提供者2: `http://localhost:9000/provider2/api/hello`
- 访问服务消费者1: `http://localhost:9000/consumer1/consumer/feign/hello`
- 访问服务消费者2: `http://localhost:9000/consumer2/consumer/feign/hello`

### 测试认证

需要在请求头中添加`Authorization`头：
```
Authorization: Bearer your-token
```

### 测试容错机制

可以使用JMeter等工具进行并发测试，验证限流和熔断降级功能。 