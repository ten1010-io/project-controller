package io.ten1010.common.eh;

public abstract class CastingUtils {
    public static <T> T cast(Object obj, Class<T> objClass) {
        if (objClass.isInstance(obj)) {
            return objClass.cast(obj);
        }
        throw new IllegalArgumentException();
    }
}
