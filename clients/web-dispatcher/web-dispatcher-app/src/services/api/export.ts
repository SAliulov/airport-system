import { API_BASE } from '../../config';
import { backendErrorText, statusText } from '../http/client';
import { getToken } from '../auth';

/** Скачивание PDF/Excel расписания на день с JWT. */
export async function downloadScheduleExport(format: 'pdf' | 'excel', date: string): Promise<void> {
  const token = getToken();
  if (!token) {
    throw new Error('Требуется авторизация');
  }
  const res = await fetch(`${API_BASE}/api/v1/schedules/export/${format}?date=${encodeURIComponent(date)}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) {
    const backend = backendErrorText(await res.json().catch(() => null));
    throw new Error(backend ?? statusText(res.status));
  }
  const blob = await res.blob();
  const ext = format === 'pdf' ? 'pdf' : 'xlsx';
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = `schedule-${date}.${ext}`;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}
