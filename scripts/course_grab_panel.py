import os
import queue
import json
import shutil
import subprocess
import threading
import tkinter as tk
from pathlib import Path
from tkinter import messagebox, ttk
from urllib import error, parse, request


ROOT = Path(__file__).resolve().parents[1]
NODE_SCRIPT = ROOT / "scripts" / "course-grab-load-test.js"
WINDOWS_MYSQL_EXE = r"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"


class CourseGrabPanel(tk.Tk):
    def __init__(self):
        super().__init__()
        self.title("智慧选课压测面板")
        self.geometry("1180x760")
        self.minsize(980, 620)
        self.output_queue = queue.Queue()
        self.process = None
        self.selected_ids = set()

        self.vars = {
            "base_url": tk.StringVar(value="http://localhost:8080"),
            "load_users": tk.StringVar(value="10000"),
            "requests": tk.StringVar(value="10000"),
            "concurrency": tk.StringVar(value="500"),
            "login_concurrency": tk.StringVar(value="200"),
            "account_batch_size": tk.StringVar(value="500"),
            "password": tk.StringVar(value=os.environ.get("PASSWORD", "")),
            "admin_password": tk.StringVar(value=os.environ.get("ADMIN_PASSWORD", "")),
            "cleanup_username": tk.StringVar(value=os.environ.get("CLEANUP_USERNAME", "")),
            "redis_host": tk.StringVar(value="localhost"),
            "redis_port": tk.StringVar(value="6379"),
            "course_source": tk.StringVar(value=os.environ.get("COURSE_SOURCE", "backend-api")),
            "mysql_host": tk.StringVar(value=os.environ.get("DB_HOST", "localhost")),
            "mysql_port": tk.StringVar(value=os.environ.get("DB_PORT", "3306")),
            "mysql_user": tk.StringVar(value=os.environ.get("DB_USERNAME", "root")),
            "mysql_password": tk.StringVar(value=os.environ.get("DB_PASSWORD", "")),
            "mysql_database": tk.StringVar(value=os.environ.get("DB_DATABASE", "tianshiwebside")),
            "smart_mode": tk.StringVar(value="random"),
            "capacity_value": tk.StringVar(value="100"),
            "smart_switch": tk.BooleanVar(value=True),
            "cleanup": tk.BooleanVar(value=True),
            "redis_enabled": tk.BooleanVar(value=True),
        }

        self._build_layout()
        self.after(100, self._drain_output)

    def _build_layout(self):
        self.columnconfigure(0, weight=2)
        self.columnconfigure(1, weight=3)
        self.rowconfigure(1, weight=1)

        top = ttk.Frame(self, padding=10)
        top.grid(row=0, column=0, columnspan=2, sticky="ew")
        top.columnconfigure(1, weight=1)
        ttk.Label(top, text="后端地址").grid(row=0, column=0, sticky="w")
        ttk.Entry(top, textvariable=self.vars["base_url"]).grid(row=0, column=1, sticky="ew", padx=8)
        ttk.Button(top, text="刷新课程", command=self.refresh_courses).grid(row=0, column=2, padx=4)
        ttk.Button(top, text="全选当前列表", command=self.select_all_visible).grid(row=0, column=3, padx=4)
        ttk.Button(top, text="清空选择", command=self.clear_selection).grid(row=0, column=4, padx=4)
        ttk.Button(top, text="万人随机预设", command=self.apply_10k_random_preset).grid(row=0, column=5, padx=4)
        ttk.Button(top, text="单人测试预设", command=self.apply_smoke_preset).grid(row=0, column=6, padx=4)

        left = ttk.Frame(self, padding=(10, 0, 5, 10))
        left.grid(row=1, column=0, sticky="nsew")
        left.rowconfigure(1, weight=1)
        left.columnconfigure(0, weight=1)

        self.filter_var = tk.StringVar()
        filter_frame = ttk.Frame(left)
        filter_frame.grid(row=0, column=0, sticky="ew", pady=(0, 6))
        filter_frame.columnconfigure(1, weight=1)
        ttk.Label(filter_frame, text="筛选").grid(row=0, column=0, sticky="w")
        filter_entry = ttk.Entry(filter_frame, textvariable=self.filter_var)
        filter_entry.grid(row=0, column=1, sticky="ew", padx=6)
        filter_entry.bind("<KeyRelease>", lambda _event: self.apply_filter())

        columns = ("id", "code", "name", "teacher", "capacity", "selected", "remaining", "time", "room")
        self.course_table = ttk.Treeview(left, columns=columns, show="headings", selectmode="extended")
        headings = {
            "id": "教学班ID",
            "code": "课程代码",
            "name": "课程名称",
            "teacher": "教师",
            "capacity": "容量",
            "selected": "已选",
            "remaining": "剩余",
            "time": "上课时间",
            "room": "教室",
        }
        widths = {
            "id": 54,
            "code": 74,
            "name": 210,
            "teacher": 76,
            "capacity": 56,
            "selected": 72,
            "remaining": 56,
            "time": 100,
            "room": 110,
        }
        for column in columns:
            self.course_table.heading(column, text=headings[column])
            self.course_table.column(column, width=widths[column], anchor="w")
        self.course_table.grid(row=1, column=0, sticky="nsew")
        self.course_table.bind("<Double-1>", lambda _event: self.toggle_selected_rows())
        self.course_table.bind("<space>", lambda _event: self.toggle_selected_rows())

        scroll = ttk.Scrollbar(left, orient="vertical", command=self.course_table.yview)
        scroll.grid(row=1, column=1, sticky="ns")
        self.course_table.configure(yscrollcommand=scroll.set)

        self.selection_label = ttk.Label(left, text="已选教学班：无")
        self.selection_label.grid(row=2, column=0, columnspan=2, sticky="ew", pady=(6, 0))

        right = ttk.Frame(self, padding=(5, 0, 10, 10))
        right.grid(row=1, column=1, sticky="nsew")
        right.rowconfigure(4, weight=1)
        right.columnconfigure(0, weight=1)

        settings = ttk.LabelFrame(right, text="压测参数", padding=10)
        settings.grid(row=0, column=0, sticky="ew")
        for col in range(6):
            settings.columnconfigure(col, weight=1)

        self._field(settings, "学生人数", "load_users", 0, 0)
        self._field(settings, "请求次数", "requests", 0, 2)
        self._field(settings, "并发数", "concurrency", 0, 4)
        self._field(settings, "登录并发", "login_concurrency", 1, 0)
        self._field(settings, "建号批量", "account_batch_size", 1, 2)
        self._field(settings, "清理账号", "cleanup_username", 1, 4)
        self._field(settings, "学生密码", "password", 2, 0, show="*")
        self._field(settings, "管理员密码", "admin_password", 2, 2, show="*")
        self._field(settings, "Redis地址", "redis_host", 2, 4)
        self._field(settings, "Redis端口", "redis_port", 3, 0)
        self._field(settings, "数据库用户", "mysql_user", 3, 2)
        self._field(settings, "数据库密码", "mysql_password", 3, 4, show="*")

        ttk.Label(settings, text="选课模式").grid(row=4, column=0, sticky="w", pady=(8, 0))
        mode_box = ttk.Combobox(
            settings,
            textvariable=self.vars["smart_mode"],
            values=("random", "random-retry", "sequential", "single"),
            state="readonly",
            width=14,
        )
        mode_box.grid(row=4, column=1, sticky="ew", padx=(4, 12), pady=(8, 0))
        ttk.Checkbutton(settings, text="压测后自动清理", variable=self.vars["cleanup"]).grid(row=4, column=2, sticky="w", pady=(8, 0))
        ttk.Checkbutton(settings, text="开启Redis缓存", variable=self.vars["redis_enabled"]).grid(row=4, column=3, sticky="w", pady=(8, 0))
        ttk.Button(settings, text="开始压测", command=self.run_load_test).grid(row=4, column=4, sticky="ew", padx=4, pady=(8, 0))
        ttk.Button(settings, text="停止", command=self.stop_process).grid(row=4, column=5, sticky="ew", padx=4, pady=(8, 0))

        db_tools = ttk.LabelFrame(right, text="课程数据维护", padding=10)
        db_tools.grid(row=1, column=0, sticky="ew", pady=(8, 8))
        for col in range(6):
            db_tools.columnconfigure(col, weight=1)
        ttk.Label(db_tools, text="刷新模式").grid(row=0, column=0, sticky="w")
        ttk.Combobox(
            db_tools,
            textvariable=self.vars["course_source"],
            values=("backend-api", "mysql-direct"),
            state="readonly",
            width=14,
        ).grid(row=0, column=1, sticky="ew", padx=(4, 12))
        ttk.Label(db_tools, text="MySQL").grid(row=0, column=2, sticky="w")
        ttk.Entry(db_tools, textvariable=self.vars["mysql_host"], width=12).grid(row=0, column=3, sticky="ew", padx=4)
        ttk.Entry(db_tools, textvariable=self.vars["mysql_port"], width=8).grid(row=0, column=4, sticky="ew", padx=4)
        ttk.Entry(db_tools, textvariable=self.vars["mysql_database"], width=12).grid(row=0, column=5, sticky="ew", padx=4)
        ttk.Label(db_tools, text="课程容量").grid(row=1, column=0, sticky="w", pady=(8, 0))
        ttk.Entry(db_tools, textvariable=self.vars["capacity_value"], width=10).grid(row=1, column=1, sticky="ew", padx=(4, 12), pady=(8, 0))
        ttk.Button(db_tools, text="修改所选容量", command=self.set_selected_capacity).grid(row=1, column=2, sticky="ew", padx=4, pady=(8, 0))
        ttk.Button(db_tools, text="清空所选记录", command=self.clear_selected_records).grid(row=1, column=3, sticky="ew", padx=4, pady=(8, 0))
        ttk.Button(db_tools, text="清空Redis库存", command=self.clear_selected_redis_stock).grid(row=1, column=4, sticky="ew", padx=4, pady=(8, 0))
        ttk.Button(db_tools, text="刷新", command=self.refresh_courses).grid(row=1, column=5, sticky="ew", padx=4, pady=(8, 0))

        help_box = ttk.LabelFrame(right, text="模式说明", padding=10)
        help_box.grid(row=2, column=0, sticky="ew", pady=(0, 8))
        self.mode_label = ttk.Label(help_box, text="random：每个学生随机抢一门所选课程；random-retry：随机顺序抢，失败后继续换课；sequential：按固定顺序换课；single：轮流分配课程，不失败重试。")
        self.mode_label.grid(row=0, column=0, sticky="ew")

        redis_box = ttk.LabelFrame(right, text="Redis缓存查看", padding=8)
        redis_box.grid(row=3, column=0, sticky="ew", pady=(0, 8))
        redis_box.columnconfigure(0, weight=1)
        redis_top = ttk.Frame(redis_box)
        redis_top.grid(row=0, column=0, sticky="ew", pady=(0, 6))
        redis_top.columnconfigure(1, weight=1)
        ttk.Label(redis_top, text="键名模式").grid(row=0, column=0, sticky="w")
        self.redis_pattern_var = tk.StringVar(value="selection:*")
        ttk.Entry(redis_top, textvariable=self.redis_pattern_var).grid(row=0, column=1, sticky="ew", padx=6)
        ttk.Button(redis_top, text="刷新Redis缓存", command=self.refresh_redis_cache).grid(row=0, column=2, padx=4)
        ttk.Button(redis_top, text="清空列表", command=self.clear_redis_table).grid(row=0, column=3, padx=4)

        redis_columns = ("key", "ttl", "value")
        self.redis_table = ttk.Treeview(redis_box, columns=redis_columns, show="headings", height=5)
        self.redis_table.heading("key", text="缓存键")
        self.redis_table.heading("ttl", text="剩余秒数")
        self.redis_table.heading("value", text="缓存值")
        self.redis_table.column("key", width=310, anchor="w")
        self.redis_table.column("ttl", width=80, anchor="center")
        self.redis_table.column("value", width=220, anchor="w")
        self.redis_table.grid(row=1, column=0, sticky="ew")

        output_frame = ttk.LabelFrame(right, text="运行输出", padding=8)
        output_frame.grid(row=4, column=0, sticky="nsew")
        output_frame.rowconfigure(0, weight=1)
        output_frame.columnconfigure(0, weight=1)
        self.output = tk.Text(output_frame, wrap="word", height=20)
        self.output.grid(row=0, column=0, sticky="nsew")
        output_scroll = ttk.Scrollbar(output_frame, orient="vertical", command=self.output.yview)
        output_scroll.grid(row=0, column=1, sticky="ns")
        self.output.configure(yscrollcommand=output_scroll.set)

    def _field(self, parent, label, key, row, col, show=None):
        ttk.Label(parent, text=label).grid(row=row, column=col, sticky="w", pady=3)
        ttk.Entry(parent, textvariable=self.vars[key], show=show).grid(row=row, column=col + 1, sticky="ew", padx=(4, 12), pady=3)

    def refresh_courses(self):
        if self.vars["course_source"].get() == "mysql-direct":
            self._append_output("正在通过 MySQL 直连刷新课程教学班列表...\n")
        else:
            self._append_output("正在通过后端 API 刷新课程教学班列表...\n")
        threading.Thread(target=self._load_courses_worker, daemon=True).start()

    def _load_courses_worker(self):
        try:
            rows = self._query_offerings()
            self.all_rows = rows
            self.output_queue.put(("courses", rows))
            self.output_queue.put(("text", f"已加载 {len(rows)} 个课程教学班。\n"))
        except Exception as exc:
            self.output_queue.put(("text", f"课程加载失败：{exc}\n"))

    def _query_offerings(self):
        if self.vars["course_source"].get() != "mysql-direct":
            return self._query_offerings_from_api()
        mysql_exe = self._resolve_mysql_exe()
        sql = (
            "select co.id, c.code, c.name, co.teacher_name, co.capacity, "
            "count(cs.id), co.capacity - count(cs.id), co.schedule_text, co.classroom "
            "from course_offering co "
            "join course c on c.id = co.course_id "
            "left join course_selection cs on cs.offering_id = co.id "
            "group by co.id, c.code, c.name, co.teacher_name, co.capacity, co.schedule_text, co.classroom "
            "order by co.id"
        )
        cmd = [
            mysql_exe,
            "-h",
            self.vars["mysql_host"].get().strip() or "localhost",
            "-P",
            self.vars["mysql_port"].get().strip() or "3306",
            "-u",
            self.vars["mysql_user"].get().strip() or "root",
            "-D",
            self.vars["mysql_database"].get().strip() or "tianshiwebside",
            "--default-character-set=utf8mb4",
            "-B",
            "-e",
            sql,
        ]
        password = self.vars["mysql_password"].get()
        if password:
            cmd.insert(7, f"-p{password}")
        result = subprocess.run(cmd, cwd=ROOT, text=True, capture_output=True, encoding="utf-8", errors="replace")
        if result.returncode != 0:
            raise RuntimeError(self._mysql_error_message(result.stderr.strip() or result.stdout.strip()))
        lines = [line for line in result.stdout.splitlines() if line.strip()]
        rows = []
        for line in lines[1:]:
            parts = line.split("\t")
            if len(parts) >= 9:
                rows.append(parts[:9])
        return rows

    def _query_offerings_from_api(self):
        token = self._admin_token()
        url = self.vars["base_url"].get().rstrip("/") + "/api/admin/course-offerings?" + parse.urlencode({
            "page": 1,
            "size": 200,
        })
        try:
            req = request.Request(url, headers={"Authorization": f"Bearer {token}"})
            with request.urlopen(req, timeout=5) as response:
                payload = json.loads(response.read().decode("utf-8"))
        except error.HTTPError as exc:
            if exc.code in (401, 403):
                raise RuntimeError("token / 账号权限错误：请填写具备管理员权限的账号和密码")
            raise RuntimeError(f"后端 API 返回错误：HTTP {exc.code}")
        except error.URLError as exc:
            raise RuntimeError(f"后端 API 未启动或不可访问：{exc.reason}")
        data = payload.get("data", {})
        records = data.get("records", []) if isinstance(data, dict) else []
        rows = []
        for item in records:
            capacity = int(item.get("capacity") or 0)
            selected = int(item.get("selectedCount") or item.get("selected") or 0)
            rows.append([
                str(item.get("id")),
                str(item.get("courseCode") or item.get("code") or ""),
                str(item.get("courseName") or item.get("name") or ""),
                str(item.get("teacherName") or ""),
                str(capacity),
                str(selected),
                str(max(0, capacity - selected)),
                str(item.get("scheduleText") or ""),
                str(item.get("classroom") or ""),
            ])
        return rows

    def _admin_token(self):
        username = self.vars["cleanup_username"].get().strip()
        password = self.vars["admin_password"].get()
        if not username or not password:
            raise RuntimeError("后端 API 模式需要填写管理员账号和管理员密码")
        url = self.vars["base_url"].get().rstrip("/") + "/api/auth/login"
        body = json.dumps({"username": username, "password": password}).encode("utf-8")
        try:
            req = request.Request(url, data=body, headers={"Content-Type": "application/json"}, method="POST")
            with request.urlopen(req, timeout=5) as response:
                payload = json.loads(response.read().decode("utf-8"))
        except error.HTTPError as exc:
            if exc.code in (401, 403):
                raise RuntimeError("token / 账号权限错误：管理员账号或密码错误")
            raise RuntimeError(f"后端登录接口返回错误：HTTP {exc.code}")
        except error.URLError as exc:
            raise RuntimeError(f"后端 API 未启动或不可访问：{exc.reason}")
        token = payload.get("data", {}).get("accessToken")
        if not token:
            raise RuntimeError("后端登录接口未返回 accessToken")
        return token

    def _run_mysql(self, sql):
        mysql_exe = self._resolve_mysql_exe()
        cmd = [
            mysql_exe,
            "-h",
            self.vars["mysql_host"].get().strip() or "localhost",
            "-P",
            self.vars["mysql_port"].get().strip() or "3306",
            "-u",
            self.vars["mysql_user"].get().strip() or "root",
            "-D",
            self.vars["mysql_database"].get().strip() or "tianshiwebside",
            "--default-character-set=utf8mb4",
            "-e",
            sql,
        ]
        password = self.vars["mysql_password"].get()
        if password:
            cmd.insert(7, f"-p{password}")
        result = subprocess.run(cmd, cwd=ROOT, text=True, capture_output=True, encoding="utf-8", errors="replace")
        if result.returncode != 0:
            raise RuntimeError(self._mysql_error_message(result.stderr.strip() or result.stdout.strip()))
        return result.stdout

    def _resolve_mysql_exe(self):
        candidates = [
            os.environ.get("MYSQL_EXE"),
            shutil.which("mysql"),
            WINDOWS_MYSQL_EXE,
        ]
        for candidate in candidates:
            if candidate and Path(candidate).exists():
                return str(candidate)
        raise RuntimeError("mysql.exe 未找到：请设置 MYSQL_EXE，或将 mysql 加入 PATH，或安装 MySQL 客户端")

    def _mysql_error_message(self, raw):
        message = raw or "数据库连接失败"
        lower = message.lower()
        if "access denied" in lower:
            return "账号密码错误：" + message
        if "unknown database" in lower:
            return "数据库不存在：" + message
        if "doesn't exist" in lower or "does not exist" in lower:
            return "表不存在：" + message
        if "can't connect" in lower or "connection refused" in lower:
            return "数据库连接失败：" + message
        return message

    def apply_filter(self):
        rows = getattr(self, "all_rows", [])
        keyword = self.filter_var.get().strip().lower()
        if keyword:
            rows = [row for row in rows if keyword in " ".join(row).lower()]
        self._render_courses(rows)

    def _render_courses(self, rows):
        self.course_table.delete(*self.course_table.get_children())
        for row in rows:
            values = tuple(row)
            item_id = str(row[0])
            tag = "selected" if item_id in self.selected_ids else ""
            self.course_table.insert("", "end", iid=item_id, values=values, tags=(tag,))
        self.course_table.tag_configure("selected", background="#dbeafe")
        self._update_selection_label()

    def toggle_selected_rows(self):
        for item in self.course_table.selection():
            if item in self.selected_ids:
                self.selected_ids.remove(item)
            else:
                self.selected_ids.add(item)
        self.apply_filter()

    def select_all_visible(self):
        for item in self.course_table.get_children():
            self.selected_ids.add(item)
        self.apply_filter()

    def clear_selection(self):
        self.selected_ids.clear()
        self.apply_filter()

    def _update_selection_label(self):
        ids = sorted(self.selected_ids, key=lambda value: int(value))
        text = ",".join(ids) if ids else "无"
        rows = getattr(self, "all_rows", [])
        selected_rows = [row for row in rows if str(row[0]) in self.selected_ids]
        total_capacity = sum(self._int(row[4]) for row in selected_rows)
        total_selected = sum(self._int(row[5]) for row in selected_rows)
        total_remaining = sum(self._int(row[6]) for row in selected_rows)
        self.selection_label.configure(
            text=f"已选教学班：{text} | 数量={len(selected_rows)} 总容量={total_capacity} 已选={total_selected} 剩余={total_remaining}"
        )

    def _int(self, value):
        try:
            return int(value)
        except (TypeError, ValueError):
            return 0

    def apply_10k_random_preset(self):
        self.vars["load_users"].set("10000")
        self.vars["requests"].set("10000")
        self.vars["concurrency"].set("500")
        self.vars["login_concurrency"].set("200")
        self.vars["account_batch_size"].set("500")
        self.vars["smart_mode"].set("random")
        self.vars["cleanup"].set(True)
        self._append_output("已应用预设：10000 名学生，随机智慧选课。\n")

    def apply_smoke_preset(self):
        self.vars["load_users"].set("1")
        self.vars["requests"].set("1")
        self.vars["concurrency"].set("1")
        self.vars["login_concurrency"].set("1")
        self.vars["account_batch_size"].set("1")
        self.vars["smart_mode"].set("random")
        self.vars["cleanup"].set(True)
        self._append_output("已应用预设：1 名学生，用于快速连通性测试。\n")

    def set_selected_capacity(self):
        ids = self._selected_id_list()
        if not ids:
            messagebox.showwarning("未选择课程", "请先选择至少一个课程教学班。")
            return
        capacity = self.vars["capacity_value"].get().strip()
        if not capacity.isdigit() or int(capacity) <= 0:
            messagebox.showwarning("容量不合法", "课程容量必须是正整数。")
            return
        sql = f"update course_offering set capacity = {int(capacity)} where id in ({','.join(ids)})"
        self._run_db_action(sql, f"已将教学班 {','.join(ids)} 的容量修改为 {capacity}。")

    def clear_selected_records(self):
        ids = self._selected_id_list()
        if not ids:
            messagebox.showwarning("未选择课程", "请先选择至少一个课程教学班。")
            return
        if not messagebox.askyesno("确认清空", "确定要删除所选课程已有的选课记录吗？"):
            return
        sql = f"delete from course_selection where offering_id in ({','.join(ids)})"
        self._run_db_action(sql, f"已清空教学班 {','.join(ids)} 的已有选课记录。")

    def clear_selected_redis_stock(self):
        ids = self._selected_id_list()
        if not ids:
            messagebox.showwarning("未选择课程", "请先选择至少一个课程教学班。")
            return
        keys = [f"selection:offering:{offering_id}:remaining" for offering_id in ids]
        self._append_output(f"正在清空 Redis 库存键：{', '.join(keys)}\n")
        threading.Thread(target=self._clear_redis_worker, args=(keys,), daemon=True).start()

    def _selected_id_list(self):
        return sorted(self.selected_ids, key=lambda value: int(value))

    def _run_db_action(self, sql, success_message):
        def worker():
            try:
                self._run_mysql(sql)
                self.output_queue.put(("text", success_message + "\n"))
                rows = self._query_offerings()
                self.all_rows = rows
                self.output_queue.put(("courses", rows))
            except Exception as exc:
                self.output_queue.put(("text", f"数据库操作失败：{exc}\n"))
        threading.Thread(target=worker, daemon=True).start()

    def _clear_redis_worker(self, keys):
        try:
            response = self._redis_command("DEL", *keys)
            self.output_queue.put(("text", f"Redis 返回：{response}\n"))
        except Exception as exc:
            self.output_queue.put(("text", f"Redis 清理失败：{exc}\n"))

    def refresh_redis_cache(self):
        self._append_output("正在读取 Redis 缓存键...\n")
        threading.Thread(target=self._load_redis_cache_worker, daemon=True).start()

    def clear_redis_table(self):
        self.redis_table.delete(*self.redis_table.get_children())

    def _load_redis_cache_worker(self):
        try:
            pattern = self.redis_pattern_var.get().strip() or "selection:*"
            keys = self._scan_redis_keys(pattern, limit=200)
            rows = []
            for key in keys:
                ttl = self._redis_command("TTL", key)
                value = self._redis_command("GET", key)
                rows.append((key, str(ttl), "" if value is None else str(value)))
            self.output_queue.put(("redis", rows))
            self.output_queue.put(("text", f"已读取 Redis 缓存 {len(rows)} 条，模式：{pattern}\n"))
        except Exception as exc:
            self.output_queue.put(("text", f"Redis 缓存读取失败：{exc}\n"))

    def _scan_redis_keys(self, pattern, limit=200):
        cursor = "0"
        keys = []
        while True:
            result = self._redis_command("SCAN", cursor, "MATCH", pattern, "COUNT", "100")
            if not isinstance(result, list) or len(result) != 2:
                return keys
            cursor = str(result[0])
            batch = result[1] if isinstance(result[1], list) else []
            keys.extend(str(key) for key in batch)
            if cursor == "0" or len(keys) >= limit:
                return keys[:limit]

    def _redis_command(self, *parts):
        import socket
        host = self.vars["redis_host"].get()
        port = int(self.vars["redis_port"].get())
        payload = f"*{len(parts)}\r\n" + "".join(
            f"${len(str(part).encode('utf-8'))}\r\n{part}\r\n" for part in parts
        )
        with socket.create_connection((host, port), timeout=2) as sock:
            sock.sendall(payload.encode("utf-8"))
            reader = sock.makefile("rb")
            return self._read_redis_response(reader)

    def _read_redis_response(self, reader):
        prefix = reader.read(1)
        if not prefix:
            raise RuntimeError("Redis 没有返回数据")
        if prefix == b"+":
            return reader.readline().decode("utf-8", errors="replace").rstrip("\r\n")
        if prefix == b"-":
            message = reader.readline().decode("utf-8", errors="replace").rstrip("\r\n")
            raise RuntimeError(message)
        if prefix == b":":
            return int(reader.readline().decode("ascii").strip())
        if prefix == b"$":
            length = int(reader.readline().decode("ascii").strip())
            if length == -1:
                return None
            data = reader.read(length)
            reader.read(2)
            return data.decode("utf-8", errors="replace")
        if prefix == b"*":
            count = int(reader.readline().decode("ascii").strip())
            if count == -1:
                return None
            return [self._read_redis_response(reader) for _ in range(count)]
        raise RuntimeError(f"无法识别的 Redis 响应：{prefix!r}")

    def run_load_test(self):
        if self.process and self.process.poll() is None:
            messagebox.showwarning("正在运行", "当前已经有压测任务在运行。")
            return
        ids = sorted(self.selected_ids, key=lambda value: int(value))
        if not ids:
            messagebox.showwarning("未选择课程", "请先选择至少一个课程教学班。")
            return
        if not self.vars["cleanup_username"].get().strip():
            messagebox.showwarning("缺少管理员账号", "请填写用于准备账号和清理数据的管理员账号。")
            return
        if not self.vars["admin_password"].get():
            messagebox.showwarning("缺少管理员密码", "请填写管理员密码。")
            return
        if not self.vars["password"].get():
            messagebox.showwarning("缺少学生密码", "请填写压测学生账号密码。")
            return

        env = os.environ.copy()
        env.update({
            "BASE_URL": self.vars["base_url"].get(),
            "LOAD_USERS": self.vars["load_users"].get(),
            "REQUESTS": self.vars["requests"].get(),
            "CONCURRENCY": self.vars["concurrency"].get(),
            "LOGIN_CONCURRENCY": self.vars["login_concurrency"].get(),
            "ACCOUNT_BATCH_SIZE": self.vars["account_batch_size"].get(),
            "PASSWORD": self.vars["password"].get(),
            "ADMIN_PASSWORD": self.vars["admin_password"].get(),
            "CLEANUP_USERNAME": self.vars["cleanup_username"].get(),
            "REDIS_HOST": self.vars["redis_host"].get(),
            "REDIS_PORT": self.vars["redis_port"].get(),
            "OFFERING_IDS": ",".join(ids),
            "SMART_MODE": self.vars["smart_mode"].get(),
            "SMART_SWITCH": "true" if self.vars["smart_mode"].get() != "single" else "false",
            "CLEANUP": "true" if self.vars["cleanup"].get() else "false",
            "REDIS_ENABLED": "true" if self.vars["redis_enabled"].get() else "false",
        })
        env.pop("OFFERING_ID", None)

        self.output.delete("1.0", "end")
        redis_mode = "开启Redis缓存" if self.vars["redis_enabled"].get() else "关闭Redis缓存，数据库兜底"
        self._append_output(f"开始压测，教学班ID：{','.join(ids)}，{redis_mode}\n\n")
        threading.Thread(target=self._run_worker, args=(env,), daemon=True).start()

    def _run_worker(self, env):
        try:
            self.process = subprocess.Popen(
                ["node", str(NODE_SCRIPT)],
                cwd=ROOT,
                env=env,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
                encoding="utf-8",
                errors="replace",
                bufsize=1,
            )
            for line in self.process.stdout:
                self.output_queue.put(("text", line))
            code = self.process.wait()
            self.output_queue.put(("text", f"\n压测进程已结束，退出码：{code}。\n"))
        except Exception as exc:
            self.output_queue.put(("text", f"压测启动失败：{exc}\n"))
        finally:
            self.process = None

    def stop_process(self):
        if self.process and self.process.poll() is None:
            self.process.terminate()
            self._append_output("\n已请求停止当前压测。\n")

    def _drain_output(self):
        try:
            while True:
                kind, payload = self.output_queue.get_nowait()
                if kind == "text":
                    self._append_output(payload)
                elif kind == "courses":
                    self._render_courses(payload)
                elif kind == "redis":
                    self._render_redis_rows(payload)
        except queue.Empty:
            pass
        self.after(100, self._drain_output)

    def _append_output(self, text):
        self.output.insert("end", text)
        self.output.see("end")

    def _render_redis_rows(self, rows):
        self.redis_table.delete(*self.redis_table.get_children())
        for index, row in enumerate(rows):
            key, ttl, value = row
            if len(value) > 120:
                value = value[:117] + "..."
            self.redis_table.insert("", "end", iid=str(index), values=(key, ttl, value))


if __name__ == "__main__":
    app = CourseGrabPanel()
    app.refresh_courses()
    app.mainloop()

