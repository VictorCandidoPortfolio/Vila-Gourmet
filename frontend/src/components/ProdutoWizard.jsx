import { useEffect, useState } from 'react'
import { get, post, put } from '../api.js'
import { Badge, Campo, Erro, Modal, brl, useCarga } from '../ui.jsx'
import { COBRANCAS, ESTRATEGIAS } from './constantes.js'
import SeletorImagem, { miniatura } from './SeletorImagem.jsx'

/**
 * Cadastro e edição de produto em etapas: 1) informações gerais, 2) grupos (ou adicionais) e 3) revisão.
 *
 * Criação: o rascunho vive só na tela e nada é gravado até a confirmação na etapa 3.
 * Edição: mesma ordem das etapas, carregada com os dados atuais; "Salvar" fica disponível em qualquer
 * etapa e aplica apenas as diferenças. Itens já salvos não são apagados (preservam o histórico dos
 * pedidos): ficam inativos e podem ser reativados.
 * A validação que vale é a do servidor; as dicas da tela apenas antecipam os conflitos.
 */

const GERAL_VAZIO = (categorias) => ({
  tipo: 'SIMPLES', categoriaId: categorias[0]?.id ?? '', nome: '', descricao: '', imagemUrl: '',
  precoBase: '', disponivel: true, estrategia: 'SOMA', participaPrecoBase: false, imagemBase64: null,
})
const GRUPO_VAZIO = { nome: '', minimo: '1', maximo: '1', modalidadeCobranca: 'PRINCIPAL' }

// ---------------------------------------------------------------- conversões servidor <-> rascunho

function deProduto(p) {
  return {
    geral: {
      tipo: p.tipo, categoriaId: String(p.categoriaId), nome: p.nome, descricao: p.descricao ?? '',
      imagemUrl: p.imagemUrl ?? '', precoBase: p.precoBase == null ? '' : String(p.precoBase),
      disponivel: p.disponivel, estrategia: p.estrategia ?? 'SOMA', participaPrecoBase: !!p.participaPrecoBase,
      imagemBase64: null,
    },
    grupos: p.grupos.map((g) => ({
      id: g.id, nome: g.nome, minimo: g.minimo, maximo: g.maximo, modalidadeCobranca: g.modalidadeCobranca, ativo: g.ativo,
      opcoes: g.opcoes.map((o) => ({
        id: o.id, insumoId: o.insumoId, nome: o.nome, preco: Number(o.preco), minimo: o.minimo, maximo: o.maximo, disponivel: o.disponivel,
      })),
    })),
    adicionais: p.adicionais.map((a) => ({ id: a.id, nome: a.nome, preco: Number(a.preco), disponivel: a.disponivel })),
  }
}

const corpoProduto = (g) => ({
  categoriaId: Number(g.categoriaId), tipo: g.tipo, nome: g.nome.trim(),
  descricao: g.descricao || null, imagemUrl: g.imagemUrl || null, imagemBase64: g.imagemBase64 || null,
  precoBase: g.precoBase === '' ? null : Number(g.precoBase), disponivel: g.disponivel,
  estrategia: g.tipo === 'COMPOSTO' ? g.estrategia : null,
  participaPrecoBase: g.tipo === 'COMPOSTO' ? !!g.participaPrecoBase : null,
})
const corpoGrupo = (g) => ({ nome: g.nome, minimo: g.minimo, maximo: g.maximo, modalidadeCobranca: g.modalidadeCobranca, ativo: g.ativo })
const corpoOpcao = (o) => ({ insumoId: o.insumoId, preco: o.preco, minimo: o.minimo, maximo: o.maximo, disponivel: o.disponivel })
const corpoAdicional = (a) => ({ nome: a.nome, preco: a.preco, disponivel: a.disponivel })
const mudou = (a, b, campos) => !b || campos.some((c) => a[c] !== b[c])

