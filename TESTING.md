# Инструкции по тестированию Smart Home на Raspberry Pi 5

## 1. Тестирование на Android эмуляторе (без Raspberry Pi)

### Шаг 1: Создание эмулятора

1. Откройте **Android Studio**
2. **Tools** → **Device Manager**
3. Нажмите **+ Create Device**
4. Выберите **Raspberry Pi 5** (если есть) или **Raspberry Pi 3**
5. Выберите образ **Android 15 (API 35) arm64-v8a**
6. Завершите создание

### Шаг 2: Запуск эмулятора

```bash
# Запустить эмулятор через командную строку
emulator -avd <имя_эмулятора>
```

### Шаг 3: Сборка и установка

```bash
# Собрать debug APK
./gradlew assembleDebug

# Установить на эмулятор
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Шаг 4: Запуск приложения

```bash
# Запустить вручную
adb shell am start -n com.besenok.smarthome/.ui.MainActivity

# Или запустить через Android Studio (Run)
```

### Шаг 5: Тестирование функционала

Приложение автоматически использует **mock данные** (без реального ioBroker):

- **Главная** — показывает статус подключения, виджеты
- **Устройства** — список mock устройств (лампа, розетка, датчик)
- **Мониторинг** — mock датчики (температура, влажность, давление)
- **Сценарии** — mock сценарии (Доброе утро, Спокойной ночи)
- **Погода** — можно ввести координаты вручную
- **Настройки** — ввод IP, порта, логина, пароля

---

## 2. Тестирование на Raspberry Pi 5 с EmteriaOS

### Требования к оборудованию

- **Raspberry Pi 5** (4GB или 8GB RAM)
- **EmteriaOS 17** (Android 15)
- **Карты памяти** microSD U3 (для быстрой работы Room)
- **Блок питания** 27W USB-C (официальный)
- **Сенсорный дисплей** (опционально, для kiosk mode)
- **Подключение к сети** Ethernet (рекомендуется) или Wi-Fi

### Шаг 1: Подготовка Raspberry Pi 5

1. **Установите EmteriaOS 17**
   - Скачайте образ с https://emteria.com/
   - Запишите на microSD через Raspberry Pi Imager или BalenaEtcher
   - Загрузитесь с карты

2. **Настройте сеть**
   ```bash
   # Проверьте подключение
   ping 8.8.8.8
   
   # Проверьте IP адрес
   ip addr show
   ```

3. **Включите ADB**
   - **Settings** → **Developer options** → **USB debugging**
   - Или через ADB:
   ```bash
   adb shell setprop persist.sys.usb.config adb
   ```

### Шаг 2: Сборка APK для Pi 5

```bash
# Debug версия (для тестирования)
./gradlew assembleDebug

# Release версия (для production)
./gradlew assembleRelease

# APK файлы:
# Debug: app/build/outputs/apk/debug/app-debug.apk
# Release: app/build/outputs/apk/release/app-release.apk
```

### Шаг 3: Установка на Raspberry Pi 5

#### Вариант 1: Через USB

```bash
# Проверить подключение
adb devices

# Установить APK
adb install app/build/outputs/apk/debug/app-debug.apk
```

#### Вариант 2: Через Wi-Fi

```bash
# Включить Wi-Fi ADB
adb tcpip 5555

# Подключиться по Wi-Fi
adb connect <ip_адрес_pi5>:5555

# Установить APK
adb install app/build/outputs/apk/debug/app-debug.apk
```

#### Вариант 3: Через файл

```bash
# Скопировать APK на Pi 5 через USB флешку или SCP
scp app/build/outputs/apk/debug/app-debug.apk pi@<ip_адрес_pi5>:/home/pi/

# Подключиться к Pi 5
ssh pi@<ip_адрес_pi5>

