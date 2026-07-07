# CLAUDE.md

이 문서는 Claude Code(및 다른 AI 코딩 어시스턴트)가 CareFlow 프로젝트에서 작업할 때 참고하는 가이드입니다.

## 프로젝트 개요

CareFlow는 가전제품 A/S(After Service) 접수·배정·관리를 위한 백엔드 API 서버입니다. 고객(CUSTOMER), 대행사(AGENCY), 수리기사(ENGINEER), 관리자(ADMIN) 4개의 역할이 협업하는 구조로, 대행사 회원가입 → 관리자 승인 → 계정 생성 → A/S 접수 → 기사 배정 → 작업 처리의 흐름을 가집니다.

## 기술 스택

- **언어**: Java 21 (Gradle Toolchain 고정)
- **프레임워크**: Spring Boot 3.5.15
- **빌드 도구**: Gradle (Groovy DSL, `build.gradle`)
- **데이터 접근**: Spring Data JPA (Hibernate)
- **DB**:
  - 운영(prod): MySQL (`mysql-connector-j`, `MySQL8Dialect`)
  - 로컬/테스트(local, test): H2 인메모리 DB (`MODE=MySQL` 호환 모드)
- **캐시/세션**: Spring Data Redis
- **인증/인가**: Spring Security + JWT (`io.jsonwebtoken:jjwt` 0.12.x/0.13.x) — Stateless 세션, `JwtFilter`가 `UsernamePasswordAuthenticationFilter` 앞에서 토큰 검증
- **검증**: Spring Validation (`jakarta.validation`, Bean Validation)
- **보일러플레이트 축소**: Lombok
- **테스트**: JUnit 5 (JUnit Platform), `spring-boot-starter-test`, `spring-security-test`, H2(테스트 전용 별도 버전 2.4.240)
- **기타**: Spring Boot DevTools(local 전용)

> ⚠️ `build.gradle`에 `jjwt-api`가 두 버전(0.13.0, 0.12.6)이 중복 선언되어 있습니다. 의존성 작업 시 버전 정리가 필요할 수 있습니다.

## 프로젝트 구조

패키지는 **계층형(Layered)이 아닌 도메인형(Domain-by-Feature)** 으로 구성됩니다. 각 도메인 패키지 하위에 `controller / service / repository / entity / dto` 서브패키지를 두는 것이 기본 패턴입니다.

```
com.careflow
├── CareflowApplication.java        # 부트 엔트리포인트
├── account_requests/               # 계정 생성 요청(승인 대기) 도메인
│   ├── controller / dto / entity / repository / service
├── agency/                         # 대행사 도메인
│   ├── controller / dto(request/response) / entity / repository / service
├── as_request/                     # A/S 접수 요청 도메인
│   ├── controller / dto / entity / repository / service
├── auth/                           # 인증(JWT 로그인/재발급) 도메인
│   ├── controller / dto / security(JwtFilter, JwtProvider, CustomUserDetails) / service
├── engineer/                       # 수리기사 도메인 (프로필/스케줄)
│   ├── controller / domain(entity, enums) / dto / repository / service
├── region/                         # 지역(행정구역) 도메인
│   ├── controller / dto / entity / repository / service
├── user/                           # 사용자(공통 회원) 도메인
│   ├── entity / repository / service
├── common/                         # 공통 모듈
│   ├── config/    (SecurityConfig 등)
│   ├── enums/     (Role, AgencyStatus, AsStatus, ApplianceStatus ... 전역 enum)
│   ├── exception/ (GlobalExceptionHandler)
│   ├── response/  (응답 래퍼 — 자리만 존재, 미구현)
│   └── util/       (자리만 존재, 미구현)
└── (스캐폴딩만 존재, 구현 진행 중인 도메인들)
    appliance, assignment, certificate, lms, notification, payment,
    repair_cost, review, scheduler, settlement, work_report, admin
```

