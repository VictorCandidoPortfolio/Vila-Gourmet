import { useEffect, useState } from 'react'
import { get, post } from '../api.js'
import { useAuth } from '../auth.jsx'
import { Badge, Campo, Erro, Modal, brl, dataHora, rotulo, useAcao, useCarga } from '../ui.jsx'
import MontadorItem from '../components/MontadorItem.jsx'
import PedidoDetalhe from '../components/PedidoDetalhe.jsx'

const telaEstreita = '(max-width: 860px)'

function ContaDetalhe({ codigo, onAtualizou, onFechar, mobile }) {
  const { pode } = useAuth()
  const { dados: c, erro, carregando, recarregar } = useCarga(() => get(`/api/caixa/mesas/${codigo}`), [codigo], 15000)
  const acao = useAcao()
  const [novos, setNovos] = useState([])
  const [pedidoAberto, setPedidoAberto] = useState(null)
  const [metodo, setMetodo] = useState('DINHEIRO')
  const [recebido, setRecebido] = useState('')
  const [justificativa, setJustificativa] = useState('')
  const [motivoEncerramento, setMotivoEncerramento] = useState('')

  const rodar = (fn) => acao.executar(async () => { await fn(); await recarregar(); onAtualizou() })

  const aberta = c?.estado === 'ABERTA'
  const fech = c?.fechamento
  const semConsumo = c?.pedidos.every((p) => p.estado === 'CANCELADO')

  const encerrarSemFechamento = () => {
    if (!window.confirm('Encerrar esta mesa sem fechamento e sem cobrança? Esta ação não reabre a conta.')) return
    rodar(() => post(`/api/caixa/mesas/${c.codigoConta}/encerrar-sem-fechamento`, { justificativa: motivoEncerramento.trim() }))
  }

  const conteudo = c ? (
    <section className="cartao">
      <div className="linha entre">
        <h2>Conta {c.codigoConta} <Badge valor={c.estado} /></h2>
        <strong className="grande">{brl(c.total)}</strong>
      </div>
      <p>{c.nomeResponsavel} <small className="mudo">{c.cpfResponsavel && `(${c.cpfResponsavel}) · `}aberta em {dataHora(c.abertaEm)}</small></p>
      <Erro erro={acao.erro} />

      <h3>Pedidos da conta</h3>
      <table className="tabela-mobile">
        <thead><tr><th>Número</th><th>Estado</th><th>Itens</th><th>Total</th></tr></thead>
        <tbody>
          {c.pedidos.map((p) => (
            <tr key={p.id} className="clicavel" onClick={() => setPedidoAberto(p.id)}>
              <td data-label="Pedido"><strong>{p.numeroPedido}</strong></td><td data-label="Estado"><Badge valor={p.estado} /></td>
              <td data-label="Itens">{p.itens.map((i) => `${i.quantidade}x ${i.nomeProduto}`).join(', ')}</td><td data-label="Total">{brl(p.total)}</td>
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
                    await post(`/api/caixa/mesas/${c.codigoConta}/pedidos`, { itens: novos.map((n) => n.item) })
                    setNovos([])
                  })}>Lançar pedido na conta</button>
                <button className="btn sec" onClick={() => setNovos([])}>Limpar</button>
              </div>
            </>
          )}
          <h3>Fechamento</h3>
          <p className="mudo">Só é possível fechar quando todos os pedidos não cancelados estiverem servidos.</p>
          <button className="btn" disabled={acao.ocupado} onClick={() => rodar(() => post(`/api/caixa/mesas/${c.codigoConta}/fechamento`))}>
            Iniciar fechamento da conta
          </button>
          {semConsumo && (
            <div className="linha">
              <Campo label="Motivo do encerramento sem cobrança">
                <input value={motivoEncerramento} maxLength={500} onChange={(e) => setMotivoEncerramento(e.target.value)}
                  placeholder="Ex.: conta aberta por engano" />
              </Campo>
              <button className="btn perigo" disabled={acao.ocupado || !motivoEncerramento.trim()} onClick={encerrarSemFechamento}>
                Encerrar mesa sem fechamento
              </button>
            </div>
          )}
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
                onClick={() => rodar(() => post(`/api/caixa/mesas/fechamentos/${fech.id}/pagamento`,
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
              onClick={() => rodar(() => post(`/api/caixa/mesas/${c.codigoConta}/fechamento/cancelar`, { justificativa }))}>
              Cancelar fechamento
            </button>
          </div>
        </>
      )}

      {c.estado === 'ENCERRADA' && fech && (
        <p>Encerrada em {dataHora(c.encerradaEm)} · pago {brl(fech.total)}{fech.pagamento && ` (${rotulo(fech.pagamento.metodo)})`}. Contas encerradas não são reabertas na V1.</p>
      )}
      {c.estado === 'ENCERRADA' && !fech && (
        <p>Encerrada sem fechamento e sem cobrança em {dataHora(c.encerradaEm)}. Contas encerradas não são reabertas na V1.</p>
      )}

      <h3>Histórico da conta</h3>
      <ul className="linha-tempo">
        {c.historico.map((h, i) => (
          <li key={i}><strong>{rotulo(h.estadoNovo)}</strong> <small>{dataHora(h.em)}{h.funcionarioId ? ` · funcionário #${h.funcionarioId}` : ''}</small>
            {h.justificativa && <div className="pequeno mudo">“{h.justificativa}”</div>}</li>
        ))}
      </ul>
    </section>
  ) : (
    <>
      {carregando && <p className="mudo" role="status">Carregando informações da mesa…</p>}
      <Erro erro={erro} />
    </>
  )

  return (
    <>
      {mobile ? (
        <div className="mesa-detalhe-mobile">
          <Modal titulo={`Detalhes da mesa ${codigo}`} onFechar={onFechar} semEsc={pedidoAberto !== null} largo>
            {conteudo}
          </Modal>
        </div>
      ) : conteudo}
      {pedidoAberto && <PedidoDetalhe id={pedidoAberto} onFechar={() => { setPedidoAberto(null); recarregar(); onAtualizou() }} />}
    </>
  )
}

