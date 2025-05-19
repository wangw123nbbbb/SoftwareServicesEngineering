package org.example.gateway.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.cloud.gateway.filter.factory.RetryGatewayFilterFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 容错机制配置
 */
@Configuration
@Slf4j
public class ResilienceConfig {
    
    /**
     * 配置断路器工厂
     */
    @Bean
    public Customizer<ReactiveResilience4JCircuitBreakerFactory> defaultCustomizer() {
        return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                .circuitBreakerConfig(CircuitBreakerConfig.custom()
                        // 滑动窗口大小
                        .slidingWindowSize(10)
                        // 最小调用次数，断路器计算失败率之前所需的最小调用数
                        .minimumNumberOfCalls(5)
                        // 故障率阈值，超过此阈值断路器将打开
                        .failureRateThreshold(50.0f)
                        // 断路器打开状态的持续时间，之后状态变为半开
                        .waitDurationInOpenState(Duration.ofSeconds(5))
                        // 半开状态允许的调用次数
                        .permittedNumberOfCallsInHalfOpenState(3)
                        // 当调用失败时记录为异常
                        .recordExceptions(Exception.class)
                        .build())
                // 超时配置
                .timeLimiterConfig(TimeLimiterConfig.custom()
                        .timeoutDuration(Duration.ofSeconds(3))
                        .build())
                .build());
    }
    
    /**
     * 配置重试机制
     * 在Spring Cloud Gateway 2024.0.1版本中，RetryConfig API已更改
     */
    @Bean
    public RetryGatewayFilterFactory.RetryConfig retryConfig() {
        RetryGatewayFilterFactory.RetryConfig retryConfig = new RetryGatewayFilterFactory.RetryConfig();
        // 设置重试次数
        retryConfig.setRetries(3);
        // 设置重试状态码
        retryConfig.setStatuses(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
        // 设置重试间隔（由于API变化，可能需要采用其他方式配置backoff）
        
        return retryConfig;
    }
} 