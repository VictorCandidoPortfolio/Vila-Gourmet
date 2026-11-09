import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { get, onSessaoClienteExpirada, post, setTokenCliente, getTokenCliente } from '../api.js'

const ClienteContext = createContext(null)
export const useCliente = () => useContext(ClienteContext)

const lerUsuario = () => {
  try { return getTokenCliente() ? JSON.parse(sessionStorage.getItem('vgc_usuario')) : null } catch { return null }
}

/** Sessão do cliente da loja (papel CLIENTE). Visitantes seguem sem sessão: só a entrega exige conta. */
export function ClienteProvider({ children }) {
  const [cliente, setCliente] = useState(lerUsuario)

  const sair = useCallback(() => {
    setTokenCliente(null)
    try { sessionStorage.removeItem('vgc_usuario') } catch { /* sem armazenamento */ }
    setCliente(null)
  }, [])
  useEffect(() => { onSessaoClienteExpirada(sair) }, [sair])

  const iniciar = (r) => {
    setTokenCliente(r.token)
    const u = { nome: r.nome }
    try { sessionStorage.setItem('vgc_usuario', JSON.stringify(u)) } catch { /* sem armazenamento */ }
    setCliente(u)
  }

  // `login` aceita e-mail ou CPF
  const entrar = async (login, senha) => iniciar(await post('/api/auth/cliente/login', { login, senha }))
  const cadastrar = async (dados) => iniciar(await post('/api/auth/cliente/cadastro', dados))

  const valor = useMemo(() => ({ cliente, entrar, cadastrar, sair }), [cliente, sair])
  return <ClienteContext.Provider value={valor}>{children}</ClienteContext.Provider>
}

/** Perfil completo (nome, e-mail, CPF) do cliente logado. */
export const carregarPerfil = () => get('/api/cliente/me')
