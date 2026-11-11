# Безпека · Security

**Українською**

**Як повідомити про вразливість.** Приватно, через GitHub: вкладка Security → «Report a vulnerability» у цьому репозиторії. Будь ласка, не пишіть про вразливість у звичайних Issues, поки її не виправлено.

**Строки.**
- Підтвердження, що звіт отримано, — протягом 3 днів.
- Критичну вразливість (доступ до чужих даних, обхід захисту, підроблене оновлення) виправляю протягом 7 днів.
- Решту — у найближчому випуску.

**Що підтримується.** Лише остання версія з [Releases](https://github.com/marybezuhla/mandat/releases/latest). Виправлення виходять новою версією; старі не оновлюються.

**Мінімальна версія Android — 12** (API 31): ключ бази у сховищі ключів, що відкривається відбитком або PIN-кодом телефона, працює так з Android 11, а кольори телефона (Material You) — з Android 12; одна межа простіша для перевірки, ніж дві.

**Як перевірити, що APK справжній.** Сертифікат підпису має SHA-256 `A7:06:53:16:67:2A:C1:5B:C3:87:A6:8C:2E:A2:96:EC:8E:A2:79:42:09:13:D6:9E:C5:02:05:BB:54:8D:77:3E`. Перевірка: `apksigner verify --print-certs mandat-<версія>.apk` (apksigner є в Android SDK Build Tools). SHA-256 самого файлу опубліковано в описі кожного випуску.

**Межі.** Мандат не призначено для відомостей з грифом: для них потрібні спеціально захищені системи, а не застосунок на особистому телефоні.

---

**In English**

**How to report a vulnerability.** Privately, through GitHub: the Security tab → Report a vulnerability in this repository. Please do not describe it in public Issues until it is fixed.

**Timelines.**
- Receipt is confirmed within 3 days.
- A critical vulnerability (access to someone's data, bypassing protection, a forged update) is fixed within 7 days.
- Others in the next release.

**Supported versions.** Only the latest one in [Releases](https://github.com/marybezuhla/mandat/releases/latest). Fixes come as a new version; older ones are not updated.

**Minimum Android version: 12** (API 31). The database key in the key store, opened with a fingerprint or the phone PIN, works this way from Android 11, and the phone's colours (Material You) need Android 12; one floor is simpler to test than two.

**How to check that an APK is genuine.** The signing certificate's SHA-256 is `A7:06:53:16:67:2A:C1:5B:C3:87:A6:8C:2E:A2:96:EC:8E:A2:79:42:09:13:D6:9E:C5:02:05:BB:54:8D:77:3E`. Check: `apksigner verify --print-certs mandat-<version>.apk` (apksigner comes with the Android SDK Build Tools). The SHA-256 of the file itself is published with every release.

**Limits.** Mandat is not for classified information, which needs specially protected systems rather than an app on a personal phone.
