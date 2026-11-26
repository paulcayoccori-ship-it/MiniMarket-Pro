package com.ejemplo.crudpersona.service;

import com.ejemplo.crudpersona.exception.NegocioException;
import com.ejemplo.crudpersona.model.UnidadMedida;
import com.ejemplo.crudpersona.repository.UnidadMedidaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Unidades de medida. "Unidad" y "Kilo" siempre existen; el usuario puede crear mas (Six pack, Caja...). */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UnidadMedidaService {

    private final UnidadMedidaRepository repository;

    public List<UnidadMedida> listar() {
        unidad();
        kilo();
        return repository.findAll(Sort.by("nombre"));
    }

    /** Unidad por defecto para productos sin unidad asignada. */
    public UnidadMedida unidad() {
        return repository.findFirstByAbreviaturaIgnoreCase(UnidadMedida.ABREV_UNIDAD).orElseGet(() ->
                repository.save(UnidadMedida.builder().nombre("Unidad").abreviatura(UnidadMedida.ABREV_UNIDAD)
                        .decimales(false).build()));
    }

    public UnidadMedida kilo() {
        return repository.findFirstByAbreviaturaIgnoreCase(UnidadMedida.ABREV_KILO).orElseGet(() ->
                repository.save(UnidadMedida.builder().nombre("Kilo").abreviatura(UnidadMedida.ABREV_KILO)
                        .decimales(true).build()));
    }

    public UnidadMedida crear(String nombre, String abreviatura, boolean decimales) {
        String n = nombre == null ? "" : nombre.trim();
        String a = abreviatura == null ? "" : abreviatura.trim().toLowerCase();
        if (n.isEmpty()) {
            throw new NegocioException("Escribe el nombre de la unidad (ej. Six pack).");
        }
        if (a.isEmpty()) {
            a = n.length() <= 6 ? n.toLowerCase() : n.substring(0, 4).toLowerCase();
        }
        if (n.length() > 40 || a.length() > 10) {
            throw new NegocioException("Nombre hasta 40 letras y abreviatura hasta 10.");
        }
        if (repository.findByNombreIgnoreCase(n).isPresent()) {
            throw new NegocioException("Ya existe la unidad \"" + n + "\".");
        }
        UnidadMedida nueva = repository.save(UnidadMedida.builder().nombre(n).abreviatura(a).decimales(decimales).build());
        log.info("Unidad creada: {} ({})", nueva.getNombre(), nueva.getAbreviatura());
        return nueva;
    }
}
