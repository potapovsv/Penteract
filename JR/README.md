# Кросс-языковое решение: Java + Rust

## Описание
Проект демонстрирует взаимодействие Java 25 (Foreign Function & Memory API) с Rust DLL.

**Задача:**
- Java генерирует 4D куб (100×100×100×100) со случайными int значениями
- Rust вычисляет сумму всех элементов куба
- Java выводит время выполнения каждой стадии

## Структура проекта

```
.
├── rust-library/          # Rust библиотека
│   ├── Cargo.toml
│   └── src/
│       └── lib.rs
└── java-project/          # Java приложение
    ├── pom.xml
    └── src/
        ├── main/
        │   ├── java/com/example/CubeSumApplication.java
        │   └── resources/cube_sum.dll  # Скомпилированная DLL
        └── test/
```

## Требования

- Java 25 (JDK 25)
- Rust 1.70+ с toolchain для Windows
- Maven 3.8+
- Windows 10/11

## Сборка и запуск

### 1. Сборка Rust библиотеки

```bash
cd rust-library
cargo build --release
```

DLL будет создана в `rust-library/target/release/cube_sum.dll`

### 2. Копирование DLL в Java проект

DLL автоматически копируется в `java-project/src/main/resources/` при сборке.

Если нужно вручную:
```bash
copy rust-library\target\release\cube_sum.dll java-project\src\main\resources\
```

### 3. Сборка Java проекта

```bash
cd java-project
mvn clean compile
```

### 4. Запуск приложения

```bash
cd java-project
mvn exec:java -Dexec.mainClass="com.example.CubeSumApplication"
```

Или напрямую:

```bash
cd java-project
java --enable-preview -cp target/classes -Djava.library.path=src/main/resources com.example.CubeSumApplication
```

## Вывод программы

Пример вывода:

```
=== Кросс-языковое решение: Java + Rust ===
Размер 4D куба: 100 x 100 x 100 x 100
Общее количество элементов: 100,000,000

[Этап 1] Генерация куба: X.XXX сек
[Этап 2] Подготовка памяти: X.XXX сек
[Этап 3] Вызов Rust функции: X.XXX сек
[Этап 4] Проверка в Java: X.XXX сек

=== Результаты ===
Сумма (Rust): X,XXX,XXX,XXX
Сумма (Java): X,XXX,XXX,XXX
Результаты совпадают: true
Общее время: X,XXX мс
```

## Технические детали

### Rust (lib.rs)
- Функция `sum_cube_elements` принимает указатель на массив и его размер
- Возвращает сумму как `i64` для предотвращения переполнения
- Использует `#[no_mangle]` для совместимости с FFI
- Обрабатывает 100,000,000 элементов (~400 MB данных)

### Java (CubeSumApplication.java)
- Использует `Foreign Function & Memory API` (Project Panama)
- `Arena` для управления off-heap памятью
- `MemorySegment` для передачи данных в Rust
- `MethodHandle` для вызова нативной функции
- Замер времени каждой стадии с помощью `System.nanoTime()`

## Производительность

Ожидаемое время выполнения:
- Генерация куба: 2-5 сек
- Подготовка памяти: 0.5-1 сек
- Вызов Rust функции: 0.1-0.3 сек
- Проверка в Java: 0.3-0.6 сек

Rust демонстрирует высокую производительность при обработке больших массивов данных благодаря:
- Прямому доступу к памяти через сырые указатели
- Оптимизациям компилятора (release build)
- Минимальным накладным расходам на вызов функции

## Устранение неполадок

### Ошибка: Не найдена библиотека cube_sum
Убедитесь, что `cube_sum.dll` находится в:
- `java-project/src/main/resources/`
- Или в системной директории PATH
- Или добавьте `-Djava.library.path=путь_к_dll` при запуске

### Ошибка: Не найдена функция sum_cube_elements
Проверьте, что Rust библиотека скомпилирована корректно:
```bash
cd rust-library
cargo clean
cargo build --release
```

### Ошибка: Несовместимость версий Java
Убедитесь, что используется Java 25:
```bash
java -version
```

## Безопасность

- Проект использует `Arena.ofConfined()` для изоляции памяти
- Rust проверяет валидность указателя перед использованием
- Сумма возвращается как `i64` для предотвращения переполнения
