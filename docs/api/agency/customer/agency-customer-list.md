# API: 대행사 소속 고객 목록 조회

## 개요

대행사 관리자가 본인 대행사 소속 수리기사에게 **A/S 서비스를 한 번이라도 완료(`as_assignments.status = COMPLETED`)받은 고객**의 목록을 조회한다.
대시보드의 "고객 관리" 메뉴에서 사용되며, 페이지네이션과 검색/필터링을 지원한다.

---

## 엔드포인트

```
GET /api/agency/customers?page=0&size=10
```

> ⚠️ 본 API는 GET이지만 필터 조건을 `@RequestBody`로 전달받는다(프론트 요구사항). Spring MVC는 GET + RequestBody를 허용하므로 기능상 문제는 없으나, 향후 캐싱/프록시 호환성 이슈가 있을 수 있음을 인지하고 있을 것.

---

## 인증/인가

- **필수 인증**: JWT Bearer 토큰
- **허용 역할**: `AGENCY`
- `@AuthenticationPrincipal CustomUserDetails`에서 `agencyId`를 직접 추출(이미 JWT 클레임에 포함되어 있음 — `AgencyController`의 `/me`, `/stats/summary`와 동일 패턴이나, 해당 API들은 `userId`로 `agenciesRepository.findByRepresentativeById()` 조회 방식을 쓰는 것과 달리 본 API는 `CustomUserDetails.getAgencyId()`를 바로 사용한다. 일반 관리자 계정(대표자가 아닌 소속 직원)도 동일하게 `agencyId` 클레임을 보유하므로 더 범용적인 방식이다.)
- ⚠️ `SecurityConfig`에는 `/api/agency/customers`에 대한 명시적 `hasAuthority("AGENCY")` 매칭이 없고 `anyRequest().authenticated()`로만 보호된다. `ENGINEER` 역할도 `agencyId` 클레임을 보유하므로(소속 대행사 식별용), 컨트롤러 진입만으로는 ENGINEER가 고객 목록에 접근할 수 있는 인가 허점이 생긴다. 따라서 `AgencyNotificationService`와 동일하게 **서비스 레이어에서 `userDetails.getRole()` 이 `"AGENCY"`인지 명시적으로 검증**하고, 아니면 `IllegalAccessException`(401)을 던진다.

---

## 요청

### 쿼리 파라미터

| 파라미터 | 타입 | 기본값 | 설명 |
|---|---|---|---|
| `page` | int | 0 | 페이지 번호 (0-base) |
| `size` | int | 10 | 페이지 크기 |

### 요청 바디 (`AgencyCustomerSearchRequest`)

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `keyword` | String | N | 이름/연락처/이메일 부분 일치 검색 |
| `status` | String | N | `ACTIVE` / `INACTIVE` / `SUSPENDED` (users.status) |
| `grade` | String | N | `VIP`/`GOLD`/`SILVER`/`BRONZE`/`NORMAL` — **DB 미지원 필드(아래 "알려진 제약" 참고), 현재는 응답에 영향 없음** |
| `joinPath` | String | N | `SOCIAL`/`WEB`/`APP` 등 — **DB 미지원 필드, 현재는 응답에 영향 없음** |
| `joinedFrom` | String | N | 가입일 검색 시작 (`yyyy-MM-dd`) — `users.created_at >= joinedFrom 00:00:00` |
| `joinedTo` | String | N | 가입일 검색 종료 (`yyyy-MM-dd`) — `users.created_at < joinedTo+1일 00:00:00` (해당일 포함) |

### 요청 예시

```
GET /api/agency/customers?page=0&size=10
Authorization: Bearer {access_token}
Content-Type: application/json

{
  "keyword": "김민수",
  "status": "ACTIVE",
  "grade": null,
  "joinPath": null,
  "joinedFrom": "2024-01-01",
  "joinedTo": "2024-12-31"
}
```

바디 생략(빈 객체 `{}` 또는 미전송) 시 전체 조회.

