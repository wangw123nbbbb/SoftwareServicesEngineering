package org.example.Controller;

import org.example.Controller.ConsumerController.ProviderController.User;
import org.example.Service.ProviderClient;
import org.example.Service.ResilienceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/resilience")
public class ResilienceController {

    @Autowired
    private ResilienceService resilienceService;

    @Autowired
    private ProviderClient providerClient;
    
//    // 断路器A测试 - 基于失败率的断路器
//    @GetMapping("/circuit-breaker-a")
//    public String testCircuitBreakerA() {
//        return resilienceService.getHelloWithCircuitBreakerA();
//    }

    @GetMapping("/circuit-breaker-a")
    public ResponseEntity<String> testCircuitBreakerA() {
        return resilienceService.getHelloWithCircuitBreakerA();
    }
    
//    // 断路器B测试 - 基于失败率和慢调用率的断路器
//    @GetMapping("/circuit-breaker-b")
//    public Map<String, Object> testCircuitBreakerB() {
//        return resilienceService.getUsersWithCircuitBreakerB();
//    }
    @GetMapping("/circuit-breaker-b")
    public ResponseEntity<Map<String, Object>> testCircuitBreakerB() {
        return resilienceService.getUsersWithCircuitBreakerB();
    }
    @GetMapping("/force-circuit-breaker")
    public ResponseEntity<String> forceCircuitBreaker() {
        try {
            ResponseEntity result = resilienceService.getHelloWithCircuitBreakerA();
            return result;
        } catch (Exception e) {
            // 重要：返回与异常匹配的HTTP状态码
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("服务调用失败: " + e.getMessage());
        }
    }
    // 隔离器测试
    @GetMapping("/bulkhead/{id}")
    public Map<String, Object> testBulkhead(@PathVariable("id") Long id) {
        return resilienceService.getUserByIdWithBulkhead(id);
    }
    
    // 限流器测试
    @PostMapping("/rate-limiter")
    public Map<String, Object> testRateLimiter(@RequestBody User user) {
        return resilienceService.addUserWithRateLimiter(user);
    }
    
    // 组合容错保护测试
    @PutMapping("/combined-protection/{id}")
    public Map<String, Object> testCombinedProtection(@PathVariable("id") Long id, @RequestBody User user) {
        return resilienceService.updateUserWithCombinedProtection(id, user);
    }
    
    // 并发测试辅助接口 - 同时调用多次接口测试隔离器效果
    @GetMapping("/concurrent-test")
    public Map<String, Object> testConcurrent() {
        Map<String, Object> result = new HashMap<>();
        
        // 记录成功和失败的请求数
        final int[] successCount = {0};
        final int[] failureCount = {0};
        
        // 创建20个线程并发调用
        int threadCount = 30; // 增加线程数量到30
        Thread[] threads = new Thread[threadCount];
        final CountDownLatch startLatch = new CountDownLatch(1); // 用于同步启动
        final CountDownLatch finishLatch = new CountDownLatch(threadCount); // 等待所有线程完成
        
        for (int i = 0; i < threads.length; i++) {
            final long userId = (i % 5) + 1; // 使用1-5的用户ID
            final int index = i;
            threads[i] = new Thread(() -> {
                try {
                    // 等待所有线程准备就绪，同时启动
                    startLatch.await();
                    
                    System.out.println("线程 " + index + " 开始执行请求...");
                    Map<String, Object> response = resilienceService.getUserByIdWithBulkhead(userId);
                    
                    if (response.containsKey("code") && response.get("code").equals(429)) {
                        System.out.println("线程 " + index + " 被隔离器拒绝");
                        synchronized (failureCount) {
                            failureCount[0]++;
                        }
                    } else {
                        System.out.println("线程 " + index + " 成功执行");
                        synchronized (successCount) {
                            successCount[0]++;
                        }
                    }
                } catch (Exception e) {
                    System.out.println("线程 " + index + " 发生异常: " + e.getMessage());
                    synchronized (failureCount) {
                        failureCount[0]++;
                    }
                } finally {
                    finishLatch.countDown();
                }
            });
            threads[i].start();
        }
        
        // 开始执行所有线程
        startLatch.countDown();
        
        try {
            // 等待所有线程完成
            finishLatch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            result.put("error", "等待线程完成时被中断");
        }
        
        result.put("message", "已启动" + threadCount + "个并发请求测试隔离器效果");
        result.put("successCount", successCount[0]);
        result.put("failureCount", failureCount[0]);
        result.put("successRate", String.format("%.2f%%", (successCount[0] * 100.0 / threadCount)));
        return result;
    }
    
