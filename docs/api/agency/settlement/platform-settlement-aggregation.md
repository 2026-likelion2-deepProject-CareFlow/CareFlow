# CareFlow→대행사 정산 집계 배치 로직 명세

> **[갱신 이력]** 최초 작성 시점(v11)에는 방향이 "대행사→플랫폼"(대행사가 플랫폼에 수수료를 납부)으로 잘못 기술되어 있었다. v12에서 실제 자금 흐름은 그 반대(**CareFlow가 대행사에 지급**)로 정정되었고, DDL v14에서 `platform_settlements`의 컬럼명(`total_gross_amount`→`gross_amount_sum` 등)도 함께 바뀌었는데 이 문서는 그 시점에 갱신되지 않아 코드와 어긋난 채로 방치되어 있었다. 이번 갱신에서 방향·컬럼명·ADMIN 승인 로직(D 수정)·engineer_payouts 병행 집계(신규)까지 실제 코드 기준으로 다시 정리한다.

## 개요

| 항목 | 내용 |
|---|---|
| 목적 | `settlements`(건별 기사·대행사 정산)를 대행사·연·월 단위로 GROUP BY 집계하여 `platform_settlements`(**CareFlow가 대행사에 지급할** 월별 배치) 레코드를 생성 |
| 트리거 방식 | 별도 스케줄러가 아니라, 기존 `SettlementGenerationService.generateForMonth()`(월별 정산 자동 생성 배치)에 **이어서** 같은 트랜잭션 흐름 안에서 실행 |
| 실행 시점 | 매월 1일 01:00 (`SettlementSchedulerConfig`의 기존 Quartz 트리거 그대로 재사용) |
| 대상 데이터 | 이번 배치 실행에서 새로 생성된 `Settlement`(PENDING) 전체 |
| 생성 단위 | 대행사(`agency_id`) × 정산 대상 연(`settlement_year`) × 정산 대상 월(`settlement_month`) 당 1행 — `uk_platform_settlement_period` 유니크 제약과 동일 |
| 생성 상태 | `PlatformSettlement.status = PENDING` |
| 패키지 위치 | `com.careflow.agency.scheduler` (`SettlementGenerationService`, `SettlementGenerationJob`) |
| 근거 스키마 | `sql/CareFlow_DDL_v14.sql` — `platform_settlements`(752행), `engineer_payouts`(797행), `settlements.platform_settlement_id`/`engineer_payout_id` FK |

---

## 설계 결정 — 집계 기준을 "정산 생성 시점의 대상 월(targetMonth)"으로 삼은 이유

`CareFlow_DDL_v14.sql`의 `platform_settlements.settlement_year` 컬럼 주석은 "집계 대상 settlements.paid_at 기준"이라고 되어 있으나, `settlements.paid_at`은 ADMIN이 이 배치를 승인(`markPaid`)하는 시점에만 채워지는 컬럼으로, **정산 생성 시점과는 무관하게 임의로 늦게 채워질 수 있음.** 이 배치는 "이번 배치가 이번 달에 만든 Settlement를 이어서 즉시 집계"하는 요구사항이므로, `settlements.paid_at`이 아니라 **`SettlementGenerationService.generateForMonth(YearMonth targetMonth)`에 전달된 `targetMonth`(정산 대상 월 = 결제가 발생한 전월)** 를 `platform_settlements.settlement_year`/`settlement_month`로 그대로 사용한다.

- 이렇게 하면 `platform_settlements`는 "CareFlow가 이 대행사에 지급해야 할 이번 달 금액 청구서"라는 의미를 가지며, 개별 기사 지급 완료 여부와 무관하게 매월 1일 즉시 생성된다.
- `DISPUTED` 상태(생성 직후에는 발생하지 않지만 이후 상태 변경으로 발생 가능)나 이미 `PAID`인 배치는 집계 대상에서 제외한다.

---

## 처리 흐름

