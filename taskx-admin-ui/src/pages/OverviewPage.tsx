import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../api";
import { Banner } from "../components/Layout";
import { formatInstant } from "../format";
import type { ExecutorView, Health, SlotView, Task } from "../types";

export function OverviewPage() {
  const [error, setError] = useState<string | null>(null);
  const [health, setHealth] = useState<Health | null>(null);
  const [tasks, setTasks] = useState<Task[]>([]);
  const [slots, setSlots] = useState<SlotView[]>([]);
  const [executors, setExecutors] = useState<ExecutorView[]>([]);

  useEffect(() => {
    Promise.all([api.health(), api.listTasks(), api.listSlots(), api.listExecutors()])
      .then(([h, t, s, e]) => {
        setHealth(h);
        setTasks(t);
        setSlots(s);
        setExecutors(e);
        setError(null);
      })
      .catch((err: unknown) => setError(err instanceof Error ? err.message : "加载失败"));
  }, []);

  const assigned = slots.filter((slot) => slot.executorId).length;
  const enabled = tasks.filter((task) => task.enabled).length;

  return (
    <>
      <h2>总览</h2>
      <p className="lede">配置写 MySQL + Redis；执行者只拉自己占有的 slot。本界面直连 Admin REST，没有登录。</p>
      <Banner kind="error">{error}</Banner>
      <div className="cards">
        <div className="card">
          <div className="label">Admin</div>
          <div className="value">{health?.status === "ok" ? "正常" : "异常"}</div>
        </div>
        <div className="card">
          <div className="label">任务 / 启用中</div>
          <div className="value">
            {tasks.length} / {enabled}
          </div>
        </div>
        <div className="card">
          <div className="label">已分配 slot</div>
          <div className="value">
            {assigned} / {slots.length || "—"}
          </div>
        </div>
        <div className="card">
          <div className="label">执行者</div>
          <div className="value">{executors.length}</div>
        </div>
      </div>
      <div className="row" style={{ marginBottom: 16 }}>
        <Link className="btn primary" to="/tasks/new">
          新建任务
        </Link>
        <Link className="btn" to="/cluster">
          迁 slot / 重建索引
        </Link>
      </div>
      <div className="panel">
        {executors.length === 0 ? (
          <div className="empty">还没有执行者心跳。启动 executor 并配置 TASKX_EXECUTOR_ID 后会出现在这里。</div>
        ) : (
          <table>
            <thead>
              <tr>
                <th>executorId</th>
                <th>最近心跳</th>
                <th>创建</th>
              </tr>
            </thead>
            <tbody>
              {executors.map((row) => (
                <tr key={row.executorId}>
                  <td className="mono">{row.executorId}</td>
                  <td>{formatInstant(row.lastHeartbeatAt)}</td>
                  <td>{formatInstant(row.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </>
  );
}
