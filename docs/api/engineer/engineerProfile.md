# 🚀 API 생성 및 개발 요구사항 정의서 — 수리기사 프로필 (Engineer Profile)

## 1. 데이터 모델 (Schema)
- 메인 스키마 정의 파일 : `sql/CareFlow_DDL_v5.sql` (DB명세서 v19 기준)
- 위 파일에서 `engineer_profiles`, `engineer_expert_brands`, `engineer_service_regions`, `appliance_categories`, `regions`, `users`, `agencies` 테이블 위주로 참조할 것
- 해당 테이블과 매핑되는 Entity 클래스가 없을 시 테이블 구조를 참조하여 직접 생성할 것
    - `EngineerProfile`(engineer_profiles), `EngineerExpertBrand`(engineer_expert_brands), `EngineerServiceRegion`(engineer_service_regions) 직접 구현
    - `ApplianceCategory`(appliance_categories), `Regions`(regions), `User`(users), `Agencies`(agencies) 는 타 도메인 Entity 를 재사용
- 핵심 제약 조건
    - `engineer_profiles.user_id` **UNIQUE** : 기사 1명당 프로필 1건(1:1) 강제
    - 전문 가전 카테고리(`category_id`)는 **소분류(depth=2)** 카테고리만 허용 (요구사항 E-02, 단일 등록)
    - 서비스 가능 지역(`engineer_service_regions.region_id`)은 **구 단위(depth=2)** 지역만 허용
    - 전문 브랜드(N) · 서비스 지역(N) 은 별도 정규화 테이블로 관리하며, 갱신 시 **"전체 삭제 후 재INSERT"(delete-then-insert)** 패턴으로 일관성을 보장
- **전제** : 계정 가입 승인(`account_requests` APPROVED) 시점에 `users` INSERT 와 함께 **빈 프로필(`EngineerProfile.createInitial`)이 선생성**되어 있어야 한다 (요구사항 E-01).

## 2. API 엔드포인트 명세
- 공통 : JWT 인증 필요(`role = ENGINEER`), `@AuthenticationPrincipal CustomUserDetails` 에서 `userId` 추출
- ⚠ **(변경) URI 단수형 통일** : 프론트엔드 `axios` 규격에 맞춰 클래스 매핑을 `/api/engineer/profile` 로 운영한다. (구 `/api/engineers/me/profile` 에서 변경)

### [POST] /api/engineer/profile - EngineerProfileController.completeProfile
- **설명**: 기사 첫 로그인 시 선생성된 빈 프로필을 완성한다(전문 카테고리·경력 시작 연도·소개·전문 브랜드·서비스 지역). 경력 시작 연도로 기술 등급을 자동 산정한다. (요구사항 E-02, E-04)
- **Request Body**: `@RequestBody @Valid CreateProfileRequest request`
    - `categoryId`(Integer, 필수), `careerStartedYear`(Integer, 필수, 1950 이상), `introduction`(String, 선택)
    - `expertBrands`(List`<String>`, 1개 이상 필수), `serviceRegionIds`(List`<Integer>`, 1개 이상 필수)
- **Response (200 OK)**: `ProfileResponse`

### [GET] /api/engineer/profile - getProfile
- **설명**: 본인 프로필 상세 조회 (전문 브랜드·서비스 지역 + 사용자/대행사 합본 포함)
- **Response (200 OK)**: `ProfileResponse`

### [PUT] /api/engineer/profile - updateProfile
- **설명**: 프로필 부분 수정 (소개글·프로필 사진·**경력 시작 연도**, 그리고 선택적으로 전문 브랜드·서비스 지역 재설정). 경력 시작 연도 변경 시 기술 등급을 재산정한다.
- **Request Body**: `@RequestBody UpdateProfileRequest request` (모든 필드 선택 — `careerStartedYear`, `introduction`, `profileImageUrl`, `expertBrands`, `serviceRegionIds`)
- **Response (200 OK)**: `ProfileResponse`

### [GET] /api/engineer/profile/me - getNavbarProfile ✨(신규)
- **설명**: 상단 내비게이션 바 표시용 요약 프로필을 조회한다(프로필 미등록 상태여도 안전하게 반환). 프로필이 없으면 `profileImageUrl` 은 `null`.
- **Response (200 OK)**: `EngineerNavbarResponse` — `name`(기사 이름), `role`(고정 문자열 `"수리 기사"`), `profileImageUrl`

### 📦 ProfileResponse 필드 명세 (✨ 프론트 연동을 위해 확장됨 — BFF 합본)
정적 팩토리는 `ProfileResponse.of(entity, expertBrands, serviceRegionIds, serviceRegionNames)` 를 사용한다(구 `from()` 대체). 프론트 화면이 ID가 아닌 **이름**과 **사용자 기본 정보**를 요구하므로 다음을 합본하여 반환한다.

