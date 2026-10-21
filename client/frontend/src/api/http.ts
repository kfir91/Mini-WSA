import { ApiError, type ApiErrorBody } from './types';

export async function requestJson<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(path, {
    cache: 'no-store',
    ...init,
    headers: {
      Accept: 'application/json',
      ...(init?.headers ?? {}),
    },
  });

  const text = await res.text();
  let body: unknown = null;
  if (text) {
    try {
      body = JSON.parse(text) as unknown;
    } catch {
      body = { message: text } satisfies ApiErrorBody;
    }
  }

  if (!res.ok) {
    const errBody = (body ?? {}) as ApiErrorBody;
    throw new ApiError(
      res.status,
      errBody.code ?? `HTTP_${res.status}`,
      errBody.message ?? (res.statusText || 'Request failed'),
    );
  }

  return body as T;
}
