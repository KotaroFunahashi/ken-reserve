package com.coha9nus.kenreserve.domain.reservation;

import java.time.LocalDate;
import java.time.LocalTime;

public record CalendarSlotDto(
        LocalDate date,
        LocalTime time,
        SlotStatus status,
        Long reservationId,
        String studentName
) {
    public static CalendarSlotDto available(LocalDate date, LocalTime time) {
        return new CalendarSlotDto(date, time, SlotStatus.AVAILABLE, null, null);
    }

    public static CalendarSlotDto reserved(LocalDate date, LocalTime time, Long reservationId, String studentName) {
        return new CalendarSlotDto(date, time, SlotStatus.RESERVED, reservationId, studentName);
    }

    public static CalendarSlotDto vacation(LocalDate date, LocalTime time, Long reservationId) {
        return new CalendarSlotDto(date, time, SlotStatus.VACATION, reservationId, null);
    }

    public static CalendarSlotDto buffer(LocalDate date, LocalTime time) {
        return new CalendarSlotDto(date, time, SlotStatus.BUFFER, null, null);
    }
}
