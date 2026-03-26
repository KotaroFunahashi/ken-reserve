package com.coha9nus.kenreserve.config;

import com.coha9nus.kenreserve.domain.user.Role;
import com.coha9nus.kenreserve.domain.user.User;
import com.coha9nus.kenreserve.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;

    @Override
    public void run(ApplicationArguments args) {
        String password = new BCryptPasswordEncoder().encode("password");
        if (userRepository.findByLoginId("admin").isEmpty()) {
            userRepository.save(User.builder().loginId("admin").password(password)
                    .displayName("管理者").role(Role.ADMIN).build());
        }
    }
}
