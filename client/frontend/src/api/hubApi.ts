import { appConfig } from '../config/appConfig';
import { requestJson } from './http';
import type { IngestEventStatus, SeedEventsParams, SeedEventsResult } from './types';

/** Hub seed: client posts synthetic events through ingest-api. */
export function seedEvents(params: SeedEventsParams): Promise<SeedEventsResult> {
  return requestJson<SeedEventsResult>(appConfig.paths.hubSeedEvents, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      count: params.count,
      configId: params.configId,
      burstSize: params.burstSize,
    }),
  });
}

/** Hub status: client forwards to ingest-api outbox projection. */
export function fetchIngestStatus(eventId: string): Promise<IngestEventStatus> {
  const id = encodeURIComponent(eventId.trim());
  return requestJson<IngestEventStatus>(`${appConfig.paths.hubEventStatusBase}/${id}/status`);
}
