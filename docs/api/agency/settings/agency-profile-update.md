# API 명세: 대행사 프로필 조회/수정

## 기본 정보

| 항목 | 내용 |
|---|---|
| HTTP 메서드 | `GET` / `PUT` |
| URL | `/api/agency/me` |
| 인증 | JWT 필수 (`ROLE_AGENCY`) |
| 설명 | 대행사 관리자가 자신이 속한 대행사의 상호명·주소·정산금 수취 계좌 정보를 조회/수정한다. 사업자등록번호(`business_number`)는 수정 불가. |

> ⚠️ 과거 명세에는 `PATCH /api/agencies/profile`로 기재되어 있었으나, 실제 구현은 `AgencyController`(`/api/agency`)의 `PUT /api/agency/me`이다. 이번 갱신에서 실제 코드 기준으로 정정함.

---

## 요청

### Headers

| 키 | 값 |
|---|---|
| `Authorization` | `Bearer {accessToken}` |
| `Content-Type` | `application/json` (PUT만 해당) |

### GET — Query/Body 없음

JWT의 `agencyId` 클레임 기준으로 조회한다. 대표 계정뿐 아니라 소속 staff 계정도 조회 가능(수정은 대표 계정 전용).

### PUT Request Body

```json
{
  "agencyName": "수정된 대행사 상호명",
  "agencyAddress": "서울특별시 서초구 서초대로 1",
  "bankName": "신한은행",
  "accountNumber": "110-123-456789"
}
```

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `agencyName` | String | O | 공백 불가, 최대 100자 | 변경할 대행사 상호명 |
| `agencyAddress` | String | O | 공백 불가, 최대 255자 | 변경할 주소(서비스 지역) |
| `bankName` | String | X (선택) | 최대 50자 | 정산금 수취 은행명. `accountNumber`와 함께 제공될 때만 반영됨 |
| `accountNumber` | String | X (선택) | 최대 50자 | 정산금 수취 계좌번호. `bankName`과 함께 제공될 때만 반영됨 |

`bankName`/`accountNumber`는 **둘 다 값이 있을 때만** 계좌 정보가 등록/수정된다. 하나만 보내거나 둘 다 생략하면 계좌 정보는 건드리지 않고 기존 값을 그대로 유지한다(대행사명/주소만 바꾸는 요청도 계속 지원).

---

## 응답

### 성공 `200 OK` (GET, PUT 공통)

