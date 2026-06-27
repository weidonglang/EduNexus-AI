package weidonglang.tianshiwebside.permission;

import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import weidonglang.tianshiwebside.common.api.ApiResponse;
import weidonglang.tianshiwebside.common.cache.QueryCacheService;
import weidonglang.tianshiwebside.permission.mapper.MenuMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/menus")
public class MenuController {
    private final MenuMapper menuMapper;
    private final SysMenuRepository menuRepository;
    private final QueryCacheService queryCacheService;

    public MenuController(MenuMapper menuMapper, SysMenuRepository menuRepository, QueryCacheService queryCacheService) {
        this.menuMapper = menuMapper;
        this.menuRepository = menuRepository;
        this.queryCacheService = queryCacheService;
    }

    @GetMapping
    /**
     * 功能：查询当前登录用户可访问的菜单。
     * 说明：后端根据用户角色查询菜单权限并组装成树形结构返回前端，
     * 前端侧边栏据此动态展示学生、教师或教务管理菜单。
     */
    public ApiResponse<List<MenuItemResponse>> menus(Authentication authentication) {
        List<String> roleCodes = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring("ROLE_".length()))
                .sorted()
                .toList();
        return ApiResponse.success(queryCacheService.get(
                "query:menus:" + String.join(",", roleCodes),
                Duration.ofMinutes(5),
                new TypeReference<List<MenuItemResponse>>() {
                },
                () -> buildMenus(roleCodes)
        ));
    }

    private List<MenuItemResponse> buildMenus(List<String> roleCodes) {
        List<MenuMapper.MenuRow> menus;
        try {
            menus = roleCodes.isEmpty()
                    ? List.of()
                    : menuMapper.findByRoleCodes(roleCodes);
        } catch (RuntimeException ignored) {
            Set<String> allowedCodes = fallbackMenuCodes(roleCodes);
            menus = menuRepository.findAllByOrderBySortOrderAsc().stream()
                    .filter(menu -> allowedCodes.contains(menu.getCode()))
                    .map(menu -> new MenuMapper.MenuRow(
                            menu.getCode(),
                            menu.getTitle(),
                            menu.getPath(),
                            menu.getIcon(),
                            menu.getParentCode()
                    ))
                    .toList();
        }
        Map<String, MutableMenuItem> items = new LinkedHashMap<>();

        for (MenuMapper.MenuRow menu : menus) {
            items.put(menu.code(), MutableMenuItem.from(menu));
        }

        List<MutableMenuItem> roots = new ArrayList<>();
        for (MutableMenuItem item : items.values()) {
            if (item.parentCode() == null || item.parentCode().isBlank()) {
                roots.add(item);
            } else {
                MutableMenuItem parent = items.get(item.parentCode());
                if (parent != null) {
                    parent.children().add(item);
                }
            }
        }

        return roots.stream().map(MutableMenuItem::toResponse).toList();
    }

    /**
     * 功能：菜单数据异常时提供本地兜底菜单。
     * 说明：当数据库菜单表或权限查询临时异常时，根据角色返回基础菜单，
     * 保证答辩演示时页面仍可进入，不影响后端接口权限控制。
     */
    private Set<String> fallbackMenuCodes(List<String> roleCodes) {
        Set<String> codes = new HashSet<>();
        codes.add("dashboard");
        codes.add("ai-assistant");
        codes.add("ai-chat");
        if (roleCodes.contains("ADMIN")) {
            codes.addAll(List.of(
                    "admin",
                    "admin-classes",
                    "admin-course-offerings",
                    "admin-status-changes",
                    "admin-registration-applications",
                    "admin-role-permissions",
                    "admin-permission-matrix",
                    "admin-users",
                    "admin-evaluations",
                    "admin-grades",
                    "admin-exams",
                    "admin-notices",
                    "admin-files",
                    "admin-audit-logs",
                    "admin-batch-tasks",
                    "admin-data-archive",
                    "admin-system-health",
                    "admin-redis-monitor",
                    "admin-course-selection-consistency",
                    "admin-data-dictionary",
                    "admin-sensitive-words",
                    "admin-load-test-reports",
                    "admin-database-browser",
                    "admin-ai-sql",
                    "admin-ai-logs",
                    "admin-ai-models"
            ));
        }
        if (roleCodes.contains("TEACHER")) {
            codes.addAll(List.of(
                    "teacher",
                    "teacher-offerings",
                    "teacher-homeroom-classes",
                    "teacher-grades",
                    "teacher-exams",
                    "teacher-evaluations"
            ));
        }
        if (roleCodes.contains("STUDENT")) {
            codes.addAll(List.of(
                    "student",
                    "student-profile",
                    "student-class",
                    "student-status-change",
                    "registration",
                    "registration-minor",
                    "registration-retake",
                    "registration-credit-internal",
                    "registration-credit-external",
                    "registration-score-bonus",
                    "registration-stream-confirm",
                    "registration-direction-confirm",
                    "course",
                    "course-selection",
                    "schedule-personal",
                    "classroom-free",
                    "info-query",
                    "info-warning",
                    "info-graduation-audit",
                    "info-class-schedule",
                    "info-roster",
                    "info-academic-progress",
                    "ai-academic-profile",
                    "info-teaching-plan",
                    "grade",
                    "grade-query",
                    "exam-query",
                    "evaluation",
                    "teaching-feedback",
                    "graduation-design",
                    "thesis-grade"
            ));
        }
        return codes;
    }

    public record MenuItemResponse(
            String code,
            String title,
            String path,
            String icon,
            List<MenuItemResponse> children
    ) {
    }

    private record MutableMenuItem(
            String code,
            String title,
            String path,
            String icon,
            String parentCode,
            List<MutableMenuItem> children
    ) {
        static MutableMenuItem from(MenuMapper.MenuRow menu) {
            return new MutableMenuItem(
                    menu.code(),
                    menu.title(),
                    menu.path(),
                    menu.icon(),
                    menu.parentCode(),
                    new ArrayList<>()
            );
        }

        MenuItemResponse toResponse() {
            return new MenuItemResponse(
                    code,
                    title,
                    path,
                    icon,
                    children.stream().map(MutableMenuItem::toResponse).toList()
            );
        }
    }
}
