# Academic-Nexus 压测面板说明

压测面板脚本：

```text
scripts/course_grab_panel.py
```

启动入口：

```powershell
.\start-load-panel.bat
```

## 后端 API 模式

默认推荐模式。面板登录管理员账号后调用：

```text
GET /api/admin/course-offerings
```

优点：

- 不依赖本机 `mysql.exe`。
- 与后端权限、分页和业务字段保持一致。
- 更适合普通演示、答辩和跨机器运行。

如果接口失败，面板会区分提示：

```text
后端 API 未启动、token / 账号权限错误、接口返回异常
```

## MySQL 直连模式

高级排查模式。仅当需要绕过后端验证数据库状态时使用。

配置项：

```text
MYSQL_EXE
DB_HOST，默认 localhost
DB_PORT，默认 3306
DB_USERNAME
DB_PASSWORD
DB_DATABASE
```

查找 `mysql.exe` 的顺序：

1. `MYSQL_EXE` 环境变量
2. `shutil.which("mysql")`
3. Windows 默认路径 `C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe`

命令会使用：

```text
-h -P -u -p -D
```

密码为空时不会拼出错误的 `-p` 参数。

常见错误会明确提示：

```text
mysql.exe 未找到
数据库连接失败
账号密码错误
数据库不存在
表不存在
```

## 报告

压测报告输出到：

```text
reports/
```

管理端可在：

```text
/admin/load-test-reports
```

查看历史 HTML/JSON 报告。
