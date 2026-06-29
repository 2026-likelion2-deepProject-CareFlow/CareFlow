# API 명세: 시간대별 접수 현황 (Hourly)

## 기본 정보

| 항목 | 내용 |
|------|------|
| Method | GET |
| URI | `/api/agency/statistics/hourly` |
| 인증 | 필수 (ROLE_AGENCY) |
| 설명 | 지정 기간 내 3시간 단위 시간대별 A/S 접수 건수를 반환한다 (바차트용) |

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

## 응답 (`List<AgencyStatisticsHourlyResponse>`)

```json
[
  { "timeSlot": "00-03시", "count": 5  },
  { "timeSlot": "03-06시", "count": 12 },
  { "timeSlot": "06-09시", "count": 65 },
  { "timeSlot": "09-12시", "count": 98 },
  { "timeSlot": "12-15시", "count": 110},
  { "timeSlot": "15-18시", "count": 85 },
  { "timeSlot": "18-21시", "count": 45 },
  { "timeSlot": "21-24시", "count": 8  }
]
```

| 필드 | 타입 | 설명 |
|------|------|------|
| timeSlot | String | 시간대 레이블 (HH-HH시, 3시간 단위 고정 8개 슬롯) |
| count | long | 해당 시간대 접수 건수 합계 |

- 항상 8개 슬롯 모두 반환 (접수가 없는 슬롯은 count=0)
- 슬롯 순서: 00-03시, 03-06시, 06-09시, 09-12시, 12-15시, 15-18시, 18-21시, 21-24시

---

## 집계 쿼리

```sql
-- 시간대(3시간 단위) 별 접수 건수
SELECT (HOUR(created_at) / 3) AS hour_slot,
       COUNT(*)                AS cnt
FROM as_requests
WHERE agency_id = :agencyId
  AND created_at >= :from
  AND created_at < :to
GROUP BY (HOUR(created_at) / 3)
ORDER BY hour_slot
```

- `HOUR() / 3` → 정수 나눗셈으로 슬롯 인덱스 0~7 계산
- Native SQL로 구현
- 서비스에서 결과를 0~7 인덱스 기반으로 8개 슬롯 배열로 변환 후 레이블 적용

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
1. `[hourly] 성공: 인증 + 유효 날짜 → 200 + 8개 슬롯 배열`
2. `[hourly] 실패: 비인증 → 401`

### 통합 테스트 (`AgencyStatisticsIntegrationTest`)

**케이스:**
1. `[hourly] H2: 09시·14시·20시에 각 1건 삽입 → 09-12시 슬롯 1, 12-15시 슬롯 1, 18-21시 슬롯 1, 나머지 0`
2. `[hourly] H2: 항상 8개 슬롯 반환 검증`
