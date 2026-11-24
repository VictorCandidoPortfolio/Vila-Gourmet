import { createContext, useContext, useEffect, useMemo, useState } from 'react'
import { get } from '../api.js'

const LojaContext = createContext({ aberta: true, carregado: false })
export const useLoja = () => useContext(LojaContext)

/**
 * Disponibilidade das vendas online, atualizada a cada 30 s. Além do caixa aberto, a sessão precisa ter
 * liberado o site; o servidor revalida isso ao criar o pedido. Enquanto não se sabe, vale "aberta".
 */
export function LojaProvider({ children }) {
  const [estado, setEstado] = useState({ aberta: true, carregado: false })
  useEffect(() => {
    let vivo = true
    const atualizar = () => get('/api/public/loja/status')
      .then((r) => { if (vivo) setEstado({ aberta: r.aberta, carregado: true }) })
      .catch(() => {})
    atualizar()
    const id = setInterval(atualizar, 30000)
    return () => { vivo = false; clearInterval(id) }
  }, [])
  const valor = useMemo(() => estado, [estado.aberta, estado.carregado])
  return <LojaContext.Provider value={valor}>{children}</LojaContext.Provider>
}
