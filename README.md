# Smart Home Monitor — Android приложение для ioBroker (EmteriaOS на Raspberry Pi 5)

Android-приложение для мониторинга и управления системой умного дома **ioBroker**, оптимизированное для **Raspberry Pi 5** с **EmteriaOS**.

---

## 🌟 Основные функции

### 🏠 Управление устройствами
- Автоматическая классификация устройств по типам (Свет, Розетки, Датчики, Ворота, Шторы, Другое)
- Умное определение типа по ролям каналов (weighted scoring)
- Фильтрация устройств по вкладкам
- Переименование и удаление устройств
- Хранение имён в DataStore

### 📡 Работа с адаптерами ioBroker
- Автоматическое обнаружение всех адаптеров в системе
- Индикатор активности адаптера (зелёный — активен, серый — неактивен)
- Просмотр списка устройств каждого адаптера
- Выбор и добавление конкретных устройств

### 🌤 Погода
- 5-дневный прогноз от OpenWeatherMap API (Forecast 2.5)
- Полная информация: температура, влажность, давление, ветер, облачность, осадки, видимость
- Отображение на Dashboard на всю ширину экрана
- Автообновление каждый час

### 🕐 Время
- Точное время с синхронизацией с интернет-серверами

---

## 📱 Версия Alpha v0.6.1 — Иконка приложения и тёмный splash

### 🎨 Иконка приложения
- Адаптивная иконка из PNG логотипа `logo_splash.png`
- Тёмный фон `#0A0E14` для минимизации системного Android splash

### Техническая реализация:
- `ic_launcher.xml` — adaptive icon с `logo_splash.png` в foreground
- `Theme.SmartHomeMonitor` — `windowBackground` тёмного цвета

---

## 📱 Версия Alpha v0.6 — Автоматическая классификация устройств

### 🎯 Умное определение типа устройства
Автоматический анализ ролей каналов по аналогии с ioBroker type-detector:

**Критерии классификации (weighted scoring):**
| Тип | Скор | Критерии |
|-----|------|----------|
| THERMOSTAT | 10 | temperature + setpoint/hysteresis/heating |
| BLIND | 9 | curtain/blind/jalousie/roll + level |
| GATE | 9 | gate/garage/door |
| LIGHT | 8 | switch.light/dimmer/color |
| SOCKET | 7 | socket/outlet/main switch |
| SENSOR | N+2 | motion/magnet/illumination/humidity/pressure/water/gas/smoke |
| Tuya numeric | 6 | >50% числовых каналов (DP IDs) |

**Fallback:** определение по имени устройства (`mapDeviceType()`)

### 📑 Фильтрация по вкладкам
- **Все** — полный список устройств
- **Свет** — устройства с типом LIGHT
- **Розетки** — устройства с типом SOCKET
- **Датчики** — устройства с типом SENSOR

Вкладки автоматически пересчитывают группы при переключении.

### 🔧 Улучшенная карточка деталей устройства
- Переключатель (Switch) показывается **только** для булевых значений (`true`/`false`, `on`/`off`)
- Строковые булевы значения нормализуются: `"true"`/`"on"`/`"1"` → `"Вкл"`, `"false"`/`"off"`/`"0"` → `"Выкл"`
- Для не-булевых значений шрифт "Значение:" увеличен в 2 раза (`headlineSmall`)

### 🧪 Точность классификации
- **Mihome устройства:** 100% (датчики температуры, движения, открытия корректно определены как SENSOR)
- **Tuya розетки:** 100% (определены как SOCKET по числовым DP ID)
- **MQTT водосчётчики:** определены как SENSOR (fallback OTHER)

### 📱 Версия Alpha v0.6.2 — AES-256-GCM шифрование паролей

#### 🔐 Безопасность
- **Пароль зашифрован** через `CryptoHelper` (AES-256-GCM через Android Keystore)
- Ключ генерируется автоматически при первом использовании и хранится в hardware-backed Keystore
- Пароль хранится в DataStore как Base64(IV + ciphertext)
- Расшифровка происходит прозрачно при каждом чтении
- Base64 больше не используется как "аутентификация" — только для хранения

#### Техническая реализация:
- `CryptoHelper` — AES/GCM/NoPadding через Android Keystore
- `getSecretKey()` — автоинициализация ключа если отсутствует
- `encrypt()` / `decrypt()` — прозрачное шифрование/дешифрование
- Ключ хранится в hardware-backed Android Keystore (не может быть извлечён)

---

### 📱 Версия Alpha v0.6.3 — Token auth (устарела в v0.7.0)

