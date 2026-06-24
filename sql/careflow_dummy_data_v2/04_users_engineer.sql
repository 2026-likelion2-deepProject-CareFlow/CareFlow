-- =============================================================
-- CareFlow 더미 데이터 Part 4
-- users — ENGINEER 30명 (user_id 44~73)
-- INACTIVE 2명: user_id 72, 73
-- region_id: depth=2 구 단위만 사용
-- =============================================================
USE careflow;
SET NAMES utf8mb4;

INSERT INTO `users`
  (user_id, agency_id, email, password_hash, name, phone, role, region_id, address_detail, status, last_login_at, created_at, updated_at, deleted_at)
VALUES
-- 대행사 1 소속 기사 (agency_id=1) 3명
(44, 1, 'eng001@hansolservice.co.kr', '$2b$12$EngHash001EngHash001EngHash001EngHash001Eng0', '김철수', '010-5001-0001', 'ENGINEER', 101, '역삼동 123-4 101호', 'ACTIVE', '2026-06-23 06:30:00', '2024-04-01 09:00:00', '2026-06-23 06:30:00', NULL),
(45, 1, 'eng002@hansolservice.co.kr', '$2b$12$EngHash002EngHash002EngHash002EngHash002Eng0', '이민수', '010-5001-0002', 'ENGINEER', 104, '화곡동 456-7 202호', 'ACTIVE', '2026-06-22 07:00:00', '2024-04-05 09:00:00', '2026-06-22 07:00:00', NULL),
(46, 1, 'eng003@hansolservice.co.kr', '$2b$12$EngHash003EngHash003EngHash003EngHash003Eng0', '박종호', '010-5001-0003', 'ENGINEER', 113, NULL, 'ACTIVE', '2026-06-21 07:30:00', '2024-04-10 09:00:00', '2026-06-21 07:30:00', NULL),

-- 대행사 2 소속 기사 (agency_id=2) 3명
(47, 2, 'eng004@namseoservice.co.kr', '$2b$12$EngHash004EngHash004EngHash004EngHash004Eng0', '최진우', '010-5002-0001', 'ENGINEER', 112, '사당동 78-9 301호', 'ACTIVE', '2026-06-23 06:00:00', '2024-04-02 09:00:00', '2026-06-23 06:00:00', NULL),
(48, 2, 'eng005@namseoservice.co.kr', '$2b$12$EngHash005EngHash005EngHash005EngHash005Eng0', '정승현', '010-5002-0002', 'ENGINEER', 120, '당산동 321-0 102호', 'ACTIVE', '2026-06-22 06:30:00', '2024-04-07 09:00:00', '2026-06-22 06:30:00', NULL),
(49, 2, 'eng006@namseoservice.co.kr', '$2b$12$EngHash006EngHash006EngHash006EngHash006Eng0', '강동현', '010-5002-0003', 'ENGINEER', 105, NULL, 'ACTIVE', '2026-06-21 07:00:00', '2024-04-12 09:00:00', '2026-06-21 07:00:00', NULL),

-- 대행사 3 소속 기사 (agency_id=3) 3명
(50, 3, 'eng007@bukhanservice.co.kr', '$2b$12$EngHash007EngHash007EngHash007EngHash007Eng0', '윤재현', '010-5003-0001', 'ENGINEER', 908, '덕소리 100-2 401호', 'ACTIVE', '2026-06-23 07:00:00', '2024-04-03 09:00:00', '2026-06-23 07:00:00', NULL),
(51, 3, 'eng008@bukhanservice.co.kr', '$2b$12$EngHash008EngHash008EngHash008EngHash008Eng0', '조형준', '010-5003-0002', 'ENGINEER', 901, '화정동 200-5 501호', 'ACTIVE', '2026-06-22 07:30:00', '2024-04-08 09:00:00', '2026-06-22 07:30:00', NULL),
(52, 3, 'eng009@bukhanservice.co.kr', '$2b$12$EngHash009EngHash009EngHash009EngHash009Eng0', '허성민', '010-5003-0003', 'ENGINEER', 912, NULL, 'ACTIVE', '2026-06-21 08:00:00', '2024-04-13 09:00:00', '2026-06-21 08:00:00', NULL),

