# GET /api/agency/engineers/top3 — 대행사 수리 완료 실적 TOP 3 기사 조회

## 개요

대행사 대시보드에서 소속 수리 기사들의 **A/S 수리 완료(COMPLETED) 건수**를 기준으로  
내림차순 정렬하여 상위 3명의 기사 정보를 조회하는 API.

---

## 요청

| 항목 | 내용 |
|------|------|
| Method | `GET` |
| URI | `/api/agency/engineers/top3` |
| 인증 | 필수 (JWT Bearer Token) |
| 권한 | `ROLE_AGENCY` 전용 |

### 요청 파라미터

없음 (로그인된 대행사 관리자의 JWT에서 소속 대행사를 자동 식별)

---

## 응답

### 성공 — 200 OK

```json
[
  {
    "rank": 1,
    "engineerUserId": 10,
    "name": "홍길동",
    "completedCount": 42
  },
  {
    "rank": 2,
    "engineerUserId": 11,
    "name": "김수리",
    "completedCount": 38
  },
  {
    "rank": 3,
    "engineerUserId": 12,
    "name": "이기사",
    "completedCount": 27
  }
]
```

| 필드 | 타입 | 설명 |
|------|------|------|
| `rank` | int | 순위 (1~3) |
| `engineerUserId` | Long | 기사 유저 ID |
| `name` | String | 기사 이름 |
| `completedCount` | long | 수리 완료(COMPLETED) 건수 |

### 기타 응답

| HTTP 상태 | 조건 |
|-----------|------|
| 200 OK | 성공 (결과 0~3건, 빈 배열도 200 반환) |
| 401 Unauthorized | 미인증 또는 ROLE_AGENCY 아닌 경우 |
| 404 Not Found | 로그인 유저의 소속 대행사 정보 없음 |

---

## 비즈니스 로직

1. JWT `@AuthenticationPrincipal`에서 `userId` 추출
2. `users` 테이블에서 `userId`로 소속 `agency_id` 조회
3. `as_assignments` JOIN `as_requests` WHERE  
   `as_assignments.agency_id = agencyId` AND `as_requests.status = 'COMPLETED'`
4. `engineer_id` 기준 GROUP BY → COUNT 집계
5. COUNT 내림차순 정렬 → 상위 3건만 반환 (JPQL `LIMIT` 대신 `Pageable` 또는 리스트 slice)
6. rank 1·2·3 순번을 DTO에 부여하여 응답

---

## 구현 대상 파일

| 레이어 | 파일 경로 |
|--------|----------|
| DTO | `agency/dto/response/EngineerRankResponse.java` |
| Repository | `assignment/repository/AsAssignmentRepository.java` — 쿼리 메서드 추가 |
| Service | `agency/service/AgencyEngineerService.java` — `getTop3Engineers()` 추가 |
| Controller | `agency/controller/AgencyEngineerController.java` — 엔드포인트 추가 |

---

## 테스트 명세

> **이 파일에 명시된 테스트 케이스는 반드시 모두 구현하고 통과시켜야 한다.**  
> 단위 테스트(`@WebMvcTest`)와 통합 테스트(`@SpringBootTest` + H2 DB) 양쪽을 모두 작성한다.

---

### 단위 테스트 — `AgencyEngineerControllerTest` 추가

> 파일: `src/test/java/com/careflow/agency/controller/AgencyEngineerControllerTest.java`

#### TC-U-01. 성공: TOP 3 기사 3명 반환 — 200 OK, 순위·완료건수 포함 JSON 배열

- Given: `agencyEngineerService.getTop3Engineers(AGENCY_USER_ID)` → 3건 stub 반환
- When: `GET /api/agency/engineers/top3` (AGENCY 인증)
- Then:
  - HTTP 200
  - `$.length() == 3`
  - `$[0].rank == 1`, `$[0].completedCount` 가장 큰 값
  - `$[2].rank == 3`

#### TC-U-02. 성공: 소속 기사 1명뿐인 경우 — 200 OK, 1건만 반환

- Given: 서비스가 1건 반환
- When: `GET /api/agency/engineers/top3`
- Then: HTTP 200, `$.length() == 1`

#### TC-U-03. 성공: 소속 기사 없음 — 200 OK, 빈 배열

- Given: 서비스가 빈 리스트 반환
- When: `GET /api/agency/engineers/top3`
- Then: HTTP 200, `$.length() == 0`

#### TC-U-04. 실패: 미인증 요청 — 401 Unauthorized

- When: 인증 토큰 없이 `GET /api/agency/engineers/top3`
- Then: HTTP 401

#### TC-U-05. 실패: 대행사 정보 없는 유저 — 404 Not Found

- Given: 서비스가 `NoSuchElementException` throw
- When: `GET /api/agency/engineers/top3`
- Then: HTTP 404

---

### 통합 테스트 — `AgencyEngineerServiceIntegrationTest` 추가

> 파일: `src/test/java/com/careflow/agency/service/AgencyEngineerServiceIntegrationTest.java`  
> 설정: `@SpringBootTest`, `@ActiveProfiles("local")`, `@Sql("/cleanup.sql")` (기존 클래스에 Nested 추가)

#### TC-I-01. 성공: H2에 COMPLETED 건수가 다른 기사 4명 → 상위 3명 내림차순 반환

- Given: 기사 A(COMPLETED 5건), B(3건), C(7건), D(1건) INSERT
- When: `agencyEngineerService.getTop3Engineers(agencyUserId)`
- Then:
  - 결과 3건
  - rank 1 → C(7), rank 2 → A(5), rank 3 → B(3)
  - D는 포함되지 않음

#### TC-I-02. 성공: 소속 기사 없을 때 — 빈 리스트 반환

- Given: 기사 없음
- When: `getTop3Engineers(agencyUserId)`
- Then: 빈 리스트

#### TC-I-03. 성공: 타 대행사 기사의 COMPLETED 건은 집계 제외 확인

- Given:
  - 내 대행사 기사: COMPLETED 3건
  - 다른 대행사 기사: COMPLETED 10건 (같은 DB)
- When: `getTop3Engineers(agencyUserId)`
- Then: 결과 1건 (내 대행사 기사만), 다른 대행사 기사 제외 확인

#### TC-I-04. 성공: COMPLETED 가 아닌 상태(ASSIGNED, CANCELLED 등)는 집계 제외 확인

- Given: 기사에게 COMPLETED 2건, CANCELLED 5건 as_request 존재
- When: `getTop3Engineers(agencyUserId)`
- Then: `completedCount == 2` (COMPLETED만 카운트)

#### TC-I-05. 실패: 소속 대행사 없는 유저 ID → `NoSuchElementException`

- Given: agency 필드 null인 유저 ID 사용
- When: `getTop3Engineers(noAgencyUserId)`
- Then: `NoSuchElementException` with message "소속 대행사 정보가 없습니다."