- Заменена на HTTP Basic Auth (ioBroker не поддерживает /auth endpoint)
- Подробнее в v0.7.0

---

### 📱 Версия Alpha v0.7.0 — HTTP Basic Auth + SRP рефакторинг

#### 🔐 Аутентификация
- **HTTP Basic Auth** — стандартный протокол ioBroker REST API (Authorization: Basic base64)
- **Пароль зашифрован** через `CryptoHelper` (AES-256-GCM через Android Keystore)
- Ключ генерируется автоматически при первом использовании и хранится в hardware-backed Keystore
- Расшифровка происходит прозрачно при каждом запросе к API
- SSL pinning disabled для работы с самоподписанными сертификатами ioBroker

#### ⚡ Rate Limiting
- Sliding window алгоритм ограничения запросов
- **10 запросов в секунду** на клиенте для предотвращения перегрузки ioBroker API
- Встроен в `IobrokerRestClient` через Hilt DI

#### 🛠 SRP Рефакторинг — разделение ответственности
`IobrokerApiService` (~550 строк) разделён на фокусированные классы:

| Класс | Ответственность |
|-------|----------------|
| `IobrokerRestClient` | REST API координатор, HTTP запросы, connection management, rate limiting |
| `AdapterService` | Управление адаптерами ioBroker (getAdapterList, alive status) |
| `DeviceParser` | Парсинг устройств из JSON объектов |
| `DeviceClassifier` | Weighted scoring классификация устройств по каналам (140 строк алгоритма) |
| `JsonUtils` | Общие JSON утилиты (getString, parseValue) |
| `TypeMapper` | Единый маппинг типов устройств и контролов |

**Результат:** каждый класс решает одну задачу, легко тестировать и поддерживать.

#### 📡 REST API вместо WebSocket
- Все запросы к ioBroker через HTTP REST API (порт 8087)
- Endpoints: `/json/getPlainValue`, `/json/set`, `/json/get`, `/objects`, `/states`
- Более предсказуемое поведение, проще отладка

#### 🔄 Автоматическое управление соединением
- Keep-alive механизм каждые 30 секунд
- Auto-reconnection с exponential backoff (1s → 30s max)
- Graceful degradation при потере соединения

