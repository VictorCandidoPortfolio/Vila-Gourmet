import { useState } from 'react'
import { get, post } from '../api.js'
import { Erro, brl, rotulo, useAcao, useCarga } from '../ui.jsx'

/**
 * Monta um item a partir do catálogo publicado. O preço é sempre calculado pelo servidor
 * (POST /api/public/cotacoes) e fica garantido pelo token da cotação até ele expirar.
 */
export default function MontadorItem({ onAdicionar }) {
  const { dados: catalogo, erro: erroCatalogo } = useCarga(() => get('/api/public/catalogo'), [])
  const [produtoId, setProdutoId] = useState('')
  const [quantidade, setQuantidade] = useState(1)
  const [opcoes, setOpcoes] = useState({})
  const [adicionais, setAdicionais] = useState({})
  const [cotacao, setCotacao] = useState(null)
  const acao = useAcao()

  const produto = catalogo?.find((p) => String(p.id) === String(produtoId))
  const resetar = () => { setOpcoes({}); setAdicionais({}); setCotacao(null) }

  const selecionados = (mapa) => Object.entries(mapa).filter(([, q]) => q > 0).map(([id, q]) => [Number(id), q])

  const cotar = () => acao.executar(async () => {
    const r = await post('/api/public/cotacoes', {
      produtoId: produto.id,
      quantidade,
      escolhas: selecionados(opcoes).map(([opcaoId, q]) => ({ opcaoId, quantidade: q })),
      adicionais: selecionados(adicionais).map(([adicionalId, q]) => ({ adicionalId, quantidade: q })),
    })
    setCotacao(r)
  })

  const adicionar = () => {
    onAdicionar({ cotacaoToken: cotacao.cotacaoToken }, `${cotacao.item.quantidade}x ${cotacao.item.nomeProduto} — ${brl(cotacao.item.subtotal)}`)
    setProdutoId(''); resetar(); setQuantidade(1)
  }

  return (
    <div className="montador">
      <Erro erro={erroCatalogo} />
      <div className="linha">
        <label className="campo"><span>Produto</span>
          <select value={produtoId} onChange={(e) => { setProdutoId(e.target.value); resetar() }}>
            <option value="">Selecione…</option>
            {catalogo?.map((p) => <option key={p.id} value={p.id}>{p.nome} ({p.categoriaNome})</option>)}
          </select>
        </label>
        <label className="campo"><span>Quantidade</span>
          <input type="number" min="1" value={quantidade} onChange={(e) => { setQuantidade(Number(e.target.value)); setCotacao(null) }} />
        </label>
      </div>

      {produto?.grupos.map((g) => (
        <fieldset key={g.id}>
          <legend>{g.nome} <small>({g.minimo}–{g.maximo} seleções · {rotulo(g.modalidadeCobranca)})</small></legend>
          {g.opcoes.map((o) => (
            <label key={o.id} className="opcao-linha">
              <span>{o.nome} <small className="mudo">{brl(o.preco)}{o.minimo > 0 && ` · mín. ${o.minimo}`}</small></span>
              <input type="number" min="0" max={o.maximo} value={opcoes[o.id] ?? 0}
                onChange={(e) => { setOpcoes({ ...opcoes, [o.id]: Number(e.target.value) }); setCotacao(null) }} />
            </label>
          ))}
        </fieldset>
      ))}

      {produto?.adicionais.length > 0 && (
        <fieldset>
          <legend>Adicionais</legend>
          {produto.adicionais.map((a) => (
            <label key={a.id} className="opcao-linha">
              <span>{a.nome} <small className="mudo">{brl(a.preco)}</small></span>
              <input type="number" min="0" value={adicionais[a.id] ?? 0}
                onChange={(e) => { setAdicionais({ ...adicionais, [a.id]: Number(e.target.value) }); setCotacao(null) }} />
            </label>
          ))}
        </fieldset>
      )}

      <Erro erro={acao.erro} />
      {produto && (
        <div className="linha">
          <button type="button" className="btn sec" disabled={acao.ocupado} onClick={cotar}>Calcular preço</button>
          {cotacao && (
            <>
              <strong>{brl(cotacao.item.precoUnitario)} un. · {brl(cotacao.item.subtotal)}</strong>
              <button type="button" className="btn" onClick={adicionar}>Adicionar ao pedido</button>
            </>
          )}
        </div>
      )}
    </div>
  )
}
