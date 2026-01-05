package org.tpv.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Producto {

    private Long id;
    private String codigoBarra;
    private String nombre;
    private BigDecimal precioBase; // precio con IVA
    //private boolean activo;
}
