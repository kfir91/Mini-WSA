import { describe, expect, it } from 'vitest';
import { appConfig, withQuery } from './appConfig';

describe('appConfig', () => {
  it('exposes default demo configId and stats paths', () => {
    expect(appConfig.defaults.configId).toBe('14227');
    expect(appConfig.paths.statsSummary).toBe('/v1/stats/summary');
    expect(appConfig.routes.stats).toBe('/stats');
    expect(appConfig.routes.apis).toBe('/apis');
  });

  it('builds query strings without empty params', () => {
    expect(withQuery('/v1/stats/summary', { configId: '14227' })).toBe(
      '/v1/stats/summary?configId=14227',
    );
    expect(withQuery('/v1/stats/summary', { configId: '', from: undefined })).toBe(
      '/v1/stats/summary',
    );
  });
});
