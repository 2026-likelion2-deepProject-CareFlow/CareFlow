-- =============================================================
-- CareFlow 더미 데이터 Part 5
-- users — CUSTOMER 50명 (user_id 74~123)
-- INACTIVE 3명: user_id 121, 122, 123
-- =============================================================
USE careflow;
SET NAMES utf8mb4;

INSERT INTO `users`
  (user_id, agency_id, email, password_hash, name, phone, role, region_id, address_detail, status, last_login_at, created_at, updated_at, deleted_at)
VALUES
(74,  NULL, 'cust001@gmail.com',    '$2b$12$CustHash001CustHash001CustHash001CustHash001C', '김지수', '010-1001-0001', 'CUSTOMER', 101, '역삼동 11-1 101호',   'ACTIVE', '2026-06-22 20:00:00', '2024-01-10 12:00:00', '2026-06-22 20:00:00', NULL),
(75,  NULL, 'cust002@naver.com',    '$2b$12$CustHash002CustHash002CustHash002CustHash002C', '이서연', '010-1001-0002', 'CUSTOMER', 115, '방배동 22-2 201호',   'ACTIVE', '2026-06-21 19:00:00', '2024-01-12 12:00:00', '2026-06-21 19:00:00', NULL),
(76,  NULL, 'cust003@kakao.com',    '$2b$12$CustHash003CustHash003CustHash003CustHash003C', '박민준', '010-1001-0003', 'CUSTOMER', 118, '잠실동 33-3 301호',   'ACTIVE', '2026-06-23 18:00:00', '2024-01-15 12:00:00', '2026-06-23 18:00:00', NULL),
(77,  NULL, 'cust004@daum.net',     '$2b$12$CustHash004CustHash004CustHash004CustHash004C', '최수아', '010-1001-0004', 'CUSTOMER', 113, '마포동 44-4 401호',   'ACTIVE', '2026-06-22 17:00:00', '2024-01-18 12:00:00', '2026-06-22 17:00:00', NULL),
(78,  NULL, 'cust005@gmail.com',    '$2b$12$CustHash005CustHash005CustHash005CustHash005C', '정은우', '010-1001-0005', 'CUSTOMER', 116, '행당동 55-5 501호',   'ACTIVE', '2026-06-21 16:00:00', '2024-01-20 12:00:00', '2026-06-21 16:00:00', NULL),
(79,  NULL, 'cust006@naver.com',    '$2b$12$CustHash006CustHash006CustHash006CustHash006C', '강하늘', '010-1001-0006', 'CUSTOMER', 109, '상계동 66-6 601호',   'ACTIVE', '2026-06-23 15:00:00', '2024-01-22 12:00:00', '2026-06-23 15:00:00', NULL),
(80,  NULL, 'cust007@hotmail.com',  '$2b$12$CustHash007CustHash007CustHash007CustHash007C', '윤소율', '010-1001-0007', 'CUSTOMER', 106, '자양동 77-7 701호',   'ACTIVE', '2026-06-22 14:00:00', '2024-02-01 12:00:00', '2026-06-22 14:00:00', NULL),
(81,  NULL, 'cust008@gmail.com',    '$2b$12$CustHash008CustHash008CustHash008CustHash008C', '조현석', '010-1001-0008', 'CUSTOMER', 110, '방학동 88-8 101호',   'ACTIVE', '2026-06-21 13:00:00', '2024-02-05 12:00:00', '2026-06-21 13:00:00', NULL),
(82,  NULL, 'cust009@naver.com',    '$2b$12$CustHash009CustHash009CustHash009CustHash009C', '임지아', '010-1001-0009', 'CUSTOMER', 117, NULL,                  'ACTIVE', '2026-06-23 12:00:00', '2024-02-08 12:00:00', '2026-06-23 12:00:00', NULL),
(83,  NULL, 'cust010@kakao.com',    '$2b$12$CustHash010CustHash010CustHash010CustHash010C', '한태양', '010-1001-0010', 'CUSTOMER', 123, '청운동 99-9 201호',   'ACTIVE', '2026-06-22 11:00:00', '2024-02-10 12:00:00', '2026-06-22 11:00:00', NULL),
(84,  NULL, 'cust011@gmail.com',    '$2b$12$CustHash011CustHash011CustHash011CustHash011C', '오주원', '010-1001-0011', 'CUSTOMER', 912, '영통동 10-1 301호',   'ACTIVE', '2026-06-21 10:00:00', '2024-02-12 12:00:00', '2026-06-21 10:00:00', NULL),
(85,  NULL, 'cust012@naver.com',    '$2b$12$CustHash012CustHash012CustHash012CustHash012C', '서민지', '010-1001-0012', 'CUSTOMER', 911, '야탑동 20-2 401호',   'ACTIVE', '2026-06-23 09:00:00', '2024-02-15 12:00:00', '2026-06-23 09:00:00', NULL),
(86,  NULL, 'cust013@gmail.com',    '$2b$12$CustHash013CustHash013CustHash013CustHash013C', '권도현', '010-1001-0013', 'CUSTOMER', 901, '화정동 30-3 501호',   'ACTIVE', '2026-06-22 08:00:00', '2024-02-18 12:00:00', '2026-06-22 08:00:00', NULL),
(87,  NULL, 'cust014@daum.net',     '$2b$12$CustHash014CustHash014CustHash014CustHash014C', '배유진', '010-1001-0014', 'CUSTOMER', 910, '부천시 원미동 40-4',  'ACTIVE', '2026-06-21 07:00:00', '2024-02-20 12:00:00', '2026-06-21 07:00:00', NULL),
(88,  NULL, 'cust015@gmail.com',    '$2b$12$CustHash015CustHash015CustHash015CustHash015C', '전나영', '010-1001-0015', 'CUSTOMER', 916, NULL,                  'ACTIVE', '2026-06-23 20:00:00', '2024-03-01 12:00:00', '2026-06-23 20:00:00', NULL),
(89,  NULL, 'cust016@naver.com',    '$2b$12$CustHash016CustHash016CustHash016CustHash016C', '황성민', '010-1001-0016', 'CUSTOMER', 914, '안산시 단원동 50-5',  'ACTIVE', '2026-06-22 19:30:00', '2024-03-05 12:00:00', '2026-06-22 19:30:00', NULL),
(90,  NULL, 'cust017@gmail.com',    '$2b$12$CustHash017CustHash017CustHash017CustHash017C', '신지호', '010-1001-0017', 'CUSTOMER', 920, '용인시 수지동 60-6',  'ACTIVE', '2026-06-21 18:30:00', '2024-03-08 12:00:00', '2026-06-21 18:30:00', NULL),
(91,  NULL, 'cust018@kakao.com',    '$2b$12$CustHash018CustHash018CustHash018CustHash018C', '문채린', '010-1001-0018', 'CUSTOMER', 924, '파주시 금촌동 70-7',  'ACTIVE', '2026-06-23 17:30:00', '2024-03-10 12:00:00', '2026-06-23 17:30:00', NULL),
(92,  NULL, 'cust019@naver.com',    '$2b$12$CustHash019CustHash019CustHash019CustHash019C', '류건우', '010-1001-0019', 'CUSTOMER', 216, NULL,                  'ACTIVE', '2026-06-22 16:30:00', '2024-03-12 12:00:00', '2026-06-22 16:30:00', NULL),
(93,  NULL, 'cust020@gmail.com',    '$2b$12$CustHash020CustHash020CustHash020CustHash020C', '남하연', '010-1001-0020', 'CUSTOMER', 207, '부산진구 부전동 80-8','ACTIVE', '2026-06-21 15:30:00', '2024-03-15 12:00:00', '2026-06-21 15:30:00', NULL),
(94,  NULL, 'cust021@daum.net',     '$2b$12$CustHash021CustHash021CustHash021CustHash021C', '노수현', '010-1001-0021', 'CUSTOMER', 204, '부산 남구 대연동 90-9','ACTIVE', '2026-06-23 14:30:00', '2024-03-18 12:00:00', '2026-06-23 14:30:00', NULL),
(95,  NULL, 'cust022@gmail.com',    '$2b$12$CustHash022CustHash022CustHash022CustHash022C', '성지원', '010-1001-0022', 'CUSTOMER', 302, '달서구 진천동 11-1',  'ACTIVE', '2026-06-22 13:30:00', '2024-03-20 12:00:00', '2026-06-22 13:30:00', NULL),
(96,  NULL, 'cust023@naver.com',    '$2b$12$CustHash023CustHash023CustHash023CustHash023C', '마은솔', '010-1001-0023', 'CUSTOMER', 307, NULL,                  'ACTIVE', '2026-06-21 12:30:00', '2024-04-01 12:00:00', '2026-06-21 12:30:00', NULL),
(97,  NULL, 'cust024@gmail.com',    '$2b$12$CustHash024CustHash024CustHash024CustHash024C', '사도현', '010-1001-0024', 'CUSTOMER', 403, '남동구 구월동 22-2',  'ACTIVE', '2026-06-23 11:30:00', '2024-04-05 12:00:00', '2026-06-23 11:30:00', NULL),
(98,  NULL, 'cust025@kakao.com',    '$2b$12$CustHash025CustHash025CustHash025CustHash025C', '아예진', '010-1001-0025', 'CUSTOMER', 406, '부평동 33-3 303호',   'ACTIVE', '2026-06-22 10:30:00', '2024-04-08 12:00:00', '2026-06-22 10:30:00', NULL),
(99,  NULL, 'cust026@naver.com',    '$2b$12$CustHash026CustHash026CustHash026CustHash026C', '자현철', '010-1001-0026', 'CUSTOMER', 1301, '완산구 효자동 44-4', 'ACTIVE', '2026-06-21 09:30:00', '2024-04-10 12:00:00', '2026-06-21 09:30:00', NULL),
(100, NULL, 'cust027@gmail.com',    '$2b$12$CustHash027CustHash027CustHash027CustHash027C', '차민영', '010-1001-0027', 'CUSTOMER', 601, '대덕구 신탄진동 55',  'ACTIVE', '2026-06-23 08:30:00', '2024-04-12 12:00:00', '2026-06-23 08:30:00', NULL),
(101, NULL, 'cust028@daum.net',     '$2b$12$CustHash028CustHash028CustHash028CustHash028C', '카도윤', '010-1001-0028', 'CUSTOMER', 604, NULL,                  'ACTIVE', '2026-06-22 07:30:00', '2024-04-15 12:00:00', '2026-06-22 07:30:00', NULL),
(102, NULL, 'cust029@gmail.com',    '$2b$12$CustHash029CustHash029CustHash029CustHash029C', '타유나', '010-1001-0029', 'CUSTOMER', 501, '광산구 첨단동 66-6',  'ACTIVE', '2026-06-21 20:00:00', '2024-04-18 12:00:00', '2026-06-21 20:00:00', NULL),
(103, NULL, 'cust030@naver.com',    '$2b$12$CustHash030CustHash030CustHash030CustHash030C', '파지훈', '010-1001-0030', 'CUSTOMER', 1601, '창원시 성산동 77-7','ACTIVE', '2026-06-23 19:00:00', '2024-05-01 12:00:00', '2026-06-23 19:00:00', NULL),
(104, NULL, 'cust031@gmail.com',    '$2b$12$CustHash031CustHash031CustHash031CustHash031C', '하준우', '010-1001-0031', 'CUSTOMER', 102, '강동구 명일동 88-8',  'ACTIVE', '2026-06-22 18:00:00', '2024-05-03 12:00:00', '2026-06-22 18:00:00', NULL),
(105, NULL, 'cust032@kakao.com',    '$2b$12$CustHash032CustHash032CustHash032CustHash032C', '사은빈', '010-1001-0032', 'CUSTOMER', 119, '목동 90-9 901호',     'ACTIVE', '2026-06-21 17:00:00', '2024-05-06 12:00:00', '2026-06-21 17:00:00', NULL),
(106, NULL, 'cust033@naver.com',    '$2b$12$CustHash033CustHash033CustHash033CustHash033C', '가민지', '010-1001-0033', 'CUSTOMER', 103, NULL,                  'ACTIVE', '2026-06-23 16:00:00', '2024-05-08 12:00:00', '2026-06-23 16:00:00', NULL),
(107, NULL, 'cust034@gmail.com',    '$2b$12$CustHash034CustHash034CustHash034CustHash034C', '나준혁', '010-1001-0034', 'CUSTOMER', 107, '구로동 11-1 101호',   'ACTIVE', '2026-06-22 15:00:00', '2024-05-10 12:00:00', '2026-06-22 15:00:00', NULL),
(108, NULL, 'cust035@daum.net',     '$2b$12$CustHash035CustHash035CustHash035CustHash035C', '다소연', '010-1001-0035', 'CUSTOMER', 114, '서대문구 홍은동 22',  'ACTIVE', '2026-06-21 14:00:00', '2024-05-12 12:00:00', '2026-06-21 14:00:00', NULL),
(109, NULL, 'cust036@gmail.com',    '$2b$12$CustHash036CustHash036CustHash036CustHash036C', '라현우', '010-1001-0036', 'CUSTOMER', 120, NULL,                  'ACTIVE', '2026-06-23 13:00:00', '2024-05-15 12:00:00', '2026-06-23 13:00:00', NULL),
(110, NULL, 'cust037@naver.com',    '$2b$12$CustHash037CustHash037CustHash037CustHash037C', '마세린', '010-1001-0037', 'CUSTOMER', 121, '용산구 이태원동 33',  'ACTIVE', '2026-06-22 12:00:00', '2024-05-18 12:00:00', '2026-06-22 12:00:00', NULL),
(111, NULL, 'cust038@kakao.com',    '$2b$12$CustHash038CustHash038CustHash038CustHash038C', '바도율', '010-1001-0038', 'CUSTOMER', 124, '중구 명동 44-4 401호', 'ACTIVE', '2026-06-21 11:00:00', '2024-05-20 12:00:00', '2026-06-21 11:00:00', NULL),
(112, NULL, 'cust039@gmail.com',    '$2b$12$CustHash039CustHash039CustHash039CustHash039C', '사지호', '010-1001-0039', 'CUSTOMER', 125, NULL,                  'ACTIVE', '2026-06-23 10:00:00', '2024-05-22 12:00:00', '2026-06-23 10:00:00', NULL),
(113, NULL, 'cust040@naver.com',    '$2b$12$CustHash040CustHash040CustHash040CustHash040C', '아나래', '010-1001-0040', 'CUSTOMER', 108, '금천구 시흥동 55-5',  'ACTIVE', '2026-06-22 09:00:00', '2024-05-25 12:00:00', '2026-06-22 09:00:00', NULL),
(114, NULL, 'cust041@daum.net',     '$2b$12$CustHash041CustHash041CustHash041CustHash041C', '자민찬', '010-1001-0041', 'CUSTOMER', 111, '동대문구 전농동 66',  'ACTIVE', '2026-06-21 08:00:00', '2024-06-01 12:00:00', '2026-06-21 08:00:00', NULL),
(115, NULL, 'cust042@gmail.com',    '$2b$12$CustHash042CustHash042CustHash042CustHash042C', '차예원', '010-1001-0042', 'CUSTOMER', 922, NULL,                  'ACTIVE', '2026-06-23 19:30:00', '2024-06-05 12:00:00', '2026-06-23 19:30:00', NULL),
(116, NULL, 'cust043@naver.com',    '$2b$12$CustHash043CustHash043CustHash043CustHash043C', '카도현', '010-1001-0043', 'CUSTOMER', 925, '평택시 안중읍 77-7',  'ACTIVE', '2026-06-22 18:30:00', '2024-06-08 12:00:00', '2026-06-22 18:30:00', NULL),
(117, NULL, 'cust044@kakao.com',    '$2b$12$CustHash044CustHash044CustHash044CustHash044C', '타은진', '010-1001-0044', 'CUSTOMER', 928, '화성시 동탄동 88-8',  'ACTIVE', '2026-06-21 17:30:00', '2024-06-10 12:00:00', '2026-06-21 17:30:00', NULL),
(118, NULL, 'cust045@gmail.com',    '$2b$12$CustHash045CustHash045CustHash045CustHash045C', '파하은', '010-1001-0045', 'CUSTOMER', 1701, NULL,                 'ACTIVE', '2026-06-23 16:30:00', '2024-06-12 12:00:00', '2026-06-23 16:30:00', NULL),
(119, NULL, 'cust046@naver.com',    '$2b$12$CustHash046CustHash046CustHash046CustHash046C', '하성우', '010-1001-0046', 'CUSTOMER', 701, '울산 남구 달동 99-9', 'ACTIVE', '2026-06-22 15:30:00', '2024-06-15 12:00:00', '2026-06-22 15:30:00', NULL),
(120, NULL, 'cust047@daum.net',     '$2b$12$CustHash047CustHash047CustHash047CustHash047C', '사예은', '010-1001-0047', 'CUSTOMER', 1001, '강릉시 교동 11-1',   'ACTIVE', '2026-06-21 14:30:00', '2024-06-18 12:00:00', '2026-06-21 14:30:00', NULL),

-- INACTIVE 3명
(121, NULL, 'cust048@gmail.com',    '$2b$12$CustHash048CustHash048CustHash048CustHash048C', '이탈퇴', '010-1001-0048', 'CUSTOMER', 101, '강남구 삼성동 22-2', 'INACTIVE', '2026-03-01 10:00:00', '2024-03-01 12:00:00', '2026-03-01 10:00:00', NULL),
(122, NULL, 'cust049@naver.com',    '$2b$12$CustHash049CustHash049CustHash049CustHash049C', '박비활성', '010-1001-0049', 'CUSTOMER', 912, NULL,               'INACTIVE', '2026-02-15 09:00:00', '2024-02-15 12:00:00', '2026-02-15 09:00:00', NULL),
(123, NULL, 'cust050@kakao.com',    '$2b$12$CustHash050CustHash050CustHash050CustHash050C', '최중단', '010-1001-0050', 'CUSTOMER', 216, '해운대구 좌동 33-3', 'INACTIVE', '2026-01-20 08:00:00', '2024-01-20 12:00:00', '2026-01-20 08:00:00', NULL);
