package org.tpv.service;

import org.tpv.domain.Cliente;
import org.tpv.repository.ClienteRepository;

import java.sql.SQLException;
import java.util.List;

public class ClienteService {

    private ClienteRepository clienteRepository;

    public ClienteService() {
        this.clienteRepository = new ClienteRepository();
    }

    /**
     * Obtiene todos los clientes
     */
    public List<Cliente> obtenerTodos() throws SQLException {
        return clienteRepository.findAll();
    }

    /**
     * Busca un cliente por DNI
     */
    public Cliente buscarPorDni(String dni) throws SQLException {
        if (dni == null || dni.trim().isEmpty()) {
            throw new IllegalArgumentException("El DNI no puede estar vacío");
        }
        return clienteRepository.findByDni(dni.trim());
    }

    /**
     * Busca clientes por nombre (búsqueda parcial)
     */
    public List<Cliente> buscarPorNombre(String nombre) throws SQLException {
        if (nombre == null || nombre.trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre no puede estar vacío");
        }
        return clienteRepository.findByNombre(nombre.trim());
    }

    /**
     * Crea un nuevo cliente
     */
    public Cliente crearCliente(String dni, String nombre, String telefono, String direccion, String email) throws SQLException {
        // Validaciones
        if (nombre == null || nombre.trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre es obligatorio");
        }

        // Si no hay DNI, generar uno automático
        if (dni == null || dni.trim().isEmpty()) {
            dni = "CLI-" + System.currentTimeMillis();
        } else {
            // Verificar que no exista ya
            Cliente existente = clienteRepository.findByDni(dni.trim());
            if (existente != null) {
                throw new IllegalStateException("Ya existe un cliente con el DNI: " + dni);
            }
        }

        Cliente cliente = new Cliente(
                null,
                dni.trim(),
                nombre.trim(),
                telefono != null ? telefono.trim() : "",
                direccion != null ? direccion.trim() : "",
                email != null ? email.trim() : ""
        );

        clienteRepository.save(cliente);
        return cliente;
    }

    /**
     * Actualiza un cliente existente
     */
    public void actualizarCliente(Cliente cliente) throws SQLException {
        if (cliente == null) {
            throw new IllegalArgumentException("El cliente no puede ser null");
        }
        if (cliente.getNombre() == null || cliente.getNombre().trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre es obligatorio");
        }
        clienteRepository.update(cliente);
    }

    /**
     * Elimina un cliente por DNI
     */
    public void eliminarCliente(String dni) throws SQLException {
        if (dni == null || dni.trim().isEmpty()) {
            throw new IllegalArgumentException("El DNI no puede estar vacío");
        }
        clienteRepository.delete(dni.trim());
    }

    /**
     * Busca clientes por DNI o nombre
     */
    public List<Cliente> buscar(String busqueda) throws SQLException {
        if (busqueda == null || busqueda.trim().isEmpty()) {
            return obtenerTodos();
        }

        List<Cliente> resultados = buscarPorNombre(busqueda);

        // Si no hay resultados por nombre, intentar por DNI
        if (resultados.isEmpty()) {
            Cliente porDni = buscarPorDni(busqueda);
            if (porDni != null) {
                resultados.add(porDni);
            }
        }

        return resultados;
    }
}