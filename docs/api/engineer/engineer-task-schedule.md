# API: 수리 기사 본인 A/S 작업 일정 조회

## 개요

수리 기사가 특정 날짜에 자신에게 배정된 A/S 작업 목록을 조회한다.
고객 정보, 제품 정보, 방문 주소, 예약 시간 등 현장 방문에 필요한 전체 정보를 반환한다.
거절(REJECTED)된 배정 건은 목록에서 제외한다.

---

## 엔드포인트

```
GET /api/engineer/schedule?date=2026-06-01
```

---

## 인증/인가

- **필수 인증**: JWT Bearer 토큰
- **허용 역할**: `ENGINEER`

---

## 요청

### 쿼리 파라미터

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `date` | String (ISO_DATE) | Y | 조회 날짜 (yyyy-MM-dd 형식) |

### 요청 예시

```
GET /api/engineer/schedule?date=2026-06-01
Authorization: Bearer {access_token}
```

---

## 응답

### 200 OK — 작업 있음

```json
[
  {
    "requestId": 101,
    "scheduledDate": "2026-06-01",
    "scheduledTime": "10:00",
    "customerName": "홍길동",
    "customerPhone": "010-1234-5678",
    "applianceBrand": "삼성",
    "applianceModelName": "비스포크 냉장고",
    "symptomName": "냉방 불량",
    "visitRegionName": "강남구",
    "visitAddressDetail": "테헤란로 123 101호",
    "requestStatus": "ACCEPTED",
    "assignmentStatus": "ACCEPTED"
  }
]
```

### 200 OK — 해당 날짜 배정 없음

```json
[]
```

### 응답 필드 설명

| 필드 | 타입 | 설명 |
|---|---|---|
| `requestId` | Long | A/S 요청 ID |
| `scheduledDate` | String (yyyy-MM-dd) | 방문 예약 날짜 |
| `scheduledTime` | String (HH:mm) | 방문 예약 시간 |
| `customerName` | String | 고객 이름 |
| `customerPhone` | String | 고객 연락처 |
| `applianceBrand` | String | 가전 브랜드명 |
| `applianceModelName` | String | 가전 모델명 |
| `symptomName` | String | 증상명 (한글) |
| `visitRegionName` | String | 방문 지역명 (구 단위) |
| `visitAddressDetail` | String | 방문 상세 주소 |
| `requestStatus` | String | A/S 요청 현재 상태 (AsStatus enum) |
| `assignmentStatus` | String | 배정 상태 (WAITING / ACCEPTED / COMPLETED) |

### 에러 응답

| HTTP 상태 | 조건 |
|---|---|
| 400 Bad Request | `date` 파라미터 누락 또는 형식 오류 (yyyy-MM-dd 아닌 경우) |
| 401 Unauthorized | JWT 토큰 없음 또는 만료 |

---

## 구현 위치

| 계층 | 클래스 |
|---|---|
| Controller | `com.careflow.as_request.controller.EngineerTaskController` |
| Service | `com.careflow.as_request.service.EngineerTaskScheduleService` (신규) |
| Repository | `com.careflow.assignment.repository.AsAssignmentRepository` (메서드 추가) |
| DTO | `com.careflow.as_request.dto.EngineerTaskScheduleResponse` (신규) |

### Repository 추가 메서드

```java
// as_assignments.engineer_id = engineerUserId
// AND as_requests.scheduled_date = date
// AND as_assignments.status != 'REJECTED'
List<AsAssignment> findByEngineer_IdAndAsRequest_ScheduledDateAndStatusNot(
    Long engineerUserId, LocalDate date, String status);
```

---

## 테스트 명세

> **준수 사항**: 단위 테스트와 통합 테스트를 모두 작성한다.
> - 단위 테스트: `@WebMvcTest` + `@MockitoBean` (Spring Boot 3.4+ 스타일)
> - 통합 테스트: `@SpringBootTest` + `@AutoConfigureMockMvc` + H2 인메모리 DB (실제 데이터 삽입·조회 검증)

---

### JUnit 5 단위 테스트 (Controller Slice — @WebMvcTest)

**파일**: `src/test/java/com/careflow/as_request/controller/EngineerTaskScheduleControllerTest.java`

#### TC-1. 성공: 해당 날짜 배정 2건 — 200 OK + 배열 반환
- Given: Service Mock → 2건 반환
- When: `GET /api/engineer/schedule?date=2026-06-01` (ENGINEER 인증)
- Then: status 200, `$.length()` == 2, `$[0].customerName` 검증

#### TC-2. 성공: 해당 날짜 배정 없음 — 200 OK + 빈 배열
- Given: Service Mock → 빈 리스트 반환
- When: `GET /api/engineer/schedule?date=2026-06-01`
- Then: status 200, `$.length()` == 0

#### TC-3. 실패: date 파라미터 누락 — 400 Bad Request
- When: `GET /api/engineer/schedule` (date 없음)
- Then: status 400

#### TC-4. 실패: date 형식 오류 — 400 Bad Request
- When: `GET /api/engineer/schedule?date=20260601` (형식 틀림)
- Then: status 400

#### TC-5. 실패: 인증 없음 — 401 Unauthorized
- When: `GET /api/engineer/schedule?date=2026-06-01` (토큰 없음)
- Then: status 401

---

### JUnit 5 통합 테스트 (H2 DB — @SpringBootTest)

**파일**: `src/test/java/com/careflow/as_request/controller/EngineerTaskScheduleIntegrationTest.java`

> H2 DB에 기사·고객·가전·증상·배정 데이터를 직접 삽입하여 실제 쿼리 결과를 검증한다.

#### TC-6. 성공: 당일 배정 작업 2건 반환 — 내용 검증
- Given: engineer 사용자 저장, 해당 날짜 배정 2건(ACCEPTED 상태) 저장
- When: `GET /api/engineer/schedule?date={date}` (기사 JWT)
- Then: status 200, 반환 건수 2, customerName·symptomName 등 필드값 DB 저장값과 일치

#### TC-7. 성공: REJECTED 배정은 결과에서 제외
- Given: 해당 날짜 배정 3건 저장 — ACCEPTED 2건, REJECTED 1건
- When: `GET /api/engineer/schedule?date={date}`
- Then: status 200, 반환 건수 2 (REJECTED 제외 확인)

#### TC-8. 성공: 타 기사 배정은 결과에 포함되지 않음 — 데이터 격리
- Given: 기사A·기사B 각각 배정 1건 저장 (같은 날짜)
- When: 기사A 토큰으로 조회
- Then: 기사A 건만 1건 반환

#### TC-9. 성공: 다른 날짜 배정은 포함되지 않음
- Given: 기사에게 6월1일·6월2일 각 1건 배정
- When: `GET /api/engineer/schedule?date=2026-06-01`
- Then: 반환 건수 1 (6월1일 건만)

#### TC-10. 성공: 배정 없는 날짜 — 빈 배열 반환
- Given: 해당 날짜 배정 없음
- When: `GET /api/engineer/schedule?date=2026-06-01`
- Then: status 200, 빈 배열 `[]`
