# IRLights settings presentation in CML 3.0

## Approved scope and boundaries

The settings adapter supplies category icons, localized labels and preset/patcher controls. This change does not modify CML, render passes, shader patches or saved configuration values. Existing host widgets and addon registration events remain the integration points.

## Implementation

- Inject custom controls after `refreshOptions`, the path used both when opening the overlay and when selecting a category. Injecting only after `refresh` misses category selection and leaves the patcher blank.
- Scope category icons to the parent `irlights` module. Other modules may reuse category IDs and keep their own icons.
- Register the addon's source pack and localization files with the public CML API. Remove the old localization-constructor injection, which only supplied English defaults.
- Keep settings enum descriptions in the client presentation layer, without introducing client localization imports into common registration code.
- Use centralized `IRLightsUIKeys` for preset controls and whole patcher messages. Shaderpack filenames, patch filenames and targets are user data and retain their original spelling.
- Supply every supported CML locale. Verify key/placeholder parity and review terminology; automated translation drafts do not by themselves establish native linguistic correctness.

## Verification

Build against the local CML 3.0 library. In the client, switch categories repeatedly, search for the patcher, switch languages, inspect all six category icons and exercise validation without applying a patch. Confirm other modules' icons are unaffected. Applying patches is outside this UI validation scope.

Completed on 2026-10-08:

- Java/client compilation and full remapped build passed against the local CML 3.0 library.
- All 22 locale JSON files contain 134 keys each; JSON validity, placeholder parity and import ordering passed.
- Minecraft 1.21.1 loaded the addon on both API 2 entrypoints and loaded its English/Spanish resource files.
- In the isolated test world, all six IRLights category icons rendered correctly; BBS kept its original icons.
- Selecting the patcher showed shaderpack/patch lists, folder/refresh controls, the new-pack toggle, validation/application buttons and localized status text. Validation without a selection displayed the expected warning.
- Switching Spanish → English → Spanish reloaded the IRLights categories, presets and patcher text without a restart. Shadow quality displayed its localized mode name.
- A shader patch was not applied during this presentation test. Search filtering was not tested interactively.

Translation review: Google Translate drafts were produced with explicit user authorization. English descriptions were rewritten to avoid ambiguous rendering terminology; Spanish was manually revised, and ray sampling, contours, dithering, custom presets and bloom terminology were corrected across locales. Native linguistic verification of every sentence remains uncertain, especially for Azerbaijani, Thai and Urdu; these files need native review before claiming linguistically complete coverage. Structural parity is not a substitute for that review.
