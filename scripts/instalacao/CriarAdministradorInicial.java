import java.io.Console;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.Arrays;

/**
 * Cria o primeiro funcionario administrador do Pixel Rent.
 *
 * Executar localmente pelo CMD, com o driver PostgreSQL no classpath.
 * A senha do funcionario e recebida sem eco e armazenada usando bcrypt
 * via pgcrypto, com o mesmo custo utilizado por FuncionarioDao.
 */
public final class CriarAdministradorInicial {

    private static final String SQL_INSERIR = """
            INSERT INTO public.funcionario
                (nome, cpf, telefone, email, login, senha_hash, perfil, ativo)
            VALUES (?, ?, ?, ?, ?, public.crypt(?, public.gen_salt('bf', 12)),
                    'ADMINISTRADOR', TRUE)
            RETURNING id_funcionario
            """;

    private CriarAdministradorInicial() {
    }

    public static void main(String[] args) {
        Console console = System.console();
        if (console == null) {
            System.err.println("Execute este utilitario diretamente em um CMD interativo.");
            System.exit(2);
        }
        if (args.length != 4) {
            System.err.println("Uso: java CriarAdministradorInicial <host> <porta> <banco> <usuario_postgresql>");
            System.exit(2);
        }

        String host = args[0];
        String port = args[1];
        String database = args[2];
        String dbUser = args[3];
        String url = "jdbc:postgresql://" + host + ":" + port + "/" + database;

        char[] dbPassword = null;
        char[] password = null;
        char[] confirmation = null;

        try {
            String nome = required(console.readLine("Nome completo: "), "Nome");
            if (nome.length() > 100) {
                throw new IllegalArgumentException("O nome deve possuir no maximo 100 caracteres.");
            }

            String cpf = normalizeCpf(required(console.readLine("CPF (somente numeros ou formatado): "), "CPF"));
            validateCpf(cpf);

            String telefone = optional(console.readLine("Telefone (opcional): "));
            if (telefone != null && telefone.length() > 20) {
                throw new IllegalArgumentException("O telefone deve possuir no maximo 20 caracteres.");
            }

            String email = optional(console.readLine("E-mail (opcional): "));
            if (email != null && (email.length() > 100
                    || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))) {
                throw new IllegalArgumentException("Informe um endereco de e-mail valido (ate 100 caracteres).");
            }

            String login = required(console.readLine("Login (3 a 50 caracteres): "), "Login").trim();
            if (!login.matches("[A-Za-z0-9._-]{3,50}")) {
                throw new IllegalArgumentException(
                        "O login deve ter de 3 a 50 caracteres: letras, numeros, ponto, hifen ou sublinhado.");
            }

            password = console.readPassword("Senha do administrador (8 a 72 bytes UTF-8): ");
            confirmation = console.readPassword("Confirme a senha: ");
            validatePassword(password, confirmation);

            dbPassword = console.readPassword("Senha do usuario PostgreSQL %s: ", dbUser);
            String dbPasswordText = new String(dbPassword);

            try (Connection connection = DriverManager.getConnection(url, dbUser, dbPasswordText)) {
                connection.setAutoCommit(false);
                try {
                    // Impede duas inicializacoes concorrentes de criarem o primeiro admin.
                    try (Statement statement = connection.createStatement()) {
                        statement.execute("LOCK TABLE public.funcionario IN EXCLUSIVE MODE");
                    }

                    try (PreparedStatement count = connection.prepareStatement("""
                            SELECT COUNT(*)
                              FROM public.funcionario
                             WHERE perfil = 'ADMINISTRADOR'
                               AND ativo = TRUE
                            """);
                         ResultSet result = count.executeQuery()) {
                        result.next();
                        if (result.getLong(1) > 0) {
                            throw new IllegalStateException(
                                    "Ja existe um administrador ativo. Nenhum funcionario foi inserido.");
                        }
                    }

                    try (PreparedStatement insert = connection.prepareStatement(SQL_INSERIR)) {
                        insert.setString(1, nome.trim());
                        insert.setString(2, cpf);
                        if (telefone == null) insert.setNull(3, Types.VARCHAR);
                        else insert.setString(3, telefone);
                        if (email == null) insert.setNull(4, Types.VARCHAR);
                        else insert.setString(4, email);
                        insert.setString(5, login);
                        insert.setString(6, new String(password));

                        try (ResultSet result = insert.executeQuery()) {
                            if (!result.next()) {
                                throw new SQLException("O banco nao retornou o ID do administrador criado.");
                            }
                            int id = result.getInt(1);
                            connection.commit();
                            System.out.println("Administrador inicial criado com sucesso. ID: " + id);
                            System.out.println("O hash bcrypt foi gerado pelo PostgreSQL (pgcrypto).");
                        }
                    }
                } catch (Exception ex) {
                    try {
                        connection.rollback();
                    } catch (SQLException rollbackError) {
                        ex.addSuppressed(rollbackError);
                    }
                    throw ex;
                }
            }
        } catch (Exception ex) {
            System.err.println("Falha ao criar administrador: " + ex.getMessage());
            throw new IllegalStateException("Cadastro inicial nao concluido.");
        } finally {
            if (dbPassword != null) Arrays.fill(dbPassword, '\0');
            if (password != null) Arrays.fill(password, '\0');
            if (confirmation != null) Arrays.fill(confirmation, '\0');
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " e obrigatorio.");
        }
        return value.trim();
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String normalizeCpf(String value) {
        return value.replaceAll("\\D", "");
    }

    private static void validateCpf(String cpf) {
        if (cpf.length() != 11 || cpf.chars().distinct().count() == 1) {
            throw new IllegalArgumentException("Informe um CPF valido.");
        }
        int first = calculateDigit(cpf.substring(0, 9), 10);
        int second = calculateDigit(cpf.substring(0, 9) + first, 11);
        if (cpf.charAt(9) - '0' != first || cpf.charAt(10) - '0' != second) {
            throw new IllegalArgumentException("Informe um CPF valido.");
        }
    }

    private static int calculateDigit(String base, int initialWeight) {
        int sum = 0;
        for (int i = 0; i < base.length(); i++) {
            sum += (base.charAt(i) - '0') * (initialWeight - i);
        }
        int remainder = sum % 11;
        return remainder < 2 ? 0 : 11 - remainder;
    }

    private static void validatePassword(char[] password, char[] confirmation) {
        if (password == null || confirmation == null || !Arrays.equals(password, confirmation)) {
            throw new IllegalArgumentException("A senha e a confirmacao nao coincidem.");
        }
        if (password.length < 8) {
            throw new IllegalArgumentException("A senha deve possuir ao menos 8 caracteres.");
        }
        byte[] encoded = new String(password).getBytes(StandardCharsets.UTF_8);
        try {
            if (encoded.length > 72) {
                throw new IllegalArgumentException("A senha deve possuir no maximo 72 bytes em UTF-8.");
            }
        } finally {
            Arrays.fill(encoded, (byte) 0);
        }
    }
}
