# API 명세: 관리자 대행사→기사 지급 배치 전체 조회 (분쟁 조정용)

## 기본 정보

| 항목 | 내용 |
|------|------|
| Method | `GET` |
| URI | `/api/admin/engineer-payouts?year=&month=&status=` |
| 역할 | ADMIN |
| 설명 | CareFlow는 대행사→기사 지급 자금을 소유·집행하지 않지만, 기사가 "급여를 못 받았다"고 민원을 제기했을 때 최소한의 조정 근거로 삼기 위해 전체 `engineer_payouts` 배치를 열람할 수 있어야 한다. [v13 정산 데이터 전체 노출 원칙] 적용 대상. |

## 인증 / 권한

- `SecurityConfig`의 `/api/admin/**` 전역 규칙(`hasRole("ADMIN")`)에 의해 role이 ADMIN이 아니면 컨트롤러 진입 전에 Spring Security가 차단하여 `403 Forbidden` 반환
- 서비스 레이어에도 `userDetails.getRole() != ADMIN` → `IllegalAccessException` 검증이 추가로 존재(방어적 이중 체크, 기존 `AdminSettlementService` 패턴과 동일) — 단, HTTP 경유 시에는 위 SecurityConfig 규칙이 먼저 적용되어 이 분기에 도달하지 않는다

## 요청 파라미터

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---------|------|------|--------|------|
| `year` | int | Y | - | 조회 연도 |
| `month` | int | Y | - | 조회 월 (1~12) |
| `status` | String | N | 없음(전체) | `PENDING`/`PAID`/`DISPUTED` 중 하나로 필터링. 특히 `DISPUTED` 필터가 분쟁 조정 드릴다운의 핵심 사용처 |

## 응답 DTO: `AdminEngineerPayoutListResponse`

```java
record AdminEngineerPayoutListResponse(
    List<Item> items
)

record Item(
    Long engineerPayoutId,
    Long agencyId,
    String agencyName,
    Long engineerId,
    String engineerName,
    int netAmountSum,
    int caseCount,
    String status,
    LocalDateTime paidAt
)
```

## 비즈니스 로직

1. role == ADMIN 검증
2. `month` 1~12 범위 검증 → `IllegalArgumentException`
3. `status` 파라미터 검증 — 값이 있으면 `PENDING`/`PAID`/`DISPUTED` 중 하나인지 확인 → `IllegalArgumentException`
4. 해당 연월(+status 필터) 배치 전체 조회 후 매핑

## 예외 처리

| 예외 | HTTP | 조건 |
|------|------|------|
| `AccessDeniedException`(Spring Security) | 403 | role != ADMIN (SecurityConfig 차단) |
| `IllegalArgumentException` | 400 | month 범위 초과, 또는 잘못된 status 값 |
| 정상(빈 결과) | 200 OK | 배치가 없어도 빈 배열 반환 |

---

## 단위 테스트 명세 (JUnit 5)

> `AdminEngineerPayoutServiceTest`

| 테스트명 | 설명 |
|---------|------|
| `정상조회_전체배치_반환` | 배치 3건(대행사·기사 혼합) → 전부 매핑되어 반환 |
| `status필터_DISPUTED만조회` | DISPUTED 1건 + PAID 2건 → `status=DISPUTED` 조회 시 1건만 |
| `ADMIN아닌_role_예외발생` | role=AGENCY → `IllegalAccessException` |
| `잘못된status값_예외발생` | `status=INVALID` → `IllegalArgumentException` |
| `month범위초과_예외발생` | month=13 → `IllegalArgumentException` |

## 통합 테스트 명세 (H2 DB)

> `AdminEngineerPayoutControllerIntegrationTest`

| 테스트명 | 설명 |
|---------|------|
| `전체대행사_배치조회` | 대행사 2곳 배치 저장 → 둘 다 조회됨 |
| `DISPUTED필터_조회` | DISPUTED 배치만 필터링되어 반환 |
| `CUSTOMER권한_401` | CUSTOMER 토큰 → 401 |
| `잘못된status_400` | `status=INVALID` → 400 |
