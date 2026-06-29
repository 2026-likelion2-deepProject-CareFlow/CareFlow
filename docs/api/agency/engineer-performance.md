# API 명세: 기사별 실적 리포트 (월 단위 조회)

## 기본 정보

| 항목 | 내용 |
|---|---|
| HTTP Method | GET |
| URL | `/api/settlements/engineers/performance` |
| 인증 | 필수 (JWT) |
| 허용 역할 | `AGENCY` |
| 도메인 패키지 | `com.careflow.settlement` |

---

## 요청

### Query Parameters

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `year` | int | O | 조회 연도 (예: 2026) |
| `month` | int | O | 조회 월 (1~12) |

### 요청 예시

```
GET /api/settlements/engineers/performance?year=2026&month=6
Authorization: Bearer {accessToken}
```

---

## 응답

### 성공 (200 OK)

```json
{
  "year": 2026,
  "month": 6,
  "engineers": [
    {
      "engineerId": 10,
      "engineerName": "홍길동",
      "completedCount": 12,
      "avgRating": 4.75,
      "totalEarning": 960000
    },
    {
      "engineerId": 11,
      "engineerName": "이순신",
      "completedCount": 8,
      "avgRating": 4.25,
      "totalEarning": 640000
    }
  ]
}
```

### 응답 필드 설명

| 필드 | 타입 | 설명 |
|---|---|---|
| `year` | int | 조회 연도 |
| `month` | int | 조회 월 |
| `engineers[].engineerId` | Long | 기사 user_id |
| `engineers[].engineerName` | String | 기사 이름 |
| `engineers[].completedCount` | int | 해당 월 완료 정산 건수 |
| `engineers[].avgRating` | Double | 해당 월 리뷰 평균 평점 (리뷰 없을 경우 null) |
| `engineers[].totalEarning` | int | 기사 실수령액 합계 (engineer_net_amount 합산) |

### 실패 응답

| 상황 | HTTP 상태 | 메시지 예시 |
|---|---|---|
| JWT 없거나 만료 | 401 | - |
| AGENCY 역할 아님 | 403 | - |
| year/month 누락 또는 범위 초과 | 400 | `"월은 1~12 사이여야 합니다."` |

---

## 비즈니스 로직

1. JWT에서 `agency_id`를 추출한다 (`CustomUserDetails` → `users.agency_id`).
2. `settlements` 테이블에서 `agency_id` + `status = 'PAID'` + `paid_at`이 요청 연월 범위인 레코드를 조회한다.
3. `engineer_id` 기준으로 그룹핑하여 `completedCount`, `engineer_net_amount` 합산을 계산한다.
4. 동일 기간·동일 기사의 `reviews.rating` 평균을 별도로 집계하여 합쳐서 응답한다.
5. 기사 이름은 `users.name`에서 조회한다.
6. 해당 월에 정산 내역이 없는 기사는 결과에 포함하지 않는다.

---

## 참조 테이블

```
settlements
  - settlement_id, payment_id, request_id
  - engineer_id (FK → users)
  - agency_id   (FK → agencies)
  - gross_amount, platform_fee, agency_fee, engineer_net_amount
  - status ENUM('PENDING','APPROVED','PAID','DISPUTED')
  - paid_at

reviews
  - review_id, request_id
  - engineer_id (FK → users)
  - rating (TINYINT 1~5)
  - created_at

users
  - user_id, name, role
```

---

## 구현 파일 목록

| 파일 | 경로 |
|---|---|
| Controller | `settlement/controller/SettlementController.java` |
| Service | `settlement/service/SettlementService.java` |
| Repository | `settlement/repository/SettlementRepository.java` |
| 응답 DTO | `settlement/dto/EngineerPerformanceResponse.java` |
| 응답 DTO (내부 항목) | `settlement/dto/EngineerPerformanceItem.java` |
| Entity | `settlement/entity/Settlements.java` |

---

## 테스트 요구사항

> **이 API를 구현할 때 아래 두 종류의 테스트를 반드시 작성해야 한다.**
> 테스트 없이 구현 완료로 간주하지 않는다.

### 1. 단위 테스트 (JUnit 5 + Mockito)

**대상**: `SettlementService`

**테스트 클래스**: `src/test/java/com/careflow/settlement/service/SettlementServiceTest.java`

**작성 규칙**:
- `@ExtendWith(MockitoExtension.class)` 사용
- `SettlementRepository`, `ReviewRepository` 등 의존성은 `@Mock`으로 처리
- 검증 대상은 집계 결과의 정확성(건수 합산, 금액 합산, 평점 평균)

**필수 테스트 케이스**:

| 케이스 | 설명 |
|---|---|
| 정상 조회 | 해당 월에 정산 데이터가 있는 경우, 기사별 건수·금액·평점이 올바르게 집계되는지 검증 |
| 빈 결과 | 해당 월에 정산 내역이 없는 경우 빈 리스트를 반환하는지 검증 |
| 리뷰 없는 기사 | 완료 건수는 있지만 리뷰가 없는 기사의 `avgRating`이 null로 처리되는지 검증 |
| 유효하지 않은 month | month가 0이나 13인 경우 `IllegalArgumentException`이 발생하는지 검증 |

---

### 2. 통합 테스트 (H2 인메모리 DB)

**대상**: `SettlementController` — 실제 HTTP 요청 → H2 DB 왕복 전체 흐름

**테스트 클래스**: `src/test/java/com/careflow/settlement/controller/SettlementControllerTest.java`

**작성 규칙**:
- `@WebMvcTest(SettlementController.class)` + `@Import(SecurityConfig.class)` 사용
- `@MockitoBean`으로 서비스 레이어 mocking (Spring Boot 3.4+ 스타일, `@MockBean` 아님)
- 실제 H2 DB와 연동하는 레포지토리 레벨 통합 테스트가 필요한 경우 `@SpringBootTest` + `@AutoConfigureMockMvc` + `@Transactional`로 별도 클래스 작성
- JWT 토큰 없이 요청 시 401이 반환되는지 검증하는 시큐리티 테스트 포함

**필수 테스트 케이스**:

| 케이스 | HTTP 상태 | 검증 내용 |
|---|---|---|
| 정상 요청 (AGENCY JWT) | 200 | 응답 JSON의 `engineers` 배열 구조 및 집계값 검증 |
| JWT 없음 | 401 | 인증 실패 |
| ENGINEER JWT로 요청 | 403 | 권한 없음 |
| month=0 요청 | 400 | 유효성 검사 실패 |
| 데이터 없는 월 | 200 | `engineers` 빈 배열 반환 |

---

## 구현 시 주의사항

- `settlements.paid_at` 기준으로 월 필터링할 것 (`created_at` 기준 아님).
- 집계 쿼리는 JPQL 또는 `@Query` 네이티브 쿼리로 작성하되, 페이징 없이 전체 결과를 반환한다 (한 대행사 소속 기사 수가 많지 않다고 가정).
- `avgRating`은 `Double`로 소수점 둘째 자리까지 반올림하여 반환한다.
- 엔티티에 Setter를 추가하지 말고, 집계 결과는 DTO 생성자 또는 record로 직접 매핑한다.
- 한글 주석으로 비즈니스 로직의 의도와 주의사항을 코드에 남긴다.
