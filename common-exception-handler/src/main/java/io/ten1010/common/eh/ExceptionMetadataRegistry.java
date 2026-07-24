package io.ten1010.common.eh;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ExceptionMetadataRegistry<Req extends Request, Res extends Response> {
    private final ExceptionTree tree = new ExceptionTree();
    private final Map<Class<? extends Exception>, ExceptionMetadata<Req, Res>> metadataMap = new HashMap<>();

    public Optional<ExceptionMetadata<Req, Res>> getCompatibleMetadata(Class<? extends Exception> exClass) {
        Class<? extends Exception> compatibleEx = this.tree.getCompatibleException(exClass);
        return Optional.ofNullable(this.metadataMap.get(compatibleEx));
    }

    public ExceptionMetadataRegistry<Req, Res> register(Class<? extends Exception> exClass, ExceptionMetadata<Req, Res> metadata) {
        if (!exClass.equals(Exception.class)) {
            this.tree.addException(exClass);
        }
        this.metadataMap.put(exClass, metadata);
        return this;
    }

    public ExceptionMetadataRegistry<Req, Res> deregister(Class<? extends Exception> exClass) {
        if (!exClass.equals(Exception.class)) {
            this.tree.removeException(exClass);
        }
        this.metadataMap.remove(exClass);
        return this;
    }
}
