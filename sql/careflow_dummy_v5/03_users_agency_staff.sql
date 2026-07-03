-- =============================================================
-- CareFlow 더미 데이터 Part 3
-- users — AGENCY 담당자 30명 (슈퍼계정 제외, 대행사별 3명씩)
-- user_id 14~43
-- =============================================================
USE careflow;
SET NAMES utf8mb4;

INSERT INTO `users`
  (user_id, agency_id, email, password_hash, name, phone, role, region_id, address_detail, status, last_login_at, created_at, updated_at, deleted_at)
VALUES
-- 대행사 1 (agency_id=1) 담당자 3명
(14, 1, 'agency1_staff1@hansolservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '박서진', '010-3001-0001', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-22 09:00:00', '2024-03-01 10:00:00', '2026-06-22 09:00:00', NULL),
(15, 1, 'agency1_staff2@hansolservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '최민준', '010-3001-0002', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-21 10:00:00', '2024-03-10 10:00:00', '2026-06-21 10:00:00', NULL),
(16, 1, 'agency1_staff3@hansolservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '정유나', '010-3001-0003', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-20 11:00:00', '2024-04-01 10:00:00', '2026-06-20 11:00:00', NULL),

-- 대행사 2 (agency_id=2) 담당자 3명
(17, 2, 'agency2_staff1@namseoservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '강태양', '010-3002-0001', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-23 08:00:00', '2024-03-05 10:00:00', '2026-06-23 08:00:00', NULL),
(18, 2, 'agency2_staff2@namseoservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '윤소희', '010-3002-0002', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-22 09:00:00', '2024-03-15 10:00:00', '2026-06-22 09:00:00', NULL),
(19, 2, 'agency2_staff3@namseoservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '임현우', '010-3002-0003', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-21 10:00:00', '2024-04-05 10:00:00', '2026-06-21 10:00:00', NULL),

-- 대행사 3 (agency_id=3) 담당자 3명
(20, 3, 'agency3_staff1@bukhanservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '오지훈', '010-3003-0001', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-23 07:30:00', '2024-03-10 10:00:00', '2026-06-23 07:30:00', NULL),
(21, 3, 'agency3_staff2@bukhanservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '한예린', '010-3003-0002', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-22 08:30:00', '2024-03-20 10:00:00', '2026-06-22 08:30:00', NULL),
(22, 3, 'agency3_staff3@bukhanservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '노준혁', '010-3003-0003', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-21 09:30:00', '2024-04-10 10:00:00', '2026-06-21 09:30:00', NULL),

-- 대행사 4 (agency_id=4) 담당자 3명
(23, 4, 'agency4_staff1@dongbuservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '성지원', '010-3004-0001', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-23 09:00:00', '2024-03-15 10:00:00', '2026-06-23 09:00:00', NULL),
(24, 4, 'agency4_staff2@dongbuservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '배수진', '010-3004-0002', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-22 10:00:00', '2024-03-25 10:00:00', '2026-06-22 10:00:00', NULL),
(25, 4, 'agency4_staff3@dongbuservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '진민서', '010-3004-0003', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-20 11:00:00', '2024-04-15 10:00:00', '2026-06-20 11:00:00', NULL),

-- 대행사 5 (agency_id=5) 담당자 3명
(26, 5, 'agency5_staff1@seobuservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '류하은', '010-3005-0001', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-23 08:00:00', '2024-04-01 10:00:00', '2026-06-23 08:00:00', NULL),
(27, 5, 'agency5_staff2@seobuservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '문재원', '010-3005-0002', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-22 09:00:00', '2024-04-10 10:00:00', '2026-06-22 09:00:00', NULL),
(28, 5, 'agency5_staff3@seobuservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '권나은', '010-3005-0003', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-21 10:00:00', '2024-04-20 10:00:00', '2026-06-21 10:00:00', NULL),

-- 대행사 6 (agency_id=6) 담당자 3명
(29, 6, 'agency6_staff1@gyeonggiservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '남도현', '010-3006-0001', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-23 07:00:00', '2024-04-05 10:00:00', '2026-06-23 07:00:00', NULL),
(30, 6, 'agency6_staff2@gyeonggiservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '심은서', '010-3006-0002', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-22 08:00:00', '2024-04-15 10:00:00', '2026-06-22 08:00:00', NULL),
(31, 6, 'agency6_staff3@gyeonggiservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '전민찬', '010-3006-0003', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-21 09:00:00', '2024-05-01 10:00:00', '2026-06-21 09:00:00', NULL),

-- 대행사 7 (agency_id=7) 담당자 3명
(32, 7, 'agency7_staff1@busanservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '고아름', '010-3007-0001', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-23 08:30:00', '2024-04-10 10:00:00', '2026-06-23 08:30:00', NULL),
(33, 7, 'agency7_staff2@busanservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '방성훈', '010-3007-0002', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-22 09:30:00', '2024-04-20 10:00:00', '2026-06-22 09:30:00', NULL),
(34, 7, 'agency7_staff3@busanservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '표지수', '010-3007-0003', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-21 10:30:00', '2024-05-05 10:00:00', '2026-06-21 10:30:00', NULL),

-- 대행사 8 (agency_id=8) 담당자 3명
(35, 8, 'agency8_staff1@daeguservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '사도윤', '010-3008-0001', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-23 09:00:00', '2024-04-15 10:00:00', '2026-06-23 09:00:00', NULL),
(36, 8, 'agency8_staff2@daeguservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '마유진', '010-3008-0002', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-22 10:00:00', '2024-04-25 10:00:00', '2026-06-22 10:00:00', NULL),
(37, 8, 'agency8_staff3@daeguservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '하지훈', '010-3008-0003', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-21 11:00:00', '2024-05-10 10:00:00', '2026-06-21 11:00:00', NULL),

-- 대행사 9 (agency_id=9) 담당자 3명
(38, 9, 'agency9_staff1@incheonservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '가은채', '010-3009-0001', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-23 08:00:00', '2024-05-01 10:00:00', '2026-06-23 08:00:00', NULL),
(39, 9, 'agency9_staff2@incheonservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '나태현', '010-3009-0002', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-22 09:00:00', '2024-05-10 10:00:00', '2026-06-22 09:00:00', NULL),
(40, 9, 'agency9_staff3@incheonservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '다솜이', '010-3009-0003', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-21 10:00:00', '2024-05-20 10:00:00', '2026-06-21 10:00:00', NULL),

-- 대행사 10 (agency_id=10) 담당자 3명
(41, 10, 'agency10_staff1@jeonseoservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '라민호', '010-3010-0001', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-23 07:30:00', '2024-05-05 10:00:00', '2026-06-23 07:30:00', NULL),
(42, 10, 'agency10_staff2@jeonseoservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '마수아', '010-3010-0002', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-22 08:30:00', '2024-05-15 10:00:00', '2026-06-22 08:30:00', NULL),
(43, 10, 'agency10_staff3@jeonseoservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '바이진', '010-3010-0003', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-21 09:30:00', '2024-05-25 10:00:00', '2026-06-21 09:30:00', NULL);
