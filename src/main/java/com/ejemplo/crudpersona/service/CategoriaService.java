package com.ejemplo.crudpersona.service;

import com.ejemplo.crudpersona.exception.NegocioException;
import com.ejemplo.crudpersona.model.Categoria;
import com.ejemplo.crudpersona.repository.CategoriaRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class CategoriaService {

    private static final int TAMANIO_PAGINA = 15;

    private final CategoriaRepository repository;

    public Page<Categoria> listar(String filtro, int pagina) {
        return listar(filtro, pagina, TAMANIO_PAGINA);
    }

    public Page<Categoria> listar(String filtro, int pagina, int tamanio) {
        Pageable pageable = PageRequest.of(pagina, tamanio, Sort.by("id").ascending());
        if (filtro == null || filtro.isBlank()) {
            return repository.findAll(pageable);
        }
        return repository.buscar(filtro.trim(), pageable);
    }

    @Transactional
    public Categoria guardar(@Valid Categoria categoria) {
        repository.findByCodigoCategoria(categoria.getCodigoCategoria()).ifPresent(existente -> {
            boolean esOtraCategoria = categoria.getId() == null
                    || !existente.getId().equals(categoria.getId());
            if (esOtraCategoria) {
                throw new NegocioException(
                        "Ya existe una categoria registrada con el codigo " + categoria.getCodigoCategoria());
            }
        });

        Categoria guardada = repository.save(categoria);
        log.info("Categoria guardada: id={} codigo={}", guardada.getId(), guardada.getCodigoCategoria());
        return guardada;
    }

    @Transactional
    public void eliminar(Long id) {
        if (!repository.existsById(id)) {
            throw new NegocioException("La categoria ya no existe en la base de datos.");
        }
        repository.deleteById(id);
    }

    public long contar() {
        return repository.count();
    }
}