    // 压力测试辅助接口 - 快速调用多次接口测试限流器效果
    @GetMapping("/rate-limit-test")
    public Map<String, Object> testRateLimit() {
        Map<String, Object> result = new HashMap<>();
        Map<String, Object> responses = new HashMap<>();
        
        // 记录成功和失败的请求
        final int[] successCount = {0};
        final int[] failureCount = {0};
        
        System.out.println("\n===== 开始限流器测试 =====");
        System.out.println("配置: 2秒内最多允许5个请求通过");
        System.out.println("测试: 连续快速发送15个请求\n");
        
        // 增加请求数量到15个，更明显展示限流效果
        for (int i = 0; i < 15; i++) {
            User user = new User((long)(100 + i), "测试用户" + i, 20 + i);
            try {
                System.out.println("发送第 " + (i+1) + " 个请求...");
                Map<String, Object> response = resilienceService.addUserWithRateLimiter(user);
                
                if (response.containsKey("code") && response.get("code").equals(429)) {
                    System.out.println("第 " + (i+1) + " 个请求被限流!");
                    failureCount[0]++;
                    responses.put("request_" + (i+1), "❌ 被限流: " + response.get("message"));
                } else {
                    System.out.println("第 " + (i+1) + " 个请求成功!");
                    successCount[0]++;
                    responses.put("request_" + (i+1), "✅ 成功: " + response);
                }
                
            } catch (Exception e) {
                System.out.println("第 " + (i+1) + " 个请求异常: " + e.getMessage());
                failureCount[0]++;
                responses.put("request_" + (i+1), "❌ 异常: " + e.getMessage());
            }
            // 打印请求间短暂暂停，不影响限流效果，但让输出更清晰
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        // 等待限流器刷新周期
        System.out.println("\n等待2秒钟，让限流器刷新令牌...");
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // 再次发送请求验证限流器恢复
        System.out.println("\n===== 限流器刷新后测试 =====");
        for (int i = 0; i < 5; i++) {
            User user = new User((long)(200 + i), "刷新后用户" + i, 30 + i);
            try {
                System.out.println("刷新后发送第 " + (i+1) + " 个请求...");
                Map<String, Object> response = resilienceService.addUserWithRateLimiter(user);
                
                if (response.containsKey("code") && response.get("code").equals(429)) {
                    System.out.println("刷新后第 " + (i+1) + " 个请求被限流!");
                    responses.put("refresh_" + (i+1), "❌ 被限流: " + response.get("message"));
                } else {
                    System.out.println("刷新后第 " + (i+1) + " 个请求成功!");
                    responses.put("refresh_" + (i+1), "✅ 成功: " + response);
                }
            } catch (Exception e) {
                System.out.println("刷新后第 " + (i+1) + " 个请求异常: " + e.getMessage());
                responses.put("refresh_" + (i+1), "❌ 异常: " + e.getMessage());
            }
            // 短暂暂停
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        // 添加统计信息
        System.out.println("\n===== 限流器测试结果 =====");
        System.out.println("第一轮测试: 成功请求 " + successCount[0] + " 个, 被限流请求 " + failureCount[0] + " 个");
        System.out.println("成功率: " + String.format("%.2f%%", (successCount[0] * 100.0 / 15)));
        
        result.put("message", "限流器测试完成，共测试20个请求(首轮15个，刷新后5个)");
        result.put("firstRound", new HashMap<String, Object>() {{
            put("total", 15);
            put("success", successCount[0]);
            put("limited", failureCount[0]);
            put("successRate", String.format("%.2f%%", (successCount[0] * 100.0 / 15)));
        }});
        result.put("detail", responses);
        
        return result;
    }

    // 组合保护测试接口 - 一次测试三种保护机制
    @GetMapping("/combined-test")
    public Map<String, Object> testCombinedProtection() {
        Map<String, Object> result = new HashMap<>();
        Map<String, Object> responses = new HashMap<>();
        
        System.out.println("\n===== 开始组合保护测试 =====");
        System.out.println("测试内容: 限流器+隔离器+断路器的组合效果");
        
        // 1. 测试限流效果 (快速发送多个请求)
        System.out.println("\n----- 测试限流效果 -----");
        System.out.println("连续发送8个请求测试限流器...");
        
        for (int i = 0; i < 8; i++) {
            User user = new User((long)(100 + i), "限流测试用户" + i, 25);
            try {
                System.out.println("发送第 " + (i+1) + " 个限流测试请求...");
                Map<String, Object> response = resilienceService.updateUserWithCombinedProtection(1L, user);
                
                if (response.containsKey("code") && response.get("code").equals(429) 
                    && response.get("message").toString().contains("限流器限制")) {
                    System.out.println("第 " + (i+1) + " 个请求被限流!");
                    responses.put("ratelimit_" + (i+1), "❌ 被限流: " + response.get("message"));
                } else {
                    System.out.println("第 " + (i+1) + " 个请求成功!");
                    responses.put("ratelimit_" + (i+1), "✅ 成功: " + response);
                }
            } catch (Exception e) {
                responses.put("ratelimit_" + (i+1), "❌ 异常: " + e.getMessage());
            }
            
            // 添加短暂延迟使输出更清晰
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        // 2. 测试隔离器效果 (并发请求)
        System.out.println("\n----- 测试隔离效果 -----");
        System.out.println("并发发送15个请求测试隔离器...");
        
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch finishLatch = new CountDownLatch(15);
        final Map<String, String> bulkheadResults = new ConcurrentHashMap<>();
        
        for (int i = 0; i < 15; i++) {
            final int index = i;
            final User user = new User(200L + index, "隔离测试用户" + index, 30);
            
            new Thread(() -> {
                try {
                    startLatch.await(); // 等待同时启动
                    
                    System.out.println("并发线程 " + index + " 开始执行...");
                    Map<String, Object> response = resilienceService.updateUserWithCombinedProtection(2L, user);
                    
                    if (response.containsKey("code") && response.get("code").equals(429) 
                        && response.get("message").toString().contains("隔离器限制")) {
                        System.out.println("并发线程 " + index + " 被隔离器拒绝");
                        bulkheadResults.put("bulkhead_" + index, "❌ 被隔离: " + response.get("message"));
                    } else {
                        System.out.println("并发线程 " + index + " 成功执行");
                        bulkheadResults.put("bulkhead_" + index, "✅ 成功: " + response);
                    }
                } catch (Exception e) {
                    bulkheadResults.put("bulkhead_" + index, "❌ 异常: " + e.getMessage());
                } finally {
                    finishLatch.countDown();
                }
            }).start();
        }
        
        // 启动所有线程
        startLatch.countDown();
        
        try {
            finishLatch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // 添加隔离器测试结果
        responses.putAll(bulkheadResults);
        
        // 3. 等待所有测试完成
        result.put("message", "组合保护测试完成");
        result.put("detail", responses);
        
        return result;
    }

    // 1. 热点参数限流测试接口
    @GetMapping("/hot-param/{userId}")
    public Map<String, Object> testHotParamLimit(@PathVariable("userId") Long userId) {
        System.out.println("接收热点参数限流请求，用户ID: " + userId);
        return resilienceService.getUserWithHotParamLimit(userId);
    }
    
    // 2. 结果缓存测试接口
    @GetMapping("/cache/{userId}")
    public Map<String, Object> testCache(@PathVariable("userId") Long userId) {
        System.out.println("接收缓存请求，用户ID: " + userId);
        return resilienceService.getUserWithCache(userId);
    }
    
    // 清除缓存测试接口
    @DeleteMapping("/cache/{userId}")
    public Map<String, Object> clearCache(@PathVariable("userId") Long userId) {
        System.out.println("接收清除缓存请求，用户ID: " + userId);
        return resilienceService.clearUserCache(userId);
    }
    
    // 3. 超时处理测试接口
    @GetMapping("/timeout/{userId}")
    public Map<String, Object> testTimeout(@PathVariable("userId") Long userId) {
        System.out.println("接收超时处理请求，用户ID: " + userId);
        return resilienceService.getUserWithTimeout(userId);
    }
    
    // 4. 综合保护测试接口
    @GetMapping("/all-protections/{userId}")
    public Map<String, Object> testAllProtections(@PathVariable("userId") Long userId) {
        System.out.println("接收综合保护请求，用户ID: " + userId);
        return resilienceService.getUserWithAllProtections(userId);
    }
    
    // 热点参数限流压力测试
    @GetMapping("/hot-param-test/{userId}")
    public Map<String, Object> testHotParamStress(@PathVariable("userId") Long userId) {
        Map<String, Object> result = new HashMap<>();
        Map<String, Object> responses = new HashMap<>();
        
        // 记录成功和失败的请求
        final int[] successCount = {0};
        final int[] failureCount = {0};
        
        System.out.println("\n===== 开始热点参数限流器测试 =====");
        System.out.println("配置: 每秒最多允许2个针对同一参数的请求");
        System.out.println("测试: 连续快速发送10个请求到同一用户ID\n");
        
        // 快速发送10个请求到同一用户ID
        for (int i = 0; i < 10; i++) {
            try {
                System.out.println("发送第 " + (i+1) + " 个请求到用户ID " + userId + "...");
                Map<String, Object> response = resilienceService.getUserWithHotParamLimit(userId);
                
                if (response.containsKey("code") && response.get("code").equals(429)) {
                    System.out.println("第 " + (i+1) + " 个请求被限流!");
                    failureCount[0]++;
                    responses.put("request_" + (i+1), "❌ 被限流: " + response.get("message"));
                } else {
                    System.out.println("第 " + (i+1) + " 个请求成功!");
                    successCount[0]++;
                    responses.put("request_" + (i+1), "✅ 成功: " + response);
                }
                
                // 短暂暂停，让输出更清晰
                Thread.sleep(100);
            } catch (Exception e) {
                System.out.println("第 " + (i+1) + " 个请求异常: " + e.getMessage());
                failureCount[0]++;
                responses.put("request_" + (i+1), "❌ 异常: " + e.getMessage());
            }
        }
        
        result.put("message", "已发送10个请求到用户ID " + userId);
        result.put("successCount", successCount[0]);
        result.put("failureCount", failureCount[0]);
        result.put("responses", responses);
        
        return result;
    }
    
    // 缓存效果测试
    @GetMapping("/cache-test/{userId}")
    public Map<String, Object> testCachePerformance(@PathVariable("userId") Long userId) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 清除缓存
            resilienceService.clearUserCache(userId);
            
            // 第一次调用（无缓存）
            long startTime1 = System.currentTimeMillis();
            Map<String, Object> response1 = resilienceService.getUserWithCache(userId);
            long duration1 = System.currentTimeMillis() - startTime1;
            
            // 第二次调用（有缓存）
            long startTime2 = System.currentTimeMillis();
            Map<String, Object> response2 = resilienceService.getUserWithCache(userId);
            long duration2 = System.currentTimeMillis() - startTime2;
            
            // 再次清除缓存
            resilienceService.clearUserCache(userId);
            
            // 结果
            result.put("message", "缓存性能测试结果");
            result.put("firstCall", Map.of(
                "duration", duration1 + "ms",
                "fromCache", response1.getOrDefault("fromCache", false),
                "response", response1
            ));
            result.put("secondCall", Map.of(
                "duration", duration2 + "ms",
                "fromCache", response2.getOrDefault("fromCache", false),
                "response", response2
            ));
            result.put("improvement", "缓存节省了 " + (duration1 - duration2) + "ms (" + 
                    (duration1 > 0 ? String.format("%.2f", (duration1 - duration2) * 100.0 / duration1) : "0") + "%)");
            
        } catch (Exception e) {
            result.put("error", "测试过程中发生异常: " + e.getMessage());
        }
        
        return result;
    }
    
    // 超时效果测试
    @GetMapping("/timeout-test")
    public Map<String, Object> testTimeoutEffects() {
        Map<String, Object> result = new HashMap<>();
        Map<String, Object> responses = new HashMap<>();
        
        System.out.println("\n===== 开始超时处理测试 =====");
        System.out.println("配置: 请求超时时间为1秒");
        System.out.println("测试: 针对ID=3,6,9的用户（会导致2秒延迟）和其他正常用户\n");
        
        // 测试会超时的用户ID (3,6,9) 和正常的用户ID
        for (long id = 1; id <= 10; id++) {
            try {
                System.out.println("请求用户ID " + id + "...");
                long startTime = System.currentTimeMillis();
                Map<String, Object> response = resilienceService.getUserWithTimeout(id);
                long duration = System.currentTimeMillis() - startTime;
                
                String status;
                if (response.containsKey("code") && response.get("code").equals(408)) {
                    status = "❌ 超时 (" + duration + "ms): " + response.get("message");
                } else {
                    status = "✅ 成功 (" + duration + "ms): " + response;
                }
                
                System.out.println("用户ID " + id + " 结果: " + status);
                responses.put("user_" + id, status);
                
            } catch (Exception e) {
                System.out.println("用户ID " + id + " 异常: " + e.getMessage());
                responses.put("user_" + id, "❌ 异常: " + e.getMessage());
            }
        }
        
        result.put("message", "超时处理测试结果");
        result.put("responses", responses);
        
        return result;
    }
} 