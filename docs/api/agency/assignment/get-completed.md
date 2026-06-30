# API 명세: 완료 배정 목록 조회

## 기본 정보

| 항목 | 내용 |
|------|------|
| Method | `GET` |
| URI | `/api/agency/as-assignments/completed` |
| 역할 | AGENCY |
| 설명 | 현재 로그인한 대행사 소속의 완료된 배정 목록을 페이징으로 조회한다. 작업 보고서(work_reports)와 고객 리뷰(reviews)를 함께 반환한다. |

## 인증 / 권한

- `@AuthenticationPrincipal CustomUserDetails`로 로그인 사용자 식별
- `userDetails.getRole() != AGENCY` → `IllegalAccessException` (401)
- `userDetails.getAgencyId()`로 소속 대행사 ID 추출

## 요청 파라미터

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---------|------|------|--------|------|
| `page` | int | N | 0 | 페이지 번호 (0-based) |
| `size` | int | N | 10 | 페이지 크기 |

## 응답 DTO: `AssignmentCompletedResponse`

```java
record AssignmentCompletedResponse(
    Long assignmentId,
    Long requestId,

    Long customerId,
    String customerName,
    String customerPhone,

    String productName,           // appliances.product_name
    String modelNo,               // appliances.model_no

    Long engineerId,
    String engineerName,
    String engineerPhone,
    Double engineerRating,        // engineer_profiles.avg_rating
    String engineerImg,           // engineer_profiles.profile_image_url

    // 작업 보고서 (work_reports)
    LocalDateTime submittedAt,
    Integer workDurationMin,
    String diagnosisResult,       // DiagnosisResult enum: NORMAL/REPAIRED/PART_REPLACED/UNREPAIRABLE
    Integer finalAmount,
    String memo,

    String visitDate,             // as_requests.scheduled_date
    String visitTime,             // as_requests.scheduled_time
    String visitAddress,          // as_requests.visit_address_detail

    // 고객 리뷰 (reviews, null 가능)
    Integer rating,
    String reviewContent,
    LocalDateTime reviewAt
)
```

## 비즈니스 로직

1. role == AGENCY 검증
2. agencyId로 `as_assignments WHERE status = 'COMPLETED'` 페이징 조회
   (JOIN FETCH: as_requests, appliances, customer, engineer, engineer_profiles)
3. 각 배정의 request_id로 work_reports 조회 (없으면 해당 항목 제외 또는 null 처리)
4. 각 request_id로 reviews LEFT JOIN (리뷰 없으면 rating/reviewContent/reviewAt = null)
5. 최신 submittedAt 순 정렬
6. 페이징 `Page<AssignmentCompletedResponse>` 반환

## 예외 처리

| 예외 | HTTP | 조건 |
|------|------|------|
| `IllegalAccessException` | 401 | role != AGENCY |
| 결과 없음 | 204 No Content | 완료 배정 없음 |

---

## 단위 테스트 명세 (JUnit 5)

> `AssignmentCompletedServiceTest` — `@ExtendWith(MockitoExtension.class)`

### 필수 테스트 케이스

| 테스트명 | 설명 |
|---------|------|
| `완료_배정목록_정상조회` | COMPLETED 배정 3건 mock → 응답 리스트 크기 3, work_reports 필드 정상 매핑 |
| `완료_배정없음_빈리스트반환` | 조회 결과 없음 → 빈 List 반환 |
| `AGENCY아닌_role_예외발생` | role=ENGINEER로 호출 → `IllegalAccessException` |
| `리뷰없는_배정_null필드_처리` | reviews 없는 배정 → rating/reviewContent/reviewAt null로 반환 |
| `리뷰있는_배정_정상매핑` | reviews 있는 배정 → rating, reviewContent, reviewAt 정확히 반환 |

---

## 통합 테스트 명세 (H2 DB)

> `AssignmentCompletedIntegrationTest` — `@SpringBootTest` + H2 인메모리 DB

### 필수 테스트 케이스

| 테스트명 | 설명 |
|---------|------|
| `완료목록_DB_페이징_조회` | Agency, Engineer, AsRequest, AsAssignment(COMPLETED), WorkReport 직접 저장 → GET 호출 → 200 OK, submittedAt/finalAmount 일치 |
| `ACCEPTED_배정은_포함되지않음` | ACCEPTED 배정 함께 저장 → 완료 목록에 포함 안 됨 |
| `리뷰_LEFT_JOIN_처리` | 리뷰 없는 완료 배정 → rating null, 리뷰 있는 배정 → rating 1-5 값 반환 |
| `페이지_크기_적용_확인` | 총 15건 중 size=5 요청 → 5건만 반환, totalElements=15 |
