import { useMemo } from 'react'
import { useSearchParams } from 'react-router-dom'
import { get } from '../../api.js'
import Icon from '../../components/Icon.jsx'
import { Erro, brl, useCarga } from '../../ui.jsx'
import ProdutoModal from '../ProdutoModal.jsx'

const semAcento = (s) => s.normalize('NFD').replace(/\p{Diacritic}/gu, '').toLowerCase()

export default function Cardapio() {
  const [params, setParams] = useSearchParams()
  const categoria = params.get('categoria') ?? ''
  const busca = params.get('busca') ?? ''
  const produtoId = params.get('produto')
  const salvaId = params.get('salva')
  const { dados: catalogo, erro, carregando } = useCarga(() => get('/api/public/catalogo'), [])

  const mudar = (chave, valor) => {
    const n = new URLSearchParams(params)
    valor ? n.set(chave, valor) : n.delete(chave)
    n.delete('produto'); n.delete('salva')
    setParams(n, { replace: true })
  }

  const categorias = useMemo(() => {
    const mapa = new Map()
    catalogo?.forEach((p) => mapa.set(p.categoriaId, p.categoriaNome))
    return [...mapa].map(([id, nome]) => ({ id, nome })).sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR'))
  }, [catalogo])

  const produtos = useMemo(() => {
    const termo = semAcento(busca.trim())
    return (catalogo ?? []).filter((p) =>
      (!categoria || String(p.categoriaId) === categoria) &&
      (!termo || semAcento(`${p.nome} ${p.descricao ?? ''} ${p.categoriaNome}`).includes(termo)))
  }, [catalogo, categoria, busca])

  const aberto = catalogo?.find((p) => String(p.id) === produtoId)
  const abrir = (p) => { const n = new URLSearchParams(params); n.set('produto', p.id); n.delete('salva'); setParams(n, { replace: true }) }
  const fechar = () => { const n = new URLSearchParams(params); n.delete('produto'); n.delete('salva'); setParams(n, { replace: true }) }

  return (
    <div className="lj-pagina">
      <h1 className="lj-titulo-pagina">Cardápio</h1>

      <div className="lj-filtros">
        <div className="lj-chips" role="tablist" aria-label="Categorias">
          <button role="tab" aria-selected={!categoria} className={!categoria ? 'ativo' : ''} onClick={() => mudar('categoria', '')}>Todas</button>
          {categorias.map((c) => (
            <button key={c.id} role="tab" aria-selected={String(c.id) === categoria} className={String(c.id) === categoria ? 'ativo' : ''} onClick={() => mudar('categoria', String(c.id))}>{c.nome}</button>
          ))}
        </div>
        <label className="lj-busca claro">
          <Icon nome="search" tamanho={18} />
          <input value={busca} onChange={(e) => mudar('busca', e.target.value)} placeholder="Buscar no cardápio…" aria-label="Buscar no cardápio" />
        </label>
      </div>

      <Erro erro={erro} />
      {carregando && !catalogo && <p className="mudo">Carregando cardápio…</p>}
      {catalogo && produtos.length === 0 && (
        <p className="mudo lj-centro">{catalogo.length === 0 ? 'O cardápio ainda não tem produtos disponíveis.' : 'Nenhum produto encontrado para esse filtro.'}</p>
      )}

      <div className="lj-produtos">
        {produtos.map((p) => (
          <button key={p.id} className="lj-produto" onClick={() => abrir(p)}>
            <div className="lj-produto-img" style={p.imagemUrl ? { backgroundImage: `url(${p.imagemUrl})` } : undefined}>
              {!p.imagemUrl && <Icon nome="pizza" tamanho={40} />}
            </div>
            <div className="lj-produto-corpo">
              <small className="lj-categoria-rotulo">{p.categoriaNome}</small>
              <strong>{p.nome}</strong>
              {p.descricao && <span className="lj-produto-desc">{p.descricao}</span>}
              <span className="lj-produto-preco">
                {Number(p.precoBase) > 0 ? <>{p.grupos.length > 0 ? 'a partir de ' : ''}<b>{brl(p.precoBase)}</b></> : <small className="mudo">Monte e veja o preço</small>}
              </span>
            </div>
          </button>
        ))}
      </div>

      {aberto && <ProdutoModal produto={aberto} salvaId={salvaId ? Number(salvaId) : null} onFechar={fechar} />}
    </div>
  )
}
