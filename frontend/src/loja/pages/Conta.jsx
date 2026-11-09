import { useState } from 'react'
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router-dom'
import { del, get, post } from '../../api.js'
import Icon from '../../components/Icon.jsx'
import { Badge, Erro, brl, dataHora, rotulo, useAcao, useCarga } from '../../ui.jsx'
import { useCliente } from '../cliente.jsx'
import EnderecoForm from '../EnderecoForm.jsx'

const ABAS = [['pedidos', 'Pedidos'], ['enderecos', 'Endereços'], ['pizzas', 'Pizzas salvas'], ['dados', 'Meus dados']]
const ENCERRADOS = ['CONCLUIDO', 'CANCELADO']

function Pedidos() {
  const { dados, erro, carregando, recarregar } = useCarga(() => get('/api/cliente/pedidos'), [], 20000)
  const acao = useAcao()
  const [codigos, setCodigos] = useState({})
  const [cancelados, setCancelados] = useState({})

  const cancelar = (p) => {
    if (!window.confirm(`Solicitar o cancelamento do pedido ${p.numeroPedido}?`)) return
    acao.executar(async () => {
      const r = await post(`/api/cliente/pedidos/${p.id}/cancelamento`)
      setCancelados((c) => ({ ...c, [p.id]: r.estado }))
      await recarregar()
    })
  }
  const novoCodigo = (p) => acao.executar(async () => {
    const r = await post(`/api/cliente/pedidos/${p.id}/codigo-confirmacao`)
    setCodigos((c) => ({ ...c, [p.id]: r.codigoConfirmacao }))
  })

  return (
    <>
      <Erro erro={erro} />
      <Erro erro={acao.erro} />
      {carregando && !dados && <p className="mudo">Carregando…</p>}
      {dados?.length === 0 && <p className="mudo">Você ainda não fez pedidos com esta conta. <Link to="/cardapio">Ver o cardápio</Link></p>}
      <div className="lj-lista">
        {dados?.map((p) => (
          <article key={p.id} className="lj-cartao">
            <div className="lj-item-topo">
              <h3>Pedido {p.numeroPedido}</h3>
              <Badge valor={p.estado} />
            </div>
            <p className="mudo pequeno">{rotulo(p.modalidade)} · {dataHora(p.criadoEm)} · total <strong>{brl(p.total)}</strong>{Number(p.taxaEntrega) > 0 && ` (entrega ${brl(p.taxaEntrega)})`}</p>
            <ul className="lj-lista-simples">
              {p.itens.map((i, k) => <li key={k}>{i.quantidade}× {i.nomeProduto}</li>)}
            </ul>
            {codigos[p.id] && <p className="alerta aviso">Novo código de confirmação: <strong className="lj-codigo pequeno">{codigos[p.id]}</strong></p>}
            {cancelados[p.id] && <p className="alerta aviso" role="status">Cancelamento solicitado: {rotulo(cancelados[p.id])}.</p>}
            {!ENCERRADOS.includes(p.estado) && (
              <div className="lj-acoes">
                {p.modalidade === 'ENTREGA' && <button className="btn contorno" disabled={acao.ocupado} onClick={() => novoCodigo(p)}>Gerar novo código de confirmação</button>}
                <button className="lj-link-texto" disabled={acao.ocupado || !!cancelados[p.id]} onClick={() => cancelar(p)}>Solicitar cancelamento</button>
              </div>
            )}
          </article>
        ))}
      </div>
    </>
  )
}

