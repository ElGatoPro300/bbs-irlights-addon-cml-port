---
name: reference-runclient-jdk21
description: "runClient аддона требует JAVA_HOME=JDK 21 (fabric-loom 1.15.5 конфигурируется только на JVM 21); дефолтный JAVA_HOME в окружении = JDK 8 и валит сборку."
metadata:
  node_type: memory
  mod_scope: addon
  type: reference
---

Сборка/запуск bbs-irlights-addon (gradlew runClient, см. [[feedback-addon-runclient-command]]) требует JAVA_HOME на JDK 21. Проверено 2026-07-09: дефолтный JAVA_HOME окружения = C:\Program Files (x86)\Eclipse Adoptium\jdk-8.0.472.8-hotspot -> Gradle падает «requires JVM 17 or later ... uses JVM 8». JDK 17 тоже НЕ подходит: fabric-loom 1.15.5 плагин «requires at least JVM runtime version 21. This build uses a Java 17 JVM». Нужен ровно 21+.

Доступные JDK на машине: C:\Program Files\Eclipse Adoptium\{jdk-17.0.19.10-hotspot, jdk-21.0.11.10-hotspot}; на PATH `java` = jdk-21 (но gradlew берёт JAVA_HOME, не PATH). `java` для tools/PatchHarness (patcher-валидация) = 21 с PATH, ок.

Запуск (Bash-tool, аргумент -Pmc не корёжится): export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.11.10-hotspot" перед ./gradlew runClient -Pmc=1.20.4 --console=plain. Иначе см. гочу PowerShell-квотинга -Pmc в [[feedback-addon-runclient-command]].
