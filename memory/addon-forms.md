---
name: addon-forms
description: "IRLite light form data model — PointLightForm and SpotlightForm fields, ranges, defaults, units."
metadata:
  node_type: memory
  mod_scope: IRLite-only
  ported_from: IRLite
  consolidated: 2026-06-18
  type: reference
  originSessionId: 7caee3ab-d073-4ed1-bfcf-8daae6a18007
---

IRLite light forms (the user-editable data model). Parent: [[addon-architecture]]. Related: [[addon-ui-config]] (editor panels), [[addon-light-collection]] (how forms become lights).

Both extend BBS mchorse.bbs_mod.forms.forms.Form, live in src/main (qualet.irlite.forms), are registered as
form types (see [[addon-architecture]]), and use BBS Value* fields (auto-serialized by BBS). Forms are placed in the
world via BBS ModelBlocks or attached to actors/replays; IRLite reads their transformed world position each frame.

PointLightForm (Link irlite:point_light, display "Point light")
- color        ValueColor  default white (rgba, alpha editable in UI)
- intensity    ValueFloat  1.0  [0..20]
- radius       ValueFloat  6.0  [0.1..64]   light reach in blocks (also point shadow far plane)
- beamStrength ValueFloat  1.0  [0..5]      volumetric beam strength
- anisotropy   ValueFloat  0.4  [-0.95..0.95]  Henyey-Greenstein g for volumetrics
- vlDensity    ValueFloat  0.05 [0.005..0.5]   volumetric density
- bulbSize     ValueFloat  0.0  [0..2]      shadow softness / penumbra (0 = use shader global)
- entitiesOnly ValueBoolean false           light lights only entities, not world (key "entities_only")
- blocksOnly   ValueBoolean false           light lights only blocks/terrain, not entities (key "blocks_only", added 2026-06-09). Mutually exclusive with entitiesOnly (UI toggles force the other off; flush() resolves to lightMask 0/1/2 into cone.z, see [[addon-light-buffer-ssbo]]). Affects diffuse+specular surface shading only; volumetric beam ignores it.
- shadows      ValueBoolean true            MASTER shadow toggle (key "shadows"). ON = light casts shadows from BOTH entities AND world blocks; OFF = light casts NO shadow at all. Renamed 2026-06-09 from old "no_entity_shadows" (default false, inverted, entity-only) — UI label "Shadows".

SpotlightForm (Link irlite:spotlight, display "Spotlight")
- color        ValueColor  white
- intensity    ValueFloat  1.0  [0..20]
- range        ValueFloat  12.0 [0.1..128]  spot reach in blocks (= shadow far plane)
- radius       ValueFloat  35.0 [1..179]    OUTER cone angle in DEGREES (full angle). NOTE: "radius" here is an angle, not a distance.
- innerRadius  ValueFloat  25.0 [1..179]    INNER cone angle in degrees (full angle), clamped <= radius
- beamStrength ValueFloat  1.0  [0..5]
- anisotropy   ValueFloat  0.4  [-0.95..0.95]
- vlDensity    ValueFloat  0.05 [0.005..0.5]
- bulbSize     ValueFloat  0.0  [0..2]
- entitiesOnly ValueBoolean false
- blocksOnly   ValueBoolean false           same as PointLightForm above (key "blocks_only", added 2026-06-09; mutually exclusive with entitiesOnly)
- shadows      ValueBoolean true            same MASTER shadow toggle as PointLightForm above (key "shadows", default ON, label "Shadows")

CONVENTIONS
- Spotlight points down LOCAL +Z (matches the editor gizmo). Direction is derived from the form's world matrix at collect time.
- Cone angles are converted to SSBO cosines as cos(angle*0.5) for outer and inner (half-angle cosine), see [[addon-light-collection]].
- intensity/color are the same for both; volumetric params (beamStrength/anisotropy/vlDensity) are per-light and flow into the SSBO vlParams, see [[addon-light-buffer-ssbo]].

Связь: IRLite-only (формы на базе BBS Form; в redactor НЕ перенесены — заменены нейтральной парой LightState <-> PlacedLight, см. [[project-irlite-base-ported]]). Держим как IRLite-источник правды по полям/диапазонам/дефолтам. Источник: память IRLite.
