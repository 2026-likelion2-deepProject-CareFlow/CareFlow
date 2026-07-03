# API: 대행사 리뷰 목록 조회

## 개요

대행사 관리자가 본인 대행사 소속 수리기사에게 달린 **리뷰 목록**을 조회한다.  
통계 요약(stats)과 페이지네이션된 리뷰 목록(content)을 함께 반환한다.

---

## 엔드포인트

```
GET /api/agency/reviews?rating=&engineerId=&isVisible=&dateFrom=&dateTo=&keyword=&page=0&size=10
```

> ⚠️ **변경 이력**: 최초 구현 시 필터 조건을 `@RequestBody`로 수신했으나(프론트 요구사항), GET 요청에 바디를 싣는 방식은 표준이 아니라 클라이언트/프록시 환경에 따라 안정적으로 전달되지 않는다는 프론트 피드백에 따라 **쿼리 파라미터 방식으로 변경**했다.

---

## 인증/인가

- **필수 인증**: JWT Bearer 토큰
- **허용 역할**: `AGENCY`
- `@AuthenticationPrincipal CustomUserDetails`에서 `agencyId` 추출 (`getAgencyId()`)
- 서비스 레이어에서 `userDetails.getRole().equals("AGENCY")` 검증 → 아니면 `IllegalAccessException`(401)

---

## 요청

### 쿼리 파라미터

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---|---|---|---|---|
| `page` | int | N | 0 | 페이지 번호 (0-base) |
| `size` | int | N | 10 | 페이지 크기 |
| `rating` | Integer | N | - | 평점 필터 (1~5), 생략 시 전체 |
| `engineerId` | Long | N | - | 기사 필터, 생략 시 전체 |
| `isVisible` | Boolean | N | - | 노출 상태 필터, 생략 시 전체 |
| `dateFrom` | String | N | - | 작성일 검색 시작 (`yyyy-MM-dd`), `created_at >= dateFrom 00:00:00` |
| `dateTo` | String | N | - | 작성일 검색 종료 (`yyyy-MM-dd`), `created_at < dateTo+1일 00:00:00` (해당일 포함) |
| `keyword` | String | N | - | 고객명 / 기사명 / 주문번호(requestId) 부분 일치 검색 |

필터 쿼리 파라미터 생략 시 전체 조회.

### 요청 예시

```
GET /api/agency/reviews?rating=5&isVisible=true&dateFrom=2024-01-01&dateTo=2024-12-31&keyword=김민수&page=0&size=10
Authorization: Bearer {access_token}
```

---

## 응답

### 200 OK

```json
{
  "stats": {
    "avgRating": 4.8,
    "totalCount": 2148,
    "fiveStarRate": 78.6,
    "newThisMonth": 256,
    "prevMonthAvgRatingDiff": 0.2,
    "prevMonthTotalDiff": 156,
    "prevMonthNewDiff": 32
  },
  "content": [
    {
      "reviewId": 1,
      "requestId": 1,
      "customerName": "김민수",
      "engineerId": 123,
      "engineerName": "김현수",
      "agencyName": "퀵케어 서비스",
      "productName": "삼성",
      "modelNo": "AF17B7538WZ",
      "visitDate": "2024-06-18",
      "visitTime": "13:00",
      "rating": 5,
      "content": "에어컨 냉방이 안 돼서 정말 당황했는데...",
      "isVisible": true,
      "createdAt": "2024-06-18T15:30:00"
    }
  ],
  "totalElements": 2148,
  "totalPages": 27,
  "currentPage": 0,
  "size": 10
}
```

### 응답 필드 설명

