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

- **상태 전이 범위**: [DDL v11]에서 `settlements.status` ENUM이 실제로 `PENDING → PAID`(또는 `DISPUTED`) 3단계로 재정비되어 `APPROVED`가 완전히 제거됐다 — "월초 일괄 승인 단계가 상태 전이 없이 바로 지급으로 이어지는 구조라 APPROVED가 별도 상태로 존재할 실익이 없다"는 DDL 변경 사유가 이 Admin API 설계 의도와 정확히 일치한다.
  본 API는 대상 월의 해당 대행사 정산 중 **`PAID`가 아닌 모든 건**(`PENDING`/`DISPUTED`)을 `Settlement.markPaid()`를 호출해 **직접 `PAID`로 전이**시킨다.
  > DISPUTED 건까지 admin이 일괄 PAID 처리하는 것이 실제 운영 요구사항과 다르다면(예: DISPUTED는 별도 검토 후 처리해야 함 등) 추후 조정이 필요할 수 있으므로, PR 시 리뷰어에게 이 결정을 명시적으로 알린다.
- **멱등성**: 이미 전부 `PAID`인 경우(미지급 건 0개)에도 에러 없이 200 OK를 반환한다 (버튼 중복 클릭·재요청에 안전).
- **`paidAt` 값**: 처리 시점의 `LocalDateTime.now()`로 일괄 설정한다 (건별로 시각이 약간씩 달라질 수 있음 — 정밀한 동시성 제어가 필요한 도메인이 아니므로 허용).
- **더티 체킹 사용**: 벌크 JPQL UPDATE가 아니라 엔티티를 조회해 `Settlement.markPaid()` 도메인 메서드를 호출하고 트랜잭션 커밋 시점에 더티 체킹으로 반영한다 (CLAUDE.md 컨벤션 — Setter 대신 도메인 메서드로 상태 변경).
- **대행사 존재 검증**: `agencyId`가 존재하지 않으면 `NoSuchElementException` (404).

---

## 응답

### 200 OK

응답 바디 없음.

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
5. **대상 조회**: `SettlementRepository.findUnpaidByAgencyAndMonth(agencyId, from, to)` — `status <> 'PAID'`인 건만 조회
6. **상태 전이**: 조회된 각 `Settlement`에 대해 `markPaid()` 호출 (더티 체킹으로 UPDATE, 대상이 0건이면 아무 것도 하지 않고 정상 종료)

---

## 개발 구성요소

| 계층 | 클래스 |
|---|---|
| Controller | `com.careflow.admin.controller.AdminSettlementController` |
| Service | `com.careflow.admin.service.AdminSettlementService` |
| Repository (수정) | `com.careflow.settlement.repository.SettlementRepository` (`findUnpaidByAgencyAndMonth` 쿼리 추가) |
| Entity (재사용, 수정 없음) | `com.careflow.settlement.entity.Settlement.markPaid()` |

---

## 테스트 명세 (필수 — 아래 항목을 반드시 준수한다)

### JUnit 5 단위 테스트 (Service Layer, Mockito)

**파일**: `src/test/java/com/careflow/admin/service/AdminSettlementServiceTest.java` (⑨ 전용 `@Nested` 그룹)

- TC-1. 정상 승인 — 미지급 정산 3건(PENDING/DISPUTED 혼합) → 전부 `markPaid()` 호출 검증(Mockito verify)
- TC-2. role이 ADMIN이 아닌 경우 → `IllegalAccessException`
- TC-3. 존재하지 않는 agencyId → `NoSuchElementException`
- TC-4. month가 0 또는 13 → `IllegalArgumentException`
- TC-5. 미지급 건 0개(전부 PAID) → 예외 없이 정상 종료, `markPaid()` 호출 없음

### JUnit 5 통합 테스트 (H2 DB, `@SpringBootTest` + `@ActiveProfiles("local")`)

**파일**: `src/test/java/com/careflow/admin/service/AdminSettlementServiceIntegrationTest.java` (⑨ 전용 `@Nested` 그룹)

- TC-I-1. PENDING 2건 승인 → H2 실제 조회 시 두 건 모두 `status="PAID"`, `paidAt` not null로 변경 확인
- TC-I-2. 타 대행사 정산 불변 — 승인 대상이 아닌 타 대행사 정산의 `status`는 변경되지 않음
- TC-I-3. 타 월 정산 불변 — 대상 월이 아닌 정산은 변경되지 않음
- TC-I-4. 이미 PAID인 건 재승인 요청 — 에러 없이 정상 종료, 기존 `paidAt` 값 변경 없음(대상에서 제외되므로)
- TC-I-5. DISPUTED 건도 승인 대상에 포함되어 PAID로 전이되는지 검증

### JUnit 5 컨트롤러 테스트 (`@WebMvcTest`)

**파일**: `src/test/java/com/careflow/admin/controller/AdminSettlementControllerTest.java` (⑨ 전용 메서드)

- TC-C-1. 인증된 ADMIN — 200 OK
- TC-C-2. 인증 없음 → 401
- TC-C-3. 존재하지 않는 agencyId → 404
