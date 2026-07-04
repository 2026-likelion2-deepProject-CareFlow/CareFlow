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
(14, 1, 'agency1_staff1@hansolservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '박서진', '01030010001', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-22 09:00:00', '2024-03-01 10:00:00', '2026-06-22 09:00:00', NULL),
(15, 1, 'agency1_staff2@hansolservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '최민준', '01030010002', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-21 10:00:00', '2024-03-10 10:00:00', '2026-06-21 10:00:00', NULL),
(16, 1, 'agency1_staff3@hansolservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '정유나', '01030010003', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-20 11:00:00', '2024-04-01 10:00:00', '2026-06-20 11:00:00', NULL),

-- 대행사 2 (agency_id=2) 담당자 3명
(17, 2, 'agency2_staff1@namseoservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '강태양', '01030020001', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-23 08:00:00', '2024-03-05 10:00:00', '2026-06-23 08:00:00', NULL),
(18, 2, 'agency2_staff2@namseoservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '윤소희', '01030020002', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-22 09:00:00', '2024-03-15 10:00:00', '2026-06-22 09:00:00', NULL),
(19, 2, 'agency2_staff3@namseoservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '임현우', '01030020003', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-21 10:00:00', '2024-04-05 10:00:00', '2026-06-21 10:00:00', NULL),

-- 대행사 3 (agency_id=3) 담당자 3명
(20, 3, 'agency3_staff1@bukhanservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '오지훈', '01030030001', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-23 07:30:00', '2024-03-10 10:00:00', '2026-06-23 07:30:00', NULL),
(21, 3, 'agency3_staff2@bukhanservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '한예린', '01030030002', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-22 08:30:00', '2024-03-20 10:00:00', '2026-06-22 08:30:00', NULL),
(22, 3, 'agency3_staff3@bukhanservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '노준혁', '01030030003', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-21 09:30:00', '2024-04-10 10:00:00', '2026-06-21 09:30:00', NULL),

-- 대행사 4 (agency_id=4) 담당자 3명
(23, 4, 'agency4_staff1@dongbuservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '성지원', '01030040001', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-23 09:00:00', '2024-03-15 10:00:00', '2026-06-23 09:00:00', NULL),
(24, 4, 'agency4_staff2@dongbuservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '배수진', '01030040002', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-22 10:00:00', '2024-03-25 10:00:00', '2026-06-22 10:00:00', NULL),
(25, 4, 'agency4_staff3@dongbuservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '진민서', '01030040003', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-20 11:00:00', '2024-04-15 10:00:00', '2026-06-20 11:00:00', NULL),

-- 대행사 5 (agency_id=5) 담당자 3명
(26, 5, 'agency5_staff1@seobuservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '류하은', '01030050001', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-23 08:00:00', '2024-04-01 10:00:00', '2026-06-23 08:00:00', NULL),
(27, 5, 'agency5_staff2@seobuservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '문재원', '01030050002', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-22 09:00:00', '2024-04-10 10:00:00', '2026-06-22 09:00:00', NULL),
(28, 5, 'agency5_staff3@seobuservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '권나은', '01030050003', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-21 10:00:00', '2024-04-20 10:00:00', '2026-06-21 10:00:00', NULL),

-- 대행사 6 (agency_id=6) 담당자 3명
(29, 6, 'agency6_staff1@gyeonggiservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '남도현', '01030060001', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-23 07:00:00', '2024-04-05 10:00:00', '2026-06-23 07:00:00', NULL),
(30, 6, 'agency6_staff2@gyeonggiservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '심은서', '01030060002', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-22 08:00:00', '2024-04-15 10:00:00', '2026-06-22 08:00:00', NULL),
(31, 6, 'agency6_staff3@gyeonggiservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '전민찬', '01030060003', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-21 09:00:00', '2024-05-01 10:00:00', '2026-06-21 09:00:00', NULL),

-- 대행사 7 (agency_id=7) 담당자 3명
(32, 7, 'agency7_staff1@busanservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '고아름', '01030070001', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-23 08:30:00', '2024-04-10 10:00:00', '2026-06-23 08:30:00', NULL),
(33, 7, 'agency7_staff2@busanservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '방성훈', '01030070002', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-22 09:30:00', '2024-04-20 10:00:00', '2026-06-22 09:30:00', NULL),
(34, 7, 'agency7_staff3@busanservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '표지수', '01030070003', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-21 10:30:00', '2024-05-05 10:00:00', '2026-06-21 10:30:00', NULL),

-- 대행사 8 (agency_id=8) 담당자 3명
(35, 8, 'agency8_staff1@daeguservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '사도윤', '01030080001', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-23 09:00:00', '2024-04-15 10:00:00', '2026-06-23 09:00:00', NULL),
(36, 8, 'agency8_staff2@daeguservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '마유진', '01030080002', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-22 10:00:00', '2024-04-25 10:00:00', '2026-06-22 10:00:00', NULL),
(37, 8, 'agency8_staff3@daeguservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '하지훈', '01030080003', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-21 11:00:00', '2024-05-10 10:00:00', '2026-06-21 11:00:00', NULL),

-- 대행사 9 (agency_id=9) 담당자 3명
(38, 9, 'agency9_staff1@incheonservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '가은채', '01030090001', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-23 08:00:00', '2024-05-01 10:00:00', '2026-06-23 08:00:00', NULL),
(39, 9, 'agency9_staff2@incheonservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '나태현', '01030090002', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-22 09:00:00', '2024-05-10 10:00:00', '2026-06-22 09:00:00', NULL),
(40, 9, 'agency9_staff3@incheonservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '다솜이', '01030090003', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-21 10:00:00', '2024-05-20 10:00:00', '2026-06-21 10:00:00', NULL),

-- 대행사 10 (agency_id=10) 담당자 3명
(41, 10, 'agency10_staff1@jeonseoservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '라민호', '01030100001', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-23 07:30:00', '2024-05-05 10:00:00', '2026-06-23 07:30:00', NULL),
(42, 10, 'agency10_staff2@jeonseoservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '마수아', '01030100002', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-22 08:30:00', '2024-05-15 10:00:00', '2026-06-22 08:30:00', NULL),
(43, 10, 'agency10_staff3@jeonseoservice.co.kr', '$2y$04$BK8.nOhlwI2Q0xULB2y0buEWae7i98mFRqTkJZY0yh0LT.Ay4oNPG', '바이진', '01030100003', 'AGENCY', NULL, NULL, 'ACTIVE', '2026-06-21 09:30:00', '2024-05-25 10:00:00', '2026-06-21 09:30:00', NULL);
