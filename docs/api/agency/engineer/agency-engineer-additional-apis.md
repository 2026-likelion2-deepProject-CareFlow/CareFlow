# 대행사 소속 기사 관리 추가 API

> 브랜치: `feature/agency_engineer`  
> 작성일: 2026-07-01  
> 관련 컨트롤러: `AgencyEngineerController` (`/api/agency/engineers`)

---

## 개요

대행사 관리자(`AGENCY` 역할)가 소속 기사를 관리하기 위한 추가 API 5종.  
모든 엔드포인트는 JWT 인증 + `@PreAuthorize("hasRole('AGENCY')")` 적용.  
서비스 레이어에서 소속 대행사 일치 여부를 추가 검증하며, 타 대행사 기사 접근 시 `IllegalAccessException` (→ 401) 발생.

---

## API 목록

| # | HTTP | URI | 기능 | 인증 |
|---|------|-----|------|------|
| 1 | GET | `/api/agency/engineers/recommended?requestId={id}` | A/S 요청 기반 추천 기사 목록 | AGENCY |
| 2 | GET | `/api/agency/engineers/realtime-status` | 소속 기사 실시간 배정 현황 | AGENCY |
| 3 | GET | `/api/agency/engineers/{id}/settlements` | 기사 정산 목록 | AGENCY |
| 4 | GET | `/api/agency/engineers/{id}/lms` | 기사 LMS 교육 이수 현황 | AGENCY |
| 5 | GET | `/api/agency/engineers/{id}/reviews` | 기사 수신 리뷰 목록 | AGENCY |

---

## API 상세 명세

### 1. GET /api/agency/engineers/recommended

**설명**: 특정 A/S 요청에 적합한 소속 기사 추천 목록 반환.  
LMS 이수 완료 + 해당 날짜 AVAILABLE 근무표를 가진 기사를 평점 내림차순으로 반환한다.

**Query Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| `requestId` | Long | ✅ | A/S 요청 ID |

**Request Body**: 없음

**Response Body** (`200 OK`): `List<EngineerRecommendResponse>`

```json
[
  {
    "id": 10,
    "name": "홍길동",
    "brand": "LG 전문",
    "region": "강남구",
    "rating": 4.9,
    "availableFrom": "09:00 이후 가능",
    "profileImageUrl": null,
    "isLmsCompleted": true
  }
]
```

**추천 기사 필터링 로직**
1. 로그인 대행사 소속 기사 전체 조회 (`findByAgencyId`)
2. `isLmsCompleted = true` 기사만 필터
3. A/S 요청의 `scheduledDate`에 AVAILABLE 근무표가 있는 기사만 필터
4. 평점(`avgRating`) 내림차순 정렬
5. `availableFrom`: 첫 번째 timeSlot의 startTime (없으면 "하루 종일 가능")

**에러 응답**

| 상태코드 | 원인 |
|---------|------|
| 401 | JWT 없음 / 만료 / AGENCY 역할 아님 |
| 404 | requestId에 해당하는 A/S 요청 없음 |

---

### 2. GET /api/agency/engineers/realtime-status

**설명**: 소속 기사 전원의 현재 배정 상태(실시간 현황)를 반환.  
현재 진행 중이거나 이동 중인 배정 정보와 LMS 이수 여부를 포함한다.

**Query Parameters**: 없음

**Request Body**: 없음

**Response Body** (`200 OK`): `List<EngineerRealtimeStatusResponse>`

```json
[
  {
    "id": 10,
    "name": "홍길동",
    "specialty": "냉장고",
    "region": "강남구",
    "product": "LG 디오스 냉장고 수리",
    "timeRange": "10:00 ~ 12:00",
    "progress": 50,
    "isLmsCompleted": true,
    "profileImageUrl": null,
    "asStatus": "IN_PROGRESS"
  },
  {
    "id": 11,
    "name": "이순신",
    "specialty": "세탁기",
    "region": "서초구",
    "product": null,
    "timeRange": null,
    "progress": null,
    "isLmsCompleted": false,
    "profileImageUrl": null,
    "asStatus": null
  }
]
```

