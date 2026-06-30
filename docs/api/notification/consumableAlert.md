# 🚀 API 생성 및 개발 요구사항 정의서 — 소모품 교체 주기 알림 (Quartz)

> 가이드 Phase 2 — 소모품·무상기간 알림(Quartz)에 해당. 매일 정해진 시각에 배치가 실행되어 교체 주기가 도래한 소모품에 대해 고객에게 알림을 발송하고, 다음 알림일을 자동 연장한다. 별도 REST 엔드포인트가 아닌 **스케줄러(Quartz) 기반 내부 배치**로 동작한다.

## 1. 데이터 모델 (Schema)
- 메인 스키마 정의 파일 : `sql/CareFlow_DDL_v5.sql` (DB명세서 v19 기준)
- 위 파일에서 `consumable_alerts`, `appliances`, `users`, `notifications` 테이블 위주로 참조할 것
- 해당 테이블과 매핑되는 Entity 클래스가 없을 시 테이블 구조를 참조하여 직접 생성할 것
    - `ConsumableAlert`(consumable_alerts) 직접 구현. `Appliance`(appliances) · `User`(users) 는 타 도메인 Entity 재사용.
- 핵심 컬럼 (`consumable_alerts`)
    - `alert_id`(PK), `appliance_id`(FK, NOT NULL), `user_id`(FK, NOT NULL), `consumable_name`(VARCHAR(100) — 필터·배수 호스 등), `cycle_months`(교체 주기, 개월), `last_changed_at`(최근 교체일, 최초엔 null 가능), `next_alert_date`(다음 알림 예정일, NOT NULL), `is_active`(알림 ON/OFF, 기본 1), `created_at`
- 핵심 제약 / 인덱스
    - `idx_consumable_alert_date (next_alert_date, is_active)` : **Quartz 배치가 매일 대상 소모품을 풀스캔 없이 찾기 위한 핵심 인덱스**
    - `idx_consumable_user (user_id)` : 고객 마이페이지 소모품 목록 조회용
- **도메인 규칙 캡슐화** : 교체 주기 리셋 로직(`updateAfterReplacement()`)을 `ConsumableAlert` 엔티티 내부에 캡슐화한다(서비스 분산 금지). `last_changed_at = now()`, `next_alert_date = last_changed_at + cycle_months`.

## 2. 스케줄러 / 트리거 명세
본 기능은 REST 엔드포인트가 아니라 **Quartz Job + Trigger** 로 동작한다.

### [Quartz] ConsumableAlertJob — 소모품 교체 주기 도래 알림
- **등록 위치**: `NotificationSchedulerConfig` (`@Configuration`), 그룹 `"notification"`
- **JobDetail**: `consumableAlertDetail` — `JobBuilder.newJob(ConsumableAlertJob.class).withIdentity("consumableAlertJob", "notification").storeDurably()`
    - `storeDurably()` : 트리거가 없어도 Job 정의를 유지
- **Trigger**: `consumableAlertTrigger` — Cron `0 0 9 * * ?` (**매일 오전 9시 정각**)
    - **Misfire 정책** : `withMisfireHandlingInstructionFireAndProceed()` — 서버 점검 등으로 9시에 꺼져 있었다면 **켜지자마자 즉시 1회 실행**하여 누락 방지
- **입력**: 없음(배치 자체 트리거). 기준일 `today = LocalDate.now()`
- **출력/부수효과**: 발송 대상별 `notifications` 적재 + SSE 실시간 전송, 발송 후 `next_alert_date` 자동 연장(UPDATE)

## 3. 상세 처리 로직 (Pipeline) — `ConsumableAlertJob.execute()`
1. **대상 조회** : `consumableAlertRepository.findAlertsToNotify(today)`
    - JPQL : `SELECT c FROM ConsumableAlert c JOIN FETCH c.user JOIN FETCH c.appliance WHERE c.nextAlertDate <= :targetDate AND c.isActive = true`
    - **`<=` 연산자** : 배치가 하루 이상 누락되어도 밀린 대상까지 함께 발송(누락 방지)
    - **`JOIN FETCH`** : 알림 발송에 필요한 `user`(수신자)·`appliance`(제품명)를 단일 쿼리로 로딩하여 **N+1 방지**
2. **대상 없음** : 빈 리스트면 로그만 남기고 정상 종료
3. **발송 + 주기 리셋** : 각 대상에 대해
    - 알림 발송 : `notificationService.send(alert.getUser(), "CONSUMABLE", title, body)` — 제목 "소모품 교체 시기 안내", 본문에 `appliance.modelName` · `consumableName` 포함
    - **다음 알림일 연장** : `alert.updateAfterReplacement()` 호출 → `last_changed_at = now()`, `next_alert_date += cycle_months`
    - ⚠ 이 연장이 없으면 다음 날도 `<= today` 조건에 걸려 **매일 중복 발송(알림 폭탄)** 이 발생하므로 필수
4. **종료** : 처리 건수 로그. 예외 발생 시 `JobExecutionException` 으로 래핑하여 throw

## 5. 예외 처리 (Error Handling) 및 제약 조건
- `execute()` 전체를 `try/catch` 로 감싸고, 오류 시 로그 기록 후 `JobExecutionException` 으로 던져 Quartz 가 인지하도록 한다(개별 실패가 스케줄러 전체를 죽이지 않도록 한다).
- **트랜잭션** : `@Transactional`(쓰기) — 발송 후 `next_alert_date` 를 Dirty Checking 으로 UPDATE 해야 하므로 읽기 전용이 아니다. 동일 트랜잭션 내에서 조회→발송→주기 갱신이 원자적으로 처리된다.
- SSE 전송 실패(끊긴 연결)는 `NotificationService` 내부에서 best-effort 로 처리되어 배치 전체를 실패시키지 않는다.
- `is_active = false` 인 알림은 발송 대상에서 제외된다.

## 6. 개발 및 출력 요구사항
- Job(`ConsumableAlertJob`) · Config(`NotificationSchedulerConfig`) · 엔티티(`ConsumableAlert`) · 리포지토리(`ConsumableAlertRepository`) 레이어를 분리하여 구현할 것 (package-by-feature)
- 엔티티 컨벤션 준수 : `@Getter` + `@NoArgsConstructor(access = PROTECTED)` + `@Builder`, `@Setter`/`@Data` 금지, 상태 변경은 도메인 메서드(`updateAfterReplacement`, `toggleActive`)로만 수행
- 배치 조회 쿼리는 `JOIN FETCH` 로 N+1 을 방지하고, `next_alert_date` 인덱스를 활용하도록 유지할 것
- 알림 발송은 기존 `NotificationService.send()` 를 재사용한다(알림 로그 적재 + SSE 푸시 일관성 유지, `channel = SSE`, `type = "CONSUMABLE"`)
- 작성된 로직에 대해 단위 테스트(JUnit5 + Mockito)와 통합 테스트(`@SpringBootTest` + H2 인메모리)를 함께 생성할 것
    - 단위(`ConsumableAlertJobTest`) : 발송 대상 존재 시 `notificationService.send` 호출 및 `updateAfterReplacement` 로 주기가 연장되는지 검증
    - 통합(`AlertRepositoryIntegrationTest`) : `findAlertsToNotify` 경계값(당일/과거/미래/비활성)을 실제 DB INSERT 후 검증 — 당일·과거는 포함, 미래·비활성은 제외
