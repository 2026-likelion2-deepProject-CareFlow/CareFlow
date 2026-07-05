# API 명세: 대행사 기사 지급 배치 목록 조회

## 기본 정보

| 항목 | 내용 |
|------|------|
| Method | `GET` |
| URI | `/api/agency/engineer-payouts?year=&month=&page=&size=` |
| 역할 | AGENCY |
| 설명 | 대행사가 소속 기사별로 "이번 달 지급해야 할 금액"을 확인하는 목록. `engineer_payouts`(대행사→기사 월별 지급 배치)를 대행사·연·월 기준으로 페이징 조회한다. |

## 배경 — 이 API가 필요한 이유

기존에는 대행사가 개별 `Settlement.status`를 직접 `PAID`로 바꿔서 "기사에게 지급했다"를 표현했다. 그런데 `Settlement.status = PAID`는 DDL v14 설계상 "CareFlow가 대행사에 지급했다"(플랫폼→대행사 배치 승인)는 의미로 확정되어 있어([admin-settlement-approve.md](../../admin/admin-settlement-approve.md) 참고), 두 서로 다른 자금 흐름이 하나의 플래그에 뒤섞여 있었다. 본 API 및 [지급 완료 처리 API](./agency-engineer-payout-pay.md)가 "대행사→기사" 흐름의 정식 자리를 대신한다.

## 인증 / 권한

- `@AuthenticationPrincipal CustomUserDetails`로 로그인 사용자 식별
- `userDetails.getRole() != AGENCY` → `IllegalAccessException` (401)
- `userDetails.getAgencyId()`로 소속 대행사 ID 추출 — 본인 대행사 소속 배치만 조회됨(쿼리 조건에 agencyId 포함)

## 요청 파라미터

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---------|------|------|--------|------|
| `year` | int | Y | - | 조회 연도 |
| `month` | int | Y | - | 조회 월 (1~12) |
| `page` | int | N | 0 | 페이지 번호 (0-based) |
| `size` | int | N | 10 | 페이지 크기 |

## 응답 DTO: `AgencyEngineerPayoutListResponse`

```java
record AgencyEngineerPayoutListResponse(
    List<Item> content,
    long totalElements,
    int totalPages,
    int currentPage,
    int size
)

record Item(
    Long engineerPayoutId,
    Long engineerId,
    String engineerName,
    String engineerPhone,
    int netAmountSum,      // 이 기사에게 지급할 금액 합계 (원)
    int caseCount,         // 집계된 A/S 건수
    String status,         // PENDING / PAID / DISPUTED
    LocalDateTime paidAt,
    String payMethod,      // bank_accounts.pay_method 한글 레이블 ("계좌이체"), 미등록 시 null
    String bankAccount     // "은행명 계좌번호" 포맷, 미등록 시 null
)
```

## 비즈니스 로직

1. role == AGENCY 검증
2. `EngineerPayoutRepository.findByAgency_IdAndPayoutYearAndPayoutMonth(agencyId, year, month, pageable)` 조회
3. 현재 페이지 기사 ID 목록으로 `bank_accounts`(기사 본인 계좌) 일괄 조회 — N+1 방지 (기존 `AgencySettlementService`와 동일 패턴)
4. 엔티티 → DTO 매핑

## 예외 처리

| 예외 | HTTP | 조건 |
|------|------|------|
| `IllegalAccessException` | 401 | role != AGENCY |
| 정상(빈 결과) | 200 OK | 해당 연월에 배치가 없어도 빈 배열 반환 |

---

## 단위 테스트 명세 (JUnit 5)

> `AgencyEngineerPayoutServiceTest` — `@ExtendWith(MockitoExtension.class)`

| 테스트명 | 설명 |
|---------|------|
| `정상조회_기사목록_반환` | 소속 기사 2명의 배치 → content 2건, 각 필드 매핑 검증 |
| `계좌미등록기사는_payMethod_bankAccount_null` | bank_accounts 레코드 없는 기사 → `payMethod`/`bankAccount` null |
| `AGENCY아닌_role_예외발생` | role=ENGINEER 호출 → `IllegalAccessException` |
| `배치없음_빈배열반환` | 해당 연월 배치 없음 → content 빈 배열, 200 |

## 통합 테스트 명세 (H2 DB)

> `AgencyEngineerPayoutControllerIntegrationTest`

| 테스트명 | 설명 |
|---------|------|
| `소속기사_배치목록_정상조회` | 소속 기사 2명 EngineerPayout 저장 → GET 호출 시 content 2건 |
| `타대행사_배치_제외` | 다른 대행사 소속 기사 배치는 결과에서 제외 |
| `타월_배치_제외` | 다른 달의 배치는 결과에서 제외 |
| `CUSTOMER권한_401` | CUSTOMER 토큰 → 401 |
