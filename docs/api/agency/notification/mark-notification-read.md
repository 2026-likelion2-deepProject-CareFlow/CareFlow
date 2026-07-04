# API 명세: 대행사 알림 읽음 처리

## 기본 정보

| 항목 | 내용 |
|------|------|
| Method | `PATCH` |
| URI | `/api/agency/notifications/{notificationId}/read` |
| 역할 | AGENCY |
| 설명 | 대행사 알림센터([get-notifications.md](./get-notifications.md))에서 특정 알림을 클릭했을 때 해당 알림을 읽음(`is_read = true`) 처리한다. `unreadCount`는 이 API가 직접 내려주지 않으며, 프론트엔드가 처리 후 `GET /api/agency/notifications`를 재호출해 최신 `stats.unreadCount`를 반영한다(1번 방식 채택 — PATCH 응답에 unreadCount를 포함하지 않음). |

## 인증 / 권한

- `@AuthenticationPrincipal CustomUserDetails`로 로그인 사용자 식별
- `userDetails.getRole() != AGENCY` → `IllegalAccessException` (401)
- `userDetails.getAgencyId()`로 소속 대행사 ID 추출
- **소속 검증**: 요청한 `notificationId`의 수신자(`notifications.user_id`)가 이 대행사의 알림 수신 대상 범위(소속 수리기사 + 그 수리기사에게 A/S를 받은 고객, [get-notifications.md](./get-notifications.md)와 동일 로직)에 속하지 않으면 `IllegalAccessException` (401) — 타 대행사 알림을 임의로 읽음 처리하는 것을 차단

## 요청 파라미터

| 파라미터 | 위치 | 타입 | 필수 | 설명 |
|---------|------|------|------|------|
| `notificationId` | Path | Long | Y | 읽음 처리할 알림 ID |

요청 바디 없음.

## 응답

- 성공 시 `204 No Content` (바디 없음)
- 프론트엔드는 응답을 받은 뒤 `GET /api/agency/notifications`를 다시 호출하여 갱신된 `content[].isRead`와 `stats.unreadCount`를 반영한다.

## 비즈니스 로직

1. role == AGENCY 검증
2. `notificationRepository.findById(notificationId)` 조회 — 없으면 `NoSuchElementException` (404)
3. `userRepository`/`asRequestRepository`로 이 대행사의 알림 수신 대상 `user_id` 목록 산정 (`get-notifications.md`의 기존 로직 재사용)
4. 조회한 알림의 `user_id`가 위 목록에 없으면 `IllegalAccessException` (401)
5. `notification.markAsRead()` 호출 (더티 체킹으로 UPDATE, 이미 읽음 상태여도 재호출 가능 — 멱등)

## 예외 처리

| 예외 | HTTP | 조건 |
|------|------|------|
| `IllegalAccessException` | 401 | role != AGENCY, 또는 본인 대행사 수신 범위 밖의 알림 |
| `NoSuchElementException` | 404 | 존재하지 않는 `notificationId` |
| 정상 | 204 | 읽음 처리 성공 (이미 읽음 상태였어도 204, 멱등 처리) |

---

## 단위 테스트 명세 (JUnit 5) — 반드시 수행

> `AgencyNotificationServiceTest`에 `@Nested` 클래스 추가 — `@ExtendWith(MockitoExtension.class)`

### 필수 테스트 케이스

| 테스트명 | 설명 |
|---------|------|
| `정상_소속기사알림_읽음처리` | notification.user_id가 소속 기사 user_id 목록에 포함 → `markAsRead()` 호출 검증 |
| `정상_소속고객알림_읽음처리` | notification.user_id가 소속 고객 user_id 목록에 포함 → `markAsRead()` 호출 검증 |
| `이미읽음상태_재호출해도_정상처리` | `is_read = true`인 알림에 대해 재호출 → 예외 없이 `markAsRead()` 재호출됨(멱등) |
| `존재하지않는알림_NoSuchElementException` | `notificationRepository.findById()`가 empty → `NoSuchElementException`, `markAsRead()` 미호출 |
| `타대행사알림_IllegalAccessException` | notification.user_id가 수신 대상 목록에 없음(다른 대행사 소속) → `IllegalAccessException`, `markAsRead()` 미호출 |
| `AGENCY아닌_role_예외발생` | role=CUSTOMER로 호출 → `IllegalAccessException`, repository 호출 없음(`verifyNoInteractions`) |

---

## 통합 테스트 명세 (H2 DB) — 반드시 수행

> `AgencyNotificationControllerIntegrationTest`에 `@Nested` 클래스 추가 — `@SpringBootTest` + `@AutoConfigureMockMvc` + H2 인메모리 DB

### 필수 테스트 케이스

| 테스트명 | 설명 |
|---------|------|
| `소속기사알림_읽음처리_204` | 소속 기사에게 발송된 `is_read=false` 알림 저장 → PATCH 호출 시 204, 이후 DB에서 `is_read=true` 확인 |
| `소속고객알림_읽음처리_204` | 대행사로부터 A/S를 받은 고객에게 발송된 알림 저장 → PATCH 호출 시 204, DB 반영 확인 |
| `읽음처리후_GET재호출시_unreadCount감소확인` | `is_read=false` 알림 2건 저장 → 1건 PATCH로 읽음 처리 → 이어서 GET 호출 시 `stats.unreadCount`가 2→1로 감소했는지 확인 (1번 방식 검증의 핵심 케이스) |
| `이미읽음상태_재호출_204` | `is_read=true`인 알림에 재차 PATCH 호출 → 여전히 204(멱등) |
| `존재하지않는알림_404` | 존재하지 않는 `notificationId`로 PATCH → 404 Not Found |
| `타대행사알림_401` | 다른 대행사 소속 기사/고객에게 발송된 알림을 현재 대행사 토큰으로 PATCH 시도 → 401 Unauthorized, DB의 `is_read` 값 변경되지 않았는지 확인 |
| `CUSTOMER권한_401` | CUSTOMER 토큰으로 호출 → 401 Unauthorized |

---

## 구현 시 유의사항

- `AgencyNotificationService.getNotifications()`에 이미 존재하는 "수신 대상 `user_id` 목록 산정" 로직을 `resolveRecipientIds(Long agencyId)` 형태의 private 메서드로 추출하여 `getNotifications()`와 `markAsRead()` 양쪽에서 재사용한다 (중복 제거).
- `Notification.markAsRead()` 도메인 메서드는 이미 구현되어 있으나 지금까지 호출하는 곳이 전혀 없었음 — 이 API가 최초의 호출 지점이다.
