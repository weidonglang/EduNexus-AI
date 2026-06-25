create table sensitive_word (
  id bigint auto_increment primary key,
  word varchar(120) not null,
  category varchar(60) not null,
  risk_level varchar(20) not null,
  enabled boolean not null,
  created_at timestamp(6) not null,
  updated_at timestamp(6) not null,
  constraint uk_sensitive_word unique (word)
);

create table content_moderation_log (
  id bigint auto_increment primary key,
  scene varchar(80) not null,
  content_hash varchar(80) not null,
  matched_words varchar(500),
  risk_level varchar(20) not null,
  action varchar(40) not null,
  operator varchar(64),
  trace_id varchar(80),
  created_at timestamp(6) not null
);

create table data_dictionary_table (
  id bigint auto_increment primary key,
  table_name varchar(120) not null,
  display_name varchar(120) not null,
  module varchar(80) not null,
  description varchar(500),
  sensitive_level varchar(20) not null,
  export_allowed boolean not null,
  constraint uk_data_dictionary_table unique (table_name)
);

create table data_dictionary_field (
  id bigint auto_increment primary key,
  table_name varchar(120) not null,
  field_name varchar(120) not null,
  display_name varchar(120) not null,
  description varchar(500),
  is_sensitive boolean not null,
  masking_rule varchar(80),
  export_allowed boolean not null,
  constraint uk_data_dictionary_field unique (table_name, field_name)
);

create table ai_feedback (
  id bigint auto_increment primary key,
  call_log_id bigint,
  username varchar(64) not null,
  rating varchar(30) not null,
  comment varchar(500),
  trace_id varchar(80),
  created_at timestamp(6) not null,
  constraint fk_ai_feedback_call_log foreign key (call_log_id) references ai_call_log (id)
);

create table grade_change_log (
  id bigint auto_increment primary key,
  grade_id bigint not null,
  old_score integer,
  new_score integer,
  reason varchar(500) not null,
  operator varchar(64) not null,
  operator_role varchar(120),
  trace_id varchar(80),
  created_at timestamp(6) not null,
  constraint fk_grade_change_log_grade foreign key (grade_id) references academic_grade (id)
);

create index idx_content_moderation_scene_time on content_moderation_log (scene, created_at);
create index idx_sensitive_word_enabled on sensitive_word (enabled, risk_level);
create index idx_data_dictionary_table_module on data_dictionary_table (module, export_allowed);
create index idx_data_dictionary_field_table on data_dictionary_field (table_name, export_allowed);
create index idx_ai_feedback_call_log on ai_feedback (call_log_id, created_at);
create index idx_grade_change_log_grade on grade_change_log (grade_id, created_at);

create index idx_course_selection_offering_time on course_selection (offering_id, selected_at);
create index idx_academic_grade_student_term on academic_grade (student_id, term);
create index idx_notice_published_at on notice (published_at);
create index idx_audit_log_risk_time on operation_audit_log (risk_level, created_at);

insert into sensitive_word (word, category, risk_level, enabled, created_at, updated_at)
values
  ('示例敏感词A', 'DEMO', 'HIGH', true, current_timestamp, current_timestamp),
  ('示例敏感词B', 'DEMO', 'MEDIUM', true, current_timestamp, current_timestamp);

insert into data_dictionary_table (table_name, display_name, module, description, sensitive_level, export_allowed)
values
  ('sys_user', '系统用户', 'USER', '登录账号与用户状态', 'HIGH', false),
  ('student', '学生档案', 'STUDENT', '学生基础学籍信息', 'MEDIUM', true),
  ('academic_grade', '成绩记录', 'GRADE', '学生课程成绩与绩点', 'HIGH', true),
  ('course_selection', '选课记录', 'COURSE', '学生与教学班的选课关系', 'MEDIUM', true),
  ('operation_audit_log', '操作审计日志', 'AUDIT', '关键操作留痕', 'HIGH', false),
  ('ai_call_log', 'AI 调用日志', 'AI', 'AI 调用状态和失败信息', 'MEDIUM', true);

insert into data_dictionary_field (table_name, field_name, display_name, description, is_sensitive, masking_rule, export_allowed)
values
  ('sys_user', 'password_hash', '密码哈希', '认证敏感字段', true, 'BLOCK', false),
  ('sys_user', 'username', '账号', '登录账号', false, null, true),
  ('student', 'phone', '手机号', '学生联系电话', true, 'PHONE', true),
  ('student', 'email', '邮箱', '学生邮箱', true, 'EMAIL', true),
  ('student', 'address', '地址', '学生联系地址', true, 'PARTIAL', false),
  ('academic_grade', 'score', '成绩', '课程分数', true, 'NONE', true),
  ('academic_grade', 'grade_point', '绩点', '课程绩点', true, 'NONE', true),
  ('operation_audit_log', 'detail', '操作详情', '可能包含业务对象摘要', true, 'PARTIAL', false),
  ('ai_call_log', 'prompt_summary', '提示词摘要', 'AI 输入摘要', true, 'PARTIAL', true);