| 분류 | 필드 | 설명 |
|---|---|---|
| 식별 | `profileId`, `userId` | 프로필/사용자 ID |
| 사용자(users) | `name`, `email`, `phone` | 기사 기본 정보 (`entity.getUser()` 합본) |
| 대행사(agencies) | `agencyName` | 소속 대행사명 (없으면 `"소속 없음"`) |
| 카테고리 | `categoryId`, `categoryName` | ID + 표시명("냉장고" 등, 없으면 `"미지정"`) |
| 브랜드 | `expertBrands`(List`<String>`) | 전문 브랜드명 목록 |
| 서비스 지역 | `serviceRegionIds`(List`<Integer>`), `serviceRegionNames`(List`<String>`) | 지역 ID + 지역명("강남구" 등) 동시 제공 |
| 등급/이수 | `skillLevel`, `careerStartedYear`, `isLmsCompleted` | 자동 산정 등급·경력·LMS 이수 여부 |
| 기타 | `introduction`, `profileImageUrl`, `avgRating`, `totalReviews`, `createdAt`, `updatedAt` | 소개·사진·평점·생성/수정 시각 |

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
    - **기술 등급(SkillLevel) 자동 산정** (요구사항 E-04) : `근무연차 = 현재연도 - careerStartedYear` (⚠ 현재 코드는 `+1` 보정이 없음 — 올해 시작=0년차부터 계산)
        - 0~5년 → `BEGINNER`(초급) / 6~10년 → `INTERMEDIATE`(중급) / 11년 이상 → `ADVANCED`(고급)
    - 프로필 본문 갱신 : `profile.completeProfile(category, careerStartedYear, skillLevel, introduction)`
    - **서비스 지역 갱신** : `delete-then-insert` — 기존 매핑 전체 삭제 후, 중복 제거된 `serviceRegionIds` 를 `depth=2` 검증하며 재INSERT
    - **전문 브랜드 갱신** : `delete-then-insert` — 기존 매핑 전체 삭제 후, `trim`·공백 제거·중복 제거한 브랜드명을 재INSERT
3. **응답(Response) 단계** : HTTP `200`. 갱신 직후 **서비스 지역 이름 목록(`serviceRegionNames`)을 재조회**하여 `ProfileResponse.of(saved, brandNames, regionIds, regionNames)` 로 반환

### (2) [GET] 프로필 조회 — getProfile
- 본인 프로필 조회(없으면 거부) → 전문 브랜드 목록 + 서비스 지역(ID·이름) + 사용자/대행사/카테고리 합본을 조합하여 `ProfileResponse.of(...)` 반환

### (3) [PATCH] 프로필 수정 — updateProfile
1. **검증** : 인증, `User`·프로필 존재 확인
2. **처리** : `careerStartedYear` 가 있으면 미래 검증 후 등급 재산정. 이후 `updateBasicInfo(careerStartedYear, newSkillLevel, introduction, profileImageUrl)` 호출 — **각 값이 null 이면 기존 값 유지**(부분 수정). `expertBrands` / `serviceRegionIds` 는 **비어있지 않은 경우에만** `delete-then-insert` 로 교체(비어있으면 기존 유지). 지역이 교체된 경우 `serviceRegionNames` 를 다시 조회
3. **응답** : HTTP `200`, `ProfileResponse.of(...)` 반환

## 5. 예외 처리 (Error Handling) 및 제약 조건
- 모든 에러 발생 시 공통 포맷(`{ "success": false, "message": "에러 내용" }`)으로 응답할 것. (프로젝트 공통 `ErrorResponse` + `GlobalExceptionHandler` 사용)
- 프로필 본문·전문 브랜드·서비스 지역 갱신을 **하나의 `@Transactional`** 안에서 수행하되, 도중 DB 에러 발생 시 **모든 변경사항을 롤백**할 것
- 클래스 레벨 기본은 `@Transactional(readOnly = true)`, 쓰기 메서드(`completeProfile`, `updateProfile`)에만 `@Transactional` 개별 지정
- 합본 매핑 시 **NPE 방지** : `user.getAgency()`·`category` 가 null 일 수 있으므로 기본값(`"소속 없음"`/`"미지정"`)으로 안전 처리
- 주요 예외 매핑
    - 유저 / 프로필 / 카테고리 / 지역 정보 없음 → `400` (IllegalArgumentException)
    - 카테고리·지역 `depth` 위반, 경력 연도 미래, 이미 완성된 프로필 재작성 → `400`
    - 표준 스키마 위반(필수 누락, 연도 범위 위반) → `400` (MethodArgumentNotValidException)

## 6. 개발 및 출력 요구사항
- 컨트롤러 · 서비스 · 리포지토리 · 엔티티 레이어를 명확히 분리하여 구현할 것 (package-by-feature 구조)
- 엔티티 컨벤션 준수 : `@Getter` + `@NoArgsConstructor(access = PROTECTED)` + `@Builder`/정적 팩토리, `@Setter`/`@Data` 금지, 상태 변경은 도메인 메서드(`completeProfile`, `updateBasicInfo`)로만 수행
- **응답 DTO 는 정적 팩토리 `of()` 로 변환** (사용자/대행사/지역명 합본을 위해 `from()` 에서 변경됨)
- 전문 브랜드·서비스 지역은 `delete-then-insert` 패턴을 유지하여 산정 일관성을 보장
- 작성된 API 에 대해 단위 테스트(JUnit5 + Mockito)와 통합 테스트(`@SpringBootTest` + H2 인메모리)를 함께 생성할 것
    - 프로필 생성은 `createInitial()`(빈 프로필) → `completeProfile()`(완성) 흐름을 반영하여 픽스처를 구성할 것
    - SkillLevel 경계(5년/10년) 자동 산정, depth 위반, 이미 완성된 프로필 재작성 등 분기를 검증할 것
    - **(합본 확장 반영)** 단위 테스트 픽스처는 `getUser()`(name·email·phone·agency)·`getCategory()`(name·id)·`getRegion()`(name) 까지 탐색 가능하도록 구성하고, `categoryName`·`serviceRegionNames`·`agencyName` 단언을 추가할 것