# API 명세: 이달의 요약 (Monthly Summary)

## 기본 정보

| 항목 | 내용 |
|------|------|
| Method | GET |
| URI | `/api/agency/statistics/monthly-summary` |
| 인증 | 필수 (ROLE_AGENCY) |
| 설명 | 현재 달(요청 시점 기준)의 주요 하이라이트 지표를 반환한다 |

---

## 요청

Query Parameter 없음 (서버가 현재 월 자동 산정)

### 인증 파라미터

```java
@AuthenticationPrincipal CustomUserDetails userDetails
```

---

## 응답 (`AgencyStatisticsMonthlySummaryResponse`)

```json
{
  "topDayOfWeek":           "금요일 (212건)",
  "topHourSlot":            "10-11시 (186건)",
  "topRatedEngineerName":   "김현수 기사 (4.9)",
  "customerSatisfactionRate": 86.3
}
```

| 필드 | 타입 | 설명 |
|------|------|------|
| topDayOfWeek | String | 접수 건수가 가장 많은 요일 + 건수 (예: "금요일 (212건)") |
| topHourSlot | String | 접수 건수가 가장 많은 1시간 단위 시간대 + 건수 (예: "10-11시 (186건)") |
| topRatedEngineerName | String | 이달 평균 평점이 가장 높은 기사 이름 + 평점 (예: "김현수 기사 (4.9)") |
| customerSatisfactionRate | double | 평점 4점 이상 리뷰 비율 (%, 소수점 1자리) |

- 데이터 없을 경우:
  - `topDayOfWeek` / `topHourSlot` → `"데이터 없음"`
  - `topRatedEngineerName` → `"데이터 없음"`
  - `customerSatisfactionRate` → `0.0`

---

## 집계 쿼리

### 최다 접수 요일
```sql
SELECT DAYOFWEEK(created_at) AS dow, COUNT(*) AS cnt
FROM as_requests
WHERE agency_id = :agencyId
  AND created_at >= :monthStart
  AND created_at <  :monthEnd
GROUP BY DAYOFWEEK(created_at)
ORDER BY cnt DESC
LIMIT 1
```
- DAYOFWEEK: 1=일, 2=월, 3=화, 4=수, 5=목, 6=금, 7=토
- 서비스에서 int → 한글 요일명 변환

### 최다 접수 시간대 (1시간 단위)
```sql
SELECT HOUR(created_at) AS hr, COUNT(*) AS cnt
FROM as_requests
WHERE agency_id = :agencyId
  AND created_at >= :monthStart
  AND created_at <  :monthEnd
GROUP BY HOUR(created_at)
ORDER BY cnt DESC
LIMIT 1
```
- 서비스에서 "HH-(HH+1)시 (N건)" 포맷으로 변환

### 최고 평점 기사
```sql
SELECT u.name AS engineer_name, AVG(r.rating) AS avg_rating
FROM reviews r
JOIN as_requests ar ON r.request_id = ar.request_id
JOIN users u        ON r.engineer_id = u.user_id
WHERE ar.agency_id = :agencyId
  AND r.is_visible = 1
  AND r.created_at >= :monthStart
  AND r.created_at <  :monthEnd
GROUP BY r.engineer_id, u.name
ORDER BY avg_rating DESC
LIMIT 1
```

### 고객 만족도 (평점 4점 이상 비율)
```sql
SELECT COUNT(CASE WHEN r.rating >= 4 THEN 1 END) AS satisfied,
       COUNT(*) AS total
FROM reviews r
JOIN as_requests ar ON r.request_id = ar.request_id
WHERE ar.agency_id = :agencyId
  AND r.is_visible = 1
  AND r.created_at >= :monthStart
  AND r.created_at <  :monthEnd
```

---

## 오류 응답

| 상황 | HTTP | 메시지 |
|------|------|--------|
| AGENCY 역할 아님 | 403 | "대행사 계정만 접근 가능합니다." |

---

## 테스트 명세

### 단위 테스트 (`AgencyStatisticsControllerTest`)

**케이스:**
1. `[monthly-summary] 성공: 인증 → 200 + 4개 필드 존재`
2. `[monthly-summary] 실패: 비인증 → 401`

### 통합 테스트 (`AgencyStatisticsIntegrationTest`)

**케이스:**
1. `[monthly-summary] H2: 이번 달 데이터 삽입 → topDayOfWeek 값이 "데이터 없음" 아님 검증`
2. `[monthly-summary] H2: 데이터 없음 → 모든 필드 기본값 반환`
3. `[monthly-summary] H2: 리뷰 5건 중 4건이 4점 이상 → satisfactionRate = 80.0`
