package com.coha9nus.kenreserve.domain.user;

import java.util.List;

public record UserDto(Long id, String loginId, String displayName, Role role, List<Long> tutorIds) {
    public static UserDto from(User user) {
        List<Long> tutorIds = user.getTutors().stream().map(User::getId).toList();
        return new UserDto(user.getId(), user.getLoginId(), user.getDisplayName(), user.getRole(), tutorIds);
    }
}