/** Antecipa os conflitos que o servidor aponta ao publicar. */
function dicas(d) {
  const g = d.geral
  const p = []
  if (g.tipo === 'SIMPLES') {
    if (g.precoBase === '') p.push('Informe o preço de venda')
    return p
  }
  const fixo = g.estrategia === 'FIXO'
  if ((fixo || g.participaPrecoBase) && g.precoBase === '') {
    p.push(fixo ? 'A estratégia FIXO exige o preço-base' : 'O preço-base participa do cálculo, mas não foi informado')
  }
  const ativos = d.grupos.filter((x) => x.ativo)
  if (!ativos.length) p.push('Crie ao menos um grupo de opções ativo')
  let principalObrigatorio = false
  ativos.forEach((gr) => {
    const r = `Grupo '${gr.nome}': `
    const disp = gr.opcoes.filter((o) => o.disponivel)
    if (!disp.length) { p.push(r + 'adicione ao menos uma opção'); return }
    if (gr.maximo < 1) p.push(r + 'o máximo deve ser pelo menos 1')
    const somaMax = disp.reduce((s, o) => s + o.maximo, 0)
    const somaMin = disp.reduce((s, o) => s + o.minimo, 0)
    if (gr.minimo > somaMax) p.push(`${r}o mínimo do grupo (${gr.minimo}) é maior que a soma dos máximos das opções (${somaMax})`)
    if (somaMin > gr.maximo) p.push(`${r}a soma dos mínimos das opções (${somaMin}) é maior que o máximo do grupo (${gr.maximo})`)
    disp.filter((o) => o.minimo > gr.maximo).forEach((o) => p.push(`${r}o mínimo da opção '${o.nome}' excede o máximo do grupo`))
    if (gr.modalidadeCobranca === 'PRINCIPAL') {
      if (fixo) p.push(r + 'a estratégia FIXO não usa grupos do cálculo principal (use cobrança separada ou gratuita)')
      else if (gr.minimo >= 1) principalObrigatorio = true
    }
  })
  if (!fixo && !principalObrigatorio) {
    p.push(`A estratégia ${g.estrategia} exige ao menos um grupo ativo de cobrança principal com mínimo ≥ 1`)
  }
  return p
}

function Etapas({ etapa, rotuloEtapa2, aoIr }) {
  const nomes = ['Informações gerais', rotuloEtapa2, 'Revisão e confirmação']
  return (
    <ol className="etapas">
      {nomes.map((n, i) => (
        <li key={n} className={i + 1 === etapa ? 'atual' : i + 1 < etapa ? 'feita' : ''}>
          {aoIr
            ? <button type="button" onClick={() => aoIr(i + 1)}><span>{i + 1 < etapa ? '✓' : i + 1}</span>{n}</button>
            : <><span>{i + 1 < etapa ? '✓' : i + 1}</span>{n}</>}
        </li>
      ))}
    </ol>
  )
}

// ---------------------------------------------------------------- etapa 1

function EtapaGeral({ geral, setGeral, categorias, edicao }) {
  const composto = geral.tipo === 'COMPOSTO'
  const mudar = (campo, valor) => setGeral({ ...geral, [campo]: valor })
  return (
    <div className="coluna">
      <div className="linha">
        <Campo label="Tipo de produto" dica={edicao ? 'O tipo não pode ser alterado.' : undefined}>
          <select value={geral.tipo} disabled={edicao} onChange={(e) => mudar('tipo', e.target.value)}>
            <option value="SIMPLES">Simples (composição predefinida)</option>
            <option value="COMPOSTO">Composto (customizável)</option>
          </select>
        </Campo>
        <Campo label="Categoria">
          <select value={geral.categoriaId} onChange={(e) => mudar('categoriaId', e.target.value)} required>
            {categorias.filter((c) => c.ativa || String(c.id) === String(geral.categoriaId)).map((c) => <option key={c.id} value={c.id}>{c.nome}</option>)}
          </select>
        </Campo>
        <Campo label="Nome"><input value={geral.nome} onChange={(e) => mudar('nome', e.target.value)} required /></Campo>
      </div>
      <div className="linha">
        <Campo label={composto ? 'Preço-base' : 'Preço de venda'} dica={composto ? 'Obrigatório no Fixo e quando participa do cálculo' : undefined}>
          <input type="number" step="0.01" min="0" value={geral.precoBase} onChange={(e) => mudar('precoBase', e.target.value)} />
        </Campo>
        <label className="check"><input type="checkbox" checked={geral.disponivel} onChange={(e) => mudar('disponivel', e.target.checked)} /> Disponível</label>
      </div>
      <SeletorImagem rotulo="Foto do produto" url={geral.imagemUrl} base64={geral.imagemBase64}
        aoMudar={({ base64, url }) => setGeral({ ...geral, imagemBase64: base64, imagemUrl: url })} />
      <Campo label="Descrição"><textarea rows="2" value={geral.descricao} onChange={(e) => mudar('descricao', e.target.value)} /></Campo>
      {composto && (
        <div className="linha">
          <Campo label="Estratégia de precificação">
            <select value={geral.estrategia} onChange={(e) => mudar('estrategia', e.target.value)}>
              {Object.entries(ESTRATEGIAS).map(([k, t]) => <option key={k} value={k}>{t}</option>)}
            </select>
          </Campo>
          {geral.estrategia !== 'FIXO' && (
            <label className="check"><input type="checkbox" checked={geral.participaPrecoBase} onChange={(e) => mudar('participaPrecoBase', e.target.checked)} />
              Incluir o preço-base na fórmula (Soma/Média/Maior valor)</label>
          )}
        </div>
      )}
    </div>
  )
}

