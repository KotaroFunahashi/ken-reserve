package com.coha9nus.kenreserve.domain.reservation;

import java.time.LocalDateTime;

public record ReservationDto(
        Long id,
        Long tutorId,
        String tutorName,
        Long userId,
        String userName,
        ReservationType type,
        ReservationStatus status,
        LocalDateTime startAt,
        LocalDateTime endAt
) {
    public static ReservationDto from(Reservation r) {
        return new ReservationDto(
                r.getId(),
                r.getTutor().getId(),
                r.getTutor().getDisplayName(),
                r.getUser() != null ? r.getUser().getId() : null,
                r.getUser() != null ? r.getUser().getDisplayName() : null,
                r.getType(),
                r.getStatus(),
                r.getStartAt(),
                r.getEndAt()
        );
    }
}
