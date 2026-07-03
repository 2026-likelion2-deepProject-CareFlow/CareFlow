# 🚀 API 생성 및 개발 요구사항 정의서 — 무상 A/S 기간 만료 임박 알림 (Quartz)

> 가이드 Phase 2 — 소모품·무상기간 알림(Quartz)에 해당. 매일 정해진 시각에 배치가 실행되어, 무상 A/S 만료일이 30일 앞으로 다가온 가전의 보유 고객에게 사전 알림을 발송한다. 별도 REST 엔드포인트가 아닌 **스케줄러(Quartz) 기반 내부 배치**로 동작한다.

## 1. 데이터 모델 (Schema)
- 메인 스키마 정의 파일 : `sql/CareFlow_DDL_v5.sql` (DB명세서 v19 기준)
- 위 파일에서 `appliances`, `users`, `notifications` 테이블 위주로 참조할 것
- **신규 테이블 없음** : 본 기능은 `appliances` 의 기존 컬럼을 재사용한다(`Appliance` 엔티티 재사용, 알림 로그는 `notifications`).
- 핵심 컬럼 (`appliances`)
    - `warranty_end_date`(DATE, nullable) — **무상 A/S 만료일 (Quartz 알림 기준)**. `Appliance.warrantyEndDate` 로 매핑.
    - `status` — `SOLD`(판매됨) 등은 발송 대상에서 제외
    - `deleted_at` — soft-delete 된 가전은 제외
- 핵심 인덱스
    - `idx_appliances_warranty (warranty_end_date, status)` : **Quartz 배치가 매일 만료 임박 가전을 풀스캔 없이 찾기 위한 인덱스**

## 2. 스케줄러 / 트리거 명세
본 기능은 REST 엔드포인트가 아니라 **Quartz Job + Trigger** 로 동작한다.

### [Quartz] WarrantyAlertJob — 무상 A/S 만료 임박 알림
- **등록 위치**: `NotificationSchedulerConfig` (`@Configuration`), 그룹 `"notification"`
- **JobDetail**: `warrantyAlertDetail` — `JobBuilder.newJob(WarrantyAlertJob.class).withIdentity("warrantyAlertJob", "notification").storeDurably()`
- **Trigger**: `warrantyAlertTrigger` — Cron `0 0 9 * * ?` (**매일 오전 9시 정각**)
    - **Misfire 정책** : `withMisfireHandlingInstructionFireAndProceed()` — 9시에 서버가 꺼져 있었다면 켜지자마자 즉시 1회 실행
- **입력**: 없음(배치 자체 트리거). 기준일 `targetDate = LocalDate.now().plusDays(30)` (만료 **30일 전**)
- **출력/부수효과**: 발송 대상별 `notifications` 적재 + SSE 실시간 전송

## 3. 상세 처리 로직 (Pipeline) — `WarrantyAlertJob.execute()`
1. **기준일 산정** : `targetDate = 오늘 + 30일`
2. **대상 조회** : `applianceRepository.findByWarrantyEndDateWithUser(targetDate)`
    - JPQL : `SELECT a FROM Appliance a JOIN FETCH a.user WHERE a.warrantyEndDate = :targetDate AND a.deletedAt IS NULL AND a.status != 'SOLD'`
    - **`JOIN FETCH a.user`** : 수신자 정보를 단일 쿼리로 로딩하여 **N+1 방지**
    - 삭제(`deletedAt`)·판매(`SOLD`) 가전은 제외
3. **대상 없음** : 빈 리스트면 로그만 남기고 정상 종료
4. **발송** : 각 가전에 대해 `notificationService.send(appliance.getUser(), "WARRANTY", title, body)`
    - 제목 "무상 A/S 기간 만료 임박 안내", 본문에 `brand` · `modelName` · 만료일(`targetDate`) 포함
5. **종료** : 처리 건수 로그. 예외 발생 시 `JobExecutionException` 으로 래핑하여 throw

## 5. 예외 처리 (Error Handling) 및 제약 조건
- `execute()` 전체를 `try/catch` 로 감싸고, 오류 시 로그 기록 후 `JobExecutionException` 으로 던진다(개별 실패가 스케줄러 전체를 죽이지 않도록 한다).
- **트랜잭션** : `@Transactional`(쓰기) — 배치 자체는 `appliances` 를 조회만 하지만, `NotificationService.send()` 가 **자체 트랜잭션을 열지 않아** 알림(`notifications`) INSERT 가 본 잡의 트랜잭션 안에서 일어난다. 따라서 `readOnly = true` 로 두면 안 되며 일반 `@Transactional` 을 사용한다. (구: `readOnly = true` 로 표기되어 있었으나 실제 코드는 쓰기 트랜잭션)
- SSE 전송 실패(끊긴 연결)는 `NotificationService` 내부에서 best-effort 로 처리되어 배치 전체를 실패시키지 않는다.
- ⚠ **(설계 주의)** 대상 조회가 `warrantyEndDate = targetDate`(정확히 30일 전 당일)인 **등치 조건**이라, 배치가 **여러 날 연속 누락**되면 그 사이 30일-경계에 걸린 코호트는 건너뛸 수 있다(소모품 알림의 `<=` 캐치업과 다른 점). 동일일 서버 다운은 Misfire(`FireAndProceed`)로 보완되나, 장기 누락 대비가 필요하면 `BETWEEN`(예: 29~30일) 또는 발송 이력 컬럼 추가를 검토할 것.

## 6. 개발 및 출력 요구사항
- Job(`WarrantyAlertJob`) · Config(`NotificationSchedulerConfig`) · 리포지토리(`ApplianceRepository.findByWarrantyEndDateWithUser`) 를 분리하여 구현할 것 (package-by-feature)
- 배치 조회 쿼리는 `JOIN FETCH` 로 N+1 을 방지하고, `warranty_end_date` 인덱스를 활용하도록 유지할 것
- 알림 발송은 기존 `NotificationService.send()` 를 재사용한다(알림 로그 적재 + SSE 푸시 일관성 유지, `channel = SSE`, `type = "WARRANTY"`)
- 작성된 로직에 대해 단위 테스트(JUnit5 + Mockito)를 작성할 것
    - 단위(`WarrantyAlertJobTest`) : 만료 30일 전 대상 존재 시 `notificationService.send` 가 대상 수만큼 호출되는지, 대상 없음 시 미발송인지 검증
    - (권장) 통합 테스트에서 `warranty_end_date = today+30` 가전과 그 외(만료 무관·SOLD·삭제) 가전을 INSERT 하여 정확히 대상만 조회되는지 검증
