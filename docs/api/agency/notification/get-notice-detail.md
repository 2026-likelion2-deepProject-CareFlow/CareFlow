# API 명세: 대행사 알림 단건 상세 조회

## 기본 정보

| 항목 | 내용 |
|------|------|
| Method | `GET` |
| URI | `/api/agency/notices` |
| 역할 | AGENCY |
| 설명 | [get-notifications.md](get-notifications.md), [get-notifications-by-type.md](get-notifications-by-type.md) 두 목록 조회 API에서 받은 `notificationId`로 알림 단건을 상세 조회한다. `notificationId`는 GET 요청이지만 `@RequestBody`로 전달받는다(요청 명세 지정 사항). |

## 인증 / 권한

- `@AuthenticationPrincipal CustomUserDetails`로 로그인 사용자 식별
- `userDetails.getRole() != AGENCY` → `IllegalAccessException` (401)
- `userDetails.getAgencyId()`로 소속 대행사 ID 추출
- 조회 대상 알림이 [get-notifications.md](get-notifications.md)와 동일한 "수신 대상 범위"(대행사 소속 수리기사 + 그 기사에게 A/S 받은 고객)에 속하지 않으면 → `IllegalAccessException` (401, 타 대행사 알림 열람 차단)

## 요청 바디: `AgencyNoticeDetailRequest`

```java
record AgencyNoticeDetailRequest(
    @NotNull(message = "notificationId는 필수입니다.")
    Long notificationId
)
```

## 응답: `List<AgencyNoticeDetailResponse>`

목록 조회 API와 동일한 알림 필드를 갖되, 응답은 **단건이어도 배열**로 반환한다(요청 명세 지정 사항).

```java
record AgencyNoticeDetailResponse(
    Long notificationId,
    String type,
    String title,
    String body,
    String channel,
    LocalDateTime createdAt
)
```

## 비즈니스 로직

1. role == AGENCY 검증
2. `notificationId`로 `notificationRepository.findById()` 조회 → 없으면 `NoSuchElementException` (404)
3. 알림 수신 대상 user_id 범위 산정 — `get-notifications.md`와 동일 로직 (대행사 소속 ENGINEER user_id + 대행사로부터 A/S 받은 customer user_id)
4. 조회된 알림의 `user_id`가 3의 범위에 포함되지 않으면 `IllegalAccessException` (401, 타 대행사 알림 접근 차단)
5. 통과 시 `List.of(단일 알림 응답)` 반환

## 예외 처리

| 예외 | HTTP | 조건 |
|------|------|------|
| `IllegalAccessException` | 401 | role != AGENCY, 또는 타 대행사 소속 알림 조회 시도 |
| `NoSuchElementException` | 404 | notificationId에 해당하는 알림 없음 |
| Bean Validation 실패 | 400 | `notificationId` 누락 |

---

## 단위 테스트 명세 (JUnit 5)

> `AgencyNoticeDetailServiceTest` — `@ExtendWith(MockitoExtension.class)`

### 필수 테스트 케이스

| 테스트명 | 설명 |
|---------|------|
| `정상조회_단건알림_배열로반환` | 소속 기사 수신 알림 1건 mock → 응답이 size=1인 List, 필드 매핑 정확 |
| `존재하지않는notificationId_NoSuchElementException` | repository.findById가 empty → `NoSuchElementException` |
| `타대행사알림조회시도_IllegalAccessException` | 조회된 알림의 user_id가 수신 대상 범위(소속 기사/고객)에 없음 → `IllegalAccessException` |
| `AGENCY아닌_role_예외발생` | role=CUSTOMER로 호출 → `IllegalAccessException`, repository 호출 없음(`verifyNoInteractions`) |

---

## 통합 테스트 명세 (H2 DB)

> `AgencyNoticeDetailControllerIntegrationTest` — `@SpringBootTest` + `@AutoConfigureMockMvc` + H2 인메모리 DB

### 필수 테스트 케이스

| 테스트명 | 설명 |
|---------|------|
| `소속기사알림_단건조회_200` | 소속 기사에게 발송된 Notification 직접 저장 → GET + body `{"notificationId": n}` 호출 시 200, 배열 size=1, 필드 일치 |
| `소속고객알림_단건조회_200` | AsRequest.agency를 현재 대행사로 연결한 고객에게 발송된 Notification 저장 → 200, 응답에 포함 |
| `존재하지않는ID_404` | DB에 없는 notificationId로 요청 → 404 Not Found |
| `타대행사알림_401` | 다른 대행사 소속 기사에게 발송된 Notification의 ID로 요청 → 401 Unauthorized |
| `notificationId누락_400` | 빈 바디 `{}`로 요청 → 400 Bad Request (Bean Validation) |
| `CUSTOMER권한_401` | CUSTOMER 토큰으로 요청 → 401 Unauthorized |
