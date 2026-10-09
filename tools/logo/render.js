// SVG dosyalarini Chromium (Playwright) ile seffaf PNG'ye cevirir.
// Kullanim: node render.js isler.json   ->  [{"svg": "a.svg", "png": "a.png", "width": 512, "height": 512}, ...]
const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');

(async () => {
  const jobs = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'));
  const browser = await chromium.launch(
    process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {});
  for (const job of jobs) {
    const page = await browser.newPage({ viewport: { width: job.width, height: job.height }, deviceScaleFactor: job.scale || 1 });
    await page.goto('file://' + path.resolve(job.svg));
    await page.evaluate(async () => { if (document.fonts) { await document.fonts.ready; } });
    await page.waitForTimeout(150);
    await page.screenshot({ path: job.png, omitBackground: true });
    await page.close();
    console.log('  ' + job.png);
  }
  await browser.close();
})();
