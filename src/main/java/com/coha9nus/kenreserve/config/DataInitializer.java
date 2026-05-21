package com.coha9nus.kenreserve.config;

import com.coha9nus.kenreserve.domain.user.Role;
import com.coha9nus.kenreserve.domain.user.User;
import com.coha9nus.kenreserve.domain.user.UserRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.data-initializer.admin-password}")
    private String adminPassword;

    @Override
    public void run(ApplicationArguments args) {
        // APサーバ冗長化で初期管理ユーザ作成が競合する可能性があるが、単一サーバ前提のため考慮不要
        if (userRepository.findByLoginId("admin").isPresent()) {
            return;
        }
        String password = passwordEncoder.encode(adminPassword);
        userRepository.save(User.builder().loginId("admin").password(password).displayName("管理者")
                .role(Role.ADMIN).build());

        if (userRepository.findByLoginId("tutor").isEmpty()) {
            userRepository.save(User.builder().loginId("tutor").password(password)
                    .displayName("けんぼう先生").role(Role.TUTOR).build());
        }
        if (userRepository.findByLoginId("student").isEmpty()) {
            userRepository.save(User.builder().loginId("student").password(password)
                    .displayName("生徒A").role(Role.STUDENT).build());
        }
    }
}
