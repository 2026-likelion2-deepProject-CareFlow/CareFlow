# 🚀 API 생성 및 개발 요구사항 정의서

## 1. 데이터 모델 (Schema)
- 메인 스키마 정의 파일 : `sql/CareFlow_v5.sql`
- 위 파일에서 agencies, users, as_assignments, as_requests, engineer_profiles, engineer_schedules, engineer_schedules_slots, engineer_expert_brands, engineer_service_regions 테이블 위주로 참조할것

## 2. API 엔드포인트 명세
### [GET] /api/agencies/assignment - AgenciesAssignementController.getAgenciesAssignement()
-- **설명**: 현재 로그인한 관리자와 같은 대행사 소속의 수리 기사들의 고객 A/S 배차 내역 조회
- **API Parameter** :
  - @Authentication CustomUserDetails : 현재 로그인한 사용자가 일반 관리자 권한이 있는지(role=AGENCY), agencyId 값을 이용해 as_assignment 테이블에서 같은 대행사 소속의 수리 기사들에게 배차된 내역이 있는지 확인
- **API Response(200 OK)** : as_assignment 테이블에서 조회된 현재 관리자와 같은 소속 수리 기사들의 배차 내역 리스트 반환
- **API Response(401 Unauthorized)** : 현재 로그인한 사용자에게 대행사 관리자 권한이 없는 경우(role != AGENCY)
## 3. 상세 처리 로직 (Pipeline)
1. **검증(Validation) 단계**
    - 인증/인가 확인(@AuthenticationPrincipal)
2. **데이터 처리(Process) 단계** 
    - 로그인한 관리자 정보로부터(userDetails) agency_id 값 추출한 후 해당 값을 기준으로 as_assignment 에 배차내역 조회(AsAssignment 객체 리스트 조회)
3. **응답(Response) 단계**
    - 조회 성공 시 HTTP $200$ OK, 조회 결과 List<AsAssignment> 를 ResponseEntity body 에 담아서 응답 반환
    - 조회 결과 데이터가 존재하지 않을 시 HTTP $204$ NoContent 응답 반환
    - 조회 도중 내부 서버 에러시 HTTP $500$ Internal Server Error 응답 반환

## 4. 예외 처리 (Error Handling) 및 제약 조건
- 모든 에러 발생 시 공통 포맷(`{ "success": false, "message": "에러 내용" }`)으로 응답할 것.
- API 요청 시 동작을 한 트랜잭션 안에서 수행하되, 로직 수행 도중 DB 에러가 발생하여 기능 수행이 도중에 중단될 경우 트랜잭션을 롤백하여 요청 수행 중 발생한 모든 DB 변경사항 롤백할것
- 
## 5. 개발 및 출력 요구사항
- 컨트롤러, 서비스, 라우터 레이어를 명확히 분리하여 구현해 줘.
- 작성된 API에 대해 기본적인 유닛 테스트와 통합 테스트(JUnit5 선호) 코드도 함께 생성해 줘.