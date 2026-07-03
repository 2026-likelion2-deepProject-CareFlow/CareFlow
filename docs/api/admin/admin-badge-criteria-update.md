# API: 인증 뱃지 기준 수정

## 개요

관리자(ADMIN)가 **가전 건강 진단서의 "안심 인증 뱃지" 부여 기준**(`minGrade`, `minScore`)을 수정한다. 저장된 값은 Redis에 영구 반영되어, 이후 작업 완료 보고서 제출/취소 시 `HealthCertificate.recalculate(...)`에서 읽혀 뱃지 부여 여부 판정에 사용된다.
위치: Admin 인증 뱃지 기준 설정 페이지 — 저장 버튼.

> **주의**: 본 API는 기준값만 갱신할 뿐, **이미 발급된 진단서(`is_certified`)를 소급 재계산하지 않는다.** 각 가전의 뱃지 여부는 해당 가전에 대한 **다음 보고서 제출/취소 이벤트가 발생할 때** 새 기준으로 재산출된다.

---

## 엔드포인트

```
PUT /api/admin/badge-criteria
```

---

## 인증/인가

- **필수 인증**: JWT Bearer 토큰
- **허용 역할**: `ADMIN`
- `SecurityConfig`의 `hasRole("ADMIN")` 1차 차단 + 컨트롤러 `checkAdminRole()` 2차 방어 → 아니면 `IllegalAccessException`

---

## 요청

### 요청 바디

`Content-Type: application/json`

```json
{
  "minGrade": "A",
  "minScore": 90
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `minGrade` | String | O | 뱃지 부여 최소 등급(`A`~`E`) |
| `minScore` | Integer | O | 뱃지 부여 최소 점수(0~100 권장) |

### 요청 예시

```
PUT /api/admin/badge-criteria
Authorization: Bearer {accessToken}
Content-Type: application/json

{ "minGrade": "A", "minScore": 90 }
```

---

## 알려진 제약 (구현 전 반드시 인지할 것)

- **저장소는 Redis**: 요청 바디(`BadgeCriteriaDto`)를 `ObjectMapper`로 직렬화하여 String 키 **`admin:badge:criteria`**에 저장한다. **TTL 없음(영구 저장)**.
- **유효성 검증 없음(현재 구현)**: `minGrade`가 `A`~`E` 범위인지, `minScore`가 0~100인지 등 **입력값 검증 로직이 없다.** 잘못된 값을 저장하면 이후 `HealthCertificate.recalculate`의 등급 비교(`grade.compareTo(minGrade)`)가 의도치 않게 동작할 수 있으므로, 필요 시 `@Valid` DTO + 커스텀 검증 추가를 권장한다.
- **응답 바디 없음**: 성공 시 `200 OK`에 **빈 본문**(`ResponseEntity<Void>`)을 반환한다. 저장 후 현재 값을 확인하려면 [조회 API](admin-badge-criteria-get.md)를 재호출한다.
- **덮어쓰기 방식**: `opsForValue().set(key, json)`으로 기존 값을 통째로 대체한다(부분 수정 아님). `minGrade`/`minScore` 둘 다 보내야 한다.
- **소급 재계산 없음**: 위 개요의 주의 참조 — 기존 진단서는 다음 이벤트 시점에 반영된다.

---

## 응답

### 200 OK

- 본문 없음 (`ResponseEntity.ok().build()`).

### 에러 응답

| HTTP 상태 | 조건 |
|---|---|
| 401 Unauthorized | JWT 토큰 없음/만료 |
| 403 Forbidden | 인증되었으나 role != ADMIN (`hasRole("ADMIN")` 차단) |

---

## 처리 로직 (Pipeline)

1. **검증**: `checkAdminRole()` → 아니면 `IllegalAccessException`
2. **직렬화**: `objectMapper.writeValueAsString(dto)` — `BadgeCriteriaDto(minGrade, minScore)` → JSON
3. **Redis 저장**: `redisTemplate.opsForValue().set("admin:badge:criteria", json)` (TTL 없음)
4. **반환**: `200 OK` (빈 본문)

---

## 개발 구성요소

| 계층 | 클래스 |
|---|---|
| Controller | `com.careflow.admin.controller.AdminBadgeCriteriaController#updateCriteria` |
| Store | Redis String 키 `admin:badge:criteria` (`StringRedisTemplate`) |
| DTO | `com.careflow.admin.controller.AdminBadgeCriteriaController.BadgeCriteriaDto` |
| 소비처(Consumer) | `com.careflow.report.service.WorkReportService#syncHealthCertificate` → `HealthCertificate#recalculate` |

---

## 테스트 명세

### JUnit 5 컨트롤러 테스트 (`@WebMvcTest`)

**파일**: `src/test/java/com/careflow/admin/controller/AdminBadgeCriteriaControllerTest.java`

- TC-C-1. 정상 저장 — `{ "minGrade": "A", "minScore": 90 }` PUT 시 200 OK, `verify(valueOperations).set("admin:badge:criteria", json)` 호출 검증 (기존 구현됨)
- TC-C-2. 인증 없음 → 401
- TC-C-3. ADMIN 아님 → 403

> `StringRedisTemplate`을 `@MockitoBean`으로 주입하고 `given(stringRedisTemplate.opsForValue()).willReturn(valueOperations)`로 모킹한 뒤, `set(...)` 호출 여부를 `verify`로 확인한다.
