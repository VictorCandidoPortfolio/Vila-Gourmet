import { Link, useNavigate } from 'react-router-dom'
import { get } from '../../api.js'
import Icon from '../../components/Icon.jsx'
import { Erro, useCarga } from '../../ui.jsx'
import { useCarrinho } from '../carrinho.jsx'

const COMO = [
  { icone: 'pizza', titulo: 'Monte do seu jeito', texto: 'Escolha sabores e adicionais de cada produto do cardápio.' },
  { icone: 'dollar', titulo: 'Preço na hora', texto: 'O valor é calculado a cada escolha e fica garantido enquanto você monta o pedido.' },
  { icone: 'clock', titulo: 'Acompanhe o pedido', texto: 'Veja em que etapa ele está com o código de acompanhamento.' },
]

export default function Inicio() {
  const navigate = useNavigate()
  const { setModalidade } = useCarrinho()
  const { dados: catalogo, erro } = useCarga(() => get('/api/public/catalogo'), [])

  const pedir = (modalidade) => {
    setModalidade(modalidade)
    navigate('/cardapio')
  }

  // Uma categoria por card, com a foto do primeiro produto que tiver uma.
  const categorias = []
  catalogo?.forEach((p) => {
    let c = categorias.find((x) => x.id === p.categoriaId)
    if (!c) { c = { id: p.categoriaId, nome: p.categoriaNome, quantidade: 0, imagem: null }; categorias.push(c) }
    c.quantidade += 1
    c.imagem ??= p.imagemUrl
  })
  categorias.sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR'))

  return (
    <>
      <section className="lj-hero">
        <div className="lj-hero-int">
          <p className="lj-eyebrow">Pizzas · Sabores · Bons momentos</p>
          <h1>Mais que pizza,<br />é um <em>bom momento.</em></h1>
          <p className="lj-hero-texto">Escolha como quer receber o seu pedido.</p>
          <div className="lj-hero-acoes">
            <button className="btn grande" onClick={() => pedir('ENTREGA')}><Icon nome="truck" /> Pedir delivery</button>
            <button className="btn contorno grande" onClick={() => pedir('RETIRADA')}><Icon nome="bag" /> Retirar no local</button>
            <button className="btn contorno grande" onClick={() => navigate('/mesa')}><Icon nome="store" /> Consumir na mesa</button>
          </div>
          <ul className="lj-como">
            {COMO.map((c) => (
              <li key={c.titulo}>
                <Icon nome={c.icone} tamanho={26} />
                <span><strong>{c.titulo}</strong>{c.texto}</span>
              </li>
            ))}
          </ul>
        </div>
      </section>

      <section className="lj-secao">
        <p className="lj-sobretitulo">Nossas categorias</p>
        <h2 className="lj-titulo">Encontre o seu sabor favorito</h2>
        <Erro erro={erro} />
        {catalogo && categorias.length === 0 && <p className="mudo lj-centro">O cardápio ainda não tem produtos disponíveis.</p>}
        <div className="lj-categorias">
          {categorias.map((c) => (
            <Link key={c.id} to={`/cardapio?categoria=${c.id}`} className="lj-categoria">
              <div className="lj-categoria-img" style={c.imagem ? { backgroundImage: `url(${c.imagem})` } : undefined}>
                {!c.imagem && <Icon nome="pizza" tamanho={44} />}
              </div>
              <div className="lj-categoria-base">
                <span><strong>{c.nome}</strong><small>{c.quantidade} {c.quantidade === 1 ? 'produto' : 'produtos'}</small></span>
                <Icon nome="arrow" />
              </div>
            </Link>
          ))}
        </div>
      </section>
    </>
  )
}
