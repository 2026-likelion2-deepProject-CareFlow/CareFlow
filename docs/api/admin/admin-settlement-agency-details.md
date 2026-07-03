# API: 특정 대행사 건별 정산 내역 조회

## 개요

관리자(ADMIN)가 [대행사별 월별 정산 현황 조회](admin-settlement-monthly-summary.md) 화면에서 특정 대행사 행의 "건별 내역"을 펼쳤을 때, 해당 대행사·해당 월의 **건별 정산 내역**을 조회한다.
위치: Admin 정산 관리 페이지 — `AdminSettlementPage.jsx`의 인라인 펼침 테이블(`r.rows`).

---

## 엔드포인트

```
GET /api/admin/settlements/{agencyId}/details?year=2026&month=6
```

---

## 인증/인가

- **필수 인증**: JWT Bearer 토큰
- **허용 역할**: `ADMIN`
- 컨트롤러에서 `checkAdminRole()` 검증 → 아니면 `IllegalAccessException` (401)

---

## 요청

### 경로 변수

| 변수 | 타입 | 설명 |
|---|---|---|
| `agencyId` | Long | 대행사 ID |

### 쿼리 파라미터

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `year` | int | O | 조회 연도 |
| `month` | int | O | 조회 월 (1~12) |

### 요청 예시

```
GET /api/admin/settlements/1/details?year=2026&month=6
Authorization: Bearer {accessToken}
```

---

## 알려진 제약 (구현 전 반드시 인지할 것)

- **`settlementId` 포맷**: 프론트 mock(`"SET-001"`)과 팀 스펙 예시(`"SET-001"`)에 맞춰 `"SET-" + 정산 PK를 3자리 zero-padding`한 문자열로 변환한다 (예: `settlement_id=1` → `"SET-001"`, `settlement_id=1234` → `"SET-1234"`, 3자리를 초과하면 자릿수 그대로 늘어난다). 실제 PK는 Long이며 이 포맷은 표시용이다.
- **`completedAt` 기준 컬럼**: `settlements` 테이블에 별도 "완료일" 컬럼이 없으므로 `settlements.created_at`의 날짜부(`yyyy-MM-dd`)를 사용한다 (agency 도메인의 `periodStart`/`periodEnd` 파생과 동일한 기준 컬럼).
- **`applianceName`**: `settlements → as_request → appliance → category → name` 경로로 조회한다 (`Appliance`에는 `brand`/`modelName`만 있고 "냉장고" 같은 표시명은 `ApplianceCategory.name`에 있음).
- **`customerName`**: `settlements → as_request → customer(User) → name` 경로로 조회한다.
- **정렬 순서**: `created_at ASC` (오래된 건부터, 프론트 mock 데이터 순서와 동일).
- **대행사 존재 검증**: `agencyId`가 존재하지 않으면 `NoSuchElementException` (404).
- **정산 내역이 0건**인 경우 빈 배열 `[]`을 반환한다 (404 아님).

---

## 응답

### 200 OK

```json
[
  {
    "settlementId": "SET-001",
    "completedAt": "2026-06-05",
    "applianceName": "냉장고",
    "customerName": "김철수",
    "totalAmount": 95000,
    "careflowFee": 9500,
    "agencyPay": 85500
  }
]
```

### 응답 필드 설명

| 필드 | 타입 | 설명 |
|---|---|---|
| `settlementId` | String | `"SET-" + PK 3자리 zero-padding` |
| `completedAt` | String | `settlements.created_at`의 날짜부 (`yyyy-MM-dd`) |
| `applianceName` | String | 가전 카테고리명 (`appliance_categories.name`) |
| `customerName` | String | 고객명 (`users.name`) |
| `totalAmount` | long | 작업 총 금액 (`gross_amount`) |
| `careflowFee` | long | CareFlow 수수료 (`platform_fee`) |
| `agencyPay` | long | `totalAmount - careflowFee` (대행사 지급액) |

### 에러 응답

