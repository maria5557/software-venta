package org.tpv.config;

import org.tpv.domain.Empleado;

public class SesionUsuario {
    private static Empleado empleadoActivo;

    public static Empleado getEmpleadoActivo() {
        return empleadoActivo;
    }

    public static void setEmpleadoActivo(Empleado empleado) {
        empleadoActivo = empleado;
    }
}