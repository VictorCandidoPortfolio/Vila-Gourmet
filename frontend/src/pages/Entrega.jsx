import { useSearchParams } from 'react-router-dom'
import { useAuth } from '../auth.jsx'
import AreasAtendidas from '../components/AreasAtendidas.jsx'
import PedidosEntrega from '../components/PedidosEntrega.jsx'

/** Entrega: pedidos para entrega (PEDIDO_VER) e áreas atendidas — cidades e bairros (CONFIG_GERENCIAR). */
export default function Entrega() {
  const { pode } = useAuth()
  const [params, setParams] = useSearchParams()
  const abas = [
    pode('PEDIDO_VER') && ['pedidos', 'Pedidos para entrega'],
    pode('CONFIG_GERENCIAR') && ['areas', 'Áreas atendidas'],
  ].filter(Boolean)
  const aba = abas.some(([id]) => id === params.get('aba')) ? params.get('aba') : abas[0]?.[0]

  return (
    <>
      <h1>Entrega</h1>
      <div className="abas" role="tablist">
        {abas.map(([id, nome]) => (
          <button key={id} role="tab" aria-selected={aba === id} className={aba === id ? 'ativo' : ''} onClick={() => setParams({ aba: id }, { replace: true })}>{nome}</button>
        ))}
      </div>
      {aba === 'pedidos' && <PedidosEntrega />}
      {aba === 'areas' && <AreasAtendidas />}
    </>
  )
}
