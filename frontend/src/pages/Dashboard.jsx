import { useState } from 'react'
import { get } from '../api.js'
import Icon from '../components/Icon.jsx'
import { Campo, Erro, brl, rotulo, useCarga } from '../ui.jsx'

const iso = (d) => {
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

/** Distribuição em tabela simples (a V1 não inclui gráficos). */
function Distribuicao({ titulo, dados }) {
  const total = Object.values(dados).reduce((a, b) => a + b, 0)
  return (
    <section className="painel sem-padding">
      <h3 className="titulo-painel">{titulo}</h3>
      <table>
        <thead><tr><th>{titulo.includes('estado') ? 'Estado' : 'Modalidade'}</th><th>Pedidos</th></tr></thead>
        <tbody>
          {Object.entries(dados).map(([k, v]) => <tr key={k}><td>{rotulo(k)}</td><td>{v}</td></tr>)}
          {total === 0 && <tr><td colSpan="2" className="mudo">Sem pedidos no período.</td></tr>}
        </tbody>
      </table>
    </section>
  )
}

/** Relatórios operacionais básicos da V1: quantidade, distribuição por estado e modalidade, total vendido. */
export default function Dashboard() {
  const hoje = new Date()
  const [inicio, setInicio] = useState(iso(new Date(hoje.getFullYear(), hoje.getMonth(), hoje.getDate() - 29)))
  const [fim, setFim] = useState(iso(hoje))
  const { dados, erro, carregando } = useCarga(
    () => get(`/api/gestao/relatorios/operacional?inicio=${inicio}&fim=${fim}`), [inicio, fim])

  return (
    <>
      <h1>Relatórios operacionais</h1>
      <div className="filtros">
        <Campo label="De"><input type="date" value={inicio} onChange={(e) => setInicio(e.target.value)} /></Campo>
        <Campo label="Até"><input type="date" value={fim} onChange={(e) => setFim(e.target.value)} /></Campo>
      </div>
      <Erro erro={erro} />
      {carregando && !dados && <p>Carregando…</p>}
      {dados && (
        <>
          <div className="kpis">
            <div className="kpi kpi-rosa">
              <span className="kpi-icone"><Icon nome="bag" tamanho={26} /></span>
              <div><span className="kpi-titulo">Quantidade de pedidos</span><div className="kpi-valor"><strong>{dados.quantidadePedidos}</strong></div></div>
            </div>
            <div className="kpi kpi-verde">
              <span className="kpi-icone"><Icon nome="dollar" tamanho={26} /></span>
              <div><span className="kpi-titulo">Total vendido no período</span><div className="kpi-valor"><strong>{brl(dados.totalVendido)}</strong></div></div>
            </div>
          </div>
          <div className="duas-colunas">
            <Distribuicao titulo="Pedidos por estado" dados={dados.porEstado} />
            <Distribuicao titulo="Pedidos por modalidade" dados={dados.porModalidade} />
          </div>
          <p className="mudo pequeno">
            Quantidade e distribuições incluem todos os pedidos criados no período, inclusive cancelados. O total vendido
            exclui pedidos cancelados e aguardando confirmação e é somado por pedido, de modo que pedidos de mesa nunca são
            contados duas vezes.
          </p>
        </>
      )}
    </>
  )
}
