# API 명세: 배정 취소

## 기본 정보

| 항목 | 내용 |
|------|------|
| Method | `DELETE` |
| URI | `/api/agency/as-assignments/{assignmentId}` |
| 역할 | AGENCY |
| 설명 | 대행사 관리자가 배정을 취소한다. 배정 status를 REJECTED로 변경하고, 연결된 A/S 요청을 AGENCY_RECEIVED(대기) 상태로 되돌린다. 기사가 수락한(ACCEPTED) 경우 기사에게 취소 알림이 발송된다는 점을 비고로 명시한다. |

## 인증 / 권한

- `@AuthenticationPrincipal CustomUserDetails`로 로그인 사용자 식별
- `userDetails.getRole() != AGENCY` → `IllegalAccessException` (401)
- `userDetails.getAgencyId()`로 소속 대행사 ID 추출

## 경로 변수

| 변수 | 타입 | 설명 |
|------|------|------|
| `assignmentId` | Long | 취소할 배정 ID |

## 응답 DTO: `AssignmentCancelResponse`

```java
record AssignmentCancelResponse(
    Long assignmentId,
    Long requestId,
    String cancelledStatus,   // "REJECTED"
    String message            // "배정이 취소되었습니다. A/S 요청이 대기 상태로 변경되었습니다."
)
```

## 비즈니스 로직

1. role == AGENCY 검증
2. assignmentId로 `AsAssignment` 조회 (JOIN FETCH asRequest) → 없으면 `NoSuchElementException`
3. 해당 배정이 현재 대행사 소속인지 확인 → 아니면 `IllegalAccessException`
4. 배정 status가 이미 `REJECTED` 또는 `COMPLETED`이면 `IllegalStateException` ("이미 완료되거나 취소된 배정입니다.")
5. 배정 status → `REJECTED` (도메인 메서드 `cancel()` 호출)
6. 연결된 `AsRequest` status → `AGENCY_RECEIVED` (도메인 메서드 `revertToAgencyReceived()` 호출)
7. `AssignmentCancelResponse` 반환

## 예외 처리

| 예외 | HTTP | 조건 |
|------|------|------|
| `IllegalAccessException` | 401 | role != AGENCY 또는 다른 대행사 소속 배정 |
| `NoSuchElementException` | 404 | assignmentId 미존재 |
| `IllegalStateException` | 400 | 배정 status가 REJECTED 또는 COMPLETED |

---

## 단위 테스트 명세 (JUnit 5)

> `AssignmentCancelServiceTest` — `@ExtendWith(MockitoExtension.class)`

### 필수 테스트 케이스

| 테스트명 | 설명 |
|---------|------|
| `WAITING_배정_취소_성공` | WAITING 배정 → DELETE 호출 → status=REJECTED, asRequest.status=AGENCY_RECEIVED |
| `ACCEPTED_배정_취소_성공` | ACCEPTED 배정 → DELETE 호출 → 정상 취소 처리 |
| `AGENCY아닌_role_예외발생` | role=ENGINEER → `IllegalAccessException` |
| `다른대행사_배정_접근_예외` | 다른 agency 소속 배정 → `IllegalAccessException` |
| `배정_미존재_예외` | 없는 assignmentId → `NoSuchElementException` |
| `이미취소된_배정_예외` | status=REJECTED 배정 → `IllegalStateException` |
| `완료된_배정_취소불가` | status=COMPLETED 배정 → `IllegalStateException` |

---

## 통합 테스트 명세 (H2 DB)

> `AssignmentCancelIntegrationTest` — `@SpringBootTest` + H2 인메모리 DB

### 필수 테스트 케이스

| 테스트명 | 설명 |
|---------|------|
| `배정취소_DB_상태변경_확인` | WAITING 배정 저장 → DELETE 호출 → DB에서 assignment.status=REJECTED, asRequest.status=AGENCY_RECEIVED 확인 |
| `COMPLETED_배정_취소_400` | COMPLETED 배정 저장 → DELETE 호출 → 400 Bad Request |
| `다른대행사_배정_취소_401` | 다른 agency 소속 배정 → DELETE 호출 → 401 Unauthorized |
| `취소후_재취소_400` | 취소 후 동일 assignmentId로 재요청 → 400 Bad Request |
