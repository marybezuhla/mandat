# Мандат · Mandat

Приватний офлайн-органайзер для Android: справи, люди, календар, доручення й обіцянки в одному зашифрованому місці. Українська та англійська. Безкоштовно, без реклами й акаунтів.

> **Статус: версія 1.0.** Формат резервної копії й бази даних далі змінюватиметься лише так, щоб старі копії відкривалися. Помилки й побажання: [Issues](../../issues).

## Особливості

- 🔒 **Приватність.** Дозволу на інтернет немає, дані лише на телефоні й завжди зашифровані (SQLCipher). Вхід відбитком або PIN-кодом (за бажанням лише PIN, і тоді ключ сам не приймає відбитка), автоблокування, знімки екрана заборонені. Копії, експорт, вимкнення режиму поїздки, дозвіл на знімки екрана й «Стерти всі дані» підтверджуються відбитком або PIN-кодом телефона. За бажанням — нейтральна назва й іконка. Резервна копія лише з паролем (Argon2id + AES-256).
- 🗂️ **Справи й увага.** Фокус, фон і сплячі справи, наступний крок, застряглі й без кроку, розбір давніх прострочених.
- 👥 **Люди.** Свої категорії, позиції в справах, команда, розмова віч-на-віч, довідник контактів з імпортом .vcf і CSV.
- 📅 **Календар і час.** Місяць, тиждень із сіткою годин, список; повторювані події (щодня, у кілька днів тижня, що два тижні, щомісяця, «другий вівторок», щороку; змінити можна одну дату або «цю й наступні»); щільність тижня з вільними вікнами; строки, доручення й обіцянки на одній стрічці.
- 📊 **Наочність.** Зведення за тиждень, місяць, рік; кожне число відкриває записи, з яких воно складається. Кольори завжди з підписом чи відсотком.
- 🧳 **Режим поїздки й лінза.** Позначені справи й усе пов'язане з ними (люди, записи щоденника, громади) зникають з екранів, пошуку, ШІ й експорту. Вимкнути режим можна лише відбитком чи PIN телефона. Числа й списки схованого не видають, але сам перемикач в Опціях видно; від того, хто змусить відкрити телефон, режим не захищає. Сховати можна й людину: тоді зникають її картка, прив'язані до неї доручення, обіцянки й події, а також записи з її повним іменем і справи, де воно є в назві чи описі. Згадка самим прізвищем лишається видимою, а нотатка без прив'язки ховається, лише якщо містить усі слова назви справи чи імені людини.
- 📤 **Експорт і імпорт.** .vcf, .ics, CSV, Markdown, PDF, сховище для Obsidian, пакет справ для колеги; за будь-який період. За замовчуванням зашифрований файл Мандата, ZIP з паролем — для інших застосунків.
- 🔭 **Ретроспектива.** Напрямки, пріоритети минулого й динаміка за місяцями з журналу змін.
- ✍️ **Тексти.** Листи, виступи, колонки, записки з шаблонами, версіями й експортом у Word і PDF.
- 📓 **Щоденник, бюджет, вхідні.**
- 🎛️ **Режими.** Верховна Рада, місцева рада, помічник депутата, посадовець, громадська діяльність або громадянин: змінюються органи, посади, шаблони й деякі назви; решта однакова. «Простий вигляд» лишає на екранах тільки потрібне щодня.
- 🤝 **Хто кому винен.** Не лише «я пообіцяв» і «я доручив», а й «мені пообіцяли» та «мені доручили»: окремо в списках, без впливу на звіти про ваші власні обіцянки.
- 🤖 **Локальний ШІ (Gemma), за бажанням:** розкладає вставлений текст на справи й доручення, пише бриф і підсумок, скорочує виступ. Усе на телефоні, лише пропозиції.

## Встановлення