---

## 알려진 제약 (구현 전 반드시 인지할 것)

- 현재 `users` 테이블에는 `grade`, `join_path` 컬럼이 **존재하지 않는다** (`sql/CareFlow_DDL_v10.sql` 확인 필요).
- 따라서 이번 구현에서는 `grade`/`joinPath`를 `AgencyCustomerSearchRequest`에 필드로는 유지하되(프론트 계약 유지), **서비스 로직에서는 필터 조건으로 사용하지 않는다.**
- 추후 DB 마이그레이션으로 두 컬럼이 추가되면 `AgencyCustomerService.searchCustomers()`의 Repository 쿼리에 조건을 추가해야 한다.
- 이 사실을 코드 내 한글 주석으로 명시한다.

---

## 응답

### 200 OK

```json
{
  "stats": {
    "totalCount": 12458,
    "activeCount": 8756,
    "inactiveCount": 3702,
    "newThisMonth": 568,
    "prevMonthDiff": 425,
    "newThisMonthDiff": 68
  },
  "content": [
    {
      "userId": 1,
      "name": "김민수",
      "phone": "010-1234-5678",
      "email": "kimms@email.com",
      "address": "서울특별시 강남구 테헤란로 123",
      "joinedAt": "2024-06-18T00:00:00",
      "joinPath": "모바일 앱",
      "status": "ACTIVE",
      "applianceCount": 5,
      "lastLoginAt": "2024-06-18T14:30:00"
    }
  ],
  "totalElements": 12458,
  "totalPages": 1246,
  "currentPage": 0,
  "size": 10
}
```

### 응답 필드 설명

| 필드 | 타입 | 설명 |
|---|---|---|
| `stats.totalCount` | long | 본 대행사로부터 COMPLETED 서비스를 1회 이상 받은 전체 고객 수 (검색 필터와 무관, 항상 전체 모수 기준) |
| `stats.activeCount` | long | 위 모수 중 `status = ACTIVE`인 고객 수 |
| `stats.inactiveCount` | long | 위 모수 중 `status = INACTIVE`인 고객 수 (`SUSPENDED`는 `totalCount`에만 포함되고 별도 필드 없음 — 응답 스펙에 명시된 필드만 채움) |
| `stats.newThisMonth` | long | 위 모수 중 이번 달(1일 00:00 ~ 다음달 1일 00:00 미만) 가입 고객 수 |
| `stats.prevMonthDiff` | long | 위 모수 중 **저번 달** 가입 고객 수 (이번 달과 비교하는 기준값) |
| `stats.newThisMonthDiff` | long | `newThisMonth - prevMonthDiff` (전월 대비 이번 달 신규 가입 증감) |
| `content[].userId` | Long | 고객 user_id |
| `content[].name` | String | 고객 이름 |
| `content[].phone` | String | 연락처 |
| `content[].email` | String | 이메일 |
| `content[].address` | String | `regions.name`(거주 지역) + 공백 + `users.address_detail`을 합친 문자열. 둘 중 하나라도 없으면 있는 값만 사용, 둘 다 없으면 빈 문자열 |
| `content[].joinedAt` | LocalDateTime | 가입일시 (`users.created_at`) |
| `content[].joinPath` | String | 가입 경로(SOCIAL/WEB/APP 등) — **`users` 테이블에 대응 컬럼이 없어(DB 미지원) 현재는 항상 `null` 반환. 추후 컬럼 추가 시 매핑 예정** |
| `content[].status` | String | 계정 상태 |
| `content[].applianceCount` | int | 등록한 가전 수 (논리 삭제되지 않은 `appliances` 기준) |
| `content[].lastLoginAt` | LocalDateTime | 최근 로그인 일시 (null 가능) |
| `totalElements` | long | 검색 조건 적용 후 전체 건수 (페이징 대상) |
| `totalPages` | int | 전체 페이지 수 |
| `currentPage` | int | 현재 페이지 번호 |
| `size` | int | 페이지 크기 |

