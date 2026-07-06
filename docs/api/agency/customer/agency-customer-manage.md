# API 명세: 대행사 고객 관리 (정보 수정 / 비밀번호 초기화 / 차단·차단해제)

## 개요

대행사 고객 관리 화면(`AgencyCustomerPage.jsx`) 상세 패널의 "관리" 버튼 4종(수정/비밀번호 초기화/차단/차단해제)에 대응하는 API.
공통 인가 규칙은 기존 [`agency-customer-list.md`](./agency-customer-list.md), [`agency-customer-appliances.md`](./agency-customer-appliances.md)와 동일하다 — `AgencyCustomerService.verifyAgencyAccessToCustomer()`를 그대로 재사용한다:
- `AGENCY` 역할이 아니면 `IllegalAccessException`(401)
- `{userId}`가 존재하지 않으면 `NoSuchElementException`(404)
- `{userId}`가 본인 대행사로부터 COMPLETED 서비스를 1회 이상 받은 고객이 아니면 `IllegalAccessException`(401) — 타 대행사 고객 데이터 격리

---

## 1. 고객 정보 수정

| 항목 | 내용 |
|---|---|
| HTTP 메서드 | `PATCH` |
| URL | `/api/agency/customers/{userId}` |
| 인증 | JWT 필수 (`AGENCY`) |
| 설명 | 대행사 관리자가 소속 고객의 이름/연락처/상세주소를 대신 수정한다. |

### Request Body

```json
{
  "name": "김철수",
  "phone": "01012345678",
  "addressDetail": "서울시 강남구 테헤란로 123, 101동 202호"
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `name` | String | X | null이면 기존 값 유지(PATCH 의미론) |
| `phone` | String | X | null이면 기존 값 유지 |
| `addressDetail` | String | X | null이면 기존 값 유지 |

### 응답

`204 No Content`

### 비즈니스 로직

1. `verifyAgencyAccessToCustomer()`로 공통 인가 검증.
2. `User.updateProfile(name, phone, region, addressDetail)` 기존 도메인 메서드를 재사용 — region은 이 화면에서 수정 대상이 아니므로 항상 `null` 전달(도메인 메서드가 null-safe하게 무시함).

---

## 2. 고객 비밀번호 초기화

| 항목 | 내용 |
|---|---|
| HTTP 메서드 | `PATCH` |
| URL | `/api/agency/customers/{userId}/reset-password` |
| 인증 | JWT 필수 (`AGENCY`) |
| 설명 | 대행사 관리자가 소속 고객의 비밀번호를 임의의 임시 비밀번호로 초기화하고, 고객 이메일로 임시 비밀번호를 발송한다. |

### 응답

`204 No Content`

### 비즈니스 로직

1. `verifyAgencyAccessToCustomer()`로 공통 인가 검증.
2. 소셜 로그인 전용 계정(`passwordHash == null`)이면 `IllegalStateException`(403) — 초기화할 비밀번호 자체가 없음.
3. `SecureRandom` 기반으로 영문 대/소문자+숫자 조합 10자리 임시 비밀번호를 생성한다.
4. `passwordEncoder.encode()`로 해싱 후 `User.updatePassword()` 호출.
5. 고객 이메일로 임시 비밀번호를 안내 메일 발송 — 기존 `PasswordResetService`가 사용하는 `JavaMailSender` 빈을 재사용한다.
   - **알림(Notification) 테이블이 아닌 이메일로 발송하는 이유**: 평문 임시 비밀번호를 DB에 영구 저장되는 알림 레코드에 남기지 않기 위함(보안).
6. 이메일 발송 실패 시에도 비밀번호 초기화 자체는 이미 커밋되었으므로 예외를 던지지 않고 로그만 남긴다(대행사 관리자가 별도로 고객에게 안내 가능하도록).

---

## 3. 고객 차단 / 차단 해제

| 항목 | 내용 |
|---|---|
| HTTP 메서드 | `PATCH` |
| URL | `/api/agency/customers/{userId}/block`, `/api/agency/customers/{userId}/unblock` |
| 인증 | JWT 필수 (`AGENCY`) |
| 설명 | 고객 계정 상태를 `SUSPENDED`(차단)/`ACTIVE`(차단 해제)로 전환한다. |

### 응답

`204 No Content`

### 비즈니스 로직

1. `verifyAgencyAccessToCustomer()`로 공통 인가 검증.
2. `User.updateStatus("SUSPENDED")` / `User.updateStatus("ACTIVE")` 기존 도메인 메서드 재사용.
3. **차단 상태는 로그인 시점에 즉시 강제된다** — `AuthService.login()`이 이미 `if (!"ACTIVE".equals(user.getStatus())) throw new IllegalStateException(...)`로 `ACTIVE`가 아닌 계정의 로그인을 차단하고 있으므로(기존 코드, 본 API 구현 시 변경 없음), 별도의 세션 무효화 로직 없이 차단이 실질적으로 적용된다.
4. 차단 대상이 이미 `SUSPENDED`(또는 차단 해제 대상이 이미 `ACTIVE`)여도 멱등하게 동작(에러 없이 204) — 중복 클릭 방어.

---

## 테스트 명세

### 단위 테스트 (`AgencyCustomerServiceTest`)

| # | 케이스 | 예상 결과 |
|---|---|---|
| 1 | 정보 수정 성공(name/phone/addressDetail 모두 전달) | `updateProfile` 호출 검증 |
| 2 | 정보 수정 - 일부 필드만 전달(name만) | 나머지 인자 null로 `updateProfile` 호출 검증 |
| 3 | 비밀번호 초기화 성공 | `updatePassword` + 메일 발송 호출 검증 |
| 4 | 비밀번호 초기화 - 소셜 로그인 계정(passwordHash null) | `IllegalStateException` |
| 5 | 차단 성공 | `updateStatus("SUSPENDED")` 호출 검증 |
| 6 | 차단 해제 성공 | `updateStatus("ACTIVE")` 호출 검증 |
| 7 | 타 대행사 고객 접근 | `IllegalAccessException` |
| 8 | 존재하지 않는 userId | `NoSuchElementException` |

### 통합 테스트 (`AgencyCustomerControllerIntegrationTest` — H2)

| # | 케이스 | 검증 내용 |
|---|---|---|
| 1 | 정보 수정 정상 흐름 | `204` + DB `name`/`phone`/`address_detail` 변경 확인 |
| 2 | 비밀번호 초기화 정상 흐름 | `204` + DB `password_hash` 값이 초기화 전과 달라짐 확인(기존 해시와 불일치) |
| 3 | 차단 → 차단된 계정으로 로그인 시도 | 차단 API `204` 이후 `/api/auth/login` 호출 시 `403`(`IllegalStateException` 매핑) |
| 4 | 차단 해제 → 재로그인 성공 | 차단 해제 후 로그인 `200 OK` |
| 5 | 타 대행사 고객에 대한 수정 시도 | `401 Unauthorized` |