**asStatus 매핑 규칙**

| asRequest.status | asStatus 반환값 | progress |
|-----------------|----------------|---------|
| `IN_PROGRESS` | `"IN_PROGRESS"` | 50 |
| `ASSIGNED`, `ACCEPTED` | `"ASSIGNED"` | null |
| 그 외 (없거나 COMPLETED) | null | null |

**에러 응답**

| 상태코드 | 원인 |
|---------|------|
| 401 | JWT 없음 / 만료 / AGENCY 역할 아님 |

---

### 3. GET /api/agency/engineers/{id}/settlements

**설명**: 소속 기사의 정산 내역 전체 조회 (최신순).  
타 대행사 소속 기사 조회 시 401 반환.

**Path Variables**

| 파라미터 | 타입 | 설명 |
|---------|------|------|
| `id` | Long | 기사 userId |

**Request Body**: 없음

**Response Body** (`200 OK`): `List<EngineerSettlementResponse>`

```json
[
  {
    "settlementId": 1,
    "requestId": 100,
    "scheduledDate": "2026-06-15",
    "grossAmount": 150000,
    "platformFee": 15000,
    "agencyFee": 7500,
    "engineerNetAmount": 127500,
    "status": "PAID",
    "createdAt": "2026-06-16T10:00:00"
  }
]
```

**에러 응답**

| 상태코드 | 원인 |
|---------|------|
| 401 | JWT 없음 / 만료 / AGENCY 역할 아님 / 타 대행사 기사 접근 |
| 404 | 기사 프로필 없음 |

---

### 4. GET /api/agency/engineers/{id}/lms

**설명**: 소속 기사의 LMS 교육 이수 현황 조회.  
당해 연도 이수 완료 여부 + 이수한 콘텐츠 이력을 반환한다.

**Path Variables**

| 파라미터 | 타입 | 설명 |
|---------|------|------|
| `id` | Long | 기사 userId |

**Request Body**: 없음

**Response Body** (`200 OK`): `EngineerLmsStatusResponse`

```json
{
  "isLmsCompleted": true,
  "currentYear": 2026,
  "confirmations": [
    {
      "contentId": 1,
      "title": "냉장고 수리 기초 교육",
      "requiredLevel": "BEGINNER",
      "confirmedAt": "2026-03-10T14:30:00",
      "confirmedVersion": "1.0"
    }
  ]
}
```

**에러 응답**

| 상태코드 | 원인 |
|---------|------|
| 401 | JWT 없음 / 만료 / AGENCY 역할 아님 / 타 대행사 기사 접근 |
| 404 | 기사 프로필 없음 |

---

### 5. GET /api/agency/engineers/{id}/reviews

**설명**: 소속 기사가 수신한 리뷰 목록 조회 (최신순, 비공개 제외).  
전체 리뷰 수 + 평균 평점 요약과 개별 리뷰 목록을 함께 반환한다.

**Path Variables**

| 파라미터 | 타입 | 설명 |
|---------|------|------|
| `id` | Long | 기사 userId |

**Request Body**: 없음

**Response Body** (`200 OK`): `EngineerReviewListResponse`

```json
{
  "totalReviews": 128,
  "avgRating": 4.9,
  "reviews": [
    {
      "reviewId": 55,
      "customerName": "김고객",
      "rating": 5,
      "content": "정말 친절하고 빠른 수리 감사합니다.",
      "createdAt": "2026-06-20T15:00:00"
    }
  ]
}
```

**에러 응답**

| 상태코드 | 원인 |
|---------|------|
| 401 | JWT 없음 / 만료 / AGENCY 역할 아님 / 타 대행사 기사 접근 |
| 404 | 기사 프로필 없음 |

---

## 구현 위치

