-- =============================================================
-- CareFlow 더미 데이터 (2026-01-01 서비스 시작 전제 반영 재생성)
-- Part 2: ADMIN(3) + AGENCY 슈퍼계정(10) + agencies (날짜 2026-01-01 전제 보정)
-- =============================================================
USE careflow;
SET NAMES utf8mb4;

SET FOREIGN_KEY_CHECKS = 0;

INSERT INTO `users`
  (user_id, agency_id, email, password_hash, name, phone, role, region_id, address_detail, status, last_login_at, two_factor_enabled, login_alert_enabled, created_at, updated_at, deleted_at)
VALUES
(1, NULL, 'admin01@careflow.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '김관리', '01090010001', 'ADMIN', NULL, NULL, 'ACTIVE', '2026-06-23 08:30:00', 0, 0, '2025-11-01 09:00:00', '2026-06-23 08:30:00', NULL),
(2, NULL, 'admin02@careflow.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '이운영', '01090010002', 'ADMIN', NULL, NULL, 'ACTIVE', '2026-06-22 09:15:00', 0, 0, '2025-11-01 09:30:00', '2026-06-22 09:15:00', NULL),
(3, NULL, 'admin03@careflow.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '박시스템', '01090010003', 'ADMIN', NULL, NULL, 'ACTIVE', '2026-06-21 11:00:00', 0, 0, '2025-11-01 10:00:00', '2026-06-21 11:00:00', NULL);

INSERT INTO `users`
  (user_id, agency_id, email, password_hash, name, phone, role, region_id, address_detail, status, last_login_at, two_factor_enabled, login_alert_enabled, created_at, updated_at, deleted_at)
VALUES
(4, NULL, 'super01@hansolservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '한솔서비스대표', '01020010001', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-23 07:00:00', 0, 0, '2025-11-10 09:00:00', '2026-06-23 07:00:00', NULL),
(5, NULL, 'super02@namseoservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '남서서비스대표', '01020020001', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-23 07:10:00', 0, 0, '2025-11-12 09:00:00', '2026-06-23 07:10:00', NULL),
(6, NULL, 'super03@bukhanservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '북한강서비스대표', '01020030001', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-22 08:00:00', 0, 0, '2025-11-14 09:00:00', '2026-06-22 08:00:00', NULL),
(7, NULL, 'super04@dongbuservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '동부서비스대표', '01020040001', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-23 08:30:00', 0, 0, '2025-11-16 09:00:00', '2026-06-23 08:30:00', NULL),
(8, NULL, 'super05@seobuservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '서부서비스대표', '01020050001', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-21 09:00:00', 0, 0, '2025-11-19 09:00:00', '2026-06-21 09:00:00', NULL),
(9, NULL, 'super06@gyeonggiservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '경기전자서비스대표', '01020060001', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-23 10:00:00', 0, 0, '2025-11-21 09:00:00', '2026-06-23 10:00:00', NULL),
(10, NULL, 'super07@busanservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '부산가전서비스대표', '01020070001', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-23 08:00:00', 0, 0, '2025-11-24 09:00:00', '2026-06-23 08:00:00', NULL),
(11, NULL, 'super08@daeguservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '대구전자대행대표', '01020080001', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-22 09:00:00', 0, 0, '2025-11-26 09:00:00', '2026-06-22 09:00:00', NULL),
(12, NULL, 'super09@incheonservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '인천서비스대표', '01020090001', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-23 07:30:00', 0, 0, '2025-11-28 09:00:00', '2026-06-23 07:30:00', NULL),
(13, NULL, 'super10@jeonseoservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '전서서비스대표', '01020100001', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-20 10:00:00', 0, 0, '2025-12-01 09:00:00', '2026-06-20 10:00:00', NULL);

INSERT INTO `agencies`
  (agency_id, representative_user_id, name, business_number, address, agency_fee_rate, approval_status, approved_at, approved_by, created_at, updated_at)
VALUES
(1, 4, '한솔전자서비스', '101-87-10001', '서울특별시 강남구 테헤란로 123', 0.46, 'APPROVED', '2025-11-13 14:00:00', 1, '2025-11-10 09:30:00', '2025-11-13 14:00:00'),
(2, 5, '남서가전수리센터', '201-87-20002', '서울특별시 영등포구 여의대로 55', 0.50, 'APPROVED', '2025-11-15 11:00:00', 1, '2025-11-12 09:30:00', '2025-11-15 11:00:00'),
(3, 6, '북한강전자서비스', '301-87-30003', '경기도 남양주시 화도읍 마석로 88', 0.53, 'APPROVED', '2025-11-18 10:00:00', 2, '2025-11-14 09:30:00', '2025-11-18 10:00:00'),
(4, 7, '동부종합서비스', '401-87-40004', '서울특별시 송파구 올림픽로 300', 0.53, 'APPROVED', '2025-11-20 15:00:00', 2, '2025-11-16 09:30:00', '2025-11-20 15:00:00'),
(5, 8, '서부가전서비스', '501-87-50005', '서울특별시 강서구 화곡로 200', 0.55, 'APPROVED', '2025-11-24 13:00:00', 3, '2025-11-19 09:30:00', '2025-11-24 13:00:00'),
(6, 9, '경기전자수리', '601-87-60006', '경기도 수원시 영통구 광교로 145', 0.46, 'APPROVED', '2025-11-26 10:00:00', 1, '2025-11-21 09:30:00', '2025-11-26 10:00:00'),
(7, 10, '부산가전전문서비스', '701-87-70007', '부산광역시 해운대구 센텀중앙로 97', 0.48, 'APPROVED', '2025-11-28 11:00:00', 2, '2025-11-24 09:30:00', '2025-11-28 11:00:00'),
(8, 11, '대구전자대행서비스', '801-87-80008', '대구광역시 달서구 달구벌대로 1234', 0.54, 'APPROVED', '2025-12-01 14:00:00', 3, '2025-11-26 09:30:00', '2025-12-01 14:00:00'),
(9, 12, '인천종합가전센터', '901-87-90009', '인천광역시 남동구 논현로 100', 0.54, 'APPROVED', '2025-12-03 10:00:00', 1, '2025-11-28 09:30:00', '2025-12-03 10:00:00'),
(10, 13, '전서가전수리점', '111-87-11110', '전라북도 전주시 완산구 효자로 77', 0.53, 'APPROVED', '2025-12-05 11:00:00', 2, '2025-12-01 09:30:00', '2025-12-05 11:00:00');

UPDATE `users` SET agency_id = 1 WHERE user_id = 4;
UPDATE `users` SET agency_id = 2 WHERE user_id = 5;
UPDATE `users` SET agency_id = 3 WHERE user_id = 6;
UPDATE `users` SET agency_id = 4 WHERE user_id = 7;
UPDATE `users` SET agency_id = 5 WHERE user_id = 8;
UPDATE `users` SET agency_id = 6 WHERE user_id = 9;
UPDATE `users` SET agency_id = 7 WHERE user_id = 10;
UPDATE `users` SET agency_id = 8 WHERE user_id = 11;
UPDATE `users` SET agency_id = 9 WHERE user_id = 12;
UPDATE `users` SET agency_id = 10 WHERE user_id = 13;

SET FOREIGN_KEY_CHECKS = 1;
