package org.tpv.config;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Configuracion {
    private int ivaGeneral; // 21

    // ===== DATOS DE LA TIENDA PARA EL TICKET =====
    private String nombreTienda;
    private String direccion;
    private String ciudad;
    private String codigoPostal;
    private String telefono;
    private String cif;
    private String nif;
    private String email;

}
