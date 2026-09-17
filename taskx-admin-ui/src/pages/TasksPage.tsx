import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../api";
import { EnabledBadge } from "../components/Badges";
import { ConfirmDialog } from "../components/ConfirmDialog";
import { Banner } from "../components/Layout";
import { triggerSummary } from "../format";
import type { Task } from "../types";

export function TasksPage() {
  const [tasks, setTasks] = useState<Task[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState<string | null>(null);
  const [toDelete, setToDelete] = useState<Task | null>(null);

  function load() {
    api
      .listTasks()
      .then((rows) => {
        setTasks(rows);
        setError(null);
      })
      .catch((err: unknown) => setError(err instanceof Error ? err.message : "加载失败"));
  }

  useEffect(load, []);

  async function toggle(task: Task) {
    setPending(task.id);
    try {
      if (task.enabled) {
        await api.disableTask(task.id);
      } else {
        await api.enableTask(task.id);
      }
      load();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "更新失败");
    } finally {
      setPending(null);
    }
  }

  async function remove() {
    if (!toDelete) {
      return;
    }
    setPending(toDelete.id);
    try {
      await api.deleteTask(toDelete.id);
      setToDelete(null);
      load();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "删除失败");
    } finally {
      setPending(null);
    }
  }

  return (
    <>
      <div className="row space">
        <div>
          <h2>任务</h2>
          <p className="lede">slot 由 CRC32(UTF-8(id)) % N 计算，不入库。id 建议用 demo（N=32 时为 slot 0）。</p>
        </div>
        <Link className="btn primary" to="/tasks/new">
          新建
        </Link>
      </div>
      <Banner kind="error">{error}</Banner>
      <div className="panel">
        {tasks.length === 0 ? (
          <div className="empty">还没有任务。用「新建」或执行者 TASKX_SEED_DEMO 写入 demo。</div>
        ) : (
          <table>
            <thead>
              <tr>
                <th>id</th>
                <th>目标</th>
                <th>触发</th>
                <th>slot</th>
                <th>状态</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {tasks.map((task) => (
                <tr key={task.id}>
                  <td className="mono">
                    <Link to={`/tasks/${encodeURIComponent(task.id)}`}>{task.id}</Link>
                  </td>
                  <td className="mono">
                    {task.targetType}:{task.targetRef}
                    {task.targetVersion == null ? "" : `@${task.targetVersion}`}
                  </td>
                  <td>
                    {task.trigger.type}
                    <div className="hint">{triggerSummary(task.trigger.type, task.trigger)}</div>
                  </td>
                  <td className="mono">{task.slot}</td>
                  <td>
                    <EnabledBadge enabled={task.enabled} />
                  </td>
                  <td>
                    <div className="actions">
                      <Link className="btn" to={`/executions?taskId=${encodeURIComponent(task.id)}`}>
                        记录
                      </Link>
                      <button className="btn" disabled={pending === task.id} onClick={() => toggle(task)}>
                        {task.enabled ? "停用" : "启用"}
                      </button>
                      <button className="btn danger" disabled={pending === task.id} onClick={() => setToDelete(task)}>
                        删除
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
      {toDelete ? (
        <ConfirmDialog
          title={`删除 ${toDelete.id}？`}
          body={<p>会从 MySQL 删除并 ZREM。进行中的执行记录不会自动取消。</p>}
          confirmLabel="删除"
          danger
          onClose={() => setToDelete(null)}
          onConfirm={remove}
        />
      ) : null}
    </>
  );
}
