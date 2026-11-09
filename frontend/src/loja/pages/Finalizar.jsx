import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { get, post } from '../../api.js'
import Icon from '../../components/Icon.jsx'
import { Campo, Erro, brl, useAcao } from '../../ui.jsx'
import { useCarrinho } from '../carrinho.jsx'
import { useLoja } from '../lojaStatus.jsx'
import { carregarPerfil, useCliente } from '../cliente.jsx'
import EnderecoForm from '../EnderecoForm.jsx'

const PAGAMENTOS = { PIX: 'Pix', CARTAO: 'Cartão', DINHEIRO: 'Dinheiro' }

function Resumo({ itens, subtotal, taxa, total }) {
  return (
    <aside className="lj-resumo lj-cartao">
      <h2>Resumo</h2>
      <ul className="lj-itens compacto">
        {itens.map((i) => (
          <li key={i.uid}>
            <div className="lj-item-topo"><strong>{i.quantidade}× {i.nomeProduto}</strong><span>{brl(i.subtotal)}</span></div>
            {i.resumo.map((l) => <small key={l} className="mudo">{l}</small>)}
          </li>
        ))}
      </ul>
      <div className="lj-total"><span>Subtotal</span><span>{brl(subtotal)}</span></div>
      {taxa != null && <div className="lj-total"><span>Taxa de entrega</span><span>{brl(taxa)}</span></div>}
      <div className="lj-total forte"><span>Total</span><strong>{brl(total)}</strong></div>
    </aside>
  )
}

