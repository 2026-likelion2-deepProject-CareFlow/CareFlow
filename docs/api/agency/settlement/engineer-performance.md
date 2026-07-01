# API 명세: 기사별 정산 내역 목록 조회

## 기본 정보

| 항목 | 내용 |
|---|---|
| HTTP Method | GET |
| URL | `/api/agency/settlements/engineers/performance` |
| 인증 | 필수 (JWT) |
| 허용 역할 | `AGENCY` |
| 도메인 패키지 | `com.careflow.settlement` |

---

## 요청

### Query Parameters

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `year` | int | O | 조회 연도 (예: 2024) |
| `month` | int | O | 조회 월 (1~12) — 기본 범위 지정용 |
| `status` | String | X | 상태 필터: `PENDING`(지급 대기) / `PAID`(지급 완료) / `DISPUTED`(보류). null 시 전체 조회 |
| `keyword` | String | X | 기사명 부분 일치 검색 |
| `settlementId` | Long | X | 정산 ID 정확히 일치 검색 |
| `dateFrom` | String | X | 날짜 범위 시작 (`yyyy-MM-dd`). null 시 해당 월 1일 |
| `dateTo` | String | X | 날짜 범위 종료 (`yyyy-MM-dd`, 해당일 포함). null 시 해당 월 말일 |
| `page` | int | X | 페이지 번호 (기본값 0) |
| `size` | int | X | 페이지 당 건수 (기본값 10) |

### 요청 예시

```
GET /api/agency/settlements/engineers/performance?year=2024&month=6&status=PAID&keyword=김현수&page=0&size=10
Authorization: Bearer {accessToken}
```

---

## 응답

### 성공 (200 OK)

```json
{
  "year": 2024,
  "month": 6,
  "settlements": [
    {
      "settlementId": 1,
      "type": "ENGINEER",
      "engineerId": 123,
      "engineerName": "김현수",
      "engineerPhone": "010-1234-5678",
      "agencyName": "퀵케어 서비스",
      "periodStart": "2024-06-01",
      "periodEnd": "2024-06-30",
      "grossAmount": 2480000,
      "platformFeeRate": 10.0,
      "platformFee": 248000,
      "agencyFeeRate": 10.0,
      "agencyFee": 248000,
      "engineerNetAmount": 1984000,
      "status": "PAID",
      "settledAt": "2024-06-18T15:30:00"
    }
  ],
  "totalElements": 248,
  "totalPages": 25,
  "currentPage": 0,
  "size": 10
}
```

### 응답 필드 설명

| 필드 | 타입 | 설명 |
|---|---|---|
| `year` | int | 조회 연도 |
| `month` | int | 조회 월 |
| `settlements[].settlementId` | Long | 정산 고유 ID |
| `settlements[].type` | String | 정산 유형 (현재 항상 `"ENGINEER"`) |
| `settlements[].engineerId` | Long | 기사 user_id |
| `settlements[].engineerName` | String | 기사 이름 |
| `settlements[].engineerPhone` | String | 기사 연락처 |
| `settlements[].agencyName` | String | 소속 대행사명 |
| `settlements[].periodStart` | String | 정산 기준 기간 시작 (해당 월 1일, `yyyy-MM-dd`) |
| `settlements[].periodEnd` | String | 정산 기준 기간 종료 (해당 월 말일, `yyyy-MM-dd`) |
| `settlements[].grossAmount` | int | 총 정산 금액 (고객 결제액) |
| `settlements[].platformFeeRate` | BigDecimal | CareFlow 수수료율 (%) 스냅샷 |
| `settlements[].platformFee` | int | CareFlow 수수료 (원) |
| `settlements[].agencyFeeRate` | BigDecimal | 대행사 수수료율 (%) 스냅샷 |
| `settlements[].agencyFee` | int | 대행사 수수료 (원) |
| `settlements[].engineerNetAmount` | int | 기사 실지급액 (원) |
| `settlements[].status` | String | `PENDING` / `PAID` / `APPROVED` / `DISPUTED` |
| `settlements[].settledAt` | LocalDateTime | 지급 완료 일시 (null: 미지급) |
| `totalElements` | long | 필터 적용 후 전체 건수 |
| `totalPages` | int | 전체 페이지 수 |
| `currentPage` | int | 현재 페이지 번호 (0-based) |
| `size` | int | 페이지 당 건수 |

