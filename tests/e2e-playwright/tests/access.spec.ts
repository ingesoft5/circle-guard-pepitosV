import { expect, test } from '@playwright/test';
import { signIn } from './support';

test.describe('Access validation', () => {
  test('the QR of a user without health restrictions is approved at the gate', async ({
    page,
  }) => {
    const { authToken, qrToken } = await signIn(page);
    expect(qrToken, 'QR token shown on the home screen').toBeTruthy();

    // The gate scanner needs a camera, so the scanned value is sent the same way the
    // scanner does: POST /api/v1/gate/validate on the gateway.
    const validation = await page.request.post('/svc/gateway/api/v1/gate/validate', {
      headers: { Authorization: `Bearer ${authToken}` },
      data: { token: qrToken },
    });
    expect(validation.ok(), 'gate validation request').toBeTruthy();
    expect(await validation.json()).toEqual({
      valid: true,
      status: 'GREEN',
      message: 'Welcome to Campus',
    });
  });

  test('a tampered QR is rejected at the gate', async ({ page }) => {
    const { authToken, qrToken } = await signIn(page);

    const validation = await page.request.post('/svc/gateway/api/v1/gate/validate', {
      headers: { Authorization: `Bearer ${authToken}` },
      data: { token: `${qrToken.slice(0, -4)}AAAA` },
    });
    expect(await validation.json()).toMatchObject({ valid: false, status: 'RED' });
  });
});
