package org.tpv.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {

    // URL de conexión - esto creará un archivo "tpv.db" en la carpeta data
    private static final String URL = "jdbc:sqlite:data/tpv.db";

    public static Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(URL);
        // Habilitar claves foráneas en SQLite
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON");
        }
        return conn;
    }

    public static void inicializarBaseDatos() throws SQLException {
        // Crear la carpeta data si no existe
        new java.io.File("data").mkdirs();

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            // Crear tabla producto
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS producto (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    codigo_barra TEXT UNIQUE NOT NULL,
                    nombre TEXT NOT NULL,
                    precio_base REAL NOT NULL
                )
            """);

            // Crear tabla factura
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS factura (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    numero_factura TEXT,
                    fecha TEXT NOT NULL,
                    total_sin_iva REAL,
                    total_iva REAL,
                    total_con_iva REAL
                )
            """);

            // Crear tabla linea_factura
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS linea_factura (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    factura_id INTEGER NOT NULL,
                    producto_id INTEGER,
                    codigo_producto TEXT,
                    nombre_producto TEXT,
                    precio_unitario REAL,
                    cantidad INTEGER,
                    iva_aplicado INTEGER,
                    FOREIGN KEY (factura_id) REFERENCES factura(id)
                )
            """);

            // Crear tabla configuracion
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS configuracion (
                    id INTEGER PRIMARY KEY CHECK (id = 1),
                    iva_general INTEGER DEFAULT 21,
                    nombre_tienda TEXT,
                    direccion TEXT,
                    ciudad TEXT,
                    codigo_postal TEXT,
                    telefono TEXT,
                    cif TEXT ,
                    email TEXT 
                )
            """);

            // Insertar configuración por defecto si no existe
            stmt.execute("""
                INSERT OR IGNORE INTO configuracion (id, iva_general, nombre_tienda, direccion, ciudad, codigo_postal, telefono, cif, email)
                VALUES (1, 21, 'ALMADENA TELEFONIA', 'AV GRANADA 5', 'JAÉN', '23003', '953 474 840', 'B12345678', 'almadenashop786@gmail.com')
            """);


            System.out.println("✓ Base de datos SQLite inicializada correctamente");
            System.out.println("✓ Archivo de base de datos: data/tpv.db");
        }
    }
}