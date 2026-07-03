# API: 전체 A/S 처리 내역 페이징 조회

## 개요

관리자(ADMIN)가 관리자 A/S 현황 관리 페이지에서 **전체 A/S 요청 내역을 페이징 + 동적 다중 필터(상태·지역·기간)**로 조회한다. 응답에는 리스트(`content`)와 함께, **현재 필터(지역·기간)가 반영된 상태별 통계(`stats`)**가 동봉되어 상단 탭 카운트와 하단 목록의 정합성을 맞춘다.
위치: Admin A/S 현황 관리 페이지 — 상단 상태 탭 + 하단 페이징 테이블.

---

## 엔드포인트

```
GET /api/admin/as-requests?status=IN_PROGRESS&region=강남구&from=2026-06-01&to=2026-06-30&page=0&size=10
```

---

## 인증/인가

- **필수 인증**: JWT Bearer 토큰
- **허용 역할**: `ADMIN`
- `SecurityConfig`의 `hasRole("ADMIN")` 1차 차단 + 컨트롤러 `checkAdminRole()` 2차 방어 → 아니면 `IllegalAccessException`

---

## 요청

### 쿼리 파라미터

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `status` | String | X | A/S 상태 필터. `AsStatus` enum 이름(대소문자 무관, 내부에서 `toUpperCase()`). 없으면 전체 |
| `region` | String | X | 방문 지역명 **부분 일치**(`LIKE %region%`, `visit_region.name` 기준). 빈 문자열은 null로 취급 |
| `from` | String | X | 접수일 시작(`yyyy-MM-dd`). 해당일 `00:00:00`부터 포함 |
| `to` | String | X | 접수일 종료(`yyyy-MM-dd`). **종료일 당일 포함**(내부적으로 `to+1일 00:00:00` 미만 조건으로 처리) |
| `page` | int | X | 페이지 번호 (기본값 0) |
| `size` | int | X | 페이지 크기 (기본값 10) |

### 요청 예시

```
GET /api/admin/as-requests?status=in_progress&region=강남&from=2026-06-01&to=2026-06-30&page=0&size=10
Authorization: Bearer {accessToken}
```

---

## 알려진 제약 (구현 전 반드시 인지할 것)

- **`stats`는 status 필터를 반영하지 않는다**: 리스트(`content`)는 status·region·기간 필터를 모두 적용하지만, `stats`는 **region·기간 필터만 적용하고 status 필터는 제외**한다. 이는 사용자가 특정 상태 탭을 눌러 목록을 필터링해도, **다른 상태 탭의 카운트가 그대로 보이게** 하기 위한 의도된 설계다.
- **`requestCode` 포맷**: `"AS-" + createdAt의 연도 + "-" + request_id 4자리 zero-padding`. 예: `created_at=2026-...`, `request_id=891` → `"AS-2026-0891"`. request_id가 4자리를 초과하면 자릿수 그대로 늘어난다. 실제 PK는 Long이며 이 포맷은 표시용이다.
- **`applianceName`**: `as_request → appliance → category → name` 경로(`appliance_categories.name`)로 조회한다. (`Appliance`에는 `brand`/`modelName`만 있으므로 "냉장고" 같은 표시명은 카테고리명 사용.)
- **`customerName`**: `as_request → customer(User) → name`.
- **`symptom`**: `as_request → symptom → symptomName`(화면 표시용 한글명, 예: "냉방 불량").
- **`region`**: `as_request → visitRegion → name`(depth=2 구 단위 지역명).
- **`status` 파싱**: `AsStatus.valueOf(status.toUpperCase())` — 유효하지 않은 값 → `IllegalArgumentException` (400).
- **날짜 파싱**: `yyyy-MM-dd` 외 형식 → `IllegalArgumentException` (400). `to`는 `endOfDay = to.plusDays(1).atStartOfDay()`로 변환하여 `createdAt < endOfDay` 조건으로 당일을 포함한다(H2·MySQL 양쪽 호환).
- **정렬 순서**: `created_at DESC`(최신 접수 우선).
- **N+1 방지**: 리스트 쿼리(`searchAllForAdmin`)에서 `customer`, `appliance`, `appliance.category`, `symptom`, `visitRegion`를 `JOIN FETCH`. 페이징 count 쿼리는 `visitRegion`만 `JOIN`.
- **빈 결과**: 조건에 맞는 건이 없으면 `content: []`, `totalElements: 0`으로 반환(404 아님).

---

## 응답

### 200 OK

```json
{
  "stats": {
    "PENDING": 12,
    "AGENCY_RECEIVED": 5,
    "ASSIGNED": 8,
    "ACCEPTED": 3,
    "ENGINEER_DEPARTED": 1,
    "ENGINEER_ARRIVED": 2,
    "IN_PROGRESS": 4,
    "COMPLETED": 20,
    "PAID": 45,
    "CANCELLED": 6
  },
  "content": [
    {
      "requestId": 891,
      "requestCode": "AS-2026-0891",
      "customerName": "김철수",
      "applianceName": "냉장고",
      "symptom": "냉방 불량",
      "region": "강남구",
      "status": "IN_PROGRESS",
      "createdAt": "2026-06-15"
    }
  ],
  "totalElements": 4,
  "totalPages": 1,
  "number": 0,
  "size": 10
}
```

### 응답 필드 설명

