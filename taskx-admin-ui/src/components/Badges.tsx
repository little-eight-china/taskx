import type { ExecutionStatus } from "../types";

export function EnabledBadge({ enabled }: { enabled: boolean }) {
  return <span className={`badge ${enabled ? "on" : "off"}`}>{enabled ? "启用" : "停用"}</span>;
}

export function StatusBadge({ status }: { status: ExecutionStatus }) {
  return <span className={`badge ${status}`}>{status}</span>;
}
