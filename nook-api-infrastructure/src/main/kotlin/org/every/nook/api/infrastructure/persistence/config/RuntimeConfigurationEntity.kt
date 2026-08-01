package org.every.nook.api.infrastructure.persistence.config

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.every.nook.api.infrastructure.persistence.BaseEntity

@Entity
@Table(
    name = "runtime_configurations",
    uniqueConstraints = [
        UniqueConstraint(name = "idx_u_configuration_key", columnNames = ["configuration_key"]),
    ],
)
class RuntimeConfigurationEntity(
    @Column(name = "configuration_key", nullable = false, length = 100)
    val configurationKey: String,
    @Column(name = "configuration_value", nullable = false, length = 255)
    val configurationValue: String,
    @Column(name = "description", nullable = true, length = 500)
    val description: String? = null,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Long? = null
        protected set
}
