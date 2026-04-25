package com.commerce.member;

import com.commerce.global.jwt.JwtProvider;
import com.commerce.member.domain.MemberRole;
import com.commerce.member.domain.MemberStatus;
import com.commerce.member.repository.MemberRepository;
import com.commerce.member.repository.RefreshTokenRepository;
import com.commerce.member.dto.SignupResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
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

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JwtProvider jwtProvider;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @BeforeEach
    void setUp() {
        client = RestClient.create("http://localhost:" + port);
        refreshTokenRepository.deleteAll();
        memberRepository.deleteAll();
    }

    // ==================== 회원가입 ====================

    @Test
    @DisplayName("회원가입 API가 유효한 이메일과 비밀번호로 회원을 등록한다")
    void signupWithValidEmailAndPassword_returns201() {
        var body = Map.of("email", "user@example.com", "password", "Password1!", "name", "홍길동");

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
        var body = Map.of("email", "USER@EXAMPLE.COM", "password", "Password1!", "name", "홍길동");

        var response = client.post()
                .uri("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(SignupResponse.class);

        assertThat(response.getBody().email()).isEqualTo("user@example.com");
    }

    @Test
    @DisplayName("회원가입 API가 비밀번호를 bcrypt로 암호화해 저장한다")
    void signup_stores_password_encoded_with_bcrypt() {
        var body = Map.of("email", "user@example.com", "password", "Password1!", "name", "홍길동");

        client.post()
                .uri("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();

        // bcrypt 해시는 HTTP 응답으로 노출되지 않으므로 DB 직접 확인 (예외적 허용)
        var member = memberRepository.findByEmail("user@example.com").orElseThrow();
        assertThat(member.getPassword())
                .startsWith("$2a$")
                .doesNotContain("Password1!");
    }

    @Test
    @DisplayName("회원가입 API가 기본 role을 CUSTOMER로 설정한다")
    void signup_sets_default_role_to_customer() {
        var body = Map.of("email", "user@example.com", "password", "Password1!", "name", "홍길동");

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
        var body = Map.of("email", "user@example.com", "password", "Password1!", "name", "홍길동");

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
        var body = Map.of("email", "user@example.com", "password", "Password1!", "name", "홍길동");

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
        var body = Map.of("email", "user@example.com", "password", "Pass1!", "name", "홍길동");

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
        var body = Map.of("email", "user@example.com", "password", "password1!", "name", "홍길동");

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
        var body = Map.of("email", "user@example.com", "password", "PASSWORD1!", "name", "홍길동");

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
        var body = Map.of("email", "user@example.com", "password", "Password!", "name", "홍길동");

        assertThatThrownBy(() ->
                client.post()
                        .uri("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .toBodilessEntity()
        ).isInstanceOf(HttpClientErrorException.BadRequest.class);
    }

    // ==================== 로그인 ====================

    @Test
    @DisplayName("로그인 API가 유효한 자격증명으로 인증에 성공한다")
    void loginWithValidCredentials_returns200() {
        signup("user@example.com");

        var response = client.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", "user@example.com", "password", "Password1!"))
                .retrieve()
                .toEntity(com.commerce.member.dto.LoginResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("로그인 API가 성공 시 Access Token을 발급한다")
    void login_returns_access_token_on_success() {
        signup("user@example.com");

        var response = login("user@example.com");

        assertThat(response.getBody().accessToken()).isNotBlank();
    }

    @Test
    @DisplayName("로그인 API가 성공 시 Refresh Token을 발급하고 DB에 저장한다")
    void login_issues_refresh_token_and_saves_to_db() {
        signup("user@example.com");

        var response = login("user@example.com");

        assertThat(response.getBody().refreshToken()).isNotBlank();
    }

    @Test
    @DisplayName("로그인 API가 성공 시 login_fail_count를 0으로 초기화한다")
    void login_resets_login_fail_count_on_success() {
        signup("user@example.com");
        var wrongBody = Map.of("email", "user@example.com", "password", "WrongPass1!");

        for (int i = 0; i < 4; i++) {
            catchThrowableOfType(
                    () -> client.post().uri("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON).body(wrongBody)
                            .retrieve().toBodilessEntity(),
                    HttpClientErrorException.Unauthorized.class
            );
        }

        login("user@example.com");

        // 초기화 후 4회 실패해도 LOCKED 아님
        for (int i = 0; i < 4; i++) {
            catchThrowableOfType(
                    () -> client.post().uri("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON).body(wrongBody)
                            .retrieve().toBodilessEntity(),
                    HttpClientErrorException.Unauthorized.class
            );
        }

        var response = login("user@example.com");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("로그인 API가 성공 시 last_login_at을 갱신한다")
    void login_updates_last_login_at_on_success() {
        signup("user@example.com");

        var response = login("user@example.com");

        assertThat(response.getBody().lastLoginAt()).isNotNull();
    }

    @Test
    @DisplayName("로그인 API가 존재하지 않는 이메일에 401 AUTH_INVALID를 반환한다")
    void login_returns_401_for_unknown_email() {
        var ex = catchThrowableOfType(
                () -> client.post()
                        .uri("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("email", "unknown@example.com", "password", "Password1!"))
                        .retrieve()
                        .toBodilessEntity(),
                HttpClientErrorException.Unauthorized.class
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getResponseBodyAsString()).contains("AUTH_INVALID");
    }

    @Test
    @DisplayName("로그인 API가 비밀번호 불일치 시 401 AUTH_INVALID를 반환한다")
    void login_returns_401_for_wrong_password() {
        signup("user@example.com");

        var ex = catchThrowableOfType(
                () -> client.post()
                        .uri("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("email", "user@example.com", "password", "WrongPass1!"))
                        .retrieve()
                        .toBodilessEntity(),
                HttpClientErrorException.Unauthorized.class
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getResponseBodyAsString()).contains("AUTH_INVALID");
    }

    @Test
    @DisplayName("로그인 API가 LOCKED 회원에 401 AUTH_INVALID를 반환한다")
    void login_returns_401_for_locked_member() {
        signup("user@example.com");
        failLogin("user@example.com", 5);

        var ex = catchThrowableOfType(
                () -> client.post()
                        .uri("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("email", "user@example.com", "password", "Password1!"))
                        .retrieve()
                        .toBodilessEntity(),
                HttpClientErrorException.Unauthorized.class
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getResponseBodyAsString()).contains("AUTH_INVALID");
    }

    @Test
    @DisplayName("로그인 API가 INACTIVE 회원에 401 AUTH_INVALID를 반환한다")
    void login_returns_401_for_inactive_member() {
        signup("user@example.com");

        // INACTIVE 상태로 전환하는 어드민 API가 없으므로 예외적으로 DB 직접 조작
        var member = memberRepository.findByEmail("user@example.com").orElseThrow();
        transactionTemplate.executeWithoutResult(status ->
                memberRepository.updateStatus(member.getId(), MemberStatus.INACTIVE));

        var ex = catchThrowableOfType(
                () -> client.post()
                        .uri("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("email", "user@example.com", "password", "Password1!"))
                        .retrieve()
                        .toBodilessEntity(),
                HttpClientErrorException.Unauthorized.class
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getResponseBodyAsString()).contains("AUTH_INVALID");
    }

    @Test
    @DisplayName("로그인 API가 5회 연속 실패 시 올바른 비밀번호로도 로그인을 차단한다")
    void login_locks_member_after_5_consecutive_failures() {
        signup("user@example.com");
        failLogin("user@example.com", 5);

        var ex = catchThrowableOfType(
                () -> client.post()
                        .uri("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("email", "user@example.com", "password", "Password1!"))
                        .retrieve()
                        .toBodilessEntity(),
                HttpClientErrorException.Unauthorized.class
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getResponseBodyAsString()).contains("AUTH_INVALID");
    }

    @Test
    @DisplayName("로그인 2번 시 첫 번째 Refresh Token이 무효화된다")
    void second_login_invalidates_previous_refresh_token() {
        signup("user@example.com");
        String firstRefreshToken = login("user@example.com").getBody().refreshToken();
        login("user@example.com"); // 두 번째 로그인 — 첫 번째 RT 삭제

        var ex = catchThrowableOfType(
                () -> client.post()
                        .uri("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("refreshToken", firstRefreshToken))
                        .retrieve()
                        .toBodilessEntity(),
                HttpClientErrorException.Unauthorized.class
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getResponseBodyAsString()).contains("TOKEN_INVALID");
    }

    // ==================== 토큰 재발급 ====================

    @Test
    @DisplayName("토큰 재발급 API가 유효한 Refresh Token으로 새 Access Token과 Refresh Token을 발급한다")
    void refresh_issues_new_tokens_with_valid_refresh_token() {
        signup("user@example.com");
        String refreshToken = login("user@example.com").getBody().refreshToken();

        var response = client.post()
                .uri("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("refreshToken", refreshToken))
                .retrieve()
                .toEntity(com.commerce.member.dto.RefreshResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().accessToken()).isNotBlank();
        assertThat(response.getBody().refreshToken()).isNotBlank();
    }

    @Test
    @DisplayName("토큰 재발급 API가 재발급 후 기존 Refresh Token을 삭제한다")
    void refresh_deletes_old_refresh_token_after_reissue() {
        signup("user@example.com");
        String oldRefreshToken = login("user@example.com").getBody().refreshToken();

        client.post()
                .uri("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("refreshToken", oldRefreshToken))
                .retrieve()
                .toBodilessEntity();

        var ex = catchThrowableOfType(
                () -> client.post()
                        .uri("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("refreshToken", oldRefreshToken))
                        .retrieve()
                        .toBodilessEntity(),
                HttpClientErrorException.Unauthorized.class
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getResponseBodyAsString()).contains("TOKEN_INVALID");
    }

    @Test
    @DisplayName("토큰 재발급 API가 만료된 Refresh Token에 401 TOKEN_EXPIRED를 반환한다")
    void refresh_returns_401_for_expired_refresh_token() {
        var expiredProvider = new JwtProvider(jwtSecret, 1800000L, -1L);
        String expiredToken = expiredProvider.generateRefreshToken(999L);

        var ex = catchThrowableOfType(
                () -> client.post()
                        .uri("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("refreshToken", expiredToken))
                        .retrieve()
                        .toBodilessEntity(),
                HttpClientErrorException.Unauthorized.class
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getResponseBodyAsString()).contains("TOKEN_EXPIRED");
    }

    @Test
    @DisplayName("토큰 재발급 API가 유효하지 않은 Refresh Token에 401 TOKEN_INVALID를 반환한다")
    void refresh_returns_401_for_invalid_refresh_token() {
        var ex = catchThrowableOfType(
                () -> client.post()
                        .uri("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("refreshToken", "this.is.not.a.valid.jwt.token"))
                        .retrieve()
                        .toBodilessEntity(),
                HttpClientErrorException.Unauthorized.class
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getResponseBodyAsString()).contains("TOKEN_INVALID");
    }

    @Test
    @DisplayName("토큰 재발급 API가 Access Token을 Refresh Token으로 사용하면 거부한다")
    void refresh_rejects_access_token_used_as_refresh_token() {
        signup("user@example.com");
        String accessToken = login("user@example.com").getBody().accessToken();

        var ex = catchThrowableOfType(
                () -> client.post()
                        .uri("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("refreshToken", accessToken))
                        .retrieve()
                        .toBodilessEntity(),
                HttpClientErrorException.Unauthorized.class
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getResponseBodyAsString()).contains("TOKEN_INVALID");
    }

    @Test
    @DisplayName("토큰 재발급 API가 이미 사용된 Refresh Token 재사용 시 401 TOKEN_INVALID를 반환한다")
    void refresh_returns_401_when_reusing_already_used_token() {
        signup("user@example.com");
        String refreshToken = login("user@example.com").getBody().refreshToken();
        var refreshBody = Map.of("refreshToken", refreshToken);

        client.post()
                .uri("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .body(refreshBody)
                .retrieve()
                .toBodilessEntity();

        var ex = catchThrowableOfType(
                () -> client.post()
                        .uri("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(refreshBody)
                        .retrieve()
                        .toBodilessEntity(),
                HttpClientErrorException.Unauthorized.class
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getResponseBodyAsString()).contains("TOKEN_INVALID");
    }

    // ==================== 로그아웃 ====================

    @Test
    @DisplayName("로그아웃 API가 로그아웃 시 Refresh Token을 삭제한다")
    void logout_deletes_refresh_token() {
        signup("user@example.com");
        var loginBody = login("user@example.com").getBody();
        String accessToken = loginBody.accessToken();
        String refreshToken = loginBody.refreshToken();

        var logoutResponse = client.post()
                .uri("/api/v1/auth/logout")
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .toBodilessEntity();

        assertThat(logoutResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        var ex = catchThrowableOfType(
                () -> client.post()
                        .uri("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("refreshToken", refreshToken))
                        .retrieve()
                        .toBodilessEntity(),
                HttpClientErrorException.Unauthorized.class
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getResponseBodyAsString()).contains("TOKEN_INVALID");
    }

    @Test
    @DisplayName("로그아웃 API가 인증 없이 요청 시 401을 반환한다")
    void logout_returns_401_without_authentication() {
        var ex = catchThrowableOfType(
                () -> client.post()
                        .uri("/api/v1/auth/logout")
                        .retrieve()
                        .toBodilessEntity(),
                HttpClientErrorException.Unauthorized.class
        );

        assertThat(ex).isNotNull();
    }

    // ==================== 회원 탈퇴 ====================

    @Test
    @DisplayName("회원 탈퇴 후 동일 계정으로 로그인이 차단된다")
    void withdraw_blocks_login_after_withdrawal() {
        signup("user@example.com");
        String accessToken = login("user@example.com").getBody().accessToken();

        client.delete()
                .uri("/api/v1/members/me")
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .toBodilessEntity();

        var ex = catchThrowableOfType(
                () -> client.post()
                        .uri("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("email", "user@example.com", "password", "Password1!"))
                        .retrieve()
                        .toBodilessEntity(),
                HttpClientErrorException.Unauthorized.class
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getResponseBodyAsString()).contains("AUTH_INVALID");
    }

    @Test
    @DisplayName("회원 탈퇴 API가 탈퇴 시 Refresh Token을 삭제한다")
    void withdraw_deletes_refresh_token() {
        signup("user@example.com");
        var loginBody = login("user@example.com").getBody();
        String accessToken = loginBody.accessToken();
        String refreshToken = loginBody.refreshToken();

        client.delete()
                .uri("/api/v1/members/me")
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .toBodilessEntity();

        var ex = catchThrowableOfType(
                () -> client.post()
                        .uri("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("refreshToken", refreshToken))
                        .retrieve()
                        .toBodilessEntity(),
                HttpClientErrorException.Unauthorized.class
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getResponseBodyAsString()).contains("TOKEN_INVALID");
    }

    @Test
    @DisplayName("회원 탈퇴 API가 탈퇴 후 동일 이메일로 재가입을 허용한다")
    void withdraw_allows_re_signup_with_same_email() {
        signup("user@example.com");
        String accessToken = login("user@example.com").getBody().accessToken();

        client.delete()
                .uri("/api/v1/members/me")
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .toBodilessEntity();

        var reSignupResponse = client.post()
                .uri("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", "user@example.com", "password", "Password1!", "name", "홍길동"))
                .retrieve()
                .toEntity(SignupResponse.class);

        assertThat(reSignupResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(reSignupResponse.getBody().email()).isEqualTo("user@example.com");
    }

    @Test
    @DisplayName("회원 탈퇴 API가 인증 없이 요청 시 401을 반환한다")
    void withdraw_returns_401_without_authentication() {
        var ex = catchThrowableOfType(
                () -> client.delete()
                        .uri("/api/v1/members/me")
                        .retrieve()
                        .toBodilessEntity(),
                HttpClientErrorException.Unauthorized.class
        );

        assertThat(ex).isNotNull();
    }

    // ==================== JWT 인증 필터 ====================

    @Test
    @DisplayName("인증 필터가 유효한 Access Token으로 인증 컨텍스트를 설정한다")
    void authFilter_sets_authentication_context_with_valid_access_token() {
        signup("user@example.com");
        String accessToken = login("user@example.com").getBody().accessToken();

        var response = client.get()
                .uri("/api/v1/members/me")
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .toEntity(com.commerce.member.controller.MemberController.MemberInfo.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().role()).isEqualTo("CUSTOMER");
    }

    @Test
    @DisplayName("인증 필터가 만료된 Access Token으로 인증 컨텍스트를 비운다")
    void authFilter_clears_context_with_expired_access_token() {
        var expiredProvider = new JwtProvider(jwtSecret, -1L, 604800000L);
        String expiredToken = expiredProvider.generateAccessToken(1L, "CUSTOMER");

        var ex = catchThrowableOfType(
                () -> client.get()
                        .uri("/api/v1/members/me")
                        .header("Authorization", "Bearer " + expiredToken)
                        .retrieve()
                        .toBodilessEntity(),
                HttpClientErrorException.Unauthorized.class
        );

        assertThat(ex).isNotNull();
    }

    @Test
    @DisplayName("인증 필터가 서명이 잘못된 Access Token으로 인증 컨텍스트를 비운다")
    void authFilter_clears_context_with_wrong_signature_access_token() {
        var wrongKeyProvider = new JwtProvider(
                "d3JvbmdTZWNyZXRLZXlGb3JUZXN0aW5nUHVycG9zZU9ubHkx",
                1800000L,
                604800000L
        );
        String wrongSignatureToken = wrongKeyProvider.generateAccessToken(1L, "CUSTOMER");

        var ex = catchThrowableOfType(
                () -> client.get()
                        .uri("/api/v1/members/me")
                        .header("Authorization", "Bearer " + wrongSignatureToken)
                        .retrieve()
                        .toBodilessEntity(),
                HttpClientErrorException.Unauthorized.class
        );

        assertThat(ex).isNotNull();
    }

    @Test
    @DisplayName("인증 필터가 Refresh Token을 Access Token으로 사용하면 인증 컨텍스트를 비운다")
    void authFilter_clears_context_when_refresh_token_used_as_access_token() {
        signup("user@example.com");
        String refreshToken = login("user@example.com").getBody().refreshToken();

        var ex = catchThrowableOfType(
                () -> client.get()
                        .uri("/api/v1/members/me")
                        .header("Authorization", "Bearer " + refreshToken)
                        .retrieve()
                        .toBodilessEntity(),
                HttpClientErrorException.Unauthorized.class
        );

        assertThat(ex).isNotNull();
    }

    @Test
    @DisplayName("인증 필터가 Bearer 없는 Authorization 헤더를 무시하고 401을 반환한다")
    void authFilter_ignores_authorization_header_without_bearer_prefix() {
        signup("user@example.com");
        String accessToken = login("user@example.com").getBody().accessToken();

        var ex = catchThrowableOfType(
                () -> client.get()
                        .uri("/api/v1/members/me")
                        .header("Authorization", accessToken) // "Bearer " 없음
                        .retrieve()
                        .toBodilessEntity(),
                HttpClientErrorException.Unauthorized.class
        );

        assertThat(ex).isNotNull();
    }

    @Test
    @DisplayName("인증 필터가 형식이 깨진 Bearer 토큰에 401을 반환한다")
    void authFilter_rejects_malformed_bearer_token() {
        var ex = catchThrowableOfType(
                () -> client.get()
                        .uri("/api/v1/members/me")
                        .header("Authorization", "Bearer not-a-valid-token")
                        .retrieve()
                        .toBodilessEntity(),
                HttpClientErrorException.Unauthorized.class
        );

        assertThat(ex).isNotNull();
    }

    @Test
    @DisplayName("인증 필터가 ADMIN 권한 없는 회원의 admin 엔드포인트 접근 시 403을 반환한다")
    void authFilter_returns_403_for_customer_accessing_admin_endpoint() {
        signup("user@example.com");
        String accessToken = login("user@example.com").getBody().accessToken();

        var ex = catchThrowableOfType(
                () -> client.get()
                        .uri("/api/v1/admin/test")
                        .header("Authorization", "Bearer " + accessToken)
                        .retrieve()
                        .toBodilessEntity(),
                HttpClientErrorException.Forbidden.class
        );

        assertThat(ex).isNotNull();
    }

    @Test
    @DisplayName("만료된 Access Token을 Refresh Token으로 재발급 받을 수 있다")
    void expired_access_token_can_be_renewed_with_refresh_token() {
        signup("user@example.com");
        var loginBody = login("user@example.com").getBody();
        String refreshToken = loginBody.refreshToken();

        var claims = jwtProvider.parse(loginBody.accessToken());
        Long memberId = Long.parseLong(claims.getSubject());
        String role = claims.get("role", String.class);

        var expiredProvider = new JwtProvider(jwtSecret, -1L, 604800000L);
        String expiredAccess = expiredProvider.generateAccessToken(memberId, role);

        catchThrowableOfType(
                () -> client.get()
                        .uri("/api/v1/members/me")
                        .header("Authorization", "Bearer " + expiredAccess)
                        .retrieve()
                        .toBodilessEntity(),
                HttpClientErrorException.Unauthorized.class
        );

        var refreshResponse = client.post()
                .uri("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("refreshToken", refreshToken))
                .retrieve()
                .toEntity(com.commerce.member.dto.RefreshResponse.class);

        String newAccessToken = refreshResponse.getBody().accessToken();

        var meResponse = client.get()
                .uri("/api/v1/members/me")
                .header("Authorization", "Bearer " + newAccessToken)
                .retrieve()
                .toEntity(com.commerce.member.controller.MemberController.MemberInfo.class);

        assertThat(meResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ==================== 헬퍼 메서드 ====================

    private void signup(String email) {
        client.post()
                .uri("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", "Password1!", "name", "홍길동"))
                .retrieve()
                .toBodilessEntity();
    }

    private org.springframework.http.ResponseEntity<com.commerce.member.dto.LoginResponse> login(String email) {
        return client.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", "Password1!"))
                .retrieve()
                .toEntity(com.commerce.member.dto.LoginResponse.class);
    }

    private void failLogin(String email, int times) {
        var wrongBody = Map.of("email", email, "password", "WrongPass1!");
        for (int i = 0; i < times; i++) {
            catchThrowableOfType(
                    () -> client.post().uri("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON).body(wrongBody)
                            .retrieve().toBodilessEntity(),
                    HttpClientErrorException.Unauthorized.class
            );
        }
    }
}