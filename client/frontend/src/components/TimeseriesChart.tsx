import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Filler,
  Tooltip,
  Legend,
} from 'chart.js';
import { Line } from 'react-chartjs-2';
import { useTranslation } from 'react-i18next';
import type { TimeseriesResponse } from '../api/types';

ChartJS.register(CategoryScale, LinearScale, PointElement, LineElement, Filler, Tooltip, Legend);

type Props = {
  timeseries: TimeseriesResponse;
};

export function TimeseriesChart({ timeseries }: Props) {
  const { t } = useTranslation();
  const points = timeseries.points ?? [];

  if (points.length === 0) {
    return <p className="empty">{t('common.noPoints')}</p>;
  }

  return (
    <div className="chart-wrap">
      <Line
        data={{
          labels: points.map((p) => p.bucket),
          datasets: [
            {
              label: t('stats.chartSeriesEvents'),
              data: points.map((p) => p.count),
              borderColor: '#3d9cf0',
              backgroundColor: 'rgba(61, 156, 240, 0.18)',
              fill: true,
              tension: 0.2,
              pointRadius: 3,
            },
          ],
        }}
        options={{
          responsive: true,
          maintainAspectRatio: false,
          plugins: { legend: { display: false } },
          scales: {
            x: {
              ticks: { color: '#9aa8bc', maxRotation: 45 },
              grid: { color: 'rgba(42, 53, 72, 0.6)' },
            },
            y: {
              beginAtZero: true,
              ticks: { color: '#9aa8bc', precision: 0 },
              grid: { color: 'rgba(42, 53, 72, 0.6)' },
            },
          },
        }}
      />
    </div>
  );
}
