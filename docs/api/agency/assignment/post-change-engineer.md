# API 명세: 배정 기사 변경

## 기본 정보

| 항목 | 내용 |
|------|------|
| Method | `POST` |
| URI | `/api/agency/as-assignments/change` |
| 역할 | AGENCY |
| 설명 | WAITING 또는 REJECTED 상태의 기존 배정에 대해, 새로운 기사로 신규 배정을 생성한다. 대행사 관리자가 수동으로 기사를 교체(WAITING)하거나, 거절된 배정에 수동으로 새 기사를 배정(REJECTED, 재배차 알림 화면)할 때 사용한다. |

## 인증 / 권한

- `@AuthenticationPrincipal CustomUserDetails`로 로그인 사용자 식별
- `userDetails.getRole() != AGENCY` → `IllegalAccessException` (401)
- `userDetails.getAgencyId()`로 소속 대행사 ID 추출

## 요청 DTO: `AssignmentChangeEngineerRequest`

```java
record AssignmentChangeEngineerRequest(
    @NotNull(message = "기존 배정 ID는 필수입니다.")
    Long assignmentId,

    @NotNull(message = "새 기사 ID는 필수입니다.")
    Long newEngineerId
)
```

## 응답 DTO: `AssignmentChangeEngineerResponse`

```java
record AssignmentChangeEngineerResponse(
    Long newAssignmentId,       // 새로 생성된 배정 ID
    Long requestId,             // 연결된 A/S 요청 ID
    Long newEngineerId,
    String newEngineerName,
    String assignmentStatus,    // "WAITING"
    LocalDateTime assignedAt
)
```

## 비즈니스 로직

1. role == AGENCY 검증
2. assignmentId로 기존 `AsAssignment` 조회 → 없으면 `NoSuchElementException`
3. 기존 배정이 해당 대행사 소속인지 확인 → 다른 대행사면 `IllegalAccessException`
4. 기존 배정 status가 `WAITING` 또는 `REJECTED`인지 확인 → 그 외(ACCEPTED/COMPLETED)면 `IllegalStateException` ("수락 대기 또는 거절 상태의 배정만 기사를 변경할 수 있습니다.")
5. newEngineerId로 `User` 조회 → 없으면 `NoSuchElementException`
6. 기존 배정이 `WAITING`이면 `cancel()` 호출해 `REJECTED`로 전환. **이미 `REJECTED`인 경우 `cancel()`을 다시 호출하지 않음** — `AsAssignment.cancel()`은 이미 REJECTED/COMPLETED 상태에서 재호출 시 `IllegalStateException`을 던지므로, 이미 종결 상태인 배정은 그대로 두고 새 배정만 생성한다.
7. 동일한 `AsRequest`에 새 `AsAssignment` 생성 (engineer=newEngineer, assignMethod=MANUAL, status=WAITING)
8. 새 배정 저장 후 `AssignmentChangeEngineerResponse` 반환

## 예외 처리

| 예외 | HTTP | 조건 |
|------|------|------|
| `IllegalAccessException` | 401 | role != AGENCY 또는 다른 대행사 소속 배정 |
| `NoSuchElementException` | 404 | assignmentId 또는 newEngineerId 미존재 |
| `IllegalStateException` | 403 (`GlobalExceptionHandler`가 `IllegalStateException`을 403으로 매핑, 이 문서의 과거 "400" 기재는 오기) | 기존 배정 status가 WAITING/REJECTED가 아님(ACCEPTED/COMPLETED) |

---

## 단위 테스트 명세 (JUnit 5)

> `AssignmentChangeEngineerServiceTest` — `@ExtendWith(MockitoExtension.class)`

### 필수 테스트 케이스

| 테스트명 | 설명 |
|---------|------|
| `기사변경_정상처리` | WAITING 배정 + 새 기사 ID → 기존 REJECTED, 새 WAITING 배정 생성, 응답 newAssignmentId 반환 |
| `AGENCY아닌_role_예외발생` | role=CUSTOMER → `IllegalAccessException` |
| `다른대행사_배정_접근_예외` | 다른 agencyId 소속 배정 → `IllegalAccessException` |
| `배정_미존재_예외` | 없는 assignmentId → `NoSuchElementException` |
| `새기사_미존재_예외` | 없는 newEngineerId → `NoSuchElementException` |
| `ACCEPTED_상태_변경불가` | status=ACCEPTED 배정 → `IllegalStateException` |
| `REJECTED_상태_정상처리` | status=REJECTED 배정 + 새 기사 ID → 정상 처리(200), **`cancel()`이 재호출되지 않는지** `verify(existing, never()).cancel()`로 검증, 새 WAITING 배정 생성 확인 |

---

## 통합 테스트 명세 (H2 DB)

> `AssignmentChangeEngineerIntegrationTest` — `@SpringBootTest` + H2 인메모리 DB

### 필수 테스트 케이스

| 테스트명 | 설명 |
|---------|------|
| `기사변경_DB_저장_확인` | WAITING 배정 저장 → POST 호출 → DB에서 기존 배정 REJECTED, 새 배정 WAITING 확인 |
| `새배정_request_id_동일_확인` | 변경 후 새 배정의 request_id가 기존 배정의 request_id와 동일한지 확인 |
| `REJECTED_배정_변경_성공` | REJECTED 배정 저장 → POST 호출 → 200 OK, 새 WAITING 배정 생성 확인 (재배차 알림 화면의 수동 배정 시나리오) |
| `ACCEPTED_배정_변경_실패` | ACCEPTED 배정 저장 → POST 호출 → 403 Forbidden |
| `다른대행사_배정_변경_실패` | 다른 agency 소속 배정 → POST 호출 → 401 Unauthorized |
