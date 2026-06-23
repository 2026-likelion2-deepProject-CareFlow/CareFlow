-- 테스트 메서드 실행 전 모든 테이블 초기화 (H2 FK 제약 일시 비활성화)
SET REFERENTIAL_INTEGRITY FALSE;
DELETE FROM account_requests;
DELETE FROM users;
DELETE FROM agencies;
DELETE FROM regions;
SET REFERENTIAL_INTEGRITY TRUE;