package org.example.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 简易限流过滤器
 * 实际生产环境建议使用Redis或其他分布式方案实现限流
 */
@Component
@Slf4j
public class RateLimiterFilter implements GlobalFilter, Ordered {

    // 存储路径的访问次数，实际应用应使用Redis等分布式存储
    private final Map<String, AtomicInteger> pathCountMap = new ConcurrentHashMap<>();
    
    // 存储路径的最后刷新时间
    private final Map<String, Long> pathLastRefreshMap = new ConcurrentHashMap<>();
    
    // 窗口时间 - 60秒
    private final long TIME_WINDOW = 60 * 1000;
    
    // 默认限流阈值 - 100次/分钟
    private final int DEFAULT_LIMIT = 100;
    
    // 特定路径限流配置
    private final Map<String, Integer> pathLimitMap = new HashMap<>() {{
        put("/provider1/api", 50);   // 每分钟50次
        put("/provider2/api", 80);   // 每分钟80次
    }};

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        
        // 获取当前路径的限流阈值
        int limit = DEFAULT_LIMIT;
        for (Map.Entry<String, Integer> entry : pathLimitMap.entrySet()) {
            if (path.startsWith(entry.getKey())) {
                limit = entry.getValue();
                break;
            }
        }
        
        // 初始化计数器
        if (!pathCountMap.containsKey(path)) {
            pathCountMap.put(path, new AtomicInteger(0));
            pathLastRefreshMap.put(path, System.currentTimeMillis());
        }
        
        // 检查是否需要重置计数器
        long now = System.currentTimeMillis();
        long lastRefresh = pathLastRefreshMap.getOrDefault(path, 0L);
        if (now - lastRefresh > TIME_WINDOW) {
            pathCountMap.get(path).set(0);
            pathLastRefreshMap.put(path, now);
        }
        
        // 递增计数并判断是否超过限流阈值
        int count = pathCountMap.get(path).incrementAndGet();
        if (count > limit) {
            log.warn("请求路径 {} 已达到限流阈值 {}/分钟，当前值: {}", path, limit, count);
            return limitExceeded(exchange);
        }
        
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        // 在认证过滤器之后执行
        return -90;
    }
    
    private Mono<Void> limitExceeded(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().add("Content-Type", "application/json;charset=UTF-8");
        
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("code", HttpStatus.TOO_MANY_REQUESTS.value());
        resultMap.put("message", "请求频率超限，请稍后再试");
        resultMap.put("timestamp", System.currentTimeMillis());
        
        String resultJson = resultMap.toString();
        DataBuffer buffer = response.bufferFactory().wrap(resultJson.getBytes(StandardCharsets.UTF_8));
        
        return response.writeWith(Mono.just(buffer));
    }
} 