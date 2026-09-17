export type TriggerType = "CRON" | "FIXED_RATE" | "FIXED_DELAY" | "DELAY" | "ONCE";

export type ExecutionStatus = "PENDING" | "RUNNING" | "SUCCESS" | "FAILED" | "CANCELLED";
export type TaskTargetType = "HANDLER" | "WORKFLOW";

export type Trigger = {
  type: TriggerType;
  expression?: string;
  intervalSeconds?: number;
  delaySeconds?: number;
  fireEpochSecond?: number;
};

export type Task = {
  id: string;
  handler: string | null;
  targetType: TaskTargetType;
  targetRef: string;
  targetVersion: number | null;
  payload: string | null;
  enabled: boolean;
  slot: number;
  trigger: Trigger;
  workflowJson: null;
};

export type TaskWrite = {
  targetType: TaskTargetType;
  targetRef: string;
  targetVersion: number | null;
  payload: string | null;
  enabled: boolean;
  trigger: Trigger;
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