-- 대행사 4 소속 기사 (agency_id=4) 3명
(53, 4, 'eng010@dongbuservice.co.kr', '$2b$12$EngHash010EngHash010EngHash010EngHash010Eng0', '오태준', '010-5004-0001', 'ENGINEER', 118, '잠실동 999-1 601호', 'ACTIVE', '2026-06-23 06:45:00', '2024-04-04 09:00:00', '2026-06-23 06:45:00', NULL),
(54, 4, 'eng011@dongbuservice.co.kr', '$2b$12$EngHash011EngHash011EngHash011EngHash011Eng0', '임도훈', '010-5004-0002', 'ENGINEER', 102, '천호동 555-3 701호', 'ACTIVE', '2026-06-22 07:15:00', '2024-04-09 09:00:00', '2026-06-22 07:15:00', NULL),
(55, 4, 'eng012@dongbuservice.co.kr', '$2b$12$EngHash012EngHash012EngHash012EngHash012Eng0', '한정민', '010-5004-0003', 'ENGINEER', 111, NULL, 'ACTIVE', '2026-06-21 07:45:00', '2024-04-14 09:00:00', '2026-06-21 07:45:00', NULL),

-- 대행사 5 소속 기사 (agency_id=5) 3명
(56, 5, 'eng013@seobuservice.co.kr', '$2b$12$EngHash013EngHash013EngHash013EngHash013Eng0', '신영호', '010-5005-0001', 'ENGINEER', 104, '강서구 방화동 333-1 201호', 'ACTIVE', '2026-06-23 06:30:00', '2024-05-01 09:00:00', '2026-06-23 06:30:00', NULL),
(57, 5, 'eng014@seobuservice.co.kr', '$2b$12$EngHash014EngHash014EngHash014EngHash014Eng0', '배찬영', '010-5005-0002', 'ENGINEER', 119, '목동 777-2 302호', 'ACTIVE', '2026-06-22 07:00:00', '2024-05-06 09:00:00', '2026-06-22 07:00:00', NULL),
(58, 5, 'eng015@seobuservice.co.kr', '$2b$12$EngHash015EngHash015EngHash015EngHash015Eng0', '황준서', '010-5005-0003', 'ENGINEER', 122, NULL, 'ACTIVE', '2026-06-21 07:30:00', '2024-05-11 09:00:00', '2026-06-21 07:30:00', NULL),

-- 대행사 6 소속 기사 (agency_id=6) 3명
(59, 6, 'eng016@gyeonggiservice.co.kr', '$2b$12$EngHash016EngHash016EngHash016EngHash016Eng0', '류성진', '010-5006-0001', 'ENGINEER', 912, '영통동 444-5 101호', 'ACTIVE', '2026-06-23 06:15:00', '2024-05-02 09:00:00', '2026-06-23 06:15:00', NULL),
(60, 6, 'eng017@gyeonggiservice.co.kr', '$2b$12$EngHash017EngHash017EngHash017EngHash017Eng0', '전광훈', '010-5006-0002', 'ENGINEER', 911, '분당동 888-6 202호', 'ACTIVE', '2026-06-22 06:45:00', '2024-05-07 09:00:00', '2026-06-22 06:45:00', NULL),
(61, 6, 'eng018@gyeonggiservice.co.kr', '$2b$12$EngHash018EngHash018EngHash018EngHash018Eng0', '고민준', '010-5006-0003', 'ENGINEER', 916, NULL, 'ACTIVE', '2026-06-21 07:15:00', '2024-05-12 09:00:00', '2026-06-21 07:15:00', NULL),

-- 대행사 7 소속 기사 (agency_id=7) 3명
(62, 7, 'eng019@busanservice.co.kr', '$2b$12$EngHash019EngHash019EngHash019EngHash019Eng0', '문태현', '010-5007-0001', 'ENGINEER', 216, '우동 111-2 301호', 'ACTIVE', '2026-06-23 07:00:00', '2024-05-03 09:00:00', '2026-06-23 07:00:00', NULL),
(63, 7, 'eng020@busanservice.co.kr', '$2b$12$EngHash020EngHash020EngHash020EngHash020Eng0', '성재훈', '010-5007-0002', 'ENGINEER', 207, '부산진구 전포동 222-3 401호', 'ACTIVE', '2026-06-22 07:30:00', '2024-05-08 09:00:00', '2026-06-22 07:30:00', NULL),
(64, 7, 'eng021@busanservice.co.kr', '$2b$12$EngHash021EngHash021EngHash021EngHash021Eng0', '방영석', '010-5007-0003', 'ENGINEER', 206, NULL, 'ACTIVE', '2026-06-21 08:00:00', '2024-05-13 09:00:00', '2026-06-21 08:00:00', NULL),

