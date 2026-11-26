package com.ejemplo.crudpersona.service;

import com.ejemplo.crudpersona.model.Proveedor;
import com.ejemplo.crudpersona.repository.ProveedorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProveedorService {

    private static final int LIMITE_SUGERENCIAS = 8;

    private final ProveedorRepository repository;

    /** Por RUC (prefijo) si el texto es numerico; si no, cada palabra debe estar en la razon social. */
    public List<Proveedor> autocompletar(String texto) {
        if (texto == null || texto.isBlank()) {
            return List.of();
        }
        String t = texto.trim();
        Specification<Proveedor> spec;
        if (t.matches("\\d+")) {
            spec = (raiz, q, cb) -> cb.like(raiz.get("ruc"), t + "%");
        } else {
            spec = Specification.where(null);
            for (String palabra : t.toLowerCase().split("\\s+")) {
                String patron = "%" + palabra + "%";
                spec = spec.and((raiz, q, cb) -> cb.like(cb.lower(raiz.get("razonSocial")), patron));
            }
        }
        return repository.findAll(spec, PageRequest.of(0, LIMITE_SUGERENCIAS, Sort.by("razonSocial"))).getContent();
    }

    public Optional<Proveedor> buscarPorRuc(String ruc) {
        return ruc == null ? Optional.empty() : repository.findByRuc(ruc.trim());
    }
}
