# API: 실시간 A/S 현황 통계 조회

## 개요

관리자(ADMIN)가 관리자 대시보드 상단에서 **필터 조건 없이 전체 A/S 요청을 상태별로 집계**한 실시간 카운트를 조회한다. 대시보드 상단 요약 카드(접수/진행/완료 등) 표출용이다.
위치: Admin A/S 현황 관리 페이지 상단 통계 영역.

> `GET /api/admin/as-requests`(전체 내역 페이징 조회)의 응답에도 동일한 `stats`가 포함되지만, 그쪽은 **날짜·지역 필터가 반영된** 통계인 반면, 본 API는 **아무 필터도 적용하지 않은 전체 누적 통계**라는 점이 다르다.

---

## 엔드포인트

```
GET /api/admin/as-requests/stats
```

---

## 인증/인가

- **필수 인증**: JWT Bearer 토큰
- **허용 역할**: `ADMIN`
- `SecurityConfig`의 `.requestMatchers("/api/admin/**").hasRole("ADMIN")`로 1차 차단, 컨트롤러 `checkAdminRole()`으로 2차 방어(이중 방어) → 아니면 `IllegalAccessException`

---

## 요청

### 경로 변수 / 쿼리 파라미터

- 없음.

### 요청 예시

```
GET /api/admin/as-requests/stats
Authorization: Bearer {accessToken}
```

---

## 알려진 제약 (구현 전 반드시 인지할 것)

- **응답은 `Map<String, Long>`** 형태이며, 키는 `AsStatus` enum의 **10개 상태값 전부**를 항상 포함한다. 해당 상태의 요청이 0건이어도 키가 누락되지 않고 `0`으로 채워진다(프론트에서 키 존재를 전제로 렌더링하므로 중요).
  - 상태값: `PENDING`, `AGENCY_RECEIVED`, `ASSIGNED`, `ACCEPTED`, `ENGINEER_DEPARTED`, `ENGINEER_ARRIVED`, `IN_PROGRESS`, `COMPLETED`, `PAID`, `CANCELLED`
- **집계 방식**: `AsRequestRepository.countGroupByStatusForAdmin(null, null, null)` — region/from/to 모두 `null`을 넘겨 **전체 요청**을 `GROUP BY status`로 집계한다.
- **초기화 후 덮어쓰기 로직**: 서비스에서 먼저 모든 상태값을 `0L`로 초기화한 뒤, 쿼리 결과(존재하는 상태만)를 덮어쓴다. 따라서 DB에 한 건도 없는 상태도 응답에 `0`으로 나타난다.
- 대행사(agency)·기간 조건이 전혀 없으므로 **CANCELLED, PAID 등 종료 상태도 모두 포함**된다.

---

## 응답

### 200 OK

```json
{
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
}
```

### 응답 필드 설명

| 필드(키) | 타입 | 설명 |
|---|---|---|
| `{AsStatus}` | long | 해당 상태의 전체 A/S 요청 건수 (10개 상태값 전부 포함, 없으면 0) |

### 에러 응답

| HTTP 상태 | 조건 |
|---|---|
| 401 Unauthorized | JWT 토큰 없음/만료 (`HttpStatusEntryPoint(UNAUTHORIZED)`) |
| 403 Forbidden | 인증되었으나 role != ADMIN (`SecurityConfig`의 `hasRole("ADMIN")`에서 차단 → `AuthorizationDeniedException` → 403) |

---

## 처리 로직 (Pipeline)

1. **검증**: `checkAdminRole()` — role != ADMIN 이면 `IllegalAccessException` (실제로는 SecurityConfig에서 403으로 먼저 차단됨)
2. **조회**: `AsRequestRepository.countGroupByStatusForAdmin(null, null, null)` → `List<Object[]>` (row = `[AsStatus, Long]`)
3. **Map 변환**: 10개 상태값 전부 `0L` 초기화 → 쿼리 결과로 덮어쓰기 (`convertStatsToMap`)
4. **반환**: `Map<String, Long>`

---

## 개발 구성요소

| 계층 | 클래스 / 메서드 |
|---|---|
| Controller | `com.careflow.admin.controller.AdminAsRequestController#getRealTimeStats` |
| Service | `com.careflow.admin.service.AdminAsRequestService#getRealTimeStats` |
| Repository | `com.careflow.as_request.repository.AsRequestRepository#countGroupByStatusForAdmin` |
| Enum | `com.careflow.common.enums.AsStatus` (10개 상태) |

---

## 테스트 명세

### JUnit 5 단위 테스트 (Service Layer, Mockito)

**파일**: `src/test/java/com/careflow/admin/service/AdminAsRequestServiceTest.java`

- TC-1. 정상 조회 — 리포지토리가 일부 상태만 반환해도, 응답 Map에는 **10개 상태값 전부**가 포함되는지 검증
- TC-2. 결과가 비어 있을 때(`countGroupByStatusForAdmin`이 빈 리스트) → 모든 상태값이 `0L`로 채워진 Map 반환
- TC-3. `countGroupByStatusForAdmin` 호출 시 인자가 `(null, null, null)`로 전달되는지 검증(전체 집계 보장)

### JUnit 5 컨트롤러 테스트 (`@WebMvcTest`)

**파일**: `src/test/java/com/careflow/admin/controller/AdminAsRequestControllerTest.java`

- TC-C-1. 인증된 ADMIN — 200 OK, 응답 JSON에 상태 키 존재 검증 (`$.PENDING`, `$.PAID` 등)
- TC-C-2. 인증 없음 → 401
- TC-C-3. ADMIN이 아닌 인증 사용자 → 403
