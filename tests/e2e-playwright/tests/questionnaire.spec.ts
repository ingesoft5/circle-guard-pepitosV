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
    // The client calls alert() right after router.replace(). Accept it inside the event,
    // as soon as it opens: accepting it later, after other awaits, sometimes hung until the
    // test timeout on stage.
    const confirmation = new Promise<string>((resolve) => {
      page.once('dialog', (dialog) => {
        resolve(dialog.message());
        void dialog.accept();
      });
    });
    await visibleText(page, 'SUBMIT REPORT').click();

    expect((await submission).status(), 'survey submission').toBe(200);
    expect(await confirmation).toContain('Thank you for your report');

    await expect(homeScreen(page)).toBeVisible();
  });
});