| HTTP 상태 | 조건 |
|---|---|
| 401 Unauthorized | JWT 토큰 없음/만료 |
| 403 Forbidden | 인증은 되었으나 role != ADMIN |
| 404 Not Found | 존재하지 않는 `agencyId` |
| 400 Bad Request | `month`이 1~12 범위를 벗어남 |

---

## 처리 로직 (Pipeline)

1. **검증**: ADMIN role 확인 → 아니면 `IllegalAccessException`
2. **대행사 존재 검증**: `AgenciesRepository.findById(agencyId)` → 없으면 `NoSuchElementException`
3. **month 검증**: 1~12 범위 확인 → `IllegalArgumentException`
4. **기간 계산**: `from = LocalDate.of(year, month, 1).atStartOfDay()`, `to = from.plusMonths(1)`
5. **조회**: `SettlementRepository.findAgencySettlementDetails(agencyId, from, to)` — `JOIN FETCH asRequest → appliance → category`, `JOIN FETCH asRequest → customer`로 N+1 방지, `createdAt ASC` 정렬
6. **DTO 매핑**: `settlementId` 포맷 변환, `completedAt` 날짜부 추출, `agencyPay` 계산

---

## 개발 구성요소

| 계층 | 클래스 |
|---|---|
| Controller | `com.careflow.admin.controller.AdminSettlementController` |
| Service | `com.careflow.admin.service.AdminSettlementService` |
| Repository (수정) | `com.careflow.settlement.repository.SettlementRepository` (`findAgencySettlementDetails` 쿼리 추가) |
| Response DTO | `com.careflow.admin.dto.response.AdminSettlementDetailResponse` |

---

## 테스트 명세 (필수 — 아래 항목을 반드시 준수한다)

### JUnit 5 단위 테스트 (Service Layer, Mockito)

**파일**: `src/test/java/com/careflow/admin/service/AdminSettlementServiceTest.java` (⑦ 서비스 테스트와 같은 클래스 내 별도 `@Nested` 그룹으로 작성)

- TC-1. 정상 조회 — 정산 3건 → 리스트 size 3, 필드 매핑 정상
- TC-2. role이 ADMIN이 아닌 경우 → `IllegalAccessException`
- TC-3. 존재하지 않는 agencyId → `NoSuchElementException`
- TC-4. month가 0 또는 13 → `IllegalArgumentException`
- TC-5. 정산 0건 → 빈 리스트 반환 (예외 아님)
- TC-6. `settlementId` 포맷 — PK=1 → `"SET-001"`, PK=1234 → `"SET-1234"` 검증
- TC-7. `agencyPay` 산출 — `totalAmount - careflowFee`로 정확히 계산되는지 검증

### JUnit 5 통합 테스트 (H2 DB, `@SpringBootTest` + `@ActiveProfiles("local")`)

**파일**: `src/test/java/com/careflow/admin/service/AdminSettlementServiceIntegrationTest.java` (⑦ 통합 테스트와 같은 클래스 내 별도 `@Nested` 그룹으로 작성)

- TC-I-1. 타 대행사 정산 제외 — 조회 대상 대행사의 정산만 포함
- TC-I-2. 월 범위 필터 — 전월/익월 생성된 정산은 제외
- TC-I-3. `applianceName`/`customerName` — H2 실제 INSERT 후 카테고리명/고객명 정상 매핑
- TC-I-4. 정렬 순서 — `created_at ASC`로 반환되는지 검증
- TC-I-5. 정산 0건인 대행사·월 조합 — 빈 배열 반환

### JUnit 5 컨트롤러 테스트 (`@WebMvcTest`)

**파일**: `src/test/java/com/careflow/admin/controller/AdminSettlementControllerTest.java` (⑦ 컨트롤러 테스트와 같은 클래스 내 별도 메서드로 작성)

- TC-C-1. 인증된 ADMIN — 200 OK, 응답 JSON 배열 구조 검증
- TC-C-2. 인증 없음 → 401
- TC-C-3. 존재하지 않는 agencyId → 404
