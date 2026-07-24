package io.ten1010.common.eh;

import io.ten1010.common.eh.log.LogLevelResolver;
import io.ten1010.common.eh.log.Logger;
import io.ten1010.common.eh.log.LogMessageFactory;
import io.ten1010.common.eh.log.LoggerProvider;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.Optional;

@AllArgsConstructor
@Getter
@Setter
public class ExceptionHandler<Req extends Request, Res extends Response> {

    private ExceptionMetadataRegistry<Req, Res> metadataRegistry;
    private LoggerProvider loggerProvider;
    private LogMessageFactory<Req> logMessageFactory;

    public Res handle(Req request, Exception e) {
        ExceptionMetadata<Req, Res> metadata = this.getCompatibleMetadata(e);
        this.log(metadata, request, e);
        return this.createResponse(metadata, request, e);
    }

    private ExceptionMetadata<Req, Res> getCompatibleMetadata(Exception e) {
        Optional<ExceptionMetadata<Req, Res>> metadataOpt = this.metadataRegistry.getCompatibleMetadata(e.getClass());
        if (metadataOpt.isEmpty()) {
            throw new IllegalArgumentException("No compatible metadata found for " + e.getClass());
        }
        return metadataOpt.get();
    }

    private void log(ExceptionMetadata<Req, Res> metadata, Req request, Exception e) {
        LogLevelResolver<Req> resolver = metadata.getLogLevelResolver();
        Logger logger = this.loggerProvider.get(resolver.resolve(request, e));
        String message = this.logMessageFactory.createMessage(request, e);
        logger.log(message);
    }

    private Res createResponse(ExceptionMetadata<Req, Res> metadata, Req request, Exception e) {
        return metadata.getResponseFactory().create(request, e);
    }

}
