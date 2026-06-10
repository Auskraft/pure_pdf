# Публикация в RuStore

Релиз — это подписанный, обфусцированный (R8) APK. Размер ~11 МБ (arm64-v8a + armeabi-v7a).

## 1. Ключ подписи (один раз)

Создай keystore (храни `.jks` **вне репозитория**, бэкап ключа и паролей — в менеджере паролей):

```powershell
& "A:\AS\jbr\bin\keytool.exe" -genkeypair -v -storetype PKCS12 `
  -keystore C:/keys/purepdf-release.jks -alias purepdf `
  -keyalg RSA -keysize 2048 -validity 10000
```

Затем подключи его к сборке:

```powershell
Copy-Item keystore.properties.example keystore.properties
```
и впиши в `keystore.properties` путь к `.jks`, alias и пароли. Файл в `.gitignore` — в репозиторий не попадёт.

> ⚠️ Потеря `.jks`/пароля = невозможность обновлять приложение в RuStore. Обязательно сделай бэкап.

## 2. Сборка релиза

```powershell
$env:JAVA_HOME = "A:\AS\jbr"     # если java нет в PATH
.\gradlew :app:assembleRelease
```
APK: `app/build/outputs/apk/release/app-release.apk`.

Проверить подпись:
```powershell
& "A:\Dop\build-tools\35.0.0\apksigner.bat" verify --print-certs app\build\outputs\apk\release\app-release.apk
```

Заметки:
- Без `keystore.properties` релиз подписывается debug-ключом (для локального теста). Для загрузки в стор **обязателен** свой ключ.
- Предупреждения R8 «error occurred when parsing kotlin metadata» — **безвредны** (R8 в AGP 8.5 старше Kotlin 2.2; на работу не влияет, проверено).
- Поднимай `versionCode` (`app/build.gradle.kts`) на каждый новый релиз.

## 3. Чек-лист листинга (console.rustore.ru)

- [ ] Аккаунт разработчика создан и верифицирован.
- [ ] Новое приложение, applicationId `com.auskraft.purepdf`.
- [ ] Загружен `app-release.apk`.
- [ ] Название: **Pure PDF**; краткое и полное описание (можно взять из [README](README.md)).
- [ ] Иконка 512×512 — `store/icon-512.png`.
- [ ] Скриншоты телефона (3–8): библиотека, читалка, поиск, ночной режим, настройки.
- [ ] Категория: «Книги» или «Инструменты».
- [ ] Возрастной рейтинг: 0+ (контента нет, только просмотр PDF).
- [ ] E-mail поддержки: `auskraft@gmail.com`.
- [ ] **URL политики конфиденциальности** (публичная ссылка — см. ниже).

Технические требования уже выполнены: targetSdk 35, 64-бит (arm64-v8a), **нет Google Play Services** (приложение полностью офлайн), нет разрешения INTERNET.

## 4. Политика конфиденциальности (нужен публичный URL)

RuStore требует ссылку на размещённую политику. Тексты уже есть в приложении
([`DocsContent.kt`](app/src/main/java/com/auskraft/purepdf/ui/docs/DocsContent.kt)). Как в
`rotating_shift`, их можно опубликовать на GitHub Pages и дать ссылку в листинге — скажи, и я
сгенерирую `legal/` (HTML-страницы Соглашение / Конфиденциальность / Обработка ПД) для хостинга.

## 5. Оценка в приложении

`RatingManager.STORE_URL` уже ведёт на `https://www.rustore.ru/catalog/app/com.auskraft.purepdf`
(заработает после публикации): 4–5★ открывают карточку RuStore, 1–3★ — письмо на почту.
