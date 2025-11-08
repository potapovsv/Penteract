# Глубокий анализ архитектуры Mondrian и оптимизация с Java 25

## 1. Анализ текущей архитектуры

### 1.1 Основные компоненты и их взаимодействие

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   XML Schema    │───▶│ Code Generator   │───▶│  MondrianDef    │
│   (Mondrian.xml)│    │  (maven-antrun)  │    │     Classes     │
└─────────────────┘    └──────────────────┘    └─────────────────┘
                                │                        │
                                ▼                        ▼
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   FoodMart DB   │◀───│  JDBC Driver     │◀───│   OLAP Engine   │
│   (MySQL 8.0)   │    │  (MySQL Conn/J)  │    │   (Core Logic)  │
└─────────────────┘    └──────────────────┘    └─────────────────┘
```

### 1.2 Выявленные узкие места производительности

#### A. **Генерация кода (Code Generation Bottleneck)**
- **Файл**: `mondrian/mondrian/pom.xml` (maven-antrun-plugin)
- **Проблема**: Синхронная генерация кода в фазе `generate-sources`
- **Влияние**: Увеличивает время сборки, блокирует параллельную компиляцию
- **Детали**:
  ```xml
  <ant target="def" />
  <ant target="generate.properties" />
  <ant target="generate.resources" />
  <ant target="parser" />
  <ant target="version" />
  ```

#### B. **XML парсинг и DOM обработка**
- **Файл**: `mondrian/mondrian/src/generated/java/mondrian/olap/MondrianDef.java`
- **Проблема**: Использование Eclipse XOM с DOM парсингом
- **Влияние**: Высокое потребление памяти, медленный парсинг больших схем
- **Пример проблемного кода**:
  ```java
  public CubeUsages(org.eigenbase.xom.DOMWrapper _def)
      throws org.eigenbase.xom.XOMException
  {
      org.eigenbase.xom.DOMElementParser _parser = new org.eigenbase.xom.DOMElementParser(_def, "", MondrianDef.class);
      _tempArray = _parser.getArray(CubeUsage.class, 1, 0); // DOM обход
  }
  ```

#### C. **Интеграционные тесты с Docker**
- **Файл**: `mondrian/mondrian/pom.xml` (docker-maven-plugin)
- **Проблема**: Создание нового MySQL контейнера для каждого тестового прогона
- **Влияние**: Длительное время подготовки тестов (~30-60 сек)
- **Детали**:
  ```xml
  <name>mysql:8.0.27</name>
  <alias>mondrian-mysql8027-db</alias>
  ```

#### D. **JDBC пул соединений**
- **Файл**: Конфигурация зависимостей
- **Проблема**: Отсутствие оптимизированного пула соединений
- **Влияние**: Накладные расходы на создание/закрытие соединений

#### E. **Многопоточность и синхронизация**
- **Файл**: `mondrian/src/generated/java/mondrian/olap/MondrianDef.java`
- **Проблема**: Синхронные методы `display()`, `displayDiff()` без оптимизации
- **Влияние**: Блокировки при отладке и логировании

## 2. Анализ существующих зависимостей

### 2.1 Java версии и совместимость
- **Текущая база**: Java 8-11 (судя по зависимостям)
- **Servlet API**: 2.4 (устаревшая версия)
- **JUnit**: 3.8.1 (очень старая версия)

### 2.2 Критические зависимости для производительности
```xml
<dependency>
    <groupId>eigenbase</groupId>
    <artifactId>eigenbase-xom</artifactId>
    <version>1.3.5</version>
</dependency>
<dependency>
    <groupId>mysql</groupId>
    <artifactId>mysql-connector-java</artifactId>
    <version>8.0.27</version>
</dependency>
```

## 3. Стратегия миграции на Java 25

### 3.1 Фазы миграции

#### Фаза 1: Подготовка (Java 17 → Java 21)
1. Обновление зависимостей
2. Миграция с JUnit 3 на JUnit 5
3. Замена устаревших API

#### Фаза 2: Java 25 Preview Features (Java 21 → Java 25)
1. Включение preview features
2. Адаптация генерации кода
3. Оптимизация критических путей

#### Фаза 3: Production Ready (Java 25 GA)
1. Стабилизация
2. Мониторинг производительности
3. Fine-tuning

## 4. Оптимизации с использованием Java 25

### 4.1 Virtual Threads для I/O операций

**Проблема**: JDBC операции блокируют потоки
**Решение**: Виртуальные потоки для concurrent запросов

```java
// mondrian/olap/ConnectionManager.java
public class ConnectionManager {
    // Замена традиционных Thread Pool на Virtual Threads
    public static final ExecutorService VIRTUAL_THREAD_EXECUTOR = 
        Executors.newVirtualThreadPerTaskExecutor();
    
