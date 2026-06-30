# 고객용 수동 배정 - 선택 기사 가능 일정 조회

> 브랜치: `feature/as_manualFlow`
> 작성일: 2026-06-30
> 관련 컨트롤러: `CustomerController` (`/api/customers/{customerId}`)

---

## 개요

고객이 [후보 기사 목록 조회](./customer-engineer-available-list.md)에서 기사 카드를 선택했을 때,
그 기사가 방문 가능한 날짜와 시간대를 조회하는 API.

프론트엔드 `CustomerAS.jsx`에서 기사 선택 후 노출되는 "가능 날짜 → 가능 시간대" 선택 UI에 대응한다.
조회된 날짜/시간을 최종적으로 `POST /api/as-requests` 호출 시 `scheduledDate`/`scheduledTime`으로 사용한다.

---

## 기본 정보

| 항목 | 내용 |
|---|---|
| Method | `GET` |
| URI | `/api/customers/{customerId}/engineers/{engineerId}/availability` |
| 인증 | Bearer JWT (`CUSTOMER` 권한) |
| 책임 도메인 | `user` (컨트롤러) / `engineer` (조회 로직) |

## 인증 / 권한

- `@AuthenticationPrincipal CustomUserDetails`로 로그인 사용자 식별
- 별도의 소유권 검증은 없음 — 기사 가용 일정은 비공개 정보가 아니므로 로그인한 고객이면 누구나 조회 가능

## 요청

### Path Variables

| 변수 | 타입 | 설명 |
|---|---|---|
| `customerId` | Long | 로그인한 고객 ID (실제 인증은 JWT 기준) |
| `engineerId` | Long | 조회 대상 기사의 `user_id` |

### Query Parameters

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `from` | LocalDate (`yyyy-MM-dd`) | ❌ | 조회 시작일. 미지정 시 오늘 날짜 |
| `to` | LocalDate (`yyyy-MM-dd`) | ❌ | 조회 종료일. 미지정 시 `from` + 27일(4주 범위) |

### Request Body

없음

## 처리 흐름

1. `engineerId`로 `EngineerProfile` 존재 여부 확인 (없으면 404)
2. `EngineerSchedule.findByUser_IdAndWorkDateBetweenOrderByWorkDateAsc(engineerId, from, to)` 조회
3. `status = AVAILABLE`인 근무표만 필터
4. 각 근무표의 `timeSlots`를 `"HH:mm"` 문자열 리스트로 변환
5. 날짜(`workDate`)를 key로 하는 Map 형태로 응답 구성

> 이미 배정된 시간대를 슬롯 단위로 제외하는 로직은 현재 AUTO 배정 로직(`EngineerProfileRepository.findByAllConditions`)에도
> 존재하지 않는 범위이므로(근무표 `status` 단위로만 가용성 판단) 본 API도 동일 기준을 따른다.
> 추후 슬롯 단위 중복 배정 방지가 필요해지면 별도 이슈로 분리한다.

## 응답

**Response Body** (`200 OK`): `CustomerEngineerAvailabilityResponse`

```json
{
  "engineerId": 12,
  "availableDates": {
    "2026-07-01": ["09:00", "11:00", "14:00"],
    "2026-07-02": ["10:00"]
  }
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `engineerId` | Long | 조회 대상 기사 ID |
| `availableDates` | Map\<String, List\<String\>\> | 날짜(`yyyy-MM-dd`) → 그 날 가능한 시작 시각(`HH:mm`) 목록. 등록된 시간 슬롯이 없는 AVAILABLE 근무표는 빈 배열로 표기 |

가능한 일정이 없으면 `availableDates`가 빈 객체(`{}`)로 반환된다 (404 아님).

## 에러 응답

| 상태코드 | 원인 |
|---|---|
| 401 | JWT 없음 / 만료 / CUSTOMER 역할 아님 |
| 400 | `from` > `to` |
| 404 | `engineerId`에 해당하는 기사 프로필 없음 |
