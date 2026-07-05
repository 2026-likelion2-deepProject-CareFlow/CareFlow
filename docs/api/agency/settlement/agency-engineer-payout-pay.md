# API 명세: 대행사 기사 지급 완료 처리

## 기본 정보

| 항목 | 내용 |
|------|------|
| Method | `PATCH` |
| URI | `/api/agency/engineer-payouts/{engineerPayoutId}/pay` |
| 역할 | AGENCY |
| 설명 | 대행사가 소속 기사에게 실제로 급여를 지급한 뒤, 해당 `engineer_payouts` 배치를 "지급 완료"로 표시한다. [목록 조회 API](./agency-engineer-payouts-list.md) 배경 설명 참고 — 기존에 `Settlement.status`를 직접 `PAID`로 바꾸던 잘못된 관행을 대체하는 정식 API. |

## 인증 / 권한

- `userDetails.getRole() != AGENCY` → `IllegalAccessException` (401)
- 소속 검증: 대상 `engineer_payouts.agency_id`가 요청자의 `agencyId`와 다르면 `IllegalAccessException` (401)

## 요청 파라미터

| 파라미터 | 위치 | 타입 | 필수 | 설명 |
|---------|------|------|------|------|
| `engineerPayoutId` | Path | Long | Y | 지급 완료 처리할 배치 ID |

요청 바디 없음.

## 응답

- 성공 시 `204 No Content`

## 비즈니스 로직

1. role == AGENCY 검증
2. `EngineerPayoutRepository.findById(engineerPayoutId)` → 없으면 `NoSuchElementException` (404)
3. 소속 검증 — `engineerPayout.agency.id != userDetails.agencyId` → `IllegalAccessException`
4. 이미 `PAID`면 아무 것도 하지 않고 정상 종료 (멱등)
5. `engineerPayout.markPaid()` 호출 (더티 체킹)

CareFlow는 이 자금을 소유·집행하지 않으므로(대행사 자체 책임 지급), `platform_settlements`처럼 계좌 등록 여부를 검증하지 않는다 — 대행사가 자체적으로 지급했음을 자체 신고(self-attestation)하는 액션이다.

## 예외 처리

| 예외 | HTTP | 조건 |
|------|------|------|
| `IllegalAccessException` | 401 | role != AGENCY, 또는 타 대행사 배치 |
| `NoSuchElementException` | 404 | 존재하지 않는 `engineerPayoutId` |
| 정상 | 204 | 처리 성공 (이미 PAID여도 204, 멱등) |

---

## 단위 테스트 명세 (JUnit 5)

> `AgencyEngineerPayoutServiceTest`에 `@Nested` 추가

| 테스트명 | 설명 |
|---------|------|
| `정상_지급완료처리` | PENDING 배치 → `markPaid()` 호출 검증 |
| `이미PAID_재호출해도_정상처리` | 이미 PAID인 배치 재호출 → 예외 없음(멱등) |
| `존재하지않는배치_404` | `findById` empty → `NoSuchElementException` |
| `타대행사배치_401` | 다른 대행사 소속 배치 → `IllegalAccessException` |
| `AGENCY아닌_role_401` | role=ENGINEER → `IllegalAccessException` |

## 통합 테스트 명세 (H2 DB)

> `AgencyEngineerPayoutControllerIntegrationTest`에 `@Nested` 추가

| 테스트명 | 설명 |
|---------|------|
| `정상_지급완료_204` | PENDING 배치 저장 후 PATCH 호출 → 204, DB에서 status=PAID/paidAt not null 확인 |
| `타대행사배치_401` | 다른 대행사 배치 PATCH 시도 → 401, DB 값 불변 |
| `존재하지않는배치_404` | 없는 ID PATCH → 404 |
