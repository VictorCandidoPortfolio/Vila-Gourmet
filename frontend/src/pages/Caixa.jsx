import { useMemo, useState } from 'react'
import { get, post } from '../api.js'
import Icon from '../components/Icon.jsx'
import PdvPedido, { PedidoLancado } from '../components/PdvPedido.jsx'
import PdvProdutoModal from '../components/PdvProdutoModal.jsx'
import { resumoDoItem } from '../montagem.js'
import { Erro, Modal, brl, dataHora, useAcao, useCarga } from '../ui.jsx'

const semAcento = (s) => s.normalize('NFD').replace(/\p{Diacritic}/gu, '').toLowerCase()

/** Aviso antes de fechar: pedidos não concluídos serão cancelados, e há casos que impedem o fechamento. */
export function FecharCaixa({ status, onFechar, onFechado }) {
  const acao = useAcao()
  const bloqueado = status.bloqueios.length > 0
  const comPedidos = status.pedidosEmAberto > 0

  const fechar = () => acao.executar(async () => {
    const r = await post('/api/caixa/fechar', { confirmarCancelamentos: comPedidos })
    onFechado(r)
  })

  return (
    <Modal titulo="Fechar o caixa" onFechar={onFechar} semEsc={acao.ocupado}>
      {bloqueado ? (
        <div className="alerta erro" role="alert">
          <strong>Não é possível fechar o caixa agora.</strong>
          <ul>{status.bloqueios.map((b) => <li key={b}>{b}</li>)}</ul>
        </div>
      ) : comPedidos ? (
        <div className="alerta aviso" role="alert">
          <strong>Atenção: pedidos não concluídos serão cancelados.</strong>
          <p>Existem {status.pedidosEmAberto} {status.pedidosEmAberto === 1 ? 'pedido não concluído' : 'pedidos não concluídos'}.
            Ao fechar o caixa {status.pedidosEmAberto === 1 ? 'ele será cancelado' : 'eles serão cancelados'}, e as contas de mesa abertas serão encerradas sem cobrança.</p>
        </div>
      ) : (
        <p>Nenhum pedido em aberto. Com o caixa fechado a pizzaria não aceita vendas, nem pela loja do cliente.</p>
      )}
      {comPedidos && !bloqueado && <p className="mudo">Com o caixa fechado a pizzaria também não aceita vendas pela loja do cliente.</p>}
      <Erro erro={acao.erro} />
      <div className="linha">
        <button className="btn sec" onClick={onFechar} disabled={acao.ocupado}>Voltar</button>
        {!bloqueado && (
          <button className={`btn ${comPedidos ? 'perigo-cheio' : ''}`} onClick={fechar} disabled={acao.ocupado}>
            {acao.ocupado ? 'Fechando…' : comPedidos ? 'Cancelar os pedidos e fechar o caixa' : 'Fechar caixa'}
          </button>
        )}
      </div>
    </Modal>
  )
}

function Produtos({ aoEscolher }) {
  const { dados: catalogo, erro, carregando } = useCarga(() => get('/api/public/catalogo'), [])
  const [categoria, setCategoria] = useState('')
  const [busca, setBusca] = useState('')
  const categorias = useMemo(() => {
    const mapa = new Map()
    catalogo?.forEach((p) => mapa.set(p.categoriaId, p.categoriaNome))
    return [...mapa].map(([id, nome]) => ({ id, nome })).sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR'))
  }, [catalogo])
  const produtos = useMemo(() => {
    const t = semAcento(busca.trim())
    return (catalogo ?? []).filter((p) => (!categoria || String(p.categoriaId) === categoria) && (!t || semAcento(`${p.nome} ${p.categoriaNome}`).includes(t)))
  }, [catalogo, categoria, busca])

  return (
    <section className="cartao pdv-produtos">
      <div className="pdv-filtros">
        <div className="pdv-chips">
          <button className={!categoria ? 'ativo' : ''} onClick={() => setCategoria('')}>Todas</button>
          {categorias.map((c) => <button key={c.id} className={String(c.id) === categoria ? 'ativo' : ''} onClick={() => setCategoria(String(c.id))}>{c.nome}</button>)}
        </div>
        <label className="pdv-busca"><Icon nome="search" tamanho={16} /><input value={busca} onChange={(e) => setBusca(e.target.value)} placeholder="Buscar produto…" aria-label="Buscar produto" /></label>
      </div>
      <Erro erro={erro} />
      {carregando && !catalogo && <p className="mudo">Carregando cardápio…</p>}
      {catalogo && produtos.length === 0 && <p className="mudo">{catalogo.length === 0 ? 'Nenhum produto publicado no cardápio.' : 'Nenhum produto encontrado.'}</p>}
      <div className="pdv-grade">
        {produtos.map((p) => (
          <button key={p.id} className="pdv-produto" onClick={() => aoEscolher(p)}>
            <small className="mudo">{p.categoriaNome}</small>
            <strong>{p.nome}</strong>
            <span>{Number(p.precoBase) > 0 ? <>{p.grupos.length > 0 && 'a partir de '}<b>{brl(p.precoBase)}</b></> : <small className="mudo">Montar</small>}</span>
          </button>
        ))}
      </div>
    </section>
  )
}

