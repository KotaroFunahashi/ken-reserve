package com.coha9nus.kenreserve.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;

import com.coha9nus.kenreserve.config.LoginUser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    UserRepository userRepository;

    @Mock
    PasswordEncoder passwordEncoder;

    @InjectMocks
    UserService service;

    LoginUser adminLogin;
    LoginUser tutorLogin;
    LoginUser studentLogin;

    User admin;
    User tutor;
    User student;

    @BeforeEach
    void setUp() {
        admin = User.builder().id(1L).loginId("admin").password("x").displayName("管理者")
                .role(Role.ADMIN).build();
        tutor = User.builder().id(2L).loginId("tutor").password("x").displayName("先生")
                .role(Role.TUTOR).build();
        student = User.builder().id(3L).loginId("student").password("x").displayName("生徒A")
                .role(Role.STUDENT).build();

        adminLogin = new LoginUser(1L, "admin", "x", "管理者", Role.ADMIN);
        tutorLogin = new LoginUser(2L, "tutor", "x", "先生", Role.TUTOR);
        studentLogin = new LoginUser(3L, "student", "x", "生徒A", Role.STUDENT);
    }

    // ==================== 閲覧権限 ====================

    @Nested
    class GetVisibleUsersTest {

        @Test
        void ADMINは全ユーザーを取得できる() {
            given(userRepository.findAll()).willReturn(List.of(admin, tutor, student));

            List<UserDto> result = service.getVisibleUsers(adminLogin);

            assertThat(result).hasSize(3);
        }

        @Test
        void TUTORはSTUDENT一覧のみ取得できる() {
            given(userRepository.findByRole(Role.STUDENT)).willReturn(List.of(student));

            List<UserDto> result = service.getVisibleUsers(tutorLogin);

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().role()).isEqualTo(Role.STUDENT);
        }

        @Test
        void STUDENTは自分と講師のみ取得できる() {
            given(userRepository.findByRole(Role.TUTOR)).willReturn(List.of(tutor));
            given(userRepository.findById(3L)).willReturn(Optional.of(student));

            List<UserDto> result = service.getVisibleUsers(studentLogin);

            assertThat(result).hasSize(2);
            assertThat(result).extracting(UserDto::role).containsExactlyInAnyOrder(Role.TUTOR,
                    Role.STUDENT);
        }
    }

    // ==================== 作成権限 ====================

    @Nested
    class CreateUserTest {

        @Test
        void ADMINは全ロールのユーザーを作成できる() {
            UserForm form = new UserForm("new_tutor", "pass", "新先生", Role.TUTOR);
            given(userRepository.findByLoginId("new_tutor")).willReturn(Optional.empty());
            given(passwordEncoder.encode(anyString())).willReturn("encoded");
            given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

            UserDto result = service.createUser(form, adminLogin);

            assertThat(result.role()).isEqualTo(Role.TUTOR);
            assertThat(result.displayName()).isEqualTo("新先生");
        }

        @Test
        void TUTORはSTUDENTを作成できる() {
            UserForm form = new UserForm("new_st", "pass", "新生徒", Role.STUDENT);
            given(userRepository.findByLoginId("new_st")).willReturn(Optional.empty());
            given(passwordEncoder.encode(anyString())).willReturn("encoded");
            given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

            UserDto result = service.createUser(form, tutorLogin);

            assertThat(result.role()).isEqualTo(Role.STUDENT);
        }

        @Test
        void TUTORがTUTORを作成しようとすると例外() {
            UserForm form = new UserForm("t2", "pass", "先生2", Role.TUTOR);

            assertThatThrownBy(() -> service.createUser(form, tutorLogin))
                    .isInstanceOf(SecurityException.class);
        }

        @Test
        void TUTORがADMINを作成しようとすると例外() {
            UserForm form = new UserForm("a2", "pass", "管理者2", Role.ADMIN);

            assertThatThrownBy(() -> service.createUser(form, tutorLogin))
                    .isInstanceOf(SecurityException.class);
        }

        @Test
        void STUDENTがユーザーを作成しようとすると例外() {
            UserForm form = new UserForm("s2", "pass", "生徒2", Role.STUDENT);

            assertThatThrownBy(() -> service.createUser(form, studentLogin))
                    .isInstanceOf(SecurityException.class);
        }

        @Test
        void 重複ログインIDは例外() {
            UserForm form = new UserForm("admin", "pass", "重複", Role.STUDENT);
            given(userRepository.findByLoginId("admin")).willReturn(Optional.of(admin));

            assertThatThrownBy(() -> service.createUser(form, adminLogin))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("既に使用されています");
        }

        @Test
        void パスワード空の新規作成は例外() {
            UserForm form = new UserForm("new", "", "新規", Role.STUDENT);
            given(userRepository.findByLoginId("new")).willReturn(Optional.empty());

            assertThatThrownBy(() -> service.createUser(form, adminLogin))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("パスワード");
        }
    }

    // ==================== 編集権限 ====================

    @Nested
    class UpdateUserTest {

        @Test
        void ADMINは他ユーザーの情報を更新できる() {
            UserForm form = new UserForm("student", null, "生徒B", Role.STUDENT);
            given(userRepository.findById(3L)).willReturn(Optional.of(student));
            given(userRepository.findByLoginId("student")).willReturn(Optional.of(student));

            UserDto result = service.updateUser(3L, form, adminLogin);

            assertThat(result.displayName()).isEqualTo("生徒B");
        }

        @Test
        void TUTORはSTUDENTの情報を更新できる() {
            UserForm form = new UserForm("student", null, "生徒B", Role.STUDENT);
            given(userRepository.findById(3L)).willReturn(Optional.of(student));
            given(userRepository.findByLoginId("student")).willReturn(Optional.of(student));

            UserDto result = service.updateUser(3L, form, tutorLogin);

            assertThat(result.displayName()).isEqualTo("生徒B");
        }

        @Test
        void TUTORがTUTORを更新しようとすると例外() {
            User otherTutor = User.builder().id(4L).loginId("tutor2").password("x")
                    .displayName("先生2").role(Role.TUTOR).build();
            UserForm form = new UserForm("tutor2", null, "先生2改", Role.TUTOR);
            given(userRepository.findById(4L)).willReturn(Optional.of(otherTutor));

            assertThatThrownBy(() -> service.updateUser(4L, form, tutorLogin))
                    .isInstanceOf(SecurityException.class);
        }

        @Test
        void STUDENTは自分自身を更新できる() {
            UserForm form = new UserForm("student", "newpass", "生徒A改", Role.STUDENT);
            given(userRepository.findById(3L)).willReturn(Optional.of(student));
            given(userRepository.findByLoginId("student")).willReturn(Optional.of(student));
            given(passwordEncoder.encode("newpass")).willReturn("encoded");

            UserDto result = service.updateUser(3L, form, studentLogin);

            assertThat(result.displayName()).isEqualTo("生徒A改");
        }

        @Test
        void STUDENTは他人を更新できない() {
            User otherStudent = User.builder().id(5L).loginId("st2").password("x")
                    .displayName("生徒B").role(Role.STUDENT).build();
            UserForm form = new UserForm("st2", null, "変更", Role.STUDENT);
            given(userRepository.findById(5L)).willReturn(Optional.of(otherStudent));

            assertThatThrownBy(() -> service.updateUser(5L, form, studentLogin))
                    .isInstanceOf(SecurityException.class);
        }

        @Test
        void パスワード空なら更新しない() {
            UserForm form = new UserForm("student", "", "生徒A改", Role.STUDENT);
            given(userRepository.findById(3L)).willReturn(Optional.of(student));
            given(userRepository.findByLoginId("student")).willReturn(Optional.of(student));

            service.updateUser(3L, form, adminLogin);

            assertThat(student.getPassword()).isEqualTo("x"); // 変更されていない
        }

        @Test
        void ADMINのみロール変更可() {
            UserForm form = new UserForm("student", null, "生徒A", Role.TUTOR);
            given(userRepository.findById(3L)).willReturn(Optional.of(student));
            given(userRepository.findByLoginId("student")).willReturn(Optional.of(student));

            service.updateUser(3L, form, adminLogin);

            assertThat(student.getRole()).isEqualTo(Role.TUTOR);
        }

        @Test
        void TUTOR経由ではロール変更されない() {
            UserForm form = new UserForm("student", null, "生徒A", Role.STUDENT);
            given(userRepository.findById(3L)).willReturn(Optional.of(student));
            given(userRepository.findByLoginId("student")).willReturn(Optional.of(student));

            service.updateUser(3L, form, tutorLogin);

            assertThat(student.getRole()).isEqualTo(Role.STUDENT);
        }
    }

    // ==================== 削除 ====================

    @Nested
    class DeleteUserTest {

        @Test
        void ADMINはユーザーを削除できる() {
            given(userRepository.findById(3L)).willReturn(Optional.of(student));

            service.deleteUser(3L, adminLogin);

            verify(userRepository).delete(student);
        }

        @Test
        void TUTORはSTUDENTを削除できる() {
            given(userRepository.findById(3L)).willReturn(Optional.of(student));

            service.deleteUser(3L, tutorLogin);

            verify(userRepository).delete(student);
        }

        @Test
        void TUTORはTUTORを削除できない() {
            User otherTutor = User.builder().id(4L).loginId("tutor2").password("x")
                    .displayName("先生2").role(Role.TUTOR).build();
            given(userRepository.findById(4L)).willReturn(Optional.of(otherTutor));

            assertThatThrownBy(() -> service.deleteUser(4L, tutorLogin))
                    .isInstanceOf(SecurityException.class);
        }

        @Test
        void 自分自身は削除できない() {
            given(userRepository.findById(1L)).willReturn(Optional.of(admin));

            assertThatThrownBy(() -> service.deleteUser(1L, adminLogin))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("自分自身");
        }
    }
}
