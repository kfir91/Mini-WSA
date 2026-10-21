import { useEffect, useState } from 'react';
import { probeUrl } from '../api/analyticsApi';
import { appConfig, healthUrl, type ServiceId } from '../config/appConfig';

export type ProbeState = 'wait' | 'ok' | 'bad';

/**
 * Best-effort reachability for the hub health strip.
 * Cross-origin actuator calls use no-cors (opaque success ≠ HTTP 200).
 * Same-origin client health can use a real JSON check when on :8084.
 */
export function useServiceHealth() {
  const [probes, setProbes] = useState<Partial<Record<ServiceId, ProbeState>>>({});

  useEffect(() => {
    void (async () => {
      const next: Partial<Record<ServiceId, ProbeState>> = {};
      await Promise.all(
        appConfig.healthTargets.map(async (id) => {
          next[id] = (await probeUrl(healthUrl(id))) ? 'ok' : 'bad';
        }),
      );
      setProbes(next);
    })();
  }, []);

  return probes;
}
