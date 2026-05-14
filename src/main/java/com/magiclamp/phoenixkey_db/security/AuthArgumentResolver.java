package com.magiclamp.phoenixkey_db.security;

import com.magiclamp.phoenixkey_db.exception.AppException;
import com.magiclamp.phoenixkey_db.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
@RequiredArgsConstructor
public class AuthArgumentResolver implements HandlerMethodArgumentResolver {

    private final JwtService jwtService;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType().equals(AuthenticatedUser.class);
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {
        String authHeader = webRequest.getHeader("Authorization");
        if (authHeader == null || !authHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Missing Bearer token");
        }
        String token = authHeader.substring(7).trim();
        Claims claims = jwtService.parseAndVerify(token);
        String tokenType = claims.get("type", String.class);
        if (!"session".equals(tokenType)) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Required token type: session, got " + tokenType);
        }
        return new AuthenticatedUser(claims.getSubject(), tokenType);
    }
}
