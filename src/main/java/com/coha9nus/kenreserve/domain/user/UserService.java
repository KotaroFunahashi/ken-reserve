package com.coha9nus.kenreserve.domain.user;

import java.util.List;

import com.coha9nus.kenreserve.config.LoginUser;

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
     * ADMIN: 全ユーザー、TUTOR: STUDENT一覧、STUDENT: 自分+講師一覧
     */
    public List<UserDto> getVisibleUsers(LoginUser loginUser) {
        return switch (loginUser.role()) {
            case ADMIN -> userRepository.findAll().stream().map(UserDto::from).toList();
            case TUTOR -> userRepository.findByRole(Role.STUDENT).stream().map(UserDto::from).toList();
            case STUDENT -> {
                List<UserDto> tutors = userRepository.findByRole(Role.TUTOR).stream()
                        .map(UserDto::from).toList();
                UserDto self = userRepository.findById(loginUser.id())
                        .map(UserDto::from)
                        .orElseThrow();
                List<UserDto> result = new java.util.ArrayList<>(tutors);
                result.add(self);
                yield result;
            }
        };
    }

    public UserDto getUser(Long id) {
        return userRepository.findById(id).map(UserDto::from)
                .orElseThrow(() -> new IllegalArgumentException("ユーザーが見つかりません: " + id));
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
            throw new IllegalArgumentException("新規作成時はパスワードが必須です。");
        }

        User user = User.builder()
                .loginId(form.loginId())
                .password(passwordEncoder.encode(form.password()))
                .displayName(form.displayName())
                .role(form.role())
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
                .orElseThrow(() -> new IllegalArgumentException("ユーザーが見つかりません: " + id));

        validateEditPermission(loginUser, user, form.role());
        validateLoginIdUnique(form.loginId(), id);

        user.updateDisplayName(form.displayName());

        if (form.password() != null && !form.password().isBlank()) {
            user.updatePassword(passwordEncoder.encode(form.password()));
        }

        // ロール変更はADMINのみ許可
        if (loginUser.role() == Role.ADMIN && form.role() != null) {
            user.updateRole(form.role());
        }

        return UserDto.from(user);
    }

    @Transactional
    public void deleteUser(Long id, LoginUser loginUser) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("ユーザーが見つかりません: " + id));

        if (id.equals(loginUser.id())) {
            throw new IllegalStateException("自分自身は削除できません。");
        }

        validateEditPermission(loginUser, user, user.getRole());
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
        throw new SecurityException("指定されたロールのユーザーを作成する権限がありません。");
    }

    /**
     * 編集権限チェック。
     */
    void validateEditPermission(LoginUser loginUser, User target, Role newRole) {
        // 自分自身の編集は許可（ロール変更は別途制御）
        if (loginUser.id().equals(target.getId())) {
            return;
        }
        validateRolePermission(loginUser, target.getRole());
        if (newRole != null && newRole != target.getRole()) {
            validateRolePermission(loginUser, newRole);
        }
    }

    private void validateLoginIdUnique(String loginId, Long excludeId) {
        userRepository.findByLoginId(loginId).ifPresent(existing -> {
            if (!existing.getId().equals(excludeId)) {
                throw new IllegalArgumentException("ログインID「" + loginId + "」は既に使用されています。");
            }
        });
    }
}
