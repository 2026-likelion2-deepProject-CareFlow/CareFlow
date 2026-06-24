-- A/S 요청 통합 테스트 전용 초기화 (H2 FK 제약 일시 비활성화)
SET REFERENTIAL_INTEGRITY FALSE;
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
