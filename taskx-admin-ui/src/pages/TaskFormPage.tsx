import { useEffect, useMemo, useState, type FormEvent } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { api } from "../api";
import { Banner } from "../components/Layout";
import { blankToNull } from "../format";
import { slotOf } from "../slot";
import type { TaskWrite, Trigger, TriggerType } from "../types";

const TYPES: TriggerType[] = ["CRON", "FIXED_RATE", "FIXED_DELAY", "DELAY", "ONCE"];

type FormState = {
  id: string;
  handler: string;
  enabled: boolean;
  payload: string;
  workflowJson: string;
  type: TriggerType;
  expression: string;
  intervalSeconds: string;
  delaySeconds: string;
  fireEpochSecond: string;
};

const empty: FormState = {
  id: "",
  handler: "demo",
  enabled: true,
  payload: "",
  workflowJson: "",
  type: "ONCE",
  expression: "0 * * * * *",
  intervalSeconds: "60",
  delaySeconds: "5",
  fireEpochSecond: "1",
};

export function TaskFormPage() {
  const { id: routeId } = useParams();
  const editing = Boolean(routeId);
  const navigate = useNavigate();
  const [form, setForm] = useState<FormState>(empty);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!routeId) {
      setForm(empty);
      return;
    }
    api
      .getTask(routeId)
      .then((task) => {
        setForm({
          id: task.id,
          handler: task.handler,
          enabled: task.enabled,
          payload: task.payload ?? "",
          workflowJson: task.workflowJson ?? "",
          type: task.trigger.type,
          expression: task.trigger.expression ?? "0 * * * * *",
          intervalSeconds: String(task.trigger.intervalSeconds ?? 60),
          delaySeconds: String(task.trigger.delaySeconds ?? 5),
          fireEpochSecond: String(task.trigger.fireEpochSecond ?? 1),
        });
        setError(null);
      })
      .catch((err: unknown) => setError(err instanceof Error ? err.message : "加载失败"));
  }, [routeId]);

  const slot = useMemo(() => slotOf(form.id), [form.id]);

  function patch(partial: Partial<FormState>) {
    setForm((current) => ({ ...current, ...partial }));
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    const id = form.id.trim();
    if (!id) {
      setError("id 必填");
      return;
    }
    let trigger: Trigger;
    try {
      trigger = buildTrigger(form);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "触发参数不合法");
      return;
    }
    const body: TaskWrite = {
      handler: form.handler.trim(),
      enabled: form.enabled,
      payload: blankToNull(form.payload),
      workflowJson: blankToNull(form.workflowJson),
      trigger,
    };
    setSaving(true);
    try {
      await api.saveTask(id, body);
      navigate("/tasks");
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "保存失败");
    } finally {
      setSaving(false);
    }
  }

  return (
    <>
      <h2>{editing ? `编辑 ${routeId}` : "新建任务"}</h2>
      <p className="lede">
        内置执行者只注册了 handler <code>demo</code>。ONCE 的 fireEpochSecond=1 会立刻到期，适合联调。
      </p>
      <Banner kind="error">{error}</Banner>
      <form className="form" onSubmit={onSubmit}>
        <label className="field">
          <span>id</span>
          <input
            className="mono"
            value={form.id}
            disabled={editing}
            onChange={(event) => patch({ id: event.target.value })}
            required
          />
          <span className="hint">
            预览 slot（N=32）：{slot == null ? "—" : slot}
            {form.id.trim() === "demo-task" ? " · demo-task 是 slot 31，占 slot 0 的执行者捞不到" : ""}
          </span>
        </label>
        <label className="field">
          <span>handler</span>
          <input className="mono" value={form.handler} onChange={(event) => patch({ handler: event.target.value })} required />
        </label>
        <label className="field">
          <span>触发类型</span>
          <select value={form.type} onChange={(event) => patch({ type: event.target.value as TriggerType })}>
            {TYPES.map((type) => (
              <option key={type} value={type}>
                {type}
              </option>
            ))}
          </select>
        </label>
        {form.type === "CRON" ? (
          <label className="field">
            <span>Cron（6 段，UTC）</span>
            <input className="mono" value={form.expression} onChange={(event) => patch({ expression: event.target.value })} />
          </label>
        ) : null}
        {form.type === "FIXED_RATE" ? (
          <label className="field">
            <span>intervalSeconds</span>
            <input value={form.intervalSeconds} onChange={(event) => patch({ intervalSeconds: event.target.value })} />
          </label>
        ) : null}
        {form.type === "FIXED_DELAY" || form.type === "DELAY" ? (
          <label className="field">
            <span>delaySeconds</span>
            <input value={form.delaySeconds} onChange={(event) => patch({ delaySeconds: event.target.value })} />
          </label>
        ) : null}
        {form.type === "ONCE" ? (
          <label className="field">
            <span>fireEpochSecond</span>
            <input value={form.fireEpochSecond} onChange={(event) => patch({ fireEpochSecond: event.target.value })} />
          </label>
        ) : null}
        <label className="field">
          <span>
            <input type="checkbox" checked={form.enabled} onChange={(event) => patch({ enabled: event.target.checked })} />{" "}
            启用（写入时 ZADD / 停用 ZREM）
          </span>
        </label>
        <label className="field">
          <span>payload（可选，JSON 文本）</span>
          <textarea value={form.payload} onChange={(event) => patch({ payload: event.target.value })} />
        </label>
        <label className="field">
          <span>workflowJson（可选，第一版引擎未接）</span>
          <textarea value={form.workflowJson} onChange={(event) => patch({ workflowJson: event.target.value })} />
        </label>
        <div className="row">
          <button className="btn primary" type="submit" disabled={saving}>
            {saving ? "保存中…" : "保存"}
          </button>
          <Link className="btn" to="/tasks">
            返回
          </Link>
        </div>
      </form>
    </>
  );
}

function buildTrigger(form: FormState): Trigger {
  switch (form.type) {
    case "CRON":
      return { type: "CRON", expression: form.expression.trim() };
    case "FIXED_RATE":
      return { type: "FIXED_RATE", intervalSeconds: positiveInt(form.intervalSeconds, "intervalSeconds") };
    case "FIXED_DELAY":
      return { type: "FIXED_DELAY", delaySeconds: positiveInt(form.delaySeconds, "delaySeconds") };
    case "DELAY":
      return { type: "DELAY", delaySeconds: positiveInt(form.delaySeconds, "delaySeconds") };
    case "ONCE":
      return { type: "ONCE", fireEpochSecond: epoch(form.fireEpochSecond) };
  }
}

function positiveInt(raw: string, field: string): number {
  const value = Number(raw);
  if (!Number.isInteger(value) || value <= 0) {
    throw new Error(`${field} 必须是正整数`);
  }
  return value;
}

function epoch(raw: string): number {
  const value = Number(raw);
  if (!Number.isInteger(value)) {
    throw new Error("fireEpochSecond 必须是整数 Unix 秒");
  }
  return value;
}
