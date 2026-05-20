package com.coha9nus.kenreserve.domain.reservation;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.coha9nus.kenreserve.domain.user.User;
import com.coha9nus.kenreserve.domain.user.UserRepository;
import com.coha9nus.kenreserve.exception.BusinessRuleViolationException;
import com.coha9nus.kenreserve.exception.NotFoundException;
import com.coha9nus.kenreserve.exception.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReservationService {

    /** PENDINGとAPPROVEDをブロック対象とするステータス一覧 */
    static final List<ReservationStatus> BLOCKING_STATUSES =
            List.of(ReservationStatus.PENDING, ReservationStatus.APPROVED);

    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final ReservationProperties reservationProperties;

    /**
     * 指定した週の月曜日から日曜日までの週間カレンダーを生成する。
     */
    public WeeklyCalendarDto getWeeklyCalendar(Long tutorId, LocalDate baseDate) {
        LocalDate weekStart = baseDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(7);

        LocalDateTime queryStart = weekStart.atTime(reservationProperties.slotStart()).minusMinutes(reservationProperties.bufferMinutes());
        LocalDateTime queryEnd = weekEnd.atTime(reservationProperties.slotEnd()).plusMinutes(reservationProperties.bufferMinutes());

        List<Reservation> reservations = reservationRepository.findByTutorAndPeriod(
                tutorId, queryStart, queryEnd, BLOCKING_STATUSES);

        List<LocalDate> dates = new ArrayList<>();
        Map<LocalDate, List<CalendarSlotDto>> slotsByDate = new LinkedHashMap<>();

        for (int d = 0; d < 7; d++) {
            LocalDate date = weekStart.plusDays(d);
            dates.add(date);
            slotsByDate.put(date, buildSlotsForDay(date, reservations));
        }

        return new WeeklyCalendarDto(weekStart, dates, slotsByDate);
    }

    List<CalendarSlotDto> buildSlotsForDay(LocalDate date, List<Reservation> reservations) {
        List<CalendarSlotDto> slots = new ArrayList<>();
        LocalTime time = reservationProperties.slotStart();

        while (time.isBefore(reservationProperties.slotEnd())) {
            LocalDateTime slotStart = date.atTime(time);
            LocalDateTime slotEnd = slotStart.plusMinutes(reservationProperties.slotMinutes());

            CalendarSlotDto slot = determineSlotStatus(date, time, slotStart, slotEnd, reservations);
            slots.add(slot);

            time = time.plusMinutes(reservationProperties.slotMinutes());
        }
        return slots;
    }

    CalendarSlotDto determineSlotStatus(LocalDate date, LocalTime time,
                                        LocalDateTime slotStart, LocalDateTime slotEnd,
                                        List<Reservation> reservations) {
        // 優先度: VACATION > RESERVED > BUFFER > AVAILABLE
        for (Reservation r : reservations) {
            if (r.getType() == ReservationType.VACATION && overlaps(slotStart, slotEnd, r.getStartAt(), r.getEndAt())) {
                return CalendarSlotDto.vacation(date, time, r.getId());
            }
        }

        for (Reservation r : reservations) {
            if (r.getType() == ReservationType.RESERVATION && overlaps(slotStart, slotEnd, r.getStartAt(), r.getEndAt())) {
                String studentName = r.getUser() != null ? r.getUser().getDisplayName() : null;
                return CalendarSlotDto.reserved(date, time, r.getId(), studentName);
            }
        }

        for (Reservation r : reservations) {
            if (r.getType() == ReservationType.RESERVATION) {
                LocalDateTime bufferStart = r.getStartAt().minusMinutes(reservationProperties.bufferMinutes());
                LocalDateTime bufferEnd = r.getEndAt().plusMinutes(reservationProperties.bufferMinutes());
                if (overlaps(slotStart, slotEnd, bufferStart, bufferEnd)) {
                    return CalendarSlotDto.buffer(date, time);
                }
            }
        }

        return CalendarSlotDto.available(date, time);
    }

    /**
     * 予約を新規作成する。バッファルールを含む衝突チェックを行う。
     */
    @Transactional
    public ReservationDto createReservation(Long tutorId, Long userId, LocalDateTime startAt) {
        LocalDateTime endAt = startAt.plusMinutes(reservationProperties.lessonMinutes());

        validateSlotTime(startAt);

        User tutor = userRepository.findById(tutorId)
                .orElseThrow(() -> new NotFoundException("講師が見つかりません: " + tutorId));
        User student = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("生徒が見つかりません: " + userId));

        checkConflicts(tutorId, startAt, endAt);

        Reservation reservation = Reservation.builder()
                .tutor(tutor)
                .user(student)
                .type(ReservationType.RESERVATION)
                .status(ReservationStatus.PENDING)
                .startAt(startAt)
                .endAt(endAt)
                .build();

        return ReservationDto.from(reservationRepository.save(reservation));
    }

    /**
     * 休暇を登録する。
     */
    @Transactional
    public ReservationDto createVacation(Long tutorId, LocalDateTime startAt, LocalDateTime endAt) {
        User tutor = userRepository.findById(tutorId)
                .orElseThrow(() -> new NotFoundException("講師が見つかりません: " + tutorId));

        // 休暇は既存の予約（バッファなし）と直接重複がないかチェック
        List<Reservation> directConflicts = reservationRepository.findOverlapping(
                tutorId, startAt, endAt, BLOCKING_STATUSES);
        if (!directConflicts.isEmpty()) {
            throw new ReservationConflictException("指定の時間帯に既存の予約があるため、休暇を登録できません。");
        }

        Reservation vacation = Reservation.builder()
                .tutor(tutor)
                .type(ReservationType.VACATION)
                .status(ReservationStatus.APPROVED)
                .startAt(startAt)
                .endAt(endAt)
                .build();

        return ReservationDto.from(reservationRepository.save(vacation));
    }

    /**
     * 予約を承認する。
     */
    @Transactional
    public ReservationDto approveReservation(Long reservationId) {
        Reservation reservation = findReservationById(reservationId);
        if (reservation.getStatus() != ReservationStatus.PENDING) {
            throw new BusinessRuleViolationException("PENDING以外の予約は承認できません。");
        }
        reservation.updateStatus(ReservationStatus.APPROVED);
        return ReservationDto.from(reservation);
    }

    /**
     * 予約を却下する。
     */
    @Transactional
    public ReservationDto rejectReservation(Long reservationId) {
        Reservation reservation = findReservationById(reservationId);
        if (reservation.getStatus() != ReservationStatus.PENDING) {
            throw new BusinessRuleViolationException("PENDING以外の予約は却下できません。");
        }
        reservation.updateStatus(ReservationStatus.REJECTED);
        return ReservationDto.from(reservation);
    }

    /**
     * 講師向け: 未回答予約リストを取得する。
     */
    public List<ReservationDto> getPendingReservations(Long tutorId) {
        return reservationRepository.findByTutorIdAndStatusIn(tutorId, List.of(ReservationStatus.PENDING))
                .stream().map(ReservationDto::from).toList();
    }

    /**
     * 生徒向け: 承認/却下された予約通知を取得する。
     */
    public List<ReservationDto> getNotifications(Long userId) {
        return reservationRepository.findByUserIdAndStatusIn(
                        userId, List.of(ReservationStatus.APPROVED, ReservationStatus.REJECTED))
                .stream().map(ReservationDto::from).toList();
    }

    /**
     * 予約を取得する。
     */
    public ReservationDto getReservation(Long reservationId) {
        return ReservationDto.from(findReservationById(reservationId));
    }

    /**
     * バッファルールを含む衝突チェック。
     * APPROVED予約とはバッファ込みで衝突を禁止。
     * PENDING予約同士のバッファ重複は許可する（同一時間帯の直接重複は禁止）。
     */
    void checkConflicts(Long tutorId, LocalDateTime startAt, LocalDateTime endAt) {
        LocalDateTime queryStart = startAt.minusMinutes(reservationProperties.bufferMinutes() * 2L);
        LocalDateTime queryEnd = endAt.plusMinutes(reservationProperties.bufferMinutes() * 2L);

        List<Reservation> candidates = reservationRepository.findOverlapping(
                tutorId, queryStart, queryEnd, BLOCKING_STATUSES);

        for (Reservation existing : candidates) {
            if (existing.getType() == ReservationType.VACATION) {
                if (overlaps(startAt, endAt, existing.getStartAt(), existing.getEndAt())) {
                    throw new ReservationConflictException(
                            "指定の時間帯は講師の休暇と重複しています。");
                }
            } else if (existing.getStatus() == ReservationStatus.APPROVED) {
                // APPROVED予約とはバッファ込みで衝突チェック
                LocalDateTime existingBufferStart = existing.getStartAt().minusMinutes(reservationProperties.bufferMinutes());
                LocalDateTime existingBufferEnd = existing.getEndAt().plusMinutes(reservationProperties.bufferMinutes());
                LocalDateTime newBufferStart = startAt.minusMinutes(reservationProperties.bufferMinutes());
                LocalDateTime newBufferEnd = endAt.plusMinutes(reservationProperties.bufferMinutes());

                if (overlaps(newBufferStart, newBufferEnd, existingBufferStart, existingBufferEnd)) {
                    throw new ReservationConflictException(
                            "指定の時間帯は承認済みの予約（バッファ含む）と重複しています。");
                }
            } else if (existing.getStatus() == ReservationStatus.PENDING) {
                // PENDING予約とは直接重複（時間帯が被る）のみ禁止、バッファ重複は許可
                if (overlaps(startAt, endAt, existing.getStartAt(), existing.getEndAt())) {
                    throw new ReservationConflictException(
                            "指定の時間帯は申請中の予約と重複しています。");
                }
            }
        }
    }

    private void validateSlotTime(LocalDateTime startAt) {
        LocalTime time = startAt.toLocalTime();
        LocalTime endTime = time.plusMinutes(reservationProperties.lessonMinutes());
        if (time.isBefore(reservationProperties.slotStart()) || endTime.isAfter(reservationProperties.slotEnd())) {
            throw new ValidationException(
                    "予約は " + reservationProperties.slotStart() + " ～ " + reservationProperties.slotEnd() + " の範囲内である必要があります。");
        }
        if (time.getMinute() % reservationProperties.slotMinutes() != 0) {
            throw new ValidationException(
                    "予約は " + reservationProperties.slotMinutes() + " 分単位で指定してください。");
        }
    }

    private Reservation findReservationById(Long reservationId) {
        return reservationRepository.findById(reservationId)
                .orElseThrow(() -> new NotFoundException("予約が見つかりません: " + reservationId));
    }

    /** 半開区間 [s1, e1) と [s2, e2) の重複判定 */
    static boolean overlaps(LocalDateTime s1, LocalDateTime e1, LocalDateTime s2, LocalDateTime e2) {
        return s1.isBefore(e2) && s2.isBefore(e1);
    }
}
