# API 명세: 대행사 관리자 계정 탈퇴

## 개요

`Agencysettingpage.jsx` "데이터 관리" 섹션의 "계정 탈퇴" 버튼에 대응. 로그인한 본인(대행사 관리자 — 슈퍼 계정 또는 일반 관리자) 계정을 소프트 삭제한다.

| 항목 | 내용 |
|---|---|
| HTTP 메서드 | `DELETE` |
| URL | `/api/agency/me/withdraw` |
| 인증 | JWT 필수 (`AGENCY`) |

### Request Body

```json
{ "password": "currentPassword123" }
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `password` | String | O | 본인 확인용 현재 비밀번호 |

### 응답

`204 No Content`

### 비즈니스 로직

1. 현재 비밀번호 일치 여부 확인 — 불일치 시 `IllegalArgumentException`(400).
2. **대표 담당자(슈퍼 계정) 탈퇴 제한**: `agencies.representative_user_id`가 본인인 경우(즉 `isRepresentative == true`) 탈퇴를 허용하지 않는다 — 대행사 자체의 대표자가 없어지는 상태를 방지하기 위함. 이 경우 `IllegalStateException`(403, "대표 담당자는 탈퇴할 수 없습니다. 먼저 대표를 위임하거나 대행사 해지를 문의해주세요.").
3. 일반 관리자 계정(대표 아님)만 탈퇴 가능 — `userRepository.delete(user)` 호출.
   - `User` 엔티티의 기존 `@SQLDelete` 어노테이션이 `UPDATE users SET deleted_at = NOW(), email = CONCAT(email, '_deleted_', user_id) WHERE user_id = ?`를 실행하므로 실제로는 소프트 삭제(이메일 재사용 가능하도록 변형 처리 포함) — 새 엔티티 메서드 추가 불필요, 기존 관례 그대로 재사용.
4. 탈퇴 후 해당 계정의 refresh token을 Redis에서 즉시 삭제하여 재발급을 차단한다(`AuthService`의 로그아웃 로직과 동일한 Redis 키 규칙 재사용).

---

## 테스트 명세

### 단위 테스트 (`AgencyAccountWithdrawServiceTest`)

| # | 케이스 | 예상 결과 |
|---|---|---|
| 1 | 일반 관리자 계정 + 올바른 비밀번호 | `userRepository.delete()` 호출 검증 |
| 2 | 비밀번호 불일치 | `IllegalArgumentException` |
| 3 | 대표 담당자(슈퍼 계정) 탈퇴 시도 | `IllegalStateException` |

### 통합 테스트 (`AgencyAccountWithdrawControllerIntegrationTest` — H2)

| # | 케이스 | 검증 내용 |
|---|---|---|
| 1 | 일반 관리자 탈퇴 정상 흐름 | `204` + DB `deleted_at` NOT NULL 확인 + 탈퇴 계정으로 재로그인 시 실패(`존재하지 않는 사용자입니다`, `@SQLRestriction`으로 조회에서 제외됨) 확인 |
| 2 | 슈퍼 계정 탈퇴 시도 | `403 Forbidden` + DB `deleted_at` NULL 유지 확인 |
| 3 | 잘못된 비밀번호로 탈퇴 시도 | `400 Bad Request` + 계정 유지 확인 |
