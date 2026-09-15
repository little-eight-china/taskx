export function formatEpoch(epochSecond: number): string {
  if (!Number.isFinite(epochSecond)) {
    return "—";
  }
  return new Date(epochSecond * 1000).toISOString().replace(".000Z", "Z");
}

export function formatInstant(value: string | null): string {
  if (!value) {
    return "—";
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return date.toISOString().replace(/\.\d{3}Z$/, "Z");
}

export function triggerSummary(type: string, trigger: {
  expression?: string;
  intervalSeconds?: number;
  delaySeconds?: number;
  fireEpochSecond?: number;
}): string {
  switch (type) {
    case "CRON":
      return trigger.expression ?? "CRON";
    case "FIXED_RATE":
      return `每 ${trigger.intervalSeconds ?? "?"} 秒`;
    case "FIXED_DELAY":
      return `终态后 ${trigger.delaySeconds ?? "?"} 秒`;
    case "DELAY":
      return `延迟 ${trigger.delaySeconds ?? "?"} 秒`;
    case "ONCE":
      return trigger.fireEpochSecond == null ? "ONCE" : formatEpoch(trigger.fireEpochSecond);
    default:
      return type;
  }
}

export function blankToNull(value: string): string | null {
  const trimmed = value.trim();
  return trimmed.length === 0 ? null : trimmed;
}
