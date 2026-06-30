# API: 대행사 소속 고객의 결제 내역 조회

## 개요

대행사 관리자가 [`GET /api/agency/customers`](./agency-customer-list.md) 목록에서 선택한 특정 고객(`userId`)이 **본인 대행사로 접수한 A/S 건**에 대해 결제한 내역을 조회한다.
고객 관리 페이지의 고객 상세 화면(결제 내역 탭)에서 사용된다. [`agency-customer-appliances.md`](./agency-customer-appliances.md), [`agency-customer-as-requests.md`](./agency-customer-as-requests.md)와 동일한 인가 구조를 따른다.

---

## 엔드포인트

```
GET /api/agency/customers/{userId}/payments
```

---

## 인증/인가

- **필수 인증**: JWT Bearer 토큰
- **허용 역할**: `AGENCY` (대행사 관리자 로그인 상태에서만 호출 가능)
- `SecurityConfig`에 본 경로 전용 매칭이 없으므로(`anyRequest().authenticated()`만 적용) 서비스 레이어에서 `userDetails.getRole() == "AGENCY"`를 명시적으로 검증한다. 아니면 `IllegalAccessException`(401).
- **고객 소속 검증**: `{userId}`가 현재 로그인한 대행사 소속 기사에게 COMPLETED 서비스를 1회 이상 받은 고객이어야 한다 — [`agency-customer-list.md`](./agency-customer-list.md) 등과 동일하게 `AsAssignmentRepository.findDistinctCompletedCustomerIdsByAgencyId(agencyId)` 결과에 `userId` 포함 여부로 판단. 미포함 시 `IllegalAccessException`(401). 본 검증은 `AgencyCustomerService.verifyAgencyAccessToCustomer(...)`(기존 공통 메서드)를 그대로 재사용한다.

---

## 요청

### 경로 변수

| 변수 | 타입 | 설명 |
|---|---|---|
| `userId` | Long | 조회 대상 고객의 user_id |

### 요청 예시

```
GET /api/agency/customers/1/payments
Authorization: Bearer {access_token}
```

---

## 응답

### 200 OK

```json
[
  {
    "paymentId": 1,
    "requestId": 1,
    "applianceBrand": "삼성",
    "applianceModelName": "바람의나라 AF17",
    "amount": 85000,
    "pgProvider": "KAKAO",
    "status": "SUCCESS",
    "paidAt": "2024-06-20T15:30:00",
    "createdAt": "2024-06-18T10:00:00"
  }
]
```

결제 내역이 없으면 빈 배열 `[]` 반환(204 아님 — [`agency-customer-appliances.md`](./agency-customer-appliances.md), [`agency-customer-as-requests.md`](./agency-customer-as-requests.md)와 동일하게 고객 상세 화면 탭 특성상 빈 배열도 정상 응답으로 처리).

### 응답 필드 설명

| 필드 | 타입 | 설명 |
|---|---|---|
| `paymentId` | Long | 결제 ID |
| `requestId` | Long | 결제 대상 A/S 요청 ID (`payments.request_id`) |
| `applianceBrand` | String | 해당 A/S 요청 대상 가전 브랜드 (`as_requests.appliance.brand`) |
| `applianceModelName` | String | 해당 A/S 요청 대상 가전 모델명 |
| `amount` | Integer | 결제 금액(원) |
| `pgProvider` | String | PG사 (`MOCK`/`KAKAO`/`TOSS`/`NAVER`) |
| `status` | String | 결제 상태 (`READY`/`SUCCESS`/`FAILED`/`CANCELLED`/`REFUNDED`) |
| `paidAt` | LocalDateTime | 결제 완료 일시 (READY/FAILED 등 아직 결제되지 않은 건은 null 가능) |
| `createdAt` | LocalDateTime | 결제 row 생성 일시 |

> 본 API는 [`agency-customer-as-requests.md`](./agency-customer-as-requests.md)와 마찬가지로 **상태 필터를 적용하지 않는다** — 결제 내역 탭은 READY/FAILED/CANCELLED/REFUNDED를 포함한 전체 결제 이력을 보여주는 것이 목적이다.

### 에러 응답

| HTTP 상태 | 조건 |
|---|---|
| 401 Unauthorized | JWT 토큰 없음/만료(Security 필터 단계), role != AGENCY, 또는 `userId`가 본인 대행사 소속 기사에게 COMPLETED 서비스를 받은 고객이 아닌 경우(서비스 레이어 검증, `IllegalAccessException`) |
| 404 Not Found | `userId`에 해당하는 유저 자체가 존재하지 않는 경우 (`NoSuchElementException`) |

