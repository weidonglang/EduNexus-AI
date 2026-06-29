update ai_model_registry
set is_default = false
where deleted = true;

update ai_model_registry
set is_default = false
where enabled = false;

update ai_model_registry
set is_default = false
where is_default = true
  and id not in (
    select keep_id
    from (
      select min(id) as keep_id
      from ai_model_registry
      where deleted = false
        and enabled = true
        and is_default = true
      group by model_type
    ) kept_defaults
  );
