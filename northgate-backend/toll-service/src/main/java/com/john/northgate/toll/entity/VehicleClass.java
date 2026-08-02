package com.john.northgate.toll.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "vehicle_class")
@Getter
@Setter
@NoArgsConstructor
public class VehicleClass {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 20)
    private String code;

    @Column(name = "label", nullable = false, length = 40)
    private String label;

    @Column(name = "fare", nullable = false, precision = 8, scale = 2)
    private BigDecimal fare;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;
}
