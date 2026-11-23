package br.unoesc.pixelrent.web;

import jakarta.servlet.DispatcherType;

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
                // Encaminhamentos internos do servidor.
                .dispatcherTypeMatchers(
                        DispatcherType.ERROR,
                        DispatcherType.FORWARD
                ).permitAll()

                // Páginas públicas e arquivos estáticos, incluindo o Tabler local.
                .requestMatchers(
                        "/", "/login", "/error",
                        "/css/**", "/js/**", "/images/**",
                        "/vendor/**", "/webjars/**", "/favicon.ico"
                ).permitAll()

                // Cadastros.
                .requestMatchers("/clientes/**")
                    .hasAuthority("PERM_CLIENTES")
                .requestMatchers("/jogos/**")
                    .hasAuthority("PERM_JOGOS")
                .requestMatchers("/funcionarios/**")
                    .hasAuthority("PERM_FUNCIONARIOS")
                .requestMatchers("/generos/**")
                    .hasAuthority("PERM_GENEROS")
                .requestMatchers("/plataformas/**")
                    .hasAuthority("PERM_PLATAFORMAS")
                .requestMatchers("/midias/**")
                    .hasAuthority("PERM_MIDIAS")

                // Operações.
                .requestMatchers("/locacoes/**")
                    .hasAuthority("PERM_LOCACOES")
                .requestMatchers("/devolucoes/**")
                    .hasAuthority("PERM_DEVOLUCOES")
                .requestMatchers("/consultas/**")
                    .hasAuthority("PERM_CONSULTAS")
                .requestMatchers("/manutencao/**")
                    .hasAuthority("PERM_MANUTENCAO")

                // Relatórios.
                .requestMatchers("/relatorios/jogos-mais-alugados/**")
                    .hasAuthority("PERM_RELATORIO_JOGOS_MAIS_ALUGADOS")
                .requestMatchers("/relatorios/faturamento-mensal/**")
                    .hasAuthority("PERM_RELATORIO_FATURAMENTO_MENSAL")
                .requestMatchers("/auditoria/**")
                    .hasAuthority("PERM_AUDITORIA")

                .anyRequest().authenticated()
            )
            .formLogin(formulario -> formulario
                .loginPage("/login")
                .permitAll()
            );

        return http.build();
    }
}