```
[매월 1일 01:00] Quartz Trigger 발동
        │
        ▼
SettlementGenerationJob.execute()
        │
        ▼
SettlementGenerationService.generateForMonth(targetMonth)
        │
        ├─ 1~5. 결제 건별 Settlement(PENDING) 생성
        │       (agency-settlement-scheduler.md 참고)
        │       → 이번 배치에서 생성된 Settlement 목록을 메모리에 보관
        │
        ├─ 6. generatePlatformSettlements(targetMonth, createdSettlements)
        │       — 생성된 Settlement를 agency_id 기준으로 GROUP BY
        │       ┌─ grossAmountSum  = Σ settlement.grossAmount
        │       ├─ platformFeeSum  = Σ settlement.platformFee (CareFlow 고정 수수료 10%)
        │       ├─ payoutAmountSum = Σ (settlement.agencyFee + settlement.engineerNetAmount)
        │       │                  = grossAmountSum - platformFeeSum
        │       │                  (CareFlow가 대행사에 실제 지급할 금액 — [DDL v14 신규])
        │       └─ settlementCount = 그룹 내 건수
        │       (agency, targetYear, targetMonth) 기준 기존 PlatformSettlement 존재 여부 확인
        │       ┌─ 없으면: PlatformSettlement.create(...) 신규 생성 (status=PENDING)
        │       ├─ 있고 PENDING이면: 기존 합계에 누적(accumulate) — 스케줄러 재실행(Misfire 복구) 대비
        │       └─ 있고 PAID/DISPUTED면: 누적하지 않고 경고 로그, 해당 Settlement들은 미할당(NULL) 상태로 남김
        │       각 Settlement.assignPlatformSettlement(platformSettlement) 호출
        │       → settlements.platform_settlement_id FK 채움
        │
        └─ 7. generateEngineerPayouts(targetMonth, createdSettlements)  ※ platform_settlements와 완전 독립
                — 같은 Settlement 목록을 (agency_id, engineer_id) 기준으로 GROUP BY
                ┌─ netAmountSum = Σ settlement.engineerNetAmount
                └─ caseCount    = 그룹 내 건수
                (agency, engineer, targetYear, targetMonth) 기준 기존 EngineerPayout 존재 여부 확인
                → 위와 동일한 신규 생성/누적/PAID·DISPUTED 스킵 패턴
                각 Settlement.assignEngineerPayout(engineerPayout) 호출
                → settlements.engineer_payout_id FK 채움 (platform_settlement_id와 독립적으로 채워짐)
```

`platform_settlements` 집계(6)와 `engineer_payouts` 집계(7)는 서로 완전히 독립적으로 수행된다. 한쪽 집계가 이미 PAID라서 스킵되어도 다른 쪽 집계에는 영향을 주지 않는다 — 같은 Settlement 1건이 `platform_settlement_id`는 채워지고 `engineer_payout_id`는 NULL로 남는(또는 그 반대) 상황이 정상적으로 발생할 수 있다.

---

## 이후 흐름 — ADMIN 승인과 대행사→기사 지급 ([D], [E] 수정 반영)

이 배치가 만드는 것은 "지급해야 할 금액이 얼마인지"를 계산한 결과(status=PENDING)일 뿐, 실제 지급 실행은 다음 두 API가 각각 담당한다.

1. **CareFlow→대행사** ([admin-settlement-approve.md](../../admin/admin-settlement-approve.md), [admin-settlement-approve-all.md](../../admin/admin-settlement-approve-all.md))
   - ADMIN이 `platform_settlements`를 배치 단위로 승인 — `agency_bank_accounts`에 계좌가 등록되어 있어야 승인 가능
   - 승인 시 `platformSettlement.markPaid(계좌ID)` + 하위 `settlements` 전체를 `platform_settlement_id` 기준 벌크 UPDATE로 PAID 전이
2. **대행사→기사** ([agency-engineer-payouts-list.md](./agency-engineer-payouts-list.md), [agency-engineer-payout-pay.md](./agency-engineer-payout-pay.md))
   - 대행사가 `engineer_payouts`를 배치 단위로 "지급 완료" 표시 (자체 신고, 계좌 검증 없음 — CareFlow는 이 자금을 집행하지 않음)
   - **[E 수정]** 과거에는 대행사가 개별 `Settlement.status`를 직접 `PAID`로 바꿀 수 있었으나, 이는 CareFlow 승인 전에 임의로 "지급 완료"를 자칭할 수 있는 구멍이었다. 이제 `PATCH /api/agency/settlements/{id}/status`는 `PAID`를 거부하고 `PENDING`/`DISPUTED`(이의 제기용)만 허용한다.

---

## 중복 방지 / 재실행 안전성

