package io.ten1010.aipub.projectcontroller.mutating;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ten1010.aipub.projectcontroller.domain.k8s.ObjectMapperFactory;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1AdmissionReview;
import io.ten1010.aipub.projectcontroller.mutating.service.AdmissionReviewService;
import io.ten1010.common.eh.ExceptionMetadataRegistry;
import io.ten1010.common.eh.log.LoggerProvider;
import io.ten1010.common.eh.log.Slf4jLoggerProvider;
import io.ten1010.common.eh.web.ExceptionWebMetadataRegistrar;
import io.ten1010.common.eh.web.WebExceptionHandler;
import io.ten1010.common.eh.web.WebResponse;
import io.ten1010.common.eh.web.spring.SpringWebExceptionHandlerAdapter;
import io.ten1010.common.eh.web.spring.SpringWebExceptionsWebMetadataRegistrar;
import io.ten1010.common.eh.web.spring.SpringWebResponseConverter;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.util.ContentCachingRequestWrapper;

import static io.ten1010.aipub.projectcontroller.mutating.AdmissionReviewController.PATH;

@RestController
@RequestMapping(PATH)
public class AdmissionReviewController {

    public static final String PATH = "/api/v1/admissionreviews";

    private static WebExceptionHandler createWebExceptionHandler() {
        ExceptionMetadataRegistry<io.ten1010.common.eh.web.WebRequest, WebResponse> backendMetadataRegistry = new ExceptionMetadataRegistry<>();
        new ExceptionWebMetadataRegistrar().register(backendMetadataRegistry);
        new SpringWebExceptionsWebMetadataRegistrar().register(backendMetadataRegistry);
        new AipubBackendResponseExceptionWebMetadataRegistrar().register(backendMetadataRegistry);

        ExceptionMetadataRegistry<io.ten1010.common.eh.web.WebRequest, WebResponse> frontendMetadataRegistry = new ExceptionMetadataRegistry<>();
        new ExceptionWebMetadataRegistrar().register(frontendMetadataRegistry);
        new AdmissionReviewExceptionWebMetadataRegistrar(backendMetadataRegistry).register(frontendMetadataRegistry);

        LoggerProvider loggerProvider = new Slf4jLoggerProvider(
                AdmissionReviewController.class.getSimpleName() + "." + WebExceptionHandler.class.getSimpleName());
        return new WebExceptionHandler(frontendMetadataRegistry, loggerProvider);
    }

    private static SpringWebExceptionHandlerAdapter createExceptionHandlerAdapter(WebExceptionHandler exceptionHandler) {
        SpringWebResponseConverter responseConverter = new AdmissionReviewSpringWebResponseConverter();
        SpringWebExceptionHandlerAdapter handlerAdapter = new SpringWebExceptionHandlerAdapter(exceptionHandler);
        handlerAdapter.setResponseConverter(responseConverter);

        return handlerAdapter;
    }

    private final AdmissionReviewService reviewService;
    private final SpringWebExceptionHandlerAdapter exceptionHandler;
    private final ObjectMapper objectMapper;

    public AdmissionReviewController(AdmissionReviewService reviewService) {
        this.reviewService = reviewService;
        this.exceptionHandler = createExceptionHandlerAdapter(createWebExceptionHandler());
        this.objectMapper = new ObjectMapperFactory().createObjectMapper();
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @PostMapping(value = "", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<V1AdmissionReview> create(@RequestBody V1AdmissionReview review) {
        V1AdmissionReview clone = V1AdmissionReviewUtils.clone(review);

        this.reviewService.review(clone);

        return ResponseEntity.ok(clone);
    }

    @ExceptionHandler
    public ResponseEntity<Object> handle(WebRequest request, Exception exception) {
        try {
            V1AdmissionReview review = getV1AdmissionReview(request);
            AdmissionReviewException reviewException = new AdmissionReviewException(exception, review);
            return this.exceptionHandler.handle(request, reviewException);
        } catch (Exception ignored) {
            return this.exceptionHandler.handle(request, exception);
        }
    }

    private V1AdmissionReview getV1AdmissionReview(WebRequest request) throws JsonProcessingException {
        if (!(request instanceof ServletWebRequest servletWebRequest)) {
            throw new IllegalArgumentException("request must be an instance of ServletWebRequest");
        }
        if (!(servletWebRequest.getNativeRequest() instanceof ContentCachingRequestWrapper nativeRequest)) {
            throw new IllegalArgumentException("nativeRequest must be an instance of ContentCachingRequestWrapper");
        }
        String json = nativeRequest.getContentAsString();

        return this.objectMapper.readValue(json, V1AdmissionReview.class);
    }

}
