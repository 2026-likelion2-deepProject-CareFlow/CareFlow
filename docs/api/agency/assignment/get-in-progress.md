# API 명세: 진행 중 배정 목록 조회

## 기본 정보

| 항목 | 내용 |
|------|------|
| Method | `GET` |
| URI | `/api/agency/as-assignments/in-progress` |
| 역할 | AGENCY |
| 설명 | 현재 로그인한 대행사 소속의 진행 중(ACCEPTED) 배정 목록을 조회한다. as_status_logs를 함께 반환하여 프론트의 실시간 진행 현황 모니터링에 사용된다. |

## 인증 / 권한

- `@AuthenticationPrincipal CustomUserDetails`로 로그인 사용자 식별
- `userDetails.getRole() != AGENCY` → `IllegalAccessException` (401)
- `userDetails.getAgencyId()`로 소속 대행사 ID 추출

## 요청 파라미터

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---------|------|------|--------|------|
| `page` | int | N | 0 | 페이지 번호 (0-based) |
| `size` | int | N | 10 | 페이지 크기 |

## 응답 DTO: `AssignmentInProgressResponse`

```java
record AssignmentInProgressResponse(
    Long assignmentId,
    Long requestId,
    String assignMethod,           // AUTO / MANUAL

    Long customerId,
    String customerName,
    String customerPhone,

    String productName,            // appliances.product_name
    String modelNo,                // appliances.model_no

    Long engineerId,
    String engineerName,
    String engineerPhone,
    Double engineerRating,         // engineer_profiles.avg_rating
    String engineerImg,            // engineer_profiles.profile_image_url

    String assignmentStatus,       // as_assignments.status (ACCEPTED)
    String latestLogStatus,        // as_status_logs 최신 to_status (null 가능)
    LocalDateTime updatedAt,       // as_requests.updated_at

    String visitDate,              // as_requests.scheduled_date
    String visitTime,              // as_requests.scheduled_time
    String visitAddress,           // as_requests.visit_address_detail

    List<StatusLogEntry> logs,     // as_status_logs (최신순)
    Map<String, String> stepTimes  // 단계별 도달 시각 (HH:mm)
)

record StatusLogEntry(
    String toStatus,
    String memo,
    LocalDateTime createdAt
)
```

## 비즈니스 로직

1. role == AGENCY 검증
2. agencyId로 `as_assignments WHERE status = 'ACCEPTED'` 목록 조회 (JOIN FETCH: as_requests, appliances, customer, engineer, engineer_profiles)
3. 각 배정에 대해 `as_status_logs`를 request_id 기준 조회 (최신순)
4. `latestLogStatus` = 로그 중 가장 최근 `to_status` (없으면 null)
5. `stepTimes` = 각 status 키에 대해 최초 도달 시각(HH:mm) 매핑. 도달하지 않은 단계는 null
6. 페이징 적용 후 반환

## 예외 처리

| 예외 | HTTP | 조건 |
|------|------|------|
| `IllegalAccessException` | 401 | role != AGENCY |
| 결과 없음 | 204 No Content | 진행 중 배정 없음 |

---

## 단위 테스트 명세 (JUnit 5)

> `AssignmentInProgressServiceTest` — `@ExtendWith(MockitoExtension.class)`

### 필수 테스트 케이스

| 테스트명 | 설명 |
|---------|------|
| `진행중_배정목록_정상조회` | agencyId로 ACCEPTED 배정 2건 조회 → 응답 리스트 크기 2, 필드 매핑 정확 |
| `진행중_배정없음_빈리스트반환` | 조회 결과 없음 → 빈 List 반환 |
| `AGENCY아닌_role_예외발생` | role=CUSTOMER로 호출 → `IllegalAccessException` |
| `statusLog_최신순_정렬확인` | 로그 3건 삽입 → 응답 logs 필드가 최신순인지 확인 |
| `stepTimes_단계별_시각_매핑` | WAITING, ENGINEER_DEPARTED 로그 존재 → stepTimes에 해당 키만 값 존재, 나머지 null |

---

## 통합 테스트 명세 (H2 DB)

> `AssignmentInProgressIntegrationTest` — `@SpringBootTest` + H2 인메모리 DB

### 필수 테스트 케이스

| 테스트명 | 설명 |
|---------|------|
| `진행중_목록_DB에서_페이징_조회` | Agency, Engineer, AsRequest, AsAssignment(ACCEPTED) 직접 저장 → GET 호출 → 200 OK, 응답 데이터 DB 값과 일치 |
| `REJECTED_배정은_포함되지않음` | REJECTED 배정 함께 저장 → 응답에 포함되지 않음 확인 |
| `statusLog_포함여부_확인` | AsStatusLog 2건 저장 → 응답 logs 필드에 포함 |
| `engineer_프로필_null_처리` | engineer_profiles 없는 기사 → engineerRating null, engineerImg null 반환 |
