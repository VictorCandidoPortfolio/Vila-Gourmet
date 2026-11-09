# 🍕 Vila Gourmet

Sistema web de pizzaria: catálogo com produtos simples e compostos, motor de precificação, pedidos por
**entrega**, **retirada** e **mesa**, painel de gestão, pagamentos manuais, rastreamento e relatórios operacionais.

> **Estado: protótipo funcional (V1).** O **backend** cobre todas as funcionalidades da documentação v1.2.
> O **frontend** tem duas áreas no mesmo app: a **loja** do cliente/visitante (em `/`) e a **gestão** da equipe
> (em `/gestao`). Cada uma tem a própria sessão e é carregada separadamente.

---

## Sumário

- [Arquitetura e estrutura de pastas](#arquitetura-e-estrutura-de-pastas)
- [Stack](#stack)
- [Como executar](#como-executar)
- [Configuração (variáveis de ambiente)](#configuração-variáveis-de-ambiente)
- [Testes](#testes)
- [Visão geral da API](#visão-geral-da-api)
- [Regras de negócio implementadas](#regras-de-negócio-implementadas)
- [Decisões tomadas sobre as pendências da documentação](#decisões-tomadas-sobre-as-pendências-da-documentação)
- [Correções e melhorias sobre o modelo SQL v0.1](#correções-e-melhorias-sobre-o-modelo-sql-v01)
- [Segurança](#segurança)
- [Limitações conhecidas e próximos passos](#limitações-conhecidas-e-próximos-passos)

---

## Arquitetura e estrutura de pastas

Monorepo com `backend/` e `frontend/` independentes. O backend segue **MVC em camadas**: *controllers* só
traduzem HTTP ⇄ DTO; **toda a lógica de negócio fica nos *services***; entidades JPA formam o *model*; a *view* é o
JSON consumido pelo React.

```
Vila-Gourmet/
├── backend/                       API Spring Boot (Java 21, Maven)
│   ├── src/main/java/com/vilagourmet/
│   │   ├── config/                configuração: segurança/CORS, propriedades, carga inicial
│   │   ├── controller/            C — endpoints REST (finos; sem regra de negócio)
│   │   ├── dto/                   DTOs de entrada/saída (records + Bean Validation)
│   │   ├── service/               TODA a lógica: precificação, pedidos, estados, mesas, pagamentos...
│   │   ├── model/                 M — entidades JPA (+ model/enums)
│   │   ├── repository/            acesso a dados (Spring Data JPA)
│   │   ├── security/              filtro JWT e principal autenticado
│   │   ├── exception/             exceções de negócio e tratamento global de erros
│   │   └── util/                  CPF e aritmética monetária
│   ├── src/main/resources/
│   │   ├── application.properties   configuração (valores padrão de desenvolvimento)
│   │   └── db/migration/          migrações Flyway (V1 esquema, V2 permissões/perfis)
│   ├── src/test/                  testes de unidade (precificação, validação, CPF)
│   ├── application-local.properties.example   modelo das credenciais locais (o real fica fora do git)
│   └── Dockerfile
├── frontend/                      V — React + Vite
│   └── src/ loja/ (cliente: pages/, carrinho.jsx, cliente.jsx, loja.css)
│            pages/, components/ (gestão, em /gestao) · api.js, ui.jsx (compartilhados)
├── scripts/smoke_test.py          teste de fumaça ponta a ponta da API
├── docker-compose.yml             MySQL + API para desenvolvimento
└── README.md
```

## Stack

| Camada | Tecnologia |
|---|---|
| Frontend | React 18, Vite, React Router |
| Backend | Spring Boot 3.3, Java 21, Spring Security, Spring Data JPA, Bean Validation |
| Banco | MySQL 8.x (8.0.16+ por causa dos `CHECK`) |
| Migrações | Flyway (o Hibernate **não** altera o esquema: `ddl-auto: none`) |
| Autenticação | JWT (HS256, `jjwt`) |
| Senhas | BCrypt (fator 12, salt por hash) |

## Como executar

### Opção A — Docker (recomendada)

Requer Docker. Sobe MySQL + API:

```bash
docker compose up -d --build
```

* API: <http://localhost:8080> · MySQL exposto em `localhost:3307` (mude com `DB_PORT`).
* As migrações Flyway rodam sozinhas na primeira subida.
* **Primeiro acesso:** não há senha padrão. Ao abrir o frontend com o banco sem funcionários, a tela de login mostra o
  formulário **"Criar conta do administrador"**. Depois disso, esse endpoint se fecha e novas contas da equipe são criadas
  por um administrador em **Funcionários e perfis**.

Depois, o frontend:

```bash
cd frontend
npm install
npm run dev        # http://localhost:5173  (loja)  ·  http://localhost:5173/gestao  (equipe)
```

**Loja (cliente):** cardápio com busca e categorias; montagem do produto com o preço calculado pelo servidor
(cotação assinada); carrinho; pedido de **entrega** (exige conta, endereço salvo e área atendida), **retirada** (visitante
informa nome e CPF) e **mesa** (abre a conta com nome e CPF e pede pelo código); acompanhamento público por código ou
número + CPF, com solicitação de cancelamento; conta com histórico, endereços, pizzas salvas e novo código de confirmação.

Para zerar o banco: `docker compose down -v`.

### Opção B — Sem Docker

Requer JDK 21, Maven e um MySQL 8 com banco/usuário `pizzaria`:

```bash
cd backend
DB_URL="jdbc:mysql://localhost:3306/pizzaria?serverTimezone=America/Sao_Paulo" \
DB_USER=pizzaria DB_PASSWORD=pizzaria \
mvn spring-boot:run
```

## Configuração

Toda a configuração da API está em `backend/src/main/resources/application.properties` (valores padrão de desenvolvimento,
sem segredos). Cada valor aceita uma **variável de ambiente** (`${VARIAVEL:padrão}`) e pode ser sobrescrito, na sua máquina,
por um arquivo **fora do git**:

```bash
cd backend
cp application-local.properties.example application-local.properties   # edite e preencha
```

O `application-local.properties` (ignorado pelo git) é lido automaticamente se estiver na pasta de trabalho da execução
(`backend/`, ou a raiz do projeto) e tem prioridade sobre o `application.properties`. É o lugar certo para as credenciais do
Cloudinary, o `app.jwt.secret` etc. Reinicie a API depois de editar. **Nunca** coloque segredos no `application.properties`.

Com **Docker**, as mesmas opções entram por variáveis de ambiente (`docker compose` repassa as `CLOUDINARY_*`, `JWT_SECRET`, `DB_*`
e `CORS_ORIGENS` que existirem no seu ambiente).

| Propriedade (arquivo) | Variável de ambiente | Padrão (dev) | Descrição |
|---|---|---|---|
| `spring.datasource.url` / `.username` / `.password` | `DB_URL` / `DB_USER` / `DB_PASSWORD` | `localhost:3307` / `pizzaria` / `pizzaria` | Conexão MySQL |
| `app.jwt.secret` | `JWT_SECRET` | valor de desenvolvimento | **Trocar em produção.** Mínimo 32 caracteres. Também deriva a chave HMAC dos códigos |
| `app.jwt.expiracao-minutos` | `JWT_EXPIRACAO_MINUTOS` | `120` | Validade do token de autenticação |
| `app.jwt.cotacao-minutos` | `COTACAO_MINUTOS` | `30` | Validade do preço garantido na sessão de montagem |
| `app.cors.origens` | `CORS_ORIGENS` | `http://localhost:5173` | Origens permitidas (lista separada por vírgula) |
| `app.cloudinary.url` **ou** `.cloud-name` + `.api-key` + `.api-secret` | `CLOUDINARY_URL` ou `CLOUDINARY_CLOUD_NAME`/`_API_KEY`/`_API_SECRET` | vazio | Credenciais do Cloudinary (veja [Imagens](#imagens-cloudinary)). `app.cloudinary.folder` / `CLOUDINARY_FOLDER` define a pasta (padrão `vila-gourmet`) |
| `app.cep.base-url` | `CEP_BASE_URL` | `https://viacep.com.br/ws` | Serviço de consulta de CEP (ViaCEP, sem chave) |
| `app.ibge.base-url` | `IBGE_BASE_URL` | `https://servicodados.ibge.gov.br/api/v1/localidades` | Municípios (API de localidades do IBGE) |
| `app.bairros.overpass-urls` | `OVERPASS_URLS` | `overpass-api.de` e `overpass.kumi.systems` | Servidores Overpass (OpenStreetMap) tentados em ordem |
| — (frontend) | `VITE_API_URL` | `http://localhost:8080` | URL da API — veja `frontend/.env.example` |

## Imagens (Cloudinary)

As fotos de produtos e insumos ficam no **Cloudinary**; o banco guarda só a **URL**.

* **Fluxo:** no frontend, o botão **Adicionar foto** lê a imagem, reduz (até 1280 px, JPEG) e a mantém em **base64** na tela. Ao
  salvar o produto/insumo, o frontend envia o base64 junto com os demais dados; o **servidor** valida, envia ao Cloudinary
  (upload assinado) e grava a URL devolvida. O `api_secret` nunca vai para o navegador.
* **Validação no servidor:** o tipo é identificado pelos primeiros bytes do arquivo (PNG, JPEG, WEBP ou GIF; não confia no prefixo
  `data:` enviado), com limite de 5 MB já decodificado. Erros: `413` (grande demais), `415` (formato inválido), `502` (falha do
  Cloudinary) e `503` (credenciais não configuradas).
* **API:** `ProdutoRequest` e `InsumoRequest` aceitam `imagemBase64` (substitui a foto); sem ele, `imagemUrl` é mantida (vazia remove a
  foto). Há também o envio avulso `POST /api/gestao/imagens?pasta=produtos|insumos` (permissão `CATALOGO_GERENCIAR`), que devolve `{ url, publicId }`.
* **Configuração:** copie `backend/application-local.properties.example` para `backend/application-local.properties` e preencha `app.cloudinary.url` (painel do Cloudinary → *API Keys*, no formato `cloudinary://chave:segredo@conta`). Sem credenciais o sistema funciona normalmente, mas o envio de fotos responde `503` com uma mensagem clara.
* **Limitação:** ao trocar ou remover uma foto, o arquivo antigo permanece no Cloudinary (não é apagado).

## Testes

```bash
# Unidade (precificação, validação de configuração, CPF) — 19 testes
cd backend && mvn test
# ...ou sem Java instalado:
docker run --rm -v "$PWD/backend:/app" -w /app maven:3.9-eclipse-temurin-21 mvn -B test

# Ponta a ponta contra a API real (banco VAZIO): cobre dezenas de verificações
python scripts/smoke_test.py
```

O exemplo de referência da documentação (preço-base R$ 35, opções R$ 20 e R$ 24, adicional separado R$ 6) é caso de
aceitação automatizado em `PrecificacaoServiceTest`: Soma 44/79, Média 22/26,33, Maior valor 24/35 (+ R$ 6 do adicional).

## Visão geral da API

Erros seguem `{ timestamp, status, mensagem, detalhes[] }`. Violações de regra de negócio retornam **422**.

| Área | Rotas | Acesso |
|---|---|---|
| Autenticação | `POST /api/auth/cliente/cadastro`, `/cliente/login` (campo `login` com e-mail **ou CPF**; `email` ainda aceito), `/funcionario/login`; primeiro acesso: `GET /funcionario/configuracao-inicial`, `POST /funcionario/primeiro-acesso` (só enquanto não há funcionários) | público |
| Catálogo e cotação | `GET /api/public/catalogo`, `POST /api/public/cotacoes`, `GET /api/public/entrega/area` | público |
| Pedidos | `POST /api/public/pedidos` (retirada: visitante; entrega: cliente logado; header `Idempotency-Key`) | público/cliente |
| Rastreamento | `POST /api/public/rastreamento`, `POST /api/public/pedidos/cancelamento` | público (limitado por IP) |
| Mesa | `POST /api/public/mesas`, `POST /api/public/mesas/{codigo}/pedidos` (código + CPF do responsável) | público |
| Cliente | `/api/cliente/**`: `me`, `enderecos` (CRUD + padrão), `pizzas` salvas, `pedidos`, cancelamento, novo código | papel CLIENTE |
| Catálogo (gestão) | `/api/gestao/{categorias,insumos,produtos,grupos,opcoes,adicionais}`, `…/validacao`, `…/publicar` | `CATALOGO_GERENCIAR` |
| Pedidos (gestão) | `/api/gestao/pedidos`, `…/estado`, `…/concluir-com-codigo`, `…/concluir-manual`, `…/cancelar`, `…/pagamento` | `PEDIDO_VER`, `PEDIDO_GERENCIAR`, `CANCELAMENTO_DECIDIR`, `PAGAMENTO_CONFIRMAR` |
| Cancelamentos | `/api/gestao/cancelamentos`, `…/{id}/decisao` | `CANCELAMENTO_DECIDIR` |
| Mesas (gestão) | `/api/gestao/mesas`, `…/{codigo}/pedidos`, `…/fechamento`, `…/fechamentos/{id}/pagamento` | `MESA_GERENCIAR` |
| Entrega (config) | `/api/gestao/entrega/{taxa,areas}` | `CONFIG_GERENCIAR` |
| Equipe | `/api/gestao/{funcionarios,perfis,permissoes}` | `FUNCIONARIO_GERENCIAR`, `PERFIL_GERENCIAR` |
| Relatórios | `GET /api/gestao/relatorios/operacional?inicio&fim` | `RELATORIO_VER` |

## Regras de negócio implementadas

* **Precificação (servidor, `BigDecimal`, arredondamento HALF_UP em 2 casas):** estratégias Fixo, Soma, Média (simples,
  sem ponderação) e Maior valor; preço-base participa conforme configuração; grupos *principal*, *cobrado separadamente*
  ou *gratuito*; opção de valor zero participa normalmente; seleções repetidas dentro dos limites.
* **Limites:** mínimo/máximo do grupo e da opção valem simultaneamente. Grupo com mínimo > 0 é obrigatório; com mínimo 0
  é opcional e, se não usado, os mínimos das suas opções não se aplicam.
* **Publicação validada:** produto composto só é publicado se for possível montá-lo e calcular um preço principal válido
  (mínimo do grupo > soma dos máximos das opções, soma dos mínimos > máximo do grupo etc. bloqueiam, **explicando cada
  conflito**). Editar um produto publicado de modo que o invalide o devolve a rascunho.
* **Cotação garantida na sessão:** `POST /cotacoes` devolve um token assinado com o preço; mudanças posteriores de preço não
  alteram a cotação até expirar. Nada é persistido antes da confirmação. Na confirmação revalida-se a disponibilidade.
* **Snapshot histórico:** ao confirmar, itens, escolhas, adicionais, nomes, preços, estratégia, modalidade de cobrança,
  endereço e totais são gravados no pedido; mudanças no catálogo não os alteram.
* **Pedidos:** entrega só para cliente cadastrado, com endereço salvo e dentro da área atendida, com taxa fixa; retirada
  com nome e CPF obrigatórios; mesa em conta com vários pedidos. Idempotência via `Idempotency-Key`.
* **Estados:** só transições previstas por modalidade; toda mudança grava histórico (quem, quando, justificativa).
  Entrega: aguardando → confirmado → em preparação → a caminho → concluído. Retirada: … → pronto para retirada → concluído.
  Mesa: … → servido → concluído.
* **Conclusão:** com código do cliente, ou **manual com justificativa obrigatória**, auditada e distinguível no histórico.
* **Cancelamento:** solicitar ≠ cancelar. Antes da confirmação do restaurante cancela na hora; depois fica **pendente** até a
  decisão do restaurante (rejeição exige justificativa).
* **Pagamentos:** dinheiro, cartão e PIX, sempre **confirmados manualmente** pelo funcionário; estado de pagamento separado
  do estado do pedido. Entrega/retirada pagam o pedido; mesa paga o **fechamento** da conta.
* **Mesa:** código próprio da conta; CPF identifica o responsável (não é chave, e não precisa ser de um cliente cadastrado); a conta fecha só com todos os pedidos
  servidos e, ao pagar, é encerrada e seus pedidos concluídos.
* **Permissões:** perfis × permissões verificados **no servidor** (`@PreAuthorize`) a cada requisição.
* **Relatórios:** quantidade, distribuição por estado e modalidade, total vendido e total recebido (separados).

## Decisões tomadas sobre as pendências da documentação

A documentação (§14) manda não presumir regras. Para entregar um protótipo completo foi preciso **escolher um
comportamento para cada pendência**. São decisões **provisórias, fáceis de alterar** e que devem ser validadas com o negócio:

| Pendência | Decisão adotada no protótipo |
|---|---|
| Unicidade do CPF | O CPF é **único por conta de cliente** (validado pelos dígitos e com chave única no banco, migração V5): um segundo cadastro com o mesmo CPF recebe 409. Vale só para contas de cliente: visitantes (retirada, mesa) continuam informando CPF sem cadastro. | Pedidos e contas de mesa lançados pelo caixa podem não ter CPF.
| Login do cliente por CPF | Aceita e-mail **ou CPF** + senha; como o CPF é único, identifica uma só conta. |
| Exclusão × desativação | **Desativação lógica** (`ativo`/`disponivel`/`ativa`); não há exclusão física via API. Endereços usados em pedidos permanecem no snapshot. |
| Pagamento parcial/múltiplo, troco, cartão | **Sem parciais**: um pagamento confirmado por destino (garantido por índice único). Dinheiro com valor recebido calcula **troco** informativo. Cartão sem detalhes adicionais. |
| Reabertura de conta de mesa | Conta **encerrada não reabre**. Um fechamento *cancelado* devolve a conta a `ABERTA`. |
| Cancelados nos relatórios | Contam na quantidade e nas distribuições; **não** entram no total vendido. Total vendido (por pedido) e total recebido (por pagamento) são indicadores separados. |
| Cancelamento após o preparo | Segue a aprovação do restaurante. Pedido com **pagamento confirmado não pode ser cancelado** (estorno fora do escopo). |
| Códigos (geração, expiração, tentativas) | Acompanhamento: 24 bytes aleatórios. Confirmação: 6 dígitos, válido por 48 h, **uso único**, máx. 5 tentativas, regenerável pelo cliente. Rastreamento/mesa limitados a 10 falhas/15 min por IP. |
| Fim da sessão de cotação | Token de cotação com validade (30 min por padrão), sem estado no servidor. |
| `incluir_preco_base` | Renomeado para `participa_preco_base`; em **Fixo** o preço-base é sempre o preço principal (campo ignorado). O `CHECK` que misturava as duas ideias foi removido. |
| SQL em MySQL real | Migrações aplicadas e a API exercitada contra **MySQL 8.4**. |
| Opção × insumo | **Decisão do negócio (altera a documentação §3):** a opção de um grupo **é um insumo cadastrado**. Ao montar um grupo escolhe-se o insumo (ex.: calabresa); o nome vem dele, e o preço, os limites e a disponibilidade são da opção. Renomear o insumo renomeia suas opções; insumo indisponível desativa as opções que o usam. O mesmo insumo não repete no grupo. |
| Estados do pedido de mesa (lacuna do doc) | aguardando → confirmado → em preparação → servido → concluído. |
| Quem lança pedido na mesa (lacuna do doc) | O **responsável** (código da conta + CPF) **ou** o funcionário (só o código). |
| Valor de grupo "cobrado separadamente" | Soma `preço × quantidade` das opções selecionadas do grupo. |
| Soma com "valores suficientes" | Estratégias não-Fixo exigem ao menos um grupo *principal* com mínimo ≥ 1 (validado na publicação) e ao menos uma seleção nele. |
| JWT: armazenamento/renovação | Token de 120 min, sem *refresh*; o front guarda em `sessionStorage`. Estratégia definitiva pendente. |

## Correções e melhorias sobre o modelo SQL v0.1

Aplicadas em `backend/src/main/resources/db/migration/V1__esquema_inicial.sql`:

* Removidos `CREATE DATABASE`/`USE` (a migração roda no schema configurado).
* **Faltavam**: áreas de entrega e taxa (`area_entrega`, `configuracao_loja`), pizzas salvas, status de publicação do produto,
  histórico da conta de mesa, idempotência do pedido, `insumo_id` opcional na opção.
* **Snapshots completos:** modalidade de cobrança e participação do preço-base no histórico do item.
* **Nome/CPF do responsável em todo pedido** (rastreamento por número + CPF também para entrega).
* **Conclusão manual** explícita (`conclusao_manual` + `CHECK` exigindo funcionário e justificativa).
* **Solicitação de cancelamento** identifica quem pediu (cliente, visitante ou funcionário).
* **Códigos:** `CHAR(64)` com **HMAC-SHA256 determinístico** (o hash salgado do v0.1 tornava a busca impossível), sem
  unicidade global no código curto, com `tentativas`, `expira_em` e `utilizado_em`.
* **Unicidade parcial** por colunas geradas: um endereço padrão por cliente, um pagamento confirmado por destino, um
  fechamento pendente por conta, uma solicitação de cancelamento pendente por pedido, um código de confirmação ativo por pedido.
* `CHECK`s novos: estados válidos do pedido, `total = subtotal + taxa`, pagamento confirmado exige responsável e data,
  produto simples exige preço.
* `ON DELETE RESTRICT` por padrão; `SET NULL` apenas em referências históricas.

## Segurança

* Senhas somente como **hash BCrypt**; comparação em tempo constante e BCrypt executado mesmo para e-mail inexistente.
* **JWT** validado em toda requisição protegida; o token carrega apenas id e tipo (sem senha nem dados pessoais).
  Perfis/permissões são lidos do banco **a cada requisição**: revogar uma permissão vale imediatamente.
* Autorização no servidor (`@PreAuthorize` + papéis por prefixo de rota); a UI só esconde o que não pode ser usado.
* Rastreamento sem CPF completo, endereço ou código de confirmação; CPF mascarado nas respostas.
* Valores monetários, totais e transições sempre calculados/validados pelo servidor.
* Contas de cliente e funcionário são separadas e **não compartilham e-mail**.
* Em produção: troque `JWT_SECRET`, use HTTPS, restrinja `CORS_ORIGENS` e, atrás de proxy, configure
  `server.forward-headers-strategy` para que o limitador por IP enxergue o IP real.

## Limitações conhecidas e próximos passos

* **Frontend do cliente/visitante** (catálogo, montagem, carrinho, rastreamento, mesa) — a API já existe.
* CPF é armazenado em texto puro (necessário para rastreamento); avaliar criptografia/minimização (LGPD).
* Limitador de tentativas em memória (uma instância); usar armazenamento compartilhado ao escalar.
* Sem renovação de token (*refresh*), sem recuperação de senha, sem confirmação de e-mail.
* Sem testes automatizados de integração com banco (o `smoke_test.py` cobre o fluxo ponta a ponta manualmente).
* Imagens de produto/insumo por URL (sem upload).
* Pagamento parcial, estorno e integração de PIX/cartão estão fora do escopo da V1.

---

## Cidades e bairros da área de entrega

Em **Gestão → Entrega → Adicionar cidade**, a equipe pesquisa um município (lista oficial do IBGE, com nome e UF),
confirma, e o servidor importa os bairros dele para a área de entrega. Depois, cada bairro é ativado/desativado
individualmente na lista (agrupada por cidade). Se não for possível obter os bairros, **nada é importado** e a tela
oferece o cadastro manual dos bairros daquela cidade.

**De onde vêm os dados (verificado em out/2026):**

| Fonte | Uso | Cobertura real |
|---|---|---|
| IBGE — API de localidades | Municípios (5.571 com UF e código) | Completa. **Não publica bairros**: `/distritos` devolve só distritos oficiais (96 em São Paulo, 6 em Piracicaba, 1 a 7 em cidades pequenas) e `/subdistritos` veio vazio em todos os testes. |
| **OpenStreetMap (Overpass API)** — *adotada* | Bairros: lugares `suburb`, `neighbourhood` e `quarter` dentro da fronteira do município, localizada pela etiqueta `IBGE:GEOCODIGO` | Boa em capitais e cidades médias (São Paulo ≈ 600 nomes, Piracicaba ≈ 370, Campinas ≈ 115, Santos ≈ 60); em municípios pequenos pode ser **vazia** (Alta Floresta D'Oeste/RO: 0). |
| ViaCEP | Já usado no formulário de endereço | Só devolve o bairro de **um** CEP/rua; não lista bairros por cidade. |

**Limitações a conhecer**

* O OSM é mapeamento colaborativo: a lista pode estar **incompleta** ou conter nomes que não são bairros no sentido
  administrativo (loteamentos, vilas, bairros rurais). Por isso a revisão é obrigatória e o cadastro manual continua disponível.
* Os bairros importados entram **ativos** (a documentação pede "adicionar à área de entrega" e depois revisar); desative os que não atendem.
* O nome do bairro do cliente (vindo do CEP) pode não coincidir com o nome do OSM (ex.: "Jd. X" × "Jardim X"); nesse caso o
  endereço é barrado e a equipe pode cadastrar o nome usado pelo CEP manualmente.
* A consulta ao Overpass é lenta (cerca de 25–60 s por cidade) e o serviço público tem limites de uso; o servidor tenta um
  segundo servidor se o primeiro falhar. Para uso intenso, aponte `OVERPASS_URLS` para uma instância própria.
* Os dados do OSM exigem atribuição (© colaboradores do OpenStreetMap, ODbL); ela aparece na tela.
* Importar de novo a mesma cidade **não duplica nem altera** o que já existe (comparação sem acentos e sem diferenciar
  maiúsculas): só entram bairros novos. Cadastros manuais antigos da mesma cidade são ligados ao município.
* Cidades de mesmo nome em UFs diferentes (ex.: Santa Luzia) são tratadas por código IBGE; a consulta de entrega do cliente
  usa a UF do endereço. Áreas antigas sem UF continuam valendo para qualquer UF.

---

## Caixa (frente de caixa) e histórico

* **A pizzaria só vende com o caixa aberto.** Com o caixa fechado, a loja do cliente mostra o aviso "Estamos fechados" e o
  servidor recusa (409) pedidos de entrega/retirada, abertura de conta de mesa e pedidos de mesa, inclusive os lançados pela equipe.
  O cardápio continua visível. Só existe um caixa aberto por vez (garantido por chave única no banco, migração V6).
* **Gestão → Frente de caixa** (permissão `CAIXA_OPERAR`, já dada a ADMINISTRADOR e ATENDENTE): abre/fecha o caixa e lança pedidos de
  **retirada**, **entrega** (endereço digitado, com CEP preenchendo rua, bairro e cidade; só vale dentro da área atendida, com a taxa)
  e **mesa** (conta aberta ou nova). **Pelo caixa basta o nome do cliente**: sem cadastro e sem CPF (opcional; se informado, é validado).
  A exigência de cliente cadastrado (e de CPF) vale só para pedidos feitos pela loja do cliente (migração V7). A montagem do produto é a mesma da loja, com o preço calculado pelo servidor.
* **Fechar o caixa:** se houver pedidos não concluídos, a tela avisa que **serão cancelados** e só fecha com a confirmação
  (`POST /api/gestao/caixa/fechar` com `confirmarCancelamentos`). Contas de mesa abertas são encerradas sem cobrança.
  Pedidos que o sistema não cancela sozinho **impedem o fechamento** e são listados: pagamento já confirmado (estorno está fora do
  escopo) e pedidos de contas de mesa em fechamento.
* **Pedidos e Entrega** mostram só pedidos **em andamento**. O **Histórico** mostra concluídos e cancelados, com filtro por dia
  (De/Até, atalhos Hoje, Ontem e Últimos 7 dias), situação e modalidade. O dia é o da **criação** do pedido.
* API: `GET /api/gestao/pedidos?situacao=ANDAMENTO|FINALIZADOS&de=AAAA-MM-DD&ate=AAAA-MM-DD`; `GET /api/public/loja/status`.
* O painel busca as permissões atuais do funcionário ao abrir (`GET /api/gestao/eu`): novas permissões aparecem no menu sem sair e entrar.
* Conta de mesa aberta pelo caixa sem CPF só recebe pedidos pelo próprio caixa; o cliente na loja entra na conta pelo código + CPF.

