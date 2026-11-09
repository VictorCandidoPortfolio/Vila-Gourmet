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
  const [nome, setNome] = useState('')
  const [cpf, setCpf] = useState('')
  const { erro, ocupado, executar } = useAcao()

  const abrir = (e) => {
    e.preventDefault()
    executar(async () => {
      const conta = await post('/api/public/mesas', { nome: nome.trim(), cpf })
      setMesa({ codigo: conta.codigoConta, cpf, nome: conta.nomeResponsavel ?? nome.trim() })
      setModalidade('MESA')
    })
  }

  const pedir = () => { setModalidade('MESA'); navigate('/cardapio') }
  const esquecer = () => { setMesa(null); setModalidade(null) }

  return (
    <div className="lj-pagina estreita">
      <h1 className="lj-titulo-pagina">Consumir na mesa</h1>
      {mesa ? (
        <div className="lj-cartao lj-centro">
          <span className="lj-selo"><Icon nome="store" tamanho={28} /></span>
          <h2>Sua conta está aberta</h2>
          <small className="mudo">Código da conta</small>
          <strong className="lj-codigo">{mesa.codigo}</strong>
          <p>Responsável: <strong>{mesa.nome}</strong>. Tudo o que pedir vai para esta conta e o pagamento é feito no fechamento.</p>
          <div className="lj-acoes centro">
            <button className="btn grande" onClick={pedir} disabled={!aberta}>Fazer um pedido</button>
            <button className="btn contorno" onClick={esquecer}>Não usar mais esta conta neste aparelho</button>
          </div>
        </div>
      ) : (
        <form className="lj-cartao" onSubmit={abrir}>
          <p className="mudo">Informe quem será o responsável pela conta da mesa. Não é preciso ter cadastro.</p>
          <Campo label="Nome do responsável"><input value={nome} onChange={(e) => setNome(e.target.value)} required maxLength={150} autoFocus /></Campo>
          <Campo label="CPF"><input value={cpf} onChange={(e) => setCpf(e.target.value)} required inputMode="numeric" placeholder="000.000.000-00" /></Campo>
          <Erro erro={erro} />
          {!aberta && <p className="alerta erro" role="status">Estamos fechados no momento: não é possível abrir contas agora.</p>}
          <button className="btn grande" disabled={ocupado || !aberta}>{ocupado ? 'Abrindo…' : 'Abrir conta da mesa'}</button>
        </form>
      )}
    </div>
  )
}
