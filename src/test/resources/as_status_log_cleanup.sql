-- A/S 상태 변경 이력 통합 테스트 전용 초기화
SET REFERENTIAL_INTEGRITY FALSE;
DELETE FROM as_status_logs;
DELETE FROM as_assignments;
DELETE FROM as_requests;
DELETE FROM appliances;
DELETE FROM symptoms;
DELETE FROM appliance_categories;
DELETE FROM users;
DELETE FROM agencies;
DELETE FROM regions;
SET REFERENTIAL_INTEGRITY TRUE;
