package com.coha9nus.kenreserve.controller;

import java.time.LocalDateTime;
import java.util.List;

import com.coha9nus.kenreserve.config.LoginUser;
import com.coha9nus.kenreserve.domain.reservation.ReservationConflictException;
import com.coha9nus.kenreserve.domain.reservation.ReservationDto;
import com.coha9nus.kenreserve.domain.reservation.ReservationRepository;
import com.coha9nus.kenreserve.domain.reservation.ReservationService;
import com.coha9nus.kenreserve.domain.user.Role;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;
    private final ReservationRepository reservationRepository;

    @GetMapping
    public String list(@AuthenticationPrincipal LoginUser loginUser, Model model) {
        List<ReservationDto> reservations;
        if (loginUser.role() == Role.STUDENT) {
            reservations = reservationRepository.findByUserId(loginUser.id()).stream()
                    .map(ReservationDto::from).toList();
        } else {
            reservations =
                    reservationRepository.findAll().stream().map(ReservationDto::from).toList();
        }
        model.addAttribute("reservations", reservations);
        model.addAttribute("loginUser", loginUser);
        return "reservations";
    }

    @PostMapping("/vacation")
    public String createVacation(@AuthenticationPrincipal LoginUser loginUser,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startAt,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endAt,
            RedirectAttributes redirectAttributes) {
        try {
            reservationService.createVacation(loginUser.id(), startAt, endAt);
            redirectAttributes.addFlashAttribute("message", "休暇を登録しました。");
        } catch (ReservationConflictException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/reservations";
    }

    @PostMapping("/delete/{id}")
    public String delete(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long id,
            RedirectAttributes redirectAttributes) {
        try {
            ReservationDto reservation = reservationService.getReservation(id);

            // 生徒は自分の予約のみ、前日までキャンセル可能
            if (loginUser.role() == Role.STUDENT) {
                if (!loginUser.id().equals(reservation.userId())) {
                    redirectAttributes.addFlashAttribute("error", "他の生徒の予約は削除できません。");
                    return "redirect:/reservations";
                }
                if (!reservation.startAt().toLocalDate().isAfter(java.time.LocalDate.now())) {
                    redirectAttributes.addFlashAttribute("error", "当日以降の予約はキャンセルできません。");
                    return "redirect:/reservations";
                }
            }

            reservationRepository.deleteById(id);
            redirectAttributes.addFlashAttribute("message", "予約を削除しました。");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/reservations";
    }
}
