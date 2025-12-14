# Рекомендации по модернизации кода Mondrian под Java 25

## Обзор проекта

Проект Mondrian использует JavaCC 5.0 для генерации парсера MDX. Кодовая база содержит как сгенерированный код парсера (`MdxParserImpl.java`), так и рукописные Java файлы. После перекомпиляции под Java 25 можно внести существенные улучшения, используя современные возможности языка.

## Анализ текущего состояния

### Найденные устаревшие подходы:

1. **Сырые типы (raw types) коллекций** - везде используется явное указание типов
2. **Устаревшие инициализации коллекций** - `new ArrayList<Type>()` вместо diamond operator
3. **Многословные проверки instanceof с приведением типов**
4. **Традиционные switch-case без использования выражений**
5. **Избыточные геттеры/сеттеры в простых DTO классах**
6. **Устаревшая обработка строк**
7. **Отсутствие sealed иерархий классов**
8. **Минимальное использование функционального программирования**
9. **Устаревшие конструкции в утилитарных классах**
10. **Отсутствие pattern matching и switch выражений**

## Конкретные рекомендации

### 1. Использование `var` для локальных переменных

**Текущий код в MdxParser.jj:**
```java
List<Id.NameSegment> list = new ArrayList<Id.NameSegment>();
Id.Segment i;
List<Id.Segment> segments = new ArrayList<Id.Segment>();
```

**Рекомендуемая замена:**
```java
var list = new ArrayList<Id.NameSegment>();
Id.Segment i;
var segments = new ArrayList<Id.Segment>();
```

**Преимущества:**
- Более краткий код
- Улучшенная читаемость
- Меньше boilerplate кода

### 2. Diamond Operator (<>)

**Текущий код:**
```java
return idList.toArray(new Id[idList.size()]);
return formulaList.toArray(new Formula[formulaList.size()]);
```

**Рекомендуемая замена:**
```java
return idList.toArray(Id[]::new);
return formulaList.toArray(Formula[]::new);
```

### 3. Pattern Matching для instanceof (Java 16+)

**Текущий код в `createCall()`:**
```java
final String name = segment instanceof Id.NameSegment
    ? ((Id.NameSegment) segment).name
    : null;
```

**Рекомендуемая замена:**
```java
final String name = segment instanceof Id.NameSegment nameSegment 
    ? nameSegment.name()  // если использовать record
    : null;
```

**Другие примеры в проекте:**
```java
// Было:
if (left instanceof Id) {
    return ((Id) left).append(segment);
}

// Стало:
if (left instanceof Id idLeft) {
    return idLeft.append(segment);
}
```

### 4. Switch выражения (Java 14+)

**Текущий код в Literal.java:**
```java
public void unparse(PrintWriter pw) {
    switch (category) {
    case Category.Symbol:
    case Category.Numeric:
        pw.print(o);
        break;
    case Category.String:
        pw.print(Util.quoteForMdx((String) o));
        break;
    case Category.Null:
        pw.print("NULL");
        break;
    default:
        throw Util.newInternal("bad literal type " + category);
    }
}
```

**Рекомендуемая замена:**
```java
public void unparse(PrintWriter pw) {
    switch (category) {
        case Category.Symbol, Category.Numeric -> pw.print(o);
        case Category.String -> pw.print(Util.quoteForMdx((String) o));
        case Category.Null -> pw.print("NULL");
        default -> throw Util.newInternal("bad literal type " + category);
    }
}
```

### 5. Record классы для DTO

**Класс `Id.NameSegment` (текущая реализация):**
```java
public static class NameSegment extends Segment {
    public final String name;
    
    public NameSegment(String name, Quoting quoting) {
        super(quoting);
        this.name = name;
        if (name == null) {
            throw new NullPointerException();
        }
        if (!(quoting == Quoting.QUOTED || quoting == Quoting.UNQUOTED)) {
            throw new IllegalArgumentException();
        }
    }
    
    // Геттеры, equals, hashCode, toString...
}
```