```json
{
  "agencyId": 1,
  "agencyName": "수정된 대행사 상호명",
  "agencyAddress": "서울특별시 서초구 서초대로 1",
  "bankName": "신한은행",
  "accountNumber": "110-123-456789"
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `agencyId` | Long | 대행사 ID |
| `agencyName` | String | 대행사 상호명 |
| `agencyAddress` | String | 대행사 주소 |
| `bankName` | String \| null | 정산금 수취 은행명 — 계좌 미등록 시 `null` |
| `accountNumber` | String \| null | 정산금 수취 계좌번호 — 계좌 미등록 시 `null` |

### 실패

| 상황 | HTTP 상태 | 메시지 예시 |
|---|---|---|
| 인증 토큰 없음 / 만료 | `401 Unauthorized` | — |
| AGENCY 역할 아님 | `403 Forbidden` | — |
| 요청 바디 유효성 실패 (PUT) | `400 Bad Request` | `agencyName: 공백일 수 없습니다`, `bankName: 은행명은 최대 50자까지 입력 가능합니다.` |
| JWT의 userId/agencyId로 대행사 조회 실패 | `404 Not Found` | `해당 사용자의 대행사 정보를 찾을 수 없습니다.` |

---

## 비즈니스 로직

### GET `/api/agency/me`
1. JWT에서 `agencyId`를 추출한다.
2. `agencies` 테이블에서 해당 ID로 조회한다.
3. `agency_bank_accounts` 테이블에서 `agency_id` 기준 1:1 계좌 정보를 조회한다(없으면 `null`).
4. 대행사 정보 + 계좌 정보를 합쳐 응답 DTO로 반환한다.

### PUT `/api/agency/me`
1. JWT에서 `userId`를 추출한다 (`@AuthenticationPrincipal CustomUserDetails`).
2. `agencies` 테이블에서 `representative_user_id = userId`인 레코드를 조회한다(대표 계정만 수정 가능).
3. 조회된 `Agencies` 엔티티의 `agencyName`, `agencyAddress`를 수정하고 `updatedAt`을 갱신한다(더티 체킹으로 UPDATE).
4. `bankName`과 `accountNumber`가 **둘 다** 공백이 아니면 계좌 정보를 등록/수정한다:
   - 기존 `agency_bank_accounts` 레코드가 없으면 신규 생성. 이때 `account_holder`(예금주명)는 프론트에서 아직 입력받지 않아 **대행사 상호명으로 기본 설정**된다.
   - 기존 레코드가 있으면 `bankName`/`accountNumber`만 갱신하고 `account_holder`는 기존 값을 유지한다.
   - 하나라도 비어 있으면 계좌 정보는 변경하지 않는다(기존 값 유지).
5. 수정된 대행사 정보 + 계좌 정보(반영된 경우)를 응답 DTO로 반환한다.

### 참고: `agency_bank_accounts` 테이블
- CareFlow가 `platform_settlements` 지급 승인 시 이 계좌로 정산금을 송금하는 용도(CareFlow → 대행사 방향).
- 기사 개인 계좌를 담는 `bank_accounts`(대행사 → 기사 방향, 정산 상세 조회 시 사용)와는 별개 테이블/엔티티이므로 혼동하지 않을 것.
- `agency_id` 1:1 UNIQUE 제약 — 대행사당 계좌 정보는 최대 1건.

---

## 테스트 명세

### 단위 테스트 (`AgencySettingsControllerTest` — `@WebMvcTest`)

| # | 케이스 | 예상 결과 |
|---|---|---|
| 1 | GET 유효한 요청 (대표 계정) | `200 OK` + `bankName`/`accountNumber` 포함 |
| 2 | GET 계좌 미등록 | `200 OK` + `bankName`/`accountNumber` = `null` |
| 3 | GET staff(비대표) 계정 | `200 OK` (agencyId 기준 동일 조회) |
| 4 | PUT 유효한 요청(계좌 정보 포함) + AGENCY 인증 | `200 OK` + 계좌 정보 반영 |
| 5 | PUT 계좌 정보 없이 상호명/주소만 수정 | `200 OK` |
| 6 | PUT `agencyName` 공백 | `400 Bad Request` |
| 7 | PUT `agencyAddress` 공백 / 255자 초과 | `400 Bad Request` |
| 8 | PUT `bankName` 50자 초과 | `400 Bad Request` |
| 9 | 인증 토큰 없음 | `401 Unauthorized` |
| 10 | AGENCY 역할 아님 | `403 Forbidden` |
| 11 | 서비스에서 `NoSuchElementException` 발생 | `404 Not Found` |

### 통합 테스트 (`AgencySettingsControllerIntegrationTest` — `@SpringBootTest` + H2)

| # | 케이스 | 검증 내용 |
|---|---|---|
| 1 | 정상 수정 요청(상호명/주소만) | `200 OK` + DB에서 `agencyName`, `agencyAddress` 변경 확인 |
| 2 | `agencyName`만 변경, 주소는 요청값 그대로 저장 | DB 값 정확성 확인 |
| 3 | 계좌 정보 최초 등록 | `200 OK` + `agency_bank_accounts` 신규 생성, `account_holder`는 대행사 상호명으로 기본 설정됨 확인 |
| 4 | 이미 등록된 계좌 정보 수정 | 기존 레코드가 갱신됨(레코드 수 그대로 1건, 신규 생성 아님) 확인 |
| 5 | 계좌 정보 없이 요청 | 기존 계좌 정보가 그대로 유지됨 확인 |
| 6 | 존재하지 않는 사용자 ID로 요청 | `404 Not Found` |

> 테스트 격리 참고: `agency_bank_accounts`는 `src/test/resources/cleanup.sql`의 초기화 대상에 새로 추가됨(기존에는 누락되어 있어 테스트 간 데이터가 누적되는 문제가 있었음).
