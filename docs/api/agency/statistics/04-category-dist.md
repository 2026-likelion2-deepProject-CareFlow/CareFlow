# API 명세: 접수 유형 분포 (Category Distribution)

## 기본 정보

| 항목 | 내용 |
|------|------|
| Method | GET |
| URI | `/api/agency/statistics/category-dist` |
| 인증 | 필수 (ROLE_AGENCY) |
| 설명 | 지정 기간 내 가전 카테고리별 접수 건수 및 비율을 반환한다 (도넛차트용) |

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

## 응답 (`List<AgencyStatisticsCategoryDistResponse>`)

```json
[
  { "categoryName": "에어컨", "count": 564, "percentage": 45.2 },
  { "categoryName": "세탁기", "count": 235, "percentage": 18.8 },
  { "categoryName": "TV",    "count": 158, "percentage": 12.7 }
]
```

| 필드 | 타입 | 설명 |
|------|------|------|
| categoryName | String | 가전 카테고리명 (appliance_categories.name) |
| count | long | 해당 카테고리 접수 건수 |
| percentage | double | 전체 대비 비율 (소수점 1자리, %) |

- 건수 내림차순 정렬
- 전체 접수 0건일 경우 percentage = 0.0

---

## 집계 쿼리

```sql
-- 가전 카테고리별 접수 건수
SELECT ac.name       AS category_name,
       COUNT(ar.request_id) AS cnt
FROM as_requests ar
JOIN appliances           a   ON ar.appliance_id = a.appliance_id
JOIN appliance_categories ac  ON a.category_id   = ac.category_id
WHERE ar.agency_id  = :agencyId
  AND ar.created_at >= :from
  AND ar.created_at <  :to
GROUP BY ac.category_id, ac.name
ORDER BY cnt DESC
```

- 3-table JOIN: `as_requests → appliances → appliance_categories`
- Native SQL로 구현
- 서비스에서 전체 합산 후 percentage 계산

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
1. `[category-dist] 성공: 인증 + 유효 날짜 → 200 + JSON 배열`
2. `[category-dist] 실패: 비인증 → 401`

### 통합 테스트 (`AgencyStatisticsIntegrationTest`)

**케이스:**
1. `[category-dist] H2: 에어컨 2건 · 냉장고 1건 삽입 → 에어컨 비율 66.7%, 냉장고 33.3%`
2. `[category-dist] H2: 빈 기간 → 빈 배열 반환`
3. `[category-dist] H2: 다른 대행사 데이터 → 집계 제외`