    public CompletableFuture<QueryResult> executeOlapQueryAsync(String mdqQuery) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(mdqQuery)) {
                return executeQuery(stmt);
            } catch (SQLException e) {
                throw new OlapException("Query execution failed", e);
            }
        }, VIRTUAL_THREAD_EXECUTOR);
    }
}
```

### 4.2 Pattern Matching и Record Patterns

**Проблема**: Verbose instanceof проверки в генерированном коде
**Решение**: Pattern matching для cleaner код

```java
// mondrian/olap/MondrianDef.java (updated)
public boolean displayDiff(org.eigenbase.xom.ElementDef _other, java.io.PrintWriter _out, int _indent) {
    return switch (_other) {
        case CubeUsages cubeUsages -> displayElementArrayDiff("cubeUsages", cubeUsages, _out, _indent+1);
        case CubeDimension dim -> displayDimensionDiff(dim, _out, _indent+1);
        case null, default -> false;
    };
}

// Record patterns для сложных структур
public record CubeInfo(String name, Dimension[] dimensions, Measure[] measures) {}
```

### 4.3 Sequenced Collections для ordered data

**Проблема**: Неэффективная работа с упорядоченными коллекциями
**Решение**: Использование новых sequenced коллекций

```java
// mondrian/olap/CubeHierarchy.java
public class CubeHierarchy {
    // Вместо ArrayList для иерархий
    private final SequencedCollection<Level> levels = new SequencedLinkedList<>();
    private final SequencedMap<String, Dimension> dimensions = new SequencedHashMap<>();
    
    // Стабильная сортировка для OLAP операций
    public SequencedCollection<Level> getSortedLevels() {
        return levels.reversed().stream()
            .sorted(Comparator.comparing(Level::getDepth))
            .collect(Collectors.toCollection(SequencedLinkedList::new));
    }
}
```

### 4.4 String Templates для динамического SQL

**Проблема**: Конкатенация строк для SQL запросов
**Решение**: String templates для безопасного SQL построения

```java
// mondrian/olap/SqlGenerator.java
public class SqlGenerator {
    public String generateOlapQuery(Cube cube, Query query) {
        var tableName = STR."\{cube.getFactTableName()}";
        var selectClause = generateSelectClause(query.getMeasures());
        var whereClause = generateWhereClause(query.getFilters());
        
        return STR."""
            SELECT \{selectClause}
            FROM \{tableName}
            \{whereClause.isEmpty() ? "" : "WHERE " + whereClause}
            ORDER BY \{generateOrderBy(query.getSorting())}
            """;
    }
}
```

### 4.5 Foreign Function & Memory API (Project Panama)

**Проблема**: JNI overhead для нативных операций
**Решение**: Современный FFI API

```java
// mondrian/util/NativeOptimizer.java
public class NativeOptimizer {
    private static final Linker linker = Linker.nativeLinker();
    private static final MemorySegment performanceLib = SymbolLookup.defaultLookup()
        .find("performance_optimizer")
        .orElseThrow(() new RuntimeException("Native library not found"));
    
    public native int optimizeQuery(String query, long[] params);
    
    public QueryOptimizationResult optimizeWithNative(String query) {
        var func = linker.downcallHandle(
            performanceLib.find("optimize_query").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT, 
                                  ValueLayout.ADDRESS, 
                                  ValueLayout.ADDRESS)
        );
        
