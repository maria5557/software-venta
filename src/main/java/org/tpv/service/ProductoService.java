package org.tpv.service;

import org.tpv.domain.Producto;
import org.tpv.repository.ProductoRepository;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;

public class ProductoService {

    private ProductoRepository productoRepository;

    public ProductoService() {
        this.productoRepository = new ProductoRepository();
    }

    public Producto buscarPorCodigo(String codigo) throws SQLException {
        return productoRepository.findByCodigo(codigo);
    }

    public List<Producto> buscarPorNombre(String nombre) throws SQLException {
        return productoRepository.findByNombre(nombre);
    }

    public Producto crearProducto(String codigo, String nombre, BigDecimal precio) throws SQLException {
        Producto producto = new Producto(null, codigo, nombre, precio);
        productoRepository.save(producto);
        return producto;
    }

    public void actualizarProducto(Producto producto) throws SQLException {
        productoRepository.update(producto);
    }

    public void eliminarProducto(String codigoBarra) throws SQLException {
        productoRepository.delete(codigoBarra);
    }

    public List<Producto> obtenerTodos() throws SQLException {
        return productoRepository.findAll();
    }
}