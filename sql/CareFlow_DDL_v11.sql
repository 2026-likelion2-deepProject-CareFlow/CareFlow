-- ============================================================
-- CareFlow DDL v11
-- DB명세서 v25 기준 / 모든 FK를 CREATE TABLE 내부 CONSTRAINT로 정의
--
-- ※ v11 변경 사항 (DB명세서 v25 반영 — 정산 워크플로우 재정비)
--
--   [v25 변경]
--   - platform_settlements 테이블 신규 추가
--     · 대행사→플랫폼 정산(수수료 납부)을 "월 단위 집계 1건"으로 관리하는 배치성 테이블
--     · settlements(기사·대행사 정산)는 A/S 신청 1건당 1행(1:1)이지만,
--       대행사→플랫폼 정산은 애초에 "해당 대행사의 지난 달 platform_fee 합계"라는
--       월 단위 집계 그 자체이므로 별도 배치 테이블로 분리 (agency_id, settlement_year, settlement_month 당 1행)
--     · settlements.platform_settlement_id(NULL 허용 FK)로 개별 정산 건이 어느 월별 플랫폼 정산에
--       집계되었는지 역추적 가능 (정산 완료 후 배치 Job이 채움)
--   - settlements.status ENUM에서 'APPROVED' 제거
--     · ENUM('PENDING','APPROVED','PAID','DISPUTED') → ENUM('PENDING','PAID','DISPUTED') 정산 워크플로우 재검토 결과,
--       "월초 일괄 승인" 단계가 실제로는 상태 전이 없이 바로 지급(PAID)으로 이어지는 구조라
--       APPROVED가 별도 상태로 존재할 실익이 없어 삭제. 오류 발생 시에는 기존과 동일하게 DISPUTED로 전이.
--   - settlements.approved_at 컬럼 삭제
--     · APPROVED 상태 제거에 따라 대응 컬럼도 함께 삭제 (더 이상 어떤 시점도 기록하지 않는 죽은 컬럼이 되므로)
--   - platform_settlements.status도 동일하게 ENUM('PENDING','PAID','DISPUTED')로 설계 (APPROVED 없음)
--
-- ※ v10 변경 사항 (DB명세서 v24 반영, 아래는 과거 이력)
--
--   [v24 변경]
--   - quiz_questions.quiz_year (YEAR NOT NULL) 컬럼 신규 추가
--     · 연도별 문항 독립 관리 (12월 사전 등록 → 1월 1일 활성화)
--     · uk_quiz_tier_order: (category_id, required_level, sort_order)
--                        → (category_id, required_level, quiz_year, sort_order) 로 변경
--     · idx_quiz_year (quiz_year, is_active) 인덱스 신규 추가 (QuizYearRolloverJob 사용)
--   - quiz_attempts.quiz_year (YEAR NOT NULL) 컬럼 신규 추가
--     · 응시 연도 스냅샷 — 전년도·금년도 응시 이력 구분 및 사이클 집계 기준
--     · idx_quiz_attempt_user에 quiz_year 포함
--     · idx_quiz_year (quiz_year, user_id) 인덱스 신규 추가
--   - notifications.type ENUM에 'QUIZ_REMINDER' 추가
--     · QuizYearRolloverJob이 신년도 문항 미등록 계층 있을 시 ADMIN에게 발송
--
--   [v23 변경 — DDL v9에서 이미 반영됨]
--   - agencies.representative_user_id: NOT NULL → NULL 허용
--   - lms_confirmations.is_active (TINYINT(1) NOT NULL DEFAULT 1) 추가
--   - quiz_questions 테이블 신규 추가
--   - quiz_attempts 테이블 신규 추가
--
--   [v22 변경 — DDL v9에서 이미 반영됨]
--   - lms_contents.video_url (VARCHAR(500) NULL) 추가
--   - bank_accounts 테이블 신규 추가
--
-- ※ 유일한 예외: users ↔ agencies 순환참조
--   agencies.representative_user_id → users (users가 먼저 생성되어야 함)
--   users.agency_id                 → agencies (agencies가 먼저 생성되어야 함)
--   → agencies 먼저 CREATE (representative_user_id FK 제외)
--   → users CREATE (agency_id FK 포함)
--   → 파일 맨 마지막에 ALTER 1개로 agencies FK 후처리
--
-- ※ 초기 데이터 INSERT 시 순환참조로 인해
--   SET FOREIGN_KEY_CHECKS = 0; 후 삽입, 완료 후 SET FOREIGN_KEY_CHECKS = 1;
-- ============================================================

DROP DATABASE IF EXISTS careflow;
CREATE DATABASE careflow DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE careflow;

-- ============================================================
-- 1. regions
-- ============================================================
CREATE TABLE `regions` (
    `region_id`  INT UNSIGNED NOT NULL PRIMARY KEY AUTO_INCREMENT COMMENT '지역 ID',
    `parent_id`  INT UNSIGNED NULL     DEFAULT NULL              COMMENT '상위 지역 ID (NULL=시·도 단위, 자기참조)',
    `name`       VARCHAR(50)  NOT NULL                           COMMENT '지역명 (서울특별시 / 강남구 등)',
    `depth`      TINYINT      NOT NULL DEFAULT 1                 COMMENT '계층 깊이 (1=시·도, 2=구·시)',
    `sort_order` INT          NOT NULL DEFAULT 0                 COMMENT '정렬 순서',

    CONSTRAINT FK_regions_self
        FOREIGN KEY (`parent_id`) REFERENCES `regions` (`region_id`),

    INDEX idx_region_parent (parent_id)
);

-- ============================================================
-- 2. appliance_categories
-- ============================================================
CREATE TABLE `appliance_categories` (
    `category_id` INT UNSIGNED NOT NULL PRIMARY KEY AUTO_INCREMENT COMMENT '카테고리 고유 ID',
    `parent_id`   INT UNSIGNED NULL     DEFAULT NULL              COMMENT '상위 카테고리 ID (NULL=대분류, 자기참조)',
    `name`        VARCHAR(50)  NOT NULL                           COMMENT '카테고리명',
    `depth`       TINYINT      NOT NULL DEFAULT 1                 COMMENT '계층 깊이 (1=대분류, 2=소분류)',
    `sort_order`  INT          NOT NULL DEFAULT 0                 COMMENT '정렬 순서',

    CONSTRAINT FK_appliance_categories_self
        FOREIGN KEY (`parent_id`) REFERENCES `appliance_categories` (`category_id`),

    UNIQUE uk_category_name   (name),
    INDEX  idx_category_parent (parent_id)
);

-- ============================================================
-- 3. agencies
-- ※ representative_user_id FK는 users 생성 후 파일 맨 끝 ALTER로 처리
-- ============================================================
CREATE TABLE `agencies` (
    `agency_id`              BIGINT UNSIGNED NOT NULL PRIMARY KEY AUTO_INCREMENT COMMENT '대행사 고유 ID',
    `representative_user_id` BIGINT UNSIGNED NULL     DEFAULT NULL               COMMENT '대표 슈퍼계정 user_id (1:1 UNIQUE) — FK는 파일 하단 ALTER로 추가',
    `name`                   VARCHAR(100)    NOT NULL                            COMMENT '대행사 상호명',
    `business_number`        VARCHAR(20)     NOT NULL                            COMMENT '사업자등록번호',
    `address`                VARCHAR(255)    NULL     DEFAULT NULL               COMMENT '소재지 주소',
    `agency_fee_rate`        DECIMAL(5,2)    NOT NULL DEFAULT 0.00               COMMENT '대행사 기사 수수료율(%) — settlements 산정 기준',
    `approval_status`        ENUM('PENDING','APPROVED','REJECTED') NOT NULL DEFAULT 'PENDING' COMMENT '승인 상태',
    `approved_at`            DATETIME        NULL     DEFAULT NULL               COMMENT '승인 처리 일시',
    `approved_by`            BIGINT UNSIGNED NULL     DEFAULT NULL               COMMENT '승인한 관리자 user_id',
    `created_at`             DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '등록일',
    `updated_at`             DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일',

    UNIQUE uk_agencies_biz_no  (business_number),
    UNIQUE uk_agencies_rep_user (representative_user_id),
    INDEX  idx_agencies_status  (approval_status)
);

