package org.microsoft.qintelipass.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Component
public class JwtUtil {

    public static final String USER_ID_CLAIM = "userId";
    public static final String BEARER_PREFIX = "Bearer ";

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    // 从令牌中获取用户名
    public String extractUsername(String token) throws Exception {
        return extractClaim(token, Claims::getSubject);
    }

    // 从令牌中获取用户 ID
    public Long extractUserId(String token) throws Exception {
        return extractClaim(token, claims -> claims.get(USER_ID_CLAIM, Long.class));
    }

    // 从令牌中获取过期时间
    public Date extractExpiration(String token) throws Exception {
        return extractClaim(token, Claims::getExpiration);
    }

    // 从令牌中获取声明
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) throws Exception {
        if (token != null && token.startsWith(BEARER_PREFIX)) {
            token = token.substring(BEARER_PREFIX.length());
        }
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    // 从令牌中获取所有声明
    private Claims extractAllClaims(String token) {
        if (token != null && token.startsWith(BEARER_PREFIX)) {
            token = token.substring(BEARER_PREFIX.length());
        }
        return Jwts
                .parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    // 检查令牌是否过期
    private Boolean isTokenExpired(String token) throws Exception {
        return extractExpiration(token).before(new Date());
    }

    // 生成令牌
    public String generateToken(UserDetails userDetails, Long userId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(USER_ID_CLAIM, userId);
        return createToken(claims, userDetails.getUsername());
    }

    // 生成令牌（只带用户名）
    public String generateToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        return createToken(claims, userDetails.getUsername());
    }

    // 创建令牌
    private String createToken(Map<String, Object> claims, String subject) {
        return Jwts.builder().setClaims(claims).setSubject(subject)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256).compact();
    }

    // 验证令牌
    public Boolean validateToken(String token, UserDetails userDetails) {
        try {
            final String username = extractUsername(token);
            return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
        } catch (Exception e) {
            return false;
        }
    }

    // 验证令牌（不检查用户详情）
    public Boolean validateToken(String token) {
        try {
            return !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }
}