function FrenteDeCaixa({ status, aoMudar }) {
  const [itens, setItens] = useState([])
  const [produto, setProduto] = useState(null)
  const [pedidoMobile, setPedidoMobile] = useState(false)
  const [lancado, setLancado] = useState(null)
  const [fechando, setFechando] = useState(false)
  const [resultado, setResultado] = useState(null)
  const subtotal = itens.reduce((s, i) => s + Number(i.subtotal), 0)

  const adicionar = (selecao, cotacao) => {
    setItens((l) => [...l, {
      uid: crypto.randomUUID(), selecao, nomeProduto: cotacao.item.nomeProduto, quantidade: cotacao.item.quantidade,
      precoUnitario: cotacao.item.precoUnitario, subtotal: cotacao.item.subtotal, resumo: resumoDoItem(cotacao.item),
      cotacaoToken: cotacao.cotacaoToken, expiraEm: cotacao.expiraEm,
    }])
    setProduto(null)
  }

  return (
    <>
      <section className="cartao pdv-status">
        <div>
          <span className="badge b-CONCLUIDO">Caixa aberto</span>
          <span className="mudo"> desde {dataHora(status.abertoEm)}{status.abertoPor && ` · por ${status.abertoPor}`} · {status.pedidosEmAberto} {status.pedidosEmAberto === 1 ? 'pedido em andamento' : 'pedidos em andamento'}</span>
        </div>
        <button className="btn perigo" onClick={() => setFechando(true)}>Fechar caixa</button>
      </section>

      <div className="pdv">
        <Produtos aoEscolher={setProduto} />
        {pedidoMobile && <button className="pdv-pedido-overlay" aria-label="Fechar pedido" onClick={() => setPedidoMobile(false)} />}
        <PdvPedido itens={itens} setItens={setItens} mobileAberto={pedidoMobile} onFecharMobile={() => setPedidoMobile(false)}
          onLancado={(r) => { setItens([]); setPedidoMobile(false); setLancado(r); aoMudar() }} />
      </div>

      <button className="pdv-pedido-mobile-bar" onClick={() => setPedidoMobile(true)} aria-label={`Ver pedido com ${itens.length} itens`}>
        <span><strong>{itens.length}</strong> {itens.length === 1 ? 'item' : 'itens'}</span>
        <span className="pdv-mobile-total">{brl(subtotal)}</span>
        <span>Ver pedido</span>
      </button>

      {produto && <PdvProdutoModal produto={produto} onAdicionar={adicionar} onFechar={() => setProduto(null)} />}
      {lancado && <PedidoLancado resposta={lancado} onFechar={() => setLancado(null)} />}
      {fechando && <FecharCaixa status={status} onFechar={() => setFechando(false)} onFechado={(r) => { setFechando(false); setResultado(r); aoMudar() }} />}
      {resultado && <Modal titulo="Caixa fechado" onFechar={() => setResultado(null)}>
        <p>{resultado.pedidosCancelados === 0 ? 'Nenhum pedido precisou ser cancelado.' : `${resultado.pedidosCancelados} ${resultado.pedidosCancelados === 1 ? 'pedido não concluído foi cancelado' : 'pedidos não concluídos foram cancelados'}.`}
          {resultado.contasEncerradas > 0 && ` ${resultado.contasEncerradas} ${resultado.contasEncerradas === 1 ? 'conta de mesa foi encerrada' : 'contas de mesa foram encerradas'} sem cobrança.`}</p>
        <div className="linha"><button className="btn" onClick={() => setResultado(null)}>Ok</button></div>
      </Modal>}
    </>
  )
}

export default function Caixa() {
  const { dados: status, erro, carregando, recarregar } = useCarga(() => get('/api/caixa'), [], 15000)
  const abrir = useAcao()

  return (
    <>
      <h1>Frente de caixa</h1>
      <Erro erro={erro} />
      {carregando && !status && <p>Carregando…</p>}
      {status && !status.aberto && (
        <section className="cartao pdv-fechado">
          <span className="badge b-CANCELADO">Caixa fechado</span>
          <h2>A pizzaria está fechada</h2>
          <p className="mudo">Com o caixa fechado não é possível realizar vendas, nem aqui nem pela loja do cliente. Abra o caixa para começar a lançar pedidos e liberar os pedidos online.</p>
          <Erro erro={abrir.erro} />
          <button className="btn" disabled={abrir.ocupado} onClick={() => abrir.executar(async () => { await post('/api/caixa/abrir'); await recarregar() })}>
            {abrir.ocupado ? 'Abrindo…' : 'Abrir caixa'}
          </button>
        </section>
      )}
      {status?.aberto && <FrenteDeCaixa status={status} aoMudar={recarregar} />}
    </>
  )
}
