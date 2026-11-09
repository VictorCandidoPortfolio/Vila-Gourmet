package com.vilagourmet.service;

import com.vilagourmet.dto.GestaoDTO.ImportacaoCidadeResponse;
import com.vilagourmet.dto.GestaoDTO.MunicipioResponse;
import com.vilagourmet.exception.NegocioException;
import com.vilagourmet.model.AreaEntrega;
import com.vilagourmet.repository.AreaEntregaRepository;
import com.vilagourmet.util.Textos;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Importa os bairros de um município (código IBGE) para a área de entrega. A busca na fonte externa (lenta) acontece
 * fora de qualquer transação; só a gravação é transacional. Repetir a importação não duplica nada: bairros que já
 * existem na cidade (comparados sem acentos e sem diferenciar maiúsculas) são mantidos como estão.
 */
@Service
public class CidadeEntregaService {

    private final IbgeService ibge;
    private final BairrosOsmService bairros;
    private final AreaEntregaRepository areas;
    private final TransactionTemplate tx;
    private final Set<Integer> emImportacao = ConcurrentHashMap.newKeySet();

    public CidadeEntregaService(IbgeService ibge, BairrosOsmService bairros, AreaEntregaRepository areas, TransactionTemplate tx) {
        this.ibge = ibge;
        this.bairros = bairros;
        this.areas = areas;
        this.tx = tx;
    }

    public List<MunicipioResponse> pesquisar(String busca) {
        return ibge.pesquisar(busca);
    }

    public ImportacaoCidadeResponse importar(int ibgeId) {
        MunicipioResponse municipio = ibge.buscarPorId(ibgeId); // o código vem do cliente: valida e usa o nome oficial
        if (!emImportacao.add(ibgeId)) {
            throw new NegocioException(HttpStatus.CONFLICT, "Já existe uma importação em andamento para " + municipio.nome() + "/" + municipio.uf());
        }
        try {
            BairrosOsmService.Resultado r = bairros.buscar(ibgeId);
            if (r.nomes().isEmpty()) {
                throw new NegocioException("Não encontramos bairros de " + municipio.nome() + "/" + municipio.uf()
                        + " no OpenStreetMap. Nenhum bairro foi importado: cadastre os bairros manualmente.");
            }
            return tx.execute(status -> gravar(municipio, r));
        } catch (DataIntegrityViolationException e) {
            throw new NegocioException(HttpStatus.CONFLICT, "Os bairros foram alterados durante a importação. Tente novamente.");
        } finally {
            emImportacao.remove(ibgeId);
        }
    }

    private ImportacaoCidadeResponse gravar(MunicipioResponse m, BairrosOsmService.Resultado r) {
        List<AreaEntrega> existentes = areas.findByCidadeIgnoreCase(m.nome()).stream()
                .filter(a -> Objects.equals(a.getCidadeIbgeId(), m.ibgeId())
                        || (a.getCidadeIbgeId() == null && (a.getUf() == null || a.getUf().equalsIgnoreCase(m.uf()))))
                .toList();
        Plano plano = planejar(existentes, r.nomes());

        plano.paraVincular().forEach(a -> { // cadastro manual antigo da mesma cidade passa a apontar para o município
            a.setCidadeIbgeId(m.ibgeId());
            a.setUf(m.uf());
        });
        List<AreaEntrega> novas = plano.paraCriar().stream().map(nome -> {
            AreaEntrega a = new AreaEntrega();
            a.setBairro(nome);
            a.setCidade(m.nome());
            a.setUf(m.uf());
            a.setCidadeIbgeId(m.ibgeId());
            a.setOrigem(AreaEntrega.ORIGEM_IMPORTADA);
            a.setAtiva(true);
            return a;
        }).toList();
        areas.saveAll(novas);
        areas.flush();
        return new ImportacaoCidadeResponse(m.ibgeId(), m.nome(), m.uf(), r.nomes().size(), novas.size(),
                r.nomes().size() - novas.size(), r.ignorados(), BairrosOsmService.FONTE);
    }

    record Plano(List<String> paraCriar, List<AreaEntrega> paraVincular) {
    }

    /** Separa o que é novo do que já existe na cidade e quais cadastros manuais precisam ser ligados ao município. */
    static Plano planejar(List<AreaEntrega> existentes, List<String> nomesDaFonte) {
        Set<String> conhecidos = new HashSet<>();
        List<AreaEntrega> vincular = new ArrayList<>();
        for (AreaEntrega a : existentes) {
            conhecidos.add(Textos.normalizar(a.getBairro()));
            if (a.getCidadeIbgeId() == null) {
                vincular.add(a);
            }
        }
        List<String> criar = new ArrayList<>();
        for (String nome : nomesDaFonte) {
            if (conhecidos.add(Textos.normalizar(nome))) {
                criar.add(nome);
            }
        }
        return new Plano(criar, vincular);
    }
}
