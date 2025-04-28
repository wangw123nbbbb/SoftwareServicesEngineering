package org.example.config;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.cache.Cache;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.expiry.CreatedExpiryPolicy;
import javax.cache.expiry.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class Resilience4jConfig {

    // 配置基于失败率的断路器
    @Bean
    public CircuitBreaker circuitBreakerA() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(30) // 失败率阈值为30%
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.TIME_BASED) // 时间窗口
                .slidingWindowSize(10) // 10秒
                .minimumNumberOfCalls(5) // 最小调用次数
                .waitDurationInOpenState(java.time.Duration.ofSeconds(5)) // 断路器从OPEN到HALF_OPEN状态的等待时间
                .permittedNumberOfCallsInHalfOpenState(3) // HALF_OPEN状态允许的调用次数
                .build();
        
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        return registry.circuitBreaker("circuitBreakerA");
    }
    
    // 配置基于慢调用率和失败率的断路器
    @Bean
    public CircuitBreaker circuitBreakerB() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50) // 失败率阈值为50%
                .slowCallRateThreshold(30) // 慢调用率阈值为30%
                .slowCallDurationThreshold(java.time.Duration.ofSeconds(2)) // 慢调用判断时间为2秒
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.TIME_BASED) // 时间窗口
                .slidingWindowSize(10) // 10秒
                .minimumNumberOfCalls(5) // 最小调用次数
                .waitDurationInOpenState(java.time.Duration.ofSeconds(5)) // 断路器从OPEN到HALF_OPEN状态的等待时间
                .permittedNumberOfCallsInHalfOpenState(3) // HALF_OPEN状态允许的调用次数
                .build();
        
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        return registry.circuitBreaker("circuitBreakerB");
    }
    
    // 配置隔离器
    @Bean
    public Bulkhead bulkheadService() {
        BulkheadConfig config = BulkheadConfig.custom()
                .maxConcurrentCalls(10) // 最大并发调用数
                .maxWaitDuration(java.time.Duration.ofMillis(20)) // 等待时间
                .build();
        
        BulkheadRegistry registry = BulkheadRegistry.of(config);
        return registry.bulkhead("bulkheadService");
    }

    // 配置限流器
    @Bean
    public RateLimiter rateLimiterService() {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitRefreshPeriod(java.time.Duration.ofSeconds(2)) // 刷新周期
                .limitForPeriod(5) // 每个周期内允许的请求数
                .timeoutDuration(java.time.Duration.ofSeconds(1)) // 请求等待超时时间
                .build();

        RateLimiterRegistry registry = RateLimiterRegistry.of(config);
        return registry.rateLimiter("rateLimiterService");
    }
    
    // 添加热点参数限流器
    @Bean
    public RateLimiter hotParamRateLimiter() {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitRefreshPeriod(java.time.Duration.ofSeconds(1)) // 每秒刷新
                .limitForPeriod(2) // 每个参数每秒只允许2个请求
                .timeoutDuration(java.time.Duration.ofMillis(500)) // 更短的超时时间
                .build();

        RateLimiterRegistry registry = RateLimiterRegistry.of(config);
        return registry.rateLimiter("hotParamRateLimiter");
    }
    
    // 配置超时处理
    @Bean
    public TimeLimiter timeLimiterService() {
        TimeLimiterConfig config = TimeLimiterConfig.custom()
                .timeoutDuration(java.time.Duration.ofSeconds(1)) // 1秒超时
                .cancelRunningFuture(true) // 超时后取消运行中的任务
                .build();
                
        TimeLimiterRegistry registry = TimeLimiterRegistry.of(config);
        return registry.timeLimiter("timeLimiterService");
    }
    
    // 配置缓存
    @Bean
    public CacheManager cacheManager() {
        // 使用JCache (JSR-107)创建缓存管理器
        CacheManager cacheManager = Caching.getCachingProvider().getCacheManager();
        
        // 创建缓存配置 - 5分钟过期时间
        MutableConfiguration<Object, Object> config = new MutableConfiguration<>()
                .setTypes(Object.class, Object.class)
                .setStoreByValue(false)
                .setExpiryPolicyFactory(CreatedExpiryPolicy.factoryOf(Duration.ONE_MINUTE)); 
        
        // 创建缓存
        cacheManager.createCache("userCache", config);
        cacheManager.createCache("resultsCache", config);
        
        return cacheManager;
    }
    
    @Bean
    public Cache<String, Object> resultsCache(CacheManager cacheManager) {
        javax.cache.Cache<String, Object> jCache = cacheManager.getCache("resultsCache");
        return Cache.of(jCache);
    }
} 