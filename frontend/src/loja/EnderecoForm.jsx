import { useRef, useState } from 'react'
import { get, post, put } from '../api.js'
import { Campo, Erro, useAcao } from '../ui.jsx'

const VAZIO = { identificacao: '', cep: '', logradouro: '', numero: '', complemento: '', bairro: '', cidade: '', estado: '', padrao: false }

const mascaraCep = (v) => {
  const d = v.replace(/\D/g, '').slice(0, 8)
  return d.length > 5 ? `${d.slice(0, 5)}-${d.slice(5)}` : d
}

/**
 * Cria (POST) ou edita (PUT) um endereço do cliente logado. Ao completar o CEP, rua, bairro, cidade e UF
 * são preenchidos pela consulta de CEP (GET /api/public/cep/{cep}) e continuam editáveis.
 */
export default function EnderecoForm({ endereco, aoSalvar, aoCancelar }) {
  const [f, setF] = useState(() => (endereco ? { ...VAZIO, ...endereco, complemento: endereco.complemento ?? '', identificacao: endereco.identificacao ?? '' } : VAZIO))
  const { erro, ocupado, executar } = useAcao()
  const [cepEstado, setCepEstado] = useState(null) // { buscando } | { erro } | { ok }
  const consulta = useRef(0)
  const numeroRef = useRef(null)
  const campo = (k) => ({ value: f[k], onChange: (e) => setF({ ...f, [k]: e.target.value }) })

  const mudarCep = (valor) => {
    const cep = mascaraCep(valor)
    setF((atual) => ({ ...atual, cep }))
    const minha = ++consulta.current
    if (cep.replace(/\D/g, '').length !== 8) { setCepEstado(null); return }
    setCepEstado({ buscando: true })
    get(`/api/public/cep/${cep.replace(/\D/g, '')}`).then((r) => {
      if (minha !== consulta.current) return
      setF((atual) => ({
        ...atual,
        logradouro: r.logradouro ?? atual.logradouro,
        bairro: r.bairro ?? atual.bairro,
        cidade: r.cidade ?? atual.cidade,
        estado: r.estado ?? atual.estado,
      }))
      setCepEstado({ ok: true, completo: !!r.logradouro })
      if (r.logradouro) numeroRef.current?.focus()
    }).catch((e) => { if (minha === consulta.current) setCepEstado({ erro: e.message }) })
  }

  const enviar = (e) => {
    e.preventDefault()
    e.stopPropagation()
    executar(async () => {
      const corpo = { ...f, estado: f.estado.toUpperCase(), identificacao: f.identificacao.trim() || null, complemento: f.complemento.trim() || null }
      const salvo = endereco ? await put(`/api/cliente/enderecos/${endereco.id}`, corpo) : await post('/api/cliente/enderecos', corpo)
      aoSalvar(salvo)
    })
  }

  return (
    <form className="lj-form-grade" onSubmit={enviar}>
      <Campo label="Nome do endereço"><input {...campo('identificacao')} maxLength={80} placeholder="Casa, trabalho…" /></Campo>
      <Campo label="CEP" dica={cepEstado?.buscando ? 'Buscando endereço…' : cepEstado?.erro ? cepEstado.erro : cepEstado?.ok ? (cepEstado.completo ? 'Endereço preenchido pelo CEP.' : 'CEP geral da cidade: informe a rua e o bairro.') : undefined}>
        <input value={f.cep} onChange={(e) => mudarCep(e.target.value)} required inputMode="numeric" placeholder="00000-000" autoComplete="postal-code" />
      </Campo>
      <Campo label="Rua / avenida" largo><input {...campo('logradouro')} required maxLength={200} /></Campo>
      <Campo label="Número"><input ref={numeroRef} {...campo('numero')} required maxLength={20} /></Campo>
      <Campo label="Complemento"><input {...campo('complemento')} maxLength={100} /></Campo>
      <Campo label="Bairro"><input {...campo('bairro')} required maxLength={100} /></Campo>
      <Campo label="Cidade"><input {...campo('cidade')} required maxLength={100} /></Campo>
      <Campo label="UF"><input {...campo('estado')} required minLength={2} maxLength={2} placeholder="SP" style={{ textTransform: 'uppercase' }} /></Campo>
      <label className="lj-check largo">
        <input type="checkbox" checked={f.padrao} onChange={(e) => setF({ ...f, padrao: e.target.checked })} /> Usar como endereço padrão
      </label>
      <div className="largo"><Erro erro={erro} /></div>
      <div className="lj-acoes largo">
        {aoCancelar && <button type="button" className="btn contorno" onClick={aoCancelar}>Cancelar</button>}
        <button className="btn" disabled={ocupado}>{ocupado ? 'Salvando…' : 'Salvar endereço'}</button>
      </div>
    </form>
  )
}
