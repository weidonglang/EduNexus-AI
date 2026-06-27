alter table ai_call_log
  add column service_mode varchar(120);

alter table ai_call_log
  add column level varchar(20);

alter table ai_call_log
  add column trace_id varchar(64);

alter table ai_call_log
  add column session_id bigint;

alter table ai_call_log
  add column model_id bigint;

create index idx_ai_call_log_level_created_at on ai_call_log (level, created_at);
create index idx_ai_call_log_username_created_at on ai_call_log (username, created_at);
create index idx_ai_call_log_success_created_at on ai_call_log (success, created_at);
create index idx_ai_call_log_trace_id on ai_call_log (trace_id);

update ai_call_log
set service_mode = model_name,
    level = case
      when success = false then 'ERROR'
      when lower(coalesce(model_name, '')) like '%fallback%'
        or lower(coalesce(model_name, '')) like '%degraded%'
        or lower(coalesce(model_name, '')) like '%local%' then 'WARN'
      when error_message is not null and trim(error_message) <> '' then 'WARN'
      else 'INFO'
    end
where level is null;

create table ai_chat_session (
  id bigint auto_increment primary key,
  owner_username varchar(64) not null,
  title varchar(160) not null,
  model_id bigint,
  model_name varchar(180),
  created_at timestamp(6) not null,
  updated_at timestamp(6) not null,
  deleted boolean not null default false,
  constraint fk_ai_chat_session_model foreign key (model_id) references ai_model_registry (id)
);

create table ai_chat_message (
  id bigint auto_increment primary key,
  session_id bigint not null,
  role varchar(20) not null,
  content varchar(4000) not null,
  service_mode varchar(120),
  model_name varchar(180),
  search_used boolean not null default false,
  created_at timestamp(6) not null,
  constraint fk_ai_chat_message_session foreign key (session_id) references ai_chat_session (id)
);

create index idx_ai_chat_session_owner_updated on ai_chat_session (owner_username, deleted, updated_at);
create index idx_ai_chat_message_session_time on ai_chat_message (session_id, created_at);

create table batch_task (
  id bigint auto_increment primary key,
  task_type varchar(80) not null,
  operator varchar(64) not null,
  status varchar(40) not null,
  success_count integer not null default 0,
  failure_count integer not null default 0,
  failure_detail varchar(2000),
  report_path varchar(300),
  started_at timestamp(6) not null,
  ended_at timestamp(6)
);

create table data_archive_record (
  id bigint auto_increment primary key,
  object_type varchar(80) not null,
  term varchar(80),
  action varchar(40) not null,
  dry_run boolean not null,
  affected_count integer not null,
  operator varchar(64) not null,
  detail varchar(1000),
  created_at timestamp(6) not null
);

create index idx_batch_task_type_time on batch_task (task_type, started_at);
create index idx_data_archive_object_time on data_archive_record (object_type, created_at);