function Enderecos() {
  const { dados, erro, recarregar } = useCarga(() => get('/api/cliente/enderecos'), [])
  const [edicao, setEdicao] = useState(null) // null | 'novo' | endereço
  const acao = useAcao()

  const remover = (e) => {
    if (!window.confirm('Remover este endereço?')) return
    acao.executar(async () => { await del(`/api/cliente/enderecos/${e.id}`); await recarregar() })
  }

  return (
    <>
      <Erro erro={erro} />
      <Erro erro={acao.erro} />
      {edicao ? (
        <div className="lj-cartao">
          <h3>{edicao === 'novo' ? 'Novo endereço' : 'Editar endereço'}</h3>
          <EnderecoForm endereco={edicao === 'novo' ? null : edicao} aoCancelar={() => setEdicao(null)} aoSalvar={async () => { setEdicao(null); await recarregar() }} />
        </div>
      ) : (
        <>
          <div className="lj-lista">
            {dados?.map((e) => (
              <article key={e.id} className="lj-cartao lj-endereco-card">
                <Icon nome="pin" tamanho={22} />
                <div>
                  <strong>{e.identificacao || 'Endereço'}{e.padrao && <em> · padrão</em>}</strong>
                  <small>{e.logradouro}, {e.numero}{e.complemento ? ` — ${e.complemento}` : ''}</small>
                  <small>{e.bairro} · {e.cidade}/{e.estado} · CEP {e.cep}</small>
                </div>
                <div className="lj-acoes">
                  <button className="lj-link-texto" onClick={() => setEdicao(e)}>Editar</button>
                  <button className="lj-link-texto perigo" onClick={() => remover(e)}>Remover</button>
                </div>
              </article>
            ))}
          </div>
          {dados?.length === 0 && <p className="mudo">Nenhum endereço salvo.</p>}
          <button className="btn" onClick={() => setEdicao('novo')}><Icon nome="plus" tamanho={16} /> Novo endereço</button>
        </>
      )}
    </>
  )
}

function Pizzas() {
  const navigate = useNavigate()
  const { dados, erro, recarregar } = useCarga(() => get('/api/cliente/pizzas'), [])
  const catalogo = useCarga(() => get('/api/public/catalogo'), [])
  const acao = useAcao()
  const produto = (id) => catalogo.dados?.find((p) => p.id === id)

  const remover = (p) => acao.executar(async () => { await del(`/api/cliente/pizzas/${p.id}`); await recarregar() })

  return (
    <>
      <Erro erro={erro} />
      <Erro erro={acao.erro} />
      {dados?.length === 0 && <p className="mudo">Você ainda não salvou nenhuma montagem. Ao montar um produto, use “Salvar esta montagem”.</p>}
      <div className="lj-lista">
        {dados?.map((p) => {
          const prod = produto(p.produtoId)
          return (
            <article key={p.id} className="lj-cartao lj-endereco-card">
              <Icon nome="pizza" tamanho={22} />
              <div>
                <strong>{p.nome}</strong>
                <small>{prod ? prod.nome : 'Produto indisponível no momento'} · salva em {dataHora(p.criadoEm)}</small>
              </div>
              <div className="lj-acoes">
                {prod && <button className="btn" onClick={() => navigate(`/cardapio?produto=${p.produtoId}&salva=${p.id}`)}>Pedir</button>}
                <button className="lj-link-texto perigo" onClick={() => remover(p)}>Excluir</button>
              </div>
            </article>
          )
        })}
      </div>
    </>
  )
}

function Dados() {
  const { dados, erro } = useCarga(() => get('/api/cliente/me'), [])
  return (
    <div className="lj-cartao">
      <Erro erro={erro} />
      {dados && (
        <dl className="lj-dados">
          <dt>Nome</dt><dd>{dados.nome}</dd>
          <dt>E-mail</dt><dd>{dados.email}</dd>
          <dt>CPF</dt><dd>{dados.cpf}</dd>
        </dl>
      )}
    </div>
  )
}

export default function Conta() {
  const { cliente, sair } = useCliente()
  const [params, setParams] = useSearchParams()
  const aba = params.get('aba') ?? 'pedidos'
  if (!cliente) return <Navigate to="/entrar?volta=/conta" replace />

  return (
    <div className="lj-pagina">
      <div className="lj-item-topo">
        <h1 className="lj-titulo-pagina">Olá, {cliente.nome.split(' ')[0]}</h1>
        <button className="lj-link-texto" onClick={sair}>Sair</button>
      </div>
      <div className="lj-abas" role="tablist">
        {ABAS.map(([id, nome]) => (
          <button key={id} role="tab" aria-selected={aba === id} className={aba === id ? 'ativo' : ''} onClick={() => setParams({ aba: id }, { replace: true })}>{nome}</button>
        ))}
      </div>
      {aba === 'pedidos' && <Pedidos />}
      {aba === 'enderecos' && <Enderecos />}
      {aba === 'pizzas' && <Pizzas />}
      {aba === 'dados' && <Dados />}
    </div>
  )
}
