import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { get, post } from '../../api.js'
import { Badge, Campo, Erro, brl, dataHora, rotulo, useAcao, useCarga } from '../../ui.jsx'
import { useCliente } from '../cliente.jsx'

// Etapas do pedido por modalidade (o cancelamento é tratado à parte).
const ETAPAS = {
  ENTREGA: ['AGUARDANDO_CONFIRMACAO', 'CONFIRMADO', 'EM_PREPARACAO', 'A_CAMINHO', 'CONCLUIDO'],
  RETIRADA: ['AGUARDANDO_CONFIRMACAO', 'CONFIRMADO', 'EM_PREPARACAO', 'PRONTO_PARA_RETIRADA', 'CONCLUIDO'],
  MESA: ['AGUARDANDO_CONFIRMACAO', 'CONFIRMADO', 'EM_PREPARACAO', 'SERVIDO', 'CONCLUIDO'],
}

function Linha({ pedido }) {
  const etapas = ETAPAS[pedido.modalidade] ?? ETAPAS.RETIRADA
  const atual = etapas.indexOf(pedido.estado)
  const quando = (estado) => pedido.historico.find((h) => h.estado === estado)?.em
  return (
    <ol className="lj-linha-tempo">
      {etapas.map((e, i) => (
        <li key={e} className={i < atual ? 'feita' : i === atual ? 'atual' : ''}>
          <span className="lj-ponto" />
          <span><strong>{rotulo(e)}</strong>{quando(e) && <small>{dataHora(quando(e))}</small>}</span>
        </li>
      ))}
    </ol>
  )
}

function DetalhesPedido({ pedido, solicitarCancelamento }) {
  const [cancelamento, setCancelamento] = useState(null)
  const cancelar = useAcao()
  const encerrado = ['CONCLUIDO', 'CANCELADO'].includes(pedido.estado)

  const pedirCancelamento = () => {
    if (!window.confirm('Solicitar o cancelamento deste pedido?')) return
    cancelar.executar(async () => setCancelamento(await solicitarCancelamento()))
  }

  return (
    <section className="lj-cartao">
      <div className="lj-item-topo">
        <h2>Pedido {pedido.numeroPedido}</h2>
        <Badge valor={pedido.estado} />
      </div>
      <p className="mudo">{rotulo(pedido.modalidade)} · total {brl(pedido.total)}{pedido.pagamento && ` · pagamento: ${rotulo(pedido.pagamento)}`}</p>
      {pedido.estado === 'CANCELADO' ? <p className="alerta erro">Este pedido foi cancelado.</p> : <Linha pedido={pedido} />}
      <h3>Itens</h3>
      <ul className="lj-lista-simples">{pedido.itens.map((i, k) => <li key={k}>{i}</li>)}</ul>
      {!encerrado && !cancelamento && (
        <>
          <Erro erro={cancelar.erro} />
          <button type="button" className="lj-link-texto" disabled={cancelar.ocupado} onClick={pedirCancelamento}>Solicitar cancelamento</button>
        </>
      )}
      {cancelamento && (
        <p className="alerta aviso" role="status">Solicitação de cancelamento: {rotulo(cancelamento.estado)}.</p>
      )}
    </section>
  )
}

