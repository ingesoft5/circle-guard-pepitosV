import { expect, test } from '@playwright/test';
import { signIn, visibleText } from './support';

test.describe('Sign in', () => {
  test('a user with valid credentials reaches the home screen', async ({ page }) => {
    await signIn(page);

    await expect(page).not.toHaveURL(/\/login/);
    await expect(visibleText(page, 'STATUS')).toBeVisible();
    await expect(visibleText(page, 'REPORT')).toBeVisible();
  });

  test('wrong credentials keep the user on the login screen', async ({ page }) => {
    await page.goto('/login');
    await page.getByPlaceholder('Username').fill('unknown_user');
    await page.getByPlaceholder('Password').fill('wrong-password');

    const dialog = page.waitForEvent('dialog');
    await visibleText(page, 'SIGN IN').click();
    const alert = await dialog;
    expect(alert.message()).toMatch(/^\[(401|403)\]/);
    await alert.dismiss();

    await expect(page).toHaveURL(/\/login/);
  });
});
