package org.musinsa.payments.point.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;

/**
 * API 요청 및 응답 로깅을 위한 필터
 */
@Slf4j
@Component
public class ApiLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Swagger UI 관련 요청은 로깅에서 제외
        String requestURI = request.getRequestURI();
        if (requestURI.contains("swagger") || requestURI.contains("api-docs")) {
            filterChain.doFilter(request, response);
            return;
        }

        ContentCachingRequestWrapper cachingRequest = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper cachingResponse = new ContentCachingResponseWrapper(response);

        long startTime = System.currentTimeMillis();
        
        try {
            filterChain.doFilter(cachingRequest, cachingResponse);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            logRequest(cachingRequest);
            logResponse(cachingResponse, duration);
            cachingResponse.copyBodyToResponse();
        }
    }

    private void logRequest(ContentCachingRequestWrapper request) {
        String queryString = request.getQueryString();
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String payload = new String(request.getContentAsByteArray());

        log.info("[REQUEST] {} {}{} | Body: {}", 
                method, uri, (queryString != null ? "?" + queryString : ""), payload);
    }

    private void logResponse(ContentCachingResponseWrapper response, long duration) {
        int status = response.getStatus();
        String payload = new String(response.getContentAsByteArray());

        log.info("[RESPONSE] Status: {} | Duration: {}ms | Body: {}", 
                status, duration, payload);
    }
}
