# API 명세: 대행사 수수료율 수정

## 기본 정보

| 항목 | 내용 |
|---|---|
| HTTP 메서드 | `PATCH` |
| URL | `/api/agencies/fee-rate` |
| 인증 | JWT 필수 (`ROLE_AGENCY`) |
| 설명 | 대행사 슈퍼계정이 자신이 속한 대행사의 수수료율을 직접 수정한다. 변경된 수수료율은 이후 생성되는 정산(`settlements`)에 적용된다. |

---

## 요청

### Headers

| 키 | 값 |
|---|---|
| `Authorization` | `Bearer {accessToken}` |
| `Content-Type` | `application/json` |

### Request Body

```json
{
  "agencyFeeRate": 7.50
}
```

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `agencyFeeRate` | Double | O | null 불가, 0 이상 100 이하, 소수점 최대 2자리 | 변경할 수수료율 (%) |

---

## 응답

### 성공 `200 OK`

```json
{
  "agencyId": 1,
  "agencyName": "테스트대행사",
  "agencyFeeRate": 7.50
}
```

### 실패

| 상황 | HTTP 상태 | 메시지 예시 |
|---|---|---|
| 인증 토큰 없음 / 만료 | `401 Unauthorized` | — |
| AGENCY 역할 아님 | `403 Forbidden` | — |
| 요청 바디 유효성 실패 | `400 Bad Request` | `agencyFeeRate: null일 수 없습니다` |
| 수수료율이 0 미만 또는 100 초과 | `400 Bad Request` | `수수료율은 0 이상 100 이하여야 합니다.` |
| 소수점 3자리 이상 | `400 Bad Request` | `수수료율은 소수점 최대 2자리까지 입력 가능합니다.` |
| JWT의 userId로 대행사 조회 실패 | `404 Not Found` | `해당 사용자의 대행사 정보를 찾을 수 없습니다.` |

---

## 비즈니스 로직

1. JWT에서 `userId`를 추출한다 (`@AuthenticationPrincipal CustomUserDetails`).
2. `agencies` 테이블에서 `representative_user_id = userId`인 레코드를 조회한다.
3. 수수료율 범위(`0 ≤ agencyFeeRate ≤ 100`)를 서비스 레이어에서 추가 검증한다.
4. 조회된 `Agencies` 엔티티의 `agencyFeeRate`를 수정하고 `updatedAt`을 갱신한다.
5. 더티 체킹으로 UPDATE가 실행된다.
6. 수정된 값을 응답 DTO로 반환한다.

> **주의**: `settlements.agency_fee_rate`는 정산 생성 시점의 스냅샷이므로, 수수료율 변경이 기존 정산 내역에 소급 적용되지 않는다.

---

## 테스트 명세

### 단위 테스트 (`AgencySettingsControllerTest` — `@WebMvcTest`)

| # | 케이스 | 예상 결과 |
|---|---|---|
| 1 | 유효한 수수료율(7.50) + AGENCY 인증 | `200 OK` |
| 2 | `agencyFeeRate` null | `400 Bad Request` (`MethodArgumentNotValidException`) |
| 3 | 소수점 3자리(5.123) | `400 Bad Request` (`MethodArgumentNotValidException`) |
| 4 | 인증 토큰 없음 | `401 Unauthorized` |
| 5 | 서비스에서 `NoSuchElementException` 발생 | `404 Not Found` |
| 6 | 서비스에서 범위 초과 `IllegalArgumentException` 발생 | `400 Bad Request` |

### 통합 테스트 (`AgencySettingsControllerIntegrationTest` — `@SpringBootTest` + H2)

| # | 케이스 | 검증 내용 |
|---|---|---|
| 1 | 정상 수정(5.00 → 7.50) | `200 OK` + DB `agency_fee_rate` 값 7.50 확인 |
| 2 | 수수료율 0.00 경계값 | `200 OK` + DB 값 0.00 확인 |
| 3 | 수수료율 100.00 경계값 | `200 OK` + DB 값 100.00 확인 |
| 4 | 수수료율 -1.00 (범위 초과) | `400 Bad Request` |
| 5 | 수수료율 100.01 (범위 초과) | `400 Bad Request` |
| 6 | 존재하지 않는 사용자 ID | `404 Not Found` |
