package com.mmtq.memora.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestLoggingFilterTest {

    private RequestLoggingFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RequestLoggingFilter();
    }

    @Test
    @DisplayName("Should pass request through filter chain and log completion for 200 OK")
    void testSuccessfulRequest() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/items");
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        MockFilterChain filterChain = new MockFilterChain();
        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Should include query parameters in request logging")
    void testRequestWithQueryParams() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/items");
        request.setQueryString("page=0&size=10");
        request.setRemoteAddr("192.168.1.50");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        MockFilterChain filterChain = new MockFilterChain();
        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Should correctly extract client IP from X-Forwarded-For header")
    void testXForwardedForClientIp() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/items");
        request.addHeader("X-Forwarded-For", "203.0.113.195, 70.41.3.18, 150.172.238.178");
        request.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(201);

        MockFilterChain filterChain = new MockFilterChain();
        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(201);
    }

    @Test
    @DisplayName("Should log and rethrow exception if downstream filter chain fails")
    void testExceptionPropagation() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/failing");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(500);

        FilterChain throwingChain = (req, res) -> {
            throw new RuntimeException("Simulated filter chain failure");
        };

        assertThatThrownBy(() -> filter.doFilter(request, response, throwingChain))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Simulated filter chain failure");
    }
}
