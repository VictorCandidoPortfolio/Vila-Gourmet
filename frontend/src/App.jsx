import { useEffect, useState } from 'react'
import { NavLink, Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { useAuth } from './auth.jsx'
import Icon from './components/Icon.jsx'
import Logo from './components/Logo.jsx'
import LimiteDeErro from './components/LimiteDeErro.jsx'
import Login from './pages/Login.jsx'
import Dashboard from './pages/Dashboard.jsx'
import Pedidos from './pages/Pedidos.jsx'
import Cancelamentos from './pages/Cancelamentos.jsx'
import Catalogo from './pages/Catalogo.jsx'
import Entrega from './pages/Entrega.jsx'
import Equipe from './pages/Equipe.jsx'
import Configuracoes from './pages/Configuracoes.jsx'
import Historico from './pages/Historico.jsx'

const MENU = [
  { to: '/', icone: 'chart', rotulo: 'Relatórios', perm: 'RELATORIO_VER', el: <Dashboard />, fim: true },
  { to: '/pedidos', icone: 'bag', rotulo: 'Pedidos', perm: 'PEDIDO_VER', el: <Pedidos /> },
  { to: '/historico', icone: 'clock', rotulo: 'Histórico', perm: 'PEDIDO_VER', el: <Historico /> },
  { to: '/cancelamentos', icone: 'cancel', rotulo: 'Cancelamentos', perm: 'CANCELAMENTO_DECIDIR', el: <Cancelamentos /> },
  { to: '/catalogo', icone: 'book', rotulo: 'Catálogo', perm: 'CATALOGO_GERENCIAR', el: <Catalogo /> },
  { to: '/entrega', icone: 'truck', rotulo: 'Entrega', perm: ['PEDIDO_VER', 'CONFIG_GERENCIAR'], el: <Entrega /> },
  { to: '/equipe', icone: 'users', rotulo: 'Funcionários e perfis', perm: 'FUNCIONARIO_GERENCIAR', el: <Equipe /> },
  { to: '/configuracoes', icone: 'settings', rotulo: 'Configurações', perm: 'CONFIG_GERENCIAR', el: <Configuracoes /> },
]

export default function App() {
  const { usuario, sair, pode } = useAuth()
  const { pathname } = useLocation()
  const [menuUsuario, setMenuUsuario] = useState(false)
  const [menuLateral, setMenuLateral] = useState(false)
  useEffect(() => { setMenuUsuario(false); setMenuLateral(false) }, [pathname])

  if (!usuario) return <Login />

  // A UI só esconde o que o usuário não pode usar; o servidor revalida cada operação.
  const visiveis = MENU.filter((m) => [].concat(m.perm).some(pode))
  const tituloAtual = visiveis.find((m) => m.fim ? pathname === '/' : pathname.startsWith(m.to))?.rotulo ?? 'Gestão'
  return (
    <div className="layout">
      {menuLateral && <button className="menu-overlay" aria-label="Fechar menu" onClick={() => setMenuLateral(false)} />}
      <aside className={`menu ${menuLateral ? 'aberto' : ''}`}>
        <button className="menu-fechar" aria-label="Fechar menu" onClick={() => setMenuLateral(false)}><Icon nome="x" tamanho={22} /></button>
        <Logo />
        <div className="app-area-label">Central de gestão</div>
        <nav>
          {visiveis.map((m) => (
            <NavLink key={m.to} to={m.to} end={m.fim} className={({ isActive }) => `item ${isActive ? 'ativo' : ''}`}>
              <Icon nome={m.icone} /><span>{m.rotulo}</span>
            </NavLink>
          ))}
        </nav>
        {pode('CAIXA_OPERAR') && (
          <a className="item acesso-caixa" href="/caixa">
            <Icon nome="dollar" /><span>Acessar frente de caixa</span>
          </a>
        )}
      </aside>

      <div className="coluna-principal">
        <header className="topo">
          <button className="menu-mobile-btn" onClick={() => setMenuLateral(true)} aria-label="Abrir menu" aria-expanded={menuLateral}>
            <Icon nome="menu" tamanho={24} /><span>Menu</span>
          </button>
          <strong className="topo-titulo">{tituloAtual}</strong>
          <div className="usuario-topo">
            <button className="usuario-btn" onClick={() => setMenuUsuario(!menuUsuario)} aria-expanded={menuUsuario}>
              <span className="avatar"><Icon nome="user" tamanho={22} /></span>
              <span className="usuario-dados"><strong>{usuario.nome}</strong></span>
              <Icon nome="down" tamanho={16} />
            </button>
            {menuUsuario && (
              <div className="menu-usuario">
                <button onClick={sair}><Icon nome="logout" tamanho={16} /> Sair</button>
              </div>
            )}
          </div>
        </header>
        <main className="conteudo">
          <LimiteDeErro chave={pathname}>
          <Routes>
            {visiveis.map((m) => <Route key={m.to} path={m.to} element={m.el} />)}
            <Route path="*" element={visiveis.length ? <Navigate to={visiveis[0].to} replace /> : <p>Seu perfil não tem permissões atribuídas.</p>} />
          </Routes>
          </LimiteDeErro>
        </main>
      </div>
    </div>
  )
}
