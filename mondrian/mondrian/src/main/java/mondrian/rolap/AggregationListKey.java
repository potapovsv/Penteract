package mondrian.rolap;

import mondrian.olap.*;

import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.impl.list.mutable.FastList;
import java.util.*;

public final class AggregationListKey {
    // Храним как immutable List, но создаем через FastList
    private final List<List<Member>> members;
    private final int cachedHashCode;
    
    public AggregationListKey(List<List<Member>> members) {
        // ОПТИМИЗАЦИЯ: Убираем Stream API + делаем defensive copy через FastList
        MutableList<List<Member>> defensiveCopy = FastList.newList(members.size());
        for (List<Member> inner : members) {
            defensiveCopy.add(FastList.newList(inner)); // Копия без Stream
        }
        this.members = Collections.unmodifiableList(defensiveCopy);
        this.cachedHashCode = computeHashCode();
    }
    
    private int computeHashCode() {
        int hash = 0;
        int size = members.size();
        for (int i = 0; i < size; i++) {
            List<Member> inner = members.get(i);
            int innerHash = 1;
            int innerSize = inner.size();
            for (int j = 0; j < innerSize; j++) {
                innerHash = 31 * innerHash + inner.get(j).getUniqueName().hashCode();
            }
            hash = 31 * hash + innerHash;
        }
        return hash;
    }
    
    @Override
    public int hashCode() {
        return cachedHashCode;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof AggregationListKey)) return false;
        AggregationListKey other = (AggregationListKey) obj;
        if (cachedHashCode != other.cachedHashCode) return false;
        return members.equals(other.members);
    }
}