export default function Mesas() {
  const [estado, setEstado] = useState('')
  const [codigo, setCodigo] = useState('')
  const [busca, setBusca] = useState('')
  const [mobile, setMobile] = useState(() => window.matchMedia(telaEstreita).matches)
  const { dados, erro, recarregar } = useCarga(() => get(`/api/caixa/mesas${estado ? `?estado=${estado}` : ''}`), [estado], 15000)

  useEffect(() => {
    const media = window.matchMedia(telaEstreita)
    const atualizar = () => setMobile(media.matches)
    media.addEventListener('change', atualizar)
    atualizar()
    return () => media.removeEventListener('change', atualizar)
  }, [])

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
        <table className="tabela-mobile">
          <thead><tr><th>Código</th><th>Responsável</th><th>Estado</th><th>Total</th></tr></thead>
          <tbody>
            {dados?.map((c) => (
              <tr key={c.id} className={`clicavel ${c.codigoConta === codigo ? 'sel' : ''}`} onClick={() => setCodigo(c.codigoConta)}>
                <td data-label="Código">
                  <button type="button" className="btn sec" onClick={(e) => { e.stopPropagation(); setCodigo(c.codigoConta) }}
                    aria-label={`Abrir detalhes da mesa ${c.codigoConta}`}>{c.codigoConta}</button>
                </td>
                <td data-label="Responsável">{c.nomeResponsavel}</td><td data-label="Estado"><Badge valor={c.estado} /></td><td data-label="Total">{brl(c.total)}</td>
              </tr>
            ))}
            {dados?.length === 0 && <tr><td colSpan="4" className="mudo">Nenhuma conta.</td></tr>}
          </tbody>
        </table>
        {codigo ? (
          <ContaDetalhe key={codigo} codigo={codigo} onAtualizou={recarregar} onFechar={() => setCodigo('')} mobile={mobile} />
        ) : !mobile && <p className="mudo">Selecione uma conta ou informe o código.</p>}
      </div>
    </>
  )
}