| 파일 종류 | 경로 |
|---------|------|
| Controller | `agency/controller/AgencyEngineerController.java` (기존 파일에 추가) |
| Service | `agency/service/AgencyEngineerService.java` (기존 파일에 추가) |
| Response DTO | `agency/dto/response/EngineerRecommendResponse.java` |
| Response DTO | `agency/dto/response/EngineerRealtimeStatusResponse.java` |
| Response DTO | `agency/dto/response/EngineerSettlementResponse.java` |
| Response DTO | `agency/dto/response/EngineerLmsStatusResponse.java` |
| Response DTO | `agency/dto/response/EngineerReviewListResponse.java` |
| Repository 추가 메서드 | `settlement/repository/SettlementRepository.java` |
| Repository 추가 메서드 | `assignment/repository/AsAssignmentRepository.java` |
| Repository 추가 메서드 | `review/repository/ReviewRepository.java` |

---

## 테스트 명세

> **모든 신규 API에 대해 아래 3종 테스트를 작성한다.**

### 테스트 1: Controller 슬라이스 테스트 (`@WebMvcTest`)

- **파일**: `AgencyEngineerAdditionalControllerTest.java`
- **어노테이션**: `@WebMvcTest(AgencyEngineerController.class)` + `@Import({SecurityConfig.class, PasswordEncoderConfig.class})`
- **목적**: HTTP 요청/응답 형식 및 인증 처리 검증 (Service는 `@MockitoBean`으로 대체)
- **필수 테스트 케이스**:
  - 정상 응답 (200 OK, 응답 필드 검증)
  - 미인증 요청 (401)
  - 타 대행사 기사 접근 (`IllegalAccessException` → 401)
  - 존재하지 않는 기사/요청 (`NoSuchElementException` → 404)

### 테스트 2: Service 단위 테스트 (`@ExtendWith(MockitoExtension.class)`)

- **파일**: `AgencyEngineerAdditionalServiceTest.java`
- **어노테이션**: `@ExtendWith(MockitoExtension.class)` + `@MockitoSettings(strictness = Strictness.LENIENT)`
- **목적**: 서비스 비즈니스 로직 단독 검증 (Repository는 `@Mock`)
- **필수 테스트 케이스**:
  - 정상 결과 반환 (리스트 크기, 필드값 검증)
  - 소속 대행사 불일치 시 `IllegalAccessException` 발생
  - 기사 프로필 없음 시 `NoSuchElementException` 발생

### 테스트 3: 통합 테스트 (`@SpringBootTest` + H2)

- **파일**: `AgencyEngineerAdditionalIntegrationTest.java`
- **어노테이션**: `@SpringBootTest` + `@AutoConfigureMockMvc` + `@ActiveProfiles("local")` + `@Sql(scripts = "/cleanup.sql", executionPhase = BEFORE_TEST_METHOD)`
- **목적**: H2 DB에 실제 데이터를 INSERT하고 전체 플로우(Controller → Service → Repository → H2) 검증
- **데이터 세팅**: `@BeforeEach`에서 `repository.save()`로 대행사/기사/정산/리뷰/LMS 데이터 직접 INSERT
- **JWT 토큰**: `jwtProvider.generateAccessToken()`으로 실제 토큰 생성 후 `Authorization: Bearer <token>` 헤더에 포함
- **필수 테스트 케이스**:
  - H2에 INSERT된 데이터가 응답에 정확히 반영되는지 검증 (필드값 일치)
  - 빈 목록 반환 케이스 (데이터 없을 때 빈 배열/빈 리스트)
  - 타 대행사 기사 접근 시 401

---

## 참고

- `@PreAuthorize("hasRole('AGENCY')")` 가 Controller 클래스 레벨에 이미 적용되어 있으므로 SecurityConfig 변경 불필요.
- `GlobalExceptionHandler`가 `IllegalAccessException` → 401, `NoSuchElementException` → 404 매핑을 이미 처리하므로 별도 예외 핸들러 추가 불필요.
- `columnDefinition`이 지정된 컬럼(`status`, `assigned_at`, `created_at` 등)은 H2 호환을 위해 절대 제거하지 않는다.
