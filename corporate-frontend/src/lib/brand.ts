import { isDemoBrand, type ThemeMode } from '../theme/themeModes';

export type BrandPack = {
  name: string;
  short: string;
  backoffice: string;
  tagline: string;
  receiptFooter: string;
};

export const BRAND_BANK_ISLAMI: BrandPack = {
  name: 'Bank Islami Corporate',
  short: 'BANK ISLAMI CORPORATE',
  backoffice: 'BANK ISLAMI BACKOFFICE',
  tagline: 'Shariah-compliant corporate banking',
  receiptFooter: 'Bank Islami Corporate · Keep this receipt for your records',
};

export const BRAND_TEST: BrandPack = {
  name: 'Bank Islami Corporate (Demo)',
  short: 'BI CORPORATE DEMO',
  backoffice: 'BI BACKOFFICE DEMO',
  tagline: 'Shariah-compliant corporate banking',
  receiptFooter: 'Bank Islami Corporate · Demo · Keep this receipt for your records',
};

/** Default brand pack (non-React call sites). Prefer `useBrand()` in UI. */
export const BRAND = BRAND_BANK_ISLAMI;

export function brandForTheme(theme: ThemeMode): BrandPack {
  return isDemoBrand(theme) ? BRAND_TEST : BRAND_BANK_ISLAMI;
}