**Record версия:**
```java
public record NameSegment(String name, Quoting quoting) extends Segment {
    public NameSegment {
        Objects.requireNonNull(name, "name");
        if (!(quoting == Quoting.QUOTED || quoting == Quoting.UNQUOTED)) {
            throw new IllegalArgumentException("Invalid quoting: " + quoting);
        }
    }
    
    public NameSegment(String name) {
        this(name, Quoting.QUOTED);
    }
    
    // toString и matches методы остаются
    public boolean matches(String otherName) {
        return switch (quoting) {
            case UNQUOTED, QUOTED -> Util.equalName(this.name, otherName);
            default -> false;
        };
    }
}
```

**Преимущества records:**
- Автоматические equals(), hashCode(), toString()
- Неизменяемость по умолчанию
- Меньше boilerplate кода
- Ясность намерений

### 6. Sealed классы для иерархий

**Текущая иерархия:**
```java
public static abstract class Segment { ... }
public static class NameSegment extends Segment { ... }
public static class KeySegment extends Segment { ... }
```

**Sealed версия:**
```java
public sealed abstract class Segment permits NameSegment, KeySegment {
    public final Quoting quoting;
    
    protected Segment(Quoting quoting) {
        this.quoting = quoting;
    }
    
    // абстрактные методы...
}

public final record NameSegment(String name, Quoting quoting) extends Segment {
    // реализация...
}

public final class KeySegment extends Segment {
    // реализация...
}
```

**Преимущества sealed классов:**
- Явное определение возможных наследников
- Улучшенная проверка в компиляторе
- Лучшая поддержка pattern matching

### 7. Улучшенные API строк

**Текущий код в Util.java:**
```java
public static String replace(String s, String find, String replace) {
    // let's be optimistic
    int found = s.indexOf(find);
    if (found == -1) {
        return s;
    }
    StringBuilder sb = new StringBuilder(s.length() + 20);
    int start = 0;
    char[] chars = s.toCharArray();
    final int step = find.length();
    if (step == 0) {
        // Special case where find is "".
        sb.append(s);
        replace(sb, 0, find, replace);
    } else {
        for (;;) {
            sb.append(chars, start, found - start);
            if (found == s.length()) {
                break;
            }
            sb.append(replace);
            start = found + step;
            found = s.indexOf(find, start);
            if (found == -1) {
                found = s.length();
            }
        }
    }
    return sb.toString();
}
```

**Улучшенная версия с новыми методами String:**
```java
public static String replace(String s, String find, String replace) {
    if (find.isEmpty()) {
        return s.replace("", replace); // Используем встроенный метод
    }
    return s.replace(find, replace); // Более просто и эффективно
}
```

**Новые возможности Java 25 для строк:**
- `String.formatted()` вместо `String.format()`
- `String.translateEscapes()` для обработки escape последовательностей
- `String.indent()` для форматирования
- Текстовые блоки для многострочных строк
- `String.repeat()` для повторения строк

### 8. Optional для nullable возвращаемых значений

**Текущий код:**
```java
public Exp accept(Validator validator) {
    // ...
    final Exp element =
        Util.lookup(
            validator.getQuery(),
            validator.getSchemaReader().withLocus(),
            segments,
            true);

    if (element == null) {
        return null;
    }
    return element.accept(validator);
}
```

**Улучшенная версия:**
```java
public Optional<Exp> accept(Validator validator) {
    return Optional.ofNullable(
            Util.lookup(
                validator.getQuery(),
                validator.getSchemaReader().withLocus(),
                segments,
                true))
        .map(element -> element.accept(validator));
}
```

### 9. Stream API для работы с коллекциями

**Методы преобразования в MdxParserImpl.java:**
```java
static Id[] toIdArray(List<Id> idList) {
    if (idList == null || idList.size() == 0) {
        return EmptyIdArray;
    } else {
        return idList.toArray(new Id[idList.size()]);
    }
}
```

**Улучшенная версия с Stream API:**
```java
static Id[] toIdArray(List<Id> idList) {
    return Optional.ofNullable(idList)
        .filter(list -> !list.isEmpty())
        .map(list -> list.toArray(Id[]::new))
        .orElse(EmptyIdArray);
}
```

