export type TriggerType = "CRON" | "FIXED_RATE" | "FIXED_DELAY" | "DELAY" | "ONCE";

export type ExecutionStatus = "PENDING" | "RUNNING" | "SUCCESS" | "FAILED" | "CANCELLED";

export type Trigger = {
  type: TriggerType;
  expression?: string;
  intervalSeconds?: number;
  delaySeconds?: number;
  fireEpochSecond?: number;
};

export type Task = {
  id: string;
  handler: string;
  payload: string | null;
  enabled: boolean;
  slot: number;
  trigger: Trigger;
  workflowJson: string | null;
};

export type TaskWrite = {
  handler: string;
  payload: string | null;
  enabled: boolean;
  trigger: Trigger;
  workflowJson: string | null;
};

export type Execution = {
  id: number;
  taskId: string;
  scheduledFireTime: number;
  executorId: string;
  status: ExecutionStatus;
};

export type SlotView = {
  slotNo: number;
  executorId: string | null;
};

export type ExecutorView = {
  executorId: string;
  lastHeartbeatAt: string | null;
  createdAt: string | null;
};

export type Health = {
  status: string;
  mysql: string;
  redis: string;
};

export type RebuildResponse = {
  enabledTasks: number;
};
