package org.example.Service;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.cache.Cache;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.decorators.Decorators;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.example.Controller.ConsumerController.ProviderController.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import javax.cache.CacheManager;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Supplier;

@Service
public class ResilienceService {


    @Autowired
    private ProviderClient providerClient;
    
    @Autowired
    private CustomProviderClient customProviderClient;
    
    @Autowired
    private CircuitBreaker circuitBreakerA;
    
    @Autowired
    private CircuitBreaker circuitBreakerB;
    
    @Autowired
    private Bulkhead bulkheadService;
    
    @Autowired
    private RateLimiter rateLimiterService;
    
    @Autowired
    private RateLimiter hotParamRateLimiter;
    
    @Autowired
    private TimeLimiter timeLimiterService;
    
    @Autowired
    private Cache<String, Object> resultsCache;
    
    @Autowired
    private CacheManager cacheManager;
    
    private ExecutorService executorService = Executors.newFixedThreadPool(10);

    // 使用断路器A保护的hello调用 - 断路器开启返回200，其他错误返回503
    public ResponseEntity<String> getHelloWithCircuitBreakerA() {
        try {
            return Decorators.ofSupplier(() -> {
                        try {
                            // 正常调用
                            String result = providerClient.callHello();
                            return ResponseEntity.ok(result);
                        } catch (Exception e) {
                            // 将服务调用异常转为503状态码
                            System.out.println("服务调用异常: " + e.getClass().getName() + " - " + e.getMessage());
                            // 抛出原始异常以便断路器计数
                            throw e;
                        }
                    })
                    .withCircuitBreaker(circuitBreakerA)
                    .withFallback(e -> {
                        if (e instanceof CallNotPermittedException) {
                            // 断路器开启时返回200状态码 - 这是预期的降级行为
                            return ResponseEntity.ok("断路器A已打开: Hello服务暂时不可用");
                        } else {
                            // 其他错误返回503状态码
                            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                                    .body("服务调用失败: " + e.getMessage());
                        }
                    })
                    .decorate()
                    .get();
        } catch (Exception e) {
            // 捕获所有未处理的异常，返回503
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("未处理的异常: " + e.getMessage());
        }
    }

    // 断路器B保护 - 熔断器打开时返回错误码，其他情况返回200
    public ResponseEntity<Map<String, Object>> getUsersWithCircuitBreakerB() {
        try {
            return Decorators.ofSupplier(() -> {
                        try {
                            // 正常调用
                            Map<String, Object> result = customProviderClient.getUsers();
                            return ResponseEntity.ok(result);
                        } catch (Exception e) {
                            // 记录原始异常
                            System.out.println("断路器B - 捕获异常: " + e.getClass().getName());

                            // 将异常包装为自定义响应，状态码为200
                            Map<String, Object> errorResponse = new HashMap<>();
                            errorResponse.put("code", 500);
                            errorResponse.put("message", "服务调用异常: " + e.getMessage());
                            return ResponseEntity.ok(errorResponse);
                        }
                    })
                    .withCircuitBreaker(circuitBreakerB)
                    .withFallback(e -> {
                        Map<String, Object> fallbackResponse = new HashMap<>();

                        if (e instanceof CallNotPermittedException) {
                            // 断路器已打开 - 返回503错误
                            fallbackResponse.put("code", 503);
                            fallbackResponse.put("message", "断路器B已打开: 获取用户列表服务暂时不可用");
                            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                                    .body(fallbackResponse);
                        } else {
                            // 其他异常 - 返回200但包含错误信息
                            fallbackResponse.put("code", 500);
                            fallbackResponse.put("message", "其他错误: " + e.getMessage());
                            return ResponseEntity.ok(fallbackResponse);
                        }
                    })
                    .decorate()
                    .get();
        } catch (Exception e) {
            // 未处理的异常也返回200
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("code", 500);
            errorResponse.put("message", "未处理异常: " + e.getMessage());
            return ResponseEntity.ok(errorResponse);
        }
    }