// ---------------------------------------------------------------- etapa 2: grupos

function JanelaOpcoes({ grupo, atualizar, alternarAtivo, insumos, onConcluir, aoCadastrarInsumo }) {
  const usados = new Set(grupo.opcoes.map((o) => o.insumoId))
  const livres = insumos.filter((i) => i.disponivel && !usados.has(i.id))
  const [insumoId, setInsumoId] = useState('')
  const [nova, setNova] = useState({ preco: '0', minimo: '0', maximo: '1' })

  const mudarGrupo = (campo, valor) => atualizar({ ...grupo, [campo]: valor })
  const mudarOpcao = (insumo, campo, valor) =>
    atualizar({ ...grupo, opcoes: grupo.opcoes.map((o) => (o.insumoId === insumo ? { ...o, [campo]: valor } : o)) })
  const removerOpcao = (o) => {
    // já salva = só desativa; ainda não salva = remove do rascunho
    if (o.id) mudarOpcao(o.insumoId, 'disponivel', !o.disponivel)
    else atualizar({ ...grupo, opcoes: grupo.opcoes.filter((x) => x.insumoId !== o.insumoId) })
  }
  const escolher = (id) => {
    setInsumoId(id)
    const ins = insumos.find((i) => String(i.id) === String(id))
    if (ins) setNova({ ...nova, preco: String(ins.valorReferencia ?? 0) })
  }
  const adicionar = (e) => {
    e.preventDefault()
    const ins = insumos.find((i) => String(i.id) === String(insumoId))
    if (!ins) return
    atualizar({ ...grupo, opcoes: [...grupo.opcoes, {
      insumoId: ins.id, nome: ins.nome, preco: Number(nova.preco), minimo: Number(nova.minimo), maximo: Number(nova.maximo), disponivel: true,
    }] })
    setInsumoId('')
  }

  return (
    <Modal titulo={`Opções do grupo: ${grupo.nome}`} onFechar={onConcluir} largo semEsc fixo>
      <div className="etapa-corpo">
        <div className="linha">
          <Campo label="Nome do grupo"><input value={grupo.nome} onChange={(e) => mudarGrupo('nome', e.target.value)} /></Campo>
          <Campo curto label="Mín. seleções" dica="0 = opcional"><input type="number" min="0" value={grupo.minimo} onChange={(e) => mudarGrupo('minimo', Number(e.target.value))} /></Campo>
          <Campo curto label="Máx. seleções"><input type="number" min="0" value={grupo.maximo} onChange={(e) => mudarGrupo('maximo', Number(e.target.value))} /></Campo>
          <Campo largo label="Cobrança">
            <select value={grupo.modalidadeCobranca} onChange={(e) => mudarGrupo('modalidadeCobranca', e.target.value)}>
              {Object.entries(COBRANCAS).map(([k, t]) => <option key={k} value={k}>{t}</option>)}
            </select>
          </Campo>
        </div>

        <h3>Opções do grupo (insumos)</h3>
        {grupo.opcoes.length > 0 ? (
          <div className="rolagem">
            <table>
              <thead><tr><th>Insumo</th><th>Preço</th><th>Mín.</th><th>Máx.</th><th /></tr></thead>
              <tbody>
                {grupo.opcoes.map((o) => (
                  <tr key={o.insumoId} className={o.disponivel ? '' : 'inativo'}>
                    <td><strong>{o.nome}</strong>{!o.disponivel && <span className="badge">Inativa</span>}</td>
                    <td><input type="number" step="0.01" min="0" value={o.preco} onChange={(e) => mudarOpcao(o.insumoId, 'preco', Number(e.target.value))} /></td>
                    <td><input type="number" min="0" value={o.minimo} onChange={(e) => mudarOpcao(o.insumoId, 'minimo', Number(e.target.value))} /></td>
                    <td><input type="number" min="0" value={o.maximo} onChange={(e) => mudarOpcao(o.insumoId, 'maximo', Number(e.target.value))} /></td>
                    <td className="acoes-linha">
                      <button type="button" className={o.id && !o.disponivel ? 'btn sec' : 'btn perigo'} onClick={() => removerOpcao(o)}>
                        {!o.id ? 'Remover' : o.disponivel ? 'Desativar' : 'Reativar'}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : <p className="mudo">Nenhuma opção ainda. Escolha um insumo abaixo.</p>}

        {insumos.length === 0 ? (
          <div className="alerta erro"><strong>Cadastre insumos primeiro.</strong> As opções são escolhidas entre os insumos cadastrados.
            <div><button type="button" className="btn" onClick={aoCadastrarInsumo}>Cadastrar insumo</button></div></div>
        ) : (
          <form className="linha" onSubmit={adicionar}>
            <Campo label="Insumo (opção)" dica={livres.length === 0 ? 'Todos os insumos disponíveis já estão neste grupo.' : undefined}>
              <select value={insumoId} onChange={(e) => escolher(e.target.value)} required>
                <option value="">Selecione um insumo…</option>
                {livres.map((i) => <option key={i.id} value={i.id}>{i.nome}</option>)}
              </select>
            </Campo>
            <Campo curto label="Preço"><input type="number" step="0.01" min="0" value={nova.preco} onChange={(e) => setNova({ ...nova, preco: e.target.value })} /></Campo>
            <Campo curto label="Mín."><input type="number" min="0" value={nova.minimo} onChange={(e) => setNova({ ...nova, minimo: e.target.value })} /></Campo>
            <Campo curto label="Máx."><input type="number" min="0" value={nova.maximo} onChange={(e) => setNova({ ...nova, maximo: e.target.value })} /></Campo>
            <button className="btn sec" disabled={!insumoId}>Adicionar opção</button>
          </form>
        )}
      </div>
      <div className="linha entre acoes rodape">
        <button type="button" className={grupo.id && !grupo.ativo ? 'btn sec' : 'btn perigo'} onClick={alternarAtivo}>
          {!grupo.id ? 'Excluir grupo' : grupo.ativo ? 'Desativar grupo' : 'Reativar grupo'}
        </button>
        <button type="button" className="btn" onClick={onConcluir}>Concluir</button>
      </div>
    </Modal>
  )
}

function EtapaGrupos({ grupos, setGrupos, insumos, aoCadastrarInsumo, edicao }) {
  const [novo, setNovo] = useState(GRUPO_VAZIO)
  const [aberto, setAberto] = useState(null) // índice do grupo aberto na janela
  const criar = (e) => {
    e.preventDefault()
    setGrupos([...grupos, { nome: novo.nome.trim(), minimo: Number(novo.minimo), maximo: Number(novo.maximo), modalidadeCobranca: novo.modalidadeCobranca, ativo: true, opcoes: [] }])
    setNovo(GRUPO_VAZIO)
  }
  const grupo = aberto != null ? grupos[aberto] : null

  /** Grupo já salvo: alterna ativo/inativo. Grupo novo (ainda só no rascunho): remove. Retorna se removeu do rascunho. */
  const alternarAtivo = (i) => {
    const g = grupos[i]
    if (!g.id) {
      if (g.opcoes.length && !window.confirm(`Excluir o grupo “${g.nome}” e suas ${g.opcoes.length} opção(ões)?`)) return false
      setGrupos(grupos.filter((_, j) => j !== i))
      return true
    }
    setGrupos(grupos.map((x, j) => (j === i ? { ...x, ativo: !x.ativo } : x)))
    return false
  }

  return (
    <>
      <form className="linha" onSubmit={criar}>
        <Campo label="Novo grupo"><input value={novo.nome} onChange={(e) => setNovo({ ...novo, nome: e.target.value })} placeholder="Ex.: Sabores" required /></Campo>
        <Campo curto label="Mín."><input type="number" min="0" value={novo.minimo} onChange={(e) => setNovo({ ...novo, minimo: e.target.value })} /></Campo>
        <Campo curto label="Máx."><input type="number" min="0" value={novo.maximo} onChange={(e) => setNovo({ ...novo, maximo: e.target.value })} /></Campo>
        <Campo largo label="Cobrança">
          <select value={novo.modalidadeCobranca} onChange={(e) => setNovo({ ...novo, modalidadeCobranca: e.target.value })}>
            {Object.entries(COBRANCAS).map(([k, t]) => <option key={k} value={k}>{t}</option>)}
          </select>
        </Campo>
        <button className="btn">Criar grupo</button>
      </form>

      <h3>Grupos do produto</h3>
      {grupos.length === 0 ? (
        <p className="mudo">Nenhum grupo criado. Crie ao menos um e use “Editar” para adicionar as opções.</p>
      ) : (
        <div className="rolagem">
          <table>
            <thead><tr><th>Grupo</th><th>Seleções</th><th>Cobrança</th><th>Opções</th><th /></tr></thead>
            <tbody>
              {grupos.map((g, i) => (
                <tr key={i} className={g.ativo ? '' : 'inativo'}>
                  <td><strong>{g.nome}</strong>{!g.ativo && <span className="badge">Inativo</span>}</td>
                  <td>{g.minimo}–{g.maximo}</td>
                  <td>{COBRANCAS[g.modalidadeCobranca]}</td>
                  <td>{g.opcoes.length ? g.opcoes.map((o) => o.nome).join(', ') : <span className="alerta-texto">Sem opções</span>}</td>
                  <td className="acoes-linha">
                    <button type="button" className="btn sec" onClick={() => setAberto(i)}>Editar</button>
                    <button type="button" className={g.id && !g.ativo ? 'btn sec' : 'btn perigo'} onClick={() => alternarAtivo(i)}>
                      {!g.id ? 'Excluir' : g.ativo ? 'Desativar' : 'Reativar'}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {edicao && grupos.some((g) => g.id) && (
        <p className="mudo pequeno">Grupos e opções já salvos não são apagados (o histórico dos pedidos é preservado): ficam inativos e podem ser reativados.</p>
      )}

      {grupo && (
        <JanelaOpcoes grupo={grupo} insumos={insumos} aoCadastrarInsumo={aoCadastrarInsumo}
          atualizar={(g) => setGrupos(grupos.map((x, i) => (i === aberto ? g : x)))}
          alternarAtivo={() => { if (alternarAtivo(aberto)) setAberto(null) }}
          onConcluir={() => setAberto(null)} />
      )}
    </>
  )
}

function EtapaAdicionais({ adicionais, setAdicionais, edicao }) {
  const [novo, setNovo] = useState({ nome: '', preco: '0' })
  const alternar = (i) => {
    const a = adicionais[i]
    setAdicionais(a.id ? adicionais.map((x, j) => (j === i ? { ...x, disponivel: !x.disponivel } : x)) : adicionais.filter((_, j) => j !== i))
  }
  return (
    <>
      <p className="mudo">Adicionais são opcionais (nome, preço e disponibilidade). Você pode avançar sem cadastrar nenhum.</p>
      <form className="linha" onSubmit={(e) => { e.preventDefault(); setAdicionais([...adicionais, { nome: novo.nome.trim(), preco: Number(novo.preco), disponivel: true }]); setNovo({ nome: '', preco: '0' }) }}>
        <Campo label="Novo adicional"><input value={novo.nome} onChange={(e) => setNovo({ ...novo, nome: e.target.value })} required /></Campo>
        <Campo curto label="Preço"><input type="number" step="0.01" min="0" value={novo.preco} onChange={(e) => setNovo({ ...novo, preco: e.target.value })} /></Campo>
        <button className="btn">Adicionar</button>
      </form>
      {adicionais.length > 0 && (
        <div className="rolagem">
          <table>
            <thead><tr><th>Adicional</th><th>Preço</th><th /></tr></thead>
            <tbody>
              {adicionais.map((a, i) => (
                <tr key={i} className={a.disponivel ? '' : 'inativo'}>
                  <td>{a.nome}{!a.disponivel && <span className="badge">Inativo</span>}</td>
                  <td>{brl(a.preco)}</td>
                  <td className="acoes-linha">
                    <button type="button" className={a.id && !a.disponivel ? 'btn sec' : 'btn perigo'} onClick={() => alternar(i)}>
                      {!a.id ? 'Excluir' : a.disponivel ? 'Desativar' : 'Reativar'}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {edicao && adicionais.some((a) => a.id) && (
        <p className="mudo pequeno">Adicionais já salvos não são apagados: ficam inativos e podem ser reativados.</p>
      )}
    </>
  )
}

// ---------------------------------------------------------------- etapa 3: revisão

function Revisao({ d, categorias, avisos }) {
  const g = d.geral
  const categoria = categorias.find((c) => String(c.id) === String(g.categoriaId))
  const gruposAtivos = d.grupos.filter((x) => x.ativo)
  const adicionaisAtivos = d.adicionais.filter((a) => a.disponivel)
  return (
    <div className="coluna">
      <section className="resumo">
        <h3>Informações gerais</h3>
        {(g.imagemBase64 || g.imagemUrl) && <img className="resumo-foto" src={g.imagemBase64 || miniatura(g.imagemUrl, 240)} alt="" />}
        <dl>
          <dt>Nome</dt><dd>{g.nome}</dd>
          <dt>Tipo</dt><dd>{g.tipo === 'SIMPLES' ? 'Simples' : 'Composto'}</dd>
          <dt>Categoria</dt><dd>{categoria?.nome}</dd>
          <dt>{g.tipo === 'COMPOSTO' ? 'Preço-base' : 'Preço de venda'}</dt><dd>{g.precoBase === '' ? '—' : brl(g.precoBase)}</dd>
          <dt>Disponível</dt><dd>{g.disponivel ? 'Sim' : 'Não'}</dd>
          {g.descricao && (<><dt>Descrição</dt><dd>{g.descricao}</dd></>)}
          {g.tipo === 'COMPOSTO' && (<>
            <dt>Precificação</dt><dd>{ESTRATEGIAS[g.estrategia]}{g.estrategia !== 'FIXO' && (g.participaPrecoBase ? ' · inclui o preço-base' : ' · sem o preço-base')}</dd>
          </>)}
        </dl>
      </section>

      {g.tipo === 'COMPOSTO' ? (
        <section className="resumo">
          <h3>Grupos de opções</h3>
          <div className="rolagem">
            {gruposAtivos.map((gr, i) => (
              <div key={i} className="resumo-grupo">
                <strong>{gr.nome}</strong> <small className="mudo">{gr.minimo}–{gr.maximo} seleções · {COBRANCAS[gr.modalidadeCobranca]}</small>
                <ul>{gr.opcoes.filter((o) => o.disponivel).map((o) => <li key={o.insumoId}>{o.nome} — {brl(o.preco)} <small className="mudo">(mín. {o.minimo}, máx. {o.maximo})</small></li>)}</ul>
              </div>
            ))}
            {gruposAtivos.length === 0 && <p className="mudo">Nenhum grupo ativo.</p>}
          </div>
        </section>
      ) : (
        <section className="resumo">
          <h3>Adicionais</h3>
          {adicionaisAtivos.length === 0 ? <p className="mudo">Nenhum adicional.</p>
            : <ul className="rolagem">{adicionaisAtivos.map((a, i) => <li key={i}>{a.nome} — {brl(a.preco)}</li>)}</ul>}
        </section>
      )}

      {avisos.length > 0 ? (
        <div className="alerta erro"><strong>Pontos a corrigir antes de publicar:</strong>
          <ul>{avisos.map((a, i) => <li key={i}>{a}</li>)}</ul>
          <small>Volte às etapas anteriores para ajustar. Se confirmar assim, o produto fica como <b>rascunho</b>.</small></div>
      ) : <div className="alerta ok">Configuração consistente. Ao confirmar, o produto é salvo e publicado.</div>}
    </div>
  )
}

// ---------------------------------------------------------------- assistente

export default function ProdutoWizard({ produtoId = null, categorias, aoFechar, aoSalvar, aoCadastrarInsumo }) {
  const edicao = produtoId != null
  const insumos = useCarga(() => get('/api/gestao/insumos'), [])
  const [pronto, setPronto] = useState(!edicao)
  const [erroCarga, setErroCarga] = useState(null)
  const [etapa, setEtapa] = useState(1)
  const [geral, setGeral] = useState(() => GERAL_VAZIO(categorias))
  const [grupos, setGrupos] = useState([])
  const [adicionais, setAdicionais] = useState([])
  const [original, setOriginal] = useState(null) // JSON do último estado salvo (edição)
  const [status, setStatus] = useState('RASCUNHO')
  const [ocupado, setOcupado] = useState(false)
  const [erro, setErro] = useState(null)
  const [aviso, setAviso] = useState(null) // { ok, texto, problemas }
  const [resultado, setResultado] = useState(null) // criação: { publicado, mensagem, problemas }

  const aplicar = (p) => {
    const d = deProduto(p)
    setGeral(d.geral)
    setGrupos(d.grupos)
    setAdicionais(d.adicionais)
    setOriginal(JSON.stringify(d))
    setStatus(p.status)
    setPronto(true)
  }

  useEffect(() => {
    if (!edicao) return
    get(`/api/gestao/produtos/${produtoId}`).then(aplicar).catch(setErroCarga)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [produtoId])

  const composto = geral.tipo === 'COMPOSTO'
  const d = { geral, grupos, adicionais }
  const avisos = dicas(d)
  const geralValido = geral.nome.trim() && geral.categoriaId !== '' && (composto || geral.precoBase !== '')
  const gruposValidos = !composto || (grupos.length > 0 && grupos.every((g) => !g.ativo || g.opcoes.length > 0))
  const sujo = edicao ? original !== JSON.stringify(d) : !!(geral.nome.trim() || grupos.length || adicionais.length)

  const fechar = () => {
    if (resultado || !sujo || window.confirm(edicao ? 'Descartar as alterações não salvas?' : 'Descartar o cadastro deste produto? Nada foi salvo ainda.')) aoFechar()
  }

  // ---- criação: grava tudo de uma vez ao confirmar
  const criar = async () => {
    setErro(null)
    setOcupado(true)
    let produto
    try {
      produto = await post('/api/gestao/produtos', corpoProduto(geral))
      for (const g of grupos) {
        const comGrupo = await post(`/api/gestao/produtos/${produto.id}/grupos`, corpoGrupo(g))
        const grupoId = comGrupo.grupos[comGrupo.grupos.length - 1].id
        for (const o of g.opcoes) await post(`/api/gestao/grupos/${grupoId}/opcoes`, corpoOpcao(o))
      }
      for (const a of adicionais) await post(`/api/gestao/produtos/${produto.id}/adicionais`, corpoAdicional(a))
    } catch (e) {
      setErro(e)
      setOcupado(false)
      if (produto) aoSalvar() // o rascunho parcial aparece na lista
      return
    }
    try {
      await post(`/api/gestao/produtos/${produto.id}/publicar`)
      setResultado({ publicado: true })
    } catch (e) {
      setResultado({ publicado: false, mensagem: e.message, problemas: e.detalhes ?? [] })
    }
    setOcupado(false)
    aoSalvar()
  }

  // ---- edição: aplica só as diferenças; pode ser chamada em qualquer etapa
  const salvar = async (publicar) => {
    setErro(null)
    setAviso(null)
    setOcupado(true)
    const eraPublicado = status === 'PUBLICADO'
    try {
      const orig = JSON.parse(original)
      await put(`/api/gestao/produtos/${produtoId}`, corpoProduto(geral))
      for (const g of grupos) {
        const og = orig.grupos.find((x) => x.id === g.id)
        let grupoId = g.id
        if (!g.id) {
          const r = await post(`/api/gestao/produtos/${produtoId}/grupos`, corpoGrupo(g))
          grupoId = r.grupos[r.grupos.length - 1].id
        } else if (mudou(g, og, ['nome', 'minimo', 'maximo', 'modalidadeCobranca', 'ativo'])) {
          await put(`/api/gestao/grupos/${g.id}`, corpoGrupo(g))
        }
        for (const o of g.opcoes) {
          const oo = og?.opcoes.find((x) => x.id === o.id)
          if (!o.id) await post(`/api/gestao/grupos/${grupoId}/opcoes`, corpoOpcao(o))
          else if (mudou(o, oo, ['preco', 'minimo', 'maximo', 'disponivel'])) await put(`/api/gestao/opcoes/${o.id}`, corpoOpcao(o))
        }
      }
      for (const a of adicionais) {
        const oa = orig.adicionais.find((x) => x.id === a.id)
        if (!a.id) await post(`/api/gestao/produtos/${produtoId}/adicionais`, corpoAdicional(a))
        else if (mudou(a, oa, ['nome', 'preco', 'disponivel'])) await put(`/api/gestao/adicionais/${a.id}`, corpoAdicional(a))
      }

      // edições intermediárias podem ter devolvido o produto a rascunho: tenta republicar quando cabe
      let atual = await get(`/api/gestao/produtos/${produtoId}`)
      let novoAviso = { ok: true, texto: 'Alterações salvas.' }
      if ((publicar || eraPublicado) && atual.status !== 'PUBLICADO') {
        try {
          atual = await post(`/api/gestao/produtos/${produtoId}/publicar`)
          novoAviso = { ok: true, texto: publicar && !eraPublicado ? 'Alterações salvas e produto publicado.' : 'Alterações salvas; o produto continua publicado.' }
        } catch (e) {
          novoAviso = { ok: false, texto: 'Alterações salvas, mas o produto está como rascunho: a configuração não permite publicar.', problemas: e.detalhes?.length ? e.detalhes : [e.message] }
        }
      } else if (atual.status !== 'PUBLICADO') {
        novoAviso = { ok: true, texto: 'Alterações salvas. O produto continua como rascunho.' }
      }
      aplicar(atual)
      setAviso(novoAviso)
      aoSalvar()
    } catch (e) {
      setErro(e)
      try { aplicar(await get(`/api/gestao/produtos/${produtoId}`)) } catch { /* mantém o rascunho atual */ }
      aoSalvar()
    }
    setOcupado(false)
  }

  const rotuloEtapa2 = composto ? 'Grupos de opções' : 'Adicionais'
  const titulo = edicao ? `Editar produto${geral.nome ? `: ${geral.nome}` : ''}` : 'Novo produto'

  if (edicao && !pronto) {
    return (
      <Modal titulo="Editar produto" onFechar={aoFechar} largo>
        <Erro erro={erroCarga} />{!erroCarga && 'Carregando…'}
      </Modal>
    )
  }

  return (
    <Modal titulo={titulo} onFechar={fechar} largo semEsc fixo>
      {resultado ? (
        <div className="coluna">
          {resultado.publicado
            ? <div className="alerta ok"><strong>Produto “{geral.nome}” criado e publicado.</strong></div>
            : <div className="alerta erro"><strong>Produto “{geral.nome}” criado como rascunho.</strong> {resultado.mensagem}
                <ul>{resultado.problemas.map((p, i) => <li key={i}>{p}</li>)}</ul>
                <small>Abra o produto na lista para corrigir e publicar.</small></div>}
          <div className="linha"><button className="btn" onClick={aoFechar}>Fechar</button></div>
        </div>
      ) : (
        <>
          <Etapas etapa={etapa} rotuloEtapa2={rotuloEtapa2} aoIr={edicao ? setEtapa : null} />
          <div className="etapa-corpo">
            {edicao && (
              <p className="mudo pequeno">Status: <Badge valor={status} />{sujo ? ' · há alterações não salvas' : ''}</p>
            )}
            {aviso && (
              <div className={`alerta ${aviso.ok ? 'ok' : 'erro'}`}>
                <strong>{aviso.texto}</strong>
                {aviso.problemas?.length > 0 && <ul>{aviso.problemas.map((p, i) => <li key={i}>{p}</li>)}</ul>}
              </div>
            )}
            {etapa === 1 && <EtapaGeral geral={geral} setGeral={setGeral} categorias={categorias} edicao={edicao} />}
            {etapa === 2 && composto && (
              <EtapaGrupos grupos={grupos} setGrupos={setGrupos} insumos={insumos.dados ?? []} aoCadastrarInsumo={aoCadastrarInsumo} edicao={edicao} />
            )}
            {etapa === 2 && !composto && <EtapaAdicionais adicionais={adicionais} setAdicionais={setAdicionais} edicao={edicao} />}
            {etapa === 3 && <Revisao d={d} categorias={categorias} avisos={avisos} />}
            <Erro erro={erro} />
          </div>
          <div className="linha entre acoes rodape">
            <button className="btn sec" onClick={etapa === 1 ? fechar : () => setEtapa(etapa - 1)} disabled={ocupado}>
              {etapa === 1 ? (edicao ? 'Fechar' : 'Cancelar') : '← Voltar'}
            </button>
            <div className="botoes-direita">
              {edicao && (
                <button className="btn sec" onClick={() => salvar(false)} disabled={ocupado || !sujo || !geralValido}
                  title={!sujo ? 'Não há alterações para salvar' : undefined}>
                  {ocupado ? 'Salvando…' : 'Salvar'}
                </button>
              )}
              {etapa < 3 ? (
                <button className="btn" onClick={() => setEtapa(etapa + 1)} disabled={etapa === 1 ? !geralValido : (!edicao && !gruposValidos)}
                  title={etapa === 2 && !edicao && !gruposValidos ? 'Crie ao menos um grupo e adicione opções a todos' : undefined}>
                  Próximo →
                </button>
              ) : (
                <button className="btn" onClick={edicao ? () => salvar(true) : criar} disabled={ocupado || (edicao && !geralValido)}>
                  {ocupado ? (edicao ? 'Salvando…' : 'Criando…') : edicao ? 'Confirmar e salvar' : 'Confirmar e criar produto'}
                </button>
              )}
            </div>
          </div>
        </>
      )}
    </Modal>
  )
}
