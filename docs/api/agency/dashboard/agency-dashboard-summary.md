# 대행사 대시보드 요약 통계 API 설계서

## 개요

대행사 관리자가 작업 관리 대시보드에 접근했을 때,  
본인 소속 대행사의 A/S 요청 현황을 **상태별 건수** 형태로 요약해 돌려주는 API.

- 리스트 조회가 아닌 `COUNT` 집계 결과만 반환한다.
- 오늘 날짜 기준(`created_at` 날짜 = 오늘)과 전체 누적 기준을 구분해서 제공한다.

---

## 엔드포인트

```
GET /api/as-requests/agency/dashboard-summary
```

### 인증
- `Authorization: Bearer <accessToken>` 헤더 필수
- `role = AGENCY` 가 아닌 경우 `401 Unauthorized`

### 요청 파라미터
없음 (로그인 정보에서 소속 agency_id를 추출)

---

## 응답

### 200 OK

```json
{
  "totalCount": 120,
  "todayNewCount": 5,
  "todayAssignedCount": 3,
  "todayAcceptedCount": 1,
  "todayCancelledCount": 1
}
```

| 필드 | 타입 | 설명 |
|------|------|------|
| `totalCount` | `long` | 대행사로 접수된 A/S 요청 **전체 누적** 건수 |
| `todayNewCount` | `long` | 오늘(`created_at` 날짜 = 오늘) 신규 접수된 A/S 요청 건수 |
| `todayAssignedCount` | `long` | 오늘 접수된 요청 중 `status = ASSIGNED` 건수 (기사 배정 대기 중) |
| `todayAcceptedCount` | `long` | 오늘 접수된 요청 중 `status = ACCEPTED` 건수 (기사 배정 승인 완료) |
| `todayCancelledCount` | `long` | 오늘 접수된 요청 중 `status = CANCELLED` 건수 (고객 취소) |

### 에러 응답

| HTTP 상태 | 발생 조건 |
|-----------|-----------|
| `401 Unauthorized` | `role != AGENCY` |
| `404 Not Found` | 로그인 유저 정보 없음 |
| `500 Internal Server Error` | 소속 대행사 정보 없음 등 서버 내부 오류 |

---

## 구현 위치

기존 대행사 A/S 요청 관련 파일에 통합한다.

| 레이어 | 파일 | 변경 내용 |
|--------|------|-----------|
| **DTO** | `as_request/dto/AgencyDashboardSummaryResponse.java` | 신규 생성 — 응답 record |
| **Repository** | `as_request/repository/AsRequestRepository.java` | 집계 쿼리 메서드 2개 추가 |
| **Service** | `as_request/service/AgencyAsRequestService.java` | `getDashboardSummary()` 메서드 추가 |
| **Controller** | `as_request/controller/AsRequestController.java` | `GET /agency/dashboard-summary` 엔드포인트 추가 |

---

## 쿼리 설계

### 1. 전체 누적 건수
```sql
SELECT COUNT(*)
FROM as_requests
WHERE agency_id = :agencyId
```

### 2. 오늘 신규 접수 건수
```sql
SELECT COUNT(*)
FROM as_requests
WHERE agency_id = :agencyId
  AND DATE(created_at) = CURRENT_DATE
```

### 3. 오늘 접수 중 상태별 건수 (ASSIGNED / ACCEPTED / CANCELLED)
```sql
SELECT COUNT(*)
FROM as_requests
WHERE agency_id = :agencyId
  AND DATE(created_at) = CURRENT_DATE
  AND status = :status
```

> **구현 전략**: 위 3·4·5번 쿼리는 동일한 `DATE(created_at) = 오늘` 조건에 `status` 만 다르므로,
> 하나의 JPQL `COUNT` 쿼리에 `status` 파라미터를 넣어 재사용한다.
> 즉, Repository에 추가되는 메서드는 2개(전체 카운트 / 날짜+상태 카운트)이다.

---

## 권한 검증 흐름

기존 `AgencyAsRequestService.extractAgencyId()` 헬퍼 메서드를 그대로 재사용한다.

```
1. CustomUserDetails.getRole() == AGENCY 확인
2. users 테이블에서 agency_id 추출
3. 추출한 agencyId 로 집계 쿼리 실행
```

---

## 주의 사항

- `DATE(created_at)` 비교는 H2(인메모리 테스트 DB)와 MySQL 모두 지원한다.
  JPQL에서는 `FUNCTION('DATE', r.createdAt)` 대신 `CAST(r.createdAt AS LocalDate)` 방식 또는
  `r.createdAt >= :startOfDay AND r.createdAt < :endOfDay` 범위 조건으로 작성하면
  양쪽 DB에서 안전하게 동작한다. → **범위 조건 방식**을 채택한다.
- `todayAssignedCount`, `todayAcceptedCount`, `todayCancelledCount` 는
  오늘 신규 접수(`created_at` 기준)된 요청 중 현재 해당 status 인 건수다.
  기사 배정 자체가 내일 이루어졌어도 접수가 오늘이면 집계 대상에 포함된다.
- 기존 `extractAgencyId()` 가 `throws IllegalAccessException` 을 선언하므로,
  새 서비스 메서드도 동일하게 선언한다.