| 필드 | 타입 | 설명 |
|---|---|---|
| `stats.avgRating` | Double | 본 대행사 전체 리뷰 평균 평점 (필터 무관, 항상 전체 모수 기준) |
| `stats.totalCount` | long | 본 대행사 전체 리뷰 수 (필터 무관) |
| `stats.fiveStarRate` | Double | 5점 리뷰 비율 (%) |
| `stats.newThisMonth` | long | 이번 달 신규 리뷰 수 |
| `stats.prevMonthAvgRatingDiff` | Double | 이번 달 평균 평점 - 전월 평균 평점 (전월 리뷰 0건이면 0.0) |
| `stats.prevMonthTotalDiff` | long | 이번 달 신규 리뷰 수 - 전월 신규 리뷰 수 |
| `stats.prevMonthNewDiff` | long | `prevMonthTotalDiff`와 동일값 (프론트 스펙 필드 유지용) |
| `content[].reviewId` | Long | 리뷰 ID |
| `content[].requestId` | Long | A/S 요청 ID |
| `content[].customerName` | String | 고객명 (`users.name`) |
| `content[].engineerId` | Long | 기사 user_id |
| `content[].engineerName` | String | 기사명 (`users.name`) |
| `content[].agencyName` | String | 대행사명 (`agencies.name`) |
| `content[].productName` | String | 가전 브랜드명 (`appliances.brand`) |
| `content[].modelNo` | String | 가전 모델명 (`appliances.model_name`) |
| `content[].visitDate` | String | 방문 예약 날짜 (`as_requests.scheduled_date`, `yyyy-MM-dd`) |
| `content[].visitTime` | String | 방문 예약 시간 (`as_requests.scheduled_time`, `HH:MM`) |
| `content[].rating` | int | 평점 (1~5) |
| `content[].content` | String | 리뷰 내용 (null 가능) |
| `content[].isVisible` | boolean | 노출 여부 |
| `content[].createdAt` | LocalDateTime | 리뷰 작성 일시 |
| `totalElements` | long | 검색 필터 적용 후 전체 건수 |
| `totalPages` | int | 전체 페이지 수 |
| `currentPage` | int | 현재 페이지 번호 |
| `size` | int | 페이지 크기 |

### 에러 응답

| HTTP 상태 | 조건 |
|---|---|
| 401 Unauthorized | JWT 토큰 없음/만료, 또는 role != AGENCY (`IllegalAccessException`) |
| 400 Bad Request | dateFrom/dateTo 잘못된 날짜 형식 (`IllegalArgumentException`) |

---

## 처리 로직 (Pipeline)

1. **검증 단계**
   - 서비스 레이어에서 `userDetails.getRole().equals("AGENCY")` 확인 → 아니면 `IllegalAccessException`
   - `userDetails.getAgencyId()`로 대행사 ID 추출

2. **stats 계산 (검색 필터 무관, 항상 전체 모수)**
   - 이번 달 범위: `[이번달 1일 00:00, 다음달 1일 00:00)`
   - 전월 범위: `[전월 1일 00:00, 이번달 1일 00:00)`
   - `ReviewRepository`에 JPQL로 agencyId 기준 집계 쿼리 추가:
     - 전체 avgRating, totalCount, fiveStarCount
     - newThisMonth (이번달 created_at 범위)
     - prevMonthNewCount (전월 created_at 범위)
     - prevMonthAvgRating (전월 범위 평균)

3. **content 조회 (검색 필터 적용)**
   - `ReviewRepository.findAgencyReviews(agencyId, filter, pageable)` JPQL로
     reviews JOIN as_requests(agency_id), appliances, customer(users), engineer(users) 한 번에 조회
   - keyword: `customer.name LIKE %keyword%` OR `engineer.name LIKE %keyword%`
     OR `CAST(review.asRequest.id AS string) LIKE %keyword%`
   - dateFrom/dateTo: `review.createdAt` 범위 조건
   - 정렬: `createdAt DESC`

4. **응답 조립**
   - `AgencyReviewListResponse.of(stats, page)` 로 변환 후 200 OK 반환

---

## 개발 구성요소

