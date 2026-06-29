# 고객 A/S 상태 조회 API 설계

## 개요

고객이 본인 A/S 요청의 **현재 상태를 단건으로 상세 조회**하거나,
**상태 변경 이력(as_status_logs)을 시간순으로 조회**하는 API 2종.

---

## API 1 — 고객 A/S 요청 단건 상세 조회

### 기본 정보

| 항목 | 내용 |
|---|---|
| Method | `GET` |
| Path | `/api/as-requests/{requestId}` |
| 인증 | Bearer JWT (CUSTOMER 권한) |
| 책임 도메인 | `as_request` |

### 처리 흐름

1. JWT에서 `customerId` 추출
2. `requestId`로 `as_requests` 단건 조회 (symptom·customer·appliance·visitRegion JOIN FETCH) — 없으면 404
3. `asRequest.customer_id == customerId` 검증 — 불일치 시 401
4. `as_assignments`에서 REJECTED가 아닌 첫 번째 배정의 기사 정보 추출 (배정 전이면 null)
5. 200 + `CustomerAsRequestDetailResponse` 반환

### 응답 예시 (200 OK)

```json
{
  "requestId": 12,
  "status": "IN_PROGRESS",
  "assignType": "MANUAL",
  "brand": "LG",
  "modelName": "디오스 V870",
  "serialNumber": "LG-2021-00123",
  "purchaseDate": "2021-03-15",
  "warrantyEndDate": "2024-03-15",
  "symptomCode": "REF-001",
  "symptomName": "냉장 기능 불량",
  "symptomDesc": "냉장실 온도가 올라가고 있어요.",
  "imageUrls": null,
  "visitRegionName": "마포구",
  "visitAddressDetail": "공덕동 123-4",
  "scheduledDate": "2026-06-30",
  "scheduledTime": "10:00",
  "engineerName": "김기사",
  "engineerPhone": "010-1234-5678",
  "cancelReason": null,
  "createdAt": "2026-06-29T09:00:00",
  "updatedAt": "2026-06-29T11:30:00"
}
```

> 배정 전 상태이면 `engineerName`, `engineerPhone`은 `null`로 반환된다.

### 오류

| 상황 | HTTP | 예외 |
|---|---|---|
| 존재하지 않는 requestId | 404 | `NoSuchElementException` |
| 본인 요청이 아님 | 401 | `IllegalAccessException` |

---

## API 2 — 고객 A/S 상태 변경 이력 조회

### 기본 정보

| 항목 | 내용 |
|---|---|
| Method | `GET` |
| Path | `/api/as-status-logs/{requestId}` |
| 인증 | Bearer JWT (CUSTOMER 권한) |
| 책임 도메인 | `as_status_log` |

### 처리 흐름

1. JWT에서 `customerId` 추출
2. `requestId`로 `as_requests` 단건 조회 — 없으면 404
3. `asRequest.customer_id == customerId` 검증 — 불일치 시 401
4. `as_status_logs`에서 `request_id` 기준으로 이력 조회 (changedBy JOIN FETCH, 시간 오름차순)
5. 200 + `AsStatusLogListResponse` 반환

### 응답 예시 (200 OK)

```json
{
  "totalCount": 3,
  "logs": [
    {
      "logId": 1,
      "requestId": 12,
      "fromStatus": null,
      "toStatus": "WAITING",
      "memo": "배정 완료",
      "changedByName": "김기사",
      "changedAt": "2026-06-29T10:00:00"
    },
    {
      "logId": 2,
      "requestId": 12,
      "fromStatus": "WAITING",
      "toStatus": "ENGINEER_DEPARTED",
      "memo": "현장 출발",
      "changedByName": "김기사",
      "changedAt": "2026-06-29T11:00:00"
    },
    {
      "logId": 3,
      "requestId": 12,
      "fromStatus": "ENGINEER_DEPARTED",
      "toStatus": "IN_PROGRESS",
      "memo": null,
      "changedByName": "김기사",
      "changedAt": "2026-06-29T11:30:00"
    }
  ]
}
```

> 이력이 없는 경우 `totalCount: 0`, `logs: []` 반환.

### 오류

| 상황 | HTTP | 예외 |
|---|---|---|
| 존재하지 않는 requestId | 404 | `NoSuchElementException` |
| 본인 요청이 아님 | 401 | `IllegalAccessException` |

---

## DB 설계 참조

```sql
-- as_status_logs (핵심 컬럼만)
log_id      BIGINT PK
request_id  BIGINT FK → as_requests.request_id
changed_by  BIGINT FK → users.user_id
from_status ENUM('WAITING','ENGINEER_DEPARTED','ENGINEER_ARRIVED','IN_PROGRESS','COMPLETED') NULL
to_status   ENUM('WAITING','ENGINEER_DEPARTED','ENGINEER_ARRIVED','IN_PROGRESS','COMPLETED') NOT NULL
memo        VARCHAR(255) NULL
created_at  DATETIME DEFAULT CURRENT_TIMESTAMP

INDEX idx_status_log_request (request_id, created_at)
```

---

## 구현 위치

```
as_request/
├── controller/AsRequestController.java          (GET /{requestId} 추가)
├── service/AsRequestService.java                (getMyAsRequestDetail() 추가)
└── dto/
    └── CustomerAsRequestDetailResponse.java     (신규)

as_status_log/
├── controller/AgencyAsStatusLogController.java  (GET /{requestId} 추가)
├── service/AgencyAsStatusLogService.java        (getCustomerStatusLogs() 추가)
└── repository/AsStatusLogRepository.java        (findByRequestIdWithDetails() 추가)
```

---

## 테스트 전략

### 단위 테스트

- `@ExtendWith(MockitoExtension.class)`
- **API 1 검증 항목**:
  - 정상 조회 (배정 전 — engineerName null, 배정 후 — engineerName 포함)
  - 타인 요청 접근 → 401
  - 존재하지 않는 requestId → 404
- **API 2 검증 항목**:
  - 이력 있음 — totalCount, 시간 오름차순 정렬 검증
  - 이력 없음 — totalCount=0, logs=[] 반환
  - 타인 요청 접근 → 401

### 통합 테스트

- `@SpringBootTest` + H2 인메모리
- 상태를 IN_PROGRESS까지 올린 뒤 GET 단건 조회 → status=IN_PROGRESS, 기사 정보 포함 확인
- 상태 변경 이력 INSERT 후 GET 이력 조회 → 시간순 정렬 확인
- 다른 고객의 requestId로 접근 → 401 확인
