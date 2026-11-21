# Etapa 2 — Autenticação, sessão e menus por perfil

## O que foi implementado

- `AutenticacaoDao` chama `public.fn_autenticar_funcionario(?, ?)` com `PreparedStatement` e lê somente `id_funcionario`, `nome` e `perfil`.
- `AutenticacaoService` abre uma conexão de leitura com `PerfilConexao.ATENDENTE`, chama o DAO e fecha a conexão ao terminar. A senha recebida como `char[]` é zerada em `finally`.
- `PerfilFuncionario` representa os valores reais do banco: `FUNCIONARIO` e `ADMINISTRADOR`.
- `Sessao` mantém somente o ID, nome e perfil — nunca senha ou hash.
- `ContextoSessao` mantém a sessão atual com `AtomicReference`.
- `PoliticaAcesso` centraliza as permissões do menu e permite repetir a checagem nos services futuros.
- `LoginDialog` usa `JPasswordField` e `SwingWorker`; nenhuma consulta JDBC é executada na thread gráfica.
- `JanelaPrincipal` mostra apenas os menus permitidos para o perfil autenticado. Os itens de módulos futuros ainda exibem uma mensagem de “em desenvolvimento”.
- `App` inicia o login e permite encerrar a sessão e voltar à tela de login.

## Como aplicar no repositório

1. Feche a aplicação, se estiver aberta.
2. Extraia o ZIP da Etapa 2 diretamente sobre a pasta raiz do projeto `pixel-rent-github`, permitindo mesclar as pastas `src` e `docs`.
3. Não substitua `pom.xml`, `config/pixelrent.properties`, `sql/` nem outros arquivos da Etapa 1.
4. No Eclipse, atualize o projeto Maven (*Maven → Update Project*) e confirme que o JDK do projeto é 21.
5. Execute os testes unitários com `mvn clean package`.
6. Execute `src/main/java/br/unoesc/pixelrent/App.java` como *Java Application* no Eclipse.

## Configuração do banco para teste

O arquivo local `config/pixelrent.properties` precisa estar configurado. Para o teste de desenvolvimento:

```properties
db.name=pixelrent_restore
db.atendente.user=usr_atendente_teste
db.atendente.password=SENHA_LOCAL_DO_ROLE
```

O arquivo `config/pixelrent.properties` é local e não deve ser enviado ao GitHub. A senha acima é apenas um marcador; use a senha que já está configurada na sua máquina. A conexão inicial de autenticação é sempre a do atendente, pois esse grupo tem `EXECUTE` na função. Depois de validar o perfil do funcionário, a `Sessao` indica qual `PerfilConexao` deverá ser usado nas próximas operações.

O login do funcionário (por exemplo, Julia) é diferente do usuário/senha PostgreSQL configurados em `db.atendente.*`.

## Roteiro de validação manual

1. Com `db.name=pixelrent_restore`, abra `App.java`.
2. Entre com um funcionário ativo cuja senha já esteja armazenada em bcrypt nessa base.
3. Confirme que o nome e o perfil aparecem na janela principal.
4. Confirme que o perfil `FUNCIONARIO` não mostra Funcionários, Gêneros, Plataformas, Mídias, Manutenção, Faturamento mensal nem Auditoria.
5. Tente uma senha incorreta: a tela deve informar apenas “Login ou senha inválidos”, sem revelar se o login existe ou se o funcionário está inativo.
6. Use “Sessão → Encerrar sessão” e confirme que a tela de login reaparece.
7. Um perfil `ADMINISTRADOR` deve visualizar os menus administrativos. Só faça esse teste com uma conta que já esteja ativa e com hash bcrypt na base de teste.

## Limites conhecidos desta etapa

- Os itens dos menus são pontos de entrada/placeholder; as telas reais serão implementadas nas próximas etapas.
- A matriz de permissões na UI não substitui os GRANTs do PostgreSQL. A proteção definitiva continua sendo responsabilidade do banco, e os services futuros também deverão chamar `PoliticaAcesso.exigir(...)`.
- O hash da conta de Marcos não foi alterado por este pacote. Essa alteração exige executar uma atualização SQL autorizada, separada da geração do Java.
- A conexão inicial usa o role `ATENDENTE` para chamar a função; o `PerfilConexao` da sessão só será usado nas operações posteriores, a partir da etapa 3.
- `char[]` é apagado após o uso, mas o driver JDBC recebe o parâmetro PostgreSQL `TEXT` como `String`; a JVM/driver não oferecem garantia de apagamento imediato dessa cópia interna.
- A política atual mantém faturamento mensal restrito à interface de administrador, conforme a decisão de projeto. O GRANT atualmente existente para atendente no banco ainda é uma permissão separada a revisar.
