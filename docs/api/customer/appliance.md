# 고객 가전제품 API 설계

## 개요

고객이 본인 소유 가전제품을 등록·조회·삭제하고,
가전별 수리 이력 타임라인 및 건강 진단서를 조회하는 API 6종.
모든 엔드포인트는 JWT에서 `userId`를 추출하며 `@RequestParam` 방식의 사용자 식별을 사용하지 않는다.

---

## API 1 — 가전제품 등록

### 기본 정보

| 항목 | 내용 |
|---|---|
| Method | `POST` |
| Path | `/api/appliances` |
| 인증 | Bearer JWT (CUSTOMER 권한) |
| 책임 도메인 | `appliance` |

### Request Body

```json
{
  "categoryId": 3,
  "brand": "LG",
  "modelName": "디오스 V870",
  "serialNumber": "LG-2021-00123",
  "purchaseDate": "2021-03-15",
  "warrantyEndDate": "2024-03-15",
  "registerMethod": "MANUAL"
}
```

| 필드 | 타입 | 필수 | 제약 |
|---|---|---|---|
| `categoryId` | Integer | ✅ | 소분류(depth=2)만 허용 |
| `brand` | String | ✅ | 50자 이내 |
| `modelName` | String | ❌ | 100자 이내 |
| `serialNumber` | String | ❌ | 100자 이내 |
| `purchaseDate` | LocalDate | ❌ | `yyyy-MM-dd` |
| `warrantyEndDate` | LocalDate | ❌ | `yyyy-MM-dd` |
| `registerMethod` | String | ❌ | `MANUAL` / `OCR` (미입력 시 `MANUAL`) |

### 처리 흐름

1. JWT에서 `userId` 추출
2. `userId`로 사용자 조회 — 없으면 404
3. `categoryId`로 카테고리 조회 — 없으면 404
4. 카테고리 `depth == 2` 검증 — 아니면 400
5. `Appliance.create()` 정적 팩토리로 저장
6. 201 + `ApplianceResponse` 반환

### 응답 예시 (201 Created)

```json
{
  "applianceId": 7,
  "userId": 3,
  "categoryId": 3,
  "brand": "LG",
  "modelName": "디오스 V870",
  "serialNumber": "LG-2021-00123",
  "purchaseDate": "2021-03-15",
  "warrantyEndDate": "2024-03-15",
  "registerMethod": "MANUAL",
  "status": "ACTIVE",
  "createdAt": "2026-06-29T10:00:00"
}
```

### 오류

| 상황 | HTTP | 예외 |
|---|---|---|
| 존재하지 않는 사용자 | 404 | `NoSuchElementException` |
| 존재하지 않는 카테고리 | 404 | `NoSuchElementException` |
| 소분류(depth=2)가 아닌 카테고리 선택 | 400 | `IllegalArgumentException` |
| 필수 필드 누락 / 길이 초과 | 400 | `MethodArgumentNotValidException` |

---

## API 2 — 내 가전제품 목록 조회

### 기본 정보

| 항목 | 내용 |
|---|---|
| Method | `GET` |
| Path | `/api/appliances/me` |
| 인증 | Bearer JWT (CUSTOMER 권한) |
| 책임 도메인 | `appliance` |

### 처리 흐름

1. JWT에서 `userId` 추출
2. `user_id = :userId AND deleted_at IS NULL` 조건으로 가전 목록 조회 (최신 등록순)
3. 200 + `List<ApplianceResponse>` 반환 (없으면 빈 배열)

### 응답 예시 (200 OK)

```json
[
  {
    "applianceId": 7,
    "userId": 3,
    "categoryId": 3,
    "brand": "LG",
    "modelName": "디오스 V870",
    "serialNumber": "LG-2021-00123",
    "purchaseDate": "2021-03-15",
    "warrantyEndDate": "2024-03-15",
    "registerMethod": "MANUAL",
    "status": "ACTIVE",
    "createdAt": "2026-06-29T10:00:00"
  }
]
```

### 오류

없음 (논리 삭제된 항목은 자동 제외, 결과 없으면 빈 배열 반환)

---

## API 3 — 가전제품 상세 조회

### 기본 정보

| 항목 | 내용 |
|---|---|
| Method | `GET` |
| Path | `/api/appliances/{applianceId}` |
| 인증 | Bearer JWT (CUSTOMER 권한) |
| 책임 도메인 | `appliance` |

