# API 명세: 대행사 수수료율 조회

## 기본 정보

| 항목 | 내용 |
|---|---|
| HTTP 메서드 | `GET` |
| URL | `/api/agencies/fee-rate` |
| 인증 | JWT 필수 (`ROLE_AGENCY`) |
| 설명 | 대행사 슈퍼계정이 자신이 속한 대행사의 현재 수수료율을 조회한다. |

---

## 요청

### Headers

| 키 | 값 |
|---|---|
| `Authorization` | `Bearer {accessToken}` |

---

## 응답

### 성공 `200 OK`

```json
{
  "agencyId": 1,
  "agencyName": "테스트대행사",
  "agencyFeeRate": 5.00
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `agencyId` | Long | 대행사 고유 ID |
| `agencyName` | String | 대행사 상호명 |
| `agencyFeeRate` | Double | 현재 수수료율 (%) — `DECIMAL(5,2)` |

### 실패

| 상황 | HTTP 상태 | 메시지 예시 |
|---|---|---|
| 인증 토큰 없음 / 만료 | `401 Unauthorized` | — |
| AGENCY 역할 아님 | `403 Forbidden` | — |
| JWT의 userId로 대행사 조회 실패 | `404 Not Found` | `해당 사용자의 대행사 정보를 찾을 수 없습니다.` |

---

## 비즈니스 로직

1. JWT에서 `userId`를 추출한다 (`@AuthenticationPrincipal CustomUserDetails`).
2. `agencies` 테이블에서 `representative_user_id = userId`인 레코드를 조회한다.
3. 조회된 대행사의 `agency_fee_rate`를 응답 DTO로 반환한다.

---

## 테스트 명세

### 단위 테스트 (`AgencySettingsControllerTest` — `@WebMvcTest`)

| # | 케이스 | 예상 결과 |
|---|---|---|
| 1 | 유효한 인증 + AGENCY 역할 | `200 OK` + `agencyFeeRate` 필드 존재 |
| 2 | 인증 토큰 없음 | `401 Unauthorized` |
| 3 | 서비스에서 `NoSuchElementException` 발생 | `404 Not Found` |

### 통합 테스트 (`AgencySettingsControllerIntegrationTest` — `@SpringBootTest` + H2)

| # | 케이스 | 검증 내용 |
|---|---|---|
| 1 | 정상 조회 | `200 OK` + 응답 `agencyFeeRate` 값이 DB 저장값과 일치 |
| 2 | 존재하지 않는 사용자 ID | `404 Not Found` |
