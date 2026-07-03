-- =============================================================
-- CareFlow 더미 데이터 Part 2
-- users (ADMIN 3명 + AGENCY 슈퍼계정 10명) 먼저 삽입
-- agencies 생성 시 순환참조 우회를 위해 FK 체크 비활성화
-- =============================================================
USE careflow;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- =============================================================
-- ADMIN 계정 (3명 고정, agency_id=NULL, region_id=NULL)
-- =============================================================
INSERT INTO `users`
  (user_id, agency_id, email, password_hash, name, phone, role, region_id, address_detail, status, last_login_at, created_at, updated_at, deleted_at)
VALUES
(1, NULL, 'admin01@careflow.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '김관리', '010-9001-0001', 'ADMIN', NULL, NULL, 'ACTIVE', '2026-06-23 08:30:00', '2024-01-02 10:00:00', '2026-06-23 08:30:00', NULL),
(2, NULL, 'admin02@careflow.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '이운영', '010-9001-0002', 'ADMIN', NULL, NULL, 'ACTIVE', '2026-06-22 09:15:00', '2024-01-02 10:05:00', '2026-06-22 09:15:00', NULL),
(3, NULL, 'admin03@careflow.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '박시스템', '010-9001-0003', 'ADMIN', NULL, NULL, 'ACTIVE', '2026-06-21 11:00:00', '2024-01-02 10:10:00', '2026-06-21 11:00:00', NULL);

-- =============================================================
-- AGENCY 슈퍼계정 (10개 대행사 × 1명)
-- 슈퍼계정은 agencies보다 먼저 생성됨 (ADMIN이 직접 생성)
-- agency_id는 나중에 agencies INSERT 후 UPDATE로 설정
-- =============================================================
INSERT INTO `users`
  (user_id, agency_id, email, password_hash, name, phone, role, region_id, address_detail, status, last_login_at, created_at, updated_at, deleted_at)
VALUES
-- 대행사 1 슈퍼계정
(4, NULL, 'super01@hansolservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '한솔서비스대표', '010-2001-0001', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-23 07:00:00', '2024-02-01 09:00:00', '2026-06-23 07:00:00', NULL),
-- 대행사 2 슈퍼계정
(5, NULL, 'super02@namseoservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '남서서비스대표', '010-2002-0001', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-23 07:10:00', '2024-02-05 09:00:00', '2026-06-23 07:10:00', NULL),
-- 대행사 3 슈퍼계정
(6, NULL, 'super03@bukhanservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '북한강서비스대표', '010-2003-0001', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-22 08:00:00', '2024-02-10 09:00:00', '2026-06-22 08:00:00', NULL),
-- 대행사 4 슈퍼계정
(7, NULL, 'super04@dongbuservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '동부서비스대표', '010-2004-0001', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-23 08:30:00', '2024-02-15 09:00:00', '2026-06-23 08:30:00', NULL),
-- 대행사 5 슈퍼계정
(8, NULL, 'super05@seobuservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '서부서비스대표', '010-2005-0001', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-21 09:00:00', '2024-03-01 09:00:00', '2026-06-21 09:00:00', NULL),
-- 대행사 6 슈퍼계정
(9, NULL, 'super06@gyeonggiservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '경기전자서비스대표', '010-2006-0001', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-23 10:00:00', '2024-03-05 09:00:00', '2026-06-23 10:00:00', NULL),
-- 대행사 7 슈퍼계정
(10, NULL, 'super07@busanservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '부산가전서비스대표', '010-2007-0001', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-23 08:00:00', '2024-03-10 09:00:00', '2026-06-23 08:00:00', NULL),
-- 대행사 8 슈퍼계정
(11, NULL, 'super08@daeguservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '대구전자대행대표', '010-2008-0001', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-22 09:00:00', '2024-03-15 09:00:00', '2026-06-22 09:00:00', NULL),
-- 대행사 9 슈퍼계정
(12, NULL, 'super09@incheonservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '인천서비스대표', '010-2009-0001', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-23 07:30:00', '2024-04-01 09:00:00', '2026-06-23 07:30:00', NULL),
-- 대행사 10 슈퍼계정
(13, NULL, 'super10@jeonseoservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '전서서비스대표', '010-2010-0001', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-20 10:00:00', '2024-04-05 09:00:00', '2026-06-20 10:00:00', NULL);

-- =============================================================
-- agencies (10개)
-- representative_user_id: 위 슈퍼계정 user_id
-- approved_by: ADMIN user_id (1,2,3 중)
-- =============================================================
INSERT INTO `agencies`
  (agency_id, representative_user_id, name, business_number, address, agency_fee_rate, approval_status, approved_at, approved_by, created_at, updated_at)
VALUES
(1,  4,  '한솔전자서비스',     '101-87-10001', '서울특별시 강남구 테헤란로 123',       15.00, 'APPROVED', '2024-02-03 14:00:00', 1, '2024-02-01 09:00:00', '2024-02-03 14:00:00'),
(2,  5,  '남서가전수리센터',   '201-87-20002', '서울특별시 영등포구 여의대로 55',       12.00, 'APPROVED', '2024-02-07 11:00:00', 1, '2024-02-05 09:00:00', '2024-02-07 11:00:00'),
(3,  6,  '북한강전자서비스',   '301-87-30003', '경기도 남양주시 화도읍 마석로 88',      13.50, 'APPROVED', '2024-02-12 10:00:00', 2, '2024-02-10 09:00:00', '2024-02-12 10:00:00'),
(4,  7,  '동부종합서비스',     '401-87-40004', '서울특별시 송파구 올림픽로 300',        10.00, 'APPROVED', '2024-02-17 15:00:00', 2, '2024-02-15 09:00:00', '2024-02-17 15:00:00'),
(5,  8,  '서부가전서비스',     '501-87-50005', '서울특별시 강서구 화곡로 200',          14.00, 'APPROVED', '2024-03-03 13:00:00', 3, '2024-03-01 09:00:00', '2024-03-03 13:00:00'),
(6,  9,  '경기전자수리',       '601-87-60006', '경기도 수원시 영통구 광교로 145',       11.00, 'APPROVED', '2024-03-07 10:00:00', 1, '2024-03-05 09:00:00', '2024-03-07 10:00:00'),
(7,  10, '부산가전전문서비스', '701-87-70007', '부산광역시 해운대구 센텀중앙로 97',     16.00, 'APPROVED', '2024-03-12 11:00:00', 2, '2024-03-10 09:00:00', '2024-03-12 11:00:00'),
(8,  11, '대구전자대행서비스', '801-87-80008', '대구광역시 달서구 달구벌대로 1234',     13.00, 'APPROVED', '2024-03-17 14:00:00', 3, '2024-03-15 09:00:00', '2024-03-17 14:00:00'),
(9,  12, '인천종합가전센터',   '901-87-90009', '인천광역시 남동구 논현로 100',          12.50, 'APPROVED', '2024-04-03 10:00:00', 1, '2024-04-01 09:00:00', '2024-04-03 10:00:00'),
(10, 13, '전서가전수리점',     '111-87-11110', '전라북도 전주시 완산구 효자로 77',      10.50, 'APPROVED', '2024-04-07 11:00:00', 2, '2024-04-05 09:00:00', '2024-04-07 11:00:00');

-- 슈퍼계정의 agency_id 업데이트
UPDATE `users` SET agency_id = 1  WHERE user_id = 4;
UPDATE `users` SET agency_id = 2  WHERE user_id = 5;
UPDATE `users` SET agency_id = 3  WHERE user_id = 6;
UPDATE `users` SET agency_id = 4  WHERE user_id = 7;
UPDATE `users` SET agency_id = 5  WHERE user_id = 8;
UPDATE `users` SET agency_id = 6  WHERE user_id = 9;
UPDATE `users` SET agency_id = 7  WHERE user_id = 10;
UPDATE `users` SET agency_id = 8  WHERE user_id = 11;
UPDATE `users` SET agency_id = 9  WHERE user_id = 12;
UPDATE `users` SET agency_id = 10 WHERE user_id = 13;

SET FOREIGN_KEY_CHECKS = 1;
