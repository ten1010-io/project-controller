package io.ten1010.common.eh.web.spring;

import io.ten1010.common.eh.ExceptionMetadataRegistry;
import io.ten1010.common.eh.web.WebMetadataRegistrar;
import io.ten1010.common.eh.web.WebRequest;
import io.ten1010.common.eh.web.WebResponse;
import org.springframework.beans.ConversionNotSupportedException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;

public class SpringWebExceptionsWebMetadataRegistrar implements WebMetadataRegistrar {
    @Override
    public void register(ExceptionMetadataRegistry<WebRequest, WebResponse> registry) {
        registry.register(NoHandlerFoundException.class, SpringExceptionMetadataEnum.NoHandlerFoundException.getMetadata())
                .register(HttpRequestMethodNotSupportedException.class, SpringExceptionMetadataEnum.HttpRequestMethodNotSupportedException.getMetadata())
                .register(MissingServletRequestPartException.class, SpringExceptionMetadataEnum.MissingServletRequestPartException.getMetadata())
                .register(HttpMediaTypeNotSupportedException.class, SpringExceptionMetadataEnum.HttpMediaTypeNotSupportedException.getMetadata())
                .register(HttpMediaTypeNotAcceptableException.class, SpringExceptionMetadataEnum.HttpMediaTypeNotAcceptableException.getMetadata())
                .register(ServletRequestBindingException.class, SpringExceptionMetadataEnum.ServletRequestBindingException.getMetadata())
                .register(MissingPathVariableException.class, SpringExceptionMetadataEnum.MissingPathVariableException.getMetadata())
                .register(MissingServletRequestParameterException.class, SpringExceptionMetadataEnum.MissingServletRequestParameterException.getMetadata())
                .register(TypeMismatchException.class, SpringExceptionMetadataEnum.TypeMismatchException.getMetadata())
                .register(MethodArgumentTypeMismatchException.class, SpringExceptionMetadataEnum.MethodArgumentTypeMismatchException.getMetadata())
                .register(ConversionNotSupportedException.class, SpringExceptionMetadataEnum.ConversionNotSupportedException.getMetadata())
                .register(HttpMessageNotReadableException.class, SpringExceptionMetadataEnum.HttpMessageNotReadableException.getMetadata())
                .register(HttpMessageNotWritableException.class, SpringExceptionMetadataEnum.HttpMessageNotWritableException.getMetadata())
                .register(BindException.class, SpringExceptionMetadataEnum.BindException.getMetadata())
                .register(MethodArgumentNotValidException.class, SpringExceptionMetadataEnum.MethodArgumentNotValidException.getMetadata())
                .register(AsyncRequestTimeoutException.class, SpringExceptionMetadataEnum.AsyncRequestTimeoutException.getMetadata());
    }
}
