alter table operation_audit_log add column module varchar(80);
alter table operation_audit_log add column risk_level varchar(20) not null default 'LOW';
alter table operation_audit_log add column success_flag boolean not null default true;
alter table operation_audit_log add column failure_reason varchar(500);

create index idx_audit_log_module_risk_time on operation_audit_log (module, risk_level, created_at);
create index idx_audit_log_operator_time on operation_audit_log (operator, created_at);
