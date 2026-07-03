# 🚀 API 생성 및 개발 요구사항 정의서 — 제품 건강 진단서 (Health Certificate)

## 1. 데이터 모델 (Schema)
- 메인 스키마 정의 파일 : `sql/CareFlow_DDL_v5.sql` (DB명세서 v19 기준)
- 위 파일에서 `health_certificates`, `repair_parts`(importance), `appliances`(purchase_date), `work_reports`(갱신 트리거 출처) 테이블 위주로 참조할 것
- 해당 테이블과 매핑되는 Entity 클래스가 없을 시 테이블 구조를 참조하여 직접 생성할 것
    - `HealthCertificate`(health_certificates) 직접 구현. `Appliance`(appliances) 는 타 도메인 Entity 재사용.
- 주요 컬럼 : `grade`(CHAR(1), A~E), `score`(0~100), `repair_count`(누적 수리 횟수), `critical_parts_replaced`(핵심 부품 교체 횟수), `last_repaired_at`, `is_certified`(인증 뱃지)
- 핵심 제약 조건
    - `health_certificates.appliance_id` **UNIQUE** : 가전 1대당 진단서 1건(1:1) 강제
- **점수 산정 로직(4축)은 공통 정책 클래스 `HealthScoreCalculator`(`report.domain.policy`)에 단일화**한다. 저장(엔티티 `recalculate`)과 조회 역산(`ApplianceService`)이 **같은 계산기만 바라보도록** 하여 산정 일관성(DRY)을 보장한다.

## 2. API 엔드포인트 명세
본 도메인은 ① 보고서 제출에 의한 **자동 갱신(내부 트리거)** 과 ② 고객/기사용 **조회(REST)** 두 흐름으로 구성된다.

### (A) [내부 트리거] 진단서 자동 갱신 — `WorkReportService.syncHealthCertificate()` → `HealthCertificate.recalculate()`
- **설명**: 수리 기사가 작업 완료 보고서를 제출(`POST /api/engineer/work-reports`)하거나 제출을 취소(`DELETE .../approval-request`)하면, **동일 트랜잭션 내**에서 서비스 헬퍼 `syncHealthCertificate(appliance)` 가 실행되어 대상 가전의 진단서를 자동 갱신한다. 별도 REST 엔드포인트가 아닌 도메인 규칙으로 동작한다. (보고서 흐름은 `workCompletionReport.md` 참조)
- **입력 (✨변경)**: 매 호출 시 해당 가전의 **모든 보고서(`work_reports`)를 다시 집계**하여 절대값으로 계산한다 — `totalRepairCount`(= 보고서 수), `totalCriticalParts`, 전 보고서 통틀어 최고 중요도(`worstImportance`, 없으면 `null`), 가장 최근 보고서의 `submittedAt`(`lastRepaired`), 가전 `purchase_date`, 인증 기준(`minGrade`/`minScore`).
- **처리**: 대상 가전 진단서가 없으면 신규 생성(INSERT) 후, 있으면 조회하여 `recalculate(...)` 로 갱신(UPDATE, JPA Dirty Checking). 4축 점수 계산은 공통 정책 클래스 `HealthScoreCalculator` 에 위임한다.

### (B) [GET] /api/appliances/{applianceId}/health-certificate - ApplianceController.getHealthCertificate
- **설명**: 고객 또는 기사가 대상 가전의 건강 진단서를 조회한다. (JWT 인증 필요, BOLA 방어 적용). 가전 도메인 컨트롤러(`/api/appliances`)에 속하므로 **고객·기사 공용 RESTful 경로를 유지**한다(URI 변경 없음).
- **Response (200 OK)**: `HealthCertificateResponse`
    - **기본** : `certId`, `applianceId`, `grade`, `score`, `isCertified`, `issuedAt`, `updatedAt`
    - **4축 세부 점수** (프론트 레이더 차트용, 조회 시 동적 역산) : `repairCountScore`, `usagePeriodScore`, `partImportanceScore`, `lastRepairedScore`
    - **✨ 4축 조건 라벨** (프론트가 그대로 렌더링하는 텍스트) : `repairCountCondition`("2회"), `usagePeriodCondition`("1년 이상 ~ 3년 미만"), `partImportanceCondition`("MINOR 부품 교체"), `lastRepairedCondition`("6개월 이내")

## 3. 상세 처리 로직 (Pipeline)

### (A) 자동 갱신 로직 — `syncHealthCertificate(appliance)` → `recalculate(totalRepairCount, totalCriticalParts, worstImportance, lastRepaired, purchaseDate, minGrade, minScore)`
1. **진단서 조회/생성** : `appliance_id` 로 진단서 조회. 없으면 신규 생성(초기값 `grade='E'`, `score=0`).
2. **절대값 재집계 (✨변경 — `+=` 누적 방식 폐기)** : 해당 가전의 **모든 보고서를 다시 조회**하여 `repair_count = 전체 보고서 수`, `critical_parts_replaced = 전 보고서 CRITICAL 부품 수`, `worstImportance = 전 보고서 최고 중요도` 로 **절대값 재계산**한다. (과거엔 매 제출마다 `+= 1` 누적하여 취소·재계산 시 정합성이 깨지는 버그가 있었음 → 원천 데이터에서 항상 재산정하도록 수정)
3. **4축 점수 산정 (각 25점 만점, 총 100점)**

   | 항목 | 조건 | 점수 |
       |---|---|---|
   | ① 수리 횟수 | 0회 / 1회 / 2회 / 3회 / 4회 이상 | 25 / 20 / 15 / 8 / 0 |
   | ② 사용 기간 (`appliances.purchase_date` 기준, null이면 만점) | 1년 미만 / 1~3년 / 3~5년 / 5~8년 / 8년 이상 | 25 / 20 / 15 / 8 / 0 |
   | ③ 교체 부품 중요도 (`repair_parts.importance` 기준) | 없음 / MINOR / NORMAL / MAJOR / CRITICAL | 25 / 20 / 15 / 8 / 0 |
   | ④ 최근 수리 주기 (직전 `last_repaired_at` 기준, null이면 첫 수리 만점) | 없음 / 2년 이전 / 1~2년 / 6개월~1년 / 6개월 이내 | 25 / 20 / 15 / 8 / 0 |

