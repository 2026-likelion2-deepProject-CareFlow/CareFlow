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

- **상태 전이 범위**: [admin-settlement-approve.md](admin-settlement-approve.md)의 "상태 전이 범위" 항목과 동일 — 대상 월의 **전체 대행사**에 걸쳐 `PAID`가 아닌 모든 정산(`PENDING`/`DISPUTED`)을 `Settlement.markPaid()`로 직접 전이한다. ([DDL v11] `settlements.status`에서 `APPROVED`가 실제로 제거되어, 이제 이 API의 전이 대상은 곧 DB 상태 도메인 전체와 정확히 일치한다.)
- **멱등성**: 미지급 건이 0개(전체 이미 PAID)여도 에러 없이 200 OK를 반환한다.
- **agencyId 경로 변수 없음**: 단일 대행사 승인과 달리 대행사 존재 검증 단계가 없다 — 대상 월에 정산 자체가 하나도 없어도 정상 200 OK(빈 처리).
- **처리량**: 전체 대행사 대상이므로 대상 건수가 많을 수 있다. 벌크 JPQL UPDATE 대신 엔티티 조회 후 도메인 메서드 호출 방식(더티 체킹)을 유지하되, 조회 시 `agency`/`engineer` 등 불필요한 연관관계를 즉시 로딩하지 않도록 주의한다(상태 변경만 필요하므로 fetch join 불필요).
- **더티 체킹 사용**: `admin-settlement-approve.md`와 동일하게 Setter 없이 도메인 메서드로 상태 변경한다.

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
3. **기간 계산**: `from = LocalDate.of(year, month, 1).atStartOfDay()`, `to = from.plusMonths(1)`
4. **대상 조회**: `SettlementRepository.findUnpaidByMonth(from, to)` — agencyId 필터 없이 `status <> 'PAID'`인 전체 건 조회
5. **상태 전이**: 조회된 각 `Settlement`에 대해 `markPaid()` 호출 (더티 체킹으로 UPDATE, 대상이 0건이면 아무 것도 하지 않고 정상 종료)

---

## 개발 구성요소

| 계층 | 클래스 |
|---|---|
| Controller | `com.careflow.admin.controller.AdminSettlementController` |
| Service | `com.careflow.admin.service.AdminSettlementService` |
| Repository (수정) | `com.careflow.settlement.repository.SettlementRepository` (`findUnpaidByMonth` 쿼리 추가) |
| Entity (재사용, 수정 없음) | `com.careflow.settlement.entity.Settlement.markPaid()` |

---

## 테스트 명세 (필수 — 아래 항목을 반드시 준수한다)

### JUnit 5 단위 테스트 (Service Layer, Mockito)

**파일**: `src/test/java/com/careflow/admin/service/AdminSettlementServiceTest.java` (⑩ 전용 `@Nested` 그룹)

- TC-1. 정상 일괄 승인 — 여러 대행사에 걸친 미지급 정산 5건 → 전부 `markPaid()` 호출 검증(Mockito verify)
- TC-2. role이 ADMIN이 아닌 경우 → `IllegalAccessException`
- TC-3. month가 0 또는 13 → `IllegalArgumentException`
- TC-4. 미지급 건 0개 → 예외 없이 정상 종료, `markPaid()` 호출 없음

### JUnit 5 통합 테스트 (H2 DB, `@SpringBootTest` + `@ActiveProfiles("local")`)

**파일**: `src/test/java/com/careflow/admin/service/AdminSettlementServiceIntegrationTest.java` (⑩ 전용 `@Nested` 그룹)

- TC-I-1. 대행사 3곳에 걸친 미지급 정산 일괄 승인 — 전부 H2 실제 조회 시 `status="PAID"`로 변경 확인
- TC-I-2. 이미 PAID인 건은 영향받지 않음 (paidAt 불변)
- TC-I-3. 타 월 정산 불변 — 대상 월이 아닌 정산은 변경되지 않음
- TC-I-4. 정산이 하나도 없는 월 요청 → 에러 없이 정상 종료

### JUnit 5 컨트롤러 테스트 (`@WebMvcTest`)

**파일**: `src/test/java/com/careflow/admin/controller/AdminSettlementControllerTest.java` (⑩ 전용 메서드)

- TC-C-1. 인증된 ADMIN — 200 OK
- TC-C-2. 인증 없음 → 401
- TC-C-3. year/month 파라미터 누락 → 400
