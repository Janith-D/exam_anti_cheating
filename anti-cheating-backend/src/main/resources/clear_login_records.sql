-- SQL script to clear all previous login records
-- Run this against the anti-cheating database

-- Clear all identity sessions (login sessions)
TRUNCATE TABLE identity_session CASCADE;
DELETE FROM identity_session;

-- Clear all identity attempts
TRUNCATE TABLE identity_attempt CASCADE;
DELETE FROM identity_attempt;

-- Clear all identity enrollment sessions
TRUNCATE TABLE identity_enrollment_session CASCADE;
DELETE FROM identity_enrollment_session;

-- Log the operation
INSERT INTO audit_log (action, timestamp) VALUES ('CLEARED_ALL_LOGIN_RECORDS', NOW());

-- Verify
SELECT 
    (SELECT COUNT(*) FROM identity_session) as identity_session_count,
    (SELECT COUNT(*) FROM identity_attempt) as identity_attempt_count,
    (SELECT COUNT(*) FROM identity_enrollment_session) as enrollment_session_count;
