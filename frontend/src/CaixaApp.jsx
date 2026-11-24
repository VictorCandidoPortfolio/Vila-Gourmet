import { useEffect, useState } from 'react'
import { NavLink, Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { useAuth } from './auth.jsx'
import Icon from './components/Icon.jsx'
import Logo from './components/Logo.jsx'
import LimiteDeErro from './components/LimiteDeErro.jsx'
import Login from './pages/Login.jsx'
import Caixa from './pages/Caixa.jsx'
import CaixaInicio from './pages/CaixaInicio.jsx'
import CaixaHistorico from './pages/CaixaHistorico.jsx'
import Mesas from './pages/Mesas.jsx'

const MENU = [
  { to: '/', rotulo: 'Caixa', icone: 'home', fim: true },
  { to: '/vendas', rotulo: 'Realizar vendas', icone: 'cart' },
  { to: '/mesas', rotulo: 'Mesas', icone: 'table', perm: 'MESA_GERENCIAR' },
  { to: '/historico', rotulo: 'Histórico', icone: 'clock' },
]

export default function CaixaApp() {
  const { usuario, sair, pode } = useAuth()
  const { pathname } = useLocation()
  const [menuUsuario, setMenuUsuario] = useState(false)
  const [menuLateral, setMenuLateral] = useState(false)
  useEffect(() => { setMenuUsuario(false); setMenuLateral(false) }, [pathname])

  if (!usuario) return <Login />
  if (!pode('CAIXA_OPERAR')) return (
    <div className="login"><section className="login-cartao"><Logo /><h2>Sem acesso ao frente de caixa</h2>
      <p className="mudo">Seu perfil não possui a permissão CAIXA_OPERAR.</p><a className="btn" href="/gestao">Voltar à gestão</a></section></div>
  )
  const menuVisivel = MENU.filter((m) => !m.perm || pode(m.perm))
  const tituloAtual = menuVisivel.find((m) => m.fim ? pathname === '/' : pathname.startsWith(m.to))?.rotulo ?? 'Frente de caixa'

  return (
    <div className="layout caixa-layout">
      {menuLateral && <button className="menu-overlay" aria-label="Fechar menu" onClick={() => setMenuLateral(false)} />}
      <aside className={`menu ${menuLateral ? 'aberto' : ''}`}>
        <button className="menu-fechar" aria-label="Fechar menu" onClick={() => setMenuLateral(false)}><Icon nome="x" tamanho={22} /></button>
        <Logo />
        <div className="caixa-area-label">Frente de caixa</div>
        <nav>{menuVisivel.map((m) => (
          <NavLink key={m.to} to={m.to} end={m.fim} className={({ isActive }) => `item ${isActive ? 'ativo' : ''}`}>
            <Icon nome={m.icone} /><span>{m.rotulo}</span>
          </NavLink>
        ))}</nav>
        <a className="item" href="/gestao"><Icon nome="settings" /><span>Voltar à gestão</span></a>
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
              <span className="usuario-dados"><strong>{usuario.nome}</strong></span><Icon nome="down" tamanho={16} />
            </button>
            {menuUsuario && <div className="menu-usuario"><button onClick={sair}><Icon nome="logout" tamanho={16} /> Sair</button></div>}
          </div>
        </header>
        <main className="conteudo caixa-conteudo"><LimiteDeErro chave={pathname}>
          <Routes>
            <Route path="/" element={<CaixaInicio />} />
            <Route path="/vendas" element={<Caixa />} />
            {pode('MESA_GERENCIAR') && <Route path="/mesas" element={<Mesas />} />}
            <Route path="/historico" element={<CaixaHistorico />} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </LimiteDeErro></main>
      </div>
    </div>
  )
}
