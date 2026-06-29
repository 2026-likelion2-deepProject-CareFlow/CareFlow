# API 명세: 대행사 프로필 수정

## 기본 정보

| 항목 | 내용 |
|---|---|
| HTTP 메서드 | `PATCH` |
| URL | `/api/agencies/profile` |
| 인증 | JWT 필수 (`ROLE_AGENCY`) |
| 설명 | 대행사 슈퍼계정이 자신이 속한 대행사의 상호명과 주소(서비스 지역)를 수정한다. 사업자등록번호(`business_number`)는 수정 불가. |

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
  "agencyName": "수정된 대행사 상호명",
  "agencyAddress": "서울특별시 서초구 서초대로 1"
}
```

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `agencyName` | String | O | 공백 불가, 최대 100자 | 변경할 대행사 상호명 |
| `agencyAddress` | String | O | 공백 불가, 최대 255자 | 변경할 주소(서비스 지역) |

---

## 응답

### 성공 `200 OK`

```json
{
  "agencyId": 1,
  "agencyName": "수정된 대행사 상호명",
  "agencyAddress": "서울특별시 서초구 서초대로 1"
}
```

### 실패

| 상황 | HTTP 상태 | 메시지 예시 |
|---|---|---|
| 인증 토큰 없음 / 만료 | `401 Unauthorized` | — |
| AGENCY 역할 아님 | `403 Forbidden` | — |
| 요청 바디 유효성 실패 | `400 Bad Request` | `agencyName: 공백일 수 없습니다` |
| JWT의 userId로 대행사 조회 실패 | `404 Not Found` | `해당 사용자의 대행사 정보를 찾을 수 없습니다.` |

---

## 비즈니스 로직

1. JWT에서 `userId`를 추출한다 (`@AuthenticationPrincipal CustomUserDetails`).
2. `agencies` 테이블에서 `representative_user_id = userId`인 레코드를 조회한다.
3. 조회된 `Agencies` 엔티티의 `agencyName`, `agencyAddress`를 수정하고 `updatedAt`을 갱신한다.
4. 더티 체킹으로 UPDATE가 실행된다.
5. 수정된 값을 응답 DTO로 반환한다.

---

## 테스트 명세

### 단위 테스트 (`AgencySettingsControllerTest` — `@WebMvcTest`)

| # | 케이스 | 예상 결과 |
|---|---|---|
| 1 | 유효한 요청 + AGENCY 인증 | `200 OK` |
| 2 | `agencyName` 공백 | `400 Bad Request` (`MethodArgumentNotValidException`) |
| 3 | `agencyAddress` 공백 | `400 Bad Request` (`MethodArgumentNotValidException`) |
| 4 | `agencyAddress` 255자 초과 | `400 Bad Request` (`MethodArgumentNotValidException`) |
| 5 | 인증 토큰 없음 | `401 Unauthorized` |
| 6 | 서비스에서 `NoSuchElementException` 발생 | `404 Not Found` |

### 통합 테스트 (`AgencySettingsControllerIntegrationTest` — `@SpringBootTest` + H2)

| # | 케이스 | 검증 내용 |
|---|---|---|
| 1 | 정상 수정 요청 | `200 OK` + DB에서 `agencyName`, `agencyAddress` 변경 확인 |
| 2 | 존재하지 않는 사용자 ID로 요청 | `404 Not Found` |
| 3 | `agencyName`만 변경, `agencyAddress`는 기존 값 유지 | DB 값 정확성 확인 |
