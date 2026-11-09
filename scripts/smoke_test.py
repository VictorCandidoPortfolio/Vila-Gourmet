"""Teste de fumaça ponta a ponta da API (requer a API em http://localhost:8080 e um banco VAZIO).

Percorre catálogo, cotação, entrega, retirada, mesa, cancelamento, pagamento, relatórios e permissões.
Uso:  python scripts/smoke_test.py
Cria o administrador pelo primeiro acesso (por isso exige banco VAZIO). NUNCA rode contra o banco de desenvolvimento com dados reais.
"""
import json, sys, urllib.request, urllib.error, uuid

import os
BASE = os.environ.get("API_URL", "http://localhost:8080")
falhas = []

def call(method, path, body=None, token=None, headers=None, esperado=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    for k, v in (headers or {}).items():
        req.add_header(k, v)
    try:
        with urllib.request.urlopen(req) as r:
            status, txt = r.status, r.read().decode()
    except urllib.error.HTTPError as e:
        status, txt = e.code, e.read().decode()
    try:
        corpo = json.loads(txt) if txt else None
    except ValueError:
        corpo = txt
    if esperado is not None and status != esperado:
        falhas.append(f"{method} {path}: esperado {esperado}, veio {status}: {txt[:300]}")
        print("  FALHA", method, path, status, txt[:300])
    return status, corpo

def check(nome, cond, extra=""):
    print(("  ok   " if cond else "  FALHA ") + nome + (f" -> {extra}" if not cond else ""))
    if not cond:
        falhas.append(nome + " " + str(extra))

print("== primeiro acesso")
s, r = call("GET", "/api/auth/funcionario/configuracao-inicial", esperado=200)
check("primeiro acesso necessário", r["primeiroAcessoNecessario"] is True)
s, r = call("POST", "/api/auth/funcionario/primeiro-acesso", {"nome": "Admin Teste", "email": "admin@vilagourmet.local", "senha": "Admin@12345"}, esperado=201)
s, _ = call("POST", "/api/auth/funcionario/primeiro-acesso", {"nome": "Outro", "email": "outro@x.com", "senha": "Outra@12345"}, esperado=403)
s, r2 = call("GET", "/api/auth/funcionario/configuracao-inicial", esperado=200)
check("primeiro acesso encerrado", r2["primeiroAcessoNecessario"] is False)
print("== login admin")
s, r = call("POST", "/api/auth/funcionario/login", {"email": "admin@vilagourmet.local", "senha": "Admin@12345"}, esperado=200)
adm = r["token"]
check("admin tem todas as permissões", len(r["permissoes"]) == 11, r["permissoes"])
s, _ = call("POST", "/api/auth/funcionario/login", {"email": "admin@vilagourmet.local", "senha": "errada"}, esperado=401)

print("== caixa fechado: sem vendas")
s, st = call("GET", "/api/public/loja/status", esperado=200)
check("loja fechada antes de abrir o caixa", st["aberta"] is False, st)
s, _ = call("POST", "/api/public/mesas", {"nome": "Sem caixa", "cpf": "52998224725"}, esperado=409)
s, cx = call("GET", "/api/gestao/caixa", token=adm, esperado=200)
check("caixa começa fechado", cx["aberto"] is False, cx)
s, cx = call("POST", "/api/gestao/caixa/abrir", token=adm, esperado=200)
check("caixa aberto", cx["aberto"] is True and cx["abertoPor"] == "Admin Teste", cx)
s, _ = call("POST", "/api/gestao/caixa/abrir", token=adm, esperado=409)
s, st = call("GET", "/api/public/loja/status", esperado=200)
check("loja aberta com o caixa aberto", st["aberta"] is True, st)

print("== catálogo")
s, cat = call("POST", "/api/gestao/categorias", {"nome": "Pizzas " + uuid.uuid4().hex[:4]}, adm, esperado=201)
def novo_insumo(nome, ref=0):
    return call("POST", "/api/gestao/insumos", {"nome": nome, "valorReferencia": ref}, adm, esperado=201)[1]
ins_cal, ins_fra, ins_bor = novo_insumo("Calabresa", 20), novo_insumo("Frango", 24), novo_insumo("Borda", 6)
ins = ins_cal
s, prod = call("POST", "/api/gestao/produtos", {"categoriaId": cat["id"], "tipo": "COMPOSTO", "nome": "Pizza Montada",
        "precoBase": 35, "estrategia": "SOMA", "participaPrecoBase": False}, adm, esperado=201)
pid = prod["id"]
s, v = call("GET", f"/api/gestao/produtos/{pid}/validacao", token=adm, esperado=200)
check("produto sem grupos é inválido", v["valido"] is False)
s, _ = call("POST", f"/api/gestao/produtos/{pid}/publicar", token=adm, esperado=422)
s, prod = call("POST", f"/api/gestao/produtos/{pid}/grupos", {"nome": "Sabores", "minimo": 1, "maximo": 2, "modalidadeCobranca": "PRINCIPAL"}, adm, esperado=201)
g1 = prod["grupos"][0]["id"]
s, prod = call("POST", f"/api/gestao/produtos/{pid}/grupos", {"nome": "Extras", "minimo": 0, "maximo": 1, "modalidadeCobranca": "SEPARADA"}, adm, esperado=201)
g2 = prod["grupos"][1]["id"]
s, prod = call("POST", f"/api/gestao/grupos/{g1}/opcoes", {"insumoId": ins_cal["id"], "preco": 20, "minimo": 0, "maximo": 1}, adm, esperado=201)
s, prod = call("POST", f"/api/gestao/grupos/{g1}/opcoes", {"insumoId": ins_fra["id"], "preco": 24, "minimo": 0, "maximo": 1}, adm, esperado=201)
s, prod = call("POST", f"/api/gestao/grupos/{g2}/opcoes", {"insumoId": ins_bor["id"], "preco": 6, "minimo": 0, "maximo": 1}, adm, esperado=201)
o_cal, o_fra = prod["grupos"][0]["opcoes"][0]["id"], prod["grupos"][0]["opcoes"][1]["id"]
o_borda = prod["grupos"][1]["opcoes"][0]["id"]
# conflito: mínimo do grupo > soma dos máximos
s, _ = call("PUT", f"/api/gestao/grupos/{g1}", {"nome": "Sabores", "minimo": 3, "maximo": 3, "modalidadeCobranca": "PRINCIPAL"}, adm, esperado=200)
s, v = call("GET", f"/api/gestao/produtos/{pid}/validacao", token=adm, esperado=200)
check("conflito de limites detectado", v["valido"] is False and any("mínimo do grupo" in p for p in v["problemas"]), v)
s, _ = call("PUT", f"/api/gestao/grupos/{g1}", {"nome": "Sabores", "minimo": 1, "maximo": 2, "modalidadeCobranca": "PRINCIPAL"}, adm, esperado=200)
s, prod = call("POST", f"/api/gestao/produtos/{pid}/publicar", token=adm, esperado=200)
check("publicado", prod["status"] == "PUBLICADO")
s, pub = call("GET", "/api/public/catalogo", esperado=200)
check("catálogo público lista o produto", any(p["id"] == pid for p in pub))

# produto simples
s, simples = call("POST", "/api/gestao/produtos", {"categoriaId": cat["id"], "tipo": "SIMPLES", "nome": "Refrigerante", "precoBase": 8.5}, adm, esperado=201)
s, simples = call("POST", f"/api/gestao/produtos/{simples['id']}/adicionais", {"nome": "Gelo e limão", "preco": 1}, adm, esperado=201)
s, simples = call("POST", f"/api/gestao/produtos/{simples['id']}/publicar", token=adm, esperado=200)
ad_id = simples["adicionais"][0]["id"]

# a opção é um insumo: sem insumo é inválido, insumo repetido no grupo é recusado
call("POST", f"/api/gestao/grupos/{g1}/opcoes", {"preco": 1, "minimo": 0, "maximo": 1}, adm, esperado=400)
call("POST", f"/api/gestao/grupos/{g1}/opcoes", {"insumoId": ins_cal["id"], "preco": 1, "minimo": 0, "maximo": 1}, adm, esperado=422)
check("nome da opção vem do insumo", prod["grupos"][0]["opcoes"][0]["nome"] == "Calabresa", prod["grupos"][0]["opcoes"])

print("== cotação")
s, cot = call("POST", "/api/public/cotacoes", {"produtoId": pid, "quantidade": 2,
    "escolhas": [{"opcaoId": o_cal, "quantidade": 1}, {"opcaoId": o_fra, "quantidade": 1}, {"opcaoId": o_borda, "quantidade": 1}]}, esperado=200)
check("soma 20+24 + borda 6 = 50 x2 = 100", cot["item"]["precoUnitario"] == 50 and cot["item"]["subtotal"] == 100, cot["item"])
tok_cot = cot["cotacaoToken"]
s, _ = call("POST", "/api/public/cotacoes", {"produtoId": pid, "quantidade": 1, "escolhas": []}, esperado=422)
s, cot_s = call("POST", "/api/public/cotacoes", {"produtoId": simples["id"], "quantidade": 1, "adicionais": [{"adicionalId": ad_id, "quantidade": 2}]}, esperado=200)
check("simples 8.50 + 2x1.00 = 10.50", cot_s["item"]["precoUnitario"] == 10.5, cot_s["item"])

print("== preço garantido na sessão")
s, _ = call("PUT", f"/api/gestao/opcoes/{o_cal}", {"insumoId": ins_cal["id"], "preco": 99, "minimo": 0, "maximo": 1}, adm, esperado=200)

print("== cliente + entrega")
mail = f"cli{uuid.uuid4().hex[:6]}@teste.com"
s, c = call("POST", "/api/auth/cliente/cadastro", {"email": mail, "senha": "senha1234", "nome": "Maria Cliente", "cpf": "529.982.247-25"}, esperado=201)
cli = c["token"]
s, _ = call("POST", "/api/auth/cliente/cadastro", {"email": mail, "senha": "senha1234", "nome": "X", "cpf": "52998224725"}, esperado=409)
s, _ = call("POST", "/api/auth/cliente/cadastro", {"email": "x" + mail, "senha": "senha1234", "nome": "X", "cpf": "11111111111"}, esperado=422)
s, e1 = call("POST", "/api/cliente/enderecos", {"cep": "01001-000", "logradouro": "Rua A", "numero": "10", "bairro": "Centro", "cidade": "São Paulo", "estado": "SP", "padrao": False}, cli, esperado=201)
check("primeiro endereço vira padrão", e1["padrao"] is True)
s, e2 = call("POST", "/api/cliente/enderecos", {"cep": "01001-000", "logradouro": "Rua B", "numero": "20", "bairro": "Longe", "cidade": "São Paulo", "estado": "SP", "padrao": True}, cli, esperado=201)
s, lista = call("GET", "/api/cliente/enderecos", token=cli, esperado=200)
check("apenas um endereço padrão", sum(1 for x in lista if x["padrao"]) == 1 and [x for x in lista if x["padrao"]][0]["id"] == e2["id"], lista)

s, _ = call("PUT", "/api/gestao/entrega/taxa", {"taxaEntrega": 7.5}, adm, esperado=200)
s, area = call("POST", "/api/gestao/entrega/areas", {"bairro": "Centro", "cidade": "São Paulo"}, adm, esperado=201)
s, a = call("GET", "/api/public/entrega/area?bairro=Centro&cidade=S%C3%A3o%20Paulo", esperado=200)
check("área atendida + taxa", a["atendida"] is True and a["taxaEntrega"] == 7.5, a)

ped_body = {"modalidade": "ENTREGA", "itens": [{"cotacaoToken": tok_cot}], "enderecoId": e1["id"], "metodoPagamento": "DINHEIRO"}
s, _ = call("POST", "/api/public/pedidos", ped_body, esperado=403)  # visitante não faz entrega
s, _ = call("POST", "/api/public/pedidos", {**ped_body, "enderecoId": e2["id"]}, cli, esperado=422)  # fora da área
idem = "k-" + uuid.uuid4().hex
s, ped = call("POST", "/api/public/pedidos", ped_body, cli, headers={"Idempotency-Key": idem}, esperado=201)
p = ped["pedido"]
check("cotação honra preço antigo: subtotal 100 + taxa 7.5", p["subtotal"] == 100 and p["taxaEntrega"] == 7.5 and p["total"] == 107.5, p)
check("códigos retornados", bool(ped["codigoAcompanhamento"]) and len(ped["codigoConfirmacao"]) == 6)
check("CPF mascarado", p["cpfResponsavel"] == "***.***.***-25", p["cpfResponsavel"])
s, ped2 = call("POST", "/api/public/pedidos", ped_body, cli, headers={"Idempotency-Key": idem}, esperado=201)
check("idempotência devolve o mesmo pedido", ped2["pedido"]["id"] == p["id"])
# nova cotação com preço vigente
s, cot2 = call("POST", "/api/public/cotacoes", {"produtoId": pid, "quantidade": 1, "escolhas": [{"opcaoId": o_cal, "quantidade": 1}]}, esperado=200)
check("nova sessão usa preço vigente (99)", cot2["item"]["precoUnitario"] == 99, cot2["item"])
check("snapshot guarda estratégia", p["itens"][0]["tipo"] == "COMPOSTO")
s, _ = call("PUT", f"/api/gestao/opcoes/{o_cal}", {"insumoId": ins_cal["id"], "preco": 20, "minimo": 0, "maximo": 1}, adm, esperado=200)
s, p_depois = call("GET", f"/api/gestao/pedidos/{p['id']}", token=adm, esperado=200)
check("pedido histórico inalterado após mudança de catálogo", p_depois["total"] == 107.5)

print("== rastreamento")
cod = ped["codigoAcompanhamento"]
s, rt = call("POST", "/api/public/rastreamento", {"codigo": cod}, esperado=200)
check("rastreio por código sem dados pessoais", "cpfResponsavel" not in rt and "endereco" not in rt and rt["estado"] == "AGUARDANDO_CONFIRMACAO", rt)
s, rt = call("POST", "/api/public/rastreamento", {"numeroPedido": p["numeroPedido"], "cpf": "52998224725"}, esperado=200)
s, _ = call("POST", "/api/public/rastreamento", {"numeroPedido": p["numeroPedido"], "cpf": "00000000000"}, esperado=404)

print("== fluxo de estados da entrega")
call("POST", f"/api/gestao/pedidos/{p['id']}/estado", {"estado": "EM_PREPARACAO"}, adm, esperado=422)  # pulo inválido
call("POST", f"/api/gestao/pedidos/{p['id']}/estado", {"estado": "CONFIRMADO"}, adm, esperado=200)
call("POST", f"/api/gestao/pedidos/{p['id']}/estado", {"estado": "EM_PREPARACAO"}, adm, esperado=200)
call("POST", f"/api/gestao/pedidos/{p['id']}/estado", {"estado": "PRONTO_PARA_RETIRADA"}, adm, esperado=422)  # estado de retirada
call("POST", f"/api/gestao/pedidos/{p['id']}/estado", {"estado": "A_CAMINHO"}, adm, esperado=200)
call("POST", f"/api/gestao/pedidos/{p['id']}/estado", {"estado": "CONCLUIDO"}, adm, esperado=422)  # exige código
for i in range(5):
    call("POST", f"/api/gestao/pedidos/{p['id']}/concluir-com-codigo", {"codigo": "000000" if ped["codigoConfirmacao"] != "000000" else "111111"}, adm, esperado=422)
s, _ = call("POST", f"/api/gestao/pedidos/{p['id']}/concluir-com-codigo", {"codigo": ped["codigoConfirmacao"]}, adm, esperado=429)
check("limite de tentativas bloqueia até o código correto", s == 429)
s, novo = call("POST", f"/api/cliente/pedidos/{p['id']}/codigo-confirmacao", token=cli, esperado=200)
s, fin = call("POST", f"/api/gestao/pedidos/{p['id']}/concluir-com-codigo", {"codigo": novo["codigoConfirmacao"]}, adm, esperado=200)
check("concluído com código", fin["estado"] == "CONCLUIDO")
s, _ = call("POST", f"/api/gestao/pedidos/{p['id']}/concluir-com-codigo", {"codigo": novo["codigoConfirmacao"]}, adm, esperado=422)

print("== pagamento")
s, _ = call("POST", f"/api/gestao/pedidos/{p['id']}/pagamento", {"metodo": "DINHEIRO", "valorRecebido": 100}, adm, esperado=422)
s, pg = call("POST", f"/api/gestao/pedidos/{p['id']}/pagamento", {"metodo": "DINHEIRO", "valorRecebido": 110}, adm, esperado=200)
check("troco 2.50", pg["estado"] == "CONFIRMADO" and pg["troco"] == 2.5, pg)
s, _ = call("POST", f"/api/gestao/pedidos/{p['id']}/pagamento", {"metodo": "PIX"}, adm, esperado=409)

print("== retirada de visitante, cancelamento e conclusão manual")
ret = {"modalidade": "RETIRADA", "itens": [{"produtoId": simples["id"], "quantidade": 2, "adicionais": [{"adicionalId": ad_id, "quantidade": 1}]}],
       "nomeResponsavel": "João Visitante", "cpfResponsavel": "52998224725", "metodoPagamento": "PIX"}
s, _ = call("POST", "/api/public/pedidos", {**ret, "cpfResponsavel": None}, esperado=422)
s, r1 = call("POST", "/api/public/pedidos", ret, esperado=201)
check("retirada total 2 x 9.50 = 19", r1["pedido"]["total"] == 19, r1["pedido"]["total"])
s, canc = call("POST", "/api/public/pedidos/cancelamento", {"codigo": r1["codigoAcompanhamento"]}, esperado=200)
check("cancelado direto antes da confirmação", canc["estado"] == "APROVADO" and canc["estadoPedido"] == "CANCELADO", canc)
s, r2 = call("POST", "/api/public/pedidos", ret, esperado=201)
rid = r2["pedido"]["id"]
call("POST", f"/api/gestao/pedidos/{rid}/estado", {"estado": "CONFIRMADO"}, adm, esperado=200)
s, canc = call("POST", "/api/public/pedidos/cancelamento", {"numeroPedido": r2["pedido"]["numeroPedido"], "cpf": "52998224725"}, esperado=200)
check("após confirmação: pendente de aprovação", canc["estado"] == "PENDENTE" and canc["estadoPedido"] == "CONFIRMADO", canc)
s, _ = call("POST", "/api/public/pedidos/cancelamento", {"codigo": r2["codigoAcompanhamento"]}, esperado=409)
s, dec = call("POST", f"/api/gestao/cancelamentos/{canc['id']}/decisao", {"aprovar": False}, adm, esperado=422)
s, dec = call("POST", f"/api/gestao/cancelamentos/{canc['id']}/decisao", {"aprovar": False, "justificativa": "Já em produção"}, adm, esperado=200)
check("rejeitado mantém o pedido", dec["estado"] == "REJEITADO" and dec["estadoPedido"] == "CONFIRMADO")
call("POST", f"/api/gestao/pedidos/{rid}/estado", {"estado": "EM_PREPARACAO"}, adm, esperado=200)
call("POST", f"/api/gestao/pedidos/{rid}/estado", {"estado": "PRONTO_PARA_RETIRADA"}, adm, esperado=200)
call("POST", f"/api/gestao/pedidos/{rid}/concluir-manual", {"justificativa": " "}, adm, esperado=400)
s, fin = call("POST", f"/api/gestao/pedidos/{rid}/concluir-manual", {"justificativa": "Cliente esqueceu o código"}, adm, esperado=200)
manual = [h for h in fin["historico"] if h["conclusaoManual"]]
check("conclusão manual auditada", len(manual) == 1 and manual[0]["justificativa"] == "Cliente esqueceu o código" and manual[0]["funcionarioId"])

print("== mesa")
s, mesa = call("POST", "/api/public/mesas", {"nome": "Carlos Mesa", "cpf": "52998224725"}, esperado=201)
cm = mesa["codigoConta"]
check("conta aberta", mesa["estado"] == "ABERTA")
s, _ = call("POST", f"/api/public/mesas/{cm}/pedidos", {"cpfResponsavel": "11144477735", "itens": [{"produtoId": simples["id"], "quantidade": 1}]}, esperado=404)
s, m1 = call("POST", f"/api/public/mesas/{cm}/pedidos", {"cpfResponsavel": "52998224725", "itens": [{"produtoId": simples["id"], "quantidade": 1}]}, esperado=201)
s, m2 = call("POST", f"/api/gestao/mesas/{cm}/pedidos", {"itens": [{"produtoId": simples["id"], "quantidade": 3}]}, adm, esperado=201)
s, conta = call("GET", f"/api/gestao/mesas/{cm}", token=adm, esperado=200)
check("conta acumula 2 pedidos: 8.5 + 25.5 = 34", len(conta["pedidos"]) == 2 and conta["total"] == 34, conta["total"])
s, _ = call("POST", f"/api/gestao/mesas/{cm}/fechamento", token=adm, esperado=422)  # pedidos ainda não servidos
for m in (m1, m2):
    mid = m["pedido"]["id"]
    for est in ("CONFIRMADO", "EM_PREPARACAO", "SERVIDO"):
        call("POST", f"/api/gestao/pedidos/{mid}/estado", {"estado": est}, adm, esperado=200)
s, _ = call("POST", f"/api/gestao/pedidos/{m2['pedido']['id']}/pagamento", {"metodo": "PIX"}, adm, esperado=422)  # mesa paga no fechamento
s, conta = call("POST", f"/api/gestao/mesas/{cm}/fechamento", token=adm, esperado=200)
check("em fechamento com total 34", conta["estado"] == "EM_FECHAMENTO" and conta["fechamento"]["total"] == 34, conta["fechamento"])
s, _ = call("POST", f"/api/public/mesas/{cm}/pedidos", {"cpfResponsavel": "52998224725", "itens": [{"produtoId": simples["id"], "quantidade": 1}]}, esperado=422)
s, pg = call("POST", f"/api/gestao/mesas/fechamentos/{conta['fechamento']['id']}/pagamento", {"metodo": "CARTAO"}, adm, esperado=200)
s, conta = call("GET", f"/api/gestao/mesas/{cm}", token=adm, esperado=200)
check("conta encerrada e pedidos concluídos", conta["estado"] == "ENCERRADA" and all(x["estado"] == "CONCLUIDO" for x in conta["pedidos"]), conta["estado"])
check("histórico da conta registrado", [h["estadoNovo"] for h in conta["historico"]] == ["ABERTA", "EM_FECHAMENTO", "ENCERRADA"], conta["historico"])

print("== relatório")
s, rel = call("GET", "/api/gestao/relatorios/operacional", token=adm, esperado=200)
check("relatório coerente", rel["quantidadePedidos"] == 5 and rel["totalVendido"] == 107.5 + 19 + 34, rel)
check("recebido separado do vendido", rel["totalRecebido"] == 107.5 + 34, rel["totalRecebido"])

print("== frente de caixa e histórico")
s, cot = call("POST", "/api/public/cotacoes", {"produtoId": simples["id"], "quantidade": 1}, esperado=200)
item_cx = {"cotacaoToken": cot["cotacaoToken"]}
s, r1 = call("POST", "/api/gestao/caixa/pedidos", {"modalidade": "RETIRADA", "itens": [item_cx], "nomeResponsavel": "Cliente do Balcão"}, adm, esperado=201)
check("retirada lançada no caixa só com o nome", r1["pedido"]["modalidade"] == "RETIRADA" and r1["pedido"].get("cpfResponsavel") is None and r1["codigoConfirmacao"], r1)
call("POST", "/api/gestao/caixa/pedidos", {"modalidade": "RETIRADA", "itens": [item_cx]}, adm, esperado=422)  # sem nome
call("POST", "/api/gestao/caixa/pedidos", {"modalidade": "RETIRADA", "itens": [item_cx], "nomeResponsavel": "X", "cpfResponsavel": "11111111111"}, adm, esperado=422)  # CPF, se informado, precisa ser válido
end_cx = {"cep": "01001-000", "logradouro": "Praça da Sé", "numero": "100", "complemento": "ap 12", "bairro": "Centro", "cidade": "São Paulo", "estado": "SP"}
s, ent = call("POST", "/api/gestao/caixa/pedidos", {"modalidade": "ENTREGA", "itens": [item_cx], "nomeResponsavel": "Fulano por telefone", "endereco": end_cx}, adm, esperado=201)
check("entrega lançada no caixa sem cadastro de cliente", ent["pedido"]["taxaEntrega"] == 7.5 and ent["pedido"]["endereco"]["bairro"] == "Centro" and ent["pedido"]["nomeResponsavel"] == "Fulano por telefone", ent)
call("POST", "/api/gestao/caixa/pedidos", {"modalidade": "ENTREGA", "itens": [item_cx], "nomeResponsavel": "Fulano", "endereco": {**end_cx, "bairro": "Longe"}}, adm, esperado=422)  # fora da área
call("POST", "/api/gestao/caixa/pedidos", {"modalidade": "ENTREGA", "itens": [item_cx], "nomeResponsavel": "Fulano"}, adm, esperado=422)  # sem endereço
call("POST", "/api/gestao/caixa/pedidos", {"modalidade": "ENTREGA", "itens": [item_cx], "nomeResponsavel": "Fulano", "endereco": {**end_cx, "cep": "123"}}, adm, esperado=422)
s, mc = call("POST", "/api/gestao/caixa/mesas", {"nome": "Mesa do Balcão"}, adm, esperado=201)
check("conta de mesa aberta só com o nome", mc.get("cpfResponsavel") is None and mc["estado"] == "ABERTA", mc)
call("POST", f"/api/public/mesas/{mc['codigoConta']}/pedidos", {"cpfResponsavel": "52998224725", "itens": [item_cx]}, esperado=404)  # visitante não entra em conta sem CPF
s, rm = call("POST", "/api/gestao/caixa/pedidos", {"modalidade": "MESA", "itens": [item_cx], "codigoConta": mc["codigoConta"]}, adm, esperado=201)
check("pedido de mesa lançado no caixa", rm["pedido"]["modalidade"] == "MESA", rm)
s, eu = call("GET", "/api/gestao/eu", token=adm, esperado=200)
check("sessão do funcionário traz as permissões atuais", "CAIXA_OPERAR" in eu["permissoes"] and eu["nome"] == "Admin Teste", eu)
s, abertas = call("GET", "/api/gestao/caixa/mesas", token=adm, esperado=200)
check("conta de mesa aparece entre as abertas", any(c["codigoConta"] == mc["codigoConta"] for c in abertas), abertas)

s, andamento = call("GET", "/api/gestao/pedidos?situacao=ANDAMENTO&tamanho=100", token=adm, esperado=200)
s, final = call("GET", "/api/gestao/pedidos?situacao=FINALIZADOS&tamanho=100", token=adm, esperado=200)
check("em andamento não traz concluídos nem cancelados", all(x["estado"] not in ("CONCLUIDO", "CANCELADO") for x in andamento["content"]) and len(andamento["content"]) >= 3, [x["estado"] for x in andamento["content"]])
check("histórico só traz concluídos e cancelados", len(final["content"]) >= 1 and all(x["estado"] in ("CONCLUIDO", "CANCELADO") for x in final["content"]), [x["estado"] for x in final["content"]])
import datetime
hoje = datetime.date.today().isoformat()
ontem = (datetime.date.today() - datetime.timedelta(days=1)).isoformat()
s, h_hoje = call("GET", f"/api/gestao/pedidos?situacao=FINALIZADOS&de={hoje}&ate={hoje}&tamanho=100", token=adm, esperado=200)
s, h_ontem = call("GET", f"/api/gestao/pedidos?situacao=FINALIZADOS&de={ontem}&ate={ontem}&tamanho=100", token=adm, esperado=200)
check("filtro de dia: hoje traz o histórico e ontem vem vazio", len(h_hoje["content"]) == len(final["content"]) and len(h_ontem["content"]) == 0, (len(h_hoje["content"]), len(h_ontem["content"])))

print("== fechamento do caixa")
s, cx = call("GET", "/api/gestao/caixa", token=adm, esperado=200)
check("caixa mostra os pedidos em aberto", cx["pedidosEmAberto"] == len(andamento["content"]) and cx["bloqueios"] == [], cx)
s, aviso = call("POST", "/api/gestao/caixa/fechar", {}, adm, esperado=409)
check("fechar com pedidos em aberto avisa que serão cancelados", "serão cancelados" in aviso["mensagem"] and len(aviso["detalhes"]) == cx["pedidosEmAberto"], aviso)
s, st = call("GET", "/api/public/loja/status", esperado=200)
check("nada foi fechado sem confirmação", st["aberta"] is True)
s, fim = call("POST", "/api/gestao/caixa/fechar", {"confirmarCancelamentos": True}, adm, esperado=200)
check("pedidos em aberto cancelados no fechamento", fim["pedidosCancelados"] == cx["pedidosEmAberto"] and fim["contasEncerradas"] >= 1, fim)
s, andamento = call("GET", "/api/gestao/pedidos?situacao=ANDAMENTO&tamanho=100", token=adm, esperado=200)
check("nada em andamento depois do fechamento", len(andamento["content"]) == 0, andamento["content"])
s, pc = call("GET", f"/api/gestao/mesas/{mc['codigoConta']}", token=adm, esperado=200)
check("conta de mesa aberta foi encerrada sem cobrança", pc["estado"] == "ENCERRADA" and pc["pedidos"][0]["estado"] == "CANCELADO", pc["estado"])
s, st = call("GET", "/api/public/loja/status", esperado=200)
check("loja fechada depois do fechamento", st["aberta"] is False, st)
s, cot2 = call("POST", "/api/public/cotacoes", {"produtoId": simples["id"], "quantidade": 1}, esperado=200)
call("POST", "/api/public/pedidos", {"modalidade": "RETIRADA", "itens": [{"cotacaoToken": cot2["cotacaoToken"]}], "nomeResponsavel": "Fora do horário", "cpfResponsavel": "52998224725"}, esperado=409)
call("POST", "/api/public/pedidos", {**ped_body, "itens": [{"cotacaoToken": cot2["cotacaoToken"]}]}, cli, esperado=409)
call("POST", "/api/gestao/caixa/pedidos", {"modalidade": "RETIRADA", "itens": [{"cotacaoToken": cot2["cotacaoToken"]}], "nomeResponsavel": "X", "cpfResponsavel": "52998224725"}, adm, esperado=409)
call("POST", "/api/gestao/caixa/fechar", {}, adm, esperado=409)  # já fechado
s, _ = call("POST", "/api/gestao/caixa/abrir", token=adm, esperado=200)  # reabre para o resto do teste
s, cx = call("GET", "/api/gestao/caixa", token=adm, esperado=200)
check("caixa reaberto sem pedidos em aberto", cx["aberto"] is True and cx["pedidosEmAberto"] == 0, cx)

print("== permissões")
s, perfis = call("GET", "/api/gestao/perfis", token=adm, esperado=200)
coz = [x for x in perfis if x["nome"] == "COZINHA"][0]
s, _ = call("POST", "/api/gestao/funcionarios", {"email": f"coz{uuid.uuid4().hex[:5]}@vila.com", "senha": "cozinha123", "nome": "Cozinheiro", "perfilIds": [coz["id"]]}, adm, esperado=201)
s, lf = call("GET", "/api/gestao/funcionarios", token=adm, esperado=200)
email_coz = [f for f in lf if "Cozinheiro" == f["nome"]][0]["email"]
s, l = call("POST", "/api/auth/funcionario/login", {"email": email_coz, "senha": "cozinha123"}, esperado=200)
tcoz = l["token"]
call("GET", "/api/gestao/pedidos", token=tcoz, esperado=200)
call("GET", "/api/gestao/caixa", token=tcoz, esperado=403)
call("POST", "/api/gestao/caixa/abrir", token=tcoz, esperado=403)
call("GET", "/api/gestao/caixa", token=cli, esperado=403)
call("GET", "/api/gestao/eu", token=cli, esperado=403)
call("POST", "/api/gestao/categorias", {"nome": "X"}, tcoz, esperado=403)
call("POST", f"/api/gestao/pedidos/{p['id']}/pagamento", {"metodo": "PIX"}, tcoz, esperado=403)
call("GET", "/api/gestao/pedidos", token=cli, esperado=403)   # cliente não acessa gestão
call("GET", "/api/gestao/pedidos", esperado=401)
call("GET", "/api/gestao/pedidos", token="abc.def.ghi", esperado=401)
s, _ = call("POST", "/api/auth/funcionario/login", {"email": email_coz, "senha": "cozinha123"}, esperado=200)

print()
if falhas:
    print(f"{len(falhas)} FALHA(S)")
    sys.exit(1)
print("SMOKE OK")
