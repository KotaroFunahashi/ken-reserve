package com.coha9nus.kenreserve.controller;

import java.util.List;

import com.coha9nus.kenreserve.config.LoginUser;
import com.coha9nus.kenreserve.domain.user.Role;
import com.coha9nus.kenreserve.domain.user.UserDto;
import com.coha9nus.kenreserve.domain.user.UserForm;
import com.coha9nus.kenreserve.domain.user.UserService;
import com.coha9nus.kenreserve.exception.BusinessRuleViolationException;
import com.coha9nus.kenreserve.exception.NotFoundException;
import com.coha9nus.kenreserve.exception.PermissionDeniedException;
import com.coha9nus.kenreserve.exception.ValidationException;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public String list(@AuthenticationPrincipal LoginUser loginUser, Model model) {
        model.addAttribute("users", userService.getVisibleUsers(loginUser));
        model.addAttribute("loginUser", loginUser);
        return "users/list";
    }

    @GetMapping("/new")
    public String newForm(@AuthenticationPrincipal LoginUser loginUser, Model model) {
        if (loginUser.role() == Role.STUDENT) {
            return "redirect:/users";
        }
        List<UserDto> availableTutors = userService.getAvailableTutors(loginUser);
        List<Long> defaultTutorIds = (loginUser.role() == Role.TUTOR && !availableTutors.isEmpty())
                ? List.of(availableTutors.getFirst().id())
                : null;
        model.addAttribute("userForm", new UserForm("", "", "", Role.STUDENT, defaultTutorIds));
        model.addAttribute("availableTutors", availableTutors);
        model.addAttribute("loginUser", loginUser);
        return "users/form";
    }

    @PostMapping("/new")
    public String create(@AuthenticationPrincipal LoginUser loginUser,
                         @Valid @ModelAttribute("userForm") UserForm form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("availableTutors", userService.getAvailableTutors(loginUser));
            model.addAttribute("loginUser", loginUser);
            return "users/form";
        }
        try {
            userService.createUser(form, loginUser);
        } catch (PermissionDeniedException | ValidationException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("availableTutors", userService.getAvailableTutors(loginUser));
            model.addAttribute("loginUser", loginUser);
            return "users/form";
        }
        redirectAttributes.addFlashAttribute("message", "ユーザーを作成しました。");
        return "redirect:/users";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@AuthenticationPrincipal LoginUser loginUser,
                           @PathVariable Long id,
                           Model model) {
        if (!canEdit(loginUser, id)) {
            return "redirect:/users";
        }
        UserDto user = userService.getUser(id);
        model.addAttribute("userForm", new UserForm(user.loginId(), "", user.displayName(), user.role(), user.tutorIds()));
        model.addAttribute("editId", id);
        // 担当講師の変更はADMINのみ許可
        if (loginUser.role() == Role.ADMIN) {
            model.addAttribute("availableTutors", userService.getAvailableTutors(loginUser));
        }
        model.addAttribute("loginUser", loginUser);
        return "users/form";
    }

    @PostMapping("/{id}/edit")
    public String update(@AuthenticationPrincipal LoginUser loginUser,
                         @PathVariable Long id,
                         @Valid
                         @ModelAttribute("userForm") UserForm form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasFieldErrors("loginId") || bindingResult.hasFieldErrors("displayName")) {
            model.addAttribute("editId", id);
            if (loginUser.role() == Role.ADMIN) {
                model.addAttribute("availableTutors", userService.getAvailableTutors(loginUser));
            }
            model.addAttribute("loginUser", loginUser);
            return "users/form";
        }
        try {
            userService.updateUser(id, form, loginUser);
        } catch (NotFoundException | PermissionDeniedException | ValidationException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("editId", id);
            if (loginUser.role() == Role.ADMIN) {
                model.addAttribute("availableTutors", userService.getAvailableTutors(loginUser));
            }
            model.addAttribute("loginUser", loginUser);
            return "users/form";
        }
        redirectAttributes.addFlashAttribute("message", "ユーザーを更新しました。");
        return "redirect:/users";
    }

    @PostMapping("/{id}/delete")
    public String delete(@AuthenticationPrincipal LoginUser loginUser,
                         @PathVariable Long id,
                         RedirectAttributes redirectAttributes) {
        try {
            userService.deleteUser(id, loginUser);
        } catch (NotFoundException | BusinessRuleViolationException | PermissionDeniedException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/users";
        }
        redirectAttributes.addFlashAttribute("message", "ユーザーを削除しました。");
        return "redirect:/users";
    }

    private boolean canEdit(LoginUser loginUser, Long targetId) {
        if (loginUser.role() == Role.ADMIN) return true;
        // TUTOR / STUDENT は自分自身のみ編集可
        return loginUser.id().equals(targetId);
    }
}
