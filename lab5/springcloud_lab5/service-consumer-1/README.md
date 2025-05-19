# 微服务容错保护实验

本实验基于SpringCloud 2024.0.1和SpringBoot 3.4.4，结合Resilience4j实现了微服务的熔断、隔离和限流保护机制。

## 实验内容

1. 配置并实现了两种断路器：
   - 断路器A：基于失败率的断路器
   - 断路器B：基于失败率和慢调用率的断路器

2. 配置并实现了隔离器，限制并发执行的最大数量

3. 配置并实现了限流器，控制请求频率

4. 为微服务调用添加了相应的服务降级保护措施

5. 新增容错机制：
   - 热点参数限流：针对特定参数值（如用户ID）限制访问频率
   - 结果缓存：缓存接口调用结果，提高响应速度
   - 超时处理：控制请求超时时间，避免长时间等待
   - 综合保护：结合多种保护机制实现全方位容错

## 测试接口说明

### 断路器测试接口

- 基于失败率的断路器A测试：`GET /resilience/circuit-breaker-a`
- 基于失败率和慢调用率的断路器B测试：`GET /resilience/circuit-breaker-b`

### 隔离器测试接口

- 隔离器测试：`GET /resilience/bulkhead/{id}`
- 并发测试（创建20个并发请求测试隔离效果）：`GET /resilience/concurrent-test`

### 限流器测试接口

- 限流器测试：`POST /resilience/rate-limiter`
- 压力测试（连续发送10个请求测试限流效果）：`GET /resilience/rate-limit-test`

### 组合保护测试接口

- 组合容错保护测试（限流+隔离+熔断）：`PUT /resilience/combined-protection/{id}`

### 新增容错机制测试接口

- 热点参数限流测试：`GET /resilience/hot-param/{userId}`
- 热点参数限流压力测试：`GET /resilience/hot-param-test/{userId}`
- 结果缓存测试：`GET /resilience/cache/{userId}`
- 缓存性能测试：`GET /resilience/cache-test/{userId}`
- 清除缓存：`DELETE /resilience/cache/{userId}`
- 超时处理测试：`GET /resilience/timeout/{userId}`
- 超时效果测试：`GET /resilience/timeout-test`
- 综合保护机制测试：`GET /resilience/all-protections/{userId}`

## 测试方法

### 使用JMeter进行并发测试

1. 下载安装JMeter：https://jmeter.apache.org/download_jmeter.cgi
2. 创建测试计划，添加线程组（Thread Group）
3. 配置HTTP请求采样器（HTTP Request Sampler）指向对应测试接口
4. 添加结果查看器（View Results Tree）监控测试结果
5. 根据测试场景配置线程数、循环次数和延迟等参数

### 测试断路器效果

1. 配置高并发请求或手动使服务出错（例如关闭provider服务）
2. 观察断路器从CLOSED状态变为OPEN状态的过程
3. 等待5秒后，观察断路器变为HALF_OPEN状态开始允许部分请求通过
4. 如果成功率恢复，观察断路器重新变为CLOSED状态

### 测试隔离器效果

1. 使用JMeter创建超过10个并发请求（例如20个）
2. 观察部分请求被隔离器拒绝，返回降级响应
3. 也可以直接使用 `/resilience/concurrent-test` 接口测试

### 测试限流器效果

1. 使用JMeter在2秒内快速发送超过5个请求
2. 观察超出限制的请求被限流，返回降级响应
3. 也可以直接使用 `/resilience/rate-limit-test` 接口测试

### 测试新增容错机制（选做题）

#### 1. 热点参数限流测试

热点参数限流针对特定参数值（如用户ID）的访问频率进行限制，防止热点数据被过度访问。

**测试步骤**：
1. 访问 `GET /resilience/hot-param-test/1` 
2. 系统会快速发送10个针对同一用户ID的请求
3. 由于配置了每秒最多允许2个请求，大约会有8个请求被拒绝
4. 观察返回结果中的成功和被限流的请求数量

**实现原理**：
```java
// 热点参数限流配置
RateLimiterConfig config = RateLimiterConfig.custom()
        .limitRefreshPeriod(java.time.Duration.ofSeconds(1)) // 每秒刷新
        .limitForPeriod(2) // 每个参数每秒只允许2个请求
        .timeoutDuration(java.time.Duration.ofMillis(500)) // 获取许可的等待时间
        .build();
```

#### 2. 结果缓存测试

结果缓存存储接口调用的结果，当相同请求再次到来时，直接从缓存返回结果，减少远程调用次数。

**测试步骤**：
1. 访问 `GET /resilience/cache-test/1`
2. 系统会清除现有缓存，进行两次调用（一次无缓存，一次有缓存）
3. 比较两次调用的响应时间，展示缓存带来的性能提升
4. 可以使用 `DELETE /resilience/cache/1` 手动清除缓存

