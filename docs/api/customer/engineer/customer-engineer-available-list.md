# 고객용 수동 배정 - 후보 기사 목록 조회

> 브랜치: `feature/as_manualFlow`
> 작성일: 2026-06-30
> 관련 컨트롤러: `CustomerController` (`/api/customers/{customerId}`)

---

## 개요

고객(`CUSTOMER` 역할)이 A/S 접수 화면에서 `assignType=MANUAL`을 선택했을 때, 직접 기사를 고르기 위해
조건(지역/브랜드/보유 기술)에 맞는 후보 기사 목록을 조회하는 API.

프론트엔드 `CustomerAS.jsx`의 수동 배정 단계 — 브랜드 필터 칩 + 보유 기술 필터 칩 + 기사 카드 목록 UI에 대응한다.
현재는 `MOCK_ENGINEERS` 더미 데이터로만 동작 중이며, 이 API로 대체한다.

기존 `AgencyEngineerController`의 `/recommended` 추천 로직(LMS 이수 + 평점 정렬)을 참고하되,
대행사 1곳에 국한하지 않고 **전체 대행사 소속 기사**를 대상으로 한다는 점이 다르다.

---

## 기본 정보

| 항목 | 내용 |
|---|---|
| Method | `GET` |
| URI | `/api/customers/{customerId}/engineers/available` |
| 인증 | Bearer JWT (`CUSTOMER` 권한) |
| 책임 도메인 | `user` (컨트롤러) / `engineer` (조회 로직) |

## 인증 / 권한

- `@AuthenticationPrincipal CustomUserDetails`로 로그인 사용자 식별
- 경로의 `customerId`는 URI 구조 일치를 위한 선언일 뿐, 실제 조회는 `userDetails.getUserId()` 기준으로 처리(타 고객 정보 접근 방지)

## 요청

### Path Variables

| 변수 | 타입 | 설명 |
|---|---|---|
| `customerId` | Long | 로그인한 고객 ID (실제 인증은 JWT 기준) |

### Query Parameters

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `regionId` | Integer | ✅ | 방문 지역 ID (`regions.region_id`, depth=2 구 단위) — 고객이 A/S 접수 화면에서 선택한 방문 지역 |
| `brand` | String | ❌ | 브랜드 필터 (예: `LG`, `삼성`, `위니아`, `대우`). 미지정 시 전체 |
| `skill` | String | ❌ | 보유 기술(전문 카테고리명, 예: `냉장고`, `세탁기`, `에어컨`). 미지정 시 전체 |

### Request Body

없음

## 처리 흐름

1. `regionId`로 `EngineerServiceRegion`에 해당 지역을 등록한 기사만 후보로 좁힘
2. `EngineerProfile.isLmsCompleted = true`인 기사만 포함 (AUTO 배정과 동일 기준)
3. 소속 대행사가 없는 기사(`user.agency == null`)는 제외 — 배정 시 `agency` 필수이기 때문 (`AsRequestService.createAssignment` 참고)
4. `brand` 파라미터가 있으면 `EngineerExpertBrand.brandName`에 해당 브랜드를 보유한 기사만 필터
5. `skill` 파라미터가 있으면 `EngineerProfile.category.name`이 일치하는 기사만 필터
6. 평점(`avgRating`) 내림차순 정렬 후 반환

> 이 단계에서는 날짜/시간 가용성을 확인하지 않는다 — 화면 흐름상 기사 선택 후 별도로
> `GET /api/customers/{customerId}/engineers/{engineerId}/availability`로 가능 일정을 조회하기 때문.

## 응답

**Response Body** (`200 OK`): `List<CustomerEngineerSummaryResponse>`

```json
[
  {
    "engineerId": 12,
    "name": "김민수",
    "rating": 4.8,
    "brands": ["LG", "삼성"],
    "skills": "냉장고",
    "profileImageUrl": null
  }
]
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `engineerId` | Long | 기사 user_id |
| `name` | String | 기사 이름 |
| `rating` | Double | 평균 평점 (`avgRating`, null이면 0.0) |
| `brands` | List\<String\> | 보유 전문 브랜드 전체 목록 |
| `skills` | String | 전문 카테고리명 (기사당 1개) |
| `profileImageUrl` | String | 프로필 이미지 URL (없으면 null) |

조건에 맞는 기사가 없으면 빈 배열(`[]`)을 반환한다 (404 아님).

## 에러 응답

| 상태코드 | 원인 |
|---|---|
| 401 | JWT 없음 / 만료 / CUSTOMER 역할 아님 |
| 400 | `regionId` 누락 |
| 404 | `regionId`에 해당하는 지역 없음 |
