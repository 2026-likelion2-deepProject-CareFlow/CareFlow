# 🚀 API 생성 및 개발 요구사항 정의서

## 1. 데이터 모델 (Schema)
- 메인 스키마 정의 파일 : `sql/CareFlow_v5.sql`
- 위 파일에서 as_requests, agencies, users, symptoms, appliances, regions 테이블 위주로 참조할 것

---

## 2. API 엔드포인트 명세

### [GET] /api/as-requests/agency — 대행사 소속 A/S 요청 전체 목록 조회

- **설명**: 현재 로그인한 대행사 관리자와 같은 소속의 수리 기사들에게 배정된 A/S 요청 목록을 전체 조회한다.
  수리가 완료된 요청(status = COMPLETED)은 제외한다.
- **API Parameter**:
  - `@AuthenticationPrincipal CustomUserDetails` : 로그인한 사용자 role이 AGENCY인지 확인 후 소속 agency_id 추출
- **API Response(200 OK)**: `List<AgencyAsRequestListResponse>` — 요청 목록 반환
- **API Response(204 No Content)**: 조회 결과가 없는 경우
- **API Response(401 Unauthorized)**: role != AGENCY인 경우
- **응답 필드**:
  | 필드명 | 타입 | 설명 |
  |---|---|---|
  | requestId | Long | A/S 요청 ID |
  | status | AsStatus | 요청 상태 (PENDING 제외, COMPLETED 제외) |
  | customerName | String | 신청 고객명 |
  | customerPhone | String | 고객 연락처 |
  | symptomName | String | 증상명 (한글) |
  | symptomDesc | String | 증상 상세 설명 |
  | visitRegionName | String | 방문 지역명 |
  | visitAddressDetail | String | 방문 상세 주소 |
  | scheduledDate | LocalDate | 방문 예정일 |
  | scheduledTime | String | 방문 예정 시간 (HH:MM) |
  | assignType | AssignType | 배정 방식 (AUTO/MANUAL) |
  | createdAt | LocalDateTime | 요청 생성 일시 |

---

### [GET] /api/as-requests/agency/search — 대행사 소속 A/S 요청 필터링 조회

- **설명**: 날짜 및 요청 상태(status)를 선택적으로 적용하여 대행사 소속 A/S 요청 목록을 조회한다.
  COMPLETED 상태의 요청은 필터 조건과 무관하게 항상 제외된다.
- **API Parameter**:
  - `@AuthenticationPrincipal CustomUserDetails` : role = AGENCY 확인, agency_id 추출
  - `@RequestParam(required = false) LocalDate date` : 방문 예정일 필터 (미입력 시 전체 날짜 조회)
  - `@RequestParam(required = false) String status` : 요청 상태 필터 (미입력 시 전체 상태 조회, COMPLETED 입력 시 무시)
- **API Response(200 OK)**: `List<AgencyAsRequestListResponse>` — 필터링된 요청 목록 반환
- **API Response(204 No Content)**: 조회 결과가 없는 경우
- **API Response(400 Bad Request)**: status 값이 AsStatus enum에 존재하지 않는 경우
- **API Response(401 Unauthorized)**: role != AGENCY인 경우

---

### [GET] /api/as-requests/agency/{requestId} — A/S 요청 단건 상세 조회

- **설명**: 목록에서 선택한 특정 A/S 요청의 request_id를 기준으로 as_requests 테이블에서 상세 정보를 단건 조회한다.
  해당 요청이 현재 로그인한 대행사 소속이 아닌 경우 접근을 차단한다.
- **API Parameter**:
  - `@PathVariable Long requestId` : 조회할 A/S 요청 ID
  - `@AuthenticationPrincipal CustomUserDetails` : role = AGENCY 확인, agency_id 추출 후 소유권 검증
