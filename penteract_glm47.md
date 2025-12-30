# Penteract Mondrian GLM-47: Рекомендации по улучшению проекта

## Исполнительный резюме

Документ содержит комплексный анализ проекта Penteract Mondrian (OLAP сервер на базе Mondrian/Pentaho) с рекомендациями по улучшению архитектуры, кода и производительности. Особое внимание уделено целесообразности перехода на Virtual Threads (Project Loom) в Java 21+.

---

## Текущее состояние проекта

### Технический стек
- **Java версия**: 25 (с preview features)
- **Система сборки**: Maven
- **Основные компоненты**:
  - Mondrian OLAP Engine (версия 1.0.0.1048-LMX)
  - XMLA (XML for Analysis) сервис
  - JDBC драйверы для различных БД (MySQL, PostgreSQL, Oracle, SQL Server, ClickHouse и др.)
  - Redis для кеширования сегментов

### Ключевые архитектурные компоненты
- `SqlStatement` - выполнение SQL запросов с ограничением через Semaphore
- `SegmentCacheWorker` - работа с внешними кешами (Redis)
- `RolapAggregationManager` - управление агрегациями
- `XmlaServlet` - обработка HTTP/XMLA запросов

---

## Критические рекомендации

### 1. Обновление системы сборки и зависимостей

#### Проблема
```xml
<maven.compiler.source>25</maven.compiler.source>
<maven.compiler.target>25</maven.compiler.target>
```
- Java 25 ещё не выпущена (актуальная версия на момент анализа - Java 21)
- Использование preview features в production рискованно

#### Рекомендации
```xml
<properties>
    <maven.compiler.source>21</maven.compiler.source>
    <maven.compiler.target>21</maven.compiler.target>
    <java.version>21</java.version>
    <!-- Удалить preview флаги для production -->
</properties>
```

**Приоритет**: КРИТИЧЕСКИЙ  
**Уровень риска**: Низкий  
**Оценка усилий**: 1-2 дня

---

### 2. Переход на Virtual Threads (Project Loom)

### Анализ применимости

#### Где Virtual Threads дадут преимущество:

| Компонент | Тип нагрузки | Потенциальная выгода |
|-----------|--------------|---------------------|
| `SqlStatement.execute()` | Блокирующий I/O (JDBC) | Высокая |
| `SegmentCacheWorker` (Redis) | Блокирующий I/O | Высокая |
| `XmlaServlet` | HTTP I/O | Высокая |
| `SegmentLoader` | Многопоточные вычисления | Низкая (CPU-bound) |
| MDX Parser | CPU-bound | Низкая |

#### Текущее состояние блокировок

В `mondrian/mondrian/src/main/java/mondrian/rolap/SqlStatement.java`:
```java
private static final Semaphore querySemaphore = new Semaphore(
    MondrianProperties.instance().QueryLimit.get(), true);

public void execute() {
    // ...
    querySemaphore.acquire();  // Блокирующая операция
    haveSemaphore = true;
    // ... execute SQL
}
```

### Рекомендации по внедрению Virtual Threads

#### Фаза 1: Введение пула virtual threads

Создайте `VirtualThreadExecutorFactory.java`:
```java
package mondrian.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class VirtualThreadExecutorFactory {
    private static final ExecutorService VIRTUAL_THREAD_EXECUTOR = 
        Executors.newVirtualThreadPerTaskExecutor();
    
    public static ExecutorService getExecutor() {
        return VIRTUAL_THREAD_EXECUTOR;
    }
    
    // Для graceful shutdown
    public static void shutdown() {
        VIRTUAL_THREAD_EXECUTOR.shutdown();
    }
}
```

#### Фаза 2: Замена Semaphore на SemaphoredVirtualThreadExecutor

```java
package mondrian.util;

import java.util.concurrent.Semaphore;
import java.util.concurrent.ExecutorService;

/**
 * Executor который ограничивает количество concurrently выполняющихся
 * virtual threads, но не блокирует platform threads.
 */
public class SemaphoredVirtualThreadExecutor {
    private final Semaphore semaphore;
    private final ExecutorService executor;
    
    public SemaphoredVirtualThreadExecutor(int maxConcurrency) {
        this.semaphore = new Semaphore(maxConcurrency);
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }
    
    public <T> CompletableFuture<T> submit(Callable<T> task) {
        return CompletableFuture.supplyAsync(() -> {
            semaphore.acquireUninterruptibly();
            try {
                return task.call();
            } finally {
                semaphore.release();
            }
        }, executor);
    }
}
```

