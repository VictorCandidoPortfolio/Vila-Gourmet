import { useEffect, useMemo, useRef, useState } from 'react'
import { post } from './api.js'

/** Seleções em formato de API a partir do estado da tela ({ id: quantidade }). */
export const selecoes = (mapa) =>
  Object.entries(mapa).filter(([, q]) => q > 0).map(([id, q]) => [Number(id), q])

export const corpoCotacao = (s) => ({
  produtoId: s.produtoId,
  quantidade: s.quantidade,
  escolhas: s.escolhas,
  adicionais: s.adicionais,
})

/** Linhas legíveis das escolhas de um item cotado, agrupadas por grupo. */
export function resumoDoItem(item) {
  const porGrupo = new Map()
  item.escolhas.forEach((e) => {
    const nome = `${e.nomeOpcao}${e.quantidade > 1 ? ` ×${e.quantidade}` : ''}`
    porGrupo.set(e.nomeGrupo, [...(porGrupo.get(e.nomeGrupo) ?? []), nome])
  })
  const linhas = [...porGrupo].map(([g, ops]) => `${g}: ${ops.join(', ')}`)
  if (item.adicionais.length) {
    linhas.push(`Adicionais: ${item.adicionais.map((a) => `${a.nome}${a.quantidade > 1 ? ` ×${a.quantidade}` : ''}`).join(', ')}`)
  }
  return linhas
}

const minimosIniciais = (produto) => {
  const mapa = {}
  produto.grupos.forEach((g) => g.opcoes.forEach((o) => { if (o.disponivel && o.minimo > 0) mapa[o.id] = o.minimo }))
  return mapa
}

/**
 * Montagem de um produto (usada pela loja e pela frente de caixa): grupos de opções com mínimo e máximo,
 * adicionais e quantidade. O preço vem sempre do servidor (POST /api/public/cotacoes), com debounce; a cotação
 * só existe quando a montagem é válida. `aoAlterar` é chamado a cada mudança feita pelo usuário.
 */
export function useMontagem(produto, { aoAlterar } = {}) {
  const [quantidade, setQuantidadeEstado] = useState(1)
  const [opcoes, setOpcoes] = useState(() => minimosIniciais(produto))
  const [adicionais, setAdicionaisEstado] = useState({})
  const [cotacao, setCotacao] = useState(null)
  const [problema, setProblema] = useState(null)

  const grupos = useMemo(() => produto.grupos.filter((g) => g.ativo && g.opcoes.some((o) => o.disponivel)), [produto])
  const adicionaisDisp = useMemo(() => produto.adicionais.filter((a) => a.disponivel), [produto])

  const selecao = useMemo(() => ({
    produtoId: produto.id,
    quantidade,
    escolhas: selecoes(opcoes).map(([opcaoId, q]) => ({ opcaoId, quantidade: q })),
    adicionais: selecoes(adicionais).map(([adicionalId, q]) => ({ adicionalId, quantidade: q })),
  }), [produto.id, quantidade, opcoes, adicionais])

  // Respostas atrasadas são descartadas.
  const seq = useRef(0)
  useEffect(() => {
    const minha = ++seq.current
    setCotacao(null)
    const t = setTimeout(async () => {
      try {
        const r = await post('/api/public/cotacoes', corpoCotacao(selecao))
        if (minha === seq.current) { setCotacao(r); setProblema(null) }
      } catch (e) {
        if (minha === seq.current) setProblema(e)
      }
    }, 250)
    return () => clearTimeout(t)
  }, [selecao])

  const totalDoGrupo = (g) => g.opcoes.reduce((s, o) => s + (opcoes[o.id] ?? 0), 0)

  const definir = (g, o, nova) => {
    aoAlterar?.()
    setOpcoes((atual) => {
      const prox = { ...atual }
      if (g.maximo === 1) {
        // escolha única: selecionar uma troca a anterior; tocar de novo desmarca se o grupo for opcional
        const marcada = (atual[o.id] ?? 0) > 0
        g.opcoes.forEach((x) => delete prox[x.id])
        if (!marcada || g.minimo > 0) prox[o.id] = Math.max(o.minimo, 1)
        return prox
      }
      const outros = g.opcoes.reduce((s, x) => s + (x.id === o.id ? 0 : atual[x.id] ?? 0), 0)
      let q = Math.min(nova, o.maximo > 0 ? o.maximo : nova)
      if (g.maximo > 0) q = Math.min(q, g.maximo - outros)
      if (q <= 0) delete prox[o.id]
      else prox[o.id] = Math.max(q, o.minimo)
      return prox
    })
  }

  return {
    grupos, adicionaisDisp, quantidade, setQuantidade: setQuantidadeEstado,
    opcoes, aplicarEscolhas: (escolhas) => setOpcoes(Object.fromEntries(escolhas.map((e) => [e.opcaoId, e.quantidade]))),
    adicionais, definirAdicional: (id, v) => { aoAlterar?.(); setAdicionaisEstado((a) => ({ ...a, [id]: v })) },
    selecao, cotacao, problema, totalDoGrupo, definir,
  }
}
