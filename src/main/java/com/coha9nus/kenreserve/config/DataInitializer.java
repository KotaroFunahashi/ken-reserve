package com.coha9nus.kenreserve.config;

import com.coha9nus.kenreserve.domain.user.Role;
import com.coha9nus.kenreserve.domain.user.User;
import com.coha9nus.kenreserve.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.data-initializer.admin-password}")
    private String adminPassword;

    @Override
    public void run(ApplicationArguments args) {
        try {
            String password = passwordEncoder.encode(adminPassword);
            userRepository.save(User.builder().loginId("admin").password(password)
                    .displayName("管理者").role(Role.ADMIN).build());
        } catch (DataIntegrityViolationException e) {
            // 別インスタンスが先に挿入済みの場合は正常扱い
        }
    }
}
