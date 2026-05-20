package com.coha9nus.kenreserve.domain.reservation;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    @Query("""
            SELECT r FROM Reservation r
            WHERE r.tutor.id = :tutorId
              AND r.startAt < :end
              AND r.endAt > :start
              AND r.status IN :includeStatuses
            """)
    List<Reservation> findOverlapping(
            @Param("tutorId") Long tutorId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("includeStatuses") List<ReservationStatus> includeStatuses);

    @Query("""
            SELECT r FROM Reservation r
            WHERE r.tutor.id = :tutorId
              AND r.startAt < :end
              AND r.endAt > :start
              AND r.status IN :includeStatuses
            ORDER BY r.startAt
            """)
    List<Reservation> findByTutorAndPeriod(
            @Param("tutorId") Long tutorId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("includeStatuses") List<ReservationStatus> includeStatuses);

    List<Reservation> findByUserIdAndStatusIn(Long userId, List<ReservationStatus> statuses);

    List<Reservation> findByTutorIdAndStatusIn(Long tutorId, List<ReservationStatus> statuses);

    List<Reservation> findByUserId(Long userId);
}
