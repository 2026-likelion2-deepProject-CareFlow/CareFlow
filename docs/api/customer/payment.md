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