-- ============================================================
-- 4. users
-- ============================================================
CREATE TABLE `users` (
    `user_id`        BIGINT UNSIGNED NOT NULL PRIMARY KEY AUTO_INCREMENT COMMENT '사용자 고유 ID',
    `agency_id`      BIGINT UNSIGNED NULL     DEFAULT NULL               COMMENT '소속 대행사 ID (ENGINEER·AGENCY 역할 사용)',
    `email`          VARCHAR(100)    NOT NULL                            COMMENT '로그인 이메일',
    `password_hash`  VARCHAR(255)    NULL     DEFAULT NULL               COMMENT '비밀번호 해시 (소셜 로그인 시 NULL)',
    `name`           VARCHAR(50)     NOT NULL                            COMMENT '이름',
    `phone`          VARCHAR(20)     NULL     DEFAULT NULL               COMMENT '연락처',
    `role`           ENUM('CUSTOMER','ENGINEER','AGENCY','ADMIN') NOT NULL DEFAULT 'CUSTOMER' COMMENT '역할 구분',
    `region_id`      INT UNSIGNED    NULL     DEFAULT NULL               COMMENT '거주 지역 ID (regions depth=2). CUSTOMER·ENGINEER 입력. AGENCY·ADMIN은 NULL.',
    `address_detail` VARCHAR(100)    NULL     DEFAULT NULL               COMMENT '상세 주소 (동·호수 등). CUSTOMER·ENGINEER 입력. AGENCY·ADMIN은 NULL.',
    `status`         ENUM('ACTIVE','INACTIVE','SUSPENDED') NOT NULL DEFAULT 'ACTIVE' COMMENT '계정 상태',
    `last_login_at`  DATETIME        NULL     DEFAULT NULL               COMMENT '최근 로그인 일시',
    `created_at`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '가입일',
    `updated_at`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일',
    `deleted_at`     DATETIME        NULL     DEFAULT NULL               COMMENT '논리 삭제일',

    CONSTRAINT FK_agencies_TO_users
        FOREIGN KEY (`agency_id`) REFERENCES `agencies` (`agency_id`),
    CONSTRAINT FK_regions_TO_users
        FOREIGN KEY (`region_id`) REFERENCES `regions` (`region_id`),

    UNIQUE uk_users_email   (email),
    INDEX  idx_users_role   (role),
    INDEX  idx_users_agency (agency_id),
    INDEX  idx_users_status (status),
    INDEX  idx_users_region (region_id)
);

-- ============================================================
-- 5. account_requests
-- ============================================================
CREATE TABLE `account_requests` (
    `account_request_id` BIGINT UNSIGNED NOT NULL PRIMARY KEY AUTO_INCREMENT COMMENT '요청 ID',
    `agency_id`          BIGINT UNSIGNED NOT NULL                            COMMENT '가입 신청 대상 대행사 ID',
    `requested_role`     ENUM('AGENCY','ENGINEER') NOT NULL                  COMMENT '신청 역할 구분 — AGENCY=담당자, ENGINEER=기사',
    `email`              VARCHAR(100)    NOT NULL                            COMMENT '신청자 이메일 → APPROVED 시 users.email로 이관',
    `password_hash`      VARCHAR(255)    NOT NULL                            COMMENT '비밀번호 해시 → APPROVED 시 users.password_hash로 이관',
    `name`               VARCHAR(50)     NOT NULL                            COMMENT '신청자 이름 → APPROVED 시 users.name으로 이관',
    `phone`              VARCHAR(20)     NULL     DEFAULT NULL               COMMENT '연락처 → APPROVED 시 users.phone으로 이관',
    `region_id`          INT UNSIGNED    NULL     DEFAULT NULL               COMMENT '거주 지역 ID (regions depth=2) → APPROVED 시 users.region_id로 이관',
    `address_detail`     VARCHAR(100)    NULL     DEFAULT NULL               COMMENT '상세 주소 → APPROVED 시 users.address_detail로 이관',
    `status`             ENUM('PENDING','APPROVED','REJECTED') NOT NULL DEFAULT 'PENDING' COMMENT '처리 상태',
    `reviewed_by`        BIGINT UNSIGNED NULL                                COMMENT '처리한 슈퍼계정 user_id (agencies.representative_user_id)',
    `reviewed_at`        DATETIME        NULL     DEFAULT NULL               COMMENT '처리 일시',
    `reject_reason`      VARCHAR(255)    NULL     DEFAULT NULL               COMMENT '거절 사유 (REJECTED 시 입력)',
    `created_user_id`    BIGINT UNSIGNED NULL     DEFAULT NULL               COMMENT 'APPROVED 후 생성된 users.user_id',
    `created_at`         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '신청일',
    `updated_at`         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일',

    CONSTRAINT FK_agencies_TO_account_requests
        FOREIGN KEY (`agency_id`) REFERENCES `agencies` (`agency_id`),
    CONSTRAINT FK_regions_TO_account_requests
        FOREIGN KEY (`region_id`) REFERENCES `regions` (`region_id`),
    CONSTRAINT FK_users_TO_account_requests_reviewed_by
        FOREIGN KEY (`reviewed_by`) REFERENCES `users` (`user_id`),
    CONSTRAINT FK_users_TO_account_requests_created_user
        FOREIGN KEY (`created_user_id`) REFERENCES `users` (`user_id`),

    INDEX idx_acc_req_agency_status (agency_id, status),
    INDEX idx_acc_req_role_status   (requested_role, status),
    INDEX idx_acc_req_email         (email)
);

