import { useState } from 'react'
import { get } from '../api.js'
import { Badge, Erro, brl, dataHora, rotulo, useCarga } from '../ui.jsx'
import PedidoDetalhe from './PedidoDetalhe.jsx'

// Só entregas em andamento: concluídas e canceladas ficam no Histórico.
const ESTADOS = ['AGUARDANDO_CONFIRMACAO', 'CONFIRMADO', 'EM_PREPARACAO', 'A_CAMINHO']

const buscar = async (estado) => {
  const qs = new URLSearchParams({ situacao: 'ANDAMENTO', modalidade: 'ENTREGA', pagina: 0, tamanho: 100, ...(estado && { estado }) })
  const p = await get(`/api/gestao/pedidos?${qs}`)
  const linhas = Array.isArray(p?.content) ? p.content : []
  const total = Number(p?.page?.totalElements ?? p?.totalElements ?? linhas.length)
  // mais antigos primeiro: é a ordem em que as entregas precisam sair
  return { linhas: [...linhas].sort((a, b) => String(a.criadoEm).localeCompare(String(b.criadoEm))), total }
}

function Endereco({ e }) {
  if (!e) return <span className="mudo">—</span>
  return (
    <>
      {e.logradouro}, {e.numero}{e.complemento ? ` — ${e.complemento}` : ''}
      <small className="mudo bloco">{e.bairro} · {e.cidade}/{e.estado} · CEP {e.cep}</small>
    </>
  )
}

/** Entregas em andamento com cliente, endereço e estado. A tela do pedido (clique na linha) permite avançar o estado. */
export default function PedidosEntrega() {
  const [estado, setEstado] = useState('')
  const [aberto, setAberto] = useState(null)
  const { dados, erro, carregando, recarregar } = useCarga(() => buscar(estado), [estado], 15000)

  return (
    <>
      <p className="mudo">Entregas em andamento. As concluídas e canceladas ficam no Histórico.</p>
      <div className="filtros">
        <label className="campo"><span>Estado</span>
          <select value={estado} onChange={(e) => setEstado(e.target.value)}>
            <option value="">Todos em andamento</option>
            {ESTADOS.map((e) => <option key={e} value={e}>{rotulo(e)}</option>)}
          </select>
        </label>
        <button className="btn sec" onClick={recarregar}>Atualizar</button>
        <small className="mudo">Atualiza sozinho a cada 15 s</small>
      </div>
      <Erro erro={erro} />
      {carregando && !dados && <p>Carregando…</p>}
      {dados && (
        <section className="painel sem-padding">
          <table className="tabela-mobile">
            <thead><tr><th>Pedido</th><th>Cliente</th><th>Endereço de entrega</th><th>Estado</th><th>Total</th><th>Pedido em</th></tr></thead>
            <tbody>
              {dados.linhas.map((p) => (
                <tr key={p.id} className="clicavel" onClick={() => setAberto(p.id)}>
                  <td data-label="Pedido"><strong>{p.numeroPedido}</strong></td>
                  <td data-label="Cliente">{p.nomeResponsavel}</td>
                  <td data-label="Endereço"><Endereco e={p.endereco} /></td>
                  <td data-label="Estado"><Badge valor={p.estado} /></td>
                  <td data-label="Total">{brl(p.total)}</td>
                  <td data-label="Pedido em">{dataHora(p.criadoEm)}</td>
                </tr>
              ))}
              {dados.linhas.length === 0 && <tr><td colSpan="6" className="mudo">Nenhuma entrega em andamento.</td></tr>}
            </tbody>
          </table>
          {dados.total > dados.linhas.length && <p className="mudo pequeno">Mostrando os {dados.linhas.length} primeiros de {dados.total}.</p>}
        </section>
      )}
      {aberto && <PedidoDetalhe id={aberto} onFechar={() => { setAberto(null); recarregar() }} />}
    </>
  )
}
