package com.vilagourmet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vilagourmet.dto.AuthDTO.CadastroClienteRequest;
import com.vilagourmet.dto.AuthDTO.LoginClienteRequest;
import com.vilagourmet.dto.AuthDTO.TokenResponse;
import com.vilagourmet.exception.NegocioException;
import com.vilagourmet.model.Cliente;
import com.vilagourmet.model.ContaCliente;
import com.vilagourmet.model.enums.TipoUsuario;
import com.vilagourmet.repository.ClienteRepository;
import com.vilagourmet.repository.ContaClienteRepository;
import com.vilagourmet.repository.ContaFuncionarioRepository;
import com.vilagourmet.repository.FuncionarioRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Login do cliente por e-mail ou CPF (o CPF é único por conta). */
class AuthServiceLoginClienteTest {

    private static final String CPF = "52998224725"; // válido
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final ContaClienteRepository contas = mock(ContaClienteRepository.class);
    private final ClienteRepository clientes = mock(ClienteRepository.class);
    private final JwtService jwt = mock(JwtService.class);
    private final RateLimiterService limiter = mock(RateLimiterService.class);
    private AuthService auth;

    @BeforeEach
    void preparar() {
        when(jwt.gerarTokenAutenticacao(any(), eq(TipoUsuario.CLIENTE))).thenReturn("token");
        auth = new AuthService(contas, clientes, mock(ContaFuncionarioRepository.class), mock(FuncionarioRepository.class),
                encoder, jwt, mock(UsuarioDetalhesService.class), limiter, mock(FuncionarioService.class));
    }

    private Cliente cliente(long id, String nome, String email, String senha, boolean ativa) {
        ContaCliente conta = new ContaCliente();
        conta.setId(id);
        conta.setEmail(email);
        conta.setSenhaHash(encoder.encode(senha));
        conta.setAtiva(ativa);
        Cliente c = new Cliente();
        c.setId(id);
        c.setConta(conta);
        c.setNome(nome);
        c.setCpf(CPF);
        when(contas.findByEmailIgnoreCase(email)).thenReturn(Optional.of(conta));
        when(clientes.findByContaId(id)).thenReturn(Optional.of(c));
        return c;
    }

    private static LoginClienteRequest login(String identificador, String senha) {
        return new LoginClienteRequest(identificador, null, senha);
    }

    @Test
    void entraComCpfComOuSemMascara() {
        Cliente maria = cliente(1, "Maria", "maria@x.com", "senhaforte1", true);
        when(clientes.findByCpf(CPF)).thenReturn(Optional.of(maria));
        assertThat(auth.loginCliente(login("529.982.247-25", "senhaforte1"), "ip").nome()).isEqualTo("Maria");
        assertThat(auth.loginCliente(login("52998224725", "senhaforte1"), "ip").tipo()).isEqualTo("CLIENTE");
        verify(limiter, atLeastOnce()).limpar("login:ip:" + CPF);
    }

    @Test
    void continuaEntrandoPorEmailTambemNoFormatoAntigo() {
        cliente(1, "Maria", "maria@x.com", "senhaforte1", true);
        assertThat(auth.loginCliente(login("maria@x.com", "senhaforte1"), "ip")).isNotNull();
        TokenResponse r = auth.loginCliente(new LoginClienteRequest(null, "maria@x.com", "senhaforte1"), "ip");
        assertThat(r.nome()).isEqualTo("Maria");
    }

    @Test
    void senhaErradaCpfDesconhecidoOuInvalidoDaoOMesmoErro401() {
        Cliente maria = cliente(1, "Maria", "maria@x.com", "senhaforte1", true);
        when(clientes.findByCpf(CPF)).thenReturn(Optional.of(maria));
        for (String id : List.of("529.982.247-25", "111.444.777-35", "123", "naoexiste@x.com")) {
            assertThatThrownBy(() -> auth.loginCliente(login(id, id.equals("naoexiste@x.com") ? "qualquer" : "errada123"), "ip"))
                    .isInstanceOfSatisfying(NegocioException.class, e -> {
                        assertThat(e.getStatus().value()).isEqualTo(401);
                        assertThat(e.getMessage()).isEqualTo("E-mail/CPF ou senha inválidos");
                    });
        }
    }

    @Test
    void contaInativaNaoEntra() {
        Cliente c = cliente(1, "Maria", "maria@x.com", "senhaforte1", false);
        when(clientes.findByCpf(CPF)).thenReturn(Optional.of(c));
        assertThatThrownBy(() -> auth.loginCliente(login(CPF, "senhaforte1"), "ip")).isInstanceOf(NegocioException.class);
    }

    @Test
    void identificadorVazioEhRejeitado() {
        assertThatThrownBy(() -> auth.loginCliente(new LoginClienteRequest(null, null, "x"), "ip"))
                .isInstanceOf(NegocioException.class).hasMessageContaining("e-mail ou o CPF");
    }

    // ------------------------------------------------------------ CPF único no cadastro

    @Test
    void cadastroArmazenaSomenteHashBcrypt() {
        auth.cadastrarCliente(new CadastroClienteRequest("novo@x.com", "Senha@forte1", "Novo", CPF));

        verify(contas).save(argThat(conta -> !"Senha@forte1".equals(conta.getSenhaHash())
                && conta.getSenhaHash().startsWith("$2")
                && encoder.matches("Senha@forte1", conta.getSenhaHash())));
    }

    @Test
    void cadastroRecusaSenhaFracaMesmoSemValidacaoDoController() {
        assertThatThrownBy(() -> auth.cadastrarCliente(
                new CadastroClienteRequest("novo@x.com", "senhafraca", "Novo", CPF)))
                .isInstanceOf(NegocioException.class)
                .hasMessage("Confira os requisitos de segurança da senha");
    }

    @Test
    void cadastroComCpfJaExistenteEhRecusadoComConflito() {
        when(clientes.existsByCpf(CPF)).thenReturn(true);
        assertThatThrownBy(() -> auth.cadastrarCliente(new CadastroClienteRequest("novo@x.com", "Senha@forte1", "Novo", "529.982.247-25")))
                .isInstanceOfSatisfying(NegocioException.class, e -> {
                    assertThat(e.getStatus().value()).isEqualTo(409);
                    assertThat(e.getMessage()).contains("CPF já cadastrado");
                });
    }

    @Test
    void cadastroSimultaneoComMesmoCpfBarradoPelaChaveUnicaDoBanco() {
        when(clientes.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uq_cliente_cpf"));
        assertThatThrownBy(() -> auth.cadastrarCliente(new CadastroClienteRequest("novo@x.com", "Senha@forte1", "Novo", CPF)))
                .isInstanceOfSatisfying(NegocioException.class, e -> assertThat(e.getStatus().value()).isEqualTo(409));
    }

    @Test
    void cadastroComCpfInvalidoNemChegaAoBanco() {
        assertThatThrownBy(() -> auth.cadastrarCliente(new CadastroClienteRequest("novo@x.com", "Senha@forte1", "Novo", "111.111.111-11")))
                .isInstanceOf(NegocioException.class).hasMessageContaining("CPF inválido");
    }
}
