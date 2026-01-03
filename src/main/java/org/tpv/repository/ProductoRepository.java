package org.tpv.repository;

import org.tpv.domain.Producto;

import java.util.Optional;

public class ProductoRepository {

    public Optional<Producto> findByCodigo(String codigoBarra);
    public void save(Producto producto);

}
