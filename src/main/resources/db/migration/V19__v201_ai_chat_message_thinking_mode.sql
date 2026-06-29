alter table ai_chat_message
  add column thinking_mode varchar(20) not null default 'AUTO';

create index idx_ai_chat_message_thinking_mode on ai_chat_message (thinking_mode);