    // 使用隔离器保护的用户详情调用
    public Map<String, Object> getUserByIdWithBulkhead(Long id) {
        return Decorators.ofSupplier(() -> {
                    // 添加人为延迟，模拟长时间处理
                    try {
                        System.out.println("开始处理用户ID: " + id + " 的请求...");
                        // 添加1秒延迟，确保所有请求都在处理中
                        Thread.sleep(1000);
                        System.out.println("完成处理用户ID: " + id + " 的请求");
                        return providerClient.getUserById(id);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("处理被中断", e);
                    }
                })
                .withBulkhead(bulkheadService)
                .withFallback(e -> {
                    Map<String, Object> fallbackResponse = new HashMap<>();
                    if (e instanceof BulkheadFullException) {
                        System.out.println("隔离器拒绝请求: " + e.getMessage());
                        fallbackResponse.put("code", 429);
                        fallbackResponse.put("message", "隔离器限制: 并发请求过多，请稍后再试");
                    } else {
                        System.out.println("其他错误: " + e.getMessage());
                        fallbackResponse.put("code", 500);
                        fallbackResponse.put("message", "隔离器保护的服务调用出错: " + e.getMessage());
                    }
                    return fallbackResponse;
                })
                .decorate()
                .get();
    }

    // 使用限流器保护的添加用户调用
    public Map<String, Object> addUserWithRateLimiter(User user) {
        return Decorators.ofSupplier(() -> {
                    System.out.println("尝试添加用户: " + user.getName() + " (ID:" + user.getId() + ")");
                    return providerClient.addUser(user);
                })
                .withRateLimiter(rateLimiterService)
                .withFallback(e -> {
                    Map<String, Object> fallbackResponse = new HashMap<>();
                    if (e instanceof RequestNotPermitted) {
                        System.out.println("限流器拒绝请求: " + e.getMessage());
                        fallbackResponse.put("code", 429);
                        fallbackResponse.put("message", "限流器限制: 请求频率过高，请稍后再试");
                    } else {
                        System.out.println("其他错误: " + e.getMessage());
                        fallbackResponse.put("code", 500);
                        fallbackResponse.put("message", "限流器保护的服务调用出错: " + e.getMessage());
                    }
                    return fallbackResponse;
                })
                .decorate()
                .get();
    }
    
    // 连接多个容错机制：限流+隔离+熔断的组合保护
    public Map<String, Object> updateUserWithCombinedProtection(Long id, User user) {
        return Decorators.ofSupplier(() -> customProviderClient.updateUser(id, user))
                .withRateLimiter(rateLimiterService)
                .withBulkhead(bulkheadService)
                .withCircuitBreaker(circuitBreakerB)
                .withFallback(e -> {
                    Map<String, Object> fallbackResponse = new HashMap<>();
                    if (e instanceof RequestNotPermitted) {
                        fallbackResponse.put("code", 429);
                        fallbackResponse.put("message", "限流器限制: 请求频率过高，请稍后再试");
                    } else if (e instanceof BulkheadFullException) {
                        fallbackResponse.put("code", 429);
                        fallbackResponse.put("message", "隔离器限制: 并发请求过多，请稍后再试");
                    } else if (e instanceof CallNotPermittedException) {
                        fallbackResponse.put("code", 503);
                        fallbackResponse.put("message", "断路器已打开: 更新用户服务暂时不可用");
                    } else {
                        fallbackResponse.put("code", 500);
                        fallbackResponse.put("message", "组合保护的服务调用出错: " + e.getMessage());
                    }
                    return fallbackResponse;
                })
                .decorate()
                .get();
    }

