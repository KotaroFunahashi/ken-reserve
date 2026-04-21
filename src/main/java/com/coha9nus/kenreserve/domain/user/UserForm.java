package com.coha9nus.kenreserve.domain.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UserForm(
    @NotBlank(message = "ログインIDは必須です。")
    @Size(max = 50, message = "ログインIDは50文字以内で入力してください。")
    String loginId,

    @Size(min = 4, max = 100, message = "パスワードは4～100文字で入力してください。")
    String password,

    @NotBlank(message = "表示名は必須です。")
    @Size(max = 100, message = "表示名は100文字以内で入力してください。")
    String displayName,

    @NotNull(message = "ロールは必須です。")
    Role role) {
}
