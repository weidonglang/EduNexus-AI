alter table academic_class
  add column homeroom_teacher_user_id bigint;

alter table academic_class
  add constraint fk_academic_class_homeroom_teacher
  foreign key (homeroom_teacher_user_id) references sys_user (id);

create index idx_academic_class_homeroom_teacher on academic_class (homeroom_teacher_user_id);

create table ai_safety_config (
  id bigint auto_increment primary key,
  scene varchar(80) not null,
  enabled boolean not null,
  strategy varchar(20) not null,
  description varchar(300),
  updated_at timestamp(6) not null,
  constraint uk_ai_safety_config_scene unique (scene)
);

insert into ai_safety_config (scene, enabled, strategy, description, updated_at)
values
  ('SENSITIVE_WORD', true, 'block', '通用敏感词检测，命中高风险内容时阻断提交。', current_timestamp),
  ('AI_INPUT', true, 'block', 'AI 输入提示词检测，禁止将个人数据、密钥、成绩批量查询等敏感内容送入模型。', current_timestamp),
  ('AI_OUTPUT', true, 'warn', 'AI 输出结果检测，命中后记录告警并保留审计线索。', current_timestamp),
  ('SEARCH_RESULT', true, 'block', '联网搜索结果检测，避免把敏感或越权内容展示给用户。', current_timestamp),
  ('STUDENT_CONTENT', true, 'block', '学生提交的评价、申请、学籍异动等文本检测。', current_timestamp),
  ('NOTICE', true, 'block', '公告标题与正文发布前检测。', current_timestamp);
