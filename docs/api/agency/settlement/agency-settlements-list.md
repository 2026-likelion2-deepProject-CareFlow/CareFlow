# API: 대행사 정산 목록 조회

## 개요

대행사 관리자가 본인 대행사 소속 기사들의 **정산 내역 목록**을 조회한다.  
통계 요약(stats)과 페이지네이션된 정산 목록(content)을 함께 반환한다.

---

## 엔드포인트

```
GET /api/agency/settlements?status=&keyword=&dateFrom=&dateTo=&page=0&size=10
```

> ⚠️ **변경 이력**: 최초 구현 시 필터 조건(status/keyword/dateFrom/dateTo)을 `@RequestBody`로 수신하도록 설계했으나(`GET /api/agency/reviews`와 동일 패턴), GET 요청에 바디를 싣는 방식은 표준이 아니라 클라이언트/프록시 환경에 따라 안정적으로 전달되지 않는다는 프론트 피드백에 따라 **쿼리 파라미터 방식으로 변경**했다.

---

## 인증/인가

- **필수 인증**: JWT Bearer 토큰
- **허용 역할**: `AGENCY`
- 서비스 레이어에서 `userDetails.getRole().equals("AGENCY")` 검증 → 아니면 `IllegalAccessException`(401)
- `userDetails.getAgencyId()`로 대행사 ID 추출

---

## 요청

### 쿼리 파라미터

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---|---|---|---|---|
| `page` | int | N | 0 | 페이지 번호 (0-base) |
| `size` | int | N | 10 | 페이지 크기 |
| `status` | String | N | 전체 | `PENDING` / `PAID` / `DISPUTED`, 생략 시 전체 |
| `keyword` | String | N | - | 기사명 부분 일치 검색 (숫자만 입력 시 정산 ID 정확 일치로 자동 분기 — 서비스 레이어 처리) |
| `dateFrom` | String | N | - | 정산 생성일 검색 시작 (`yyyy-MM-dd`) |
| `dateTo` | String | N | - | 정산 생성일 검색 종료 (`yyyy-MM-dd`), 해당일 포함 |

필터 파라미터 생략 시 전체 조회.

### 요청 예시

```
GET /api/agency/settlements?status=PAID&keyword=김현수&dateFrom=2024-06-01&dateTo=2024-06-30&page=0&size=10
Authorization: Bearer {access_token}
```

---

## 알려진 제약 (구현 전 반드시 인지할 것)

- **`status` 값 불일치**: 프론트 mock은 `SUCCESS/READY/FAILED/CANCELLED`를 사용하지만 DB `settlements.status`는 [DDL v11] `PENDING/PAID/DISPUTED`(APPROVED 제거)이다. 백엔드 API는 DB 값 기준으로 요청/응답하며, 프론트가 매핑 테이블을 별도로 유지한다.
- **`period` 파생 필드**: `settlements` 테이블에 기간 컬럼이 없다. `periodStart` = `created_at` 월의 1일, `periodEnd` = `created_at` 월의 마지막 날로 파생한다. 추후 DB 마이그레이션 시 수정 가능.
- **`completedCount`**: 1건의 settlement = 1건의 A/S 작업이므로 항상 1로 반환한다.
- **`type`**: 현재 `settlements` 테이블은 기사(ENGINEER) 단위 정산만 지원하므로 항상 `"ENGINEER"`로 반환한다. AGENCY 타입 정산은 미구현이다.
- **`payMethod` / `bankAccount`**: `bank_accounts`(기사 계좌) 테이블에서 조회해 매핑한다. 기사가 계좌를 등록하지 않았으면 `null`.
- **`engineerPayoutId` / `engineerPayoutStatus` / `engineerPayoutPaidAt`** ([engineer_payouts 신규]): 이 정산 건이 집계된 대행사→기사 월별 지급 배치 정보. `status`(CareFlow→대행사)와는 독립적인 자금 흐름이므로 혼동하지 말 것 — 자세한 배경은 [agency-engineer-payouts-list.md](./agency-engineer-payouts-list.md) 참고.
- **`stats` 집계 기준** ([변경] 프론트 피드백 반영): `stats`는 **현재 조회 필터(status/dateFrom/dateTo/keyword)로 조회된 결과 전체** 기준으로 집계한다(= `content` 조회와 동일한 WHERE 조건). 필터를 아예 지정하지 않으면 전체 기간 기준으로 집계된다. `paidAmount`/`pendingAmount`/`disputedAmount`도 이 필터링된 범위 내 해당 상태 건의 `gross_amount` 합계이다.
  - 필드명(`thisMonthGrossAmount`/`thisMonthCount`)은 프론트 하위 호환을 위해 그대로 유지하지만, 필터가 걸린 경우 실제로는 "이번 달"이 아니라 "필터링된 기간"의 값을 의미한다.
