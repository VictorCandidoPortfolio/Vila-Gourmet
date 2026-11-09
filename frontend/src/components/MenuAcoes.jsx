import { useEffect, useRef, useState } from 'react'
import Icon from './Icon.jsx'

/**
 * Menu de três pontos ("⋯") com as ações de uma linha. `itens`: [{ texto, aoClicar, perigo }].
 * O painel usa posição fixa (não é cortado pela rolagem da tabela), abre para cima quando falta espaço
 * embaixo e fecha ao clicar fora, rolar, redimensionar ou apertar Esc. Clicar nele não aciona a linha.
 */
export default function MenuAcoes({ itens, desabilitado, rotulo = 'Ações' }) {
  const botao = useRef(null)
  const painel = useRef(null)
  const [pos, setPos] = useState(null) // { top, left } quando aberto

  const abrir = (e) => {
    e.stopPropagation()
    if (pos) { setPos(null); return }
    const r = botao.current.getBoundingClientRect()
    const alturaPainel = itens.length * 42 + 12
    const largura = 180
    const embaixo = r.bottom + 6 + alturaPainel <= window.innerHeight
    setPos({
      top: embaixo ? r.bottom + 6 : Math.max(8, r.top - 6 - alturaPainel),
      left: Math.max(8, Math.min(r.right - largura, window.innerWidth - largura - 8)),
    })
  }

  useEffect(() => {
    if (!pos) return undefined
    const fechar = () => setPos(null)
    const fora = (e) => {
      if (!painel.current?.contains(e.target) && !botao.current?.contains(e.target)) fechar()
    }
    const tecla = (e) => e.key === 'Escape' && fechar()
    document.addEventListener('mousedown', fora)
    document.addEventListener('keydown', tecla)
    window.addEventListener('resize', fechar)
    window.addEventListener('scroll', fechar, true)
    return () => {
      document.removeEventListener('mousedown', fora)
      document.removeEventListener('keydown', tecla)
      window.removeEventListener('resize', fechar)
      window.removeEventListener('scroll', fechar, true)
    }
  }, [pos])

  return (
    <>
      <button ref={botao} type="button" className={`menu-acoes-btn ${pos ? 'aberto' : ''}`} onClick={abrir} disabled={desabilitado}
        aria-haspopup="menu" aria-expanded={!!pos} aria-label={rotulo} title={rotulo}>
        <Icon nome="mais" tamanho={20} />
      </button>
      {pos && (
        <div ref={painel} className="menu-acoes" role="menu" style={{ top: pos.top, left: pos.left }} onClick={(e) => e.stopPropagation()}>
          {itens.map((item) => (
            <button key={item.texto} type="button" role="menuitem" className={item.perigo ? 'perigo' : ''}
              onClick={() => { setPos(null); item.aoClicar() }}>
              {item.texto}
            </button>
          ))}
        </div>
      )}
    </>
  )
}
