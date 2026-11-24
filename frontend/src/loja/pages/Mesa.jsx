import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { post } from '../../api.js'
import Icon from '../../components/Icon.jsx'
import { Campo, Erro, useAcao } from '../../ui.jsx'
import { useCarrinho } from '../carrinho.jsx'
import { useCliente } from '../cliente.jsx'
import { useLoja } from '../lojaStatus.jsx'

function ContaAtiva({ mesa, esquecer }) {
  const navigate = useNavigate()
  const { setModalidade } = useCarrinho()
  const { aberta } = useLoja()
  const emFechamento = mesa.estado === 'EM_FECHAMENTO'
  const pedir = () => { setModalidade('MESA'); navigate('/cardapio') }

  return (
    <div className="lj-cartao lj-centro">
      <span className="lj-selo"><Icon nome="store" tamanho={28} /></span>
      <h2>{emFechamento ? 'Sua conta está em fechamento' : 'Sua conta está aberta'}</h2>
      <small className="mudo">Código da conta</small>
      <strong className="lj-codigo">{mesa.codigo}</strong>
      <p>Responsável: <strong>{mesa.nome}</strong>. {emFechamento
        ? 'A conta já está sendo fechada e não aceita novos pedidos.'
        : 'Tudo o que pedir vai para esta conta e o pagamento é feito no fechamento.'}</p>
      <div className="lj-acoes centro">
        <button className="btn grande" onClick={pedir} disabled={!aberta || emFechamento}>Fazer um pedido</button>
        {esquecer && <button className="btn contorno" onClick={esquecer}>Não usar mais esta conta neste aparelho</button>}
      </div>
    </div>
  )
}

function MesaCliente() {
  const { mesa, carregandoMesa, erroMesa, recarregarMesa } = useCarrinho()
  const { aberta } = useLoja()
  const { erro, ocupado, executar } = useAcao()

  const abrir = () => executar(async () => {
    try {
      await post('/api/cliente/mesa')
    } finally {
      await recarregarMesa()
    }
  })

  if (mesa) return <ContaAtiva mesa={mesa} />
  if (carregandoMesa) return <p className="mudo" role="status">Consultando sua mesa…</p>

  return (
    <div className="lj-cartao">
      <Erro erro={erroMesa || erro} />
      {erroMesa ? (
        <button type="button" className="btn grande" onClick={recarregarMesa}>Tentar novamente</button>
      ) : (
        <button type="button" className="btn grande" onClick={abrir} disabled={!aberta || ocupado}>
          {ocupado ? 'Abrindo mesa…' : 'Pedir abertura de mesa'}
        </button>
      )}
    </div>
  )
}

function MesaVisitante() {
  const { mesa, setMesa, setModalidade } = useCarrinho()
  const { aberta } = useLoja()
  const [modo, setModo] = useState('abrir')
  const [nome, setNome] = useState('')
  const [cpf, setCpf] = useState('')
  const { erro, ocupado, executar, limpar } = useAcao()

  const abrir = (e) => {
    e.preventDefault()
    executar(async () => {
      const conta = await post('/api/public/mesas', { nome: nome.trim(), cpf })
      setMesa({ codigo: conta.codigoConta, cpf, nome: conta.nomeResponsavel ?? nome.trim(), estado: conta.estado })
      setModalidade('MESA')
    })
  }

  const acessar = (e) => {
    e.preventDefault()
    executar(async () => {
      const conta = await post('/api/public/mesas/acesso', { cpf })
      setMesa({ codigo: conta.codigoConta, cpf, nome: conta.nomeResponsavel, estado: conta.estado })
      setModalidade('MESA')
    })
  }

  const trocarModo = (novoModo) => { setModo(novoModo); limpar() }

  const esquecer = () => { setMesa(null); setModalidade(null) }

  return mesa ? <ContaAtiva mesa={mesa} esquecer={esquecer} /> : (
    <form className="lj-cartao" onSubmit={modo === 'abrir' ? abrir : acessar}>
      <div className="lj-abas" role="tablist">
        <button type="button" role="tab" aria-selected={modo === 'abrir'} className={modo === 'abrir' ? 'ativo' : ''} onClick={() => trocarModo('abrir')}>Abrir conta</button>
        <button type="button" role="tab" aria-selected={modo === 'acessar'} className={modo === 'acessar' ? 'ativo' : ''} onClick={() => trocarModo('acessar')}>Acessar conta aberta</button>
      </div>
      <p className="mudo">{modo === 'abrir'
        ? 'Informe quem será o responsável pela conta da mesa. Não é preciso ter cadastro.'
        : 'Informe o CPF do responsável para acessar a conta que já está aberta.'}</p>
      {modo === 'abrir' && <Campo label="Nome do responsável"><input value={nome} onChange={(e) => setNome(e.target.value)} required maxLength={150} autoFocus /></Campo>}
      <Campo label="CPF"><input value={cpf} onChange={(e) => setCpf(e.target.value)} required inputMode="numeric" placeholder="000.000.000-00" /></Campo>
      <Erro erro={erro} />
      {!aberta && <p className="alerta erro" role="status">Estamos fechados no momento: não é possível acessar ou abrir contas agora.</p>}
      <button className="btn grande" disabled={ocupado || !aberta}>{ocupado
        ? (modo === 'abrir' ? 'Abrindo…' : 'Acessando…')
        : (modo === 'abrir' ? 'Abrir conta da mesa' : 'Acessar conta')}</button>
    </form>
  )
}

export default function Mesa() {
  const { cliente } = useCliente()

  return (
    <div className="lj-pagina estreita">
      <h1 className="lj-titulo-pagina">Consumir na mesa</h1>
      {cliente ? <MesaCliente /> : <MesaVisitante />}
    </div>
  )
}