#### Фаза 3: Интеграция в SqlStatement

Измените метод execute() для поддержки async execution:

```java
// Новый интерфейс для асинхронного выполнения
public interface SqlStatementCallback {
    void onSuccess(SqlStatement statement);
    void onError(Throwable t);
}

public CompletableFuture<Void> executeAsync(SqlStatementCallback callback) {
    return CompletableFuture.runAsync(() -> {
        execute();
        callback.onSuccess(this);
    }, VirtualThreadExecutorFactory.getExecutor())
    .exceptionally(ex -> {
        callback.onError(ex);
        return null;
    });
}
```

### Заключение по Virtual Threads

**РЕШЕНИЕ**: ПЕРЕХОД ЦЕЛЕСООБРАЗЕН, но требуется пошаговая интеграция

**Обоснование**:
1. OLAP сервер выполняет множество I/O операций (SQL, Redis, HTTP)
2. Current thread-per-request model масштабируется плохо
3. Virtual Threads позволяют обрабатывать тысячи одновременных запросов
4. Отсутствие изменений в JDBC драйверах - виртуальные потоки прозрачны

**Ограничения**:
- Не решит проблемы с CPU-bound операциями (агрегации, вычисления)
- Требует мониторинга и тестирования производительности
- Может потребовать настройки connection pool

**Приоритет**: ВЫСОКИЙ  
**Уровень риска**: Средний (требует тщательного тестирования)  
**Оценка усилий**: 2-3 недели

---

## Рекомендации по модернизации кода под Java 21+

### 3. Использование Record для DTO классов

#### Текущий код (ролеподобные классы):
```java
public class CellKey {
    private final int[] positions;
    private final int hashCode;
    
    public CellKey(int[] positions, int hashCode) {
        this.positions = positions;
        this.hashCode = hashCode;
    }
    
    public int[] getPositions() { return positions; }
    public int getHashCode() { return hashCode; }
    
    @Override
    public boolean equals(Object o) { /* ... */ }
    @Override
    public int hashCode() { /* ... */ }
}
```

#### Рекомендация:
```java
public record CellKey(int[] positions, int hashCode) {
    public CellKey {
        // Validation
        Objects.requireNonNull(positions);
    }
}
```

**Кандидаты для Record**:
- `CellKey` → record
- `AggregationKey` → record
- `MemberKey` → record  
- `HierarchyUsage` → record
- Query metadata classes

**Приоритет**: СРЕДНИЙ  
**Оценка усилий**: 1 неделя

---

### 4. Sealed Classes для иерархий типов

#### Текущая проблема:
```java
public abstract class Calc { /* ... */ }
public abstract class ListCalc extends Calc { /* ... */ }
public abstract class DoubleCalc extends Calc { /* ... */ }
// 20+ подклассов, сложная иерархия
```

#### Рекомендация:
```java
public sealed abstract class Calc 
    permits DoubleCalc, IntegerCalc, StringCalc, 
            BooleanCalc, DateTimeCalc, TupleCalc, 
            ListCalc, IterCalc, VoidCalc {
    // ...
}
```

**Преимущества**:
- Компилятор проверяет исчерпываемость switch
- Лучшая поддержка pattern matching
- Более понятная архитектура

**Приоритет**: СРЕДНИЙ  
**Оценка усилий**: 1-2 недели

---

### 5. Pattern Matching и Switch Expressions

#### Текущий код (в calc и fun пакетах):
```java
if (type instanceof SetType) {
    final Calc calc = compiler.compileAs(exp, null, ResultStyle.LIST_MUTABLELIST);
    if (calc == null) {
        return compiler.compileList(exp, false);
    }
    return (ListCalc) calc;
} else if (type instanceof MemberType) {
    // ...
} else {
    // ...
}
```

#### Рекомендация:
```java
return switch (type) {
    case SetType setType -> {
        final Calc calc = compiler.compileAs(exp, null, ResultStyle.LIST_MUTABLELIST);
        yield calc != null ? (ListCalc) calc : compiler.compileList(exp, false);
    }
    case MemberType memberType -> new MemberValueCalc(...);
    case TupleType tupleType -> new TupleValueCalc(...);
    default -> throw new IllegalArgumentException("Unknown type: " + type);
};
```

