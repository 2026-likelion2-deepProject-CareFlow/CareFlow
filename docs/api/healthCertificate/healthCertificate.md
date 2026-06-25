# 🚀 API 생성 및 개발 요구사항 정의서 — 제품 건강 진단서 (Health Certificate)

## 1. 데이터 모델 (Schema)
- 메인 스키마 정의 파일 : `sql/CareFlow_DDL_v5.sql` (DB명세서 v19 기준)
- 위 파일에서 `health_certificates`, `repair_parts`(importance), `appliances`(purchase_date), `work_reports`(갱신 트리거 출처) 테이블 위주로 참조할 것
- 해당 테이블과 매핑되는 Entity 클래스가 없을 시 테이블 구조를 참조하여 직접 생성할 것
    - `HealthCertificate`(health_certificates) 직접 구현. `Appliance`(appliances) 는 타 도메인 Entity 재사용.
- 주요 컬럼 : `grade`(CHAR(1), A~E), `score`(0~100), `repair_count`(누적 수리 횟수), `critical_parts_replaced`(핵심 부품 교체 횟수), `last_repaired_at`, `is_certified`(인증 뱃지)
- 핵심 제약 조건
    - `health_certificates.appliance_id` **UNIQUE** : 가전 1대당 진단서 1건(1:1) 강제
- **점수 산정 로직(4축)은 `HealthCertificate` 엔티티 내부에 캡슐화**할 것 (서비스 레이어에 분산 금지 — 산정 일관성 보장)

## 2. API 엔드포인트 명세
본 도메인은 ① 보고서 제출에 의한 **자동 갱신(내부 트리거)** 과 ② 고객용 **조회(REST)** 두 흐름으로 구성된다.

### (A) [내부 트리거] 진단서 자동 갱신 — `HealthCertificate.calculateAndUpdateHealth()`
- **설명**: 수리 기사가 작업 완료 보고서를 제출하면(`POST /api/engineers/me/reports`), **동일 트랜잭션 내**에서 대상 가전의 진단서를 자동 갱신한다. 별도 REST 엔드포인트가 아닌 도메인 규칙으로 동작한다. (보고서 제출 흐름은 `workCompletionReport.md` 참조)
- **입력**: 교체 부품 중 최고 중요도(`PartImportance`, 교체 없으면 `null`), 가전 `purchase_date`
- **처리**: 대상 가전 진단서가 없으면 신규 생성(INSERT), 있으면 조회 후 갱신(UPDATE, JPA Dirty Checking)

### (B) [GET] /api/appliances/{applianceId}/health-certificate - (구현 예정, 요구사항 C-24 고객용)
- **설명**: 고객이 본인 가전의 건강 진단서(등급·점수·항목별 점수·인증 여부)를 조회·다운로드한다. (JWT 인증 필요)
- **Response (200 OK)**: `cert_id`, `grade`, `score`, `repair_count`, `critical_parts_replaced`, `last_repaired_at`, `is_certified`, `issued_at` 등을 JSON 으로 반환 (정적 팩토리 `from()` 기반 Response DTO 권장)

## 3. 상세 처리 로직 (Pipeline)

### (A) 자동 갱신 로직 — `calculateAndUpdateHealth(maxImportance, purchaseDate)`
1. **진단서 조회/생성** : `appliance_id` 로 진단서 조회. 없으면 신규 생성(초기값 `grade='E'`, `score=0`).
2. **누적 상태 갱신** : `repair_count += 1`, 교체 부품 최고 중요도가 `CRITICAL` 이면 `critical_parts_replaced += 1`. (수리 횟수 점수는 증가 **후** 값을 기준으로 산정)
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

5. **인증 뱃지** : `score >= 75 && grade ∈ {A, B}` → `is_certified = true`
6. **최근 수리 일시 갱신** : `last_repaired_at = now()` (④ 점수 산정이 끝난 **이후**에 갱신하여, 이번 수리와 직전 수리 사이의 간격이 반영되도록 함)

### (B) 조회 로직 (구현 예정)
1. **검증** : `@AuthenticationPrincipal` 인증 확인, `applianceId` 의 소유자가 요청 고객 본인인지 확인
2. **처리** : `appliance_id` 로 진단서 조회, 미발급(진단서 없음) 시 별도 응답 처리
3. **응답** : 등급·점수·항목별 정보·인증 여부 반환

## 5. 예외 처리 (Error Handling) 및 제약 조건
- 모든 에러 발생 시 공통 포맷(`{ "success": false, "message": "에러 내용" }`)으로 응답할 것.
- **(A) 자동 갱신은 보고서 제출 트랜잭션에 종속** — 진단서 갱신 단계에서 에러가 발생하면 보고서 저장·상태 전이까지 함께 롤백된다(부분 반영 방지).
- `appliance_id` UNIQUE 제약으로 진단서 중복 생성을 방지한다(동시 요청 시 DB 제약이 최종 방어선).
- (B) 조회 시 진단서 미발급 가전 → `404` 또는 "미발급" 안내 응답 / 타인 가전 조회 시도 → `403`

## 6. 개발 및 출력 요구사항
- 진단서 점수 산정(4축) 로직은 서비스가 아닌 **`HealthCertificate` 엔티티 도메인 메서드**(`calculateAndUpdateHealth()`)로 캡슐화할 것
- 엔티티 컨벤션 준수 : `@Getter` + `@NoArgsConstructor(access = PROTECTED)` + `@Builder`, `@Setter` 금지, 상태 변경은 도메인 메서드로만 수행
- 작성된 로직에 대해 단위 테스트(JUnit5 + Mockito)와 통합 테스트(`@SpringBootTest` + H2 인메모리)를 함께 생성할 것
    - 부품 중요도별(CRITICAL/MAJOR/NORMAL/MINOR) 등급·점수 분기는 `@ParameterizedTest` 로 검증
    - 통합 테스트에서 실제 DB 에 INSERT 후 SELECT 하여 등급·점수·인증 여부가 정합성 있게 반영되는지 직접 확인
