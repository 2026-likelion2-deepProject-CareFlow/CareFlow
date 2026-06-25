# 🚀 API 생성 및 개발 요구사항 정의서 — 수리기사 프로필 (Engineer Profile)

## 1. 데이터 모델 (Schema)
- 메인 스키마 정의 파일 : `sql/CareFlow_DDL_v5.sql` (DB명세서 v19 기준)
- 위 파일에서 `engineer_profiles`, `engineer_expert_brands`, `engineer_service_regions`, `appliance_categories`, `regions`, `users` 테이블 위주로 참조할 것
- 해당 테이블과 매핑되는 Entity 클래스가 없을 시 테이블 구조를 참조하여 직접 생성할 것
    - `EngineerProfile`(engineer_profiles), `EngineerExpertBrand`(engineer_expert_brands), `EngineerServiceRegion`(engineer_service_regions) 직접 구현
    - `ApplianceCategory`(appliance_categories), `Regions`(regions), `User`(users) 는 타 도메인 Entity 를 재사용
- 핵심 제약 조건
    - `engineer_profiles.user_id` **UNIQUE** : 기사 1명당 프로필 1건(1:1) 강제
    - 전문 가전 카테고리(`category_id`)는 **소분류(depth=2)** 카테고리만 허용 (요구사항 E-02, 단일 등록)
    - 서비스 가능 지역(`engineer_service_regions.region_id`)은 **구 단위(depth=2)** 지역만 허용
    - 전문 브랜드(N) · 서비스 지역(N) 은 별도 정규화 테이블로 관리하며, 갱신 시 **"전체 삭제 후 재INSERT"(delete-then-insert)** 패턴으로 일관성을 보장
- **전제** : 계정 가입 승인(`account_requests` APPROVED) 시점에 `users` INSERT 와 함께 **빈 프로필(`EngineerProfile.createInitial`)이 선생성**되어 있어야 한다 (요구사항 E-01).

## 2. API 엔드포인트 명세
- 이미 URI 로 매핑된 API 가 존재할 시 아래의 요구사항대로 코드를 변경할 것
- 공통 : JWT 인증 필요(`role = ENGINEER`), `@AuthenticationPrincipal CustomUserDetails` 에서 `userId` 추출

### [PUT] /api/engineers/me/profile - EngineerProfileController.completeProfile
- **설명**: 기사 첫 로그인 시 선생성된 빈 프로필을 완성한다(전문 카테고리·경력 시작 연도·소개·전문 브랜드·서비스 지역). 경력 시작 연도로 기술 등급을 자동 산정한다. (요구사항 E-02, E-04)
- **Request Body**: `@Valid @RequestBody CreateProfileRequest request`
    - `categoryId`(Integer, 필수), `careerStartedYear`(Integer, 필수, 1950 이상), `introduction`(String, 선택)
    - `expertBrands`(List`<String>`, 1개 이상 필수), `serviceRegionIds`(List`<Integer>`, 1개 이상 필수)
- **Response (200 OK)**: `ProfileResponse` (프로필 전체 + 전문 브랜드 목록 + 서비스 지역 ID 목록)

### [GET] /api/engineers/me/profile - getProfile
- **설명**: 본인 프로필 상세 조회 (전문 브랜드·서비스 지역 포함)
- **Response (200 OK)**: `ProfileResponse`

### [PATCH] /api/engineers/me/profile - updateProfile
- **설명**: 프로필 부분 수정 (소개글·프로필 사진, 그리고 선택적으로 전문 브랜드·서비스 지역 재설정)
- **Request Body**: `@RequestBody UpdateProfileRequest request` (모든 필드 선택 — `introduction`, `profileImageUrl`, `expertBrands`, `serviceRegionIds`)
- **Response (200 OK)**: `ProfileResponse`

## 3. 상세 처리 로직 (Pipeline)

### (1) [PUT] 프로필 완성 — completeProfile
1. **검증(Validation) 단계**
    - 인증 확인(`@AuthenticationPrincipal`) 및 `User` 존재 확인
    - `role = ENGINEER` 권한 검증 (그 외 권한이면 거부)
    - 선생성된 빈 프로필 존재 확인(`findByUser_Id`) — 없으면 "대행사 승인이 완료되지 않은 기사" 로 거부
    - **이미 완성된 프로필이면 거부**(`isCompleted()` = category·careerStartedYear 존재 여부)
    - 전문 카테고리 `depth = 2`(소분류) 검증
    - 경력 시작 연도 **미래 불가** 검증 (`@Min(1950)` + 현재 연도 초과 차단)
