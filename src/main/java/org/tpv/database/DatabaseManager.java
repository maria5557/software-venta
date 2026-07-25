package org.tpv.database;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {

    // 1. Definimos la carpeta y la ruta completa de forma limpia
    private static final String CARPETA_APP = System.getProperty("user.home")
            + File.separator + "AppData"
            + File.separator + "Local"
            + File.separator + "MiTPV";

    private static final String ARCHIVO_DB = CARPETA_APP + File.separator + "tpv.db";
    private static final String URL = "jdbc:sqlite:" + ARCHIVO_DB;

    public static Connection getConnection() throws SQLException {

        // 2. ¡CLAVE DEL FIX! Si la carpeta MiTPV no existe en el PC nuevo, la creamos primero
        File carpeta = new File(CARPETA_APP);
        if (!carpeta.exists()) {
            carpeta.mkdirs();
        }

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

            // Crear tabla cliente
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS cliente (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    dni TEXT UNIQUE,
                    nombre TEXT NOT NULL,
                    telefono TEXT,
                    direccion TEXT,
                    email TEXT
                )
            """);

            // ⬇️ Insertar cliente por defecto para ventas genéricas
            stmt.execute("""
                INSERT OR IGNORE INTO cliente (id, dni, nombre, telefono, direccion, email)
                VALUES (1, '00000000X', 'AL CONTADO', '', '', '')
            """);

            // Crear tabla empleado
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS empleado (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    nombre TEXT NOT NULL,
                    usuario TEXT UNIQUE NOT NULL,
                    password TEXT
                )
            """);

            // Insertar empleado por defecto
            stmt.execute("""
                INSERT OR IGNORE INTO empleado (id, nombre, usuario, password)
                VALUES (1, 'Ahmed', 'admin', '')
            """);

            // Crear tabla factura
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS factura (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    numero_factura TEXT,
                    fechaEmision TEXT NOT NULL,
                    cliente_id INTEGER,
                    cliente_nombre TEXT,
                    cliente_dni TEXT,
                    empleado_id INTEGER,
                    empleado_nombre TEXT,
                    total_sin_iva REAL,
                    total_iva REAL,
                    total_con_iva REAL,
                    metodo_pago TEXT DEFAULT 'EFECTIVO',
                    entregado_cliente DECIMAL(10,2) DEFAULT 0,
                    FOREIGN KEY (cliente_id) REFERENCES cliente(id),
                    FOREIGN KEY (empleado_id) REFERENCES empleado(id)
                )
            """);

            try {
                stmt.execute("ALTER TABLE factura RENAME COLUMN fecha TO fechaEmision;");
            } catch (SQLException ignored) {
                // Si la columna ya se llama fechaEmision o la BD es nueva, ignora el aviso
            }

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
                    descuento INTEGER DEFAULT 0,
                    FOREIGN KEY (factura_id) REFERENCES factura(id)
                )
            """);

            // Crear tabla configuracion (nueva)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS configuracion (
                    id INTEGER PRIMARY KEY CHECK (id = 1),
                    iva_general INTEGER DEFAULT 21,
                    nombre_tienda TEXT DEFAULT 'MI TIENDA',
                    direccion TEXT DEFAULT 'Calle Principal 123',
                    ciudad TEXT DEFAULT 'Ciudad',
                    codigo_postal TEXT DEFAULT '11000',
                    telefono TEXT DEFAULT '956 123 456',
                    cif TEXT DEFAULT 'B12345678',
                    nif TEXT DEFAULT '12345678X',
                    email TEXT DEFAULT 'info@mitienda.com'
                )
            """);

            // Insertar configuración por defecto si no existe
            stmt.execute("""
                INSERT OR IGNORE INTO configuracion (id, iva_general, nombre_tienda, direccion, ciudad, codigo_postal, telefono, cif, nif, email)
                VALUES (1, 21, 'ALMADENA TELEFONIA', 'AVDA Granada 5', 'Jaén', '23003', '953 47 48 40', '78429149-T', '78429149-T', 'almadenatelefonia@hotmail.com')
            """);

            System.out.println("✓ Base de datos SQLite inicializada correctamente");
            System.out.println("✓ Archivo de base de datos en: " + ARCHIVO_DB);        }
    }
}