# Установить APK
adb install /home/pi/app-debug.apk
```

### Шаг 4: Настройка подключения к ioBroker

1. Запустите приложение на Pi 5
2. Перейдите в **Настройки** (шестерёнка на главном экране)
3. Введите:
   - **Host**: IP адрес вашего ioBroker (например, `192.168.1.100`)
   - **Port**: `8081`
   - **Username**: имя пользователя ioBroker
   - **Password**: пароль ioBroker
4. Нажмите **Сохранить**

### Шаг 5: Проверка подключения

1. Перейдите на главный экран
2. Проверьте статус подключения (должно быть "Подключено")
3. Перейдите в **Устройства** — должен появиться список устройств из ioBroker
4. Попробуйте переключить устройство (toggle)

### Шаг 6: Настройка автозапуска на Pi 5

В EmteriaOS:
1. **Settings** → **Applications** → **Smart Home**
2. Включите **Auto-start**

Или через ADB:
```bash
adb shell settings put global auto_start_enabled 1
```

### Шаг 7: Настройка Kiosk Mode на Pi 5

Для полноэкранного режима без навигации:

```bash
# Установить Device Owner (требует подключения к ПК)
adb shell dpm set-device-owner com.besenok.smarthome/.SmartHomeApplication

# Или запустить в режиме kiosk
adb shell am start -n com.besenok.smarthome/.ui.MainActivity -S
```

### Шаг 8: Оптимизация для Pi 5

```bash
# Увеличить приоритет процесса
adb shell run-as com.besenok.smarthome renice -10 $(pgrep -f com.besenok.smarthome)

# Проверить использование памяти
adb shell dumpsys meminfo com.besenok.smarthome

# Проверить использование CPU
adb shell top -n 1 | grep com.besenok.smarthome
```

---

## 3. Тестирование с реальным ioBroker

### Шаг 1: Подготовка ioBroker

Убедитесь, что ioBroker работает и доступен:

```bash
# Проверить порт 8081
telnet 192.168.1.100 8081

# Или через браузер
http://192.168.1.100:8081
```

### Шаг 2: Настройка прав доступа

В ioBroker администраторе:
1. Откройте **Users**
2. Убедитесь, что пользователь имеет права на чтение/запись состояний
3. Проверьте, что WebSocket API включён

### Шаг 3: Тестирование устройств

1. Подключите приложение к ioBroker
2. Перейдите в **Устройства**
3. Проверьте, что устройства загружаются
4. Попробуйте переключить устройство
5. Проверьте, что состояние обновилось в ioBroker

### Шаг 4: Тестирование датчиков

1. Перейдите в **Мониторинг**
2. Проверьте, что датчики отображаются
3. Убедитесь, что значения обновляются в реальном времени

### Шаг 5: Тестирование сценариев

1. Перейдите в **Сценарии**
2. Проверьте список сценариев
3. Попробуйте выполнить сценарий

---

## 4. Отладка и диагностика на Pi 5

### Проверка подключения к ioBroker

```bash
# Проверить WebSocket подключение
adb shell netstat -an | grep 8081

# Проверить логи подключения
adb logcat | grep -i "websocket\|iobroker\|connect"
```

### Проверка состояния устройств

```bash
# Проверить, что устройства загружаются
adb shell am start -n com.besenok.smarthome/.ui.MainActivity

# Проверить логи
adb logcat -s com.besenok.smarthome
```

### Очистка данных приложения

```bash
# Сбросить настройки
adb shell pm clear com.besenok.smarthome

# Переустановить
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Проверка Room базы данных

```bash
# Подключиться к базе данных
adb shell

# Найти базу данных
find /data/data/com.besenok.smarthome -name "*.db"

# Проверить содержимое
sqlite3 /data/data/com.besenok.smarthome/databases/smarthome_database
```

### Проверка производительности Pi 5

```bash
# Проверить температуру CPU
adb shell cat /sys/class/thermal/thermal_zone0/temp

# Проверить использование памяти
adb shell free -m

# Проверить использование CPU
adb shell top -n 1

# Проверить загрузку системы
adb shell uptime
```

---

## 5. Чек-лист тестирования

### UI тесты
- [ ] Приложение запускается на Pi 5
- [ ] Все экраны отображаются корректно
- [ ] Навигация работает (табы, переходы)
- [ ] Кнопки и переключатели реагируют на касания
- [ ] Темная тема переключается

### Функциональные тесты
- [ ] Настройки сохраняются
- [ ] Устройства загружаются (mock или реальные)
- [ ] Устройства переключаются (toggle)
- [ ] Датчики отображаются с актуальными данными
- [ ] Сценарии отображаются и выполняются
- [ ] Погода загружается при вводе координат
- [ ] Время отображается корректно

