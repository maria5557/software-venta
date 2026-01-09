package org.tpv.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Empleado {
    private Long id;
    private String nombre;
    private String usuario;
    private String password;

    // Empleado por defecto
    public static Empleado empleadoPorDefecto() {
        return new Empleado(1L, "Ahmed", "admin", "");
    }
}