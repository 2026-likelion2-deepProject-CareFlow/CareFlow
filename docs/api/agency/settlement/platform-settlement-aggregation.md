# 대행사→플랫폼 정산 집계 배치 로직 명세

## 개요

| 항목 | 내용                                                                                                                            |
|---|-------------------------------------------------------------------------------------------------------------------------------|
| 목적 | `settlements`(건별 기사·대행사 정산)를 대행사·연·월 단위로 GROUP BY 집계하여 `platform_settlements`(대행사가 플랫폼에 납부할 월별 수수료 청구서) 레코드를 생성               |
| 트리거 방식 | 별도 스케줄러가 아니라, 기존 `SettlementGenerationService.generateForMonth()`(월별 정산 자동 생성 배치)에 **이어서** 같은 트랜잭션 흐름 안에서 실행                  |
| 실행 시점 | 매월 1일 01:00 (`SettlementSchedulerConfig`의 기존 Quartz 트리거 그대로 재사용, 신규 Trigger 없음)                                               |
| 대상 데이터 | 이번 배치 실행에서 새로 생성된 `Settlement`(PENDING) 전체                                                                                    |
| 생성 단위 | 대행사(`agency_id`) × 정산 대상 연(`settlement_year`) × 정산 대상 월(`settlement_month`) 당 1행 — `uk_platform_settlement_period` 유니크 제약과 동일 |
| 생성 상태 | `PlatformSettlement.status = PENDING`                                                                                         |
| 패키지 위치 | `com.careflow.agency.scheduler` (기존 `SettlementGenerationService`, `SettlementGenerationJob`과 동일 패키지)                         |
| 근거 스키마 | `sql/CareFlow_DDL_v14.sql` — `platform_settlements` 테이블(618행), `settlements.platform_settlement_id` FK(659행)                  |

---

## 설계 결정 — 집계 기준을 "정산 생성 시점의 대상 월(targetMonth)"으로 삼은 이유

`CareFlow_DDL_v14.sql`의 `platform_settlements.settlement_year` 컬럼 주석은 "집계 대상 settlements.paid_at 기준"이라고 되어 있으나, `settlements.paid_at`은 `Settlement.markPaid()`가 호출되는 시점(기사 지급 완료 시점, 관리자의 별도 승인 액션)에만 채워지는 컬럼으로, **정산 생성 시점과는 무관하게 임의로 늦게 채워질 수 있음.** 이 배치는 "이번 배치가 이번 달에 만든 Settlement를 이어서 즉시 집계"하는 요구사항이므로, `settlements.paid_at`이 아니라 **`SettlementGenerationService.generateForMonth(YearMonth targetMonth)`에 전달된 `targetMonth`(정산 대상 월 = 결제가 발생한 전월)** 를 `platform_settlements.settlement_year`/`settlement_month`로 그대로 사용한다.

- 이렇게 하면 `platform_settlements`는 "대행사가 지난달 발생시킨 정산 총액에 대해 플랫폼에 납부해야 할 수수료 청구서"라는 의미를 가지며, 개별 기사 지급 완료 여부(`markPaid()` 호출 여부)와 무관하게 매월 1일 즉시 생성된다.
- `DISPUTED` 상태(생성 직후에는 발생하지 않지만 이후 상태 변경으로 발생 가능)는 집계 대상에서 제외한다 — DDL 주석의 "DISPUTED 상태인 건은 NULL" 문구와 동일한 취지.

---

## 처리 흐름

```
[매월 1일 01:00] Quartz Trigger 발동 (기존과 동일)
        │
        ▼
SettlementGenerationJob.execute()
        │
        ▼
SettlementGenerationService.generateForMonth(targetMonth)
        │
        ├─ 1~5. 기존 로직 — 결제 건별 Settlement(PENDING) 생성
        │       (agency-settlement-scheduler.md 참고)
        │       → 이번 배치에서 생성된 Settlement 목록을 메모리에 보관
        │
        ├─ 6. [신규] 생성된 Settlement 목록을 agency_id 기준으로 그룹핑
        │
        ├─ 7. [신규] 그룹별로 집계
        │       ┌─ totalGrossAmount = Σ settlement.grossAmount
        │       ├─ totalPlatformFee = Σ settlement.platformFee
        │       └─ settlementCount  = 그룹 내 건수
        │
        ├─ 8. [신규] (agency, targetYear, targetMonth) 기준 기존 PlatformSettlement 존재 여부 확인
        │       ┌─ 없으면: PlatformSettlement.create(...) 신규 생성 (status=PENDING)
        │       ├─ 있고 PENDING이면: 기존 합계에 누적(accumulate) — 스케줄러 재실행(Misfire 복구) 대비
        │       └─ 있고 PAID/DISPUTED면: 누적하지 않고 경고 로그, 해당 Settlement들은 미할당(NULL) 상태로 남김
        │
        └─ 9. [신규] 그룹에 속한 각 Settlement.assignPlatformSettlement(platformSettlement) 호출
                → settlements.platform_settlement_id FK 채움
```

