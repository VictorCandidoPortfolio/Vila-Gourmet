import { useState } from 'react'
import { get, post } from '../api.js'
import { useAuth } from '../auth.jsx'
import { Badge, Campo, Erro, brl, dataHora, rotulo, useAcao, useCarga } from '../ui.jsx'
import MontadorItem from '../components/MontadorItem.jsx'
import PedidoDetalhe from '../components/PedidoDetalhe.jsx'

function ContaDetalhe({ codigo, onAtualizou }) {
  const { pode } = useAuth()
  const { dados: c, erro, recarregar } = useCarga(() => get(`/api/gestao/mesas/${codigo}`), [codigo], 15000)
  const acao = useAcao()
  const [novos, setNovos] = useState([])
  const [pedidoAberto, setPedidoAberto] = useState(null)
  const [metodo, setMetodo] = useState('DINHEIRO')
  const [recebido, setRecebido] = useState('')
  const [justificativa, setJustificativa] = useState('')

  const rodar = (fn) => acao.executar(async () => { await fn(); await recarregar(); onAtualizou() })

  if (!c) return <Erro erro={erro} />
  const aberta = c.estado === 'ABERTA'
  const fech = c.fechamento

  return (
    <section className="cartao">
      <div className="linha entre">
        <h2>Conta {c.codigoConta} <Badge valor={c.estado} /></h2>
        <strong className="grande">{brl(c.total)}</strong>
      </div>
      <p>{c.nomeResponsavel} <small className="mudo">{c.cpfResponsavel && `(${c.cpfResponsavel}) · `}aberta em {dataHora(c.abertaEm)}</small></p>
      <Erro erro={acao.erro} />

      <h3>Pedidos da conta</h3>
      <table>
        <thead><tr><th>Número</th><th>Estado</th><th>Itens</th><th>Total</th></tr></thead>
        <tbody>
          {c.pedidos.map((p) => (
            <tr key={p.id} className="clicavel" onClick={() => setPedidoAberto(p.id)}>
              <td><strong>{p.numeroPedido}</strong></td><td><Badge valor={p.estado} /></td>
              <td>{p.itens.map((i) => `${i.quantidade}x ${i.nomeProduto}`).join(', ')}</td><td>{brl(p.total)}</td>
            </tr>
          ))}
          {c.pedidos.length === 0 && <tr><td colSpan="4" className="mudo">Sem pedidos ainda.</td></tr>}
        </tbody>
      </table>

      {aberta && (
        <>
          <h3>Adicionar pedido</h3>
          <MontadorItem onAdicionar={(item, resumo) => setNovos([...novos, { item, resumo }])} />
          {novos.length > 0 && (
            <>
              <ul>{novos.map((n, i) => <li key={i}>{n.resumo}</li>)}</ul>
              <div className="linha">
                <button className="btn" disabled={acao.ocupado}
                  onClick={() => rodar(async () => {
                    await post(`/api/gestao/mesas/${c.codigoConta}/pedidos`, { itens: novos.map((n) => n.item) })
                    setNovos([])
                  })}>Lançar pedido na conta</button>
                <button className="btn sec" onClick={() => setNovos([])}>Limpar</button>
              </div>
            </>
          )}
          <h3>Fechamento</h3>
          <p className="mudo">Só é possível fechar quando todos os pedidos não cancelados estiverem servidos.</p>
          <button className="btn" disabled={acao.ocupado} onClick={() => rodar(() => post(`/api/gestao/mesas/${c.codigoConta}/fechamento`))}>
            Iniciar fechamento da conta
          </button>
        </>
      )}

      {c.estado === 'EM_FECHAMENTO' && fech && (
        <>
          <h3>Fechamento pendente — {brl(fech.total)}</h3>
          {pode('PAGAMENTO_CONFIRMAR') && (
            <div className="linha">
              <Campo label="Método">
                <select value={metodo} onChange={(e) => setMetodo(e.target.value)}>
                  <option value="DINHEIRO">Dinheiro</option><option value="CARTAO">Cartão</option><option value="PIX">PIX (verificar manualmente)</option>
                </select>
              </Campo>
              {metodo === 'DINHEIRO' && (
                <Campo label="Valor recebido"><input type="number" step="0.01" min="0" value={recebido} onChange={(e) => setRecebido(e.target.value)} /></Campo>
              )}
              <button className="btn" disabled={acao.ocupado}
                onClick={() => rodar(() => post(`/api/gestao/mesas/fechamentos/${fech.id}/pagamento`,
                  { metodo, valorRecebido: metodo === 'DINHEIRO' && recebido ? Number(recebido) : null }))}>
                Confirmar pagamento e encerrar conta
              </button>
            </div>
          )}
          <div className="linha">
            <Campo label="Justificativa para voltar ao consumo">
              <input value={justificativa} onChange={(e) => setJustificativa(e.target.value)} />
            </Campo>
            <button className="btn sec" disabled={acao.ocupado || !justificativa.trim()}
              onClick={() => rodar(() => post(`/api/gestao/mesas/${c.codigoConta}/fechamento/cancelar`, { justificativa }))}>
              Cancelar fechamento
            </button>
          </div>
        </>
      )}

      {c.estado === 'ENCERRADA' && fech && (
        <p>Encerrada em {dataHora(c.encerradaEm)} · pago {brl(fech.total)}{fech.pagamento && ` (${rotulo(fech.pagamento.metodo)})`}. Contas encerradas não são reabertas na V1.</p>
      )}

      <h3>Histórico da conta</h3>
      <ul className="linha-tempo">
        {c.historico.map((h, i) => (
          <li key={i}><strong>{rotulo(h.estadoNovo)}</strong> <small>{dataHora(h.em)}{h.funcionarioId ? ` · funcionário #${h.funcionarioId}` : ''}</small>
            {h.justificativa && <div className="pequeno mudo">“{h.justificativa}”</div>}</li>
        ))}
      </ul>
      {pedidoAberto && <PedidoDetalhe id={pedidoAberto} onFechar={() => { setPedidoAberto(null); recarregar(); onAtualizou() }} />}
    </section>
  )
}

