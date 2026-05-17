package com.coha9nus.kenreserve.domain.reservation;

import java.time.LocalTime;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 予約システムの時間設定。application.yml の app.reservation セクションにマッピングされる。
 *
 * <pre>
 * app:
 *   reservation:
 *     slot-start: "09:00"
 *     slot-end: "21:00"
 *     slot-minutes: 30
 *     lesson-minutes: 60
 *     buffer-minutes: 30
 * </pre>
 */
@ConfigurationProperties(prefix = "app.reservation")
public record ReservationProperties(
        LocalTime slotStart,
        LocalTime slotEnd,
        int slotMinutes,
        int lessonMinutes,
        int bufferMinutes) {
}
