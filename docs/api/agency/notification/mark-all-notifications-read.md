# API 명세: 대행사 알림 전체 읽음 처리

## 기본 정보

| 항목 | 내용 |
|------|------|
| Method | `PATCH` |
| URI | `/api/agency/notifications/read-all` |
| 역할 | AGENCY |
| 설명 | 대행사 알림센터([get-notifications.md](./get-notifications.md))에서 "모두 읽음 처리" 버튼 클릭 시, 이 대행사의 알림 수신 대상 범위(소속 수리기사 + 그 수리기사에게 A/S를 받은 고객) 전체의 미열람 알림을 일괄 읽음 처리한다. `read-all`은 결과가 결정적(전체가 읽음 → `unreadCount`는 반드시 0)이므로, [mark-notification-read.md](./mark-notification-read.md)(단건 읽음 처리)와 달리 GET 재호출 없이 프론트엔드가 즉시 로컬 상태(`content[].isRead = true` 전체, `stats.unreadCount = 0`)로 갱신한다. |

## 인증 / 권한

- `@AuthenticationPrincipal CustomUserDetails`로 로그인 사용자 식별
- `userDetails.getRole() != AGENCY` → `IllegalAccessException` (401)
- `userDetails.getAgencyId()`로 소속 대행사 ID 추출
- 별도의 리소스 단위 소유권 검증은 불필요 — 애초에 이 대행사의 수신 대상 범위 안에서만 UPDATE가 실행되므로 타 대행사 데이터는 조건절에 의해 자연히 제외된다.

## 요청 파라미터

없음 (Path/Query/Body 전부 없음).

## 응답

- 성공 시 `204 No Content` (바디 없음)
- 대상 알림이 이미 0건(전부 읽음 상태이거나 수신 대상 자체가 없음)이어도 에러 없이 `204` (멱등)

## 비즈니스 로직

1. role == AGENCY 검증
2. `get-notifications.md`와 동일한 로직으로 이 대행사의 알림 수신 대상 `user_id` 목록 산정 (`resolveRecipientIds`, [mark-notification-read.md](./mark-notification-read.md)에서 이미 추출한 private 메서드 재사용)
3. 수신 대상이 비어 있으면 UPDATE 쿼리 없이 즉시 종료 (204)
4. `UPDATE notifications SET is_read = true WHERE user_id IN (:recipientIds) AND is_read = false` 벌크 업데이트 실행 — 건별 엔티티 로딩 없이 JPQL 벌크 쿼리로 처리 (대상이 많을 수 있으므로 N+1 방지)

## 예외 처리

| 예외 | HTTP | 조건 |
|------|------|------|
| `IllegalAccessException` | 401 | role != AGENCY |
| 정상 | 204 | 처리 성공 (대상 0건이어도 204, 멱등) |

---

## 단위 테스트 명세 (JUnit 5) — 반드시 수행

> `AgencyNotificationServiceTest`에 `@Nested` 클래스 추가 — `@ExtendWith(MockitoExtension.class)`

### 필수 테스트 케이스

| 테스트명 | 설명 |
|---------|------|
| `정상_수신대상전체_벌크읽음처리` | 소속 기사·고객 user_id 목록 stub → `notificationRepository.markAllAsReadByUserIds(recipientIds)` 호출 검증 |
| `수신대상없음_쿼리호출없이_정상종료` | 소속 기사/고객이 모두 없음 → `markAllAsReadByUserIds` 미호출(`verify(..., never())`), 예외 없이 정상 종료 |
| `대상이미모두읽음_재호출해도_정상처리` | `markAllAsReadByUserIds`가 0건 반환하도록 stub → 예외 없이 정상 종료 |
| `AGENCY아닌_role_예외발생` | role=CUSTOMER로 호출 → `IllegalAccessException`, repository 호출 없음(`verifyNoInteractions`) |

---

## 통합 테스트 명세 (H2 DB) — 반드시 수행

> `AgencyNotificationControllerIntegrationTest`에 `@Nested` 클래스 추가 — `@SpringBootTest` + `@AutoConfigureMockMvc` + H2 인메모리 DB

### 필수 테스트 케이스

| 테스트명 | 설명 |
|---------|------|
| `소속기사고객알림_전체읽음처리_204` | 소속 기사 알림 2건 + 소속 고객 알림 1건(모두 `is_read=false`) 저장 → PATCH 호출 시 204, 이후 DB에서 3건 모두 `is_read=true` 확인 |
| `읽음처리후_GET재호출시_unreadCount0` | `is_read=false` 알림 3건 저장 → `read-all` PATCH 호출 → 이어서 GET 호출 시 `stats.unreadCount = 0` 확인 |
| `타대행사알림_영향없음` | 다른 대행사 소속 기사에게 발송된 `is_read=false` 알림 저장 + 현재 대행사 알림도 저장 → PATCH 호출 후 타 대행사 알림은 `is_read=false`로 그대로 남아있는지 확인 |
| `대상없음_204` | 수신 대상 알림이 전혀 없는 대행사로 호출 → 204 (에러 없음) |
| `이미전체읽음상태_재호출_204` | 전부 `is_read=true`인 알림만 있는 상태에서 재호출 → 여전히 204(멱등) |
| `CUSTOMER권한_401` | CUSTOMER 토큰으로 호출 → 401 Unauthorized |

---

## 구현 시 유의사항

- `NotificationRepository`에 `@Modifying @Transactional @Query("UPDATE Notification n SET n.isRead = true WHERE n.user.id IN :userIds AND n.isRead = false")` 형태의 벌크 업데이트 메서드를 추가한다 (프로젝트 내 `AsAssignmentRepository.updateStatus()`와 동일한 패턴).
- `AgencyNotificationService.resolveRecipientIds()` — [mark-notification-read.md](./mark-notification-read.md) 구현 시 이미 추출된 private 메서드를 그대로 재사용한다 (신규 중복 로직 작성 금지).