    // 1. 实现热点参数限流
    public Map<String, Object> getUserWithHotParamLimit(Long userId) {
        String paramKey = "user_" + userId;
        
        // 每个userId都有自己的限流器实例
        RateLimiter userRateLimiter = hotParamRateLimiter;
        
        return Decorators.ofSupplier(() -> {
                    System.out.println("尝试获取用户信息，用户ID: " + userId);
                    return providerClient.getUserById(userId);
                })
                .withRateLimiter(userRateLimiter)
                .withFallback(e -> {
                    Map<String, Object> fallbackResponse = new HashMap<>();
                    if (e instanceof RequestNotPermitted) {
                        System.out.println("热点参数限流拒绝请求: 用户ID " + userId);
                        fallbackResponse.put("code", 429);
                        fallbackResponse.put("message", "热点参数限流: 对用户ID " + userId + " 的请求频率过高，请稍后再试");
                    } else {
                        System.out.println("其他错误: " + e.getMessage());
                        fallbackResponse.put("code", 500);
                        fallbackResponse.put("message", "获取用户信息失败: " + e.getMessage());
                    }
                    return fallbackResponse;
                })
                .decorate()
                .get();
    }
    
    // 2. 实现结果缓存功能 - 使用手动缓存管理
    public Map<String, Object> getUserWithCache(Long userId) {
        String cacheKey = "user_" + userId;
        
        // 先检查缓存中是否有数据
        javax.cache.Cache<String, Object> cache = cacheManager.getCache("resultsCache");
        if (cache.containsKey(cacheKey)) {
            System.out.println("从缓存中获取用户信息，用户ID: " + userId);
            @SuppressWarnings("unchecked")
            Map<String, Object> cachedResult = (Map<String, Object>) cache.get(cacheKey);
            cachedResult.put("fromCache", true);
            return cachedResult;
        }
        
        // 缓存中没有，调用远程服务
        try {
            System.out.println("缓存未命中，从远程服务获取用户信息，用户ID: " + userId);
            Map<String, Object> result = providerClient.getUserById(userId);
            
            // 存入缓存
            cache.put(cacheKey, result);
            result.put("fromCache", false);
            System.out.println("已将用户信息存入缓存: " + result);
            return result;
        } catch (Exception e) {
            System.out.println("获取用户信息失败: " + e.getMessage());
            Map<String, Object> fallbackResponse = new HashMap<>();
            fallbackResponse.put("code", 500);
            fallbackResponse.put("message", "获取用户信息失败: " + e.getMessage());
            return fallbackResponse;
        }
    }
    
