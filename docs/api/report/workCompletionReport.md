# 🚀 API 생성 및 개발 요구사항 정의서 — 작업 완료 보고서 (Engineer 도메인)

## 1. 데이터 모델 (Schema)
- 메인 스키마 정의 파일 : `sql/CareFlow_DDL_v5.sql` (DB명세서 v19 기준)
- 위 파일에서 `work_reports`, `work_report_parts`, `repair_parts`(부품 마스터), `as_requests`, `as_assignments`, `appliances` 테이블 위주로 참조할 것
- 해당 테이블과 매핑되는 Entity 클래스가 없을 시 테이블 구조를 참조하여 직접 생성할 것
    - `WorkReport`(work_reports), `WorkReportPart`(work_report_parts), `RepairPart`(repair_parts) 직접 구현
    - `AsRequest`(as_requests), `AsAssignment`(as_assignments), `Appliance`(appliances) 는 타 도메인 팀원 Entity 를 재사용한다(객체 그래프 탐색 `asRequest.getAppliance()`).
- 핵심 제약 조건
    - `work_reports.request_id` **UNIQUE** : A/S 1건당 보고서 1건(1:1) 강제
    - `work_report_parts.applied_unit_price` : 보고서 작성 시점의 부품 단가 **스냅샷**(단가 변경 후에도 과거 보고서 금액 보존)
- 본 API 는 제품 건강 진단서(`health_certificates`) 갱신을 **트리거**하나, 진단서 점수·등급 산정의 상세 로직은 별도 정의서 **`healthCertificate.md`** 를 참조한다.

## 2. API 엔드포인트 명세
- 이미 URI 로 매핑된 API 가 존재할 시 아래의 요구사항대로 코드를 변경할 것

### [POST] /api/engineers/me/reports - WorkReportController.submitReport
- **설명**: 수리 기사가 본인에게 배정된 A/S 작업 완료 후 작업 완료 보고서를 제출한다. 제출 시 교체 부품 내역 영속화, 진단서 갱신, 상태 로그(AsStatusLog) 기록 및 SSE 완료 알림 이벤트 발행이 함께 이루어진다.
- **Request Body**:
    - `@Valid @RequestBody CreateWorkReportRequest request` 형태로 매개변수로서 요청 데이터 받음
    - 필수 필드 : `requestId`(Long), `diagnosisResult`(String, `DiagnosisResult` enum 값), `workDurationMin`(Integer, 0 이상), `finalAmount`(Integer, 0 이상)
    - 선택 필드 : `memo`(String), `imageUrls`(String, JSON), `parts`(List)
    - `parts[]` 각 항목 : `repairPartId`(Long, 필수), `quantity`(Integer, 1 이상 필수), `appliedUnitPrice`(Integer, 선택 — 미입력 시 `repair_parts.base_unit_price` 로 fallback)
- **Response (201 Created)**:
    - `ResponseEntity` body 에 "작업 완료 보고서가 제출되고, 제품 건강 진단서가 갱신되었습니다. (Report ID: {reportId})" 형식의 문자열과 201 응답 반환

### [GET] /api/engineers/me/reports/{reportId} - WorkReportController.getReportDetail
- **설명**: 작업 완료 보고서 상세 조회 (고객/기사 공용)
- **Response (200 OK)**: `WorkReportDetailResponse`

### [PATCH] /api/engineers/me/reports/{reportId}/approve - WorkReportController.approveReport
- **설명**: 작업 완료 보고서 고객 승인
- **Response (200 OK)**: "작업 보고서가 성공적으로 승인되었습니다. 결제 단계로 이동합니다." 형식의 문자열

## 3. 상세 처리 로직 (Pipeline)
1. **검증(Validation) 단계**
    - 인증 확인 : `@AuthenticationPrincipal CustomUserDetails` 에서 `engineerId` 추출
    - `CreateWorkReportRequest` validation 만족 확인 (**표준 스키마 강제화** — 필수 항목 누락 또는 값 범위 위반 시 제출 불가)
    - 대상 A/S 신청 존재 확인 : `requestId` 로 `AsRequest` 조회, 없으면 예외
    - **본인 배정 검증 (인가)** : `as_assignments` 에서 해당 `request_id` 배정 내역을 조회하여, `engineer_id` 가 요청자 본인과 일치하고 배정 상태가 `ACCEPTED` 또는 `COMPLETED` 인 건이 존재하는지 확인 — 없으면 거부(타 기사의 보고서 대리 작성 차단)
    - **중복 제출 방어** : `work_reports.request_id` 존재 여부 선검사 — 이미 제출된 보고서가 있으면 거부
    - (검증 순서 : **인가 검증 → 중복 검사 → 상태 전이** 순으로 수행하여, 권한 없는 요청자에게 보고서 존재 여부가 노출되지 않도록 한다)
