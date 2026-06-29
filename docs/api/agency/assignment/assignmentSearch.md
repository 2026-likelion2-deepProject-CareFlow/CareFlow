# 🚀 API 생성 및 개발 요구사항 정의서

## 1. 데이터 모델 (Schema)
- 메인 스키마 정의 파일 : `sql/CareFlow_DDL_v5.sql`
- 참조 테이블 : `as_assignments`, `as_requests`, `symptoms`, `users`
  - `as_assignments.status` : 배차 상태 필터 기준 컬럼 (WAITING / ACCEPTED / REJECTED / COMPLETED)
  - `as_requests.scheduled_date` : 날짜 필터 기준 컬럼 (방문 예정일)

## 2. API 엔드포인트 명세

### [GET] /api/assignment/search

- **설명** : 대행사 소속 배차 목록을 날짜·상태 필터로 동적 조회 (배차 현황 모니터링용)
- **API Parameter (RequestParam, 모두 선택)**

  | 파라미터 | 타입 | 기본값 | 설명 |
  |---|---|---|---|
  | `date` | `LocalDate` (yyyy-MM-dd) | 오늘(`LocalDate.now()`) | 방문 예정일 기준 필터. 미입력 시 오늘 날짜로 자동 적용 |
  | `status` | `String` | null (전체) | 배차 상태 필터. `WAITING` / `ACCEPTED` / `REJECTED` / `COMPLETED` 중 하나. 미입력 시 전체 상태 반환 |

- **Response (200 OK)** : 조회된 배차 목록을 `List<AssignmentFilterResponse>` 로 반환
- **Response (204 No Content)** : 조건에 맞는 배차 내역이 없는 경우
- **Response (401 Unauthorized)** : role != AGENCY
- **Response (400 Bad Request)** : status 값이 허용된 ENUM 값이 아닌 경우

## 3. 상세 처리 로직 (Pipeline)

1. **검증(Validation) 단계**
   - `@AuthenticationPrincipal` 로 role 확인 → role != AGENCY 이면 401
   - `status` 파라미터가 입력된 경우 허용 값(`WAITING`, `ACCEPTED`, `REJECTED`, `COMPLETED`) 여부 검증 → 불일치 시 400

2. **데이터 처리(Process) 단계**
   - 로그인 유저 정보로부터 `agencyId` 추출 (`userRepository.findById`)
   - 아래 조건을 **단일 JPQL 쿼리**로 조회 (동적 필터)
     - 고정 조건 : `as_assignments.agency_id = :agencyId`
     - 날짜 조건 : `:date IS NULL OR as_requests.scheduled_date = :date`
     - 상태 조건 : `:status IS NULL OR as_assignments.status = :status`
   - N+1 방지를 위해 `JOIN FETCH` 로 `as_requests`, `symptoms`, `engineer(users)` 한 번에 로딩

3. **동적 쿼리 구조 (JPQL)**
   ```
   SELECT DISTINCT a FROM AsAssignment a
   JOIN FETCH a.asRequest r
   JOIN FETCH r.symptom
   JOIN FETCH a.engineer
   WHERE a.agency.id = :agencyId
     AND (:date IS NULL OR r.scheduledDate = :date)
     AND (:status IS NULL OR a.status = :status)
   ORDER BY r.scheduledDate ASC, a.assignedAt ASC
   ```

4. **응답(Response) 단계**
   - 결과 없음 → 204 No Content
   - 결과 있음 → 200 OK + `List<AssignmentFilterResponse>`

## 4. Response 필드 (`AssignmentFilterResponse`)

| 필드 | 출처 | 설명 |
|---|---|---|
| `assignmentId` | as_assignments | 배차 ID |
| `assignmentStatus` | as_assignments.status | 배차 상태 |
| `assignMethod` | as_assignments.assign_method | AUTO / MANUAL |
| `assignedAt` | as_assignments.assigned_at | 배차 일시 |
| `acceptedAt` | as_assignments.accepted_at | 수락 일시 (null 가능) |
| `requestId` | as_requests.request_id | A/S 요청 ID |
| `scheduledDate` | as_requests.scheduled_date | 방문 예정일 |
| `scheduledTime` | as_requests.scheduled_time | 방문 예정 시간 |
| `visitAddressDetail` | as_requests.visit_address_detail | 방문 상세 주소 |
| `symptomName` | symptoms.symptom_name | 증상명 |
| `engineerId` | users.user_id | 배정 기사 ID |
| `engineerName` | users.name | 배정 기사 이름 |

## 5. 예외 처리 및 제약 조건
- 모든 에러 발생 시 공통 포맷 (`{ "success": false, "message": "에러 내용" }`) 응답
- 트랜잭션 내 DB 에러 발생 시 롤백

## 6. 개발 및 출력 요구사항
- 컨트롤러, 서비스, Repository 레이어 명확히 분리
- 단위 테스트 + H2 통합 테스트 함께 작성
