import { useEffect, useState } from 'react'
import { get, post } from '../api.js'
import Icon from '../components/Icon.jsx'
import { useMontagem } from '../montagem.js'
import { Erro, brl, useAcao } from '../ui.jsx'
import { useCarrinho } from './carrinho.jsx'
import { useCliente } from './cliente.jsx'
import { useLoja } from './lojaStatus.jsx'

function Passo({ valor, min = 0, max, aoMudar, rotulo }) {
  return (
    <div className="lj-qtd" role="group" aria-label={rotulo}>
      <button type="button" disabled={valor <= min} onClick={() => aoMudar(valor - 1)} aria-label="Diminuir"><Icon nome="minus" tamanho={16} /></button>
      <span>{valor}</span>
      <button type="button" disabled={max != null && valor >= max} onClick={() => aoMudar(valor + 1)} aria-label="Aumentar"><Icon nome="plus" tamanho={16} /></button>
    </div>
  )
}

/**
 * Montagem de um produto: grupos de opções (sabores/ingredientes cadastrados), adicionais e quantidade.
 * O preço vem do servidor a cada alteração; o botão só habilita com uma cotação válida e a loja aberta.
 */
export default function ProdutoModal({ produto, salvaId, onFechar }) {
  const { adicionar } = useCarrinho()
  const { cliente } = useCliente()
  const { aberta } = useLoja()
  const [salvas, setSalvas] = useState([])
  const [nomeSalva, setNomeSalva] = useState('')
  const [salvou, setSalvou] = useState(false)
  const [paginaGrupo, setPaginaGrupo] = useState(0)
  const acaoSalvar = useAcao()
  const m = useMontagem(produto, { aoAlterar: () => setSalvou(false) })
  const { grupos, adicionaisDisp, quantidade, opcoes, adicionais, selecao, cotacao, problema } = m
  const paginas = [
    ...grupos.map((grupo) => ({ id: `grupo-${grupo.id}`, tipo: 'grupo', grupo })),
    ...(adicionaisDisp.length > 0 ? [{ id: 'adicionais', tipo: 'adicionais' }] : []),
  ]
  const paginaAtual = paginas[paginaGrupo] ?? paginas[0]
  const grupoAtual = paginaAtual?.tipo === 'grupo' ? paginaAtual.grupo : null
  const totalAtual = grupoAtual ? m.totalDoGrupo(grupoAtual) : 0
  const escolhaUnica = grupoAtual?.maximo === 1

  useEffect(() => {
    setPaginaGrupo((atual) => Math.min(atual, Math.max(paginas.length - 1, 0)))
  }, [paginas.length])

  // Pizzas salvas do cliente para este produto
  useEffect(() => {
    if (!cliente || grupos.length === 0) return
    get('/api/cliente/pizzas').then((lista) => {
      const doProduto = lista.filter((p) => p.produtoId === produto.id)
      setSalvas(doProduto)
      const inicial = salvaId && doProduto.find((p) => p.id === salvaId)
      if (inicial) aplicarSalva(inicial)
    }).catch(() => {})
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [cliente, produto.id])

  const aplicarSalva = (p) => {
    m.aplicarEscolhas(p.escolhas)
    setNomeSalva(p.nome)
  }

  const salvar = () => acaoSalvar.executar(async () => {
    const r = await post('/api/cliente/pizzas', { nome: nomeSalva.trim(), produtoId: produto.id, escolhas: selecao.escolhas })
    setSalvas((l) => [r, ...l])
    setSalvou(true)
  })

  const incluir = () => {
    adicionar(selecao, cotacao)
    onFechar()
  }

  useEffect(() => {
    const fn = (e) => e.key === 'Escape' && onFechar()
    window.addEventListener('keydown', fn)
    return () => window.removeEventListener('keydown', fn)
  }, [onFechar])

  return (
    <div className="lj-modal-fundo" onMouseDown={(e) => e.target === e.currentTarget && onFechar()}>
      <div className="lj-modal" role="dialog" aria-modal="true" aria-label={produto.nome}>
        <button className="lj-modal-fechar" onClick={onFechar} aria-label="Fechar"><Icon nome="x" /></button>
        <div className="lj-modal-corpo">
          <div className="lj-modal-capa" style={produto.imagemUrl ? { backgroundImage: `url(${produto.imagemUrl})` } : undefined}>
            {!produto.imagemUrl && <Icon nome="pizza" tamanho={56} />}
          </div>
          <div className="lj-modal-conteudo">
            <small className="lj-categoria-rotulo">{produto.categoriaNome}</small>
            <h2>{produto.nome}</h2>
            {produto.descricao && <p className="mudo">{produto.descricao}</p>}

            {salvas.length > 0 && (
              <label className="campo">
                <span>Usar uma das minhas pizzas salvas</span>
                <select value="" onChange={(e) => { const p = salvas.find((x) => String(x.id) === e.target.value); if (p) aplicarSalva(p) }}>
                  <option value="">Escolher…</option>
                  {salvas.map((p) => <option key={p.id} value={p.id}>{p.nome}</option>)}
                </select>
              </label>
            )}

            {paginas.length > 0 && (
              <section className="lj-grupos-carrossel" aria-label="Opções do produto">
                {paginas.length > 1 && (
                  <nav className="lj-grupos-navegacao" aria-label="Navegar pelos grupos de opções">
                    <button
                      type="button"
                      className="lj-grupo-seta"
                      disabled={paginaGrupo === 0}
                      onClick={() => setPaginaGrupo((pagina) => pagina - 1)}
                      aria-label="Grupo anterior"
                    >
                      <span aria-hidden="true">‹</span>
                    </button>
                    <div className="lj-grupo-indicador" aria-live="polite">
                      <small>Etapa {paginaGrupo + 1} de {paginas.length}</small>
                      <strong>{grupoAtual?.nome ?? 'Adicionais'}</strong>
                    </div>
                    <button
                      type="button"
                      className="lj-grupo-seta"
                      disabled={paginaGrupo === paginas.length - 1}
                      onClick={() => setPaginaGrupo((pagina) => pagina + 1)}
                      aria-label="Próximo grupo"
                    >
                      <span aria-hidden="true">›</span>
                    </button>
                  </nav>
                )}

                <div key={paginaAtual.id} className="lj-grupo-pagina">
                  {grupoAtual && (
                    <fieldset className="lj-grupo">
                  <legend>
                        <strong>{grupoAtual.nome}</strong>
                        <small className={totalAtual >= grupoAtual.minimo ? 'ok' : 'falta'}>
                          {grupoAtual.maximo === grupoAtual.minimo ? `Escolha ${grupoAtual.minimo}` : `Escolha de ${grupoAtual.minimo} a ${grupoAtual.maximo}`}{grupoAtual.maximo > 1 && ` · ${totalAtual} selecionado${totalAtual === 1 ? '' : 's'}`}
                    </small>
                  </legend>
                      {grupoAtual.opcoes.filter((o) => o.disponivel).map((o) => {
                    const q = opcoes[o.id] ?? 0
                        const cobra = Number(o.preco) > 0 && grupoAtual.modalidadeCobranca !== 'GRATUITA'
                    return (
                      <div key={o.id} className={`lj-opcao ${q > 0 ? 'marcada' : ''}`}>
                            {escolhaUnica || o.maximo <= 1 ? (
                              <button type="button" className="lj-opcao-btn" role={escolhaUnica ? 'radio' : 'checkbox'} aria-checked={q > 0} onClick={() => m.definir(grupoAtual, o, q > 0 ? 0 : 1)}>
                                <span className={`lj-marca ${escolhaUnica ? 'redonda' : ''}`}>{q > 0 && <Icon nome="check" tamanho={14} />}</span>
                            <span className="lj-opcao-nome">{o.nome}</span>
                            {cobra && <span className="lj-opcao-preco">+ {brl(o.preco)}</span>}
                          </button>
                        ) : (
                          <>
                            <span className="lj-opcao-btn estatico">
                              <span className="lj-opcao-nome">{o.nome}</span>
                              {cobra && <span className="lj-opcao-preco">+ {brl(o.preco)}</span>}
                            </span>
                                <Passo valor={q} max={o.maximo} aoMudar={(v) => m.definir(grupoAtual, o, v)} rotulo={o.nome} />
                          </>
                        )}
                      </div>
                    )
                  })}
                </fieldset>
                  )}

                  {paginaAtual.tipo === 'adicionais' && (
                    <fieldset className="lj-grupo">
                      <legend><strong>Adicionais</strong><small>opcional</small></legend>
                      {adicionaisDisp.map((a) => (
                        <div key={a.id} className={`lj-opcao ${(adicionais[a.id] ?? 0) > 0 ? 'marcada' : ''}`}>
                          <span className="lj-opcao-btn estatico">
                            <span className="lj-opcao-nome">{a.nome}</span>
                            <span className="lj-opcao-preco">+ {brl(a.preco)}</span>
                          </span>
                          <Passo valor={adicionais[a.id] ?? 0} aoMudar={(v) => m.definirAdicional(a.id, v)} rotulo={a.nome} />
                        </div>
                      ))}
                    </fieldset>
                  )}
                </div>

                {paginas.length > 1 && (
                  <div className="lj-grupo-pontos" aria-hidden="true">
                    {paginas.map((pagina, indice) => (
                      <span key={pagina.id} className={indice === paginaGrupo ? 'ativo' : ''} />
                    ))}
                  </div>
                )}
              </section>
            )}

            {cliente && grupos.length > 0 && (
              <div className="lj-salvar">
                <label className="campo"><span>Salvar esta montagem como</span>
                  <input value={nomeSalva} maxLength={100} onChange={(e) => { setNomeSalva(e.target.value); setSalvou(false) }} placeholder="Ex.: Minha favorita" />
                </label>
                <button type="button" className="btn contorno" disabled={!nomeSalva.trim() || selecao.escolhas.length === 0 || acaoSalvar.ocupado} onClick={salvar}>
                  {salvou ? 'Salva ✓' : 'Salvar'}
                </button>
              </div>
            )}
            <Erro erro={acaoSalvar.erro} />
          </div>
        </div>

        <footer className="lj-modal-base">
          <Passo valor={quantidade} min={1} aoMudar={m.setQuantidade} rotulo="Quantidade" />
          <div className="lj-modal-preco">
            {cotacao ? (
              <>
                <strong>{brl(cotacao.item.subtotal)}</strong>
                {quantidade > 1 && <small className="mudo">{brl(cotacao.item.precoUnitario)} cada</small>}
              </>
            ) : problema ? (
              <small className="falta" role="status">{problema.message}</small>
            ) : <small className="mudo">Calculando…</small>}
          </div>
          <button className="btn grande" disabled={!cotacao || !aberta} onClick={incluir} title={aberta ? undefined : 'A pizzaria está fechada'}>
            {aberta ? 'Adicionar' : 'Fechado'}
          </button>
        </footer>
      </div>
    </div>
  )
}
