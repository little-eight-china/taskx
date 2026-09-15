# TaskX Admin UI

React 管理台，开发时通过 Vite 把 `/api` 代理到 `http://127.0.0.1:8080`。无登录。

```bash
# 先起 Admin REST（见仓库 README / docs/10-testing.md）
cd taskx-admin-ui
npm install
npm run dev
```

浏览器打开 `http://localhost:5173`。

| 页面 | 作用 |
|------|------|
| 总览 | health、任务数、slot / 执行者 |
| 任务 | 列表、启停、删除；新建/编辑触发器 |
| 执行记录 | 按 taskId / 状态筛选，requeue / cancel |
| 集群 | 点选 slot 改归属；全量重建 ZSET |

生产构建：`npm run build`，产物在 `dist/`。若静态资源和 API 不同源，构建前设 `VITE_API_BASE=http://127.0.0.1:8080`（Admin 已开 CORS）。
