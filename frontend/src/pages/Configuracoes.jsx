import { useEffect, useState } from 'react'
import { get, put } from '../api.js'
import { Campo, Erro, brl, useAcao, useCarga } from '../ui.jsx'

export default function Configuracoes() {
  const taxa = useCarga(() => get('/api/gestao/entrega/taxa'), [])
  const acao = useAcao()
  const [valor, setValor] = useState('')
  const [salvo, setSalvo] = useState(false)

  useEffect(() => { if (taxa.dados) setValor(String(taxa.dados.taxaEntrega)) }, [taxa.dados])

  const salvar = (e) => {
    e.preventDefault()
    setSalvo(false)
    acao.executar(async () => {
      await put('/api/gestao/entrega/taxa', { taxaEntrega: Number(valor) })
      await taxa.recarregar()
      setSalvo(true)
    })
  }

  return (
    <>
      <h1>Configurações</h1>
      <Erro erro={taxa.erro} />

      <section className="cartao">
        <h2>Taxa de entrega</h2>
        <p className="mudo">Atual: {brl(taxa.dados?.taxaEntrega)}. Taxa fixa para toda entrega; não há pedido mínimo. Ela é apresentada ao cliente antes da confirmação.</p>
        <form className="linha" onSubmit={salvar}>
          <Campo label="Nova taxa (R$)">
            <input type="number" step="0.01" min="0" value={valor} onChange={(e) => { setValor(e.target.value); setSalvo(false) }} required />
          </Campo>
          <button className="btn" disabled={acao.ocupado || valor === ''}>{acao.ocupado ? 'Salvando…' : 'Salvar taxa'}</button>
        </form>
        <Erro erro={acao.erro} />
        {salvo && <p className="alerta ok" role="status">Taxa de entrega atualizada.</p>}
      </section>
    </>
  )
}