-- ============================================================
-- 6. engineer_profiles
-- ============================================================
CREATE TABLE `engineer_profiles` (
    `profile_id`          BIGINT UNSIGNED NOT NULL PRIMARY KEY AUTO_INCREMENT COMMENT '프로필 ID',
    `user_id`             BIGINT UNSIGNED NOT NULL                            COMMENT '기사 user_id (users.user_id 참조, 1:1)',
    `category_id`         INT UNSIGNED    NULL                                COMMENT '전문 가전 카테고리 ID (appliance_categories depth=2 소분류만). 기사당 1개. 첫 로그인 전 NULL.',
    `career_started_year` YEAR            NULL                                COMMENT '경력 시작 연도 — skill_level 산정 기준',
    `skill_level`         ENUM('BEGINNER','INTERMEDIATE','ADVANCED') NOT NULL DEFAULT 'BEGINNER' COMMENT '기술 등급 (career_started_year 기반 자동 산정)',
    `is_lms_completed`    TINYINT(1)      NOT NULL DEFAULT 0                  COMMENT '당해 연도 필수 교육 이수 여부 (0=미이수, 1=이수) — 배차 조건',
    `introduction`        TEXT            NULL     DEFAULT NULL               COMMENT '기사 자기소개',
    `profile_image_url`   VARCHAR(500)    NULL     DEFAULT NULL               COMMENT '프로필 사진 URL',
    `avg_rating`          DECIMAL(3,2)    NOT NULL DEFAULT 0.00               COMMENT '평균 평점 (역정규화 — reviews 집계)',
    `total_reviews`       INT UNSIGNED    NOT NULL DEFAULT 0                  COMMENT '총 리뷰 수 (역정규화)',
    `created_at`          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '등록일',
    `updated_at`          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일',

    CONSTRAINT FK_users_TO_engineer_profiles
        FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`),
    CONSTRAINT FK_appliance_categories_TO_engineer_profiles
        FOREIGN KEY (`category_id`) REFERENCES `appliance_categories` (`category_id`),

    UNIQUE uk_engineer_profiles_user (user_id),
    INDEX  idx_engineer_category     (category_id),
    INDEX  idx_engineer_skill_lms    (skill_level, is_lms_completed),
    INDEX  idx_engineer_avg_rating   (avg_rating)
);

-- ============================================================
-- 7. engineer_service_regions
-- ============================================================
CREATE TABLE `engineer_service_regions` (
    `id`          BIGINT UNSIGNED NOT NULL PRIMARY KEY AUTO_INCREMENT COMMENT '매핑 ID',
    `engineer_id` BIGINT UNSIGNED NOT NULL                            COMMENT '기사 user_id (users.user_id 참조)',
    `region_id`   INT UNSIGNED    NOT NULL                            COMMENT '지역 ID (regions depth=2 구 단위)',

    CONSTRAINT FK_users_TO_engineer_service_regions
        FOREIGN KEY (`engineer_id`) REFERENCES `users` (`user_id`),
    CONSTRAINT FK_regions_TO_engineer_service_regions
        FOREIGN KEY (`region_id`) REFERENCES `regions` (`region_id`),

    UNIQUE uk_eng_region      (engineer_id, region_id),
    INDEX  idx_eng_region_reg (region_id)
);

-- ============================================================
-- 8. engineer_expert_brands
-- ============================================================
CREATE TABLE `engineer_expert_brands` (
    `id`          BIGINT UNSIGNED NOT NULL PRIMARY KEY AUTO_INCREMENT COMMENT 'ID',
    `engineer_id` BIGINT UNSIGNED NOT NULL                            COMMENT '기사 user_id (users.user_id 참조)',
    `brand_name`  VARCHAR(50)     NOT NULL                            COMMENT '브랜드명 (삼성·LG·위니아 등)',

    CONSTRAINT FK_users_TO_engineer_expert_brands
        FOREIGN KEY (`engineer_id`) REFERENCES `users` (`user_id`),

    UNIQUE uk_eng_brand       (engineer_id, brand_name),
    INDEX  idx_eng_brand_name (brand_name)
);

-- ============================================================
-- 9. engineer_schedules
-- ============================================================
CREATE TABLE `engineer_schedules` (
    `schedule_id`  BIGINT UNSIGNED NOT NULL PRIMARY KEY AUTO_INCREMENT COMMENT '근무표 ID',
    `user_id`      BIGINT UNSIGNED NOT NULL                            COMMENT '기사 user_id',
    `work_date`    DATE            NOT NULL                            COMMENT '근무 가능 날짜',
    `status`       ENUM('AVAILABLE','BOOKED','OFF') NOT NULL DEFAULT 'AVAILABLE' COMMENT '근무 상태 (BOOKED=배차 완료)',
    `submitted_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '제출 일시',
    `updated_at`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일',

    CONSTRAINT FK_users_TO_engineer_schedules
        FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`),

    UNIQUE uk_eng_schedule          (user_id, work_date),
    INDEX  idx_schedule_date_status (work_date, status),
    INDEX  idx_schedule_user_date   (user_id, work_date, status)
);

-- ============================================================
-- 10. engineer_schedule_slots
-- ============================================================
CREATE TABLE `engineer_schedule_slots` (
    `slot_id`     BIGINT UNSIGNED NOT NULL PRIMARY KEY AUTO_INCREMENT COMMENT '슬롯 ID',
    `schedule_id` BIGINT UNSIGNED NOT NULL                            COMMENT 'engineer_schedules.schedule_id 참조',
    `start_time`  TIME            NOT NULL                            COMMENT '근무 시작 시각',
    `end_time`    TIME            NOT NULL                            COMMENT '근무 종료 시각',

    CONSTRAINT FK_engineer_schedules_TO_slots
        FOREIGN KEY (`schedule_id`) REFERENCES `engineer_schedules` (`schedule_id`),

    INDEX idx_slot_time (schedule_id, start_time, end_time)
);

-- ============================================================
-- 11. appliance_categories (already defined above as #2)
-- ============================================================

-- ============================================================
-- 12. appliances
-- ============================================================
CREATE TABLE `appliances` (
    `appliance_id`      BIGINT UNSIGNED NOT NULL PRIMARY KEY AUTO_INCREMENT COMMENT '가전 ID',
    `user_id`           BIGINT UNSIGNED NOT NULL                            COMMENT '소유자 user_id (CUSTOMER)',
    `category_id`       INT UNSIGNED    NOT NULL                            COMMENT '가전 카테고리 ID (appliance_categories depth=2)',
    `brand`             VARCHAR(50)     NOT NULL                            COMMENT '브랜드명',
    `model_name`        VARCHAR(100)    NOT NULL                            COMMENT '모델명',
    `serial_number`     VARCHAR(100)    NULL     DEFAULT NULL               COMMENT '시리얼 번호',
    `purchase_date`     DATE            NULL     DEFAULT NULL               COMMENT '구매일',
    `warranty_end_date` DATE            NULL     DEFAULT NULL               COMMENT '무상 A/S 만료일',
    `register_method`   ENUM('MANUAL','OCR') NOT NULL DEFAULT 'MANUAL'     COMMENT '등록 방식',
    `image_url`         VARCHAR(500)    NULL     DEFAULT NULL               COMMENT '가전 사진 URL',
    `status`            ENUM('NORMAL','NEED_REPAIR','SOLD') NOT NULL DEFAULT 'NORMAL' COMMENT '가전 상태',
    `created_at`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '등록일',
    `updated_at`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일',
    `deleted_at`        DATETIME        NULL     DEFAULT NULL               COMMENT '논리 삭제일',

    CONSTRAINT FK_users_TO_appliances
        FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`),
    CONSTRAINT FK_appliance_categories_TO_appliances
        FOREIGN KEY (`category_id`) REFERENCES `appliance_categories` (`category_id`),

    INDEX idx_appliances_user         (user_id, status),
    INDEX idx_appliances_warranty     (warranty_end_date, status),
    INDEX idx_appliances_brand_model  (brand, model_name),
    INDEX idx_appliances_serial       (serial_number)
);