![Dashboard](https://github.com/aeekfors-del/smart-home/raw/master/docs/screenshots/v0.6/v0.6-dashboard.png)
*Главный экран — Dashboard с погодой и статусом ioBroker*

![Устройства с фильтрацией](https://github.com/aeekfors-del/smart-home/raw/master/docs/screenshots/v0.6/v0.6-devices-filter.png)
*Экран устройств — фильтрация по вкладкам (Свет, Розетки, Датчики)*

![Детали устройства](https://github.com/aeekfors-del/smart-home/raw/master/docs/screenshots/v0.6/v0.6-device-detail.png)
*Карточка деталей — нормализованные значения и умный переключатель*

### Техническая реализация:
- `IobrokerApiService.detectDeviceTypeFromChannels()` — анализ ролей каналов с взвешенным скорингом
- `DeviceListViewModel.setFilter()` — пересчёт групп при смене фильтра
- `DeviceDetailScreen.isBooleanValue()` — определение булевых значений
- `DeviceDetailScreen.formatValueCompact()` — нормализация строковых булевых

---

## 📱 Версия Alpha v0.5 — Расширенный прогноз погоды

### Прогноз с полной информацией (8 объектов, 25 полей)
- **Температура:** текущая, ощущается как, min/max за день
- **Влажность и давление** (в мм рт. ст.)
- **Ветер:** скорость, направление, порывы
- **Облачность:** процент покрытия
- **Осадки:** вероятность в процентах
- **Видимость:** в километрах

### Автоматическое обновление
- Загрузка при старте приложения
- Обновление каждый час (3600 секунд)

### Как использовать:
1. Откройте **Dashboard** (главный экран)
2. Прокрутите вниз до секции "Прогноз на 5 дней"
3. Прогноз загружается автоматически из OpenWeatherMap
4. Обновляется автоматически каждый час

### Техническая реализация:
- `OpenWeatherForecastResponse` — модель для Forecast 2.5 API (25 полей)
- `WeatherInfo.ForecastEntry` — доменная модель с полным парсингом
- `WeatherRepositoryImpl.getForecast()` — группировка по дням с выбором записи к полудню
- `ForecastDayCardCompact` — UI виджет с полной информацией
- `DashboardViewModel.scheduleWeatherUpdates()` — автообновление каждый час
- OpenWeatherMap API: `lat`, `lon`, `appid`, `units=metric`, `lang=ru`

---

## 📱 Версия Alpha v0.4 — Управление адаптерами и устройствами

### Добавление устройств из адаптеров ioBroker
- Просмотр списка всех адаптеров ioBroker
- Индикатор активности адаптера (зелёный/серый)
- Выделение выбранного адаптера цветом
- Выбор и добавление устройств из адаптера
- Автоматическая загрузка устройств через WebSocket API

### Управление устройствами
- **Удаление** — кнопка 🗑️ на карточке устройства
- **Переименование** — кнопка ✏️ на карточке устройства
- Пользовательские имена сохраняются в DataStore
- Оригинальные имена от ioBroker сохраняются

### Как использовать:
1. Нажмите **"+"** в правом верхнем углу экрана
2. Выберите адаптер из списка (admin, mihome, mqtt и др.)
3. Выберите устройства для добавления
4. Нажмите **"Добавить"** — устройства появятся в списке
5. Используйте 🗑️ для удаления и ✏️ для переименования

### Техническая реализация:
- `GetAdaptersUseCase` — загрузка списка адаптеров
- `GetDevicesFromAdapterUseCase` — загрузка устройств из адаптера
- `IobrokerApiService.getAdapterList()` — получение адаптеров с alive статусом
- `IobrokerApiService.getAdapterDevices()` — парсинг устройств из states
- `AdapterInfo` — модель адаптера (id, name, alive, connected, devicesCount)
- `AddDeviceDialog` — UI диалог выбора адаптера/устройств
- `DeviceListViewModel` — управление списком устройств
- DataStore: `added_adapters` и `added_device_ids` для хранения

---

## 🌤 Версия Alpha v0.3 — Погода на Dashboard

### Прогноз погоды
- 5-дневный прогноз от OpenWeatherMap API (Forecast 2.5)
- Отображение на Dashboard в нижней строке (на всю ширину)
- Компактные карточки с рамкой (1.5x размер)
- День недели, иконка погоды, температура
- Автоматическая загрузка при открытии Dashboard

### Как использовать:
1. Откройте **Dashboard** (главный экран)
2. Прокрутите вниз до секции "Прогноз на 5 дней"
3. Прогноз загружается автоматически из OpenWeatherMap

### Техническая реализация:
- `OpenWeatherForecastResponse` — модель для Forecast 2.5 API
- `GetForecastUseCase` — use case для загрузки прогноза
- `WeatherRepositoryImpl.getForecast()` — группировка по дням
- `ForecastWidgetCard` — UI виджет на Dashboard
- Исправление конвертации timestamp (секунды → миллисекунды)

---

## 🔄 Версия Alpha v0.2 — Переименование устройств

### Переименование устройств
- Пользовательские имена для устройств с сохранением в DataStore
- Оригинальные имена от брокера (MiHome/Tuya) сохраняются
- Автоматическое применение пользовательских имен при загрузке
- Интуитивный UI: кнопка ✏️ на карточке устройства

### Как использовать:
1. Откройте экран **Устройства**
2. Найдите карточку устройства
3. Нажмите на иконку ✏️ в правом верхнем углу
4. Введите новое имя в диалоге
5. Нажмите **Сохранить** — имя применится автоматически

### Техническая реализация:
- `DeviceName` — модель для хранения соответствия имен
- `AppPreferencesDataSource` — сохранение в DataStore
- `ApplyDeviceNamesUseCase` — применение имен к устройствам
- `RenameDeviceDialog` — UI диалог переименования

---

## Стек технологий

- **Язык:** Kotlin 2.2.10
- **UI:** Jetpack Compose (без XML) + Material Design 3
- **Архитектура:** MVVM + Clean Architecture
- **Сеть:** Ktor Client (WebSocket + HTTP)
- **ioBroker:** WebSocket JSON-RPC 2.0 (порт 8081)
- **DI:** Hilt
- **Навигация:** Jetpack Navigation Compose
- **Асинхронность:** Kotlin Coroutines + Flow
- **Хранение:** DataStore (настройки и устройства), Room (кэш)
- **Сборка:** Gradle 9.6.0 + AGP 9.4.1

## Сборка

```powershell
# Debug APK
powershell -ExecutionPolicy Bypass -File "build.ps1" assembleDebug installDebug

# Или через Gradle
./gradlew assembleDebug
```

## Требования к оборудованию

- **Raspberry Pi 5** (4GB или 8GB RAM)
- **EmteriaOS 17** (Android 15)
- **microSD U3** (для быстрой работы Room)
- **Блок питания** 27W USB-C
- **Сенсорный дисплей** (опционально)
- **Подключение к сети** Ethernet или Wi-Fi
- **ioBroker** сервер (любая платформа)
