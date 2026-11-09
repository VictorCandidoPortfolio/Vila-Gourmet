import { useState } from 'react'
import { get } from '../api.js'
import Icon from '../components/Icon.jsx'
import PedidoDetalhe from '../components/PedidoDetalhe.jsx'
import { Badge, Campo, Erro, brl, dataHora, rotulo, useCarga } from '../ui.jsx'

const MODALIDADES = ['ENTREGA', 'RETIRADA', 'MESA']
const iso = (d) => {
  const z = new Date(d.getTime() - d.getTimezoneOffset() * 60000)
  return z.toISOString().slice(0, 10)
}
const deslocar = (dias) => { const d = new Date(); d.setDate(d.getDate() + dias); return iso(d) }

/** Histórico: pedidos concluídos e cancelados, com filtro por dia (data de criação do pedido). */
export default function Historico() {
  const [de, setDe] = useState(deslocar(0))
  const [ate, setAte] = useState(deslocar(0))
  const [estado, setEstado] = useState('')
  const [modalidade, setModalidade] = useState('')
  const [pagina, setPagina] = useState(0)
  const [aberto, setAberto] = useState(null)

  const periodo = (a, b) => { setDe(a); setAte(b); setPagina(0) }
  const invalido = de && ate && de > ate

  const qs = new URLSearchParams({
    situacao: 'FINALIZADOS', pagina, tamanho: 20,
    ...(estado && { estado }), ...(modalidade && { modalidade }), ...(de && { de }), ...(ate && { ate }),
  })
  const { dados, erro, carregando, recarregar } = useCarga(
    () => (invalido ? Promise.resolve(null) : get(`/api/gestao/pedidos?${qs}`)), [de, ate, estado, modalidade, pagina, invalido])

  const linhas = Array.isArray(dados?.content) ? dados.content : null
  const pag = dados?.page ?? dados ?? {}
  const paginaAtual = Number(pag.number ?? 0)
  const totalPaginas = Math.max(Number(pag.totalPages ?? 1), 1)
  const total = Number(pag.totalElements ?? linhas?.length ?? 0)

  return (
    <>
      <h1>Histórico</h1>
      <p className="mudo">Pedidos concluídos e cancelados. O filtro de dia usa a data em que o pedido foi feito.</p>

      <div className="filtros">
        <Campo label="De"><input type="date" value={de} max={ate || undefined} onChange={(e) => { setDe(e.target.value); setPagina(0) }} /></Campo>
        <Campo label="Até"><input type="date" value={ate} min={de || undefined} onChange={(e) => { setAte(e.target.value); setPagina(0) }} /></Campo>
        <div className="atalhos-dia">
          <button className="btn sec" onClick={() => periodo(deslocar(0), deslocar(0))}>Hoje</button>
          <button className="btn sec" onClick={() => periodo(deslocar(-1), deslocar(-1))}>Ontem</button>
          <button className="btn sec" onClick={() => periodo(deslocar(-6), deslocar(0))}>Últimos 7 dias</button>
          <button className="btn sec" onClick={() => periodo('', '')}>Todos os dias</button>
        </div>
      </div>
      <div className="filtros">
        <label className="campo"><span>Situação</span>
          <select value={estado} onChange={(e) => { setEstado(e.target.value); setPagina(0) }}>
            <option value="">Concluídos e cancelados</option>
            <option value="CONCLUIDO">Concluídos</option>
            <option value="CANCELADO">Cancelados</option>
          </select>
        </label>
        <label className="campo"><span>Modalidade</span>
          <select value={modalidade} onChange={(e) => { setModalidade(e.target.value); setPagina(0) }}>
            <option value="">Todas</option>
            {MODALIDADES.map((m) => <option key={m} value={m}>{rotulo(m)}</option>)}
          </select>
        </label>
        <button className="btn sec" onClick={recarregar}>Atualizar</button>
      </div>

      {invalido && <div className="alerta erro" role="alert">A data inicial não pode ser depois da data final.</div>}
      <Erro erro={erro} />
      {carregando && !dados && !invalido && <p>Carregando…</p>}
      {linhas && (
        <section className="painel sem-padding">
          <table>
            <thead><tr><th>Número</th><th>Modalidade</th><th>Situação</th><th>Responsável</th><th>Total</th><th>Pagamento</th><th>Pedido em</th><th /></tr></thead>
            <tbody>
              {linhas.map((p) => (
                <tr key={p.id} className="clicavel" onClick={() => setAberto(p.id)}>
                  <td><strong>{p.numeroPedido}</strong>{p.codigoConta && <small> · mesa {p.codigoConta}</small>}</td>
                  <td><span className={`mod mod-${p.modalidade}`}>{rotulo(p.modalidade)}</span></td>
                  <td><Badge valor={p.estado} /></td>
                  <td>{p.nomeResponsavel}</td>
                  <td>{brl(p.total)}</td>
                  <td>{p.pagamento ? <Badge valor={p.pagamento.estado} /> : <span className="mudo">—</span>}</td>
                  <td>{dataHora(p.criadoEm)}</td>
                  <td className="mudo"><Icon nome="eye" tamanho={18} /></td>
                </tr>
              ))}
              {linhas.length === 0 && <tr><td colSpan="8" className="mudo">Nenhum pedido concluído ou cancelado nesse período.</td></tr>}
            </tbody>
          </table>
          <div className="paginacao">
            <button className="btn sec" disabled={paginaAtual <= 0} onClick={() => setPagina((p) => p - 1)}>← Anterior</button>
            <span>Página {paginaAtual + 1} de {totalPaginas} · {total} {total === 1 ? 'pedido' : 'pedidos'}</span>
            <button className="btn sec" disabled={paginaAtual + 1 >= totalPaginas} onClick={() => setPagina((p) => p + 1)}>Próxima →</button>
          </div>
        </section>
      )}
      {aberto && <PedidoDetalhe id={aberto} onFechar={() => setAberto(null)} />}
    </>
  )
}
