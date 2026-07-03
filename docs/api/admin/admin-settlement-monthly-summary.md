# API: 대행사별 월별 정산 현황 조회

## 개요

관리자(ADMIN)가 특정 연/월 기준으로 **전체 대행사의 정산 현황**을 한눈에 조회한다.
위치: Admin 정산 관리 페이지 전체 (`AdminSettlementPage.jsx` — 대행사별 정산 현황 테이블 + 상단 KPI 카드).

기존 `GET /api/agency/settlements`는 로그인한 대행사 **본인** 정산만 조회하는 AGENCY 전용 API이므로, 전체 대행사를 조회하는 본 API와는 명확히 별개의 컨트롤러/서비스로 구현한다.

---

## 엔드포인트

```
GET /api/admin/settlements?year=2026&month=6
```

---

## 인증/인가

- **필수 인증**: JWT Bearer 토큰
- **허용 역할**: `ADMIN`
- `AdminUserController.checkAdminRole()`과 동일한 패턴으로 컨트롤러에서 `userDetails.getRole().equals("ADMIN")` 검증 → 아니면 `IllegalAccessException` (401)

---

## 요청

### 쿼리 파라미터

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `year` | int | O | 조회 연도 (예: 2026) |
| `month` | int | O | 조회 월 (1~12) |

### 요청 예시

```
GET /api/admin/settlements?year=2026&month=6
Authorization: Bearer {accessToken}
```

---

## 알려진 제약 (구현 전 반드시 인지할 것)

- **집계 대상 대행사 범위**: `agencies.approval_status = 'APPROVED'`인 대행사만 목록에 포함한다 (승인 대기/거절된 대행사는 정산 대상이 아님).
- **해당 월 정산 내역이 없는 대행사도 포함**: 프론트 mock(`mockAgencies` 중 "케어플러스" 2026년 6월 항목)처럼 `asCount=0`인 대행사도 `status: "NONE"`으로 목록에 포함되어야 한다 → `Agencies`를 기준으로 `Settlement`를 LEFT JOIN한다.
- **월 필터 기준 컬럼**: `settlements.created_at` 기준으로 월 범위를 필터링한다 (agency 도메인의 `findMonthlySummary` / `findAgencySettlements`와 동일 기준, `paid_at` 아님).
- **`agencyPay` 산출 공식**: `agencyPay = totalRevenue(grossAmount 합계) - careflowFee(platformFee 합계)`. 이는 `agencyFee + engineerNetAmount`와 동일한 값이며, "CareFlow가 대행사에 실제로 송금하는 금액"을 의미한다. 대행사 내부에서 기사에게 재정산하는 로직과는 무관하다.
- **`status` 값 체계**: 기존 `settlements.status`(PENDING/APPROVED/PAID/DISPUTED, 4종)와 달리 본 API의 대행사 단위 집계 `status`는 프론트 UI(`STATUS_META`)에 맞춰 **3종(`PENDING`/`PAID`/`NONE`)**으로 파생한다.
  - `asCount == 0` → `"NONE"` (해당 월 정산 내역 없음)
  - 해당 월 정산 중 `PAID`가 아닌 건이 1건이라도 있으면 → `"PENDING"`
  - 전부 `PAID`이면 → `"PAID"`
- **`summary.pendingCount`**: 정산 **건수**가 아니라 위 파생 `status`가 `"PENDING"`인 **대행사 수**이다 (팀 스펙 예시 `"pendingCount": 3`은 대행사 수 기준).

---

## 응답

### 200 OK

```json
{
  "summary": {
    "totalRevenue": 11130000,
    "totalCareflowFee": 1113000,
    "totalAgencyPay": 10017000,
    "pendingCount": 3
  },
  "agencies": [
    {
      "agencyId": 1,
      "agencyName": "한국서비스대행사",
      "asCount": 5,
      "totalRevenue": 5200000,
      "careflowFee": 520000,
      "agencyPay": 4680000,
      "status": "PENDING"
    },
    {
      "agencyId": 5,
      "agencyName": "케어플러스",
      "asCount": 0,
      "totalRevenue": 0,
      "careflowFee": 0,
      "agencyPay": 0,
      "status": "NONE"
    }
  ]
}
```

### 응답 필드 설명

| 필드 | 타입 | 설명 |
|---|---|---|
| `summary.totalRevenue` | long | 전체 대행사 `totalRevenue` 합계 |
| `summary.totalCareflowFee` | long | 전체 대행사 `careflowFee` 합계 |
| `summary.totalAgencyPay` | long | 전체 대행사 `agencyPay` 합계 |
| `summary.pendingCount` | long | `status == "PENDING"`인 대행사 수 |
| `agencies[].agencyId` | Long | 대행사 ID |
| `agencies[].agencyName` | String | 대행사 상호명 (`agencies.name`) |
| `agencies[].asCount` | long | 해당 월 정산 건수 |
| `agencies[].totalRevenue` | long | 해당 월 `gross_amount` 합계 |
| `agencies[].careflowFee` | long | 해당 월 `platform_fee` 합계 |
| `agencies[].agencyPay` | long | `totalRevenue - careflowFee` |
| `agencies[].status` | String | `PENDING` / `PAID` / `NONE` |

### 에러 응답

