import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { get } from '../api.js'
import Icon from '../components/Icon.jsx'
import { Badge, Erro, brl, dataHora, rotulo, useCarga } from '../ui.jsx'
import PedidoDetalhe from '../components/PedidoDetalhe.jsx'

// Só pedidos em andamento: concluídos e cancelados ficam no Histórico.
const ESTADOS = ['AGUARDANDO_CONFIRMACAO', 'CONFIRMADO', 'EM_PREPARACAO', 'A_CAMINHO', 'PRONTO_PARA_RETIRADA', 'SERVIDO']
const MODALIDADES = ['ENTREGA', 'RETIRADA', 'MESA']

export default function Pedidos() {
  // filtros mantidos na URL
  const [params, setParams] = useSearchParams()
  const estado = params.get('estado') ?? ''
  const modalidade = params.get('modalidade') ?? ''
  const [pagina, setPagina] = useState(0)
  const [aberto, setAberto] = useState(null)

  const mudar = (chave, valor) => {
    const n = new URLSearchParams(params)
    valor ? n.set(chave, valor) : n.delete(chave)
    setParams(n)
    setPagina(0)
  }

  const qs = new URLSearchParams({ situacao: 'ANDAMENTO', pagina, tamanho: 20, ...(estado && { estado }), ...(modalidade && { modalidade }) })
  const { dados, erro, carregando, recarregar } = useCarga(() => get(`/api/gestao/pedidos?${qs}`), [estado, modalidade, pagina], 15000)

  const linhas = Array.isArray(dados?.content) ? dados.content : null
  // PagedModel ({ page: { number, totalPages } }); aceita também o formato plano antigo de uma API ainda não reiniciada
  const pag = dados?.page ?? dados ?? {}
  const paginaAtual = Number(pag.number ?? 0)
  const totalPaginas = Math.max(Number(pag.totalPages ?? 1), 1)

  return (
    <>
      <h1>Pedidos</h1>
      <p className="mudo">Pedidos em andamento. Os concluídos e cancelados ficam no Histórico.</p>
      <div className="filtros">
        <label className="campo"><span>Estado</span>
          <select value={estado} onChange={(e) => mudar('estado', e.target.value)}>
            <option value="">Todos em andamento</option>
            {ESTADOS.map((s) => <option key={s} value={s}>{rotulo(s)}</option>)}
          </select>
        </label>
        <label className="campo"><span>Modalidade</span>
          <select value={modalidade} onChange={(e) => mudar('modalidade', e.target.value)}>
            <option value="">Todas</option>
            {MODALIDADES.map((s) => <option key={s} value={s}>{rotulo(s)}</option>)}
          </select>
        </label>
        <button className="btn sec" onClick={recarregar}>Atualizar</button>
        <small className="mudo">Atualiza sozinho a cada 15 s</small>
      </div>
      <Erro erro={erro} />
      {carregando && !dados && <p>Carregando…</p>}
      {linhas && (
        <section className="painel sem-padding">
          <table className="tabela-mobile">
            <thead><tr><th>Número</th><th>Modalidade</th><th>Estado</th><th>Responsável</th><th>Total</th><th>Pagamento</th><th>Criado em</th><th /></tr></thead>
            <tbody>
              {linhas.map((p) => (
                <tr key={p.id} className="clicavel" onClick={() => setAberto(p.id)}>
                  <td data-label="Pedido"><strong>{p.numeroPedido}</strong>{p.codigoConta && <small> · mesa {p.codigoConta}</small>}</td>
                  <td data-label="Modalidade"><span className={`mod mod-${p.modalidade}`}>{rotulo(p.modalidade)}</span></td>
                  <td data-label="Estado"><Badge valor={p.estado} /></td>
                  <td data-label="Responsável">{p.nomeResponsavel}</td>
                  <td data-label="Total">{brl(p.total)}</td>
                  <td data-label="Pagamento">{p.pagamento ? <Badge valor={p.pagamento.estado} /> : <span className="mudo">—</span>}</td>
                  <td data-label="Criado em">{dataHora(p.criadoEm)}</td>
                  <td data-label="" className="mudo"><Icon nome="eye" tamanho={18} /> Ver pedido</td>
                </tr>
              ))}
              {linhas.length === 0 && <tr><td colSpan="8" className="mudo">Nenhum pedido em andamento.</td></tr>}
            </tbody>
          </table>
          <div className="paginacao">
            <button className="btn sec" disabled={paginaAtual <= 0} onClick={() => setPagina((p) => p - 1)}>Anterior</button>
            <span>Página {paginaAtual + 1} de {totalPaginas}</span>
            <button className="btn sec" disabled={paginaAtual + 1 >= totalPaginas} onClick={() => setPagina((p) => p + 1)}>Próxima</button>
          </div>
        </section>
      )}
      {aberto && <PedidoDetalhe id={aberto} onFechar={() => { setAberto(null); recarregar() }} />}
    </>
  )
}
