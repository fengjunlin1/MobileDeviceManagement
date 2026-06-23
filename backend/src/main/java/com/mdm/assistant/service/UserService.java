package com.mdm.assistant.service;

import com.mdm.assistant.entity.User;
import com.mdm.assistant.repository.ChatHistoryRepository;
import com.mdm.assistant.repository.ChatMemoryRepository;
import com.mdm.assistant.repository.FavoriteDeviceRepository;
import com.mdm.assistant.repository.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final ChatHistoryRepository chatHistoryRepository;
    private final FavoriteDeviceRepository favoriteDeviceRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository,
                       ChatHistoryRepository chatHistoryRepository,
                       FavoriteDeviceRepository favoriteDeviceRepository,
                       BCryptPasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.chatHistoryRepository = chatHistoryRepository;
        this.favoriteDeviceRepository = favoriteDeviceRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User register(String email, String password, String nickname) {
        return register(email, password, nickname, null);
    }

    public User register(String email, String password, String nickname, String phone) {
        if (email != null && !email.isEmpty() && userRepository.existsByEmail(email)) {
            throw new RuntimeException("邮箱已被注册");
        }
        if (phone != null && !phone.isEmpty() && userRepository.existsByPhone(phone)) {
            throw new RuntimeException("手机号已被注册");
        }
        
        User user = User.builder()
                .email(email)
                .phone(phone)
                .password(passwordEncoder.encode(password))
                .nickname(nickname)
                .build();
        
        return userRepository.save(user);
    }

    public Optional<User> login(String identifier, String password) {
        Optional<User> user = userRepository.findByEmail(identifier);
        if (user.isEmpty()) {
            user = userRepository.findByPhone(identifier);
        }
        
        if (user.isPresent() && passwordEncoder.matches(password, user.get().getPassword())) {
            return user;
        }
        
        return Optional.empty();
    }

    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public User updateProfile(Long userId, String nickname, String email, String phone,
                               String gender, LocalDate birthday) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        if (nickname != null && !nickname.isEmpty()) {
            user.setNickname(nickname);
        }
        if (email != null) {
            if (email.isEmpty()) {
                user.setEmail(null);
            } else {
                user.setEmail(email);
            }
        }
        if (phone != null) {
            if (phone.isEmpty()) {
                user.setPhone(null);
            } else {
                user.setPhone(phone);
            }
        }
        if (gender != null) {
            if (gender.isEmpty()) {
                user.setGender(null);
            } else {
                user.setGender(gender);
            }
        }
        user.setBirthday(birthday);

        return userRepository.save(user);
    }

    public User updateAvatar(Long userId, String avatarData) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        user.setAvatarData(avatarData);
        return userRepository.save(user);
    }

    public void changePassword(Long userId, String oldPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new RuntimeException("旧密码不正确");
        }

        if (newPassword == null || newPassword.length() < 6) {
            throw new RuntimeException("新密码长度不能少于6位");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    @Transactional
    public void deleteAccount(Long userId, String password) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new RuntimeException("密码不正确，无法注销账号");
        }

        chatHistoryRepository.deleteByUserId(userId);
        favoriteDeviceRepository.deleteByUserId(String.valueOf(userId));
        userRepository.delete(user);
    }
}