### 처리 흐름

1. JWT에서 `userId` 추출
2. `applianceId`로 가전 조회 (`deleted_at IS NULL`) — 없으면 404
3. `appliance.user_id == userId` 검증 — 불일치 시 401
4. 200 + `ApplianceResponse` 반환

### 응답 예시 (200 OK)

```json
{
  "applianceId": 7,
  "userId": 3,
  "categoryId": 3,
  "brand": "LG",
  "modelName": "디오스 V870",
  "serialNumber": "LG-2021-00123",
  "purchaseDate": "2021-03-15",
  "warrantyEndDate": "2024-03-15",
  "registerMethod": "MANUAL",
  "status": "ACTIVE",
  "createdAt": "2026-06-29T10:00:00"
}
```

### 오류

| 상황 | HTTP | 예외 |
|---|---|---|
| 존재하지 않거나 삭제된 가전 | 404 | `NoSuchElementException` |
| 본인 소유가 아닌 가전 | 401 | `IllegalAccessException` |

---

## API 4 — 가전제품 논리 삭제

### 기본 정보

| 항목 | 내용 |
|---|---|
| Method | `DELETE` |
| Path | `/api/appliances/{applianceId}` |
| 인증 | Bearer JWT (CUSTOMER 권한) |
| 책임 도메인 | `appliance` |

### 처리 흐름

1. JWT에서 `userId` 추출
2. `applianceId`로 가전 조회 (`deleted_at IS NULL`) — 없으면 404
3. `appliance.user_id == userId` 검증 — 불일치 시 401
4. `appliance.delete()` — `deleted_at = now()` 세팅 (물리 삭제 아님)
5. 204 No Content 반환

### 응답

성공 시 Body 없이 `204 No Content` 반환.

### 오류

| 상황 | HTTP | 예외 |
|---|---|---|
| 존재하지 않거나 이미 삭제된 가전 | 404 | `NoSuchElementException` |
| 본인 소유가 아닌 가전 | 401 | `IllegalAccessException` |

---

## API 5 — 수리 이력 타임라인 조회

### 기본 정보

| 항목 | 내용 |
|---|---|
| Method | `GET` |
| Path | `/api/appliances/{applianceId}/repair-history` |
| 인증 | Bearer JWT (CUSTOMER 또는 ENGINEER 권한) |
| 책임 도메인 | `appliance`, `report` |

> 고객의 가전 상세 화면과 수리기사의 작업 상세 화면에서 공통으로 사용하는 API (E-10).

### 처리 흐름

1. JWT에서 `userId`, `role` 추출
2. 동일 가전(`appliance_id`)에 작성된 모든 `work_reports` 조회 (최신순, JOIN FETCH로 N+1 방지)
3. `role` 및 `userId` 기반 접근 권한 검증 (서비스 내부 처리)
4. 200 + `List<RepairHistoryResponse>` 반환 (없으면 빈 배열)

### 응답 예시 (200 OK)

```json
[
  {
    "reportId": 5,
    "submittedAt": "2026-05-10T14:30:00",
    "symptomName": "냉장 기능 불량",
    "engineerName": "김기사",
    "diagnosisResult": "REPAIRED",
    "finalAmount": 85000
  },
  {
    "reportId": 2,
    "submittedAt": "2025-11-03T10:00:00",
    "symptomName": "소음 발생",
    "engineerName": "이기사",
    "diagnosisResult": "REPAIRED",
    "finalAmount": 35000
  }
]
```

### 오류

| 상황 | HTTP | 예외 |
|---|---|---|
| 접근 권한 없음 | 401 | `IllegalAccessException` |

---

## API 6 — 건강 진단서 조회

### 기본 정보

| 항목 | 내용 |
|---|---|
| Method | `GET` |
| Path | `/api/appliances/{applianceId}/health-certificate` |
| 인증 | Bearer JWT (CUSTOMER 권한) |
| 책임 도메인 | `appliance` |

> 명세: **C-24**

### 처리 흐름

