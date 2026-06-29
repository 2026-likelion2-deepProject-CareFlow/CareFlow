# 🚀 API 생성 및 개발 요구사항 정의서 — 작업 완료 보고서 (Engineer 도메인)

## 1. 데이터 모델 (Schema)
- 메인 스키마 정의 파일 : `sql/CareFlow_DDL_v5.sql` (DB명세서 v19 기준)
- 위 파일에서 `work_reports`, `work_report_parts`, `repair_parts`(부품 마스터), `as_requests`, `as_assignments`, `as_status_logs`, `appliances`, `users` 테이블 위주로 참조할 것
- 해당 테이블과 매핑되는 Entity 클래스가 없을 시 테이블 구조를 참조하여 직접 생성할 것
    - `WorkReport`(work_reports), `WorkReportPart`(work_report_parts), `RepairPart`(repair_parts) 직접 구현
    - `AsRequest`(as_requests), `AsAssignment`(as_assignments), `AsStatusLog`(as_status_logs), `Appliance`(appliances), `User`(users) 는 타 도메인 Entity 를 재사용한다(객체 그래프 탐색 `asRequest.getAppliance()`, `asRequest.getCustomer()`).
- 핵심 제약 조건
    - `work_reports.request_id` **UNIQUE** : A/S 1건당 보고서 1건(1:1) 강제
    - `work_report_parts.applied_unit_price` : 보고서 작성 시점의 부품 단가 **스냅샷**(단가 변경 후에도 과거 보고서 금액 보존)
- 본 API 는 제품 건강 진단서(`health_certificates`) 갱신을 **트리거**하나, 진단서 점수·등급 산정의 상세 로직은 별도 정의서 **`healthCertificate.md`** 를 참조한다.

## 2. API 엔드포인트 명세
- 공통 : JWT 인증 필요, `@AuthenticationPrincipal CustomUserDetails` 에서 `userId`·`role` 추출
- ⚠ **(변경) URI 단수형 통일** : 프론트엔드 `axios` 규격(`/api/engineer/work-reports`)에 맞춰 클래스 매핑을 변경한다. (구 `/api/engineers/me/reports`)

### [POST] /api/engineer/work-reports - WorkReportController.submitReport
- **설명**: 수리 기사가 본인에게 배정된 A/S 작업 완료 후 작업 완료 보고서를 제출한다(`role=ENGINEER`). 제출 시 교체 부품 내역 영속화, 진단서 갱신, 상태 로그(AsStatusLog) 기록, 고객·대행사 SSE 완료 알림 발송이 함께 이루어진다.
- **Request Body**: `@Valid @RequestBody CreateWorkReportRequest request`
    - 필수 필드 : `requestId`(Long), `diagnosisResult`(String, `DiagnosisResult` enum 값), `workDurationMin`(Integer, 0 이상), `finalAmount`(Integer, 0 이상)
    - 선택 필드 : `memo`(String), `imageUrls`(String, JSON), `parts`(List)
    - `parts[]` 각 항목 : `repairPartId`(Long, 필수), `quantity`(Integer, 1 이상 필수), `appliedUnitPrice`(Integer, 선택 — 미입력 시 `repair_parts.base_unit_price` 로 fallback)
- **Response (201 Created)**: `ResponseEntity<String>` — "작업 완료 보고서가 제출되고, 제품 건강 진단서가 갱신되었습니다. (Report ID: {reportId})" 형식 문자열

### [GET] /api/engineer/work-reports/{reportId} - WorkReportController.getReportDetail
- **설명**: 작업 완료 보고서 상세 조회 (고객/기사 공용). 서비스 시그니처는 `getWorkReportDetail(userId, role, reportId)`.
- **Response (200 OK)**: `WorkReportDetailResponse`

### [PATCH] /api/engineer/work-reports/{reportId}/approve - WorkReportController.approveReport
- **설명**: 작업 완료 보고서 고객 승인 (`customer_approved` 전환 → 결제 단계 진입). **호출 주체는 고객(CUSTOMER)** 이나 보고서 도메인 경로 하위에 위치한다(경로 재배치는 팀 협의 사항).
- **Response (200 OK)**: "작업 보고서가 성공적으로 승인되었습니다. 결제 단계로 이동합니다." 형식 문자열

