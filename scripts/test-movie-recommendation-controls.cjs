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
    const visible = () => card.evaluate(el => el.classList.contains('visible') && !el.inert);
    const poster = (name, color) => 'data:image/svg+xml,' + encodeURIComponent(`<svg xmlns="http://www.w3.org/2000/svg" width="144" height="216"><rect width="144" height="216" fill="${color}"/><circle cx="72" cy="80" r="40" fill="white" opacity=".12"/><text x="72" y="160" text-anchor="middle" fill="white" font-family="sans-serif" font-size="14">${name}</text></svg>`);
    const items = Array.from({ length: 20 }, (_, index) => ({
      actionKey: `fixture:movie:${index}`,
      title: `Movie ${index + 1}`,
      poster: poster(`Movie ${index + 1}`, '#294657'),
    }));
    const events = () => page.evaluate(() => window.cardEvents.map(event => event.type));
    const posters = page.locator('.movie-recommendation-item');
    await update({ isLoading: false, showOpeningOverlay: false, title: 'A movie',
      movieRecommendationSessionKey: 'fixture', movieRecommendationStage: 0,
      movieRecommendationItems: items, controlsVisible: false });
    await page.evaluate(() => window.playerUpdate({ duration: 600, position: 500, paused: false }));
    assert.equal(await visible(), false);
    await update({ movieRecommendationStage: 1 });
    await page.clock.fastForward(30000);
    assert.equal(await visible(), true, 'shelf stays open while browsing');
    assert.equal(await posters.count(), 20);
    assert.equal(await posters.locator("span").count(), 0);
    assert.equal(await posters.first().getAttribute("aria-label"), "Movie 1");
    for (const [width, height, isFullscreen] of [[1100, 750, false], [640, 360, false], [1920, 1080, true]]) {
      await page.setViewportSize({ width, height });
      await update({ isFullscreen });
      const layout = await card.evaluate(el => {
        const row = el.querySelector('.movie-recommendation-items');
        const rect = el.getBoundingClientRect();
        const seek = document.querySelector('#seek').getBoundingClientRect();
        const tops = [...row.children].map(poster => poster.getBoundingClientRect().top);
        return { oneRow: new Set(tops).size === 1, scrollable: row.scrollWidth > row.clientWidth,
          bottom: rect.bottom, top: rect.top, seekBottom: seek.bottom,
          seekReceivesInput: document.elementFromPoint(seek.x + seek.width / 2, seek.y + seek.height / 2)?.id === 'seek' };
      });
      assert.ok(layout.oneRow && layout.scrollable);
      assert.ok(layout.top >= 0 && layout.bottom <= height && layout.seekBottom < layout.top);
      assert.ok(layout.seekReceivesInput, 'timeline remains interactive');
    }
    if (process.env.NUVIO_CARD_SCREENSHOT) await page.screenshot({ path: process.env.NUVIO_CARD_SCREENSHOT });
    const row = page.locator('#movieRecommendationItems');
    await page.locator('#movieRecommendationNext').click();
    assert.ok(await row.evaluate(el => el.scrollLeft) > 0, 'chevron scrolls the shelf');
    await page.locator('#movieRecommendationPrevious').click();
    await row.hover();
    await page.mouse.wheel(0, 320);
    await page.waitForFunction(() => document.querySelector('#movieRecommendationItems').scrollLeft > 0);
    await posters.first().focus();
    for (let i = 0; i < 19; i++) await page.keyboard.press('ArrowRight');
    assert.equal(await page.evaluate(() => document.activeElement.dataset.recommendationKey), 'fixture:movie:19');
    await page.keyboard.press('Enter');
    assert.ok((await events()).includes('openMovieRecommendation:fixture:movie:19'));
    await page.keyboard.press('Escape');
    assert.equal(await visible(), false);
    assert.equal(await page.evaluate(() => document.activeElement.id), 'playerRoot');
    await update({ movieRecommendationStage: 2, controlsVisible: true });
    assert.equal(await visible(), false, 'dismissal survives milestones and control activity');
    await update({ movieRecommendationStage: 3, movieRecommendationDismissed: true });
    assert.equal(await visible(), false);
    const seekBack = async () => {
      await update({ movieRecommendationStage: 0, movieRecommendationDismissed: false });
      assert.equal(await visible(), false);
      await update({ movieRecommendationStage: 1 });
      assert.equal(await visible(), true);
    };
    await seekBack();
    await page.mouse.click(400, 130);
    await page.clock.fastForward(300);
    assert.equal(await visible(), false, 'clicking the video dismisses');
    assert.equal(await page.locator('#toggle').getAttribute('aria-label'), 'Pause', 'playback remains active');
    await seekBack();
    await card.locator('.movie-recommendation-header').click({ position: { x: 400, y: 20 } });
    assert.equal(await visible(), false, 'clicking empty shelf space dismisses');
    await seekBack();
    await page.locator('#movieRecommendationClose').click();
    assert.equal(await visible(), false);
    const actions = await events();
    assert.equal(actions.filter(type => type === 'dismissMovieRecommendations:fixture').length, 4);
    for (const action of ['togglePlayback', 'setPlaybackStateQuiet', 'back', 'toggleFullscreen']) {
      assert.ok(!actions.includes(action), `shelf input never sends ${action}`);
    }
    await seekBack();
    await posters.first().focus();
    await update({ movieRecommendationItems: [] });
    assert.equal(await visible(), false);
    assert.equal(await page.evaluate(() => document.activeElement.id), 'playerRoot');
    await update({ movieRecommendationItems: items, isInPip: true });
    assert.equal(await visible(), false);
    await update({ isInPip: false });
    assert.equal(await visible(), true);
    await page.locator('[data-command="speed"]').click();
    assert.equal(await visible(), false, 'player dialog takes precedence');
    await page.keyboard.press('Escape');
    assert.equal(await visible(), true);
    assert.deepEqual(errors, []);
    console.log('Movie recommendation overlay: 20 posters, scrolling, controls, dismissal, keyboard, actions, focus, PiP, and resize passed.');
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
