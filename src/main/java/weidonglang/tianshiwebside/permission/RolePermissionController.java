package weidonglang.tianshiwebside.permission;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import weidonglang.tianshiwebside.audit.AuditLogService;
import weidonglang.tianshiwebside.common.api.ApiResponse;
import weidonglang.tianshiwebside.common.cache.QueryCacheService;
import weidonglang.tianshiwebside.common.error.BusinessException;
import weidonglang.tianshiwebside.common.error.ErrorCode;
import weidonglang.tianshiwebside.permission.mapper.RolePermissionMapper;

import java.util.List;

@RestController
@RequestMapping("/api/admin/role-permissions")
public class RolePermissionController {
    private final RolePermissionMapper rolePermissionMapper;
    private final AuditLogService auditLogService;
    private final QueryCacheService queryCacheService;

    public RolePermissionController(
            RolePermissionMapper rolePermissionMapper,
            AuditLogService auditLogService,
            QueryCacheService queryCacheService
    ) {
        this.rolePermissionMapper = rolePermissionMapper;
        this.auditLogService = auditLogService;
        this.queryCacheService = queryCacheService;
    }

    @GetMapping("/roles")
    /**
     * 功能：查询系统角色列表。
     * 说明：管理端角色权限页面加载时调用，返回 ADMIN、TEACHER、STUDENT 等角色，
     * 供管理员选择后维护对应菜单权限。
     */
    public ApiResponse<List<RolePermissionMapper.RoleRow>> roles() {
        return ApiResponse.success(rolePermissionMapper.findRoles());
    }

    @GetMapping("/menus")
    /**
     * 功能：查询可分配的菜单权限。
     * 说明：返回系统全部菜单节点，前端以权限树形式展示，管理员勾选后保存到角色菜单关系表。
     */
    public ApiResponse<List<RolePermissionMapper.MenuPermissionRow>> menus() {
        return ApiResponse.success(rolePermissionMapper.findMenus());
    }

    @GetMapping("/roles/{roleId}/menus")
    public ApiResponse<List<String>> roleMenus(@PathVariable Long roleId) {
        ensureRoleExists(roleId);
        return ApiResponse.success(rolePermissionMapper.findMenuCodesByRoleId(roleId));
    }

    @PutMapping("/roles/{roleId}/menus")
    @Transactional
    @PreAuthorize("hasAuthority('ROLE_PERMISSION_WRITE')")
    /**
     * 功能：给角色分配菜单权限。
     * 说明：管理员提交角色对应的菜单 code 列表，后端先校验角色和菜单是否存在，
     * 再重建角色菜单关系；用户下次登录或刷新菜单时即可看到新的菜单范围。
     */
    public ApiResponse<List<String>> updateRoleMenus(
            Authentication authentication,
            @PathVariable Long roleId,
            @Valid @RequestBody UpdateRoleMenusRequest request
    ) {
        ensureRoleExists(roleId);
        for (String menuCode : request.menuCodes()) {
            if (rolePermissionMapper.countMenuByCode(menuCode) == 0) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "菜单不存在: " + menuCode);
            }
        }
        rolePermissionMapper.deleteRoleMenus(roleId);
        request.menuCodes().stream()
                .distinct()
                .forEach(menuCode -> rolePermissionMapper.insertRoleMenu(roleId, menuCode));
        auditLogService.record(authentication.getName(), "UPDATE_ROLE_MENUS", "ROLE", roleId, String.join(",", request.menuCodes()), null);
        queryCacheService.evictByPrefix("query:menus:");
        return ApiResponse.success(rolePermissionMapper.findMenuCodesByRoleId(roleId));
    }

    private void ensureRoleExists(Long roleId) {
        if (rolePermissionMapper.countRoleById(roleId) == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "角色不存在");
        }
    }

    public record UpdateRoleMenusRequest(
            @NotNull List<String> menuCodes
    ) {
    }
}
