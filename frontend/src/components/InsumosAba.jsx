import { useState } from 'react'
import { api, get, put, post } from '../api.js'
import { Campo, Chave, Erro, Modal, brl, useAcao, useCarga } from '../ui.jsx'
import Icon from './Icon.jsx'
import MenuAcoes from './MenuAcoes.jsx'
import SeletorImagem, { miniatura } from './SeletorImagem.jsx'

/** Janelinha de criação/edição de insumo. */
function FormInsumo({ insumo, aoFechar, aoSalvar }) {
  const acao = useAcao()
  const [f, setF] = useState({
    nome: insumo?.nome ?? '', descricao: insumo?.descricao ?? '',
    valorReferencia: insumo?.valorReferencia == null ? '' : String(insumo.valorReferencia),
  })
  const [foto, setFoto] = useState({ base64: null, url: insumo?.imagemUrl ?? '' })

  const salvar = (e) => {
    e.preventDefault()
    const corpo = {
      nome: f.nome.trim(), descricao: f.descricao.trim() || null,
      valorReferencia: f.valorReferencia === '' ? null : Number(f.valorReferencia),
      // a situação (disponível/indisponível) só é alterada pela chave da lista: novo = disponível; edição = mantém
      disponivel: insumo?.disponivel ?? true, imagemUrl: foto.url || null, imagemBase64: foto.base64,
    }
    acao.executar(async () => {
      await (insumo ? put(`/api/gestao/insumos/${insumo.id}`, corpo) : post('/api/gestao/insumos', corpo))
      aoSalvar()
    })
  }

  return (
    <Modal titulo={insumo ? `Editar insumo: ${insumo.nome}` : 'Novo insumo'} onFechar={aoFechar}>
      <form className="coluna" onSubmit={salvar}>
        <Campo label="Nome"><input value={f.nome} onChange={(e) => setF({ ...f, nome: e.target.value })} autoFocus required maxLength={150} /></Campo>
        <Campo label="Valor de referência (R$)" dica="Sugerido como preço ao usar o insumo em um grupo.">
          <input type="number" step="0.01" min="0" value={f.valorReferencia} onChange={(e) => setF({ ...f, valorReferencia: e.target.value })} />
        </Campo>
        <Campo label="Descrição"><textarea rows="2" value={f.descricao} onChange={(e) => setF({ ...f, descricao: e.target.value })} /></Campo>
        <SeletorImagem rotulo="Foto (opcional)" url={foto.url} base64={foto.base64} aoMudar={setFoto} />
        <Erro erro={acao.erro} />
        <div className="linha entre acoes rodape">
          <button type="button" className="btn sec" onClick={aoFechar} disabled={acao.ocupado}>Cancelar</button>
          <button className="btn" disabled={acao.ocupado || !f.nome.trim()}>{acao.ocupado ? 'Salvando…' : insumo ? 'Salvar alterações' : 'Criar insumo'}</button>
        </div>
      </form>
    </Modal>
  )
}

export default function InsumosAba() {
  const { dados, erro, recarregar } = useCarga(() => get('/api/gestao/insumos'), [])
  const acao = useAcao()
  const [editando, setEditando] = useState(undefined) // undefined = fechado | null = novo | insumo

  const excluir = (i) => {
    const aviso = [
      `Excluir definitivamente o insumo “${i.nome}”?`,
      'Só é possível se ele não for opção de nenhum produto. Para apenas tirá-lo de uso, desligue a chave de disponibilidade.',
    ].join('\n\n')
    if (!window.confirm(aviso)) return
    acao.executar(async () => { await api(`/api/gestao/insumos/${i.id}`, { method: 'DELETE' }); await recarregar() })
  }

  const alternar = (i, disponivel) => acao.executar(async () => {
    await put(`/api/gestao/insumos/${i.id}`, {
      nome: i.nome, descricao: i.descricao, imagemUrl: i.imagemUrl, valorReferencia: i.valorReferencia, disponivel,
    })
    await recarregar()
  })

  return (
    <section className="cartao">
      <div className="linha entre"><h2>Insumos</h2><button className="btn" onClick={() => setEditando(null)}>+ Novo insumo</button></div>
      <p className="mudo">Insumos são cadastros de referência (sem estoque nem ficha técnica). As opções dos grupos dos produtos compostos são escolhidas entre os insumos cadastrados aqui.
        Desativar um insumo desativa as opções que o usam.</p>
      <Erro erro={erro} /><Erro erro={acao.erro} />
      <div className="tabela-rolavel">
        <table className="tabela-mobile">
          <thead><tr><th>Insumo</th><th>Descrição</th><th>Referência</th><th>Situação</th><th /></tr></thead>
          <tbody>
            {dados?.map((i) => (
              <tr key={i.id} className={`clicavel ${i.disponivel ? '' : 'inativo'}`} onClick={() => setEditando(i)}>
                <td data-label="Insumo">
                  <span className="produto-celula">
                    <span className="miniatura">{i.imagemUrl ? <img src={miniatura(i.imagemUrl)} alt="" loading="lazy" /> : <Icon nome="camera" tamanho={16} />}</span>
                    <strong>{i.nome}</strong>
                  </span>
                </td>
                <td data-label="Descrição">{i.descricao}</td>
                <td data-label="Referência">{brl(i.valorReferencia)}</td>
                <td data-label="Situação"><Chave ligado={i.disponivel} desabilitado={acao.ocupado} aoAlterar={(v) => alternar(i, v)} rotulo={i.disponivel ? 'Disponível' : 'Indisponível'} /></td>
                <td className="acoes-linha">
                  <MenuAcoes desabilitado={acao.ocupado} rotulo={`Ações de ${i.nome}`}
                    itens={[{ texto: 'Editar', aoClicar: () => setEditando(i) }, { texto: 'Excluir', perigo: true, aoClicar: () => excluir(i) }]} />
                </td>
              </tr>
            ))}
            {dados?.length === 0 && <tr><td colSpan="5" className="mudo">Nenhum insumo cadastrado. Use “+ Novo insumo”.</td></tr>}
          </tbody>
        </table>
      </div>
      {editando !== undefined && (
        <FormInsumo key={editando?.id ?? 'novo'} insumo={editando} aoFechar={() => setEditando(undefined)}
          aoSalvar={() => { setEditando(undefined); recarregar() }} />
      )}
    </section>
  )
}
