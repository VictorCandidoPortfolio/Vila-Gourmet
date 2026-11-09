export default function Logo({ compacto }) {
  return (
    <div className="logo">
      <span className="logo-nome">Vila <em>Gourmet</em></span>
      {!compacto && <span className="logo-lema">Pizzas · Sabores · Bons momentos</span>}
    </div>
  )
}
