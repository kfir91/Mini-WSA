export type CategoryStats = {
  count: number;
  avgThreatScore: number;
};

export type AttackerStats = {
  clientIp: string;
  count: number;
  avgThreatScore: number;
};

export type PathStats = {
  path: string;
  count: number;
};

export type SummaryResponse = {
  configId?: number | null;
  totalEvents: number;
  byCategory?: Record<string, CategoryStats>;
  byAction?: Record<string, number>;
  topAttackers?: AttackerStats[];
  topTargetedPaths?: PathStats[];
};

export type TimeseriesPoint = {
  bucket: string;
  count: number;
};

export type TimeseriesResponse = {
  interval: string;
  points: TimeseriesPoint[];
};

export type AlertEvalRow = {
  alertId: string;
  name?: string;
  firing: boolean;
  value: number;
  threshold: number;
  category?: string;
  metric?: string;
  window?: string;
  configId?: number;
};

export type DemoAlertRule = {
  label: string;
  meta: string;
  rule: Record<string, unknown>;
};

export type SeedEventsResult = {
  requested: number;
  accepted: number;
  failed: number;
  configId: number;
  message: string;
};

export type SeedEventsParams = {
  count: number;
  configId?: number;
  burstSize?: number;
};

export type IngestClientStatus = 'accepted' | 'progressing' | 'done' | 'failed';

export type IngestEventStatus = {
  eventId: string;
  status: IngestClientStatus;
  attemptCount: number;
  createdAt: string;
  publishedAt?: string | null;
  lastError?: string | null;
};

export type ApiErrorBody = {
  code?: string;
  message?: string;
};

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;

  constructor(status: number, code: string, message: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
  }
}
