# API 명세: 대행사 고객 관리 (차단 / 차단해제)

## 개요

대행사 고객 관리 화면(`AgencyCustomerPage.jsx`) 상세 패널의 "관리" 버튼에 대응하는 API.
공통 인가 규칙은 기존 [`agency-customer-list.md`](./agency-customer-list.md), [`agency-customer-appliances.md`](./agency-customer-appliances.md)와 동일하다 — `AgencyCustomerService.verifyAgencyAccessToCustomer()`를 그대로 재사용한다:
- `AGENCY` 역할이 아니면 `IllegalAccessException`(401)
- `{userId}`가 존재하지 않으면 `NoSuchElementException`(404)
- `{userId}`가 본인 대행사로부터 COMPLETED 서비스를 1회 이상 받은 고객이 아니면 `IllegalAccessException`(401) — 타 대행사 고객 데이터 격리

> **[변경 이력]** 대행사 관리자가 고객 정보를 대신 수정하거나 비밀번호를 초기화할 수 있는 것은 보안/개인정보 정책상 부적절하다는 피드백에 따라, "고객 정보 수정"(`PATCH /api/agency/customers/{userId}`)과 "비밀번호 초기화"(`PATCH /api/agency/customers/{userId}/reset-password`) API 및 관련 UI를 완전히 제거했다. 대행사는 이제 고객 차단/차단해제만 수행할 수 있다.

---

## 1. 고객 차단 / 차단 해제

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
| 1 | 차단 성공 | `updateStatus("SUSPENDED")` 호출 검증 |
| 2 | 차단 해제 성공 | `updateStatus("ACTIVE")` 호출 검증 |
| 3 | 타 대행사 고객 접근 | `IllegalAccessException` |
| 4 | 존재하지 않는 userId | `NoSuchElementException` |

### 통합 테스트 (`AgencyCustomerControllerIntegrationTest` — H2)

| # | 케이스 | 검증 내용 |
|---|---|---|
| 1 | 차단 → 차단된 계정으로 로그인 시도 | 차단 API `204` 이후 `/api/auth/login` 호출 시 `403`(`IllegalStateException` 매핑) |
| 2 | 차단 해제 → 재로그인 성공 | 차단 해제 후 로그인 `200 OK` |
