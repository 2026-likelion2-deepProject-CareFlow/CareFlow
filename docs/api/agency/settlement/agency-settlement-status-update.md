# API 명세: 대행사 정산 상태 변경 (이의 제기용)

## 기본 정보

| 항목 | 내용 |
|------|------|
| Method | `PATCH` |
| URI | `/api/agency/settlements/{settlementId}/status` |
| 역할 | AGENCY |
| 설명 | 대행사가 개별 정산 건에 대해 계산 오류 등을 이유로 이의 제기(`DISPUTED`) 하거나, 이의 제기를 철회(`PENDING`)하는 API. |

## 배경 — [E 수정] 이 문서를 작성하게 된 이유

기존 구현은 이 API로 대행사가 `status`를 `PAID`로도 지정할 수 있게 해두었다. 그런데 DDL v14 기준 `settlements.status = PAID`는 "CareFlow가 대행사에 지급했다"(플랫폼→대행사 배치 승인, [admin-settlement-approve.md](../../admin/admin-settlement-approve.md))는 뜻으로 확정되어 있어서, 대행사가 CareFlow 승인을 받기도 전에 스스로 정산 건을 `PAID`로 찍어버릴 수 있는 구멍이었다. 게다가 `AdminSettlementService.approveAgency`가 (D 수정 이전 기준) "미지급 Settlement"를 이 `status` 값으로 판단했기 때문에, 대행사가 먼저 PAID로 찍으면 ADMIN 승인 절차 자체를 우회할 수 있었다.

**정정 내용**: `target=PAID`는 더 이상 허용하지 않는다. "대행사가 기사에게 지급했다"는 별개의 사실은 [PATCH /api/agency/engineer-payouts/{id}/pay](./agency-engineer-payout-pay.md)가 전담한다.

## 인증 / 권한

- `userDetails.getRole() != AGENCY` → `IllegalAccessException` (401)
- 소속 검증: 대상 `settlements.agency_id`가 요청자의 `agencyId`와 다르면 `IllegalAccessException` (401)

## 요청 파라미터

| 파라미터 | 위치 | 타입 | 필수 | 설명 |
|---------|------|------|------|------|
| `settlementId` | Path | Long | Y | 상태를 변경할 정산 ID |
| `status` | Body | String | Y | `DISPUTED` 또는 `PENDING` — **`PAID`는 더 이상 허용되지 않음**(400) |

## 응답

- 성공 시 `204 No Content`

## 비즈니스 로직

1. role == AGENCY 검증
2. `SettlementRepository.findById(settlementId)` → 없으면 `NoSuchElementException` (404)
3. 소속 검증 — 타 대행사 정산이면 `IllegalAccessException`
4. 현재 상태가 `PAID`면 어떤 변경도 거부 — `IllegalStateException`
5. `target`에 따라:
   - `DISPUTED` → `settlement.dispute()`
   - `PENDING` → `settlement.revertToPending()`
   - 그 외(`PAID` 포함) → `IllegalArgumentException`(400) — DTO의 `@Pattern(regexp = "DISPUTED|PENDING")`에서 이미 걸러지므로 서비스까지 도달하는 경우는 드묾(직접 서비스 호출하는 단위 테스트 등)

## 예외 처리

| 예외 | HTTP | 조건 |
|------|------|------|
| `IllegalAccessException` | 401 | role != AGENCY, 또는 타 대행사 정산 |
| `NoSuchElementException` | 404 | 존재하지 않는 `settlementId` |
| `IllegalStateException` | 403 | 이미 `PAID`인 정산 (어떤 변경도 불가) |
| `IllegalArgumentException` / Bean Validation 400 | 400 | `status`가 `DISPUTED`/`PENDING` 외의 값(`PAID` 포함) |
| 정상 | 204 | 처리 성공 |

---

## 단위 테스트 명세 (JUnit 5)

> `AgencySettlementServiceTest`에 `@Nested UpdateStatus` 추가 (기존 이 메서드에 대한 테스트가 전무했음)

| 테스트명 | 설명 |
|---------|------|
| `정상_DISPUTED로전이` | PENDING 정산 → `status=DISPUTED` 요청 → `dispute()` 호출 검증 |
| `정상_PENDING으로복귀` | DISPUTED 정산 → `status=PENDING` 요청 → `revertToPending()` 호출 검증 |
| `PAID요청시_IllegalArgumentException` | `status=PAID` 요청 → `IllegalArgumentException`, `markPaid()` 미호출 |
| `이미PAID인건_어떤변경도거부` | 현재 status=PAID → `IllegalStateException` (target 무관) |
| `타대행사정산_예외발생` | 다른 대행사 소속 정산 → `IllegalAccessException` |
| `AGENCY아닌_role_예외발생` | role=CUSTOMER → `IllegalAccessException` |

## 통합 테스트 명세 (H2 DB)

> `AgencySettlementServiceIntegrationTest`에 `@Nested UpdateStatus` 추가

| 테스트명 | 설명 |
|---------|------|
| `DISPUTED_전이_204_DB반영` | PENDING 정산 PATCH `DISPUTED` → 204, DB에서 status=DISPUTED 확인 |
| `PAID요청_400_DB불변` | `status=PAID` PATCH 시도 → 400, DB 값 변경 없음 |
| `이미PAID_403` | 이미 PAID인 정산에 PATCH 시도 → 403 |
