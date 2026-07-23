package io.ten1010.common.eh;

import io.ten1010.common.eh.log.LogLevelResolver;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class ExceptionMetadata<Req extends Request, Res extends Response> {

    private final LogLevelResolver<Req> logLevelResolver;
    private final ResponseFactory<Req, Res> responseFactory;

}
