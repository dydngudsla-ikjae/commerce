package com.commerce.member;

import com.commerce.member.domain.MemberRole;
import com.commerce.member.domain.MemberStatus;
import com.commerce.member.infrastructure.MemberRepository;
import com.commerce.member.presentation.SignupResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthControllerTest {

    @LocalServerPort
    private int port;

    private RestClient client;

    @Autowired
    private MemberRepository memberRepository;

    @BeforeEach
    void setUp() {
        client = RestClient.create("http://localhost:" + port);
        memberRepository.deleteAll();
    }

    @Test
    @DisplayName("회원가입 API가 유효한 이메일과 비밀번호로 회원을 등록한다")
    void signupWithValidEmailAndPassword_returns201() {
        var body = Map.of(
                "email", "user@example.com",
                "password", "Password1!",
                "name", "홍길동"
        );

        var response = client.post()
                .uri("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(SignupResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("회원가입 API가 이메일을 소문자로 저장한다")
    void signup_stores_email_in_lowercase() {
        var body = Map.of(
                "email", "USER@EXAMPLE.COM",
                "password", "Password1!",
                "name", "홍길동"
        );

        var response = client.post()
                .uri("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(SignupResponse.class);

        assertThat(response.getBody().email()).isEqualTo("user@example.com");
    }

    @Test
    @DisplayName("회원가입 API가 기본 role을 CUSTOMER로 설정한다")
    void signup_sets_default_role_to_customer() {
        var body = Map.of(
                "email", "user@example.com",
                "password", "Password1!",
                "name", "홍길동"
        );

        var response = client.post()
                .uri("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(SignupResponse.class);

        assertThat(response.getBody().role()).isEqualTo(MemberRole.CUSTOMER);
    }

    @Test
    @DisplayName("회원가입 API가 기본 status를 ACTIVE로 설정한다")
    void signup_sets_default_status_to_active() {
        var body = Map.of(
                "email", "user@example.com",
                "password", "Password1!",
                "name", "홍길동"
        );

        var response = client.post()
                .uri("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(SignupResponse.class);

        assertThat(response.getBody().status()).isEqualTo(MemberStatus.ACTIVE);
    }

    @Test
    @DisplayName("회원가입 API가 중복 이메일을 409 MEMBER_ALREADY_EXISTS로 거부한다")
    void signup_rejects_duplicate_email_with_409() {
        var body = Map.of(
                "email", "user@example.com",
                "password", "Password1!",
                "name", "홍길동"
        );

        client.post()
                .uri("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();

        var ex = catchThrowableOfType(
                () -> client.post()
                        .uri("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .toBodilessEntity(),
                HttpClientErrorException.Conflict.class
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getResponseBodyAsString()).contains("MEMBER_ALREADY_EXISTS");
    }

    @Test
    @DisplayName("회원가입 API가 8자 미만 비밀번호를 거부한다")
    void signup_rejects_password_shorter_than_8_chars() {
        var body = Map.of(
                "email", "user@example.com",
                "password", "Pass1!",
                "name", "홍길동"
        );

        assertThatThrownBy(() ->
                client.post()
                        .uri("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .toBodilessEntity()
        ).isInstanceOf(HttpClientErrorException.BadRequest.class);
    }

    @Test
    @DisplayName("회원가입 API가 영문 대문자 없는 비밀번호를 거부한다")
    void signup_rejects_password_without_uppercase() {
        var body = Map.of(
                "email", "user@example.com",
                "password", "password1!",
                "name", "홍길동"
        );

        assertThatThrownBy(() ->
                client.post()
                        .uri("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .toBodilessEntity()
        ).isInstanceOf(HttpClientErrorException.BadRequest.class);
    }

    @Test
    @DisplayName("회원가입 API가 영문 소문자 없는 비밀번호를 거부한다")
    void signup_rejects_password_without_lowercase() {
        var body = Map.of(
                "email", "user@example.com",
                "password", "PASSWORD1!",
                "name", "홍길동"
        );

        assertThatThrownBy(() ->
                client.post()
                        .uri("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .toBodilessEntity()
        ).isInstanceOf(HttpClientErrorException.BadRequest.class);
    }

    @Test
    @DisplayName("회원가입 API가 숫자 없는 비밀번호를 거부한다")
    void signup_rejects_password_without_digit() {
        var body = Map.of(
                "email", "user@example.com",
                "password", "Password!",
                "name", "홍길동"
        );

        assertThatThrownBy(() ->
                client.post()
                        .uri("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .toBodilessEntity()
        ).isInstanceOf(HttpClientErrorException.BadRequest.class);
    }

    @Test
    @DisplayName("로그인 API가 유효한 자격증명으로 인증에 성공한다")
    void loginWithValidCredentials_returns200() {
        var signupBody = Map.of(
                "email", "user@example.com",
                "password", "Password1!",
                "name", "홍길동"
        );
        client.post()
                .uri("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .body(signupBody)
                .retrieve()
                .toBodilessEntity();

        var loginBody = Map.of(
                "email", "user@example.com",
                "password", "Password1!"
        );

        var response = client.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(loginBody)
                .retrieve()
                .toEntity(com.commerce.member.presentation.LoginResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("로그인 API가 성공 시 Access Token을 발급한다")
    void login_returns_access_token_on_success() {
        var signupBody = Map.of(
                "email", "user@example.com",
                "password", "Password1!",
                "name", "홍길동"
        );
        client.post()
                .uri("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .body(signupBody)
                .retrieve()
                .toBodilessEntity();

        var loginBody = Map.of(
                "email", "user@example.com",
                "password", "Password1!"
        );

        var response = client.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(loginBody)
                .retrieve()
                .toEntity(com.commerce.member.presentation.LoginResponse.class);

        assertThat(response.getBody().accessToken()).isNotBlank();
    }
}