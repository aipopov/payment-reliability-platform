-- Account class and normal side are derived from AccountType in Java.
-- Keeping them in the database would duplicate domain information.

ALTER TABLE accounts DROP COLUMN account_class, DROP COLUMN normal_side;