Завантажте `.apk` з [Releases](../../releases/latest). Для автооновлень — [Obtainium](https://obtainium.imranr.dev/). Перед оновленням радимо зробити резервну копію.

ШІ (необов'язково): Опції → «ШІ» → «Завантажити модель» (≈2,6 ГБ) → «Обрати файл моделі». Файл перевіряється за контрольною сумою.

## Прозорість

- **Дозволи:** сповіщення й перезапуск нагадувань після ввімкнення телефона; читання календаря телефона — лише якщо ви ввімкнете його показ; відбиток чи PIN телефона для входу; приховування чужих вікон поверх Мандата (лише захист, без даних). Збірка зупиняється, якщо з'являється дозвіл на інтернет.
- **Шифрування:** не шифруються лише налаштування вигляду й безпеки (тема, «Лише PIN», знімки екрана, автоблокування, нейтральна іконка), вмикачі нагадувань і календаря телефона, позначка «Мандат щось скопіював у буфер» (без тексту), технічні позначки й файл моделі ШІ (без ваших даних) і дати, на які є нагадування (щоб сповіщення приходили без розблокування; у тексті сповіщення нічого немає). Android також пам'ятає вибрану теку автокопій. Відкритий файл експорту — лише за вашим вибором і з попередженням.
- **Збірка:** APK збирається з цього коду в GitHub Actions окремо від тестів і підписується лише після схвалення автора. Контрольні суми залежностей і самого Gradle перевіряються; SHA-256 кожного APK опубліковано в релізі.
- **Підпис:** справжній APK підписано сертифікатом із відбитком SHA-256 `A7:06:53:16:67:2A:C1:5B:C3:87:A6:8C:2E:A2:96:EC:8E:A2:79:42:09:13:D6:9E:C5:02:05:BB:54:8D:77:3E`. Перевірити без жодного застосунку: `apksigner verify --print-certs mandat-<версія>.apk` (з Android SDK Build Tools).
- **Копія без застосунку:** резервну копію можна відкрити на комп'ютері своїм паролем, без Мандата: `scripts/decrypt_backup.py` (там же описано будову файлу). Копія не має жодної позначки: без пароля це просто випадкові байти.
- **Межі:** не призначено для таємної та службової інформації — для неї потрібні спеціально захищені системи, а не застосунок на особистому телефоні. Режим поїздки не захищає від того, хто змусить відкрити Мандат і підтвердити вимкнення; резервні копії містять усе.
- **Як створено:** код написано за допомогою ШІ (Claude, Anthropic) під керівництвом і з тестуванням автора.

Kotlin · Jetpack Compose · Room · SQLCipher · [LiteRT-LM](https://github.com/google-ai-edge/LiteRT-LM). Ліцензія GNU GPL версії 3 або (на ваш вибір) будь-якої пізнішої (GPL-3.0-or-later). Сторонні матеріали — [NOTICE.md](NOTICE.md), приватність — [PRIVACY.md](PRIVACY.md), вразливості — [SECURITY.md](SECURITY.md).

---

# Mandat · English

A private, offline organiser for Android: matters, people, calendar, delegations and promises in one encrypted place. Ukrainian and English. Free, no ads, no accounts.

> **Status: version 1.0.** From here on the backup and database formats change only in ways that keep older backups opening. Bugs and wishes: [Issues](../../issues).

## Features

- 🔒 **Privacy.** No internet permission, data stays on the phone and is always encrypted (SQLCipher). Fingerprint or PIN to open (optionally PIN only, and then the key itself refuses a fingerprint), automatic lock, screenshots blocked. Backups, exports, turning trip mode off, allowing screenshots and Erase all data are confirmed with the phone's fingerprint or PIN. Optionally a neutral name and icon. Backups only with a password (Argon2id + AES-256).
- 🗂️ **Matters and attention.** Focus, background and sleeping matters, next steps, stuck ones and ones without a step, sorting out old overdue items.
- 👥 **People.** Your own categories, stances in matters, a team, one-to-one talks, an address book with .vcf and CSV import.
- 📅 **Calendar and time.** Month, week with an hour grid, list; repeating events (every day, on several weekdays, every two weeks, monthly, "the second Tuesday", yearly; one date or "this and later dates" can be changed on their own); week density with free windows; deadlines, delegations and promises on one strip.
- 📊 **At a glance.** Week, month and year overviews; every number opens the records it counts. Colours always come with a label or a percentage.
- 🧳 **Trip mode and lens.** Marked matters and everything linked to them (people, diary entries, communities) disappear from screens, search, AI and export. Turning trip mode off needs the phone's fingerprint or PIN. Numbers and lists do not give away what is hidden, but the switch itself is visible in Options, and the mode does not protect against someone who forces you to open the phone. A person can be hidden too: then their card, the delegations, promises and events tied to them, and the records and matters with their full name disappear. A mention by the surname alone stays visible, and a note not tied to anything is hidden only when it contains every word of the matter's title or the person's name.
- 📤 **Export and import.** .vcf, .ics, CSV, Markdown, PDF, an Obsidian vault, a packet of matters for a colleague; for any period. By default an encrypted Mandat file; a ZIP with a password for other apps.
- 🔭 **Retrospective.** Directions, past priorities and month-by-month movement from a journal of changes.
- ✍️ **Texts.** Letters, speeches, columns, notes with templates, versions and export to Word and PDF.
- 📓 **Diary, budget, inbox.**
- 🎛️ **Modes.** Verkhovna Rada, local council, a deputy's assistant, official, civic work or citizen: the bodies, positions, templates and some names change; everything else is the same. "Simple view" leaves on the screens only what is needed every day.
- 🤝 **Who owes whom.** Not only "I promised" and "I handed out", but also "promised to me" and "handed to me": listed apart, and never counted in reports about your own promises.
- 🤖 **On-device AI (Gemma), optional:** splits pasted text into matters and delegations, writes a brief and a summary, shortens a speech. Everything stays on the phone; only suggestions.

## Install

Download the `.apk` from [Releases](../../releases/latest). For updates, use [Obtainium](https://obtainium.imranr.dev/). Make a backup before updating.

AI (optional): Options → AI → Download the model (≈2.6 GB) → Choose the model file. The file is checked by its checksum.

## Transparency

- **Permissions:** notifications and restoring reminders after a restart; reading the phone's calendar only if you turn on showing it; the phone's fingerprint or PIN to open; hiding other apps' windows over Mandat (protection only, no data). The build stops if an internet permission appears.
- **Encryption:** the only things not encrypted are appearance and security settings (theme, PIN only, screenshots, automatic lock, neutral icon), the reminder and phone-calendar switches, a mark that Mandat copied something to the clipboard (without the text), technical AI marks and the model file (none of your data) and the dates that have reminders (so notifications arrive without unlocking; the notification text says nothing). Android also remembers the folder chosen for automatic backups. An open export file only by your choice and after a warning.
- **Build:** the APK is built from this code in GitHub Actions apart from the tests and signed only after the author's approval. Checksums of the dependencies and of Gradle itself are verified; each APK's SHA-256 is published with the release.
- **Signature:** the genuine APK is signed with a certificate whose SHA-256 fingerprint is `A7:06:53:16:67:2A:C1:5B:C3:87:A6:8C:2E:A2:96:EC:8E:A2:79:42:09:13:D6:9E:C5:02:05:BB:54:8D:77:3E`. To check it without any app: `apksigner verify --print-certs mandat-<version>.apk` (from the Android SDK Build Tools).
- **A backup without the app:** a backup opens on a computer with your password, without Mandat: `scripts/decrypt_backup.py` (the file layout is described there). A backup carries no marker at all: without the password it is just random bytes.
- **Limits:** not for classified or restricted official information, which needs specially protected systems rather than an app on a personal phone. Trip mode does not protect against someone who forces you to open Mandat and confirm turning it off; backups contain everything.
- **How it was made:** written with AI assistance (Claude, Anthropic), directed and tested by the author.

Kotlin · Jetpack Compose · Room · SQLCipher · [LiteRT-LM](https://github.com/google-ai-edge/LiteRT-LM). Licence: GNU GPL version 3 or (at your option) any later version (GPL-3.0-or-later). Third-party material: [NOTICE.md](NOTICE.md), privacy: [PRIVACY.md](PRIVACY.md), vulnerabilities: [SECURITY.md](SECURITY.md).
