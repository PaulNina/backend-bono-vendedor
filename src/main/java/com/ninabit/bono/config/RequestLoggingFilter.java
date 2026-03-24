package com.ninabit.bono.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;

@Component
@Slf4j
public class RequestLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        long startTime = System.currentTimeMillis();

        // Wrap request if future need arises to read body, but for now just pass it
        ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(request);

        filterChain.doFilter(requestWrapper, response);

        long duration = System.currentTimeMillis() - startTime;
        String userCorreo = "guest / system";

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !auth.getPrincipal().equals("anonymousUser")) {
            userCorreo = auth.getName();
        }

        log.info("{} | {} {} | Status: {} | {}ms",
                userCorreo,
                request.getMethod(),
                request.getRequestURI(),
                response.getStatus(),
                duration);
    }
}
