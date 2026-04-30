package com.coha9nus.kenreserve.domain.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.coha9nus.kenreserve.domain.user.Role;
import com.coha9nus.kenreserve.domain.user.User;
import com.coha9nus.kenreserve.domain.user.UserRepository;
import com.coha9nus.kenreserve.exception.BusinessRuleViolationException;
import com.coha9nus.kenreserve.exception.ValidationException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    ReservationRepository reservationRepository;

    @Mock
    UserRepository userRepository;

    @InjectMocks
    ReservationService service;

    static final LocalDate MON = LocalDate.of(2026, 3, 30); // Monday
    static final Long TUTOR_ID = 1L;
    static final Long STUDENT_ID = 2L;

    User tutor;
    User student;

    @BeforeEach
    void setUp() {
        tutor = User.builder().id(TUTOR_ID).loginId("tutor").password("x").displayName("先生")
                .role(Role.TUTOR).build();
        student = User.builder().id(STUDENT_ID).loginId("student").password("x").displayName("生徒A")
                .role(Role.STUDENT).build();
    }

    // ==================== 半開区間重複判定 ====================

    @Nested
    class OverlapsTest {
        @Test
        void 完全に重なる場合は重複() {
            assertThat(ReservationService.overlaps(dt(10, 0), dt(11, 0), dt(10, 0), dt(11, 0)))
                    .isTrue();
        }

        @Test
        void 部分的に重なる場合は重複() {
            assertThat(ReservationService.overlaps(dt(10, 0), dt(11, 0), dt(10, 30), dt(11, 30)))
                    .isTrue();
        }

        @Test
        void 隣接する半開区間は重複しない() {
            assertThat(ReservationService.overlaps(dt(10, 0), dt(11, 0), dt(11, 0), dt(12, 0)))
                    .isFalse();
        }

        @Test
        void 完全に離れている場合は重複しない() {
            assertThat(ReservationService.overlaps(dt(10, 0), dt(11, 0), dt(12, 0), dt(13, 0)))
                    .isFalse();
        }
    }

    // ==================== カレンダースロット判定 ====================

    @Nested
    class CalendarSlotTest {

        @Test
        void 予約がない日は全スロットがAVAILABLE() {
            List<CalendarSlotDto> slots = service.buildSlotsForDay(MON, Collections.emptyList());

            assertThat(slots).hasSize(24); // 09:00～20:30 = 24 slots
            assertThat(slots).allMatch(s -> s.status() == SlotStatus.AVAILABLE);
        }

        @Test
        void 予約がある時間帯のスロットはRESERVED() {
            // 10:00-11:00 の予約
            Reservation r = reservation(10, 0, 11, 0, ReservationType.RESERVATION, student);
            List<CalendarSlotDto> slots = service.buildSlotsForDay(MON, List.of(r));

            // 10:00, 10:30 が RESERVED
            assertSlotStatus(slots, 10, 0, SlotStatus.RESERVED);
            assertSlotStatus(slots, 10, 30, SlotStatus.RESERVED);
        }

        @Test
        void 予約の前後30分はBUFFER() {
            // 10:00-11:00 の予約 → バッファ: 09:30, 11:00
            Reservation r = reservation(10, 0, 11, 0, ReservationType.RESERVATION, student);
            List<CalendarSlotDto> slots = service.buildSlotsForDay(MON, List.of(r));

            assertSlotStatus(slots, 9, 30, SlotStatus.BUFFER);
            assertSlotStatus(slots, 11, 0, SlotStatus.BUFFER);
            // 09:00 と 11:30 はまだ空き
            assertSlotStatus(slots, 9, 0, SlotStatus.AVAILABLE);
            assertSlotStatus(slots, 11, 30, SlotStatus.AVAILABLE);
        }

        @Test
        void 休暇のスロットはVACATION() {
            // 13:00-17:00 の休暇
            Reservation v = vacation(13, 0, 17, 0);
            List<CalendarSlotDto> slots = service.buildSlotsForDay(MON, List.of(v));

            assertSlotStatus(slots, 13, 0, SlotStatus.VACATION);
            assertSlotStatus(slots, 14, 0, SlotStatus.VACATION);
            assertSlotStatus(slots, 16, 30, SlotStatus.VACATION);
            // 休暇にバッファはない
            assertSlotStatus(slots, 12, 30, SlotStatus.AVAILABLE);
            assertSlotStatus(slots, 17, 0, SlotStatus.AVAILABLE);
        }

        @Test
        void VACATIONはRESERVATIONより優先される() {
            // 休暇と予約が同じ時間帯に存在する場合（データ不整合のエッジケース）
            Reservation v = vacation(10, 0, 11, 0);
            Reservation r = reservation(10, 0, 11, 0, ReservationType.RESERVATION, student);
            List<CalendarSlotDto> slots = service.buildSlotsForDay(MON, List.of(v, r));

            assertSlotStatus(slots, 10, 0, SlotStatus.VACATION);
        }

        @Test
        void RESERVEDスロットには生徒名が含まれる() {
            Reservation r = reservation(10, 0, 11, 0, ReservationType.RESERVATION, student);
            List<CalendarSlotDto> slots = service.buildSlotsForDay(MON, List.of(r));

            CalendarSlotDto slot = findSlot(slots, 10, 0);
            assertThat(slot.studentName()).isEqualTo("生徒A");
        }

        @Test
        void 複数予約がある場合のバッファ判定() {
            // 10:00-11:00 と 14:00-15:00 の2件
            Reservation r1 = reservation(10, 0, 11, 0, ReservationType.RESERVATION, student);
            Reservation r2 = reservation(14, 0, 15, 0, ReservationType.RESERVATION, student);
            List<CalendarSlotDto> slots = service.buildSlotsForDay(MON, List.of(r1, r2));

            // r1のバッファ
            assertSlotStatus(slots, 9, 30, SlotStatus.BUFFER);
            assertSlotStatus(slots, 11, 0, SlotStatus.BUFFER);
            // r1とr2の間はAVAILABLE
            assertSlotStatus(slots, 12, 0, SlotStatus.AVAILABLE);
            // r2のバッファ
            assertSlotStatus(slots, 13, 30, SlotStatus.BUFFER);
            assertSlotStatus(slots, 15, 0, SlotStatus.BUFFER);
        }

        @Test
        void 始業時間09時00分のスロットが出力される() {
            List<CalendarSlotDto> slots = service.buildSlotsForDay(MON, Collections.emptyList());
            assertThat(slots.getFirst().time()).isEqualTo(LocalTime.of(9, 0));
        }

        @Test
        void 最終スロットは20時30分() {
            List<CalendarSlotDto> slots = service.buildSlotsForDay(MON, Collections.emptyList());
            assertThat(slots.getLast().time()).isEqualTo(LocalTime.of(20, 30));
        }
    }

    // ==================== 予約作成 & バッファルール ====================

    @Nested
    class CreateReservationTest {

        @Test
        void 空きスロットに予約を作成できる() {
            LocalDateTime startAt = MON.atTime(10, 0);
            given(userRepository.findById(TUTOR_ID)).willReturn(Optional.of(tutor));
            given(userRepository.findById(STUDENT_ID)).willReturn(Optional.of(student));
            given(reservationRepository.findOverlapping(eq(TUTOR_ID), any(), any(), any()))
                    .willReturn(Collections.emptyList());
            given(reservationRepository.save(any(Reservation.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            ReservationDto result = service.createReservation(TUTOR_ID, STUDENT_ID, startAt);

            assertThat(result.startAt()).isEqualTo(startAt);
            assertThat(result.startAt().plusMinutes(60)).isEqualTo(result.endAt());
            assertThat(result.status()).isEqualTo(ReservationStatus.PENDING);
            assertThat(result.type()).isEqualTo(ReservationType.RESERVATION);
        }

        @Test
        void 既存予約と直接重複する場合は例外() {
            LocalDateTime startAt = MON.atTime(10, 0);
            Reservation existing = reservation(10, 0, 11, 0, ReservationType.RESERVATION, student);

            given(userRepository.findById(TUTOR_ID)).willReturn(Optional.of(tutor));
            given(userRepository.findById(STUDENT_ID)).willReturn(Optional.of(student));
            given(reservationRepository.findOverlapping(eq(TUTOR_ID), any(), any(), any()))
                    .willReturn(List.of(existing));

            assertThatThrownBy(() -> service.createReservation(TUTOR_ID, STUDENT_ID, startAt))
                    .isInstanceOf(ReservationConflictException.class);
        }

        @Test
        void APPROVED予約のバッファ圏内で新規予約を試みると例外() {
            // 既存(APPROVED): 10:00-11:00 → バッファ圏: 09:30-11:30
            // 新規: 11:00-12:00 → バッファ圏: 10:30-12:30
            // 重複: 10:30-11:30
            LocalDateTime startAt = MON.atTime(11, 0);
            Reservation existing = approvedReservation(10, 0, 11, 0);

            given(userRepository.findById(TUTOR_ID)).willReturn(Optional.of(tutor));
            given(userRepository.findById(STUDENT_ID)).willReturn(Optional.of(student));
            given(reservationRepository.findOverlapping(eq(TUTOR_ID), any(), any(), any()))
                    .willReturn(List.of(existing));

            assertThatThrownBy(() -> service.createReservation(TUTOR_ID, STUDENT_ID, startAt))
                    .isInstanceOf(ReservationConflictException.class);
        }

        @Test
        void PENDING予約のバッファ圏内でも直接重複しなければ予約可能() {
            // 既存(PENDING): 13:00-14:00
            // 新規: 15:00-16:00 → バッファ圏: 14:30-16:30
            // 既存バッファ圏: 12:30-14:30 → 14:30で隣接のみ（半開区間で重複なし）
            LocalDateTime startAt = MON.atTime(15, 0);
            Reservation existing = reservation(13, 0, 14, 0, ReservationType.RESERVATION, student);

            given(userRepository.findById(TUTOR_ID)).willReturn(Optional.of(tutor));
            given(userRepository.findById(STUDENT_ID)).willReturn(Optional.of(student));
            given(reservationRepository.findOverlapping(eq(TUTOR_ID), any(), any(), any()))
                    .willReturn(List.of(existing));
            given(reservationRepository.save(any(Reservation.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            ReservationDto result = service.createReservation(TUTOR_ID, STUDENT_ID, startAt);
            assertThat(result.startAt()).isEqualTo(startAt);
        }

        @Test
        void PENDING予約と直接重複する場合は例外() {
            // 既存(PENDING): 10:00-11:00
            // 新規: 10:00-11:00 → 完全一致で直接重複
            LocalDateTime startAt = MON.atTime(10, 0);
            Reservation existing = reservation(10, 0, 11, 0, ReservationType.RESERVATION, student);

            given(userRepository.findById(TUTOR_ID)).willReturn(Optional.of(tutor));
            given(userRepository.findById(STUDENT_ID)).willReturn(Optional.of(student));
            given(reservationRepository.findOverlapping(eq(TUTOR_ID), any(), any(), any()))
                    .willReturn(List.of(existing));

            assertThatThrownBy(() -> service.createReservation(TUTOR_ID, STUDENT_ID, startAt))
                    .isInstanceOf(ReservationConflictException.class);
        }

        @Test
        void バッファ圏外なら予約可能_APPROVED後の例() {
            // 既存(APPROVED): 10:00-11:00 → バッファ圏: 09:30-11:30
            // 新規: 12:00-13:00 → バッファ圏: 11:30-13:30
            // 隣接するが重複しない（半開区間）
            LocalDateTime startAt = MON.atTime(12, 0);
            Reservation existing = approvedReservation(10, 0, 11, 0);

            given(userRepository.findById(TUTOR_ID)).willReturn(Optional.of(tutor));
            given(userRepository.findById(STUDENT_ID)).willReturn(Optional.of(student));
            given(reservationRepository.findOverlapping(eq(TUTOR_ID), any(), any(), any()))
                    .willReturn(List.of(existing));
            given(reservationRepository.save(any(Reservation.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            ReservationDto result = service.createReservation(TUTOR_ID, STUDENT_ID, startAt);
            assertThat(result.startAt()).isEqualTo(startAt);
        }

        @Test
        void APPROVED予約の30分前に予約しようとすると例外() {
            // 既存(APPROVED): 10:00-11:00 → バッファ圏: 09:30-11:30
            // 新規: 09:00-10:00 → バッファ圏: 08:30-10:30
            // 重複: 09:30-10:00
            LocalDateTime startAt = MON.atTime(9, 0);
            Reservation existing = approvedReservation(10, 0, 11, 0);

            given(userRepository.findById(TUTOR_ID)).willReturn(Optional.of(tutor));
            given(userRepository.findById(STUDENT_ID)).willReturn(Optional.of(student));
            given(reservationRepository.findOverlapping(eq(TUTOR_ID), any(), any(), any()))
                    .willReturn(List.of(existing));

            assertThatThrownBy(() -> service.createReservation(TUTOR_ID, STUDENT_ID, startAt))
                    .isInstanceOf(ReservationConflictException.class);
        }

        @Test
        void 休暇時間帯に予約しようとすると例外() {
            LocalDateTime startAt = MON.atTime(14, 0);
            Reservation vacation = vacation(13, 0, 17, 0);

            given(userRepository.findById(TUTOR_ID)).willReturn(Optional.of(tutor));
            given(userRepository.findById(STUDENT_ID)).willReturn(Optional.of(student));
            given(reservationRepository.findOverlapping(eq(TUTOR_ID), any(), any(), any()))
                    .willReturn(List.of(vacation));

            assertThatThrownBy(() -> service.createReservation(TUTOR_ID, STUDENT_ID, startAt))
                    .isInstanceOf(ReservationConflictException.class);
        }

        @Test
        void 営業時間外に予約しようとすると例外() {
            assertThatThrownBy(
                    () -> service.createReservation(TUTOR_ID, STUDENT_ID, MON.atTime(8, 0)))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        void 終了が営業時間を超える予約は例外() {
            // 20:30開始 → 21:30終了 → 超過
            assertThatThrownBy(
                    () -> service.createReservation(TUTOR_ID, STUDENT_ID, MON.atTime(20, 30)))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        void スロット単位でない時間は例外() {
            assertThatThrownBy(
                    () -> service.createReservation(TUTOR_ID, STUDENT_ID, MON.atTime(10, 15)))
                    .isInstanceOf(ValidationException.class);
        }
    }

    // ==================== 承認・却下 ====================

    @Nested
    class ApproveRejectTest {

        @Test
        void PENDING予約を承認するとAPPROVEDになる() {
            Reservation r = Reservation.builder().id(1L).tutor(tutor).user(student)
                    .type(ReservationType.RESERVATION).status(ReservationStatus.PENDING)
                    .startAt(dt(10, 0)).endAt(dt(11, 0)).build();
            given(reservationRepository.findById(1L)).willReturn(Optional.of(r));

            ReservationDto result = service.approveReservation(1L);
            assertThat(result.status()).isEqualTo(ReservationStatus.APPROVED);
        }

        @Test
        void PENDING予約を却下するとREJECTEDになる() {
            Reservation r = Reservation.builder().id(1L).tutor(tutor).user(student)
                    .type(ReservationType.RESERVATION).status(ReservationStatus.PENDING)
                    .startAt(dt(10, 0)).endAt(dt(11, 0)).build();
            given(reservationRepository.findById(1L)).willReturn(Optional.of(r));

            ReservationDto result = service.rejectReservation(1L);
            assertThat(result.status()).isEqualTo(ReservationStatus.REJECTED);
        }

        @Test
        void APPROVED済み予約を承認しようとすると例外() {
            Reservation r = Reservation.builder().id(1L).tutor(tutor).user(student)
                    .type(ReservationType.RESERVATION).status(ReservationStatus.APPROVED)
                    .startAt(dt(10, 0)).endAt(dt(11, 0)).build();
            given(reservationRepository.findById(1L)).willReturn(Optional.of(r));

            assertThatThrownBy(() -> service.approveReservation(1L))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }

        @Test
        void REJECTED済み予約を却下しようとすると例外() {
            Reservation r = Reservation.builder().id(1L).tutor(tutor).user(student)
                    .type(ReservationType.RESERVATION).status(ReservationStatus.REJECTED)
                    .startAt(dt(10, 0)).endAt(dt(11, 0)).build();
            given(reservationRepository.findById(1L)).willReturn(Optional.of(r));

            assertThatThrownBy(() -> service.rejectReservation(1L))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }
    }

    // ==================== ヘルパー ====================

    private LocalDateTime dt(int hour, int minute) {
        return MON.atTime(hour, minute);
    }

    private Reservation reservation(int startHour, int startMin, int endHour, int endMin,
            ReservationType type, User user) {
        return Reservation.builder().id((long) (startHour * 100 + startMin)).tutor(tutor).user(user)
                .type(type).status(ReservationStatus.PENDING).startAt(dt(startHour, startMin))
                .endAt(dt(endHour, endMin)).build();
    }

    private Reservation approvedReservation(int startHour, int startMin, int endHour, int endMin) {
        return Reservation.builder().id((long) (startHour * 100 + startMin + 5000)).tutor(tutor)
                .user(student).type(ReservationType.RESERVATION).status(ReservationStatus.APPROVED)
                .startAt(dt(startHour, startMin)).endAt(dt(endHour, endMin)).build();
    }

    private Reservation vacation(int startHour, int startMin, int endHour, int endMin) {
        return Reservation.builder().id((long) (startHour * 100 + startMin + 9000)).tutor(tutor)
                .type(ReservationType.VACATION).status(ReservationStatus.APPROVED)
                .startAt(dt(startHour, startMin)).endAt(dt(endHour, endMin)).build();
    }

    private void assertSlotStatus(List<CalendarSlotDto> slots, int hour, int minute,
            SlotStatus expected) {
        CalendarSlotDto slot = findSlot(slots, hour, minute);
        assertThat(slot.status()).as("Slot at %02d:%02d", hour, minute).isEqualTo(expected);
    }

    private CalendarSlotDto findSlot(List<CalendarSlotDto> slots, int hour, int minute) {
        LocalTime time = LocalTime.of(hour, minute);
        return slots.stream().filter(s -> s.time().equals(time)).findFirst()
                .orElseThrow(() -> new AssertionError("Slot not found: " + time));
    }

    // AssertionError is intentional for test helper readability
    private static class AssertionError extends RuntimeException {
        AssertionError(String msg) {
            super(msg);
        }
    }
}
