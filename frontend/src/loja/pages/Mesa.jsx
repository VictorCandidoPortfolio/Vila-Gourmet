import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { post } from '../../api.js'
import Icon from '../../components/Icon.jsx'
import { Campo, Erro, useAcao } from '../../ui.jsx'
import { useCarrinho } from '../carrinho.jsx'
import { useLoja } from '../lojaStatus.jsx'

/**
 * Consumo na mesa: o responsável abre a conta com nome e CPF e passa a pedir pelo código dela.
 * A conta aberta fica guardada neste aparelho para os próximos pedidos.
 */
export default function Mesa() {
  const navigate = useNavigate()
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

  const pedir = () => { setModalidade('MESA'); navigate('/cardapio') }
  const esquecer = () => { setMesa(null); setModalidade(null) }
  const emFechamento = mesa?.estado === 'EM_FECHAMENTO'

  return (
    <div className="lj-pagina estreita">
      <h1 className="lj-titulo-pagina">Consumir na mesa</h1>
      {mesa ? (
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
            <button className="btn contorno" onClick={esquecer}>Não usar mais esta conta neste aparelho</button>
          </div>
        </div>
      ) : (
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
      )}
    </div>
  )
}