    // 3. 实现超时处理功能
    public Map<String, Object> getUserWithTimeout(Long userId) {
        CompletableFuture<Map<String, Object>> future = CompletableFuture.supplyAsync(() -> {
            try {
                System.out.println("异步获取用户信息，用户ID: " + userId);
                // 模拟耗时操作
                if (userId % 3 == 0) {
                    System.out.println("模拟慢请求 - 休眠2秒...");
                    Thread.sleep(2000);
                } else {
                    Thread.sleep(500);
                }
                return providerClient.getUserById(userId);
            } catch (InterruptedException e) {
                System.out.println("操作被中断: " + e.getMessage());
                Thread.currentThread().interrupt();
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("code", 500);
                errorResponse.put("message", "操作被中断");
                return errorResponse;
            }
        }, executorService);
        
        try {
            // 添加超时限制
            Map<String, Object> result = future.get(1, TimeUnit.SECONDS);
            return result;
        } catch (TimeoutException e) {
            System.out.println("获取用户信息超时，用户ID: " + userId);
            Map<String, Object> fallbackResponse = new HashMap<>();
            fallbackResponse.put("code", 408);
            fallbackResponse.put("message", "获取用户信息超时，用户ID: " + userId);
            return fallbackResponse;
        } catch (InterruptedException | ExecutionException e) {
            System.out.println("执行异常: " + e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("code", 500);
            errorResponse.put("message", "执行异常: " + e.getMessage());
            return errorResponse;
        }
    }
    
    // 4. 综合实现：热点参数限流 + 结果缓存 + 超时处理
    public Map<String, Object> getUserWithAllProtections(Long userId) {
        String cacheKey = "user_all_" + userId;
        
        // 先检查缓存
        javax.cache.Cache<String, Object> cache = cacheManager.getCache("resultsCache");
        if (cache.containsKey(cacheKey)) {
            System.out.println("综合保护：从缓存中获取用户信息，用户ID: " + userId);
            @SuppressWarnings("unchecked")
            Map<String, Object> cachedResult = (Map<String, Object>) cache.get(cacheKey);
            cachedResult.put("fromCache", true);
            return cachedResult;
        }
        
        // 应用热点参数限流 - 检查此用户ID的请求频率
        try {
            hotParamRateLimiter.acquirePermission();
        } catch (RequestNotPermitted e) {
            System.out.println("综合保护：热点参数限流拒绝请求，用户ID: " + userId);
            Map<String, Object> fallbackResponse = new HashMap<>();
            fallbackResponse.put("code", 429);
            fallbackResponse.put("message", "请求过于频繁，用户ID: " + userId);
            return fallbackResponse;
        }
        
        // 使用断路器和超时保护
        CompletableFuture<Map<String, Object>> future = CompletableFuture.supplyAsync(() -> {
            try {
                // 检查断路器状态
                if (!circuitBreakerB.tryAcquirePermission()) {
                    System.out.println("断路器已打开，拒绝请求");
                    Map<String, Object> errorResponse = new HashMap<>();
                    errorResponse.put("code", 503);
                    errorResponse.put("message", "服务暂时不可用，断路器已打开");
                    return errorResponse;
                }
                
                System.out.println("综合保护：从远程服务获取用户信息，用户ID: " + userId);
                
                // 模拟随机延迟
                if (userId % 5 == 0) {
                    System.out.println("模拟慢请求 - 休眠1.5秒");
                    Thread.sleep(1500);
                } else {
                    Thread.sleep(300);
                }
                
                // 执行实际调用
                Map<String, Object> result;
                try {
                    // 记录开始时间
                    long startTime = System.nanoTime();
                    
                    result = providerClient.getUserById(userId);
                    
                    // 计算调用耗时
                    long durationInNanos = System.nanoTime() - startTime;
                    
                    // 将结果记录到断路器，并指定调用耗时
                    circuitBreakerB.onSuccess(durationInNanos, TimeUnit.NANOSECONDS);
                    
                    // 将结果存入缓存
                    cache.put(cacheKey, result);
                    result.put("fromCache", false);
                    System.out.println("综合保护：成功获取用户信息并缓存: " + result);
                    return result;
                } catch (Exception e) {
                    // 记录失败结果 - 现在使用了正确的参数
                    long errorDuration = 0; // 失败时的持续时间不重要，设为0
                    circuitBreakerB.onError(errorDuration, TimeUnit.NANOSECONDS, e);
                    throw e;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("操作被中断", e);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, executorService);
        
        try {
            // 添加超时限制
            return future.get(1, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            System.out.println("综合保护：获取用户信息超时，用户ID: " + userId);
            Map<String, Object> fallbackResponse = new HashMap<>();
            fallbackResponse.put("code", 408);
            fallbackResponse.put("message", "获取用户超时，用户ID: " + userId);
            return fallbackResponse;
        } catch (InterruptedException | ExecutionException e) {
            System.out.println("综合保护：执行异常: " + e.getMessage());
            Throwable cause = e.getCause();
            Map<String, Object> errorResponse = new HashMap<>();
            
            if (cause != null && cause.getMessage().contains("断路器已打开")) {
                errorResponse.put("code", 503);
                errorResponse.put("message", "服务暂时不可用，断路器已打开");
            } else {
                errorResponse.put("code", 500);
                errorResponse.put("message", "获取用户信息失败: " + e.getMessage());
            }
            
            System.out.println("综合保护故障：" + errorResponse.get("message"));
            return errorResponse;
        }
    }
    
    // 清理缓存的方法
    public Map<String, Object> clearUserCache(Long userId) {
        String cacheKey = "user_" + userId;
        
        javax.cache.Cache<String, Object> cache = cacheManager.getCache("resultsCache");
        cache.remove(cacheKey);
        
        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("message", "成功清除用户 " + userId + " 的缓存");
        return response;
    }
} 