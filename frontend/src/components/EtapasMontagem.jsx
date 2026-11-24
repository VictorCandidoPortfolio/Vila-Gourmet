import { useState } from 'react'
import './EtapasMontagem.css'

export default function EtapasMontagem({ grupos, temAdicionais, renderGrupo, renderAdicionais }) {
  const [etapa, setEtapa] = useState(0)
  const total = grupos.length + (temAdicionais ? 1 : 0)
  if (total === 0) return null

  const atual = Math.min(etapa, total - 1)
  const grupo = grupos[atual]

  return (
    <section className="montagem-etapas" aria-label="Opções do produto">
      {total > 1 && (
        <nav className="montagem-navegacao" aria-label="Navegar pelas etapas da montagem">
          <button type="button" disabled={atual === 0} onClick={() => setEtapa(atual - 1)} aria-label="Etapa anterior">
            <span aria-hidden="true">‹</span>
          </button>
          <div className="montagem-indicador" aria-live="polite">
            <small>Etapa {atual + 1} de {total}</small>
            <strong>{grupo?.nome ?? 'Adicionais'}</strong>
          </div>
          <button type="button" disabled={atual === total - 1} onClick={() => setEtapa(atual + 1)} aria-label="Próxima etapa">
            <span aria-hidden="true">›</span>
          </button>
        </nav>
      )}
      <div key={atual} className="montagem-pagina">
        {grupo ? renderGrupo(grupo) : renderAdicionais()}
      </div>
      {total > 1 && (
        <div className="montagem-pontos" aria-hidden="true">
          {Array.from({ length: total }, (_, indice) => <span key={indice} className={indice === atual ? 'ativo' : ''} />)}
        </div>
      )}
    </section>
  )
}
