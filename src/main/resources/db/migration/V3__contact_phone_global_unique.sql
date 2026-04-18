-- V3__contact_phone_global_unique.sql
-- Make contacts.phone_number globally unique so phone-based OTP login can
-- look up a user by phone without ambiguity. De-dupe defensively first,
-- keeping the oldest row per number.

DELETE FROM contacts c
 USING contacts older
 WHERE c.phone_number = older.phone_number
   AND c.id > older.id;

ALTER TABLE contacts
    ADD CONSTRAINT uk_contact_phone_number UNIQUE (phone_number);
