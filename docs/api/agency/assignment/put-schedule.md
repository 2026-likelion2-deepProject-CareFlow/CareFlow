# API 명세: 배정 일정 변경

## 기본 정보

| 항목 | 내용 |
|------|------|
| Method | `PUT` |
| URI | `/api/agency/as-assignments/{assignmentId}/schedule` |
| 역할 | AGENCY |
| 설명 | 배정된 A/S 방문 일정(날짜, 시간)을 변경한다. as_requests의 scheduled_date와 scheduled_time을 업데이트한다. |

## 인증 / 권한

- `@AuthenticationPrincipal CustomUserDetails`로 로그인 사용자 식별
- `userDetails.getRole() != AGENCY` → `IllegalAccessException` (401)
- `userDetails.getAgencyId()`로 소속 대행사 ID 추출

## 경로 변수

| 변수 | 타입 | 설명 |
|------|------|------|
| `assignmentId` | Long | 일정을 변경할 배정 ID |

## 요청 DTO: `AssignmentScheduleRequest`

```java
record AssignmentScheduleRequest(
    @NotNull(message = "방문 날짜는 필수입니다.")
    LocalDate scheduledDate,

    @NotBlank(message = "방문 시간은 필수입니다.")
    @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "시간 형식은 HH:mm이어야 합니다.")
    String scheduledTime
)
```

## 응답 DTO: `AssignmentScheduleResponse`

```java
record AssignmentScheduleResponse(
    Long assignmentId,
    Long requestId,
    LocalDate scheduledDate,
    String scheduledTime
)
```

## 비즈니스 로직

1. role == AGENCY 검증
2. assignmentId로 `AsAssignment` 조회 → 없으면 `NoSuchElementException`
3. 해당 배정이 현재 대행사 소속인지 확인 → 아니면 `IllegalAccessException`
4. 배정 status가 `WAITING` 또는 `ACCEPTED`인지 확인 → 그 외(`REJECTED`, `COMPLETED`)면 `IllegalStateException` ("완료되거나 취소된 배정의 일정은 변경할 수 없습니다.")
5. 배정에 연결된 `AsRequest`의 일정 업데이트 (도메인 메서드 `updateSchedule(date, time)` 호출)
6. `AssignmentScheduleResponse` 반환

## 예외 처리

| 예외 | HTTP | 조건 |
|------|------|------|
| `IllegalAccessException` | 401 | role != AGENCY 또는 다른 대행사 소속 배정 |
| `NoSuchElementException` | 404 | assignmentId 미존재 |
| `IllegalStateException` | 400 | 배정 status가 REJECTED 또는 COMPLETED |

---

## 단위 테스트 명세 (JUnit 5)

> `AssignmentScheduleServiceTest` — `@ExtendWith(MockitoExtension.class)`

### 필수 테스트 케이스

| 테스트명 | 설명 |
|---------|------|
| `WAITING_배정_일정변경_성공` | WAITING 배정 → PUT 호출 → scheduledDate/Time 변경, 응답 정확 |
| `ACCEPTED_배정_일정변경_성공` | ACCEPTED 배정 → PUT 호출 → 정상 처리 |
| `AGENCY아닌_role_예외발생` | role=CUSTOMER → `IllegalAccessException` |
| `다른대행사_배정_접근_예외` | 다른 agency 소속 배정 → `IllegalAccessException` |
| `배정_미존재_예외` | 없는 assignmentId → `NoSuchElementException` |
| `COMPLETED_배정_일정변경_불가` | status=COMPLETED → `IllegalStateException` |
| `REJECTED_배정_일정변경_불가` | status=REJECTED → `IllegalStateException` |

---

## 통합 테스트 명세 (H2 DB)

> `AssignmentScheduleIntegrationTest` — `@SpringBootTest` + H2 인메모리 DB

### 필수 테스트 케이스

| 테스트명 | 설명 |
|---------|------|
| `일정변경_DB_반영_확인` | AsRequest(scheduledDate=오늘) 저장 → PUT 호출(새 날짜) → DB에서 scheduledDate 변경 확인 |
| `COMPLETED_배정_일정변경_400` | COMPLETED 배정 저장 → PUT 호출 → 400 Bad Request |
| `잘못된_시간형식_400` | scheduledTime="25:00" → 400 Bad Request (Bean Validation) |
| `다른대행사_배정_401` | 다른 agency 소속 배정 → PUT 호출 → 401 Unauthorized |
