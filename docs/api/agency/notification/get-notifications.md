# API 명세: 대행사 알림센터 목록 조회

## 기본 정보

| 항목 | 내용 |
|------|------|
| Method | `GET` |
| URI | `/api/agency/notifications` |
| 역할 | AGENCY |
| 설명 | 현재 로그인한 관리자가 속한 대행사 기준으로 발행된 알림 전체를 페이징 조회한다. `notifications` 테이블에는 대행사를 직접 구분하는 컬럼이 없으므로, "대행사 소속 수리기사" 또는 "그 수리기사에게 A/S를 받은 고객"에게 발송된 알림을 찾아서 반환한다. |

## 인증 / 권한

- `@AuthenticationPrincipal CustomUserDetails`로 로그인 사용자 식별
- `userDetails.getRole() != AGENCY` → `IllegalAccessException` (401)
- `userDetails.getAgencyId()`로 소속 대행사 ID 추출

## 요청 파라미터

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---------|------|------|--------|------|
| `page` | int | N | 0 | 페이지 번호 (0-based) |
| `size` | int | N | 10 | 페이지 크기 |

## 알림 수신 대상 범위 정의 (핵심 로직)

`notifications.user_id`가 아래 두 그룹 중 하나에 속하면 "이 대행사의 알림"으로 간주한다.

1. **대행사 소속 수리기사** — `users WHERE role = 'ENGINEER' AND agency_id = :agencyId`
2. **그 대행사로부터 A/S를 받은 고객** — `as_requests WHERE agency_id = :agencyId` 에 등장하는 `customer_id` (DISTINCT)

위 두 그룹의 `user_id` 합집합을 구한 뒤, `notifications.user_id IN (합집합)` 조건으로 조회한다. 합집합이 비어 있으면 DB 조회 없이 빈 결과(통계 0, content 빈 배열)를 즉시 반환한다.

## 응답 DTO: `AgencyNotificationResponse`

```java
record AgencyNotificationResponse(
    Stats stats,
    List<NotificationItem> content,
    long totalElements,
    int totalPages,
    int currentPage,
    int size
)

record Stats(
    long totalCount,   // 위 수신 대상 범위 내 전체 알림 건수 (= totalElements)
    long unreadCount,  // 위 범위 내 is_read = false 건수
    long todayCount    // 위 범위 내 오늘(00:00~24:00) created_at 건수
)

record NotificationItem(
    Long notificationId,
    String type,        // AS_STATUS / CONSUMABLE / WARRANTY / LMS
    String title,
    String body,
    String channel,      // SSE / PUSH / SMS / KAKAO
    LocalDateTime createdAt
)
```

## 비즈니스 로직

1. role == AGENCY 검증
2. `userRepository`에서 `agencyId` 소속 ENGINEER의 `user_id` 목록 조회
3. `asRequestRepository`에서 `agencyId` 기준 `customer_id` DISTINCT 목록 조회
4. 2, 3을 합쳐 수신 대상 `user_id` 목록 생성 (합집합이 비면 즉시 빈 응답 반환)
5. `notificationRepository`에서 해당 `user_id` 목록 + `Pageable`(createdAt DESC)로 `Page<Notification>` 조회 → `content`, `totalElements`, `totalPages`, `currentPage`, `size` 매핑
6. 동일 `user_id` 목록 기준으로 `unreadCount`(`is_read = false`), `todayCount`(`created_at >= 오늘 00:00 AND < 내일 00:00`) 각각 집계 쿼리 실행
7. `totalCount` = `totalElements`와 동일 값 사용

## 예외 처리

| 예외 | HTTP | 조건 |
|------|------|------|
| `IllegalAccessException` | 401 | role != AGENCY |
| 정상(빈 결과) | 200 OK | 수신 대상 알림이 없는 경우에도 stats 0 / content 빈 배열로 200 반환 (목록 조회 API이므로 204 처리하지 않음) |

---

## 단위 테스트 명세 (JUnit 5)

> `AgencyNotificationServiceTest` — `@ExtendWith(MockitoExtension.class)`

### 필수 테스트 케이스

| 테스트명 | 설명 |
|---------|------|
| `정상조회_기사와고객알림_합쳐서반환` | 소속 기사 user_id 목록 + 소속 고객 user_id 목록을 모두 stub → 두 그룹의 알림이 모두 content에 포함되는지 검증 |
| `수신대상없음_빈결과반환` | 소속 기사/고객이 모두 없음 → notificationRepository 호출 없이 stats 전부 0, content 빈 리스트 |
| `unreadCount_isRead_false건수만집계` | is_read=false 2건, true 3건 mock → unreadCount=2 |
| `todayCount_오늘생성건만집계` | 오늘 생성 1건, 어제 생성 1건 mock → todayCount=1 |
| `페이징_요청파라미터_그대로반영` | page=2, size=5 요청 → totalPages/currentPage/size 응답에 그대로 매핑 |
| `AGENCY아닌_role_예외발생` | role=CUSTOMER로 호출 → `IllegalAccessException`, repository 호출 없음(`verifyNoInteractions`) |

---

## 통합 테스트 명세 (H2 DB)

> `AgencyNotificationControllerIntegrationTest` — `@SpringBootTest` + `@AutoConfigureMockMvc` + H2 인메모리 DB

### 필수 테스트 케이스

| 테스트명 | 설명 |
|---------|------|
| `소속기사알림_목록에포함` | Agency, Engineer(소속) 직접 저장 후 해당 기사에게 발송된 Notification 저장 → GET 호출 시 응답 content에 포함 확인 |
| `소속고객알림_목록에포함` | AsRequest.agency를 현재 대행사로 설정한 Customer에게 발송된 Notification 저장 → 응답 content에 포함 확인 |
| `타대행사알림_제외` | 다른 대행사 소속 기사/고객에게 발송된 Notification 저장 → 응답에 포함되지 않음 확인 |
| `unreadCount_DB값과일치` | is_read=false 알림 2건, true 1건 저장 → 응답 stats.unreadCount=2 |
| `todayCount_DB값과일치` | created_at을 오늘/어제로 나눠 저장 → stats.todayCount가 오늘 건수만 집계됐는지 확인 |
| `페이징_DB에서_정상조회` | 알림 15건 저장 후 size=10으로 조회 → 1페이지 10건, totalElements=15, totalPages=2 확인 |
| `CUSTOMER권한_401` | CUSTOMER 토큰으로 호출 → 401 Unauthorized |
| `알림없음_200_빈배열` | 수신 대상 알림이 전혀 없는 대행사로 조회 → 200 OK, content 빈 배열, stats 전부 0 |