### 📦 WorkReportDetailResponse 필드 명세 (✨ 프론트 연동을 위해 확장됨)
정적 팩토리는 `WorkReportDetailResponse.of(report, statusTimeMap)` 를 사용한다(구 `from()` 대체). `statusTimeMap` 은 해당 `requestId` 의 `as_status_logs` 를 `toStatus → createdAt` 으로 변환한 맵이다.

| 분류 | 필드 | 설명 |
|---|---|---|
| 보고서 본문 | `reportId`, `requestId`, `engineerName`, `diagnosisResult`, `workDurationMin`, `finalAmount`, `memo`, `imageUrls` | 보고서 핵심 |
| 승인 | `customerApproved`, `approvedAt`, `submittedAt` | 고객 승인 여부·시각, 제출 시각 |
| ✨ 가전/고객 | `modelNo`, `serialNo`, `customerPhone`, `customerAddress` | `asRequest.getAppliance()` / `asRequest.getCustomer()` 합본 (주소는 지역명+상세주소 조립) |
| ✨ 타임라인 | `arrivedAt`, `workStartAt`, `workEndAt` | `as_status_logs` 에서 `ENGINEER_ARRIVED` / `IN_PROGRESS` / `COMPLETED` 시각 매핑 |
| 부품 | `parts[]` : `partName`, **`partCode`(✨추가)**, `quantity`, `appliedUnitPrice` | 교체 부품 목록(`repair_parts` 조인) |

## 3. 상세 처리 로직 (Pipeline)

### [POST] 보고서 제출 — submitReport
1. **검증(Validation) 단계**
    - 인증 확인 : `@AuthenticationPrincipal CustomUserDetails` 에서 `engineerId` 추출
    - `CreateWorkReportRequest` validation 만족 확인 (**표준 스키마 강제화** — 필수 항목 누락 또는 값 범위 위반 시 제출 불가)
    - 대상 A/S 신청 존재 확인 : `requestId` 로 `AsRequest` 조회, 없으면 예외
    - **본인 배정 검증 (인가)** : `as_assignments` 에서 해당 `request_id` 배정 내역을 조회하여, `engineer_id` 가 요청자 본인과 일치하고 배정 상태가 `ACCEPTED` 또는 `COMPLETED` 인 건이 존재하는지 확인 — 없으면 거부
    - **중복 제출 방어** : `work_reports.request_id` 존재 여부 선검사 — 이미 제출된 보고서가 있으면 거부
    - (검증 순서 : **인가 검증 → 중복 검사 → 상태 전이** 순으로 수행하여, 권한 없는 요청자에게 보고서 존재 여부가 노출되지 않도록 한다)
2. **데이터 처리(Process) 단계**
    - **상태 전이 (FSM)** : `AsRequest.completeWork()` 호출 — A/S 상태가 **`IN_PROGRESS`일 때만** `COMPLETED` 로 전환. 그 외 상태에서 호출되면 예외 처리한다.
        - 전체 상태 흐름 : `PENDING → AGENCY_RECEIVED → ASSIGNED → ACCEPTED → IN_PROGRESS → COMPLETED → PAID`
        - 선행 전이는 배정 수락(`acceptAssignment()`: ASSIGNED→ACCEPTED), 작업 시작(`startWork()`: ACCEPTED→IN_PROGRESS)을 통해 이루어진다.
    - **보고서·부품 내역 영속화** : `work_reports` 및 `work_report_parts` 에 **Cascade** 동시 저장. 부품 단가는 `applied_unit_price` 에 스냅샷 저장(요청에 없으면 `repair_parts.base_unit_price` 사용).
    - **건강 진단서 갱신 트리거** : 교체된 부품들 중 `repair_parts.importance` 기준 최고 중요도를 추출하여(없으면 `null`) `HealthCertificate.calculateAndUpdateHealth()` 호출. **상세 로직은 `healthCertificate.md` 참조.**
    - **상태 변경 로그 기록** : `as_status_logs` 에 `from_status(IN_PROGRESS)`, `to_status(COMPLETED)`, `memo` 기록 (SSE 원천·타임라인 출처).
    - **실시간 알림** : 고객·대행사 대표에게 완료 알림 발송 (SSE 상세는 `sseRealtimeTracking.md` 참조).
