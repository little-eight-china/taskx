import type {
  Execution,
  ExecutionStatus,
  ExecutorView,
  Health,
  RebuildResponse,
  SlotView,
  Task,
  TaskWrite,
} from "./types";

const BASE = import.meta.env.VITE_API_BASE ?? "";

export class ApiError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

async function healthRequest(): Promise<Health> {
  const response = await fetch(`${BASE}/api/health`);
  const text = await response.text();
  let body: Health | null = null;
  try {
    body = JSON.parse(text) as Health;
  } catch {
    body = null;
  }
  if (!body || (response.status !== 200 && response.status !== 503)) {
    throw new ApiError(response.status, messageFrom(text, response));
  }
  return body;
}

function messageFrom(text: string, response: Response): string {
  if (!text) {
    return response.statusText || `HTTP ${response.status}`;
  }
  try {
    const body = JSON.parse(text) as { error?: string };
    return body.error || text;
  } catch {
    return text;
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${BASE}${path}`, {
    ...init,
    headers: {
      ...(init?.body ? { "Content-Type": "application/json" } : {}),
      ...init?.headers,
    },
  });
  if (response.status === 204) {
    return undefined as T;
  }
  if (!response.ok) {
    const text = await response.text();
    throw new ApiError(response.status, messageFrom(text, response));
  }
  if (response.status === 200 && response.headers.get("content-length") === "0") {
    return undefined as T;
  }
  return (await response.json()) as T;
}

export const api = {
  health: () => healthRequest(),
  listTasks: () => request<Task[]>("/api/tasks"),
  getTask: (id: string) => request<Task>(`/api/tasks/${encodeURIComponent(id)}`),
  saveTask: (id: string, body: TaskWrite) =>
    request<Task>(`/api/tasks/${encodeURIComponent(id)}`, {
      method: "PUT",
      body: JSON.stringify(body),
    }),
  deleteTask: (id: string) =>
    request<void>(`/api/tasks/${encodeURIComponent(id)}`, { method: "DELETE" }),
  disableTask: (id: string) =>
    request<Task>(`/api/tasks/${encodeURIComponent(id)}/disable`, { method: "POST" }),
  enableTask: (id: string) =>
    request<Task>(`/api/tasks/${encodeURIComponent(id)}/enable`, { method: "POST" }),
  listExecutions: (query: { taskId?: string; status?: ExecutionStatus | ""; limit?: number }) => {
    const params = new URLSearchParams();
    if (query.taskId) {
      params.set("taskId", query.taskId);
    }
    if (query.status) {
      params.set("status", query.status);
    }
    params.set("limit", String(query.limit ?? 50));
    return request<Execution[]>(`/api/executions?${params.toString()}`);
  },
  requeue: (id: number) => request<Execution>(`/api/executions/${id}/requeue`, { method: "POST" }),
  cancel: (id: number) => request<Execution>(`/api/executions/${id}/cancel`, { method: "POST" }),
  listSlots: () => request<SlotView[]>("/api/slots"),
  assignSlot: (slotNo: number, executorId: string) =>
    request<SlotView>(`/api/slots/${slotNo}`, {
      method: "PUT",
      body: JSON.stringify({ executorId }),
    }),
  listExecutors: () => request<ExecutorView[]>("/api/executors"),
  rebuildTriggers: () =>
    request<RebuildResponse>("/api/maintenance/rebuild-triggers", { method: "POST" }),
};
