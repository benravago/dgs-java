package dgs.graphql.nf.internal;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

public class EntityFetcherRegistry {
    
    public record Entry(Object object, Method method) {}    
    
    public final Map<String, Entry> entityFetchers = new LinkedHashMap<>();
    
    public void put(String key, Object object, Method method) { entityFetchers.put(key, new Entry(object,method)); }

}

