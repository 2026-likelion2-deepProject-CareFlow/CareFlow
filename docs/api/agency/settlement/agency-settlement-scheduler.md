# 월별 정산 자동 생성 스케줄러 명세

## 개요

| 항목 | 내용 |
|---|---|
| 트리거 방식 | Spring Quartz (Cron) |
| 실행 주기 | **매월 1일 새벽 1시** (`0 0 1 1 * ?`) |
| 대상 데이터 | 전월에 결제 완료(`PaymentStatus = SUCCESS`)된 A/S 건 중 정산 미생성 건 |
| 생성 상태 | `Settlement.status = PENDING` (이후 승인/지급은 별도 API) |
| 패키지 위치 | `com.careflow.agency.scheduler` |
| 참고 패턴 | `notification/scheduler/WarrantyAlertJob` + `notification/config/NotificationSchedulerConfig` |

---

## 처리 흐름

```
[매월 1일 01:00] Quartz Trigger 발동
        │
        ▼
SettlementGenerationJob.execute()
        │
        ├─ 1. 전월 범위 계산
        │       prevMonth = YearMonth.now().minusMonths(1)
        │       from = prevMonth.atDay(1).atStartOfDay()
        │       to   = YearMonth.now().atDay(1).atStartOfDay()
        │
        ├─ 2. 정산 생성 대상 조회 (PaymentRepository)
        │       Payment.status = SUCCESS
        │       AND payment.paidAt >= from AND < to
        │       AND 해당 payment_id로 Settlement가 미존재
        │       JOIN FETCH asRequest → asRequest.agency, asRequest.assignment(engineer)
        │
        ├─ 3. 건별 Settlement 생성 (SettlementGenerationService)
        │       ┌─ grossAmount   = payment.amount
        │       ├─ agencyFeeRate = agency.agencyFeeRate
        │       ├─ agencyFee     = round(gross × agencyFeeRate / 100)
        │       ├─ feeRate(CareFlow 플랫폼) = 고정 10% (상수)
        │       ├─ platformFee   = round(gross × feeRate / 100)
        │       └─ engineerNetAmount = gross - platformFee - agencyFee
        │
        ├─ 4. Settlement.create() 호출 → settlementRepository.save()
        │       status = PENDING (기본값)
        │
        └─ 5. 로그 출력 (생성 건수 / 스킵 건수 / 오류 건수)
```

---

## 수수료 계산 규칙

| 항목 | 계산 방식 | 비고 |
|---|---|---|
| `grossAmount` | `payment.amount` | 고객이 실제 결제한 금액 |
| `platformFee` | `round(gross × PLATFORM_FEE_RATE / 100)` | CareFlow 고정 10%, `BigDecimal` 반올림 |
| `platformFeeRate` | 상수 `10` (`BigDecimal`) | 설정값 변경 시 상수만 수정 |
| `agencyFeeRate` | `agency.agencyFeeRate` | 대행사별 상이, 생성 시점 스냅샷 저장 |
| `agencyFee` | `round(gross × agencyFeeRate / 100)` | |
| `engineerNetAmount` | `gross - platformFee - agencyFee` | 기사 실수령액 |

---

## 엔지니어 조회 전략

`Payment → AsRequest → AsAssignment(COMPLETED)` 경로로 담당 기사를 조회합니다.

- `as_assignments` 테이블에서 `request_id` 일치 + `status = 'COMPLETED'`인 배정을 조회
- 복수 배정 중 최신 1건(`assignedAt DESC`) 기준으로 기사 확정
- 배정이 없는 경우 → 해당 건 스킵 + 경고 로그 출력

---

## 중복 방지

- `settlements` 테이블에 `payment_id` 유니크 제약(`@OneToOne`)이 걸려 있으므로, 쿼리 레벨에서 `NOT EXISTS (SELECT 1 FROM settlements WHERE payment_id = p.id)` 조건으로 미생성 건만 조회
- 스케줄러 재실행(서버 재기동 등)에도 중복 INSERT가 발생하지 않도록 보장

---

## Quartz 설정

| 항목 | 값 |
|---|---|
| Job 클래스 | `SettlementGenerationJob` |
| Group | `settlement` |
| Trigger | `settlementGenerationTrigger` |
| Cron 표현식 | `0 0 1 1 * ?` (매월 1일 01:00:00) |
| Misfire 정책 | `withMisfireHandlingInstructionFireAndProceed` — 서버 재기동 시 누락 즉시 실행 |

---

## 구현 파일 목록

| 파일 | 패키지 | 역할 |
|---|---|---|
| `SettlementGenerationJob.java` | `com.careflow.agency.scheduler` | Quartz Job — 실행 진입점 |
| `SettlementSchedulerConfig.java` | `com.careflow.agency.scheduler` | JobDetail + Trigger Bean 등록 |
| `SettlementGenerationService.java` | `com.careflow.agency.scheduler` | 정산 생성 비즈니스 로직 (트랜잭션 단위 분리) |
| `PaymentRepository` 추가 쿼리 | `com.careflow.payment.repository` | 정산 미생성 결제 건 조회 |

---

## 상태 전이 (이번 스케줄러 범위)

```
Payment(SUCCESS) ──[스케줄러]──▶ Settlement(PENDING)
                                        │
                              [별도 API — 추후 구현]
                                        │
                              PENDING ──▶ PAID       ([DDL v11] APPROVED 경유 없이 직접 전이)
                              PENDING ──▶ DISPUTED
```

---

## 로그 예시

```
[월별 정산] 2024년 6월 정산 생성 시작 (대상 기간: 2024-06-01 ~ 2024-07-01)
[월별 정산] 대상 결제 건 조회 완료: 총 152건
[월별 정산] 정산 생성 완료: 148건 / 스킵(기사 없음): 3건 / 오류: 1건
[월별 정산] 소요 시간: 1234ms
```
