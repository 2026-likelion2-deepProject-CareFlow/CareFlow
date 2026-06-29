# API 명세: 상태별 건수 (Status Count)

## 기본 정보

| 항목 | 내용 |
|------|------|
| Method | GET |
| URI | `/api/agency/statistics/status-count` |
| 인증 | 필수 (ROLE_AGENCY) |
| 설명 | 지정 기간 내 A/S 요청의 상태별 건수와 전체 대비 비율을 반환한다 |

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

## 응답 (`List<AgencyStatisticsStatusCountResponse>`)

```json
[
  { "statusLabel": "접수",      "count": 1248, "percentage": 100.0 },
  { "statusLabel": "배차 완료", "count": 1156, "percentage": 92.6  },
  { "statusLabel": "작업 중",   "count": 423,  "percentage": 33.9  },
  { "statusLabel": "작업 완료", "count": 1086, "percentage": 87.0  },
  { "statusLabel": "취소",      "count": 78,   "percentage": 6.3   }
]
```

| 필드 | 타입 | 설명 |
|------|------|------|
| statusLabel | String | 한글 상태 레이블 |
| count | long | 해당 상태 건수 |
| percentage | double | 전체 접수(PENDING 이상) 대비 비율 (%) |

### 상태 그룹 매핑 (AsStatus → 레이블)

| 레이블 | 포함 AsStatus 값 |
|--------|----------------|
| 접수 | 전체 (PENDING 이상) → 분모 기준 |
| 배차 완료 | ASSIGNED, ACCEPTED, ENGINEER_DEPARTED, ENGINEER_ARRIVED, IN_PROGRESS, COMPLETED, PAID |
| 작업 중 | IN_PROGRESS |
| 작업 완료 | COMPLETED, PAID |
| 취소 | CANCELLED |

---

## 집계 쿼리 (JPQL)

```jpql
SELECT r.status AS status, COUNT(r) AS cnt
FROM AsRequest r
WHERE r.agency.id = :agencyId
  AND r.createdAt >= :from
  AND r.createdAt < :to
GROUP BY r.status
```

- 서비스에서 enum별 결과를 그룹 레이블로 집계
- 전체 접수 건수는 모든 그룹 합산으로 계산

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
1. `[status-count] 성공: 인증 + 유효 날짜 → 200 + JSON 배열`
2. `[status-count] 실패: 비인증 → 401`

### 통합 테스트 (`AgencyStatisticsIntegrationTest`)

**케이스:**
1. `[status-count] H2: PENDING 2건·COMPLETED 1건·CANCELLED 1건 → 레이블별 건수 정확`
2. `[status-count] H2: 전체 0건 → percentage = 0.0`
