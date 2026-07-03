# API: 수리 비용 가이드 금액 수정

## 개요

관리자(ADMIN)가 [수리 비용 가이드 목록](admin-repair-costs-list.md)에서 특정 증상의 **평균 예상 수리 비용(`avgCost`)을 수동으로 보정**한다. Quartz 자동 집계값이 실제 시세와 맞지 않을 때 관리자가 직접 덮어쓰는 용도다.
위치: Admin 수리 비용 가이드 관리 페이지 — 각 행의 금액 인라인 편집.

---

## 엔드포인트

```
PATCH /api/admin/repair-costs/{id}
```

---

## 인증/인가

- **필수 인증**: JWT Bearer 토큰
- **허용 역할**: `ADMIN`
- `SecurityConfig`의 `hasRole("ADMIN")` 1차 차단 + 컨트롤러 `checkAdminRole()` 2차 방어 → 아니면 `IllegalAccessException`
- 컨트롤러 메서드에 `@Transactional`(쓰기 트랜잭션 — 더티 체킹으로 반영)

---

## 요청

### 경로 변수

| 변수 | 타입 | 설명 |
|---|---|---|
| `id` | Long | 예상 수리 비용 PK (`repair_cost_id`) |

### 요청 바디

`Content-Type: application/json`

```json
{
  "avgCost": 90000
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `avgCost` | Integer | O | 새 평균 예상 수리 비용(원) |

### 요청 예시

```
PATCH /api/admin/repair-costs/1
Authorization: Bearer {accessToken}
Content-Type: application/json

{ "avgCost": 90000 }
```

---

## 알려진 제약 (구현 전 반드시 인지할 것)

- **요청 바디는 `Map<String, Integer>`**로 받으며, **키 이름은 반드시 `"avgCost"`**여야 한다. 컨트롤러는 `request.get("avgCost")`로 값을 꺼낸다.
- **키 누락 방어 없음(현재 구현)**: 바디에 `avgCost` 키가 없으면 `request.get("avgCost")`가 `null`을 반환하고, `updateAvgCost(null)`이 호출되어 **`avg_cost`가 null로 덮어써진다**. (음수·null에 대한 별도 유효성 검증이 없음 → 필요 시 `@Valid` DTO 또는 명시적 검증 추가 권장.)
- **존재하지 않는 `id`**: `findById(id)`가 비면 `NoSuchElementException("존재하지 않는 수리 비용 데이터입니다.")` → 404.
- **더티 체킹 반영**: `cost.updateAvgCost(newAvgCost)`가 `avgCost`와 `updatedAt`을 갱신하며, `@Transactional` 커밋 시점에 UPDATE 반영(별도 `save()` 호출 없음).
- **응답은 수정된 전체 `RepairCostDto`**: 수정된 `avgCost`를 포함해 `id/categoryId/categoryName/symptom/avgCost`를 그대로 반환한다(목록 API와 동일 DTO).

---

## 응답

### 200 OK

```json
{
  "id": 1,
  "categoryId": 11,
  "categoryName": "냉장고",
  "symptom": "냉방 불량",
  "avgCost": 90000
}
```

### 응답 필드 설명

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | Long | 예상 수리 비용 PK |
| `categoryId` | Integer | 가전 카테고리 PK |
| `categoryName` | String | 가전 카테고리명 |
| `symptom` | String | 증상 표시명 |
| `avgCost` | Integer | **수정 반영된** 평균 예상 수리 비용 |

### 에러 응답

| HTTP 상태 | 조건 |
|---|---|
| 401 Unauthorized | JWT 토큰 없음/만료 |
| 403 Forbidden | 인증되었으나 role != ADMIN (`hasRole("ADMIN")` 차단) |
| 404 Not Found | 존재하지 않는 `id` (`NoSuchElementException`) |

---

## 처리 로직 (Pipeline)

1. **검증**: `checkAdminRole()` → 아니면 `IllegalAccessException`
2. **조회**: `ExpectedRepairCostRepository.findById(id)` → 없으면 `NoSuchElementException` (404)
3. **값 갱신**: `cost.updateAvgCost(request.get("avgCost"))` — `avgCost`, `updatedAt` 변경(더티 체킹)
4. **응답 매핑**: 수정된 엔티티를 `RepairCostDto`로 변환하여 반환
5. **커밋**: `@Transactional` 종료 시 UPDATE 반영

---

## 개발 구성요소

| 계층 | 클래스 |
|---|---|
| Controller | `com.careflow.admin.controller.AdminRepairCostController#updateRepairCost` |
| Repository | `com.careflow.assignment.repository.ExpectedRepairCostRepository#findById` |
| Entity (수정 메서드) | `com.careflow.assignment.entity.ExpectedRepairCost#updateAvgCost` |
| Response DTO | `com.careflow.admin.controller.AdminRepairCostController.RepairCostDto` |

---

## 테스트 명세

### JUnit 5 컨트롤러 테스트 (`@WebMvcTest`)

**파일**: `src/test/java/com/careflow/admin/controller/AdminRepairCostControllerTest.java` — `@Nested class UpdateRepairCost`

- TC-C-1. 정상 수정 — `{ "avgCost": 90000 }` 요청 시 200 OK, `$.avgCost` == 90000 (기존 구현됨)
- TC-C-2. 존재하지 않는 `id` → 404 (`findById`가 `Optional.empty()`)
- TC-C-3. 인증 없음 → 401
- TC-C-4. ADMIN 아님 → 403

> 픽스처: `ExpectedRepairCost.createForTest(...)` + `given(repository.findById(1L)).willReturn(Optional.of(mockCost))`. `updateAvgCost`는 실제 엔티티 메서드이므로 값 변경이 DTO에 반영되는지 확인한다.
