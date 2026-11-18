// Seek-bar time preview regression fixture for the desktop player controls.
// Run with a Playwright-enabled NODE_PATH; uses an isolated browser and local player files.
const { chromium } = require('playwright');
const assert = require('node:assert/strict');
const path = require('node:path');
const { pathToFileURL } = require('node:url');

(async () => {
  const browser = await chromium.launch({ headless: true, channel: process.env.PLAYWRIGHT_CHANNEL || 'chrome' });
  try {
    const page = await browser.newPage({ viewport: { width: 1280, height: 720 } });
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.addInitScript(() => {
      window.playerEvents = [];
      window.webkit = { messageHandlers: { player: { postMessage: event => window.playerEvents.push(event) } } };
    });
    // Frozen timers keep the chrome from auto-hiding; CSS transitions still run.
    await page.clock.install();
    await page.goto(pathToFileURL(path.resolve(__dirname, '../composeApp/src/desktopMain/resources/player-ui/controls.html')).href);
    const update = state => page.evaluate(state => window.playerControls(state), state);
    const progress = (duration, position) =>
      page.evaluate(([duration, position]) => window.playerUpdate({ duration, position, paused: true }), [duration, position]);
    const settle = () => page.waitForTimeout(250);
    const preview = page.locator('#seekPreview');
    const visible = () => preview.evaluate(el => el.classList.contains('visible'));
    const text = () => page.locator('#seekPreviewTime').textContent();
    const seekRect = () => page.locator('#seek').evaluate(el => el.getBoundingClientRect().toJSON());
    const previewRect = () => page.locator('#seekPreview .seek-preview-bubble').evaluate(el => el.getBoundingClientRect().toJSON());
    // Mirrors the range input's mapping: an invisible 10px thumb insets the track by 5px per side.
    const xAt = (rect, fraction) => rect.x + 5 + fraction * (rect.width - 10);
    const y = rect => rect.y + rect.height / 2;
    const lastEvent = type => page.evaluate(type => [...window.playerEvents].reverse().find(e => e.type === type), type);

    await update({ isLoading: false, showOpeningOverlay: false, title: 'A movie', controlsVisible: true });
    await progress(6000, 100);
    assert.equal(await visible(), false, 'hidden until the bar is hovered');

    const bar = await seekRect();
    await page.mouse.move(xAt(bar, 0.25), y(bar));
    await settle();
    assert.equal(await visible(), true, 'hover shows the preview');
    assert.equal(await text(), '25:00');
    let box = await previewRect();
    assert.ok(Math.abs(box.x + box.width / 2 - xAt(bar, 0.25)) <= 1, 'centred on the pointer');
    // The bubble must track the pointer in the same frame: no position transition.
    for (const fraction of [0.3, 0.55, 0.42]) {
      await page.mouse.move(xAt(bar, fraction), y(bar));
      box = await previewRect();
      assert.ok(Math.abs(box.x + box.width / 2 - xAt(bar, fraction)) <= 1, `tracks the pointer at ${fraction} without lag`);
    }
    box = await previewRect();
    assert.ok(box.y + box.height <= bar.y - 7, 'sits above the bar');

    await page.mouse.click(xAt(bar, 0.25), y(bar));
    assert.equal((await lastEvent('scrubFinish')).value, 1500000, 'a click seeks to the previewed time');

    await page.mouse.move(bar.x + 1, y(bar));
    await settle();
    assert.equal(await text(), '00:00');
    box = await previewRect();
    assert.ok(box.x >= bar.x - 0.5, 'clamped at the start of the bar');

    await page.mouse.move(bar.x + bar.width - 1, y(bar));
    await settle();
    assert.equal(await text(), '1:40:00');
    box = await previewRect();
    assert.ok(box.x + box.width <= bar.x + bar.width + 0.5, 'clamped at the end of the bar');

    await page.mouse.move(bar.x + bar.width / 2, bar.y - 200);
    await settle();
    assert.equal(await visible(), false, 'leaving the bar hides the preview');

    await page.mouse.move(xAt(bar, 0.1), y(bar));
    await page.mouse.down();
    await page.mouse.move(xAt(bar, 0.6), y(bar), { steps: 5 });
    await page.mouse.move(xAt(bar, 0.6), bar.y - 120, { steps: 3 });
    await settle();
    assert.equal(await visible(), true, 'dragging keeps the preview off the bar');
    assert.equal(await text(), '1:00:00');
    assert.equal((await lastEvent('scrubChange')).value, 3600000);
    const toggleCount = () => page.evaluate(() => window.playerEvents.filter(e => e.type === 'setPlaybackStateQuiet').length);
    const togglesBeforeRelease = await toggleCount();
    await page.mouse.up();
    await page.clock.fastForward(400);
    await settle();
    assert.equal((await lastEvent('scrubFinish')).value, 3600000);
    assert.equal(await visible(), false, 'release away from the bar hides the preview');
    assert.equal(await toggleCount(), togglesBeforeRelease, 'releasing a drag over the video does not toggle playback');
    // WKWebView does not capture the pointer to the range input: a drag released
    // over the video sends `click` to the common ancestor (#playerRoot).
    await page.evaluate(() => {
      const seekEl = document.getElementById('seek');
      const rootEl = document.getElementById('playerRoot');
      const r = seekEl.getBoundingClientRect();
      const at = (x, y) => ({ bubbles: true, cancelable: true, composed: true, button: 0, clientX: x, clientY: y, pointerId: 1, isPrimary: true });
      seekEl.dispatchEvent(new PointerEvent('pointerdown', at(r.x + 80, r.y + 5)));
      seekEl.dispatchEvent(new PointerEvent('pointerleave', { ...at(r.x + 80, r.y - 200), bubbles: false }));
      rootEl.dispatchEvent(new PointerEvent('pointerup', at(r.x + 80, r.y - 200)));
      rootEl.dispatchEvent(new MouseEvent('click', at(r.x + 80, r.y - 200)));
    });
    await page.clock.fastForward(400);
    assert.equal(await toggleCount(), togglesBeforeRelease, 'WebKit-style drag release over the video does not toggle playback');
    await page.mouse.click(xAt(bar, 0.5), bar.y - 300);
    await page.clock.fastForward(400);
    assert.equal(await toggleCount(), togglesBeforeRelease + 1, 'a later tap on the video still toggles playback');

    await page.locator('#seek').focus();
    await page.keyboard.press('ArrowRight');
    await settle();
    assert.equal(await visible(), false, 'keyboard seeking does not show the pointer preview');

    await page.mouse.move(xAt(bar, 0.5), y(bar));
    await settle();
    assert.equal(await visible(), true);
    await update({ controlsVisible: false });
    assert.equal(await visible(), false, 'hidden chrome hides the preview');
    await update({ controlsVisible: true });
    await progress(0, 0);
    await page.mouse.move(xAt(bar, 0.4), y(bar));
    assert.equal(await visible(), false, 'no preview without a duration');

    assert.deepEqual(errors, []);
    console.log('Seek preview: hover time, centring, edge clamping, click target, drag, keyboard, hidden chrome and zero duration passed.');
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