- **`prevMonthCountDiff`/`prevMonthGrossDiff`(전월 대비)**: 필터가 하나라도 걸려 있으면 의미가 없어지므로 **필터가 전혀 없을 때만** "이번 달 vs 전월"을 별도로 집계해서 계산한다. 필터가 하나라도 걸려 있으면 `null`로 반환한다.

---

## 응답

### 200 OK

```json
{
  "stats": {
    "thisMonthGrossAmount": 125680000,
    "paidAmount": 98420000,
    "pendingAmount": 22380000,
    "disputedAmount": 4880000,
    "thisMonthCount": 1248,
    "prevMonthCountDiff": 105,
    "prevMonthGrossDiff": 13680000
  },
  "content": [
    {
      "settlementId": 1,
      "type": "ENGINEER",
      "engineerId": 123,
      "engineerName": "김현수",
      "engineerPhone": "010-1234-5678",
      "agencyName": "퀵케어 서비스",
      "periodStart": "2024-06-01",
      "periodEnd": "2024-06-30",
      "completedCount": 1,
      "grossAmount": 88000,
      "platformFeeRate": 10.00,
      "platformFee": 8800,
      "agencyFeeRate": 10.00,
      "agencyFee": 8800,
      "engineerNetAmount": 70400,
      "status": "PAID",
      "settledAt": "2024-06-18T15:30:00",
      "payMethod": null,
      "bankAccount": null,
      "engineerPayoutId": 42,
      "engineerPayoutStatus": "PENDING",
      "engineerPayoutPaidAt": null
    }
  ],
  "totalElements": 1248,
  "totalPages": 125,
  "currentPage": 0,
  "size": 10
}
```

### 응답 필드 설명

