package com.vilagourmet.service;

import com.vilagourmet.dto.GestaoDTO.AreaConsultaResponse;
import com.vilagourmet.dto.GestaoDTO.AreaEntregaRequest;
import com.vilagourmet.dto.GestaoDTO.AreaEntregaResponse;
import com.vilagourmet.dto.GestaoDTO.MunicipioResponse;
import com.vilagourmet.dto.GestaoDTO.TaxaEntregaResponse;
import com.vilagourmet.exception.NaoEncontradoException;
import com.vilagourmet.exception.NegocioException;
import com.vilagourmet.model.AreaEntrega;
import com.vilagourmet.model.ConfiguracaoLoja;
import com.vilagourmet.repository.AreaEntregaRepository;
import com.vilagourmet.repository.ConfiguracaoLojaRepository;
import com.vilagourmet.util.Dinheiro;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Taxa de entrega fixa e áreas de entrega (bairro + cidade) configuradas pela pizzaria. */
@Service
public class ConfiguracaoService {

    private final ConfiguracaoLojaRepository configuracoes;
    private final AreaEntregaRepository areas;
    private final IbgeService ibge;

    public ConfiguracaoService(ConfiguracaoLojaRepository configuracoes, AreaEntregaRepository areas, IbgeService ibge) {
        this.configuracoes = configuracoes;
        this.areas = areas;
        this.ibge = ibge;
    }

    @Transactional(readOnly = true)
    public BigDecimal taxaEntrega() {
        return configuracoes.findById(ConfiguracaoLoja.TAXA_ENTREGA)
                .map(c -> new BigDecimal(c.getValor())).orElse(Dinheiro.ZERO);
    }

    @Transactional
    public TaxaEntregaResponse definirTaxa(BigDecimal taxa) {
        BigDecimal valor = Dinheiro.arredondar(taxa);
        configuracoes.save(new ConfiguracaoLoja(ConfiguracaoLoja.TAXA_ENTREGA, valor.toPlainString()));
        return new TaxaEntregaResponse(valor);
    }

    @Transactional(readOnly = true)
    public List<AreaEntregaResponse> listarAreas() {
        return areas.findAllByOrderByCidadeAscBairroAsc().stream().map(this::paraResponse).toList();
    }

    @Transactional
    public AreaEntregaResponse criarArea(AreaEntregaRequest req) {
        String bairro = req.bairro().trim();
        String cidade = req.cidade().trim();
        AreaEntrega a = new AreaEntrega();
        if (req.cidadeIbgeId() != null) { // cidade e UF oficiais do IBGE, não o que veio do cliente
            MunicipioResponse m = ibge.buscarPorId(req.cidadeIbgeId());
            cidade = m.nome();
            a.setCidadeIbgeId(m.ibgeId());
            a.setUf(m.uf());
        }
        if (areas.findByBairroIgnoreCaseAndCidadeIgnoreCaseAndCidadeIbgeId(bairro, cidade, a.getCidadeIbgeId()).isPresent()) {
            throw new NegocioException("Essa área de entrega já está cadastrada");
        }
        a.setBairro(bairro);
        a.setCidade(cidade);
        a.setAtiva(req.ativa() == null || req.ativa());
        return paraResponse(areas.save(a));
    }

    @Transactional
    public AreaEntregaResponse atualizarArea(Long id, AreaEntregaRequest req) {
        AreaEntrega a = areas.findById(id).orElseThrow(() -> new NaoEncontradoException("Área de entrega não encontrada"));
        String bairro = req.bairro().trim();
        String cidade = req.cidade().trim();
        areas.findByBairroIgnoreCaseAndCidadeIgnoreCaseAndCidadeIbgeId(bairro, cidade, a.getCidadeIbgeId()).filter(o -> !o.getId().equals(id)).ifPresent(o -> {
            throw new NegocioException("Essa área de entrega já está cadastrada");
        });
        a.setBairro(bairro);
        a.setCidade(cidade);
        if (req.ativa() != null) {
            a.setAtiva(req.ativa());
        }
        return paraResponse(a);
    }

    @Transactional(readOnly = true)
    public boolean areaAtendida(String bairro, String cidade, String uf) {
        return bairro != null && cidade != null
                && areas.atendida(bairro.trim(), cidade.trim(), uf == null || uf.isBlank() ? null : uf.trim());
    }

    @Transactional(readOnly = true)
    public AreaConsultaResponse consultar(String bairro, String cidade, String uf) {
        boolean atendida = areaAtendida(bairro, cidade, uf);
        return new AreaConsultaResponse(atendida, taxaEntrega());
    }

    private AreaEntregaResponse paraResponse(AreaEntrega a) {
        return new AreaEntregaResponse(a.getId(), a.getBairro(), a.getCidade(), a.isAtiva(), a.getCidadeIbgeId(), a.getUf(), a.getOrigem());
    }
}
