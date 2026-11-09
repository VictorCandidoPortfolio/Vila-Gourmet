import { useCallback, useEffect, useState } from 'react'

export const brl = (n) =>
  n == null ? '—' : Number(n).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })

export const dataHora = (s) => (s ? new Date(s).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' }) : '—')

const ACENTOS = { confirmacao: 'confirmação', preparacao: 'preparação', concluido: 'concluído', cobranca: 'cobrança', fechamento: 'fechamento' }

/** Converte um enum do servidor (EM_PREPARACAO) em texto legível (Em preparação). */
export const rotulo = (s) => {
  if (!s) return '—'
  const texto = s.replaceAll('_', ' ').toLowerCase().replace(/\b\w+\b/g, (w) => ACENTOS[w] ?? w)
  return texto.replace(/^./, (c) => c.toUpperCase())
}

export function Erro({ erro }) {
  if (!erro) return null
  return (
    <div className="alerta erro" role="alert">
      <strong>{erro.message}</strong>
      {erro.detalhes?.length > 0 && <ul>{erro.detalhes.map((d, i) => <li key={i}>{d}</li>)}</ul>}
    </div>
  )
}

/** Chave liga/desliga (acessível: checkbox com role="switch"). Não propaga o clique (útil dentro de linhas clicáveis). */
export function Chave({ ligado, aoAlterar, desabilitado, rotulo }) {
  return (
    <label className="chave" onClick={(e) => e.stopPropagation()}>
      <input type="checkbox" role="switch" checked={ligado} disabled={desabilitado} onChange={(e) => aoAlterar(e.target.checked)} />
      <span className="trilho" />
      {rotulo && <span className="chave-rotulo">{rotulo}</span>}
    </label>
  )
}

export function Badge({ valor }) {
  return <span className={`badge b-${valor}`}>{rotulo(valor)}</span>
}

export function Modal({ titulo, onFechar, children, largo, semEsc, fixo }) {
  useEffect(() => {
    if (semEsc) return undefined
    const fn = (e) => e.key === 'Escape' && onFechar()
    window.addEventListener('keydown', fn)
    return () => window.removeEventListener('keydown', fn)
  }, [onFechar, semEsc])
  return (
    <div className="modal-fundo" onMouseDown={(e) => !semEsc && e.target === e.currentTarget && onFechar()}>
      <div className={`modal ${largo ? 'largo' : ''} ${fixo ? 'fixo' : ''}`} role="dialog" aria-modal="true" aria-label={titulo}>
        <header>
          <h2>{titulo}</h2>
          <button className="btn sec" onClick={onFechar} aria-label="Fechar">✕</button>
        </header>
        <div className="modal-corpo">{children}</div>
      </div>
    </div>
  )
}

export function Campo({ label, children, dica, curto, largo }) {
  return (
    <label className={`campo${curto ? ' curto' : ''}${largo ? ' largo' : ''}`}>
      <span>{label}</span>
      {children}
      {dica && <small>{dica}</small>}
    </label>
  )
}

/** Carrega dados de uma função assíncrona; devolve { dados, erro, carregando, recarregar }. */
export function useCarga(fn, deps = [], intervaloMs = 0) {
  const [estado, setEstado] = useState({ dados: null, erro: null, carregando: true })
  const carregar = useCallback(async () => {
    try {
      const dados = await fn()
      setEstado({ dados, erro: null, carregando: false })
    } catch (erro) {
      setEstado((s) => ({ ...s, erro, carregando: false }))
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps)
  useEffect(() => {
    carregar()
    if (!intervaloMs) return undefined
    const id = setInterval(carregar, intervaloMs)
    return () => clearInterval(id)
  }, [carregar, intervaloMs])
  return { ...estado, recarregar: carregar }
}

/** Executa uma ação assíncrona com controle de erro e de "ocupado". */
export function useAcao() {
  const [erro, setErro] = useState(null)
  const [ocupado, setOcupado] = useState(false)
  const executar = async (fn) => {
    setErro(null)
    setOcupado(true)
    try {
      return await fn()
    } catch (e) {
      setErro(e)
      return undefined
    } finally {
      setOcupado(false)
    }
  }
  return { erro, ocupado, executar, limpar: () => setErro(null) }
}
