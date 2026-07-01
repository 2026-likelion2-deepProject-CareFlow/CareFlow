# API 명세: 대행사 정산 합산 내역 (월별)

## 기본 정보

| 항목 | 내용 |
|---|---|
| HTTP Method | GET |
| URL | `/api/agency/settlements/summary` |
| 인증 | 필수 (JWT) |
| 허용 역할 | `AGENCY` |
| 도메인 패키지 | `com.careflow.settlement` |

---

## 요청

### Query Parameters

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `year` | int | O | 조회 연도 (예: 2026) |
| `month` | int | O | 조회 월 (1~12) |

### 요청 예시

```
GET /api/agency/settlements/summary?year=2026&month=6
Authorization: Bearer {accessToken}
```

---

## 응답

### 성공 (200 OK)

```json
{
  "year": 2026,
  "month": 6,
  "totalCount": 20,
  "totalGrossAmount": 4000000,
  "paidAmount": 3200000,
  "pendingAmount": 500000,
  "disputedAmount": 300000,
  "totalPlatformFee": 400000,
  "totalAgencyFee": 360000,
  "totalEngineerPayout": 3240000
}
```

### 응답 필드 설명

| 필드 | 타입 | 설명 |
|---|---|---|
| `year` | int | 조회 연도 |
| `month` | int | 조회 월 |
| `totalCount` | long | 해당 월 정산 건수 (모든 status 포함) |
| `totalGrossAmount` | long | 총 매출 합계 (모든 status의 gross_amount 합산, 원) |
| `paidAmount` | long | 지급 완료 금액 (status = PAID인 gross_amount 합산, 원) |
| `pendingAmount` | long | 지급 대기 금액 (status IN (PENDING, APPROVED) gross_amount 합산, 원) |
| `disputedAmount` | long | 보류 금액 (status = DISPUTED인 gross_amount 합산, 원) |
| `totalPlatformFee` | long | CareFlow 수수료 합계 (platform_fee 합산, 원) |
| `totalAgencyFee` | long | 대행사 수수료 합계 (agency_fee 합산, 원) |
| `totalEngineerPayout` | long | 기사 지급액 합계 (engineer_net_amount 합산, 원) |

> `paidAmount + pendingAmount + disputedAmount = totalGrossAmount` 가 항상 성립한다.  
> 데이터가 없는 월은 모든 금액 필드가 0, `totalCount`가 0인 응답을 반환한다 (404 아님).

### 실패 응답

| 상황 | HTTP 상태 | 메시지 예시 |
|---|---|---|
| JWT 없거나 만료 | 401 | - |
| AGENCY 역할 아님 | 403 | - |
| year/month 누락 또는 범위 초과 | 400 | `"월은 1~12 사이여야 합니다."` |

---

## 비즈니스 로직

1. JWT에서 `agency_id`를 추출한다 (`CustomUserDetails` → `users.agency_id`).
2. `settlements` 테이블에서 `agency_id` + `created_at`이 요청 연월 범위인 레코드를 필터링한다 (**전체 status 포함**).
3. 해당 레코드 전체에 대해 아래 항목을 집계한다:
   - `COUNT(*)` → `totalCount`
   - `SUM(gross_amount)` → `totalGrossAmount` (전체)
   - `SUM(CASE WHEN status = 'PAID' THEN gross_amount ELSE 0 END)` → `paidAmount`
   - `SUM(CASE WHEN status IN ('PENDING','APPROVED') THEN gross_amount ELSE 0 END)` → `pendingAmount`
   - `SUM(CASE WHEN status = 'DISPUTED' THEN gross_amount ELSE 0 END)` → `disputedAmount`
   - `SUM(platform_fee)` → `totalPlatformFee`
   - `SUM(agency_fee)` → `totalAgencyFee`
   - `SUM(engineer_net_amount)` → `totalEngineerPayout`
4. 집계 결과가 없으면(0건) 모든 금액을 0으로 채운 응답을 반환한다.

> **날짜 기준**: `created_at` 기준으로 월 범위를 필터링한다. PENDING/APPROVED/DISPUTED 상태의 정산은 `paid_at` 이 없으므로 `paid_at` 기준 사용 불가.

