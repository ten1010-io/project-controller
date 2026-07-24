package io.ten1010.common.eh.web.spring;

import io.ten1010.common.eh.CastingUtils;
import io.ten1010.common.eh.ExceptionMetadata;
import io.ten1010.common.eh.log.LogLevelEnum;
import io.ten1010.common.eh.web.WebRequest;
import io.ten1010.common.eh.web.WebResponse;
import io.ten1010.common.eh.web.WebResponseBody;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

public enum SpringExceptionMetadataEnum {
    AuthenticationException(LogLevelEnum.DEBUG, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", null),
    BadCredentialsException(LogLevelEnum.DEBUG, HttpStatus.UNAUTHORIZED, "BAD_CREDENTIAL", null),
    InsufficientAuthenticationException(LogLevelEnum.DEBUG, HttpStatus.UNAUTHORIZED, "INSUFFICIENT_AUTHENTICATION", null),
    UsernameNotFoundException(LogLevelEnum.DEBUG, HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND", null),
    OAuth2AuthenticationException(LogLevelEnum.DEBUG, HttpStatus.UNAUTHORIZED, "OAUTH2_ERROR", e -> {
        OAuth2AuthenticationException casted = CastingUtils.cast(e, OAuth2AuthenticationException.class);
        OAuth2Error error = casted.getError();
        Oauth2ErrorDetail detail = new Oauth2ErrorDetail();
        detail.setErrorCode(error.getErrorCode());
        detail.setDescription(error.getDescription());
        detail.setUri(error.getUri());
        return detail;
    }),
    AccessDeniedException(LogLevelEnum.DEBUG, HttpStatus.FORBIDDEN, "FORBIDDEN", null),
    NoHandlerFoundException(LogLevelEnum.DEBUG, HttpStatus.NOT_FOUND, "NO_HANDLER_FOUND", null),
    HttpRequestMethodNotSupportedException(LogLevelEnum.DEBUG, HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", null),
    MissingServletRequestPartException(LogLevelEnum.DEBUG, HttpStatus.BAD_REQUEST, "INVALID_MULTIPART_REQUEST", null),
    HttpMediaTypeNotSupportedException(LogLevelEnum.DEBUG, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", null),
    HttpMediaTypeNotAcceptableException(LogLevelEnum.DEBUG, HttpStatus.NOT_ACCEPTABLE, "NOT_ACCEPTABLE", null),
    ServletRequestBindingException(LogLevelEnum.ERROR, HttpStatus.INTERNAL_SERVER_ERROR, "REQUEST_BINDING_ERROR", null),
    MissingPathVariableException(LogLevelEnum.DEBUG, HttpStatus.BAD_REQUEST, "MISSING_PATH_VARIABLE", e -> {
        MissingPathVariableException casted = CastingUtils.cast(e, MissingPathVariableException.class);
        VariableNameDetail detail = new VariableNameDetail();
        detail.setVariableName(casted.getVariableName());
        return detail;
    }),
    MissingServletRequestParameterException(LogLevelEnum.DEBUG, HttpStatus.BAD_REQUEST, "MISSING_REQUEST_PARAMETER", e -> {
        MissingServletRequestParameterException casted = CastingUtils.cast(e, MissingServletRequestParameterException.class);
        ParameterNameDetail detail = new ParameterNameDetail();
        detail.setParameterName(casted.getParameterName());
        return detail;
    }),
    TypeMismatchException(LogLevelEnum.ERROR, HttpStatus.INTERNAL_SERVER_ERROR, "TYPE_MISMATCH_ERROR", null),
    MethodArgumentTypeMismatchException(LogLevelEnum.DEBUG, HttpStatus.BAD_REQUEST, "ARGUMENT_TYPE_MISMATCH", e -> {
        MethodArgumentTypeMismatchException casted = CastingUtils.cast(e, MethodArgumentTypeMismatchException.class);
        ArgumentNameDetail detail = new ArgumentNameDetail();
        detail.setArgumentName(casted.getName());
        return detail;
    }),
    ConversionNotSupportedException(LogLevelEnum.ERROR, HttpStatus.INTERNAL_SERVER_ERROR, "CONVERSION_NOT_SUPPORTED", null),
    HttpMessageNotReadableException(LogLevelEnum.DEBUG, HttpStatus.BAD_REQUEST, "HTTP_MESSAGE_READ_ERROR", null),
    HttpMessageNotWritableException(LogLevelEnum.ERROR, HttpStatus.INTERNAL_SERVER_ERROR, "HTTP_MESSAGE_WRITE_ERROR", null),
    BindException(LogLevelEnum.DEBUG, HttpStatus.BAD_REQUEST, "BIND_ERROR", null),
    MethodArgumentNotValidException(LogLevelEnum.DEBUG, HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", e -> {
        MethodArgumentNotValidException casted = CastingUtils.cast(e, MethodArgumentNotValidException.class);
        ParameterNameDetail detail = new ParameterNameDetail();
        detail.setParameterName(casted.getParameter().getParameterName());
        return detail;
    }),
    AsyncRequestTimeoutException(LogLevelEnum.ERROR, HttpStatus.SERVICE_UNAVAILABLE, "REQUEST_TIMEOUT", null);

    private final LogLevelEnum logLevel;
    private final HttpStatus httpStatus;
    private final String type;
    @Nullable
    private final Function<Exception, Object> detailFactory;

    public ExceptionMetadata<WebRequest, WebResponse> getMetadata() {
        return new ExceptionMetadata<>((request, e) -> this.logLevel, (request, e) -> {
            WebResponseBody body = this.detailFactory == null ? new WebResponseBody(this.type, null) : new WebResponseBody(this.type, this.detailFactory.apply(e));
            return new WebResponse(e, this.httpStatus.value(), body);
        });
    }

    SpringExceptionMetadataEnum(LogLevelEnum logLevel, HttpStatus httpStatus, String type, Function<Exception, Object> detailFactory) {
        this.logLevel = logLevel;
        this.httpStatus = httpStatus;
        this.type = type;
        this.detailFactory = detailFactory;
    }
}