2. **데이터 처리(Process) 단계**
    - **기술 등급(SkillLevel) 자동 산정** (요구사항 E-04) : `근무연차 = 현재연도 - careerStartedYear + 1`
        - 1~5년 → `BEGINNER`(초급) / 6~10년 → `INTERMEDIATE`(중급) / 11년 이상 → `ADVANCED`(고급)
    - 프로필 본문 갱신 : `profile.completeProfile(category, careerStartedYear, skillLevel, introduction)`
    - **서비스 지역 갱신** : `delete-then-insert` — 기존 매핑 전체 삭제 후, 중복 제거된 `serviceRegionIds` 를 `depth=2` 검증하며 재INSERT
    - **전문 브랜드 갱신** : `delete-then-insert` — 기존 매핑 전체 삭제 후, `trim`·공백 제거·중복 제거한 브랜드명을 재INSERT
3. **응답(Response) 단계** : HTTP `200`, `ProfileResponse.from(...)` 반환

### (2) [GET] 프로필 조회 — getProfile
- 본인 프로필 조회(없으면 거부) → 전문 브랜드 목록 + 서비스 지역 ID 목록을 조합하여 `ProfileResponse` 반환

### (3) [PATCH] 프로필 수정 — updateProfile
1. **검증** : 인증, `User`·프로필 존재 확인
2. **처리** : `updateBasicInfo(introduction, profileImageUrl)` — **각 값이 null 이면 기존 값 유지**(부분 수정). `expertBrands` / `serviceRegionIds` 는 **비어있지 않은 경우에만** `delete-then-insert` 로 교체(비어있으면 기존 유지)
3. **응답** : HTTP `200`, `ProfileResponse` 반환

## 5. 예외 처리 (Error Handling) 및 제약 조건
- 모든 에러 발생 시 공통 포맷(`{ "success": false, "message": "에러 내용" }`)으로 응답할 것. (프로젝트 공통 `ErrorResponse` + `GlobalExceptionHandler` 사용)
- 프로필 본문·전문 브랜드·서비스 지역 갱신을 **하나의 `@Transactional`** 안에서 수행하되, 도중 DB 에러 발생 시 **모든 변경사항을 롤백**할 것 (브랜드만 갱신되고 지역은 누락되는 등의 부분 반영 방지)
- 클래스 레벨 기본은 `@Transactional(readOnly = true)`, 쓰기 메서드(`completeProfile`, `updateProfile`)에만 `@Transactional` 개별 지정
- 주요 예외 매핑
    - 유저 / 프로필 / 카테고리 / 지역 정보 없음 → `400` (IllegalArgumentException)
    - 카테고리·지역 `depth` 위반, 경력 연도 미래, 이미 완성된 프로필 재작성 → `400`
    - 표준 스키마 위반(필수 누락, 연도 범위 위반) → `400` (MethodArgumentNotValidException)

## 6. 개발 및 출력 요구사항
- 컨트롤러 · 서비스 · 리포지토리 · 엔티티 레이어를 명확히 분리하여 구현할 것 (package-by-feature 구조)
- 엔티티 컨벤션 준수 : `@Getter` + `@NoArgsConstructor(access = PROTECTED)` + `@Builder`/정적 팩토리, `@Setter`/`@Data` 금지, 상태 변경은 도메인 메서드(`completeProfile`, `updateBasicInfo`)로만 수행
- 응답 DTO 는 정적 팩토리 `from()` 으로 변환
- 전문 브랜드·서비스 지역은 `delete-then-insert` 패턴을 유지하여 산정 일관성을 보장
- 작성된 API 에 대해 단위 테스트(JUnit5 + Mockito)와 통합 테스트(`@SpringBootTest` + H2 인메모리)를 함께 생성할 것
    - 프로필 생성은 `createInitial()`(빈 프로필) → `completeProfile()`(완성) 흐름을 반영하여 픽스처를 구성할 것
    - SkillLevel 경계(5년/10년) 자동 산정, depth 위반, 이미 완성된 프로필 재작성 등 분기를 검증할 것