-- ============================================================
-- 13. consumable_alerts
-- ============================================================
CREATE TABLE `consumable_alerts` (
    `alert_id`        BIGINT UNSIGNED  NOT NULL PRIMARY KEY AUTO_INCREMENT COMMENT '소모품 알림 ID',
    `appliance_id`    BIGINT UNSIGNED  NOT NULL                            COMMENT '해당 가전 ID',
    `user_id`         BIGINT UNSIGNED  NOT NULL                            COMMENT '소유 고객 user_id',
    `consumable_name` VARCHAR(100)     NOT NULL                            COMMENT '소모품 항목명 (필터, 물통 등)',
    `cycle_months`    TINYINT UNSIGNED NOT NULL                            COMMENT '교체 주기 (개월)',
    `last_changed_at` DATE             NULL     DEFAULT NULL               COMMENT '마지막 교체일 (NULL=교체 이력 없음)',
    `next_alert_date` DATE             NOT NULL                            COMMENT '다음 알림 예정일',
    `is_active`       TINYINT(1)       NOT NULL DEFAULT 1                  COMMENT '알림 활성화 여부',
    `created_at`      DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '등록일',

    CONSTRAINT FK_appliances_TO_consumable_alerts
        FOREIGN KEY (`appliance_id`) REFERENCES `appliances` (`appliance_id`),
    CONSTRAINT FK_users_TO_consumable_alerts
        FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`),

    INDEX idx_consumable_alert_date (next_alert_date, is_active),
    INDEX idx_consumable_user       (user_id)
);

-- ============================================================
-- 14. symptoms
-- ============================================================
CREATE TABLE `symptoms` (
    `symptom_id`   BIGINT UNSIGNED NOT NULL PRIMARY KEY AUTO_INCREMENT COMMENT '증상 마스터 ID',
    `category_id`  INT UNSIGNED    NOT NULL                            COMMENT '가전 카테고리 ID (appliance_categories depth=2)',
    `symptom_code` VARCHAR(50)     NOT NULL                            COMMENT '증상 코드 (예: COOLING_FAIL)',
    `symptom_name` VARCHAR(100)    NOT NULL                            COMMENT '증상명 (화면 표시용 한글명)',
    `is_active`    TINYINT(1)      NOT NULL DEFAULT 1                  COMMENT '노출 여부 (0=비활성화)',
    `created_at`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '등록일',
    `updated_at`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일',

    CONSTRAINT FK_appliance_categories_TO_symptoms
        FOREIGN KEY (`category_id`) REFERENCES `appliance_categories` (`category_id`),

    UNIQUE uk_symptom_cat_code (category_id, symptom_code),
    INDEX  idx_symptom_category (category_id, is_active),
    INDEX  idx_symptom_active   (is_active)
);

-- ============================================================
-- 15. expected_repair_costs
-- ============================================================
CREATE TABLE `expected_repair_costs` (
    `repair_cost_id`     BIGINT UNSIGNED NOT NULL PRIMARY KEY AUTO_INCREMENT COMMENT '예상 비용 ID',
    `category_id`        INT UNSIGNED    NOT NULL                            COMMENT '가전 카테고리 ID',
    `symptom_id`         BIGINT UNSIGNED NOT NULL                            COMMENT '증상 ID (symptoms.symptom_id 참조)',
    `avg_cost`           INT UNSIGNED    NULL     DEFAULT NULL               COMMENT '평균 수리 비용 (원) — Quartz 배치 자동 집계. 미집계 시 NULL',
    `sample_count`       INT UNSIGNED    NULL     DEFAULT 0                  COMMENT '집계 기준 건수',
    `last_calculated_at` DATETIME        NULL     DEFAULT NULL               COMMENT '마지막 집계 시각',
    `created_at`         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '생성일',
    `updated_at`         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '최근 갱신일',

    CONSTRAINT FK_appliance_categories_TO_expected_repair_costs
        FOREIGN KEY (`category_id`) REFERENCES `appliance_categories` (`category_id`),
    CONSTRAINT FK_symptoms_TO_expected_repair_costs
        FOREIGN KEY (`symptom_id`) REFERENCES `symptoms` (`symptom_id`),

    UNIQUE uk_erc_symptom         (symptom_id),
    INDEX  idx_repair_cost_lookup (symptom_id),
    INDEX  idx_repair_cost_calc   (last_calculated_at)
);

-- ============================================================
-- 16. as_requests
-- ============================================================
CREATE TABLE `as_requests` (
    `request_id`           BIGINT UNSIGNED NOT NULL PRIMARY KEY AUTO_INCREMENT COMMENT 'A/S 신청 ID',
    `customer_id`          BIGINT UNSIGNED NOT NULL                            COMMENT '신청 고객 user_id',
    `appliance_id`         BIGINT UNSIGNED NOT NULL                            COMMENT '수리 대상 가전 ID',
    `agency_id`            BIGINT UNSIGNED NULL     DEFAULT NULL               COMMENT '접수 대행사 ID',
    `preferred_engineer_id` BIGINT UNSIGNED NULL    DEFAULT NULL               COMMENT '고객 선호 기사 user_id (수동 배정 시)',
    `symptom_id`           BIGINT UNSIGNED NOT NULL                            COMMENT '증상 ID (symptoms.symptom_id 참조). 카테고리별 드롭다운 선택.',
    `symptom_desc`         TEXT            NULL     DEFAULT NULL               COMMENT '증상 상세 설명',
    `image_urls`           JSON            NULL     DEFAULT NULL               COMMENT '첨부 사진 URL 목록',
    `assign_type`          ENUM('AUTO','MANUAL') NOT NULL DEFAULT 'MANUAL'     COMMENT '배정 방식',
    `visit_region_id`      INT UNSIGNED    NOT NULL                            COMMENT '방문 지역 ID (regions depth=2). users.region_id 기본값 자동 설정. 자동 배정 필터링 기준.',
    `visit_address_detail` VARCHAR(100)    NOT NULL                            COMMENT '방문 상세 주소 (동·호수·번지 등) — 기사 방문을 위해 필수 입력',
    `scheduled_date`       DATE            NOT NULL                            COMMENT '방문 예약 날짜',
    `scheduled_time`       VARCHAR(10)     NOT NULL                            COMMENT '방문 예약 시간 (HH:MM)',
    `status`               ENUM('PENDING','AGENCY_RECEIVED','ASSIGNED','ACCEPTED','IN_PROGRESS','COMPLETED','PAID','CANCELLED') NOT NULL DEFAULT 'PENDING' COMMENT 'A/S 진행 상태',
    `cancel_reason`        VARCHAR(255)    NULL     DEFAULT NULL               COMMENT '취소 사유',
    `created_at`           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '신청일',
    `updated_at`           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일',

    CONSTRAINT FK_users_TO_as_requests
        FOREIGN KEY (`customer_id`) REFERENCES `users` (`user_id`),
    CONSTRAINT FK_appliances_TO_as_requests
        FOREIGN KEY (`appliance_id`) REFERENCES `appliances` (`appliance_id`),
    CONSTRAINT FK_agencies_TO_as_requests
        FOREIGN KEY (`agency_id`) REFERENCES `agencies` (`agency_id`),
    CONSTRAINT FK_users_TO_as_requests_preferred_engineer
        FOREIGN KEY (`preferred_engineer_id`) REFERENCES `users` (`user_id`),
    CONSTRAINT FK_symptoms_TO_as_requests
        FOREIGN KEY (`symptom_id`) REFERENCES `symptoms` (`symptom_id`),
    CONSTRAINT FK_regions_TO_as_requests
        FOREIGN KEY (`visit_region_id`) REFERENCES `regions` (`region_id`),

    INDEX idx_as_req_customer       (customer_id, status, created_at),
    INDEX idx_as_req_agency         (agency_id, status),
    INDEX idx_as_req_status_date    (status, scheduled_date),
    INDEX idx_as_req_pref_engineer  (preferred_engineer_id),
    INDEX idx_as_req_visit_region   (visit_region_id, status)
);

-- ============================================================
-- 17. as_assignments
-- ============================================================
CREATE TABLE `as_assignments` (
    `assignment_id` BIGINT UNSIGNED NOT NULL PRIMARY KEY AUTO_INCREMENT COMMENT '배정 ID',
    `request_id`    BIGINT UNSIGNED NOT NULL                            COMMENT 'A/S 신청 ID',
    `engineer_id`   BIGINT UNSIGNED NOT NULL                            COMMENT '배정된 기사 user_id',
    `agency_id`     BIGINT UNSIGNED NOT NULL                            COMMENT '배차 대행사 ID',
    `assign_method` ENUM('AUTO','MANUAL') NOT NULL DEFAULT 'MANUAL'    COMMENT '배정 방식',
    `status`        ENUM('WAITING','ACCEPTED','REJECTED','COMPLETED') NOT NULL DEFAULT 'WAITING' COMMENT '배정 상태',
    `accepted_at`   DATETIME        NULL     DEFAULT NULL               COMMENT '기사 수락 일시',
    `rejected_at`   DATETIME        NULL     DEFAULT NULL               COMMENT '거절 일시',
    `reject_reason` VARCHAR(255)    NULL     DEFAULT NULL               COMMENT '거절 사유',
    `assigned_at`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '배정 일시',

    CONSTRAINT FK_as_requests_TO_as_assignments
        FOREIGN KEY (`request_id`) REFERENCES `as_requests` (`request_id`),
    CONSTRAINT FK_users_TO_as_assignments
        FOREIGN KEY (`engineer_id`) REFERENCES `users` (`user_id`),
    CONSTRAINT FK_agencies_TO_as_assignments
        FOREIGN KEY (`agency_id`) REFERENCES `agencies` (`agency_id`),

    INDEX idx_assignment_request  (request_id, status),
    INDEX idx_assignment_engineer (engineer_id, status),
    INDEX idx_assignment_agency   (agency_id, status)
);

-- ============================================================
-- 18. as_status_logs
-- ============================================================
CREATE TABLE `as_status_logs` (
    `log_id`      BIGINT UNSIGNED NOT NULL PRIMARY KEY AUTO_INCREMENT COMMENT '로그 ID',
    `request_id`  BIGINT UNSIGNED NOT NULL                            COMMENT 'A/S 신청 ID',
    `changed_by`  BIGINT UNSIGNED NOT NULL                            COMMENT '상태 변경 주체 user_id',
    `from_status` ENUM('WAITING','ENGINEER_DEPARTED','ENGINEER_ARRIVED','IN_PROGRESS','COMPLETED') NULL DEFAULT NULL COMMENT '변경 전 상태',
    `to_status`   ENUM('WAITING','ENGINEER_DEPARTED','ENGINEER_ARRIVED','IN_PROGRESS','COMPLETED') NOT NULL DEFAULT 'WAITING' COMMENT '변경 후 상태',
    `memo`        VARCHAR(255)    NULL     DEFAULT NULL               COMMENT '변경 메모',
    `created_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '변경 일시',

    CONSTRAINT FK_as_requests_TO_as_status_logs
        FOREIGN KEY (`request_id`) REFERENCES `as_requests` (`request_id`),
    CONSTRAINT FK_users_TO_as_status_logs
        FOREIGN KEY (`changed_by`) REFERENCES `users` (`user_id`),

    INDEX idx_status_log_request (request_id, created_at)
);

