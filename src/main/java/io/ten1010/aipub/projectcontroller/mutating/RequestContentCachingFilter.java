package io.ten1010.aipub.projectcontroller.mutating;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.springframework.web.util.ContentCachingRequestWrapper;

public class RequestContentCachingFilter implements Filter {

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (request instanceof HttpServletRequest) {
      String contentType = request.getContentType();
      if (contentType == null || !contentType.contains("multipart/form-data")) {
        request = new ContentCachingRequestWrapper((HttpServletRequest) request);
      }
    }
    chain.doFilter(request, response);
  }

}