---

## 참조 테이블

```
settlements
  - settlement_id, payment_id, request_id
  - engineer_id (FK → users)
  - agency_id   (FK → agencies)
  - gross_amount      : 작업 총 금액
  - platform_fee      : CareFlow 수수료
  - fee_rate          : CareFlow 수수료율 스냅샷
  - agency_fee        : 대행사 수수료
  - agency_fee_rate   : 대행사 수수료율 스냅샷
  - engineer_net_amount : 기사 실수령액
  - status ENUM('PENDING','APPROVED','PAID','DISPUTED')
  - paid_at
  - created_at        ← 월 범위 필터링 기준 컬럼

payments
  - payment_id, request_id, customer_id
  - amount     : 결제 금액 (참고용 — 집계는 settlements.gross_amount 기준)
  - status, paid_at
```

---

## 구현 파일 목록

| 파일 | 경로 |
|---|---|
| Controller | `settlement/controller/SettlementController.java` |
| Service | `settlement/service/SettlementService.java` |
| Repository | `settlement/repository/SettlementRepository.java` |
| 응답 DTO | `settlement/dto/MonthlySummaryResponse.java` |
| Entity | `settlement/entity/Settlements.java` |

> `SettlementController`, `SettlementService`, `SettlementRepository`는 `engineer-performance` API와 동일 클래스를 공유한다.

---

## 테스트 요구사항

### 1. 단위 테스트 (JUnit 5 + Mockito)

**테스트 클래스**: `src/test/java/com/careflow/settlement/service/SettlementServiceTest.java`

| 케이스 | 설명 |
|---|---|
| 정상 집계 | `paidAmount`, `pendingAmount`, `disputedAmount` 분리 집계값 검증 |
| 빈 결과 | 모든 금액 필드(상태별 포함)가 0인지 검증 |
| 단일 건(PAID) | `paidAmount = totalGrossAmount`, `pendingAmount/disputedAmount = 0` 검증 |
| 유효하지 않은 month | month=0 시 `IllegalArgumentException` 발생 검증 |

### 2. 통합 테스트 (H2 인메모리 DB)

**테스트 클래스**: `src/test/java/com/careflow/settlement/service/SettlementServiceIntegrationTest.java`

| 케이스 | 설명 |
|---|---|
| PAID 정산 2건 합산 | `paidAmount = totalGrossAmount`, `pendingAmount/disputedAmount = 0` 검증 |
| 데이터 없는 월 | 모든 금액 0 반환 (상태별 필드 포함) |
| 타 월 정산 제외 | 7월 정산이 6월 조회에 포함되지 않음 |

### 3. 컨트롤러 슬라이스 테스트 (`@WebMvcTest`)

**테스트 클래스**: `src/test/java/com/careflow/settlement/controller/SettlementControllerTest.java`

| 케이스 | HTTP 상태 | 검증 내용 |
|---|---|---|
| 정상 요청 (AGENCY JWT) | 200 | 상태별 금액 분리 필드 포함 전체 응답 JSON 검증 |
| 데이터 없는 월 | 200 | 모든 금액 0 (상태별 포함) |
| JWT 없음 | 401 | 인증 실패 |
| month=13 요청 | 400 | 유효성 검사 실패 |

---

## 구현 시 주의사항

- 집계는 `settlements.created_at` 기준으로 월 필터링할 것 (`paid_at` 기준 아님).
- `payments.amount`는 참고용으로만 존재하며, 집계는 반드시 `settlements.gross_amount`를 기준으로 한다 (스냅샷 값이 결제 금액과 다를 수 있음).
- **`status = 'PAID'` 고정 필터를 사용하지 말 것** — 모든 status를 포함하되 CASE WHEN 으로 상태별 합산을 분리한다.
- 집계 쿼리는 JPQL `@Query`로 작성하며 DB 레벨에서 한 번에 합산한다 (애플리케이션 레벨 루프 집계 금지).
- 엔티티에 Setter를 추가하지 말고, 집계 결과는 인터페이스 프로젝션으로 직접 매핑한다.
- 한글 주석으로 비즈니스 로직의 의도와 주의사항을 코드에 남긴다.