**Приоритет**: СРЕДНИЙ  
**Оценка усилий**: 2-3 недели

---

### 6. Sequenced Collections (Java 21)

#### Текущий код:
```java
List<String> list = ...;
String first = list.get(0);  // Может бросить IndexOutOfBoundsException
String last = list.get(list.size() - 1);
```

#### Рекомендация:
```java
List<String> list = ...;
String first = list.getFirst();   // Более читаемый, null-safe вариант
String last = list.getLast();

// Для добавления/удаления с конца
list.addLast(element);
list.removeLast();
```

**Приоритет**: НИЗКИЙ  
**Оценка усилий**: 2-3 дня

---

### 7. String Templates (Preview в Java 21, Stable в будущих версиях)

#### Текущий код:
```java
String query = "SELECT " + column + " FROM " + table + " WHERE " + condition;
```

#### Рекомендация (когда станет stable):
```java
String query = STR."SELECT \{column} FROM \{table} WHERE \{condition}";
```

**Приоритет**: НИЗКИЙ (ждать стабильной версии)  
**Оценка усилий**: 1 день

---

## Архитектурные улучшения

### 8. Рефакторинг кеширования

#### Проблемы:
- Разнородные механизмы кеширования (SmartCache, MemorySegmentCache, RedisSegmentCache)
- Отсутствие единой стратегии инвалидации

#### Рекомендация: Паттерн Cache Aside с Virtual Threads

```java
public sealed interface SegmentCacheStrategy 
    permits InMemoryStrategy, RedisStrategy, HybridStrategy {
    
    CompletableFuture<Optional<SegmentBody>> get(SegmentHeader header);
    CompletableFuture<Void> put(SegmentHeader header, SegmentBody body);
}

public class RedisStrategy implements SegmentCacheStrategy {
    private final RedisAsyncClient redisClient;
    
    @Override
    public CompletableFuture<Optional<SegmentBody>> get(SegmentHeader header) {
        return CompletableFuture.supplyAsync(() -> {
            return Optional.ofNullable(redisClient.get(header.getEncodedKey()));
        }, VirtualThreadExecutorFactory.getExecutor());
    }
}
```

**Приоритет**: ВЫСОКИЙ  
**Оценка усилий**: 3-4 недели

---

### 9. Reactive Streams для XMLA

#### Текущая проблема:
`XmlaServlet` блокирует thread на всю длительность обработки запроса

#### Рекомендация: Использовать Jakarta REST (JAX-RS) 3.1 с async support

```java
@Path("/xmla")
@Produces(MediaType.APPLICATION_XML)
public class XmlaResource {
    
    @POST
    public CompletionStage<Response> executeXmla(String xmlaRequest) {
        return CompletableFuture.supplyAsync(() -> {
            // Обработка в virtual thread
            XmlaResponse response = xmlaHandler.handle(xmlaRequest);
            return Response.ok(response.getXml()).build();
        }, VirtualThreadExecutorFactory.getExecutor());
    }
}
```

**Приоритет**: СРЕДНИЙ  
**Оценка усилий**: 2-3 недели

---

### 10. Оптимизация ObjectPool

#### Текущее состояние (в `ObjectPool.java`):
- Использует open addressing с rehash
- НЕ thread-safe
- Примитивная реализация

#### Рекомендация 1: Сделать thread-safe версию для concurrent access

```java
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ConcurrentObjectPool<T> {
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    
    public T add(T key) {
        lock.writeLock().lock();
        try {
            // ... add logic
        } finally {
            lock.writeLock().unlock();
        }
    }
}
```

#### Рекомендация 2: Рассмотреть использование java.util.concurrent.ConcurrentHashMap

```java
// Для сценариев где не нужна экономия памяти:
ConcurrentHashMap<PoolKey<T>, T> pool = new ConcurrentHashMap<>();
```

**Приоритет**: СРЕДНИЙ  
**Оценка усилий**: 1-2 недели

---

### 11. Улучшение обработки исключений

#### Текущая проблема:
Много `catch (Throwable t)` блоков, которые теряют контекст

#### Рекомендация: Multi-catch с pattern matching

