package br.unoesc.pixelrent.web;

import br.unoesc.pixelrent.config.ConfiguracaoBanco;
import br.unoesc.pixelrent.config.ConfiguracaoLoader;
import br.unoesc.pixelrent.dao.AutenticacaoDao;
import br.unoesc.pixelrent.dao.AuditoriaDao;
import br.unoesc.pixelrent.dao.ClienteDao;
import br.unoesc.pixelrent.dao.GeneroDao;
import br.unoesc.pixelrent.dao.FuncionarioDao;
import br.unoesc.pixelrent.dao.JogoDao;
import br.unoesc.pixelrent.dao.MidiaDao;
import br.unoesc.pixelrent.dao.OperacaoDao;
import br.unoesc.pixelrent.dao.PlataformaDao;
import br.unoesc.pixelrent.dao.RelatorioDao;
import br.unoesc.pixelrent.db.ConnectionProvider;
import br.unoesc.pixelrent.security.PoliticaAcesso;
import br.unoesc.pixelrent.service.AutenticacaoService;
import br.unoesc.pixelrent.service.AuditoriaService;
import br.unoesc.pixelrent.service.ClienteService;
import br.unoesc.pixelrent.service.GeneroService;
import br.unoesc.pixelrent.service.FuncionarioService;
import br.unoesc.pixelrent.service.JogoService;
import br.unoesc.pixelrent.service.MidiaService;
import br.unoesc.pixelrent.service.OperacaoService;
import br.unoesc.pixelrent.service.PlataformaService;
import br.unoesc.pixelrent.service.RelatorioService;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConfiguracaoDependenciasWeb {
    @Bean
    public ConfiguracaoBanco configuracaoBanco() { return ConfiguracaoLoader.carregar(); }

    @Bean
    public ConnectionProvider connectionProvider(ConfiguracaoBanco configuracaoBanco) {
        return new ConnectionProvider(configuracaoBanco);
    }

    @Bean
    public AutenticacaoDao autenticacaoDao() { return new AutenticacaoDao(); }

    @Bean
    public AutenticacaoService autenticacaoService(ConnectionProvider provider, AutenticacaoDao dao) {
        return new AutenticacaoService(provider, dao);
    }

    @Bean
    public ClienteDao clienteDao() { return new ClienteDao(); }

    @Bean
    public ClienteService clienteService(ConnectionProvider provider, ClienteDao dao, PoliticaAcesso politica) {
        return new ClienteService(provider, dao, politica);
    }

    @Bean
    public GeneroDao generoDao() { return new GeneroDao(); }

    @Bean
    public GeneroService generoService(ConnectionProvider provider, GeneroDao dao, PoliticaAcesso politica) {
        return new GeneroService(provider, dao, politica);
    }

    @Bean
    public PlataformaDao plataformaDao() { return new PlataformaDao(); }

    @Bean
    public PlataformaService plataformaService(ConnectionProvider provider, PlataformaDao dao, PoliticaAcesso politica) {
        return new PlataformaService(provider, dao, politica);
    }

    @Bean
    public JogoDao jogoDao() { return new JogoDao(); }

    @Bean
    public JogoService jogoService(ConnectionProvider provider, JogoDao dao, PoliticaAcesso politica) {
        return new JogoService(provider, dao, politica);
    }

    @Bean
    public MidiaDao midiaDao() { return new MidiaDao(); }

    @Bean
    public MidiaService midiaService(ConnectionProvider provider, MidiaDao dao, PoliticaAcesso politica) {
        return new MidiaService(provider, dao, politica);
    }

    @Bean
    public OperacaoDao operacaoDao() { return new OperacaoDao(); }

    @Bean
    public OperacaoService operacaoService(ConnectionProvider provider, OperacaoDao dao, PoliticaAcesso politica) {
        return new OperacaoService(provider, dao, politica);
    }


    @Bean
    public FuncionarioDao funcionarioDao() { return new FuncionarioDao(); }

    @Bean
    public FuncionarioService funcionarioService(ConnectionProvider provider, FuncionarioDao dao, PoliticaAcesso politica) {
        return new FuncionarioService(provider, dao, politica);
    }

    @Bean
    public RelatorioDao relatorioDao() { return new RelatorioDao(); }

    @Bean
    public RelatorioService relatorioService(ConnectionProvider provider, RelatorioDao dao, PoliticaAcesso politica) {
        return new RelatorioService(provider, dao, politica);
    }

    @Bean
    public AuditoriaDao auditoriaDao() { return new AuditoriaDao(); }

    @Bean
    public AuditoriaService auditoriaService(ConnectionProvider provider, AuditoriaDao dao, PoliticaAcesso politica) {
        return new AuditoriaService(provider, dao, politica);
    }

    @Bean
    public PoliticaAcesso politicaAcesso() { return new PoliticaAcesso(); }
}
