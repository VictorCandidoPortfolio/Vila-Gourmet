import { useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import Icon from '../components/Icon.jsx'
import { Erro, brl, useAcao } from '../ui.jsx'
import { useCarrinho } from './carrinho.jsx'
import { useLoja } from './lojaStatus.jsx'

/** Painel lateral do carrinho: itens, quantidades e total. A cotação final é refeita ao finalizar. */
export default function CarrinhoGaveta() {
  const { itens, total, aberto, setAberto, remover, alterarQuantidade, limpar } = useCarrinho()
  const navigate = useNavigate()
  const { aberta } = useLoja()
  const acao = useAcao()

  useEffect(() => {
    if (!aberto) return undefined
    const fn = (e) => e.key === 'Escape' && setAberto(false)
    window.addEventListener('keydown', fn)
    return () => window.removeEventListener('keydown', fn)
  }, [aberto, setAberto])

  if (!aberto) return null
  const ir = (rota) => { setAberto(false); navigate(rota) }

  return (
    <div className="lj-gaveta-fundo" onMouseDown={(e) => e.target === e.currentTarget && setAberto(false)}>
      <aside className="lj-gaveta" role="dialog" aria-modal="true" aria-label="Carrinho">
        <header>
          <h2>Seu carrinho</h2>
          <button className="lj-icone" onClick={() => setAberto(false)} aria-label="Fechar"><Icon nome="x" /></button>
        </header>

        {itens.length === 0 ? (
          <div className="lj-vazio">
            <Icon nome="cart" tamanho={40} />
            <p>Seu carrinho está vazio.</p>
            <button className="btn" onClick={() => ir('/cardapio')}>Ver o cardápio</button>
          </div>
        ) : (
          <>
            <ul className="lj-itens">
              {itens.map((i) => (
                <li key={i.uid}>
                  <div className="lj-item-topo">
                    <strong>{i.nomeProduto}</strong>
                    <span>{brl(i.subtotal)}</span>
                  </div>
                  {i.resumo.map((l) => <small key={l} className="mudo">{l}</small>)}
                  <div className="lj-item-base">
                    <div className="lj-qtd" aria-label={`Quantidade de ${i.nomeProduto}`}>
                      <button disabled={i.quantidade <= 1 || acao.ocupado} onClick={() => acao.executar(() => alterarQuantidade(i.uid, i.quantidade - 1))} aria-label="Diminuir"><Icon nome="minus" tamanho={16} /></button>
                      <span>{i.quantidade}</span>
                      <button disabled={acao.ocupado} onClick={() => acao.executar(() => alterarQuantidade(i.uid, i.quantidade + 1))} aria-label="Aumentar"><Icon nome="plus" tamanho={16} /></button>
                    </div>
                    <button className="lj-link-texto" onClick={() => remover(i.uid)}>Remover</button>
                  </div>
                </li>
              ))}
            </ul>
            <Erro erro={acao.erro} />
            <footer>
              <div className="lj-total"><span>Subtotal</span><strong>{brl(total)}</strong></div>
              <small className="mudo">Taxa de entrega, quando houver, é calculada ao finalizar.</small>
              {!aberta && <p className="alerta erro" role="status">Estamos fechados no momento: não é possível finalizar pedidos.</p>}
              <button className="btn grande" disabled={!aberta} onClick={() => ir('/finalizar')}>Finalizar pedido</button>
              <button className="lj-link-texto" onClick={limpar}>Esvaziar carrinho</button>
            </footer>
          </>
        )}
      </aside>
    </div>
  )
}
