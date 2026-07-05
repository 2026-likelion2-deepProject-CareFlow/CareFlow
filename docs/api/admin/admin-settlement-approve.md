# API: 단일 대행사 지급 승인

## 개요

관리자(ADMIN)가 [대행사별 월별 정산 현황 조회](admin-settlement-monthly-summary.md) 화면에서 특정 대행사의 "지급 승인" 버튼을 클릭했을 때, 해당 대행사·해당 월의 **미지급 정산 전체를 지급 완료 처리**한다.
위치: Admin 정산 관리 페이지 — `AdminSettlementPage.jsx`의 `approveAgency(id)`.

---

## 엔드포인트

```
PATCH /api/admin/settlements/{agencyId}/approve?year=2026&month=6
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
| `year` | int | O | 대상 연도 |
| `month` | int | O | 대상 월 (1~12) |

### 요청 바디: 없음

### 요청 예시

```
PATCH /api/admin/settlements/1/approve?year=2026&month=6
Authorization: Bearer {accessToken}
```

---

## 알려진 제약 (구현 전 반드시 인지할 것)

> **[D 수정, DDL v14 반영]** 아래는 `settlements` 건별 직접 승인 방식이었던 v11~v13 시점의 설계였다. v14에서 `platform_settlements`(CareFlow→대행사 월별 지급 배치)와 `agency_bank_accounts`(대행사 정산금 수취 계좌)가 도입되면서, 본 API는 **`settlements` 건별이 아니라 `platform_settlements` 배치 단위로 승인**하도록 재구현되었다.

- **승인 단위 변경**: 대상 월·대행사의 `platform_settlements` 1건(`agency_id + settlement_year + settlement_month` 유니크 키)을 조회해 승인 처리한다. 해당 배치가 아예 존재하지 않으면(집계 배치가 생성된 적 없음) `NoSuchElementException`(404) — "승인할 대상 자체가 없다"는 뜻이므로 조용히 넘어가지 않는다.
- **정산금 수취 계좌 필수 검증**: `agency_bank_accounts`에 이 대행사의 계좌가 등록되어 있지 않으면 `IllegalStateException`(403)으로 지급을 막는다. DDL 설계 의도(계좌 미등록 대행사는 지급 승인 불가) 그대로 반영.
- **캐스케이드 전이**: `platform_settlements.markPaid(계좌ID)`로 배치를 확정(`status=PAID`, `paid_bank_account_id` 스냅샷, `paid_at`)한 뒤, 그 배치에 연결된(`settlements.platform_settlement_id` 기준) 하위 `settlements` 전체를 벌크 UPDATE로 `PAID` 전이한다. 개별 `Settlement`의 기존 상태가 `PENDING`이든 `DISPUTED`이든 상관없이 이 배치에 속해 있으면 전부 `PAID`로 전이된다(기존 "DISPUTED도 포함" 정책 유지).
- **멱등성**: 대상 배치가 이미 `PAID`이면 아무 것도 하지 않고 정상 종료한다 (버튼 중복 클릭·재요청에 안전).
- **`paidAt` 값**: 처리 시점의 `LocalDateTime.now()`를 배치와 하위 settlements에 동일하게 적용한다.
- **벌크 UPDATE 사용**: 하위 settlements 전이는 건별 조회 후 `markPaid()` 호출이 아니라 `SettlementRepository.markPaidByPlatformSettlementId(platformSettlementId, paidAt)` 벌크 JPQL UPDATE로 처리한다 (배치 규모가 클 수 있어 N+1 방지).
- **대행사 존재 검증**: `agencyId`가 존재하지 않으면 `NoSuchElementException` (404) — 기존과 동일.

---

## 응답

### 200 OK

응답 바디 없음.

### 에러 응답

| HTTP 상태 | 조건 |
|---|---|
| 401 Unauthorized | JWT 토큰 없음/만료 |
| 403 Forbidden | 인증은 되었으나 role != ADMIN, 또는 정산금 수취 계좌 미등록(`IllegalStateException`) |
| 404 Not Found | 존재하지 않는 `agencyId`, 또는 해당 기간의 `platform_settlements` 배치가 존재하지 않음 |
| 400 Bad Request | `month`이 1~12 범위를 벗어남 |

---

## 처리 로직 (Pipeline)

1. **검증**: ADMIN role 확인 → 아니면 `IllegalAccessException`
2. **대행사 존재 검증**: `AgenciesRepository.existsById(agencyId)` → 없으면 `NoSuchElementException`
3. **month 검증**: 1~12 범위 확인 → `IllegalArgumentException`
4. **배치 조회**: `PlatformSettlementRepository.findByAgency_IdAndSettlementYearAndSettlementMonth(agencyId, year, month)` → 없으면 `NoSuchElementException`
5. **멱등 체크**: 배치 `status == "PAID"`면 아무 것도 하지 않고 정상 종료
6. **계좌 검증**: `AgencyBankAccountRepository.findByAgencyId(agencyId)` → 없으면 `IllegalStateException`
7. **배치 확정**: `platformSettlement.markPaid(bankAccount.getId())` (status=PAID, paid_bank_account_id, paid_at 세팅)
8. **캐스케이드**: `SettlementRepository.markPaidByPlatformSettlementId(platformSettlement.getId(), paidAt)` 벌크 UPDATE로 하위 settlements 전체 PAID 전이

---

## 개발 구성요소

| 계층 | 클래스 |
|---|---|
| Controller | `com.careflow.admin.controller.AdminSettlementController` (변경 없음) |
| Service (수정) | `com.careflow.admin.service.AdminSettlementService` — `PlatformSettlementRepository`, `AgencyBankAccountRepository` 의존성 추가 |
| Repository (신규) | `com.careflow.settlement.repository.PlatformSettlementRepository.findBySettlementYearAndSettlementMonthAndStatusNot` |
| Repository (신규) | `com.careflow.settlement.repository.SettlementRepository.markPaidByPlatformSettlementId` (벌크 UPDATE) |
| Entity (수정) | `com.careflow.settlement.entity.PlatformSettlement.markPaid(Long paidBankAccountId)` |

---

## 테스트 명세 (필수 — 아래 항목을 반드시 준수한다)

### JUnit 5 단위 테스트 (Service Layer, Mockito)

**파일**: `src/test/java/com/careflow/admin/service/AdminSettlementServiceTest.java` (⑨ 전용 `@Nested` 그룹)

- TC-1. PENDING 배치 + 계좌 등록됨 → `markPaid(계좌ID)` 및 하위 settlements 캐스케이드 호출 검증
- TC-2. role이 ADMIN이 아닌 경우 → `IllegalAccessException`
- TC-3. 존재하지 않는 agencyId → `NoSuchElementException`
- TC-4. month가 0 또는 13 → `IllegalArgumentException`
- TC-5. 해당 기간 정산 배치가 없으면 → `NoSuchElementException`
- TC-6. 이미 PAID인 배치 재승인 요청 → 에러 없이 정상 종료, 값 변경 없음(멱등)
- TC-7. 정산금 수취 계좌 미등록 → `IllegalStateException`, 배치 상태 변경 없음

### JUnit 5 통합 테스트 (H2 DB, `@SpringBootTest` + `@ActiveProfiles("local")`)

**파일**: `src/test/java/com/careflow/admin/service/AdminSettlementServiceIntegrationTest.java` (⑨ 전용 `@Nested` 그룹)

- TC-I-1. PENDING 배치 승인 → 배치·하위 settlements 모두 `status="PAID"`, `paidAt`/`paid_bank_account_id` 확정 확인
- TC-I-2. 타 대행사 배치의 settlement는 변경되지 않는다
- TC-I-3. 배치에 연결되지 않은(다른 달 소속) settlement는 변경되지 않는다
- TC-I-4. 이미 PAID인 배치 재승인 요청 → 에러 없이 정상 종료, 기존 `paidAt`/계좌 값 변경 없음(멱등)
- TC-I-5. DISPUTED 건도 배치에 묶여 있으면 승인 시 PAID로 전이되는지 검증
- TC-I-6. 해당 기간 정산 배치가 없으면 `NoSuchElementException`
- TC-I-7. 정산금 수취 계좌 미등록 → `IllegalStateException`, 배치·하위 정산 상태 변경 없음

### JUnit 5 컨트롤러 테스트 (`@WebMvcTest`)

**파일**: `src/test/java/com/careflow/admin/controller/AdminSettlementControllerTest.java` (⑨ 전용 메서드, 서비스 mock — 변경 없음)

- TC-C-1. 인증된 ADMIN — 200 OK
- TC-C-2. 인증 없음 → 401
- TC-C-3. 존재하지 않는 agencyId → 404
