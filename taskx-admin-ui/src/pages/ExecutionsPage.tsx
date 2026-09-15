import { useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { api } from "../api";
import { StatusBadge } from "../components/Badges";
import { Banner } from "../components/Layout";
import { formatEpoch } from "../format";
import type { Execution, ExecutionStatus } from "../types";

const STATUSES: Array<ExecutionStatus | ""> = ["", "PENDING", "RUNNING", "SUCCESS", "FAILED", "CANCELLED"];

export function ExecutionsPage() {
  const [params, setParams] = useSearchParams();
  const taskId = params.get("taskId") ?? "";
  const status = (params.get("status") ?? "") as ExecutionStatus | "";
  const [rows, setRows] = useState<Execution[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);

  function load(nextTaskId = taskId, nextStatus = status) {
    api
      .listExecutions({ taskId: nextTaskId || undefined, status: nextStatus || undefined, limit: 100 })
      .then((list) => {
        setRows(list);
        setError(null);
      })
      .catch((err: unknown) => setError(err instanceof Error ? err.message : "加载失败"));
  }

  useEffect(() => {
    load();
  }, [taskId, status]);

  function updateFilter(partial: { taskId?: string; status?: string }) {
    const next = new URLSearchParams(params);
    const nextTaskId = partial.taskId ?? taskId;
    const nextStatus = partial.status ?? status;
    if (nextTaskId) {
      next.set("taskId", nextTaskId);
    } else {
      next.delete("taskId");
    }
    if (nextStatus) {
      next.set("status", nextStatus);
    } else {
      next.delete("status");
    }
    setParams(next, { replace: true });
  }

  async function act(id: number, kind: "requeue" | "cancel") {
    setBusyId(id);
    try {
      if (kind === "requeue") {
        await api.requeue(id);
      } else {
        await api.cancel(id);
      }
      load();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "操作失败");
    } finally {
      setBusyId(null);
    }
  }

  return (
    <>
      <h2>执行记录</h2>
      <p className="lede">
        requeue 把 RUNNING/FAILED 改回 PENDING，由原 executorId 的恢复循环捞。执行者还活着时不要对正在跑的行乱点。
      </p>
      <Banner kind="error">{error}</Banner>
      <div className="filters">
        <input
          className="mono"
          placeholder="taskId"
          value={taskId}
          onChange={(event) => updateFilter({ taskId: event.target.value })}
        />
        <select value={status} onChange={(event) => updateFilter({ status: event.target.value })}>
          {STATUSES.map((value) => (
            <option key={value || "all"} value={value}>
              {value || "全部状态"}
            </option>
          ))}
        </select>
        <button className="btn" type="button" onClick={() => load()}>
          刷新
        </button>
      </div>
      <div className="panel">
        {rows.length === 0 ? (
          <div className="empty">没有记录。先建任务并启动占对应 slot 的执行者。</div>
        ) : (
          <table>
            <thead>
              <tr>
                <th>id</th>
                <th>taskId</th>
                <th>fireTime (UTC)</th>
                <th>executor</th>
                <th>状态</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.id}>
                  <td className="mono">{row.id}</td>
                  <td className="mono">
                    <Link to={`/tasks/${encodeURIComponent(row.taskId)}`}>{row.taskId}</Link>
                  </td>
                  <td className="mono">
                    {row.scheduledFireTime}
                    <div className="hint">{formatEpoch(row.scheduledFireTime)}</div>
                  </td>
                  <td className="mono">{row.executorId}</td>
                  <td>
                    <StatusBadge status={row.status} />
                  </td>
                  <td>
                    <div className="actions">
                      {(row.status === "RUNNING" || row.status === "FAILED") && (
                        <button className="btn" disabled={busyId === row.id} onClick={() => act(row.id, "requeue")}>
                          requeue
                        </button>
                      )}
                      {(row.status === "PENDING" || row.status === "RUNNING") && (
                        <button className="btn danger" disabled={busyId === row.id} onClick={() => act(row.id, "cancel")}>
                          cancel
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </>
  );
}