### 에러 응답

| HTTP 상태 | 조건 |
|---|---|
| 401 Unauthorized | JWT 토큰 없음/만료(Security 필터 단계), 또는 role != AGENCY(서비스 레이어 검증, `IllegalAccessException`) |

> `agencyId`가 없는 경우는 발생하지 않는다 — JWT 발급 시점에 AGENCY 역할이면 항상 `agencyId` 클레임이 포함된다(`CustomUserDetails` 참고). 따라서 본 API에서는 별도의 "소속 대행사 없음" 404 분기를 두지 않는다.

---

## 처리 로직 (Pipeline)

1. **검증 단계**
   - 서비스 레이어에서 `userDetails.getRole() == "AGENCY"` 확인 → 아니면 `IllegalAccessException`(401)
   - `userDetails.getAgencyId()`로 대행사 ID 추출

2. **데이터 처리 단계**
   - `AsAssignmentRepository.findDistinctCompletedCustomerIdsByAgencyId(agencyId)`로 COMPLETED 서비스를 받은 고객 user_id DISTINCT 목록 조회
   - 목록이 비어있으면 stats 전부 0, content 빈 리스트로 즉시 반환 (DB 추가 조회 없이 단락 처리)
   - **stats**: 위 고객 id 목록 기준으로 `UserRepository`의 `countByIdInAndRole`, `countByIdInAndStatus`, `countByIdInAndCreatedAtBetween` 등 파생 쿼리로 집계 (검색 필터 미적용, 항상 전체 모수)
   - **content**: 위 고객 id 목록 + `keyword`/`status`/`joinedFrom`/`joinedTo` 필터를 적용한 `UserRepository.searchAgencyCustomers(...)` 페이징 조회 (`region`을 `LEFT JOIN FETCH`하여 N+1 방지)
   - 조회된 고객들의 `userId` 목록으로 `ApplianceRepository`에서 가전 개수를 한 번에 GROUP BY 집계(N+1 방지) 후 Map으로 변환해 DTO 매핑 시 사용
   - `address`는 `region.getName() + " " + addressDetail` 조합(서비스 레이어에서 null-safe 처리)

3. **응답 단계**
   - `AgencyCustomerListResponse.of(stats, page, applianceCountMap)`로 변환 후 200 OK 반환

---

## 예외 처리 및 제약 조건

- 표준 예외 4종(`NoSuchElementException`/`IllegalArgumentException`/`IllegalStateException`/`IllegalAccessException`) 사용 원칙 준수하되, 본 API는 정상 플로우에서 예외가 발생할 분기가 없음(고객이 0명이어도 정상 응답)
- 모든 조회는 `@Transactional(readOnly = true)`
- `joinedFrom`/`joinedTo` 파싱 실패(잘못된 날짜 형식) 시 `IllegalArgumentException` → 400

---

## 개발 및 출력 요구사항

| 계층 | 클래스 |
|---|---|
| Controller | `com.careflow.agency.controller.AgencyCustomerController` |
| Service | `com.careflow.agency.service.AgencyCustomerService` |
| Repository (수정) | `com.careflow.assignment.repository.AsAssignmentRepository` (메서드 추가), `com.careflow.user.repository.UserRepository` (메서드 추가), `com.careflow.appliance.repository.ApplianceRepository` (메서드 추가) |
| Request DTO | `com.careflow.agency.dto.request.AgencyCustomerSearchRequest` |
| Response DTO | `com.careflow.agency.dto.response.AgencyCustomerListResponse` (Stats, CustomerSummary 내부 record 포함) |

- `ApplianceRepository`의 기존 `@Param` import가 `io.lettuce.core.dynamic.annotation.Param`로 잘못되어 있어(Spring Data JPA 파라미터 바인딩 불가) `org.springframework.data.repository.query.Param`으로 함께 수정한다.

---

## 테스트 명세 (필수 — 아래 항목을 반드시 준수한다)

