import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { get, onSessaoExpirada, post, setToken, getToken } from './api.js'

const AuthContext = createContext(null)
export const useAuth = () => useContext(AuthContext)

export function AuthProvider({ children }) {
  const [usuario, setUsuario] = useState(() => {
    try { return getToken() ? JSON.parse(sessionStorage.getItem('vg_usuario')) : null } catch { return null }
  })

  const sair = useCallback(() => {
    setToken(null)
    sessionStorage.removeItem('vg_usuario')
    setUsuario(null)
  }, [])

  useEffect(() => { onSessaoExpirada(sair) }, [sair])

  // As permissões ficam guardadas na sessão: ao abrir o painel buscamos as de agora (novas permissões aparecem sem sair e entrar).
  useEffect(() => {
    if (!usuario) return
    get('/api/gestao/eu').then((r) => {
      const u = { nome: r.nome || usuario.nome, permissoes: r.permissoes }
      if (JSON.stringify(u.permissoes) === JSON.stringify(usuario.permissoes)) return
      try { sessionStorage.setItem('vg_usuario', JSON.stringify(u)) } catch { /* sem armazenamento */ }
      setUsuario(u)
    }).catch(() => {})
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const entrar = async (email, senha) => {
    const r = await post('/api/auth/funcionario/login', { email, senha })
    setToken(r.token)
    const u = { nome: r.nome, permissoes: r.permissoes }
    sessionStorage.setItem('vg_usuario', JSON.stringify(u))
    setUsuario(u)
  }

  // A UI só esconde o que o usuário não pode usar; o servidor revalida cada operação.
  const pode = useCallback((perm) => !!usuario?.permissoes?.includes(perm), [usuario])
  const valor = useMemo(() => ({ usuario, entrar, sair, pode }), [usuario, sair, pode])
  return <AuthContext.Provider value={valor}>{children}</AuthContext.Provider>
}
