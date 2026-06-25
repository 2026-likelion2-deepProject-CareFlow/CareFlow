# Agency A/S 상태 변경 이력 API 설계

## 개요

대행사 관리자 계정으로 로그인한 사용자가 본인 소속 대행사에 접수된 A/S 요청의 **단계별 상태 변경 이력(as_status_logs)**을 조회할 수 있는 API 2종을 제공한다.

---

## API 1 — 소속 대행사 A/S 상태 변경 이력 목록 조회

### 기본 정보

| 항목 | 내용 |
|---|---|
| Method | `GET` |
| Path | `/api/as-status-logs/agency` |
| 인증 | Bearer JWT (AGENCY 권한) |
| 책임 도메인 | `assignment` |

### 처리 흐름

1. JWT에서 `userId` 추출 → `users.agency_id`로 소속 대행사 확인 (AGENCY 권한 강제)
2. `as_requests.agency_id = :agencyId` 조건으로 해당 대행사의 모든 요청 ID 범위 확정
3. `as_status_logs` JOIN `as_requests` JOIN `users(changed_by)` → 이력 목록 반환
4. 정렬: `created_at DESC` (최신 변경 이력이 먼저)

### 응답 예시

```json
{
  "totalCount": 3,
  "logs": [
    {
      "logId": 10,
      "requestId": 5,
      "fromStatus": "WAITING",
      "toStatus": "ENGINEER_DEPARTED",
      "memo": "현장 출발",
      "changedByName": "김기사",
      "changedAt": "2026-06-26T10:30:00"
    }
  ]
}
```

### 오류

| 상황 | HTTP |
|---|---|
| AGENCY 권한 없음 | 401 |
| 소속 대행사 없음 | 403 |
| 사용자 없음 | 404 |

---

## API 2 — 소속 대행사 A/S 상태별 집계

### 기본 정보

| 항목 | 내용 |
|---|---|
| Method | `GET` |
| Path | `/api/as-status-logs/agency/status-summary` |
| 인증 | Bearer JWT (AGENCY 권한) |
| 책임 도메인 | `assignment` |

### 처리 흐름

1. JWT → 소속 `agencyId` 추출 (API 1과 동일)
2. `as_status_logs.to_status` 컬럼 기준 GROUP BY COUNT 집계
   - 집계 대상 상태: `WAITING`, `ENGINEER_DEPARTED`, `ENGINEER_ARRIVED`, `IN_PROGRESS`, `COMPLETED`
   - 이력이 없는 상태는 0으로 채움
3. 결과 반환

### 응답 예시

```json
{
  "waitingCount": 12,
  "engineerDepartedCount": 8,
  "engineerArrivedCount": 7,
  "inProgressCount": 5,
  "completedCount": 20
}
```

### 오류

| 상황 | HTTP |
|---|---|
| AGENCY 권한 없음 | 401 |
| 소속 대행사 없음 | 403 |
| 사용자 없음 | 404 |

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
created_at  DATETIME NOT NULL

INDEX idx_status_log_request (request_id, created_at)
```

---

## 구현 위치

```
assignment/
├── controller/AgencyAsStatusLogController.java  (신규)
├── service/AgencyAsStatusLogService.java         (신규)
├── repository/AsStatusLogRepository.java         (쿼리 추가)
├── dto/
│   ├── AsStatusLogListResponse.java              (신규)
│   └── AsStatusLogSummaryResponse.java           (신규)
```

---

## 테스트 전략

> **JUnit5 단위 테스트**와 **H2 기반 통합 테스트** 모두 작성한다.

### 단위 테스트 (`AgencyAsStatusLogServiceTest`)

- `@ExtendWith(MockitoExtension.class)` + `@InjectMocks` / `@Mock`
- Repository mock 주입으로 서비스 로직만 격리 검증
- 검증 항목:
  - `getStatusLogs()`: 정상 반환, AGENCY 권한 없을 때 401, 소속 대행사 없을 때 403
  - `getStatusSummary()`: 정상 집계(5개 상태 모두 포함/일부 누락 시 0 채움), 권한 오류

### 통합 테스트 (`AgencyAsStatusLogIntegrationTest`)

- `@SpringBootTest` + `@AutoConfigureMockMvc` + `@ActiveProfiles("local")`
- H2 인메모리 DB에 실제 데이터 삽입 후 HTTP 호출 → 응답 검증
- `@Sql(scripts = "/as_status_log_cleanup.sql", ...)` 각 테스트 전 초기화
- 검증 항목:
  - 200 OK + `totalCount`, `logs` 배열 내용 검증
  - 이력이 없을 때 빈 배열 + `totalCount = 0` 반환
  - 다른 대행사 이력 미포함 검증
  - AGENCY 이외 권한 → 401
  - 상태별 집계 수치 정확성 검증
  - 이력이 없을 때 모든 count = 0 반환
