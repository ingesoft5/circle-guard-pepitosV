import { APIRequestContext, Locator, Page, Response, expect } from '@playwright/test';

function requiredEnv(name: string): string {
  const value = process.env[name];
  if (!value) {
    throw new Error(`Set ${name} for the end-to-end test user`);
  }
  return value;
}

export const testUser = {
  get username(): string {
    return requiredEnv('E2E_USERNAME');
  },
  get password(): string {
    return requiredEnv('E2E_PASSWORD');
  },
};

/**
 * The QR countdown of the home screen. Expo Router keeps earlier screens mounted but
 * hidden, so only the visible match counts.
 */
export const homeScreen = (page: Page): Locator =>
  page.locator('text=/EXPIRES IN \\d+:\\d{2}/ >> visible=true');

/** Exact text on the visible screen, ignoring the hidden screens Expo Router keeps mounted. */
export const visibleText = (page: Page, text: string): Locator =>
  page.getByText(text, { exact: true }).locator('visible=true');

const isQrRequest = (response: Response) =>
  response.url().includes('/svc/auth/api/v1/auth/qr/generate') && response.ok();

export interface Session {
  authToken: string;
  qrToken: string;
}

/**
 * Signs in through the login screen and waits for the home screen and its first QR token.
 */
export async function signIn(page: Page): Promise<Session> {
  await page.goto('/login');
  await page.getByPlaceholder('Username').fill(testUser.username);
  await page.getByPlaceholder('Password').fill(testUser.password);

  const loginResponse = page.waitForResponse(
    (response) => response.url().includes('/svc/auth/api/v1/auth/login'),
  );
  const qrResponse = page.waitForResponse(isQrRequest);
  await visibleText(page, 'SIGN IN').click();

  expect((await loginResponse).status(), 'login request').toBe(200);
  const { qrToken } = await (await qrResponse).json();
  await expect(homeScreen(page)).toBeVisible();

  const authToken = await page.evaluate(() => localStorage.getItem('circleguard_token'));
  expect(authToken, 'session token stored by the client').toBeTruthy();
  return { authToken: authToken as string, qrToken };
}

/**
 * The form service has no seed data, so a fresh environment has no active questionnaire.
 * Creates and activates one only when none is active.
 */
export async function ensureActiveQuestionnaire(request: APIRequestContext): Promise<string> {
  const active = await request.get('/svc/form/api/v1/questionnaires/active');
  if (active.ok()) {
    return (await active.json()).title;
  }
  expect(active.status(), 'active questionnaire lookup').toBe(404);

  const created = await request.post('/svc/form/api/v1/questionnaires', {
    data: {
      title: 'Daily health screening',
      description: 'Created by the end-to-end tests because no questionnaire was active',
      version: 1,
      questions: [{ text: 'Do you have a fever today?', type: 'YES_NO', orderIndex: 0 }],
    },
  });
  expect(created.ok(), 'create questionnaire').toBeTruthy();
  const questionnaire = await created.json();

  const activated = await request.post(
    `/svc/form/api/v1/questionnaires/${questionnaire.id}/activate`,
  );
  expect(activated.ok(), 'activate questionnaire').toBeTruthy();
  return questionnaire.title;
}
