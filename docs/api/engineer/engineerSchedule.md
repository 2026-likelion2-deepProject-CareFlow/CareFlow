# 🚀 API 생성 및 개발 요구사항 정의서 — 수리기사 근무 스케줄 (Engineer Schedule)

## 1. 데이터 모델 (Schema)
- 메인 스키마 정의 파일 : `sql/CareFlow_DDL_v5.sql` (DB명세서 v19 기준)
- 위 파일에서 `engineer_schedules`, `engineer_schedule_slots`, `engineer_profiles`, `users` 테이블 위주로 참조할 것
- 해당 테이블과 매핑되는 Entity 클래스가 없을 시 테이블 구조를 참조하여 직접 생성할 것
    - `EngineerSchedule`(engineer_schedules), `EngineerScheduleSlot`(engineer_schedule_slots) 직접 구현
    - `EngineerProfile`, `User` 는 재사용
- 핵심 제약 조건
    - `engineer_schedules` **UNIQUE(`user_id`, `work_date`)** : 기사 1명당 하루 1개의 근무표만 등록 가능
    - 근무 시간 슬롯(`engineer_schedule_slots`)은 근무표(`engineer_schedules`)에 **Cascade** 로 종속 저장되며, `start_time ASC` 정렬로 조회
    - 근무표 상태 `status` : `AVAILABLE`(배차 가능) / `BOOKED`(A/S 배정됨) / `OFF`(휴무)
- 본 데이터(근무 날짜·시간 슬롯)는 추후 **A/S 자동 배차 매칭**에서 기사의 근무 가능 여부를 필터링하는 기준이 된다.

## 2. API 엔드포인트 명세
- 공통 : JWT 인증 필요(`role = ENGINEER`), `@AuthenticationPrincipal CustomUserDetails` 에서 `userId` 추출
- ⚠ **(변경) URI 단수형 통일** : 프론트엔드 `axios` 규격(`/api/engineer/my-schedule`)에 맞춰 클래스 매핑을 변경한다. (구 `/api/engineers/me/schedules`)

### [POST] /api/engineer/my-schedule - EngineerScheduleController.createSchedule
- **설명**: 특정 날짜의 근무표(근무 가능 시간 슬롯 N개)를 등록한다. **프로필 완성이 선행되어야 한다.** (요구사항 E-03)
- **Request Body**: `@Valid @RequestBody ScheduleRequest request`
    - `workDate`(LocalDate, 필수, **오늘 이후**(`@FutureOrPresent`))
    - `timeSlots`(List, 1개 이상 필수) — 각 슬롯 `start` / `end` (String, **`"HH:mm"` 형식**, `00:00 ~ 23:59`)
- **Response (201 Created)**: `ScheduleResponse` (scheduleId, workDate, timeSlots[], status)

### [GET] /api/engineer/my-schedule?year={year}&month={month} - getMonthlySchedules
- **설명**: 본인의 월간 근무 일정을 조회한다(근무 날짜 오름차순).
- **Query Params**: `year`(int, 필수), `month`(int, 필수)
- **Response (200 OK)**: `List<ScheduleResponse>`

### [GET] /api/engineer/my-schedule/schedule?date={yyyy-MM-dd} - getDailySchedule ✨(신규)
- **설명**: 프론트엔드 미니 달력에서 **특정 날짜를 클릭**했을 때 그 날의 근무표 단건을 조회한다.
- **Query Params**: `date`(LocalDate, 필수, `@DateTimeFormat(iso = DATE)`)
- **Response (200 OK)**: `ScheduleResponse` (해당 날짜 근무표)
- **참고**: 해당 날짜 근무표가 없으면 `IllegalArgumentException` → `400` (프론트는 "일정 없음"으로 처리). 추후 빈 응답/`204`로 바꿀지 팀과 협의 가능.

### [DELETE] /api/engineer/my-schedule/{scheduleId} - deleteSchedule
- **설명**: 근무표를 삭제한다. 물리 삭제가 아니라 **`OFF`(휴무) 상태로 전환하고 시간 슬롯을 제거**하는 soft-delete 방식이다. **이미 A/S가 배정된(`BOOKED`) 근무표는 삭제할 수 없다.**
- **Path Variable**: `scheduleId`(Long)
- **Response (200 OK)**: 안내 문자열(`"해당 날짜의 근무표가 휴무(OFF) 처리되었습니다."`)

## 3. 상세 처리 로직 (Pipeline)