-- ============================================================
-- 19. repair_parts
-- ============================================================
CREATE TABLE `repair_parts` (
    `repair_part_id`  BIGINT UNSIGNED NOT NULL PRIMARY KEY AUTO_INCREMENT COMMENT '부품 마스터 ID',
    `part_code`       VARCHAR(50)     NOT NULL                            COMMENT '부품 코드 (예: COMP-AC-R410A-1HP)',
    `part_name`       VARCHAR(100)    NOT NULL                            COMMENT '부품명',
    `spec`            VARCHAR(200)    NULL     DEFAULT NULL               COMMENT '부품 규격·사양',
    `importance`      ENUM('CRITICAL','MAJOR','NORMAL','MINOR') NOT NULL DEFAULT 'NORMAL' COMMENT '부품 중요도 — 진단서 등급 산정 기준',
    `base_unit_price` INT UNSIGNED    NOT NULL                            COMMENT '기준 단가 (원)',
    `is_active`       TINYINT(1)      NOT NULL DEFAULT 1                  COMMENT '활성 여부 (0=단종·사용중단)',
    `created_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '등록일',
    `updated_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일',

    UNIQUE uk_repair_parts_code      (part_code),
    INDEX  idx_repair_parts_importance (importance),
    INDEX  idx_repair_parts_active   (is_active)
);

-- ============================================================
-- 20. work_reports
-- ============================================================
CREATE TABLE `work_reports` (
    `report_id`        BIGINT UNSIGNED NOT NULL PRIMARY KEY AUTO_INCREMENT COMMENT '작업 보고서 ID',
    `request_id`       BIGINT UNSIGNED NOT NULL                            COMMENT 'A/S 신청 ID (1:1)',
    `engineer_id`      BIGINT UNSIGNED NOT NULL                            COMMENT '작성 기사 user_id',
    `diagnosis_result` ENUM('NORMAL','REPAIRED','PART_REPLACED','UNREPAIRABLE') NOT NULL COMMENT '진단 결과 — 필수',
    `work_duration_min` SMALLINT UNSIGNED NOT NULL                         COMMENT '실제 작업 시간 (분) — 필수',
    `final_amount`     INT UNSIGNED    NOT NULL                            COMMENT '최종 청구 금액 (원) — 필수',
    `memo`             TEXT            NULL     DEFAULT NULL               COMMENT '작업 메모',
    `image_urls`       JSON            NULL     DEFAULT NULL               COMMENT '작업 사진 URL 목록',
    `customer_approved` TINYINT(1)     NOT NULL DEFAULT 0                  COMMENT '고객 승인 여부 (1=승인)',
    `approved_at`      DATETIME        NULL     DEFAULT NULL               COMMENT '고객 승인 일시',
    `submitted_at`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '보고서 제출 일시',
    `updated_at`       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일',

    CONSTRAINT FK_as_requests_TO_work_reports
        FOREIGN KEY (`request_id`) REFERENCES `as_requests` (`request_id`),
    CONSTRAINT FK_users_TO_work_reports
        FOREIGN KEY (`engineer_id`) REFERENCES `users` (`user_id`),

    UNIQUE uk_work_report_request     (request_id),
    INDEX  idx_work_report_engineer   (engineer_id, submitted_at),
    INDEX  idx_work_report_approval   (customer_approved, submitted_at)
);

-- ============================================================
-- 21. work_report_parts
-- ============================================================
CREATE TABLE `work_report_parts` (
    `part_id`           BIGINT UNSIGNED  NOT NULL PRIMARY KEY AUTO_INCREMENT COMMENT 'ID',
    `report_id`         BIGINT UNSIGNED  NOT NULL                            COMMENT '작업 보고서 ID',
    `repair_part_id`    BIGINT UNSIGNED  NOT NULL                            COMMENT '부품 ID',
    `quantity`          TINYINT UNSIGNED NOT NULL DEFAULT 1                  COMMENT '수량',
    `applied_unit_price` INT UNSIGNED    NULL     DEFAULT NULL               COMMENT '보고서 적용 단가 스냅샷(원) — repair_parts.base_unit_price 기준으로 기사 입력. 단가 변경 후에도 과거 보고서 금액 보존.',
    `created_at`        DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '등록일',

    CONSTRAINT FK_work_reports_TO_work_report_parts
        FOREIGN KEY (`report_id`) REFERENCES `work_reports` (`report_id`),
    CONSTRAINT FK_repair_parts_TO_work_report_parts
        FOREIGN KEY (`repair_part_id`) REFERENCES `repair_parts` (`repair_part_id`),

    INDEX idx_parts_report      (report_id),
    INDEX idx_parts_repair_part (repair_part_id)
);

-- ============================================================
-- 22. health_certificates
-- ============================================================
CREATE TABLE `health_certificates` (
    `cert_id`                BIGINT UNSIGNED  NOT NULL PRIMARY KEY AUTO_INCREMENT COMMENT '진단서 ID',
    `appliance_id`           BIGINT UNSIGNED  NOT NULL                            COMMENT '가전 ID (1:1)',
    `grade`                  CHAR(1)          NOT NULL DEFAULT 'E'               COMMENT '등급 A~E (DEFAULT E — 최초 생성 시 최저 등급)',
    `score`                  TINYINT UNSIGNED NOT NULL DEFAULT 0                  COMMENT '환산 점수 (0~100)',
    `repair_count`           TINYINT UNSIGNED NOT NULL DEFAULT 0                  COMMENT '누적 수리 횟수',
    `critical_parts_replaced` TINYINT UNSIGNED NOT NULL DEFAULT 0                 COMMENT '핵심 부품 교체 횟수',
    `last_repaired_at`       DATETIME         NULL     DEFAULT NULL               COMMENT '최근 수리 완료 일시',
    `is_certified`           TINYINT(1)       NOT NULL DEFAULT 0                  COMMENT 'CareFlow 인증 여부 (B등급↑, 75점↑ 자동 부여)',
    `issued_at`              DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '최초 발급일',
    `updated_at`             DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '최근 갱신일',

    CONSTRAINT FK_appliances_TO_health_certificates
        FOREIGN KEY (`appliance_id`) REFERENCES `appliances` (`appliance_id`),

    UNIQUE uk_cert_appliance (appliance_id),
    INDEX  idx_cert_grade    (grade, is_certified)
);

