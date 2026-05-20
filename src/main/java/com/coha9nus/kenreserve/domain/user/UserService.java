package com.coha9nus.kenreserve.domain.user;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.coha9nus.kenreserve.config.LoginUser;
import com.coha9nus.kenreserve.exception.BusinessRuleViolationException;
import com.coha9nus.kenreserve.exception.NotFoundException;
import com.coha9nus.kenreserve.exception.PermissionDeniedException;
import com.coha9nus.kenreserve.exception.ValidationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * ロールに基づいてユーザー一覧を返す。
     * ADMIN: 全ユーザー、TUTOR: 自分+担当生徒のみ、STUDENT: 自分+担当講師のみ
     */
    public List<UserDto> getVisibleUsers(LoginUser loginUser) {
        return switch (loginUser.role()) {
            case ADMIN -> userRepository.findAll().stream().map(UserDto::from).toList();
            case TUTOR -> {
                User tutorUser = userRepository.findById(loginUser.id()).orElseThrow();
                UserDto self = UserDto.from(tutorUser);
                List<UserDto> assignedStudents = userRepository.findByTutors_Id(loginUser.id()).stream()
                        .map(UserDto::from).toList();
                List<UserDto> result = new ArrayList<>(assignedStudents);
                result.add(self);
                yield result;
            }
            case STUDENT -> {
                User studentUser = userRepository.findById(loginUser.id()).orElseThrow();
                UserDto self = UserDto.from(studentUser);
                List<UserDto> assignedTutors = studentUser.getTutors().stream()
                        .map(UserDto::from).toList();
                List<UserDto> result = new ArrayList<>(assignedTutors);
                result.add(self);
                yield result;
            }
        };
    }

    /**
     * 担当講師として選択可能なユーザーを返す。
     * ADMIN: 全TUTOR、TUTOR: 自分のみ、STUDENT: 空リスト
     */
    public List<UserDto> getAvailableTutors(LoginUser loginUser) {
        return switch (loginUser.role()) {
            case ADMIN -> userRepository.findByRole(Role.TUTOR).stream().map(UserDto::from).toList();
            case TUTOR -> userRepository.findById(loginUser.id())
                    .map(u -> List.of(UserDto.from(u)))
                    .orElse(List.of());
            case STUDENT -> List.of();
        };
    }

    public UserDto getUser(Long id) {
        return userRepository.findById(id).map(UserDto::from)
                .orElseThrow(() -> new NotFoundException("ユーザーが見つかりません: " + id));
    }

    /**
     * ユーザーを新規作成する。
     * ロール制約: ADMINは全ロール作成可、TUTORはSTUDENTのみ作成可。
     */
    @Transactional
    public UserDto createUser(UserForm form, LoginUser loginUser) {
        validateRolePermission(loginUser, form.role());
        validateLoginIdUnique(form.loginId(), null);

        if (form.password() == null || form.password().isBlank()) {
            throw new ValidationException("新規作成時はパスワードが必須です。");
        }

        // TUTORが作成する場合は自分を担当講師に自動セット、ADMINはフォーム入力値を使用
        Set<User> tutors;
        if (loginUser.role() == Role.TUTOR) {
            User tutorUser = userRepository.findById(loginUser.id()).orElseThrow();
            tutors = new HashSet<>(Set.of(tutorUser));
        } else {
            tutors = resolveTutors(form.tutorIds());
        }

        User user = User.builder()
                .loginId(form.loginId())
                .password(passwordEncoder.encode(form.password()))
                .displayName(form.displayName())
                .role(form.role())
                .tutors(tutors)
                .build();

        return UserDto.from(userRepository.save(user));
    }

    /**
     * ユーザーを更新する。
     * ロール制約 + 自分自身の編集も考慮。
     */
    @Transactional
    public UserDto updateUser(Long id, UserForm form, LoginUser loginUser) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("ユーザーが見つかりません: " + id));

        validateEditPermission(loginUser, user, form.role());
        validateLoginIdUnique(form.loginId(), id);

        user.updateLoginId(form.loginId());
        user.updateDisplayName(form.displayName());

        if (form.password() != null && !form.password().isBlank()) {
            user.updatePassword(passwordEncoder.encode(form.password()));
        }

        // ロール変更はADMINのみ許可
        if (loginUser.role() == Role.ADMIN && form.role() != null) {
            user.updateRole(form.role());
        }

        // 担当講師の更新はADMINのみ許可
        if (loginUser.role() == Role.ADMIN && form.tutorIds() != null) {
            user.updateTutors(resolveTutors(form.tutorIds()));
        }

        return UserDto.from(user);
    }

    @Transactional
    public void deleteUser(Long id, LoginUser loginUser) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("ユーザーが見つかりません: " + id));

        if (id.equals(loginUser.id())) {
            throw new BusinessRuleViolationException("自分自身は削除できません。");
        }

        switch (loginUser.role()) {
            case ADMIN -> {}
            case TUTOR -> {
                boolean isAssigned = user.getTutors().stream()
                        .anyMatch(t -> t.getId().equals(loginUser.id()));
                if (!isAssigned) {
                    throw new PermissionDeniedException("担当生徒のみ削除できます。");
                }
            }
            case STUDENT -> throw new PermissionDeniedException("削除権限がありません。");
        }

        userRepository.delete(user);
    }

    /**
     * 作成権限チェック: 指定ロールのユーザーを作成する権限があるか。
     */
    void validateRolePermission(LoginUser loginUser, Role targetRole) {
        if (loginUser.role() == Role.ADMIN) {
            return; // ADMINは全ロール作成可
        }
        if (loginUser.role() == Role.TUTOR && targetRole == Role.STUDENT) {
            return; // TUTORはSTUDENTのみ作成可
        }
        throw new PermissionDeniedException("指定されたロールのユーザーを作成する権限がありません。");
    }

    /**
     * 編集権限チェック。
     * ADMIN: 全ユーザー編集可、TUTOR/STUDENT: 自分自身のみ編集可。
     */
    void validateEditPermission(LoginUser loginUser, User target, Role newRole) {
        // 自分自身の編集は許可（ロール変更は別途制御）
        if (loginUser.id().equals(target.getId())) {
            return;
        }
        // ADMINは全ユーザー編集可
        if (loginUser.role() == Role.ADMIN) {
            return;
        }
        throw new PermissionDeniedException("編集権限がありません。");
    }

    private void validateLoginIdUnique(String loginId, Long excludeId) {
        userRepository.findByLoginId(loginId).ifPresent(existing -> {
            if (!existing.getId().equals(excludeId)) {
                throw new ValidationException("ログインID「" + loginId + "」は既に使用されています。");
            }
        });
    }

    private Set<User> resolveTutors(List<Long> tutorIds) {
        if (tutorIds == null || tutorIds.isEmpty()) {
            return new HashSet<>();
        }
        return new HashSet<>(userRepository.findAllById(tutorIds));
    }
}