### Интеграционные тесты
- [ ] Подключение к ioBroker работает
- [ ] Автопереподключение при потере связи
- [ ] Реал-тайм обновления состояний
- [ ] Ошибки сети обрабатываются корректно

### Pi 5 специфика
- [ ] Автозапуск при старте работает
- [ ] Kiosk mode активен
- [ ] Landscape ориентация корректна
- [ ] Сенсорное управление работает
- [ ] Приложение не закрывается при бездействии
- [ ] Температура CPU в норме (<80°C)
- [ ] Память не переполняется

---

## 6. Решение проблем на Pi 5

### Приложение не запускается

```bash
# Проверить логи
adb logcat | grep -i "crash\|exception\|smarthome"

# Проверить, установлен ли APK
adb shell pm list packages | grep smarthome
```

### Не подключается к ioBroker

1. Проверьте IP адрес: `ping <ip_iobroker>`
2. Проверьте порт: `telnet <ip_iobroker> 8081`
3. Проверьте логин/пароль в настройках ioBroker
4. Проверьте, что WebSocket API включён

### Нет устройств в списке

1. Проверьте, что устройства созданы в ioBroker
2. Проверьте права доступа пользователя
3. Проверьте логи: `adb logcat | grep -i "device"`

### Погода не загружается

1. Проверьте API ключ в `WeatherApiService.kt`
2. Проверьте подключение к интернету
3. Проверьте координаты в настройках погоды

### Высокая температура CPU

```bash
# Проверить температуру
adb shell cat /sys/class/thermal/thermal_zone0/temp

# Если >80°C, ограничить частоту CPU
adb shell echo "powersave" > /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor
```

### Медленная работа приложения

```bash
# Проверить использование памяти
adb shell dumpsys meminfo com.besenok.smarthome

# Проверить использование CPU
adb shell top -n 1 | grep com.besenok.smarthome

# Очистить кэш Room
adb shell run-as com.besenok.smarthome rm -rf databases/smarthome_database
```

---

## 7. Полезные команды для Pi 5

```bash
# Список подключённых устройств
adb devices

# Установка APK
adb install app/build/outputs/apk/debug/app-debug.apk

# Установка с сохранением данных
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Удаление приложения
adb uninstall com.besenok.smarthome

# Просмотр логов
adb logcat

# Фильтрация логов по приложению
adb logcat -s com.besenok.smarthome

# Очистка логов
adb logcat -c

# Проверка состояния устройства
adb shell getprop | grep ro.emteria

# Перезагрузка устройства
adb reboot

# Скриншот
adb shell screencap -p /sdcard/screenshot.png
adb pull /sdcard/screenshot.png

# Проверка температуры CPU
adb shell cat /sys/class/thermal/thermal_zone0/temp

# Проверка использования памяти
adb shell free -m

# Проверка загрузки системы
adb shell uptime

# Проверка процесса
adb shell ps | grep smarthome
```

---

## 8. Специфичные настройки для Raspberry Pi 5

### Настройка GPU для Compose

```bash
# Включить аппаратное ускорение
adb shell setprop debug.hwui.renderer skia
```

### Настройка сети для WebSocket

```bash
# Проверить firewall
adb shell iptables -L

# Разрешить порт 8081
adb shell iptables -A OUTPUT -p tcp --dport 8081 -j ACCEPT
```

### Настройка автозапуска при старте

```bash
# Создать сервис для автозапуска
adb shell echo "[Unit]
Description=Smart Home App
After=network-online.target

[Service]
Type=simple
ExecStart=/system/bin/am start -n com.besenok.smarthome/.ui.MainActivity
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target" > /etc/systemd/system/smarthome.service

# Включить сервис
adb shell systemctl enable smarthome.service
```

### Оптимизация EmteriaOS для Pi 5

```bash
# Увеличить размер кэша Dalvik
adb shell cmd package set-max-dex-cache-size 256

# Отключить анимации для производительности
adb shell settings put global window_animation_scale 0.0
adb shell settings put global transition_animation_scale 0.0
adb shell settings put global animator_duration_scale 0.0
```
