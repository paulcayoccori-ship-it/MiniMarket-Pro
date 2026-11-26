package com.ejemplo.crudpersona.seeder;

import com.ejemplo.crudpersona.model.Proveedor;
import com.ejemplo.crudpersona.repository.ProveedorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Registra 10 proveedores mayoristas. Idempotente: omite los RUC que ya existen. */
@Component
@RequiredArgsConstructor
public class ProveedorSeeder {

    static final List<Proveedor> PROVEEDORES = List.of(
            proveedor("20100055237", "ALICORP S.A.A.", "Av. Argentina 4793, Callao", "013150800"),
            proveedor("20100190797", "GLORIA S.A.", "Av. Republica de Panama 2461, La Victoria", "014707170"),
            proveedor("20100113610", "UNION DE CERVECERIAS PERUANAS BACKUS Y JOHNSTON S.A.A.", "Av. Nicolas Ayllon 3986, Ate", "013113000"),
            proveedor("20331061655", "DISTRIBUIDORA MAYORISTA DEL SUR S.A.C.", "Jr. Huallaga 520, Cercado de Lima", "014261122"),
            proveedor("20512345671", "COMERCIAL LIMPIEZA TOTAL E.I.R.L.", "Av. Colonial 1880, Lima", "015647788"),
            proveedor("20601987654", "TECNOIMPORT PERU S.A.C.", "Av. Petit Thouars 5356, Miraflores", "017159900"),
            proveedor("20487654321", "HOGAR Y FERRETERIA ANDINA S.R.L.", "Av. Tupac Amaru 3450, Comas", "015372200"),
            proveedor("20554433221", "ELECTRODOMESTICOS DEL PACIFICO S.A.", "Av. Javier Prado Este 2465, San Borja", "012245566"),
            proveedor("20298765432", "PAPELERA Y UTILES LIMA S.A.C.", "Jr. Cotabambas 360, Cercado de Lima", "014283344"),
            proveedor("20611122233", "PET FOOD DISTRIBUCIONES S.A.C.", "Av. La Molina 1120, La Molina", "013489900"));

    private final ProveedorRepository repository;

    @Transactional
    public int sembrar() {
        Set<String> existentes = repository.findAll().stream().map(Proveedor::getRuc).collect(Collectors.toSet());
        List<Proveedor> nuevos = new ArrayList<>();
        for (Proveedor p : PROVEEDORES) {
            if (!existentes.contains(p.getRuc())) {
                nuevos.add(Proveedor.builder().ruc(p.getRuc()).razonSocial(p.getRazonSocial())
                        .direccion(p.getDireccion()).telefono(p.getTelefono()).build());
            }
        }
        repository.saveAll(nuevos);
        return nuevos.size();
    }

    private static Proveedor proveedor(String ruc, String razonSocial, String direccion, String telefono) {
        return Proveedor.builder().ruc(ruc).razonSocial(razonSocial).direccion(direccion).telefono(telefono).build();
    }
}
