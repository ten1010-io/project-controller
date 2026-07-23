package io.ten1010.common.eh;

public interface MetadataRegistrar<Req extends Request, Res extends Response> {
    void register(ExceptionMetadataRegistry<Req, Res> registry);
}
