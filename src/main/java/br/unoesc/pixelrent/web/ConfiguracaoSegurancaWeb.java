package br.unoesc.pixelrent.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class ConfiguracaoSegurancaWeb {

    private final AutenticacaoWebProvider autenticacaoWebProvider;

    public ConfiguracaoSegurancaWeb(
            AutenticacaoWebProvider autenticacaoWebProvider) {
        this.autenticacaoWebProvider = autenticacaoWebProvider;
    }

    @Bean
    public SecurityFilterChain filtroSeguranca(HttpSecurity http)
            throws Exception {

        http
            .authenticationProvider(autenticacaoWebProvider)
            .authorizeHttpRequests(autorizacao -> autorizacao
                .requestMatchers("/", "/login").permitAll()
                .anyRequest().authenticated()
            )
            .formLogin(formulario -> formulario
                .loginPage("/login")
                .permitAll()
            );

        return http.build();
    }
}