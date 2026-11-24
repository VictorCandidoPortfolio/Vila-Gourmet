import { useEffect, useState } from 'react'
import { get, post, put } from '../api.js'
import Icon from '../components/Icon.jsx'
import { Badge, Campo, Erro, Modal, brl, dataHora, rotulo, useAcao, useCarga } from '../ui.jsx'

const intervalo = (c) => `${new Date(c.abertoEm).toLocaleDateString('pt-BR')} · ${new Date(c.abertoEm).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })}–${c.fechadoEm ? new Date(c.fechadoEm).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' }) : 'aberto'}`

function EditarVenda({ venda, onFechar, onSalvo }) {
  const [nome, setNome] = useState(venda.nomeResponsavel ?? '')
  const [cpf, setCpf] = useState('')
  const [observacao, setObservacao] = useState(venda.observacao ?? '')
  const [metodo, setMetodo] = useState(venda.pagamento?.metodo ?? '')
  const [justificativa, setJustificativa] = useState('')
  const acao = useAcao()
  const salvar = (e) => {
    e.preventDefault()
    acao.executar(async () => {
      await put(`/api/caixa/pedidos/${venda.id}`, { nomeResponsavel: nome, cpfResponsavel: cpf || null, observacao: observacao || null, metodoPagamento: metodo || null, justificativa })
      onSalvo()
    })
  }
  return <Modal titulo={`Editar venda ${venda.numeroPedido}`} onFechar={onFechar} largo>
    <form onSubmit={salvar}>
      <div className="alerta aviso"><strong>Itens e valores permanecem como foram vendidos.</strong> Esta correção altera somente os dados administrativos da venda.</div>
      <div className="duas-colunas">
        <Campo label="Responsável"><input value={nome} onChange={(e) => setNome(e.target.value)} required /></Campo>
        <Campo label="Novo CPF" dica="Deixe vazio para remover o CPF"><input value={cpf} onChange={(e) => setCpf(e.target.value)} inputMode="numeric" placeholder={venda.cpfResponsavel || 'Opcional'} /></Campo>
        <Campo label="Forma de pagamento"><select value={metodo} onChange={(e) => setMetodo(e.target.value)} disabled={venda.pagamento?.estado === 'CONFIRMADO'}><option value="">Não informada</option><option value="DINHEIRO">Dinheiro</option><option value="CARTAO">Cartão</option><option value="PIX">PIX</option></select></Campo>
        <Campo label="Justificativa da edição"><input value={justificativa} onChange={(e) => setJustificativa(e.target.value)} required placeholder="Informe por que a venda foi corrigida" /></Campo>
      </div>
      <Campo label="Observação"><textarea value={observacao} onChange={(e) => setObservacao(e.target.value)} rows="3" /></Campo>
      <Erro erro={acao.erro} /><div className="linha"><button type="button" className="btn sec" onClick={onFechar}>Cancelar</button><button className="btn" disabled={acao.ocupado}>{acao.ocupado ? 'Salvando…' : 'Salvar correção'}</button></div>
    </form>
  </Modal>
}

