@AGENTS.md

Claude Code does not discover `.agents/skills/` on its own. As `AGENTS.md` says, read
`.agents/skills/nuvio-project-patterns/SKILL.md` directly before implementing.

Label every review launch with the task, so it shows "Nuvio · <task>" instead of "MainKt":

```sh
./gradlew :composeApp:run -Pnuvio.desktop.devName="<short task name>"
```

Run full-app desktop UI tests with `-I scripts/desktop-ui-tests.init.gradle`.
