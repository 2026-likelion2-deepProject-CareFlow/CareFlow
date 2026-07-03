# API: 수리 비용 가이드 목록 조회

## 개요

관리자(ADMIN)가 **증상별 예상 수리 비용(가이드) 전체 목록**을 조회한다. 이 데이터는 Quartz 배치가 완료된 작업 보고서를 집계하여 `expected_repair_costs` 테이블에 채우며, 고객 A/S 접수 시 예상 비용 안내에 사용된다. 관리자는 본 목록에서 값을 확인하고 필요 시 수동 보정([금액 수정 API](admin-repair-costs-update.md))한다.
위치: Admin 수리 비용 가이드 관리 페이지 — 카테고리·증상별 표.

---

## 엔드포인트

```
GET /api/admin/repair-costs
```

---

## 인증/인가

- **필수 인증**: JWT Bearer 토큰
- **허용 역할**: `ADMIN`
- `SecurityConfig`의 `hasRole("ADMIN")` 1차 차단 + 컨트롤러 `checkAdminRole()` 2차 방어 → 아니면 `IllegalAccessException`
- 컨트롤러 메서드에 `@Transactional(readOnly = true)`

---

## 요청

### 경로 변수 / 쿼리 파라미터

- 없음 (전체 목록 조회).

### 요청 예시

```
GET /api/admin/repair-costs
Authorization: Bearer {accessToken}
```

---

## 알려진 제약 (구현 전 반드시 인지할 것)

- **`avgCost` null 방어**: `expected_repair_costs.avg_cost`는 Quartz 집계 전까지 `null`일 수 있다(샘플 데이터 부족 시). 응답에서는 `avgCost != null ? avgCost : 0`으로 **null을 0으로 치환**하여 내려준다.
- **`categoryId` 타입은 Integer**: `ApplianceCategory`의 PK(`category_id`)는 `Long`이 아니라 **`Integer`**다. DTO 필드도 `Integer categoryId`.
- **`symptom` 1:1 관계**: `expected_repair_costs`는 `symptom_id`에 대해 `@OneToOne`(unique)이다. 즉 증상 1개당 예상 비용 행 1개.
- **`symptom` 표시명**: `expected_repair_cost → symptom → symptomName`(예: "냉방 불량"). `symptomCode`가 아니라 한글 표시명을 내려준다.
- **`categoryName`**: `expected_repair_cost → category → name`(`appliance_categories.name`).
- **정렬 순서**: `category.id ASC, repair_cost_id ASC`(카테고리 → 비용행 순).
- **N+1 방지**: `findAllWithCategoryAndSymptom()`에서 `category`, `symptom`을 `JOIN FETCH`.
- **빈 목록**: 데이터가 없으면 빈 배열 `[]` 반환.

---

## 응답

### 200 OK

```json
[
  {
    "id": 1,
    "categoryId": 11,
    "categoryName": "냉장고",
    "symptom": "냉방 불량",
    "avgCost": 85000
  },
  {
    "id": 2,
    "categoryId": 11,
    "categoryName": "냉장고",
    "symptom": "소음 발생",
    "avgCost": 0
  }
]
```

### 응답 필드 설명

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | Long | 예상 수리 비용 PK (`repair_cost_id`) — 수정 API의 path 변수로 사용 |
| `categoryId` | Integer | 가전 카테고리 PK (`category_id`) |
| `categoryName` | String | 가전 카테고리명 (`appliance_categories.name`) |
| `symptom` | String | 증상 표시명 (`symptoms.symptom_name`) |
| `avgCost` | Integer | 평균 예상 수리 비용(원). Quartz 집계 전 null이면 `0`으로 치환 |

### 에러 응답

| HTTP 상태 | 조건 |
|---|---|
| 401 Unauthorized | JWT 토큰 없음/만료 |
| 403 Forbidden | 인증되었으나 role != ADMIN (`hasRole("ADMIN")` 차단) |

---

## 처리 로직 (Pipeline)

1. **검증**: `checkAdminRole()` → 아니면 `IllegalAccessException`
2. **조회**: `ExpectedRepairCostRepository.findAllWithCategoryAndSymptom()` — `JOIN FETCH category, symptom`, `category.id ASC, id ASC` 정렬
3. **DTO 매핑**: 각 행을 `RepairCostDto(id, category.categoryId, category.name, symptom.symptomName, avgCost ?? 0)`로 변환
4. **반환**: `List<RepairCostDto>`

---

## 개발 구성요소

| 계층 | 클래스 |
|---|---|
| Controller | `com.careflow.admin.controller.AdminRepairCostController#getRepairCosts` |
| Repository | `com.careflow.assignment.repository.ExpectedRepairCostRepository#findAllWithCategoryAndSymptom` |
| Entity | `com.careflow.assignment.entity.ExpectedRepairCost` |
| Response DTO | `com.careflow.admin.controller.AdminRepairCostController.RepairCostDto` (컨트롤러 내부 record) |

> 참고: 이 API는 별도 Service 계층 없이 컨트롤러가 리포지토리를 직접 사용한다(단순 조회·매핑).

---

## 테스트 명세

### JUnit 5 컨트롤러 테스트 (`@WebMvcTest`)

**파일**: `src/test/java/com/careflow/admin/controller/AdminRepairCostControllerTest.java` — `@Nested class GetRepairCosts`

- TC-C-1. 인증된 ADMIN — 200 OK, `$[0].categoryName`, `$[0].symptom`, `$[0].avgCost` 값 검증 (기존 구현됨)
- TC-C-2. `avgCost`가 null인 행 → 응답에서 `0`으로 치환되는지 검증
- TC-C-3. 인증 없음 → 401
- TC-C-4. ADMIN 아님 → 403

> 리포지토리는 `@MockitoBean`으로 주입하고, `ExpectedRepairCost.createForTest(category, symptom, avgCost, sampleCount)` 정적 팩토리로 픽스처를 만든다.