-- ============================================================
-- 23. reviews
-- ============================================================
CREATE TABLE `reviews` (
    `review_id`   BIGINT UNSIGNED  NOT NULL PRIMARY KEY AUTO_INCREMENT COMMENT '리뷰 ID',
    `request_id`  BIGINT UNSIGNED  NOT NULL                            COMMENT 'A/S 신청 ID (1:1)',
    `customer_id` BIGINT UNSIGNED  NOT NULL                            COMMENT '작성 고객 user_id',
    `engineer_id` BIGINT UNSIGNED  NOT NULL                            COMMENT '대상 기사 user_id',
    `rating`      TINYINT UNSIGNED NOT NULL                            COMMENT '별점 (1~5)',
    `content`     TEXT             NULL     DEFAULT NULL               COMMENT '리뷰 본문',
    `is_visible`  TINYINT(1)       NOT NULL DEFAULT 1                  COMMENT '노출 여부',
    `created_at`  DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '작성일',

    CONSTRAINT FK_as_requests_TO_reviews
        FOREIGN KEY (`request_id`) REFERENCES `as_requests` (`request_id`),
    CONSTRAINT FK_users_TO_reviews_customer
        FOREIGN KEY (`customer_id`) REFERENCES `users` (`user_id`),
    CONSTRAINT FK_users_TO_reviews_engineer
        FOREIGN KEY (`engineer_id`) REFERENCES `users` (`user_id`),

    UNIQUE uk_review_request   (request_id),
    INDEX  idx_review_engineer (engineer_id, created_at),
    INDEX  idx_review_customer (customer_id)
);

-- ============================================================
-- 24. payments
-- ============================================================
CREATE TABLE `payments` (
    `payment_id`        BIGINT UNSIGNED NOT NULL PRIMARY KEY AUTO_INCREMENT COMMENT '결제 ID',
    `request_id`        BIGINT UNSIGNED NOT NULL                            COMMENT 'A/S 신청 ID (1:1)',
    `customer_id`       BIGINT UNSIGNED NOT NULL                            COMMENT '결제 고객 user_id',
    `amount`            INT UNSIGNED    NOT NULL                            COMMENT '결제 금액 (원)',
    `pg_provider`       ENUM('MOCK','KAKAO','TOSS','NAVER') NOT NULL DEFAULT 'MOCK' COMMENT 'PG사 코드',
    `pg_transaction_id` VARCHAR(100)    NULL     DEFAULT NULL               COMMENT 'PG사 거래 ID (Mocking 시 NULL)',
    `status`            ENUM('READY','SUCCESS','FAILED','CANCELLED','REFUNDED') NOT NULL DEFAULT 'READY' COMMENT '결제 상태',
    `paid_at`           DATETIME        NULL     DEFAULT NULL               COMMENT '결제 완료 일시',
    `cancelled_at`      DATETIME        NULL     DEFAULT NULL               COMMENT '취소 일시',
    `created_at`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '생성일',

    CONSTRAINT FK_as_requests_TO_payments
        FOREIGN KEY (`request_id`) REFERENCES `as_requests` (`request_id`),
    CONSTRAINT FK_users_TO_payments
        FOREIGN KEY (`customer_id`) REFERENCES `users` (`user_id`),

    UNIQUE uk_payment_request  (request_id),
    UNIQUE uk_payment_pg_txid  (pg_transaction_id),
    INDEX  idx_payment_status  (status, paid_at),
    INDEX  idx_payment_customer (customer_id, created_at)
);

-- ============================================================
-- 25. platform_settlements
-- [v25 신규] 대행사→플랫폼 정산 (수수료 납부) — 월 단위 집계 배치 테이블
--   - settlements(기사·대행사 정산)는 A/S 신청 1건당 1행이지만,
--     대행사→플랫폼 정산은 "해당 대행사의 지난 달 platform_fee 합계"라는
--     월 단위 집계 그 자체이므로 agency_id + settlement_year + settlement_month 당 1행으로 관리
--   - 워크플로우: ① 매월 초 배치 Job이 전월 settlements(해당 agency_id, status='PAID')를 집계하여
--     1행 생성(PENDING) + 대상 settlements.platform_settlement_id를 이 행의 ID로 채움
--     ② 대행사가 플랫폼에 수수료 납부 ③ status를 PAID로 변경
--   - 집계 과정 오류 시 status를 DISPUTED로 전이 (settlements와 동일 패턴)
--   - settlements보다 먼저 생성 (settlements.platform_settlement_id FK가 이 테이블을 참조하므로 선행 필요)
-- ============================================================
CREATE TABLE `platform_settlements` (
    `platform_settlement_id` BIGINT UNSIGNED NOT NULL PRIMARY KEY AUTO_INCREMENT COMMENT '대행사→플랫폼 정산 ID',
    `agency_id`               BIGINT UNSIGNED NOT NULL                            COMMENT '대행사 ID',
    `settlement_year`         YEAR            NOT NULL                            COMMENT '정산 대상 연도 (집계 대상 settlements.paid_at 기준)',
    `settlement_month`        TINYINT UNSIGNED NOT NULL                          COMMENT '정산 대상 월 (1~12)',
    `total_gross_amount`      INT UNSIGNED    NOT NULL                            COMMENT '집계 대상 settlements.gross_amount 합계 (원) — 검증·감사용',
    `total_platform_fee`      INT UNSIGNED    NOT NULL                            COMMENT '집계 대상 settlements.platform_fee 합계 (원) — 대행사가 플랫폼에 납부할 금액',
    `settlement_count`        INT UNSIGNED    NOT NULL DEFAULT 0                  COMMENT '집계된 settlements 건수',
    `status`                  ENUM('PENDING','PAID','DISPUTED') NOT NULL DEFAULT 'PENDING' COMMENT '정산 상태 (APPROVED 없음 — settlements와 동일 사유)',
    `paid_at`                 DATETIME        NULL     DEFAULT NULL               COMMENT '플랫폼 수수료 납부 완료 일시',
    `created_at`              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '집계(배치 생성) 일시',

    CONSTRAINT FK_agencies_TO_platform_settlements
        FOREIGN KEY (`agency_id`) REFERENCES `agencies` (`agency_id`),

    UNIQUE uk_platform_settlement_period (agency_id, settlement_year, settlement_month),
    INDEX  idx_platform_settlement_status (status, settlement_year, settlement_month)
);

