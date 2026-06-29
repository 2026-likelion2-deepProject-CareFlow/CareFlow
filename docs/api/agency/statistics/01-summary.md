# API 명세: 대행사 통계 요약 (Summary)

## 기본 정보

| 항목 | 내용 |
|------|------|
| Method | GET |
| URI | `/api/agency/statistics/summary` |
| 인증 | 필수 (ROLE_AGENCY) |
| 설명 | 지정 기간의 핵심 지표 6가지와 전 기간 대비 증감률을 반환한다 |

---

## 요청

### Query Parameters (RequestDto: `AgencyStatisticsDateRangeRequest`)

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| dateFrom | LocalDate | Y | 조회 시작일 (yyyy-MM-dd) |
| dateTo | LocalDate | Y | 조회 종료일 (yyyy-MM-dd, 포함) |

- `@ModelAttribute`로 바인딩
- `dateFrom <= dateTo` 조건을 서비스에서 검증

### 인증 파라미터

```java
@AuthenticationPrincipal CustomUserDetails userDetails
```

- `userDetails.getRole()` → `"AGENCY"` 여부 검증
- `userDetails.getAgencyId()` → 대행사 필터 조건

---

## 응답 (`AgencyStatisticsSummaryResponse`)

```json
{
  "totalReceipts": 1248,
  "totalReceiptsChangeRate": 15.2,
  "completedCount": 1086,
  "completedCountChangeRate": 12.6,
  "completionRate": 87.0,
  "completionRateChange": 2.3,
  "avgProcessingTimeHours": 2.4,
  "avgProcessingTimeChange": -0.3,
  "avgRating": 4.8,
  "avgRatingChange": 0.2,
  "totalSettlementAmount": 98420000,
  "totalSettlementAmountChangeRate": 12.5
}
```

| 필드 | 타입 | 설명 |
|------|------|------|
| totalReceipts | long | 기간 내 총 A/S 접수 건수 |
| totalReceiptsChangeRate | double | 전 기간 대비 증감률 (%) |
| completedCount | long | 기간 내 완료 건수 (COMPLETED + PAID) |
| completedCountChangeRate | double | 전 기간 대비 증감률 (%) |
| completionRate | double | 완료율 = completedCount / totalReceipts × 100 |
| completionRateChange | double | 전 기간 대비 증감 포인트 |
| avgProcessingTimeHours | double | 완료 건 기준 평균 처리 시간 (시간 단위, created_at→updated_at) |
| avgProcessingTimeChange | double | 전 기간 대비 변화량 (시간) |
| avgRating | double | 기간 내 리뷰 평균 평점 |
| avgRatingChange | double | 전 기간 대비 변화량 |
| totalSettlementAmount | long | 기간 내 총 정산 금액 (gross_amount 합산) |
| totalSettlementAmountChangeRate | double | 전 기간 대비 증감률 (%) |

### 전 기간 계산 방식
- 조회 기간 길이(days)와 동일한 길이로 `dateFrom` 이전 구간을 자동 산정
- 예: 06.01 ~ 06.18(18일) → 전 기간: 05.14 ~ 05.31

---

## 집계 쿼리 대상 테이블

| 지표 | 참조 테이블 |
|------|------------|
| 접수 건수 | `as_requests` (agency_id, created_at) |
| 완료 건수 | `as_requests` (status IN COMPLETED, PAID) |
| 평균 처리 시간 | `as_requests` (TIMESTAMPDIFF MINUTE created_at → updated_at) |
| 평균 평점 | `reviews` JOIN `as_requests` (agency_id 필터) |
| 총 정산 금액 | `settlements` (agency_id, created_at, gross_amount) |

---

## 오류 응답

| 상황 | HTTP | 메시지 |
|------|------|--------|
| AGENCY 역할 아님 | 403 | "대행사 계정만 접근 가능합니다." |
| dateFrom > dateTo | 400 | "시작일은 종료일보다 이전이어야 합니다." |
| agencyId 없음 | 404 | "대행사 정보를 찾을 수 없습니다." |

---

## 테스트 명세

### 단위 테스트 (`AgencyStatisticsControllerTest`)

- `@WebMvcTest(AgencyStatisticsController.class)` + `@Import(SecurityConfig.class)`
- `AgencyStatisticsService` → `@MockitoBean`

**케이스:**
1. `[summary] 성공: AGENCY 역할 + 유효 날짜 → 200 + 응답 JSON 필드 존재`
2. `[summary] 실패: 비인증 요청 → 401`
3. `[summary] 실패: dateFrom 누락 → 400`
4. `[summary] 실패: dateFrom > dateTo → 400`

### 통합 테스트 (`AgencyStatisticsIntegrationTest`)

- `@SpringBootTest` + `@AutoConfigureMockMvc` + `@ActiveProfiles("local")`
- H2 인메모리 DB에 픽스처 데이터 직접 삽입 후 API 호출

**케이스:**
1. `[summary] H2: as_requests 3건(2완료) 삽입 → totalReceipts=3, completedCount=2, completionRate≈66.7`
2. `[summary] H2: 빈 기간 조회 → 모든 수치 0`
3. `[summary] H2: 다른 대행사 데이터 → 집계에 포함되지 않음`
