-- V8__tighten_property_location.sql
-- Requires district, address, latitude, and longitude on every property.
-- Safe because the properties table is empty in all environments.

ALTER TABLE properties
    ALTER COLUMN district  SET NOT NULL,
    ALTER COLUMN address   SET NOT NULL,
    ALTER COLUMN latitude  SET NOT NULL,
    ALTER COLUMN longitude SET NOT NULL;
