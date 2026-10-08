# CML 3.0 port (Minecraft 1.21.1)

This branch compiles against the local CML 3.0 API, rather than the former BBS 2.x Modrinth dependency. The shared `irl-core` engine remains bundled separately.

## Host library

Reference artifact: `bbs-cml-3.0-dev-1-1.21.1.jar`.

Copy the matching Minecraft 1.21.1 artifact from CML's `build/libs` into this addon's `libs`. Put its `-sources.jar` alongside it to inspect the host implementation in the IDE. These generated libraries are ignored by Git; CML is a dependency and is not embedded in the addon JAR.

An alternative location can be supplied with `-PcmlJar=<absolute path>`. The build fails with an explicit path if the host library is missing. Use Java 21.

```powershell
./gradlew.bat build '-Ploader_version=0.19.5'
./gradlew.bat runClient '-Ploader_version=0.19.5' '-PrunDir=build/cml3-test'
```

The host requires Fabric Loader 0.19.5 or newer. Runtime metadata requires CML 3.0 and Minecraft 1.21.1; this branch has not been ported to the older profiles still listed in the legacy build script.

## Adapter changes

- Forms, renderers, editors and replay selection keyframe factories register through CML addon API version 2 events.
- Runtime entity lookup uses CML's integer replay indices while saved selection lists retain stable replay IDs.
- Guide dragging uses the current picking controller and instant keyframe option.
- Light track colors and icons are scoped to light forms, including body parts. Other forms with matching property IDs remain untouched.
- Form property channels use CML's native channels and undo notifications.
- Shadow revisions conservatively return UNKNOWN so moving forms are rendered every frame. The legacy 1.21.1 revision bridge was unaudited; its obsolete pose scratch adapters have been removed.

## Validation and remaining work

- Java and client compilation, packaging and remapping passed against the reference host library.
- Fabric Loader now defaults to 0.19.5, matching the host requirement.
- The isolated client loaded both API 2 entrypoints. Settings category icons, patcher controls, localized enum labels and Spanish/English language switching were verified in-game; see `irlights-settings-ui.md`.
- Verify point/spot lights, moving shadows, cookies, guides, replay filters, undo and film save/load in an isolated test world with patched BSL and Complementary.
- The former BBS 2.7 custom Light tab and custom section titles need a CML-native presentation design. Colors/icons already use native sheets.
- Settings, preset controls and patcher messages now use centralized localization keys and resources for all 22 CML locales. The constructor-based English fallback mixin has been removed. Some translations still need native linguistic review; legacy form-editor strings outside this settings migration remain separate work.
- A later host-side development run produced `crash-2026-10-08_01.25.46-client.txt`: `NoSuchMethodError` for `ModelBlockEntity.method_11016()` in `IRLiteBbsCasterSource.emitModelBlock`, reached through `ShadowBaker.collect`. This runtime linkage issue remains unresolved in this port snapshot; the settings smoke test does not establish complete shadow compatibility.
