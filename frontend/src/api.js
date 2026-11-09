// Cliente HTTP da API. Os tokens JWT ficam em sessionStorage (somem ao fechar a aba).
// A estratégia definitiva de armazenamento/renovação de tokens ainda está pendente de decisão.
// Há dois tokens independentes: o da gestão (funcionário) e o da loja (cliente); a rota decide qual enviar.
const BASE = import.meta.env.VITE_API_URL ?? 'http://localhost:8080'

const CHAVES = { gestao: 'vg_token', cliente: 'vgc_token' }
const lerToken = (area) => { try { return sessionStorage.getItem(CHAVES[area]) } catch { return null } }
const tokens = { gestao: lerToken('gestao'), cliente: lerToken('cliente') }
const aoExpirar = { gestao: () => {}, cliente: () => {} }

const areaDe = (path) => (path.startsWith('/api/gestao') || path.startsWith('/api/auth/funcionario') ? 'gestao' : 'cliente')

function guardar(area, t) {
  tokens[area] = t
  try {
    if (t) sessionStorage.setItem(CHAVES[area], t)
    else sessionStorage.removeItem(CHAVES[area])
  } catch { /* armazenamento indisponível: o token vale só enquanto a página estiver aberta */ }
}

// Gestão (funcionário)
export const setToken = (t) => guardar('gestao', t)
export const getToken = () => tokens.gestao
export const onSessaoExpirada = (fn) => { aoExpirar.gestao = fn }

// Loja (cliente)
export const setTokenCliente = (t) => guardar('cliente', t)
export const getTokenCliente = () => tokens.cliente
export const onSessaoClienteExpirada = (fn) => { aoExpirar.cliente = fn }

export class ApiError extends Error {
  constructor(status, mensagem, detalhes = []) {
    super(mensagem)
    this.status = status
    this.detalhes = detalhes
  }
}

export async function api(path, { method = 'GET', body, headers = {} } = {}) {
  const area = areaDe(path)
  const token = tokens[area]
  const res = await fetch(BASE + path, {
    method,
    headers: {
      ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...headers,
    },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  })
  if (res.status === 204) return null
  const texto = await res.text()
  const dados = texto ? JSON.parse(texto) : null
  if (!res.ok) {
    if (res.status === 401 && token) aoExpirar[area]()
    throw new ApiError(res.status, dados?.mensagem ?? `Erro ${res.status}`, dados?.detalhes ?? [])
  }
  return dados
}

export const get = (p) => api(p)
export const post = (p, body = {}, headers) => api(p, { method: 'POST', body, headers })
export const put = (p, body) => api(p, { method: 'PUT', body })
export const del = (p) => api(p, { method: 'DELETE' })