function ListaCliente() {
  const { dados, erro, carregando } = useCarga(() => get('/api/cliente/pedidos'), [], 15000)

  return (
    <>
      {carregando && <p role="status" className="mudo">Carregando pedidos…</p>}
      <Erro erro={erro} />
      {dados?.length === 0 && <p className="lj-cartao">Você ainda não tem pedidos vinculados a esta conta.</p>}
      {dados?.length > 0 && (
        <ul className="lj-lista lj-rastreio-lista">
          {dados.map((pedido) => (
            <li key={pedido.id}>
              <Link className="lj-cartao lj-rastreio-pedido" to={`?pedido=${pedido.id}`}>
                <span className="lj-item-topo">
                  <strong>Pedido {pedido.numeroPedido}</strong>
                  <Badge valor={pedido.estado} />
                </span>
                <span className="mudo pequeno">{rotulo(pedido.modalidade)} · {dataHora(pedido.criadoEm)} · {brl(pedido.total)}</span>
                <span className="lj-link-texto">Acompanhar pedido</span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </>
  )
}

function RastreioCliente({ id }) {
  const caminho = `/api/cliente/pedidos/${id}`
  const { dados, erro, carregando, recarregar } = useCarga(() => get(`${caminho}/rastreamento`), [caminho], 15000)

  const cancelar = async () => {
    const resposta = await post(`${caminho}/cancelamento`)
    await recarregar()
    return resposta
  }

  return (
    <>
      <Link className="lj-link-texto" to="/acompanhar">Voltar aos meus pedidos</Link>
      {carregando && <p role="status" className="mudo">Carregando acompanhamento…</p>}
      <Erro erro={erro} />
      {dados && <DetalhesPedido pedido={dados} solicitarCancelamento={cancelar} />}
    </>
  )
}

/** Rastreamento por código de acompanhamento ou número do pedido e CPF. */
function RastreioPublico({ somenteCodigo = false }) {
  const [params] = useSearchParams()
  const [modo, setModo] = useState(params.get('codigo') || !params.get('numero') ? 'codigo' : 'cpf')
  const [codigo, setCodigo] = useState(params.get('codigo') ?? '')
  const [numero, setNumero] = useState('')
  const [cpf, setCpf] = useState('')
  const [consulta, setConsulta] = useState(null)
  const [pedido, setPedido] = useState(null)
  const busca = useAcao()

  const corpo = (m = modo) => (m === 'codigo' ? { codigo: codigo.trim() } : { numeroPedido: numero.trim(), cpf })

  const consultar = (c) => busca.executar(async () => setPedido(await post('/api/public/rastreamento', c)))

  const enviar = (e) => {
    e.preventDefault()
    const c = corpo()
    setConsulta(c); setPedido(null)
    consultar(c)
  }

  // Link vindo da confirmação do pedido
  useEffect(() => {
    if (params.get('codigo')) { const c = { codigo: params.get('codigo') }; setConsulta(c); consultar(c) }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    if (!consulta || !pedido || ['CONCLUIDO', 'CANCELADO'].includes(pedido.estado)) return undefined
    const id = setInterval(() => post('/api/public/rastreamento', consulta).then(setPedido).catch(() => {}), 15000)
    return () => clearInterval(id)
  }, [consulta, pedido?.estado])

  return (
    <>
      {somenteCodigo ? (
        <>
          <Link className="lj-link-texto" to="/acompanhar">Voltar aos meus pedidos</Link>
          {busca.ocupado && <p role="status" className="mudo">Carregando acompanhamento…</p>}
          <Erro erro={busca.erro} />
        </>
      ) : <form className="lj-cartao" onSubmit={enviar}>
        <div className="lj-abas" role="tablist">
          <button type="button" role="tab" aria-selected={modo === 'codigo'} className={modo === 'codigo' ? 'ativo' : ''} onClick={() => setModo('codigo')}>Pelo código</button>
          <button type="button" role="tab" aria-selected={modo === 'cpf'} className={modo === 'cpf' ? 'ativo' : ''} onClick={() => setModo('cpf')}>Número do pedido e CPF</button>
        </div>
        {modo === 'codigo' ? (
          <Campo label="Código de acompanhamento"><input value={codigo} onChange={(e) => setCodigo(e.target.value)} required autoFocus /></Campo>
        ) : (
          <div className="lj-form-grade">
            <Campo label="Número do pedido"><input value={numero} onChange={(e) => setNumero(e.target.value)} required /></Campo>
            <Campo label="CPF"><input value={cpf} onChange={(e) => setCpf(e.target.value)} required inputMode="numeric" placeholder="000.000.000-00" /></Campo>
          </div>
        )}
        <Erro erro={busca.erro} />
        <button className="btn" disabled={busca.ocupado}>{busca.ocupado ? 'Buscando…' : 'Consultar'}</button>
      </form>}

      {pedido && (
        <DetalhesPedido key={pedido.numeroPedido} pedido={pedido}
          solicitarCancelamento={() => post('/api/public/pedidos/cancelamento', consulta)} />
      )}
    </>
  )
}

export default function Acompanhar() {
  const { cliente } = useCliente()
  const [params] = useSearchParams()
  const id = params.get('pedido')
  const codigo = params.get('codigo')
  let conteudo

  if (!cliente) {
    conteudo = <RastreioPublico key={codigo ?? 'visitante'} />
  } else if (id && /^\d+$/.test(id)) {
    conteudo = <RastreioCliente key={id} id={id} />
  } else if (codigo) {
    conteudo = <RastreioPublico key={`codigo-${codigo}`} somenteCodigo />
  } else {
    conteudo = <ListaCliente />
  }

  return (
    <div className="lj-pagina estreita">
      <h1 className="lj-titulo-pagina">{cliente && !id && !codigo ? 'Meus pedidos' : 'Acompanhar pedido'}</h1>
      {conteudo}
    </div>
  )
}