-- 대행사 8 소속 기사 (agency_id=8) 3명
(65, 8, 'eng022@daeguservice.co.kr', '$2b$12$EngHash022EngHash022EngHash022EngHash022Eng0', '차민규', '010-5008-0001', 'ENGINEER', 302, '달서구 월성동 333-4 501호', 'ACTIVE', '2026-06-23 06:30:00', '2024-05-04 09:00:00', '2026-06-23 06:30:00', NULL),
(66, 8, 'eng023@daeguservice.co.kr', '$2b$12$EngHash023EngHash023EngHash023EngHash023Eng0', '표지현', '010-5008-0002', 'ENGINEER', 307, '수성구 범어동 444-5 601호', 'ACTIVE', '2026-06-22 07:00:00', '2024-05-09 09:00:00', '2026-06-22 07:00:00', NULL),
(67, 8, 'eng024@daeguservice.co.kr', '$2b$12$EngHash024EngHash024EngHash024EngHash024Eng0', '어진우', '010-5008-0003', 'ENGINEER', 305, NULL, 'ACTIVE', '2026-06-21 07:30:00', '2024-05-14 09:00:00', '2026-06-21 07:30:00', NULL),

-- 대행사 9 소속 기사 (agency_id=9) 3명
(68, 9, 'eng025@incheonservice.co.kr', '$2b$12$EngHash025EngHash025EngHash025EngHash025Eng0', '석준현', '010-5009-0001', 'ENGINEER', 403, '남동구 논현동 555-6 101호', 'ACTIVE', '2026-06-23 06:45:00', '2024-05-05 09:00:00', '2026-06-23 06:45:00', NULL),
(69, 9, 'eng026@incheonservice.co.kr', '$2b$12$EngHash026EngHash026EngHash026EngHash026Eng0', '연승훈', '010-5009-0002', 'ENGINEER', 406, '부평동 666-7 202호', 'ACTIVE', '2026-06-22 07:15:00', '2024-05-10 09:00:00', '2026-06-22 07:15:00', NULL),
(70, 9, 'eng027@incheonservice.co.kr', '$2b$12$EngHash027EngHash027EngHash027EngHash027Eng0', '엄태섭', '010-5009-0003', 'ENGINEER', 408, NULL, 'ACTIVE', '2026-06-21 07:45:00', '2024-05-15 09:00:00', '2026-06-21 07:45:00', NULL),

-- 대행사 10 소속 기사 (agency_id=10) 3명
(71, 10, 'eng028@jeonseoservice.co.kr', '$2b$12$EngHash028EngHash028EngHash028EngHash028Eng0', '여민재', '010-5010-0001', 'ENGINEER', 1301, '전주시 완산구 효자동 777-8 301호', 'ACTIVE', '2026-06-23 07:00:00', '2024-05-06 09:00:00', '2026-06-23 07:00:00', NULL),
(72, 10, 'eng029@jeonseoservice.co.kr', '$2b$12$EngHash029EngHash029EngHash029EngHash029Eng0', '오세진', '010-5010-0002', 'ENGINEER', 1302, '군산시 문화동 888-9 401호', 'INACTIVE', '2026-05-10 09:00:00', '2024-05-11 09:00:00', '2026-05-10 09:00:00', NULL),
(73, 10, 'eng030@jeonseoservice.co.kr', '$2b$12$EngHash030EngHash030EngHash030EngHash030Eng0', '우도훈', '010-5010-0003', 'ENGINEER', 1303, NULL, 'INACTIVE', '2026-04-15 10:00:00', '2024-05-16 09:00:00', '2026-04-15 10:00:00', NULL);