| HTTP 상태 | 조건 |
|---|---|
| 401 Unauthorized | JWT 토큰 없음/만료 (`SecurityConfig`의 `/api/admin/**` 인증 필터 단에서 차단) |
| 403 Forbidden | 인증은 되었으나 role != ADMIN (`SecurityConfig`의 `hasRole("ADMIN")` 필터에서 차단, 컨트롤러의 `checkAdminRole()`은 방어적 이중 체크) |
| 400 Bad Request | `month`이 1~12 범위를 벗어남, 또는 `year`/`month` 누락 |

---

## 처리 로직 (Pipeline)

1. **검증**: `userDetails.getRole() == "ADMIN"` 확인 → 아니면 `IllegalAccessException`
2. **month 검증**: 1~12 범위 확인 → 벗어나면 `IllegalArgumentException` ("월은 1~12 사이여야 합니다.")
3. **기간 계산**: `from = LocalDate.of(year, month, 1).atStartOfDay()`, `to = from.plusMonths(1)`
4. **집계 조회**: `SettlementRepository.findAllAgenciesMonthlySummary(from, to)` — `Agencies`(approval_status=APPROVED) 기준 `Settlement`를 LEFT JOIN ON(agency + createdAt 범위)하여 대행사 단위로 GROUP BY, DB 레벨에서 `COUNT`/`SUM`/미지급 건수(`unpaidCount`)까지 한 번에 집계
5. **DTO 매핑**: 각 로우에서 `status` 파생(`asCount`/`unpaidCount` 기준), `agencyPay = totalRevenue - careflowFee` 계산
6. **summary 집계**: 이미 조회된 대행사별 리스트를 스트림으로 합산(대행사 수가 많지 않으므로 애플리케이션 레벨 합산 허용), `pendingCount`는 `status == "PENDING"` 개수

---

## 개발 구성요소

| 계층 | 클래스 |
|---|---|
| Controller | `com.careflow.admin.controller.AdminSettlementController` |
| Service | `com.careflow.admin.service.AdminSettlementService` |
| Repository (수정) | `com.careflow.settlement.repository.SettlementRepository` (`findAllAgenciesMonthlySummary` 쿼리 + `AdminAgencySettlementProjection` 인터페이스 프로젝션 추가) |
| Response DTO | `com.careflow.admin.dto.response.AdminSettlementSummaryResponse` (내부 record `Summary`, `AgencySettlementItem` 포함) |

---

## 테스트 명세 (필수 — 아래 항목을 반드시 준수한다)

### JUnit 5 단위 테스트 (Service Layer, Mockito)

**파일**: `src/test/java/com/careflow/admin/service/AdminSettlementServiceTest.java`

- TC-1. 정상 조회 — 대행사 2곳, 각각 정산 존재 → `agencies` size 2, `summary` 정상 합산
- TC-2. role이 ADMIN이 아닌 경우 → `IllegalAccessException`
- TC-3. month가 0 또는 13 → `IllegalArgumentException`
- TC-4. 특정 대행사 `asCount = 0` → `status = "NONE"`, 금액 전부 0
- TC-5. 특정 대행사 정산 전부 PAID → `status = "PAID"`
- TC-6. 특정 대행사 정산 중 1건이라도 PAID가 아님 → `status = "PENDING"`
- TC-7. `pendingCount` — status가 PENDING인 대행사만 카운트 (NONE/PAID 제외) 검증
- TC-8. `agencyPay` 산출 — `totalRevenue - careflowFee`로 정확히 계산되는지 검증

### JUnit 5 통합 테스트 (H2 DB, `@SpringBootTest` + `@ActiveProfiles("local")`)

**파일**: `src/test/java/com/careflow/admin/service/AdminSettlementServiceIntegrationTest.java`

- `AgencySettlementServiceIntegrationTest` 픽스처 패턴 동일 적용 (`@Sql("/cleanup.sql")`, `createSettlement()` 헬퍼)
- TC-I-1. 승인된 대행사만 목록 포함 — `approval_status = PENDING/REJECTED`인 대행사는 응답에서 제외
- TC-I-2. 정산 없는 대행사도 목록에 포함 — `asCount=0`, `status="NONE"` 검증
- TC-I-3. 다수 대행사에 걸친 정산 데이터 — 대행사별로 정확히 분리 집계되는지 검증(타 대행사 값 혼입 없음)
- TC-I-4. 월 범위 필터 — 전월/익월 생성된 정산은 집계에서 제외
- TC-I-5. status 파생 — 특정 대행사에 PAID 1건 + PENDING 1건 → `status="PENDING"`, `asCount=2`
- TC-I-6. status 파생 — 특정 대행사 전부 PAID → `status="PAID"`
- TC-I-7. summary 총합 — 여러 대행사 값의 합이 `summary.totalRevenue`/`totalCareflowFee`/`totalAgencyPay`와 정확히 일치

### JUnit 5 컨트롤러 테스트 (`@WebMvcTest`)

**파일**: `src/test/java/com/careflow/admin/controller/AdminSettlementControllerTest.java`

- `AgencySettlementControllerTest` 패턴 동일 적용 (`StringRedisTemplate` `@MockitoBean` 포함)
- TC-C-1. 인증된 ADMIN — 200 OK, 응답 JSON 구조 검증 (`summary`/`agencies`)
- TC-C-2. 인증 없음 → 401
- TC-C-3. ADMIN이 아닌 role(AGENCY 등) → 403 (`SecurityConfig` 필터 레벨 차단)
- TC-C-4. `year`/`month` 파라미터 누락 → 400
