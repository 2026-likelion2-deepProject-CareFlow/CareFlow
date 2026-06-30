# API: 대행사 소속 고객의 가전 목록 조회

## 개요

대행사 관리자가 [`GET /api/agency/customers`](./agency-customer-list.md) 목록에서 선택한 특정 고객(`userId`)이 등록한 가전 목록을 조회한다.
고객 관리 페이지의 고객 상세 화면(가전 탭)에서 사용된다.

---

## 엔드포인트

```
GET /api/agency/customers/{userId}/appliances
```

---

## 인증/인가

- **필수 인증**: JWT Bearer 토큰
- **허용 역할**: `AGENCY` (대행사 관리자 로그인 상태에서만 호출 가능)
- [`agency-customer-list.md`](./agency-customer-list.md)와 동일하게 `SecurityConfig`에는 본 경로에 대한 명시적 `hasAuthority("AGENCY")` 매칭이 없으므로(`anyRequest().authenticated()`만 적용), **서비스 레이어에서 `userDetails.getRole() == "AGENCY"`를 명시적으로 검증**한다. 아니면 `IllegalAccessException`(401).
- **고객 소속 검증**: `{userId}`가 임의의 고객이 아니라, **현재 로그인한 대행사 소속 기사에게 COMPLETED 서비스를 1회 이상 받은 고객**이어야 한다(목록 API와 동일한 모수). 검증은 `AsAssignmentRepository.findDistinctCompletedCustomerIdsByAgencyId(agencyId)` 결과에 `userId`가 포함되는지로 판단 — 타 대행사 고객이거나 자사와 서비스 이력이 없는 고객이면 `IllegalAccessException`(401)으로 차단(데이터 격리).

---

## 요청

### 경로 변수

| 변수 | 타입 | 설명 |
|---|---|---|
| `userId` | Long | 조회 대상 고객의 user_id |

### 요청 예시

```
GET /api/agency/customers/1/appliances
Authorization: Bearer {access_token}
```

---

## 응답

### 200 OK

```json
[
  {
    "applianceId": 1,
    "categoryName": "에어컨",
    "brand": "삼성",
    "modelName": "바람의나라 AF17",
    "serialNumber": "SN-001",
    "purchaseDate": "2022-03-15",
    "warrantyEndDate": "2025-03-15",
    "status": "NORMAL",
    "registerMethod": "MANUAL",
    "imageUrl": "https://...",
    "createdAt": "2024-06-18T00:00:00"
  }
]
```

가전이 없으면 빈 배열 `[]` 반환(204 아님 — 목록 API와 달리 고객 상세 화면 특성상 빈 배열도 정상 응답으로 처리).

### 응답 필드 설명

| 필드 | 타입 | 설명 |
|---|---|---|
| `applianceId` | Long | 가전 ID |
| `categoryName` | String | 가전 카테고리명 (`appliance_categories.name`, 소분류) |
| `brand` | String | 브랜드명 |
| `modelName` | String | 모델명 |
| `serialNumber` | String | 시리얼 번호 (null 가능) |
| `purchaseDate` | LocalDate | 구매일 (null 가능) |
| `warrantyEndDate` | LocalDate | 무상 A/S 만료일 (null 가능) |
| `status` | String | 가전 상태 (`NORMAL`/`NEED_REPAIR`/`SOLD`) |
| `registerMethod` | String | 등록 방식 (`MANUAL`/`OCR`) |
| `imageUrl` | String | 가전 사진 URL (null 가능) |
| `createdAt` | LocalDateTime | 가전 등록일시 |

> 논리 삭제(`deleted_at IS NOT NULL`)된 가전은 응답에서 항상 제외한다 — [`agency-customer-list.md`](./agency-customer-list.md)의 `applianceCount` 집계 기준과 동일.

### 에러 응답