- 이번 배치 실행에서 새로 생성된 `Settlement`만을 대상으로 하므로, 정상적인 매월 1회 실행에서는 항상 신규 `PlatformSettlement`/`EngineerPayout`이 생성된다.
- 서버 재기동 등으로 동일 대상 월에 대해 배치가 두 번 실행되는 극단적 상황(Misfire 복구 직후 재실행 등)에 대비해, 유니크 키(`agency_id, settlement_year, settlement_month` / `agency_id, engineer_id, payout_year, payout_month`)로 기존 레코드를 먼저 조회하고, 존재하면 `accumulate()`로 합계에 더하는 방식으로 구현한다.
- 단, 해당 기간의 배치가 이미 `PAID`/`DISPUTED`로 상태가 바뀐 뒤라면 합계를 임의로 변경하지 않고 경고 로그만 남긴다(재무 데이터 무결성 보호) — 이 경우 관련 Settlement는 해당 FK가 `NULL` 상태로 남아 수동 확인이 필요하다.

---

## 구현 파일 목록

| 파일 | 역할 |
|---|---|
| `SettlementGenerationService.java` | `generatePlatformSettlements()`, `generateEngineerPayouts()` — 두 집계를 각각 수행, `Result`에 `platformSettlementsCreated`/`engineerPayoutsCreated` 포함 |
| `SettlementGenerationJob.java` | 완료 로그에 두 집계 건수 출력 |
| `PlatformSettlement.java` | 컬럼: `gross_amount_sum`/`platform_fee_sum`/`payout_amount_sum`/`paid_bank_account_id`. `accumulate(gross, fee, payout, count)`, `markPaid(계좌ID)` |
| `EngineerPayout.java` | 컬럼: `net_amount_sum`/`case_count`. `accumulate(net, count)`, `markPaid()`(계좌 검증 없음) |
| `PlatformSettlementRepository.java` / `EngineerPayoutRepository.java` | 각각의 유니크 키 조회, ADMIN 조회용 쿼리 |
| `AdminSettlementService.java` | `platform_settlements` 배치 승인 + 하위 settlements 캐스케이드 |
| `AgencyEngineerPayoutService.java` | `engineer_payouts` 배치 조회/지급완료 |
| `AgencySettlementService.java` | `updateStatus()` — PAID 전이 제거, PENDING/DISPUTED만 허용 |

---

## 테스트 요구사항

- **JUnit 5 단위 테스트** (`SettlementGenerationServiceTest`): `GeneratePlatformSettlements`, `GenerateEngineerPayouts` 두 `@Nested` 그룹에서 각각 동일 그룹 집계, 서로 다른 그룹 각각 생성, 대상 0건, 기존 PENDING 누적, 기존 PAID 스킵 케이스를 모두 검증.
- **H2 DB 통합 테스트** (`SettlementGenerationServiceIntegrationTest`): 실제 DB에서 `settlements.platform_settlement_id`/`engineer_payout_id`가 각각 올바르게 채워지는지, 두 집계 테이블의 합계·건수가 실제 저장된 값과 일치하는지 확인.
- **ADMIN 승인 / 대행사 지급완료 테스트**: `AdminSettlementServiceTest`/`IntegrationTest`, `AgencyEngineerPayoutServiceTest`/`ControllerIntegrationTest` 참고.

---

## 로그 예시

```
[월별 정산] 2026년 6월 정산 생성 시작 (대상 기간: 2026-06-01T00:00 ~ 2026-07-01T00:00)
[월별 정산] 대상 결제 건 조회 완료: 총 152건
[월별 정산] 정산 생성 완료: 148건 / 스킵(기사 없음): 3건 / 오류: 1건
[플랫폼 정산 집계] 대행사 3곳 대상 집계 시작 — 대상 Settlement 148건
[플랫폼 정산 집계] agency=케어플로우 서울대행사, 2026년 6월, 건수=52, gross=15200000원, fee=1520000원
[플랫폼 정산 집계] 생성 완료: 3건
[기사 지급 집계] 대행사·기사 조합 41건 대상 집계 시작 — 대상 Settlement 148건
[기사 지급 집계] agency=케어플로우 서울대행사, engineer=홍길동, 2026년 6월, 건수=3, net=684000원
[기사 지급 집계] 생성 완료: 41건
[월별 정산] Job 완료 — 생성: 148건, 스킵: 3건, 오류: 1건, 플랫폼정산집계: 3건, 기사지급집계: 41건, 소요시간: 1520ms
```
