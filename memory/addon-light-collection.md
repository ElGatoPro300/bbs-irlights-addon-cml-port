---
name: addon-light-collection
description: "How IRLite gathers lights each frame — LightCollector scanner path, render-path renderers, LightRegistry accumulation/dedup, IRLightPositionResolver."
metadata:
  node_type: memory
  mod_scope: IRLite-only
  ported_from: IRLite
  consolidated: 2026-06-18
  type: reference
  originSessionId: 7caee3ab-d073-4ed1-bfcf-8daae6a18007
---

IRLite light collection (form -> per-frame light set). Parent: [[addon-architecture]]. Feeds [[addon-light-buffer-ssbo]]. Forms: [[addon-forms]]. Renderers also draw guides: [[addon-ui-config]].

TWO REGISTRATION PATHS, one owner per source (prevents double-registration with diverging coordinate frames):
- SCANNER path (LightCollector): owns BBS ModelBlock-placed lights. Runs at renderWorld HEAD in clean world coords.
- RENDER path (AbstractLightFormRenderer.render3D): owns everything that needs render-pose — live actor entities and
  film replays (FormRenderType.ENTITY). Registers during world render, so its lights are uploaded on the NEXT frame's
  flush (1 frame stale — acceptable for moving lights).
Gate: LightCollector.isHandledByScanner(ctx) == (ctx.type == MODEL_BLOCK). The renderer registers only when NOT handled
by the scanner. Dedup by System.identityHashCode(form) keeps a form rendered multiple times per frame to one slot.

LightCollector.collect(world, cameraPos)
- Reads world.blockEntityTickers via WorldBlockEntityTickersAccessor (accessor mixin on net.minecraft.world.World).
- For each ticker within MAX_DIST 256 blocks of camera: get ModelBlockEntity -> ModelProperties (must be enabled) -> root Form.
- Builds root matrix: translate(pos+0.5 on X/Z, pos.y on Y) then multiply by ModelProperties.getTransform(). Then walk().
- walk(form, parentMatrix): respects form.visible; multiplies form.transform; if PointLightForm/SpotlightForm -> emit; then
  recurses into BodyPart children that have NO bone binding (bone-bound parts are skipped — render-path territory).
- emitPoint: transforms origin (0,0,0,1) by matrix -> world pos; calls LightRegistry.registerPoint with color/intensity/radius/etc.
- emitSpot: origin like above; direction = transform of local (0,0,1,0), normalized; outer/inner cone angles -> cosOuter/cosInner = cos(deg*0.5); calls registerSpot.

IRLightPositionResolver.resolve(ctx) (render path only)
- Strips view rotation incl. BBS camera roll: matrix = inverseViewRotationMatrix * ctx.stack.peek().positionMatrix.
- Returns camera.pos + matrix translation -> absolute world position. Spot direction on this path is computed the same way
  in SpotlightFormRenderer (transform local +Z through inverseViewRot * stack).

LightRegistry (per-frame accumulator, parallel arrays, MAX = LightBuffer.MAX_LIGHTS = 2048; было 256, поднято 2026-06-18 под слайдер редактора 0–2000 — см. [[addon-light-buffer-ssbo]])
- registerPoint(...) / registerSpot(...): find slot by identity (slot(identity): linear scan existing id[], else append, -1 if full).
  New slot initializes shadowTile = -1. type 0 = point, 1 = spot.
- Stores: pos, color, intensity, radius/range, dir, cosOuter/cosInner, entitiesOnly, anisotropy, density, beam, bulbSize, noEntityShadows, shadowTile, id.
- Accessors used by ShadowBaker to iterate lights and to write back shadow tiles: getCount, getType, getX/Y/Z, getDir{X,Y,Z}, getRange, getCosOuter, getNoEntityShadows, setShadowTile.
- flush(): LightBuffer.begin(); for each light addPoint/addSpot (passing shadowTile as float); LightBuffer.upload(); count = 0.

INVARIANTS
- Scanner registers this frame; render-path lights are from previous frame's render. Both sit in the same registry at flush time.
- shadowTile is assigned by ShadowBaker (runs before flush) and reaches the SSBO as vlParams.w; -1 means "no shadow map for this light".

Связь: IRLite-only (LightCollector + IRLightPositionResolver = BBS-адаптеры сбора, per-mod). В redactor заменены швом LightScene/PlacedLight/LightDriver ([[project-irlite-base-ported]]); сам LightRegistry уехал в irl-core. Источник: память IRLite.
