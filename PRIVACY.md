# Приватність · Privacy

**Українською**

Мандат не має дозволу на інтернет: він нічого не надсилає й нічого не завантажує сам.

**Які дані є і де вони лежать**
- Справи, люди, календар, доручення, обіцянки, тексти, щоденник, бюджет, вхідні й довідник контактів — лише на цьому телефоні, у базі, зашифрованій SQLCipher. Ключ лежить у сховищі ключів Android і відкривається відбитком або PIN-кодом телефона.
- Без шифрування лишаються: налаштування вигляду й безпеки (тема, «Лише PIN», знімки екрана, автоблокування, нейтральна іконка), вмикачі нагадувань і календаря телефона, позначка «Мандат щось скопіював у буфер» (без тексту), технічні позначки й файл моделі ШІ (без ваших даних) і дати, на які є нагадування. Android також пам'ятає вибрану теку автокопій.
- Хмарних копій і перенесення Android немає (`allowBackup="false"`, усе виключено). Резервна копія — лише у файл чи теку, які обираєте ви, і завжди з паролем (Argon2id + AES-256). Копія містить усе, разом зі справами, схованими режимом поїздки.
- Експорт за замовчуванням — зашифрований файл Мандата; ZIP з паролем — для інших застосунків. Відкритий файл (.vcf, .ics, CSV, Markdown, PDF, Word) — лише за вашим вибором і після попередження: його прочитає кожен, хто має файл.

**Що бачать сторонні**
- **Тека автокопій.** Якщо це тека іншого застосунку (наприклад, хмарного диска), той застосунок може надіслати зашифровану копію на свої сервери. Мандат попереджає про це під час вибору теки.
- **Телефон, SMS, пошта, WhatsApp, Signal** — лише коли ви самі натискаєте, щоб подзвонити чи написати людині: Мандат передає номер або адресу цьому застосунку. Дзвінків і повідомлень Мандат не читає.
- **Календар телефона** — лише якщо ви ввімкнете його показ: Мандат читає події, показує їх поруч зі своїми й ніде не зберігає. Коли ви самі додаєте подію з Мандата в календар телефона, туди передаються лише назва, час і місце.
- **Контакти телефона** — лише один контакт, який ви самі обираєте, щоб додати його в довідник.
- **«Подякувати» і звіт по округу** — текст подяки за виконане доручення чи звіт ви самі надсилаєте через застосунок, який оберете (звіт — після підтвердження відбитком або PIN-кодом телефона).
- **«Поділитися» з інших застосунків** — текст потрапляє у «Вхідні» лише після вашого підтвердження.
- **Hugging Face** — лише якщо ви самі відкриваєте сторінку моделі ШІ, щоб її завантажити: це робить браузер, а не Мандат.
- **Сайти з листа авторки в «Про застосунок»** (офіційні збори на підтримку України) — лише коли ви самі відкриваєте посилання: як і будь-який сайт, вони бачать адресу, з якої ви заходите.
- **GitHub** бачить адресу телефона, коли ви завантажуєте APK чи оновлення (самі або через Obtainium). Сам Мандат до GitHub не звертається.
- **Клавіатура** не вчиться на тому, що ви пишете в Мандаті: застосунок просить її про це в кожному полі. Скопійоване з Мандата позначається як чутливе (на Android 13 і новіших) і прибирається з буфера, коли ви блокуєте Мандат кнопкою, коли він блокується сам після бездіяльності, коли гасне екран або під час наступного відкриття. Якщо ви просто перейшли в інший застосунок, скопійоване лишається в буфері, щоб його можна було вставити.
- Аналітики, реклами, акаунтів і звітів про збої назовні немає.

