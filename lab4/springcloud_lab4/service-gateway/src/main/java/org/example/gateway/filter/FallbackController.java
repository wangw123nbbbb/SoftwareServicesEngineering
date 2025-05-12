package org.example.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * 熔断降级处理控制器
 */
@RestController
@Slf4j
public class FallbackController {

    /**
     * 全局熔断降级处理
     * 当服务不可用时，返回友好提示
     */
    @RequestMapping("/fallback")
    public Mono<ResponseEntity<Map<String, Object>>> fallback() {
        log.error("触发熔断降级");
        Map<String, Object> result = new HashMap<>();
        result.put("code", HttpStatus.SERVICE_UNAVAILABLE.value());
        result.put("message", "服务暂时不可用，请稍后再试");
        result.put("timestamp", System.currentTimeMillis());
        
        return Mono.just(new ResponseEntity<>(result, HttpStatus.SERVICE_UNAVAILABLE));
    }
} 