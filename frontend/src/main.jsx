import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'

// Um único app, duas áreas: a gestão (equipe) vive em /gestao e a loja (cliente) em /.
// Cada área é carregada sob demanda para que o cliente não baixe o código da gestão (e vice-versa).
const emGestao = window.location.pathname === '/gestao' || window.location.pathname.startsWith('/gestao/')

async function iniciar() {
  let arvore
  if (emGestao) {
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
