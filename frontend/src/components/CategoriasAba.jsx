import { useState } from 'react'
import { api, get, post, put } from '../api.js'
import { Campo, Chave, Erro, Modal, useAcao, useCarga } from '../ui.jsx'
import MenuAcoes from './MenuAcoes.jsx'

/** Janelinha de criação/edição de categoria. */
function FormCategoria({ categoria, aoFechar, aoSalvar }) {
  const acao = useAcao()
  const [nome, setNome] = useState(categoria?.nome ?? '')

  const salvar = (e) => {
    e.preventDefault()
    // a situação só é alterada pela chave da lista: nova = ativa; edição = mantém
    const corpo = { nome: nome.trim(), ativa: categoria?.ativa ?? true }
    acao.executar(async () => {
      await (categoria ? put(`/api/gestao/categorias/${categoria.id}`, corpo) : post('/api/gestao/categorias', corpo))
      aoSalvar()
    })
  }

  return (
    <Modal titulo={categoria ? `Editar categoria: ${categoria.nome}` : 'Nova categoria'} onFechar={aoFechar}>
      <form className="coluna" onSubmit={salvar}>
        <Campo label="Nome"><input value={nome} onChange={(e) => setNome(e.target.value)} autoFocus required maxLength={100} /></Campo>
        <Erro erro={acao.erro} />
        <div className="linha entre acoes rodape">
          <button type="button" className="btn sec" onClick={aoFechar} disabled={acao.ocupado}>Cancelar</button>
          <button className="btn" disabled={acao.ocupado || !nome.trim()}>{acao.ocupado ? 'Salvando…' : categoria ? 'Salvar alterações' : 'Criar categoria'}</button>
        </div>
      </form>
    </Modal>
  )
}

export default function CategoriasAba({ aoMudar }) {
  const { dados, erro, recarregar } = useCarga(() => get('/api/gestao/categorias'), [])
  const acao = useAcao()
  const [editando, setEditando] = useState(undefined) // undefined = fechada | null = nova | categoria

  const excluir = (c) => {
    const aviso = [
      `Excluir definitivamente a categoria “${c.nome}”?`,
      'Só é possível se nenhum produto estiver usando esta categoria. Para apenas escondê-la, desligue a chave “Ativa”.',
    ].join('\n\n')
    if (!window.confirm(aviso)) return
    acao.executar(async () => { await api(`/api/gestao/categorias/${c.id}`, { method: 'DELETE' }); await recarregar(); aoMudar?.() })
  }

  const alternar = (c, ativa) => acao.executar(async () => {
    await put(`/api/gestao/categorias/${c.id}`, { nome: c.nome, ativa })
    await recarregar()
    aoMudar?.()
  })

  return (
    <section className="cartao">
      <div className="linha entre"><h2>Categorias</h2><button className="btn" onClick={() => setEditando(null)}>+ Nova categoria</button></div>
      <Erro erro={erro} /><Erro erro={acao.erro} />
      <div className="tabela-rolavel">
        <table className="tabela-mobile">
          <thead><tr><th>Categoria</th><th>Situação</th><th /></tr></thead>
          <tbody>
            {dados?.map((c) => (
              <tr key={c.id} className={`clicavel ${c.ativa ? '' : 'inativo'}`} onClick={() => setEditando(c)}>
                <td data-label="Categoria"><strong>{c.nome}</strong></td>
                <td data-label="Situação"><Chave ligado={c.ativa} desabilitado={acao.ocupado} aoAlterar={(v) => alternar(c, v)} rotulo={c.ativa ? 'Ativa' : 'Desativada'} /></td>
                <td className="acoes-linha">
                  <MenuAcoes desabilitado={acao.ocupado} rotulo={`Ações de ${c.nome}`}
                    itens={[{ texto: 'Editar', aoClicar: () => setEditando(c) }, { texto: 'Excluir', perigo: true, aoClicar: () => excluir(c) }]} />
                </td>
              </tr>
            ))}
            {dados?.length === 0 && <tr><td colSpan="3" className="mudo">Nenhuma categoria cadastrada. Use “+ Nova categoria”.</td></tr>}
          </tbody>
        </table>
      </div>
      {editando !== undefined && (
        <FormCategoria key={editando?.id ?? 'nova'} categoria={editando} aoFechar={() => setEditando(undefined)}
          aoSalvar={() => { setEditando(undefined); recarregar(); aoMudar?.() }} />
      )}
    </section>
  )
}
