import { useEffect, useState } from 'react'
import { Link, NavLink, Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import Icon from '../components/Icon.jsx'
import LimiteDeErro from '../components/LimiteDeErro.jsx'
import Logo from '../components/Logo.jsx'
import { useCarrinho } from './carrinho.jsx'
import { useCliente } from './cliente.jsx'
import { useLoja } from './lojaStatus.jsx'
import CarrinhoGaveta from './CarrinhoGaveta.jsx'
import Inicio from './pages/Inicio.jsx'
import Cardapio from './pages/Cardapio.jsx'
import Entrar from './pages/Entrar.jsx'
import Finalizar from './pages/Finalizar.jsx'
import PedidoFeito from './pages/PedidoFeito.jsx'
import Mesa from './pages/Mesa.jsx'
import Acompanhar from './pages/Acompanhar.jsx'
import Conta from './pages/Conta.jsx'

function Busca() {
  const navigate = useNavigate()
  const { pathname, search } = useLocation()
  const atual = pathname === '/cardapio' ? new URLSearchParams(search).get('busca') ?? '' : ''
  const [texto, setTexto] = useState(atual)
  useEffect(() => { setTexto(atual) }, [atual])
  const enviar = (e) => {
    e.preventDefault()
    navigate(`/cardapio${texto.trim() ? `?busca=${encodeURIComponent(texto.trim())}` : ''}`)
  }
  return (
    <form className={`lj-busca lj-busca-global ${pathname === '/cardapio' ? 'em-cardapio' : ''}`} onSubmit={enviar} role="search">
      <Icon nome="search" tamanho={18} />
      <input value={texto} onChange={(e) => setTexto(e.target.value)} placeholder="Buscar no cardápio…" aria-label="Buscar no cardápio" />
    </form>
  )
}

function Cabecalho() {
  const { cliente, sair } = useCliente()
  const { quantidadeTotal, setAberto } = useCarrinho()
  const [menu, setMenu] = useState(false)
  const { pathname } = useLocation()
  useEffect(() => { setMenu(false) }, [pathname])

  return (
    <header className="lj-topo">
      <div className="lj-topo-int">
        <Link to="/" className="lj-logo" aria-label="Vila Gourmet — início"><Logo /></Link>
        <nav className="lj-nav" aria-label="Principal">
          <NavLink to="/" end><Icon nome="home" tamanho={19} /><span>Início</span></NavLink>
          <NavLink to="/cardapio"><Icon nome="book" tamanho={19} /><span>Cardápio</span></NavLink>
          <NavLink to="/mesa"><Icon nome="store" tamanho={19} /><span>Mesa</span></NavLink>
          <NavLink to="/acompanhar"><Icon nome="clock" tamanho={19} /><span>Acompanhar</span></NavLink>
        </nav>
        <Busca />
        <div className="lj-conta">
          {cliente ? (
            <div className="lj-usuario">
              <button className="lj-link" onClick={() => setMenu(!menu)} aria-expanded={menu}>
                <Icon nome="user" tamanho={20} /> <span>{cliente.nome.split(' ')[0]}</span>
              </button>
              {menu && (
                <div className="lj-menu-usuario">
                  <Link to="/conta">Minha conta</Link>
                  <button onClick={sair}>Sair</button>
                </div>
              )}
            </div>
          ) : (
            <Link to="/entrar" className="lj-link"><Icon nome="user" tamanho={20} /> <span>Entrar</span></Link>
          )}
          <button className="lj-carrinho-btn" onClick={() => setAberto(true)} aria-label={`Abrir carrinho (${quantidadeTotal} itens)`}>
            <Icon nome="cart" tamanho={24} />
            {quantidadeTotal > 0 && <span className="lj-contador">{quantidadeTotal}</span>}
          </button>
        </div>
      </div>
    </header>
  )
}

export default function Loja() {
  const { pathname } = useLocation()
  const { aberta } = useLoja()
  useEffect(() => { window.scrollTo(0, 0) }, [pathname])
  return (
    <div className="lj">
      <Cabecalho />
      {!aberta && (
        <div className="lj-faixa-fechada" role="status">
          <strong>Estamos fechados no momento.</strong> Você pode ver o cardápio, mas não é possível fazer pedidos agora.
        </div>
      )}
      <main className="lj-principal">
        <LimiteDeErro chave={pathname}>
          <Routes>
            <Route path="/" element={<Inicio />} />
            <Route path="/cardapio" element={<Cardapio />} />
            <Route path="/entrar" element={<Entrar />} />
            <Route path="/finalizar" element={<Finalizar />} />
            <Route path="/pedido-feito" element={<PedidoFeito />} />
            <Route path="/mesa" element={<Mesa />} />
            <Route path="/acompanhar" element={<Acompanhar />} />
            <Route path="/conta" element={<Conta />} />
            <Route path="*" element={<Inicio />} />
          </Routes>
        </LimiteDeErro>
      </main>
      <CarrinhoGaveta />
      <footer className="lj-rodape">
        <Logo compacto />
        <a href="/gestao">Acesso da equipe</a>
      </footer>
    </div>
  )
}
