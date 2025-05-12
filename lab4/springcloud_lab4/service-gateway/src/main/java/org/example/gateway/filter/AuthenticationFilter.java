package org.example.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 全局权限认证过滤器
 */
@Component
@Slf4j
public class AuthenticationFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        
        log.info("请求路径: {}", path);
        
        // 对于某些公开的API，不需要token验证
        if (path.contains("/public/") || path.contains("/open/")) {
            return chain.filter(exchange);
        }
        
        // 获取token
        String token = request.getHeaders().getFirst("Authorization");
        
        // 这里简单判断token是否存在，实际应用中应该进行更复杂的token验证
        if (token == null || token.isEmpty()) {
            return unauthorized(exchange, "未授权：缺少令牌");
        }
        
        // 假设token必须以"Bearer "开头
        if (!token.startsWith("Bearer ")) {
            return unauthorized(exchange, "未授权：令牌格式错误");
        }
        
        // 在实际应用中，这里应该验证token的合法性，例如JWT验证等
        // 这里仅做演示，使用一个简单的模拟验证
        String actualToken = token.substring(7); // 去除"Bearer "前缀
        if ("invalid-token".equals(actualToken)) {
            return unauthorized(exchange, "未授权：无效的令牌");
        }
        
        log.info("认证成功，令牌: {}", token);
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        // 确保该过滤器在过滤器链的早期执行
        return -100;
    }
    
    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add("Content-Type", "application/json;charset=UTF-8");
        
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("code", HttpStatus.UNAUTHORIZED.value());
        resultMap.put("message", message);
        resultMap.put("timestamp", System.currentTimeMillis());
        
        String resultJson = resultMap.toString();
        DataBuffer buffer = response.bufferFactory().wrap(resultJson.getBytes(StandardCharsets.UTF_8));
        
        log.warn("认证失败: {}", message);
        return response.writeWith(Mono.just(buffer));
    }
} 