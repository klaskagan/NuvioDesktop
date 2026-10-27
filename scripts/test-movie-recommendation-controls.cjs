// Run with a Playwright-enabled NODE_PATH; uses an isolated browser and local player files.
const { chromium } = require('playwright');
const assert = require('node:assert/strict');
const path = require('node:path');
const { pathToFileURL } = require('node:url');

(async () => {
  const browser = await chromium.launch({ headless: true, channel: process.env.PLAYWRIGHT_CHANNEL || 'chrome' });
  try {
    const page = await browser.newPage({ viewport: { width: 1100, height: 750 }, reducedMotion: 'reduce' });
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.addInitScript(() => {
      window.cardEvents = [];
      window.webkit = { messageHandlers: { player: { postMessage: event => window.cardEvents.push(event) } } };
    });
    await page.clock.install();
    await page.goto(pathToFileURL(path.resolve(__dirname, '../composeApp/src/desktopMain/resources/player-ui/controls.html')).href);
    const update = state => page.evaluate(state => window.playerControls(state), state);
    const card = page.locator('#movieRecommendationCard');
    const assertGridFits = async () => {
      const layout = await card.evaluate(el => {
        const box = el.getBoundingClientRect();
        const grid = el.querySelector('.movie-recommendation-items');
        const items = [...grid.children].map(item => {
          const rect = item.getBoundingClientRect();
          return { x: rect.x, y: rect.y, right: rect.right, bottom: rect.bottom };
        });
        return { x: box.x, y: box.y, right: box.right, bottom: box.bottom, items,
          cardOverflow: el.scrollWidth > el.clientWidth || el.scrollHeight > el.clientHeight,
          gridOverflow: grid.scrollWidth > grid.clientWidth || grid.scrollHeight > grid.clientHeight };
      });
      assert.equal(layout.items.length, 10, 'all ten recommendations are rendered');
      assert.equal(new Set(layout.items.map(item => Math.round(item.x))).size, 5, 'five columns');
      assert.equal(new Set(layout.items.map(item => Math.round(item.y))).size, 2, 'two rows');
      assert.equal(layout.cardOverflow, false, 'card does not scroll');
      assert.equal(layout.gridOverflow, false, 'grid does not scroll');
      for (const item of layout.items) {
        assert.ok(item.x - layout.x >= 9 && layout.right - item.right >= 9, 'fixed left and right gutters');
        assert.ok(item.y > layout.y && item.bottom <= layout.bottom - 9, 'every title fits inside the card');
      }
    };
    const visible = () => card.evaluate(el => el.classList.contains('visible') && !el.inert);
    const poster = (name, color) => 'data:image/svg+xml,' + encodeURIComponent(`<svg xmlns="http://www.w3.org/2000/svg" width="144" height="216"><rect width="144" height="216" fill="${color}"/><circle cx="72" cy="80" r="40" fill="white" opacity=".12"/><text x="72" y="160" text-anchor="middle" fill="white" font-family="sans-serif" font-size="14">${name}</text></svg>`);
    const items = ['Arrival', 'Interstellar', 'Contact', 'Moon', 'Solaris', 'Gravity', 'Ad Astra', 'Dune', 'Oblivion', 'Sunshine'].map((title, index) => ({ actionKey: `fixture:movie:${index}`, title, poster: poster(title, ['#294657', '#433d59', '#275b56', '#544532'][index % 4]) }));
    await update({ isLoading: false, showOpeningOverlay: false, title: 'A movie', movieRecommendationSessionKey: 'fixture', movieRecommendationStage: 0, movieRecommendationItems: items, controlsVisible: false });
    assert.equal(await visible(), false);
    await update({ movieRecommendationStage: 1, controlsVisible: false });
    assert.equal(await visible(), true);
    await page.clock.fastForward(10001);
    assert.equal(await visible(), false, 'card auto-hides after ten seconds');
    assert.equal(await card.getAttribute('aria-hidden'), 'true');
    await update({ controlsVisible: true });
    assert.equal(await visible(), true, 'controls reveal auto-hidden card');
    await page.locator('#movieRecommendationClose').click();
    assert.equal(await page.evaluate(() => window.cardEvents.some(e => e.type === 'dismissMovieRecommendations:fixture')), true);
    assert.equal(await visible(), true, 'mobile behavior lets controls override dismissal');
    await update({ controlsVisible: false, movieRecommendationDismissed: true });
    assert.equal(await visible(), false);
    await update({ movieRecommendationStage: 2, movieRecommendationDismissed: false });
    assert.equal(await visible(), true, 'next stage reopens card');
    await page.clock.fastForward(10001);
    assert.equal(await visible(), false);
    await update({ movieRecommendationStage: 3, movieRecommendationDismissed: false });
    await page.clock.fastForward(30000);
    assert.equal(await visible(), true, 'ended stage does not auto-hide');
    await update({ movieRecommendationDismissed: true, controlsVisible: false });
    assert.equal(await visible(), false, 'ended stage can be dismissed');
    await update({ movieRecommendationDismissed: false, controlsVisible: true });
    const first = page.locator('.movie-recommendation-item').first();
    await first.focus();
    await page.keyboard.press('Enter');
    assert.equal(await page.evaluate(() => window.cardEvents.some(e => e.type === 'openMovieRecommendation:fixture:movie:0')), true);
    assert.equal(await page.evaluate(() => window.cardEvents.some(e => e.type === 'togglePlayback')), false, 'card activation does not pause playback');
    await page.keyboard.press('Escape');
    assert.equal(await page.evaluate(() => window.cardEvents.some(e => e.type === 'back')), false, 'Escape dismisses the card instead of leaving playback');
    assert.equal(await page.evaluate(() => document.activeElement.id), 'playerRoot');
    await update({ movieRecommendationDismissed: false });
    await page.locator('.movie-recommendation-item').first().focus();
    await update({ movieRecommendationItems: [] });
    assert.equal(await visible(), false);
    assert.equal(await page.evaluate(() => document.activeElement.id), 'playerRoot');
    await update({ movieRecommendationItems: items, movieRecommendationStage: 1, movieRecommendationSessionKey: 'new-session', controlsVisible: false });
    assert.equal(await visible(), true, 'new session resets auto-hide');
    await update({ isInPip: true });
    assert.equal(await visible(), false);
    await update({ isInPip: false, movieRecommendationStage: 0 });
    assert.equal(await visible(), false);
    await update({ movieRecommendationStage: 3, controlsVisible: true });
    await page.clock.fastForward(300);
    await assertGridFits();
    if (process.env.NUVIO_CARD_SCREENSHOT) await page.screenshot({ path: process.env.NUVIO_CARD_SCREENSHOT });
    for (const viewport of [{ width: 640, height: 480 }, { width: 640, height: 360 }, { width: 320, height: 480 }, { width: 1920, height: 1080 }]) {
      await page.setViewportSize(viewport);
      await page.clock.fastForward(300);
      await assertGridFits();
      const box = await card.boundingBox();
      assert.ok(box.x >= 0 && box.x + box.width <= viewport.width + 1, 'card stays inside window');
      assert.ok(box.y >= 0 && box.y + box.height <= viewport.height + 1, 'card fits above controls');
    }
    await update({ controlsVisible: false });
    await page.clock.fastForward(300);
    await assertGridFits();
    const last = page.locator('.movie-recommendation-item').last();
    await last.click();
    assert.equal(await page.evaluate(() => window.cardEvents.some(e => e.type === 'openMovieRecommendation:fixture:movie:9')), true, 'tenth title opens without scrolling');
    assert.deepEqual(errors, []);
    console.log('Movie recommendation overlay: stage transitions, auto-hide, controls, dismissal, keyboard, actions, empty/session states, PiP, and resize passed.');
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
