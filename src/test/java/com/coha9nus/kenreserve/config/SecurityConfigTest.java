package com.coha9nus.kenreserve.config;

import com.coha9nus.kenreserve.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {"app.data-initializer.admin-password=password"})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    // --- 未認証アクセスのリダイレクト ---

    @Test
    void 未認証ユーザーはホームにアクセスするとログイン画面にリダイレクトされる() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void 未認証ユーザーはユーザー管理画面にアクセスできない() throws Exception {
        mockMvc.perform(get("/users")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void 未認証ユーザーは予約管理画面にアクセスできない() throws Exception {
        mockMvc.perform(get("/reservations")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    // --- 認証済みアクセス ---

    @Test
    @WithMockUser
    void 認証済みユーザーはホームにアクセスできる() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().isOk());
    }

    // --- ログイン画面 ---

    @Test
    void ログイン画面は未認証でアクセスできる() throws Exception {
        mockMvc.perform(get("/login")).andExpect(status().isOk());
    }

    // --- ログイン POST ---

    @Test
    void 正しい認証情報でログインするとホームにリダイレクトされる() throws Exception {
        mockMvc.perform(
                post("/login").with(csrf()).param("loginId", "admin").param("password", "password"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/"));
    }

    @Test
    void 誤った認証情報でログインするとエラー付きログイン画面に戻る() throws Exception {
        mockMvc.perform(
                post("/login").with(csrf()).param("loginId", "nobody").param("password", "wrong"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login?error"));
    }

    // --- ログアウト ---

    @Test
    @WithMockUser
    void ログアウトするとログイン画面にリダイレクトされる() throws Exception {
        mockMvc.perform(post("/logout").with(csrf())).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?logout"));
    }

    @Test
    void ログアウト後はセッションが無効化され保護ページにアクセスするとログイン画面にリダイレクトされる() throws Exception {
        MvcResult loginResult = mockMvc.perform(
                post("/login").with(csrf()).param("loginId", "admin").param("password", "password"))
                .andExpect(redirectedUrl("/")).andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

        mockMvc.perform(post("/logout").with(csrf()).session(session))
                .andExpect(redirectedUrl("/login?logout"));

        assertTrue(session.isInvalid(), "ログアウト後にセッションが無効化されていること");

        mockMvc.perform(get("/").session(session)).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }
}
