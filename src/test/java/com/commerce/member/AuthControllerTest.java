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
    private TransactionTemplate transactionTemplate;

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

    @Test
    @DisplayName("로그인 API가 성공 시 Refresh Token을 발급하고 DB에 저장한다")
    void login_issues_refresh_token_and_saves_to_db() {
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

        assertThat(response.getBody().refreshToken()).isNotBlank();
    }

    @Test
    @DisplayName("로그인 API가 성공 시 login_fail_count를 0으로 초기화한다")
    void login_resets_login_fail_count_on_success() {
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

        var wrongBody = Map.of("email", "user@example.com", "password", "WrongPass1!");
        var correctBody = Map.of("email", "user@example.com", "password", "Password1!");

        // 4회 실패 (임계치 5회 미만)
        for (int i = 0; i < 4; i++) {
            catchThrowableOfType(
                    () -> client.post()
                            .uri("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(wrongBody)
                            .retrieve()
                            .toBodilessEntity(),
                    HttpClientErrorException.Unauthorized.class
            );
        }

        // 성공 로그인으로 fail_count 초기화
        client.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(correctBody)
                .retrieve()
                .toBodilessEntity();

        // 초기화되었으면 4번 더 실패해도 LOCKED가 아님 (총 4번 실패 → 잠기지 않아야)
        for (int i = 0; i < 4; i++) {
            catchThrowableOfType(
                    () -> client.post()
                            .uri("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(wrongBody)
                            .retrieve()
                            .toBodilessEntity(),
                    HttpClientErrorException.Unauthorized.class
            );
        }

        var response = client.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(correctBody)
                .retrieve()
                .toEntity(com.commerce.member.presentation.LoginResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("로그인 API가 성공 시 last_login_at을 갱신한다")
    void login_updates_last_login_at_on_success() {
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

        assertThat(response.getBody().lastLoginAt()).isNotNull();
    }

    @Test
    @DisplayName("로그인 API가 비밀번호 불일치 시 401 AUTH_INVALID를 반환한다")
    void login_returns_401_for_wrong_password() {
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
                "password", "WrongPass1!"
        );

        var ex = catchThrowableOfType(
                () -> client.post()
                        .uri("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(loginBody)
                        .retrieve()
                        .toBodilessEntity(),
                HttpClientErrorException.Unauthorized.class
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getResponseBodyAsString()).contains("AUTH_INVALID");
    }

    @Test
    @DisplayName("로그인 API가 5회 연속 실패 시 회원 상태를 LOCKED로 전환한다")
    void login_locks_member_after_5_consecutive_failures() {
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

        var wrongBody = Map.of("email", "user@example.com", "password", "WrongPass1!");

        // 5회 연속 실패
        for (int i = 0; i < 5; i++) {
            catchThrowableOfType(
                    () -> client.post()
                            .uri("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(wrongBody)
                            .retrieve()
                            .toBodilessEntity(),
                    HttpClientErrorException.Unauthorized.class
            );
        }

        // 올바른 비밀번호로도 로그인 불가 (LOCKED 상태)
        var correctBody = Map.of("email", "user@example.com", "password", "Password1!");
        var ex = catchThrowableOfType(
                () -> client.post()
                        .uri("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(correctBody)
                        .retrieve()
                        .toBodilessEntity(),
                HttpClientErrorException.Unauthorized.class
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getResponseBodyAsString()).contains("AUTH_INVALID");
    }

    @Test
    @DisplayName("로그인 API가 인증 실패 시 login_fail_count를 원자적으로 증가시킨다")
    void login_increments_login_fail_count_atomically() {
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

        var wrongBody = Map.of("email", "user@example.com", "password", "WrongPass1!");

        // 3회 실패
        for (int i = 0; i < 3; i++) {
            catchThrowableOfType(
                    () -> client.post()
                            .uri("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(wrongBody)
                            .retrieve()
                            .toBodilessEntity(),
                    HttpClientErrorException.Unauthorized.class
            );
        }

        // 4번째 실패 후 올바른 비밀번호로 로그인 가능해야 함 (아직 LOCKED 아님)
        var correctBody = Map.of("email", "user@example.com", "password", "Password1!");
        var response = client.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(correctBody)
                .retrieve()
                .toEntity(com.commerce.member.presentation.LoginResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        // 성공 후 실패 카운트 초기화 확인: 다시 4번 실패해도 LOCKED 아님
        for (int i = 0; i < 4; i++) {
            catchThrowableOfType(
                    () -> client.post()
                            .uri("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(wrongBody)
                            .retrieve()
                            .toBodilessEntity(),
                    HttpClientErrorException.Unauthorized.class
            );
        }

        var response2 = client.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(correctBody)
                .retrieve()
                .toEntity(com.commerce.member.presentation.LoginResponse.class);

        assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("로그인 API가 INACTIVE 회원에 401 AUTH_INVALID를 반환한다")
    void login_returns_401_for_inactive_member() {
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

        // 회원 상태를 INACTIVE로 변경 (테스트 픽스처 설정)
        var member = memberRepository.findByEmail("user@example.com").orElseThrow();
        transactionTemplate.executeWithoutResult(status ->
                memberRepository.updateStatus(member.getId(), MemberStatus.INACTIVE));

        var loginBody = Map.of("email", "user@example.com", "password", "Password1!");
        var ex = catchThrowableOfType(
                () -> client.post()
                        .uri("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(loginBody)
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

        // 5회 실패로 LOCKED 전환
        var wrongBody = Map.of("email", "user@example.com", "password", "WrongPass1!");
        for (int i = 0; i < 5; i++) {
            catchThrowableOfType(
                    () -> client.post()
                            .uri("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(wrongBody)
                            .retrieve()
                            .toBodilessEntity(),
                    HttpClientErrorException.Unauthorized.class
            );
        }

        // 올바른 비밀번호로 시도해도 LOCKED이므로 거부
        var correctBody = Map.of("email", "user@example.com", "password", "Password1!");
        var ex = catchThrowableOfType(
                () -> client.post()
                        .uri("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(correctBody)
                        .retrieve()
                        .toBodilessEntity(),
                HttpClientErrorException.Unauthorized.class
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getResponseBodyAsString()).contains("AUTH_INVALID");
    }

    @Test
    @DisplayName("로그인 API가 존재하지 않는 이메일에 401 AUTH_INVALID를 반환한다")
    void login_returns_401_for_unknown_email() {
        var loginBody = Map.of(
                "email", "unknown@example.com",
                "password", "Password1!"
        );

        var ex = catchThrowableOfType(
                () -> client.post()
                        .uri("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(loginBody)
                        .retrieve()
                        .toBodilessEntity(),
                HttpClientErrorException.Unauthorized.class
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getResponseBodyAsString()).contains("AUTH_INVALID");
    }
}