### JUnit 5 단위 테스트 (Service Layer, Mockito)

**파일**: `src/test/java/com/careflow/agency/service/AgencyCustomerServiceTest.java`

- TC-1. 정상 조회 — COMPLETED 고객 2명 존재, 필터 없음 → content size 2, stats 정상 매핑
- TC-2. COMPLETED 고객 0명 → stats 전부 0, content 빈 리스트, Repository 추가 조회(검색/통계) 호출 안 됨(Mockito verify)
- TC-3. keyword 검색 시 Repository에 올바른 파라미터 전달 검증
- TC-4. joinedFrom/joinedTo 파싱 — 정상 날짜 문자열 → LocalDateTime 범위로 변환되어 Repository 호출
- TC-5. joinedFrom 잘못된 형식("2024-13-99") → `IllegalArgumentException`
- TC-6. address 조합 — region 있음 + addressDetail 있음 → "지역명 상세주소" 형태로 합쳐짐
- TC-7. address 조합 — region null인 경우 addressDetail만 사용(NPE 없이 처리)
- TC-8. applianceCount — 가전 보유 고객/미보유 고객 혼합 시 Map 매핑 정확성(미보유 고객은 0)

### JUnit 5 통합 테스트 (H2 DB 연동, `@SpringBootTest` + `@ActiveProfiles("local")`)

**파일**: `src/test/java/com/careflow/agency/service/AgencyCustomerServiceIntegrationTest.java`

- 기존 `AgencyEngineerServiceIntegrationTest` 패턴을 따른다: `@Sql(scripts = "/cleanup.sql", executionPhase = BEFORE_TEST_METHOD)`로 매 테스트 전 초기화, `@BeforeEach`에서 대행사/유저/카테고리/지역 INSERT
- TC-I-1. H2에 실제 AsRequest → AsAssignment(COMPLETED)까지 INSERT한 고객만 목록에 포함되는지 검증(PENDING/ACCEPTED 등 미완료 배정 고객은 제외)
- TC-I-2. 타 대행사의 COMPLETED 배정 고객은 결과에서 제외되는지 검증
- TC-I-3. 동일 고객이 COMPLETED 서비스를 여러 번 받아도 DISTINCT로 1건만 집계되는지 검증
- TC-I-4. appliances 테이블에 실제 INSERT한 가전 개수가 `applianceCount`에 정확히 반영되는지 검증(논리 삭제된 가전은 제외)
- TC-I-5. regions + users.address_detail 실제 JOIN 결과로 `address` 필드가 올바르게 합쳐지는지 검증
- TC-I-6. 페이징 — 11명 INSERT 후 `size=10`으로 조회 시 1페이지 10건, `totalPages=2` 검증
- TC-I-7. status 필터 — ACTIVE/INACTIVE 혼합 INSERT 후 `status=ACTIVE` 조건으로 content는 필터링되지만 stats는 전체 모수 기준 유지되는지 검증
- TC-I-8. keyword 검색 — 이름/연락처/이메일 각각으로 부분 일치 검색 시 정상 매칭

### JUnit 5 컨트롤러 테스트 (`@WebMvcTest`)

**파일**: `src/test/java/com/careflow/agency/controller/AgencyCustomerControllerTest.java`

- 기존 `AgencyEngineerControllerTest` 패턴(`@Import({SecurityConfig.class, PasswordEncoderConfig.class})`, `@MockitoBean` JwtProvider 등 OAuth2 관련 빈 포함) 그대로 따른다
- TC-C-1. 인증된 AGENCY 역할 — 200 OK + 응답 JSON 구조(stats/content/totalElements 등) 검증
- TC-C-2. 인증 없음(anonymous) — 401
- TC-C-3. page/size 쿼리 파라미터 기본값 적용 검증(미전달 시 page=0, size=10으로 Service 호출)
- TC-C-4. 요청 바디 없이 호출 시에도 정상 동작(빈 필터로 처리)
