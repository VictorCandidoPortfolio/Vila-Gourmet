import { useState } from 'react'
import { get, post } from '../api.js'
import { useAuth } from '../auth.jsx'
import { Badge, Campo, Erro, Modal, brl, dataHora, rotulo, useAcao, useCarga } from '../ui.jsx'

// Sequência de estados por modalidade — apenas para a UI sugerir o próximo passo.
// O servidor é quem valida cada transição.
const FLUXO = {
  ENTREGA: ['AGUARDANDO_CONFIRMACAO', 'CONFIRMADO', 'EM_PREPARACAO', 'A_CAMINHO', 'CONCLUIDO'],
  RETIRADA: ['AGUARDANDO_CONFIRMACAO', 'CONFIRMADO', 'EM_PREPARACAO', 'PRONTO_PARA_RETIRADA', 'CONCLUIDO'],
  MESA: ['AGUARDANDO_CONFIRMACAO', 'CONFIRMADO', 'EM_PREPARACAO', 'SERVIDO', 'CONCLUIDO'],
}

/** Detalhe do pedido em telas separadas (abas): resumo, itens, pagamento, andamento e histórico. */
export default function PedidoDetalhe({ id, onFechar }) {
  const { pode } = useAuth()
  const { dados: p, erro: erroCarga, recarregar } = useCarga(() => get(`/api/gestao/pedidos/${id}`), [id])
  const acao = useAcao()
  const [aba, setAba] = useState('resumo')
  const [codigo, setCodigo] = useState('')
  const [justificativa, setJustificativa] = useState('')
  const [metodo, setMetodo] = useState('DINHEIRO')
  const [recebido, setRecebido] = useState('')
  const [cancelando, setCancelando] = useState(false)

  const rodar = (fn) => acao.executar(async () => { await fn(); await recarregar() })

  if (!p) return <Modal titulo="Pedido" onFechar={onFechar}><Erro erro={erroCarga} />{!erroCarga && 'Carregando…'}</Modal>

  const fluxo = FLUXO[p.modalidade]
  const finalizado = p.estado === 'CONCLUIDO' || p.estado === 'CANCELADO'
  const idx = fluxo.indexOf(p.estado)
  const proximo = !finalizado ? fluxo[idx + 1] : null
  const ultimoAntes = !finalizado && p.modalidade !== 'MESA' && proximo === 'CONCLUIDO'
  const podeEstado = pode('PEDIDO_GERENCIAR')
  const pago = p.pagamento?.estado === 'CONFIRMADO'
  const podeConfirmarPagamento = p.modalidade !== 'MESA' && p.estado !== 'CANCELADO' && !pago && pode('PAGAMENTO_CONFIRMAR')
  const podeCancelar = !finalizado && pode('CANCELAMENTO_DECIDIR')
  const temAndamento = !finalizado && (podeEstado || podeCancelar)

  // "Andamento" só existe enquanto há o que fazer com o pedido.
  const abas = [
    ['resumo', 'Resumo'],
    ['itens', `Itens (${p.itens.length})`],
    ['pagamento', 'Pagamento'],
    temAndamento && ['andamento', 'Andamento'],
    ['historico', 'Histórico'],
  ].filter(Boolean)
  const abaAtiva = abas.some(([x]) => x === aba) ? aba : 'resumo'

  return (
    <Modal titulo={`Pedido ${p.numeroPedido}`} onFechar={onFechar} largo>
      <div className="pedido-topo">
        <Badge valor={p.estado} />
        <span>{rotulo(p.modalidade)}{p.codigoConta && ` · conta ${p.codigoConta}`}</span>
        <strong>{brl(p.total)}</strong>
      </div>
      <div className="abas" role="tablist">
        {abas.map(([x, nome]) => (
          <button key={x} role="tab" aria-selected={abaAtiva === x} className={abaAtiva === x ? 'ativo' : ''} onClick={() => setAba(x)}>{nome}</button>
        ))}
      </div>
      <Erro erro={acao.erro} />

      {abaAtiva === 'resumo' && (
        <div className="grade-info">
          <div><span className="mudo">Estado</span><Badge valor={p.estado} /></div>
          <div><span className="mudo">Modalidade</span>{rotulo(p.modalidade)}{p.codigoConta && ` · conta ${p.codigoConta}`}</div>
          <div><span className="mudo">Responsável</span>{p.nomeResponsavel} {p.cpfResponsavel && <small>({p.cpfResponsavel})</small>}</div>
          <div><span className="mudo">Criado em</span>{dataHora(p.criadoEm)}</div>
          {p.endereco && (
            <div className="largura-total"><span className="mudo">Endereço de entrega (snapshot)</span>
              {p.endereco.logradouro}, {p.endereco.numero}{p.endereco.complemento && ` — ${p.endereco.complemento}`} · {p.endereco.bairro} · {p.endereco.cidade}/{p.endereco.estado} · CEP {p.endereco.cep}
            </div>
          )}
          {p.observacao && <div className="largura-total"><span className="mudo">Observação</span>{p.observacao}</div>}
        </div>
      )}

      {abaAtiva === 'itens' && (
        <table>
          <thead><tr><th>Item</th><th>Qtd</th><th>Unitário</th><th>Subtotal</th></tr></thead>
          <tbody>
            {p.itens.map((i, k) => (
              <tr key={k}>
                <td>
                  <strong>{i.nomeProduto}</strong>
                  {i.escolhas.map((e, j) => <div key={j} className="pequeno mudo">{e.quantidade}x {e.opcao} <em>({e.grupo} · {rotulo(e.modalidadeCobranca)})</em></div>)}
                  {i.adicionais.map((a, j) => <div key={j} className="pequeno mudo">+ {a.quantidade}x {a.nome} ({brl(a.subtotal)})</div>)}
                </td>
                <td>{i.quantidade}</td><td>{brl(i.precoUnitario)}</td><td>{brl(i.subtotal)}</td>
              </tr>
            ))}
          </tbody>
          <tfoot>
            <tr><td colSpan="3">Subtotal</td><td>{brl(p.subtotal)}</td></tr>
            {p.modalidade === 'ENTREGA' && <tr><td colSpan="3">Taxa de entrega</td><td>{brl(p.taxaEntrega)}</td></tr>}
            <tr><td colSpan="3"><strong>Total</strong></td><td><strong>{brl(p.total)}</strong></td></tr>
          </tfoot>
        </table>
      )}

      {abaAtiva === 'pagamento' && (
        <>
          {p.pagamento
            ? <p><Badge valor={p.pagamento.estado} /> {rotulo(p.pagamento.metodo)} · {brl(p.pagamento.valor)}
                {p.pagamento.troco != null && ` · troco ${brl(p.pagamento.troco)}`}{p.pagamento.confirmadoEm && ` · confirmado em ${dataHora(p.pagamento.confirmadoEm)}`}</p>
            : <p className="mudo">Nenhum método escolhido ainda. {p.modalidade === 'MESA' ? 'O pagamento é feito no fechamento da conta.' : ''}</p>}

          {podeConfirmarPagamento && (
            <section className="acoes">
              <h3>Confirmar recebimento</h3>
              <div className="linha">
                <Campo label="Método">
                  <select value={metodo} onChange={(e) => setMetodo(e.target.value)}>
                    <option value="DINHEIRO">Dinheiro</option><option value="CARTAO">Cartão</option><option value="PIX">PIX (verificar manualmente)</option>
                  </select>
                </Campo>
                {metodo === 'DINHEIRO' && (
                  <Campo label="Valor recebido" dica="Opcional; calcula o troco">
                    <input type="number" step="0.01" min="0" value={recebido} onChange={(e) => setRecebido(e.target.value)} />
                  </Campo>
                )}
                <button className="btn" disabled={acao.ocupado}
                  onClick={() => rodar(() => post(`/api/gestao/pedidos/${id}/pagamento`, { metodo, valorRecebido: metodo === 'DINHEIRO' && recebido ? Number(recebido) : null }))}>
                  Confirmar pagamento de {brl(p.total)}
                </button>
              </div>
            </section>
          )}
        </>
      )}

      {abaAtiva === 'andamento' && (
        <>
          {podeEstado && (
            <section className="acoes">
              <h3>Andamento</h3>
              {proximo && !ultimoAntes && (
                <button className="btn" disabled={acao.ocupado} onClick={() => rodar(() => post(`/api/gestao/pedidos/${id}/estado`, { estado: proximo }))}>
                  Avançar para: {rotulo(proximo)}
                </button>
              )}
              {ultimoAntes && (
                <>
                  <div className="linha">
                    <Campo label="Código informado pelo cliente">
                      <input value={codigo} onChange={(e) => setCodigo(e.target.value)} inputMode="numeric" maxLength={6} placeholder="6 dígitos" />
                    </Campo>
                    <button className="btn" disabled={acao.ocupado || codigo.length < 6}
                      onClick={() => rodar(() => post(`/api/gestao/pedidos/${id}/concluir-com-codigo`, { codigo }))}>Concluir com código</button>
                  </div>
                  <div className="linha">
                    <Campo label="Conclusão manual (sem código) — justificativa obrigatória">
                      <input value={justificativa} onChange={(e) => setJustificativa(e.target.value)} placeholder="Ex.: cliente esqueceu o código" />
                    </Campo>
                    <button className="btn sec" disabled={acao.ocupado || !justificativa.trim()}
                      onClick={() => rodar(() => post(`/api/gestao/pedidos/${id}/concluir-manual`, { justificativa }))}>Concluir manualmente</button>
                  </div>
                </>
              )}
            </section>
          )}

          {podeCancelar && (
            <section className="acoes">
              {!cancelando ? (
                <button className="btn perigo" onClick={() => setCancelando(true)}>Cancelar pedido…</button>
              ) : (
                <div className="linha">
                  <Campo label="Justificativa do cancelamento">
                    <input value={justificativa} onChange={(e) => setJustificativa(e.target.value)} />
                  </Campo>
                  <button className="btn perigo" disabled={acao.ocupado || !justificativa.trim()}
                    onClick={() => rodar(() => post(`/api/gestao/pedidos/${id}/cancelar`, { justificativa }))}>Confirmar cancelamento</button>
                  <button className="btn sec" onClick={() => setCancelando(false)}>Voltar</button>
                </div>
              )}
            </section>
          )}
        </>
      )}

      {abaAtiva === 'historico' && (
        <ul className="linha-tempo">
          {p.historico?.map((h, i) => (
            <li key={i}>
              <strong>{rotulo(h.estadoNovo)}</strong> <small>{dataHora(h.em)}{h.funcionarioId ? ` · funcionário #${h.funcionarioId}` : ''}</small>
              {h.conclusaoManual && <span className="badge b-CANCELADO">conclusão manual</span>}
              {h.justificativa && <div className="pequeno mudo">“{h.justificativa}”</div>}
            </li>
          ))}
        </ul>
      )}
    </Modal>
  )
}
