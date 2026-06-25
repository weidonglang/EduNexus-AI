package weidonglang.tianshiwebside.permission.mapper;

import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface RolePermissionMapper {
    @Select("""
            select
              id as role_id,
              code as code,
              name as name
            from sys_role
            order by code asc
            """)
    List<RoleRow> findRoles();

    @Select("""
            select
              id as menu_id,
              code as code,
              title as title,
              path as path,
              icon as icon,
              parent_code as parent_code,
              sort_order as sort_order
            from sys_menu
            order by sort_order asc
            """)
    List<MenuPermissionRow> findMenus();

    @Select("""
            select count(*)
            from sys_role
            where id = #{roleId}
            """)
    int countRoleById(@Param("roleId") Long roleId);

    @Select("""
            select count(*)
            from sys_menu
            where code = #{menuCode}
            """)
    int countMenuByCode(@Param("menuCode") String menuCode);

    @Select("""
            select m.code
            from sys_menu m
            join sys_role_menu rm on rm.menu_id = m.id
            where rm.role_id = #{roleId}
            order by m.sort_order asc
            """)
    List<String> findMenuCodesByRoleId(@Param("roleId") Long roleId);

    @Delete("""
            delete from sys_role_menu
            where role_id = #{roleId}
            """)
    int deleteRoleMenus(@Param("roleId") Long roleId);

    @Insert("""
            insert into sys_role_menu (role_id, menu_id)
            select #{roleId}, id
            from sys_menu
            where code = #{menuCode}
            """)
    int insertRoleMenu(@Param("roleId") Long roleId, @Param("menuCode") String menuCode);

    record RoleRow(
            Long roleId,
            String code,
            String name
    ) {
    }

    record MenuPermissionRow(
            Long menuId,
            String code,
            String title,
            String path,
            String icon,
            String parentCode,
            Integer sortOrder
    ) {
    }
}
