package io.ten1010.common.eh;

public interface ResponseFactory<Req extends Request, Res extends Response> {
    Res create(Req request, Exception e);
}