---

## 처리 로직 (Pipeline)

1. **검증 단계**
   - `AgencyCustomerService.verifyAgencyAccessToCustomer(userDetails, userId)` 재사용 — role = AGENCY 확인, 유저 존재 확인(404), 본인 대행사 COMPLETED 서비스 이력 확인(401)

2. **데이터 처리 단계**
   - `PaymentRepository`에서 `customer.id = userId AND asRequest.agency.id = agencyId` 조건으로 `asRequest`·`asRequest.appliance`를 `JOIN FETCH`하여 단일 쿼리 조회(N+1 방지), 최신 결제 row 생성순(`created_at DESC`) 정렬
   - **`asRequest.agency.id = agencyId` 조건을 명시적으로 포함**하는 이유: [`agency-customer-as-requests.md`](./agency-customer-as-requests.md)와 동일 — 고객이 과거 타 대행사에 접수한 A/S 건의 결제 내역까지 노출되면 안 되므로(데이터 격리)

3. **응답 단계**
   - `List<AgencyCustomerPaymentResponse>`로 매핑 후 200 OK 반환 (빈 리스트도 200)

---

## 예외 처리 및 제약 조건

- 표준 예외 4종 중 `NoSuchElementException`(404), `IllegalAccessException`(401) 사용
- `@Transactional(readOnly = true)` 적용

---

## 개발 및 출력 요구사항

| 계층 | 클래스 |
|---|---|
| Controller (수정) | `com.careflow.agency.controller.AgencyCustomerController` — 메서드 추가 |
| Service (수정) | `com.careflow.agency.service.AgencyCustomerService` — 메서드 추가, `verifyAgencyAccessToCustomer` 재사용 |
| Repository (수정) | `com.careflow.payment.repository.PaymentRepository` — `customer.id` + `asRequest.agency.id` 조건의 `JOIN FETCH` 조회 메서드 추가 |
| Response DTO | `com.careflow.agency.dto.response.AgencyCustomerPaymentResponse` |

---

## 테스트 명세 (필수 — 아래 항목을 반드시 준수한다)

### JUnit 5 단위 테스트 (Service Layer, Mockito)

**파일**: `src/test/java/com/careflow/agency/service/AgencyCustomerServiceTest.java` (기존 클래스에 `@Nested` 그룹 추가)

- TC-1. 정상 조회 — 결제 내역 2건 → size 2, 필드 매핑 정확성(applianceBrand/Model 포함) 검증
- TC-2. 결제 내역 없음 — 빈 리스트 반환(예외 아님)
- TC-3. 존재하지 않는 userId — `NoSuchElementException`
- TC-4. COMPLETED 서비스 이력 없는(타사 또는 무관계) 고객 — `IllegalAccessException`
- TC-5. ENGINEER 역할로 호출 — `IllegalAccessException`

### JUnit 5 통합 테스트 (H2 DB 연동)

**파일**: `src/test/java/com/careflow/agency/service/AgencyCustomerServiceIntegrationTest.java` (기존 클래스에 `@Nested` 그룹 추가)

- TC-I-1. H2에 실제 결제 2건(서로 다른 상태) INSERT 후 조회 → 응답 필드가 DB 저장값과 일치하는지 검증
- TC-I-2. 동일 고객이 **타 대행사**로 접수한 A/S 건의 결제 내역은 결과에서 제외되는지 검증(`asRequest.agency.id` 필터)
- TC-I-3. READY/FAILED/CANCELLED/REFUNDED 등 모든 상태가 필터 없이 전부 포함되는지 검증(상태 무관 전체 이력)
- TC-I-4. 타 대행사 고객(COMPLETED 서비스 이력 없음) 조회 시도 → `IllegalAccessException`
- TC-I-5. 존재하지 않는 userId → `NoSuchElementException`
- TC-I-6. 최신 생성순(`createdAt DESC`) 정렬 검증

### JUnit 5 컨트롤러 테스트 (`@WebMvcTest`)

**파일**: `src/test/java/com/careflow/agency/controller/AgencyCustomerControllerTest.java` (기존 클래스에 `@Nested` 그룹 추가)

- TC-C-1. 인증된 AGENCY — 200 OK + JSON 배열 검증
- TC-C-2. 인증 없음 — 401
- TC-C-3. Service에서 `NoSuchElementException` 발생 — 404
- TC-C-4. Service에서 `IllegalAccessException` 발생 — 401
