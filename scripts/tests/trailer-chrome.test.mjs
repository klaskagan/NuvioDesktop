import { readFileSync } from 'node:fs';
import vm from 'node:vm';
import assert from 'node:assert/strict';
import test from 'node:test';

const source = readFileSync(new URL('../../composeApp/src/desktopMain/resources/player-ui/controls.js', import.meta.url), 'utf8');
const activity = source.slice(source.indexOf('const noteCursorActivity ='), source.indexOf('const hideChromeFromAutoTimer ='));

for (const presentation of ['overlay', 'fullscreen']) {
  test(`${presentation}: moving over hidden chrome reveals it and notifies its owner`, () => {
    const context = vm.createContext({
      state: { controlsVisible: false }, chromeAutoHideActivity: 0,
      playbackErrorText: () => '', shouldHideCursorForIdleChrome: () => false,
      syncHiddenCursor: () => {}, renderChrome: () => {},
      events: [], send: function (type) { this.events.push(type); },
    });
    // send is called as a plain function, like the browser helper.
    vm.runInContext('send = type => events.push(type);' + activity + 'noteCursorActivity();', context);
    assert.equal(context.state.controlsVisible, true);
    assert.equal(context.chromeAutoHideActivity, 1);
    assert.deepEqual(Array.from(context.events), ['keepChromeVisible']);
    vm.runInContext('noteCursorActivity();', context);
    assert.equal(context.chromeAutoHideActivity, 1);
  });
}