```java
try {
    return cache.get(header);
} catch (SQLException | RedisConnectionException e) {
    LOGGER.error("Database/Cache access failed for header: {}", header, e);
    throw new CacheAccessException("Failed to retrieve segment", e);
} catch (ClassCastException e) {
    LOGGER.error("Cache deserialization failed", e);
    throw new CacheException("Invalid cache data", e);
}
```

**Приоритет**: СРЕДНИЙ  
**Оценка усилий**: 3-5 дней

---

## Производительность и масштабируемость

### 12. Lazy Loading и Backpressure

#### Проблема:
Mondrian загружает большие объёмы данных за раз

#### Рекомендация: Reactive Streams с backpressure

```java
public interface ReactiveTupleReader {
    Publisher<Tuple> streamTuples(Query query, int batchSize);
}

public class ReactiveSqlTupleReader implements ReactiveTupleReader {
    
    @Override
    public Publisher<Tuple> streamTuples(Query query, int batchSize) {
        return Flowable.create(emitter -> {
            try (ResultSet rs = executeQuery(query)) {
                List<Tuple> batch = new ArrayList<>(batchSize);
                while (rs.next() && !emitter.isCancelled()) {
                    batch.add(extractTuple(rs));
                    if (batch.size() >= batchSize) {
                        emitter.onNext(batch);
                        batch.clear();
                    }
                }
                if (!batch.isEmpty()) {
                    emitter.onNext(batch);
                }
                emitter.onComplete();
            } catch (SQLException e) {
                emitter.onError(e);
            }
        }, BackpressureStrategy.BUFFER);
    }
}
```

**Приоритет**: ВЫСОКИЙ  
**Оценка усилий**: 4-6 недель

---

### 13. Connection Pool оптимизация

#### Текущая проблема:
- Неявное использование через `DataSource.getConnection()`
- Нет явной конфигурации pool

#### Рекомендация: HikariCP с virtual threads awareness

```java
HikariConfig config = new HikariConfig();
config.setMaximumPoolSize(100);  // На основе QueryLimit
config.setMinimumIdle(10);
config.setConnectionTimeout(30000);
config.setIdleTimeout(600000);
config.setMaxLifetime(1800000);
config.setPoolName("MondrianHikariPool");

// HikariCP хорошо работает с virtual threads
DataSource dataSource = new HikariDataSource(config);
```

**Приоритет**: ВЫСОКИЙ  
**Оценка усилий**: 2-3 дня

---

## Безопасность и надежность

### 14. Устранение SQL Injection рисков

#### Анализ:
В `SqlQuery.java` и `SqlStatement.java` происходит построение SQL через StringBuilder

#### Рекомендация: Parameterized queries везде

```java
// Вместо:
String sql = "SELECT * FROM table WHERE id = " + id;

// Использовать:
PreparedStatement ps = connection.prepareStatement(
    "SELECT * FROM table WHERE id = ?"
);
ps.setInt(1, id);
```

**Приоритет**: КРИТИЧЕСКИЙ  
**Оценка усилий**: 1-2 недели

---

### 15. Timeout Management

#### Проблема:
Отсутствие явных timeouts для long-running queries

#### Рекомендация: Структурированные timeouts

```java
public class QueryTimeoutManager {
    private final Duration defaultTimeout;
    private final Duration hardTimeout;
    
    public <T> T executeWithTimeout(Callable<T> operation, Duration customTimeout) {
        Duration timeout = customTimeout != null ? customTimeout : defaultTimeout;
        try {
            return CompletableFuture.supplyAsync(() -> {
                try { return operation.call(); } 
                catch (Exception e) { throw new CompletionException(e); }
            }, VirtualThreadExecutorFactory.getExecutor())
            .get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new QueryTimeoutException("Query exceeded timeout: " + timeout);
        }
    }
}
```

**Приоритет**: ВЫСОКИЙ  
**Оценка усилий**: 1 неделя

---

## Тестирование и мониторинг

### 16. JUnit 5 миграция

#### Текущее состояние:
Судя по pom.xml, используется `maven-failsafe-plugin` с тестами

#### Рекомендация:
- Мигрировать на JUnit 5 (Jupiter)
- Использовать `@Nested` для группировки тестов
- Добавить `@RepeatedTest` для стресс-тестов
- Интегрировать с Virtual Threads тестами

```java
@Test
@RepeatedTest(10)
void testConcurrentSegmentLoading() {
    // Тест для проверки корректности работы с virtual threads
}
```

