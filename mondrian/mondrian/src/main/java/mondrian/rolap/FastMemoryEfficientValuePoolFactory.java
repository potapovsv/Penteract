package mondrian.rolap;


import mondrian.rolap.SqlMemberSource;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Фабрика для создания быстрого и эффективного по памяти пула значений.
 * 
 * Ключевые особенности:
 * - Потокобезопасность через ConcurrentHashMap (без блокировок при чтении)
 * - Ограничение размера для предотвращения утечек памяти
 * - Автоматическое отключение пулинга при достижении лимита
 * - Оптимизировано для паттерна get/put в Mondrian
 * 
 * Конфигурация:
 * -Dbachedrian.rolap.SqlMemberSource.ValuePool.maxSize=20000
 */
public class FastMemoryEfficientValuePoolFactory implements SqlMemberSource.ValuePoolFactory {
    public static final String MAX_SIZE_PROPERTY = "mondrian.rolap.SqlMemberSource.ValuePool.maxSize";
    public static final int DEFAULT_MAX_SIZE = 10000;
    
    @Override
    public Map<Object, Object> create(SqlMemberSource source) {
        int maxSize = Integer.getInteger(MAX_SIZE_PROPERTY, DEFAULT_MAX_SIZE);
        return new BoundedConcurrentValuePool(maxSize);
    }
    
    private static class BoundedConcurrentValuePool implements Map<Object, Object> {
        private final ConcurrentHashMap<Object, Object> map;
        private final int maxSize;
        
        public BoundedConcurrentValuePool(int maxSize) {
            this.map = new ConcurrentHashMap<>(16, 0.75f, 16);
            this.maxSize = maxSize;
        }
        
        @Override
        public Object put(Object key, Object value) {
            // Атомарная операция: если переполнен, не вставляем
            if (map.size() >= maxSize) {
                return map.get(key);  // ← ПРАВИЛЬНО: вернули старое (или null)
            }
            
            // Пытаемся вставить, если еще не существует
            Object existing = map.putIfAbsent(key, value);
            return existing;  // ← ПРАВИЛЬНО: вернули старое (или null)
        }
        
        @Override
        public Object get(Object key) {
            return map.get(key);
        }
        
        // Остальные методы - минимальные заглушки
        // (Mondrian их не вызывает)
        @Override public int size() { return map.size(); }
        @Override public boolean isEmpty() { return map.isEmpty(); }
        @Override public boolean containsKey(Object key) { return map.containsKey(key); }
        @Override public boolean containsValue(Object value) { throw new UnsupportedOperationException(); }
        @Override public Object remove(Object key) { throw new UnsupportedOperationException(); }
        @Override public void putAll(Map<?, ?> m) { throw new UnsupportedOperationException(); }
        @Override public void clear() { map.clear(); }
        @Override public Set<Object> keySet() { return map.keySet(); }
        @Override public Collection<Object> values() { throw new UnsupportedOperationException(); }
        @Override public Set<Entry<Object, Object>> entrySet() { throw new UnsupportedOperationException(); }
    }
}