리소스 구조:
```
src/main/resources/
├── application.yaml        # active profile 스위치만 담당 (local/prod)
├── application-local.yaml  # H2, Redis(localhost), JWT 비밀키, SQL 로깅
└── application-prod.yaml   # MySQL, Redis, JWT (시크릿은 환경변수 주입)
```

## Database Schema
- 메인 스키마 정의 파일 : `sql/CareFlow_DDL_v16.sql`
- 데이터베이스 수정 사항이나 쿼리를 작성할 때는 위 파일의 테이블 구조를 항상 참고하세요.

새로운 도메인을 추가할 때는 위와 동일하게 `도메인명/{controller,service,repository,entity,dto}` 패턴을 따르세요. 비어있는 패키지(`review`, `settlement`, `work_report`, `scheduler`, `repair_cost`, `payment`, `notification`, `lms`, `certificate`, `appliance`, `assignment`, `admin`)는 향후 구현을 위해 미리 만들어둔 자리이므로, 해당 기능 작업 시 그 패키지를 활용하세요.

## 코딩 컨벤션

### 패키지/네이밍
- 패키지명은 스네이크 케이스 허용(`account_requests`, `as_request`, `work_report` 등 도메인에 따라 혼용). 새 도메인 추가 시 기존 도메인의 네이밍과 통일성을 맞추기보다, 그 도메인 자체의 기존 합의를 우선 따르세요(일관되지 않은 부분이 있어 임의 변경하지 않는 것을 권장).
- 클래스명은 PascalCase, 복수형 사용이 섞여 있음(`Agencies`, `AgenciesController`, `AgenciesService` — 엔티티명도 복수형). 신규 도메인에서는 단수형(`User`, `Regions`처럼 일부는 단수/복수 혼재)을 프로젝트 기존 패턴에 맞게 선택하되, 같은 도메인 내에서는 일관성을 유지하세요.

### 엔티티(Entity)
- `@Entity` + `@Table(name = "...")` 명시(스네이크 케이스 테이블/컬럼명).
- `@Getter`만 사용(Setter 없음) — 불변성 지향, 상태 변경은 명시적 도메인 메서드(`updateLastLogin()` 등)로 처리.
- `@NoArgsConstructor(access = AccessLevel.PROTECTED)` — JPA 기본 생성자를 외부에 노출하지 않음.
- `@Builder`가 달린 생성자(전체 필드가 아닌 "생성 시 필요한 필드"만 받는 생성자)로 객체 생성.
- 정적 팩토리 메서드 `static Xxx create(...)` 패턴을 활용해 생성 로직을 캡슐화(`Agencies.create(...)`, `AccountRequests.create(...)` 참고).
- `createdAt` / `updatedAt` / `deletedAt`(soft delete 대비) 컬럼을 갖는 것이 기본 패턴이며, `columnDefinition`으로 DB 기본값(`DEFAULT CURRENT_TIMESTAMP` 등)을 명시하는 경우가 있음 — **H2 테스트 호환을 위해 이 `columnDefinition`을 함부로 제거하지 말 것** (코드 내 주석으로도 강조되어 있음).
- Enum 컬럼은 `@Enumerated(EnumType.STRING)` 사용.
- 연관관계는 기본적으로 `FetchType.LAZY` 사용.

### DTO
- 두 가지 스타일이 혼용됨:
  1. **Record 기반** (`AgencyCreateRequest` 등) — 불변 요청 DTO에 권장.
  2. **Lombok 클래스 기반** (`@Getter @NoArgsConstructor`, 예: `AsRequestCreateDto`) — 주로 Jackson 역직렬화가 필요한 요청/응답 DTO에 사용.
- 둘 다 허용되지만, 신규 작성 시 **단순 불변 요청 DTO는 record를 우선 고려**하고, 프레임워크 제약(역직렬화/상속 등)이 있는 경우 Lombok 클래스를 사용하세요.
- Bean Validation 어노테이션(`@NotBlank`, `@NotNull`, `@Email`, `@Pattern`, `@Size`, `@Digits` 등)을 DTO 필드에 직접 부여. 클래스 기반 DTO는 검증 실패 메시지를 한글로 직접 지정하는 경우가 많음 (`@NotNull(message = "...")`).

