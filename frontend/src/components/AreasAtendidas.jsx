import { useMemo, useState } from 'react'
import { get, post, put } from '../api.js'
import ImportarCidadeModal from './ImportarCidadeModal.jsx'
import Icon from './Icon.jsx'
import { Campo, Chave, Erro, useAcao, useCarga } from '../ui.jsx'

const normalizar = (s) => s.normalize('NFD').replace(/\p{Diacritic}/gu, '').toLowerCase().trim()

/** Agrupa as áreas por município (código IBGE quando existe; senão pelo nome digitado). */
function agrupar(areas) {
  const mapa = new Map()
  areas.forEach((a) => {
    const chave = a.cidadeIbgeId ? `ibge:${a.cidadeIbgeId}` : `nome:${normalizar(a.cidade)}`
    if (!mapa.has(chave)) mapa.set(chave, { chave, cidade: a.cidade, uf: a.uf, ibgeId: a.cidadeIbgeId, areas: [] })
    mapa.get(chave).areas.push(a)
  })
  return [...mapa.values()].sort((a, b) => a.cidade.localeCompare(b.cidade, 'pt-BR') || (a.uf ?? '').localeCompare(b.uf ?? ''))
}

/** Cidades e bairros atendidos pela entrega: importação por cidade, cadastro manual e ativação por bairro. */
export default function AreasAtendidas() {
  const areas = useCarga(() => get('/api/gestao/entrega/areas'), [])
  const acao = useAcao()
  const [bairro, setBairro] = useState('')
  const [cidade, setCidade] = useState('')
  const [filtro, setFiltro] = useState('')
  const [abertas, setAbertas] = useState(() => new Set())
  const [importando, setImportando] = useState(false)

  const termo = normalizar(filtro)
  const grupos = useMemo(() => {
    const todas = areas.dados ?? []
    const filtradas = termo ? todas.filter((a) => normalizar(`${a.bairro} ${a.cidade} ${a.uf ?? ''}`).includes(termo)) : todas
    const totais = new Map() // contagens da cidade inteira, mesmo com o filtro aplicado
    agrupar(todas).forEach((g) => totais.set(g.chave, { total: g.areas.length, ativas: g.areas.filter((a) => a.ativa).length }))
    return agrupar(filtradas).map((g) => ({ ...g, ...totais.get(g.chave) }))
  }, [areas.dados, termo])
  const temImportada = areas.dados?.some((a) => a.origem === 'IBGE_OSM')

  const alternar = (chave) => setAbertas((s) => { const n = new Set(s); n.has(chave) ? n.delete(chave) : n.add(chave); return n })
  const definirAtiva = (a, ativa) => acao.executar(async () => {
    await put(`/api/gestao/entrega/areas/${a.id}`, { bairro: a.bairro, cidade: a.cidade, ativa })
    await areas.recarregar()
  })

  // Depois da importação (ou de um cadastro manual na janela), recarrega a lista e abre a cidade
  const aoMudar = async (ibgeId) => {
    await areas.recarregar()
    setAbertas((s) => new Set(s).add(`ibge:${ibgeId}`))
  }

  return (
    <>
      <Erro erro={acao.erro} /><Erro erro={areas.erro} />

      <section className="cartao">
        <div className="linha entre">
          <div>
            <h2>Cidades e bairros atendidos</h2>
            <p className="mudo">Endereços fora das áreas ativas são bloqueados na entrega. A taxa de entrega fica em Configurações.</p>
          </div>
          <button className="btn" onClick={() => setImportando(true)}><Icon nome="plus" tamanho={16} /> Adicionar cidade</button>
        </div>

        <form className="linha" onSubmit={(e) => {
          e.preventDefault()
          acao.executar(async () => { await post('/api/gestao/entrega/areas', { bairro, cidade }); setBairro(''); await areas.recarregar() })
        }}>
          <Campo label="Bairro"><input value={bairro} onChange={(e) => setBairro(e.target.value)} required /></Campo>
          <Campo label="Cidade"><input value={cidade} onChange={(e) => setCidade(e.target.value)} required /></Campo>
          <button className="btn sec" disabled={acao.ocupado}>Adicionar bairro manualmente</button>
        </form>

        {(areas.dados?.length ?? 0) > 0 && (
          <label className="campo filtro-areas"><span>Filtrar bairros ou cidades</span>
            <input value={filtro} onChange={(e) => setFiltro(e.target.value)} placeholder="Digite para filtrar…" />
          </label>
        )}

        {areas.carregando && !areas.dados && <p className="mudo">Carregando…</p>}
        {areas.dados?.length === 0 && <p className="mudo">Nenhuma área cadastrada — nenhuma entrega será aceita.</p>}
        {areas.dados?.length > 0 && grupos.length === 0 && <p className="mudo">Nenhum bairro encontrado para “{filtro}”.</p>}

        <div className="cidades">
          {grupos.map((g) => {
            const aberta = termo !== '' || abertas.has(g.chave)
            return (
              <article key={g.chave} className="cidade-grupo">
                <button type="button" className="cidade-topo" aria-expanded={aberta} onClick={() => alternar(g.chave)}>
                  <span className={`seta ${aberta ? 'aberta' : ''}`}><Icon nome="arrow" tamanho={18} /></span>
                  <strong>{g.cidade}</strong>
                  {g.uf && <span className="badge">{g.uf}</span>}
                  <small className="mudo">{g.ativas} de {g.total} {g.total === 1 ? 'bairro atendido' : 'bairros atendidos'}</small>
                </button>
                {aberta && (
                  <table>
                    <thead><tr><th>Bairro</th><th>Origem</th><th>Atendido</th></tr></thead>
                    <tbody>
                      {g.areas.map((a) => (
                        <tr key={a.id}>
                          <td>{a.bairro}</td>
                          <td className="mudo">{a.origem === 'IBGE_OSM' ? 'Importado' : 'Manual'}</td>
                          <td><Chave ligado={a.ativa} desabilitado={acao.ocupado} aoAlterar={(v) => definirAtiva(a, v)} rotulo={a.ativa ? 'Atendido' : 'Desativado'} /></td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )}
              </article>
            )
          })}
        </div>
        {temImportada && <p className="mudo pequeno">Bairros importados: dados © colaboradores do OpenStreetMap (ODbL). A lista pode estar incompleta; complete com o cadastro manual.</p>}
      </section>

      {importando && <ImportarCidadeModal onFechar={() => setImportando(false)} aoMudar={aoMudar} />}
    </>
  )
}
