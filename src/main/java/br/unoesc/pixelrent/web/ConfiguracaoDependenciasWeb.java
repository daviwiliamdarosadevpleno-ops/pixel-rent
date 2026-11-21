package br.unoesc.pixelrent.web;

import br.unoesc.pixelrent.config.ConfiguracaoBanco;
import br.unoesc.pixelrent.config.ConfiguracaoLoader;
import br.unoesc.pixelrent.dao.AutenticacaoDao;
import br.unoesc.pixelrent.db.ConnectionProvider;
import br.unoesc.pixelrent.security.PoliticaAcesso;
import br.unoesc.pixelrent.service.AutenticacaoService;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConfiguracaoDependenciasWeb {

    @Bean
    public ConfiguracaoBanco configuracaoBanco() {
        return ConfiguracaoLoader.carregar();
    }

    @Bean
    public ConnectionProvider connectionProvider(
            ConfiguracaoBanco configuracaoBanco) {

        return new ConnectionProvider(configuracaoBanco);
    }

    @Bean
    public AutenticacaoDao autenticacaoDao() {
        return new AutenticacaoDao();
    }

    @Bean
    public AutenticacaoService autenticacaoService(
            ConnectionProvider connectionProvider,
            AutenticacaoDao autenticacaoDao) {

        return new AutenticacaoService(connectionProvider, autenticacaoDao);
    }

    @Bean
    public PoliticaAcesso politicaAcesso() {
        return new PoliticaAcesso();
    }
}