### Controller
- `@RestController` + `@RequestMapping("/api/...")` + `@RequiredArgsConstructor`(생성자 주입, 필드 주입 금지).
- 엔드포인트 경로는 `/api/리소스명(복수형, kebab-case)` 패턴 (`/api/agencies`, `/api/as-requests`).
- 요청 바디는 `@Valid @RequestBody`로 검증.
- 응답은 `ResponseEntity<T>`로 래핑, 상태코드는 명시적으로 지정(`HttpStatus.CREATED` 등). 별도의 공통 응답 래퍼(`ApiResponse` 등)는 아직 도입되어 있지 않음(`common/response` 패키지가 비어있어 향후 도입 예정으로 추정).
- 비즈니스 설명/주의사항은 코드 위에 한글 주석으로 상세히 남기는 문화가 있음 — 이 컨벤션을 유지하세요.
- 인증 정보가 필요한 API는 `@AuthenticationPrincipal`로 사용자 식별자를 받는 패턴으로 점진 전환 중(과거 코드 일부는 `@RequestParam Long customerId`로 임시 처리되어 있으며 주석에 "추후 대체 예정"이라고 명시됨 — 신규 API는 `@AuthenticationPrincipal` 사용을 우선하세요).

### Service
- `@Service` + `@RequiredArgsConstructor`.
- 조회 전용 메서드는 `@Transactional(readOnly = true)`, 변경 메서드는 `@Transactional` 명시.
- 예외는 비즈니스 의미에 맞는 표준 예외 타입을 사용:
  - 리소스 없음 → `NoSuchElementException`
  - 잘못된 입력값 → `IllegalArgumentException`
  - 상태/흐름상 허용되지 않는 요청 → `IllegalStateException`
  - 인증/권한 문제 → `IllegalAccessException`
  - 예외 메시지는 한글로 사용자에게 보여줄 수 있는 문장으로 작성.
- `GlobalExceptionHandler`(`@RestControllerAdvice`)가 위 예외들을 각각 HTTP 상태코드로 매핑하므로, **새로운 예외 타입을 도입하기 전에 기존 4종(`NoSuchElementException`/`IllegalArgumentException`/`IllegalStateException`/`IllegalAccessException`) 중 적합한 것이 있는지 먼저 검토**하세요.

### Repository
- Spring Data JPA `interface XxxRepository extends JpaRepository<Entity, Long>` 패턴.
- 커스텀 조회 메서드는 메서드 이름 기반 쿼리(`findByXxx`, `existsByXxx`) 우선 사용.

### 주석/문서화
- 한글 주석을 적극적으로 사용하는 프로젝트입니다(비즈니스 로직 배경, 처리 순서, 주의사항 등). 코드 수정/추가 시에도 이 문화를 따라 한글 주석을 남기는 것을 권장합니다.
- 일부 파일은 CRLF(`\r\n`) 줄바꿈을 사용합니다(Windows 환경 작업자 영향으로 추정). 기존 파일 수정 시 해당 파일의 기존 줄바꿈 스타일을 유지하세요.

### Git / 협업 컨벤션 (README.md 기준)
- **브랜치 전략**:
  - `main`: 배포용(직접 커밋 금지)
  - `develop`: 통합 개발 브랜치
  - `feature/기능명`: 기능 개발 (예: `feature/login`)
  - `fix/버그명`: 버그 수정 (예: `fix/error-404`)
- **커밋 메시지**: `타입 : 메시지 내용` 형식
  | 타입 | 의미 |
  |---|---|
  | Feat | 새로운 기능 추가 |
  | Fix | 버그 수정 |
  | Docs | 문서 수정 |
  | Style | 포맷팅 등 코드 변경 없는 수정 |
  | Refactor | 코드 리팩토링 |
  | Test | 테스트 코드 작성/수정 (실제 커밋 로그에서 사용됨) |
  | chore | 빌드/설정 등 기타 변경 (실제 커밋 로그에서 사용됨) |