**Приоритет**: СРЕДНИЙ  
**Оценка усилий**: 1-2 недели

---

### 17. Observability (Micrometer + OpenTelemetry)

#### Рекомендация: Добавить метрики для virtual threads

```java
// Включить JMX monitoring для virtual threads
System.setProperty("jdk.virtualThreadScheduler.parallelism", "256");
System.setProperty("jdk.virtualThreadScheduler.maxPoolSize", "1000000");

// Метрики через Micrometer
Metrics.gauge("virtual.threads.active", 
    Thread.getAllStackTraces().keySet().stream()
        .filter(Thread::isVirtual)
        .filter(t -> t.getState() == State.RUNNABLE)
        .count()
);
```

**Приоритет**: СРЕДНИЙ  
**Оценка усилий**: 1-2 недели

---

## План работ

### Этап 1: Стабилизация (2-3 недели)
- [ ] Перейти с Java 25 на Java 21 (stable)
- [ ] Обновить зависимости Maven
- [ ] Внедрить HikariCP
- [ ] Добавить explicit timeout management
- [ ] Мигрировать на JUnit 5

### Этап 2: Virtual Threads Pilot (3-4 недели)
- [ ] Создать `VirtualThreadExecutorFactory`
- [ ] Внедрить в `SegmentCacheWorker` (Redis operations)
- [ ] Внедрить в `XmlaServlet` обработку запросов
- [ ] Добавить мониторинг virtual threads
- [ ] Провести нагрузочное тестирование

### Этап 3: Virtual Threads Expansion (4-6 недель)
- [ ] Рефакторинг `SqlStatement` для async execution
- [ ] Реализовать `SemaphoredVirtualThreadExecutor`
- [ ] Оптимизировать connection pool под virtual threads
- [ ] Регрессионное тестирование

### Этап 4: Кодовая модернизация (6-8 недель)
- [ ] Внедрить Record для DTO классов
- [ ] Sealed classes для Calc иерархии
- [ ] Pattern matching и switch expressions
- [ ] Sequenced Collections
- [ ] Multi-catch improvements

### Этап 5: Архитектурные улучшения (8-12 недель)
- [ ] Unified caching strategy
- [ ] Reactive XMLA endpoint
- [ ] Backpressure для tuple readers
- [ ] Observability stack (Micrometer + OpenTelemetry)
- [ ] Документация и training

---

## Риски и митигация

| Риск | Вероятность | Влияние | Митигация |
|------|-------------|---------|-----------|
| Virtual threads не дают ожидаемого прироста производительности | Средняя | Высокое | Тщательное benchmarking перед внедрением |
| JDBC драйвер не совместим с virtual threads | Низкая | Высокое | Тестирование с целевыми БД |
| Увеличение потребления памяти | Средняя | Среднее | Мониторинг, настройка heap size |
| Обратная совместимость API | Низкая | Среднее | Deprecation warnings, gradual migration |
| Процессы CI/CD не поддерживают Java 21 | Низкая | Среднее | Обновление инфраструктуры |

---

## Дополнительные ресурсы

### Для изучения Virtual Threads:
- [JEP 444: Virtual Threads](https://openjdk.org/jeps/444)
- [Virtual Threads: The Secret Weapon for Java Concurrency](https://www.youtube.com/watch?v=OqDgB1g2xR8)
- [Java Virtual Threads: Best Practices](https://inside.java/2023/03/16/virtual-threads/)

### Для тестирования:
- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/)
- [JMH (Java Microbenchmark Harness)](https://openjdk.org/projects/code-tools/jmh/)

---

## Заключение

Проект Penteract Mondrian имеет отличную архитектурную основу, но может существенно выиграть от:

1. **Перехода на Virtual Threads** - критически важно для масштабируемости I/O операций
2. **Модернизации под Java 21** - улучшит читаемость, безопасность типов и поддержку разработчиков
3. **Улучшения кеширования** - повысит производительность OLAP запросов
4. **Обновления инфраструктуры** - HikariCP, explicit timeouts, observability

Рекомендуемый подход - постепенная интеграция изменений с постоянным тестированием производительности на реальных нагрузках.

---

*Документ создан на основе анализа кодовой базы Penteract Mondrian (версия 1.0.0.1048-LMX)*
*Дата анализа: 30 декабря 2025*
