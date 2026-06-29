# 🚀 API 생성 및 개발 요구사항 정의서

## 1. 데이터 모델 (Schema)
- 메인 스키마 정의 파일 : `sql/CareFlow_v5.sql`
- 위 파일에서 as_requests, agencies, users, appliance, engineer_profiles, engineer_schedules, engineer_schedules_slots, engineer_expert_brands, engineer_service_regions 테이블 위주로 참조할것 
- 해당 테이블과 매핑되는 Entity 클래스가 없을 시 직접 생성할 것

## 2. API 엔드포인트 명세
- 이미 URI 로 매핑된 API 가 존재할 시 아래의 요구사항 대로 코드를 변경할 것
### [POST] /api/as-requests - AgenciesController
- **설명**: 고객 가전 A/S 요청 (로그인을 통한 인증 필요)
- **Request Body**:
    - @Valid @RequestBody AsRequestCreateDto dto 형태로 매개변수로서 요청 데이터 받음
- **Response (201 Created)**:
    - ResponseEntity body 에 생성된 AsRequest Entity 객체 id 값 적재 후 201 응답반환

## 3. 상세 처리 로직 (Pipeline)
1. **검증(Validation) 단계**
    - 인증/인가 확인(@AuthenticationPrincipal)
    - AsRequestCreateDto 객체 validation 조건 만족 확인
2. **데이터 처리(Process) 단계**
    - 고객은 AsRequestCreateDto 객체에 validation 조건이 걸린 필수 입력 필드들에 대한 정보 입력 후 A/S 신청을 할 수 있다.
    - 이때, AsRequestCreateDto 의 assignType 값이 "AUTO" 일 때와 "MANUAL" 일 때 각각 다른 로직을 구현한다.
    - assignType 값에 따라 다른 로직이 수행되어 해당 A/S 요청을 할당받을 수리 기사가 정해지면 AsAssignment Entity 객체에 데이터가 저장되어 DB 테이블에 적재된다.(만약 AsAssignment Entity 객체가 존재하지 않는다면 as_assignment 테이블 구조를 참조하여 객체를 만든다.)
    - assignType 값이 "AUTO" 일 경우 수리기사를 아래의 4가지 조건에 모두 부합하는 수리 기사를 조회하여 자동으로 배정해준다.
   1. 방문 예약 날짜(scheduledDate) 및 방문 예약시간(scheduledTime) 에 대해 해당 날짜와 시간에 근무가 가능한 수리 기사(수리 기사 근무 날짜 : engineer_schedules 테이블의 work_date 값과 일치하는 경우, 수리 기사 근무 가능시간 : engineer_schedule_slots 테이블의 start_time 컬럼값 보다 크거나 같거나 end_time 컬럼보다 작은값인 경우, 두 가지 조건 모두 충족해야 할 것)
   2. 수리 기사의 전문 브랜드와 현재 접수된 가전의 브랜드가 일치하는 수리 기사(as_requests 테이블의 appliance_id 컬럼과 외래키로 매핑되는 appliances 테이블의 레코드의 brand 컬럼에 저장되어 있는 데이터와 engineer_expert_brands 테이블의 brand_name 이 일치하는 지 확인)
   3. engineer_profiles.category_id 와 as_requests 데이터의 appliance_id 와 매핑되는 레코드의 category_id 값 일치 여부(전문 가전 카테고리 단일)
   4. engineer_service_regions.regions_id 와 as_requests.visit_region_id 일치 여부(기사 서비스 가능지역)

    - 위의 4가지 조건을 모두 충족하는 기사가 다수일 경우 engineer_profiles.avg_rating 값이 높은 순으로 자동 배정 -> 이렇게 처리하면 avg_rating 값이 가장 높은 기사에게 일거리가 집중적으로 몰릴 수 있는데 이를 해결하기 위한 방법이 있다면 추론 후 제시해줄 것, 방법을 제시한 이후 개발자가 검토 및 승인 할 때까지 대기할 것
    - 위의 4가지 조건을 모두 충족하는 기사가 없을 경우 순차적으로 필터링 조건 완화(fallback)
    1. 브랜드 조건 완화(브랜드 일치 조건 제외 및 재 검색) 
    2. 1번 완화에도 조건을 충족하는 기사가 없을 경우 서비스 가능 지역 조건 완화(기사 서비스 가능 지역 조건 제외 및 재 검색)
    3. 1,2번 완화에도 조건을 충족하는 기사가 없을 경우 전문 가전 카테고리 조건 완화(engineer_profiles.category_id 일치 조건 제외 후 재 검색)
    4. 1,2,3 번 완화에도 조건을 충족하는 기사가 없을 경우 근무 가능한 날짜 조건 완화(고객에게 일정 재협의 안내)
    위의 4가지 조건을 모두 완화 하였음에도 고객에게 배정 가능한 기사가 없을 경우 고객에게 배정 가능한 기사가 없음을 안내 후 수동 배정으로 유도

    - assignType 값이 "MANUAL" 일 경우 고객이 수동으로 선택한 기사에게 직접 작업 배정 요청(as_assignment 테이블에 데이터 적재)
    - 고객이 수동으로 입력한 수리 기사에 대한 정보를 토대로 users 테이블에서 해당하는 수리 기사(role = ENGINEER) 를 찾아서 as_assignment 테이블에 데이터를 적재할 것

3. **응답(Response) 단계**
- 수리 기사 배정 성공 및 as_assignment 테이블에 고객 A/S 요청 데이터 적재 성공 시 HTTP $201$, 생성된 AsAssignment Entity 객체의 id 값을 json 형태로 body 에 포함하여 반환

- 실패 시 HTTP $500$, 서버 내부에서 에러가 발생한 이유를 문자열로 작성 후 에러 메시지로 반환(e.getMessage())

## 4. 예외 처리 (Error Handling) 및 제약 조건
- 모든 에러 발생 시 공통 포맷(`{ "success": false, "message": "에러 내용" }`)으로 응답할 것.
- API 요청 시 동작을 한 트랜잭션 안에서 수행하되, 로직 수행 도중 DB 에러가 발생하여 기능 수행이 도중에 중단될 경우 트랜잭션을 롤백하여 요청 수행 중 발생한 모든 DB 변경사항 롤백할것  

## 5. 개발 및 출력 요구사항
- 컨트롤러, 서비스, 라우터 레이어를 명확히 분리하여 구현해 줘.
- 작성된 API에 대해 기본적인 유닛 테스트(JUnit5 선호) 코드도 함께 생성해 줘.