### 실패 응답

| 상황 | HTTP 상태 | 메시지 예시 |
|---|---|---|
| JWT 없거나 만료 | 401 | - |
| AGENCY 역할 아님 | 403 | - |
| year/month 누락 또는 month 범위 초과 | 400 | `"월은 1~12 사이여야 합니다."` |
| dateFrom/dateTo 형식 오류 | 400 | `"날짜 형식이 올바르지 않습니다. (yyyy-MM-dd)"` |

---

## 비즈니스 로직

1. JWT에서 `agency_id`를 추출한다 (`CustomUserDetails` → `userRepository` → `agency`).
2. `year` + `month`로 기본 조회 범위를 계산한다 (해당 월 1일 00:00 ~ 다음 달 1일 00:00).
3. `dateFrom` / `dateTo`가 제공된 경우 기본 범위 내에서 추가 좁힘 필터로 적용한다.
4. `settlements` 테이블에서 `agency_id` 일치 + 날짜 범위 + 동적 필터(status / keyword / settlementId)를 적용하여 조회한다.
5. `paidAt DESC` 정렬, 페이징 적용.
6. `periodStart` / `periodEnd`는 `createdAt` 기준 해당 월의 1일~말일로 서비스 레이어에서 파생한다.
7. CSV 다운로드(`/monthly-report/download`)는 기존 집계 방식을 유지하며 이 메서드를 사용하지 않는다.

---

## 필터 동작 규칙

| 필터 | null 시 동작 |
|---|---|
| `status` | 전체 상태 조회 |
| `keyword` | 기사명 필터 미적용 |
| `settlementId` | ID 필터 미적용 |
| `dateFrom` | 해당 월 1일 기준 |
| `dateTo` | 해당 월 말일 기준 |

---

## 구현 파일 목록

| 파일 | 경로 |
|---|---|
| Controller | `settlement/controller/SettlementController.java` |
| Service | `settlement/service/SettlementService.java` — `getSettlementList()` 메서드 |
| Repository | `settlement/repository/SettlementRepository.java` — `findSettlementListByAgency()` 추가 |
| 응답 DTO | `settlement/dto/EngineerSettlementListResponse.java` |

---

## 테스트 요구사항

> **단위 테스트(JUnit 5 + Mockito)와 컨트롤러 슬라이스 테스트를 반드시 작성한다.**

### 1. 단위 테스트 (`SettlementServiceTest`)

| TC | 케이스 | 검증 포인트 |
|---|---|---|
| TC-1 | 필터 없음 — 전체 조회 | `settlements` 리스트 크기, `totalElements` |
| TC-2 | `status=PAID` 필터 전달 | Repository에 `status="PAID"` 파라미터 전달 여부 |
| TC-3 | `keyword` 필터 전달 | Repository에 `keyword` 파라미터 전달 여부 |
| TC-4 | `settlementId` 필터 전달 | Repository에 `settlementId` 파라미터 전달 여부 |
| TC-5 | `dateFrom`/`dateTo` 정상 파싱 | `LocalDateTime`으로 변환되어 Repository 호출 여부 |
| TC-6 | 잘못된 날짜 형식 | `IllegalArgumentException` 발생 |
| TC-7 | 유효하지 않은 month | `IllegalArgumentException("월은 1~12 사이여야 합니다.")` |
| TC-8 | 결과 0건 | 빈 리스트, `totalElements=0` |

### 2. 컨트롤러 슬라이스 테스트 (`SettlementControllerTest`)

| TC | HTTP 상태 | 검증 포인트 |
|---|---|---|
| 정상 요청 (year/month만) | 200 | `settlements` 배열, 페이징 필드 구조 검증 |
| status 필터 포함 | 200 | 서비스 호출 시 status 파라미터 전달 검증 |
| JWT 없음 | 401 | 인증 실패 |
| year 누락 | 400 | 파라미터 오류 |
| month=0 | 400 | IllegalArgumentException → 400 |