**Як усе стерти**
- Опції → Дані та копії → «Стерти всі дані»: після підтвердження PIN-кодом чи відбитком телефона видаляються база з усіма записами, ключ шифрування, дати нагадувань і доступ до теки автокопій. Лишаються налаштування вигляду, знімків екрана й автоблокування та модель ШІ (у них немає ваших даних). Нагадування й «Лише PIN» вимикаються: їх можна ввімкнути знову.
- Видалення застосунку (системне питання Android) прибирає все, що він зберігав на телефоні, разом із ключем і моделлю ШІ.
- Ваші файли резервних копій та експорту лишаються там, куди ви їх зберегли: їх видаляєте ви.

**Межі.** Мандат не призначено для відомостей з грифом. Режим поїздки не захищає від того, хто змусить відкрити Мандат і підтвердити вимкнення режиму.

---

**In English**

Mandat has no internet permission: it sends nothing and downloads nothing by itself.

**What data there is and where it lives**
- Matters, people, the calendar, delegations, promises, texts, the diary, the budget, the inbox and the address book: only on this phone, in a database encrypted with SQLCipher. The key is in the Android key store and opens with your fingerprint or the phone PIN.
- Not encrypted: appearance and security settings (theme, PIN only, screenshots, automatic lock, neutral icon), the reminder and phone-calendar switches, a mark that Mandat copied something to the clipboard (without the text), technical AI marks and the model file (none of your data) and the dates that have reminders. Android also remembers the folder chosen for automatic backups.
- No Android cloud backup or device transfer (`allowBackup="false"`, everything excluded). Backups go only to a file or folder you choose, always with a password (Argon2id + AES-256). A backup contains everything, including matters hidden by trip mode.
- Export is an encrypted Mandat file by default; a ZIP with a password is for other apps. An open file (.vcf, .ics, CSV, Markdown, PDF, Word) only by your choice and after a warning: anyone who has the file can read it.

**What others can see**
- **The automatic backup folder.** If it belongs to another app (a cloud drive, for example), that app may send the encrypted copy to its servers. Mandat warns about this when you choose the folder.
- **Phone, SMS, email, WhatsApp, Signal**: only when you tap to call or write to a person yourself. Mandat hands the number or address to that app. Mandat never reads calls or messages.
- **The phone's calendar**: only if you turn on showing it. Mandat reads the events, shows them next to its own and saves them nowhere. When you add an event from Mandat to the phone's calendar yourself, only its title, time and place are handed over.
- **The phone's contacts**: only the one contact you pick yourself to add to the address book.
- **Saying thanks and the district report**: the thank-you text for a finished delegation, or the report, is sent by you, through the app you choose (the report after you confirm with the phone's fingerprint or PIN).
- **Share from other apps**: the text reaches the Inbox only after you confirm.
- **Hugging Face**: only if you open the AI model's page yourself to download it. The browser does this, not Mandat.
- **The sites in the author's letter** (official fundraisers for Ukraine): only when you open a link yourself; like any site, they see the address you come from.
- **GitHub** sees your phone's address when you download the APK or an update (yourself or through Obtainium). Mandat itself never contacts GitHub.
- **The keyboard** does not learn from what you type in Mandat: the app asks it not to in every field. What you copy from Mandat is marked sensitive (on Android 13 and newer) and taken off the clipboard when you lock Mandat with the button, when it locks itself after idling, when the screen turns off, or the next time it is opened. If you simply switch to another app, the copy stays on the clipboard so that it can be pasted.
- No analytics, ads, accounts or crash reports sent anywhere.

**How to erase everything**
- Options → Data and backups → Erase all data: after you confirm with the phone's PIN or fingerprint, the database with every record, the encryption key, the reminder dates and the access to the automatic backup folder are removed. Appearance, screenshot and automatic-lock settings and the AI model stay (they hold none of your data). Reminders and PIN only are switched off: you can turn them on again.
- Uninstalling the app (Android asks to confirm) removes everything it kept on the phone, the key and the AI model included.
- Your backup and export files stay where you saved them: you delete those yourself.

**Limits.** Mandat is not for classified information. Trip mode does not protect against someone who forces you to open Mandat and confirm turning it off.
