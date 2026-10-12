package com.wys.inventory.config;

import com.wys.inventory.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.authentication.logout.SimpleUrlLogoutSuccessHandler;
import jakarta.servlet.DispatcherType;

/**
 * Spring Security configuration.
 *
 * Route-level access has been deliberately simplified: all authenticated users
 * can reach any ZUL page URL. Granular page/button/action-level permissions are
 * evaluated at runtime by AccessControlService inside each ViewModel — this is the
 * Dynamic RBAC pattern that replaces the old hardcoded role matchers.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private UserService userService;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            com.wys.inventory.model.User user = userService.findByUsername(username);
            if (user == null) {
                throw new UsernameNotFoundException("User not found: " + username);
            }
            // Build Spring Security UserDetails with the role_code from the DB Role entity
            String roleCode = user.getRoleCode() != null ? user.getRoleCode() : "USER";
            return org.springframework.security.core.userdetails.User
                    .withUsername(user.getUsername())
                    .password(user.getPassword())
                    .roles(roleCode)
                    .build();
        };
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .dispatcherTypeMatchers(DispatcherType.FORWARD, DispatcherType.ERROR).permitAll()
                // Public paths
                .requestMatchers(
                    "/zul/login", "/login", "/perform_login", "/perform_logout", "/error"
                ).permitAll()
                .requestMatchers("/zul/index", "/").permitAll()
                // ZK & static assets always public
                .requestMatchers("/zkau/**", "/zk/**", "/web/**", "/style/**", "/~./**").permitAll()
                // All ZUL app pages require authentication only.
                // Fine-grained access (which menus/actions) is handled by AccessControlService.
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/zul/login")
                .loginProcessingUrl("/perform_login")
                .defaultSuccessUrl("/zul/app", true)
                .failureUrl("/zul/login?error=true")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/perform_logout")
                .logoutSuccessHandler(logoutSuccessHandler())
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            )
            .csrf(csrf -> csrf.disable())
            .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));

        return http.build();
    }

    @Bean
    public LogoutSuccessHandler logoutSuccessHandler() {
        SimpleUrlLogoutSuccessHandler handler = new SimpleUrlLogoutSuccessHandler();
        handler.setDefaultTargetUrl("/zul/login?logout=true");
        return handler;
    }
}
