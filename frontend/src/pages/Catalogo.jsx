import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { api, get, post } from '../api.js'
import { Badge, Campo, Chave, Erro, brl, rotulo, useAcao, useCarga } from '../ui.jsx'
import Icon from '../components/Icon.jsx'
import { miniatura } from '../components/SeletorImagem.jsx'
import ProdutoWizard from '../components/ProdutoWizard.jsx'
import MenuAcoes from '../components/MenuAcoes.jsx'
import CategoriasAba from '../components/CategoriasAba.jsx'
import InsumosAba from '../components/InsumosAba.jsx'

// ---------------------------------------------------------------- editor de produto

function Produtos({ categorias, aoCriarCategoria, aoCadastrarInsumo }) {
  const { dados, erro, recarregar } = useCarga(() => get('/api/gestao/produtos'), [])
  const [editando, setEditando] = useState(undefined) // undefined = fechado | null = novo | id
  const acao = useAcao()
  const [busca, setBusca] = useState('')
  const [exibir, setExibir] = useState('TODOS') // TODOS | ATIVOS | INATIVOS
  const [ordem, setOrdem] = useState('NOME_ASC')

  const normalizar = (t) => (t ?? '').toString().normalize('NFD').replace(/\p{Diacritic}/gu, '').toLowerCase()
  const comparadores = {
    NOME_ASC: (a, b) => a.nome.localeCompare(b.nome, 'pt-BR'),
    NOME_DESC: (a, b) => b.nome.localeCompare(a.nome, 'pt-BR'),
    PRECO_ASC: (a, b) => (a.precoBase ?? Infinity) - (b.precoBase ?? Infinity),
    PRECO_DESC: (a, b) => (b.precoBase ?? -Infinity) - (a.precoBase ?? -Infinity),
    CATEGORIA: (a, b) => a.categoriaNome.localeCompare(b.categoriaNome, 'pt-BR') || a.nome.localeCompare(b.nome, 'pt-BR'),
    RECENTES: (a, b) => b.id - a.id,
    ANTIGOS: (a, b) => a.id - b.id,
  }
  const termo = normalizar(busca.trim())
  const lista = (dados ?? [])
    .filter((p) => exibir === 'TODOS' || (exibir === 'ATIVOS' ? p.disponivel : !p.disponivel))
    .filter((p) => !termo || [p.nome, p.categoriaNome, p.descricao, p.tipo === 'SIMPLES' ? 'simples' : 'composto'].some((t) => normalizar(t).includes(termo)))
    .sort(comparadores[ordem])
  const filtrando = termo || exibir !== 'TODOS'
  const totais = {
    todos: (dados ?? []).length,
    ativos: (dados ?? []).filter((p) => p.disponivel).length,
    inativos: (dados ?? []).filter((p) => !p.disponivel).length,
  }

  const alternar = (p, disponivel) =>
    acao.executar(async () => { await post(`/api/gestao/produtos/${p.id}/disponibilidade`, { disponivel }); await recarregar() })
  const excluir = (p) => {
    const aviso = [
      `Excluir definitivamente o produto “${p.nome}”?`,
      'Seus grupos, opções e adicionais também serão removidos, assim como pizzas salvas de clientes com ele. '
        + 'Os pedidos já feitos continuam com o histórico.',
      'Para apenas tirá-lo do catálogo, desligue a chave “Ativo”. Esta ação não pode ser desfeita.',
    ].join('\n\n')
    if (!window.confirm(aviso)) return
    acao.executar(async () => { await api(`/api/gestao/produtos/${p.id}`, { method: 'DELETE' }); await recarregar() })
  }
  return (
    <section className="cartao">
      <div className="linha entre"><h2>Produtos</h2>
        <button className="btn" disabled={!categorias.length} onClick={() => setEditando(null)}>+ Novo produto</button></div>
      {!categorias.length && (
        <div className="alerta erro">
          <strong>Antes de criar produtos, cadastre ao menos uma categoria.</strong>
          <div><button className="btn" onClick={aoCriarCategoria}>Cadastrar categoria</button></div>
        </div>
      )}
      <Erro erro={erro} /><Erro erro={acao.erro} />
      <div className="barra-filtros">
        <label className="busca-campo">
          <Icon nome="search" tamanho={18} />
          <input type="search" value={busca} onChange={(e) => setBusca(e.target.value)} placeholder="Pesquisar produto…" aria-label="Pesquisar produtos" />
        </label>
        <div className="segmentos" role="group" aria-label="Exibir">
          {[['TODOS', 'Todos', totais.todos], ['ATIVOS', 'Ativos', totais.ativos], ['INATIVOS', 'Desativados', totais.inativos]].map(([k, t, n]) => (
            <button type="button" key={k} className={exibir === k ? 'ativo' : ''} aria-pressed={exibir === k} onClick={() => setExibir(k)}>
              {t}<span>{n}</span>
            </button>
          ))}
        </div>
        <label className="ordenar">
          <span>Ordenar</span>
          <select value={ordem} onChange={(e) => setOrdem(e.target.value)} aria-label="Ordenar por">
            <option value="NOME_ASC">Nome (A–Z)</option>
            <option value="NOME_DESC">Nome (Z–A)</option>
            <option value="PRECO_ASC">Preço: menor primeiro</option>
            <option value="PRECO_DESC">Preço: maior primeiro</option>
            <option value="CATEGORIA">Categoria</option>
            <option value="RECENTES">Mais recentes</option>
            <option value="ANTIGOS">Mais antigos</option>
          </select>
        </label>
      </div>
      {dados && (
        <p className="resumo-filtro">
          {lista.length} de {dados.length} produto(s)
          {filtrando && <button type="button" className="link-limpar" onClick={() => { setBusca(''); setExibir('TODOS') }}>Limpar filtros</button>}
        </p>
      )}
      <div className="tabela-rolavel">
      <table className="tabela-mobile">
        <thead><tr><th>Produto</th><th>Categoria</th><th>Tipo</th><th>Preço</th><th>Status</th><th>Situação</th><th /></tr></thead>
        <tbody>
          {lista.map((p) => (
            <tr key={p.id} className={`clicavel ${p.disponivel ? '' : 'inativo'}`} onClick={() => setEditando(p.id)}>
              <td data-label="Produto"><span className="produto-celula"><span className="miniatura">{p.imagemUrl ? <img src={miniatura(p.imagemUrl)} alt="" loading="lazy" /> : <Icon nome="pizza" tamanho={18} />}</span><strong>{p.nome}</strong></span></td><td data-label="Categoria">{p.categoriaNome}</td>
              <td data-label="Tipo">{rotulo(p.tipo)}{p.estrategia && <small className="mudo"> · {rotulo(p.estrategia)}</small>}</td><td data-label="Preço">{brl(p.precoBase)}</td>
              <td data-label="Status"><Badge valor={p.status} /></td>
              <td data-label="Disponibilidade"><Chave ligado={p.disponivel} desabilitado={acao.ocupado} aoAlterar={(v) => alternar(p, v)} rotulo={p.disponivel ? 'Ativo' : 'Inativo'} /></td>
              <td className="acoes-linha">
                <MenuAcoes desabilitado={acao.ocupado} rotulo={`Ações de ${p.nome}`}
                  itens={[{ texto: 'Editar', aoClicar: () => setEditando(p.id) }, { texto: 'Excluir', perigo: true, aoClicar: () => excluir(p) }]} />
              </td>
            </tr>
          ))}
          {dados && lista.length === 0 && (
            <tr><td colSpan="7" className="mudo">{dados.length === 0 ? 'Nenhum produto cadastrado.' : 'Nenhum produto corresponde aos filtros.'}</td></tr>
          )}
        </tbody>
      </table>
      </div>
      {editando !== undefined && (
        <ProdutoWizard key={editando ?? 'novo'} produtoId={editando} categorias={categorias}
          aoFechar={() => setEditando(undefined)} aoSalvar={recarregar} aoCadastrarInsumo={aoCadastrarInsumo} />
      )}
    </section>
  )
}

export default function Catalogo() {
  const [params, setParams] = useSearchParams()
  const aba = params.get('aba') ?? 'produtos'
  const setAba = (k) => setParams({ aba: k })
  const categorias = useCarga(() => get('/api/gestao/categorias'), [])
  return (
    <>
      <h1>Catálogo</h1>
      <div className="abas">
        {[['produtos', 'Produtos'], ['categorias', 'Categorias'], ['insumos', 'Insumos']].map(([k, t]) => (
          <button key={k} className={aba === k ? 'ativo' : ''} onClick={() => setAba(k)}>{t}</button>
        ))}
      </div>
      {aba === 'produtos' && <Produtos categorias={categorias.dados ?? []} aoCriarCategoria={() => setAba('categorias')} aoCadastrarInsumo={() => setAba('insumos')} />}
      {aba === 'categorias' && <CategoriasAba aoMudar={categorias.recarregar} />}
      {aba === 'insumos' && <InsumosAba />}
    </>
  )
}
