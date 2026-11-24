import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'

// Um único app, três áreas carregadas sob demanda: loja, gestão e frente de caixa.
const emGestao = window.location.pathname === '/gestao' || window.location.pathname.startsWith('/gestao/')
const emCaixa = window.location.pathname === '/caixa' || window.location.pathname.startsWith('/caixa/')

async function iniciar() {
  let arvore
  if (emCaixa) {
    const [{ default: CaixaApp }, { AuthProvider }] = await Promise.all([import('./CaixaApp.jsx'), import('./auth.jsx'), import('./styles.css')])
    document.title = 'Vila Gourmet — Frente de caixa'
    arvore = (
      <BrowserRouter basename="/caixa">
        <AuthProvider><CaixaApp /></AuthProvider>
      </BrowserRouter>
    )
  } else if (emGestao) {
    const [{ default: App }, { AuthProvider }] = await Promise.all([import('./App.jsx'), import('./auth.jsx'), import('./styles.css')])
    document.title = 'Vila Gourmet — Gestão'
    arvore = (
      <BrowserRouter basename="/gestao">
        <AuthProvider><App /></AuthProvider>
      </BrowserRouter>
    )
  } else {
    const [{ default: Loja }, { ClienteProvider }, { CarrinhoProvider }, { LojaProvider }] = await Promise.all([
      import('./loja/Loja.jsx'), import('./loja/cliente.jsx'), import('./loja/carrinho.jsx'), import('./loja/lojaStatus.jsx'), import('./loja/loja.css'),
    ])
    document.title = 'Vila Gourmet'
    arvore = (
      <BrowserRouter>
        <ClienteProvider>
          <LojaProvider>
            <CarrinhoProvider><Loja /></CarrinhoProvider>
          </LojaProvider>
        </ClienteProvider>
      </BrowserRouter>
    )
  }
  ReactDOM.createRoot(document.getElementById('root')).render(<React.StrictMode>{arvore}</React.StrictMode>)
}

iniciar()
