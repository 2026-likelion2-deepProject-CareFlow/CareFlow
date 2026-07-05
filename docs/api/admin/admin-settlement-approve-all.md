# API: 미지급 전체 일괄 승인

## 개요

관리자(ADMIN)가 [대행사별 월별 정산 현황 조회](admin-settlement-monthly-summary.md) 화면 상단의 "미지급 전체 승인" 버튼을 클릭했을 때, 해당 월 **전체 대행사의 미지급 정산을 한 번에 지급 완료 처리**한다.
위치: Admin 정산 관리 페이지 — `AdminSettlementPage.jsx`의 `approveAll()`.

[단일 대행사 지급 승인](admin-settlement-approve.md) API와 상태 전이 로직·설계 결정(“PENDING/DISPUTED → PAID 직접 전이”, 멱등성 등)을 동일하게 공유하며, 대상 범위만 **agencyId 필터 없이 전체 대행사**로 확장된다.

---

## 엔드포인트

```
PATCH /api/admin/settlements/approve-all?year=2026&month=6
```

---

## 인증/인가

- **필수 인증**: JWT Bearer 토큰
- **허용 역할**: `ADMIN`
- 컨트롤러에서 `checkAdminRole()` 검증 → 아니면 `IllegalAccessException` (401)

---

## 요청

### 쿼리 파라미터

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `year` | int | O | 대상 연도 |
| `month` | int | O | 대상 월 (1~12) |

### 요청 바디: 없음

### 요청 예시

```
PATCH /api/admin/settlements/approve-all?year=2026&month=6
Authorization: Bearer {accessToken}
```

---

## 알려진 제약 (구현 전 반드시 인지할 것)

> **[D 수정, DDL v14 반영]** [admin-settlement-approve.md](admin-settlement-approve.md)와 동일하게, `settlements` 건별 직접 승인 방식에서 **`platform_settlements` 배치 단위 일괄 승인**으로 재구현되었다.

- **대상 조회**: 대상 월(agencyId 필터 없이) `platform_settlements.status <> 'PAID'`인 배치 전체를 조회한다.
- **계좌 미등록 대행사는 개별 스킵**: 단일 승인과 달리 이 API는 여러 대행사를 한 번에 처리하므로, 특정 대행사의 계좌가 미등록이어도 그 대행사만 건너뛰고(경고 로그 남김) **나머지 대행사는 계속 처리**한다 — 한 대행사의 계좌 미등록이 전체 일괄 승인을 막지 않도록 함. (단일 승인 API처럼 즉시 `IllegalStateException`을 던져 전체를 중단시키지 않음.)
- **멱등성**: 미지급 배치가 0개(전체 이미 PAID)여도 에러 없이 200 OK를 반환한다.
- **agencyId 경로 변수 없음**: 단일 대행사 승인과 달리 대행사 존재 검증 단계가 없다 — 대상 월에 배치 자체가 하나도 없어도 정상 200 OK(빈 처리).
- **캐스케이드**: 승인된 배치마다 `admin-settlement-approve.md`와 동일하게 `platform_settlement.markPaid(계좌ID)` + `SettlementRepository.markPaidByPlatformSettlementId(...)` 벌크 UPDATE로 하위 settlements를 일괄 전이한다.

---

## 응답

### 200 OK

응답 바디 없음.

### 에러 응답

| HTTP 상태 | 조건 |
|---|---|
| 401 Unauthorized | JWT 토큰 없음/만료 |
| 403 Forbidden | 인증은 되었으나 role != ADMIN |
| 400 Bad Request | `month`이 1~12 범위를 벗어남 |

---

## 처리 로직 (Pipeline)

1. **검증**: ADMIN role 확인 → 아니면 `IllegalAccessException`
2. **month 검증**: 1~12 범위 확인 → `IllegalArgumentException`
3. **대상 조회**: `PlatformSettlementRepository.findBySettlementYearAndSettlementMonthAndStatusNot(year, month, "PAID")` — agencyId 필터 없이 미지급 배치 전체 조회
4. **배치별 처리 반복**: 각 배치에 대해
   - 이미 `PAID`면 스킵(멱등)
   - `AgencyBankAccountRepository.findByAgencyId(agencyId)` 없으면 `IllegalStateException` 발생 → **이 배치만 경고 로그 남기고 continue**, 나머지 배치는 계속 진행
   - 계좌가 있으면 `markPaid(계좌ID)` + 하위 settlements 벌크 UPDATE

---

## 개발 구성요소

| 계층 | 클래스 |
|---|---|
| Controller | `com.careflow.admin.controller.AdminSettlementController` (변경 없음) |
| Service (수정) | `com.careflow.admin.service.AdminSettlementService` — `approvePlatformSettlement()` private 공통 메서드를 ⑨/⑩에서 재사용 |
| Repository (신규) | `com.careflow.settlement.repository.PlatformSettlementRepository.findBySettlementYearAndSettlementMonthAndStatusNot` |
| Repository (신규) | `com.careflow.settlement.repository.SettlementRepository.markPaidByPlatformSettlementId` (벌크 UPDATE) |

---

## 테스트 명세 (필수 — 아래 항목을 반드시 준수한다)

### JUnit 5 단위 테스트 (Service Layer, Mockito)

**파일**: `src/test/java/com/careflow/admin/service/AdminSettlementServiceTest.java` (⑩ 전용 `@Nested` 그룹)

- TC-1. 대행사 2곳의 미지급 배치 + 둘 다 계좌 등록됨 → 둘 다 PAID 전이
- TC-2. role이 ADMIN이 아닌 경우 → `IllegalAccessException`
- TC-3. month가 0 또는 13 → `IllegalArgumentException`
- TC-4. 미지급 배치 0개 → 예외 없이 정상 종료
- TC-5. 계좌 미등록 대행사는 스킵되고, 나머지 대행사는 정상 처리된다

### JUnit 5 통합 테스트 (H2 DB, `@SpringBootTest` + `@ActiveProfiles("local")`)

**파일**: `src/test/java/com/careflow/admin/service/AdminSettlementServiceIntegrationTest.java` (⑩ 전용 `@Nested` 그룹)

- TC-I-1. 대행사 여러 곳에 걸친 미지급 배치가 전부 PAID로 전이된다
- TC-I-2. 이미 PAID인 배치는 영향받지 않음 (paidAt 불변)
- TC-I-3. 대상 월이 아닌 배치는 변경되지 않는다
- TC-I-4. 정산 배치가 하나도 없는 월 요청 → 에러 없이 정상 종료
- TC-I-5. 계좌 미등록 대행사는 스킵되고, 나머지 대행사는 정상 처리된다

### JUnit 5 컨트롤러 테스트 (`@WebMvcTest`)

**파일**: `src/test/java/com/careflow/admin/controller/AdminSettlementControllerTest.java` (⑩ 전용 메서드, 서비스 mock — 변경 없음)

- TC-C-1. 인증된 ADMIN — 200 OK
- TC-C-2. 인증 없음 → 401
- TC-C-3. year/month 파라미터 누락 → 400
