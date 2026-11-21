
package br.unoesc.pixelrent.web;

import br.unoesc.pixelrent.exception.AcessoNegadoException;
import br.unoesc.pixelrent.exception.DadosInvalidosException;
import br.unoesc.pixelrent.exception.FalhaTecnicaException;
import br.unoesc.pixelrent.security.Permissao;
import br.unoesc.pixelrent.security.PoliticaAcesso;
import br.unoesc.pixelrent.security.Sessao;
import br.unoesc.pixelrent.service.AutenticacaoService;

import java.util.ArrayList;
import java.util.List;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

@Component
public final class AutenticacaoWebProvider implements AuthenticationProvider {

    private final AutenticacaoService autenticacaoService;
    private final PoliticaAcesso politicaAcesso;

    public AutenticacaoWebProvider(
            AutenticacaoService autenticacaoService,
            PoliticaAcesso politicaAcesso) {

        this.autenticacaoService = autenticacaoService;
        this.politicaAcesso = politicaAcesso;
    }

    @Override
    public Authentication authenticate(Authentication authentication)
            throws AuthenticationException {

        Object credenciais = authentication.getCredentials();

        if (!(credenciais instanceof String senhaTexto)) {
            throw new BadCredentialsException("Login ou senha inválidos.");
        }

        char[] senha = senhaTexto.toCharArray();

        try {
            Sessao sessao = autenticacaoService.autenticar(
                    authentication.getName(), senha);

            List<GrantedAuthority> autoridades = new ArrayList<>();

            autoridades.add(new SimpleGrantedAuthority(
                    "ROLE_" + sessao.perfil().name()));

            for (Permissao permissao :
                    politicaAcesso.permissoesPara(sessao.perfil())) {

                autoridades.add(new SimpleGrantedAuthority(
                        "PERM_" + permissao.name()));
            }

            return UsernamePasswordAuthenticationToken.authenticated(
                    sessao, null, autoridades);

        } catch (AcessoNegadoException | DadosInvalidosException e) {
            throw new BadCredentialsException("Login ou senha inválidos.");

        } catch (FalhaTecnicaException e) {
            throw new AuthenticationServiceException(
                    "Não foi possível concluir a autenticação.", e);
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class
                .isAssignableFrom(authentication);
    }
}

