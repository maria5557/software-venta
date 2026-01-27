package org.tpv.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Cliente {
    private Long id;
    private String dni="";
    private String nombre="";
    private String telefono="";
    private String direccion="";
    private String email="";

    // Cliente por defecto
    public static Cliente clientePorDefecto() {
        return new Cliente(null, "", "CLIENTE AL CONTADO", "", "", "");
    }

    public boolean esClientePorDefecto() {
        return id == null || "CLIENTE AL CONTADO".equals(nombre);
    }
}