package com.coha9nus.kenreserve.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;
import java.util.Set;

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
            assertThat(result).extracting(UserDto::role)
                    .containsExactlyInAnyOrder(Role.ADMIN, Role.TUTOR, Role.STUDENT);
        }

        @Test
        void TUTORは自分と担当生徒のみ取得できる() {
            User student2 = User.builder().id(4L).loginId("student2").password("x")
                    .displayName("生徒B").role(Role.STUDENT).tutors(Set.of(tutor)).build();
            given(userRepository.findById(2L)).willReturn(Optional.of(tutor));
            given(userRepository.findByTutors_Id(2L)).willReturn(List.of(student2));

            List<UserDto> result = service.getVisibleUsers(tutorLogin);

            assertThat(result).hasSize(2);
            assertThat(result).extracting(UserDto::id).containsExactlyInAnyOrder(2L, 4L);
        }

        @Test
        void TUTORのリストにTUTOR自身が含まれる() {
            given(userRepository.findById(2L)).willReturn(Optional.of(tutor));
            given(userRepository.findByTutors_Id(2L)).willReturn(List.of(student));

            List<UserDto> result = service.getVisibleUsers(tutorLogin);

            assertThat(result).extracting(UserDto::id).contains(tutorLogin.id());
        }

        @Test
        void TUTORは担当生徒なしの場合は自分のみ取得できる() {
            given(userRepository.findById(2L)).willReturn(Optional.of(tutor));
            given(userRepository.findByTutors_Id(2L)).willReturn(List.of());

            List<UserDto> result = service.getVisibleUsers(tutorLogin);

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().id()).isEqualTo(2L);
        }

        @Test
        void STUDENTは自分と担当講師のみ取得できる() {
            User studentWithTutor = User.builder().id(3L).loginId("student").password("x")
                    .displayName("生徒A").role(Role.STUDENT).tutors(Set.of(tutor)).build();
            given(userRepository.findById(3L)).willReturn(Optional.of(studentWithTutor));

            List<UserDto> result = service.getVisibleUsers(studentLogin);

            assertThat(result).hasSize(2);
            assertThat(result).extracting(UserDto::role)
                    .containsExactlyInAnyOrder(Role.TUTOR, Role.STUDENT);
            assertThat(result).extracting(UserDto::id)
                    .containsExactlyInAnyOrder(2L, 3L);
        }

        @Test
        void STUDENTは担当外の講師は取得できない() {
            User otherTutor = User.builder().id(5L).loginId("tutor2").password("x")
                    .displayName("先生2").role(Role.TUTOR).build();
            // student の担当講師は tutor(id=2) のみ。otherTutor(id=5) は担当外
            User studentWithTutor = User.builder().id(3L).loginId("student").password("x")
                    .displayName("生徒A").role(Role.STUDENT).tutors(Set.of(tutor)).build();
            given(userRepository.findById(3L)).willReturn(Optional.of(studentWithTutor));

            List<UserDto> result = service.getVisibleUsers(studentLogin);

            assertThat(result).extracting(UserDto::id).doesNotContain(otherTutor.getId());
        }

        @Test
        void STUDENTは複数の担当講師を取得できる() {
            User tutor2 = User.builder().id(5L).loginId("tutor2").password("x")
                    .displayName("先生2").role(Role.TUTOR).build();
            User studentWithTutors = User.builder().id(3L).loginId("student").password("x")
                    .displayName("生徒A").role(Role.STUDENT).tutors(Set.of(tutor, tutor2)).build();
            given(userRepository.findById(3L)).willReturn(Optional.of(studentWithTutors));

            List<UserDto> result = service.getVisibleUsers(studentLogin);

            assertThat(result).hasSize(3); // tutor + tutor2 + self
            assertThat(result).extracting(UserDto::id)
                    .containsExactlyInAnyOrder(2L, 5L, 3L);
        }

        @Test
        void STUDENTは担当講師なしの場合は自分のみ取得できる() {
            given(userRepository.findById(3L)).willReturn(Optional.of(student));

            List<UserDto> result = service.getVisibleUsers(studentLogin);

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().id()).isEqualTo(3L);
            assertThat(result.getFirst().role()).isEqualTo(Role.STUDENT);
        }
    }

    // ==================== 作成権限 ====================

    @Nested
    class GetAvailableTutorsTest {

        @Test
        void ADMINは全TUTORを取得できる() {
            given(userRepository.findByRole(Role.TUTOR)).willReturn(List.of(tutor));

            List<UserDto> result = service.getAvailableTutors(adminLogin);

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().role()).isEqualTo(Role.TUTOR);
        }

        @Test
        void TUTORは自分のみ取得できる() {
            given(userRepository.findById(2L)).willReturn(Optional.of(tutor));

            List<UserDto> result = service.getAvailableTutors(tutorLogin);

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().id()).isEqualTo(2L);
        }

        @Test
        void STUDENTは空リストを取得する() {
            List<UserDto> result = service.getAvailableTutors(studentLogin);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    class CreateUserTest {

        @Test
        void ADMINは全ロールのユーザーを作成できる() {
            UserForm form = new UserForm("new_tutor", "pass", "新先生", Role.TUTOR, null);
            given(userRepository.findByLoginId("new_tutor")).willReturn(Optional.empty());
            given(passwordEncoder.encode(anyString())).willReturn("encoded");
            given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

            UserDto result = service.createUser(form, adminLogin);

            assertThat(result.role()).isEqualTo(Role.TUTOR);
            assertThat(result.displayName()).isEqualTo("新先生");
        }

        @Test
        void TUTORはSTUDENTを作成できる() {
            UserForm form = new UserForm("new_st", "pass", "新生徒", Role.STUDENT, null);
            given(userRepository.findByLoginId("new_st")).willReturn(Optional.empty());
            given(passwordEncoder.encode(anyString())).willReturn("encoded");
            given(userRepository.findById(2L)).willReturn(Optional.of(tutor));
            given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

            UserDto result = service.createUser(form, tutorLogin);

            assertThat(result.role()).isEqualTo(Role.STUDENT);
            assertThat(result.tutorIds()).containsExactly(2L);
        }

        @Test
        void TUTORがTUTORを作成しようとすると例外() {
            UserForm form = new UserForm("t2", "pass", "先生2", Role.TUTOR, null);

            assertThatThrownBy(() -> service.createUser(form, tutorLogin))
                    .isInstanceOf(SecurityException.class);
        }

        @Test
        void TUTORがADMINを作成しようとすると例外() {
            UserForm form = new UserForm("a2", "pass", "管理聧2", Role.ADMIN, null);

            assertThatThrownBy(() -> service.createUser(form, tutorLogin))
                    .isInstanceOf(SecurityException.class);
        }

        @Test
        void STUDENTがユーザーを作成しようとすると例外() {
            UserForm form = new UserForm("s2", "pass", "生徒2", Role.STUDENT, null);

            assertThatThrownBy(() -> service.createUser(form, studentLogin))
                    .isInstanceOf(SecurityException.class);
        }

        @Test
        void 重複ログインIDは例外() {
            UserForm form = new UserForm("admin", "pass", "重複", Role.STUDENT, null);
            given(userRepository.findByLoginId("admin")).willReturn(Optional.of(admin));

            assertThatThrownBy(() -> service.createUser(form, adminLogin))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("既に使用されています");
        }

        @Test
        void パスワード空の新規作成は例外() {
            UserForm form = new UserForm("new", "", "新規", Role.STUDENT, null);
            given(userRepository.findByLoginId("new")).willReturn(Optional.empty());

            assertThatThrownBy(() -> service.createUser(form, adminLogin))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("パスワード");
        }
        @Test
        void STUDENTを担当講師付きで作成できる() {
            UserForm form = new UserForm("new_st", "pass", "新生徒", Role.STUDENT, List.of(2L));
            given(userRepository.findByLoginId("new_st")).willReturn(Optional.empty());
            given(passwordEncoder.encode(anyString())).willReturn("encoded");
            given(userRepository.findAllById(List.of(2L))).willReturn(List.of(tutor));
            given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

            UserDto result = service.createUser(form, adminLogin);

            assertThat(result.role()).isEqualTo(Role.STUDENT);
            assertThat(result.tutorIds()).containsExactly(2L);
        }    }

    // ==================== 編集権限 ====================

    @Nested
    class UpdateUserTest {

        @Test
        void ADMINは他ユーザーの情報を更新できる() {
            UserForm form = new UserForm("student", null, "生徒B", Role.STUDENT, null);
            given(userRepository.findById(3L)).willReturn(Optional.of(student));
            given(userRepository.findByLoginId("student")).willReturn(Optional.of(student));

            UserDto result = service.updateUser(3L, form, adminLogin);

            assertThat(result.displayName()).isEqualTo("生徒B");
        }

        @Test
        void TUTORはSTUDENTの情報を更新できない() {
            UserForm form = new UserForm("student", null, "生徒B", Role.STUDENT, null);
            given(userRepository.findById(3L)).willReturn(Optional.of(student));

            assertThatThrownBy(() -> service.updateUser(3L, form, tutorLogin))
                    .isInstanceOf(SecurityException.class);
        }

        @Test
        void TUTORがTUTORを更新しようとすると例外() {
            User otherTutor = User.builder().id(4L).loginId("tutor2").password("x")
                    .displayName("先生2").role(Role.TUTOR).build();
            UserForm form = new UserForm("tutor2", null, "先生2改", Role.TUTOR, null);
            given(userRepository.findById(4L)).willReturn(Optional.of(otherTutor));

            assertThatThrownBy(() -> service.updateUser(4L, form, tutorLogin))
                    .isInstanceOf(SecurityException.class);
        }

        @Test
        void TUTORは自分自身を更新できる() {
            UserForm form = new UserForm("tutor", "newpass", "先生改", Role.TUTOR, null);
            given(userRepository.findById(2L)).willReturn(Optional.of(tutor));
            given(userRepository.findByLoginId("tutor")).willReturn(Optional.of(tutor));
            given(passwordEncoder.encode("newpass")).willReturn("encoded");

            UserDto result = service.updateUser(2L, form, tutorLogin);

            assertThat(result.displayName()).isEqualTo("先生改");
        }

        @Test
        void STUDENTは自分自身を更新できる() {
            UserForm form = new UserForm("student", "newpass", "生徒A改", Role.STUDENT, null);
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
            UserForm form = new UserForm("st2", null, "変更", Role.STUDENT, null);
            given(userRepository.findById(5L)).willReturn(Optional.of(otherStudent));

            assertThatThrownBy(() -> service.updateUser(5L, form, studentLogin))
                    .isInstanceOf(SecurityException.class);
        }

        @Test
        void パスワード空なら更新しない() {
            UserForm form = new UserForm("student", "", "生徒A改", Role.STUDENT, null);
            given(userRepository.findById(3L)).willReturn(Optional.of(student));
            given(userRepository.findByLoginId("student")).willReturn(Optional.of(student));

            service.updateUser(3L, form, adminLogin);

            assertThat(student.getPassword()).isEqualTo("x"); // 変更されていない
        }

        @Test
        void ADMINのみロール変更可() {
            UserForm form = new UserForm("student", null, "生徒A", Role.TUTOR, null);
            given(userRepository.findById(3L)).willReturn(Optional.of(student));
            given(userRepository.findByLoginId("student")).willReturn(Optional.of(student));

            service.updateUser(3L, form, adminLogin);

            assertThat(student.getRole()).isEqualTo(Role.TUTOR);
        }

        @Test
        void TUTOR経由ではロール変更されない() {
            UserForm form = new UserForm("student", null, "生徒A", Role.STUDENT, null);
            given(userRepository.findById(3L)).willReturn(Optional.of(student));

            assertThatThrownBy(() -> service.updateUser(3L, form, tutorLogin))
                    .isInstanceOf(SecurityException.class);
        }
        @Test
        void STUDENTの担当講師を更新できる() {
            UserForm form = new UserForm("student", null, "生徒A", Role.STUDENT, List.of(2L));
            given(userRepository.findById(3L)).willReturn(Optional.of(student));
            given(userRepository.findByLoginId("student")).willReturn(Optional.of(student));
            given(userRepository.findAllById(List.of(2L))).willReturn(List.of(tutor));

            UserDto result = service.updateUser(3L, form, adminLogin);

            assertThat(result.tutorIds()).containsExactly(2L);
        }

        @Test
        void TUTOR経由では担当講師は変更されない() {
            UserForm form = new UserForm("student", null, "生徒A", Role.STUDENT, List.of(2L));
            given(userRepository.findById(3L)).willReturn(Optional.of(student));

            assertThatThrownBy(() -> service.updateUser(3L, form, tutorLogin))
                    .isInstanceOf(SecurityException.class);
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
        void TUTORは担当生徒を削除できる() {
            User assignedStudent = User.builder().id(3L).loginId("student").password("x")
                    .displayName("生徒A").role(Role.STUDENT).tutors(Set.of(tutor)).build();
            given(userRepository.findById(3L)).willReturn(Optional.of(assignedStudent));

            service.deleteUser(3L, tutorLogin);

            verify(userRepository).delete(assignedStudent);
        }

        @Test
        void TUTORは担当外の生徒を削除できない() {
            given(userRepository.findById(3L)).willReturn(Optional.of(student));

            assertThatThrownBy(() -> service.deleteUser(3L, tutorLogin))
                    .isInstanceOf(SecurityException.class);
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
