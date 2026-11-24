alter table idempotency_records
    drop column status;
alter table idempotency_records
    alter column request_hash type varchar(64);