| HTTP 상태 | 조건 |
|---|---|
| 401 Unauthorized | JWT 토큰 없음/만료(Security 필터 단계), role != AGENCY, 또는 `userId`가 본인 대행사 소속 기사에게 COMPLETED 서비스를 받은 고객이 아닌 경우(서비스 레이어 검증, `IllegalAccessException`) |
| 404 Not Found | `userId`에 해당하는 유저 자체가 존재하지 않는 경우 (`NoSuchElementException`) |

---

## 처리 로직 (Pipeline)

1. **검증 단계**
   - role = AGENCY 확인 → 아니면 401
   - `userId`로 `UserRepository.findById` 조회 → 없으면 404
   - `AsAssignmentRepository.findDistinctCompletedCustomerIdsByAgencyId(agencyId)` 결과에 `userId` 포함 여부 확인 → 미포함 시 401

2. **데이터 처리 단계**
   - `ApplianceRepository`에서 `userId` 기준 미삭제(`deleted_at IS NULL`) 가전 목록을 `category`까지 `JOIN FETCH`하여 단일 쿼리로 조회(N+1 방지), 최신 등록순(`created_at DESC`) 정렬

3. **응답 단계**
   - `List<AgencyCustomerApplianceResponse>`로 매핑 후 200 OK 반환 (빈 리스트도 200)

---

## 예외 처리 및 제약 조건

- 표준 예외 4종 중 `NoSuchElementException`(404), `IllegalAccessException`(401) 사용
- `@Transactional(readOnly = true)` 적용

---

## 개발 및 출력 요구사항

| 계층 | 클래스 |
|---|---|
| Controller (수정) | `com.careflow.agency.controller.AgencyCustomerController` — 메서드 추가 |
| Service (수정) | `com.careflow.agency.service.AgencyCustomerService` — 메서드 추가 |
| Repository (수정) | `com.careflow.appliance.repository.ApplianceRepository` — `category` `JOIN FETCH` 조회 메서드 추가 |
| Response DTO | `com.careflow.agency.dto.response.AgencyCustomerApplianceResponse` |

---

## 테스트 명세 (필수 — 아래 항목을 반드시 준수한다)

### JUnit 5 단위 테스트 (Service Layer, Mockito)

**파일**: `src/test/java/com/careflow/agency/service/AgencyCustomerServiceTest.java` (기존 클래스에 `@Nested` 그룹 추가)

- TC-1. 정상 조회 — 가전 2건 → size 2, 필드 매핑 정확성(categoryName 포함) 검증
- TC-2. 가전 없음 — 빈 리스트 반환(예외 아님)
- TC-3. 존재하지 않는 userId — `NoSuchElementException`
- TC-4. COMPLETED 서비스 이력 없는(타사 또는 무관계) 고객 — `IllegalAccessException`
- TC-5. ENGINEER 역할로 호출 — `IllegalAccessException`

### JUnit 5 통합 테스트 (H2 DB 연동)

**파일**: `src/test/java/com/careflow/agency/service/AgencyCustomerServiceIntegrationTest.java` (기존 클래스에 `@Nested` 그룹 추가)

- TC-I-1. H2에 실제 가전 2건 INSERT 후 조회 → 응답 필드가 DB 저장값과 일치하는지 검증(categoryName 포함)
- TC-I-2. 논리 삭제된 가전은 결과에서 제외되는지 검증
- TC-I-3. 타 대행사 고객의 가전 조회 시도 → `IllegalAccessException`
- TC-I-4. 존재하지 않는 userId → `NoSuchElementException`
- TC-I-5. 최신 등록순(`createdAt DESC`) 정렬 검증

### JUnit 5 컨트롤러 테스트 (`@WebMvcTest`)

**파일**: `src/test/java/com/careflow/agency/controller/AgencyCustomerControllerTest.java` (기존 클래스에 `@Nested` 그룹 추가)

- TC-C-1. 인증된 AGENCY — 200 OK + JSON 배열 검증
- TC-C-2. 인증 없음 — 401
- TC-C-3. Service에서 `NoSuchElementException` 발생 — 404
- TC-C-4. Service에서 `IllegalAccessException` 발생 — 401