---

## 중복 방지 / 재실행 안전성

- 이번 배치 실행에서 새로 생성된 `Settlement`만을 대상으로 하므로(기존 `NOT EXISTS` 조건으로 결제 건 자체가 중복 처리되지 않음), 정상적인 매월 1회 실행에서는 항상 신규 `PlatformSettlement`가 생성된다.
- 서버 재기동 등으로 동일 대상 월에 대해 배치가 두 번 실행되는 극단적 상황(Misfire 복구 직후 재실행 등)에 대비해, `(agency_id, settlement_year, settlement_month)` 유니크 키로 기존 레코드를 먼저 조회하고, 존재하면 `accumulate()`로 합계에 더하는 방식으로 구현한다(중복 INSERT 시 유니크 제약 위반 방지).
- 단, 해당 기간의 `PlatformSettlement`가 이미 `PAID`/`DISPUTED`로 상태가 바뀐 뒤라면 합계를 임의로 변경하지 않고 경고 로그만 남긴다(재무 데이터 무결성 보호) — 이 경우 관련 Settlement는 `platform_settlement_id = NULL` 상태로 남아 수동 확인이 필요하다.

---

## 구현 파일 목록

| 파일 | 변경 내용 |
|---|---|
| `SettlementGenerationService.java` | `createSettlement()` 반환 타입 `boolean → Settlement`(nullable)로 변경해 생성 목록 수집, `generatePlatformSettlements()` 메서드 신규 추가, `PlatformSettlementRepository` 의존성 추가, `Result` 레코드에 `platformSettlementsCreated` 필드 추가 |
| `SettlementGenerationJob.java` | 완료 로그에 `platformSettlementsCreated` 건수 추가 출력 |
| `PlatformSettlement.java` | `accumulate(int grossAmount, int platformFee, int count)` 도메인 메서드 신규 추가 — 기존 합계에 누적(더티 체킹 UPDATE) |
| `PlatformSettlementRepository.java` | 변경 없음(기존 `findByAgency_IdAndSettlementYearAndSettlementMonth` 재사용) |

---

## 테스트 요구사항

- **JUnit 5 단위 테스트** (`SettlementGenerationServiceTest`): `SettlementRepository`, `PaymentRepository`, `AsAssignmentRepository`, `PlatformSettlementRepository`를 Mockito로 mocking하여, 동일 대행사 결제 건 2건 → `PlatformSettlement` 1건 집계(합계·건수 검증), 서로 다른 대행사 결제 건 → `PlatformSettlement` 각각 생성, 대상 결제 건 0건 → `PlatformSettlement` 생성 안 함, 기존 PENDING `PlatformSettlement` 존재 시 누적(accumulate) 검증을 반드시 포함할 것.
- **H2 DB 통합 테스트** (`SettlementGenerationServiceIntegrationTest`): `@SpringBootTest` + `@ActiveProfiles("local")`로 실제 DB 흐름을 검증. `AsRequest → Payment(SUCCESS) → AsAssignment(COMPLETED)` 픽스처를 구성해 `generateForMonth()`를 직접 호출하고, `settlements.platform_settlement_id`가 올바르게 채워지는지, `platform_settlements`의 집계 합계·건수가 실제 저장된 값과 일치하는지 DB 레벨에서 확인할 것.

---

## 로그 예시

```
[월별 정산] 2026년 6월 정산 생성 시작 (대상 기간: 2026-06-01T00:00 ~ 2026-07-01T00:00)
[월별 정산] 대상 결제 건 조회 완료: 총 152건
[월별 정산] 정산 생성 완료: 148건 / 스킵(기사 없음): 3건 / 오류: 1건
[플랫폼 정산 집계] 대행사 3곳 대상 집계 시작 — 대상 Settlement 148건
[플랫폼 정산 집계] agency=케어플로우 서울대행사, 2026년 6월, 건수=52, gross=15200000원, fee=1520000원
[플랫폼 정산 집계] 생성 완료: 3건
[월별 정산] Job 완료 — 생성: 148건, 스킵: 3건, 오류: 1건, 플랫폼정산집계: 3건, 소요시간: 1450ms
```
