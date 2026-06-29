# API 명세: 기사별 완료 건수 TOP 5 (Engineer Top 5)

## 기본 정보

| 항목 | 내용 |
|------|------|
| Method | GET |
| URI | `/api/agency/statistics/engineer-top5` |
| 인증 | 필수 (ROLE_AGENCY) |
| 설명 | 지정 기간 내 소속 기사 중 완료 건수 상위 5명을 반환한다 |

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

---

## 응답 (`List<AgencyStatisticsEngineerTop5Response>`)

```json
[
  { "rank": 1, "engineerName": "김현수", "completedCount": 128 },
  { "rank": 2, "engineerName": "박지영", "completedCount": 112 },
  { "rank": 3, "engineerName": "이정호", "completedCount": 98  },
  { "rank": 4, "engineerName": "최우진", "completedCount": 87  },
  { "rank": 5, "engineerName": "정우성", "completedCount": 76  }
]
```

| 필드 | 타입 | 설명 |
|------|------|------|
| rank | int | 순위 (1~5) |
| engineerName | String | 기사 이름 (users.name) |
| completedCount | long | 완료 건수 |

- 완료 건수 내림차순 정렬
- 5명 미만이면 실제 기사 수만큼만 반환
- `as_assignments.status = 'COMPLETED'` 기준 집계 (`assigned_at` 기간 필터)

---

## 집계 쿼리

```sql
SELECT u.name         AS engineer_name,
       COUNT(aa.assignment_id) AS completed_count
FROM as_assignments aa
JOIN users u ON aa.engineer_id = u.user_id
WHERE aa.agency_id = :agencyId
  AND aa.status    = 'COMPLETED'
  AND aa.assigned_at >= :from
  AND aa.assigned_at <  :to
GROUP BY aa.engineer_id, u.name
ORDER BY completed_count DESC
LIMIT 5
```

- `as_assignments` 기준: 배차 완료(COMPLETED) 건수
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

**케이스:**
1. `[engineer-top5] 성공: 인증 + 유효 날짜 → 200 + 배열 반환`
2. `[engineer-top5] 실패: 비인증 → 401`

### 통합 테스트 (`AgencyStatisticsIntegrationTest`)

**케이스:**
1. `[engineer-top5] H2: 기사 3명 · 완료 건수 5·3·1 → rank 1 name과 completedCount=5 검증`
2. `[engineer-top5] H2: 완료 없음 → 빈 배열`
3. `[engineer-top5] H2: 다른 대행사 기사 배차 → 포함 안 됨`