export default function Finalizar() {
  const navigate = useNavigate()
  const { cliente } = useCliente()
  const { aberta } = useLoja()
  const { itens, total: subtotal, modalidade, setModalidade, mesa, revalidar, limpar } = useCarrinho()
  const [mudancas, setMudancas] = useState([])
  const [pronto, setPronto] = useState(false)
  const acaoRev = useAcao()
  const envio = useAcao()
  const chaveEnvio = useRef(crypto.randomUUID())

  const [nome, setNome] = useState('')
  const [cpf, setCpf] = useState('')
  const [pagamento, setPagamento] = useState('')
  const [observacao, setObservacao] = useState('')

  const [enderecos, setEnderecos] = useState(null)
  const [enderecoId, setEnderecoId] = useState(null)
  const [novoEndereco, setNovoEndereco] = useState(false)
  const [area, setArea] = useState(null)
  const [erroArea, setErroArea] = useState(null)

  // Sem modalidade escolhida: usa a mesa aberta, se houver; senão retirada.
  const efetiva = modalidade ?? (mesa ? 'MESA' : 'RETIRADA')

  // Cotações vencidas são refeitas antes de pedir; mudanças de preço são avisadas.
  useEffect(() => {
    if (itens.length === 0) { setPronto(true); return }
    acaoRev.executar(async () => {
      const r = await revalidar()
      setMudancas(r.mudancas)
    }).finally(() => setPronto(true))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // Dados do cliente logado para retirada
  useEffect(() => {
    if (!cliente) return
    carregarPerfil().then((p) => { setNome((n) => n || p.nome); setCpf((c) => c || p.cpf || '') }).catch(() => {})
  }, [cliente])

  // Endereços para entrega
  useEffect(() => {
    if (efetiva !== 'ENTREGA' || !cliente) return
    get('/api/cliente/enderecos').then((lista) => {
      setEnderecos(lista)
      setEnderecoId((atual) => atual ?? (lista.find((e) => e.padrao) ?? lista[0])?.id ?? null)
    }).catch((e) => setErroArea(e))
  }, [efetiva, cliente])

  const endereco = enderecos?.find((e) => e.id === enderecoId)
  useEffect(() => {
    setArea(null); setErroArea(null)
    if (efetiva !== 'ENTREGA' || !endereco) return
    const q = new URLSearchParams({ bairro: endereco.bairro, cidade: endereco.cidade, uf: endereco.estado })
    get(`/api/public/entrega/area?${q}`).then(setArea).catch(setErroArea)
  }, [efetiva, endereco?.id])

  const taxa = efetiva === 'ENTREGA' && area?.atendida ? Number(area.taxaEntrega) : null
  const total = subtotal + (taxa ?? 0)

  const entregaPronta = efetiva !== 'ENTREGA' || (cliente && endereco && area?.atendida)
  const dadosPronto = efetiva === 'RETIRADA' ? nome.trim() && cpf.trim() : true
  const podeEnviar = aberta && pronto && itens.length > 0 && entregaPronta && dadosPronto && !envio.ocupado

  const enviar = (e) => {
    e.preventDefault()
    envio.executar(async () => {
      // Se algo venceu entre a revisão e o clique, cota de novo e deixa o cliente conferir.
      const { itens: atuais, mudancas: novas } = await revalidar()
      if (novas.length) { setMudancas(novas); throw new Error('O preço de algum item mudou. Confira o resumo e confirme de novo.') }
      const itensReq = atuais.map((i) => ({ ...i.selecao, cotacaoToken: i.cotacaoToken }))
      let resposta
      if (efetiva === 'MESA') {
        resposta = await post(`/api/public/mesas/${mesa.codigo}/pedidos`, { cpfResponsavel: mesa.cpf, itens: itensReq, observacao: observacao || null }, { 'Idempotency-Key': chaveEnvio.current })
      } else {
        resposta = await post('/api/public/pedidos', {
          modalidade: efetiva,
          itens: itensReq,
          enderecoId: efetiva === 'ENTREGA' ? endereco.id : null,
          nomeResponsavel: efetiva === 'RETIRADA' ? nome.trim() : null,
          cpfResponsavel: efetiva === 'RETIRADA' ? cpf : null,
          metodoPagamento: pagamento || null,
          observacao: observacao || null,
        }, { 'Idempotency-Key': chaveEnvio.current })
      }
      limpar()
      chaveEnvio.current = crypto.randomUUID()
      navigate('/pedido-feito', { state: resposta, replace: true })
    })
  }

  if (itens.length === 0 && pronto) {
    return (
      <div className="lj-pagina estreita lj-centro">
        <div className="lj-cartao">
          <Icon nome="cart" tamanho={40} />
          <h1 className="lj-titulo-pagina">Seu carrinho está vazio</h1>
          <Link className="btn" to="/cardapio">Ver o cardápio</Link>
        </div>
      </div>
    )
  }

  return (
    <div className="lj-pagina">
      <h1 className="lj-titulo-pagina">Finalizar pedido</h1>
      <div className="lj-checkout">
        <form onSubmit={enviar} className="lj-checkout-form">
          {mudancas.length > 0 && (
            <div className="alerta aviso" role="status">
              <strong>Os preços foram atualizados:</strong>
              <ul>{mudancas.map((m) => <li key={m.nome}>{m.nome}: de {brl(m.de)} para {brl(m.para)}</li>)}</ul>
            </div>
          )}
          <Erro erro={acaoRev.erro} />

          <section className="lj-cartao">
            <h2>Como você quer receber?</h2>
            <div className="lj-modalidades" role="radiogroup">
              {[['ENTREGA', 'truck', 'Entrega'], ['RETIRADA', 'bag', 'Retirar no local'], ['MESA', 'store', 'Na mesa']].map(([id, icone, rotulo]) => (
                <button key={id} type="button" role="radio" aria-checked={efetiva === id} className={`lj-modalidade ${efetiva === id ? 'ativa' : ''}`}
                  disabled={id === 'MESA' && !mesa} onClick={() => setModalidade(id)}>
                  <Icon nome={icone} tamanho={24} /><span>{rotulo}</span>
                  {id === 'MESA' && !mesa && <small>abra uma conta na aba “Na mesa”</small>}
                </button>
              ))}
            </div>
          </section>

          {efetiva === 'ENTREGA' && (
            <section className="lj-cartao">
              <h2>Endereço de entrega</h2>
              {!cliente ? (
                <div className="lj-aviso-login">
                  <p>A entrega exige uma conta. Entre ou crie a sua para continuar — seu carrinho fica guardado.</p>
                  <div className="lj-acoes">
                    <Link className="btn" to="/entrar?volta=/finalizar">Entrar</Link>
                    <Link className="btn contorno" to="/entrar?modo=cadastro&volta=/finalizar">Criar conta</Link>
                  </div>
                </div>
              ) : (
                <>
                  {enderecos?.length === 0 && !novoEndereco && <p className="mudo">Você ainda não tem endereços salvos.</p>}
                  <div className="lj-enderecos">
                    {enderecos?.map((e) => (
                      <label key={e.id} className={`lj-endereco ${e.id === enderecoId ? 'ativo' : ''}`}>
                        <input type="radio" name="endereco" checked={e.id === enderecoId} onChange={() => setEnderecoId(e.id)} />
                        <span>
                          <strong>{e.identificacao || 'Endereço'}{e.padrao && <em> · padrão</em>}</strong>
                          <small>{e.logradouro}, {e.numero}{e.complemento ? ` — ${e.complemento}` : ''}</small>
                          <small>{e.bairro} · {e.cidade}/{e.estado} · CEP {e.cep}</small>
                        </span>
                      </label>
                    ))}
                  </div>
                  {novoEndereco ? (
                    <EnderecoForm
                      aoCancelar={() => setNovoEndereco(false)}
                      aoSalvar={(salvo) => { setEnderecos((l) => [...(l ?? []), salvo]); setEnderecoId(salvo.id); setNovoEndereco(false) }} />
                  ) : (
                    <button type="button" className="btn contorno" onClick={() => setNovoEndereco(true)}><Icon nome="plus" tamanho={16} /> Novo endereço</button>
                  )}
                  {endereco && area && (
                    area.atendida
                      ? <p className="lj-ok"><Icon nome="check" tamanho={16} /> Atendemos esse endereço. Taxa de entrega: <strong>{brl(area.taxaEntrega)}</strong></p>
                      : <p className="lj-falta"><Icon nome="x" tamanho={16} /> Esse endereço está fora da nossa área de entrega. Escolha outro ou opte por retirada.</p>
                  )}
                  <Erro erro={erroArea} />
                </>
              )}
            </section>
          )}

          {efetiva === 'RETIRADA' && (
            <section className="lj-cartao">
              <h2>Quem vai retirar?</h2>
              <div className="lj-form-grade">
                <Campo label="Nome"><input value={nome} onChange={(e) => setNome(e.target.value)} required maxLength={150} /></Campo>
                <Campo label="CPF"><input value={cpf} onChange={(e) => setCpf(e.target.value)} required inputMode="numeric" placeholder="000.000.000-00" /></Campo>
              </div>
              {!cliente && <p className="mudo pequeno">Não precisa de conta para retirar. <Link to="/entrar?volta=/finalizar">Entrar</Link></p>}
            </section>
          )}

          {efetiva === 'MESA' && mesa && (
            <section className="lj-cartao">
              <h2>Pedido na mesa</h2>
              <p>Conta <strong>{mesa.codigo}</strong> · responsável <strong>{mesa.nome}</strong>. O pagamento é feito no fechamento da conta.</p>
            </section>
          )}

          {efetiva !== 'MESA' && (
            <section className="lj-cartao">
              <h2>Forma de pagamento</h2>
              <div className="lj-chips">
                <button type="button" className={!pagamento ? 'ativo' : ''} onClick={() => setPagamento('')}>Não informar agora</button>
                {Object.entries(PAGAMENTOS).map(([id, r]) => (
                  <button key={id} type="button" className={pagamento === id ? 'ativo' : ''} onClick={() => setPagamento(id)}>{r}</button>
                ))}
              </div>
            </section>
          )}

          <section className="lj-cartao">
            <Campo label="Observação (opcional)"><textarea rows={2} value={observacao} onChange={(e) => setObservacao(e.target.value)} maxLength={500} /></Campo>
          </section>

          <Erro erro={envio.erro} />
          {!aberta && <p className="alerta erro" role="status">Estamos fechados no momento: não é possível confirmar pedidos agora. Seu carrinho fica guardado.</p>}
          <button className="btn grande" disabled={!podeEnviar}>{envio.ocupado ? 'Enviando pedido…' : `Confirmar pedido · ${brl(total)}`}</button>
        </form>

        <Resumo itens={itens} subtotal={subtotal} taxa={taxa} total={total} />
      </div>
    </div>
  )
}
