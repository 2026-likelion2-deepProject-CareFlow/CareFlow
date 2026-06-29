# API: 소속 기사 목록 조회

## 개요

대행사 관리자가 본인 대행사에 소속된 수리기사 전체 목록을 조회한다.

---

## 엔드포인트

```
GET /api/agencies/me/engineers
```

---

## 인증/인가

- **필수 인증**: JWT Bearer 토큰
- **허용 역할**: `AGENCY`
- `@AuthenticationPrincipal`로 로그인한 대행사 관리자의 userId를 추출하여 소속 agencyId를 조회한다.

---

## 요청

### 쿼리 파라미터

없음

### 요청 예시

```
GET /api/agencies/me/engineers
Authorization: Bearer {access_token}
```

---

## 응답

### 200 OK

```json
[
  {
    "engineerUserId": 10,
    "name": "홍길동",
    "categoryId": 5,
    "categoryName": "냉장고",
    "skillLevel": "INTERMEDIATE",
    "serviceRegionIds": [101, 102],
    "isLmsCompleted": true,
    "currentWorkStatus": "AVAILABLE"
  },
  {
    "engineerUserId": 11,
    "name": "김수리",
    "categoryId": 3,
    "categoryName": "세탁기",
    "skillLevel": "BEGINNER",
    "serviceRegionIds": [103],
    "isLmsCompleted": false,
    "currentWorkStatus": "BOOKED"
  }
]
```

### 응답 필드 설명

| 필드 | 타입 | 설명 |
|---|---|---|
| `engineerUserId` | Long | 기사의 user_id |
| `name` | String | 기사 이름 |
| `categoryId` | Integer | 전문 가전 카테고리 ID (소분류 depth=2) |
| `categoryName` | String | 전문 가전 카테고리 이름 |
| `skillLevel` | String | 기술 등급 (BEGINNER / INTERMEDIATE / ADVANCED) |
| `serviceRegionIds` | List\<Integer\> | 활동 지역 ID 목록 |
| `isLmsCompleted` | Boolean | 당해 연도 LMS 이수 여부 |
| `currentWorkStatus` | String | 현재 작업 상태 (AVAILABLE / BOOKED / OFF) |

### 에러 응답

| HTTP 상태 | 조건 |
|---|---|
| 401 Unauthorized | JWT 토큰 없음 또는 만료 |
| 403 Forbidden | AGENCY 역할이 아닌 경우 |
| 404 Not Found | 로그인한 유저에 연결된 대행사 정보가 없는 경우 |

---

## 구현 위치

| 계층 | 클래스 |
|---|---|
| Controller | `com.careflow.agency.controller.AgencyEngineerController` |
| Service | `com.careflow.agency.service.AgencyEngineerService` |
| Repository | `com.careflow.engineer.repository.EngineerProfileRepository` (기존 `findByAgencyId` 재사용) |
| DTO | `com.careflow.agency.dto.response.AgencyEngineerSummaryResponse` |

---

## 테스트 명세

### JUnit 5 단위 테스트 (Service Layer)

**파일**: `src/test/java/com/careflow/agency/service/AgencyEngineerServiceTest.java`

#### TC-1. 정상 조회 — 소속 기사 2명 반환
- Given: agencyId=1 에 소속된 EngineerProfile 2개 Mock
- When: `getAgencyEngineers(agencyUserId)` 호출
- Then: 반환 리스트 size == 2, 각 필드 정상 매핑 검증

#### TC-2. 소속 기사 없음 — 빈 리스트 반환
- Given: agencyId=1 에 소속 기사 0명
- When: `getAgencyEngineers(agencyUserId)` 호출
- Then: 빈 리스트 반환 (예외 아님)

#### TC-3. 대행사 정보 없는 유저 — 예외
- Given: userId에 해당하는 User의 agency == null
- When: `getAgencyEngineers(agencyUserId)` 호출
- Then: `NoSuchElementException` 발생

---

### JUnit 5 통합 테스트 (Controller Layer — @WebMvcTest)

**파일**: `src/test/java/com/careflow/agency/controller/AgencyEngineerControllerTest.java`

#### TC-4. 인증된 AGENCY 역할 — 200 OK + 목록 반환
- Given: `@WithMockUser(roles = "AGENCY")`, Service Mock → 기사 2명 목록 반환
- When: `GET /api/agencies/me/engineers`
- Then: status 200, JSON 배열 size 2

#### TC-5. 인증 없음 — 401
- When: Authorization 헤더 없이 `GET /api/agencies/me/engineers`
- Then: status 401

#### TC-6. ENGINEER 역할로 접근 — 403
- Given: `@WithMockUser(roles = "ENGINEER")`
- When: `GET /api/agencies/me/engineers`
- Then: status 403