### 10. Улучшенные фабричные методы коллекций

**Вместо:**
```java
List<Exp> list = new ArrayList<Exp>();
List<Id.Segment> list = new ArrayList<Id.Segment>();
```

**Использовать, где возможно:**
```java
List<Exp> list = new ArrayList<>();
var list = new ArrayList<Id.Segment>();

// Для неизменяемых коллекций:
List<Exp> emptyList = List.of();
List<Exp> singletonList = List.of(exp);
```

## Новые рекомендации из анализа других частей кода

### 11. Улучшение обработки исключений

**Текущий код в Util.java:**
```java
public static byte[] digestSha256(String value) {
    final MessageDigest algorithm;
    try {
        algorithm = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
        throw new RuntimeException(e);
    }
    return algorithm.digest(value.getBytes());
}
```

**Улучшенная версия с более информативным исключением:**
```java
public static byte[] digestSha256(String value) {
    try {
        return MessageDigest.getInstance("SHA-256")
            .digest(value.getBytes());
    } catch (NoSuchAlgorithmException e) {
        throw new IllegalStateException("SHA-256 algorithm not available", e);
    }
}
```

### 12. Использование текстовых блоков (Java 15+)

**Текущий код с многострочными строками:**
```java
throw MondrianResource.instance().InvalidAxis.ex(
    number.doubleValue());
```

**Текстовые блоки для SQL/MDX запросов в тестах:**
```java
// Было:
String query = "SELECT\n" +
               "  {[Measures].[Unit Sales]} ON COLUMNS,\n" +
               "  {[Store].[All Stores]} ON ROWS\n" +
               "FROM [Sales]";

// Стало:
String query = """
    SELECT
      {[Measures].[Unit Sales]} ON COLUMNS,
      {[Store].[All Stores]} ON ROWS
    FROM [Sales]
    """;
```

### 13. Pattern matching в switch (Java 21+)

**Текущий код с цепочками if-instanceof:**
```java
if (exp instanceof MemberExpr) {
    MemberExpr memberExpr = (MemberExpr) exp;
    return memberExpr.getMember();
} else if (exp instanceof LevelExpr) {
    LevelExpr levelExpr = (LevelExpr) exp;
    return levelExpr.getLevel();
} else if (exp instanceof HierarchyExpr) {
    HierarchyExpr hierarchyExpr = (HierarchyExpr) exp;
    return hierarchyExpr.getHierarchy();
} else if (exp instanceof DimensionExpr) {
    DimensionExpr dimensionExpr = (DimensionExpr) exp;
    return dimensionExpr.getDimension();
} else {
    throw Util.newInternal("Not an olap element: " + exp);
}
```

**Улучшенная версия с pattern matching в switch:**
```java
return switch (exp) {
    case MemberExpr memberExpr -> memberExpr.getMember();
    case LevelExpr levelExpr -> levelExpr.getLevel();
    case HierarchyExpr hierarchyExpr -> hierarchyExpr.getHierarchy();
    case DimensionExpr dimensionExpr -> dimensionExpr.getDimension();
    default -> throw Util.newInternal("Not an olap element: " + exp);
};
```

### 14. Records для простых Value классов

**Класс Pair в mondrian.util:**
```java
public class Pair<T1, T2> {
    public final T1 left;
    public final T2 right;
    
    public Pair(T1 left, T2 right) {
        this.left = left;
        this.right = right;
    }
    
    // equals, hashCode, toString...
}
```

**Record версия:**
```java
public record Pair<T1, T2>(T1 left, T2 right) {
    // Автоматически генерирует конструктор, equals, hashCode, toString
}
```

### 15. Улучшение многопоточности

1. **Текущий код в Util.java для создания ExecutorService:**
```java
final ThreadFactory factory =
    new ThreadFactory() {
        private final AtomicInteger counter = new AtomicInteger(0);
        public Thread newThread(Runnable r) {
            final Thread t =
                Executors.defaultThreadFactory().newThread(r);
            t.setDaemon(true);
            t.setName(name + '_' + counter.incrementAndGet());
            return t;
        }
    };
```

