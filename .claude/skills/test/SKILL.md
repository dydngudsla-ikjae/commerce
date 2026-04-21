---
name: test
description: Write integration tests for Java Spring Boot using JUnit5 and H2. Use when writing tests, adding test coverage, or asked to test a feature. Classicist style, tests the outermost layer with real Spring context and H2 database.
allowed-tools: Read, Grep, Glob, Bash(./gradlew test:*)
---

# Spring Boot 통합 테스트 가이드

## 핵심 원칙
- Mock 사용 금지. 실제 Spring Context + H2 DB로 테스트
- 가장 바깥쪽 레이어에서 실제 HTTP 요청/응답으로 테스트
- 구조: Arrange → Act → Assert
- 검증은 응답(status, body) 또는 검증용 추가 요청으로만 확인. Repository 직접 조회 금지

## 기본 셋업

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Transactional
class FeatureTest {

    @LocalServerPort
    private int port;

    private RestClient client;

    @BeforeEach
    void setUp() {
        client = RestClient.create("http://localhost:" + port);
    }
}
```

## 테스트 작성 패턴

```java
@Test
@DisplayName("유효한 요청이면 200을 반환한다")
void validRequestReturns200() {
    // arrange
    var request = new SomeRequest("value");

    // act
    var response = client.post()
        .uri("/api/some-endpoint")
        .contentType(MediaType.APPLICATION_JSON)
        .body(request)
        .retrieve()
        .toEntity(SomeResponse.class);

    // assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().getField()).isEqualTo("expected");
}

@Test
@DisplayName("생성 후 조회하면 반영된 데이터를 반환한다")
void createdResourceIsRetrievable() {
    // arrange
    var createRequest = new CreateRequest("test");
    client.post()
        .uri("/api/some-endpoint")
        .body(createRequest)
        .retrieve()
        .toBodilessEntity();

    // act (검증용 추가 요청)
    var response = client.get()
        .uri("/api/some-endpoint/1")
        .retrieve()
        .toEntity(SomeResponse.class);

    // assert
    assertThat(response.getBody().getName()).isEqualTo("test");
}
```

## 예외 케이스 처리

```java
@Test
@DisplayName("잘못된 요청이면 400을 반환한다")
void invalidRequestReturns400() {
    // arrange
    var invalidRequest = new SomeRequest("");

    // act & assert
    assertThatThrownBy(() ->
        client.post()
            .uri("/api/some-endpoint")
            .body(invalidRequest)
            .retrieve()
            .toEntity(ErrorResponse.class)
    ).isInstanceOf(HttpClientErrorException.BadRequest.class);
}
```

## 규칙
1. 테스트 메서드명은 camelCase 영어로 작성하고 @DisplayName("") 이용해서 한글 매핑한다.
2. 상태 검증은 응답(status code, response body) 또는 검증용 GET 요청으로만 확인
3. Repository 직접 조회 금지
4. @Transactional로 테스트 간 데이터 격리 보장
5. 테스트 작성 후 `./gradlew test` 실행하여 통과 확인
