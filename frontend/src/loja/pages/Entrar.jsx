import { useState } from 'react'
import { Navigate, useNavigate, useSearchParams } from 'react-router-dom'
import { Campo, Erro, useAcao } from '../../ui.jsx'
import CampoSenha, { senhaValida } from '../../components/CampoSenha.jsx'
import { useCliente } from '../cliente.jsx'

export default function Entrar() {
  const { cliente, entrar, cadastrar } = useCliente()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const volta = params.get('volta') ?? '/conta'
  const [cadastro, setCadastro] = useState(params.get('modo') === 'cadastro')
  const [f, setF] = useState({ nome: '', cpf: '', email: '', login: '', senha: '' })
  const [tentouEnviar, setTentouEnviar] = useState(false)
  const { erro, ocupado, executar } = useAcao()
  const campo = (k) => ({ value: f[k], onChange: (e) => setF({ ...f, [k]: e.target.value }) })

  if (cliente) return <Navigate to={volta} replace />

  const enviar = (e) => {
    e.preventDefault()
    setTentouEnviar(true)
    if (!e.currentTarget.checkValidity()) { e.currentTarget.reportValidity(); return }
    if (cadastro && !senhaValida(f.senha)) return
    executar(async () => {
      if (cadastro) await cadastrar({ nome: f.nome.trim(), cpf: f.cpf, email: f.email.trim(), senha: f.senha })
      else await entrar(f.login.trim(), f.senha)
      navigate(volta, { replace: true })
    })
  }

  return (
    <div className="lj-pagina estreita">
      <form className="lj-cartao" onSubmit={enviar} noValidate>
        <div className="lj-abas" role="tablist">
          <button type="button" role="tab" aria-selected={!cadastro} className={!cadastro ? 'ativo' : ''} onClick={() => setCadastro(false)}>Entrar</button>
          <button type="button" role="tab" aria-selected={cadastro} className={cadastro ? 'ativo' : ''} onClick={() => setCadastro(true)}>Criar conta</button>
        </div>
        <p className="mudo">
          {cadastro ? 'Com uma conta você salva endereços, pede entrega e acompanha o histórico.' : 'Entre com seu e-mail ou CPF para pedir entrega e ver seus pedidos.'}
        </p>
        {cadastro && (
          <>
            <Campo label="Nome completo"><input {...campo('nome')} required maxLength={150} autoComplete="name" /></Campo>
            <Campo label="CPF"><input {...campo('cpf')} required inputMode="numeric" placeholder="000.000.000-00" autoComplete="off" /></Campo>
          </>
        )}
        {cadastro ? (
          <Campo label="E-mail"><input type="email" {...campo('email')} required autoComplete="email" /></Campo>
        ) : (
          <Campo label="E-mail ou CPF"><input {...campo('login')} required autoComplete="username" autoFocus placeholder="voce@email.com ou 000.000.000-00" /></Campo>
        )}
        <CampoSenha value={f.senha} onChange={(e) => setF({ ...f, senha: e.target.value })} required politica={cadastro}
          mostrarErro={cadastro && tentouEnviar} autoComplete={cadastro ? 'new-password' : 'current-password'} />
        <Erro erro={erro} />
        <button className="btn grande" disabled={ocupado}>{ocupado ? 'Aguarde…' : cadastro ? 'Criar conta' : 'Entrar'}</button>
        <p className="mudo pequeno lj-centro">Para retirada ou mesa não é preciso ter conta.</p>
      </form>
    </div>
  )
}
