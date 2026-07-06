# API 명세: 대행사 데이터 내보내기 / 가져오기

## 개요

`Agencysettingpage.jsx` "데이터 관리" 섹션의 "데이터 내보내기"/"데이터 가져오기" 버튼에 대응. 대행사 소속 기사 로스터를 CSV로 백업(내보내기)하거나, CSV로 기사 가입 신청을 일괄 등록(가져오기)한다.

기존 `AgencyStatisticsReportCsvGenerator`의 CSV 생성 패턴(UTF-8 BOM + `Content-Disposition: attachment`)을 재사용한다.

---

## 1. 데이터 내보내기 (기사 로스터 CSV)

| 항목 | 내용 |
|---|---|
| HTTP 메서드 | `GET` |
| URL | `/api/agency/me/data-export` |
| 인증 | JWT 필수 (`AGENCY`) |
| 응답 타입 | `text/csv;charset=UTF-8` |

### 응답 컬럼

```
engineerUserId,name,email,phone,categoryName,skillLevel,status,avgRating
```

- 대행사 소속 전체 기사(`EngineerProfile` + `User`)를 대상으로 한다.
- 파일명: `Content-Disposition: attachment; filename="engineers_{agencyId}_{yyyyMMdd}.csv"`

### 비즈니스 로직

1. `AGENCY` 역할 검증.
2. `EngineerProfileRepository.findByAgencyId(agencyId)`로 기존 기사 목록 조회 API(`AgencyEngineerService.getAgencyEngineers`)와 동일한 데이터 소스 재사용.
3. `AgencyStatisticsReportCsvGenerator`와 동일한 방식으로 CSV 바이트 생성 후 `ByteArrayResource`로 반환.

---

## 2. 데이터 가져오기 (기사 가입 신청 일괄 등록)

| 항목 | 내용 |
|---|---|
| HTTP 메서드 | `POST` |
| URL | `/api/agency/me/data-import` |
| 인증 | JWT 필수 (`AGENCY`) |
| 요청 타입 | `multipart/form-data` (필드명 `file`) |

### CSV 형식(업로드)

```
name,email,phone
홍길동,hong@example.com,01011112222
```

> `account_requests` 테이블에는 전문 카테고리(category_id) 컬럼이 없다 — 기사의 전문 분야는 승인 후 본인이 `EngineerProfile`을 등록할 때 입력하는 값이므로, 가입 신청 단계의 CSV 컬럼에는 포함하지 않는다(당초 설계에 `categoryId`를 넣었다가 스키마 확인 후 제외함).

### 응답 `200 OK`

```json
{ "successCount": 8, "failCount": 2, "errors": ["3행: 이미 가입된 이메일입니다."] }
```

### 비즈니스 로직

1. `AGENCY` 역할 검증(대표 담당자만 — 신규 기사 승인 흐름을 시작하는 민감한 동작이므로 `isRepresentative` 확인, 아니면 `IllegalAccessException`).
2. CSV를 한 줄씩 파싱하여 행마다 검증(이메일 형식, 중복 이메일 — `users`/`account_requests` 양쪽 확인).
3. 유효한 행마다 `account_requests`에 `status=PENDING`, `requested_role=ENGINEER`, `region_id=NULL`(CSV에 지역 정보 없음, 승인 후 본인이 프로필에서 입력) 레코드를 생성한다 — **바로 기사 계정을 생성하지 않고**, 기존 기사 가입 승인/반려 워크플로우(`EngineerAccountRequestController`)에 편입시켜 대행사 관리자가 건별로 검토 후 승인하도록 한다(무분별한 일괄 계정 생성 방지).
4. 실패한 행은 건너뛰고 사유를 `errors` 배열에 담아 함께 반환한다(전체 롤백 아님 — 행 단위 부분 성공 허용).
5. 임시 비밀번호는 `SecureRandom`으로 생성해 해싱 저장(신청자는 승인 후 "비밀번호 초기화" 절차로 재설정 가능).

---

## 테스트 명세

### 단위 테스트 (`AgencyDataTransferServiceTest`)

| # | 케이스 | 예상 결과 |
|---|---|---|
| 1 | 내보내기 - 기사 3명 | CSV 3행 + 헤더 1행 = 4행 생성 확인 |
| 2 | 내보내기 - 소속 기사 없음 | 헤더만 있는 CSV(0건) |
| 3 | 가져오기 - 정상 CSV 2건 | `account_requests` 2건 생성(`PENDING`), successCount=2 |
| 4 | 가져오기 - 중복 이메일 포함 | 정상 행만 생성, failCount에 반영 |
| 5 | 가져오기 - 대표 담당자 아님 | `IllegalAccessException` |

### 통합 테스트 (`AgencyDataTransferControllerIntegrationTest` — H2)

| # | 케이스 | 검증 내용 |
|---|---|---|
| 1 | 내보내기 정상 흐름 | `200` + `Content-Type: text/csv` + 응답 바디에 기사 정보 포함 확인 |
| 2 | 가져오기 정상 흐름 | `200` + DB `account_requests`에 PENDING 행 생성 확인 |
| 3 | 가져오기 후 기존 승인 API로 승인 | `POST /api/account-requests/engineer/approval`로 정상 승인되어 ENGINEER 계정 생성까지 확인(기존 워크플로우와의 연계 검증) |
