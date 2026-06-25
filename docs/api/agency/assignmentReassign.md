# 🚀 API 생성 및 개발 요구사항 정의서

## 1. 배경 및 목적

배차를 받은 수리 기사가 30분 내 응답 없거나 배차를 거부(`status = REJECTED`)한 경우,
대행사 관리자가 해당 배차 건에 대해 **재배차**를 요청한다.

- 30분 초과인지 거부인지 **클라이언트에서 `assigned_at`, `status` 필드를 통해 판단**하므로 서버는 구분 없이 동일하게 처리
- **단, 재배차 방식은 원래 A/S 요청의 `assign_type` 에 따라 분기**

## 2. 데이터 모델 (Schema)
- 메인 스키마 정의 파일 : `sql/CareFlow_DDL_v5.sql`
- 참조 테이블 : `as_assignments`, `as_requests`, `notifications`
  - `as_assignments.status` : WAITING / ACCEPTED / REJECTED / COMPLETED
  - `as_requests.assign_type` : AUTO / MANUAL (재배차 방식 결정 기준)
  - `notifications.type` : AS_STATUS (재배차 관련 고객 알림)

## 3. API 엔드포인트 명세

### [POST] /api/assignment/{assignmentId}/reassign

- **설명** : 배차 응답 없음(30분 초과) 또는 거부(REJECTED)된 배차 건에 대해 재배차 수행
- **Path Variable** : `assignmentId` — 재배차 대상 기존 배차 ID
- **Request Body** : 없음 (재배차 사유는 클라이언트가 이미 판단한 상태로 전달)
- **인증** : JWT, `role = AGENCY`

### Response

| 시나리오 | 상태코드 | 설명 |
|---|---|---|
| AUTO 재배차 성공 | 200 OK | 새 배차 생성, `newAssignmentId` 반환 |
| MANUAL → 고객 알림 발송 | 200 OK | 재배차 없음, 고객에게 재신청 알림 발송, `notificationId` 반환 |
| AUTO 재배차 실패 (가용 기사 없음) | 403 Forbidden | `IllegalStateException` → `GlobalExceptionHandler` → 403 |
| 배차 없음 | 404 Not Found | assignmentId 미존재 |
| 권한 없음 | 401 Unauthorized | role != AGENCY |

## 4. 상세 처리 로직 (Pipeline)

### 공통 전처리
1. JWT에서 `role` 추출 → `AGENCY` 아니면 401
2. `assignmentId`로 `as_assignments` 조회 (JOIN FETCH asRequest, engineer, agency) → 없으면 404
3. `as_request.assign_type` 확인 → AUTO / MANUAL 분기

### 분기 A: assign_type = AUTO (자동 재배차)
4-A-1. **이전 배차 기사 목록 조회**: 동일 `request_id`로 연결된 모든 `as_assignments`에서 `engineer_id` 수집
         → `Set<Long> excludeEngineerIds`
4-A-2. **자동 배차 후보 탐색**: 기존 4단계 Fallback 로직 재활용 (단, `excludeEngineerIds` 제외)
4-A-3. **복합 점수로 최적 기사 선택**: 기존 `selectByCompositeScore()` 로직 재활용
4-A-4. **새 AsAssignment 생성 및 저장** (기존 배차 레코드는 삭제하지 않고 이력으로 보존)
4-A-5. **응답 반환**: `{ result: "REASSIGNED", newAssignmentId: xxx, message: "재배차가 완료되었습니다." }`

### 분기 B: assign_type = MANUAL (재배차 불가 → 고객 알림)
4-B-1. **Notification 저장**: 고객(`as_request.customer`)에게 알림 발송
   - `user_id`: 고객 ID
   - `type`: AS_STATUS
   - `title`: "A/S 재신청 필요"
   - `body`: "요청하신 A/S 건(요청번호: {requestId})의 수리 기사 배정이 완료되지 못했습니다. 수리 기사를 다시 선택하여 A/S를 재신청해 주세요."
   - `channel`: SSE
4-B-2. **응답 반환**: `{ result: "MANUAL_NOTIFIED", notificationId: xxx, message: "고객에게 재신청 안내 알림을 발송했습니다." }`

## 5. AUTO 재배차 Fallback 전략

기존 `AsRequestService.processAutoAssignment()` 4단계 Fallback을 **재사용**, 단 각 단계에서 `excludeEngineerIds`를 제외:

| 단계 | 조건 | 추가 제외 조건 |
|---|---|---|
| Fallback 0 | 스케줄 + 브랜드 + 카테고리 + 서비스 지역 | `AND u.id NOT IN :excludeIds` |
| Fallback 1 | 스케줄 + 카테고리 + 서비스 지역 (브랜드 제거) | `AND u.id NOT IN :excludeIds` |
| Fallback 2 | 스케줄 + 카테고리 (브랜드 + 지역 제거) | `AND u.id NOT IN :excludeIds` |
| Fallback 3 | 스케줄만 | `AND u.id NOT IN :excludeIds` |
| Fallback 4 | 모두 완화 후에도 없음 → `IllegalStateException` | — |

### 기존 기능을 건드리지 않는 구현 방법
- `EngineerProfileRepository`에 `*Excluding` 버전 메서드 4개 추가 (각각 `Set<Long> excludeIds` 파라미터 포함)
- `AsRequestService`의 기존 메서드(`processAutoAssignment`)는 일체 변경하지 않음
- 재배차 전용 서비스(`AssignmentReassignService`)에서 자체적으로 Fallback 탐색을 수행
  - `excludeIds`가 비어있지 않을 때: Excluding 버전 메서드 호출
  - 위 케이스에서도 비어있으면 기존 메서드와 동일하게 동작하지만, 재배차 상황에서는 항상 1명 이상이 exists

## 6. Response DTO (`AssignmentReassignResponse`)

| 필드 | 타입 | 설명 |
|---|---|---|
| `result` | `String` | `"REASSIGNED"` 또는 `"MANUAL_NOTIFIED"` |
| `newAssignmentId` | `Long` (nullable) | AUTO 재배차 시 생성된 새 배차 ID |
| `notificationId` | `Long` (nullable) | MANUAL 시 발송된 알림 ID |
| `message` | `String` | 처리 결과 메시지 (한글) |

## 7. 신규 생성 파일 목록

| 파일 | 역할 |
|---|---|
| `notification/entity/Notification.java` | notifications 테이블 엔티티 |
| `notification/repository/NotificationRepository.java` | 알림 저장 |
| `assignment/dto/AssignmentReassignResponse.java` | 재배차 응답 DTO |
| `assignment/service/AssignmentReassignService.java` | 재배차 핵심 로직 |

## 8. 수정 파일 목록

| 파일 | 변경 내용 |
|---|---|
| `EngineerProfileRepository.java` | `findByAllConditionsExcluding` 등 4개 메서드 추가 |
| `AssignmentController.java` | `POST /api/assignment/{assignmentId}/reassign` 엔드포인트 추가 |
| `assignment_cleanup.sql` (테스트) | notifications 테이블 클린업 추가 |

## 9. 예외 처리
- 모든 에러: `{ "success": false, "message": "..." }` 포맷 (`GlobalExceptionHandler` 처리)
- 트랜잭션: 재배차 저장 + 알림 저장을 하나의 트랜잭션으로 처리
