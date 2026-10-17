# Smart Home — Android приложение для ioBroker (EmteriaOS на Raspberry Pi 5)

Android-приложение для управления системой умного дома **ioBroker**, оптимизированное для **Raspberry Pi 5** с **EmteriaOS**.

## Новые функции (Alpha v0.1)

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
- `DeviceName` - модель для хранения соответствия имен
- `AppPreferencesDataSource` - сохранение в DataStore
- `ApplyDeviceNamesUseCase` - применение имен к устройствам
- `RenameDeviceDialog` - UI диалог переименования

- 🏠 **Управление устройствами** — свет, розетки, датчики
- ✏️ **Переименование устройств** — пользовательские имена с сохранением в DataStore
- 📊 **Мониторинг** — температура, влажность, давление, безопасность
- 🔄 **Сценарии и автоматизация** — правила и триггеры
- 🌤 **Погода** — текущая погода + прогноз (ручной ввод города)
- 🕐 **Точное время** — синхронизация с интернет-серверами

## Стек технологий

- **Язык:** Kotlin
- **UI:** Jetpack Compose + Material Design 3
- **Архитектура:** MVVM + Clean Architecture
- **Сеть:** Ktor Client (WebSocket + HTTP)
- **ioBroker:** WebSocket JSON-RPC 2.0 (порт 8081)
- **DI:** Hilt
- **Навигация:** Jetpack Navigation Compose
- **Асинхронность:** Kotlin Coroutines + Flow
- **Хранение:** DataStore (настройки), Room (кэш)

## Raspberry Pi 5 Особенности

### Оптимизация для Pi 5
- **ABI:** arm64-v8a (нативная поддержка Pi 5)
- **GPU ускорение:** Skia renderer для Compose
- **Память:** оптимизированная работа с Room
- **CPU:** мониторинг температуры и нагрузки

### Автозапуск
Приложение автоматически запускается при старте системы (BOOT_COMPLETED).

### Kiosk Mode
- Полноэкранный режим (fullscreen immersive)
- Отключены системные панели
- Landscape ориентация (для сенсорных дисплеев)
- Device Owner mode для production

### Без Google Play Services
- Не требует GMS
- Ручной ввод координат для погоды
- Работает на EmteriaOS без Google

## Установка на Raspberry Pi 5

### 1. Подготовка Raspberry Pi 5

1. **Установите EmteriaOS 17** (Android 15)
   - Скачайте с https://emteria.com/
   - Запишите на microSD U3 через Raspberry Pi Imager

2. **Настройте сеть**
   ```bash
   # Проверьте подключение
   ping 8.8.8.8
   
   # Проверьте IP
   ip addr show
   ```

3. **Включите ADB**
   - **Settings** → **Developer options** → **USB debugging**

### 2. Сборка APK

```bash
# Debug версия (для тестирования)
./gradlew assembleDebug

# Release версия (для production)
./gradlew assembleRelease
```

### 3. Установка на Pi 5

```bash
# Проверить подключение
adb devices

# Установить APK
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 4. Настройка автозапуска

В EmteriaOS:
1. **Settings** → **Applications** → **Smart Home**
2. Включите **Auto-start**

### 5. Настройка Kiosk Mode

```bash
# Device Owner mode (для production)
adb shell dpm set-device-owner com.besenok.smarthome/.SmartHomeApplication
```

### 6. Настройка подключения к ioBroker

1. Запустите приложение
2. Перейдите в **Настройки**
3. Укажите:
   - **Host** — IP адрес ioBroker (например, `192.168.1.100`)
   - **Port** — `8081`
   - **Username** — имя пользователя ioBroker
   - **Password** — пароль ioBroker

### 7. Настройка погоды

1. Перейдите в раздел **Погода**
2. Нажмите **Изменить город**
3. Введите координаты (например, Москва: `55.7558, 37.6173`)

## Структура проекта

```
app/src/main/java/com/besenok/smarthome/
├── SmartHomeApplication.kt     # Приложение, Hilt
├── BootReceiver.kt             # Автозапуск при старте
├── di/                          # Hilt модули DI
├── data/
│   ├── remote/                  # API клиенты (ioBroker, Weather, Time)
│   ├── local/                   # DataStore, Room
│   └── repository/              # Реализации репозиториев
├── domain/
│   ├── model/                   # Домашние модели
│   ├── repository/              # Интерфейсы репозиториев
│   └── usecase/                 # Use cases
└── ui/
    ├── MainActivity.kt          # Точка входа (Kiosk mode)
    ├── theme/                   # Material 3 тема
    ├── navigation/              # Навигация
    ├── screens/                 # Экраны
    │   ├── dashboard/           # Главный экран
    │   ├── devices/             # Управление устройствами
    │   ├── sensors/             # Мониторинг
    │   ├── scenarios/           # Сценарии
    │   ├── weather/             # Погода (с ручным вводом города)
    │   ├── time/                # Точное время
    │   └── settings/            # Настройки подключения
    ├── components/              # Переиспользуемые компоненты
    └── common/                  # Общие классы (UiState)
```

## Архитектура

### Data Flow

```
UI (Compose) → ViewModel → UseCase → Repository → API/Local Storage
```

### ioBroker WebSocket JSON-RPC

Приложение подключается к ioBroker через WebSocket на порту 8081, используя протокол JSON-RPC 2.0:

- **Подключение:** `ws://{host}:8081`
- **Аутентификация:** JSON-RPC метод `authenticate`
- **Управление:** `getState`, `setState`, `getObject`, `subscribe`
- **Автопереподключение:** экспоненциальная задержка (1s → 30s)

### Ключевые JSON-RPC методы

| Метод | Описание |
|-------|----------|
| `authenticate(user, pass)` | Авторизация |
| `getObject(id)` | Получить объект |
| `getState(id)` | Получить состояние |
| `setState(id, value, ack)` | Установить состояние |
| `subscribe(id)` | Подписаться на обновления |
| `device.list` | Список устройств |

## Сборка

```bash
# Debug APK
./gradlew assembleDebug

# Release APK
./gradlew assembleRelease

# Запуск тестов
./gradlew test
```

## Требования к оборудованию

- **Raspberry Pi 5** (4GB или 8GB RAM)
- **EmteriaOS 17** (Android 15)
- **microSD U3** (для быстрой работы Room)
- **Блок питания** 27W USB-C
- **Сенсорный дисплей** (опционально)
- **Подключение к сети** Ethernet или Wi-Fi
- **ioBroker** сервер (любая платформа)

## Лицензия

MIT
