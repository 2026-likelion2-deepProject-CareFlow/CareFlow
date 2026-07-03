# 고객 결제 API 설계

## 개요

결제가 완료된 A/S 요청(`status = COMPLETED`)에 대해 고객이 결제를 처리하는 API.
결제 금액은 클라이언트가 전달하지 않고, **서버가 `work_reports.final_amount`에서 확정**한다.
결제 성공 시 `as_requests.status → PAID`로 전환된다. (Phase 1: PG 호출 없이 즉시 SUCCESS 처리 — MOCK)

명세: **C-21, C-22**

---

## API — 고객 A/S 결제

### 기본 정보

| 항목 | 내용 |
|---|---|
| Method | `POST` |
| Path | `/api/customer/as-requests/{requestId}/payment` |
| 인증 | Bearer JWT (CUSTOMER 권한) |
| 책임 도메인 | `payment` |

### Request

- Body 없음 (결제 금액은 서버가 `work_reports.final_amount`에서 읽음)

### 처리 흐름

1. JWT에서 `customerId` 추출
2. `requestId`로 `as_requests` 단건 조회 — 없으면 404
3. `asRequest.customer_id == customerId` 검증 — 불일치 시 401
4. `as_requests.status == COMPLETED` 확인 — 아니면 403
5. `work_reports`에서 `request_id`로 보고서 조회 — 없으면 404 (C-21: 보고서 없으면 결제 불가)
6. `work_reports.customer_approved == true` 확인 — false면 403 (C-22: 고객 승인 필수)
7. `payments`에 중복 결제 여부 확인 — 이미 결제됐으면 403
8. `Payment` 생성 (status=READY), `payment.markSuccess()` — MOCK PG 즉시 SUCCESS
9. `as_requests.status → PAID`
10. 201 + `PaymentResponse` 반환

### 응답 예시 (201 Created)

```json
{
  "paymentId": 3,
  "requestId": 12,
  "amount": 85000,
  "status": "SUCCESS",
  "pgProvider": "MOCK",
  "paidAt": "2026-06-29T14:22:05"
}
```

### 오류

| 상황 | HTTP | 예외 |
|---|---|---|
| 존재하지 않는 requestId | 404 | `NoSuchElementException` |
| 본인 요청이 아님 | 401 | `IllegalAccessException` |
| status != COMPLETED | 403 | `IllegalStateException` |
| 작업 완료 보고서 없음 | 404 | `NoSuchElementException` |
| 고객 승인 미완료 (`customer_approved=false`) | 403 | `IllegalStateException` |
| 이미 결제 완료 | 403 | `IllegalStateException` |

---

## DB 설계 참조

```sql
-- payments (핵심 컬럼만)
payment_id   BIGINT PK
request_id   BIGINT FK → as_requests.request_id  UNIQUE
customer_id  BIGINT FK → users.user_id
amount       INT NOT NULL
status       ENUM('READY','SUCCESS','FAIL') DEFAULT 'READY'
pg_provider  ENUM('MOCK', ...) DEFAULT 'MOCK'
paid_at      DATETIME NULL
created_at   DATETIME DEFAULT CURRENT_TIMESTAMP

-- work_reports (결제 연동 관련 컬럼)
final_amount      INT NOT NULL       -- 결제 확정 금액 출처
customer_approved TINYINT(1) DEFAULT 0
approved_at       DATETIME NULL
```

---

## 구현 위치

```
payment/
├── controller/PaymentController.java
├── service/PaymentService.java
├── entity/Payment.java
├── repository/PaymentRepository.java
└── dto/
    └── PaymentResponse.java

report/
└── repository/WorkReportRepository.java   (findByAsRequest_Id 메서드 사용)
```

---

## 테스트 전략

### 단위 테스트 (`PaymentServiceTest`)

- `@ExtendWith(MockitoExtension.class)`
- 검증 항목:
  - 정상 결제 흐름 (READY → SUCCESS, status → PAID)
  - COMPLETED가 아닌 상태에서 결제 시도 → 403
  - 보고서 없음 → 404
  - `customer_approved=false` → 403
  - 중복 결제 시도 → 403
  - 타인 요청 결제 시도 → 401

