package com.wallet.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final ApiKeyAuthenticationFilter apiKeyAuthFilter;
    private final JwtService jwtService;
    private final com.wallet.service.UserService userService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers(
                            "/auth/**",
                            "/wallet/paystack/webhook",
                            "/swagger-ui/**",
                            "/v3/api-docs/**",
                            "/docs/**",
                            "/oauth2/**",
                            "/error/**"
                    ).permitAll()
                    .anyRequest().authenticated()
            )
            // ⚠️ Enable OAuth2 login with custom callback
            .oauth2Login(oauth -> oauth
                    .loginPage("/auth/google") // Optional: endpoint that starts login
                    .redirectionEndpoint(redir -> redir
                            .baseUri("/auth/google/callback") // <-- your custom callback
                    )
                    .successHandler(new SimpleUrlAuthenticationSuccessHandler() {
                        @Override
                        protected void onAuthenticationSuccess(
                                javax.servlet.http.HttpServletRequest request,
                                javax.servlet.http.HttpServletResponse response,
                                org.springframework.security.core.Authentication authentication
                        ) throws java.io.IOException {
                            // Generate JWT for the authenticated user
                            var oauthUser = (org.springframework.security.oauth2.core.user.OAuth2User) authentication.getPrincipal();

                            String googleId = oauthUser.getAttribute("sub");
                            String email = oauthUser.getAttribute("email");
                            String name = oauthUser.getAttribute("name");
                            String picture = oauthUser.getAttribute("picture");

                            var user = userService.findOrCreateUser(googleId, email, name, picture);
                            String token = jwtService.generateToken(user);

                            // Return JSON instead of redirecting
                            response.setContentType("application/json");
                            response.getWriter().write(
                                    "{ \"token\": \"" + token + "\", \"tokenType\": \"Bearer\" }"
                            );
                        }
                    })
            )
            // ⚠️ Temporary session required for OAuth2 handshake
            .sessionManagement(session -> session
                    .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            );

        // Add JWT & API key filters for normal API requests
        http.addFilterBefore(apiKeyAuthFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList(
                "Authorization", "Content-Type", "x-api-key", "X-Requested-With"
        ));
        configuration.setExposedHeaders(List.of("Authorization"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}