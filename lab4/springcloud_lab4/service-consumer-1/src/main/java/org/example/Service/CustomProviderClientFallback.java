package org.example.Service;

import org.example.Controller.ConsumerController.ProviderController.User;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class CustomProviderClientFallback implements CustomProviderClient {
    
    @Override
    public String callHello() {
        return "自定义服务降级响应: Hello服务暂时不可用";
    }
    
    @Override
    public Map<String, Object> getUsers() {
        Map<String, Object> fallbackResponse = new HashMap<>();
        fallbackResponse.put("code", 500);
        fallbackResponse.put("message", "自定义服务降级响应: 获取用户列表服务暂时不可用");
        return fallbackResponse;
    }
    
    @Override
    public Map<String, Object> getUserById(Long id) {
        Map<String, Object> fallbackResponse = new HashMap<>();
        fallbackResponse.put("code", 500);
        fallbackResponse.put("message", "自定义服务降级响应: 获取用户(ID:" + id + ")服务暂时不可用");
        return fallbackResponse;
    }
    
    @Override
    public Map<String, Object> addUser(User user) {
        Map<String, Object> fallbackResponse = new HashMap<>();
        fallbackResponse.put("code", 500);
        fallbackResponse.put("message", "自定义服务降级响应: 添加用户服务暂时不可用");
        return fallbackResponse;
    }
    
    @Override
    public Map<String, Object> updateUser(Long id, User user) {
        Map<String, Object> fallbackResponse = new HashMap<>();
        fallbackResponse.put("code", 500);
        fallbackResponse.put("message", "自定义服务降级响应: 更新用户(ID:" + id + ")服务暂时不可用");
        return fallbackResponse;
    }
    
    @Override
    public Map<String, Object> deleteUser(Long id) {
        Map<String, Object> fallbackResponse = new HashMap<>();
        fallbackResponse.put("code", 500);
        fallbackResponse.put("message", "自定义服务降级响应: 删除用户(ID:" + id + ")服务暂时不可用");
        return fallbackResponse;
    }
} 