function Detalhe({ id, onFechar, onReaberto }) {
  const { dados, erro, recarregar } = useCarga(() => get(`/api/caixa/historico/${id}`), [id])
  const acao = useAcao()
  const [editando, setEditando] = useState(null)
  if (!dados) return <Modal titulo="Sessão de caixa" onFechar={onFechar} largo><Erro erro={erro} />{!erro && 'Carregando…'}</Modal>
  if (editando) return <EditarVenda venda={editando} onFechar={() => setEditando(null)} onSalvo={async () => { setEditando(null); await recarregar() }} />
  const c = dados.caixa
  return <Modal titulo={`Caixa #${c.id} — ${intervalo(c)}`} onFechar={onFechar} largo>
      <div className="linha entre"><div><Badge valor={c.aberto ? 'ABERTA' : 'ENCERRADA'} /> <span className="mudo">{c.quantidadeVendas} vendas · {brl(c.totalVendas)}</span></div>
        {!c.aberto && <button className="btn" disabled={acao.ocupado} onClick={() => acao.executar(async () => { await post(`/api/caixa/historico/${id}/reabrir`); onReaberto() })}>Reabrir este caixa</button>}
      </div>
      {c.quantidadeReaberturas > 0 && <p className="mudo">Esta sessão foi reaberta {c.quantidadeReaberturas} {c.quantidadeReaberturas === 1 ? 'vez' : 'vezes'}.</p>}
      <Erro erro={acao.erro} />
      <section className="painel sem-padding caixa-vendas-historico"><table className="tabela-mobile"><thead><tr><th>Venda</th><th>Modalidade</th><th>Estado</th><th>Responsável</th><th>Total</th><th>Horário</th><th>Correções</th><th /></tr></thead><tbody>
        {dados.vendas.map((v) => <tr key={v.id}><td data-label="Venda"><strong>{v.numeroPedido}</strong>{v.ultimaEdicaoCaixaEm && <small className="edicao-marca">editada</small>}</td><td data-label="Modalidade">{rotulo(v.modalidade)}</td><td data-label="Estado"><Badge valor={v.estado} /></td><td data-label="Responsável">{v.nomeResponsavel}</td><td data-label="Total">{brl(v.total)}</td><td data-label="Horário">{dataHora(v.criadoEm)}</td><td data-label="Correções">{v.edicoesCaixa?.length ? <span title={v.edicoesCaixa.map((e) => `${dataHora(e.em)} — ${e.justificativa}`).join('\n')}>{v.edicoesCaixa.length} {v.edicoesCaixa.length === 1 ? 'edição' : 'edições'}</span> : <span className="mudo">—</span>}</td><td data-label=""><button className="btn sec" onClick={() => setEditando(v)}><Icon nome="settings" tamanho={15} /> Editar</button></td></tr>)}
        {dados.vendas.length === 0 && <tr><td colSpan="8" className="mudo">Nenhuma venda registrada nesta sessão.</td></tr>}
      </tbody></table></section>
    </Modal>
}

export default function CaixaHistorico() {
  const { dados, erro, carregando, recarregar } = useCarga(() => get('/api/caixa/historico'), [])
  const [aberto, setAberto] = useState(null)
  useEffect(() => { if (!aberto) recarregar() }, [aberto])
  return <>
    <h1>Histórico do frente de caixa</h1><p className="mudo">Cada registro reúne as vendas realizadas entre a abertura e o fechamento da sessão.</p>
    <Erro erro={erro} />{carregando && !dados && <p>Carregando…</p>}
    {dados && <section className="painel sem-padding"><table className="tabela-mobile"><thead><tr><th>Caixa</th><th>Período</th><th>Responsáveis</th><th>Vendas</th><th>Total</th><th>Situação</th><th /></tr></thead><tbody>
      {dados.map((c) => <tr key={c.id} className="clicavel" onClick={() => setAberto(c.id)}><td data-label="Caixa"><strong>#{c.id}</strong></td><td data-label="Período">{intervalo(c)}</td><td data-label="Responsáveis">{c.abertoPor || '—'}{c.fechadoPor && c.fechadoPor !== c.abertoPor ? ` / ${c.fechadoPor}` : ''}</td><td data-label="Vendas">{c.quantidadeVendas}</td><td data-label="Total">{brl(c.totalVendas)}</td><td data-label="Situação"><Badge valor={c.aberto ? 'ABERTA' : 'ENCERRADA'} /></td><td data-label=""><Icon nome="eye" tamanho={18} /> Ver sessão</td></tr>)}
      {dados.length === 0 && <tr><td colSpan="7" className="mudo">Nenhuma sessão de caixa foi aberta ainda.</td></tr>}
    </tbody></table></section>}
    {aberto && <Detalhe id={aberto} onFechar={() => setAberto(null)} onReaberto={() => { setAberto(null); recarregar() }} />}
  </>
}