-- ============================================================
-- 26. settlements
-- [v25] status ENUM에서 'APPROVED' 제거 → ENUM('PENDING','PAID','DISPUTED')
-- [v25] approved_at 컬럼 삭제 (대응 상태 제거로 죽은 컬럼이 되어 삭제)
-- [v25] platform_settlement_id 컬럼 신규 추가 (NULL 허용 FK)
--   - 이 정산 건의 platform_fee가 어느 월별 대행사→플랫폼 정산(platform_settlements)에
--     집계되었는지 역추적하는 컬럼. 월초 배치 Job이 platform_settlements 1건을 생성하며 채움.
--   - 아직 집계 전(월 마감 전)이거나 DISPUTED 상태인 건은 NULL
-- ============================================================
CREATE TABLE `settlements` (
    `settlement_id`          BIGINT UNSIGNED NOT NULL PRIMARY KEY AUTO_INCREMENT COMMENT '정산 ID',
    `payment_id`             BIGINT UNSIGNED NOT NULL                            COMMENT '결제 ID (1:1)',
    `request_id`             BIGINT UNSIGNED NOT NULL                            COMMENT 'A/S 신청 ID',
    `engineer_id`            BIGINT UNSIGNED NOT NULL                            COMMENT '기사 user_id',
    `agency_id`              BIGINT UNSIGNED NOT NULL                            COMMENT '소속 대행사 ID',
    `gross_amount`           INT UNSIGNED    NOT NULL                            COMMENT '작업 총 금액 (원)',
    `platform_fee`           INT UNSIGNED    NOT NULL                            COMMENT 'CareFlow 수수료 (원)',
    `fee_rate`               DECIMAL(5,2)    NOT NULL                            COMMENT 'CareFlow 수수료율(%) 스냅샷',
    `agency_fee`             INT UNSIGNED    NOT NULL                            COMMENT '대행사 수수료 (원)',
    `agency_fee_rate`        DECIMAL(5,2)    NOT NULL                            COMMENT '대행사 수수료율(%) 스냅샷',
    `engineer_net_amount`    INT UNSIGNED    NOT NULL                            COMMENT '기사 실수령액 (원)',
    `status`                 ENUM('PENDING','PAID','DISPUTED') NOT NULL DEFAULT 'PENDING' COMMENT '[v25] 정산 상태 — APPROVED 제거 (월초 일괄 승인 단계가 상태 전이 없이 바로 지급으로 이어져 실익 없음)',
    `platform_settlement_id` BIGINT UNSIGNED NULL     DEFAULT NULL               COMMENT '[v25 신규] 이 건의 platform_fee가 집계된 월별 대행사→플랫폼 정산 ID (platform_settlements FK, 집계 전 NULL)',
    `paid_at`                DATETIME        NULL     DEFAULT NULL               COMMENT '지급 일시',
    `created_at`             DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '생성일',

    CONSTRAINT FK_payments_TO_settlements
        FOREIGN KEY (`payment_id`) REFERENCES `payments` (`payment_id`),
    CONSTRAINT FK_as_requests_TO_settlements
        FOREIGN KEY (`request_id`) REFERENCES `as_requests` (`request_id`),
    CONSTRAINT FK_users_TO_settlements_engineer
        FOREIGN KEY (`engineer_id`) REFERENCES `users` (`user_id`),
    CONSTRAINT FK_agencies_TO_settlements
        FOREIGN KEY (`agency_id`) REFERENCES `agencies` (`agency_id`),
    CONSTRAINT FK_platform_settlements_TO_settlements
        FOREIGN KEY (`platform_settlement_id`) REFERENCES `platform_settlements` (`platform_settlement_id`),

    UNIQUE uk_settlement_payment       (payment_id),
    INDEX  idx_settlement_engineer     (engineer_id, status, created_at),
    INDEX  idx_settlement_agency       (agency_id, status, created_at),
    INDEX  idx_settlement_status       (status, created_at),
    INDEX  idx_settlement_platform_stl (platform_settlement_id)
);

-- ============================================================
-- 27. bank_accounts
-- 기사(엔지니어) 정산금 지급 계좌 정보. DB명세서 v21 반영.
-- ============================================================
CREATE TABLE `bank_accounts` (
    `bank_account_id` BIGINT UNSIGNED NOT NULL PRIMARY KEY AUTO_INCREMENT COMMENT '계좌 정보 ID',
    `engineer_id`     BIGINT UNSIGNED NOT NULL                            COMMENT '기사 user_id (users.user_id 참조, 1:1)',
    `bank_name`       VARCHAR(50)     NOT NULL                            COMMENT '은행명 (예: 국민은행, 신한은행)',
    `account_number`  VARCHAR(50)     NOT NULL                            COMMENT '계좌번호',
    `account_holder`  VARCHAR(50)     NOT NULL                            COMMENT '예금주명',
    `pay_method`      ENUM('BANK_TRANSFER') NOT NULL DEFAULT 'BANK_TRANSFER' COMMENT '지급 방식 (현재 계좌이체 단일 지원, 추후 확장 대비 ENUM화)',
    `created_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '등록일',
    `updated_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일',

    CONSTRAINT FK_users_TO_bank_accounts
        FOREIGN KEY (`engineer_id`) REFERENCES `users` (`user_id`),

    UNIQUE uk_bank_accounts_engineer (engineer_id)
);

-- ============================================================
-- 28. notifications
-- ============================================================
CREATE TABLE `notifications` (
    `notification_id` BIGINT UNSIGNED NOT NULL PRIMARY KEY AUTO_INCREMENT COMMENT '알림 ID',
    `user_id`         BIGINT UNSIGNED NOT NULL                            COMMENT '수신 대상 user_id',
    `type`            ENUM('AS_STATUS','CONSUMABLE','WARRANTY','LMS') NOT NULL COMMENT '알림 유형',
    `title`           VARCHAR(200)    NOT NULL                            COMMENT '알림 제목',
    `body`            TEXT            NOT NULL                            COMMENT '알림 내용',
    `channel`         ENUM('SSE','PUSH','SMS','KAKAO') NOT NULL DEFAULT 'SSE' COMMENT '발송 채널',
    `is_read`         BOOLEAN         NOT NULL DEFAULT FALSE              COMMENT '읽음 여부 — 읽음·안읽음 갯수 집계용',
    `created_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '생성일',

    CONSTRAINT FK_users_TO_notifications
        FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`),

    INDEX idx_notif_user      (user_id, created_at),
    INDEX idx_notif_type      (type),
    INDEX idx_notif_user_read (user_id, is_read)
);

-- ============================================================
-- 29. lms_contents
-- [v22] video_url 컬럼 신규 추가
--   - content_type = 'VIDEO' 일 때 YouTube 영상 URL 저장
--   - content_type = 'TEXT'  일 때 NULL
--   - body 컬럼은 TEXT 전용 본문으로 의미 분리
-- ============================================================
CREATE TABLE `lms_contents` (
    `content_id`     BIGINT UNSIGNED NOT NULL PRIMARY KEY AUTO_INCREMENT COMMENT '콘텐츠 ID',
    `category_id`    INT UNSIGNED    NULL     DEFAULT NULL               COMMENT '가전 카테고리 ID (appliance_categories depth=2) — 제품군별 교육 구분',
    `title`          VARCHAR(200)    NOT NULL                            COMMENT '교육 콘텐츠 제목',
    `body`           TEXT            NULL     DEFAULT NULL               COMMENT '콘텐츠 본문 (TEXT 타입일 때 사용, HTML 또는 Markdown. VIDEO 타입이면 NULL)',
    `video_url`      VARCHAR(500)    NULL     DEFAULT NULL               COMMENT '[v22 신규] YouTube 영상 URL (VIDEO 타입일 때 사용, TEXT 타입이면 NULL)',
    `required_level` ENUM('BEGINNER','INTERMEDIATE','ADVANCED','ALL') NOT NULL DEFAULT 'ALL' COMMENT '필수 이수 대상 등급',
    `content_type`   ENUM('TEXT','VIDEO') NOT NULL DEFAULT 'TEXT'       COMMENT '콘텐츠 유형',
    `version`        VARCHAR(10)     NOT NULL DEFAULT '1.0'             COMMENT '콘텐츠 버전',
    `is_active`      TINYINT(1)      NOT NULL DEFAULT 1                  COMMENT '노출 여부',
    `created_by`     BIGINT UNSIGNED NOT NULL                            COMMENT '작성한 관리자 user_id',
    `created_at`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '등록일',
    `updated_at`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일',

    CONSTRAINT FK_appliance_categories_TO_lms_contents
        FOREIGN KEY (`category_id`) REFERENCES `appliance_categories` (`category_id`),
    CONSTRAINT FK_users_TO_lms_contents_created_by
        FOREIGN KEY (`created_by`) REFERENCES `users` (`user_id`),

    INDEX idx_lms_category_level (category_id, required_level, is_active),
    INDEX idx_lms_active         (is_active)
);

