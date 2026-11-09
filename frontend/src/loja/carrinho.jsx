import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { post } from '../api.js'
import { corpoCotacao, resumoDoItem } from '../montagem.js'

const CarrinhoContext = createContext(null)
export const useCarrinho = () => useContext(CarrinhoContext)

const CHAVE = 'vg_carrinho'
const ler = () => {
  try { return JSON.parse(localStorage.getItem(CHAVE)) ?? {} } catch { return {} }
}

const montarLinha = (selecao, cotacao) => ({
  uid: crypto.randomUUID(),
  selecao,
  nomeProduto: cotacao.item.nomeProduto,
  quantidade: cotacao.item.quantidade,
  precoUnitario: cotacao.item.precoUnitario,
  subtotal: cotacao.item.subtotal,
  resumo: resumoDoItem(cotacao.item),
  cotacaoToken: cotacao.cotacaoToken,
  expiraEm: cotacao.expiraEm,
})

/**
 * Carrinho da loja. O preço de cada item vem sempre de uma cotação do servidor (token assinado);
 * se o token expirar, o item é cotado de novo antes de finalizar. Guarda também a modalidade
 * escolhida (ENTREGA, RETIRADA ou MESA) e a conta de mesa aberta neste aparelho.
 */
export function CarrinhoProvider({ children }) {
  const inicial = useMemo(ler, [])
  const [itens, setItens] = useState(inicial.itens ?? [])
  const [modalidade, setModalidade] = useState(inicial.modalidade ?? null)
  const [mesa, setMesa] = useState(inicial.mesa ?? null) // { codigo, cpf, nome }
  const [aberto, setAberto] = useState(false)

  useEffect(() => {
    try { localStorage.setItem(CHAVE, JSON.stringify({ itens, modalidade, mesa })) } catch { /* sem armazenamento */ }
  }, [itens, modalidade, mesa])

  const adicionar = useCallback((selecao, cotacao) => {
    setItens((atual) => [...atual, montarLinha(selecao, cotacao)])
    setAberto(true)
  }, [])
  const remover = useCallback((uid) => setItens((atual) => atual.filter((i) => i.uid !== uid)), [])
  const limpar = useCallback(() => setItens([]), [])

  const recotar = async (linha, quantidade = linha.selecao.quantidade) => {
    const selecao = { ...linha.selecao, quantidade }
    const cotacao = await post('/api/public/cotacoes', corpoCotacao(selecao))
    return { ...montarLinha(selecao, cotacao), uid: linha.uid }
  }

  const alterarQuantidade = async (uid, quantidade) => {
    const linha = itens.find((i) => i.uid === uid)
    if (!linha || quantidade < 1) return
    const nova = await recotar(linha, quantidade)
    setItens((atual) => atual.map((i) => (i.uid === uid ? nova : i)))
  }

  /** Cota de novo os itens com cotação vencida (ou prestes a vencer). Devolve o que mudou de preço. */
  const revalidar = async () => {
    const limite = Date.now() + 60_000
    const mudancas = []
    const novos = await Promise.all(itens.map(async (linha) => {
      if (new Date(linha.expiraEm).getTime() > limite) return linha
      const nova = await recotar(linha)
      if (Number(nova.subtotal) !== Number(linha.subtotal)) mudancas.push({ nome: linha.nomeProduto, de: linha.subtotal, para: nova.subtotal })
      return nova
    }))
    setItens(novos)
    return { itens: novos, mudancas }
  }

  const total = itens.reduce((s, i) => s + Number(i.subtotal), 0)
  const quantidadeTotal = itens.reduce((s, i) => s + i.quantidade, 0)

  const valor = {
    itens, total, quantidadeTotal, modalidade, setModalidade, mesa, setMesa,
    aberto, setAberto, adicionar, remover, limpar, alterarQuantidade, revalidar,
  }
  return <CarrinhoContext.Provider value={valor}>{children}</CarrinhoContext.Provider>
}