4. **총점 = ① + ② + ③ + ④** → **등급 산정**

   | 등급 | 점수 범위 | 의미 |
       |---|---|---|
   | A | 90점 이상 | 최상 상태 |
   | B | 75점 ~ 89점 | 양호 |
   | C | 60점 ~ 74점 | 보통 |
   | D | 40점 ~ 59점 | 주의 필요 |
   | E | 39점 이하 | 불량 |

5. **인증 뱃지 (✨변경 — 관리자 기준값 주입)** : `score >= minScore && grade <= minGrade`(알파벳 비교) → `is_certified = true`. `minGrade`/`minScore` 는 Redis(`admin:badge:criteria`)의 관리자 설정에서 읽어오며, 값이 없으면 **기본값 `minGrade="B"`, `minScore=75`**(= 기존 `score>=75 && grade∈{A,B}` 와 동일).
6. **최근 수리 일시 갱신 (✨변경)** : `last_repaired_at = 가장 최근 보고서의 submittedAt`. (과거엔 `now()` 로 갱신했으나, 원천 데이터 재집계 방식으로 바뀌며 보고서 시각을 그대로 사용) ④ 축은 이 값과 현재 시각(`now`)의 간격으로 산정.

### (B) 조회 로직 — getHealthCertificate
1. **검증** : `@AuthenticationPrincipal` 인증 확인. `role`이 `CUSTOMER`일 경우, `applianceId`의 소유자가 요청 고객 본인인지 확인.
2. **처리** : `applianceId`로 `HealthCertificate`를 조회. 미발급(진단서 없음) 시 예외 처리. 해당 가전의 과거 `WorkReport`를 모두 조회하여 4축 점수를 역산하고, **각 축의 조건 라벨(Condition)도 함께 산출**한다.
    - 조건 라벨 산출은 서비스의 헬퍼 메서드(`getRepairCountCondition`, `getUsagePeriodCondition`, `getPartImportanceCondition`, `getLastRepairedCondition`)로 처리하며, 점수 구간과 동일한 경계(예: 사용기간 1/3/5/8년, 최근수리 6개월/1년/2년)를 사용한다.
3. **응답** : 등급, 총점, 4축별 점수 + 4축 조건 라벨, 인증 여부 등을 `HealthCertificateResponse`에 담아 반환.

## 5. 예외 처리 (Error Handling) 및 제약 조건
- 모든 에러 발생 시 공통 포맷(`{ "success": false, "message": "에러 내용" }`)으로 응답할 것.
- **(A) 자동 갱신은 보고서 제출 트랜잭션에 종속** — 진단서 갱신 단계에서 에러가 발생하면 보고서 저장·상태 전이까지 함께 롤백된다(부분 반영 방지).
- `appliance_id` UNIQUE 제약으로 진단서 중복 생성을 방지한다(동시 요청 시 DB 제약이 최종 방어선).
- 조건 라벨 산출 시 `purchase_date`·`last_repaired_at` 이 null 일 수 있으므로 안전 분기("알 수 없음"/"수리 이력 없음")로 처리한다.
- (B) 조회 시 진단서 미발급 가전 → `404` (NoSuchElementException) / 타인 가전 조회 시도 → `403` (IllegalAccessException)

## 6. 개발 및 출력 요구사항
- 진단서 상태 갱신은 엔티티 도메인 메서드 **`HealthCertificate.recalculate(...)`** 로 수행하되, 4축 점수 계산 자체는 공통 정책 **`HealthScoreCalculator`** 에 위임할 것. 원천 데이터(전체 보고서) 재집계는 서비스 헬퍼 **`WorkReportService.syncHealthCertificate()`** 가 담당한다.
- 조회용 조건 라벨(Condition) 변환은 표시 전용이므로 조회 서비스(`ApplianceService`)의 private 헬퍼로 두되, 점수 산정과 동일한 경계값을 사용해 일관성을 유지할 것
- 엔티티 컨벤션 준수 : `@Getter` + `@NoArgsConstructor(access = PROTECTED)` + `@Builder`, `@Setter` 금지, 상태 변경은 도메인 메서드로만 수행
- 작성된 로직에 대해 단위 테스트(JUnit5 + Mockito)와 통합 테스트(`@SpringBootTest` + H2 인메모리)를 함께 생성할 것
    - 부품 중요도별(CRITICAL/MAJOR/NORMAL/MINOR) 등급·점수 분기는 `@ParameterizedTest` 로 검증
    - 통합 테스트에서 실제 DB 에 INSERT 후 SELECT 하여 등급·점수·인증 여부가 정합성 있게 반영되는지 직접 확인
    - **(라벨 확장 반영)** 조회 응답의 4축 조건 라벨(`usagePeriodCondition` 등)이 점수 구간과 일치하는지 단언을 추가할 것