2. **데이터 처리(Process) 단계**
    - **상태 전이 (FSM)** : `AsRequest.completeWork()` 호출 — A/S 상태가 **`IN_PROGRESS`(수리 진행 중)일 때만** `COMPLETED` 로 전환한다. 그 외 상태에서 호출되면 예외 처리한다.
        - 전체 상태 흐름 : `PENDING → AGENCY_RECEIVED → ASSIGNED → ACCEPTED → IN_PROGRESS → COMPLETED → PAID`
        - 선행 전이는 배정 수락(`acceptAssignment()`: ASSIGNED→ACCEPTED), 작업 시작(`startWork()`: ACCEPTED→IN_PROGRESS)을 통해 이루어지며, 각 전이는 직전 상태를 가드로 검증한다.
    - **보고서·부품 내역 영속화** : `work_reports` 및 `work_report_parts` 테이블에 **Cascade** 적용하여 동시 저장한다. 부품 단가는 `applied_unit_price` 에 스냅샷 저장(요청에 없으면 `repair_parts.base_unit_price` 사용).
    - **건강 진단서 갱신 트리거** : 교체된 부품들 중 `repair_parts.importance` 기준 가장 심각한(severity 최상위) 등급을 추출하여(부품 교체가 없으면 `null`), 대상 가전의 진단서 갱신 도메인 메서드(`HealthCertificate.calculateAndUpdateHealth()`)를 호출한다. **상세 산정 로직은 `healthCertificate.md` 참조.**
    - **(✨ 신규) 상태 변경 로그 기록** : as_status_logs 테이블에 from_status(IN_PROGRESS), to_status(COMPLETED), memo(작업 완료 안내) 기록.
3. **응답(Response) 단계**
    - 보고서 제출 및 진단서 갱신 성공 시 HTTP `201`, 생성된 `WorkReport` 의 `reportId` 값을 포함하여 반환
    - 실패 시 아래 공통 에러 포맷으로 응답

## 5. 예외 처리 (Error Handling) 및 제약 조건
- 모든 에러 발생 시 공통 포맷(`{ "success": false, "message": "에러 내용" }`)으로 응답할 것. (프로젝트 공통 `ErrorResponse` + `GlobalExceptionHandler` 사용)
- 보고서 저장 · 상태 전이 · 진단서 갱신을 **하나의 `@Transactional`** 안에서 수행하되, 어느 단계에서 DB 에러가 발생하더라도 **모든 DB 변경사항을 롤백**할 것 (진단서만 갱신되고 보고서는 누락되는 등의 부분 반영 방지)
- 주요 예외 매핑
    - 존재하지 않는 A/S 신청 / 존재하지 않는 부품 → `400` (IllegalArgumentException)
    - 본인 미배정 건 / `IN_PROGRESS` 가 아닌 상태에서 완료 시도 → `403` (IllegalStateException)
    - 동일 A/S 건 중복 제출 (`work_reports.request_id` UNIQUE 위반) → `409 Conflict` 권장
    - 표준 스키마 위반 (필수 항목 누락 / 값 범위 위반) → `400` (MethodArgumentNotValidException)

## 6. 개발 및 출력 요구사항
- 컨트롤러 · 서비스 · 리포지토리 · 엔티티 레이어를 명확히 분리하여 구현할 것 (package-by-feature 구조)
- 엔티티 컨벤션 준수 : `@Getter` + `@NoArgsConstructor(access = PROTECTED)` + `@Builder`, `@Setter`/`@Data` 금지, **모든 상태 변경은 도메인 메서드를 통해서만** 수행 (`completeWork()`, `acceptAssignment()`, `startWork()` 등)
- 작성된 API 에 대해 단위 테스트(JUnit5 + Mockito, DB 의존성 제거)와 통합 테스트(`@SpringBootTest` + H2 인메모리)를 함께 생성할 것
    - 상태 전이는 reflection 강제 주입이 아닌 **실제 도메인 메서드 호출**(`processAssignment → acceptAssignment → startWork`)로 `IN_PROGRESS` 까지 도달시킨 뒤 검증할 것
    - 본인 미배정 / 중복 제출 / 잘못된 상태 등 실패 케이스를 함께 검증할 것