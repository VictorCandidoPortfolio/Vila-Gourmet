import { useRef, useState } from 'react'
import Icon from './Icon.jsx'

const TIPOS = ['image/png', 'image/jpeg', 'image/webp', 'image/gif']
const LIMITE_ORIGINAL = 15 * 1024 * 1024 // arquivo escolhido; a foto é reduzida antes do envio (limite do servidor: 5 MB)

/** Lê o arquivo, reduz o maior lado e devolve um data URL JPEG (leve para enviar em base64). */
export function prepararImagem(arquivo, maiorLado = 1280, qualidade = 0.88) {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(arquivo)
    const img = new Image()
    img.onload = () => {
      const escala = Math.min(1, maiorLado / Math.max(img.width, img.height))
      const canvas = document.createElement('canvas')
      canvas.width = Math.max(1, Math.round(img.width * escala))
      canvas.height = Math.max(1, Math.round(img.height * escala))
      const ctx = canvas.getContext('2d')
      ctx.fillStyle = '#fff' // PNG transparente vira fundo branco no JPEG
      ctx.fillRect(0, 0, canvas.width, canvas.height)
      ctx.drawImage(img, 0, 0, canvas.width, canvas.height)
      URL.revokeObjectURL(url)
      resolve(canvas.toDataURL('image/jpeg', qualidade))
    }
    img.onerror = () => { URL.revokeObjectURL(url); reject(new Error('Não foi possível ler a imagem.')) }
    img.src = url
  })
}

/** URL menor do Cloudinary para miniaturas (corte quadrado, formato e qualidade automáticos). */
export function miniatura(url, lado = 96) {
  if (!url || !url.includes('res.cloudinary.com') || !url.includes('/upload/')) return url
  return url.replace('/upload/', `/upload/c_fill,w_${lado},h_${lado},q_auto,f_auto/`)
}

/**
 * Caixinha de foto: clique (ou Enter/Espaço) abre o seletor de arquivos, e também é possível arrastar uma
 * imagem até ela. Ao passar o mouse aparece o ícone de upload; com foto, uma lixeira no canto superior a remove.
 * A foto escolhida fica como base64 até o salvamento (o servidor envia ao Cloudinary e grava só a URL).
 * `url` é a foto já salva; `base64` é a foto nova ainda não enviada.
 */
export default function SeletorImagem({ url, base64, aoMudar, rotulo = 'Foto' }) {
  const campo = useRef(null)
  const [erro, setErro] = useState(null)
  const [lendo, setLendo] = useState(false)
  const [arrastando, setArrastando] = useState(false)
  const previa = base64 || miniatura(url, 360)

  const processar = async (arquivo) => {
    if (!arquivo) return
    setErro(null)
    if (!TIPOS.includes(arquivo.type)) { setErro('Use uma imagem PNG, JPEG, WEBP ou GIF.'); return }
    if (arquivo.size > LIMITE_ORIGINAL) { setErro('A imagem é grande demais (máximo 15 MB).'); return }
    setLendo(true)
    try {
      aoMudar({ base64: await prepararImagem(arquivo), url })
    } catch (ex) {
      setErro(ex.message)
    } finally {
      setLendo(false)
    }
  }

  const escolher = (e) => {
    const arquivo = e.target.files?.[0]
    e.target.value = '' // permite escolher o mesmo arquivo de novo
    processar(arquivo)
  }

  const soltar = (e) => {
    e.preventDefault()
    setArrastando(false)
    processar(e.dataTransfer.files?.[0])
  }

  const abrir = () => !lendo && campo.current?.click()

  return (
    <div className="seletor-imagem">
      <span className="seletor-rotulo">{rotulo}</span>
      <div
        className={`dropzone ${previa ? 'com-foto' : 'vazia'} ${arrastando ? 'arrastando' : ''}`}
        role="button" tabIndex={0}
        aria-label={previa ? `${rotulo}: trocar foto` : `${rotulo}: adicionar foto`}
        onClick={abrir}
        onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); abrir() } }}
        onDragEnter={(e) => { e.preventDefault(); setArrastando(true) }}
        onDragOver={(e) => { e.preventDefault(); e.dataTransfer.dropEffect = 'copy'; setArrastando(true) }}
        onDragLeave={(e) => { if (!e.currentTarget.contains(e.relatedTarget)) setArrastando(false) }}
        onDrop={soltar}
      >
        {previa && <img src={previa} alt="Pré-visualização da foto" draggable={false} />}
        <div className="sobreposicao">
          <Icon nome="upload" tamanho={28} />
          <span>{lendo ? 'Processando…' : arrastando ? 'Solte a foto aqui' : previa ? 'Trocar foto' : 'Adicionar foto'}</span>
        </div>
        {previa && (
          <button type="button" className="dropzone-lixeira" title="Remover foto" aria-label="Remover foto" disabled={lendo}
            onClick={(e) => { e.stopPropagation(); setErro(null); aoMudar({ base64: null, url: '' }) }}>
            <Icon nome="lixeira" tamanho={16} />
          </button>
        )}
      </div>
      <small className="seletor-legenda">
        {base64 ? 'Nova foto — será enviada ao salvar.' : 'Clique ou arraste uma imagem · PNG, JPEG, WEBP ou GIF'}
      </small>
      {erro && <small className="alerta-texto">{erro}</small>}
      <input ref={campo} type="file" accept="image/png,image/jpeg,image/webp,image/gif" hidden onChange={escolher} />
    </div>
  )
}
