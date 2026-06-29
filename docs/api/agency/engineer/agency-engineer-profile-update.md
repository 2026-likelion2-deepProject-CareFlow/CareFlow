# API: 소속 기사 프로필 수정 (대행사 관리자용)

## 개요

대행사 관리자가 소속 기사의 활동 지역, 전문 브랜드, 전문 가전 카테고리를 수정한다.
기사 본인의 `PATCH /api/engineers/me/profile`과 별개 권한으로 분리한다.

---

## 엔드포인트

```
PATCH /api/agencies/me/engineers/{engineerUserId}/profile
```

---

## 인증/인가

- **필수 인증**: JWT Bearer 토큰
- **허용 역할**: `AGENCY`
- 수정 대상 기사가 로그인한 대행사 관리자와 동일 대행사 소속인지 검증한다.

---

## 요청

### 경로 파라미터

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `engineerUserId` | Long | Y | 수정할 기사의 user_id |

### 요청 바디 (application/json)

```json
{
  "categoryId": 5,
  "expertBrands": ["삼성", "LG"],
  "serviceRegionIds": [101, 102, 103]
}
```

### 요청 필드 설명

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `categoryId` | Integer | N | 전문 가전 카테고리 ID (소분류 depth=2만 허용). null이면 변경 없음 |
| `expertBrands` | List\<String\> | N | 전문 브랜드 목록 (전체 교체). null 또는 빈 배열이면 변경 없음 |
| `serviceRegionIds` | List\<Integer\> | N | 활동 지역 ID 목록 (전체 교체, 구 단위 depth=2만 허용). null 또는 빈 배열이면 변경 없음 |

> 모든 필드가 선택적이며, 전달된 필드만 수정한다.

---

## 응답

### 200 OK

```json
{
  "engineerUserId": 10,
  "name": "홍길동",
  "categoryId": 5,
  "categoryName": "냉장고",
  "skillLevel": "INTERMEDIATE",
  "isLmsCompleted": true,
  "introduction": "냉장고 전문 수리기사입니다.",
  "profileImageUrl": "https://...",
  "avgRating": 4.80,
  "totalReviews": 32,
  "expertBrands": ["삼성", "LG"],
  "serviceRegionIds": [101, 102, 103],
  "createdAt": "2025-01-10T09:00:00",
  "updatedAt": "2026-06-26T10:00:00"
}
```

### 에러 응답

| HTTP 상태 | 조건 |
|---|---|
| 400 Bad Request | categoryId가 depth=2(소분류)가 아닌 경우, serviceRegionIds가 depth=2(구 단위)가 아닌 경우 |
| 401 Unauthorized | JWT 토큰 없음 또는 만료 |
| 403 Forbidden | AGENCY 역할이 아닌 경우, 또는 타 대행사 소속 기사 수정 시도 |
| 404 Not Found | `engineerUserId`에 해당하는 기사 프로필이 없는 경우 |

---

## 구현 위치

| 계층 | 클래스 |
|---|---|
| Controller | `com.careflow.agency.controller.AgencyEngineerController` |
| Service | `com.careflow.agency.service.AgencyEngineerService` |
| Repository | `com.careflow.engineer.repository.EngineerProfileRepository`, `EngineerExpertBrandRepository`, `EngineerServiceRegionRepository` |
| DTO (요청) | `com.careflow.agency.dto.request.AgencyEngineerProfileUpdateRequest` |
| DTO (응답) | `com.careflow.agency.dto.response.AgencyEngineerDetailResponse` |

---

## 테스트 명세

### JUnit 5 단위 테스트 (Service Layer)

**파일**: `src/test/java/com/careflow/agency/service/AgencyEngineerServiceTest.java`

#### TC-1. 카테고리·브랜드·지역 모두 수정 — 정상
- Given: agencyId=1 소속 engineerUserId=10, 유효한 categoryId/brands/regionIds Mock
- When: `updateAgencyEngineerProfile(agencyUserId, 10, request)` 호출
- Then: 반환 DTO의 categoryId, expertBrands, serviceRegionIds 수정값 반영 확인

#### TC-2. 카테고리만 수정 — 나머지 필드 유지
- Given: request.categoryId=7, 나머지 null
- When: `updateAgencyEngineerProfile(agencyUserId, 10, request)` 호출
- Then: categoryId만 변경, expertBrands·serviceRegionIds는 기존값 유지

#### TC-3. categoryId가 depth=1(대분류) — 예외
- Given: categoryId에 해당하는 ApplianceCategory.depth == 1
- When: `updateAgencyEngineerProfile(...)` 호출
- Then: `IllegalArgumentException` 발생

#### TC-4. serviceRegionId가 depth=1(시 단위) — 예외
- Given: regionId에 해당하는 Regions.depth == 1
- When: `updateAgencyEngineerProfile(...)` 호출
- Then: `IllegalArgumentException` 발생

#### TC-5. 타 대행사 기사 수정 시도 — 예외
- Given: 대상 기사의 agency.id != 로그인 대행사의 id
- When: `updateAgencyEngineerProfile(...)` 호출
- Then: `IllegalAccessException` 발생

---

### JUnit 5 통합 테스트 (Controller Layer — @WebMvcTest)

**파일**: `src/test/java/com/careflow/agency/controller/AgencyEngineerControllerTest.java`

#### TC-6. 정상 수정 — 200 OK
- Given: `@WithMockUser(roles = "AGENCY")`, 유효한 요청 바디, Service Mock → 수정된 DTO 반환
- When: `PATCH /api/agencies/me/engineers/10/profile`
- Then: status 200, 응답 JSON 수정값 확인

#### TC-7. 타 대행사 기사 수정 — 403
- Given: Service가 `IllegalAccessException` 던지도록 Mock
- When: `PATCH /api/agencies/me/engineers/20/profile`
- Then: status 403

#### TC-8. 잘못된 카테고리 — 400
- Given: Service가 `IllegalArgumentException` 던지도록 Mock
- When: `PATCH /api/agencies/me/engineers/10/profile` (depth=1 categoryId)
- Then: status 400
