import { expect, test } from '@playwright/test';
import { ensureActiveQuestionnaire, homeScreen, signIn, visibleText } from './support';

test.describe('Health questionnaire', () => {
  let questionnaireTitle: string;

  test.beforeAll(async ({ request }) => {
    questionnaireTitle = await ensureActiveQuestionnaire(request);
  });

  test('an authenticated user submits the questionnaire and sees the confirmation', async ({
    page,
  }) => {
    await signIn(page);

    await visibleText(page, 'REPORT').click();
    await expect(page).toHaveURL(/\/questionnaire/);
    await expect(visibleText(page, questionnaireTitle)).toBeVisible();

    const submission = page.waitForResponse(
      (response) =>
        response.url().includes('/svc/form/api/v1/surveys') &&
        response.request().method() === 'POST',
    );
    const confirmation = page.waitForEvent('dialog');
    await visibleText(page, 'SUBMIT REPORT').click();

    expect((await submission).status(), 'survey submission').toBe(200);
    const alert = await confirmation;
    expect(alert.message()).toContain('Thank you for your report');
    await alert.accept();

    await expect(homeScreen(page)).toBeVisible();
  });
});
