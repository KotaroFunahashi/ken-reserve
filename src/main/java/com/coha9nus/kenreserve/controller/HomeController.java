package com.coha9nus.kenreserve.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.coha9nus.kenreserve.config.LoginUser;
import com.coha9nus.kenreserve.domain.reservation.ReservationConflictException;
import com.coha9nus.kenreserve.domain.reservation.ReservationService;
import com.coha9nus.kenreserve.domain.reservation.WeeklyCalendarDto;
import com.coha9nus.kenreserve.domain.user.Role;
import com.coha9nus.kenreserve.domain.user.UserRepository;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final ReservationService reservationService;
    private final UserRepository userRepository;

    @GetMapping("/")
    public String home(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week,
            Model model) {
        LocalDate baseDate = (week != null) ? week : LocalDate.now();

        Long tutorId = resolveTutorId(loginUser);
        WeeklyCalendarDto calendar = reservationService.getWeeklyCalendar(tutorId, baseDate);

        model.addAttribute("calendar", calendar);
        model.addAttribute("loginUser", loginUser);
        model.addAttribute("tutorId", tutorId);

        if (loginUser.role() == Role.TUTOR || loginUser.role() == Role.ADMIN) {
            model.addAttribute("pendingList", reservationService.getPendingReservations(tutorId));
        }
        if (loginUser.role() == Role.STUDENT) {
            model.addAttribute("notifications",
                    reservationService.getNotifications(loginUser.id()));
        }

        return "index";
    }

    @PostMapping("/reserve")
    public String reserve(@AuthenticationPrincipal LoginUser loginUser,
            @RequestParam Long tutorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startAt,
            RedirectAttributes redirectAttributes) {
        try {
            reservationService.createReservation(tutorId, loginUser.id(), startAt);
            redirectAttributes.addFlashAttribute("message", "予約を申請しました。");
        } catch (ReservationConflictException | IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/";
    }

    @PostMapping("/approve/{id}")
    public String approve(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        try {
            reservationService.approveReservation(id);
            redirectAttributes.addFlashAttribute("message", "予約を承認しました。");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/";
    }

    @PostMapping("/reject/{id}")
    public String reject(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        try {
            reservationService.rejectReservation(id);
            redirectAttributes.addFlashAttribute("message", "予約を却下しました。");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/";
    }

    private Long resolveTutorId(LoginUser loginUser) {
        if (loginUser.role() == Role.TUTOR) {
            return loginUser.id();
        }
        // STUDENT/ADMIN: 最初の講師のカレンダーを表示
        return userRepository.findByRole(Role.TUTOR).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("講師が登録されていません。")).getId();
    }
}
