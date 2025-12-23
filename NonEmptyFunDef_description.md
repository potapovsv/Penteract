# Анализ класса NonEmptyFunDef.java

**Класс `NonEmptyFunDef`** реализует функцию `NonEmpty` в MDX (Multidimensional Expressions). Функция возвращает множество кортежей из первого аргумента, которые дают непустое значение в текущем контексте (либо в пересечении со вторым множеством, если оно указано).

## Основные компоненты

### 1. Регистрация функции
```java
static final ReflectiveMultiResolver Resolver =
        new ReflectiveMultiResolver(
                "NonEmpty",
                "NonEmpty(<Set1>[, <Set2>])",
                "Returns the set of tuples that are not empty ...",
                new String[] {"fxx", "fxxx"},
                NonEmptyFunDef.class);
```
Функция объявлена с двумя сигнатурами:
- `fxx` – один аргумент (`NonEmpty(set1)`)
- `fxxx` – два аргумента (`NonEmpty(set1, set2)`)

### 2. Компиляция вызова
Метод `compileCall` преобразует синтаксическое дерево вызова в исполняемый объект `Calc`:
```java
public Calc compileCall(ResolvedFunCall call, ExpCompiler compiler) {
    final ListCalc listCalc1 = compiler.compileList(call.getArg(0));
    ListCalc listCalc2 = null;
    if(call.getArgCount() == 2) {
        listCalc2 = compiler.compileList(call.getArg(1));
    }
    return new NonEmptyListCalcImpl(call, listCalc1, listCalc2);
}
```

### 3. Внутренний класс `NonEmptyListCalcImpl`
Расширяет `AbstractListCalc` и содержит основную логику вычисления.

#### Конструктор и поля:
```java
private final ListCalc listCalc1;
private final ListCalc listCalc2;
```
- `listCalc1` – вычисление первого множества
- `listCalc2` – вычисление второго множества (может быть `null`)

#### Метод `evaluateList` – ключевая логика:

**Шаг 1 – Сохранение состояния вычисления**
```java
final int savepoint = evaluator.savepoint();
try {
    // ... вычисления
} finally {
    evaluator.restore(savepoint);
}
```
Используется механизм savepoint/restore для временного изменения контекста вычислений.

**Шаг 2 – Вычисление второго множества (если есть)**
```java
evaluator.setNonEmpty(false);  // отключаем фильтрацию пустых значений
TupleList rightTuples = null;
boolean hasRightTuples = false;
if (this.listCalc2 != null) {
    rightTuples = listCalc2.evaluateList(evaluator);
    hasRightTuples = rightTuples != null && !rightTuples.isEmpty();
}
```

**Шаг 3 – Вычисление первого множества с фильтрацией**
```java
evaluator.setNonEmpty(true);  // включаем фильтрацию пустых значений
TupleList leftTuples = listCalc1.evaluateList(evaluator);
if (leftTuples.isEmpty()) {
    return TupleCollections.emptyList(leftTuples.getArity());
}
```

**Шаг 4 – Фильтрация кортежей**
Создаётся результирующий список с оптимизацией (`FastList` вместо `ArrayList`):

**Вариант с двумя множествами** (декартово произведение):
```java
if (hasRightTuples) {
    for (List<Member> leftTuple : leftTuples) {
        for (List<Member> rightTuple : rightTuples) {
            evaluator.setContext(leftTuple);
            evaluator.setContext(rightTuple);
            Object tupleResult = evaluator.evaluateCurrent();
            if (tupleResult != null) {
                result.add(leftTuple);
                break;  // достаточно одного непустого пересечения
            }
        }
    }
}
```

**Вариант с одним множеством** (проверка в текущем контексте):
```java
for (List<Member> leftTuple : leftTuples) {
    evaluator.setContext(leftTuple);
    Object tupleResult = evaluator.evaluateCurrent();
    if (tupleResult != null) {
        result.add(leftTuple);
    }
}
```

### 4. Зависимости
Метод `dependsOn` определяет зависимость вычисления от иерархии:
```java
public boolean dependsOn(Hierarchy hierarchy) {
    return anyDependsButFirst(getCalcs(), hierarchy);
}
```

## Взаимосвязь с другими компонентами

### Управление состоянием `nonEmpty`
В `RolapEvaluator.java` методы `setNonEmpty()`, `push(boolean nonEmpty)`, `isNonEmpty()` управляют флагом фильтрации пустых значений. Команда `Command.SET_NON_EMPTY` позволяет откатывать состояние при backtracking.

### Нативная оптимизация
В `CrossJoinFunDef.java` методы `nonEmptyOptimizeList()` и `nonEmptyList()` оптимизируют вычисления, применяя фильтрацию пустых значений на уровне нативного выполнения.

### Специальная функция `NonEmptyCrossJoin`
`NonEmptyCrossJoinFunDef` (упоминается в `RolapNativeCrossJoin.java`) – отдельная реализация для нативного выполнения `NonEmpty` над декартовым произведением. Метод `safeToConstrainByOtherAxes()` возвращает `false` для `NonEmptyCrossJoinFunDef`, что означает особую обработку: ограничения других осей не применяются для этой функции.

### Синтаксический разбор
Парсеры (`Parser.cup`, `MdxParser.jj`) обрабатывают конструкции `IS EMPTY`, `IN`, `MATCHES` и их отрицания, но сама функция `NonEmpty` разбирается как обычный вызов функции.

## Оптимизации в коде

1. **Ранний выход**: если первое множество пустое, сразу возвращается пустой результат.
2. **Прерывание внутреннего цикла**: при наличии второго множества, как только найдено непустое пересечение для кортежа, дальше проверять не нужно (`break`).
3. **Использование `FastList`**: вместо стандартного `ArrayList` для повышения производительности.
4. **Логирование времени выполнения**: для отладки производительности.

## Семантика MDX

Функция `NonEmpty(set1 [, set2])` возвращает кортежи из `set1`, для которых:
- При одном аргументе: значение в текущем контексте не пусто.
- При двух аргументах: существует хотя бы одна комбинация с кортежем из `set2`, дающая непустое значение.

Это соответствует стандарту MDX и используется для фильтрации пустых ячеек в результатах запросов.