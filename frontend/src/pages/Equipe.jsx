import { useState } from 'react'
import { get, post, put } from '../api.js'
import { useAuth } from '../auth.jsx'
import CampoSenha, { senhaValida } from '../components/CampoSenha.jsx'
import { Campo, Erro, Modal, useAcao, useCarga } from '../ui.jsx'

function FormFuncionario({ f, perfis, onSalvo, onFechar }) {
  const acao = useAcao()
  const [email, setEmail] = useState(f?.email ?? '')
  const [nome, setNome] = useState(f?.nome ?? '')
  const [senha, setSenha] = useState('')
  const [tentouEnviar, setTentouEnviar] = useState(false)
  const [ativa, setAtiva] = useState(f?.ativa ?? true)
  const [perfilIds, setPerfilIds] = useState(new Set(perfis.filter((p) => f?.perfis.includes(p.nome)).map((p) => p.id)))

  const alternar = (id) => { const n = new Set(perfilIds); n.has(id) ? n.delete(id) : n.add(id); setPerfilIds(n) }
  const salvar = (e) => {
    e.preventDefault()
    setTentouEnviar(true)
    if (!e.currentTarget.checkValidity()) { e.currentTarget.reportValidity(); return }
    if ((!f || senha) && !senhaValida(senha)) return
    const corpo = { email, nome, ativa, perfilIds: [...perfilIds], senha: senha || null }
    acao.executar(async () => { await (f ? put(`/api/gestao/funcionarios/${f.id}`, corpo) : post('/api/gestao/funcionarios', corpo)); onSalvo() })
  }
  return (
    <Modal titulo={f ? 'Editar funcionário' : 'Novo funcionário'} onFechar={onFechar}>
      <form onSubmit={salvar} className="coluna" noValidate>
        <Campo label="Nome"><input value={nome} onChange={(e) => setNome(e.target.value)} required /></Campo>
        <Campo label="E-mail"><input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required /></Campo>
        <CampoSenha label={f ? 'Nova senha (deixe em branco para manter)' : 'Senha'} value={senha}
          onChange={(e) => setSenha(e.target.value)} required={!f} politica permitirVazia={Boolean(f)}
          mostrarErro={tentouEnviar && (!f || Boolean(senha))} autoComplete="new-password" />
        <fieldset><legend>Perfis</legend>
          {perfis.filter((p) => p.ativo || perfilIds.has(p.id)).map((p) => (
            <label key={p.id} className="check"><input type="checkbox" checked={perfilIds.has(p.id)} onChange={() => alternar(p.id)} /> {p.nome}</label>
          ))}
        </fieldset>
        <label className="check"><input type="checkbox" checked={ativa} onChange={(e) => setAtiva(e.target.checked)} /> Conta ativa</label>
        <Erro erro={acao.erro} />
        <button className="btn" disabled={acao.ocupado}>Salvar</button>
      </form>
    </Modal>
  )
}

function FormPerfil({ perfil, permissoes, onSalvo, onFechar }) {
  const acao = useAcao()
  const [nome, setNome] = useState(perfil?.nome ?? '')
  const [descricao, setDescricao] = useState(perfil?.descricao ?? '')
  const [ativo, setAtivo] = useState(perfil?.ativo ?? true)
  const [ids, setIds] = useState(new Set(perfil?.permissoes.map((p) => p.id) ?? []))

  const alternar = (id) => { const n = new Set(ids); n.has(id) ? n.delete(id) : n.add(id); setIds(n) }
  const salvar = (e) => {
    e.preventDefault()
    const corpo = { nome, descricao, ativo, permissaoIds: [...ids] }
    acao.executar(async () => { await (perfil ? put(`/api/gestao/perfis/${perfil.id}`, corpo) : post('/api/gestao/perfis', corpo)); onSalvo() })
  }
  return (
    <Modal titulo={perfil ? `Perfil ${perfil.nome}` : 'Novo perfil'} onFechar={onFechar}>
      <form onSubmit={salvar} className="coluna">
        <Campo label="Nome"><input value={nome} onChange={(e) => setNome(e.target.value)} required /></Campo>
        <Campo label="Descrição"><input value={descricao ?? ''} onChange={(e) => setDescricao(e.target.value)} /></Campo>
        <fieldset><legend>Permissões</legend>
          {permissoes.map((p) => (
            <label key={p.id} className="check"><input type="checkbox" checked={ids.has(p.id)} onChange={() => alternar(p.id)} />
              <span><strong>{p.codigo}</strong><small className="mudo"> — {p.descricao}</small></span></label>
          ))}
        </fieldset>
        <label className="check"><input type="checkbox" checked={ativo} onChange={(e) => setAtivo(e.target.checked)} /> Perfil ativo</label>
        <Erro erro={acao.erro} />
        <button className="btn" disabled={acao.ocupado}>Salvar</button>
      </form>
    </Modal>
  )
}

