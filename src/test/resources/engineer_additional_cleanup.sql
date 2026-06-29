-- 대행사/기사 추가 API 통합 테스트용 초기화 SQL
-- settlements, reviews, lms_confirmations, lms_contents 포함 전체 초기화
SET REFERENTIAL_INTEGRITY FALSE;
DELETE FROM lms_confirmations;
DELETE FROM lms_contents;
DELETE FROM settlements;
DELETE FROM payments;
DELETE FROM reviews;
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
