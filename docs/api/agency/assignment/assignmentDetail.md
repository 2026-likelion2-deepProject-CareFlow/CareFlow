# 🚀 API 생성 및 개발 요구사항 정의서

## 1. 데이터 모델 (Schema)
- 메인 스키마 정의 파일 : `sql/CareFlow_v5.sql`
- 위 파일에서 as_requests, appliances, users, symptoms, as_status_logs, expected_repair_costs, work_reports, engineer_profiles 테이블 위주로 참조할것
- 해당 테이블과 매핑되는 Entity 클래스가 없을 시 직접 생성할 것

## 2. API 엔드포인트 명세
### [GET] /api/assignment/detail/{assignmentId}
- **설명** : 대행사 소속 수리 기사 배차 상태 상세보기
- **API Parameter** :
  - **assignmentId** : 상세보기 버튼 클릭을 통해 클라이언트로부터 받아온 as_assignment 테이블(AsAssignment) 데이터 기본키 값(@PathVariable)

- **Response (200 OK)**: 아래 데이터 처리 단계에서 조회해온 각종 정보들을 AssignDetailResponse 클래스(record)의 객체에 적재한 후 ResponseEntity body 에 담아서 반환
- **Response (401 Unauthorized)**: 현재 로그인한 사용자에게 대행사 관리자 권한이 없는 경우(role != AGENCY)
- **Response (404 Not Found)**: 조회 결과 데이터를 찾을 수 없는 경우

## 3. 상세 처리 로직 (Pipeline)
1. **검증(Validation) 단계**
  - 인증/인가 확인(@AuthenticationPrincipal) — role != AGENCY 이면 IllegalAccessException → 401 반환

2. **데이터 처리(Process) 단계**
   1. 파라미터로 받은 assignmentId 를 이용해 as_assignments 테이블에서 배차 내역을 단건 조회
      - `asAssignmentRepository.findById(assignmentId)` → 없으면 NoSuchElementException → 404 반환
   2. 1에서 받아온 AsAssignment 객체의 데이터를 이용해 아래와 같은 각종 테이블들과 조인하여 데이터를 조회
      - **AsRequest 조회**: `assignment.getAsRequest()` 로 연관 A/S 요청 객체 추출
        → 방문 일정(scheduled_date), 방문 시간(scheduled_time), 증상 상세설명(symptom_desc), 주소(visit_address_detail) 추출
        → 추후 추가 조회 요청에서 `asRequest.getId()` 및 `asRequest.getAppliance()` 활용
      - **Symptom 조회**: `asRequest.getSymptom()` → 증상명(symptom_name) 추출
        → 추후 추가 조회 요청에서 `symptom.getId()` 활용
      - **고객 정보 조회**: `asRequest.getCustomer()` → 고객명(name), 연락처(phone) 추출
      - **가전 정보 조회**: `asRequest.getAppliance()` → 모델명(model_name), 구매일(purchase_date), 보증 기간(warranty_end_date) 추출
        → 추후 이전 수리 이력 조회에서 `appliance.getId()` 활용
   3. 2에서 조회해온 정보를 활용해 다시 아래의 정보를 추가 조회(select)
      - **예상 수리 비용 조회**: `symptom.getId()` 를 이용해 `expectedRepairCostRepository.findBySymptom_Id(symptomId)` 로 expected_repair_costs 테이블 조회 → avg_cost 추출 (데이터 없을 시 null 허용)
      - **처리 이력 조회**: `asRequest.getId()` 를 이용해 `asStatusLogRepository.findByAsRequest_IdOrderByCreatedAtAsc(requestId)` 로 as_status_logs 테이블 조회 → 상태 변경 이력 리스트 반환
      - **이전 수리 이력 조회**: `asRequest.getAppliance().getId()` (appliance_id) 를 이용해 `workReportRepository.findByAsRequest_Appliance_Id(applianceId)` 로 work_reports 테이블 조회
        → 같은 가전제품(동일 appliance_id)에 대해 작성된 모든 work_reports 데이터 반환 (현재 A/S 건 포함)
        → 결과를 WorkReportInfo 서브 record 로 변환하여 이전 수리 이력 목록으로 제공
      - **배정 기사 프로필 조회**: `assignment.getEngineer().getId()` 를 이용해 `engineerProfileRepository.findByUser_Id(engineerId)` 로 engineer_profiles 테이블 조회 → 기술 등급(skill_level), 평균 평점(avg_rating), 자기소개(introduction) 등 추출 (프로필 미등록 시 null 허용)

3. **응답(Response) 단계**
  - 조회 성공 시 HTTP 200 OK, 조회 결과 AssignDetailResponse 객체를 ResponseEntity body 에 담아서 응답 반환
  - 조회 결과 데이터가 존재하지 않을 시(assignmentId 미존재) HTTP 404 Not Found 응답 반환
  - 조회 도중 내부 서버 에러시 HTTP 500 Internal Server Error 응답 반환

## 4. 예외 처리 (Error Handling) 및 제약 조건
- 모든 에러 발생 시 공통 포맷(`{ "success": false, "message": "에러 내용" }`)으로 응답할 것.
- API 요청 시 동작을 한 트랜잭션 안에서 수행하되, 로직 수행 도중 DB 에러가 발생하여 기능 수행이 도중에 중단될 경우 트랜잭션을 롤백하여 요청 수행 중 발생한 모든 DB 변경사항 롤백할것

## 5. 개발 및 출력 요구사항
- 컨트롤러, 서비스, 라우터 레이어를 명확히 분리하여 구현해 줘.
- 작성된 API에 대해 기본적인 유닛 테스트와 h2 DB 와 직접 데이터를 주고받는 통합 테스트(JUnit5 선호) 코드도 함께 생성해 줘.