-- ============================================================
-- 30. lms_confirmations
-- [v23 변경] is_active 컬럼 추가
--   - OX퀴즈 3회 불합격 시 재이수 강제를 위한 논리 삭제 컬럼
--   - is_active=0: 재이수 강제로 비활성화된 이수 이력 (물리 삭제 없이 이력 보존)
--   - 응시 자격 판단 시 is_active=1 조건 필수
-- ============================================================
CREATE TABLE `lms_confirmations` (
    `confirmation_id`   BIGINT UNSIGNED NOT NULL PRIMARY KEY AUTO_INCREMENT COMMENT '이수 이력 ID',
    `user_id`           BIGINT UNSIGNED NOT NULL                            COMMENT '이수한 기사 user_id',
    `content_id`        BIGINT UNSIGNED NOT NULL                            COMMENT '이수한 콘텐츠 content_id',
    `completion_year`   YEAR            NOT NULL                            COMMENT '이수 연도',
    `confirmed_version` VARCHAR(10)     NOT NULL                            COMMENT '이수 당시 콘텐츠 버전',
    `confirmed_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '이수 완료 일시',
    `is_active`         TINYINT(1)      NOT NULL DEFAULT 1                  COMMENT '[v23 신규] 이수 이력 활성화 여부 (1=활성, 0=OX퀴즈 3회 불합격으로 재이수 강제 시 논리 삭제 — 응시 자격 판단 시 is_active=1 조건 필수)',
    `updated_at`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일',

    CONSTRAINT FK_users_TO_lms_confirmations
        FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`),
    CONSTRAINT FK_lms_contents_TO_lms_confirmations
        FOREIGN KEY (`content_id`) REFERENCES `lms_contents` (`content_id`),

    UNIQUE uk_lms_confirm_year       (user_id, content_id, completion_year),
    INDEX  idx_lms_confirm_user_year (user_id, completion_year),
    INDEX  idx_lms_confirm_content   (content_id, confirmed_at)
);


-- ============================================================
-- 31. quiz_questions (OX퀴즈 문항 마스터)
-- [v23 신규] LMS OX퀴즈 확장 — 카테고리 × 기술 등급 계층별 문항 마스터
-- [v24 변경] quiz_year 컬럼 추가 — 연도별 문항 독립 관리
--   - 12월 중 ADMIN이 내년도 문항 사전 등록 (is_active=0, quiz_year=내년)
--   - 1월 1일 QuizYearRolloverJob: 전년도 is_active=0 아카이브 + 신년도 is_active=1 활성화
--   - required_level: BEGINNER/INTERMEDIATE/ADVANCED (ALL 없음 — 퀴즈는 등급별로만 출제)
--   - 계층+연도당 5문제 고정: UNIQUE(category_id, required_level, quiz_year, sort_order)
--   - 정답(correct_answer)은 기사 화면 비공개, 서버 채점 전용
-- ============================================================
CREATE TABLE `quiz_questions` (
    `question_id`    BIGINT UNSIGNED NOT NULL PRIMARY KEY AUTO_INCREMENT COMMENT 'OX퀴즈 문항 ID',
    `category_id`    INT UNSIGNED    NOT NULL                            COMMENT '가전 카테고리 ID (appliance_categories depth=2 소분류)',
    `required_level` ENUM('BEGINNER','INTERMEDIATE','ADVANCED') NOT NULL COMMENT '대상 기술 등급 (ALL 없음 — 퀴즈는 등급별로만 출제)',
    `quiz_year`      YEAR            NOT NULL                            COMMENT '[v24 신규] 출제 연도 — 연도별 문항 관리. 12월 사전 등록(is_active=0) → 1월 1일 Quartz가 is_active=1로 전환',
    `question_text`  TEXT            NOT NULL                            COMMENT '문항 내용',
    `correct_answer` TINYINT(1)      NOT NULL                            COMMENT '정답 (1=O, 0=X) — 기사 화면 비공개, 서버 채점 전용',
    `sort_order`     TINYINT UNSIGNED NOT NULL DEFAULT 1                 COMMENT '문항 번호 (1~5) — uk_quiz_tier_order 구성 컬럼',
    `is_active`      TINYINT(1)      NOT NULL DEFAULT 1                  COMMENT '활성화 여부 (0=전년도 아카이브 또는 사전 등록 미활성화 상태)',
    `created_by`     BIGINT UNSIGNED NOT NULL                            COMMENT '등록 관리자 user_id',
    `created_at`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '등록일',
    `updated_at`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일',

    CONSTRAINT FK_appliance_categories_TO_quiz_questions
        FOREIGN KEY (`category_id`) REFERENCES `appliance_categories` (`category_id`),
    CONSTRAINT FK_users_TO_quiz_questions_created_by
        FOREIGN KEY (`created_by`) REFERENCES `users` (`user_id`),

    UNIQUE uk_quiz_tier_order      (category_id, required_level, quiz_year, sort_order),
    INDEX  idx_quiz_category_level (category_id, required_level, quiz_year, is_active),
    INDEX  idx_quiz_year           (quiz_year, is_active)
);

-- ============================================================
-- 32. quiz_attempts (OX퀴즈 응시 이력)
-- [v23 신규] 기사별 계층별 응시 이력 및 합격 여부 관리
-- [v24 변경] quiz_year 컬럼 추가 — 응시 연도 스냅샷
--   - is_passed: score >= 4 (5문항 중 4개 이상 정답)
--   - 불합격 시 합격/불합격 여부만 응답. 점수·정오답 일체 비공개
--   - 3회 불합격 시 lms_confirmations.is_active=0 처리 후 재이수 강제
--   - quiz_year: 응시 시점의 연도 스냅샷 — 전년도·금년도 응시 이력 구분
-- ============================================================
CREATE TABLE `quiz_attempts` (
    `attempt_id`     BIGINT UNSIGNED NOT NULL PRIMARY KEY AUTO_INCREMENT COMMENT 'OX퀴즈 응시 이력 ID',
    `user_id`        BIGINT UNSIGNED NOT NULL                            COMMENT '응시 기사 user_id',
    `category_id`    INT UNSIGNED    NOT NULL                            COMMENT '응시 카테고리 ID',
    `required_level` ENUM('BEGINNER','INTERMEDIATE','ADVANCED') NOT NULL COMMENT '응시 기술 등급',
    `quiz_year`      YEAR            NOT NULL                            COMMENT '[v24 신규] 응시 연도 스냅샷 — 전년도·금년도 응시 이력 구분 및 사이클 집계 기준',
    `score`          TINYINT UNSIGNED NOT NULL                           COMMENT '정답 수 (0~5) — 서버 내부 집계용, 프론트 미전달',
    `is_passed`      TINYINT(1)      NOT NULL DEFAULT 0                  COMMENT '합격 여부 (1=합격: score>=4, 0=불합격)',
    `attempted_at`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '응시 일시',

    CONSTRAINT FK_users_TO_quiz_attempts
        FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`),
    CONSTRAINT FK_appliance_categories_TO_quiz_attempts
        FOREIGN KEY (`category_id`) REFERENCES `appliance_categories` (`category_id`),

    INDEX idx_quiz_attempt_user   (user_id, category_id, required_level, quiz_year),
    INDEX idx_quiz_attempt_passed (user_id, is_passed),
    INDEX idx_quiz_year           (quiz_year, user_id)
);

-- ============================================================
-- ※ 순환참조 후처리 ALTER (agencies ↔ users)
-- agencies.representative_user_id → users.user_id
-- agencies.approved_by            → users.user_id
-- ============================================================
ALTER TABLE `agencies`
    ADD CONSTRAINT FK_users_TO_agencies_representative
        FOREIGN KEY (`representative_user_id`) REFERENCES `users` (`user_id`),
    ADD CONSTRAINT FK_users_TO_agencies_approved_by
        FOREIGN KEY (`approved_by`) REFERENCES `users` (`user_id`);
