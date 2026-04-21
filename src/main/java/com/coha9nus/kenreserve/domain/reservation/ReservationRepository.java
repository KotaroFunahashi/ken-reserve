package com.coha9nus.kenreserve.domain.reservation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    @Query("""
            SELECT r FROM Reservation r
            WHERE r.tutor.id = :tutorId
              AND r.startAt < :end
              AND r.endAt > :start
              AND (:excludeStatuses IS NULL OR r.status NOT IN :excludeStatuses)
            """)
    List<Reservation> findOverlapping(
            @Param("tutorId") Long tutorId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("excludeStatuses") List<ReservationStatus> excludeStatuses);

    @Query("""
            SELECT r FROM Reservation r
            WHERE r.tutor.id = :tutorId
              AND r.startAt < :end
              AND r.endAt > :start
              AND r.status NOT IN :excludeStatuses
            ORDER BY r.startAt
            """)
    List<Reservation> findByTutorAndPeriod(
            @Param("tutorId") Long tutorId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("excludeStatuses") List<ReservationStatus> excludeStatuses);

    List<Reservation> findByUserIdAndStatusIn(Long userId, List<ReservationStatus> statuses);

    List<Reservation> findByTutorIdAndStatusIn(Long tutorId, List<ReservationStatus> statuses);

    List<Reservation> findByUserId(Long userId);
}
