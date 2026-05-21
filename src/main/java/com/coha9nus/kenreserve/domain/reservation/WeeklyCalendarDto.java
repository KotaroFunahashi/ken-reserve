package com.coha9nus.kenreserve.domain.reservation;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record WeeklyCalendarDto(
        LocalDate weekStart,
        List<LocalDate> dates,
        Map<LocalDate, List<CalendarSlotDto>> slotsByDate
) {
}
