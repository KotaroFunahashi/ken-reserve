package com.coha9nus.kenreserve.domain.user;

public record UserDto(Long id, String loginId, String displayName, Role role) {
    public static UserDto from(User user) {
        return new UserDto(user.getId(), user.getLoginId(), user.getDisplayName(), user.getRole());
    }
}
