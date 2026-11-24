import Icon from './Icon.jsx'
import EtapasMontagem from './EtapasMontagem.jsx'
import { useMontagem } from '../montagem.js'
import { Modal, brl } from '../ui.jsx'

function Passo({ valor, min = 0, max, aoMudar, rotulo }) {
  return (
    <div className="pdv-qtd" role="group" aria-label={rotulo}>
      <button type="button" disabled={valor <= min} onClick={() => aoMudar(valor - 1)} aria-label="Diminuir"><Icon nome="minus" tamanho={16} /></button>
      <span>{valor}</span>
      <button type="button" disabled={max != null && valor >= max} onClick={() => aoMudar(valor + 1)} aria-label="Aumentar"><Icon nome="plus" tamanho={16} /></button>
    </div>
  )
}

/**
 * Montagem de um produto na frente de caixa (mesma lógica da loja): opções com mínimo/máximo, adicionais e
 * quantidade, com o preço calculado pelo servidor. Só adiciona ao pedido com uma montagem válida.
 */
export default function PdvProdutoModal({ produto, onAdicionar, onFechar }) {
  const m = useMontagem(produto)
  const { grupos, adicionaisDisp, quantidade, opcoes, adicionais, selecao, cotacao, problema } = m

  return (
    <Modal titulo={produto.nome} onFechar={onFechar} largo>
      {produto.descricao && <p className="mudo">{produto.descricao}</p>}

      <EtapasMontagem key={produto.id} grupos={grupos} temAdicionais={adicionaisDisp.length > 0} renderGrupo={(g) => {
        const n = m.totalDoGrupo(g)
        const unica = g.maximo === 1
        return (
          <fieldset className="pdv-grupo">
            <legend>
              <strong>{g.nome}</strong>
              <small className={n >= g.minimo ? 'ok' : 'falta'}>
                {g.maximo === g.minimo ? `Escolha ${g.minimo}` : `Escolha de ${g.minimo} a ${g.maximo}`}{g.maximo > 1 && ` · ${n} selecionado${n === 1 ? '' : 's'}`}
              </small>
            </legend>
            {g.opcoes.filter((o) => o.disponivel).map((o) => {
              const q = opcoes[o.id] ?? 0
              const cobra = Number(o.preco) > 0 && g.modalidadeCobranca !== 'GRATUITA'
              return (
                <div key={o.id} className={`pdv-opcao ${q > 0 ? 'marcada' : ''}`}>
                  {unica || o.maximo <= 1 ? (
                    <button type="button" className="pdv-opcao-btn" role={unica ? 'radio' : 'checkbox'} aria-checked={q > 0} onClick={() => m.definir(g, o, q > 0 ? 0 : 1)}>
                      <span className={`pdv-marca ${unica ? 'redonda' : ''}`}>{q > 0 && <Icon nome="check" tamanho={14} />}</span>
                      <span className="pdv-opcao-nome">{o.nome}</span>
                      {cobra && <span className="mudo">+ {brl(o.preco)}</span>}
                    </button>
                  ) : (
                    <>
                      <span className="pdv-opcao-btn estatico">
                        <span className="pdv-opcao-nome">{o.nome}</span>
                        {cobra && <span className="mudo">+ {brl(o.preco)}</span>}
                      </span>
                      <Passo valor={q} max={o.maximo} aoMudar={(v) => m.definir(g, o, v)} rotulo={o.nome} />
                    </>
                  )}
                </div>
              )
            })}
          </fieldset>
        )
      }} renderAdicionais={() => (
        <fieldset className="pdv-grupo">
          <legend><strong>Adicionais</strong><small>opcional</small></legend>
          {adicionaisDisp.map((a) => (
            <div key={a.id} className={`pdv-opcao ${(adicionais[a.id] ?? 0) > 0 ? 'marcada' : ''}`}>
              <span className="pdv-opcao-btn estatico">
                <span className="pdv-opcao-nome">{a.nome}</span>
                <span className="mudo">+ {brl(a.preco)}</span>
              </span>
              <Passo valor={adicionais[a.id] ?? 0} aoMudar={(v) => m.definirAdicional(a.id, v)} rotulo={a.nome} />
            </div>
          ))}
        </fieldset>
      )} />

      <div className="pdv-modal-base">
        <Passo valor={quantidade} min={1} aoMudar={m.setQuantidade} rotulo="Quantidade" />
        <div className="pdv-modal-preco">
          {cotacao ? <strong>{brl(cotacao.item.subtotal)}</strong>
            : problema ? <small className="falta" role="status">{problema.message}</small>
            : <small className="mudo">Calculando…</small>}
        </div>
        <button className="btn" disabled={!cotacao} onClick={() => onAdicionar(selecao, cotacao)}>Adicionar ao pedido</button>
      </div>
    </Modal>
  )
}