2. **Улучшенная версия с lambda:**
```java
final ThreadFactory factory = r -> {
    Thread t = Executors.defaultThreadFactory().newThread(r);
    t.setDaemon(true);
    t.setName(name + '_' + counter.incrementAndGet());
    return t;
};
```

### 16. Использование var в for-each циклах

**Текущий код:**
```java
for (Segment segment : segments) {
    names[k++] = ((NameSegment) segment).getName();
}
```

**Улучшенная версия с pattern matching:**
```java
for (Segment segment : segments) {
    if (segment instanceof NameSegment nameSegment) {
        names[k++] = nameSegment.getName();
    }
}
```

### 17. Упрощение equals/hashCode методов

**Текущий код в NameSegment:**
```java
public boolean equals(final Object o) {
    if (this == o) {
        return true;
    }
    if (!(o instanceof NameSegment)) {
        return false;
    }
    NameSegment that = (NameSegment) o;
    return that.name.equals(this.name);
}

public int hashCode() {
    return name.hashCode();
}
```

**С record это генерируется автоматически. Без record можно использовать Objects.equals/hash:**
```java
public boolean equals(final Object o) {
    return o instanceof NameSegment that 
        && Objects.equals(this.name, that.name);
}

public int hashCode() {
    return Objects.hash(name);
}
```

## Примеры из конкретных файлов проекта

### Util.java улучшения:

1. **Метод `compareKey`** можно упростить с помощью pattern matching:
```java
public static int compareKey(Object k1, Object k2) {
    return switch (k1) {
        case Boolean b1 -> {
            Boolean b2 = (Boolean) k2;
            yield Boolean.compare(b1, b2);
        }
        case Comparable<?> c -> ((Comparable<Object>) c).compareTo(k2);
        default -> throw new IllegalArgumentException("Not comparable: " + k1.getClass());
    };
}
```

2. **Метод `isSorted`** можно переписать с Stream API:
```java
public static <T extends Comparable<T>> boolean isSorted(List<T> list) {
    return IntStream.range(1, list.size())
        .allMatch(i -> list.get(i-1).compareTo(list.get(i)) <= 0);
}
```

### ExpBase.java улучшения:

1. **Метод `cloneArray`** можно улучшить с помощью Streams:
```java
protected static Exp[] cloneArray(Exp[] a) {
    return Arrays.stream(a)
        .map(Exp::clone)
        .toArray(Exp[]::new);
}
```

### Литералы и константы:

1. **Вместо `Double.valueOf(FunUtil.DoubleNull)`** использовать `Double.valueOf(FunUtil.DoubleNull)` можно заменить на более современный подход с Optional.

## План миграции

### Фаза 1: Наименее рискованные изменения
1. Замена явных типов на `var` в локальных переменных
2. Использование diamond operator `<>`
3. Обновление методов коллекций (`isEmpty()` вместо `size() == 0`)
4. Использование `List.of()`, `Set.of()`, `Map.of()` для неизменяемых коллекций

### Фаза 2: Современные конструкции языка
1. Pattern matching для `instanceof`
2. Switch выражения
3. Текстовые блоки для многострочных строк
4. Улучшенная обработка исключений

### Фаза 3: Архитектурные улучшения
1. Преобразование DTO классов в `record`
2. Внедрение `sealed` иерархий классов
3. Использование `Optional` для nullable возвращаемых значений
4. Применение Stream API где это уместно

### Фаза 4: Оптимизация производительности
1. Анализ и оптимизация горячих участков кода
2. Кэширование immutable объектов
3. Использование новых методов `String` API
4. Оптимизация многопоточных операций

## Ограничения и предостережения

### 1. Совместимость с JavaCC
JavaCC 5.0 может не поддерживать весь синтаксис Java 25. Проверьте:
- Поддержка `var` в сгенерированном коде
- Поддержка pattern matching
- Поддержка sealed классов

### 2. Обратная совместимость
- Изменения в публичном API должны сохранять обратную совместимость
- Record классы меняют сигнатуры методов (геттеры без `get` префикса)
- Sealed классы требуют явного разрешения всех наследников