| 필드 | 타입 | 설명 |
|---|---|---|
| `stats` | Map<String, Long> | region·기간 필터가 반영된 상태별 카운트(status 필터 제외). 10개 상태값 전부 포함 |
| `content[].requestId` | Long | A/S 요청 PK (`request_id`) |
| `content[].requestCode` | String | `"AS-{연도}-{PK 4자리}"` 표시용 접수번호 |
| `content[].customerName` | String | 고객명 (`users.name`) |
| `content[].applianceName` | String | 가전 카테고리명 (`appliance_categories.name`) |
| `content[].symptom` | String | 증상 표시명 (`symptoms.symptom_name`) |
| `content[].region` | String | 방문 지역명 (`regions.name`) |
| `content[].status` | String | A/S 상태 (`AsStatus` enum 이름) |
| `content[].createdAt` | String | 접수일 (`yyyy-MM-dd`) |
| `totalElements` | long | 필터 적용 후 전체 건수 |
| `totalPages` | int | 전체 페이지 수 |
| `number` | int | 현재 페이지 번호(0-base) |
| `size` | int | 페이지 크기 |

### 에러 응답

| HTTP 상태 | 조건 |
|---|---|
| 401 Unauthorized | JWT 토큰 없음/만료 |
| 403 Forbidden | 인증되었으나 role != ADMIN (`hasRole("ADMIN")` 차단) |
| 400 Bad Request | 유효하지 않은 `status` 값, 또는 `from`/`to` 날짜 형식 오류(`yyyy-MM-dd` 아님) |

---

## 처리 로직 (Pipeline)

1. **검증**: `checkAdminRole()` → 아니면 `IllegalAccessException`
2. **파라미터 파싱**: `parseStatus(status)`(빈 값 → null, 잘못된 값 → 400), `parseDate(from, false)` / `parseDate(to, true)`(빈 값 → null, 형식 오류 → 400), `region` 빈 문자열 → null 방어
3. **리스트 조회**: `AsRequestRepository.searchAllForAdmin(status, region, startOfDay, endOfDay, pageable)` — 동적 필터 + `JOIN FETCH` + `created_at DESC`
4. **통계 조회(status 제외)**: `countGroupByStatusForAdmin(region, startOfDay, endOfDay)` → `convertStatsToMap`으로 10개 상태 전부 채움
5. **DTO 매핑**: `AdminAsRequestItem.from(AsRequest)` — `requestCode` 포맷 조립, 각 연관 엔티티에서 표시명 추출, `createdAt` `yyyy-MM-dd` 포맷
6. **반환**: `AdminAsRequestListResponse(stats, content, totalElements, totalPages, number, size)`

---

## 개발 구성요소

| 계층 | 클래스 |
|---|---|
| Controller | `com.careflow.admin.controller.AdminAsRequestController#getAsRequests` |
| Service | `com.careflow.admin.service.AdminAsRequestService#searchAsRequests` |
| Repository | `com.careflow.as_request.repository.AsRequestRepository#searchAllForAdmin`, `#countGroupByStatusForAdmin` |
| Response DTO | `com.careflow.admin.dto.response.AdminAsRequestListResponse` (+ 중첩 `AdminAsRequestItem`) |

---

## 테스트 명세

### JUnit 5 단위 테스트 (Service Layer, Mockito)

**파일**: `src/test/java/com/careflow/admin/service/AdminAsRequestServiceTest.java`

- TC-1. 정상 조회 — 3건 반환 시 `content` size 3, 필드 매핑 정상(`requestCode` = `AS-{연도}-{4자리}`)
- TC-2. `stats`는 status 필터 무시 — status를 넘겨도 `countGroupByStatusForAdmin`에는 status가 전달되지 않는지 검증(다른 탭 카운트 유지)
- TC-3. `status` 파싱 — 소문자 입력(`in_progress`)이 `AsStatus.IN_PROGRESS`로 정규화되는지, 잘못된 값 → `IllegalArgumentException`
- TC-4. 날짜 파싱 — `to`가 `+1일 00:00:00`으로 변환되어 당일 포함되는지, 잘못된 형식 → `IllegalArgumentException`
- TC-5. `region` 빈 문자열 → null 처리(전체 지역 조회)
- TC-6. 빈 결과 — `content: []`, `totalElements: 0` 반환

### JUnit 5 통합 테스트 (H2 DB, `@SpringBootTest` + `@ActiveProfiles("local")`)

**파일**: `src/test/java/com/careflow/admin/service/AdminAsRequestServiceIntegrationTest.java`

- TC-I-1. 지역 부분 일치 — "강남"으로 "강남구" 매칭되는지 검증
- TC-I-2. 기간 경계 — `to` 당일 접수 건이 포함되고, 익월 건은 제외되는지 검증
- TC-I-3. 정렬 순서 — `created_at DESC` 반환 검증
- TC-I-4. `applianceName`/`symptom`/`region` — 실제 INSERT 후 표시명 매핑 정상 확인

### JUnit 5 컨트롤러 테스트 (`@WebMvcTest`)

**파일**: `src/test/java/com/careflow/admin/controller/AdminAsRequestControllerTest.java`

- TC-C-1. 인증된 ADMIN — 200 OK, `$.content`, `$.stats`, `$.totalElements` 구조 검증
- TC-C-2. 인증 없음 → 401
- TC-C-3. ADMIN 아님 → 403
- TC-C-4. 잘못된 `status` 파라미터 → 400
