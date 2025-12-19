package mondrian.rolap;

import mondrian.olap.*;
import mondrian.rolap.agg.*;
import mondrian.rolap.aggmatcher.AggGen;
import mondrian.rolap.aggmatcher.AggStar;
import mondrian.rolap.cache.SegmentCacheIndex;
import mondrian.rolap.cache.SegmentCacheIndexImpl;
import mondrian.server.Execution;
import mondrian.server.Locus;
import mondrian.spi.*;
import mondrian.util.*;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.util.*;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

public final class AggregationListKey {
    private final List<List<Member>> members;
    private final int cachedHashCode;
    
    public AggregationListKey(List<List<Member>> members) {
        // Defensive copy + immutable
        this.members = members.stream()
            .map(List::copyOf)
            .collect(Collectors.toUnmodifiableList());
        this.cachedHashCode = computeHashCode();
    }
    
    private int computeHashCode() {
        int hash = 0;
        // Оптимизированная версия без создания итераторов
        for (int i = 0; i < members.size(); i++) {
            List<Member> inner = members.get(i);
            int innerHash = 1;
            for (int j = 0; j < inner.size(); j++) {
                // Используем uniqueName — он уже кэширует hashCode
                innerHash = 31 * innerHash + inner.get(j).getUniqueName().hashCode();
            }
            hash = 31 * hash + innerHash;
        }
        return hash;
    }
    
    @Override
    public int hashCode() {
        return cachedHashCode; // O(1) вместо O(n*m)
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof AggregationListKey)) return false;
        AggregationListKey other = (AggregationListKey) obj;
        // Быстрая проверка hashCode
        if (cachedHashCode != other.cachedHashCode) return false;
        return members.equals(other.members);
    }
}