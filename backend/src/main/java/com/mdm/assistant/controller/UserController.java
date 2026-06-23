package com.mdm.assistant.controller;

import com.mdm.assistant.entity.User;
import com.mdm.assistant.entity.UserPreferenceEntity;
import com.mdm.assistant.service.UserPreferenceService;
import com.mdm.assistant.service.UserService;
import com.mdm.assistant.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private final UserService userService;
    private final UserPreferenceService userPreferenceService;
    private final JwtUtil jwtUtil;

    public UserController(UserService userService, UserPreferenceService userPreferenceService, JwtUtil jwtUtil) {
        this.userService = userService;
        this.userPreferenceService = userPreferenceService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(
            @RequestParam(required = false) String email,
            @RequestParam String password,
            @RequestParam String nickname,
            @RequestParam(required = false) String phone) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            if ((email == null || email.isEmpty()) && (phone == null || phone.isEmpty())) {
                throw new RuntimeException("邮箱或手机号至少填写一个");
            }
            
            User user = userService.register(email, password, nickname, phone);
            String token = jwtUtil.generateToken(user.getId(), user.getEmail(), user.getNickname());
            
            response.put("success", true);
            response.put("message", "注册成功");
            response.put("token", token);
            response.put("user", buildUserMap(user));
        } catch (RuntimeException e) {
            response.put("success", false);
            response.put("message", e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(
            @RequestParam String identifier,
            @RequestParam String password) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            var userOpt = userService.login(identifier, password);
            
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                String token = jwtUtil.generateToken(user.getId(), user.getEmail(), user.getNickname());
                
                response.put("success", true);
                response.put("message", "登录成功");
                response.put("token", token);
                response.put("user", buildUserMap(user));
            } else {
                response.put("success", false);
                response.put("message", "账号或密码错误");
            }
        } catch (Exception e) {
            log.error("登录异常: ", e);
            response.put("success", false);
            response.put("message", "登录失败：" + e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getUserInfo(HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();
        
        String token = extractToken(request);
        if (token == null || !jwtUtil.validateToken(token)) {
            response.put("success", false);
            response.put("message", "未登录");
            return ResponseEntity.ok(response);
        }
        
        Long userId = jwtUtil.extractUserId(token);
        var userOpt = userService.findById(userId);
        
        if (userOpt.isPresent()) {
            response.put("success", true);
            response.put("user", buildUserMap(userOpt.get()));
        } else {
            response.put("success", false);
            response.put("message", "用户不存在");
        }
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/update-profile")
    public ResponseEntity<Map<String, Object>> updateProfile(
            HttpServletRequest request,
            @RequestParam(required = false) String nickname,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String gender,
            @RequestParam(required = false) String birthday) {
        
        Map<String, Object> response = new HashMap<>();
        
        String token = extractToken(request);
        if (token == null || !jwtUtil.validateToken(token)) {
            response.put("success", false);
            response.put("message", "未登录");
            return ResponseEntity.ok(response);
        }
        
        Long userId = jwtUtil.extractUserId(token);
        
        try {
            LocalDate birthdayDate = null;
            if (birthday != null && !birthday.isEmpty()) {
                birthdayDate = LocalDate.parse(birthday, DateTimeFormatter.ISO_LOCAL_DATE);
            }
            User user = userService.updateProfile(userId, nickname, email, phone, gender, birthdayDate);
            response.put("success", true);
            response.put("message", "更新成功");
            response.put("user", buildUserMap(user));
        } catch (RuntimeException e) {
            response.put("success", false);
            response.put("message", e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/upload-avatar")
    public ResponseEntity<Map<String, Object>> uploadAvatar(
            HttpServletRequest request,
            @RequestParam String avatarData) {
        
        Map<String, Object> response = new HashMap<>();
        
        String token = extractToken(request);
        if (token == null || !jwtUtil.validateToken(token)) {
            response.put("success", false);
            response.put("message", "未登录");
            return ResponseEntity.ok(response);
        }
        
        Long userId = jwtUtil.extractUserId(token);
        
        try {
            User user = userService.updateAvatar(userId, avatarData);
            response.put("success", true);
            response.put("message", "头像更新成功");
            response.put("user", buildUserMap(user));
        } catch (RuntimeException e) {
            response.put("success", false);
            response.put("message", e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/change-password")
    public ResponseEntity<Map<String, Object>> changePassword(
            HttpServletRequest request,
            @RequestParam String oldPassword,
            @RequestParam String newPassword) {
        
        Map<String, Object> response = new HashMap<>();
        
        String token = extractToken(request);
        if (token == null || !jwtUtil.validateToken(token)) {
            response.put("success", false);
            response.put("message", "未登录");
            return ResponseEntity.ok(response);
        }
        
        Long userId = jwtUtil.extractUserId(token);
        
        try {
            userService.changePassword(userId, oldPassword, newPassword);
            response.put("success", true);
            response.put("message", "密码修改成功");
        } catch (RuntimeException e) {
            response.put("success", false);
            response.put("message", e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/delete-account")
    public ResponseEntity<Map<String, Object>> deleteAccount(
            HttpServletRequest request,
            @RequestParam String password) {
        
        Map<String, Object> response = new HashMap<>();
        
        String token = extractToken(request);
        if (token == null || !jwtUtil.validateToken(token)) {
            response.put("success", false);
            response.put("message", "未登录");
            return ResponseEntity.ok(response);
        }
        
        Long userId = jwtUtil.extractUserId(token);
        
        try {
            userService.deleteAccount(userId, password);
            response.put("success", true);
            response.put("message", "账号已注销");
        } catch (RuntimeException e) {
            response.put("success", false);
            response.put("message", e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "退出成功");
        return ResponseEntity.ok(response);
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    private Map<String, Object> buildUserMap(User user) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", user.getId());
        map.put("email", user.getEmail() != null ? user.getEmail() : "");
        map.put("phone", user.getPhone() != null ? user.getPhone() : "");
        map.put("nickname", user.getNickname());
        map.put("gender", user.getGender() != null ? user.getGender() : "");
        map.put("birthday", user.getBirthday() != null ? user.getBirthday().toString() : "");
        map.put("avatarData", user.getAvatarData() != null ? user.getAvatarData() : "");
        return map;
    }

    private Long extractUserIdFromToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            String token = bearerToken.substring(7);
            try {
                if (jwtUtil.validateToken(token)) {
                    return jwtUtil.extractUserId(token);
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    @GetMapping("/preference")
    public ResponseEntity<Map<String, Object>> getPreference(HttpServletRequest request) {
        Long userId = extractUserIdFromToken(request);
        Map<String, Object> result = new HashMap<>();
        if (userId == null) {
            result.put("success", false);
            result.put("message", "未登录");
            return ResponseEntity.ok(result);
        }
        UserPreferenceEntity pref = userPreferenceService.getOrCreate(userId);
        Map<String, Object> autoPrefs = userPreferenceService.calculateAutoPreferences(userId);
        result.put("success", true);
        result.put("preferredBrands", pref.getPreferredBrands() != null ? pref.getPreferredBrands() : "");
        result.put("budgetMin", pref.getBudgetMin() != null ? pref.getBudgetMin() : "");
        result.put("budgetMax", pref.getBudgetMax() != null ? pref.getBudgetMax() : "");
        result.put("primaryUse", pref.getPrimaryUse() != null ? pref.getPrimaryUse() : "");
        result.put("autoBrand", autoPrefs.getOrDefault("autoBrand", ""));
        result.put("autoBudgetMin", autoPrefs.getOrDefault("autoBudgetMin", ""));
        result.put("autoBudgetMax", autoPrefs.getOrDefault("autoBudgetMax", ""));
        return ResponseEntity.ok(result);
    }

    @PostMapping("/preference")
    public ResponseEntity<Map<String, Object>> savePreference(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        Long userId = extractUserIdFromToken(request);
        Map<String, Object> result = new HashMap<>();
        if (userId == null) {
            result.put("success", false);
            result.put("message", "未登录");
            return ResponseEntity.ok(result);
        }
        String brands = (String) body.getOrDefault("preferredBrands", "");
        Integer budgetMin = body.get("budgetMin") != null && !body.get("budgetMin").toString().isEmpty()
                ? Integer.parseInt(body.get("budgetMin").toString()) : null;
        Integer budgetMax = body.get("budgetMax") != null && !body.get("budgetMax").toString().isEmpty()
                ? Integer.parseInt(body.get("budgetMax").toString()) : null;
        String primaryUse = (String) body.getOrDefault("primaryUse", "");

        userPreferenceService.save(userId,
                brands.isEmpty() ? null : brands,
                budgetMin, budgetMax,
                primaryUse.isEmpty() ? null : primaryUse);

        result.put("success", true);
        result.put("message", "保存成功");
        return ResponseEntity.ok(result);
    }
}