package com.coha9nus.kenreserve.domain.user;

import com.coha9nus.kenreserve.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void ユーザーを保存してユーザー名で検索できる() {
        userRepository.save(User.builder()
                .username("student01")
                .password("{noop}password")
                .displayName("テスト生徒")
                .role(Role.STUDENT)
                .build());

        assertThat(userRepository.findByUsername("student01"))
                .isPresent()
                .get()
                .extracting(User::getDisplayName, User::getRole)
                .containsExactly("テスト生徒", Role.STUDENT);
    }

    @Test
    void ユーザー名が重複すると保存に失敗する() {
        userRepository.saveAndFlush(User.builder()
                .username("duplicate")
                .password("{noop}p")
                .displayName("初回")
                .role(Role.STUDENT)
                .build());

        assertThatThrownBy(() -> userRepository.saveAndFlush(User.builder()
                .username("duplicate")
                .password("{noop}p")
                .displayName("重複")
                .role(Role.STUDENT)
                .build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
