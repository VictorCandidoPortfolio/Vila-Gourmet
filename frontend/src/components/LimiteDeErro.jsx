import { Component } from 'react'

/**
 * Impede que um erro de renderização em uma tela apague a aplicação inteira: mostra um aviso com o
 * motivo e opções para tentar de novo ou recarregar. `chave` (ex.: a rota) reinicia o limite ao navegar.
 */
export default class LimiteDeErro extends Component {
  state = { erro: null, chave: this.props.chave }

  static getDerivedStateFromError(erro) {
    return { erro }
  }

  static getDerivedStateFromProps(props, state) {
    return props.chave !== state.chave ? { erro: null, chave: props.chave } : null
  }

  componentDidCatch(erro, info) {
    console.error('Erro ao exibir a tela:', erro, info?.componentStack)
  }

  render() {
    if (!this.state.erro) return this.props.children
    return (
      <section className="cartao">
        <h2>Não foi possível exibir esta tela</h2>
        <p className="mudo">
          Ocorreu um erro inesperado. Isso costuma acontecer quando o frontend e a API estão em versões diferentes —
          nesse caso, reinicie a API e recarregue a página.
        </p>
        <pre className="erro-tecnico">{String(this.state.erro?.message ?? this.state.erro)}</pre>
        <div className="linha">
          <button className="btn" onClick={() => this.setState({ erro: null })}>Tentar de novo</button>
          <button className="btn sec" onClick={() => window.location.reload()}>Recarregar a página</button>
        </div>
      </section>
    )
  }
}