| 계층 | 클래스 |
|---|---|
| Controller | `com.careflow.agency.controller.AgencyReviewController` |
| Service | `com.careflow.agency.service.AgencyReviewService` |
| Repository (수정) | `com.careflow.review.repository.ReviewRepository` (메서드 추가) |
| Request DTO | `com.careflow.agency.dto.request.AgencyReviewSearchRequest` |
| Response DTO | `com.careflow.agency.dto.response.AgencyReviewListResponse` (Stats, ReviewSummary 내부 record 포함) |

---

## 테스트 명세 (필수 — 아래 항목을 반드시 준수한다)

### JUnit 5 단위 테스트 (Service Layer, Mockito)

**파일**: `src/test/java/com/careflow/agency/service/AgencyReviewServiceTest.java`

- TC-1. 정상 조회 — 리뷰 2건 존재, 필터 없음 → content size 2, stats 정상 매핑 검증
- TC-2. role이 AGENCY가 아닌 경우 → `IllegalAccessException`
- TC-3. rating 필터 전달 시 Repository에 올바른 파라미터 전달 검증 (Mockito verify)
- TC-4. engineerId 필터 전달 시 Repository에 올바른 파라미터 전달 검증
- TC-5. dateFrom/dateTo 정상 날짜 문자열 → LocalDateTime 범위로 변환되어 Repository 호출
- TC-6. dateFrom 잘못된 형식("2024-13-99") → `IllegalArgumentException`
- TC-7. 리뷰 0건 → stats avgRating 0.0, totalCount 0, fiveStarRate 0.0, content 빈 리스트
- TC-8. fiveStarRate 계산 — totalCount=10, fiveStarCount=7 → 70.0 검증

### JUnit 5 통합 테스트 (H2 DB, `@SpringBootTest` + `@ActiveProfiles("local")`)

**파일**: `src/test/java/com/careflow/agency/service/AgencyReviewServiceIntegrationTest.java`

- 기존 `AgencyCustomerServiceIntegrationTest` 패턴 따름: `@Sql(scripts = "/cleanup.sql", executionPhase = BEFORE_TEST_METHOD)`로 매 테스트 전 초기화, `@BeforeEach`에서 대행사/유저/가전/A/S요청/리뷰 INSERT
- TC-I-1. 본 대행사 기사의 리뷰만 조회되는지 검증 (타 대행사 리뷰 제외)
- TC-I-2. rating 필터 — rating=5인 리뷰만 content에 포함되는지 검증
- TC-I-3. isVisible 필터 — isVisible=false인 리뷰 제외 검증
- TC-I-4. keyword 검색 — 고객명으로 부분 일치 시 정상 매칭
- TC-I-5. dateFrom/dateTo 범위 — 범위 내 리뷰만 포함, 경계값(해당 날짜 포함) 검증
- TC-I-6. 페이징 — 11건 INSERT 후 size=10으로 조회 시 1페이지 10건, totalPages=2 검증
- TC-I-7. stats는 필터 무관 — rating=5 필터 적용 시 content는 필터링되지만 totalCount는 전체 기준 유지
- TC-I-8. fiveStarRate — 5점 리뷰 실제 비율이 응답에 정확히 반영되는지 검증

### JUnit 5 컨트롤러 테스트 (`@WebMvcTest`)

**파일**: `src/test/java/com/careflow/agency/controller/AgencyReviewControllerTest.java`

- 기존 `AgencyCustomerControllerTest` 패턴(`@Import({SecurityConfig.class, PasswordEncoderConfig.class})`, `@MockitoBean` JwtProvider 등) 그대로 따른다
- TC-C-1. 인증된 AGENCY 역할 — 200 OK + 응답 JSON 구조(stats/content/totalElements 등) 검증
- TC-C-2. 인증 없음(anonymous) — 401
- TC-C-3. page/size 기본값 검증 (미전달 시 page=0, size=10으로 Service 호출)
- TC-C-4. 필터 쿼리 파라미터 없이 호출 시에도 정상 동작 (빈 필터로 처리)
- TC-C-5. rating/engineerId/isVisible/dateFrom/dateTo/keyword 쿼리 파라미터가 Service 필터로 정확히 전달되는지 검증
