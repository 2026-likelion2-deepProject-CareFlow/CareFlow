# API: 소속 기사 월간 근무표 조회 (대행사 관리자용)

## 개요

대행사 관리자가 소속 기사의 특정 월 근무 일정을 조회한다.
배차 가용 여부 판단의 기준으로 활용된다.

---

## 엔드포인트

```
GET /api/agencies/me/engineers/{engineerUserId}/schedules
```

---

## 인증/인가

- **필수 인증**: JWT Bearer 토큰
- **허용 역할**: `AGENCY`
- 조회 대상 기사가 로그인한 대행사 관리자와 동일 대행사 소속인지 검증한다.

---

## 요청

### 경로 파라미터

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `engineerUserId` | Long | Y | 조회할 기사의 user_id |

### 쿼리 파라미터

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `year` | int | Y | 조회 연도 (예: 2026) |
| `month` | int | Y | 조회 월 (1~12) |

### 요청 예시

```
GET /api/agencies/me/engineers/10/schedules?year=2026&month=6
Authorization: Bearer {access_token}
```

---

## 응답

### 200 OK

```json
[
  {
    "scheduleId": 201,
    "workDate": "2026-06-03",
    "timeSlots": [
      { "start": "09:00", "end": "12:00" },
      { "start": "13:00", "end": "18:00" }
    ],
    "status": "AVAILABLE"
  },
  {
    "scheduleId": 202,
    "workDate": "2026-06-05",
    "timeSlots": [
      { "start": "10:00", "end": "15:00" }
    ],
    "status": "BOOKED"
  }
]
```

### 응답 필드 설명

| 필드 | 타입 | 설명 |
|---|---|---|
| `scheduleId` | Long | 근무표 ID |
| `workDate` | LocalDate | 근무 날짜 |
| `timeSlots` | List | 근무 가능 시간 슬롯 목록 |
| `timeSlots[].start` | String | 시작 시간 (HH:mm) |
| `timeSlots[].end` | String | 종료 시간 (HH:mm) |
| `status` | String | 근무 상태 (AVAILABLE / BOOKED / OFF) |

> 해당 월에 등록된 근무표가 없으면 빈 배열 `[]` 을 반환한다.

### 에러 응답

| HTTP 상태 | 조건 |
|---|---|
| 401 Unauthorized | JWT 토큰 없음 또는 만료 |
| 403 Forbidden | AGENCY 역할이 아닌 경우, 또는 타 대행사 소속 기사 조회 시도 |
| 404 Not Found | `engineerUserId`에 해당하는 유저가 없는 경우 |

---

## 구현 위치

| 계층 | 클래스 |
|---|---|
| Controller | `com.careflow.agency.controller.AgencyEngineerController` |
| Service | `com.careflow.agency.service.AgencyEngineerService` |
| Repository | `com.careflow.engineer.repository.EngineerScheduleRepository` (기존 `findByUser_IdAndWorkDateBetweenOrderByWorkDateAsc` 재사용) |
| DTO | `com.careflow.engineer.dto.ScheduleResponse` (기존 재사용) |

---

## 테스트 명세

### JUnit 5 단위 테스트 (Service Layer)

**파일**: `src/test/java/com/careflow/agency/service/AgencyEngineerServiceTest.java`

#### TC-1. 정상 조회 — 해당 월 근무표 반환
- Given: agencyId=1 소속 engineerUserId=10, 2026년 6월 근무표 3건 Mock
- When: `getAgencyEngineerSchedules(agencyUserId, 10, 2026, 6)` 호출
- Then: 반환 리스트 size == 3, workDate가 6월 범위 내 확인

#### TC-2. 근무표 없음 — 빈 리스트 반환
- Given: engineerUserId=10 의 2026년 6월 근무표 없음
- When: `getAgencyEngineerSchedules(agencyUserId, 10, 2026, 6)` 호출
- Then: 빈 리스트 반환 (예외 아님)

#### TC-3. 타 대행사 기사 조회 시도 — 예외
- Given: 대상 기사의 agency.id != 로그인 대행사의 id
- When: `getAgencyEngineerSchedules(...)` 호출
- Then: `IllegalAccessException` 발생

#### TC-4. 존재하지 않는 기사 — 예외
- Given: engineerUserId=999 에 해당하는 User 없음
- When: `getAgencyEngineerSchedules(agencyUserId, 999, 2026, 6)` 호출
- Then: `NoSuchElementException` 발생

---

### JUnit 5 통합 테스트 (Controller Layer — @WebMvcTest)

**파일**: `src/test/java/com/careflow/agency/controller/AgencyEngineerControllerTest.java`

#### TC-5. 정상 조회 — 200 OK + 근무표 반환
- Given: `@WithMockUser(roles = "AGENCY")`, Service Mock → 근무표 2건 반환
- When: `GET /api/agencies/me/engineers/10/schedules?year=2026&month=6`
- Then: status 200, JSON 배열 size 2

#### TC-6. year/month 파라미터 누락 — 400
- When: `GET /api/agencies/me/engineers/10/schedules` (year/month 없음)
- Then: status 400

#### TC-7. 타 대행사 기사 조회 — 403
- Given: Service가 `IllegalAccessException` 던지도록 Mock
- When: `GET /api/agencies/me/engineers/20/schedules?year=2026&month=6`
- Then: status 403

#### TC-8. 존재하지 않는 기사 — 404
- Given: Service가 `NoSuchElementException` 던지도록 Mock
- When: `GET /api/agencies/me/engineers/999/schedules?year=2026&month=6`
- Then: status 404