3. **응답(Response) 단계** : HTTP `201`, 생성된 `reportId` 를 포함한 안내 문자열 반환

### [GET] 보고서 상세 — getWorkReportDetail(userId, role, reportId)
1. **검증** : `findByIdWithParts` 로 조회(없으면 `NoSuchElementException`). `role` 별 소유 검증 — `CUSTOMER` 는 본인 A/S 보고서, `ENGINEER` 는 본인 작성 보고서만 조회 가능(그 외 권한 거부)
2. **타임라인 조합** : `asStatusLogRepository.findByAsRequest_IdOrderByCreatedAtAsc(requestId)` 로 상태 로그를 조회해 `toStatus→createdAt` 맵 생성(중복 시 최초값 유지)
3. **응답** : `WorkReportDetailResponse.of(report, statusTimeMap)` 반환 (가전·고객·타임라인·부품 합본)

### [PATCH] 보고서 승인 — approveReport
- 고객 본인 검증 후 `customer_approved = true` 전환, 결제 단계 진입 안내 문자열 반환

## 5. 예외 처리 (Error Handling) 및 제약 조건
- 모든 에러 발생 시 공통 포맷(`{ "success": false, "message": "에러 내용" }`)으로 응답할 것.
- 제출 흐름(보고서 저장 · 상태 전이 · 진단서 갱신 · 상태 로그 기록)을 **하나의 `@Transactional`** 안에서 수행하되, 어느 단계에서 DB 에러가 발생하더라도 **모든 변경사항을 롤백**할 것. 상세 조회는 `@Transactional(readOnly = true)`.
- 상세 응답 조립 시 `getAppliance()`·`getCustomer().getRegionId()` 등이 null 일 수 있으므로 안전 분기로 처리(주소 조립 시 null 가드).
- 주요 예외 매핑
    - 존재하지 않는 A/S 신청 / 존재하지 않는 부품 → `400` (IllegalArgumentException)
    - 보고서 미존재(상세 조회) → `404` (NoSuchElementException)
    - 본인 미배정 건 / `IN_PROGRESS` 가 아닌 상태에서 완료 시도 → `403` (IllegalStateException)
    - 타인 보고서 조회/승인 시도 → `403` (IllegalAccessException)
    - 동일 A/S 건 중복 제출 (`work_reports.request_id` UNIQUE 위반) → `409 Conflict` 권장
    - 표준 스키마 위반 (필수 항목 누락 / 값 범위 위반) → `400` (MethodArgumentNotValidException)

## 6. 개발 및 출력 요구사항
- 컨트롤러 · 서비스 · 리포지토리 · 엔티티 레이어를 명확히 분리하여 구현할 것 (package-by-feature 구조)
- 엔티티 컨벤션 준수 : `@Getter` + `@NoArgsConstructor(access = PROTECTED)` + `@Builder`, `@Setter`/`@Data` 금지, **모든 상태 변경은 도메인 메서드를 통해서만** 수행 (`completeWork()`, `acceptAssignment()`, `startWork()` 등)
- **상세 응답 DTO 는 정적 팩토리 `of(report, statusTimeMap)` 로 변환** (타임라인·가전·고객 합본을 위해 `from()` 에서 변경됨)
- 작성된 API 에 대해 단위 테스트(JUnit5 + Mockito, DB 의존성 제거)와 통합 테스트(`@SpringBootTest` + H2 인메모리)를 함께 생성할 것
    - 상태 전이는 reflection 강제 주입이 아닌 **실제 도메인 메서드 호출**(`processAssignment → acceptAssignment → startWork`)로 `IN_PROGRESS` 까지 도달시킨 뒤 검증할 것
    - 본인 미배정 / 중복 제출 / 잘못된 상태 등 실패 케이스를 함께 검증할 것
    - **(상세 확장 반영)** 단위 테스트는 `asStatusLogRepository.findByAsRequest_IdOrderByCreatedAtAsc(...)` 스터빙과 `asRequest.getAppliance()` 픽스처를 추가할 것. 통합 테스트는 `as_status_logs` 3건(ARRIVED/IN_PROGRESS/COMPLETED)·`repair_parts.part_code` 를 적재한 뒤 타임라인(`arrivedAt` 등)·`partCode` 를 단언할 것