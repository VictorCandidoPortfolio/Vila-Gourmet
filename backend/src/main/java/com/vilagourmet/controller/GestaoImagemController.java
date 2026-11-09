package com.vilagourmet.controller;

import com.vilagourmet.dto.CatalogoDTO.ImagemRequest;
import com.vilagourmet.dto.CatalogoDTO.ImagemResponse;
import com.vilagourmet.service.ImagemService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Envio avulso de imagem (base64) ao Cloudinary; devolve a URL. Os cadastros de produto e insumo também aceitam base64 direto. */
@RestController
@RequestMapping("/api/gestao/imagens")
@PreAuthorize("hasAuthority('CATALOGO_GERENCIAR')")
public class GestaoImagemController {

    private final ImagemService imagens;

    public GestaoImagemController(ImagemService imagens) {
        this.imagens = imagens;
    }

    @PostMapping
    public ImagemResponse enviar(@Valid @RequestBody ImagemRequest req, @RequestParam(defaultValue = "produtos") String pasta) {
        return imagens.enviar(req.imagemBase64(), pasta);
    }
}