1. JWT에서 `userId`, `role` 추출
2. `applianceId`로 가전 조회 (`deleted_at IS NULL`) — 없으면 404
3. `role == CUSTOMER`이면 `appliance.user_id == userId` 검증 — 불일치 시 401
4. `health_certificates`에서 `appliance_id`로 진단서 조회 — 없으면 404
5. 최신 `work_reports` 목록으로 4축 점수 역산
   - 1축 `repairCountScore` : 누적 수리 횟수
   - 2축 `usagePeriodScore` : 구매일 기준 사용 기간
   - 3축 `partImportanceScore` : 최근 보고서 교체 부품 최고 중요도
   - 4축 `lastRepairedScore` : 직전 수리 이후 경과 기간
6. 200 + `HealthCertificateResponse` 반환

### 응답 예시 (200 OK)

```json
{
  "certId": 4,
  "applianceId": 7,
  "grade": "B",
  "score": 78,
  "isCertified": true,
  "issuedAt": "2026-05-10T14:30:00",
  "updatedAt": "2026-05-10T14:30:00",
  "repairCountScore": 20,
  "usagePeriodScore": 20,
  "partImportanceScore": 20,
  "lastRepairedScore": 18
}
```

> `isCertified`: `score >= 75` 이고 `grade ∈ {A, B}`이면 `true`.
> 4축 점수는 DB에 저장된 값이 아니라 응답 시 서버에서 역산하여 제공한다.

### 오류

| 상황 | HTTP | 예외 |
|---|---|---|
| 존재하지 않거나 삭제된 가전 | 404 | `NoSuchElementException` |
| 본인 소유가 아닌 가전 (CUSTOMER) | 401 | `IllegalAccessException` |
| 진단서 미발급 (수리 이력 없음) | 404 | `NoSuchElementException` |

---

## DB 설계 참조

```sql
-- appliances (핵심 컬럼만)
appliance_id     BIGINT PK
user_id          BIGINT FK → users.user_id
category_id      INT FK → appliance_categories.category_id
brand            VARCHAR(50) NOT NULL
model_name       VARCHAR(100) NULL
serial_number    VARCHAR(100) NULL
purchase_date    DATE NULL
warranty_end_date DATE NULL
register_method  ENUM('MANUAL','OCR') DEFAULT 'MANUAL'
status           ENUM('ACTIVE','INACTIVE') DEFAULT 'ACTIVE'
created_at       DATETIME NOT NULL
deleted_at       DATETIME NULL     -- 논리 삭제 필드

-- health_certificates (핵심 컬럼만)
cert_id          BIGINT PK
appliance_id     BIGINT FK → appliances.appliance_id  UNIQUE
grade            CHAR(1)       -- A~E
score            INT           -- 0~100
repair_count     INT DEFAULT 0
is_certified     TINYINT(1) DEFAULT 0
issued_at        DATETIME NOT NULL
updated_at       DATETIME NOT NULL
```

---

## 구현 위치

```
appliance/
├── controller/ApplianceController.java
├── service/ApplianceService.java
├── entity/
│   ├── Appliance.java
│   ├── ApplianceCategory.java
│   └── HealthCertificate.java
├── repository/
│   ├── ApplianceRepository.java
│   ├── ApplianceCategoryRepository.java
│   └── HealthCertificateRepository.java
└── dto/
    ├── ApplianceCreateRequest.java
    ├── ApplianceResponse.java
    └── HealthCertificateResponse.java

report/
├── service/WorkReportService.java       (repair-history 조회 로직)
└── dto/RepairHistoryResponse.java
```

---

## 테스트 전략

### 단위 테스트 (`ApplianceServiceTest`)

- `@ExtendWith(MockitoExtension.class)`
- 검증 항목:
  - 정상 등록 흐름 (categoryId depth=2 검증 포함)
  - 소분류가 아닌 카테고리 → 400
  - 타인 가전 상세 조회 시도 → 401
  - 타인 가전 삭제 시도 → 401
  - 논리 삭제 후 재조회 → 404
  - 진단서 미발급 가전 건강 진단서 조회 → 404

### 통합 테스트

- `@SpringBootTest` + H2 인메모리
- 가전 등록 → 목록 조회 → 상세 조회 → 삭제 → 재조회(404) 전체 흐름 검증
- 수리 이력 없는 가전의 repair-history → 빈 배열 반환 확인
- 수리 보고서 제출 후 health-certificate 조회 → 4축 점수 포함 응답 확인