        try (var arena = Arena.ofAuto()) {
            var queryStr = arena.allocateFrom(query, StandardCharsets.UTF_8);
            var params = arena.allocateArray(ValueLayout.JAVA_LONG, new long[16]);
            
            int result = (int) func.invoke(queryStr, params);
            
            return new QueryOptimizationResult(result, params.toArray(ValueLayout.JAVA_LONG));
        }
    }
}
```

### 4.6 Structured Concurrency для составных операций

**Проблема**: Сложное управление multiple async операциями
**Решение**: Structured concurrency для cleaner async code

```java
// mondrian/olap/ParallelQueryExecutor.java
public class ParallelQueryExecutor {
    public CompletableFuture<MultiCubeResult> executeParallelQueries(
            List<Query> queries, 
            ExecutionContext context) {
        
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            var futures = queries.stream()
                .map(query -> scope.fork(() -> executeSingleQuery(query, context)))
                .toList();
            
            scope.throwIfFailed();
            
            return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .thenApply(v -> combineResults(futures));
        }
    }
}
```

## 5. Обновление конфигурации сборки

### 5.1 Maven конфигурация для Java 25

```xml
<!-- mondrian/pom.xml -->
<properties>
    <maven.compiler.release>25</maven.compiler.release>
    <maven.compiler.parameters>true</maven.compiler.parameters>
    <maven.compiler.compilerArgs>
        <arg>--enable-preview</arg>
        <arg>--release=25</arg>
        <arg>-Xlint:preview</arg>
    </maven.compiler.compilerArgs>
    <argLine>
        --enable-preview
        -XX:+UnlockDiagnosticVMOptions
        -XX:+PrintInlining
        -Djava.security.manager=allow
    </argLine>
</properties>

<profiles>
    <profile>
        <id>java25-preview</id>
        <activation>
            <jdk>25</jdk>
        </activation>
        <properties>
            <maven.compiler.args>
                --enable-preview
                -Xlint:preview
                -XDsuppressNotes
            </maven.compiler.args>
        </properties>
    </profile>
</profiles>
```

### 5.2 Обновление зависимостей

```xml
<!-- mondrian/pom.xml - обновленные dependencies -->
<dependencies>
    <!-- Java 25 compatible versions -->
    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter</artifactId>
        <version>4.13.2</version>
        <scope>test</scope>
    </dependency>
    
    <!-- Modern XML processing -->
    <dependency>
        <groupId>jakarta.xml.bind</groupId>
        <artifactId>jakarta.xml.bind-api</artifactId>
        <version>4.0.0</version>
    </dependency>
    
    <!-- Enhanced JDBC support -->
    <dependency>
        <groupId>com.zaxxer</groupId>
        <artifactId>HikariCP</artifactId>
        <version>5.0.1</version>
    </dependency>
    
    <!-- Native optimization libraries -->
    <dependency>
        <groupId>org.bytedeco</groupId>
        <artifactId>openblas-platform</artifactId>
        <version>0.3.21-11</version>
    </dependency>
</dependencies>
```

## 6. План реализации и тестирования

### 6.1 Этапы внедрения

1. **Неделя 1-2**: Подготовка инфраструктуры
   - Обновление build конфигурации
   - Настройка preview features
   - Создание тестового окружения

2. **Неделя 3-4**: Core оптимизации
   - Внедрение Virtual Threads
   - Обновление XML processing
   - Pattern matching adoption

3. **Неделя 5-6**: Advanced оптимизации
   - Foreign Function API
   - Structured Concurrency
   - Native code integration

4. **Неделя 7-8**: Performance tuning
   - Benchmarking
   - Memory optimization
   - Production deployment

### 6.2 Метрики для мониторинга

- **Время выполнения запросов**: -40% improvement target
- **Использование памяти**: -30% reduction target
- **Throughput**: +200% improvement target
- **Latency**: -50% p99 latency reduction

### 6.3 Риски и митигация

| Риск | Вероятность | Влияние | Митигация |
|------|-------------|---------|-----------|
| Preview features нестабильность | Средняя | Высокое | Fallback на Java 21 |
| Сложность миграции | Высокая | Среднее | Поэтапная миграция |
| Производительность native code | Низкая | Высокое | Comprehensive benchmarking |

## 7. Ожидаемые результаты

### 7.1 Производительность
- **Query execution**: 40-60% улучшение
- **Memory footprint**: 25-35% снижение
- **Scalability**: поддержка 10x больше concurrent пользователей

### 7.2 Качество кода
- **Type safety**: Pattern matching и records
- **Maintainability**: String templates, structured concurrency
- **Debuggability**: Enhanced debugging features

### 7.3 Developer Experience
- **Developer productivity**: -30% время разработки
- **Code readability**: Упрощение сложных конструкций
- **Testing**: Улучшенные testing возможности

## Заключение

Миграция на Java 25 с использованием preview features позволит существенно повысить производительность Mondrian OLAP движка. Ключевые улучшения включают использование Virtual Threads для I/O, Pattern Matching для cleaner кода, и Foreign Function API для нативных оптимизаций. Поэтапный подход к миграции минимизирует риски и обеспечивает стабильность системы.
