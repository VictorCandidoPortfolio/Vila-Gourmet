import { useState } from 'react'
import { get, post } from '../api.js'
import { Badge, Erro, dataHora, rotulo, useAcao, useCarga } from '../ui.jsx'

export default function Cancelamentos() {
  const [filtro, setFiltro] = useState('PENDENTE')
  const { dados, erro, recarregar } = useCarga(
    () => get(`/api/gestao/cancelamentos${filtro ? `?estado=${filtro}` : ''}`), [filtro], 15000)
  const acao = useAcao()
  const [justificativas, setJustificativas] = useState({})

  const decidir = (c, aprovar) => acao.executar(async () => {
    await post(`/api/gestao/cancelamentos/${c.id}/decisao`, { aprovar, justificativa: justificativas[c.id] || null })
    await recarregar()
  })

  return (
    <>
      <h1>Solicitações de cancelamento</h1>
      <p className="mudo">Solicitar cancelamento não cancela o pedido: depois da confirmação do restaurante, a decisão é sua.</p>
      <div className="filtros">
        <label className="campo"><span>Mostrar</span>
          <select value={filtro} onChange={(e) => setFiltro(e.target.value)}>
            <option value="PENDENTE">Pendentes</option><option value="">Todas</option>
            <option value="APROVADO">Aprovadas</option><option value="REJEITADO">Rejeitadas</option>
          </select>
        </label>
      </div>
      <Erro erro={erro} /><Erro erro={acao.erro} />
      <table>
        <thead><tr><th>Pedido</th><th>Solicitante</th><th>Solicitado em</th><th>Estado do pedido</th><th>Situação</th><th>Decisão</th></tr></thead>
        <tbody>
          {dados?.map((c) => (
            <tr key={c.id}>
              <td><strong>{c.numeroPedido}</strong></td>
              <td>{rotulo(c.solicitadoPor)}</td>
              <td>{dataHora(c.solicitadoEm)}</td>
              <td><Badge valor={c.estadoPedido} /></td>
              <td><Badge valor={c.estado} />{c.justificativa && <div className="pequeno mudo">{c.justificativa}</div>}</td>
              <td>
                {c.estado === 'PENDENTE' && (
                  <div className="linha">
                    <input placeholder="Justificativa (obrigatória para rejeitar)" value={justificativas[c.id] ?? ''}
                      onChange={(e) => setJustificativas({ ...justificativas, [c.id]: e.target.value })} />
                    <button className="btn" disabled={acao.ocupado} onClick={() => decidir(c, true)}>Aprovar</button>
                    <button className="btn perigo" disabled={acao.ocupado} onClick={() => decidir(c, false)}>Rejeitar</button>
                  </div>
                )}
              </td>
            </tr>
          ))}
          {dados?.length === 0 && <tr><td colSpan="6" className="mudo">Nenhuma solicitação.</td></tr>}
        </tbody>
      </table>
    </>
  )
}
