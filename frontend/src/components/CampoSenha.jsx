import { useId, useState } from 'react'
import Icon from './Icon.jsx'
import './CampoSenha.css'

export const requisitosSenha = (senha = '') => [
  { texto: 'Pelo menos 8 caracteres', ok: senha.length >= 8 },
  { texto: 'Uma letra maiúscula', ok: /[A-Z]/.test(senha) },
  { texto: 'Uma letra minúscula', ok: /[a-z]/.test(senha) },
  { texto: 'Um número', ok: /\d/.test(senha) },
  { texto: 'Um caractere especial', ok: /[^A-Za-z0-9\s]/.test(senha) },
]

export const senhaValida = (senha = '') => senha.length <= 72 && requisitosSenha(senha).every((item) => item.ok)

function forcaDaSenha(senha) {
  if (!senha) return null
  const comum = /(1234|abcd|qwerty|senha|password|admin|(.)\1{2,})/i.test(senha)
  const atendidos = requisitosSenha(senha).filter((item) => item.ok).length
  if (comum || atendidos < 4) return { texto: 'Fraca', nivel: 'fraca' }
  if (senha.length >= 12 && atendidos === 5) return { texto: 'Forte', nivel: 'forte' }
  return { texto: 'Razoável', nivel: 'razoavel' }
}

export default function CampoSenha({
  label = 'Senha', value, onChange, required = false, politica = false, mostrarErro = false,
  permitirVazia = false, erroExterno, autoComplete, autoFocus, name, placeholder,
}) {
  const id = useId()
  const [visivel, setVisivel] = useState(false)
  const [tocado, setTocado] = useState(false)
  const requisitos = requisitosSenha(value)
  const invalida = politica && !(permitirVazia && !value) && !senhaValida(value)
  const erroPolitica = invalida && (tocado || mostrarErro) ? 'Confira os requisitos de segurança da senha.' : null
  const erro = erroExterno || erroPolitica
  const forca = politica ? forcaDaSenha(value) : null
  const ajudaId = politica ? `${id}-requisitos` : undefined
  const erroId = erro ? `${id}-erro` : undefined
  const descricao = [ajudaId, erroId].filter(Boolean).join(' ') || undefined

  return (
    <div className={`campo campo-senha${erro ? ' campo-senha-invalida' : ''}`}>
      <label htmlFor={id}>{label}</label>
      <div className="campo-senha-controle">
        <input id={id} name={name} type={visivel ? 'text' : 'password'} value={value} onChange={onChange}
          onBlur={() => setTocado(true)} required={required} maxLength={politica ? 72 : undefined} autoComplete={autoComplete}
          autoFocus={autoFocus} placeholder={placeholder} aria-invalid={erro ? 'true' : undefined} aria-describedby={descricao} />
        <button type="button" className="campo-senha-olho" onClick={() => setVisivel((atual) => !atual)}
          aria-label={visivel ? 'Ocultar senha' : 'Mostrar senha'} title={visivel ? 'Ocultar senha' : 'Mostrar senha'}>
          <Icon nome={visivel ? 'eyeOff' : 'eye'} tamanho={19} />
        </button>
      </div>
      {politica && (
        <div className="senha-ajuda" id={ajudaId}>
          <span>Sua senha deve conter:</span>
          <ul>
            {requisitos.map((item) => (
              <li key={item.texto} className={item.ok ? 'atendido' : ''}>
                <span aria-hidden="true">{item.ok ? '✓' : '○'}</span>{item.texto}
              </li>
            ))}
          </ul>
          {forca && <div className={`senha-forca ${forca.nivel}`}><span>Força:</span> <strong>{forca.texto}</strong></div>}
        </div>
      )}
      {erro && <small id={erroId} className="campo-senha-erro" role="alert">{erro}</small>}
    </div>
  )
}
