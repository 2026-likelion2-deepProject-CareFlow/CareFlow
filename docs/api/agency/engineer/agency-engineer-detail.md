# API: 소속 기사 단건 상세 조회

## 개요

대행사 관리자가 소속 기사 한 명의 상세 프로필(경력, 기술 등급, 전문 브랜드, 활동 지역 등)을 조회한다.

---

## 엔드포인트

```
GET /api/agencies/me/engineers/{engineerUserId}
```

---

## 인증/인가

- **필수 인증**: JWT Bearer 토큰
- **허용 역할**: `AGENCY`
- 요청한 기사가 로그인한 대행사 관리자와 동일 대행사 소속인지 검증한다.

---

## 요청

### 경로 파라미터

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `engineerUserId` | Long | Y | 조회할 기사의 user_id |

### 요청 예시

```
GET /api/agencies/me/engineers/10
Authorization: Bearer {access_token}
```

---

## 응답

### 200 OK

```json
{
  "engineerUserId": 10,
  "name": "홍길동",
  "email": "hong@example.com",
  "phone": "010-1234-5678",
  "categoryId": 5,
  "categoryName": "냉장고",
  "careerStartedYear": 2015,
  "skillLevel": "INTERMEDIATE",
  "introduction": "냉장고 전문 수리기사입니다.",
  "profileImageUrl": "https://...",
  "avgRating": 4.80,
  "totalReviews": 32,
  "isLmsCompleted": true,
  "expertBrands": ["삼성", "LG"],
  "serviceRegionIds": [101, 102],
  "serviceRegionNames": ["강남구", "서초구"],
  "createdAt": "2025-01-10T09:00:00",
  "updatedAt": "2026-06-01T14:30:00"
}
```

### 응답 필드 설명

| 필드 | 타입 | 설명 |
|---|---|---|
| `engineerUserId` | Long | 기사의 user_id |
| `name` | String | 기사 이름 |
| `email` | String | 이메일 |
| `phone` | String | 전화번호 |
| `categoryId` | Integer | 전문 가전 카테고리 ID |
| `categoryName` | String | 전문 가전 카테고리 이름 |
| `careerStartedYear` | Integer | 경력 시작 연도 |
| `skillLevel` | String | 기술 등급 (BEGINNER / INTERMEDIATE / ADVANCED) |
| `introduction` | String | 자기소개 |
| `profileImageUrl` | String | 프로필 이미지 URL |
| `avgRating` | BigDecimal | 평균 평점 |
| `totalReviews` | Integer | 총 리뷰 수 |
| `isLmsCompleted` | Boolean | 당해 연도 LMS 이수 여부 |
| `expertBrands` | List\<String\> | 전문 브랜드 목록 |
| `serviceRegionIds` | List\<Integer\> | 활동 지역 ID 목록 (수정 폼 등 ID 참조용) |
| `serviceRegionNames` | List\<String\> | 활동 지역 이름 목록 (화면 표시용) |
| `createdAt` | LocalDateTime | 프로필 생성일시 |
| `updatedAt` | LocalDateTime | 프로필 최종 수정일시 |

### 에러 응답

| HTTP 상태 | 조건 |
|---|---|
| 401 Unauthorized | JWT 토큰 없음 또는 만료 |
| 403 Forbidden | AGENCY 역할이 아닌 경우, 또는 타 대행사 소속 기사 조회 시도 |
| 404 Not Found | `engineerUserId`에 해당하는 기사 프로필이 없는 경우 |

---

## 구현 위치

| 계층 | 클래스 |
|---|---|
| Controller | `com.careflow.agency.controller.AgencyEngineerController` |
| Service | `com.careflow.agency.service.AgencyEngineerService` |
| Repository | `com.careflow.engineer.repository.EngineerProfileRepository` |
| DTO | `com.careflow.agency.dto.response.AgencyEngineerDetailResponse` |

---

## 테스트 명세

### JUnit 5 단위 테스트 (Service Layer)

**파일**: `src/test/java/com/careflow/agency/service/AgencyEngineerServiceTest.java`

#### TC-1. 정상 조회 — 소속 기사 상세 반환
- Given: agencyId=1 에 소속된 engineerUserId=10 의 EngineerProfile Mock
- When: `getAgencyEngineerDetail(agencyUserId, engineerUserId=10)` 호출
- Then: 반환 DTO의 모든 필드 정상 매핑 검증

#### TC-2. 타 대행사 소속 기사 조회 시도 — 예외
- Given: engineerUserId=20 의 User.agency.id == 2 (로그인 대행사는 agencyId=1)
- When: `getAgencyEngineerDetail(agencyUserId, engineerUserId=20)` 호출
- Then: `IllegalAccessException` 발생

#### TC-3. 존재하지 않는 기사 — 예외
- Given: engineerUserId=999 에 해당하는 EngineerProfile 없음
- When: `getAgencyEngineerDetail(agencyUserId, engineerUserId=999)` 호출
- Then: `NoSuchElementException` 발생

---

### JUnit 5 통합 테스트 (Controller Layer — @WebMvcTest)

**파일**: `src/test/java/com/careflow/agency/controller/AgencyEngineerControllerTest.java`

#### TC-4. 인증된 AGENCY 역할 — 200 OK + 상세 반환
- Given: `@WithMockUser(roles = "AGENCY")`, Service Mock → 기사 상세 DTO 반환
- When: `GET /api/agencies/me/engineers/10`
- Then: status 200, JSON 필드 검증

#### TC-5. 타 대행사 기사 조회 — 403
- Given: Service가 `IllegalAccessException` 던지도록 Mock
- When: `GET /api/agencies/me/engineers/20`
- Then: status 403

#### TC-6. 존재하지 않는 기사 — 404
- Given: Service가 `NoSuchElementException` 던지도록 Mock
- When: `GET /api/agencies/me/engineers/999`
- Then: status 404
