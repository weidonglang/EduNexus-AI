alter table ai_model_registry
  add column deleted boolean not null default false;

alter table ai_model_registry
  add column deleted_at timestamp(6);

alter table ai_model_registry
  add column deleted_by varchar(80);

create index idx_ai_model_registry_deleted_type on ai_model_registry (deleted, model_type, enabled, is_default);
