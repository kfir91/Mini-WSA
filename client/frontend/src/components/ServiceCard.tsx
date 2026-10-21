import type { ServiceId } from '../config/appConfig';
import { servicePortLabel } from '../config/appConfig';
import type { ProbeState } from '../hooks/useServiceHealth';

type Props = {
  id: ServiceId;
  title: string;
  description: string;
  probe?: ProbeState;
  waitLabel: string;
  actions: React.ReactNode;
};

export function ServiceCard({ id, title, description, probe, waitLabel, actions }: Props) {
  const state = probe ?? 'wait';
  return (
    <article className="card">
      <h3>{title}</h3>
      <span className={`status ${state}`}>{probe ?? waitLabel}</span>
      <span className="port-badge">{servicePortLabel(id)}</span>
      <p>{description}</p>
      <div className="card-actions">{actions}</div>
    </article>
  );
}