- **API Response(200 OK)**: `AgencyAsRequestDetailResponse` — 요청 상세 반환
- **API Response(401 Unauthorized)**: role != AGENCY이거나 해당 요청이 본인 소속 대행사의 요청이 아닌 경우
- **API Response(404 Not Found)**: requestId에 해당하는 요청이 존재하지 않는 경우
- **응답 필드**:
  | 필드명 | 타입 | 설명 |
  |---|---|---|
  | requestId | Long | A/S 요청 ID |
  | status | AsStatus | 요청 상태 |
  | customerName | String | 신청 고객명 |
  | customerPhone | String | 고객 연락처 |
  | symptomCode | String | 증상 코드 |
  | symptomName | String | 증상명 (한글) |
  | symptomDesc | String | 증상 상세 설명 |
  | imageUrls | String | 첨부 이미지 URL 목록 (JSON) |
  | brand | String | 가전 브랜드 |
  | modelName | String | 가전 모델명 |
  | serialNumber | String | 가전 시리얼 번호 |
  | purchaseDate | LocalDate | 가전 구매일 |
  | warrantyEndDate | LocalDate | 가전 보증 만료일 |
  | assignType | AssignType | 배정 방식 (AUTO/MANUAL) |
  | visitRegionName | String | 방문 지역명 |
  | visitAddressDetail | String | 방문 상세 주소 |
  | scheduledDate | LocalDate | 방문 예정일 |
  | scheduledTime | String | 방문 예정 시간 (HH:MM) |
  | cancelReason | String | 취소 사유 (취소된 경우에만 존재) |
  | createdAt | LocalDateTime | 요청 생성 일시 |
  | updatedAt | LocalDateTime | 최종 수정 일시 |

---

## 3. 상세 처리 로직 (Pipeline)

### 전체 목록 조회 / 필터링 조회 공통 흐름
1. **검증(Validation) 단계**
   - `@AuthenticationPrincipal`로 role = AGENCY 확인 → 아니면 401 반환
   - 로그인 유저 정보로 소속 agency_id 조회 → 없으면 IllegalStateException
2. **데이터 처리(Process) 단계**
   - `as_requests` 테이블에서 `agency_id = 현재_대행사_id` AND `status != COMPLETED` 조건으로 조회
   - 필터링 조회 시: 추가로 `scheduledDate = date` (date 파라미터 존재 시), `status = 요청_상태` (status 파라미터 존재 시) 조건 적용
   - status 파라미터로 COMPLETED가 전달된 경우 서비스 레이어에서 빈 리스트 반환
   - N+1 방지를 위해 symptom, customer, visitRegion JOIN FETCH 사용
3. **응답(Response) 단계**
   - 결과 있음 → 200 OK + `List<AgencyAsRequestListResponse>`
   - 결과 없음 → 204 No Content

### 단건 상세 조회 흐름
1. **검증(Validation) 단계**
   - role = AGENCY 확인 → 아니면 401 반환
   - requestId로 as_requests 조회 → 없으면 404 반환
   - 조회된 요청의 agency_id가 현재 로그인한 대행사와 일치하는지 검증 → 불일치 시 401 반환
2. **데이터 처리(Process) 단계**
   - symptom, customer, appliance, visitRegion JOIN FETCH로 단건 조회
3. **응답(Response) 단계**
   - 성공 → 200 OK + `AgencyAsRequestDetailResponse`

---

## 4. 예외 처리 (Error Handling) 및 제약 조건

- 모든 에러 발생 시 `GlobalExceptionHandler`가 처리하는 표준 예외 4종 사용
  - 리소스 없음 → `NoSuchElementException` (404)
  - 잘못된 입력값 → `IllegalArgumentException` (400)
  - 상태/흐름 위반 → `IllegalStateException` (409)
  - 인증/권한 문제 → `IllegalAccessException` (401)
- COMPLETED 상태의 요청은 항상 결과에서 제외 (수리 완료 이후 내역은 이 API 범위 밖)
- 단건 조회 시 타 대행사 요청에 대한 접근은 권한 예외로 차단 (데이터 격리)
- 모든 조회는 `@Transactional(readOnly = true)` 적용

---

## 5. 개발 및 출력 요구사항

- 컨트롤러, 서비스, 리포지토리 레이어를 명확히 분리하여 구현
- 신규 서비스 클래스: `AgencyAsRequestService` (`as_request/service/` 하위)
- 신규 DTO: `AgencyAsRequestListResponse`, `AgencyAsRequestDetailResponse` (`as_request/dto/` 하위)
- 기존 `AsRequestController`에 agency 전용 엔드포인트 추가 (`/agency` prefix)
- 기존 `AsRequestRepository`에 조회 쿼리 메서드 추가
- 한글 주석으로 비즈니스 의도를 명확히 기술
