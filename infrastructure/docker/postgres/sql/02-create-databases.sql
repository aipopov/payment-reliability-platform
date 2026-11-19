CREATE ROLE payment_user LOGIN PASSWORD :'payment_password';
CREATE ROLE ledger_user LOGIN PASSWORD :'ledger_password';
CREATE ROLE provider_user LOGIN PASSWORD :'provider_password';
CREATE ROLE reconciliation_user LOGIN PASSWORD :'reconciliation_password';

CREATE DATABASE payment_db OWNER payment_user;
CREATE DATABASE ledger_db OWNER ledger_user;
CREATE DATABASE provider_db OWNER provider_user;
CREATE DATABASE reconciliation_db OWNER reconciliation_user;