import { useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { get, post } from '../api.js'
import Icon from '../components/Icon.jsx'
import { Erro, Modal, dataHora, useAcao, useCarga } from '../ui.jsx'
import { FecharCaixa } from './Caixa.jsx'

export default function CaixaInicio() {
  const { dados: status, erro, carregando, recarregar } = useCarga(() => get('/api/caixa'), [], 15000)
  const abrir = useAcao()
  const abrindoRef = useRef(false)
  const [fechando, setFechando] = useState(false)
  const [resultado, setResultado] = useState(null)
  const [abrirSite, setAbrirSite] = useState(false)

  const abrirCaixa = () => {
    if (abrindoRef.current) return
    abrindoRef.current = true
    abrir.executar(async () => {
      try {
        await post('/api/caixa/abrir', { vendasOnline: abrirSite })
        setAbrirSite(false)
        await recarregar()
      } finally {
        abrindoRef.current = false
      }
    })
  }

  return (
    <>
      <div className="linha entre"><div><h1>Frente de caixa</h1><p className="mudo">Controle a sessão atual e acesse as operações do PDV.</p></div></div>
      <Erro erro={erro} />
      {carregando && !status && <p>Carregando…</p>}
      {status && (
        <section className={`cartao caixa-resumo ${status.aberto ? 'aberto' : 'fechado'}`}>
          <div>
            <span className={`badge b-${status.aberto ? 'CONCLUIDO' : 'CANCELADO'}`}>Caixa {status.aberto ? 'aberto' : 'fechado'}</span>
            <h2>{status.aberto ? `Sessão #${status.caixaId} em andamento` : 'Nenhuma sessão aberta'}</h2>
            <p className="mudo">{status.aberto
              ? `Aberta em ${dataHora(status.abertoEm)}${status.abertoPor ? ` por ${status.abertoPor}` : ''}.`
              : 'Abra uma nova sessão ou retome uma sessão encerrada pelo histórico.'}</p>
          </div>
          {status.aberto && (
            <div className={`caixa-site-status ${status.vendasOnline ? 'disponivel' : 'indisponivel'}`} role="status">
              <strong>{status.vendasOnline ? 'Site de vendas disponível' : 'Site de vendas indisponível'}</strong>
              <span>{status.vendasOnline
                ? 'Clientes podem realizar pedidos pelo site durante esta sessão.'
                : 'Esta sessão aceita somente vendas realizadas pelo frente de caixa.'}</span>
              {status.vendasOnline && <a className="btn sec caixa-site-link" href="/" target="_blank" rel="noopener noreferrer">Abrir site de vendas</a>}
            </div>
          )}
          <Erro erro={abrir.erro} />
          {!status.aberto && (
            <div className="caixa-abertura-opcao">
              <label className="check" htmlFor="abrir-site-vendas">
                <input id="abrir-site-vendas" type="checkbox" checked={abrirSite}
                  onChange={(e) => setAbrirSite(e.target.checked)} disabled={abrir.ocupado}
                  aria-describedby="abrir-site-vendas-ajuda" />
                <span>Disponibilizar vendas pelo site após abrir o caixa</span>
              </label>
              <small id="abrir-site-vendas-ajuda" className="mudo">Libera os pedidos online. O painel do caixa permanece nesta tela.</small>
            </div>
          )}
          <div className="linha">
            {status.aberto ? <>
              <Link className="btn" to="/vendas"><Icon nome="cart" tamanho={17} /> Realizar venda</Link>
              <button className="btn perigo" onClick={() => setFechando(true)}>Fechar caixa</button>
            </> : <button className="btn" disabled={abrir.ocupado} onClick={abrirCaixa}>
              {abrir.ocupado ? 'Abrindo…' : 'Abrir novo caixa'}
            </button>}
            <Link className="btn sec" to="/historico"><Icon nome="clock" tamanho={17} /> Histórico</Link>
          </div>
        </section>
      )}
      {status?.aberto && (
        <div className="kpis caixa-kpis">
          <div className="kpi kpi-verde"><div className="kpi-icone"><Icon nome="bag" /></div><div className="kpi-valor"><span className="kpi-titulo">Pedidos em andamento</span><strong>{status.pedidosEmAberto}</strong></div></div>
          <div className="kpi kpi-rosa"><div className="kpi-icone"><Icon nome="clock" /></div><div className="kpi-valor"><span className="kpi-titulo">Abertura</span><strong className="caixa-hora">{dataHora(status.abertoEm)}</strong></div></div>
        </div>
      )}
      {fechando && <FecharCaixa status={status} onFechar={() => setFechando(false)} onFechado={(r) => { setFechando(false); setResultado(r); recarregar() }} />}
      {resultado && <Modal titulo="Caixa fechado" onFechar={() => setResultado(null)}><p>A sessão foi encerrada e já está disponível no histórico.</p><div className="linha"><button className="btn" onClick={() => setResultado(null)}>Ok</button></div></Modal>}
    </>
  )
}