export default function Mesas() {
  const [estado, setEstado] = useState('')
  const [codigo, setCodigo] = useState('')
  const [busca, setBusca] = useState('')
  const { dados, erro, recarregar } = useCarga(() => get(`/api/gestao/mesas${estado ? `?estado=${estado}` : ''}`), [estado], 15000)

  return (
    <>
      <h1>Contas de mesa</h1>
      <div className="filtros">
        <label className="campo"><span>Estado</span>
          <select value={estado} onChange={(e) => setEstado(e.target.value)}>
            <option value="">Todas</option><option value="ABERTA">Abertas</option>
            <option value="EM_FECHAMENTO">Em fechamento</option><option value="ENCERRADA">Encerradas</option>
          </select>
        </label>
        <Campo label="Localizar pelo código da conta">
          <input value={busca} onChange={(e) => setBusca(e.target.value.toUpperCase())} placeholder="Ex.: M7K2QX" />
        </Campo>
        <button className="btn" disabled={!busca} onClick={() => setCodigo(busca)}>Abrir</button>
      </div>
      <Erro erro={erro} />
      <div className="duas-colunas assimetrica">
        <table>
          <thead><tr><th>Código</th><th>Responsável</th><th>Estado</th><th>Total</th></tr></thead>
          <tbody>
            {dados?.map((c) => (
              <tr key={c.id} className={`clicavel ${c.codigoConta === codigo ? 'sel' : ''}`} onClick={() => setCodigo(c.codigoConta)}>
                <td><strong>{c.codigoConta}</strong></td><td>{c.nomeResponsavel}</td><td><Badge valor={c.estado} /></td><td>{brl(c.total)}</td>
              </tr>
            ))}
            {dados?.length === 0 && <tr><td colSpan="4" className="mudo">Nenhuma conta.</td></tr>}
          </tbody>
        </table>
        {codigo ? <ContaDetalhe key={codigo} codigo={codigo} onAtualizou={recarregar} /> : <p className="mudo">Selecione uma conta ou informe o código.</p>}
      </div>
    </>
  )
}
