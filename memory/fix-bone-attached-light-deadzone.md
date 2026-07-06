---
name: fix-bone-attached-light-deadzone
description: "Свет (Point/Spotlight form) прицепленный к кости через BodyPart.bone не регистрировался ни scanner-, ни render-path; фикс в AbstractLightFormRenderer (master)."
metadata:
  node_type: memory
  mod_scope: IRLite-only
  type: project
  originSessionId: 2783a2db-5ee4-4509-89b5-b6d2393f8eff
---

Баг: PointLightForm/SpotlightForm, прицепленная к BodyPart с непустым bone, не светит (не регистрируется как источник). Связано: [[addon-light-collection]], [[addon-architecture]], [[addon-forms]].

Корень — граница владения scanner<->render-path не учитывает bone-binding, на пересечении двух отказов свет не регистрирует НИКТО:
- Scanner (LightCollector.walk) пропускает любую part с непустым bone (`continue`) — у него чистые world-coords без позы рига (матрицы костей this.bones живут только в ModelFormRenderer во время рендера), физически не может позиционировать кость -> делегирует render-path.
- Render-path (AbstractLightFormRenderer.render3D) регистрирует только если `!isHandledByScanner(context)`. А isHandledByScanner==true для: MODEL_BLOCK (всегда) и ENTITY в roster открытого dashboard-редактора. -> для bone-света на этих контекстах render-path уступает scanner-у, который его пропустил.
- Матрица сценариев: ModelBlock = не светил; dashboard film-превью = не светил; живой in-world актёр (isHandledByScanner false) = светил. Т.е. ломалось во всех основных кейсах редактирования.

Фикс (ветка master аддона, 2026-07-01): qualet.irlite.client.forms.AbstractLightFormRenderer.render3D — заставить render-path забирать bone-свет всегда:
```java
boolean boneAttached = this.form.getParent() instanceof BodyPart part && !part.bone.get().isEmpty();
if (boneAttached || !LightCollector.isHandledByScanner(context)) { this.registerLight(context); }
```
+ import mchorse.bbs_mod.forms.forms.BodyPart. Дедуп по System.identityHashCode(form) в LightRegistry страхует от двойной регистрации. irl-core НЕ трогали (правка per-mod, ядро пересобирать не нужно).

Механика, на которой держится фикс (bbs-fs):
- form.parent указывает на BodyPart: BodyPart.setInternalForm -> this.add(form) -> ValueGroup.add вызывает value.setParent(this). BaseValue.getParent() публичный. BaseValueGroup extends BaseValue -> BodyPart подтип BaseValue -> instanceof валиден.
- Render-path реально доходит до bone-света: ModelFormRenderer.renderBodyParts берёт this.bones.get(part.bone).matrix(), домножает стек и зовёт renderBodyPart -> FormUtilsClient.render(child, context) с ТЕМ ЖЕ context (type не меняется, остаётся ENTITY/MODEL_BLOCK), проходит гейт render3D.

Trade-off: bone-свет в dashboard-превью теперь идёт через render-path -> подвержен camera-roll багу (getInverseViewRotationMatrix не отражает preview-roll; ровно ради этого dashboard-сущности гнали через scanner). Приемлемо: «светит, но в превью при roll может уезжать» лучше чем «не светит». Полный фикс roll для кости потребовал бы отдельно протащить матрицу кости.

Статус: build-verified (compileClientJava OK, irlite 1.1 загрузился, runClient 1.20.4 поднялся до film-панели). Рантайм-подтверждение самого свечения от пользователя на 2026-07-01 ещё ожидается.