### (1) [POST] 근무표 등록 — createSchedule
1. **검증(Validation) 단계**
    - 인증 확인 및 `User` 존재 확인, `role = ENGINEER` 권한 검증
    - 프로필 존재 + **`isCompleted()` 검증** — 전문 분야 등 프로필 필수 정보 완성이 선행되어야 등록 가능
    - **해당 날짜 중복 검사**(`existsByUser_IdAndWorkDate`) — 이미 근무표가 있으면 거부
    - 시간 슬롯 파싱·검증(`validAndParseTimeSlots`)
        - 최소 1개 이상
        - `"HH:mm"` → `LocalTime` 파싱 후 시작 시간 기준 **정렬**
        - **역전 방어** : 각 슬롯은 `start < end` 여야 함
        - **겹침 방어** : 정렬된 인접 슬롯 간 `직전 슬롯.end ≤ 현재 슬롯.start`
2. **데이터 처리(Process) 단계**
    - `EngineerSchedule`(status = `AVAILABLE`) 생성 후, 검증된 슬롯을 `addTimeSlot()` 으로 연관(부모-자식 양방향) → Cascade 저장
3. **응답(Response) 단계** : HTTP `201`, `ScheduleResponse.from(...)` 반환

### (2) [GET] 월간 근무 일정 조회 — getMonthlySchedules
- `year`·`month` 로 해당 월의 시작일~말일 범위를 계산
- `findByUser_IdAndWorkDateBetweenOrderByWorkDateAsc` 로 조회 후 `List<ScheduleResponse>` 로 변환하여 반환

### (3) [GET] 일간 근무 일정 조회 — getDailySchedule ✨(신규)
- `findByUser_IdAndWorkDateBetweenOrderByWorkDateAsc(userId, date, date)` 로 해당 날짜만 조회 후 첫 건을 `ScheduleResponse.from(...)` 으로 변환
- 결과가 없으면 `IllegalArgumentException`("해당 날짜에 등록된 근무표가 없습니다.")

### (4) [DELETE] 근무표 삭제(OFF 처리) — deleteSchedule
1. **검증** : 근무표 존재 확인, **본인 소유 검증**(`schedule.user.id == userId`)
2. **상태 검증** : `BOOKED`(배정됨) 상태면 삭제 불가
3. **처리** : `status → OFF` 전환 + `timeSlots.clear()`(orphanRemoval 로 슬롯 삭제) — soft delete
4. **응답** : HTTP `200`, 안내 문자열 반환

## 5. 예외 처리 (Error Handling) 및 제약 조건
- 모든 에러 발생 시 공통 포맷(`{ "success": false, "message": "에러 내용" }`)으로 응답할 것. (프로젝트 공통 `ErrorResponse` + `GlobalExceptionHandler` 사용)
- 등록/삭제 로직을 **하나의 `@Transactional`** 안에서 수행하되, 도중 DB 에러 발생 시 모든 변경사항을 롤백할 것 (조회 메서드는 `@Transactional(readOnly = true)`)
- 주요 예외 매핑
    - 유저 / 프로필 / 근무표 정보 없음 → `400` (IllegalArgumentException)
    - 프로필 미완성 상태에서 근무표 등록 → `400`
    - 시간 슬롯 역전 / 겹침 / `"HH:mm"` 형식 위반 / 최소 1개 미충족 → `400`
    - **일간 조회 시 해당 날짜 근무표 없음 → `400`** (IllegalArgumentException)
    - 타인의 근무표 삭제 시도 → `400`
    - **`BOOKED`(배정됨) 근무표 삭제 시도 → `403`** (IllegalStateException)
- **동시 등록 방어** : 같은 날짜에 대한 동시 요청은 `uk_eng_schedule` UNIQUE 제약이 최종 방어선이 되며, `GlobalExceptionHandler` 에서 해당 제약 위반을 감지해 `409 Conflict` 로 응답한다(애플리케이션 선검사 `400` + DB 제약 `409` 2중 방어).

## 6. 개발 및 출력 요구사항
- 컨트롤러 · 서비스 · 리포지토리 · 엔티티 레이어를 명확히 분리하여 구현할 것 (package-by-feature 구조)
- 엔티티 컨벤션 준수 : `@Getter` + `@NoArgsConstructor(access = PROTECTED)` + `@Builder`, `@Setter`/`@Data` 금지, 상태 변경은 도메인 메서드(`changeScheduleStatus`, `addTimeSlot`)로만 수행
- 응답 DTO 는 정적 팩토리 `from()` 으로 변환
- 시간 슬롯의 **역전·겹침 검증 로직을 유지**할 것 (자동 배차 매칭의 기준 데이터 정합성 보장)
- 작성된 API 에 대해 단위 테스트(JUnit5 + Mockito)와 통합 테스트(`@SpringBootTest` + H2 인메모리)를 함께 생성할 것
    - 시간 슬롯 겹침 / 역전 / 날짜 중복 / 프로필 미완성 / `BOOKED` 삭제 시도 등 실패 케이스를 함께 검증할 것
    - **(신규)** `getDailySchedule` 성공(해당 날짜 1건 반환) 및 실패(근무표 없음 → 예외) 케이스를 추가할 것