export default function Equipe() {
  const { pode } = useAuth()
  const funcionarios = useCarga(() => get('/api/gestao/funcionarios'), [])
  const perfis = useCarga(() => get('/api/gestao/perfis'), [])
  const permissoes = useCarga(() => (pode('PERFIL_GERENCIAR') ? get('/api/gestao/permissoes') : Promise.resolve([])), [])
  const [formF, setFormF] = useState(null)   // null | 'novo' | funcionario
  const [formP, setFormP] = useState(null)

  return (
    <>
      <h1>Equipe e perfis</h1>
      <Erro erro={funcionarios.erro} /><Erro erro={perfis.erro} />

      <section className="cartao">
        <div className="linha entre"><h2>Funcionários</h2><button className="btn" onClick={() => setFormF('novo')}>+ Novo funcionário</button></div>
        <table className="tabela-mobile">
          <thead><tr><th>Nome</th><th>E-mail</th><th>Perfis</th><th>Situação</th><th /></tr></thead>
          <tbody>
            {funcionarios.dados?.map((f) => (
              <tr key={f.id}>
                <td data-label="Nome">{f.nome}</td><td data-label="E-mail">{f.email}</td><td data-label="Perfis">{f.perfis.join(', ') || '—'}</td>
                <td data-label="Situação">{f.ativa ? 'Ativo' : 'Desativado'}</td>
                <td data-label=""><button className="btn sec" onClick={() => setFormF(f)}>Editar</button></td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>

      {pode('PERFIL_GERENCIAR') && (
        <section className="cartao">
          <div className="linha entre"><h2>Perfis e permissões</h2><button className="btn" onClick={() => setFormP('novo')}>+ Novo perfil</button></div>
          <table className="tabela-mobile">
            <thead><tr><th>Perfil</th><th>Permissões</th><th>Situação</th><th /></tr></thead>
            <tbody>
              {perfis.dados?.map((p) => (
                <tr key={p.id}>
                  <td data-label="Perfil"><strong>{p.nome}</strong><div className="pequeno mudo">{p.descricao}</div></td>
                  <td data-label="Permissões" className="pequeno">{p.permissoes.map((x) => x.codigo).join(', ') || '—'}</td>
                  <td data-label="Situação">{p.ativo ? 'Ativo' : 'Inativo'}</td>
                  <td data-label=""><button className="btn sec" onClick={() => setFormP(p)}>Editar</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      )}

      {formF && perfis.dados && (
        <FormFuncionario f={formF === 'novo' ? null : formF} perfis={perfis.dados}
          onFechar={() => setFormF(null)} onSalvo={() => { setFormF(null); funcionarios.recarregar() }} />
      )}
      {formP && permissoes.dados && (
        <FormPerfil perfil={formP === 'novo' ? null : formP} permissoes={permissoes.dados}
          onFechar={() => setFormP(null)} onSalvo={() => { setFormP(null); perfis.recarregar(); funcionarios.recarregar() }} />
      )}
    </>
  )
}
