create table academic_class (
  id bigint auto_increment primary key,
  college varchar(80) not null,
  major varchar(80) not null,
  grade varchar(20) not null,
  class_name varchar(80) not null,
  advisor varchar(80),
  created_at timestamp(6) not null,
  updated_at timestamp(6) not null,
  constraint uk_academic_class_name unique (class_name)
);

create index idx_academic_class_college_major on academic_class (college, major, grade);
