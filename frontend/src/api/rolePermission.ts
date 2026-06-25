import { http, type ApiResponse } from './http'

// 角色权限管理 API。
// 管理端通过这里读取角色、菜单树并保存角色菜单关系，最终影响不同账号登录后的菜单范围。
export interface RolePermissionRole {
  roleId: number
  code: string
  name: string
}

export interface RolePermissionMenu {
  menuId: number
  code: string
  title: string
  path: string
  icon: string
  parentCode?: string
  sortOrder: number
}

export function rolePermissionRolesApi() {
  return http.get<never, ApiResponse<RolePermissionRole[]>>('/admin/role-permissions/roles')
}

export function rolePermissionMenusApi() {
  return http.get<never, ApiResponse<RolePermissionMenu[]>>('/admin/role-permissions/menus')
}

export function rolePermissionMenuCodesApi(roleId: number) {
  return http.get<never, ApiResponse<string[]>>(`/admin/role-permissions/roles/${roleId}/menus`)
}

export function updateRolePermissionMenusApi(roleId: number, menuCodes: string[]) {
  return http.put<never, ApiResponse<string[]>>(`/admin/role-permissions/roles/${roleId}/menus`, {
    menuCodes,
  })
}
