import { useState } from 'react'
import { get, post } from '../api.js'
import { useAuth } from '../auth.jsx'
import Logo from '../components/Logo.jsx'
import { Campo, Erro, useAcao, useCarga } from '../ui.jsx'

function PrimeiroAcesso({ onCriado }) {
  const [nome, setNome] = useState('')
  const [email, setEmail] = useState('')
  const [senha, setSenha] = useState('')
  const [confirmacao, setConfirmacao] = useState('')
  const { erro, ocupado, executar } = useAcao()
  const diferente = confirmacao !== '' && confirmacao !== senha

  const criar = (e) => {
    e.preventDefault()
    if (senha !== confirmacao) return
    executar(async () => {
      await post('/api/auth/funcionario/primeiro-acesso', { nome, email, senha })
      await onCriado(email, senha)
    })
  }

  return (
    <form className="login-cartao" onSubmit={criar}>
      <Logo />
      <p className="mudo">Primeiro acesso: crie a conta do administrador</p>
      <Campo label="Nome"><input value={nome} onChange={(e) => setNome(e.target.value)} autoFocus required /></Campo>
      <Campo label="E-mail"><input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required /></Campo>
      <Campo label="Senha" dica="Mínimo de 8 caracteres.">
        <input type="password" minLength={8} value={senha} onChange={(e) => setSenha(e.target.value)} required autoComplete="new-password" />
      </Campo>
      <Campo label="Confirmar senha" dica={diferente ? 'As senhas não conferem.' : undefined}>
        <input type="password" value={confirmacao} onChange={(e) => setConfirmacao(e.target.value)} required autoComplete="new-password" />
      </Campo>
      <Erro erro={erro} />
      <button className="btn" disabled={ocupado || diferente}>{ocupado ? 'Criando…' : 'Criar conta e entrar'}</button>
    </form>
  )
}

export default function Login() {
  const { entrar } = useAuth()
  const [email, setEmail] = useState('')
  const [senha, setSenha] = useState('')
  const { erro, ocupado, executar } = useAcao()
  const inicial = useCarga(() => get('/api/auth/funcionario/configuracao-inicial'), [])

  const enviar = (e) => {
    e.preventDefault()
    executar(() => entrar(email, senha))
  }

  return (
    <div className="login">
      {inicial.carregando ? null : inicial.dados?.primeiroAcessoNecessario ? (
        <PrimeiroAcesso onCriado={entrar} />
      ) : (
        <form className="login-cartao" onSubmit={enviar}>
          <Logo />
          <p className="mudo">Acesso da equipe</p>
          <Campo label="E-mail">
            <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoFocus required />
          </Campo>
          <Campo label="Senha">
            <input type="password" value={senha} onChange={(e) => setSenha(e.target.value)} required />
          </Campo>
          <Erro erro={inicial.erro} />
          <Erro erro={erro} />
          <button className="btn" disabled={ocupado}>{ocupado ? 'Entrando…' : 'Entrar'}</button>
          <p className="mudo pequeno">Novas contas da equipe são criadas por um administrador, em “Funcionários e perfis”.</p>
        </form>
      )}
    </div>
  )
}
