package br.unoesc.pixelrent.service;

import br.unoesc.pixelrent.dao.MidiaDao;
import br.unoesc.pixelrent.db.ConnectionProvider;
import br.unoesc.pixelrent.exception.ConfiguracaoException;
import br.unoesc.pixelrent.exception.DadosInvalidosException;
import br.unoesc.pixelrent.exception.FalhaTecnicaException;
import br.unoesc.pixelrent.model.Midia;
import br.unoesc.pixelrent.model.OpcaoJogoPlataforma;
import br.unoesc.pixelrent.security.Permissao;
import br.unoesc.pixelrent.security.PoliticaAcesso;
import br.unoesc.pixelrent.security.Sessao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Regras de negócio de mídias; seu acesso é limitado ao perfil com MIDIAS. */
public final class MidiaService {
    private static final List<String> ESTADOS = List.of("NOVO", "BOM", "REGULAR", "RUIM");
    private final ConnectionProvider provider;
    private final MidiaDao dao;
    private final PoliticaAcesso politica;

    public MidiaService(ConnectionProvider provider, MidiaDao dao, PoliticaAcesso politica) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.dao = Objects.requireNonNull(dao, "dao");
        this.politica = Objects.requireNonNull(politica, "politica");
    }

    public List<Midia> listar(Sessao sessao, String pesquisa) {
        exigir(sessao);
        try (Connection conexao = provider.abrirSomenteLeitura(sessao.perfilConexao())) {
            return dao.listar(conexao, pesquisa);
        } catch (SQLException e) {
            throw traduzir(e, "consultar mídias", false);
        } catch (ConfiguracaoException e) {
            throw new FalhaTecnicaException("Não foi possível configurar o acesso às mídias.", e);
        }
    }

    public Optional<Midia> buscarPorId(Sessao sessao, int id) {
        exigir(sessao);
        validarId(id);
        try (Connection conexao = provider.abrirSomenteLeitura(sessao.perfilConexao())) {
            return dao.buscarPorId(conexao, id);
        } catch (SQLException e) {
            throw traduzir(e, "consultar a mídia", false);
        } catch (ConfiguracaoException e) {
            throw new FalhaTecnicaException("Não foi possível configurar o acesso às mídias.", e);
        }
    }

    public List<OpcaoJogoPlataforma> listarOpcoes(Sessao sessao) {
        exigir(sessao);
        try (Connection conexao = provider.abrirSomenteLeitura(sessao.perfilConexao())) {
            return dao.listarOpcoes(conexao);
        } catch (SQLException e) {
            throw traduzir(e, "consultar jogos e plataformas", false);
        } catch (ConfiguracaoException e) {
            throw new FalhaTecnicaException("Não foi possível configurar o acesso às mídias.", e);
        }
    }

    public int cadastrar(Sessao sessao, String chaveJogoPlataforma, String tipo,
                         BigDecimal valorDiaria, String codigoBarras,
                         String estadoConservacao, String codigoLicenca, LocalDate validade) {
        exigir(sessao);
        Par jogoPlataforma = interpretarChave(chaveJogoPlataforma);
        Dados dados = validar(tipo, valorDiaria, codigoBarras, estadoConservacao, codigoLicenca, validade);
        try (Connection conexao = provider.abrir(sessao.perfilConexao())) {
            conexao.setAutoCommit(false);
            try {
                int id = dao.inserirBase(conexao, jogoPlataforma.idJogo(),
                        jogoPlataforma.idPlataforma(), dados.tipo(), dados.valorDiaria());
                if ("FISICA".equals(dados.tipo())) {
                    dao.inserirFisica(conexao, id, dados.codigoBarras(), dados.estado());
                } else {
                    dao.inserirDigital(conexao, id, dados.codigoLicenca(), dados.validade());
                }
                conexao.commit();
                return id;
            } catch (SQLException | RuntimeException e) {
                rollback(conexao, e);
                throw e;
            }
        } catch (SQLException e) {
            throw traduzir(e, "cadastrar a mídia", false);
        } catch (ConfiguracaoException e) {
            throw new FalhaTecnicaException("Não foi possível configurar o acesso às mídias.", e);
        }
    }

    public boolean atualizar(Sessao sessao, int id, String tipo, BigDecimal valorDiaria,
                             String codigoBarras, String estadoConservacao,
                             String codigoLicenca, LocalDate validade) {
        exigir(sessao);
        validarId(id);
        Dados dados = validar(tipo, valorDiaria, codigoBarras, estadoConservacao, codigoLicenca, validade);
        try (Connection conexao = provider.abrir(sessao.perfilConexao())) {
            conexao.setAutoCommit(false);
            try {
                Optional<Midia> existente = dao.buscarPorId(conexao, id);
                if (existente.isEmpty()) {
                    conexao.rollback();
                    return false;
                }
                Midia atual = existente.get();
                if (!"DISPONIVEL".equals(atual.status())) {
                    throw new DadosInvalidosException("Somente mídias disponíveis podem ser editadas.");
                }
                if (!atual.tipo().equals(dados.tipo())) {
                    throw new DadosInvalidosException("O tipo da mídia não pode ser alterado após o cadastro.");
                }
                if (!dao.atualizarBaseDisponivel(conexao, id, dados.valorDiaria())) {
                    throw new DadosInvalidosException("A mídia deixou de estar disponível e não pode ser editada.");
                }
                if ("FISICA".equals(dados.tipo())) {
                    dao.atualizarFisica(conexao, id, dados.codigoBarras(), dados.estado());
                } else {
                    dao.atualizarDigital(conexao, id, dados.codigoLicenca(), dados.validade());
                }
                conexao.commit();
                return true;
            } catch (SQLException | RuntimeException e) {
                rollback(conexao, e);
                throw e;
            }
        } catch (DadosInvalidosException e) {
            throw e;
        } catch (SQLException e) {
            throw traduzir(e, "atualizar a mídia", false);
        } catch (ConfiguracaoException e) {
            throw new FalhaTecnicaException("Não foi possível configurar o acesso às mídias.", e);
        }
    }

    public boolean excluir(Sessao sessao, int id) {
        exigir(sessao);
        validarId(id);
        try (Connection conexao = provider.abrir(sessao.perfilConexao())) {
            return dao.excluirDisponivel(conexao, id);
        } catch (SQLException e) {
            throw traduzir(e, "excluir a mídia", true);
        } catch (ConfiguracaoException e) {
            throw new FalhaTecnicaException("Não foi possível configurar o acesso às mídias.", e);
        }
    }

    private void exigir(Sessao sessao) { politica.exigir(sessao, Permissao.MIDIAS); }

    private static Par interpretarChave(String chave) {
        if (chave == null || !chave.matches("\\d+:\\d+")) {
            throw new DadosInvalidosException("Selecione uma combinação válida de jogo e plataforma.");
        }
        try {
            String[] partes = chave.split(":");
            int jogo = Integer.parseInt(partes[0]);
            int plataforma = Integer.parseInt(partes[1]);
            if (jogo <= 0 || plataforma <= 0) throw new NumberFormatException();
            return new Par(jogo, plataforma);
        } catch (NumberFormatException e) {
            throw new DadosInvalidosException("Selecione uma combinação válida de jogo e plataforma.");
        }
    }

    private static Dados validar(String tipo, BigDecimal diaria, String codigoBarras,
                                 String estado, String codigoLicenca, LocalDate validade) {
        String tipoOk = tipo == null ? "" : tipo.trim().toUpperCase();
        if (!List.of("FISICA", "DIGITAL").contains(tipoOk)) {
            throw new DadosInvalidosException("Selecione o tipo físico ou digital.");
        }
        if (diaria == null || diaria.compareTo(BigDecimal.ZERO) <= 0) {
            throw new DadosInvalidosException("Informe um valor de diária maior que zero.");
        }
        if ("FISICA".equals(tipoOk)) {
            String codigo = textoObrigatorio(codigoBarras, 30, "código de barras");
            String estadoOk = estado == null ? "" : estado.trim().toUpperCase();
            if (!ESTADOS.contains(estadoOk)) throw new DadosInvalidosException("Selecione um estado de conservação válido.");
            return new Dados(tipoOk, diaria, codigo, estadoOk, null, null);
        }
        String licenca = textoObrigatorio(codigoLicenca, 40, "código da licença");
        if (validade == null) throw new DadosInvalidosException("Informe a validade da licença digital.");
        return new Dados(tipoOk, diaria, null, null, licenca, validade);
    }

    private static String textoObrigatorio(String valor, int limite, String campo) {
        if (valor == null || valor.isBlank()) throw new DadosInvalidosException("Informe o " + campo + ".");
        String texto = valor.trim();
        if (texto.length() > limite) throw new DadosInvalidosException("O " + campo + " deve possuir no máximo " + limite + " caracteres.");
        return texto;
    }

    private static void validarId(int id) {
        if (id <= 0) throw new DadosInvalidosException("Identificador de mídia inválido.");
    }

    private static RuntimeException traduzir(SQLException e, String operacao, boolean exclusao) {
        if ("23505".equals(e.getSQLState())) return new DadosInvalidosException("O código de barras ou de licença já está cadastrado.");
        if ("23503".equals(e.getSQLState())) {
            return new DadosInvalidosException(exclusao
                    ? "Não é possível excluir esta mídia porque ela possui histórico de locação."
                    : "A combinação de jogo e plataforma não existe no catálogo.");
        }
        if ("23514".equals(e.getSQLState())) return new DadosInvalidosException("Um dos valores informados não atende às regras do banco de dados.");
        return new FalhaTecnicaException("Não foi possível " + operacao + ".", e);
    }

    private static void rollback(Connection conexao, Throwable causa) {
        try { conexao.rollback(); } catch (SQLException erroRollback) { causa.addSuppressed(erroRollback); }
    }

    private record Par(int idJogo, int idPlataforma) { }
    private record Dados(String tipo, BigDecimal valorDiaria, String codigoBarras,
                         String estado, String codigoLicenca, LocalDate validade) { }
}
