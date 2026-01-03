package org.tpv.service;

import org.tpv.domain.Producto;
import org.tpv.repository.ProductoRepository;

import java.math.BigDecimal;
import java.sql.SQLException;


public class ProductoService {

    private final ProductoRepository repository = new ProductoRepository();

    public Producto buscarPorCodigo(String codigoBarra) throws SQLException {
        return repository.findByCodigo(codigoBarra);
    }

    public Producto crearProducto(String codigoBarra, String nombre, BigDecimal precioBase) throws SQLException {
        Producto producto = new Producto(null, codigoBarra, nombre, precioBase);
        repository.save(producto);
        return producto;
    }
}