| 필드 | 타입 | 설명 |
|---|---|---|
| `stats.thisMonthGrossAmount` | long | 현재 필터로 조회된 결과 전체의 정산 총액 합계 (필터 없으면 전체 기간, status 무관) |
| `stats.paidAmount` | long | 현재 필터 범위 내 PAID 상태 gross_amount 합계 |
| `stats.pendingAmount` | long | 현재 필터 범위 내 PENDING 상태 gross_amount 합계 |
| `stats.disputedAmount` | long | 현재 필터 범위 내 DISPUTED 상태 gross_amount 합계 |
| `stats.thisMonthCount` | long | 현재 필터로 조회된 결과 전체 건수 |
| `stats.prevMonthCountDiff` | Long (nullable) | 이번 달 건수 - 전월 건수. **필터가 하나라도 걸려 있으면 `null`** |
| `stats.prevMonthGrossDiff` | Long (nullable) | 이번 달 총액 - 전월 총액. **필터가 하나라도 걸려 있으면 `null`** |
| `content[].settlementId` | Long | 정산 ID |
| `content[].type` | String | 항상 `"ENGINEER"` (현재 DB 미지원으로 AGENCY 타입 없음) |
| `content[].engineerId` | Long | 기사 user_id |
| `content[].engineerName` | String | 기사명 (`users.name`) |
| `content[].engineerPhone` | String | 기사 연락처 (`users.phone`) |
| `content[].agencyName` | String | 대행사명 (`agencies.agency_name`) |
| `content[].periodStart` | String | 기준 기간 시작 (`created_at` 월의 1일, `yyyy-MM-dd`) |
| `content[].periodEnd` | String | 기준 기간 종료 (`created_at` 월의 마지막 날, `yyyy-MM-dd`) |
| `content[].completedCount` | int | 항상 1 (settlement 1건 = A/S 1건) |
| `content[].grossAmount` | int | 작업 총 금액 (원) |
| `content[].platformFeeRate` | BigDecimal | CareFlow 수수료율 스냅샷 (%) |
| `content[].platformFee` | int | CareFlow 수수료 (원) |
| `content[].agencyFeeRate` | BigDecimal | 대행사 수수료율 스냅샷 (%) |
| `content[].agencyFee` | int | 대행사 수수료 (원) |
| `content[].engineerNetAmount` | int | 기사 실수령액 (원) |
| `content[].status` | String | `PENDING` / `PAID` / `DISPUTED` — **[E 수정] CareFlow→대행사** 지급 상태. 대행사가 직접 `PAID`로 바꿀 수 없음(ADMIN 배치 승인 전용) |
| `content[].settledAt` | LocalDateTime | CareFlow→대행사 지급 완료 일시 (`settlements.paid_at`, null 가능) |
| `content[].payMethod` | String | 지급 방식 (`bank_accounts.pay_method` 한글 레이블, 기사 계좌 미등록 시 null) |
| `content[].bankAccount` | String | 지급 계좌 ("은행명 계좌번호" 포맷, 기사 계좌 미등록 시 null) |
| `content[].engineerPayoutId` | Long (nullable) | **[engineer_payouts 신규]** 이 건이 집계된 대행사→기사 지급 배치 ID. 월 배치가 아직 안 돌았거나 다른 사유로 미할당이면 null |
| `content[].engineerPayoutStatus` | String (nullable) | **대행사→기사** 지급 상태(`PENDING`/`PAID`/`DISPUTED`) — `status`(CareFlow→대행사)와 완전히 별개. `PATCH /api/agency/engineer-payouts/{engineerPayoutId}/pay`로 대행사가 직접 PAID 전이 |
| `content[].engineerPayoutPaidAt` | LocalDateTime (nullable) | 대행사→기사 지급 완료 일시 |
| `totalElements` | long | 검색 필터 적용 후 전체 건수 |
| `totalPages` | int | 전체 페이지 수 |
| `currentPage` | int | 현재 페이지 번호 |
| `size` | int | 페이지 크기 |

### 에러 응답

| HTTP 상태 | 조건 |
|---|---|
| 401 Unauthorized | JWT 토큰 없음/만료, 또는 role != AGENCY |
| 400 Bad Request | dateFrom/dateTo 잘못된 날짜 형식 |

---

## 처리 로직 (Pipeline)

1. **검증**: `userDetails.getRole() == "AGENCY"` 확인 → 아니면 `IllegalAccessException`
2. **날짜 파싱** (fail-fast): dateFrom/dateTo → LocalDateTime, 오류 시 `IllegalArgumentException`
3. **keyword 분기**: 순수 숫자면 `settlementId` 정확 일치, 아니면 `nameKeyword` LIKE 검색
4. **stats 집계** (현재 필터 결과 전체 기준):
   - `SettlementRepository.findAgencySettlementStatsByFilter(...)` — `content` 조회와 동일한 WHERE 조건 사용
   - 필터가 하나도 없을 때만 `findAgencySettlementStatsByPeriod(...)`를 이번 달/전월 각각 추가 호출해 `prevMonthCountDiff`/`prevMonthGrossDiff` 계산, 필터가 있으면 두 필드 모두 `null`
5. **content 조회**: `JOIN FETCH s.engineer, s.agency`로 N+1 방지, 필터 조건 적용 후 `createdAt DESC` 정렬
6. **DTO 매핑**: `createdAt`에서 periodStart/periodEnd 파생, payMethod/bankAccount는 null 반환

