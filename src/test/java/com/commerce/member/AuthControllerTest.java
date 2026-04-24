package com.commerce.member;

import com.commerce.member.infrastructure.MemberRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        memberRepository.deleteAll();
    }

    @Test
    @DisplayName("회원가입 API가 유효한 이메일과 비밀번호로 회원을 등록한다")
    void signupWithValidEmailAndPassword_returns201() throws Exception {
        Map<String, String> body = Map.of(
                "email", "user@example.com",
                "password", "Password1!",
                "name", "홍길동"
        );

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());

        assertThat(memberRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("회원가입 API가 이메일을 소문자로 저장한다")
    void signup_stores_email_in_lowercase() throws Exception {
        Map<String, String> body = Map.of(
                "email", "USER@EXAMPLE.COM",
                "password", "Password1!",
                "name", "홍길동"
        );

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());

        String savedEmail = memberRepository.findAll().get(0).getEmail();
        assertThat(savedEmail).isEqualTo("user@example.com");
    }
    
    @Test
    @DisplayName("회원가입 API가 비밀번호를 bcrypt로 암호화해 저장한다")
    void signup_stores_password_encoded_with_bcrypt() throws Exception {
        String rawPassword = "Password1!";
        Map<String, String> body = Map.of(
                "email", "user@example.com",
                "password", rawPassword,
                "name", "홍길동"
        );

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());

        String savedPassword = memberRepository.findAll().get(0).getPassword();
        assertThat(savedPassword).isNotEqualTo(rawPassword);
        assertThat(passwordEncoder.matches(rawPassword, savedPassword)).isTrue();
    }

    @Test
    @DisplayName("회원가입 API가 기본 role을 CUSTOMER로 설정한다")
    void signup_sets_default_role_to_customer() throws Exception {
        Map<String, String> body = Map.of(
                "email", "user@example.com",
                "password", "Password1!",
                "name", "홍길동"
        );

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());

        com.commerce.member.domain.MemberRole savedRole = memberRepository.findAll().get(0).getRole();
        assertThat(savedRole).isEqualTo(com.commerce.member.domain.MemberRole.CUSTOMER);
    }

    @Test
    @DisplayName("회원가입 API가 기본 status를 ACTIVE로 설정한다")
    void signup_sets_default_status_to_active() throws Exception {
        Map<String, String> body = Map.of(
                "email", "user@example.com",
                "password", "Password1!",
                "name", "홍길동"
        );

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());

        com.commerce.member.domain.MemberStatus savedStatus = memberRepository.findAll().get(0).getStatus();
        assertThat(savedStatus).isEqualTo(com.commerce.member.domain.MemberStatus.ACTIVE);
    }

    @Test
    @DisplayName("회원가입 API가 중복 이메일을 409 MEMBER_ALREADY_EXISTS로 거부한다")
    void signup_rejects_duplicate_email_with_409() throws Exception {
        Map<String, String> body = Map.of(
                "email", "user@example.com",
                "password", "Password1!",
                "name", "홍길동"
        );

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBER_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("회원가입 API가 8자 미만 비밀번호를 거부한다")
    void signup_rejects_password_shorter_than_8_chars() throws Exception {
        Map<String, String> body = Map.of(
                "email", "user@example.com",
                "password", "Pass1!",
                "name", "홍길동"
        );

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("회원가입 API가 영문 대문자 없는 비밀번호를 거부한다")
    void signup_rejects_password_without_uppercase() throws Exception {
        Map<String, String> body = Map.of(
                "email", "user@example.com",
                "password", "password1!",
                "name", "홍길동"
        );

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }
}