- **PR 규칙**: `feature/*` → `develop`으로 PR. 충돌은 작업자가 직접 해결 후 push.

## 인증/보안 구조

- `SecurityConfig`: CSRF 비활성화, Stateless 세션, `JwtFilter`를 `UsernamePasswordAuthenticationFilter` 앞에 등록.
- 인증 없이 허용된 경로: `/`, `/api/auth/**`, (대행사 회원가입 경로 — 코드상 `/api/agency/signup`로 명시되어 있으나 실제 컨트롤러 매핑은 `/api/agencies/signup`이므로 **불일치 가능성이 있어 확인/수정이 필요**할 수 있음).
- `/h2-console/**`은 `WebSecurityCustomizer`로 시큐리티 필터 자체에서 제외.
- JWT: HS256 기반 서명(`Keys.hmacShaKeyFor`), access/refresh 토큰 분리 발급, payload에 `email`, `role` 클레임 포함.
- 비밀번호는 `BCryptPasswordEncoder`로 해싱하여 저장.

## 환경 설정 (application.yaml)

- 프로필 스위치: `application.yaml`의 `spring.profiles.active`로 `local`/`prod` 전환.
- `local`: H2 인메모리(`MySQL` 호환 모드), `ddl-auto: create-drop`, SQL 로그 상세 출력.
- `prod`: MySQL, `ddl-auto: update`, DB 계정은 `${DB_USERNAME}` / `${DB_PASSWORD}` 환경변수 주입.
- 공통: Redis(`localhost:6379`), JWT 시크릿/만료시간 설정.
- ⚠️ `jwt.secret`이 local/prod 모두 코드 내 평문으로 동일하게 박혀 있음 — 운영 반영 전 반드시 환경변수/시크릿 매니저로 분리 필요.

## 테스트

- `@WebMvcTest(XxxController.class)` + `@Import(SecurityConfig.class)`로 컨트롤러 슬라이스 테스트.
- `@MockitoBean`으로 서비스/`JwtProvider` 등 의존성 mocking (Spring Boot 3.4+ 스타일, `@MockBean` 아님에 유의).
- 테스트 DB는 H2(별도 버전 2.4.240) 사용.
- 테스트 커버리지는 아직 초기 단계(`agency`, `auth` 도메인 일부만 존재) — 신규 기능 추가 시 동일 패턴으로 컨트롤러 테스트를 함께 작성하는 것을 권장합니다.

## 빌드/실행

```bash
# 빌드
./gradlew build

# 테스트
./gradlew test

# 로컬 실행 (H2 사용, 기본 프로필)
./gradlew bootRun

# 운영 프로필로 실행 시
./gradlew bootRun --args='--spring.profiles.active=prod'
```

## 작업 시 주의사항 (Claude 전용 가이드)

1. 새 도메인 작업 시 `controller/service/repository/entity/dto` 5단 구조를 그대로 따를 것.
2. 엔티티에 Setter를 추가하지 말고, 의미 있는 도메인 메서드 또는 정적 팩토리 메서드로 상태 변경을 캡슐화할 것.
3. 예외 처리는 `GlobalExceptionHandler`가 핸들링하는 4종 표준 예외를 우선 재사용할 것.
4. `columnDefinition`이 지정된 컬럼은 H2 테스트 호환성 때문인 경우가 많으므로 임의로 제거하지 말 것.
5. 커밋 메시지는 README의 `타입 : 메시지` 형식을 따르고, 브랜치는 `feature/기능명` 또는 `fix/버그명`으로 생성할 것.
6. 비즈니스 로직에는 한글 주석으로 의도/순서/예외 상황을 설명하는 기존 스타일을 유지할 것.
7. `/h2-console`, JWT 시크릿 등 보안 관련 설정을 prod로 옮길 때는 반드시 환경변수화할 것.
8. 비어 있는 도메인 패키지(`review`, `settlement`, `payment` 등)에 기능을 추가할 때는 기존 구현 완료 도메인(`agency`, `as_request`, `engineer`)의 구조를 참고할 것.
