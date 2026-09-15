import { useEffect, useState } from "react";
import { api } from "../api";
import { ConfirmDialog } from "../components/ConfirmDialog";
import { Banner } from "../components/Layout";
import { formatInstant } from "../format";
import type { ExecutorView, SlotView } from "../types";

export function ClusterPage() {
  const [slots, setSlots] = useState<SlotView[]>([]);
  const [executors, setExecutors] = useState<ExecutorView[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [assigning, setAssigning] = useState<SlotView | null>(null);
  const [executorId, setExecutorId] = useState("");
  const [rebuildOpen, setRebuildOpen] = useState(false);
  const [busy, setBusy] = useState(false);

  function load() {
    Promise.all([api.listSlots(), api.listExecutors()])
      .then(([s, e]) => {
        setSlots(s);
        setExecutors(e);
        setError(null);
      })
      .catch((err: unknown) => setError(err instanceof Error ? err.message : "加载失败"));
  }

  useEffect(load, []);

  async function assign() {
    if (!assigning) {
      return;
    }
    const id = executorId.trim();
    if (!id) {
      setError("executorId 必填");
      return;
    }
    setBusy(true);
    try {
      await api.assignSlot(assigning.slotNo, id);
      setAssigning(null);
      setNotice(`slot ${assigning.slotNo} → ${id}`);
      load();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "改归属失败");
    } finally {
      setBusy(false);
    }
  }

  async function rebuild() {
    setBusy(true);
    try {
      const result = await api.rebuildTriggers();
      setRebuildOpen(false);
      setNotice(`已重建，启用中的任务 ${result.enabledTasks} 条`);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "重建失败");
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <div className="row space">
        <div>
          <h2>集群</h2>
          <p className="lede">迁 slot 会抢槽锁；执行者正在拉的时候可能 409。全量重建必须先停所有执行者。</p>
        </div>
        <button className="btn danger" type="button" onClick={() => setRebuildOpen(true)}>
          重建触发索引
        </button>
      </div>
      <Banner kind="error">{error}</Banner>
      <Banner kind="ok">{notice}</Banner>
      <h3 style={{ margin: "0 0 8px", fontSize: 16 }}>Slots</h3>
      <div className="panel">
        <div className="slots">
          {slots.map((slot) => (
            <button
              key={slot.slotNo}
              type="button"
              className={`slot ${slot.executorId ? "" : "empty"}`}
              onClick={() => {
                setAssigning(slot);
                setExecutorId(slot.executorId ?? "");
                setError(null);
              }}
            >
              <div className="n">slot {slot.slotNo}</div>
              <div className="owner mono">{slot.executorId ?? "未分配"}</div>
            </button>
          ))}
        </div>
      </div>
      <h3 style={{ margin: "20px 0 8px", fontSize: 16 }}>执行者</h3>
      <div className="panel">
        {executors.length === 0 ? (
          <div className="empty">没有 tx_executor 行。执行者成功 claim 后会出现。</div>
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
      {assigning ? (
        <ConfirmDialog
          title={`改 slot ${assigning.slotNo} 归属`}
          confirmLabel={busy ? "提交中…" : "保存"}
          onClose={() => setAssigning(null)}
          onConfirm={assign}
          body={
            <>
              <p>建议先停当前占用该 slot 的执行者。可从已有执行者里选，或填新的稳定 ID。</p>
              <label className="field">
                <span>executorId</span>
                <input className="mono" value={executorId} onChange={(event) => setExecutorId(event.target.value)} list="executor-ids" />
                <datalist id="executor-ids">
                  {executors.map((row) => (
                    <option key={row.executorId} value={row.executorId} />
                  ))}
                </datalist>
              </label>
            </>
          }
        />
      ) : null}
      {rebuildOpen ? (
        <ConfirmDialog
          title="重建全部 trigger ZSET？"
          confirmLabel={busy ? "重建中…" : "仍然重建"}
          danger
          onClose={() => setRebuildOpen(false)}
          onConfirm={rebuild}
          body={
            <p>
              会锁 0..N-1 全部 slot，清空 ZSET，再按启用中的 Task 从 Redis「现在」写入下次时间。执行者还在跑会 409。改 N
              或 Redis 丢数据时才用。
            </p>
          }
        />
      ) : null}
    </>
  );
}
