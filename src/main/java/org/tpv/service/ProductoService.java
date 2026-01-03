package org.tpv.service;

import org.tpv.domain.Producto;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

public class ProductoService {

    private final Map<String, Producto> productos = new HashMap<>();

    public Producto buscarPorCodigo(String codigoBarra) {
        return productos.get(codigoBarra);
    }

    public Producto crearProducto(String codigoBarra, String nombre, BigDecimal precioBase) {
        Producto producto = new Producto(null, codigoBarra, nombre, precioBase);
        productos.put(codigoBarra, producto);
        return producto;
    }
}
