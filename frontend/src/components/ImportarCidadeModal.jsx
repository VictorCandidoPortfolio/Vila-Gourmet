import { useEffect, useRef, useState } from 'react'
import { get, post } from '../api.js'
import { Campo, Erro, Modal, useAcao } from '../ui.jsx'

const normalizar = (s) => s.normalize('NFD').replace(/\p{Diacritic}/gu, '').toLowerCase().trim()

/**
 * Adicionar cidade à área de entrega: pesquisa de municípios (IBGE), confirmação e importação dos bairros
 * (OpenStreetMap, feita no servidor). Se não for possível obter os bairros, nada é importado e o cadastro manual
 * dos bairros da cidade escolhida fica disponível.
 */
export default function ImportarCidadeModal({ onFechar, aoMudar }) {
  const [termo, setTermo] = useState('')
  const [sugestoes, setSugestoes] = useState([])
  const [buscando, setBuscando] = useState(false)
  const [erroBusca, setErroBusca] = useState(null)
  const [cidade, setCidade] = useState(null) // { ibgeId, nome, uf }
  const [fase, setFase] = useState('busca') // busca | importando | resultado | falha
  const [resultado, setResultado] = useState(null)
  const [erroImport, setErroImport] = useState(null)
  const [manuais, setManuais] = useState([])
  const [bairro, setBairro] = useState('')
  const manual = useAcao()
  const seq = useRef(0)

  // Sugestões enquanto digita (a partir de 2 letras)
  useEffect(() => {
    if (cidade) return undefined
    const minha = ++seq.current
    setErroBusca(null)
    if (termo.trim().length < 2) { setSugestoes([]); setBuscando(false); return undefined }
    setBuscando(true)
    const t = setTimeout(() => {
      get(`/api/gestao/entrega/municipios?busca=${encodeURIComponent(termo.trim())}`)
        .then((r) => { if (minha === seq.current) setSugestoes(r) })
        .catch((e) => { if (minha === seq.current) { setSugestoes([]); setErroBusca(e) } })
        .finally(() => { if (minha === seq.current) setBuscando(false) })
    }, 250)
    return () => clearTimeout(t)
  }, [termo, cidade])

  // Nome completo digitado e sem homônimos: pode ser selecionado com Enter
  const exatas = sugestoes.filter((s) => normalizar(s.nome) === normalizar(termo))
  const selecionar = (c) => { setCidade(c); setSugestoes([]); setErroImport(null) }
  const aoEnter = (e) => {
    if (e.key !== 'Enter') return
    e.preventDefault()
    if (exatas.length === 1) selecionar(exatas[0])
  }

  const importar = async () => {
    setFase('importando')
    setErroImport(null)
    try {
      setResultado(await post('/api/gestao/entrega/cidades/importar', { ibgeId: cidade.ibgeId }))
      setFase('resultado')
    } catch (e) {
      setErroImport(e)
      setFase('falha')
    }
    aoMudar(cidade.ibgeId)
  }

  const adicionarManual = (e) => {
    e.preventDefault()
    manual.executar(async () => {
      const nome = bairro.trim()
      await post('/api/gestao/entrega/areas', { bairro: nome, cidade: cidade.nome, cidadeIbgeId: cidade.ibgeId })
      setManuais((l) => [...l, nome])
      setBairro('')
      aoMudar(cidade.ibgeId)
    })
  }

  const ocupado = fase === 'importando'

  return (
    <Modal titulo="Adicionar cidade" onFechar={ocupado ? () => {} : onFechar} semEsc={ocupado}>
      {fase === 'busca' && !cidade && (
        <>
          <p className="mudo">Pesquise a cidade (lista oficial do IBGE). Os bairros são buscados automaticamente depois que você confirmar.</p>
          <Campo label="Cidade">
            <input value={termo} onChange={(e) => setTermo(e.target.value)} onKeyDown={aoEnter} autoFocus
              placeholder="Digite o nome da cidade…" role="combobox" aria-expanded={sugestoes.length > 0} aria-controls="lista-cidades" />
          </Campo>
          <Erro erro={erroBusca} />
          {buscando && <p className="mudo pequeno">Buscando…</p>}
          {!buscando && !erroBusca && termo.trim().length >= 2 && sugestoes.length === 0 && (
            <p className="mudo pequeno">Nenhuma cidade encontrada para “{termo.trim()}”.</p>
          )}
          {sugestoes.length > 0 && (
            <ul className="sugestoes" id="lista-cidades" role="listbox">
              {sugestoes.map((s) => (
                <li key={s.ibgeId}>
                  <button type="button" role="option" onClick={() => selecionar(s)}>
                    <strong>{s.nome}</strong><span className="badge">{s.uf}</span>
                  </button>
                </li>
              ))}
            </ul>
          )}
          {exatas.length > 1 && <p className="mudo pequeno">Existem cidades com esse nome em estados diferentes: escolha pela UF.</p>}
        </>
      )}

      {cidade && fase === 'busca' && (
        <>
          <div className="cidade-escolhida">
            <div><strong>{cidade.nome}</strong> <span className="badge">{cidade.uf}</span><small className="mudo"> · código IBGE {cidade.ibgeId}</small></div>
            <button className="btn sec" onClick={() => setCidade(null)}>Trocar</button>
          </div>
          <p className="mudo">
            Vamos buscar os bairros desta cidade no OpenStreetMap e adicioná-los à área de entrega. Em seguida você poderá revisar e
            desativar os que a pizzaria não atende. A cobertura depende do mapeamento colaborativo e pode estar incompleta.
          </p>
          <div className="linha">
            <button className="btn" onClick={importar}>Importar bairros de {cidade.nome}/{cidade.uf}</button>
            <button className="btn sec" onClick={onFechar}>Cancelar</button>
          </div>
        </>
      )}

      {fase === 'importando' && (
        <div className="importando" role="status" aria-live="polite">
          <span className="giro" aria-hidden="true" />
          <div>
            <strong>Buscando bairros de {cidade.nome}/{cidade.uf}…</strong>
            <p className="mudo">Isso pode levar cerca de 1 minuto. Não feche esta janela.</p>
          </div>
        </div>
      )}

      {fase === 'resultado' && resultado && (
        <>
          <div className="alerta ok" role="status">
            <strong>{resultado.adicionados} {resultado.adicionados === 1 ? 'bairro adicionado' : 'bairros adicionados'} em {resultado.cidade}/{resultado.uf}.</strong>
            <ul>
              <li>{resultado.encontrados} encontrados na fonte{resultado.jaExistentes > 0 && `; ${resultado.jaExistentes} já estavam cadastrados e foram mantidos como estão`}.</li>
              {resultado.ignorados > 0 && <li>{resultado.ignorados} nomes inválidos foram ignorados.</li>}
              <li>Todos entram como atendidos: revise a lista e desative os que não forem entregues.</li>
            </ul>
          </div>
          <p className="mudo pequeno">Fonte: {resultado.fonte} · © colaboradores do OpenStreetMap (ODbL).</p>
          <div className="linha"><button className="btn" onClick={onFechar}>Revisar bairros</button></div>
        </>
      )}

      {fase === 'falha' && (
        <>
          <Erro erro={erroImport} />
          <p className="mudo">
            Nenhum bairro foi importado{erroImport?.status === 409 ? '' : ' de ' + cidade.nome + '/' + cidade.uf}. Você pode tentar de novo
            ou cadastrar os bairros manualmente abaixo.
          </p>
          <h3>Cadastro manual em {cidade.nome}/{cidade.uf}</h3>
          <form className="linha" onSubmit={adicionarManual}>
            <Campo label="Bairro"><input value={bairro} onChange={(e) => setBairro(e.target.value)} required maxLength={100} /></Campo>
            <button className="btn" disabled={manual.ocupado || !bairro.trim()}>Adicionar bairro</button>
          </form>
          <Erro erro={manual.erro} />
          {manuais.length > 0 && <p className="alerta ok">Adicionados: {manuais.join(', ')}.</p>}
          <div className="linha">
            <button className="btn sec" onClick={importar}>Tentar importar de novo</button>
            <button className="btn" onClick={onFechar}>Concluir</button>
          </div>
        </>
      )}
    </Modal>
  )
}
