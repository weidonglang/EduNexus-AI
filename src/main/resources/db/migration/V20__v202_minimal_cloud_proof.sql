create table if not exists cloud_tx_demo_main (
    id bigint primary key auto_increment,
    tx_no varchar(64) not null unique,
    remark varchar(255) not null,
    created_at timestamp not null default current_timestamp
);

create table if not exists cloud_tx_demo_ai (
    id bigint primary key auto_increment,
    tx_no varchar(64) not null unique,
    remark varchar(255) not null,
    created_at timestamp not null default current_timestamp
);

create table if not exists undo_log (
    branch_id bigint not null,
    xid varchar(128) not null,
    context varchar(128) not null,
    rollback_info blob not null,
    log_status int not null,
    log_created timestamp not null,
    log_modified timestamp not null,
    constraint ux_undo_log unique (xid, branch_id)
);