**实现原理**：
```java
// 缓存配置
MutableConfiguration<Object, Object> config = new MutableConfiguration<>()
        .setTypes(Object.class, Object.class)
        .setStoreByValue(false)
        .setExpiryPolicyFactory(CreatedExpiryPolicy.factoryOf(Duration.ONE_MINUTE)); 
```

#### 3. 超时处理测试

超时处理确保远程调用不会无限期等待，当调用时间超过设定的阈值后会主动中断并返回降级结果。

**测试步骤**：
1. 访问 `GET /resilience/timeout-test`
2. 系统会依次请求用户ID 1-10
3. 其中ID为3、6、9的用户会模拟2秒的延迟，超过1秒的超时阈值
4. 观察这些请求的超时响应，以及其他请求的正常响应

**实现原理**：
```java
// 超时处理配置
TimeLimiterConfig config = TimeLimiterConfig.custom()
        .timeoutDuration(java.time.Duration.ofSeconds(1)) // 1秒超时
        .cancelRunningFuture(true) // 超时后取消运行中的任务
        .build();
```

#### 4. 综合保护机制测试

综合保护机制将热点参数限流、结果缓存和超时处理结合使用，形成多层次的保护机制。

**测试步骤**：
1. 访问 `GET /resilience/all-protections/1`（正常用户）
2. 访问 `GET /resilience/all-protections/5`（模拟慢请求，可能触发超时）
3. 连续多次访问同一ID，例如：
   ```
   GET /resilience/all-protections/1
   GET /resilience/all-protections/1
   GET /resilience/all-protections/1
   ```
4. 第二次请求应该从缓存中获取结果（更快），第三次请求可能会被限流

**实现流程**：
1. 首先检查缓存是否有结果
2. 应用热点参数限流控制访问频率
3. 添加超时控制确保响应时间
4. 使用断路器防止系统过载
5. 将成功结果存入缓存供后续使用

## 监控断路器状态

可以通过Actuator端点查看断路器状态：

- 健康状态：`GET /actuator/health`
- 所有断路器状态：`GET /actuator/circuitbreakers`
- 特定断路器事件：`GET /actuator/circuitbreakerevents`
- 特定断路器状态：`GET /actuator/circuitbreakerevents/{name}`

## 实验分析与总结

### 断路器异同点

1. **失败率熔断与慢调用熔断的异同点**：
   - 相同点：都用于防止系统雪崩，保护后端服务
   - 不同点：
     - 失败率熔断关注请求失败（异常）的比例
     - 慢调用熔断关注响应时间过长的请求比例

2. **适用场景**：
   - 失败率熔断适用于后端服务完全不可用的场景
   - 慢调用熔断适用于后端服务响应缓慢但仍可访问的场景

### 隔离器作用

1. 限制对下游服务的并发调用数量
2. 防止单个服务占用过多系统资源
3. 在高并发场景下保护系统稳定性
4. 避免线程池耗尽导致的系统崩溃

### 限流器作用

1. 控制系统访问频率，防止系统过载
2. 保护后端服务免受突发流量冲击
3. 在有限资源下提供更稳定的服务
4. 可用于防止恶意攻击或异常访问

### 热点参数限流作用

1. 针对某些热点参数（如热门商品ID、热门用户ID）进行精细化限流
2. 防止热点数据被频繁访问导致系统过载
3. 实现更精准的流量控制，而不是粗粒度地限制所有请求
4. 特别适用于有明显热点请求的系统，如电商、社交媒体等

### 结果缓存作用

1. 减少对相同数据的重复查询，降低系统负载
2. 提高接口响应速度，改善用户体验
3. 降低下游服务的压力，提高系统整体吞吐量
4. 减少网络传输和序列化开销，节约系统资源

### 超时处理作用

1. 防止接口长时间等待造成线程池耗尽
2. 提供更好的用户体验，避免用户长时间等待
3. 及时释放系统资源，避免资源浪费
4. 与断路器配合使用，可以更早地发现系统问题

### 总体实验结论

微服务容错保护机制对提高系统可靠性和韧性至关重要，它们各自解决不同维度的问题：

- 断路器：解决服务依赖不可用问题
- 隔离器：解决资源竞争问题
- 限流器：解决请求过载问题
- 热点参数限流：解决热点数据过载问题
- 结果缓存：解决重复请求问题
- 超时处理：解决响应时间过长问题

在实际生产环境中，应根据系统特点综合使用这些保护机制，并结合监控系统实时调整配置参数，以获得最佳效果。通过多层次的容错保护，可以构建一个更加健壮、可靠的微服务系统。 