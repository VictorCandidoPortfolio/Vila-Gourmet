import { useEffect, useRef, useState } from 'react'
import { get, post } from '../api.js'
import { corpoCotacao } from '../montagem.js'
import Icon from './Icon.jsx'
import { Campo, Erro, Modal, brl, rotulo, useAcao } from '../ui.jsx'

const PAGAMENTOS = { PIX: 'Pix', CARTAO: 'Cartão', DINHEIRO: 'Dinheiro' }
const VAZIO_END = { cep: '', logradouro: '', numero: '', complemento: '', bairro: '', cidade: '', estado: '' }

const mascaraCep = (v) => {
  const d = v.replace(/\D/g, '').slice(0, 8)
  return d.length > 5 ? `${d.slice(0, 5)}-${d.slice(5)}` : d
}

/**
 * Painel do pedido na frente de caixa: itens, modalidade (retirada, entrega ou mesa) e envio.
 * Pelo caixa basta o nome do cliente (sem cadastro); a entrega usa o endereço digitado, que deve estar na área atendida.
 */
export default function PdvPedido({ itens, setItens, onLancado }) {
  const [modalidade, setModalidade] = useState('RETIRADA')
  const [nome, setNome] = useState('')
  const [pagamento, setPagamento] = useState('')
  const [observacao, setObservacao] = useState('')
  // entrega
  const [end, setEnd] = useState(VAZIO_END)
  const [cepMsg, setCepMsg] = useState('')
  const [area, setArea] = useState(null)
  const [erroArea, setErroArea] = useState(null)
  const consultaCep = useRef(0)
  // mesa
  const [contas, setContas] = useState(null)
  const [codigoConta, setCodigoConta] = useState('')
  const [novaMesa, setNovaMesa] = useState(false)
  const [nomeMesa, setNomeMesa] = useState('')
  const [avisoPrecos, setAvisoPrecos] = useState([])

  const mesaAcao = useAcao()
  const envio = useAcao()
  const chave = useRef(crypto.randomUUID())
  const campoEnd = (k) => ({ value: end[k], onChange: (e) => setEnd({ ...end, [k]: e.target.value }) })

  const subtotal = itens.reduce((s, i) => s + Number(i.subtotal), 0)

  const mudarCep = (valor) => {
    const cep = mascaraCep(valor)
    setEnd((a) => ({ ...a, cep }))
    const minha = ++consultaCep.current
    const digitos = cep.replace(/\D/g, '')
    if (digitos.length !== 8) { setCepMsg(''); return }
    setCepMsg('Buscando…')
    get(`/api/public/cep/${digitos}`).then((r) => {
      if (minha !== consultaCep.current) return
      setEnd((a) => ({ ...a, logradouro: r.logradouro ?? a.logradouro, bairro: r.bairro ?? a.bairro, cidade: r.cidade ?? a.cidade, estado: r.estado ?? a.estado }))
      setCepMsg('Endereço preenchido pelo CEP.')
    }).catch((e) => { if (minha === consultaCep.current) setCepMsg(e.message) })
  }

  // Confere a área atendida (e mostra a taxa) assim que bairro, cidade e UF estiverem preenchidos.
  useEffect(() => {
    setArea(null); setErroArea(null)
    if (modalidade !== 'ENTREGA' || !end.bairro.trim() || !end.cidade.trim() || end.estado.trim().length !== 2) return undefined
    const t = setTimeout(() => {
      const q = new URLSearchParams({ bairro: end.bairro.trim(), cidade: end.cidade.trim(), uf: end.estado.trim() })
      get(`/api/public/entrega/area?${q}`).then(setArea).catch(setErroArea)
    }, 400)
    return () => clearTimeout(t)
  }, [modalidade, end.bairro, end.cidade, end.estado])

  const carregarContas = () => mesaAcao.executar(async () => setContas(await get('/api/gestao/caixa/mesas')))
  useEffect(() => { if (modalidade === 'MESA' && contas === null) carregarContas() }, [modalidade])

  const taxa = modalidade === 'ENTREGA' && area?.atendida ? Number(area.taxaEntrega) : 0
  const total = subtotal + taxa

  const enderecoOk = end.cep.replace(/\D/g, '').length === 8 && end.logradouro.trim() && end.numero.trim()
    && end.bairro.trim() && end.cidade.trim() && end.estado.trim().length === 2
  const dadosOk = modalidade === 'RETIRADA' ? nome.trim()
    : modalidade === 'ENTREGA' ? nome.trim() && enderecoOk && area?.atendida
    : !!codigoConta
  const podeLancar = itens.length > 0 && dadosOk && !envio.ocupado

  const abrirMesa = (e) => {
    e.preventDefault()
    mesaAcao.executar(async () => {
      const c = await post('/api/gestao/caixa/mesas', { nome: nomeMesa.trim() })
      setContas((l) => [c, ...(l ?? [])])
      setCodigoConta(c.codigoConta)
      setNovaMesa(false); setNomeMesa('')
    })
  }

  const lancar = () => envio.executar(async () => {
    // Cotações vencidas são refeitas; se o preço mudou, avisa e deixa o funcionário conferir antes de lançar.
    const limite = Date.now() + 30_000
    const mudancas = []
    const atualizados = await Promise.all(itens.map(async (i) => {
      if (new Date(i.expiraEm).getTime() > limite) return i
      const c = await post('/api/public/cotacoes', corpoCotacao(i.selecao))
      if (Number(c.item.subtotal) !== Number(i.subtotal)) mudancas.push(`${i.nomeProduto}: ${brl(i.subtotal)} → ${brl(c.item.subtotal)}`)
      return { ...i, subtotal: c.item.subtotal, precoUnitario: c.item.precoUnitario, cotacaoToken: c.cotacaoToken, expiraEm: c.expiraEm }
    }))
    setItens(atualizados)
    if (mudancas.length) { setAvisoPrecos(mudancas); throw new Error('O preço de algum item mudou. Confira o total e lance de novo.') }
    setAvisoPrecos([])

    const base = { modalidade, itens: atualizados.map((i) => ({ ...i.selecao, cotacaoToken: i.cotacaoToken })), observacao: observacao || null }
    const corpo = modalidade === 'ENTREGA'
      ? { ...base, nomeResponsavel: nome.trim(), endereco: { ...end, estado: end.estado.toUpperCase(), complemento: end.complemento.trim() || null }, metodoPagamento: pagamento || null }
      : modalidade === 'MESA' ? { ...base, codigoConta }
      : { ...base, nomeResponsavel: nome.trim(), metodoPagamento: pagamento || null }
    const r = await post('/api/gestao/caixa/pedidos', corpo, { 'Idempotency-Key': chave.current })
    chave.current = crypto.randomUUID()
    onLancado(r)
    setNome(''); setPagamento(''); setObservacao(''); setEnd(VAZIO_END); setCepMsg('')
  })

  return (
    <aside className="pdv-pedido cartao">
      <h2>Pedido</h2>

      {itens.length === 0 ? <p className="mudo">Escolha produtos ao lado para montar o pedido.</p> : (
        <ul className="pdv-itens">
          {itens.map((i) => (
            <li key={i.uid}>
              <div className="pdv-item-topo"><strong>{i.quantidade}× {i.nomeProduto}</strong><span>{brl(i.subtotal)}</span></div>
              {i.resumo.map((l) => <small key={l} className="mudo bloco">{l}</small>)}
              <button type="button" className="pdv-remover" onClick={() => setItens((l) => l.filter((x) => x.uid !== i.uid))}><Icon nome="lixeira" tamanho={14} /> Remover</button>
            </li>
          ))}
        </ul>
      )}

      <div className="pdv-modalidades" role="radiogroup" aria-label="Modalidade">
        {[['RETIRADA', 'bag'], ['ENTREGA', 'truck'], ['MESA', 'store']].map(([id, icone]) => (
          <button key={id} type="button" role="radio" aria-checked={modalidade === id} className={modalidade === id ? 'ativa' : ''} onClick={() => setModalidade(id)}>
            <Icon nome={icone} tamanho={18} /> {rotulo(id)}
          </button>
        ))}
      </div>

      {modalidade !== 'MESA' && (
        <Campo label="Nome do cliente"><input value={nome} onChange={(e) => setNome(e.target.value)} maxLength={150} placeholder="Quem fez o pedido" /></Campo>
      )}

      {modalidade === 'ENTREGA' && (
        <div className="pdv-endereco-form">
          <div className="linha">
            <Campo label="CEP" dica={cepMsg}><input value={end.cep} onChange={(e) => mudarCep(e.target.value)} inputMode="numeric" placeholder="00000-000" /></Campo>
            <Campo label="Número" curto><input {...campoEnd('numero')} maxLength={20} /></Campo>
          </div>
          <Campo label="Rua / avenida"><input {...campoEnd('logradouro')} maxLength={200} /></Campo>
          <div className="linha">
            <Campo label="Bairro"><input {...campoEnd('bairro')} maxLength={100} /></Campo>
            <Campo label="Complemento"><input {...campoEnd('complemento')} maxLength={100} /></Campo>
          </div>
          <div className="linha">
            <Campo label="Cidade"><input {...campoEnd('cidade')} maxLength={100} /></Campo>
            <Campo label="UF" curto><input {...campoEnd('estado')} maxLength={2} style={{ textTransform: 'uppercase' }} /></Campo>
          </div>
          {area && (area.atendida
            ? <p className="pdv-ok"><Icon nome="check" tamanho={14} /> Área atendida · taxa {brl(area.taxaEntrega)}</p>
            : <p className="pdv-falta"><Icon nome="x" tamanho={14} /> Endereço fora da área de entrega.</p>)}
          <Erro erro={erroArea} />
        </div>
      )}

      {modalidade === 'MESA' && (
        <>
          <Erro erro={mesaAcao.erro} />
          {contas && contas.length === 0 && !novaMesa && <p className="mudo">Nenhuma conta de mesa aberta.</p>}
          {contas?.map((c) => (
            <label key={c.codigoConta} className={`pdv-endereco ${c.codigoConta === codigoConta ? 'ativo' : ''}`}>
              <input type="radio" name="pdv-mesa" checked={c.codigoConta === codigoConta} onChange={() => setCodigoConta(c.codigoConta)} />
              <span><strong>{c.codigoConta}</strong> · {c.nomeResponsavel}<small className="mudo bloco">{c.pedidos.length} pedido(s) · {brl(c.total)}</small></span>
            </label>
          ))}
          {novaMesa ? (
            <form onSubmit={abrirMesa}>
              <Campo label="Nome do responsável"><input value={nomeMesa} onChange={(e) => setNomeMesa(e.target.value)} required maxLength={150} autoFocus /></Campo>
              <div className="linha">
                <button type="button" className="btn sec" onClick={() => setNovaMesa(false)}>Cancelar</button>
                <button className="btn" disabled={mesaAcao.ocupado || !nomeMesa.trim()}>Abrir conta</button>
              </div>
            </form>
          ) : (
            <button type="button" className="btn sec" onClick={() => setNovaMesa(true)}><Icon nome="plus" tamanho={14} /> Abrir nova conta</button>
          )}
        </>
      )}

      {modalidade !== 'MESA' && (
        <div className="pdv-pagamento">
          <small className="mudo">Pagamento</small>
          <div className="pdv-chips">
            <button type="button" className={!pagamento ? 'ativo' : ''} onClick={() => setPagamento('')}>Não informar</button>
            {Object.entries(PAGAMENTOS).map(([id, r]) => <button key={id} type="button" className={pagamento === id ? 'ativo' : ''} onClick={() => setPagamento(id)}>{r}</button>)}
          </div>
        </div>
      )}

      <Campo label="Observação"><input value={observacao} onChange={(e) => setObservacao(e.target.value)} maxLength={500} /></Campo>

      {avisoPrecos.length > 0 && <div className="alerta aviso"><strong>Preços atualizados:</strong><ul>{avisoPrecos.map((m) => <li key={m}>{m}</li>)}</ul></div>}
      <Erro erro={envio.erro} />

      <div className="pdv-totais">
        <div><span>Subtotal</span><span>{brl(subtotal)}</span></div>
        {modalidade === 'ENTREGA' && <div><span>Taxa de entrega</span><span>{brl(taxa)}</span></div>}
        <div className="forte"><span>Total</span><strong>{brl(total)}</strong></div>
      </div>
      <button className="btn pdv-lancar" disabled={!podeLancar} onClick={lancar}>{envio.ocupado ? 'Lançando…' : 'Lançar pedido'}</button>
    </aside>
  )
}

/** Confirmação do pedido lançado, com os códigos que só aparecem agora. */
export function PedidoLancado({ resposta, onFechar }) {
  const { pedido, codigoAcompanhamento, codigoConfirmacao } = resposta
  return (
    <Modal titulo="Pedido lançado" onFechar={onFechar}>
      <p><strong>{pedido.numeroPedido}</strong> · {rotulo(pedido.modalidade)} · total {brl(pedido.total)}</p>
      <div className="pdv-codigos">
        {codigoAcompanhamento && <div><small className="mudo">Código de acompanhamento</small><strong>{codigoAcompanhamento}</strong></div>}
        {codigoConfirmacao && <div><small className="mudo">Código de confirmação {pedido.modalidade === 'ENTREGA' ? 'da entrega' : 'da retirada'}</small><strong>{codigoConfirmacao}</strong></div>}
      </div>
      <p className="mudo pequeno">Os códigos só são mostrados agora. {codigoConfirmacao ? 'O código de confirmação é pedido ao cliente na conclusão.' : ''}</p>
      <div className="linha"><button className="btn" onClick={onFechar}>Novo pedido</button></div>
    </Modal>
  )
}
