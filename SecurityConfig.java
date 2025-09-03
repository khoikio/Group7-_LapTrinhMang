package com.example.demo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import java.util.List;
import java.util.ArrayList;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
        // Tạo nhiều tài khoản sinh viên tự động
        List<UserDetails> users = new ArrayList<>();
        
        // Tạo tài khoản sinh viên từ sv01 đến sv10
        for (int i = 1; i <= 10; i++) {
            String username = String.format("sv%02d", i);
            UserDetails user = User.builder()
                    .username(username)
                    .password(passwordEncoder.encode("123"))
                    .roles("USER")
                    .build();
            users.add(user);
        }

        // Tài khoản admin
        UserDetails admin = User.builder()
                .username("admin")
                .password(passwordEncoder.encode("admin123"))
                .roles("ADMIN", "USER")
                .build();
        users.add(admin);

        // Tài khoản giảng viên
        UserDetails teacher = User.builder()
                .username("teacher01")
                .password(passwordEncoder.encode("teacher123"))
                .roles("TEACHER", "USER")
                .build();
        users.add(teacher);

        return new InMemoryUserDetailsManager(users);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/login", "/css/**", "/js/**", "/images/**", "/chat").permitAll()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/", true)
                .failureUrl("/login?error=true")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/login?logout=true")
                .permitAll()
            )
            .csrf(csrf -> csrf.disable()) // Disable CSRF for WebSocket
            .headers(headers -> headers.frameOptions().sameOrigin()); // Allow same-origin frames

        return http.build();
    }
}