---

## 개발 구성요소

| 계층 | 클래스 |
|---|---|
| Controller | `com.careflow.agency.controller.AgencySettlementController` |
| Service | `com.careflow.agency.service.AgencySettlementService` |
| Repository (수정) | `com.careflow.settlement.repository.SettlementRepository` (쿼리 추가) |
| Request DTO | `com.careflow.agency.dto.request.AgencySettlementSearchRequest` |
| Response DTO | `com.careflow.agency.dto.response.AgencySettlementListResponse` (Stats, SettlementSummary 내부 record 포함) |

---

## 테스트 명세 (필수 — 아래 항목을 반드시 준수한다)

### JUnit 5 단위 테스트 (Service Layer, Mockito)

**파일**: `src/test/java/com/careflow/agency/service/AgencySettlementServiceTest.java`

- TC-1. 정상 조회 — 정산 2건, 필터 없음 → content size 2, stats 정상 매핑
- TC-2. role이 AGENCY가 아닌 경우 → `IllegalAccessException`
- TC-3. status 필터 전달 시 Repository에 status 파라미터 전달 검증 (Mockito verify)
- TC-4. keyword 필터 전달 시 Repository에 keyword 파라미터 전달 검증
- TC-5. dateFrom/dateTo 정상 날짜 → LocalDateTime 범위로 변환되어 Repository 호출
- TC-6. dateFrom 잘못된 형식 → `IllegalArgumentException`
- TC-7. 정산 0건 → stats 전부 0, content 빈 리스트
- TC-8. periodStart/periodEnd 파생 — createdAt=2024-06-15 → periodStart="2024-06-01", periodEnd="2024-06-30"

### JUnit 5 통합 테스트 (H2 DB, `@SpringBootTest` + `@ActiveProfiles("local")`)

**파일**: `src/test/java/com/careflow/agency/service/AgencySettlementServiceIntegrationTest.java`

- `AgencyReviewServiceIntegrationTest` 패턴 동일 적용 (`@Sql("/cleanup.sql")`, `@BeforeEach` 픽스처 구성)
- TC-I-1. 본 대행사 정산만 조회 — 타 대행사 정산 제외 검증
- TC-I-2. status 필터 — PAID 상태만 content에 포함, stats도 동일 필터(PAID) 기준으로 집계되고 전월 대비 diff는 null
- TC-I-3. keyword 필터 — 기사명 부분 일치 시 정상 매칭
- TC-I-4. dateFrom/dateTo 범위 — 범위 내 정산만 포함, 경계값(해당일) 포함 검증, stats도 해당 범위로 집계되고 전월 대비 diff는 null
- TC-I-5. 페이징 — 11건 INSERT 후 size=10 조회 시 1페이지 10건, totalPages=2
- TC-I-6. stats 필터 없음 — 이번 달/전월 정산 모두 전체 기간으로 합산, 전월 대비 diff는 별도 계산되어 채워짐
- TC-I-7. periodStart/periodEnd — H2 실제 INSERT 후 createdAt 월의 1일/말일로 정확히 파생
- TC-I-8. pendingAmount — PENDING 합산이 stats에 정확히 반영 ([DDL v11] APPROVED 제거)

### JUnit 5 컨트롤러 테스트 (`@WebMvcTest`)

**파일**: `src/test/java/com/careflow/agency/controller/AgencySettlementControllerTest.java`

- `AgencyReviewControllerTest` 패턴 동일 적용 (`StringRedisTemplate` @MockitoBean 포함)
- TC-C-1. 인증된 AGENCY — 200 OK, 응답 JSON 구조 검증 (stats/content/totalElements)
- TC-C-2. 인증 없음 — 401
- TC-C-3. page/size 기본값 검증 (미전달 시 page=0, size=10)
- TC-C-4. 필터 쿼리 파라미터 없이 호출해도 정상 동작
- TC-C-5. status/keyword/dateFrom/dateTo 쿼리 파라미터가 Service 필터로 정확히 전달되는지 검증
