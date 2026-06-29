# API 명세: 일별 접수·완료 추이 (Daily Trend)

## 기본 정보

| 항목 | 내용 |
|------|------|
| Method | GET |
| URI | `/api/agency/statistics/daily-trend` |
| 인증 | 필수 (ROLE_AGENCY) |
| 설명 | 지정 기간 내 날짜별 접수 건수 및 완료 건수 목록을 반환한다 (라인차트용) |

---

## 요청

### Query Parameters (`AgencyStatisticsDateRangeRequest`)

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| dateFrom | LocalDate | Y | 조회 시작일 |
| dateTo | LocalDate | Y | 조회 종료일 (포함) |

### 인증 파라미터

```java
@AuthenticationPrincipal CustomUserDetails userDetails
```

- `userDetails.getAgencyId()` → agency_id 필터

---

## 응답 (`List<AgencyStatisticsDailyTrendResponse>`)

```json
[
  { "date": "06.01", "receiptCount": 38, "completedCount": 32 },
  { "date": "06.02", "receiptCount": 41, "completedCount": 35 }
]
```

| 필드 | 타입 | 설명 |
|------|------|------|
| date | String | 날짜 문자열 (MM.dd 포맷) |
| receiptCount | long | 해당 일 접수 건수 (created_at 기준) |
| completedCount | long | 해당 일 완료 건수 (status IN COMPLETED, PAID, updated_at 기준) |

- 접수가 없는 날짜는 목록에서 제외 (0건 날짜 미포함)
- `date` 오름차순 정렬

---

## 집계 쿼리

```sql
-- 날짜별 접수 건수
SELECT DATE(created_at) AS date_str,
       COUNT(*)         AS receipt_count,
       SUM(CASE WHEN status IN ('COMPLETED','PAID') THEN 1 ELSE 0 END) AS completed_count
FROM as_requests
WHERE agency_id = :agencyId
  AND created_at >= :from
  AND created_at < :to
GROUP BY DATE(created_at)
ORDER BY date_str
```

- `DATE()` 함수 → H2(MySQL 모드) 및 MySQL 양쪽 호환
- Native SQL로 구현 (`AgencyStatisticsQueryRepository`)

---

## 오류 응답

| 상황 | HTTP | 메시지 |
|------|------|--------|
| AGENCY 역할 아님 | 403 | "대행사 계정만 접근 가능합니다." |
| dateFrom > dateTo | 400 | "시작일은 종료일보다 이전이어야 합니다." |

---

## 테스트 명세

### 단위 테스트 (`AgencyStatisticsControllerTest`)

- `@WebMvcTest` + `@MockitoBean AgencyStatisticsService`

**케이스:**
1. `[daily-trend] 성공: AGENCY 인증 + 유효 날짜 → 200 + JSON 배열 반환`
2. `[daily-trend] 실패: 비인증 → 401`
3. `[daily-trend] 실패: dateTo 누락 → 400`

### 통합 테스트 (`AgencyStatisticsIntegrationTest`)

- H2에 날짜 분산 데이터 삽입 후 범위 조회

**케이스:**
1. `[daily-trend] H2: 3일에 걸친 접수 데이터 → 날짜별 건수 정확히 반환`
2. `[daily-trend] H2: 조회 범위 외 데이터 → 포함되지 않음`
3. `[daily-trend] H2: 빈 기간 → 빈 배열 반환`
