-- 테스트 메서드 실행 전 모든 테이블 초기화 (H2 FK 제약 일시 비활성화)
-- SET REFERENTIAL_INTEGRITY FALSE 로 삭제 순서에 관계없이 일괄 초기화
SET REFERENTIAL_INTEGRITY FALSE;
DELETE FROM agency_bank_accounts;
DELETE FROM account_requests;
DELETE FROM work_report_parts;
DELETE FROM work_reports;
DELETE FROM health_certificates;
DELETE FROM as_assignments;
DELETE FROM as_requests;
DELETE FROM engineer_service_regions;
DELETE FROM engineer_expert_brands;
DELETE FROM engineer_schedule_slots;
DELETE FROM engineer_schedules;
DELETE FROM engineer_profiles;
DELETE FROM appliances;
DELETE FROM symptoms;
DELETE FROM appliance_categories;
DELETE FROM users;
DELETE FROM agencies;
DELETE FROM regions;
SET REFERENTIAL_INTEGRITY TRUE;
