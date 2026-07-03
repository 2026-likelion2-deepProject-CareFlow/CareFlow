# API: 인증 뱃지 기준 조회

## 개요

관리자(ADMIN)가 **가전 건강 진단서(Health Certificate)의 "안심 인증 뱃지" 부여 기준**을 조회한다. 이 기준(`minGrade`, `minScore`)은 Redis에 저장되며, 작업 완료 보고서 제출/취소 시 `HealthCertificate.recalculate(...)`에서 읽어 **인증 뱃지 부여 여부(`is_certified`)**를 판정하는 데 사용된다.
위치: Admin 인증 뱃지 기준 설정 페이지.

> **뱃지 부여 규칙**: `is_certified = (score >= minScore) AND (grade <= minGrade)`
> 등급은 점수로 산출된다: `≥90 → A`, `≥75 → B`, `≥60 → C`, `≥40 → D`, 그 외 `E`.
> 등급 비교는 문자열 사전순(`A < B < C < D < E`)이며, `grade.compareTo(minGrade) <= 0` 즉 **minGrade와 같거나 더 좋은(상위) 등급**이면 통과한다. 예: `minGrade="B"`면 A·B 등급만 뱃지 부여.

---

## 엔드포인트

```
GET /api/admin/badge-criteria
```

---

## 인증/인가

- **필수 인증**: JWT Bearer 토큰
- **허용 역할**: `ADMIN`
- `SecurityConfig`의 `hasRole("ADMIN")` 1차 차단 + 컨트롤러 `checkAdminRole()` 2차 방어 → 아니면 `IllegalAccessException`

---

## 요청

### 경로 변수 / 쿼리 파라미터

- 없음.

### 요청 예시

```
GET /api/admin/badge-criteria
Authorization: Bearer {accessToken}
```

---

## 알려진 제약 (구현 전 반드시 인지할 것)

- **저장소는 Redis**: 관계형 DB가 아니라 Redis String 키 **`admin:badge:criteria`**에 JSON으로 저장된다(TTL 없음, 영구).
- **기본값 폴백**: 키가 없으면(`get()`이 null) **`{ "minGrade": "B", "minScore": 75 }`**를 반환한다. 이는 `HealthCertificate` 재계산부의 하드코딩 기본값(`minGrade="B"`, `minScore=75`)과 동일하다.
- **`minGrade` 값 범위**: 등급 문자 1글자(`A`~`E`)를 기대한다. 다만 **현재 구현에는 값 유효성 검증이 없어** Redis에 저장된 값을 그대로 역직렬화하여 반환한다.
- **`minScore` 값 범위**: 건강 점수는 4개 축 합산으로 0~100 범위지만, 역시 **저장 시 범위 검증 없음**.
- **역직렬화 대상**: `AdminBadgeCriteriaController.BadgeCriteriaDto` 레코드(`minGrade`, `minScore`)로 `ObjectMapper.readValue` 처리.

---

## 응답

### 200 OK (Redis에 저장된 값이 있는 경우)

```json
{
  "minGrade": "A",
  "minScore": 90
}
```

### 200 OK (Redis에 값이 없는 경우 — 기본값)

```json
{
  "minGrade": "B",
  "minScore": 75
}
```

### 응답 필드 설명

| 필드 | 타입 | 설명 |
|---|---|---|
| `minGrade` | String | 뱃지 부여 최소 등급(이 등급과 같거나 상위여야 통과). 예: `"B"` |
| `minScore` | Integer | 뱃지 부여 최소 점수(이상이어야 통과). 예: `75` |

### 에러 응답

| HTTP 상태 | 조건 |
|---|---|
| 401 Unauthorized | JWT 토큰 없음/만료 |
| 403 Forbidden | 인증되었으나 role != ADMIN (`hasRole("ADMIN")` 차단) |

---

## 처리 로직 (Pipeline)

1. **검증**: `checkAdminRole()` → 아니면 `IllegalAccessException`
2. **Redis 조회**: `redisTemplate.opsForValue().get("admin:badge:criteria")`
3. **폴백**: null 이면 `new BadgeCriteriaDto("B", 75)` 반환
4. **역직렬화**: 값이 있으면 `objectMapper.readValue(json, BadgeCriteriaDto.class)` 반환

---

## 개발 구성요소

| 계층 | 클래스 |
|---|---|
| Controller | `com.careflow.admin.controller.AdminBadgeCriteriaController#getCriteria` |
| Store | Redis String 키 `admin:badge:criteria` (`StringRedisTemplate`) |
| DTO | `com.careflow.admin.controller.AdminBadgeCriteriaController.BadgeCriteriaDto` |
| 소비처(Consumer) | `com.careflow.report.service.WorkReportService#syncHealthCertificate` → `HealthCertificate#recalculate` |

---

## 테스트 명세

### JUnit 5 컨트롤러 테스트 (`@WebMvcTest`)

**파일**: `src/test/java/com/careflow/admin/controller/AdminBadgeCriteriaControllerTest.java`

- TC-C-1. Redis에 값 없음 → 기본값(`minGrade="B"`, `minScore=75`) 반환 (기존 구현됨)
- TC-C-2. Redis에 저장된 JSON 존재 → 역직렬화하여 그대로 반환(`$.minGrade`, `$.minScore` 검증)
- TC-C-3. 인증 없음 → 401
- TC-C-4. ADMIN 아님 → 403

> `StringRedisTemplate`을 `@MockitoBean`으로 주입하고 `given(stringRedisTemplate.opsForValue()).willReturn(valueOperations)` → `given(valueOperations.get("admin:badge:criteria")).willReturn(...)`로 모킹한다.
