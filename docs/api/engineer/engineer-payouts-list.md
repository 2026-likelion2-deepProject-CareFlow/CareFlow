# API 명세: 기사 본인 지급 배치 이력 조회

## 기본 정보

| 항목 | 내용 |
|------|------|
| Method | `GET` |
| URI | `/api/engineer/payouts?page=&size=` |
| 역할 | ENGINEER |
| 설명 | 기사가 본인이 대행사로부터 월별로 실제 지급받았는지(또는 대기 중인지) 확인하는 목록. 기존 `GET /api/engineer/settlements`는 건별 계산 원장(`settlements`)만 보여줄 뿐 "월별로 묶여 실제 지급됐는지"는 알 수 없었는데, 본 API가 그 공백을 채운다. |

## 인증 / 권한

- 컨트롤러에 `@PreAuthorize("hasRole('ENGINEER')")` 적용 — role이 ENGINEER가 아니면 컨트롤러 진입 전에 Spring Security가 차단하여 `403 Forbidden` 반환 (기존 `EngineerSettlementController`와 동일 패턴)
- `userDetails.getUserId()` 기준 본인 배치만 조회

## 요청 파라미터

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---------|------|------|--------|------|
| `page` | int | N | 0 | 페이지 번호 (0-based) |
| `size` | int | N | 10 | 페이지 크기 |

## 응답 DTO: `EngineerPayoutPageResponse`

```java
record EngineerPayoutPageResponse(
    Long engineerPayoutId,
    String agencyName,
    Integer payoutYear,
    Integer payoutMonth,
    int netAmountSum,
    int caseCount,
    String status,        // PENDING / PAID / DISPUTED
    LocalDateTime paidAt
)
```
정렬: `payoutYear DESC, payoutMonth DESC` (최신 월부터).

## 비즈니스 로직

1. role == ENGINEER 검증
2. `EngineerPayoutRepository.findByEngineer_IdOrderByPayoutYearDescPayoutMonthDesc(userId, pageable)` 조회
3. 엔티티 → DTO 매핑 (agencyName은 `engineerPayout.agency.agencyName`)

## 예외 처리

| 예외 | HTTP | 조건 |
|------|------|------|
| `AccessDeniedException`(Spring Security) | 403 | role != ENGINEER (`@PreAuthorize` 차단) |
| 정상(빈 결과) | 200 OK | 지급 이력이 없어도 빈 배열 반환 |

---

## 단위 테스트 명세 (JUnit 5)

> `EngineerPayoutServiceTest` — `@ExtendWith(MockitoExtension.class)`

| 테스트명 | 설명 |
|---------|------|
| `정상조회_본인배치_반환` | 본인 배치 2건 mock → content 2건, 필드 매핑 검증 |
| `ENGINEER아닌_role_예외발생` | role=AGENCY 호출 → `IllegalAccessException` |
| `배치없음_빈배열반환` | 배치 없음 → 빈 배열, 200 |

## 통합 테스트 명세 (H2 DB)

> `EngineerPayoutControllerIntegrationTest`

| 테스트명 | 설명 |
|---------|------|
| `본인배치_목록조회` | 본인 EngineerPayout 2건 저장 → GET 시 2건 반환, 최신월 순 정렬 확인 |
| `타기사배치_제외` | 다른 기사 소속 배치는 결과에서 제외 |
| `CUSTOMER권한_401` | CUSTOMER 토큰 → 401 |
