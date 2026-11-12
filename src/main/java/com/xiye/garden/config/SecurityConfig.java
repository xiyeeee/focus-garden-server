package com.xiye.garden.config;

import com.xiye.garden.auth.security.*;
import com.xiye.garden.auth.service.AuthService;
import com.xiye.garden.auth.dto.LoginDTO;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import com.xiye.garden.user.mapper.UserMapper;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.password.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.*;
import org.springframework.security.web.csrf.*;
import org.springframework.security.web.authentication.session.*;
import org.springframework.security.config.http.SessionCreationPolicy;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    public AuthenticationManager authenticationManager(
            AuthService auth) {
        return request -> {
            if (!(request.getPrincipal() instanceof String email) || !(request.getCredentials() instanceof String password)
                    || !(request.getDetails() instanceof WebAuthenticationDetails details)) {
                throw new BadCredentialsException("邮箱或密码错误");
            }
            var principal = auth.login(new LoginDTO(email, password), details.getRemoteAddress());
            return UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of());
        };
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        String algorithm = "pbkdf2@SpringSecurity_v5_8";
        return new DelegatingPasswordEncoder(algorithm,
                Map.of(algorithm, Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8()));
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public CsrfTokenRepository csrfTokenRepository() {
        return new HttpSessionCsrfTokenRepository();
    }

    @Bean
    public SessionAuthenticationStrategy sessionAuthenticationStrategy(CsrfTokenRepository csrf) {
        return new CompositeSessionAuthenticationStrategy(List.of(
                new ChangeSessionIdAuthenticationStrategy(), new CsrfAuthenticationStrategy(csrf)));
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository contexts,
            CsrfTokenRepository csrf, UserMapper users, SecurityJsonResponse responses) throws Exception {
        http.cors(cors -> {})
                .csrf(config -> config.csrfTokenRepository(csrf))
                .securityContext(config -> config.securityContextRepository(contexts).requireExplicitSave(true))
                .sessionManagement(config -> config.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(config -> config
                        .requestMatchers(HttpMethod.GET, "/api/health", "/api/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/email-codes", "/api/auth/register",
                                "/api/auth/login", "/api/auth/password/reset").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(config -> config
                        .authenticationEntryPoint((request, response, exception) ->
                                responses.error(response, 401, "UNAUTHORIZED", "请先登录或重新登录"))
                        .accessDeniedHandler(responses::denied))
                .logout(config -> config.logoutUrl("/api/auth/logout")
                        .invalidateHttpSession(true).clearAuthentication(true).deleteCookies("GARDEN_SESSION")
                        .logoutSuccessHandler((request, response, authentication) -> responses.logout(response)))
                .addFilterAfter(new SessionUserFilter(users, responses), SecurityContextHolderFilter.class);
        return http.build();
    }
}
