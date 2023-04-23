package dgs.graphql.nf.support;

import java.util.concurrent.Callable;
import org.springframework.util.ReflectionUtils;

public interface Kt {

    static Class<?> classForName(String name) {
        try { return Class.forName(name); }
        catch (Exception e) { throw unchecked(e); }
    }

    static <T extends Enum<T>> T enumValue(Class<?> enumClass, String value) {
        try { return (T) enumClass.getDeclaredMethod("valueOf", String.class).invoke(null, value); }
        catch (Exception e)  { throw unchecked(e); }
    }

    static <T> T newInstance(Class<T> targetClass) {
        try {
            var ctor = ReflectionUtils.accessibleConstructor(targetClass);
            ReflectionUtils.makeAccessible(ctor);
            return ctor.newInstance();
        }
        catch (Exception e)  { throw unchecked(e); }
    }

    static <V> V call(Callable<V> mightThrow) {
        try { return mightThrow.call(); }
        catch (Exception e)  { throw unchecked(e); }
    }

    static <T extends Exception> T unchecked(Exception t) throws T { throw (T)t; }

}
