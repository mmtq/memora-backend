package com.mmtq.memora.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger( RequestLoggingFilter.class );

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        long startTime = System.currentTimeMillis();
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String queryString = request.getQueryString();
        String pathWithQuery = queryString != null ? uri + "?" + queryString : uri;
        String clientIp = resolveClientIp( request );

        try {
            filterChain.doFilter( request, response );
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            int status = response.getStatus();

            if ( status >= 500 ) {
                log.error( "[HTTP] {} {} -> {} in {}ms (ip: {})", method, pathWithQuery, status, duration, clientIp );
            } else if ( status >= 400 ) {
                log.warn( "[HTTP] {} {} -> {} in {}ms (ip: {})", method, pathWithQuery, status, duration, clientIp );
            } else {
                log.info( "[HTTP] {} {} -> {} in {}ms (ip: {})", method, pathWithQuery, status, duration, clientIp );
            }
        }
    }

    private String resolveClientIp( HttpServletRequest request ) {
        String xForwardedFor = request.getHeader( "X-Forwarded-For" );
        if ( xForwardedFor != null && !xForwardedFor.isBlank() ) {
            return xForwardedFor.split( "," )[0].trim();
        }
        return request.getRemoteAddr();
    }
}