### 3. Производительность
- Pattern matching и switch выражения могут иметь отличия в производительности
- Record классы создают дополнительные методы (не всегда нужны)
- Stream API может быть менее производительным в hot-путях

### 4. Тестирование
Все изменения должны быть покрыты тестами:
- Модульные тесты для отдельных классов
- Интеграционные тесты для парсера
- Регрессионное тестирование существующей функциональности

## Конкретные примеры изменений

### Пример 1: Упрощение метода `toIdArray`

**До:**
```java
static Id[] toIdArray(List<Id> idList) {
    if (idList == null || idList.size() == 0) {
        return EmptyIdArray;
    } else {
        return idList.toArray(new Id[idList.size()]);
    }
}
```

**После:**
```java
static Id[] toIdArray(List<Id> idList) {
    return Optional.ofNullable(idList)
        .filter(list -> !list.isEmpty())
        .map(list -> list.toArray(Id[]::new))
        .orElse(EmptyIdArray);
}
```

### Пример 2: Pattern matching в парсере

**До:**
```java
if (left instanceof Id && !call) {
    return ((Id) left).append(segment);
} else if (left == null) {
    return new Id(segment);
} else {
    return new UnresolvedFunCall(name, syntax, new Exp[] {left});
}
```

**После:**
```java
if (left instanceof Id idLeft && !call) {
    return idLeft.append(segment);
} else if (left == null) {
    return new Id(segment);
} else {
    return new UnresolvedFunCall(name, syntax, new Exp[] {left});
}
```

### Пример 3: Switch выражение для Quoting enum

**До:**
```java
public void toString(StringBuilder buf) {
    switch (quoting) {
    case UNQUOTED:
        buf.append(name);
        return;
    case QUOTED:
        Util.quoteMdxIdentifier(name, buf);
        return;
    default:
        throw Util.unexpected(quoting);
    }
}
```

**После:**
```java
public void toString(StringBuilder buf) {
    switch (quoting) {
        case UNQUOTED -> buf.append(name);
        case QUOTED -> Util.quoteMdxIdentifier(name, buf);
        default -> throw Util.unexpected(quoting);
    }
}
```

## Рекомендации по внедрению

### Инкрементальный подход
1. Начните с одного модуля или пакета
2. Сначала примените автоматические рефакторинги (IDE)
3. Добавьте тесты для измененных компонентов
4. Проверьте производительность критических участков

### Инструменты
1. **IDE рефакторинги:** IntelliJ IDEA или Eclipse имеют автоматические преобразования
2. **Статический анализ:** SpotBugs, Checkstyle для проверки кода
3. **Тестирование:** JUnit 5 с поддержкой новых возможностей Java
4. **Сборка:** Maven/Gradle с правильной настройкой компилятора

### Контроль качества
1. **Code Review:** Особое внимание на совместимость API
2. **Регрессионное тестирование:** Проверьте все существующие тесты
3. **Производительность:** Бенчмарки для критических участков
4. **Документация:** Обновите JavaDoc для новых возможностей

## Заключение

Модернизация кода Mondrian под Java 25 позволит:
- **Улучшить читаемость** кода за счет современных языковых конструкций
- **Увеличить безопасность типов** с помощью pattern matching и sealed классов
- **Сократить boilerplate код** с использованием record и var
- **Упростить поддержку** благодаря ясным архитектурным решениям
- **Подготовить код** к будущим версиям Java

Рекомендуется начать с наименее рискованных изменений и постепенно переходить к более значительным архитектурным улучшениям.

## Дополнительные возможности Java 25 для рассмотрения

### 18. Virtual Threads (Project Loom)
Для улучшения производительности IO-операций можно рассмотреть использование virtual threads.

### 19. Pattern matching для switch (Java 21)
Полная поддержка pattern matching в switch выражениях.

### 20. Sequenced Collections (Java 21)
Новые интерфейсы для коллекций с улучшенным доступом к первому/последнему элементам.

### 21. String Templates (Java 21 Preview)
Для улучшенного форматирования строк в будущих версиях.

### 22. Scoped Values (Java 21)
Для безопасной передачи данных между потоками.