### 통합 테스트

- `@SpringBootTest` + H2 인메모리
- 정상 흐름: 상태를 COMPLETED까지 올린 뒤 보고서 생성 → `customer_approved=true` 설정 → POST 결제 → status=PAID, payment row 확인
- 결제 후 재결제 시도 → 403 확인

---

## API — 고객 결제 대시보드 (요약/추이/내역)

프론트 결제 대시보드 화면에서 사용하는 조회 전용 API 3종. 모두 로그인한 본인(`customerId`)의 데이터만 반환하며, 클라이언트가 별도로 넘기는 `customerId` 파라미터는 없음(JWT에서 추출).

| 항목 | 내용 |
|---|---|
| 인증 | Bearer JWT (`Authorization: Bearer {accessToken}`), 미인증 시 401 |
| 책임 도메인 | `payment` |
| 컨트롤러 | `payment/controller/CustomerPaymentSummaryController.java` |

### 1. `GET /api/customer/payments/summary` — 결제 요약 KPI

Request: 없음 (쿼리/바디 없음)

응답 예시 (200 OK)
```json
{
  "totalAmount": 150000,
  "thisMonthAmount": 50000,
  "unpaidCount": 2,
  "partsAmount": 60000,
  "otherAmount": 90000
}
```

| 필드 | 설명 |
|---|---|
| `totalAmount` | `payments.status='SUCCESS'` 전체 합계(원) |
| `thisMonthAmount` | 위 중 이번 달(`paid_at` 기준) 합계(원) |
| `unpaidCount` | `as_requests.status='COMPLETED'`(결제 대기) 건수 |
| `partsAmount` | SUCCESS 결제 건 기준 부품비 합계 |
| `otherAmount` | `totalAmount - partsAmount` (출장비+수리비 등 나머지) — 비용 3분할(출장비/부품비/수리비)은 DB 스키마상 불가하여 부품비 vs 기타 2분할로 확정 |

### 2. `GET /api/customer/payments/monthly` — 월별 결제액 추이

Request: 없음

응답 예시 (200 OK) — 이번 달 포함 최근 6개월 고정, 데이터 없는 달은 `amount: 0`으로 채워서 오래된 달 → 최신 달 순으로 반환 (배열 길이 항상 6)
```json
[
  { "yearMonth": "2026-02", "amount": 0 },
  { "yearMonth": "2026-03", "amount": 30000 },
  { "yearMonth": "2026-04", "amount": 0 },
  { "yearMonth": "2026-05", "amount": 45000 },
  { "yearMonth": "2026-06", "amount": 0 },
  { "yearMonth": "2026-07", "amount": 50000 }
]
```

### 3. `GET /api/customer/payments` — 결제 내역 전체 목록

Request: 없음 (상태 필터 없음 — READY/FAILED/CANCELLED/REFUNDED/SUCCESS 전체를 최신순으로 반환)

응답 예시 (200 OK)
```json
[
  {
    "paymentId": 3,
    "requestId": 30,
    "amount": 50000,
    "status": "SUCCESS",
    "pgProvider": "MOCK",
    "paidAt": "2026-07-01T10:00:00"
  },
  {
    "paymentId": 2,
    "requestId": 20,
    "amount": 30000,
    "status": "FAILED",
    "pgProvider": "MOCK",
    "paidAt": null
  }
]
```
- `status`가 `SUCCESS`가 아니면 `paidAt`은 `null`로 내려감
- 결제 내역이 없으면 `[]` (빈 배열, 200 OK — 204 아님)

### 공통 오류

| 상황 | HTTP |
|---|---|
| Authorization 헤더 없음/만료된 토큰 | 401 |

### 구현 위치

```
payment/
├── controller/CustomerPaymentSummaryController.java
├── dto/CustomerPaymentSummaryResponse.java
├── dto/CustomerMonthlyPaymentResponse.java
└── dto/PaymentResponse.java (내역 목록에서 재사용)
```

### 테스트

- `CustomerPaymentSummaryControllerTest` (`@WebMvcTest`) — 3개 엔드포인트 × (성공/빈값·401) 케이스, 총 8개 전부 통과 확인
