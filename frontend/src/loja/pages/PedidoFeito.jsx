import { Link, Navigate, useLocation } from 'react-router-dom'
import Icon from '../../components/Icon.jsx'
import { Badge, brl } from '../../ui.jsx'
import { useCliente } from '../cliente.jsx'

/** Confirmação do pedido. Os códigos em texto puro só existem nesta resposta: o cliente precisa anotá-los. */
export default function PedidoFeito() {
  const { state } = useLocation()
  const { cliente } = useCliente()
  if (!state?.pedido) return <Navigate to="/" replace />
  const { pedido, codigoAcompanhamento, codigoConfirmacao } = state
  const acompanhamento = cliente && pedido.modalidade !== 'MESA'
    ? `/acompanhar?pedido=${pedido.id}`
    : codigoAcompanhamento && `/acompanhar?codigo=${encodeURIComponent(codigoAcompanhamento)}`

  return (
    <div className="lj-pagina estreita">
      <div className="lj-cartao lj-centro">
        <span className="lj-selo"><Icon nome="check" tamanho={30} /></span>
        <h1 className="lj-titulo-pagina">Pedido enviado!</h1>
        <p>Pedido <strong>{pedido.numeroPedido}</strong> · <Badge valor={pedido.estado} /> · total <strong>{brl(pedido.total)}</strong></p>

        <div className="lj-codigos">
          {codigoAcompanhamento && (
            <div>
              <small>Código de acompanhamento</small>
              <strong className="lj-codigo">{codigoAcompanhamento}</strong>
            </div>
          )}
          {codigoConfirmacao && (
            <div>
              <small>Código de confirmação {pedido.modalidade === 'ENTREGA' ? 'da entrega' : 'da retirada'}</small>
              <strong className="lj-codigo">{codigoConfirmacao}</strong>
            </div>
          )}
        </div>
        <p className="alerta aviso">Anote os códigos: eles são mostrados só agora. {codigoConfirmacao && `O código de confirmação é informado ${pedido.modalidade === 'ENTREGA' ? 'ao entregador na chegada' : 'à equipe na retirada'}. `}Clientes com conta podem gerar um novo em “Minha conta”.</p>

        <div className="lj-acoes centro">
          {acompanhamento && <Link className="btn" to={acompanhamento}>Acompanhar pedido</Link>}
          <Link className="btn contorno" to="/cardapio">Voltar ao cardápio</Link>
        </div>
      </div>
    </div>
  )
}
